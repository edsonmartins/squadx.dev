"""Tests for container metrics collection module."""

from unittest.mock import MagicMock

import pytest

from squadx_client.docker.metrics import ContainerMetrics, ContainerMetricsCollector


# Sample Docker stats JSON matching the real API format
SAMPLE_STATS = {
    "cpu_stats": {
        "cpu_usage": {"total_usage": 500_000_000},
        "system_cpu_usage": 10_000_000_000,
        "online_cpus": 4,
    },
    "precpu_stats": {
        "cpu_usage": {"total_usage": 400_000_000},
        "system_cpu_usage": 9_000_000_000,
    },
    "memory_stats": {
        "usage": 256 * 1024 * 1024,  # 256 MB
        "limit": 1024 * 1024 * 1024,  # 1 GB
    },
    "networks": {
        "eth0": {"rx_bytes": 1000, "tx_bytes": 2000},
        "eth1": {"rx_bytes": 500, "tx_bytes": 300},
    },
    "pids_stats": {
        "current": 15,
        "limit": 100,
    },
    "blkio_stats": {
        "io_service_bytes_recursive": [
            {"op": "read", "value": 4096},
            {"op": "write", "value": 8192},
            {"op": "read", "value": 1024},
        ],
    },
}


class TestContainerMetrics:
    """Test ContainerMetrics dataclass and properties."""

    def test_defaults(self):
        m = ContainerMetrics()
        assert m.cpu_percent == 0.0
        assert m.memory_used_mb == 0.0
        assert m.network_rx_bytes == 0
        assert m.pids_current == 0

    def test_memory_available_mb(self):
        m = ContainerMetrics(memory_used_mb=200.0, memory_limit_mb=512.0)
        assert m.memory_available_mb == 312.0

    def test_memory_available_mb_clamped_to_zero(self):
        m = ContainerMetrics(memory_used_mb=600.0, memory_limit_mb=512.0)
        assert m.memory_available_mb == 0.0

    def test_to_dict_structure(self):
        m = ContainerMetrics(
            cpu_percent=25.123,
            memory_used_mb=128.0,
            memory_limit_mb=512.0,
            memory_percent=25.0,
            network_rx_bytes=1000,
            network_tx_bytes=2000,
            pids_current=10,
            pids_limit=50,
            block_read_bytes=4096,
            block_write_bytes=8192,
        )
        d = m.to_dict()
        assert d["cpu"]["percent"] == 25.12
        assert d["memory"]["usedMb"] == 128.0
        assert d["memory"]["limitMb"] == 512.0
        assert d["memory"]["availableMb"] == 384.0
        assert d["network"]["rxBytes"] == 1000
        assert d["network"]["txBytes"] == 2000
        assert d["pids"]["current"] == 10
        assert d["pids"]["limit"] == 50
        assert d["blockIo"]["readBytes"] == 4096
        assert d["blockIo"]["writeBytes"] == 8192


class TestContainerMetricsCollector:
    """Test ContainerMetricsCollector._parse_stats and collect."""

    @pytest.fixture
    def collector(self):
        client = MagicMock()
        return ContainerMetricsCollector(client)

    def test_cpu_calculation(self, collector):
        """CPU% = (cpu_delta / system_delta) * num_cpus * 100."""
        metrics = collector._parse_stats("c1", SAMPLE_STATS)
        # cpu_delta = 100_000_000, system_delta = 1_000_000_000, cpus = 4
        # expected = (100M / 1000M) * 4 * 100 = 40.0
        assert abs(metrics.cpu_percent - 40.0) < 0.01

    def test_memory_calculation(self, collector):
        metrics = collector._parse_stats("c1", SAMPLE_STATS)
        assert abs(metrics.memory_used_mb - 256.0) < 0.1
        assert abs(metrics.memory_limit_mb - 1024.0) < 0.1
        assert abs(metrics.memory_percent - 25.0) < 0.1

    def test_network_aggregation(self, collector):
        """Network bytes are summed across all interfaces."""
        metrics = collector._parse_stats("c1", SAMPLE_STATS)
        assert metrics.network_rx_bytes == 1500  # 1000 + 500
        assert metrics.network_tx_bytes == 2300  # 2000 + 300

    def test_pids_parsing(self, collector):
        metrics = collector._parse_stats("c1", SAMPLE_STATS)
        assert metrics.pids_current == 15
        assert metrics.pids_limit == 100

    def test_block_io_parsing(self, collector):
        """Block I/O aggregates multiple read/write entries."""
        metrics = collector._parse_stats("c1", SAMPLE_STATS)
        assert metrics.block_read_bytes == 5120  # 4096 + 1024
        assert metrics.block_write_bytes == 8192

    def test_cpu_zero_when_system_delta_zero(self, collector):
        """No division by zero when system_delta is 0."""
        stats = {
            "cpu_stats": {"cpu_usage": {"total_usage": 100}, "system_cpu_usage": 500, "online_cpus": 1},
            "precpu_stats": {"cpu_usage": {"total_usage": 100}, "system_cpu_usage": 500},
        }
        metrics = collector._parse_stats("c1", stats)
        assert metrics.cpu_percent == 0.0

    def test_handles_missing_networks(self, collector):
        stats = {
            "cpu_stats": {"cpu_usage": {"total_usage": 0}, "system_cpu_usage": 0, "online_cpus": 1},
            "precpu_stats": {"cpu_usage": {"total_usage": 0}, "system_cpu_usage": 0},
            "memory_stats": {"usage": 0, "limit": 0},
        }
        metrics = collector._parse_stats("c1", stats)
        assert metrics.network_rx_bytes == 0
        assert metrics.network_tx_bytes == 0

    def test_handles_null_blkio(self, collector):
        """blkio_stats.io_service_bytes_recursive can be None on some platforms."""
        stats = {
            "cpu_stats": {"cpu_usage": {"total_usage": 0}, "system_cpu_usage": 0, "online_cpus": 1},
            "precpu_stats": {"cpu_usage": {"total_usage": 0}, "system_cpu_usage": 0},
            "memory_stats": {"usage": 0, "limit": 0},
            "blkio_stats": {"io_service_bytes_recursive": None},
        }
        metrics = collector._parse_stats("c1", stats)
        assert metrics.block_read_bytes == 0
        assert metrics.block_write_bytes == 0

    def test_collect_with_mocked_container(self):
        """collect() calls container.stats(stream=False) and returns parsed metrics."""
        mock_client = MagicMock()
        mock_container = MagicMock()
        mock_container.stats.return_value = SAMPLE_STATS
        mock_client.containers.get.return_value = mock_container

        collector = ContainerMetricsCollector(mock_client)
        metrics = collector.collect("container-abc")

        mock_client.containers.get.assert_called_once_with("container-abc")
        mock_container.stats.assert_called_once_with(stream=False)
        assert metrics is not None
        assert metrics.cpu_percent > 0
        assert metrics.memory_used_mb > 0

    def test_collect_returns_none_on_error(self):
        mock_client = MagicMock()
        mock_client.containers.get.side_effect = Exception("container not found")
        collector = ContainerMetricsCollector(mock_client)
        assert collector.collect("missing") is None
