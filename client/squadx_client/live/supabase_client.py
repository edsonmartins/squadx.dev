"""Supabase client for live streaming signaling and session management."""

import asyncio
import json
import logging
from typing import Any, Callable, Optional
from dataclasses import dataclass

from supabase import create_client, Client
from supabase.lib.client_options import ClientOptions

from squadx_client.config import settings

logger = logging.getLogger(__name__)


@dataclass
class RealtimeMessage:
    """Message received from Supabase Realtime."""

    event: str
    payload: dict[str, Any]
    channel: str


class SupabaseClient:
    """Client for Supabase operations including Realtime signaling."""

    def __init__(
        self,
        url: Optional[str] = None,
        anon_key: Optional[str] = None,
    ):
        self.url = url or getattr(settings, "supabase_url", None)
        self.anon_key = anon_key or getattr(settings, "supabase_anon_key", None)

        if not self.url or not self.anon_key:
            raise ValueError(
                "Supabase URL and anon key are required. "
                "Set SUPABASE_URL and SUPABASE_ANON_KEY environment variables."
            )

        self._client: Optional[Client] = None
        self._channels: dict[str, Any] = {}
        self._message_callbacks: dict[str, list[Callable[[RealtimeMessage], None]]] = {}

    @property
    def client(self) -> Client:
        """Get or create the Supabase client."""
        if self._client is None:
            self._client = create_client(
                self.url,
                self.anon_key,
                options=ClientOptions(
                    auto_refresh_token=True,
                    persist_session=False,
                ),
            )
        return self._client

    async def create_session(
        self,
        task_id: int,
        host_user_id: Optional[str] = None,
        mode: str = "p2p",
        max_viewers: int = 25,
    ) -> dict[str, Any]:
        """Create a new live session.

        Args:
            task_id: The task ID this session is for
            host_user_id: Optional user ID of the host
            mode: 'p2p' or 'sfu'
            max_viewers: Maximum number of viewers

        Returns:
            Session data including id and join_code
        """
        result = self.client.rpc(
            "create_live_session",
            {
                "p_task_id": task_id,
                "p_host_user_id": host_user_id,
                "p_mode": mode,
                "p_max_viewers": max_viewers,
            },
        ).execute()

        if result.data:
            logger.info(f"Created live session for task {task_id}: {result.data}")
            return result.data
        raise Exception(f"Failed to create session: {result}")

    async def get_session(self, session_id: str) -> Optional[dict[str, Any]]:
        """Get session by ID."""
        result = (
            self.client.table("live_sessions")
            .select("*")
            .eq("id", session_id)
            .single()
            .execute()
        )
        return result.data

    async def get_session_by_code(self, join_code: str) -> Optional[dict[str, Any]]:
        """Get session by join code."""
        result = (
            self.client.table("live_sessions")
            .select("*")
            .eq("join_code", join_code)
            .single()
            .execute()
        )
        return result.data

    async def get_session_by_task(self, task_id: int) -> Optional[dict[str, Any]]:
        """Get active session for a task."""
        result = (
            self.client.table("live_sessions")
            .select("*")
            .eq("task_id", task_id)
            .eq("status", "active")
            .single()
            .execute()
        )
        return result.data

    async def update_session_status(
        self,
        session_id: str,
        status: str,
    ) -> dict[str, Any]:
        """Update session status."""
        result = (
            self.client.table("live_sessions")
            .update({"status": status})
            .eq("id", session_id)
            .execute()
        )
        return result.data[0] if result.data else {}

    async def end_session(self, session_id: str) -> bool:
        """End a live session."""
        try:
            await self.update_session_status(session_id, "ended")
            logger.info(f"Ended live session: {session_id}")
            return True
        except Exception as e:
            logger.error(f"Failed to end session: {e}")
            return False

    async def add_participant(
        self,
        session_id: str,
        user_id: Optional[str] = None,
        display_name: str = "Viewer",
        role: str = "viewer",
    ) -> dict[str, Any]:
        """Add a participant to the session."""
        result = (
            self.client.table("live_participants")
            .insert({
                "session_id": session_id,
                "user_id": user_id,
                "display_name": display_name,
                "role": role,
                "control_state": "view-only",
            })
            .execute()
        )
        return result.data[0] if result.data else {}

    async def remove_participant(self, participant_id: str) -> bool:
        """Remove a participant from the session."""
        try:
            self.client.table("live_participants").delete().eq(
                "id", participant_id
            ).execute()
            return True
        except Exception as e:
            logger.error(f"Failed to remove participant: {e}")
            return False

    async def get_participants(self, session_id: str) -> list[dict[str, Any]]:
        """Get all participants in a session."""
        result = (
            self.client.table("live_participants")
            .select("*")
            .eq("session_id", session_id)
            .is_("left_at", "null")
            .execute()
        )
        return result.data or []

    # Realtime signaling methods

    def subscribe_to_session(
        self,
        session_id: str,
        on_message: Callable[[RealtimeMessage], None],
    ) -> None:
        """Subscribe to session realtime channel for WebRTC signaling.

        Args:
            session_id: Session to subscribe to
            on_message: Callback for received messages
        """
        channel_name = f"session:{session_id}"

        if channel_name in self._channels:
            logger.warning(f"Already subscribed to {channel_name}")
            return

        # Store callback
        if channel_name not in self._message_callbacks:
            self._message_callbacks[channel_name] = []
        self._message_callbacks[channel_name].append(on_message)

        # Create channel
        channel = self.client.channel(channel_name)

        def handle_broadcast(payload: dict):
            msg = RealtimeMessage(
                event=payload.get("event", "unknown"),
                payload=payload.get("payload", {}),
                channel=channel_name,
            )
            for callback in self._message_callbacks.get(channel_name, []):
                try:
                    callback(msg)
                except Exception as e:
                    logger.error(f"Error in message callback: {e}")

        channel.on_broadcast("signal", handle_broadcast)
        channel.subscribe()

        self._channels[channel_name] = channel
        logger.info(f"Subscribed to {channel_name}")

    def unsubscribe_from_session(self, session_id: str) -> None:
        """Unsubscribe from session channel."""
        channel_name = f"session:{session_id}"

        if channel_name in self._channels:
            self._channels[channel_name].unsubscribe()
            del self._channels[channel_name]
            self._message_callbacks.pop(channel_name, None)
            logger.info(f"Unsubscribed from {channel_name}")

    async def send_signal(
        self,
        session_id: str,
        signal_type: str,
        data: dict[str, Any],
        sender_id: Optional[str] = None,
    ) -> bool:
        """Send a WebRTC signaling message.

        Args:
            session_id: Target session
            signal_type: 'offer', 'answer', 'ice-candidate', etc.
            data: Signal data (SDP, ICE candidate, etc.)
            sender_id: ID of the sender

        Returns:
            True if sent successfully
        """
        channel_name = f"session:{session_id}"

        if channel_name not in self._channels:
            logger.error(f"Not subscribed to {channel_name}")
            return False

        try:
            self._channels[channel_name].send_broadcast(
                "signal",
                {
                    "type": signal_type,
                    "sender_id": sender_id,
                    "data": data,
                },
            )
            logger.debug(f"Sent {signal_type} signal to {channel_name}")
            return True
        except Exception as e:
            logger.error(f"Failed to send signal: {e}")
            return False

    async def send_offer(
        self,
        session_id: str,
        sdp: str,
        sender_id: Optional[str] = None,
    ) -> bool:
        """Send WebRTC offer."""
        return await self.send_signal(
            session_id,
            "offer",
            {"sdp": sdp},
            sender_id,
        )

    async def send_answer(
        self,
        session_id: str,
        sdp: str,
        sender_id: Optional[str] = None,
    ) -> bool:
        """Send WebRTC answer."""
        return await self.send_signal(
            session_id,
            "answer",
            {"sdp": sdp},
            sender_id,
        )

    async def send_ice_candidate(
        self,
        session_id: str,
        candidate: dict[str, Any],
        sender_id: Optional[str] = None,
    ) -> bool:
        """Send ICE candidate."""
        return await self.send_signal(
            session_id,
            "ice-candidate",
            {"candidate": candidate},
            sender_id,
        )

    def close(self) -> None:
        """Close all channels and cleanup."""
        for channel_name, channel in list(self._channels.items()):
            try:
                channel.unsubscribe()
            except Exception as e:
                logger.error(f"Error unsubscribing from {channel_name}: {e}")

        self._channels.clear()
        self._message_callbacks.clear()
        logger.info("Closed Supabase client")


# Global instance (lazy initialization)
_supabase_client: Optional[SupabaseClient] = None


def get_supabase_client() -> SupabaseClient:
    """Get the global Supabase client instance."""
    global _supabase_client
    if _supabase_client is None:
        _supabase_client = SupabaseClient()
    return _supabase_client
