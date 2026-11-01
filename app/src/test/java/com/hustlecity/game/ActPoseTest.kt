package com.hustlecity.game

import com.hustlecity.game.render.Act
import com.hustlecity.game.render.PeopleModels
import com.hustlecity.game.render.PersonPainter
import com.hustlecity.game.render.Pose
import com.hustlecity.game.render.Projector
import com.hustlecity.game.render.RenderBatch
import com.hustlecity.game.render.Scene3D
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every character acting out every job (the till, mopping, cutting hair, curls...) stays in one piece: over ten
 * seconds of each, no part of the body is flung further than a person's reach.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActPoseTest {
    @Test
    fun everyoneStaysInOnePiece() {
        val painter = PersonPainter(Scene3D(Projector(), RenderBatch()))
        val seated = setOf(Act.TYPE, Act.EAT, Act.PRESS, Act.ROW)
        assertTrue("no character models", PeopleModels.models.isNotEmpty())
        for (i in PeopleModels.models.indices) {
            val still = painter.debugExtent(i, Act.NONE, 0f)
            for (a in 1 until Act.COUNT) {
                val pose = if (a in seated) Pose.SIT else Pose.STAND
                for (k in 0 until 50) {
                    val x = painter.debugExtent(i, a, k * .2f, pose)
                    val span = maxOf(x[3] - x[0], x[4] - x[1], x[5] - x[2])
                    assertTrue("${PeopleModels.models[i].name} act $a at ${k * .2f}s: ${x.joinToString { "%.2f".format(it) }}", span < 2.1f)
                    // the arms never go through the floor, nor the head through the ceiling
                    assertTrue("${PeopleModels.models[i].name} act $a reaches below the floor", x[2] > still[2] - (if (pose == Pose.SIT) .6f else .08f))
                    assertTrue("${PeopleModels.models[i].name} act $a reaches too high", x[5] < 2.2f)
                }
            }
        }
    }

    /**
     * Walking with a phone, a call, a snack or money in hand, the arm holds it where it would standing still (relative to
     * the head), all through the stride, instead of swinging it about.
     */
    @Test
    fun heldThingsStayPutWhileWalking() {
        val painter = PersonPainter(Scene3D(Projector(), RenderBatch()))
        for (i in PeopleModels.models.indices) for (act in intArrayOf(Act.PHONE, Act.CALL, Act.SNACK, Act.PAY)) {
            val still = painter.debugWalkingHand(i, act, 2f, 0f, 0f)
            for (k in 0 until 10) {
                val h = painter.debugWalkingHand(i, act, 2f, 1f, k / 10f)
                val d = kotlin.math.sqrt((h[0] - still[0]) * (h[0] - still[0]) + (h[1] - still[1]) * (h[1] - still[1]) + (h[2] - still[2]) * (h[2] - still[2]))
                assertTrue("${PeopleModels.models[i].name} act $act at ${k / 10f} of the stride: hand %.2f m off".format(d), d < .12f)
            }
        }
    }
}
