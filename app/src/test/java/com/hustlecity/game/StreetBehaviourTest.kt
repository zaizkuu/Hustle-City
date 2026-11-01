package com.hustlecity.game

import com.hustlecity.game.world.City
import com.hustlecity.game.world.Maps
import com.hustlecity.game.world.PedActivity
import com.hustlecity.game.world.Traffic
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The sidewalks are more than people walking: window-shoppers, phones out, a chat, joggers, shopping bags, and a jump back from a car. */
class StreetBehaviourTest {
    @Test
    fun peopleDoThingsOnTheSidewalk() {
        val city = City(1337L, Maps.NYC)
        val t = Traffic(Random(8))
        t.attach(city)
        t.ensure(30, 160)
        val n = city.roads.nodes.filter { it.degree >= 3 && !city.shore.wet(it.x, it.y) }
        val focus = n[n.size / 3]
        // a few open shop doors along the street, as the game hands them over near the camera
        val shops = city.buildings.filter { it.solid && abs(it.doorX - focus.x) < 90f && abs(it.doorY - focus.y) < 90f }.take(12)
        t.doors = FloatArray(shops.size * 5).also { d -> for ((k, b) in shops.withIndex()) { d[k * 5] = b.doorX; d[k * 5 + 1] = b.doorY; d[k * 5 + 2] = .05f; d[k * 5 + 3] = -b.frontX; d[k * 5 + 4] = -b.frontY } }
        t.doorCount = shops.size
        t.update(0f, focus.x, focus.y, 200f, -9999f, -9999f, initial = true)
        val seen = IntArray(7)
        var bags = 0; var joggers = 0
        repeat(30 * 240) {
            t.update(1f / 30f, focus.x, focus.y, 200f, -9999f, -9999f)
            for (a in t.peds) {
                if (a.hidden || !a.placed) continue
                seen[a.activity]++
                if (a.bag) bags++
                if (a.jogger) joggers++
            }
        }
        println("frames: window ${seen[PedActivity.WINDOW]}, phone ${seen[PedActivity.PHONE]}, chat ${seen[PedActivity.CHAT]}, bags $bags, joggers $joggers")
        assertTrue("someone stopped at a shop window", seen[PedActivity.WINDOW] > 0)
        assertTrue("someone read their phone", seen[PedActivity.PHONE] > 0)
        assertTrue("two people stopped to talk", seen[PedActivity.CHAT] > 0)
        assertTrue("shoppers carry bags", bags > 0)
        assertTrue("people jog", joggers > 0)
        // most of the time, most people are just walking
        val busy = seen[1] + seen[2] + seen[3] + seen[4]
        assertTrue("not everyone loiters ($busy of ${seen.sum()})", busy * 4 < seen.sum())
    }

    /** Passers-by stop at the street food carts, pay, and walk on eating; walking past, they go round the cart, not through it. */
    @Test
    fun peopleBuyFromFoodCarts() {
        for (mapId in listOf(Maps.MANILA, Maps.NYC)) {
            val city = City(1337L, mapId)
            val props = com.hustlecity.game.render.StreetProps(city)
            val carts = (0 until props.count).filter { props.kind[it] == com.hustlecity.game.render.StreetProps.VENDOR }
            assertTrue("$mapId has food carts", carts.size > 10)
            // the busiest corner for carts: the cart with most others within 120 m
            val c0 = carts.maxByOrNull { i -> carts.count { j -> abs(props.x[j] - props.x[i]) < 120f && abs(props.y[j] - props.y[i]) < 120f } }!!
            val fx = props.x[c0]; val fy = props.y[c0]
            val near = carts.filter { abs(props.x[it] - fx) < 160f && abs(props.y[it] - fy) < 160f }
            val t = Traffic(Random(5))
            t.attach(city)
            t.ensure(0, 220)
            t.stands = FloatArray(near.size * 4).also { d -> for ((k, i) in near.withIndex()) { d[k * 4] = props.x[i]; d[k * 4 + 1] = props.y[i]; d[k * 4 + 2] = props.dirX[i]; d[k * 4 + 3] = props.dirY[i] } }
            t.standCount = near.size
            t.update(0f, fx, fy, 200f, -9999f, -9999f, initial = true)
            var buying = 0; var eating = 0; var through = 0; var passing = 0
            repeat(30 * 300) {
                t.update(1f / 30f, fx, fy, 200f, -9999f, -9999f)
                for (a in t.peds) {
                    if (a.hidden || !a.placed) continue
                    if (a.state == 7) buying++
                    if (a.activity == PedActivity.EAT) eating++
                    if (a.state != 0) continue
                    // walking: never inside a cart (along it within its length, across it within its depth)
                    for (i in near) {
                        val qx = a.x - props.x[i]; val qy = a.y - props.y[i]
                        val la = qx * -props.dirY[i] + qy * props.dirX[i]; val ld = qx * props.dirX[i] + qy * props.dirY[i]
                        if (abs(la) < 1.2f && abs(ld) < 1.4f) passing++
                        if (abs(la) < .75f && ld > -.35f && ld < .35f) through++
                    }
                }
            }
            println("$mapId: ${near.size} carts, frames buying $buying, eating $eating, passing $passing, through a cart $through")
            assertTrue("$mapId: someone bought from a cart", buying > 0)
            assertTrue("$mapId: and walked off eating it", eating > 0)
            assertTrue("$mapId: walkers go round the carts ($through of $passing frames)", through * 50 <= passing)
        }
    }

    @Test
    fun peopleJumpBackFromACarComingAtThem() {
        val city = City(1337L, Maps.NYC)
        val t = Traffic(Random(3))
        t.attach(city)
        t.ensure(0, 120)
        val n = city.roads.nodes.filter { it.degree >= 3 && !city.shore.wet(it.x, it.y) }
        val focus = n[n.size / 2]
        t.update(0f, focus.x, focus.y, 200f, -9999f, -9999f, initial = true)
        repeat(30) { t.update(1f / 30f, focus.x, focus.y, 200f, -9999f, -9999f) }
        // a car coming straight down the sidewalk at someone, 8 m off, at 10 m/s
        val ped = t.peds.first { it.placed && !it.hidden && it.state == 0 && !it.jogger }
        val h = ped.heading + Math.PI.toFloat()
        t.threatX = ped.x - sin(h) * 8f; t.threatY = ped.y + cos(h) * 8f
        t.threatHeading = h; t.threatSpeed = 10f
        t.update(1f / 30f, focus.x, focus.y, 200f, -9999f, -9999f)
        assertTrue("they saw it coming", ped.startled > 0f)
        val x0 = ped.x; val y0 = ped.y
        repeat(20) { t.update(1f / 30f, focus.x, focus.y, 200f, -9999f, -9999f) }
        // they hurried: further in two thirds of a second than a stroll would take them
        val moved = kotlin.math.hypot(ped.x - x0, ped.y - y0)
        assertTrue("they ran (%.2f m)".format(moved), moved > 1.4f)
    }
}
