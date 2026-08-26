package dev.squadx.dto.intelligence;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.model.enums.IntelligenceSnapshotStatus;

import java.time.Instant;

public record SnapshotResponse(
        Long id,
        @JsonProperty("project_id") Long projectId,
        @JsonProperty("organization_id") Long organizationId,
        @JsonProperty("repository_url") String repositoryUrl,
        String revision,
        String provider,
        @JsonProperty("provider_version") String providerVersion,
        IntelligenceSnapshotStatus status,
        @JsonProperty("indexed_at") Instant indexedAt,
        @JsonProperty("job_id") Long jobId,
        boolean created
) {}

