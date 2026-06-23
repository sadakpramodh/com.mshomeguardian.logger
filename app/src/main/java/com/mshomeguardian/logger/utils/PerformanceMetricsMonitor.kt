package com.mshomeguardian.logger.utils

import android.content.Context
import android.app.ActivityManager
import android.os.Debug
import android.util.Log
import android.os.BatteryManager
import android.content.IntentFilter
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import java.text.DecimalFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Real-time system metrics and performance monitoring
 */
class PerformanceMetricsMonitor(private val context: Context) {
    private val TAG = "PerformanceMetrics"
    
    data class DeviceMetrics(
        val memoryUsage: MemoryMetrics = MemoryMetrics(),
        val cpuUsage: Double = 0.0,
        val networkStats: NetworkStats = NetworkStats(),
        val thermalStatus: ThermalStatus = ThermalStatus.NORMAL,
        val batteryStatus: BatteryStatus = BatteryStatus(),
        val fpsInfo: FpsInfo = FpsInfo(),
        val timestamp: Long = System.currentTimeMillis()
    )
    
    data class MemoryMetrics(
        val totalMemory: Long = 0,
        val usedMemory: Long = 0,
        val availableMemory: Long = 0,
        val nativeHeap: Long = 0,
        val usedPercentage: Float = 0f
    )
    
    data class NetworkStats(
        val bytesSent: Long = 0,
        val bytesReceived: Long = 0,
        val totalBytes: Long = 0,
        val mobileDataUsed: Long = 0,
        val wifiDataUsed: Long = 0
    )
    
    enum class ThermalStatus {
        NORMAL, MODERATE, CRITICAL, SEVERE
    }
    
    data class BatteryStatus(
        val level: Int = 0,
        val temperature: Int = 0,
        val isCharging: Boolean = false,
        val health: String = "Unknown",
        val status: String = "Unknown"
    )
    
    data class FpsInfo(
        val currentFps: Int = 60,
        val averageFps: Int = 60,
        val minFps: Int = 60,
        val maxFps: Int = 60,
        val droppedFrames: Int = 0
    )
    
    private val _metricsFlow = MutableStateFlow(DeviceMetrics())
    val metricsFlow: StateFlow<DeviceMetrics> = _metricsFlow
    
    private var lastCpuTime = 0L
    private var lastUptime = 0L
    private val fpsBuffer = mutableListOf<Int>()
    
    fun updateMetrics() {
        try {
            val memory = getMemoryMetrics()
            val network = getNetworkStats()
            val thermal = getThermalStatus()
            val battery = getBatteryStatus()
            val fps = getFpsInfo()
            
            _metricsFlow.value = DeviceMetrics(
                memoryUsage = memory,
                cpuUsage = estimateCpuUsage(),
                networkStats = network,
                thermalStatus = thermal,
                batteryStatus = battery,
                fpsInfo = fps,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error updating metrics", e)
        }
    }
    
    private fun getMemoryMetrics(): MemoryMetrics {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        val nativeHeap = 0L // Native heap info not reliably available
        val usedPercentage = if (totalMemory > 0) (usedMemory * 100f) / totalMemory else 0f
        
        return MemoryMetrics(
            totalMemory = totalMemory,
            usedMemory = usedMemory,
            availableMemory = memInfo.availMem,
            nativeHeap = nativeHeap,
            usedPercentage = usedPercentage
        )
    }
    
    private fun getNetworkStats(): NetworkStats {
        return try {
            val bytesSent = android.net.TrafficStats.getTotalTxBytes()
            val bytesReceived = android.net.TrafficStats.getTotalRxBytes()
            
            NetworkStats(
                bytesSent = bytesSent,
                bytesReceived = bytesReceived,
                totalBytes = bytesSent + bytesReceived
            )
        } catch (e: Exception) {
            NetworkStats()
        }
    }
    
    private fun estimateCpuUsage(): Double {
        return try {
            val currentTime = System.currentTimeMillis()
            val uptime = android.os.SystemClock.uptimeMillis()
            
            val cpuUsage = if (lastUptime == 0L) {
                lastCpuTime = currentTime
                lastUptime = uptime
                0.0
            } else {
                val timeDelta = currentTime - lastCpuTime
                val uptimeDelta = uptime - lastUptime
                
                lastCpuTime = currentTime
                lastUptime = uptime
                
                if (uptimeDelta > 0) {
                    (timeDelta.toDouble() / uptimeDelta.toDouble()) * 100
                } else 0.0
            }
            
            cpuUsage.coerceIn(0.0, 100.0)
        } catch (e: Exception) {
            0.0
        }
    }
    
    private fun getThermalStatus(): ThermalStatus {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                when (powerManager?.currentThermalStatus) {
                    android.os.PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.MODERATE
                    android.os.PowerManager.THERMAL_STATUS_CRITICAL -> ThermalStatus.CRITICAL
                    android.os.PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.SEVERE
                    else -> ThermalStatus.NORMAL
                }
            } else {
                ThermalStatus.NORMAL
            }
        } catch (e: Exception) {
            ThermalStatus.NORMAL
        }
    }
    
    private fun getBatteryStatus(): BatteryStatus {
        return try {
            val batteryReceiver = BatteryReceiver()
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = context.registerReceiver(batteryReceiver, filter)
            
            if (intent != null) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                
                BatteryStatus(
                    level = level,
                    temperature = temperature,
                    isCharging = isCharging,
                    health = getHealthString(health),
                    status = getStatusString(status)
                )
            } else {
                BatteryStatus()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting battery status", e)
            BatteryStatus()
        }
    }
    
    private fun getFpsInfo(): FpsInfo {
        val fps = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
                displayManager?.getDisplay(0)?.refreshRate?.toInt() ?: 60
            } else {
                @Suppress("DEPRECATION")
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                windowManager?.defaultDisplay?.refreshRate?.toInt() ?: 60
            }
        } catch (e: Exception) {
            60
        }
        
        return FpsInfo(
            currentFps = fps,
            averageFps = fps,
            minFps = 30,
            maxFps = fps
        )
    }
    
    private fun getHealthString(health: Int): String = when (health) {
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
        BatteryManager.BATTERY_HEALTH_UNKNOWN -> "Unknown"
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        else -> "Unknown"
    }
    
    private fun getStatusString(status: Int): String = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
        BatteryManager.BATTERY_STATUS_FULL -> "Full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
        BatteryManager.BATTERY_STATUS_UNKNOWN -> "Unknown"
        else -> "Unknown"
    }
    
    fun getFormattedMetrics(): String {
        val metrics = _metricsFlow.value
        val df = DecimalFormat("#.##")
        
        return buildString {
            appendLine("Memory: ${formatBytes(metrics.memoryUsage.usedMemory)}/${formatBytes(metrics.memoryUsage.totalMemory)} (${df.format(metrics.memoryUsage.usedPercentage)}%)")
            appendLine("CPU: ${df.format(metrics.cpuUsage)}%")
            appendLine("Network: ↓${formatBytes(metrics.networkStats.bytesReceived)} ↑${formatBytes(metrics.networkStats.bytesSent)}")
            appendLine("Battery: ${metrics.batteryStatus.level}% (${metrics.batteryStatus.status})")
            appendLine("Thermal: ${metrics.thermalStatus}")
            appendLine("FPS: ${metrics.fpsInfo.currentFps}")
        }
    }
    
    private fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unitIndex = 0
        
        while (value >= 1024 && unitIndex < units.size - 1) {
            value /= 1024
            unitIndex++
        }
        
        return String.format("%.2f %s", value, units[unitIndex])
    }
    
    private class BatteryReceiver : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {}
    }
}
