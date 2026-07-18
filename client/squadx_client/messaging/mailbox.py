"""Agent mailbox for inter-agent communication via SquadX API."""
import logging
from dataclasses import dataclass

logger = logging.getLogger(__name__)


@dataclass
class AgentMailboxMessage:
    id: int
    from_agent_id: int
    from_agent_name: str
    message_type: str
    content: str
    to_agent_id: int = 0
    to_agent_name: str = ""
    execution_id: int | None = None
    is_read: bool = False
    created_at: str = ""


class AgentMailbox:
    """Inter-agent messaging client."""

    def __init__(self, api_client, agent_id: int):
        self._api = api_client
        self._agent_id = agent_id
        self._cache: list[AgentMailboxMessage] = []

    async def send(
        self,
        to_agent_id: int,
        content: str,
        message_type: str = "TASK_UPDATE",
        execution_id: int | None = None,
    ) -> bool:
        try:
            await self._api.post(
                "/api/v1/agent-messages",
                {
                    "fromAgentId": self._agent_id,
                    "toAgentId": to_agent_id,
                    "messageType": message_type,
                    "content": content,
                    "executionId": execution_id,
                },
            )
            return True
        except Exception as e:
            logger.error(f"Failed to send message: {e}")
            return False

    async def broadcast(self, execution_id: int, content: str) -> bool:
        try:
            await self._api.post(
                "/api/v1/agent-messages/broadcast",
                {
                    "fromAgentId": self._agent_id,
                    "executionId": execution_id,
                    "content": content,
                },
            )
            return True
        except Exception as e:
            logger.error(f"Failed to broadcast: {e}")
            return False

    async def receive(self) -> list[AgentMailboxMessage]:
        try:
            response = await self._api.get(
                f"/api/v1/agent-messages/unread?agentId={self._agent_id}"
            )
            messages = [
                AgentMailboxMessage(
                    id=m["id"],
                    from_agent_id=m["fromAgentId"],
                    from_agent_name=m.get("fromAgentName", ""),
                    message_type=m["messageType"],
                    content=m["content"],
                    to_agent_id=m.get("toAgentId", 0),
                    to_agent_name=m.get("toAgentName", ""),
                    execution_id=m.get("executionId"),
                    is_read=m.get("isRead", False),
                    created_at=m.get("createdAt", ""),
                )
                for m in response.get("data", [])
            ]
            self._cache = messages
            return messages
        except Exception as e:
            logger.error(f"Failed to receive messages: {e}")
            return []

    async def mark_read(self, message_id: int) -> bool:
        try:
            await self._api.put(f"/api/v1/agent-messages/{message_id}/read")
            return True
        except Exception:
            return False

    async def mark_all_read(self) -> bool:
        try:
            await self._api.put(
                f"/api/v1/agent-messages/read-all?agentId={self._agent_id}"
            )
            return True
        except Exception:
            return False
