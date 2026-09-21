package dev.rcht.jist.llm

import dev.rcht.jist.llm.model.ChatMessage
import dev.rcht.jist.llm.model.LlmError
import dev.rcht.jist.llm.model.LlmResponse

sealed class LlmResult<out T> {
    data class Success<T>(val data: T) : LlmResult<T>()
    data class Error<T>(val error: LlmError) : LlmResult<T>()
}

interface LlmClient {
    /**
     * Send chat messages to LLM and get a response.
     * @param messages List of chat messages (user, system, assistant)
     * @param config Configuration for this request
     * @return LlmResult with response or error
     */
    suspend fun complete(
        messages: List<ChatMessage>,
        config: LlmRequestConfig
    ): LlmResult<LlmResponse>
}

data class LlmRequestConfig(
    val model: String,
    val maxTokens: Int = 1000,
    val temperature: Float = 0.7f,
    val systemPrompt: String? = null,
    val apiKey: String = "",
    val baseUrl: String = "",
    /** Extra HTTP headers added to every request. Overrides built-in headers of the same name (case-insensitive). */
    val extraHeaders: Map<String, String> = emptyMap()
)

/**
 * Parse a multi-line custom header block into a map.
 * Format: one `Key: Value` pair per line; blank lines and lines starting
 * with `#` are ignored. Lines without a colon are skipped.
 */
fun parseCustomHeaders(raw: String): Map<String, String> {
    if (raw.isBlank()) return emptyMap()
    val result = LinkedHashMap<String, String>()
    for (line in raw.lineSequence()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
        val idx = trimmed.indexOf(':')
        if (idx <= 0) continue
        val key = trimmed.substring(0, idx).trim()
        val value = trimmed.substring(idx + 1).trim()
        if (key.isNotEmpty() && value.isNotEmpty()) {
            result[key] = value
        }
    }
    return result
}

/**
 * Count lines in a custom header block that [parseCustomHeaders] would
 * silently drop: non-empty, non-comment lines without a usable
 * "Key: Value" pair. Used for UI validation feedback.
 */
fun countIgnoredHeaderLines(raw: String): Int {
    if (raw.isBlank()) return 0
    var count = 0
    for (line in raw.lineSequence()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
        val idx = trimmed.indexOf(':')
        if (idx <= 0) {
            count++
            continue
        }
        val key = trimmed.substring(0, idx).trim()
        val value = trimmed.substring(idx + 1).trim()
        if (key.isEmpty() || value.isEmpty()) count++
    }
    return count
}

/**
 * Merge user-provided headers into built-in headers.
 * Header names are case-insensitive per the HTTP spec, so a user header
 * overrides a built-in header regardless of letter case; the user's
 * spelling is kept. Prevents duplicate headers like `Content-Type` plus
 * `content-type` being sent in the same request.
 */
fun mergeHeaders(builtIn: Map<String, String>, extra: Map<String, String>): Map<String, String> {
    val result = LinkedHashMap(builtIn)
    if (extra.isEmpty()) return result
    val extraKeys = extra.keys.mapTo(HashSet()) { it.lowercase() }
    result.entries.removeAll { it.key.lowercase() in extraKeys }
    result.putAll(extra)
    return result
}
