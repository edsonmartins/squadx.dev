-- Seed Admin User
-- Version: 3.0.0
-- Description: Creates a default admin user and organization for development

-- Default admin user
-- Email: admin@squadx.dev
-- Password: admin123 (BCrypt hashed)
INSERT INTO users (email, password, full_name, role, is_active, email_verified)
VALUES (
    'admin@squadx.dev',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3ePMLzBMsRJVZh.PK1Hy',
    'Admin User',
    'ADMIN',
    true,
    true
) ON CONFLICT (email) DO NOTHING;

-- Default organization
INSERT INTO organizations (name, slug, description, is_active)
VALUES (
    'SquadX Demo',
    'squadx-demo',
    'Default organization for development and testing',
    true
) ON CONFLICT (slug) DO NOTHING;

-- Add admin to organization
INSERT INTO organization_members (organization_id, user_id, role, is_active)
SELECT o.id, u.id, 'OWNER', true
FROM organizations o, users u
WHERE o.slug = 'squadx-demo' AND u.email = 'admin@squadx.dev'
ON CONFLICT (organization_id, user_id) DO NOTHING;

-- Default Squad
INSERT INTO squads (name, description, is_active, organization_id)
SELECT 'Alpha Squad', 'Default AI development squad', true, o.id
FROM organizations o
WHERE o.slug = 'squadx-demo'
ON CONFLICT DO NOTHING;

-- Default Agents for the squad
INSERT INTO agents (name, agent_type, description, model_id, temperature, max_tokens, is_active, squad_id)
SELECT
    'Claude Fullstack',
    'FULLSTACK',
    'Full-stack development agent powered by Claude',
    'claude-3-5-sonnet-20241022',
    0.7,
    4096,
    true,
    s.id
FROM squads s
JOIN organizations o ON s.organization_id = o.id
WHERE o.slug = 'squadx-demo' AND s.name = 'Alpha Squad'
ON CONFLICT DO NOTHING;

INSERT INTO agents (name, agent_type, description, model_id, temperature, max_tokens, is_active, squad_id)
SELECT
    'GPT Backend',
    'BACKEND',
    'Backend specialist agent powered by GPT-4',
    'gpt-4-turbo',
    0.5,
    4096,
    true,
    s.id
FROM squads s
JOIN organizations o ON s.organization_id = o.id
WHERE o.slug = 'squadx-demo' AND s.name = 'Alpha Squad'
ON CONFLICT DO NOTHING;

INSERT INTO agents (name, agent_type, description, model_id, temperature, max_tokens, is_active, squad_id)
SELECT
    'React Frontend',
    'FRONTEND',
    'Frontend specialist for React/Next.js applications',
    'claude-3-5-sonnet-20241022',
    0.7,
    4096,
    true,
    s.id
FROM squads s
JOIN organizations o ON s.organization_id = o.id
WHERE o.slug = 'squadx-demo' AND s.name = 'Alpha Squad'
ON CONFLICT DO NOTHING;
