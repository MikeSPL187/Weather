package app.aura.core.scene3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** How the shaders treat a surface. Stored per vertex, so one draw call covers the whole island. */
object Material {
    const val SOLID = 0f
    const val WINDOW = 1f
    const val WATER = 2f
    const val FOLIAGE = 3f
    const val LAMP = 4f
    const val METAL = 5f
    const val GLASS = 6f
    const val PUDDLE = 7f
    const val FLOATING = 8f
    const val BEACON = 9f
    const val GLOW = 10f
}

/**
 * Interleaved vertices: position, normal, linear colour, then material, how much snow the
 * surface takes, baked ambient occlusion and a per-object phase used for sway, bobbing and
 * which windows are lit.
 */
class Mesh(val vertices: FloatArray, val indices: IntArray) {
    val vertexCount: Int get() = vertices.size / STRIDE

    companion object {
        const val STRIDE = 13
    }
}

class MeshBuilder {
    private var data = FloatArray(4096)
    private var size = 0
    private var idx = IntArray(4096)
    private var idxSize = 0

    val vertexCount: Int get() = size / Mesh.STRIDE

    /** Defaults applied to every vertex until changed; set per object. */
    var material = Material.SOLID
    var snow = 1f
    var phase = 0f

    fun vertex(p: Vec3, n: Vec3, c: Rgb, ao: Float = 1f): Int {
        if (size + Mesh.STRIDE > data.size) data = data.copyOf(data.size * 2)
        val values = floatArrayOf(p.x, p.y, p.z, n.x, n.y, n.z, c.r, c.g, c.b, material, snow, ao, phase)
        values.copyInto(data, size)
        size += Mesh.STRIDE
        return vertexCount - 1
    }

    fun index(vararg i: Int) {
        if (idxSize + i.size > idx.size) idx = idx.copyOf(maxOf(idx.size * 2, idxSize + i.size))
        i.copyInto(idx, idxSize)
        idxSize += i.size
    }

    /** A flat triangle, counter-clockwise seen from outside. */
    fun triangle(a: Vec3, b: Vec3, c: Vec3, color: Rgb, ao: FloatArray? = null) {
        val n = ((b - a) cross (c - a)).normalized()
        val i0 = vertex(a, n, color, ao?.get(0) ?: 1f)
        val i1 = vertex(b, n, color, ao?.get(1) ?: 1f)
        val i2 = vertex(c, n, color, ao?.get(2) ?: 1f)
        index(i0, i1, i2)
    }

    /** A flat quad a-b-c-d, counter-clockwise seen from outside. */
    fun quad(a: Vec3, b: Vec3, c: Vec3, d: Vec3, color: Rgb, ao: FloatArray? = null) {
        val n = ((b - a) cross (c - a)).normalized()
        val i0 = vertex(a, n, color, ao?.get(0) ?: 1f)
        val i1 = vertex(b, n, color, ao?.get(1) ?: 1f)
        val i2 = vertex(c, n, color, ao?.get(2) ?: 1f)
        val i3 = vertex(d, n, color, ao?.get(3) ?: 1f)
        index(i0, i1, i2, i0, i2, i3)
    }

    /** Vertical darkening near the ground, the cheap half of ambient occlusion. */
    private fun groundAo(y: Float): Float = 0.6f + 0.4f * smooth(((y - GROUND_AO_START) / 0.45f).coerceIn(0f, 1f))

    /** Box standing on [y0], rotated [rotY] radians about its vertical axis. */
    fun box(cx: Float, y0: Float, cz: Float, w: Float, h: Float, d: Float, color: Rgb, rotY: Float = 0f, top: Rgb = color) {
        val c = cos(rotY)
        val s = sin(rotY)
        fun p(x: Float, y: Float, z: Float) = Vec3(cx + x * c + z * s, y, cz - x * s + z * c)
        val x0 = -w / 2
        val x1 = w / 2
        val z0 = -d / 2
        val z1 = d / 2
        val y1 = y0 + h
        val lo = groundAo(y0)
        val hi = groundAo(y1)
        val side = floatArrayOf(lo, lo, hi, hi)
        quad(p(x0, y0, z1), p(x1, y0, z1), p(x1, y1, z1), p(x0, y1, z1), color, side)
        quad(p(x1, y0, z0), p(x0, y0, z0), p(x0, y1, z0), p(x1, y1, z0), color, side)
        quad(p(x1, y0, z1), p(x1, y0, z0), p(x1, y1, z0), p(x1, y1, z1), color, side)
        quad(p(x0, y0, z0), p(x0, y0, z1), p(x0, y1, z1), p(x0, y1, z0), color, side)
        quad(p(x0, y1, z1), p(x1, y1, z1), p(x1, y1, z0), p(x0, y1, z0), top)
    }

    /**
     * A frustum around the vertical axis: a cylinder, a cone when [rTop] is 0. Smooth
     * shading unless [flat]; the top is capped when it has a radius.
     */
    fun frustum(cx: Float, y0: Float, cz: Float, rBottom: Float, rTop: Float, h: Float, segments: Int, color: Rgb, flat: Boolean = false, cap: Boolean = true, twist: Float = 0f) {
        val y1 = y0 + h
        val slope = (rBottom - rTop) / h
        fun ring(r: Float, y: Float, a: Float) = Vec3(cx + cos(a) * r, y, cz + sin(a) * r)
        for (i in 0 until segments) {
            val a0 = twist + (i * 2 * PI / segments).toFloat()
            val a1 = twist + ((i + 1) * 2 * PI / segments).toFloat()
            val b0 = ring(rBottom, y0, a0)
            val b1 = ring(rBottom, y0, a1)
            val t0 = ring(rTop, y1, a0)
            val t1 = ring(rTop, y1, a1)
            val ao = floatArrayOf(groundAo(y0), groundAo(y0), groundAo(y1), groundAo(y1))
            if (flat || rTop == 0f) {
                if (rTop == 0f && !flat) {
                    // Cone: smooth normals at the base, the tip takes the face's middle normal.
                    val am = (a0 + a1) / 2
                    val n0 = Vec3(cos(a0), slope, sin(a0)).normalized()
                    val n1 = Vec3(cos(a1), slope, sin(a1)).normalized()
                    val nm = Vec3(cos(am), slope, sin(am)).normalized()
                    val i0 = vertex(b0, n0, color, ao[0])
                    val i1 = vertex(b1, n1, color, ao[1])
                    val i2 = vertex(t0, nm, color, ao[2])
                    index(i0, i2, i1)
                } else if (rTop == 0f) {
                    triangle(b0, t0, b1, color, floatArrayOf(ao[0], ao[2], ao[1]))
                } else {
                    quad(b1, b0, t0, t1, color, floatArrayOf(ao[1], ao[0], ao[2], ao[3]))
                }
            } else {
                val n0 = Vec3(cos(a0), slope, sin(a0)).normalized()
                val n1 = Vec3(cos(a1), slope, sin(a1)).normalized()
                val i0 = vertex(b0, n0, color, ao[0])
                val i1 = vertex(b1, n1, color, ao[1])
                val i2 = vertex(t1, n1, color, ao[3])
                val i3 = vertex(t0, n0, color, ao[2])
                index(i1, i0, i3, i1, i3, i2)
            }
            if (cap && rTop > 0f) triangle(Vec3(cx, y1, cz), t1, t0, color)
        }
    }

    /** Upper half of a sphere resting on [y0]. */
    fun dome(cx: Float, y0: Float, cz: Float, r: Float, color: Rgb, segments: Int = 20, rings: Int = 8, heightScale: Float = 1f) {
        val base = vertexCount
        for (j in 0..rings) {
            val phi = (j * PI / 2 / rings).toFloat()
            for (i in 0..segments) {
                val theta = (i * 2 * PI / segments).toFloat()
                val n = Vec3(cos(theta) * cos(phi), sin(phi) / heightScale, sin(theta) * cos(phi)).normalized()
                vertex(Vec3(cx + cos(theta) * cos(phi) * r, y0 + sin(phi) * r * heightScale, cz + sin(theta) * cos(phi) * r), n, color)
            }
        }
        val row = segments + 1
        for (j in 0 until rings) for (i in 0 until segments) {
            val a = base + j * row + i
            val b = a + row
            index(a, b, a + 1, a + 1, b, b + 1)
        }
    }

    /** A faceted ball, for low-poly tree crowns and stones. */
    fun gem(center: Vec3, r: Float, color: Rgb, squash: Float = 1f, seed: Int = 0) {
        val t = ((1 + sqrt(5.0)) / 2).toFloat()
        val raw = listOf(
            Vec3(-1f, t, 0f), Vec3(1f, t, 0f), Vec3(-1f, -t, 0f), Vec3(1f, -t, 0f),
            Vec3(0f, -1f, t), Vec3(0f, 1f, t), Vec3(0f, -1f, -t), Vec3(0f, 1f, -t),
            Vec3(t, 0f, -1f), Vec3(t, 0f, 1f), Vec3(-t, 0f, -1f), Vec3(-t, 0f, 1f),
        )
        val faces = intArrayOf(
            0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11, 1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
            3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9, 4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1,
        )
        val random = kotlin.random.Random(seed)
        val pts = raw.map { v ->
            val n = v.normalized()
            val jitter = 1f + (random.nextFloat() - 0.5f) * 0.18f
            Vec3(center.x + n.x * r * jitter, center.y + n.y * r * squash * jitter, center.z + n.z * r * jitter)
        }
        for (f in faces.indices step 3) {
            val a = pts[faces[f]]
            val b = pts[faces[f + 1]]
            val c = pts[faces[f + 2]]
            // Undersides sit in the crown's own shade.
            val ao = arrayOf(a, b, c).let { vs -> FloatArray(3) { k -> 0.65f + 0.35f * ((vs[k].y - center.y) / r * 0.5f + 0.5f).coerceIn(0f, 1f) } }
            triangle(a, b, c, color, ao)
        }
    }

    /** A pitched roof along the box's longer side. */
    fun gableRoof(cx: Float, y0: Float, cz: Float, w: Float, d: Float, h: Float, color: Rgb, overhang: Float = 0.05f) {
        val alongX = w >= d
        val hw = (if (alongX) w else d) / 2 + overhang
        val hd = (if (alongX) d else w) / 2 + overhang
        fun p(a: Float, y: Float, b: Float) = if (alongX) Vec3(cx + a, y, cz + b) else Vec3(cx + b, y, cz + a)
        val e0 = p(-hw, y0, hd)
        val e1 = p(hw, y0, hd)
        val e2 = p(hw, y0, -hd)
        val e3 = p(-hw, y0, -hd)
        val r0 = p(-hw, y0 + h, 0f)
        val r1 = p(hw, y0 + h, 0f)
        if (alongX) {
            quad(e0, e1, r1, r0, color)
            quad(e2, e3, r0, r1, color)
            triangle(e1, e2, r1, color)
            triangle(e3, e0, r0, color)
        } else {
            // Axes are swapped here, so the winding is mirrored.
            quad(e1, e0, r0, r1, color)
            quad(e3, e2, r1, r0, color)
            triangle(e2, e1, r1, color)
            triangle(e0, e3, r0, color)
        }
    }

    /** A four-sided pyramid roof, as on Kremlin towers. */
    fun pyramid(cx: Float, y0: Float, cz: Float, w: Float, h: Float, color: Rgb) {
        val hw = w / 2
        val tip = Vec3(cx, y0 + h, cz)
        val c = listOf(Vec3(cx - hw, y0, cz + hw), Vec3(cx + hw, y0, cz + hw), Vec3(cx + hw, y0, cz - hw), Vec3(cx - hw, y0, cz - hw))
        for (i in 0 until 4) triangle(c[i], c[(i + 1) % 4], tip, color)
    }

    /** A flat ellipse lying on the ground, facing up. */
    fun disc(cx: Float, y: Float, cz: Float, rx: Float, rz: Float, color: Rgb, segments: Int = 24) {
        val center = vertex(Vec3(cx, y, cz), Vec3.UP, color)
        val first = vertexCount
        for (i in 0..segments) {
            val a = (i * 2 * PI / segments).toFloat()
            vertex(Vec3(cx + cos(a) * rx, y, cz + sin(a) * rz), Vec3.UP, color)
        }
        for (i in 0 until segments) index(center, first + i + 1, first + i)
    }

    fun build(): Mesh = Mesh(data.copyOf(size), idx.copyOf(idxSize))

    companion object {
        /** Island ground level; walls darken towards it. */
        const val GROUND_AO_START = 0f

        fun smooth(t: Float) = t * t * (3 - 2 * t)
    }
}
