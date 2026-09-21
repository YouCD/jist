package dev.rcht.jist.mcp

import android.util.Log
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Custom MCP [io.modelcontextprotocol.kotlin.sdk.shared.Transport] for Android.
 *
 * The official SDK HTTP transports are Ktor-based and unsuitable for Android, so this
 * transport bridges raw HTTP (served by NanoHTTPD) to the SDK protocol engine.
 *
 * Model: a single long-lived session ("stateless streamable HTTP" mode).
 * - Inbound client messages arrive via [deliver] (called by the HTTP layer per POST).
 * - Outbound responses are matched to the in-flight POST by JSON-RPC request id
 *   ([registerPending] + [awaitResponse]); the HTTP layer serializes and replies.
 * - Server-to-client notifications have no HTTP channel in stateless mode and are dropped.
 */
class McpHttpTransport : AbstractTransport() {

    private val pending = ConcurrentHashMap<RequestId, CompletableDeferred<JSONRPCMessage>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun start() {
        // No persistent connection to establish; messages flow per HTTP request.
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        when (message) {
            is JSONRPCResponse -> pending.remove(message.id)?.complete(message)
            is JSONRPCError -> pending.remove(message.id)?.complete(message)
            is JSONRPCRequest -> Log.w(TAG, "unexpected server->client request dropped: ${message.method}")
            else -> Log.d(TAG, "server->client notification dropped (stateless mode)")
        }
    }

    override suspend fun close() {
        pending.values.forEach {
            it.completeExceptionally(CancellationException("MCP transport closed"))
        }
        pending.clear()
        scope.cancel()
    }

    /** Deliver an inbound client message to the protocol engine. */
    fun deliver(message: JSONRPCMessage) {
        scope.launch {
            try {
                _onMessage(message)
            } catch (e: Exception) {
                Log.e(TAG, "protocol dispatch failed", e)
            }
        }
    }

    /** Register a pending response slot for an outbound request id. */
    fun registerPending(id: RequestId) {
        pending[id] = CompletableDeferred()
    }

    /**
     * Wait for the response matching [id] (completed by [send]).
     * Returns null on timeout; the caller should emit a JSON-RPC internal error.
     */
    suspend fun awaitResponse(id: RequestId, timeoutMs: Long): JSONRPCMessage? {
        val deferred = pending[id] ?: return null
        return try {
            withTimeoutOrNull(timeoutMs) { deferred.await() }
        } finally {
            pending.remove(id)
        }
    }

    /**
     * Handle one HTTP POST body (JSON-RPC message or batch) and produce the HTTP reply.
     * - Requests (with id): the response/error is returned as JSON.
     * - Notifications only: 202 with empty body.
     */
    suspend fun handlePost(body: String): McpHttpResponse {
        val root: JsonElement = try {
            McpJson.parseToJsonElement(body)
        } catch (e: Exception) {
            return McpHttpResponse(400, errorJson(null, RPCError.ErrorCode.PARSE_ERROR, "Parse error: ${e.message}"))
        }
        val elements = if (root is JsonArray) root else listOf(root)

        val messages = mutableListOf<JSONRPCMessage>()
        for (el in elements) {
            val msg = try {
                McpJson.decodeFromJsonElement<JSONRPCMessage>(el)
            } catch (e: Exception) {
                return McpHttpResponse(400, errorJson(null, RPCError.ErrorCode.INVALID_REQUEST, "Invalid request: ${e.message}"))
            }
            messages.add(msg)
        }

        val requests = messages.filterIsInstance<JSONRPCRequest>()
        if (requests.isEmpty()) {
            // Notifications only (e.g. notifications/initialized)
            messages.forEach { deliver(it) }
            return McpHttpResponse(202, null)
        }

        requests.forEach { registerPending(it.id) }
        messages.forEach { deliver(it) }

        val responses = requests.map { req ->
            val response = awaitResponse(req.id, RESPONSE_TIMEOUT_MS)
            when (response) {
                is JSONRPCMessage -> McpJson.encodeToJsonElement(response)
                null -> McpJson.encodeToJsonElement(
                    JSONRPCError(id = req.id, error = RPCError(RPCError.ErrorCode.REQUEST_TIMEOUT, "Request timed out"))
                )
            }
        }
        val bodyJson = if (responses.size == 1) {
            responses[0].toString()
        } else {
            buildJsonArray { responses.forEach { add(it) } }.toString()
        }
        return McpHttpResponse(200, bodyJson)
    }

    private fun errorJson(id: RequestId?, code: Int, message: String): String =
        McpJson.encodeToJsonElement(JSONRPCError(id = id, error = RPCError(code, message))).toString()

    data class McpHttpResponse(val statusCode: Int, val body: String?)

    private companion object {
        const val TAG = "McpHttpTransport"
        const val RESPONSE_TIMEOUT_MS = 30_000L
    }
}
