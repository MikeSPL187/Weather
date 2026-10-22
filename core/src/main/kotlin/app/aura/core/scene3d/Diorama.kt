package app.aura.core.scene3d

import app.aura.core.model.Landmark
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Soft camera-facing puffs: centre, size, coverage rank, shade, orbit speed, seed, corner. */
class CloudPuffs(val vertices: FloatArray, val indices: IntArray) {
    companion object {
        const val STRIDE = 10
    }
}

/** Everything the renderer uploads once per city. */
class DioramaModel(
    /** Island, buildings, trees, windows, lamps and floating rocks. */
    val world: Mesh,
    /** Ponds, canals and puddles, drawn after the opaque pass with reflections. */
    val water: Mesh,
    val clouds: CloudPuffs,
    /** Street lamps, up to [MAX_LAMPS], as x, y, z of each bulb; they light the ground at night. */
    val lamps: FloatArray,
) {
    companion object {
        const val MAX_LAMPS = 8
    }
}

private fun c(hex: Long) = Rgb.hex(hex)

/** A footprint on the ground, so the grass darkens softly around what stands on it. */
private class Footprint(val x: Float, val z: Float, val hw: Float, val hd: Float) {
    fun distance(px: Float, pz: Float): Float {
        val dx = max(abs(px - x) - hw, 0f)
        val dz = max(abs(pz - z) - hd, 0f)
        return sqrt(dx * dx + dz * dz)
    }
}

/**
 * Builds the floating island for a city: a grassy top with a soil rim and a faceted rock
 * underside, the city's landmark and houses with windows, trees, lamps, ponds and puddles,
 * and the clouds around it. Units are roughly 100 m; the ground is at y = 0.
 */
class DioramaBuilder(private val landmark: Landmark) {
    private val world = MeshBuilder()
    private val water = MeshBuilder()
    private val footprints = mutableListOf<Footprint>()
    private val lamps = mutableListOf<Vec3>()
    private val random = Random(landmark.ordinal * 7919 + 13)

    private val trunk = c(0x6B4A33)
    private val gold = c(0xFFC94A)

    fun build(): DioramaModel {
        when (landmark) {
            Landmark.MOSCOW_HIGHRISE -> moscow()
            Landmark.PETERSBURG_SPIRE -> petersburg()
            Landmark.KAZAN_MOSQUE -> kazan()
            Landmark.ALMATY_MOUNTAINS -> almaty()
            Landmark.GENERIC_TOWN -> town()
        }
        island()
        floatingRocks()
        val bulbs = FloatArray(DioramaModel.MAX_LAMPS * 3)
        lamps.take(DioramaModel.MAX_LAMPS).forEachIndexed { i, p -> floatArrayOf(p.x, p.y, p.z).copyInto(bulbs, i * 3) }
        return DioramaModel(world.build(), water.build(), clouds(), bulbs)
    }

    // --- Island ---------------------------------------------------------------------------

    private fun radius(theta: Float): Float =
        ISLAND_RADIUS * (1 + 0.05f * sin(3 * theta + 0.7f) + 0.035f * sin(5 * theta + 2.1f) + 0.02f * sin(9 * theta + 0.3f))

    private fun groundAo(x: Float, z: Float): Float {
        var ao = 1f
        for (f in footprints) ao *= 1 - 0.42f * exp(-f.distance(x, z) / 0.16f)
        return ao
    }

    /** Subtle patches so the grass is not one flat colour. */
    private fun grass(x: Float, z: Float): Rgb {
        val n = 0.5f + 0.25f * sin(x * 2.3f + z * 1.7f) + 0.25f * sin(x * -1.1f + z * 3.1f + 1.3f)
        return c(0x4A9A58).mix(c(0x6DB35E), n * 0.55f)
    }

    private fun island() = with(world) {
        material = Material.SOLID
        snow = 1f
        phase = 0f
        val sectors = 84
        val rings = 16
        val lip = 0.86f
        val base = vertexCount
        // Top: rings out from the centre; flat where the city stands, rounding over at the rim.
        for (k in 0..rings) {
            val f = k / rings.toFloat()
            for (i in 0..sectors) {
                val theta = (i * 2 * PI / sectors).toFloat()
                val r = radius(theta) * f
                val x = cos(theta) * r
                val z = sin(theta) * r
                val t = ((f - lip) / (1 - lip)).coerceIn(0f, 1f)
                val y = -0.12f * t * t
                val n = Vec3(cos(theta) * t * 0.8f, 1f, sin(theta) * t * 0.8f).normalized()
                vertex(Vec3(x, y, z), n, grass(x, z), groundAo(x, z))
            }
        }
        val row = sectors + 1
        for (k in 0 until rings) for (i in 0 until sectors) {
            val a = base + k * row + i
            val b = a + row
            index(a, a + 1, b, a + 1, b + 1, b)
        }

        // Rim: a band of turf, then layered soil, slightly tapering.
        snow = 0.3f
        val bands = listOf(
            Triple(-0.12f, 1.0f, c(0x3F8A4C)), Triple(-0.2f, 0.995f, c(0x5B3F2C)),
            Triple(-0.38f, 0.975f, c(0x8C6A4E)), Triple(-0.5f, 0.965f, c(0xA07E5E)), Triple(-0.66f, 0.94f, c(0x8E7262)),
        )
        for (bIndex in 0 until bands.size - 1) {
            val (y0, f0, col) = bands[bIndex]
            val (y1, f1, _) = bands[bIndex + 1]
            for (i in 0 until sectors) {
                val a0 = (i * 2 * PI / sectors).toFloat()
                val a1 = ((i + 1) * 2 * PI / sectors).toFloat()
                fun p(a: Float, y: Float, f: Float) = Vec3(cos(a) * radius(a) * f, y, sin(a) * radius(a) * f)
                val n0 = Vec3(cos(a0), 0.1f, sin(a0)).normalized()
                val n1 = Vec3(cos(a1), 0.1f, sin(a1)).normalized()
                val i0 = vertex(p(a0, y0, f0), n0, col)
                val i1 = vertex(p(a1, y0, f0), n1, col)
                val i2 = vertex(p(a1, y1, f1), n1, col, 0.9f)
                val i3 = vertex(p(a0, y1, f1), n0, col, 0.9f)
                index(i0, i1, i2, i0, i2, i3)
            }
        }

        // Underside: faceted rock narrowing to a point, darker and cooler towards the tip.
        snow = 0f
        val levels = listOf(-0.66f to 0.94f, -1.05f to 0.8f, -1.55f to 0.6f, -2.05f to 0.4f, -2.55f to 0.2f, -2.95f to 0.06f)
        val rockSectors = 28
        val jitter = Random(42)
        val pts = levels.mapIndexed { l, (y, f) ->
            List(rockSectors) { i ->
                val a = (i * 2 * PI / rockSectors).toFloat() + l * 0.11f
                val j = if (l == 0) 1f else 1 + (jitter.nextFloat() - 0.5f) * 0.22f
                Vec3(cos(a) * radius(a) * f * j, y + if (l == 0) 0f else (jitter.nextFloat() - 0.5f) * 0.16f, sin(a) * radius(a) * f * j)
            }
        }
        val tip = Vec3(0.15f, -3.25f, -0.1f)
        for (l in 0 until levels.size) {
            val shade = l / (levels.size - 1f)
            val col = c(0x9A7B62).mix(c(0x6A5A5E), shade)
            for (i in 0 until rockSectors) {
                val a = pts[l][i]
                val b = pts[l][(i + 1) % rockSectors]
                if (l == levels.size - 1) {
                    triangle(a, b, tip, col.mix(c(0x4E4A5E), 0.5f), floatArrayOf(0.8f, 0.8f, 0.6f))
                } else {
                    val d = pts[l + 1][i]
                    val e = pts[l + 1][(i + 1) % rockSectors]
                    triangle(a, b, e, col, floatArrayOf(0.95f, 0.95f, 0.85f))
                    triangle(a, e, d, col.mix(c(0x7D6656), 0.3f), floatArrayOf(0.95f, 0.85f, 0.85f))
                }
            }
        }
    }

    private fun floatingRocks() = with(world) {
        material = Material.FLOATING
        snow = 0.6f
        listOf(Triple(Vec3(3.7f, -0.9f, 0.6f), 0.26f, 0f), Triple(Vec3(-3.6f, -1.5f, -0.4f), 0.34f, 2f), Triple(Vec3(2.9f, -2.4f, 1.6f), 0.2f, 4f))
            .forEachIndexed { i, (p, r, ph) ->
                phase = ph
                gem(p, r, c(0x8E7765), squash = 0.8f, seed = i + 3)
                // A tuft of grass on top.
                gem(p + Vec3(0f, r * 0.62f, 0f), r * 0.55f, c(0x4E9A55), squash = 0.35f, seed = i + 9)
            }
        material = Material.SOLID
        phase = 0f
    }

    // --- Shared pieces --------------------------------------------------------------------

    private fun footprint(x: Float, z: Float, w: Float, d: Float) {
        footprints += Footprint(x, z, w / 2, d / 2)
    }

    /** Rows of windows on all four faces of an axis-aligned box. */
    private fun windows(x: Float, z: Float, w: Float, d: Float, y0: Float, h: Float, step: Float = 0.24f) = with(world) {
        material = Material.WINDOW
        snow = 0f
        val ww = 0.085f
        val wh = 0.11f
        val off = 0.006f
        val frame = c(0x3E5A7E)
        var y = y0 + 0.16f
        while (y + wh < y0 + h - 0.06f) {
            val nx = max(1, (w / 0.19f).toInt())
            val nz = max(1, (d / 0.19f).toInt())
            for (k in 0 until nx) {
                val px = x - w / 2 + (k + 0.5f) * w / nx
                phase = random.nextFloat()
                quad(Vec3(px - ww / 2, y, z + d / 2 + off), Vec3(px + ww / 2, y, z + d / 2 + off), Vec3(px + ww / 2, y + wh, z + d / 2 + off), Vec3(px - ww / 2, y + wh, z + d / 2 + off), frame)
                phase = random.nextFloat()
                quad(Vec3(px + ww / 2, y, z - d / 2 - off), Vec3(px - ww / 2, y, z - d / 2 - off), Vec3(px - ww / 2, y + wh, z - d / 2 - off), Vec3(px + ww / 2, y + wh, z - d / 2 - off), frame)
            }
            for (k in 0 until nz) {
                val pz = z - d / 2 + (k + 0.5f) * d / nz
                phase = random.nextFloat()
                quad(Vec3(x + w / 2 + off, y, pz + ww / 2), Vec3(x + w / 2 + off, y, pz - ww / 2), Vec3(x + w / 2 + off, y + wh, pz - ww / 2), Vec3(x + w / 2 + off, y + wh, pz + ww / 2), frame)
                phase = random.nextFloat()
                quad(Vec3(x - w / 2 - off, y, pz - ww / 2), Vec3(x - w / 2 - off, y, pz + ww / 2), Vec3(x - w / 2 - off, y + wh, pz + ww / 2), Vec3(x - w / 2 - off, y + wh, pz - ww / 2), frame)
            }
            y += step
        }
        material = Material.SOLID
        snow = 1f
        phase = 0f
    }

    /** A building with windows and either a pitched roof or a flat one with a parapet. */
    private fun block(x: Float, z: Float, w: Float, d: Float, h: Float, wall: Long, roof: Long? = null) {
        world.snow = 0.2f
        world.box(x, 0f, z, w, h, d, c(wall))
        footprint(x, z, w, d)
        windows(x, z, w, d, 0f, h)
        world.snow = 1f
        if (roof != null) {
            world.gableRoof(x, h, z, w, d, minOf(w, d) * 0.45f, c(roof))
        } else {
            val rim = c(wall).mix(c(0x5A6070), 0.35f)
            world.box(x, h, z, w + 0.03f, 0.05f, d + 0.03f, rim)
        }
    }

    private fun tree(x: Float, z: Float, kind: Int) = with(world) {
        snow = 0.2f
        frustum(x, 0f, z, 0.05f, 0.035f, 0.3f, 6, trunk)
        material = Material.FOLIAGE
        phase = random.nextFloat() * 6.28f
        snow = 0.7f
        when (kind) {
            SPRUCE -> {
                val h = 0.5f + random.nextFloat() * 0.3f
                val green = c(0x2F7A4F)
                frustum(x, 0.18f, z, 0.26f, 0f, h * 0.7f, 7, green, flat = true, twist = phase)
                frustum(x, 0.18f + h * 0.4f, z, 0.19f, 0f, h * 0.6f, 7, green.mix(c(0x3D8F5C), 0.4f), flat = true, twist = phase + 0.3f)
            }
            APPLE -> {
                val s = 0.24f + random.nextFloat() * 0.08f
                gem(Vec3(x, 0.3f + s, z), s, c(0x5DA04F), squash = 0.9f, seed = random.nextInt())
                material = Material.SOLID
                repeat(4) {
                    val a = random.nextFloat() * 6.28f
                    gem(Vec3(x + cos(a) * s * 0.9f, 0.3f + s * (0.7f + random.nextFloat() * 0.6f), z + sin(a) * s * 0.9f), 0.035f, c(0xD9342B), seed = it)
                }
            }
            else -> {
                val s = 0.22f + random.nextFloat() * 0.1f
                gem(Vec3(x, 0.3f + s, z), s, c(0x4E9A55), squash = 0.95f, seed = random.nextInt())
            }
        }
        footprint(x, z, 0.12f, 0.12f)
        material = Material.SOLID
        snow = 1f
        phase = 0f
    }

    /** A street lamp that glows after dusk. */
    private fun lamp(x: Float, z: Float) = with(world) {
        snow = 0f
        frustum(x, 0f, z, 0.018f, 0.014f, 0.36f, 6, c(0x2B2F3A))
        material = Material.LAMP
        phase = random.nextFloat()
        gem(Vec3(x, 0.38f, z), 0.035f, c(0xFFE2A8), seed = 5)
        lamps += Vec3(x, 0.38f, z)
        material = Material.SOLID
        phase = 0f
        snow = 1f
    }

    private fun path(x0: Float, z0: Float, x1: Float, z1: Float, width: Float) = with(world) {
        snow = 1f
        val dir = Vec3(x1 - x0, 0f, z1 - z0).normalized()
        val side = Vec3(-dir.z, 0f, dir.x) * (width / 2)
        val y = 0.004f
        val a = Vec3(x0, y, z0)
        val b = Vec3(x1, y, z1)
        quad(a + side, b + side, b - side, a - side, c(0xD9C9A8))
    }

    private fun pond(x: Float, z: Float, rx: Float, rz: Float) {
        world.snow = 0.5f
        world.disc(x, 0.002f, z, rx * 1.12f, rz * 1.12f, c(0x9C9488))
        water.material = Material.WATER
        water.disc(x, 0.008f, z, rx, rz, c(0x3D6FB0), segments = 32)
    }

    private fun puddle(x: Float, z: Float, r: Float) {
        water.material = Material.PUDDLE
        water.phase = random.nextFloat()
        water.disc(x, 0.006f, z, r * 1.35f, r, c(0x6F8FB8), segments = 20)
        water.phase = 0f
    }

    private fun spire(x: Float, y: Float, z: Float, h: Float, r: Float) = with(world) {
        material = Material.METAL
        snow = 0f
        frustum(x, y, z, r, 0f, h, 10, gold)
        material = Material.SOLID
        snow = 1f
    }

    private fun star(x: Float, y: Float, z: Float, r: Float) = with(world) {
        material = Material.GLOW
        snow = 0f
        gem(Vec3(x, y, z), r, c(0xE0352B), squash = 1.3f, seed = 1)
        material = Material.SOLID
        snow = 1f
    }

    // --- Cities ---------------------------------------------------------------------------

    private fun moscow() = with(world) {
        val stone = c(0xE9DCC4)
        // The Stalinist high-rise: stepped tiers, a crown and a golden spire with a star.
        snow = 0.15f
        box(-0.1f, 0f, -0.3f, 1.7f, 0.7f, 0.85f, stone)
        windows(-0.1f, -0.3f, 1.7f, 0.85f, 0f, 0.7f)
        box(-0.1f, 0.7f, -0.3f, 0.85f, 1.0f, 0.85f, stone)
        windows(-0.1f, -0.3f, 0.85f, 0.85f, 0.7f, 1.0f)
        box(-0.1f, 1.7f, -0.3f, 0.6f, 0.55f, 0.6f, stone)
        windows(-0.1f, -0.3f, 0.6f, 0.6f, 1.7f, 0.55f)
        box(-0.1f, 2.25f, -0.3f, 0.4f, 0.4f, 0.4f, stone)
        box(-0.1f, 2.65f, -0.3f, 0.26f, 0.22f, 0.26f, stone)
        footprint(-0.1f, -0.3f, 1.7f, 0.85f)
        // Corner turrets on the lower tier.
        listOf(-0.9f, 0.7f).forEach { x -> box(x, 0.7f, -0.3f, 0.2f, 0.25f, 0.85f, stone); pyramid(x, 0.95f, -0.3f, 0.2f, 0.18f, c(0x6D7F6A)) }
        spire(-0.1f, 2.87f, -0.3f, 0.95f, 0.1f)
        star(-0.1f, 3.86f, -0.3f, 0.07f)
        // Kremlin wall with three towers and green tented roofs.
        val brick = c(0xB0412E)
        snow = 0.5f
        box(0f, 0f, -1.95f, 3.6f, 0.32f, 0.14f, brick)
        footprint(0f, -1.95f, 3.6f, 0.14f)
        // Merlons along the wall.
        var mx = -1.75f
        while (mx < 1.75f) {
            box(mx, 0.32f, -1.95f, 0.07f, 0.07f, 0.14f, brick)
            mx += 0.16f
        }
        listOf(-1.5f to 0.7f, 0f to 1.05f, 1.5f to 0.7f).forEach { (x, h) ->
            box(x, 0f, -1.95f, 0.32f, h, 0.32f, brick)
            snow = 1f
            pyramid(x, h, -1.95f, 0.36f, 0.42f, c(0x2E6B4F))
            snow = 0.5f
        }
        star(0f, 1.55f, -1.95f, 0.06f)
        snow = 1f
        block(1.6f, 0.55f, 0.6f, 0.8f, 0.9f, 0xCFD8E6)
        block(-1.75f, 0.6f, 0.6f, 0.6f, 0.7f, 0xF2C6A0, 0x8E5B4A)
        block(1.05f, 1.6f, 0.6f, 0.5f, 0.6f, 0xDDE3EE, 0x5E6A7A)
        block(-0.75f, 1.65f, 0.5f, 0.5f, 0.5f, 0xE8E2D6, 0x7A4E3A)
        pond(-1.55f, 1.4f, 0.42f, 0.32f)
        path(-0.1f, 0.15f, 0.15f, 2.3f, 0.22f)
        path(-2.3f, 0.95f, 2.3f, 1.05f, 0.18f)
        listOf(-0.35f to 0.6f, 0.45f to 0.95f, -0.45f to 1.9f, 0.6f to 2.0f).forEach { (x, z) -> lamp(x, z) }
        listOf(2.1f to -0.8f, -2.2f to -0.9f, 0.2f to 2.25f, -2.3f to 0.0f, 2.2f to 1.3f, 0.65f to 1.35f, -1.35f to -1.25f).forEach { (x, z) -> tree(x, z, SPRUCE) }
        puddle(0.5f, 0.8f, 0.26f); puddle(-0.65f, 0.7f, 0.2f); puddle(1.75f, -0.45f, 0.18f); puddle(-1.1f, -1.2f, 0.2f); puddle(1.45f, 1.1f, 0.16f)
    }

    private fun petersburg() = with(world) {
        // A canal with granite banks and a bridge.
        water.material = Material.WATER
        snow = 0.6f
        val granite = c(0x8C8A8F)
        water.quad(Vec3(-2.75f, 0.012f, 1.13f), Vec3(2.75f, 0.012f, 1.13f), Vec3(2.75f, 0.012f, 0.57f), Vec3(-2.75f, 0.012f, 0.57f), c(0x3D6FB0))
        box(0f, 0f, 0.55f, 5.4f, 0.08f, 0.06f, granite)
        box(0f, 0f, 1.15f, 5.4f, 0.08f, 0.06f, granite)
        box(0.35f, 0.08f, 0.85f, 0.34f, 0.06f, 0.74f, granite)
        box(0.35f, 0.14f, 0.5f, 0.34f, 0.1f, 0.04f, c(0x2B2B30))
        box(0.35f, 0.14f, 1.2f, 0.34f, 0.1f, 0.04f, c(0x2B2B30))
        // Peter and Paul Cathedral: nave, stepped bell tower and the golden needle.
        val sand = c(0xF2D9A8)
        snow = 0.15f
        box(-0.5f, 0f, -1.25f, 1.3f, 0.6f, 0.55f, sand)
        windows(-0.5f, -1.25f, 1.3f, 0.55f, 0f, 0.6f, step = 0.3f)
        snow = 1f
        gableRoof(-0.5f, 0.6f, -1.25f, 1.3f, 0.55f, 0.2f, c(0x5E8F7A))
        snow = 0.15f
        box(0.35f, 0f, -1.25f, 0.44f, 0.75f, 0.44f, sand)
        box(0.35f, 0.75f, -1.25f, 0.34f, 0.45f, 0.34f, sand)
        material = Material.METAL
        box(0.35f, 1.2f, -1.25f, 0.24f, 0.32f, 0.24f, gold)
        material = Material.SOLID
        footprint(-0.25f, -1.25f, 1.8f, 0.55f)
        spire(0.35f, 1.52f, -1.25f, 1.75f, 0.06f)
        // Pastel houses along the embankments.
        listOf(
            floatArrayOf(-1.7f, -0.1f, 0.7f, 0.5f, 0.6f), floatArrayOf(-0.6f, 0.05f, 0.6f, 0.5f, 0.55f),
            floatArrayOf(0.6f, -0.05f, 0.6f, 0.5f, 0.65f), floatArrayOf(1.65f, -0.45f, 0.6f, 0.6f, 0.55f),
        ).zip(listOf(0xF2D388L to 0x4F7F6AL, 0xA8D8C0L to 0x8E5B4AL, 0xE8B4B8L to 0x5E6A7AL, 0xBFD3EAL to 0x6B4F3AL)).forEach { (b, col) ->
            block(b[0], b[1], b[2], b[3], b[4], col.first, col.second)
        }
        listOf(floatArrayOf(-1.0f, 1.75f, 0.6f, 0.45f, 0.45f), floatArrayOf(0.25f, 1.9f, 0.6f, 0.45f, 0.5f), floatArrayOf(1.4f, 1.65f, 0.5f, 0.45f, 0.45f))
            .zip(listOf(0xA8D8C0L to 0x5E6A7AL, 0xE8B4B8L to 0x6B4F3AL, 0xF2D388L to 0x8E5B4AL)).forEach { (b, col) ->
                block(b[0], b[1], b[2], b[3], b[4], col.first, col.second)
            }
        listOf(-1.3f to 0.42f, 0.95f to 0.4f, -0.25f to 1.4f, 1.0f to 1.38f).forEach { (x, z) -> lamp(x, z) }
        listOf(-2.1f to 0.45f, 2.2f to 0.3f, -1.9f to -1.2f, 1.5f to -1.6f, -2.0f to 1.55f, 2.1f to 1.45f).forEach { (x, z) -> tree(x, z, ROUND) }
        puddle(-0.1f, -0.55f, 0.26f); puddle(1.15f, 0.3f, 0.18f); puddle(-1.25f, 0.3f, 0.18f); puddle(-0.4f, 1.5f, 0.16f); puddle(1.0f, -0.8f, 0.18f)
    }

    private fun kazan() = with(world) {
        val white = c(0xF4F1EA)
        val turquoise = c(0x2E9FD0)
        // Kul Sharif: white base, drum, turquoise dome and four minarets.
        snow = 0.15f
        box(0f, 0f, -0.5f, 1.1f, 0.5f, 1.1f, white)
        windows(0f, -0.5f, 1.1f, 1.1f, 0f, 0.5f, step = 0.3f)
        footprint(0f, -0.5f, 1.1f, 1.1f)
        frustum(0f, 0.5f, -0.5f, 0.36f, 0.34f, 0.28f, 18, white)
        material = Material.METAL
        snow = 1f
        dome(0f, 0.78f, -0.5f, 0.4f, turquoise, heightScale = 1.15f)
        material = Material.SOLID
        spire(0f, 1.24f, -0.5f, 0.3f, 0.04f)
        listOf(-0.68f to -1.18f, 0.68f to -1.18f, -0.68f to 0.18f, 0.68f to 0.18f).forEach { (x, z) ->
            snow = 0.1f
            frustum(x, 0f, z, 0.075f, 0.055f, 1.9f, 12, white)
            frustum(x, 1.32f, z, 0.11f, 0.1f, 0.05f, 12, white)
            frustum(x, 1.72f, z, 0.1f, 0.09f, 0.05f, 12, white)
            material = Material.METAL
            snow = 0.6f
            frustum(x, 1.9f, z, 0.075f, 0f, 0.36f, 12, turquoise)
            material = Material.SOLID
            footprint(x, z, 0.16f, 0.16f)
        }
        // White Kremlin wall and the red Söyembikä tower.
        snow = 0.5f
        box(-0.2f, 0f, -2.0f, 3.2f, 0.3f, 0.13f, white)
        footprint(-0.2f, -2.0f, 3.2f, 0.13f)
        val red = c(0xB54A3A)
        snow = 0.2f
        box(1.55f, 0f, -1.35f, 0.4f, 0.5f, 0.4f, red)
        box(1.55f, 0.5f, -1.35f, 0.32f, 0.4f, 0.32f, red)
        box(1.55f, 0.9f, -1.35f, 0.24f, 0.35f, 0.24f, red)
        footprint(1.55f, -1.35f, 0.4f, 0.4f)
        snow = 1f
        frustum(1.55f, 1.25f, -1.35f, 0.18f, 0f, 0.42f, 8, c(0x2E6B4F), flat = true)
        spire(1.55f, 1.65f, -1.35f, 0.22f, 0.03f)
        block(1.65f, 0.65f, 0.6f, 0.6f, 0.6f, 0xEFE3C8, 0x5E7FA0)
        block(-1.7f, 0.45f, 0.6f, 0.6f, 0.7f, 0xE4D6BC, 0x7A4E3A)
        block(0.9f, 1.65f, 0.55f, 0.45f, 0.5f, 0xDCE4EE)
        pond(-1.2f, 1.55f, 0.55f, 0.36f)
        path(0f, 0.1f, 0f, 2.3f, 0.22f)
        listOf(-0.3f to 0.6f, 0.32f to 1.2f, -0.3f to 1.85f).forEach { (x, z) -> lamp(x, z) }
        listOf(-2.1f to -0.7f, 2.25f to -0.2f, 0.4f to 2.2f, -0.55f to 1.0f, 2.0f to 1.45f, -2.3f to 0.9f).forEach { (x, z) -> tree(x, z, ROUND) }
        puddle(0.6f, 0.9f, 0.26f); puddle(-0.8f, 0.45f, 0.2f); puddle(1.15f, -0.2f, 0.16f); puddle(-1.4f, -0.85f, 0.2f); puddle(0.35f, 1.7f, 0.16f)
    }

    private fun almaty() = with(world) {
        // Zailiysky Alatau with permanent snow.
        snow = 0f
        val rock = c(0x7D8DA8)
        listOf(floatArrayOf(-1.15f, -1.6f, 0.95f, 1.9f), floatArrayOf(0.3f, -1.85f, 1.05f, 2.5f), floatArrayOf(1.6f, -1.45f, 0.8f, 1.7f)).forEach { p ->
            frustum(p[0], 0f, p[1], p[2], 0f, p[3], 7, rock, flat = true, twist = p[0])
            val cap = p[3] * 0.38f
            frustum(p[0], p[3] - cap + 0.005f, p[1], p[2] * 0.4f, 0f, cap + 0.01f, 7, c(0xF2F6FF), flat = true, twist = p[0])
            footprint(p[0], p[1], p[2] * 1.5f, p[2] * 1.5f)
        }
        // Kok-Tobe TV tower with a blinking beacon.
        val steel = c(0xE6E8EE)
        material = Material.METAL
        frustum(1.85f, 0f, 0.3f, 0.08f, 0.035f, 2.9f, 8, steel)
        frustum(1.85f, 1.98f, 0.3f, 0.17f, 0.17f, 0.06f, 16, steel)
        frustum(1.85f, 2.28f, 0.3f, 0.12f, 0.12f, 0.05f, 16, steel)
        material = Material.BEACON
        gem(Vec3(1.85f, 2.94f, 0.3f), 0.045f, c(0xFF3B30), seed = 2)
        material = Material.SOLID
        footprint(1.85f, 0.3f, 0.16f, 0.16f)
        // Glass towers.
        material = Material.GLASS
        snow = 0.1f
        box(-0.2f, 0f, 0.2f, 0.5f, 1.7f, 0.5f, c(0x9DB9DA))
        box(0.55f, 0f, -0.3f, 0.42f, 1.25f, 0.42f, c(0x8FAFD4))
        material = Material.SOLID
        footprint(-0.2f, 0.2f, 0.5f, 0.5f)
        footprint(0.55f, -0.3f, 0.42f, 0.42f)
        snow = 1f
        block(-1.4f, 0.4f, 0.7f, 0.55f, 0.6f, 0xEADBC4, 0x9A5B3E)
        block(0.9f, 1.4f, 0.6f, 0.5f, 0.5f, 0xDDE3EE)
        block(-0.7f, 1.55f, 0.55f, 0.45f, 0.45f, 0xF2C6A0, 0x6B7F5A)
        path(-2.2f, 0.95f, 2.2f, 0.85f, 0.2f)
        listOf(-0.2f to 0.75f, 0.6f to 0.75f, 1.4f to 0.75f).forEach { (x, z) -> lamp(x, z) }
        listOf(-2.1f to 0.0f, -2.0f to 1.2f, 0.1f to 2.15f, 1.9f to 1.4f, -0.4f to 0.95f, 2.3f to -0.5f, -1.9f to -0.85f).forEach { (x, z) -> tree(x, z, APPLE) }
        puddle(0.3f, 1.1f, 0.26f); puddle(-1.0f, -0.25f, 0.2f); puddle(1.25f, 0.5f, 0.16f); puddle(-1.2f, 1.2f, 0.18f); puddle(0.9f, -1.0f, 0.16f)
    }

    private fun town() = with(world) {
        // An ordinary town: a church with an onion dome, a water tower and pastel houses.
        val wall = c(0xF1EDE4)
        snow = 0.15f
        box(-0.4f, 0f, -1.0f, 0.9f, 0.55f, 0.6f, wall)
        windows(-0.4f, -1.0f, 0.9f, 0.6f, 0f, 0.55f, step = 0.3f)
        snow = 1f
        gableRoof(-0.4f, 0.55f, -1.0f, 0.9f, 0.6f, 0.25f, c(0x4F7F6A))
        snow = 0.15f
        box(0.25f, 0f, -1.0f, 0.36f, 1.0f, 0.36f, wall)
        frustum(0.25f, 1.0f, -1.0f, 0.15f, 0.15f, 0.18f, 14, wall)
        material = Material.METAL
        snow = 1f
        dome(0.25f, 1.18f, -1.0f, 0.17f, gold, heightScale = 1.3f)
        material = Material.SOLID
        spire(0.25f, 1.4f, -1.0f, 0.24f, 0.025f)
        footprint(-0.1f, -1.0f, 1.3f, 0.6f)
        // Water tower.
        snow = 0.3f
        frustum(1.7f, 0f, -0.9f, 0.13f, 0.11f, 0.9f, 12, c(0xB0412E))
        frustum(1.7f, 0.9f, -0.9f, 0.22f, 0.22f, 0.22f, 14, c(0xC9C2B4))
        snow = 1f
        frustum(1.7f, 1.12f, -0.9f, 0.24f, 0f, 0.16f, 14, c(0x5E6A7A))
        footprint(1.7f, -0.9f, 0.26f, 0.26f)
        block(-1.65f, -0.2f, 0.65f, 0.55f, 0.5f, 0xF2D388, 0x8E5B4A)
        block(-1.4f, 0.95f, 0.6f, 0.5f, 0.45f, 0xA8D8C0, 0x6B4F3A)
        block(0.15f, 0.45f, 0.7f, 0.5f, 0.5f, 0xE8B4B8, 0x5E6A7A)
        block(1.35f, 0.65f, 0.6f, 0.55f, 0.55f, 0xBFD3EA, 0x7A4E3A)
        block(0.55f, 1.65f, 0.6f, 0.5f, 0.45f, 0xEFE3C8, 0x4F7F6A)
        pond(-0.75f, 1.85f, 0.42f, 0.3f)
        path(-2.3f, 0.3f, 2.3f, 0.15f, 0.2f)
        listOf(-0.6f to 0.42f, 0.8f to 0.36f, 1.9f to 0.3f).forEach { (x, z) -> lamp(x, z) }
        listOf(-2.2f to -0.9f, 2.25f to -0.1f, -2.2f to 0.6f, 2.0f to 1.4f, -0.3f to 1.2f, 1.5f to -1.7f, -1.3f to -1.6f).forEach { (x, z) -> tree(x, z, if (x < 0) SPRUCE else ROUND) }
        puddle(0.4f, 1.05f, 0.24f); puddle(-0.9f, 0.6f, 0.2f); puddle(1.0f, -0.3f, 0.18f); puddle(-1.4f, -0.6f, 0.18f); puddle(1.6f, 1.25f, 0.16f)
    }

    // --- Clouds ---------------------------------------------------------------------------

    /**
     * Twelve banks of puffs on a slow orbit around the island. Each bank has a rank: at 30 %
     * cover only the lowest-ranked third shows, under rain all of them do.
     */
    private fun clouds(): CloudPuffs {
        val r = Random(7)
        val verts = ArrayList<Float>()
        val idx = ArrayList<Int>()
        var n = 0
        fun puff(x: Float, y: Float, z: Float, size: Float, rank: Float, shade: Float, speed: Float) {
            val seed = r.nextFloat()
            for ((cx, cy) in listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)) verts += listOf(x, y, z, size, rank, shade, speed, seed, cx, cy)
            idx += listOf(n, n + 1, n + 2, n, n + 2, n + 3)
            n += 4
        }
        // Banks behind the island come first as cover grows, those in front of it last.
        val banks = 12
        val angles = List(banks) { i -> (i / banks.toFloat()) * 2 * PI.toFloat() + r.nextFloat() * 0.3f }
        val order = angles.indices.sortedBy { sin(angles[it]) }
        for ((position, i) in order.withIndex()) {
            val angle = angles[i]
            val dist = 4.2f + (i % 3) * 0.8f
            val height = 3.7f + (i % 3) * 0.45f + r.nextFloat() * 0.3f
            val width = 1.1f + r.nextFloat() * 1.1f
            val rank = (position + 0.5f) / banks
            val speed = 0.02f + r.nextFloat() * 0.02f
            val puffs = 7
            for (j in 0 until puffs) {
                val t = j / (puffs - 1f)
                val bump = sin(t * PI.toFloat())
                val along = (t - 0.5f) * width * 2
                // Puffs are laid along the tangent of the orbit.
                val px = cos(angle) * dist - sin(angle) * along + (r.nextFloat() - 0.5f) * 0.3f
                val pz = (sin(angle) * dist + cos(angle) * along) * 0.7f + (r.nextFloat() - 0.5f) * 0.3f
                val py = height + r.nextFloat() * 0.55f * bump
                val size = (0.8f + r.nextFloat() * 0.6f) * (0.65f + 0.5f * bump)
                puff(px, py, pz, size, rank, ((py - height) / 0.55f).coerceIn(0f, 1f), speed)
            }
        }
        // Low mist around the island for fog and rain: rank 2 marks it.
        for (i in 0 until 14) {
            val angle = (i / 14f) * 2 * PI.toFloat()
            val dist = 2.6f + r.nextFloat() * 1.6f
            puff(cos(angle) * dist, -0.9f + r.nextFloat() * 1.1f, sin(angle) * dist * 0.8f, 1.5f + r.nextFloat() * 0.8f, 2f, 0.5f, 0.015f)
        }
        return CloudPuffs(verts.toFloatArray(), idx.toIntArray())
    }

    companion object {
        const val ISLAND_RADIUS = 2.8f
        private const val SPRUCE = 0
        private const val ROUND = 1
        private const val APPLE = 2

        fun build(landmark: Landmark): DioramaModel = DioramaBuilder(landmark).build()
    }
}
