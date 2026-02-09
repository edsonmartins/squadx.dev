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
    commit_changes,
    handle_error,
)

logger = structlog.get_logger()


def should_continue(state: OrchestratorState) -> Literal["execute", "review", "error", "end"]:
    """Determine the next step based on current state."""
    if state.error:
        return "error"

    if state.should_end:
        return "end"

    if not state.plan:
        return "error"

    # Check if all subtasks are completed
    all_subtask_ids = [st.id for st in state.plan.subtasks]
    pending_subtasks = [
        st_id
        for st_id in all_subtask_ids
        if st_id not in state.completed_subtasks and st_id not in state.failed_subtasks
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
    graph.add_edge("review", "commit")
    graph.add_edge("commit", END)
    graph.add_edge("error", END)

    # Compile and return
    return graph.compile()


# Diagram of the graph:
#
#                    ┌─────────────┐
#                    │   analyze   │
#                    └──────┬──────┘
#                           │
#                    ┌──────▼──────┐
#                    │    plan     │
#                    └──────┬──────┘
#                           │
#           ┌───────────────┼───────────────┐
#           │               │               │
#    ┌──────▼─────┐  ┌──────▼─────┐  ┌──────▼─────┐
#    │  execute   │  │   review   │  │   error    │
#    └──────┬─────┘  └──────┬─────┘  └──────┬─────┘
#           │               │               │
#           └───────────────┼───────────────┘
#                           │
#                    ┌──────▼──────┐
#                    │   commit    │
#                    └──────┬──────┘
#                           │
#                    ┌──────▼──────┐
#                    │     END     │
#                    └─────────────┘
