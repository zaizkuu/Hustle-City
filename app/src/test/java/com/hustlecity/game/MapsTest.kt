package com.hustlecity.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.world.City
import com.hustlecity.game.world.Maps
import com.hustlecity.game.world.Park
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Every map generates a sound city (places, connected roads, no overlapping buildings, reachable doors) and a top-down preview. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MapsTest {
    @Test
    fun everyMapIsPlayable() {
        for (m in Maps.all) {
            val t0 = System.nanoTime()
            val city = City(1337L, m.id)
            val ms = (System.nanoTime() - t0) / 1e6
            println("${m.id}: ${city.buildings.size} buildings, ${city.roads.edges.size} roads, ${city.roads.nodes.size} junctions, ${city.trees.size} trees in %.0f ms".format(ms))
            assertTrue("${m.id}: too few buildings", city.buildings.size > 500)
            for (p in Catalog.places.map { it.id }.distinct()) assertNotNull("${m.id}: missing $p", city.placeBuilding(p))
            // no overlapping solid footprints
            for (a in city.buildings) {
                if (!a.solid) continue
                val oa = a.obb()
                city.forBuildingsIn(a.minX, a.minY, a.maxX, a.maxY) { b ->
                    if (b.id > a.id && b.solid) assertTrue("${m.id}: overlap ${a.id} ${b.id}", !oa.overlaps(b.obb(), -.05f))
                }
            }
            // doors are on walkable ground
            val out = FloatArray(2)
            for (b in city.buildings.filter { it.solid }.take(300)) {
                city.collide(b.doorX, b.doorY, .45f, out)
                assertTrue("${m.id}: door of ${b.address} blocked", kotlin.math.abs(out[0] - b.doorX) < .7f && kotlin.math.abs(out[1] - b.doorY) < .7f)
            }
            // GPS finds a way between the player's start and every place
            val e = GameEngine(null)
            e.newGame("T", 0, 0, 1, mapId = m.id)
            for (p in Catalog.places) {
                val b = e.city.placeBuilding(p.id)!!
                e.state.player.waypoint = b.id
                e.gps.update(1f)
                assertTrue("${m.id}: no route to ${p.id}", e.gps.points >= 2)
            }
            assertTrue("${m.id}: generation took $ms ms", ms < 6000)
            preview(city)
        }
    }

    /** A top-down picture of the whole map: water, parks, roads, buildings and places. */
    private fun preview(city: City) {
        val bmp = com.hustlecity.game.render.MapPreview.render(city, 1200)
        File("build/snapshots").mkdirs()
        File("build/snapshots/map_${city.mapId}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
