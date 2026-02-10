"""WebRTC bridge for VNC to browser streaming.

This module uses aiortc to create WebRTC peer connections and stream
VNC frames as video to web browsers.
"""

import asyncio
import fractions
import json
import logging
import time
from dataclasses import dataclass, field
from typing import Any, Callable, Optional

from aiortc import RTCPeerConnection, RTCSessionDescription, RTCIceCandidate
from aiortc.contrib.media import MediaRelay
from aiortc.mediastreams import MediaStreamTrack, VideoStreamTrack
from av import VideoFrame

from squadx_client.streaming.vnc_client import VNCClient, VNCFrame
from squadx_client.live.session_manager import LiveSessionManager, get_session_manager
from squadx_client.config import settings

logger = logging.getLogger(__name__)


class VNCVideoTrack(VideoStreamTrack):
    """Video track that sources frames from VNC client.

    This track captures frames from a VNC connection and converts
    them to WebRTC-compatible video frames.
    """

    kind = "video"

    def __init__(
        self,
        vnc_client: VNCClient,
        fps: int = 30,
    ):
        super().__init__()
        self.vnc_client = vnc_client
        self.fps = fps
        self._frame_time = 1.0 / fps
        self._start_time: Optional[float] = None
        self._frame_count = 0

        # Frame queue for buffering
        self._frame_queue: asyncio.Queue[VNCFrame] = asyncio.Queue(maxsize=5)
        self._capture_task: Optional[asyncio.Task] = None

    async def start_capture(self):
        """Start capturing frames from VNC."""
        if self._capture_task is not None:
            return

        async def capture_loop():
            try:
                async for frame in self.vnc_client.capture_frames(fps=self.fps):
                    try:
                        # Drop old frames if queue is full
                        if self._frame_queue.full():
                            try:
                                self._frame_queue.get_nowait()
                            except asyncio.QueueEmpty:
                                pass
                        await asyncio.wait_for(
                            self._frame_queue.put(frame),
                            timeout=0.1,
                        )
                    except asyncio.TimeoutError:
                        pass
            except asyncio.CancelledError:
                pass
            except Exception as e:
                logger.error(f"VNC capture error: {e}")

        self._capture_task = asyncio.create_task(capture_loop())
        logger.info("Started VNC capture for WebRTC track")

    async def stop_capture(self):
        """Stop capturing frames."""
        if self._capture_task:
            self._capture_task.cancel()
            try:
                await self._capture_task
            except asyncio.CancelledError:
                pass
            self._capture_task = None

    async def recv(self) -> VideoFrame:
        """Receive the next video frame.

        This is called by aiortc to get frames for the WebRTC stream.
        """
        if self._start_time is None:
            self._start_time = time.time()
            await self.start_capture()

        # Get frame from queue
        try:
            vnc_frame = await asyncio.wait_for(
                self._frame_queue.get(),
                timeout=self._frame_time * 2,
            )
        except asyncio.TimeoutError:
            # Return black frame if no VNC frame available
            vnc_frame = VNCFrame(
                width=self.vnc_client.width or 1920,
                height=self.vnc_client.height or 1080,
                data=bytes(self.vnc_client.width * self.vnc_client.height * 4),
                timestamp=time.time(),
            )

        # Convert to PIL Image
        img = self.vnc_client.frame_to_image(vnc_frame)

        # Create video frame
        frame = VideoFrame.from_image(img)
        frame.pts = self._frame_count
        frame.time_base = fractions.Fraction(1, self.fps)

        self._frame_count += 1

        return frame


@dataclass
class PeerConnectionInfo:
    """Information about a WebRTC peer connection."""

    peer_id: str
    pc: RTCPeerConnection
    video_track: Optional[VNCVideoTrack] = None
    data_channel: Optional[Any] = None
    state: str = "new"
    created_at: float = field(default_factory=time.time)


class WebRTCBridge:
    """Bridge between VNC and WebRTC for live streaming.

    This class manages WebRTC peer connections for streaming VNC
    content to web browsers.
    """

    def __init__(
        self,
        session_id: str,
        vnc_client: VNCClient,
        session_manager: Optional[LiveSessionManager] = None,
        fps: int = 30,
        ice_servers: Optional[list[dict]] = None,
    ):
        self.session_id = session_id
        self.vnc_client = vnc_client
        self.session_manager = session_manager or get_session_manager()
        self.fps = fps

        # Get ICE servers from settings if not provided
        self.ice_servers = ice_servers or settings.get_ice_servers()
        logger.info(f"WebRTC using {len(self.ice_servers)} ICE servers")

        self._peers: dict[str, PeerConnectionInfo] = {}
        self._relay = MediaRelay()
        self._video_track: Optional[VNCVideoTrack] = None
        self._running = False

        # Register signal handler
        self.session_manager.on_signal(self._handle_signal)

    async def start(self):
        """Start the WebRTC bridge."""
        if self._running:
            return

        # Create the video track
        self._video_track = VNCVideoTrack(self.vnc_client, fps=self.fps)

        self._running = True
        logger.info(f"WebRTC bridge started for session {self.session_id}")

    async def stop(self):
        """Stop the WebRTC bridge and close all connections."""
        self._running = False

        # Stop video track
        if self._video_track:
            await self._video_track.stop_capture()
            self._video_track = None

        # Close all peer connections
        for peer_info in list(self._peers.values()):
            await self._close_peer(peer_info.peer_id)

        logger.info(f"WebRTC bridge stopped for session {self.session_id}")

    async def create_offer(self, peer_id: str) -> str:
        """Create an SDP offer for a new peer.

        Args:
            peer_id: ID of the peer to connect

        Returns:
            SDP offer string
        """
        pc = RTCPeerConnection(
            configuration={"iceServers": self.ice_servers}
        )

        # Add video track
        if self._video_track:
            relayed_track = self._relay.subscribe(self._video_track)
            pc.addTrack(relayed_track)

        # Create data channel for control messages
        data_channel = pc.createDataChannel("control", ordered=True)

        # Store peer info
        peer_info = PeerConnectionInfo(
            peer_id=peer_id,
            pc=pc,
            video_track=self._video_track,
            data_channel=data_channel,
            state="creating-offer",
        )
        self._peers[peer_id] = peer_info

        # Set up event handlers
        self._setup_peer_handlers(peer_info)

        # Create offer
        offer = await pc.createOffer()
        await pc.setLocalDescription(offer)

        # Wait for ICE gathering to complete (with timeout)
        await self._wait_for_ice_gathering(pc)

        peer_info.state = "offer-sent"

        return pc.localDescription.sdp

    async def handle_answer(self, peer_id: str, sdp: str):
        """Handle an SDP answer from a peer.

        Args:
            peer_id: ID of the peer
            sdp: SDP answer string
        """
        peer_info = self._peers.get(peer_id)
        if not peer_info:
            logger.error(f"Unknown peer: {peer_id}")
            return

        answer = RTCSessionDescription(sdp=sdp, type="answer")
        await peer_info.pc.setRemoteDescription(answer)
        peer_info.state = "connected"

        logger.info(f"Peer {peer_id} connected")

    async def handle_offer(self, peer_id: str, sdp: str) -> str:
        """Handle an SDP offer from a peer (viewer-initiated).

        Args:
            peer_id: ID of the peer
            sdp: SDP offer string

        Returns:
            SDP answer string
        """
        pc = RTCPeerConnection(
            configuration={"iceServers": self.ice_servers}
        )

        # Add video track
        if self._video_track:
            relayed_track = self._relay.subscribe(self._video_track)
            pc.addTrack(relayed_track)

        # Store peer info
        peer_info = PeerConnectionInfo(
            peer_id=peer_id,
            pc=pc,
            video_track=self._video_track,
            state="handling-offer",
        )
        self._peers[peer_id] = peer_info

        # Set up event handlers
        self._setup_peer_handlers(peer_info)

        # Handle offer
        offer = RTCSessionDescription(sdp=sdp, type="offer")
        await pc.setRemoteDescription(offer)

        # Create answer
        answer = await pc.createAnswer()
        await pc.setLocalDescription(answer)

        # Wait for ICE gathering
        await self._wait_for_ice_gathering(pc)

        peer_info.state = "connected"

        return pc.localDescription.sdp

    async def handle_ice_candidate(
        self,
        peer_id: str,
        candidate: dict[str, Any],
    ):
        """Handle an ICE candidate from a peer.

        Args:
            peer_id: ID of the peer
            candidate: ICE candidate dict
        """
        peer_info = self._peers.get(peer_id)
        if not peer_info:
            logger.warning(f"ICE candidate for unknown peer: {peer_id}")
            return

        try:
            ice_candidate = RTCIceCandidate(
                sdpMid=candidate.get("sdpMid"),
                sdpMLineIndex=candidate.get("sdpMLineIndex"),
                candidate=candidate.get("candidate"),
            )
            await peer_info.pc.addIceCandidate(ice_candidate)
        except Exception as e:
            logger.error(f"Error adding ICE candidate: {e}")

    async def _wait_for_ice_gathering(
        self,
        pc: RTCPeerConnection,
        timeout: float = 5.0,
    ):
        """Wait for ICE gathering to complete."""
        if pc.iceGatheringState == "complete":
            return

        done = asyncio.Event()

        @pc.on("icegatheringstatechange")
        def on_gathering_state_change():
            if pc.iceGatheringState == "complete":
                done.set()

        try:
            await asyncio.wait_for(done.wait(), timeout=timeout)
        except asyncio.TimeoutError:
            logger.warning("ICE gathering timed out")

    def _setup_peer_handlers(self, peer_info: PeerConnectionInfo):
        """Set up event handlers for a peer connection."""
        pc = peer_info.pc
        peer_id = peer_info.peer_id

        @pc.on("iceconnectionstatechange")
        async def on_ice_state_change():
            logger.debug(f"ICE state for {peer_id}: {pc.iceConnectionState}")
            if pc.iceConnectionState in ("failed", "disconnected", "closed"):
                await self._close_peer(peer_id)

        @pc.on("connectionstatechange")
        async def on_connection_state_change():
            logger.debug(f"Connection state for {peer_id}: {pc.connectionState}")
            if pc.connectionState in ("failed", "closed"):
                await self._close_peer(peer_id)

        @pc.on("icecandidate")
        async def on_ice_candidate(candidate):
            if candidate:
                # Send ICE candidate to peer via signaling
                await self.session_manager.send_ice_candidate(
                    self.session_id,
                    peer_id,
                    {
                        "candidate": candidate.candidate,
                        "sdpMid": candidate.sdpMid,
                        "sdpMLineIndex": candidate.sdpMLineIndex,
                    },
                )

        @pc.on("datachannel")
        def on_data_channel(channel):
            peer_info.data_channel = channel

            @channel.on("message")
            def on_message(message):
                self._handle_data_message(peer_id, message)

    def _handle_data_message(self, peer_id: str, message: str):
        """Handle a message from the data channel.

        Args:
            peer_id: ID of the sender
            message: Message content (JSON string)
        """
        try:
            data = json.loads(message)
            msg_type = data.get("type")

            if msg_type == "ping":
                # Respond with pong
                peer_info = self._peers.get(peer_id)
                if peer_info and peer_info.data_channel:
                    peer_info.data_channel.send(json.dumps({
                        "type": "pong",
                        "timestamp": time.time(),
                    }))

            elif msg_type == "input":
                # Handle input event (mouse, keyboard)
                event = data.get("event", {})
                # Schedule async input handling
                asyncio.create_task(self._process_input_event(peer_id, event))

            elif msg_type == "control-request":
                # Viewer requesting control
                logger.info(f"Control requested by {peer_id}")
                self._grant_control(peer_id)

            elif msg_type == "control-release":
                # Viewer releasing control
                logger.info(f"Control released by {peer_id}")
                self._revoke_control(peer_id)

        except Exception as e:
            logger.error(f"Error handling data message: {e}")

    async def _process_input_event(self, peer_id: str, event: dict[str, Any]):
        """Process an input event from a viewer.

        Args:
            peer_id: ID of the viewer
            event: Input event data
        """
        from squadx_client.streaming.vnc_client import js_key_to_keysym

        event_type = event.get("type")
        if not event_type:
            return

        # Check if peer has control
        peer_info = self._peers.get(peer_id)
        if not peer_info or not getattr(peer_info, "has_control", False):
            logger.debug(f"Ignoring input from {peer_id} - no control granted")
            return

        try:
            if event_type == "mousemove":
                # Convert relative position to absolute
                x = int(event.get("x", 0) * self.vnc_client.width)
                y = int(event.get("y", 0) * self.vnc_client.height)
                await self.vnc_client.send_mouse_move(x, y)

            elif event_type == "mousedown":
                x = int(event.get("x", 0) * self.vnc_client.width)
                y = int(event.get("y", 0) * self.vnc_client.height)
                button = event.get("button", "left")
                await self.vnc_client.send_mouse_click(x, y, button, down=True)

            elif event_type == "mouseup":
                x = int(event.get("x", 0) * self.vnc_client.width)
                y = int(event.get("y", 0) * self.vnc_client.height)
                button = event.get("button", "left")
                await self.vnc_client.send_mouse_click(x, y, button, down=False)

            elif event_type == "wheel":
                x = int(event.get("x", 0) * self.vnc_client.width)
                y = int(event.get("y", 0) * self.vnc_client.height)
                delta_y = event.get("deltaY", 0)
                # Normalize scroll direction
                if delta_y != 0:
                    scroll_dir = 1 if delta_y < 0 else -1
                    await self.vnc_client.send_mouse_scroll(x, y, scroll_dir)

            elif event_type == "keydown":
                key = event.get("key", "")
                keysym = js_key_to_keysym(key)
                if keysym:
                    await self.vnc_client.send_key_event(keysym, down=True)

            elif event_type == "keyup":
                key = event.get("key", "")
                keysym = js_key_to_keysym(key)
                if keysym:
                    await self.vnc_client.send_key_event(keysym, down=False)

        except Exception as e:
            logger.error(f"Error processing input event: {e}")

    def _grant_control(self, peer_id: str):
        """Grant control to a viewer.

        Args:
            peer_id: ID of the viewer to grant control
        """
        # Revoke control from any other peer first
        for pid, peer_info in self._peers.items():
            if getattr(peer_info, "has_control", False):
                peer_info.has_control = False
                if peer_info.data_channel:
                    try:
                        peer_info.data_channel.send(json.dumps({
                            "type": "control-revoked",
                            "timestamp": time.time(),
                        }))
                    except Exception:
                        pass

        # Grant control to the requesting peer
        peer_info = self._peers.get(peer_id)
        if peer_info:
            peer_info.has_control = True
            if peer_info.data_channel:
                try:
                    peer_info.data_channel.send(json.dumps({
                        "type": "control-granted",
                        "timestamp": time.time(),
                    }))
                except Exception:
                    pass
            logger.info(f"Control granted to {peer_id}")

    def _revoke_control(self, peer_id: str):
        """Revoke control from a viewer.

        Args:
            peer_id: ID of the viewer to revoke control from
        """
        peer_info = self._peers.get(peer_id)
        if peer_info:
            peer_info.has_control = False
            logger.info(f"Control revoked from {peer_id}")

    async def _handle_signal(
        self,
        session_id: str,
        signal_type: str,
        data: dict[str, Any],
    ):
        """Handle incoming WebRTC signals.

        Args:
            session_id: Session ID
            signal_type: Signal type (offer, answer, ice-candidate)
            data: Signal data
        """
        if session_id != self.session_id:
            return

        sender_id = data.get("sender_id")
        if not sender_id:
            return

        try:
            if signal_type == "offer":
                # Viewer is initiating connection
                sdp = data.get("sdp")
                if sdp:
                    answer_sdp = await self.handle_offer(sender_id, sdp)
                    await self.session_manager.send_answer(
                        self.session_id,
                        sender_id,
                        answer_sdp,
                    )

            elif signal_type == "answer":
                # Viewer responding to our offer
                sdp = data.get("sdp")
                if sdp:
                    await self.handle_answer(sender_id, sdp)

            elif signal_type == "ice-candidate":
                # ICE candidate from viewer
                candidate = data.get("candidate")
                if candidate:
                    await self.handle_ice_candidate(sender_id, candidate)

        except Exception as e:
            logger.error(f"Error handling signal: {e}")

    async def _close_peer(self, peer_id: str):
        """Close a peer connection.

        Args:
            peer_id: ID of the peer to close
        """
        peer_info = self._peers.pop(peer_id, None)
        if peer_info:
            try:
                await peer_info.pc.close()
            except Exception as e:
                logger.error(f"Error closing peer {peer_id}: {e}")

            logger.info(f"Peer {peer_id} disconnected")

    @property
    def peer_count(self) -> int:
        """Get the number of connected peers."""
        return len(self._peers)

    def get_stats(self) -> dict[str, Any]:
        """Get bridge statistics."""
        return {
            "session_id": self.session_id,
            "peer_count": self.peer_count,
            "running": self._running,
            "fps": self.fps,
            "peers": [
                {
                    "peer_id": p.peer_id,
                    "state": p.state,
                    "connection_state": p.pc.connectionState,
                }
                for p in self._peers.values()
            ],
        }


async def create_live_stream(
    task_id: int,
    vnc_host: str = "localhost",
    vnc_port: int = 5900,
    fps: int = 30,
) -> tuple[WebRTCBridge, str]:
    """Create a complete live streaming setup.

    Args:
        task_id: Task ID to stream
        vnc_host: VNC server host
        vnc_port: VNC server port
        fps: Target frames per second

    Returns:
        Tuple of (WebRTCBridge, join_code)
    """
    # Create VNC client
    vnc_client = VNCClient(host=vnc_host, port=vnc_port)
    if not await vnc_client.connect():
        raise RuntimeError(f"Failed to connect to VNC at {vnc_host}:{vnc_port}")

    # Create live session
    session_manager = get_session_manager()
    session = await session_manager.create_session(
        task_id=task_id,
        vnc_host=vnc_host,
        vnc_port=vnc_port,
    )

    # Create WebRTC bridge
    bridge = WebRTCBridge(
        session_id=session.id,
        vnc_client=vnc_client,
        session_manager=session_manager,
        fps=fps,
    )

    await bridge.start()
    await session_manager.start_streaming(session.id)

    logger.info(
        f"Live stream created for task {task_id}, "
        f"join code: {session.join_code}"
    )

    return bridge, session.join_code
