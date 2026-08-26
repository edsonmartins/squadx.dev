package dev.squadx.service;

import dev.squadx.dto.intelligence.EnsureSnapshotRequest;
import dev.squadx.dto.intelligence.SnapshotResponse;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.*;
import dev.squadx.model.enums.IntelligenceJobStatus;
import dev.squadx.model.enums.IntelligenceSnapshotStatus;
import dev.squadx.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CodeIntelligenceSnapshotService {

    private final ProjectRepository projectRepository;
    private final OrganizationMemberRepository memberRepository;
    private final CodeIntelligenceSnapshotRepository snapshotRepository;
    private final CodeIntelligenceIndexJobRepository jobRepository;

    @Transactional
    public SnapshotResponse ensure(EnsureSnapshotRequest request, User currentUser) {
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        Long organizationId = project.getOrganization().getId();
        if (!memberRepository.existsByOrganizationIdAndUserId(organizationId, currentUser.getId())) {
            throw new ForbiddenException("User does not have access to this organization");
        }

        var existing = snapshotRepository.findByProjectIdAndRevisionAndProvider(
                project.getId(), request.revision().toLowerCase(), request.provider().toLowerCase());
        if (existing.isPresent()) {
            CodeIntelligenceSnapshot snapshot = existing.get();
            Long jobId = activeJobId(snapshot.getId());
            return response(snapshot, jobId, false);
        }

        CodeIntelligenceSnapshot snapshot = snapshotRepository.save(CodeIntelligenceSnapshot.builder()
                .project(project)
                .organization(project.getOrganization())
                .repositoryUrl(project.getRepositoryUrl())
                .revision(request.revision().toLowerCase())
                .provider(request.provider().toLowerCase())
                .status(IntelligenceSnapshotStatus.PENDING)
                .build());
        CodeIntelligenceIndexJob job = jobRepository.save(CodeIntelligenceIndexJob.builder()
                .snapshot(snapshot)
                .status(IntelligenceJobStatus.PENDING)
                .build());
        return response(snapshot, job.getId(), true);
    }

    private Long activeJobId(Long snapshotId) {
        return jobRepository.findBySnapshotIdAndStatusIn(snapshotId,
                        List.of(IntelligenceJobStatus.PENDING, IntelligenceJobStatus.RUNNING))
                .stream().findFirst().map(CodeIntelligenceIndexJob::getId).orElse(null);
    }

    private SnapshotResponse response(CodeIntelligenceSnapshot snapshot, Long jobId, boolean created) {
        return new SnapshotResponse(snapshot.getId(), snapshot.getProject().getId(),
                snapshot.getOrganization().getId(), snapshot.getRepositoryUrl(), snapshot.getRevision(),
                snapshot.getProvider(), snapshot.getProviderVersion(), snapshot.getStatus(),
                snapshot.getIndexedAt(), jobId, created);
    }
}

