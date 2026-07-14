-- V35: opt-in human-approval gate for completed executions.
-- When a task has requires_approval = TRUE, a completed run parks at IN_REVIEW and a
-- PENDING approval is created; the task only reaches DONE via ApprovalService.review.
-- See documentos/LEARNINGS-Lemma.md (#7) and ADR-0004 (state machine / gate objetivo).
ALTER TABLE tasks
    ADD COLUMN requires_approval BOOLEAN NOT NULL DEFAULT FALSE;
