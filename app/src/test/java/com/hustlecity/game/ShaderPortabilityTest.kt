package com.hustlecity.game

import com.hustlecity.game.render.gpu.Shaders
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Every shader compiles in the reference GLSL compiler both as Android gets it (GLSL ES 3.00) and as the Windows build
 * hands it to the desktop driver (GLSL 3.30 core), so a strict driver (AMD, Intel) never meets one the forgiving ones
 * let through, such as a function named after a desktop built-in. Needs glslangValidator (the glslang-tools package);
 * skipped without it, except on CI.
 */
class ShaderPortabilityTest {
    private val shaders = listOf(
        "world.vert" to Shaders.WORLD_VS, "world.frag" to Shaders.worldFs(false), "world_fast.frag" to Shaders.worldFs(true),
        "shadow.vert" to Shaders.SHADOW_VS, "shadow.frag" to Shaders.SHADOW_FS,
        "sky.vert" to Shaders.SKY_VS, "sky.frag" to Shaders.SKY_FS,
    )

    @Test
    fun shadersCompileForAndroidAndWindows() {
        val tool = System.getenv("PATH").orEmpty().split(File.pathSeparator).map { File(it, "glslangValidator") }.firstOrNull { it.canExecute() }
        if (System.getenv("CI") == null) assumeTrue("glslangValidator not installed", tool != null)
        assertTrue("glslangValidator is needed on CI (apt install glslang-tools)", tool != null)
        val dir = File("build/shader-check").apply { mkdirs() }
        val failures = StringBuilder()
        for ((name, es) in shaders) {
            assertTrue("$name isn't GLSL ES 3.00", es.startsWith("#version 300 es"))
            // what the desktop build does to a shader before compiling it (android.opengl.GLES30.desktopGlsl)
            val desktop = es.replaceFirst("#version 300 es", "#version 330 core")
            for ((flavour, src) in listOf("es" to es, "gl330" to desktop)) {
                val stage = name.substringAfterLast('.')
                val f = File(dir, "${name.substringBeforeLast('.')}_$flavour.$stage")
                f.writeText(src)
                val p = ProcessBuilder(tool!!.path, f.path).redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                check(p.waitFor(60, TimeUnit.SECONDS)) { "glslangValidator hung on ${f.name}" }
                if (p.exitValue() != 0) failures.append("${f.name}:\n").append(out.trim()).append('\n')
            }
        }
        assertTrue(failures.toString(), failures.isEmpty())
    }
}
