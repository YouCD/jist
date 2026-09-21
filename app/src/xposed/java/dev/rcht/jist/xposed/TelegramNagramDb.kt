@file:Suppress("unused")

package dev.rcht.jist.xposed

import android.database.Cursor
import android.util.Log
import java.lang.ClassLoader

internal var lastClassLoader: ClassLoader? = null

internal fun loadNagramCache() {
    try {
        val db = TelegramNagram.nagramDb ?: return
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
            val ctx = currentContext()
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
                        TelegramNagram.nagramUserCache[uid] = name
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
                        TelegramNagram.nagramChatCache[uid] = name
                        count++
                    }
                }
                Log.i(TAG, "loadNagramCache: chats $count entries")
            }
        } catch (e: Exception) {
            Log.w(TAG, "loadNagramCache chats: ${e.message}")
        }
        Log.i(TAG, "loadNagramCache done: ${TelegramNagram.nagramUserCache.size} users, ${TelegramNagram.nagramChatCache.size} chats")
    } catch (e: Exception) {
        Log.w(TAG, "loadNagramCache: ${e.message}")
    }
}

internal fun loadNagramCacheTelegram(db: Any) {
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
            TelegramNagram.messagesV2Columns.clear()
            TelegramNagram.messagesV2Columns.addAll(cols)
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
                    TelegramNagram.nagramUserCache[uid] = name.split(";;;").first().trim()
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
                    TelegramNagram.nagramChatCache[uid] = name.split(";;;").first().trim()
                    count++
                }
            }
            Log.i(TAG, "loadNagramCacheTelegram: chats $count entries")
        }
    } catch (e: Exception) {
        Log.w(TAG, "loadNagramCacheTelegram chats: ${e.message}")
    }
    Log.i(TAG, "loadNagramCacheTelegram done: ${TelegramNagram.nagramUserCache.size} users, ${TelegramNagram.nagramChatCache.size} chats")
}

/**
 * Runs [sql] against the captured Nagram DB and returns the first column of
 * every row.
 *
 * Telegram forks use either the in-house org.telegram.SQLite wrapper
 * (queryFinalized + next()/stringValue()) or a standard sqlite DB (WCDB /
 * sqlcipher / android — rawQuery + android.database.Cursor). This helper
 * speaks both, so name resolution works for every variant.
 */
internal fun nagramQueryFirstColumn(db: Any, sql: String, vararg stringArgs: String?): List<String> {
    val isTgWrapper = db.javaClass.name.contains("telegram.SQLite")
    val result = try {
        if (isTgWrapper) {
            XposedHelpers.callMethod(db, "queryFinalized", sql,
                if (stringArgs.isEmpty()) emptyArray<Any>() else stringArgs.map { it ?: "null" }.toTypedArray())
        } else {
            XposedHelpers.callMethod(db, "rawQuery", sql,
                if (stringArgs.isEmpty()) null else stringArgs.map { it ?: "null" }.toTypedArray())
        }
    } catch (_: Exception) {
        return emptyList()
    }
    val out = mutableListOf<String>()
    try {
        if (isTgWrapper) {
            while (XposedHelpers.callMethod(result, "next") as Boolean) {
                val v = XposedHelpers.callMethod(result, "stringValue", 0) as? String
                if (!v.isNullOrBlank()) out.add(v)
            }
        } else {
            (result as? Cursor)?.use { c ->
                while (c.moveToNext()) {
                    if (!c.isNull(0)) {
                        val v = c.getString(0)
                        if (v.isNotBlank()) out.add(v)
                    }
                }
            }
        }
    } catch (_: Exception) { }
    return out
}

internal fun resolveChatName(uid: Long, classLoader: ClassLoader? = null): String {
    val cached = TelegramNagram.nagramChatCache[uid]
    if (cached != null) return cached
    // Fallback: query chats table directly
    val db = TelegramNagram.nagramDb
    if (db != null) {
        try {
            val names = nagramQueryFirstColumn(db, "SELECT name FROM chats WHERE uid=?", uid.toString())
            if (names.isNotEmpty()) {
                val clean = names.first().split(";;;").first().trim()
                TelegramNagram.nagramChatCache[uid] = clean
                return clean
            }
            // Try the mirrored positive uid (chats table may use positive IDs)
            val absNames = nagramQueryFirstColumn(db, "SELECT name FROM chats WHERE uid=?", (-uid).toString())
            if (absNames.isNotEmpty()) {
                val clean = absNames.first().split(";;;").first().trim()
                TelegramNagram.nagramChatCache[uid] = clean
                TelegramNagram.nagramChatCache[-uid] = clean
                return clean
            }
            // Fallback: extract from data BLOB (Telegram wrapper only — the only
            // DB type whose `data` column holds TL-serialized objects)
            if (db.javaClass.name.contains("telegram.SQLite")) {
                try {
                    val raw2 = XposedHelpers.callMethod(db, "queryFinalized",
                        "SELECT data FROM chats WHERE uid=?", arrayOf<Any>(uid))
                    if (raw2 != null) {
                        while (XposedHelpers.callMethod(raw2, "next") as Boolean) {
                            val blob = XposedHelpers.callMethod(raw2, "byteArrayValue", 0) as? ByteArray
                            if (blob != null) {
                                val cl = classLoader ?: lastClassLoader
                                val title = if (cl != null) deserializeChatTitle(cl, blob) else null
                                if (title != null) {
                                    TelegramNagram.nagramChatCache[uid] = title
                                    return title
                                }
                            }
                        }
                    }
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }
    return uid.toString()
}

internal fun resolveUserName(uid: Long): String {
    val cached = TelegramNagram.nagramUserCache[uid]
    if (cached != null) return cached
    // Fallback: query users table directly
    val db = TelegramNagram.nagramDb
    if (db != null) {
        try {
            val names = nagramQueryFirstColumn(db, "SELECT name FROM users WHERE uid=?", uid.toString())
            if (names.isNotEmpty()) {
                val clean = names.first().split(";;;").first().trim()
                TelegramNagram.nagramUserCache[uid] = clean
                return clean
            }
        } catch (_: Exception) { }
    }
    return uid.toString()
}
