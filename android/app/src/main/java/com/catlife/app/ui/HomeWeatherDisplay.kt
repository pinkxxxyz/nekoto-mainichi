package com.catlife.app.ui

import com.catlife.app.weather.WeatherCondition
import com.catlife.app.weather.WeatherReading
import com.catlife.app.weather.weatherCondition
import kotlin.math.roundToInt

internal data class HomeWeatherDisplay(
    val showWeatherIcon: Boolean,
    val condition: String,
    val currentTemperature: String,
    val highTemperature: String,
    val lowTemperature: String,
    val weatherCondition: WeatherCondition? = null,
)

internal fun homeWeatherDisplay(reading: WeatherReading?): HomeWeatherDisplay {
    if (reading == null) return HomeWeatherDisplay(false, "ーー", "ーー", "ーー", "ーー")
    val condition = weatherCondition(reading.weatherCode)
    return HomeWeatherDisplay(
        condition != null, condition?.label ?: "ーー",
        "${reading.currentTemperature.roundToInt()}℃", "${reading.highTemperature.roundToInt()}℃",
        "${reading.lowTemperature.roundToInt()}℃", condition,
    )
}
