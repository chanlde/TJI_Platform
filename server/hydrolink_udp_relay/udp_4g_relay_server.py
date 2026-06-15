#!/usr/bin/env python3
"""Public UDP relay for 4G PPP speaker audio forwarding.

The STM32 device sends heartbeat packets to this server so the server can learn
the carrier-NAT public endpoint. App audio packets must carry a speaker UDP v2
header with deviceId, and are routed only to the matching online device.
"""

from __future__ import annotations

import argparse
import socket
import struct
import time
from dataclasses import dataclass


DEVICE_HELLO_PREFIX = b"HLDEV1 "
STATUS_PREFIX = b"HLSTAT1 "
SPEAKER_AUDIO_MAGIC_LE = b"\x5a\xa5"
SPEAKER_AUDIO_V2 = 2
SPEAKER_AUDIO_CODEC_IMA_ADPCM = 1
SPEAKER_AUDIO_V2_FORMAL_FIXED_HEADER = 28
SPEAKER_AUDIO_V2_LEGACY_ROUTING_HEADER = 24
SPEAKER_AUDIO_V2_TALK_ROUTING_HEADER = 25
DEFAULT_LISTEN_HOST = "0.0.0.0"
DEFAULT_LISTEN_PORT = 7000
DEFAULT_TIMEOUT_S = 30.0


@dataclass
class DeviceState:
    addr: tuple[str, int] | None = None
    device_id: str = ""
    last_seen: float = 0.0
    rx_heartbeats: int = 0
    forwarded_packets: int = 0
    dropped_packets: int = 0

    def online(self, now: float, timeout_s: float) -> bool:
        return self.addr is not None and (now - self.last_seen) <= timeout_s


def parse_device_hello(packet: bytes, token: str) -> str | None:
    if not packet.startswith(DEVICE_HELLO_PREFIX):
        return None

    # Format: b"HLDEV1 <token> <device_id>"
    try:
        text = packet.decode("utf-8", errors="strict").strip()
    except UnicodeDecodeError:
        return None

    parts = text.split(maxsplit=2)
    if len(parts) < 2 or parts[0] != "HLDEV1":
        return None
    if token and parts[1] != token:
        return None
    return parts[2] if len(parts) >= 3 else "default"


def parse_status_request(packet: bytes, token: str) -> bool:
    if not packet.startswith(STATUS_PREFIX):
        return False

    try:
        text = packet.decode("utf-8", errors="strict").strip()
    except UnicodeDecodeError:
        return False

    parts = text.split(maxsplit=1)
    return len(parts) == 2 and parts[0] == "HLSTAT1" and (not token or parts[1] == token)


def parse_target_device_id(packet: bytes) -> str | None:
    """Extract target deviceId from the App speaker UDP v2 header.

    Formal v2 layout from Notion:
      uint16 magic, uint8 version, uint8 codec
      uint16 headerLen, uint16 flags
      uint32 seq, uint32 timestampMs
      uint16 sampleRate, uint8 channels, uint8 packetMs
      uint16 payloadLen, uint16 sampleCount
      uint8 deviceIdLen, uint8 sessionIdLen, uint8 talkIdLen, uint8 reserved
      deviceId, sessionId, talkId, payload

    Legacy routed V2 layout:
      0..1 magic 0xA55A little-endian
      2    version=2
      3    codec
      4..19 legacy audio fields
      20   stream type
      21   deviceId byte length
      22   taskId byte length
      23   reserved
      24.. deviceId, then taskId

    Current routed V2 layout for record-store packets adds talkId length:
      23   talkId byte length
      24   reserved
      25.. deviceId, then taskId, then talkId
    """
    if len(packet) < SPEAKER_AUDIO_V2_LEGACY_ROUTING_HEADER:
        return None
    if packet[0:2] != SPEAKER_AUDIO_MAGIC_LE or packet[2] != SPEAKER_AUDIO_V2:
        return None

    formal = parse_formal_v2_device_id(packet)
    if formal is not None:
        return formal

    if len(packet) >= SPEAKER_AUDIO_V2_TALK_ROUTING_HEADER and packet[24] == 0:
        current = parse_target_device_id_at(packet, base_offset=SPEAKER_AUDIO_V2_TALK_ROUTING_HEADER)
        if current is not None:
            return current
    return parse_target_device_id_at(packet, base_offset=SPEAKER_AUDIO_V2_LEGACY_ROUTING_HEADER)


def parse_formal_v2_device_id(packet: bytes) -> str | None:
    if len(packet) < SPEAKER_AUDIO_V2_FORMAL_FIXED_HEADER:
        return None

    (
        _magic,
        version,
        codec,
        header_len,
        _flags,
        _seq,
        _timestamp_ms,
        sample_rate,
        channels,
        packet_ms,
        payload_len,
        _sample_count,
        device_len,
        session_len,
        talk_len,
        _reserved,
    ) = struct.unpack_from("<HBBHHIIHBBHHBBBB", packet, 0)

    if version != SPEAKER_AUDIO_V2 or codec != SPEAKER_AUDIO_CODEC_IMA_ADPCM:
        return None
    if sample_rate != 8000 or channels != 1 or packet_ms != 40:
        return None
    if device_len <= 0:
        return None
    if header_len < SPEAKER_AUDIO_V2_FORMAL_FIXED_HEADER:
        return None
    expected_header = SPEAKER_AUDIO_V2_FORMAL_FIXED_HEADER + device_len + session_len + talk_len
    if header_len != expected_header:
        return None
    if len(packet) < header_len + payload_len:
        return None

    try:
        device_id = packet[
            SPEAKER_AUDIO_V2_FORMAL_FIXED_HEADER :
            SPEAKER_AUDIO_V2_FORMAL_FIXED_HEADER + device_len
        ].decode("utf-8", errors="strict")
    except UnicodeDecodeError:
        return None
    return device_id if device_id else None


def parse_target_device_id_at(packet: bytes, base_offset: int) -> str | None:
    if len(packet) < base_offset:
        return None

    device_len = packet[21]
    task_len = packet[22]
    talk_len = packet[23] if base_offset == SPEAKER_AUDIO_V2_TALK_ROUTING_HEADER else 0
    expected_header = base_offset + device_len + task_len + talk_len
    if device_len <= 0 or len(packet) < expected_header:
        return None

    try:
        device_id = packet[base_offset : base_offset + device_len].decode("utf-8", errors="strict")
    except UnicodeDecodeError:
        return None
    return device_id if device_id else None


def format_status_response(
    devices: dict[str, DeviceState],
    latest_device_id: str,
    now: float,
    timeout_s: float,
) -> bytes:
    online_count = sum(1 for state in devices.values() if state.online(now, timeout_s))
    rows: list[str] = []
    for device_id, state in sorted(devices.items()):
        age = (now - state.last_seen) if state.addr is not None else -1.0
        addr = "%s:%d" % state.addr if state.addr is not None else "-"
        rows.append(
            "id=%s %s age=%.1f addr=%s heartbeats=%d forwarded=%d dropped=%d"
            % (
                device_id,
                "online" if state.online(now, timeout_s) else "offline",
                age,
                addr,
                state.rx_heartbeats,
                state.forwarded_packets,
                state.dropped_packets,
            )
        )
    text = "HLSTAT1 devices=%d online=%d latest=%s %s" % (
        len(devices),
        online_count,
        latest_device_id or "-",
        " | ".join(rows) if rows else "no-device",
    )
    return text.encode("utf-8")


def forward_packet(
    sock: socket.socket,
    packet: bytes,
    state: DeviceState,
    now: float,
    started: float,
) -> None:
    assert state.addr is not None
    sock.sendto(packet, state.addr)
    state.forwarded_packets += 1
    if (state.forwarded_packets % 100) == 0:
        age = now - state.last_seen
        uptime = now - started
        print(
            "routed forwarded=%d dropped=%d id=%s device=%s:%d age=%.1fs uptime=%.0fs"
            % (
                state.forwarded_packets,
                state.dropped_packets,
                state.device_id,
                state.addr[0],
                state.addr[1],
                age,
                uptime,
            )
        )


def serve(listen_host: str, listen_port: int, token: str, timeout_s: float) -> None:
    devices: dict[str, DeviceState] = {}
    latest_device_id = ""
    started = time.time()

    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        sock.bind((listen_host, listen_port))
        print(f"UDP relay listening on {listen_host}:{listen_port}")
        print('Device heartbeat format: "HLDEV1 <token> <device_id>"')

        while True:
            packet, addr = sock.recvfrom(4096)
            now = time.time()
            device_id = parse_device_hello(packet, token)

            if parse_status_request(packet, token):
                sock.sendto(format_status_response(devices, latest_device_id, now, timeout_s), addr)
                continue

            if device_id is not None:
                state = devices.get(device_id)
                if state is None:
                    state = DeviceState(device_id=device_id)
                    devices[device_id] = state
                state.addr = addr
                state.device_id = device_id
                state.last_seen = now
                state.rx_heartbeats += 1
                latest_device_id = device_id
                if (state.rx_heartbeats % 10) == 1:
                    print(f"device online id={device_id} addr={addr[0]}:{addr[1]}")
                continue

            target_device_id = parse_target_device_id(packet)
            if target_device_id:
                state = devices.get(target_device_id)
                if state is not None and state.online(now, timeout_s):
                    forward_packet(sock, packet, state, now, started)
                else:
                    if state is not None:
                        state.dropped_packets += 1
                    if state is None or (state.dropped_packets % 50) == 1:
                        print(f"drop routed packet: target offline id={target_device_id}")
                continue

            if latest_device_id and latest_device_id in devices:
                devices[latest_device_id].dropped_packets += 1
                dropped = devices[latest_device_id].dropped_packets
            else:
                dropped = 1
            if (dropped % 50) == 1:
                print("drop packet: missing or invalid target deviceId")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--listen", default=DEFAULT_LISTEN_HOST)
    parser.add_argument("--port", type=int, default=DEFAULT_LISTEN_PORT)
    parser.add_argument("--token", default="hydrolink")
    parser.add_argument("--timeout", type=float, default=DEFAULT_TIMEOUT_S)
    args = parser.parse_args()

    serve(args.listen, args.port, args.token, args.timeout)


if __name__ == "__main__":
    main()
