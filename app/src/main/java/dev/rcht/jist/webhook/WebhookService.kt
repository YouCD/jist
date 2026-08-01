package dev.rcht.jist.webhook

import android.util.Log
import dev.rcht.jist.data.db.entity.SummaryEntity
import dev.rcht.jist.data.preferences.JistPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class WebhookService(private val httpClient: OkHttpClient) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun sendAsync(summary: SummaryEntity, prefs: JistPreferences) {
        if (!prefs.webhookEnabled || prefs.webhookUrl.isBlank()) return
        scope.launch {
            try {
                val bodyText = interpolate(prefs.webhookMessageTemplate, summary)
                val customHeaders = parseCustomHeaders(prefs.webhookCustomHeaders)
                val request = buildRequest(prefs.webhookUrl, prefs.webhookHttpMethod, bodyText, customHeaders)
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w("WebhookService", "HTTP ${response.code} for ${prefs.webhookUrl}")
                    }
                }
            } catch (e: Exception) {
                Log.e("WebhookService", "Failed to send webhook", e)
            }
        }
    }

    private fun interpolate(template: String, summary: SummaryEntity): String {
        return template
            .replace("\${summaryText}", summary.summaryText)
            .replace("\${appName}", summary.appName)
            .replace("\${contactOrGroup}", summary.contactOrGroup)
            .replace("\${messageCount}", summary.messageCount.toString())
            .replace("\${modelUsed}", summary.modelUsed)
            .replace("\${tokenCount}", (summary.tokenCount?.toString() ?: ""))
            .replace("\${createdAt}", summary.createdAt.toString())
            .replace("\${packageName}", summary.packageName)
            .replace("\${summaryId}", summary.id.toString())
    }

    private fun parseCustomHeaders(headersJson: String): Map<String, String> {
        if (headersJson.isBlank()) return emptyMap()
        return try {
            val obj = JSONObject(headersJson)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun sendTestAsync(
        url: String,
        httpMethod: String,
        template: String,
        customHeaders: String,
        onResult: (Result<String>) -> Unit
    ) {
        scope.launch {
            try {
                val bodyText = template
                    .replace("\${summaryText}", "[test] AI summary content")
                    .replace("\${appName}", "Test App")
                    .replace("\${contactOrGroup}", "Test Contact")
                    .replace("\${messageCount}", "5")
                    .replace("\${modelUsed}", "gpt-4o-mini")
                    .replace("\${tokenCount}", "150")
                    .replace("\${createdAt}", System.currentTimeMillis().toString())
                    .replace("\${packageName}", "com.example.test")
                    .replace("\${summaryId}", "0")
                val headers = parseCustomHeaders(customHeaders)
                val request = buildRequest(url, httpMethod, bodyText, headers)
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        onResult(Result.success("HTTP ${response.code}: $body"))
                    } else {
                        onResult(Result.failure(Exception("HTTP ${response.code}: $body")))
                    }
                }
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    private fun buildRequest(url: String, method: String, bodyText: String, headers: Map<String, String>): Request {
        val builder = Request.Builder().url(url)
        headers.forEach { (key, value) -> builder.addHeader(key, value) }
        return when (method.uppercase()) {
            "GET" -> builder.get().build()
            "PUT" -> builder.put(bodyText.toRequestBody(jsonMediaType)).build()
            else -> builder.post(bodyText.toRequestBody(jsonMediaType)).build()
        }
    }
}
