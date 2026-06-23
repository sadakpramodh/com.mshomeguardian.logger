# Debug Console & Sync Stats Features - Implementation Guide

## Overview

A complete debugging and monitoring suite for Home Guardian Logger app with the following features:

1. **Sync Statistics Display** - Shows last sync time, total syncs, items logged
2. **Debug Console** - Resizable bottom panel with colored logs (INFO, WARNING, ERROR, etc.)
3. **Performance Metrics** - Real-time CPU, memory, battery, thermal, network monitoring
4. **Console Logger** - Color-coded log levels with emoji indicators
5. **Triple-Tap Detection** - 3x tap on Device ID to toggle debug display
6. **Auto-Metrics Monitoring** - Periodic performance tracking

---

## Features

### 1. Sync Statistics Manager (`SyncStatsManager`)
Tracks and displays synchronization events with persistent storage.

**Features:**
- Last sync timestamp
- Total sync count
- Items synced per session
- Total items logged
- Sync status tracking
- Enable/disable debug console display

**Usage:**
```kotlin
val syncStatsManager = SyncStatsManager(context)
syncStatsManager.recordSync(itemCount = 42, status = "Success")
syncStatsManager.getFormattedLastSyncTime()  // "12/22 15:46"
```

### 2. Console Logger (`ConsoleLogger`)
Centralized logging with color-coded output and in-memory buffering.

**Log Levels (with Colors):**
- DEBUG (Gray) - `#A0A0A0`
- INFO (Green) - `#4CAF50`
- WARNING (Orange) - `#FF9800`
- ERROR (Red) - `#F44336`
- SUCCESS (Light Green) - `#8BC34A`
- PERFORMANCE (Blue) - `#2196F3`
- NETWORK (Purple) - `#9C27B0`
- THERMAL (Deep Orange) - `#FF5722`

**Usage:**
```kotlin
val logger = ConsoleLogger.getInstance()
logger.info("TAG", "Information message")
logger.warning("TAG", "Warning message")
logger.error("TAG", "Error message", exception)
logger.success("TAG", "✓ Operation succeeded")
logger.performance("TAG", "⚡ Performance metric")
logger.thermal("TAG", "🌡️ Thermal warning")
```

**Features:**
- 500-entry circular buffer (prevents memory bloat)
- Real-time StateFlow for UI updates
- Automatic Android Log integration
- Log statistics (count per level)

### 3. Performance Metrics Monitor (`PerformanceMetricsMonitor`)
Real-time system performance tracking.

**Monitored Metrics:**
- Memory Usage (total, used, available, percentage)
- CPU Usage (estimated)
- Network Stats (bytes sent/received)
- Battery Status (level, temperature, charging state)
- Thermal Status (NORMAL, MODERATE, CRITICAL, SEVERE)
- FPS Information (current, average, min, max)

**Usage:**
```kotlin
val monitor = PerformanceMetricsMonitor(context)
monitor.updateMetrics()
val metrics = monitor.metricsFlow.value
println("Memory: ${metrics.memoryUsage.usedPercentage}%")
println("Battery: ${metrics.batteryStatus.level}%")
println("Thermal: ${metrics.thermalStatus}")
```

### 4. Debug Console View (`DebugConsoleView`)
Resizable bottom panel for displaying colored logs.

**Features:**
- Positioned 25% from bottom of screen
- Resizable by dragging header (min 100dp, max 1000dp)
- Expandable/collapsible content
- Clear logs button
- Close button
- Live log statistics
- Colored output based on log level
- Scrollable history (last 100 logs)

**Usage:**
```kotlin
val debugConsole = DebugConsoleView(context)
rootContainer.addView(debugConsole)
// Auto-observes ConsoleLogger for updates
```

### 5. Sync Stats View (`SyncStatsView`)
Display panel for synchronization statistics.

**Shows:**
- Last sync time and status
- Total number of syncs
- Total items logged (with last sync count)
- Current sync status with color coding

**Usage:**
```kotlin
val syncStatsView = SyncStatsView(context)
syncStatsView.setSyncStatsManager(syncStatsManager)
rootContainer.addView(syncStatsView)
syncStatsView.show()  // Initially hidden
syncStatsView.toggle()  // Toggle visibility
```

### 6. Triple-Tap Detector (`TripleTapDetector`)
Detects 3 rapid taps within 300ms on any View.

**Usage:**
```kotlin
TripleTapDetector(deviceIdTextView) {
    syncStatsView.toggle()  // Toggle on triple-tap
}
```

---

## Integration Guide

### Step 1: Add to MainActivity

**a) Add imports:**
```kotlin
import com.mshomeguardian.logger.ui.DebugConsoleView
import com.mshomeguardian.logger.ui.SyncStatsView
import com.mshomeguardian.logger.utils.DebugFeaturesManager
```

**b) Add properties to MainActivity class:**
```kotlin
private lateinit var debugFeaturesManager: DebugFeaturesManager
```

**c) Initialize in `initializeUIWithCrashProtection()`:**
```kotlin
private fun initializeUIWithCrashProtection() {
    CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "initializeUI", Unit) {
        try {
            // ... existing code ...
            
            // Initialize debug features
            setupDebugFeatures()
            
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Critical error in UI initialization", e)
            showFallbackUI()
        }
    }
}

private fun setupDebugFeatures() {
    val rootView = findViewById<ViewGroup>(R.id.root)  // Your root container ID
    debugFeaturesManager = DebugFeaturesManager.getInstance(this)
    debugFeaturesManager.initialize(rootView, deviceIdText)
    
    debugFeaturesManager.logSuccess("App started successfully")
}
```

### Step 2: Record Sync Events

**Update the sync button listener:**
```kotlin
syncButton.setOnClickListener {
    if (areAllRequiredPermissionsGrantedSafely()) {
        Toast.makeText(this, "Starting manual sync...", Toast.LENGTH_SHORT).show()
        debugFeaturesManager.setSyncInProgress(true)
        
        CrashPreventionUtils.ErrorHandling.safeAsync(TAG, "manual sync") {
            try {
                debugFeaturesManager.logInfo("📤 Sync started")
                
                DataSyncManager.syncAll(applicationContext)
                
                // Count synced items (adapt to your data)
                val itemCount = calculateSyncedItems()
                debugFeaturesManager.recordSync(itemCount, "Success")
                
                withContext(Dispatchers.Main) {
                    updateWidgetsSafely()
                    Toast.makeText(this@MainActivity, "Sync completed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                debugFeaturesManager.logError("Sync failed", e)
                debugFeaturesManager.recordSync(0, "Error: ${e.message}")
            } finally {
                debugFeaturesManager.setSyncInProgress(false)
            }
        }
    }
}
```

### Step 3: Add Debug Logging Throughout App

**In any service or activity:**
```kotlin
val debugManager = DebugFeaturesManager.getInstance(this)

// Log different types of messages
debugManager.logInfo("Loading data...")
debugManager.logWarning("Low memory warning")
debugManager.logError("Connection failed", exception)
debugManager.logSuccess("✓ Data loaded")
debugManager.logPerformance("Query took 250ms")
debugManager.logNetwork("🌐 Uploaded 5MB")
debugManager.logThermal("🌡️ Device heating")

// Record syncs
debugManager.recordSync(itemCount = 150, status = "Success")
debugManager.setSyncInProgress(true)
```

---

## User Interactions

### Enabling Debug Display
1. Triple-tap (3x rapid taps) on the Device ID text
2. Or programmatically: `debugFeaturesManager.toggleDebugDisplay()`

### Using Debug Console
- **Show/Hide**: Tap the arrow/play button
- **Resize**: Drag the handle at the top of console
- **Clear**: Tap the clear button
- **Close**: Tap the X button
- **View Details**: Expand to see full logs and metrics

### Viewing Sync Stats
- Shows last sync time, total syncs, items logged
- Visible when debug display is enabled
- Auto-updates in real-time
- Color-coded status indicators

---

## Monitoring Capabilities

### Real-Time Metrics
The console automatically displays:
- Memory usage with percentage
- CPU utilization
- Network traffic (up/down)
- Battery level and status
- Thermal status warnings
- Current FPS

### Log Statistics
Header shows count of:
- Total logs
- Debug logs (🐛)
- Info logs (ℹ️)
- Warnings (⚠️)
- Errors (❌)
- Success logs (✓)

### Performance Alerts
Automatic alerts for:
- Thermal status changes
- Memory usage > 80%
- Battery < 15%

---

## Architecture

### Data Flow
```
Data Events → ConsoleLogger → StateFlow
                             ↓
                         DebugConsoleView (UI)
                         
Sync Events → SyncStatsManager → StateFlow
                                ↓
                            SyncStatsView (UI)
                            
System Events → PerformanceMetricsMonitor → StateFlow
                                           ↓
                                    Console Display
```

### Storage
- **SyncStatsManager**: SharedPreferences (persistent)
- **ConsoleLogger**: In-memory circular buffer (last 500 logs)
- **PerformanceMetricsMonitor**: Current state only

---

## Best Practices

1. **Always use DebugFeaturesManager singleton** for consistency
2. **Log at appropriate levels** - INFO for events, DEBUG for details, WARNING for issues
3. **Record syncs** with accurate item counts for better tracking
4. **Use context managers** - initialize in onCreate, clean up in onDestroy
5. **Handle exceptions** - errors are logged automatically
6. **Monitor performance** - thermal and battery warnings are automatic

---

## Performance Impact

- **Minimal CPU overhead**: Runs in main thread, updates every 5 seconds
- **Memory usage**: ~2-5MB for buffer + UI components
- **Network access**: None (local monitoring only)
- **Can be disabled** in release builds by not calling `setupDebugFeatures()`

---

## Example: Complete Integration

```kotlin
class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private lateinit var debugFeaturesManager: DebugFeaturesManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        val rootView = findViewById<ViewGroup>(R.id.root)
        val deviceIdText = findViewById<TextView>(R.id.deviceIdText)
        
        // Initialize debug manager
        debugFeaturesManager = DebugFeaturesManager.getInstance(this)
        debugFeaturesManager.initialize(rootView, deviceIdText)
        debugFeaturesManager.logSuccess("App initialized")
    }
    
    override fun onDestroy() {
        debugFeaturesManager.destroy()
        super.onDestroy()
    }
}
```

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Debug console not showing | Check if `initialize()` was called with correct root container |
| Sync stats not updating | Ensure `recordSync()` is called after sync operations |
| Triple-tap not working | Verify TripleTapDetector is attached to correct view |
| Performance metrics not updating | Check if app has required permission for system info |
| Logs not appearing | Ensure ConsoleLogger.getInstance() is used globally |

---

## Files Created

1. `SyncStatsManager.kt` - Sync statistics tracking
2. `ConsoleLogger.kt` - Unified logging system
3. `PerformanceMetricsMonitor.kt` - System metrics
4. `DebugConsoleView.kt` - UI for debug console
5. `SyncStatsView.kt` - UI for sync statistics
6. `TripleTapDetector.kt` - Gesture detection
7. `DebugFeaturesManager.kt` - Central manager (recommended for integration)

---

## Future Enhancements

- Export logs to file
- Real-time data visualization
- Performance graph history
- Custom alert thresholds
- Integration with crash reporting
- Remote debug server
- Log filtering UI

---

*Last Updated: December 22, 2024*
