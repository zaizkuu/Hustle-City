package com.hustlecity.game.engine

import com.hustlecity.game.render.Snack
import com.hustlecity.game.render.StreetProps
import com.hustlecity.game.world.Maps
import com.hustlecity.game.world.dist

/** Something to eat off a street food cart: what it's called, what it costs and how much it fills you up. */
class StreetSnack(val name: String, val price: Double, val hunger: Double, val energy: Double = 0.0, val item: Int = Snack.SKEWER,
                  /** How it's said in "You had ...". */
                  val label: String = "a ${name.lowercase()}")

/** What the street food carts sell in each city: cheap, quick, and less filling than a sit-down meal. */
object StreetFood {
    private val menus = HashMap<String, List<StreetSnack>>()

    /** The snacks a city's carts sell (always three). */
    fun menu(mapId: String): List<StreetSnack> = menus.getOrPut(mapId) { build(mapId) }

    private fun build(mapId: String): List<StreetSnack> = when (mapId) {
        Maps.MANILA -> listOf(
            StreetSnack("Fishballs", 2.0, 14.0, label = "a stick of fishballs"), StreetSnack("Kwek-kwek", 2.0, 12.0, item = Snack.BOAT, label = "some kwek-kwek"),
            StreetSnack("Taho", 1.0, 6.0, energy = 4.0, item = Snack.CUP, label = "a cup of taho"))
        Maps.TOKYO -> listOf(
            StreetSnack("Takoyaki", 5.0, 22.0, item = Snack.BOAT, label = "some takoyaki"), StreetSnack("Yakitori", 4.0, 18.0, label = "yakitori skewers"),
            StreetSnack("Hot tea", 2.0, 2.0, energy = 6.0, item = Snack.CUP, label = "a cup of hot tea"))
        Maps.SEOUL -> listOf(
            StreetSnack("Tteokbokki", 4.0, 22.0, item = Snack.BOAT, label = "some tteokbokki"), StreetSnack("Fish cake skewer", 2.0, 10.0),
            StreetSnack("Hotteok", 3.0, 14.0, item = Snack.WRAP))
        Maps.NYC -> listOf(
            StreetSnack("Hot dog", 4.0, 20.0, item = Snack.HOT_DOG), StreetSnack("Pretzel", 3.0, 14.0, item = Snack.WRAP),
            StreetSnack("Soda", 2.0, 3.0, energy = 4.0, item = Snack.CUP))
        else -> listOf(
            StreetSnack("Hot dog", 4.0, 20.0, item = Snack.HOT_DOG), StreetSnack("Churros", 3.0, 12.0, item = Snack.BOAT, label = "churros"),
            StreetSnack("Lemonade", 2.0, 3.0, energy = 4.0, item = Snack.CUP))
    }

    /** What a city's carts are called on the buttons ("Hot dog cart", "Yatai"...). */
    fun cartName(mapId: String): String = when (mapId) {
        Maps.MANILA -> "Street food cart"
        Maps.TOKYO -> "Yatai"
        Maps.SEOUL -> "Pojangmacha"
        else -> "Hot dog cart"
    }

    /** How close to a cart's middle you have to be to buy from it. */
    const val REACH = 2.6f
}

/** The street food cart within reach of the player on foot (its index in [GameEngine.props]), or -1. */
fun GameEngine.vendorNear(x: Float, y: Float): Int {
    val pr = props
    var best = -1; var bestD = StreetFood.REACH
    pr.forIn(x - StreetFood.REACH, y - StreetFood.REACH, x + StreetFood.REACH, y + StreetFood.REACH) { i ->
        if (pr.kind[i] == StreetProps.VENDOR) {
            val d = dist(x, y, pr.x[i], pr.y[i])
            if (d < bestD) { bestD = d; best = i }
        }
    }
    return best
}

/** Buys [snack] from the cart you're at and eats it on the spot (or as you walk on). */
fun GameEngine.buyStreetFood(snack: StreetSnack) {
    if (!spend(snack.price, "Food")) return
    val p = state.player
    p.hunger = (p.hunger + snack.hunger).coerceAtMost(100.0)
    p.energy = (p.energy + snack.energy).coerceIn(0.0, 100.0)
    p.happiness = (p.happiness + 1.0).coerceIn(0.0, 100.0)
    // hand the money over, then eat it as you go
    ui.payingUntil = realTime + 1.1f
    ui.eatingUntil = realTime + 1.1f + if (snack.hunger >= 10.0) 9f else 5f
    ui.eatingItem = snack.item
    // face the cart while you pay
    if (nearVendor >= 0) {
        val pr = props
        p.heading = kotlin.math.atan2(pr.x[nearVendor] - p.x, -(pr.y[nearVendor] - p.y))
    }
    toast("You had ${snack.label} (${money(snack.price)})", ToastKind.GOOD)
    bump()
}

/**
 * Keeps a walker of radius [r] at (x, y) out of the street food carts (and their cooks): returns true with the pushed
 * position in [out] when one was in the way.
 */
fun GameEngine.clearOfCarts(x: Float, y: Float, r: Float, out: FloatArray): Boolean {
    val pr = props
    val tent = pr.cartStyle == StreetProps.CART_POCHA
    // the cart's extent: along the street, and from the customers' side (-) to the cook's (+)
    val hl = if (tent) 1.2f else if (pr.cartStyle == StreetProps.CART_YATAI) 1.0f else .85f
    val d0 = if (tent) -.9f else -.5f; val d1 = 1.2f
    var px = x; var py = y; var hit = false
    pr.forIn(x - 3f, y - 3f, x + 3f, y + 3f) { i ->
        if (pr.kind[i] != StreetProps.VENDOR) return@forIn
        val dx = pr.dirX[i]; val dy = pr.dirY[i]
        val ux = -dy; val uy = dx
        val qx = px - pr.x[i]; val qy = py - pr.y[i]
        val la = qx * ux + qy * uy; val ld = qx * dx + qy * dy
        val ca = la.coerceIn(-hl, hl); val cd = ld.coerceIn(d0, d1)
        val ea = la - ca; val ed = ld - cd
        val dd = ea * ea + ed * ed
        if (dd >= r * r) return@forIn
        val na: Float; val nd: Float
        if (dd > 1e-6f) {
            val d = kotlin.math.sqrt(dd)
            na = ca + ea / d * r; nd = cd + ed / d * r
        } else {
            // inside: out the nearest side
            val toA = hl - kotlin.math.abs(la); val toD0 = ld - d0; val toD1 = d1 - ld
            if (toA < toD0 && toA < toD1) { na = (if (la >= 0f) hl else -hl) + (if (la >= 0f) r else -r); nd = ld }
            else if (toD0 < toD1) { na = la; nd = d0 - r } else { na = la; nd = d1 + r }
        }
        px = pr.x[i] + ux * na + dx * nd; py = pr.y[i] + uy * na + dy * nd
        hit = true
    }
    out[0] = px; out[1] = py
    return hit
}
