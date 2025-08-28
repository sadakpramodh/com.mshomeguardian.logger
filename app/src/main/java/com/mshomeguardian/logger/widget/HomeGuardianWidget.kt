package com.mshomeguardian.logger.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.mshomeguardian.logger.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeGuardianWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_home_guardian)

        val dateFormat = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault())
        val lastSyncTime = getLastSyncTime(context)
        val lastSyncText = if (lastSyncTime > 0) {
            "Last Sync: ${dateFormat.format(Date(lastSyncTime))}"
        } else {
            "Last Sync: Never"
        }
        views.setTextViewText(R.id.widget_last_sync, lastSyncText)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun getLastSyncTime(context: Context): Long {
        val locationPrefs = context.getSharedPreferences("location_sync", Context.MODE_PRIVATE)
        val callLogPrefs = context.getSharedPreferences("call_log_sync", Context.MODE_PRIVATE)
        val messagePrefs = context.getSharedPreferences("message_sync", Context.MODE_PRIVATE)
        val contactsPrefs = context.getSharedPreferences("contacts_sync", Context.MODE_PRIVATE)

        val locationTime = locationPrefs.getLong("last_sync_time", 0)
        val callLogTime = callLogPrefs.getLong("last_sync_time", 0)
        val messageTime = messagePrefs.getLong("last_sync_time", 0)
        val contactsTime = contactsPrefs.getLong("last_sync_time", 0)

        return maxOf(locationTime, callLogTime, messageTime, contactsTime)
    }
}

