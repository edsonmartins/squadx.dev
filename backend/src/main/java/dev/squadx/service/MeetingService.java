package dev.squadx.service;

import dev.squadx.dto.meeting.AttendeeResponse;
import dev.squadx.dto.meeting.CreateMeetingRequest;
import dev.squadx.dto.meeting.MeetingResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OrganizationAccessGuard accessGuard;

    @Transactional
    public MeetingResponse create(CreateMeetingRequest request, User createdBy) {
        accessGuard.requireMember(request.getOrganizationId(), createdBy.getId());
        Organization org = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        Meeting meeting = Meeting.builder()
                .organization(org)
                .title(request.getTitle())
                .description(request.getDescription())
                .scheduledAt(request.getScheduledAt())
                .durationMinutes(request.getDurationMinutes() != null ? request.getDurationMinutes() : 30)
                .meetingUrl(request.getMeetingUrl())
                .createdBy(createdBy)
                .build();

        meeting = meetingRepository.save(meeting);

        // Add attendees
        if (request.getAttendeeIds() != null) {
            for (Long userId : request.getAttendeeIds()) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
                MeetingAttendee attendee = MeetingAttendee.builder()
                        .meeting(meeting)
                        .user(user)
                        .build();
                meeting.getAttendees().add(attendee);
            }
            meeting = meetingRepository.save(meeting);
        }

        return mapToResponse(meeting);
    }

    @Transactional
    public MeetingResponse updateRsvp(Long meetingId, User user, RsvpStatus rsvpStatus) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found"));

        meeting.getAttendees().stream()
                .filter(a -> a.getUser().getId().equals(user.getId()))
                .findFirst()
                .ifPresent(a -> a.setRsvpStatus(rsvpStatus));

        meeting = meetingRepository.save(meeting);
        return mapToResponse(meeting);
    }

    @Transactional
    public MeetingResponse cancel(Long meetingId, User currentUser) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found"));
        accessGuard.requireMember(meeting.getOrganization().getId(), currentUser.getId());
        meeting.setStatus(MeetingStatus.CANCELLED);
        meeting = meetingRepository.save(meeting);
        return mapToResponse(meeting);
    }

    public MeetingResponse getById(Long id, User currentUser) {
        Meeting meeting = meetingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found"));
        accessGuard.requireMember(meeting.getOrganization().getId(), currentUser.getId());
        return mapToResponse(meeting);
    }

    public Page<MeetingResponse> getByOrganization(Long organizationId, Pageable pageable, User currentUser) {
        accessGuard.requireMember(organizationId, currentUser.getId());
        return meetingRepository.findByOrganizationId(organizationId, pageable)
                .map(this::mapToResponse);
    }

    public List<MeetingResponse> getUpcomingForUser(Long userId) {
        return meetingRepository.findUpcomingForUser(userId, Instant.now()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<MeetingResponse> getByDateRange(Long orgId, Instant from, Instant to, User currentUser) {
        accessGuard.requireMember(orgId, currentUser.getId());
        return meetingRepository.findByOrganizationAndDateRange(orgId, from, to).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private MeetingResponse mapToResponse(Meeting meeting) {
        List<AttendeeResponse> attendees = meeting.getAttendees().stream()
                .map(a -> AttendeeResponse.builder()
                        .userId(a.getUser().getId())
                        .userName(a.getUser().getFullName())
                        .userEmail(a.getUser().getEmail())
                        .rsvpStatus(a.getRsvpStatus())
                        .build())
                .collect(Collectors.toList());

        return MeetingResponse.builder()
                .id(meeting.getId())
                .title(meeting.getTitle())
                .description(meeting.getDescription())
                .scheduledAt(meeting.getScheduledAt())
                .durationMinutes(meeting.getDurationMinutes())
                .meetingUrl(meeting.getMeetingUrl())
                .status(meeting.getStatus())
                .organizationId(meeting.getOrganization().getId())
                .createdById(meeting.getCreatedBy().getId())
                .createdByName(meeting.getCreatedBy().getFullName())
                .attendees(attendees)
                .createdAt(meeting.getCreatedAt())
                .build();
    }
}
