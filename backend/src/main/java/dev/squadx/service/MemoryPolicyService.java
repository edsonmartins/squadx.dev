package dev.squadx.service;

import dev.squadx.integration.IntegrationConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MemoryPolicyService {

    private final IntegrationConfig integrationConfig;

    public Map<String, Object> describePolicy() {
        IntegrationConfig.BrainSentryConfig config = integrationConfig.getBrainsentry();

        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("provider", "brainsentry");
        policy.put("enabled", config.isEnabled());
        policy.put("memoryScope", config.getMemoryScope());
        policy.put("proceduralMemoryEnabled", config.isProceduralMemoryEnabled());
        policy.put("proceduralLimit", config.getProceduralLimit());
        policy.put("tenantMode", config.isPerOrganizationTenant() ? "per_organization" : "static");
        policy.put("recommendedScopes", List.of(
                "organization",
                "project",
                "organization-agent",
                "project-agent",
                "execution"
        ));
        policy.put("status", resolveStatus(config));
        return policy;
    }

    private String resolveStatus(IntegrationConfig.BrainSentryConfig config) {
        if (!config.isEnabled()) {
            return "DISABLED";
        }
        if (!config.isProceduralMemoryEnabled()) {
            return "CONTEXT_ONLY";
        }
        return "ACTIVE";
    }
}
