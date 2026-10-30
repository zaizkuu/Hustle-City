package com.hustlecity.game.desktop

import com.hustlecity.game.render.gpu.GpuFrame
import com.hustlecity.game.render.gpu.GpuRenderer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.lwjgl.opengl.GL33C
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CompletableFuture

/**
 * Renders the trailer's recorded shots (CinematicTest via CinemaWriter) with the game's own GpuRenderer on the
 * desktop's OpenGL, as tools/cinema/render.mjs does in a browser, but several times faster on a machine without a
 * GPU. Writes <out>/<shot>/NNNN.png.
 *
 *   ./gradlew :desktop:test --tests '*CinemaRender*' -PcinemaIn=app/build/cinema -PcinemaOut=frames [-PcinemaShots=n01,n02]
 *       [-PcinemaFrames=0,24,48] [-PcinemaPlan=plan.txt]
 * A plan (from a cut's --plan) lists the frames a cut uses, a shot per line ("n04_bridge_gold 2,3,4"), so only those
 * are drawn.
 */
class CinemaRender {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun render() {
        val input = System.getProperty("cinema.in")
        assumeTrue("set -PcinemaIn", input != null)
        val root = File(input!!)
        val out = File(System.getProperty("cinema.out") ?: "build/cinema-frames")
        val only = System.getProperty("cinema.shots")?.split(',')?.filter { it.isNotBlank() }
        val pick = System.getProperty("cinema.frames")?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.toSet()
        val plan = System.getProperty("cinema.plan")?.let { p ->
            File(p).readLines().filter { it.isNotBlank() }.associate { l -> l.substringBefore(' ') to l.substringAfter(' ').split(',').map { it.toInt() }.toSet() }
        }
        val shots = root.listFiles()!!.filter { File(it, "0000").isDirectory && (only == null || only.any { p -> it.name.startsWith(p) }) }
            .filter { plan == null || it.name in plan }.sortedBy { it.name }
        val gl = Gl()
        try {
            for (shot in shots) {
                // each recording numbers its cached cities from 1, so one shot's city must never carry into the next
                static = null; staticId = -1L
                val want = plan?.get(shot.name) ?: pick
                val frames = shot.listFiles()!!.filter { it.name.matches(Regex("\\d{4}")) && (want == null || it.name.toInt() in want) }.sortedBy { it.name }
                val dir = File(out, shot.name).apply { mkdirs() }
                val t0 = System.nanoTime()
                var n = 0
                for (fd in frames) {
                    val target = File(dir, fd.name + ".png")
                    if (target.exists()) continue
                    target.writeBytes(gl.render(loadFrame(shot, fd)))
                    n++
                }
                println("${shot.name}: $n frames, %.2f s/frame".format((System.nanoTime() - t0) / 1e9 / n.coerceAtLeast(1)))
            }
        } finally {
            gl.close()
        }
    }

    // ---------------------------------------------------------------- reading a recording

    private var static: GpuFrame? = null
    private var staticId = -1L
    private var version = 0L

    private fun ints(f: File): IntArray {
        val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
        return IntArray(b.remaining()).also { b.get(it) }
    }

    private fun fill(frame: GpuFrame, dir: File) {
        for (i in 0 until 3) {
            val v = ints(File(dir, "v$i.bin")); val ix = ints(File(dir, "i$i.bin"))
            val s = frame.streams[i]
            s.clear()
            s.ensure(v.size / GpuFrame.STRIDE, ix.size)
            System.arraycopy(v, 0, s.v, 0, v.size); System.arraycopy(ix, 0, s.idx, 0, ix.size)
            s.nv = v.size / GpuFrame.STRIDE; s.ni = ix.size
        }
    }

    private fun loadFrame(shot: File, fd: File): GpuFrame {
        val j = json.parseToJsonElement(File(fd, "frame.json").readText()) as JsonObject
        fun f(k: String) = j[k]!!.jsonPrimitive.float
        fun arr(k: String, into: FloatArray) { val a = j[k]!!.jsonArray; for (i in into.indices) into[i] = a[i].jsonPrimitive.float }
        val g = GpuFrame()
        fill(g, fd)
        g.width = f("width"); g.height = f("height")
        arr("cam", g.cam); arr("right", g.right); arr("up", g.up); arr("fwd", g.fwd)
        g.focal = f("focal"); g.centerX = f("centerX"); g.centerY = f("centerY"); g.near = f("near"); g.far = f("far")
        arr("sunDir", g.sunDir); arr("sunColor", g.sunColor); arr("skyAmbient", g.skyAmbient); arr("groundAmbient", g.groundAmbient)
        arr("fogColor", g.fogColor); arr("skyTop", g.skyTop); arr("skyHorizon", g.skyHorizon)
        val fog = FloatArray(3).also { arr("fog", it) }
        g.fogStart = fog[0]; g.fogEnd = fog[1]; g.fogAmount = fog[2]
        val misc = FloatArray(4).also { arr("misc", it) }
        g.daylight = misc[0]; g.wet = misc[1]; g.exposure = misc[2]; g.mode = misc[3].toInt()
        g.clearColor = j["clearColor"]!!.jsonPrimitive.long.toInt()
        g.time = f("time")
        arr("light", g.lightMat)
        val sh = FloatArray(4).also { arr("shadow", it) }
        g.shadowStrength = sh[0]; g.shadowOffset = sh[1]; g.shadowTexel = sh[2]; g.shadowOn = sh[3] > .5f
        g.shadowSize = j["shadowSize"]!!.jsonPrimitive.int
        val skip = j["skip"]!!.jsonArray.map { it.jsonPrimitive.int }
        if (g.skip.size < skip.size) g.skip = IntArray(skip.size)
        for (i in skip.indices) g.skip[i] = skip[i]
        g.skipCount = skip.size / 2
        // the cached city: loaded once per version, like the game's GL thread uploads it
        val sid = j["static"]!!.jsonPrimitive.long
        if (sid < 0) g.staticGeom = null
        else {
            if (sid != staticId || static == null) {
                static = GpuFrame().also { fill(it, File(shot, "static_$sid")); it.version = sid }
                staticId = sid
            }
            g.staticGeom = static
        }
        g.version = ++version
        return g
    }

    // ---------------------------------------------------------------- the GL side

    /** A GpuRenderer on the desktop GL thread, drawing into a multisampled framebuffer and reading it back. */
    private class Gl {
        private var renderer: GpuRenderer? = null
        private var w = 0; private var h = 0
        private var fbo = 0; private var color = 0; private var depth = 0; private var resolve = 0; private var resolveColor = 0
        private var pixels: ByteBuffer? = null

        private fun <T> onGl(block: () -> T): T {
            val f = CompletableFuture<T>()
            DesktopGl.post { try { f.complete(block()) } catch (t: Throwable) { f.completeExceptionally(t) } }
            return f.get()
        }

        init {
            onGl {
                check(DesktopGl.ready) { "no OpenGL context: ${DesktopGl.failure}" }
                renderer = GpuRenderer(2).also { it.init() }
                println("rendering with ${DesktopGl.renderer}, photo materials ${renderer!!.photoMaterials}")
            }
        }

        private fun targets(fw: Int, fh: Int) {
            if (fw == w && fh == h) return
            w = fw; h = fh
            val samples = minOf(4, DesktopGl.maxSamples)
            fbo = GL33C.glGenFramebuffers(); color = GL33C.glGenRenderbuffers(); depth = GL33C.glGenRenderbuffers()
            GL33C.glBindRenderbuffer(GL33C.GL_RENDERBUFFER, color)
            GL33C.glRenderbufferStorageMultisample(GL33C.GL_RENDERBUFFER, samples, GL33C.GL_RGBA8, w, h)
            GL33C.glBindRenderbuffer(GL33C.GL_RENDERBUFFER, depth)
            GL33C.glRenderbufferStorageMultisample(GL33C.GL_RENDERBUFFER, samples, GL33C.GL_DEPTH_COMPONENT24, w, h)
            GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, fbo)
            GL33C.glFramebufferRenderbuffer(GL33C.GL_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_RENDERBUFFER, color)
            GL33C.glFramebufferRenderbuffer(GL33C.GL_FRAMEBUFFER, GL33C.GL_DEPTH_ATTACHMENT, GL33C.GL_RENDERBUFFER, depth)
            resolve = GL33C.glGenFramebuffers(); resolveColor = GL33C.glGenRenderbuffers()
            GL33C.glBindRenderbuffer(GL33C.GL_RENDERBUFFER, resolveColor)
            GL33C.glRenderbufferStorage(GL33C.GL_RENDERBUFFER, GL33C.GL_RGBA8, w, h)
            GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, resolve)
            GL33C.glFramebufferRenderbuffer(GL33C.GL_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_RENDERBUFFER, resolveColor)
            pixels = MemoryUtil.memAlloc(w * h * 4)
        }

        /** Draws [f] and returns it as a PNG. */
        fun render(f: GpuFrame): ByteArray {
            val fw = f.width.toInt(); val fh = f.height.toInt()
            val bytes = onGl {
                targets(fw, fh)
                val r = renderer!!
                GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, fbo)
                r.target = fbo
                r.resize(fw, fh)
                r.draw(f)
                GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, fbo)
                GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, resolve)
                GL33C.glBlitFramebuffer(0, 0, fw, fh, 0, 0, fw, fh, GL33C.GL_COLOR_BUFFER_BIT, GL33C.GL_NEAREST)
                GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, resolve)
                GL33C.glReadBuffer(GL33C.GL_COLOR_ATTACHMENT0)
                val p = pixels!!
                p.clear()
                GL33C.glReadPixels(0, 0, fw, fh, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, p)
                // GL rows run bottom up
                val row = fw * 4
                val out = ByteArray(row * fh)
                for (y in 0 until fh) { p.position((fh - 1 - y) * row); p.get(out, y * row, row) }
                out
            }
            val img = Image.makeRaster(ImageInfo(fw, fh, ColorType.RGBA_8888, ColorAlphaType.OPAQUE), bytes, fw * 4)
            return img.encodeToData(EncodedImageFormat.PNG)!!.bytes.also { img.close() }
        }

        fun close() = onGl { renderer?.release() }
    }
}
