package app.aura.core.map

import app.aura.core.data.Cities
import app.aura.core.model.City
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDateTime
import javax.imageio.ImageIO
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Writes forecast overlays and their bounds for the map preview in `tools/map-preview`.
 * Uses the sample front; runs only with MAP_PREVIEW=1.
 */
class MapPreviewExport {

    private val out = File(System.getenv("MAP_PREVIEW_DIR") ?: "build/map-preview")

    @Test
    fun export() {
        assumeTrue(System.getenv("MAP_PREVIEW") == "1")
        out.mkdirs()
        File(out, "style.json").writeText(requireNotNull(javaClass.getResource("/aura/map/style.json")).readText())
        val cases = listOf(Cities.moscow to true, Cities.petersburg to false, Cities.kazan to false)
        val entries = cases.map { (city: City, overhead) ->
            val grid = SampleRegionGrid.generate(city, LocalDateTime.of(2026, 10, 22, 14, 0), overhead)
            MapLayer.entries.forEach { layer ->
                val size = 160
                val pixels = MapOverlay.render(grid, layer, 0f, size)
                val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
                image.setRGB(0, 0, size, size, pixels, 0, size)
                ImageIO.write(image, "png", File(out, "${city.id}-${layer.name.lowercase()}.png"))
            }
            val b = grid.bounds()
            "\"${city.id}\":{\"name\":\"${city.name}\",\"lat\":${city.latitude},\"lon\":${city.longitude},\"bounds\":[${b.joinToString(",")}]}"
        }
        File(out, "cases.json").writeText(entries.joinToString(",", "{", "}"))
    }
}
