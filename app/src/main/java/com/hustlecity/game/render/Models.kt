package com.hustlecity.game.render

import android.content.res.AssetManager
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Furniture meshes built by tools/models from free (CC0) model packs and our own models, stored as HCM1 files in
 * assets/models/. The GPU renderer draws them in place of the box models; the classic renderer keeps the boxes. A mesh
 * sits in its item's own tile units: x across the footprint, y deep, z up in meters, front toward +y.
 */
object Models {
    /**
     * One mesh, ready to copy into a frame: positions and normals as floats, colors already in the GPU's RGBA byte
     * order, and the per-vertex flags (material and glow) as the float bits the vertex stream wants.
     */
    class Mesh(
        val vertices: Int,
        val pos: FloatArray,
        val nrm: FloatArray,
        val rgba: IntArray,
        val flags: IntArray,
        val idx: IntArray,
        /** Bounds: min x, y, z, max x, y, z. */
        val bounds: FloatArray,
    )

    /** Where the model files are read from (set by the game engine when it has an Android context). */
    @Volatile var assets: AssetManager? = null

    /** Turned off, everything draws as boxes (for comparison shots). */
    @Volatile var enabled = true

    private val cache = HashMap<String, Mesh?>()

    /** The mesh for a furniture id, or null when there's none (or no assets to read it from). */
    fun get(id: String): Mesh? {
        if (!enabled) return null
        val am = assets ?: return null
        synchronized(cache) {
            if (cache.containsKey(id)) return cache[id]
            val m = runCatching { am.open("models/$id.hcm").use { read(it.readBytes()) } }.getOrNull()
            cache[id] = m
            return m
        }
    }

    /** Ids that have a model file. */
    fun ids(): List<String> = assets?.list("models")?.filter { it.endsWith(".hcm") }?.map { it.removeSuffix(".hcm") } ?: emptyList()

    fun clear() = synchronized(cache) { cache.clear() }

    /** Parses an HCM1 file (see tools/models/build_models.py). */
    fun read(bytes: ByteArray): Mesh {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size >= 36 && bytes[0] == 'H'.code.toByte() && bytes[1] == 'C'.code.toByte() && bytes[2] == 'M'.code.toByte() && bytes[3] == '1'.code.toByte()) { "not an HCM1 model" }
        b.position(4)
        val nv = b.int; val ni = b.int
        val bounds = FloatArray(6) { b.float }
        val pos = FloatArray(nv * 3); val nrm = FloatArray(nv * 3); val rgba = IntArray(nv); val flags = IntArray(nv)
        for (i in 0 until nv) {
            pos[i * 3] = b.float; pos[i * 3 + 1] = b.float; pos[i * 3 + 2] = b.float
            nrm[i * 3] = b.get() / 127f; nrm[i * 3 + 1] = b.get() / 127f; nrm[i * 3 + 2] = b.get() / 127f
            flags[i] = java.lang.Float.floatToRawIntBits((b.get().toInt() and 255).toFloat())
            val r = b.get().toInt() and 255; val g = b.get().toInt() and 255; val bl = b.get().toInt() and 255; val a = b.get().toInt() and 255
            // GL reads the bytes in memory order: r, g, b, a
            rgba[i] = (a shl 24) or (bl shl 16) or (g shl 8) or r
        }
        val idx = IntArray(ni) { b.short.toInt() and 0xFFFF }
        return Mesh(nv, pos, nrm, rgba, flags, idx, bounds)
    }
}
