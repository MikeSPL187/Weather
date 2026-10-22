package app.aura.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import app.aura.core.format.Formatters
import app.aura.core.map.MapLayer
import app.aura.core.map.Places
import app.aura.core.map.RegionGrid
import app.aura.ui.scene.rememberReduceMotion
import app.aura.ui.theme.AuraColors
import app.aura.ui.theme.AuraType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Colour stops for each layer: value to colour, blended linearly between stops. */
internal val PrecipitationStops = listOf(
    0.1f to Color(0x8C5FB8FF), 1f to Color(0xBF6FE3FF), 3f to Color(0xD99C7BFF), 8f to Color(0xF2E06BFF),
)
internal val CloudStops = listOf(0f to Color(0x00FFFFFF), 40f to Color(0x59E3E9F5), 100f to Color(0xD9F4F7FF))
internal val TemperatureStops = listOf(
    -25f to Color(0xFF6A4CFF), -10f to Color(0xFF4F8EF7), 0f to Color(0xFF6FE3FF),
    10f to Color(0xFF34C77B), 20f to Color(0xFFFFC94A), 30f to Color(0xFFFF6B4A),
)
internal val WindStops = listOf(0f to Color(0x2234C7B7), 5f to Color(0x6634C7B7), 10f to Color(0x99FFC94A), 18f to Color(0xCCFF6B4A))

internal fun ramp(stops: List<Pair<Float, Color>>, value: Float): Color {
    if (value <= stops.first().first) return stops.first().second
    for (i in 1 until stops.size) {
        val (v1, c1) = stops[i]
        if (value <= v1) {
            val (v0, c0) = stops[i - 1]
            return lerp(c0, c1, (value - v0) / (v1 - v0))
        }
    }
    return stops.last().second
}

/**
 * Plane coordinates (u east, v south, both −1…1, h up) seen by a camera tilted [tiltDeg] from
 * straight down and turned [yawDeg] around the vertical, with perspective.
 */
private class Projection(size: Size, yawDeg: Float, tiltDeg: Float) {
    val cx = size.width / 2
    val cy = size.height * 0.58f
    val r = size.width * 0.45f
    private val cosY = cos(yawDeg * PI / 180).toFloat()
    private val sinY = sin(yawDeg * PI / 180).toFloat()
    private val cosT = cos(tiltDeg * PI / 180).toFloat()
    private val sinT = sin(tiltDeg * PI / 180).toFloat()
    private val distance = r * 3.4f

    fun point(u: Float, v: Float, h: Float = 0f): Offset {
        val x = (u * cosY - v * sinY) * r
        val y = (u * sinY + v * cosY) * r
        val screenY = y * cosT - h * r * sinT
        val z = y * sinT + h * r * cosT
        val s = distance / (distance - z)
        return Offset(cx + x * s, cy + screenY * s)
    }

    /** Larger is nearer the viewer; cells are drawn far to near. */
    fun depth(u: Float, v: Float): Float = u * sinY + v * cosY
}

private class Particle(val u: Float, val v: Float, val phase: Float)

private const val MESH = 40

/**
 * The region as a tilted 3D board: precipitation floats as a lit layer above the ground with
 * its shadow and falling streaks, clouds drift higher, temperature and wind lie on the board.
 * Dragging sideways turns it.
 */
@Composable
fun RegionMap(
    grid: RegionGrid,
    layer: MapLayer,
    hour: Float,
    hereLabel: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    var time by remember { mutableFloatStateOf(0f) }
    if (!reduceMotion) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { time = (it - start) / 1_000_000_000f }
        }
    }
    var yaw by remember { mutableFloatStateOf(-14f) }
    val measurer = rememberTextMeasurer()
    val particles = remember { Random(7).let { r -> List(150) { Particle(r.nextFloat() * 2 - 1, r.nextFloat() * 2 - 1, r.nextFloat()) } } }
    val places = remember(grid) {
        Places.all.mapNotNull { place ->
            if (place.name == grid.center.name) return@mapNotNull null
            grid.position(place.latitude, place.longitude)?.let { (col, row) -> Triple(place.name, col, row) }
        }
    }

    Canvas(
        modifier = modifier
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, amount ->
                    change.consume()
                    yaw = (yaw + amount * 0.25f).coerceIn(-60f, 60f)
                }
            },
    ) {
        val p = Projection(size, yaw, tiltDeg = 56f)
        val last = (grid.size - 1).toFloat()
        fun uOf(col: Float) = col / last * 2 - 1
        fun colOf(u: Float) = (u + 1) / 2 * last

        board(p, grid.size)
        val cells = buildList {
            for (j in 0 until MESH) for (i in 0 until MESH) add(i to j)
        }.sortedBy { (i, j) -> p.depth(-1 + (2 * i + 1f) / MESH, -1 + (2 * j + 1f) / MESH) }
        val frames = grid.frames(layer)
        // Values at mesh corners, so neighbouring cells share edges and surfaces stay smooth.
        val corners = FloatArray((MESH + 1) * (MESH + 1)) { k ->
            val i = k % (MESH + 1)
            val j = k / (MESH + 1)
            grid.sample(frames, hour, colOf(-1 + 2f * i / MESH), colOf(-1 + 2f * j / MESH)).toFloat()
        }
        fun corner(i: Int, j: Int) = corners[j * (MESH + 1) + i]
        fun average(i: Int, j: Int) = (corner(i, j) + corner(i + 1, j) + corner(i, j + 1) + corner(i + 1, j + 1)) / 4

        when (layer) {
            MapLayer.TEMPERATURE -> {
                cells.forEach { (i, j) -> cell(p, i, j, ramp(TemperatureStops, average(i, j)).copy(alpha = 0.78f)) }
            }
            MapLayer.WIND -> {
                cells.forEach { (i, j) -> cell(p, i, j, ramp(WindStops, average(i, j))) }
                windStreaks(p, grid, hour, time, particles)
            }
            MapLayer.CLOUDS -> {
                val drift = 0.012f * sin(time * 0.6f)
                // Thicker cloud stands taller, so the deck has relief.
                fun lift(cover: Float) = 0.14f + 0.1f * (cover / 100f) + drift
                cells.forEach { (i, j) ->
                    val cover = average(i, j)
                    if (cover > 15) cell(p, i, j, Color(0xFF050A20).copy(alpha = 0.22f * cover / 100))
                }
                cells.forEach { (i, j) ->
                    val cover = average(i, j)
                    if (cover <= 15) return@forEach
                    val h00 = lift(corner(i, j))
                    val h10 = lift(corner(i + 1, j))
                    val h11 = lift(corner(i + 1, j + 1))
                    val h01 = lift(corner(i, j + 1))
                    val color = shade(ramp(CloudStops, cover), h00, h10, h11, h01)
                    // Thin cloud fades out instead of ending in a hard edge.
                    cell(p, i, j, color.copy(alpha = color.alpha * ((cover - 15) / 25).coerceIn(0f, 1f)), h00, h10, h11, h01)
                }
            }
            MapLayer.PRECIPITATION -> {
                fun height(mm: Float) = if (mm < 0.05f) 0.02f else 0.05f + 0.16f * sqrt((mm.coerceAtMost(10f)) / 10f)
                cells.forEach { (i, j) ->
                    val mm = average(i, j)
                    if (mm >= 0.08f) cell(p, i, j, Color(0xFF050A20).copy(alpha = (0.16f + 0.04f * ln(1 + mm)).coerceAtMost(0.32f)))
                }
                // Streaks fall from the wettest cells to the ground.
                cells.forEach { (i, j) ->
                    val mm = average(i, j)
                    if (mm >= 0.4f && (i + j * 3) % 4 == 0) {
                        val u = -1 + (2 * i + 1f) / MESH
                        val v = -1 + (2 * j + 1f) / MESH
                        val top = height(mm)
                        val fall = ((time * 1.4f + (i * 7 + j * 13) % 10 / 10f) % 1f)
                        val a = p.point(u, v, top * (1 - fall))
                        val b = p.point(u, v, (top * (1 - fall) - 0.05f).coerceAtLeast(0f))
                        drawLine(Color(0x99C8E1FF), a, b, strokeWidth = 1.4f * density, cap = StrokeCap.Round)
                    }
                }
                cells.forEach { (i, j) ->
                    val mm = average(i, j)
                    if (mm < 0.08f) return@forEach
                    val h00 = height(corner(i, j))
                    val h10 = height(corner(i + 1, j))
                    val h11 = height(corner(i + 1, j + 1))
                    val h01 = height(corner(i, j + 1))
                    val base = ramp(PrecipitationStops, mm.coerceAtLeast(0.1f))
                    val fade = ((mm - 0.08f) / 0.4f).coerceIn(0f, 1f)
                    val color = shade(base, h00, h10, h11, h01)
                    cell(p, i, j, color.copy(alpha = base.alpha * fade), h00, h10, h11, h01)
                }
            }
        }

        rings(p, grid, measurer)
        // Labels never overlap each other or the city's own pin.
        val center = p.point(0f, 0f)
        val taken = mutableListOf(Rect(center.x - 70 * density, center.y - 40 * density, center.x + 70 * density, center.y + 10 * density))
        if (layer == MapLayer.TEMPERATURE) temperatureLabels(p, grid, hour, measurer, taken)
        places.forEach { (name, col, row) -> place(p, uOf(col), uOf(row), name, measurer, taken) }
        here(p, hereLabel, time, measurer)
    }
}

private fun quad(p: Projection, u0: Float, v0: Float, u1: Float, v1: Float, h00: Float, h10: Float = h00, h11: Float = h00, h01: Float = h00): Path = Path().apply {
    val a = p.point(u0, v0, h00)
    val b = p.point(u1, v0, h10)
    val c = p.point(u1, v1, h11)
    val d = p.point(u0, v1, h01)
    moveTo(a.x, a.y)
    lineTo(b.x, b.y)
    lineTo(c.x, c.y)
    lineTo(d.x, d.y)
    close()
}

/** Slopes facing the light, from the north-west, are brighter; the far side is darker. */
private fun shade(color: Color, h00: Float, h10: Float, h11: Float, h01: Float): Color {
    val slope = ((h00 + h01) - (h10 + h11) + (h00 + h10) - (h01 + h11)) * 6f
    return lerp(color, if (slope > 0) Color.White else Color(0xFF0A1030), abs(slope).coerceAtMost(0.35f)).copy(alpha = color.alpha)
}

/** Cells share exact edges and are filled without anti-aliasing, so no seams or doubled edges show. */
private val cellPaint = Paint().apply { isAntiAlias = false }

private fun DrawScope.cell(p: Projection, i: Int, j: Int, color: Color, h00: Float = 0f, h10: Float = h00, h11: Float = h00, h01: Float = h00) {
    val u0 = -1 + 2f * i / MESH
    val v0 = -1 + 2f * j / MESH
    val u1 = -1 + 2f * (i + 1) / MESH
    val v1 = -1 + 2f * (j + 1) / MESH
    cellPaint.color = color
    drawContext.canvas.drawPath(quad(p, u0, v0, u1, v1, h00, h10, h11, h01), cellPaint)
}

/** The ground: a dark board with its edge catching the light and a line every grid step. */
private fun DrawScope.board(p: Projection, size: Int) {
    val board = quad(p, -1f, -1f, 1f, 1f, 0f)
    val base = quad(p, -1f, -1f, 1f, 1f, -0.06f)
    drawPath(base, Color(0xFF070C26))
    drawPath(board, Brush.verticalGradient(listOf(Color(0xFF1A2A5E), Color(0xFF12204A))))
    for (k in 0 until size) {
        val t = -1 + 2f * k / (size - 1)
        drawLine(Color.White.copy(alpha = 0.06f), p.point(t, -1f), p.point(t, 1f), strokeWidth = density)
        drawLine(Color.White.copy(alpha = 0.06f), p.point(-1f, t), p.point(1f, t), strokeWidth = density)
    }
    drawPath(board, Color.White.copy(alpha = 0.22f), style = Stroke(1.5f * density))
}

private fun DrawScope.label(measurer: TextMeasurer, text: String, at: Offset, style: TextStyle, background: Color? = null) {
    val layout = measurer.measure(text, style)
    val topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f)
    if (background != null) {
        drawRoundRect(
            background,
            topLeft = topLeft - Offset(8 * density, 3 * density),
            size = Size(layout.size.width + 16 * density, layout.size.height + 6 * density),
            cornerRadius = CornerRadius(layout.size.height.toFloat()),
        )
    }
    drawText(layout, topLeft = topLeft)
}

/** Distance rings around the city and the north mark. */
private fun DrawScope.rings(p: Projection, grid: RegionGrid, measurer: TextMeasurer) {
    val style = AuraType.Caption.copy(color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
    listOf(grid.stepKm, grid.stepKm * 2).forEach { km ->
        val radius = (km / grid.radiusKm).toFloat()
        val ring = Path()
        for (k in 0..64) {
            val a = k / 64f * 2 * PI.toFloat()
            val pt = p.point(cos(a) * radius, sin(a) * radius)
            if (k == 0) ring.moveTo(pt.x, pt.y) else ring.lineTo(pt.x, pt.y)
        }
        drawPath(ring, Color.White.copy(alpha = 0.28f), style = Stroke(density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * density, 5 * density))))
        label(measurer, "${km.toInt()} км", p.point(radius * 0.72f, radius * 0.72f), style)
    }
    label(measurer, "С", p.point(0f, -1.12f), AuraType.CardTitle.copy(color = AuraColors.OnSun), background = AuraColors.Sun)
}

private fun DrawScope.place(p: Projection, u: Float, v: Float, name: String, measurer: TextMeasurer, taken: MutableList<Rect>) {
    val at = p.point(u, v)
    val style = AuraType.Caption.copy(color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
    val layout = measurer.measure(name, style)
    val labelAt = at + Offset(0f, -10 * density)
    val pad = 3 * density
    val box = Rect(
        labelAt.x - layout.size.width / 2f - pad, labelAt.y - layout.size.height / 2f - pad,
        labelAt.x + layout.size.width / 2f + pad, at.y + 4 * density,
    )
    if (taken.any { it.overlaps(box) }) return
    taken += box
    drawCircle(Color.White.copy(alpha = 0.85f), radius = 2.5f * density, center = at)
    drawText(layout, topLeft = Offset(labelAt.x - layout.size.width / 2f, labelAt.y - layout.size.height / 2f))
}

/** The viewer's city: a pulsing pin with its temperature. */
private fun DrawScope.here(p: Projection, text: String, time: Float, measurer: TextMeasurer) {
    val at = p.point(0f, 0f)
    val pulse = (time * 0.7f) % 1f
    drawCircle(AuraColors.Sun.copy(alpha = 0.5f * (1 - pulse)), radius = (5 + 16 * pulse) * density, center = at, style = Stroke(2 * density))
    drawCircle(Color.White, radius = 6 * density, center = at)
    drawCircle(AuraColors.Sun, radius = 4 * density, center = at)
    label(measurer, text, at + Offset(0f, -24 * density), AuraType.CardTitle.copy(color = AuraColors.Ink), background = Color.White)
}

private fun DrawScope.temperatureLabels(p: Projection, grid: RegionGrid, hour: Float, measurer: TextMeasurer, taken: MutableList<Rect>) {
    val style = AuraType.CardTitle.copy(color = Color.White, fontSize = 12.sp)
    val last = grid.size - 1
    for (row in 1 until last step 2) for (col in 1 until last step 2) {
        val t = grid.sample(grid.temperatureC, hour, col.toFloat(), row.toFloat())
        val layout = measurer.measure(Formatters.temperature(t), style)
        val at = p.point(col * 2f / last - 1, row * 2f / last - 1)
        val box = Rect(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f, at.x + layout.size.width / 2f, at.y + layout.size.height / 2f)
        if (taken.any { it.overlaps(box) }) continue
        taken += box
        drawText(layout, topLeft = box.topLeft)
    }
}

/** Short streaks carried by the wind, fading in and out as they travel. */
private fun DrawScope.windStreaks(p: Projection, grid: RegionGrid, hour: Float, time: Float, particles: List<Particle>) {
    val last = (grid.size - 1).toFloat()
    particles.forEach { particle ->
        val col = (particle.u + 1) / 2 * last
        val row = (particle.v + 1) / 2 * last
        val speed = grid.sample(grid.windSpeedMs, hour, col, row).toFloat()
        // Wind is reported by where it comes from; streaks move the other way.
        val to = (grid.sample(grid.windDirectionDeg, hour, col, row) + 180) * PI / 180
        val du = sin(to).toFloat()
        val dv = -cos(to).toFloat()
        val age = (time * 0.35f + particle.phase) % 1f
        val travel = 0.06f + speed * 0.03f
        val u = particle.u + du * travel * age
        val v = particle.v + dv * travel * age
        if (u !in -1f..1f || v !in -1f..1f) return@forEach
        val tail = 0.025f + speed * 0.006f
        val alpha = sin(age * PI.toFloat()) * 0.9f
        drawLine(Color.White.copy(alpha = alpha), p.point(u - du * tail, v - dv * tail), p.point(u, v), strokeWidth = 1.6f * density, cap = StrokeCap.Round)
    }
}
