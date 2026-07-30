import os
import time
import uuid
import zlib
from pathlib import Path

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse


DEFAULT_RECORD_TMP_DIR = "/tmp/tji-speaker-records"
DEFAULT_RECORD_TTL_SECONDS = 30 * 60
DEFAULT_PUBLIC_BASE_URL = "http://146.56.250.203:8008"
MAX_RECORD_UPLOAD_BYTES = 8 * 1024 * 1024


app = FastAPI(title="TJI Speaker Record Transfer Service", version="1.2.0")


@app.get("/")
def index():
    return health()


@app.get("/health")
def health():
    return {
        "ok": True,
        "service": "speaker-record-transfer",
        "recordTmpDir": str(_record_tmp_dir()),
        "recordTtlSeconds": _record_ttl_seconds(),
        "maxRecordUploadBytes": MAX_RECORD_UPLOAD_BYTES,
    }


@app.post("/api/speaker/audio/upload-temp")
async def upload_temp_record(
    file: UploadFile = File(...),
    deviceId: str = Form(...),
    recordId: str = Form(...),
    name: str = Form(""),
    fileSize: int = Form(...),
    crc32: str = Form(...),
    durationMs: int = Form(...),
    container: str = Form("ogg"),
    codec: str = Form("opus"),
    sampleRate: int = Form(24000),
    channels: int = Form(1),
    packetMs: int = Form(20),
    bitrate: int = Form(24000),
):
    _cleanup_expired_records()
    clean_device_id = _safe_id(deviceId, "DEVICE")
    clean_record_id = _safe_id(recordId, "REC")
    if (
        container != "ogg"
        or codec != "opus"
        or sampleRate not in (8000, 12000, 16000, 24000, 48000)
        or channels != 1
        or packetMs != 20
        or bitrate < 6000
        or bitrate > 128000
    ):
        raise HTTPException(status_code=400, detail="unsupported audio metadata")
    if fileSize <= 0 or fileSize > MAX_RECORD_UPLOAD_BYTES:
        raise HTTPException(status_code=400, detail="invalid fileSize")

    body = await file.read(MAX_RECORD_UPLOAD_BYTES + 1)
    if len(body) > MAX_RECORD_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="record file too large")
    if len(body) != fileSize:
        raise HTTPException(status_code=400, detail="fileSize mismatch")
    _validate_ogg_opus(body, sampleRate, channels)

    actual_crc = _format_crc32(zlib.crc32(body) & 0xFFFFFFFF)
    if _normalize_crc32(crc32) != actual_crc:
        raise HTTPException(status_code=400, detail=f"crc32 mismatch: expected {crc32}, actual {actual_crc}")

    token = uuid.uuid4().hex
    filename = f"{clean_record_id}.opus"
    record_dir = _record_tmp_dir() / token
    record_dir.mkdir(parents=True, exist_ok=True)
    path = record_dir / filename
    path.write_bytes(body)

    expires_at = int(time.time() + _record_ttl_seconds())
    download_url = f"{_public_base_url()}/api/speaker/audio/temp/{token}/{filename}"
    return {
        "ok": True,
        "deviceId": clean_device_id,
        "recordId": clean_record_id,
        "name": name,
        "downloadUrl": download_url,
        "fileSize": fileSize,
        "crc32": actual_crc,
        "durationMs": durationMs,
        "container": container,
        "codec": codec,
        "sampleRate": sampleRate,
        "channels": channels,
        "packetMs": packetMs,
        "bitrate": bitrate,
        "expiresAt": expires_at,
    }


def _validate_ogg_opus(body: bytes, sample_rate: int, channels: int) -> None:
    if len(body) < 47 or body[:4] != b"OggS" or body[4] != 0:
        raise HTTPException(status_code=400, detail="invalid Ogg stream")
    segment_count = body[26]
    payload_offset = 27 + segment_count
    if payload_offset + 19 > len(body) or body[payload_offset:payload_offset + 8] != b"OpusHead":
        raise HTTPException(status_code=400, detail="missing OpusHead")
    if body[payload_offset + 9] != channels:
        raise HTTPException(status_code=400, detail="Opus channel mismatch")
    input_rate = int.from_bytes(body[payload_offset + 12:payload_offset + 16], "little")
    if input_rate not in (0, sample_rate):
        raise HTTPException(status_code=400, detail="Opus sampleRate mismatch")


@app.get("/api/speaker/audio/temp/{token}/{filename}")
def download_temp_record(token: str, filename: str):
    clean_token = _safe_id(token, "")
    clean_filename = _safe_filename(filename)
    if not clean_token or not clean_filename.endswith(".opus"):
        raise HTTPException(status_code=404, detail="record not found")

    path = _record_tmp_dir() / clean_token / clean_filename
    if not path.is_file():
        raise HTTPException(status_code=404, detail="record not found")
    if time.time() - path.stat().st_mtime > _record_ttl_seconds():
        raise HTTPException(status_code=410, detail="record download expired")

    return FileResponse(
        path,
        media_type="audio/ogg",
        filename=clean_filename,
        headers={"Cache-Control": "no-store"},
    )


def _record_tmp_dir() -> Path:
    path = Path(os.environ.get("SPEAKER_RECORD_TMP_DIR", DEFAULT_RECORD_TMP_DIR))
    path.mkdir(parents=True, exist_ok=True)
    return path


def _record_ttl_seconds() -> int:
    return max(60, int(os.environ.get("SPEAKER_RECORD_TTL_SECONDS", str(DEFAULT_RECORD_TTL_SECONDS))))


def _public_base_url() -> str:
    return os.environ.get("SPEAKER_RECORD_PUBLIC_BASE_URL", DEFAULT_PUBLIC_BASE_URL).rstrip("/")


def _cleanup_expired_records() -> None:
    root = _record_tmp_dir()
    cutoff = time.time() - _record_ttl_seconds()
    for child in root.iterdir():
        try:
            if child.is_dir():
                files = list(child.iterdir())
                if files and all(file.stat().st_mtime < cutoff for file in files):
                    for file in files:
                        file.unlink(missing_ok=True)
                    child.rmdir()
            elif child.stat().st_mtime < cutoff:
                child.unlink(missing_ok=True)
        except OSError:
            continue


def _safe_id(value: str, fallback: str) -> str:
    cleaned = "".join(ch for ch in value if ch.isalnum() or ch in "_-")
    return cleaned or fallback


def _safe_filename(value: str) -> str:
    return Path(value).name


def _format_crc32(value: int) -> str:
    return f"0x{value:08X}"


def _normalize_crc32(value: str) -> str:
    text = value.strip()
    try:
        if text.lower().startswith("0x"):
            number = int(text[2:], 16)
        else:
            number = int(text, 16)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=f"invalid crc32: {value}") from exc
    return _format_crc32(number & 0xFFFFFFFF)
