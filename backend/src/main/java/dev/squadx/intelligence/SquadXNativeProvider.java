package dev.squadx.intelligence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static dev.squadx.intelligence.CodeIntelligenceModels.*;

/** First commercial-native provider slice: revision-pinned local search and architecture boundaries. */
@Component
public class SquadXNativeProvider implements CodeIntelligenceProvider {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private final Path root;
    private final ObjectMapper mapper;
    private final NativeIndexManifestBuilder manifestBuilder;
    private final Map<String, CodeIndexManifest> manifests = new ConcurrentHashMap<>();

    public SquadXNativeProvider(ObjectMapper mapper,
            @Value("${intelligence.native.repository-root:${SQUADX_INTELLIGENCE_REPOSITORY_ROOT:/repositories}}") String repositoryRoot) {
        this.root = Path.of(repositoryRoot).toAbsolutePath().normalize();
        this.mapper = mapper;
        this.manifestBuilder = new NativeIndexManifestBuilder();
    }

    @Override public ProviderDescriptor descriptor() {
        return new ProviderDescriptor("native", "0.1", Set.of(Capability.SEARCH, Capability.SYMBOL_CONTEXT, Capability.ARCHITECTURE));
    }

    @Override public RepositorySnapshot ensureSnapshot(SnapshotRequest request) {
        requireRevision(request.revision()); Path repo = repository(request.projectId());
        String actual = run(repo, List.of("git", "rev-parse", "HEAD")).trim(); assertRevision(actual, request.revision());
        manifest(repo, "native:" + request.projectId() + ":" + actual, actual);
        return new RepositorySnapshot("native:" + request.projectId() + ":" + actual, request.organizationId(), request.projectId(),
                request.repositoryUrl(), actual, "native", "0.1", SnapshotStatus.READY, Instant.now(), null);
    }

    @Override public SearchResult search(SearchQuery query) {
        Identity id = identity(query.snapshotId()); Path repo = repository(id.projectId()); assertRevision(run(repo, List.of("git", "rev-parse", "HEAD")).trim(), id.revision());
        String output = run(repo, List.of("git", "grep", "-n", "-I", "-F", "-m",
                "" + Math.min(Math.max(query.size(), 1), 200), "--", query.query()), true);
        List<SearchHit> hits = new ArrayList<>();
        for (String line : output.lines().toList()) {
            if (line.isBlank()) continue;
            try {
                String[] fields = line.split(":", 3); if (fields.length < 3) continue;
                String path = fields[0]; int lineNo = Integer.parseInt(fields[1]); String text = fields[2];
                hits.add(new SearchHit(new CodeLocation(path, lineNo, lineNo), text, null, 1.0,
                        List.of(new EvidenceRef(query.snapshotId(), id.revision(), path, lineNo, lineNo, sha256(text)))));
                if (hits.size() >= Math.min(Math.max(query.size(), 1), 200)) break;
            } catch (NumberFormatException e) { throw new IllegalStateException("Invalid native search output", e); }
        }
        return new SearchResult(new ResultMetadata("native", "0.1", query.snapshotId(), id.revision(), 1.0, Instant.now(), Map.of("engine", "git-grep")), hits, false);
    }

    @Override public ArchitectureSnapshot getArchitecture(ArchitectureQuery query) {
        Identity id = identity(query.snapshotId()); Path repo = repository(id.projectId()); assertRevision(run(repo, List.of("git", "rev-parse", "HEAD")).trim(), id.revision());
        var manifest = manifest(repo, query.snapshotId(), id.revision());
        Map<String, List<String>> byBoundary = manifest.files().stream().limit(2000)
                .collect(Collectors.groupingBy(file -> firstBoundary(file.path()), LinkedHashMap::new,
                        Collectors.mapping(CodeIntelligenceModels.IndexedFile::path, Collectors.toList())));
        Map<String, String> hashes = manifest.files().stream()
                .collect(Collectors.toMap(CodeIntelligenceModels.IndexedFile::path,
                        CodeIntelligenceModels.IndexedFile::contentHash));
        List<ArchitectureComponent> components = byBoundary.entrySet().stream().limit(50)
                .map(e -> new ArchitectureComponent("boundary:" + e.getKey(), e.getKey(), "directory", e.getValue().stream().limit(500).map(p -> "file:" + p).toList())).toList();
        List<EvidenceRef> evidence = byBoundary.values().stream().flatMap(Collection::stream).limit(200)
                .map(p -> new EvidenceRef(query.snapshotId(), id.revision(), p, null, null, hashes.get(p))).toList();
        return new ArchitectureSnapshot(new ResultMetadata("native", "0.1", query.snapshotId(), id.revision(), .7, Instant.now(), Map.of("engine", "filesystem-boundaries")), components, List.of(), evidence);
    }

    private CodeIndexManifest manifest(Path repo, String snapshotId, String revision) {
        CodeIndexManifest cached = manifests.get(snapshotId);
        if (cached != null) return cached;
        CodeIndexManifest created = manifestBuilder.build(repo, snapshotId, revision);
        if (manifests.size() >= 8) {
            manifests.keySet().stream().findFirst().ifPresent(manifests::remove);
        }
        manifests.putIfAbsent(snapshotId, created);
        return manifests.get(snapshotId);
    }

    private String firstBoundary(String path) {
        int slash = path.indexOf('/');
        return slash < 0 ? path : path.substring(0, slash);
    }

    @Override public SymbolContext getSymbolContext(SymbolQuery query) {
        Identity id = identity(query.snapshotId());
        Path repo = repository(id.projectId());
        assertRevision(run(repo, List.of("git", "rev-parse", "HEAD")).trim(), id.revision());
        var manifest = manifest(repo, query.snapshotId(), id.revision());
        var symbol = manifest.symbols().stream()
                .filter(candidate -> candidate.id().equals(query.symbol())
                        || candidate.qualifiedName().equals(query.symbol()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Native symbol not found: " + query.symbol()));
        var evidence = List.of(new EvidenceRef(query.snapshotId(), id.revision(),
                symbol.location().path(), symbol.location().startLine(), symbol.location().endLine(), null));
        return new SymbolContext(new ResultMetadata("native", "0.1", query.snapshotId(), id.revision(), .8,
                Instant.now(), Map.of("engine", "manifest-symbols")), symbol, List.of(), List.of(), evidence);
    }
    @Override public DependencyGraph getDependencies(DependencyQuery query) { throw unsupported("DEPENDENCIES"); }
    @Override public ChangeImpact getChangeImpact(ChangeImpactQuery query) { throw unsupported("CHANGE_IMPACT"); }
    private UnsupportedOperationException unsupported(String capability) { return new UnsupportedOperationException("native provider capability pending: " + capability + " (SCIP pipeline)"); }
    private Path repository(Long projectId) { Path p = root.resolve(String.valueOf(projectId)).normalize(); if (!p.startsWith(root) || !Files.isDirectory(p.resolve(".git"))) throw new IllegalStateException("Native repository mirror unavailable for project " + projectId); return p; }
    private void assertRevision(String actual, String requested) { if (!actual.equalsIgnoreCase(requested) && !actual.toLowerCase().startsWith(requested.toLowerCase())) throw new IllegalStateException("Native mirror revision mismatch"); }
    private Identity identity(String value) { String[] p = value == null ? new String[0] : value.split(":", 3); if (p.length != 3 || !"native".equals(p[0])) throw new IllegalArgumentException("Invalid native snapshot id"); requireRevision(p[2]); return new Identity(Long.parseLong(p[1]), p[2]); }
    private String run(Path dir, List<String> command) { return run(dir, command, false); }
    private String run(Path dir, List<String> command, boolean noMatches) { try { Process p = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start(); if (!p.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) { p.destroyForcibly(); throw new IllegalStateException("Native search timed out"); } String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8); if (p.exitValue() != 0 && !(noMatches && p.exitValue() == 1)) throw new IllegalStateException("Native command failed: " + out.strip()); return out; } catch (IOException e) { throw new IllegalStateException("Native executable unavailable", e); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Native command interrupted", e); } }
    private static void requireRevision(String v) { if (v == null || !v.matches("[0-9a-fA-F]{7,64}")) throw new IllegalArgumentException("Invalid Git revision"); }
    private static String sha256(String v) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private record Identity(Long projectId, String revision) {}
}
