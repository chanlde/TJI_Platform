# 喊话器 Payload UI 重做说明

更新时间：2026-06-08

## 1. 结论

可以先拿喊话器做试点，把之前 Open Design 里的喊话器页面用一套新的 UI 系统重做。

这套 UI 不直接使用 `@openai/apps-sdk-ui` 的代码，因为当前 App 是 Android Jetpack Compose，而 Apps SDK UI 是 React + Tailwind 组件库。正确做法是借鉴它的设计语言和组件纪律，再在 Compose 里实现一套 `Payload UI` 组件。

目标不是做“无人机控制 App”，而是做：

**无人机负载设备控制台 / Payload Device Console**

喊话器是非常适合试点的产品线，因为它同时包含：

- 设备状态
- 按住喊话
- 音量调节
- 文字转语音
- 录音保存
- 录音库管理
- 高级音色设置
- 容量/反馈/错误状态

如果喊话器这套 UI 跑通，后续消防吊桶、光伏清洗、六段投放、无线电检测都可以沿用同一套设计系统。

## 2. 为什么不能直接用 Apps SDK UI

`apps-sdk-ui` 适合 ChatGPT Apps / Web Widget，技术栈是：

- React 18/19
- Tailwind 4
- Radix primitives
- Web CSS design tokens

当前 App 技术栈是：

- Android
- Kotlin
- Jetpack Compose
- Material 3
- 原生麦克风、MQTT、后台服务、悬浮窗

所以不建议用 WebView 直接嵌 React 页面。WebView 会让麦克风权限、长按录音、MQTT 状态、后台服务、悬浮窗交互都更复杂。

推荐方式：

- 参考 Apps SDK UI 的视觉语言。
- 在 Open Design 里先画喊话器新版。
- 再把视觉稿翻译成 Compose 组件。

## 3. 设计定位

### 3.1 产品定位

喊话器不是普通音频播放器，也不是消费级智能音箱。

它是无人机负载设备，核心场景是现场任务：

- 快速确认设备在线。
- 快速按住喊话。
- 调整输出音量。
- 播放/管理预置录音。
- 输入文字并合成喊话。
- 查看容量、错误、设备反馈。

### 3.2 视觉关键词

推荐：

- 工业级
- 可靠
- 清晰
- 克制
- 高对比
- 任务优先
- 状态明确

不推荐：

- 大面积渐变
- 营销感产品卡
- 过度插画
- 过多圆角胶囊
- 类金融 App
- 类无人机飞控 HUD
- 一屏堆满说明文字

## 4. 参考系统如何迁移

### 4.1 从 Apps SDK UI 借鉴的点

借鉴：

- 清晰的 surface 层级。
- 小而明确的 Badge。
- Primary / Secondary / Soft / Danger 按钮分级。
- 卡片不靠大阴影堆质感，而靠边框、间距、排版。
- 内容先行，装饰退后。
- 每个模块都有明确下一步动作。

不照搬：

- Web 的 Tailwind class。
- React 组件 API。
- ChatGPT 内嵌 App 的窄容器约束。
- 纯 Web 的 hover 交互。

### 4.2 Compose 中对应组件

| Apps SDK UI 思路 | Compose 组件建议 | 用途 |
| --- | --- | --- |
| Surface | `PayloadPanel` | 状态区、控制区、录音区 |
| Badge | `PayloadStatusBadge` | 在线、离线、发送中、成功、失败 |
| Button primary | `PayloadPrimaryButton` | 播放、发送、保存 |
| Button soft | `PayloadSoftButton` | 刷新、测试音色 |
| Button danger | `PayloadDangerButton` | 停止、删除 |
| Field row | `PayloadInfoRow` | SN、容量、版本、状态 |
| Metric card | `PayloadMetricTile` | 音量、容量、录音数 |
| Section | `PayloadSection` | 首页、录音库、文字喊话、高级 |
| List row | `PayloadRecordRow` | 录音条目 |
| Segmented control | `PayloadSegmentTabs` | 喊话 / 录音 / 文字 / 高级 |

## 5. 喊话器新版页面结构

### 5.1 顶部区域

目的：让用户一眼知道当前控制的是哪台负载设备。

内容：

- 设备名
- SN 后 4 位或完整 SN 的弱展示
- 在线状态
- 当前任务状态：空闲 / 录音中 / 发送中 / 保存中 / 播放中
- 右侧小按钮：刷新状态或设备设置

布局建议：

- 不做大 hero。
- 顶部是紧凑设备 header。
- 高度控制在 88-112dp。
- 状态 badge 使用小尺寸，避免压过主操作。

### 5.2 喊话首页

核心动作：按住喊话。

页面分区：

1. 设备状态条
2. 按住喊话主控
3. 输出音量
4. 最近反馈

主控建议：

- 中央大按钮可以保留，但不要做夸张拟物。
- 按下时状态变为 `录音中`，按钮颜色加深。
- 松开后进入 `发送中`，显示进度或简短反馈。
- 无麦克风权限时显示明确禁用态和授权动作。

音量建议：

- 使用短滑条，不要横跨整屏。
- 右侧显示百分比。
- 停止按钮使用 danger 或 warning，不和主按钮同色。

### 5.3 录音库

核心动作：保存喊话、搜索录音、播放/改名/删除。

页面分区：

1. 容量概览
2. 保存一段喊话
3. 搜索和刷新
4. 录音列表

容量概览建议：

- 使用 `PayloadMetricTile` 三列：
  - 已用容量
  - 剩余容量
  - 录音数量
- 用一条细进度条表达容量。
- 失败状态使用 warning/error badge，不显示底层 code 为主。

录音列表建议：

- 每条录音是一行，不做大卡片。
- 左侧小波形图标。
- 中间：名称、时长、创建时间/文件大小。
- 右侧：播放按钮。
- 次级操作：改名、删除，删除要弱化但明确危险色。

### 5.4 文字喊话

核心动作：输入文本并合成喊话。

页面分区：

1. 文本输入
2. 引擎选择
3. 音色/语速
4. 发送按钮

建议：

- 输入框高度固定，避免内容推动页面乱跳。
- 引擎选择使用 segmented control。
- 音色选择使用 compact selector。
- 发送按钮固定在区块底部。
- 发送中按钮禁用并显示状态。

### 5.5 高级设置

核心动作：调音色和测试。

页面分区：

1. 低音/高音滑条
2. 音色测试
3. 高级 TTS 设置

建议：

- 高级设置默认不要抢主页面视觉。
- 高级参数以 compact row + slider 呈现。
- 测试按钮使用 soft primary。
- 所有滑条都要有当前数值和单位。

## 6. 视觉系统

### 6.1 颜色 Token

建议先用浅色系统，后续控制页可扩展暗色任务模式。

| Token | 建议色值 | 用途 |
| --- | --- | --- |
| `PayloadBackground` | `#F6F7F9` | 页面背景 |
| `PayloadSurface` | `#FFFFFF` | 主面板 |
| `PayloadSurfaceMuted` | `#F1F3F5` | 次级面板 |
| `PayloadBorder` | `#DDE2E8` | 边框/分割线 |
| `PayloadText` | `#101318` | 主文字 |
| `PayloadTextSecondary` | `#59616D` | 次级文字 |
| `PayloadTextMuted` | `#8A93A1` | 弱信息 |
| `PayloadPrimary` | `#2563EB` | 主操作 |
| `PayloadPrimarySoft` | `#EAF1FF` | 主操作弱背景 |
| `PayloadSuccess` | `#16A34A` | 在线/成功 |
| `PayloadWarning` | `#F59E0B` | 警告/等待 |
| `PayloadDanger` | `#DC2626` | 失败/删除/停止 |
| `PayloadInfo` | `#0891B2` | 处理中/发送中 |

### 6.2 字体层级

| 用途 | 建议 |
| --- | --- |
| 页面标题 | 20sp / 700 |
| 区块标题 | 16sp / 700 |
| 卡片标题 | 14sp / 600 |
| 正文 | 13-14sp / 400-500 |
| 标签 | 11-12sp / 500 |
| 关键数值 | 20-24sp / 700 |
| 按钮文字 | 14sp / 600 |

### 6.3 圆角

| 组件 | 圆角 |
| --- | --- |
| 页面主面板 | 16dp |
| 控制按钮 | 12dp |
| 小按钮 | 8dp |
| 输入框 | 12dp |
| 状态 Badge | 999dp |
| 录音列表行 | 10dp |

### 6.4 间距

建议使用 4dp 基准：

- 页面左右：16dp
- 区块间距：12-16dp
- 卡片内边距：14-16dp
- 列表行内边距：12dp
- 图标和文字：8dp

## 7. Open Design 试点交付物

先不要做全 App。Open Design 里只做喊话器 4 个 screen。

### 7.1 Screen A：喊话首页

必须包含：

- 设备 header
- 在线状态
- 主按住喊话按钮
- 输出音量
- 停止按钮
- 最近命令反馈

状态：

- 默认在线
- 正在录音
- 发送中
- 麦克风未授权
- 离线禁用

### 7.2 Screen B：录音库

必须包含：

- 容量概览
- 保存录音主控
- 搜索框
- 刷新按钮
- 录音列表
- 删除危险态

状态：

- 有录音
- 空列表
- 容量查询失败
- 正在保存

### 7.3 Screen C：文字喊话

必须包含：

- 文本输入
- TTS 引擎选择
- 音色选择
- 语速设置
- 发送按钮

状态：

- 默认
- 文本为空
- 合成中
- 发送失败

### 7.4 Screen D：高级设置

必须包含：

- 低音/高音
- 音色测试
- TTS 高级参数

状态：

- 默认
- 测试播放中
- 设置失败反馈

## 8. Compose 落地顺序

### Phase 1：建立组件

新增建议文件：

- `PayloadTheme.kt`
- `PayloadPanel.kt`
- `PayloadStatusBadge.kt`
- `PayloadActionButton.kt`
- `PayloadMetricTile.kt`
- `PayloadSection.kt`
- `PayloadSegmentTabs.kt`

不要一开始改所有页面。

### Phase 2：重做喊话器页面

优先替换：

- `SpeakerControlChrome.kt`
- `SpeakerTalkSection.kt`
- `SpeakerRecordSection.kt`
- `SpeakerRecordPanel.kt`
- `SpeakerTextSettingsSection.kt`

保留：

- ViewModel
- Repository
- MQTT 协议
- TTS/录音业务逻辑

### Phase 3：验证

必须验证：

- `./gradlew testDebugUnitTest assembleRelease`
- 小屏 360dp 宽度文字不挤压
- 离线态不可误操作
- 麦克风权限未授权有明确入口
- 删除录音有危险色和确认/防误触策略

## 9. 与当前代码的对应关系

当前喊话器 UI 已经拆分过，适合直接替换视觉层：

| 当前文件 | 重做重点 |
| --- | --- |
| `SpeakerControlScreen.kt` | 保持 screen coordinator，不放复杂 UI |
| `SpeakerControlChrome.kt` | 替换顶部栏、底部 tab、公共色值 |
| `SpeakerTalkSection.kt` | 重做按住喊话和音量控制 |
| `SpeakerRecordPanel.kt` | 重做录音保存和搜索列表容器 |
| `SpeakerRecordSection.kt` | 重做容量卡、录音行、波形图标 |
| `SpeakerTextSettingsSection.kt` | 重做文本喊话和高级参数 |

## 10. 成功标准

喊话器新版 UI 通过以下标准才算可继续推广到其他产品：

- 一眼能看出这是负载设备控制台，不是普通消费 App。
- 首页主操作清楚：按住喊话。
- 录音库不是卡片堆砌，而是高效列表。
- 状态、错误、成功反馈统一用 badge / feedback row。
- 操作按钮有主次和危险级别。
- 页面不依赖大图或渐变撑美观。
- 所有控件在 360dp 宽度下不重叠。
- Compose 代码仍保持拆分后的结构，不回到大文件。

## 11. 推荐方向

先让 Open Design 产出一版：

**Light Payload Console**

风格：

- 浅灰背景
- 白色/浅灰 surface
- 低阴影或无阴影
- 细边框
- 蓝色主操作
- 青色处理中
- 绿色在线/成功
- 橙色警告
- 红色危险

等喊话器试点确认后，再考虑是否给“现场任务模式”增加暗色主题。
