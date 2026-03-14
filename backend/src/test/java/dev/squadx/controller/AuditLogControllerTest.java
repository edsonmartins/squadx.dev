package dev.squadx.controller;

import dev.squadx.dto.audit.AuditLogResponse;
import dev.squadx.dto.common.PageResponse;
import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.model.AuditLog;
import dev.squadx.model.User;
import dev.squadx.model.enums.UserRole;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import dev.squadx.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuditLogController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditService auditService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserDetailsService userDetailsService;

    private User adminUser;
    private AuditLog sampleAuditLog;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .email("admin@example.com")
                .password("encoded")
                .fullName("Admin User")
                .role(UserRole.ADMIN)
                .build();
        adminUser.setId(1L);

        sampleAuditLog = AuditLog.builder()
                .id(1L)
                .userEmail("admin@example.com")
                .action("CREATE")
                .resourceType("Project")
                .resourceId(10L)
                .details("{\"name\":\"Test\"}")
                .ipAddress("127.0.0.1")
                .createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("GET /api/v1/audit-logs")
    class GetAuditLogsEndpoint {

        @Test
        @DisplayName("should return audit logs with 200")
        void shouldReturnAuditLogs() throws Exception {
            Page<AuditLog> page = new PageImpl<>(List.of(sampleAuditLog));

            when(auditService.findWithFilters(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/audit-logs")
                            .with(user(adminUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].action").value("CREATE"));
        }

        @Test
        @DisplayName("should return audit logs filtered by action")
        void shouldReturnAuditLogsFilteredByAction() throws Exception {
            Page<AuditLog> page = new PageImpl<>(List.of(sampleAuditLog));

            when(auditService.findWithFilters(isNull(), any(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/audit-logs")
                            .param("action", "CREATE")
                            .with(user(adminUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].action").value("CREATE"));
        }

        @Test
        @DisplayName("should return audit logs filtered by userId")
        void shouldReturnAuditLogsFilteredByUserId() throws Exception {
            Page<AuditLog> page = new PageImpl<>(List.of(sampleAuditLog));

            when(auditService.findWithFilters(any(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/audit-logs")
                            .param("userId", "1")
                            .with(user(adminUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("should return empty page when no audit logs match")
        void shouldReturnEmptyPageWhenNoMatch() throws Exception {
            Page<AuditLog> emptyPage = new PageImpl<>(List.of());

            when(auditService.findWithFilters(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(emptyPage);

            mockMvc.perform(get("/api/v1/audit-logs")
                            .with(user(adminUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isEmpty());
        }
    }
}
