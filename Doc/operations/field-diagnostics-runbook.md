# 现场诊断与故障处理

## 状态

- 状态：active
- 生效日期：2026-08-12
- 适用范围：TJI Platform Android App

## 最少诊断指标

App 本地保留不超过 256 KiB 的 JSONL 事件，只记录：App 版本、versionCode、flavor、build type、
手机型号/API、启动、登录成功/失败、MQTT 连接结果、OTA 查询/启动结果和未捕获异常。

- 账号、密码、token 不记录；常见敏感字段会再次脱敏。
- 设备序列号只记录 SHA-256 前 12 位引用，不能从诊断包还原原值。
- 日志只保存在 App 私有目录；用户在“设置 → 导出诊断包”主动分享后才离开设备。
- 文件超过上限时保留最近一半记录，避免长期运行持续占用存储。

## 现场提取

1. 打开 App 设置，点击“导出诊断包”。
2. 保存 `tji-diagnostics-*.jsonl`，同时记录问题发生时间、App 版本、手机型号和设备固件版本。
3. 若 App 无法打开，通过受控调试电脑执行 `adb bugreport`；不得要求客户发送账号、密码或 relay token。
4. Release 崩溃必须用同一 commit 产出的 `mapping.txt` 和 `native-debug-symbols.zip` 还原。

## 故障分工

| 故障 | 第一责任角色 | 需要的证据 | 回退方式 |
| --- | --- | --- | --- |
| App 崩溃/ANR/页面卡死 | Android App 负责人 | 诊断包、版本、手机/API、复现步骤 | 安装上一正式签名版本 |
| 登录/API/App 更新 | 服务端负责人 + Android App 负责人 | 诊断包、HTTP 时间、环境 | 停发更新；回退上一 App |
| MQTT 连接/状态/控制 | 服务端 MQTT 负责人 + 产品负责人 | 诊断包、产品、脱敏设备引用、Broker 时间 | 停止操作；回退上一 App/服务配置 |
| MCU OTA | MCU 固件负责人 + 产品负责人 | App/固件版本、OTA 状态、包 SHA256 | 停发固件；按设备回滚流程处理 |
| Speaker 音频 Relay | Relay 负责人 + Speaker 负责人 | App/MCU版本、会话时间、网络 | 停止喊话；回退 Relay 或 App |

具体姓名、电话和值班表属于部署环境配置，不提交到公开源码。正式放行单必须填写当班联系人。

## 产物归档

CI 按源码 SHA 归档 Map/NoMap 的 `mapping.txt` 与 `native-debug-symbols.zip`。正式签名脚本另生成包含
APK SHA256、签名证书摘要、版本和 flavor 的 release manifest。任何现场包都必须能反查源码 commit、
签名与符号文件；缺少其中任一项不得标记正式放行。
