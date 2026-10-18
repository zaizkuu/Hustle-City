package com.hustlecity.game.render.gpu

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.opengles.GL10

/**
 * Hosts the GPU renderer. The game thread fills [acquire]d frames and [submit]s them; the GL thread draws the most
 * recent one. Three frames rotate so neither side ever waits for or touches the other's data.
 * [onFailure] runs on the main thread if GLES 3 can't be used, so the caller can fall back to the Canvas renderer.
 */
class GlWorldView(context: Context, private val onFailure: () -> Unit) : GLSurfaceView(context) {
    private val frames = arrayOf(GpuFrame(), GpuFrame(), GpuFrame())
    private val lock = Any()
    private var build = 0
    private var pending = -1
    private var spare = 1
    private var draw = 2
    private var version = 0L

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(MsaaChooser())
        preserveEGLContextOnPause = true
        setRenderer(Renderer())
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    /** The frame the game thread may fill now. */
    fun acquire(): GpuFrame = frames[build]

    /** Publishes the filled frame and asks the GL thread to draw it. */
    fun submit() {
        frames[build].version = ++version
        synchronized(lock) {
            if (pending >= 0) { val t = pending; pending = build; build = t }
            else { pending = build; build = spare; spare = -1 }
        }
        requestRender()
    }

    private fun take(): GpuFrame {
        synchronized(lock) {
            if (pending >= 0) { spare = draw; draw = pending; pending = -1 }
            return frames[draw]
        }
    }

    companion object {
        fun supported(context: Context): Boolean {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
            return am.deviceConfigurationInfo.reqGlEsVersion >= 0x30000
        }
    }

    /**
     * Picks an RGB888 + 24-bit depth config with 4x MSAA, falling back to fewer samples, a 16-bit depth and finally
     * whatever the device offers, so choosing a config never crashes the GL thread.
     */
    private class MsaaChooser : EGLConfigChooser {
        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
            for (samples in intArrayOf(4, 2, 0)) for (depth in intArrayOf(24, 16)) {
                val spec = intArrayOf(
                    EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, depth, EGL10.EGL_RENDERABLE_TYPE, 0x40 /* EGL_OPENGL_ES3_BIT_KHR */,
                    EGL10.EGL_SAMPLE_BUFFERS, if (samples > 0) 1 else 0, EGL10.EGL_SAMPLES, samples, EGL10.EGL_NONE,
                )
                pick(egl, display, spec)?.let { return it }
            }
            pick(egl, display, intArrayOf(EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_NONE))?.let { return it }
            val n = IntArray(1)
            egl.eglGetConfigs(display, null, 0, n)
            val all = arrayOfNulls<EGLConfig>(maxOf(1, n[0]))
            egl.eglGetConfigs(display, all, all.size, n)
            return all.first()!!
        }

        private fun pick(egl: EGL10, display: EGLDisplay, spec: IntArray): EGLConfig? {
            val n = IntArray(1)
            if (!egl.eglChooseConfig(display, spec, null, 0, n) || n[0] <= 0) return null
            val configs = arrayOfNulls<EGLConfig>(n[0])
            if (!egl.eglChooseConfig(display, spec, configs, n[0], n)) return null
            return configs.firstOrNull()
        }
    }

    private inner class Renderer : GLSurfaceView.Renderer {
        private var world = 0
        private var sky = 0
        private var shadowProg = 0
        private var shadowLight = -1
        private val shadowTex = IntArray(1)
        private val shadowFbo = IntArray(1)
        private var shadowAlloc = 0
        private var viewW = 1
        private var viewH = 1
        private val vbo = IntArray(4)
        private val ibo = IntArray(3)
        private var vbuf: IntBuffer = direct(1 shl 18)
        private var ibuf: IntBuffer = direct(1 shl 17)
        private val counts = IntArray(3)
        private var lastUploaded = -1L
        private var failed = false
        private val u = HashMap<String, Int>()
        private val us = HashMap<String, Int>()

        private fun direct(ints: Int): IntBuffer = ByteBuffer.allocateDirect(ints * 4).order(ByteOrder.nativeOrder()).asIntBuffer()

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            try {
                world = program(Shaders.WORLD_VS, Shaders.WORLD_FS)
                sky = program(Shaders.SKY_VS, Shaders.SKY_FS)
                shadowProg = program(Shaders.SHADOW_VS, Shaders.SHADOW_FS)
                shadowLight = GLES30.glGetUniformLocation(shadowProg, "uLight")
                GLES30.glGenTextures(1, shadowTex, 0)
                GLES30.glGenFramebuffers(1, shadowFbo, 0)
                shadowAlloc = 0
                for (name in listOf("uCam", "uRight", "uUp", "uFwd", "uProj", "uViewport", "uNearFar", "uSunDir", "uSunColor", "uSkyAmb", "uGroundAmb",
                        "uFogColor", "uFog", "uMisc", "uLight", "uShadow", "uSkyTop", "uSkyHorizon", "uTime", "uShadowMap"))
                    u[name] = GLES30.glGetUniformLocation(world, name)
                for (name in listOf("uRight", "uUp", "uFwd", "uProj", "uViewport", "uSkyTop", "uSkyHorizon", "uFogColor", "uSunDir", "uSunColor", "uMisc"))
                    us[name] = GLES30.glGetUniformLocation(sky, name)
                GLES30.glGenBuffers(4, vbo, 0)
                GLES30.glGenBuffers(3, ibo, 0)
                // fullscreen triangle for the sky
                val tri = ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f))
                tri.position(0)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[3])
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 24, tri, GLES30.GL_STATIC_DRAW)
                lastUploaded = -1L
                failed = false
            } catch (t: Throwable) {
                Log.e("HustleGL", "GL init failed", t)
                fail()
            }
        }

        private fun fail() {
            if (failed) return
            failed = true
            Handler(Looper.getMainLooper()).post(onFailure)
        }

        private fun shader(type: Int, src: String): Int {
            val s = GLES30.glCreateShader(type)
            GLES30.glShaderSource(s, src)
            GLES30.glCompileShader(s)
            val ok = IntArray(1)
            GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) throw IllegalStateException("shader: " + GLES30.glGetShaderInfoLog(s))
            return s
        }

        private fun program(vs: String, fs: String): Int {
            val p = GLES30.glCreateProgram()
            GLES30.glAttachShader(p, shader(GLES30.GL_VERTEX_SHADER, vs))
            GLES30.glAttachShader(p, shader(GLES30.GL_FRAGMENT_SHADER, fs))
            GLES30.glLinkProgram(p)
            val ok = IntArray(1)
            GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
            if (ok[0] == 0) throw IllegalStateException("link: " + GLES30.glGetProgramInfoLog(p))
            return p
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            viewW = width; viewH = height
            GLES30.glViewport(0, 0, width, height)
        }

        /** (Re)creates the depth texture the sun renders into. */
        private fun ensureShadowMap(size: Int) {
            if (shadowAlloc == size) return
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadowTex[0])
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_DEPTH_COMPONENT24, size, size, 0, GLES30.GL_DEPTH_COMPONENT, GLES30.GL_UNSIGNED_INT, null)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_MODE, GLES30.GL_COMPARE_REF_TO_TEXTURE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_FUNC, GLES30.GL_LEQUAL)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFbo[0])
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_TEXTURE_2D, shadowTex[0], 0)
            GLES30.glDrawBuffers(1, intArrayOf(GLES30.GL_NONE), 0)
            GLES30.glReadBuffer(GLES30.GL_NONE)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            shadowAlloc = size
        }

        private fun shadowPass(f: GpuFrame) {
            ensureShadowMap(f.shadowSize)
            if (!f.shadowOn || counts[GpuFrame.OPAQUE] == 0) return
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFbo[0])
            GLES30.glViewport(0, 0, f.shadowSize, f.shadowSize)
            GLES30.glDepthMask(true)
            GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthFunc(GLES30.GL_LEQUAL)
            GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
            GLES30.glPolygonOffset(2f, 4f)
            GLES30.glUseProgram(shadowProg)
            GLES30.glUniformMatrix4fv(shadowLight, 1, false, f.lightMat, 0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[GpuFrame.OPAQUE])
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo[GpuFrame.OPAQUE])
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, GpuFrame.STRIDE * 4, 0)
            for (a in 1..3) GLES30.glDisableVertexAttribArray(a)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, counts[GpuFrame.OPAQUE], GLES30.GL_UNSIGNED_INT, 0)
            GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glViewport(0, 0, viewW, viewH)
        }

        override fun onDrawFrame(gl: GL10?) {
            if (failed) return
            val f = take()
            if (f.version != lastUploaded) upload(f)
            shadowPass(f)
            val cc = f.clearColor
            GLES30.glClearColor(((cc shr 16) and 255) / 255f, ((cc shr 8) and 255) / 255f, (cc and 255) / 255f, 1f)
            GLES30.glDepthMask(true)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
            if (f.mode == GpuFrame.MODE_CITY) drawSky(f)
            GLES30.glUseProgram(world)
            setWorldUniforms(f)
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            // ground layers in painter's order
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthMask(false)
            drawStream(GpuFrame.GROUND)
            // solid geometry
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthFunc(GLES30.GL_LEQUAL)
            GLES30.glDepthMask(true)
            drawStream(GpuFrame.OPAQUE)
            // see-through geometry in emission (back to front) order
            GLES30.glDepthMask(false)
            drawStream(GpuFrame.TRANSPARENT)
            GLES30.glDepthMask(true)
            val err = GLES30.glGetError()
            if (err != GLES30.GL_NO_ERROR) Log.w("HustleGL", "GL error $err")
        }

        private fun upload(f: GpuFrame) {
            for (i in 0 until 3) {
                val s = f.streams[i]
                counts[i] = s.ni
                if (s.ni == 0) continue
                val nInts = s.nv * GpuFrame.STRIDE
                if (vbuf.capacity() < nInts) vbuf = direct(maxOf(nInts, vbuf.capacity() * 2))
                vbuf.clear(); vbuf.put(s.v, 0, nInts); vbuf.position(0)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[i])
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, nInts * 4, vbuf, GLES30.GL_STREAM_DRAW)
                if (ibuf.capacity() < s.ni) ibuf = direct(maxOf(s.ni, ibuf.capacity() * 2))
                ibuf.clear(); ibuf.put(s.idx, 0, s.ni); ibuf.position(0)
                GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo[i])
                GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, s.ni * 4, ibuf, GLES30.GL_STREAM_DRAW)
            }
            lastUploaded = f.version
        }

        private fun drawStream(i: Int) {
            if (counts[i] == 0) return
            val stride = GpuFrame.STRIDE * 4
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[i])
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo[i])
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 12)
            GLES30.glEnableVertexAttribArray(2)
            GLES30.glVertexAttribPointer(2, 4, GLES30.GL_UNSIGNED_BYTE, true, stride, 24)
            GLES30.glEnableVertexAttribArray(3)
            GLES30.glVertexAttribPointer(3, 1, GLES30.GL_FLOAT, false, stride, 28)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, counts[i], GLES30.GL_UNSIGNED_INT, 0)
        }

        private fun v3(map: HashMap<String, Int>, name: String, a: FloatArray) { map[name]?.let { GLES30.glUniform3f(it, a[0], a[1], a[2]) } }

        private fun setWorldUniforms(f: GpuFrame) {
            v3(u, "uCam", f.cam); v3(u, "uRight", f.right); v3(u, "uUp", f.up); v3(u, "uFwd", f.fwd)
            GLES30.glUniform3f(u["uProj"]!!, f.focal, f.centerX, f.centerY)
            GLES30.glUniform2f(u["uViewport"]!!, f.width, f.height)
            GLES30.glUniform2f(u["uNearFar"]!!, f.near, f.far)
            v3(u, "uSunDir", f.sunDir); v3(u, "uSunColor", f.sunColor); v3(u, "uSkyAmb", f.skyAmbient); v3(u, "uGroundAmb", f.groundAmbient)
            v3(u, "uFogColor", f.fogColor)
            GLES30.glUniform3f(u["uFog"]!!, f.fogStart, f.fogEnd, f.fogAmount)
            GLES30.glUniform4f(u["uMisc"]!!, f.daylight, f.wet, f.exposure, f.mode.toFloat())
            GLES30.glUniformMatrix4fv(u["uLight"]!!, 1, false, f.lightMat, 0)
            GLES30.glUniform4f(u["uShadow"]!!, f.shadowStrength, f.shadowOffset, f.shadowTexel, if (f.shadowOn) 1f else 0f)
            v3(u, "uSkyTop", f.skyTop); v3(u, "uSkyHorizon", f.skyHorizon)
            GLES30.glUniform1f(u["uTime"]!!, f.time)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadowTex[0])
            GLES30.glUniform1i(u["uShadowMap"]!!, 0)
        }

        private fun drawSky(f: GpuFrame) {
            GLES30.glUseProgram(sky)
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthMask(false)
            GLES30.glDisable(GLES30.GL_BLEND)
            v3(us, "uRight", f.right); v3(us, "uUp", f.up); v3(us, "uFwd", f.fwd)
            GLES30.glUniform3f(us["uProj"]!!, f.focal, f.centerX, f.centerY)
            GLES30.glUniform2f(us["uViewport"]!!, f.width, f.height)
            v3(us, "uSkyTop", f.skyTop); v3(us, "uSkyHorizon", f.skyHorizon); v3(us, "uFogColor", f.fogColor)
            v3(us, "uSunDir", f.sunDir); v3(us, "uSunColor", f.sunColor)
            GLES30.glUniform4f(us["uMisc"]!!, f.daylight, f.wet, f.exposure, f.mode.toFloat())
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[3])
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
            for (a in 1..3) GLES30.glDisableVertexAttribArray(a)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        }
    }
}
