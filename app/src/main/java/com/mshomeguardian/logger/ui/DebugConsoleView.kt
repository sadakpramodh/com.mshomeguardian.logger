package com.mshomeguardian.logger.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ScrollView
import android.graphics.Color
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.mshomeguardian.logger.utils.ConsoleLogger
import com.mshomeguardian.logger.utils.ConsoleLogEntry
import com.mshomeguardian.logger.utils.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Resizable debug console view for displaying colored logs
 */
class DebugConsoleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    
    private val consoleLogger = ConsoleLogger.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main)
    
    private lateinit var headerView: LinearLayout
    private lateinit var expandCollapseBtn: ImageButton
    private lateinit var clearBtn: ImageButton
    private lateinit var closeBtn: ImageButton
    private lateinit var statsText: TextView
    private lateinit var contentContainer: LinearLayout
    private lateinit var logsRecyclerView: RecyclerView
    private lateinit var resizeHandle: View
    
    private var isExpanded = false
    private var initialHeight = 0
    private var initialY = 0f
    private var initialTouchY = 0f
    private var isResizing = false
    
    private val logAdapter = LogAdapter()
    
    init {
        setupUI()
        observeLogs()
    }
    
    private fun setupUI() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 300)
        }
        
        // Header
        headerView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                50
            )
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            
            statsText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                text = "Logs: 0 | Debug: 0 | Info: 0 | Warnings: 0 | Errors: 0"
                setTextColor(Color.WHITE)
                textSize = 10f
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(12, 0, 12, 0)
            }
            addView(statsText)
            
            expandCollapseBtn = ImageButton(context).apply {
                layoutParams = LinearLayout.LayoutParams(40, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.TRANSPARENT)
                setImageResource(android.R.drawable.ic_media_play)
                setColorFilter(Color.WHITE)
                setOnClickListener { toggleExpand() }
            }
            addView(expandCollapseBtn)
            
            clearBtn = ImageButton(context).apply {
                layoutParams = LinearLayout.LayoutParams(40, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.TRANSPARENT)
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setColorFilter(Color.WHITE)
                setOnClickListener { consoleLogger.clearLogs() }
            }
            addView(clearBtn)
            
            closeBtn = ImageButton(context).apply {
                layoutParams = LinearLayout.LayoutParams(40, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.TRANSPARENT)
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setColorFilter(Color.parseColor("#FF5252"))
                setOnClickListener { visibility = View.GONE }
            }
            addView(closeBtn)
        }
        root.addView(headerView)
        
        // Content Container
        contentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            visibility = View.GONE
            
            logsRecyclerView = RecyclerView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                layoutManager = LinearLayoutManager(context)
                adapter = logAdapter
                setBackgroundColor(Color.parseColor("#0D0D0D"))
            }
            addView(logsRecyclerView)
        }
        root.addView(contentContainer)
        
        // Resize handle
        resizeHandle = View(context).apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 8)
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            isHapticFeedbackEnabled = true
        }
        root.addView(resizeHandle)
        
        addView(root)
        
        setupTouchListeners()
    }
    
    private fun setupTouchListeners() {
        resizeHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isResizing = true
                    initialTouchY = event.rawY
                    initialHeight = height
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isResizing) {
                        val dy = (event.rawY - initialTouchY).toInt()
                        val newHeight = (initialHeight - dy).coerceIn(100, 1000)
                        val params = layoutParams
                        params.height = newHeight
                        layoutParams = params
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    isResizing = false
                    true
                }
                else -> false
            }
        }
    }
    
    private fun toggleExpand() {
        isExpanded = !isExpanded
        if (isExpanded) {
            contentContainer.visibility = View.VISIBLE
            expandCollapseBtn.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            contentContainer.visibility = View.GONE
            expandCollapseBtn.setImageResource(android.R.drawable.ic_media_play)
        }
    }
    
    private fun observeLogs() {
        scope.launch {
            consoleLogger.logsFlow.collect { logs ->
                logAdapter.submitList(logs.takeLast(100))
                logsRecyclerView.scrollToPosition(logs.size.coerceAtLeast(1) - 1)
                updateStats()
            }
        }
    }
    
    private fun updateStats() {
        val stats = consoleLogger.getLogStats()
        statsText.text = "Logs: ${stats.totalLogs} | 🐛 ${stats.debugLogs} | ℹ️ ${stats.infoLogs} | ⚠️ ${stats.warningLogs} | ❌ ${stats.errorLogs} | ✓ ${stats.successLogs}"
    }
    
    private inner class LogAdapter : RecyclerView.Adapter<LogViewHolder>() {
        private var items = listOf<ConsoleLogEntry>()
        
        fun submitList(list: List<ConsoleLogEntry>) {
            items = list
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            val view = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setTextColor(Color.WHITE)
                textSize = 11f
                setPadding(8, 4, 8, 4)
                setBackgroundColor(Color.parseColor("#1A1A1A"))
            }
            return LogViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            val entry = items[position]
            val textView = holder.itemView as TextView
            
            val logText = "[${entry.getFormattedTime()}] [${entry.level.name}] ${entry.tag}: ${entry.message}"
            textView.text = logText
            textView.setTextColor(entry.level.colorHex.toInt())
            
            if (entry.throwable != null) {
                val errorText = "\n${entry.throwable.stackTraceToString()}"
                textView.append(errorText)
            }
        }
        
        override fun getItemCount() = items.size
    }
    
    private class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
