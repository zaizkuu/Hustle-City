package com.hustlecity.game.render

import android.util.Log
import java.io.DataInputStream

/**
 * Car models from the Cars Pack by Quaternius (CC0), baked by tools/assets/cars.py into resources/models/cars.bin:
 * one unit long, wide and high in the car's own frame (forward, right, up), so a model stretches to any car's size,
 * and each face tagged with what it is rather than a colour, so the paint takes the car's own.
 */
object CarModels {
    const val PAINT = 0
    const val PAINT2 = 1
    const val GLASS = 2
    const val HEAD = 3
    const val TAIL = 4
    const val DARK = 5
    const val TRIM = 6

    /**
     * Polygon p has [sizes][p] corners and role [roles][p]; its corners are the next entries of [corners], which
     * index the vertex arrays [f] (forward, -0.5..0.5), [r] (right, -0.5..0.5) and [u] (up, 0..1).
     */
    class Model(val name: String, val roles: ByteArray, val sizes: ByteArray, val corners: IntArray, val f: FloatArray, val r: FloatArray, val u: FloatArray) {
        val polygons get() = roles.size
        val maxCorners: Int = sizes.maxOfOrNull { it.toInt() and 255 } ?: 0
    }

    private val models: Map<String, Model> by lazy { load() }

    fun get(name: String): Model? = models[name]

    private fun load(): Map<String, Model> = runCatching {
        val stream = CarModels::class.java.getResourceAsStream("/models/cars.bin") ?: error("missing cars.bin")
        DataInputStream(stream.buffered()).use { d ->
            val magic = ByteArray(4).also { d.readFully(it) }
            check(String(magic) == "HCM1") { "not a car model file" }
            val out = HashMap<String, Model>()
            repeat(d.readUnsignedShort()) {
                val name = ByteArray(d.readUnsignedShort()).also { d.readFully(it) }.decodeToString()
                val np = d.readUnsignedShort(); val nv = d.readUnsignedShort()
                val roles = ByteArray(np); val sizes = ByteArray(np)
                var total = 0
                for (p in 0 until np) { roles[p] = d.readByte(); sizes[p] = d.readByte(); total += sizes[p].toInt() and 255 }
                val corners = IntArray(total) { d.readUnsignedShort() }
                val f = FloatArray(nv); val r = FloatArray(nv); val u = FloatArray(nv)
                for (v in 0 until nv) { f[v] = d.readShort() / 20000f; r[v] = d.readShort() / 20000f; u[v] = d.readShort() / 20000f }
                out[name] = Model(name, roles, sizes, corners, f, r, u)
            }
            out
        }
    }.onFailure { Log.w("HustleCars", "car models unavailable: $it") }.getOrDefault(emptyMap())
}
