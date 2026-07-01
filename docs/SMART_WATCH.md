# Smart Watch — 智能关注

## 概述

**Smart Watch** 是 Jist 的 AI 驱动的关注功能。用户可以设定一个关注主题（如"买冰箱"、"看房"、"特斯拉股票"），AI 在新通知到达时自动筛选相关内容，持续收集并主动推送进展。

不同于通知摘要（Summarization）的按 conversationKey 分组，Smart Watch 是**跨应用、跨对话**的内容收集，核心价值在于帮用户从碎片化信息中追踪特定事物。

---

## 架构概览

```
┌─────────────────────────────────────────────────────────┐
│                       UI Layer                           │
│  WatchListScreen · WatchDetailScreen · WatchEditScreen   │
├─────────────────────────────────────────────────────────┤
│                     Domain Layer                         │
│  WatchEngine · WatchMatcher                              │
├─────────────────────────────────────────────────────────┤
│                     Data Layer                           │
│  WatchTopicEntity · WatchCollectedItemEntity             │
│  WatchTopicDao · WatchCollectedItemDao                   │
│  WatchTopicRepository · WatchCollectedItemRepository     │
├─────────────────────────────────────────────────────────┤
│                  Android Platform                        │
│  NotificationListenerService                             │
└─────────────────────────────────────────────────────────┘
```

---

## 新增组件

| Component | Role |
|---|---|
| `WatchEngine` | 核心编排器。通知到达时触发匹配 |
| `WatchMatcher` | 匹配引擎。关键词本地过滤 + 可选 LLM 语义匹配 |
| `WatchTopicEntity` | 关注主题实体 |
| `WatchCollectedItemEntity` | 收集条目实体 |
| `WatchTopicDao` | Watch 主题 CRUD |
| `WatchCollectedItemDao` | 收集条目 CRUD + 统计 |
| `WatchTopicRepository` | Watch 主题 Repository |
| `WatchCollectedItemRepository` | 收集条目 Repository |

---

## 数据流程

仅一条数据通路：**实时匹配**。

```
Notification arrives
    │
    ▼
JistNotificationListenerService.onNotificationPosted()
    │
    ├── 已有逻辑: 解析、去重、入库
    │
    ▼
WatchEngine.matchNewNotification(notification)
    │
    ├── 1. 获取所有活跃的 WatchTopic
    ├── 2. WatchMatcher 关键词过滤
    │     通知内容/标题 包含任一关键词 → 候选
    ├── 3. 仅关键词模式 → 直接收录
    ├── 4. AI 模式 → 调 LLM 判断语义相关 + 提取关键信息
    │
    ▼
WatchCollectedItemDao.insert()
```

---

## 数据库设计

### `watch_topics` 表

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | Long | PRIMARY KEY, auto-generate | 唯一标识 |
| `title` | String | NOT NULL | 关注标题，如"买冰箱" |
| `description` | String | NOT NULL, default "" | 详细描述，辅助 AI 理解上下文 |
| `keywords` | String | NOT NULL | JSON 字符串数组，如 `["冰箱","海尔","双开门"]` |
| `matchMode` | String | NOT NULL, default "KEYWORD_ONLY" | `KEYWORD_ONLY` / `AI_SEMANTIC` |
| `notifyMode` | String | NOT NULL, default "IMPORTANT_ONLY" | `EACH_TIME` / `DAILY_DIGEST` / `IMPORTANT_ONLY` |
| `isEnabled` | Boolean | NOT NULL, default true | 启用/暂停 |
| `createdAt` | Long | NOT NULL | 创建时间戳 |
| `updatedAt` | Long | NOT NULL | 最后更新时间戳 |

### `watch_collected_items` 表

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | Long | PRIMARY KEY, auto-generate | 唯一标识 |
| `topicId` | Long | NOT NULL, FK, INDEX | 所属关注主题 |
| `notificationId` | Long | NOT NULL, FK, INDEX | 来源通知 |
| `sourceApp` | String | NOT NULL | 来源应用包名 |
| `matchedKeyword` | String | NOT NULL | 触发的关键词 |
| `matchType` | String | NOT NULL | `KEYWORD` / `AI_SEMANTIC` |
| `aiExtractedInfo` | String? | | AI 提取的关键信息 |
| `importance` | Int | NOT NULL, default 3 | 重要性 1-5 |
| `matchedAt` | Long | NOT NULL | 匹配时间戳 |
| `isRead` | Boolean | NOT NULL, default false | 用户是否已查看 |

**Unique Index：** `(topicId, notificationId)` — 同一条通知不重复收录

---

## 匹配引擎

### 匹配逻辑

```kotlin
class WatchMatcher {
    
    fun matches(
        notification: NotificationEntity,
        topic: WatchTopic
    ): String? {
        val text = "${notification.title} ${notification.content}"
        
        for (keyword in topic.keywords) {
            if (text.contains(keyword, ignoreCase = true)) {
                return keyword
            }
        }
        
        return null
    }
}
```

### AI 语义匹配 Prompt

```kotlin
fun buildSemanticPrompt(notification: NotificationEntity, topic: WatchTopic): List<ChatMessage> {
    return listOf(
        ChatMessage("system", 
            "你是一个信息收集助手。用户正在关注以下主题：\n" +
            "「${topic.title}」\n" +
            "${topic.description}\n\n" +
            "请判断以下通知是否与用户关注的主题相关。" +
            "如果相关，提取关键信息并以 JSON 格式返回：\n" +
            """{"relevant":true,"keyInfo":"提取的关键信息","importance":1-5}""" +
            "\n如果不相关，返回：\n" +
            """{"relevant":false}"""
        ),
        ChatMessage("user",
            "通知来源：${notification.appName}\n" +
            "标题：${notification.title}\n" +
            "内容：${notification.content}"
        )
    )
}
```

---

## UI 设计

### 新增页面

| 页面 | 路由 | 说明 |
|------|------|------|
| `WatchListScreen` | `watch_list` | 关注列表，Dashboard 入口 + 底部 Tab |
| `WatchEditScreen` | `watch_edit?watchId={id}` | 新建/编辑关注，含关键词标签管理 |
| `WatchDetailScreen` | `watch_detail/{watchId}` | 某个关注的收集详情 |

### WatchListScreen

```
┌──────────────────────────────────────────┐
│  ←       我的关注                 +      │
├──────────────────────────────────────────┤
│                                          │
│  ┌────────────────────────────────────┐  │
│  │ 🔍 买冰箱                     ● 活跃│  │
│  │ 预算4000-6000，双开门               │  │
│  │ ────────────────────────────────── │  │
│  │ 已收集 8 条 · 最新: 京东海尔5299   │  │
│  │ 2小时前更新                         │  │
│  └────────────────────────────────────┘  │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │ 🏠 看房                      ● 活跃│  │
│  │ 朝阳区三居室，总价500-700万          │  │
│  │ ────────────────────────────────── │  │
│  │ 已收集 15 条 · 最新: 望京业主急售   │  │
│  │ 30分钟前更新                        │  │
│  └────────────────────────────────────┘  │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │ 📈 特斯拉                    ⏸ 暂停│  │
│  │ 财报、交付量、股价                   │  │
│  │ ────────────────────────────────── │  │
│  │ 已收集 23 条 · 最新: Q2交付量超预期│  │
│  │ 昨天更新                            │  │
│  └────────────────────────────────────┘  │
│                                          │
│                    [＋] (FAB)             │
└──────────────────────────────────────────┘
```

**空状态：**

```
┌──────────────────────────────────────────┐
│                                          │
│              🔍                          │
│                                          │
│       还没有任何关注                       │
│                                          │
│   设定关注主题，AI 会自动从你的通知中       │
│   收集相关信息并整理提醒。                 │
│                                          │
│          ┌──────────────────┐            │
│          │  ＋ 创建第一个关注  │            │
│          └──────────────────┘            │
│                                          │
└──────────────────────────────────────────┘
```

### WatchEditScreen

```
┌──────────────────────────────────────────┐
│  ←       新建关注          ✓ 保存        │
├──────────────────────────────────────────┤
│                                          │
│  关注标题                                 │
│  ┌────────────────────────────────────┐  │
│  │ 例: 买冰箱                         │  │
│  └────────────────────────────────────┘  │
│                                          │
│  详细描述                                 │
│  ┌────────────────────────────────────┐  │
│  │ 预算4000-6000，双开门，一级能效    │  │
│  └────────────────────────────────────┘  │
│                                          │
│  ── 关键词 ─────────────────             │
│                                          │
│  ┌ 添加关键词 ──────────────── [+ 添加] ┐│
│  │ 海尔                          ↵ 回车 ││
│  └──────────────────────────────────────┘│
│                                          │
│  [冰箱 ✕] [海尔 ✕] [容声 ✕] [美的 ✕]    │
│  [西门子 ✕] [双开门 ✕]                   │
│                                          │
│  ── 匹配设置 ─────────────────           │
│                                          │
│  ○ 仅关键词匹配（本地，更快）              │
│  ● AI 语义匹配（更精准）                   │
│                                          │
│  ── 通知方式 ─────────────────           │
│  [ 有重要信息时提醒 ▼ ]                   │
│                                          │
│  [测试匹配]     [保存]                    │
│                                          │
└──────────────────────────────────────────┘
```

**关键词添加交互：**

```
1. 用户在输入框输入关键词
2. 点击 [+ 添加] 或按回车键
3. 关键词以 Chip 形式出现在输入框下方
4. 点击 Chip 上的 ✕ 移除（带动画）
5. 重复关键词自动去重（提示"已存在"）
```

**关键词 Chip 组件：**

```kotlin
@Composable
fun KeywordChip(
    text: String,
    onRemove: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandHorizontally(),
        exit = fadeOut() + shrinkHorizontally()
    ) {
        AssistChip(
            onClick = {},
            label = { Text(text) },
            trailingIcon = {
                IconButton(onClick = { onRemove() }) {
                    Icon(Icons.Default.Close, contentDescription = "删除", modifier = Modifier.size(14.dp))
                }
            },
            shape = RoundedCornerShape(20.dp),
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        )
    }
}
```

### WatchDetailScreen

```
┌──────────────────────────────────────────┐
│  ←       买冰箱          [暂停] [编辑]   │
├──────────────────────────────────────────┤
│                                          │
│  关键词: 冰箱 海尔 容声 美的 ...          │
│  匹配模式: AI 语义 · 通知: 重要时提醒     │
│                                          │
│  ── 收集摘要 ────────────────────────    │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │ 已收集 8 条信息，来自 3 个应用        │  │
│  │                                    │  │
│  │ [AI 整理] 目前找到的冰箱信息：        │  │
│  │ 京东海尔双开门 5299 元（限时）       │  │
│  │ 容声十字对开门 3999 元               │  │
│  │ 苏宁以旧换新补贴 500 元              │  │
│  └────────────────────────────────────┘  │
│                                          │
│  ── 详细记录 ────────────────────────    │
│                                          │
│  今天                                    │
│  ┌────────────────────────────────────┐  │
│  │ 📄 京东海尔双开门冰箱限时5299元    │  │
│  │ WhatsApp - 家庭群 · 2小时前 · ⭐⭐⭐│  │
│  └────────────────────────────────────┘  │
│                                          │
│  昨天                                    │
│  ┌────────────────────────────────────┐  │
│  │ 📄 容声十字对开门3999              │  │
│  │ WhatsApp - 家庭群 · 1天前 · ⭐⭐   │  │
│  └────────────────────────────────────┘  │
│  ┌────────────────────────────────────┐  │
│  │ 📄 苏宁以旧换新补贴通知             │  │
│  │ 短信 · 1天前 · ⭐                  │  │
│  └────────────────────────────────────┘  │
│                                          │
└──────────────────────────────────────────┘
```

### 底部导航扩展

```kotlin
// JistApp.kt 的 bottomNavItems() 中新增
BottomNavItem(
    label = "关注",
    route = Screen.WatchList.route,
    selectedIcon = Icons.Filled.Visibility,
    unselectedIcon = Icons.Outlined.Visibility
)
```

### 导航路由

```kotlin
// Screen.kt
data object WatchList : Screen("watch_list")
data object WatchEdit : Screen("watch_edit?watchId={watchId}") {
    fun createRoute(watchId: Long? = null) = "watch_edit?watchId=$watchId"
}
data object WatchDetail : Screen("watch_detail/{watchId}") {
    fun createRoute(watchId: Long) = "watch_detail/$watchId"
}
```

---

## 微件（Widget）

提供一个桌面微件，在桌面直接展示所有关注主题的最新收集状态。

### 交互

```
┌──────────────────────────────────────────────┐
│  🔍 我的关注                         Jist    │  ← 标题行，点击打开 WatchList
├──────────────────────────────────────────────┤
│                                              │
│  🔍 买冰箱                         3小时前   │  ← 每个关注一行
│  已收集 8 条 · 最新: 京东海尔5299元          │
│  ──────────────────────────────────────      │
│  🏠 看房                         30分钟前    │
│  已收集 15 条 · 最新: 望京业主急售620万      │
│  ──────────────────────────────────────      │
│  📈 特斯拉                        昨天       │
│  已收集 23 条 · 最新: Q2交付量超预期        │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │  ＋ 创建关注                            │  │  ← 底部按钮
│  └────────────────────────────────────────┘  │
└──────────────────────────────────────────────┘
```

| 操作 | 行为 |
|------|------|
| 点击顶部标题栏 | 打开 `WatchListScreen` |
| 点击某一行关注 | 打开对应 `WatchDetailScreen(watchId)` |
| 点击底部"创建关注" | 打开 `WatchEditScreen(new)` |
| 空状态 | 居中显示"暂无关注，点击创建" |

### 布局文件

**`widget_watch.xml`（根布局）：**

```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="12dp"
    android:background="@android:color/transparent">

    <!-- 标题行 -->
    <TextView
        android:id="@+id/widget_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="🔍 我的关注"
        android:textSize="16sp"
        android:textColor="#FFFFFF"
        android:textStyle="bold"
        android:paddingBottom="8dp" />

    <!-- 关注列表 -->
    <ListView
        android:id="@+id/widget_list"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:divider="#1AFFFFFF"
        android:dividerHeight="1dp" />

    <!-- 底部操作栏 -->
    <TextView
        android:id="@+id/widget_create"
        android:layout_width="match_parent"
        android:layout_height="40dp"
        android:text="＋ 创建关注"
        android:textSize="13sp"
        android:textColor="#00E5FF"
        android:gravity="center"
        android:paddingTop="6dp" />

</LinearLayout>
```

**`widget_watch_item.xml`（列表项）：**

```xml
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:clickable="true"
    android:focusable="true">

    <LinearLayout
        android:id="@+id/widget_item_root"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="8dp"
        android:duplicateParentState="true">

        <!-- 第一行：图标 + 标题 + 时间 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical">

            <TextView
                android:id="@+id/item_icon"
                android:layout_width="24dp"
                android:layout_height="24dp"
                android:textSize="16sp"
                android:gravity="center" />

            <TextView
                android:id="@+id/item_title"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:layout_marginStart="8dp"
                android:textSize="14sp"
                android:textColor="#FFFFFF"
                android:textStyle="bold"
                android:maxLines="1"
                android:ellipsize="end" />

            <TextView
                android:id="@+id/item_time"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textSize="11sp"
                android:textColor="#88FFFFFF" />

        </LinearLayout>

        <!-- 第二行：统计 + 最新预览 -->
        <TextView
            android:id="@+id/item_preview"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:layout_marginStart="32dp"
            android:textSize="12sp"
            android:textColor="#CCFFFFFF"
            android:maxLines="1"
            android:ellipsize="end" />

    </LinearLayout>

</FrameLayout>
```

### Widget Info

```xml
<!-- res/xml/watch_widget_info.xml -->
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="300dp"
    android:minHeight="120dp"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/widget_watch"
    android:resizeMode="vertical|horizontal"
    android:widgetCategory="home_screen"
    android:description="显示关注主题的最新收集状态" />
```

### WatchWidgetProvider

```kotlin
class WatchWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateWidget(context, appWidgetManager, id)
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_watch)

        // 标题点击 → WatchListScreen
        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("open_page", "watch_list")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        views.setOnClickPendingIntent(R.id.widget_title, PendingIntent.getActivity(
            context, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        ))

        // 创建按钮 → WatchEditScreen
        val createIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("open_page", "watch_edit")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        views.setOnClickPendingIntent(R.id.widget_create, PendingIntent.getActivity(
            context, 1, createIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        ))

        // 列表数据
        val listIntent = Intent(context, WatchWidgetRemoteViewsService::class.java)
        views.setRemoteAdapter(R.id.widget_list, listIntent)

        val templateIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("open_page", "watch_detail")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        views.setPendingIntentTemplate(R.id.widget_list, PendingIntent.getActivity(
            context, appWidgetId, templateIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        ))

        appWidgetManager.updateAppWidget(appWidgetId, views)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
    }

    companion object {
        fun refreshWidget(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WatchWidgetProvider::class.java))
            for (id in ids) manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
        }
    }
}
```

### WatchWidgetRemoteViewsService

```kotlin
class WatchWidgetRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent) = WatchViewsFactory(this)
}

class WatchViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<WatchWidgetItem> = emptyList()

    data class WatchWidgetItem(
        val topicId: Long,
        val icon: String,        // 显示的 emoji
        val title: String,
        val collectedCount: Int,
        val latestPreview: String,
        val timeAgo: String
    )

    override fun onCreate() { loadData() }
    override fun onDataSetChanged() { loadData() }

    private fun loadData() {
        items = runBlocking {
            try {
                val db = JistDatabase.getInstance(context)
                val topics = db.watchTopicDao().getActiveTopics()
                topics.map { topic ->
                    val stats = db.watchCollectedItemDao().getStatsForTopic(topic.id)
                    WatchWidgetItem(
                        topicId = topic.id,
                        icon = "🔍",
                        title = topic.title,
                        collectedCount = stats.count,
                        latestPreview = stats.latestPreview ?: "暂无信息",
                        timeAgo = formatTimeAgo(stats.latestMatchedAt)
                    )
                }
            } catch (e: Exception) { emptyList() }
        }
    }

    override fun getCount() = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = items[position]
        val views = RemoteViews(context.packageName, R.layout.widget_watch_item)

        views.setTextViewText(R.id.item_icon, item.icon)
        views.setTextViewText(R.id.item_title, item.title)
        views.setTextViewText(R.id.item_time, item.timeAgo)
        views.setTextViewText(R.id.item_preview,
            "已收集 ${item.collectedCount} 条 · 最新: ${item.latestPreview}")

        val fillIntent = Intent().apply { putExtra("watch_id", item.topicId) }
        views.setOnClickFillInIntent(R.id.widget_item_root, fillIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) = position.toLong()
    override fun hasStableIds() = true
}
```

### 刷新时机

| 触发条件 | 操作 |
|---------|------|
| 新通知匹配到收集条目 | `WatchWidgetProvider.refreshWidget(context)` |
| 用户创建/删除 Watch | `WatchWidgetProvider.refreshWidget(context)` |
| Widget 周期性更新 | 系统每 30 分钟自动触发 |

在 `WatchEngine.collectItem()` 末尾调用 `refreshWidget()`：

```kotlin
// WatchEngine.kt
suspend fun collectItem(...) {
    watchCollectedItemDao.insert(item)
    // 刷新桌面微件
    withContext(Dispatchers.Main) {
        WatchWidgetProvider.refreshWidget(context)
    }
}
```

### AndroidManifest 注册

```xml
<receiver
    android:name=".widget.WatchWidgetProvider"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/watch_widget_info" />
</receiver>

<service
    android:name=".widget.WatchWidgetRemoteViewsService"
    android:exported="false"
    android:permission="android.permission.BIND_REMOTEVIEWS" />
```

---

## 实现路线

| # | 内容 | 文件 |
|---|------|------|
| 1 | Entity: WatchTopicEntity, WatchCollectedItemEntity | `data/db/entity/` |
| 2 | DAO: WatchTopicDao, WatchCollectedItemDao | `data/db/dao/` |
| 3 | 注册到 JistDatabase + 版本升级 | `data/db/JistDatabase.kt` |
| 4 | Repository | `data/repository/` |
| 5 | WatchEngine + WatchMatcher | `engine/` |
| 6 | 集成到 NotificationListenerService | `service/JistNotificationListenerService.kt` |
| 7 | WatchListScreen + WatchCard | `ui/screens/WatchListScreen.kt` |
| 8 | WatchEditScreen + KeywordInputSection | `ui/screens/WatchEditScreen.kt` |
| 9 | WatchDetailScreen | `ui/screens/WatchDetailScreen.kt` |
| 10 | ViewModel | `ui/watchlist/`, `ui/watchedit/`, `ui/watchdetail/` |
| 11 | 导航路由 + 底部 Tab | `ui/navigation/Screen.kt`, `ui/JistApp.kt` |
| 12 | Widget 布局: widget_watch.xml, widget_watch_item.xml | `res/layout/` |
| 13 | Widget info: watch_widget_info.xml | `res/xml/` |
| 14 | WatchWidgetProvider | `widget/WatchWidgetProvider.kt` |
| 15 | WatchWidgetRemoteViewsService | `widget/WatchWidgetRemoteViewsService.kt` |
| 16 | AndroidManifest 注册 | `AndroidManifest.xml` |
| 17 | WatchEngine 末尾调用 refreshWidget() | `engine/WatchEngine.kt` |
