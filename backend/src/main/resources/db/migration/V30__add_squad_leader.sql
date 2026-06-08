-- Squad leader-delegation: a squad has a leader agent. Work assigned to the squad
-- routes to the leader (stable addressing as the team grows), inspired by Multica.
ALTER TABLE squads
    ADD COLUMN leader_agent_id BIGINT REFERENCES agents(id) ON DELETE SET NULL;
