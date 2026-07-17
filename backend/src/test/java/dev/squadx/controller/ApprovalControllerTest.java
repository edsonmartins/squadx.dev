package dev.squadx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.squadx.dto.approval.ApprovalResponse;
import dev.squadx.dto.approval.CreateApprovalRequest;
import dev.squadx.dto.approval.ReviewApprovalRequest;
import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.User;
import dev.squadx.model.enums.ApprovalStatus;
import dev.squadx.model.enums.ApprovalType;
import dev.squadx.model.enums.UserRole;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import dev.squadx.service.ApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = ApprovalController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ApprovalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ApprovalService approvalService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserDetailsService userDetailsService;

    private User testUser;
    private ApprovalResponse sampleApproval;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .password("encoded")
                .fullName("Test User")
                .role(UserRole.USER)
                .build();
        testUser.setId(1L);

        sampleApproval = ApprovalResponse.builder()
                .id(1L)
                .taskId(10L)
                .taskTitle("Test Task")
                .requestedById(1L)
                .requestedByName("Test User")
                .status(ApprovalStatus.PENDING)
                .approvalType(ApprovalType.COMMIT)
                .title("Approve commit")
                .description("Please review")
                .createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/approvals")
    class CreateEndpoint {

        @Test
        @DisplayName("should create approval request and return 201")
        void shouldCreateApprovalSuccessfully() throws Exception {
            CreateApprovalRequest request = new CreateApprovalRequest();
            request.setTaskId(10L);
            request.setTitle("Approve commit");
            request.setApprovalType(ApprovalType.COMMIT);

            when(approvalService.create(any(CreateApprovalRequest.class), nullable(User.class)))
                    .thenReturn(sampleApproval);

            mockMvc.perform(post("/api/v1/approvals")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.message").value("Approval request created"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/approvals/{id}/review")
    class ReviewEndpoint {

        @Test
        @DisplayName("should review approval and return 200")
        void shouldReviewApprovalSuccessfully() throws Exception {
            ReviewApprovalRequest request = new ReviewApprovalRequest();
            request.setApproved(true);
            request.setReviewComment("Looks good");

            ApprovalResponse reviewed = ApprovalResponse.builder()
                    .id(1L)
                    .status(ApprovalStatus.APPROVED)
                    .reviewerId(1L)
                    .reviewerName("Test User")
                    .reviewedAt(Instant.now())
                    .build();

            when(approvalService.review(eq(1L), any(ReviewApprovalRequest.class), nullable(User.class)))
                    .thenReturn(reviewed);

            mockMvc.perform(post("/api/v1/approvals/1/review")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Approval reviewed"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/approvals/{id}/cancel")
    class CancelEndpoint {

        @Test
        @DisplayName("should cancel approval and return 200")
        void shouldCancelApprovalSuccessfully() throws Exception {
            ApprovalResponse cancelled = ApprovalResponse.builder()
                    .id(1L)
                    .status(ApprovalStatus.CANCELLED)
                    .build();

            when(approvalService.cancel(eq(1L), nullable(User.class)))
                    .thenReturn(cancelled);

            mockMvc.perform(post("/api/v1/approvals/1/cancel")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Approval cancelled"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/approvals/{id}")
    class GetByIdEndpoint {

        @Test
        @DisplayName("should return approval by id with 200")
        void shouldReturnApprovalById() throws Exception {
            when(approvalService.getById(eq(1L), nullable(User.class))).thenReturn(sampleApproval);

            mockMvc.perform(get("/api/v1/approvals/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.title").value("Approve commit"));
        }

        @Test
        @DisplayName("should return 404 when approval not found")
        void shouldReturn404WhenNotFound() throws Exception {
            when(approvalService.getById(eq(999L), nullable(User.class)))
                    .thenThrow(new ResourceNotFoundException("Approval not found"));

            mockMvc.perform(get("/api/v1/approvals/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Approval not found"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/approvals/pending")
    class GetPendingEndpoint {

        @Test
        @DisplayName("should return pending approvals for current user")
        void shouldReturnPendingApprovals() throws Exception {
            Page<ApprovalResponse> page = new PageImpl<>(List.of(sampleApproval));

            when(approvalService.getPendingForUser(eq(1L), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/approvals/pending")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].id").value(1));
        }
    }
}
