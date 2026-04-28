package dev.squadx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.squadx.dto.agent.AgentRequest;
import dev.squadx.dto.agent.AgentResponse;
import dev.squadx.dto.common.PageResponse;
import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.User;
import dev.squadx.model.enums.AgentType;
import dev.squadx.model.enums.UserRole;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import dev.squadx.service.AgentService;
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
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = AgentController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AgentService agentService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserDetailsService userDetailsService;

    private User testUser;
    private AgentResponse sampleAgent;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .password("encoded")
                .fullName("Test User")
                .role(UserRole.USER)
                .build();
        testUser.setId(1L);

        sampleAgent = AgentResponse.builder()
                .id(1L)
                .name("Test Agent")
                .agentType(AgentType.BACKEND)
                .description("A test agent")
                .modelId("gpt-4")
                .isActive(true)
                .squadId(10L)
                .squadName("Test Squad")
                .capabilities(Set.of("code_review"))
                .executionsCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/agents")
    class CreateEndpoint {

        @Test
        @DisplayName("should create agent successfully and return 201")
        void shouldCreateAgentSuccessfully() throws Exception {
            AgentRequest request = AgentRequest.builder()
                    .name("Test Agent")
                    .agentType(AgentType.BACKEND)
                    .squadId(10L)
                    .build();

            when(agentService.create(any(AgentRequest.class), nullable(User.class)))
                    .thenReturn(sampleAgent);

            mockMvc.perform(post("/api/v1/agents")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("Test Agent"))
                    .andExpect(jsonPath("$.message").value("Agent created successfully"));
        }

        @Test
        @DisplayName("should return 400 when validation fails")
        void shouldReturn400WhenValidationFails() throws Exception {
            AgentRequest request = AgentRequest.builder()
                    .name("")
                    .build();

            mockMvc.perform(post("/api/v1/agents")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/agents/{id}")
    class GetByIdEndpoint {

        @Test
        @DisplayName("should return agent by id with 200")
        void shouldReturnAgentById() throws Exception {
            when(agentService.getById(eq(1L), nullable(User.class)))
                    .thenReturn(sampleAgent);

            mockMvc.perform(get("/api/v1/agents/1")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("Test Agent"));
        }

        @Test
        @DisplayName("should return 404 when agent not found")
        void shouldReturn404WhenNotFound() throws Exception {
            when(agentService.getById(eq(999L), nullable(User.class)))
                    .thenThrow(new ResourceNotFoundException("Agent not found"));

            mockMvc.perform(get("/api/v1/agents/999")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Agent not found"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/agents/squad/{squadId}")
    class GetBySquadIdEndpoint {

        @Test
        @DisplayName("should return agents by squad id with 200")
        void shouldReturnAgentsBySquadId() throws Exception {
            PageResponse<AgentResponse> pageResponse = PageResponse.<AgentResponse>builder()
                    .content(List.of(sampleAgent))
                    .pageNumber(0)
                    .pageSize(20)
                    .totalElements(1)
                    .totalPages(1)
                    .isFirst(true)
                    .isLast(true)
                    .build();

            when(agentService.getBySquadId(eq(10L), any(), nullable(User.class)))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/agents/squad/10")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].id").value(1))
                    .andExpect(jsonPath("$.data.total_elements").value(1));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/agents/{id}")
    class DeleteEndpoint {

        @Test
        @DisplayName("should delete agent successfully and return 200")
        void shouldDeleteAgentSuccessfully() throws Exception {
            doNothing().when(agentService).delete(eq(1L), nullable(User.class));

            mockMvc.perform(delete("/api/v1/agents/1")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Agent deleted successfully"));
        }

        @Test
        @DisplayName("should return 404 when deleting non-existent agent")
        void shouldReturn404WhenDeletingNonExistent() throws Exception {
            doThrow(new ResourceNotFoundException("Agent not found"))
                    .when(agentService).delete(eq(999L), nullable(User.class));

            mockMvc.perform(delete("/api/v1/agents/999")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Agent not found"));
        }
    }
}
