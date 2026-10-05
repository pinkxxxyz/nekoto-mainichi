package com.catlife.app

import com.catlife.app.settings.WeatherLocation
import com.catlife.app.weather.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.CopyOnWriteArrayList

class OpenMeteoWeatherTest {
    private val tokyo = WeatherLocation("130001", "東京都", "131016", "千代田区", 35.69, 139.75)
    private val osaka = WeatherLocation("270008", "大阪府", "271004", "大阪市", 34.69, 135.50)
    private val good = """{"current":{"temperature_2m":23.6,"weather_code":61},"daily":{"temperature_2m_max":[27.4],"temperature_2m_min":[18.5]}}"""

    private suspend fun serverTest(test: suspend (LocalWeatherHttpServer, String) -> Unit) {
        val server = LocalWeatherHttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool()
        server.executor = executor
        server.start()
        try { test(server, "http://127.0.0.1:${server.address.port}/forecast") }
        finally { server.stop(0); executor.shutdownNow() }
    }

    @Test fun unsetRegionDoesNotAccessNetwork() = runBlocking {
        serverTest { server, endpoint ->
            var calls = 0
            server.createContext("/forecast") { calls++ }
            assertNull(OpenMeteoWeatherRepository(endpoint).fetch(null))
            assertEquals(0, calls)
        }
    }

    @Test fun realHttpResponseUsesSelectedCoordinatesAndRequiredParameters() = runBlocking {
        serverTest { server, endpoint ->
            var query: Map<String, String>? = null
            server.createContext("/forecast") { exchange ->
                query = exchange.requestURI.rawQuery.split('&').associate {
                    val pair = it.split('=', limit = 2)
                    pair[0] to URLDecoder.decode(pair[1], "UTF-8")
                }
                val body = good.toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            val reading = OpenMeteoWeatherRepository(endpoint).fetch(tokyo)!!
            assertEquals(23.6, reading.currentTemperature, 0.001)
            assertEquals(27.4, reading.highTemperature, 0.001)
            assertEquals(18.5, reading.lowTemperature, 0.001)
            assertEquals(61, reading.weatherCode)
            assertEquals("35.69", query!!["latitude"])
            assertEquals("139.75", query!!["longitude"])
            assertEquals("temperature_2m,weather_code", query!!["current"])
            assertEquals("temperature_2m_max,temperature_2m_min", query!!["daily"])
            assertEquals("auto", query!!["timezone"])
            assertEquals("1", query!!["forecast_days"])
            assertFalse(query!!.containsKey("apikey"))
        }
    }

    @Test fun httpApiMalformedMissingAndNullJsonBecomeUnavailable() = runBlocking {
        serverTest { server, endpoint ->
            var status = 503
            var body = good
            server.createContext("/forecast") { exchange ->
                val bytes = body.toByteArray()
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            val repository = OpenMeteoWeatherRepository(endpoint)
            assertNull(repository.fetch(tokyo))
            status = 200
            for (invalid in listOf("not json", "{}", "{\"error\":true}",
                good.replace("23.6", "null"), good.replace("[27.4]", "[]"),
                good.replace("23.6", "\"hot\""), good.replace("61", "61.5"))) {
                body = invalid
                assertNull("body=$invalid", repository.fetch(tokyo))
            }
        }
    }

    @Test fun connectionFailureAndReadTimeoutBecomeUnavailable() = runBlocking {
        serverTest { server, endpoint ->
            server.createContext("/forecast") { exchange ->
                Thread.sleep(200)
                runCatching { exchange.sendResponseHeaders(200, 0); exchange.close() }
            }
            assertNull(OpenMeteoWeatherRepository(endpoint, readTimeoutMillis = 30).fetch(tokyo))
            server.stop(0)
            assertNull(OpenMeteoWeatherRepository(endpoint, connectTimeoutMillis = 100).fetch(tokyo))
        }
    }

    @Test fun coordinateChangesAndHomeReturnsFetchButRepeatedSameStateDoesNot() = runBlocking {
        serverTest { server, endpoint ->
            val queries = CopyOnWriteArrayList<String>()
            server.createContext("/forecast") { exchange ->
                val query = exchange.requestURI.rawQuery
                queries.add(query)
                val body = (if (query.contains("latitude=34.69")) good.replace("23.6", "11.2") else good).toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            val locations = MutableStateFlow<WeatherLocation?>(null)
            val visible = MutableStateFlow(true)
            val display = MutableStateFlow<WeatherReading?>(null)
            val collector = launch { OpenMeteoWeatherRepository(endpoint).observe(locations, visible).collect { display.value = it } }
            try {
                delay(50)
                assertTrue(queries.isEmpty())
                locations.value = tokyo
                withTimeout(3000) { display.filterNotNull().first { it.currentTemperature == 23.6 } }
                locations.value = osaka
                withTimeout(3000) { display.filterNotNull().first { it.currentTemperature == 11.2 } }
                assertEquals(2, queries.size)
                assertTrue(queries[1].contains("longitude=135.5"))
                locations.value = osaka.copy()
                visible.value = true
                delay(50)
                assertEquals(2, queries.size)
                visible.value = false
                withTimeout(3000) { display.first { it == null } }
                visible.value = true
                withTimeout(3000) { display.filterNotNull().first() }
                assertEquals(3, queries.size)
            } finally { collector.cancelAndJoin() }
        }
    }
}
