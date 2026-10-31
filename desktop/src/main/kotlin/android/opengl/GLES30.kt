package android.opengl

import org.lwjgl.opengl.GL33C
import org.lwjgl.system.MemoryUtil
import java.nio.Buffer
import java.nio.ByteBuffer

/**
 * Desktop stand-in for Android's GLES30, mapping the calls the game's [com.hustlecity.game.render.gpu.GpuRenderer]
 * makes onto desktop OpenGL 3.3 core through LWJGL. The two APIs match call for call; the only differences are the
 * shading language header (GLSL ES 3.00 becomes GLSL 3.30) and that a core context needs a vertex array object bound,
 * which the desktop GL thread does once when it creates its context.
 */
@Suppress("FunctionName", "unused")
object GLES30 {
    const val GL_NO_ERROR = GL33C.GL_NO_ERROR
    const val GL_OUT_OF_MEMORY = GL33C.GL_OUT_OF_MEMORY
    const val GL_NONE = GL33C.GL_NONE
    const val GL_DEPTH_BUFFER_BIT = GL33C.GL_DEPTH_BUFFER_BIT
    const val GL_COLOR_BUFFER_BIT = GL33C.GL_COLOR_BUFFER_BIT
    const val GL_TRIANGLES = GL33C.GL_TRIANGLES
    const val GL_FLOAT = GL33C.GL_FLOAT
    const val GL_UNSIGNED_BYTE = GL33C.GL_UNSIGNED_BYTE
    const val GL_UNSIGNED_INT = GL33C.GL_UNSIGNED_INT
    const val GL_ARRAY_BUFFER = GL33C.GL_ARRAY_BUFFER
    const val GL_ELEMENT_ARRAY_BUFFER = GL33C.GL_ELEMENT_ARRAY_BUFFER
    const val GL_STATIC_DRAW = GL33C.GL_STATIC_DRAW
    const val GL_STREAM_DRAW = GL33C.GL_STREAM_DRAW
    const val GL_DYNAMIC_DRAW = GL33C.GL_DYNAMIC_DRAW
    const val GL_VERTEX_SHADER = GL33C.GL_VERTEX_SHADER
    const val GL_FRAGMENT_SHADER = GL33C.GL_FRAGMENT_SHADER
    const val GL_COMPILE_STATUS = GL33C.GL_COMPILE_STATUS
    const val GL_LINK_STATUS = GL33C.GL_LINK_STATUS
    const val GL_TEXTURE_2D = GL33C.GL_TEXTURE_2D
    const val GL_TEXTURE_2D_ARRAY = GL33C.GL_TEXTURE_2D_ARRAY
    const val GL_TEXTURE0 = GL33C.GL_TEXTURE0
    const val GL_TEXTURE1 = GL33C.GL_TEXTURE1
    const val GL_TEXTURE2 = GL33C.GL_TEXTURE2
    const val GL_TEXTURE_MIN_FILTER = GL33C.GL_TEXTURE_MIN_FILTER
    const val GL_TEXTURE_MAG_FILTER = GL33C.GL_TEXTURE_MAG_FILTER
    const val GL_TEXTURE_WRAP_S = GL33C.GL_TEXTURE_WRAP_S
    const val GL_TEXTURE_WRAP_T = GL33C.GL_TEXTURE_WRAP_T
    const val GL_TEXTURE_COMPARE_MODE = GL33C.GL_TEXTURE_COMPARE_MODE
    const val GL_TEXTURE_COMPARE_FUNC = GL33C.GL_TEXTURE_COMPARE_FUNC
    const val GL_COMPARE_REF_TO_TEXTURE = GL33C.GL_COMPARE_REF_TO_TEXTURE
    const val GL_LINEAR = GL33C.GL_LINEAR
    const val GL_LINEAR_MIPMAP_LINEAR = GL33C.GL_LINEAR_MIPMAP_LINEAR
    const val GL_REPEAT = GL33C.GL_REPEAT
    const val GL_UNPACK_ALIGNMENT = GL33C.GL_UNPACK_ALIGNMENT
    const val GL_NEAREST = GL33C.GL_NEAREST
    const val GL_CLAMP_TO_EDGE = GL33C.GL_CLAMP_TO_EDGE
    const val GL_DEPTH_COMPONENT = GL33C.GL_DEPTH_COMPONENT
    const val GL_DEPTH_COMPONENT24 = GL33C.GL_DEPTH_COMPONENT24
    const val GL_RED = GL33C.GL_RED
    const val GL_R8 = GL33C.GL_R8
    const val GL_RGBA = GL33C.GL_RGBA
    const val GL_RGBA8 = GL33C.GL_RGBA8
    const val GL_FRAMEBUFFER = GL33C.GL_FRAMEBUFFER
    const val GL_DEPTH_ATTACHMENT = GL33C.GL_DEPTH_ATTACHMENT
    const val GL_COLOR_ATTACHMENT0 = GL33C.GL_COLOR_ATTACHMENT0
    const val GL_DEPTH_TEST = GL33C.GL_DEPTH_TEST
    const val GL_BLEND = GL33C.GL_BLEND
    const val GL_CULL_FACE = GL33C.GL_CULL_FACE
    const val GL_POLYGON_OFFSET_FILL = GL33C.GL_POLYGON_OFFSET_FILL
    const val GL_LEQUAL = GL33C.GL_LEQUAL
    const val GL_LESS = GL33C.GL_LESS
    const val GL_SRC_ALPHA = GL33C.GL_SRC_ALPHA
    const val GL_ONE_MINUS_SRC_ALPHA = GL33C.GL_ONE_MINUS_SRC_ALPHA
    const val GL_ONE = GL33C.GL_ONE

    // shaders and programs

    @JvmStatic fun glCreateShader(type: Int): Int = GL33C.glCreateShader(type)
    @JvmStatic fun glShaderSource(shader: Int, source: String) = GL33C.glShaderSource(shader, desktopGlsl(source))
    @JvmStatic fun glCompileShader(shader: Int) = GL33C.glCompileShader(shader)
    @JvmStatic fun glGetShaderiv(shader: Int, pname: Int, params: IntArray, offset: Int) { params[offset] = GL33C.glGetShaderi(shader, pname) }
    @JvmStatic fun glGetShaderInfoLog(shader: Int): String = GL33C.glGetShaderInfoLog(shader)
    @JvmStatic fun glDeleteShader(shader: Int) = GL33C.glDeleteShader(shader)
    @JvmStatic fun glCreateProgram(): Int = GL33C.glCreateProgram()
    @JvmStatic fun glAttachShader(program: Int, shader: Int) = GL33C.glAttachShader(program, shader)
    @JvmStatic fun glLinkProgram(program: Int) = GL33C.glLinkProgram(program)
    @JvmStatic fun glGetProgramiv(program: Int, pname: Int, params: IntArray, offset: Int) { params[offset] = GL33C.glGetProgrami(program, pname) }
    @JvmStatic fun glGetProgramInfoLog(program: Int): String = GL33C.glGetProgramInfoLog(program)
    @JvmStatic fun glUseProgram(program: Int) = GL33C.glUseProgram(program)
    @JvmStatic fun glDeleteProgram(program: Int) = GL33C.glDeleteProgram(program)
    @JvmStatic fun glGetUniformLocation(program: Int, name: String): Int = GL33C.glGetUniformLocation(program, name)

    // uniforms

    @JvmStatic fun glUniform1i(location: Int, x: Int) = GL33C.glUniform1i(location, x)
    @JvmStatic fun glUniform1f(location: Int, x: Float) = GL33C.glUniform1f(location, x)
    @JvmStatic fun glUniform2f(location: Int, x: Float, y: Float) = GL33C.glUniform2f(location, x, y)
    @JvmStatic fun glUniform3f(location: Int, x: Float, y: Float, z: Float) = GL33C.glUniform3f(location, x, y, z)
    @JvmStatic fun glUniform4f(location: Int, x: Float, y: Float, z: Float, w: Float) = GL33C.glUniform4f(location, x, y, z, w)
    @JvmStatic fun glUniformMatrix4fv(location: Int, count: Int, transpose: Boolean, value: FloatArray, offset: Int) =
        GL33C.glUniformMatrix4fv(location, transpose, if (offset == 0 && value.size == count * 16) value else value.copyOfRange(offset, offset + count * 16))

    // buffers and vertex attributes

    @JvmStatic fun glGenBuffers(n: Int, buffers: IntArray, offset: Int) { for (i in 0 until n) buffers[offset + i] = GL33C.glGenBuffers() }
    @JvmStatic fun glDeleteBuffers(n: Int, buffers: IntArray, offset: Int) { for (i in 0 until n) GL33C.glDeleteBuffers(buffers[offset + i]) }
    @JvmStatic fun glBindBuffer(target: Int, buffer: Int) = GL33C.glBindBuffer(target, buffer)
    @JvmStatic fun glBufferData(target: Int, size: Int, data: Buffer?, usage: Int) =
        GL33C.nglBufferData(target, size.toLong(), if (data == null) MemoryUtil.NULL else MemoryUtil.memAddress0(data) + byteOffset(data), usage)
    @JvmStatic fun glEnableVertexAttribArray(index: Int) = GL33C.glEnableVertexAttribArray(index)
    @JvmStatic fun glDisableVertexAttribArray(index: Int) = GL33C.glDisableVertexAttribArray(index)
    @JvmStatic fun glVertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int) =
        GL33C.glVertexAttribPointer(index, size, type, normalized, stride, offset.toLong())

    // drawing

    @JvmStatic fun glDrawElements(mode: Int, count: Int, type: Int, offset: Int) = GL33C.glDrawElements(mode, count, type, offset.toLong())
    @JvmStatic fun glDrawArrays(mode: Int, first: Int, count: Int) = GL33C.glDrawArrays(mode, first, count)
    @JvmStatic fun glViewport(x: Int, y: Int, width: Int, height: Int) = GL33C.glViewport(x, y, width, height)
    @JvmStatic fun glClearColor(r: Float, g: Float, b: Float, a: Float) = GL33C.glClearColor(r, g, b, a)
    @JvmStatic fun glClear(mask: Int) = GL33C.glClear(mask)
    @JvmStatic fun glEnable(cap: Int) = GL33C.glEnable(cap)
    @JvmStatic fun glDisable(cap: Int) = GL33C.glDisable(cap)
    @JvmStatic fun glDepthMask(flag: Boolean) = GL33C.glDepthMask(flag)
    @JvmStatic fun glDepthFunc(func: Int) = GL33C.glDepthFunc(func)
    @JvmStatic fun glBlendFunc(sfactor: Int, dfactor: Int) = GL33C.glBlendFunc(sfactor, dfactor)
    @JvmStatic fun glPolygonOffset(factor: Float, units: Float) = GL33C.glPolygonOffset(factor, units)
    @JvmStatic fun glGetError(): Int = GL33C.glGetError()

    // textures and framebuffers

    @JvmStatic fun glGenTextures(n: Int, textures: IntArray, offset: Int) { for (i in 0 until n) textures[offset + i] = GL33C.glGenTextures() }
    @JvmStatic fun glDeleteTextures(n: Int, textures: IntArray, offset: Int) { for (i in 0 until n) GL33C.glDeleteTextures(textures[offset + i]) }
    @JvmStatic fun glActiveTexture(texture: Int) = GL33C.glActiveTexture(texture)
    @JvmStatic fun glBindTexture(target: Int, texture: Int) = GL33C.glBindTexture(target, texture)
    @JvmStatic fun glTexParameteri(target: Int, pname: Int, param: Int) = GL33C.glTexParameteri(target, pname, param)
    @JvmStatic fun glTexImage2D(target: Int, level: Int, internalformat: Int, width: Int, height: Int, border: Int, format: Int, type: Int, pixels: Buffer?) =
        GL33C.nglTexImage2D(target, level, internalformat, width, height, border, format, type,
            if (pixels == null) MemoryUtil.NULL else MemoryUtil.memAddress0(pixels) + byteOffset(pixels))
    @JvmStatic fun glTexImage3D(target: Int, level: Int, internalformat: Int, width: Int, height: Int, depth: Int, border: Int, format: Int, type: Int, pixels: Buffer?) =
        GL33C.nglTexImage3D(target, level, internalformat, width, height, depth, border, format, type,
            if (pixels == null) MemoryUtil.NULL else MemoryUtil.memAddress0(pixels) + byteOffset(pixels))
    @JvmStatic fun glGenerateMipmap(target: Int) = GL33C.glGenerateMipmap(target)
    @JvmStatic fun glPixelStorei(pname: Int, param: Int) = GL33C.glPixelStorei(pname, param)
    @JvmStatic fun glGenFramebuffers(n: Int, framebuffers: IntArray, offset: Int) { for (i in 0 until n) framebuffers[offset + i] = GL33C.glGenFramebuffers() }
    @JvmStatic fun glDeleteFramebuffers(n: Int, framebuffers: IntArray, offset: Int) { for (i in 0 until n) GL33C.glDeleteFramebuffers(framebuffers[offset + i]) }
    @JvmStatic fun glBindFramebuffer(target: Int, framebuffer: Int) = GL33C.glBindFramebuffer(target, framebuffer)
    @JvmStatic fun glFramebufferTexture2D(target: Int, attachment: Int, textarget: Int, texture: Int, level: Int) =
        GL33C.glFramebufferTexture2D(target, attachment, textarget, texture, level)
    @JvmStatic fun glDrawBuffers(n: Int, bufs: IntArray, offset: Int) =
        GL33C.glDrawBuffers(if (offset == 0 && bufs.size == n) bufs else bufs.copyOfRange(offset, offset + n))
    @JvmStatic fun glReadBuffer(mode: Int) = GL33C.glReadBuffer(mode)

    /** GLSL ES 3.00 to GLSL 3.30: the shaders only use what both languages share, so only the header changes. */
    fun desktopGlsl(source: String): String = source.replaceFirst("#version 300 es", "#version 330 core")

    /** Bytes from the start of a direct buffer's memory to its position. */
    private fun byteOffset(b: Buffer): Long = b.position().toLong() * when (b) {
        is ByteBuffer -> 1
        is java.nio.ShortBuffer, is java.nio.CharBuffer -> 2
        is java.nio.LongBuffer, is java.nio.DoubleBuffer -> 8
        else -> 4
    }
}
