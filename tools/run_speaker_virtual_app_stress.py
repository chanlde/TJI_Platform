#!/usr/bin/env python3
"""Direct Speaker MQTT/UDP stress client that does not launch the Android App."""

from __future__ import annotations

import argparse
import ast
import csv
import json
import os
import queue
import re
import socket
import struct
import subprocess
import threading
import time
import uuid
import zlib
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import paho.mqtt.client as mqtt


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MCU_ROOT = Path("/Users/wangtianlong/Desktop/code/TJI_Bucket-1.0.15/micro")
DEFAULT_DEVICE_ID = "T5TNBFM4Q"

UDP_MAGIC = 0xA55A
UDP_VERSION = 2
CODEC_OPUS = 2
UDP_FIXED_HEADER_BYTES = 28
MAX_DATAGRAM_BYTES = 1472
PACKET_MS = 20
SAMPLE_RATE = 48_000
OUTPUT_RATE = 24_000
BITRATE = 32_000
FLAG_LAST = 0x0001
FLAG_PLAYBACK = 0x0004
FLAG_FEEDBACK = 0x0008
FLAG_START = 0x0010
FLAG_ACK = 0x0020
CHUNK_MAGIC = 0x3152544D
ACK_MAGIC = 0x3141524D
MAX_CHUNK_DATA_BYTES = 1100
MAX_PAGES_PER_CHUNK = 8
FEEDBACK_JITTER_BUDGET_MS = 520.0


@dataclass
class Event:
    elapsed_ms: float
    kind: str
    round: int = 0
    chunk: int = -1
    value: float = 0.0
    detail: str = ""


@dataclass
class MediaResult:
    round: int
    session_id: str
    record_id: str
    chunks: int
    bytes: int
    elapsed_ms: float
    sends: int
    retries: int
    max_ack_silence_ms: float


class EventLog:
    def __init__(self) -> None:
        self.started = time.monotonic()
        self.events: list[Event] = []
        self.lock = threading.Lock()

    def add(
        self,
        kind: str,
        *,
        round_index: int = 0,
        chunk: int = -1,
        value: float = 0.0,
        detail: str = "",
    ) -> None:
        event = Event(
            elapsed_ms=(time.monotonic() - self.started) * 1000.0,
            kind=kind,
            round=round_index,
            chunk=chunk,
            value=value,
            detail=detail,
        )
        with self.lock:
            self.events.append(event)


class SerialCapture:
    def __init__(self, port: str | None, baud: int) -> None:
        self.port = port
        self.baud = baud
        self.data = bytearray()
        self.error: str | None = None
        self.stop_event = threading.Event()
        self.thread: threading.Thread | None = None

    def start(self) -> None:
        if not self.port:
            return
        self.thread = threading.Thread(target=self._capture, daemon=True)
        self.thread.start()

    def stop(self) -> None:
        self.stop_event.set()
        if self.thread:
            self.thread.join(timeout=2.0)

    def _capture(self) -> None:
        try:
            import serial  # type: ignore

            with serial.Serial(self.port, self.baud, timeout=0.2) as stream:
                while not self.stop_event.is_set():
                    data = stream.read(4096)
                    if data:
                        self.data.extend(data)
        except Exception as exc:  # Serial evidence is optional; network test must still finish.
            self.error = str(exc)


def generated_build_config_value(name: str) -> str | None:
    candidates = sorted(
        ROOT.glob("app/build/generated/source/buildConfig/noMap/debug/**/BuildConfig.java")
    )
    pattern = re.compile(rf"\b{name}\s*=\s*(\"(?:[^\"\\]|\\.)*\"|\d+)")
    for path in candidates:
        match = pattern.search(path.read_text(encoding="utf-8", errors="replace"))
        if not match:
            continue
        literal = match.group(1)
        return str(ast.literal_eval(literal)) if literal.startswith('"') else literal
    return None


def c_define_value(path: Path, name: str) -> str | None:
    if not path.is_file():
        return None
    pattern = re.compile(rf"^\s*#define\s+{name}\s+(?:\"([^\"]*)\"|(\d+))", re.MULTILINE)
    match = pattern.search(path.read_text(encoding="utf-8", errors="replace"))
    if not match:
        return None
    return match.group(1) if match.group(1) is not None else match.group(2)


def route_id(label: str, value: str) -> bytes:
    encoded = value.encode("utf-8")
    if not encoded or len(encoded) > 32 or any(char.isspace() for char in value):
        raise ValueError(f"{label} must be 1..32 bytes without whitespace")
    return encoded


def ogg_pages(data: bytes) -> list[bytes]:
    pages: list[bytes] = []
    offset = 0
    while offset < len(data):
        if offset + 27 > len(data) or data[offset : offset + 4] != b"OggS":
            raise ValueError(f"invalid Ogg page at offset {offset}")
        segments = data[offset + 26]
        if offset + 27 + segments > len(data):
            raise ValueError("truncated Ogg lacing table")
        payload_bytes = sum(data[offset + 27 : offset + 27 + segments])
        page_bytes = 27 + segments + payload_bytes
        if offset + page_bytes > len(data):
            raise ValueError("truncated Ogg page")
        pages.append(data[offset : offset + page_bytes])
        offset += page_bytes
    return pages


def grouped_pages(data: bytes) -> list[bytes]:
    groups: list[bytes] = []
    current = bytearray()
    page_count = 0
    for page in ogg_pages(data):
        if len(page) > MAX_CHUNK_DATA_BYTES:
            raise ValueError("one Ogg page exceeds the media datagram limit")
        if current and (
            page_count >= MAX_PAGES_PER_CHUNK or
            len(current) + len(page) > MAX_CHUNK_DATA_BYTES
        ):
            groups.append(bytes(current))
            current.clear()
            page_count = 0
        current.extend(page)
        page_count += 1
    if current:
        groups.append(bytes(current))
    return groups


def udp_packet(
    device_id: str,
    session_id: str,
    record_id: str,
    flags: int,
    sequence: int,
    payload: bytes,
) -> bytes:
    device = route_id("deviceId", device_id)
    session = route_id("sessionId", session_id)
    record = route_id("recordId", record_id)
    header_bytes = UDP_FIXED_HEADER_BYTES + len(device) + len(session) + len(record)
    header = struct.pack(
        "<HBBHHIIHBBHHBBBB",
        UDP_MAGIC,
        UDP_VERSION,
        CODEC_OPUS,
        header_bytes,
        flags,
        sequence,
        0,
        SAMPLE_RATE,
        1,
        PACKET_MS,
        len(payload),
        0,
        len(device),
        len(session),
        len(record),
        0,
    )
    packet = header + device + session + record + payload
    if len(packet) > MAX_DATAGRAM_BYTES:
        raise ValueError(f"media datagram exceeds MTU: {len(packet)}")
    return packet


def media_chunks(
    fixture: bytes,
    device_id: str,
    session_id: str,
    record_id: str,
    duration_ms: int,
    volume: int,
) -> list[bytes]:
    groups = grouped_pages(fixture)
    file_crc = zlib.crc32(fixture) & 0xFFFFFFFF
    name = "低音量链路测试".encode("utf-8")
    created = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ").encode("ascii")
    packets: list[bytes] = []
    for index, data in enumerate(groups):
        first = index == 0
        metadata = name + created if first else b""
        chunk_header = struct.pack(
            "<IBBBBHHIIIIIBBBB",
            CHUNK_MAGIC,
            1,
            1,  # PlayTemporary
            max(0, min(volume, 100)),
            2,  # test record type
            index,
            len(groups),
            len(fixture),
            file_crc,
            duration_ms,
            BITRATE,
            zlib.crc32(data) & 0xFFFFFFFF,
            len(name) if first else 0,
            len(created) if first else 0,
            0,  # hidden temporary item
            0,
        )
        flags = FLAG_PLAYBACK
        if first:
            flags |= FLAG_START
        if index + 1 == len(groups):
            flags |= FLAG_LAST
        packets.append(
            udp_packet(
                device_id,
                session_id,
                record_id,
                flags,
                index,
                chunk_header + metadata + data,
            )
        )
    return packets


def parse_media_ack(
    packet: bytes,
    device_id: str,
    session_id: str,
    record_id: str,
) -> tuple[int, int, int] | None:
    if len(packet) < UDP_FIXED_HEADER_BYTES:
        return None
    fields = struct.unpack_from("<HBBHHIIHBBHHBBBB", packet)
    magic, version, codec, header_bytes, flags = fields[:5]
    payload_bytes = fields[10]
    device_bytes, session_bytes, record_bytes = fields[12:15]
    if (
        magic != UDP_MAGIC or version != UDP_VERSION or codec != CODEC_OPUS or
        flags & (FLAG_FEEDBACK | FLAG_ACK) != (FLAG_FEEDBACK | FLAG_ACK) or
        header_bytes != UDP_FIXED_HEADER_BYTES + device_bytes + session_bytes + record_bytes or
        len(packet) != header_bytes + payload_bytes or payload_bytes != 16
    ):
        return None
    offset = UDP_FIXED_HEADER_BYTES
    routes = []
    for size in (device_bytes, session_bytes, record_bytes):
        routes.append(packet[offset : offset + size].decode("utf-8", errors="replace"))
        offset += size
    if routes != [device_id, session_id, record_id]:
        return None
    if struct.unpack_from("<I", packet, header_bytes)[0] != ACK_MAGIC:
        return None
    status = packet[header_bytes + 5]
    chunk_index = struct.unpack_from("<I", packet, header_bytes + 8)[0]
    expected_chunk = struct.unpack_from("<I", packet, header_bytes + 12)[0]
    return status, chunk_index, expected_chunk


class MqttControl:
    def __init__(self, host: str, port: int, username: str, device_id: str, events: EventLog) -> None:
        self.host = host
        self.port = port
        self.device_id = device_id
        self.events = events
        self.messages: queue.Queue[dict[str, Any]] = queue.Queue()
        self.subscription_ready = threading.Event()
        client_id = f"speaker-lab-{uuid.uuid4().hex[:10]}"
        self.client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=client_id)
        if username:
            self.client.username_pw_set(username)
        self.client.on_connect = self._on_connect
        self.client.on_subscribe = self._on_subscribe
        self.client.on_message = self._on_message

    @property
    def control_topic(self) -> str:
        return f"Speaker/devices/{self.device_id}/control"

    @property
    def status_topic(self) -> str:
        return f"Speaker/devices/{self.device_id}/status"

    def _on_connect(self, client: mqtt.Client, userdata: Any, flags: Any, reason_code: Any, properties: Any) -> None:
        code = int(getattr(reason_code, "value", reason_code))
        if code != 0:
            self.events.add("mqtt_connect_error", value=float(code))
            return
        client.subscribe(self.status_topic, qos=1)
        self.events.add("mqtt_connected")

    def _on_subscribe(
        self,
        client: mqtt.Client,
        userdata: Any,
        mid: int,
        reason_code_list: Any,
        properties: Any,
    ) -> None:
        self.subscription_ready.set()
        self.events.add("mqtt_subscribed", value=float(mid))

    def _on_message(self, client: mqtt.Client, userdata: Any, message: Any) -> None:
        try:
            payload = json.loads(message.payload.decode("utf-8"))
        except Exception:
            return
        if isinstance(payload, dict):
            self.messages.put(payload)
            self.events.add("mqtt_message", detail=str(payload.get("type", "unknown")))

    def start(self) -> None:
        self.client.connect(self.host, self.port, keepalive=30)
        self.client.loop_start()
        deadline = time.monotonic() + 8.0
        while time.monotonic() < deadline:
            if self.client.is_connected() and self.subscription_ready.is_set():
                return
            time.sleep(0.05)
        raise TimeoutError("MQTT connection timeout")

    def close(self) -> None:
        self.client.disconnect()
        self.client.loop_stop()

    def command(self, code: int, name: str, fields: dict[str, Any] | None = None) -> str:
        msg_id = f"lab-{code}-{int(time.time() * 1000):x}-{uuid.uuid4().hex[:4]}"
        fields = fields or {}
        payload: dict[str, Any] = {
            "v": 1,
            "deviceId": self.device_id,
            "cmdId": msg_id,
            "msgId": msg_id,
            "ts": int(time.time() * 1000),
            "cmd": code,
            "cmdName": name,
            **fields,
        }
        if fields:
            payload["params"] = fields
        info = self.client.publish(self.control_topic, json.dumps(payload, separators=(",", ":")), qos=1)
        info.wait_for_publish(timeout=5.0)
        self.events.add("mqtt_command", value=float(code), detail=name)
        return msg_id

    def wait_for(self, predicate: Any, timeout: float) -> dict[str, Any]:
        deadline = time.monotonic() + timeout
        deferred: list[dict[str, Any]] = []
        try:
            while time.monotonic() < deadline:
                try:
                    payload = self.messages.get(timeout=min(0.2, deadline - time.monotonic()))
                except queue.Empty:
                    continue
                if predicate(payload):
                    return payload
                deferred.append(payload)
        finally:
            for payload in deferred:
                self.messages.put(payload)
        raise TimeoutError("expected MQTT response not received")

    def discard(self, predicate: Any) -> None:
        retained: list[dict[str, Any]] = []
        while True:
            try:
                payload = self.messages.get_nowait()
            except queue.Empty:
                break
            if not predicate(payload):
                retained.append(payload)
        for payload in retained:
            self.messages.put(payload)

    def get_state(self, timeout: float = 8.0) -> dict[str, Any]:
        self.discard(lambda item: item.get("type") == "state")
        self.command(106, "GET_STATUS")
        # MCU ts is an RTOS tick, not Unix epoch.  Queue ordering after the
        # command is the only valid freshness boundary here.
        return self.wait_for(lambda item: item.get("type") == "state", timeout)

    def wait_record_playback(self, record_id: str, timeout: float) -> dict[str, Any]:
        event = self.wait_for(
            lambda item: item.get("type") == "record_playback" and
            item.get("recordId") == record_id,
            timeout,
        )
        if not bool(event.get("ok", False)):
            raise RuntimeError(
                f"MCU playback failed record={record_id}: "
                f"{event.get('msg', event.get('message', ''))}"
            )
        return event

    def set_feedback(self, enabled: bool, session_id: str = "", talk_id: str = "") -> None:
        fields: dict[str, Any] = {"enabled": 1 if enabled else 0}
        if enabled:
            fields.update({
                "sessionId": session_id,
                "talkId": talk_id,
                "codec": "opus",
                "sampleRate": 16000,
                "channels": 1,
                "packetMs": 20,
                "ttlMs": 120000,
            })
        msg_id = self.command(116, "SET_PLAYBACK_FEEDBACK", fields)
        ack = self.wait_for(
            lambda item: item.get("type") == "ack" and
            (item.get("msgId") == msg_id or item.get("cmdId") == msg_id),
            8.0,
        )
        if not bool(ack.get("ok", False)):
            raise RuntimeError(f"MCU rejected feedback command: {ack.get('message', '')}")


class FeedbackReceiver:
    def __init__(
        self,
        relay: tuple[str, int],
        token: str,
        device_id: str,
        session_id: str,
        talk_id: str,
        events: EventLog,
    ) -> None:
        self.relay = relay
        self.token = token
        self.device_id = device_id
        self.session_id = session_id
        self.talk_id = talk_id
        self.events = events
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.settimeout(0.2)
        self.stop_event = threading.Event()
        self.thread: threading.Thread | None = None
        self.sequences: list[int] = []
        self.receive_times: list[float] = []
        self.payload_bytes = 0

    def registration(self, enabled: bool) -> bytes:
        prefix = "HLAPP1" if enabled else "HLAPP0"
        return f"{prefix} {self.token} {self.device_id} {self.session_id} {self.talk_id}".encode()

    def start(self) -> None:
        expected = f"HLAPPACK1 REGISTERED {self.device_id} {self.session_id} {self.talk_id}".encode()
        deadline = time.monotonic() + 8.0
        last_send = 0.0
        while time.monotonic() < deadline:
            now = time.monotonic()
            if now - last_send >= 1.0:
                self.sock.sendto(self.registration(True), self.relay)
                last_send = now
            try:
                packet, address = self.sock.recvfrom(4096)
            except socket.timeout:
                continue
            if address == self.relay and packet == expected:
                self.events.add("feedback_registered")
                self.thread = threading.Thread(target=self._receive, daemon=True)
                self.thread.start()
                return
        raise TimeoutError("feedback relay registration timeout")

    def refresh(self) -> None:
        self.sock.sendto(self.registration(True), self.relay)

    def _receive(self) -> None:
        while not self.stop_event.is_set():
            try:
                packet, address = self.sock.recvfrom(4096)
            except socket.timeout:
                continue
            if address != self.relay or len(packet) < UDP_FIXED_HEADER_BYTES:
                continue
            fields = struct.unpack_from("<HBBHHIIHBBHHBBBB", packet)
            magic, version, codec, header_bytes, flags, sequence = fields[:6]
            payload_bytes = fields[10]
            if (
                magic != UDP_MAGIC or version != UDP_VERSION or codec != CODEC_OPUS or
                flags & FLAG_FEEDBACK == 0 or flags & FLAG_ACK != 0 or
                len(packet) != header_bytes + payload_bytes
            ):
                continue
            now = time.monotonic()
            self.sequences.append(sequence)
            self.receive_times.append(now)
            self.payload_bytes += payload_bytes
            self.events.add("feedback_packet", chunk=sequence, value=float(payload_bytes))

    def close(self) -> None:
        self.stop_event.set()
        try:
            self.sock.sendto(self.registration(False), self.relay)
        except OSError:
            pass
        if self.thread:
            self.thread.join(timeout=1.0)
        self.sock.close()

    def summary(self) -> dict[str, Any]:
        gaps = [
            (self.receive_times[index] - self.receive_times[index - 1]) * 1000.0
            for index in range(1, len(self.receive_times))
        ]
        unique_sequences = set(self.sequences)
        missing = 0
        duplicates = len(self.sequences) - len(unique_sequences)
        reordered = 0
        for previous, current in zip(self.sequences, self.sequences[1:]):
            if current < previous:
                reordered += 1
        if unique_sequences:
            missing = (
                max(unique_sequences) - min(unique_sequences) + 1 -
                len(unique_sequences)
            )
        return {
            "packets": len(self.sequences),
            "payloadBytes": self.payload_bytes,
            "missingSequences": missing,
            "duplicates": duplicates,
            "reorderedTransitions": reordered,
            "maxInterarrivalMs": max(gaps, default=0.0),
            "meanInterarrivalMs": sum(gaps) / len(gaps) if gaps else 0.0,
        }


def register_media_socket(
    sock: socket.socket,
    relay: tuple[str, int],
    token: str,
    device_id: str,
    session_id: str,
    record_id: str,
) -> None:
    command = f"HLAPP1 {token} {device_id} {session_id} {record_id}".encode()
    expected = f"HLAPPACK1 REGISTERED {device_id} {session_id} {record_id}".encode()
    deadline = time.monotonic() + 8.0
    last_send = 0.0
    while time.monotonic() < deadline:
        now = time.monotonic()
        if now - last_send >= 1.0:
            sock.sendto(command, relay)
            last_send = now
        try:
            packet, address = sock.recvfrom(4096)
        except socket.timeout:
            continue
        if address == relay and packet == expected:
            return
    raise TimeoutError("media relay registration timeout")


def send_media(
    relay: tuple[str, int],
    token: str,
    device_id: str,
    fixture: bytes,
    duration_ms: int,
    volume: int,
    round_index: int,
    send_window: int,
    retry_timeout_ms: int,
    send_pacing_ms: int,
    events: EventLog,
) -> MediaResult:
    suffix = f"{int(time.time() * 1000):x}"[-10:].upper()
    session_id = f"LABPTT_{suffix}_{round_index}"
    record_id = f"LABTALK_{suffix}_{round_index}"
    packets = media_chunks(
        fixture, device_id, session_id, record_id, duration_ms, volume
    )
    attempts = [0] * len(packets)
    sent_at = [0.0] * len(packets)
    expected_chunk = 0
    fast_retransmit_chunk = -1
    sends = 0
    max_ack_silence = 0.0

    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        sock.settimeout(0.1)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 128 * 1024)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 128 * 1024)
        register_media_socket(sock, relay, token, device_id, session_id, record_id)
        events.add("media_registered", round_index=round_index)
        started = time.monotonic()
        last_ack = started
        deadline = started + max(20.0, len(packets) * 1.5)
        while expected_chunk < len(packets) and time.monotonic() < deadline:
            now = time.monotonic()
            outstanding = sum(
                1 for index in range(expected_chunk, len(packets))
                if sent_at[index] != 0.0 and
                now - sent_at[index] < retry_timeout_ms / 1000.0
            )
            # 0 号块携带启动会话所需的完整元数据。先确认它，再打开滑动
            # 窗口，避免公网乱序令尚未建会话的 MCU 丢弃整个首窗口。
            send_end = 1 if expected_chunk == 0 else len(packets)
            for index in range(expected_chunk, send_end):
                due = (
                    sent_at[index] == 0.0 or
                    now - sent_at[index] >= retry_timeout_ms / 1000.0
                )
                if not due or outstanding >= send_window:
                    continue
                if attempts[index] >= 30:
                    raise TimeoutError(f"chunk retry limit: {index + 1}/{len(packets)}")
                sock.sendto(packets[index], relay)
                attempts[index] += 1
                sent_at[index] = time.monotonic()
                outstanding += 1
                sends += 1
                events.add(
                    "media_send",
                    round_index=round_index,
                    chunk=index,
                    value=float(attempts[index]),
                )
                if send_pacing_ms > 0:
                    time.sleep(send_pacing_ms / 1000.0)
            try:
                packet, address = sock.recvfrom(4096)
            except socket.timeout:
                continue
            if address != relay:
                continue
            ack = parse_media_ack(packet, device_id, session_id, record_id)
            if ack is None:
                continue
            status, chunk_index, cumulative = ack
            now = time.monotonic()
            max_ack_silence = max(max_ack_silence, (now - last_ack) * 1000.0)
            last_ack = now
            events.add(
                "media_ack",
                round_index=round_index,
                chunk=chunk_index,
                value=float(cumulative),
                detail=f"status={status}",
            )
            if status == 0:
                progressed = max(expected_chunk, min(cumulative, len(packets)))
                if progressed != expected_chunk:
                    expected_chunk = progressed
                    fast_retransmit_chunk = -1
            elif status == 3 and cumulative < len(packets):
                expected_chunk = min(expected_chunk, cumulative)
                # 一个窗口内可能有多块都报告同一个缺口；只快速重传一次，
                # 后续相同 GAP 交给正常超时，防止同一块被 ACK 风暴放大。
                if fast_retransmit_chunk != cumulative:
                    sent_at[cumulative] = 0.0
                    fast_retransmit_chunk = cumulative
            elif status == 1:
                raise RuntimeError(
                    f"MCU media service busy chunk={chunk_index} "
                    f"expected={cumulative}"
                )
            elif status == 2:
                raise RuntimeError("MCU rejected media packet")
        if expected_chunk != len(packets):
            raise TimeoutError(f"media transfer timeout: {expected_chunk}/{len(packets)}")
        try:
            sock.sendto(
                f"HLAPP0 {token} {device_id} {session_id} {record_id}".encode(), relay
            )
        except OSError:
            pass

    elapsed_ms = (time.monotonic() - started) * 1000.0
    return MediaResult(
        round=round_index,
        session_id=session_id,
        record_id=record_id,
        chunks=len(packets),
        bytes=len(fixture),
        elapsed_ms=elapsed_ms,
        sends=sends,
        retries=sends - len(packets),
        max_ack_silence_ms=max_ack_silence,
    )


def ensure_fixture(mcu_root: Path, output_dir: Path, duration_ms: int, amplitude: int) -> Path:
    build_dir = output_dir / "fixture-build"
    opus_build = build_dir / "opus"
    helper = build_dir / "speaker_virtual_app_fixture"
    fixture = output_dir / "quiet-48k-32k.opus"
    build_dir.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [
            "cmake", "-S", str(mcu_root / "Middlewares/Third_Party/opus"),
            "-B", str(opus_build),
            "-DOPUS_BUILD_SHARED_LIBRARY=OFF",
            "-DOPUS_BUILD_PROGRAMS=OFF",
            "-DOPUS_BUILD_TESTING=OFF",
            "-DOPUS_DISABLE_INTRINSICS=ON",
            "-DOPUS_INSTALL_PKG_CONFIG_MODULE=OFF",
            "-DOPUS_INSTALL_CMAKE_CONFIG_MODULE=OFF",
        ],
        check=True,
        stdout=subprocess.DEVNULL,
    )
    subprocess.run(
        ["cmake", "--build", str(opus_build)],
        check=True,
        stdout=subprocess.DEVNULL,
    )
    subprocess.run(
        [
            "cc", "-std=c11", "-Wall", "-Wextra", "-Werror",
            "-I", str(mcu_root / "Middlewares/Third_Party/opus/include"),
            str(ROOT / "tools/speaker_virtual_app_fixture.c"),
            str(opus_build / "libopus.a"), "-lm", "-o", str(helper),
        ],
        check=True,
    )
    subprocess.run(
        [str(helper), str(fixture), str(duration_ms), str(amplitude)],
        check=True,
    )
    return fixture


def state_metrics(state: dict[str, Any]) -> dict[str, Any]:
    return {
        "audio": state.get("audio", {}),
        "feedback": state.get("feedback", {}),
        "playing": state.get("playing"),
        "lastError": state.get("lastError"),
    }


def metric_delta(before: dict[str, Any], after: dict[str, Any], group: str, key: str) -> int:
    return int(after.get(group, {}).get(key, 0)) - int(before.get(group, {}).get(key, 0))


def write_outputs(
    output_dir: Path,
    args: argparse.Namespace,
    events: EventLog,
    media_results: list[MediaResult],
    feedback: dict[str, Any],
    before: dict[str, Any],
    after: dict[str, Any],
    serial_capture: SerialCapture,
) -> bool:
    output_dir.mkdir(parents=True, exist_ok=True)
    with (output_dir / "events.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(asdict(Event(0, "")).keys()))
        writer.writeheader()
        with events.lock:
            writer.writerows(asdict(event) for event in events.events)
    (output_dir / "mcu-serial.bin").write_bytes(serial_capture.data)
    (output_dir / "mcu-serial.txt").write_text(
        serial_capture.data.decode("utf-8", errors="replace"), encoding="utf-8"
    )

    serial_text = serial_capture.data.decode("utf-8", errors="replace")
    stream_rebuffers = serial_text.count("stream rebuffer")
    deltas = {
        "outUnderruns": metric_delta(before, after, "audio", "outUnderruns"),
        "dmaErrors": metric_delta(before, after, "audio", "dmaErrors"),
        "fillLate": metric_delta(before, after, "audio", "fillLate"),
        "saiErrors": metric_delta(before, after, "audio", "saiErrors"),
        "feedbackDropped": metric_delta(before, after, "feedback", "packetsDropped"),
        "feedbackSendErrors": metric_delta(before, after, "feedback", "sendErrors"),
        "feedbackQueueOverflows": metric_delta(
            before, after, "feedback", "uplinkQueueOverflows"
        ),
        "feedbackRawOverflows": metric_delta(before, after, "feedback", "rawQueueOverflows"),
        "aecDeadlineMisses": metric_delta(before, after, "feedback", "aecDeadlineMisses"),
    }
    underrun_ms = deltas["outUnderruns"] * 1000.0 / OUTPUT_RATE
    media_ok = True  # Every returned result has a confirmed successful playback event.
    feedback_required = args.mode in {"feedback-only", "duplex-real"}
    feedback_ok = (
        not feedback_required or
        (feedback.get("packets", 0) > 0 and
         feedback.get("missingSequences", 0) == 0 and
         feedback.get("maxInterarrivalMs", 0.0) < FEEDBACK_JITTER_BUDGET_MS)
    )
    hardware_ok = (
        all(value == 0 for value in deltas.values()) and
        stream_rebuffers == 0
    )
    passed = media_ok and feedback_ok and hardware_ok
    report = {
        "result": "PASS" if passed else "FAIL",
        "mode": args.mode,
        "deviceId": args.device_id,
        "rounds": args.rounds,
        "fixtureDurationMs": args.duration_ms,
        "fixtureAmplitude": args.amplitude,
        "playbackVolume": args.volume,
        "sendWindow": args.send_window,
        "retryTimeoutMs": args.retry_timeout_ms,
        "sendPacingMs": args.send_pacing_ms,
        "media": [asdict(item) for item in media_results],
        "feedback": feedback,
        "counterDeltas": deltas,
        "outUnderrunMs": underrun_ms,
        "streamRebuffers": stream_rebuffers,
        "transportWarnings": {
            "mediaRetries": sum(item.retries for item in media_results),
            "mediaMaxAckSilenceMs": max(
                (item.max_ack_silence_ms for item in media_results), default=0.0
            ),
            "feedbackGapsOverJitterBudget":
                feedback.get("maxInterarrivalMs", 0.0) >=
                FEEDBACK_JITTER_BUDGET_MS,
            "feedbackQueueHighWater":
                int(after.get("feedback", {}).get("uplinkQueueHighWater", 0)),
        },
        "stateBefore": state_metrics(before),
        "stateAfter": state_metrics(after),
        "serialBytes": len(serial_capture.data),
        "serialError": serial_capture.error,
    }
    (output_dir / "report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    return passed


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--mode",
        choices=(
            "downlink-only",
            "feedback-only",
            "downlink-feedback-load",
            "duplex-real",
        ),
        default="downlink-only",
    )
    parser.add_argument("--device-id", default=DEFAULT_DEVICE_ID)
    parser.add_argument("--rounds", type=int, default=5)
    parser.add_argument("--duration-ms", type=int, default=4000)
    parser.add_argument("--observe-seconds", type=float, default=12.0)
    parser.add_argument("--amplitude", type=int, default=1200, help="PCM peak before MCU volume; max 4000")
    parser.add_argument("--volume", type=int, default=10, help="MCU playback volume percent")
    parser.add_argument("--settle-seconds", type=float, default=2.0)
    parser.add_argument("--send-window", type=int, default=4)
    parser.add_argument("--retry-timeout-ms", type=int, default=300)
    parser.add_argument("--send-pacing-ms", type=int, default=0)
    parser.add_argument("--fixture", type=Path)
    parser.add_argument("--mcu-root", type=Path, default=Path(os.environ.get("TJI_SPEAKER_MCU_ROOT", DEFAULT_MCU_ROOT)))
    parser.add_argument("--relay-host", default=os.environ.get("TJI_SPEAKER_RELAY_HOST") or generated_build_config_value("TJI_SPEAKER_RELAY_HOST"))
    parser.add_argument("--relay-port", type=int, default=int(os.environ.get("TJI_SPEAKER_RELAY_PORT") or generated_build_config_value("TJI_SPEAKER_RELAY_PORT") or 7000))
    parser.add_argument("--relay-token", default=os.environ.get("TJI_SPEAKER_RELAY_TOKEN") or generated_build_config_value("TJI_SPEAKER_RELAY_TOKEN"))
    parser.add_argument("--mqtt-host")
    parser.add_argument("--mqtt-port", type=int)
    parser.add_argument("--mqtt-username", default=os.environ.get("TJI_MQTT_USERNAME", ""))
    parser.add_argument("--serial-port", default="/dev/cu.usbmodem0000697303491")
    parser.add_argument("--serial-baud", type=int, default=115200)
    parser.add_argument("--output-dir", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not args.relay_host or not args.relay_token or not args.relay_token.strip():
        raise RuntimeError("relay host/token unavailable; build the configured debug App or set environment variables")
    if (args.rounds < 1 or args.volume not in range(0, 31) or
        args.send_window not in range(1, 9) or
        args.retry_timeout_ms not in range(100, 2001) or
        args.send_pacing_ms not in range(0, 101)):
        raise ValueError(
            "rounds must be positive; diagnostic volume 0..30; "
            "send window 1..8; retry timeout 100..2000 ms; "
            "send pacing 0..100 ms"
        )
    config_path = args.mcu_root / "Config/speaker_config.h"
    args.mqtt_host = args.mqtt_host or c_define_value(config_path, "SPEAKER_CFG_MQTT_HOST")
    args.mqtt_port = args.mqtt_port or int(c_define_value(config_path, "SPEAKER_CFG_MQTT_PORT") or 1883)
    if not args.mqtt_host:
        raise RuntimeError("MQTT host unavailable")
    output_dir = args.output_dir or (
        ROOT / "build/speaker-virtual-app" / datetime.now().strftime("%Y%m%d-%H%M%S")
    )
    output_dir.mkdir(parents=True, exist_ok=True)
    fixture_path = args.fixture or ensure_fixture(
        args.mcu_root, output_dir, args.duration_ms, args.amplitude
    )
    fixture = fixture_path.read_bytes()
    if not fixture.startswith(b"OggS"):
        raise ValueError("fixture is not Ogg Opus")

    events = EventLog()
    serial_capture = SerialCapture(
        None if args.serial_port == "none" else args.serial_port,
        args.serial_baud,
    )
    control = MqttControl(
        args.mqtt_host, args.mqtt_port, args.mqtt_username, args.device_id, events
    )
    feedback_receiver: FeedbackReceiver | None = None
    feedback_enabled = False
    media_results: list[MediaResult] = []
    before: dict[str, Any] = {}
    after: dict[str, Any] = {}
    serial_capture.start()
    control.start()
    try:
        # Every run starts from a known single-chain baseline. This also keeps
        # a previously open phone App from leaving an old feedback lease active.
        control.set_feedback(False)
        if args.mode in {"feedback-only", "downlink-feedback-load", "duplex-real"}:
            suffix = f"{int(time.time() * 1000):x}"[-10:].upper()
            feedback_session = f"LABFB_{suffix}"
            feedback_talk = f"LABMON_{suffix}"
            if args.mode in {"feedback-only", "duplex-real"}:
                relay_ip = socket.gethostbyname(args.relay_host)
                feedback_receiver = FeedbackReceiver(
                    (relay_ip, args.relay_port),
                    args.relay_token,
                    args.device_id,
                    feedback_session,
                    feedback_talk,
                    events,
                )
                feedback_receiver.start()
            control.set_feedback(True, feedback_session, feedback_talk)
            feedback_enabled = True
            time.sleep(1.0)
        before = control.get_state()

        relay = (socket.gethostbyname(args.relay_host), args.relay_port)
        if args.mode == "feedback-only":
            deadline = time.monotonic() + args.observe_seconds
            while time.monotonic() < deadline:
                time.sleep(min(2.0, deadline - time.monotonic()))
                if feedback_receiver:
                    feedback_receiver.refresh()
        else:
            for round_index in range(1, args.rounds + 1):
                media_results.append(
                    send_media(
                        relay,
                        args.relay_token,
                        args.device_id,
                        fixture,
                        args.duration_ms,
                        args.volume,
                        round_index,
                        args.send_window,
                        args.retry_timeout_ms,
                        args.send_pacing_ms,
                        events,
                    )
                )
                playback_timeout = max(15.0, args.duration_ms / 1000.0 + 12.0)
                control.wait_record_playback(
                    media_results[-1].record_id,
                    playback_timeout,
                )
                events.add("playback_done", round_index=round_index)
                if feedback_receiver:
                    feedback_receiver.refresh()
                time.sleep(args.settle_seconds)
        time.sleep(1.0)
        after = control.get_state()
    finally:
        if feedback_enabled:
            try:
                control.set_feedback(False)
            except Exception as exc:
                events.add("feedback_stop_error", detail=str(exc))
        if feedback_receiver:
            feedback_receiver.close()
        control.close()
        serial_capture.stop()

    feedback_summary = feedback_receiver.summary() if feedback_receiver else {}
    passed = write_outputs(
        output_dir,
        args,
        events,
        media_results,
        feedback_summary,
        before,
        after,
        serial_capture,
    )
    print(f"report={output_dir / 'report.json'}")
    print(f"result={'PASS' if passed else 'FAIL'}")
    return 0 if passed else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError, TimeoutError, subprocess.SubprocessError) as exc:
        print(f"result=ERROR message={exc}", file=os.sys.stderr)
        raise SystemExit(2)
