package dev.squadx.dto.liveview;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LiveChatMessageRequest {

    @NotBlank
    private String content;

    private String recipientId;
}
