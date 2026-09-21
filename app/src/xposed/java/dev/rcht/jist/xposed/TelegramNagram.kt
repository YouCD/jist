@file:Suppress("unused")

package dev.rcht.jist.xposed

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.util.Log
import java.lang.ClassLoader

object TelegramNagram {
    internal var nagramDb: Any? = null
    internal val nagramUserCache = mutableMapOf<Long, String>()
    internal val nagramChatCache = mutableMapOf<Long, String>()
    internal var appDisplayName = "Nagram"
    internal val messagesV2Columns = mutableListOf<String>()
    internal val pendingMid = mutableMapOf<Int, Long>()
    internal val pendingUid = mutableMapOf<Int, Long>()
    internal var isQueryingDb = false

    fun setupNagram(loader: ClassLoader, pkg: String, displayName: String = "Nagram") {
        appDisplayName = displayName
        lastClassLoader = loader
        Log.i(TAG, "Setting up $displayName message hooks")
        // Try WCDB first — many Telegram forks use it for cache4.db
        try {
            val wcdbClass = XposedHelpers.findClass(
                "com.tencent.wcdb.database.SQLiteDatabase", loader)
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
                        onNagramMessageRow(loader, pkg, cv)
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
                "net.sqlcipher.database.SQLiteDatabase", loader)
            fun makeCipherHook(methodName: String) = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val table = param.args[0] as? String ?: return
                    if (nagramDb == null) {
                        nagramDb = param.thisObject
                        loadNagramCache()
                    }
                    if (table == "messages_v2") {
                        val cv = param.args[2] as? ContentValues ?: return
                        onNagramMessageRow(loader, pkg, cv)
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
                "org.telegram.SQLite.SQLiteDatabase", loader)


            // Telegram uses executeFast(String) -> SQLitePreparedStatement -> bindXXX -> step()
            val prepClass = XposedHelpers.findClass(
                "org.telegram.SQLite.SQLitePreparedStatement", loader)

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
                                            onNagramMessageRow(loader, pkg, cv)
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

        hookStandardSqliteDatabaseFallback(loader, pkg)
    }

    private fun hookStandardSqliteDatabaseFallback(loader: ClassLoader, pkg: String) {
        // Fallback: standard android.database.sqlite.SQLiteDatabase
        try {
            val dbClass = XposedHelpers.findClass(
                "android.database.sqlite.SQLiteDatabase", loader)

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
                        onNagramMessageRow(loader, pkg, cv)
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

    private fun onNagramMessageRow(loader: ClassLoader, pkg: String, cv: ContentValues) {
        try {
            val mid = cv.getAsLong("mid") ?: return
            val uid = cv.getAsLong("uid") ?: return
            val date = cv.getAsLong("date") ?: return
            val out = cv.getAsInteger("out") ?: 0
            val media = cv.getAsInteger("media") ?: -1

            // First try message/caption columns (old messages table), then extract from data BLOB
            var fullText = cv.getAsString("message") ?: cv.getAsString("caption")
            if (fullText.isNullOrBlank()) {
                fullText = extractMessageTextFromData(loader, cv)
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
                chatName = resolveChatName(uid, loader)
                // Try to extract actual sender from data blob for group messages
                val fromSenderId = if (out == 1) null else extractSenderIdFromData(loader, cv)
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

            val context = currentContext() ?: run {
                return
            }

            val timestamp = date * 1000L
            bgHandler.post {
                saveNagramMessage(context, pkg, uid.toString(), senderName, chatName,
                    timestamp, mid, media, fullText, chatType)
            }
        } catch (e: Exception) {
            Log.w(TAG, "onNagramMessageRow error: ${e.message}")
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
    internal var lastClassLoader: ClassLoader? = null
}
