package com.mshomeguardian.logger.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.graphics.Color
import android.view.ViewGroup
import android.widget.LinearLayout
import com.mshomeguardian.logger.utils.SyncStatsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Sync stats display view showing last sync time and counts
 */
class SyncStatsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    
    private var syncStatsManager: SyncStatsManager? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    
    private lateinit var statsContainer: LinearLayout
    private lateinit var lastSyncTimeText: TextView
    private lateinit var syncCountText: TextView
    private lateinit var itemsCountText: TextView
    private lateinit var statusText: TextView
    
    init {
        setupUI()
    }
    
    private fun setupUI() {
        statsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundColor(Color.parseColor("#263238"))
            elevation = 4f
            
            lastSyncTimeText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                text = "Last Sync: Never"
                setTextColor(Color.WHITE)
                textSize = 12f
                setPadding(16, 8, 16, 4)
            }
            addView(lastSyncTimeText)
            
            syncCountText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                text = "Total Syncs: 0"
                setTextColor(Color.parseColor("#A8D5BA"))
                textSize = 11f
                setPadding(16, 2, 16, 2)
            }
            addView(syncCountText)
            
            itemsCountText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                text = "Items Logged: 0"
                setTextColor(Color.parseColor("#FFB74D"))
                textSize = 11f
                setPadding(16, 2, 16, 2)
            }
            addView(itemsCountText)
            
            statusText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                text = "Status: Ready"
                setTextColor(Color.parseColor("#EF9A9A"))
                textSize = 11f
                setPadding(16, 2, 16, 8)
            }
            addView(statusText)
        }
        
        addView(statsContainer)
        visibility = View.GONE
    }
    
    fun setSyncStatsManager(manager: SyncStatsManager) {
        syncStatsManager = manager
        observeStats()
    }
    
    private fun observeStats() {
        syncStatsManager?.let { manager ->
            scope.launch {
                manager.syncStatsFlow.collect { stats ->
                    updateUI(stats)
                }
            }
        }
    }
    
    private fun updateUI(stats: SyncStatsManager.SyncStats) {
        lastSyncTimeText.text = "Last Sync: ${stats.lastSyncStatus} (${syncStatsManager?.getFormattedLastSyncTime() ?: "Never"})"
        syncCountText.text = "Total Syncs: ${stats.totalSyncCount}"
        itemsCountText.text = "Items Logged: ${stats.totalItemsLogged} (Last: ${stats.lastSyncedItems})"
        
        val status = when {
            stats.isSyncing -> "Status: Syncing..."
            stats.totalSyncCount == 0 -> "Status: No syncs yet"
            stats.lastSyncStatus == "Success" -> "Status: ✓ Success"
            else -> "Status: ${stats.lastSyncStatus}"
        }
        statusText.text = status
        
        val statusColor = when {
            stats.isSyncing -> Color.parseColor("#81C784")
            stats.lastSyncStatus == "Success" -> Color.parseColor("#4CAF50")
            else -> Color.parseColor("#FF7043")
        }
        statusText.setTextColor(statusColor)
    }
    
    fun show() {
        visibility = View.VISIBLE
    }
    
    fun hide() {
        visibility = View.GONE
    }
    
    fun toggle() {
        visibility = if (visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }
}
