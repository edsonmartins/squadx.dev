"""Tests for squadx_client.websocket.messages module."""

import json
from datetime import datetime

import pytest

from squadx_client.websocket.messages import (
    LogMessage,
    MessageType,
    StatusMessage,
    StompCommand,
    StompFrame,
    TaskMessage,
)


class TestStompFrameEncodeDecode:
    """Test StompFrame encode/decode round-trip."""

    def test_encode_simple_frame(self):
        frame = StompFrame(command=StompCommand.SEND, headers={"destination": "/app/test"}, body="hello")
        encoded = frame.encode()
        assert isinstance(encoded, bytes)
        assert encoded.endswith(b"\x00")
        assert b"SEND" in encoded

    def test_decode_simple_frame(self):
        raw = "MESSAGE\ndestination:/topic/test\n\nhello body\x00"
        frame = StompFrame.decode(raw)
        assert frame.command == StompCommand.MESSAGE
        assert frame.headers["destination"] == "/topic/test"
        assert frame.body == "hello body"

    def test_encode_decode_round_trip(self):
        original = StompFrame(
            command=StompCommand.SEND,
            headers={"destination": "/app/tasks", "content-type": "application/json"},
            body='{"task_id": 1}',
        )
        encoded = original.encode()
        decoded = StompFrame.decode(encoded)
        assert decoded.command == original.command
        assert decoded.headers["destination"] == original.headers["destination"]
        assert decoded.body == original.body

    def test_decode_from_bytes(self):
        raw = b"CONNECTED\nversion:1.2\n\n\x00"
        frame = StompFrame.decode(raw)
        assert frame.command == StompCommand.CONNECTED
        assert frame.headers["version"] == "1.2"
        assert frame.body == ""

    def test_header_escaping_colon(self):
        frame = StompFrame(
            command=StompCommand.SEND,
            headers={"selector": "key:value"},
            body="",
        )
        encoded = frame.encode()
        decoded = StompFrame.decode(encoded)
        assert decoded.headers["selector"] == "key:value"

    def test_header_escaping_newline(self):
        frame = StompFrame(
            command=StompCommand.SEND,
            headers={"header": "line1\nline2"},
            body="",
        )
        encoded = frame.encode()
        # The encoded form should escape the newline
        assert b"\\n" in encoded
        decoded = StompFrame.decode(encoded)
        assert decoded.headers["header"] == "line1\nline2"

    def test_empty_body(self):
        frame = StompFrame(command=StompCommand.CONNECT, headers={"host": "localhost"})
        encoded = frame.encode()
        decoded = StompFrame.decode(encoded)
        assert decoded.body == ""
        assert decoded.command == StompCommand.CONNECT

    def test_decode_empty_frame_raises(self):
        with pytest.raises((ValueError, IndexError)):
            StompFrame.decode("")


class TestStompCommand:
    """Test StompCommand enum values."""

    def test_client_commands_exist(self):
        assert StompCommand.CONNECT.value == "CONNECT"
        assert StompCommand.SEND.value == "SEND"
        assert StompCommand.SUBSCRIBE.value == "SUBSCRIBE"
        assert StompCommand.DISCONNECT.value == "DISCONNECT"

    def test_server_commands_exist(self):
        assert StompCommand.CONNECTED.value == "CONNECTED"
        assert StompCommand.MESSAGE.value == "MESSAGE"
        assert StompCommand.ERROR.value == "ERROR"


class TestMessageType:
    """Test MessageType enum values."""

    def test_task_types(self):
        assert MessageType.TASK_ASSIGNED.value == "task_assigned"
        assert MessageType.TASK_CANCELLED.value == "task_cancelled"
        assert MessageType.TASK_COMPLETED.value == "task_completed"

    def test_system_types(self):
        assert MessageType.PING.value == "ping"
        assert MessageType.PONG.value == "pong"

    def test_live_view_types(self):
        assert MessageType.START_LIVE_VIEW.value == "start_live_view"
        assert MessageType.STOP_LIVE_VIEW.value == "stop_live_view"


class TestTaskMessage:
    """Test TaskMessage serialization."""

    def test_to_dict_contains_required_fields(self):
        msg = TaskMessage(task_id=42, type=MessageType.TASK_ASSIGNED, data={"key": "val"})
        d = msg.to_dict()
        assert d["task_id"] == 42
        assert d["type"] == "task_assigned"
        assert d["data"] == {"key": "val"}
        assert "timestamp" in d

    def test_to_dict_timestamp_is_iso(self):
        msg = TaskMessage(task_id=1, type=MessageType.TASK_COMPLETED)
        d = msg.to_dict()
        # Should be parseable as ISO datetime
        datetime.fromisoformat(d["timestamp"])

    def test_default_data_is_empty_dict(self):
        msg = TaskMessage(task_id=1, type=MessageType.TASK_ASSIGNED)
        assert msg.data == {}


class TestStatusMessage:
    """Test StatusMessage serialization."""

    def test_to_dict_contains_required_fields(self):
        msg = StatusMessage(task_id=10, status="IN_PROGRESS", progress=50, current_step="Building")
        d = msg.to_dict()
        assert d["task_id"] == 10
        assert d["status"] == "IN_PROGRESS"
        assert d["progress"] == 50
        assert d["current_step"] == "Building"

    def test_defaults(self):
        msg = StatusMessage(task_id=1, status="PENDING")
        assert msg.progress == 0
        assert msg.current_step == ""


class TestLogMessage:
    """Test LogMessage serialization."""

    def test_to_dict_contains_required_fields(self):
        msg = LogMessage(execution_id=5, level="INFO", message="Starting task", agent="backend")
        d = msg.to_dict()
        assert d["execution_id"] == 5
        assert d["level"] == "INFO"
        assert d["message"] == "Starting task"
        assert d["agent"] == "backend"

    def test_default_agent_is_empty(self):
        msg = LogMessage(execution_id=1, level="ERROR", message="fail")
        assert msg.agent == ""
