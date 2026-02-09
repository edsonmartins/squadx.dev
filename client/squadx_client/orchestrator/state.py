"""State definitions for the LangGraph orchestrator."""

from typing import Any, Literal

from langchain_core.messages import BaseMessage
from pydantic import BaseModel, Field


class SubTask(BaseModel):
    """Represents a subtask assigned to a specialist agent."""

    id: str
    title: str
    description: str
    agent_type: Literal["frontend", "backend", "fullstack", "devops", "qa"]
    status: Literal["pending", "in_progress", "completed", "failed"] = "pending"
    result: str | None = None
    files_modified: list[str] = Field(default_factory=list)
    error: str | None = None


class TaskPlan(BaseModel):
    """Task execution plan created by the coordinator."""

    analysis: str
    approach: str
    subtasks: list[SubTask]
    execution_order: list[str]  # List of subtask IDs in execution order
    parallel_groups: list[list[str]] = Field(default_factory=list)  # Groups that can run in parallel


class ExecutionMetrics(BaseModel):
    """Metrics collected during task execution."""

    input_tokens: int = 0
    output_tokens: int = 0
    total_cost: float = 0.0
    execution_time_seconds: float = 0.0
    files_created: int = 0
    files_modified: int = 0
    lines_added: int = 0
    lines_removed: int = 0


class OrchestratorState(BaseModel):
    """State for the LangGraph orchestrator."""

    # Input
    task_id: int
    task: dict[str, Any]

    # Conversation
    messages: list[BaseMessage] = Field(default_factory=list)

    # Planning
    plan: TaskPlan | None = None

    # Execution
    current_subtask_id: str | None = None
    completed_subtasks: list[str] = Field(default_factory=list)
    failed_subtasks: list[str] = Field(default_factory=list)

    # Results
    final_result: str | None = None
    git_branch: str | None = None
    git_commit: str | None = None

    # Metrics
    metrics: ExecutionMetrics = Field(default_factory=ExecutionMetrics)

    # Control
    should_end: bool = False
    error: str | None = None

    class Config:
        arbitrary_types_allowed = True
