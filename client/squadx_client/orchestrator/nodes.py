"""Node implementations for the LangGraph orchestrator."""

import json
import os
import tempfile
import time
import uuid
from typing import Any

import structlog
from langchain_core.messages import HumanMessage, SystemMessage

from squadx_client.config import settings
from squadx_client.llm.router import get_llm
from squadx_client.memory import BrainSentryClient, PromptInterceptor
from squadx_client.memory.policy import MemoryScopeContext
from squadx_client.orchestrator.state import OrchestratorState, TaskPlan, SubTask, ExecutionMetrics, AgentMetrics
from squadx_client.agents.factory import create_agent
from squadx_client.docker.sandbox import AgentSandbox
from squadx_client.git.manager import GitManager

logger = structlog.get_logger()


async def _intercept_prompt(prompt: str, state: OrchestratorState) -> str:
    """Enrich a prompt with BrainSentry context when a session is available."""
    if not state.brainsentry_session_id:
        return prompt

    client = BrainSentryClient()
    interceptor = PromptInterceptor(client)
    interceptor.set_session(state.brainsentry_session_id)
    interceptor.set_user(_resolve_memory_user_id(state))
    interceptor.set_scope(_resolve_memory_scope(state))
    try:
        return await interceptor.intercept(prompt)
    finally:
        await client.close()


async def _record_prompt_interaction(
    state: OrchestratorState,
    prompt: str,
    response: str,
    stage: str,
) -> None:
    """Push an orchestrator-level interaction into BrainSentry session cache."""
    if not state.brainsentry_session_id:
        return

    client = BrainSentryClient()
    scope = _resolve_memory_scope(state)
    try:
        await client.push_session_interaction(
            state.brainsentry_session_id,
            query=_truncate_for_memory(prompt, 4000),
            response=_truncate_for_memory(response, 4000),
            metadata={
                "stage": stage,
                "taskId": str(state.task_id),
                "executionId": str(state.execution_id or state.task_id),
                **scope.to_metadata(),
            },
        )
    finally:
        await client.close()


def _truncate_for_memory(text: str | None, limit: int) -> str:
    if not text:
        return ""
    if len(text) <= limit:
        return text
    return text[: limit - 3] + "..."


def _resolve_memory_user_id(state: OrchestratorState) -> str | None:
    task = state.task or {}
    agent_id = task.get("assigned_agent_id") or task.get("agent_id")
    if agent_id:
        return str(agent_id)

    if state.execution_id:
        return f"execution-{state.execution_id}"

    return None


def _resolve_memory_scope(state: OrchestratorState) -> MemoryScopeContext:
    task = state.task or {}
    return MemoryScopeContext(
        organization_id=_as_scope_value(task.get("organization_id") or task.get("organizationId")),
        project_id=_as_scope_value(task.get("project_id") or task.get("projectId")),
        task_id=_as_scope_value(task.get("id") or state.task_id),
        agent_id=_resolve_memory_user_id(state),
        execution_id=_as_scope_value(state.execution_id),
    )


def _as_scope_value(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


async def analyze_task(state: OrchestratorState) -> dict[str, Any]:
    """Analyze the task and understand requirements."""
    logger.info("analyzing_task", task_id=state.task_id)

    task = state.task
    llm = get_llm()

    system_prompt = """You are a senior software architect analyzing a development task.

Your job is to:
1. Understand the task requirements
2. Identify the technical approach
3. Break down into subtasks for specialist agents

Available agent types:
- frontend: React, Next.js, Vue, CSS, UI/UX
- backend: Python, Java, Node.js, APIs, databases
- fullstack: Full-stack tasks that span multiple areas
- devops: Docker, CI/CD, infrastructure, deployment
- qa: Testing, test automation, quality assurance

Respond in JSON format:
{
    "analysis": "Detailed analysis of the task",
    "approach": "Technical approach to solve it",
    "complexity": "low|medium|high",
    "estimated_time": "in minutes"
}"""

    task_prompt = f"Task: {task.get('title')}\n\nDescription: {task.get('description', 'No description')}"
    enriched_prompt = await _intercept_prompt(task_prompt, state)

    response = await llm.ainvoke(
        [
            SystemMessage(content=system_prompt),
            HumanMessage(content=enriched_prompt),
        ]
    )

    await _record_prompt_interaction(state, task_prompt, response.content, "analyze_task")

    # Parse the analysis
    try:
        analysis = json.loads(response.content)
    except json.JSONDecodeError:
        analysis = {"analysis": response.content, "approach": "General approach", "complexity": "medium"}

    logger.info("task_analyzed", analysis=analysis)

    return {
        "messages": state.messages + [response],
    }


async def create_plan(state: OrchestratorState) -> dict[str, Any]:
    """Create an execution plan with subtasks."""
    logger.info("creating_plan", task_id=state.task_id)

    task = state.task
    llm = get_llm()

    system_prompt = """You are a project manager creating an execution plan.

Based on the task analysis, create a detailed plan with subtasks for specialist agents.

Each subtask should specify:
- id: unique identifier (use UUIDs)
- title: brief title
- description: detailed description of what to do
- agent_type: frontend|backend|fullstack|devops|qa

Respond in JSON format:
{
    "subtasks": [
        {
            "id": "uuid",
            "title": "Subtask title",
            "description": "What the agent should do",
            "agent_type": "backend"
        }
    ],
    "execution_order": ["uuid1", "uuid2"],
    "parallel_groups": [["uuid1", "uuid2"], ["uuid3"]]
}"""

    plan_prompt = f"""Task: {task.get('title')}

Description: {task.get('description', 'No description')}

Previous analysis: {state.messages[-1].content if state.messages else 'None'}

Create an execution plan."""
    enriched_prompt = await _intercept_prompt(plan_prompt, state)

    response = await llm.ainvoke(
        [
            SystemMessage(content=system_prompt),
            HumanMessage(content=enriched_prompt),
        ]
    )

    await _record_prompt_interaction(state, plan_prompt, response.content, "create_plan")

    try:
        plan_data = json.loads(response.content)
        subtasks = [
            SubTask(
                id=st.get("id", str(uuid.uuid4())),
                title=st["title"],
                description=st["description"],
                agent_type=st["agent_type"],
            )
            for st in plan_data.get("subtasks", [])
        ]

        plan = TaskPlan(
            analysis=state.messages[-1].content if state.messages else "",
            approach="Multi-agent execution",
            subtasks=subtasks,
            execution_order=plan_data.get("execution_order", [st.id for st in subtasks]),
            parallel_groups=plan_data.get("parallel_groups", []),
        )
    except (json.JSONDecodeError, KeyError) as e:
        logger.error("plan_creation_failed", error=str(e))
        # Create a simple single-task plan
        subtask_id = str(uuid.uuid4())
        plan = TaskPlan(
            analysis="Direct execution",
            approach="Single agent",
            subtasks=[
                SubTask(
                    id=subtask_id,
                    title=task.get("title", "Execute task"),
                    description=task.get("description", ""),
                    agent_type="fullstack",
                )
            ],
            execution_order=[subtask_id],
        )

    logger.info("plan_created", subtask_count=len(plan.subtasks))

    return {
        "plan": plan,
        "messages": state.messages + [response],
    }


async def execute_subtask(state: OrchestratorState) -> dict[str, Any]:
    """Execute the next pending subtask."""
    if not state.plan:
        return {"error": "No plan available"}

    # Find next subtask to execute
    for subtask_id in state.plan.execution_order:
        if subtask_id not in state.completed_subtasks and subtask_id not in state.failed_subtasks:
            break
    else:
        return {"should_end": True}

    subtask = next((st for st in state.plan.subtasks if st.id == subtask_id), None)
    if not subtask:
        return {"error": f"Subtask {subtask_id} not found"}

    logger.info(
        "executing_subtask",
        subtask_id=subtask_id,
        title=subtask.title,
        agent_type=subtask.agent_type,
    )

    sandbox = None
    start_time = time.time()

    try:
        # Get workspace path from settings or use temp directory
        workspace_path = getattr(settings, "workspace_path", None)
        if not workspace_path:
            workspace_path = os.path.join(tempfile.gettempdir(), f"squadx-task-{state.task_id}")
            os.makedirs(workspace_path, exist_ok=True)

        # Check if sandbox execution is enabled
        use_sandbox = getattr(settings, "enable_sandbox", True)

        if use_sandbox:
            # Create sandbox for this subtask
            sandbox = AgentSandbox(
                task_id=state.task_id,
                agent_type=subtask.agent_type,
                workspace_path=workspace_path,
            )

            # Start the sandbox
            sandbox_started = await sandbox.start(
                image=getattr(settings, "agent_image", "squadx/agent:latest"),
                memory_limit=getattr(settings, "agent_memory_limit", "2g"),
                cpu_limit=getattr(settings, "agent_cpu_limit", 2.0),
                enable_vnc=getattr(settings, "enable_vnc", True),
            )

            if not sandbox_started:
                logger.warning("sandbox_start_failed", subtask_id=subtask_id)
                sandbox = None  # Fall back to simple execution

        # Create the appropriate agent with sandbox (if available)
        agent = create_agent(
            subtask.agent_type,
            sandbox=sandbox,
            brainsentry_session_id=state.brainsentry_session_id,
        )

        # Execute the subtask
        result = await agent.execute(
            task_title=subtask.title,
            task_description=subtask.description,
            context={
                "main_task": state.task,
                "execution_id": state.execution_id or state.task_id,
                "completed_subtasks": [
                    st for st in state.plan.subtasks if st.id in state.completed_subtasks
                ],
            },
        )

        execution_time = time.time() - start_time

        # Update subtask status
        subtask.status = "completed"
        subtask.result = result.get("output", "")
        subtask.files_modified = result.get("files_modified", [])

        logger.info("subtask_completed", subtask_id=subtask_id, execution_time=execution_time)

        # Capture live session code from sandbox
        live_session_code = None
        if sandbox and sandbox.live_join_code:
            live_session_code = sandbox.live_join_code
            subtask.live_session_code = live_session_code
            logger.info("live_session_active", join_code=live_session_code)

        # Create agent-specific metrics
        agent_metrics = AgentMetrics(
            agent_type=subtask.agent_type,
            subtask_id=subtask_id,
            subtask_title=subtask.title,
            input_tokens=result.get("input_tokens", 0),
            output_tokens=result.get("output_tokens", 0),
            cost=result.get("cost", 0.0),
            execution_time_seconds=execution_time,
            tool_calls=result.get("tool_calls", 0),
            files_modified=result.get("files_modified", []),
            success=True,
        )

        # Update overall metrics
        updated_metrics = state.metrics.model_copy()
        updated_metrics.add_agent_metrics(agent_metrics)
        updated_metrics.execution_time_seconds = time.time() - start_time

        # Update live session codes
        live_codes = state.live_session_codes.copy()
        if live_session_code and live_session_code not in live_codes:
            live_codes.append(live_session_code)

        return {
            "completed_subtasks": state.completed_subtasks + [subtask_id],
            "current_subtask_id": None,
            "metrics": updated_metrics,
            "live_session_codes": live_codes,
        }

    except Exception as e:
        execution_time = time.time() - start_time
        logger.error("subtask_execution_failed", subtask_id=subtask_id, error=str(e))
        subtask.status = "failed"
        subtask.error = str(e)

        # Track failed agent metrics
        agent_metrics = AgentMetrics(
            agent_type=subtask.agent_type,
            subtask_id=subtask_id,
            subtask_title=subtask.title,
            execution_time_seconds=execution_time,
            success=False,
            error=str(e),
        )

        updated_metrics = state.metrics.model_copy()
        updated_metrics.add_agent_metrics(agent_metrics)

        return {
            "failed_subtasks": state.failed_subtasks + [subtask_id],
            "current_subtask_id": None,
            "metrics": updated_metrics,
        }

    finally:
        # Always cleanup sandbox
        if sandbox:
            try:
                await sandbox.cleanup()
            except Exception as e:
                logger.error("sandbox_cleanup_failed", error=str(e))


async def review_results(state: OrchestratorState) -> dict[str, Any]:
    """Review the results of all subtasks."""
    logger.info("reviewing_results", task_id=state.task_id)

    if not state.plan:
        return {"error": "No plan available"}

    completed = [st for st in state.plan.subtasks if st.id in state.completed_subtasks]
    failed = [st for st in state.plan.subtasks if st.id in state.failed_subtasks]

    llm = get_llm()

    system_prompt = """You are a code reviewer summarizing the results of a multi-agent task execution.

Review the completed subtasks and provide:
1. A summary of what was accomplished
2. Any issues or concerns
3. Suggestions for improvement

Be concise and focus on the key outcomes."""

    summary_content = f"""Task: {state.task.get('title')}

Completed subtasks ({len(completed)}):
{chr(10).join(f"- {st.title}: {st.result}" for st in completed)}

Failed subtasks ({len(failed)}):
{chr(10).join(f"- {st.title}: {st.error}" for st in failed)}"""

    enriched_prompt = await _intercept_prompt(summary_content, state)

    response = await llm.ainvoke(
        [
            SystemMessage(content=system_prompt),
            HumanMessage(content=enriched_prompt),
        ]
    )

    await _record_prompt_interaction(state, summary_content, response.content, "review_results")

    logger.info("results_reviewed")

    return {
        "final_result": response.content,
        "messages": state.messages + [response],
    }


async def commit_changes(state: OrchestratorState) -> dict[str, Any]:
    """Commit the changes to git."""
    logger.info("committing_changes", task_id=state.task_id)

    if not state.plan:
        return {}

    # Collect all modified files
    all_files = []
    for subtask in state.plan.subtasks:
        if subtask.id in state.completed_subtasks:
            all_files.extend(subtask.files_modified)

    if not all_files:
        logger.info("no_files_to_commit")
        return {"should_end": True}

    try:
        git_manager = GitManager()

        # Create branch
        branch_name = f"squadx/task-{state.task_id}"
        git_manager.create_branch(branch_name)

        # Stage and commit
        commit_message = f"""[SquadX] {state.task.get('title', 'Task completion')}

{state.final_result or 'Task completed by AI agents'}

Task ID: {state.task_id}
Files modified: {len(all_files)}
"""
        commit_hash = git_manager.commit(all_files, commit_message)

        logger.info("changes_committed", branch=branch_name, commit=commit_hash)

        return {
            "git_branch": branch_name,
            "git_commit": commit_hash,
            "should_end": True,
        }

    except Exception as e:
        logger.error("commit_failed", error=str(e))
        return {"should_end": True}


async def handle_error(state: OrchestratorState) -> dict[str, Any]:
    """Handle errors in the orchestration."""
    logger.error("orchestration_error", error=state.error, task_id=state.task_id)

    return {
        "should_end": True,
        "final_result": f"Task execution failed: {state.error}",
    }
