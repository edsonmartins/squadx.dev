"""Tests for Attention Budget metadata defaults (RFC-0005 §1)."""

from squadx_client.messaging.run_event import (
    default_run_event_metadata,
    for_level,
    for_severity,
)


def test_known_event_type_wins_over_level():
    md = default_run_event_metadata(event_type="run.completed", level="DEBUG")
    assert md.visibility == "human"
    assert md.importance == "high"


def test_falls_back_to_level_when_type_unknown():
    md = default_run_event_metadata(event_type="something.unknown", level="INFO")
    # INFO is internal/diagnostic by default → audit channel
    assert md.visibility == "audit"
    assert md.importance == "normal"


def test_explicit_values_win():
    md = default_run_event_metadata(
        event_type="tool.log", level="DEBUG", visibility="human", importance="blocking"
    )
    assert md == ("human", "blocking")


def test_unknown_level_is_not_hidden():
    md = for_level("VERBOSE")
    assert md.visibility == "human"
    assert md.importance == "normal"


def test_error_level_is_human():
    assert for_level("ERROR") == ("human", "high")


def test_severity_mapping():
    assert for_severity("blocker") == ("human", "blocking")
    assert for_severity("major") == ("audit", "high")
    assert for_severity("nit") == ("audit", "low")
