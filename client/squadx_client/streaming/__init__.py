"""VNC streaming module for Live View functionality."""

from squadx_client.streaming.vnc_streamer import (
    VNCStreamer,
    StreamConfig,
    StreamManager,
    StreamStatus,
    stream_manager,
)
from squadx_client.streaming.vnc_client import (
    VNCClient,
    VNCFrame,
    PixelFormat,
)
from squadx_client.streaming.webrtc_bridge import (
    WebRTCBridge,
    VNCVideoTrack,
    create_live_stream,
)

__all__ = [
    # Legacy streamer (WebSocket-based)
    "VNCStreamer",
    "StreamConfig",
    "StreamManager",
    "StreamStatus",
    "stream_manager",
    # New RFB client
    "VNCClient",
    "VNCFrame",
    "PixelFormat",
    # WebRTC Bridge
    "WebRTCBridge",
    "VNCVideoTrack",
    "create_live_stream",
]
