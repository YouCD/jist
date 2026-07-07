@file:Suppress("unused")

package dev.rcht.jist.xposed

import android.app.Notification
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
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

class JistXposed : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == PROVIDER_PACKAGE) return
        if (lpparam.packageName.startsWith("android.")) return
        if (lpparam.packageName.startsWith("com.android.")) return

        when (lpparam.packageName) {
            WECHAT_PACKAGE -> {
                hookNotificationNotify(lpparam)
                WeChatHooks.setup(lpparam)
            }
            TELEGRAM_X_PACKAGE -> {
                hookNotificationNotify(lpparam)
                TelegramHooks.setupTelegramX(lpparam)
            }
            NAGRAM_PACKAGE -> {
                hookNotificationNotify(lpparam)
                TelegramHooks.setupNagram(lpparam, "Nagram")
            }
            TELEGRAM_PACKAGE -> {
                hookNotificationNotify(lpparam)
                TelegramHooks.setupNagram(lpparam, "Telegram")
            }
            else -> hookNotificationNotify(lpparam)
        }
    }

    // ── NotificationManager hook (all apps) ──────────────────────────

    private fun hookNotificationNotify(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val nmClass = XposedHelpers.findClass("android.app.NotificationManager", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(nmClass, "notify",
                String::class.java, Int::class.javaPrimitiveType, Notification::class.java,
                notifyHook(lpparam.packageName, hasTag = true))
            XposedHelpers.findAndHookMethod(nmClass, "notify",
                Int::class.javaPrimitiveType, Notification::class.java,
                notifyHook(lpparam.packageName, hasTag = false))
            try {
                XposedHelpers.findAndHookMethod(nmClass, "notifyAsPackage",
                    String::class.java, String::class.java, Int::class.javaPrimitiveType, Notification::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val pkg = param.args[0] as? String ?: return
                            val tag = param.args[1] as? String
                            val id = (param.args[2] as? Int) ?: return
                            val notification = param.args[3] as? Notification ?: return
                            captureAndSendNotification(pkg, tag, id, notification)
                        }
                    })
                Log.i(TAG, "notifyAsPackage hook OK")
            } catch (_: Exception) { }
        } catch (_: Exception) { }
    }

    private fun notifyHook(pkg: String, hasTag: Boolean) = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            captureAndSend(param, pkg, hasTag)
        }
    }

    private fun captureAndSend(param: XC_MethodHook.MethodHookParam, pkg: String, hasTag: Boolean) {
        try {
            val (tag, id, notification) = if (hasTag) {
                Triple(param.args[0] as? String, (param.args[1] as? Int) ?: return,
                       (param.args[2] as? Notification) ?: return)
            } else {
                Triple(null, (param.args[0] as? Int) ?: return,
                       (param.args[1] as? Notification) ?: return)
            }
            captureAndSendNotification(pkg, tag, id, notification)
        } catch (_: Exception) { }
    }

    private fun captureAndSendNotification(pkg: String, tag: String?, id: Int, notification: Notification) {
        try {
            val extras = notification.extras ?: return
            val title = extras.getString(Notification.EXTRA_TITLE, "")
            val content = extras.getString(Notification.EXTRA_TEXT, "")
            if (title.isBlank() && content.isBlank()) return

            val context = XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.app.ActivityThread", null), "currentApplication"
            ) as? Context ?: return

            val appName = try {
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(pkg, 0))?.toString() ?: pkg
            } catch (_: Exception) { pkg }

            val senderName = extras.getString(Notification.EXTRA_SELF_DISPLAY_NAME)
                ?: extras.getString(Notification.EXTRA_SUB_TEXT)

           //if (pkg == WECHAT_PACKAGE) {
           //    Log.d(TAG, "notification: title=$title senderName=$senderName content=${content.take(60)}")
           //}

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
