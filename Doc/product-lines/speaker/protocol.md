# 喊话器协议说明

## 状态

- 状态：active
- 产品代码：`speaker`

## MQTT Topic

当前 topic 以 `SpeakerMqttTopics.kt` 为准：

```text
Speaker/devices/{deviceId}/control
Speaker/devices/{deviceId}/status
Speaker/devices/{deviceId}/lifecycle
```

## 命令类型

App 当前覆盖：

- 状态查询。
- 文字喊话准备和播放。
- 文件播放。
- 音量设置。
- 音质设置。
- 舵机角度设置。
- 录音保存。
- 录音列表查询。
- 录音播放、删除、改名。
- 存储状态查询。
- MCU 板载麦克风监听开关。

### 松手喊话统一媒体下行

App 松手后将语音编码为 48 kHz、单声道、20 ms、32 kbit/s Ogg Opus，
与 TTS、录音保存共用可靠 UDP 媒体协议。App 从同一个 UDP socket 注册
`deviceId/sessionId/recordId` 后，以四分块滑动窗口发送；MCU 返回累计
`expectedChunk`，重复包只确认、不重复解码，缺口返回期望分块号。

临时播放先采用最小的 5 帧输出预填（100 ms）起播；D3 源缓冲水位可配置，
默认不增加额外等待。真机若仍出现欠载，再依据 ACK RTT 和缓冲低水位按
100 ms 步进增加，而不是直接使用允许的 1 秒上限。网络收包、Opus 解码和
硬件播放分别由独立队列/任务推进。

App、relay 和 MCU 共同使用 `DPT1-GOLDEN-1` 字节门禁：

```text
5aa502024a001500000000000000000080bb011425008007091213005435544e42464d34515054545f5435544e42464d34515f5445535454414c4b5f5435544e42464d34515f544553544450543101025000000001000200000028000000007d00008b3ed597030011223302004455
```

relay 只转发由该路由当前注册 endpoint 发出的正式 UDP v2 下行包；共享
token 由 App 构建配置和 relay 服务环境注入，源码不提供生产默认值。

### MCU 板载麦克风监听

该链路不生成下载链接，也不属于手机实时喊话。App 先用接收 UDP socket
向 relay 注册三段路由 ID，再通过 MQTT 下发同一个命令控制开启和关闭：

```json
{
  "cmd": 116,
  "cmdName": "SET_PLAYBACK_FEEDBACK",
  "enabled": 1,
  "sessionId": "FB_T123_ABC",
  "talkId": "MON_T123_ABC",
  "codec": "opus",
  "sampleRate": 16000,
  "channels": 1,
  "packetMs": 20,
  "ttlMs": 30000
}
```

- 开启和关闭都使用 `cmd=116`，只改变 `enabled=1/0`。
- `cmd=116` 的成功 ACK 表示 MCU 已经完成 PDM 回传启停，不是仅进入命令队列。
- App 每 10 秒刷新 relay 注册并重发相同会话的开启命令。
- MCU 租约为 30 秒；App 异常退出或断网后 MCU 会自动停止采集。
- UDP v2 包必须带 `FEEDBACK (0x0008)` 标志，固定 16 kHz、单声道、
  20 ms、每包 320 个解码采样。
- 负载是 raw Opus packet（`codec=2`，非 Ogg 文件）；目标 16 kbit/s CBR，
  通常约 40 字节。App 保持一个 Opus 解码器状态，并对缺失序号执行 PLC。
- App 校验 `deviceId/sessionId/talkId`，不接收其他设备或旧会话的数据。
- 麦克风监听开启时按住喊话，App 立即暂停并清空本地回传播放，同时下发
  `enabled=0`；松手后用原会话下发 `enabled=1`，等待 MCU ACK 和新的
  160 ms 抖动预缓冲，再发送喊话音频。这样手机录音不会录入自身回传，
  MCU 上行带宽也在录音期间释放。

### 舵机角度设置

App 通过控制 topic 下发舵机角度：

```json
{
  "v": 1,
  "deviceId": "T12345678",
  "cmdId": "speaker-servo-angle-1",
  "msgId": "speaker-servo-angle-1",
  "ts": 123456789,
  "cmd": 107,
  "cmdName": "SET_SERVO_ANGLE",
  "angle": 90,
  "speedDps": 60,
  "params": {
    "angle": 90,
    "speedDps": 60
  }
}
```

- `angle` 单位为度，App 侧限制为 `0..180`。
- `speedDps` 为舵机转动速度，单位 `度/秒`，App 侧限制为 `1..360`，默认 `60`。
- 为兼容不同固件解析方式，App 会同时在顶层和 `params` 内写入舵机参数。
- 状态上报可继续使用旧字段 `servoAngle`，也可以上报新的 `servo` 对象。

### 舵机状态上报

新固件建议在状态里增加 `servo` 对象，App 会解析并展示当前角度、目标角度、速度和移动状态：

```json
{
  "servoAngle": 90,
  "servo": {
    "currentAngle": 90,
    "targetAngle": 120,
    "speedDps": 60,
    "moving": true
  }
}
```

- `servoAngle` 仍作为兼容字段保留。
- App 不假设下发后舵机会立即到位，会优先显示固件上报的 `servo.currentAngle` 和 `servo.targetAngle`。

## Ogg/Opus 文件

- App 生成或录制音频后封装为标准 `.opus`（Ogg 容器）。
- 临时上传服务返回下载 URL。
- MCU 通过 URL 下载后播放或保存。
- 当前受控参数和端到端职责见 [ogg-opus-profile.md](ogg-opus-profile.md)。

## ACK 和事件

- ACK 解析由 `SpeakerMqttInbound` 负责。
- 录音列表、存储状态、录音事件都进入 `SpeakerRepository`。
- 客户界面只展示可理解状态，不展示底层包数或原始 payload。
