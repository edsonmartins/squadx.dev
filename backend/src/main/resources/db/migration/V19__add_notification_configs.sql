CREATE TABLE notification_configs (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    channel VARCHAR(20) NOT NULL,
    webhook_url VARCHAR(500) NOT NULL,
    channel_name VARCHAR(100),
    notify_task_completed BOOLEAN NOT NULL DEFAULT true,
    notify_task_failed BOOLEAN NOT NULL DEFAULT true,
    notify_approval_required BOOLEAN NOT NULL DEFAULT true,
    notify_agent_error BOOLEAN NOT NULL DEFAULT true,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notification_configs_org ON notification_configs(organization_id);
CREATE INDEX idx_notification_configs_channel ON notification_configs(organization_id, channel);
