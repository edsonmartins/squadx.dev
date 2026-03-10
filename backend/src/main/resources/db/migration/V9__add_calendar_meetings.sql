-- Calendar and meeting integration
CREATE TABLE meetings (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    scheduled_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_minutes INTEGER NOT NULL DEFAULT 30,
    meeting_url     VARCHAR(500),
    status          VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    created_by_id   BIGINT NOT NULL REFERENCES users(id),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE,

    CONSTRAINT chk_meeting_status CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);

CREATE TABLE meeting_attendees (
    id          BIGSERIAL PRIMARY KEY,
    meeting_id  BIGINT NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    rsvp_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_rsvp_status CHECK (rsvp_status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'TENTATIVE')),
    CONSTRAINT uq_meeting_attendee UNIQUE (meeting_id, user_id)
);

CREATE INDEX idx_meetings_org_id ON meetings(organization_id);
CREATE INDEX idx_meetings_scheduled_at ON meetings(scheduled_at);
CREATE INDEX idx_meeting_attendees_user_id ON meeting_attendees(user_id);
