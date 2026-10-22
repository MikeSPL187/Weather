package app.aura.core.advice

import app.aura.core.model.Condition
import app.aura.core.model.DailyForecast
import app.aura.core.model.HourlyForecast
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdviceTest {

    private val today = LocalDate.of(2026, 10, 22) // Thursday

    private fun hour(
        h: Int,
        feels: Double,
        chance: Int = 0,
        mm: Double = 0.0,
        wind: Double = 3.0,
        condition: Condition = Condition.CLOUDY,
        uv: Double = 1.0,
    ) = HourlyForecast(
        time = LocalDateTime.of(2026, 10, 22, h, 0),
        temperatureC = feels + 2,
        feelsLikeC = feels,
        condition = condition,
        precipitationChancePercent = chance,
        precipitationMm = mm,
        windSpeedMs = wind,
        uvIndex = uv,
        isDay = h in 8..16,
    )

    @Test
    fun coldRainyDayNeedsWarmLayersAndUmbrella() {
        val outfit = OutfitAdvisor.advise(listOf(hour(9, 2.0, chance = 70, mm = 0.6), hour(10, 4.0)))
        assertTrue(outfit.umbrella)
        assertTrue(outfit.items.any { it.label == "Тёплая куртка" })
        assertTrue(outfit.summary.contains("возможен дождь: 70 %"))
    }

    @Test
    fun warmSunnyDayNeedsSunscreenNotUmbrella() {
        val outfit = OutfitAdvisor.advise(listOf(hour(12, 26.0, condition = Condition.CLEAR, uv = 6.0)))
        assertFalse(outfit.umbrella)
        assertTrue(outfit.sunscreen)
        assertTrue(outfit.items.any { it.label == "Шорты" })
    }

    @Test
    fun snowMeansNoUmbrella() {
        val outfit = OutfitAdvisor.advise(listOf(hour(9, -8.0, chance = 80, mm = 1.0, condition = Condition.SNOW)))
        assertFalse(outfit.umbrella)
        assertTrue(outfit.items.any { it.label == "Зимняя куртка" })
    }

    @Test
    fun runAvoidsTheRainyHours() {
        val hours = (8..16).map { h -> if (h in 10..12) hour(h, 10.0, chance = 90, mm = 1.5) else hour(h, 10.0) }
        val window = ActivityPlanner.bestWindow(Activity.RUN, hours, LocalTime.of(7, 40), LocalTime.of(17, 13))!!
        assertTrue(window.start.hour !in 10..12)
        assertTrue(window.score >= 8)
    }

    @Test
    fun photoPrefersGoldenHour() {
        val hours = (8..16).map { hour(it, 8.0, condition = Condition.CLEAR) }
        val window = ActivityPlanner.bestWindow(Activity.PHOTO, hours, LocalTime.of(7, 40), LocalTime.of(17, 13))!!
        assertTrue(window.start.hour == 8 || window.start.hour == 16)
    }

    @Test
    fun badDayIsCalledOut() {
        val hours = (8..16).map { hour(it, 2.0, chance = 95, mm = 3.0, wind = 14.0, condition = Condition.STORM) }
        val window = ActivityPlanner.bestWindow(Activity.BIKE, hours, null, null)!!
        assertTrue(window.score <= 3)
        assertTrue(window.reason.startsWith("Сегодня неудачный день"))
    }

    private fun day(offset: Long, condition: Condition, max: Double, chance: Int = 0) = DailyForecast(
        date = today.plusDays(offset), minC = max - 6, maxC = max, condition = condition,
        precipitationChancePercent = chance, precipitationSumMm = 0.0, windSpeedMaxMs = 4.0, uvIndexMax = 1.0,
        sunrise = null, sunset = null,
    )

    @Test
    fun weekSummaryNamesRainAndCooling() {
        val days = listOf(
            day(0, Condition.CLEAR, 12.0), day(1, Condition.CLOUDY, 11.0), day(2, Condition.RAIN, 10.0, 80),
            day(3, Condition.CLOUDY, 6.0), day(4, Condition.CLOUDY, 5.0), day(5, Condition.CLOUDY, 5.0), day(6, Condition.CLEAR, 4.0),
        )
        assertEquals("Дождь в субботу. Похолодание: днём на 6° ниже, чем сейчас.", WeekSummary.describe(days, today))
    }

    @Test
    fun weekSummaryDryAndSnow() {
        val dry = List(5) { day(it.toLong(), Condition.CLEAR, 10.0) }
        assertEquals("Без осадков все 5 дней.", WeekSummary.describe(dry, today))
        val snowy = listOf(day(0, Condition.CLOUDY, 2.0), day(1, Condition.SNOW, 0.0, 70))
        assertEquals("Снег завтра.", WeekSummary.describe(snowy, today))
    }
}
