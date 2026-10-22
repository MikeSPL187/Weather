package app.aura.core.scene3d

import app.aura.core.model.Condition
import app.aura.core.solar.SolarPosition

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** How much of each ingredient a weather has; the scene eases between these. */
data class WeatherMix(
    val cloud: Float,
    val dark: Float,
    val rain: Float = 0f,
    val snow: Float = 0f,
    val fog: Float = 0f,
    val storm: Float = 0f,
) {
    /** Eases [t] of the way towards [o]. */
    fun towards(o: WeatherMix, t: Float) = WeatherMix(
        cloud + (o.cloud - cloud) * t, dark + (o.dark - dark) * t, rain + (o.rain - rain) * t,
        snow + (o.snow - snow) * t, fog + (o.fog - fog) * t, storm + (o.storm - storm) * t,
    )

    companion object {
        fun of(condition: Condition): WeatherMix = when (condition) {
            Condition.CLEAR -> WeatherMix(cloud = 0.16f, dark = 0f)
            Condition.PARTLY_CLOUDY -> WeatherMix(cloud = 0.5f, dark = 0.05f)
            Condition.CLOUDY -> WeatherMix(cloud = 0.9f, dark = 0.35f)
            Condition.RAIN -> WeatherMix(cloud = 1f, dark = 0.6f, rain = 0.75f)
            Condition.STORM -> WeatherMix(cloud = 1f, dark = 0.85f, rain = 1f, storm = 1f)
            Condition.SNOW -> WeatherMix(cloud = 0.95f, dark = 0.3f, snow = 1f)
            Condition.FOG -> WeatherMix(cloud = 0.6f, dark = 0.25f, fog = 1f)
        }
    }
}

object Sky {
    /** 0 at night, 1 in full daylight, from the real solar elevation. */
    fun dayFactor(elevationDeg: Double): Float {
        val t = ((elevationDeg + 6) / 14).coerceIn(0.0, 1.0).toFloat()
        return t * t * (3 - 2 * t)
    }

    /** Peaks at sunrise and sunset. */
    fun twilight(elevationDeg: Double): Float {
        val e = elevationDeg.toFloat()
        return exp(-(e / 7f) * (e / 7f)) * if (e > 0) 1f else exp(e / 3f)
    }

    private fun mix(a: Long, b: Long, t: Float): Long {
        fun ch(v: Long, s: Int) = ((v shr s) and 0xFF).toFloat()
        fun m(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * t.coerceIn(0f, 1f)).toLong().coerceIn(0, 255)
        return (0xFFL shl 24) or (m(16) shl 16) or (m(8) shl 8) or m(0)
    }

    /** Top and bottom of the sky gradient as ARGB, for the screen background and reflections. */
    fun colors(condition: Condition, elevationDeg: Double): Pair<Long, Long> {
        val day = dayFactor(elevationDeg)
        val dusk = twilight(elevationDeg)
        var top = mix(0xFF04071A, 0xFF1D4ED8, day)
        var bottom = mix(0xFF1A2152, 0xFF6EA8F7, day)
        top = mix(top, 0xFF2B3A8C, dusk * 0.5f)
        bottom = mix(bottom, 0xFFF2925A, dusk * 0.8f)
        val grey = when (condition) {
            Condition.RAIN, Condition.STORM -> 0.8f
            Condition.SNOW -> 0.55f
            Condition.CLOUDY, Condition.FOG -> 0.45f
            Condition.PARTLY_CLOUDY -> 0.1f
            Condition.CLEAR -> 0f
        }
        top = mix(top, mix(0xFF0C101C, 0xFF2A3650, day), grey)
        bottom = mix(bottom, mix(0xFF262C3E, 0xFF5E6E8C, day), grey)
        return top to bottom
    }
}

/**
 * Everything one frame needs, as named shader uniforms. Both the Android renderer and the
 * browser preview bind this map as it is, so they draw the same picture.
 */
class SceneFrame(
    val uniforms: Map<String, FloatArray>,
    val drawSun: Boolean,
    val drawMoon: Boolean,
    val rain: Float,
    val snow: Float,
    /** Lightning ribbon triangles for this frame, or null. */
    val bolt: FloatArray? = null,
)

/** The camera's view: an orbit around the island, nudged by the phone's tilt and the viewer's drag. */
data class CameraPose(val yawDeg: Float = 0f, val pitchDeg: Float = 0f, val aspect: Float = 390f / 330f)

object Atmosphere {
    private val target = Vec3(0f, 0.45f, 0f)
    private const val DISTANCE = 14.5f
    private const val ELEVATION_DEG = 16f
    private const val FOV_DEG = 33f

    private fun v(x: Vec3) = floatArrayOf(x.x, x.y, x.z)
    private fun v(c: Rgb) = floatArrayOf(c.r, c.g, c.b)
    private fun f(x: Float) = floatArrayOf(x)

    fun camera(pose: CameraPose): Pair<Mat4, Vec3> {
        val yaw = Math.toRadians(pose.yawDeg.toDouble()).toFloat()
        val el = Math.toRadians((ELEVATION_DEG + pose.pitchDeg).toDouble()).toFloat()
        val eye = Vec3(sin(yaw) * cos(el) * DISTANCE, sin(el) * DISTANCE, cos(yaw) * cos(el) * DISTANCE) + target
        // Narrow screens see the island from a little further so it still fits.
        val fov = if (pose.aspect < 1f) FOV_DEG / pose.aspect.coerceAtLeast(0.6f) else FOV_DEG
        val proj = Mat4.perspective(fov, pose.aspect, 1f, 60f)
        return proj * Mat4.lookAt(eye, target) to eye
    }

    /**
     * Lighting, sky, weather and camera for [condition] with the sun at [sun], [time] seconds
     * into the animation.
     */
    fun frame(condition: Condition, sun: SolarPosition, time: Float, pose: CameraPose = CameraPose()): SceneFrame {
        val (top, bottom) = Sky.colors(condition, sun.elevationDeg)
        return frame(WeatherMix.of(condition), Rgb.hex(top and 0xFFFFFF), Rgb.hex(bottom and 0xFFFFFF), sun, time, pose)
    }

    /** The same with the weather and sky given directly, so the renderer can ease between weathers. */
    fun frame(w: WeatherMix, skyTop: Rgb, skyBottom: Rgb, sun: SolarPosition, time: Float, pose: CameraPose = CameraPose()): SceneFrame {
        val elevation = sun.elevationDeg
        val day = Sky.dayFactor(elevation)
        val dusk = Sky.twilight(elevation)

        // The sun crosses the sky behind the island, from left to right as its azimuth turns.
        val az = Math.toRadians(sun.azimuthDeg).toFloat()
        val el = Math.toRadians(elevation).toFloat()
        val sx = -sin(az) * 5.2f
        // Kept low enough to stay in frame at noon and midnight.
        val sunPos = Vec3(sx, (0.2f + sin(el) * 7f).coerceAtMost(3.4f), -14f)
        val moonPos = Vec3(-sx, (0.2f - sin(el) * 7f).coerceAtMost(3.0f), -14f)
        val sunUp = elevation > -2

        // The key light stays in front of the island so facades read, from the sun's side.
        val sunDir = Vec3(sx * 1.6f, max(sunPos.y, 1.4f), 6f).normalized()
        val moonDir = Vec3(-sx * 1.6f, 6f, 6f).normalized()
        val sunStrength = if (elevation > -1) (0.45f + 2.6f * day) * (1 - 0.72f * w.dark) * (1 - 0.45f * w.fog) else 0f
        val sunColor = Rgb.hex(0xFFF1D6).mix(Rgb.hex(0xFF9A5A), dusk) * sunStrength
        val moonStrength = (1 - day) * 0.32f * (1 - 0.6f * w.cloud)
        val moonColor = Rgb.hex(0x9FB4FF) * moonStrength
        val useSun = sunStrength >= moonStrength
        val lightDir = if (useSun) sunDir else moonDir
        val lightColor = if (useSun) sunColor else moonColor

        // Lightning: a short flash every few seconds in a storm, none on a still frame at t = 0.
        val cycle = (time + 2f) % 4.3f
        val flash = if (w.storm > 0 && cycle < 0.22f) (1 - cycle / 0.22f) * w.storm else 0f

        val ambientStrength = 0.34f + 0.3f * day
        val skyAmbient = skyBottom.mix(skyTop, 0.35f).mix(Rgb(0.62f, 0.66f, 0.76f), 0.45f) * ambientStrength + Rgb(1f, 1f, 1f) * (flash * 1.4f)
        val groundAmbient = Rgb.hex(0x3A3328) * (0.4f + 0.5f * day)

        // Clouds go blue-grey and dim at night, warm at sunset, leaden in a storm.
        val cloudBright = 0.12f + 0.88f * max(day, flash)
        val night = Rgb.hex(0x46507A)
        val cloudLit = Rgb(1f, 1f, 1f).mix(Rgb.hex(0xFFB07A), dusk * 0.7f * (1 - w.dark)).mix(Rgb.hex(0x3E4558), w.dark * 0.75f).mix(night, (1 - day) * 0.55f) * cloudBright
        val cloudShade = Rgb.hex(0x7D88A8).mix(Rgb.hex(0x2E3445), w.dark).mix(night, (1 - day) * 0.5f) * cloudBright

        val windowGlow = (1 - day * 1.5f + w.dark * 0.35f).coerceIn(0f, 1f)
        val lampGlow = (1 - day * 1.8f + w.dark * 0.2f).coerceIn(0f, 1f)
        val (viewProj, eye) = camera(pose)

        // Shadows from the key light, fitted around the island.
        val lightView = Mat4.lookAt(lightDir * 14f, Vec3(0f, 0.3f, 0f))
        val lightViewProj = Mat4.ortho(-4.4f, 4.4f, -4.4f, 4.4f, 2f, 30f) * lightView
        val view = Mat4.lookAt(eye, target)
        val right = Vec3(view.m[0], view.m[4], view.m[8])
        val up = Vec3(view.m[1], view.m[5], view.m[9])

        val u = linkedMapOf(
            "uViewProj" to viewProj.m,
            "uLightViewProj" to lightViewProj.m,
            "uCamPos" to v(eye),
            "uCamRight" to v(right),
            "uCamUp" to v(up),
            "uTime" to f(time),
            "uLightDir" to v(lightDir),
            "uLightColor" to v(lightColor),
            "uSkyAmb" to v(skyAmbient),
            "uGroundAmb" to v(groundAmbient),
            "uSkyTop" to v(skyTop),
            "uSkyBottom" to v(skyBottom),
            "uFogColor" to v(skyBottom.mix(Rgb(1f, 1f, 1f), 0.15f * day)),
            "uFog" to f(w.fog),
            "uWet" to f(max(w.rain, w.storm)),
            "uPuddle" to f(max(w.rain, w.storm) * (1 - w.snow)),
            "uSnow" to f(w.snow),
            "uWind" to f(0.4f + w.rain * 0.6f + w.storm * 1.5f),
            "uWindowGlow" to f(windowGlow),
            "uLampGlow" to f(lampGlow),
            "uFlash" to f(flash),
            "uDay" to f(day),
            "uCover" to f(w.cloud),
            "uCloudDrop" to f(w.dark * 0.35f),
            "uMist" to f((w.fog + w.rain * 0.3f).coerceAtMost(1f)),
            "uStars" to f(((1 - day * 2.2f) * (1 - w.cloud * 0.9f)).coerceIn(0f, 1f)),
            "uCloudScale" to f(1f + w.dark * 0.45f),
            "uCloudLit" to v(cloudLit),
            "uCloudShade" to v(cloudShade),
            "uSunPos" to v(sunPos),
            "uSunColor" to v(Rgb.hex(0xFFD27A).mix(Rgb.hex(0xFF8A4A), dusk)),
            "uSunAlpha" to f(((elevation + 4) / 8).toFloat().coerceIn(0f, 1f) * (1 - w.cloud * 0.85f) * (1 - w.dark * 0.9f) * (1 - w.fog * 0.7f)),
            "uMoonPos" to v(moonPos),
            "uMoonAlpha" to f((1 - day) * (1 - w.cloud * 0.9f)),
            "uRain" to f(w.rain),
            "uSnowFall" to f(w.snow),
            "uSlant" to f(0.08f + w.storm * 0.25f),
        )
        return SceneFrame(u, drawSun = sunUp, drawMoon = elevation < 3, rain = w.rain, snow = w.snow, bolt = Lightning.bolt(time, w.storm, right))
    }
}

/** Rain streaks or snowflakes: seed position, fall speed, rank (how many show), corner. */
class Particles(val vertices: FloatArray, val indices: IntArray) {
    companion object {
        const val STRIDE = 7

        fun build(count: Int = 700, seed: Int = 3): Particles {
            val r = Random(seed)
            val verts = FloatArray(count * 4 * STRIDE)
            val idx = IntArray(count * 6)
            var o = 0
            for (i in 0 until count) {
                val x = (r.nextFloat() - 0.5f) * 11f
                val y = r.nextFloat()
                val z = (r.nextFloat() - 0.5f) * 8f
                val speed = 0.8f + r.nextFloat() * 0.5f
                val rank = i / count.toFloat()
                for ((cx, cy) in listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)) {
                    floatArrayOf(x, y, z, speed, rank, cx, cy).copyInto(verts, o)
                    o += STRIDE
                }
                val b = i * 4
                intArrayOf(b, b + 1, b + 2, b, b + 2, b + 3).copyInto(idx, i * 6)
            }
            return Particles(verts, idx)
        }
    }
}

/** A camera-facing quad for the sun and the moon. */
val SkyQuad = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f)


/** A forked lightning bolt from the cloud base, as camera-facing ribbon triangles. */
object Lightning {
    const val STRIDE = 4 // x, y, z, across (−1…1)

    /** The bolt to draw at [time], or null between strikes. Each strike has its own shape. */
    fun bolt(time: Float, storm: Float, cameraRight: Vec3): FloatArray? {
        if (storm <= 0f) return null
        val cycle = (time + 2f) % 4.3f
        if (cycle > 0.22f) return null
        val strike = ((time + 2f) / 4.3f).toInt()
        val r = Random(strike * 7919 + 1)
        val out = ArrayList<Float>()
        fun ribbon(points: List<Vec3>, width: Float) {
            for (i in 0 until points.size - 1) {
                val a = points[i]
                val b = points[i + 1]
                val wa = cameraRight * (width * (1 - i / points.size.toFloat() * 0.5f))
                val wb = cameraRight * (width * (1 - (i + 1) / points.size.toFloat() * 0.5f))
                val quad = listOf(a - wa to -1f, a + wa to 1f, b + wb to 1f, a - wa to -1f, b + wb to 1f, b - wb to -1f)
                quad.forEach { (p, s) -> out += listOf(p.x, p.y, p.z, s) }
            }
        }
        fun path(start: Vec3, end: Vec3, steps: Int, jag: Float): List<Vec3> = List(steps + 1) { i ->
            val t = i / steps.toFloat()
            val p = start + (end - start) * t
            if (i == 0 || i == steps) p else p + Vec3((r.nextFloat() - 0.5f) * jag, 0f, (r.nextFloat() - 0.5f) * jag * 0.4f)
        }
        val x = (r.nextFloat() - 0.5f) * 4.5f
        val start = Vec3(x, 3.4f, -0.8f + r.nextFloat() * 1.2f)
        val end = Vec3(x + (r.nextFloat() - 0.5f) * 1.2f, if (kotlin.math.abs(x) < 2.2f) 0.05f else -1.6f, start.z + 0.3f)
        val main = path(start, end, 10, 0.55f)
        ribbon(main, 0.06f)
        // One fork from the upper part of the bolt.
        val from = main[3 + r.nextInt(3)]
        ribbon(path(from, from + Vec3((r.nextFloat() - 0.5f) * 1.6f, -1.1f, 0f), 5, 0.35f), 0.036f)
        return out.toFloatArray()
    }
}
