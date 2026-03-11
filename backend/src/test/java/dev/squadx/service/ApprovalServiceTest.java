package dev.squadx.service;

import dev.squadx.dto.approval.ApprovalResponse;
import dev.squadx.dto.approval.CreateApprovalRequest;
import dev.squadx.dto.approval.ReviewApprovalRequest;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.*;
import dev.squadx.model.enums.ApprovalStatus;
import dev.squadx.model.enums.ApprovalType;
import dev.squadx.model.enums.UserRole;
import dev.squadx.repository.ApprovalRepository;
import dev.squadx.repository.ExecutionRepository;
import dev.squadx.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock
    private ApprovalRepository approvalRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ExecutionRepository executionRepository;

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

        testTask = Task.builder()
                .title("Task")
                .build();
        testTask.setId(1L);

        testExecution = Execution.builder()
                .task(testTask)
                .build();
        testExecution.setId(1L);

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

            verify(approvalRepository).save(any(Approval.class));
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

            verify(approvalRepository).save(any(Approval.class));
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
}
