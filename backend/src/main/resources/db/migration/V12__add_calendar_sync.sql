-- Google Calendar integration
CREATE TABLE calendar_integrations (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    provider        VARCHAR(20) NOT NULL DEFAULT 'google',
    access_token    TEXT NOT NULL,
    refresh_token   TEXT NOT NULL,
    token_expiry    TIMESTAMP WITH TIME ZONE,
    calendar_id     VARCHAR(255),
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE,

    CONSTRAINT chk_calendar_provider CHECK (provider IN ('google')),
    CONSTRAINT uq_calendar_user_org_provider UNIQUE (user_id, organization_id, provider)
);

CREATE INDEX idx_calendar_integrations_user_id ON calendar_integrations(user_id);
CREATE INDEX idx_calendar_integrations_org_id ON calendar_integrations(organization_id);

-- Add external_event_id to meetings for Google Calendar sync
ALTER TABLE meetings ADD COLUMN external_event_id VARCHAR(500);
CREATE INDEX idx_meetings_external_event_id ON meetings(external_event_id);
