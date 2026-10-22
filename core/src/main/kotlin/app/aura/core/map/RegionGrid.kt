package app.aura.core.map

import app.aura.core.model.City
import app.aura.core.model.DataSource
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class MapLayer { PRECIPITATION, CLOUDS, TEMPERATURE, WIND }

private const val KM_PER_DEGREE = 111.2

/**
 * Weather on a square grid around a city, hour by hour: what the map tab draws.
 * Row 0 is the northern edge and column 0 the western one; values are stored per hour
 * as `row * cols + col`.
 */
class RegionGrid(
    val center: City,
    val size: Int,
    val stepKm: Double,
    val times: List<LocalDateTime>,
    val precipitationMmH: List<DoubleArray>,
    val cloudCoverPercent: List<DoubleArray>,
    val temperatureC: List<DoubleArray>,
    val windSpeedMs: List<DoubleArray>,
    val windDirectionDeg: List<DoubleArray>,
    val source: DataSource,
) {
    init {
        require(size >= 2) { "Grid needs at least 2×2 points" }
        val layers = listOf(precipitationMmH, cloudCoverPercent, temperatureC, windSpeedMs, windDirectionDeg)
        require(layers.all { it.size == times.size }) { "Every layer needs one frame per hour" }
        require(layers.all { frames -> frames.all { it.size == size * size } }) { "Every frame needs $size×$size values" }
    }

    val radiusKm: Double get() = stepKm * (size - 1) / 2

    fun frames(layer: MapLayer): List<DoubleArray> = when (layer) {
        MapLayer.PRECIPITATION -> precipitationMmH
        MapLayer.CLOUDS -> cloudCoverPercent
        MapLayer.TEMPERATURE -> temperatureC
        MapLayer.WIND -> windSpeedMs
    }

    /**
     * The value at fractional grid position ([col], [row]) and fractional [hour], blended
     * between the four nearest points and the two nearest hours, so the map animates smoothly.
     */
    fun sample(frames: List<DoubleArray>, hour: Float, col: Float, row: Float): Double {
        val h0 = floor(hour).toInt().coerceIn(0, times.lastIndex)
        val h1 = (h0 + 1).coerceAtMost(times.lastIndex)
        val th = (hour - h0).coerceIn(0f, 1f)
        val a = spatial(frames[h0], col, row)
        val b = spatial(frames[h1], col, row)
        return a + (b - a) * th
    }

    private fun spatial(values: DoubleArray, col: Float, row: Float): Double {
        val c = col.coerceIn(0f, (size - 1).toFloat())
        val r = row.coerceIn(0f, (size - 1).toFloat())
        val c0 = floor(c).toInt().coerceAtMost(size - 2)
        val r0 = floor(r).toInt().coerceAtMost(size - 2)
        val tc = c - c0
        val tr = r - r0
        fun v(rr: Int, cc: Int) = values[rr * size + cc]
        val top = v(r0, c0) + (v(r0, c0 + 1) - v(r0, c0)) * tc
        val bottom = v(r0 + 1, c0) + (v(r0 + 1, c0 + 1) - v(r0 + 1, c0)) * tc
        return top + (bottom - top) * tr
    }

    /** Like [sample], but with Catmull-Rom curves between points, so overlays have no creases. */
    fun sampleSmooth(frames: List<DoubleArray>, hour: Float, col: Float, row: Float): Double {
        val h0 = floor(hour).toInt().coerceIn(0, times.lastIndex)
        val h1 = (h0 + 1).coerceAtMost(times.lastIndex)
        val th = (hour - h0).coerceIn(0f, 1f)
        val a = cubic(frames[h0], col, row)
        val b = cubic(frames[h1], col, row)
        return a + (b - a) * th
    }

    private fun cubic(values: DoubleArray, col: Float, row: Float): Double {
        val c = col.coerceIn(0f, (size - 1).toFloat())
        val r = row.coerceIn(0f, (size - 1).toFloat())
        val c1 = floor(c).toInt().coerceAtMost(size - 2)
        val r1 = floor(r).toInt().coerceAtMost(size - 2)
        val tc = (c - c1).toDouble()
        val tr = (r - r1).toDouble()
        fun v(rr: Int, cc: Int) = values[rr.coerceIn(0, size - 1) * size + cc.coerceIn(0, size - 1)]
        fun spline(p0: Double, p1: Double, p2: Double, p3: Double, t: Double) =
            p1 + 0.5 * t * (p2 - p0 + t * (2 * p0 - 5 * p1 + 4 * p2 - p3 + t * (3 * (p1 - p2) + p3 - p0)))
        val rows = DoubleArray(4) { k -> val rr = r1 - 1 + k; spline(v(rr, c1 - 1), v(rr, c1), v(rr, c1 + 1), v(rr, c1 + 2), tc) }
        return spline(rows[0], rows[1], rows[2], rows[3], tr)
    }

    /** North, south, west and east edges of the grid in degrees: where the overlay image is pinned. */
    fun bounds(): DoubleArray {
        val pts = points(center, size, stepKm)
        return doubleArrayOf(pts.first().first, pts.last().first, pts.first().second, pts.last().second)
    }

    /** Grid position of a place, or null when it lies outside the grid. */
    fun position(latitude: Double, longitude: Double): Pair<Float, Float>? {
        val mid = (size - 1) / 2.0
        val col = mid + (longitude - center.longitude) * KM_PER_DEGREE * cos(center.latitude * PI / 180) / stepKm
        val row = mid - (latitude - center.latitude) * KM_PER_DEGREE / stepKm
        if (col < -0.01 || row < -0.01 || col > size - 0.99 || row > size - 0.99) return null
        return col.toFloat() to row.toFloat()
    }

    companion object {
        /** Grid points, row by row from the north-west corner, [stepKm] apart. */
        fun points(center: City, size: Int, stepKm: Double): List<Pair<Double, Double>> {
            val mid = (size - 1) / 2.0
            val latStep = stepKm / KM_PER_DEGREE
            val lonStep = stepKm / (KM_PER_DEGREE * cos(center.latitude * PI / 180))
            return List(size * size) { i ->
                val row = i / size
                val col = i % size
                val lat = center.latitude + (mid - row) * latStep
                val lon = center.longitude + (col - mid) * lonStep
                (lat * 1000).roundToInt() / 1000.0 to (lon * 1000).roundToInt() / 1000.0
            }
        }
    }
}

/** Where the nearest precipitation is at a given hour, seen from the grid's centre. */
data class NearbyPrecipitation(val distanceKm: Int, val bearingDeg: Int)

object PrecipitationRadar {
    /** Lighter than this is not worth reporting on the map. */
    const val THRESHOLD_MM_H = 0.1

    fun nearest(grid: RegionGrid, hour: Int): NearbyPrecipitation? {
        val frame = grid.precipitationMmH[hour.coerceIn(0, grid.times.lastIndex)]
        val mid = (grid.size - 1) / 2.0
        var best: NearbyPrecipitation? = null
        for (i in frame.indices) {
            if (frame[i] < THRESHOLD_MM_H) continue
            val east = (i % grid.size - mid) * grid.stepKm
            val north = (mid - i / grid.size) * grid.stepKm
            val distance = sqrt(east * east + north * north).roundToInt()
            if (best == null || distance < best.distanceKm) {
                val bearing = ((atan2(east, north) * 180 / PI + 360) % 360).roundToInt()
                best = NearbyPrecipitation(distance, bearing)
            }
        }
        return best
    }

    /**
     * One line for the map: whether it is wet here, how far the nearest rain or snow is and
     * whether it is closing in over the next two hours.
     */
    fun describe(grid: RegionGrid, hour: Int): String {
        val now = nearest(grid, hour)
        val radius = grid.radiusKm.roundToInt()
        if (now == null) return "В радиусе $radius км без осадков"
        if (now.distanceKm < grid.stepKm * 0.5) {
            val center = grid.size * grid.size / 2
            val dry = (hour + 1..grid.times.lastIndex).firstOrNull { grid.precipitationMmH[it][center] < THRESHOLD_MM_H }
            return if (dry == null) "Осадки над вами, не стихнут до ${"%02d:00".format(grid.times.last().hour)}"
            else "Осадки над вами, стихнут через ${dry - hour} ч"
        }
        val later = nearest(grid, (hour + 2).coerceAtMost(grid.times.lastIndex))
        val trend = when {
            later == null -> ", уходят"
            later.distanceKm <= now.distanceKm - grid.stepKm * 0.5 -> ", приближаются"
            later.distanceKm >= now.distanceKm + grid.stepKm * 0.5 -> ", удаляются"
            else -> ""
        }
        return "Осадки в ${now.distanceKm} км ${towards(now.bearingDeg)}$trend"
    }

    /** "к северу", "к юго-западу" … */
    fun towards(bearingDeg: Int): String {
        val names = listOf("к северу", "к северо-востоку", "к востоку", "к юго-востоку", "к югу", "к юго-западу", "к западу", "к северо-западу")
        return names[(((bearingDeg % 360) + 360) % 360 + 22) / 45 % 8]
    }
}
