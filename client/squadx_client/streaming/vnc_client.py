"""VNC client implementing RFB protocol for screen capture.

This client connects to a VNC server (x11vnc in the agent container)
and captures framebuffer updates for WebRTC streaming.

RFB Protocol Reference: https://datatracker.ietf.org/doc/html/rfc6143
"""

import asyncio
import logging
import struct
from collections.abc import AsyncGenerator, Callable
from dataclasses import dataclass
from enum import IntEnum

from PIL import Image

logger = logging.getLogger(__name__)


class RFBMessageType(IntEnum):
    """RFB server-to-client message types."""

    FRAMEBUFFER_UPDATE = 0
    SET_COLOUR_MAP_ENTRIES = 1
    BELL = 2
    SERVER_CUT_TEXT = 3


# JavaScript key code to X11 keysym mapping
# Based on: https://www.cl.cam.ac.uk/~mgk25/ucs/keysymdef.h
JS_KEY_TO_KEYSYM: dict[str, int] = {
    # Modifier keys
    "Shift": 0xFFE1,
    "Control": 0xFFE3,
    "Alt": 0xFFE9,
    "Meta": 0xFFEB,
    "CapsLock": 0xFFE5,
    "NumLock": 0xFF7F,

    # Navigation keys
    "ArrowUp": 0xFF52,
    "ArrowDown": 0xFF54,
    "ArrowLeft": 0xFF51,
    "ArrowRight": 0xFF53,
    "Home": 0xFF50,
    "End": 0xFF57,
    "PageUp": 0xFF55,
    "PageDown": 0xFF56,

    # Editing keys
    "Backspace": 0xFF08,
    "Tab": 0xFF09,
    "Enter": 0xFF0D,
    "Escape": 0xFF1B,
    "Delete": 0xFFFF,
    "Insert": 0xFF63,

    # Function keys
    "F1": 0xFFBE,
    "F2": 0xFFBF,
    "F3": 0xFFC0,
    "F4": 0xFFC1,
    "F5": 0xFFC2,
    "F6": 0xFFC3,
    "F7": 0xFFC4,
    "F8": 0xFFC5,
    "F9": 0xFFC6,
    "F10": 0xFFC7,
    "F11": 0xFFC8,
    "F12": 0xFFC9,

    # Whitespace
    " ": 0x0020,

    # Special characters
    "`": 0x0060,
    "~": 0x007E,
    "!": 0x0021,
    "@": 0x0040,
    "#": 0x0023,
    "$": 0x0024,
    "%": 0x0025,
    "^": 0x005E,
    "&": 0x0026,
    "*": 0x002A,
    "(": 0x0028,
    ")": 0x0029,
    "-": 0x002D,
    "_": 0x005F,
    "=": 0x003D,
    "+": 0x002B,
    "[": 0x005B,
    "{": 0x007B,
    "]": 0x005D,
    "}": 0x007D,
    "\\": 0x005C,
    "|": 0x007C,
    ";": 0x003B,
    ":": 0x003A,
    "'": 0x0027,
    '"': 0x0022,
    ",": 0x002C,
    "<": 0x003C,
    ".": 0x002E,
    ">": 0x003E,
    "/": 0x002F,
    "?": 0x003F,
}


def js_key_to_keysym(key: str) -> int:
    """Convert JavaScript key to X11 keysym.

    Args:
        key: JavaScript key string (from KeyboardEvent.key)

    Returns:
        X11 keysym value
    """
    # Check special keys first
    if key in JS_KEY_TO_KEYSYM:
        return JS_KEY_TO_KEYSYM[key]

    # Single character - use Unicode code point
    if len(key) == 1:
        code = ord(key)
        # ASCII range maps directly
        if 0x20 <= code <= 0x7E:
            return code
        # Unicode range (add 0x01000000 prefix)
        return 0x01000000 | code

    # Unknown key
    return 0


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
        password: str | None = None,
    ):
        self.host = host
        self.port = port
        self.password = password

        self._reader: asyncio.StreamReader | None = None
        self._writer: asyncio.StreamWriter | None = None
        self._connected = False

        self.width = 0
        self.height = 0
        self.name = ""
        self.pixel_format = PixelFormat()

        self._framebuffer: bytearray | None = None
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
            assert self._reader is not None and self._writer is not None

            # Protocol version exchange
            server_version = await self._reader.readline()
            logger.debug(f"Server version: {server_version!r}")

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

        except TimeoutError:
            logger.error(f"Connection timeout to {self.host}:{self.port}")
            return False
        except Exception as e:
            logger.error(f"Failed to connect to VNC server: {e}")
            return False

    async def _security_handshake(self) -> bool:
        """Perform security handshake."""
        assert self._reader is not None and self._writer is not None
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
        assert self._writer is not None
        self._writer.write(msg)
        await self._writer.drain()

    async def _handle_framebuffer_update(self) -> VNCFrame | None:
        """Handle framebuffer update message."""
        import time

        assert self._reader is not None and self._framebuffer is not None
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
        assert self._framebuffer is not None
        bytes_per_pixel = self.pixel_format.bits_per_pixel // 8

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
        assert self._framebuffer is not None
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
        assert self._reader is not None

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

            except TimeoutError:
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

    async def send_pointer_event(
        self,
        x: int,
        y: int,
        button_mask: int = 0,
    ):
        """Send pointer (mouse) event to VNC server.

        Args:
            x: X coordinate (absolute)
            y: Y coordinate (absolute)
            button_mask: Button mask (bit 0=left, bit 1=middle, bit 2=right,
                        bits 3-4=wheel up/down)
        """
        if not self._connected or not self._writer:
            return

        # Clamp coordinates
        x = max(0, min(x, self.width - 1))
        y = max(0, min(y, self.height - 1))

        msg = struct.pack(
            "!B B HH",
            RFBClientMessageType.POINTER_EVENT,
            button_mask,
            x,
            y,
        )
        self._writer.write(msg)
        await self._writer.drain()

    async def send_key_event(
        self,
        key: int,
        down: bool,
    ):
        """Send key event to VNC server.

        Args:
            key: X11 keysym
            down: True if key is pressed, False if released
        """
        if not self._connected or not self._writer:
            return

        msg = struct.pack(
            "!B B xx I",
            RFBClientMessageType.KEY_EVENT,
            1 if down else 0,
            key,
        )
        self._writer.write(msg)
        await self._writer.drain()

    async def send_mouse_move(self, x: int, y: int):
        """Send mouse move event (no buttons pressed)."""
        await self.send_pointer_event(x, y, 0)

    async def send_mouse_click(
        self,
        x: int,
        y: int,
        button: str = "left",
        down: bool = True,
    ):
        """Send mouse button event.

        Args:
            x: X coordinate
            y: Y coordinate
            button: 'left', 'middle', or 'right'
            down: True for press, False for release
        """
        button_map = {"left": 1, "middle": 2, "right": 4}
        mask = button_map.get(button, 1) if down else 0
        await self.send_pointer_event(x, y, mask)

    async def send_mouse_scroll(
        self,
        x: int,
        y: int,
        delta_y: int,
    ):
        """Send mouse scroll event.

        Args:
            x: X coordinate
            y: Y coordinate
            delta_y: Scroll delta (positive = up, negative = down)
        """
        # Scroll is represented as button 4 (up) or 5 (down) press/release
        if delta_y > 0:
            # Scroll up (button 4)
            await self.send_pointer_event(x, y, 8)  # Button 4 press
            await self.send_pointer_event(x, y, 0)  # Release
        elif delta_y < 0:
            # Scroll down (button 5)
            await self.send_pointer_event(x, y, 16)  # Button 5 press
            await self.send_pointer_event(x, y, 0)  # Release

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
