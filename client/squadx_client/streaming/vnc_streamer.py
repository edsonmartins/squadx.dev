"""VNC streaming service for Live View.

This module provides functionality to capture VNC output from agent sandboxes
and stream it via WebSocket to viewers in the dashboard.
"""

import asyncio
import io
import logging
import struct
from collections.abc import Callable
from dataclasses import dataclass, field
from enum import Enum
from typing import Any

from PIL import Image

from squadx_client.streaming.vnc_client import (
    RFBMessageType,
    VNCClient,
    VNCFrame,
)

logger = logging.getLogger(__name__)


class StreamStatus(str, Enum):
    """Status of the VNC stream."""

    DISCONNECTED = "disconnected"
    CONNECTING = "connecting"
    CONNECTED = "connected"
    STREAMING = "streaming"
    ERROR = "error"


@dataclass
class StreamConfig:
    """Configuration for VNC streaming."""

    vnc_host: str = "localhost"
    vnc_port: int = 5900
    vnc_password: str = ""
    quality: int = 80  # JPEG quality 1-100
    fps: int = 10  # Frames per second
    resize_width: int | None = 1280
    resize_height: int | None = 720


@dataclass
class StreamSession:
    """Represents an active streaming session."""

    session_id: str
    task_id: int
    config: StreamConfig
    status: StreamStatus = StreamStatus.DISCONNECTED
    viewers: set = field(default_factory=set)
    frame_count: int = 0
    bytes_sent: int = 0


class VNCStreamer:
    """VNC to WebSocket streamer.

    Connects to a VNC server running in an agent sandbox and streams
    the screen contents to WebSocket viewers.
    """

    def __init__(
        self,
        session_id: str,
        task_id: int,
        config: StreamConfig | None = None,
    ):
        self.session_id = session_id
        self.task_id = task_id
        self.config = config or StreamConfig()

        self.session = StreamSession(
            session_id=session_id,
            task_id=task_id,
            config=self.config,
        )

        self._vnc_client: VNCClient | None = None
        self._stream_task: asyncio.Task | None = None
        self._running = False

        # Callbacks
        self._on_frame: Callable[[bytes], Any] | None = None
        self._on_status_change: Callable[[StreamStatus], Any] | None = None
        self._on_error: Callable[[Exception], Any] | None = None

    def on_frame(self, callback: Callable[[bytes], Any]):
        """Register callback for frame data (JPEG bytes)."""
        self._on_frame = callback

    def on_status_change(self, callback: Callable[[StreamStatus], Any]):
        """Register callback for status changes."""
        self._on_status_change = callback

    def on_error(self, callback: Callable[[Exception], Any]):
        """Register callback for errors."""
        self._on_error = callback

    def _set_status(self, status: StreamStatus):
        """Update status and notify callback."""
        self.session.status = status
        if self._on_status_change:
            try:
                self._on_status_change(status)
            except Exception as e:
                logger.error(f"Status callback error: {e}")

    async def start(self) -> bool:
        """Start the VNC streaming.

        Returns:
            True if streaming started successfully, False otherwise.
        """
        if self._running:
            logger.warning("Streamer already running")
            return True

        self._set_status(StreamStatus.CONNECTING)

        try:
            # Attempt to connect to VNC server
            connected = await self._connect_vnc()
            if not connected:
                self._set_status(StreamStatus.ERROR)
                return False

            self._set_status(StreamStatus.CONNECTED)

            # Start streaming loop
            self._running = True
            self._stream_task = asyncio.create_task(self._stream_loop())

            logger.info(
                f"VNC streaming started for session {self.session_id} "
                f"from {self.config.vnc_host}:{self.config.vnc_port}"
            )
            return True

        except Exception as e:
            logger.error(f"Failed to start VNC streaming: {e}")
            self._set_status(StreamStatus.ERROR)
            if self._on_error:
                self._on_error(e)
            return False

    async def stop(self):
        """Stop the VNC streaming."""
        self._running = False

        if self._stream_task:
            self._stream_task.cancel()
            try:
                await self._stream_task
            except asyncio.CancelledError:
                pass
            self._stream_task = None

        await self._disconnect_vnc()
        self._set_status(StreamStatus.DISCONNECTED)

        logger.info(
            f"VNC streaming stopped for session {self.session_id}. "
            f"Frames sent: {self.session.frame_count}, "
            f"Bytes sent: {self.session.bytes_sent}"
        )

    async def _connect_vnc(self) -> bool:
        """Connect to the VNC server using VNCClient.

        Returns:
            True if connection successful, False otherwise.
        """
        try:
            self._vnc_client = VNCClient(
                host=self.config.vnc_host,
                port=self.config.vnc_port,
                password=self.config.vnc_password or None,
            )

            connected = await self._vnc_client.connect()
            if not connected:
                logger.error(
                    f"VNC client failed to connect to "
                    f"{self.config.vnc_host}:{self.config.vnc_port}"
                )
                self._vnc_client = None
                return False

            # Request an initial full framebuffer update so the
            # internal buffer is populated before the first capture.
            await self._vnc_client.request_update(incremental=False)

            logger.info(
                f"VNC client connected: {self._vnc_client.width}x{self._vnc_client.height} "
                f"desktop '{self._vnc_client.name}'"
            )
            return True

        except TimeoutError:
            logger.error("VNC connection timeout")
            return False
        except ConnectionRefusedError:
            logger.error(
                f"VNC connection refused at "
                f"{self.config.vnc_host}:{self.config.vnc_port}"
            )
            return False
        except Exception as e:
            logger.error(f"VNC connection error: {e}")
            return False

    async def _disconnect_vnc(self):
        """Disconnect from the VNC server."""
        if self._vnc_client:
            try:
                await self._vnc_client.disconnect()
            except Exception as e:
                logger.debug(f"Error closing VNC connection: {e}")
            finally:
                self._vnc_client = None

    async def _stream_loop(self):
        """Main streaming loop that captures and sends frames."""
        self._set_status(StreamStatus.STREAMING)
        frame_interval = 1.0 / self.config.fps

        while self._running:
            try:
                frame_start = asyncio.get_event_loop().time()

                # Capture frame from VNC
                frame_data = await self._capture_frame()

                if frame_data and self._on_frame:
                    self.session.frame_count += 1
                    self.session.bytes_sent += len(frame_data)
                    await self._emit_frame(frame_data)

                # Maintain FPS
                elapsed = asyncio.get_event_loop().time() - frame_start
                if elapsed < frame_interval:
                    await asyncio.sleep(frame_interval - elapsed)

            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Stream loop error: {e}")
                if self._on_error:
                    self._on_error(e)
                await asyncio.sleep(1)  # Brief pause before retry

    async def _capture_frame(self) -> bytes | None:
        """Capture a frame from the VNC server.

        Reads pending server messages (framebuffer updates, bell, cut-text),
        converts the current framebuffer to a PIL Image, optionally resizes
        it, and encodes it as JPEG.

        Returns:
            JPEG-encoded frame bytes, or None if capture failed.
        """
        if not self._vnc_client or not self._vnc_client.connected:
            return None

        try:
            frame = await self._read_vnc_update()

            if frame is None:
                # No update arrived, but we can still encode the current
                # framebuffer (it may be unchanged since the last frame).
                if self._vnc_client._framebuffer is None:
                    return None
                frame = VNCFrame(
                    width=self._vnc_client.width,
                    height=self._vnc_client.height,
                    data=bytes(self._vnc_client._framebuffer),
                    timestamp=asyncio.get_event_loop().time(),
                )

            # Convert raw pixel data to a PIL RGB image.
            # The VNCClient's pixel format is 32-bit BGRX (red_shift=16,
            # green_shift=8, blue_shift=0).  frame_to_image() handles the
            # channel swap.
            img = self._vnc_client.frame_to_image(frame)

            # Resize if configured
            if self.config.resize_width and self.config.resize_height:
                target = (self.config.resize_width, self.config.resize_height)
                if (img.width, img.height) != target:
                    img = img.resize(target, Image.Resampling.LANCZOS)

            # Encode as JPEG
            buf = io.BytesIO()
            img.save(buf, format="JPEG", quality=self.config.quality)
            return buf.getvalue()

        except Exception as e:
            logger.error(f"Frame capture error: {e}")
            return None

    async def _read_vnc_update(self) -> VNCFrame | None:
        """Read a single server message, processing framebuffer updates.

        Uses a short timeout so the stream loop is never blocked for long.

        Returns:
            A VNCFrame if a framebuffer update was received, else None.
        """
        client = self._vnc_client
        if client is None:
            return None
        reader = client._reader

        if reader is None:
            return None

        frame: VNCFrame | None = None
        frame_interval = 1.0 / self.config.fps

        try:
            # Read the 1-byte message type with a short timeout so we
            # don't stall the stream loop if the server has nothing to send.
            raw = await asyncio.wait_for(
                reader.readexactly(1),
                timeout=frame_interval * 2,
            )
            msg_type = struct.unpack("!B", raw)[0]

            if msg_type == RFBMessageType.FRAMEBUFFER_UPDATE:
                frame = await client._handle_framebuffer_update()

            elif msg_type == RFBMessageType.BELL:
                pass  # nothing to do

            elif msg_type == RFBMessageType.SERVER_CUT_TEXT:
                # Read and discard server cut text
                await reader.readexactly(3)  # padding
                text_len = struct.unpack("!I", await reader.readexactly(4))[0]
                await reader.readexactly(text_len)

            elif msg_type == RFBMessageType.SET_COLOUR_MAP_ENTRIES:
                # Read and discard colour map entries
                await reader.readexactly(1)  # padding
                _ = struct.unpack("!H", await reader.readexactly(2))[0]  # first colour
                num_colours = struct.unpack("!H", await reader.readexactly(2))[0]
                await reader.readexactly(num_colours * 6)  # 3x uint16 per entry

            else:
                logger.warning(f"Unknown VNC message type: {msg_type}")

        except TimeoutError:
            # No message within the timeout window -- that's fine.
            pass

        # Always request the next incremental update so the server
        # keeps sending changes.
        try:
            await client.request_update(incremental=True)
        except Exception:
            pass

        return frame

    async def _emit_frame(self, frame_data: bytes):
        """Emit frame data to callback.

        Args:
            frame_data: JPEG-encoded frame bytes.
        """
        if self._on_frame:
            try:
                # Call callback - it might be sync or async
                result = self._on_frame(frame_data)
                if asyncio.iscoroutine(result):
                    await result
            except Exception as e:
                logger.error(f"Frame callback error: {e}")

    def add_viewer(self, viewer_id: str):
        """Add a viewer to this session.

        Args:
            viewer_id: Unique identifier for the viewer.
        """
        self.session.viewers.add(viewer_id)
        logger.debug(f"Viewer {viewer_id} added to session {self.session_id}")

    def remove_viewer(self, viewer_id: str):
        """Remove a viewer from this session.

        Args:
            viewer_id: Unique identifier for the viewer.
        """
        self.session.viewers.discard(viewer_id)
        logger.debug(f"Viewer {viewer_id} removed from session {self.session_id}")

    @property
    def viewer_count(self) -> int:
        """Get the current number of viewers."""
        return len(self.session.viewers)

    @property
    def status(self) -> StreamStatus:
        """Get the current stream status."""
        return self.session.status

    def get_stats(self) -> dict:
        """Get streaming statistics.

        Returns:
            Dictionary with streaming stats.
        """
        return {
            "session_id": self.session_id,
            "task_id": self.task_id,
            "status": self.session.status.value,
            "viewers": self.viewer_count,
            "frame_count": self.session.frame_count,
            "bytes_sent": self.session.bytes_sent,
            "fps": self.config.fps,
            "quality": self.config.quality,
        }


class StreamManager:
    """Manages multiple VNC streaming sessions."""

    def __init__(self):
        self.sessions: dict[str, VNCStreamer] = {}

    async def create_session(
        self,
        session_id: str,
        task_id: int,
        vnc_port: int,
        vnc_host: str = "localhost",
    ) -> VNCStreamer:
        """Create a new streaming session.

        Args:
            session_id: Unique session identifier.
            task_id: Associated task ID.
            vnc_port: VNC server port.
            vnc_host: VNC server host.

        Returns:
            The created VNCStreamer instance.
        """
        if session_id in self.sessions:
            logger.warning(f"Session {session_id} already exists")
            return self.sessions[session_id]

        config = StreamConfig(vnc_host=vnc_host, vnc_port=vnc_port)
        streamer = VNCStreamer(session_id, task_id, config)
        self.sessions[session_id] = streamer

        logger.info(f"Created streaming session {session_id} for task {task_id}")
        return streamer

    async def start_session(self, session_id: str) -> bool:
        """Start streaming for a session.

        Args:
            session_id: Session to start.

        Returns:
            True if started successfully.
        """
        streamer = self.sessions.get(session_id)
        if not streamer:
            logger.error(f"Session {session_id} not found")
            return False

        return await streamer.start()

    async def stop_session(self, session_id: str):
        """Stop streaming for a session.

        Args:
            session_id: Session to stop.
        """
        streamer = self.sessions.get(session_id)
        if streamer:
            await streamer.stop()
            del self.sessions[session_id]
            logger.info(f"Stopped and removed session {session_id}")

    async def stop_all(self):
        """Stop all active streaming sessions."""
        for session_id in list(self.sessions.keys()):
            await self.stop_session(session_id)

    def get_session(self, session_id: str) -> VNCStreamer | None:
        """Get a streaming session by ID."""
        return self.sessions.get(session_id)

    def get_active_sessions(self) -> list[str]:
        """Get list of active session IDs."""
        return [
            sid for sid, streamer in self.sessions.items()
            if streamer.status == StreamStatus.STREAMING
        ]


# Global stream manager instance
stream_manager = StreamManager()
