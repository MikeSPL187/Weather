package app.aura.core.scene3d

import app.aura.core.data.Cities
import app.aura.core.model.Condition
import app.aura.core.model.Landmark
import app.aura.core.solar.SolarCalculator
import java.time.LocalDate
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneTest {

    @Test
    fun everyDioramaIsAValidMesh() {
        Landmark.entries.forEach { landmark ->
            val model = DioramaBuilder.build(landmark)
            listOf(model.world, model.water).forEach { mesh ->
                assertTrue(landmark.name, mesh.indices.isNotEmpty())
                assertTrue(landmark.name, mesh.indices.all { it in 0 until mesh.vertexCount })
                for (v in 0 until mesh.vertexCount) {
                    val o = v * Mesh.STRIDE
                    val length = Vec3(mesh.vertices[o + 3], mesh.vertices[o + 4], mesh.vertices[o + 5]).length
                    assertEquals("$landmark normal $v", 1f, length, 0.01f)
                    assertTrue(mesh.vertices.copyOfRange(o, o + Mesh.STRIDE).all { it.isFinite() })
                }
            }
            assertEquals(0, model.clouds.vertices.size % CloudPuffs.STRIDE)
        }
    }

    @Test
    fun boxFacesPointOutwards() {
        val b = MeshBuilder()
        b.box(1f, 0f, 2f, 1f, 1f, 1f, Rgb(1f, 1f, 1f))
        val mesh = b.build()
        val center = Vec3(1f, 0.5f, 2f)
        for (t in mesh.indices.indices step 3) {
            val p = (0 until 3).map { k ->
                val o = mesh.indices[t + k] * Mesh.STRIDE
                Vec3(mesh.vertices[o], mesh.vertices[o + 1], mesh.vertices[o + 2])
            }
            val winding = ((p[1] - p[0]) cross (p[2] - p[0])).normalized()
            val outward = ((p[0] + p[1] + p[2]) * (1f / 3) - center).normalized()
            assertTrue("triangle $t faces inwards", (winding dot outward) > 0.3f)
        }
    }

    @Test
    fun cameraFramesTheIsland() {
        val (viewProj, _) = Atmosphere.camera(CameraPose())
        fun ndc(p: Vec3): Pair<Float, Float> = viewProj.transform(p).let { it[0] / it[3] to it[1] / it[3] }
        val spireTop = ndc(Vec3(-0.1f, 3.95f, -0.3f))
        val tip = ndc(Vec3(0.15f, -3.25f, -0.1f))
        val left = ndc(Vec3(-2.9f, 0f, 0f))
        val right = ndc(Vec3(2.9f, 0f, 0f))
        listOf(spireTop, tip, left, right).forEach { (x, y) -> assertTrue("$x,$y", abs(x) < 1f && abs(y) < 1f) }
    }

    @Test
    fun framesAreFiniteAndFollowTheSun() {
        val date = LocalDate.of(2026, 10, 22)
        Condition.entries.forEach { condition ->
            listOf(3 * 60.0, 13 * 60.0, 17 * 60.0).forEach { minutes ->
                val frame = Atmosphere.frame(condition, SolarCalculator.position(Cities.moscow, date, minutes), time = 1.5f)
                frame.uniforms.forEach { (name, v) -> assertTrue("$condition $name", v.all { it.isFinite() }) }
            }
        }
        val noon = Atmosphere.frame(Condition.CLEAR, SolarCalculator.position(Cities.moscow, date, 13 * 60.0), 0f)
        val night = Atmosphere.frame(Condition.CLEAR, SolarCalculator.position(Cities.moscow, date, 1 * 60.0), 0f)
        assertTrue(noon.uniforms.getValue("uLightColor").sum() > night.uniforms.getValue("uLightColor").sum() * 4)
        assertTrue(night.uniforms.getValue("uWindowGlow")[0] > 0.9f)
        assertTrue(noon.uniforms.getValue("uStars")[0] < 0.01f)
        // Rain wets the ground and shows puddles; snow covers them.
        val rain = Atmosphere.frame(Condition.RAIN, SolarCalculator.position(Cities.moscow, date, 13 * 60.0), 0f)
        assertEquals(0.75f, rain.uniforms.getValue("uPuddle")[0], 0.01f)
        val snow = Atmosphere.frame(Condition.SNOW, SolarCalculator.position(Cities.moscow, date, 13 * 60.0), 0f)
        assertEquals(0f, snow.uniforms.getValue("uPuddle")[0], 0.01f)
    }

    @Test
    fun shadersAssemble() {
        Shaders.programs.forEach { p ->
            val vertex = Shaders.vertexSource(p)
            val fragment = Shaders.fragmentSource(p)
            assertTrue(vertex.startsWith("#version 300 es"))
            assertTrue(fragment.contains("void main()"))
        }
    }

    @Test
    fun weatherEasesBetweenStates() {
        val clear = WeatherMix.of(Condition.CLEAR)
        val storm = WeatherMix.of(Condition.STORM)
        val half = clear.towards(storm, 0.5f)
        assertEquals((clear.cloud + storm.cloud) / 2, half.cloud, 1e-6f)
        assertEquals(storm, clear.towards(storm, 1f))
    }
}
