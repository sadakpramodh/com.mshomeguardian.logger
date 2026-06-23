package com.mshomeguardian.logger.utils

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages sync statistics tracking including last sync time and sync counts
 */
class SyncStatsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "sync_stats",
        Context.MODE_PRIVATE
    )
    
    private val _syncStatsFlow = MutableStateFlow(SyncStats())
    val syncStatsFlow: StateFlow<SyncStats> = _syncStatsFlow
    
    private val _debugConsoleEnabled = MutableStateFlow(false)
    val debugConsoleEnabled: StateFlow<Boolean> = _debugConsoleEnabled
    
    data class SyncStats(
        val lastSyncTime: Long = 0,
        val totalSyncCount: Int = 0,
        val lastSyncedItems: Int = 0,
        val totalItemsLogged: Int = 0,
        val lastSyncStatus: String = "Never",
        val isSyncing: Boolean = false
    )
    
    fun recordSync(itemCount: Int, status: String = "Success") {
        val currentTime = System.currentTimeMillis()
        val totalCount = getTotalSyncCount() + 1
        
        prefs.edit().apply {
            putLong(KEY_LAST_SYNC_TIME, currentTime)
            putInt(KEY_TOTAL_SYNC_COUNT, totalCount)
            putInt(KEY_LAST_SYNCED_ITEMS, itemCount)
            putInt(KEY_TOTAL_ITEMS_LOGGED, getTotalItemsLogged() + itemCount)
            putString(KEY_LAST_SYNC_STATUS, status)
            apply()
        }
        
        updateFlowState()
    }
    
    fun setSyncInProgress(inProgress: Boolean) {
        val stats = _syncStatsFlow.value.copy(isSyncing = inProgress)
        _syncStatsFlow.value = stats
    }
    
    fun toggleDebugConsole() {
        _debugConsoleEnabled.value = !_debugConsoleEnabled.value
    }
    
    fun setDebugConsoleEnabled(enabled: Boolean) {
        _debugConsoleEnabled.value = enabled
        prefs.edit().putBoolean(KEY_DEBUG_CONSOLE_ENABLED, enabled).apply()
    }
    
    fun isDebugConsoleEnabled(): Boolean = _debugConsoleEnabled.value
    
    fun getLastSyncTime(): Long = prefs.getLong(KEY_LAST_SYNC_TIME, 0)
    
    fun getTotalSyncCount(): Int = prefs.getInt(KEY_TOTAL_SYNC_COUNT, 0)
    
    fun getLastSyncedItems(): Int = prefs.getInt(KEY_LAST_SYNCED_ITEMS, 0)
    
    fun getTotalItemsLogged(): Int = prefs.getInt(KEY_TOTAL_ITEMS_LOGGED, 0)
    
    fun getLastSyncStatus(): String = prefs.getString(KEY_LAST_SYNC_STATUS, "Never") ?: "Never"
    
    fun getFormattedLastSyncTime(): String {
        val lastSync = getLastSyncTime()
        if (lastSync == 0L) return "Never synced"
        
        val calendar = Calendar.getInstance().apply { timeInMillis = lastSync }
        val formatter = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        return formatter.format(calendar.time)
    }
    
    fun getSyncStats(): SyncStats {
        return SyncStats(
            lastSyncTime = getLastSyncTime(),
            totalSyncCount = getTotalSyncCount(),
            lastSyncedItems = getLastSyncedItems(),
            totalItemsLogged = getTotalItemsLogged(),
            lastSyncStatus = getLastSyncStatus(),
            isSyncing = _syncStatsFlow.value.isSyncing
        )
    }
    
    fun clearStats() {
        prefs.edit().clear().apply()
        updateFlowState()
    }
    
    private fun updateFlowState() {
        _syncStatsFlow.value = SyncStats(
            lastSyncTime = getLastSyncTime(),
            totalSyncCount = getTotalSyncCount(),
            lastSyncedItems = getLastSyncedItems(),
            totalItemsLogged = getTotalItemsLogged(),
            lastSyncStatus = getLastSyncStatus(),
            isSyncing = _syncStatsFlow.value.isSyncing
        )
    }
    
    companion object {
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_TOTAL_SYNC_COUNT = "total_sync_count"
        private const val KEY_LAST_SYNCED_ITEMS = "last_synced_items"
        private const val KEY_TOTAL_ITEMS_LOGGED = "total_items_logged"
        private const val KEY_LAST_SYNC_STATUS = "last_sync_status"
        private const val KEY_DEBUG_CONSOLE_ENABLED = "debug_console_enabled"
    }
}
