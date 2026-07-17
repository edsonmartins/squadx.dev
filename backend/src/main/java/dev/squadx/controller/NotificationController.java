package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.dto.notification.NotificationConfigRequest;
import dev.squadx.dto.notification.NotificationConfigResponse;
import dev.squadx.controller.support.AuthenticatedUserResolver;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.model.User;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Slack, Discord, and Teams notification management")
public class NotificationController {

    private final NotificationService notificationService;
    private final OrganizationMemberRepository memberRepository;

    @GetMapping
    @Operation(summary = "List notification configurations for an organization")
    public ResponseEntity<ApiResponse<List<NotificationConfigResponse>>> list(
            @PathVariable Long orgId,
            @AuthenticationPrincipal User user,
            HttpServletRequest request
    ) {
        User currentUser = AuthenticatedUserResolver.resolve(user, request);
        validateOrgAccess(orgId, currentUser);
        List<NotificationConfigResponse> configs = notificationService.listByOrganization(orgId);
        return ResponseEntity.ok(ApiResponse.success(configs));
    }

    @PostMapping
    @Operation(summary = "Create a notification configuration")
    public ResponseEntity<ApiResponse<NotificationConfigResponse>> create(
            @PathVariable Long orgId,
            @Valid @RequestBody NotificationConfigRequest request,
            @AuthenticationPrincipal User user,
            HttpServletRequest httpRequest
    ) {
        User currentUser = AuthenticatedUserResolver.resolve(user, httpRequest);
        validateOrgAccess(orgId, currentUser);
        NotificationConfigResponse config = notificationService.create(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(config, "Notification configuration created successfully"));
    }

    @PutMapping("/{configId}")
    @Operation(summary = "Update a notification configuration")
    public ResponseEntity<ApiResponse<NotificationConfigResponse>> update(
            @PathVariable Long orgId,
            @PathVariable Long configId,
            @Valid @RequestBody NotificationConfigRequest request,
            @AuthenticationPrincipal User user,
            HttpServletRequest httpRequest
    ) {
        User currentUser = AuthenticatedUserResolver.resolve(user, httpRequest);
        validateOrgAccess(orgId, currentUser);
        NotificationConfigResponse config = notificationService.update(configId, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success(config, "Notification configuration updated successfully"));
    }

    @DeleteMapping("/{configId}")
    @Operation(summary = "Delete a notification configuration")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long orgId,
            @PathVariable Long configId,
            @AuthenticationPrincipal User user,
            HttpServletRequest request
    ) {
        User currentUser = AuthenticatedUserResolver.resolve(user, request);
        validateOrgAccess(orgId, currentUser);
        notificationService.delete(configId, currentUser);
        return ResponseEntity.ok(ApiResponse.success(null, "Notification configuration deleted successfully"));
    }

    @PostMapping("/test")
    @Operation(summary = "Send a test notification to all enabled channels")
    public ResponseEntity<ApiResponse<Void>> sendTest(
            @PathVariable Long orgId,
            @AuthenticationPrincipal User user,
            HttpServletRequest request
    ) {
        User currentUser = AuthenticatedUserResolver.resolve(user, request);
        validateOrgAccess(orgId, currentUser);
        notificationService.notifyTaskCompleted(orgId, "Test Task", "Test Project");
        return ResponseEntity.ok(ApiResponse.success(null, "Test notification sent"));
    }

    private void validateOrgAccess(Long orgId, User user) {
        if (!memberRepository.existsByOrganizationIdAndUserId(orgId, user.getId())) {
            throw new ForbiddenException("User does not have access to this organization");
        }
    }
}
