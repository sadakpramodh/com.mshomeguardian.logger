package com.mshomeguardian.logger.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.mshomeguardian.logger.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeGuardianWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Update each widget instance
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        // Create remote views
        val views = RemoteViews(context.packageName, R.layout.widget_home_guardian)

        // Get weather data from shared preferences
        val weatherPrefs = context.getSharedPreferences("weather_data", Context.MODE_PRIVATE)
        val temperature = weatherPrefs.getFloat("temperature", 0.0f)
        val description = weatherPrefs.getString("description", "Weather unavailable")
        val timestamp = weatherPrefs.getLong("timestamp", 0)

        // Get last sync times
        val locationPrefs = context.getSharedPreferences("location_sync", Context.MODE_PRIVATE)
        val callLogPrefs = context.getSharedPreferences("call_log_sync", Context.MODE_PRIVATE)
        val messagePrefs = context.getSharedPreferences("message_sync", Context.MODE_PRIVATE)
        val contactsPrefs = context.getSharedPreferences("contacts_sync", Context.MODE_PRIVATE)

        val locationTime = locationPrefs.getLong("last_sync_time", 0)
        val callLogTime = callLogPrefs.getLong("last_sync_time", 0)
        val messageTime = messagePrefs.getLong("last_sync_time", 0)
        val contactsTime = contactsPrefs.getLong("last_sync_time", 0)

        // Get most recent sync time
        val lastSyncTime = maxOf(locationTime, callLogTime, messageTime, contactsTime)

        // Format dates
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val lastSyncText = if (lastSyncTime > 0) {
            "Last Sync: ${dateFormat.format(Date(lastSyncTime))}"
        } else {
            "Last Sync: Never"
        }

        // Set weather data
        views.setTextViewText(R.id.widget_temperature, "${temperature.toInt()}°C")
        views.setTextViewText(R.id.widget_weather_desc, description)

        // Set weather icon based on description
        val weatherIconRes = when {
            description?.contains("rain", ignoreCase = true) == true -> R.drawable.ic_weather_rain
            description?.contains("cloud", ignoreCase = true) == true -> R.drawable.ic_weather_cloudy
            description?.contains("clear", ignoreCase = true) == true -> R.drawable.ic_weather_sunny
            description?.contains("snow", ignoreCase = true) == true -> R.drawable.ic_weather_snow
            description?.contains("thunder", ignoreCase = true) == true -> R.drawable.ic_weather_thunder
            description?.contains("fog", ignoreCase = true) == true -> R.drawable.ic_weather_foggy
            else -> R.drawable.ic_weather_default
        }
        views.setImageViewResource(R.id.widget_weather_icon, weatherIconRes)

        // Set last sync time
        views.setTextViewText(R.id.widget_last_sync, lastSyncText)

        // Set last update time for widget
        val widgetUpdateText = "Widget updated: ${dateFormat.format(Date())}"
        views.setTextViewText(R.id.widget_update_time, widgetUpdateText)

        // Update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}