package com.hustlecity.game.render.gpu

import android.graphics.BitmapFactory
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The HD renderer's photo materials: CC0 scans from ambientCG baked by tools/assets/textures.py into
 * resources/textures. Each material has a detail map (its brightness pattern, which the game's own colours are
 * multiplied by) and a bump map (normal tilt and roughness); they load as layers of two texture arrays.
 */
object PhotoMaterials {
    /** The layers in order; Shaders.WORLD_FS samples them by these indices. */
    val NAMES = listOf("brick", "stone", "asphalt", "slabs", "grass", "wood", "tiles", "metal", "gravel", "plaster", "concrete")
    const val SIZE = 512

    /** One byte per texel of detail, four (tilt x, tilt y, roughness, unused) of bump, every layer bottom row first. */
    class Maps(val detail: ByteBuffer, val bump: ByteBuffer)

    /** Decodes all the maps, or returns null (and the renderer keeps its procedural patterns) if one is missing. */
    fun load(): Maps? = runCatching {
        val n = SIZE * SIZE
        val detail = ByteArray(n * NAMES.size)
        val bump = ByteArray(n * 4 * NAMES.size)
        val px = IntArray(n)
        for ((layer, name) in NAMES.withIndex()) {
            decode("${name}_detail.jpg", px)
            var o = layer * n
            for (y in SIZE - 1 downTo 0) for (x in 0 until SIZE) detail[o++] = (px[y * SIZE + x] shr 16).toByte()
            decode("${name}_bump.jpg", px)
            o = layer * n * 4
            for (y in SIZE - 1 downTo 0) for (x in 0 until SIZE) {
                val c = px[y * SIZE + x]
                bump[o++] = (c shr 16).toByte(); bump[o++] = (c shr 8).toByte(); bump[o++] = c.toByte(); bump[o++] = -1
            }
        }
        Maps(direct(detail), direct(bump))
    }.onFailure { Log.w("HustleGL", "photo materials unavailable: $it") }.getOrNull()

    private fun direct(b: ByteArray): ByteBuffer =
        ByteBuffer.allocateDirect(b.size).order(ByteOrder.nativeOrder()).put(b).also { it.position(0) }

    private fun decode(file: String, out: IntArray) {
        val stream = PhotoMaterials::class.java.getResourceAsStream("/textures/$file") ?: error("missing $file")
        val bmp = stream.use { BitmapFactory.decodeStream(it) } ?: error("can't decode $file")
        check(bmp.width == SIZE && bmp.height == SIZE) { "$file is ${bmp.width}x${bmp.height}" }
        bmp.getPixels(out, 0, SIZE, 0, 0, SIZE, SIZE)
        bmp.recycle()
    }
}
