# OTA 第一版职责与协议

本文档用于整理第一版 OTA 的最小可落地流程，重点说明：

- App 需要做什么
- 服务器需要做什么
- 单片机需要做什么
- 各端之间用什么协议
- MQTT topic 和消息格式怎么设计

第一版先不要做复杂推送、灰度、强制升级、批量任务。  
先保证 App 能发现新版本，用户能手动触发升级，单片机能下载并完成升级。

## 1. 总体分工

```text
服务器：保存最新固件信息，提供固件下载地址
App：读取设备版本，查询服务器版本，对比后提示用户升级
单片机：上报当前版本，接收升级命令，下载固件，校验并升级
```

简单理解：

```text
App 判断有没有新版本。
服务器告诉 App 最新版本是多少。
单片机负责真正升级。
```

## 2. 协议选择

第一版建议使用：

```text
App <-> 服务器：HTTP / HTTPS
App <-> 单片机：MQTT
单片机 <-> 服务器：HTTP / HTTPS 下载固件文件
```

也就是：

```text
App 用 HTTP 接口查询服务器最新固件版本。
App 用 MQTT 获取设备当前版本、下发升级命令。
单片机用 HTTP / HTTPS 从服务器下载 bin 固件。
单片机用 MQTT 上报升级进度和结果。
```

## 3. App 需要做什么

App 进入光伏清洗设备界面后，执行以下流程：

```text
1. 订阅设备信息 topic，获取单片机当前版本
2. 主动发送 GET_DEVICE_INFO，让设备重新上报一次当前信息
3. 请求服务器 OTA 接口，获取服务器最新版本
4. 对比服务器版本和单片机版本
5. 如果服务器版本更高，显示“发现新版本”
6. 用户点击升级
7. App 下发 START_OTA 给单片机
8. App 订阅 OTA 状态 topic，显示升级进度
9. App 显示升级成功或失败
```

App 的版本判断逻辑：

```text
if server.innerVersion > device.inner_version:
    显示可升级
else:
    显示已是最新版本
```

App 不负责下载固件，也不负责刷固件。  
App 只负责提示、确认和下发升级命令。

## 4. 服务器需要做什么

服务器第一版只需要管理固件信息，并提供一个查询最新版本的接口。

服务器需要保存的固件信息：

```json
{
  "version": 2,
  "hardware_version": "HW-A",
  "file_size": 123456,
  "sha256": "xxxxx",
  "download_url": "https://example.com/firmware/HW-A/v2.bin",
  "release_note": "修复清洗逻辑问题",
  "enable": true
}
```

### 4.1 查询最新版本接口

App 请求：

```http
GET /api/data/appversion/getAppVersion?productId={productId}&type={type}
```

`type` 用来区分升级包类型：

| type | 含义 | productId |
| --- | --- | --- |
| 1 | App 更新包 | 1=水枪控制，2=水桶控制 |
| 2 | 设备固件更新包 | 3=光伏清洗，4=消防吊桶 |

光伏清洗固件查询固定使用：

```http
GET /api/data/appversion/getAppVersion?productId=3&type=2
```

服务器返回：

```json
{
  "id": 8,
  "version": "4",
  "productName": "光伏清洗",
  "techDesc": "光伏清洗bin",
  "innerVersion": "6",
  "publishDate": "2026-05-29 00:00:00",
  "path": "/download/SolarClean_APP_V0.0.1_0X08020000.bin",
  "fileSize": 245760,
  "sha256": "xxxxxxxx",
  "type": 2
}
```

App 使用设备上报的内部版本 `inner_version` 与服务器 `innerVersion` 在本地比较是否有更新。`firmware_version` 和服务器 `version` 主要用于用户展示，不作为第一判断依据。服务器当前返回 `path`，App 下发 `START_OTA` 前会把相对路径补成完整下载地址。

服务器必须返回 `fileSize` 和 `sha256`。App 会把 `fileSize` 转成 MQTT 的 `file_size` 下发给单片机，单片机必须校验文件大小和 sha256。

## 5. 单片机需要做什么

单片机需要做三件事：

```text
1. 上报设备信息
2. 接收 App 查询和升级命令
3. 执行 OTA 并上报进度
```

### 5.1 上电自动上报设备信息

单片机上电联网后，自动发布设备信息。

MQTT topic：

```text
SolarClean/devices/{sn}/status
```

示例：

```text
SolarClean/devices/PV-CLEAN-0001/status
```

消息内容：

```json
{
  "v": 1,
  "type": "deviceInfo",
  "ts": 8481,
  "hardware_version": "HW-A",
  "firmware_version": "0.0.1.0",
  "inner_version": 3
}
```

这个 topic 建议设置 retain：

```text
retain: true
```

原因：

```text
App 进入页面后订阅 status，可以立刻拿到设备最后一次上报的信息。
```

### 5.2 支持 App 主动查询设备信息

App 下发查询命令。

MQTT topic：

```text
SolarClean/devices/{sn}/control
```

示例：

```text
SolarClean/devices/PV-CLEAN-0001/control
```

消息内容：

```json
{
  "v": 1,
  "msgId": "device-info-1777617643409",
  "ts": 1777617643410,
  "cmd": 1,
  "cmdName": "GET_DEVICE_INFO"
}
```

`cmd` 是正式命令码，单片机按数字枚举处理；`cmdName` 只用于 App、MQTTX、日志调试，不作为固件判断依据。

光伏清洗 App 下发命令码表：

| cmd | cmdName | 参数 | 说明 |
|---:|---|---|---|
| 0 | PING | 无 | 诊断 |
| 1 | GET_DEVICE_INFO | 无 | 查询设备硬件/固件信息 |
| 2 | SET_PUMP | `on` | 水泵开关 |
| 3 | SET_PUMP_PRESSURE | `percent` | 水泵压力，0-100 |
| 4 | SET_SPRAY_ANGLE | `amplitudeDeg` | 喷洒单边摆幅角，0-40 |
| 5 | SET_SWING_SPEED | `speedPercent` | 摆动速度，0-100 |
| 6 | SET_SERVO_SWING | `on`，可选 `speedPercent`、`amplitude` | 摆动开关 |
| 20 | START_OTA | 必填 `target_version`、`target_inner_version`、`download_url`、`file_size`、`sha256`；可选 `hardware_version`、`signature` | 开始 OTA |
| 30 | ROUTE_LIST | 无 | 查询航线槽位，当前 App 暂不使用 |
| 31 | ROUTE_DELETE | `slot` | 删除航线槽位，当前 App 暂不使用 |
| 32 | ROUTE_DOWNLOAD | `slot`、`url`、`size`，可选 `checksum` | 下载航线，当前 App 暂不使用 |
| 33 | ROUTE_DOWNLOAD_CANCEL | 可选 `slot` | 取消航线下载，当前 App 暂不使用 |
| 34 | EXECUTE_SLOT | `slot` | 执行航线槽位，当前 App 暂不使用 |

单片机收到后，重新发布一次设备信息：

```json
{
  "v": 1,
  "type": "deviceInfo",
  "ts": 8481,
  "hardware_version": "HW-A",
  "firmware_version": "0.0.1.0",
  "inner_version": 3
}
```

发布 topic 仍然是：

```text
SolarClean/devices/PV-CLEAN-0001/status
```

该消息也可以继续 retain，用新消息覆盖旧的 retained 消息。

### 5.3 接收升级命令

用户在 App 点击升级后，App 向单片机下发升级命令。

MQTT topic：

```text
SolarClean/devices/{sn}/control
```

消息内容：

```json
{
  "v": 1,
  "msgId": "ota-1777617643409",
  "ts": 1777617643410,
  "cmd": 20,
  "cmdName": "START_OTA",
  "target_version": "4",
  "target_inner_version": 6,
  "file_size": 245760,
  "sha256": "xxxxxxxx",
  "download_url": "https://api.tjinnovations.cloud/download/SolarClean_APP_V0.0.1_0X08020000.bin"
}
```

`file_size` 来自服务器 `fileSize`。`sha256` 来自服务器同名字段。App 不再允许缺少这两个字段时启动 OTA。

cmd topic 不要设置 retain：

```text
retain: false
```

原因：

```text
命令不能被保留，否则设备重连后可能重复执行旧命令。
```

### 5.4 执行 OTA

单片机收到 START_OTA 后，执行：

```text
1. 根据 download_url 下载固件
2. 写入备用分区
3. 校验 file_size
4. 校验 sha256
5. 校验通过后准备重启
6. 重启进入新固件
7. 新固件启动成功后，上报新的 firmware_version 和 inner_version
```

升级前的设备状态检查属于单片机内部必做逻辑，不放在本文档展开。

## 6. MQTT Topic 设计

App 已经按统一多产品 MQTT 结构实现，OTA 第一版不再使用旧的 `device/{sn}/...` topic。  
光伏清洗 OTA 统一复用 SolarClean 产品线三段 topic：

| 用途 | Topic | 方向 | Retain |
|---|---|---|---|
| 设备信息上报 | `SolarClean/devices/{sn}/status` | 单片机 -> App | true |
| App 下发命令 | `SolarClean/devices/{sn}/control` | App -> 单片机 | false |
| OTA 状态上报 | `SolarClean/devices/{sn}/status` | 单片机 -> App | false |

建议：

```text
设备信息可以 retain。
control 命令绝对不要 retain。
OTA 状态第一版可以不用 retain。
App 仍然订阅 lifecycle/status，命令只发布到 control。
```

## 7. OTA 状态上报

单片机升级过程中，通过以下 topic 上报状态：

```text
SolarClean/devices/{sn}/status
```

示例：

```text
SolarClean/devices/PV-CLEAN-0001/status
```

### 7.1 开始升级

```json
{
  "status": "STARTED",
  "target_version": 2
}
```

### 7.2 下载中

```json
{
  "status": "DOWNLOADING",
  "progress": 35,
  "target_version": 2
}
```

### 7.3 校验中

```json
{
  "status": "VERIFYING",
  "progress": 100,
  "target_version": 2
}
```

### 7.4 准备重启

```json
{
  "status": "REBOOTING",
  "target_version": 2
}
```

### 7.5 升级成功

```json
{
  "status": "SUCCESS",
  "firmware_version": 2
}
```

升级成功后，单片机还应该重新发布一次设备信息：

```json
{
  "hardware_version": "HW-A",
  "firmware_version": 2
}
```

发布到：

```text
SolarClean/devices/PV-CLEAN-0001/status
```

### 7.6 升级失败

```json
{
  "status": "FAILED",
  "reason": "DOWNLOAD_FAILED"
}
```

第一版失败原因可以先定义这些：

| reason | 含义 |
|---|---|
| `DOWNLOAD_FAILED` | 固件下载失败 |
| `FILE_SIZE_ERROR` | 文件大小不一致 |
| `SHA256_ERROR` | Hash 校验失败 |
| `FLASH_WRITE_ERROR` | Flash 写入失败 |
| `REBOOT_FAILED` | 重启或切换失败 |
| `UNKNOWN_ERROR` | 未知错误 |

## 8. 完整时序

### 8.1 App 进入设备页面

```text
1. 单片机上电后发布 retained info
2. App 进入设备页面
3. App 订阅 SolarClean/devices/{sn}/status
4. App 立刻收到 retained info
5. App 发送 GET_DEVICE_INFO
6. 单片机重新上报 info
7. App 请求服务器 /api/data/appversion/getAppVersion?productId=3&type=2
8. App 对比版本
9. App 显示是否有更新
```

### 8.2 用户点击升级

```text
1. 用户点击升级
2. App 发布 START_OTA 到 SolarClean/devices/{sn}/control
3. 单片机开始下载固件
4. 单片机上报 DOWNLOADING 进度
5. 下载完成后单片机上报 VERIFYING
6. 校验通过后单片机上报 REBOOTING
7. 单片机重启
8. 新固件启动成功
9. 单片机上报 SUCCESS
10. 单片机重新发布 info，firmware_version 变成新版本
```

## 9. 第一版最小开发清单

### 9.1 App

- 订阅 `SolarClean/devices/{sn}/status`
- 发送 `cmd=1` / `cmdName=GET_DEVICE_INFO`
- 请求服务器 `/api/data/appversion/getAppVersion?productId=3&type=2`
- 对比服务器 `innerVersion` 和设备 `inner_version`
- 显示“已是最新版本”或“发现新版本”
- 发送 `cmd=20` / `cmdName=START_OTA`
- 订阅 `SolarClean/devices/{sn}/status`
- 显示升级进度、成功、失败

### 9.2 服务器

- 保存固件版本信息
- 保存硬件版本（建议）
- 保存固件大小（建议）
- 保存 SHA256（建议）
- 保存下载地址
- 提供 `/api/data/appversion/getAppVersion` 接口
- 使用 `type=1` 区分 App 更新，`type=2` 区分设备固件更新
- 光伏清洗固件使用 `productId=3&type=2`
- 消防吊桶固件使用 `productId=4&type=2`
- 提供固件 bin 文件下载

### 9.3 单片机

- 上电发布设备信息到 `SolarClean/devices/{sn}/status`
- `info` 设置 retain
- 接收 `cmd=1`，按 `GET_DEVICE_INFO` 处理
- 接收 `cmd=20`，按 `START_OTA` 处理
- 根据 `download_url` 下载固件
- 校验 `file_size`
- 校验 `sha256`
- 上报 `ota/status`
- 升级成功后重新上报 `firmware_version` 和 `inner_version`

## 10. 关键注意事项

```text
info 可以 retain。
cmd 绝对不要 retain。
App 可以做版本号对比。
单片机负责真正升级。
服务器第一版只要返回最新固件信息即可。
```

最小闭环：

```text
单片机上报当前版本 -> App 查询服务器最新版本 -> App 对比 -> 用户确认 -> App 下发升级命令 -> 单片机下载并升级 -> 单片机上报结果
```
