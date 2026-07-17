"""
Real-time container metrics collection.
Collects CPU, memory, network I/O from Docker stats API.
Inspired by OpenSandbox's /metrics endpoint.
"""
import asyncio
import logging
import time
from collections.abc import AsyncIterator
from dataclasses import dataclass, field

logger = logging.getLogger(__name__)


@dataclass
class ContainerMetrics:
    """Point-in-time container resource metrics."""
    timestamp: float = field(default_factory=time.time)
    cpu_percent: float = 0.0
    memory_used_mb: float = 0.0
    memory_limit_mb: float = 0.0
    memory_percent: float = 0.0
    network_rx_bytes: int = 0
    network_tx_bytes: int = 0
    pids_current: int = 0
    pids_limit: int = 0
    block_read_bytes: int = 0
    block_write_bytes: int = 0

    @property
    def memory_available_mb(self) -> float:
        return max(0, self.memory_limit_mb - self.memory_used_mb)

    def to_dict(self) -> dict:
        return {
            "timestamp": self.timestamp,
            "cpu": {"percent": round(self.cpu_percent, 2)},
            "memory": {
                "usedMb": round(self.memory_used_mb, 1),
                "limitMb": round(self.memory_limit_mb, 1),
                "percent": round(self.memory_percent, 1),
                "availableMb": round(self.memory_available_mb, 1),
            },
            "network": {
                "rxBytes": self.network_rx_bytes,
                "txBytes": self.network_tx_bytes,
            },
            "pids": {
                "current": self.pids_current,
                "limit": self.pids_limit,
            },
            "blockIo": {
                "readBytes": self.block_read_bytes,
                "writeBytes": self.block_write_bytes,
            },
        }


class ContainerMetricsCollector:
    """Collects real-time metrics from Docker containers using the stats API."""

    def __init__(self, docker_client):
        self._client = docker_client
        self._previous_cpu: dict[str, dict] = {}

    def collect(self, container_id: str) -> ContainerMetrics | None:
        """Collect a single metrics snapshot for a container."""
        try:
            container = self._client.containers.get(container_id)
            stats = container.stats(stream=False)
            return self._parse_stats(container_id, stats)
        except Exception as e:
            logger.error(f"Failed to collect metrics for {container_id}: {e}")
            return None

    async def stream(self, container_id: str, interval: float = 1.0) -> AsyncIterator[ContainerMetrics]:
        """Stream metrics at the given interval."""
        while True:
            metrics = await asyncio.get_event_loop().run_in_executor(
                None, self.collect, container_id
            )
            if metrics is None:
                break
            yield metrics
            await asyncio.sleep(interval)

    def _parse_stats(self, container_id: str, stats: dict) -> ContainerMetrics:
        """Parse Docker stats JSON into ContainerMetrics."""
        metrics = ContainerMetrics()

        # CPU
        cpu_stats = stats.get("cpu_stats", {})
        precpu_stats = stats.get("precpu_stats", {})
        cpu_delta = (
            cpu_stats.get("cpu_usage", {}).get("total_usage", 0) -
            precpu_stats.get("cpu_usage", {}).get("total_usage", 0)
        )
        system_delta = (
            cpu_stats.get("system_cpu_usage", 0) -
            precpu_stats.get("system_cpu_usage", 0)
        )
        num_cpus = cpu_stats.get("online_cpus", 1)
        if system_delta > 0 and cpu_delta >= 0:
            metrics.cpu_percent = (cpu_delta / system_delta) * num_cpus * 100.0

        # Memory
        mem_stats = stats.get("memory_stats", {})
        metrics.memory_used_mb = mem_stats.get("usage", 0) / (1024 * 1024)
        metrics.memory_limit_mb = mem_stats.get("limit", 0) / (1024 * 1024)
        if metrics.memory_limit_mb > 0:
            metrics.memory_percent = (metrics.memory_used_mb / metrics.memory_limit_mb) * 100

        # Network
        networks = stats.get("networks", {})
        for iface_stats in networks.values():
            metrics.network_rx_bytes += iface_stats.get("rx_bytes", 0)
            metrics.network_tx_bytes += iface_stats.get("tx_bytes", 0)

        # PIDs
        pids_stats = stats.get("pids_stats", {})
        metrics.pids_current = pids_stats.get("current", 0)
        metrics.pids_limit = pids_stats.get("limit", 0)

        # Block I/O
        blkio = stats.get("blkio_stats", {}).get("io_service_bytes_recursive", []) or []
        for entry in blkio:
            op = entry.get("op", "").lower()
            if op == "read":
                metrics.block_read_bytes += entry.get("value", 0)
            elif op == "write":
                metrics.block_write_bytes += entry.get("value", 0)

        return metrics
