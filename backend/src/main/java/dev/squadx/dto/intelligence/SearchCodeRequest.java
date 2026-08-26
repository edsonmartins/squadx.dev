package dev.squadx.dto.intelligence;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

public record SearchCodeRequest(
        @NotNull @JsonProperty("snapshot_id") Long snapshotId,
        @NotBlank @Size(max = 500) String query,
        @Min(0) int page,
        @Min(1) @Max(200) int size
) {}

