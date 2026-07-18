package dev.squadx.service;

import dev.squadx.dto.meeting.MeetingResponse;
import dev.squadx.dto.meeting.CreateMeetingRequest;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Meeting;
import dev.squadx.model.MeetingAttendee;
import dev.squadx.model.Organization;
import dev.squadx.model.User;
import dev.squadx.model.enums.MeetingStatus;
import dev.squadx.model.enums.RsvpStatus;
import dev.squadx.repository.MeetingRepository;
import dev.squadx.repository.OrganizationRepository;
import dev.squadx.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationAccessGuard accessGuard;

    @InjectMocks
    private MeetingService meetingService;

    private Organization testOrg;
    private User testUser;
    private Meeting testMeeting;
    private MeetingAttendee testAttendee;

    @BeforeEach
    void setUp() {
        testOrg = Organization.builder()
                .name("Test Org")
                .slug("test-org")
                .build();
        testOrg.setId(1L);

        testUser = User.builder()
                .email("john@squadx.dev")
                .password("encoded")
                .fullName("John Doe")
                .build();
        testUser.setId(1L);

        testMeeting = Meeting.builder()
                .organization(testOrg)
                .title("Daily Standup")
                .description("Team sync")
                .scheduledAt(Instant.parse("2026-04-01T10:00:00Z"))
                .durationMinutes(30)
                .meetingUrl("https://meet.squadx.dev/abc")
                .createdBy(testUser)
                .attendees(new HashSet<>())
                .build();
        testMeeting.setId(1L);
        testMeeting.setCreatedAt(Instant.now());

        testAttendee = MeetingAttendee.builder()
                .meeting(testMeeting)
                .user(testUser)
                .rsvpStatus(RsvpStatus.PENDING)
                .build();
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("should create meeting without attendees")
        void shouldCreateMeetingWithoutAttendees() {
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setOrganizationId(1L);
            request.setTitle("Daily Standup");
            request.setDescription("Team sync");
            request.setScheduledAt(Instant.parse("2026-04-01T10:00:00Z"));
            request.setDurationMinutes(30);
            request.setMeetingUrl("https://meet.squadx.dev/abc");
            request.setAttendeeIds(null);

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(meetingRepository.save(any(Meeting.class))).thenReturn(testMeeting);

            MeetingResponse result = meetingService.create(request, testUser);

            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("Daily Standup");
            assertThat(result.getOrganizationId()).isEqualTo(1L);
            assertThat(result.getCreatedById()).isEqualTo(1L);
            assertThat(result.getCreatedByName()).isEqualTo("John Doe");
            verify(organizationRepository).findById(1L);
            verify(meetingRepository).save(any(Meeting.class));
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("should create meeting with attendees")
        void shouldCreateMeetingWithAttendees() {
            User attendeeUser = User.builder()
                    .email("jane@squadx.dev")
                    .password("encoded")
                    .fullName("Jane Smith")
                    .build();
            attendeeUser.setId(2L);

            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setOrganizationId(1L);
            request.setTitle("Daily Standup");
            request.setDescription("Team sync");
            request.setScheduledAt(Instant.parse("2026-04-01T10:00:00Z"));
            request.setDurationMinutes(30);
            request.setAttendeeIds(List.of(2L));

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(meetingRepository.save(any(Meeting.class))).thenReturn(testMeeting);
            when(userRepository.findById(2L)).thenReturn(Optional.of(attendeeUser));

            MeetingResponse result = meetingService.create(request, testUser);

            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("Daily Standup");
            verify(organizationRepository).findById(1L);
            verify(userRepository).findById(2L);
            verify(meetingRepository, times(2)).save(any(Meeting.class));
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when organization not found")
        void shouldThrowWhenOrgNotFound() {
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setOrganizationId(99L);
            request.setTitle("Meeting");
            request.setScheduledAt(Instant.now());

            when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> meetingService.create(request, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Organization not found");

            verify(organizationRepository).findById(99L);
            verifyNoInteractions(meetingRepository);
        }
    }

    @Nested
    @DisplayName("updateRsvp()")
    class UpdateRsvp {

        @Test
        @DisplayName("should update RSVP status for attendee")
        void shouldUpdateRsvpStatus() {
            testMeeting.getAttendees().add(testAttendee);

            when(meetingRepository.findById(1L)).thenReturn(Optional.of(testMeeting));
            when(meetingRepository.save(any(Meeting.class))).thenReturn(testMeeting);

            MeetingResponse result = meetingService.updateRsvp(1L, testUser, RsvpStatus.ACCEPTED);

            assertThat(result).isNotNull();
            assertThat(testAttendee.getRsvpStatus()).isEqualTo(RsvpStatus.ACCEPTED);
            verify(meetingRepository).findById(1L);
            verify(meetingRepository).save(testMeeting);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when meeting not found")
        void shouldThrowWhenMeetingNotFound() {
            when(meetingRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> meetingService.updateRsvp(99L, testUser, RsvpStatus.ACCEPTED))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Meeting not found");

            verify(meetingRepository).findById(99L);
            verify(meetingRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("cancel()")
    class Cancel {

        @Test
        @DisplayName("should cancel meeting and set status to CANCELLED")
        void shouldCancelMeeting() {
            when(meetingRepository.findById(1L)).thenReturn(Optional.of(testMeeting));
            when(meetingRepository.save(any(Meeting.class))).thenReturn(testMeeting);

            MeetingResponse result = meetingService.cancel(1L, testUser);

            assertThat(result).isNotNull();
            assertThat(testMeeting.getStatus()).isEqualTo(MeetingStatus.CANCELLED);
            verify(meetingRepository).findById(1L);
            verify(meetingRepository).save(testMeeting);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when meeting not found")
        void shouldThrowWhenMeetingNotFound() {
            when(meetingRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> meetingService.cancel(99L, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Meeting not found");

            verify(meetingRepository).findById(99L);
            verify(meetingRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @DisplayName("should return meeting response when found")
        void shouldReturnMeeting() {
            when(meetingRepository.findById(1L)).thenReturn(Optional.of(testMeeting));

            MeetingResponse result = meetingService.getById(1L, testUser);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getTitle()).isEqualTo("Daily Standup");
            assertThat(result.getDescription()).isEqualTo("Team sync");
            assertThat(result.getDurationMinutes()).isEqualTo(30);
            assertThat(result.getMeetingUrl()).isEqualTo("https://meet.squadx.dev/abc");
            assertThat(result.getStatus()).isEqualTo(MeetingStatus.SCHEDULED);
            assertThat(result.getOrganizationId()).isEqualTo(1L);
            assertThat(result.getCreatedById()).isEqualTo(1L);
            assertThat(result.getCreatedByName()).isEqualTo("John Doe");
            assertThat(result.getAttendees()).isEmpty();
            verify(meetingRepository).findById(1L);
        }

        @Test
        @DisplayName("getById denies a non-member of the meeting's organization")
        void getById_deniesNonMember() {
            when(meetingRepository.findById(1L)).thenReturn(Optional.of(testMeeting));
            doThrow(new ForbiddenException("nope"))
                    .when(accessGuard).requireMember(anyLong(), anyLong());

            assertThatThrownBy(() -> meetingService.getById(1L, testUser))
                    .isInstanceOf(ForbiddenException.class);
        }
    }
}
