package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.dto.harness.HarnessRequest;
import dev.squadx.dto.harness.HarnessResponse;
import dev.squadx.model.User;
import dev.squadx.service.HarnessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/harnesses")
@RequiredArgsConstructor
public class HarnessController {
    private final HarnessService service;

    @GetMapping("/catalog")
    public ResponseEntity<ApiResponse<List<dev.squadx.dto.harness.HarnessCatalogItem>>> catalog() {
        return ResponseEntity.ok(ApiResponse.success(service.catalog()));
    }

    @GetMapping("/organization/{organizationId}")
    public ResponseEntity<ApiResponse<List<HarnessResponse>>> list(@PathVariable Long organizationId,
                                                                    @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(service.list(organizationId, user)));
    }

    @PostMapping("/organization/{organizationId}")
    public ResponseEntity<ApiResponse<HarnessResponse>> create(@PathVariable Long organizationId,
                                                                 @Valid @RequestBody HarnessRequest request,
                                                                 @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(organizationId, request, user)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HarnessResponse>> update(@PathVariable Long id,
                                                                @Valid @RequestBody HarnessRequest request,
                                                                @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(service.update(id, request, user)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, @AuthenticationPrincipal User user) {
        service.delete(id, user);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
