"""Base agent class for all specialist agents."""

from abc import ABC, abstractmethod
from typing import Any, Optional, TYPE_CHECKING

import structlog
from langchain_core.messages import HumanMessage, SystemMessage, AIMessage

from squadx_client.llm.router import get_coding_llm
from squadx_client.memory import BrainSentryClient, MemoryCollector, PromptInterceptor
from squadx_client.memory.policy import MemoryScopeContext
from squadx_client.memory.procedural import ProceduralMemoryManager

if TYPE_CHECKING:
    from squadx_client.docker.sandbox import AgentSandbox

logger = structlog.get_logger()


class BaseAgent(ABC):
    """Base class for all specialist agents."""

    agent_type: str = "base"
    system_prompt: str = "You are a helpful AI assistant."

    def __init__(self, sandbox: Optional["AgentSandbox"] = None, brainsentry_session_id: str | None = None):
        self.llm = get_coding_llm()
        self.sandbox = sandbox
        self.tools = []
        self.logger = structlog.get_logger().bind(agent_type=self.agent_type)
        self.brainsentry_session_id = brainsentry_session_id
        self.memory_client = BrainSentryClient()
        self.prompt_interceptor = PromptInterceptor(self.memory_client)
        self.prompt_interceptor.set_session(brainsentry_session_id)
        self.memory_collector = MemoryCollector(self.memory_client)
        self.procedural_memory = ProceduralMemoryManager(self.memory_client)

        # If sandbox provided, create tools
        if sandbox:
            from squadx_client.agents.tools import create_sandbox_tools
            self.tools = create_sandbox_tools(sandbox)
            # Bind tools to LLM for function calling
            self.llm_with_tools = self.llm.bind_tools(self.tools)

    @abstractmethod
    def get_system_prompt(self) -> str:
        """Get the system prompt for this agent type."""
        pass

    async def execute(
        self,
        task_title: str,
        task_description: str,
        context: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        """Execute a task.

        Args:
            task_title: Title of the task
            task_description: Detailed description
            context: Additional context (main task, completed subtasks, etc.)

        Returns:
            Dictionary with:
            - output: The result of the task
            - files_modified: List of modified files
            - input_tokens: Number of input tokens used
            - output_tokens: Number of output tokens used
            - cost: Estimated cost
        """
        self.logger.info("executing_task", title=task_title)
        self.prompt_interceptor.set_user(self._resolve_memory_user_id(context))
        self.prompt_interceptor.set_scope(MemoryScopeContext.from_context(context, self.agent_type))

        try:
            # Use tool-based execution if sandbox is available
            if self.sandbox:
                return await self._execute_with_tools(task_title, task_description, context)
            return await self._execute_simple(task_title, task_description, context)
        except Exception as exc:
            await self._record_failure(task_title, task_description, str(exc), context)
            raise
        finally:
            await self.memory_client.close()

    async def _execute_with_tools(
        self,
        task_title: str,
        task_description: str,
        context: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        """Execute task using sandbox tools (agentic loop)."""
        self.logger.info("executing_with_tools", title=task_title)

        system_prompt = self._get_tool_system_prompt()
        context_str = self._build_context_string(context)

        user_message = f"""Task: {task_title}

Description: {task_description}
{context_str}

You have access to a sandbox environment where you can:
- Execute bash commands
- Write and read files
- Run Python code
- Install dependencies
- Use git commands

Complete this task by using the available tools. After completing the task, provide a summary of what was done."""

        original_user_message = user_message
        user_message = await self.prompt_interceptor.intercept(user_message)

        messages = [
            SystemMessage(content=system_prompt),
            HumanMessage(content=user_message),
        ]

        files_modified = []
        total_input_tokens = 0
        total_output_tokens = 0
        total_tool_calls = 0
        max_iterations = 20

        for iteration in range(max_iterations):
            self.logger.debug("tool_loop_iteration", iteration=iteration)

            response = await self.llm_with_tools.ainvoke(messages)
            messages.append(response)

            # Extract token counts from response metadata if available
            input_tok, output_tok = self._extract_token_usage(response)
            total_input_tokens += input_tok
            total_output_tokens += output_tok

            # Check if there are tool calls
            if not response.tool_calls:
                # No more tool calls, agent is done
                self.logger.info("tool_loop_complete", iterations=iteration + 1, tool_calls=total_tool_calls)
                break

            # Execute each tool call
            for tool_call in response.tool_calls:
                tool_name = tool_call["name"]
                tool_args = tool_call["args"]
                total_tool_calls += 1

                self.logger.info("executing_tool", tool=tool_name, args=tool_args)

                # Find and execute the tool
                tool_result = await self._execute_tool(tool_name, tool_args)

                # Track file modifications
                if tool_name == "write_file" and "Successfully" in tool_result:
                    file_path = tool_args.get("path", "")
                    if file_path and file_path not in files_modified:
                        files_modified.append(file_path)

                # Add tool result to messages
                from langchain_core.messages import ToolMessage
                messages.append(
                    ToolMessage(
                        content=tool_result,
                        tool_call_id=tool_call["id"],
                    )
                )

        # Extract final output from last AI message
        final_output = ""
        for msg in reversed(messages):
            if isinstance(msg, AIMessage) and msg.content:
                final_output = msg.content
                break

        self.logger.info(
            "task_completed_with_tools",
            files_modified=len(files_modified),
            iterations=iteration + 1,
            tool_calls=total_tool_calls,
        )

        await self._record_success(task_title, original_user_message, final_output, context, files_modified)

        return {
            "output": final_output,
            "files_modified": files_modified,
            "input_tokens": total_input_tokens,
            "output_tokens": total_output_tokens,
            "tool_calls": total_tool_calls,
            "cost": self._estimate_cost(total_input_tokens, total_output_tokens),
        }

    async def _execute_tool(self, tool_name: str, tool_args: dict) -> str:
        """Execute a specific tool by name."""
        for tool in self.tools:
            if tool.name == tool_name:
                try:
                    result = await tool.ainvoke(tool_args)
                    return str(result)
                except Exception as e:
                    self.logger.error("tool_execution_error", tool=tool_name, error=str(e))
                    return f"Error executing {tool_name}: {str(e)}"

        return f"Unknown tool: {tool_name}"

    def _get_tool_system_prompt(self) -> str:
        """Get system prompt enhanced for tool usage."""
        base_prompt = self.get_system_prompt()
        tool_instructions = """

You have access to the following tools to execute code and modify files:

1. **execute_bash(command)**: Run bash commands in the sandbox
2. **write_file(path, content)**: Create or overwrite files
3. **read_file(path)**: Read file contents
4. **list_directory(path)**: List directory contents
5. **run_python(code, filename)**: Execute Python code
6. **install_dependencies(package_manager, packages)**: Install packages (npm, pip, pnpm, yarn)
7. **git_status()**: Check git status
8. **git_diff(file_path)**: View changes
9. **git_add(file_path)**: Stage files

Guidelines:
- Use tools to actually implement the code, not just describe it
- Read existing files before modifying them
- Test your changes when possible
- Use appropriate tools for each task
- Provide a clear summary when done"""

        return base_prompt + tool_instructions

    async def _execute_simple(
        self,
        task_title: str,
        task_description: str,
        context: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        """Execute task without tools (simple LLM call)."""
        self.logger.info("executing_simple", title=task_title)

        system_prompt = self.get_system_prompt()
        context_str = self._build_context_string(context)

        user_message = f"""Task: {task_title}

Description: {task_description}
{context_str}

Please complete this task. Provide your implementation and list any files that need to be created or modified.

Format your response as:
## Implementation
[Your implementation details]

## Files Modified
- path/to/file1.py
- path/to/file2.js

## Summary
[Brief summary of what was done]"""

        original_user_message = user_message
        user_message = await self.prompt_interceptor.intercept(user_message)

        response = await self.llm.ainvoke(
            [
                SystemMessage(content=system_prompt),
                HumanMessage(content=user_message),
            ]
        )

        output = response.content
        files_modified = self._extract_files(output)

        input_tokens, output_tokens = self._extract_token_usage(response)
        if input_tokens == 0 and output_tokens == 0:
            # Final fallback for _execute_simple if no metadata at all
            input_tokens = len(system_prompt + user_message) // 4
            output_tokens = len(output) // 4

        self.logger.info(
            "task_completed",
            files_modified=len(files_modified),
            output_length=len(output),
        )

        await self._record_success(task_title, original_user_message, output, context, files_modified)

        return {
            "output": output,
            "files_modified": files_modified,
            "input_tokens": input_tokens,
            "output_tokens": output_tokens,
            "cost": self._estimate_cost(input_tokens, output_tokens),
        }

    def _build_context_string(self, context: dict[str, Any] | None) -> str:
        """Build context string from context dict."""
        context_str = ""
        if context:
            if context.get("main_task"):
                context_str += f"\n\nMain task: {context['main_task'].get('title', 'N/A')}"
            if context.get("completed_subtasks"):
                context_str += "\n\nAlready completed:"
                for st in context["completed_subtasks"]:
                    context_str += f"\n- {st.title}: {st.result}"
        return context_str

    def _extract_files(self, output: str) -> list[str]:
        """Extract file paths from the output."""
        files = []
        in_files_section = False

        for line in output.split("\n"):
            if "## Files Modified" in line or "## Files Created" in line:
                in_files_section = True
                continue
            if in_files_section:
                if line.startswith("##"):
                    break
                if line.strip().startswith("-"):
                    file_path = line.strip().lstrip("- ").strip()
                    if file_path and not file_path.startswith("#"):
                        files.append(file_path)

        return files

    @staticmethod
    def _extract_token_usage(response: AIMessage) -> tuple[int, int]:
        """Extract token usage from an LLM response.

        Checks multiple metadata locations used by different LangChain providers:
        1. response.usage_metadata (newer LangChain standard)
        2. response.response_metadata["usage"] (OpenAI-style)
        3. Falls back to character-based estimate

        Returns:
            Tuple of (input_tokens, output_tokens)
        """
        # Try usage_metadata (LangChain >=0.2 standard)
        usage_meta = getattr(response, "usage_metadata", None)
        if usage_meta:
            input_tok = getattr(usage_meta, "input_tokens", 0) or usage_meta.get("input_tokens", 0) if isinstance(usage_meta, dict) else getattr(usage_meta, "input_tokens", 0)
            output_tok = getattr(usage_meta, "output_tokens", 0) or usage_meta.get("output_tokens", 0) if isinstance(usage_meta, dict) else getattr(usage_meta, "output_tokens", 0)
            if input_tok or output_tok:
                return int(input_tok), int(output_tok)

        # Try response_metadata.usage (OpenAI / Anthropic style)
        resp_meta = getattr(response, "response_metadata", None) or {}
        usage = resp_meta.get("usage", {}) if isinstance(resp_meta, dict) else {}
        if usage:
            input_tok = usage.get("prompt_tokens") or usage.get("input_tokens", 0)
            output_tok = usage.get("completion_tokens") or usage.get("output_tokens", 0)
            if input_tok or output_tok:
                return int(input_tok), int(output_tok)

        # Fallback: character-based estimate
        content = response.content or ""
        return len(content) // 4, len(content) // 4

    def _estimate_cost(self, input_tokens: int, output_tokens: int) -> float:
        """Estimate the cost based on token usage."""
        # GPT-4o pricing (rough estimate)
        input_cost = input_tokens * 0.00001  # $10 per 1M tokens
        output_cost = output_tokens * 0.00003  # $30 per 1M tokens
        return input_cost + output_cost

    async def _record_success(
        self,
        task_title: str,
        prompt: str,
        output: str,
        context: dict[str, Any] | None,
        files_modified: list[str],
    ) -> None:
        """Persist successful execution artifacts into BrainSentry."""
        if not self.memory_client.enabled:
            return

        main_task = context.get("main_task", {}) if context else {}
        task_id = main_task.get("task_id") or main_task.get("id")
        execution_id = context.get("execution_id") if context else None

        summary = self._truncate_for_memory(output, 800)
        scope = MemoryScopeContext.from_context(context, self.agent_type)
        self.memory_collector.record_learning(
            f"{self.agent_type} agent completed '{task_title}'. Summary: {summary}",
            tags=[
                "agent-execution",
                self.agent_type,
                *( [f"task:{task_id}"] if task_id else []),
                *( [f"execution:{execution_id}"] if execution_id else []),
                *scope.to_tags(),
            ],
        )

        if files_modified:
            self.memory_collector.record_pattern(
                f"{self.agent_type} agent modified files for '{task_title}': {', '.join(files_modified[:20])}",
                tags=["agent-execution", self.agent_type, "files-modified", *scope.to_tags()],
            )

        await self.procedural_memory.record_procedure(
            title=f"{self.agent_type}::{task_title}",
            summary=summary,
            scope=scope,
            steps=[
                f"Execute task '{task_title}' with agent type {self.agent_type}",
                f"Apply implementation based on current task context and completed subtasks",
                "Validate output and persist execution learnings",
            ],
            files_modified=files_modified,
        )

        await self.memory_client.push_session_interaction(
            self.brainsentry_session_id or "",
            query=self._truncate_for_memory(prompt, 4000),
            response=self._truncate_for_memory(output, 4000),
            metadata={
                "agentType": self.agent_type,
                "taskTitle": task_title,
                **scope.to_metadata(),
            },
        )
        await self.memory_collector.flush()

    async def _record_failure(
        self,
        task_title: str,
        task_description: str,
        error: str,
        context: dict[str, Any] | None,
    ) -> None:
        """Persist failure artifacts into BrainSentry."""
        if not self.memory_client.enabled:
            return

        main_task = context.get("main_task", {}) if context else {}
        task_id = main_task.get("task_id") or main_task.get("id")
        execution_id = context.get("execution_id") if context else None

        self.memory_collector.record_bug(
            f"{self.agent_type} agent failed on '{task_title}'. Error: {self._truncate_for_memory(error, 1000)}",
            tags=[
                "agent-execution",
                self.agent_type,
                *( [f"task:{task_id}"] if task_id else []),
                *( [f"execution:{execution_id}"] if execution_id else []),
                *MemoryScopeContext.from_context(context, self.agent_type).to_tags(),
            ],
        )

        self.memory_collector.record_antipattern(
            f"Avoid repeating failure while executing '{task_title}': {self._truncate_for_memory(error, 500)}",
            tags=[
                "agent-execution",
                self.agent_type,
                "failure",
                *( [f"task:{task_id}"] if task_id else []),
                *( [f"execution:{execution_id}"] if execution_id else []),
                *MemoryScopeContext.from_context(context, self.agent_type).to_tags(),
            ],
        )

        await self.memory_client.push_session_interaction(
            self.brainsentry_session_id or "",
            query=self._truncate_for_memory(task_description, 4000),
            response=f"ERROR: {self._truncate_for_memory(error, 4000)}",
            metadata={
                "agentType": self.agent_type,
                "taskTitle": task_title,
                "status": "failed",
                **MemoryScopeContext.from_context(context, self.agent_type).to_metadata(),
            },
        )
        await self.memory_collector.flush()

    @staticmethod
    def _truncate_for_memory(text: str | None, limit: int) -> str:
        """Bound large payloads before sending them to memory services."""
        if not text:
            return ""
        if len(text) <= limit:
            return text
        return text[: limit - 3] + "..."

    @staticmethod
    def _resolve_memory_user_id(context: dict[str, Any] | None) -> str | None:
        if not context:
            return None

        agent_id = context.get("assigned_agent_id") or context.get("agent_id")
        if agent_id:
            return str(agent_id)

        execution_id = context.get("execution_id")
        if execution_id:
            return f"execution-{execution_id}"

        return None
