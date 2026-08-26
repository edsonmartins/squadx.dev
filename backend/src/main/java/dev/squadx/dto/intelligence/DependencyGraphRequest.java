package dev.squadx.dto.intelligence;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DependencyGraphRequest(
        @NotNull @JsonProperty("snapshot_id") Long snapshotId,
        @NotBlank @Size(max = 500) @JsonProperty("symbol_id") String symbolId,
        @Min(1) @Max(10) int depth
) {}
