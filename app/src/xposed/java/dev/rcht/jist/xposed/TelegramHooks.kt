@file:Suppress("unused")

package dev.rcht.jist.xposed

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

object TelegramHooks {
    private var nagramDb: Any? = null
    private val nagramUserCache = mutableMapOf<Long, String>()
    private val nagramChatCache = mutableMapOf<Long, String>()
    private var appDisplayName = "Nagram"
    private val messagesV2Columns = mutableListOf<String>()
    private val pendingMid = mutableMapOf<Int, Long>()
    private val pendingUid = mutableMapOf<Int, Long>()
    private var isQueryingDb = false

    fun setupTelegramX(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.i(TAG, "Attempting Telegram X hooks")
        try {
            val resultHandlerClass = XposedHelpers.findClass(
                "org.drinkless.td.libcore.telegram.Client\$ResultHandler", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(resultHandlerClass, "onResult",
                XposedHelpers.findClass("org.drinkless.td.libcore.telegram.TdApi\$Object", lpparam.classLoader),
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val obj = param.args[0] ?: return
                        val className = obj.javaClass.name
                        if (className.contains("UpdateNewMessage")) {
                            handleTelegramXNewMessage(lpparam, obj)
                        } else if (className.contains("UpdateMessageContent")) {
                            Log.d(TAG, "TGX UpdateMessageContent received (not processed)")
                        }
                    }
                })
            Log.i(TAG, "TGX TDLib hook OK")
        } catch (e: Exception) {
            Log.w(TAG, "TGX TDLib hook failed: ${e.message}")
        }
    }

    private fun handleTelegramXNewMessage(lpparam: XC_LoadPackage.LoadPackageParam, updateObj: Any) {
        try {
            val msg = XposedHelpers.getObjectField(updateObj, "message")
            val chatId = XposedHelpers.getObjectField(msg, "chatId") as Long
            val msgId = XposedHelpers.getObjectField(msg, "id") as Long
            val date = XposedHelpers.getObjectField(msg, "date") as Int
            val isOutgoing = XposedHelpers.getObjectField(msg, "isOutgoing") as Boolean
            val content = XposedHelpers.getObjectField(msg, "content")
            val contentClass = content.javaClass.name
            val senderId = XposedHelpers.getObjectField(msg, "senderId")

            // TDLib chat ID convention: negative = group/channel
            val isGroup = chatId < 0
            val isChannel = chatId <= -1000000000000L
            val chatType = when {
                !isGroup -> "private"
                isChannel -> "channel/supergroup"
                else -> "group"
            }


            // Extract text content from various message types
            var textContent: String? = null
            try {
                textContent = when {
                    contentClass.contains("MessageText") -> {
                        val text = XposedHelpers.getObjectField(content, "text")
                        XposedHelpers.callMethod(text, "toString") as? String
                    }
                    contentClass.contains("MessagePhoto") || contentClass.contains("MessageVideo") ||
                    contentClass.contains("MessageAnimation") || contentClass.contains("MessageDocument") -> {
                        val caption = XposedHelpers.getObjectField(content, "caption")
                        if (caption != null) XposedHelpers.callMethod(caption, "toString") as? String else null
                    }
                    else -> null
                }
            } catch (_: Exception) { }

            // Determine media type label
            val mediaLabel = when {
                contentClass.contains("MessageText") -> null
                contentClass.contains("MessagePhoto") -> "[图片]"
                contentClass.contains("MessageVideo") -> "[视频]"
                contentClass.contains("MessageAnimation") -> "[GIF]"
                contentClass.contains("MessageDocument") -> "[文件]"
                contentClass.contains("MessageSticker") -> "[贴纸]"
                contentClass.contains("MessageAudio") -> "[音频]"
                contentClass.contains("MessageVoiceNote") -> "[语音]"
                contentClass.contains("MessageLocation") -> "[位置]"
                contentClass.contains("MessageContact") -> "[联系人]"
                contentClass.contains("MessagePoll") -> "[投票]"
                else -> "[${contentClass.substringAfterLast('$')}]"
            }

            // Resolve sender name
            val senderName = if (isOutgoing) "我" else resolveTelegramXSenderName(senderId)

            // Build content string
            val contentStr = when {
                !textContent.isNullOrBlank() && mediaLabel != null -> "$mediaLabel $textContent"
                !textContent.isNullOrBlank() -> textContent
                mediaLabel != null -> mediaLabel
                else -> "[空消息]"
            }

            val context = XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.app.ActivityThread", null), "currentApplication"
            ) as? Context ?: run {
                Log.w(TAG, "  TGX: failed to get application context")
                return
            }

            val pkg = lpparam.packageName
            val timestamp = date * 1000L

            bgHandler.post {
                saveTelegramXMessage(context, pkg, chatId.toString(), senderName,
                    chatId.toString(), timestamp, msgId, contentStr, chatType)
            }
        } catch (e: Exception) {
            Log.w(TAG, "TGX handleNewMessage error: ${e.message}")
        }
    }

    private fun resolveTelegramXSenderName(senderId: Any?): String {
        if (senderId == null) return "[未知发送者]"
        val senderClass = senderId.javaClass.name
        return when {
            senderClass.contains("MessageSenderUser") -> {
                try {
                    val userId = XposedHelpers.getObjectField(senderId, "userId") as Long
                    "用户$userId"
                } catch (_: Exception) { "[用户]" }
            }
            senderClass.contains("MessageSenderChat") -> {
                try {
                    val chatId = XposedHelpers.getObjectField(senderId, "chatId") as Long
                    "频道/群$chatId"
                } catch (_: Exception) { "[频道]" }
            }
            else -> "[发送者]"
        }
    }

    private fun saveTelegramXMessage(
        context: Context, pkg: String, chatId: String, senderName: String,
        chatName: String, timestamp: Long, msgId: Long, content: String, chatType: String
    ) {
        try {
            Log.d(TAG, "[Telegram] [$chatName] [$senderName] ${content.take(80)}")
            val intent = Intent("dev.rcht.jist.SAVE_MESSAGE").apply {
                setClassName("dev.rcht.jist", "dev.rcht.jist.receiver.MessageReceiver")
                putExtra("pkg", pkg)
                putExtra("chatId", chatId)
                putExtra("senderName", senderName)
                putExtra("chatName", chatName)
                putExtra("content", content)
                putExtra("timestamp", timestamp)
                putExtra("msgSeq", msgId)
                putExtra("msgType", 0)
                putExtra("appDisplayName", "Telegram X")
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.w(TAG, "saveTelegramXMessage error: ${e.message}")
        }
    }

    fun setupNagram(lpparam: XC_LoadPackage.LoadPackageParam, displayName: String = "Nagram") {
        appDisplayName = displayName
        lastClassLoader = lpparam.classLoader
        Log.i(TAG, "Setting up $displayName message hooks")
        // Try WCDB first — many Telegram forks use it for cache4.db
        try {
            val wcdbClass = XposedHelpers.findClass(
                "com.tencent.wcdb.database.SQLiteDatabase", lpparam.classLoader)
            Log.i(TAG, "Nagram uses WCDB! Wrapping WCDB hooks...")

            fun makeWcdbInsertHook(methodName: String) = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val table = param.args[0] as? String ?: return
                    Log.d(TAG, "Nagram WCDB $methodName: table=$table")
                    if (nagramDb == null) {
                        nagramDb = param.thisObject
                        Log.d(TAG, "nagramDb captured via WCDB $methodName")
                        loadNagramCache()
                    }
                    if (table == "messages_v2") {
                        val cv = param.args[2] as? ContentValues ?: return
                        onNagramMessageRow(lpparam, cv)
                    }
                }
            }

            try {
                XposedHelpers.findAndHookMethod(wcdbClass, "insertWithOnConflict",
                    String::class.java, String::class.java, ContentValues::class.java,
                    Int::class.javaPrimitiveType, makeWcdbInsertHook("insertWithOnConflict"))
            } catch (_: Exception) { }
            try {
                XposedHelpers.findAndHookMethod(wcdbClass, "insert",
                    String::class.java, String::class.java, ContentValues::class.java,
                    makeWcdbInsertHook("insert"))
            } catch (_: Exception) { }
            try {
                XposedHelpers.findAndHookMethod(wcdbClass, "insertOrThrow",
                    String::class.java, String::class.java, ContentValues::class.java,
                    makeWcdbInsertHook("insertOrThrow"))
            } catch (_: Exception) { }
            return  // WCDB found, no need for standard SQLiteDatabase hooks
        } catch (_: Throwable) {
            Log.d(TAG, "Nagram does NOT use WCDB")
        }

        // Try sqlcipher — Telegram uses encrypted DB via net.zetetic
        try {
            val cipherClass = XposedHelpers.findClass(
                "net.sqlcipher.database.SQLiteDatabase", lpparam.classLoader)
            fun makeCipherHook(methodName: String) = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val table = param.args[0] as? String ?: return
                    if (nagramDb == null) {
                        nagramDb = param.thisObject
                        loadNagramCache()
                    }
                    if (table == "messages_v2") {
                        val cv = param.args[2] as? ContentValues ?: return
                        onNagramMessageRow(lpparam, cv)
                    }
                }
            }

            for (sig in listOf(
                "insertWithOnConflict" to arrayOf(String::class.java, String::class.java, ContentValues::class.java, Int::class.javaPrimitiveType),
                "insert" to arrayOf(String::class.java, String::class.java, ContentValues::class.java),
                "insertOrThrow" to arrayOf(String::class.java, String::class.java, ContentValues::class.java),
            )) {
                try {
                    XposedHelpers.findAndHookMethod(cipherClass, sig.first, *sig.second, makeCipherHook(sig.first))
                } catch (_: Exception) { }
            }
            return
        } catch (_: Throwable) {
            Log.d(TAG, "Nagram does NOT use sqlcipher")
        }

        // Try Telegram's own SQLiteDatabase wrapper (org.telegram.SQLite.SQLiteDatabase)
        try {
            val tgDbClass = XposedHelpers.findClass(
                "org.telegram.SQLite.SQLiteDatabase", lpparam.classLoader)


            // Telegram uses executeFast(String) -> SQLitePreparedStatement -> bindXXX -> step()
            val prepClass = XposedHelpers.findClass(
                "org.telegram.SQLite.SQLitePreparedStatement", lpparam.classLoader)

            // Track SQL + bound values per prepared statement (keyed by identity hash)
            val statementSql = mutableMapOf<Int, String>()
            val boundValues = mutableMapOf<Int, MutableMap<Int, Any?>>()

            // Hook executeFast to capture SQL per statement
            try {
                XposedHelpers.findAndHookMethod(tgDbClass, "executeFast",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val sql = param.args[0] as? String ?: return
                            val stmt = param.result ?: return
                            val key = System.identityHashCode(stmt)
                            statementSql[key] = sql
                            boundValues[key] = mutableMapOf()
                        }
                    })
            } catch (e: Throwable) {
                Log.w(TAG, "Nagram hook: executeFast FAILED: ${e.message}")
            }

            // Hook bind methods on SQLitePreparedStatement to capture values
            val bindSpecs = listOf(
                "bindLong" to listOf(Int::class.javaPrimitiveType, Long::class.javaPrimitiveType),
                "bindInteger" to listOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType),
                "bindString" to listOf(Int::class.javaPrimitiveType, String::class.java),
                "bindDouble" to listOf(Int::class.javaPrimitiveType, Double::class.javaPrimitiveType),
            )
            for ((bindName, paramTypes) in bindSpecs) {
                try {
                    XposedHelpers.findAndHookMethod(prepClass, bindName,
                        *paramTypes.toTypedArray(),
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val key = System.identityHashCode(param.thisObject)
                                if (key !in statementSql) return
                                val index = param.args[0] as Int
                                val value = param.args[1]
                                boundValues[key]?.set(index, value)
                            }
                        })

                } catch (_: Throwable) { }
            }
            // Hook bindNull
            try {
                XposedHelpers.findAndHookMethod(prepClass, "bindNull",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val key = System.identityHashCode(param.thisObject)
                            if (key !in statementSql) return
                            val index = param.args[0] as Int
                            boundValues[key]?.set(index, null)
                        }
                    })
            } catch (_: Throwable) { }
            // Hook bindByteArray to capture BLOB values (e.g. data column)
            try {
                XposedHelpers.findAndHookMethod(prepClass, "bindByteArray",
                    Int::class.javaPrimitiveType, ByteArray::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val key = System.identityHashCode(param.thisObject)
                            if (key !in statementSql) return
                            val index = param.args[0] as Int
                            val value = param.args[1] as ByteArray
                            boundValues[key]?.set(index, value)
                        }
                    })
            } catch (_: Throwable) { }

            // Hook step() to process INSERT/REPLACE into messages_v2
            // Using before+after to capture mid/uid first, then query data BLOB after step completes
            try {
                XposedHelpers.findAndHookMethod(prepClass, "step",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val key = System.identityHashCode(param.thisObject)
                            val sql = statementSql[key] ?: return
                            if (!sql.contains("messages_v2", ignoreCase = true) ||
                                (!sql.contains("INSERT", ignoreCase = true) &&
                                 !sql.contains("REPLACE", ignoreCase = true))) return
                            val values = boundValues[key] ?: return
                            // Capture mid and uid for afterHookedMethod
                            val rawMid = values[1] ?: return
                            val rawUid = values[2] ?: return
                            pendingMid[key] = (rawMid as Number).toLong()
                            pendingUid[key] = (rawUid as Number).toLong()
                        }
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val key = System.identityHashCode(param.thisObject)
                            val mid = pendingMid.remove(key) ?: return
                            val uid = pendingUid.remove(key) ?: return
                            // Query the DB in afterHookedMethod to get the data BLOB for text extraction
                            if (nagramDb != null && !isQueryingDb) {
                                isQueryingDb = true
                                try {
                                    val raw = XposedHelpers.callMethod(nagramDb!!, "queryFinalized",
                                        "SELECT date, data, out, media FROM messages_v2 WHERE mid=? AND uid=?",
                                        arrayOf<Any>(mid, uid))
                                    if (raw != null) {
                                        while (XposedHelpers.callMethod(raw, "next") as Boolean) {
                                            val date = XposedHelpers.callMethod(raw, "intValue", 0) as Int
                                            val data = XposedHelpers.callMethod(raw, "byteArrayValue", 1) as? ByteArray
                                            val out = XposedHelpers.callMethod(raw, "intValue", 2) as Int
                                            val media = XposedHelpers.callMethod(raw, "intValue", 3) as Int
                                            val cv = ContentValues()
                                            cv.put("mid", mid)
                                            cv.put("uid", uid)
                                            cv.put("date", date.toLong())
                                            cv.put("out", out)
                                            cv.put("media", media)
                                            if (data != null) cv.put("data", data)
                                            onNagramMessageRow(lpparam, cv)
                                        }
                                    }
                                } catch (e: Exception) {
                                } finally {
                                    isQueryingDb = false
                                }
                            }
                        }
                    })
            } catch (e: Throwable) {
            }

            // Also hook queryFinalized to capture DB instance
            try {
                XposedHelpers.findAndHookMethod(tgDbClass, "queryFinalized",
                    String::class.java, Array<Any?>::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (nagramDb == null) {
                                nagramDb = param.thisObject
                                loadNagramCache()
                            }
                        }
                    })
            } catch (e: Throwable) {
            }
            return
        } catch (_: Throwable) {
            Log.d(TAG, "Nagram does NOT use Telegram SQLite wrapper")
        }

        // Fallback: standard android.database.sqlite.SQLiteDatabase
        try {
            val dbClass = XposedHelpers.findClass(
                "android.database.sqlite.SQLiteDatabase", lpparam.classLoader)

            // Hook ALL SQLiteDatabase insert methods — log every table for debugging
            fun makeInsertHook(methodName: String) = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val table = param.args[0] as? String ?: return
                    val cv = param.args[2] as? ContentValues ?: return
                    Log.d(TAG, "Nagram $methodName: table=$table keys=${cv.keySet()}")
                    if (nagramDb == null) {
                        nagramDb = param.thisObject
                        Log.d(TAG, "nagramDb captured via $methodName")
                        loadNagramCache()
                    }
                    if (table == "messages_v2") {
                        onNagramMessageRow(lpparam, cv)
                    }
                }
            }

            try {
                XposedHelpers.findAndHookMethod(dbClass, "insertWithOnConflict",
                    String::class.java, String::class.java, ContentValues::class.java,
                    Int::class.javaPrimitiveType, makeInsertHook("insertWithOnConflict"))
            } catch (_: Exception) { }

            try {
                XposedHelpers.findAndHookMethod(dbClass, "insert",
                    String::class.java, String::class.java, ContentValues::class.java,
                    makeInsertHook("insert"))
            } catch (_: Exception) { }

            try {
                XposedHelpers.findAndHookMethod(dbClass, "insertOrThrow",
                    String::class.java, String::class.java, ContentValues::class.java,
                    makeInsertHook("insertOrThrow"))
            } catch (_: Exception) { }

            // Hook execSQL to catch raw INSERT statements
            try {
                XposedHelpers.findAndHookMethod(dbClass, "execSQL",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val sql = param.args[0] as? String ?: return
                            if (sql.startsWith("INSERT") || sql.startsWith("insert")) {
                                Log.d(TAG, "Nagram execSQL: ${sql.take(200)}")
                            }
                        }
                    })
            } catch (_: Exception) { }

            // Hook execSQL(String, Object[]) variant
            try {
                XposedHelpers.findAndHookMethod(dbClass, "execSQL",
                    String::class.java, Array::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val sql = param.args[0] as? String ?: return
                            if (sql.startsWith("INSERT") || sql.startsWith("insert")) {
                                Log.d(TAG, "Nagram execSQL(bind): ${sql.take(200)}")
                            }
                        }
                    })
            } catch (_: Exception) { }

            // Hook compileStatement to catch prepared INSERTs
            try {
                XposedHelpers.findAndHookMethod(dbClass, "compileStatement",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val sql = param.args[0] as? String ?: return
                            if (!sql.startsWith("INSERT") && !sql.startsWith("insert")) return
                            Log.d(TAG, "Nagram compileStatement: ${sql.take(200)}")
                        }
                    })
            } catch (_: Exception) { }

            // Hook database open methods to detect when cache4.db is accessed
            try {
                XposedHelpers.findAndHookMethod(dbClass, "openDatabase",
                    String::class.java, android.database.sqlite.SQLiteDatabase.CursorFactory::class.java,
                    Int::class.javaPrimitiveType, android.database.DatabaseErrorHandler::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val path = param.args[0] as? String ?: return
                            Log.i(TAG, "Nagram openDatabase: $path")
                        }
                    })
            } catch (_: Exception) { }

            try {
                XposedHelpers.findAndHookMethod(dbClass, "openOrCreateDatabase",
                    String::class.java, android.database.sqlite.SQLiteDatabase.CursorFactory::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val path = param.args[0] as? String ?: return
                            Log.i(TAG, "Nagram openOrCreateDatabase: $path")
                        }
                    })
                Log.i(TAG, "Nagram hook: openOrCreateDatabase OK")
            } catch (_: Exception) { }

            try {
                XposedHelpers.findAndHookMethod(dbClass, "openOrCreateDatabase",
                    String::class.java, android.database.sqlite.SQLiteDatabase.CursorFactory::class.java,
                    android.database.DatabaseErrorHandler::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val path = param.args[0] as? String ?: return
                            Log.i(TAG, "Nagram openOrCreateDatabase(err): $path")
                        }
                    })
                Log.i(TAG, "Nagram hook: openOrCreateDatabase(err) OK")
            } catch (_: Exception) { }
        } catch (e: Throwable) {
            Log.w(TAG, "Nagram hook init failed: ${e.message}")
        }
    }

    private fun loadNagramCache() {
        try {
            val db = nagramDb ?: return
            // Detect Telegram's own SQLite wrapper (uses queryFinalized, not rawQuery)
            if (db.javaClass.name.contains("telegram.SQLite")) {
                loadNagramCacheTelegram(db)
                return
            }
            // Probe all tables in this database
            try {
                val raw = XposedHelpers.callMethod(db, "rawQuery",
                    "SELECT name FROM sqlite_master WHERE type='table'", null)
                val c = raw as? Cursor ?: return
                val tables = mutableListOf<String>()
                c.use { while (it.moveToNext()) tables.add(it.getString(0)) }
                Log.i(TAG, "Nagram DB tables: $tables")
            } catch (e: Exception) {
                Log.w(TAG, "loadNagramCache probe tables: ${e.message}")
            }

            // Probe all databases — list files
            try {
                val ctx = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null), "currentApplication"
                ) as? Context
                if (ctx != null) {
                    val dbDir = ctx.getDatabasePath("dummy").parentFile
                    val files = dbDir?.list()?.joinToString(", ")
                    if (files != null) Log.i(TAG, "Nagram db files: $files")
                    // Also check files/ directory for cache4.db
                    try {
                        val filesDir = ctx.filesDir
                        val filesList = filesDir?.list()?.filter { it.endsWith(".db") }?.joinToString(", ")
                        if (filesList != null) Log.i(TAG, "Nagram files/*.db: $filesList")
                    } catch (_: Exception) { }
                }
            } catch (_: Exception) { }

            try {
                val raw = XposedHelpers.callMethod(db, "rawQuery",
                    "SELECT uid, name FROM users WHERE name IS NOT NULL", null)
                val c = raw as? Cursor ?: return
                c.use { cur ->
                    val uidIdx = cur.getColumnIndex("uid")
                    val nameIdx = cur.getColumnIndex("name")
                    if (uidIdx < 0 || nameIdx < 0) return@use
                    var count = 0
                    while (cur.moveToNext()) {
                        val uid = cur.getLong(uidIdx)
                        val name = cur.getString(nameIdx)
                        if (!name.isNullOrBlank()) {
                            nagramUserCache[uid] = name
                            count++
                        }
                    }
                    Log.i(TAG, "loadNagramCache: users $count entries")
                }
            } catch (e: Exception) {
                Log.w(TAG, "loadNagramCache users: ${e.message}")
            }
            try {
                val raw = XposedHelpers.callMethod(db, "rawQuery",
                    "SELECT uid, name FROM chats WHERE name IS NOT NULL", null)
                val c = raw as? Cursor ?: return
                c.use { cur ->
                    val uidIdx = cur.getColumnIndex("uid")
                    val nameIdx = cur.getColumnIndex("name")
                    if (uidIdx < 0 || nameIdx < 0) return@use
                    var count = 0
                    while (cur.moveToNext()) {
                        val uid = cur.getLong(uidIdx)
                        val name = cur.getString(nameIdx)
                        if (!name.isNullOrBlank()) {
                            nagramChatCache[uid] = name
                            count++
                        }
                    }
                    Log.i(TAG, "loadNagramCache: chats $count entries")
                }
            } catch (e: Exception) {
                Log.w(TAG, "loadNagramCache chats: ${e.message}")
            }
            Log.i(TAG, "loadNagramCache done: ${nagramUserCache.size} users, ${nagramChatCache.size} chats")
        } catch (e: Exception) {
            Log.w(TAG, "loadNagramCache: ${e.message}")
        }
    }

    private fun loadNagramCacheTelegram(db: Any) {
        Log.i(TAG, "loadNagramCacheTelegram: using queryFinalized")
        // Probe tables
        try {
            val raw = XposedHelpers.callMethod(db, "queryFinalized",
                "SELECT name FROM sqlite_master WHERE type='table'", emptyArray<Any>())
            if (raw != null) {
                val tables = mutableListOf<String>()
                while (XposedHelpers.callMethod(raw, "next") as Boolean) {
                    tables.add(XposedHelpers.callMethod(raw, "stringValue", 0) as String)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "loadNagramCacheTelegram probe tables: ${e.message}")
        }
        // Probe messages_v2 columns
        try {
            val raw = XposedHelpers.callMethod(db, "queryFinalized",
                "PRAGMA table_info(messages_v2)", emptyArray<Any>())
            if (raw != null) {
                val cols = mutableListOf<String>()
                while (XposedHelpers.callMethod(raw, "next") as Boolean) {
                    cols.add(XposedHelpers.callMethod(raw, "stringValue", 1) as String)
                }
                messagesV2Columns.clear()
                messagesV2Columns.addAll(cols)
            }
        } catch (e: Exception) {
            Log.w(TAG, "messages_v2 PRAGMA: ${e.message}")
        }
        // Load users
        try {
            val raw = XposedHelpers.callMethod(db, "queryFinalized",
                "SELECT uid, name FROM users WHERE name IS NOT NULL", emptyArray<Any>())
            if (raw != null) {
                var count = 0
                while (XposedHelpers.callMethod(raw, "next") as Boolean) {
                    val uid = XposedHelpers.callMethod(raw, "longValue", 0) as Long
                    val name = XposedHelpers.callMethod(raw, "stringValue", 1) as String
                    if (!name.isNullOrBlank()) {
                        nagramUserCache[uid] = name.split(";;;").first().trim()
                        count++
                    }
                }
                Log.i(TAG, "loadNagramCacheTelegram: users $count entries")
            }
        } catch (e: Exception) {
            Log.w(TAG, "loadNagramCacheTelegram users: ${e.message}")
        }
        // Load chats – probe column names first
        val chatNameCol = "name"
        try {
            val raw = XposedHelpers.callMethod(db, "queryFinalized",
                "SELECT uid, $chatNameCol FROM chats WHERE $chatNameCol IS NOT NULL", emptyArray<Any>())
            if (raw != null) {
                var count = 0
                while (XposedHelpers.callMethod(raw, "next") as Boolean) {
                    val uid = XposedHelpers.callMethod(raw, "longValue", 0) as Long
                    val name = XposedHelpers.callMethod(raw, "stringValue", 1) as String
                    if (!name.isNullOrBlank()) {
                        nagramChatCache[uid] = name.split(";;;").first().trim()
                        count++
                    }
                }
                Log.i(TAG, "loadNagramCacheTelegram: chats $count entries")
            }
        } catch (e: Exception) {
            Log.w(TAG, "loadNagramCacheTelegram chats: ${e.message}")
        }
        Log.i(TAG, "loadNagramCacheTelegram done: ${nagramUserCache.size} users, ${nagramChatCache.size} chats")
    }

    private fun parseInsertSql(sql: String, args: Array<*>?): ContentValues? {
        try {
            val pattern = Regex("INSERT\\s+INTO\\s+(\\w+)\\s*\\(([^)]+)\\)\\s*VALUES", RegexOption.IGNORE_CASE)
            val match = pattern.find(sql) ?: return null
            val columns = match.groupValues[2].split(",").map { it.trim() }
            if (args == null || args.size < columns.size) {
                Log.d(TAG, "parseInsertSql: columns=${columns.size} args=${args?.size ?: 0} mismatch")
                return null
            }
            val cv = ContentValues()
            for ((i, col) in columns.withIndex()) {
                val arg = args[i] ?: continue
                when (arg) {
                    is Long -> cv.put(col, arg)
                    is Int -> cv.put(col, arg)
                    is String -> cv.put(col, arg)
                    is ByteArray -> cv.put(col, arg)
                    is Boolean -> cv.put(col, arg)
                    is Float -> cv.put(col, arg)
                    is Double -> cv.put(col, arg)
                    else -> cv.put(col, arg.toString())
                }
            }
            return cv
        } catch (e: Exception) {
            Log.d(TAG, "parseInsertSql error: ${e.message}")
            return null
        }
    }

    private fun parseInsertSqlIndexed(sql: String, values: Map<Int, Any?>): ContentValues? {
        try {
            val pattern = Regex("INSERT\\s+INTO\\s+(\\w+)\\s*\\(([^)]+)\\)\\s*VALUES", RegexOption.IGNORE_CASE)
            val match = pattern.find(sql) ?: return null
            val columns = match.groupValues[2].split(",").map { it.trim() }
            val cv = ContentValues()
            for ((i, col) in columns.withIndex()) {
                // SQLite bind params are 1-indexed
                val arg = values[i + 1] ?: continue
                when (arg) {
                    is Long -> cv.put(col, arg)
                    is Int -> cv.put(col, arg)
                    is String -> cv.put(col, arg)
                    is ByteArray -> cv.put(col, arg)
                    is Boolean -> cv.put(col, arg)
                    is Float -> cv.put(col, arg)
                    is Double -> cv.put(col, arg)
                    is java.lang.Long -> cv.put(col, arg.toLong())
                    is java.lang.Integer -> cv.put(col, arg.toInt())
                    is java.lang.Double -> cv.put(col, arg.toDouble())
                    else -> cv.put(col, arg.toString())
                }
            }
            return cv
        } catch (e: Exception) {
            Log.d(TAG, "parseInsertSqlIndexed error: ${e.message}")
            return null
        }
    }

    private fun parseInsertSqlPositional(sql: String, values: Map<Int, Any?>, columns: List<String>): ContentValues? {
        try {
            // Extract the VALUES clause content
            val parenStart = sql.indexOf("VALUES(")
            if (parenStart < 0) return null
            var depth = 0
            val valStart = parenStart + 7
            var parenEnd = -1
            for (i in valStart until sql.length) {
                when (sql[i]) {
                    '(' -> depth++
                    ')' -> if (depth == 0) { parenEnd = i; break } else depth--
                }
            }
            if (parenEnd < 0) return null
            val valuesClause = sql.substring(valStart, parenEnd)
            // Split into tokens (handling nested parens and literals)
            val tokens = mutableListOf<String>()
            val current = StringBuilder()
            depth = 0
            for (c in valuesClause) {
                when {
                    c == '(' -> { current.append(c); depth++ }
                    c == ')' -> { current.append(c); depth-- }
                    c == ',' && depth == 0 -> {
                        tokens.add(current.toString().trim()); current.clear()
                    }
                    else -> current.append(c)
                }
            }
            if (current.isNotEmpty()) tokens.add(current.toString().trim())
            // Map bind params (1-indexed) to column positions
            val cv = ContentValues()
            var bindIdx = 1
            for ((colIdx, token) in tokens.withIndex()) {
                if (colIdx >= columns.size) break
                val colName = columns[colIdx]
                if (token == "?" || token == "? ") {
                    val v = values[bindIdx]
                    bindIdx++
                    if (v == null) continue
                    putCvValue(cv, colName, v)
                } else {
                    // Literal value (e.g. "0", "'text'")
                    try {
                        val trimmed = token.trim()
                        if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
                            cv.put(colName, trimmed.substring(1, trimmed.length - 1))
                        } else if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                            cv.put(colName, trimmed.substring(1, trimmed.length - 1))
                        } else if (trimmed.contains(".")) {
                            cv.put(colName, trimmed.toDouble())
                        } else if (trimmed.equals("null", ignoreCase = true)) {
                            // skip
                        } else {
                            val longVal = trimmed.toLong()
                            val intVal = longVal.toInt()
                            if (intVal.toLong() == longVal) cv.put(colName, intVal) else cv.put(colName, longVal)
                        }
                    } catch (_: Exception) { }
                }
            }
            return cv
        } catch (e: Exception) {
            Log.d(TAG, "parseInsertSqlPositional error: ${e.message}")
            return null
        }
    }

    private fun putCvValue(cv: ContentValues, col: String, v: Any) {
        when (v) {
            is Long -> cv.put(col, v)
            is Int -> cv.put(col, v)
            is String -> cv.put(col, v)
            is ByteArray -> cv.put(col, v)
            is Boolean -> cv.put(col, v)
            is Float -> cv.put(col, v)
            is Double -> cv.put(col, v)
            is java.lang.Long -> cv.put(col, (v as java.lang.Long).toLong())
            is java.lang.Integer -> cv.put(col, (v as java.lang.Integer).toInt())
            is java.lang.Double -> cv.put(col, (v as java.lang.Double).toDouble())
            else -> cv.put(col, v.toString())
        }
    }

    private fun onNagramMessageRow(lpparam: XC_LoadPackage.LoadPackageParam, cv: ContentValues) {
        try {
            val mid = cv.getAsLong("mid") ?: return
            val uid = cv.getAsLong("uid") ?: return
            val date = cv.getAsLong("date") ?: return
            val out = cv.getAsInteger("out") ?: 0
            val media = cv.getAsInteger("media") ?: -1

            // First try message/caption columns (old messages table), then extract from data BLOB
            var fullText = cv.getAsString("message") ?: cv.getAsString("caption")
            if (fullText.isNullOrBlank()) {
                fullText = extractMessageTextFromData(lpparam, cv)
            }

            // Telegram DB uid convention: negative = group/channel
            val isGroup = uid < 0
            val isChannel = uid <= -1000000000000L
            val chatType = when {
                !isGroup -> "private"
                isChannel -> "channel/supergroup"
                else -> "group"
            }


            val chatName: String
            val senderName: String

            if (isGroup) {
                chatName = resolveChatName(uid, lpparam.classLoader)
                // Try to extract actual sender from data blob for group messages
                val fromSenderId = if (out == 1) null else extractSenderIdFromData(lpparam, cv)
                senderName = when {
                    out == 1 -> "我"
                    fromSenderId != null -> nagramUserCache[fromSenderId] ?: "用户$fromSenderId"
                    else -> "[群组成员]"
                }
            } else {
                val userName = resolveUserName(uid)
                chatName = userName
                senderName = if (out == 1) "我" else userName
            }

            val context = XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.app.ActivityThread", null), "currentApplication"
            ) as? Context ?: run {
                return
            }

            val pkg = lpparam.packageName
            val timestamp = date * 1000L
            bgHandler.post {
                saveNagramMessage(context, pkg, uid.toString(), senderName, chatName,
                    timestamp, mid, media, fullText, chatType)
            }
        } catch (e: Exception) {
            Log.w(TAG, "onNagramMessageRow error: ${e.message}")
        }
    }

    private var lastClassLoader: ClassLoader? = null

    private fun resolveChatName(uid: Long, classLoader: ClassLoader? = null): String {
        val cached = nagramChatCache[uid]
        if (cached != null) return cached
        // Fallback: query chats table directly
        if (nagramDb != null) {
            try {
                val raw = XposedHelpers.callMethod(nagramDb!!, "queryFinalized",
                    "SELECT name FROM chats WHERE uid=?", arrayOf<Any>(uid))
                if (raw != null) {
                    while (XposedHelpers.callMethod(raw, "next") as Boolean) {
                        val name = XposedHelpers.callMethod(raw, "stringValue", 0) as? String
                        if (!name.isNullOrBlank()) {
                            val clean = name.split(";;;").first().trim()
                            nagramChatCache[uid] = clean
                            return clean
                        }
                    }
                }
                // Fallback: try both uid and abs(uid) (chats table may use positive IDs)
                for (tryUid in listOf(uid, -uid)) {
                    try {
                        val r = XposedHelpers.callMethod(nagramDb!!, "queryFinalized",
                            "SELECT name FROM chats WHERE uid=?", arrayOf<Any>(tryUid))
                        if (r != null) {
                            while (XposedHelpers.callMethod(r, "next") as Boolean) {
                                val n = XposedHelpers.callMethod(r, "stringValue", 0) as? String
                                if (!n.isNullOrBlank()) {
                                    val clean = n.split(";;;").first().trim()
                                    nagramChatCache[uid] = clean
                                    nagramChatCache[tryUid] = clean
                                    return clean
                                }
                            }
                        }
                    } catch (_: Exception) { }
                }
                // Fallback: extract from data BLOB
                try {
                    val raw2 = XposedHelpers.callMethod(nagramDb!!, "queryFinalized",
                        "SELECT data FROM chats WHERE uid=?", arrayOf<Any>(uid))
                    if (raw2 != null) {
                        while (XposedHelpers.callMethod(raw2, "next") as Boolean) {
                            val blob = XposedHelpers.callMethod(raw2, "byteArrayValue", 0) as? ByteArray
                            if (blob != null) {
                                val cl = classLoader ?: lastClassLoader
                                val title = if (cl != null) deserializeChatTitle(cl, blob) else null
                                if (title != null) {
                                    nagramChatCache[uid] = title
                                    return title
                                }
                            }
                        }
                    }
                } catch (_: Exception) { }
            } catch (_: Exception) { }
        }
        return uid.toString()
    }

    private fun resolveUserName(uid: Long): String {
        val cached = nagramUserCache[uid]
        if (cached != null) return cached
        // Fallback: query users table directly
        if (nagramDb != null) {
            try {
                val raw = XposedHelpers.callMethod(nagramDb!!, "queryFinalized",
                    "SELECT name FROM users WHERE uid=?", arrayOf<Any>(uid))
                if (raw != null) {
                    while (XposedHelpers.callMethod(raw, "next") as Boolean) {
                        val name = XposedHelpers.callMethod(raw, "stringValue", 0) as? String
                        if (!name.isNullOrBlank()) {
                            val clean = name.split(";;;").first().trim()
                            nagramUserCache[uid] = clean
                            return clean
                        }
                    }
                }
            } catch (_: Exception) { }
        }
        return uid.toString()
    }

    private fun extractSenderIdFromData(lpparam: XC_LoadPackage.LoadPackageParam, cv: ContentValues): Long? {
        try {
            val data = cv.getAsByteArray("data") ?: return null
            val serializedDataClass = XposedHelpers.findClass(
                "org.telegram.tgnet.SerializedData", lpparam.classLoader)
            val serializedData = serializedDataClass.getConstructor(ByteArray::class.java).newInstance(data)
            val constructor = XposedHelpers.callMethod(serializedData, "readInt32", true) as Int
            val tlrpcMessageClass = XposedHelpers.findClass(
                "org.telegram.tgnet.TLRPC\$Message", lpparam.classLoader)
            val msg = XposedHelpers.callStaticMethod(tlrpcMessageClass, "TLdeserialize",
                serializedData, constructor, true) ?: return null
            // Try long first (newer versions), then int (older versions), then Peer object
            val fromId = try {
                XposedHelpers.getObjectField(msg, "from_id") as Long
            } catch (_: Exception) {
                try {
                    (XposedHelpers.getObjectField(msg, "from_id") as Int).toLong()
                } catch (_: Exception) {
                    try {
                        val peer = XposedHelpers.getObjectField(msg, "from_id")
                        XposedHelpers.getObjectField(peer, "user_id") as Long
                    } catch (_: Exception) { 0L }
                }
            }
            return if (fromId != 0L) fromId else null
        } catch (_: Throwable) {
            return null
        }
    }

    private fun extractMessageTextFromData(lpparam: XC_LoadPackage.LoadPackageParam, cv: ContentValues): String? {
        try {
            val data = cv.getAsByteArray("data") ?: return null
            val serializedDataClass = XposedHelpers.findClass(
                "org.telegram.tgnet.SerializedData", lpparam.classLoader)
            val serializedData = serializedDataClass.getConstructor(ByteArray::class.java).newInstance(data)
            val constructor = XposedHelpers.callMethod(serializedData, "readInt32", true) as Int
            val tlrpcMessageClass = XposedHelpers.findClass(
                "org.telegram.tgnet.TLRPC\$Message", lpparam.classLoader)
            val msg = XposedHelpers.callStaticMethod(tlrpcMessageClass, "TLdeserialize",
                serializedData, constructor, true) ?: return null
            // Extract message text (message field always exists; caption may not)
            val message = try {
                XposedHelpers.getObjectField(msg, "message") as? String
            } catch (_: Throwable) { null }
            val caption = try {
                XposedHelpers.getObjectField(msg, "caption") as? String
            } catch (_: Throwable) { null }
            val text = when {
                !message.isNullOrBlank() && !caption.isNullOrBlank() -> "$message\n$caption"
                !message.isNullOrBlank() -> message
                !caption.isNullOrBlank() -> caption
                else -> null
            }
            return text
        } catch (_: Throwable) {
            return null
        }
    }
    private fun deserializeChatTitle(classLoader: ClassLoader, blob: ByteArray): String? {
        try {
            val serializedDataClass = XposedHelpers.findClass(
                "org.telegram.tgnet.SerializedData", classLoader)
            val serializedData = serializedDataClass.getConstructor(ByteArray::class.java).newInstance(blob)
            val constructor = XposedHelpers.callMethod(serializedData, "readInt32", true) as Int
            // Try TLRPC.Chat first, then TLRPC.ChatFull
            for (className in listOf("org.telegram.tgnet.TLRPC\$Chat", "org.telegram.tgnet.TLRPC\$ChatFull")) {
                try {
                    val chatClass = XposedHelpers.findClass(className, classLoader)
                    val chat = XposedHelpers.callStaticMethod(chatClass, "TLdeserialize",
                        serializedData, constructor, true) ?: continue
                    val title = XposedHelpers.getObjectField(chat, "title") as? String
                    if (!title.isNullOrBlank()) return title.split(";;;").first().trim()
                } catch (_: Throwable) { }
            }
            return null
        } catch (e: Throwable) {
            Log.d(TAG, "deserializeChatTitle: ${e.message}")
            return null
        }
    }

    private fun saveNagramMessage(
        context: Context, pkg: String, chatId: String, senderName: String,
        chatName: String, timestamp: Long, msgId: Long, mediaType: Int,
        textContent: String?, chatType: String
    ) {
        try {
            val mediaLabel = when (mediaType) {
                -1 -> null
                0 -> "[图片]"
                1 -> "[视频]"
                3 -> "[文件]"
                4 -> "[位置]"
                5 -> "[联系人]"
                10 -> "[贴纸]"
                13 -> "[语音]"
                14 -> "[音乐]"
                else -> "[消息类型: $mediaType]"
            }
            val content = when {
                !textContent.isNullOrBlank() && mediaLabel != null -> "$mediaLabel $textContent"
                !textContent.isNullOrBlank() -> textContent
                mediaLabel != null -> mediaLabel
                else -> "[空消息]"
            }
            Log.i(TAG, "[Nagram] [$chatName] [$senderName] ${content.take(60)}")

            val intent = Intent("dev.rcht.jist.SAVE_MESSAGE").apply {
                setClassName("dev.rcht.jist", "dev.rcht.jist.receiver.MessageReceiver")
                putExtra("pkg", pkg)
                putExtra("chatId", chatId)
                putExtra("senderName", senderName)
                putExtra("chatName", chatName)
                putExtra("content", content)
                putExtra("timestamp", timestamp)
                putExtra("msgSeq", msgId)
                putExtra("msgType", mediaType)
                putExtra("appDisplayName", appDisplayName)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.w(TAG, "saveNagramMessage error: ${e.message}")
        }
    }
}
