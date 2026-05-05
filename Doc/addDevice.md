1. 你们当前系统的真实模型（先统一概念）
你现在要做的“添加设备/绑定设备”，在后端实现上其实分两件事：

设备记录预先存在（库里已有 udid、sn，但 user_account_id 为空）
App 做的是把已有记录绑定到当前用户（把 user_account_id 填上）
所以 App 侧做的不是“创造一台新设备”，而是“从未绑定设备池里领用/绑定”。

2. App 要实现的业务流程（你到底要怎么做）

**线上当前可用、App 已接入的只有「流程 A」**（见第 3 节）。以下两条接口在 `https://wx.tjinnovations.cn/api` 下可联调。

流程 A：绑定（现网协议）
适用：设备已经被“预置/导入/后台录入”到库里，且当前未绑定任何用户。

用户输入（或扫码得到）一个查询 key
当前后端支持的是 udid（而且是尾部匹配）
调查询接口，拿到候选设备列表（每条都带 udid 和 sn）
用户选择一条设备
调绑定接口，把这条设备绑定到当前登录用户

流程 B：真正“新增设备 + 绑定”（**线上暂未提供**，见第 7 节）
适用：库里完全没有这台设备记录，你希望 App 自己创建并绑定。需要后端先部署对应接口后再在 App 开放入口。

3. 接口清单（现有后端可用 — 现网联调以本节为准）

假设你的 Retrofit baseUrl 是 https://wx.tjinnovations.cn/api
那么下面的 path 就不要再带 /api

3.1 查询可绑定设备（未绑定池）
方法：GET
路径：/data/bind/queryByUdid
Query：
udid: String（模糊查询 key）
返回：[{ udid, sn }]
重要：后端是尾部匹配，等价于 udid LIKE '%<udid参数>'
所以搜 139 会命中 HydroLink-25794139（因为它以 139 结尾），搜 257 不会命中它（因为不是以 257 结尾）。

3.2 绑定设备到当前用户
方法：POST
路径：/data/bind/update
Header：
token: <登录返回token>（必须）
Body（JSON）：
udid: String
sn1: String（注意字段名是 sn1；值来自查询返回的 sn）
后端会从 token 里解析当前用户 id，然后按 udid + sn1 定位记录，把 user_account_id 更新成当前用户。

4. Retrofit 建议写法（现网 App 直接能用）

**重要（避免 404）**：若 `baseUrl` 已是 `https://wx.tjinnovations.cn/api/`（末尾带 `api/`），接口方法上的 path **不要用前导 `/`**（例如写 `data/bind/queryByUdid`，不要写 `/data/bind/queryByUdid`）。Retrofit 里以 `/` 开头的 path 会从**域名根路径**拼接，会把 `api/` 丢掉，实际请求变成 `https://wx.tjinnovations.cn/data/...`。

interface WxApiService {

  @GET("data/bind/queryByUdid")
  suspend fun queryBindableDevicesByUdid(
    @Query("udid") udid: String
  ): ApiResponse<List<BindableDevice>>

  @POST("data/bind/update")
  suspend fun bindDevice(
    @Header("token") token: String,
    @Body body: BindDeviceRequest
  ): ApiResponse<Map<String, Any>?>
}

data class BindableDevice(
  val udid: String,
  val sn: String
)

data class BindDeviceRequest(
  val udid: String,
  val sn1: String
)
说明：

Authorization: Bearer ... 对这条链路不是必须（后端用的是 token header），你可以保留也可以不保留。
绑定必须带 token，否则后端不知道绑给谁。

5. App UI/交互应该怎么做（最小可用 + 不让用户觉得“多一步”）
页面：绑定设备
输入框：UDID（你们现在是“尾号/尾部字符串”）
按钮：查询/搜索（也可以做输入后自动查询）
列表：显示候选设备（udid + sn）
按钮：确认绑定
推荐体验优化：

查询结果只有 1 条：默认选中，用户点击一次“确认绑定”即可
查询结果 0 条：提示“未找到未绑定设备，请联系后台先录入/导入设备”
查询结果 >1 条：让用户选择正确那一条（避免绑错）
6. 失败与边界情况（App 必须处理）
查询为空
含义：未绑定池里没有匹配 udid 尾部的设备
App 提示：让用户确认输入，或提示联系后台录入设备
绑定失败（code != 200 或 success=false）
常见原因：
token 失效/未登录
设备已被其他账号绑定（不在未绑定池）
udid+sn1 不存在（库里没记录）
重复绑定
你们后端查询只返回 user_account_id is null，理论上重复绑定不会发生
但仍建议：绑定失败时提示“设备已绑定或不可绑定”，并提示刷新列表

7. 「createAndBind」——预研协议，线上多数环境尚未部署（勿当现网依赖）

以下接口若在环境中返回 **HTTP 404**，说明**当前网关/应用尚未注册该路由**，App **不应**向用户展示依赖该接口的能力；待后端发布后再打开 App 入口。

接口（约定稿）：**POST `data/bind/createAndBind`**（相对 base `https://wx.tjinnovations.cn/api/`）

- **Header**：`token`（与查询、update 一致）
- **Body（固定）**：`udid: String`、`sn1: String`、`productId: String`
- **消防吊桶**：`productId = "2"`（字符串）

**后端行为约定（产品已拍板，以后端实际上线为准）：**

| 库里 (udid, sn1) | user_account_id | 行为 |
|------------------|-------------------|------|
| 已存在 | null | 直接绑定到当前 token 用户 |
| 已存在 | 非 null | 返回错误（如「设备已绑定」） |
| 不存在 | — | 先插入记录再绑定 |

**数据库建议**：`UNIQUE(udid, sn1)`（至少），避免重复添加。

工程内 NetWork 层可保留 `createAndBind` 方法供将来启用；**BindBucketDeviceDialog 当前仅走 query + update**。

8. 你现在最该做的开发清单（按优先级 — 与现网一致部分已完成）

接入 GET /data/bind/queryByUdid
列表选中后接入 POST /data/bind/update（header 带 token，body 带 udid+sn1）
做好失败提示与刷新
如果要“无感查询”：输入后自动查，结果 1 条则默认选中
（可选）后端上线 createAndBind 后，再增加「手动录入创建并绑定」UI
