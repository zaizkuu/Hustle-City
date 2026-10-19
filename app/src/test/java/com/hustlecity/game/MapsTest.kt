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
        val b = city.bounds
        val scale = 1200f / maxOf(b[2] - b[0], b[3] - b[1])
        val w = ((b[2] - b[0]) * scale).toInt(); val h = ((b[3] - b[1]) * scale).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun x(v: Float) = (v - b[0]) * scale
        fun y(v: Float) = (v - b[1]) * scale
        fun poly(pts: FloatArray, color: Int) {
            val path = Path()
            for (k in 0 until pts.size / 2) if (k == 0) path.moveTo(x(pts[0]), y(pts[1])) else path.lineTo(x(pts[k * 2]), y(pts[k * 2 + 1]))
            path.close(); p.color = color; p.style = Paint.Style.FILL; c.drawPath(path, p)
        }
        c.drawColor(if (city.shore.land.isEmpty()) 0xFFB9BCC1.toInt() else 0xFF27445C.toInt())
        for (l in city.shore.land) poly(l, 0xFFB9BCC1.toInt())
        for (wt in city.shore.water) poly(wt, 0xFF27445C.toInt())
        for (i in city.shore.islands) poly(i, 0xFFB9BCC1.toInt())
        for (cn in city.shore.canals) poly(cn, 0xFF3B6E96.toInt())
        for (pk in city.parks) poly(pk.poly, if (pk.kind == Park.PLAZA) 0xFFD8CFB8.toInt() else 0xFF6DA35C.toInt())
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        for (e in city.roads.edges) {
            p.color = if (e.overWater) 0xFF8A6D4B.toInt() else 0xFF4A4D53.toInt()
            p.strokeWidth = e.cls.halfWidth * 2 * scale
            c.drawLine(x(e.ax), y(e.ay), x(e.bx), y(e.by), p)
        }
        for (r in city.rails) {
            p.color = 0xFFF2994A.toInt(); p.strokeWidth = 2f
            for (k in 0 until r.pts.size / 2 - 1) c.drawLine(x(r.pts[k * 2]), y(r.pts[k * 2 + 1]), x(r.pts[k * 2 + 2]), y(r.pts[k * 2 + 3]), p)
        }
        p.style = Paint.Style.FILL
        for (bd in city.buildings) {
            val pts = floatArrayOf(bd.wx(0f, 0f), bd.wy(0f, 0f), bd.wx(bd.w, 0f), bd.wy(bd.w, 0f), bd.wx(bd.w, bd.d), bd.wy(bd.w, bd.d), bd.wx(0f, bd.d), bd.wy(0f, bd.d))
            val shade = (0xFF - (bd.height * 1.6f).toInt().coerceIn(0, 140))
            poly(pts, when {
                bd.placeId != null -> 0xFFE0B04A.toInt()
                else -> (0xFF shl 24) or ((bd.hood.color shr 16 and 255) * shade / 255 shl 16) or ((bd.hood.color shr 8 and 255) * shade / 255 shl 8) or ((bd.hood.color and 255) * shade / 255)
            })
        }
        File("build/snapshots").mkdirs()
        File("build/snapshots/map_${city.mapId}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
