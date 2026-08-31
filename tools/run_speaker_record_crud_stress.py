#!/usr/bin/env python3
"""Safely stress the MCU recording CRUD path with disposable LAB_CRUD_* data."""

from __future__ import annotations

import argparse
import json
import os
import socket
import struct
import time
import uuid
import zlib
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import run_speaker_virtual_app_stress as lab


ROOT = Path(__file__).resolve().parents[1]
TEST_PREFIX = "LAB_CRUD_"
STORE_MODE = 2
RECORD_TYPE_RECORD = 0


def store_packets(
    fixture: bytes,
    device_id: str,
    session_id: str,
    record_id: str,
    name: str,
    duration_ms: int,
) -> list[bytes]:
    groups = lab.grouped_pages(fixture)
    file_crc = zlib.crc32(fixture) & 0xFFFFFFFF
    encoded_name = name.encode("utf-8")
    created = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ").encode("ascii")
    packets: list[bytes] = []
    for index, data in enumerate(groups):
        first = index == 0
        metadata = encoded_name + created if first else b""
        header = struct.pack(
            "<IBBBBHHIIIIIBBBB",
            lab.CHUNK_MAGIC,
            1,
            STORE_MODE,
            0,
            RECORD_TYPE_RECORD,
            index,
            len(groups),
            len(fixture),
            file_crc,
            duration_ms,
            lab.BITRATE,
            zlib.crc32(data) & 0xFFFFFFFF,
            len(encoded_name) if first else 0,
            len(created) if first else 0,
            1,
            0,
        )
        flags = lab.FLAG_PLAYBACK
        if first:
            flags |= lab.FLAG_START
        if index + 1 == len(groups):
            flags |= lab.FLAG_LAST
        packets.append(
            lab.udp_packet(
                device_id, session_id, record_id, flags, index,
                header + metadata + data,
            )
        )
    return packets


def send_store(
    relay: tuple[str, int],
    token: str,
    device_id: str,
    fixture: bytes,
    duration_ms: int,
    record_id: str,
    name: str,
) -> float:
    session_id = f"CRUD_{uuid.uuid4().hex[:12].upper()}"
    packets = store_packets(
        fixture, device_id, session_id, record_id, name, duration_ms
    )
    started = time.monotonic()
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        sock.settimeout(0.25)
        lab.register_media_socket(
            sock, relay, token, device_id, session_id, record_id
        )
        expected = 0
        attempts = 0
        while expected < len(packets):
            sock.sendto(packets[expected], relay)
            attempts += 1
            if attempts > len(packets) * 30:
                raise TimeoutError(f"store ACK timeout {record_id}: {expected}/{len(packets)}")
            try:
                response, address = sock.recvfrom(4096)
            except socket.timeout:
                continue
            if address != relay:
                continue
            ack = lab.parse_media_ack(
                response, device_id, session_id, record_id
            )
            if ack is None:
                continue
            status, _, next_expected = ack
            if status == 0:
                expected = max(expected, min(next_expected, len(packets)))
            elif status == 3:
                expected = min(next_expected, len(packets) - 1)
            elif status == 1:
                raise RuntimeError(f"record store busy: {record_id}")
            else:
                raise RuntimeError(f"record store rejected packet: {record_id}")
        sock.sendto(
            f"HLAPP0 {token} {device_id} {session_id} {record_id}".encode(),
            relay,
        )
    return (time.monotonic() - started) * 1000.0


def command_event(
    control: lab.MqttControl,
    code: int,
    name: str,
    fields: dict[str, Any],
    event_type: str,
    timeout: float = 8.0,
) -> tuple[dict[str, Any], float]:
    started = time.monotonic()
    msg_id = control.command(code, name, fields)
    event = control.wait_for(
        lambda item: item.get("type") == event_type and
        (item.get("cmdId") == msg_id or item.get("msgId") == msg_id),
        timeout,
    )
    return event, (time.monotonic() - started) * 1000.0


def list_records(control: lab.MqttControl) -> tuple[list[dict[str, Any]], list[float]]:
    records: list[dict[str, Any]] = []
    timings: list[float] = []
    offset = 0
    for _ in range(16):
        event, elapsed = command_event(
            control, 119, "LIST_RECORDS",
            {"offset": offset, "limit": 4}, "record_list",
        )
        timings.append(elapsed)
        items = event.get("items", event.get("records", []))
        if not isinstance(items, list):
            raise RuntimeError("record_list items is not an array")
        records.extend(item for item in items if isinstance(item, dict))
        next_offset = int(event.get("nextOffset", offset + len(items)))
        has_more = bool(event.get("hasMore", False))
        if next_offset < offset or (has_more and next_offset == offset):
            raise RuntimeError(
                f"record_list cursor did not advance: {offset}->{next_offset}"
            )
        offset = next_offset
        if not has_more:
            total = int(event.get("total", len(records)))
            if total != len(records):
                raise RuntimeError(
                    f"record_list total mismatch: total={total} parsed={len(records)}"
                )
            ids = [str(item.get("recordId", "")) for item in records]
            if len(ids) != len(set(ids)):
                raise RuntimeError("record_list contains duplicate recordId")
            return records, timings
    raise RuntimeError("record_list pagination exceeded 16 pages")


def storage_status(control: lab.MqttControl) -> dict[str, Any]:
    event, _ = command_event(
        control, 122, "GET_STORAGE_STATUS", {}, "storage_status"
    )
    if not bool(event.get("ok", True)):
        raise RuntimeError(f"storage query failed: {event}")
    return event


def expect_ok(event: dict[str, Any], operation: str) -> None:
    if not bool(event.get("ok", False)):
        raise RuntimeError(
            f"{operation} failed code={event.get('code')} "
            f"message={event.get('msg', event.get('message', ''))}"
        )


def delete_fixture(control: lab.MqttControl, record_id: str) -> float:
    event, elapsed = command_event(
        control, 120, "DELETE_RECORD", {"recordId": record_id},
        "record_deleted",
    )
    expect_ok(event, f"delete {record_id}")
    return elapsed


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device-id", default=lab.DEFAULT_DEVICE_ID)
    parser.add_argument("--cycles", type=int, default=5)
    parser.add_argument("--batch-size", type=int, default=6)
    parser.add_argument("--duration-ms", type=int, default=400)
    parser.add_argument("--amplitude", type=int, default=400)
    parser.add_argument("--mcu-root", type=Path, default=lab.DEFAULT_MCU_ROOT)
    parser.add_argument("--relay-host", default=os.environ.get("TJI_SPEAKER_RELAY_HOST") or lab.generated_build_config_value("TJI_SPEAKER_RELAY_HOST"))
    parser.add_argument("--relay-port", type=int, default=int(os.environ.get("TJI_SPEAKER_RELAY_PORT") or lab.generated_build_config_value("TJI_SPEAKER_RELAY_PORT") or 7000))
    parser.add_argument("--relay-token", default=os.environ.get("TJI_SPEAKER_RELAY_TOKEN") or lab.generated_build_config_value("TJI_SPEAKER_RELAY_TOKEN"))
    parser.add_argument("--mqtt-host")
    parser.add_argument("--mqtt-port", type=int)
    parser.add_argument("--mqtt-username", default=os.environ.get("TJI_MQTT_USERNAME", ""))
    parser.add_argument("--output", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.cycles < 1 or args.batch_size < 5 or args.batch_size > 12:
        raise ValueError("cycles must be positive and batch-size must be 5..12")
    if not args.relay_host or not args.relay_token:
        raise RuntimeError("relay configuration unavailable")
    config = args.mcu_root / "Config/speaker_config.h"
    mqtt_host = args.mqtt_host or lab.c_define_value(config, "SPEAKER_CFG_MQTT_HOST")
    mqtt_port = args.mqtt_port or int(lab.c_define_value(config, "SPEAKER_CFG_MQTT_PORT") or 1883)
    if not mqtt_host:
        raise RuntimeError("MQTT host unavailable")

    output = args.output or (
        ROOT / "build/speaker-record-crud" /
        datetime.now().strftime("%Y%m%d-%H%M%S") / "report.json"
    )
    output.parent.mkdir(parents=True, exist_ok=True)
    fixture_path = lab.ensure_fixture(
        args.mcu_root, output.parent, args.duration_ms, args.amplitude
    )
    fixture = fixture_path.read_bytes()
    events = lab.EventLog()
    control = lab.MqttControl(
        mqtt_host, mqtt_port, args.mqtt_username, args.device_id, events
    )
    relay = (args.relay_host, args.relay_port)
    created: set[str] = set()
    timings: dict[str, list[float]] = {
        "create": [], "list": [], "rename": [], "play": [], "delete": []
    }
    control.start()
    baseline_records, baseline_list_times = list_records(control)
    baseline_ids = {str(item.get("recordId")) for item in baseline_records}
    if any(record_id.startswith(TEST_PREFIX) for record_id in baseline_ids):
        raise RuntimeError("pre-existing LAB_CRUD_* record found; refusing ambiguous cleanup")
    before_storage = storage_status(control)
    timings["list"].extend(baseline_list_times)

    try:
        for cycle in range(args.cycles):
            batch: list[str] = []
            stamp = f"{int(time.time() * 1000):X}"
            for index in range(args.batch_size):
                record_id = f"{TEST_PREFIX}{stamp}_{cycle}_{index}"
                original_name = f"CRUD测试-{cycle}-{index}"
                started = time.monotonic()
                send_store(
                    relay, args.relay_token, args.device_id, fixture,
                    args.duration_ms, record_id, original_name,
                )
                saved = control.wait_for(
                    lambda item, rid=record_id: item.get("type") == "record_saved" and
                    item.get("recordId") == rid,
                    10.0,
                )
                created.add(record_id)
                if saved.get("path") in (None, "") or not bool(saved.get("visible", False)):
                    raise RuntimeError(f"create event is incomplete: {record_id}")
                timings["create"].append((time.monotonic() - started) * 1000.0)
                batch.append(record_id)

            listed, page_times = list_records(control)
            timings["list"].extend(page_times)
            listed_ids = {str(item.get("recordId")) for item in listed}
            if not set(batch).issubset(listed_ids):
                raise RuntimeError("created records missing from paged query")

            for index, record_id in enumerate(batch):
                renamed = f"已改名-{cycle}-{index}"
                event, elapsed = command_event(
                    control, 121, "UPDATE_RECORD",
                    {"recordId": record_id, "name": renamed},
                    "record_updated",
                )
                expect_ok(event, f"rename {record_id}")
                timings["rename"].append(elapsed)

            listed, page_times = list_records(control)
            timings["list"].extend(page_times)
            names = {str(item.get("recordId")): str(item.get("name")) for item in listed}
            for index, record_id in enumerate(batch):
                if names.get(record_id) != f"已改名-{cycle}-{index}":
                    raise RuntimeError(f"renamed value not persisted: {record_id}")

            play_event, play_elapsed = command_event(
                control, 118, "PLAY_RECORD",
                {"recordId": batch[0], "volume": 0}, "record_playback", 10.0,
            )
            expect_ok(play_event, f"play {batch[0]}")
            timings["play"].append(play_elapsed)
            time.sleep(args.duration_ms / 1000.0 + 0.3)

            for record_id in batch:
                timings["delete"].append(delete_fixture(control, record_id))
                created.remove(record_id)

            listed, page_times = list_records(control)
            timings["list"].extend(page_times)
            if {str(item.get("recordId")) for item in listed} != baseline_ids:
                raise RuntimeError("record set was not restored after delete cycle")

        missing_id = f"{TEST_PREFIX}MISSING_{uuid.uuid4().hex[:8].upper()}"
        rename_missing, _ = command_event(
            control, 121, "UPDATE_RECORD",
            {"recordId": missing_id, "name": "不存在"}, "record_updated",
        )
        delete_missing, _ = command_event(
            control, 120, "DELETE_RECORD",
            {"recordId": missing_id}, "record_deleted",
        )
        if int(rename_missing.get("code", 0)) != 404 or int(delete_missing.get("code", 0)) != 404:
            raise RuntimeError("missing-record error path did not return 404")

        after_storage = storage_status(control)
        final_records, final_list_times = list_records(control)
        timings["list"].extend(final_list_times)
        final_ids = {str(item.get("recordId")) for item in final_records}
        if final_ids != baseline_ids:
            raise RuntimeError("final record IDs differ from baseline")

        def stats(values: list[float]) -> dict[str, float]:
            ordered = sorted(values)
            return {
                "count": len(values),
                "meanMs": round(sum(values) / len(values), 2) if values else 0.0,
                "maxMs": round(max(values), 2) if values else 0.0,
                "p95Ms": round(ordered[min(len(ordered) - 1, int(len(ordered) * 0.95))], 2) if ordered else 0.0,
            }

        report = {
            "result": "PASS",
            "deviceId": args.device_id,
            "cycles": args.cycles,
            "batchSize": args.batch_size,
            "operations": args.cycles * args.batch_size,
            "baselineRecordIds": sorted(baseline_ids),
            "finalRecordIds": sorted(final_ids),
            "timings": {name: stats(values) for name, values in timings.items()},
            "storageBefore": before_storage,
            "storageAfter": after_storage,
            "freeBytesDelta": int(after_storage.get("freeBytes", 0)) - int(before_storage.get("freeBytes", 0)),
            "leftoverTestRecords": sorted(created),
            "negativePaths": {"renameMissingCode": 404, "deleteMissingCode": 404},
        }
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(report, ensure_ascii=False, indent=2))
        print(f"REPORT={output}")
        return 0
    finally:
        for record_id in list(created):
            try:
                delete_fixture(control, record_id)
            except Exception as error:
                print(f"cleanup failed {record_id}: {error}")
        control.close()


if __name__ == "__main__":
    raise SystemExit(main())
