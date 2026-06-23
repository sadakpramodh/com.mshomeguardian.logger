package com.mshomeguardian.logger.utils

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * Log level enum with associated colors for console display
 */
enum class LogLevel(val color: String, val colorHex: Long) {
    DEBUG("#A0A0A0", 0xFFA0A0A0),      // Gray
    INFO("#4CAF50", 0xFF4CAF50),        // Green
    WARNING("#FF9800", 0xFFFF9800),     // Orange
    ERROR("#F44336", 0xFFF44336),       // Red
    SUCCESS("#8BC34A", 0xFF8BC34A),     // Light Green
    PERFORMANCE("#2196F3", 0xFF2196F3), // Blue
    NETWORK("#9C27B0", 0xFF9C27B0),     // Purple
    THERMAL("#FF5722", 0xFFFF5722)      // Deep Orange
}

/**
 * Log entry data class
 */
data class ConsoleLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val tag: String = "",
    val message: String = "",
    val throwable: Throwable? = null
) {
    fun getFormattedTime(): String {
        val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }
}

/**
 * Console logger for in-app debugging with colored output
 */
class ConsoleLogger {
    private val logBuffer = mutableListOf<ConsoleLogEntry>()
    private val maxLogSize = 500
    
    private val _logsFlow = MutableStateFlow<List<ConsoleLogEntry>>(emptyList())
    val logsFlow: StateFlow<List<ConsoleLogEntry>> = _logsFlow
    
    private val _lastLog = MutableStateFlow<ConsoleLogEntry?>(null)
    val lastLog: StateFlow<ConsoleLogEntry?> = _lastLog
    
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val entry = ConsoleLogEntry(
            level = level,
            tag = tag,
            message = message,
            throwable = throwable
        )
        
        synchronized(logBuffer) {
            logBuffer.add(entry)
            if (logBuffer.size > maxLogSize) {
                logBuffer.removeAt(0)
            }
            _logsFlow.value = logBuffer.toList()
        }
        
        _lastLog.value = entry
        
        // Also log to Android Log
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARNING -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
            LogLevel.SUCCESS -> Log.i(tag, "✓ $message", throwable)
            LogLevel.PERFORMANCE -> Log.i(tag, "⚡ $message", throwable)
            LogLevel.NETWORK -> Log.i(tag, "🌐 $message", throwable)
            LogLevel.THERMAL -> Log.w(tag, "🌡️ $message", throwable)
        }
    }
    
    fun debug(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun info(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun warning(tag: String, message: String) = log(LogLevel.WARNING, tag, message)
    fun error(tag: String, message: String, throwable: Throwable? = null) = 
        log(LogLevel.ERROR, tag, message, throwable)
    fun success(tag: String, message: String) = log(LogLevel.SUCCESS, tag, message)
    fun performance(tag: String, message: String) = log(LogLevel.PERFORMANCE, tag, message)
    fun network(tag: String, message: String) = log(LogLevel.NETWORK, tag, message)
    fun thermal(tag: String, message: String) = log(LogLevel.THERMAL, tag, message)
    
    fun getLogs(): List<ConsoleLogEntry> = _logsFlow.value
    
    fun getLogs(level: LogLevel): List<ConsoleLogEntry> = 
        _logsFlow.value.filter { it.level == level }
    
    fun clearLogs() {
        synchronized(logBuffer) {
            logBuffer.clear()
            _logsFlow.value = emptyList()
        }
        _lastLog.value = null
    }
    
    fun getLogStats(): LogStats {
        val logs = _logsFlow.value
        return LogStats(
            totalLogs = logs.size,
            debugLogs = logs.count { it.level == LogLevel.DEBUG },
            infoLogs = logs.count { it.level == LogLevel.INFO },
            warningLogs = logs.count { it.level == LogLevel.WARNING },
            errorLogs = logs.count { it.level == LogLevel.ERROR },
            successLogs = logs.count { it.level == LogLevel.SUCCESS }
        )
    }
    
    data class LogStats(
        val totalLogs: Int = 0,
        val debugLogs: Int = 0,
        val infoLogs: Int = 0,
        val warningLogs: Int = 0,
        val errorLogs: Int = 0,
        val successLogs: Int = 0
    )
    
    companion object {
        private var instance: ConsoleLogger? = null
        
        fun getInstance(): ConsoleLogger {
            return instance ?: synchronized(this) {
                ConsoleLogger().also { instance = it }
            }
        }
    }
}
