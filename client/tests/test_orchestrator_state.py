"""Tests for orchestrator state models."""

import pytest

from squadx_client.orchestrator.state import (
    SubTask,
    TaskPlan,
    AgentMetrics,
    ExecutionMetrics,
    OrchestratorState,
)


class TestSubTask:
    def test_default_values(self):
        st = SubTask(id="1", title="Test", description="Desc", agent_type="backend")
        assert st.status == "pending"
        assert st.result is None
        assert st.files_modified == []
        assert st.error is None
        assert st.live_session_code is None

    def test_creation_with_all_fields(self):
        st = SubTask(
            id="sub-1",
            title="Build API",
            description="Create REST endpoints",
            agent_type="backend",
            status="completed",
            result="Endpoints created",
            files_modified=["api.py", "routes.py"],
            error=None,
            live_session_code="ABC123",
        )
        assert st.id == "sub-1"
        assert st.agent_type == "backend"
        assert st.status == "completed"
        assert len(st.files_modified) == 2

    def test_serialization_roundtrip(self):
        st = SubTask(id="1", title="T", description="D", agent_type="qa")
        data = st.model_dump()
        restored = SubTask(**data)
        assert restored == st

    def test_invalid_agent_type_rejected(self):
        with pytest.raises(Exception):
            SubTask(id="1", title="T", description="D", agent_type="invalid_type")

    def test_invalid_status_rejected(self):
        with pytest.raises(Exception):
            SubTask(
                id="1", title="T", description="D",
                agent_type="backend", status="unknown"
            )


class TestTaskPlan:
    def test_creation(self):
        subtask = SubTask(id="s1", title="T", description="D", agent_type="frontend")
        plan = TaskPlan(
            analysis="Analyze the task",
            approach="Step by step",
            subtasks=[subtask],
            execution_order=["s1"],
        )
        assert len(plan.subtasks) == 1
        assert plan.parallel_groups == []

    def test_parallel_groups_default_empty(self):
        plan = TaskPlan(
            analysis="A", approach="B", subtasks=[], execution_order=[]
        )
        assert plan.parallel_groups == []

    def test_multiple_subtasks_with_parallel_groups(self):
        s1 = SubTask(id="s1", title="Frontend", description="UI", agent_type="frontend")
        s2 = SubTask(id="s2", title="Backend", description="API", agent_type="backend")
        plan = TaskPlan(
            analysis="Full stack app",
            approach="Parallel frontend and backend",
            subtasks=[s1, s2],
            execution_order=["s1", "s2"],
            parallel_groups=[["s1", "s2"]],
        )
        assert len(plan.subtasks) == 2
        assert plan.parallel_groups == [["s1", "s2"]]

    def test_serialization_roundtrip(self):
        plan = TaskPlan(
            analysis="A", approach="B",
            subtasks=[SubTask(id="1", title="T", description="D", agent_type="devops")],
            execution_order=["1"],
        )
        data = plan.model_dump()
        restored = TaskPlan(**data)
        assert restored == plan


class TestExecutionMetrics:
    def test_defaults(self):
        m = ExecutionMetrics()
        assert m.input_tokens == 0
        assert m.output_tokens == 0
        assert m.total_cost == 0.0
        assert m.agent_metrics == []
        assert m.successful_subtasks == 0
        assert m.failed_subtasks == 0

    def test_add_successful_agent_metrics(self):
        m = ExecutionMetrics()
        am = AgentMetrics(
            agent_type="backend",
            subtask_id="s1",
            subtask_title="Build API",
            input_tokens=100,
            output_tokens=200,
            cost=0.05,
            execution_time_seconds=10.0,
            tool_calls=3,
            files_modified=["a.py", "b.py"],
            success=True,
        )
        m.add_agent_metrics(am)

        assert m.input_tokens == 100
        assert m.output_tokens == 200
        assert m.total_cost == 0.05
        assert m.total_tool_calls == 3
        assert m.files_modified == 2
        assert m.successful_subtasks == 1
        assert m.failed_subtasks == 0
        assert len(m.agent_metrics) == 1

    def test_add_failed_agent_metrics(self):
        m = ExecutionMetrics()
        am = AgentMetrics(
            agent_type="qa",
            subtask_id="s2",
            subtask_title="Run tests",
            success=False,
            error="Tests failed",
        )
        m.add_agent_metrics(am)
        assert m.failed_subtasks == 1
        assert m.successful_subtasks == 0

    def test_to_summary_structure(self):
        m = ExecutionMetrics()
        am = AgentMetrics(
            agent_type="frontend",
            subtask_id="s1",
            subtask_title="Build UI",
            input_tokens=50,
            output_tokens=100,
            cost=0.02,
            execution_time_seconds=5.5,
        )
        m.add_agent_metrics(am)
        summary = m.to_summary()

        assert summary["tokens"]["input"] == 50
        assert summary["tokens"]["output"] == 100
        assert summary["tokens"]["total"] == 150
        assert summary["cost_usd"] == 0.02
        assert summary["subtasks"]["total"] == 1
        assert len(summary["agents"]) == 1
        assert summary["agents"][0]["type"] == "frontend"

    def test_accumulates_multiple_agents(self):
        m = ExecutionMetrics()
        for i in range(3):
            am = AgentMetrics(
                agent_type="backend",
                subtask_id=f"s{i}",
                subtask_title=f"Task {i}",
                input_tokens=100,
                output_tokens=50,
                cost=0.01,
                tool_calls=2,
            )
            m.add_agent_metrics(am)

        assert m.input_tokens == 300
        assert m.output_tokens == 150
        assert m.total_cost == pytest.approx(0.03)
        assert m.total_tool_calls == 6
        assert m.successful_subtasks == 3


class TestOrchestratorState:
    def test_minimal_creation(self):
        state = OrchestratorState(task_id=1, task={"title": "Test"})
        assert state.task_id == 1
        assert state.plan is None
        assert state.current_subtask_id is None
        assert state.completed_subtasks == []
        assert state.failed_subtasks == []
        assert state.final_result is None
        assert state.should_end is False
        assert state.error is None

    def test_messages_default_empty(self):
        state = OrchestratorState(task_id=1, task={})
        assert state.messages == []

    def test_metrics_default_instance(self):
        state = OrchestratorState(task_id=1, task={})
        assert isinstance(state.metrics, ExecutionMetrics)
        assert state.metrics.input_tokens == 0

    def test_state_mutation(self):
        state = OrchestratorState(task_id=1, task={"title": "App"})
        state.current_subtask_id = "s1"
        state.completed_subtasks.append("s1")
        state.current_subtask_id = "s2"
        state.failed_subtasks.append("s2")
        state.should_end = True
        state.error = "Subtask s2 failed"

        assert len(state.completed_subtasks) == 1
        assert len(state.failed_subtasks) == 1
        assert state.should_end is True

    def test_live_session_codes_default_empty(self):
        state = OrchestratorState(task_id=1, task={})
        assert state.live_session_codes == []

    def test_git_fields_default_none(self):
        state = OrchestratorState(task_id=1, task={})
        assert state.git_branch is None
        assert state.git_commit is None

    def test_plan_assignment(self):
        state = OrchestratorState(task_id=1, task={"title": "Test"})
        subtask = SubTask(id="s1", title="T", description="D", agent_type="backend")
        plan = TaskPlan(
            analysis="A", approach="B",
            subtasks=[subtask],
            execution_order=["s1"],
        )
        state.plan = plan
        assert state.plan is not None
        assert len(state.plan.subtasks) == 1


class TestAgentMetrics:
    def test_defaults(self):
        am = AgentMetrics(
            agent_type="backend", subtask_id="s1", subtask_title="Task"
        )
        assert am.input_tokens == 0
        assert am.output_tokens == 0
        assert am.cost == 0.0
        assert am.success is True
        assert am.error is None

    def test_failed_metrics(self):
        am = AgentMetrics(
            agent_type="qa", subtask_id="s1", subtask_title="Test",
            success=False, error="Timeout",
        )
        assert am.success is False
        assert am.error == "Timeout"
