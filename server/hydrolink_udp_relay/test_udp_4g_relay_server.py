import struct
import unittest

from udp_4g_relay_server import parse_target_device_id


class UdpRelayServerTest(unittest.TestCase):
    def test_parse_formal_v2_record_store_header(self):
        device_id = b"TEWNHZDBK"
        session_id = b"STORE_TEWNHZDBK_1"
        talk_id = b"REC_TEWNHZDBK_1"
        payload = b"\x00" * 164
        header_len = 28 + len(device_id) + len(session_id) + len(talk_id)
        header = struct.pack(
            "<HBBHHIIHBBHHBBBB",
            0xA55A,
            2,
            1,
            header_len,
            0x03,
            0,
            0,
            8000,
            1,
            40,
            len(payload),
            320,
            len(device_id),
            len(session_id),
            len(talk_id),
            0,
        )

        packet = header + device_id + session_id + talk_id + payload

        self.assertEqual(parse_target_device_id(packet), "TEWNHZDBK")

    def test_parse_simplified_v2_header_for_temporary_field_compatibility(self):
        device_id = b"TEWNHZDBK"
        session_id = b"STORE_TEWNHZDBK_1"
        talk_id = b"REC_TEWNHZDBK_1"
        packet = bytearray()
        packet += b"\x5a\xa5"
        packet += bytes([2, 1])
        packet += (0).to_bytes(4, "little")
        packet += (0).to_bytes(4, "little")
        packet += (8000).to_bytes(2, "little")
        packet += bytes([1, 1])
        packet += (164).to_bytes(2, "little")
        packet += (320).to_bytes(2, "little")
        packet += bytes([1, len(device_id), len(session_id), len(talk_id), 0])
        packet += device_id + session_id + talk_id + b"\x00" * 164

        self.assertEqual(parse_target_device_id(bytes(packet)), "TEWNHZDBK")


if __name__ == "__main__":
    unittest.main()
