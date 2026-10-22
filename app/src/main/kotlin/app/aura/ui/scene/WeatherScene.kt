package app.aura.ui.scene

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.aura.SceneState
import app.aura.core.model.Condition
import app.aura.core.model.Landmark
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/**
 * «Живая диорама»: a floating island with the city's landmark in 2.5D, lit from where the sun
 * really stands. Faces turn toward or away from the light, buildings cast shadows that lengthen
 * as the sun drops, puddles form in rain and roofs whiten in snow. Drawn with Compose Canvas;
 * a Filament renderer can replace it behind the same [SceneState].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeatherScene(
    scene: SceneState,
    modifier: Modifier = Modifier,
    onLongPress: () -> Unit = {},
) {
    val reduceMotion = rememberReduceMotion()
    var time by remember { mutableFloatStateOf(0f) }
    val tilt by rememberTilt(enabled = !reduceMotion)
    if (!reduceMotion) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { time = (it - start) / 1_000_000_000f }
        }
    }
    val particles = remember { Particles(Random(42)) }
    val description = sceneDescription(scene)

    Canvas(
        modifier = modifier
            .semantics { contentDescription = description }
            .combinedClickable(onLongClickLabel = "Предпросмотр другой погоды", onLongClick = onLongPress, onClick = {}),
    ) {
        val world = World(this, scene, time, reduceMotion, tilt)
        world.draw(particles)
    }
}

/**
 * One still frame of the diorama laid out in [area] from the scope's origin, without the sky
 * behind it. Home-screen widgets paint it into a bitmap.
 */
fun DrawScope.drawStillScene(scene: SceneState, area: Size = size) {
    World(this, scene, time = 0f, still = true, tilt = Offset.Zero, area = area).draw(Particles(Random(42)))
}

@Composable
internal fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

internal fun sceneDescription(scene: SceneState): String {
    val sky = when (scene.condition) {
        Condition.CLEAR -> if (scene.isNight) "звёздное небо и луна" else "солнце в ясном небе"
        Condition.PARTLY_CLOUDY -> if (scene.isNight) "луна среди облаков" else "солнце и облака"
        Condition.CLOUDY -> "облака"
        Condition.RAIN -> "тучи, дождь и лужи"
        Condition.STORM -> "грозовые тучи, ливень и молнии"
        Condition.SNOW -> "снегопад, крыши в снегу"
        Condition.FOG -> "туман"
    }
    return "Парящий остров, $sky. Долгое нажатие показывает другую погоду"
}

/** 0 at night, 1 in full daylight, from the real solar elevation. */
fun dayFactor(elevationDeg: Double): Float {
    val t = ((elevationDeg + 6) / 14).coerceIn(0.0, 1.0).toFloat()
    return t * t * (3 - 2 * t)
}

private fun twilight(elevationDeg: Double): Float {
    val e = elevationDeg.toFloat()
    return exp(-(e / 7f) * (e / 7f)) * if (e > 0) 1f else exp(e / 3f)
}

/** Sky gradient for the whole screen behind the scene. */
fun skyBrush(scene: SceneState?): Brush {
    if (scene == null) return Brush.verticalGradient(listOf(Color(0xFF0A1030), Color(0xFF15205A)))
    val (top, bottom) = skyColors(scene)
    return Brush.verticalGradient(listOf(top, lerp(top, bottom, 0.55f), bottom))
}

fun skyColors(scene: SceneState): Pair<Color, Color> {
    val day = dayFactor(scene.sun.elevationDeg)
    val dusk = twilight(scene.sun.elevationDeg)
    var top = lerp(Color(0xFF04071A), Color(0xFF1D4ED8), day)
    var bottom = lerp(Color(0xFF1A2152), Color(0xFF6EA8F7), day)
    top = lerp(top, Color(0xFF2B3A8C), dusk * 0.5f)
    bottom = lerp(bottom, Color(0xFFF2925A), dusk * 0.8f)
    val grey = when (scene.condition) {
        Condition.RAIN, Condition.STORM -> 0.8f
        Condition.SNOW -> 0.55f
        Condition.CLOUDY, Condition.FOG -> 0.45f
        Condition.PARTLY_CLOUDY -> 0.1f
        Condition.CLEAR -> 0f
    }
    top = lerp(top, lerp(Color(0xFF0C101C), Color(0xFF2A3650), day), grey)
    bottom = lerp(bottom, lerp(Color(0xFF262C3E), Color(0xFF5E6E8C), day), grey)
    return top to bottom
}

private class Particles(random: Random) {
    val rain = List(110) { floatArrayOf(random.nextFloat(), random.nextFloat(), 0.8f + random.nextFloat() * 0.5f) }
    val snow = List(70) { floatArrayOf(random.nextFloat(), random.nextFloat(), random.nextFloat() * 6.28f) }
    val stars = List(46) { floatArrayOf(random.nextFloat(), random.nextFloat() * 0.55f, random.nextFloat() * 6.28f) }
    val splashes = List(14) { floatArrayOf(random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1, random.nextFloat()) }
}

private const val INK = 0xFF151A33

/** One frame of the diorama. Holds the derived lighting so every element shades consistently. */
private class World(
    val ds: DrawScope,
    val scene: SceneState,
    val time: Float,
    val still: Boolean,
    val tilt: Offset,
    area: Size = ds.size,
) {
    val w = area.width
    val h = area.height
    val u = w / 390f
    val cx = w / 2
    val bob = if (still) 0f else sin(time * 0.6f) * 3 * u
    val topY = h - 112 * u + bob
    val rx = 132 * u
    val ry = 34 * u

    val day = dayFactor(scene.sun.elevationDeg)
    val dusk = twilight(scene.sun.elevationDeg)
    val overcast = when (scene.condition) {
        Condition.CLEAR -> 0f
        Condition.PARTLY_CLOUDY -> 0.25f
        Condition.CLOUDY, Condition.FOG -> 0.55f
        else -> 0.85f
    }
    val snowy = scene.condition == Condition.SNOW
    val wet = scene.condition == Condition.RAIN || scene.condition == Condition.STORM

    // Where the light comes from: the sun by day, the moon by night.
    private val az = scene.sun.azimuthDeg * PI / 180
    val sunX = (cx - sin(az) * w * 0.3).toFloat()
    val sunUp = scene.sun.elevationDeg > -3
    val lightX = if (sunUp) sunX else w - sunX
    val lightFromLeft = lightX < cx
    val brightness = 0.32f + 0.68f * day
    val warm = lerp(Color.White, Color(0xFFFFB37A), dusk * 0.6f)

    fun tone(c: Color, lit: Float = 1f): Color {
        val base = lerp(Color(INK), c, (brightness * lit).coerceIn(0f, 1f))
        return lerp(base, base.multiply(warm), day * 0.5f)
    }

    fun Color.multiply(o: Color) = Color(red * o.red, green * o.green, blue * o.blue, alpha)

    val roofSnow = Color(0xFFF2F6FF)
    val windowLit = Color(0xFFFFD68A)
    val windowsOn = day < 0.55f

    /** Layers further away move less when the phone tilts, which reads as depth. */
    fun DrawScope.layer(depth: Float, block: DrawScope.() -> Unit) =
        translate(left = tilt.x * depth * 14 * u, top = tilt.y * depth * 8 * u, block = block)

    fun draw(p: Particles) = with(ds) {
        layer(-1.0f) {
            if (day < 0.5f && overcast < 0.6f) stars(p)
            celestial()
        }
        layer(-0.6f) { clouds(front = false) }
        layer(0.15f) {
            island()
            clipPath(groundClip()) { shadowsAndGround(p) }
            landmark()
        }
        layer(0.9f) { clouds(front = true) }
        when (scene.condition) {
            Condition.RAIN -> rain(p, heavy = false)
            Condition.STORM -> {
                rain(p, heavy = true)
                lightning()
            }
            Condition.SNOW -> snow(p)
            Condition.FOG -> fog()
            else -> Unit
        }
    }

    // --- Sky -----------------------------------------------------------------------------

    fun DrawScope.stars(p: Particles) {
        val alpha = (1 - day * 2) * (1 - overcast)
        p.stars.forEach { (x, y, phase) ->
            val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(time * 1.7f + phase))
            drawCircle(Color.White.copy(alpha = alpha * twinkle), radius = 1.5f * u, center = Offset(x * w, y * h))
        }
    }

    fun DrawScope.orb(center: Offset, radius: Float, colors: List<Color>, alpha: Float = 1f) {
        drawCircle(
            brush = Brush.radialGradient(colors, center = center + Offset(-radius * 0.3f, -radius * 0.4f), radius = radius * 1.4f),
            radius = radius,
            center = center,
            alpha = alpha,
        )
    }

    fun DrawScope.celestial() {
        val el = scene.sun.elevationDeg * PI / 180
        val horizon = topY + 10 * u
        val lift = h * 1.05f
        val minY = 62 * u
        // Rain and snow clouds hide the sun and moon completely.
        if (overcast >= 0.85f) return
        val fade = 1 - overcast * 0.82f
        if (sunUp) {
            val c = Offset(sunX, (horizon - sin(el).toFloat() * lift).coerceAtLeast(minY))
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFFFD678).copy(alpha = 0.6f * fade), Color.Transparent), center = c, radius = 150 * u),
                radius = 150 * u, center = c,
            )
            if (!still && overcast < 0.5f) {
                // Slowly turning rays.
                val rays = 14
                for (i in 0 until rays) {
                    val a = time * 0.05f + i * (2 * PI.toFloat() / rays)
                    val dir = Offset(kotlin.math.cos(a), sin(a))
                    drawLine(Color(0xFFFFE6A8).copy(alpha = 0.16f * fade), c + dir * (74 * u), c + dir * (112 * u), strokeWidth = 5 * u, cap = StrokeCap.Round)
                }
            }
            orb(c, 58 * u, listOf(Color(0xFFFFF8D6), lerp(Color(0xFFFFC94A), Color(0xFFFF8A4A), dusk), Color(0xFFE2701A)), alpha = fade)
        } else {
            val c = Offset(w - sunX, (horizon - sin(-el).toFloat() * lift).coerceAtLeast(minY))
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0x66AAB9FF).copy(alpha = 0.4f * fade), Color.Transparent), center = c, radius = 100 * u),
                radius = 100 * u, center = c,
            )
            orb(c, 42 * u, listOf(Color.White, Color(0xFFE9ECF7), Color(0xFFA8AFCB)), alpha = fade)
            listOf(Triple(-12f, -8f, 8f), Triple(10f, 10f, 6f), Triple(-2f, 18f, 4f)).forEach { (dx, dy, r) ->
                drawCircle(Color(0xFF8C96BE).copy(alpha = 0.35f * fade), radius = r * u, center = c + Offset(dx * u, dy * u))
            }
        }
    }

    fun DrawScope.cloud(origin: Offset, scale: Float, dark: Boolean) {
        val top = if (dark) Color(0xFFC3CCDF) else Color.White
        val mid = if (dark) Color(0xFF8592AD) else Color(0xFFE9EFFB)
        val base = if (dark) Color(0xFF3E4660) else Color(0xFFB4C1DE)
        val dim = 0.4f + 0.6f * day
        fun t(c: Color) = lerp(lerp(Color(0xFF1A2040), c, dim), Color(0xFFFFC4A0), dusk * 0.35f * (if (dark) 0.3f else 1f))
        // Flat, shaded base first, then lit puffs on top.
        drawOval(t(base), topLeft = origin + Offset(-70 * scale, 4 * scale), size = Size(140 * scale, 34 * scale))
        listOf(
            Triple(-50f, 8f, 28f), Triple(-22f, -10f, 36f), Triple(14f, -18f, 42f), Triple(48f, -2f, 32f), Triple(66f, 10f, 22f),
        ).forEach { (dx, dy, r) ->
            orb(origin + Offset(dx * scale, dy * scale), r * scale, listOf(t(top), t(mid), t(base)))
        }
    }

    fun DrawScope.clouds(front: Boolean) {
        val dark = wet
        val layout = when (scene.condition) {
            Condition.CLEAR -> if (front) listOf(Triple(300f, 210f, 0.75f)) else listOf(Triple(80f, 90f, 0.55f))
            Condition.PARTLY_CLOUDY -> if (front) listOf(Triple(290f, 190f, 0.95f), Triple(96f, 168f, 0.7f)) else listOf(Triple(70f, 80f, 0.75f), Triple(330f, 70f, 0.6f))
            Condition.FOG -> if (front) emptyList() else listOf(Triple(110f, 80f, 0.9f), Triple(300f, 120f, 0.75f))
            Condition.CLOUDY, Condition.SNOW -> if (front) listOf(Triple(110f, 150f, 0.95f), Triple(300f, 120f, 0.85f))
            else listOf(Triple(60f, 70f, 0.8f), Triple(330f, 60f, 0.9f), Triple(200f, 92f, 1.05f))
            else -> if (front) listOf(Triple(120f, 118f, 1.15f), Triple(290f, 98f, 1.05f))
            else listOf(Triple(50f, 60f, 1.0f), Triple(340f, 50f, 1.05f), Triple(200f, 70f, 1.25f))
        }
        layout.forEachIndexed { i, (x, y, s) ->
            val drift = if (still) 0f else sin(time * (0.1f + i * 0.04f) + i * 1.7f) * 16f
            cloud(Offset((x + drift) * u, y * u), s * u, dark)
        }
    }

    // --- Island --------------------------------------------------------------------------

    fun groundClip(): Path = Path().apply { addOval(Rect(cx - rx, topY - ry, cx + rx, topY + ry)) }

    fun DrawScope.island() {
        // Rock underside: a lit side and a shaded side, split by the light direction.
        val rock = Path().apply {
            moveTo(cx - rx * 0.98f, topY + 10 * u)
            cubicTo(cx - rx * 0.7f, topY + 70 * u, cx - 30 * u, topY + 96 * u, cx + 6 * u, topY + 104 * u)
            cubicTo(cx + 40 * u, topY + 92 * u, cx + rx * 0.72f, topY + 66 * u, cx + rx * 0.98f, topY + 10 * u)
            close()
        }
        val litRock = tone(Color(0xFFA6886C))
        val darkRock = tone(Color(0xFF5E4A3E))
        drawPath(
            rock,
            Brush.horizontalGradient(
                if (lightFromLeft) listOf(litRock, darkRock) else listOf(darkRock, litRock),
                startX = cx - rx, endX = cx + rx,
            ),
        )
        drawPath(rock, Brush.verticalGradient(listOf(Color.Transparent, Color(0x66000000)), startY = topY, endY = topY + 104 * u))
        // Strata lines.
        listOf(30f, 52f).forEach { dy ->
            drawArc(Color(0x22000000), 20f, 140f, false, topLeft = Offset(cx - rx * 0.8f, topY - 20 * u + dy * u), size = Size(rx * 1.6f, 40 * u), style = Stroke(1.5f * u))
        }
        // Soil band and the grass top.
        drawOval(tone(Color(0xFF7A5A40)), topLeft = Offset(cx - rx, topY - ry + 10 * u), size = Size(rx * 2, ry * 2))
        val grass = when {
            snowy -> Color(0xFFE8EEF8)
            wet -> Color(0xFF3F7F4E)
            else -> Color(0xFF5DA86A)
        }
        drawOval(
            Brush.radialGradient(
                listOf(tone(grass, 1.15f), tone(grass, 0.85f)),
                center = Offset(if (lightFromLeft) cx - rx * 0.4f else cx + rx * 0.4f, topY - ry * 0.4f),
                radius = rx * 1.3f,
            ),
            topLeft = Offset(cx - rx, topY - ry),
            size = Size(rx * 2, ry * 2),
        )
        drawOval(Color.White.copy(alpha = 0.18f * brightness), topLeft = Offset(cx - rx, topY - ry), size = Size(rx * 2, ry * 2), style = Stroke(1.5f * u))
        // Floating pebbles.
        listOf(Triple(-170f, 40f, 9f), Triple(168f, 22f, 7f), Triple(150f, 82f, 11f)).forEachIndexed { i, (dx, dy, r) ->
            val y = topY + dy * u + if (still) 0f else sin(time * 0.8f + i * 2) * 4 * u
            orb(Offset(cx + dx * u, y), r * u, listOf(tone(Color(0xFFB4977C)), tone(Color(0xFF8E7765)), tone(Color(0xFF4E3E34))))
        }
    }

    /** Cast shadows, puddles and splashes, clipped to the grass so nothing spills off the island. */
    fun DrawScope.shadowsAndGround(p: Particles) {
        if (sunUp && scene.sun.elevationDeg > 1 && overcast < 0.7f) {
            val elev = scene.sun.elevationDeg.coerceAtLeast(4.0) * PI / 180
            val stretch = (1 / tan(elev)).toFloat().coerceAtMost(4f)
            val dir = if (lightFromLeft) 1f else -1f
            val alpha = 0.28f * (1 - overcast) * day
            footprints().forEach { (x, width, height) ->
                val len = height * stretch * 0.55f * dir
                val base = topY + 6 * u
                val shadow = Path().apply {
                    moveTo(x - width / 2, base)
                    lineTo(x + width / 2, base)
                    lineTo(x + width / 2 + len, base - height * 0.12f)
                    lineTo(x - width / 2 + len, base - height * 0.12f)
                    close()
                }
                drawPath(shadow, Color(0xFF0A1020).copy(alpha = alpha))
            }
        }
        if (wet) {
            val (skyTop, skyBottom) = skyColors(scene)
            listOf(Triple(-70f, 12f, 30f), Triple(62f, 18f, 24f), Triple(-10f, 24f, 18f), Triple(96f, 2f, 14f)).forEach { (dx, dy, r) ->
                val c = Offset(cx + dx * u, topY + dy * u)
                drawOval(
                    Brush.verticalGradient(listOf(lerp(skyBottom, Color.White, 0.25f), skyTop), startY = c.y - r * 0.3f * u, endY = c.y + r * 0.3f * u),
                    topLeft = c - Offset(r * u, r * 0.3f * u),
                    size = Size(r * 2 * u, r * 0.6f * u),
                )
            }
            if (!still) p.splashes.forEach { (sx, sy, phase) ->
                val t = (time * 1.6f + phase) % 1f
                val c = Offset(cx + sx * rx * 0.85f, topY + sy * ry * 0.7f)
                drawOval(
                    Color.White.copy(alpha = 0.5f * (1 - t)),
                    topLeft = c - Offset(6 * u * t, 2 * u * t),
                    size = Size(12 * u * t, 4 * u * t),
                    style = Stroke(1.2f * u),
                )
            }
        }
    }

    // --- 2.5D primitives -----------------------------------------------------------------

    /** A box in oblique projection: front face, side face toward depth, and a lit top. */
    fun DrawScope.box(x: Float, ground: Float, width: Float, height: Float, depth: Float, color: Color, roof: Color? = null, lit: Boolean = true) {
        val dx = depth * 0.5f * (if (lightFromLeft) 1f else -1f)
        val dy = -depth * 0.32f
        val left = x - width / 2
        val right = x + width / 2
        val top = ground - height
        val sideX = if (dx > 0) right else left
        val side = Path().apply {
            moveTo(sideX, ground); lineTo(sideX + dx, ground + dy); lineTo(sideX + dx, top + dy); lineTo(sideX, top); close()
        }
        drawPath(side, tone(color, 0.62f))
        drawRect(tone(color, 0.92f), topLeft = Offset(left, top), size = Size(width, height))
        // A soft light gradient across the front face.
        drawRect(
            Brush.horizontalGradient(
                if (lightFromLeft) listOf(Color.White.copy(alpha = 0.12f * day), Color.Transparent) else listOf(Color.Transparent, Color.White.copy(alpha = 0.12f * day)),
                startX = left, endX = right,
            ),
            topLeft = Offset(left, top), size = Size(width, height),
        )
        val topFace = Path().apply {
            moveTo(left, top); lineTo(right, top); lineTo(right + dx, top + dy); lineTo(left + dx, top + dy); close()
        }
        drawPath(topFace, if (snowy) roofSnow else tone(roof ?: color, 1.12f))
        if (lit) windows(left, top, width, height)
    }

    fun DrawScope.windows(left: Float, top: Float, width: Float, height: Float) {
        val color = if (windowsOn) windowLit else tone(Color(0xFF4A6A90), 1.05f)
        var y = top + 8 * u
        var row = 0
        while (y < top + height - 9 * u) {
            var x = left + 5 * u
            var col = 0
            while (x < left + width - 7 * u) {
                val on = !windowsOn || (row * 7 + col * 3) % 5 != 0
                if (on) drawRect(color, topLeft = Offset(x, y), size = Size(4 * u, 5 * u))
                x += 9 * u
                col++
            }
            y += 11 * u
            row++
        }
    }

    fun DrawScope.gable(x: Float, ground: Float, width: Float, height: Float, wall: Color, roof: Color) {
        box(x, ground, width, height, 14 * u, wall)
        val peak = Path().apply {
            moveTo(x - width / 2 - 3 * u, ground - height)
            lineTo(x, ground - height - width * 0.45f)
            lineTo(x + width / 2 + 3 * u, ground - height)
            close()
        }
        drawPath(peak, if (snowy) roofSnow else tone(roof))
    }

    fun DrawScope.spike(x: Float, base: Float, width: Float, height: Float, color: Color) {
        val half = width / 2
        val litLeft = Path().apply { moveTo(x - half, base); lineTo(x, base - height); lineTo(x, base); close() }
        val litRight = Path().apply { moveTo(x, base); lineTo(x, base - height); lineTo(x + half, base); close() }
        drawPath(litLeft, tone(color, if (lightFromLeft) 1.05f else 0.7f))
        drawPath(litRight, tone(color, if (lightFromLeft) 0.7f else 1.05f))
    }

    fun DrawScope.dome(x: Float, base: Float, radius: Float, color: Color) {
        val c = Offset(x, base)
        drawArc(
            Brush.radialGradient(
                listOf(tone(color, 1.25f), tone(color, 0.8f), tone(color, 0.5f)),
                center = c + Offset(if (lightFromLeft) -radius * 0.4f else radius * 0.4f, -radius * 0.6f),
                radius = radius * 1.4f,
            ),
            180f, 180f, true, topLeft = Offset(x - radius, base - radius), size = Size(radius * 2, radius * 2),
        )
        if (snowy) drawArc(roofSnow, 200f, 140f, true, topLeft = Offset(x - radius * 0.8f, base - radius), size = Size(radius * 1.6f, radius * 1.1f))
    }

    fun DrawScope.spruce(x: Float, ground: Float, height: Float) {
        drawRect(tone(Color(0xFF6B4A33)), topLeft = Offset(x - 1.5f * u, ground - 6 * u), size = Size(3 * u, 6 * u))
        spike(x, ground - 4 * u, height * 0.5f, height, Color(0xFF2F7A4F))
        if (snowy) spike(x, ground - height * 0.62f, height * 0.22f, height * 0.42f, roofSnow)
    }

    fun DrawScope.roundTree(x: Float, ground: Float, radius: Float, fruit: Boolean = false) {
        drawRect(tone(Color(0xFF6B4A33)), topLeft = Offset(x - 1.5f * u, ground - 8 * u), size = Size(3 * u, 8 * u))
        val c = Offset(x, ground - 8 * u - radius * 0.8f)
        orb(c, radius, listOf(tone(Color(0xFF7CC47A), 1.1f), tone(Color(0xFF4E9A55)), tone(Color(0xFF2E6B3B), 0.8f)))
        if (snowy) drawArc(roofSnow, 190f, 160f, true, topLeft = c - Offset(radius * 0.9f, radius), size = Size(radius * 1.8f, radius * 1.1f))
        if (fruit && !snowy) listOf(-0.4f to 0.1f, 0.35f to -0.2f, 0.1f to 0.4f).forEach { (fx, fy) ->
            drawCircle(tone(Color(0xFFD9342B)), radius = 2.2f * u, center = c + Offset(fx * radius, fy * radius))
        }
    }

    // --- Landmarks -----------------------------------------------------------------------

    /** Footprints (x, width, height) of the tall elements, so shadows match what is drawn. */
    fun footprints(): List<Triple<Float, Float, Float>> = when (scene.landmarkCity.landmark) {
        Landmark.MOSCOW_HIGHRISE -> listOf(Triple(cx, 56 * u, 150 * u), Triple(cx - 82 * u, 30 * u, 40 * u), Triple(cx + 84 * u, 34 * u, 46 * u))
        Landmark.PETERSBURG_SPIRE -> listOf(Triple(cx + 30 * u, 26 * u, 160 * u), Triple(cx - 40 * u, 110 * u, 44 * u))
        Landmark.KAZAN_MOSQUE -> listOf(Triple(cx, 90 * u, 70 * u), Triple(cx - 62 * u, 8 * u, 120 * u), Triple(cx + 62 * u, 8 * u, 120 * u))
        Landmark.ALMATY_MOUNTAINS -> listOf(Triple(cx - 10 * u, 28 * u, 96 * u), Triple(cx + 22 * u, 22 * u, 70 * u), Triple(cx + 100 * u, 6 * u, 150 * u))
        Landmark.GENERIC_TOWN -> listOf(Triple(cx - 30 * u, 44 * u, 74 * u), Triple(cx + 40 * u, 40 * u, 56 * u))
    }

    fun DrawScope.landmark() {
        val back = topY - 4 * u
        val front = topY + 16 * u
        val gold = Color(0xFFFFC94A)
        when (scene.landmarkCity.landmark) {
            Landmark.MOSCOW_HIGHRISE -> {
                val brick = Color(0xFFB0412E)
                box(cx, back - 8 * u, 220 * u, 16 * u, 6 * u, brick, lit = false)
                listOf(-96f, 0f, 96f).forEach { dx ->
                    val tall = if (dx == 0f) 44f else 30f
                    box(cx + dx * u, back - 8 * u, 18 * u, tall * u, 10 * u, brick, lit = false)
                    spike(cx + dx * u, back - 8 * u - tall * u, 22 * u, 20 * u, Color(0xFF2E6B4F))
                }
                val stone = Color(0xFFE9DCC4)
                box(cx, front, 112 * u, 42 * u, 26 * u, stone)
                box(cx, front - 42 * u, 56 * u, 62 * u, 22 * u, stone)
                box(cx, front - 104 * u, 34 * u, 30 * u, 16 * u, stone, lit = false)
                box(cx, front - 134 * u, 18 * u, 18 * u, 10 * u, stone, lit = false)
                spike(cx, front - 152 * u, 10 * u, 48 * u, gold)
                drawCircle(Color(0xFFE53935), radius = 4 * u, center = Offset(cx, front - 203 * u))
                box(cx - 96 * u, front + 4 * u, 34 * u, 34 * u, 16 * u, Color(0xFFCFD8E6))
                box(cx + 94 * u, front + 2 * u, 36 * u, 44 * u, 18 * u, Color(0xFFF2C6A0))
                spruce(cx - 122 * u, front - 6 * u, 34 * u)
                spruce(cx + 124 * u, front - 10 * u, 30 * u)
            }
            Landmark.PETERSBURG_SPIRE -> {
                val (skyTop, skyBottom) = skyColors(scene)
                drawRect(
                    Brush.verticalGradient(listOf(lerp(skyBottom, Color.White, 0.2f), skyTop)),
                    topLeft = Offset(cx - rx, topY + 6 * u), size = Size(rx * 2, 9 * u),
                )
                val sand = Color(0xFFF2D9A8)
                box(cx - 40 * u, back, 110 * u, 42 * u, 22 * u, sand, roof = Color(0xFF5E8F7A))
                box(cx + 30 * u, back, 28 * u, 64 * u, 18 * u, sand)
                box(cx + 30 * u, back - 64 * u, 20 * u, 26 * u, 12 * u, sand, lit = false)
                box(cx + 30 * u, back - 90 * u, 12 * u, 16 * u, 8 * u, gold, lit = false)
                spike(cx + 30 * u, back - 106 * u, 6 * u, 102 * u, gold)
                gable(cx - 100 * u, front + 6 * u, 34 * u, 30 * u, Color(0xFFA8D8C0), Color(0xFF8E5B4A))
                gable(cx + 92 * u, front + 6 * u, 36 * u, 28 * u, Color(0xFFE8B4B8), Color(0xFF5E6A7A))
                gable(cx - 4 * u, front + 10 * u, 30 * u, 24 * u, Color(0xFFF2D388), Color(0xFF4F7F6A))
                roundTree(cx - 128 * u, front - 6 * u, 12 * u)
                roundTree(cx + 124 * u, front - 4 * u, 11 * u)
            }
            Landmark.KAZAN_MOSQUE -> {
                val white = Color(0xFFF4F1EA)
                val blue = Color(0xFF2E9FD0)
                box(cx, front - 4 * u, 92 * u, 40 * u, 26 * u, white)
                box(cx, front - 44 * u, 44 * u, 12 * u, 14 * u, white, lit = false)
                dome(cx, front - 56 * u, 30 * u, blue)
                spike(cx, front - 84 * u, 4 * u, 16 * u, gold)
                listOf(-62f, -40f, 40f, 62f).forEach { dx ->
                    val x = cx + dx * u
                    val base = if (kotlin.math.abs(dx) > 50) front - 2 * u else back
                    box(x, base, 7 * u, 112 * u, 5 * u, white, lit = false)
                    spike(x, base - 112 * u, 11 * u, 22 * u, blue)
                }
                val red = Color(0xFFB54A3A)
                box(cx + 106 * u, front + 4 * u, 20 * u, 40 * u, 12 * u, red, lit = false)
                box(cx + 106 * u, front - 36 * u, 14 * u, 22 * u, 10 * u, red, lit = false)
                spike(cx + 106 * u, front - 58 * u, 16 * u, 22 * u, Color(0xFF2E6B4F))
                roundTree(cx - 112 * u, front, 13 * u)
                roundTree(cx - 86 * u, front + 8 * u, 10 * u)
            }
            Landmark.ALMATY_MOUNTAINS -> {
                listOf(Triple(-70f, 110f, 120f), Triple(14f, 140f, 160f), Triple(86f, 100f, 108f)).forEach { (dx, width, height) ->
                    val x = cx + dx * u
                    spike(x, back + 6 * u, width * u, height * u, Color(0xFF7D8DA8))
                    spike(x, back + 6 * u - height * u * 0.64f, width * u * 0.36f, height * u * 0.36f, roofSnow)
                }
                box(cx - 10 * u, front, 28 * u, 96 * u, 16 * u, Color(0xFF9DB9DA))
                box(cx + 22 * u, front + 6 * u, 22 * u, 70 * u, 14 * u, Color(0xFF8FB3D9))
                box(cx + 100 * u, front - 2 * u, 5 * u, 150 * u, 4 * u, Color(0xFFE6E8EE), lit = false)
                box(cx + 100 * u, front - 108 * u, 18 * u, 4 * u, 8 * u, Color(0xFFE6E8EE), lit = false)
                if (day < 0.6f && (still || sin(time * 3f) > 0)) drawCircle(Color(0xFFFF3B30), radius = 3 * u, center = Offset(cx + 100 * u, front - 154 * u))
                gable(cx - 74 * u, front + 8 * u, 34 * u, 26 * u, Color(0xFFEADBC4), Color(0xFF9A5B3E))
                roundTree(cx - 116 * u, front, 12 * u, fruit = true)
                roundTree(cx + 60 * u, front + 10 * u, 10 * u, fruit = true)
                roundTree(cx - 40 * u, front + 12 * u, 9 * u, fruit = true)
            }
            Landmark.GENERIC_TOWN -> {
                box(cx - 30 * u, back, 44 * u, 74 * u, 20 * u, Color(0xFFDDE3EE))
                box(cx + 40 * u, back + 2 * u, 40 * u, 56 * u, 18 * u, Color(0xFFE8E2D6))
                gable(cx - 92 * u, front + 6 * u, 32 * u, 26 * u, Color(0xFFF2D388), Color(0xFF8E5B4A))
                gable(cx + 96 * u, front + 6 * u, 34 * u, 28 * u, Color(0xFFBFD3EA), Color(0xFF5E6A7A))
                gable(cx + 6 * u, front + 12 * u, 30 * u, 22 * u, Color(0xFFE8B4B8), Color(0xFF4F7F6A))
                spruce(cx - 124 * u, front - 6 * u, 32 * u)
                roundTree(cx + 128 * u, front - 6 * u, 11 * u)
                roundTree(cx - 54 * u, front + 12 * u, 9 * u)
            }
        }
    }

    // --- Precipitation -------------------------------------------------------------------

    fun DrawScope.rain(p: Particles, heavy: Boolean) {
        val slant = if (heavy) 0.25f else 0.08f
        val color = Color(0xFFC8E1FF).copy(alpha = if (heavy) 0.75f else 0.6f)
        val drops = if (heavy) p.rain.size else p.rain.size * 2 / 3
        for (i in 0 until drops) {
            val (sx, sy, speed) = p.rain[i]
            val y = ((sy + time * speed * 1.4f) % 1f) * h
            val x = ((sx * w - y * slant) % w + w) % w
            drawLine(color, Offset(x, y), Offset(x + 18 * u * slant, y + 18 * u), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
        }
    }

    fun DrawScope.lightning() {
        if (still) return
        // Offset so a still frame at t = 0 shows no flash.
        val cycle = (time + 2f) % 4.3f
        if (cycle > 0.18f) return
        val glow = 1 - cycle / 0.18f
        val random = Random(((time + 2f) / 4.3f).toInt())
        val strikeX = w * (0.25f + random.nextFloat() * 0.5f)
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.45f * glow), Color.Transparent), center = Offset(strikeX, 90 * u), radius = w * 0.6f),
            radius = w * 0.6f, center = Offset(strikeX, 90 * u),
        )
        val bolt = Path().apply {
            var x = strikeX
            var y = 80 * u
            moveTo(x, y)
            repeat(6) {
                x += (random.nextFloat() - 0.5f) * 30 * u
                y += 22 * u
                lineTo(x, y)
            }
        }
        drawPath(bolt, Color(0xFFEAF2FF), style = Stroke(3 * u, cap = StrokeCap.Round))
    }

    fun DrawScope.snow(p: Particles) {
        p.snow.forEach { (sx, sy, phase) ->
            val y = ((sy + time * 0.12f) % 1f) * h
            val x = sx * w + sin(time * 0.9f + phase) * 10 * u
            drawCircle(Color.White.copy(alpha = 0.9f), radius = 3.2f * u, center = Offset(x, y))
        }
    }

    fun DrawScope.fog() {
        listOf(0.42f, 0.6f, 0.78f).forEachIndexed { i, f ->
            val y = h * f
            val drift = if (still) 0f else sin(time * 0.2f + i) * 20 * u
            translate(left = drift) {
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.3f), Color.Transparent), startY = y - 34 * u, endY = y + 34 * u),
                    topLeft = Offset(-40 * u, y - 34 * u),
                    size = Size(w + 80 * u, 68 * u),
                )
            }
        }
    }
}
