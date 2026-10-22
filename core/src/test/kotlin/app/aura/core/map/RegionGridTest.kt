package app.aura.core.map

import app.aura.core.data.Cities
import app.aura.core.data.OpenMeteoParser
import app.aura.core.data.OpenMeteoUrls
import app.aura.core.model.DataSource
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionGridTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/open-meteo/$name")) { "missing fixture $name" }.readText()

    private val grid = OpenMeteoParser.parseGrid(fixture("grid_2x2.json"), Cities.moscow, size = 2, stepKm = 40.0)
    private val now = LocalDateTime.of(2026, 10, 22, 14, 0)

    @Test
    fun parsesEveryPointAndHour() {
        assertEquals(DataSource.LIVE, grid.source)
        assertEquals(now, grid.times.first())
        assertEquals(3, grid.times.size)
        // Points run north-west, north-east, south-west, south-east.
        assertEquals(2.0, grid.precipitationMmH[0][2], 1e-9)
        assertEquals(1.2, grid.precipitationMmH[2][0], 1e-9)
        assertEquals(0.0, grid.precipitationMmH[1][3], 1e-9) // null reads as dry
        assertEquals(11.0, grid.temperatureC[0][3], 1e-9)
        assertEquals(260.0, grid.windDirectionDeg[2][0], 1e-9)
    }

    @Test
    fun blendsBetweenPointsAndHours() {
        val frames = grid.temperatureC
        assertEquals(9.0, grid.sample(frames, 0f, 0f, 0f), 1e-9)
        assertEquals((9.0 + 10.0 + 8.0 + 11.0) / 4, grid.sample(frames, 0f, 0.5f, 0.5f), 1e-9)
        assertEquals(8.75, grid.sample(frames, 0.5f, 0f, 0f), 1e-9)
        // Outside the grid and past the last hour it holds the edge value.
        assertEquals(10.0, grid.sample(frames, 9f, 5f, 5f), 1e-9)
    }

    @Test
    fun gridPointsAreRowsFromTheNorthWest() {
        val points = RegionGrid.points(Cities.moscow, size = 3, stepKm = 40.0)
        assertEquals(9, points.size)
        assertTrue(points[0].first > points[6].first)
        assertTrue(points[0].second < points[2].second)
        assertEquals(Cities.moscow.latitude, points[4].first, 0.001)
        val url = OpenMeteoUrls.grid(points, 12)
        assertTrue(url.contains("forecast_hours=12"))
        assertEquals(9, url.substringAfter("latitude=").substringBefore("&").split(",").size)
    }

    @Test
    fun placesOutsideTheGridHaveNoPosition() {
        val big = SampleRegionGrid.generate(Cities.moscow, now, overhead = false)
        val (col, row) = big.position(Cities.moscow.latitude, Cities.moscow.longitude)!!
        assertEquals(3f, col, 0.01f)
        assertEquals(3f, row, 0.01f)
        assertNull(big.position(Cities.petersburg.latitude, Cities.petersburg.longitude))
    }

    @Test
    fun describesNearestPrecipitationAndTrend() {
        val approaching = SampleRegionGrid.generate(Cities.moscow, now, overhead = false)
        val line = PrecipitationRadar.describe(approaching, 0)
        assertTrue(line, line.startsWith("Осадки в ") && line.contains("западу") && line.endsWith(", приближаются"))

        val overhead = SampleRegionGrid.generate(Cities.moscow, now, overhead = true)
        val wet = PrecipitationRadar.describe(overhead, 0)
        assertTrue(wet, Regex("Осадки над вами, стихнут через \\d+ ч").matches(wet))

        // In the 2×2 fixture the wet points at 14:00 are both in the south-west.
        val nearest = PrecipitationRadar.nearest(grid, 0)!!
        assertEquals(225, nearest.bearingDeg)
        assertEquals("к юго-западу", PrecipitationRadar.towards(nearest.bearingDeg))
    }

    @Test
    fun saysWhenTheRegionIsDry() {
        val body = fixture("grid_2x2.json").replace(Regex("\"precipitation\": \\[[^]]*]"), "\"precipitation\": [0, 0, 0]")
        val dry = OpenMeteoParser.parseGrid(body, Cities.moscow, 2, 40.0)
        assertEquals("В радиусе 20 км без осадков", PrecipitationRadar.describe(dry, 0))
    }
}
