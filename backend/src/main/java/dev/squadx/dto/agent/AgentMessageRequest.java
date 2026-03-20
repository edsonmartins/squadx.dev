package dev.squadx.dto.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMessageRequest {

    @NotNull(message = "From agent ID is required")
    @JsonProperty("fromAgentId")
    private Long fromAgentId;

    @JsonProperty("toAgentId")
    private Long toAgentId;

    @JsonProperty("executionId")
    private Long executionId;

    @JsonProperty("messageType")
    private String messageType;

    @NotBlank(message = "Content is required")
    private String content;
}
