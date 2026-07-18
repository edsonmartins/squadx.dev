package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.model.Subscription;
import dev.squadx.model.User;
import dev.squadx.service.BillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
@Tag(name = "Billing", description = "Billing and subscription management endpoints")
public class BillingController {

    private final BillingService billingService;

    @PostMapping("/checkout")
    @Operation(summary = "Create a Stripe checkout session")
    public ResponseEntity<ApiResponse<Map<String, String>>> createCheckoutSession(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal User user
    ) {
        Long orgId = Long.valueOf(request.get("organizationId").toString());
        String plan = request.get("plan").toString();

        Map<String, String> session = billingService.createCheckoutSession(orgId, plan, user);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(session, "Checkout session created successfully"));
    }

    @PostMapping("/webhook")
    @Operation(summary = "Handle Stripe webhook events")
    public ResponseEntity<ApiResponse<Void>> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        billingService.handleWebhook(payload, sigHeader);
        return ResponseEntity.ok(ApiResponse.success(null, "Webhook processed successfully"));
    }

    @GetMapping("/subscription")
    @Operation(summary = "Get current subscription for an organization")
    public ResponseEntity<ApiResponse<Subscription>> getSubscription(
            @RequestParam Long organizationId,
            @AuthenticationPrincipal User user
    ) {
        Subscription subscription = billingService.getSubscription(organizationId, user);
        return ResponseEntity.ok(ApiResponse.success(subscription));
    }

    @PostMapping("/cancel")
    @Operation(summary = "Cancel subscription for an organization")
    public ResponseEntity<ApiResponse<Void>> cancelSubscription(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal User user
    ) {
        Long orgId = Long.valueOf(request.get("organizationId").toString());
        billingService.cancelSubscription(orgId, user);
        return ResponseEntity.ok(ApiResponse.success(null, "Subscription cancelled successfully"));
    }
}
