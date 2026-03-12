-- White-Label branding configuration per organization
CREATE TABLE brand_configs (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    app_name VARCHAR(255),
    logo_url VARCHAR(1000),
    favicon_url VARCHAR(1000),
    primary_color VARCHAR(7),
    secondary_color VARCHAR(7),
    accent_color VARCHAR(7),
    custom_domain VARCHAR(255),
    custom_css TEXT,
    footer_text VARCHAR(500),
    support_email VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(organization_id)
);

-- Index for custom domain lookups
CREATE UNIQUE INDEX idx_brand_configs_custom_domain ON brand_configs(custom_domain) WHERE custom_domain IS NOT NULL;
CREATE INDEX idx_brand_configs_org ON brand_configs(organization_id);
