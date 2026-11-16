package com.hustlecity.game.render

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.sqrt

/**
 * One draw part of a model at one level of detail: vertices in the model's own frame (x right, y forward, z up,
 * meters), ready to be appended to a [com.hustlecity.game.render.gpu.GpuFrame].
 *
 * [rgba] is each vertex's color in the byte order GL reads. A vertex with a [slot] from 1 to 7 has no color of its
 * own: the caller tints it (a person's shirt, a car's paint), and [rgba] then holds a shade to multiply the tint by.
 * Slots from [LIGHT_SLOT] up are lights in their own color (a sign, a window of a train) that shine only when the
 * caller's glow mask has their bit: after dark. [mat] is the shader material times two, plus one for a light that
 * is always on.
 */
class MeshPart(
    val nv: Int, val ni: Int, val pos: FloatArray, val nrm: FloatArray, val rgba: IntArray, val mat: ByteArray, val slot: ByteArray,
    val idx: ShortArray,
) {
    companion object {
        /** The first slot that keeps its own color and only switches its glow (see the class comment). */
        const val LIGHT_SLOT = 8
        /** A glow mask that lights every [LIGHT_SLOT]: what models pass after dark. */
        const val NIGHT_LIGHTS = 1 shl LIGHT_SLOT
    }
}

/** A model at one level of detail: its solid part and its see-through part (glass). */
class MeshLevel(val solid: MeshPart, val clear: MeshPart) {
    val triangles: Int get() = (solid.ni + clear.ni) / 3
}

/**
 * A model from the Blender library (tools/blender), with [levels] from full detail (0) down. [meta] holds the few
 * numbers some models carry besides their shape (a building's design footprint and wall height, a wheel's hub).
 */
class Mesh(
    val name: String,
    val minX: Float, val minY: Float, val minZ: Float, val maxX: Float, val maxY: Float, val maxZ: Float,
    val levels: Array<MeshLevel>,
    var meta: FloatArray = FloatArray(0),
) {
    val width: Float get() = maxX - minX
    val length: Float get() = maxY - minY
    val height: Float get() = maxZ - minZ
    val centerX: Float get() = (minX + maxX) / 2
    val centerY: Float get() = (minY + maxY) / 2
    val centerZ: Float get() = (minZ + maxZ) / 2
    /** Radius of a sphere round the middle of the model that holds all of it. */
    val radius: Float = sqrt(width * width + length * length + height * height) / 2

    fun level(i: Int): MeshLevel = levels[i.coerceIn(0, levels.size - 1)]
}

/**
 * The models the game draws, read once from assets/models.hcm (written by tools/blender/export_game.py, which also
 * documents the file). Until [load] has run, [get] finds nothing and every painter keeps its own box model: the
 * models are an upgrade on top of the procedural look, never something the game can't draw without.
 */
object MeshLib {
    @Volatile private var meshes: Map<String, Mesh> = emptyMap()

    /** Goes up whenever the set of models changes, so painters know to look their models up again. */
    @Volatile var version = 0
        private set

    val ready: Boolean get() = meshes.isNotEmpty()

    fun get(name: String): Mesh? = meshes[name]

    val names: Set<String> get() = meshes.keys

    fun clear() { meshes = emptyMap(); version++ }

    /** Reads a model pack. Throws if it isn't one. */
    fun load(input: InputStream) {
        val buf = ByteBuffer.wrap(input.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.get().toInt() == 'H'.code && buf.get().toInt() == 'C'.code && buf.get().toInt() == 'M'.code && buf.get().toInt() == '1'.code) {
            "not a model pack"
        }
        val count = buf.getShort().toInt() and 0xFFFF
        val out = HashMap<String, Mesh>(count * 2)
        for (m in 0 until count) {
            val nameBytes = ByteArray(buf.get().toInt() and 0xFF)
            buf.get(nameBytes)
            val name = String(nameBytes, Charsets.US_ASCII)
            val b = FloatArray(6) { buf.getFloat() }
            val nLevels = buf.get().toInt() and 0xFF
            val levels = Array(nLevels) { MeshLevel(part(buf), part(buf)) }
            out[name] = Mesh(name, b[0], b[1], b[2], b[3], b[4], b[5], levels)
        }
        // then, in newer packs, the numbers some models carry
        if (buf.remaining() >= 6 && buf.get(buf.position()).toInt() == 'M'.code) {
            buf.get(); buf.get(); buf.get(); buf.get()
            val n = buf.getShort().toInt() and 0xFFFF
            for (k in 0 until n) {
                val nameBytes = ByteArray(buf.get().toInt() and 0xFF)
                buf.get(nameBytes)
                val vals = FloatArray(buf.get().toInt() and 0xFF) { buf.getFloat() }
                out[String(nameBytes, Charsets.US_ASCII)]?.meta = vals
            }
        }
        meshes = out
        version++
    }

    private fun part(buf: ByteBuffer): MeshPart {
        val nv = buf.getInt()
        val ni = buf.getInt()
        val pos = FloatArray(nv * 3)
        for (i in pos.indices) pos[i] = buf.getFloat()
        val nrm = FloatArray(nv * 3)
        for (i in nrm.indices) nrm[i] = buf.get() / 127f
        val rgba = IntArray(nv)
        for (i in 0 until nv) {
            val r = buf.get().toInt() and 0xFF; val g = buf.get().toInt() and 0xFF; val b = buf.get().toInt() and 0xFF; val a = buf.get().toInt() and 0xFF
            rgba[i] = (a shl 24) or (b shl 16) or (g shl 8) or r
        }
        val mat = ByteArray(nv)
        buf.get(mat)
        val slot = ByteArray(nv)
        buf.get(slot)
        val idx = ShortArray(ni)
        for (i in 0 until ni) idx[i] = buf.getShort()
        return MeshPart(nv, ni, pos, nrm, rgba, mat, slot, idx)
    }

    /** Largest of three scale factors, for sizing a culling sphere. */
    fun maxScale(sx: Float, sy: Float, sz: Float): Float = max(sx, max(sy, sz))
}
