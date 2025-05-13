package com.mshomeguardian.logger.utils

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object WeatherUtil {

    private const val API_KEY = "ef856b7e2fc9edee0a644d1aa11fec95"
    private const val TAG = "WeatherUtil"

    data class WeatherData(
        val temperature: Double,
        val feelsLike: Double,
        val description: String,
        val humidity: Int,
        val windSpeed: Double,
        val iconCode: String,
        val cityName: String
    )

    fun getWeatherData(latitude: Double, longitude: Double): WeatherData {
        val url = "https://api.openweathermap.org/data/2.5/weather?lat=$latitude&lon=$longitude&appid=$API_KEY&units=metric"

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(url).build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val jsonResponse = JSONObject(responseBody)

                val main = jsonResponse.getJSONObject("main")
                val weather = jsonResponse.getJSONArray("weather").getJSONObject(0)
                val wind = jsonResponse.getJSONObject("wind")

                val temperature = main.getDouble("temp")
                val feelsLike = main.getDouble("feels_like")
                val humidity = main.getInt("humidity")
                val description = weather.getString("description")
                val windSpeed = wind.getDouble("speed")
                val iconCode = weather.getString("icon")
                val cityName = jsonResponse.getString("name")

                return WeatherData(
                    temperature = temperature,
                    feelsLike = feelsLike,
                    description = description.capitalize(),
                    humidity = humidity,
                    windSpeed = windSpeed,
                    iconCode = iconCode,
                    cityName = cityName
                )
            } else {
                Log.e(TAG, "Weather API error: ${response.code} - ${responseBody ?: "No body"}")
                return WeatherData(
                    temperature = 0.0,
                    feelsLike = 0.0,
                    description = "Weather data unavailable",
                    humidity = 0,
                    windSpeed = 0.0,
                    iconCode = "",
                    cityName = ""
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Weather API request failed", e)
            return WeatherData(
                temperature = 0.0,
                feelsLike = 0.0,
                description = "Network error",
                humidity = 0,
                windSpeed = 0.0,
                iconCode = "",
                cityName = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Weather data parsing failed", e)
            return WeatherData(
                temperature = 0.0,
                feelsLike = 0.0,
                description = "Error processing weather data",
                humidity = 0,
                windSpeed = 0.0,
                iconCode = "",
                cityName = ""
            )
        }
    }

    // Legacy method for backward compatibility
    fun getWeather(latitude: Double, longitude: Double): String {
        try {
            val weatherData = getWeatherData(latitude, longitude)
            return "${weatherData.temperature}°C, ${weatherData.description}"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting weather", e)
            return "N/A"
        }
    }
}