package app.aura.core.map

import app.aura.core.data.Cities
import java.time.Instant
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapOverlayTest {

    private val grid = SampleRegionGrid.generate(Cities.moscow, LocalDateTime.of(2026, 10, 22, 14, 0), overhead = true)

    @Test
    fun overlayFadesAtTheBorderAndShowsRainInside() {
        val size = 96
        val pixels = MapOverlay.render(grid, MapLayer.PRECIPITATION, 0f, size)
        assertEquals(size * size, pixels.size)
        val alpha = { x: Int, y: Int -> pixels[y * size + x] ushr 24 }
        assertEquals(0, alpha(0, 0))
        assertEquals(0, alpha(size - 1, size / 2))
        // The front is overhead, so the middle is wet.
        assertTrue(alpha(size / 2, size / 2) > 80)
    }

    @Test
    fun smoothSamplingPassesThroughGridPoints() {
        val frames = grid.temperatureC
        for (row in 0 until grid.size step 3) for (col in 0 until grid.size step 3) {
            assertEquals(frames[0][row * grid.size + col], grid.sampleSmooth(frames, 0f, col.toFloat(), row.toFloat()), 1e-9)
        }
    }

    @Test
    fun boundsSpanTheGrid() {
        val (north, south, west, east) = grid.bounds().toList()
        assertTrue(north > Cities.moscow.latitude && south < Cities.moscow.latitude)
        assertTrue(west < Cities.moscow.longitude && east > Cities.moscow.longitude)
        assertEquals(grid.radiusKm * 2 / 111.2, north - south, 0.01)
    }

    @Test
    fun paletteBlendsBetweenStops() {
        val stops = listOf(0f to 0x00000000L, 10f to 0xFF0000FFL)
        val mid = MapPalette.color(stops, 5f)
        assertEquals(128.0, (mid ushr 24).toDouble(), 1.0)
        assertEquals(128.0, (mid and 0xFF).toDouble(), 1.0)
    }

    @Test
    fun parsesRainViewerIndex() {
        val body = requireNotNull(javaClass.getResource("/rainviewer/weather-maps.json")).readText()
        val frames = RainViewer.parse(body)
        assertEquals(3, frames.past.size)
        assertEquals(Instant.ofEpochSecond(1792663200), frames.past.last().time)
        assertEquals("https://tilecache.rainviewer.com/v2/radar/1792663200/256/{z}/{x}/{y}/2/1_1.png", frames.tiles(frames.past.last()))
    }
}
