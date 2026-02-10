"""Live session manager for coordinating VNC→WebRTC streaming."""

import asyncio
import logging
from typing import Any, Callable, Optional
from dataclasses import dataclass, field
from enum import Enum

from squadx_client.live.supabase_client import (
    SupabaseClient,
    RealtimeMessage,
    get_supabase_client,
)

logger = logging.getLogger(__name__)


class SessionState(str, Enum):
    """Live session state."""

    CREATED = "created"
    CONNECTING = "connecting"
    STREAMING = "streaming"
    PAUSED = "paused"
    ENDED = "ended"
    ERROR = "error"


@dataclass
class LiveSession:
    """Represents an active live streaming session."""

    id: str
    task_id: int
    join_code: str
    mode: str  # 'p2p' or 'sfu'
    state: SessionState = SessionState.CREATED
    vnc_host: Optional[str] = None
    vnc_port: Optional[int] = None
    viewer_count: int = 0
    error: Optional[str] = None


@dataclass
class PeerConnection:
    """Represents a WebRTC peer connection."""

    peer_id: str
    display_name: str
    role: str  # 'viewer' or 'controller'
    state: str = "new"  # 'new', 'connecting', 'connected', 'disconnected'


class LiveSessionManager:
    """Manages live streaming sessions for agent observation.

    This class coordinates:
    1. Session creation/management via Supabase
    2. WebRTC signaling for peer connections
    3. VNC capture and streaming
    """

    def __init__(
        self,
        supabase_client: Optional[SupabaseClient] = None,
    ):
        self._client = supabase_client or get_supabase_client()
        self._sessions: dict[str, LiveSession] = {}
        self._peers: dict[str, dict[str, PeerConnection]] = {}  # session_id -> peer_id -> peer
        self._on_peer_join: Optional[Callable[[str, PeerConnection], None]] = None
        self._on_peer_leave: Optional[Callable[[str, str], None]] = None
        self._on_signal: Optional[Callable[[str, str, dict], None]] = None

    def on_peer_join(self, callback: Callable[[str, PeerConnection], None]):
        """Register callback for when a peer joins."""
        self._on_peer_join = callback

    def on_peer_leave(self, callback: Callable[[str, str], None]):
        """Register callback for when a peer leaves."""
        self._on_peer_leave = callback

    def on_signal(self, callback: Callable[[str, str, dict], None]):
        """Register callback for WebRTC signals (offer, answer, ice-candidate)."""
        self._on_signal = callback

    async def create_session(
        self,
        task_id: int,
        vnc_host: str = "localhost",
        vnc_port: int = 5900,
        mode: str = "p2p",
        max_viewers: int = 25,
    ) -> LiveSession:
        """Create a new live streaming session.

        Args:
            task_id: Task ID this session is for
            vnc_host: VNC server host (usually localhost for container)
            vnc_port: VNC server port
            mode: 'p2p' for direct connections, 'sfu' for server relay
            max_viewers: Maximum number of viewers

        Returns:
            LiveSession object
        """
        # Check if session already exists for this task
        existing = await self._client.get_session_by_task(task_id)
        if existing:
            logger.info(f"Reusing existing session for task {task_id}")
            session = LiveSession(
                id=existing["id"],
                task_id=task_id,
                join_code=existing["join_code"],
                mode=existing["mode"],
                state=SessionState(existing["status"]),
                vnc_host=vnc_host,
                vnc_port=vnc_port,
            )
            self._sessions[session.id] = session
            return session

        # Create new session
        result = await self._client.create_session(
            task_id=task_id,
            mode=mode,
            max_viewers=max_viewers,
        )

        session = LiveSession(
            id=result["id"],
            task_id=task_id,
            join_code=result["join_code"],
            mode=mode,
            state=SessionState.CREATED,
            vnc_host=vnc_host,
            vnc_port=vnc_port,
        )

        self._sessions[session.id] = session
        self._peers[session.id] = {}

        # Subscribe to session signaling
        self._client.subscribe_to_session(
            session.id,
            lambda msg: self._handle_signal_message(session.id, msg),
        )

        logger.info(
            f"Created live session {session.id} for task {task_id}, "
            f"join code: {session.join_code}"
        )

        return session

    async def start_streaming(self, session_id: str) -> bool:
        """Start streaming for a session.

        This should be called after the WebRTC bridge is ready.
        """
        session = self._sessions.get(session_id)
        if not session:
            logger.error(f"Session {session_id} not found")
            return False

        if session.state == SessionState.STREAMING:
            logger.warning(f"Session {session_id} already streaming")
            return True

        try:
            await self._client.update_session_status(session_id, "active")
            session.state = SessionState.STREAMING
            logger.info(f"Started streaming for session {session_id}")
            return True
        except Exception as e:
            logger.error(f"Failed to start streaming: {e}")
            session.state = SessionState.ERROR
            session.error = str(e)
            return False

    async def pause_streaming(self, session_id: str) -> bool:
        """Pause streaming for a session."""
        session = self._sessions.get(session_id)
        if not session:
            return False

        try:
            await self._client.update_session_status(session_id, "paused")
            session.state = SessionState.PAUSED
            logger.info(f"Paused streaming for session {session_id}")
            return True
        except Exception as e:
            logger.error(f"Failed to pause streaming: {e}")
            return False

    async def end_session(self, session_id: str) -> bool:
        """End a live session and cleanup."""
        session = self._sessions.get(session_id)
        if not session:
            return False

        try:
            # Unsubscribe from signaling
            self._client.unsubscribe_from_session(session_id)

            # End session in database
            await self._client.end_session(session_id)

            # Cleanup local state
            session.state = SessionState.ENDED
            self._peers.pop(session_id, None)
            self._sessions.pop(session_id, None)

            logger.info(f"Ended session {session_id}")
            return True
        except Exception as e:
            logger.error(f"Failed to end session: {e}")
            return False

    def get_session(self, session_id: str) -> Optional[LiveSession]:
        """Get a session by ID."""
        return self._sessions.get(session_id)

    def get_session_by_task(self, task_id: int) -> Optional[LiveSession]:
        """Get active session for a task."""
        for session in self._sessions.values():
            if session.task_id == task_id and session.state != SessionState.ENDED:
                return session
        return None

    def get_peers(self, session_id: str) -> list[PeerConnection]:
        """Get all peers for a session."""
        return list(self._peers.get(session_id, {}).values())

    def get_join_url(self, session: LiveSession, base_url: str = "") -> str:
        """Get the join URL for a session.

        Args:
            session: The session to get URL for
            base_url: Base URL of the frontend (e.g., 'http://localhost:3000')

        Returns:
            Full join URL
        """
        return f"{base_url}/live/{session.join_code}"

    # WebRTC Signaling

    async def send_offer(
        self,
        session_id: str,
        peer_id: str,
        sdp: str,
    ) -> bool:
        """Send WebRTC offer to a specific peer."""
        return await self._client.send_signal(
            session_id,
            "offer",
            {"sdp": sdp, "target_peer": peer_id},
            sender_id="host",
        )

    async def send_answer(
        self,
        session_id: str,
        peer_id: str,
        sdp: str,
    ) -> bool:
        """Send WebRTC answer to a specific peer."""
        return await self._client.send_signal(
            session_id,
            "answer",
            {"sdp": sdp, "target_peer": peer_id},
            sender_id="host",
        )

    async def send_ice_candidate(
        self,
        session_id: str,
        peer_id: str,
        candidate: dict[str, Any],
    ) -> bool:
        """Send ICE candidate to a specific peer."""
        return await self._client.send_signal(
            session_id,
            "ice-candidate",
            {"candidate": candidate, "target_peer": peer_id},
            sender_id="host",
        )

    def _handle_signal_message(
        self,
        session_id: str,
        message: RealtimeMessage,
    ):
        """Handle incoming signaling message."""
        try:
            signal_type = message.payload.get("type")
            sender_id = message.payload.get("sender_id")
            data = message.payload.get("data", {})

            # Ignore our own messages
            if sender_id == "host":
                return

            logger.debug(
                f"Received signal: type={signal_type}, sender={sender_id}"
            )

            if signal_type == "join":
                # New viewer joining
                peer = PeerConnection(
                    peer_id=sender_id,
                    display_name=data.get("display_name", "Viewer"),
                    role="viewer",
                    state="connecting",
                )
                self._peers.setdefault(session_id, {})[sender_id] = peer

                session = self._sessions.get(session_id)
                if session:
                    session.viewer_count = len(self._peers[session_id])

                if self._on_peer_join:
                    self._on_peer_join(session_id, peer)

            elif signal_type == "leave":
                # Viewer leaving
                if session_id in self._peers:
                    self._peers[session_id].pop(sender_id, None)

                    session = self._sessions.get(session_id)
                    if session:
                        session.viewer_count = len(self._peers[session_id])

                if self._on_peer_leave:
                    self._on_peer_leave(session_id, sender_id)

            elif signal_type in ("offer", "answer", "ice-candidate"):
                # WebRTC signaling
                if self._on_signal:
                    self._on_signal(session_id, signal_type, {
                        "sender_id": sender_id,
                        **data,
                    })

        except Exception as e:
            logger.error(f"Error handling signal message: {e}")

    async def cleanup(self):
        """Cleanup all sessions."""
        for session_id in list(self._sessions.keys()):
            await self.end_session(session_id)

        self._client.close()
        logger.info("Cleaned up LiveSessionManager")


# Global instance (lazy initialization)
_session_manager: Optional[LiveSessionManager] = None


def get_session_manager() -> LiveSessionManager:
    """Get the global session manager instance."""
    global _session_manager
    if _session_manager is None:
        _session_manager = LiveSessionManager()
    return _session_manager
