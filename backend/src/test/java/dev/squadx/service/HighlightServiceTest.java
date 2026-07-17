package dev.squadx.service;

import dev.squadx.dto.highlight.HighlightResponse;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.*;
import dev.squadx.model.enums.HighlightType;
import dev.squadx.model.enums.UserRole;
import dev.squadx.repository.ExecutionLogRepository;
import dev.squadx.repository.SessionHighlightRepository;
import dev.squadx.repository.SessionRecordingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HighlightServiceTest {

    @Mock
    private SessionHighlightRepository highlightRepository;

    @Mock
    private SessionRecordingRepository recordingRepository;

    @Mock
    private ExecutionLogRepository executionLogRepository;

    @Mock
    private OrganizationAccessGuard accessGuard;

    @InjectMocks
    private HighlightService highlightService;

    private SessionRecording testRecording;
    private Execution testExecution;
    private LiveSession testSession;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .fullName("Test User")
                .role(UserRole.USER)
                .isActive(true)
                .build();
        testUser.setId(1L);

        Organization org = Organization.builder().name("Org").slug("org").build();
        org.setId(1L);
        Project project = Project.builder().name("Proj").slug("proj").organization(org).build();
        project.setId(1L);
        Task task = Task.builder().title("Task").project(project).build();
        task.setId(1L);

        testExecution = new Execution();
        testExecution.setId(1L);

        testSession = new LiveSession();
        testSession.setId(1L);
        testSession.setExecution(testExecution);
        testSession.setTask(task);

        testRecording = SessionRecording.builder()
                .session(testSession)
                .s3Key("recordings/test.webm")
                .s3Bucket("test-bucket")
                .durationSeconds(600)
                .startedAt(Instant.parse("2025-01-01T10:00:00Z"))
                .build();
        testRecording.setId(1L);
    }

    @Nested
    @DisplayName("getByRecording()")
    class GetByRecording {

        @Test
        @DisplayName("should return highlights ordered by timestamp")
        void shouldReturnHighlightsForRecording() {
            SessionHighlight highlight = SessionHighlight.builder()
                    .recording(testRecording)
                    .highlightType(HighlightType.ERROR_DETECTED)
                    .timestampSeconds(120)
                    .title("Error detected")
                    .description("NullPointerException")
                    .confidence(0.75f)
                    .build();
            highlight.setId(1L);

            when(recordingRepository.findById(1L)).thenReturn(Optional.of(testRecording));
            when(highlightRepository.findByRecordingIdOrderByTimestampSecondsAsc(1L))
                    .thenReturn(List.of(highlight));

            List<HighlightResponse> results = highlightService.getByRecording(1L, testUser);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getHighlightType()).isEqualTo(HighlightType.ERROR_DETECTED);
            assertThat(results.get(0).getTimestampSeconds()).isEqualTo(120);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when recording not found")
        void shouldThrowWhenRecordingNotFound() {
            when(recordingRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> highlightService.getByRecording(99L, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Recording not found");
        }
    }

    @Nested
    @DisplayName("generateHighlights()")
    class GenerateHighlights {

        @Test
        @DisplayName("should detect error patterns from execution logs")
        void shouldDetectErrorFromLogs() {
            ExecutionLog errorLog = ExecutionLog.builder()
                    .execution(testExecution)
                    .level("ERROR")
                    .message("NullPointerException in UserService")
                    .build();
            errorLog.setCreatedAt(Instant.parse("2025-01-01T10:02:00Z"));

            when(recordingRepository.findById(1L)).thenReturn(Optional.of(testRecording));
            when(executionLogRepository.findByExecutionIdOrderByCreatedAtAsc(1L))
                    .thenReturn(List.of(errorLog));
            when(highlightRepository.saveAll(anyList())).thenAnswer(invocation -> {
                List<SessionHighlight> highlights = invocation.getArgument(0);
                for (int i = 0; i < highlights.size(); i++) {
                    highlights.get(i).setId((long) (i + 1));
                }
                return highlights;
            });

            List<HighlightResponse> results = highlightService.generateHighlights(1L, testUser);

            assertThat(results).isNotEmpty();
            assertThat(results).anyMatch(h -> h.getHighlightType() == HighlightType.ERROR_DETECTED);
        }

        @Test
        @DisplayName("should detect test passed patterns from client logs")
        void shouldDetectTestPassedFromClientLogs() {
            when(recordingRepository.findById(1L)).thenReturn(Optional.of(testRecording));
            when(executionLogRepository.findByExecutionIdOrderByCreatedAtAsc(1L))
                    .thenReturn(List.of());
            when(highlightRepository.saveAll(anyList())).thenAnswer(invocation -> {
                List<SessionHighlight> highlights = invocation.getArgument(0);
                for (int i = 0; i < highlights.size(); i++) {
                    highlights.get(i).setId((long) (i + 1));
                }
                return highlights;
            });

            List<String> clientLogs = List.of("All tests passed successfully", "BUILD SUCCESS");

            List<HighlightResponse> results = highlightService.generateHighlights(1L, clientLogs, testUser);

            assertThat(results).isNotEmpty();
            assertThat(results).anyMatch(h -> h.getHighlightType() == HighlightType.TEST_PASSED);
        }

        @Test
        @DisplayName("should detect commit patterns from client logs")
        void shouldDetectCommitFromClientLogs() {
            when(recordingRepository.findById(1L)).thenReturn(Optional.of(testRecording));
            when(executionLogRepository.findByExecutionIdOrderByCreatedAtAsc(1L))
                    .thenReturn(List.of());
            when(highlightRepository.saveAll(anyList())).thenAnswer(invocation -> {
                List<SessionHighlight> highlights = invocation.getArgument(0);
                for (int i = 0; i < highlights.size(); i++) {
                    highlights.get(i).setId((long) (i + 1));
                }
                return highlights;
            });

            List<String> clientLogs = List.of("git commit -m 'fix: resolve login issue'");

            List<HighlightResponse> results = highlightService.generateHighlights(1L, clientLogs, testUser);

            assertThat(results).isNotEmpty();
            assertThat(results).anyMatch(h -> h.getHighlightType() == HighlightType.COMMIT_MADE);
        }

        @Test
        @DisplayName("should detect deploy patterns from client logs")
        void shouldDetectDeployFromClientLogs() {
            when(recordingRepository.findById(1L)).thenReturn(Optional.of(testRecording));
            when(executionLogRepository.findByExecutionIdOrderByCreatedAtAsc(1L))
                    .thenReturn(List.of());
            when(highlightRepository.saveAll(anyList())).thenAnswer(invocation -> {
                List<SessionHighlight> highlights = invocation.getArgument(0);
                for (int i = 0; i < highlights.size(); i++) {
                    highlights.get(i).setId((long) (i + 1));
                }
                return highlights;
            });

            List<String> clientLogs = List.of("Deployed to production successfully");

            List<HighlightResponse> results = highlightService.generateHighlights(1L, clientLogs, testUser);

            assertThat(results).isNotEmpty();
            assertThat(results).anyMatch(h -> h.getHighlightType() == HighlightType.DEPLOY);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when recording not found")
        void shouldThrowWhenRecordingNotFound() {
            when(recordingRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> highlightService.generateHighlights(99L, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Recording not found");
        }
    }

    @Nested
    @DisplayName("getSummary()")
    class GetSummary {

        @Test
        @DisplayName("should return summary with highlight counts and timeline")
        void shouldReturnSummaryWithHighlights() {
            SessionHighlight errorHighlight = SessionHighlight.builder()
                    .recording(testRecording)
                    .highlightType(HighlightType.ERROR_DETECTED)
                    .timestampSeconds(120)
                    .title("Error detected")
                    .description("NullPointerException")
                    .confidence(0.75f)
                    .build();

            SessionHighlight commitHighlight = SessionHighlight.builder()
                    .recording(testRecording)
                    .highlightType(HighlightType.COMMIT_MADE)
                    .timestampSeconds(300)
                    .title("Commit made")
                    .description("git commit -m 'fix'")
                    .confidence(0.85f)
                    .build();

            when(recordingRepository.findById(1L)).thenReturn(Optional.of(testRecording));
            when(highlightRepository.findByRecordingIdOrderByTimestampSecondsAsc(1L))
                    .thenReturn(List.of(errorHighlight, commitHighlight));

            String summary = highlightService.getSummary(1L, testUser);

            assertThat(summary).contains("Session Summary");
            assertThat(summary).contains("Total highlights: 2");
            assertThat(summary).contains("Errors detected: 1");
            assertThat(summary).contains("Commits made: 1");
            assertThat(summary).contains("Timeline:");
        }

        @Test
        @DisplayName("should return empty summary message when no highlights exist")
        void shouldReturnEmptySummaryWhenNoHighlights() {
            when(recordingRepository.findById(1L)).thenReturn(Optional.of(testRecording));
            when(highlightRepository.findByRecordingIdOrderByTimestampSecondsAsc(1L))
                    .thenReturn(List.of());

            String summary = highlightService.getSummary(1L, testUser);

            assertThat(summary).isEqualTo("No highlights detected for this recording session.");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when recording not found")
        void shouldThrowWhenRecordingNotFound() {
            when(recordingRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> highlightService.getSummary(99L, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Recording not found");
        }
    }

    @Nested
    @DisplayName("cross-tenant access control")
    class CrossTenantAccess {

        @Test
        @DisplayName("getByRecording denies a non-member of the recording's organization")
        void getByRecording_deniesNonMember() {
            when(recordingRepository.findById(1L)).thenReturn(Optional.of(testRecording));
            doThrow(new ForbiddenException("nope"))
                    .when(accessGuard).requireMember(anyLong(), anyLong());

            assertThatThrownBy(() -> highlightService.getByRecording(1L, testUser))
                    .isInstanceOf(ForbiddenException.class);

            verifyNoInteractions(highlightRepository);
        }
    }
}
