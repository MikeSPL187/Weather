package app.aura.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aura.core.model.Condition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Small shaded weather icons in the same lighting as the scene: light from the top left,
 * soft gradients, a cloud that sits in front of the sun. Readable at 24 dp by shape alone,
 * not only by colour.
 */
@Composable
fun WeatherIcon(condition: Condition, isNight: Boolean = false, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        when (condition) {
            Condition.CLEAR -> if (isNight) moon(Offset(s * 0.5f, s * 0.5f), s * 0.36f) else sun(Offset(s * 0.5f, s * 0.5f), s * 0.24f, rays = true)
            Condition.PARTLY_CLOUDY -> {
                if (isNight) moon(Offset(s * 0.38f, s * 0.36f), s * 0.26f) else sun(Offset(s * 0.38f, s * 0.36f), s * 0.18f, rays = true)
                cloud(Offset(s * 0.58f, s * 0.68f), s * 0.36f, dark = false)
            }
            Condition.CLOUDY -> {
                cloud(Offset(s * 0.38f, s * 0.42f), s * 0.28f, dark = true, alpha = 0.85f)
                cloud(Offset(s * 0.55f, s * 0.62f), s * 0.4f, dark = false)
            }
            Condition.RAIN -> {
                cloud(Offset(s * 0.5f, s * 0.42f), s * 0.42f, dark = true)
                drops(s, count = 3)
            }
            Condition.STORM -> {
                cloud(Offset(s * 0.5f, s * 0.4f), s * 0.42f, dark = true)
                drops(s, count = 2)
                bolt(s)
            }
            Condition.SNOW -> {
                cloud(Offset(s * 0.5f, s * 0.42f), s * 0.42f, dark = false)
                flakes(s)
            }
            Condition.FOG -> {
                cloud(Offset(s * 0.5f, s * 0.38f), s * 0.38f, dark = false, alpha = 0.7f)
                fog(s)
            }
        }
    }
}

private fun DrawScope.sun(c: Offset, r: Float, rays: Boolean) {
    drawCircle(Brush.radialGradient(listOf(Color(0x66FFD678), Color.Transparent), center = c, radius = r * 2.1f), radius = r * 2.1f, center = c)
    if (rays) for (i in 0 until 8) {
        val a = i * PI / 4
        val d = Offset(cos(a).toFloat(), sin(a).toFloat())
        drawLine(Color(0xFFFFD27A), c + d * (r * 1.35f), c + d * (r * 1.75f), strokeWidth = r * 0.22f, cap = StrokeCap.Round)
    }
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFFF8D6), Color(0xFFFFC94A), Color(0xFFE8791C)), center = c + Offset(-r * 0.35f, -r * 0.4f), radius = r * 1.5f),
        radius = r, center = c,
    )
}

private fun DrawScope.moon(c: Offset, r: Float) {
    drawCircle(Brush.radialGradient(listOf(Color(0x44B9C6FF), Color.Transparent), center = c, radius = r * 1.8f), radius = r * 1.8f, center = c)
    val disc = Path().apply { addOval(Rect(c, r)) }
    val bite = Path().apply { addOval(Rect(c + Offset(r * 0.55f, -r * 0.35f), r * 0.88f)) }
    val crescent = Path().apply { op(disc, bite, PathOperation.Difference) }
    drawPath(crescent, Brush.radialGradient(listOf(Color.White, Color(0xFFE3E8F7), Color(0xFFA8B2D6)), center = c + Offset(-r * 0.4f, -r * 0.3f), radius = r * 1.5f))
}

/** A cloud as one silhouette, so overlapping puffs do not show seams; [c] is its centre, [w] its width. */
private fun DrawScope.cloud(c: Offset, w: Float, dark: Boolean, alpha: Float = 1f) {
    val h = w * 0.62f
    val left = c.x - w / 2
    val bottom = c.y + h / 2
    val shape = Path().apply {
        addRoundRect(RoundRect(Rect(left, bottom - h * 0.48f, left + w, bottom), CornerRadius(h * 0.24f)))
        addOval(Rect(Offset(left + w * 0.3f, bottom - h * 0.5f), w * 0.22f))
        addOval(Rect(Offset(left + w * 0.6f, bottom - h * 0.62f), w * 0.28f))
    }
    val top = if (dark) Color(0xFFCAD3E6) else Color.White
    val base = if (dark) Color(0xFF6D7894) else Color(0xFFC3CEE6)
    drawPath(shape, Brush.verticalGradient(listOf(top, base), startY = bottom - h * 1.1f, endY = bottom), alpha = alpha)
    drawPath(shape, Color.White.copy(alpha = 0.35f * alpha), style = Stroke(width = w * 0.025f))
}

private fun DrawScope.drops(s: Float, count: Int) {
    val color = Color(0xFF6FE3FF)
    val xs = if (count == 3) listOf(0.32f, 0.5f, 0.68f) else listOf(0.34f, 0.66f)
    xs.forEach { x ->
        drawLine(color, Offset(s * x, s * 0.72f), Offset(s * (x - 0.05f), s * 0.88f), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.flakes(s: Float) {
    listOf(0.32f to 0.78f, 0.52f to 0.88f, 0.7f to 0.78f).forEach { (x, y) ->
        drawCircle(Color.White, radius = s * 0.05f, center = Offset(s * x, s * y))
        drawCircle(Color(0x667DB7FF), radius = s * 0.05f, center = Offset(s * x, s * y), style = Stroke(s * 0.015f))
    }
}

private fun DrawScope.bolt(s: Float) {
    val bolt = Path().apply {
        moveTo(s * 0.54f, s * 0.6f)
        lineTo(s * 0.42f, s * 0.8f)
        lineTo(s * 0.52f, s * 0.8f)
        lineTo(s * 0.44f, s * 0.98f)
        lineTo(s * 0.64f, s * 0.74f)
        lineTo(s * 0.54f, s * 0.74f)
        lineTo(s * 0.6f, s * 0.6f)
        close()
    }
    drawPath(bolt, Brush.verticalGradient(listOf(Color(0xFFFFF1A8), Color(0xFFFFB547))))
}

private fun DrawScope.fog(s: Float) {
    listOf(0.7f to 0.7f, 0.82f to 0.56f, 0.94f to 0.66f).forEach { (y, width) ->
        drawLine(Color(0xCCE3E9F5), Offset(s * (0.5f - width / 2), s * y), Offset(s * (0.5f + width / 2), s * y), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
    }
}
