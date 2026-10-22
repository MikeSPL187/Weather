package app.aura.ui.scene

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import kotlin.random.Random

/**
 * The «Живая диорама» scene drawn with Compose Canvas: a floating platform with the
 * city's landmark, the sun or moon where it really stands, clouds and precipitation.
 * This is the MVP renderer; the Filament 3D scene replaces it behind the same state.
 */
@Composable
fun WeatherScene(
    scene: SceneState,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
) {
    val reduceMotion = rememberReduceMotion()
    var time by remember { mutableFloatStateOf(0f) }
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
            .clickable(onClickLabel = "Показать другую погоду", onClick = onTap),
    ) {
        val u = size.width / 390f
        val horizonY = size.height - 70 * u
        val day = dayFactor(scene.sun.elevationDeg)
        val overcast = when (scene.condition) {
            Condition.CLEAR -> 0f
            Condition.CLOUDY, Condition.FOG -> 0.5f
            else -> 0.85f
        }

        if (day < 0.5f && overcast < 0.6f) drawStars(particles, time, (1 - day * 2) * (1 - overcast))
        drawSunAndMoon(scene, horizonY, u, overcast)
        drawClouds(scene.condition, time, u, day, front = false)
        drawPlatform(u)
        drawLandmark(scene.landmarkCity.landmark, u, day, scene.condition == Condition.SNOW)
        drawClouds(scene.condition, time, u, day, front = true)
        when (scene.condition) {
            Condition.RAIN -> drawRain(particles, time, u, heavy = false)
            Condition.STORM -> {
                drawRain(particles, time, u, heavy = true)
                if (!reduceMotion && time % 4.3f < 0.12f) drawRect(Color.White.copy(alpha = 0.35f))
            }
            Condition.SNOW -> drawSnow(particles, time, u)
            Condition.FOG -> drawFog(u)
            else -> Unit
        }
    }
}

@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

private fun sceneDescription(scene: SceneState): String {
    val sky = when (scene.condition) {
        Condition.CLEAR -> if (scene.isNight) "звёздное небо и луна" else "солнце в ясном небе"
        Condition.CLOUDY -> "облака"
        Condition.RAIN -> "тучи и дождь"
        Condition.STORM -> "грозовые тучи, дождь и молнии"
        Condition.SNOW -> "снегопад"
        Condition.FOG -> "туман"
    }
    return "Парящий остров, $sky"
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
        Condition.CLEAR -> 0f
    }
    top = lerp(top, lerp(Color(0xFF0C101C), Color(0xFF2A3650), day), grey)
    bottom = lerp(bottom, lerp(Color(0xFF262C3E), Color(0xFF5E6E8C), day), grey)
    return Brush.verticalGradient(listOf(top, lerp(top, bottom, 0.55f), bottom))
}

private class Particles(random: Random) {
    val rain = List(90) { floatArrayOf(random.nextFloat(), random.nextFloat(), 0.8f + random.nextFloat() * 0.5f) }
    val snow = List(60) { floatArrayOf(random.nextFloat(), random.nextFloat(), random.nextFloat() * 6.28f) }
    val stars = List(40) { floatArrayOf(random.nextFloat(), random.nextFloat() * 0.6f, random.nextFloat() * 6.28f) }
}

private fun DrawScope.drawStars(p: Particles, time: Float, alpha: Float) {
    p.stars.forEach { (x, y, phase) ->
        val twinkle = 0.4f + 0.6f * (0.5f + 0.5f * sin(time * 1.7f + phase))
        drawCircle(Color.White.copy(alpha = alpha * twinkle), radius = 1.6f, center = Offset(x * size.width, y * size.height))
    }
}

private fun DrawScope.orb(center: Offset, radius: Float, colors: List<Color>) {
    drawCircle(
        brush = Brush.radialGradient(colors, center = center + Offset(-radius * 0.3f, -radius * 0.4f), radius = radius * 1.4f),
        radius = radius,
        center = center,
    )
}

private fun DrawScope.drawSunAndMoon(scene: SceneState, horizonY: Float, u: Float, overcast: Float) {
    val az = scene.sun.azimuthDeg * PI / 180
    val el = scene.sun.elevationDeg * PI / 180
    val cx = size.width / 2
    val sunX = (cx - sin(az) * size.width * 0.3).toFloat()
    val lift = size.height * 1.1f
    val minY = 70 * u
    if (scene.sun.elevationDeg > -3) {
        val y = (horizonY - sin(el).toFloat() * lift).coerceAtLeast(minY)
        val center = Offset(sunX, y)
        val glowAlpha = 0.55f * (1 - overcast)
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFFFFD678).copy(alpha = glowAlpha), Color.Transparent), center = center, radius = 130 * u),
            radius = 130 * u,
            center = center,
        )
        val warm = twilight(scene.sun.elevationDeg)
        orb(center, 62 * u, listOf(Color(0xFFFFF8D6), lerp(Color(0xFFFFC94A), Color(0xFFFF8A4A), warm), Color(0xFFE2701A)))
    } else {
        val y = (horizonY - sin(-el).toFloat() * lift).coerceAtLeast(minY)
        val center = Offset(size.width - sunX, y)
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0x66AAB9FF), Color.Transparent), center = center, radius = 90 * u),
            radius = 90 * u,
            center = center,
        )
        orb(center, 46 * u, listOf(Color.White, Color(0xFFE9ECF7), Color(0xFFA8AFCB)))
    }
}

private fun DrawScope.cloud(origin: Offset, scale: Float, dark: Boolean, day: Float) {
    val light = if (dark) Color(0xFFC9D3E6) else Color.White
    val mid = if (dark) Color(0xFF8592AD) else Color(0xFFEEF3FF)
    val shadow = if (dark) Color(0xFF4B5670) else Color(0xFFBFCBE6)
    val dim = 0.45f + 0.55f * day
    val colors = listOf(light, mid, shadow).map { lerp(Color(0xFF1A2040), it, dim) }
    listOf(Triple(-48f, 10f, 30f), Triple(0f, -12f, 42f), Triple(46f, 6f, 32f)).forEach { (dx, dy, r) ->
        orb(origin + Offset(dx * scale, dy * scale), r * scale, colors)
    }
}

private fun DrawScope.drawClouds(condition: Condition, time: Float, u: Float, day: Float, front: Boolean) {
    val dark = condition == Condition.RAIN || condition == Condition.STORM
    val layout = when (condition) {
        Condition.CLEAR -> if (front) listOf(Triple(300f, 190f, 0.9f)) else emptyList()
        Condition.FOG -> if (front) emptyList() else listOf(Triple(110f, 70f, 1.0f), Triple(290f, 110f, 0.8f))
        else -> if (front) listOf(Triple(130f, 120f, 1.25f), Triple(290f, 95f, 1.1f))
        else listOf(Triple(70f, 50f, 0.9f), Triple(320f, 40f, 1.0f), Triple(200f, 70f, 1.2f))
    }
    layout.forEachIndexed { i, (x, y, s) ->
        val drift = sin(time * (0.12f + i * 0.05f) + i) * 18f
        cloud(Offset((x + drift) * u, y * u), s * u, dark, day)
    }
}

private fun DrawScope.drawPlatform(u: Float) {
    val cx = size.width / 2
    val topY = size.height - 62 * u
    val w = 250 * u
    drawOval(
        brush = Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x0AFFFFFF)), startY = topY, endY = topY + 60 * u),
        topLeft = Offset(cx - w / 2, topY + 14 * u),
        size = Size(w, 44 * u),
    )
    drawOval(
        brush = Brush.radialGradient(listOf(Color(0x6BFFFFFF), Color(0x14FFFFFF)), center = Offset(cx, topY + 14 * u), radius = w * 0.6f),
        topLeft = Offset(cx - w / 2, topY),
        size = Size(w, 44 * u),
    )
}

private fun DrawScope.block(x: Float, bottom: Float, w: Float, h: Float, color: Color) {
    drawRect(color, topLeft = Offset(x - w / 2, bottom - h), size = Size(w / 2, h))
    drawRect(lerp(color, Color(0xFF1A2040), 0.28f), topLeft = Offset(x, bottom - h), size = Size(w / 2, h))
}

private fun DrawScope.windows(x: Float, bottom: Float, w: Float, h: Float, u: Float, lit: Color) {
    var y = bottom - h + 10 * u
    while (y < bottom - 8 * u) {
        var wx = x - w / 2 + 6 * u
        while (wx < x + w / 2 - 6 * u) {
            drawRect(lit, topLeft = Offset(wx, y), size = Size(4 * u, 5 * u))
            wx += 10 * u
        }
        y += 12 * u
    }
}

private fun DrawScope.spike(x: Float, base: Float, w: Float, h: Float, color: Color) {
    val path = Path().apply {
        moveTo(x - w / 2, base)
        lineTo(x, base - h)
        lineTo(x + w / 2, base)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawLandmark(landmark: Landmark, u: Float, day: Float, snowy: Boolean) {
    val cx = size.width / 2
    val ground = size.height - 50 * u
    val lit = lerp(Color(0xFFFFD68A), Color(0xFF4A6A90), day)
    val gold = Color(0xFFFFC94A)
    val shade = 0.35f + 0.65f * day
    fun tone(c: Color) = lerp(Color(0xFF151A33), c, shade)
    val roof = if (snowy) Color(0xFFF2F6FF) else null

    when (landmark) {
        Landmark.MOSCOW_HIGHRISE -> {
            val stone = tone(Color(0xFFE9DCC4))
            block(cx, ground, 110 * u, 40 * u, stone); windows(cx, ground, 110 * u, 40 * u, u, lit)
            block(cx, ground - 40 * u, 54 * u, 60 * u, stone); windows(cx, ground - 40 * u, 54 * u, 60 * u, u, lit)
            block(cx, ground - 100 * u, 34 * u, 34 * u, stone)
            block(cx, ground - 134 * u, 18 * u, 22 * u, stone)
            spike(cx, ground - 156 * u, 10 * u, 52 * u, gold)
            drawCircle(Color(0xFFD9342B), radius = 4 * u, center = Offset(cx, ground - 210 * u))
            block(cx - 92 * u, ground, 34 * u, 30 * u, tone(Color(0xFFB0412E)))
            spike(cx - 92 * u, ground - 30 * u, 30 * u, 22 * u, tone(Color(0xFF2E6B4F)))
            block(cx + 92 * u, ground, 34 * u, 30 * u, tone(Color(0xFFB0412E)))
            spike(cx + 92 * u, ground - 30 * u, 30 * u, 22 * u, tone(Color(0xFF2E6B4F)))
        }
        Landmark.PETERSBURG_SPIRE -> {
            val sand = tone(Color(0xFFF2D9A8))
            block(cx - 30 * u, ground, 120 * u, 44 * u, sand); windows(cx - 30 * u, ground, 120 * u, 44 * u, u, lit)
            drawRect(roof ?: tone(Color(0xFF5E8F7A)), topLeft = Offset(cx - 90 * u, ground - 52 * u), size = Size(120 * u, 8 * u))
            block(cx + 46 * u, ground, 32 * u, 64 * u, sand)
            block(cx + 46 * u, ground - 64 * u, 22 * u, 30 * u, sand)
            block(cx + 46 * u, ground - 94 * u, 14 * u, 18 * u, gold)
            spike(cx + 46 * u, ground - 112 * u, 6 * u, 110 * u, gold)
            block(cx - 104 * u, ground, 30 * u, 30 * u, tone(Color(0xFFA8D8C0)))
            block(cx + 96 * u, ground, 34 * u, 28 * u, tone(Color(0xFFE8B4B8)))
        }
        Landmark.KAZAN_MOSQUE -> {
            val white = tone(Color(0xFFF4F1EA))
            val blue = tone(Color(0xFF2E9FD0))
            block(cx, ground, 90 * u, 42 * u, white); windows(cx, ground, 90 * u, 42 * u, u, lit)
            drawArc(roof ?: blue, 180f, 180f, true, topLeft = Offset(cx - 32 * u, ground - 74 * u), size = Size(64 * u, 64 * u))
            spike(cx, ground - 74 * u, 4 * u, 18 * u, gold)
            listOf(-62f, -40f, 40f, 62f).forEach { dx ->
                val x = cx + dx * u
                block(x, ground, 7 * u, 118 * u, white)
                spike(x, ground - 118 * u, 10 * u, 22 * u, roof ?: blue)
            }
        }
        Landmark.ALMATY_MOUNTAINS -> {
            val rock = tone(Color(0xFF7D8DA8))
            listOf(Triple(-62f, 92f, 110f), Triple(16f, 120f, 150f), Triple(82f, 84f, 100f)).forEach { (dx, w, h) ->
                val x = cx + dx * u
                spike(x, ground, w * u, h * u, rock)
                spike(x, ground - h * u * 0.62f, w * u * 0.38f, h * u * 0.38f, Color(0xFFF2F6FF))
            }
            block(cx + 104 * u, ground, 5 * u, 150 * u, tone(Color(0xFFE6E8EE)))
            drawRect(tone(Color(0xFFE6E8EE)), topLeft = Offset(cx + 94 * u, ground - 110 * u), size = Size(20 * u, 4 * u))
            if (day < 0.6f) drawCircle(Color(0xFFFF3B30), radius = 3 * u, center = Offset(cx + 104 * u, ground - 152 * u))
            block(cx - 18 * u, ground, 30 * u, 88 * u, tone(Color(0xFF9DB9DA))); windows(cx - 18 * u, ground, 30 * u, 88 * u, u, lit)
        }
    }
}

private fun DrawScope.drawRain(p: Particles, time: Float, u: Float, heavy: Boolean) {
    val slant = if (heavy) 0.25f else 0.08f
    val color = Color(0xFFC8E1FF).copy(alpha = if (heavy) 0.75f else 0.6f)
    val drops = if (heavy) p.rain.size else p.rain.size * 2 / 3
    for (i in 0 until drops) {
        val (sx, sy, speed) = p.rain[i]
        val y = ((sy + time * speed * 1.4f) % 1f) * size.height
        val x = ((sx * size.width - y * slant) % size.width + size.width) % size.width
        drawLine(color, Offset(x, y), Offset(x + 18 * u * slant, y + 18 * u), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawSnow(p: Particles, time: Float, u: Float) {
    p.snow.forEach { (sx, sy, phase) ->
        val y = ((sy + time * 0.12f) % 1f) * size.height
        val x = sx * size.width + sin(time * 0.9f + phase) * 10 * u
        drawCircle(Color.White.copy(alpha = 0.9f), radius = 3.2f * u, center = Offset(x, y))
    }
}

private fun DrawScope.drawFog(u: Float) {
    listOf(0.45f, 0.62f, 0.8f).forEachIndexed { i, f ->
        val y = size.height * f
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent), startY = y - 30 * u, endY = y + 30 * u),
            topLeft = Offset(0f, y - 30 * u),
            size = Size(size.width, 60 * u + i),
        )
    }
}
