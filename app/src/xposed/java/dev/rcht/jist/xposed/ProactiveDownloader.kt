package dev.rcht.jist.xposed

import android.os.HandlerThread
import android.os.Handler
import android.util.Log
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Proactively downloads original images through WeChat's own mars CDN stack.
 *
 * On image-message arrival we hold fileid + aeskey (from the message XML).
 * WeChat's real download request (captured via CdnRequestObserver) uses:
 *   fileKey = adownimg_<16hex>_<size>_<seq>_hevc, fileType=2,
 *   customHeader = "source_format:2", bizid=1, chatType=1, msgType=1.
 *
 * We replay that template with our own savePath (a temp file under
 * cache/temp) and move the finished file into image2/, where the normal
 * capture pipeline (scanner / decode hooks) associates it by size.
 *
 * Requires the CdnManager instance WeChat uses, cached by
 * CdnRequestObserver on the first real download (thumbnails auto-download
 * on every message, so this happens within seconds of a message arriving).
 */
object ProactiveDownloader {

    private const val TAG = "JistProactive"
    private const val WECHAT_DATA = "/data/data/com.tencent.mm"
    private const val TEMP_ROOT = "$WECHAT_DATA/cache/temp/jistcdntemp"
    private const val MICRO_MSG_DIR =
        "$WECHAT_DATA/MicroMsg/16ee1528fdafc1668981f4ddc97f3821"

    @Volatile private var cdnManager: Any? = null
    private var requestClass: Class<*>? = null
    private var callbackClass: Class<*>? = null
    private var startMethod: Method? = null
    private val builderSetters = HashMap<String, Method>()
    // fileid -> task, waiting for the CdnManager cache (first real download)
    private val waiting = LinkedHashMap<String, Task>()

    private data class Task(
        val aeskey: String,
        val chatId: String,
        val senderName: String,
        val createTime: Long
    )
    private val ht by lazy { HandlerThread("jist-proactive").apply { start() } }
    private val handler by lazy { Handler(ht.looper) }

    /** Cached by CdnRequestObserver from the manager instance WeChat uses. */
    @Synchronized
    fun cacheManager(instance: Any) {
        if (cdnManager == null) {
            cdnManager = instance
            Log.i(TAG, "CdnManager instance cached")
            flush()
        }
    }

    /** Lazily resolve the mars API surface through the app class loader. */
    private fun ensureApi(loader: ClassLoader) {
        if (startMethod != null) return
        val managerClass = Class.forName("com.tencent.mars.cdn.CdnManager", true, loader)
        val reqClass = Class.forName("com.tencent.mars.cdn.CdnManager\$C2CDownloadRequest", true, loader)
        val cbClass = Class.forName("com.tencent.mars.cdn.CdnManager\$DownloadCallback", true, loader)
        requestClass = reqClass
        callbackClass = cbClass
        startMethod = managerClass.getDeclaredMethod(
            "OnJniStartC2CDownload", reqClass, cbClass).apply { isAccessible = true }
        for (m in reqClass.getDeclaredMethods()) {
            if (m.name.startsWith("set") && !Modifier.isStatic(m.modifiers) && m.parameterCount == 1) {
                m.isAccessible = true
                builderSetters[m.name] = m
            }
        }
        Log.i(TAG, "mars api resolved: ${builderSetters.size} builder setters")
    }

    /** Queue a proactive download for one original image. */
    fun enqueue(fileid: String, aeskey: String, chatId: String, senderName: String, createTime: Long) {
        synchronized(waiting) {
            if (waiting.containsKey(fileid)) return
            waiting[fileid] = Task(aeskey, chatId, senderName, createTime)
        }
        flush()
    }

    /** Start every queued download that can be started now. */
    private fun flush() {
        handler.post {
            val queued = synchronized(waiting) { waiting.toMap() }
            for ((fileid, task) in queued) {
                try {
                    val manager = cdnManager
                    if (manager == null) {
                        // Still no manager — keep queued, flush happens on cache.
                        continue
                    }
                    ensureApi(manager.javaClass.classLoader)
                    val request = buildRequest(fileid, task.aeskey)
                    val cbClass = callbackClass!!
                    val callback = Proxy.newProxyInstance(
                        cbClass.classLoader, arrayOf(cbClass),
                        { _, m, _ -> handleCallback(m.name); null })
                    val result = startMethod!!.invoke(manager, request, callback)
                    // The entry stays in `waiting` until the download completes
                    // (moveTempFile consumes it to recover the message context).
                    Log.i(TAG, "proactive download started: result=$result fileid=${fileid.take(32)}")
                } catch (e: Exception) {
                    Log.w(TAG, "proactive download failed: ${e.message}")
                }
            }
        }
    }

    private fun buildRequest(fileid: String, aeskey: String): Any {
        val req = requestClass!!.getConstructor().newInstance()
        val digest = MessageDigest.getInstance("MD5").digest(fileid.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val fileKey = "adownimg_${digest.take(16)}_0_${System.nanoTime() % 1000000000}_hevc"
        val savePath = "$TEMP_ROOT/$digest"
        fun set(name: String, value: Any?) {
            try {
                builderSetters[name]?.invoke(req, value)
            } catch (e: Exception) {
                Log.w(TAG, "setter $name missing or failed: ${e.message}")
            }
        }
        set("setFileid", fileid)
        set("setAeskey", aeskey)
        set("setFileKey", fileKey)
        set("setSavePath", savePath)
        set("setFileType", 2)
        set("setBizid", 1)
        set("setChatType", 1)
        set("setMsgType", 1)
        set("setCustomHeader", "source_format:2")
        set("setConcurrentCount", 1)
        set("setConnectionCount", 1)
        set("setCertificateVerifyPolicy", 1)
        set("setMaxHttpRedirectCount", 5)
        set("setResolveHostFlags", 5)
        set("setTransforTimeoutSeconds", 300000)
        set("setApptype", 0)
        return try {
            requestClass!!.getMethod("build").invoke(req)
        } catch (e: Exception) {
            req // fall back to the builder itself
        }
    }

    private fun handleCallback(name: String) {
        if (name == "onC2CDownloadCompleted") moveTempFile()
    }

    private fun md5Hex(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun moveTempFile() {
        try {
            val root = java.io.File(TEMP_ROOT)
            if (!root.exists()) root.mkdirs()
            val fresh = root.listFiles()?.filter {
                it.isFile && it.length() > 0 &&
                    System.currentTimeMillis() - it.lastModified() < 30_000
            }?.sortedByDescending { it.lastModified() }?.first() ?: return
            val digest = fresh.name
            // Recover the message context from the queued task (digest = md5(fileid)).
            val entry = waiting.entries.firstOrNull { md5Hex(it.key) == digest }
            val task = entry?.value
            if (entry != null) synchronized(waiting) { waiting.remove(entry.key) }
            val destDir = java.io.File("$MICRO_MSG_DIR/image2/" +
                digest.take(2) + "/" + digest.substring(2, 4))
            destDir.mkdirs()
            val dest = java.io.File(destDir, "$digest.jpg")
            if (dest.exists()) {
                fresh.delete()
                return
            }
            fresh.renameTo(dest)
            Log.i(TAG, "moved proactive download -> $dest (${dest.length()}B)"
                + if (task != null) " chat=${task.chatId}" else "")
            if (task != null) {
                ImageCapture.forceAssociate(dest.absolutePath, task.chatId, task.senderName, task.createTime)
            }
            // Feed the capture pipeline (forced association skips size matching).
            ImageCapture.onPossibleImageOpen(dest.absolutePath)
        } catch (e: Exception) {
            Log.w(TAG, "move failed: ${e.message}")
        }
    }
}
