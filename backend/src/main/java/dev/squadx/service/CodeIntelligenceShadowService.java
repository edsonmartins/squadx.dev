package dev.squadx.service;

import dev.squadx.dto.intelligence.ShadowComparisonResponse;
import dev.squadx.dto.intelligence.ShadowSearchRequest;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.intelligence.CodeIntelligenceModels.*;
import dev.squadx.intelligence.CodeIntelligenceProviderRegistry;
import dev.squadx.model.*;
import dev.squadx.model.enums.IntelligenceSnapshotStatus;
import dev.squadx.repository.*;
import dev.squadx.observability.CodeIntelligenceMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;

import static dev.squadx.intelligence.CodeIntelligenceModels.Capability.SEARCH;

@Service @RequiredArgsConstructor @Slf4j
public class CodeIntelligenceShadowService {
    private final CodeIntelligenceSnapshotRepository snapshots;
    private final CodeIntelligenceShadowComparisonRepository comparisons;
    private final OrganizationMemberRepository members;
    private final CodeIntelligenceProviderRegistry registry;
    private final CodeIntelligenceMetrics metrics;

    @Transactional
    public ShadowComparisonResponse compareSearch(ShadowSearchRequest request, User user) {
        CodeIntelligenceSnapshot snapshot = snapshots.findById(request.snapshotId())
                .orElseThrow(() -> new ResourceNotFoundException("Code intelligence snapshot not found"));
        if (!members.existsByOrganizationIdAndUserId(snapshot.getOrganization().getId(), user.getId()))
            throw new ForbiddenException("User does not have access to this organization");
        if (snapshot.getStatus() != IntelligenceSnapshotStatus.READY || snapshot.getExternalSnapshotId() == null)
            throw new BadRequestException("Code intelligence snapshot is not ready");
        var selection = registry.select(snapshot.getOrganization().getId(), SEARCH);
        if (selection.shadow() == null)
            throw new BadRequestException("Shadow provider is not configured for this organization");
        CodeIntelligenceSnapshot shadowSnapshot = snapshots
                .findByProjectIdAndRevisionAndProvider(snapshot.getProject().getId(),
                        snapshot.getRevision(), selection.shadow().descriptor().id())
                .orElseThrow(() -> new BadRequestException("Shadow snapshot is not provisioned for provider '"
                        + selection.shadow().descriptor().id() + "'"));
        if (shadowSnapshot.getStatus() != IntelligenceSnapshotStatus.READY
                || shadowSnapshot.getExternalSnapshotId() == null) {
            throw new BadRequestException("Shadow snapshot is not ready for provider '"
                    + selection.shadow().descriptor().id() + "'");
        }
        long start = System.nanoTime();
        SearchResult primary;
        try {
            primary = selection.primary().search(new SearchQuery(snapshot.getExternalSnapshotId(), request.query(), 0, 100));
            metrics.providerCall("search", selection.primary().descriptor().id(), primaryMs(start), true);
        } catch (RuntimeException e) {
            metrics.providerCall("search", selection.primary().descriptor().id(), primaryMs(start), false);
            throw e;
        }
        long primaryMs = elapsed(start);
        start = System.nanoTime();
        SearchResult shadow;
        try {
            shadow = selection.shadow().search(new SearchQuery(shadowSnapshot.getExternalSnapshotId(), request.query(), 0, 100));
            metrics.providerCall("search", selection.shadow().descriptor().id(), elapsed(start), true);
        } catch (RuntimeException e) {
            metrics.providerCall("search", selection.shadow().descriptor().id(), elapsed(start), false);
            throw e;
        }
        long shadowMs = elapsed(start);
        var primaryKeys = new HashSet<>(primary.hits().stream().map(h -> h.location().path()).toList());
        var shadowKeys = new HashSet<>(shadow.hits().stream().map(h -> h.location().path()).toList());
        primaryKeys.retainAll(shadowKeys);
        int overlap = primaryKeys.size();
        int union = new HashSet<>(primary.hits().stream().map(h -> h.location().path()).toList()).size();
        union += shadowKeys.size() - overlap;
        double divergence = union == 0 ? 0.0 : 1.0 - ((double) overlap / union);
        CodeIntelligenceShadowComparison saved = comparisons.save(CodeIntelligenceShadowComparison.builder()
                .snapshot(snapshot).query(request.query())
                .primaryProvider(selection.primary().descriptor().id()).shadowProvider(selection.shadow().descriptor().id())
                .primaryHits(primary.hits().size()).shadowHits(shadow.hits().size()).overlapHits(overlap)
                .divergenceScore(divergence).primaryLatencyMs(primaryMs).shadowLatencyMs(shadowMs)
                .comparedAt(Instant.now()).build());
        metrics.shadow(saved.getPrimaryProvider(), saved.getShadowProvider(), saved.getDivergenceScore());
        return map(saved);
    }

    private long elapsed(long start) { return (System.nanoTime() - start) / 1_000_000; }
    private ShadowComparisonResponse map(CodeIntelligenceShadowComparison c) {
        return new ShadowComparisonResponse(c.getId(), c.getSnapshot().getId(), c.getQuery(), c.getPrimaryProvider(),
                c.getShadowProvider(), c.getPrimaryHits(), c.getShadowHits(), c.getOverlapHits(), c.getDivergenceScore(),
                c.getPrimaryLatencyMs(), c.getShadowLatencyMs(), c.getErrorMessage(), c.getComparedAt());
    }

    private long primaryMs(long started) { return elapsed(started); }
}
