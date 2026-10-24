package com.hustlecity.game.render

import com.hustlecity.game.data.c
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Low-poly vehicles built from extruded side profiles: sedans with a hood and trunk, pickups with a bed, vans,
 * sports cars, taxis with a roof sign, and the city's own rides: Manila's jeepneys and tricycles, boxy kei cars and
 * city buses. Wheels, lights, bumpers and a contact shadow up close; plain boxes far away.
 */
class CarPainter(private val scene: Scene3D) {
    private val proj get() = scene.proj

    private val wx = FloatArray(16); private val wy = FloatArray(16); private val wz = FloatArray(16)
    private val pf = FloatArray(12); private val pzz = FloatArray(12)
    private val wheelF = FloatArray(8); private val wheelZ = FloatArray(8)

    // frame of the car being drawn
    private var ox = 0f; private var oy = 0f; private var fx = 0f; private var fy = 0f; private var rx = 0f; private var ry = 0f

    /** Traffic keeps left: bus doors and tricycle sidecars go on the left. */
    var leftHand = false

    companion object {
        private val HEAD_GLOW = c(0xFFFFEBA8)
        val GLASS = c(0xFF26313D)
        val TIRE = c(0xFF1E2023)
        val RIM = c(0xFFB9C0C7)
        val BUMPER = c(0xFF2E3136)
        val TAXI = c(0xFFF2C230)
        val HEAD = c(0xFFFFF4D6)
        val TAIL = c(0xFFC0392B)
        val CHROME = c(0xFFD3D8DD)
        /** Open window holes and doorways: the dim inside of a jeepney or a sidecar. */
        val INSIDE = c(0xFF2B2622)
        val BUS_ROOF = c(0xFFE4E7EA)
        /** Lit bus windows at night and the amber destination display. */
        val LIT_WINDOW = c(0xFFFFE3A0)
        val LED = c(0xFFFFD34D)
        private val SKIN = intArrayOf(c(0xFFC68B59), c(0xFF8D5524), c(0xFFE0AC69))
        /** Each car model's body (length, width, height in meters, without its mirrors or a taxi's sign): scaled to the car's. */
        internal val MODEL_SIZE = mapOf(
            "sedan" to floatArrayOf(4.5f, 1.84f, 1.45f), "hatchback" to floatArrayOf(4.1f, 1.8f, 1.46f), "taxi" to floatArrayOf(4.5f, 1.84f, 1.45f),
            "pickup" to floatArrayOf(5.2f, 1.84f, 1.8f), "van" to floatArrayOf(5.8f, 2.1f, 2.5f), "sports" to floatArrayOf(4.5f, 1.94f, 1.2f),
            "suv" to floatArrayOf(4.8f, 2.0f, 1.85f), "box_truck" to floatArrayOf(7.2f, 2.4f, 3.3f),
        )
        private val SHIRTS = intArrayOf(c(0xFFEB5757), c(0xFF2F80ED), c(0xFFF2F2F2), c(0xFF27AE60), c(0xFF333333), c(0xFFF2C94C))
    }

    /**
     * Draws a vehicle. [style] 0 sedan, 1 pickup, 2 van, 3 sports, 4 jeepney, 5 tricycle, 6 kei car, 7 city bus,
     * 8 motorbike, 9 SUV, 10 box truck, 11 modern jeepney, 12 moped.
     * [near] adds wheels, lights and trim. [braking] brightens the tail lights; [taxi] adds the roof sign. [accent]
     * is a livery's second color (stripes, a two-tone roof); 0 lets the painter pick one.
     */
    fun draw(x: Float, y: Float, heading: Float, len: Float, wid: Float, hgt: Float, color: Int, style: Int,
             near: Boolean, taxi: Boolean = false, braking: Boolean = false, accent: Int = 0, pitch: Float = 0f, roll: Float = 0f) {
        val night = scene.daylight < .5f
        // headlights go on after dark, and in rain or fog
        val lights = night || scene.rain > .3f
        frame(x, y, heading)
        if (!near) {
            farCar(len, wid, hgt, color, style, accent)
            if (lights) headGlow(x, y, heading, len)
            return
        }
        tiltF = pitch; tiltL = roll
        val hl = len / 2; val hw = wid / 2
        // contact shadow
        quadFlat(-hl - .15f, -hw - .1f, hl + .15f, hw + .1f, .03f, scene.withAlpha(0xFF000000.toInt(), .28f))
        val body = color
        scene.material = Mat.PAINT
        val camF = (scene.camLX - x) * fx + (scene.camLY - y) * fy
        val camL = (scene.camLX - x) * rx + (scene.camLY - y) * ry
        val nearSide = if (camL >= 0f) 1 else -1
        // the smallest trim (ornaments, mirrors, lettering) only close to the camera
        val fine = proj.camZOf(x, y, 1f) < 42f
        if (model(x, y, len, wid, hgt, body, style, taxi, lights, braking, pitch, roll)) {
            tiltF = 0f; tiltL = 0f
            scene.material = Mat.PLAIN
            if (lights) headGlow(x, y, heading, len)
            if (braking) brakeGlow(x, y, hl)
            return
        }
        when (style) {
            4 -> jeepney(hl, hw, hgt, body, accent, night, braking, camF, nearSide, fine)
            5 -> tricycle(hl, hw, hgt, body, accent, night, braking, camF, nearSide, fine)
            6 -> kei(hl, hw, hgt, body, accent, night, braking, camF, nearSide, fine)
            7 -> bus(hl, hw, hgt, body, accent, night, braking, camF, nearSide, fine)
            8 -> { bike(0f, hl, body, if (accent != 0) accent else CHROME, night, braking, camF, nearSide, fine); shift(0f) }
            10 -> boxTruck(hl, hw, len, hgt, body, accent, night, braking, camF, nearSide)
            11 -> modernJeep(hl, hw, hgt, body, accent, night, braking, camF, nearSide, fine)
            12 -> moped(hl, body, accent, night, braking, camF, nearSide, fine)
            else -> classic(hl, hw, len, wid, hgt, body, style, taxi, night, braking, camF, nearSide)
        }
        tiltF = 0f; tiltL = 0f
        scene.material = Mat.PLAIN
        if (lights) headGlow(x, y, heading, len)
        if (braking) brakeGlow(x, y, hl)
    }

    /** Brake lights wash the road behind red, fading out softly. */
    private fun brakeGlow(x: Float, y: Float, hl: Float) {
        val bx = x - fx * (hl + .7f); val by = y - fy * (hl + .7f)
        scene.glow = true
        scene.glowOval(bx, by, .04f, 1.5f, 1.15f, fx, fy, c(0xFFFF3B30), .15f)
        scene.glow = false
    }

    /**
     * In HD, the everyday cars (sedan, pickup, van, sports car, SUV, box truck, and the taxis) are models
     * (tools/models/cars.py) painted in the car's color, leaning on their springs, their lights lit at night and the tail
     * lights when braking. False when this one isn't (another style, the classic renderer, no model files).
     */
    private fun model(x: Float, y: Float, len: Float, wid: Float, hgt: Float, color: Int, style: Int, taxi: Boolean, lights: Boolean,
                      braking: Boolean, pitch: Float, roll: Float): Boolean {
        if (scene.gpu == null || !scene.smooth) return false
        val id = when (style) {
            0 -> if (taxi) "taxi" else if (len < 4.4f) "hatchback" else "sedan"
            1 -> "pickup"; 2 -> "van"; 3 -> "sports"; 9 -> "suv"; 10 -> "box_truck"
            else -> return false
        }
        val m = Models.car(id) ?: return false
        val size = MODEL_SIZE.getValue(id)
        val lenses = when {
            lights -> com.hustlecity.game.render.gpu.GpuFrame.LENSES_LIT
            braking -> com.hustlecity.game.render.gpu.GpuFrame.LENSES_BRAKING
            else -> com.hustlecity.game.render.gpu.GpuFrame.LENSES_OFF
        }
        // the model's y points left; the lean's roll is per meter to the right
        return scene.meshAt(m, x, y, 0f, fx, fy, -rx, -ry, len / size[0], wid / size[1], hgt / size[2],
            paint = color, tiltF = pitch, tiltL = roll, lenses = lenses)
    }

    private fun frame(x: Float, y: Float, heading: Float) {
        ox = x; oy = y
        fx = sin(heading); fy = -cos(heading)
        rx = -fy; ry = fx
    }

    /** Sedan, pickup, van and sports car. */
    private fun classic(hl: Float, hw: Float, len: Float, wid: Float, hgt: Float, body: Int, style: Int, taxi: Boolean, night: Boolean,
                        braking: Boolean, camF: Float, nearSide: Int) {
        // far wheels and the end facing away first, so the body covers them
        wheels(len, wid, style, -nearSide)
        if (camF > 0f) rearParts(hl, hw, hgt, style, night, braking, body) else frontParts(hl, hw, hgt, night)
        when (style) {
            1 -> { // pickup: cab up front, open bed behind
                P(0, -hl, .32f); P(1, hl, .32f); P(2, hl, hgt * .5f); P(3, hl - .25f, hgt * .56f); P(4, -hl, hgt * .56f)
                prism(5, -hw, hw, body, body)
                val cab0 = hl - len * .62f; val cab1 = hl - len * .22f
                if (camF > 0f) bed(hl, hw, hgt, cab0, body)
                P(0, cab0, hgt * .56f); P(1, cab1, hgt * .56f); P(2, cab1 - .45f, hgt); P(3, cab0 + .1f, hgt)
                prism(4, -hw * .92f, hw * .92f, GLASS, body, roofIdx = 2)
                if (camF <= 0f) bed(hl, hw, hgt, cab0, body)
            }
            2 -> { // van: tall box with a raked windscreen
                P(0, -hl, .32f); P(1, hl, .32f); P(2, hl, hgt * .5f); P(3, hl - .75f, hgt); P(4, -hl, hgt)
                prism(5, -hw, hw, body, body, glassIdx = 2)
                // side windows up front and a stripe
                sideRect(hl - 1.9f, hl - .85f, hgt * .58f, hgt * .9f, hw, GLASS)
                sideRect(-hl + .2f, hl - 2.1f, hgt * .45f, hgt * .5f, hw, scene.mix(body, 0xFF000000.toInt(), .25f))
            }
            3 -> { // sports car: long hood, low cabin
                P(0, -hl, .25f); P(1, hl, .25f); P(2, hl, hgt * .42f); P(3, hl - .5f, hgt * .55f); P(4, -hl + .1f, hgt * .58f); P(5, -hl, hgt * .5f)
                prism(6, -hw, hw, body, body)
                val c0 = -hl + len * .2f; val c1 = hl - len * .38f
                P(0, c0, hgt * .56f); P(1, c1, hgt * .56f); P(2, c1 - .9f, hgt); P(3, c0 + .7f, hgt)
                prism(4, -hw * .86f, hw * .86f, GLASS, body, roofIdx = 2)
            }
            9 -> { // SUV: high, square body, a tall cabin and roof rails
                P(0, -hl, .42f); P(1, hl, .42f); P(2, hl, hgt * .52f); P(3, hl - .55f, hgt * .6f); P(4, -hl, hgt * .62f)
                prism(5, -hw, hw, body, body)
                val c0 = -hl + .2f; val c1 = hl - len * .3f
                P(0, c0, hgt * .6f); P(1, c1, hgt * .6f); P(2, c1 - .5f, hgt); P(3, c0, hgt)
                prism(4, -hw * .92f, hw * .92f, GLASS, body, roofIdx = 2)
                sideRect(-hl + .1f, hl - .1f, .42f, .58f, hw, scene.mix(body, 0xFF000000.toInt(), .45f))
                slab(c0 + .2f, c1 - .7f, -hw * .82f, -hw * .74f, hgt, hgt + .08f, CHROME)
                slab(c0 + .2f, c1 - .7f, hw * .74f, hw * .82f, hgt, hgt + .08f, CHROME)
            }
            else -> { // sedan
                P(0, -hl, .3f); P(1, hl, .3f); P(2, hl, hgt * .45f); P(3, hl - .35f, hgt * .55f); P(4, -hl + .25f, hgt * .58f); P(5, -hl, hgt * .5f)
                prism(6, -hw, hw, body, body)
                val c0 = -hl + len * .24f; val c1 = hl - len * .3f
                P(0, c0, hgt * .56f); P(1, c1, hgt * .56f); P(2, c1 - .7f, hgt); P(3, c0 + .45f, hgt)
                prism(4, -hw * .88f, hw * .88f, GLASS, body, roofIdx = 2)
                if (taxi) {
                    // roof sign and a checker band, light on dark cabs
                    val mid = (c0 + c1) / 2 - .1f
                    slab(mid - .35f, mid + .35f, -.28f, .28f, hgt, hgt + .28f, if (night) c(0xFFFFF2B0) else c(0xFFFAFAFA))
                    sideRect(-hl + .4f, hl - .4f, hgt * .4f, hgt * .46f, hw, if (luma(body) < 70) c(0xFFD9C27A) else c(0xFF222222))
                }
            }
        }
        if (camF > 0f) frontParts(hl, hw, hgt, night) else rearParts(hl, hw, hgt, style, night, braking, body)
        wheels(len, wid, style, nearSide)
    }

    // ------------------------------------------------------------------ jeepney

    /**
     * The jeepney: a long chromed body on a jeep's front end. Separate fenders flank a narrow hood with a chrome grille
     * and horse ornaments; the passenger cab has open, glassless windows, painted stripes, a roof rack and a route
     * signboard on top, and an open doorway with a step at the back.
     */
    private fun jeepney(hl: Float, hw: Float, hgt: Float, body: Int, accent: Int, night: Boolean, braking: Boolean, camF: Float, nearSide: Int, fine: Boolean) {
        val stripe = if (accent != 0) accent else scene.mix(body, 0xFFFFFFFF.toInt(), .65f)
        val rz = hgt * .9f
        val c1 = hl - 1.6f // front of the passenger cab
        val hood = hw * .6f
        val bottom = .48f
        val front = hl - .85f; val rear = -hl + 1.55f
        wheelPair(front, rear, .4f, hw - .3f, hw + .02f, -nearSide)
        // whatever faces away goes first: the back, or the front end with its hood
        if (camF > 0f) jeepRear(hl, hw, rz, bottom, night, braking)
        else { jeepFront(hl, hw, hood, bottom, night, fine); jeepHood(hl, hood, hw, bottom, body, front, nearSide, fine) }
        // the passenger cab, its skirt hanging over the near rear wheel
        wheelAt(rear, .4f, nearSide * (hw - .3f), nearSide * (hw + .02f), nearSide)
        P(0, -hl, bottom); P(1, c1, bottom); P(2, c1, 1.32f); P(3, c1 - .22f, rz); P(4, -hl, rz)
        prism(5, -hw, hw, body, CHROME, glassIdx = 2)
        // stripes, chrome skirt and the open windows along both sides
        sideRect(-hl, c1, bottom, bottom + .1f, hw, CHROME)
        sideRect(-hl, c1, .78f, .86f, hw, scene.mix(stripe, 0xFF000000.toInt(), .2f))
        sideRect(-hl, c1, 1.04f, 1.2f, hw, stripe)
        sideRect(-hl, c1 - .2f, rz - .2f, rz - .03f, hw, stripe)
        val w0 = -hl + .3f; val w1 = c1 - .28f
        val n = if (fine) 6 else 4
        val step = (w1 - w0) / n
        for (k in 0 until n) sideRect(w0 + k * step + .07f, w0 + (k + 1) * step - .07f, 1.3f, rz - .26f, hw, INSIDE)
        if (fine) {
            // the route painted on the roof band, and side mirrors on stalks
            sideRect(-hl + .9f, -hl + 2.6f, rz - .16f, rz - .07f, hw + .01f, c(0xFF1F1F1F))
            slab(c1 + .05f, c1 + .14f, -hw - .22f, -hw - .02f, 1.45f, 1.7f, BUMPER)
            slab(c1 + .05f, c1 + .14f, hw + .02f, hw + .22f, 1.45f, 1.7f, BUMPER)
        }
        if (camF > 0f) { jeepHood(hl, hood, hw, bottom, body, front, nearSide, fine); jeepFront(hl, hw, hood, bottom, night, fine) }
        // roof: a visor over the windscreen, rails round the rack and the route signboard up front
        slab(c1 - .3f, c1 + .28f, -hw, hw, rz - .07f, rz, CHROME)
        slab(-hl + .15f, c1 - .35f, -hw + .02f, -hw + .1f, rz, rz + .12f, CHROME)
        slab(-hl + .15f, c1 - .35f, hw - .1f, hw - .02f, rz, rz + .12f, CHROME)
        if (fine) for (k in 0..2) { val f = -hl + .3f + k * (c1 + hl - .8f) / 2f; slab(f, f + .07f, -hw + .1f, hw - .1f, rz + .06f, rz + .1f, CHROME) }
        val sb = c1 - .12f
        slab(sb - .08f, sb, -hw * .62f, hw * .62f, rz, rz + .34f, c(0xFFF7F3E6))
        if (fine) {
            frontRect(sb + .005f, -hw * .5f, hw * .1f, rz + .2f, rz + .27f, c(0xFFC0392B))
            frontRect(sb + .005f, -hw * .5f, hw * .4f, rz + .08f, rz + .15f, c(0xFF1F3A93))
        }
        if (camF <= 0f) jeepRear(hl, hw, rz, bottom, night, braking)
    }

    /**
     * The jeep front end: a fender either side of a narrow hood, horses standing on it. The near front wheel at
     * [axle] goes in just before its fender, which arches over it.
     */
    private fun jeepHood(hl: Float, hood: Float, hw: Float, bottom: Float, body: Int, axle: Float, nearSide: Int, fine: Boolean) {
        val h0 = hl - 1.65f
        for (pass in 0..2) {
            if (pass == 1) {
                P(0, h0, bottom); P(1, hl, bottom); P(2, hl, 1.1f); P(3, hl - .14f, 1.24f); P(4, h0, 1.3f)
                prism(5, -hood, hood, body, body)
                // a chrome strip down the middle of the hood
                slab(h0 + .1f, hl - .2f, -.07f, .07f, 1.25f, 1.29f, CHROME)
                if (fine) for (s in -1..1 step 2) horse(hl - .5f, s * hood * .6f, 1.27f, nearSide)
                continue
            }
            // the fender on the far side first, the near one last, flaring out over the wheel
            val side = if (pass == 0) -nearSide else nearSide
            if (pass == 2) wheelAt(axle, .4f, side * (hw - .3f), side * (hw + .02f), side)
            P(0, h0 + .05f, .58f); P(1, hl - .04f, .58f); P(2, hl - .04f, .82f); P(3, hl - .36f, 1.02f); P(4, h0 + .35f, 1.02f); P(5, h0 + .05f, .88f)
            if (side > 0) prism(6, hood, hw + .05f, body, body) else prism(6, -hw - .05f, -hood, body, body)
        }
    }

    private fun jeepFront(hl: Float, hw: Float, hood: Float, bottom: Float, night: Boolean, fine: Boolean) {
        // a wide chrome bumper, the grille and round lamps either side of it
        slab(hl - .02f, hl + .16f, -hw + .02f, hw - .02f, bottom, bottom + .16f, CHROME)
        frontRect(hl + .01f, -hood + .08f, hood - .08f, .66f, 1.06f, CHROME)
        if (fine) for (k in -2..2) frontRect(hl + .015f, k * .16f - .04f, k * .16f + .04f, .7f, 1.0f, c(0xFF5B6168))
        val head = if (night) HEAD else c(0xFFE8ECEF)
        frontRect(hl + .02f, -hood + .1f, -hood + .32f, .84f, 1.0f, head)
        frontRect(hl + .02f, hood - .32f, hood - .1f, .84f, 1.0f, head)
        // amber signal lamps on the fenders
        frontRect(hl - .03f, -hw - .02f, -hw + .2f, .66f, .76f, c(0xFFF39C12))
        frontRect(hl - .03f, hw - .2f, hw + .02f, .66f, .76f, c(0xFFF39C12))
    }

    private fun jeepRear(hl: Float, hw: Float, rz: Float, bottom: Float, night: Boolean, braking: Boolean) {
        // the open doorway with grab bars and a step
        frontRect(-hl - .01f, -.42f, .42f, bottom + .12f, rz - .14f, INSIDE, rear = true)
        slab(-hl - .26f, -hl, -.55f, .55f, bottom - .06f, bottom + .04f, CHROME)
        slab(-hl - .06f, -hl - .01f, -.5f, -.45f, bottom + .2f, rz - .2f, CHROME)
        slab(-hl - .06f, -hl - .01f, .45f, .5f, bottom + .2f, rz - .2f, CHROME)
        val tail = if (braking) c(0xFFFF2E2E) else if (night) c(0xFFE53935) else TAIL
        frontRect(-hl - .01f, -hw + .08f, -hw + .3f, .9f, 1.25f, tail, rear = true)
        frontRect(-hl - .01f, hw - .3f, hw - .08f, .9f, 1.25f, tail, rear = true)
    }

    /** A rearing chrome horse standing on the hood, side on. */
    private fun horse(f: Float, l: Float, z: Float, nearSide: Int) {
        val col = scene.lit(CHROME, max(.85f, scene.sideShade(rx * nearSide, ry * nearSide)))
        fz4(l, f - .17f, z, f - .1f, z, f - .04f, z + .2f, f - .15f, z + .2f, col)                // hind legs
        fz4(l, f - .2f, z + .17f, f + .1f, z + .2f, f + .1f, z + .31f, f - .19f, z + .29f, col)   // body
        fz4(l, f + .03f, z + .26f, f + .12f, z + .24f, f + .24f, z + .42f, f + .15f, z + .46f, col) // neck and head
        fz4(l, f + .06f, z + .21f, f + .22f, z + .27f, f + .2f, z + .32f, f + .05f, z + .28f, col)  // raised forelegs
    }

    // ------------------------------------------------------------------ tricycle

    /**
     * The Philippine tricycle: a small motorbike with a covered sidecar bolted on its curb side. The sidecar's roof
     * reaches over the rider, leaving the bike's nose and tail in the open, with the drivers' association signboard
     * on top.
     */
    private fun tricycle(hl: Float, hw: Float, hgt: Float, body: Int, accent: Int, night: Boolean, braking: Boolean, camF: Float, nearSide: Int, fine: Boolean) {
        val sd = if (leftHand) -1 else 1
        val trim = if (accent != 0) accent else scene.mix(body, 0xFFFFFFFF.toInt(), .7f)
        val rz = hgt * .9f
        val carL = sd * (hw - .46f)  // sidecar centre
        val bikeL = -sd * (hw - .27f)
        // the part further from the camera goes first
        val bikeFirst = nearSide == sd
        for (pass in 0..1) {
            if ((pass == 0) == bikeFirst) bike(bikeL, hl, body, trim, night, braking, camF, nearSide, fine)
            else sidecar(carL, hl, rz, body, trim, night, nearSide, sd, fine)
        }
        // the roof over the sidecar, reaching across over the rider's head
        val c0 = carL - .46f; val c1 = carL + .46f
        slab(-hl + .1f, .5f, c0, c1, rz, rz + .08f, body)
        sideRectAt(-hl + .1f, .5f, rz + .005f, rz + .075f, c0, c1, trim)
        val b0 = if (sd > 0) bikeL - .22f else c1 - .02f
        val b1 = if (sd > 0) c0 + .02f else bikeL + .22f
        slab(-.72f, .2f, b0, b1, rz - .01f, rz + .07f, body)
        sideRectAt(-.72f, .2f, rz - .005f, rz + .065f, b0, b1, trim, only = -sd)
        if (fine) {
            // the association's signboard
            val l0 = carL - .34f; val l1 = carL + .34f
            slab(.4f, .47f, l0, l1, rz + .08f, rz + .28f, c(0xFFF7F3E6))
            frontRect(.475f, l0 + .06f, l1 - .06f, rz + .14f, rz + .21f, trim)
        }
    }

    private fun sidecar(l: Float, hl: Float, rz: Float, body: Int, trim: Int, night: Boolean, nearSide: Int, sd: Int, fine: Boolean) {
        shift(l)
        val hw = .44f
        // its wheel on the outer side
        if (nearSide != sd) wheelAt(-.35f, .27f, sd * hw - .04f, sd * hw + .1f, sd)
        // the tub with a rounded nose, then the cab with a windscreen
        P(0, -hl + .1f, .24f); P(1, .52f, .24f); P(2, .76f, .46f); P(3, .7f, .8f); P(4, -hl + .1f, .82f)
        prism(5, -hw, hw, body, body)
        P(0, -hl + .1f, .8f); P(1, .6f, .8f); P(2, .38f, rz); P(3, -hl + .1f, rz)
        prism(4, -hw, hw, body, body, glassIdx = 1)
        // chrome and a painted band, a doorway on the curb side and a small window toward the bike
        sideRect(-hl + .1f, .55f, .3f, .38f, hw, CHROME)
        sideRect(-hl + .1f, .58f, .66f, .76f, hw, trim)
        sideRectAt(-.75f, .1f, .52f, rz - .1f, -hw - .01f, hw + .01f, INSIDE, only = sd)
        sideRectAt(-.85f, .15f, .95f, rz - .12f, -hw, hw, INSIDE, only = -sd)
        val tail = if (night) c(0xFFE53935) else TAIL
        frontRect(-hl + .09f, -hw + .06f, -hw + .2f, .5f, .68f, tail, rear = true)
        frontRect(-hl + .09f, hw - .2f, hw - .06f, .5f, .68f, tail, rear = true)
        if (nearSide == sd) wheelAt(-.35f, .27f, sd * hw - .04f, sd * hw + .1f, sd)
        shift(-l)
    }

    private fun bike(l: Float, hl: Float, body: Int, trim: Int, night: Boolean, braking: Boolean, camF: Float, nearSide: Int, fine: Boolean) {
        shift(l)
        val frame = c(0xFF2B2E33)
        val wf = hl - .42f; val wr = -hl + .6f
        // wheels and the engine low down, then the rider on the seat
        if (camF > 0f) wheelAt(wr, .29f, -.05f, .05f, nearSide) else wheelAt(wf, .29f, -.05f, .05f, nearSide)
        slab(wr - .1f, wf - .35f, -.12f, .12f, .28f, .55f, frame)
        // rear fender, fuel tank and seat
        slab(wr - .16f, wr + .22f, -.13f, .13f, .58f, .64f, body)
        slab(-.02f, .38f, -.15f, .15f, .58f, .82f, body)
        slab(-.6f, .0f, -.14f, .14f, .64f, .74f, c(0xFF1E1E1E))
        if (fine) slab(-.62f, -.15f, .13f, .21f, .3f, .4f, CHROME) // exhaust
        rider(body xor trim, fine)
        // the raked fork, handlebars and headlamp
        P(0, wf - .04f, .29f); P(1, wf + .04f, .29f); P(2, wf - .2f, 1.02f); P(3, wf - .28f, 1.02f)
        prism(4, -.035f, .035f, frame, frame)
        slab(wf - .32f, wf - .26f, -.36f, .36f, .98f, 1.03f, frame)
        slab(wf - .22f, wf - .1f, -.09f, .09f, .78f, .94f, trim)
        frontRect(wf - .095f, -.07f, .07f, .81f, .92f, if (night) HEAD else c(0xFFE8ECEF))
        val tail = if (braking) c(0xFFFF2E2E) else if (night) c(0xFFE53935) else TAIL
        frontRect(wr - .17f, -.07f, .07f, .58f, .64f, tail, rear = true)
        if (camF > 0f) wheelAt(wf, .29f, -.05f, .05f, nearSide) else wheelAt(wr, .29f, -.05f, .05f, nearSide)
        shift(-l)
    }

    /**
     * A step-through moped: a leg shield and floorboard between small wheels, the seat over the engine. One in four
     * carries a passenger riding pillion; with a livery accent of its own it's a delivery rider with a box on the rack.
     */
    private fun moped(hl: Float, body: Int, accent: Int, night: Boolean, braking: Boolean, camF: Float, nearSide: Int, fine: Boolean) {
        val frame = c(0xFF2B2E33)
        val wf = hl - .3f; val wr = -hl + .32f
        val pillion = ((body ushr 5) xor (body ushr 13)) and 3 == 0
        val box = accent != 0 && accent != body
        if (camF > 0f) wheelAt(wr, .23f, -.05f, .05f, nearSide) else wheelAt(wf, .23f, -.05f, .05f, nearSide)
        // the floorboard, the body over the engine and the seat on it
        slab(-.3f, .3f, -.16f, .16f, .26f, .34f, frame)
        P(0, wr - .14f, .3f); P(1, -.26f, .3f); P(2, -.2f, .64f); P(3, wr - .02f, .7f); P(4, wr - .2f, .54f)
        prism(5, -.16f, .16f, body, body)
        slab(-.74f, -.18f, -.15f, .15f, .7f, .78f, c(0xFF1E1E1E))
        // the leg shield sweeping up from the floor to the handlebars
        P(0, .24f, .28f); P(1, .46f, .3f); P(2, wf - .1f, .98f); P(3, wf - .26f, 1.0f)
        prism(4, -.2f, .2f, body, body)
        if (box) slab(-1.02f, -.54f, -.24f, .24f, .8f, 1.28f, accent, scene.mix(accent, 0xFFFFFFFF.toInt(), .2f))
        if (pillion) { ahead(-.5f); rider(body * 31 + 7, fine); ahead(.5f) }
        ahead(-.1f); rider(body xor accent, fine); ahead(.1f)
        // handlebars with the headlamp in their cowl, the tail lamp under the seat
        slab(wf - .3f, wf - .22f, -.34f, .34f, .98f, 1.03f, frame)
        slab(wf - .28f, wf - .14f, -.11f, .11f, .9f, 1.05f, body)
        frontRect(wf - .135f, -.07f, .07f, .93f, 1.02f, if (night) HEAD else c(0xFFE8ECEF))
        val tail = if (braking) c(0xFFFF2E2E) else if (night) c(0xFFE53935) else TAIL
        frontRect(wr - .15f, -.06f, .06f, .6f, .67f, tail, rear = true)
        if (camF > 0f) wheelAt(wf, .23f, -.05f, .05f, nearSide) else wheelAt(wr, .23f, -.05f, .05f, nearSide)
    }

    /** Moves the frame [f] meters forward, so a rider can sit further back. */
    private fun ahead(f: Float) { ox += fx * f; oy += fy * f }

    /** The driver astride the bike: knees forward, hands on the bars, a helmet up close. */
    private fun rider(seed: Int, fine: Boolean) {
        val h = (seed ushr 3) and 0xffff
        val shirt = SHIRTS[h % SHIRTS.size]
        val skin = SKIN[h % SKIN.size]
        val pants = c(0xFF34383F)
        slab(-.3f, .2f, -.21f, -.11f, .64f, .78f, pants)
        slab(-.3f, .2f, .11f, .21f, .64f, .78f, pants)
        slab(.12f, .22f, -.21f, -.11f, .32f, .7f, pants)
        slab(.12f, .22f, .11f, .21f, .32f, .7f, pants)
        slab(-.34f, -.08f, -.17f, .17f, .74f, 1.2f, shirt)
        // arms reaching for the handlebars
        P(0, -.16f, 1.08f); P(1, -.1f, 1.15f); P(2, .54f, 1.04f); P(3, .5f, .98f)
        prism(4, -.24f, -.17f, shirt, shirt)
        prism(4, .17f, .24f, shirt, shirt)
        scene.blob(ox - fx * .2f, oy - fy * .2f, 1.32f, .12f, scene.lit(skin, 1f), if (fine) 10 else 7)
        if (fine) scene.blob(ox - fx * .22f, oy - fy * .22f, 1.37f, .12f, scene.lit(c(0xFF2A2A2A), 1f), 8, 0f, -.3f, .7f)
    }

    // ------------------------------------------------------------------ kei car

    /**
     * Kei car or boxy minivan: a stubby hood, a tall glasshouse with near-vertical sides and a flat roof (two-tone with
     * an [accent]), tall tail lamps up the rear corners and small wheels pushed to the ends.
     */
    private fun kei(hl: Float, hw: Float, hgt: Float, body: Int, accent: Int, night: Boolean, braking: Boolean, camF: Float, nearSide: Int, fine: Boolean) {
        val roof = if (accent != 0) accent else body
        val belt = hgt * .5f
        val front = hl - .52f; val rear = -hl + .5f
        wheelPair(front, rear, .29f, hw - .2f, hw + .02f, -nearSide)
        if (camF > 0f) keiRear(hl, hw, belt, night, braking, body) else frontParts(hl, hw, hgt, night, .54f)
        P(0, -hl, .26f); P(1, hl, .26f); P(2, hl, .6f); P(3, hl - .34f, belt); P(4, -hl, belt)
        prism(5, -hw, hw, body, body)
        val gw = hw * .95f
        P(0, -hl + .03f, belt); P(1, hl - .36f, belt); P(2, hl - .95f, hgt); P(3, -hl + .1f, hgt)
        prism(4, -gw, gw, GLASS, roof, roofIdx = 2)
        // thick pillars make it read as a box, a roof edge in the roof color
        sideRect(-.12f, .04f, belt, hgt - .09f, gw, body)
        sideRect(-hl + .1f, -hl + .42f, belt, hgt - .09f, gw, body)
        sideRect(-hl + .1f, hl - .95f, hgt - .09f, hgt, gw, roof)
        sideRect(-hl + .1f, hl - .1f, .3f, .38f, hw, scene.mix(body, 0xFF000000.toInt(), .3f))
        if (fine) {
            slab(hl - .5f, hl - .4f, -hw - .14f, -hw + .02f, belt + .02f, belt + .18f, body)
            slab(hl - .5f, hl - .4f, hw - .02f, hw + .14f, belt + .02f, belt + .18f, body)
        }
        if (camF > 0f) frontParts(hl, hw, hgt, night, .54f) else keiRear(hl, hw, belt, night, braking, body)
        wheelPair(front, rear, .29f, hw - .2f, hw + .02f, nearSide)
    }

    private fun keiRear(hl: Float, hw: Float, belt: Float, night: Boolean, braking: Boolean, body: Int) {
        slab(-hl - .08f, -hl + .02f, -hw + .05f, hw - .05f, .26f, .46f, BUMPER)
        val tail = if (braking) c(0xFFFF2E2E) else if (night) c(0xFFE53935) else TAIL
        // tall lamps up the corners
        frontRect(-hl - .01f, -hw + .06f, -hw + .24f, .5f, belt + .32f, tail, rear = true)
        frontRect(-hl - .01f, hw - .24f, hw - .06f, .5f, belt + .32f, tail, rear = true)
        frontRect(-hl - .01f, -.22f, .22f, .5f, .62f, c(0xFFF0F0E8), rear = true)
    }

    // ------------------------------------------------------------------ city bus

    /**
     * Single-decker city bus: a long box with a band of big windows (lit at night), doors on the curb side, a wide
     * windscreen under an amber destination display, an air-conditioning pod on the white roof, and the city's livery:
     * [body] with an [accent] stripe.
     */
    private fun bus(hl: Float, hw: Float, hgt: Float, body: Int, accent: Int, night: Boolean, braking: Boolean, camF: Float, nearSide: Int, fine: Boolean) {
        val stripe = if (accent != 0) accent else scene.mix(body, 0xFF000000.toInt(), .35f)
        val front = hl - 2.5f; val rear = -hl + 3f
        val curb = if (leftHand) -1 else 1
        wheelPair(front, rear, .5f, hw - .34f, hw + .01f, -nearSide)
        if (camF > 0f) busRear(hl, hw, hgt, night, braking, fine) else busFront(hl, hw, hgt, night, fine)
        P(0, -hl, .32f); P(1, hl, .32f); P(2, hl, hgt - .16f); P(3, hl - .16f, hgt); P(4, -hl + .1f, hgt); P(5, -hl, hgt - .1f)
        prism(6, -hw, hw, body, BUS_ROOF)
        // livery: a stripe under the windows and a band above them, a dark skirt
        sideRect(-hl, hl, .32f, .5f, hw, scene.mix(body, 0xFF000000.toInt(), .45f))
        sideRect(-hl, hl, .9f, 1.08f, hw, stripe)
        sideRect(-hl, hl, 2.66f, 2.78f, hw, stripe)
        // the window band, broken by pillars
        val win = if (night) LIT_WINDOW else GLASS
        val w0 = -hl + .45f; val w1 = hl - 1.45f
        val n = if (fine) ((w1 - w0) / 1.35f).toInt() else 3
        val step = (w1 - w0) / n
        for (k in 0 until n) sideRect(w0 + k * step + .07f, w0 + (k + 1) * step - .07f, 1.2f, 2.58f, hw, win)
        // driver's window on one side, the front door opposite, the middle door behind it
        sideRectAt(hl - 1.35f, hl - .25f, 1.15f, 2.6f, -hw, hw, win, only = -curb)
        sideRectAt(hl - 1.4f, hl - .3f, .4f, 2.62f, -hw, hw, c(0xFF1B2128), only = curb)
        sideRectAt(hl - 1.33f, hl - .37f, .5f, 2.55f, -hw - .01f, hw + .01f, GLASS, only = curb)
        sideRectAt(-.65f, .65f, .4f, 2.62f, -hw, hw, c(0xFF1B2128), only = curb)
        sideRectAt(-.58f, .58f, .5f, 2.55f, -hw - .01f, hw + .01f, GLASS, only = curb)
        if (fine) {
            // a destination display on the side and mirrors on stalks
            sideRectAt(hl - 2.6f, hl - 1.6f, 2.3f, 2.52f, -hw - .01f, hw + .01f, c(0xFF15171A), only = curb)
            slab(hl + .02f, hl + .22f, -hw - .2f, -hw - .04f, 1.9f, 2.4f, BUMPER)
            slab(hl + .02f, hl + .22f, hw + .04f, hw + .2f, 1.9f, 2.4f, BUMPER)
        }
        // an air-conditioning pod and an escape hatch on the roof
        slab(-1.6f, 1.3f, -hw * .62f, hw * .62f, hgt, hgt + .24f, c(0xFFD4D8DC))
        if (fine) slab(-hl + 1.5f, -hl + 2.3f, -.4f, .4f, hgt, hgt + .06f, c(0xFFB9BEC3))
        if (camF > 0f) busFront(hl, hw, hgt, night, fine) else busRear(hl, hw, hgt, night, braking, fine)
        wheelPair(front, rear, .5f, hw - .34f, hw + .01f, nearSide)
    }

    /**
     * A modern jeepney: the new boxy minibus with a flat front, a big windscreen under the LED route board, a tinted
     * window band, the door on the curb side and an air conditioner on the roof, in its cooperative's colors.
     */
    private fun modernJeep(hl: Float, hw: Float, hgt: Float, body: Int, accent: Int, night: Boolean, braking: Boolean, camF: Float, nearSide: Int, fine: Boolean) {
        val band = if (accent != 0) accent else scene.mix(body, 0xFF000000.toInt(), .35f)
        val front = hl - 1.2f; val rear = -hl + 1.5f
        val curb = if (leftHand) -1 else 1
        wheelPair(front, rear, .42f, hw - .3f, hw + .01f, -nearSide)
        if (camF > 0f) modernJeepRear(hl, hw, hgt, night, braking, fine) else modernJeepFront(hl, hw, hgt, night, band)
        P(0, -hl, .36f); P(1, hl, .36f); P(2, hl, hgt - .26f); P(3, hl - .22f, hgt); P(4, -hl + .1f, hgt); P(5, -hl, hgt - .1f)
        prism(6, -hw, hw, body, BUS_ROOF)
        // the cooperative's colors: a dark skirt, a bold band under the windows and a thin one over them
        sideRect(-hl, hl, .36f, .52f, hw, scene.mix(body, 0xFF000000.toInt(), .5f))
        sideRect(-hl, hl, .82f, 1.08f, hw, band)
        sideRect(-hl, hl, 2.4f, 2.48f, hw, band)
        // passenger windows back to the door, which opens on the curb side just behind the front wheel
        val win = if (night) LIT_WINDOW else GLASS
        val door0 = front - 1.55f; val door1 = front - .5f
        val w0 = -hl + .35f; val w1 = door0 - .08f
        val n = if (fine) 4 else 2
        val step = (w1 - w0) / n
        for (k in 0 until n) sideRect(w0 + k * step + .06f, w0 + (k + 1) * step - .06f, 1.2f, 2.32f, hw, win)
        sideRectAt(door0, hl - .2f, 1.2f, 2.32f, -hw, hw, win, only = -curb)
        sideRectAt(door0, door1, .42f, 2.36f, -hw, hw, c(0xFF1B2128), only = curb)
        sideRectAt(door0 + .07f, door1 - .07f, .52f, 2.3f, -hw - .01f, hw + .01f, GLASS, only = curb)
        sideRectAt(door1 + .08f, hl - .2f, 1.2f, 2.32f, -hw, hw, win, only = curb)
        if (fine) {
            // the route card in the window by the door, and mirrors on stalks
            sideRectAt(door1 + .2f, door1 + .85f, 1.7f, 2.05f, -hw - .015f, hw + .015f, c(0xFFF5F5F0), only = curb)
            slab(hl + .02f, hl + .2f, -hw - .2f, -hw - .04f, 1.6f, 2.1f, BUMPER)
            slab(hl + .02f, hl + .2f, hw + .04f, hw + .2f, 1.6f, 2.1f, BUMPER)
        }
        // the air conditioner on the roof
        slab(-1.3f, 1.0f, -hw * .6f, hw * .6f, hgt, hgt + .22f, c(0xFFD4D8DC))
        if (camF > 0f) modernJeepFront(hl, hw, hgt, night, band) else modernJeepRear(hl, hw, hgt, night, braking, fine)
        wheelPair(front, rear, .42f, hw - .3f, hw + .01f, nearSide)
    }

    private fun modernJeepFront(hl: Float, hw: Float, hgt: Float, night: Boolean, band: Int) {
        slab(hl - .02f, hl + .1f, -hw + .03f, hw - .03f, .36f, .62f, BUMPER)
        frontRect(hl + .01f, -hw + .1f, hw - .1f, 1.02f, 2.3f, GLASS)
        // the route board, amber letters on black, and the cooperative's band across the nose
        frontRect(hl + .01f, -hw + .12f, hw - .12f, 2.37f, hgt - .3f, c(0xFF15171A))
        frontRect(hl + .015f, -hw + .35f, hw - .35f, 2.44f, hgt - .37f, LED)
        frontRect(hl + .01f, -hw + .1f, hw - .1f, .66f, .9f, band)
        val head = if (night) HEAD else c(0xFFE8ECEF)
        frontRect(hl + .015f, -hw + .14f, -hw + .5f, .68f, .86f, head)
        frontRect(hl + .015f, hw - .5f, hw - .14f, .68f, .86f, head)
    }

    private fun modernJeepRear(hl: Float, hw: Float, hgt: Float, night: Boolean, braking: Boolean, fine: Boolean) {
        slab(-hl - .1f, -hl + .02f, -hw + .03f, hw - .03f, .36f, .58f, BUMPER)
        frontRect(-hl - .01f, -hw + .32f, hw - .32f, 1.3f, 2.3f, GLASS, rear = true)
        if (fine) frontRect(-hl - .015f, -.35f, .35f, 2.42f, 2.62f, LED, rear = true)
        val tail = if (braking) c(0xFFFF2E2E) else if (night) c(0xFFE53935) else TAIL
        frontRect(-hl - .01f, -hw + .08f, -hw + .28f, .7f, 1.5f, tail, rear = true)
        frontRect(-hl - .01f, hw - .28f, hw - .08f, .7f, 1.5f, tail, rear = true)
    }

    private fun busFront(hl: Float, hw: Float, hgt: Float, night: Boolean, fine: Boolean) {
        slab(hl - .02f, hl + .1f, -hw + .03f, hw - .03f, .32f, .58f, BUMPER)
        frontRect(hl + .01f, -hw + .1f, hw - .1f, 1.0f, 2.6f, GLASS)
        // the destination display: amber letters on black
        frontRect(hl + .01f, -hw + .15f, hw - .15f, 2.66f, hgt - .2f, c(0xFF15171A))
        frontRect(hl + .015f, -hw + .4f, hw - .7f, 2.74f, hgt - .28f, LED)
        if (fine) frontRect(hl + .015f, hw - .55f, hw - .3f, 2.74f, hgt - .28f, LED)
        val head = if (night) HEAD else c(0xFFE8ECEF)
        frontRect(hl + .11f, -hw + .15f, -hw + .55f, .62f, .78f, head)
        frontRect(hl + .11f, hw - .55f, hw - .15f, .62f, .78f, head)
    }

    private fun busRear(hl: Float, hw: Float, hgt: Float, night: Boolean, braking: Boolean, fine: Boolean) {
        slab(-hl - .1f, -hl + .02f, -hw + .03f, hw - .03f, .32f, .55f, BUMPER)
        frontRect(-hl - .01f, -hw + .35f, hw - .35f, 1.75f, 2.55f, GLASS, rear = true)
        // the engine grille, the route number and tall tail lamps
        frontRect(-hl - .01f, -.75f, .75f, .62f, 1.2f, c(0xFF2A2E33), rear = true)
        if (fine) frontRect(-hl - .015f, -.35f, .35f, 2.62f, 2.85f, LED, rear = true)
        val tail = if (braking) c(0xFFFF2E2E) else if (night) c(0xFFE53935) else TAIL
        frontRect(-hl - .01f, -hw + .08f, -hw + .3f, .62f, 1.5f, tail, rear = true)
        frontRect(-hl - .01f, hw - .3f, hw - .08f, .62f, 1.5f, tail, rear = true)
    }

    // ------------------------------------------------------------------ shared parts

    private fun bed(hl: Float, hw: Float, hgt: Float, cab0: Float, body: Int) {
        val b0 = -hl + .05f; val b1 = cab0 - .05f
        slab(b0 + .1f, b1, -hw + .1f, hw - .1f, hgt * .56f, hgt * .58f, c(0xFF33373C))
        slab(b0, b1, -hw, -hw + .1f, hgt * .56f, hgt * .78f, body)
        slab(b0, b1, hw - .1f, hw, hgt * .56f, hgt * .78f, body)
        slab(b0, b0 + .1f, -hw, hw, hgt * .56f, hgt * .78f, body)
    }

    private fun frontParts(hl: Float, hw: Float, hgt: Float, night: Boolean, lampZ: Float = hgt * .38f) {
        slab(hl - .02f, hl + .1f, -hw + .05f, hw - .05f, .25f, .48f, BUMPER)
        val z = lampZ
        val head = if (night) HEAD else c(0xFFE8ECEF)
        frontRect(hl + .11f, -hw + .15f, -hw + .55f, z - .1f, z + .06f, head)
        frontRect(hl + .11f, hw - .55f, hw - .15f, z - .1f, z + .06f, head)
        frontRect(hl + .11f, -.35f, .35f, z - .08f, z + .04f, c(0xFF3A3F45))
    }

    private fun rearParts(hl: Float, hw: Float, hgt: Float, style: Int, night: Boolean, braking: Boolean, body: Int) {
        if (style == 3) slab(-hl + .05f, -hl + .35f, -hw * .9f, hw * .9f, hgt * .7f, hgt * .76f, scene.mix(body, 0xFF000000.toInt(), .4f))
        slab(-hl - .1f, -hl + .02f, -hw + .05f, hw - .05f, .25f, .48f, BUMPER)
        val z = hgt * .38f
        val tail = if (braking) c(0xFFFF2E2E) else if (night) c(0xFFE53935) else TAIL
        frontRect(-hl - .11f, -hw + .12f, -hw + .45f, z - .08f, z + .06f, tail, rear = true)
        frontRect(-hl - .11f, hw - .45f, hw - .12f, z - .08f, z + .06f, tail, rear = true)
        frontRect(-hl - .11f, -.25f, .25f, z - .02f, z + .12f, c(0xFFF0F0E8), rear = true)
    }

    /** Headlight beams on the road: a soft oval of light ahead of the car, brightest near the bumper. */
    private fun headGlow(x: Float, y: Float, heading: Float, len: Float) {
        val hx = sin(heading); val hy = -cos(heading)
        val night = max(1f - scene.daylight, scene.rain * .75f).coerceIn(0f, 1f)
        if (night <= .01f) return
        val ahead = len * .5f + 2.6f
        scene.glow = true
        scene.glowOval(x + hx * ahead, y + hy * ahead, .05f, 4.6f, 2.4f, hx, hy, HEAD_GLOW, .3f * night)
        scene.glow = false
    }


    /** Simple boxes for traffic far away, in the car's frame. */
    private fun farCar(len: Float, wid: Float, hgt: Float, color: Int, style: Int, accent: Int) {
        val hl = len / 2; val hw = wid / 2
        when (style) {
            1 -> {
                slab(-hl, hl, -hw, hw, .32f, hgt * .55f, color)
                slab(-len * .04f, len * .32f, -hw * .9f, hw * .9f, hgt * .55f, hgt, GLASS, color)
            }
            2 -> slab(-hl, hl, -hw, hw, .3f, hgt, color)
            4 -> {
                slab(hl - 1.65f, hl, -hw * .75f, hw * .75f, .5f, 1.28f, color)
                slab(-hl, hl - 1.6f, -hw, hw, .48f, hgt * .9f, color, CHROME)
            }
            5 -> {
                val sd = if (leftHand) -1 else 1
                val l = sd * (hw - .46f)
                slab(-hl + .1f, .7f, l - .44f, l + .44f, .24f, hgt * .9f, color)
                val b = -sd * (hw - .27f)
                slab(-hl + .3f, hl, b - .12f, b + .12f, .2f, 1.1f, c(0xFF2B2E33))
            }
            6 -> {
                slab(-hl, hl, -hw, hw, .26f, hgt * .5f, color)
                slab(-hl + .05f, hl - .5f, -hw * .95f, hw * .95f, hgt * .5f, hgt, GLASS, if (accent != 0) accent else color)
            }
            7 -> {
                slab(-hl, hl, -hw, hw, .32f, 1.15f, color)
                slab(-hl, hl, -hw, hw, 1.15f, 2.6f, GLASS, GLASS)
                slab(-hl, hl, -hw, hw, 2.6f, hgt, color, BUS_ROOF)
            }
            8, 12 -> slab(-hl * .8f, hl * .8f, -.2f, .2f, .25f, 1.3f, color)
            11 -> {
                slab(-hl, hl, -hw, hw, .36f, 1.15f, color)
                slab(-hl, hl, -hw, hw, 1.15f, 2.36f, GLASS, GLASS)
                slab(-hl, hl, -hw, hw, 2.36f, hgt, color, BUS_ROOF)
            }
            9 -> {
                slab(-hl, hl, -hw, hw, .4f, hgt * .6f, color)
                slab(-hl + .2f, hl - len * .3f, -hw * .9f, hw * .9f, hgt * .6f, hgt, GLASS, color)
            }
            10 -> {
                slab(-hl, hl - 1.95f, -hw, hw, .62f, hgt, c(0xFFF1F2F0))
                slab(hl - 1.9f, hl, -hw * .96f, hw * .96f, .45f, 2.4f, color, color)
            }
            else -> {
                slab(-hl, hl, -hw, hw, .25f, hgt * .55f, color)
                slab(-len * .29f, len * .19f, -hw * .84f, hw * .84f, hgt * .55f, hgt, GLASS, color)
            }
        }
    }

    private fun luma(color: Int) = (((color shr 16) and 255) * 3 + ((color shr 8) and 255) * 6 + (color and 255)) / 10

    /** Sets point [i] of the profile (forward, height). */
    private fun P(i: Int, f: Float, z: Float) { pf[i] = f; pzz[i] = z }

    // the body's lean on its springs (rise per meter forward and to the right); wheels and the ground stay put
    private var tiltF = 0f
    private var tiltL = 0f
    private var onWheels = false

    private fun wpt(f: Float, l: Float, z: Float, i: Int) {
        wx[i] = ox + fx * f + rx * l; wy[i] = oy + fy * f + ry * l
        wz[i] = if (onWheels || z < .25f) z else z + f * tiltF + l * tiltL
    }

    /** Moves the frame [l] meters to the right, so off-centre parts (a sidecar) can be built centred. */
    private fun shift(l: Float) { ox += rx * l; oy += ry * l }

    private fun shadeFor(nx: Float, ny: Float, nz: Float): Float {
        if (nz >= .7f) return 1f
        val h = sqrt(nx * nx + ny * ny)
        val side = if (h > 1e-3f) scene.sideShade(nx / h, ny / h) else .8f
        return if (nz >= 0f) side + (1f - side) * nz else side * .7f
    }

    /**
     * Extrudes the current profile (forward, z points, counter-clockwise seen from the right) between lateral
     * offsets [l0] and [l1]. Edge [glassIdx] is glass (a windscreen); edge [roofIdx] takes [roof] color.
     */
    private fun prism(n: Int, l0: Float, l1: Float, sides: Int, roof: Int, glassIdx: Int = -1, roofIdx: Int = -1) {
        // profile centroid, to orient edge normals outward
        var cf = 0f; var cz = 0f
        for (i in 0 until n) { cf += pf[i]; cz += pzz[i] }
        cf /= n; cz /= n
        val camX = scene.camLX; val camY = scene.camLY; val camZ = proj.camZ
        for (i in 0 until n) {
            val j = (i + 1) % n
            val df = pf[j] - pf[i]; val dz = pzz[j] - pzz[i]
            var nf = dz; var nz = -df
            val mf = (pf[i] + pf[j]) / 2; val mz = (pzz[i] + pzz[j]) / 2
            if ((mf - cf) * nf + (mz - cz) * nz < 0) { nf = -nf; nz = -nz }
            val ln = sqrt(nf * nf + nz * nz); if (ln < 1e-4f) continue
            nf /= ln; nz /= ln
            val nwx = fx * nf; val nwy = fy * nf
            val mx = ox + fx * mf; val my = oy + fy * mf
            if ((camX - mx) * nwx + (camY - my) * nwy + (camZ - mz) * nz <= 0f) continue
            val base = when (i) { glassIdx -> GLASS; roofIdx -> roof; else -> if (nz > .7f) roof else sides }
            val col = if (nz > .7f) scene.lit(scene.snowy(base), 1f) else scene.lit(base, shadeFor(nwx, nwy, nz))
            wpt(pf[i], l0, pzz[i], 0); wpt(pf[j], l0, pzz[j], 1); wpt(pf[j], l1, pzz[j], 2); wpt(pf[i], l1, pzz[i], 3)
            scene.poly(4, wx, wy, wz, col)
        }
        // the two side faces
        for (side in 0..1) {
            val l = if (side == 0) l0 else l1
            val sgn = if (side == 0) -1f else 1f
            val px = ox + rx * l; val py = oy + ry * l
            if ((camX - px) * rx * sgn + (camY - py) * ry * sgn <= 0f) continue
            for (i in 0 until n) wpt(pf[i], l, pzz[i], i)
            scene.poly(n, wx, wy, wz, scene.lit(sides, scene.sideShade(rx * sgn, ry * sgn)))
        }
    }

    /** An axis-aligned box in the car's frame (bumpers, bed walls, roof signs); [top] colors its upper face. */
    private fun slab(f0: Float, f1: Float, l0: Float, l1: Float, z0: Float, z1: Float, color: Int, top: Int = color) {
        P(0, f0, z0); P(1, f1, z0); P(2, f1, z1); P(3, f0, z1)
        prism(4, l0, l1, color, top, roofIdx = 2)
    }

    /** A flat rectangle on both sides of the body (windows, stripes). */
    private fun sideRect(f0: Float, f1: Float, z0: Float, z1: Float, hw: Float, color: Int) = sideRectAt(f0, f1, z0, z1, -hw, hw, color)

    /** A flat rectangle on the left face at [l0] and the right face at [l1]; [only] -1 or 1 keeps just that side. */
    private fun sideRectAt(f0: Float, f1: Float, z0: Float, z1: Float, l0: Float, l1: Float, color: Int, only: Int = 0) {
        for (si in 0..1) {
            val sgn = if (si == 0) -1 else 1
            if (only != 0 && only != sgn) continue
            val l = (if (si == 0) l0 else l1) + .02f * sgn
            val px = ox + rx * l; val py = oy + ry * l
            if ((scene.camLX - px) * rx * sgn + (scene.camLY - py) * ry * sgn <= 0f) continue
            wpt(f0, l, z0, 0); wpt(f1, l, z0, 1); wpt(f1, l, z1, 2); wpt(f0, l, z1, 3)
            scene.poly(4, wx, wy, wz, scene.lit(color, scene.sideShade(rx * sgn, ry * sgn)))
        }
    }

    /** A rectangle on the front (or rear) face: lights and plates. */
    private fun frontRect(f: Float, l0: Float, l1: Float, z0: Float, z1: Float, color: Int, rear: Boolean = false) {
        val sgn = if (rear) -1f else 1f
        val px = ox + fx * f; val py = oy + fy * f
        if ((scene.camLX - px) * fx * sgn + (scene.camLY - py) * fy * sgn <= 0f) return
        wpt(f, l0, z0, 0); wpt(f, l1, z0, 1); wpt(f, l1, z1, 2); wpt(f, l0, z1, 3)
        scene.poly(4, wx, wy, wz, scene.lit(color, max(.85f, scene.sideShade(fx * sgn, fy * sgn))))
    }

    /** A flat cut-out standing upright along the car at lateral offset [l] (ornaments). */
    private fun fz4(l: Float, f0: Float, z0: Float, f1: Float, z1: Float, f2: Float, z2: Float, f3: Float, z3: Float, color: Int) {
        wpt(f0, l, z0, 0); wpt(f1, l, z1, 1); wpt(f2, l, z2, 2); wpt(f3, l, z3, 3)
        scene.poly(4, wx, wy, wz, color)
    }

    private fun quadFlat(f0: Float, l0: Float, f1: Float, l1: Float, z: Float, color: Int) {
        wpt(f0, l0, z, 0); wpt(f1, l0, z, 1); wpt(f1, l1, z, 2); wpt(f0, l1, z, 3)
        scene.poly(4, wx, wy, wz, color)
    }

    /** A box truck: a cab up front and a tall cargo box with a roll-up door and the company stripe. */
    private fun boxTruck(hl: Float, hw: Float, len: Float, hgt: Float, body: Int, accent: Int, night: Boolean, braking: Boolean, camF: Float, nearSide: Int) {
        val cab = hl - 1.9f
        val box = c(0xFFF1F2F0)
        val stripe = if (accent != 0) accent else body
        wheelPair(hl - 1.05f, -hl + 1.35f, .46f, hw - .32f, hw + .02f, -nearSide)
        if (camF > 0f) rearParts(hl, hw, 1.4f, 10, night, braking, body) else frontParts(hl, hw, 2.4f, night, lampZ = .85f)
        // the cargo box, raised on its chassis, and the cab
        slab(-hl, cab - .08f, -hw, hw, .62f, hgt, box, box)
        sideRect(-hl + .2f, cab - .3f, hgt * .55f, hgt * .7f, hw, stripe)
        frontRect(-hl - .005f, -hw + .15f, hw - .15f, .75f, hgt - .15f, c(0xFFD6D9DD), rear = true)
        P(0, cab, .45f); P(1, hl, .45f); P(2, hl, 1.45f); P(3, hl - .45f, 2.35f); P(4, cab, 2.45f)
        prism(5, -hw * .96f, hw * .96f, body, body, glassIdx = 2)
        sideRect(cab + .25f, hl - .7f, 1.5f, 2.15f, hw * .96f, GLASS)
        if (camF > 0f) frontParts(hl, hw, 2.4f, night, lampZ = .85f) else rearParts(hl, hw, 1.4f, 10, night, braking, body)
        wheelPair(hl - 1.05f, -hl + 1.35f, .46f, hw - .32f, hw + .02f, nearSide)
    }

    private fun wheels(len: Float, wid: Float, style: Int, only: Int) {
        val r = if (style == 2 || style == 9) .4f else if (style == 3) .34f else .36f
        val axle = len / 2 - (if (style == 2) .8f else .75f)
        val hw = wid / 2
        wheelPair(axle, -axle, r, hw - .22f, hw + .03f, only)
    }

    /** Both wheels on side [only] (-1 left, 1 right), the tread between [lIn] and [lOut] from the centre line. */
    private fun wheelPair(front: Float, rear: Float, r: Float, lIn: Float, lOut: Float, only: Int) {
        wheelAt(front, r, only * lIn, only * lOut, only)
        wheelAt(rear, r, only * lIn, only * lOut, only)
    }

    /** One wheel at [af] spanning lateral [la]..[lb], with a hub cap on its [outer] (-1/1) face. */
    private fun wheelAt(af: Float, r: Float, la: Float, lb: Float, outer: Int) {
        onWheels = true
        try { wheelAtLevel(af, r, la, lb, outer) } finally { onWheels = false }
    }

    private fun wheelAtLevel(af: Float, r: Float, la: Float, lb: Float, outer: Int) {
        for (k in 0 until 8) {
            val a = k * 6.2832f / 8 + .3927f
            wheelF[k] = cos(a) * r; wheelZ[k] = sin(a) * r
        }
        for (k in 0 until 8) { pf[k] = af + wheelF[k]; pzz[k] = r + wheelZ[k] }
        val l0 = minOf(la, lb); val l1 = maxOf(la, lb)
        prism(8, l0, l1, TIRE, TIRE)
        // hub cap on the outer side
        val l = (if (outer > 0) l1 else l0) + .01f * outer
        val px = ox + rx * l; val py = oy + ry * l
        if ((scene.camLX - px) * rx * outer + (scene.camLY - py) * ry * outer > 0f) {
            for (k in 0 until 8) wpt(af + wheelF[k] * .55f, l, r + wheelZ[k] * .55f, k)
            scene.poly(8, wx, wy, wz, scene.lit(RIM, scene.sideShade(rx * outer, ry * outer)))
        }
    }
}
