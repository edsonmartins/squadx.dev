package dev.squadx.service;

import dev.squadx.config.S3Config;
import dev.squadx.dto.recording.RecordingResponse;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.LiveSession;
import dev.squadx.model.SessionRecording;
import dev.squadx.model.enums.RecordingStatus;
import dev.squadx.repository.LiveSessionRepository;
import dev.squadx.repository.SessionRecordingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordingServiceTest {

    @Mock
    private SessionRecordingRepository recordingRepository;

    @Mock
    private LiveSessionRepository liveSessionRepository;

    @Mock
    private S3Config s3Config;

    @Mock
    private S3Presigner s3Presigner;

    private RecordingService recordingService;

    private LiveSession liveSession;

    @BeforeEach
    void setUp() {
        // RecordingService takes Optional<S3Presigner> -- construct manually
        recordingService = new RecordingService(
                recordingRepository,
                liveSessionRepository,
                s3Config,
                Optional.of(s3Presigner)
        );

        liveSession = new LiveSession();
        liveSession.setId(10L);
        liveSession.setCode("ABCD1234");
    }

    @Nested
    @DisplayName("startRecording()")
    class StartRecording {

        @Test
        @DisplayName("should create recording and return upload URL")
        void shouldStartRecording() throws MalformedURLException {
            when(liveSessionRepository.findById(10L)).thenReturn(Optional.of(liveSession));
            when(s3Config.getBucket()).thenReturn("my-bucket");
            when(recordingRepository.save(any(SessionRecording.class))).thenAnswer(inv -> {
                SessionRecording r = inv.getArgument(0);
                r.setId(1L);
                return r;
            });

            PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
            when(presigned.url()).thenReturn(URI.create("https://s3.amazonaws.com/my-bucket/test.webm").toURL());
            when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

            RecordingResponse response = recordingService.startRecording(10L);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(RecordingStatus.RECORDING);
            assertThat(response.getUploadUrl()).contains("s3.amazonaws.com");
            verify(recordingRepository).save(any(SessionRecording.class));
        }

        @Test
        @DisplayName("should throw when session not found")
        void shouldThrowWhenSessionNotFound() {
            when(liveSessionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> recordingService.startRecording(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Live session not found");
        }

        @Test
        @DisplayName("should throw when S3 is not configured")
        void shouldThrowWhenS3NotConfigured() {
            RecordingService serviceWithoutS3 = new RecordingService(
                    recordingRepository, liveSessionRepository, s3Config, Optional.empty()
            );

            assertThatThrownBy(() -> serviceWithoutS3.startRecording(10L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("S3 storage is not configured");
        }
    }

    @Nested
    @DisplayName("completeRecording()")
    class CompleteRecording {

        @Test
        @DisplayName("should mark recording as completed with metadata")
        void shouldCompleteRecording() {
            SessionRecording recording = SessionRecording.builder()
                    .session(liveSession)
                    .s3Key("recordings/session-10/uuid.webm")
                    .s3Bucket("my-bucket")
                    .status(RecordingStatus.RECORDING)
                    .startedAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();
            recording.setId(1L);

            when(recordingRepository.findById(1L)).thenReturn(Optional.of(recording));
            when(recordingRepository.save(any(SessionRecording.class))).thenAnswer(inv -> inv.getArgument(0));

            RecordingResponse response = recordingService.completeRecording(1L, 5_000_000L, 120);

            assertThat(response.getStatus()).isEqualTo(RecordingStatus.COMPLETED);
            assertThat(response.getFileSizeBytes()).isEqualTo(5_000_000L);
            assertThat(response.getDurationSeconds()).isEqualTo(120);
        }

        @Test
        @DisplayName("should throw when recording is not in RECORDING status")
        void shouldThrowWhenNotRecording() {
            SessionRecording recording = SessionRecording.builder()
                    .session(liveSession)
                    .s3Key("key")
                    .s3Bucket("bucket")
                    .status(RecordingStatus.COMPLETED)
                    .startedAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();
            recording.setId(1L);

            when(recordingRepository.findById(1L)).thenReturn(Optional.of(recording));

            assertThatThrownBy(() -> recordingService.completeRecording(1L, 1000L, 60))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Recording is not in RECORDING status");
        }
    }

    @Nested
    @DisplayName("listBySession()")
    class ListBySession {

        @Test
        @DisplayName("should return recordings for a session")
        void shouldListRecordings() {
            SessionRecording recording = SessionRecording.builder()
                    .session(liveSession)
                    .s3Key("recordings/session-10/uuid.webm")
                    .s3Bucket("my-bucket")
                    .status(RecordingStatus.COMPLETED)
                    .startedAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();
            recording.setId(1L);

            when(liveSessionRepository.existsById(10L)).thenReturn(true);
            when(recordingRepository.findBySessionIdOrderByStartedAtDesc(10L))
                    .thenReturn(List.of(recording));

            List<RecordingResponse> result = recordingService.listBySession(10L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSessionId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("should throw when session does not exist")
        void shouldThrowWhenSessionNotFound() {
            when(liveSessionRepository.existsById(999L)).thenReturn(false);

            assertThatThrownBy(() -> recordingService.listBySession(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Live session not found");
        }
    }
}
