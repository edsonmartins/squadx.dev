package dev.squadx.service;

import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.CalendarIntegration;
import dev.squadx.model.Meeting;
import dev.squadx.model.Organization;
import dev.squadx.model.User;
import dev.squadx.repository.CalendarIntegrationRepository;
import dev.squadx.repository.MeetingRepository;
import dev.squadx.repository.OrganizationRepository;
import dev.squadx.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarServiceTest {

    @Mock
    private CalendarIntegrationRepository calendarIntegrationRepository;

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    private GoogleCalendarService serviceConfigured;
    private GoogleCalendarService serviceNotConfigured;

    private Organization testOrg;
    private User testUser;

    @BeforeEach
    void setUp() {
        testOrg = Organization.builder()
                .name("Test Org")
                .slug("test-org")
                .build();
        testOrg.setId(1L);

        testUser = User.builder()
                .email("test@example.com")
                .fullName("Test User")
                .build();
        testUser.setId(5L);

        serviceConfigured = new GoogleCalendarService(
                calendarIntegrationRepository,
                meetingRepository,
                userRepository,
                organizationRepository,
                "test-client-id",
                "test-client-secret",
                "http://localhost:3000/api/calendar/callback"
        );

        serviceNotConfigured = new GoogleCalendarService(
                calendarIntegrationRepository,
                meetingRepository,
                userRepository,
                organizationRepository,
                "",
                "",
                "http://localhost:3000/api/calendar/callback"
        );
    }

    @Nested
    @DisplayName("isConfigured()")
    class IsConfigured {

        @Test
        @DisplayName("should return true when client id and secret are set")
        void shouldReturnTrueWhenConfigured() {
            assertThat(serviceConfigured.isConfigured()).isTrue();
        }

        @Test
        @DisplayName("should return false when credentials are empty")
        void shouldReturnFalseWhenNotConfigured() {
            assertThat(serviceNotConfigured.isConfigured()).isFalse();
        }
    }

    @Nested
    @DisplayName("getAuthorizationUrl()")
    class GetAuthorizationUrl {

        @Test
        @DisplayName("should throw BadRequestException when not configured")
        void shouldThrowWhenNotConfigured() {
            assertThatThrownBy(() -> serviceNotConfigured.getAuthorizationUrl(5L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Google Calendar API is not configured");
        }
    }

    @Nested
    @DisplayName("handleOAuthCallback()")
    class HandleOAuthCallback {

        @Test
        @DisplayName("should throw BadRequestException when not configured")
        void shouldThrowWhenNotConfigured() {
            assertThatThrownBy(() -> serviceNotConfigured.handleOAuthCallback("code", 5L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Google Calendar API is not configured");
        }
    }

    @Nested
    @DisplayName("getIntegrationStatus()")
    class GetIntegrationStatus {

        @Test
        @DisplayName("should return integration when it exists")
        void shouldReturnIntegrationWhenExists() {
            CalendarIntegration integration = CalendarIntegration.builder()
                    .user(testUser)
                    .organization(testOrg)
                    .provider("google")
                    .accessToken("access-token")
                    .refreshToken("refresh-token")
                    .enabled(true)
                    .build();
            integration.setId(1L);

            when(calendarIntegrationRepository.findByUserIdAndOrganizationIdAndProvider(5L, 1L, "google"))
                    .thenReturn(Optional.of(integration));

            CalendarIntegration result = serviceConfigured.getIntegrationStatus(5L, 1L);

            assertThat(result).isNotNull();
            assertThat(result.getProvider()).isEqualTo("google");
            assertThat(result.getEnabled()).isTrue();
        }

        @Test
        @DisplayName("should return null when no integration exists")
        void shouldReturnNullWhenNotExists() {
            when(calendarIntegrationRepository.findByUserIdAndOrganizationIdAndProvider(5L, 1L, "google"))
                    .thenReturn(Optional.empty());

            CalendarIntegration result = serviceConfigured.getIntegrationStatus(5L, 1L);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("disconnect()")
    class Disconnect {

        @Test
        @DisplayName("should delete the calendar integration for user and org")
        void shouldDisconnect() {
            serviceConfigured.disconnect(5L, 1L);

            verify(calendarIntegrationRepository).deleteByUserIdAndOrganizationIdAndProvider(5L, 1L, "google");
        }
    }

    @Nested
    @DisplayName("syncMeetingsToGoogle()")
    class SyncMeetingsToGoogle {

        @Test
        @DisplayName("should throw BadRequestException when not configured")
        void shouldThrowWhenNotConfigured() {
            assertThatThrownBy(() -> serviceNotConfigured.syncMeetingsToGoogle(5L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Google Calendar API is not configured");
        }
    }

    @Nested
    @DisplayName("syncMeetingsFromGoogle()")
    class SyncMeetingsFromGoogle {

        @Test
        @DisplayName("should throw BadRequestException when not configured")
        void shouldThrowWhenNotConfigured() {
            assertThatThrownBy(() -> serviceNotConfigured.syncMeetingsFromGoogle(5L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Google Calendar API is not configured");
        }
    }
}
