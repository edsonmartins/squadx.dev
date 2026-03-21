package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.model.User;
import dev.squadx.service.EventBufferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Buffered event polling for offline WebSocket clients")
public class EventBufferController {

    private final EventBufferService eventBufferService;

    @GetMapping("/pending")
    @Operation(summary = "Drain all pending events for the current user")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> drainPending(
            @AuthenticationPrincipal User user
    ) {
        List<Map<String, Object>> events = eventBufferService.drainEvents(user.getId());
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    @GetMapping("/pending/count")
    @Operation(summary = "Get count of pending events for the current user")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> pendingCount(
            @AuthenticationPrincipal User user
    ) {
        int count = eventBufferService.pendingCount(user.getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
    }
}
