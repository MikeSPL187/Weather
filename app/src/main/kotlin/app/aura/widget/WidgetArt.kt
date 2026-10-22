package app.aura.widget

import android.graphics.Bitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import app.aura.SceneState
import app.aura.core.model.Condition
import app.aura.ui.components.drawWeatherIcon
import app.aura.ui.scene.drawStillScene
import app.aura.ui.scene.skyBrush
import kotlin.math.roundToInt

/** Widget bitmaps stay small: RemoteViews caps the memory a widget may send. */
private const val MAX_SCALE = 2f

/** How the diorama sits inside each widget shape. */
enum class WidgetShape { SMALL, WIDE, LARGE }

internal const val WIDGET_CORNER_DP = 24f

/** The 4:3.4 diorama the app draws, so its proportions match the Now screen. */
private const val SCENE_ASPECT = 390f / 330f

private fun paint(widthPx: Int, heightPx: Int, scale: Float, block: DrawScope.() -> Unit): Bitmap {
    val image = ImageBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1))
    CanvasDrawScope().draw(Density(scale), LayoutDirection.Ltr, Canvas(image), Size(widthPx.toFloat(), heightPx.toFloat()), block)
    return image.asAndroidBitmap()
}

/**
 * The widget background: the city's sky, the same lit island as the app at its real sun
 * position, a scrim behind the text, and for the large widget a glass band for the hours.
 */
internal fun backgroundBitmap(scene: SceneState?, size: DpSize, shape: WidgetShape, density: Float): Bitmap {
    val scale = density.coerceAtMost(MAX_SCALE)
    val w = (size.width.value * scale).roundToInt()
    val h = (size.height.value * scale).roundToInt()
    return paint(w, h, scale) {
        val radius = WIDGET_CORNER_DP * scale
        clipPath(Path().apply { addRoundRect(RoundRect(0f, 0f, this@paint.size.width, this@paint.size.height, CornerRadius(radius))) }) {
            drawRect(skyBrush(scene))
            val width = this.size.width
            val height = this.size.height
            val band = if (shape == WidgetShape.LARGE) height * 0.42f else 0f
            if (scene != null) {
                // The island fills the right of the widget above the hours band.
                val sceneH = when (shape) {
                    WidgetShape.SMALL -> height * 0.64f
                    WidgetShape.WIDE -> height * 1.12f
                    WidgetShape.LARGE -> (height - band) * 1.12f
                }
                val sceneW = sceneH * SCENE_ASPECT
                val left = when (shape) {
                    WidgetShape.SMALL -> width - sceneW * 0.86f
                    else -> width - sceneW * 0.92f
                }
                val top = height - band - sceneH + (if (shape == WidgetShape.SMALL) sceneH * 0.1f else sceneH * 0.04f)
                translate(left, top) { drawStillScene(scene, Size(sceneW, sceneH)) }
            }
            // Text sits on the left; darken it just enough to read on a bright sky.
            drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent), endX = width * 0.62f))
            if (band > 0) {
                drawRect(
                    Brush.verticalGradient(listOf(Color(0x000A1030), Color(0xB30A1030)), startY = height - band - 24 * scale, endY = height),
                    topLeft = Offset(0f, height - band - 24 * scale),
                    size = Size(width, band + 24 * scale),
                )
            }
        }
    }
}

internal fun iconBitmap(condition: Condition, isNight: Boolean, sizeDp: Float, density: Float): Bitmap {
    val px = (sizeDp * density.coerceAtMost(MAX_SCALE + 1)).roundToInt()
    return paint(px, px, density) { drawWeatherIcon(condition, isNight) }
}
