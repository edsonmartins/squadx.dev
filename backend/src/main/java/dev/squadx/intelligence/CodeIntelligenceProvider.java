package dev.squadx.intelligence;

import dev.squadx.intelligence.CodeIntelligenceModels.*;

/** Vendor-neutral port implemented by native, RepoWise and future search providers. */
public interface CodeIntelligenceProvider {

    ProviderDescriptor descriptor();

    RepositorySnapshot ensureSnapshot(SnapshotRequest request);

    default RepositorySnapshot refreshSnapshot(RepositorySnapshot snapshot) {
        return snapshot;
    }

    SearchResult search(SearchQuery query);

    SymbolContext getSymbolContext(SymbolQuery query);

    DependencyGraph getDependencies(DependencyQuery query);

    ChangeImpact getChangeImpact(ChangeImpactQuery query);

    ArchitectureSnapshot getArchitecture(ArchitectureQuery query);
}
