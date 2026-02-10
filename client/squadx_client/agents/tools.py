"""LangChain tools for agent sandbox execution."""

from typing import Optional, TYPE_CHECKING
from langchain_core.tools import tool
from pydantic import BaseModel, Field

if TYPE_CHECKING:
    from squadx_client.docker.sandbox import AgentSandbox


class SandboxToolkit:
    """Toolkit providing sandbox execution tools for agents."""

    def __init__(self, sandbox: "AgentSandbox"):
        self.sandbox = sandbox

    def get_tools(self):
        """Get all tools bound to this sandbox instance."""
        return [
            self._create_execute_bash_tool(),
            self._create_write_file_tool(),
            self._create_read_file_tool(),
            self._create_list_directory_tool(),
            self._create_run_python_tool(),
            self._create_install_deps_tool(),
        ]

    def _create_execute_bash_tool(self):
        """Create the execute_bash tool bound to this sandbox."""
        sandbox = self.sandbox

        @tool
        async def execute_bash(command: str) -> str:
            """Execute a bash command in the sandbox environment.

            Use this to run shell commands, scripts, or system utilities.
            The command runs in the /workspace directory by default.

            Args:
                command: The bash command to execute.

            Returns:
                The command output (stdout and stderr combined).
            """
            result = await sandbox.execute(
                ["bash", "-c", command],
                workdir="/workspace",
                timeout=120,
            )
            if result.success:
                return result.output or "(no output)"
            else:
                return f"Error (exit code {result.exit_code}): {result.error or result.output}"

        return execute_bash

    def _create_write_file_tool(self):
        """Create the write_file tool bound to this sandbox."""
        sandbox = self.sandbox

        @tool
        async def write_file(path: str, content: str) -> str:
            """Write content to a file in the sandbox.

            Use this to create or overwrite files. The path should be relative
            to /workspace or an absolute path within the sandbox.

            Args:
                path: File path (relative to /workspace or absolute).
                content: The content to write to the file.

            Returns:
                Success or error message.
            """
            # Ensure path is within workspace
            if not path.startswith("/"):
                path = f"/workspace/{path}"

            success = await sandbox.write_file(path, content)
            if success:
                return f"Successfully wrote to {path}"
            else:
                return f"Failed to write to {path}"

        return write_file

    def _create_read_file_tool(self):
        """Create the read_file tool bound to this sandbox."""
        sandbox = self.sandbox

        @tool
        async def read_file(path: str) -> str:
            """Read content from a file in the sandbox.

            Args:
                path: File path (relative to /workspace or absolute).

            Returns:
                The file content or an error message.
            """
            if not path.startswith("/"):
                path = f"/workspace/{path}"

            content = await sandbox.read_file(path)
            if content is not None:
                return content
            else:
                return f"Error: Could not read file {path}"

        return read_file

    def _create_list_directory_tool(self):
        """Create the list_directory tool bound to this sandbox."""
        sandbox = self.sandbox

        @tool
        async def list_directory(path: str = ".") -> str:
            """List contents of a directory in the sandbox.

            Args:
                path: Directory path (relative to /workspace or absolute).

            Returns:
                List of files and directories.
            """
            if not path.startswith("/"):
                path = f"/workspace/{path}"

            result = await sandbox.execute(
                ["ls", "-la", path],
                workdir="/workspace",
                timeout=30,
            )
            if result.success:
                return result.output
            else:
                return f"Error listing directory: {result.error}"

        return list_directory

    def _create_run_python_tool(self):
        """Create the run_python tool bound to this sandbox."""
        sandbox = self.sandbox

        @tool
        async def run_python(code: str, filename: str = "script.py") -> str:
            """Execute Python code in the sandbox.

            Args:
                code: The Python code to execute.
                filename: Optional filename to save the script as.

            Returns:
                The execution output.
            """
            script_path = f"/workspace/{filename}"

            # Write the script
            success = await sandbox.write_file(script_path, code)
            if not success:
                return "Error: Failed to write Python script"

            # Execute the script
            result = await sandbox.execute(
                ["python3", script_path],
                workdir="/workspace",
                timeout=120,
            )

            if result.success:
                return result.output or "(no output)"
            else:
                return f"Error (exit code {result.exit_code}):\n{result.error or result.output}"

        return run_python

    def _create_install_deps_tool(self):
        """Create the install_dependencies tool bound to this sandbox."""
        sandbox = self.sandbox

        @tool
        async def install_dependencies(
            package_manager: str,
            packages: str,
        ) -> str:
            """Install dependencies using a package manager.

            Args:
                package_manager: The package manager to use (npm, pip, pnpm, yarn).
                packages: Space-separated list of packages to install.

            Returns:
                Installation output or error.
            """
            pm = package_manager.lower()

            if pm == "npm":
                cmd = f"npm install {packages}"
            elif pm == "pip":
                cmd = f"pip install {packages}"
            elif pm == "pnpm":
                cmd = f"pnpm add {packages}"
            elif pm == "yarn":
                cmd = f"yarn add {packages}"
            else:
                return f"Unknown package manager: {package_manager}"

            result = await sandbox.execute(
                ["bash", "-c", cmd],
                workdir="/workspace",
                timeout=300,  # Allow more time for package installation
            )

            if result.success:
                return f"Successfully installed: {packages}"
            else:
                return f"Installation failed:\n{result.error or result.output}"

        return install_dependencies


# Additional specialized tools

class GitToolkit:
    """Git-related tools for agent execution."""

    def __init__(self, sandbox: "AgentSandbox"):
        self.sandbox = sandbox

    def get_tools(self):
        """Get all git tools."""
        return [
            self._create_git_status_tool(),
            self._create_git_diff_tool(),
            self._create_git_add_tool(),
        ]

    def _create_git_status_tool(self):
        sandbox = self.sandbox

        @tool
        async def git_status() -> str:
            """Get the current git status of the workspace.

            Returns:
                The git status output.
            """
            result = await sandbox.execute(
                ["git", "status", "--porcelain"],
                workdir="/workspace",
                timeout=30,
            )
            if result.success:
                return result.output or "(working tree clean)"
            else:
                return f"Error: {result.error}"

        return git_status

    def _create_git_diff_tool(self):
        sandbox = self.sandbox

        @tool
        async def git_diff(file_path: str = "") -> str:
            """Get the git diff for changes.

            Args:
                file_path: Optional specific file to diff. If empty, shows all changes.

            Returns:
                The diff output.
            """
            cmd = ["git", "diff"]
            if file_path:
                cmd.append(file_path)

            result = await sandbox.execute(
                cmd,
                workdir="/workspace",
                timeout=30,
            )
            if result.success:
                return result.output or "(no changes)"
            else:
                return f"Error: {result.error}"

        return git_diff

    def _create_git_add_tool(self):
        sandbox = self.sandbox

        @tool
        async def git_add(file_path: str = ".") -> str:
            """Stage files for commit.

            Args:
                file_path: File or directory to stage. Defaults to all changes.

            Returns:
                Success or error message.
            """
            result = await sandbox.execute(
                ["git", "add", file_path],
                workdir="/workspace",
                timeout=30,
            )
            if result.success:
                return f"Staged: {file_path}"
            else:
                return f"Error: {result.error}"

        return git_add


def create_sandbox_tools(sandbox: "AgentSandbox") -> list:
    """Create all tools for a sandbox instance.

    Args:
        sandbox: The AgentSandbox instance to bind tools to.

    Returns:
        List of LangChain tools.
    """
    sandbox_toolkit = SandboxToolkit(sandbox)
    git_toolkit = GitToolkit(sandbox)

    return sandbox_toolkit.get_tools() + git_toolkit.get_tools()
