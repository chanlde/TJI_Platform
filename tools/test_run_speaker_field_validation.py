#!/usr/bin/env python3
"""Unit tests for run_speaker_field_validation.py."""

from __future__ import annotations

import unittest

import run_speaker_field_validation


class RunSpeakerFieldValidationTest(unittest.TestCase):
    def test_activity_component_handles_relative_activity_name(self) -> None:
        self.assertEqual(
            run_speaker_field_validation.activity_component("com.tji.device", ".ui.main.MainActivity"),
            "com.tji.device/.ui.main.MainActivity",
        )

    def test_activity_component_accepts_fully_qualified_component(self) -> None:
        self.assertEqual(
            run_speaker_field_validation.activity_component("com.tji.device", "com.tji.device/.ui.main.MainActivity"),
            "com.tji.device/.ui.main.MainActivity",
        )

    def test_parse_monitor_summary_reads_key_value_output(self) -> None:
        summary = run_speaker_field_validation.parse_monitor_summary(
            "\n".join(
                [
                    "listenPort=47000",
                    "totalPackets=25",
                    "unknownPackets=0",
                    "firstSequence=0",
                    "lastSequence=24",
                    "avgGapMs=40.125",
                ]
            )
        )

        self.assertEqual(summary.total_packets, 25)
        self.assertEqual(summary.unknown_packets, 0)
        self.assertEqual(summary.first_sequence, 0)
        self.assertEqual(summary.last_sequence, 24)
        self.assertTrue(run_speaker_field_validation.monitor_ok(summary, expect_packets=25))

    def test_monitor_ok_rejects_unknown_packets(self) -> None:
        summary = run_speaker_field_validation.parse_monitor_summary(
            "totalPackets=25\nunknownPackets=1\nfirstSequence=0\nlastSequence=24\n"
        )

        self.assertFalse(run_speaker_field_validation.monitor_ok(summary, expect_packets=25))

    def test_monitor_ok_rejects_missing_expected_packets(self) -> None:
        summary = run_speaker_field_validation.parse_monitor_summary(
            "totalPackets=12\nunknownPackets=0\nfirstSequence=0\nlastSequence=11\n"
        )

        self.assertFalse(run_speaker_field_validation.monitor_ok(summary, expect_packets=25))

    def test_monitor_summary_lines_include_expected_fields(self) -> None:
        summary = run_speaker_field_validation.parse_monitor_summary(
            "totalPackets=25\nunknownPackets=0\nfirstSequence=0\nlastSequence=24\nv2Packets=25\navgGapMs=40.125\n"
        )

        lines = run_speaker_field_validation.monitor_summary_lines(summary, expect_packets=25)

        self.assertIn("udpMonitorPackets=25", lines)
        self.assertIn("udpMonitorExpectedPackets=25", lines)
        self.assertIn("udpMonitorV2Packets=25", lines)
        self.assertIn("udpMonitorAvgGapMs=40.125", lines)


if __name__ == "__main__":
    unittest.main()
