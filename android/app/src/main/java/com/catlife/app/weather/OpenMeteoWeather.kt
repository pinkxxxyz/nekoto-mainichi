package com.catlife.app.weather

import com.catlife.app.settings.WeatherLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class WeatherReading(
    val currentTemperature: Double,
    val highTemperature: Double,
    val lowTemperature: Double,
    val weatherCode: Int,
)

internal enum class WeatherCondition(val label: String) {
    CLEAR("晴れ"), MAINLY_CLEAR("ほぼ晴れ"), PARTLY_CLOUDY("薄曇り"), CLOUDY("くもり"),
    FOG("霧"), DRIZZLE("霧雨"), RAIN("雨"), SNOW("雪"),
    SHOWERS("にわか雨"), SNOW_SHOWERS("にわか雪"), THUNDERSTORM("雷雨"),
}

internal fun weatherCondition(code: Int): WeatherCondition? = when (code) {
    0 -> WeatherCondition.CLEAR
    1 -> WeatherCondition.MAINLY_CLEAR
    2 -> WeatherCondition.PARTLY_CLOUDY
    3 -> WeatherCondition.CLOUDY
    45, 48 -> WeatherCondition.FOG
    in 51..57 -> WeatherCondition.DRIZZLE
    in 61..67 -> WeatherCondition.RAIN
    in 71..77 -> WeatherCondition.SNOW
    in 80..82 -> WeatherCondition.SHOWERS
    85, 86 -> WeatherCondition.SNOW_SHOWERS
    in 95..99 -> WeatherCondition.THUNDERSTORM
    else -> null
}

private fun finiteTemperature(value: Any): Double {
    require(value is Number) { "Temperature must be numeric" }
    return value.toDouble().also { require(it.isFinite()) }
}

internal fun parseOpenMeteoWeather(body: String): WeatherReading {
    val json = JSONObject(body)
    require(!json.optBoolean("error", false))
    val current = json.getJSONObject("current")
    val daily = json.getJSONObject("daily")
    val code = current.get("weather_code")
    require(code is Number && code.toDouble().isFinite() && code.toDouble() == code.toInt().toDouble())
    val high = finiteTemperature(daily.getJSONArray("temperature_2m_max").get(0))
    val low = finiteTemperature(daily.getJSONArray("temperature_2m_min").get(0))
    require(high >= low)
    return WeatherReading(finiteTemperature(current.get("temperature_2m")), high, low, code.toInt())
}

internal class OpenMeteoWeatherRepository(
    private val endpoint: String = "https://api.open-meteo.com/v1/forecast",
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 10_000,
) {
    suspend fun fetch(location: WeatherLocation?): WeatherReading? {
        if (location == null) return null
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return null
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                currentCoroutineContext().ensureActive()
                val url = URL("$endpoint?latitude=${location.latitude}&longitude=${location.longitude}" +
                    "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min" +
                    "&timezone=auto&forecast_days=1&temperature_unit=celsius")
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = connectTimeoutMillis
                connection.readTimeout = readTimeoutMillis
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                if (connection.responseCode !in 200..299) return@withContext null
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                currentCoroutineContext().ensureActive()
                parseOpenMeteoWeather(body)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } finally {
                connection?.disconnect()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(locations: Flow<WeatherLocation?>, homeVisible: Flow<Boolean>): Flow<WeatherReading?> =
        combine(locations, homeVisible) { location, visible -> location to visible }
            .distinctUntilChanged()
            .transformLatest { (location, visible) ->
                // Clear old-region/failed readings, and cancel stale requests on any change.
                emit(null)
                if (visible && location != null) emit(fetch(location))
            }
}
