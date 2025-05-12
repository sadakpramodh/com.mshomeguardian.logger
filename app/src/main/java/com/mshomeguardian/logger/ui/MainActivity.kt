package com.mshomeguardian.logger.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mshomeguardian.logger.R
import com.mshomeguardian.logger.workers.LocationWorker
import com.mshomeguardian.logger.workers.LocationWorkerScheduler

class MainActivity : AppCompatActivity() {
    private val PERM_REQ = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERM_REQ
            )
        } else {
            startLocationJobs()
        }
    }

    override fun onRequestPermissionsResult(
        req: Int, perms: Array<out String>, results: IntArray
    ) {
        super.onRequestPermissionsResult(req, perms, results)
        if (req == PERM_REQ && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startLocationJobs()
        }
    }

    private fun startLocationJobs() {
        // immediate test
        val testWork = OneTimeWorkRequestBuilder<LocationWorker>().build()
        WorkManager.getInstance(this).enqueue(testWork)
        // schedule periodic
        LocationWorkerScheduler.schedule(this)
    }
}
