@file:Suppress("unused")

package dev.rcht.jist.xposed

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import io.github.libxposed.api.XposedModule
import java.lang.ClassLoader

object WeChatHooks {
    internal val contactCache = mutableMapOf<String, String>()
    internal val chatNameCache = mutableMapOf<String, String>()
    internal var weChatDb: Any? = null

    fun setup(loader: ClassLoader, pkg: String) {
        Log.i(TAG, "Attempting WeChat hooks for $pkg")
        hookWeChatAddMessage(loader, pkg)
    }

    private fun hookWeChatAddMessage(loader: ClassLoader, pkg: String) {
        hookWeChatMsgInfoStorage(loader, pkg)
        hookWeChatDbInsert(loader, pkg)
    }

    private fun hookWeChatMsgInfoStorage(loader: ClassLoader, pkg: String) {
        val storageCandidates = listOf(
            "com.tencent.mm.storage.a9",
            "com.tencent.mm.storage.bg",
            "com.tencent.mm.storage.bf",
            "com.tencent.mm.model.bf",
            "com.tencent.mm.model.bd",
        )
        for (clz in storageCandidates) {
            try {
                val cls = loader.loadClass(clz)
                for (m in cls.declaredMethods) {
                    val ptypes = m.parameterTypes
                    if (ptypes.size == 1 && ContentValues::class.java.isAssignableFrom(ptypes[0])) {
                        XposedHelpers.findAndHookMethod(cls, m.name,
                            ContentValues::class.java,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    val cv = param.args[0] as? ContentValues ?: return
                                    val talker = cv.getAsString("talker") ?: return
                                    Log.d(TAG, "MsgInfoStorage hook: $clz.${m.name} talker=$talker")
                                    onWeChatMessageRow(pkg, cv)
                                }
                            })
                        Log.i(TAG, "MsgInfoStorage hook OK: $clz.${m.name}")
                        return
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun hookWeChatDbInsert(loader: ClassLoader, pkg: String) {
        try {
            val dbClass = XposedHelpers.findClass(
                "com.tencent.wcdb.database.SQLiteDatabase", loader)

            try {
                XposedHelpers.findAndHookMethod(dbClass, "insertOrThrow",
                    String::class.java, String::class.java, ContentValues::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val table = param.args[0] as? String ?: return
                            if (table != "message") return
                            val cv = param.args[2] as? ContentValues ?: return
                            onWeChatMessageRow(pkg, cv)
                        }
                    })
                Log.i(TAG, "WCDB hook: insertOrThrow(String,String,ContentValues)")
            } catch (_: Exception) { }

            try {
                XposedHelpers.findAndHookMethod(dbClass, "insertWithOnConflict",
                    String::class.java, String::class.java, ContentValues::class.java,
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val table = param.args[0] as? String ?: return
                            val cv = param.args[2] as? ContentValues ?: return
                            if (table == "message" && weChatDb == null) {
                                weChatDb = param.thisObject
                                Log.d(TAG, "db instance captured via insertWithOnConflict")
                                loadContactCache()
                            }
                            when (table) {
                                "message" -> onWeChatMessageRow(pkg, cv)
                                "rcontact" -> onWeChatContactRow(cv)
                                "rconversation" -> onWeChatConversationRow(cv)
                                "chatroom" -> onWeChatChatroomRow(cv)
                            }
                        }
                    })
                Log.i(TAG, "WCDB hook: insertWithOnConflict(String,String,ContentValues,int)")
            } catch (_: Exception) { }

            try {
                XposedHelpers.findAndHookMethod(dbClass, "execSQL",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val sql = param.args[0] as? String ?: return
                            if (sql.contains("INSERT") && sql.contains("message")) {
                                Log.d(TAG, "WCDB execSQL INSERT message: ${sql.take(120)}")
                            }
                        }
                    })
                Log.i(TAG, "WCDB hook: execSQL(String)")
            } catch (_: Exception) { }
        } catch (e: Exception) {
            Log.w(TAG, "WeChat WCDB hook init failed: ${e.message}")
        }
    }

    private fun resolveContact(rawSender: String): String {
        contactCache[rawSender]?.let { if (it.isNotEmpty()) return it }
        val db = weChatDb ?: return rawSender
        try {
            val safeSender = rawSender.replace("'", "''")
            for (col in listOf("username", "alias", "wechatID")) {
                try {
                    val rawResult = XposedHelpers.callMethod(db, "rawQuery",
                        "SELECT nickname, conRemark FROM rcontact WHERE $col='$safeSender' LIMIT 1",
                        null) ?: continue
                    val c = rawResult as? Cursor ?: continue
                    var foundName: String? = null
                    c.use { cur ->
                        if (cur.moveToFirst()) {
                            val remarkIdx = cur.getColumnIndex("conRemark")
                            val nickIdx = cur.getColumnIndex("nickname")
                            foundName = if (remarkIdx >= 0 && !cur.isNull(remarkIdx)) {
                                val r = cur.getString(remarkIdx)
                                if (!r.isNullOrBlank()) r else null
                            } else if (nickIdx >= 0 && !cur.isNull(nickIdx)) {
                                val n = cur.getString(nickIdx)
                                if (!n.isNullOrBlank()) n else null
                            } else null
                        }
                    }
                    if (foundName != null) {
                        contactCache[rawSender] = foundName
                        Log.d(TAG, "contact resolved via $col: $rawSender -> $foundName")
                        return foundName
                    }
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
        return rawSender
    }

    private fun loadContactCache() {
        try {
            val db = weChatDb ?: return
            val cursor = XposedHelpers.callMethod(db, "rawQuery",
                "SELECT username, nickname, conRemark FROM rcontact",
                null) as? Cursor ?: return
            cursor.use { c ->
                val userIdx = c.getColumnIndex("username")
                val nickIdx = c.getColumnIndex("nickname")
                val remarkIdx = c.getColumnIndex("conRemark")
                if (userIdx < 0) {
                    Log.w(TAG, "loadContactCache: no username column")
                    return
                }
                var count = 0
                while (c.moveToNext()) {
                    val wxid = c.getString(userIdx) ?: continue
                    if (wxid in contactCache) continue
                    val nick = if (nickIdx >= 0) c.getString(nickIdx) else null
                    val remark = if (remarkIdx >= 0) c.getString(remarkIdx) else null
                    val name = if (!remark.isNullOrBlank()) remark else if (!nick.isNullOrBlank()) nick else continue
                    contactCache[wxid] = name
                    count++
                }
                Log.i(TAG, "loadContactCache: cached $count contacts (total ${contactCache.size})")
            }
            loadChatNameCache()
        } catch (e: Exception) {
            Log.w(TAG, "loadContactCache failed: ${e.message}")
        }
    }

    private fun onWeChatMessageRow(pkg: String, cv: ContentValues) {
        try {
            val talker = cv.getAsString("talker")
            val content = cv.getAsString("content")
            val createTime = cv.getAsLong("createTime")
            val msgSvrId = cv.getAsLong("msgSvrId")
            val type = cv.getAsInteger("type")
            if (talker == null || content == null || createTime == null) return

            val senderName: String
            var messageContent: String
            val colonIdx = content.indexOf(":\n")
            if (colonIdx > 0 && colonIdx < 50) {
                val rawSender = content.substring(0, colonIdx)
                senderName = resolveContact(rawSender)
                messageContent = content.substring(colonIdx + 2)
            } else {
                val firstColon = content.indexOf(':')
                if (firstColon > 0 && firstColon < 50 && content.contains("*#*\n")) {
                    val rawSender = content.substring(0, firstColon)
                    senderName = resolveContact(rawSender)
                    val nlIdx = content.indexOf('\n', firstColon)
                    messageContent = if (nlIdx > 0) content.substring(nlIdx + 1) else content
                } else {
                    senderName = ""
                    messageContent = content
                }
            }

            val rawXml: String? = if (messageContent.startsWith("<?xml") || messageContent.startsWith("<msg")) {
                val original = messageContent
                val extracted = extractXmlTitle(messageContent)
                if (extracted != null) messageContent = extracted
                original
            } else null

            if (senderName.isEmpty()) {
                Log.w(TAG, "skip message with empty sender from chat=$talker")
                return
            }


            val context = currentContext() ?: return

            val msgSvrIdVal = msgSvrId ?: 0L

            bgHandler.post {
                saveWeChatMessage(context, pkg, talker, senderName, messageContent, createTime, msgSvrIdVal, type ?: 0, rawXml)
            }
        } catch (e: Exception) {
            Log.w(TAG, "onWeChatMessageRow error: ${e.message}")
        }
    }

    private fun onWeChatContactRow(cv: ContentValues) {
        try {
            Log.d(TAG, "rcontact insert keys=${cv.keySet()}")
            val username = cv.getAsString("username") ?: cv.getAsString("wxid") ?: return
            val nickname = cv.getAsString("nickname")
            val conRemark = cv.getAsString("conRemark")
            val name = conRemark ?: nickname
            if (name != null) {
                contactCache[username] = name
                Log.d(TAG, "contact cached: $username -> $name")
            }
        } catch (_: Exception) { }
    }

    private fun onWeChatConversationRow(cv: ContentValues) {
        try {
            val chatId = cv.getAsString("username") ?: return
            val chatName = NAME_COLUMN_CANDIDATES.firstNotNullOfOrNull { cv.getAsString(it) }
            if (chatName.isNullOrBlank() || chatName == chatId) return
            chatNameCache[chatId] = chatName
            Log.d(TAG, "conversation name cached from live insert: $chatId -> $chatName")
        } catch (_: Exception) { }
    }

    private fun onWeChatChatroomRow(cv: ContentValues) {
        try {
            val chatId = cv.getAsString("username") ?: cv.getAsString("chatroomname") ?: return
            val displayName = cv.getAsString("displayname")
            if (!displayName.isNullOrBlank() && displayName != chatId &&
                !displayName.contains(",") && !displayName.contains("\n")) {
                chatNameCache[chatId] = displayName
                Log.d(TAG, "chatroom name cached from live insert: $chatId -> $displayName")
            }
        } catch (_: Exception) { }
    }

    private fun saveWeChatMessage(
        context: Context, pkg: String, chatId: String, senderName: String,
        content: String, timestamp: Long, msgSeq: Long, msgType: Int,
        rawData: String? = null
    ) {
        try {
            val cr = context.contentResolver
            val baseUri = Uri.parse("content://$AUTHORITY")

            val sourceId = insertSource(cr, baseUri, pkg, "微信")
            if (sourceId < 0) return

            val resolvedChatName = chatNameCache[chatId] ?: resolveChatNameFromDb(chatId)
            Log.d(TAG, "[微信] [$resolvedChatName] $senderName ${content.take(60)}")
            val watchedChatId = ensureWatchedChat(cr, baseUri, sourceId, chatId, resolvedChatName)
            if (watchedChatId < 0) return

            val values = ContentValues().apply {
                put("watchedChatId", watchedChatId)
                put("senderName", senderName)
                put("content", content)
                put("timestamp", timestamp)
                put("msgType", msgType)
                put("msgSeq", msgSeq)
                put("chatAppKey", pkg)
                put("chatId", chatId)
                if (rawData != null) put("rawData", rawData)
            }
            cr.insert(Uri.withAppendedPath(baseUri, CHAT_MESSAGE_PATH), values)
        } catch (_: Exception) { }
    }

}
