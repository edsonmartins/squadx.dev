#!/usr/bin/env python3
"""
E2E Test for Live Streaming WebRTC Signaling.

This script tests the complete flow:
1. Start Docker container with VNC
2. Create live session in Supabase
3. Start WebRTC bridge
4. Simulate viewer connection via Supabase Realtime
5. Verify offer/answer exchange

Usage:
    cd client
    source .venv/bin/activate
    python tests/test_e2e_live_streaming.py

    # Or with browser test:
    python tests/test_e2e_live_streaming.py --browser
"""

import asyncio
import argparse
import json
import logging
import os
import sys
import time
import uuid
from typing import Optional

# Add parent to path for imports
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from squadx_client.config import settings
from squadx_client.docker.manager import DockerManager, ContainerConfig
from squadx_client.live.supabase_client import SupabaseClient, RealtimeMessage
from squadx_client.live.session_manager import LiveSessionManager, get_session_manager
from squadx_client.streaming.vnc_client import VNCClient
from squadx_client.streaming.webrtc_bridge import WebRTCBridge

# Configure logging
logging.basicConfig(
    level=logging.DEBUG,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("e2e_test")

# Reduce noise from other loggers
logging.getLogger("aiortc").setLevel(logging.WARNING)
logging.getLogger("aioice").setLevel(logging.WARNING)


class MockViewer:
    """Simulates a browser viewer for testing WebRTC signaling."""

    def __init__(self, supabase_url: str, supabase_key: str):
        self.peer_id = f"test-viewer-{uuid.uuid4().hex[:8]}"
        self.supabase_url = supabase_url
        self.supabase_key = supabase_key
        self._client: Optional[SupabaseClient] = None
        self._channel = None
        self._received_signals: list[dict] = []
        self._offer_received = asyncio.Event()
        self._answer_received = asyncio.Event()

    async def connect(self, join_code: str):
        """Connect to live session as viewer."""
        from supabase import create_async_client, AsyncClientOptions

        client = await create_async_client(
            self.supabase_url,
            self.supabase_key,
            options=AsyncClientOptions(
                auto_refresh_token=True,
                persist_session=False,
            ),
        )

        channel_name = f"live:{join_code}"
        logger.info(f"[Viewer] Connecting to channel: {channel_name}")

        self._channel = client.channel(
            channel_name,
            {
                "config": {
                    "broadcast": {"self": False},
                    "presence": {"key": self.peer_id},
                }
            }
        )

        def handle_signal(broadcast):
            # Extract payload from BroadcastPayload object
            # Structure: broadcast.payload = {'event': 'webrtc-signal', 'payload': {actual data}}
            raw_payload = broadcast.payload if hasattr(broadcast, 'payload') else broadcast

            # The signal data is nested under 'payload' key
            if isinstance(raw_payload, dict) and 'payload' in raw_payload:
                payload = raw_payload['payload']
            else:
                payload = raw_payload

            logger.info(f"[Viewer] Received signal: {payload.get('type')} from {payload.get('sender_id')}")
            self._received_signals.append(payload)

            if payload.get("type") == "offer":
                self._offer_received.set()
            elif payload.get("type") == "answer":
                self._answer_received.set()

        self._channel.on_broadcast("webrtc-signal", handle_signal)

        def on_presence_sync():
            state = self._channel.presence_state()
            logger.info(f"[Viewer] Presence sync: {len(state) if state else 0} peers")
            for key, presences in (state or {}).items():
                for p in presences:
                    logger.info(f"  - {key}: {p}")

        self._channel.on_presence_sync(on_presence_sync)

        await self._channel.subscribe()
        await self._channel.track({"role": "viewer", "peer_id": self.peer_id})
        logger.info(f"[Viewer] Joined as {self.peer_id}")

    async def send_offer(self):
        """Send a mock WebRTC offer to the host."""
        if not self._channel:
            raise RuntimeError("Not connected")

        # This is a simplified SDP offer for testing
        mock_sdp = """v=0
o=- 0 0 IN IP4 127.0.0.1
s=-
t=0 0
a=group:BUNDLE 0
m=video 9 UDP/TLS/RTP/SAVPF 96
c=IN IP4 0.0.0.0
a=rtcp:9 IN IP4 0.0.0.0
a=ice-ufrag:test
a=ice-pwd:MOCK_ICE_PASSWORD_FOR_TESTING
a=fingerprint:sha-256 00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00
a=setup:actpass
a=mid:0
a=recvonly
a=rtcp-mux
a=rtpmap:96 VP8/90000
"""

        payload = {
            "type": "offer",
            "sender_id": self.peer_id,
            "sdp": mock_sdp,
        }

        logger.info(f"[Viewer] Sending offer...")
        await self._channel.send_broadcast("webrtc-signal", payload)

    async def send_ice_candidate(self):
        """Send a mock ICE candidate."""
        if not self._channel:
            raise RuntimeError("Not connected")

        payload = {
            "type": "ice-candidate",
            "sender_id": self.peer_id,
            "target_id": "host",
            "candidate": {
                "candidate": "candidate:1 1 UDP 2130706431 192.168.1.1 54321 typ host",
                "sdpMid": "0",
                "sdpMLineIndex": 0,
            },
        }

        logger.info(f"[Viewer] Sending ICE candidate...")
        await self._channel.send_broadcast("webrtc-signal", payload)

    async def wait_for_answer(self, timeout: float = 10.0) -> bool:
        """Wait for answer from host."""
        try:
            await asyncio.wait_for(self._answer_received.wait(), timeout=timeout)
            return True
        except asyncio.TimeoutError:
            return False

    async def disconnect(self):
        """Disconnect from session."""
        if self._channel:
            try:
                await self._channel.untrack()
                await self._channel.unsubscribe()
            except Exception as e:
                logger.warning(f"Error disconnecting viewer: {e}")

    @property
    def received_signals(self) -> list[dict]:
        return self._received_signals


async def test_signaling_only():
    """Test signaling without Docker container (uses mock)."""
    logger.info("=" * 60)
    logger.info("Testing WebRTC Signaling (No Docker)")
    logger.info("=" * 60)

    supabase_url = settings.supabase_url
    supabase_key = settings.supabase_anon_key

    if not supabase_url or not supabase_key:
        logger.error("SUPABASE_URL and SUPABASE_ANON_KEY required")
        return False

    # Create session manager
    session_manager = get_session_manager()

    # Use random task ID to avoid conflicts
    import random
    task_id = random.randint(100000, 999999)

    # Create a test session
    logger.info(f"Creating live session for task {task_id}...")
    session = await session_manager.create_session(
        task_id=task_id,
        mode="p2p",
        max_viewers=5,
    )
    logger.info(f"Session created: {session.join_code}")

    # Track received signals on host side
    host_signals: list[dict] = []

    def on_signal(session_id: str, signal_type: str, data: dict):
        logger.info(f"[Host] Received {signal_type} from {data.get('sender_id')}")
        host_signals.append({"type": signal_type, **data})

    session_manager.on_signal(on_signal)

    # Start streaming (simulates WebRTC bridge ready)
    await session_manager.start_streaming(session.id)

    # Create mock viewer
    viewer = MockViewer(supabase_url, supabase_key)

    try:
        # Connect viewer
        await viewer.connect(session.join_code)
        await asyncio.sleep(1)  # Let presence sync

        # Viewer sends offer
        await viewer.send_offer()
        await asyncio.sleep(0.5)

        # Viewer sends ICE candidate
        await viewer.send_ice_candidate()
        await asyncio.sleep(1)

        # Check results
        logger.info("-" * 40)
        logger.info("Results:")
        logger.info(f"  Host received {len(host_signals)} signals")
        for sig in host_signals:
            logger.info(f"    - {sig['type']} from {sig.get('sender_id')}")

        logger.info(f"  Viewer received {len(viewer.received_signals)} signals")
        for sig in viewer.received_signals:
            logger.info(f"    - {sig['type']} from {sig.get('sender_id')}")

        # Verify
        has_offer = any(s["type"] == "offer" for s in host_signals)
        has_ice = any(s["type"] == "ice-candidate" for s in host_signals)

        if has_offer and has_ice:
            logger.info("✅ Signaling test PASSED!")
            return True
        else:
            logger.error("❌ Signaling test FAILED - host didn't receive all signals")
            return False

    finally:
        await viewer.disconnect()
        await session_manager.end_session(session.id)
        await session_manager.cleanup()


async def test_full_e2e(open_browser: bool = False):
    """Full E2E test with Docker container and VNC."""
    logger.info("=" * 60)
    logger.info("Full E2E Test: Docker + VNC + WebRTC")
    logger.info("=" * 60)

    supabase_url = settings.supabase_url
    supabase_key = settings.supabase_anon_key

    if not supabase_url or not supabase_key:
        logger.error("SUPABASE_URL and SUPABASE_ANON_KEY required")
        return False

    # Create Docker manager
    docker_mgr = DockerManager()
    if not docker_mgr.connect():
        logger.error("Failed to connect to Docker")
        return False

    task_id = 8888
    container_id = None
    join_code = None

    try:
        # Create container config
        config = ContainerConfig(
            image="squadx/agent:live",  # Image with VNC support
            enable_vnc=True,
            resolution="1280x720x24",
        )

        # Create container
        logger.info("Creating Docker container with VNC...")
        container_id = await docker_mgr.create_container(
            config=config,
            task_id=task_id,
            agent_type="test",
        )

        if not container_id:
            logger.error("Failed to create container")
            return False

        logger.info(f"Container ID: {container_id[:12]}")

        # Start container with live streaming
        success, join_code = await docker_mgr.start_container_with_live(
            container_id=container_id,
            task_id=task_id,
            enable_live=True,
        )

        if not success:
            logger.error("Failed to start container")
            return False

        if not join_code:
            logger.error("No join code generated!")
            return False

        logger.info(f"Join Code: {join_code}")

        # Wait for VNC to be ready
        logger.info("Waiting for VNC to be ready...")
        await asyncio.sleep(3)

        if open_browser:
            # Open browser for manual testing
            import webbrowser
            url = f"http://localhost:3000/live/{join_code}"
            logger.info(f"Opening browser: {url}")
            webbrowser.open(url)

            logger.info("-" * 40)
            logger.info("Manual test mode - check browser console for WebRTC logs")
            logger.info("Press Ctrl+C to stop...")

            try:
                while True:
                    await asyncio.sleep(1)
            except KeyboardInterrupt:
                pass

            return True

        else:
            # Automated test with mock viewer
            viewer = MockViewer(supabase_url, supabase_key)

            try:
                await viewer.connect(join_code)
                await asyncio.sleep(2)

                # Send offer
                await viewer.send_offer()

                # Wait for answer
                logger.info("Waiting for answer from host...")
                got_answer = await viewer.wait_for_answer(timeout=15)

                if got_answer:
                    logger.info("✅ Received answer from host!")

                    # Check for any ICE candidates from host
                    ice_candidates = [
                        s for s in viewer.received_signals
                        if s.get("type") == "ice-candidate"
                    ]
                    logger.info(f"  Received {len(ice_candidates)} ICE candidates from host")

                    return True
                else:
                    logger.error("❌ Timeout waiting for answer")
                    logger.info("Received signals:")
                    for sig in viewer.received_signals:
                        logger.info(f"  - {sig}")
                    return False

            finally:
                await viewer.disconnect()

    finally:
        logger.info("Cleaning up...")
        if container_id:
            await docker_mgr.stop_container_with_live(container_id)
        docker_mgr.disconnect()


async def main():
    parser = argparse.ArgumentParser(description="E2E Live Streaming Test")
    parser.add_argument(
        "--browser",
        action="store_true",
        help="Open browser for manual testing",
    )
    parser.add_argument(
        "--signaling-only",
        action="store_true",
        help="Test signaling only (no Docker)",
    )
    args = parser.parse_args()

    if args.signaling_only:
        success = await test_signaling_only()
    else:
        success = await test_full_e2e(open_browser=args.browser)

    sys.exit(0 if success else 1)


if __name__ == "__main__":
    asyncio.run(main())
