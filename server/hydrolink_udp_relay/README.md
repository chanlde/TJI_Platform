# HydroLink UDP Relay

This is the local source of truth for the Speaker UDP 4G relay deployed on
`146.56.250.203`.

The relay routes App UDP audio packets to the latest 4G endpoint reported by the
MCU heartbeat. It also routes MCU `FEEDBACK` packets back to an explicitly
registered App UDP listener:

```text
HLDEV1 <relay-token> TEWNHZDBK
```

The App registers and refreshes its listener mapping from the same UDP socket
that receives audio. The production App refreshes every 10 seconds; mappings
expire after 30 seconds:

```text
HLAPP1 <relay-token> TEWNHZDBK FEEDBACK_SESSION LISTEN_TALK
```

It unregisters before closing:

```text
HLAPP0 <relay-token> TEWNHZDBK FEEDBACK_SESSION LISTEN_TALK
```

Formal Speaker UDP v2 packets must use the Notion 28-byte fixed header:

```text
magic/version/codec/headerLen/flags/seq/timestamp/sampleRate/channels/packetMs/payloadLen/sampleCount/deviceIdLen/sessionIdLen/talkIdLen/reserved/deviceId/sessionId/talkId/payload
```

For record storage packets:

```text
flags.storeToSd = bit1
flags.lastPacket = bit0 on the final packet
sessionId = storeTaskId
talkId = recordId
```

## Local Checks

```bash
python3 -m py_compile server/hydrolink_udp_relay/udp_4g_relay_server.py
python3 -m unittest discover server/hydrolink_udp_relay
```

## Deploy

Configure the same nonblank secret in the App build property/environment
`TJI_SPEAKER_RELAY_TOKEN` and in the relay service. The server contains no
production default token. Do not commit the secret or print it in build logs:

```bash
export TJI_SPEAKER_RELAY_TOKEN='<generated-secret>'
server/hydrolink_udp_relay/deploy_server.sh
```

For the connected Android test device, use the repository helper. It reads the
credential from the already-running relay over SSH, keeps it in process memory,
runs the speaker regression tests, and injects it into the debug build without
printing or storing it:

```bash
tools/install_no_map_debug_from_relay.sh
```
