package dev.squadx.dto.intelligence;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
public record DecisionCandidateRequest(@NotNull @JsonProperty("snapshot_id") Long snapshotId,
                                       @NotBlank @Size(max = 200) String title,
                                       @NotBlank @Size(max = 4000) String rationale,
                                       @NotBlank @Size(max = 12000) @JsonProperty("evidence_json") String evidenceJson) {}
