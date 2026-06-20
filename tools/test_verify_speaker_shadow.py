#!/usr/bin/env python3
"""Unit tests for verify_speaker_shadow.py."""

from __future__ import annotations

import tempfile
import unittest
import zipfile
from pathlib import Path

import verify_speaker_shadow


class VerifySpeakerShadowTest(unittest.TestCase):
    def test_missing_native_libs_reports_absent_entries(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            apk = Path(temp_dir) / "test.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("lib/arm64-v8a/libtji_speaker_core_jni.so", b"jni")

            self.assertEqual(
                verify_speaker_shadow.missing_native_libs(apk),
                ["lib/arm64-v8a/libc++_shared.so"],
            )

    def test_parse_shadow_events_extracts_status_and_fields(self) -> None:
        events = verify_speaker_shadow.parse_shadow_events(
            [
                "06-20 10:00:00 speakerCoreShadow status=match label=v2-adpcm-packet path=recorded-v2-udp sequence=1\n",
                "unrelated line\n",
                "06-20 10:00:01 speakerCoreShadow status=mismatch path=record-save kotlinSize=1 nativeSize=2\n",
            ]
        )

        self.assertEqual(len(events), 2)
        self.assertEqual(events[0].status, "match")
        self.assertEqual(events[0].path, "recorded-v2-udp")
        self.assertEqual(events[0].fields["sequence"], "1")
        self.assertEqual(events[1].status, "mismatch")
        self.assertEqual(events[1].fields["kotlinSize"], "1")

    def test_summarize_events_counts_status_and_paths(self) -> None:
        events = verify_speaker_shadow.parse_shadow_events(
            [
                "speakerCoreShadow status=match path=recorded-v2-udp\n",
                "speakerCoreShadow status=match path=recorded-v2-udp\n",
                "speakerCoreShadow status=nativeUnavailable path=live-legacy-udp\n",
            ]
        )

        summary = verify_speaker_shadow.summarize_events(events)

        self.assertIn("shadowEvents=3", summary)
        self.assertIn("statusCounts=match:2,nativeUnavailable:1", summary)
        self.assertIn("pathCounts=live-legacy-udp:1,recorded-v2-udp:2", summary)
        self.assertIn("nonMatchEvents=1", summary)


if __name__ == "__main__":
    unittest.main()
