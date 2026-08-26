package dev.squadx.dto.intelligence;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
public record DecisionCandidateResponse(Long id, @JsonProperty("snapshot_id") Long snapshotId,
 String title, String rationale, @JsonProperty("evidence_json") String evidenceJson, String status,
 @JsonProperty("brainsentry_memory_id") String brainsentryMemoryId, @JsonProperty("reviewed_by") Long reviewedBy,
 @JsonProperty("reviewed_at") Instant reviewedAt) {}
