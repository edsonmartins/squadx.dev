package dev.squadx.service;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.AuthorizationCodeTokenRequest;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class GoogleCalendarService {

    private final CalendarIntegrationRepository calendarIntegrationRepository;
    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String APPLICATION_NAME = "SquadX.dev";
    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);

    public GoogleCalendarService(
            CalendarIntegrationRepository calendarIntegrationRepository,
            MeetingRepository meetingRepository,
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            @Value("${google.calendar.client-id:}") String clientId,
            @Value("${google.calendar.client-secret:}") String clientSecret,
            @Value("${google.calendar.redirect-uri:http://localhost:3000/api/calendar/callback}") String redirectUri) {
        this.calendarIntegrationRepository = calendarIntegrationRepository;
        this.meetingRepository = meetingRepository;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    /**
     * Check if Google Calendar API credentials are configured.
     */
    public boolean isConfigured() {
        return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
    }

    /**
     * Generate the Google OAuth2 consent URL for a user.
     */
    public String getAuthorizationUrl(Long userId, Long orgId) {
        ensureConfigured();

        try {
            GoogleAuthorizationCodeFlow flow = buildFlow();
            return flow.newAuthorizationUrl()
                    .setRedirectUri(redirectUri)
                    .setState(userId + ":" + orgId)
                    .build();
        } catch (Exception e) {
            log.error("Failed to generate Google authorization URL", e);
            throw new BadRequestException("Failed to generate authorization URL: " + e.getMessage());
        }
    }

    /**
     * Handle the OAuth2 callback: exchange authorization code for tokens and save the integration.
     */
    @Transactional
    public CalendarIntegration handleOAuthCallback(String code, Long userId, Long orgId) {
        ensureConfigured();

        try {
            GoogleAuthorizationCodeFlow flow = buildFlow();
            TokenResponse tokenResponse = flow.newTokenRequest(code)
                    .setRedirectUri(redirectUri)
                    .execute();

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            Organization organization = organizationRepository.findById(orgId)
                    .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

            // Check for existing integration and update, or create new
            CalendarIntegration integration = calendarIntegrationRepository
                    .findByUserIdAndOrganizationIdAndProvider(userId, orgId, "google")
                    .orElse(CalendarIntegration.builder()
                            .user(user)
                            .organization(organization)
                            .provider("google")
                            .build());

            integration.setAccessToken(tokenResponse.getAccessToken());
            integration.setRefreshToken(tokenResponse.getRefreshToken() != null
                    ? tokenResponse.getRefreshToken()
                    : integration.getRefreshToken());
            integration.setTokenExpiry(Instant.now().plusSeconds(
                    tokenResponse.getExpiresInSeconds() != null ? tokenResponse.getExpiresInSeconds() : 3600));
            integration.setCalendarId("primary");
            integration.setEnabled(true);

            integration = calendarIntegrationRepository.save(integration);

            log.info("Google Calendar integration saved for user: {}, org: {}", userId, orgId);
            return integration;

        } catch (Exception e) {
            log.error("Failed to handle Google OAuth callback for user: {}", userId, e);
            throw new BadRequestException("Failed to complete Google Calendar authorization: " + e.getMessage());
        }
    }

    /**
     * Push all local meetings to Google Calendar for a user.
     */
    @Async
    public void syncMeetingsToGoogle(Long userId) {
        ensureConfigured();

        List<CalendarIntegration> integrations = calendarIntegrationRepository.findByUserIdAndEnabled(userId, true);
        for (CalendarIntegration integration : integrations) {
            try {
                Calendar calendarService = buildCalendarService(integration);
                List<Meeting> meetings = meetingRepository.findUpcomingForUser(userId, Instant.now());

                for (Meeting meeting : meetings) {
                    pushMeetingToGoogle(calendarService, integration, meeting);
                }

                log.info("Synced {} meetings to Google Calendar for user: {}", meetings.size(), userId);
            } catch (Exception e) {
                log.error("Failed to sync meetings to Google for user: {}", userId, e);
            }
        }
    }

    /**
     * Pull Google Calendar events to local meetings for a user.
     */
    @Async
    public void syncMeetingsFromGoogle(Long userId) {
        ensureConfigured();

        List<CalendarIntegration> integrations = calendarIntegrationRepository.findByUserIdAndEnabled(userId, true);
        for (CalendarIntegration integration : integrations) {
            try {
                Calendar calendarService = buildCalendarService(integration);

                com.google.api.client.util.DateTime timeMin =
                        new com.google.api.client.util.DateTime(System.currentTimeMillis());
                com.google.api.client.util.DateTime timeMax =
                        new com.google.api.client.util.DateTime(
                                Instant.now().plus(30, ChronoUnit.DAYS).toEpochMilli());

                Events events = calendarService.events().list(integration.getCalendarId())
                        .setTimeMin(timeMin)
                        .setTimeMax(timeMax)
                        .setMaxResults(100)
                        .setSingleEvents(true)
                        .setOrderBy("startTime")
                        .execute();

                int imported = 0;
                for (Event event : events.getItems()) {
                    if (event.getStart() == null || event.getStart().getDateTime() == null) {
                        continue; // Skip all-day events
                    }

                    // Check if we already have this event
                    String externalId = event.getId();
                    boolean exists = meetingRepository.findByOrganizationIdAndStatus(
                                    integration.getOrganization().getId(),
                                    dev.squadx.model.enums.MeetingStatus.SCHEDULED)
                            .stream()
                            .anyMatch(m -> externalId.equals(m.getExternalEventId()));

                    if (!exists) {
                        Meeting meeting = Meeting.builder()
                                .organization(integration.getOrganization())
                                .title(event.getSummary() != null ? event.getSummary() : "Untitled Event")
                                .description(event.getDescription())
                                .scheduledAt(Instant.ofEpochMilli(event.getStart().getDateTime().getValue()))
                                .durationMinutes(calculateDuration(event))
                                .meetingUrl(event.getHangoutLink())
                                .createdBy(integration.getUser())
                                .externalEventId(externalId)
                                .build();
                        meetingRepository.save(meeting);
                        imported++;
                    }
                }

                log.info("Imported {} events from Google Calendar for user: {}", imported, userId);
            } catch (Exception e) {
                log.error("Failed to sync meetings from Google for user: {}", userId, e);
            }
        }
    }

    /**
     * Push a single meeting to Google Calendar.
     */
    @Async
    public void pushMeeting(Long meetingId) {
        ensureConfigured();

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found"));

        User createdBy = meeting.getCreatedBy();
        CalendarIntegration integration = calendarIntegrationRepository
                .findByUserIdAndOrganizationIdAndProvider(createdBy.getId(),
                        meeting.getOrganization().getId(), "google")
                .orElse(null);

        if (integration == null || !integration.getEnabled()) {
            log.debug("No active Google Calendar integration for user: {}", createdBy.getId());
            return;
        }

        try {
            Calendar calendarService = buildCalendarService(integration);
            pushMeetingToGoogle(calendarService, integration, meeting);
            log.info("Pushed meeting {} to Google Calendar", meetingId);
        } catch (Exception e) {
            log.error("Failed to push meeting {} to Google Calendar", meetingId, e);
        }
    }

    /**
     * Get the integration status for a user/org.
     */
    public CalendarIntegration getIntegrationStatus(Long userId, Long orgId) {
        return calendarIntegrationRepository
                .findByUserIdAndOrganizationIdAndProvider(userId, orgId, "google")
                .orElse(null);
    }

    /**
     * Disconnect Google Calendar integration.
     */
    @Transactional
    public void disconnect(Long userId, Long orgId) {
        calendarIntegrationRepository.deleteByUserIdAndOrganizationIdAndProvider(userId, orgId, "google");
        log.info("Disconnected Google Calendar for user: {}, org: {}", userId, orgId);
    }

    // ---- Internal helpers ----

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new BadRequestException(
                    "Google Calendar API is not configured. Set GOOGLE_CALENDAR_CLIENT_ID and GOOGLE_CALENDAR_CLIENT_SECRET environment variables.");
        }
    }

    private GoogleAuthorizationCodeFlow buildFlow() throws GeneralSecurityException, IOException {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        return new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientId, clientSecret, SCOPES)
                .setAccessType("offline")
                .build();
    }

    private Calendar buildCalendarService(CalendarIntegration integration)
            throws GeneralSecurityException, IOException {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        Credential credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
                .setTransport(httpTransport)
                .setJsonFactory(JSON_FACTORY)
                .setTokenServerUrl(new GenericUrl("https://oauth2.googleapis.com/token"))
                .setClientAuthentication(new com.google.api.client.http.BasicAuthentication(clientId, clientSecret))
                .build()
                .setAccessToken(integration.getAccessToken())
                .setRefreshToken(integration.getRefreshToken());

        return new Calendar.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private void pushMeetingToGoogle(Calendar calendarService, CalendarIntegration integration, Meeting meeting) {
        try {
            Event event = new Event()
                    .setSummary(meeting.getTitle())
                    .setDescription(meeting.getDescription());

            EventDateTime start = new EventDateTime()
                    .setDateTime(new com.google.api.client.util.DateTime(meeting.getScheduledAt().toEpochMilli()));
            event.setStart(start);

            Instant endTime = meeting.getScheduledAt().plus(meeting.getDurationMinutes(), ChronoUnit.MINUTES);
            EventDateTime end = new EventDateTime()
                    .setDateTime(new com.google.api.client.util.DateTime(endTime.toEpochMilli()));
            event.setEnd(end);

            if (meeting.getMeetingUrl() != null) {
                event.setLocation(meeting.getMeetingUrl());
            }

            if (meeting.getExternalEventId() != null) {
                // Update existing event
                calendarService.events()
                        .update(integration.getCalendarId(), meeting.getExternalEventId(), event)
                        .execute();
            } else {
                // Create new event
                Event created = calendarService.events()
                        .insert(integration.getCalendarId(), event)
                        .execute();
                meeting.setExternalEventId(created.getId());
                meetingRepository.save(meeting);
            }
        } catch (IOException e) {
            log.error("Failed to push meeting {} to Google Calendar", meeting.getId(), e);
        }
    }

    private int calculateDuration(Event event) {
        if (event.getEnd() != null && event.getEnd().getDateTime() != null
                && event.getStart() != null && event.getStart().getDateTime() != null) {
            long durationMs = event.getEnd().getDateTime().getValue() - event.getStart().getDateTime().getValue();
            return (int) (durationMs / 60000);
        }
        return 30; // Default duration
    }
}
