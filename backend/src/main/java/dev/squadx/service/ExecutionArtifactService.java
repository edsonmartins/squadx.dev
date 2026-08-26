package dev.squadx.service;

import dev.squadx.dto.artifact.ExecutionArtifactResponse;
import dev.squadx.dto.artifact.PublishArtifactRequest;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Execution;
import dev.squadx.model.ExecutionArtifact;
import dev.squadx.model.User;
import dev.squadx.repository.ExecutionArtifactRepository;
import dev.squadx.repository.ExecutionRepository;
import dev.squadx.repository.OrganizationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExecutionArtifactService {
    private final ExecutionArtifactRepository artifactRepository;
    private final ExecutionRepository executionRepository;
    private final OrganizationMemberRepository memberRepository;

    @Transactional
    public ExecutionArtifactResponse publish(Long executionId, PublishArtifactRequest request, User currentUser) {
        Execution execution = requireAccessibleExecution(executionId, currentUser);
        ExecutionArtifact artifact = artifactRepository
                .findByExecutionIdAndArtifactKey(executionId, request.getArtifactKey())
                .orElseGet(() -> ExecutionArtifact.builder()
                        .execution(execution)
                        .artifactKey(request.getArtifactKey())
                        .build());

        artifact.setType(request.getType());
        artifact.setFormat(request.getFormat());
        artifact.setName(request.getName());
        artifact.setGitRevision(request.getGitRevision());
        artifact.setBaseRevision(request.getBaseRevision());
        artifact.setArtifactGroup(request.getArtifactGroup());
        artifact.setViewRole(request.getViewRole());
        artifact.setEvidenceJson(request.getEvidenceJson());
        artifact.setContent(request.getContent());
        artifact.setChecksumSha256(sha256(request.getContent()));
        return map(artifactRepository.save(artifact), true);
    }

    @Transactional(readOnly = true)
    public List<ExecutionArtifactResponse> list(Long executionId, User currentUser) {
        requireAccessibleExecution(executionId, currentUser);
        return artifactRepository.findByExecutionIdOrderByCreatedAtDesc(executionId).stream()
                .map(artifact -> map(artifact, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public ExecutionArtifactResponse get(Long artifactId, User currentUser) {
        ExecutionArtifact artifact = artifactRepository.findById(artifactId)
                .orElseThrow(() -> new ResourceNotFoundException("Execution artifact not found"));
        validateUserAccess(artifact.getExecution(), currentUser);
        return map(artifact, true);
    }

    @Transactional(readOnly = true)
    public ExecutionArtifactResponse getArchitectureBaseline(Long executionId, User currentUser) {
        Execution execution = requireAccessibleExecution(executionId, currentUser);
        return artifactRepository
                .findFirstByExecutionTaskProjectIdAndExecutionIdNotAndTypeAndFormatAndViewRoleInOrderByCreatedAtDesc(
                        execution.getTask().getProject().getId(), executionId,
                        "ARCHITECTURE_MAP", "JSON", List.of("HEAD", "CURRENT"))
                .map(artifact -> map(artifact, true))
                .orElse(null);
    }

    private Execution requireAccessibleExecution(Long executionId, User currentUser) {
        Execution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found"));
        validateUserAccess(execution, currentUser);
        return execution;
    }

    private void validateUserAccess(Execution execution, User currentUser) {
        Long organizationId = execution.getTask().getProject().getOrganization().getId();
        if (currentUser == null || !memberRepository.existsByOrganizationIdAndUserId(organizationId, currentUser.getId())) {
            throw new ForbiddenException("You do not have access to this execution");
        }
    }

    private static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static ExecutionArtifactResponse map(ExecutionArtifact artifact, boolean includeContent) {
        return ExecutionArtifactResponse.builder()
                .id(artifact.getId())
                .executionId(artifact.getExecution().getId())
                .artifactKey(artifact.getArtifactKey())
                .type(artifact.getType())
                .format(artifact.getFormat())
                .name(artifact.getName())
                .gitRevision(artifact.getGitRevision())
                .baseRevision(artifact.getBaseRevision())
                .artifactGroup(artifact.getArtifactGroup())
                .viewRole(artifact.getViewRole())
                .checksumSha256(artifact.getChecksumSha256())
                .evidenceJson(artifact.getEvidenceJson())
                .content(includeContent ? artifact.getContent() : null)
                .createdAt(artifact.getCreatedAt())
                .updatedAt(artifact.getUpdatedAt())
                .build();
    }
}
