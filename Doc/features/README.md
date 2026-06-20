# 功能方案文档

这里放具体功能、交互和 UI/UX 方案。平台级边界放到 `../architecture/`，产品线私有协议放到 `../product-lines/`。

## 当前文档

| 主题 | 文档 |
|------|------|
| 设备绑定流程 | [device-binding.md](device-binding.md) |
| App UI/UX 优化 | [app-ui-ux-optimization.md](app-ui-ux-optimization.md) |
| 悬浮窗 UI 优化 | [floating-window-ui-optimization.md](floating-window-ui-optimization.md) |
| 喊话器 Payload UI 重做 | [speaker-payload-ui-redesign.md](speaker-payload-ui-redesign.md) |
| 喊话器 C++ Core 与 Qt 上位机 | [speaker-cpp-core-qt-plan.md](speaker-cpp-core-qt-plan.md) |
| OTA 专项 | [ota/](ota/README.md) |

## 写作规则

- 功能方案要写清楚目标、当前状态、代码落点和验收标准。
- 如果方案已经落地，保留“已完成”项，后续再决定是否归档。
- 不在客户界面暴露协议、topic、payload 或内部调试指标。
