"""SQLite local database for SquadX client."""

import sqlite3
import json
import logging
from datetime import datetime
from typing import Optional, Any
from dataclasses import dataclass, asdict
from pathlib import Path
from contextlib import contextmanager

from ..config import settings

logger = logging.getLogger(__name__)


@dataclass
class ExecutionRecord:
    """Record of a task execution."""

    id: Optional[int] = None
    task_id: int = 0
    execution_id: Optional[int] = None
    agent_type: str = ""
    status: str = "pending"
    container_id: Optional[str] = None
    started_at: Optional[str] = None
    completed_at: Optional[str] = None
    output: Optional[str] = None
    error: Optional[str] = None
    tokens_used: int = 0
    cost: float = 0.0
    metadata: Optional[str] = None  # JSON string


@dataclass
class MetricsRecord:
    """Record of execution metrics."""

    id: Optional[int] = None
    execution_id: int = 0
    timestamp: str = ""
    metric_type: str = ""  # tokens, cost, latency, etc.
    value: float = 0.0
    metadata: Optional[str] = None


class LocalDatabase:
    """SQLite database for local persistence."""

    def __init__(self, db_path: Optional[str] = None):
        self.db_path = db_path or settings.db_path
        self._ensure_dir()
        self._init_schema()

    def _ensure_dir(self):
        """Ensure database directory exists."""
        Path(self.db_path).parent.mkdir(parents=True, exist_ok=True)

    @contextmanager
    def _get_connection(self):
        """Get a database connection."""
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        try:
            yield conn
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()

    def _init_schema(self):
        """Initialize database schema."""
        with self._get_connection() as conn:
            cursor = conn.cursor()

            # Executions table
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS executions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    task_id INTEGER NOT NULL,
                    execution_id INTEGER,
                    agent_type TEXT NOT NULL,
                    status TEXT DEFAULT 'pending',
                    container_id TEXT,
                    started_at TEXT,
                    completed_at TEXT,
                    output TEXT,
                    error TEXT,
                    tokens_used INTEGER DEFAULT 0,
                    cost REAL DEFAULT 0.0,
                    metadata TEXT,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
            """)

            # Metrics table
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS metrics (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    execution_id INTEGER NOT NULL,
                    timestamp TEXT NOT NULL,
                    metric_type TEXT NOT NULL,
                    value REAL NOT NULL,
                    metadata TEXT,
                    FOREIGN KEY (execution_id) REFERENCES executions(id)
                )
            """)

            # Cache table for offline mode
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS cache (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL,
                    expires_at TEXT,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
            """)

            # Pending actions queue
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS pending_actions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    action_type TEXT NOT NULL,
                    payload TEXT NOT NULL,
                    priority INTEGER DEFAULT 0,
                    retry_count INTEGER DEFAULT 0,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
            """)

            # Create indexes
            cursor.execute("""
                CREATE INDEX IF NOT EXISTS idx_executions_task_id
                ON executions(task_id)
            """)
            cursor.execute("""
                CREATE INDEX IF NOT EXISTS idx_executions_status
                ON executions(status)
            """)
            cursor.execute("""
                CREATE INDEX IF NOT EXISTS idx_metrics_execution_id
                ON metrics(execution_id)
            """)

            logger.info(f"Database initialized at {self.db_path}")

    # Execution CRUD
    def create_execution(self, record: ExecutionRecord) -> int:
        """Create an execution record."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO executions (
                    task_id, execution_id, agent_type, status, container_id,
                    started_at, completed_at, output, error, tokens_used, cost, metadata
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                record.task_id,
                record.execution_id,
                record.agent_type,
                record.status,
                record.container_id,
                record.started_at,
                record.completed_at,
                record.output,
                record.error,
                record.tokens_used,
                record.cost,
                record.metadata,
            ))
            return cursor.lastrowid

    def update_execution(self, id: int, **kwargs) -> bool:
        """Update an execution record."""
        if not kwargs:
            return False

        fields = ", ".join(f"{k} = ?" for k in kwargs.keys())
        values = list(kwargs.values()) + [id]

        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(f"""
                UPDATE executions SET {fields} WHERE id = ?
            """, values)
            return cursor.rowcount > 0

    def get_execution(self, id: int) -> Optional[ExecutionRecord]:
        """Get an execution record by ID."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM executions WHERE id = ?", (id,))
            row = cursor.fetchone()
            if row:
                return ExecutionRecord(**dict(row))
            return None

    def get_executions_by_task(self, task_id: int) -> list[ExecutionRecord]:
        """Get all executions for a task."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "SELECT * FROM executions WHERE task_id = ? ORDER BY created_at DESC",
                (task_id,)
            )
            return [ExecutionRecord(**dict(row)) for row in cursor.fetchall()]

    def get_pending_executions(self) -> list[ExecutionRecord]:
        """Get all pending executions."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "SELECT * FROM executions WHERE status IN ('pending', 'running')"
            )
            return [ExecutionRecord(**dict(row)) for row in cursor.fetchall()]

    # Metrics CRUD
    def add_metric(self, record: MetricsRecord) -> int:
        """Add a metrics record."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO metrics (execution_id, timestamp, metric_type, value, metadata)
                VALUES (?, ?, ?, ?, ?)
            """, (
                record.execution_id,
                record.timestamp,
                record.metric_type,
                record.value,
                record.metadata,
            ))
            return cursor.lastrowid

    def get_metrics_by_execution(self, execution_id: int) -> list[MetricsRecord]:
        """Get all metrics for an execution."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "SELECT * FROM metrics WHERE execution_id = ? ORDER BY timestamp",
                (execution_id,)
            )
            return [MetricsRecord(**dict(row)) for row in cursor.fetchall()]

    def get_aggregated_metrics(
        self,
        metric_type: str,
        start_date: Optional[str] = None,
        end_date: Optional[str] = None,
    ) -> dict[str, float]:
        """Get aggregated metrics."""
        with self._get_connection() as conn:
            cursor = conn.cursor()

            query = """
                SELECT
                    SUM(value) as total,
                    AVG(value) as average,
                    MIN(value) as minimum,
                    MAX(value) as maximum,
                    COUNT(*) as count
                FROM metrics
                WHERE metric_type = ?
            """
            params = [metric_type]

            if start_date:
                query += " AND timestamp >= ?"
                params.append(start_date)
            if end_date:
                query += " AND timestamp <= ?"
                params.append(end_date)

            cursor.execute(query, params)
            row = cursor.fetchone()

            return {
                "total": row["total"] or 0,
                "average": row["average"] or 0,
                "minimum": row["minimum"] or 0,
                "maximum": row["maximum"] or 0,
                "count": row["count"] or 0,
            }

    # Cache operations
    def cache_set(self, key: str, value: Any, expires_at: Optional[str] = None):
        """Set a cache value."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT OR REPLACE INTO cache (key, value, expires_at)
                VALUES (?, ?, ?)
            """, (key, json.dumps(value), expires_at))

    def cache_get(self, key: str) -> Optional[Any]:
        """Get a cache value."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                SELECT value, expires_at FROM cache WHERE key = ?
            """, (key,))
            row = cursor.fetchone()

            if not row:
                return None

            # Check expiration
            if row["expires_at"]:
                if datetime.fromisoformat(row["expires_at"]) < datetime.now():
                    self.cache_delete(key)
                    return None

            return json.loads(row["value"])

    def cache_delete(self, key: str):
        """Delete a cache value."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM cache WHERE key = ?", (key,))

    def cache_clear(self):
        """Clear all cache."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM cache")

    # Pending actions queue
    def queue_action(self, action_type: str, payload: dict, priority: int = 0) -> int:
        """Queue an action for later execution."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO pending_actions (action_type, payload, priority)
                VALUES (?, ?, ?)
            """, (action_type, json.dumps(payload), priority))
            return cursor.lastrowid

    def get_pending_actions(self, limit: int = 10) -> list[dict]:
        """Get pending actions ordered by priority."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                SELECT * FROM pending_actions
                ORDER BY priority DESC, created_at ASC
                LIMIT ?
            """, (limit,))
            return [
                {
                    "id": row["id"],
                    "action_type": row["action_type"],
                    "payload": json.loads(row["payload"]),
                    "priority": row["priority"],
                    "retry_count": row["retry_count"],
                }
                for row in cursor.fetchall()
            ]

    def complete_action(self, id: int):
        """Mark an action as complete (remove from queue)."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM pending_actions WHERE id = ?", (id,))

    def retry_action(self, id: int):
        """Increment retry count for an action."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                UPDATE pending_actions
                SET retry_count = retry_count + 1
                WHERE id = ?
            """, (id,))

    # Statistics
    def get_statistics(self) -> dict:
        """Get overall statistics."""
        with self._get_connection() as conn:
            cursor = conn.cursor()

            # Execution stats
            cursor.execute("""
                SELECT
                    COUNT(*) as total,
                    SUM(CASE WHEN status = 'completed' THEN 1 ELSE 0 END) as completed,
                    SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) as failed,
                    SUM(CASE WHEN status = 'running' THEN 1 ELSE 0 END) as running,
                    SUM(tokens_used) as total_tokens,
                    SUM(cost) as total_cost
                FROM executions
            """)
            exec_stats = dict(cursor.fetchone())

            return {
                "executions": exec_stats,
                "database_path": self.db_path,
            }


# Global instance
local_db = LocalDatabase()
