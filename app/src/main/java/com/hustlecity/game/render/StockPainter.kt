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
 * you need to see inside), what each filled product slot shows, and see-through glass over them. The HD renderer
 * shows real product models (see [Models.product]); the classic renderer, and products without one, a few small
 * shapes. New items drop into place; far away, each run of the same product becomes a single block so it stays cheap.
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
        /** The model of an unopened box of stock. */
        private const val BOX = "_box"
        // what a pass draws: everything, only the product models (into the cache), or everything but them
        private const val ALL = 0
        private const val MODELS_ONLY = 1
        private const val NO_MODELS = 2
    }

    private val pt = FloatArray(3)
    private var def: FurnitureDef? = null
    private var px = 0f; private var py = 0f; private var rot = 0
    private var alpha = 1f
    private var pass = ALL

    /**
     * Draws placed display [p] (item [index] of the scene) with the stock [shelf] says it shows. With [cached], its
     * product models are already in the renderer's cache (see [drawModels]) and are left out.
     */
    fun draw(def: FurnitureDef, p: PlacedItem, index: Int, shelf: ShelfStock, lay: StockLayout, alpha: Float, cached: Boolean = false) {
        this.def = def; px = p.x.toFloat(); py = p.y.toFloat(); rot = p.rot; this.alpha = alpha
        pass = if (cached) NO_MODELS else ALL
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
        pass = ALL
    }

    /**
     * Only the product models display [p] shows, for the renderer's cache: drawn once into a frame the GPU keeps, they
     * don't have to be rebuilt every frame (see [cacheable]).
     */
    fun drawModels(def: FurnitureDef, p: PlacedItem, index: Int, shelf: ShelfStock, lay: StockLayout) {
        this.def = def; px = p.x.toFloat(); py = p.y.toFloat(); rot = p.rot; alpha = 1f
        val slots = shelf.slots(index) ?: return
        val ages = shelf.ages(index) ?: return
        val boxed = shelf.boxed(index)
        pass = MODELS_ONLY
        for (s in 0 until lay.slots) {
            val pid = slots[s] ?: continue
            item(lay, s, pid, ages[s], boxed)
        }
        pass = ALL
    }

    /**
     * Whether display [p]'s product models can come from the cache: near enough to show single products, and none of
     * them dropping into place right now.
     */
    fun cacheable(def: FurnitureDef, p: PlacedItem, index: Int, shelf: ShelfStock, lay: StockLayout): Boolean {
        if (!scene.smooth || far(p, def)) return false
        val slots = shelf.slots(index) ?: return false
        val ages = shelf.ages(index) ?: return false
        for (s in 0 until lay.slots) if (slots[s] != null && ages[s] < DROP) return false
        return true
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

    /** The model slot [slot] shows of product [pid] in the HD renderer (neighboring slots take turns between its looks), or null. */
    private fun model(pid: String, slot: Int): Models.Mesh? {
        if (!scene.smooth) return null
        val looks = Models.product(pid) ?: return null
        return looks[slot % looks.size]
    }

    /**
     * Draws model [m] in the display's own units: its middle at (cx, cy) and its bottom at [z] (or, [hang]ing from a
     * rail, its top), shrunk if need be to fit [cw] x [cd] x [h]. Hanging clothes only squeeze in where they must, and
     * a [stretch]ed model fills the space exactly (boxes of stock).
     */
    private fun place(m: Models.Mesh, cx: Float, cy: Float, z: Float, cw: Float, cd: Float, h: Float, hang: Boolean = false, stretch: Boolean = false): Boolean {
        val b = m.bounds
        val mw = b[3] - b[0]; val md = b[4] - b[1]; val mh = b[5] - b[2]
        val sx: Float; val sy: Float; val sz: Float
        if (stretch) { sx = cw / mw; sy = cd / md; sz = h / mh }
        else if (hang) { sx = min(1f, cw / mw); sy = min(1f, cd / md); sz = min(1f, h / mh) }
        else { val k = min(1f, min(cw / mw, min(cd / md, h / mh))); sx = k; sy = k; sz = k }
        // where the model's own origin goes, in the display's units
        val lx = cx - (b[0] + b[3]) * .5f * sx
        val ly = cy - (b[1] + b[4]) * .5f * sy
        val lz = if (hang) z - b[5] * sz else z - b[2] * sz
        val w = def!!.w.toFloat(); val d = def!!.d.toFloat()
        // turned with the display, like PartSorter.add
        return when (rot and 3) {
            0 -> scene.meshAt(m, px + lx, py + ly, lz, 1f, 0f, 0f, 1f, sx, sy, sz, alpha)
            1 -> scene.meshAt(m, px + d - ly, py + lx, lz, 0f, 1f, -1f, 0f, sx, sy, sz, alpha)
            2 -> scene.meshAt(m, px + w - lx, py + d - ly, lz, -1f, 0f, 0f, -1f, sx, sy, sz, alpha)
            else -> scene.meshAt(m, px + ly, py + w - lx, lz, 0f, -1f, 1f, 0f, sx, sy, sz, alpha)
        }
    }

    /** Neighboring items of the same product alternate a little in shade so they read as separate things. */
    private fun shade(color: Int, s: Int) = if (s % 2 == 0) color else scene.mix(color, 0xFF000000.toInt(), .1f)

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
            val m = model(BOX, 0)
            if (m != null) { if (pass != NO_MODELS) place(m, cx, cy, z, bw, bd, bh, stretch = true) }
            else if (pass != MODELS_ONLY) cbox(cx, cy, bw, bd, z, z + bh, if (s % 2 == 0) KRAFT else KRAFT_DARK, KRAFT_TOP)
            if (pass != MODELS_ONLY) cbox(cx, cy + bd / 2 + .006f, bw * .5f, .012f, z + bh * .35f, z + bh * .75f, prod?.color ?: RED)
            return
        }
        val m = model(pid, s)
        if (m != null) {
            if (pass != NO_MODELS) place(m, cx, cy, z, cw * .94f, cd * .94f, if (row.hang) h else h * .95f, hang = row.hang)
            return
        }
        if (pass == MODELS_ONLY) return
        if (row.hang) {
            // clothes on hangers: thin across the rail, hanging down from it
            val len = min(h, if (s % 3 == 1) .7f else .55f)
            val gw = min(cw * .6f, .07f); val gd = min(cd * .95f, .4f)
            cbox(cx, cy, .02f, .02f, z - .07f, z, SILVER)
            cbox(cx, cy, gw, gd, z - .07f - len, z - .07f, color, scene.mix(color, 0xFFFFFFFF.toInt(), .15f))
            return
        }
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
