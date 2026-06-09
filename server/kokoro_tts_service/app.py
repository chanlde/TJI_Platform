import os
import struct
import threading
import time
import uuid
import zlib
from collections import OrderedDict
from pathlib import Path
from typing import Literal, Optional

import numpy as np
import sherpa_onnx
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse, Response
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field


VOICE_PRESETS = [
    {"voice": "zf_xiaobei", "speakerId": 45, "label": "小北", "gender": "女声"},
    {"voice": "zf_xiaoni", "speakerId": 46, "label": "小妮", "gender": "女声"},
    {"voice": "zf_xiaoxiao", "speakerId": 47, "label": "小小", "gender": "女声"},
    {"voice": "zf_xiaoyi", "speakerId": 48, "label": "小艺", "gender": "女声"},
    {"voice": "zm_yunjian", "speakerId": 49, "label": "云健", "gender": "男声"},
    {"voice": "zm_yunxi", "speakerId": 50, "label": "云希", "gender": "男声"},
    {"voice": "zm_yunxia", "speakerId": 51, "label": "云夏", "gender": "男声"},
    {"voice": "zm_yunyang", "speakerId": 52, "label": "云扬", "gender": "男声"},
]
VOICE_TO_SID = {preset["voice"]: preset["speakerId"] for preset in VOICE_PRESETS}

DEFAULT_MODEL_DIR = "/opt/tji/kokoro-tts/kokoro-multi-lang-v1_0"
DEFAULT_TARGET_SAMPLE_RATE = 8000
STATIC_DIR = Path(__file__).resolve().parent / "static"
DEFAULT_CACHE_MAX_ITEMS = 64
DEFAULT_RECORD_TMP_DIR = "/tmp/tji-speaker-records"
DEFAULT_RECORD_TTL_SECONDS = 30 * 60
DEFAULT_PUBLIC_BASE_URL = "http://146.56.250.203:8008"
MAX_RECORD_UPLOAD_BYTES = 8 * 1024 * 1024


class KokoroTtsRequest(BaseModel):
    text: str = Field(min_length=1, max_length=500)
    voice: str = "zm_yunxi"
    speakerId: Optional[int] = None
    speed: float = Field(default=1.0, ge=0.75, le=1.25)
    sampleRate: int = Field(default=DEFAULT_TARGET_SAMPLE_RATE, ge=8000, le=24000)
    format: Literal["pcm16", "wav"] = "pcm16"


app = FastAPI(title="TJI Kokoro TTS Service", version="1.0.0")
app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")
_tts_lock = threading.Lock()
_cache_lock = threading.Lock()
_response_cache = OrderedDict()
_tts = None


@app.get("/")
def index():
    return FileResponse(STATIC_DIR / "index.html")


@app.get("/health")
def health():
    return {
        "ok": True,
        "modelDir": str(_model_dir()),
        "voices": VOICE_PRESETS,
        "cacheItems": len(_response_cache),
        "cacheMaxItems": _cache_max_items(),
    }


@app.get("/api/tts/voices")
def voices():
    return VOICE_PRESETS


@app.post("/api/tts/kokoro")
def synthesize_kokoro(request: KokoroTtsRequest):
    text = request.text.strip()
    if not text:
        raise HTTPException(status_code=400, detail="text is empty")

    sid = request.speakerId if request.speakerId is not None else VOICE_TO_SID.get(request.voice)
    if sid not in VOICE_TO_SID.values():
        raise HTTPException(status_code=400, detail=f"unsupported voice/speakerId: {request.voice}/{sid}")

    cache_key = _cache_key(text, int(sid), float(request.speed), int(request.sampleRate), request.format)
    cached = _get_cached_response(cache_key)
    if cached is not None:
        content, media_type = cached
        return _audio_response(content, media_type, request, sid, int(request.sampleRate), cache_hit=True)

    generation_config = sherpa_onnx.GenerationConfig()
    generation_config.sid = int(sid)
    generation_config.speed = float(request.speed)
    generation_config.silence_scale = 0.2

    with _tts_lock:
        audio = _get_tts().generate(text, generation_config)

    samples = np.asarray(audio.samples, dtype=np.float32)
    if samples.size == 0:
        raise HTTPException(status_code=500, detail="generated empty audio")

    target_rate = int(request.sampleRate)
    if audio.sample_rate != target_rate:
        samples = _resample_linear(samples, int(audio.sample_rate), target_rate)

    pcm = _float_to_pcm16le(samples)
    content = pcm
    media_type = f"audio/L16; rate={target_rate}; channels=1"
    if request.format == "wav":
        content = _pcm16le_to_wav(pcm, target_rate)
        media_type = "audio/wav"

    _put_cached_response(cache_key, content, media_type)
    return _audio_response(content, media_type, request, sid, target_rate, cache_hit=False)


@app.post("/api/speaker/records/upload-temp")
async def upload_temp_record(
    file: UploadFile = File(...),
    deviceId: str = Form(...),
    recordId: str = Form(...),
    name: str = Form(""),
    fileSize: int = Form(...),
    crc32: str = Form(...),
    durationMs: int = Form(...),
    codec: str = Form("ima_adpcm"),
    sampleRate: int = Form(DEFAULT_TARGET_SAMPLE_RATE),
    channels: int = Form(1),
    packetMs: int = Form(40),
):
    _cleanup_expired_records()
    clean_device_id = _safe_id(deviceId, "DEVICE")
    clean_record_id = _safe_id(recordId, "REC")
    if codec != "ima_adpcm" or sampleRate != 8000 or channels != 1 or packetMs != 40:
        raise HTTPException(status_code=400, detail="unsupported audio metadata")
    if fileSize <= 0 or fileSize > MAX_RECORD_UPLOAD_BYTES:
        raise HTTPException(status_code=400, detail="invalid fileSize")

    body = await file.read(MAX_RECORD_UPLOAD_BYTES + 1)
    if len(body) > MAX_RECORD_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="record file too large")
    if len(body) != fileSize:
        raise HTTPException(status_code=400, detail="fileSize mismatch")

    actual_crc = _format_crc32(zlib.crc32(body) & 0xFFFFFFFF)
    if _normalize_crc32(crc32) != actual_crc:
        raise HTTPException(status_code=400, detail=f"crc32 mismatch: expected {crc32}, actual {actual_crc}")

    token = uuid.uuid4().hex
    filename = f"{clean_record_id}.hadp"
    record_dir = _record_tmp_dir() / token
    record_dir.mkdir(parents=True, exist_ok=True)
    path = record_dir / filename
    path.write_bytes(body)

    expires_at = int(time.time() + _record_ttl_seconds())
    download_url = f"{_public_base_url()}/api/speaker/records/temp/{token}/{filename}"
    return {
        "ok": True,
        "deviceId": clean_device_id,
        "recordId": clean_record_id,
        "name": name,
        "downloadUrl": download_url,
        "fileSize": fileSize,
        "crc32": actual_crc,
        "durationMs": durationMs,
        "codec": codec,
        "sampleRate": sampleRate,
        "channels": channels,
        "packetMs": packetMs,
        "expiresAt": expires_at,
    }


@app.get("/api/speaker/records/temp/{token}/{filename}")
def download_temp_record(token: str, filename: str):
    clean_token = _safe_id(token, "")
    clean_filename = _safe_filename(filename)
    if not clean_token or not clean_filename.endswith(".hadp"):
        raise HTTPException(status_code=404, detail="record not found")

    path = _record_tmp_dir() / clean_token / clean_filename
    if not path.is_file():
        raise HTTPException(status_code=404, detail="record not found")
    if time.time() - path.stat().st_mtime > _record_ttl_seconds():
        raise HTTPException(status_code=410, detail="record download expired")

    return FileResponse(
        path,
        media_type="application/octet-stream",
        filename=clean_filename,
        headers={"Cache-Control": "no-store"},
    )


def _audio_response(
    content: bytes,
    media_type: str,
    request: KokoroTtsRequest,
    sid: int,
    target_rate: int,
    cache_hit: bool
) -> Response:
    return Response(
        content=content,
        media_type=media_type,
        headers={
            "X-TJI-Sample-Rate": str(target_rate),
            "X-TJI-Format": request.format,
            "X-TJI-Voice": request.voice,
            "X-TJI-Speaker-Id": str(sid),
            "X-TJI-Cache": "HIT" if cache_hit else "MISS",
        },
    )


def _cache_key(text: str, sid: int, speed: float, sample_rate: int, audio_format: str):
    return (text, sid, round(speed, 3), sample_rate, audio_format)


def _cache_max_items() -> int:
    return max(0, int(os.environ.get("KOKORO_CACHE_MAX_ITEMS", str(DEFAULT_CACHE_MAX_ITEMS))))


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


def _get_cached_response(cache_key):
    if _cache_max_items() <= 0:
        return None
    with _cache_lock:
        cached = _response_cache.get(cache_key)
        if cached is not None:
            _response_cache.move_to_end(cache_key)
        return cached


def _put_cached_response(cache_key, content: bytes, media_type: str):
    max_items = _cache_max_items()
    if max_items <= 0:
        return
    with _cache_lock:
        _response_cache[cache_key] = (content, media_type)
        _response_cache.move_to_end(cache_key)
        while len(_response_cache) > max_items:
            _response_cache.popitem(last=False)


def _get_tts():
    global _tts
    if _tts is None:
        _tts = sherpa_onnx.OfflineTts(_build_config())
    return _tts


def _model_dir() -> Path:
    return Path(os.environ.get("KOKORO_MODEL_DIR", DEFAULT_MODEL_DIR)).expanduser().resolve()


def _build_config():
    model_dir = _model_dir()
    required = [
        "model.onnx",
        "voices.bin",
        "tokens.txt",
        "lexicon-us-en.txt",
        "lexicon-zh.txt",
        "date-zh.fst",
        "phone-zh.fst",
        "number-zh.fst",
        "espeak-ng-data",
    ]
    missing = [name for name in required if not (model_dir / name).exists()]
    if missing:
        raise RuntimeError(f"Kokoro model dir is incomplete: {model_dir}, missing={missing}")

    config = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            kokoro=sherpa_onnx.OfflineTtsKokoroModelConfig(
                model=str(model_dir / "model.onnx"),
                voices=str(model_dir / "voices.bin"),
                tokens=str(model_dir / "tokens.txt"),
                data_dir=str(model_dir / "espeak-ng-data"),
                lexicon=f"{model_dir / 'lexicon-us-en.txt'},{model_dir / 'lexicon-zh.txt'}",
            ),
            provider="cpu",
            debug=False,
            num_threads=int(os.environ.get("KOKORO_NUM_THREADS", "2")),
        ),
        rule_fsts=f"{model_dir / 'date-zh.fst'},{model_dir / 'phone-zh.fst'},{model_dir / 'number-zh.fst'}",
        max_num_sentences=1,
    )
    if not config.validate():
        raise RuntimeError("invalid sherpa-onnx Kokoro config")
    return config


def _resample_linear(samples: np.ndarray, source_rate: int, target_rate: int) -> np.ndarray:
    if source_rate == target_rate:
        return samples
    source_count = samples.shape[0]
    target_count = max(1, int(round(source_count * target_rate / source_rate)))
    source_positions = np.linspace(0, source_count - 1, num=target_count, dtype=np.float32)
    return np.interp(source_positions, np.arange(source_count), samples).astype(np.float32)


def _float_to_pcm16le(samples: np.ndarray) -> bytes:
    clipped = np.clip(samples, -1.0, 1.0)
    pcm = np.where(clipped < 0, clipped * 32768.0, clipped * 32767.0)
    return pcm.astype("<i2").tobytes()


def _pcm16le_to_wav(pcm: bytes, sample_rate: int) -> bytes:
    channels = 1
    bits_per_sample = 16
    byte_rate = sample_rate * channels * bits_per_sample // 8
    block_align = channels * bits_per_sample // 8
    data_size = len(pcm)
    riff_size = 36 + data_size
    header = struct.pack(
        "<4sI4s4sIHHIIHH4sI",
        b"RIFF",
        riff_size,
        b"WAVE",
        b"fmt ",
        16,
        1,
        channels,
        sample_rate,
        byte_rate,
        block_align,
        bits_per_sample,
        b"data",
        data_size,
    )
    return header + pcm
