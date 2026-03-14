"""Tests for squadx_client.metrics.collector module."""

import time
from unittest.mock import MagicMock, patch

import pytest

from squadx_client.metrics.collector import (
    MetricsCollector,
    ExecutionMetrics,
    MetricType,
    MODEL_COSTS,
)


@pytest.fixture
def collector():
    """Create a fresh MetricsCollector."""
    return MetricsCollector()


class TestExecutionMetricsModel:
    """Test the ExecutionMetrics dataclass."""

    def test_calculate_cost_known_model(self):
        m = ExecutionMetrics(
            execution_id=1, task_id=1, agent_type="backend",
            model="gpt-4o",
            tokens_input=1000, tokens_output=500,
        )
        cost = m.calculate_cost()
        expected = (1000 / 1000) * 0.005 + (500 / 1000) * 0.015
        assert cost == pytest.approx(expected)
        assert m.cost == pytest.approx(expected)

    def test_calculate_cost_unknown_model_uses_default(self):
        m = ExecutionMetrics(
            execution_id=1, task_id=1, agent_type="backend",
            model="unknown-model-xyz",
            tokens_input=1000, tokens_output=500,
        )
        cost = m.calculate_cost()
        default_costs = MODEL_COSTS["default"]
        expected = (1000 / 1000) * default_costs["input"] + (500 / 1000) * default_costs["output"]
        assert cost == pytest.approx(expected)

    def test_avg_latency_empty(self):
        m = ExecutionMetrics(execution_id=1, task_id=1, agent_type="backend")
        assert m.avg_latency_ms() == 0.0

    def test_avg_latency_with_values(self):
        m = ExecutionMetrics(
            execution_id=1, task_id=1, agent_type="backend",
            latencies_ms=[100.0, 200.0, 300.0],
        )
        assert m.avg_latency_ms() == pytest.approx(200.0)


class TestStartAndEndExecution:
    """Test execution lifecycle tracking."""

    def test_start_execution(self, collector):
        metrics = collector.start_execution(
            execution_id=1, task_id=10, agent_type="backend", model="gpt-4o"
        )
        assert metrics.execution_id == 1
        assert metrics.task_id == 10
        assert metrics.agent_type == "backend"
        assert metrics.start_time is not None

    @patch("squadx_client.metrics.collector.local_db")
    def test_end_execution(self, mock_db, collector):
        collector.start_execution(execution_id=1, task_id=10, agent_type="backend")
        collector.add_tokens(1, input_tokens=100, output_tokens=50)

        result = collector.end_execution(1)

        assert result is not None
        assert result.tokens_input == 100
        assert result.tokens_output == 50
        assert result.tokens_total == 150
        assert result.execution_time_s > 0
        assert result.cost > 0

    def test_end_nonexistent_execution(self, collector):
        result = collector.end_execution(999)
        assert result is None


class TestTokensAndCounters:
    """Test token and counter tracking."""

    def test_add_tokens(self, collector):
        collector.start_execution(execution_id=1, task_id=10, agent_type="backend")
        collector.add_tokens(1, input_tokens=500, output_tokens=200)
        collector.add_tokens(1, input_tokens=300, output_tokens=100)

        metrics = collector.get_metrics(1)
        assert metrics.tokens_input == 800
        assert metrics.tokens_output == 300
        assert metrics.tokens_total == 1100

    def test_add_tokens_nonexistent_execution(self, collector):
        # Should not raise
        collector.add_tokens(999, input_tokens=100)

    def test_increment_api_calls(self, collector):
        collector.start_execution(execution_id=1, task_id=10, agent_type="backend")
        collector.increment_api_calls(1, count=3)
        collector.increment_api_calls(1)

        metrics = collector.get_metrics(1)
        assert metrics.api_calls == 4

    def test_increment_errors(self, collector):
        collector.start_execution(execution_id=1, task_id=10, agent_type="backend")
        collector.increment_errors(1)
        collector.increment_errors(1)

        metrics = collector.get_metrics(1)
        assert metrics.errors == 2

    def test_add_latency(self, collector):
        collector.start_execution(execution_id=1, task_id=10, agent_type="backend")
        collector.add_latency(1, 150.0)
        collector.add_latency(1, 250.0)

        metrics = collector.get_metrics(1)
        assert len(metrics.latencies_ms) == 2
        assert metrics.avg_latency_ms() == pytest.approx(200.0)


class TestCallbacks:
    """Test metrics callbacks."""

    @patch("squadx_client.metrics.collector.local_db")
    def test_callback_called_on_end(self, mock_db, collector):
        callback = MagicMock()
        collector.register_callback(callback)

        collector.start_execution(execution_id=1, task_id=10, agent_type="backend")
        collector.end_execution(1)

        callback.assert_called_once()
        call_arg = callback.call_args[0][0]
        assert isinstance(call_arg, ExecutionMetrics)
        assert call_arg.execution_id == 1

    @patch("squadx_client.metrics.collector.local_db")
    def test_callback_error_does_not_propagate(self, mock_db, collector):
        bad_callback = MagicMock(side_effect=ValueError("callback broke"))
        collector.register_callback(bad_callback)

        collector.start_execution(execution_id=1, task_id=10, agent_type="backend")
        # Should not raise
        collector.end_execution(1)


class TestGetSummary:
    """Test summary and cost breakdown."""

    @patch("squadx_client.metrics.collector.local_db")
    def test_get_summary_calls_db(self, mock_db, collector):
        mock_db.get_aggregated_metrics.return_value = {
            "total": 100, "average": 50, "count": 2
        }

        summary = collector.get_summary()

        assert MetricType.TOKENS_INPUT.value in summary
        assert MetricType.COST.value in summary

    @patch("squadx_client.metrics.collector.local_db")
    def test_get_cost_breakdown(self, mock_db, collector):
        mock_db.get_aggregated_metrics.return_value = {
            "total": 0.50, "average": 0.25, "count": 2
        }

        breakdown = collector.get_cost_breakdown()

        assert "total_cost" in breakdown
        assert "total_tokens" in breakdown
        assert "execution_count" in breakdown
