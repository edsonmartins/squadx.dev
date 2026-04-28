package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.service.MemoryPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/memory")
@RequiredArgsConstructor
@Tag(name = "Memory", description = "Agent memory policy and readiness endpoints")
public class MemoryPolicyController {

    private final MemoryPolicyService memoryPolicyService;

    @GetMapping("/policy")
    @Operation(summary = "Get active agent memory policy")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPolicy() {
        return ResponseEntity.ok(ApiResponse.success(memoryPolicyService.describePolicy()));
    }
}
