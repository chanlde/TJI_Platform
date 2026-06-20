# 喊话器 C++ Core 与 Qt 上位机实施方案

## 状态

- 状态：active
- 适用对象：Android App、Qt/C++ 电脑上位机、服务器临时文件服务、MCU 联调。
- 当前结论：先把喊话器纯算法和协议转换抽成共享 C++ core，再让 Android 通过 JNI 调用、Qt 上位机直接链接。
- 当前落地：Qt 环境已安装；App 最新稳定版已推送远端；`native/speaker-core` 已建立，HADP/ADPCM/UDP 分包第一版 C++ core 已通过 CTest 和服务器上传/下载验证。
- Android JNI shadow mode 已建立：App 可编译 `tji_speaker_core_jni`，Kotlin wrapper 在 native 不可用时安全返回，不改变当前正式业务路径。
- Qt desktop MVP 已建立：`$HOME/Desktop/code/QT/tji-speaker-desktop` 可直接链接 `speaker-core`，console 和 Widgets 两个入口都能生成 HADP、上传服务器、下载比对，并输出 `RECORD_DOWNLOAD` 控制 JSON。

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
tji_sc_crc32(...)
tji_sc_free(...)
```

暂未抽入：

- `SpeakerVoiceProcessor.processPushToTalk`
- `SpeakerVoiceProcessor.applyPlaybackTone`
- 重采样 / tone generator
- 命令 JSON 与 MQTT parser
- Android JNI wrapper

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
4. Debug 日志比较 size、CRC、header、frameCount、audioBytes。
5. 连续通过后再切换真实调用。

验收：

```bash
./gradlew :app:externalNativeBuildDebug :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
```

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
  shared/
    SpeakerWorkflow.cpp
    SpeakerWorkflow.h
```

当前 MVP 功能：

- 设备 `deviceId` 命令行输入。
- 服务器地址命令行配置。
- Qt Widgets 窗口输入 `deviceId`、`recordId`、服务器地址和输出路径。
- Qt Widgets 使用 `QSettings` 保存设备、服务器和文件路径配置。
- HADP 本地生成。
- 上传临时 HADP 到服务器。
- 生成 `RECORD_DOWNLOAD` 控制 JSON。
- 下载 `downloadUrl` 并做字节比对。
- 导出 v2 record-store UDP packet 预览文件。
- 配置 UDP host/port 并发送单个 v2 record-store UDP packet。

暂未做：

- 多设备配置管理。
- 连续 UDP 播放流和真实设备播放路径验证。
- 日志导出。

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

### V6：产品化收尾

目标：从可联调变成客户可用。

- Windows 打包和签名。
- macOS 打包和权限说明。
- 日志导出。
- 崩溃日志。
- 设备发现。
- 多设备列表。
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
9. 下一步：在真实 Android 设备上打开 shadow 日志，连续比对真实录音/播放路径。
10. 下一步：Qt 多设备配置管理、连续 UDP 播放流/真实设备播放路径验证和日志导出。
