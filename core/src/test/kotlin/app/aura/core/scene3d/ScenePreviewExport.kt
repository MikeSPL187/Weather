package app.aura.core.scene3d

import app.aura.core.data.Cities
import app.aura.core.model.City
import app.aura.core.model.Condition
import app.aura.core.model.Landmark
import app.aura.core.solar.SolarCalculator
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Writes the scene's meshes, frames and shaders for the browser preview in
 * `tools/scene-preview`. Runs only with SCENE_PREVIEW=1, so the normal test run stays fast.
 */
class ScenePreviewExport {

    private val out = File(System.getenv("SCENE_PREVIEW_DIR") ?: "build/scene-preview")

    private fun floats(name: String, data: FloatArray) {
        val buffer = ByteBuffer.allocate(data.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(data)
        File(out, name).writeBytes(buffer.array())
    }

    private fun ints(name: String, data: IntArray) {
        val buffer = ByteBuffer.allocate(data.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asIntBuffer().put(data)
        File(out, name).writeBytes(buffer.array())
    }

    private fun json(uniforms: Map<String, FloatArray>): String =
        uniforms.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":[${v.joinToString(",")}]" }

    @Test
    fun export() {
        assumeTrue(System.getenv("SCENE_PREVIEW") == "1")
        out.mkdirs()
        Landmark.entries.forEach { landmark ->
            val model = DioramaBuilder.build(landmark)
            floats("${landmark.name}.world.f32", model.world.vertices)
            ints("${landmark.name}.world.i32", model.world.indices)
            floats("${landmark.name}.water.f32", model.water.vertices)
            ints("${landmark.name}.water.i32", model.water.indices)
            floats("${landmark.name}.cloud.f32", model.clouds.vertices)
            ints("${landmark.name}.cloud.i32", model.clouds.indices)
            floats("${landmark.name}.lamps.f32", model.lamps)
        }
        val particles = Particles.build()
        floats("particles.f32", particles.vertices)
        ints("particles.i32", particles.indices)
        Shaders.programs.forEach { p ->
            File(out, "${p.name}.vert").writeText(Shaders.vertexSource(p))
            File(out, "${p.name}.frag").writeText(Shaders.fragmentSource(p))
        }

        val tver = City("tver", "Тверь", "", 56.8587, 35.9176, 180, Landmark.GENERIC_TOWN)
        val cases = listOf(
            Triple("moscow-clear-afternoon", Cities.moscow, Condition.CLEAR to LocalDateTime.of(2026, 10, 22, 14, 20)),
            Triple("moscow-partly-morning", Cities.moscow, Condition.PARTLY_CLOUDY to LocalDateTime.of(2026, 10, 22, 9, 30)),
            Triple("petersburg-rain", Cities.petersburg, Condition.RAIN to LocalDateTime.of(2026, 10, 22, 13, 5)),
            Triple("kazan-night", Cities.kazan, Condition.CLEAR to LocalDateTime.of(2026, 10, 22, 22, 30)),
            Triple("almaty-snow", Cities.almaty, Condition.SNOW to LocalDateTime.of(2026, 10, 22, 11, 15)),
            Triple("town-storm", tver, Condition.STORM to LocalDateTime.of(2026, 10, 22, 16, 40)),
            Triple("moscow-sunset", Cities.moscow, Condition.CLEAR to LocalDateTime.of(2026, 10, 22, 16, 55)),
            Triple("kazan-fog", Cities.kazan, Condition.FOG to LocalDateTime.of(2026, 10, 22, 8, 40)),
        )
        val entries = cases.map { (name, city, cw) ->
            val (condition, time) = cw
            val sun = SolarCalculator.position(city, time.toLocalDate(), time.hour * 60.0 + time.minute)
            val frame = Atmosphere.frame(condition, sun, time = 3.1f)
            val (top, bottom) = Sky.colors(condition, sun.elevationDeg)
            "\"$name\":{\"landmark\":\"${city.landmark.name}\",\"sun\":${frame.drawSun},\"moon\":${frame.drawMoon}," +
                "\"skyTop\":\"#${"%06X".format(top and 0xFFFFFF)}\",\"skyBottom\":\"#${"%06X".format(bottom and 0xFFFFFF)}\"," +
                "\"uniforms\":${json(frame.uniforms)}}"
        }
        File(out, "cases.json").writeText(entries.joinToString(",", "{", "}"))
    }
}
