package com.catlife.app

import com.catlife.app.ui.homeWeatherDisplay
import com.catlife.app.weather.*
import org.junit.Assert.*
import org.junit.Test

class HomeWeatherDisplayTest {
    @Test fun unsetOrFailedWeatherShowsOnlyPlaceholders() {
        val display = homeWeatherDisplay(null)
        assertFalse(display.showWeatherIcon)
        assertEquals(listOf("ーー", "ーー", "ーー", "ーー"), listOf(display.condition, display.currentTemperature, display.highTemperature, display.lowTemperature))
    }

    @Test fun responseIsParsedIntoRoundedCurrentAndTodayTemperatures() {
        val data = parseOpenMeteoWeather("""{"current":{"temperature_2m":23.6,"weather_code":61},"daily":{"temperature_2m_max":[27.4],"temperature_2m_min":[18.5]}}""")
        val display = homeWeatherDisplay(data)
        assertTrue(display.showWeatherIcon)
        assertEquals("雨", display.condition)
        assertEquals("24℃", display.currentTemperature)
        assertEquals("27℃", display.highTemperature)
        assertEquals("19℃", display.lowTemperature)
        assertEquals("-3℃", homeWeatherDisplay(WeatherReading(-2.6, 1.0, -6.8, 71)).currentTemperature)
    }

    @Test fun weatherCodesCoverEveryRequestedCategoryAndUnknownIsNotSunny() {
        val cases = listOf(0 to "晴れ", 1 to "ほぼ晴れ", 2 to "薄曇り", 3 to "くもり", 45 to "霧", 48 to "霧") +
            (51..57).map { it to "霧雨" } + (61..67).map { it to "雨" } +
            (71..77).map { it to "雪" } + (80..82).map { it to "にわか雨" } +
            (85..86).map { it to "にわか雪" } + (95..99).map { it to "雷雨" }
        cases.forEach { (code, label) -> assertEquals("code=$code", label, weatherCondition(code)?.label) }
        assertNull(weatherCondition(10))
    }
}
