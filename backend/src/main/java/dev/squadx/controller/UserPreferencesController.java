package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.dto.preferences.UserPreferencesRequest;
import dev.squadx.dto.preferences.UserPreferencesResponse;
import dev.squadx.model.User;
import dev.squadx.service.UserPreferencesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/preferences")
@RequiredArgsConstructor
@Tag(name = "User Preferences", description = "Per-user notification and live-view preferences")
public class UserPreferencesController {

    private final UserPreferencesService preferencesService;

    @GetMapping
    @Operation(summary = "Get the current user's preferences")
    public ResponseEntity<ApiResponse<UserPreferencesResponse>> get(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.success(preferencesService.get(user)));
    }

    @PutMapping
    @Operation(summary = "Update the current user's preferences")
    public ResponseEntity<ApiResponse<UserPreferencesResponse>> update(
            @Valid @RequestBody UserPreferencesRequest request,
            @AuthenticationPrincipal User user
    ) {
        UserPreferencesResponse response = preferencesService.update(request, user);
        return ResponseEntity.ok(ApiResponse.success(response, "Preferences updated"));
    }
}
