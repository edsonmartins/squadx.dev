"""Agent factory for creating specialist agents."""

from typing import TYPE_CHECKING, Optional

from squadx_client.agents.base import BaseAgent

if TYPE_CHECKING:
    from squadx_client.docker.sandbox import AgentSandbox


# Shared discipline appended to every specialist prompt — the cross-cutting habits that keep
# AI-written code honest (reproduce-first, reuse over reinvention, verify-before-done, real docs,
# no AI self-attribution).
_ENGINEERING_DISCIPLINE = """

Engineering discipline (every task):
- Reuse before adding: match the existing structure and patterns; extend a helper rather than
  duplicate it. No speculative abstraction, no over-engineering.
- Bug fixes: reproduce FIRST with a failing test (confirm it's red), then fix until green.
- Satisfy every acceptance criterion you are given; add or update focused tests for what you change.
- Look up CURRENT library docs (don't guess an API from memory) when you touch an external
  library/SDK/framework — pull the version the project actually depends on.
- Verify before claiming done: build, run the relevant tests and linter, check for regressions.
  Never report a red build as done — report what you actually ran and its result.
- Keep the change minimal and scoped: no unrelated edits or drive-by refactors.
- Write commits/PRs as the engineering team: no AI self-attribution, no "Generated with",
  no Co-Authored-By bot trailer.

Output: a summary of what changed, the files touched, and the exact commands to verify it."""


class FrontendAgent(BaseAgent):
    """Specialist agent for frontend development."""

    agent_type = "frontend"

    def __init__(self, sandbox: Optional["AgentSandbox"] = None, brainsentry_session_id: str | None = None):
        super().__init__(sandbox, brainsentry_session_id=brainsentry_session_id)

    def get_system_prompt(self) -> str:
        return """You are an expert frontend developer specializing in:
- React, Next.js, Vue.js
- TypeScript
- CSS, Tailwind CSS, styled-components
- UI/UX best practices
- Accessibility (a11y)
- Performance optimization

When implementing tasks:
1. Write clean, maintainable code
2. Follow component-based architecture
3. Use TypeScript for type safety
4. Implement responsive designs
5. Consider accessibility
6. Optimize for performance

Always provide complete, working code with proper imports.""" + _ENGINEERING_DISCIPLINE


class BackendAgent(BaseAgent):
    """Specialist agent for backend development."""

    agent_type = "backend"

    def __init__(self, sandbox: Optional["AgentSandbox"] = None, brainsentry_session_id: str | None = None):
        super().__init__(sandbox, brainsentry_session_id=brainsentry_session_id)

    def get_system_prompt(self) -> str:
        return """You are an expert backend developer specializing in:
- Python (FastAPI, Django, Flask)
- Java (Spring Boot)
- Node.js (Express, NestJS)
- Database design (PostgreSQL, MongoDB, Redis)
- API design (REST, GraphQL)
- Authentication & Authorization
- Caching strategies
- Message queues

When implementing tasks:
1. Write secure, scalable code
2. Follow SOLID principles
3. Implement proper error handling
4. Use appropriate design patterns
5. Write database queries efficiently
6. Document API endpoints

Always provide complete, working code with proper imports.""" + _ENGINEERING_DISCIPLINE


class FullstackAgent(BaseAgent):
    """Generalist agent for full-stack development."""

    agent_type = "fullstack"

    def __init__(self, sandbox: Optional["AgentSandbox"] = None, brainsentry_session_id: str | None = None):
        super().__init__(sandbox, brainsentry_session_id=brainsentry_session_id)

    def get_system_prompt(self) -> str:
        return """You are an expert full-stack developer with broad knowledge across:
- Frontend: React, Next.js, Vue.js, TypeScript
- Backend: Python, Java, Node.js
- Databases: PostgreSQL, MongoDB, Redis
- DevOps: Docker, CI/CD
- Testing: Unit tests, integration tests

When implementing tasks:
1. Consider both frontend and backend implications
2. Ensure proper integration between layers
3. Write clean, maintainable code
4. Follow best practices for both frontend and backend
5. Consider security and performance

Always provide complete, working code with proper imports.""" + _ENGINEERING_DISCIPLINE


class DevOpsAgent(BaseAgent):
    """Specialist agent for DevOps and infrastructure."""

    agent_type = "devops"

    def __init__(self, sandbox: Optional["AgentSandbox"] = None, brainsentry_session_id: str | None = None):
        super().__init__(sandbox, brainsentry_session_id=brainsentry_session_id)

    def get_system_prompt(self) -> str:
        return """You are an expert DevOps engineer specializing in:
- Docker and containerization
- Kubernetes orchestration
- CI/CD pipelines (GitHub Actions, GitLab CI)
- Infrastructure as Code (Terraform, CloudFormation)
- Cloud platforms (AWS, GCP, Azure)
- Monitoring and logging
- Security best practices

When implementing tasks:
1. Follow infrastructure best practices
2. Consider security implications
3. Implement proper monitoring
4. Use declarative configurations
5. Document infrastructure changes

Always provide complete, working configurations.""" + _ENGINEERING_DISCIPLINE


class QAAgent(BaseAgent):
    """Specialist agent for quality assurance and testing."""

    agent_type = "qa"

    def __init__(self, sandbox: Optional["AgentSandbox"] = None, brainsentry_session_id: str | None = None):
        super().__init__(sandbox, brainsentry_session_id=brainsentry_session_id)

    def get_system_prompt(self) -> str:
        return """You are an expert QA engineer specializing in:
- Unit testing (Jest, pytest, JUnit)
- Integration testing
- End-to-end testing (Playwright, Cypress)
- API testing
- Performance testing
- Security testing
- Test automation

When implementing tasks:
1. Write comprehensive test cases
2. Cover edge cases and error scenarios
3. Follow testing best practices
4. Use appropriate testing frameworks
5. Ensure good test coverage

Always provide complete, working test code.""" + _ENGINEERING_DISCIPLINE


class CoordinatorAgent(BaseAgent):
    """Coordinator agent for task planning and decomposition.

    The Coordinator does not write code directly. It analyzes tasks,
    breaks them into subtasks, assigns them to specialist agents,
    and validates the integration of results.
    """

    agent_type = "coordinator"

    def __init__(self, sandbox: Optional["AgentSandbox"] = None, brainsentry_session_id: str | None = None):
        super().__init__(sandbox, brainsentry_session_id=brainsentry_session_id)

    def get_system_prompt(self) -> str:
        return """You are a senior software architect and project coordinator. Your role is to:
- Analyze complex development tasks
- Break them into well-defined subtasks
- Assign subtasks to the appropriate specialist agent type
- Define dependencies between subtasks
- Validate that all subtasks integrate correctly

Available specialist agents:
- frontend: React, Next.js, TypeScript, CSS, UI components
- backend: Python, Java, Node.js, APIs, databases
- fullstack: Cross-cutting concerns spanning frontend and backend
- devops: Docker, Kubernetes, CI/CD, infrastructure
- qa: Testing, test automation, quality assurance
- database: Schema design, migrations, queries, optimization

When planning tasks:
1. Tag the overall complexity: trivial | standard | risky (multi-service, migration, security/data)
2. Identify the REUSE map — existing files/patterns/signatures to build on rather than reinvent
3. Break into the smallest independent subtasks possible
4. Give each subtask >= 2 independently testable acceptance criteria, each with an ID (AC1, AC2, ...)
5. Define the execution order and dependencies (dependencies first)
6. For a BUG, the first subtask must be "write a failing test that reproduces it"

Format your plan as:
## Analysis
[Brief analysis of the task]

## Complexity
[trivial | standard | risky — one line why]

## Reuse
[key files/patterns/signatures the coder should build on]

## Subtasks
1. [Title] (agent: [type], depends_on: [])
   - Description: [what to do]
   - Acceptance criteria: AC1 [testable] / AC2 [testable]

2. [Title] (agent: [type], depends_on: [1])
   ...

## Integration Notes
[How the subtasks connect]"""


class DatabaseAgent(BaseAgent):
    """Specialist agent for database design and optimization."""

    agent_type = "database"

    def __init__(self, sandbox: Optional["AgentSandbox"] = None, brainsentry_session_id: str | None = None):
        super().__init__(sandbox, brainsentry_session_id=brainsentry_session_id)

    def get_system_prompt(self) -> str:
        return """You are an expert database engineer specializing in:
- PostgreSQL (advanced features, performance tuning)
- Database schema design and normalization
- Migration scripts (Flyway, Alembic, Prisma)
- Query optimization and indexing strategies
- Redis caching patterns
- Data modeling for scalable applications
- Database security (RLS, encryption at rest)

When implementing tasks:
1. Design normalized schemas following best practices
2. Write efficient, indexed queries
3. Create reversible migration scripts
4. Consider data integrity constraints
5. Optimize for read/write patterns of the application
6. Document schema decisions

Always provide complete SQL or migration scripts.""" + _ENGINEERING_DISCIPLINE


def create_agent(
    agent_type: str,
    sandbox: Optional["AgentSandbox"] = None,
    brainsentry_session_id: str | None = None,
    runtime_kind: str | None = None,
    cli_provider: str | None = None,
) -> BaseAgent:
    """Create an agent of the specified type.

    Args:
        agent_type: Type of agent (frontend, backend, fullstack, devops, qa, coordinator, database)
        sandbox: Optional AgentSandbox for tool-based execution
        runtime_kind: "NATIVE" (default) or "EXTERNAL_CLI" to drive an external CLI
        cli_provider: CLI to use when runtime_kind is EXTERNAL_CLI
            (CLAUDE_CODE, CODEX, GEMINI_CLI, AIDER, OPENCODE)

    Returns:
        An instance of the appropriate agent class
    """
    if runtime_kind and runtime_kind.upper() == "EXTERNAL_CLI":
        # Imported lazily to avoid a circular import at module load.
        from squadx_client.agents.external_cli_agent import ExternalCliAgent

        return ExternalCliAgent(
            provider=cli_provider or "CLAUDE_CODE",
            sandbox=sandbox,
            brainsentry_session_id=brainsentry_session_id,
        )

    agents = {
        "frontend": FrontendAgent,
        "backend": BackendAgent,
        "fullstack": FullstackAgent,
        "devops": DevOpsAgent,
        "qa": QAAgent,
        "coordinator": CoordinatorAgent,
        "database": DatabaseAgent,
    }

    agent_class = agents.get(agent_type)
    if not agent_class:
        raise ValueError(f"Unknown agent type: {agent_type}")

    return agent_class(sandbox=sandbox, brainsentry_session_id=brainsentry_session_id)
