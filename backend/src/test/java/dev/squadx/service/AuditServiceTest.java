package dev.squadx.service;

import dev.squadx.model.AuditLog;
import dev.squadx.model.User;
import dev.squadx.model.enums.UserRole;
import dev.squadx.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .password("encoded-password")
                .fullName("Test User")
                .role(UserRole.USER)
                .isActive(true)
                .build();
        testUser.setId(1L);
    }

    @Nested
    @DisplayName("log()")
    class Log {

        @Test
        @DisplayName("should save audit log with all fields populated")
        void shouldSaveAuditLog() {
            auditService.log(testUser, "CREATE", "Project", 10L, "{\"name\":\"My Project\"}", "192.168.1.1");

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getUser()).isEqualTo(testUser);
            assertThat(saved.getUserEmail()).isEqualTo("test@example.com");
            assertThat(saved.getAction()).isEqualTo("CREATE");
            assertThat(saved.getResourceType()).isEqualTo("Project");
            assertThat(saved.getResourceId()).isEqualTo(10L);
            assertThat(saved.getDetails()).isEqualTo("{\"name\":\"My Project\"}");
            assertThat(saved.getIpAddress()).isEqualTo("192.168.1.1");
        }

        @Test
        @DisplayName("should set userEmail to null when user is null")
        void shouldHandleNullUser() {
            auditService.log(null, "SYSTEM_EVENT", "Health", null, "check", null);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getUser()).isNull();
            assertThat(saved.getUserEmail()).isNull();
            assertThat(saved.getAction()).isEqualTo("SYSTEM_EVENT");
        }

        @Test
        @DisplayName("should not propagate exception when repository save fails")
        void shouldNotPropagateException() {
            when(auditLogRepository.save(any(AuditLog.class)))
                    .thenThrow(new RuntimeException("DB down"));

            // Should not throw -- the method catches exceptions internally
            auditService.log(testUser, "CREATE", "Project", 1L, null, null);

            verify(auditLogRepository).save(any(AuditLog.class));
        }
    }

    @Nested
    @DisplayName("findWithFilters()")
    class FindWithFilters {

        @Test
        @DisplayName("should delegate to repository with all filter parameters")
        void shouldDelegateToRepository() {
            Pageable pageable = PageRequest.of(0, 20);
            Instant from = Instant.parse("2025-01-01T00:00:00Z");
            Instant to = Instant.parse("2025-12-31T23:59:59Z");

            AuditLog log = AuditLog.builder()
                    .action("CREATE")
                    .resourceType("Project")
                    .build();
            Page<AuditLog> expectedPage = new PageImpl<>(List.of(log), pageable, 1);

            when(auditLogRepository.findWithFilters(1L, "CREATE", "Project", from, to, pageable))
                    .thenReturn(expectedPage);

            Page<AuditLog> result = auditService.findWithFilters(1L, "CREATE", "Project", from, to, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getAction()).isEqualTo("CREATE");
            verify(auditLogRepository).findWithFilters(1L, "CREATE", "Project", from, to, pageable);
        }

        @Test
        @DisplayName("should return empty page when no results match filters")
        void shouldReturnEmptyPage() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<AuditLog> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(auditLogRepository.findWithFilters(null, null, null, null, null, pageable))
                    .thenReturn(emptyPage);

            Page<AuditLog> result = auditService.findWithFilters(null, null, null, null, null, pageable);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("should pass null filters through to repository")
        void shouldPassNullFilters() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<AuditLog> page = new PageImpl<>(List.of(), pageable, 0);

            when(auditLogRepository.findWithFilters(null, null, null, null, null, pageable))
                    .thenReturn(page);

            auditService.findWithFilters(null, null, null, null, null, pageable);

            verify(auditLogRepository).findWithFilters(null, null, null, null, null, pageable);
        }
    }
}
