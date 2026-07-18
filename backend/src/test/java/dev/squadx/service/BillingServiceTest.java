package dev.squadx.service;

import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Organization;
import dev.squadx.model.Subscription;
import dev.squadx.model.User;
import dev.squadx.model.enums.SubscriptionPlan;
import dev.squadx.model.enums.SubscriptionStatus;
import dev.squadx.model.enums.UserRole;
import dev.squadx.repository.OrganizationRepository;
import dev.squadx.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationAccessGuard accessGuard;

    @InjectMocks
    private BillingService billingService;

    private Organization testOrg;
    private Subscription testSubscription;
    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        testUser = User.builder()
                .email("test@example.com")
                .fullName("Test User")
                .role(UserRole.USER)
                .isActive(true)
                .build();
        testUser.setId(1L);

        testOrg = Organization.builder()
                .name("Test Org")
                .slug("test-org")
                .build();
        testOrg.setId(1L);

        testSubscription = Subscription.builder()
                .organization(testOrg)
                .stripeCustomerId("cus_test123")
                .stripeSubscriptionId("sub_test123")
                .plan(SubscriptionPlan.STARTER)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(Instant.now())
                .currentPeriodEnd(Instant.now().plusSeconds(2592000))
                .build();
        testSubscription.setId(1L);
    }

    private void setStripeApiKey(String value) throws Exception {
        Field field = BillingService.class.getDeclaredField("stripeApiKey");
        field.setAccessible(true);
        field.set(billingService, value);
    }

    private void setWebhookSecret(String value) throws Exception {
        Field field = BillingService.class.getDeclaredField("webhookSecret");
        field.setAccessible(true);
        field.set(billingService, value);
    }

    @Nested
    @DisplayName("getSubscription()")
    class GetSubscription {

        @Test
        @DisplayName("should return subscription when found")
        void shouldReturnSubscriptionWhenFound() {
            when(subscriptionRepository.findByOrganizationId(1L))
                    .thenReturn(Optional.of(testSubscription));

            Subscription result = billingService.getSubscription(1L, testUser);

            assertThat(result).isNotNull();
            assertThat(result.getStripeCustomerId()).isEqualTo("cus_test123");
            assertThat(result.getPlan()).isEqualTo(SubscriptionPlan.STARTER);
            assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            verify(subscriptionRepository).findByOrganizationId(1L);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when subscription not found")
        void shouldThrowWhenSubscriptionNotFound() {
            when(subscriptionRepository.findByOrganizationId(99L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> billingService.getSubscription(99L, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Subscription not found");

            verify(subscriptionRepository).findByOrganizationId(99L);
        }
    }

    @Nested
    @DisplayName("validateStripeConfigured()")
    class ValidateStripeConfigured {

        @Test
        @DisplayName("should throw BadRequestException when api-key is blank")
        void shouldThrowWhenApiKeyIsBlank() throws Exception {
            setStripeApiKey("");

            assertThatThrownBy(() -> billingService.createCheckoutSession(1L, "STARTER", testUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Stripe is not configured");

            verifyNoInteractions(organizationRepository);
        }

        @Test
        @DisplayName("should throw BadRequestException when api-key is null")
        void shouldThrowWhenApiKeyIsNull() throws Exception {
            setStripeApiKey(null);

            assertThatThrownBy(() -> billingService.cancelSubscription(1L, testUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Stripe is not configured");

            verifyNoInteractions(subscriptionRepository);
        }
    }

    @Nested
    @DisplayName("createCheckoutSession()")
    class CreateCheckoutSession {

        @Test
        @DisplayName("should throw BadRequestException when Stripe is not configured")
        void shouldThrowWhenStripeNotConfigured() throws Exception {
            setStripeApiKey("");

            assertThatThrownBy(() -> billingService.createCheckoutSession(1L, "STARTER", testUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Stripe is not configured");

            verifyNoInteractions(organizationRepository);
            verifyNoInteractions(subscriptionRepository);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when organization not found")
        void shouldThrowWhenOrgNotFound() throws Exception {
            setStripeApiKey("sk_test_valid");

            when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> billingService.createCheckoutSession(99L, "STARTER", testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Organization not found");

            verify(organizationRepository).findById(99L);
        }
    }

    @Nested
    @DisplayName("cancelSubscription()")
    class CancelSubscription {

        @Test
        @DisplayName("should throw BadRequestException when Stripe is not configured")
        void shouldThrowWhenStripeNotConfigured() throws Exception {
            setStripeApiKey("");

            assertThatThrownBy(() -> billingService.cancelSubscription(1L, testUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Stripe is not configured");

            verifyNoInteractions(subscriptionRepository);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when subscription not found")
        void shouldThrowWhenSubscriptionNotFound() throws Exception {
            setStripeApiKey("sk_test_valid");

            when(subscriptionRepository.findByOrganizationId(99L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> billingService.cancelSubscription(99L, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Subscription not found");

            verify(subscriptionRepository).findByOrganizationId(99L);
        }

        @Test
        @DisplayName("should cancel subscription and update status when no Stripe subscription id")
        void shouldCancelSubscriptionWithoutStripeId() throws Exception {
            setStripeApiKey("sk_test_valid");

            Subscription subWithoutStripeId = Subscription.builder()
                    .organization(testOrg)
                    .stripeCustomerId("cus_test123")
                    .stripeSubscriptionId(null)
                    .plan(SubscriptionPlan.STARTER)
                    .status(SubscriptionStatus.ACTIVE)
                    .build();
            subWithoutStripeId.setId(2L);

            when(subscriptionRepository.findByOrganizationId(1L))
                    .thenReturn(Optional.of(subWithoutStripeId));
            when(subscriptionRepository.save(any(Subscription.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            billingService.cancelSubscription(1L, testUser);

            assertThat(subWithoutStripeId.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
            verify(subscriptionRepository).save(subWithoutStripeId);
        }
    }

    @Nested
    @DisplayName("handleWebhook()")
    class HandleWebhook {

        @Test
        @DisplayName("should throw BadRequestException when Stripe is not configured")
        void shouldThrowWhenStripeNotConfigured() throws Exception {
            setStripeApiKey("");

            assertThatThrownBy(() -> billingService.handleWebhook("payload", "sig"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Stripe is not configured");
        }

        @Test
        @DisplayName("should throw BadRequestException when api-key is blank whitespace")
        void shouldThrowWhenApiKeyIsWhitespace() throws Exception {
            setStripeApiKey("   ");

            assertThatThrownBy(() -> billingService.handleWebhook("payload", "sig"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Stripe is not configured");
        }
    }

    @Nested
    @DisplayName("cross-tenant access control")
    class CrossTenantAccess {

        @Test
        @DisplayName("getSubscription denies a non-member and never touches the subscription")
        void getSubscription_deniesNonMember() {
            doThrow(new ForbiddenException("nope"))
                    .when(accessGuard).requireMember(anyLong(), anyLong());

            assertThatThrownBy(() -> billingService.getSubscription(1L, testUser))
                    .isInstanceOf(ForbiddenException.class);

            verifyNoInteractions(subscriptionRepository);
        }
    }
}
