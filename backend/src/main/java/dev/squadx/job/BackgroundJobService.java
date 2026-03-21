package dev.squadx.job;

import dev.squadx.repository.AuditLogRepository;
import dev.squadx.repository.LiveSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Background job service for scheduled maintenance tasks.
 * Uses JobRunr for persistent, retryable job execution.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackgroundJobService {

    private final LiveSessionRepository liveSessionRepository;
    private final AuditLogRepository auditLogRepository;

    /**
     * Cleanup expired live sessions older than 30 days.
     * Runs daily at 3:00 AM.
     */
    @Recurring(id = "cleanup-expired-sessions", cron = "0 3 * * *")
    @Job(name = "Cleanup expired live sessions", retries = 2)
    @Transactional
    public void cleanupExpiredSessions() {
        Instant threshold = Instant.now().minus(30, ChronoUnit.DAYS);
        int deleted = liveSessionRepository.deleteByEndedAtBefore(threshold);
        log.info("Cleaned up {} expired live sessions older than 30 days", deleted);
    }

    /**
     * Archive old audit logs older than 90 days.
     * Runs weekly on Sunday at 4:00 AM.
     */
    @Recurring(id = "cleanup-old-audit-logs", cron = "0 4 * * 0")
    @Job(name = "Cleanup old audit logs", retries = 2)
    @Transactional
    public void cleanupOldAuditLogs() {
        Instant threshold = Instant.now().minus(90, ChronoUnit.DAYS);
        int deleted = auditLogRepository.deleteByCreatedAtBefore(threshold);
        log.info("Cleaned up {} audit logs older than 90 days", deleted);
    }
}
