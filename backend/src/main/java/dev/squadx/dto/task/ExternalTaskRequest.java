package dev.squadx.dto.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExternalTaskRequest(
        @NotBlank @Size(max = 500) @JsonProperty("repository_url") String repositoryUrl,
        @NotNull @JsonProperty("review_id") Long reviewId,
        @NotBlank @Size(max = 255) String title,
        String description,
        @Size(max = 128) @JsonProperty("head_revision") String headRevision,
        @JsonProperty("auto_start") Boolean autoStart
) {}
