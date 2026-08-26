package dev.squadx.controlpanel.controller;

import dev.squadx.controlpanel.dto.requirement.RequirementRequest;
import dev.squadx.controlpanel.dto.requirement.RequirementResponse;
import dev.squadx.controlpanel.service.RequirementService;
import dev.squadx.dto.common.ApiResponse;
import dev.squadx.model.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/requirements")
@RequiredArgsConstructor
@Tag(name = "Control Panel — Requirements", description = "Spec requirements + acceptance scenarios")
public class RequirementController {

    private final RequirementService requirementService;

    @PostMapping
    @Operation(summary = "Create a requirement with acceptance scenarios")
    public ResponseEntity<ApiResponse<RequirementResponse>> create(
            @Valid @RequestBody RequirementRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(requirementService.create(request, user), "Requirement created successfully"));
    }

    @GetMapping("/change/{changeId}")
    @Operation(summary = "List requirements of a change")
    public ResponseEntity<ApiResponse<List<RequirementResponse>>> getByChange(
            @PathVariable Long changeId,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.success(requirementService.getByChangeId(changeId, user)));
    }
}

