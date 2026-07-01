package dev.rcht.jist.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import dev.rcht.jist.R
import dev.rcht.jist.data.db.JistDatabase
import kotlinx.coroutines.runBlocking

class WatchWidgetRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent) = WatchViewsFactory(this)
}

class WatchViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private data class WatchWidgetItem(
        val topicId: Long,
        val icon: String,
        val title: String,
        val collectedCount: Int,
        val latestPreview: String,
        val timeAgo: String
    )

    private var items: List<WatchWidgetItem> = emptyList()

    override fun onCreate() { loadData() }
    override fun onDataSetChanged() { loadData() }
    override fun onDestroy() {}

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
            } catch (_: Exception) { emptyList() }
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

    private fun formatTimeAgo(timestampMs: Long?): String {
        if (timestampMs == null) return ""
        val now = System.currentTimeMillis()
        val diff = now - timestampMs
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3_600_000 -> "${diff / 60_000}分钟前"
            diff < 86_400_000 -> "${diff / 3_600_000}小时前"
            else -> "${diff / 86_400_000}天前"
        }
    }
}
