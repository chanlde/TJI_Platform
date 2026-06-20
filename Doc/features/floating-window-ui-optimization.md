# 悬浮窗与 App UI 交互优化方案

更新时间：2026-05-06

## 状态

- 状态：draft
- 说明：悬浮窗体验优化方案，部分问题已处理，剩余交互细节仍需结合当前实现继续核对。

## 1. 当前问题

光伏清洗悬浮窗目前已经完成了紧凑化、透明度设置、ACK 反馈和设备同步，但仍有几个体验问题需要继续打磨：

- Switch 切换时中间圆点不是顺滑滑过去，而是短暂跳变，视觉上像“方块”。
- 自定义 Switch 当前用 `contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart` 直接切换位置，缺少位移动画。
- 开关和滑动条是快捷控制，必须轻、稳、反馈明确，不能像完整控制台。
- 透明背景默认全透明后，可读性依赖底层页面，复杂背景下需要允许用户调高透明度。
- 离线状态下控制是否禁用需要统一，避免用户点了没反应但不知道原因。

## 2. 设计方向

采用轻量级 Glassmorphism 快捷控制风格：

- 外层：可调透明度，默认全透明。
- 内容：图标 + 短滑条 + 数值，避免长滑条占屏。
- 开关：小型自定义 Switch，保留 44dp 以上可点击区域。
- 反馈：标题栏显示 `具体操作 + 成功/失败/无响应`，不展示 ACK、msgId、JSON。
- 动效：150-220ms，使用 `animateDpAsState` / `animateColorAsState`，不要用布局硬切换。

## 3. Switch 问题原因

当前 `MiniSwitch` 的核心逻辑类似：

```kotlin
Box(
    contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .background(Color.White, RoundedCornerShape(99.dp))
    )
}
```

这个写法的问题：

- `checked` 改变时，thumb 从左到右是“重新布局”，不是“动画位移”。
- Compose 会重新计算 `contentAlignment`，圆点位置瞬间跳到另一侧。
- 由于外层尺寸很小、背景透明、重组很快，切换瞬间容易看到不自然的方块感。

## 4. 推荐实现方案

### 4.1 使用动画偏移替代 contentAlignment

推荐结构：

```kotlin
val thumbOffset by animateDpAsState(
    targetValue = if (checked) 16.dp else 0.dp,
    animationSpec = tween(
        durationMillis = 180,
        easing = FastOutSlowInEasing
    ),
    label = "miniSwitchThumbOffset"
)

Box(
    modifier = Modifier
        .size(width = 34.dp, height = 18.dp)
        .clip(RoundedCornerShape(99.dp))
        .background(trackColor)
) {
    Box(
        modifier = Modifier
            .offset(x = thumbOffset)
            .size(14.dp)
            .clip(CircleShape)
            .background(Color.White)
    )
}
```

关键点：

- track 用固定圆角，不随状态改变尺寸。
- thumb 用 `offset` 做位移动画。
- thumb 必须 `clip(CircleShape)`，不要只依赖 `RoundedCornerShape`。
- 颜色用 `animateColorAsState`，避免 track 颜色闪变。

### 4.2 点击区域和视觉尺寸分离

视觉 Switch 可以很小，但点击区域不能太小：

- 视觉尺寸：`34dp x 18dp`
- 点击区域：建议外层 cell 至少 `44dp` 高，或给 `Modifier.padding` 扩展触控范围。
- 不要只让小圆点可点，整个 `水泵 / 摆动` cell 都应该可点。

### 4.3 离线状态

建议离线时直接禁用控制：

```kotlin
val enabled = !serialNumber.isNullOrBlank() && link?.isOnline == true
```

离线状态表现：

- 滑动条不可拖动。
- Switch 不可切换。
- 标题栏显示红色 `离线`。
- 不发送 MQTT 控制指令。

这是给客户用的逻辑，比“允许发送然后无响应”更清楚。

## 5. ACK 反馈规范

保留现在的方向：

- 成功：`水泵设置成功`
- 失败：`水泵设置失败`
- 超时：`水泵设置无响应`

不显示：

- `ack`
- `msgId`
- `cmd`
- `code`
- JSON payload

显示位置：

- 主控制页面：设备控制标题右侧。
- 悬浮窗：标题栏设备状态旁边。

显示时长：

- 成功/失败：约 2 秒。
- 无响应：约 2-3 秒。

## 6. 悬浮窗视觉规范

### 6.1 尺寸

当前尺寸不再继续扩大。后续只做内部动效和状态优化。

### 6.2 背景透明度

设置项已加入：

- 默认 `0%`，全透明。
- 用户可调高背景透明度。
- 透明度只影响光伏清洗悬浮窗，不影响消防吊桶。

建议范围：

- `0%`：适合干净背景，最轻。
- `10-20%`：推荐日常使用，有一点玻璃承托。
- `30%+`：适合复杂背景，提高可读性。

### 6.3 图标

保持当前代码绘制图标方向：

- 不用 emoji。
- 不依赖 PNG。
- 图标 stroke 保持统一。
- 滑条图标、开关图标使用同一视觉语言。

## 7. App 级 UI 优化清单

### P0：必须修

- 修复自定义 Switch thumb 动画，避免切换方块/跳变。
- 离线设备禁用悬浮窗控制，不再发送 MQTT 控制。
- 所有 icon-only 按钮补 `contentDescription`，确保可访问性。

### P1：建议近期做

- 给悬浮窗 Switch 增加轻微 pressed feedback，点击时有即时反馈。
- ACK 成功时可给状态胶囊轻微 fade-in/fade-out。
- 滑条拖动结束才发 MQTT，拖动过程中只更新本地 UI。
- 设置页透明度滑条文案可改成“背景强度”，用户更容易理解。

### P2：后续优化

- 已完成：抽出通用 `TjiMiniSwitch`，悬浮窗开关使用同一套动画和状态。
- 已完成：抽出 `TjiFeedbackBadge`，主控制页和悬浮窗复用 ACK 反馈样式。
- 已完成：抽出 `TjiOnlineStatus`，设备卡片、详情顶栏、悬浮窗在线状态统一。
- 已完成：建立 App UI token 和公共卡片、按钮、滑条组件。
- 已完成：保留悬浮窗、小屏、离线状态 Preview，方便后续继续调 UI。

## 8. 推荐下一步实现顺序

1. 修复 `MiniSwitch` 动画：`animateDpAsState` + `animateColorAsState`。
2. 离线时禁用悬浮窗控制。
3. 给 Switch cell 添加按压反馈。
4. 统一 ACK 胶囊样式为公共组件。
5. 复查所有 Compose Preview，确保悬浮窗、主控制页、设置页都能预览。

## 9. 验收标准

- Switch 切换时 thumb 连续滑动，没有方块闪烁。
- 切换动画时间约 150-220ms，不能拖泥带水。
- 离线时开关和滑条不可操作。
- 悬浮窗默认全透明，设置页能实时调整背景透明度。
- ACK 提示只显示客户能理解的文案。
- 小屏、横屏下悬浮窗不出现大面积空白或遮挡严重问题。
