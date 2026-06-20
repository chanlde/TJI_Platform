#!/usr/bin/env python3
"""Generate a HADP sample with the C++ core and upload it to the speaker server."""

from __future__ import annotations

import argparse
import json
import mimetypes
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
import uuid
from pathlib import Path


DEFAULT_SERVER = "http://146.56.250.203:8008"


def parse_kv(output: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for line in output.splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip()
    return result


def multipart_body(fields: dict[str, str], file_path: Path) -> tuple[bytes, str]:
    boundary = f"----TjiSpeakerCore{uuid.uuid4().hex}"
    chunks: list[bytes] = []

    for key, value in fields.items():
        chunks.append(f"--{boundary}\r\n".encode())
        chunks.append(f'Content-Disposition: form-data; name="{key}"\r\n\r\n'.encode())
        chunks.append(str(value).encode())
        chunks.append(b"\r\n")

    content_type = mimetypes.guess_type(file_path.name)[0] or "application/octet-stream"
    chunks.append(f"--{boundary}\r\n".encode())
    chunks.append(
        (
            f'Content-Disposition: form-data; name="file"; filename="{file_path.name}"\r\n'
            f"Content-Type: {content_type}\r\n\r\n"
        ).encode()
    )
    chunks.append(file_path.read_bytes())
    chunks.append(b"\r\n")
    chunks.append(f"--{boundary}--\r\n".encode())
    return b"".join(chunks), f"multipart/form-data; boundary={boundary}"


def run_generator(generator: Path, output: Path, device_id: str, record_id: str) -> dict[str, str]:
    completed = subprocess.run(
        [str(generator), str(output), device_id, record_id],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return parse_kv(completed.stdout)


def upload(server: str, metadata: dict[str, str], file_path: Path) -> dict[str, object]:
    url = f"{server.rstrip('/')}/api/speaker/records/upload-temp"
    fields = {
        "deviceId": metadata["deviceId"],
        "recordId": metadata["recordId"],
        "name": metadata["name"],
        "fileSize": metadata["fileSize"],
        "crc32": metadata["crc32"],
        "durationMs": metadata["durationMs"],
        "codec": metadata["codec"],
        "sampleRate": metadata["sampleRate"],
        "channels": metadata["channels"],
        "packetMs": metadata["packetMs"],
        "frameBytes": metadata["frameBytes"],
        "samplesPerFrame": metadata["samplesPerFrame"],
    }
    body, content_type = multipart_body(fields, file_path)
    request = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": content_type, "Content-Length": str(len(body))},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def verify_download(upload_response: dict[str, object], file_path: Path) -> None:
    download_url = upload_response.get("downloadUrl")
    if not isinstance(download_url, str) or not download_url:
        raise RuntimeError("upload response did not include downloadUrl")
    with urllib.request.urlopen(download_url, timeout=30) as response:
        downloaded = response.read()
    original = file_path.read_bytes()
    if downloaded != original:
        raise RuntimeError(f"download mismatch: original={len(original)} downloaded={len(downloaded)}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--server", default=DEFAULT_SERVER)
    parser.add_argument("--generator", type=Path, default=Path("build/tji_speaker_core_sample"))
    parser.add_argument("--device-id", default="T12345678")
    parser.add_argument("--record-id", default="REC_CPP_CORE_SMOKE")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    output = args.output
    with tempfile.TemporaryDirectory(prefix="tji-speaker-core-") as tmp:
        if output is None:
            output = Path(tmp) / f"{args.record_id}.hadp"
        metadata = run_generator(args.generator, output, args.device_id, args.record_id)
        file_path = Path(metadata["file"])
        try:
            response = upload(args.server, metadata, file_path)
            verify_download(response, file_path)
            print(json.dumps(response, ensure_ascii=False, separators=(",", ":")))
            print("downloadVerified=true")
        except urllib.error.HTTPError as exc:
            sys.stderr.write(exc.read().decode("utf-8", errors="replace") + "\n")
            return 1
        except RuntimeError as exc:
            sys.stderr.write(f"{exc}\n")
            return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
