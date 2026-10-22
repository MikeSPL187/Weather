package app.aura.ui.scene3d

import android.opengl.GLES30
import app.aura.core.model.Landmark
import app.aura.core.scene3d.CloudPuffs
import app.aura.core.scene3d.DioramaBuilder
import app.aura.core.scene3d.Mesh
import app.aura.core.scene3d.Particles
import app.aura.core.scene3d.SceneFrame
import app.aura.core.scene3d.Shaders
import app.aura.core.scene3d.SkyQuad
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Draws the 3D diorama with OpenGL ES 3.0: a shadow pass from the key light, the lit island,
 * stars, sun and moon, water, clouds and precipitation. Mirrors `tools/scene-preview/renderer.js`
 * pass for pass, so what the preview shows is what the phone draws. Must be used on the GL thread.
 */
class SceneRenderer {

    private class Gpu(val vao: Int, val count: Int)

    private class Program(val id: Int) {
        val locations = HashMap<String, Int>()
        fun location(name: String): Int = locations.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }
    }

    private val programs = HashMap<String, Program>()
    private class Model(val world: Gpu, val water: Gpu, val clouds: Gpu, val lamps: FloatArray)

    private val models = HashMap<Landmark, Model>()
    private lateinit var particles: Gpu
    private lateinit var quad: Gpu
    private var shadowTexture = 0
    private var shadowFbo = 0
    private var width = 1
    private var height = 1

    fun init() {
        Shaders.programs.forEach { p ->
            val id = GLES30.glCreateProgram()
            GLES30.glAttachShader(id, compile(GLES30.GL_VERTEX_SHADER, Shaders.vertexSource(p)))
            GLES30.glAttachShader(id, compile(GLES30.GL_FRAGMENT_SHADER, Shaders.fragmentSource(p)))
            GLES30.glLinkProgram(id)
            val status = IntArray(1)
            GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, status, 0)
            check(status[0] == GLES30.GL_TRUE) { "${p.name}: ${GLES30.glGetProgramInfoLog(id)}" }
            programs[p.name] = Program(id)
        }
        val drops = Particles.build()
        particles = upload(drops.vertices, drops.indices, intArrayOf(4, 1, 2))
        quad = upload(SkyQuad, intArrayOf(0, 1, 2, 0, 2, 3), intArrayOf(2))

        val tex = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        shadowTexture = tex[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadowTexture)
        GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, GLES30.GL_DEPTH_COMPONENT24, SHADOW_SIZE, SHADOW_SIZE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_MODE, GLES30.GL_COMPARE_REF_TO_TEXTURE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_FUNC, GLES30.GL_LEQUAL)
        val fbo = IntArray(1)
        GLES30.glGenFramebuffers(1, fbo, 0)
        shadowFbo = fbo[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_TEXTURE_2D, shadowTexture, 0)
        GLES30.glDrawBuffers(1, intArrayOf(GLES30.GL_NONE), 0)
        GLES30.glReadBuffer(GLES30.GL_NONE)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    fun resize(w: Int, h: Int) {
        width = w.coerceAtLeast(1)
        height = h.coerceAtLeast(1)
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES30.GL_TRUE) { GLES30.glGetShaderInfoLog(shader) }
        return shader
    }

    private fun upload(vertices: FloatArray, indices: IntArray, layout: IntArray): Gpu {
        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        val vao = ids[0]
        GLES30.glBindVertexArray(vao)
        val buffers = IntArray(2)
        GLES30.glGenBuffers(2, buffers, 0)
        val vb = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(vertices).also { it.position(0) }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vb, GLES30.GL_STATIC_DRAW)
        val stride = layout.sum() * 4
        var offset = 0
        layout.forEachIndexed { location, size ->
            GLES30.glEnableVertexAttribArray(location)
            GLES30.glVertexAttribPointer(location, size, GLES30.GL_FLOAT, false, stride, offset)
            offset += size * 4
        }
        val ib = ByteBuffer.allocateDirect(indices.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().put(indices).also { it.position(0) }
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, buffers[1])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, ib, GLES30.GL_STATIC_DRAW)
        GLES30.glBindVertexArray(0)
        return Gpu(vao, indices.size)
    }

    private fun model(landmark: Landmark): Model = models.getOrPut(landmark) {
        val m = DioramaBuilder.build(landmark)
        fun mesh(mesh: Mesh) = upload(mesh.vertices, mesh.indices, intArrayOf(3, 3, 3, 4))
        fun clouds(c: CloudPuffs) = upload(c.vertices, c.indices, intArrayOf(4, 4, 2))
        Model(mesh(m.world), mesh(m.water), clouds(m.clouds), m.lamps)
    }

    /** Builds the city's meshes ahead of its first frame. */
    fun prepare(landmark: Landmark) {
        model(landmark)
    }

    private fun bind(name: String, uniforms: Map<String, FloatArray>, extra: Map<String, FloatArray> = emptyMap()) {
        val p = programs.getValue(name)
        GLES30.glUseProgram(p.id)
        fun set(key: String, v: FloatArray) {
            val loc = p.location(key)
            if (loc < 0) return
            if (key.endsWith("[0]")) {
                GLES30.glUniform3fv(loc, v.size / 3, v, 0)
                return
            }
            when (v.size) {
                16 -> GLES30.glUniformMatrix4fv(loc, 1, false, v, 0)
                3 -> GLES30.glUniform3fv(loc, 1, v, 0)
                2 -> GLES30.glUniform2fv(loc, 1, v, 0)
                else -> GLES30.glUniform1f(loc, v[0])
            }
        }
        uniforms.forEach { (k, v) -> if (k !in extra) set(k, v) }
        extra.forEach { (k, v) -> set(k, v) }
        p.location("uShadowMap").takeIf { it >= 0 }?.let { GLES30.glUniform1i(it, 0) }
        p.location("uShadowTexel").takeIf { it >= 0 }?.let { GLES30.glUniform1f(it, 1f / SHADOW_SIZE) }
    }

    private fun draw(g: Gpu) {
        GLES30.glBindVertexArray(g.vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, g.count, GLES30.GL_UNSIGNED_INT, 0)
    }

    fun render(landmark: Landmark, frame: SceneFrame) {
        val model = model(landmark)
        val world = model.world
        val water = model.water
        val clouds = model.clouds
        val u = HashMap(frame.uniforms).apply { put("uLamps[0]", model.lamps) }

        // 1. Shadow depth from the key light.
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFbo)
        GLES30.glViewport(0, 0, SHADOW_SIZE, SHADOW_SIZE)
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glPolygonOffset(1.5f, 3f)
        bind("shadow", u)
        draw(world)
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)

        // 2. Opaque world over a transparent background: the app's sky gradient shows through.
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadowTexture)
        bind("world", u)
        draw(world)

        // 3. Stars, sun and moon, behind everything that is in front of them.
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        val stars = u.getValue("uStars")
        if (stars[0] > 0.01f) {
            bind("particle", u, mapOf("uAmount" to stars, "uKind" to floatArrayOf(2f), "uTint" to floatArrayOf(1f, 1f, 1f)))
            draw(particles)
        }
        if (frame.drawSun) {
            bind("sky", u, mapOf("uCenter" to u.getValue("uSunPos"), "uSize" to floatArrayOf(4.2f), "uColor" to u.getValue("uSunColor"), "uAlpha" to u.getValue("uSunAlpha"), "uMoon" to floatArrayOf(0f)))
            draw(quad)
        }
        if (frame.drawMoon) {
            bind("sky", u, mapOf("uCenter" to u.getValue("uMoonPos"), "uSize" to floatArrayOf(3f), "uColor" to floatArrayOf(1f, 1f, 1f), "uAlpha" to u.getValue("uMoonAlpha"), "uMoon" to floatArrayOf(1f)))
            draw(quad)
        }
        // 4. Water and puddles.
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glPolygonOffset(-1f, -2f)
        bind("water", u)
        draw(water)
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
        // 5. Clouds.
        bind("cloud", u)
        draw(clouds)
        // 6. Rain or snow.
        if (frame.rain > 0.01f) {
            bind("particle", u, mapOf("uAmount" to u.getValue("uRain"), "uKind" to floatArrayOf(0f), "uTint" to floatArrayOf(0.75f, 0.83f, 1f)))
            draw(particles)
        }
        if (frame.snow > 0.01f) {
            bind("particle", u, mapOf("uAmount" to u.getValue("uSnowFall"), "uKind" to floatArrayOf(1f), "uTint" to floatArrayOf(1f, 1f, 1f)))
            draw(particles)
        }
        GLES30.glDepthMask(true)
        GLES30.glBindVertexArray(0)
    }

    private companion object {
        const val SHADOW_SIZE = 2048
    }
}
