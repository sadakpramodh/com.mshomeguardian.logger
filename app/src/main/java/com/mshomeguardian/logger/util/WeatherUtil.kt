package com.mshomeguardian.logger.util

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object WeatherUtil {

    private const val API_KEY = "ef856b7e2fc9edee0a644d1aa11fec95"

    fun getWeather(latitude: Double, longitude: Double): String {
        val url = "https://api.openweathermap.org/data/2.5/weather?lat=$latitude&lon=$longitude&appid=$API_KEY&units=metric"
        val request = Request.Builder().url(url).build()
        val client = OkHttpClient()
        val response = client.newCall(request).execute()

        response.body?.string()?.let {
            val json = JSONObject(it)
            val main = json.getJSONObject("main").getDouble("temp")
            val weather = json.getJSONArray("weather").getJSONObject(0).getString("description")
            return "$main°C, $weather"
        }

        return "N/A"
    }
}