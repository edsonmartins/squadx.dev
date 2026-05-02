package dev.squadx.dto.memory;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class MemorySkillRequest {

    @NotNull
    @JsonProperty("organization_id")
    private Long organizationId;

    @JsonProperty("project_id")
    private Long projectId;

    @JsonProperty("agent_id")
    private Long agentId;

    @JsonProperty("agent_type")
    private String agentType;

    @NotBlank
    private String title;

    @NotBlank
    private String summary;

    private String content;

    private boolean antipattern;

    private List<String> steps;

    @JsonProperty("files_modified")
    private List<String> filesModified;
}
