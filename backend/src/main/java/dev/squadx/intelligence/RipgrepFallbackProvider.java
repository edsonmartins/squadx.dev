package dev.squadx.intelligence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

import static dev.squadx.intelligence.CodeIntelligenceModels.*;

/** Bounded lexical fallback over pre-existing, revision-pinned local repository mirrors. */
@Component
@ConditionalOnProperty(name = "intelligence.ripgrep.enabled", havingValue = "true")
public class RipgrepFallbackProvider implements CodeIntelligenceProvider {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_PAGE_SIZE = 200;
    private final Path repositoryRoot;
    private final ObjectMapper objectMapper;

    public RipgrepFallbackProvider(
            ObjectMapper objectMapper,
            @Value("${intelligence.ripgrep.repository-root:}") String repositoryRoot) {
        if (repositoryRoot == null || repositoryRoot.isBlank()) {
            throw new IllegalStateException("intelligence.ripgrep.repository-root is required");
        }
        this.repositoryRoot = Path.of(repositoryRoot).toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
    }

    @Override
    public ProviderDescriptor descriptor() {
        return new ProviderDescriptor("ripgrep", "local", Set.of(Capability.SEARCH, Capability.ARCHITECTURE));
    }

    @Override
    public RepositorySnapshot ensureSnapshot(SnapshotRequest request) {
        requireRevision(request.revision());
        Path repository = resolveRepository(request.projectId());
        String actualRevision = run(repository, List.of("git", "rev-parse", "HEAD")).trim();
        if (!actualRevision.toLowerCase().startsWith(request.revision().toLowerCase())) {
            throw new IllegalStateException("Local mirror HEAD does not match requested revision");
        }
        String id = snapshotId(request.projectId(), actualRevision);
        return new RepositorySnapshot(id, request.organizationId(), request.projectId(),
                request.repositoryUrl(), actualRevision, "ripgrep", "local",
                SnapshotStatus.READY, Instant.now(), null);
    }

    @Override
    public SearchResult search(SearchQuery query) {
        if (query.query() == null || query.query().isBlank() || query.query().length() > 500) {
            throw new IllegalArgumentException("Search query must contain 1 to 500 characters");
        }
        SnapshotIdentity identity = parseSnapshotId(query.snapshotId());
        Path repository = resolveRepository(identity.projectId());
        assertRevision(repository, identity.revision());
        int page = Math.max(query.page(), 0);
        int size = Math.min(Math.max(query.size(), 1), MAX_PAGE_SIZE);
        int required = Math.min((page + 1) * size + 1, 10_001);
        String output = run(repository, List.of(
                "rg", "--json", "--line-number", "--column", "--color", "never",
                "--fixed-strings", "--max-count", String.valueOf(required),
                "--", query.query(), "."), true);

        List<SearchHit> all = parseMatches(query.snapshotId(), identity.revision(), output, required);
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        ResultMetadata metadata = new ResultMetadata("ripgrep", "local", query.snapshotId(),
                identity.revision(), 1.0, Instant.now(), java.util.Map.of("mode", "fallback"));
        return new SearchResult(metadata, all.subList(from, to), all.size() > to);
    }

    @Override public SymbolContext getSymbolContext(SymbolQuery query) { return unsupported(); }
    @Override public DependencyGraph getDependencies(DependencyQuery query) { return unsupported(); }
    @Override public ChangeImpact getChangeImpact(ChangeImpactQuery query) { return unsupported(); }
    @Override
    public ArchitectureSnapshot getArchitecture(ArchitectureQuery query) {
        SnapshotIdentity identity = parseSnapshotId(query.snapshotId());
        Path repository = resolveRepository(identity.projectId());
        assertRevision(repository, identity.revision());
        try {
            Map<String, List<String>> filesByBoundary;
            try (var paths = Files.walk(repository)) {
                filesByBoundary = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.startsWith(repository.resolve(".git")))
                    .map(repository::relativize)
                    .filter(path -> path.getNameCount() > 0)
                    .limit(2000)
                    .collect(Collectors.groupingBy(path -> path.getName(0).toString(),
                            LinkedHashMap::new, Collectors.mapping(Path::toString, Collectors.toList())));
            }
            List<ArchitectureComponent> components = filesByBoundary.entrySet().stream()
                    .limit(50)
                    .map(entry -> new ArchitectureComponent(
                            "boundary:" + entry.getKey(), entry.getKey(), "directory",
                            entry.getValue().stream().limit(500).map(path -> "file:" + path).toList()))
                    .toList();
            List<EvidenceRef> evidence = filesByBoundary.values().stream().flatMap(List::stream)
                    .limit(200).map(path -> new EvidenceRef(query.snapshotId(), identity.revision(), path,
                            null, null, sha256(path))).toList();
            return new ArchitectureSnapshot(
                    new ResultMetadata("ripgrep", "local", query.snapshotId(), identity.revision(), 0.7,
                            Instant.now(), Map.of("mode", "lexical-boundaries", "verified_revision", identity.revision())),
                    components, List.of(), evidence);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to inspect repository architecture", e);
        }
    }

    private <T> T unsupported() {
        throw new UnsupportedOperationException("ripgrep provider supports SEARCH only");
    }

    private List<SearchHit> parseMatches(String snapshotId, String revision, String output, int limit) {
        List<SearchHit> hits = new ArrayList<>();
        for (String line : output.lines().toList()) {
            if (line.isBlank()) continue;
            try {
                JsonNode event = objectMapper.readTree(line);
                if (!"match".equals(event.path("type").asText())) continue;
                JsonNode data = event.path("data");
                String path = data.path("path").path("text").asText();
                String text = data.path("lines").path("text").asText().stripTrailing();
                JsonNode submatch = data.path("submatches").path(0);
                int lineNumber = data.path("line_number").asInt();
                int column = submatch.path("start").asInt() + 1;
                EvidenceRef evidence = new EvidenceRef(snapshotId, revision, path,
                        lineNumber, lineNumber, sha256(text));
                hits.add(new SearchHit(new CodeLocation(path, lineNumber, lineNumber),
                        text, null, 1.0, List.of(evidence)));
                if (hits.size() >= limit) break;
            } catch (IOException e) {
                throw new IllegalStateException("Invalid ripgrep JSON output", e);
            }
        }
        return hits;
    }

    private Path resolveRepository(Long projectId) {
        Path repository = repositoryRoot.resolve(String.valueOf(projectId)).normalize();
        if (!repository.startsWith(repositoryRoot) || !Files.isDirectory(repository.resolve(".git"))) {
            throw new IllegalStateException("Local repository mirror is unavailable for project " + projectId);
        }
        return repository;
    }

    private void assertRevision(Path repository, String expected) {
        String actual = run(repository, List.of("git", "rev-parse", "HEAD")).trim();
        if (!actual.equalsIgnoreCase(expected)) {
            throw new IllegalStateException("Local mirror changed after snapshot creation");
        }
    }

    private String run(Path workingDirectory, List<String> command) {
        return run(workingDirectory, command, false);
    }

    private String run(Path workingDirectory, List<String> command, boolean acceptNoMatches) {
        ProcessBuilder builder = new ProcessBuilder(command).directory(workingDirectory.toFile())
                .redirectErrorStream(true);
        try {
            Process process = builder.start();
            boolean completed = process.waitFor(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new IllegalStateException("Code search command timed out");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0 && !(acceptNoMatches && process.exitValue() == 1)) {
                throw new IllegalStateException("Code search command failed: " + output.strip());
            }
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Code search executable is unavailable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Code search command was interrupted", e);
        }
    }

    private static String snapshotId(Long projectId, String revision) {
        return "ripgrep:" + projectId + ":" + revision;
    }

    private static SnapshotIdentity parseSnapshotId(String snapshotId) {
        String[] parts = snapshotId == null ? new String[0] : snapshotId.split(":", 3);
        if (parts.length != 3 || !"ripgrep".equals(parts[0])) {
            throw new IllegalArgumentException("Invalid ripgrep snapshot id");
        }
        requireRevision(parts[2]);
        try {
            return new SnapshotIdentity(Long.parseLong(parts[1]), parts[2]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid project in ripgrep snapshot id", e);
        }
    }

    private static void requireRevision(String revision) {
        if (revision == null || !revision.matches("[0-9a-fA-F]{7,64}")) {
            throw new IllegalArgumentException("Invalid Git revision");
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private record SnapshotIdentity(Long projectId, String revision) {}
}
