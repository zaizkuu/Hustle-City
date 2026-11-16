package com.hustlecity.game

import android.graphics.Bitmap
import android.graphics.Canvas
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.model.RivalBusiness
import com.hustlecity.game.render.InteriorRenderer
import com.hustlecity.game.world.BKind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders every interior a player can walk into: home, each public place and a rival store of every type. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class InteriorGalleryTest {
    private val outDir = File("build/snapshots/interiors").apply { mkdirs() }
    private val w = 1200
    private val h = 700

    private fun engine(): GameEngine = GameEngine(null).apply { newGame("Tester", 1, 0, 1) }

    private fun shoot(e: GameEngine, name: String) {
        repeat(40) { e.frame(1 / 20f) }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        InteriorRenderer(e).draw(Canvas(bmp), w, h)
        File(outDir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun gallery() {
        val e = engine()
        e.state.minute = 13L * 60
        // home
        val home = e.city.building(e.state.player.home)!!
        e.state.player.inBuilding = home.id
        e.interior = InteriorScene(e, home, InteriorMode.HOME)
        shoot(e, "home.png")
        println("home ${e.interior!!.w}x${e.interior!!.d}: ${e.interior!!.items.map { it.itemId }}")
        // places
        for (p in Catalog.places) {
            val b = e.city.placeBuilding(p.id) ?: continue
            if (b.kind == BKind.GAS) continue
            e.state.player.inBuilding = b.id
            e.interior = InteriorScene(e, b, InteriorMode.PLACE)
            println("place ${p.id} ${e.interior!!.w}x${e.interior!!.d}: ${e.interior!!.items.map { it.itemId }}")
            shoot(e, "place_${p.id}.png")
        }
        // rivals of every type
        val types = Catalog.businessTypes + Catalog.officeTypes
        for (t in types) {
            val kind = if (t.office) BKind.OFFICE else BKind.RETAIL
            val b = e.city.buildings.filter { it.kind == kind && it.placeId == null && it.rentable }.sortedByDescending { it.area }
                .firstOrNull { cand -> e.state.rivalBusinesses.none { it.buildingId == cand.id } } ?: continue
            for (lvl in listOf(1, 3)) {
                e.state.rivalBusinesses.removeAll { it.buildingId == b.id }
                e.state.rivalBusinesses += RivalBusiness(9000 + lvl, 0, b.id, t.id, "Rival ${t.name}", level = lvl)
                e.state.player.inBuilding = b.id
                e.interior = InteriorScene(e, b, InteriorMode.RIVAL)
                println("rival ${t.id} L$lvl ${e.interior!!.w}x${e.interior!!.d}: ${e.interior!!.items.map { it.itemId }}")
                shoot(e, "rival_${t.id}_$lvl.png")
            }
            e.state.rivalBusinesses.removeAll { it.buildingId == b.id }
        }
    }
}
