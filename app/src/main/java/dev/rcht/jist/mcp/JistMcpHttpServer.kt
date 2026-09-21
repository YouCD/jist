package dev.rcht.jist.mcp

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking

/**
 * Minimal HTTP layer for the MCP endpoint (NanoHTTPD).
 *
 * Routes (MCP Streamable HTTP, stateless mode):
 * - `POST /mcp`   -> JSON-RPC message/batch (the only stateful endpoint)
 * - `DELETE /mcp` -> 200 no-op (no server-side session state to tear down)
 * - `OPTIONS /mcp`-> CORS preflight (for LAN browser-based clients)
 * - everything else -> 404 / 405
 *
 * Auth: `Authorization: Bearer <token>`; the token is supplied by a lambda so
 * token rotation in settings takes effect without a server restart.
 */
class JistMcpHttpServer(
    private val transport: McpHttpTransport,
    private val expectedToken: () -> String,
    private val loopbackOnly: Boolean,
    hostname: String,
    port: Int,
) : NanoHTTPD(hostname, port) {

    override fun serve(session: IHTTPSession): Response {
        return try {
            route(session)
        } catch (e: Exception) {
            Log.e(TAG, "error handling request", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_JSON,
                """{"jsonrpc":"2.0","id":null,"error":{"code":-32603,"message":"Internal error"}}"""
            )
        }
    }

    private fun route(session: IHTTPSession): Response {
        if (session.uri != MCP_PATH) {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Not found. Use POST /mcp (MCP Streamable HTTP)."
            )
        }

        val token = expectedToken()
        if (token.isNotEmpty()) {
            val auth = session.headers.entries
                .firstOrNull { it.key.equals("Authorization", ignoreCase = true) }
                ?.value
            if (auth != "Bearer $token") {
                return newFixedLengthResponse(
                    Response.Status.UNAUTHORIZED,
                    MIME_JSON,
                    """{"error":"unauthorized","message":"Expected header: Authorization: Bearer <token>"}"""
                )
            }
        } else if (!loopbackOnly) {
            // No token configured but the server is reachable from the LAN:
            // refuse rather than exposing chat data unauthenticated.
            return newFixedLengthResponse(
                Response.Status.UNAUTHORIZED,
                MIME_JSON,
                """{"error":"unauthorized","message":"No token configured; set one in Settings or disable LAN access"}"""
            )
        }
        // Empty token + loopback bind: allow (localhost-only by default).

        return when (session.method) {
            Method.POST -> {
                val body = readBody(session)
                if (body.isBlank()) {
                    newFixedLengthResponse(
                        Response.Status.BAD_REQUEST,
                        MIME_JSON,
                        """{"jsonrpc":"2.0","id":null,"error":{"code":-32700,"message":"Parse error: empty body"}}"""
                    )
                } else {
                    val result = runBlocking { transport.handlePost(body) }
                    if (result.body == null) {
                        // 202 Accepted, no body (notification-only post)
                        newFixedLengthResponse(
                            if (result.statusCode == 202) Response.Status.ACCEPTED else Response.Status.OK,
                            MIME_JSON,
                            ""
                        )
                    } else {
                        newFixedLengthResponse(
                            if (result.statusCode == 200) Response.Status.OK else Response.Status.BAD_REQUEST,
                            MIME_JSON,
                            result.body
                        )
                    }
                }
            }

            Method.DELETE -> newFixedLengthResponse(Response.Status.OK, MIME_JSON, "{}")

            Method.OPTIONS -> {
                val resp = newFixedLengthResponse(Response.Status.OK, MIME_JSON, "{}")
                addCorsHeaders(resp)
                resp
            }

            else -> newFixedLengthResponse(
                Response.Status.METHOD_NOT_ALLOWED,
                MIME_PLAINTEXT,
                "Method not allowed. Use POST /mcp."
            )
        }
    }

    /**
     * Read exactly Content-Length bytes. Reading to EOF instead would block on
     * keep-alive connections (the client never closes the socket after the body).
     */
    private fun readBody(session: IHTTPSession): String {
        val len = session.headers.entries
            .firstOrNull { it.key.equals("content-length", ignoreCase = true) }
            ?.value?.toIntOrNull() ?: return ""
        if (len <= 0) return ""
        val max = minOf(len, 4 * 1024 * 1024) // cap: 4 MB
        val buf = ByteArray(minOf(max, 8192))
        var remaining = minOf(len, max)
        val sb = StringBuilder(len.coerceAtMost(max))
        while (remaining > 0) {
            val n = session.inputStream.read(buf, 0, minOf(buf.size, remaining))
            if (n < 0) break
            sb.append(String(buf, 0, n, Charsets.UTF_8))
            remaining -= n
        }
        return sb.toString()
    }

    private fun addCorsHeaders(resp: Response) {
        resp.addHeader("Access-Control-Allow-Origin", "*")
        resp.addHeader("Access-Control-Allow-Methods", "POST, GET, DELETE, OPTIONS")
        resp.addHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Mcp-Session-Id")
        resp.addHeader("Access-Control-Max-Age", "86400")
    }

    companion object {
        const val MCP_PATH = "/mcp"
        const val TAG = "JistMcpHttpServer"

        // NanoHTTPD 2.3.1 only ships MIME_PLAINTEXT / MIME_HTML.
        const val MIME_JSON = "application/json"
    }
}
