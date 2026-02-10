"""Agent factory for creating specialist agents."""

from typing import Optional, TYPE_CHECKING

from squadx_client.agents.base import BaseAgent

if TYPE_CHECKING:
    from squadx_client.docker.sandbox import AgentSandbox


class FrontendAgent(BaseAgent):
    """Specialist agent for frontend development."""

    agent_type = "frontend"

    def __init__(self, sandbox: Optional["AgentSandbox"] = None):
        super().__init__(sandbox)

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

Always provide complete, working code with proper imports."""


class BackendAgent(BaseAgent):
    """Specialist agent for backend development."""

    agent_type = "backend"

    def __init__(self, sandbox: Optional["AgentSandbox"] = None):
        super().__init__(sandbox)

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

Always provide complete, working code with proper imports."""


class FullstackAgent(BaseAgent):
    """Generalist agent for full-stack development."""

    agent_type = "fullstack"

    def __init__(self, sandbox: Optional["AgentSandbox"] = None):
        super().__init__(sandbox)

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

Always provide complete, working code with proper imports."""


class DevOpsAgent(BaseAgent):
    """Specialist agent for DevOps and infrastructure."""

    agent_type = "devops"

    def __init__(self, sandbox: Optional["AgentSandbox"] = None):
        super().__init__(sandbox)

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

Always provide complete, working configurations."""


class QAAgent(BaseAgent):
    """Specialist agent for quality assurance and testing."""

    agent_type = "qa"

    def __init__(self, sandbox: Optional["AgentSandbox"] = None):
        super().__init__(sandbox)

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

Always provide complete, working test code."""


def create_agent(
    agent_type: str,
    sandbox: Optional["AgentSandbox"] = None,
) -> BaseAgent:
    """Create an agent of the specified type.

    Args:
        agent_type: Type of agent (frontend, backend, fullstack, devops, qa)
        sandbox: Optional AgentSandbox for tool-based execution

    Returns:
        An instance of the appropriate agent class
    """
    agents = {
        "frontend": FrontendAgent,
        "backend": BackendAgent,
        "fullstack": FullstackAgent,
        "devops": DevOpsAgent,
        "qa": QAAgent,
    }

    agent_class = agents.get(agent_type)
    if not agent_class:
        raise ValueError(f"Unknown agent type: {agent_type}")

    return agent_class(sandbox=sandbox)
