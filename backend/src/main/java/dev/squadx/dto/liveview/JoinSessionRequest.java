package dev.squadx.dto.liveview;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JoinSessionRequest {

    @NotBlank(message = "Session code is required")
    @Size(min = 6, max = 6, message = "Session code must be 6 characters")
    private String code;
}
