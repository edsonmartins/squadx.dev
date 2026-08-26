package dev.squadx.intelligence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Canonical, revision-pinned types shared by every code-intelligence provider. */
public final class CodeIntelligenceModels {
    private CodeIntelligenceModels() {}

    public record ProviderDescriptor(String id, String version, Set<Capability> capabilities) {
        public ProviderDescriptor {
            capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        }
    }

    public enum Capability {
        SEARCH, SYMBOL_CONTEXT, DEPENDENCIES, CHANGE_IMPACT, ARCHITECTURE
    }

    public enum SnapshotStatus { PENDING, INDEXING, READY, FAILED }

    public record SnapshotRequest(Long organizationId, Long projectId, String repositoryUrl,
                                  String revision) {}

    /** Parser-neutral index payload. It is always scoped to one immutable Git revision. */
    public record CodeIndexManifest(String snapshotId, String revision,
                                    List<IndexedFile> files,
                                    List<CodeSymbol> symbols,
                                    List<IndexedReference> references,
                                    List<EvidenceRef> evidence) {
        public CodeIndexManifest {
            if (snapshotId == null || snapshotId.isBlank()) throw new IllegalArgumentException("snapshotId is required");
            if (revision == null || revision.isBlank()) throw new IllegalArgumentException("revision is required");
            files = immutable(files);
            symbols = immutable(symbols);
            references = immutable(references);
            evidence = immutable(evidence);
        }
    }

    public record IndexedFile(String path, String language, long sizeBytes, String contentHash) {
        public IndexedFile {
            if (path == null || path.isBlank()) throw new IllegalArgumentException("file path is required");
            if (sizeBytes < 0) throw new IllegalArgumentException("file size cannot be negative");
            if (contentHash == null || contentHash.isBlank()) throw new IllegalArgumentException("content hash is required");
        }
    }

    public record IndexedReference(String sourceSymbolId, String targetSymbolId, String kind,
                                   double confidence, List<EvidenceRef> evidence) {
        public IndexedReference {
            if (sourceSymbolId == null || sourceSymbolId.isBlank()) throw new IllegalArgumentException("source symbol is required");
            if (targetSymbolId == null || targetSymbolId.isBlank()) throw new IllegalArgumentException("target symbol is required");
            if (kind == null || kind.isBlank()) throw new IllegalArgumentException("reference kind is required");
            confidence = normalizeConfidence(confidence);
            evidence = immutable(evidence);
        }
    }

    public record RepositorySnapshot(String id, Long organizationId, Long projectId,
                                     String repositoryUrl, String revision, String provider,
                                     String providerVersion, SnapshotStatus status,
                                     Instant indexedAt, String externalJobId) {}

    public record EvidenceRef(String snapshotId, String revision, String path,
                              Integer startLine, Integer endLine, String excerptHash) {}

    public record CodeLocation(String path, int startLine, int endLine) {}

    public record CodeSymbol(String id, String qualifiedName, String kind, String language,
                             CodeLocation location) {}

    public record CodeRelationship(String sourceId, String targetId, String kind,
                                   double confidence, List<EvidenceRef> evidence) {
        public CodeRelationship {
            confidence = normalizeConfidence(confidence);
            evidence = immutable(evidence);
        }
    }

    public record SearchQuery(String snapshotId, String query, int page, int size) {}
    public record SearchHit(CodeLocation location, String snippet, String symbolId,
                            double confidence, List<EvidenceRef> evidence) {
        public SearchHit {
            confidence = normalizeConfidence(confidence);
            evidence = immutable(evidence);
        }
    }
    public record SearchResult(ResultMetadata metadata, List<SearchHit> hits,
                               boolean hasMore) {
        public SearchResult { hits = immutable(hits); }
    }

    public record SymbolQuery(String snapshotId, String symbol) {}
    public record SymbolContext(ResultMetadata metadata, CodeSymbol symbol,
                                List<CodeRelationship> incoming,
                                List<CodeRelationship> outgoing,
                                List<EvidenceRef> evidence) {
        public SymbolContext {
            incoming = immutable(incoming);
            outgoing = immutable(outgoing);
            evidence = immutable(evidence);
        }
    }

    public record DependencyQuery(String snapshotId, String symbolId, int depth) {}
    public record DependencyGraph(ResultMetadata metadata, List<CodeSymbol> symbols,
                                  List<CodeRelationship> relationships) {
        public DependencyGraph {
            symbols = immutable(symbols);
            relationships = immutable(relationships);
        }
    }

    public record ChangeImpactQuery(String baseSnapshotId, String headSnapshotId,
                                    List<String> changedPaths) {
        public ChangeImpactQuery { changedPaths = immutable(changedPaths); }
    }
    public record ChangeImpact(ResultMetadata metadata, String baseRevision, String headRevision,
                               List<CodeSymbol> changed, List<CodeSymbol> affected,
                               List<CodeRelationship> impactPaths,
                               List<EvidenceRef> evidence) {
        public ChangeImpact {
            changed = immutable(changed);
            affected = immutable(affected);
            impactPaths = immutable(impactPaths);
            evidence = immutable(evidence);
        }
    }

    public record ArchitectureQuery(String snapshotId) {}
    public record ArchitectureComponent(String id, String name, String boundary,
                                        List<String> memberSymbolIds) {
        public ArchitectureComponent { memberSymbolIds = immutable(memberSymbolIds); }
    }
    public record ArchitectureSnapshot(ResultMetadata metadata,
                                       List<ArchitectureComponent> components,
                                       List<CodeRelationship> relationships,
                                       List<EvidenceRef> evidence) {
        public ArchitectureSnapshot {
            components = immutable(components);
            relationships = immutable(relationships);
            evidence = immutable(evidence);
        }
    }

    public record ResultMetadata(String provider, String providerVersion, String snapshotId,
                                 String revision, double confidence, Instant producedAt,
                                 Map<String, String> attributes) {
        public ResultMetadata {
            confidence = normalizeConfidence(confidence);
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }

    private static double normalizeConfidence(double value) {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        return value;
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
