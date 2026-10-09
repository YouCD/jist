@file:Suppress("unused")

package dev.rcht.jist.xposed

import android.content.ContentValues
import android.net.Uri
import android.os.HandlerThread
import android.util.Log
import io.github.libxposed.api.XposedModule
import java.io.File
import java.security.MessageDigest

/**
 * Captures WeChat chat images as they are written to the local image cache.
 *
 * Key finding (2026-10, WeChat 8.0.x on Android 16):
 *  - Chat images are stored PLAINTEXT under
 *    /data/data/com.tencent.mm/MicroMsg/<uhash>/image2/
 *  - th_<hash>        -> thumbnail, plain JPEG
 *  - <hash>.jpg/.png  -> original, plain JPEG/PNG or "wxgf" HEVC container
 *  - <hash>hd         -> HD thumbnail variant, plain JPEG
 *  - *.cdninfo under cdn/download/ are metadata descriptors (mostly zero-filled),
 *    NOT the image payload.
 *  - No decryption is needed; we only detect the write and copy the file.
 *
 * Delivery:
 *  1. preferred: `su 0 cp` the file into jist's files/inbox dir (needs
 *     com.tencent.mm on the KernelSU su allowlist)
 *  2. fallback: byte[] through the ContentProvider (files <= INLINE_MAX)
 */
object ImageCapture {

    private const val INLINE_MAX = 700_000
    private const val READ_DELAY_MS = 2500L
    private const val PENDING_WINDOW_MS = 120_000L
    private const val BACKFILL_MARKER = "/data/local/tmp/jist_image_backfill"

    private data class PendingImageMsg(
        val chatId: String,
        val senderName: String,
        val createTime: Long,
        val thumbLen: Int,
        val hdLen: Int,
        val fullLen: Int,
        val fullFileid: String,
        val fullAeskey: String
    )

    private val pendingMsgs = ArrayDeque<PendingImageMsg>()
    private val seenPaths = LinkedHashMap<String, Long>()
    // base-image hash -> matched message, so th_<hash>hd inherits the match
    // of th_<hash> (the local hd file size never equals the CDN hdlength)
    private val baseMatches = LinkedHashMap<String, PendingImageMsg>()
    // path -> message for proactive downloads: the downloader knows exactly
    // which message its fileid belongs to, no size guessing needed.
    private val forceMatches = java.util.concurrent.ConcurrentHashMap<String, PendingImageMsg>()

    /** Register a known association for a file the proactive downloader wrote. */
    fun forceAssociate(path: String, chatId: String, senderName: String, createTime: Long) {
        forceMatches[path] = PendingImageMsg(chatId, senderName, createTime, 0, 0, 0, "", "")
    }
    private val handlerThread by lazy { HandlerThread("jist-image-capture").apply { start() } }
    private val handler by lazy { android.os.Handler(handlerThread.looper) }
    private var backfillArmed = false

    fun registerPendingMessage(chatId: String, senderName: String, createTime: Long, thumbLen: Int, hdLen: Int, fullLen: Int, fullFileid: String, fullAeskey: String) {
        synchronized(pendingMsgs) {
            pendingMsgs.addLast(PendingImageMsg(chatId, senderName, createTime, thumbLen, hdLen, fullLen, fullFileid, fullAeskey))
            val cutoff = System.currentTimeMillis() - PENDING_WINDOW_MS
            while (pendingMsgs.isNotEmpty() && pendingMsgs.first().createTime < cutoff) pendingMsgs.removeFirst()
        }
        // Proactively fetch the original image through WeChat's own mars CDN
        // stack instead of waiting for the user to open the image.
        if (fullFileid.isNotEmpty() && fullAeskey.isNotEmpty()) {
            ProactiveDownloader.enqueue(fullFileid, fullAeskey, chatId, senderName, createTime)
        }
    }

    fun setup(loader: ClassLoader) {
        hookImageDecoders(loader) // render-time path detection
        armBackfillWatcher()
        Log.i(TAG, "ImageCapture hooks installed (file-read + decode hooks; proactive downloader covers originals)")
    }

    // ── Image-decode hooks (framework layer, no obfuscation) ─────────

    /**
     * When WeChat renders an image it decodes the file into a bitmap via
     * framework entry points; hooking them yields the exact path at the
     * exact moment of the read.
     */
    private fun hookImageDecoders(loader: ClassLoader) {
        try {
            val bm = Class.forName("android.graphics.BitmapFactory")
            XposedHelpers.findAndHookMethod(bm, "decodeFile", String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val path = param.args[0] as? String ?: return
                        onPossibleImageOpen(path)
                    }
                })
            Log.i(TAG, "BitmapFactory.decodeFile hook OK")
        } catch (e: Exception) {
            Log.w(TAG, "BitmapFactory hook failed: ${e.message}")
        }
        try {
            val idec = XposedHelpers.findClass("android.graphics.ImageDecoder", loader)
            XposedHelpers.findAndHookMethod(idec, "decodeFile", String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val path = param.args[0] as? String ?: return
                        onPossibleImageOpen(path)
                    }
                })
            Log.i(TAG, "ImageDecoder.decodeFile hook OK")
        } catch (e: Exception) {
            Log.w(TAG, "ImageDecoder hook failed: ${e.message}")
        }
    }


    fun onPossibleImageOpen(path: String) {
        if (!path.contains("/image2/")) return
        val name = path.substringAfterLast('/')
        if (name.isEmpty()) return
        if (name.contains("\u2316")) return          // ⌖ sidecar metadata files
        if (name.startsWith(".ref")) return
        if (name.endsWith("_backup")) return
        if (name.endsWith("_link")) return
        val isThumb = name.startsWith("th_")
        val isOriginal = name.endsWith(".jpg") || name.endsWith(".png") ||
            name.endsWith("_hd") || name.endsWith("_tmp.jpg") ||
            (name.length == 32 && name.all { it.isDigit() || it in 'a'..'f' })
        if (!isThumb && !isOriginal) return

        val now = System.currentTimeMillis()
        synchronized(seenPaths) {
            seenPaths[path] = now
            while (seenPaths.size > 512) {
                val first = seenPaths.entries.first()
                if (now - first.value > 60_000L) seenPaths.remove(first.key) else break
            }
        }
        Log.i(TAG, "image write detected: $name (${path.substringAfter("image2/")})")
        handler.postDelayed({ capture(path) }, READ_DELAY_MS)
    }

    // ── Capture & deliver ────────────────────────────────────────────

    private fun capture(path: String) {
        try {
            val f = File(path)
            if (!f.exists()) {
                Log.w(TAG, "file vanished before capture: $path")
                return
            }
            val bytes = f.readBytes()
            if (bytes.isEmpty()) return
            val fmt = sniffFormat(bytes)
            if (fmt == null) {
                Log.d(TAG, "skipping non-image content: $path")
                return
            }
            val hash = sha256Hex(bytes)
            val name = f.name
            val baseHash = name.removePrefix("th_").removeSuffix("hd")
                .removeSuffix(".jpg").removeSuffix(".png").removeSuffix("_tmp.jpg")
            val matched = forceMatches.remove(path)
                ?: matchPendingMessage(bytes.size, f.lastModified())
                ?: if (baseHash.length == 32) baseMatches[baseHash] else null
            Log.i(TAG, "captured $name fmt=$fmt size=${bytes.size} hash=${hash.take(12)} matched=${matched?.chatId}")
            if (matched != null && !name.endsWith("hd")) {
                synchronized(baseMatches) {
                    baseMatches[baseHash] = matched
                    while (baseMatches.size > 256) baseMatches.remove(baseMatches.keys.first())
                }
            }

            val delivered = if (bytes.size <= INLINE_MAX) {
                deliverInline(name, fmt, bytes, matched)
            } else {
                deliverViaRootCopy(path, name, fmt, bytes.size, hash, matched)
            }
            if (delivered) Log.i(TAG, "image delivered: $name")
            else Log.w(TAG, "image delivery failed (su allowlist? size?): $name")
        } catch (e: Exception) {
            Log.w(TAG, "capture error for $path: ${e.message}")
        }
    }

    private fun sniffFormat(b: ByteArray): String? = when {
        b.size > 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> "jpeg"
        b.size > 8 && b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte() && b[3] == 0x47.toByte() -> "png"
        b.size > 4 && b[0] == 0x77.toByte() && b[1] == 0x78.toByte() && b[2] == 0x67.toByte() && b[3] == 0x66.toByte() -> "wxgf"
        else -> null
    }

    /**
     * Exact-size match against pending image messages. On ties (same byte
     * size for different messages) the candidate whose createTime is closest
     * to the file's write time wins — the download lands seconds after the
     * message arrives, never before.
     */
    private fun matchPendingMessage(sizeBytes: Int, fileMtime: Long): PendingImageMsg? {
        synchronized(pendingMsgs) {
            var best: PendingImageMsg? = null
            var bestGap = Long.MAX_VALUE
            for (m in pendingMsgs) {
                val len = when {
                    m.thumbLen == sizeBytes -> m.thumbLen
                    m.hdLen == sizeBytes -> m.hdLen
                    m.fullLen == sizeBytes -> m.fullLen
                    else -> 0
                }
                if (len == 0) continue
                val gap = fileMtime - m.createTime
                // File is written after the message: penalize negative gaps hard.
                val score = if (gap >= 0) gap else gap * 10
                if (score < bestGap) { bestGap = score; best = m }
            }
            return best
        }
    }

    private fun deliverInline(name: String, fmt: String, bytes: ByteArray, m: PendingImageMsg?): Boolean {
        val context = currentContext() ?: return false
        val cv = ContentValues().apply {
            fillImageFields(this, name, fmt, bytes.size, sha256Hex(bytes), null, m)
            put("imageBytes", bytes)
        }
        return insertImage(context, cv)
    }

    private fun deliverViaRootCopy(
        srcPath: String, name: String, fmt: String, size: Int, hash: String, m: PendingImageMsg?
    ): Boolean {
        val context = currentContext() ?: return false
        val ext = when (fmt) { "jpeg" -> "jpg"; "png" -> "png"; else -> "bin" }
        val dst = "/data/data/$PROVIDER_PACKAGE/files/inbox/$hash.$ext"
        val ok = runShell("su", "0", "cp", srcPath, dst) && runShell("su", "0", "chmod", "660", dst)
        if (!ok) {
            Log.w(TAG, "su cp failed for $name — add com.tencent.mm to KernelSU su allowlist")
            return false
        }
        val cv = ContentValues().apply {
            fillImageFields(this, name, fmt, size, hash, "inbox/$hash.$ext", m)
        }
        return insertImage(context, cv)
    }

    private fun fillImageFields(
        cv: ContentValues, name: String, fmt: String, size: Int, hash: String,
        storedPath: String?, m: PendingImageMsg?
    ) {
        cv.put("fileName", name)
        cv.put("format", fmt)
        cv.put("sizeBytes", size)
        cv.put("contentHash", hash)
        if (storedPath != null) cv.put("storedPath", storedPath)
        cv.put("sourcePath", "")
        cv.put("chatAppKey", WECHAT_PACKAGE)
        cv.put("capturedAt", System.currentTimeMillis())
        if (m != null) {
            cv.put("chatId", m.chatId)
            cv.put("senderName", m.senderName)
            cv.put("messageTimestamp", m.createTime)
        } else {
            cv.put("chatId", "")
            cv.put("senderName", "")
            cv.put("messageTimestamp", 0L)
        }
    }

    private fun insertImage(context: android.content.Context, cv: ContentValues): Boolean {
        return try {
            val uri = Uri.parse("content://$AUTHORITY/$IMAGE_PATH")
            context.contentResolver.insert(uri, cv) != null
        } catch (e: Exception) {
            Log.w(TAG, "image insert failed: ${e.message}")
            false
        }
    }

    private fun runShell(vararg argv: String): Boolean {
        return try {
            ProcessBuilder(*argv).start().waitFor() == 0
        } catch (_: Exception) { false }
    }

    // ── Backfill of the existing image2 stock ───────────────────────

    private fun armBackfillWatcher() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                maybeBackfill()
                handler.postDelayed(this, 60_000L)
            }
        }, 30_000L)
    }

    private fun maybeBackfill() {
        try {
            val marker = File(BACKFILL_MARKER)
            if (!marker.exists() || backfillArmed) return
            backfillArmed = true
            Log.i(TAG, "backfill marker found — scanning image2/")
            handler.postDelayed({
                backfillImages()
                marker.delete()
                backfillArmed = false
            }, 5_000L)
        } catch (e: Exception) {
            Log.w(TAG, "backfill watcher error: ${e.message}")
        }
    }

    private fun backfillImages() {
        val context = currentContext() ?: return
        val mmRoot = File("/data/data/com.tencent.mm/MicroMsg").listFiles() ?: return
        var total = 0
        for (dir in mmRoot) {
            if (!dir.isDirectory) continue
            val image2 = File(dir, "image2")
            if (!image2.exists()) continue
            val files = image2.walkTopDown().filter { it.isFile }.toList()
            Log.i(TAG, "backfill: ${files.size} files in ${dir.name}")
            for (f in files) {
                val name = f.name
                if (name.contains("\u2316") || name.startsWith(".ref") ||
                    name.endsWith("_backup") || name.endsWith("_link")) continue
                if (name.startsWith("th_") || name.endsWith(".jpg") || name.endsWith(".png") ||
                    name.endsWith("_hd") || name.endsWith("_tmp.jpg") ||
                    (name.length == 32 && name.all { it.isDigit() || it in 'a'..'f' })) {
                    capture(f.absolutePath)
                    total++
                    if (total % 200 == 0) Log.i(TAG, "backfill progress: $total")
                }
            }
        }
        Log.i(TAG, "backfill complete: $total files captured")
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun sha256Hex(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b)
            .joinToString("") { "%02x".format(it) }

    private const val TAG = "JistImageCapture"
    const val IMAGE_PATH = "chat_images"
}
