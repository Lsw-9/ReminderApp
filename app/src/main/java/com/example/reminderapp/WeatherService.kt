package com.example.reminderapp

import retrofit2.http.GET
import retrofit2.http.Query
import com.example.reminderapp.WeatherType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

interface WeatherService {
    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,weather_code,is_day",
        @Query("timezone") timezone: String = "auto"
    ): WeatherResponse
}

data class WeatherResponse(
    val current: Current,
    val current_units: CurrentUnits
)

data class Current(
    val time: String,
    val temperature_2m: Double,
    val relative_humidity_2m: Int,
    val weather_code: Int,
    val is_day: Int
)

data class CurrentUnits(
    val temperature_2m: String,
    val relative_humidity_2m: String
)

// WMO Weather interpretation codes (WW)
// https://open-meteo.com/en/docs
object WeatherCode {
    fun getWeatherType(code: Int, isDay: Int): WeatherType {
        return when (code) {
            0 -> if (isDay == 1) WeatherType.SUNNY else WeatherType.CLEAR_NIGHT // Clear sky
            1, 2 -> if (isDay == 1) WeatherType.PARTLY_CLOUDY_DAY else WeatherType.PARTLY_CLOUDY_NIGHT // Partly cloudy
            3 -> WeatherType.CLOUDY // Overcast
            45, 48 -> if (isDay == 1) WeatherType.PARTLY_CLOUDY_DAY else WeatherType.PARTLY_CLOUDY_NIGHT // Fog
            51, 53, 55, // Drizzle
            56, 57, // Freezing Drizzle
            61, 63, 65, // Rain
            66, 67, // Freezing Rain
            80, 81, 82 -> WeatherType.RAINY // Rain showers
            71, 73, 75, // Snow fall
            77, // Snow grains
            85, 86 -> WeatherType.SNOWY // Snow showers
            95, 96, 99 -> WeatherType.RAINY // Thunderstorm
            else -> if (isDay == 1) WeatherType.PARTLY_CLOUDY_DAY else WeatherType.PARTLY_CLOUDY_NIGHT
        }
    }

    fun getDescription(code: Int, isDay: Int): String {
        return when (code) {
            0 -> if (isDay == 1) "Clear sky" else "Clear night"
            1 -> if (isDay == 1) "Mainly clear" else "Mainly clear night"
            2 -> if (isDay == 1) "Partly cloudy" else "Partly cloudy night"
            3 -> "Overcast"
            45 -> "Fog"
            48 -> "Depositing rime fog"
            51 -> "Light drizzle"
            53 -> "Moderate drizzle"
            55 -> "Dense drizzle"
            56 -> "Light freezing drizzle"
            57 -> "Dense freezing drizzle"
            61 -> "Slight rain"
            63 -> "Moderate rain"
            65 -> "Heavy rain"
            66 -> "Light freezing rain"
            67 -> "Heavy freezing rain"
            71 -> "Slight snow fall"
            73 -> "Moderate snow fall"
            75 -> "Heavy snow fall"
            77 -> "Snow grains"
            80 -> "Slight rain showers"
            81 -> "Moderate rain showers"
            82 -> "Violent rain showers"
            85 -> "Slight snow showers"
            86 -> "Heavy snow showers"
            95 -> "Thunderstorm"
            96 -> "Thunderstorm with slight hail"
            99 -> "Thunderstorm with heavy hail"
            else -> if (isDay == 1) "Unknown weather condition" else "Unknown night condition"
        }
    }
}