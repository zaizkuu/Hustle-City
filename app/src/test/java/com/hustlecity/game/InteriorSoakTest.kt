package com.hustlecity.game

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.InteriorMode
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.engine.Person
import com.hustlecity.game.engine.itemDims
import com.hustlecity.game.model.RivalBusiness
import com.hustlecity.game.world.BKind
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.hypot

/**
 * Every kind of store, fully fitted out and at its busiest hour, for five minutes: customers and staff stay inside the
 * room and never walk through shelves or counters, nobody's position goes bad, and every customer finishes and leaves
 * instead of freezing somewhere.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InteriorSoakTest {
    @Test
    fun everyStoreKeepsItsPeopleMoving() {
        val report = StringBuilder()
        var failed = false
        for (type in Catalog.businessTypes) {
            val (ok, line) = soak(type.id)
            report.append(line).append('\n')
            if (!ok) failed = true
        }
        println(report)
        assertTrue(report.toString(), !failed)
    }

    private fun soak(typeId: String): Pair<Boolean, String> {
        val e = GameEngine(null)
        e.newGame("T", 0, 0, 1)
        val b = e.city.buildings.first { e.isVacant(it.id) && it.kind == BKind.RETAIL && it.gridW >= 12 && it.gridD >= 9 }
        // a competitor's store at its biggest fit-out lays itself out for the type
        e.state.rivalBusinesses += RivalBusiness(9999, 0, b.id, typeId, "R", level = 3)
        val curve = Catalog.type(typeId).curve
        val busiest = curve.indices.maxByOrNull { curve[it] } ?: 12
        e.state.minute = e.state.minute - e.state.minute % (24 * 60) + busiest * 60L
        val sc = InteriorScene(e, b, InteriorMode.RIVAL)
        val solid = BooleanArray(sc.w * sc.d)
        for ((i, p) in sc.items.withIndex()) {
            val def = Catalog.item(p.itemId) ?: continue
            // what people sit on or work out on, they step onto and off; shelves, counters and registers they walk round
            if ("dance_floor" in def.tags || sc.seatsOf(i).isNotEmpty()) continue
            val (fw, fd) = itemDims(def, p.rot)
            for (yy in p.y until p.y + fd) for (xx in p.x until p.x + fw) if (xx in 0 until sc.w && yy in 0 until sc.d) solid[yy * sc.w + xx] = true
        }
        val dt = 1f / 30f
        var bad = 0; var outside = 0; var inFurniture = 0; var frozen = 0
        var spawned = 0
        val seen = HashSet<Person>()
        val age = HashMap<Person, Int>(); val still = HashMap<Person, Int>()
        val lastX = HashMap<Person, Float>(); val lastY = HashMap<Person, Float>()
        var oldest = 0
        val examples = ArrayList<String>()
        fun note(s: String) { if (examples.size < 4) examples += s }
        repeat(30 * 300) { f ->
            sc.update(dt, true)
            for ((who, list) in listOf("customer" to sc.visitors, "staff" to sc.staff)) for (v in list) {
                if (!v.x.isFinite() || !v.y.isFinite()) { bad++; note("$typeId: $who at a bad position"); continue }
                // the door is in the front wall, at y = d: people come and go through it
                if (v.x < -.05f || v.x > sc.w + .05f || v.y < -.05f || v.y > sc.d + 1.6f) { outside++; note("$typeId: $who outside the room at %.1f,%.1f".format(v.x, v.y)) }
                // inside a piece of furniture, not just up against it (browsing a shelf means standing at its edge)
                val tx = v.x.toInt(); val ty = v.y.toInt()
                val fx = v.x - tx; val fy = v.y - ty
                val onItem = tx in 0 until sc.w && ty in 0 until sc.d && solid[ty * sc.w + tx] && fx in .1f..(.9f) && fy in .1f..(.9f)
                if (onItem && v.seat == null && !v.seated && v.ghost <= 0f && v.moving > .5f) {
                    inFurniture++; note("$typeId: $who walking through furniture at %.1f,%.1f (stage ${v.stage})".format(v.x, v.y))
                }
                if (who == "customer") {
                    if (seen.add(v)) spawned++
                    age[v] = (age[v] ?: 0) + 1; oldest = maxOf(oldest, age[v]!!)
                    val moved = hypot(v.x - (lastX[v] ?: v.x), v.y - (lastY[v] ?: v.y))
                    // sitting through a film or queueing for the register is fine; standing about in the aisle for a minute isn't
                    val waiting = v.seated || v.seat != null || v.counter >= 0
                    still[v] = if (moved < .001f && !waiting) (still[v] ?: 0) + 1 else 0
                    if (still[v] == 30 * 60) { frozen++; note("$typeId: customer frozen at %.1f,%.1f (stage ${v.stage}, goal ${v.goal})".format(v.x, v.y)) }
                    lastX[v] = v.x; lastY[v] = v.y
                }
            }
        }
        val left = spawned - sc.visitors.size
        // shows and films keep people in their seats for a while; nobody should stay for more than a few minutes
        val longest = oldest / 30
        val ok = bad == 0 && outside == 0 && inFurniture == 0 && frozen == 0 && spawned >= 5 && left >= spawned / 2 && longest < 240
        val line = "$typeId: items=${sc.items.size} came=$spawned left=$left staff=${sc.staff.size} longest=${longest}s bad=$bad outside=$outside " +
            "throughFurniture=$inFurniture frozen=$frozen" + if (examples.isEmpty()) "" else "\n   " + examples.joinToString("\n   ")
        return ok to line
    }
}
