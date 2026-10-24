package com.hustlecity.game.render

import android.content.res.AssetManager
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Furniture, product and street prop meshes built by tools/models from free (CC0) model packs and our own models,
 * stored as HCM1 files in assets/models/ (products in assets/models/products/, props in assets/models/props/). The GPU
 * renderer draws them in place of the box models; the classic renderer keeps the boxes. A furniture mesh sits in its
 * item's own tile units: x across the footprint, y deep, z up in meters, front toward +y. A product mesh is in meters,
 * centered on its middle, front toward +y. A prop mesh is in meters around where it stands, +y toward the road.
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
    ) {
        /**
         * Per vertex, for drawing it tinted and lit (see GpuFrame.mesh): [PART_PAINT] takes a car's color (white or grey
         * paint), [PART_LENS] is a light that's only lit when it's on, [PART_RED_LENS] one that's also lit when braking.
         */
        val parts: ByteArray by lazy {
            ByteArray(vertices) { i ->
                val f = java.lang.Float.intBitsToFloat(flags[i]).toInt()
                val c = rgba[i]
                val r = c and 255; val g = (c shr 8) and 255; val b = (c shr 16) and 255
                when {
                    f and 1 == 1 -> if (r > 150 && g < 90 && b < 90) PART_RED_LENS else PART_LENS
                    // car paint is modelled white or grey (metal and chrome share the surface, in their own colors)
                    f shr 1 == MAT_PAINT && r == g && g == b -> PART_PAINT
                    else -> 0
                }.toByte()
            }
        }
    }

    const val PART_PAINT: Byte = 1
    const val PART_LENS: Byte = 2
    const val PART_RED_LENS: Byte = 3
    /** The paint surface's number in the model files (tools/models/build_models.py). */
    private const val MAT_PAINT = 16

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

    private val products = HashMap<String, Array<Mesh>>()
    private val props = HashMap<String, Array<Mesh>>()
    private val cars = HashMap<String, Array<Mesh>>()

    /**
     * The looks of product [pid] (models/products/<pid>.hcm, then <pid>.1.hcm, <pid>.2.hcm...: neighboring slots of a
     * display take turns), or null when it has no model.
     */
    fun product(pid: String): Array<Mesh>? = looks("products", pid, products)

    /** The looks of street prop [id] (models/props/<id>.hcm and its variants), or null when it has no model. */
    fun prop(id: String): Array<Mesh>? = looks("props", id, props)

    /** Car model [id] (models/cars/<id>.hcm: sedan, hatchback, taxi, pickup, van, sports, suv, box_truck), or null. */
    fun car(id: String): Mesh? = looks("cars", id, cars)?.get(0)

    private fun looks(folder: String, id: String, cache: HashMap<String, Array<Mesh>>): Array<Mesh>? {
        if (!enabled) return null
        val am = assets ?: return null
        synchronized(cache) {
            val known = cache[id]
            if (known != null) return if (known.isEmpty()) null else known
            val looks = ArrayList<Mesh>()
            while (true) {
                val name = if (looks.isEmpty()) "models/$folder/$id.hcm" else "models/$folder/$id.${looks.size}.hcm"
                looks += runCatching { am.open(name).use { read(it.readBytes()) } }.getOrNull() ?: break
            }
            val arr = looks.toTypedArray()
            cache[id] = arr
            return if (arr.isEmpty()) null else arr
        }
    }

    /** Ids that have a model file. */
    fun ids(): List<String> = assets?.list("models")?.filter { it.endsWith(".hcm") && '/' !in it }?.map { it.removeSuffix(".hcm") } ?: emptyList()

    /** Product ids that have a model (each once, however many looks it has). */
    fun productIds(): List<String> =
        assets?.list("models/products")?.filter { it.endsWith(".hcm") }?.map { it.substringAfterLast('/').removeSuffix(".hcm").substringBefore('.') }?.distinct() ?: emptyList()

    /** Street prop ids that have a model. */
    fun propIds(): List<String> =
        assets?.list("models/props")?.filter { it.endsWith(".hcm") }?.map { it.substringAfterLast('/').removeSuffix(".hcm").substringBefore('.') }?.distinct() ?: emptyList()

    fun clear() {
        synchronized(cache) { cache.clear() }
        synchronized(products) { products.clear() }
        synchronized(props) { props.clear() }
        synchronized(cars) { cars.clear() }
    }

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
