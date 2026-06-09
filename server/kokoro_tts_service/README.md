# Kokoro TTS Service

离线 Kokoro TTS HTTP 服务，用于喊话器 App 的远程文字转语音。

## 模型

推荐使用 sherpa-onnx 官方 Kokoro 模型：

```bash
mkdir -p /opt/tji/kokoro-tts
cd /opt/tji/kokoro-tts
curl -L -o kokoro-multi-lang-v1_0.tar.bz2 \
  https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2
tar xf kokoro-multi-lang-v1_0.tar.bz2
```

模型目录约 394MB。

## 安装

```bash
cd /opt/tji/kokoro-tts
python3 -m venv venv
./venv/bin/pip install -r /path/to/server/kokoro_tts_service/requirements.txt
```

## 本地调试

本地先调好音色、语速、停顿、接口格式，再上传服务器：

```bash
server/kokoro_tts_service/run_local.sh
```

默认读取本地模型目录：

```text
.tmp_tts_probe/kokoro-multi-lang-v1_0
```

如果模型放在其他地方：

```bash
KOKORO_MODEL_DIR=/path/to/kokoro-multi-lang-v1_0 \
  server/kokoro_tts_service/run_local.sh
```

本地验证：

```bash
curl -X POST http://127.0.0.1:8008/api/tts/kokoro \
  -H 'Content-Type: application/json' \
  -d '{"text":"前方危险，请立即撤离","voice":"zm_yunxi","speed":1.0,"sampleRate":8000,"format":"pcm16"}' \
  --output local-test.pcm
```

## 上传服务器

本地验证没问题后，直接同步服务代码、安装依赖并重启服务器服务：

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

需要覆盖时用环境变量：

```bash
KOKORO_SERVER_HOST=146.56.250.203 \
KOKORO_SSH_KEY=~/.ssh/tji_kokoro_deploy \
  server/kokoro_tts_service/deploy_server.sh
```

## 启动

```bash
export KOKORO_MODEL_DIR=/opt/tji/kokoro-tts/kokoro-multi-lang-v1_0
export KOKORO_TTS_HOST=0.0.0.0
export KOKORO_TTS_PORT=8008
./venv/bin/uvicorn app:app --host "$KOKORO_TTS_HOST" --port "$KOKORO_TTS_PORT"
```

## 调用

```bash
curl -X POST http://146.56.250.203:8008/api/tts/kokoro \
  -H 'Content-Type: application/json' \
  -d '{"text":"前方危险，请立即撤离","voice":"zm_yunxi","speed":1.0,"sampleRate":8000,"format":"pcm16"}' \
  --output test.pcm
```

返回格式：`audio/L16`，8 kHz mono PCM16 little-endian。

## 音色

| voice | sid | 类型 |
| --- | ---: | --- |
| zf_xiaobei | 45 | 女声 |
| zf_xiaoni | 46 | 女声 |
| zf_xiaoxiao | 47 | 女声 |
| zf_xiaoyi | 48 | 女声 |
| zm_yunjian | 49 | 男声 |
| zm_yunxi | 50 | 男声 |
| zm_yunxia | 51 | 男声 |
| zm_yunyang | 52 | 男声 |
