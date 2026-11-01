package com.hustlecity.game.render

import com.hustlecity.game.data.FurnitureDef
import com.hustlecity.game.data.c
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hand-built models of the furniture people see most (seats, tables, beds, plants, lamps, registers, desks, speakers
 * and a few pieces of gym gear) for the HD renderer: rounded cushions, turned legs and poles, round seats and tops,
 * leaves on the plants, screens that glow. Each is drawn in the item's own frame (x across its [FurnitureDef.w] tiles,
 * y over its [FurnitureDef.d], z up, in meters; customers stand on the +y side, staff behind), turned like the item,
 * and keeps the size, seat heights and front of the box model it stands in for, which the classic renderer still
 * draws. Displays keep their box models (their goods sit on those boxes), bevelled by [PartSorter].
 */
class FurnitureModels(private val scene: Scene3D) {
    companion object {
        private val MODELS = setOf(
            "sofa", "lounge_sofa", "table_set", "barstool", "bench", "plant_large", "plant_small", "floor_lamp", "bed", "trash_can", "rug",
            "cash_register", "checkout_counter", "office_desk", "desk", "meeting_table", "boxing_bag", "weight_rack", "speaker_small",
            "speaker_medium", "speaker_large", "water_cooler", "mannequin", "tv", "home_fridge", "hair_chair",
            "self_checkout", "order_kiosk", "waiting_chairs", "coffee_table",
        )
        private val WOOD = c(0xFF8B5A3C)
        private val WOOD_L = c(0xFFB07A52)
        private val WOOD_D = c(0xFF5D4037)
        private val STEEL = c(0xFFB8C0CA)
        private val CHROME = c(0xFFD9DEE4)
        private val DARK = c(0xFF2E3238)
        private val BLACK = c(0xFF1E2126)
        private val WHITE = c(0xFFE9EDF2)
        private val LINEN = c(0xFFF4F1EA)
        private val SCREEN = c(0xFF56CCF2)
        private val SOIL = c(0xFF4A3B2E)
        private val LEAVES = intArrayOf(c(0xFF2E8B57), c(0xFF3CB371), c(0xFF27AE60), c(0xFF4CAF50))
    }

    fun has(id: String) = id in MODELS

    private var alpha = 1f
    /** [color] as drawn: see-through while the item is being placed. */
    private fun k(color: Int) = if (alpha < 1f) scene.withAlpha(color, alpha * ((color ushr 24) / 255f)) else color

    /** Draws [def] with its corner at grid (x, y), turned [rot] quarter turns; [alpha] below 1 for a ghost being placed. */
    fun draw(def: FurnitureDef, x: Float, y: Float, rot: Int, alpha: Float) {
        this.alpha = alpha
        val w = def.w.toFloat(); val d = def.d.toFloat()
        // the same turn as PartSorter's: local (lx, ly) lands at the item's corner turned rot quarter turns
        when (rot and 3) {
            0 -> scene.setFrame(x, y, 1f, 0f)
            1 -> scene.setFrame(x + d, y, 0f, 1f)
            2 -> scene.setFrame(x + w, y + d, -1f, 0f)
            else -> scene.setFrame(x, y + w, 0f, -1f)
        }
        val material = scene.material
        try {
            when (def.id) {
                "sofa" -> sofa(def.parts[0].color, def.parts[0].color, c(0xFF3E2723), plush = false)
                "lounge_sofa" -> sofa(def.parts[0].color, def.parts[1].color, c(0xFFC9A227), plush = true)
                "table_set" -> tableSet()
                "barstool" -> barstool(def.parts.getOrNull(1)?.color ?: c(0xFFC8E6A0))
                "bench" -> bench()
                "plant_large" -> plant(large = true)
                "plant_small" -> plant(large = false)
                "floor_lamp" -> floorLamp(def.parts.last().color)
                "bed" -> bed(def.parts.getOrNull(1)?.color ?: c(0xFF5B4FD6))
                "trash_can" -> trashCan(def.parts[0].color, def.parts.getOrNull(1)?.color ?: DARK)
                "rug" -> rug(def.parts[0].color, def.parts.getOrNull(1)?.color ?: c(0xFFC9A227))
                "cash_register" -> register(def.parts[0].color, def.parts.getOrNull(1)?.color ?: c(0xFF9AA3AD))
                "checkout_counter" -> checkout(def.parts[0].color)
                "office_desk" -> desk(office = true)
                "desk" -> desk(office = false)
                "meeting_table" -> meetingTable()
                "boxing_bag" -> boxingBag(def.parts[0].color)
                "weight_rack" -> weightRack()
                "speaker_small" -> speaker(.3f, .7f, .7f, 1)
                "speaker_medium" -> speaker(.25f, .75f, 1.1f, 2)
                "speaker_large" -> speaker(.15f, .85f, 1.5f, 3)
                "water_cooler" -> waterCooler()
                "mannequin" -> mannequin(def.parts.getOrNull(2)?.color ?: c(0xFF3949AB))
                "tv" -> tv()
                "home_fridge" -> homeFridge()
                "hair_chair" -> hairChair()
                "self_checkout" -> selfCheckout()
                "order_kiosk" -> orderKiosk()
                "waiting_chairs" -> waitingChairs()
                "coffee_table" -> coffeeTable()
            }
        } finally {
            scene.clearFrame()
            scene.material = material
            scene.glow = false
            scene.sphereDetail = 1
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun rb(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, r: Float, top: Int, side: Int = top, steps: Int = 2, under: Boolean = false) =
        scene.roundBox(x0, y0, z0, x1, y1, z1, r, k(top), k(side), steps, under)

    private fun bx(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, top: Int, side: Int = top) = scene.box(x0, y0, z0, x1, y1, z1, k(top), k(side))

    private fun cyl(cx: Float, cy: Float, r: Float, z0: Float, z1: Float, color: Int, top: Int = color, sides: Int = 10) =
        scene.cylinder(cx, cy, r, z0, z1, sides, k(color), k(top))

    private val lr = FloatArray(16)
    private val lz = FloatArray(16)
    private val lc = IntArray(16)
    private var ln = 0

    /** A point of a turned profile: radius [r] at height [z], the band up to the next point in [color]. */
    private fun pt(r: Float, z: Float, color: Int) { if (ln < 16) { lr[ln] = r; lz[ln] = z; lc[ln] = k(color); ln++ } }

    /** Turns the profile given with [pt] round (cx, cy). */
    private fun turned(cx: Float, cy: Float, sides: Int = 12) {
        if (ln >= 2) scene.lathe(cx, cy, 1f, 1f, lr, lz, lc, sides, lc[ln - 1], ln)
        ln = 0
    }

    /** An open ring of upright strips (a foot rest, a rail), no top or bottom. */
    private fun hoop(cx: Float, cy: Float, r: Float, z0: Float, z1: Float, color: Int, n: Int = 12) {
        for (i in 0 until n) {
            val a0 = i * 6.2832f / n; val a1 = (i + 1) * 6.2832f / n
            scene.quad(cx + cos(a0) * r, cy + sin(a0) * r, z0, cx + cos(a1) * r, cy + sin(a1) * r, z0, cx + cos(a1) * r, cy + sin(a1) * r, z1, cx + cos(a0) * r, cy + sin(a0) * r, z1, k(color))
        }
    }

    private fun ball(x: Float, y: Float, z: Float, r: Float, color: Int, squash: Float = .85f) = scene.blob(x, y, z, r, k(color), 10, 0f, 0f, squash)

    private val px = FloatArray(16)
    private val py = FloatArray(16)
    private val pz = FloatArray(16)

    /** A disc standing upright facing ±y at depth [y]: a speaker cone, a clock, a dial. */
    private fun uprightDisc(cx: Float, y: Float, cz: Float, r: Float, color: Int, n: Int = 12) {
        for (i in 0 until n) { val a = i * 6.2832f / n; px[i] = cx + cos(a) * r; py[i] = y; pz[i] = cz + sin(a) * r }
        scene.poly(n, px, py, pz, k(color))
    }

    /** A flat panel between two corners that may lean: (x0, ya, z0) to (x1, yb, z1), its face toward ±y. */
    private fun panel(x0: Float, ya: Float, z0: Float, x1: Float, yb: Float, z1: Float, color: Int) {
        px[0] = x0; py[0] = ya; pz[0] = z0
        px[1] = x1; py[1] = ya; pz[1] = z0
        px[2] = x1; py[2] = yb; pz[2] = z1
        px[3] = x0; py[3] = yb; pz[3] = z1
        scene.poly(4, px, py, pz, k(color))
    }

    /** One leaf: from its stem at (bx, by, bz) out [len] along angle [a], rising [rise] at its middle and drooping at the tip. */
    private fun leaf(bx: Float, by: Float, bz: Float, a: Float, len: Float, rise: Float, width: Float, color: Int) {
        val ux = cos(a); val uy = sin(a); val vx = -uy; val vy = ux
        px[0] = bx; py[0] = by; pz[0] = bz
        px[1] = bx + ux * len * .45f + vx * width; py[1] = by + uy * len * .45f + vy * width; pz[1] = bz + rise
        px[2] = bx + ux * len; py[2] = by + uy * len; pz[2] = bz + rise * .55f
        px[3] = bx + ux * len * .45f - vx * width; py[3] = by + uy * len * .45f - vy * width; pz[3] = bz + rise
        scene.poly(4, px, py, pz, k(color))
    }

    /** A chair at (cx, cy) whose sitter faces along [face] (0 -y, 1 +x, 2 +y, 3 -x): seat at [seat] m, legs, a back behind. */
    private fun chair(cx: Float, cy: Float, face: Int, seat: Float, seatColor: Int, frame: Int) {
        val hw = .2f
        scene.material = Mat.METAL
        for (sx in -1..1 step 2) for (sy in -1..1 step 2) cyl(cx + sx * (hw - .03f), cy + sy * (hw - .03f), .018f, 0f, seat - .04f, frame, sides = 6)
        scene.material = Mat.FABRIC
        rb(cx - hw, cy - hw, seat - .05f, cx + hw, cy + hw, seat, .025f, seatColor)
        // the back, on the side away from where the sitter looks
        val bxs = when (face) { 1 -> -1f; 3 -> 1f; else -> 0f }
        val bys = when (face) { 0 -> 1f; 2 -> -1f; else -> 0f }
        val ex = cx + bxs * (hw - .02f); val ey = cy + bys * (hw - .02f)
        scene.material = Mat.METAL
        if (bxs != 0f) { cyl(ex, cy - hw + .03f, .016f, seat, seat + .42f, frame, sides = 6); cyl(ex, cy + hw - .03f, .016f, seat, seat + .42f, frame, sides = 6) }
        else { cyl(cx - hw + .03f, ey, .016f, seat, seat + .42f, frame, sides = 6); cyl(cx + hw - .03f, ey, .016f, seat, seat + .42f, frame, sides = 6) }
        scene.material = Mat.FABRIC
        if (bxs != 0f) rb(ex - .025f, cy - hw, seat + .2f, ex + .025f, cy + hw, seat + .44f, .02f, seatColor)
        else rb(cx - hw, ey - .025f, seat + .2f, cx + hw, ey + .025f, seat + .44f, .02f, seatColor)
    }

    // ------------------------------------------------------------------ seats and tables

    /** A sofa two tiles wide with its back along y = 0: legs, a frame, two seat and two back cushions, rolled arms and two throw pillows. */
    private fun sofa(body: Int, cushion: Int, feet: Int, plush: Boolean) {
        val frame = scene.mix(body, 0xFF000000.toInt(), .22f)
        scene.material = if (plush) Mat.METAL else Mat.WOOD
        for (lx in floatArrayOf(.14f, 1.86f)) for (ly in floatArrayOf(.14f, .86f)) {
            ln = 0; pt(.03f, 0f, feet); pt(.04f, .1f, feet); pt(0f, .12f, feet); turned(lx, ly, 8)
        }
        scene.material = Mat.FABRIC
        rb(.02f, .03f, .1f, 1.98f, .97f, .3f, .05f, frame)
        // seat cushions, puffed up off the frame
        val seat = if (plush) .42f else .47f
        rb(.2f, .3f, .27f, 1.0f, .98f, seat, .07f, cushion, under = true)
        rb(1.0f, .3f, .27f, 1.8f, .98f, seat, .07f, cushion, under = true)
        if (plush) {
            // a deep-buttoned velvet back in three channels
            for (i in 0 until 3) rb(.2f + i * .5333f, .04f, .28f, .2f + (i + 1) * .5333f, .3f, .96f, .1f, cushion, steps = 3)
        } else {
            rb(.2f, .04f, .28f, 1.0f, .32f, .9f, .09f, cushion, steps = 3)
            rb(1.0f, .04f, .28f, 1.8f, .32f, .9f, .09f, cushion, steps = 3)
        }
        // rolled arms
        val armTop = if (plush) .66f else .64f
        rb(0f, .02f, .1f, .22f, .98f, armTop, .1f, body, steps = 3)
        rb(1.78f, .02f, .1f, 2f, .98f, armTop, .1f, body, steps = 3)
        if (!plush) {
            rb(.26f, .3f, .45f, .56f, .44f, .74f, .07f, c(0xFFF2C94C), under = true)
            rb(1.44f, .3f, .45f, 1.74f, .44f, .74f, .07f, c(0xFF56CCF2), under = true)
        }
    }

    /** A café table for two: a round top on a pedestal, a chair either side facing it, a cup and a pastry. */
    private fun tableSet() {
        scene.material = Mat.METAL
        ln = 0; pt(.27f, 0f, DARK); pt(.24f, .03f, DARK); pt(.06f, .08f, DARK); pt(.045f, .1f, DARK); pt(.045f, .68f, DARK); pt(.12f, .7f, DARK); turned(1f, 1f, 12)
        scene.material = Mat.WOOD
        cyl(1f, 1f, .42f, .7f, .76f, WOOD_L, sides = 12)
        chair(.3f, 1f, 1, .45f, c(0xFF2E3238), c(0xFF1E2126))
        chair(1.7f, 1f, 3, .45f, c(0xFF2E3238), c(0xFF1E2126))
        scene.material = Mat.PLAIN
        cyl(.9f, .86f, .07f, .76f, .768f, WHITE, sides = 10)
        ln = 0; pt(.032f, .768f, WHITE); pt(.04f, .83f, WHITE); pt(.034f, .83f, c(0xFF5D3A1A)); turned(.9f, .86f, 10)
        cyl(1.14f, 1.12f, .09f, .76f, .766f, WHITE, sides = 10)
        ball(1.14f, 1.12f, .79f, .05f, c(0xFFE0A84C), .6f)
    }

    private fun barstool(seat: Int) {
        scene.material = Mat.METAL
        ln = 0; pt(.2f, 0f, DARK); pt(.19f, .02f, DARK); pt(.05f, .035f, DARK); turned(.5f, .5f, 12)
        cyl(.5f, .5f, .022f, .03f, .73f, STEEL, sides = 8)
        // the foot ring on its two spokes
        hoop(.5f, .5f, .16f, .3f, .325f, STEEL)
        bx(.34f, .49f, .3f, .66f, .51f, .315f, STEEL)
        scene.material = Mat.FABRIC
        ln = 0; pt(.16f, .72f, DARK); pt(.21f, .75f, seat); pt(.22f, .81f, seat); pt(.18f, .85f, seat); turned(.5f, .5f, 12)
    }

    private fun bench() {
        scene.material = Mat.METAL
        for (lx in floatArrayOf(.25f, 1.75f)) {
            rb(lx - .05f, .34f, 0f, lx + .05f, .38f, .4f, .015f, STEEL)
            rb(lx - .05f, .62f, 0f, lx + .05f, .66f, .4f, .015f, STEEL)
            rb(lx - .05f, .32f, .36f, lx + .05f, .68f, .4f, .015f, STEEL)
        }
        scene.material = Mat.WOOD
        for (i in 0 until 3) { val y0 = .3f + i * .14f; rb(.08f, y0, .4f, 1.92f, y0 + .12f, .48f, .02f, WOOD_L, WOOD) }
    }

    private fun meetingTable() {
        scene.material = Mat.METAL
        for (lx in floatArrayOf(.9f, 2.1f)) { ln = 0; pt(.25f, 0f, DARK); pt(.22f, .03f, DARK); pt(.05f, .06f, DARK); pt(.05f, .7f, DARK); turned(lx, 1f, 10) }
        scene.material = Mat.WOOD
        rb(.3f, .55f, .7f, 2.7f, 1.45f, .76f, .06f, WOOD, steps = 2)
        for (i in 0 until 3) {
            chair(.5f + i, .25f, 2, .45f, c(0xFF37474F), BLACK)
            chair(.5f + i, 1.75f, 0, .45f, c(0xFF37474F), BLACK)
        }
        scene.material = Mat.PLAIN
        // a laptop and papers
        rb(1.25f, .62f, .76f, 1.65f, .9f, .78f, .008f, c(0xFFB0B7BF))
        panel(1.25f, .62f, .78f, 1.65f, .6f, 1.0f, c(0xFF37474F))
        bx(2.0f, .7f, .76f, 2.25f, 1.0f, .765f, WHITE)
        bx(.7f, 1.1f, .76f, .92f, 1.38f, .765f, WHITE)
    }

    // ------------------------------------------------------------------ decor

    private fun plant(large: Boolean) {
        scene.material = Mat.PLASTER
        val pot = c(0xFFE0E0E0); val rim = c(0xFFC9C9C9)
        if (large) { ln = 0; pt(.15f, 0f, pot); pt(.2f, .38f, pot); pt(.23f, .4f, rim); pt(.23f, .45f, rim); pt(.2f, .45f, SOIL); turned(.5f, .5f, 12) }
        else { ln = 0; pt(.08f, 0f, pot); pt(.11f, .25f, pot); pt(.125f, .26f, rim); pt(.125f, .3f, rim); pt(.1f, .3f, SOIL); turned(.5f, .5f, 10) }
        scene.material = Mat.FOLIAGE
        val base = if (large) .45f else .3f
        val n = if (large) 13 else 8
        if (large) for (s in 0 until 3) {
            val a = s * 2.1f
            cyl(.5f + cos(a) * .04f, .5f + sin(a) * .04f, .015f, base, base + .55f + s * .2f, c(0xFF2E5E36), sides = 5)
        }
        scene.sphereDetail = 1
        if (large) {
            ball(.5f, .5f, 1.05f, .24f, LEAVES[0]); ball(.42f, .58f, 1.32f, .19f, LEAVES[1]); ball(.6f, .44f, 1.45f, .17f, LEAVES[2])
        } else ball(.5f, .5f, .48f, .13f, LEAVES[1])
        for (i in 0 until n) {
            val a = i * 2.39996f
            val z = base + (if (large) .25f + (i % 4) * .28f else .05f + (i % 3) * .1f)
            val len = if (large) .32f + (i % 3) * .06f else .17f + (i % 2) * .04f
            leaf(.5f + cos(a) * .04f, .5f + sin(a) * .04f, z, a, len, if (large) .14f else .08f, if (large) .09f else .055f, LEAVES[i % LEAVES.size])
        }
    }

    private fun floorLamp(shade: Int) {
        scene.material = Mat.METAL
        ln = 0; pt(.15f, 0f, DARK); pt(.15f, .025f, DARK); pt(.05f, .05f, DARK); turned(.5f, .5f, 12)
        cyl(.5f, .5f, .014f, .05f, 1.56f, DARK, sides = 6)
        scene.glow = true
        ball(.5f, .5f, 1.6f, .05f, c(0xFFFFF8E1))
        scene.material = Mat.FABRIC
        ln = 0; pt(.22f, 1.5f, shade); pt(.14f, 1.84f, shade); turned(.5f, .5f, 12)
        scene.glow = false
    }

    private fun rug(field: Int, pattern: Int) {
        scene.material = Mat.FABRIC
        rb(.1f, .1f, 0f, 1.9f, 1.9f, .024f, .024f, field, steps = 1)
        bx(.24f, .24f, .024f, 1.76f, 1.76f, .028f, pattern)
        bx(.32f, .32f, .028f, 1.68f, 1.68f, .031f, field)
        cyl(1f, 1f, .32f, .031f, .034f, pattern, sides = 12)
        cyl(1f, 1f, .16f, .034f, .036f, field, sides = 10)
        // fringe at both ends
        val fringe = scene.mix(c(0xFFF4F1EA), field, .15f)
        for (i in 0 until 12) {
            val x = .16f + i * .14f
            bx(x, .03f, 0f, x + .05f, .1f, .012f, fringe)
            bx(x, 1.9f, 0f, x + .05f, 1.97f, .012f, fringe)
        }
    }

    private fun trashCan(body: Int, lid: Int) {
        scene.material = Mat.METAL
        ln = 0; pt(.16f, 0f, body); pt(.19f, .76f, body); pt(.2f, .78f, lid); pt(.2f, .82f, lid); pt(.13f, .88f, lid); turned(.5f, .5f, 12)
        // the pedal
        rb(.44f, .66f, .02f, .56f, .74f, .05f, .01f, BLACK)
    }

    private fun mannequin(outfit: Int) {
        scene.material = Mat.METAL
        ln = 0; pt(.14f, 0f, DARK); pt(.14f, .02f, DARK); pt(.02f, .04f, DARK); turned(.5f, .5f, 12)
        cyl(.5f, .5f, .015f, .04f, .82f, CHROME, sides = 6)
        scene.material = Mat.FABRIC
        val skin = c(0xFFE0E0E0)
        // torso in the outfit: hips, waist, chest, shoulders
        ln = 0; pt(.13f, .8f, outfit); pt(.15f, .92f, outfit); pt(.12f, 1.1f, outfit); pt(.16f, 1.3f, outfit); pt(.15f, 1.42f, outfit); pt(.05f, 1.48f, skin); turned(.5f, .5f, 12)
        scene.material = Mat.PLAIN
        cyl(.5f, .5f, .035f, 1.46f, 1.55f, skin, sides = 8)
        ball(.5f, .5f, 1.64f, .095f, skin, 1.15f)
        // arms hanging at the sides, in the outfit's sleeves
        rb(.29f, .46f, 1.0f, .34f, .54f, 1.42f, .022f, outfit)
        rb(.66f, .46f, 1.0f, .71f, .54f, 1.42f, .022f, outfit)
    }

    private fun waterCooler() {
        scene.material = Mat.PAINT
        rb(.3f, .3f, 0f, .7f, .7f, 1.0f, .04f, WHITE)
        bx(.36f, .69f, .55f, .64f, .71f, .85f, c(0xFFBDC3C9))
        rb(.38f, .66f, .52f, .62f, .76f, .55f, .01f, c(0xFF9AA3AD))
        cyl(.44f, .73f, .018f, .72f, .8f, c(0xFF2F80ED), sides = 6)
        cyl(.56f, .73f, .018f, .72f, .8f, c(0xFFEB5757), sides = 6)
        scene.material = Mat.GLASS
        val water = scene.withAlpha(c(0xFF8EC5E8), .72f)
        ln = 0; pt(.05f, 1.0f, water); pt(.15f, 1.07f, water); pt(.16f, 1.12f, water); pt(.16f, 1.46f, water); pt(.12f, 1.52f, water); turned(.5f, .5f, 12)
    }

    // ------------------------------------------------------------------ home

    private fun bed(blanket: Int) {
        scene.material = Mat.WOOD
        for (lx in floatArrayOf(.08f, 1.92f)) for (ly in floatArrayOf(.1f, 1.92f)) cyl(lx, ly, .035f, 0f, .12f, WOOD_D, sides = 8)
        rb(0f, .04f, .1f, 2f, 2f, .34f, .03f, WOOD)
        // headboard: wood with a padded panel
        rb(0f, 0f, .1f, 2f, .1f, 1.02f, .04f, WOOD_D)
        scene.material = Mat.FABRIC
        rb(.1f, .08f, .42f, 1.9f, .14f, .96f, .05f, c(0xFFD7CCC8))
        rb(.05f, .12f, .32f, 1.95f, 1.97f, .5f, .07f, LINEN)
        // the duvet over the lower two thirds, its top folded back
        rb(.02f, .72f, .36f, 1.98f, 2f, .56f, .07f, blanket)
        rb(.02f, .72f, .54f, 1.98f, .9f, .6f, .03f, scene.mix(blanket, 0xFFFFFFFF.toInt(), .3f))
        rb(.2f, .16f, .48f, .92f, .52f, .64f, .07f, WHITE, under = true)
        rb(1.08f, .16f, .48f, 1.8f, .52f, .64f, .07f, WHITE, under = true)
    }

    private fun tv() {
        scene.material = Mat.WOOD
        for (lx in floatArrayOf(.16f, 1.84f)) for (ly in floatArrayOf(.26f, .64f)) cyl(lx, ly, .02f, 0f, .1f, DARK, sides = 6)
        rb(.1f, .2f, .1f, 1.9f, .7f, .45f, .025f, WOOD)
        bx(.98f, .69f, .14f, 1.02f, .71f, .41f, WOOD_D)
        rb(.5f, .69f, .26f, .7f, .72f, .29f, .01f, CHROME); rb(1.3f, .69f, .26f, 1.5f, .72f, .29f, .01f, CHROME)
        scene.material = Mat.METAL
        rb(.82f, .32f, .45f, 1.18f, .56f, .47f, .01f, DARK)
        bx(.97f, .42f, .47f, 1.03f, .46f, .62f, DARK)
        rb(.15f, .41f, .58f, 1.85f, .47f, 1.46f, .015f, BLACK)
        scene.material = Mat.GLASS
        panel(.19f, .471f, .62f, 1.81f, .471f, 1.42f, c(0xFF1E2A36))
    }

    private fun homeFridge() {
        scene.material = Mat.PAINT
        for (lx in floatArrayOf(.12f, .88f)) cyl(lx, .85f, .025f, 0f, .03f, DARK, sides = 6)
        rb(.05f, .1f, .03f, .95f, .95f, 1.9f, .05f, WHITE)
        bx(.06f, .95f, 1.2f, .94f, .965f, 1.22f, c(0xFFC9CED6))
        scene.material = Mat.METAL
        rb(.8f, .95f, .9f, .85f, 1f, 1.15f, .012f, STEEL)
        rb(.8f, .95f, 1.3f, .85f, 1f, 1.7f, .012f, STEEL)
    }

    // ------------------------------------------------------------------ counters and desks

    /** The point-of-sale: its screen toward the cashier (-y), a small one toward the customer, a card reader and a printer. */
    private fun pos(cx: Float, z: Float) {
        scene.material = Mat.METAL
        cyl(cx, .45f, .03f, z, z + .14f, DARK, sides = 8)
        rb(cx - .1f, .36f, z, cx + .1f, .54f, z + .02f, .01f, DARK)
        rb(cx - .27f, .4f, z + .14f, cx + .27f, .46f, z + .48f, .015f, BLACK)
        scene.glow = true
        panel(cx - .24f, .395f, z + .17f, cx + .24f, .395f, z + .45f, SCREEN)
        scene.glow = false
    }

    private fun register(body: Int, top: Int) {
        scene.material = Mat.PAINT
        rb(0f, .15f, 0f, 2f, .95f, 1.0f, .03f, body)
        bx(0f, .93f, 0f, 2f, .97f, .12f, BLACK)
        rb(.12f, .945f, .2f, 1.88f, .97f, .88f, .02f, scene.mix(body, 0xFFFFFFFF.toInt(), .18f))
        scene.material = Mat.STONE
        rb(0f, .1f, 1.0f, 2f, 1.0f, 1.06f, .025f, top)
        scene.material = Mat.METAL
        rb(.25f, .25f, 1.06f, .9f, .78f, 1.16f, .02f, DARK)
        bx(.3f, .2f, 1.08f, .85f, .25f, 1.14f, c(0xFF3A4048))
        pos(.57f, 1.16f)
        // the customer's display, the card reader and the receipt printer
        cyl(1.72f, .82f, .02f, 1.06f, 1.28f, DARK, sides = 6)
        rb(1.6f, .8f, 1.28f, 1.84f, .85f, 1.42f, .01f, BLACK)
        scene.glow = true; panel(1.62f, .853f, 1.3f, 1.82f, .853f, 1.4f, c(0xFF6FCF97)); scene.glow = false
        rb(1.18f, .7f, 1.06f, 1.34f, .9f, 1.1f, .012f, BLACK)
        scene.glow = true; bx(1.22f, .84f, 1.1f, 1.3f, .88f, 1.104f, c(0xFF6FCF97)); scene.glow = false
        rb(1.3f, .22f, 1.06f, 1.55f, .45f, 1.18f, .025f, WHITE)
        bx(1.36f, .44f, 1.15f, 1.49f, .46f, 1.16f, LINEN)
    }

    /** A self-checkout: a cabinet with the scanner glass on top, its screen facing the customer (+y), the card reader and a lane light. */
    private fun selfCheckout() {
        scene.material = Mat.PAINT
        rb(.12f, .15f, 0f, .88f, .78f, .84f, .03f, WHITE)
        bx(.12f, .74f, 0f, .88f, .79f, .1f, DARK)
        // a bag shelf down the side with a carrier bag on its hook rail
        rb(.14f, .52f, .45f, .86f, .78f, .48f, .01f, c(0xFFB8C0CA))
        scene.material = Mat.STONE
        rb(.1f, .15f, .84f, .9f, .92f, .9f, .02f, c(0xFF9AA3AD))
        // the scanner glass, its red line lit
        bx(.3f, .5f, .9f, .7f, .78f, .905f, c(0xFF1E2126))
        scene.glow = true; bx(.32f, .63f, .905f, .68f, .65f, .907f, c(0xFFFF3B30)); scene.glow = false
        // the screen on its post at the back, tilted up toward the customer
        scene.material = Mat.METAL
        rb(.46f, .16f, .9f, .54f, .24f, 1.1f, .01f, DARK)
        rb(.26f, .18f, 1.06f, .74f, .28f, 1.48f, .015f, BLACK)
        scene.glow = true
        panel(.29f, .29f, 1.1f, .71f, .29f, 1.45f, SCREEN)
        // its big green PAY button and a list of what's been scanned
        panel(.48f, .293f, 1.13f, .68f, .293f, 1.22f, c(0xFF6FCF97))
        for (k in 0 until 3) panel(.32f, .293f, 1.38f - k * .06f, .6f - k * .05f, .293f, 1.41f - k * .06f, WHITE)
        scene.glow = false
        // the card reader on the right
        rb(.74f, .62f, .9f, .86f, .78f, .97f, .01f, BLACK)
        scene.glow = true; bx(.77f, .7f, .97f, .83f, .75f, .972f, c(0xFF6FCF97)); scene.glow = false
        // the lane light: green while it's free
        cyl(.18f, .22f, .02f, .9f, 1.75f, CHROME, sides = 6)
        scene.glow = true; cyl(.18f, .22f, .05f, 1.75f, 1.86f, c(0xFF6FCF97), sides = 8); scene.glow = false
    }

    /** A self-order kiosk: a slim white column with a tall portrait touchscreen facing +y, its menu tiles lit. */
    private fun orderKiosk() {
        scene.material = Mat.METAL
        rb(.24f, .28f, 0f, .76f, .72f, .04f, .02f, DARK)
        rb(.42f, .42f, .04f, .58f, .56f, .95f, .02f, WHITE)
        scene.material = Mat.PAINT
        rb(.2f, .34f, .9f, .8f, .55f, 1.98f, .03f, WHITE)
        bx(.2f, .34f, 1.9f, .8f, .555f, 1.98f, c(0xFFEB5757))
        scene.glow = true
        panel(.24f, .556f, 1.04f, .76f, .556f, 1.86f, c(0xFFF4F1EA))
        // the menu: rows of pictures, and the order button
        val tiles = intArrayOf(c(0xFFE0A85A), c(0xFFEB5757), c(0xFF6FCF97), c(0xFFF2C94C), c(0xFF56CCF2), c(0xFFE0A85A))
        for (k in 0 until 6) {
            val tx = .28f + (k % 2) * .24f; val tz = 1.62f - (k / 2) * .2f
            panel(tx, .558f, tz, tx + .2f, .558f, tz + .16f, tiles[k])
        }
        panel(.3f, .558f, 1.08f, .7f, .558f, 1.16f, c(0xFF27AE60))
        scene.glow = false
        // the card reader under the screen
        rb(.42f, .5f, .92f, .58f, .62f, .99f, .01f, BLACK)
    }

    /** Three waiting room chairs on one beam, facing +y, with armrests between them. */
    private fun waitingChairs() {
        scene.material = Mat.METAL
        bx(.05f, .46f, .27f, 2.95f, .56f, .32f, DARK)
        for (lx in floatArrayOf(.18f, 2.82f)) {
            rb(lx - .04f, .48f, 0f, lx + .04f, .54f, .3f, .01f, CHROME)
            rb(lx - .05f, .2f, 0f, lx + .05f, .82f, .03f, .01f, CHROME)
        }
        for (i in 0 until 3) {
            val x0 = i + .07f; val x1 = i + .93f
            scene.material = Mat.FABRIC
            rb(x0, .28f, .38f, x1, .8f, .47f, .03f, c(0xFF37474F))
            rb(x0, .14f, .47f, x1, .25f, .95f, .03f, c(0xFF37474F))
            scene.material = Mat.METAL
            rb(x0 + .05f, .22f, .32f, x0 + .12f, .3f, .4f, .01f, DARK)
            rb(x1 - .12f, .22f, .32f, x1 - .05f, .3f, .4f, .01f, DARK)
        }
        // armrests at the ends and between the seats
        for (k in 0..3) {
            val ax = k.toFloat().coerceIn(.04f, 2.96f)
            rb(ax - .03f, .3f, .47f, ax + .03f, .74f, .62f, .015f, CHROME)
        }
    }

    /** A low wooden coffee table with a shelf, a few magazines and a cup on it. */
    private fun coffeeTable() {
        scene.material = Mat.WOOD
        rb(.12f, .18f, .36f, .88f, .82f, .42f, .03f, WOOD, WOOD_D)
        for (lx in floatArrayOf(.18f, .82f)) for (ly in floatArrayOf(.24f, .76f)) rb(lx - .03f, ly - .03f, 0f, lx + .03f, ly + .03f, .36f, .01f, WOOD_D)
        rb(.16f, .22f, .1f, .84f, .78f, .13f, .01f, WOOD_L)
        scene.material = Mat.PLAIN
        bx(.22f, .28f, .42f, .5f, .62f, .43f, c(0xFFEB5757), WHITE)
        bx(.3f, .4f, .43f, .6f, .74f, .44f, c(0xFF2F80ED), WHITE)
        bx(.24f, .3f, .13f, .5f, .6f, .16f, c(0xFFF2C94C), WHITE)
        cyl(.72f, .32f, .045f, .42f, .52f, WHITE, c(0xFF5D4037), sides = 8)
    }

    private fun checkout(body: Int) {
        scene.material = Mat.PAINT
        rb(0f, .15f, 0f, 3f, .95f, .95f, .03f, body)
        bx(0f, .93f, 0f, 3f, .97f, .12f, BLACK)
        scene.material = Mat.METAL
        // the belt between its steel rails, a divider bar on it
        rb(0f, .2f, .92f, 2f, .3f, 1.03f, .015f, STEEL)
        rb(0f, .8f, .92f, 2f, .9f, 1.03f, .015f, STEEL)
        scene.material = Mat.FABRIC
        rb(.02f, .3f, .93f, 1.98f, .8f, .99f, .02f, c(0xFF2E3238))
        scene.material = Mat.METAL
        rb(1.2f, .32f, .99f, 1.25f, .78f, 1.03f, .015f, c(0xFFEB5757))
        // the bagging end and the till
        scene.material = Mat.STONE
        rb(2f, .1f, .95f, 3f, 1f, 1.0f, .025f, c(0xFF9AA3AD))
        pos(2.42f, 1.0f)
        rb(2.68f, .7f, 1.0f, 2.84f, .9f, 1.04f, .012f, BLACK)
        scene.glow = true; bx(2.72f, .84f, 1.04f, 2.8f, .88f, 1.044f, c(0xFF6FCF97)); scene.glow = false
    }

    private fun desk(office: Boolean) {
        scene.material = Mat.METAL
        rb(.05f, .15f, 0f, .12f, .85f, .72f, .015f, DARK)
        rb(1.88f, .15f, 0f, 1.95f, .85f, .72f, .015f, DARK)
        rb(.12f, .14f, .3f, 1.88f, .17f, .7f, .01f, DARK)
        scene.material = Mat.WOOD
        rb(0f, .1f, .72f, 2f, .9f, .78f, .02f, WOOD_L)
        // a drawer pedestal under the left end
        rb(.16f, .2f, 0f, .6f, .82f, .62f, .02f, c(0xFFB0B7BF))
        for (i in 0 until 3) bx(.3f, .82f, .12f + i * .18f, .46f, .835f, .14f + i * .18f, CHROME)
        // the monitor (facing the worker at +y), the keyboard, a mug and papers
        scene.material = Mat.METAL
        rb(.88f, .22f, .78f, 1.12f, .4f, .795f, .01f, DARK)
        bx(.98f, .28f, .795f, 1.02f, .31f, .92f, DARK)
        rb(.65f, .26f, .9f, 1.35f, .31f, 1.24f, .012f, BLACK)
        scene.glow = true; panel(.68f, .312f, .93f, 1.32f, .312f, 1.21f, if (office) SCREEN else c(0xFF4F7FA6)); scene.glow = false
        if (office) {
            rb(.72f, .52f, .78f, 1.22f, .66f, .795f, .006f, c(0xFF2A2D31))
            rb(1.3f, .55f, .78f, 1.36f, .63f, .795f, .01f, c(0xFF2A2D31))
            scene.material = Mat.PLAIN
            ln = 0; pt(.035f, .78f, WHITE); pt(.04f, .88f, WHITE); pt(.033f, .88f, c(0xFF5D3A1A)); turned(1.6f, .4f, 10)
            bx(1.5f, .55f, .78f, 1.78f, .82f, .785f, WHITE)
        }
    }

    // ------------------------------------------------------------------ gym

    private fun boxingBag(bag: Int) {
        scene.material = Mat.METAL
        cyl(.5f, .5f, .012f, 1.62f, 2.6f, DARK, sides = 5)
        ln = 0; pt(.0f, 1.6f, DARK); pt(.05f, 1.66f, DARK); pt(0f, 1.7f, DARK); turned(.5f, .5f, 8)
        scene.material = Mat.FABRIC
        val band = scene.mix(bag, 0xFF000000.toInt(), .45f)
        ln = 0; pt(.12f, .32f, bag); pt(.18f, .38f, bag); pt(.2f, .5f, band); pt(.2f, .56f, bag); pt(.2f, 1.36f, band); pt(.2f, 1.42f, bag); pt(.18f, 1.54f, bag); pt(.12f, 1.6f, bag)
        turned(.5f, .5f, 12)
    }

    private fun weightRack() {
        scene.material = Mat.METAL
        for (lx in floatArrayOf(.04f, 1.88f)) rb(lx, .2f, 0f, lx + .08f, .8f, .82f, .015f, DARK)
        rb(.04f, .22f, .32f, 1.96f, .5f, .36f, .01f, c(0xFF333333))
        rb(.04f, .5f, .62f, 1.96f, .78f, .66f, .01f, c(0xFF333333))
        // hex dumbbells lying front to back, heavier toward the right, bright light ones on top
        val tops = intArrayOf(c(0xFFEB5757), c(0xFF2F80ED), c(0xFFF2C94C), c(0xFF27AE60), c(0xFFEB5757), c(0xFF2F80ED))
        for (i in 0 until 6) {
            val x = .2f + i * .3f
            val r = .05f + i * .008f
            rb(x - r, .24f, .36f, x + r, .3f, .36f + 2 * r, .015f, BLACK)
            rb(x - r, .42f, .36f, x + r, .48f, .36f + 2 * r, .015f, BLACK)
            bx(x - .015f, .3f, .36f + r - .015f, x + .015f, .42f, .36f + r + .015f, CHROME)
            val s = .04f
            rb(x - s, .52f, .66f, x + s, .57f, .66f + 2 * s, .012f, tops[i])
            rb(x - s, .71f, .66f, x + s, .76f, .66f + 2 * s, .012f, tops[i])
            bx(x - .012f, .57f, .66f + s - .012f, x + .012f, .71f, .66f + s + .012f, CHROME)
        }
    }

    private fun hairChair() {
        // the station: a mirror in a frame on a cabinet with a shelf of bottles
        scene.material = Mat.PAINT
        rb(.1f, 0f, 0f, .9f, .12f, 1.95f, .03f, c(0xFFD5DBE2))
        rb(.12f, .06f, .78f, .88f, .3f, .84f, .02f, WHITE)
        scene.material = Mat.GLASS
        panel(.17f, .121f, .95f, .83f, .121f, 1.85f, c(0xFF9FC6E6))
        scene.material = Mat.PLAIN
        val bottles = intArrayOf(c(0xFFEB5757), c(0xFF6FCF97), c(0xFF2F80ED))
        for (i in 0 until 3) { ln = 0; pt(.025f, .84f, bottles[i]); pt(.025f, .96f, bottles[i]); pt(.01f, 1.0f, DARK); turned(.3f + i * .12f, .2f, 8) }
        // the chair: a hydraulic base, a seat and back in black, chrome arms and a foot rest
        scene.material = Mat.METAL
        ln = 0; pt(.26f, 0f, CHROME); pt(.24f, .03f, CHROME); pt(.07f, .06f, CHROME); pt(.06f, .38f, CHROME); turned(.5f, 1.2f, 12)
        rb(.36f, .72f, .18f, .64f, .82f, .22f, .015f, CHROME)
        bx(.47f, .82f, .2f, .53f, 1.0f, .44f, CHROME)
        scene.material = Mat.FABRIC
        rb(.27f, .95f, .4f, .73f, 1.45f, .56f, .06f, BLACK)
        rb(.29f, 1.38f, .5f, .71f, 1.52f, 1.12f, .07f, BLACK)
        rb(.4f, 1.42f, 1.14f, .6f, 1.5f, 1.26f, .04f, BLACK)
        scene.material = Mat.METAL
        rb(.22f, 1.0f, .62f, .28f, 1.42f, .68f, .02f, CHROME)
        rb(.72f, 1.0f, .62f, .78f, 1.42f, .68f, .02f, CHROME)
    }

    private fun speaker(lo: Float, hi: Float, height: Float, size: Int) {
        scene.material = Mat.WOOD
        if (size > 1) rb(lo - .02f, lo - .02f, 0f, hi + .02f, hi + .02f, .05f, .015f, BLACK)
        val z0 = if (size > 1) .05f else 0f
        rb(lo, lo, z0, hi, hi, height, .035f, DARK)
        scene.material = Mat.PLAIN
        val front = hi + .002f
        val cx = (lo + hi) / 2
        val woofers = when (size) { 1 -> floatArrayOf(.3f); 2 -> floatArrayOf(.45f); else -> floatArrayOf(.45f, .9f) }
        val wr = when (size) { 1 -> .13f; 2 -> .18f; else -> .24f }
        for (wz in woofers) {
            uprightDisc(cx, front, wz, wr, c(0xFF555555), 14)
            uprightDisc(cx, front + .002f, wz, wr * .78f, c(0xFF2A2D31), 14)
            uprightDisc(cx, front + .004f, wz, wr * .26f, c(0xFF777777), 10)
        }
        val tz = height - (if (size == 1) .15f else .22f)
        uprightDisc(cx, front, tz, .05f + size * .008f, c(0xFF555555), 10)
        uprightDisc(cx, front + .002f, tz, .025f + size * .004f, c(0xFFB0B7BF), 8)
        scene.glow = true
        uprightDisc(hi - .06f, front + .004f, z0 + .06f, .01f, c(0xFF56CCF2), 6)
        scene.glow = false
    }
}
