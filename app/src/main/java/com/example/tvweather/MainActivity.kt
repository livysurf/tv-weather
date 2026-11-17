package com.example.tvweather

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private lateinit var heroTemp: TextView
    private lateinit var heroCondition: TextView
    private lateinit var heroCity: TextView
    private lateinit var heroTime: TextView
    private lateinit var detailClouds: TextView
    private lateinit var detailRain: TextView
    private lateinit var detailWind: TextView
    private lateinit var detailHumidity: TextView
    private lateinit var dailyContainer: LinearLayout
    
    private val cityName = "Seattle"
    private val latitude = 47.6062
    private val longitude = -122.3321

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_main)

        heroTemp = findViewById(R.id.hero_temp)
        heroCondition = findViewById(R.id.hero_condition)
        heroCity = findViewById(R.id.hero_city)
        heroTime = findViewById(R.id.hero_time)
        detailClouds = findViewById(R.id.detail_clouds)
        detailRain = findViewById(R.id.detail_rain)
        detailWind = findViewById(R.id.detail_wind)
        detailHumidity = findViewById(R.id.detail_humidity)
        dailyContainer = findViewById(R.id.daily_container)

        heroCity.text = cityName
        loadWeather()
    }

    private fun loadWeather() {
        executor.execute {
            try {
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current_weather=true&hourly=relativehumidity_2m,cloud_cover,precipitation&daily=temperature_2m_max,temperature_2m_min,weathercode&timezone=auto")
                val connection = url.openConnection() as HttpURLConnection
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                
                val cw = json.getJSONObject("current_weather")
                val daily = json.getJSONObject("daily")
                val hourly = json.getJSONObject("hourly")
                
                val temp = cw.getDouble("temperature").roundToInt()
                val wind = cw.getDouble("windspeed").roundToInt()
                val currentCode = cw.getInt("weathercode")
                
                val hTimes = hourly.getJSONArray("time")
                val currentHourStr = SimpleDateFormat("yyyy-MM-dd'T'HH:00", Locale.getDefault()).format(Date())
                var currentIndex = 0
                for (i in 0 until hTimes.length()) {
                    if (hTimes.getString(i) == currentHourStr) {
                        currentIndex = i
                        break
                    }
                }

                val humidity = hourly.getJSONArray("relativehumidity_2m").getInt(currentIndex)
                val clouds = hourly.getJSONArray("cloud_cover").getInt(currentIndex)
                val rain = hourly.getJSONArray("precipitation").getDouble(currentIndex)

                val dMax = daily.getJSONArray("temperature_2m_max")
                val dMin = daily.getJSONArray("temperature_2m_min")
                val dCodes = daily.getJSONArray("weathercode")
                val dTime = daily.getJSONArray("time")

                val updated = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val dayOfWeek = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())

                mainHandler.post {
                    heroTemp.text = "$temp°"
                    heroCondition.text = weatherText(currentCode)
                    heroTime.text = "$updated - $dayOfWeek"
                    
                    detailClouds.text = "$clouds %"
                    detailRain.text = "$rain mm"
                    detailWind.text = "$wind km/h"
                    detailHumidity.text = "$humidity %"
                    
                    buildDailyForecast(dTime, dMax, dMin, dCodes)
                }
            } catch (e: Exception) {
                mainHandler.post { heroCondition.text = "OFFLINE" }
            }
        }
    }

    private fun buildDailyForecast(times: org.json.JSONArray, maxTemps: org.json.JSONArray, minTemps: org.json.JSONArray, codes: org.json.JSONArray) {
        val inflater = LayoutInflater.from(this)
        dailyContainer.removeAllViews()
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("EEE", Locale.getDefault())

        for (i in 0 until 7) {
            if (i >= times.length()) break
            val view = inflater.inflate(R.layout.item_daily, dailyContainer, false)
            val dateObj = inputFormat.parse(times.getString(i))
            
            view.findViewById<TextView>(R.id.day_name).text = if (i == 0) "TODAY" else outputFormat.format(dateObj).uppercase(Locale.getDefault())
            
            val hi = maxTemps.getDouble(i).roundToInt()
            val lo = minTemps.getDouble(i).roundToInt()
            view.findViewById<TextView>(R.id.day_temp).text = "$hi°"
            view.findViewById<TextView>(R.id.day_low).text = "L $lo°"
            
            view.setOnFocusChangeListener { v, hasFocus ->
                val scale = if (hasFocus) 1.1f else 1f
                v.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
            }
            dailyContainer.addView(view)
        }
    }

    private fun weatherText(code: Int): String = when (code) {
        0 -> "Clear"; 1, 2 -> "Partly Cloudy"; 3 -> "Overcast"; 45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"; 61, 63, 65 -> "Rainy"; 71, 73, 75 -> "Snow"
        80, 81, 82 -> "Showers"; 95 -> "Thunderstorm"; else -> "Cloudy"
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdown()
        super.onDestroy()
    }
}