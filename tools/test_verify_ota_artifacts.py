import hashlib
import io
import json
import unittest

from verify_ota_artifacts import OtaArtifactError, verify_product_artifact


class _Response(io.BytesIO):
    def __init__(self, payload: bytes, headers=None):
        super().__init__(payload)
        self.headers = headers or {}

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()


class OtaArtifactVerificationTest(unittest.TestCase):
    def test_valid_metadata_and_download_are_verified(self):
        firmware = b"signed fixture bytes"
        metadata = _metadata(
            size=len(firmware),
            sha256=hashlib.sha256(firmware).hexdigest(),
        )

        result = verify_product_artifact(
            product_id=6,
            api_base_url="https://api.example.test/",
            download_base_url="https://download.example.test/",
            opener=_fake_opener(metadata, firmware),
        )

        self.assertEqual(6, result.product_id)
        self.assertEqual(len(firmware), result.file_size)
        self.assertEqual(hashlib.sha256(firmware).hexdigest(), result.sha256)

    def test_wrong_hash_and_cross_domain_url_are_rejected(self):
        firmware = b"tampered"
        metadata = _metadata(size=len(firmware), sha256="0" * 64)
        with self.assertRaisesRegex(OtaArtifactError, "SHA256"):
            verify_product_artifact(
                product_id=6,
                api_base_url="https://api.example.test/",
                download_base_url="https://download.example.test/",
                opener=_fake_opener(metadata, firmware),
            )

        cross_domain = _metadata(
            size=len(firmware),
            sha256=hashlib.sha256(firmware).hexdigest(),
            path="https://evil.example/fw.bin",
        )
        with self.assertRaisesRegex(OtaArtifactError, "host"):
            verify_product_artifact(
                product_id=6,
                api_base_url="https://api.example.test/",
                download_base_url="https://download.example.test/",
                opener=_fake_opener(cross_domain, firmware),
            )

    def test_missing_metadata_and_oversized_stream_are_rejected(self):
        missing = json.dumps({"code": 200, "data": {"path": None}}).encode()
        with self.assertRaisesRegex(OtaArtifactError, "metadata"):
            verify_product_artifact(
                product_id=3,
                api_base_url="https://api.example.test/",
                download_base_url="https://download.example.test/",
                opener=_fake_opener(missing, b""),
            )

        declared = b"short"
        actual = b"too-long"
        metadata = _metadata(
            size=len(declared),
            sha256=hashlib.sha256(declared).hexdigest(),
        )
        with self.assertRaisesRegex(OtaArtifactError, "declared size"):
            verify_product_artifact(
                product_id=6,
                api_base_url="https://api.example.test/",
                download_base_url="https://download.example.test/",
                opener=_fake_opener(metadata, actual),
            )


def _metadata(size, sha256, path="/download/fw.bin"):
    return json.dumps(
        {
            "code": 200,
            "data": {
                "productName": "fixture",
                "path": path,
                "filesize": size,
                "sha256Hex": sha256,
            },
        }
    ).encode()


def _fake_opener(metadata, firmware):
    calls = {"count": 0}

    def open_url(_request, timeout=0):
        del timeout
        calls["count"] += 1
        if calls["count"] == 1:
            return _Response(metadata)
        return _Response(firmware, {"Content-Length": str(len(firmware))})

    return open_url


if __name__ == "__main__":
    unittest.main()
