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
from squadx_client.memory import BrainSentryClient, MemoryCollector, PromptInterceptor
from squadx_client.memory.policy import MemoryScopeContext
from squadx_client.orchestrator.state import OrchestratorState, TaskPlan, SubTask, ExecutionMetrics, AgentMetrics
from squadx_client.orchestrator.context_packet import build_context_packet
from squadx_client.agents.factory import create_agent
from squadx_client.agents.security import enforce_prompt_security, filter_internal_artifacts
from squadx_client.docker.sandbox import AgentSandbox
from squadx_client.git.manager import GitManager
from squadx_client.git.worktree import WorktreeManager

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


def _is_git_repo(path: str) -> bool:
    """Cheap guard: a worktree is only useful when the workspace is a git repo.

    Uses gitpython because the daemon already depends on it (git/manager.py);
    avoids spawning a `git` subprocess for a hot-path check.
    """
    from git import Repo
    from git.exc import InvalidGitRepositoryError, NoSuchPathError

    try:
        Repo(path)
        return True
    except (InvalidGitRepositoryError, NoSuchPathError):
        return False


def _map_complexity(value: Any) -> str:
    """Map the analyzer's free-form complexity to the loop's gate scale.

    trivial → one quick pass, skip heavy gates; standard → full gate;
    risky → full gate, no APPROVE while any blocker remains.
    """
    text = (str(value).strip().lower() if value is not None else "")
    return {
        "low": "trivial",
        "trivial": "trivial",
        "medium": "standard",
        "standard": "standard",
        "high": "risky",
        "risky": "risky",
    }.get(text, "standard")


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
    # Apply the same prompt-injection policy as the External-CLI path (threat-model #5):
    # the task title/description is untrusted input. enforce mode aborts here before any LLM call.
    enforce_prompt_security(
        task_prompt,
        mode=getattr(settings, "cli_security_mode", "enforce"),
        logger=logger,
    )
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

    complexity = _map_complexity(analysis.get("complexity"))

    logger.info("task_analyzed", analysis=analysis, complexity=complexity)

    return {
        "messages": state.messages + [response],
        "complexity": complexity,
    }


async def create_plan(state: OrchestratorState) -> dict[str, Any]:
    """Create an execution plan with subtasks."""
    logger.info("creating_plan", task_id=state.task_id)

    task = state.task
    llm = get_llm()

    system_prompt = """You are the Planner/Architect. Convert the request into an executable plan \
BEFORE any code is written — you never write code here. Reuse before adding.

For EACH subtask specify:
- id: a short unique identifier (e.g. "s1")
- title: brief title
- description: detailed description of what to do
- agent_type: frontend|backend|fullstack|devops|qa
- acceptance_criteria: >= 2 independently testable criteria, each prefixed with an ID (AC1, AC2, ...)
- depends_on: ids of subtasks that must finish first ([] if none)

Also identify, for the change as a whole:
- reuse: the key existing files/patterns/signatures the coder should build on rather than reinvent
  (one or two lines; "" if genuinely unknown)
- complexity: trivial (one-line/local) | standard | risky (multi-service, migration, security/data)

For a BUG, the FIRST subtask must be "write a failing test that reproduces it".
Slice the work so subtasks integrate cleanly, and order them so dependencies come first.

Respond in JSON format:
{
    "complexity": "trivial|standard|risky",
    "reuse": "key files/patterns to build on",
    "subtasks": [
        {
            "id": "s1",
            "title": "Subtask title",
            "description": "What the agent should do",
            "agent_type": "backend",
            "acceptance_criteria": ["AC1 <testable>", "AC2 <testable>"],
            "depends_on": []
        }
    ],
    "execution_order": ["s1", "s2"],
    "parallel_groups": [["s1"], ["s2"]]
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

    plan_complexity: str | None = None
    try:
        plan_data = json.loads(response.content)
        if plan_data.get("complexity"):
            plan_complexity = _map_complexity(plan_data.get("complexity"))
        subtasks = [
            SubTask(
                id=st.get("id", str(uuid.uuid4())),
                title=st["title"],
                description=st["description"],
                agent_type=st["agent_type"],
                acceptance_criteria=[str(ac) for ac in st.get("acceptance_criteria", [])],
                depends_on=[str(d) for d in st.get("depends_on", [])],
            )
            for st in plan_data.get("subtasks", [])
        ]

        plan = TaskPlan(
            analysis=state.messages[-1].content if state.messages else "",
            approach="Multi-agent execution",
            subtasks=subtasks,
            execution_order=plan_data.get("execution_order", [st.id for st in subtasks]),
            parallel_groups=plan_data.get("parallel_groups", []),
            reuse=str(plan_data.get("reuse", "")),
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

    logger.info("plan_created", subtask_count=len(plan.subtasks), complexity=plan_complexity)

    update: dict[str, Any] = {
        "plan": plan,
        "messages": state.messages + [response],
    }
    # The planner sees the decomposition, so prefer its complexity call over the analyzer's.
    if plan_complexity:
        update["complexity"] = plan_complexity
    return update


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

        # Worktree isolation (SQUADX_USE_WORKTREES=true + git repo): each subtask
        # gets its own branch under .worktrees/<unique-name>/, branched from main.
        # The sandbox mounts that worktree path as /workspace, so the agent's edits
        # stay isolated from sibling subtasks and from the integration branch.
        # Falls back to the shared workspace on any failure — worktree is a hardening
        # layer, never a hard dependency.
        subtask_worktree_branch: str | None = None
        subtask_work_path = workspace_path
        if getattr(settings, "use_worktrees", False) and _is_git_repo(workspace_path):
            try:
                # Unique key per subtask so two backend subtasks don't collide.
                wt_key = f"{subtask.agent_type}-{subtask_id}"
                wm = WorktreeManager(workspace_path)
                wt_info = wm.create(
                    agent_name=wt_key,
                    task_id=str(state.task_id),
                    base_branch="main",
                )
                subtask_work_path = wt_info.path
                subtask_worktree_branch = wt_info.branch
                logger.info(
                    f"worktree_created subtask={subtask_id} branch={wt_info.branch}"
                )
            except Exception as e:
                logger.warning(
                    f"worktree_setup_failed_falling_back subtask={subtask_id} error={e}"
                )

        # Check if sandbox execution is enabled
        use_sandbox = getattr(settings, "enable_sandbox", True)

        if use_sandbox:
            # Per-squad egress policy from the backend (RFC-0006). None when absent or
            # unrecognised — AgentSandbox then uses the daemon default, never no policy.
            from squadx_client.docker.network_policy import policy_name_from_backend

            network_policy = policy_name_from_backend(
                (state.task or {}).get("sandbox_egress_policy")
            )

            # Create sandbox for this subtask
            sandbox = AgentSandbox(
                task_id=state.task_id,
                agent_type=subtask.agent_type,
                workspace_path=subtask_work_path,
                network_policy=network_policy,
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

        # Assemble a curated, auditable Context Packet (ADR-0007, RFC-0005) and pass it alongside
        # the raw context. Prompt builders prefer the packet; raw keys remain for compatibility.
        subtask_context: dict[str, Any] = {
            "main_task": state.task,
            "execution_id": state.execution_id or state.task_id,
            "completed_subtasks": [
                st for st in state.plan.subtasks if st.id in state.completed_subtasks
            ],
            "acceptance_criteria": subtask.acceptance_criteria,
            "reuse_map": state.plan.reuse,
        }
        subtask_context["context_packet"] = build_context_packet(subtask_context)

        # Execute the subtask
        result = await agent.execute(
            task_title=subtask.title,
            task_description=subtask.description,
            context=subtask_context,
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
        # Checkpoint the worktree BEFORE tearing down the sandbox so the host
        # bind-mount path is still valid. Files live on the host, so cleanup()
        # doesn't lose them — but we want a recorded commit on the branch.
        if subtask_worktree_branch and _is_git_repo(workspace_path):
            try:
                wt_key = f"{subtask.agent_type}-{subtask_id}"
                wm = WorktreeManager(workspace_path)
                wm.checkpoint(
                    agent_name=wt_key,
                    message=f"subtask {subtask_id} completed",
                )
                # Persist for the commit_changes merge step. Only on success.
                state.subtask_worktrees[subtask_id] = subtask_worktree_branch
            except Exception as e:
                logger.warning(
                    f"worktree_checkpoint_failed subtask={subtask_id} error={e}"
                )
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

    system_prompt = """You are a strict, adversarial code reviewer. You are handed ONLY the work \
produced (not the reasoning behind it), so you are not biased toward it. Actively try to break it.

Review against: correctness (every acceptance criterion, edge cases, error paths), security \
(injection, secrets, authz, path traversal), cross-service contracts, and real test coverage. \
Also sweep for design rot: needless re-implementation (reuse), over-engineering (simplification), \
hot-path inefficiency, and code living at the wrong layer (altitude).

Classify each finding's severity: blocker | major | minor | nit. A failed subtask is a blocker.
Be concrete; cite the subtask and, when possible, file:line. Do NOT bikeshed style as a blocker.

Respond in JSON only:
{
  "summary": "one short paragraph: overall verdict",
  "risk": "low|medium|high",
  "findings": [
    {"severity": "blocker|major|minor|nit", "subtask": "<title>", "file": "path:line or ''", "what": "what is wrong and how to fix"}
  ]
}"""

    summary_content = f"""Task: {state.task.get('title')}
Complexity: {state.complexity} | review round: {state.cycle_count + 1}/{state.max_cycles}

Completed subtasks ({len(completed)}):
{chr(10).join(f"- {st.title} [ACs: {'; '.join(st.acceptance_criteria) or 'none'}]: {st.result}" for st in completed)}

Failed subtasks ({len(failed)}):
{chr(10).join(f"- {st.title}: {st.error}" for st in failed)}

Do a FRESH full adversarial pass over the CURRENT state — a fix routinely introduces a new issue, \
so re-derive findings from scratch rather than just confirming old ones are gone."""

    enriched_prompt = await _intercept_prompt(summary_content, state)

    response = await llm.ainvoke(
        [
            SystemMessage(content=system_prompt),
            HumanMessage(content=enriched_prompt),
        ]
    )

    await _record_prompt_interaction(state, summary_content, response.content, "review_results")

    findings: list[dict[str, Any]] = []
    summary_text = response.content
    try:
        parsed = json.loads(response.content)
        findings = [f for f in parsed.get("findings", []) if isinstance(f, dict)]
        summary_text = parsed.get("summary", response.content)
    except (json.JSONDecodeError, AttributeError):
        # Couldn't parse a structured verdict — treat any failed subtask as a blocker so the
        # arbiter still gates, but don't fabricate findings for completed work.
        logger.warning("review_parse_failed", task_id=state.task_id)

    # A failed subtask is always a blocker, even if the model didn't enumerate it.
    for st in failed:
        findings.append({
            "severity": "blocker",
            "subtask": st.title,
            "file": "",
            "what": f"Subtask failed: {st.error}",
        })

    logger.info("results_reviewed", finding_count=len(findings))

    return {
        "final_result": summary_text,
        "review_findings": findings,
        "messages": state.messages + [response],
    }


def _blockers(findings: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Findings severe enough to block a SHIP."""
    return [f for f in findings if str(f.get("severity", "")).lower() in ("blocker", "major")]


def _pick_fix_agent(state: OrchestratorState, blockers: list[dict[str, Any]]) -> str:
    """Route a fix subtask to the specialist whose work the blockers touch."""
    valid = {"frontend", "backend", "fullstack", "devops", "qa"}
    if state.plan:
        flagged_titles = {str(b.get("subtask", "")).strip() for b in blockers}
        for st in state.plan.subtasks:
            if st.title in flagged_titles and st.agent_type in valid:
                return st.agent_type
    return "fullstack"


async def _record_team_learnings(state: OrchestratorState, *, verdict: str, summary: str,
                                 blockers: list[dict[str, Any]]) -> None:
    """Distil what this run learned so the next one starts smarter (team memory loop).

    Reading already happens via the prompt interceptor; this closes the loop on the write side.
    Best-effort: never let a memory failure break the run.
    """
    if not state.brainsentry_session_id:
        return
    client = BrainSentryClient()
    collector = MemoryCollector(client)
    scope = _resolve_memory_scope(state)
    tags = ["execution", "arbiter", verdict] + [t for t in (scope.project_id, scope.task_id) if t]
    try:
        collector.record_learning(f"[{verdict}] {state.task.get('title', 'task')}: {summary}"[:1000], tags=tags)
        for b in blockers:
            collector.record_antipattern(
                f"Recurring review finding on '{b.get('subtask', '')}': {b.get('what', '')}"[:1000],
                tags=tags,
            )
        await collector.flush()
    except Exception as exc:  # noqa: BLE001 - memory is non-critical
        logger.warning("team_memory_write_failed", error=str(exc))
    finally:
        await client.close()


async def arbiter(state: OrchestratorState) -> dict[str, Any]:
    """The loop-breaker: after each review round decide CONTINUE / APPROVE / ESCALATE.

    Biased hard toward APPROVE or ESCALATE so the team never loops forever. The cycle count
    is a hard backstop; complexity scales the rigor (trivial bails to a human instead of
    looping; risky never approves while a blocker remains).
    """
    cycle = state.cycle_count + 1
    blockers = _blockers(state.review_findings)
    has_failed = bool(state.failed_subtasks)
    needs_work = bool(blockers) or has_failed
    spent = state.metrics.total_cost
    over_budget = state.cost_budget_usd is not None and spent >= state.cost_budget_usd

    if not needs_work:
        verdict = "approve"
    elif cycle >= state.max_cycles:
        verdict = "escalate"  # didn't converge within the backstop
    elif over_budget:
        verdict = "escalate"  # cost ceiling hit — stop spending on another loop, hand to a human
    elif state.complexity == "trivial":
        verdict = "escalate"  # a trivial task that still has blockers isn't worth looping on
    else:
        verdict = "continue"

    logger.info("arbiter_decision", verdict=verdict, cycle=cycle, max_cycles=state.max_cycles,
                blockers=len(blockers), failed=len(state.failed_subtasks), complexity=state.complexity,
                cost_usd=round(spent, 4), budget_usd=state.cost_budget_usd)

    if verdict == "continue":
        # Inject a single fix subtask that owns the open blockers and route back to execute.
        plan = state.plan.model_copy(deep=True)
        fix_id = str(uuid.uuid4())
        blocker_desc = "\n".join(
            f"- [{b.get('severity')}] {b.get('subtask', '')} ({b.get('file') or 'n/a'}): {b.get('what', '')}"
            for b in blockers
        ) or "Address the failed subtasks from the previous round."
        plan.subtasks.append(SubTask(
            id=fix_id,
            title=f"Fix review findings (round {cycle})",
            description=f"Resolve the blocking review findings below. Reproduce before fixing; "
                        f"do not introduce regressions.\n\n{blocker_desc}",
            agent_type=_pick_fix_agent(state, blockers),
            is_fix=True,
        ))
        plan.execution_order.append(fix_id)
        return {
            "review_verdict": "continue",
            "cycle_count": cycle,
            "plan": plan,
            # The fix subtask now owns the prior failures; clear them so they don't perpetually re-block.
            "failed_subtasks": [],
        }

    summary = state.final_result or ""
    await _record_team_learnings(state, verdict=verdict, summary=summary, blockers=blockers)

    if verdict == "escalate":
        if over_budget:
            reason = (f"Cost ceiling reached (${spent:.4f} ≥ ${state.cost_budget_usd:.4f}) with "
                      f"{len(blockers)} blocker(s) still open — stopped before another loop.")
        else:
            reason = (f"Did not converge after {cycle} round(s); "
                      f"{len(blockers)} blocker(s) and {len(state.failed_subtasks)} failed subtask(s) remain.")
        return {"review_verdict": "escalate", "cycle_count": cycle, "escalation_reason": reason}

    return {"review_verdict": "approve", "cycle_count": cycle}


async def escalate(state: OrchestratorState) -> dict[str, Any]:
    """Hand the task back to a human instead of shipping unconverged work.

    Reports the blocker through the workspace MCP tool when it's available (it ships on the
    Control Panel branches); degrades to a log-only escalation otherwise.
    """
    reason = state.escalation_reason or "Task escalated to a human reviewer."
    logger.warning("task_escalated", task_id=state.task_id, reason=reason)

    try:
        from squadx_client.mcp.bridge import report_blocker  # optional: present on cp-mcp-bridge

        await report_blocker(state.task_id, reason)
        logger.info("blocker_reported", task_id=state.task_id)
    except ImportError:
        logger.info("mcp_bridge_absent_skipping_report_blocker", task_id=state.task_id)
    except Exception as exc:  # noqa: BLE001 - escalation must not crash the run
        logger.warning("report_blocker_failed", task_id=state.task_id, error=str(exc))

    return {
        "should_end": True,
        "final_result": f"Escalated to human: {reason}",
    }


async def commit_changes(state: OrchestratorState) -> dict[str, Any]:
    """Commit the changes to git."""
    logger.info("committing_changes", task_id=state.task_id)

    if not state.plan:
        return {}

    # Gate: only the arbiter's APPROVE reaches a commit. Defensive — the graph already routes
    # CONTINUE/ESCALATE elsewhere — but it stops a future rewiring from shipping ungated work.
    if state.review_verdict != "approve":
        logger.warning("commit_blocked_unapproved", verdict=state.review_verdict, task_id=state.task_id)
        return {"should_end": True}

    # Collect all modified files, dropping internal agent-CLI artifacts (.claude/.codex/.omx)
    # so they never pollute the task commit (ADR-0007).
    all_files = []
    for subtask in state.plan.subtasks:
        if subtask.id in state.completed_subtasks:
            all_files.extend(subtask.files_modified)
    all_files = filter_internal_artifacts(all_files)

    if not all_files:
        logger.info("no_files_to_commit")
        return {"should_end": True}

    try:
        git_manager = GitManager()

        # Fold each per-subtask worktree branch into the integration branch.
        # Done BEFORE create_branch so the merge commits sit on top of the
        # pre-task state and the final commit captures the merged result.
        # Failure of any individual merge is non-fatal — we log and continue
        # so one broken subtask doesn't kill the whole task commit.
        for subtask_id, branch_name in state.subtask_worktrees.items():
            try:
                if not git_manager.merge_branch(
                    branch_name,
                    no_ff=True,
                    message=f"Merge subtask {subtask_id} work from {branch_name}",
                ):
                    logger.warning(
                        f"worktree_merge_returned_false subtask={subtask_id} "
                        f"branch={branch_name}"
                    )
            except Exception as e:
                logger.warning(
                    f"worktree_merge_failed subtask={subtask_id} "
                    f"branch={branch_name} error={e}"
                )

        # Create branch
        branch_name = f"squadx/task-{state.task_id}"
        git_manager.create_branch(branch_name)

        # Stage and commit. Write as the engineering team — no AI self-attribution (no
        # "Generated with"/"AI agents", no Co-Authored-By bot trailer).
        commit_message = f"""{state.task.get('title', 'Task completion')}

{state.final_result or 'Implements the task per its acceptance criteria.'}

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
