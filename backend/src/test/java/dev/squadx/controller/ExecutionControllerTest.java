package dev.squadx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.squadx.dto.common.PageResponse;
import dev.squadx.dto.execution.ExecutionRequest;
import dev.squadx.dto.execution.ExecutionResponse;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.model.User;
import dev.squadx.model.enums.ExecutionStatus;
import dev.squadx.model.enums.UserRole;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import dev.squadx.service.ExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = ExecutionController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ExecutionService executionService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserDetailsService userDetailsService;

    private User testUser;
    private ExecutionResponse sampleExecution;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .password("encoded")
                .fullName("Test User")
                .role(UserRole.USER)
                .build();
        testUser.setId(1L);

        sampleExecution = ExecutionResponse.builder()
                .id(1L)
                .taskId(10L)
                .taskTitle("Test Task")
                .agentId(5L)
                .agentName("Claude Agent")
                .status(ExecutionStatus.RUNNING)
                .startedAt(Instant.now())
                .createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/executions")
    class StartEndpoint {

        @Test
        @DisplayName("should start execution successfully and return 201")
        void shouldStartExecutionSuccessfully() throws Exception {
            ExecutionRequest request = ExecutionRequest.builder()
                    .taskId(10L)
                    .agentId(5L)
                    .build();

            when(executionService.startExecution(any(ExecutionRequest.class), nullable(User.class)))
                    .thenReturn(sampleExecution);

            mockMvc.perform(post("/api/v1/executions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.task_id").value(10))
                    .andExpect(jsonPath("$.message").value("Execution started"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/executions/{id}")
    class GetByIdEndpoint {

        @Test
        @DisplayName("should return execution by id with 200")
        void shouldReturnExecutionById() throws Exception {
            when(executionService.getById(eq(1L), nullable(User.class)))
                    .thenReturn(sampleExecution);

            mockMvc.perform(get("/api/v1/executions/1")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.status").value("RUNNING"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/executions/task/{taskId}")
    class GetByTaskIdEndpoint {

        @Test
        @DisplayName("should return executions by task id with 200")
        void shouldReturnExecutionsByTaskId() throws Exception {
            PageResponse<ExecutionResponse> pageResponse = PageResponse.<ExecutionResponse>builder()
                    .content(List.of(sampleExecution))
                    .pageNumber(0)
                    .pageSize(20)
                    .totalElements(1)
                    .totalPages(1)
                    .isFirst(true)
                    .isLast(true)
                    .build();

            when(executionService.getByTaskId(eq(10L), any(), nullable(User.class)))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/executions/task/10")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].id").value(1))
                    .andExpect(jsonPath("$.data.total_elements").value(1));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/executions/{id}/status")
    class UpdateStatusEndpoint {

        @Test
        @DisplayName("should update execution status and return 200")
        void shouldUpdateStatusSuccessfully() throws Exception {
            ExecutionResponse completedExecution = ExecutionResponse.builder()
                    .id(1L)
                    .taskId(10L)
                    .status(ExecutionStatus.COMPLETED)
                    .build();

            when(executionService.updateStatus(eq(1L), eq(ExecutionStatus.COMPLETED), nullable(User.class)))
                    .thenReturn(completedExecution);

            mockMvc.perform(patch("/api/v1/executions/1/status")
                            .param("status", "COMPLETED")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.message").value("Execution status updated"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/executions/{id}/cancel")
    class CancelEndpoint {

        @Test
        @DisplayName("should cancel execution successfully and return 200")
        void shouldCancelExecutionSuccessfully() throws Exception {
            ExecutionResponse cancelledExecution = ExecutionResponse.builder()
                    .id(1L)
                    .taskId(10L)
                    .status(ExecutionStatus.CANCELLED)
                    .build();

            when(executionService.cancel(eq(1L), nullable(User.class)))
                    .thenReturn(cancelledExecution);

            mockMvc.perform(post("/api/v1/executions/1/cancel")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.message").value("Execution cancelled"));
        }

        @Test
        @DisplayName("should return 400 when cancelling already finished execution")
        void shouldReturn400WhenCancellingFinishedExecution() throws Exception {
            when(executionService.cancel(eq(1L), nullable(User.class)))
                    .thenThrow(new BadRequestException("Cannot cancel a completed execution"));

            mockMvc.perform(post("/api/v1/executions/1/cancel")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Cannot cancel a completed execution"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/executions/organization/{organizationId}/metrics")
    class GetMetricsEndpoint {

        @Test
        @DisplayName("should return organization metrics with 200")
        void shouldReturnOrganizationMetrics() throws Exception {
            Map<String, Object> metrics = Map.of(
                    "total_executions", 100,
                    "successful_executions", 85,
                    "failed_executions", 15
            );

            when(executionService.getOrganizationMetrics(eq(10L), nullable(User.class)))
                    .thenReturn(metrics);

            mockMvc.perform(get("/api/v1/executions/organization/10/metrics")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.total_executions").value(100))
                    .andExpect(jsonPath("$.data.successful_executions").value(85));
        }
    }
}
