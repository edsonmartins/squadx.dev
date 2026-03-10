package dev.squadx.service;

import dev.squadx.dto.approval.ApprovalResponse;
import dev.squadx.dto.approval.CreateApprovalRequest;
import dev.squadx.dto.approval.ReviewApprovalRequest;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Approval;
import dev.squadx.model.Execution;
import dev.squadx.model.Task;
import dev.squadx.model.User;
import dev.squadx.model.enums.ApprovalStatus;
import dev.squadx.model.enums.ApprovalType;
import dev.squadx.repository.ApprovalRepository;
import dev.squadx.repository.ExecutionRepository;
import dev.squadx.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalRepository approvalRepository;
    private final TaskRepository taskRepository;
    private final ExecutionRepository executionRepository;

    @Transactional
    public ApprovalResponse create(CreateApprovalRequest request, User requestedBy) {
        Task task = taskRepository.findById(request.getTaskId())
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        Execution execution = null;
        if (request.getExecutionId() != null) {
            execution = executionRepository.findById(request.getExecutionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Execution not found"));
        }

        Approval approval = Approval.builder()
                .task(task)
                .execution(execution)
                .requestedBy(requestedBy)
                .approvalType(request.getApprovalType() != null ? request.getApprovalType() : ApprovalType.COMMIT)
                .title(request.getTitle())
                .description(request.getDescription())
                .changesSummary(request.getChangesSummary())
                .build();

        approval = approvalRepository.save(approval);
        return mapToResponse(approval);
    }

    @Transactional
    public ApprovalResponse review(Long approvalId, ReviewApprovalRequest request, User reviewer) {
        Approval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval not found"));

        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new BadRequestException("Approval is not in PENDING status");
        }

        approval.setReviewer(reviewer);
        approval.setStatus(request.getApproved() ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        approval.setReviewComment(request.getReviewComment());
        approval.setReviewedAt(Instant.now());

        approval = approvalRepository.save(approval);
        return mapToResponse(approval);
    }

    @Transactional
    public ApprovalResponse cancel(Long approvalId, User user) {
        Approval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval not found"));

        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new BadRequestException("Only PENDING approvals can be cancelled");
        }

        if (!approval.getRequestedBy().getId().equals(user.getId())) {
            throw new BadRequestException("Only the requester can cancel an approval");
        }

        approval.setStatus(ApprovalStatus.CANCELLED);
        approval = approvalRepository.save(approval);
        return mapToResponse(approval);
    }

    public ApprovalResponse getById(Long id) {
        Approval approval = approvalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Approval not found"));
        return mapToResponse(approval);
    }

    public Page<ApprovalResponse> getByTask(Long taskId, Pageable pageable) {
        return approvalRepository.findByTaskId(taskId, pageable).map(this::mapToResponse);
    }

    public Page<ApprovalResponse> getPendingForUser(Long userId, Pageable pageable) {
        return approvalRepository.findPendingForUser(userId, pageable).map(this::mapToResponse);
    }

    public Page<ApprovalResponse> getByStatus(ApprovalStatus status, Pageable pageable) {
        return approvalRepository.findByStatus(status, pageable).map(this::mapToResponse);
    }

    private ApprovalResponse mapToResponse(Approval approval) {
        return ApprovalResponse.builder()
                .id(approval.getId())
                .taskId(approval.getTask().getId())
                .taskTitle(approval.getTask().getTitle())
                .executionId(approval.getExecution() != null ? approval.getExecution().getId() : null)
                .requestedById(approval.getRequestedBy().getId())
                .requestedByName(approval.getRequestedBy().getFullName())
                .reviewerId(approval.getReviewer() != null ? approval.getReviewer().getId() : null)
                .reviewerName(approval.getReviewer() != null ? approval.getReviewer().getFullName() : null)
                .status(approval.getStatus())
                .approvalType(approval.getApprovalType())
                .title(approval.getTitle())
                .description(approval.getDescription())
                .changesSummary(approval.getChangesSummary())
                .reviewComment(approval.getReviewComment())
                .reviewedAt(approval.getReviewedAt())
                .expiresAt(approval.getExpiresAt())
                .createdAt(approval.getCreatedAt())
                .build();
    }
}
