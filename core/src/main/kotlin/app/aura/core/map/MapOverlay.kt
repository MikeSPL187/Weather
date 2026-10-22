package app.aura.core.map

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Colours for each layer, as ARGB with straight alpha, blended linearly between stops. */
object MapPalette {
    val precipitation = listOf(
        0.05f to 0x005FB8FFL, 0.2f to 0x995FB8FFL, 1f to 0xCC3FA9F5L, 3f to 0xDD6A5CFFL, 8f to 0xEEB84DFFL, 20f to 0xF5FF4D8DL,
    )
    val clouds = listOf(10f to 0x00E8EEF8L, 50f to 0x66E8EEF8L, 100f to 0xB8F4F7FFL)
    val temperature = listOf(
        -25f to 0x996A4CFFL, -10f to 0x994F8EF7L, 0f to 0x996FE3FFL, 10f to 0x9934C77BL, 20f to 0x99FFC94AL, 30f to 0x99FF6B4AL,
    )
    val wind = listOf(0f to 0x0034C7B7L, 4f to 0x5534C7B7L, 9f to 0x88FFC94AL, 15f to 0xAAFF6B4AL, 22f to 0xCCD13BFFL)

    fun stops(layer: MapLayer) = when (layer) {
        MapLayer.PRECIPITATION -> precipitation
        MapLayer.CLOUDS -> clouds
        MapLayer.TEMPERATURE -> temperature
        MapLayer.WIND -> wind
    }

    fun color(stops: List<Pair<Float, Long>>, value: Float): Int {
        if (value <= stops.first().first) return stops.first().second.toInt()
        for (i in 1 until stops.size) {
            val (v1, c1) = stops[i]
            if (value <= v1) {
                val (v0, c0) = stops[i - 1]
                return mix(c0, c1, (value - v0) / (v1 - v0))
            }
        }
        return stops.last().second.toInt()
    }

    private fun mix(a: Long, b: Long, t: Float): Int {
        fun ch(v: Long, s: Int) = ((v shr s) and 0xFF).toFloat()
        fun m(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * t).roundToInt().coerceIn(0, 255)
        return (m(24) shl 24) or (m(16) shl 16) or (m(8) shl 8) or m(0)
    }
}

/**
 * Paints a layer of the forecast grid into a smooth image that the map pins over the region:
 * curves instead of cells, light rain fading in rather than starting at an edge, and the
 * whole field fading out towards the border of the grid.
 */
object MapOverlay {
    /** ARGB pixels with straight alpha, row 0 at the north edge. */
    fun render(grid: RegionGrid, layer: MapLayer, hour: Float, size: Int = 160): IntArray {
        val frames = grid.frames(layer)
        val stops = MapPalette.stops(layer)
        val last = (grid.size - 1).toFloat()
        val out = IntArray(size * size)
        for (y in 0 until size) {
            val row = y / (size - 1f) * last
            val fy = min(y, size - 1 - y) / (size * 0.15f)
            for (x in 0 until size) {
                val col = x / (size - 1f) * last
                val value = grid.sampleSmooth(frames, hour, col, row).toFloat()
                val color = MapPalette.color(stops, if (layer == MapLayer.PRECIPITATION) max(0f, value) else value)
                val fx = min(x, size - 1 - x) / (size * 0.15f)
                val edge = min(1f, min(fx, fy))
                val alpha = ((color ushr 24) * edge * edge * (3 - 2 * edge)).roundToInt().coerceIn(0, 255)
                out[y * size + x] = (alpha shl 24) or (color and 0xFFFFFF)
            }
        }
        return out
    }

    /** A rough intensity word for the legend and accessibility. */
    fun intensity(mmPerHour: Double): String = when {
        mmPerHour < 0.1 -> "без осадков"
        mmPerHour < 1 -> "слабые"
        mmPerHour < 4 -> "умеренные"
        mmPerHour < 10 -> "сильные"
        else -> "ливень"
    }
}
