package com.mshomeguardian.logger.utils

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Simple utility that checks GitHub releases for updates and downloads the APK
 * using [DownloadManager] when a newer version is available.
 */
object UpdateManager {
    private const val TAG = "UpdateManager"
    private const val RELEASE_URL =
        "https://api.github.com/repos/sadakpramodh/com.mshomeguardian.logger/releases/latest"

    private var downloadId: Long = -1L

    // Safely obtain the app's version name without a hard dependency on BuildConfig
    private fun getAppVersion(): String {
        return try {
            val clazz = Class.forName("com.mshomeguardian.logger.BuildConfig")
            val field = clazz.getDeclaredField("VERSION_NAME")
            field.get(null) as? String ?: ""
        } catch (e: Exception) {
            OptimizedLogger.w(TAG, "Could not access BuildConfig.VERSION_NAME")
            ""
        }
    }

    /**
     * Checks GitHub for the latest release and prompts the user to update if a
     * newer APK is available.
     */
    suspend fun checkForUpdates(context: Context) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(RELEASE_URL).build()
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            if (!response.isSuccessful) {
                OptimizedLogger.e(TAG, "Release fetch failed: ${response.code}")
                return
            }

            val body = response.body?.string() ?: return
            val json = JSONObject(body)
            val latestTag = json.optString("tag_name")
            val currentVersion = getAppVersion()
            if (latestTag.isNullOrEmpty() || !isNewerVersion(latestTag, currentVersion)) {
                return
            }

            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (name.endsWith(".apk")) {
                    val url = asset.optString("browser_download_url")
                    promptUpdate(context, url)
                    break
                }
            }
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Update check failed", e)
        }
    }

    private fun promptUpdate(context: Context, url: String) {
        AlertDialog.Builder(context)
            .setTitle("Update available")
            .setMessage("A newer version of Home Guardian is available. Download and install?")
            .setPositiveButton("Update") { _, _ ->
                downloadApk(context, url)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun downloadApk(context: Context, url: String) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Home Guardian update")
            .setDescription("Downloading update")
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "home-guardian-update.apk"
            )
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = dm.enqueue(request)

        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    ctx?.unregisterReceiver(this)
                    installApk(ctx ?: context, dm.getUriForDownloadedFile(downloadId))
                }
            }
        }, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    private fun installApk(context: Context, uri: Uri?) {
        if (uri == null) return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.trimStart('v').split('.')
        val currentParts = current.trimStart('v').split('.')
        val max = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until max) {
            val l = latestParts.getOrNull(i)?.toIntOrNull() ?: 0
            val c = currentParts.getOrNull(i)?.toIntOrNull() ?: 0
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
