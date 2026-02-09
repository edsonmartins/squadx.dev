package dev.squadx.dto.liveview;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LiveSessionRequest {

    @NotNull(message = "Task ID is required")
    private Long taskId;

    private String containerId;

    @Min(value = 1, message = "Max viewers must be at least 1")
    @Max(value = 50, message = "Max viewers cannot exceed 50")
    private Integer maxViewers = 5;

    private String resolution = "1280x720";
}
