package dev.squadx.service;

import dev.squadx.dto.intelligence.SearchCodeRequest;
import dev.squadx.dto.intelligence.SymbolContextRequest;
import dev.squadx.dto.intelligence.ChangeImpactRequest;
import dev.squadx.dto.intelligence.DependencyGraphRequest;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.intelligence.CodeIntelligenceModels.SearchQuery;
import dev.squadx.intelligence.CodeIntelligenceModels.SearchResult;
import dev.squadx.intelligence.CodeIntelligenceModels.SymbolContext;
import dev.squadx.intelligence.CodeIntelligenceModels.SymbolQuery;
import dev.squadx.intelligence.CodeIntelligenceModels.DependencyGraph;
import dev.squadx.intelligence.CodeIntelligenceModels.DependencyQuery;
import dev.squadx.intelligence.CodeIntelligenceModels.ChangeImpact;
import dev.squadx.intelligence.CodeIntelligenceModels.ChangeImpactQuery;
import dev.squadx.intelligence.CodeIntelligenceProviderRegistry;
import dev.squadx.model.CodeIntelligenceSnapshot;
import dev.squadx.model.User;
import dev.squadx.model.enums.IntelligenceSnapshotStatus;
import dev.squadx.repository.CodeIntelligenceSnapshotRepository;
import dev.squadx.repository.OrganizationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static dev.squadx.intelligence.CodeIntelligenceModels.Capability;

@Service
@RequiredArgsConstructor
public class CodeIntelligenceQueryService {

    private final CodeIntelligenceSnapshotRepository snapshotRepository;
    private final OrganizationMemberRepository memberRepository;
    private final CodeIntelligenceProviderRegistry providerRegistry;

    @Transactional(readOnly = true)
    public SearchResult search(SearchCodeRequest request, User user) {
        CodeIntelligenceSnapshot snapshot = accessibleReadySnapshot(request.snapshotId(), user);
        var provider = providerRegistry.requireProvider(snapshot.getProvider(), Capability.SEARCH);
        return provider.search(new SearchQuery(snapshot.getExternalSnapshotId(), request.query(),
                request.page(), request.size()));
    }

    @Transactional(readOnly = true)
    public SymbolContext symbolContext(SymbolContextRequest request, User user) {
        CodeIntelligenceSnapshot snapshot = accessibleReadySnapshot(request.snapshotId(), user);
        var provider = providerRegistry.requireProvider(snapshot.getProvider(), Capability.SYMBOL_CONTEXT);
        return provider.getSymbolContext(new SymbolQuery(snapshot.getExternalSnapshotId(), request.symbol()));
    }

    @Transactional(readOnly = true)
    public DependencyGraph dependencies(DependencyGraphRequest request, User user) {
        CodeIntelligenceSnapshot snapshot = accessibleReadySnapshot(request.snapshotId(), user);
        var provider = providerRegistry.requireProvider(snapshot.getProvider(), Capability.DEPENDENCIES);
        return provider.getDependencies(new DependencyQuery(snapshot.getExternalSnapshotId(),
                request.symbolId(), request.depth()));
    }

    @Transactional(readOnly = true)
    public ChangeImpact changeImpact(ChangeImpactRequest request, User user) {
        CodeIntelligenceSnapshot base = accessibleReadySnapshot(request.baseSnapshotId(), user);
        CodeIntelligenceSnapshot head = accessibleReadySnapshot(request.headSnapshotId(), user);
        if (!base.getProject().getId().equals(head.getProject().getId())) {
            throw new BadRequestException("Base and head snapshots must belong to the same project");
        }
        if (!base.getProvider().equals(head.getProvider())) {
            throw new BadRequestException("Base and head snapshots must use the same provider");
        }
        var provider = providerRegistry.requireProvider(base.getProvider(), Capability.CHANGE_IMPACT);
        return provider.getChangeImpact(new ChangeImpactQuery(base.getExternalSnapshotId(),
                head.getExternalSnapshotId(), request.changedPaths()));
    }

    private CodeIntelligenceSnapshot accessibleReadySnapshot(Long snapshotId, User user) {
        CodeIntelligenceSnapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("Code intelligence snapshot not found"));
        if (!memberRepository.existsByOrganizationIdAndUserId(
                snapshot.getOrganization().getId(), user.getId())) {
            throw new ForbiddenException("User does not have access to this organization");
        }
        if (snapshot.getStatus() != IntelligenceSnapshotStatus.READY
                || snapshot.getExternalSnapshotId() == null
                || snapshot.getExternalSnapshotId().isBlank()) {
            throw new BadRequestException("Code intelligence snapshot is not ready");
        }
        return snapshot;
    }
}
