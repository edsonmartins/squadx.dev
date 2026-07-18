"""Metrics collector for SquadX client."""

import logging
import time
from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from threading import Lock
from typing import Any

from ..storage.local_db import MetricsRecord, local_db

logger = logging.getLogger(__name__)


class MetricType(str, Enum):
    """Types of metrics collected."""

    TOKENS_INPUT = "tokens_input"
    TOKENS_OUTPUT = "tokens_output"
    TOKENS_TOTAL = "tokens_total"
    COST = "cost"
    LATENCY_MS = "latency_ms"
    EXECUTION_TIME_S = "execution_time_s"
    API_CALLS = "api_calls"
    ERRORS = "errors"


# Cost per 1K tokens for different models (approximate)
MODEL_COSTS = {
    # OpenAI
    "gpt-4o": {"input": 0.005, "output": 0.015},
    "gpt-4o-mini": {"input": 0.00015, "output": 0.0006},
    "gpt-4-turbo": {"input": 0.01, "output": 0.03},
    "gpt-4": {"input": 0.03, "output": 0.06},
    "gpt-3.5-turbo": {"input": 0.0005, "output": 0.0015},
    # Anthropic
    "claude-3-5-sonnet-20241022": {"input": 0.003, "output": 0.015},
    "claude-3-5-haiku-20241022": {"input": 0.001, "output": 0.005},
    "claude-opus-4-5-20251101": {"input": 0.015, "output": 0.075},
    "claude-3-opus": {"input": 0.015, "output": 0.075},
    "claude-3-sonnet": {"input": 0.003, "output": 0.015},
    "claude-3-haiku": {"input": 0.00025, "output": 0.00125},
    # Default fallback
    "default": {"input": 0.01, "output": 0.03},
}


@dataclass
class ExecutionMetrics:
    """Metrics for a single execution."""

    execution_id: int
    task_id: int
    agent_type: str
    model: str = "default"

    tokens_input: int = 0
    tokens_output: int = 0
    tokens_total: int = 0
    cost: float = 0.0

    api_calls: int = 0
    errors: int = 0

    start_time: float | None = None
    end_time: float | None = None
    execution_time_s: float = 0.0

    latencies_ms: list[float] = field(default_factory=list)

    def calculate_cost(self) -> float:
        """Calculate cost based on token usage and model."""
        costs = MODEL_COSTS.get(self.model, MODEL_COSTS["default"])
        input_cost = (self.tokens_input / 1000) * costs["input"]
        output_cost = (self.tokens_output / 1000) * costs["output"]
        self.cost = input_cost + output_cost
        return self.cost

    def avg_latency_ms(self) -> float:
        """Get average latency."""
        if not self.latencies_ms:
            return 0.0
        return sum(self.latencies_ms) / len(self.latencies_ms)


class MetricsCollector:
    """Collector for execution metrics."""

    def __init__(self):
        self._executions: dict[int, ExecutionMetrics] = {}
        self._lock = Lock()
        self._callbacks: list[Callable[[ExecutionMetrics], Any]] = []

    def register_callback(self, callback: Callable[[ExecutionMetrics], Any]):
        """Register a callback for metrics updates."""
        self._callbacks.append(callback)

    def _notify_callbacks(self, metrics: ExecutionMetrics):
        """Notify all registered callbacks."""
        for callback in self._callbacks:
            try:
                callback(metrics)
            except Exception as e:
                logger.error(f"Metrics callback error: {e}")

    def start_execution(
        self,
        execution_id: int,
        task_id: int,
        agent_type: str,
        model: str = "default",
    ) -> ExecutionMetrics:
        """Start tracking an execution."""
        with self._lock:
            metrics = ExecutionMetrics(
                execution_id=execution_id,
                task_id=task_id,
                agent_type=agent_type,
                model=model,
                start_time=time.time(),
            )
            self._executions[execution_id] = metrics
            logger.debug(f"Started metrics for execution {execution_id}")
            return metrics

    def end_execution(self, execution_id: int) -> ExecutionMetrics | None:
        """End tracking an execution."""
        with self._lock:
            metrics = self._executions.get(execution_id)
            if not metrics:
                return None

            metrics.end_time = time.time()
            if metrics.start_time:
                metrics.execution_time_s = metrics.end_time - metrics.start_time

            metrics.calculate_cost()

            # Store in local database
            self._store_metrics(metrics)

            # Notify callbacks
            self._notify_callbacks(metrics)

            logger.info(
                f"Execution {execution_id} completed: "
                f"tokens={metrics.tokens_total}, "
                f"cost=${metrics.cost:.4f}, "
                f"time={metrics.execution_time_s:.2f}s"
            )

            return metrics

    def add_tokens(
        self,
        execution_id: int,
        input_tokens: int = 0,
        output_tokens: int = 0,
    ):
        """Add token usage."""
        with self._lock:
            metrics = self._executions.get(execution_id)
            if metrics:
                metrics.tokens_input += input_tokens
                metrics.tokens_output += output_tokens
                metrics.tokens_total = metrics.tokens_input + metrics.tokens_output

    def add_latency(self, execution_id: int, latency_ms: float):
        """Add a latency measurement."""
        with self._lock:
            metrics = self._executions.get(execution_id)
            if metrics:
                metrics.latencies_ms.append(latency_ms)

    def increment_api_calls(self, execution_id: int, count: int = 1):
        """Increment API call count."""
        with self._lock:
            metrics = self._executions.get(execution_id)
            if metrics:
                metrics.api_calls += count

    def increment_errors(self, execution_id: int, count: int = 1):
        """Increment error count."""
        with self._lock:
            metrics = self._executions.get(execution_id)
            if metrics:
                metrics.errors += count

    def get_metrics(self, execution_id: int) -> ExecutionMetrics | None:
        """Get metrics for an execution."""
        return self._executions.get(execution_id)

    def _store_metrics(self, metrics: ExecutionMetrics):
        """Store metrics in local database."""
        timestamp = datetime.now().isoformat()

        # Store individual metrics
        metric_records = [
            (MetricType.TOKENS_INPUT, metrics.tokens_input),
            (MetricType.TOKENS_OUTPUT, metrics.tokens_output),
            (MetricType.TOKENS_TOTAL, metrics.tokens_total),
            (MetricType.COST, metrics.cost),
            (MetricType.EXECUTION_TIME_S, metrics.execution_time_s),
            (MetricType.API_CALLS, metrics.api_calls),
            (MetricType.ERRORS, metrics.errors),
        ]

        for metric_type, value in metric_records:
            try:
                local_db.add_metric(MetricsRecord(
                    execution_id=metrics.execution_id,
                    timestamp=timestamp,
                    metric_type=metric_type.value,
                    value=float(value),
                ))
            except Exception as e:
                logger.error(f"Failed to store metric {metric_type}: {e}")

        # Store average latency if available
        if metrics.latencies_ms:
            try:
                local_db.add_metric(MetricsRecord(
                    execution_id=metrics.execution_id,
                    timestamp=timestamp,
                    metric_type=MetricType.LATENCY_MS.value,
                    value=metrics.avg_latency_ms(),
                ))
            except Exception as e:
                logger.error(f"Failed to store latency metric: {e}")

    def get_summary(
        self,
        start_date: str | None = None,
        end_date: str | None = None,
    ) -> dict:
        """Get aggregated metrics summary."""
        summary = {}

        for metric_type in MetricType:
            summary[metric_type.value] = local_db.get_aggregated_metrics(
                metric_type.value,
                start_date=start_date,
                end_date=end_date,
            )

        return summary

    def get_cost_breakdown(
        self,
        start_date: str | None = None,
        end_date: str | None = None,
    ) -> dict:
        """Get cost breakdown."""
        cost_metrics = local_db.get_aggregated_metrics(
            MetricType.COST.value,
            start_date=start_date,
            end_date=end_date,
        )

        token_metrics = local_db.get_aggregated_metrics(
            MetricType.TOKENS_TOTAL.value,
            start_date=start_date,
            end_date=end_date,
        )

        return {
            "total_cost": cost_metrics["total"],
            "average_cost_per_execution": cost_metrics["average"],
            "total_tokens": token_metrics["total"],
            "average_tokens_per_execution": token_metrics["average"],
            "execution_count": cost_metrics["count"],
        }


# Decorator for tracking LLM calls
def track_llm_call(execution_id: int):
    """Decorator to track LLM API calls."""
    def decorator(func):
        async def wrapper(*args, **kwargs):
            start_time = time.time()
            metrics_collector.increment_api_calls(execution_id)

            try:
                result = await func(*args, **kwargs)

                # Track latency
                latency_ms = (time.time() - start_time) * 1000
                metrics_collector.add_latency(execution_id, latency_ms)

                # Try to extract token usage from result
                if hasattr(result, "usage"):
                    usage = result.usage
                    metrics_collector.add_tokens(
                        execution_id,
                        input_tokens=getattr(usage, "prompt_tokens", 0),
                        output_tokens=getattr(usage, "completion_tokens", 0),
                    )

                return result

            except Exception:
                metrics_collector.increment_errors(execution_id)
                raise

        return wrapper
    return decorator


# Global instance
metrics_collector = MetricsCollector()
