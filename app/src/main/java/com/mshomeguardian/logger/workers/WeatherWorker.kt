package com.mshomeguardian.logger.workers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.LocationUtils
import com.mshomeguardian.logger.utils.WeatherUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.HashMap

class WeatherWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val deviceId = DeviceIdentifier.getPersistentDeviceId(context.applicationContext)

    private val firestore: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firestore", e)
        null
    }

    companion object {
        private const val TAG = "WeatherWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            // Check permissions
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Missing ACCESS_FINE_LOCATION permission")
                return@withContext Result.failure()
            }

            // Get current location
            val location = LocationUtils.getLastKnownLocation(applicationContext)

            if (location != null) {
                // Get weather data
                val weatherData = WeatherUtil.getWeatherData(location.latitude, location.longitude)
                val timestamp = System.currentTimeMillis()

                // Store in shared preferences for widget
                val prefs = applicationContext.getSharedPreferences("weather_data", Context.MODE_PRIVATE)
                prefs.edit()
                    .putFloat("latitude", location.latitude.toFloat())
                    .putFloat("longitude", location.longitude.toFloat())
                    .putFloat("temperature", weatherData.temperature.toFloat())
                    .putFloat("feels_like", weatherData.feelsLike.toFloat())
                    .putString("description", weatherData.description)
                    .putInt("humidity", weatherData.humidity)
                    .putFloat("wind_speed", weatherData.windSpeed.toFloat())
                    .putString("icon_code", weatherData.iconCode)
                    .putString("city_name", weatherData.cityName)
                    .putLong("timestamp", timestamp)
                    .apply()

                // Upload to Firestore
                uploadWeatherData(location.latitude, location.longitude, weatherData, timestamp)

                Log.d(TAG, "Weather data updated: ${weatherData.temperature}°C, ${weatherData.description}")
                Result.success()
            } else {
                Log.e(TAG, "Could not get location")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating weather data", e)
            Result.retry()
        }
    }

    private suspend fun uploadWeatherData(
        latitude: Double,
        longitude: Double,
        weatherData: WeatherUtil.WeatherData,
        timestamp: Long
    ) {
        val firestoreInstance = firestore ?: return

        try {
            val weatherMap = HashMap<String, Any>()
            weatherMap["latitude"] = latitude
            weatherMap["longitude"] = longitude
            weatherMap["temperature"] = weatherData.temperature
            weatherMap["feels_like"] = weatherData.feelsLike
            weatherMap["description"] = weatherData.description
            weatherMap["humidity"] = weatherData.humidity
            weatherMap["wind_speed"] = weatherData.windSpeed
            weatherMap["icon_code"] = weatherData.iconCode
            weatherMap["city_name"] = weatherData.cityName
            weatherMap["timestamp"] = timestamp
            weatherMap["deviceId"] = deviceId

            firestoreInstance.collection("devices")
                .document(deviceId)
                .collection("weather")
                .document(timestamp.toString())
                .set(weatherMap, SetOptions.merge())
                .await()

            Log.d(TAG, "Weather data uploaded to Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload weather data", e)
        }
    }
}