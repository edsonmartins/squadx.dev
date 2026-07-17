package dev.squadx.service;

import dev.squadx.config.S3Config;
import dev.squadx.dto.recording.RecordingResponse;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.LiveSession;
import dev.squadx.model.SessionRecording;
import dev.squadx.model.User;
import dev.squadx.model.enums.RecordingStatus;
import dev.squadx.repository.LiveSessionRepository;
import dev.squadx.repository.SessionRecordingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingService {

    private final SessionRecordingRepository recordingRepository;
    private final LiveSessionRepository liveSessionRepository;
    private final S3Config s3Config;
    private final Optional<S3Presigner> s3Presigner;
    private final OrganizationAccessGuard accessGuard;

    private static final Duration UPLOAD_URL_EXPIRY = Duration.ofHours(2);
    private static final Duration DOWNLOAD_URL_EXPIRY = Duration.ofHours(1);

    /**
     * Start recording a session. Creates a database record and returns a pre-signed upload URL.
     */
    @Transactional
    public RecordingResponse startRecording(Long sessionId, User currentUser) {
        ensureS3Available();

        LiveSession session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Live session not found with id: " + sessionId));
        accessGuard.requireMember(sessionOrgId(session), currentUser.getId());

        String s3Key = generateS3Key(sessionId);

        SessionRecording recording = SessionRecording.builder()
                .session(session)
                .s3Key(s3Key)
                .s3Bucket(s3Config.getBucket())
                .status(RecordingStatus.RECORDING)
                .startedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        recording = recordingRepository.save(recording);

        String uploadUrl = generateUploadUrl(s3Key);

        log.info("Started recording for session: {}, s3Key: {}", sessionId, s3Key);

        return mapToResponse(recording, uploadUrl, null);
    }

    /**
     * Mark a recording as complete with file size and duration metadata.
     */
    @Transactional
    public RecordingResponse completeRecording(Long recordingId, Long fileSizeBytes, Integer durationSeconds, User currentUser) {
        SessionRecording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new ResourceNotFoundException("Recording not found with id: " + recordingId));
        accessGuard.requireMember(sessionOrgId(recording.getSession()), currentUser.getId());

        recording = completeRecordingEntity(recording, fileSizeBytes, durationSeconds);

        log.info("Completed recording: {}, size: {} bytes, duration: {}s",
                recordingId, fileSizeBytes, durationSeconds);

        return mapToResponse(recording, null, null);
    }

    /**
     * Complete the latest active recording for a session from an external webhook.
     */
    @Transactional
    public RecordingResponse completeLatestRecordingForSession(Long sessionId, Long fileSizeBytes, Integer durationSeconds) {
        SessionRecording recording = recordingRepository
                .findFirstBySessionIdAndStatusOrderByStartedAtDesc(sessionId, RecordingStatus.RECORDING)
                .orElseThrow(() -> new ResourceNotFoundException("No active recording found for session id: " + sessionId));

        recording = completeRecordingEntity(recording, fileSizeBytes, durationSeconds);

        log.info("Completed latest recording for session: {}, recordingId: {}, size: {} bytes, duration: {}s",
                sessionId, recording.getId(), fileSizeBytes, durationSeconds);

        return mapToResponse(recording, null, null);
    }

    private SessionRecording completeRecordingEntity(SessionRecording recording, Long fileSizeBytes, Integer durationSeconds) {
        if (recording.getStatus() != RecordingStatus.RECORDING) {
            throw new BadRequestException("Recording is not in RECORDING status");
        }

        recording.setStatus(RecordingStatus.COMPLETED);
        recording.setFileSizeBytes(fileSizeBytes);
        recording.setDurationSeconds(durationSeconds);
        recording.setCompletedAt(Instant.now());

        return recordingRepository.save(recording);
    }

    /**
     * Get a pre-signed download URL for a recording (1 hour expiry).
     */
    public RecordingResponse getRecordingUrl(Long recordingId, User currentUser) {
        ensureS3Available();

        SessionRecording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new ResourceNotFoundException("Recording not found with id: " + recordingId));
        accessGuard.requireMember(sessionOrgId(recording.getSession()), currentUser.getId());

        if (recording.getStatus() != RecordingStatus.COMPLETED) {
            throw new BadRequestException("Recording is not yet completed");
        }

        String playbackUrl = generateDownloadUrl(recording.getS3Key(), recording.getS3Bucket());

        return mapToResponse(recording, null, playbackUrl);
    }

    /**
     * List all recordings for a given session.
     */
    public List<RecordingResponse> listBySession(Long sessionId, User currentUser) {
        // Verify session exists
        LiveSession session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Live session not found with id: " + sessionId));
        accessGuard.requireMember(sessionOrgId(session), currentUser.getId());

        return recordingRepository.findBySessionIdOrderByStartedAtDesc(sessionId)
                .stream()
                .map(recording -> mapToResponse(recording, null, null))
                .toList();
    }

    private Long sessionOrgId(LiveSession session) {
        if (session.getTask() != null) return session.getTask().getProject().getOrganization().getId();
        if (session.getAgent() != null) return session.getAgent().getSquad().getOrganization().getId();
        throw new dev.squadx.exception.BadRequestException("Session is not linked to a task or agent");
    }

    private void ensureS3Available() {
        if (s3Presigner.isEmpty()) {
            throw new BadRequestException(
                    "S3 storage is not configured. Set AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY environment variables.");
        }
    }

    private String generateS3Key(Long sessionId) {
        return String.format("recordings/session-%d/%s.webm", sessionId, UUID.randomUUID());
    }

    private String generateUploadUrl(String s3Key) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(s3Config.getBucket())
                .key(s3Key)
                .contentType("video/webm")
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(UPLOAD_URL_EXPIRY)
                .putObjectRequest(putRequest)
                .build();

        return s3Presigner.orElseThrow()
                .presignPutObject(presignRequest)
                .url()
                .toString();
    }

    private String generateDownloadUrl(String s3Key, String bucket) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(DOWNLOAD_URL_EXPIRY)
                .getObjectRequest(getRequest)
                .build();

        return s3Presigner.orElseThrow()
                .presignGetObject(presignRequest)
                .url()
                .toString();
    }

    private RecordingResponse mapToResponse(SessionRecording recording, String uploadUrl, String playbackUrl) {
        return RecordingResponse.builder()
                .id(recording.getId())
                .sessionId(recording.getSession().getId())
                .s3Key(recording.getS3Key())
                .s3Bucket(recording.getS3Bucket())
                .uploadUrl(uploadUrl)
                .playbackUrl(playbackUrl)
                .durationSeconds(recording.getDurationSeconds())
                .fileSizeBytes(recording.getFileSizeBytes())
                .status(recording.getStatus())
                .startedAt(recording.getStartedAt())
                .completedAt(recording.getCompletedAt())
                .createdAt(recording.getCreatedAt())
                .build();
    }
}
