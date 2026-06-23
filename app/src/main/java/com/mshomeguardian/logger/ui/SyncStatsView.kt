package com.mshomeguardian.logger.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.view.ViewGroup
import android.widget.LinearLayout
import com.mshomeguardian.logger.utils.SyncStatsManager
import com.mshomeguardian.logger.utils.applyRoundedBackground
import com.mshomeguardian.logger.utils.dp
import com.mshomeguardian.logger.utils.errorColor
import com.mshomeguardian.logger.utils.infoColor
import com.mshomeguardian.logger.utils.onSurfaceColor
import com.mshomeguardian.logger.utils.onSurfaceVariantColor
import com.mshomeguardian.logger.utils.outlineColor
import com.mshomeguardian.logger.utils.successColor
import com.mshomeguardian.logger.utils.surfaceColor
import com.mshomeguardian.logger.utils.surfaceVariantColor
import com.mshomeguardian.logger.utils.warningColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
    private lateinit var titleText: TextView
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
            setPadding(dp(16), dp(14), dp(16), dp(14))
            applyRoundedBackground(context.surfaceColor(), context.outlineColor(), radiusDp = 16)
            elevation = dp(4).toFloat()
            
            titleText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                text = "Sync stats"
                setTextColor(context.onSurfaceColor())
                textSize = 15f
                setPadding(0, 0, 0, dp(6))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            addView(titleText)
            
            lastSyncTimeText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                text = "Last Sync: Never"
                setTextColor(context.onSurfaceVariantColor())
                textSize = 13f
                setPadding(0, 0, 0, dp(2))
            }
            addView(lastSyncTimeText)
            
            syncCountText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                text = "Total Syncs: 0"
                setTextColor(context.onSurfaceVariantColor())
                textSize = 13f
                setPadding(0, 0, 0, dp(2))
            }
            addView(syncCountText)
            
            itemsCountText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                text = "Items Logged: 0"
                setTextColor(context.onSurfaceVariantColor())
                textSize = 13f
                setPadding(0, 0, 0, dp(2))
            }
            addView(itemsCountText)
            
            statusText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                text = "Status: Ready"
                setTextColor(context.successColor())
                textSize = 13f
                setPadding(0, dp(6), 0, 0)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
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
            stats.isSyncing -> context.infoColor()
            stats.lastSyncStatus == "Success" -> context.successColor()
            stats.lastSyncStatus.equals("Warning", ignoreCase = true) -> context.warningColor()
            else -> context.errorColor()
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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
    }
}
