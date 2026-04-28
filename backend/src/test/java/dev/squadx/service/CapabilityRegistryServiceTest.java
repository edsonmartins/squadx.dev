package dev.squadx.service;

import dev.squadx.integration.BrainSentryClient;
import dev.squadx.integration.SquadxLiveClient;
import dev.squadx.model.NotificationChannel;
import dev.squadx.notification.NotificationProvider;
import dev.squadx.notification.NotificationProviderRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CapabilityRegistryServiceTest {

    @Mock
    private BrainSentryClient brainSentryClient;

    @Mock
    private SquadxLiveClient squadxLiveClient;

    @Mock
    private NotificationProvider slackProvider;

    @Mock
    private MemoryPolicyService memoryPolicyService;

    @Test
    @DisplayName("should expose integrations and available notification providers")
    void shouldExposeIntegrationsAndNotificationProviders() {
        when(brainSentryClient.healthCheck()).thenReturn(Map.of("status", "UP", "enabled", true));
        when(squadxLiveClient.healthCheck()).thenReturn(Map.of("status", "DISABLED", "enabled", false));
        when(memoryPolicyService.describePolicy()).thenReturn(Map.of(
                "status", "ACTIVE",
                "enabled", true,
                "memoryScope", "adaptive",
                "proceduralMemoryEnabled", true,
                "proceduralLimit", 5
        ));
        lenient().when(slackProvider.supports(any())).thenReturn(false);
        when(slackProvider.supports(NotificationChannel.SLACK)).thenReturn(true);

        CapabilityRegistryService service = new CapabilityRegistryService(
                brainSentryClient,
                squadxLiveClient,
                new NotificationProviderRegistry(List.of(slackProvider)),
                memoryPolicyService
        );

        Map<String, Object> capabilities = service.getCapabilities();

        assertThat(capabilities).containsKeys("summary", "integrations", "notifications");
        assertThat((Map<String, Object>) capabilities.get("integrations")).containsKeys("brainsentry", "live", "memoryPolicy");
        assertThat(((Map<String, Object>) capabilities.get("summary")).get("proceduralMemoryEnabled")).isEqualTo(true);
        assertThat((List<Map<String, Object>>) capabilities.get("notifications"))
                .anySatisfy(item -> {
                    assertThat(item.get("key")).isEqualTo("notifications.slack");
                    assertThat(item.get("enabled")).isEqualTo(true);
                });
    }
}
