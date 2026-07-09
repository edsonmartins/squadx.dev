"""Tests for the Context Packet assembly (ADR-0007, RFC-0005)."""

from squadx_client.orchestrator.context_packet import ContextPacket, build_context_packet


def test_returns_none_for_empty_context():
    assert build_context_packet(None) is None
    assert build_context_packet({}) is None


def test_builds_packet_from_context_dict():
    ctx = {
        "main_task": {"title": "Add login", "description": "Implement OAuth login"},
        "acceptance_criteria": ["users can log in", "tokens expire"],
        "reuse_map": "use auth/service.py",
        "completed_subtasks": [{"title": "scaffold", "result": "done"}],
        "exclusions": ["no SSO"],
        "budget_tokens": 5000,
    }

    packet = build_context_packet(ctx)

    assert packet.summary == "Implement OAuth login"
    assert "users can log in" in packet.intent
    assert any("scaffold" in f.text for f in packet.facts)
    assert any("auth/service.py" in m for m in packet.must_preserve)
    assert packet.exclusions == ["no SSO"]
    assert packet.budget_tokens == 5000
    # A primary source for the root task and a supporting source for prior work.
    assert any(s.role == "primary" for s in packet.sources)
    assert any(s.role == "supporting" for s in packet.sources)


def test_render_includes_sections():
    ctx = {
        "main_task": {"title": "T", "description": "summary text"},
        "acceptance_criteria": ["AC1"],
        "exclusions": ["X"],
    }
    rendered = build_context_packet(ctx).render()
    assert "## Context summary" in rendered
    assert "## Intent" in rendered
    assert "## Out of scope" in rendered


def test_empty_packet_renders_empty_string():
    assert ContextPacket().render() == ""


def test_handles_object_style_subtasks():
    class FakeSubtask:
        title = "build api"
        result = "shipped"

    packet = build_context_packet({"completed_subtasks": [FakeSubtask()]})
    assert any("build api: shipped" in f.text for f in packet.facts)
