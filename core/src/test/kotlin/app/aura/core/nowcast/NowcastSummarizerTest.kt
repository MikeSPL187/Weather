package app.aura.core.nowcast

import app.aura.core.model.Nowcast
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowcastSummarizerTest {

    private fun nowcast(vararg mmH: Double) = Nowcast(LocalTime.of(17, 5), 5, mmH.toList(), 87)

    @Test
    fun rainEndingSoon() {
        val summary = NowcastSummarizer.summarize(nowcast(2.4, 1.8, 1.1, 0.5, 0.05, 0.0, 0.0))
        assertEquals("Дождь закончится через 20 мин", summary.headline)
        assertEquals(20, summary.minutesUntilChange)
        assertTrue(summary.isRainingNow)
    }

    @Test
    fun rainStartingLater() {
        val summary = NowcastSummarizer.summarize(nowcast(0.0, 0.0, 0.0, 0.4, 1.2))
        assertEquals("Дождь начнётся через 15 мин", summary.headline)
        assertFalse(summary.isRainingNow)
    }

    @Test
    fun drizzleBelowThresholdCountsAsDry() {
        val summary = NowcastSummarizer.summarize(nowcast(*DoubleArray(24) { 0.05 }))
        assertEquals("Без осадков в ближайшие 2 часа", summary.headline)
        assertNull(summary.minutesUntilChange)
    }

    @Test
    fun steadyRainForTheWholeWindow() {
        val summary = NowcastSummarizer.summarize(nowcast(*DoubleArray(12) { 1.5 }))
        assertEquals("Дождь не прекратится в ближайший час", summary.headline)
    }
}
