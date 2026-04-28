package dev.squadx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.squadx.dto.meeting.CreateMeetingRequest;
import dev.squadx.dto.meeting.MeetingResponse;
import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.User;
import dev.squadx.model.enums.MeetingStatus;
import dev.squadx.model.enums.UserRole;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import dev.squadx.service.MeetingService;
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
        value = MeetingController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MeetingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MeetingService meetingService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserDetailsService userDetailsService;

    private User testUser;
    private MeetingResponse sampleMeeting;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .password("encoded")
                .fullName("Test User")
                .role(UserRole.USER)
                .build();
        testUser.setId(1L);

        sampleMeeting = MeetingResponse.builder()
                .id(1L)
                .title("Sprint Planning")
                .description("Weekly sprint planning meeting")
                .scheduledAt(Instant.now().plusSeconds(3600))
                .durationMinutes(60)
                .status(MeetingStatus.SCHEDULED)
                .organizationId(10L)
                .createdById(1L)
                .createdByName("Test User")
                .createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/meetings")
    class CreateEndpoint {

        @Test
        @DisplayName("should schedule meeting and return 201")
        void shouldCreateMeetingSuccessfully() throws Exception {
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setOrganizationId(10L);
            request.setTitle("Sprint Planning");
            request.setScheduledAt(Instant.now().plusSeconds(3600));
            request.setDurationMinutes(60);

            when(meetingService.create(any(CreateMeetingRequest.class), nullable(User.class)))
                    .thenReturn(sampleMeeting);

            mockMvc.perform(post("/api/v1/meetings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.title").value("Sprint Planning"))
                    .andExpect(jsonPath("$.message").value("Meeting scheduled"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/meetings/{id}")
    class GetByIdEndpoint {

        @Test
        @DisplayName("should return meeting by id with 200")
        void shouldReturnMeetingById() throws Exception {
            when(meetingService.getById(1L)).thenReturn(sampleMeeting);

            mockMvc.perform(get("/api/v1/meetings/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.title").value("Sprint Planning"));
        }

        @Test
        @DisplayName("should return 404 when meeting not found")
        void shouldReturn404WhenNotFound() throws Exception {
            when(meetingService.getById(999L))
                    .thenThrow(new ResourceNotFoundException("Meeting not found"));

            mockMvc.perform(get("/api/v1/meetings/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Meeting not found"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/meetings/organization/{orgId}")
    class GetByOrganizationEndpoint {

        @Test
        @DisplayName("should return meetings by organization with 200")
        void shouldReturnMeetingsByOrganization() throws Exception {
            Page<MeetingResponse> page = new PageImpl<>(List.of(sampleMeeting));

            when(meetingService.getByOrganization(eq(10L), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/meetings/organization/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].id").value(1));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/meetings/upcoming")
    class GetUpcomingEndpoint {

        @Test
        @DisplayName("should return upcoming meetings for current user")
        void shouldReturnUpcomingMeetings() throws Exception {
            when(meetingService.getUpcomingForUser(1L))
                    .thenReturn(List.of(sampleMeeting));

            mockMvc.perform(get("/api/v1/meetings/upcoming")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].title").value("Sprint Planning"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/meetings/{id}/rsvp")
    class RsvpEndpoint {

        @Test
        @DisplayName("should update RSVP and return 200")
        void shouldUpdateRsvpSuccessfully() throws Exception {
            when(meetingService.updateRsvp(eq(1L), nullable(User.class), any()))
                    .thenReturn(sampleMeeting);

            mockMvc.perform(post("/api/v1/meetings/1/rsvp")
                            .param("status", "ACCEPTED")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("RSVP updated"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/meetings/{id}/cancel")
    class CancelEndpoint {

        @Test
        @DisplayName("should cancel meeting and return 200")
        void shouldCancelMeetingSuccessfully() throws Exception {
            MeetingResponse cancelled = MeetingResponse.builder()
                    .id(1L)
                    .status(MeetingStatus.CANCELLED)
                    .build();

            when(meetingService.cancel(1L)).thenReturn(cancelled);

            mockMvc.perform(post("/api/v1/meetings/1/cancel"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Meeting cancelled"));
        }
    }
}
