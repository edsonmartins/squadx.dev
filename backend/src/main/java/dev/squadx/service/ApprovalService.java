package dev.squadx.service;

import dev.squadx.dto.approval.ApprovalResponse;
import dev.squadx.dto.approval.CreateApprovalRequest;
import dev.squadx.dto.approval.ReviewApprovalRequest;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Approval;
import dev.squadx.model.Execution;
import dev.squadx.model.Task;
import dev.squadx.model.User;
import dev.squadx.model.enums.ApprovalStatus;
import dev.squadx.model.enums.ApprovalType;
import dev.squadx.model.enums.TaskStatus;
import dev.squadx.model.vo.TaskStatusTransition;
import dev.squadx.repository.ApprovalRepository;
import dev.squadx.repository.ExecutionRepository;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private final ApprovalRepository approvalRepository;
    private final TaskRepository taskRepository;
    private final ExecutionRepository executionRepository;
    private final OrganizationMemberRepository memberRepository;

    @Transactional
    public ApprovalResponse create(CreateApprovalRequest request, User requestedBy) {
        Task task = taskRepository.findById(request.getTaskId())
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        validateUserAccess(task.getProject().getOrganization().getId(), requestedBy.getId());

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
        validateUserAccess(approval.getTask().getProject().getOrganization().getId(), reviewer.getId());

        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new BadRequestException("Approval is not in PENDING status");
        }

        boolean approved = Boolean.TRUE.equals(request.getApproved());
        approval.setReviewer(reviewer);
        approval.setStatus(approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        approval.setReviewComment(request.getReviewComment());
        approval.setReviewedAt(Instant.now());
        approval = approvalRepository.save(approval);

        // Drive the linked task's state from the human decision (ADR-0004 gate).
        // APPROVED → DONE; REJECTED → reopen (IN_PROGRESS). Only when the transition is valid,
        // so an approval reviewed while the task already moved on is a no-op on state.
        applyDecisionToTask(approval.getTask(), approved);

        return mapToResponse(approval);
    }

    private void applyDecisionToTask(Task task, boolean approved) {
        TaskStatus target = approved ? TaskStatus.DONE : TaskStatus.IN_PROGRESS;
        if (task.getStatus() == target) {
            return;
        }
        if (!TaskStatusTransition.isValid(task.getStatus(), target)) {
            log.warn("Approval decision for task {} could not transition {} -> {} (invalid); leaving state unchanged",
                    task.getId(), task.getStatus(), target);
            return;
        }
        task.setStatus(target);
        if (target == TaskStatus.DONE) {
            task.setCompletedAt(Instant.now());
        }
        taskRepository.save(task);
    }

    /**
     * Creates a PENDING approval for a completed execution whose task opts into the gate
     * (task.requiresApproval). System-initiated (triggered by a trusted daemon completion event),
     * so there is no acting user to authorize — the task creator is recorded as the requester.
     * Returns null when the task has no creator to attribute the request to.
     */
    @Transactional
    public Approval autoCreateForCompletedExecution(Task task, Execution execution) {
        User requestedBy = task.getCreatedBy();
        if (requestedBy == null) {
            log.warn("Task {} requires approval but has no createdBy; skipping auto-approval", task.getId());
            return null;
        }
        String changes = execution != null && execution.getGitCommit() != null
                ? "Commit " + execution.getGitCommit()
                    + (execution.getGitBranch() != null ? " on " + execution.getGitBranch() : "")
                : null;
        Approval approval = Approval.builder()
                .task(task)
                .execution(execution)
                .requestedBy(requestedBy)
                .approvalType(ApprovalType.COMMIT)
                .title("Review completed work for: " + task.getTitle())
                .changesSummary(changes)
                .build();
        return approvalRepository.save(approval);
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

    public ApprovalResponse getById(Long id, User user) {
        Approval approval = approvalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Approval not found"));
        validateUserAccess(approval.getTask().getProject().getOrganization().getId(), user.getId());
        return mapToResponse(approval);
    }

    public Page<ApprovalResponse> getByTask(Long taskId, Pageable pageable, User user) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        validateUserAccess(task.getProject().getOrganization().getId(), user.getId());
        return approvalRepository.findByTaskId(taskId, pageable).map(this::mapToResponse);
    }

    public Page<ApprovalResponse> getPendingForUser(Long userId, Pageable pageable) {
        return approvalRepository.findPendingForUser(userId, pageable).map(this::mapToResponse);
    }

    public Page<ApprovalResponse> getByStatus(ApprovalStatus status, Pageable pageable, User user) {
        Page<Approval> page = approvalRepository.findByStatus(status, pageable);
        java.util.List<ApprovalResponse> scoped = page.getContent().stream()
                .filter(a -> memberRepository.existsByOrganizationIdAndUserId(
                        a.getTask().getProject().getOrganization().getId(), user.getId()))
                .map(this::mapToResponse)
                .toList();
        return new org.springframework.data.domain.PageImpl<>(scoped, pageable, scoped.size());
    }

    private void validateUserAccess(Long organizationId, Long userId) {
        if (!memberRepository.existsByOrganizationIdAndUserId(organizationId, userId)) {
            throw new ForbiddenException("User does not have access to this organization");
        }
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
