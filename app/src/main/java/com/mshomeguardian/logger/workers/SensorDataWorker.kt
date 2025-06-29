package com.mshomeguardian.logger.workers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.FirebaseServiceHelper
import com.mshomeguardian.logger.utils.OptimizedLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class SensorDataWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val deviceId = DeviceIdentifier.getPersistentDeviceId(context.applicationContext)

    companion object {
        private const val TAG = "SensorDataWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val userEmail = FirebaseServiceHelper.getCurrentUserEmail()
            if (userEmail == null || !FirebaseServiceHelper.isFirebaseAvailable()) {
                OptimizedLogger.w(TAG, "User not authenticated or Firebase unavailable")
                return@withContext Result.success()
            }

            val sensorManager = applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val data = HashMap<String, Any>()
            data["timestamp"] = System.currentTimeMillis()
            data["deviceId"] = deviceId

            suspend fun readSensor(type: Int, keys: List<String>) {
                val sensor = sensorManager.getDefaultSensor(type) ?: return
                val values = suspendCancellableCoroutine<FloatArray> { cont ->
                    val listener = object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent) {
                            cont.resume(event.values.clone())
                            sensorManager.unregisterListener(this)
                        }
                        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
                    }
                    sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                    cont.invokeOnCancellation { sensorManager.unregisterListener(listener) }
                }
                keys.forEachIndexed { index, key ->
                    if (index < values.size) data[key] = values[index]
                }
            }

            readSensor(Sensor.TYPE_ACCELEROMETER, listOf("accelX", "accelY", "accelZ"))
            readSensor(Sensor.TYPE_GYROSCOPE, listOf("gyroX", "gyroY", "gyroZ"))
            readSensor(Sensor.TYPE_LIGHT, listOf("light"))
            readSensor(Sensor.TYPE_PROXIMITY, listOf("proximity"))
            readSensor(Sensor.TYPE_STEP_COUNTER, listOf("steps"))
            readSensor(Sensor.TYPE_HEART_RATE, listOf("heartRate"))

            FirebaseServiceHelper.uploadSensorData(userEmail, deviceId, data)
            OptimizedLogger.d(TAG, "Sensor data uploaded")
            Result.success()
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error collecting sensor data", e)
            Result.retry()
        }
    }
}
