package dev.squadx.dto.harness;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class HarnessResponse {
    Long id;
    @JsonProperty("organization_id") Long organizationId;
    String key;
    String name;
    String vendor;
    String status;
    String model;
    List<String> models;
    @JsonProperty("created_at") Instant createdAt;
    @JsonProperty("updated_at") Instant updatedAt;
}
