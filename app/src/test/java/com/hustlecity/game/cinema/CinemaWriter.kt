package com.hustlecity.game.cinema

import android.graphics.Bitmap
import com.hustlecity.game.render.gpu.GpuFrame
import com.hustlecity.game.render.gpu.Shaders
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes a shot as a sequence of HD-renderer frames for tools/cinema/render.mjs: per frame its own geometry and
 * camera (NNNN/), the cached city geometry once per cache version (static_V/), and optionally a transparent
 * overlay (labels, weather or the game's HUD) to lay over the render.
 */
class CinemaWriter(val dir: File) {
    private val written = HashSet<Long>()

    init {
        dir.deleteRecursively(); dir.mkdirs()
        val sh = dir.parentFile!!
        File(sh, "world.vs").writeText(Shaders.WORLD_VS); File(sh, "world.fs").writeText(Shaders.WORLD_FS)
        File(sh, "sky.vs").writeText(Shaders.SKY_VS); File(sh, "sky.fs").writeText(Shaders.SKY_FS)
        File(sh, "shadow.vs").writeText(Shaders.SHADOW_VS); File(sh, "shadow.fs").writeText(Shaders.SHADOW_FS)
    }

    var frames = 0
        private set

    fun write(frame: GpuFrame, overlay: Bitmap? = null) {
        val st = frame.staticGeom
        if (st != null && written.add(st.version)) {
            val sd = File(dir, "static_${st.version}").apply { mkdirs() }
            for (i in 0 until 3) {
                val s = st.streams[i]
                ints(File(sd, "v$i.bin"), s.v, s.nv * GpuFrame.STRIDE)
                ints(File(sd, "i$i.bin"), s.idx, s.ni)
            }
        }
        val fd = File(dir, "%04d".format(frames)).apply { mkdirs() }
        for (i in 0 until 3) {
            val s = frame.streams[i]
            ints(File(fd, "v$i.bin"), s.v, s.nv * GpuFrame.STRIDE)
            ints(File(fd, "i$i.bin"), s.idx, s.ni)
        }
        val skip = (0 until frame.skipCount * 2).joinToString(",", "[", "]") { frame.skip[it].toString() }
        fun a(x: FloatArray) = x.joinToString(",", "[", "]")
        File(fd, "frame.json").writeText("""{"width":${frame.width},"height":${frame.height},"cam":${a(frame.cam)},"right":${a(frame.right)},"up":${a(frame.up)},"fwd":${a(frame.fwd)},
"focal":${frame.focal},"centerX":${frame.centerX},"centerY":${frame.centerY},"near":${frame.near},"far":${frame.far},
"sunDir":${a(frame.sunDir)},"sunColor":${a(frame.sunColor)},"skyAmbient":${a(frame.skyAmbient)},"groundAmbient":${a(frame.groundAmbient)},
"fogColor":${a(frame.fogColor)},"fog":[${frame.fogStart},${frame.fogEnd},${frame.fogAmount}],"skyTop":${a(frame.skyTop)},"skyHorizon":${a(frame.skyHorizon)},
"misc":[${frame.daylight},${frame.wet},${frame.exposure},${frame.mode}],"clearColor":${frame.clearColor},"time":${frame.time},
"light":${a(frame.lightMat)},"shadow":[${frame.shadowStrength},${frame.shadowOffset},${frame.shadowTexel},${if (frame.shadowOn) 1 else 0}],"shadowSize":${frame.shadowSize},
"static":${st?.version ?: -1},"skip":$skip,"overlay":${overlay != null}}""")
        if (overlay != null) FileOutputStream(File(fd, "overlay.png")).use { overlay.compress(Bitmap.CompressFormat.PNG, 100, it) }
        frames++
    }

    private fun ints(f: File, data: IntArray, n: Int) {
        val bb = ByteBuffer.allocate(n * 4).order(ByteOrder.LITTLE_ENDIAN)
        bb.asIntBuffer().put(data, 0, n)
        DataOutputStream(FileOutputStream(f)).use { it.write(bb.array()) }
    }
}
