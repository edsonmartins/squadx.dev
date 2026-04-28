package dev.squadx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.squadx.dto.common.PageResponse;
import dev.squadx.dto.squad.SquadRequest;
import dev.squadx.dto.squad.SquadResponse;
import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.User;
import dev.squadx.model.enums.UserRole;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import dev.squadx.service.SquadService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = SquadController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SquadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SquadService squadService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserDetailsService userDetailsService;

    private User testUser;
    private SquadResponse sampleSquad;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .password("encoded")
                .fullName("Test User")
                .role(UserRole.USER)
                .build();
        testUser.setId(1L);

        sampleSquad = SquadResponse.builder()
                .id(1L)
                .name("Alpha Squad")
                .description("The alpha team")
                .isActive(true)
                .organizationId(10L)
                .organizationName("Test Org")
                .agentsCount(3)
                .projectsCount(2)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/squads")
    class CreateEndpoint {

        @Test
        @DisplayName("should create squad successfully and return 201")
        void shouldCreateSquadSuccessfully() throws Exception {
            SquadRequest request = SquadRequest.builder()
                    .name("Alpha Squad")
                    .description("The alpha team")
                    .organizationId(10L)
                    .build();

            when(squadService.create(any(SquadRequest.class), nullable(User.class)))
                    .thenReturn(sampleSquad);

            mockMvc.perform(post("/api/v1/squads")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("Alpha Squad"))
                    .andExpect(jsonPath("$.message").value("Squad created successfully"));
        }

        @Test
        @DisplayName("should return 400 when validation fails")
        void shouldReturn400WhenValidationFails() throws Exception {
            SquadRequest request = SquadRequest.builder()
                    .name("")
                    .build();

            mockMvc.perform(post("/api/v1/squads")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/squads/{id}")
    class GetByIdEndpoint {

        @Test
        @DisplayName("should return squad by id with 200")
        void shouldReturnSquadById() throws Exception {
            when(squadService.getById(eq(1L), nullable(User.class)))
                    .thenReturn(sampleSquad);

            mockMvc.perform(get("/api/v1/squads/1")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("Alpha Squad"));
        }

        @Test
        @DisplayName("should return 404 when squad not found")
        void shouldReturn404WhenNotFound() throws Exception {
            when(squadService.getById(eq(999L), nullable(User.class)))
                    .thenThrow(new ResourceNotFoundException("Squad not found"));

            mockMvc.perform(get("/api/v1/squads/999")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Squad not found"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/squads/organization/{organizationId}")
    class GetByOrganizationEndpoint {

        @Test
        @DisplayName("should return squads by organization with 200")
        void shouldReturnSquadsByOrganization() throws Exception {
            PageResponse<SquadResponse> pageResponse = PageResponse.<SquadResponse>builder()
                    .content(List.of(sampleSquad))
                    .pageNumber(0)
                    .pageSize(20)
                    .totalElements(1)
                    .totalPages(1)
                    .isFirst(true)
                    .isLast(true)
                    .build();

            when(squadService.getByOrganizationId(eq(10L), any(), nullable(User.class)))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/squads/organization/10")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].id").value(1))
                    .andExpect(jsonPath("$.data.total_elements").value(1));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/squads/{id}")
    class DeleteEndpoint {

        @Test
        @DisplayName("should delete squad successfully and return 200")
        void shouldDeleteSquadSuccessfully() throws Exception {
            doNothing().when(squadService).delete(eq(1L), nullable(User.class));

            mockMvc.perform(delete("/api/v1/squads/1")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Squad deleted successfully"));
        }

        @Test
        @DisplayName("should return 404 when deleting non-existent squad")
        void shouldReturn404WhenDeletingNonExistent() throws Exception {
            doThrow(new ResourceNotFoundException("Squad not found"))
                    .when(squadService).delete(eq(999L), nullable(User.class));

            mockMvc.perform(delete("/api/v1/squads/999")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Squad not found"));
        }
    }
}
