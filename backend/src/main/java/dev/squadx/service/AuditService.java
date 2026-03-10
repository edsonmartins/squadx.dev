package dev.squadx.service;

import dev.squadx.model.AuditLog;
import dev.squadx.model.User;
import dev.squadx.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(User user, String action, String resourceType, Long resourceId,
                    String details, String ipAddress) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .user(user)
                    .userEmail(user != null ? user.getEmail() : null)
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .details(details)
                    .ipAddress(ipAddress)
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log: action={}, resourceType={}, resourceId={}",
                    action, resourceType, resourceId, e);
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> findWithFilters(Long userId, String action, String resourceType,
                                          Instant from, Instant to, Pageable pageable) {
        return auditLogRepository.findWithFilters(userId, action, resourceType, from, to, pageable);
    }
}
