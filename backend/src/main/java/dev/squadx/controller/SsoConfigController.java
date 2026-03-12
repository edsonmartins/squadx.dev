package dev.squadx.controller;

import dev.squadx.dto.auth.SsoConfigRequest;
import dev.squadx.dto.auth.SsoConfigResponse;
import dev.squadx.dto.common.ApiResponse;
import dev.squadx.model.User;
import dev.squadx.service.SsoConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/sso")
@RequiredArgsConstructor
@Tag(name = "SSO Configuration", description = "Organization-level SSO/OIDC configuration management")
public class SsoConfigController {

    private final SsoConfigService ssoConfigService;

    @PostMapping
    @Operation(summary = "Create or update an SSO configuration for a provider")
    @PreAuthorize("@permissionChecker.check(#user.id, #orgId, 'SETTINGS', 'CREATE')")
    public ResponseEntity<ApiResponse<SsoConfigResponse>> createOrUpdate(
            @PathVariable Long orgId,
            @Valid @RequestBody SsoConfigRequest request,
            @AuthenticationPrincipal User user
    ) {
        SsoConfigResponse response = ssoConfigService.createOrUpdate(orgId, request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response, "SSO configuration saved successfully"));
    }

    @GetMapping
    @Operation(summary = "List all SSO configurations for an organization")
    @PreAuthorize("@permissionChecker.check(#user.id, #orgId, 'SETTINGS', 'READ')")
    public ResponseEntity<ApiResponse<List<SsoConfigResponse>>> getAll(
            @PathVariable Long orgId,
            @AuthenticationPrincipal User user
    ) {
        List<SsoConfigResponse> configs = ssoConfigService.getByOrganization(orgId);
        return ResponseEntity.ok(ApiResponse.success(configs));
    }

    @GetMapping("/{provider}")
    @Operation(summary = "Get SSO configuration for a specific provider")
    @PreAuthorize("@permissionChecker.check(#user.id, #orgId, 'SETTINGS', 'READ')")
    public ResponseEntity<ApiResponse<SsoConfigResponse>> getByProvider(
            @PathVariable Long orgId,
            @PathVariable String provider,
            @AuthenticationPrincipal User user
    ) {
        SsoConfigResponse config = ssoConfigService.getByOrganizationAndProvider(orgId, provider);
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @DeleteMapping("/{provider}")
    @Operation(summary = "Delete SSO configuration for a specific provider")
    @PreAuthorize("@permissionChecker.check(#user.id, #orgId, 'SETTINGS', 'DELETE')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long orgId,
            @PathVariable String provider,
            @AuthenticationPrincipal User user
    ) {
        ssoConfigService.delete(orgId, provider);
        return ResponseEntity.ok(ApiResponse.success(null, "SSO configuration deleted successfully"));
    }

    @PatchMapping("/{provider}/toggle")
    @Operation(summary = "Enable or disable SSO for a specific provider")
    @PreAuthorize("@permissionChecker.check(#user.id, #orgId, 'SETTINGS', 'UPDATE')")
    public ResponseEntity<ApiResponse<SsoConfigResponse>> toggle(
            @PathVariable Long orgId,
            @PathVariable String provider,
            @RequestBody Map<String, Boolean> body,
            @AuthenticationPrincipal User user
    ) {
        boolean enabled = body.getOrDefault("enabled", false);
        SsoConfigResponse response = ssoConfigService.toggleEnabled(orgId, provider, enabled);
        return ResponseEntity.ok(ApiResponse.success(response, "SSO configuration updated successfully"));
    }
}
