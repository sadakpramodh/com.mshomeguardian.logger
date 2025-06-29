package com.mshomeguardian.logger.workers

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.FirebaseServiceHelper
import com.mshomeguardian.logger.utils.OptimizedLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val deviceId = DeviceIdentifier.getPersistentDeviceId(context.applicationContext)

    companion object {
        private const val TAG = "InstalledAppsWorker"
        private const val UPDATE_THRESHOLD_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val userEmail = FirebaseServiceHelper.getCurrentUserEmail()
            if (userEmail == null || !FirebaseServiceHelper.isFirebaseAvailable()) {
                OptimizedLogger.w(TAG, "User not authenticated or Firebase unavailable")
                return@withContext Result.success()
            }

            val pm = applicationContext.packageManager
            val packages = pm.getInstalledPackages(0)
            val now = System.currentTimeMillis()

            packages.forEach { pkg ->
                val appMap = HashMap<String, Any>()
                appMap["packageName"] = pkg.packageName
                appMap["versionName"] = pkg.versionName ?: ""
                appMap["versionCode"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkg.longVersionCode else pkg.versionCode.toLong()
                appMap["firstInstallTime"] = pkg.firstInstallTime
                appMap["lastUpdateTime"] = pkg.lastUpdateTime
                appMap["isSystemApp"] = (pkg.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                appMap["regularlyUpdated"] = now - pkg.lastUpdateTime < UPDATE_THRESHOLD_MS
                appMap["deviceId"] = deviceId
                appMap["timestamp"] = now

                FirebaseServiceHelper.uploadInstalledApp(userEmail, deviceId, appMap)
            }

            OptimizedLogger.d(TAG, "Uploaded ${packages.size} installed apps")
            Result.success()
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error collecting installed apps", e)
            Result.retry()
        }
    }
}
