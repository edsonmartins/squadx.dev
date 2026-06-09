package dev.squadx.controlpanel.dto.change;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.controlpanel.model.enums.ChangePhase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeResponse {

    private Long id;
    private String module;
    private ChangePhase phase;

    @JsonProperty("project_id")
    private Long projectId;

    @JsonProperty("project_name")
    private String projectName;

    @JsonProperty("created_by_id")
    private Long createdById;

    @JsonProperty("created_by_name")
    private String createdByName;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
