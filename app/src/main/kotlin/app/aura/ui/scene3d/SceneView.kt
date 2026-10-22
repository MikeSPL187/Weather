package app.aura.ui.scene3d

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.view.Choreographer
import android.view.TextureView
import app.aura.core.model.Condition
import app.aura.core.model.Landmark
import app.aura.core.scene3d.Atmosphere
import app.aura.core.scene3d.CameraPose
import app.aura.core.scene3d.Rgb
import app.aura.core.scene3d.Sky
import app.aura.core.scene3d.WeatherMix
import app.aura.core.solar.SolarPosition
import kotlin.math.exp

/** What the scene should show; the view eases towards it. */
data class SceneTarget(val landmark: Landmark, val condition: Condition, val sun: SolarPosition)

/**
 * A transparent [TextureView] that draws the 3D diorama on its own thread at the display's
 * frame rate. It pauses while hidden, halves its rate in battery saver, and reports
 * [onFailure] if the device cannot run the renderer, so the caller can fall back.
 */
@SuppressLint("ViewConstructor")
class SceneView(context: Context, private val onFailure: (Throwable) -> Unit) : TextureView(context), TextureView.SurfaceTextureListener {

    @Volatile var target: SceneTarget? = null
    @Volatile var tiltX = 0f
    @Volatile var tiltY = 0f
    @Volatile var dragYaw = 0f
    @Volatile var dragging = false
    @Volatile var stillFrame = false

    private val thread = HandlerThread("AuraScene").apply { start() }
    private val handler = Handler(thread.looper)
    private val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    @Volatile private var visible = true

    // Everything below belongs to the render thread.
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var renderer: SceneRenderer? = null
    private var running = false
    private var failed = false
    private var lastNanos = 0L
    private var time = 0f
    private var yaw = 0f
    private var frameCount = 0
    private var mix: WeatherMix? = null
    private var shown: Landmark? = null
    private var skyTop: Rgb? = null
    private var skyBottom: Rgb? = null
    private var width = 1
    private var height = 1

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            Choreographer.getInstance().postFrameCallback(this)
            frameCount++
            if (power?.isPowerSaveMode == true && frameCount % 2 == 1) return
            if (!visible) {
                lastNanos = frameTimeNanos
                return
            }
            drawFrame(frameTimeNanos)
        }
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, w: Int, h: Int) {
        handler.post { start(texture, w, h) }
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, w: Int, h: Int) {
        handler.post {
            width = w
            height = h
            renderer?.resize(w, h)
        }
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        // Wait for the thread to let go of the surface before the texture is released.
        val done = java.util.concurrent.CountDownLatch(1)
        handler.post {
            stop()
            done.countDown()
        }
        done.await(2, java.util.concurrent.TimeUnit.SECONDS)
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        visible = isVisible
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.post {
            stop()
            thread.quitSafely()
        }
    }

    private fun start(texture: SurfaceTexture, w: Int, h: Int) {
        if (failed) return
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }
            val config = chooseConfig() ?: error("No OpenGL ES 3 config")
            eglContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
            check(eglContext != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }
            surface = EGL14.eglCreateWindowSurface(display, config, texture, intArrayOf(EGL14.EGL_NONE), 0)
            check(surface != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed" }
            check(EGL14.eglMakeCurrent(display, surface, surface, eglContext)) { "eglMakeCurrent failed" }
            width = w
            height = h
            renderer = SceneRenderer().also {
                it.init()
                it.resize(w, h)
                target?.let { t -> it.prepare(t.landmark) }
            }
            running = true
            lastNanos = 0L
            Choreographer.getInstance().postFrameCallback(frameCallback)
        } catch (e: Throwable) {
            fail(e)
        }
    }

    private fun chooseConfig(): EGLConfig? {
        fun attempt(samples: Int, depth: Int): EGLConfig? {
            val attributes = mutableListOf(
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, depth, EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            )
            if (samples > 0) attributes += listOf(EGL14.EGL_SAMPLE_BUFFERS, 1, EGL14.EGL_SAMPLES, samples)
            attributes += EGL14.EGL_NONE
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            return if (EGL14.eglChooseConfig(display, attributes.toIntArray(), 0, configs, 0, 1, count, 0) && count[0] > 0) configs[0] else null
        }
        return attempt(4, 24) ?: attempt(0, 24) ?: attempt(0, 16)
    }

    private fun drawFrame(nanos: Long) {
        val t = target ?: return
        val r = renderer ?: return
        val dt = if (lastNanos == 0L) 0f else ((nanos - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastNanos = nanos
        if (!stillFrame) time += dt
        // A new city swings in from the side.
        if (shown != t.landmark) {
            if (shown != null && !stillFrame && !dragging) dragYaw = 32f
            shown = t.landmark
        }

        // Ease into a new weather and sky instead of cutting.
        val goal = WeatherMix.of(t.condition)
        val (top, bottom) = Sky.colors(t.condition, t.sun.elevationDeg)
        val topGoal = Rgb.hex(top and 0xFFFFFF)
        val bottomGoal = Rgb.hex(bottom and 0xFFFFFF)
        val k = if (mix == null || stillFrame) 1f else 1 - exp(-dt * 1.6f)
        mix = mix?.towards(goal, k) ?: goal
        skyTop = skyTop?.mix(topGoal, k) ?: topGoal
        skyBottom = skyBottom?.mix(bottomGoal, k) ?: bottomGoal

        // A dragged island drifts back to its usual view.
        yaw = if (dragging) dragYaw else yaw + (dragYaw - yaw) * (1 - exp(-dt * 8f))
        if (!dragging) dragYaw *= exp(-dt * 0.35f)
        val pose = CameraPose(yawDeg = yaw + tiltX * 7f, pitchDeg = tiltY * 3f, aspect = width / height.toFloat())
        try {
            r.render(t.landmark, Atmosphere.frame(mix!!, skyTop!!, skyBottom!!, t.sun, time, pose))
            EGL14.eglSwapBuffers(display, surface)
        } catch (e: Throwable) {
            fail(e)
        }
    }

    private fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        renderer = null
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglTerminate(display)
        }
        surface = EGL14.EGL_NO_SURFACE
        eglContext = EGL14.EGL_NO_CONTEXT
        display = EGL14.EGL_NO_DISPLAY
    }

    private fun fail(e: Throwable) {
        failed = true
        stop()
        post { onFailure(e) }
    }
}
