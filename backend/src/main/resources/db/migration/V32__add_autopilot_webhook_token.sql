-- Autopilots can be fired via a public webhook URL identified by a secret token.
ALTER TABLE autopilots
    ADD COLUMN webhook_token VARCHAR(64);
CREATE UNIQUE INDEX idx_autopilots_webhook_token ON autopilots(webhook_token);
