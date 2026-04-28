package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.controller.support.AuthenticatedUserResolver;
import dev.squadx.model.CalendarIntegration;
import dev.squadx.model.User;
import dev.squadx.service.GoogleCalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/calendar-sync")
@RequiredArgsConstructor
@Tag(name = "Calendar Sync", description = "Google Calendar integration and sync")
public class CalendarSyncController {

    private final GoogleCalendarService googleCalendarService;

    @GetMapping("/auth-url")
    @Operation(summary = "Get Google OAuth2 authorization URL")
    public ResponseEntity<ApiResponse<Map<String, String>>> getAuthUrl(
            @RequestParam Long organizationId,
            @AuthenticationPrincipal User user,
            HttpServletRequest request
    ) {
        User currentUser = AuthenticatedUserResolver.resolve(user, request);
        String url = googleCalendarService.getAuthorizationUrl(currentUser.getId(), organizationId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("url", url)));
    }

    @GetMapping("/callback")
    @Operation(summary = "Handle Google OAuth2 callback")
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleCallback(
            @RequestParam String code,
            @RequestParam String state
    ) {
        // State format: "userId:orgId"
        String[] parts = state.split(":");
        Long userId = Long.parseLong(parts[0]);
        Long orgId = Long.parseLong(parts[1]);

        CalendarIntegration integration = googleCalendarService.handleOAuthCallback(code, userId, orgId);

        return ResponseEntity.ok(ApiResponse.success(
                Map.of(
                        "integration_id", integration.getId(),
                        "provider", integration.getProvider(),
                        "enabled", integration.getEnabled()
                ),
                "Google Calendar connected successfully"
        ));
    }

    @PostMapping("/sync")
    @Operation(summary = "Trigger full calendar sync (push and pull)")
    public ResponseEntity<ApiResponse<String>> sync(
            @AuthenticationPrincipal User user,
            HttpServletRequest request
    ) {
        User currentUser = AuthenticatedUserResolver.resolve(user, request);
        googleCalendarService.syncMeetingsToGoogle(currentUser.getId());
        googleCalendarService.syncMeetingsFromGoogle(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Sync initiated", "Calendar sync started in background"));
    }

    @DeleteMapping("/disconnect")
    @Operation(summary = "Disconnect Google Calendar integration")
    public ResponseEntity<ApiResponse<Void>> disconnect(
            @RequestParam Long organizationId,
            @AuthenticationPrincipal User user,
            HttpServletRequest request
    ) {
        User currentUser = AuthenticatedUserResolver.resolve(user, request);
        googleCalendarService.disconnect(currentUser.getId(), organizationId);
        return ResponseEntity.ok(ApiResponse.success(null, "Google Calendar disconnected"));
    }

    @GetMapping("/status")
    @Operation(summary = "Check Google Calendar integration status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus(
            @RequestParam Long organizationId,
            @AuthenticationPrincipal User user,
            HttpServletRequest request
    ) {
        User currentUser = AuthenticatedUserResolver.resolve(user, request);
        CalendarIntegration integration = googleCalendarService.getIntegrationStatus(currentUser.getId(), organizationId);

        if (integration == null) {
            return ResponseEntity.ok(ApiResponse.success(
                    Map.of(
                            "connected", false,
                            "configured", googleCalendarService.isConfigured()
                    )
            ));
        }

        return ResponseEntity.ok(ApiResponse.success(
                Map.of(
                        "connected", true,
                        "configured", googleCalendarService.isConfigured(),
                        "enabled", integration.getEnabled(),
                        "provider", integration.getProvider(),
                        "calendar_id", integration.getCalendarId() != null ? integration.getCalendarId() : ""
                )
        ));
    }
}
