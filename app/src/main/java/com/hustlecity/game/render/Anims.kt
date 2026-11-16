package com.hustlecity.game.render

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * One animation of the people's skeleton: for every frame where the hips are and each bone's turn from its parent
 * (a quaternion), in the figure's own axes (x to its left, y forward, z up). Authored in Blender as an Action on the
 * HC Rig (tools/blender/rig.py).
 */
class Clip(val name: String, val frames: Int, val fps: Float, val loop: Boolean, private val bones: Int, private val root: FloatArray, private val quat: FloatArray) {
    val seconds: Float = (if (loop) frames else (frames - 1).coerceAtLeast(1)) / fps

    /** The pose [u] of the way through the clip (a loop wraps, anything else holds its ends). */
    fun at(u: Float, outRoot: FloatArray, outQuat: FloatArray) {
        val f: Float; val a: Int; val b: Int
        if (loop) {
            f = (u - floor(u)) * frames
            a = f.toInt().coerceIn(0, frames - 1); b = (a + 1) % frames
        } else {
            f = u.coerceIn(0f, 1f) * (frames - 1)
            a = f.toInt().coerceIn(0, frames - 1); b = (a + 1).coerceAtMost(frames - 1)
        }
        val t = f - a
        for (i in 0 until 3) outRoot[i] = root[a * 3 + i] + (root[b * 3 + i] - root[a * 3 + i]) * t
        val oa = a * bones * 4; val ob = b * bones * 4
        for (k in 0 until bones) {
            val i = k * 4
            val ax = quat[oa + i]; val ay = quat[oa + i + 1]; val az = quat[oa + i + 2]; val aw = quat[oa + i + 3]
            var bx = quat[ob + i]; var by = quat[ob + i + 1]; var bz = quat[ob + i + 2]; var bw = quat[ob + i + 3]
            if (ax * bx + ay * by + az * bz + aw * bw < 0f) { bx = -bx; by = -by; bz = -bz; bw = -bw }
            val x = ax + (bx - ax) * t; val y = ay + (by - ay) * t; val z = az + (bz - az) * t; val w = aw + (bw - aw) * t
            val l = sqrt(x * x + y * y + z * z + w * w).coerceAtLeast(1e-6f)
            outQuat[i] = x / l; outQuat[i + 1] = y / l; outQuat[i + 2] = z / l; outQuat[i + 3] = w / l
        }
    }

    /** The pose [seconds] into the clip. */
    fun atTime(seconds: Float, outRoot: FloatArray, outQuat: FloatArray) = at(seconds / this.seconds, outRoot, outQuat)
}

/**
 * The skeleton and the clips, read once from assets/anims.hca (written by tools/blender/export_game.py, which also
 * documents the file). Until [load] has run nobody is animated: people are drawn as boxes, as the classic renderer
 * always draws them.
 */
object AnimLib {
    /** Bones in the order the file has them (a bone's parent always comes before it). */
    const val HIPS = 0; const val TORSO = 1; const val HEAD = 2
    const val UPPER_L = 3; const val FORE_L = 4; const val HAND_L = 5
    const val UPPER_R = 6; const val FORE_R = 7; const val HAND_R = 8
    const val THIGH_L = 9; const val SHIN_L = 10; const val FOOT_L = 11
    const val THIGH_R = 12; const val SHIN_R = 13; const val FOOT_R = 14
    const val BONES = 15
    private val NAMES = arrayOf("hips", "torso", "head", "upper_arm.L", "forearm.L", "hand.L", "upper_arm.R", "forearm.R", "hand.R",
        "thigh.L", "shin.L", "foot.L", "thigh.R", "shin.R", "foot.R")

    @Volatile private var clips: Map<String, Clip> = emptyMap()
    @Volatile var version = 0
        private set

    /** Parent of each bone (-1 for the hips) and where its joint is at rest, meters. */
    var parent = IntArray(BONES) { -1 }
        private set
    var rest = FloatArray(BONES * 3)
        private set
    /** How much closer in a woman's shoulders sit: a factor on the arm joints' x. */
    var narrow = 1f
        private set

    val ready: Boolean get() = clips.isNotEmpty()

    fun get(name: String): Clip? = clips[name]

    val names: Set<String> get() = clips.keys

    fun clear() { clips = emptyMap(); version++ }

    /** Reads a clip pack. Throws if it isn't one, or if its skeleton isn't the one the painter knows. */
    fun load(input: InputStream) {
        val buf = ByteBuffer.wrap(input.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.get().toInt() == 'H'.code && buf.get().toInt() == 'C'.code && buf.get().toInt() == 'A'.code && buf.get().toInt() == '1'.code) {
            "not a clip pack"
        }
        val n = buf.get().toInt() and 0xFF
        require(n == BONES) { "the clip pack has $n bones, the painter knows $BONES" }
        val par = IntArray(n); val rst = FloatArray(n * 3)
        for (k in 0 until n) {
            val nameBytes = ByteArray(buf.get().toInt() and 0xFF)
            buf.get(nameBytes)
            require(String(nameBytes, Charsets.US_ASCII) == NAMES[k]) { "bone $k is ${String(nameBytes, Charsets.US_ASCII)}, expected ${NAMES[k]}" }
            par[k] = buf.get().toInt()
            require(par[k] < k) { "bone $k comes before its parent" }
            for (i in 0 until 3) rst[k * 3 + i] = buf.getFloat()
        }
        val nar = buf.getFloat()
        val count = buf.getShort().toInt() and 0xFFFF
        val out = HashMap<String, Clip>(count * 2)
        for (c in 0 until count) {
            val nameBytes = ByteArray(buf.get().toInt() and 0xFF)
            buf.get(nameBytes)
            val name = String(nameBytes, Charsets.US_ASCII)
            val frames = buf.getShort().toInt() and 0xFFFF
            val fps = buf.getFloat()
            val loop = buf.get().toInt() != 0
            val root = FloatArray(frames * 3)
            val quat = FloatArray(frames * n * 4)
            for (f in 0 until frames) {
                for (i in 0 until 3) root[f * 3 + i] = buf.getFloat()
                val o = f * n * 4
                for (i in 0 until n * 4) quat[o + i] = buf.getShort() / 32767f
            }
            out[name] = Clip(name, frames, fps, loop, n, root, quat)
        }
        parent = par; rest = rst; narrow = nar
        clips = out
        version++
    }
}
