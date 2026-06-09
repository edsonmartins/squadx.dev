package dev.squadx.dto.liveview;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class LiveChatMessageResponse {

    private String id;
    private String sessionId;
    private String participantId;
    private String displayName;
    private String content;
    private String messageType;
    private String recipientId;
    private Instant createdAt;
}
