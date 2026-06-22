# 喊话器 App 落地

## 状态

- 状态：active
- 产品代码：`speaker`

## 代码落点

```text
app/src/main/java/com/tji/device/product/speaker/
  audio/
  model/
  mqtt/
  repository/
  runtime/
  ui/
  viewmodel/
```

## 当前 App 能力

- 实时喊话音频处理。
- 本地 / 系统 TTS。
- ADPCM packetizer 和 HADP encoder。
- `.hadp` 临时上传。
- 录音库分页、保存、删除、改名和播放。
- 音量、音质、音色设置。
- 客户界面隐藏底层包数和调试入口。

## HADP 协议

App 生成 `.hadp` 必须遵守 [hadp-file-format.md](hadp-file-format.md)。当前主路径通过 `SpeakerCoreAudioEngine` 调用 native `speaker-core`，Kotlin 实现只作为 fallback 和影子对照。

后续 Qt 上位机接入时，应复用同一套 `native/speaker-core`，不要按 Kotlin 或 UI 代码重新实现一份 HADP 编码。

## 本地模型资源

本地 Kokoro TTS 需要额外准备模型资源：

```text
app/src/main/assets/kokoro-multi-lang-v1_0/
```

大模型资源不直接提交普通 Git，正式分发前应走 Git LFS、制品下载或安装包内置资源流程。

## 测试

- `SpeakerTalkSectionTest` 锁住客户可见状态文案，不显示 `packetsSent` 或“包数”。
- 后续继续补音频链路、录音库分页、HADP 元数据和错误状态测试。
