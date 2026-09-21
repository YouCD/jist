# Jist MCP Server 设计（只读查询版）

> 目标：外部 agent（Claude Desktop / pi / Cursor 等 MCP client）连接本应用，
> **只读查询**消息数据。范围（v1）：
> - `list_notifications`（最近通知/消息）
> - `list_conversations`（群名/会话名清单）
> - `get_chat_messages`（按群名 → 时间范围消息）
> - 不做：整理（写操作）、消息交互（open_chat / ask_about_messages / send_message）、
>   watch topics 查询（v1 移除）

---

## 1. "群名 → 指定时间范围消息"的数据链路

Jist 有两条消息通道，链路均可走通：

### 通道 A：Xposed（完整聊天历史）✅ 查询已就绪
- 群名：`watched_chats.chatName`，经 `chat_sources` 关联到 app 包名
  （`com.tencent.mm` 微信 / `org.telegram.messenger` Telegram）
- 消息：`ChatMessageDao.getByChatAndTimeRange(chatAppKey, chatId, timeFrom, timeTo)`
  **已存在**，handler 只需把 `chatName` 解析为 `(chatAppKey, chatId)`
  （watched_chats + chat_sources 内存 join，表很小，无需新查询）

### 通道 B：通知监听（无需 Xposed，仅"通知级"消息）⚠️ 需补 2 个小查询
- 群名即 `NotificationEntity.title`；`conversationKey` 由
  `ConversationKeyExtractor` 生成：Telegram = `org.telegram.messenger:<群名>`，
  微信 fallback = `<pkg>:<群名>`
- 需补：
  1. `NotificationDao.listConversations(pkg?)`：
     `SELECT packageName, title, conversationKey, COUNT(*), MAX(timestamp)
      FROM notifications [WHERE packageName=?] GROUP BY conversationKey ORDER BY MAX(timestamp) DESC`
  2. `NotificationDao.getByConversationKeyAndTimeRange(key, timeFrom, timeTo)`：
     `SELECT * FROM notifications WHERE conversationKey=? AND timestamp BETWEEN ? AND ? ORDER BY timestamp ASC`
- 限制：只有到达过通知的消息（可能不完整）

### 保留期约束（时间范围查询的边界）
- 通知：`autoDeleteNotificationsAfterDays`（默认 7 天）
- Xposed 消息：按每个 watched chat 的 `retentionDays` 清理（`SummaryWorker.cleanupOldMessages`）
- tool 返回中附 `retentionNote`，范围超保留期时明确告知数据可能缺失

---

## 2. Tools 清单（v1 共 3 个）

### `list_notifications` —— 最近通知（消息）流

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `app` | string | 否 | 全部 app | 包名或别名（wx/wechat、tg/telegram、whatsapp、gmail…） |
| `limit` | int | 否 | 50（max 200） | 返回条数，按时间倒序 |
| `offset` | int | 否 | 0 | 分页偏移 |

实现：`NotificationRepository.getRecent/getByApp`，内存按时间范围截断。
输出 items：`{id, time, app, group, sender, content}`。

### `list_conversations` —— 群名/会话名清单

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `app` | string | 否 | 全部 app | 包名或别名，只返回该 app 的会话 |
| `channel` | string(enum) | 否 | 全部 | `xposed` / `notification`，按来源通道过滤 |
| `limit` | int | 否 | 100（max 500） | 按 lastSeenAt 倒序 |
| `offset` | int | 否 | 0 | 分页偏移 |

实现：双通道 union——
- Xposed：**仅已开启 AI 摘要的监听会话**（`watched_chats.isSummarized = true`，与 SummaryWorker 口径一致）⋈ chat_sources → `{name=chatName, app=pkg, channel="xposed", key=chatId, messageCount, lastSeenAt}`。`get_chat_messages` 仍可按名称/ID 查询任意监听会话（含未开摘要的）。
- 通知：`NotificationDao.listConversations`（`GROUP BY conversationKey`）→ `{name=title, app=pkg, channel="notification", key=conversationKey, messageCount, lastSeenAt}`

`key` 可直接作为 `get_chat_messages` 的入参（chatId / conversationKey），agent 无需猜测。

### `get_chat_messages` —— 按群名 + 时间范围取消息

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `chatName` | string | 二选一 | — | 群名/联系人名（与 `chatId` 至少传一个） |
| `chatId` | string | 二选一 | — | Xposed 的 chatId 或通知的 conversationKey，直传跳过 name 解析 |
| `app` | string | 否 | — | 包名或别名；`chatName` 重名时用于消歧 |
| `timeFrom` | string\|int | 否 | 最早 | ISO-8601（如 `2026-02-10T00:00:00Z`）或 epoch ms |
| `timeTo` | string\|int | 否 | now | 同上 |
| `limit` | int | 否 | 100（max 500） | 条数上限，时间范围内按时间倒序 |

解析顺序：① Xposed：chatName/chatId → (chatAppKey, chatId) → `getByChatAndTimeRange`
② 通知通道 fallback：按 conversationKey 匹配（`<pkg>:<chatName>` 或 Telegram 规则）
→ `getByConversationKeyAndTimeRange`。
输出：`{items: [{sender, content, time, source: "xposed"\|"notification"}], retentionNote?}`。
群名未命中时不报错，返回该 app 下可用群名列表供 agent 纠错。

**包名别名**（入参 `app` 支持）：`wechat|wx → com.tencent.mm`，
`telegram|tg → org.telegram.messenger`，`whatsapp → com.whatsapp`，其余按原 pkg 处理。

**输出约定**：
- 时间一律 ISO-8601 UTC 字符串（agent 友好），内部仍是 ms
- 所有列表 tool 返回 `{items, total?, limit, offset, retentionNote?}`
- 错误：未知群名 → 返回 `content` 里列出该 app 下可用群名（MCP isError=false，
  让 agent 自行纠错），不抛 JSON-RPC error

---

## 3. 传输与部署

- **NanoHTTPD** 绑 `127.0.0.1:8765`，实现 MCP Streamable HTTP 最简形态
  （`POST /mcp` 无状态 JSON-RPC 2.0 同步响应）。
  不用官方 Kotlin MCP SDK 的 server HTTP transport——其基于 Ktor，
  依赖链太重（见 §7），故自定义 `McpHttpTransport`（继承 SDK 的
  `AbstractTransport`）桥接 NanoHTTPD，协议逻辑仍全部用 SDK。
- 鉴权：`Authorization: Bearer <token>`；token 首次开启生成（32 字节随机），
  存 `JistPreferences.mcpToken`；错 token → 401。
- 保活：开启时 foreground service（通知栏"Jist MCP 运行中"）。
- 可达性：默认 `adb reverse tcp:8765 tcp:8765`；局域网绑定为显式开关
  （`mcpAllowLan`，绑 0.0.0.0，必须带 token）。

Client 配置示例：
```json
{ "mcpServers": { "jist": {
    "url": "http://127.0.0.1:8765/mcp",
    "headers": { "Authorization": "Bearer <token>" } } } }
```

---

## 4. 安全（只读场景简化版）

1. 默认关闭：设置页 `mcpEnabled` 开关，关闭即停止监听
2. Token 鉴权 + 重新生成
3. 默认仅 loopback；LAN 为显式开关
4. 所有输出不含 LLM apiKey（v1 不暴露 LLM 配置）
5. 日志只记 tool 名 + 耗时 + 结果码，不记消息正文
6. v1 全只读，无写路径 → 天然无数据破坏风险

---

## 5. 代码组织（实际实现）

```
dev/rcht/jist/mcp/
├── JistMcpServer.kt      // 生命周期 facade：start/stop/restart/applyEnabled/updateToken，
│                          //   持有 SDK Server + 3 个 tool 的注册（schema + handler）
├── JistMcpHttpServer.kt  // NanoHTTPD 子类：/mcp 路由、Bearer 鉴权、CORS、
│                          //   runBlocking 桥接同步 HTTP -> suspend transport
├── McpHttpTransport.kt   // 自定义 AbstractTransport：deliver（入站）、
│                          //   registerPending/awaitResponse（按 RequestId 关联响应）、
│                          //   handlePost（JSON-RPC 单条/批量 -> HTTP 响应）
├── McpToolHandlers.kt    // 3 个 tool 的实现（别名解析、双通道 fallback、统一输出）
└── McpForegroundService.kt // 保活 + 通知栏（dataSync 类型）
```

协议逻辑全部交给官方 Kotlin MCP SDK（`Server`/`ServerSession`/`ToolRegistry`），
自定义 transport 只负责 HTTP <-> 消息桥接。

配套改动（均很小）：
- `NotificationDao` + `NotificationRepository`：新增
  `listConversations`、`getByConversationKeyAndTimeRange`、`oldestTimestamp`（§1 通道 B）
- `ChatMessageDao`：新增 `maxTimestampByWatchedChat`（retentionNote 判断）
- `JistPreferences`：新增 `mcpEnabled`、`mcpToken`、`mcpPort=8765`、`mcpAllowLan=false`
- `JistApplication`：初始化 `JistMcpServer` 并注入
  `NotificationRepository`、`ChatSourceRepository`、`WatchedChatRepository`、`ChatMessageRepository`；
  启动时若 `mcpEnabled` 则自动 start
- `SettingsScreen`/`SettingsViewModel`：MCP 区块（开关、状态、token 显示/复制/重生成、
  端口、LAN 开关、`adb reverse` 命令复制、client 配置 JSON 复制）

---

## 6. 实施步骤（P0 一次性交付）

1. `McpHttpTransport` + `JistMcpHttpServer` + `JistMcpServer`（含鉴权、foreground service）
2. `NotificationDao` 新查询 + repository 方法
3. 3 个 tool handler（包名别名解析、双通道 fallback、统一输出格式）
4. 设置页 MCP 区块 + `JistPreferences` 字段
5. 验证：`adb reverse` + MCP inspector / pi 客户端连入，
   实测 `list_conversations` → `get_chat_messages(群名, 昨天~今天)` 全链路

---

## 7. 构建注意事项（实测踩坑）

1. **Ktor 排除**：SDK 发布的 server 模块 POM 把 Ktor server 全家桶列为 runtime 依赖。
   只用纯协议类，`implementation(...) { exclude(group = "io.ktor") }` 即可，
   核心类（Server/ServerSession/TransportManager/Protocol）字节码不引用 io.ktor。
2. **stdlib 排除（关键）**：SDK POM 还把 `kotlin-stdlib` 提升到 2.4.0。
   本项目用 AGP 9.2.1，其内置应用的 Kotlin 编译器是 **2.2.10**
   （libs.versions.toml 里的 2.3.21 并未实际用于编译，`kotlinCompilerClasspath`
   实际解析为 2.2.10），只能读 ≤2.3.0 的 metadata → stdlib 2.4.0 会让整个编译崩溃
   （`kotlin.Unit` 都解析不了）。必须对两个 SDK 依赖
   `exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")`（含 -jdk7/-jdk8），
   让 classpath 用回项目自己的 stdlib（2.3.20）。
3. 其余被提升的依赖 metadata 均可读：kotlinx-serialization-json 1.11.0（mv 2.3）、
   kotlinx-coroutines-core 1.11.0（mv 2.0）、kotlinx-io-core 0.9.1（mv 2.3.0）。
4. **NanoHTTPD 2.3.1 API**：无 `MIME_JSON` 常量（自行定义）；
   状态码枚举同时存在 `Response.Status`（实现 `IStatus`）与 `Response.IStatus`；
   构造器 `NanoHTTPD(hostname, port)` + `start()`；body 用 `session.inputStream`，
   headers 用 `session.headers: Map<String,String>`，`resp.addHeader(k, v)`。
5. **kotlinx-serialization 1.11.0 API**：`Json.parse(String)` 不存在，用
   `parseToJsonElement`；reified 扩展 `decodeFromJsonElement`/`encodeToJsonElement`
   在 `kotlinx.serialization.json` 包（非 `kotlinx.serialization`）。
6. **Kotlin 2.2 编译器**：`synchronized` 临界区内不允许 suspend 调用
   （"suspension point inside a critical section" 是 error 不是 warning）；
   `const val` 初始化器必须是编译期常量（不能用 `trimIndent()`）。
7. material-icons-extended 1.7.8 没有 `Pub`、`Refresh` 图标（用 `Lan`/`Sync`）。
8. Gradle 9.6 + 双 flavor：编译任务为 `:app:compileNormalDebugKotlin` /
   `:app:compileXposedDebugKotlin`（`:app:compileDebugKotlin` 有歧义）；
   内存紧张时两个 assemble 一次跑会让 Gradle daemon 被 OOM kill，分开跑。

### 7.1 运行时踩坑（真机 E2E 发现，已修复）

9. **slf4j-api 缺失 → 启动崩溃**：SDK 传递依赖 `kotlin-logging`（8.0.4），
   Gradle 解析到其 Android 变体 `kotlin-logging-android`，该变体的 POM
   只声明 `kotlin-stdlib`、**丢掉了 `slf4j-api`** 依赖 →
   `NoClassDefFoundError: org/slf4j/LoggerFactory`，App 一启动就崩
   （MCP 开关打开时，`JistApplication.onCreate` 里自动 start 即触发）。
   修复：显式 `implementation(libs.slf4j.api)`（2.0.18）。
   无 binding 时 slf4j 回退 NOP logger，Android 上无需额外配置。
10. **token 快照 bug**：`JistMcpServer.start()` 里若写 `expectedToken = { token }`
    （捕获局部变量），用户在 UI 点“重新生成 token”后 `updateToken()` 只更新了
    `authToken` 属性，运行中的服务器仍拿**旧 token** 比对 → 新 token 一律 401。
    必须写 `expectedToken = { authToken }`（每次请求读属性）。
11. **readBody 读到 EOF 超时**：NanoHTTPD 是 keep-alive 连接，客户端发完 body
    不关 socket；若 `inputStream.readText()` 读到 EOF，会阻塞到 socket 超时
    （5s）抛 `SocketTimeoutException`。必须按 `Content-Length` 精确读字节数。
    （之前被 401 分支“掩盖”——认证失败时根本走不到 readBody。）

### 7.2 E2E 验证记录（2026-09-20，真机 xposed flavor）

```
adb reverse tcp:8765 tcp:8765   # 宿主机 8765 被其他进程占用时改用设备端 curl
# 设备端：adb shell 'curl -s -X POST http://127.0.0.1:8765/mcp \
#   -H "Content-Type: application/json" -H "Authorization: Bearer <token>" -d ...'
initialize      → 200，返回 protocolVersion/capabilities/serverInfo/instructions
tools/list      → 200，返回 3 个工具（list_notifications/list_conversations/get_chat_messages）
tools/call
  list_conversations(app=wx)        → 真实微信群列表（群名/key/messageCount/lastSeenAt）
  get_chat_messages(chatName, 时间范围) → 真实群消息（sender/content/time，channel=xposed）
```

无 token / 错误 token → 401 `{"error":"unauthorized",...}`；非 /mcp 路径 → 404；
OPTIONS → CORS 预检 200。
