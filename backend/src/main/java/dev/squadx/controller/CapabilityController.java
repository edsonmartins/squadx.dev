package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.service.CapabilityRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/capabilities")
@RequiredArgsConstructor
@Tag(name = "Capabilities", description = "Runtime capability registry endpoints")
public class CapabilityController {

    private final CapabilityRegistryService capabilityRegistryService;

    @GetMapping
    @Operation(summary = "List runtime capabilities and integration providers")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listCapabilities() {
        return ResponseEntity.ok(ApiResponse.success(capabilityRegistryService.getCapabilities()));
    }
}
