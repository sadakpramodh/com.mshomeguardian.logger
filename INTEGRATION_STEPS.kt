package com.mshomeguardian.logger.examples

/**
 * STEP-BY-STEP INTEGRATION GUIDE FOR MAINACTIVITY
 * 
 * This file shows exactly where and how to add debug features to MainActivity
 */

/*

STEP 1: Add imports to MainActivity.kt
=========================================

Add these imports at the top of the file:

```kotlin
import com.mshomeguardian.logger.ui.DebugConsoleView
import com.mshomeguardian.logger.ui.SyncStatsView
import com.mshomeguardian.logger.utils.DebugFeaturesManager
import com.mshomeguardian.logger.utils.QuickDebugSetup
import com.mshomeguardian.logger.utils.LogLevel
```

---

STEP 2: Add property to MainActivity class
==========================================

After existing properties around line 41-59, add:

```kotlin
class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    
    // ... existing properties ...
    private lateinit var signOutButton: Button
    
    // NEW: Add this
    private lateinit var debugFeaturesManager: DebugFeaturesManager
```

---

STEP 3: Initialize debug features in onCreate
==============================================

In the onCreate() method, after setContentView(), add:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "onCreate", Unit) {
        CrashPreventionUtils.initialize(this)

        if (!safeAuthCheck()) {
            OptimizedLogger.d(TAG, "User not signed in, starting SignInActivity")
            startSignInActivity()
            return@safeExecute
        }

        OptimizedLogger.d(TAG, "User signed in: ${AuthManager.getCurrentUser()?.email}")
        setContentView(R.layout.activity_main)

        // Initialize UI
        initializeUIWithCrashProtection()

        // NEW: Add this line
        setupDebugFeatures()

        // Check battery optimizations
        safeCheckBatteryOptimizations()
    }
}
```

---

STEP 4: Add setupDebugFeatures() method
======================================

Add this new method to MainActivity class (around line 182 after initializeUIWithCrashProtection):

```kotlin
private fun setupDebugFeatures() {
    CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "setupDebugFeatures", Unit) {
        try {
            // Find your root container - adjust R.id.root to match your layout's root ViewGroup ID
            val rootView = findViewById<ViewGroup>(R.id.root)
            
            // Initialize debug features with single line
            debugFeaturesManager = DebugFeaturesManager.getInstance(this)
            debugFeaturesManager.initialize(rootView, deviceIdText)
            
            debugFeaturesManager.logSuccess("Debug features enabled")
            OptimizedLogger.d(TAG, "Debug features initialized")
            
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Failed to initialize debug features", e)
        }
    }
}
```

---

STEP 5: Update sync button listener
==================================

Find the syncButton click listener (around line 256) and replace it with:

```kotlin
syncButton.setOnClickListener {
    if (areAllRequiredPermissionsGrantedSafely()) {
        Toast.makeText(this, "Starting manual sync...", Toast.LENGTH_SHORT).show()
        
        // NEW: Set sync in progress
        debugFeaturesManager.setSyncInProgress(true)
        
        CrashPreventionUtils.ErrorHandling.safeAsync(
            TAG, "manual sync"
        ) {
            try {
                // NEW: Log sync start
                debugFeaturesManager.logInfo("📤 Sync started")
                
                DataSyncManager.syncAll(applicationContext)
                
                // NEW: Calculate and record sync
                // (Adapt calculateSyncedItems() to your actual data)
                val itemCount = calculateSyncedItems()
                debugFeaturesManager.recordSync(itemCount, "Success")
                
                // NEW: Log success
                debugFeaturesManager.logSuccess("✓ Sync completed successfully")
                
                withContext(Dispatchers.Main) {
                    updateWidgetsSafely()
                    Toast.makeText(
                        this@MainActivity, 
                        "Sync completed: $itemCount items", 
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                // NEW: Log error
                debugFeaturesManager.logError("Sync failed: ${e.message}", e)
                debugFeaturesManager.recordSync(0, "Error: ${e.message}")
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Sync failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                // NEW: Set sync complete
                debugFeaturesManager.setSyncInProgress(false)
            }
        }
    } else {
        debugFeaturesManager.logWarning("⚠️ Missing permissions for sync")
        Toast.makeText(this, "Please grant all permissions first", Toast.LENGTH_LONG).show()
        updatePermissionStatusSafely()
    }
}
```

---

STEP 6: Add helper method to calculate synced items
==================================================

Add this method to MainActivity class:

```kotlin
private fun calculateSyncedItems(): Int {
    return try {
        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "calculateSyncedItems", 0) {
            // TODO: Replace with your actual data count logic
            // Example: count from database, shared prefs, or API response
            
            // For now, return 0 as placeholder
            // Adapt this to your actual sync logic
            0
        }
    } catch (e: Exception) {
        OptimizedLogger.e(TAG, "Error calculating sync items", e)
        0
    }
}
```

---

STEP 7: Update setupButtonListeners() for audio recording
========================================================

Find the recordingButton listener (around line 273) and add logging:

```kotlin
recordingButton.setOnClickListener {
    if (canUseAudioFeaturesSafely()) {
        debugFeaturesManager.logInfo("🎤 Recording toggled")
        toggleRecordingServiceSafely()
    } else {
        debugFeaturesManager.logWarning("⚠️ Audio permissions required")
        requestAudioPermissionsSafely()
    }
}
```

---

STEP 8: Cleanup in onDestroy()
=============================

Add onDestroy() method if not already present:

```kotlin
override fun onDestroy() {
    // Cleanup debug features
    try {
        debugFeaturesManager.destroy()
    } catch (e: Exception) {
        Log.e(TAG, "Error destroying debug features", e)
    }
    
    super.onDestroy()
}
```

---

STEP 9: (OPTIONAL) Add logging to other methods
==============================================

For better debugging, add logs to key methods:

```kotlin
private fun startBackgroundServicesSafely() {
    debugFeaturesManager.logInfo("🚀 Starting background services...")
    // ... existing code ...
}

private fun updatePermissionStatusSafely() {
    debugFeaturesManager.logDebug("Updating permission status")
    // ... existing code ...
}

private fun signOutSafely() {
    debugFeaturesManager.logInfo("👋 Signing out...")
    // ... existing code ...
}
```

---

USAGE: Triple-Tap Activation
=============================

Once integrated, users can:
1. Triple-tap (3x rapid) on the Device ID text
2. This shows/hides the debug console and sync stats

To show debug console programmatically:
```kotlin
debugFeaturesManager.toggleDebugDisplay()
```

---

IMPORTANT NOTES
==============

1. Make sure R.id.root matches your actual root container ID in activity_main.xml
2. If you don't have a root ViewGroup ID, add one:
   ```xml
   <LinearLayout
       android:id="@+id/root"
       android:layout_width="match_parent"
       android:layout_height="match_parent"
       android:orientation="vertical">
   ```

3. The debug console will appear at the bottom of the screen
4. It's initially hidden - enable with triple-tap or toggle
5. Default height is 300dp, resizable from 100dp to 1000dp

---

TESTING CHECKLIST
=================

After integration, verify:
- [ ] App starts without crashes
- [ ] Device ID triple-tap reveals debug display
- [ ] Sync button records sync events
- [ ] Console shows colored logs
- [ ] Stats display updates in real-time
- [ ] Resize handle works on debug console
- [ ] Clear button clears logs
- [ ] Close button hides console
- [ ] Performance metrics update automatically

*/
