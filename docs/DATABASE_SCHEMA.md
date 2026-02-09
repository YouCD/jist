# Database Schema

## Overview

Jist uses **Room** (SQLite) for persistent storage of notifications, summaries, app rules, and LLM configurations. **DataStore** is used for global preferences.

---

## Entity Relationship Diagram

```
┌──────────────────┐       ┌──────────────────┐
│   notifications   │──────▶│    summaries      │
│                  │  M:1  │                  │
├──────────────────┤       ├──────────────────┤
│ id (PK)          │       │ id (PK)          │
│ packageName      │       │ conversationKey  │
│ appName          │       │ appName          │
│ title            │       │ contactOrGroup   │
│ content          │       │ summaryText      │
│ conversationKey  │       │ messageCount     │
│ timestamp        │       │ modelUsed        │
│ isSummarized     │       │ tokenCount       │
│ summaryId (FK)   │       │ createdAt        │
└──────────────────┘       └──────────────────┘

┌──────────────────┐       ┌──────────────────┐
│    app_rules      │       │   llm_configs     │
├──────────────────┤       ├──────────────────┤
│ id (PK)          │       │ id (PK)          │
│ packageName (UQ) │       │ name             │
│ appName          │       │ provider         │
│ enabled          │       │ apiKey (encrypted)│
│ mode             │       │ baseUrl          │
│ batchWindowMin   │       │ modelId          │
│ minMessages      │       │ isDefault        │
│ customPrompt     │       │ maxTokens        │
└──────────────────┘       │ temperature      │
                           └──────────────────┘
```

---

## `notifications` Table

Stores every captured notification.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | Long | PRIMARY KEY, auto-generate | Unique identifier |
| `packageName` | String | NOT NULL | App package name (e.g., `com.whatsapp`) |
| `appName` | String | NOT NULL | Human-readable app name |
| `title` | String | NOT NULL | Notification title (contact/group name) |
| `content` | String | NOT NULL | Notification text body |
| `conversationKey` | String | NOT NULL, INDEX | Derived grouping key: `packageName:title` |
| `timestamp` | Long | NOT NULL, INDEX | When notification was received (epoch ms) |
| `isSummarized` | Boolean | NOT NULL, default false | Whether included in a summary |
| `summaryId` | Long? | FOREIGN KEY → summaries.id | FK to the summary that includes this notification |

**Indices:**
- `idx_notifications_conversation_key` on `conversationKey`
- `idx_notifications_timestamp` on `timestamp`
- `idx_notifications_package` on `packageName`
- `idx_notifications_unsummarized` on `(conversationKey, isSummarized)` WHERE `isSummarized = false`

**DAO Operations:**
```kotlin
@Dao
interface NotificationDao {
    @Insert
    suspend fun insert(notification: NotificationEntity): Long

    @Query("SELECT * FROM notifications WHERE conversationKey = :key AND isSummarized = 0 ORDER BY timestamp ASC")
    suspend fun getUnsummarizedForKey(key: String): List<NotificationEntity>

    @Query("SELECT conversationKey, COUNT(*) as count FROM notifications WHERE isSummarized = 0 GROUP BY conversationKey HAVING count >= :minCount")
    suspend fun getPendingConversations(minCount: Int): List<PendingConversation>

    @Query("UPDATE notifications SET isSummarized = 1, summaryId = :summaryId WHERE id IN (:ids)")
    suspend fun markSummarized(ids: List<Long>, summaryId: Long)

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getRecent(limit: Int, offset: Int): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE packageName = :packageName ORDER BY timestamp DESC")
    suspend fun getByApp(packageName: String): List<NotificationEntity>

    @Query("DELETE FROM notifications WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()
}
```

---

## `summaries` Table

Stores generated summaries.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | Long | PRIMARY KEY, auto-generate | Unique identifier |
| `conversationKey` | String | NOT NULL, INDEX | Which conversation this summarizes |
| `appName` | String | NOT NULL | Source app display name |
| `contactOrGroup` | String | NOT NULL | Contact or group name |
| `summaryText` | String | NOT NULL | LLM-generated summary text |
| `messageCount` | Int | NOT NULL | How many notifications were summarized |
| `modelUsed` | String | NOT NULL | Which model produced this (e.g., `gpt-4o-mini`) |
| `tokenCount` | Int? | | Tokens used (if reported by API) |
| `createdAt` | Long | NOT NULL, INDEX | When summary was generated (epoch ms) |

**DAO Operations:**
```kotlin
@Dao
interface SummaryDao {
    @Insert
    suspend fun insert(summary: SummaryEntity): Long

    @Query("SELECT * FROM summaries ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getRecent(limit: Int, offset: Int): List<SummaryEntity>

    @Query("SELECT * FROM summaries WHERE conversationKey = :key ORDER BY createdAt DESC")
    suspend fun getForConversation(key: String): List<SummaryEntity>

    @Query("SELECT * FROM summaries WHERE appName = :appName ORDER BY createdAt DESC")
    suspend fun getByApp(appName: String): List<SummaryEntity>

    @Query("SELECT * FROM summaries WHERE summaryText LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    suspend fun search(query: String): List<SummaryEntity>

    @Query("SELECT SUM(tokenCount) FROM summaries WHERE createdAt >= :since")
    suspend fun getTotalTokensSince(since: Long): Int?

    @Delete
    suspend fun delete(summary: SummaryEntity)

    @Query("DELETE FROM summaries WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
```

---

## `app_rules` Table

Per-app configuration rules.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | Long | PRIMARY KEY, auto-generate | Unique identifier |
| `packageName` | String | NOT NULL, UNIQUE | Target app package name |
| `appName` | String | NOT NULL | Display name |
| `enabled` | Boolean | NOT NULL, default true | Whether to process this app's notifications |
| `mode` | String (Enum) | NOT NULL, default "AUTO" | `AUTO` / `MANUAL` / `DISABLED` |
| `batchWindowMinutes` | Int | NOT NULL, default 15 | Collect notifications for this long before summarizing |
| `minMessagesForSummary` | Int | NOT NULL, default 3 | Minimum messages before triggering summary |
| `customPrompt` | String? | | Per-app system prompt override |

**Mode Enum:**
- `AUTO` — Automatically summarize when thresholds are met
- `MANUAL` — Show "Summarize" button notification, user decides
- `DISABLED` — Capture but don't summarize

**DAO Operations:**
```kotlin
@Dao
interface AppRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: AppRuleEntity)

    @Query("SELECT * FROM app_rules WHERE packageName = :packageName")
    suspend fun getForApp(packageName: String): AppRuleEntity?

    @Query("SELECT * FROM app_rules WHERE enabled = 1")
    suspend fun getEnabledApps(): List<AppRuleEntity>

    @Query("SELECT * FROM app_rules ORDER BY appName ASC")
    suspend fun getAll(): List<AppRuleEntity>

    @Delete
    suspend fun delete(rule: AppRuleEntity)
}
```

---

## `llm_configs` Table

LLM provider configurations.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | Long | PRIMARY KEY, auto-generate | Unique identifier |
| `name` | String | NOT NULL | User-given name (e.g., "My GPT-4o") |
| `provider` | String (Enum) | NOT NULL | `OPENAI` / `GEMINI` / `CLAUDE` / `OPENROUTER` / `CUSTOM` |
| `apiKey` | String | NOT NULL | Encrypted API key |
| `baseUrl` | String | NOT NULL | API endpoint URL |
| `modelId` | String | NOT NULL | Model identifier (e.g., `gpt-4o`, `gemini-2.0-flash`) |
| `isDefault` | Boolean | NOT NULL, default false | Whether this is the active model |
| `maxTokens` | Int | NOT NULL, default 512 | Max response tokens |
| `temperature` | Float | NOT NULL, default 0.3 | Temperature (0.0 - 2.0) |

**Provider Defaults:**

| Provider | Base URL | Default Model |
|---|---|---|
| OPENAI | `https://api.openai.com` | `gpt-4o-mini` |
| GEMINI | `https://generativelanguage.googleapis.com` | `gemini-2.0-flash` |
| CLAUDE | `https://api.anthropic.com` | `claude-3-5-haiku-20241022` |
| OPENROUTER | `https://openrouter.ai/api` | (user selects) |
| CUSTOM | (user provides) | (user provides) |

**DAO Operations:**
```kotlin
@Dao
interface LlmConfigDao {
    @Insert
    suspend fun insert(config: LlmConfigEntity): Long

    @Update
    suspend fun update(config: LlmConfigEntity)

    @Query("SELECT * FROM llm_configs WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): LlmConfigEntity?

    @Query("SELECT * FROM llm_configs ORDER BY name ASC")
    suspend fun getAll(): List<LlmConfigEntity>

    @Query("UPDATE llm_configs SET isDefault = 0")
    suspend fun clearDefaults()

    @Delete
    suspend fun delete(config: LlmConfigEntity)
}
```

---

## DataStore Preferences

Global app preferences stored in `DataStore<Preferences>`.

```kotlin
data class JistPreferences(
    val isOnboardingComplete: Boolean = false,
    val defaultMode: SummarizeMode = SummarizeMode.AUTO,
    val defaultBatchWindowMinutes: Int = 15,
    val defaultMinMessages: Int = 3,
    val autoDeleteNotificationsAfterDays: Int = 7,     // 0 = never
    val autoDeleteSummariesAfterDays: Int = 30,         // 0 = never
    val deleteRawAfterSummarizing: Boolean = false,
    val theme: AppTheme = AppTheme.SYSTEM,              // SYSTEM / LIGHT / DARK
    val summaryNotificationSound: Boolean = true,
    val summaryNotificationVibrate: Boolean = true,
)
```

---

## Migration Strategy

- **Version 1**: Initial schema (Phase 1-2)
- **Version 2+**: Use Room's `Migration` objects for schema changes
- Destructive migration fallback disabled in production — always write proper migrations
