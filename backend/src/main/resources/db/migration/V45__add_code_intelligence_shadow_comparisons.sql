CREATE TABLE code_intelligence_shadow_comparisons (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    snapshot_id BIGINT NOT NULL REFERENCES code_intelligence_snapshots(id),
    query VARCHAR(500) NOT NULL,
    primary_provider VARCHAR(80) NOT NULL,
    shadow_provider VARCHAR(80) NOT NULL,
    primary_hits INTEGER NOT NULL,
    shadow_hits INTEGER NOT NULL,
    overlap_hits INTEGER NOT NULL,
    divergence_score DOUBLE PRECISION NOT NULL,
    primary_latency_ms BIGINT,
    shadow_latency_ms BIGINT,
    error_message TEXT,
    compared_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_ci_shadow_snapshot ON code_intelligence_shadow_comparisons(snapshot_id, compared_at DESC);
