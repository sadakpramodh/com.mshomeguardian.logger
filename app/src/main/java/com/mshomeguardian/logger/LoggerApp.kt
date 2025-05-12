package com.mshomeguardian.logger

import android.app.Application


import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_table")
data class LocationEntity(
    @PrimaryKey val timestamp: Long,
    val latitude: Double,
    val longitude: Double
)


class LoggerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // You can initialize Firebase, DB, etc. here if needed

        LocationWorkerScheduler.schedule(applicationContext)

    }
}
