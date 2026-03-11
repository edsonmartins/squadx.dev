"""Tests for LocalDatabase storage."""

import pytest
from datetime import datetime, timedelta
from unittest.mock import patch

from squadx_client.storage.local_db import LocalDatabase, ExecutionRecord, MetricsRecord


@pytest.fixture
def db(tmp_path):
    db_path = str(tmp_path / "test.db")
    with patch("squadx_client.storage.local_db.settings") as mock_settings:
        mock_settings.db_path = db_path
        database = LocalDatabase(db_path=db_path)
    return database


class TestDatabaseInit:
    def test_creates_database_file(self, tmp_path):
        db_path = str(tmp_path / "subdir" / "test.db")
        with patch("squadx_client.storage.local_db.settings") as mock_settings:
            mock_settings.db_path = db_path
            db = LocalDatabase(db_path=db_path)
        assert db.db_path == db_path

    def test_schema_tables_exist(self, db):
        import sqlite3
        conn = sqlite3.connect(db.db_path)
        cursor = conn.cursor()
        cursor.execute("SELECT name FROM sqlite_master WHERE type='table'")
        tables = {row[0] for row in cursor.fetchall()}
        conn.close()
        assert "executions" in tables
        assert "metrics" in tables
        assert "cache" in tables
        assert "pending_actions" in tables


class TestExecutionCRUD:
    def test_create_and_get_execution(self, db):
        record = ExecutionRecord(
            task_id=1,
            agent_type="backend",
            status="running",
            tokens_used=100,
            cost=0.01,
        )
        exec_id = db.create_execution(record)
        assert exec_id is not None

        retrieved = db.get_execution(exec_id)
        assert retrieved is not None
        assert retrieved.task_id == 1
        assert retrieved.agent_type == "backend"
        assert retrieved.status == "running"

    def test_get_nonexistent_execution(self, db):
        assert db.get_execution(9999) is None

    def test_update_execution(self, db):
        record = ExecutionRecord(task_id=2, agent_type="frontend", status="pending")
        exec_id = db.create_execution(record)

        updated = db.update_execution(exec_id, status="completed", output="All done")
        assert updated is True

        retrieved = db.get_execution(exec_id)
        assert retrieved.status == "completed"
        assert retrieved.output == "All done"

    def test_update_with_no_kwargs_returns_false(self, db):
        assert db.update_execution(1) is False

    def test_get_executions_by_task(self, db):
        for i in range(3):
            db.create_execution(ExecutionRecord(task_id=10, agent_type=f"agent_{i}"))
        db.create_execution(ExecutionRecord(task_id=20, agent_type="other"))

        results = db.get_executions_by_task(10)
        assert len(results) == 3
        assert all(r.task_id == 10 for r in results)

    def test_get_pending_executions(self, db):
        db.create_execution(ExecutionRecord(task_id=1, agent_type="a", status="pending"))
        db.create_execution(ExecutionRecord(task_id=2, agent_type="b", status="running"))
        db.create_execution(ExecutionRecord(task_id=3, agent_type="c", status="completed"))

        pending = db.get_pending_executions()
        assert len(pending) == 2
        statuses = {r.status for r in pending}
        assert statuses == {"pending", "running"}


class TestCacheOperations:
    def test_set_and_get_cache(self, db):
        db.cache_set("key1", {"data": "value"})
        result = db.cache_get("key1")
        assert result == {"data": "value"}

    def test_get_missing_key_returns_none(self, db):
        assert db.cache_get("nonexistent") is None

    def test_cache_delete(self, db):
        db.cache_set("temp", 42)
        db.cache_delete("temp")
        assert db.cache_get("temp") is None

    def test_cache_clear(self, db):
        db.cache_set("a", 1)
        db.cache_set("b", 2)
        db.cache_clear()
        assert db.cache_get("a") is None
        assert db.cache_get("b") is None

    def test_expired_cache_returns_none(self, db):
        past = (datetime.now() - timedelta(hours=1)).isoformat()
        db.cache_set("expired_key", "old_value", expires_at=past)
        assert db.cache_get("expired_key") is None


class TestPendingActions:
    def test_queue_and_get_actions(self, db):
        action_id = db.queue_action("deploy", {"env": "prod"}, priority=5)
        assert action_id is not None

        actions = db.get_pending_actions()
        assert len(actions) == 1
        assert actions[0]["action_type"] == "deploy"
        assert actions[0]["payload"] == {"env": "prod"}

    def test_complete_action_removes_it(self, db):
        action_id = db.queue_action("test", {"step": 1})
        db.complete_action(action_id)
        assert db.get_pending_actions() == []

    def test_priority_ordering(self, db):
        db.queue_action("low", {}, priority=1)
        db.queue_action("high", {}, priority=10)
        db.queue_action("mid", {}, priority=5)

        actions = db.get_pending_actions()
        priorities = [a["priority"] for a in actions]
        assert priorities == sorted(priorities, reverse=True)


class TestMetrics:
    def test_add_and_get_metrics(self, db):
        exec_id = db.create_execution(ExecutionRecord(task_id=1, agent_type="test"))
        metric = MetricsRecord(
            execution_id=exec_id,
            timestamp=datetime.now().isoformat(),
            metric_type="tokens",
            value=500.0,
        )
        db.add_metric(metric)

        metrics = db.get_metrics_by_execution(exec_id)
        assert len(metrics) == 1
        assert metrics[0].metric_type == "tokens"
        assert metrics[0].value == 500.0

    def test_aggregated_metrics(self, db):
        exec_id = db.create_execution(ExecutionRecord(task_id=1, agent_type="test"))
        now = datetime.now().isoformat()
        for val in [10.0, 20.0, 30.0]:
            db.add_metric(MetricsRecord(
                execution_id=exec_id,
                timestamp=now,
                metric_type="cost",
                value=val,
            ))

        agg = db.get_aggregated_metrics("cost")
        assert agg["total"] == 60.0
        assert agg["average"] == 20.0
        assert agg["minimum"] == 10.0
        assert agg["maximum"] == 30.0
        assert agg["count"] == 3
