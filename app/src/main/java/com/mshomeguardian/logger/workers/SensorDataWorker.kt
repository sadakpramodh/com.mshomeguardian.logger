package com.mshomeguardian.logger.workers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.data.SensorDataEntity
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

    private val db = AppDatabase.getInstance(context.applicationContext)
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
            val values = HashMap<String, Float>()

            suspend fun readSensor(type: Int, keys: List<String>) {
                val sensor = sensorManager.getDefaultSensor(type) ?: return
                val readings = suspendCancellableCoroutine<FloatArray> { cont ->
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
                    if (index < readings.size) values[key] = readings[index]
                }
            }

            readSensor(Sensor.TYPE_ACCELEROMETER, listOf("accelX", "accelY", "accelZ"))
            readSensor(Sensor.TYPE_GYROSCOPE, listOf("gyroX", "gyroY", "gyroZ"))
            readSensor(Sensor.TYPE_MAGNETIC_FIELD, listOf("magX", "magY", "magZ"))
            readSensor(Sensor.TYPE_GRAVITY, listOf("gravityX", "gravityY", "gravityZ"))
            readSensor(Sensor.TYPE_LINEAR_ACCELERATION, listOf("linearAccelX", "linearAccelY", "linearAccelZ"))
            readSensor(Sensor.TYPE_ROTATION_VECTOR, listOf("rotX", "rotY", "rotZ", "rotScalar"))
            readSensor(Sensor.TYPE_PRESSURE, listOf("pressure"))
            readSensor(Sensor.TYPE_RELATIVE_HUMIDITY, listOf("humidity"))
            readSensor(Sensor.TYPE_AMBIENT_TEMPERATURE, listOf("ambientTemperature"))
            readSensor(Sensor.TYPE_LIGHT, listOf("light"))
            readSensor(Sensor.TYPE_PROXIMITY, listOf("proximity"))
            readSensor(Sensor.TYPE_STEP_COUNTER, listOf("steps"))
            readSensor(Sensor.TYPE_STEP_DETECTOR, listOf("stepDetected"))
            // Heart rate requires BODY_SENSORS runtime permission
            if (ContextCompat.checkSelfPermission(
                    applicationContext, Manifest.permission.BODY_SENSORS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                readSensor(Sensor.TYPE_HEART_RATE, listOf("heartRate"))
            } else {
                OptimizedLogger.w(TAG, "BODY_SENSORS not granted — skipping heart rate sensor")
            }

            val entity = SensorDataEntity(
                timestamp = System.currentTimeMillis(),
                deviceId = deviceId,
                accelX = values["accelX"],
                accelY = values["accelY"],
                accelZ = values["accelZ"],
                gyroX = values["gyroX"],
                gyroY = values["gyroY"],
                gyroZ = values["gyroZ"],
                magX = values["magX"],
                magY = values["magY"],
                magZ = values["magZ"],
                gravityX = values["gravityX"],
                gravityY = values["gravityY"],
                gravityZ = values["gravityZ"],
                linearAccelX = values["linearAccelX"],
                linearAccelY = values["linearAccelY"],
                linearAccelZ = values["linearAccelZ"],
                rotX = values["rotX"],
                rotY = values["rotY"],
                rotZ = values["rotZ"],
                rotScalar = values["rotScalar"],
                pressure = values["pressure"],
                humidity = values["humidity"],
                ambientTemperature = values["ambientTemperature"],
                light = values["light"],
                proximity = values["proximity"],
                steps = values["steps"],
                stepDetected = values["stepDetected"],
                heartRate = values["heartRate"]
            )

            db.sensorDataDao().insert(entity)

            val pending = db.sensorDataDao().getNotUploaded()
            val uploadedIds = mutableListOf<Long>()
            pending.forEach { row ->
                val data = HashMap<String, Any>()
                data["timestamp"] = row.timestamp
                data["timezone"] = row.timezone
                data["deviceId"] = row.deviceId
                row.accelX?.let { data["accelX"] = it }
                row.accelY?.let { data["accelY"] = it }
                row.accelZ?.let { data["accelZ"] = it }
                row.gyroX?.let { data["gyroX"] = it }
                row.gyroY?.let { data["gyroY"] = it }
                row.gyroZ?.let { data["gyroZ"] = it }
                row.magX?.let { data["magX"] = it }
                row.magY?.let { data["magY"] = it }
                row.magZ?.let { data["magZ"] = it }
                row.gravityX?.let { data["gravityX"] = it }
                row.gravityY?.let { data["gravityY"] = it }
                row.gravityZ?.let { data["gravityZ"] = it }
                row.linearAccelX?.let { data["linearAccelX"] = it }
                row.linearAccelY?.let { data["linearAccelY"] = it }
                row.linearAccelZ?.let { data["linearAccelZ"] = it }
                row.rotX?.let { data["rotX"] = it }
                row.rotY?.let { data["rotY"] = it }
                row.rotZ?.let { data["rotZ"] = it }
                row.rotScalar?.let { data["rotScalar"] = it }
                row.pressure?.let { data["pressure"] = it }
                row.humidity?.let { data["humidity"] = it }
                row.ambientTemperature?.let { data["ambientTemperature"] = it }
                row.light?.let { data["light"] = it }
                row.proximity?.let { data["proximity"] = it }
                row.steps?.let { data["steps"] = it }
                row.stepDetected?.let { data["stepDetected"] = it }
                row.heartRate?.let { data["heartRate"] = it }
                if (FirebaseServiceHelper.uploadSensorData(userEmail, deviceId, data)) {
                    uploadedIds.add(row.id)
                }
            }
            if (uploadedIds.isNotEmpty()) {
                db.sensorDataDao().markAsUploaded(uploadedIds, System.currentTimeMillis())
            }

            OptimizedLogger.d(TAG, "Sensor data synced. Uploaded=${uploadedIds.size}")
            Result.success()
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error collecting sensor data", e)
            Result.retry()
        }
    }
}
