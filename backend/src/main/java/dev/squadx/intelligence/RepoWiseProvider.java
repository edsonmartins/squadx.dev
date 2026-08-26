package dev.squadx.intelligence;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static dev.squadx.intelligence.CodeIntelligenceModels.*;

/** HTTP adapter for an independently deployed RepoWise service. */
@Component
@ConditionalOnProperty(name = "intelligence.repowise.enabled", havingValue = "true")
public class RepoWiseProvider implements CodeIntelligenceProvider {

    private final RestClient client;
    private final Path repositoryRoot;
    private final int failureThreshold;
    private final long openMillis;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openUntil = new AtomicLong();

    public RepoWiseProvider(
            @Value("${intelligence.repowise.url}") String url,
            @Value("${intelligence.repowise.api-key}") String apiKey,
            @Value("${intelligence.repowise.repository-root}") String repositoryRoot,
            @Value("${intelligence.repowise.timeout-seconds:15}") int timeoutSeconds,
            @Value("${intelligence.repowise.failure-threshold:3}") int failureThreshold,
            @Value("${intelligence.repowise.open-seconds:30}") int openSeconds) {
        if (url.isBlank() || apiKey.isBlank() || repositoryRoot.isBlank()) {
            throw new IllegalStateException("RepoWise URL, API key and repository root are required");
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.client = RestClient.builder().baseUrl(url.replaceAll("/$", ""))
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .requestFactory(requestFactory).build();
        this.repositoryRoot = Path.of(repositoryRoot).toAbsolutePath().normalize();
        this.failureThreshold = Math.max(failureThreshold, 1);
        this.openMillis = Duration.ofSeconds(Math.max(openSeconds, 1)).toMillis();
    }

    RepoWiseProvider(RestClient client, Path repositoryRoot, int failureThreshold, Duration openDuration) {
        this.client = client;
        this.repositoryRoot = repositoryRoot.toAbsolutePath().normalize();
        this.failureThreshold = Math.max(failureThreshold, 1);
        this.openMillis = openDuration.toMillis();
    }

    @Override
    public ProviderDescriptor descriptor() {
        return new ProviderDescriptor("repowise", "http-api", Set.of(
                Capability.SEARCH, Capability.SYMBOL_CONTEXT,
                Capability.DEPENDENCIES, Capability.CHANGE_IMPACT));
    }

    public boolean healthy() {
        return guarded(() -> client.get().uri("/api/meta/version").retrieve()
                .body(JsonNode.class)) != null;
    }

    @Override
    public RepositorySnapshot ensureSnapshot(SnapshotRequest request) {
        requireRevision(request.revision());
        Path localPath = repositoryRoot.resolve(String.valueOf(request.projectId())).normalize();
        if (!localPath.startsWith(repositoryRoot)) throw new IllegalArgumentException("Invalid project path");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "squadx-project-" + request.projectId());
        body.put("local_path", localPath.toString());
        body.put("url", request.repositoryUrl());
        body.put("index", true);
        JsonNode response = guarded(() -> client.post().uri("/api/repos")
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class));
        String repoId = requiredText(response, "id");
        String indexedRevision = response.path("head_commit").asText("");
        if (!indexedRevision.toLowerCase(Locale.ROOT).startsWith(request.revision().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("RepoWise registered a different repository revision");
        }
        boolean indexing = response.hasNonNull("initial_job_id");
        return new RepositorySnapshot(snapshotId(repoId, indexedRevision), request.organizationId(),
                request.projectId(), request.repositoryUrl(), indexedRevision, "repowise", "http-api",
                indexing ? SnapshotStatus.INDEXING : SnapshotStatus.READY,
                indexing ? null : Instant.now(), response.path("initial_job_id").asText(null));
    }

    @Override
    public RepositorySnapshot refreshSnapshot(RepositorySnapshot snapshot) {
        if (snapshot.externalJobId() == null || snapshot.externalJobId().isBlank()) return snapshot;
        JsonNode job = guarded(() -> client.get().uri("/api/jobs/{id}", snapshot.externalJobId())
                .retrieve().body(JsonNode.class));
        String status = job.path("status").asText("pending").toLowerCase(Locale.ROOT);
        SnapshotStatus mapped = switch (status) {
            case "completed", "complete", "succeeded", "success" -> SnapshotStatus.READY;
            case "failed", "error", "cancelled", "canceled" -> SnapshotStatus.FAILED;
            default -> SnapshotStatus.INDEXING;
        };
        return new RepositorySnapshot(snapshot.id(), snapshot.organizationId(), snapshot.projectId(),
                snapshot.repositoryUrl(), snapshot.revision(), snapshot.provider(), snapshot.providerVersion(),
                mapped, mapped == SnapshotStatus.READY ? Instant.now() : snapshot.indexedAt(),
                snapshot.externalJobId());
    }

    @Override
    public SearchResult search(SearchQuery query) {
        Identity identity = identity(query.snapshotId());
        int limit = Math.min(Math.max(query.size(), 1), 100);
        String uri = UriComponentsBuilder.fromPath("/api/search")
                .queryParam("query", query.query()).queryParam("search_type", "fulltext")
                .queryParam("limit", limit).queryParam("repo_id", identity.repoId())
                .build().encode().toUriString();
        JsonNode response = guarded(() -> client.get().uri(uri).retrieve().body(JsonNode.class));
        List<SearchHit> hits = new ArrayList<>();
        for (JsonNode item : response) {
            String path = item.path("target_path").asText();
            String snippet = item.path("snippet").asText();
            double score = clamp(item.path("score").asDouble(0.5));
            EvidenceRef evidence = new EvidenceRef(query.snapshotId(), identity.revision(), path,
                    null, null, null);
            hits.add(new SearchHit(new CodeLocation(path, 0, 0), snippet, null, score,
                    List.of(evidence)));
        }
        return new SearchResult(metadata(query.snapshotId(), identity.revision(), 0.8), hits, false);
    }

    @Override
    public SymbolContext getSymbolContext(SymbolQuery query) {
        Identity identity = identity(query.snapshotId());
        String uri = UriComponentsBuilder.fromPath("/api/symbols")
                .queryParam("repo_id", identity.repoId()).queryParam("q", query.symbol())
                .queryParam("limit", 1).build().encode().toUriString();
        JsonNode page = guarded(() -> client.get().uri(uri).retrieve().body(JsonNode.class));
        JsonNode item = page.path("items").path(0);
        if (item.isMissingNode()) throw new NoSuchElementException("RepoWise symbol not found");
        CodeSymbol symbol = symbol(item);
        EvidenceRef evidence = evidence(query.snapshotId(), identity.revision(), symbol.location());
        return new SymbolContext(metadata(query.snapshotId(), identity.revision(), 0.9), symbol,
                List.of(), List.of(), List.of(evidence));
    }

    @Override
    public DependencyGraph getDependencies(DependencyQuery query) {
        Identity identity = identity(query.snapshotId());
        String uri = UriComponentsBuilder.fromPath("/api/symbols/detail")
                .queryParam("repo_id", identity.repoId()).queryParam("symbol_id", query.symbolId())
                .build().encode().toUriString();
        JsonNode detail = guarded(() -> client.get().uri(uri).retrieve().body(JsonNode.class));
        JsonNode root = detail.path("symbol");
        CodeSymbol focal = symbol(root);
        List<CodeSymbol> symbols = new ArrayList<>(); symbols.add(focal);
        List<CodeRelationship> relationships = new ArrayList<>();
        appendRelations(query.snapshotId(), identity.revision(), focal.id(), detail.path("callers"),
                true, symbols, relationships);
        appendRelations(query.snapshotId(), identity.revision(), focal.id(), detail.path("callees"),
                false, symbols, relationships);
        return new DependencyGraph(metadata(query.snapshotId(), identity.revision(), 0.85),
                symbols, relationships);
    }

    @Override
    public ChangeImpact getChangeImpact(ChangeImpactQuery query) {
        Identity base = identity(query.baseSnapshotId());
        Identity head = identity(query.headSnapshotId());
        if (!base.repoId().equals(head.repoId())) {
            throw new IllegalArgumentException("Base and head snapshots belong to different repositories");
        }
        Map<String, Object> body = Map.of("changed_files", query.changedPaths(), "max_depth", 3);
        JsonNode response = guarded(() -> client.post()
                .uri("/api/repos/{id}/blast-radius", head.repoId())
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class));
        List<CodeSymbol> changed = query.changedPaths().stream()
                .map(path -> fileSymbol(path, "changed")).toList();
        List<CodeSymbol> affected = new ArrayList<>();
        for (JsonNode item : response.path("transitive_affected")) {
            affected.add(fileSymbol(item.path("path").asText(), "affected"));
        }
        double confidence = clamp(response.path("overall_risk_score").asDouble(5.0) / 10.0);
        return new ChangeImpact(metadata(query.headSnapshotId(), head.revision(), confidence),
                base.revision(), head.revision(), changed, affected, List.of(), List.of());
    }

    @Override public ArchitectureSnapshot getArchitecture(ArchitectureQuery query) {
        throw new UnsupportedOperationException("RepoWise architecture mapping is not enabled yet");
    }

    private void appendRelations(String snapshotId, String revision, String focalId, JsonNode nodes,
                                 boolean incoming, List<CodeSymbol> symbols,
                                 List<CodeRelationship> relationships) {
        for (JsonNode node : nodes) {
            String id = node.path("symbol_id").asText();
            CodeLocation location = new CodeLocation(node.path("file").asText(),
                    node.path("start_line").asInt(0), node.path("start_line").asInt(0));
            symbols.add(new CodeSymbol(id, node.path("name").asText(), node.path("kind").asText(),
                    "unknown", location));
            relationships.add(new CodeRelationship(incoming ? id : focalId, incoming ? focalId : id,
                    node.path("edge_type").asText("calls"), clamp(node.path("confidence").asDouble()),
                    List.of(evidence(snapshotId, revision, location))));
        }
    }

    private CodeSymbol symbol(JsonNode item) {
        return new CodeSymbol(requiredText(item, "symbol_id"), item.path("qualified_name").asText(),
                item.path("kind").asText(), item.path("language").asText(),
                new CodeLocation(item.path("file_path").asText(), item.path("start_line").asInt(),
                        item.path("end_line").asInt()));
    }

    private CodeSymbol fileSymbol(String path, String kind) {
        return new CodeSymbol("file:" + path, path, kind, "unknown", new CodeLocation(path, 0, 0));
    }

    private EvidenceRef evidence(String snapshotId, String revision, CodeLocation location) {
        return new EvidenceRef(snapshotId, revision, location.path(), location.startLine(),
                location.endLine(), null);
    }

    private ResultMetadata metadata(String snapshotId, String revision, double confidence) {
        return new ResultMetadata("repowise", "http-api", snapshotId, revision,
                clamp(confidence), Instant.now(), Map.of("mode", "shadow"));
    }

    private <T> T guarded(Supplier<T> operation) {
        long now = System.currentTimeMillis();
        if (openUntil.get() > now) throw new IllegalStateException("RepoWise circuit breaker is open");
        try {
            T result = operation.get();
            consecutiveFailures.set(0); openUntil.set(0); return result;
        } catch (RuntimeException e) {
            if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
                openUntil.set(now + openMillis);
            }
            throw e;
        }
    }

    private static String snapshotId(String repoId, String revision) {
        return "repowise:" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(repoId.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ":" + revision;
    }

    private static Identity identity(String snapshotId) {
        String[] parts = snapshotId == null ? new String[0] : snapshotId.split(":", 3);
        if (parts.length != 3 || !"repowise".equals(parts[0])) {
            throw new IllegalArgumentException("Invalid RepoWise snapshot id");
        }
        requireRevision(parts[2]);
        try {
            String repoId = new String(Base64.getUrlDecoder().decode(parts[1]),
                    java.nio.charset.StandardCharsets.UTF_8);
            return new Identity(repoId, parts[2]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid RepoWise repository id", e);
        }
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node == null ? "" : node.path(field).asText("");
        if (value.isBlank()) throw new IllegalStateException("RepoWise response has no " + field);
        return value;
    }

    private static void requireRevision(String revision) {
        if (revision == null || !revision.matches("[0-9a-fA-F]{7,64}")) {
            throw new IllegalArgumentException("Invalid Git revision");
        }
    }

    private static double clamp(double value) { return Math.max(0.0, Math.min(value, 1.0)); }
    private record Identity(String repoId, String revision) {}
}
