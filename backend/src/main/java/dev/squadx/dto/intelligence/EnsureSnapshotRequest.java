package dev.squadx.dto.intelligence;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EnsureSnapshotRequest(
        @NotNull @JsonProperty("project_id") Long projectId,
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{7,64}") @Size(max = 128) String revision,
        @NotBlank @Size(max = 80) String provider
) {}

