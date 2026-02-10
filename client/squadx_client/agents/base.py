"""Base agent class for all specialist agents."""

from abc import ABC, abstractmethod
from typing import Any, Optional, TYPE_CHECKING

import structlog
from langchain_core.messages import HumanMessage, SystemMessage, AIMessage

from squadx_client.llm.router import get_coding_llm

if TYPE_CHECKING:
    from squadx_client.docker.sandbox import AgentSandbox

logger = structlog.get_logger()


class BaseAgent(ABC):
    """Base class for all specialist agents."""

    agent_type: str = "base"
    system_prompt: str = "You are a helpful AI assistant."

    def __init__(self, sandbox: Optional["AgentSandbox"] = None):
        self.llm = get_coding_llm()
        self.sandbox = sandbox
        self.tools = []
        self.logger = structlog.get_logger().bind(agent_type=self.agent_type)

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

        # Use tool-based execution if sandbox is available
        if self.sandbox:
            return await self._execute_with_tools(task_title, task_description, context)
        else:
            return await self._execute_simple(task_title, task_description, context)

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

            # Estimate tokens
            total_input_tokens += len(str(messages)) // 4
            total_output_tokens += len(response.content or "") // 4

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

        response = await self.llm.ainvoke(
            [
                SystemMessage(content=system_prompt),
                HumanMessage(content=user_message),
            ]
        )

        output = response.content
        files_modified = self._extract_files(output)

        input_tokens = len(system_prompt + user_message) // 4
        output_tokens = len(output) // 4

        self.logger.info(
            "task_completed",
            files_modified=len(files_modified),
            output_length=len(output),
        )

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

    def _estimate_cost(self, input_tokens: int, output_tokens: int) -> float:
        """Estimate the cost based on token usage."""
        # GPT-4o pricing (rough estimate)
        input_cost = input_tokens * 0.00001  # $10 per 1M tokens
        output_cost = output_tokens * 0.00003  # $30 per 1M tokens
        return input_cost + output_cost
