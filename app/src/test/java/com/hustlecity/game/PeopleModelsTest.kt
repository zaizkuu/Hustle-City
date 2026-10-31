package com.hustlecity.game

import com.hustlecity.game.render.PeopleModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The baked people load, stay light enough to draw a crowd, and every clip poses them as a person-sized, upright figure. */
class PeopleModelsTest {
    private val clips = listOf("Idle", "Walk", "Run", "Carry", "CarryIdle", "Sit", "Dance")

    @Test
    fun modelsLoad() {
        val models = PeopleModels.models
        assertEquals(13, models.size)
        for (m in models) {
            assertTrue("${m.name} has ${m.triangles} triangles", m.triangles in 300..1600)
            for (i in m.tri) assertTrue("${m.name} index out of range", i in 0 until m.vertices)
            for (r in m.roles) assertTrue("${m.name} role $r", r >= 0 && r < 4 + m.palette.size)
            for (v in 0 until m.vertices) {
                assertTrue(m.b0[v] in 0 until m.rig.bones && m.b1[v] in 0 until m.rig.bones)
                assertTrue(m.w[v] in 0f..1f)
            }
            // everyone has a face and hands, and something to wear
            assertTrue("${m.name} has no skin", m.roles.any { it.toInt() == PeopleModels.SKIN })
            assertTrue("${m.name} has no clothes", m.roles.any { it.toInt() == PeopleModels.TOP || it.toInt() >= 4 })
            for (c in clips) assertTrue("${m.rig.name} lacks $c", c in m.rig.clips)
        }
    }

    @Test
    fun everyClipPosesAPerson() {
        val bones = FloatArray(64 * 12)
        for (m in PeopleModels.models) for (c in clips) {
            val clip = m.rig.clips.getValue(c)
            for (step in 0..4) {
                val t = clip.duration * step / 4f
                // halfway between standing and walking, the one blend the game makes (Idle to Walk, CarryIdle to Carry)
                val toward = when (c) { "Idle" -> "Walk"; "CarryIdle" -> "Carry"; else -> c }
                PeopleModels.pose(m.rig, c, t, toward, t, if (step == 2) .5f else 0f, bones)
                var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE; var wide = 0f
                for (v in 0 until m.vertices) {
                    val a = m.b0[v] * 12; val b = m.b1[v] * 12; val w = m.w[v]; val u = 1 - w
                    val px = m.pos[v * 3]; val py = m.pos[v * 3 + 1]; val pz = m.pos[v * 3 + 2]
                    fun row(o: Int) = (bones[a + o] * px + bones[a + o + 1] * py + bones[a + o + 2] * pz + bones[a + o + 3]) * w +
                        (bones[b + o] * px + bones[b + o + 1] * py + bones[b + o + 2] * pz + bones[b + o + 3]) * u
                    val f = row(0); val r = row(4); val up = row(8)
                    assertTrue("${m.name} $c: bad vertex", f.isFinite() && r.isFinite() && up.isFinite())
                    lo = minOf(lo, up); hi = maxOf(hi, up); wide = maxOf(wide, kotlin.math.abs(f), kotlin.math.abs(r))
                }
                val tall = hi - lo
                if (c == "Sit") {
                    // drawn at the seat's height (benches and chairs are half a meter): the feet reach the floor below it
                    assertTrue("${m.name} sitting is $tall m tall", tall in 1.1f..1.6f)
                    assertTrue("${m.name}'s feet hang to $lo below the seat", lo in -.56f..-.36f)
                } else {
                    // (dancers have their arms up over their heads)
                    assertTrue("${m.name} $c is $tall m tall", tall in 1.5f..(if (c == "Dance") 2.5f else 2.1f))
                    assertTrue("${m.name} $c floats or sinks: lowest point $lo", lo in -.12f..(if (c == "Run") .2f else .12f))
                }
                // nothing flung far from the body
                assertTrue("${m.name} $c reaches $wide m out", wide < 1.2f)
            }
        }
    }
}
