CREATE TABLE code_intelligence_provider_policies (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL UNIQUE REFERENCES organizations(id) ON DELETE CASCADE,
    primary_provider VARCHAR(80) NOT NULL,
    fallback_provider VARCHAR(80),
    shadow_provider VARCHAR(80),
    shadow_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE
);

