package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.dto.meeting.CreateMeetingRequest;
import dev.squadx.dto.meeting.MeetingResponse;
import dev.squadx.model.User;
import dev.squadx.model.enums.RsvpStatus;
import dev.squadx.service.MeetingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
@Tag(name = "Meetings", description = "Calendar and meeting management")
public class MeetingController {

    private final MeetingService meetingService;

    @PostMapping
    @Operation(summary = "Schedule a new meeting")
    public ResponseEntity<ApiResponse<MeetingResponse>> create(
            @Valid @RequestBody CreateMeetingRequest request,
            @AuthenticationPrincipal User user) {
        MeetingResponse response = meetingService.create(request, user);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Meeting scheduled"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get meeting by ID")
    public ResponseEntity<ApiResponse<MeetingResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(meetingService.getById(id)));
    }

    @GetMapping("/organization/{orgId}")
    @Operation(summary = "Get meetings by organization")
    public ResponseEntity<ApiResponse<Page<MeetingResponse>>> getByOrganization(
            @PathVariable Long orgId,
            @PageableDefault Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(meetingService.getByOrganization(orgId, pageable)));
    }

    @GetMapping("/upcoming")
    @Operation(summary = "Get upcoming meetings for current user")
    public ResponseEntity<ApiResponse<List<MeetingResponse>>> getUpcoming(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(meetingService.getUpcomingForUser(user.getId())));
    }

    @GetMapping("/organization/{orgId}/range")
    @Operation(summary = "Get meetings in date range")
    public ResponseEntity<ApiResponse<List<MeetingResponse>>> getByDateRange(
            @PathVariable Long orgId,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return ResponseEntity.ok(ApiResponse.success(meetingService.getByDateRange(orgId, from, to)));
    }

    @PostMapping("/{id}/rsvp")
    @Operation(summary = "RSVP to a meeting")
    public ResponseEntity<ApiResponse<MeetingResponse>> rsvp(
            @PathVariable Long id,
            @RequestParam RsvpStatus status,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(
                meetingService.updateRsvp(id, user, status), "RSVP updated"));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a meeting")
    public ResponseEntity<ApiResponse<MeetingResponse>> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(meetingService.cancel(id), "Meeting cancelled"));
    }
}
