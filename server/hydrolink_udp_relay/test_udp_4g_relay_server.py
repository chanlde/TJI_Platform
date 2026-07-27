import random
import socket
import struct
import threading
import unittest

if __package__:
    from .udp_4g_relay_server import (
        AUDIO_STREAM_FLAG_FEEDBACK,
        DeviceState,
        ListenerState,
        listener_key,
        parse_app_listener_command,
        parse_device_hello,
        parse_formal_v2_route,
        parse_target_device_id,
        route_feedback_packet,
        serve,
    )
else:
    from udp_4g_relay_server import (
        AUDIO_STREAM_FLAG_FEEDBACK,
        DeviceState,
        ListenerState,
        listener_key,
        parse_app_listener_command,
        parse_device_hello,
        parse_formal_v2_route,
        parse_target_device_id,
        route_feedback_packet,
        serve,
    )


class UdpRelayServerTest(unittest.TestCase):
    @staticmethod
    def formal_packet(
        *,
        device_id: bytes = b"TEWNHZDBK",
        session_id: bytes = b"FEEDBACK_TEWNHZDBK_1",
        talk_id: bytes = b"LISTEN_TEWNHZDBK_1",
        sample_rate: int = 16000,
        flags: int = AUDIO_STREAM_FLAG_FEEDBACK,
    ) -> bytes:
        payload = b"\x00" * 324
        header_len = 28 + len(device_id) + len(session_id) + len(talk_id)
        header = struct.pack(
            "<HBBHHIIHBBHHBBBB",
            0xA55A,
            2,
            1,
            header_len,
            flags,
            7,
            640,
            sample_rate,
            1,
            40,
            len(payload),
            640 if sample_rate == 16000 else 320,
            len(device_id),
            len(session_id),
            len(talk_id),
            0,
        )
        return header + device_id + session_id + talk_id + payload

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

    def test_parse_feedback_route(self):
        route = parse_formal_v2_route(self.formal_packet())
        self.assertIsNotNone(route)
        self.assertEqual(route.device_id, "TEWNHZDBK")
        self.assertEqual(route.sample_rate, 16000)
        self.assertTrue(route.is_feedback)

    def test_parse_app_listener_registration(self):
        command = parse_app_listener_command(
            b"HLAPP1 hydrolink TEWNHZDBK FEEDBACK_1 LISTEN_1",
            "hydrolink",
        )
        self.assertIsNotNone(command)
        self.assertTrue(command.enabled)
        self.assertEqual(command.device_id, "TEWNHZDBK")
        self.assertIsNone(
            parse_app_listener_command(
                b"HLAPP1 wrong TEWNHZDBK FEEDBACK_1 LISTEN_1",
                "hydrolink",
            )
        )

    def test_device_hello_requires_bounded_device_id(self):
        self.assertEqual(
            parse_device_hello(b"HLDEV1 hydrolink TEWNHZDBK", "hydrolink"),
            "TEWNHZDBK",
        )
        self.assertIsNone(parse_device_hello(b"HLDEV1 hydrolink", "hydrolink"))
        self.assertIsNone(
            parse_device_hello(
                b"HLDEV1 hydrolink " + b"A" * 33,
                "hydrolink",
            )
        )

    def test_rejects_trailing_bytes_in_formal_packet_without_legacy_fallback(self):
        malformed = self.formal_packet() + b"\x00"
        self.assertIsNone(parse_formal_v2_route(malformed))
        self.assertIsNone(parse_target_device_id(malformed))

    def test_parsers_do_not_crash_on_random_datagrams(self):
        rng = random.Random(0xA55A)
        for _ in range(5_000):
            length = rng.randrange(0, 1_601)
            packet = bytes(rng.getrandbits(8) for _ in range(length))
            parse_device_hello(packet, "hydrolink")
            parse_app_listener_command(packet, "hydrolink")
            parse_formal_v2_route(packet)
            parse_target_device_id(packet)

    def test_feedback_routes_only_from_registered_device_to_listener(self):
        class FakeSocket:
            def __init__(self):
                self.sent = []

            def sendto(self, packet, addr):
                self.sent.append((packet, addr))

        now = 100.0
        device_addr = ("10.0.0.2", 5000)
        app_addr = ("10.0.0.3", 40000)
        packet = self.formal_packet()
        route = parse_formal_v2_route(packet)
        devices = {
            "TEWNHZDBK": DeviceState(
                addr=device_addr,
                device_id="TEWNHZDBK",
                last_seen=now,
            )
        }
        key = listener_key("TEWNHZDBK", "FEEDBACK_TEWNHZDBK_1", "LISTEN_TEWNHZDBK_1")
        listeners = {
            key: ListenerState(
                addr=app_addr,
                device_id=key[0],
                session_id=key[1],
                talk_id=key[2],
                last_seen=now,
            )
        }
        sock = FakeSocket()

        self.assertTrue(
            route_feedback_packet(sock, packet, device_addr, route, devices, listeners, now, 30.0)
        )
        self.assertEqual(sock.sent, [(packet, app_addr)])
        self.assertFalse(
            route_feedback_packet(
                sock,
                packet,
                ("10.0.0.99", 5000),
                route,
                devices,
                listeners,
                now,
                30.0,
            )
        )

    def test_real_udp_socket_routes_playback_and_feedback_both_directions(self):
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as reservation:
            reservation.bind(("127.0.0.1", 0))
            relay_port = reservation.getsockname()[1]

        stop_event = threading.Event()
        ready_event = threading.Event()
        server_thread = threading.Thread(
            target=serve,
            kwargs={
                "listen_host": "127.0.0.1",
                "listen_port": relay_port,
                "token": "hydrolink",
                "timeout_s": 2.0,
                "stop_event": stop_event,
                "ready_event": ready_event,
            },
            daemon=True,
        )
        server_thread.start()
        self.assertTrue(ready_event.wait(timeout=2.0))
        relay_addr = ("127.0.0.1", relay_port)

        try:
            with (
                socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as device,
                socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as app,
            ):
                device.bind(("127.0.0.1", 0))
                app.bind(("127.0.0.1", 0))
                device.settimeout(1.0)
                app.settimeout(1.0)

                device.sendto(b"HLDEV1 hydrolink TEWNHZDBK", relay_addr)
                app.sendto(
                    b"HLAPP1 hydrolink TEWNHZDBK FEEDBACK_TEWNHZDBK_1 LISTEN_TEWNHZDBK_1",
                    relay_addr,
                )
                ack, _ = app.recvfrom(4096)
                self.assertEqual(
                    ack,
                    b"HLAPPACK1 REGISTERED TEWNHZDBK "
                    b"FEEDBACK_TEWNHZDBK_1 LISTEN_TEWNHZDBK_1",
                )

                feedback_packet = self.formal_packet()
                device.sendto(feedback_packet, relay_addr)
                forwarded_feedback, _ = app.recvfrom(4096)
                self.assertEqual(forwarded_feedback, feedback_packet)

                playback_packet = self.formal_packet(
                    session_id=b"PLAY_TEWNHZDBK_1",
                    talk_id=b"PTT_TEWNHZDBK_1",
                    sample_rate=8000,
                    flags=0x0004,
                )
                app.sendto(playback_packet, relay_addr)
                forwarded_playback, _ = device.recvfrom(4096)
                self.assertEqual(forwarded_playback, playback_packet)

                app.sendto(
                    b"HLAPP0 hydrolink TEWNHZDBK FEEDBACK_TEWNHZDBK_1 LISTEN_TEWNHZDBK_1",
                    relay_addr,
                )
                ack, _ = app.recvfrom(4096)
                self.assertEqual(
                    ack,
                    b"HLAPPACK1 REMOVED TEWNHZDBK "
                    b"FEEDBACK_TEWNHZDBK_1 LISTEN_TEWNHZDBK_1",
                )
                device.sendto(feedback_packet, relay_addr)
                with self.assertRaises(socket.timeout):
                    app.recvfrom(4096)
        finally:
            stop_event.set()
            server_thread.join(timeout=2.0)
            self.assertFalse(server_thread.is_alive())


if __name__ == "__main__":
    unittest.main()
