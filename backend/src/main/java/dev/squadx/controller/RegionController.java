package dev.squadx.controller;

import dev.squadx.config.RegionConfig;
import dev.squadx.dto.common.ApiResponse;
import dev.squadx.dto.region.RegionInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
@Tag(name = "Regions", description = "Multi-region management endpoints")
public class RegionController {

    private final RegionConfig regionConfig;

    private static final Map<String, String> REGION_DISPLAY_NAMES = Map.of(
            "us-east-1", "US East (N. Virginia)",
            "us-west-2", "US West (Oregon)",
            "eu-west-1", "Europe (Ireland)",
            "eu-central-1", "Europe (Frankfurt)",
            "ap-southeast-1", "Asia Pacific (Singapore)",
            "ap-northeast-1", "Asia Pacific (Tokyo)",
            "sa-east-1", "South America (Sao Paulo)"
    );

    @GetMapping
    @Operation(summary = "List all available regions")
    public ResponseEntity<ApiResponse<List<RegionInfo>>> listRegions() {
        List<RegionInfo> regions = regionConfig.getAvailable().stream()
                .map(name -> RegionInfo.builder()
                        .name(name)
                        .displayName(REGION_DISPLAY_NAMES.getOrDefault(name, name))
                        .status(name.equals(regionConfig.getCurrent()) ? "active" : "available")
                        .build())
                .toList();
        return ResponseEntity.ok(ApiResponse.success(regions));
    }

    @GetMapping("/current")
    @Operation(summary = "Get current region information")
    public ResponseEntity<ApiResponse<RegionInfo>> getCurrentRegion() {
        RegionInfo current = RegionInfo.builder()
                .name(regionConfig.getCurrent())
                .displayName(REGION_DISPLAY_NAMES.getOrDefault(
                        regionConfig.getCurrent(), regionConfig.getCurrent()))
                .status("active")
                .build();
        return ResponseEntity.ok(ApiResponse.success(current));
    }
}
