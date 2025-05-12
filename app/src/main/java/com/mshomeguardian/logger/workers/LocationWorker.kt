package com.mshomeguardian.logger.workers

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.data.LocationEntity
import com.mshomeguardian.logger.utils.LocationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = AppDatabase.getInstance(context)
    private val firestore = FirebaseFirestore.getInstance()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val loc: Location? = LocationUtils.getLastKnownLocation(applicationContext)
            if (loc != null) {
                val ts = System.currentTimeMillis()
                val entity = LocationEntity(ts, loc.latitude, loc.longitude)
                db.locationDao().insertLocation(entity)
                firestore.collection("users/device-001/locations")
                    .document(ts.toString())
                    .set(entity, SetOptions.merge())
                Log.d("LocationWorker", "Saved $entity")
                Result.success()
            } else {
                Log.e("LocationWorker", "Location == null")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("LocationWorker", "Error", e)
            Result.retry()
        }
    }
}
