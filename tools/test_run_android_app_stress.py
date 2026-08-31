import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("run_android_app_stress.py")
SPEC = importlib.util.spec_from_file_location("run_android_app_stress", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class AndroidAppStressParserTest(unittest.TestCase):
    def test_detects_app_fatal_exception(self):
        log = """E AndroidRuntime: FATAL EXCEPTION: main
E AndroidRuntime: Process: com.tji.device, PID: 42
E AndroidRuntime: java.lang.IllegalStateException: fixture
"""
        self.assertEqual(len(MODULE.crash_evidence(log)), 1)

    def test_ignores_another_apps_fatal_exception(self):
        log = """E AndroidRuntime: FATAL EXCEPTION: main
E AndroidRuntime: Process: example.other, PID: 42
"""
        self.assertEqual(MODULE.crash_evidence(log), [])

    def test_detects_app_anr_and_native_crash(self):
        log = """E ActivityManager: ANR in com.tji.device
F DEBUG: >>> com.tji.device <<<
"""
        self.assertEqual(len(MODULE.crash_evidence(log)), 2)

    def test_percentile_uses_ordered_nearest_rank(self):
        self.assertEqual(MODULE.percentile([100, 10, 50, 30, 20], 0.95), 100)


if __name__ == "__main__":
    unittest.main()
