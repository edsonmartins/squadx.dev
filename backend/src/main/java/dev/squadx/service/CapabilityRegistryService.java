package dev.squadx.service;

import dev.squadx.integration.BrainSentryClient;
import dev.squadx.integration.LinktorClient;
import dev.squadx.integration.SquadxLiveClient;
import dev.squadx.notification.NotificationProviderRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CapabilityRegistryService {

    private final BrainSentryClient brainSentryClient;
    private final SquadxLiveClient squadxLiveClient;
    private final LinktorClient linktorClient;
    private final NotificationProviderRegistry notificationProviderRegistry;
    private final MemoryPolicyService memoryPolicyService;

    public Map<String, Object> getCapabilities() {
        Map<String, Object> integrations = new LinkedHashMap<>();
        integrations.put("brainsentry", brainSentryClient.healthCheck());
        integrations.put("live", squadxLiveClient.healthCheck());
        integrations.put("linktor", linktorClient.healthCheck());
        integrations.put("memoryPolicy", memoryPolicyService.describePolicy());

        List<Map<String, Object>> notifications = notificationProviderRegistry.describeProviders();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("integrations", integrations.size());
        summary.put("notificationProviders", notifications.stream()
                .filter(item -> Boolean.TRUE.equals(item.get("enabled")))
                .count());
        summary.put("proceduralMemoryEnabled",
                Boolean.TRUE.equals(memoryPolicyService.describePolicy().get("proceduralMemoryEnabled")));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("summary", summary);
        response.put("integrations", integrations);
        response.put("notifications", notifications);
        return response;
    }
}
