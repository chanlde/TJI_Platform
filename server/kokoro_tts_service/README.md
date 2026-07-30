# Speaker Record Transfer Service

喊话器临时音频文件传输服务。App 上传标准 Ogg Opus 文件，服务生成短期下载链接，MCU 边下载、边解码、边播放或保存。

这个服务不再负责 TTS 合成。文字转语音由 App 本地生成音频，再走同一套 Ogg Opus 上传下载链路。

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

### 上传临时 Ogg Opus

```text
POST /api/speaker/audio/upload-temp
```

Multipart 字段：

```text
file              .opus 文件（Ogg 容器）
deviceId          设备正式通信身份
recordId          录音 ID
name              显示名称
fileSize          文件字节数
crc32             文件 CRC32
durationMs        音频时长
container         ogg
codec             opus
sampleRate        8000 / 12000 / 16000 / 24000 / 48000
channels          1
packetMs          20
bitrate           6000..128000 bit/s
```

返回 `downloadUrl`，供 MCU 下载。

### 下载临时 Ogg Opus

```text
GET /api/speaker/audio/temp/{token}/{filename}
```

临时文件默认保留 30 分钟，不写数据库。
