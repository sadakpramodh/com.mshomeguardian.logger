package com.mshomeguardian.logger.services

import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Background service handling privileged device admin actions like lock and wipe.
 */
class DeviceAdminService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, DeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            when (action) {
                ACTION_LOCK -> dpm.lockNow()
                ACTION_WIPE -> dpm.wipeData(0)
            }
        } else {
            Log.w(TAG, "Device admin not active; action $action ignored")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_LOCK = "com.mshomeguardian.logger.ACTION_LOCK_DEVICE"
        const val ACTION_WIPE = "com.mshomeguardian.logger.ACTION_WIPE_DEVICE"
        private const val TAG = "DeviceAdminService"
    }
}
