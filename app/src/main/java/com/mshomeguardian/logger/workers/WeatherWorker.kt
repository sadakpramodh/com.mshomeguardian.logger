package com.mshomeguardian.logger.workers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.LocationUtils
import com.mshomeguardian.logger.utils.WeatherUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mshomeguardian.logger.utils.FirebaseServiceHelper

class WeatherWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val deviceId = DeviceIdentifier.getPersistentDeviceId(context.applicationContext)


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
        try {
            val weatherMap = mutableMapOf<String, Any>()
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

            val userEmail = FirebaseServiceHelper.getCurrentUserEmail()
            if (userEmail == null) {
                Log.e(TAG, "User not authenticated")
                return
            }

            val sanitizedEmail = FirebaseServiceHelper.sanitizeEmailForFirestore(userEmail)
            val collectionPath = "users/$sanitizedEmail/devices/$deviceId/weather"
            Log.d(TAG, "Uploading weather data to $collectionPath")
            val success = FirebaseServiceHelper.uploadWeather(userEmail, deviceId, weatherMap)
            if (success) {
                Log.d(TAG, "Weather data uploaded to $collectionPath")
            } else {
                Log.e(TAG, "Failed to upload weather data to Firestore")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload weather data", e)
        }
    }
}