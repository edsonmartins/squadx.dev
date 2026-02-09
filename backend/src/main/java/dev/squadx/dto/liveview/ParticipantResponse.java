package dev.squadx.dto.liveview;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ParticipantResponse {
    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private Boolean canControl;
    private Boolean isHost;
    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;
}
