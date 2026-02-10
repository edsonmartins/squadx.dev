"""Supabase client for live streaming signaling and session management."""

import asyncio
import json
import logging
from typing import Any, Callable, Optional
from dataclasses import dataclass

from supabase import create_async_client, AsyncClient, AsyncClientOptions

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

        self._client: Optional[AsyncClient] = None
        self._channels: dict[str, Any] = {}
        self._message_callbacks: dict[str, list[Callable[[RealtimeMessage], None]]] = {}

    async def get_client(self) -> AsyncClient:
        """Get or create the Supabase async client."""
        if self._client is None:
            self._client = await create_async_client(
                self.url,
                self.anon_key,
                options=AsyncClientOptions(
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
        client = await self.get_client()
        result = await client.rpc(
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
        client = await self.get_client()
        result = await (
            client.table("live_sessions")
            .select("*")
            .eq("id", session_id)
            .maybe_single()
            .execute()
        )
        return result.data if result else None

    async def get_session_by_code(self, join_code: str) -> Optional[dict[str, Any]]:
        """Get session by join code."""
        client = await self.get_client()
        result = await (
            client.table("live_sessions")
            .select("*")
            .eq("join_code", join_code)
            .maybe_single()
            .execute()
        )
        return result.data if result else None

    async def get_session_by_task(self, task_id: int) -> Optional[dict[str, Any]]:
        """Get active session for a task."""
        client = await self.get_client()
        result = await (
            client.table("live_sessions")
            .select("*")
            .eq("task_id", task_id)
            .eq("status", "active")
            .maybe_single()
            .execute()
        )
        return result.data if result else None

    async def update_session_status(
        self,
        session_id: str,
        status: str,
    ) -> dict[str, Any]:
        """Update session status."""
        client = await self.get_client()
        result = await (
            client.table("live_sessions")
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
        client = await self.get_client()
        result = await (
            client.table("live_participants")
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
            client = await self.get_client()
            await client.table("live_participants").delete().eq(
                "id", participant_id
            ).execute()
            return True
        except Exception as e:
            logger.error(f"Failed to remove participant: {e}")
            return False

    async def get_participants(self, session_id: str) -> list[dict[str, Any]]:
        """Get all participants in a session."""
        client = await self.get_client()
        result = await (
            client.table("live_participants")
            .select("*")
            .eq("session_id", session_id)
            .is_("left_at", "null")
            .execute()
        )
        return result.data or []

    # Realtime signaling methods

    async def subscribe_to_session(
        self,
        join_code: str,
        on_message: Callable[[RealtimeMessage], None],
    ) -> None:
        """Subscribe to session realtime channel for WebRTC signaling.

        Args:
            join_code: Session join code (matches frontend channel name)
            on_message: Callback for received messages
        """
        # Use same channel name as frontend: live:${joinCode}
        channel_name = f"live:{join_code}"

        if channel_name in self._channels:
            logger.warning(f"Already subscribed to {channel_name}")
            return

        # Store callback
        if channel_name not in self._message_callbacks:
            self._message_callbacks[channel_name] = []
        self._message_callbacks[channel_name].append(on_message)

        # Create channel with broadcast config matching frontend
        client = await self.get_client()
        channel = client.channel(
            channel_name,
            {
                "config": {
                    "broadcast": {"self": False},
                    "presence": {"key": "host"},
                }
            }
        )

        def handle_broadcast(broadcast):
            """Handle webrtc-signal broadcast from frontend viewers.

            Args:
                broadcast: BroadcastPayload with {event, payload} structure
            """
            # Extract the actual signal data from nested structure
            # broadcast.payload = {'event': 'webrtc-signal', 'payload': {actual signal data}}
            raw_payload = broadcast.payload if hasattr(broadcast, 'payload') else broadcast

            # The signal data is nested under 'payload' key
            if isinstance(raw_payload, dict) and 'payload' in raw_payload:
                signal_data = raw_payload['payload']
            else:
                signal_data = raw_payload

            logger.debug(f"Received signal data: type={signal_data.get('type')}, sender={signal_data.get('sender_id')}")

            msg = RealtimeMessage(
                event="webrtc-signal",
                payload=signal_data,
                channel=channel_name,
            )
            for callback in self._message_callbacks.get(channel_name, []):
                try:
                    callback(msg)
                except Exception as e:
                    logger.error(f"Error in message callback: {e}")

        # Listen for webrtc-signal events (same as frontend)
        channel.on_broadcast("webrtc-signal", handle_broadcast)

        # Handle presence events
        def handle_presence_sync():
            state = channel.presence_state()
            peer_count = len(state) if state else 0
            logger.debug(f"Presence sync: {peer_count} peers in {channel_name}")

        def handle_presence_join(key, current_presences, new_presences):
            logger.debug(f"Peer joined: {key}")

        def handle_presence_leave(key, current_presences, left_presences):
            logger.debug(f"Peer left: {key}")

        channel.on_presence_sync(handle_presence_sync)
        channel.on_presence_join(handle_presence_join)
        channel.on_presence_leave(handle_presence_leave)

        # Subscribe and track our presence as "host"
        await channel.subscribe()
        await channel.track({"role": "host", "online": True})

        self._channels[channel_name] = channel
        logger.info(f"Subscribed to {channel_name} as host")

    async def unsubscribe_from_session(self, join_code: str) -> None:
        """Unsubscribe from session channel."""
        channel_name = f"live:{join_code}"

        if channel_name in self._channels:
            channel = self._channels[channel_name]
            try:
                await channel.untrack()
            except Exception as e:
                logger.warning(f"Error untracking presence: {e}")
            await channel.unsubscribe()
            del self._channels[channel_name]
            self._message_callbacks.pop(channel_name, None)
            logger.info(f"Unsubscribed from {channel_name}")

    async def send_signal(
        self,
        join_code: str,
        signal_type: str,
        data: dict[str, Any],
        sender_id: str = "host",
        target_id: Optional[str] = None,
    ) -> bool:
        """Send a WebRTC signaling message.

        Payload format matches frontend SupabaseSignaling:
        {
            type: 'offer' | 'answer' | 'ice-candidate',
            sender_id: string,
            target_id?: string,
            sdp?: string,
            candidate?: { candidate, sdpMid, sdpMLineIndex }
        }

        Args:
            join_code: Session join code
            signal_type: 'offer', 'answer', 'ice-candidate'
            data: Signal data (sdp or candidate)
            sender_id: ID of the sender (default: 'host')
            target_id: Optional target peer ID

        Returns:
            True if sent successfully
        """
        channel_name = f"live:{join_code}"

        if channel_name not in self._channels:
            logger.error(f"Not subscribed to {channel_name}")
            return False

        try:
            # Build payload matching frontend WebRTCSignal interface
            payload: dict[str, Any] = {
                "type": signal_type,
                "sender_id": sender_id,
            }

            if target_id:
                payload["target_id"] = target_id

            # Flatten data into payload (frontend expects flat structure)
            if "sdp" in data:
                payload["sdp"] = data["sdp"]
            if "candidate" in data:
                payload["candidate"] = data["candidate"]

            await self._channels[channel_name].send_broadcast(
                "webrtc-signal",
                payload,
            )
            logger.debug(f"Sent {signal_type} signal to {channel_name}")
            return True
        except Exception as e:
            logger.error(f"Failed to send signal: {e}")
            return False

    async def send_offer(
        self,
        join_code: str,
        sdp: str,
        target_id: Optional[str] = None,
    ) -> bool:
        """Send WebRTC offer."""
        return await self.send_signal(
            join_code,
            "offer",
            {"sdp": sdp},
            sender_id="host",
            target_id=target_id,
        )

    async def send_answer(
        self,
        join_code: str,
        sdp: str,
        target_id: str,
    ) -> bool:
        """Send WebRTC answer to specific peer."""
        return await self.send_signal(
            join_code,
            "answer",
            {"sdp": sdp},
            sender_id="host",
            target_id=target_id,
        )

    async def send_ice_candidate(
        self,
        join_code: str,
        candidate: dict[str, Any],
        target_id: Optional[str] = None,
    ) -> bool:
        """Send ICE candidate."""
        return await self.send_signal(
            join_code,
            "ice-candidate",
            {"candidate": candidate},
            sender_id="host",
            target_id=target_id,
        )

    async def close(self) -> None:
        """Close all channels and cleanup."""
        for channel_name, channel in list(self._channels.items()):
            try:
                await channel.unsubscribe()
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
