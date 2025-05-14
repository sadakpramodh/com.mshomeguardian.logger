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
 * Implementation of App Widget functionality.
 */
class HomeGuardianWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "HomeGuardianWidget"
        private const val ACTION_SYNC = "com.mshomeguardian.logger.widget.ACTION_SYNC"
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

        // Handle our custom sync action
        if (intent.action == ACTION_SYNC) {
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
        val views = RemoteViews(context.packageName, R.layout.home_guardian_widget)

        // Set the click listener for the main widget area
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

        // Set the click listener for the sync button
        views.setOnClickPendingIntent(R.id.widget_sync_button, syncPendingIntent)

        // Set the current time
        val dateFormat = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault())
        val currentTime = dateFormat.format(Date())
        views.setTextViewText(R.id.widget_time, "Last updated: $currentTime")

        // Update statistics asynchronously
        scope.launch {
            val stats = loadStatistics(context)
            withContext(Dispatchers.Main) {
                views.setTextViewText(R.id.widget_location_count, "Locations: ${stats.locationCount}")
                views.setTextViewText(R.id.widget_call_count, "Calls: ${stats.callCount}")
                views.setTextViewText(R.id.widget_message_count, "Messages: ${stats.messageCount}")

                // Tell the AppWidgetManager to update the widget
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
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

    data class WidgetStats(
        val locationCount: Int,
        val callCount: Int,
        val messageCount: Int
    )
}