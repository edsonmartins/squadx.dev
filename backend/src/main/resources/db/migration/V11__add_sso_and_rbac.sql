-- SSO Configuration per organization
CREATE TABLE sso_configs (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    client_id VARCHAR(500) NOT NULL,
    client_secret VARCHAR(1000) NOT NULL,
    issuer_uri VARCHAR(1000),
    enabled BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(organization_id, provider)
);

-- Custom roles per organization
CREATE TABLE custom_roles (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(organization_id, name)
);

-- Permissions attached to custom roles
CREATE TABLE role_permissions (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL REFERENCES custom_roles(id) ON DELETE CASCADE,
    resource VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(role_id, resource, action)
);

-- Assignment of custom roles to users within an organization
CREATE TABLE user_custom_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    custom_role_id BIGINT NOT NULL REFERENCES custom_roles(id) ON DELETE CASCADE,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, custom_role_id, organization_id)
);

-- Add SSO fields to users table
ALTER TABLE users ADD COLUMN sso_provider VARCHAR(50);
ALTER TABLE users ADD COLUMN sso_subject_id VARCHAR(500);

-- Index for SSO lookups
CREATE INDEX idx_users_sso_provider_subject ON users(sso_provider, sso_subject_id);
CREATE INDEX idx_sso_configs_org ON sso_configs(organization_id);
CREATE INDEX idx_custom_roles_org ON custom_roles(organization_id);
CREATE INDEX idx_role_permissions_role ON role_permissions(role_id);
CREATE INDEX idx_user_custom_roles_user_org ON user_custom_roles(user_id, organization_id);
