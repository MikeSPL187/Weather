package app.aura.core.map

import app.aura.core.data.GRID_HOURS
import app.aura.core.data.GRID_SIZE
import app.aura.core.data.GRID_STEP_KM
import app.aura.core.model.City
import app.aura.core.model.DataSource
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A deterministic weather front for screenshots and offline demos: a band of rain from the
 * south-west to the north-east drifting east at 30 km/h, with a heavier cell inside it.
 * The map marks it as an example whenever it is shown.
 */
object SampleRegionGrid {

    /** [overhead] puts the band over the city now; otherwise it is still 100 km to the west. */
    fun generate(
        city: City,
        now: LocalDateTime,
        overhead: Boolean,
        size: Int = GRID_SIZE,
        stepKm: Double = GRID_STEP_KM,
        hours: Int = GRID_HOURS,
    ): RegionGrid {
        val start = now.withMinute(0).withSecond(0).withNano(0)
        val times = List(hours) { start.plusHours(it.toLong()) }
        val mid = (size - 1) / 2.0
        // Unit vector across the band (pointing east-south-east) and along it.
        val across = Pair(cos(PI / 8), -sin(PI / 8))
        val along = Pair(-across.second, across.first)
        val startKm = if (overhead) -10.0 else -100.0

        fun frame(hour: Int, value: (eastKm: Double, northKm: Double, front: Double, core: Double) -> Double) =
            DoubleArray(size * size) { i ->
                val east = (i % size - mid) * stepKm
                val north = (mid - i / size) * stepKm
                val position = startKm + 30.0 * hour
                val offset = east * across.first + north * across.second - position
                val front = exp(-(offset / 34) * (offset / 34))
                val run = east * along.first + north * along.second - (20.0 - hour * 6)
                val core = front * exp(-(run / 50) * (run / 50))
                value(east, north, front, core)
            }

        val precipitation = times.indices.map { h -> frame(h) { _, _, front, core -> max(0.0, front * 1.6 + core * 4.2 - 0.15) } }
        val clouds = times.indices.map { h -> frame(h) { _, _, front, _ -> (6 + 94 * sqrt(front)).coerceIn(0.0, 100.0) } }
        val temperature = times.indices.map { h ->
            val hourOfDay = times[h].hour
            val diurnal = 3.5 * cos(2 * PI * (hourOfDay - 15) / 24)
            frame(h) { _, north, front, _ -> 6.0 + diurnal - north * 0.025 - front * 2.0 }
        }
        val windSpeed = times.indices.map { h -> frame(h) { east, _, front, _ -> 4.5 + front * 5 + east * 0.004 } }
        val windDirection = times.indices.map { h -> frame(h) { _, north, front, _ -> 245 + north * 0.08 + front * 20 } }
        return RegionGrid(city, size, stepKm, times, precipitation, clouds, temperature, windSpeed, windDirection, DataSource.SAMPLE)
    }
}
