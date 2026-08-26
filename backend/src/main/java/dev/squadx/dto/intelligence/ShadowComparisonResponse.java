package dev.squadx.dto.intelligence;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ShadowComparisonResponse(Long id, @JsonProperty("snapshot_id") Long snapshotId,
                                       String query, @JsonProperty("primary_provider") String primaryProvider,
                                       @JsonProperty("shadow_provider") String shadowProvider,
                                       int primaryHits, int shadowHits, int overlapHits,
                                       double divergenceScore, Long primaryLatencyMs, Long shadowLatencyMs,
                                       String errorMessage, Instant comparedAt) {}
