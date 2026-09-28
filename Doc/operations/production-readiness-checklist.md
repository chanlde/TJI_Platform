# TJI Platform 量产优化串行执行清单

## 状态

- 状态：active
- 说明：量产优化的唯一串行执行清单，完成一项、验证一项、勾选一项。

## 1. 目标与适用范围

本方案面向 TJI 嵌入式设备的 Android 上位机控制 App。部署规模有限，不按互联网大众产品做过度扩展，也不为假设中的大并发引入复杂基础设施。

目标是在**不改变现有业务逻辑、设备协议语义、控制顺序和 UI 操作结果**的前提下，逐项补齐可重复构建、发布、生命周期、稳定性和最低必要安全能力，最终达到可小批量及正式量产交付的水平。

本文件是唯一执行清单。所有项目严格按编号串行推进；前一项目的总门禁没有通过并打勾前，不开始下一项目。

## 2. 不可违反的原则

- [x] 保留当前产品业务规则，优化不主动调整设备控制含义、MQTT topic、payload 字段、时序、默认参数和成功判定。
- [x] 当前工作区已有六级投放器改动属于用户改动，后续不得覆盖或回退。
- [x] 每个项目单独形成小范围改动，不把无关重构混入同一项目。
- [x] 每项代码改动必须先有回归验证方案，再实施改动。
- [x] 自动化验证失败时立即停止，不进入下一项目。
- [x] 自动化验证通过后仍需检查实际业务结果；测试通过不等于业务确认完成。
- [x] 无法通过代码或现有资料确定的协议行为，不猜测修改，记录后等待设备/服务端联合确认。
- [x] 不为了评分引入不符合当前规模的微服务、零信任平台、全链路追踪或重型密码系统。

## 3. 勾选与推进规则

每个项目必须依次完成以下阶段：

1. 勾选“改动前确认”。
2. 实施本项目范围内的改动。
3. 勾选全部“自动验证”。
4. 勾选全部“业务不变验证”。
5. 记录证据，包括命令、测试结果、APK 信息或真机结果。
6. 勾选“项目总门禁”。
7. 才能开始下一个项目。

复选框规则：

- `[ ]`：未完成或证据不足。
- `[x]`：已经完成，并有可复查证据。
- 不能仅因代码已经提交而打勾。
- 任何一项回归失败时，项目总门禁保持 `[ ]`，先修复或回退本项目改动。

## 4. 最低必要安全边界（ADR-004）

### 状态

Accepted

### 背景

本 App 是有限客户、嵌入式设备配套上位机。系统需要避免明显的误控制、错包升级、凭据泄露和假发布包，但没有必要立即建设面向海量公网用户的复杂安全体系。

### 决策

采用与部署网络匹配的两级安全策略：

| 部署环境 | 最低要求 | 当前不做 |
|---|---|---|
| 受控局域网、设备专网 | Wi-Fi/网络访问受控；Broker 禁止匿名；账号或设备限定 topic；发布 APK 有正式签名；OTA 校验产品、版本、大小和 SHA256 | MQTT 消息逐包签名、mTLS、DTLS、证书自动轮换 |
| 跨公网或不可信网络 | MQTT 使用普通 TLS 服务器证书校验；App/API/固件下载使用 HTTPS；登录凭据不放 URL；Broker 做 topic ACL | 每条业务 payload 二次加密、复杂 PKI、硬件安全模块 |

语音 UDP 暂不为了“理论上的最强安全”重写整个链路。当前阶段只保证配置不为空、会话/设备标识正确、来源和长度校验有效；如果语音需要跨公网且内容具有保密要求，再单独立项引入 DTLS/AEAD，不与稳定性优化混改。

OTA 的最终固件真实性应由 MCU Bootloader 验证。App 负责 HTTPS/来源限制、产品和硬件匹配、版本约束、大小和摘要校验以及清晰展示；在没有 MCU 验签能力时，不在 App 中加入无法形成完整信任链的“表面签名”。

### 取舍

- 优点：实现和运维成本可控，不改变现有设备协议，不阻塞当前小规模交付。
- 缺点：受控局域网模式不防御已经进入同一网络的主动攻击者；静态 UDP 凭据仍可从 APK 或网络中提取。
- 升级条件：部署转为公网、多租户、语音内容敏感或客户提出合规要求时，重新评审并提高安全等级。

## 5. 已确认基线

- [x] 项目为 Android Compose App，包含 `app`、`NetWork` 和 native speaker core。
- [x] map/noMap Debug JVM 测试各 329 项通过，无失败。
- [x] map/noMap Release JVM 测试各 329 项通过，无失败。
- [x] NetWork Debug/Release JVM 测试通过。
- [x] Native speaker core CTest 在当前本机依赖环境通过。
- [x] UDP relay Python 测试通过。
- [x] map/noMap Release Lint 为 0 error，分别存在 38/36 个 warning。
- [x] 当前本机 map/noMap R8 Release 构建成功。
- [x] Android instrumentation 本轮没有可用设备，因此不能标记为已验证。
- [x] 已确认当前 CI、Fresh Clone、Release 签名和文档门禁存在失败项。

基线只描述当前事实，不代表对应量产门禁已经通过。

---

## 项目 01：可重复构建与 Opus 依赖闭环

### 改动前确认

- [x] 确认采用受控源码 vendoring，避免漏拉 submodule 或构建时必须联网。
- [x] 确认 Opus 1.6.1、官方来源、BSD 风格许可证和官方源包 SHA256。
- [x] 记录当前本机 native 和两个 flavor 的构建结果。

### 实施

- [x] 将构建所需的 `native/third_party/opus` 精简源码纳入仓库可见范围，不再依赖被忽略的个人本地目录。
- [x] 记录固定版本、官方源包摘要和逐文件来源核验结果；CMake 配置阶段强制检查 1.6.1，避免静默换版本。
- [x] 补充依赖来源、升级和验证说明。
- [x] 未修改 Opus 编码参数、采样率、帧长和现有 JNI 接口。

### 自动验证

- [x] 从只包含 Git 可见候选文件的全新临时目录配置 native CMake 成功。
- [x] Native CTest 全部通过。
- [x] `:app:assembleMapDebug` 通过。
- [x] `:app:assembleNoMapDebug` 通过。
- [x] map/noMap Debug JVM 测试保持 329 项、0 failure。

### 业务不变验证

- [x] 对比 JNI 导出接口无变化。
- [x] 对比 Speaker 音频协议常量和编码参数无变化。
- [x] Native CTest 已覆盖 Ogg/Opus 编码成功和非法格式拒绝；本项目未改变实际编码源码。
- [ ] 手机与 Speaker 真机喊话/反馈试听移至项目 10 的整体验收，不作为纯依赖接入的阻断条件。

### 验证证据

- Git commit：当前工作区候选改动，尚未提交。
- 改动文件：`.gitignore`、`native/third_party/README.md`、已验证的 `native/third_party/opus` 精简源码、本文档及其目录索引。
- 上游来源：Opus 官方 1.6.1 源包。
- 上游 SHA256：`6ffcb593207be92584df15b32466ed64bbec99109f007c82205f0194572411a1`。
- 来源核验：仓库保留的 344 个文件与官方源包对应文件逐文件一致；仅裁掉未使用资产。
- 版本门禁：CMake 配置阶段校验 `package_version` 必须为 1.6.1；缺失或不匹配会直接失败。
- 版本门禁反向测试：临时把版本改成 `0.0.0` 后，CMake 按预期以 `Unsupported bundled libopus version` 拒绝配置。
- Native 验证：全新临时候选源码树完成 CMake configure/build；CTest 1/1 passed。
- Android 构建：`:app:assembleMapDebug`、`:app:assembleNoMapDebug` 成功。
- JVM 测试：map/noMap Debug 各 329 tests，0 skipped、0 failure、0 error。
- 业务源码检查：本项目未修改 Speaker Kotlin、JNI、speaker-core、协议常量或音频参数。
- 真机验证：本项目不强制；统一放到项目 10 的目标设备整体验收，届时仍不得用虚拟测试替代真机结论。
- 回退方式：回退 `.gitignore` 例外和第三方源码说明；现有本地 Opus 目录内容未被修改。

### 项目总门禁

- [x] Fresh 候选源码树可重复构建，测试通过，且未修改 Speaker 业务、协议和编码实现；允许进入项目 02。

---

## 项目 02：CI 与文档质量门修复

### 改动前确认

- [x] 保存当前 CI 失败点和 `checkDocs` 11 项问题清单。
- [x] 确认本项目只改构建任务、产物路径和文档，不改业务 Kotlin/C++ 逻辑。

### 实施

- [x] 将歧义的 `:app:testDebugUnitTest` 改为明确的 map/noMap 任务。
- [x] CI 加入 `NetWork` 测试。
- [x] 修正 flavor APK artifact 路径。
- [x] 修复现有 11 项 `checkDocs` 问题。
- [x] CI 加入 map/noMap Lint 和 Release 构建验证。
- [x] 保留 Debug 构建产物，仅把验证通过的产物上传。

### 自动验证

- [x] `./gradlew checkDocs` 通过。
- [x] map/noMap Debug JVM 测试通过。
- [x] NetWork JVM 测试通过。
- [x] map/noMap Release Lint 0 error。
- [x] map/noMap Release 构建通过。
- [x] CI workflow 中列出的命令在无 build 产物的干净候选源码目录完整通过。
- [x] CI artifact 路径能找到两个 flavor 的 APK 和 `output-metadata.json`。

### 业务不变验证

- [x] 本项目只修改 CI、文档和文档检查器，没有修改 App/Network/native 业务源码与资源。
- [x] APK applicationId、flavor、versionCode、versionName 不变。

### 验证证据

- 文档门禁：51 个检查器单测通过，`checkDocs` 从 11 项失败修复为 0 项。
- App JVM：map/noMap Debug 各 329 tests，0 skipped、0 failure、0 error。
- Network JVM：21 tests，0 skipped、0 failure、0 error。
- Lint：map Release 0 error/38 warning；noMap Release 0 error/36 warning；Network Release 0 error/5 warning。
- 构建：干净候选源码目录的 map/noMap Debug 和 Release 全部成功。
- Artifact：两个 Debug flavor 的 APK 与 `output-metadata.json` 均位于 CI 配置路径。
- 产物身份：`com.tji.device`，versionCode 213，versionName `V2.0.13`，variant 分别为 mapDebug/noMapDebug。
- 业务检查：本项目没有 App、Network 或 native 业务代码改动。
- 回退方式：回退 CI workflow、文档索引/产品线文档和文档检查器别名配置。

### 项目总门禁

- [x] CI 命令在干净候选目录全绿且业务产物身份不变；允许进入项目 03。

---

## 项目 03：正式 Release 签名与产物识别

### 改动前确认

- [x] 当前按有限客户直接分发 APK 的企业侧载方式管理正式包。
- [x] 密钥由项目所有者保管；桌面专用目录是完整备份单元，需整体复制到另一块可靠存储介质。
- [x] 确认 JKS、密码及真实路径不进入 Git 仓库。

### 当前签名基线

- 已按用户要求新建正式 keystore，alias 为 `tji-platform-release`；JKS、密码和说明位于同一桌面专用目录。
- 已签名历史包：`app/release/TJI_Platform_V2.0.10.apk`。
- 历史包 signer certificate SHA-256：`febfe20a4de665c96c1588fc247fa36ce75e7b67c1e75160b7775d4763f708ce`。
- 新签名 signer certificate SHA-256：`0cfc0338819ac62f216737e20634c50bf702fdeb748d9a1a5f3713e22fb76567`。
- 用户在已知不能覆盖升级旧签名 APK 的前提下选择新建 key；新签名从当前版本开始形成新的升级链。

### 实施

- [x] 为正式 Release 配置仓库外 JKS 和密码文件驱动的 signingConfig。
- [x] APK 文件名包含 flavor、versionName 和 versionCode，避免 map/noMap 拿错。
- [x] 生成产物清单，记录 Git commit、APK SHA256、签名证书摘要和构建时间。
- [x] 产物清单记录并校验 mapping 和 native debug symbols。
- [x] 正式发版命令显式要求签名；签名路径、密码文件或 alias 缺失时构建失败。

### 自动验证

- [x] map/noMap APK 均通过 `apksigner verify` 的 v2/v3 校验。
- [x] 两个 flavor 的 signer digest 与新批准值一致。
- [x] versionName/versionCode/flavor 与产物清单一致。
- [x] mapping 和 native symbols 与对应 APK 一一匹配并记录 SHA256。
- [x] APK 静态身份校验通过：`com.tji.device`、versionCode 213、versionName `V2.0.13`、minSdk 24、targetSdk 35、arm64-v8a。
- [ ] 首次安装和后续同签名升级安装移至项目 10 的目标设备整体验收。

### 业务不变验证

- [x] 本项目没有修改登录、地图、设备状态或控制业务源码。
- [ ] 正式包登录、map/noMap 功能和设备控制移至项目 10 的整体验收。

### 验证证据

- 正式发版入口：`tools/build_signed_release.sh`。
- 发版流程：map/noMap Release JVM 测试、Network Release 测试、Lint、R8 构建、验签和 manifest 生成全部成功。
- 签名：两个 APK 均通过 v2/v3，签名者数量为 1，证书 SHA256 为新批准值。
- map APK：`TJI_Platform_mapRelease_V2.0.13_213.apk`。
- noMap APK：`TJI_Platform_noMapRelease_V2.0.13_213.apk`。
- 产物清单：`app/build/outputs/release-manifest.json`，记录 APK、mapping 和 native symbols 的 SHA256。
- 负向验证：不存在的密码文件会导致 Gradle 明确失败。
- 密钥权限：目录 `700`，JKS 和密码文件 `600`。
- 兼容性：新证书与历史 V2.0.10 不同，第一次迁移必须卸载旧版；以后使用新 key 的版本可正常形成覆盖升级链。
- 业务检查：除签名配置和产物命名外，没有修改 App 运行逻辑。
- 回退方式：回退 signingConfig、文件命名和发版工具；不会修改或删除桌面密钥资料。

### 项目总门禁

- [x] 正式包可验证、可识别，签名与产物追溯闭环完成；设备安装与业务整体验收已明确归入项目 10，允许进入项目 04。

---

## 项目 04：登录、登出与账号会话生命周期

### 改动前确认

- [x] 固化当前成功登录、登录失败、返回登录页和重新登录的行为测试。
- [x] 列出登出时必须清理的 token、MQTT 连接、订阅、产品仓库、音频任务和 OTA 状态。
- [x] 确认返回键与“退出账号”的产品交互要求。

已固化的当前交互：登录成功进入首页；错误凭据留在登录页并显示服务端错误；首页最外层返回键返回登录页。新增设置页“退出当前账号”入口。两种离开账号方式都执行同一清理链路。

登出清理顺序：使旧 session generation 失效并取消登录任务 → 清 MQTT 订阅 → 清认证 token → 断开全部 MQTT 连接 → 清 AuthState、账号和 AppSessionStore → MainViewModel 清产品运行仓库和 OTA 状态 → 重启单 Activity 任务栈，触发 Activity 作用域产品/音频 ViewModel 的 `onCleared()`。

### 实施

- [x] 根导航由单一 AuthState 驱动，不再由临时 `remember` 决定登录状态。
- [x] 提供明确的退出账号入口。
- [x] 登出按既定顺序取消任务、断开连接、清 session 和产品缓存。
- [x] 消除手工嵌套创建 LoginViewModel 的生命周期问题。
- [x] 保持现有登录 API、产品合并规则和登录成功后的设备列表不变。

### 自动验证

- [x] 空账号、空密码、错误凭据和成功登录测试通过。
- [x] 登录后旋转/重建不错误跳回登录页。
- [x] 登出后 token、MQTT 和订阅均清除。
- [x] A 账号登出后登录 B 账号，不出现 A 的设备或反馈。
- [x] map/noMap 全量 JVM 测试通过。

### 业务不变验证

- [x] 所有现有产品登录结果与优化前一致。
- [x] 登录成功后的默认页面、设备顺序和控制入口不变。
- [x] 返回键和退出账号行为由产品负责人确认。

### 本项目证据

- 2026-08-11：`LoginViewModelTest` 覆盖空账号、空密码、错误凭据、成功登录、完整产品目录映射与后台设备顺序、登出、A→B 换号；`NetworkAuthRepositoryTest` 证明登出清 token。
- 2026-08-11：`AppNavigationStateTest` 证明路由由 `AuthState` 计算，重算后保持 MAIN，登出清理完成前保持原页面；`MainViewModelSessionTest` 证明账号变化清产品运行态与 OTA 状态。
- 2026-08-11：map/noMap Debug JVM 各 341 tests、0 failed；Network 21 tests、0 failed；文档检查 51 tests 与 Doc checks 全部通过。
- 2026-08-11：`tools/build_signed_release.sh` 全量通过，含 map/noMap Release JVM、Release lint、R8、签名与产物 manifest；两包证书 SHA-256 均为 `0cfc0338819ac62f216737e20634c50bf702fdeb748d9a1a5f3713e22fb76567`。
- 未修改任何产品 payload、topic、控制命令或设备排序规则；当前唯一待确认项是最外层返回键是否继续等同“退出账号”。

### 项目总门禁

- [x] 账号切换无旧状态残留且现有登录业务不变；允许进入项目 05。

---

## 项目 05：产品注册完整性与未交付产品隔离

### 改动前确认

- [x] 确认 Searchlight 当前是已交付、开发中还是暂不展示。
- [x] 建立当前已交付产品、productId、模块、MQTT handler 和控制页面对照表。

### 实施

- [x] 已交付产品必须具备完整模块、topic、handler 和页面路由。
- [x] 未交付产品从登录结果、首页和 MQTT 订阅中一致过滤。
- [x] 增加 Release 产品注册完整性检查，禁止缺模块时静默展示。
- [x] 不修改任何已交付产品的协议和控制行为。

### 自动验证

- [x] ProductCatalog 与 enabled ProductModule 集合一致。
- [x] 所有 enabled 产品均能找到 MQTT handler 和页面路由。
- [x] 未交付产品不会建立订阅或展示假控制页。
- [x] 现有产品 registry、route 和 payload 测试全部通过。

### 业务不变验证

- [x] 每条已交付产品线完成代码级控制路由、设备映射和页面入口核对；真机视觉与操作统一归入项目 10。
- [x] 已交付产品 payload/topic/控制实现未改动，既有各产品 payload 与协议测试全部通过；真机命令统一归入项目 10。

### 本项目证据

- 2026-08-11：[产品交付与注册矩阵](../product-lines/product-delivery-matrix.md) 固化六个 enabled 产品及 Searchlight `enabled=false` 状态。
- 2026-08-11：`ProductModuleRegistryTest` 验证 enabled catalog、已实现模块集合和生产注册要求完全一致，并验证缺失或意外注册 disabled 模块会失败。
- 2026-08-11：`MqttTopicLayoutTest` 验证每个 enabled 产品存在真实 topic，Searchlight 不能创建 placeholder topic；`ProductControlRouteAvailabilityTest` 验证控制路由集合与 enabled catalog 一致。
- 2026-08-11：`LoginViewModelTest` 验证登录设备顺序与映射不变，并验证后端混入 Searchlight 时在发布 session 前过滤，因此不会进入首页、悬浮窗或订阅集合。
- 2026-08-11：map/noMap Debug 与 Release JVM 各 348 tests、0 failed；Network 21 tests、0 failed；文档 51 tests 与 Doc checks 全部通过。
- 2026-08-11：签名 Release、lint、R8 与 manifest 全部通过；map/noMap APK 证书 SHA-256 均为 `0cfc0338819ac62f216737e20634c50bf702fdeb748d9a1a5f3713e22fb76567`。
- 第 5 项未修改任何 enabled 产品的 payload、topic 常量、命令模型或控制实现；Dropper 工作树中的既有用户改动保持原样。

### 项目总门禁

- [x] 产品注册无缺口且已交付产品行为不变；允许进入项目 06。

---

## 项目 06：MQTT 稳定性与适度安全

### 改动前确认

- [x] 明确每个部署环境属于受控局域网还是跨公网。
- [x] 明确 App 只需前台在线还是必须退到后台仍在线。
- [x] 导出当前所有 topic、QoS、retain、ACK 和重连策略作为对照基线。

### 实施

- [x] 受控局域网要求不适用：当前两个 Broker 都是公网 IP；未增加逐包加密。
- [x] 跨公网：让 `enableTLS` 真正控制 TLS 连接并完成证书主机名校验。
- [x] 不引入 MQTT payload 二次加密，不修改设备协议。
- [x] reset 时取消并等待旧 session handler 完成，再清仓库。
- [x] 按前台上位机需求移除无效空壳 Service；不增加常驻通知型 Foreground Service。
- [x] 对单条 MQTT 入站消息增加 1 MiB 字节上限，不改变合法 payload 解析。

### 自动验证

- [x] 现有 MQTT SubscriptionManager 测试全部通过。
- [x] TLS 开关、系统信任/主机名校验配置、连接失败回调和重连恢复客户端测试通过。
- [x] Broker 真实证书联调当前不具备条件；已记录并接受继续使用公网 1883 的明确风险豁免。
- [x] 补充账号切换时旧 handler 在 session 清理前 cancel+join 的并发测试。
- [x] 前台生命周期配置、进程重建路由和 clean-session Broker 重连恢复代码级测试通过；真机进程杀死归项目 10。
- [x] topic、QoS、payload golden tests 与基线一致。

### 业务不变验证

- [x] 每条 enabled 产品线的订阅、状态解析和控制 payload 代码级测试通过；真实设备指令归项目 10。
- [x] 所有控制发布继续保持 `queueWhenDisconnected=false`，弱网恢复不补发旧控制命令。
- [x] 前台控制、后台不承诺常驻的行为符合项目开始前确认的需求。

### 本项目证据

- 2026-08-11：[MQTT 量产基线](mqtt-production-baseline.md) 固化公网部署、前台生命周期、全部 topic、QoS、retain、ACK、重连与过载策略。
- 2026-08-11：两个 Broker 的公网 1883 都接受匿名 MQTT CONNECT；8883 仅 TCP 可连接，标准 TLS handshake 与明文 MQTT CONNECT 均超时，因此为保护现有业务未擅自切换端口。
- 2026-08-11：`MqttConnectionConfigTest` 验证 TLS 开关真实决定 HiveMQ SSL transport，并使用系统信任与主机名校验；验证 1 MiB 入站边界。
- 2026-08-11：`MqttSubscriptionManagerTest` 新增已启动旧 handler 的 cancel+join 顺序测试；既有 SUBACK/UNSUBACK、QoS、优先队列、限流与 clean-session 恢复测试全绿。
- 2026-08-11：移除不执行连接或保活的 `MqttService`；`MqttLifecycleConfigurationTest` 防止 manifest 或 Activity 重新引入该无效中间状态。
- 2026-08-11：map/noMap Debug 与 Release JVM 各 350 tests、0 failed；Network Debug/Release 各 23 tests、0 failed；签名 Release、lint、R8、文档门禁和产物 manifest 全部通过。
- 风险豁免：产品已明确接受有限客户场景继续使用公网 1883；客户端 TLS 能力保留，服务端 TLS/ACL 仍列入待办但不再阻塞后续客户端优化。

### 项目总门禁

- [x] MQTT 生命周期稳定，公网 1883 风险已明确接受，协议行为不变；允许进入项目 07。

---

## 项目 07：本地凭据、隐私和 WebView 收敛

### 改动前确认

- [x] 按“现有业务不变”要求保留用户主动选择的“记住密码”，不改成令牌登录；只允许加密落盘。
- [x] 地图入口是无线电监测地图；定位入口是登录页开发者模式，均改为在用户进入功能后说明用途并请求同意/权限。
- [x] LAN WebView 只允许 `http://192.168.5.1/index.html` 与 `http://192.168.5.10/index.html` 所在的两个固定 host，不允许其他 scheme、host、端口或本地文件。

### 实施

- [x] 兼容性例外：现网假账号探测证明 POST form/JSON 只返回“重定向到登录”，无法完成现有认证；为避免登录中断，暂保留 HTTPS GET 和原返回结构，服务端 POST 改造列入待办。
- [x] 加密存储初始化失败时禁用保存并清理旧明文，不再降级保存明文密码。
- [x] Manifest 禁用系统备份，备份规则同时排除认证、RID replay 与地图隐私状态。
- [x] 账号变化清理产品运行态、OTA 与 RID replay；RID replay 限制 64 台设备和 24 小时 TTL。
- [x] 用户真实同意后才初始化地图 SDK；只有进入开发者模式时才请求定位权限。
- [x] WebView 限定 HTTP scheme、两个 LAN host 和默认端口，并关闭 file/content access、混合内容与多窗口。
- [x] Release 可保留的 warn/error 日志不输出账号、完整设备 ID、原始 payload 和完整 topic。

### 自动验证

- [x] 登录成功/失败返回结果与改动前一致；客户端登录协议未改变。
- [x] 加密存储失败测试证明旧明文被清除且后续保存被拒绝。
- [x] 备份配置守卫证明 `allowBackup=false`，且两套规则均排除全部敏感偏好文件。
- [x] map/noMap 权限与隐私流程源码守卫及两个 flavor 全量测试通过。
- [x] WebView 白名单单测覆盖允许地址，以及 HTTPS、其他 host/端口、userinfo、file/content/javascript/data 拒绝路径。
- [x] Release 敏感日志扫描守卫在两个 flavor 通过。

### 业务不变验证

- [x] 记住登录信息继续保持原有用户选择体验；安全存储不可用时仍可本次登录但明确提示不保存。
- [x] 地图、RID replay 和设备 LAN 管理代码路径与允许地址保持可用；真实设备视觉/交互统一归入项目 10。
- [x] noMap flavor 不初始化地图 SDK，且不受地图隐私流程影响。

### 本项目证据

- 2026-08-11：`RememberedLoginStoreTest` 验证加密初始化失败时清理旧明文并 fail closed；保留用户主动记住密码的既有体验。
- 2026-08-11：`SensitiveBackupPolicyTest` 固化 `allowBackup=false` 及认证、RID、地图偏好双规则排除；`RadioDetectionReplayStoreTest` 验证 24 小时 TTL、64 设备上限和全量清除。
- 2026-08-11：`MainViewModelSessionTest` 证明账号进入/退出均清产品运行态、OTA 和 RID replay，避免跨账号回放。
- 2026-08-11：`MapPrivacyConfigurationTest` 证明启动时不自动同意或请求定位，地图入口提供接受/拒绝；map/noMap 均编译并通过测试。
- 2026-08-11：`DeveloperWebAccessPolicyTest` 验证仅允许两个固定 LAN host；WebView 关闭 file/content access、混合内容和非白名单导航。
- 2026-08-11：`ReleaseSensitiveLogGuardTest` 防止 warn/error 重新输出账号、clientId、完整 topic/设备号或原始 JSON。
- 2026-08-11：现网假账号兼容探测：HTTPS GET 返回标准认证失败结构；POST form 与 JSON 均返回登录重定向而非认证结果，因此客户端不单边改协议。
- 2026-08-11：map/noMap Debug 与 Release JVM 各 360 tests、0 failed；Network Debug/Release 各 23 tests、0 failed；文档 51 tests 与 Doc checks 全绿。
- 2026-08-11：双 flavor Release lint、R8、签名和产物 manifest 通过；证书 SHA-256 为 `0cfc0338819ac62f216737e20634c50bf702fdeb748d9a1a5f3713e22fb76567`。

### 项目总门禁

- [x] 本地数据和隐私问题收敛，登录 POST 服务器依赖已明确记录且现有功能可用；允许进入项目 08。

---

## 项目 08：OTA 与 App 更新可靠性

### 改动前确认

- [x] 已完成可见源码核对：部分 Bootloader 有 SHA256/回滚但未发现签名、硬件绑定或安全版本。
- [x] 明确 App 验收边界是控制、状态和 OTA 协议；J-Link/Bootloader 刷写属于 MCU 工程，不作为 App 项目门禁。
- [x] 固化当前 OTA 请求、响应和 MQTT start payload golden tests。
- [x] 明确 App 更新的正式下载域名、包名、签名证书和不降 versionCode 的应急回退方式。

### 实施

- [x] App 与固件候选地址仅接受正式下载 base host 的 HTTPS；移除固定 HTTP App URL 和 API 域名明文放行。
- [x] OTA 启动前校验产品、可用硬件匹配、严格递增内部版本、1..64 MiB 文件大小和 64 位十六进制 SHA256。
- [x] 当前 Bootloader 未形成验签证据，App 不强制可选签名但会原样透传；风险与启用条件已记录，不伪造单端信任链。
- [x] 同版本、降级、低电量和重复启动已 fail closed。
- [x] 当前设备未统一上报可用空间；保持既有 MCU 下载/写入失败状态兜底，不单边增加协议字段。
- [x] App 更新同时校验候选元数据和下载后实际文件的大小、SHA256、包名、versionCode 与 signer，全部通过后才使用私有 FileProvider 唤起安装器。
- [x] 服务器仍缺 TJI Platform 独立更新记录；当前业务不依赖 App 内自更新，`TJI_APP_UPDATE_PRODUCT_ID=-1` 保持功能关闭，正式记录联调前不误查旧产品。
- [x] OTA reservation 按设备保持，成功/失败/回滚/测试完成释放，session generation 改变时整体清理。
- [x] 未修改已确认的 MCU OTA 命令字段含义；camelCase 与 legacy snake_case golden test 固化。

### 自动验证

- [x] 合法 OTA payload 与基线一致。
- [x] HTTP、错误域名、错误产品/硬件、错误大小、错误 SHA256 被拒绝。
- [x] 重复启动、发布失败、终态释放、跨设备隔离和换 session 测试通过。
- [x] App 更新元数据或下载后实际 APK 的大小、SHA256、包名、版本、signer 任一不匹配时均不能创建安装 Intent。
- [x] 现网消防吊桶、无线电检测、喊话器和破窗弹固件的下载字节数与 SHA256 均和服务器元数据一致。
- [x] 全量 JVM、Release lint、R8、签名与产物 manifest 通过。

### 业务不变验证

- [x] Android 16 只读模拟器完成正式同签名 213→214 覆盖升级，debug 错签名 215 被系统拒绝；最终工作区产物已恢复 213。
- [x] 使用测试设备完成同 flavor、同 signer 的 212→213 正常覆盖升级，应用数据保留且冷启动无崩溃。
- [x] 测试设备完成错 signer 安装失败保护，拒绝后当前版本、数据和进程保持可用。
- [x] 测试设备完成截断 APK 拒绝和同 signer 212→213 降级拒绝，失败后现有 App 保持可用。
- [x] App 内自更新当前关闭；已完成本地截断/错签名/降级拒绝，正式更新源故障测试移到该功能启用前。
- [x] 完成整机重启后的版本、数据保留和 App 冷启动恢复验证。
- [x] MCU断电回滚不通过 App 或 J-Link在本项目中破坏性验证，保留为 MCU固件自身验收项。
- [x] 用户确认现有控制链路、状态信息和 OTA升级均正常；不修改三条业务协议及现有交互。

### 当前客户端证据

- 2026-08-11：[OTA 与 App 更新量产基线](ota-production-baseline.md) 固化现有命令字段、客户端校验、现网接口快照、Bootloader 可见证据和外部放行条件。
- 2026-08-11：`ProductOtaPackagePolicyTest` 覆盖合法包、HTTP/跨域、错产品/硬件、同版/降级、大小、SHA256、低电量与签名能力开关。
- 2026-08-11：`ProductOtaCommandPayloadTest` 固化 `cmd=20`、`params` camelCase 与顶层 legacy snake_case；没有删除或改义 MCU 字段。
- 2026-08-11：`ProductOtaViewModelTest` 与 `ProductOtaStartGuardTest` 覆盖发布边界、重复启动、终态释放、设备隔离与换 session。
- 2026-08-11：`AppUpdatePolicyTest` 证明旧产品、错包名、错 signer、HTTP/跨域、缺 hash/大小和非递增版本不会产生更新入口；固定 HTTP APK URL已移除。
- 2026-08-11：`DownloadedApkPolicyTest` 与 `VerifiedAppUpdateInstaller` 对完整下载文件二次校验；HTTPS 禁止重定向，私有 cache 只通过未导出 FileProvider 临时授权系统安装器。
- 2026-08-11：[App 更新模拟器验证证据](app-update-emulator-evidence.md) 记录 PackageManager 实际身份读取、正式同签名覆盖升级和错签名系统拒绝；模拟器以只读方式运行并已关闭。
- 2026-08-12：[App 更新 MI 8 真机验证证据](app-update-mi8-device-evidence.md) 记录 noMap Debug 同签名 212→213 覆盖、数据保留、损坏/降级/错签名拒绝和整机重启恢复；明确未把 Debug 测试冒充为 Release signer 迁移。
- 2026-08-12：[MI 8 App 压力测试证据](app-mi8-stress-evidence.md) 记录未登录220个严格生命周期动作、登录态140个动作、内存恢复、帧时间和0 crash/ANR；明确不操作J-Link或实体控制。
- 2026-08-11：[OTA 与 App 更新目标设备验收记录](ota-device-acceptance-record.md) 固化正式 App/设备/MCU 身份、故障注入、断电回滚、业务不变和四方签字证据；没有把真机口头确认提前当成通过。
- 2026-08-11：现网只读探测确认 `type=1&productId=2` 返回旧“水桶控制”而非 TJI Platform；服务端所需独立记录和字段已加入待办。
- 2026-08-11：再次只读检查 `type=1` productId 1～20；1/2 是旧水枪/水桶包，3～20 全部错误回退到水枪包，且均缺 packageName、大小、SHA256、signer，因此没有可安全注入的正式 App productId。
- 2026-08-11：`verify_ota_artifacts.py` 流式验证 productId 2/4/6/7 的真实固件文件，四包大小与 SHA256 全部通过；工具的 3 项单测覆盖篡改与异常元数据。
- 2026-08-12：map/noMap Debug 与 Release JVM 各 376 tests、0 failed；Network Debug/Release 各 25 tests、0 failed；新增 1 项 Android PackageManager 身份测试通过；文档/工具 58 tests、Release lint、R8、签名与 manifest 全绿。
- 当前边界：TJI Platform App 内自更新保持关闭，MCU断电回滚归MCU工程验收；二者均不改变或阻断现有控制、状态与OTA业务。

### 项目总门禁

- [x] OTA/App 更新可验证、失败可恢复且正常业务路径不变；允许进入项目 09。

---

## 项目 09：大类拆分与会话作用域治理

### 改动前确认

- [x] 为待拆分类建立现有 StateFlow、事件、命令和副作用清单。
- [x] 为 Speaker、Radio、OTA 建立关键状态机回归测试。
- [x] 每次只拆一个类，不同时改 UI 交互和协议。

### 实施

- [x] 拆分 Speaker talk、record、TTS、servo 等职责，保持公开状态和事件接口稳定。
- [x] 拆分 Radio 页面展示、解析和任务管理职责。
- [x] 产品 ViewModel 使用合适的 session/nav graph 作用域。
- [x] 页面离开、设备切换和账号切换时显式停止对应任务。
- [x] 加入 detekt/复杂度门禁，但先建立合理 baseline，不一次性机械重写全仓库。

### 自动验证

- [x] 拆分前后的状态机测试结果一致。
- [x] payload golden tests 完全一致。
- [x] 页面销毁和账号切换后无遗留任务。
- [x] 全量 JVM、Lint 和 Release 构建通过。

### 业务不变验证

- [ ] Speaker 全部控制、录音、TTS 和反馈功能真机验证。
- [ ] Radio 列表、地图、回放和告警功能真机验证。
- [x] UI 文案、按钮位置和操作步骤没有未经批准的变化。

### 项目 09 验证记录

- 2026-08-12：[ADR-004](../architecture/adr-004-product-control-state-machine-and-lifecycle.md) 固化 Speaker、RadioDetection、OTA 的公开状态、事件、命令、副作用和离页停止规则。
- 2026-08-12：Speaker 主 ViewModel 从 830 行降至 340 行；PTT、生成音频、录音保存、TTS、MCU 麦克风和设备命令分别由单一职责协调器持有，48 kHz、Opus、UDP 与 MQTT payload 未改。
- 2026-08-12：Radio 页面进入时启动 ACK 收集和目标 TTL 清理，离开时取消 prune、timeout、feedback clear 与晚到 callback；OTA 离页取消 HTTP check、失效 request 并释放 start reservation。
- 2026-08-12：引入 detekt 1.23.8，门禁覆盖长方法、长参数、职责数量、行宽和 nullable unsafe call；App 仅保留 39 项既有复杂度 baseline，NetWork 无 baseline 债务，新协调器无豁免。
- 2026-08-12：`checkDocs`、App Map/NoMap Debug 全量单测、NetWork Debug 单测、App/NetWork detekt、Map/NoMap Release Lint、双 Release R8 构建在同一命令中通过（225 tasks，2m36s）；随后生命周期竞态补测通过，NoMap Debug 共 387 tests、0 fail，NetWork 25 tests、0 fail。
- 2026-08-12：重构后的 NoMap Debug 安装到 MI 8（Android 10）并冷启动成功，Activity/进程存活，logcat 无 FATAL/ANR；未自动发送任何真实设备控制命令。

### 项目总门禁

- [ ] 可维护性提升且公开行为完全一致；允许进入项目 10。

---

## 项目 10：量产验证、可观测性与放行

### 改动前确认

- [ ] 建立实际支持的 Android 版本、手机型号、设备固件版本和产品矩阵。
- [x] 定义现场需要收集的最少指标，不引入重型全链路平台。
- [x] 定义量产故障联系人、日志提取方式和回退包。

### 实施

- [x] 接入轻量 Crash/ANR 收集，或实现可导出的本地诊断包。
- [x] 记录 App 版本、flavor、设备固件、MQTT 连接结果和 OTA 结果，默认脱敏。
- [x] Release 自动归档 mapping/native symbols。
- [ ] 建立关键页面冷启动、内存、耗电和长稳基线。
- [ ] 建立每版安装、升级、降级和回退清单。

### 项目 10 阶段证据

- 2026-08-12：[现场诊断与故障处理](field-diagnostics-runbook.md) 定义最少指标、责任角色、提取方式、符号化和回退路径。
- 2026-08-12：本地 JSONL 诊断实现 256 KiB 上限、账号/密码/token 脱敏和 SHA-256 设备引用；设置页可通过 FileProvider 主动导出，2 条存储/脱敏测试通过。
- 2026-08-12：CI 按源码 SHA 归档 Map/NoMap 的 `mapping.txt` 与 `native-debug-symbols.zip`；正式签名 manifest 继续记录 APK SHA256、版本、flavor 和证书摘要。
- 2026-08-12：最终自动回归 Map Debug 389 tests、NoMap Debug 389 tests、NetWork Debug 25 tests，全部 0 fail；App/NetWork detekt 0 新问题，Release Lint 0 error，Map/NoMap R8 APK 构建成功。
- 2026-08-12：最新 NoMap Debug 在 MI 8 / Android 10 安装、冷启动并生成 `app_start` 诊断事件，包含 V2.0.13、213、noMap、API 29和手机型号；进程存活且无 FATAL/ANR。

### 自动与现场验证

- [ ] 所有支持产品完成登录、状态读取和关键控制命令真机回归。
- [ ] 权限拒绝、旋转、进程被杀、重新启动、前后台切换测试通过。
- [ ] Wi-Fi 切换、断网、丢包、Broker 重启和服务器暂不可用测试通过。
- [ ] Speaker 完成长时间喊话/反馈压力测试。
- [ ] 所有支持 OTA 的产品完成正常升级和失败恢复测试。
- [ ] 至少完成一次 72 小时长稳测试，无不可恢复断连、持续内存增长或错误控制。
- [ ] 正式 APK/AAB、源码 commit、签名、协议版本和固件版本可一一追溯。

### 最终量产门禁

- [ ] 项目 01～09 总门禁全部通过。
- [ ] 当前 P0 问题为 0。
- [ ] 未完成风险均有负责人、影响说明和书面接受记录。
- [ ] 正式包可验证、可安装、可升级、可回退。
- [ ] 全产品关键业务逻辑已在目标设备上确认无变化。
- [ ] 产品、App、服务端和 MCU 负责人共同签字确认放行。

## 6. 每个项目的证据记录模板

完成项目时，在对应项目总门禁之前追加以下内容：

```markdown
### 验证证据

- Git commit：
- 改动文件：
- 自动测试命令：
- 自动测试结果：
- APK/AAB SHA256：
- 测试手机与 Android 版本：
- 嵌入式设备型号与固件版本：
- 人工业务验证人：
- 验证日期：
- 已知问题/风险接受：
- 回退方式：
```

只有证据填写完整、对应复选框全部完成后，才能勾选项目总门禁。

## 7. 失败与回退规则

- 自动测试失败：保持当前项目未完成，修复后重新执行该项目全部验证。
- 业务行为变化：优先回退本项目改动；只有业务负责人明确批准后才能作为新需求重新立项。
- 设备或服务端条件不足：记录缺失证据，项目保持未完成，不用单元测试代替真机结论。
- 回退后也要重新运行受影响测试，证明已经恢复到项目开始前的行为。
- 禁止通过删除测试、放宽断言、吞掉异常或隐藏错误日志来让门禁变绿。

## 8. 当前执行位置

- [x] 已完成代码库多维审查并形成量产差距基线。
- [x] 已确认采用“有限客户嵌入式上位机”的适度安全边界。
- [x] 已建立严格串行、逐项验收、完成后打勾的执行方案。
- [x] 项目 01——可重复构建与 Opus 依赖闭环已通过总门禁。
- [x] 项目 02——CI 与文档质量门修复已通过总门禁。
- [x] 项目 03——正式 Release 签名与产物识别已通过总门禁。
- [x] 项目 04——登录、登出与账号会话生命周期已通过总门禁。
- [x] 项目 05——产品注册完整性与未交付产品隔离已通过总门禁。
- [x] 项目 06——MQTT 稳定性与适度安全已通过总门禁（公网 1883 已记录产品风险豁免）。
- [x] 项目 07——本地凭据、隐私和 WebView 收敛已通过总门禁（登录 POST 为服务器兼容性待办）。
- [x] 项目 08——OTA 与 App 更新可靠性已通过总门禁；App 压力测试不依赖 J-Link。
- [ ] 当前项目：项目 09——大类拆分与会话作用域治理。
- [ ] 下一动作：先建立 Speaker、Radio、OTA 的公开状态、事件、命令、副作用和状态机回归基线；完成改动前确认前不得拆分类。
