"""State definitions for the LangGraph orchestrator."""

from typing import Any, Literal

from langchain_core.messages import BaseMessage
from pydantic import BaseModel, ConfigDict, Field


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
    live_session_code: str | None = None  # Live streaming session code
    acceptance_criteria: list[str] = Field(default_factory=list)  # Testable ACs the work must satisfy
    depends_on: list[str] = Field(default_factory=list)  # IDs of subtasks that must finish first
    is_fix: bool = False  # True when injected by the arbiter to address review findings


class TaskPlan(BaseModel):
    """Task execution plan created by the coordinator."""

    analysis: str
    approach: str
    subtasks: list[SubTask]
    execution_order: list[str]  # List of subtask IDs in execution order
    parallel_groups: list[list[str]] = Field(default_factory=list)  # Groups that can run in parallel
    reuse: str = ""  # Key existing files/patterns the coder should build on rather than reinvent


class AgentMetrics(BaseModel):
    """Metrics for a single agent execution."""

    agent_type: str
    subtask_id: str
    subtask_title: str
    input_tokens: int = 0
    output_tokens: int = 0
    cost: float = 0.0
    execution_time_seconds: float = 0.0
    tool_calls: int = 0
    files_modified: list[str] = Field(default_factory=list)
    success: bool = True
    error: str | None = None


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

    # Per-agent breakdown
    agent_metrics: list[AgentMetrics] = Field(default_factory=list)

    # Aggregations
    total_tool_calls: int = 0
    successful_subtasks: int = 0
    failed_subtasks: int = 0

    def add_agent_metrics(self, metrics: AgentMetrics):
        """Add metrics from a single agent execution."""
        self.agent_metrics.append(metrics)
        self.input_tokens += metrics.input_tokens
        self.output_tokens += metrics.output_tokens
        self.total_cost += metrics.cost
        self.execution_time_seconds += metrics.execution_time_seconds
        self.total_tool_calls += metrics.tool_calls
        self.files_modified += len(metrics.files_modified)

        if metrics.success:
            self.successful_subtasks += 1
        else:
            self.failed_subtasks += 1

    def to_summary(self) -> dict:
        """Generate a summary of execution metrics."""
        return {
            "tokens": {
                "input": self.input_tokens,
                "output": self.output_tokens,
                "total": self.input_tokens + self.output_tokens,
            },
            "cost_usd": round(self.total_cost, 4),
            "execution_time_seconds": round(self.execution_time_seconds, 2),
            "tool_calls": self.total_tool_calls,
            "files_modified": self.files_modified,
            "subtasks": {
                "successful": self.successful_subtasks,
                "failed": self.failed_subtasks,
                "total": self.successful_subtasks + self.failed_subtasks,
            },
            "agents": [
                {
                    "type": m.agent_type,
                    "subtask": m.subtask_title,
                    "tokens": m.input_tokens + m.output_tokens,
                    "cost": round(m.cost, 4),
                    "time_seconds": round(m.execution_time_seconds, 2),
                    "success": m.success,
                }
                for m in self.agent_metrics
            ],
        }


class OrchestratorState(BaseModel):
    """State for the LangGraph orchestrator."""

    # Input
    task_id: int
    task: dict[str, Any]
    execution_id: int | None = None
    brainsentry_session_id: str | None = None

    # Conversation
    messages: list[BaseMessage] = Field(default_factory=list)

    # Planning
    plan: TaskPlan | None = None

    # Execution
    current_subtask_id: str | None = None
    completed_subtasks: list[str] = Field(default_factory=list)
    failed_subtasks: list[str] = Field(default_factory=list)
    # Per-subtask git worktree branches (subtask_id -> branch name). Populated by
    # execute_subtask when SQUADX_USE_WORKTREES=true and the workspace is a git
    # repo. Consumed by commit_changes to merge each specialist's branch back
    # into the integration branch before the final commit.
    subtask_worktrees: dict[str, str] = Field(default_factory=dict)

    # Results
    final_result: str | None = None
    git_branch: str | None = None
    git_commit: str | None = None

    # Live Streaming
    live_session_codes: list[str] = Field(default_factory=list)  # Active live sessions

    # Metrics
    metrics: ExecutionMetrics = Field(default_factory=ExecutionMetrics)

    # Review / arbitration loop (the loop-breaker — see arbiter node)
    complexity: Literal["trivial", "standard", "risky"] = "standard"
    cycle_count: int = 0  # review→arbiter rounds completed
    max_cycles: int = 3  # hard backstop so the team never loops forever
    review_findings: list[dict[str, Any]] = Field(default_factory=list)  # latest reviewer pass
    review_verdict: Literal["continue", "approve", "escalate"] | None = None  # arbiter's decision
    escalation_reason: str | None = None  # why the arbiter handed back to a human
    cost_budget_usd: float | None = None  # hard cost ceiling; over it the arbiter escalates (None = no cap)

    # Control
    should_end: bool = False
    error: str | None = None

    model_config = ConfigDict(arbitrary_types_allowed=True)
