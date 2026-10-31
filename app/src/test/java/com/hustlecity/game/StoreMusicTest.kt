package com.hustlecity.game

import com.hustlecity.game.audio.Audio
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.engine.startBusiness
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.model.Rental
import com.hustlecity.game.model.StoreMusic
import com.hustlecity.game.world.BKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A store's speakers take over from the phone radio while you're inside, and give it back when you leave. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoreMusicTest {
    @Test
    fun speakersPlayTheStoresChoiceAndHandBackThePhone() {
        val e = GameEngine(null)
        e.newGame("T", 0, 0, 1)
        e.settings.radioOn = true; e.settings.station = 0; e.settings.musicVolume = .5f
        e.settings.applyAudio()
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.placeId == null }
        e.state.rentals += Rental(b.id, b.baseRent, 1)
        val biz = e.startBusiness(b.id, "giftshop", "X")!!
        e.state.player.inBuilding = b.id
        e.interior = InteriorScene(e, b, InteriorMode.BUSINESS)
        fun frames(n: Int = 3) = repeat(n) { e.frame(1 / 30f) }

        // no speakers: the phone radio carries on as it was
        biz.music = 2
        frames()
        assertEquals(0f, e.speakerGain(biz))
        assertTrue(Audio.radioOn)
        assertEquals(.5f, Audio.musicVolume, 1e-4f)

        // a speaker: the store's own station, a little quieter than a room full of them
        biz.furniture += PlacedItem("speaker_small", 0, 0, 0)
        frames()
        assertTrue(Audio.radioOn)
        assertTrue("switching to station 2", Audio.station == 2 || Audio.musicVolume < .5f)
        assertTrue(Audio.musicVolume in .2f..(.5f))
        val quiet = Audio.musicVolume
        biz.furniture += PlacedItem("speaker_large", 0, 1, 0)
        frames()
        assertTrue("more speakers, louder", Audio.musicVolume > quiet)

        // off: silence in the store, and its speakers stop counting toward the atmosphere
        val withMusic = com.hustlecity.game.engine.BizCalc.decorScore(biz, b)
        biz.music = StoreMusic.OFF
        frames()
        assertTrue(!Audio.radioOn)
        assertTrue("music off, less atmosphere", com.hustlecity.game.engine.BizCalc.decorScore(biz, b) < withMusic)

        // out of the door: the phone radio is back as the player left it
        e.exitBuilding()
        frames()
        assertTrue(Audio.radioOn)
        assertEquals(.5f, Audio.musicVolume, 1e-4f)
    }
}
