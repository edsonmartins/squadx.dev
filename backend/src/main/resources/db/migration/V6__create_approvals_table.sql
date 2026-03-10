-- Approval workflow for task executions
CREATE TABLE approvals (
    id              BIGSERIAL PRIMARY KEY,
    task_id         BIGINT NOT NULL REFERENCES tasks(id),
    execution_id    BIGINT REFERENCES executions(id),
    requested_by_id BIGINT NOT NULL REFERENCES users(id),
    reviewer_id     BIGINT REFERENCES users(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approval_type   VARCHAR(30) NOT NULL DEFAULT 'COMMIT',
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    changes_summary TEXT,
    review_comment  TEXT,
    reviewed_at     TIMESTAMP WITH TIME ZONE,
    expires_at      TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE,

    CONSTRAINT chk_approval_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT chk_approval_type CHECK (approval_type IN ('COMMIT', 'DEPLOY', 'DELETE', 'MERGE', 'CONFIGURATION'))
);

CREATE INDEX idx_approvals_task_id ON approvals(task_id);
CREATE INDEX idx_approvals_reviewer_id ON approvals(reviewer_id);
CREATE INDEX idx_approvals_status ON approvals(status);
CREATE INDEX idx_approvals_created_at ON approvals(created_at);
