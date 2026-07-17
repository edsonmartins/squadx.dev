package dev.squadx.controller;

import dev.squadx.dto.approval.ApprovalResponse;
import dev.squadx.dto.approval.CreateApprovalRequest;
import dev.squadx.dto.approval.ReviewApprovalRequest;
import dev.squadx.dto.common.ApiResponse;
import dev.squadx.controller.support.AuthenticatedUserResolver;
import dev.squadx.model.User;
import dev.squadx.model.enums.ApprovalStatus;
import dev.squadx.service.ApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
@Tag(name = "Approvals", description = "Approval workflow endpoints")
public class ApprovalController {

    private final ApprovalService approvalService;

    @PostMapping
    @Operation(summary = "Create an approval request")
    public ResponseEntity<ApiResponse<ApprovalResponse>> create(
            @Valid @RequestBody CreateApprovalRequest request,
            @AuthenticationPrincipal User user) {
        User currentUser = AuthenticatedUserResolver.resolve(user);
        ApprovalResponse response = approvalService.create(request, currentUser);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Approval request created"));
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "Approve or reject an approval request")
    public ResponseEntity<ApiResponse<ApprovalResponse>> review(
            @PathVariable Long id,
            @Valid @RequestBody ReviewApprovalRequest request,
            @AuthenticationPrincipal User user) {
        User currentUser = AuthenticatedUserResolver.resolve(user);
        ApprovalResponse response = approvalService.review(id, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response, "Approval reviewed"));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an approval request")
    public ResponseEntity<ApiResponse<ApprovalResponse>> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        User currentUser = AuthenticatedUserResolver.resolve(user);
        ApprovalResponse response = approvalService.cancel(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response, "Approval cancelled"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get approval by ID")
    public ResponseEntity<ApiResponse<ApprovalResponse>> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        User currentUser = AuthenticatedUserResolver.resolve(user);
        ApprovalResponse response = approvalService.getById(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/task/{taskId}")
    @Operation(summary = "Get approvals by task")
    public ResponseEntity<ApiResponse<Page<ApprovalResponse>>> getByTask(
            @PathVariable Long taskId,
            @PageableDefault Pageable pageable,
            @AuthenticationPrincipal User user) {
        User currentUser = AuthenticatedUserResolver.resolve(user);
        Page<ApprovalResponse> response = approvalService.getByTask(taskId, pageable, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/pending")
    @Operation(summary = "Get pending approvals for current user")
    public ResponseEntity<ApiResponse<Page<ApprovalResponse>>> getPending(
            @AuthenticationPrincipal User user,
            HttpServletRequest request,
            @PageableDefault Pageable pageable) {
        User currentUser = AuthenticatedUserResolver.resolve(user, request);
        Page<ApprovalResponse> response = approvalService.getPendingForUser(currentUser.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "Get approvals by status")
    public ResponseEntity<ApiResponse<Page<ApprovalResponse>>> getByStatus(
            @RequestParam(defaultValue = "PENDING") ApprovalStatus status,
            @PageableDefault Pageable pageable,
            @AuthenticationPrincipal User user) {
        User currentUser = AuthenticatedUserResolver.resolve(user);
        Page<ApprovalResponse> response = approvalService.getByStatus(status, pageable, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
