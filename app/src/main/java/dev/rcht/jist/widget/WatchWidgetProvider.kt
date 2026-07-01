package dev.rcht.jist.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.annotation.SuppressLint
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.rcht.jist.MainActivity
import dev.rcht.jist.R

@SuppressLint("Deprecation")
class WatchWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateWidget(context, appWidgetManager, id)
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_watch)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("open_page", "watch_list")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        views.setOnClickPendingIntent(R.id.widget_title, PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        ))

        val createIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("open_page", "watch_edit")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        views.setOnClickPendingIntent(R.id.widget_create, PendingIntent.getActivity(
            context, 1, createIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        ))

        val listIntent = Intent(context, WatchWidgetRemoteViewsService::class.java)
        @Suppress("DEPRECATION")
        views.setRemoteAdapter(R.id.widget_list, listIntent)

        val templateIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("open_page", "watch_detail")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        views.setPendingIntentTemplate(R.id.widget_list, PendingIntent.getActivity(
            context, appWidgetId, templateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        ))

        appWidgetManager.updateAppWidget(appWidgetId, views)
        notifyDataChanged(appWidgetManager, appWidgetId)
    }

    companion object {
        fun refreshWidget(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, WatchWidgetProvider::class.java)
                )
                for (id in ids) {
                    notifyDataChanged(manager, id)
                }
            } catch (_: Exception) { }
        }
    }
}

@Suppress("DEPRECATION")
private fun notifyDataChanged(manager: AppWidgetManager, appWidgetId: Int) {
    manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
}
