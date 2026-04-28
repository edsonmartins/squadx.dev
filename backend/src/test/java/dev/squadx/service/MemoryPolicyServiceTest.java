package dev.squadx.service;

import dev.squadx.integration.IntegrationConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryPolicyServiceTest {

    @Test
    @DisplayName("should expose procedural memory policy from integration config")
    void shouldExposeMemoryPolicy() {
        IntegrationConfig config = new IntegrationConfig();
        config.getBrainsentry().setEnabled(true);
        config.getBrainsentry().setMemoryScope("project-agent");
        config.getBrainsentry().setProceduralMemoryEnabled(true);
        config.getBrainsentry().setProceduralLimit(7);

        MemoryPolicyService service = new MemoryPolicyService(config);

        Map<String, Object> policy = service.describePolicy();

        assertThat(policy.get("provider")).isEqualTo("brainsentry");
        assertThat(policy.get("memoryScope")).isEqualTo("project-agent");
        assertThat(policy.get("proceduralMemoryEnabled")).isEqualTo(true);
        assertThat(policy.get("proceduralLimit")).isEqualTo(7);
        assertThat(policy.get("status")).isEqualTo("ACTIVE");
    }
}
