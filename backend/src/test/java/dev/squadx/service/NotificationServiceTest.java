package dev.squadx.service;

import dev.squadx.dto.notification.NotificationConfigRequest;
import dev.squadx.dto.notification.NotificationConfigResponse;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.NotificationChannel;
import dev.squadx.model.NotificationConfig;
import dev.squadx.model.Organization;
import dev.squadx.repository.NotificationConfigRepository;
import dev.squadx.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationConfigRepository notificationConfigRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private RestClient.Builder restClientBuilder;

    @InjectMocks
    private NotificationService notificationService;

    private Organization testOrg;
    private NotificationConfig testConfig;

    @BeforeEach
    void setUp() {
        testOrg = Organization.builder()
                .name("Test Org")
                .slug("test-org")
                .build();
        testOrg.setId(1L);

        testConfig = NotificationConfig.builder()
                .organization(testOrg)
                .channel(NotificationChannel.SLACK)
                .webhookUrl("https://hooks.slack.com/services/test")
                .channelName("#general")
                .notifyTaskCompleted(true)
                .notifyTaskFailed(true)
                .notifyApprovalRequired(true)
                .notifyAgentError(true)
                .enabled(true)
                .build();
        testConfig.setId(1L);
        testConfig.setCreatedAt(Instant.now());
        testConfig.setUpdatedAt(Instant.now());
    }

    @Nested
    @DisplayName("listByOrganization()")
    class ListByOrganization {

        @Test
        @DisplayName("should return all configs for an organization")
        void shouldReturnConfigs() {
            when(notificationConfigRepository.findByOrganizationId(1L))
                    .thenReturn(List.of(testConfig));

            List<NotificationConfigResponse> result = notificationService.listByOrganization(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getChannel()).isEqualTo("SLACK");
            assertThat(result.get(0).getWebhookUrl()).isEqualTo("https://hooks.slack.com/services/test");
        }

        @Test
        @DisplayName("should return empty list when no configs exist")
        void shouldReturnEmptyList() {
            when(notificationConfigRepository.findByOrganizationId(1L))
                    .thenReturn(List.of());

            List<NotificationConfigResponse> result = notificationService.listByOrganization(1L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("should create a new notification config")
        void shouldCreateConfig() {
            NotificationConfigRequest request = NotificationConfigRequest.builder()
                    .channel("SLACK")
                    .webhookUrl("https://hooks.slack.com/services/new")
                    .channelName("#dev")
                    .build();

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(notificationConfigRepository.existsByOrganizationIdAndChannel(1L, NotificationChannel.SLACK))
                    .thenReturn(false);
            when(notificationConfigRepository.save(any(NotificationConfig.class))).thenAnswer(invocation -> {
                NotificationConfig saved = invocation.getArgument(0);
                saved.setId(2L);
                saved.setCreatedAt(Instant.now());
                saved.setUpdatedAt(Instant.now());
                return saved;
            });

            NotificationConfigResponse response = notificationService.create(1L, request);

            assertThat(response).isNotNull();
            assertThat(response.getChannel()).isEqualTo("SLACK");
            assertThat(response.getWebhookUrl()).isEqualTo("https://hooks.slack.com/services/new");
            verify(notificationConfigRepository).save(any(NotificationConfig.class));
        }

        @Test
        @DisplayName("should throw when organization not found")
        void shouldThrowWhenOrgNotFound() {
            NotificationConfigRequest request = NotificationConfigRequest.builder()
                    .channel("SLACK")
                    .webhookUrl("https://hooks.slack.com/services/test")
                    .build();

            when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.create(99L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Organization not found");
        }

        @Test
        @DisplayName("should throw when config already exists for channel")
        void shouldThrowWhenDuplicateChannel() {
            NotificationConfigRequest request = NotificationConfigRequest.builder()
                    .channel("SLACK")
                    .webhookUrl("https://hooks.slack.com/services/test")
                    .build();

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(notificationConfigRepository.existsByOrganizationIdAndChannel(1L, NotificationChannel.SLACK))
                    .thenReturn(true);

            assertThatThrownBy(() -> notificationService.create(1L, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("should throw for invalid channel")
        void shouldThrowForInvalidChannel() {
            NotificationConfigRequest request = NotificationConfigRequest.builder()
                    .channel("TELEGRAM")
                    .webhookUrl("https://example.com/webhook")
                    .build();

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));

            assertThatThrownBy(() -> notificationService.create(1L, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid channel");
        }

        @Test
        @DisplayName("should throw for non-HTTPS webhook URL")
        void shouldThrowForNonHttpsUrl() {
            NotificationConfigRequest request = NotificationConfigRequest.builder()
                    .channel("SLACK")
                    .webhookUrl("http://hooks.slack.com/services/test")
                    .build();

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(notificationConfigRepository.existsByOrganizationIdAndChannel(1L, NotificationChannel.SLACK))
                    .thenReturn(false);

            assertThatThrownBy(() -> notificationService.create(1L, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("HTTPS");
        }

        @Test
        @DisplayName("should accept case-insensitive channel name")
        void shouldAcceptCaseInsensitiveChannel() {
            NotificationConfigRequest request = NotificationConfigRequest.builder()
                    .channel("discord")
                    .webhookUrl("https://discord.com/api/webhooks/test")
                    .build();

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(notificationConfigRepository.existsByOrganizationIdAndChannel(1L, NotificationChannel.DISCORD))
                    .thenReturn(false);
            when(notificationConfigRepository.save(any(NotificationConfig.class))).thenAnswer(invocation -> {
                NotificationConfig saved = invocation.getArgument(0);
                saved.setId(3L);
                saved.setCreatedAt(Instant.now());
                saved.setUpdatedAt(Instant.now());
                return saved;
            });

            NotificationConfigResponse response = notificationService.create(1L, request);

            assertThat(response.getChannel()).isEqualTo("DISCORD");
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("should update webhook URL")
        void shouldUpdateWebhookUrl() {
            NotificationConfigRequest request = NotificationConfigRequest.builder()
                    .webhookUrl("https://hooks.slack.com/services/updated")
                    .build();

            when(notificationConfigRepository.findById(1L)).thenReturn(Optional.of(testConfig));
            when(notificationConfigRepository.save(any(NotificationConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

            NotificationConfigResponse response = notificationService.update(1L, request);

            assertThat(response.getWebhookUrl()).isEqualTo("https://hooks.slack.com/services/updated");
        }

        @Test
        @DisplayName("should throw when config not found")
        void shouldThrowWhenNotFound() {
            when(notificationConfigRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.update(99L, NotificationConfigRequest.builder().build()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Notification config not found");
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("should delete config when it exists")
        void shouldDeleteConfig() {
            when(notificationConfigRepository.existsById(1L)).thenReturn(true);

            notificationService.delete(1L);

            verify(notificationConfigRepository).deleteById(1L);
        }

        @Test
        @DisplayName("should throw when config not found")
        void shouldThrowWhenNotFound() {
            when(notificationConfigRepository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> notificationService.delete(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Notification config not found");
        }
    }
}
