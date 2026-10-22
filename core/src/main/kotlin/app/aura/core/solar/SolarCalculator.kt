package app.aura.core.solar

import app.aura.core.model.City
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

data class SolarPosition(
    /** Degrees above the horizon; negative below it. */
    val elevationDeg: Double,
    /** Degrees clockwise from north. */
    val azimuthDeg: Double,
)

data class SunTimes(
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
    val solarNoon: LocalTime,
    val maxElevationDeg: Double,
)

/**
 * Sun position from the NOAA general solar position approximation.
 * Accurate to about a minute for sunrise and sunset at the latitudes Aura covers.
 */
object SolarCalculator {
    private const val RAD = PI / 180.0
    private const val HORIZON_DEG = -0.833

    fun position(city: City, date: LocalDate, minutesOfDay: Double): SolarPosition {
        val gamma = 2 * PI / 365 * (date.dayOfYear - 1 + (minutesOfDay / 60 - 12) / 24)
        val equationOfTime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
            0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
        val declination = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
        val trueSolarMinutes = minutesOfDay + equationOfTime + 4 * city.longitude - city.utcOffsetMinutes
        val hourAngle = (trueSolarMinutes / 4 - 180) * RAD
        val lat = city.latitude * RAD
        val cosZenith = (sin(lat) * sin(declination) + cos(lat) * cos(declination) * cos(hourAngle)).coerceIn(-1.0, 1.0)
        val elevation = 90 - acos(cosZenith) / RAD
        val azimuth = (180 + atan2(sin(hourAngle), cos(hourAngle) * sin(lat) - tan(declination) * cos(lat)) / RAD + 360) % 360
        return SolarPosition(elevation, azimuth)
    }

    fun sunTimes(city: City, date: LocalDate): SunTimes {
        var sunrise: Int? = null
        var sunset: Int? = null
        var noon = 720
        var best = -90.0
        var previous = position(city, date, 0.0).elevationDeg
        for (minute in 1 until 24 * 60) {
            val elevation = position(city, date, minute.toDouble()).elevationDeg
            if (sunrise == null && previous < HORIZON_DEG && elevation >= HORIZON_DEG) sunrise = minute
            if (previous >= HORIZON_DEG && elevation < HORIZON_DEG) sunset = minute
            if (elevation > best) {
                best = elevation
                noon = minute
            }
            previous = elevation
        }
        return SunTimes(sunrise?.toLocalTime(), sunset?.toLocalTime(), noon.toLocalTime(), best)
    }

    private fun Int.toLocalTime(): LocalTime = LocalTime.of(this / 60, this % 60)
}
