"""LangGraph orchestrator for multi-agent task execution."""

from typing import Literal

import structlog
from langgraph.graph import StateGraph, END

from squadx_client.orchestrator.state import OrchestratorState
from squadx_client.orchestrator.nodes import (
    analyze_task,
    create_plan,
    execute_subtask,
    review_results,
    arbiter,
    escalate,
    commit_changes,
    handle_error,
)

logger = structlog.get_logger()


def route_after_arbiter(state: OrchestratorState) -> Literal["execute", "commit", "escalate"]:
    """Route on the arbiter's verdict: re-work, ship, or hand back to a human."""
    _get = (lambda s, k, d=None: s.get(k, d)) if isinstance(state, dict) else (lambda s, k, d=None: getattr(s, k, d))
    verdict = _get(state, "review_verdict")
    if verdict == "continue":
        return "execute"
    if verdict == "escalate":
        return "escalate"
    return "commit"


def should_continue(state: OrchestratorState) -> Literal["execute", "review", "error", "end"]:
    """Determine the next step based on current state.

    Note: LangGraph may pass the state as either a Pydantic model or a dict
    depending on the version and graph configuration. We use getattr/get
    defensively to handle both cases.
    """
    # Support both attribute access (Pydantic) and dict access (TypedDict)
    _get = (lambda s, k, d=None: s.get(k, d)) if isinstance(state, dict) else (lambda s, k, d=None: getattr(s, k, d))

    if _get(state, "error"):
        return "error"

    if _get(state, "should_end", False):
        return "end"

    plan = _get(state, "plan")
    if not plan:
        return "error"

    # Check if all subtasks are completed
    subtasks = plan.subtasks if hasattr(plan, "subtasks") else plan.get("subtasks", [])
    all_subtask_ids = [st.id if hasattr(st, "id") else st["id"] for st in subtasks]
    completed = _get(state, "completed_subtasks", [])
    failed = _get(state, "failed_subtasks", [])
    pending_subtasks = [
        st_id
        for st_id in all_subtask_ids
        if st_id not in completed and st_id not in failed
    ]

    if not pending_subtasks:
        return "review"

    return "execute"


def create_orchestrator() -> StateGraph:
    """Create the LangGraph orchestrator for task execution."""
    # Create the graph
    graph = StateGraph(OrchestratorState)

    # Add nodes
    graph.add_node("analyze", analyze_task)
    graph.add_node("plan", create_plan)
    graph.add_node("execute", execute_subtask)
    graph.add_node("review", review_results)
    graph.add_node("arbiter", arbiter)
    graph.add_node("escalate", escalate)
    graph.add_node("commit", commit_changes)
    graph.add_node("error", handle_error)

    # Set entry point
    graph.set_entry_point("analyze")

    # Add edges
    graph.add_edge("analyze", "plan")
    graph.add_conditional_edges(
        "plan",
        should_continue,
        {
            "execute": "execute",
            "review": "review",
            "error": "error",
            "end": END,
        },
    )
    graph.add_conditional_edges(
        "execute",
        should_continue,
        {
            "execute": "execute",
            "review": "review",
            "error": "error",
            "end": END,
        },
    )
    # Review produces findings; the arbiter is the authority on when the loop stops.
    graph.add_edge("review", "arbiter")
    graph.add_conditional_edges(
        "arbiter",
        route_after_arbiter,
        {
            "execute": "execute",   # CONTINUE — re-work the injected fix subtask
            "commit": "commit",     # APPROVE — ship it
            "escalate": "escalate", # ESCALATE — hand back to a human
        },
    )
    graph.add_edge("escalate", END)
    graph.add_edge("commit", END)
    graph.add_edge("error", END)

    # Compile and return
    return graph.compile()


# Diagram of the graph:
#
#                    ┌─────────────┐
#                    │   analyze   │  (sets complexity)
#                    └──────┬──────┘
#                           │
#                    ┌──────▼──────┐
#                    │    plan     │
#                    └──────┬──────┘
#                           │
#              ┌────────────▼────────────┐
#              │         execute         │◄────────────┐ CONTINUE
#              └────────────┬────────────┘             │ (fix subtask)
#                           │ (no pending)             │
#                    ┌──────▼──────┐                   │
#                    │   review    │ (findings + risk) │
#                    └──────┬──────┘                   │
#                           │                          │
#                    ┌──────▼──────┐  the loop-breaker │
#                    │   arbiter   │───────────────────┘
#                    └──┬───────┬──┘
#               APPROVE │       │ ESCALATE
#                 ┌─────▼──┐ ┌──▼────────┐
#                 │ commit │ │ escalate  │ (report_blocker → human)
#                 └────┬───┘ └─────┬─────┘
#                      └─────┬─────┘
#                     ┌──────▼──────┐
#                     │     END     │
#                     └─────────────┘
#
# The arbiter — not the reviewer — decides when the loop stops. cycle_count is a hard backstop
# (max_cycles); complexity scales rigor. A risky task never APPROVEs with an open blocker; a
# trivial one escalates rather than looping.
