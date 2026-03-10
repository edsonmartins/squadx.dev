CREATE TABLE subscriptions (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    stripe_customer_id VARCHAR(255),
    stripe_subscription_id VARCHAR(255),
    plan VARCHAR(30) NOT NULL DEFAULT 'STARTER',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    current_period_start TIMESTAMP WITH TIME ZONE,
    current_period_end TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_plan CHECK (plan IN ('STARTER', 'PROFESSIONAL', 'ENTERPRISE')),
    CONSTRAINT chk_sub_status CHECK (status IN ('ACTIVE', 'PAST_DUE', 'CANCELLED', 'TRIALING'))
);

CREATE UNIQUE INDEX idx_subscriptions_org ON subscriptions(organization_id);
CREATE INDEX idx_subscriptions_stripe ON subscriptions(stripe_subscription_id);
