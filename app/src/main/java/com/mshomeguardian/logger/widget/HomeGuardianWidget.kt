package com.mshomeguardian.logger.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.mshomeguardian.logger.R
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.ui.MainActivity
import com.mshomeguardian.logger.utils.LocationUtils
import com.mshomeguardian.logger.utils.WeatherUtil
import com.mshomeguardian.logger.workers.WorkerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Implementation of App Widget functionality with enhanced weather support.
 */
class HomeGuardianWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "HomeGuardianWidget"
        private const val ACTION_SYNC = "com.mshomeguardian.logger.widget.ACTION_SYNC"
        private const val ACTION_UPDATE = "com.mshomeguardian.logger.widget.ACTION_UPDATE"
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        // Handle custom actions
        when (intent.action) {
            ACTION_SYNC -> {
                Log.d(TAG, "Sync action received from widget")

                // Trigger sync from the widget
                WorkerScheduler.runAllWorkersOnce(context)

                // Update the widget with new information
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(intent.component)

                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            }
            ACTION_UPDATE -> {
                Log.d(TAG, "Update action received for widget")

                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(intent.component)

                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            }
        }
    }

    override fun onDisabled(context: Context) {
        // Cancel the job when all widgets are removed
        job.cancel()
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        // Create an Intent to launch MainActivity when clicked
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create an Intent for the sync button
        val syncIntent = Intent(context, HomeGuardianWidget::class.java).apply {
            action = ACTION_SYNC
        }
        val syncPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            syncIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Get the layout for the widget
        val views = RemoteViews(context.packageName, R.layout.widget_home_guardian)

        // Set the click listener for the main widget area
        views.setOnClickPendingIntent(R.id.widget_title, pendingIntent)

        // Set the click listener for the sync button
        views.setOnClickPendingIntent(R.id.widget_sync_button, syncPendingIntent)

        // Set the current time
        val dateFormat = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault())
        val currentTime = dateFormat.format(Date())
        views.setTextViewText(R.id.widget_update_time, "Widget updated: $currentTime")

        // Update weather and statistics asynchronously
        scope.launch {
            // Get weather data
            val weatherData = getWeatherData(context)

            // Get statistics
            val stats = loadStatistics(context)

            withContext(Dispatchers.Main) {
                // Update weather display
                views.setTextViewText(R.id.widget_temperature, "${weatherData.temperature.toInt()}°C")
                views.setTextViewText(R.id.widget_weather_desc, weatherData.description)

                // Set weather icon based on description or icon code
                val weatherIconRes = getWeatherIconResource(weatherData.iconCode, weatherData.description)
                views.setImageViewResource(R.id.widget_weather_icon, weatherIconRes)

                // Update statistics
                views.setTextViewText(R.id.widget_location_count, "Locations: ${stats.locationCount}")
                views.setTextViewText(R.id.widget_call_count, "Calls: ${stats.callCount}")
                views.setTextViewText(R.id.widget_message_count, "Messages: ${stats.messageCount}")

                // Update last sync time
                val lastSyncTime = getLastSyncTime(context)
                val lastSyncText = if (lastSyncTime > 0) {
                    "Last Sync: ${dateFormat.format(Date(lastSyncTime))}"
                } else {
                    "Last Sync: Never"
                }
                views.setTextViewText(R.id.widget_last_sync, lastSyncText)

                // Tell the AppWidgetManager to update the widget
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    private suspend fun getWeatherData(context: Context): WeatherUtil.WeatherData {
        return withContext(Dispatchers.IO) {
            try {
                // First check if we have cached weather data
                val weatherPrefs = context.getSharedPreferences("weather_data", Context.MODE_PRIVATE)
                val temperature = weatherPrefs.getFloat("temperature", 0.0f)
                val description = weatherPrefs.getString("description", "Weather unavailable") ?: "Weather unavailable"
                val iconCode = weatherPrefs.getString("icon_code", "") ?: ""
                val timestamp = weatherPrefs.getLong("timestamp", 0)
                val cityName = weatherPrefs.getString("city_name", "") ?: ""

                // If we have recent data (last 30 minutes), use it
                if (timestamp > System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(30)) {
                    return@withContext WeatherUtil.WeatherData(
                        temperature = temperature.toDouble(),
                        feelsLike = weatherPrefs.getFloat("feels_like", temperature).toDouble(),
                        description = description,
                        humidity = weatherPrefs.getInt("humidity", 0),
                        windSpeed = weatherPrefs.getFloat("wind_speed", 0f).toDouble(),
                        iconCode = iconCode,
                        cityName = cityName
                    )
                }

                // Otherwise get a fresh location and weather
                val location = LocationUtils.getLastKnownLocation(context)
                if (location != null) {
                    val weatherData = WeatherUtil.getWeatherData(location.latitude, location.longitude)

                    // Cache the new weather data
                    weatherPrefs.edit()
                        .putFloat("latitude", location.latitude.toFloat())
                        .putFloat("longitude", location.longitude.toFloat())
                        .putFloat("temperature", weatherData.temperature.toFloat())
                        .putFloat("feels_like", weatherData.feelsLike.toFloat())
                        .putString("description", weatherData.description)
                        .putInt("humidity", weatherData.humidity)
                        .putFloat("wind_speed", weatherData.windSpeed.toFloat())
                        .putString("icon_code", weatherData.iconCode)
                        .putString("city_name", weatherData.cityName)
                        .putLong("timestamp", System.currentTimeMillis())
                        .apply()

                    return@withContext weatherData
                } else {
                    // Return cached data or default if no location is available
                    return@withContext WeatherUtil.WeatherData(
                        temperature = temperature.toDouble(),
                        feelsLike = weatherPrefs.getFloat("feels_like", temperature).toDouble(),
                        description = description,
                        humidity = weatherPrefs.getInt("humidity", 0),
                        windSpeed = weatherPrefs.getFloat("wind_speed", 0f).toDouble(),
                        iconCode = iconCode,
                        cityName = cityName
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting weather data", e)
                return@withContext WeatherUtil.WeatherData(
                    temperature = 0.0,
                    feelsLike = 0.0,
                    description = "Weather unavailable",
                    humidity = 0,
                    windSpeed = 0.0,
                    iconCode = "",
                    cityName = ""
                )
            }
        }
    }

    private fun getWeatherIconResource(iconCode: String, description: String): Int {
        // First try to match by icon code (if available)
        if (iconCode.isNotEmpty()) {
            when {
                iconCode.contains("01") -> return R.drawable.ic_weather_sunny // Clear sky
                iconCode.contains("02") || iconCode.contains("03") || iconCode.contains("04") ->
                    return R.drawable.ic_weather_cloudy // Clouds
                iconCode.contains("09") || iconCode.contains("10") ->
                    return R.drawable.ic_weather_rain // Rain
                iconCode.contains("11") -> return R.drawable.ic_weather_thunder // Thunderstorm
                iconCode.contains("13") -> return R.drawable.ic_weather_snow // Snow
                iconCode.contains("50") -> return R.drawable.ic_weather_foggy // Fog/mist
            }
        }

        // Fallback to matching by description
        return when {
            description.contains("rain", ignoreCase = true) -> R.drawable.ic_weather_rain
            description.contains("cloud", ignoreCase = true) -> R.drawable.ic_weather_cloudy
            description.contains("clear", ignoreCase = true) ||
                    description.contains("sunny", ignoreCase = true) -> R.drawable.ic_weather_sunny
            description.contains("snow", ignoreCase = true) -> R.drawable.ic_weather_snow
            description.contains("thunder", ignoreCase = true) -> R.drawable.ic_weather_thunder
            description.contains("fog", ignoreCase = true) ||
                    description.contains("mist", ignoreCase = true) -> R.drawable.ic_weather_foggy
            else -> R.drawable.ic_weather_default
        }
    }

    private suspend fun loadStatistics(context: Context): WidgetStats {
        return withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(context)

                // Get the timestamp for the last 24 hours
                val last24Hours = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)

                // Get counts from the database
                val locationCount = db.locationDao().getAllLocations().size
                val callCount = db.callLogDao().getCallLogsCountSince(last24Hours)
                val messageCount = db.messageDao().getMessagesCountSince(last24Hours)

                WidgetStats(locationCount, callCount, messageCount)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading statistics", e)
                WidgetStats(0, 0, 0)
            }
        }
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

        // Return the most recent sync time
        return maxOf(locationTime, callLogTime, messageTime, contactsTime)
    }

    data class WidgetStats(
        val locationCount: Int,
        val callCount: Int,
        val messageCount: Int
    )
}