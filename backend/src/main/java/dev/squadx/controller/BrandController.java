package dev.squadx.controller;

import dev.squadx.dto.brand.BrandConfigRequest;
import dev.squadx.dto.brand.BrandConfigResponse;
import dev.squadx.dto.common.ApiResponse;
import dev.squadx.model.User;
import dev.squadx.service.BrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/branding")
@RequiredArgsConstructor
@Tag(name = "Branding", description = "White-label branding management endpoints")
public class BrandController {

    private final BrandService brandService;

    @GetMapping("/{orgId}")
    @Operation(summary = "Get brand configuration for an organization")
    public ResponseEntity<ApiResponse<BrandConfigResponse>> getBrandConfig(
            @PathVariable Long orgId,
            @AuthenticationPrincipal User user
    ) {
        BrandConfigResponse config = brandService.getByOrganization(orgId, user);
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @PutMapping("/{orgId}")
    @Operation(summary = "Create or update brand configuration for an organization")
    public ResponseEntity<ApiResponse<BrandConfigResponse>> upsertBrandConfig(
            @PathVariable Long orgId,
            @RequestBody BrandConfigRequest request,
            @AuthenticationPrincipal User user
    ) {
        BrandConfigResponse config = brandService.upsert(orgId, request, user);
        return ResponseEntity.ok(ApiResponse.success(config, "Brand configuration updated successfully"));
    }

    @DeleteMapping("/{orgId}")
    @Operation(summary = "Remove branding and revert to default")
    public ResponseEntity<ApiResponse<Void>> deleteBrandConfig(
            @PathVariable Long orgId,
            @AuthenticationPrincipal User user
    ) {
        brandService.delete(orgId, user);
        return ResponseEntity.ok(ApiResponse.success(null, "Brand configuration removed successfully"));
    }

    @GetMapping("/domain/{domain}")
    @Operation(summary = "Resolve brand configuration by custom domain (public endpoint)")
    public ResponseEntity<ApiResponse<BrandConfigResponse>> getBrandByDomain(
            @PathVariable String domain
    ) {
        BrandConfigResponse config = brandService.getByDomain(domain);
        return ResponseEntity.ok(ApiResponse.success(config));
    }
}
