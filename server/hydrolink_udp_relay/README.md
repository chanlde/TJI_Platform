# HydroLink UDP Relay

This is the local source of truth for the Speaker UDP 4G relay deployed on
`146.56.250.203`.

The relay routes App UDP audio packets to the latest 4G endpoint reported by the
MCU heartbeat. It also routes MCU `FEEDBACK` packets back to an explicitly
registered App UDP listener:

```text
HLDEV1 hydrolink TEWNHZDBK
```

The App registers and refreshes its listener mapping from the same UDP socket
that receives audio. The production App refreshes every 10 seconds; mappings
expire after 30 seconds:

```text
HLAPP1 hydrolink TEWNHZDBK FEEDBACK_SESSION LISTEN_TALK
```

It unregisters before closing:

```text
HLAPP0 hydrolink TEWNHZDBK FEEDBACK_SESSION LISTEN_TALK
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

```bash
server/hydrolink_udp_relay/deploy_server.sh
```
