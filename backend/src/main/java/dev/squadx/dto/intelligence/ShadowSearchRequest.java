package dev.squadx.dto.intelligence;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ShadowSearchRequest(@NotNull @JsonProperty("snapshot_id") Long snapshotId,
                                  @NotBlank @Size(max = 500) String query) {}
