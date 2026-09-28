# App 更新模拟器验证证据

## 状态

- 状态：active
- 验证日期：2026-08-11
- 环境：Android 16、arm64 Google APIs 模拟器，只读启动且不保存快照

## 验证范围

本验证只覆盖 Android App 包身份解析、正式签名覆盖升级和错签名拒绝，不连接生产账号、MQTT Broker 或嵌入式设备，也不替代 MCU OTA 真机验证。

## 结果

1. `DownloadedReleaseApkIdentityTest` 在模拟器运行通过。Android `PackageManager` 能从已安装 APK 读取 `com.tji.device`、versionCode、完整 APK SHA256 和 signer 摘要。
2. 安装正式 `TJI_Platform_mapRelease_V2.0.13_213.apk` 成功，系统报告 versionCode 213、versionName V2.0.13。
3. 临时构建相同正式 key 签名的 V2.0.14/214，使用覆盖安装成功，系统随后报告 versionCode 214。
4. 临时构建 versionCode 215 的 debug 签名 APK 并尝试覆盖正式 214，Android 返回 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，明确说明新旧签名不匹配；已安装版本保持 214。
5. 测试结束后关闭只读模拟器，并重新构建正式 V2.0.13/213；map/noMap Release 与 map Debug 的最终 output metadata 均恢复为 versionCode 213、versionName V2.0.13。

## 仍未覆盖

- 正式服务器 TJI Platform 更新记录尚不存在，因此未执行真实 HTTPS 候选下载；
- “安装未知应用”授权页面和用户点击系统安装器的视觉流程仍需发布候选包联调；
- 下载中断、网络切换和进程被杀恢复需在正式更新源可用后验证；
- MCU 固件正常升级、损坏包、空间不足、重启和断电回滚必须使用对应真实设备。
