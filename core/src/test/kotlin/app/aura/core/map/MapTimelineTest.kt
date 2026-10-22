package app.aura.core.map

import java.time.Instant
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapTimelineTest {
    // Radar at 13:20, 13:30 … 14:00 Moscow time (UTC+3), then forecast 14:00 … 16:00.
    private val radar = (0..4).map { RadarFrame(Instant.parse("2026-10-22T10:20:00Z").plusSeconds(it * 600L), "/v2/radar/$it") }
    private val forecast = (0..2).map { LocalDateTime.of(2026, 10, 22, 14 + it, 0) }
    private val timeline = MapTimeline(radar, forecast, 180)

    @Test
    fun nowIsTheLatestRadarFrame() {
        assertEquals(4, timeline.now)
        assertEquals(6, timeline.last)
        assertEquals("Сейчас · 14:00", timeline.label(4f))
        assertEquals("−40 мин · 13:20", timeline.label(0f))
        assertEquals("+2 ч · 16:00", timeline.label(6f))
        assertTrue(timeline.isRadar(3.4f))
        assertFalse(timeline.isRadar(5f))
    }

    @Test
    fun blendsRadarFramesThenFadesIntoTheForecast() {
        val between = timeline.moment(1.25f)
        assertEquals(1, between.radarFrom)
        assertEquals(2, between.radarTo)
        assertEquals(0.25f, between.radarBlend, 1e-6f)
        assertEquals(0f, between.forecastOpacity, 1e-6f)

        val half = timeline.moment(4.5f)
        assertEquals(0.5f, half.radarOpacity, 1e-6f)
        assertEquals(0.5f, half.forecastOpacity, 1e-6f)
        assertEquals(0.5f, half.forecastHour, 1e-6f)

        val later = timeline.moment(6f)
        assertEquals(0f, later.radarOpacity, 1e-6f)
        assertEquals(2f, later.forecastHour, 1e-6f)
    }

    @Test
    fun withoutRadarItIsForecastOnly() {
        val only = MapTimeline(emptyList(), forecast, 180)
        assertEquals(0, only.now)
        assertEquals("Сейчас · 14:00", only.label(0f))
        assertEquals("+1 ч · 15:00", only.label(1f))
        assertEquals(1f, only.moment(1f).forecastOpacity, 1e-6f)
    }
}
