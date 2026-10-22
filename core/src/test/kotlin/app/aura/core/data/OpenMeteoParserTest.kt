package app.aura.core.data

import app.aura.core.model.Condition
import app.aura.core.model.DataSource
import app.aura.core.model.Landmark
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoParserTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/open-meteo/$name")) { "missing fixture $name" }.readText()

    @Test
    fun currentConditionsAndUnits() {
        val forecast = OpenMeteoParser.parse(fixture("forecast_moscow.json"), Cities.moscow, airQualityIndex = 31)
        val now = forecast.current
        assertEquals(DataSource.LIVE, forecast.source)
        assertEquals(9.6, now.temperatureC, 0.01)
        assertEquals(6.9, now.feelsLikeC, 0.01)
        assertEquals(Condition.RAIN, now.condition)
        assertEquals(747, now.pressureMmHg) // 996.2 hPa
        assertEquals(24.14, now.visibilityKm!!, 0.01)
        assertEquals(31, now.airQualityIndex)
        assertEquals(180, forecast.city.utcOffsetMinutes)
    }

    @Test
    fun hourlyStartsAtTheCurrentHour() {
        val forecast = OpenMeteoParser.parse(fixture("forecast_moscow.json"), Cities.moscow, null)
        assertEquals(24, forecast.hourly.size)
        assertEquals(LocalDateTime.of(2026, 10, 22, 14, 0), forecast.hourly.first().time)
        assertEquals(80, forecast.hourly.first().precipitationChancePercent)
        assertTrue(forecast.hourly[3].time.hour == 17)
    }

    @Test
    fun dailyIncludesSunTimesAndWmoMapping() {
        val days = OpenMeteoParser.parse(fixture("forecast_moscow.json"), Cities.moscow, null).daily
        assertEquals(10, days.size)
        assertEquals(LocalTime.of(7, 40), days.first().sunrise)
        assertEquals(Condition.SNOW, days[7].condition)
        assertEquals(Condition.CLEAR, days[5].condition)
    }

    @Test
    fun nowcastUsesFifteenMinuteSteps() {
        val nowcast = OpenMeteoParser.parse(fixture("forecast_moscow.json"), Cities.moscow, null).nowcast
        assertEquals(15, nowcast.stepMinutes)
        assertEquals(LocalTime.of(14, 15), nowcast.start)
        assertEquals(8, nowcast.intensitiesMmH.size)
        assertEquals(1.2, nowcast.intensitiesMmH.first(), 0.001) // 0.3 mm per 15 min
        assertEquals(0.0, nowcast.intensitiesMmH[1], 0.001)
    }

    @Test
    fun nullsInArraysDoNotBreakParsing() {
        val forecast = OpenMeteoParser.parse(fixture("forecast_moscow.json"), Cities.moscow, null)
        assertNull(forecast.current.airQualityIndex)
    }

    @Test
    fun searchResultsCarryRegionOffsetAndLandmark() {
        val cities = OpenMeteoParser.parseSearch(fixture("search.json"), Instant.parse("2026-10-22T10:00:00Z"))
        assertEquals(2, cities.size)
        assertEquals("Татарстан, Россия", cities[0].region)
        assertEquals(180, cities[0].utcOffsetMinutes)
        assertEquals(Landmark.KAZAN_MOSQUE, cities[0].landmark)
        assertEquals(300, cities[1].utcOffsetMinutes) // Asia/Almaty is UTC+5 since 2024
        assertEquals("Алматы, Казахстан", cities[1].region)
    }

    @Test
    fun emptySearchHasNoResultsKey() {
        assertTrue(OpenMeteoParser.parseSearch("{\"generationtime_ms\":0.3}").isEmpty())
    }

    @Test
    fun airQuality() {
        assertEquals(31, OpenMeteoParser.parseAirQuality(fixture("air_quality.json")))
    }

    @Test
    fun repositoryToleratesAirQualityFailure() = runTest {
        val http = HttpFetcher { url ->
            if (url.contains("air-quality")) throw java.io.IOException("offline") else fixture("forecast_moscow.json")
        }
        val forecast = OpenMeteoWeatherRepository(http).forecast(Cities.moscow, LocalDateTime.now())
        assertNull(forecast.current.airQualityIndex)
        assertEquals(9.6, forecast.current.temperatureC, 0.01)
    }

    @Test
    fun wmoCodes() {
        assertEquals(Condition.CLEAR, conditionForWmo(0))
        assertEquals(Condition.FOG, conditionForWmo(45))
        assertEquals(Condition.RAIN, conditionForWmo(81))
        assertEquals(Condition.SNOW, conditionForWmo(86))
        assertEquals(Condition.STORM, conditionForWmo(95))
    }
}
