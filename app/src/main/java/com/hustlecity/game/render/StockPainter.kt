package com.hustlecity.game.render

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.FurnitureDef
import com.hustlecity.game.data.StockLayout
import com.hustlecity.game.data.StockLayouts
import com.hustlecity.game.data.StockShape
import com.hustlecity.game.data.c
import com.hustlecity.game.engine.ShelfStock
import com.hustlecity.game.model.PlacedItem
import kotlin.math.min

/**
 * Draws a display of your store with what it holds: the model (without its placeholder goods, hollowed out where
 * you need to see inside), one small shape per filled product slot, and see-through glass over them. New items
 * drop into place; far away, each run of the same product becomes a single block so it stays cheap.
 */
class StockPainter(private val scene: Scene3D, private val parts: PartSorter) {
    companion object {
        private val KRAFT = c(0xFFC8A165)
        private val KRAFT_TOP = c(0xFFD9B98A)
        private val KRAFT_DARK = c(0xFFB38A55)
        private val SILVER = c(0xFFC9D1D9)
        private val PAPER = c(0xFFF2EEE6)
        private val BLACK = c(0xFF26292E)
        private val STEM = c(0xFF3E8E41)
        private val BUCKET = c(0xFF4F5A64)
        private val GOLD = c(0xFFE8C04A)
        private val VELVET = c(0xFF3A1420)
        private val RED = c(0xFFD0392B)
        private val WHITE = c(0xFFF5F5F5)
        private const val DROP = .3f
    }

    private val pt = FloatArray(3)
    private var def: FurnitureDef? = null
    private var px = 0f; private var py = 0f; private var rot = 0

    /** Draws placed display [p] (item [index] of the scene) with the stock [shelf] says it shows. */
    fun draw(def: FurnitureDef, p: PlacedItem, index: Int, shelf: ShelfStock, lay: StockLayout, alpha: Float) {
        this.def = def; px = p.x.toFloat(); py = p.y.toFloat(); rot = p.rot
        parts.begin()
        val model = def.parts
        for (k in model.indices) {
            if (k < 64 && (lay.hideMask ushr k) and 1L == 1L) continue
            val part = model[k]
            box(part.x0, part.y0, part.z0, part.x1, part.y1, part.z1, part.color)
        }
        val shell = lay.shell
        for (k in shell.indices) { val part = shell[k]; box(part.x0, part.y0, part.z0, part.x1, part.y1, part.z1, part.color) }
        val slots = shelf.slots(index)
        val ages = shelf.ages(index)
        if (slots != null && ages != null) {
            val boxed = shelf.boxed(index)
            if (far(p, def)) blocks(lay, slots, boxed) else for (s in 0 until lay.slots) {
                val pid = slots[s] ?: continue
                item(lay, s, pid, ages[s], boxed)
            }
        }
        val glass = lay.glass
        for (k in glass.indices) { val g = glass[k]; box(g.x0, g.y0, g.z0, g.x1, g.y1, g.z1, scene.withAlpha(StockLayouts.GLASS, .26f)) }
        parts.end(alpha)
    }

    /**
     * Where slot [slot] of a placed display is in the room: out = (x, y, z) of the middle of its item, turned with
     * the display. Used to send an item from the shelf to a customer's hands.
     */
    fun slotWorld(def: FurnitureDef, p: PlacedItem, lay: StockLayout, slot: Int, out: FloatArray) {
        lay.slotCenter(slot, pt)
        val row = lay.row(slot)
        val w = def.w.toFloat(); val d = def.d.toFloat()
        val ax = pt[0]; val ay = pt[1]
        when (p.rot % 4) {
            0 -> { out[0] = p.x + ax; out[1] = p.y + ay }
            1 -> { out[0] = p.x + d - ay; out[1] = p.y + ax }
            2 -> { out[0] = p.x + w - ax; out[1] = p.y + d - ay }
            else -> { out[0] = p.x + ay; out[1] = p.y + w - ax }
        }
        out[2] = if (row.hang) pt[2] - .4f else pt[2] + .06f
    }

    /** True when the display is so small on screen that single products wouldn't show (one block per run instead). */
    private fun far(p: PlacedItem, def: FurnitureDef): Boolean {
        val zc = scene.proj.camZOf(p.x + def.w * .5f, p.y + def.d * .5f, 1f)
        return zc > 0f && scene.proj.focal * .12f / zc < 3.5f
    }

    private fun box(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, color: Int, top: Int = color) =
        parts.add(def!!, px, py, rot, x0, y0, z0, x1, y1, z1, color, top)

    /** A box centered on (cx, cy) in the item's units, [w] across and [d] deep, from z0 to z1. */
    private fun cbox(cx: Float, cy: Float, w: Float, d: Float, z0: Float, z1: Float, color: Int, top: Int = color) =
        box(cx - w / 2, cy - d / 2, z0, cx + w / 2, cy + d / 2, z1, color, top)

    /** Neighboring items of the same product alternate a little in shade so they read as separate things. */
    private fun shade(color: Int, s: Int) = if (s % 2 == 0) color else scene.mix(color, 0xFF000000.toInt(), .1f)

    // ------------------------------------------------------------------ turned shapes (HD)

    private val rr = FloatArray(12); private val rz = FloatArray(12); private val rc = IntArray(12)
    private var rn = 0

    /** Starts a turned shape's profile. */
    private fun profile() { rn = 0 }

    /** The next ring of the profile: radius (scaled by the shape's size) and height; [color] is the band above it. */
    private fun ring(r: Float, z: Float, color: Int) { rr[rn] = r; rz[rn] = z; rc[rn] = color; rn++ }

    /** Draws the profile turned round (cx, cy) in the item's units, [rx] by [ry] across, straight into the room. */
    private fun turn(cx: Float, cy: Float, rx: Float, ry: Float, cap: Int, sides: Int = 7) {
        val d = def!!; val w = d.w.toFloat(); val dd = d.d.toFloat()
        var ox = rx; var oy = ry
        val wx: Float; val wy: Float
        when (rot % 4) {
            0 -> { wx = cx; wy = cy }
            1 -> { wx = dd - cy; wy = cx; ox = ry; oy = rx }
            2 -> { wx = w - cx; wy = dd - cy }
            else -> { wx = cy; wy = w - cx; ox = ry; oy = rx }
        }
        scene.lathe(px + wx, py + wy, ox, oy, rr, rz, rc, sides, cap, rn)
    }

    /** A ball (fruit, blooms, a scoop): [r] across, [hgt] tall, sitting at z. */
    private fun ball(cx: Float, cy: Float, r: Float, hgt: Float, z: Float, color: Int, sides: Int = 7, ry: Float = r) {
        profile()
        ring(0f, z, color); ring(.72f, z + hgt * .14f, color); ring(1f, z + hgt * .5f, color); ring(.72f, z + hgt * .86f, color); ring(0f, z + hgt, color)
        turn(cx, cy, r, ry, color, sides)
    }

    /**
     * The HD look of the round things on the shelves (bottles, cans, fruit, cups, buns...): turned shapes instead of boxes.
     * Returns false for the products that are boxes anyway (books, phones, gifts, trays), which the box shapes draw.
     */
    private fun roundItem(pid: String, shape: Int, s: Int, cx: Float, cy: Float, cw: Float, cd: Float, z: Float, h: Float, color: Int): Boolean {
        val light = scene.mix(color, 0xFFFFFFFF.toInt(), .55f)
        val dark = scene.mix(color, 0xFF000000.toInt(), .3f)
        when (shape) {
            StockShape.CAN -> {
                val r = min(min(cw, cd) * .36f, .04f); val hh = min(h * .85f, .14f)
                profile(); ring(.9f, z, color); ring(1f, z + .01f, color); ring(1f, z + hh - .012f, SILVER); ring(.86f, z + hh, SILVER)
                turn(cx, cy, r, r, SILVER, 7)
            }
            StockShape.BOTTLE -> {
                val r = min(min(cw, cd) * .31f, .04f); val bh = min(h * .55f, .2f); val nh = min(h * .3f, .1f)
                val capColor = if (pid == "wine") c(0xFF7A1E2C) else if (pid == "whiskey") BLACK else WHITE
                profile()
                ring(.92f, z, color); ring(1f, z + .012f, color); ring(1f, z + bh * .3f, light); ring(1f, z + bh * .75f, color)
                ring(1f, z + bh, dark); ring(.34f, z + bh + nh * .45f, dark); ring(.33f, z + bh + nh * .8f, capColor); ring(.36f, z + bh + nh, capColor)
                turn(cx, cy, r, r, capColor, 7)
            }
            StockShape.ROLLS -> {
                val r = min(min(cw, cd) * .22f, .055f); val hh = min(h * .85f, .12f)
                for (k in 0 until 4) {
                    val ox = (if (k % 2 == 0) -1 else 1) * r * 1.02f; val oy = (if (k < 2) -1 else 1) * r * 1.02f
                    profile(); ring(1f, z, PAPER); ring(1f, z + hh, c(0xFF8EC5E8))
                    turn(cx + ox, cy + oy, r, r, PAPER, 6)
                }
            }
            StockShape.UMBRELLA -> {
                val r = .026f; val hh = min(h * .92f, .42f)
                profile(); ring(.2f, z, BLACK); ring(.7f, z + hh * .12f, color); ring(1f, z + hh * .55f, color); ring(.35f, z + hh * .95f, dark); ring(0f, z + hh, dark)
                turn(cx, cy, r, r, color, 6)
                cbox(cx, cy, .016f, .05f, z + hh, z + hh + .03f, BLACK)
            }
            StockShape.FLOWERS -> {
                val bw = min(min(cw, cd) * .28f, .1f)
                // a bucket, a bundle of stems and a bunch of blooms
                profile(); ring(.8f, z, BUCKET); ring(1f, z + .15f, BUCKET); turn(cx, cy, bw, bw, c(0xFF2E3740), 7)
                profile(); ring(.5f, z + .15f, STEM); ring(.5f, z + min(h * .62f, .3f), STEM); turn(cx, cy, bw, bw, STEM, 5)
                val top = z + min(h * .62f, .3f)
                for (k in 0 until 4) {
                    val a = k * 1.5708f + s * .7f
                    val bloom = if (k == 3) light else if (pid == "flower_exp" && k % 2 == 1) c(0xFFF2F2F2) else color
                    ball(cx + kotlin.math.cos(a) * bw * .55f, cy + kotlin.math.sin(a) * bw * .55f, bw * .55f, bw * .9f, top - bw * .2f + (k % 2) * .02f, bloom, 6)
                }
            }
            StockShape.PRODUCE -> when (pid) {
                "bananas" -> for (k in 0 until 3) {
                    val oy = (k - 1) * min(cd * .22f, .03f)
                    ball(cx, cy + oy, min(cw * .4f, .08f), .035f, z + (k % 2) * .012f, if (k == 1) c(0xFFF7D44C) else color, 6, ry = .02f)
                }
                "carrots" -> for (k in 0 until 3) {
                    val ox = (k - 1) * min(cw * .26f, .035f); val oy = (if (k == 1) .02f else -.015f)
                    profile(); ring(0f, z, color); ring(.018f, z + .085f, color); ring(.012f, z + .09f, STEM); ring(0f, z + .13f, STEM)
                    turn(cx + ox, cy + oy, 1f, 1f, STEM, 5)
                }
                "lettuce" -> {
                    val r = min(min(cw, cd) * .38f, .07f)
                    ball(cx, cy, r, r * 1.1f, z, color, 7)
                    ball(cx, cy, r * .62f, r * .8f, z + r * .55f, light, 6)
                }
                else -> for (k in 0 until 4) {
                    // apples: a little heap
                    val r = min(min(cw, cd) * .2f, .04f)
                    val ox = (if (k % 2 == 0) -1 else 1) * r * .95f; val oy = (if (k < 2) -1 else 1) * r * .95f
                    ball(cx + ox, cy + oy, r, r * 1.8f, z, if ((k + s) % 3 == 0) dark else color, 6)
                }
            }
            StockShape.TUB -> {
                val r = min(min(cw, cd) * .38f, .08f); val hh = min(h * .55f, .11f)
                profile(); ring(.88f, z, color); ring(1f, z + hh, WHITE); ring(1.02f, z + hh + .012f, WHITE)
                turn(cx, cy, r, r, WHITE, 8)
            }
            StockShape.PASTRY -> if (pid == "croissant") {
                ball(cx, cy, min(cw * .4f, .065f), .045f, z, color, 6, ry = min(cd * .3f, .032f))
            } else {
                profile(); ring(.8f, z, PAPER); ring(1f, z + .042f, color); ring(1.06f, z + .055f, color); ring(.6f, z + .082f, light); ring(0f, z + .095f, light)
                turn(cx, cy, .034f, .034f, light, 7)
            }
            StockShape.CUP -> {
                val body = if (pid == "slushie") color else WHITE
                val lid = if (pid == "slushie") WHITE else color
                profile(); ring(.78f, z, body); ring(1f, z + min(h * .78f, .1f), lid); ring(1.04f, z + min(h * .78f, .1f) + .012f, lid)
                if (pid == "slushie") { ring(.5f, z + min(h * .78f, .1f) + .03f, lid); ring(0f, z + min(h * .78f, .1f) + .035f, lid) }
                turn(cx, cy, .036f, .036f, lid, 7)
            }
            StockShape.PATTY -> {
                if (pid == "hotdog") return false
                val r = min(min(cw, cd) * .38f, .058f)
                val bun = c(0xFFD9A35B)
                profile(); ring(.9f, z, bun); ring(1f, z + .018f, c(0xFF5A3A22)); ring(1.03f, z + .033f, c(0xFF5FAE45)); ring(1.03f, z + .039f, bun)
                ring(.94f, z + .055f, bun); ring(.55f, z + .07f, bun); ring(0f, z + .075f, bun)
                turn(cx, cy, r, r, bun, 8)
            }
            StockShape.BUCKET -> {
                if (pid == "cotton_candy") {
                    cbox(cx, cy, .01f, .01f, z, z + .1f, WHITE)
                    ball(cx, cy, .05f, .09f, z + .08f, color, 7)
                } else {
                    val hh = min(h * .6f, .12f)
                    profile(); ring(.78f, z, RED); ring(1f, z + hh, WHITE); ring(1.04f, z + hh + .01f, color); ring(.6f, z + hh + .035f, color); ring(0f, z + hh + .045f, color)
                    turn(cx, cy, .045f, .045f, color, 7)
                }
            }
            StockShape.GLASS -> if (pid == "cocktail") {
                profile(); ring(.6f, z, SILVER); ring(.6f, z + .006f, SILVER); ring(.1f, z + .01f, SILVER); ring(.1f, z + .055f, color); ring(1f, z + .1f, color)
                turn(cx, cy, .042f, .042f, color, 7)
            } else {
                val hh = min(h * .5f, .12f)
                profile(); ring(.85f, z, color); ring(1f, z + hh * .82f, WHITE); ring(1f, z + hh, WHITE)
                turn(cx, cy, .032f, .032f, WHITE, 7)
            }
            else -> return false
        }
        return true
    }

    private fun item(lay: StockLayout, s: Int, pid: String, age: Float, boxed: Boolean) {
        lay.slotCenter(s, pt)
        val row = lay.row(s)
        val cw = lay.cellW(s); val cd = lay.cellD(s)
        val cx = pt[0]; val cy = pt[1]
        // new stock drops into place
        val k = if (age < DROP) 1f - age / DROP else 0f
        val z = pt[2] + k * k * .22f
        val prod = Catalog.product(pid)
        val color = shade(prod?.color ?: KRAFT, s)
        val h = row.h
        if (boxed) {
            // a cardboard box of stock with the product's label on the front
            val bw = cw * .9f; val bd = cd * .9f; val bh = h * .92f
            cbox(cx, cy, bw, bd, z, z + bh, if (s % 2 == 0) KRAFT else KRAFT_DARK, KRAFT_TOP)
            cbox(cx, cy + bd / 2 + .006f, bw * .5f, .012f, z + bh * .35f, z + bh * .75f, prod?.color ?: RED)
            return
        }
        if (row.hang) {
            // clothes on hangers: thin across the rail, hanging down from it
            val len = min(h, if (s % 3 == 1) .7f else .55f)
            val gw = min(cw * .6f, .07f); val gd = min(cd * .95f, .4f)
            cbox(cx, cy, .02f, .02f, z - .07f, z, SILVER)
            cbox(cx, cy, gw, gd, z - .07f - len, z - .07f, color, scene.mix(color, 0xFFFFFFFF.toInt(), .15f))
            return
        }
        // in HD, round things are turned shapes, near enough to tell
        if (scene.gpu != null && roundItem(pid, StockLayouts.shapeOf(pid), s, cx, cy, cw, cd, z, h, color)) return
        when (StockLayouts.shapeOf(pid)) {
            StockShape.CAN -> { val w = min(cw * .72f, .085f); cbox(cx, cy, w, min(cd * .8f, w), z, z + min(h * .85f, .15f), color, SILVER) }
            StockShape.BOTTLE -> {
                val w = min(cw * .62f, .085f); val bh = min(h * .6f, .22f)
                cbox(cx, cy, w, w, z, z + bh, color)
                cbox(cx, cy, w * .36f, w * .36f, z + bh, z + bh + min(h * .3f, .1f), scene.mix(color, 0xFF000000.toInt(), .25f))
            }
            StockShape.BAR -> cbox(cx, cy, min(cw * .85f, .17f), min(cd * .7f, .12f), z, z + min(h * .45f, .1f), color, scene.mix(color, 0xFFFFFFFF.toInt(), .3f))
            StockShape.ROLLS -> cbox(cx, cy, min(cw * .86f, .2f), min(cd * .8f, .22f), z, z + min(h * .85f, .26f), PAPER, c(0xFF8EC5E8))
            StockShape.GIFT -> {
                val g = min(min(cw * .78f, cd * .8f), min(h * .75f, .16f))
                cbox(cx, cy, g, g, z, z + g, color)
                cbox(cx, cy, g * 1.04f, g * .22f, z + g * .02f, z + g * 1.03f, GOLD)
            }
            StockShape.UMBRELLA -> {
                cbox(cx, cy, .045f, .045f, z, z + min(h * .92f, .42f), color)
                cbox(cx, cy, .02f, .06f, z + min(h * .92f, .42f), z + min(h * .92f, .42f) + .03f, BLACK)
            }
            StockShape.BOOK -> {
                val bw = cw * .42f; val bd = min(cd * .85f, .3f); val bh = min(h * .85f, .27f)
                cbox(cx - bw * .52f, cy, bw, bd, z, z + bh, color, PAPER)
                cbox(cx + bw * .52f, cy, bw, bd, z, z + bh * (if (s % 3 == 0) .82f else .92f), scene.mix(color, 0xFFFFFFFF.toInt(), .22f), PAPER)
            }
            StockShape.MAGAZINE -> cbox(cx, cy, min(cw * .82f, .21f), .02f, z, z + min(h * .9f, .27f), color, PAPER)
            StockShape.SHOE -> {
                val sw = cw * .3f; val sd = min(cd * .9f, .25f); val sh = min(h * .8f, .09f)
                cbox(cx - sw * .58f, cy, sw, sd, z, z + sh, color, scene.mix(color, 0xFFFFFFFF.toInt(), .2f))
                cbox(cx + sw * .58f, cy, sw, sd, z, z + sh, color, scene.mix(color, 0xFFFFFFFF.toInt(), .2f))
            }
            StockShape.FLOWERS -> {
                val bw = min(min(cw, cd) * .55f, .2f)
                cbox(cx, cy, bw, bw, z, z + .16f, BUCKET)
                cbox(cx, cy, bw * .5f, bw * .5f, z + .16f, z + min(h * .7f, .32f), STEM)
                cbox(cx, cy, bw * 1.2f, bw * 1.2f, z + min(h * .7f, .32f), z + min(h * .7f, .32f) + .09f, color)
            }
            StockShape.PRODUCE -> {
                cbox(cx, cy, cw * .84f, cd * .84f, z, z + min(h * .4f, .07f), color)
                cbox(cx, cy, cw * .5f, cd * .5f, z + min(h * .4f, .07f), z + min(h * .4f, .07f) + min(h * .3f, .05f), scene.mix(color, 0xFFFFFFFF.toInt(), .12f))
            }
            StockShape.TRAY -> cbox(cx, cy, min(cw * .86f, .24f), min(cd * .82f, .17f), z, z + min(h * .3f, .06f), c(0xFFD5DBE2), color)
            StockShape.TUB -> { val w = min(cw * .76f, .17f); cbox(cx, cy, w, min(cd * .76f, w), z, z + min(h * .55f, .12f), color, WHITE) }
            StockShape.PASTRY -> {
                if (pid == "croissant") cbox(cx, cy, min(cw * .75f, .12f), min(cd * .6f, .07f), z, z + .05f, color)
                else {
                    cbox(cx, cy, .07f, .07f, z, z + .05f, PAPER)
                    cbox(cx, cy, .075f, .075f, z + .05f, z + .09f, color)
                }
            }
            StockShape.CUP -> cbox(cx, cy, min(cw * .6f, .07f), min(cd * .6f, .07f), z, z + min(h * .85f, .1f), if (pid == "slushie") color else WHITE, color)
            StockShape.PATTY -> {
                if (pid == "hotdog") cbox(cx, cy, min(cw * .8f, .15f), .045f, z, z + .04f, color, scene.mix(color, 0xFFFFFFFF.toInt(), .1f))
                else cbox(cx, cy, min(cw * .7f, .11f), min(cd * .7f, .11f), z, z + .03f, color, scene.mix(color, 0xFF000000.toInt(), .2f))
            }
            StockShape.FRIES -> {
                cbox(cx, cy, .065f, .05f, z, z + .08f, RED)
                cbox(cx, cy, .055f, .04f, z + .08f, z + .12f, color)
            }
            StockShape.PIZZA -> cbox(cx, cy, min(cw * .82f, .3f), min(cd * .82f, .3f), z, z + .045f, KRAFT, color)
            StockShape.BUCKET -> {
                cbox(cx, cy, .085f, .085f, z, z + min(h * .6f, .13f), if (pid == "popcorn") RED else WHITE)
                cbox(cx, cy, .09f, .09f, z + min(h * .6f, .13f), z + min(h * .6f, .13f) + .035f, color)
            }
            StockShape.PHONE -> {
                cbox(cx, cy, .08f, .06f, z, z + .02f, WHITE)
                when (pid) {
                    "earbuds" -> cbox(cx, cy, .055f, .045f, z + .02f, z + .05f, color, WHITE)
                    "smartwatch" -> cbox(cx, cy, .045f, .02f, z + .02f, z + .07f, color, BLACK)
                    else -> cbox(cx, cy, .065f, .014f, z + .02f, z + .14f, color, BLACK)
                }
            }
            StockShape.LAPTOP -> {
                val lw = min(cw * .8f, .24f); val ld = min(cd * .7f, .17f)
                cbox(cx, cy, lw, ld, z, z + .015f, color)
                cbox(cx, cy - ld * .48f, lw, .014f, z + .015f, z + .015f + ld * .9f, color, BLACK)
            }
            StockShape.HEADPHONES -> {
                cbox(cx, cy, .03f, .03f, z, z + .12f, WHITE)
                cbox(cx, cy, min(cw * .7f, .13f), .05f, z + .1f, z + .17f, color, scene.mix(color, 0xFF000000.toInt(), .3f))
            }
            StockShape.JEWEL -> {
                // on a velvet pad: a ring with its stone, a necklace laid out flat, or a watch on its band
                cbox(cx, cy, min(cw * .8f, .11f), min(cd * .8f, .11f), z, z + .025f, VELVET)
                val shine = scene.mix(color, 0xFFFFFFFF.toInt(), .45f)
                when (pid) {
                    "ring" -> {
                        cbox(cx, cy, .05f, .05f, z + .025f, z + .05f, GOLD, shine)
                        cbox(cx, cy, .025f, .025f, z + .05f, z + .07f, c(0xFF8FE3FF), c(0xFFE8FBFF))
                    }
                    "necklace" -> {
                        cbox(cx, cy - .025f, .08f, .014f, z + .025f, z + .035f, color, shine)
                        cbox(cx, cy + .015f, .03f, .03f, z + .025f, z + .045f, c(0xFF2F80ED), c(0xFF8FC7FF))
                    }
                    else -> {
                        cbox(cx, cy, .03f, .09f, z + .025f, z + .04f, c(0xFF3A3A3A))
                        cbox(cx, cy, .05f, .05f, z + .025f, z + .055f, color, c(0xFF1C2A3A))
                    }
                }
            }
            StockShape.GLASS -> cbox(cx, cy, min(cw * .62f, .07f), min(cd * .5f, .07f), z, z + min(h * .5f, .13f), color, if (pid == "draft_beer") WHITE else color)
            else -> cbox(cx, cy, min(cw * .8f, .15f), min(cd * .8f, .12f), z, z + min(h * .75f, .2f), color, scene.mix(color, 0xFFFFFFFF.toInt(), .25f))
        }
    }

    /** Far away: each run of slots showing the same product on a level becomes one block. */
    private fun blocks(lay: StockLayout, slots: Array<String?>, boxed: Boolean) {
        var s = 0
        while (s < lay.slots) {
            val pid = slots[s]
            if (pid == null) { s++; continue }
            val row = lay.row(s)
            lay.slotCenter(s, pt)
            val sx0 = pt[0] - lay.cellW(s) / 2; val sy0 = pt[1] - lay.cellD(s) / 2; val z = pt[2]
            var e = s
            while (e + 1 < lay.slots && slots[e + 1] == pid && lay.row(e + 1) === row) e++
            lay.slotCenter(e, pt)
            val sx1 = pt[0] + lay.cellW(e) / 2; val sy1 = pt[1] + lay.cellD(e) / 2
            val color = if (boxed) KRAFT else Catalog.product(pid)?.color ?: KRAFT
            val x0 = minOf(sx0, sx1) + .02f; val x1 = maxOf(sx0, sx1) - .02f
            val y0 = minOf(sy0, sy1) + .02f; val y1 = maxOf(sy0, sy1) - .02f
            if (row.hang) box(x0, y0, z - .6f, x1, y1, z - .07f, color)
            else box(x0, y0, z, x1, y1, z + row.h * (if (boxed) .9f else .6f), color)
            s = e + 1
        }
    }
}
