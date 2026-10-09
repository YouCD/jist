package dev.rcht.jist.llm

import android.content.Context
import android.util.Base64
import android.util.Log
import dev.rcht.jist.data.repository.ChatImageRepository
import dev.rcht.jist.llm.model.ChatMessage
import java.io.File

/**
 * Attaches a chat's captured images to an LLM summary request as vision
 * content. Shared by the manual summarize flow (chat detail) and the
 * automatic periodic summarizer (SummaryWorker).
 *
 * Only jpeg/png are attached — wxgf (HEVC annex-B) files are skipped until
 * they can be decoded without a transcode step.
 */
object ImageAnalysisHelper {

    private const val TAG = "ImageAnalysis"
    const val MAX_ANALYZED_IMAGES = 4

    /**
     * Build a vision user message for images in [timeFrom, timeTo], or null.
     *
     * @param maxWaitMs image capture lags message arrival by a few seconds
     *   (proactive CDN download + capture). When the window is fresh and the
     *   first lookup finds nothing, poll until this deadline.
     */
    suspend fun buildImageMessage(
        context: Context,
        chatImageRepository: ChatImageRepository,
        weChatChatId: String,
        timeFrom: Long,
        timeTo: Long,
        maxWaitMs: Long = 0L
    ): ChatMessage? {
        val deadline = if (maxWaitMs > 0) System.currentTimeMillis() + maxWaitMs else 0L
        while (true) {
            val msg = try {
                buildOnce(context, chatImageRepository, weChatChatId, timeFrom, timeTo)
            } catch (e: Exception) {
                Log.w(TAG, "image analysis prep failed: ${e.message}")
                null
            }
            if (msg != null) return msg
            val now = System.currentTimeMillis()
            // Only wait when the window is fresh (recent messages) and there is
            // still time left; stale windows will never gain images.
            if (deadline == 0L || now >= deadline || now - timeTo > 120_000L) {
                Log.w(TAG, "no attachable image in window for $weChatChatId " +
                    "[timeFrom=$timeFrom timeTo=$timeTo]")
                return null
            }
            kotlinx.coroutines.delay(1_000L)
        }
    }

    private suspend fun buildOnce(
        context: Context,
        chatImageRepository: ChatImageRepository,
        weChatChatId: String,
        timeFrom: Long,
        timeTo: Long
    ): ChatMessage? {
        return try {
            val candidates = chatImageRepository.getByChat(weChatChatId)
                .filter { it.messageTimestamp in timeFrom..timeTo ||
                    it.capturedAt in timeFrom..timeTo }
                .filter { it.format == "jpeg" || it.format == "png" }
                .sortedByDescending { it.capturedAt }
                .take(MAX_ANALYZED_IMAGES)
            if (candidates.isEmpty()) return null
            val images = mutableListOf<String>()
            for (img in candidates) {
                val path = img.storedPath ?: continue
                val f = File(context.filesDir, path)
                if (!f.exists()) continue
                val bytes = f.readBytes()
                if (bytes.isEmpty()) continue
                val mime = if (img.format == "png") "image/png" else "image/jpeg"
                images.add("data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP))
            }
            if (images.isEmpty()) return null
            Log.i(TAG, "attaching ${images.size} image(s) for $weChatChatId")
            ChatMessage(
                role = "user",
                content = "以下是该会话在本次总结时间窗口内出现的图片，请识别每张图片的内容，并在摘要中自然地体现这些信息。",
                images = images
            )
        } catch (e: Exception) {
            Log.w(TAG, "image analysis prep failed: ${e.message}")
            null
        }
    }
}
