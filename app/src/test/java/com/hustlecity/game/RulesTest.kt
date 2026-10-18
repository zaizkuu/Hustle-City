package com.hustlecity.game

import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.rentOf
import com.hustlecity.game.model.GameRules
import com.hustlecity.game.world.BKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Difficulty presets and custom rules change starting cash, rent, wages, demand and how fast you tire. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RulesTest {
    private fun game(difficulty: Int, rules: GameRules = GameRules.preset(difficulty)) =
        GameEngine(null).also { it.newGame("T", 0, 0, difficulty, rules = rules); it.ui.tip = null }

    @Test
    fun presetsAndCustomRulesApply() {
        val easy = game(0); val normal = game(1); val hard = game(2)
        assertEquals(20000.0, easy.state.player.money, .01)
        assertEquals(5000.0, normal.state.player.money, .01)
        assertEquals(1500.0, hard.state.player.money, .01)
        val shop = normal.city.buildings.first { it.kind == BKind.RETAIL && normal.isVacant(it.id) }
        assertTrue("hard rent is dearer", hard.rentOf(shop) > normal.rentOf(shop))
        assertTrue("easy rent is cheaper", easy.rentOf(shop) < normal.rentOf(shop))
        // a custom game with no tiredness: energy holds over an afternoon
        val relaxed = game(3, GameRules(startMoney = 12345.0, needs = 0.0, rivals = 0.0))
        assertEquals(12345.0, relaxed.state.player.money, .01)
        val e0 = relaxed.state.player.energy
        repeat(30 * 60) { relaxed.frame(1 / 30f) }
        assertEquals(e0, relaxed.state.player.energy, .001)
        val tiring = game(1)
        val t0 = tiring.state.player.energy
        repeat(30 * 60) { tiring.frame(1 / 30f) }
        assertTrue("normal games tire you (${tiring.state.player.energy} vs $t0)", tiring.state.player.energy < t0)
        // and the rules survive a save
        val codec = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true; allowSpecialFloatingPointValues = true }
        val json = codec.encodeToString(com.hustlecity.game.model.GameState.serializer(), relaxed.state)
        val back = codec.decodeFromString(com.hustlecity.game.model.GameState.serializer(), json)
        assertEquals(0.0, back.player.rules.needs, 0.0)
    }
}
