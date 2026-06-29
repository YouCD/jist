package dev.rcht.jist.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.rcht.jist.MainActivity
import dev.rcht.jist.R

class SummaryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (AppWidgetManager.ACTION_APPWIDGET_UPDATE == intent.action) {
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            if (ids != null) {
                onUpdate(context, AppWidgetManager.getInstance(context), ids)
            }
        } else {
            super.onReceive(context, intent)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_summary)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_title, pendingIntent)

        val listIntent = Intent(context, SummaryWidgetRemoteViewsService::class.java)
        views.setRemoteAdapter(R.id.widget_list, listIntent)

        val templateIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val templatePending = PendingIntent.getActivity(
            context, appWidgetId, templateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        views.setPendingIntentTemplate(R.id.widget_list, templatePending)

        appWidgetManager.updateAppWidget(appWidgetId, views)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
    }

    companion object {
        private const val TAG = "SummaryWidget"

        fun refreshWidget(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, SummaryWidgetProvider::class.java)
            )
            for (id in ids) {
                appWidgetManager.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
            }
        }
    }
}
