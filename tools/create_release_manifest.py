#!/usr/bin/env python3
"""Create a traceable manifest for the two TJI Platform release APKs."""

from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
APP_OUTPUTS = ROOT / "app/build/outputs"
VARIANTS = ("mapRelease", "noMapRelease")
CERT_SHA256_RE = re.compile(r"certificate SHA-256 digest: ([0-9a-f]+)", re.IGNORECASE)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def sdk_dir() -> Path:
    configured = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if configured:
        return Path(configured).expanduser()
    local_properties = ROOT / "local.properties"
    if local_properties.is_file():
        for line in local_properties.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                return Path(line.split("=", 1)[1]).expanduser()
    raise RuntimeError("Android SDK location is not configured")


def version_key(path: Path) -> tuple[int, ...]:
    parts = re.findall(r"\d+", path.parent.name)
    return tuple(int(part) for part in parts)


def apksigner() -> Path:
    candidates = list((sdk_dir() / "build-tools").glob("*/apksigner"))
    if not candidates:
        raise RuntimeError("apksigner was not found under Android SDK build-tools")
    return max(candidates, key=version_key)


def command_output(*command: str) -> str:
    return subprocess.run(
        command,
        cwd=ROOT,
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    ).stdout


def single_file(directory: Path, pattern: str) -> Path:
    matches = sorted(directory.glob(pattern))
    if len(matches) != 1:
        raise RuntimeError(f"Expected exactly one {pattern} under {directory}, found {len(matches)}")
    return matches[0]


def artifact_for(variant: str, signer: Path) -> dict[str, object]:
    flavor = "map" if variant == "mapRelease" else "noMap"
    apk_dir = APP_OUTPUTS / "apk" / flavor / "release"
    metadata_path = apk_dir / "output-metadata.json"
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    element = metadata["elements"][0]
    apk = apk_dir / element["outputFile"]
    if not apk.is_file():
        raise RuntimeError(f"Release APK is missing: {apk}")

    verification = command_output(str(signer), "verify", "--verbose", "--print-certs", str(apk))
    certificate_match = CERT_SHA256_RE.search(verification)
    if certificate_match is None:
        raise RuntimeError(f"Could not read signer certificate from {apk}")
    if "Verified using v2 scheme (APK Signature Scheme v2): true" not in verification:
        raise RuntimeError(f"APK v2 signature verification failed: {apk}")

    mapping = APP_OUTPUTS / "mapping" / variant / "mapping.txt"
    native_symbols = APP_OUTPUTS / "native-debug-symbols" / variant / "native-debug-symbols.zip"
    for evidence in (mapping, native_symbols):
        if not evidence.is_file():
            raise RuntimeError(f"Release evidence is missing: {evidence}")

    return {
        "variant": metadata["variantName"],
        "applicationId": metadata["applicationId"],
        "versionCode": element["versionCode"],
        "versionName": element["versionName"],
        "apk": str(apk.relative_to(ROOT)),
        "apkSha256": sha256(apk),
        "signerCertificateSha256": certificate_match.group(1).lower(),
        "mapping": str(mapping.relative_to(ROOT)),
        "mappingSha256": sha256(mapping),
        "nativeSymbols": str(native_symbols.relative_to(ROOT)),
        "nativeSymbolsSha256": sha256(native_symbols),
    }


def main() -> int:
    signer = apksigner()
    artifacts = [artifact_for(variant, signer) for variant in VARIANTS]
    signer_digests = {artifact["signerCertificateSha256"] for artifact in artifacts}
    if len(signer_digests) != 1:
        raise RuntimeError("Release variants were signed by different certificates")

    manifest = {
        "schemaVersion": 1,
        "generatedAtUtc": datetime.now(timezone.utc).isoformat(),
        "sourceCommit": command_output("git", "rev-parse", "HEAD").strip(),
        "sourceDirty": bool(command_output("git", "status", "--porcelain").strip()),
        "artifacts": artifacts,
    }
    output = APP_OUTPUTS / "release-manifest.json"
    output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
