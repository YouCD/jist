package dev.rcht.jist.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import dev.rcht.jist.R
import dev.rcht.jist.data.db.JistDatabase
import dev.rcht.jist.data.db.entity.SummaryEntity
import kotlinx.coroutines.runBlocking

class SummaryWidgetRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent) = SummaryViewsFactory(this)
}

class SummaryViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var summaries: List<SummaryEntity> = emptyList()

    override fun onCreate() { loadData() }
    override fun onDataSetChanged() { loadData() }
    override fun onDestroy() {}

    private fun loadData() {
        summaries = runBlocking {
            try {
                val db = JistDatabase.getInstance(context)
                db.summaryDao().getAll().take(10)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    override fun getCount() = summaries.size

    override fun getViewAt(position: Int): RemoteViews {
        val summary = summaries[position]
        val views = RemoteViews(context.packageName, R.layout.widget_summary_item)

        views.setTextViewText(R.id.item_app_name, summary.appName)
        views.setTextViewText(R.id.item_contact, summary.contactOrGroup)
        views.setTextViewText(R.id.item_summary, summary.summaryText.take(200))

        try {
            val drawable = context.packageManager.getApplicationIcon(summary.packageName)
            val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, 48, 48)
            drawable.draw(canvas)
            views.setImageViewBitmap(R.id.item_icon, bitmap)
        } catch (e: Exception) {
            views.setImageViewResource(R.id.item_icon, R.mipmap.ic_launcher)
        }

        val fillIntent = Intent().apply {
            putExtra("summary_id", summary.id.toString())
        }
        views.setOnClickFillInIntent(R.id.widget_item_root, fillIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) = position.toLong()
    override fun hasStableIds() = true
}
