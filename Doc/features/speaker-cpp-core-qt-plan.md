# 喊话器 C++ Core 与 Qt 上位机实施方案

## 状态

- 状态：active
- 适用对象：Android App、Qt/C++ 电脑上位机、服务器临时文件服务、MCU 联调。
- 当前结论：先把喊话器纯算法和协议转换抽成共享 C++ core，再让 Android 通过 JNI 调用、Qt 上位机直接链接。
- 当前落地：Qt 环境已安装；App 最新稳定版已推送远端；`native/speaker-core` 已建立，HADP/ADPCM/UDP 分包第一版 C++ core 已通过 CTest 和服务器上传/下载验证。
- Android JNI shadow mode 已建立：App 可编译 `tji_speaker_core_jni`，Kotlin wrapper 在 native 不可用时安全返回，不改变当前正式业务路径；TTS 临时文件、本地 Kokoro TTS 文件、录音保存 HADP 路径和 UDP 分包路径会输出结构化 shadow 摘要。
- Qt desktop MVP 已建立：`$HOME/Desktop/code/QT/tji-speaker-desktop` 可直接链接 `speaker-core`，console 和 Widgets 两个入口都能生成 HADP、上传服务器、下载比对，并输出 `RECORD_DOWNLOAD` 控制 JSON。
- Qt Widgets 已支持多设备 profiles：可保存、加载、删除命名设备配置，覆盖 deviceId、recordId、服务器、文件路径和 UDP 目标。
- Qt Widgets 已支持麦克风 UDP 流和输入格式转换：优先请求 8 kHz mono PCM16，失败时用设备 preferred format 采集，再混音/线性重采样为 8 kHz mono PCM16 后用 stateful ADPCM packetizer 按 40 ms 分包为 v2 record-store UDP 发送。
- Qt Widgets 已支持麦克风手动增益、轻量自动增益和阈值 Noise Gate：手动增益范围 `-24 dB` 到 `+24 dB`，Noise Gate 阈值范围 `-70 dB` 到 `-20 dB`，相关设置随当前设置和命名 profile 保存，并写入联调日志。
- Qt Widgets/CLI 已支持 Qt Multimedia 解码的压缩音频文件流：macOS AAC/M4A/MP3 均已验证可解码、转 8 kHz mono PCM16，再用连续 ADPCM 状态按 40 ms 分包为 v2 record-store UDP 发送。
- Qt CLI 已增加 UDP monitor 工具：可监听真实设备或桌面端 UDP 流，输出包数、字节数、v1/v2 包分类、首包 header、序号范围和平均包间隔。
- Qt Widgets 已支持导出联调日志：连接参数、生成文件 metadata、`RECORD_DOWNLOAD`、UDP 状态和操作日志可保存为文本文件。
- Qt macOS `.app` bundle target 和本地打包脚本已建立：`TJI Speaker Control.app` 内置 `NSMicrophoneUsageDescription`，`scripts/package-macos.sh` 可运行 `macdeployqt`、ad-hoc 签名校验并生成本地 zip、SHA256SUMS 和 manifest；Apple Developer 签名、公证和正式安装包分发仍待产品化。
- Qt desktop MVP 已初始化为独立本地 Git 仓库，当前本地提交为 `163d545 Add packaged app smoke script`；远端仓库地址待定，拿到地址后可用 `scripts/push-remote.sh` 配置 origin 并推送。

## 1. 目标

把喊话器当前 App 中已经稳定的核心能力抽成可复用 C++ 库，避免 Android App 和电脑上位机各写一套协议。

最终目标：

```text
speaker-core C++
  -> Android App JNI
  -> Qt/C++ 上位机
  -> CMake 单元测试和 golden samples
```

保持不变的正式口径：

```text
ProductCode = Speaker
deviceId = Txxxxxxx
MQTT topic = Speaker/devices/{deviceId}/lifecycle|status|control
HADP 临时文件上传 = /api/speaker/records/upload-temp
```

## 2. 当前 App 代码落点

| 能力 | 当前文件 | 是否适合抽入 C++ core |
|------|----------|------------------------|
| 命令 JSON 生成 | `app/src/main/java/com/tji/device/product/speaker/repository/SpeakerRepository.kt` | 第二期 |
| MQTT 入站解析 | `app/src/main/java/com/tji/device/product/speaker/mqtt/SpeakerMqttInbound.kt` | 第二期 |
| ADPCM UDP 分包 | `app/src/main/java/com/tji/device/product/speaker/audio/SpeakerAdpcmPacketizer.kt` | 第一期 |
| HADP 编码 | `app/src/main/java/com/tji/device/product/speaker/audio/SpeakerHadpEncoder.kt` | 第一期 |
| HADP / ADPCM 解码 | `app/src/main/java/com/tji/device/product/speaker/audio/SpeakerAdpcmDecoder.kt` | 第一期 |
| PTT / TTS 音频处理 | `app/src/main/java/com/tji/device/product/speaker/audio/SpeakerVoiceProcessor.kt` | 第一期后段 |
| 麦克风采集 / UDP 发送 | `app/src/main/java/com/tji/device/product/speaker/audio/SpeakerAudioRelay.kt` | 不抽 |
| TTS 引擎 | `SpeakerTtsSynthesizer` / `SpeakerLocalKokoroTtsClient` | 不抽 |
| 上传临时 HADP | `SpeakerRecordUploadClient` | 不抽，保留平台网络层 |
| UI / ViewModel 状态 | `SpeakerControlViewModel.kt` | 不抽 |

## 3. 不抽进 core 的边界

C++ core 只负责纯逻辑：

- 输入 PCM，输出处理后的 PCM。
- 输入 PCM，输出 `.hadp` 字节和元数据。
- 输入 PCM 帧，输出 UDP audio packet。
- 输入命令参数，输出 JSON 字符串。
- 输入 MQTT JSON，输出结构化解析结果。

C++ core 不负责：

- Android 麦克风权限和 `AudioRecord`。
- Qt 麦克风设备选择。
- UDP socket 发送。
- HTTP 上传。
- MQTT 连接。
- 页面状态、按钮、Toast、Compose 或 Qt Widgets。
- Android TTS / Kokoro 模型调用。

## 4. 版本路线

### V1：冻结 App 行为

目标：不改业务，只把当前 Kotlin 行为固定成 golden samples。

要做：

- 扩展 `SpeakerAudioDataTest`，导出 ADPCM、HADP、PCM 处理样本。
- 增加命令 JSON golden samples。
- 增加 MQTT 入站解析 golden samples。
- 把样本放到 `app/src/test/resources/speaker-core-golden/`。

验收：

```bash
./gradlew :app:testDebugUnitTest
```

### V2：C++ core 第一版

目标：C++ 复刻纯算法，先不接 Android。

当前已建立：

目录：

```text
native/speaker-core/
  CMakeLists.txt
  include/tji_speaker_core.h
  src/
    adpcm_codec.cpp
    udp_packetizer.cpp
    hadp_codec.cpp
    pcm_utils.cpp
    speaker_core_c_api.cpp
    speaker_core_internal.h
  tests/
    speaker_core_tests.cpp
  tools/
    generate_hadp_sample.cpp
    verify_record_upload.py
```

已实现 API：

```cpp
tji_sc_encode_hadp(...)
tji_sc_decode_hadp_pcm16(...)
tji_sc_packetize_adpcm_legacy(...)
tji_sc_packetize_adpcm_v2(...)
tji_sc_adpcm_packetizer_create(...)
tji_sc_adpcm_packetizer_packetize_legacy(...)
tji_sc_adpcm_packetizer_packetize_v2(...)
tji_sc_adpcm_packetizer_reset(...)
tji_sc_adpcm_packetizer_free(...)
tji_sc_crc32(...)
tji_sc_free(...)
```

暂未抽入：

- `SpeakerVoiceProcessor.processPushToTalk`
- `SpeakerVoiceProcessor.applyPlaybackTone`
- 重采样 / tone generator
- 命令 JSON 与 MQTT parser
- JNI 正式替换路径；当前只用于 shadow mode。

验收：

```bash
cmake -S native/speaker-core -B native/speaker-core/build -G Ninja
cmake --build native/speaker-core/build
ctest --test-dir native/speaker-core/build --output-on-failure
```

### V3：Android JNI 接入

目标：Android 可以调用 C++ core，但先 shadow mode。

新增：

```text
app/src/main/cpp/speaker_core_jni.cpp
app/src/main/java/com/tji/device/product/speaker/core/SpeakerCoreNative.kt
app/src/main/java/com/tji/device/product/speaker/core/SpeakerCoreShadowVerifier.kt
```

Gradle 增加：

```kotlin
externalNativeBuild {
    cmake {
        path = file("src/main/cpp/CMakeLists.txt")
    }
}
```

接入策略：

1. Kotlin 原实现继续生产真实结果。
2. `SpeakerCoreNative` 通过 JNI 调用 C++ core，native 不可用时返回 `null`。
3. `SpeakerCoreShadowVerifier` 同时计算一份 C++ 结果。
4. Debug 日志比较 size、CRC、header、frameCount、audioBytes；HADP 主路径和 UDP 分包路径统一输出 `speakerCoreShadow status=...`。
5. 连续通过后再切换真实调用。

验收：

```bash
./gradlew :app:externalNativeBuildDebug :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
```

真机抓 shadow 日志：

```bash
tools/run_speaker_real_device_validation.sh
```

默认会先执行 `./gradlew :app:assembleDebug`，再安装并启动 App，启用 Qt UDP monitor，并要求覆盖 5 条标准 shadow path：`tts-temp-file`、`local-kokoro-tts-file`、`record-save`、`live-legacy-udp`、`recorded-v2-udp`。

脚本开始后会在输出目录写入 `trigger-checklist.md`，现场抓日志时按清单依次触发文字临时文件、本地 Kokoro TTS、录音保存、实时喊话和录音播放路径。

常用覆盖项：

```bash
SERIAL=<adb-serial> DURATION_S=180 UDP_PORT=47000 tools/run_speaker_real_device_validation.sh
SKIP_BUILD=1 APK=app/build/outputs/apk/debug/TJI_Platform_V2.0.4.apk tools/run_speaker_real_device_validation.sh
EXPECT_PACKETS=25 tools/run_speaker_real_device_validation.sh
```

底层命令等价于：

```bash
tools/run_speaker_field_validation.py \
  --duration-s 120 \
  --apk app/build/outputs/apk/debug/TJI_Platform_*.apk \
  --install-apk \
  --launch-app \
  --qt-monitor $HOME/Desktop/code/QT/tji-speaker-desktop/build/apps/qt-speaker-monitor/tji_speaker_monitor \
  --udp-port 47000 \
  --expect-shadow-events 1 \
  --require-shadow-path tts-temp-file \
  --require-shadow-path local-kokoro-tts-file \
  --require-shadow-path record-save \
  --require-shadow-path live-legacy-udp \
  --require-shadow-path recorded-v2-udp
```

`verify_speaker_shadow.py --apk-only` 会先检查 APK 内是否包含：

```text
lib/arm64-v8a/libtji_speaker_core_jni.so
lib/arm64-v8a/libc++_shared.so
```

接手机后，`run_speaker_field_validation.py` 可先安装 APK、启动默认入口 `com.tji.device/.ui.main.MainActivity`，再同时启动 Qt UDP monitor、清空 `SpeakerAudioData` logcat 视图并抓取指定时长日志。抓取期间手动触发 TTS 临时文件、本地 Kokoro TTS 文件、录音保存、实时喊话和录音播放路径。输出摘要重点看：

```text
installStatus=ok
launchStatus=ok
shadowEvents=...
statusCounts=match:...
pathCounts=...
nonMatchEvents=0
requiredShadowPaths=tts-temp-file,local-kokoro-tts-file,record-save,live-legacy-udp,recorded-v2-udp
missingShadowPaths=
triggerChecklist=.../trigger-checklist.md
udpMonitorPackets=...
udpMonitorUnknownPackets=0
udpMonitorSequence=0..N
udpMonitorStatus=ok
reportOutput=.../field-validation-report.md
```

脚本会在输出目录生成 `field-validation-report.md`，报告内会记录期望 shadow 事件数、必需 shadow path、缺失 shadow path、期望 UDP 包数、`shadowStatus`、`udpMonitorStatus`、`trigger-checklist.md` 和原始日志路径，同时保留 `android-shadow.log` 和 `qt-monitor.log`，便于把一次现场联调结果归档。真实设备验收建议保留 `--expect-shadow-events 1`，避免忘记触发喊话链路时出现空日志误通过；保留 `--require-shadow-path` 列表，避免只触发了其中一条链路就误判完整通过。如果抓取期间没有任何 shadow 事件，脚本会输出 `shadowStatus=failed expectedEvents=1 actualEvents=0` 并失败；如果缺路径，会输出 `missingShadowPaths=...` 并失败。

如果只抓 Android shadow、不监听电脑 UDP，可用：

```bash
tools/verify_speaker_shadow.py --duration-s 120 --apk app/build/outputs/apk/debug/TJI_Platform_*.apk
```

手工备用命令：

```bash
adb logcat -c
adb logcat -s SpeakerAudioData | rg 'speakerCoreShadow|record save encoded|tts temp file encoded|local kokoro tts file encoded'
```

通过标准：

```text
speakerCoreShadow status=match path=tts-temp-file ...
speakerCoreShadow status=match path=local-kokoro-tts-file ...
speakerCoreShadow status=match path=record-save ...
speakerCoreShadow status=match path=recorded-v2-udp ...
speakerCoreShadow status=match path=live-legacy-udp ...
```

如果出现 `status=nativeUnavailable`，优先检查 APK 是否打入 `libtji_speaker_core_jni.so`、ABI 是否匹配、`System.loadLibrary("tji_speaker_core_jni")` 是否成功。若出现 `status=mismatch`，日志会带出 `kotlinSize`、`nativeSize`、`mismatchOffset`、两边 CRC 和首包/header 前缀。UDP shadow 使用 stateful native packetizer，能覆盖第二帧之后的连续 ADPCM step index。

### V4：协议层抽入 C++ core

目标：Android 和 Qt 共用命令生成与解析。

抽取：

- `SpeakerCommand -> JSON`
- ACK parser
- state parser
- record_list parser
- storage_status parser
- record_event parser
- recordId / storeTaskId 生成规则

验收：

```bash
./gradlew :app:testDebugUnitTest
ctest --test-dir native/speaker-core/build --output-on-failure
```

### V5：Qt 上位机 MVP

目标：Qt 上位机直接链接 `speaker-core`，先做可联调版本。

当前目录：

```text
$HOME/Desktop/code/QT/tji-speaker-desktop/
  CMakeLists.txt
  README.md
  apps/qt-speaker-console/
    CMakeLists.txt
    src/
      main.cpp
  apps/qt-speaker-control/
    CMakeLists.txt
    src/
      MainWindow.cpp
      MainWindow.h
      main.cpp
  apps/qt-speaker-monitor/
    CMakeLists.txt
    src/
      main.cpp
  shared/
    SpeakerWorkflow.cpp
    SpeakerWorkflow.h
```

当前 MVP 功能：

- 设备 `deviceId` 命令行输入。
- 服务器地址命令行配置。
- Qt Widgets 窗口输入 `deviceId`、`recordId`、服务器地址和输出路径。
- Qt Widgets 使用 `QSettings` 保存设备、服务器和文件路径配置。
- Qt Widgets 保存、加载、删除命名设备 profiles，便于多台喊话器反复联调。
- HADP 本地生成。
- 上传临时 HADP 到服务器。
- 生成 `RECORD_DOWNLOAD` 控制 JSON。
- 下载 `downloadUrl` 并做字节比对。
- 导出 v2 record-store UDP packet 预览文件。
- 配置 UDP host/port 并发送单个 v2 record-store UDP packet。
- 按 40 ms 节奏发送 1 秒 v2 record-store UDP 测试流，使用 stateful ADPCM packetizer，并用 Qt UDP monitor 验证 25 包、序号 `0..24` 和约 40 ms 包间隔。
- 从 raw 8 kHz mono PCM16LE 文件按 40 ms 节奏发送 stateful v2 record-store UDP 流，并用 Kotlin golden PCM 验证 25 包。
- 从 PCM 16-bit mono 8 kHz WAV 文件解析 data chunk 后按 40 ms 节奏发送 stateful v2 record-store UDP 流，并验证 25 包。
- 从 Qt Multimedia 支持的压缩音频文件解码到 8 kHz mono PCM16 后按 40 ms 节奏发送 stateful v2 record-store UDP 流；macOS AAC/M4A 已验证 25 包，MP3 已验证 17 包、680 ms。
- 从默认麦克风采集音频，转换为 8 kHz mono PCM16，并按 40 ms 节奏发送 stateful v2 record-store UDP 流。
- 麦克风流发送前可手动调节 `-24 dB` 到 `+24 dB` 输入增益，也可开启轻量 Auto Gain 和阈值 Noise Gate，并随 profile 保存。
- Qt UDP monitor 监听指定端口，输出 `totalPackets`、`v2Packets`、`firstMagic`、`firstVersion`、`firstSequence`、`lastSequence`、`firstHeader`、`avgGapMs`，用于桌面端自测和真实设备联调。
- 导出 Widgets 联调日志，包含连接参数、生成结果、控制 JSON 和操作日志。
- macOS `.app` bundle 目标输出到 `build/apps/qt-speaker-control/TJI Speaker Control.app`，Info.plist 已包含麦克风权限说明；`scripts/package-macos.sh` 可生成 `dist/TJI-Speaker-Control-macOS.zip`、`dist/SHA256SUMS` 和 `dist/TJI-Speaker-Control-macOS.manifest.json`，`scripts/smoke-packaged.sh` 可验证打包后 `.app` 的服务器上传/下载和 25 包 v2 UDP 流，原 `tji_speaker_control` 可执行文件继续保留给命令行 smoke 和 CI 使用。

暂未做：

- 麦克风高级处理：当前只做通道平均、线性重采样、手动增益、轻量自动增益和阈值 Noise Gate，尚未做频谱降噪和回声消除。
- 压缩音频 codec 覆盖矩阵：当前依赖本机 Qt Multimedia backend，AAC/M4A/MP3 已在 macOS 验证，Windows 仍需补验。
- 真实设备播放路径验证。

验收：

```bash
cd $HOME/Desktop/code/QT/tji-speaker-desktop
source ../scripts/qt-env.sh
cmake -S . -B build -G Ninja
cmake --build build
./build/apps/qt-speaker-console/tji_speaker_console \
  --device-id T12345678 \
  --record-id REC_QT_CONSOLE_SMOKE \
  --server http://146.56.250.203:8008 \
  --output build/generated/REC_QT_CONSOLE_SMOKE.hadp
QT_QPA_PLATFORM=offscreen ./build/apps/qt-speaker-control/tji_speaker_control \
  --smoke \
  --device-id T12345678 \
  --record-id REC_QT_WIDGETS_SMOKE \
  --server http://146.56.250.203:8008 \
  --output build/generated/REC_QT_WIDGETS_SMOKE.hadp \
  --udp-output build/generated/REC_QT_RECORD_STORE_PACKET.bin
```

监听 1 秒 UDP 流时，先在 shell A 启动 monitor：

```bash
./build/apps/qt-speaker-monitor/tji_speaker_monitor \
  --port 47002 \
  --duration-ms 5000 \
  --expect-packets 25
```

再在 shell B 发送测试流：

```bash
QT_QPA_PLATFORM=offscreen ./build/apps/qt-speaker-control/tji_speaker_control \
  --smoke \
  --send-udp-stream \
  --udp-host 127.0.0.1 \
  --udp-port 47002
```

其他文件流 smoke：

```bash
QT_QPA_PLATFORM=offscreen ./build/apps/qt-speaker-control/tji_speaker_control \
  --smoke \
  --send-pcm-file-stream \
  --pcm-input $HOME/Desktop/code/TJI/TJI_Platform/app/src/test/resources/speaker-core-golden/voice_1s_8k_pcm16le.raw \
  --udp-host 127.0.0.1 \
  --udp-port 47003
QT_QPA_PLATFORM=offscreen ./build/apps/qt-speaker-control/tji_speaker_control \
  --smoke \
  --send-pcm-file-stream \
  --pcm-input build/generated/voice_1s_8k_pcm16le.wav \
  --udp-host 127.0.0.1 \
  --udp-port 47004
```

### V6：产品化收尾

目标：从可联调变成客户可用。

- Windows 打包和签名。
- macOS Developer ID 签名、公证和正式安装包分发。
- 日志归档和自动附加最近联调记录。
- 崩溃日志。
- 设备发现。
- 多设备列表批量管理。
- 弱网重试。
- 协议版本协商。
- MCU 固件兼容矩阵。

## 5. Qt 环境

当前 Mac 环境采用 Homebrew 安装 Qt：

```bash
brew install qt ninja
brew install --cask qt-creator
```

Qt 工作区：

```text
$HOME/Desktop/code/QT
```

环境脚本：

```bash
source $HOME/Desktop/code/QT/scripts/qt-env.sh
```

smoke 工程：

```bash
cd $HOME/Desktop/code/QT/examples/qt-smoke
source $HOME/Desktop/code/QT/scripts/qt-env.sh
cmake -S . -B build -G Ninja
cmake --build build
```

## 6. Git 同步流程

每次进入 C++ core 提取前，先同步当前 App 稳定状态。

标准流程：

```bash
git status --short --branch
./gradlew checkDocs :app:testDebugUnitTest :NetWork:testDebugUnitTest
git add README.md Doc tools build.gradle.kts app/src/main/java app/src/test/java
git commit -m "Align platform docs and device identity"
git push origin main
```

约束：

- 只在测试通过后 push。
- push 前看 `git diff --stat`，确认没有误加入本地大文件、模型、APK、build 目录。
- 如果远端拒绝 push，先 `git pull --rebase origin main`，解决冲突后重新测试。

## 7. Golden Samples 对齐

Kotlin golden fixtures 位置：

```text
app/src/test/resources/speaker-core-golden/
```

当前覆盖：

- 1 秒 8 kHz mono PCM16 synthetic voice。
- legacy ADPCM UDP frame 0 / frame 1。
- v2 record-store last packet。
- PCM16 HADP。
- IMA ADPCM HADP。
- 元数据 CRC、frameCount、fileSize、durationMs。

刷新 fixtures：

```bash
TJI_UPDATE_SPEAKER_GOLDEN=1 \
  ./gradlew :app:testDebugUnitTest \
  --tests 'com.tji.device.product.speaker.audio.SpeakerCoreGoldenFixtureTest'
```

普通校验：

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.tji.device.product.speaker.audio.SpeakerCoreGoldenFixtureTest'
```

C++ 字节级对齐：

```bash
source $HOME/Desktop/code/QT/scripts/qt-env.sh
cmake -S native/speaker-core -B native/speaker-core/build -G Ninja
cmake --build native/speaker-core/build
ctest --test-dir native/speaker-core/build --output-on-failure
```

约束：

- 只有确认 Kotlin 当前行为就是新标准时，才允许刷新 fixture。
- 刷新后必须同时跑 Kotlin 单测和 C++ CTest。
- C++ 对 fixture 的校验必须是字节级，不只比 header 或 CRC。

## 8. 服务器交换测试

服务器临时 HADP 上传接口：

```text
POST /api/speaker/records/upload-temp
```

服务代码：

```text
server/kokoro_tts_service/app.py
```

健康检查：

```bash
curl http://146.56.250.203:8008/health
```

C++ core 提取后必须验证：

1. C++ 生成 `.hadp`。
2. 计算 `fileSize`、`crc32`、`durationMs`、`codec`、`sampleRate`、`channels`、`packetMs`、`frameBytes`、`samplesPerFrame`。
3. 上传到服务器。
4. 服务器返回 `ok=true` 和 `downloadUrl`。
5. 下载 `downloadUrl`，比对文件字节完全一致。

当前自动化命令：

```bash
source $HOME/Desktop/code/QT/scripts/qt-env.sh
cmake -S native/speaker-core -B native/speaker-core/build -G Ninja
cmake --build native/speaker-core/build
native/speaker-core/tools/verify_record_upload.py \
  --generator native/speaker-core/build/tji_speaker_core_sample \
  --output native/speaker-core/generated/REC_CPP_CORE_SMOKE.hadp
```

成功输出示例：

```text
{"ok":true,...,"codec":"ima_adpcm","frameBytes":164,"samplesPerFrame":320,...}
downloadVerified=true
```

## 9. Android 替换顺序

推荐顺序：

1. `SpeakerHadpEncoder` -> JNI。
2. `SpeakerAdpcmPacketizer` -> JNI。
3. `SpeakerAdpcmDecoder` -> JNI。
4. `SpeakerVoiceProcessor.processPushToTalk` -> JNI。
5. `SpeakerVoiceProcessor.applyPlaybackTone` -> JNI。
6. `SpeakerCommand.toJson` -> JNI。
7. `SpeakerMqttInbound` parser -> JNI。

每一步都保留 Kotlin fallback：

```kotlin
val result = runCatching {
    SpeakerCoreNative.encodeHadp(...)
}.getOrElse {
    SpeakerHadpEncoder.encode(...)
}
```

Debug 期可以同时计算：

```text
Kotlin result CRC == C++ result CRC
Kotlin header == C++ header
Kotlin metadata == C++ metadata
```

## 10. Qt 上位机通信边界

Qt 负责：

- UI。
- 设备列表。
- TCP / UDP / MQTT / HTTP。
- 文件选择和播放控制。
- 日志展示。

`speaker-core` 负责：

- PCM 处理。
- HADP。
- ADPCM。
- UDP packet bytes。
- 命令 JSON。
- MQTT payload parser。

这样即使以后 Qt 不满意，换 C# 上位机也可以继续调用同一个 C ABI / DLL。

## 11. 回滚策略

每个阶段必须能独立回滚：

- V1 只加测试和样本，不改运行时。
- V2 只加 C++ core，不接 App。
- V3 JNI 默认 shadow mode，不影响 App 正式行为。
- V4 每个 parser 单独替换，失败回 Kotlin fallback。
- V5 Qt 上位机独立工程，不阻塞 Android 发布。

## 12. 近期执行清单

1. 已完成：当前 App 测试通过并推送远端。
2. 已完成：建立 `native/speaker-core`。
3. 已完成：迁移 HADP/ADPCM/UDP 分包到 C++ 第一版。
4. 已完成：增加 CMake/CTest。
5. 已完成：增加服务器上传验证脚本，并完成上传/下载字节比对。
6. 已完成：从 App 当前实现导出 Kotlin golden samples，补齐 C++ 与 Kotlin 字节级对齐测试。
7. 已完成：Android 接 JNI shadow mode，Debug/Release native build 均通过。
8. 已完成：Qt console + Widgets MVP 接入 core，并完成服务器上传/下载字节比对。
9. 已完成：Qt Widgets 增加日志导出，并完成本地编译与服务器 smoke 验证。
10. 已完成：Qt Widgets 增加命名设备 profiles，并完成本地编译、窗口启动和服务器 smoke 验证。
11. 已完成：Qt Widgets 增加麦克风 UDP 流第一版，并完成本地编译、窗口启动和服务器 smoke 回归。
12. 已完成：Qt Widgets 增加麦克风输入格式转换，支持从设备 preferred format 转为 8 kHz mono PCM16 后分包发送。
13. 已完成：Qt shared workflow 增加压缩音频解码文件流，macOS AAC/M4A smoke 验证 25 包、1000 ms。
14. 已完成：Qt Widgets 增加麦克风手动增益控制，支持 profile 保存和日志导出，并完成本地编译、窗口启动和文件流 smoke 回归。
15. 已完成：Qt shared workflow 通过本机 MP3 样本完成 MP3 解码 smoke，验证 17 包、680 ms。
16. 已完成：Qt Widgets 增加麦克风轻量 Auto Gain，支持 profile 保存和日志导出，并完成本地编译、窗口启动和文件流 smoke 回归。
17. 已完成：Qt Widgets 增加麦克风阈值 Noise Gate，支持 profile 保存和日志导出，并完成本地编译、窗口启动和文件流 smoke 回归。
18. 已完成：Qt CLI 增加 UDP monitor，完成服务器上传/下载 smoke 和本地流监听验证：25 个 v2 包、序号 `0..24`、平均间隔约 `40.25 ms`。
19. 已完成：Android HADP shadow 日志结构化，TTS 临时文件、本地 Kokoro TTS 文件和录音保存路径输出 `status=match|mismatch|nativeUnavailable`、CRC、size、header 前缀和路径 metadata。
20. 已完成：C++ core 增加 stateful ADPCM UDP packetizer C ABI，CTest 验证 stateful v2 UDP payload 与 HADP 连续 ADPCM frame 字节一致。
21. 已完成：Android JNI 增加 stateful packetizer wrapper，UDP shadow session 接入 live legacy UDP 和 recorded v2 UDP 分包路径。
22. 已完成：Qt 1 秒流、文件流和麦克风流切换到 stateful ADPCM packetizer；本地 monitor smoke 验证 25 个 v2 包、序号 `0..24`、平均间隔约 `40.125 ms`。
23. 已完成：新增 `tools/verify_speaker_shadow.py`，支持 APK native lib 检查、ADB 设备选择、logcat 抓取和 `speakerCoreShadow` 状态/路径汇总。
24. 已完成：新增 `tools/run_speaker_field_validation.py`，可同时编排 APK native lib 检查、Android shadow logcat 抓取和 Qt UDP monitor 输出汇总。
25. 已完成：field validation 脚本增加 `--install-apk` 和 `--launch-app`，默认安装后启动 `com.tji.device/.ui.main.MainActivity`。
26. 已完成：field validation 脚本自动生成 `field-validation-report.md`，汇总 APK、安装启动、Android shadow、Qt UDP monitor 和日志路径。
27. 已完成：Qt macOS `.app` bundle target 建立，`Info.plist` 包含 `NSMicrophoneUsageDescription`；完成 bundle build、PlistBuddy 权限字段检查、服务器上传/下载 smoke 和本地 monitor 验证：25 个 v2 包、序号 `0..24`、平均间隔约 `40.4167 ms`。
28. 已完成：Qt 新增 macOS 本地打包脚本和分发说明，`macdeployqt` 使用 Homebrew library search path、ad-hoc 签名、错误输出拦截和 zip 产物生成；完成打包脚本验证、codesign 校验和 `.app` 包内可执行文件 smoke，monitor 验证 25 个 v2 包、序号 `0..24`、平均间隔约 `40.0417 ms`。
29. 已完成：field validation 脚本新增 `--expect-shadow-events`，真实验收可要求至少抓到 1 条 Android shadow 事件，报告会记录期望值、`shadowStatus` 和 `udpMonitorStatus`，避免空日志误通过；emulator 预演已验证 APK native libs、安装、启动和报告生成链路。
30. 已完成：field validation 脚本新增 `--require-shadow-path`，可要求覆盖 `tts-temp-file`、`local-kokoro-tts-file`、`record-save`、`live-legacy-udp`、`recorded-v2-udp` 等关键路径，报告会记录必需路径和缺失路径。
31. 已完成：新增 `tools/run_speaker_real_device_validation.sh`，把构建、安装、启动、Qt monitor、标准 shadow path 验收和常用环境变量覆盖收敛为真实设备一键入口；emulator 预演已验证参数拼装和缺路径失败报告。
32. 已完成：field validation 输出目录新增 `trigger-checklist.md`，列出抓取窗口、UDP 端口、必需 shadow path 和现场人工触发动作，报告会链接该清单。
33. 已完成：Qt macOS 打包脚本新增 `SHA256SUMS` 和 manifest，记录 bundle id、版本、Qt 版本、本地 Git commit、zip 字节数、SHA-256、签名模式和 notarization 状态；完成 `shasum -a 256 -c`、codesign 校验和 `.app` 包内可执行文件 smoke，monitor 验证 25 个 v2 包、序号 `0..24`、平均间隔 `40 ms`。
34. 已完成：Qt 仓库新增 `scripts/push-remote.sh`，拿到远端地址后可检查干净工作区、配置或更新 `origin`，并 `git push -u origin main`；已完成 shell 语法和 usage 检查。
35. 已完成：Qt 仓库新增 `scripts/smoke-packaged.sh`，自动拉起 monitor、运行 `.app` 内可执行文件、保存 sender/monitor 日志，并校验 `downloadVerified=true`、25 个 v2 UDP 包、序号 `0..24` 和 `unknownPackets=0`；本机脚本化 smoke 已通过，平均间隔约 `40.0833 ms`。
36. 下一步：在真实 Android 设备上运行 field validation 脚本，确认 shadow 全 `match`、必需路径无缺失，Qt monitor 包数、序号和间隔正常。
37. 下一步：Qt 麦克风频谱降噪/回声消除、Windows codec 覆盖补验和真实设备播放路径验证。
38. 下一步：为 `$HOME/Desktop/code/QT/tji-speaker-desktop` 创建远端 Git 仓库并使用 `scripts/push-remote.sh` 推送。
