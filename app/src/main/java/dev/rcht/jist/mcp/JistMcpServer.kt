package dev.rcht.jist.mcp

import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dev.rcht.jist.JistApplication
import dev.rcht.jist.data.preferences.PreferencesRepository
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Lifecycle facade for the Jist MCP server.
 *
 * Owns:
 * - the NanoHTTPD HTTP endpoint ([JistMcpHttpServer], bound to 127.0.0.1 by default)
 * - the custom [McpHttpTransport] bridging HTTP to the SDK protocol engine
 * - the SDK [Server] with the three read-only tools
 * - the foreground service that keeps the process (and socket) alive
 *
 * All state is guarded by [lock]; [start]/[stop] are idempotent.
 */
class JistMcpServer(
    private val app: JistApplication,
    private val preferencesRepository: PreferencesRepository,
    private val handlers: McpToolHandlers,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private var httpd: JistMcpHttpServer? = null
    private var transport: McpHttpTransport? = null
    private var session: ServerSession? = null
    private var authToken: String = ""

    @Volatile
    var isRunning = false
        private set

    @Volatile
    var bindDescription: String = ""
        private set

    @Volatile
    var lastError: String? = null
        private set

    /** Start the server if not already running. Reads port/token/lan settings from prefs. */
    suspend fun start() {
        synchronized(lock) {
            if (isRunning) return
        }
        // Suspend calls (prefs read, session connect) stay outside the lock:
        // suspending inside a critical section is not allowed.
        val prefs = preferencesRepository.preferencesFlow.first()
        val port = prefs.mcpPort
        val token = prefs.mcpToken
        val bind = if (prefs.mcpAllowLan) "0.0.0.0" else "127.0.0.1"

        try {
            val t = McpHttpTransport()
            val server = buildServer()
            val s = server.createSession(t)

            val h = JistMcpHttpServer(
                transport = t,
                // Read the property on every request (not the local snapshot) so
                // updateToken() takes effect on the running server without a restart.
                expectedToken = { authToken },
                loopbackOnly = (bind == "127.0.0.1"),
                hostname = bind,
                port = port,
            )
            h.start()

            var alreadyRunning = false
            synchronized(lock) {
                alreadyRunning = isRunning
                if (!alreadyRunning) {
                    authToken = token
                    httpd = h
                    transport = t
                    session = s
                    isRunning = true
                    bindDescription = "$bind:$port"
                    lastError = null
                }
            }
            if (alreadyRunning) {
                // A concurrent start won the race; roll ours back (outside the lock,
                // since transport.close() suspends).
                runCatching { h.stop() }
                runCatching { t.close() }
                return
            }

            Log.i(TAG, "MCP server started at $bind:$port (token set: ${token.isNotEmpty()})")

            // Keep the process alive while the socket is serving.
            ContextCompat.startForegroundService(app, Intent(app, McpForegroundService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "failed to start MCP server", e)
            lastError = "启动失败：${e.message}"
            cleanupAfterFailure()
        }
    }

    /** Stop the server (socket, session, foreground service). */
    fun stop() {
        synchronized(lock) {
            if (!isRunning) return
            try {
                app.stopService(Intent(app, McpForegroundService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "failed to stop foreground service", e)
            }
            try {
                httpd?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "failed to stop http server", e)
            }
            scope.launch {
                runCatching { transport?.close() }
            }
            httpd = null
            transport = null
            session = null
            isRunning = false
            bindDescription = ""
            Log.i(TAG, "MCP server stopped")
        }
    }

    /** Apply a new token to the running server without a restart. */
    fun updateToken(token: String) {
        synchronized(lock) {
            authToken = token
        }
    }

    /** Restart (stop + start) — used when port / LAN binding changes. */
    suspend fun restart() {
        stop()
        start()
    }

    /** Start or stop according to the enabled flag (idempotent). */
    suspend fun applyEnabled(enabled: Boolean) {
        if (enabled) start() else stop()
    }

    private fun cleanupAfterFailure() {
        runCatching { httpd?.stop() }
        scope.launch { runCatching { transport?.close() } }
        runCatching {
            app.stopService(Intent(app, McpForegroundService::class.java))
        }
        httpd = null
        transport = null
        session = null
    }

    private fun buildServer(): Server = Server(
        serverInfo = Implementation(
            name = "Jist",
            version = BuildConfigVersion,
            title = "Jist 消息助手",
        ),
        options = ServerOptions(ServerCapabilities(tools = ServerCapabilities.Tools())),
        instructions = INSTRUCTIONS,
    ) {
        addTool(
            name = "list_notifications",
            description = "获取最近收到的消息通知列表（所有应用，或指定应用）。" +
                "返回每条消息的时间、应用、群名/会话名、发送者和内容。" +
                "通知数据默认保留 7 天，更早的数据可能已被清理。",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put(
                        "app",
                        buildJsonObject {
                            put("type", "string")
                            put(
                                "description",
                                "应用包名或别名（wx/wechat、tg/telegram、whatsapp、gmail），可选"
                            )
                        }
                    )
                    put(
                        "limit",
                        buildJsonObject {
                            put("type", "integer")
                            put("default", 50)
                            put("maximum", 200)
                        }
                    )
                    put(
                        "offset",
                        buildJsonObject {
                            put("type", "integer")
                            put("default", 0)
                        }
                    )
                }
            ),
        ) { request ->
            handlers.listNotifications(request.arguments)
        }

        addTool(
            name = "list_conversations",
            description = "列出群/会话名称（即群列表）。Xposed 渠道只列已开启 AI 摘要的会话" +
                "（与摘要 worker 口径一致，历史完整）；通知渠道列通知捕获的会话" +
                "（仅通知到达过的消息，可能不完整）。" +
                "返回项的 key 字段可直接作为 get_chat_messages 的 chatId 使用；" +
                "get_chat_messages 仍可按名称查询未开启摘要的监听会话。",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put(
                        "app",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "按应用过滤：包名或别名（wx/wechat、tg/telegram 等），可选")
                        }
                    )
                    put(
                        "channel",
                        buildJsonObject {
                            put("type", "string")
                            put(
                                "enum",
                                buildJsonArray {
                                    add(JsonPrimitive("xposed"))
                                    add(JsonPrimitive("notification"))
                                }
                            )
                            put("description", "按来源渠道过滤，可选")
                        }
                    )
                    put(
                        "limit",
                        buildJsonObject {
                            put("type", "integer")
                            put("default", 100)
                            put("maximum", 500)
                        }
                    )
                    put(
                        "offset",
                        buildJsonObject {
                            put("type", "integer")
                            put("default", 0)
                        }
                    )
                }
            ),
        ) { request ->
            handlers.listConversations(request.arguments)
        }

        addTool(
            name = "get_chat_messages",
            description = "查询指定群/会话在时间范围内的消息。" +
                "chatName（群名称，大小写不敏感）与 chatId 至少提供一个；" +
                "优先查 Xposed 完整历史，无结果时回退到通知渠道。" +
                "时间支持 ISO-8601（如 2026-02-10T00:00:00Z 或 2026-02-10）或毫秒时间戳。" +
                "注意保留期限制：通知默认 7 天，Xposed 消息按会话保留设置清理。",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put(
                        "chatName",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "群/会话名称（与 chatId 二选一）")
                        }
                    )
                    put(
                        "chatId",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "会话 key（list_conversations 返回的 key，与 chatName 二选一）")
                        }
                    )
                    put(
                        "app",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "应用包名或别名，用于同名会话消歧，可选")
                        }
                    )
                    put(
                        "timeFrom",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "起始时间：ISO-8601 或毫秒时间戳，可选（默认最早）")
                        }
                    )
                    put(
                        "timeTo",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "结束时间：ISO-8601 或毫秒时间戳，可选（默认现在）")
                        }
                    )
                    put(
                        "limit",
                        buildJsonObject {
                            put("type", "integer")
                            put("default", 100)
                            put("maximum", 500)
                        }
                    )
                }
            ),
        ) { request ->
            handlers.getChatMessages(request.arguments)
        }
    }

    companion object {
        const val TAG = "JistMcpServer"
        private const val BuildConfigVersion = "1.0"

        private val INSTRUCTIONS = """
            Jist 手机消息助手（只读）。
            典型流程：
            1. list_conversations(app="wx") 获取群名列表；
            2. get_chat_messages(chatName="群名", timeFrom="2026-02-10T00:00:00Z", timeTo="2026-02-10T23:59:59Z") 查询该群某天的消息。
            数据受保留期限制：通知默认保留 7 天；Xposed 消息按会话保留设置清理，更早的消息可能已被删除。
        """.trimIndent()
    }
}
