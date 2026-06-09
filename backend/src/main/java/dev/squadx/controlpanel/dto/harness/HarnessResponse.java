package dev.squadx.controlpanel.dto.harness;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.controlpanel.model.enums.HarnessStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HarnessResponse {

    private Long id;
    private String key;
    private String name;
    private String vendor;
    private HarnessStatus status;
    private String model;
    private List<String> models;

    @JsonProperty("organization_id")
    private Long organizationId;

    @JsonProperty("agent_id")
    private Long agentId;

    @JsonProperty("agent_name")
    private String agentName;

    @JsonProperty("last_connected_at")
    private Instant lastConnectedAt;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
