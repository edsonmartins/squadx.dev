package dev.squadx.dto.liveview;

import dev.squadx.model.enums.LiveSessionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class LiveSessionResponse {
    private Long id;
    private String code;
    private Long taskId;
    private String taskTitle;
    private Long agentId;
    private String agentName;
    private String sessionMode;
    private Long hostUserId;
    private String hostUserName;
    private String containerId;
    private LiveSessionStatus status;
    private Integer maxViewers;
    private Integer currentViewers;
    private String resolution;
    private String viewerUrl;
    private String hostUrl;
    private String externalSessionId;
    private String externalJoinCode;
    private String externalJoinUrl;
    private String externalAgentParticipantId;
    private String externalAgentDisplayName;
    private List<ParticipantResponse> participants;
    private Instant createdAt;
    private LocalDateTime endedAt;
}
