# App 更新 MI 8 真机验证证据

## 状态

- 状态：active
- 验证日期：2026-08-12
- 范围：Project 08 Android App 同签名覆盖升级、数据保留、冷启动和错签名拒绝

## 设备与升级前状态

- 设备：Xiaomi MI 8，Android 10 / API 29；
- 包名：`com.tji.device`；
- 升级前版本：V2.0.12 / versionCode 212；
- 升级前 signer SHA256：`f0939877b50545161e9381b6ad3ad3bf2331f8e5bcbf74a298818bf7d7155141`，证书 DN 显示为 Android Debug；
- 当前安装 APK 没有 AMap 组件，选择相同 noMap flavor 做覆盖升级；
- 升级前 `userId=10234`、`firstInstallTime=2026-08-03 17:06:26`，应用私有目录有 2 个文件、占用 64 KiB。

手机原安装包不是桌面新建的 Release Key 签名。直接换装 Release 包会被 Android 拒绝，卸载后再装会清除业务数据，因此本次没有卸载或清数据。

## 同签名覆盖升级结果

使用本机与手机现有 signer 完全一致的 `noMapDebug` V2.0.13 / 213 执行 `adb install -r`：

- 安装返回 `Success`；
- 升级后 versionCode 为 213、versionName 为 V2.0.13；
- `userId` 和首次安装时间未改变；
- 应用私有目录仍为 2 个文件、64 KiB；
- 从手机重新拉取的安装 APK 与本机构建文件 SHA256 均为 `6952b7ca18ffeb578703276c6fa30f3b9da6129cc44f651edbbefd485165444f`；
- 手机安装包 signer 仍为原 Debug signer，没有发生证书替换；
- `MainActivity` 冷启动返回 `Status: ok`，耗时 3891 ms，进程正常存在；最近日志没有该进程的 FATAL EXCEPTION 或 ANR；
- 登录页正常显示，“记住我”和原有登录输入仍在。为避免触发真实账号、MQTT 或设备控制，本次未自动点击登录。

## 安装失败保护结果

随后尝试用桌面 Release Key 签名的 mapRelease 213 覆盖当前 Debug 213。Android 返回：

```text
INSTALL_FAILED_UPDATE_INCOMPATIBLE: Package com.tji.device signatures do not match previously installed version
```

拒绝后仍为 V2.0.13 / 213，`userId`、首次安装时间、私有目录文件数与大小均不变，App 进程继续存在。该结果证明系统能够阻止错签名覆盖，也证明量产设备必须从第一次安装起就使用同一 Release Key；不能把已安装 Debug 包无损迁移为 Release signer。

继续执行两项不卸载、不清数据的失败保护：

- 将本机 213 APK 截断为 4 MiB 后尝试覆盖，Android 返回 `INSTALL_PARSE_FAILED_NOT_APK`；
- 使用升级前提取、同一 Debug signer 的 212 APK 尝试覆盖 213，Android 返回 `INSTALL_FAILED_VERSION_DOWNGRADE`。

两次拒绝后手机仍为 213，`userId`、首次安装时间、2 个私有文件和 64 KiB 占用均未改变，App 进程正常存在。

## 整机重启恢复结果

经用户确认后通过 ADB 执行一次整机重启。Android 再次报告 `sys.boot_completed=1` 后：

- ADB 重新连接到同一 Xiaomi MI 8；
- App 仍为 V2.0.13 / 213，`userId=10234`，首次安装时间和最后更新时间均未改变；
- 私有目录仍为 2 个文件、64 KiB；
- App 没有在开机后自行常驻，符合当前前台型控制 App 设计；
- 手动启动 `MainActivity` 返回 `Status: ok`、`LaunchState: COLD`，启动耗时 1023 ms；
- 启动后进程正常存在，最近日志没有该进程的 FATAL EXCEPTION 或 ANR；
- 登录页正常显示，原登录输入和“记住我”状态仍保留。

## 验证边界

- 本证据证明测试手机上的 Debug 同签名 212→213 正常覆盖升级，不把它冒充为 Release Key 的真机迁移；Release 同签名升级已另在 Android 16 模拟器验证。
- 正式服务端仍没有 TJI Platform App 更新记录，因此没有验证 App 内真实 HTTPS 下载、断网、损坏包或安装授权页面。
- 未执行真实账号登录、控制命令或 MCU OTA；这些操作仍需对应负责人按 `ota-device-acceptance-record.md` 执行。
- 验证截图只保存在本机临时目录，因包含登录标识而不归档到仓库。
