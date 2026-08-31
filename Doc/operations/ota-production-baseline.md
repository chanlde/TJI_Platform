# OTA 与 App 更新量产基线

## 状态

- 状态：active
- 更新时间：2026-08-11
- 范围：项目 08 客户端校验、现网接口证据和外部放行条件

## 客户端不变协议

设备信息命令继续使用 `cmd=1 / GET_DEVICE_INFO`。OTA 启动命令继续使用 `cmd=20 / START_OTA`，保留 `params` camelCase 正式字段和顶层 snake_case 兼容字段：目标版本、内部版本、硬件版本、下载 URL、文件大小、SHA256 和可选签名。发布仍使用 QoS 1、`queueWhenDisconnected=false`。

对应 golden test 是 `ProductOtaCommandPayloadTest`。字段名、层级和语义未经 MCU 联调确认不得删除或重命名。

## 客户端启动校验

OTA 命令发布前必须同时满足：

- 接口返回 `type=2`，产品名称与当前产品匹配；
- 设备与服务器都有可比较的内部版本，目标版本严格大于当前版本；
- 服务器硬件版本存在时必须与设备一致；缺失时只透传设备已上报的硬件版本；
- 文件大小为 1 字节到 64 MiB，SHA256 是 64 位十六进制；
- 下载 URL 是 `TJI_OTA_BASE_URL` 同域、标准 443 端口的 HTTPS 地址；
- 服务器给出最低电量且设备上报电量时，设备电量必须达标；
- 重复命令按设备 reservation 阻止，发布失败或终态释放；session generation 改变时清空旧 reservation。

当前设备状态没有统一可用空间字段，因此“空间不足”还不能在 App 启动前判断，必须由 MCU 下载/写入失败状态兜底，并作为协议待办。

## Bootloader 能力证据

本机可见的破窗弹、喊话器和消防载荷 Bootloader 源码存在 SHA256 镜像校验、待确认启动和失败回滚逻辑；未找到非对称签名、公钥、硬件型号绑定或安全版本防降级实现。无线电与光伏清洗的量产 Bootloader 版本也没有在本仓库形成可追溯证据。

因此 App 当前不强制 `signature`：服务器给出时原样透传，但不能把可选字符串当作已经建立的信任链。正式强制签名前，必须由各 MCU 负责人提供对应量产 commit、签名算法、公钥烧录方式、签名覆盖字段和断电回滚证据。

## 2026-08-11 现网接口快照

对正式 HTTPS 版本接口使用公开产品 ID 做只读探测：

| productId | 产品 | 结果 |
|---:|---|---|
| 2 | 消防吊桶 | 有相对 HTTPS 下载路径、大小和 SHA256；内部版本是语义字符串，无硬件/签名 |
| 3 | 光伏清洗 | 返回版本，但下载路径、大小和 SHA256 为空 |
| 4 | 无线电检测 | 有路径、大小和 SHA256；内部版本是语义字符串，无硬件/签名 |
| 5 | 六段抛投 | 接口返回没有该产品固件 |
| 6 | 喊话器 | 有路径、大小、SHA256 和数字内部版本；无硬件/签名 |
| 7 | 破窗弹 | 有路径、大小、SHA256 和数字内部版本；无硬件/签名 |

客户端的 flexible-int 解析保证语义字符串不会导致整份响应解析失败，但无法比较的内部版本会 fail closed，不会启动 OTA。

### 现网固件字节验证

运行以下只读命令会流式下载但不保存固件，也不会发布任何 MQTT：

```bash
python3 tools/verify_ota_artifacts.py --product-ids 2,4,6,7
```

2026-08-11 实测四个产品全部通过：消防吊桶 594248 字节、无线电检测 487488 字节、喊话器 216256 字节、破窗弹 540428 字节，实际 SHA256 均与接口元数据完全一致。`test_verify_ota_artifacts.py` 另覆盖错 hash、跨域地址、缺元数据和超出声明大小的拒绝路径。

## App 更新放行条件

旧逻辑查询 `productId=2&type=1`，服务器实际返回“水桶控制”APK；固定的 TJI Platform HTTP 地址也不可作为正式来源。现在只有以下元数据全部满足时才显示强制更新：

2026-08-11 再次只读查询 `type=1` 的 productId 1～20：ID 1 返回“水枪控制”，ID 2 返回“水桶控制”，ID 3～20 均错误回退到“水枪控制”；没有任何记录返回 TJI Platform、`packageName`、`fileSize`、`sha256` 或 `signerSha256`。因此不能通过扩大 ID 猜测范围启用更新，必须由服务端提供明确且未知 ID 不回退的独立记录。

- 产品名明确为 TJI Platform，`type=1`；
- `packageName=com.tji.device`；
- `innerVersion` 严格大于本地 versionCode；
- HTTPS 下载路径与正式下载域同源且后缀为 `.apk`；
- 文件大小、APK SHA256、签名证书 SHA256 完整；
- signer digest 等于正式 Release 证书 `0cfc0338819ac62f216737e20634c50bf702fdeb748d9a1a5f3713e22fb76567`。

服务器未提供上述字段时客户端不阻塞登录、不打开错误下载地址。`TJI_APP_UPDATE_PRODUCT_ID` 默认是 `-1`，没有正式独立记录时完全跳过错误的旧产品查询。

候选元数据通过后也不会交给浏览器：App 以禁止重定向的 HTTPS 请求下载到私有 cache，限制响应恰好等于声明大小，再计算完整文件 SHA256，并通过 Android `PackageManager` 读取实际包名、versionCode 和 APK signer。五项全部与候选一致后，才以未导出的 `FileProvider` 临时只读 URI 唤起系统安装器；失败文件立即删除。Android 8 及以上未授予“安装未知应用”权限时先进入本 App 的授权页，用户授权后需再次点击更新。

服务端仍需建立 TJI Platform 独立 productId 和正式包记录；正式发布配置显式注入该 productId 后才会启用检查。

### 正式 App 身份与回退规则

以下身份已经由构建配置、桌面 Release Key 和签名产物共同验证，不再作为口头约定：

- 正式下载 base：`https://www.tjinnovations.cloud/`，客户端只接受同 host、标准 443 端口且不重定向的 HTTPS APK；
- Android 包名：`com.tji.device`；
- Release signer SHA256：`0cfc0338819ac62f216737e20634c50bf702fdeb748d9a1a5f3713e22fb76567`；
- map/noMap 是两个 flavor，但包名和 signer 相同；服务端每条记录必须明确实际 APK 的文件大小和 SHA256，不能混发；
- 正式 Key 保存在桌面独立目录，密码不进入 Git、APK、文档或 CI 日志。

Android 默认不允许直接覆盖安装更低 `versionCode`，因此 App 回退不采用降级安装，也不增加自研安装器或额外加密。发现坏版本时先停用服务器更新记录；然后从上一稳定源码构建“应急恢复版”，使用高于坏版本的 `versionCode` 和同一 Release Key 签名，经相同 Release 门禁后发布。若版本包含不可逆数据迁移，发布前必须额外证明恢复版能读取新数据；否则不得宣称可回退。目标设备的实际回退证据记录在 `ota-device-acceptance-record.md`。

## 仍需设备或服务器完成

- 每个启用产品提供量产 Bootloader commit 与能力签字；
- 服务器补硬件版本、最低电量、签名能力标记和 TJI Platform App 身份/完整性字段；
- 真机验证正常升级、断网、损坏包、空间不足、重启恢复和断电回滚；
- App 更新包完成同签名覆盖安装、错签名/错包名/损坏文件真机拒绝测试。
