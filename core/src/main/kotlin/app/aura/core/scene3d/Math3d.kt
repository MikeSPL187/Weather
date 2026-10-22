package app.aura.core.scene3d

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val length: Float get() = sqrt(this dot this)
    fun normalized(): Vec3 = length.let { if (it < 1e-8f) this else this * (1f / it) }

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val UP = Vec3(0f, 1f, 0f)
    }
}

/** Linear RGB, converted from the sRGB hex colours of the design so lighting adds up correctly. */
data class Rgb(val r: Float, val g: Float, val b: Float) {
    operator fun times(s: Float) = Rgb(r * s, g * s, b * s)
    operator fun plus(o: Rgb) = Rgb(r + o.r, g + o.g, b + o.b)
    fun mix(o: Rgb, t: Float) = Rgb(r + (o.r - r) * t, g + (o.g - g) * t, b + (o.b - b) * t)

    companion object {
        private fun channel(v: Int): Float {
            val c = v / 255f
            return if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        }

        fun hex(rgb: Long): Rgb = Rgb(channel(((rgb shr 16) and 0xFF).toInt()), channel(((rgb shr 8) and 0xFF).toInt()), channel((rgb and 0xFF).toInt()))
    }
}

/** Column-major 4×4 matrix, laid out as OpenGL expects it. */
class Mat4(val m: FloatArray = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }) {
    operator fun times(o: Mat4): Mat4 {
        val r = FloatArray(16)
        for (c in 0 until 4) for (row in 0 until 4) {
            var s = 0f
            for (k in 0 until 4) s += m[k * 4 + row] * o.m[c * 4 + k]
            r[c * 4 + row] = s
        }
        return Mat4(r)
    }

    fun transform(v: Vec3, w: Float = 1f): FloatArray {
        val out = FloatArray(4)
        for (row in 0 until 4) out[row] = m[row] * v.x + m[4 + row] * v.y + m[8 + row] * v.z + m[12 + row] * w
        return out
    }

    companion object {
        fun perspective(fovYDeg: Float, aspect: Float, near: Float, far: Float): Mat4 {
            val f = 1f / tan(Math.toRadians(fovYDeg / 2.0)).toFloat()
            val r = FloatArray(16)
            r[0] = f / aspect
            r[5] = f
            r[10] = (far + near) / (near - far)
            r[11] = -1f
            r[14] = 2 * far * near / (near - far)
            return Mat4(r)
        }

        fun ortho(left: Float, right: Float, bottom: Float, top: Float, near: Float, far: Float): Mat4 {
            val r = FloatArray(16)
            r[0] = 2 / (right - left)
            r[5] = 2 / (top - bottom)
            r[10] = -2 / (far - near)
            r[12] = -(right + left) / (right - left)
            r[13] = -(top + bottom) / (top - bottom)
            r[14] = -(far + near) / (far - near)
            r[15] = 1f
            return Mat4(r)
        }

        fun lookAt(eye: Vec3, target: Vec3, up: Vec3 = Vec3.UP): Mat4 {
            val f = (target - eye).normalized()
            val s = (f cross up).normalized()
            val u = s cross f
            val r = FloatArray(16)
            r[0] = s.x; r[4] = s.y; r[8] = s.z
            r[1] = u.x; r[5] = u.y; r[9] = u.z
            r[2] = -f.x; r[6] = -f.y; r[10] = -f.z
            r[12] = -(s dot eye)
            r[13] = -(u dot eye)
            r[14] = f dot eye
            r[15] = 1f
            return Mat4(r)
        }

        fun rotationY(rad: Float): Mat4 {
            val c = cos(rad)
            val s = sin(rad)
            val r = FloatArray(16)
            r[0] = c; r[2] = -s; r[8] = s; r[10] = c; r[5] = 1f; r[15] = 1f
            return Mat4(r)
        }
    }
}
