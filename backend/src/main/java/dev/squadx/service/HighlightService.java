package dev.squadx.service;

import dev.squadx.dto.highlight.HighlightResponse;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.ExecutionLog;
import dev.squadx.model.SessionHighlight;
import dev.squadx.model.SessionRecording;
import dev.squadx.model.enums.HighlightType;
import dev.squadx.repository.ExecutionLogRepository;
import dev.squadx.repository.SessionHighlightRepository;
import dev.squadx.repository.SessionRecordingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class HighlightService {

    private final SessionHighlightRepository highlightRepository;
    private final SessionRecordingRepository recordingRepository;
    private final ExecutionLogRepository executionLogRepository;
    private final Optional<AiAnalysisService> aiAnalysisService;

    public HighlightService(
            SessionHighlightRepository highlightRepository,
            SessionRecordingRepository recordingRepository,
            ExecutionLogRepository executionLogRepository,
            Optional<AiAnalysisService> aiAnalysisService
    ) {
        this.highlightRepository = highlightRepository;
        this.recordingRepository = recordingRepository;
        this.executionLogRepository = executionLogRepository;
        this.aiAnalysisService = aiAnalysisService != null ? aiAnalysisService : Optional.empty();
        if (this.aiAnalysisService.isPresent()) {
            log.info("HighlightService initialized with AI analysis support");
        } else {
            log.info("HighlightService initialized with regex-only analysis (AI disabled)");
        }
    }

    // Pattern definitions for log analysis
    private static final Pattern ERROR_PATTERN =
            Pattern.compile("(?i)(error|exception|fatal|panic|stacktrace|caused by)");
    private static final Pattern TEST_PASSED_PATTERN =
            Pattern.compile("(?i)(test(s)?\\s+(passed|succeeded|ok|green)|all tests passed|BUILD SUCCESS)");
    private static final Pattern TEST_FAILED_PATTERN =
            Pattern.compile("(?i)(test(s)?\\s+(failed|failure|broken|red)|TESTS? FAILED|BUILD FAILURE)");
    private static final Pattern COMMIT_PATTERN =
            Pattern.compile("(?i)(git commit|committed|\\[master\\]|\\[main\\]|commit [a-f0-9]{7,40})");
    private static final Pattern DEPLOY_PATTERN =
            Pattern.compile("(?i)(deploy(ed|ing|ment)?|released|pushed to (prod|production|staging))");
    private static final Pattern BUG_PATTERN =
            Pattern.compile("(?i)(bug\\s*(fix|found|detected|report)|defect|regression)");
    private static final Pattern FEATURE_PATTERN =
            Pattern.compile("(?i)(feature\\s*(complete|done|finished|implemented)|implementation complete)");

    /**
     * Analyze execution logs for a recording's session and generate highlights.
     */
    @Transactional
    public List<HighlightResponse> generateHighlights(Long recordingId) {
        return generateHighlights(recordingId, null);
    }

    /**
     * Analyze execution logs and optionally client-provided logs to generate highlights.
     */
    @Transactional
    public List<HighlightResponse> generateHighlights(Long recordingId, List<String> clientLogs) {
        SessionRecording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new ResourceNotFoundException("Recording not found with id: " + recordingId));

        Instant recordingStart = recording.getStartedAt();
        List<SessionHighlight> highlights = new ArrayList<>();

        // 1. Analyze execution logs from the session's execution (if linked)
        if (recording.getSession() != null && recording.getSession().getExecution() != null) {
            Long executionId = recording.getSession().getExecution().getId();
            List<ExecutionLog> executionLogs = executionLogRepository.findByExecutionIdOrderByCreatedAtAsc(executionId);

            for (ExecutionLog execLog : executionLogs) {
                int timestampSeconds = calculateTimestampSeconds(recordingStart, execLog.getCreatedAt());
                if (timestampSeconds < 0) {
                    timestampSeconds = 0;
                }

                analyzeLogMessage(execLog.getMessage(), execLog.getLevel(), timestampSeconds, recording, highlights);
            }
        }

        // 2. Analyze client-provided logs if any
        if (clientLogs != null && !clientLogs.isEmpty()) {
            int intervalSeconds = recording.getDurationSeconds() != null
                    ? recording.getDurationSeconds() / Math.max(clientLogs.size(), 1)
                    : 10;

            for (int i = 0; i < clientLogs.size(); i++) {
                int timestampSeconds = i * intervalSeconds;
                analyzeLogMessage(clientLogs.get(i), "INFO", timestampSeconds, recording, highlights);
            }
        }

        // Save all highlights
        List<SessionHighlight> saved = highlightRepository.saveAll(highlights);

        log.info("Generated {} highlights for recording: {}", saved.size(), recordingId);

        return saved.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    /**
     * List all highlights for a recording, ordered by timestamp.
     */
    public List<HighlightResponse> getByRecording(Long recordingId) {
        if (!recordingRepository.existsById(recordingId)) {
            throw new ResourceNotFoundException("Recording not found with id: " + recordingId);
        }

        return highlightRepository.findByRecordingIdOrderByTimestampSecondsAsc(recordingId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Generate a text summary of the session based on its highlights.
     */
    public String getSummary(Long recordingId) {
        SessionRecording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new ResourceNotFoundException("Recording not found with id: " + recordingId));

        List<SessionHighlight> highlights =
                highlightRepository.findByRecordingIdOrderByTimestampSecondsAsc(recordingId);

        if (highlights.isEmpty()) {
            return "No highlights detected for this recording session.";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("Session Summary for recording #").append(recordingId);

        if (recording.getDurationSeconds() != null) {
            int minutes = recording.getDurationSeconds() / 60;
            int seconds = recording.getDurationSeconds() % 60;
            summary.append(String.format(" (Duration: %dm %ds)", minutes, seconds));
        }
        summary.append("\n\n");

        // Count by type
        long errors = highlights.stream().filter(h -> h.getHighlightType() == HighlightType.ERROR_DETECTED).count();
        long testsPassed = highlights.stream().filter(h -> h.getHighlightType() == HighlightType.TEST_PASSED).count();
        long testsFailed = highlights.stream().filter(h -> h.getHighlightType() == HighlightType.TEST_FAILED).count();
        long commits = highlights.stream().filter(h -> h.getHighlightType() == HighlightType.COMMIT_MADE).count();
        long deploys = highlights.stream().filter(h -> h.getHighlightType() == HighlightType.DEPLOY).count();
        long bugs = highlights.stream().filter(h -> h.getHighlightType() == HighlightType.BUG_FOUND).count();
        long features = highlights.stream().filter(h -> h.getHighlightType() == HighlightType.FEATURE_COMPLETED).count();

        summary.append("Overview:\n");
        summary.append(String.format("- Total highlights: %d\n", highlights.size()));
        if (errors > 0) summary.append(String.format("- Errors detected: %d\n", errors));
        if (testsPassed > 0) summary.append(String.format("- Tests passed: %d\n", testsPassed));
        if (testsFailed > 0) summary.append(String.format("- Tests failed: %d\n", testsFailed));
        if (commits > 0) summary.append(String.format("- Commits made: %d\n", commits));
        if (deploys > 0) summary.append(String.format("- Deployments: %d\n", deploys));
        if (bugs > 0) summary.append(String.format("- Bugs found: %d\n", bugs));
        if (features > 0) summary.append(String.format("- Features completed: %d\n", features));

        summary.append("\nTimeline:\n");
        for (SessionHighlight h : highlights) {
            int min = h.getTimestampSeconds() / 60;
            int sec = h.getTimestampSeconds() % 60;
            summary.append(String.format("  [%02d:%02d] %s - %s\n",
                    min, sec, h.getHighlightType().name(), h.getTitle()));
        }

        return summary.toString();
    }

    // ---- Internal helpers ----

    private void analyzeLogMessage(String message, String level, int timestampSeconds,
                                   SessionRecording recording, List<SessionHighlight> highlights) {
        if (message == null) return;

        if (BUG_PATTERN.matcher(message).find()) {
            highlights.add(buildHighlight(recording, HighlightType.BUG_FOUND, timestampSeconds,
                    "Bug detected", truncate(message, 200), 0.8f));
        }

        if (FEATURE_PATTERN.matcher(message).find()) {
            highlights.add(buildHighlight(recording, HighlightType.FEATURE_COMPLETED, timestampSeconds,
                    "Feature completed", truncate(message, 200), 0.8f));
        }

        if (TEST_PASSED_PATTERN.matcher(message).find()) {
            highlights.add(buildHighlight(recording, HighlightType.TEST_PASSED, timestampSeconds,
                    "Tests passed", truncate(message, 200), 0.9f));
        }

        if (TEST_FAILED_PATTERN.matcher(message).find()) {
            highlights.add(buildHighlight(recording, HighlightType.TEST_FAILED, timestampSeconds,
                    "Tests failed", truncate(message, 200), 0.9f));
        }

        if (COMMIT_PATTERN.matcher(message).find()) {
            highlights.add(buildHighlight(recording, HighlightType.COMMIT_MADE, timestampSeconds,
                    "Commit made", truncate(message, 200), 0.85f));
        }

        if (DEPLOY_PATTERN.matcher(message).find()) {
            highlights.add(buildHighlight(recording, HighlightType.DEPLOY, timestampSeconds,
                    "Deployment detected", truncate(message, 200), 0.85f));
        }

        // Check for errors last (broadest pattern) - only if level indicates error or pattern matches
        if ("ERROR".equalsIgnoreCase(level) || "FATAL".equalsIgnoreCase(level)
                || ERROR_PATTERN.matcher(message).find()) {
            // Avoid duplicate if already matched a more specific type
            boolean alreadyMatched = highlights.stream()
                    .anyMatch(h -> h.getTimestampSeconds().equals(timestampSeconds)
                            && h.getHighlightType() != HighlightType.ERROR_DETECTED);
            if (!alreadyMatched || "ERROR".equalsIgnoreCase(level) || "FATAL".equalsIgnoreCase(level)) {
                highlights.add(buildHighlight(recording, HighlightType.ERROR_DETECTED, timestampSeconds,
                        "Error detected", truncate(message, 200), 0.75f));
            }
        }
    }

    private SessionHighlight buildHighlight(SessionRecording recording, HighlightType type,
                                            int timestampSeconds, String title, String description,
                                            float confidence) {
        return SessionHighlight.builder()
                .recording(recording)
                .highlightType(type)
                .timestampSeconds(timestampSeconds)
                .title(title)
                .description(description)
                .confidence(confidence)
                .build();
    }

    private int calculateTimestampSeconds(Instant recordingStart, Instant logTime) {
        if (recordingStart == null || logTime == null) return 0;
        return (int) Duration.between(recordingStart, logTime).getSeconds();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    private HighlightResponse mapToResponse(SessionHighlight highlight) {
        return HighlightResponse.builder()
                .id(highlight.getId())
                .recordingId(highlight.getRecording().getId())
                .highlightType(highlight.getHighlightType())
                .timestampSeconds(highlight.getTimestampSeconds())
                .title(highlight.getTitle())
                .description(highlight.getDescription())
                .confidence(highlight.getConfidence())
                .metadata(highlight.getMetadata())
                .createdAt(highlight.getCreatedAt())
                .build();
    }
}
