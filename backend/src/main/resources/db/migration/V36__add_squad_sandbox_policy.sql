-- V36: per-squad sandbox egress policy (RFC-0006 / ADR-0008).
--
-- Egress enforcement lives in the client daemon, but which policy a run gets was only
-- expressible as a daemon-wide env var (SQUADX_NETWORK_POLICY) — so every squad on a
-- daemon shared one setting, and it could not be seen or changed from the product.
-- The squad is the right owner: every agent has a mandatory squad, so a squad-scoped
-- policy always resolves for any executing agent (a project's squad is nullable and
-- therefore cannot be the anchor).
--
-- NOT NULL DEFAULT 'AGENT_DEFAULT' — default-deny plus a working allowlist. Existing
-- squads adopt it on migrate, which is a tightening: the client's own default was
-- already agent-default, so this records the status quo rather than changing it.
ALTER TABLE squads
    ADD COLUMN sandbox_egress_policy VARCHAR(32) NOT NULL DEFAULT 'AGENT_DEFAULT';
