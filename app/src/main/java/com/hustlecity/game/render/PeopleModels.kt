package com.hustlecity.game.render

import android.util.Log
import java.io.DataInputStream
import kotlin.math.sqrt

/**
 * People from the Ultimate Modular Men and Women packs by Quaternius (CC0), baked by tools/assets/people.py into
 * resources/models/people.bin: each character's triangles tagged with what they are (skin, hair, top, bottoms, or a
 * colour of their own), and each vertex bound to two bones of its rig with the first one's share. A rig's clips are
 * the bind-to-pose transform of every bone, sampled; [pose] blends them into the matrices a character is drawn with.
 */
object PeopleModels {
    const val SKIN = 0
    const val HAIR = 1
    const val TOP = 2
    const val BOTTOM = 3

    /** Per sample per bone: rotation quaternion x, y, z, w then translation f, r, u (meters). */
    class Clip(val name: String, val loop: Boolean, val duration: Float, val samples: Int, val bones: Int, val data: FloatArray)

    class Rig(val name: String, val bones: Int, val clips: Map<String, Clip>)

    /**
     * Triangle t has role [roles][t] (0..3, or 4 + n for [palette][n]) and corners [tri][3t..3t+2]; vertex v sits at
     * [pos][3v..3v+2] (forward, right, up) in the bind pose, bound to bones [b0][v] and [b1][v], [w][v] of it to the first.
     */
    class Model(val name: String, val rig: Rig, val palette: IntArray, val roles: ByteArray, val tri: IntArray, val pos: FloatArray,
                val b0: ByteArray, val b1: ByteArray, val w: FloatArray) {
        val triangles get() = roles.size
        val vertices get() = pos.size / 3
    }

    val models: List<Model> by lazy { load() }

    private fun load(): List<Model> = runCatching {
        val stream = PeopleModels::class.java.getResourceAsStream("/models/people.bin") ?: error("missing people.bin")
        DataInputStream(stream.buffered()).use { d ->
            val magic = ByteArray(4).also { d.readFully(it) }
            check(String(magic) == "HPC1") { "not a people file" }
            fun utf() = ByteArray(d.readUnsignedShort()).also { d.readFully(it) }.decodeToString()
            val rigs = ArrayList<Rig>()
            repeat(d.readUnsignedShort()) {
                val name = utf(); val bones = d.readUnsignedShort()
                val clips = HashMap<String, Clip>()
                repeat(d.readUnsignedShort()) {
                    val cn = utf(); val loop = d.readUnsignedByte() == 1; val dur = d.readFloat(); val n = d.readUnsignedShort()
                    val data = FloatArray(n * bones * 7)
                    for (k in 0 until n * bones) {
                        for (i in 0 until 4) data[k * 7 + i] = d.readShort() / 32767f
                        for (i in 4 until 7) data[k * 7 + i] = d.readShort() / 8000f
                    }
                    clips[cn] = Clip(cn, loop, dur, n, bones, data)
                }
                rigs += Rig(name, bones, clips)
            }
            val out = ArrayList<Model>()
            repeat(d.readUnsignedShort()) {
                val name = utf(); val rig = rigs[d.readUnsignedByte()]
                val palette = IntArray(d.readUnsignedByte()) { d.readInt() }
                val nt = d.readUnsignedShort(); val nv = d.readUnsignedShort()
                val roles = ByteArray(nt); val tri = IntArray(nt * 3)
                for (t in 0 until nt) {
                    roles[t] = d.readByte()
                    for (i in 0 until 3) tri[t * 3 + i] = d.readUnsignedShort()
                }
                val pos = FloatArray(nv * 3); val b0 = ByteArray(nv); val b1 = ByteArray(nv); val w = FloatArray(nv)
                for (v in 0 until nv) {
                    for (i in 0 until 3) pos[v * 3 + i] = d.readShort() / 8000f
                    b0[v] = d.readByte(); b1[v] = d.readByte(); w[v] = d.readUnsignedByte() / 255f
                }
                out += Model(name, rig, palette, roles, tri, pos, b0, b1, w)
            }
            out
        }
    }.onFailure { Log.w("HustlePeople", "people models unavailable: $it") }.getOrDefault(emptyList())

    private val qa = FloatArray(7)
    private val qb = FloatArray(7)

    /** Bone [bone] of [clip] at [t] seconds (wrapped when it loops): rotation and translation into [out]. */
    private fun sample(clip: Clip, bone: Int, t: Float, out: FloatArray) {
        val n = clip.samples
        val u = if (n <= 1 || clip.duration <= 0f) 0f else {
            val x = t / clip.duration * n
            if (clip.loop) ((x % n) + n) % n else x.coerceIn(0f, n - 1f)
        }
        val i0 = u.toInt().coerceIn(0, n - 1)
        val i1 = if (clip.loop) (i0 + 1) % n else (i0 + 1).coerceAtMost(n - 1)
        val f = u - i0
        val a = (i0 * clip.bones + bone) * 7; val b = (i1 * clip.bones + bone) * 7
        val d = clip.data
        // the shorter way round between the two rotations
        val sgn = if (d[a] * d[b] + d[a + 1] * d[b + 1] + d[a + 2] * d[b + 2] + d[a + 3] * d[b + 3] < 0f) -1f else 1f
        for (i in 0 until 4) out[i] = d[a + i] * (1 - f) + d[b + i] * sgn * f
        for (i in 4 until 7) out[i] = d[a + i] * (1 - f) + d[b + i] * f
    }

    /**
     * The matrices (3x4 per bone, row-major: rotation then translation per row) of [rig] in clip [a] at [ta] blended
     * toward clip [b] at [tb] by [k] (0 all a, 1 all b), into [out].
     */
    fun pose(rig: Rig, a: String, ta: Float, b: String, tb: Float, k: Float, out: FloatArray) {
        val ca = rig.clips[a] ?: return
        val cb = rig.clips[b] ?: ca
        for (bone in 0 until rig.bones) {
            sample(ca, bone, ta, qa)
            if (k > 0f) {
                sample(cb, bone, tb, qb)
                val sgn = if (qa[0] * qb[0] + qa[1] * qb[1] + qa[2] * qb[2] + qa[3] * qb[3] < 0f) -1f else 1f
                for (i in 0 until 4) qa[i] = qa[i] * (1 - k) + qb[i] * sgn * k
                for (i in 4 until 7) qa[i] = qa[i] * (1 - k) + qb[i] * k
            }
            val l = sqrt(qa[0] * qa[0] + qa[1] * qa[1] + qa[2] * qa[2] + qa[3] * qa[3]).coerceAtLeast(1e-6f)
            val x = qa[0] / l; val y = qa[1] / l; val z = qa[2] / l; val w = qa[3] / l
            val o = bone * 12
            out[o] = 1 - 2 * (y * y + z * z); out[o + 1] = 2 * (x * y - z * w); out[o + 2] = 2 * (x * z + y * w); out[o + 3] = qa[4]
            out[o + 4] = 2 * (x * y + z * w); out[o + 5] = 1 - 2 * (x * x + z * z); out[o + 6] = 2 * (y * z - x * w); out[o + 7] = qa[5]
            out[o + 8] = 2 * (x * z - y * w); out[o + 9] = 2 * (y * z + x * w); out[o + 10] = 1 - 2 * (x * x + y * y); out[o + 11] = qa[6]
        }
    }
}
