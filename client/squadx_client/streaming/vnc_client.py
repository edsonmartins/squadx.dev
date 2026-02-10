"""VNC client implementing RFB protocol for screen capture.

This client connects to a VNC server (x11vnc in the agent container)
and captures framebuffer updates for WebRTC streaming.

RFB Protocol Reference: https://datatracker.ietf.org/doc/html/rfc6143
"""

import asyncio
import logging
import struct
from dataclasses import dataclass
from enum import IntEnum
from typing import AsyncGenerator, Callable, Optional

from PIL import Image

logger = logging.getLogger(__name__)


class RFBMessageType(IntEnum):
    """RFB server-to-client message types."""

    FRAMEBUFFER_UPDATE = 0
    SET_COLOUR_MAP_ENTRIES = 1
    BELL = 2
    SERVER_CUT_TEXT = 3


class RFBClientMessageType(IntEnum):
    """RFB client-to-server message types."""

    SET_PIXEL_FORMAT = 0
    SET_ENCODINGS = 2
    FRAMEBUFFER_UPDATE_REQUEST = 3
    KEY_EVENT = 4
    POINTER_EVENT = 5
    CLIENT_CUT_TEXT = 6


class RFBEncoding(IntEnum):
    """RFB encoding types."""

    RAW = 0
    COPY_RECT = 1
    RRE = 2
    HEXTILE = 5
    TRLE = 15
    ZRLE = 16
    CURSOR = -239
    DESKTOP_SIZE = -223


@dataclass
class PixelFormat:
    """RFB pixel format."""

    bits_per_pixel: int = 32
    depth: int = 24
    big_endian: bool = False
    true_colour: bool = True
    red_max: int = 255
    green_max: int = 255
    blue_max: int = 255
    red_shift: int = 16
    green_shift: int = 8
    blue_shift: int = 0

    def to_bytes(self) -> bytes:
        """Convert to RFB wire format."""
        return struct.pack(
            "!BBBB HHH BBB xxx",
            self.bits_per_pixel,
            self.depth,
            1 if self.big_endian else 0,
            1 if self.true_colour else 0,
            self.red_max,
            self.green_max,
            self.blue_max,
            self.red_shift,
            self.green_shift,
            self.blue_shift,
        )


@dataclass
class FramebufferUpdate:
    """Represents a framebuffer update from the server."""

    x: int
    y: int
    width: int
    height: int
    encoding: int
    data: bytes


@dataclass
class VNCFrame:
    """A complete frame captured from VNC."""

    width: int
    height: int
    data: bytes  # Raw RGBA data
    timestamp: float


class VNCClient:
    """Async VNC client for screen capture.

    Connects to a VNC server and captures framebuffer updates.
    Designed to work with x11vnc running in Docker containers.
    """

    # Protocol version
    RFB_VERSION = b"RFB 003.008\n"

    def __init__(
        self,
        host: str = "localhost",
        port: int = 5900,
        password: Optional[str] = None,
    ):
        self.host = host
        self.port = port
        self.password = password

        self._reader: Optional[asyncio.StreamReader] = None
        self._writer: Optional[asyncio.StreamWriter] = None
        self._connected = False

        self.width = 0
        self.height = 0
        self.name = ""
        self.pixel_format = PixelFormat()

        self._framebuffer: Optional[bytearray] = None
        self._frame_callbacks: list[Callable[[VNCFrame], None]] = []

    @property
    def connected(self) -> bool:
        """Check if connected to VNC server."""
        return self._connected

    def on_frame(self, callback: Callable[[VNCFrame], None]):
        """Register callback for frame updates."""
        self._frame_callbacks.append(callback)

    async def connect(self) -> bool:
        """Connect to VNC server and perform handshake.

        Returns:
            True if connection successful
        """
        try:
            self._reader, self._writer = await asyncio.wait_for(
                asyncio.open_connection(self.host, self.port),
                timeout=10.0,
            )

            # Protocol version exchange
            server_version = await self._reader.readline()
            logger.debug(f"Server version: {server_version}")

            self._writer.write(self.RFB_VERSION)
            await self._writer.drain()

            # Security handshake
            if not await self._security_handshake():
                return False

            # Client init - request shared desktop
            self._writer.write(struct.pack("!B", 1))  # shared=True
            await self._writer.drain()

            # Server init
            await self._read_server_init()

            # Set pixel format (32-bit RGBA)
            await self._set_pixel_format()

            # Set encodings
            await self._set_encodings()

            self._connected = True
            logger.info(
                f"Connected to VNC server {self.host}:{self.port} "
                f"({self.width}x{self.height})"
            )

            # Initialize framebuffer
            self._framebuffer = bytearray(self.width * self.height * 4)

            return True

        except asyncio.TimeoutError:
            logger.error(f"Connection timeout to {self.host}:{self.port}")
            return False
        except Exception as e:
            logger.error(f"Failed to connect to VNC server: {e}")
            return False

    async def _security_handshake(self) -> bool:
        """Perform security handshake."""
        # Read security types
        num_types = struct.unpack("!B", await self._reader.readexactly(1))[0]

        if num_types == 0:
            # Connection failed, read reason
            reason_len = struct.unpack("!I", await self._reader.readexactly(4))[0]
            reason = (await self._reader.readexactly(reason_len)).decode()
            logger.error(f"Connection failed: {reason}")
            return False

        security_types = list(await self._reader.readexactly(num_types))
        logger.debug(f"Security types: {security_types}")

        # Prefer no auth (type 1) or VNC auth (type 2)
        if 1 in security_types:
            # No authentication
            self._writer.write(struct.pack("!B", 1))
            await self._writer.drain()
        elif 2 in security_types:
            # VNC authentication
            if not self.password:
                logger.error("VNC requires password but none provided")
                return False

            self._writer.write(struct.pack("!B", 2))
            await self._writer.drain()

            # DES challenge-response
            challenge = await self._reader.readexactly(16)
            response = self._encrypt_challenge(challenge, self.password)
            self._writer.write(response)
            await self._writer.drain()
        else:
            logger.error(f"No supported security type: {security_types}")
            return False

        # Read security result
        result = struct.unpack("!I", await self._reader.readexactly(4))[0]
        if result != 0:
            # Read reason (RFB 3.8+)
            try:
                reason_len = struct.unpack("!I", await self._reader.readexactly(4))[0]
                reason = (await self._reader.readexactly(reason_len)).decode()
                logger.error(f"Security handshake failed: {reason}")
            except Exception:
                logger.error("Security handshake failed")
            return False

        return True

    def _encrypt_challenge(self, challenge: bytes, password: str) -> bytes:
        """Encrypt VNC authentication challenge with DES.

        Note: VNC uses a weird bit-reversed DES.
        """
        try:
            from Crypto.Cipher import DES
        except ImportError:
            # Fallback - try pycryptodome
            try:
                from Cryptodome.Cipher import DES
            except ImportError:
                logger.error("pycryptodome required for VNC authentication")
                raise

        # Pad or truncate password to 8 bytes
        key = password.encode()[:8].ljust(8, b"\x00")

        # Reverse bits in each byte (VNC quirk)
        def reverse_bits(b: int) -> int:
            return int(bin(b)[2:].zfill(8)[::-1], 2)

        key = bytes(reverse_bits(b) for b in key)

        cipher = DES.new(key, DES.MODE_ECB)
        return cipher.encrypt(challenge)

    async def _read_server_init(self):
        """Read server initialization message."""
        # Width, height
        data = await self._reader.readexactly(4)
        self.width, self.height = struct.unpack("!HH", data)

        # Pixel format (16 bytes)
        pf_data = await self._reader.readexactly(16)
        (
            bits_per_pixel,
            depth,
            big_endian,
            true_colour,
            red_max,
            green_max,
            blue_max,
            red_shift,
            green_shift,
            blue_shift,
        ) = struct.unpack("!BBBB HHH BBB xxx", pf_data)

        logger.debug(
            f"Server pixel format: {bits_per_pixel}bpp, "
            f"depth={depth}, rgb_shifts=({red_shift},{green_shift},{blue_shift})"
        )

        # Desktop name
        name_len = struct.unpack("!I", await self._reader.readexactly(4))[0]
        self.name = (await self._reader.readexactly(name_len)).decode()
        logger.debug(f"Desktop name: {self.name}")

    async def _set_pixel_format(self):
        """Set pixel format to 32-bit RGBA."""
        msg = struct.pack("!B xxx", RFBClientMessageType.SET_PIXEL_FORMAT)
        msg += self.pixel_format.to_bytes()
        self._writer.write(msg)
        await self._writer.drain()

    async def _set_encodings(self):
        """Set supported encodings."""
        encodings = [
            RFBEncoding.RAW,
            RFBEncoding.COPY_RECT,
            RFBEncoding.DESKTOP_SIZE,
        ]

        msg = struct.pack(
            "!B x H",
            RFBClientMessageType.SET_ENCODINGS,
            len(encodings),
        )
        for enc in encodings:
            msg += struct.pack("!i", enc)

        self._writer.write(msg)
        await self._writer.drain()

    async def request_update(self, incremental: bool = True):
        """Request a framebuffer update.

        Args:
            incremental: If True, only request changed regions
        """
        msg = struct.pack(
            "!B B HH HH",
            RFBClientMessageType.FRAMEBUFFER_UPDATE_REQUEST,
            1 if incremental else 0,
            0, 0,  # x, y
            self.width, self.height,
        )
        self._writer.write(msg)
        await self._writer.drain()

    async def _handle_framebuffer_update(self) -> Optional[VNCFrame]:
        """Handle framebuffer update message."""
        import time

        # Read number of rectangles
        _ = await self._reader.readexactly(1)  # padding
        num_rects = struct.unpack("!H", await self._reader.readexactly(2))[0]

        for _ in range(num_rects):
            # Read rectangle header
            rect_data = await self._reader.readexactly(12)
            x, y, w, h, encoding = struct.unpack("!HH HH i", rect_data)

            if encoding == RFBEncoding.RAW:
                # Raw pixel data
                size = w * h * (self.pixel_format.bits_per_pixel // 8)
                pixel_data = await self._reader.readexactly(size)

                # Copy to framebuffer
                self._update_framebuffer(x, y, w, h, pixel_data)

            elif encoding == RFBEncoding.COPY_RECT:
                # Copy from another region
                src_x, src_y = struct.unpack(
                    "!HH", await self._reader.readexactly(4)
                )
                self._copy_rect(src_x, src_y, x, y, w, h)

            elif encoding == RFBEncoding.DESKTOP_SIZE:
                # Desktop resize
                self.width = w
                self.height = h
                self._framebuffer = bytearray(self.width * self.height * 4)
                logger.info(f"Desktop resized to {w}x{h}")

            else:
                logger.warning(f"Unsupported encoding: {encoding}")

        # Create frame from current framebuffer
        return VNCFrame(
            width=self.width,
            height=self.height,
            data=bytes(self._framebuffer),
            timestamp=time.time(),
        )

    def _update_framebuffer(
        self,
        x: int, y: int,
        w: int, h: int,
        data: bytes,
    ):
        """Update region of framebuffer with new pixel data."""
        bytes_per_pixel = self.pixel_format.bits_per_pixel // 8
        row_stride = self.width * bytes_per_pixel

        for row in range(h):
            src_offset = row * w * bytes_per_pixel
            dst_offset = ((y + row) * self.width + x) * bytes_per_pixel

            self._framebuffer[dst_offset:dst_offset + w * bytes_per_pixel] = \
                data[src_offset:src_offset + w * bytes_per_pixel]

    def _copy_rect(
        self,
        src_x: int, src_y: int,
        dst_x: int, dst_y: int,
        w: int, h: int,
    ):
        """Copy rectangle from one region to another."""
        bytes_per_pixel = self.pixel_format.bits_per_pixel // 8

        # Copy row by row (handle overlapping regions)
        temp = bytearray(w * h * bytes_per_pixel)

        for row in range(h):
            src_offset = ((src_y + row) * self.width + src_x) * bytes_per_pixel
            temp_offset = row * w * bytes_per_pixel
            temp[temp_offset:temp_offset + w * bytes_per_pixel] = \
                self._framebuffer[src_offset:src_offset + w * bytes_per_pixel]

        for row in range(h):
            dst_offset = ((dst_y + row) * self.width + dst_x) * bytes_per_pixel
            temp_offset = row * w * bytes_per_pixel
            self._framebuffer[dst_offset:dst_offset + w * bytes_per_pixel] = \
                temp[temp_offset:temp_offset + w * bytes_per_pixel]

    async def capture_frames(
        self,
        fps: int = 30,
    ) -> AsyncGenerator[VNCFrame, None]:
        """Continuously capture frames from VNC server.

        Args:
            fps: Target frames per second

        Yields:
            VNCFrame objects
        """
        if not self._connected:
            raise RuntimeError("Not connected to VNC server")

        interval = 1.0 / fps

        # Request initial full update
        await self.request_update(incremental=False)

        while self._connected:
            try:
                # Wait for server message
                msg_type = struct.unpack(
                    "!B", await asyncio.wait_for(
                        self._reader.readexactly(1),
                        timeout=interval * 2,
                    )
                )[0]

                if msg_type == RFBMessageType.FRAMEBUFFER_UPDATE:
                    frame = await self._handle_framebuffer_update()
                    if frame:
                        yield frame

                        # Notify callbacks
                        for callback in self._frame_callbacks:
                            try:
                                callback(frame)
                            except Exception as e:
                                logger.error(f"Frame callback error: {e}")

                elif msg_type == RFBMessageType.BELL:
                    logger.debug("Bell")

                elif msg_type == RFBMessageType.SERVER_CUT_TEXT:
                    # Read and discard cut text
                    _ = await self._reader.readexactly(3)  # padding
                    text_len = struct.unpack(
                        "!I", await self._reader.readexactly(4)
                    )[0]
                    _ = await self._reader.readexactly(text_len)

                else:
                    logger.warning(f"Unknown message type: {msg_type}")

                # Request next update
                await self.request_update(incremental=True)

                # Rate limiting
                await asyncio.sleep(interval)

            except asyncio.TimeoutError:
                # No update, request again
                await self.request_update(incremental=True)

            except asyncio.CancelledError:
                break

            except Exception as e:
                logger.error(f"Error capturing frame: {e}")
                self._connected = False
                break

    def frame_to_image(self, frame: VNCFrame) -> Image.Image:
        """Convert VNC frame to PIL Image.

        Args:
            frame: VNC frame with BGRA data

        Returns:
            PIL Image in RGB format
        """
        # VNC sends BGRA (or BGRX)
        img = Image.frombytes(
            "RGBA",
            (frame.width, frame.height),
            frame.data,
        )

        # Swap R and B channels (BGRA -> RGBA)
        r, g, b, a = img.split()
        img = Image.merge("RGB", (b, g, r))

        return img

    async def disconnect(self):
        """Disconnect from VNC server."""
        self._connected = False

        if self._writer:
            try:
                self._writer.close()
                await self._writer.wait_closed()
            except Exception:
                pass

        self._reader = None
        self._writer = None
        self._framebuffer = None

        logger.info("Disconnected from VNC server")

    async def __aenter__(self):
        """Async context manager entry."""
        await self.connect()
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        """Async context manager exit."""
        await self.disconnect()
