package dev.squadx.dto.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMessageResponse {

    private Long id;

    @JsonProperty("fromAgentId")
    private Long fromAgentId;

    @JsonProperty("fromAgentName")
    private String fromAgentName;

    @JsonProperty("toAgentId")
    private Long toAgentId;

    @JsonProperty("toAgentName")
    private String toAgentName;

    @JsonProperty("executionId")
    private Long executionId;

    @JsonProperty("messageType")
    private String messageType;

    private String content;

    @JsonProperty("isRead")
    private boolean isRead;

    @JsonProperty("isBroadcast")
    private boolean isBroadcast;

    @JsonProperty("createdAt")
    private Instant createdAt;
}
