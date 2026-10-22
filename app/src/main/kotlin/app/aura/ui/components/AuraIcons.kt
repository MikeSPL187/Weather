package app.aura.ui.components

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Stroke icons from the mockups, drawn on a 24-unit grid. */
object AuraIcons {
    private fun stroke(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach { data ->
            builder.addPath(
                pathData = addPathNodes(data),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    val Home = stroke("home", "M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z")
    val Calendar = stroke("calendar", "M6 5h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z", "M8 3v4M16 3v4M3 10h18")
    val Layers = stroke("layers", "m12 3 9 5-9 5-9-5 9-5z", "m3 13 9 5 9-5")
    val Bulb = stroke("bulb", "M9 18h6M10 21h4", "M12 3a6 6 0 0 0-3.6 10.8c.7.5 1.1 1.3 1.1 2.2h5c0-.9.4-1.7 1.1-2.2A6 6 0 0 0 12 3z")
    val Pin = stroke("pin", "M12 21s-7-6.2-7-11.5A7 7 0 0 1 19 9.5C19 14.8 12 21 12 21z", "M14.5 9.5a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0")
    val Plus = stroke("plus", "M12 5v14M5 12h14")
    val Drop = stroke("drop", "M12 3s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11z")
    val Back = stroke("back", "M19 12H5M11 6l-6 6 6 6")
    val Check = stroke("check", "m5 12 5 5 9-10")
}
