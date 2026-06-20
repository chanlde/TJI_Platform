# FC100_FireDrop MCU 职责

## 状态

- 状态：draft
- 产品代码：`droppersixstage`

## MCU 必须负责

- 上报设备 lifecycle 和周期 state。
- 执行 1-6 段独立抛投控制。
- 执行全部抛投和全部复位控制。
- 对每条控制命令返回 ACK。
- 上报电量、载荷检测、通道开闭状态。

## 安全约束

- 是否需要保险或二次确认：待定。
- 抛投后是否允许 App 复位为待命：待定。
- 载荷检测异常时是否禁止抛投：待定。
- 断线重连后是否需要主动补发 retained state：待定。

## 联调清单

- App 发送单段开关命令后，MCU 能返回对应 stage 的 ACK。
- App 发送全部开关命令后，MCU 能返回整体 ACK 并刷新 state。
- MCU 上报异常 stage 时，App 不崩溃并显示可理解状态。
- 设备离线后，App 不继续发送控制命令。
