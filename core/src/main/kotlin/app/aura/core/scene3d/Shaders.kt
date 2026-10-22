package app.aura.core.scene3d

/** The scene's GLSL ES 3.00 programs, assembled from the shared sources in resources. */
object Shaders {
    private const val VERSION = "#version 300 es\n"

    class Program(val name: String, val vertex: List<String>, val fragment: List<String>, val defines: List<String> = emptyList())

    val programs = listOf(
        Program("world", listOf("world.vert"), listOf("common.glsl", "world.frag")),
        Program("shadow", listOf("world.vert"), listOf("shadow.frag"), defines = listOf("SHADOW")),
        Program("water", listOf("world.vert"), listOf("common.glsl", "water.frag")),
        Program("cloud", listOf("cloud.vert"), listOf("cloud.frag")),
        Program("sky", listOf("sky.vert"), listOf("sky.frag")),
        Program("particle", listOf("particle.vert"), listOf("particle.frag")),
        Program("bolt", listOf("bolt.vert"), listOf("bolt.frag")),
    )

    fun source(name: String): String =
        requireNotNull(Shaders::class.java.getResourceAsStream("/aura/shaders/$name")) { "Missing shader $name" }.bufferedReader().use { it.readText() }

    fun vertexSource(program: Program): String = assemble(program.vertex, program.defines)
    fun fragmentSource(program: Program): String = assemble(program.fragment, program.defines)

    private fun assemble(parts: List<String>, defines: List<String>): String = buildString {
        append(VERSION)
        defines.forEach { append("#define ").append(it).append('\n') }
        parts.forEach { append(source(it)).append('\n') }
    }
}
