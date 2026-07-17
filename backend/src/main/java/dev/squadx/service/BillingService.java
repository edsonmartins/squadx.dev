package dev.squadx.service;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Organization;
import dev.squadx.model.Subscription;
import dev.squadx.model.User;
import dev.squadx.model.enums.SubscriptionPlan;
import dev.squadx.model.enums.SubscriptionStatus;
import dev.squadx.repository.OrganizationRepository;
import dev.squadx.repository.SubscriptionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private final SubscriptionRepository subscriptionRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationAccessGuard accessGuard;

    @Value("${stripe.api-key:}")
    private String stripeApiKey;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${stripe.prices.starter:}")
    private String starterPriceId;

    @Value("${stripe.prices.professional:}")
    private String professionalPriceId;

    @PostConstruct
    public void init() {
        if (stripeApiKey != null && !stripeApiKey.isBlank()) {
            Stripe.apiKey = stripeApiKey;
            log.info("Stripe API initialized successfully");
        } else {
            log.warn("Stripe API key is not configured. Billing features will be unavailable.");
        }
    }

    @Transactional
    public Map<String, String> createCheckoutSession(Long orgId, String plan, User currentUser) {
        accessGuard.requireMember(orgId, currentUser.getId());
        validateStripeConfigured();

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        String priceId = resolvePriceId(plan);
        if (priceId == null || priceId.isBlank()) {
            throw new BadRequestException("Price not configured for plan: " + plan);
        }

        try {
            Subscription subscription = subscriptionRepository.findByOrganizationId(orgId)
                    .orElse(null);

            String customerId;
            if (subscription != null && subscription.getStripeCustomerId() != null) {
                customerId = subscription.getStripeCustomerId();
            } else {
                CustomerCreateParams customerParams = CustomerCreateParams.builder()
                        .setName(org.getName())
                        .putMetadata("organization_id", orgId.toString())
                        .build();
                Customer customer = Customer.create(customerParams);
                customerId = customer.getId();

                if (subscription == null) {
                    subscription = Subscription.builder()
                            .organization(org)
                            .stripeCustomerId(customerId)
                            .plan(SubscriptionPlan.STARTER)
                            .status(SubscriptionStatus.ACTIVE)
                            .build();
                } else {
                    subscription.setStripeCustomerId(customerId);
                }
                subscriptionRepository.save(subscription);
            }

            SessionCreateParams sessionParams = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomer(customerId)
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setPrice(priceId)
                            .setQuantity(1L)
                            .build())
                    .setSuccessUrl("https://app.squadx.dev/billing/success?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl("https://app.squadx.dev/billing/cancel")
                    .putMetadata("organization_id", orgId.toString())
                    .putMetadata("plan", plan)
                    .build();

            Session session = Session.create(sessionParams);

            return Map.of(
                    "sessionId", session.getId(),
                    "url", session.getUrl()
            );
        } catch (StripeException e) {
            log.error("Stripe error creating checkout session for org={}: {}", orgId, e.getMessage(), e);
            throw new BadRequestException("Failed to create checkout session: " + e.getMessage());
        }
    }

    @Transactional
    public void handleWebhook(String payload, String sigHeader) {
        validateStripeConfigured();

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("Stripe webhook signature verification failed: {}", e.getMessage());
            throw new BadRequestException("Invalid webhook signature");
        }

        log.info("Processing Stripe webhook event: type={}, id={}", event.getType(), event.getId());

        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutCompleted(event);
            case "customer.subscription.updated" -> handleSubscriptionUpdated(event);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
            case "invoice.payment_failed" -> handlePaymentFailed(event);
            default -> log.info("Unhandled Stripe event type: {}", event.getType());
        }
    }

    @Transactional(readOnly = true)
    public Subscription getSubscription(Long orgId, User currentUser) {
        accessGuard.requireMember(orgId, currentUser.getId());
        return subscriptionRepository.findByOrganizationId(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found for organization"));
    }

    @Transactional
    public void cancelSubscription(Long orgId, User currentUser) {
        accessGuard.requireMember(orgId, currentUser.getId());
        validateStripeConfigured();

        Subscription subscription = subscriptionRepository.findByOrganizationId(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found for organization"));

        if (subscription.getStripeSubscriptionId() != null) {
            try {
                com.stripe.model.Subscription stripeSub =
                        com.stripe.model.Subscription.retrieve(subscription.getStripeSubscriptionId());
                stripeSub.cancel();
            } catch (StripeException e) {
                log.error("Stripe error cancelling subscription for org={}: {}", orgId, e.getMessage(), e);
                throw new BadRequestException("Failed to cancel subscription: " + e.getMessage());
            }
        }

        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscriptionRepository.save(subscription);
        log.info("Subscription cancelled for org={}", orgId);
    }

    private void handleCheckoutCompleted(Event event) {
        Session session = (Session) event.getDataObjectDeserializer()
                .getObject().orElse(null);
        if (session == null) return;

        String orgIdStr = session.getMetadata().get("organization_id");
        String plan = session.getMetadata().get("plan");
        if (orgIdStr == null) return;

        Long orgId = Long.valueOf(orgIdStr);
        Subscription subscription = subscriptionRepository.findByOrganizationId(orgId)
                .orElse(null);

        if (subscription != null) {
            subscription.setStripeSubscriptionId(session.getSubscription());
            subscription.setPlan(SubscriptionPlan.valueOf(plan.toUpperCase()));
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setCurrentPeriodStart(Instant.now());
            subscriptionRepository.save(subscription);
            log.info("Checkout completed for org={}, plan={}", orgId, plan);
        }
    }

    private void handleSubscriptionUpdated(Event event) {
        com.stripe.model.Subscription stripeSub = (com.stripe.model.Subscription) event
                .getDataObjectDeserializer().getObject().orElse(null);
        if (stripeSub == null) return;

        subscriptionRepository.findByStripeSubscriptionId(stripeSub.getId())
                .ifPresent(subscription -> {
                    String status = stripeSub.getStatus();
                    switch (status) {
                        case "active" -> subscription.setStatus(SubscriptionStatus.ACTIVE);
                        case "past_due" -> subscription.setStatus(SubscriptionStatus.PAST_DUE);
                        case "canceled" -> subscription.setStatus(SubscriptionStatus.CANCELLED);
                        case "trialing" -> subscription.setStatus(SubscriptionStatus.TRIALING);
                    }
                    subscription.setCurrentPeriodStart(
                            Instant.ofEpochSecond(stripeSub.getCurrentPeriodStart()));
                    subscription.setCurrentPeriodEnd(
                            Instant.ofEpochSecond(stripeSub.getCurrentPeriodEnd()));
                    subscriptionRepository.save(subscription);
                    log.info("Subscription updated: stripeId={}, status={}", stripeSub.getId(), status);
                });
    }

    private void handleSubscriptionDeleted(Event event) {
        com.stripe.model.Subscription stripeSub = (com.stripe.model.Subscription) event
                .getDataObjectDeserializer().getObject().orElse(null);
        if (stripeSub == null) return;

        subscriptionRepository.findByStripeSubscriptionId(stripeSub.getId())
                .ifPresent(subscription -> {
                    subscription.setStatus(SubscriptionStatus.CANCELLED);
                    subscriptionRepository.save(subscription);
                    log.info("Subscription deleted: stripeId={}", stripeSub.getId());
                });
    }

    private void handlePaymentFailed(Event event) {
        log.warn("Payment failed for event: {}", event.getId());
    }

    private String resolvePriceId(String plan) {
        return switch (plan.toUpperCase()) {
            case "STARTER" -> starterPriceId;
            case "PROFESSIONAL" -> professionalPriceId;
            default -> throw new BadRequestException("Unknown plan: " + plan);
        };
    }

    private void validateStripeConfigured() {
        if (stripeApiKey == null || stripeApiKey.isBlank()) {
            throw new BadRequestException("Stripe is not configured. Set STRIPE_API_KEY environment variable.");
        }
    }
}
