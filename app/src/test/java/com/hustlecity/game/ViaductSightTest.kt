package com.hustlecity.game

import com.hustlecity.game.render.CityFeatures
import com.hustlecity.game.world.City
import com.hustlecity.game.world.Maps
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Manila's LRT runs above Taft Avenue: the spans between the camera and you are the ones made see-through. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ViaductSightTest {
    @Test
    fun spansOverYouAreTheOnesInTheWay() {
        val f = CityFeatures(City(1337L, Maps.MANILA))
        fun hiding(cx: Float, cy: Float, cz: Float, px: Float, py: Float) = (0 until f.count).filter { f.hides(it, cx, cy, cz, px, py, 1.2f) }
        // driving down Taft under the line, the camera up behind the car
        val under = hiding(531f, 737f, 59f, 531f, 700f)
        assertTrue("a span over the car is in the way", under.isNotEmpty())
        assertTrue("only the spans by the car (${under.size})", under.size <= 3)
        // on a side street well away from the line nothing is
        assertTrue("nothing hides a car 60 m off the line", hiding(591f, 737f, 59f, 591f, 700f).isEmpty())
        // looking straight down from high above the car, the deck can't be between unless it's over it
        assertFalse("a camera looking down beside the line", hiding(560f, 700f, 59f, 560f, 700f).isNotEmpty())
    }
}
