package dev.squadx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.squadx.dto.common.PageResponse;
import dev.squadx.dto.task.TaskRequest;
import dev.squadx.dto.task.TaskResponse;
import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.User;
import dev.squadx.model.enums.TaskPriority;
import dev.squadx.model.enums.TaskStatus;
import dev.squadx.model.enums.UserRole;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import dev.squadx.service.TaskService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = TaskController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaskService taskService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserDetailsService userDetailsService;

    private User testUser;
    private TaskResponse sampleTask;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .password("encoded")
                .fullName("Test User")
                .role(UserRole.USER)
                .build();
        testUser.setId(1L);

        sampleTask = TaskResponse.builder()
                .id(1L)
                .title("Test Task")
                .description("A test task description")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .projectId(10L)
                .projectName("Test Project")
                .createdById(1L)
                .createdByName("Test User")
                .orderIndex(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/tasks")
    class CreateEndpoint {

        @Test
        @DisplayName("should create task successfully and return 201")
        void shouldCreateTaskSuccessfully() throws Exception {
            TaskRequest request = TaskRequest.builder()
                    .title("Test Task")
                    .description("A test task description")
                    .projectId(10L)
                    .priority(TaskPriority.MEDIUM)
                    .build();

            when(taskService.create(any(TaskRequest.class), nullable(User.class)))
                    .thenReturn(sampleTask);

            mockMvc.perform(post("/api/v1/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.title").value("Test Task"))
                    .andExpect(jsonPath("$.message").value("Task created successfully"));
        }

        @Test
        @DisplayName("should return 400 when validation fails")
        void shouldReturn400WhenValidationFails() throws Exception {
            TaskRequest request = TaskRequest.builder()
                    .title("")
                    .projectId(null)
                    .build();

            mockMvc.perform(post("/api/v1/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/tasks/{id}")
    class GetByIdEndpoint {

        @Test
        @DisplayName("should return task by id with 200")
        void shouldReturnTaskById() throws Exception {
            when(taskService.getById(eq(1L), nullable(User.class)))
                    .thenReturn(sampleTask);

            mockMvc.perform(get("/api/v1/tasks/1")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.title").value("Test Task"));
        }

        @Test
        @DisplayName("should return 404 when task not found")
        void shouldReturn404WhenNotFound() throws Exception {
            when(taskService.getById(eq(999L), nullable(User.class)))
                    .thenThrow(new ResourceNotFoundException("Task not found"));

            mockMvc.perform(get("/api/v1/tasks/999")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Task not found"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/tasks/project/{projectId}")
    class GetByProjectIdEndpoint {

        @Test
        @DisplayName("should return tasks by project id with 200")
        void shouldReturnTasksByProjectId() throws Exception {
            PageResponse<TaskResponse> pageResponse = PageResponse.<TaskResponse>builder()
                    .content(List.of(sampleTask))
                    .pageNumber(0)
                    .pageSize(20)
                    .totalElements(1)
                    .totalPages(1)
                    .isFirst(true)
                    .isLast(true)
                    .build();

            when(taskService.getByProjectId(eq(10L), any(), nullable(User.class)))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/tasks/project/10")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].id").value(1))
                    .andExpect(jsonPath("$.data.total_elements").value(1));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/tasks/{id}")
    class UpdateEndpoint {

        @Test
        @DisplayName("should update task successfully and return 200")
        void shouldUpdateTaskSuccessfully() throws Exception {
            TaskRequest request = TaskRequest.builder()
                    .title("Updated Task")
                    .description("Updated description")
                    .projectId(10L)
                    .priority(TaskPriority.HIGH)
                    .build();

            TaskResponse updatedTask = TaskResponse.builder()
                    .id(1L)
                    .title("Updated Task")
                    .description("Updated description")
                    .priority(TaskPriority.HIGH)
                    .projectId(10L)
                    .build();

            when(taskService.update(eq(1L), any(TaskRequest.class), nullable(User.class)))
                    .thenReturn(updatedTask);

            mockMvc.perform(put("/api/v1/tasks/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.title").value("Updated Task"))
                    .andExpect(jsonPath("$.message").value("Task updated successfully"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/tasks/{id}/status")
    class UpdateStatusEndpoint {

        @Test
        @DisplayName("should update task status and return 200")
        void shouldUpdateStatusSuccessfully() throws Exception {
            TaskResponse updatedTask = TaskResponse.builder()
                    .id(1L)
                    .title("Test Task")
                    .status(TaskStatus.IN_PROGRESS)
                    .projectId(10L)
                    .build();

            when(taskService.updateStatus(eq(1L), eq(TaskStatus.IN_PROGRESS), nullable(User.class)))
                    .thenReturn(updatedTask);

            mockMvc.perform(patch("/api/v1/tasks/1/status")
                            .param("status", "IN_PROGRESS")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                    .andExpect(jsonPath("$.message").value("Task status updated"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/tasks/{id}")
    class DeleteEndpoint {

        @Test
        @DisplayName("should delete task successfully and return 200")
        void shouldDeleteTaskSuccessfully() throws Exception {
            doNothing().when(taskService).delete(eq(1L), nullable(User.class));

            mockMvc.perform(delete("/api/v1/tasks/1")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Task deleted successfully"));
        }
    }
}
