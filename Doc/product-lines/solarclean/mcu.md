# 光伏清洗 MCU 职责

## 状态

- 状态：active
- 产品代码：`solarclean`

## MCU 必须负责

- 上报 lifecycle。
- 周期上报设备状态。
- 执行水泵、压力、喷洒角度、摆动速度、摆动开关控制。
- 返回控制 ACK。
- 离线、异常、故障码上报。

## OTA 职责

单片机侧需要负责：

- Bootloader。
- A/B 分区。
- 固件下载。
- 固件校验。
- 启动确认。
- 失败回滚。

详细方案见：

```text
Doc/features/ota/solar-clean-ota-plan.md
Doc/features/ota/ota-v1-responsibility-contract.md
```

## 安全约束

- 清洗中、电机运行中是否允许 OTA：以 MCU 安全检查为准。
- 离线设备不可执行 App 控制。
- 控制失败必须返回可诊断 ACK 或状态。
