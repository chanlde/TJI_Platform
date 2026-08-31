#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

./gradlew \
  :app:testMapReleaseUnitTest \
  :app:testNoMapReleaseUnitTest \
  :NetWork:testReleaseUnitTest \
  :app:lintMapRelease \
  :app:lintNoMapRelease \
  :NetWork:lintRelease \
  :app:assembleMapRelease \
  :app:assembleNoMapRelease \
  -PTJI_REQUIRE_RELEASE_SIGNING=true

tools/create_release_manifest.py
