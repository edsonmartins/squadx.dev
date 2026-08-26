package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.dto.task.ExternalTaskRequest;
import dev.squadx.dto.task.ExternalTaskResponse;
import dev.squadx.service.ExternalTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/v1/internal/tasks")
@RequiredArgsConstructor
public class InternalTaskController {

    private final ExternalTaskService externalTaskService;

    @Value("${squadx.service-secret:}")
    private String serviceSecret;

    @PostMapping("/pullwise")
    public ResponseEntity<ApiResponse<ExternalTaskResponse>> upsertPullwiseTask(
            @RequestHeader(value = "X-SquadX-Service-Secret", required = false) String suppliedSecret,
            @Valid @RequestBody ExternalTaskRequest request) {
        if (!validSecret(suppliedSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ExternalTaskResponse response = externalTaskService.upsertPullwiseTask(request);
        return ResponseEntity.status(response.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    private boolean validSecret(String suppliedSecret) {
        if (serviceSecret == null || serviceSecret.isBlank() || suppliedSecret == null) return false;
        return MessageDigest.isEqual(serviceSecret.getBytes(StandardCharsets.UTF_8),
                suppliedSecret.getBytes(StandardCharsets.UTF_8));
    }
}
