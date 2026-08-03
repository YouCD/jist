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
    private val contactCache = mutableMapOf<String, String>()
    private val chatNameCache = mutableMapOf<String, String>()
    private var weChatDb: Any? = null

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

    private fun loadChatNameCache() {
        try {
            val db = weChatDb ?: return
            val candidates = mutableListOf<String>()
            try {
                val raw = XposedHelpers.callMethod(db, "rawQuery",
                    "SELECT name FROM sqlite_master WHERE type='table' AND (name LIKE '%chat%' OR name LIKE '%room%' OR name LIKE '%group%' OR name LIKE '%convers%')",
                    null)
                val c = raw as? Cursor
                c?.use { while (it.moveToNext()) candidates.add(it.getString(0)) }
            } catch (_: Exception) { }
            Log.i(TAG, "Chat table candidates: $candidates")

            val priorityOrder = listOf("rcontact", "rconversation", "conversation", "BizChatInfo")
            val sortedTables = candidates.sortedWith(compareBy { tbl ->
                val idx = priorityOrder.indexOf(tbl)
                if (idx >= 0) idx else Int.MAX_VALUE
            })
            val skipTables = setOf("chatroom")

            for (table in sortedTables) {
                if (table in skipTables) {
                    Log.d(TAG, "loadChatNameCache: skipping $table (displayname is member list, not group name)")
                    continue
                }
                val allCols = try {
                    val schemaResult = XposedHelpers.callMethod(db, "rawQuery",
                        "PRAGMA table_info($table)", null)
                    val schemaC = schemaResult as? Cursor ?: continue
                    val cols = mutableListOf<String>()
                    schemaC.use { s -> while (s.moveToNext()) { cols.add(s.getString(s.getColumnIndexOrThrow("name"))) } }
                    cols
                } catch (_: Exception) { continue }

                val idCol = allCols.firstOrNull { cn ->
                    ID_COLUMN_CANDIDATES.any { it.equals(cn, ignoreCase = true) }
                } ?: continue

                val nameCol = allCols.firstOrNull { cn ->
                    !cn.equals(idCol, ignoreCase = true) && nameColsIgnoreCase(cn)
                } ?: continue

                try {
                    val rawResult = XposedHelpers.callMethod(db, "rawQuery",
                        "SELECT $idCol, $nameCol FROM $table WHERE $nameCol IS NOT NULL AND $nameCol != '' LIMIT 200",
                        null) ?: continue
                    val cursor = rawResult as? Cursor ?: continue
                    var count = 0
                    cursor.use { c ->
                        val idIdx = c.getColumnIndex(idCol)
                        val cnIdx = c.getColumnIndex(nameCol)
                        if (idIdx < 0) return@use
                        while (c.moveToNext()) {
                            val id = c.getString(idIdx) ?: continue
                            if (id in chatNameCache) continue
                            if (cnIdx < 0 || c.isNull(cnIdx)) continue
                            val name = c.getString(cnIdx)
                            if (name.isNullOrBlank()) continue
                            chatNameCache[id] = name
                            count++
                        }
                    }
                    if (count > 0) Log.i(TAG, "ChatNameCache($table:$idCol/$nameCol): cached $count")
                } catch (t: Throwable) {
                    Log.w(TAG, "ChatNameCache($table): ${t.message}")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "loadChatNameCache: ${t.message}")
        }
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

    // ── Chat name resolution helpers (WeChat-specific) ────────────────

    private val NAME_COLUMN_CANDIDATES = listOf(
        "ChatRoomName", "chatroomname", "chatRoomName", "RoomName", "roomname", "roomName",
        "chatname", "chatName", "ChatName",
        "displayname", "displayName",
        "conversationName", "conversationname",
        "nickname", "NickName", "nickName",
        "name", "Name",
        "conRemark", "con_remark",
        "groupName", "groupname", "groupNick", "groupnick", "groupNickname", "groupnickname",
    )

    private val ID_COLUMN_CANDIDATES = listOf(
        "username", "wxid", "roomid", "chatroomname", "roomname",
        "id", "_id", "bizChatLocalId", "brandUserName",
    )

    private fun nameColsIgnoreCase(cn: String): Boolean {
        return NAME_COLUMN_CANDIDATES.any { it.equals(cn, ignoreCase = true) }
    }

    private fun resolveChatNameFromDb(chatId: String): String {
        chatNameCache[chatId]?.let { if (it.isNotBlank()) return it }
        contactCache[chatId]?.let {
            if (it.isNotBlank()) { chatNameCache[chatId] = it; return it }
        }
        val db = weChatDb ?: run {
            Log.w(TAG, "resolveChatNameFromDb: weChatDb is null, returning raw chatId")
            return chatId
        }
        try {
            Log.d(TAG, "resolveChatNameFromDb: looking up $chatId")
            val directName = queryChatNameDirect(db, chatId)
            if (directName != null) {
                chatNameCache[chatId] = directName
                Log.d(TAG, "resolveChatNameFromDb: $chatId -> $directName (direct)")
                return directName
            }
            val convName = querySingleColumn(db, chatId, "rconversation", "username", "displayname")
            if (convName != null) {
                chatNameCache[chatId] = convName
                Log.d(TAG, "resolveChatNameFromDb: $chatId -> $convName (rconversation)")
                return convName
            }
            val roomName = querySingleColumn(db, chatId, "chatroom", "chatroomname", "displayname")
            if (roomName != null) {
                chatNameCache[chatId] = roomName
                Log.d(TAG, "resolveChatNameFromDb: $chatId -> $roomName (chatroom, may be member list)")
                return roomName
            }
            Log.d(TAG, "resolveChatNameFromDb: $chatId not found in any table")
        } catch (e: Exception) {
            Log.w(TAG, "resolveChatNameFromDb: ${e.message}")
        }
        return chatId
    }

    private fun queryChatNameDirect(db: Any, chatId: String): String? {
        val safeId = chatId.replace("'", "''")
        for (col in listOf("nickname", "conRemark")) {
            try {
                val raw = XposedHelpers.callMethod(db, "rawQuery",
                    "SELECT $col FROM rcontact WHERE username='$safeId' LIMIT 1", null) ?: continue
                val c = raw as? Cursor ?: continue
                c.use { cur ->
                    if (cur.moveToFirst()) {
                        val idx = cur.getColumnIndex(col)
                        if (idx >= 0 && !cur.isNull(idx)) {
                            val v = cur.getString(idx)
                            if (!v.isNullOrBlank()) return v
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "queryChatNameDirect($col): ${e.message}")
            }
        }
        return null
    }

    private fun querySingleColumn(db: Any, chatId: String, table: String, idCol: String, nameCol: String): String? {
        val safeId = chatId.replace("'", "''")
        return try {
            val raw = XposedHelpers.callMethod(db, "rawQuery",
                "SELECT $nameCol FROM $table WHERE $idCol='$safeId' LIMIT 1", null) ?: return null
            val c = raw as? Cursor ?: return null
            c.use { cur ->
                if (cur.moveToFirst()) {
                    val idx = cur.getColumnIndex(nameCol)
                    if (idx >= 0 && !cur.isNull(idx)) {
                        val v = cur.getString(idx)
                        if (!v.isNullOrBlank()) v else null
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "querySingleColumn($table.$nameCol): ${e.message}")
            null
        }
    }

    // ── WeChat XML message parsing ──────────────────────────────────

    private fun extractXmlTitle(xml: String): String? {
        val title = extractTag(xml, "title")
        val des = extractTag(xml, "des")
        val url = extractTag(xml, "url")
        val prefix = when {
            title != null && des != null -> "[卡片] $title - $des"
            title != null -> "[卡片] $title"
            else -> "[分享]"
        }
        return if (url != null) "$prefix\n$url" else prefix
    }

    private fun extractTag(xml: String, tag: String): String? {
        val open = "<$tag>"
        val close = "</$tag>"
        val start = xml.indexOf(open)
        if (start < 0) return null
        val contentStart = start + open.length
        val end = xml.indexOf(close, contentStart)
        if (end < 0) return null
        val text = xml.substring(contentStart, end).trim()
        return text.ifBlank { null }
    }
}
