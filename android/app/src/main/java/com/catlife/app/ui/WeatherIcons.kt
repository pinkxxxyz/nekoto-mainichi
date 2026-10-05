package com.catlife.app.ui

import com.catlife.app.R
import com.catlife.app.weather.WeatherCondition

internal fun weatherIconResource(condition: WeatherCondition?): Int = when (condition) {
    WeatherCondition.CLEAR, WeatherCondition.MAINLY_CLEAR -> R.drawable.weather_sun
    WeatherCondition.PARTLY_CLOUDY, WeatherCondition.CLOUDY -> R.drawable.weather_cloud
    WeatherCondition.FOG -> R.drawable.weather_fog
    WeatherCondition.DRIZZLE, WeatherCondition.RAIN, WeatherCondition.SHOWERS -> R.drawable.weather_rain
    WeatherCondition.SNOW, WeatherCondition.SNOW_SHOWERS -> R.drawable.weather_snow
    WeatherCondition.THUNDERSTORM -> R.drawable.weather_thunder
    null -> R.drawable.weather_cloud // Transparent placeholder keeps the original layout footprint.
}
