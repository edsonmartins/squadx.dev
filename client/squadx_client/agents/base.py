"""Base agent class for all specialist agents."""

from abc import ABC, abstractmethod
from typing import Any

import structlog
from langchain_core.messages import HumanMessage, SystemMessage

from squadx_client.llm.router import get_coding_llm

logger = structlog.get_logger()


class BaseAgent(ABC):
    """Base class for all specialist agents."""

    agent_type: str = "base"
    system_prompt: str = "You are a helpful AI assistant."

    def __init__(self):
        self.llm = get_coding_llm()
        self.logger = structlog.get_logger().bind(agent_type=self.agent_type)

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

        system_prompt = self.get_system_prompt()

        # Build the context string
        context_str = ""
        if context:
            if context.get("main_task"):
                context_str += f"\n\nMain task: {context['main_task'].get('title', 'N/A')}"
            if context.get("completed_subtasks"):
                context_str += "\n\nAlready completed:"
                for st in context["completed_subtasks"]:
                    context_str += f"\n- {st.title}: {st.result}"

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

        # Parse the response
        output = response.content
        files_modified = self._extract_files(output)

        # Estimate tokens (rough approximation)
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
