package com.hustlecity.game.render.gpu

/**
 * Off-screen capture of the HD renderer for trailers and tests: when [frame] is set and no GL view is running, the
 * world view draws its 3D geometry into it (and only labels and weather onto the screen), so the frame can be saved
 * and rendered elsewhere.
 */
object GpuCapture {
    @Volatile @JvmField var frame: GpuFrame? = null
}
