@file:Suppress("unused")

package dev.rcht.jist.xposed

import android.app.Notification
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Method
import java.security.MessageDigest

// ── Package-level constants ─────────────────────────────────────────

const val TAG = "JistXposed"
const val PROVIDER_PACKAGE = "dev.rcht.jist"
const val AUTHORITY = "dev.rcht.jist.provider"
const val NOTIFICATION_PATH = "notifications"
const val CHAT_SOURCE_PATH = "chat_sources"
const val WATCHED_CHAT_PATH = "watched_chats"
const val CHAT_MESSAGE_PATH = "chat_messages"

const val WECHAT_PACKAGE = "com.tencent.mm"
const val TELEGRAM_PACKAGE = "org.telegram.messenger"
const val TELEGRAM_X_PACKAGE = "org.thunderdog.challegram"
const val NAGRAM_PACKAGE = "xyz.nextalone.nagram"

val bgHandler by lazy { Handler(Looper.getMainLooper()) }

// ── Shared reflection helpers ────────────────────────────────────────

fun findMethod(clazz: Class<*>, name: String, vararg paramTypes: Class<*>?): Method? {
    return try {
        val types = paramTypes.filterNotNull().toTypedArray()
        clazz.getDeclaredMethod(name, *types).apply { isAccessible = true }
    } catch (_: Throwable) { null }
}

fun currentContext(): Context? {
    return try {
        val atClass = Class.forName("android.app.ActivityThread")
        val m = atClass.getDeclaredMethod("currentApplication")
        m.isAccessible = true
        m.invoke(null) as? Context
    } catch (_: Throwable) { null }
}

// ── Shared provider operations ─────────────────────────────────────

fun insertSource(cr: android.content.ContentResolver, baseUri: Uri, pkg: String, name: String): Long {
    val values = ContentValues().apply {
        put("packageName", pkg)
        put("displayName", name)
    }
    val uri = cr.insert(Uri.withAppendedPath(baseUri, CHAT_SOURCE_PATH), values)
    return uri?.lastPathSegment?.toLongOrNull() ?: -1L
}

fun isChatWatched(cr: android.content.ContentResolver, baseUri: Uri, sourceId: Long, chatId: String): Boolean {
    val uri = Uri.withAppendedPath(baseUri, "$WATCHED_CHAT_PATH/query_by_chat")
        .buildUpon()
        .appendQueryParameter("sourceId", sourceId.toString())
        .appendQueryParameter("chatId", chatId)
        .build()
    val cursor = cr.query(uri, null, null, null, null)
    return cursor?.use { it.moveToFirst() && it.getInt(it.getColumnIndexOrThrow("isEnabled")) == 1 } ?: false
}

fun ensureWatchedChat(cr: android.content.ContentResolver, baseUri: Uri, sourceId: Long, chatId: String, chatName: String): Long {
    val values = ContentValues().apply {
        put("sourceId", sourceId)
        put("chatId", chatId)
        put("chatName", chatName)
    }
    val uri = cr.insert(Uri.withAppendedPath(baseUri, WATCHED_CHAT_PATH), values)
    return uri?.lastPathSegment?.toLongOrNull() ?: -1L
}

fun sha256(input: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
}

// ── Main Xposed entry point ────────────────────────────────────────

class JistXposed : XposedModule() {

    override fun onPackageReady(param: PackageReadyParam) {
        XposedHelpers.module = this
        val pkg = param.packageName
        if (pkg == PROVIDER_PACKAGE) return
        if (pkg.startsWith("android.")) return
        if (pkg.startsWith("com.android.")) return

        val loader = param.classLoader
        when (pkg) {
            WECHAT_PACKAGE -> {
                hookNotificationNotify(pkg)
                WeChatHooks.setup(loader, pkg)
            }
            TELEGRAM_X_PACKAGE -> {
                hookNotificationNotify(pkg)
                TelegramHooks.setupTelegramX(loader, pkg)
            }
            NAGRAM_PACKAGE -> {
                hookNotificationNotify(pkg)
                TelegramHooks.setupNagram(loader, pkg, "Nagram")
            }
            TELEGRAM_PACKAGE -> {
                hookNotificationNotify(pkg)
                TelegramHooks.setupNagram(loader, pkg, "Telegram")
            }
            else -> hookNotificationNotify(pkg)
        }
    }

    // ── NotificationManager hook (all apps) ──────────────────────────

    private fun hookNotificationNotify(pkg: String) {
        try {
            val nmClass = Class.forName("android.app.NotificationManager")
            val notifyWithTag = findMethod(nmClass, "notify",
                String::class.java, Int::class.javaPrimitiveType, Notification::class.java)
            if (notifyWithTag != null) {
                hook(notifyWithTag).intercept { chain ->
                    val tag = chain.args[0] as? String
                    val id = chain.args[1] as? Int ?: return@intercept null
                    val notification = chain.args[2] as? Notification ?: return@intercept null
                    captureAndSendNotification(pkg, tag, id, notification)
                    chain.proceed()
                }
            }
            val notifyNoTag = findMethod(nmClass, "notify",
                Int::class.javaPrimitiveType, Notification::class.java)
            if (notifyNoTag != null) {
                hook(notifyNoTag).intercept { chain ->
                    val id = chain.args[0] as? Int ?: return@intercept null
                    val notification = chain.args[1] as? Notification ?: return@intercept null
                    captureAndSendNotification(pkg, null, id, notification)
                    chain.proceed()
                }
            }
            val notifyAsPkg = findMethod(nmClass, "notifyAsPackage",
                String::class.java, String::class.java, Int::class.javaPrimitiveType, Notification::class.java)
            if (notifyAsPkg != null) {
                hook(notifyAsPkg).intercept { chain ->
                    val targetPkg = chain.args[0] as? String ?: return@intercept null
                    val tag = chain.args[1] as? String
                    val id = chain.args[2] as? Int ?: return@intercept null
                    val notification = chain.args[3] as? Notification ?: return@intercept null
                    captureAndSendNotification(targetPkg, tag, id, notification)
                    chain.proceed()
                }
                Log.i(TAG, "notifyAsPackage hook OK")
            }
        } catch (_: Exception) { }
    }

    private fun captureAndSendNotification(pkg: String, tag: String?, id: Int, notification: Notification) {
        try {
            val extras = notification.extras ?: return
            val title = extras.getString(Notification.EXTRA_TITLE, "")
            val content = extras.getString(Notification.EXTRA_TEXT, "")
            if (title.isBlank() && content.isBlank()) return

            val context = currentContext() ?: return

            val appName = try {
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(pkg, 0))?.toString() ?: pkg
            } catch (_: Exception) { pkg }

            val senderName = extras.getString(Notification.EXTRA_SELF_DISPLAY_NAME)
                ?: extras.getString(Notification.EXTRA_SUB_TEXT)

            val values = ContentValues().apply {
                put("packageName", pkg)
                put("appName", appName)
                put("title", title)
                put("content", content)
                if (senderName != null) put("senderName", senderName)
                put("notificationTag", tag ?: "")
                put("notificationId", id)
                put("notificationKey", "$pkg:${tag ?: ""}:$id")
                put("timestamp", System.currentTimeMillis())
                put("contentHash", sha256(content))
            }

            val uri = Uri.parse("content://$AUTHORITY/$NOTIFICATION_PATH")
            bgHandler.post { try { context.contentResolver.insert(uri, values) } catch (_: Exception) { } }
        } catch (_: Exception) { }
    }
}