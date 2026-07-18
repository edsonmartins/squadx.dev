package dev.squadx.service;

import dev.squadx.dto.approval.ApprovalResponse;
import dev.squadx.dto.approval.CreateApprovalRequest;
import dev.squadx.dto.approval.ReviewApprovalRequest;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.*;
import dev.squadx.model.enums.ApprovalStatus;
import dev.squadx.model.enums.ApprovalType;
import dev.squadx.model.enums.TaskStatus;
import dev.squadx.model.enums.UserRole;
import dev.squadx.repository.ApprovalRepository;
import dev.squadx.repository.ExecutionRepository;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock
    private ApprovalRepository approvalRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ExecutionRepository executionRepository;

    @Mock
    private OrganizationMemberRepository memberRepository;

    @InjectMocks
    private ApprovalService approvalService;

    private User testUser;
    private User testReviewer;
    private Task testTask;
    private Execution testExecution;
    private Approval testApproval;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .fullName("Test User")
                .role(UserRole.USER)
                .isActive(true)
                .build();
        testUser.setId(1L);

        testReviewer = User.builder()
                .email("reviewer@example.com")
                .fullName("Reviewer")
                .role(UserRole.USER)
                .isActive(true)
                .build();
        testReviewer.setId(2L);

        Organization organization = Organization.builder().build();
        organization.setId(1L);
        Project project = Project.builder().organization(organization).build();
        project.setId(1L);

        testTask = Task.builder()
                .title("Task")
                .status(TaskStatus.IN_REVIEW)
                .project(project)
                .createdBy(testUser)
                .build();
        testTask.setId(1L);

        testExecution = Execution.builder()
                .task(testTask)
                .gitCommit("abc123")
                .gitBranch("squadx/task-1")
                .build();
        testExecution.setId(1L);

        // Both requester and reviewer are members of the task's organization by default.
        lenient().when(memberRepository.existsByOrganizationIdAndUserId(eq(1L), any()))
                .thenReturn(true);

        testApproval = Approval.builder()
                .task(testTask)
                .requestedBy(testUser)
                .approvalType(ApprovalType.COMMIT)
                .status(ApprovalStatus.PENDING)
                .title("Deploy approval")
                .build();
        testApproval.setId(1L);
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("should create approval without execution")
        void shouldCreateApprovalWithoutExecution() {
            CreateApprovalRequest request = new CreateApprovalRequest();
            request.setTaskId(1L);
            request.setApprovalType(ApprovalType.COMMIT);
            request.setTitle("Deploy approval");
            request.setDescription("Please review");

            when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
            when(approvalRepository.save(any(Approval.class))).thenAnswer(invocation -> {
                Approval saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            ApprovalResponse response = approvalService.create(request, testUser);

            assertThat(response).isNotNull();
            assertThat(response.getTaskId()).isEqualTo(1L);
            assertThat(response.getExecutionId()).isNull();
            assertThat(response.getApprovalType()).isEqualTo(ApprovalType.COMMIT);
            assertThat(response.getTitle()).isEqualTo("Deploy approval");
            assertThat(response.getStatus()).isEqualTo(ApprovalStatus.PENDING);

            verify(approvalRepository).save(any(Approval.class));
            verify(executionRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should create approval with execution")
        void shouldCreateApprovalWithExecution() {
            CreateApprovalRequest request = new CreateApprovalRequest();
            request.setTaskId(1L);
            request.setExecutionId(1L);
            request.setApprovalType(ApprovalType.DEPLOY);
            request.setTitle("Deploy approval");

            when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
            when(executionRepository.findById(1L)).thenReturn(Optional.of(testExecution));
            when(approvalRepository.save(any(Approval.class))).thenAnswer(invocation -> {
                Approval saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            ApprovalResponse response = approvalService.create(request, testUser);

            assertThat(response).isNotNull();
            assertThat(response.getExecutionId()).isEqualTo(1L);

            verify(executionRepository).findById(1L);
            verify(approvalRepository).save(any(Approval.class));
        }

        @Test
        @DisplayName("should throw when task not found")
        void shouldThrowWhenTaskNotFound() {
            CreateApprovalRequest request = new CreateApprovalRequest();
            request.setTaskId(99L);
            request.setTitle("Approval");

            when(taskRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> approvalService.create(request, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Task not found");

            verify(approvalRepository, never()).save(any(Approval.class));
        }

        @Test
        @DisplayName("should throw when execution not found")
        void shouldThrowWhenExecutionNotFound() {
            CreateApprovalRequest request = new CreateApprovalRequest();
            request.setTaskId(1L);
            request.setExecutionId(99L);
            request.setTitle("Approval");

            when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
            when(executionRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> approvalService.create(request, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Execution not found");

            verify(approvalRepository, never()).save(any(Approval.class));
        }
    }

    @Nested
    @DisplayName("review()")
    class Review {

        @Test
        @DisplayName("should approve successfully")
        void shouldApproveSuccessfully() {
            ReviewApprovalRequest request = new ReviewApprovalRequest();
            request.setApproved(true);
            request.setReviewComment("Looks good");

            when(approvalRepository.findById(1L)).thenReturn(Optional.of(testApproval));
            when(approvalRepository.save(any(Approval.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ApprovalResponse response = approvalService.review(1L, request, testReviewer);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
            assertThat(testApproval.getReviewedAt()).isNotNull();
            assertThat(testApproval.getReviewer()).isEqualTo(testReviewer);
            assertThat(testApproval.getReviewComment()).isEqualTo("Looks good");
            // Approval drives the task to DONE (IN_REVIEW -> DONE is valid).
            assertThat(testTask.getStatus()).isEqualTo(TaskStatus.DONE);
            assertThat(testTask.getCompletedAt()).isNotNull();

            verify(approvalRepository).save(any(Approval.class));
            verify(taskRepository).save(testTask);
        }

        @Test
        @DisplayName("should reject successfully")
        void shouldRejectSuccessfully() {
            ReviewApprovalRequest request = new ReviewApprovalRequest();
            request.setApproved(false);
            request.setReviewComment("Needs changes");

            when(approvalRepository.findById(1L)).thenReturn(Optional.of(testApproval));
            when(approvalRepository.save(any(Approval.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ApprovalResponse response = approvalService.review(1L, request, testReviewer);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
            assertThat(testApproval.getReviewedAt()).isNotNull();
            // Rejection reopens the task (IN_REVIEW -> IN_PROGRESS).
            assertThat(testTask.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);

            verify(approvalRepository).save(any(Approval.class));
            verify(taskRepository).save(testTask);
        }

        @Test
        @DisplayName("should leave task unchanged when transition is invalid")
        void shouldLeaveTaskUnchangedWhenTransitionInvalid() {
            // A DONE task cannot go to DONE again; approval still records but task state is untouched.
            testTask.setStatus(TaskStatus.DONE);
            ReviewApprovalRequest request = new ReviewApprovalRequest();
            request.setApproved(true);

            when(approvalRepository.findById(1L)).thenReturn(Optional.of(testApproval));
            when(approvalRepository.save(any(Approval.class))).thenAnswer(invocation -> invocation.getArgument(0));

            approvalService.review(1L, request, testReviewer);

            assertThat(testTask.getStatus()).isEqualTo(TaskStatus.DONE);
            verify(taskRepository, never()).save(any(Task.class));
        }

        @Test
        @DisplayName("should throw Forbidden when reviewer is not an org member")
        void shouldThrowForbiddenWhenReviewerNotMember() {
            when(approvalRepository.findById(1L)).thenReturn(Optional.of(testApproval));
            when(memberRepository.existsByOrganizationIdAndUserId(eq(1L), eq(2L))).thenReturn(false);

            ReviewApprovalRequest request = new ReviewApprovalRequest();
            request.setApproved(true);

            assertThatThrownBy(() -> approvalService.review(1L, request, testReviewer))
                    .isInstanceOf(ForbiddenException.class);

            verify(approvalRepository, never()).save(any(Approval.class));
        }

        @Test
        @DisplayName("should throw when approval is not pending")
        void shouldThrowWhenNotPending() {
            testApproval.setStatus(ApprovalStatus.APPROVED);

            ReviewApprovalRequest request = new ReviewApprovalRequest();
            request.setApproved(true);

            when(approvalRepository.findById(1L)).thenReturn(Optional.of(testApproval));

            assertThatThrownBy(() -> approvalService.review(1L, request, testReviewer))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Approval is not in PENDING status");

            verify(approvalRepository, never()).save(any(Approval.class));
        }

        @Test
        @DisplayName("should throw when approval not found")
        void shouldThrowWhenApprovalNotFound() {
            ReviewApprovalRequest request = new ReviewApprovalRequest();
            request.setApproved(true);

            when(approvalRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> approvalService.review(99L, request, testReviewer))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Approval not found");
        }
    }

    @Nested
    @DisplayName("autoCreateForCompletedExecution()")
    class AutoCreate {

        @Test
        @DisplayName("should create a PENDING COMMIT approval attributed to the task creator")
        void shouldCreatePendingCommitApproval() {
            when(approvalRepository.save(any(Approval.class))).thenAnswer(invocation -> {
                Approval saved = invocation.getArgument(0);
                saved.setId(5L);
                return saved;
            });

            Approval approval = approvalService.autoCreateForCompletedExecution(testTask, testExecution);

            assertThat(approval).isNotNull();
            assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.PENDING);
            assertThat(approval.getApprovalType()).isEqualTo(ApprovalType.COMMIT);
            assertThat(approval.getRequestedBy()).isEqualTo(testUser);
            assertThat(approval.getChangesSummary()).contains("abc123");

            verify(approvalRepository).save(any(Approval.class));
        }

        @Test
        @DisplayName("should return null and skip when the task has no creator")
        void shouldReturnNullWhenNoCreator() {
            testTask.setCreatedBy(null);

            Approval approval = approvalService.autoCreateForCompletedExecution(testTask, testExecution);

            assertThat(approval).isNull();
            verify(approvalRepository, never()).save(any(Approval.class));
        }
    }

    @Nested
    @DisplayName("cancel()")
    class CancelApproval {

        @Test
        @DisplayName("should cancel successfully when requester cancels own approval")
        void shouldCancelSuccessfully() {
            when(approvalRepository.findById(1L)).thenReturn(Optional.of(testApproval));
            when(approvalRepository.save(any(Approval.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ApprovalResponse response = approvalService.cancel(1L, testUser);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);

            verify(approvalRepository).save(any(Approval.class));
        }

        @Test
        @DisplayName("should throw when approval is not pending")
        void shouldThrowWhenNotPending() {
            testApproval.setStatus(ApprovalStatus.APPROVED);

            when(approvalRepository.findById(1L)).thenReturn(Optional.of(testApproval));

            assertThatThrownBy(() -> approvalService.cancel(1L, testUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Only PENDING approvals can be cancelled");

            verify(approvalRepository, never()).save(any(Approval.class));
        }

        @Test
        @DisplayName("should throw when non-requester tries to cancel")
        void shouldThrowWhenNonRequesterTriesToCancel() {
            when(approvalRepository.findById(1L)).thenReturn(Optional.of(testApproval));

            assertThatThrownBy(() -> approvalService.cancel(1L, testReviewer))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Only the requester can cancel an approval");

            verify(approvalRepository, never()).save(any(Approval.class));
        }
    }

    @Nested
    @DisplayName("cross-tenant access control")
    class CrossTenantAccess {

        @Test
        @DisplayName("getById denies a user who is not a member of the approval's organization")
        void getById_deniesNonMember() {
            User nonMember = User.builder()
                    .email("intruder@example.com")
                    .fullName("Intruder")
                    .role(UserRole.USER)
                    .isActive(true)
                    .build();
            nonMember.setId(3L);

            when(approvalRepository.findById(1L)).thenReturn(Optional.of(testApproval));
            when(memberRepository.existsByOrganizationIdAndUserId(eq(1L), eq(3L))).thenReturn(false);

            assertThatThrownBy(() -> approvalService.getById(1L, nonMember))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("getByStatus filters out approvals from organizations the user is not a member of")
        void getByStatus_filtersNonMemberOrgs() {
            // Second approval belongs to a different organization (id 2) the user is NOT in.
            Organization otherOrg = Organization.builder().build();
            otherOrg.setId(2L);
            Project otherProject = Project.builder().organization(otherOrg).build();
            otherProject.setId(2L);
            Task otherTask = Task.builder()
                    .title("Other Task")
                    .status(TaskStatus.IN_REVIEW)
                    .project(otherProject)
                    .createdBy(testUser)
                    .build();
            otherTask.setId(2L);

            Approval otherApproval = Approval.builder()
                    .task(otherTask)
                    .requestedBy(testUser)
                    .approvalType(ApprovalType.COMMIT)
                    .status(ApprovalStatus.PENDING)
                    .title("Other approval")
                    .build();
            otherApproval.setId(2L);

            Pageable pageable = PageRequest.of(0, 10);
            when(approvalRepository.findByStatus(ApprovalStatus.PENDING, pageable))
                    .thenReturn(new PageImpl<>(List.of(testApproval, otherApproval), pageable, 2));
            // testUser is a member of org 1 (lenient stub) but not org 2 (unstubbed -> false).

            var result = approvalService.getByStatus(ApprovalStatus.PENDING, pageable, testUser);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(1L);
        }
    }
}
