#!/usr/bin/env python3
"""Verify OTA metadata and firmware bytes without persisting the artifact."""

from __future__ import annotations

import argparse
import dataclasses
import hashlib
import json
import re
import sys
from typing import Callable, Iterable, Optional
from urllib.parse import urlencode, urljoin, urlparse
from urllib.request import Request, urlopen


MAX_FIRMWARE_BYTES = 64 * 1024 * 1024
READ_CHUNK_BYTES = 64 * 1024
SHA256_PATTERN = re.compile(r"^[0-9a-fA-F]{64}$")


class OtaArtifactError(RuntimeError):
    pass


@dataclasses.dataclass(frozen=True)
class VerifiedOtaArtifact:
    product_id: int
    product_name: str
    download_url: str
    file_size: int
    sha256: str


def verify_product_artifact(
    product_id: int,
    api_base_url: str,
    download_base_url: str,
    opener: Callable = urlopen,
    timeout_seconds: float = 20.0,
) -> VerifiedOtaArtifact:
    metadata_url = urljoin(
        _normalized_base(api_base_url),
        "api/data/appversion/getAppVersion?"
        + urlencode({"productId": product_id, "type": 2}),
    )
    metadata_request = Request(metadata_url, headers={"User-Agent": "TJI-OTA-Verifier/1"})
    with opener(metadata_request, timeout=timeout_seconds) as response:
        payload = json.loads(response.read().decode("utf-8"))

    if payload.get("code") != 200 or not isinstance(payload.get("data"), dict):
        raise OtaArtifactError(
            f"product {product_id}: metadata unavailable: {payload.get('message', 'unknown')}"
        )
    data = payload["data"]
    path = _first_value(data, "download_url", "downloadUrl", "path")
    size = _positive_int(_first_value(data, "file_size", "fileSize", "filesize", "size"))
    sha256 = str(_first_value(data, "sha256", "sha256Hex") or "").strip().lower()
    if not path or size is None or not SHA256_PATTERN.fullmatch(sha256):
        raise OtaArtifactError(f"product {product_id}: incomplete or invalid OTA metadata")
    if size > MAX_FIRMWARE_BYTES:
        raise OtaArtifactError(f"product {product_id}: firmware exceeds 64 MiB limit")

    download_url = _resolve_allowed_download_url(str(path), download_base_url)
    download_request = Request(
        download_url,
        headers={"User-Agent": "TJI-OTA-Verifier/1", "Accept": "application/octet-stream"},
    )
    digest = hashlib.sha256()
    downloaded = 0
    with opener(download_request, timeout=timeout_seconds) as response:
        content_length = _positive_int(response.headers.get("Content-Length"))
        if content_length is not None and content_length != size:
            raise OtaArtifactError(
                f"product {product_id}: HTTP Content-Length differs from declared size"
            )
        while True:
            chunk = response.read(READ_CHUNK_BYTES)
            if not chunk:
                break
            downloaded += len(chunk)
            if downloaded > size:
                raise OtaArtifactError(f"product {product_id}: download exceeds declared size")
            digest.update(chunk)

    if downloaded != size:
        raise OtaArtifactError(f"product {product_id}: downloaded size differs from metadata")
    actual_sha256 = digest.hexdigest()
    if actual_sha256 != sha256:
        raise OtaArtifactError(f"product {product_id}: firmware SHA256 mismatch")
    return VerifiedOtaArtifact(
        product_id=product_id,
        product_name=str(data.get("productName") or ""),
        download_url=download_url,
        file_size=size,
        sha256=actual_sha256,
    )


def _resolve_allowed_download_url(path: str, download_base_url: str) -> str:
    base = urlparse(_normalized_base(download_base_url))
    resolved_url = urljoin(base.geturl(), path.strip())
    resolved = urlparse(resolved_url)
    if resolved.scheme.lower() != "https":
        raise OtaArtifactError("firmware URL must use HTTPS")
    if resolved.hostname != base.hostname:
        raise OtaArtifactError("firmware URL host differs from configured download host")
    if resolved.port not in (None, 443) or resolved.username or resolved.password:
        raise OtaArtifactError("firmware URL contains a disallowed port or user info")
    if resolved.fragment:
        raise OtaArtifactError("firmware URL must not contain a fragment")
    return resolved_url


def _normalized_base(value: str) -> str:
    return value if value.endswith("/") else value + "/"


def _first_value(data: dict, *keys: str):
    for key in keys:
        if key in data and data[key] is not None:
            return data[key]
    return None


def _positive_int(value) -> Optional[int]:
    if isinstance(value, bool) or value is None:
        return None
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        return None
    return parsed if parsed > 0 else None


def _parse_product_ids(raw: str) -> Iterable[int]:
    for part in raw.split(","):
        value = part.strip()
        if value:
            yield int(value)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api-base", default="https://api.tjinnovations.cloud/")
    parser.add_argument("--download-base", default="https://www.tjinnovations.cloud/")
    parser.add_argument("--product-ids", default="2,4,6,7")
    args = parser.parse_args(argv)

    failed = False
    for product_id in _parse_product_ids(args.product_ids):
        try:
            result = verify_product_artifact(
                product_id=product_id,
                api_base_url=args.api_base,
                download_base_url=args.download_base,
            )
            print(
                f"PASS productId={result.product_id} product={result.product_name} "
                f"bytes={result.file_size} sha256={result.sha256}"
            )
        except (OtaArtifactError, OSError, ValueError, json.JSONDecodeError) as error:
            failed = True
            print(f"FAIL productId={product_id} error={error}", file=sys.stderr)
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
