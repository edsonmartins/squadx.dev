package dev.squadx.service;

import dev.squadx.intelligence.CodeIntelligenceModels.Capability;
import dev.squadx.intelligence.CodeIntelligenceModels.SnapshotRequest;
import dev.squadx.intelligence.CodeIntelligenceProviderRegistry;
import dev.squadx.model.CodeIntelligenceIndexJob;
import dev.squadx.model.CodeIntelligenceSnapshot;
import dev.squadx.model.enums.IntelligenceJobStatus;
import dev.squadx.model.enums.IntelligenceSnapshotStatus;
import dev.squadx.repository.CodeIntelligenceIndexJobRepository;
import dev.squadx.repository.CodeIntelligenceSnapshotRepository;
import dev.squadx.observability.CodeIntelligenceMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import dev.squadx.intelligence.CodeIntelligenceModels.RepositorySnapshot;

@Component
@RequiredArgsConstructor
@Slf4j
public class CodeIntelligenceIndexWorker {

    private final CodeIntelligenceIndexJobRepository jobRepository;
    private final CodeIntelligenceSnapshotRepository snapshotRepository;
    private final CodeIntelligenceProviderRegistry providerRegistry;
    private final CodeIntelligenceMetrics metrics;

    @Scheduled(fixedDelayString = "${intelligence.worker.poll-interval-ms:5000}")
    @Transactional
    public void processPending() {
        for (CodeIntelligenceIndexJob job : jobRepository
                .findTop20ByStatusOrderByCreatedAtAsc(IntelligenceJobStatus.PENDING)) {
            process(job);
        }
    }

    void process(CodeIntelligenceIndexJob job) {
        long startedNanos = System.nanoTime();
        CodeIntelligenceSnapshot snapshot = job.getSnapshot();
        job.setStatus(IntelligenceJobStatus.RUNNING);
        job.setAttempt(job.getAttempt() + 1);
        job.setStartedAt(Instant.now());
        snapshot.setStatus(IntelligenceSnapshotStatus.INDEXING);
        jobRepository.save(job);
        snapshotRepository.save(snapshot);
        try {
            var provider = providerRegistry.requireProvider(snapshot.getProvider(), Capability.SEARCH);
            RepositorySnapshot result;
            if (snapshot.getExternalSnapshotId() != null && snapshot.getExternalJobId() != null) {
                result = provider.refreshSnapshot(new RepositorySnapshot(
                        snapshot.getExternalSnapshotId(), snapshot.getOrganization().getId(),
                        snapshot.getProject().getId(), snapshot.getRepositoryUrl(), snapshot.getRevision(),
                        snapshot.getProvider(), snapshot.getProviderVersion(),
                        dev.squadx.intelligence.CodeIntelligenceModels.SnapshotStatus.INDEXING,
                        snapshot.getIndexedAt(), snapshot.getExternalJobId()));
            } else {
                result = provider.ensureSnapshot(new SnapshotRequest(
                        snapshot.getOrganization().getId(), snapshot.getProject().getId(),
                        snapshot.getRepositoryUrl(), snapshot.getRevision()));
            }
            snapshot.setExternalSnapshotId(result.id());
            snapshot.setExternalJobId(result.externalJobId());
            snapshot.setProviderVersion(result.providerVersion());
            snapshot.setStatus(result.status() == dev.squadx.intelligence.CodeIntelligenceModels.SnapshotStatus.READY
                    ? IntelligenceSnapshotStatus.READY : IntelligenceSnapshotStatus.INDEXING);
            snapshot.setIndexedAt(result.indexedAt());
            snapshot.setErrorMessage(null);
            job.setStatus(snapshot.getStatus() == IntelligenceSnapshotStatus.READY
                    ? IntelligenceJobStatus.COMPLETED : IntelligenceJobStatus.RUNNING);
            if (job.getStatus() == IntelligenceJobStatus.COMPLETED) job.setCompletedAt(Instant.now());
            metrics.indexJob(snapshot.getProvider(), job.getStatus().name(), elapsed(startedNanos));
        } catch (Exception e) {
            snapshot.setStatus(IntelligenceSnapshotStatus.FAILED);
            snapshot.setErrorMessage(truncate(e.getMessage()));
            job.setStatus(IntelligenceJobStatus.FAILED);
            job.setErrorMessage(truncate(e.getMessage()));
            job.setCompletedAt(Instant.now());
            log.warn("Code intelligence indexing failed for snapshot {}: {}", snapshot.getId(), e.getMessage());
            metrics.indexJob(snapshot.getProvider(), "failed", elapsed(startedNanos));
        }
        snapshotRepository.save(snapshot);
        jobRepository.save(job);
    }

    private long elapsed(long startedNanos) { return (System.nanoTime() - startedNanos) / 1_000_000; }

    private String truncate(String value) {
        if (value == null) return "Unknown provider failure";
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
