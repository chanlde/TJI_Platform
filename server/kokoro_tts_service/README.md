# Speaker Record Transfer Service

喊话器临时音频文件传输服务。App 上传完整 `.hadp` 文件，服务生成短期下载链接，MCU 通过链接下载后播放或保存。

这个服务不再负责 TTS 合成。文字转语音由 App 本地生成音频，再走同一套 `.hadp` 上传下载链路。

## 安装

```bash
cd /opt/tji/kokoro-tts
python3 -m venv venv
./venv/bin/pip install -r /path/to/server/kokoro_tts_service/requirements.txt
```

## 本地调试

```bash
server/kokoro_tts_service/run_local.sh
```

默认监听：

```text
http://127.0.0.1:8008
```

健康检查：

```bash
curl http://127.0.0.1:8008/health
```

## 上传服务器

```bash
server/kokoro_tts_service/deploy_server.sh
```

默认服务器配置：

```text
host: 146.56.250.203
user: root
ssh key: ~/.ssh/tji_kokoro_deploy
remote dir: /opt/tji/kokoro-tts/server
service: tji-kokoro-tts.service
```

## 接口

### 上传临时 HADP

```text
POST /api/speaker/records/upload-temp
```

Multipart 字段：

```text
file              .hadp 文件
deviceId          设备 SN
recordId          录音 ID
name              显示名称
fileSize          文件字节数
crc32             文件 CRC32
durationMs        音频时长
codec             pcm16 或 ima_adpcm
sampleRate        8000 / 16000 / 24000
channels          1
packetMs          40
frameBytes        每帧字节数
samplesPerFrame   每帧采样数
```

返回 `downloadUrl`，供 MCU 下载。

### 下载临时 HADP

```text
GET /api/speaker/records/temp/{token}/{filename}
```

临时文件默认保留 30 分钟，不写数据库。
