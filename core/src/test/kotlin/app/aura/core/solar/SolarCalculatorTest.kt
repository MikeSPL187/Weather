package app.aura.core.solar

import app.aura.core.data.Cities
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SolarCalculatorTest {

    private fun assertNear(expected: LocalTime, actual: LocalTime?, toleranceMinutes: Long = 5) {
        requireNotNull(actual)
        val diff = abs(ChronoUnit.MINUTES.between(expected, actual))
        assertTrue("expected $expected, got $actual", diff <= toleranceMinutes)
    }

    @Test
    fun moscowSummerSolsticeMatchesAlmanac() {
        // Moscow, 21 June: sunrise 03:44, sunset 21:18 (MSK, UTC+3).
        val times = SolarCalculator.sunTimes(Cities.moscow, LocalDate.of(2026, 6, 21))
        assertNear(LocalTime.of(3, 44), times.sunrise)
        assertNear(LocalTime.of(21, 18), times.sunset)
    }

    @Test
    fun moscowWinterSolsticeMatchesAlmanac() {
        // Moscow, 21 December: sunrise 08:58, sunset 15:57.
        val times = SolarCalculator.sunTimes(Cities.moscow, LocalDate.of(2026, 12, 21))
        assertNear(LocalTime.of(8, 58), times.sunrise)
        assertNear(LocalTime.of(15, 57), times.sunset)
    }

    @Test
    fun noonElevationFollowsLatitudeAndDeclination() {
        // At the equinox the noon sun stands at roughly 90° − latitude.
        val equinox = LocalDate.of(2026, 3, 20)
        val moscow = SolarCalculator.sunTimes(Cities.moscow, equinox).maxElevationDeg
        val almaty = SolarCalculator.sunTimes(Cities.almaty, equinox).maxElevationDeg
        assertEquals(90 - 55.76, moscow, 1.0)
        assertEquals(90 - 43.24, almaty, 1.0)
    }

    @Test
    fun sunIsInTheSouthAtNoonAndEastInTheMorning() {
        val date = LocalDate.of(2026, 10, 22)
        val noon = SolarCalculator.sunTimes(Cities.kazan, date).solarNoon
        val atNoon = SolarCalculator.position(Cities.kazan, date, noon.toSecondOfDay() / 60.0)
        assertEquals(180.0, atNoon.azimuthDeg, 2.0)
        val morning = SolarCalculator.position(Cities.kazan, date, 8.0 * 60)
        assertTrue(morning.azimuthDeg in 90.0..160.0)
    }

    @Test
    fun nightIsBelowHorizon() {
        val midnight = SolarCalculator.position(Cities.petersburg, LocalDate.of(2026, 10, 22), 0.0)
        assertTrue(midnight.elevationDeg < -20)
    }
}
