"""Memory collector that batches execution artifacts for BrainSentry storage."""

import structlog
from dataclasses import dataclass, field

from squadx_client.memory.client import BrainSentryClient

logger = structlog.get_logger()


@dataclass
class MemoryEntry:
    """A pending memory to be stored in BrainSentry."""
    content: str
    category: str = "KNOWLEDGE"
    importance: str = "MINOR"
    memory_type: str = "semantic"
    tags: list[str] = field(default_factory=list)
    metadata: dict = field(default_factory=dict)


class MemoryCollector:
    """Collects execution artifacts during orchestration for batch storage.

    Agents record decisions, patterns, errors, and learnings during
    execution. At the end of execution, all are flushed to BrainSentry.
    """

    MAX_ENTRIES = 1000

    def __init__(self, client: BrainSentryClient):
        self.client = client
        self._entries: list[MemoryEntry] = []

    def record_decision(self, description: str, tags: list[str] | None = None) -> None:
        """Record a decision made during execution."""
        if len(self._entries) >= self.MAX_ENTRIES:
            logger.warning("memory_collector_full", max=self.MAX_ENTRIES)
            return
        self._entries.append(MemoryEntry(
            content=description,
            category="DECISION",
            importance="IMPORTANT",
            memory_type="episodic",
            tags=tags or ["execution", "decision"],
        ))

    def record_pattern(self, description: str, tags: list[str] | None = None) -> None:
        """Record a code pattern discovered during execution."""
        if len(self._entries) >= self.MAX_ENTRIES:
            logger.warning("memory_collector_full", max=self.MAX_ENTRIES)
            return
        self._entries.append(MemoryEntry(
            content=description,
            category="PATTERN",
            importance="IMPORTANT",
            memory_type="procedural",
            tags=tags or ["execution", "pattern"],
        ))

    def record_bug(self, description: str, tags: list[str] | None = None) -> None:
        """Record a bug found during execution."""
        if len(self._entries) >= self.MAX_ENTRIES:
            logger.warning("memory_collector_full", max=self.MAX_ENTRIES)
            return
        self._entries.append(MemoryEntry(
            content=description,
            category="BUG",
            importance="CRITICAL",
            memory_type="episodic",
            tags=tags or ["execution", "bug"],
        ))

    def record_antipattern(self, description: str, tags: list[str] | None = None) -> None:
        """Record an antipattern to avoid in future executions."""
        if len(self._entries) >= self.MAX_ENTRIES:
            logger.warning("memory_collector_full", max=self.MAX_ENTRIES)
            return
        self._entries.append(MemoryEntry(
            content=description,
            category="ANTIPATTERN",
            importance="IMPORTANT",
            memory_type="procedural",
            tags=tags or ["execution", "antipattern"],
        ))

    def record_learning(self, description: str, tags: list[str] | None = None) -> None:
        """Record a general learning from execution."""
        if len(self._entries) >= self.MAX_ENTRIES:
            logger.warning("memory_collector_full", max=self.MAX_ENTRIES)
            return
        self._entries.append(MemoryEntry(
            content=description,
            category="INSIGHT",
            importance="MINOR",
            memory_type="semantic",
            tags=tags or ["execution", "learning"],
        ))

    async def flush(self) -> int:
        """Flush all collected memories to BrainSentry.

        Returns the number of successfully stored memories.
        """
        if not self._entries:
            return 0
        if not self.client.enabled:
            logger.warning("brainsentry_disabled_entries_pending", pending=len(self._entries))
            return 0

        stored = 0
        for entry in self._entries:
            result = await self.client.create_memory(
                content=entry.content,
                category=entry.category,
                importance=entry.importance,
                memory_type=entry.memory_type,
                tags=entry.tags,
                metadata=entry.metadata,
            )
            if result:
                stored += 1

        total = len(self._entries)
        self._entries.clear()
        logger.info("memories_flushed", stored=stored, total=total)
        return stored

    @property
    def pending_count(self) -> int:
        return len(self._entries)
