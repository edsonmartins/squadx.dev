package dev.squadx.dto.intelligence;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChangeImpactRequest(
        @NotNull @JsonProperty("base_snapshot_id") Long baseSnapshotId,
        @NotNull @JsonProperty("head_snapshot_id") Long headSnapshotId,
        @NotEmpty @Size(max = 500) List<@Valid @Size(max = 500) String> changedPaths
) {}
