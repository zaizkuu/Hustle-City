package com.hustlecity.game.render

import com.hustlecity.game.data.c
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val PI_F = 3.14159265f

/**
 * What a person is doing with their hands, acted out on top of their pose (HD models): staff at work, customers
 * paying, eating or training. [PersonPainter.draw] takes one with a running time.
 */
object Act {
    const val NONE = 0
    /** Behind the counter, hands on it, now and then typing on the till. */
    const val TILL = 1
    /** Sweeping goods past the scanner. */
    const val SCAN = 2
    /** Packing a bag. */
    const val BAG = 3
    /** Handing something across a counter, or taking it. */
    const val GIVE = 4
    /** At a keyboard (seated or standing). */
    const val TYPE = 5
    /** Mopping the floor. */
    const val MOP = 6
    /** Behind the decks: a hand on the headphones, the other on the platter. */
    const val DJ = 7
    /** Cutting the hair of someone seated in front. */
    const val HAIR = 8
    /** Coaching with a clipboard, pointing now and then. */
    const val COACH = 9
    /** Playing to the audience. */
    const val PERFORM = 10
    /** Taking goods out of a box onto a shelf. */
    const val STOCK = 11
    /** Straightening the goods on a display (no box). */
    const val TIDY = 20
    /** Reaching up to a shelf for something. */
    const val REACH = 12
    /** Putting something down on a counter. */
    const val PLACE = 13
    /** Holding a card to the reader. */
    const val CARD = 14
    /** Eating and drinking, seated. */
    const val EAT = 15
    /** Pressing a bar up from a bench. */
    const val PRESS = 16
    /** Dumbbell curls. */
    const val CURL = 17
    /** Punching a bag. */
    const val PUNCH = 18
    /** Pulling a rowing handle. */
    const val ROW = 19
    /** At a street food cart's griddle: turning what's cooking with a spatula, now and then handing it over. */
    const val COOK = 21
    /** Reading their phone, both thumbs on it. */
    const val PHONE = 22
    /** On a call, the phone held to their cheek. */
    const val CALL = 23
    /** Talking with someone, a hand going as they speak. */
    const val TALK = 24
    /** Looking in a shop window: chin in hand, now and then pointing something out. */
    const val POINT = 25
    /** Looking about: both ways before crossing, round the street while they wait. */
    const val LOOK = 26
    /** Eating standing up or on the move: a snack in hand, a bite now and then ([PersonPainter.draw]'s item says what). */
    const val SNACK = 27
    /** Paying at a stall: the money held out across it. */
    const val PAY = 28
    /** One past the last act. */
    const val COUNT = 29
}

/** What a [Act.SNACK] has in hand. */
object Snack {
    /** Fishballs, yakitori, fish cake: something on a skewer. */
    const val SKEWER = 0
    const val HOT_DOG = 1
    /** A drink with a straw (taho, soda). */
    const val CUP = 2
    /** A paper boat or bag: takoyaki, fries, tteokbokki, kwek-kwek. */
    const val BOAT = 3
    /** Something round in a paper wrap: a pretzel, a hotteok, a burger. */
    const val WRAP = 4
}

/** Poses for [PersonPainter]. */
object Pose {
    const val STAND = 0
    const val SIT = 1
    const val DANCE = 2
    /** Running on a treadmill: a full walking cycle without moving. */
    const val RUN = 3
}

/**
 * Draws a 3D person facing [heading] (0 = north). In HD, anyone close enough to make out is one of the CC0
 * characters of [PeopleModels], dressed in their own colours and posed from its rig's clips (walking, running,
 * standing, sitting, dancing, carrying); otherwise, and on the Canvas, a low-poly figure out of boxes whose legs and
 * arms swing. Umbrellas, boxes, carts and bags are boxes either way. No allocations per call, and box parts are drawn
 * back to front so the figure looks solid from any side.
 */
class PersonPainter(private val scene: Scene3D) {
    /** Seconds, for the idle sway of people standing still. */
    var clock = 0f

    companion object {
        val PANTS = intArrayOf(c(0xFF34383F), c(0xFF1F3A5F), c(0xFF4A3B2A), c(0xFF5B6770), c(0xFF2E4A3A), c(0xFF22252A), c(0xFF6B4F3A))
        val HAIR = intArrayOf(c(0xFF2B1D14), c(0xFF4A3220), c(0xFF0F0F10), c(0xFF8A5A2B), c(0xFFD9B26A), c(0xFF7A7A7A), c(0xFFA0522D))
        val UMBRELLAS = intArrayOf(c(0xFF1F2933), c(0xFFC0392B), c(0xFF2F80ED), c(0xFFF2C94C), c(0xFF27AE60), c(0xFF9B51E0))
        private const val MAX = 40
        private val CARD = c(0xFFC8A165)
        private val CARD_DARK = c(0xFFB38A55)
        private val TIRE = c(0xFF1C1E22)
        private val STEEL = c(0xFF9AA3AD)
        private val TRUCK_RED = c(0xFFD0392B)
        private val CART_BLUE = c(0xFF2F80ED)
        val BAG = c(0xFFD9B98A)
        val BAG_DARK = c(0xFFB38A55)
        /** Taller on screen than this many pixels, a person is drawn as a model. */
        private const val MODEL_PX = 30f
        private const val TWO_PI = (Math.PI * 2).toFloat()
    }

    // parts: forward offset, right offset, z0, z1, length (forward), width (right), color, draw-last flag
    private val pf = FloatArray(MAX); private val pr = FloatArray(MAX)
    private val pz0 = FloatArray(MAX); private val pz1 = FloatArray(MAX)
    private val pl = FloatArray(MAX); private val pw = FloatArray(MAX)
    private val pc = IntArray(MAX); private val pfront = IntArray(MAX); private val plast = BooleanArray(MAX)
    private val key = FloatArray(MAX); private val order = IntArray(MAX)
    private var n = 0
    private val qx = FloatArray(4); private val qy = FloatArray(4)
    private val wx = FloatArray(4); private val wy = FloatArray(4); private val wz = FloatArray(4)

    private fun part(f: Float, r: Float, z0: Float, z1: Float, l: Float, w: Float, color: Int, front: Int = color, last: Boolean = false) {
        if (n >= MAX) return
        pf[n] = f; pr[n] = r; pz0[n] = z0; pz1[n] = z1; pl[n] = l; pw[n] = w; pc[n] = color; pfront[n] = front; plast[n] = last
        n++
    }

    /**
     * @param z height of the floor or seat the person stands/sits on (0 for the ground)
     * @param moving 0..1 how much the limbs swing (walking speed)
     * @param umbrella umbrella color, or 0 for none
     * @param carry 0 nothing, 1 a box in both arms, 2 pushing a hand truck, 3 pushing a flatbed cart
     * @param load boxes on the hand truck or cart
     * @param held color of a product held in the right hand (a shopper's pick), or 0 for none
     * @param bag true to carry a paper shopping bag instead (paid for)
     * @param reach arms out in front, empty-handed (handing something over at a counter)
     */
    fun draw(
        x: Float, y: Float, z: Float, heading: Float, phase: Float, moving: Float,
        shirt: Int, pants: Int, skin: Int, hair: Int, scale: Float = 1f, pose: Int = Pose.STAND,
        apron: Int = 0, umbrella: Int = 0, alpha: Float = 1f, carry: Int = 0, load: Int = 0, held: Int = 0, bag: Boolean = false,
        reach: Boolean = false, act: Int = Act.NONE, actT: Float = 0f, item: Int = Snack.SKEWER,
    ) {
        n = 0
        // until the model says otherwise, the right hand hangs at the hip, a little out from the body
        val hfx = sin(heading); val hfy = -cos(heading)
        handX = x - hfy * .27f * scale; handY = y + hfx * .27f * scale; handZ = z + .86f * scale
        val s = scale
        // far away people get a cheap three-box version
        val camD = proj().camZOf(x, y, z + .9f * s)
        val lod = camD > 0 && proj().focal * 1.75f * s / camD < 22f
        val models = PeopleModels.models
        if (scene.gpu != null && models.isNotEmpty() && camD > 0 && proj().focal * 1.75f * s / camD > MODEL_PX) {
            val holding = carry >= 2 || (carry == 1 && load > 0)
            modelled(models[pick(shirt, pants, hair, skin, models.size)], x, y, z, heading, phase, moving, pose, s,
                if (apron != 0) apron else shirt, pants, skin, hair, alpha, holding || reach, act, actT)
            // what they push or shelter under goes on as before; what they hold hangs from the model's own hand
            if (pose == Pose.STAND || pose == Pose.RUN) {
                if (holding) carried(z, z + .82f * s, s, carry, load)
                else if ((held != 0 || bag) && handLocal[3] > 0f) inModelHand(z, s, held, bag)
            }
            if (umbrella != 0 && pose == Pose.STAND) umbrella(z, s, umbrella)
            emit(x, y, heading, alpha)
            if (act != Act.NONE && handLocal[3] > 0f) props(act, actT, x, y, z, heading, s, item)
            return
        }
        when (pose) {
            Pose.SIT -> {
                // seat height z: thighs forward, shins down to the floor
                val h = z
                part(.2f * s, -.1f * s, h, h + .14f * s, .42f * s, .14f * s, pants)
                part(.2f * s, .1f * s, h, h + .14f * s, .42f * s, .14f * s, pants)
                part(.39f * s, -.1f * s, (h - .45f * s).coerceAtLeast(0f), h + .02f, .12f * s, .12f * s, pants)
                part(.39f * s, .1f * s, (h - .45f * s).coerceAtLeast(0f), h + .02f, .12f * s, .12f * s, pants)
                upperBody(0f, h + .12f * s, s, shirt, skin, hair, apron, 0f, phase, false)
            }
            Pose.DANCE -> {
                val bounce = abs(sin(phase * 1.6f)) * .08f * s
                val step = sin(phase * 1.6f) * .08f * s
                legs(z + bounce, s, pants, step)
                upperBody(0f, z + .82f * s + bounce, s, shirt, skin, hair, apron, 0f, phase, true)
            }
            else -> {
                val amp = if (pose == Pose.RUN) 1f else moving.coerceIn(0f, 1f)
                val swing = sin(phase) * amp
                val bob = abs(sin(phase)) * .03f * amp * s
                if (lod) {
                    part(0f, 0f, z, z + .82f * s, .16f * s, .3f * s, pants)
                } else legs(z + bob, s, pants, swing * .17f * s)
                val holding = carry >= 2 || (carry == 1 && load > 0)
                upperBody(0f, z + .82f * s + bob, s, shirt, skin, hair, apron, swing, phase, false, lod,
                    reach = holding || reach || act == Act.GIVE || act == Act.PLACE || act == Act.CARD || act == Act.SCAN || act == Act.TILL || act == Act.STOCK)
                if (holding) carried(z, z + .82f * s + bob, s, carry, load)
                else if (!lod && (held != 0 || bag)) inHand(z + .82f * s + bob, s, swing, held, bag)
            }
        }
        if (umbrella != 0 && pose == Pose.STAND) umbrella(z, s, umbrella)
        emit(x, y, heading, alpha)
    }

    private fun umbrella(z: Float, s: Float, color: Int) {
        val top = z + 2.05f * s
        part(.08f * s, .2f * s, z + 1.1f * s, top, .03f * s, .03f * s, c(0xFF2B2B2B), last = true)
        part(.05f * s, .1f * s, top - .06f * s, top, 1.05f * s, 1.05f * s, color, last = true)
        part(.05f * s, .1f * s, top, top + .1f * s, .6f * s, .6f * s, color, last = true)
    }

    /** Which character a person is, from their colours, so they stay the same one from frame to frame. */
    private fun pick(shirt: Int, pants: Int, hair: Int, skin: Int, count: Int): Int {
        var h = shirt * 31 + pants
        h = h * 31 + hair; h = h * 31 + skin
        h = h xor (h ushr 13); h *= 0x5bd1e995; h = h xor (h ushr 15)
        return ((h % count) + count) % count
    }

    private val bones = FloatArray(32 * 12)
    /** The standing pose, for arms that stay put while the legs walk. */
    private val idle = FloatArray(32 * 12)
    private val inv = FloatArray(12); private val rel = FloatArray(12)

    /**
     * Puts arm [first]'s upper arm, forearm and hand (the three bones after its shoulder bone) as they are in [idle],
     * relative to the shoulder bone as it is in [bones].
     */
    private fun rebase(first: Int) {
        if (first < 0) return
        val s = first * 12
        // the idle shoulder's inverse (rigid: the rotation transposed, the translation turned back)
        for (r in 0 until 3) for (c in 0 until 3) inv[r * 4 + c] = idle[s + c * 4 + r]
        for (r in 0 until 3) inv[r * 4 + 3] = -(inv[r * 4] * idle[s + 3] + inv[r * 4 + 1] * idle[s + 7] + inv[r * 4 + 2] * idle[s + 11])
        // the walking shoulder times that: what takes the idle arm onto the walking body
        compose(bones, s, inv, 0, rel)
        for (b in first + 1..first + 3) {
            compose(rel, 0, idle, b * 12, inv)
            System.arraycopy(inv, 0, bones, b * 12, 12)
        }
        // inv was used as scratch: it's rebuilt on the next call
    }

    /** out = a (at [ao]) times b (at [bo]), 3x4 rigid transforms stored row by row. */
    private fun compose(a: FloatArray, ao: Int, b: FloatArray, bo: Int, out: FloatArray) {
        for (r in 0 until 3) {
            val a0 = a[ao + r * 4]; val a1 = a[ao + r * 4 + 1]; val a2 = a[ao + r * 4 + 2]
            for (c in 0 until 4) out[r * 4 + c] = a0 * b[bo + c] + a1 * b[bo + 4 + c] + a2 * b[bo + 8 + c] + (if (c == 3) a[ao + r * 4 + 3] else 0f)
        }
    }
    private var vx = FloatArray(0); private var vy = FloatArray(0); private var vz = FloatArray(0)
    private val tx = FloatArray(3); private val ty = FloatArray(3); private val tz = FloatArray(3)

    /** [m] posed for what the person is doing, at (x, y, z) facing [heading], in their colours. */
    private fun modelled(m: PeopleModels.Model, x: Float, y: Float, z: Float, heading: Float, phase: Float, moving: Float, pose: Int,
                         s: Float, top: Int, bottom: Int, skin: Int, hair: Int, alpha: Float, holding: Boolean, act: Int = Act.NONE, actT: Float = 0f) {
        posed(m, x + y, phase, moving, pose, holding, act, actT)
        skinned(m, x, y, z, heading, s, top, bottom, skin, hair, alpha)
    }

    /** Tests: model [index] [act]ing [t] seconds in while walking at [moving] (0..1), [cycle] (0..1) through a stride: hand minus neck. */
    internal fun debugWalkingHand(index: Int, act: Int, t: Float, moving: Float, cycle: Float): FloatArray {
        val m = PeopleModels.models[index]
        posed(m, 0f, cycle * TWO_PI, moving, Pose.STAND, false, act, t)
        val j = jointsOf(m); val b = bones
        val wr = wristOf(m); val o = wr[0].toInt() * 12
        val n = j.neck; val q = 6 * 12
        return FloatArray(3) { r ->
            (b[o + r * 4] * wr[1] + b[o + r * 4 + 1] * wr[2] + b[o + r * 4 + 2] * wr[3] + b[o + r * 4 + 3]) -
                (b[q + r * 4] * n[0] + b[q + r * 4 + 1] * n[1] + b[q + r * 4 + 2] * n[2] + b[q + r * 4 + 3])
        }
    }

    /** Poses [m]'s bones for [pose] at [moving] speed, [phase] through the stride, acting [act]; [seed] staggers the idle sway. */
    private fun posed(m: PeopleModels.Model, seed: Float, phase: Float, moving: Float, pose: Int, holding: Boolean, act: Int, actT: Float) {
        val rig = m.rig
        val cycle = (phase / TWO_PI).let { it - kotlin.math.floor(it) }
        fun dur(name: String) = rig.clips[name]?.duration ?: 1f
        when (pose) {
            Pose.SIT -> PeopleModels.pose(rig, "Sit", 0f, "Sit", 0f, 0f, bones)
            Pose.DANCE -> PeopleModels.pose(rig, "Dance", phase * .3f, "Dance", 0f, 0f, bones)
            Pose.RUN -> PeopleModels.pose(rig, "Run", cycle * dur("Run"), "Run", 0f, 0f, bones)
            else -> {
                // standing still to walking, by how fast they go; arms held out in front when they carry something
                val k = moving.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
                val still = if (holding) "CarryIdle" else "Idle"
                val walk = if (holding) "Carry" else "Walk"
                PeopleModels.pose(rig, still, clock + seed * .37f, walk, cycle * dur(walk), k, bones)
                // an arm busy with a phone, a snack or money keeps its standing pose, carried along by the walking
                // shoulders, rather than swinging with the stride
                if (k > 0f && (act == Act.PHONE || act == Act.CALL || act == Act.SNACK || act == Act.PAY)) {
                    PeopleModels.pose(rig, still, clock + seed * .37f, still, 0f, 0f, idle)
                    val j = jointsOf(m)
                    rebase(j.right)
                    if (act == Act.PHONE) rebase(j.left)
                }
            }
        }
        if (act != Act.NONE) acting(m, act, actT, pose)
    }

    /** Skins [m]'s mesh to the bones just posed, at (x, y, z) facing [heading], in their colours; notes where the hands are. */
    private fun skinned(m: PeopleModels.Model, x: Float, y: Float, z: Float, heading: Float, s: Float, top: Int, bottom: Int, skin: Int, hair: Int, alpha: Float) {
        val nv = m.vertices
        if (vx.size < nv) { vx = FloatArray(nv); vy = FloatArray(nv); vz = FloatArray(nv) }
        val fx = sin(heading); val fy = -cos(heading)
        val rx = -fy; val ry = fx
        // where the left hand is (for what it holds), then the right
        val lj = jointsOf(m)
        if (lj.left >= 0) {
            val o = (lj.left + 3) * 12; val b = bones
            val hf = b[o] * lj.lHand[0] + b[o + 1] * lj.lHand[1] + b[o + 2] * lj.lHand[2] + b[o + 3]
            val hr = b[o + 4] * lj.lHand[0] + b[o + 5] * lj.lHand[1] + b[o + 6] * lj.lHand[2] + b[o + 7]
            val hu = b[o + 8] * lj.lHand[0] + b[o + 9] * lj.lHand[1] + b[o + 10] * lj.lHand[2] + b[o + 11]
            lHandX = x + (fx * hf + rx * hr) * s; lHandY = y + (fy * hf + ry * hr) * s; lHandZ = z + hu * s
        }
        // where the right hand is in this pose
        val wr = wristOf(m)
        handLocal[3] = 0f
        if (wr[0] >= 0f) {
            val o = wr[0].toInt() * 12; val b = bones
            val hf = b[o] * wr[1] + b[o + 1] * wr[2] + b[o + 2] * wr[3] + b[o + 3]
            val hr = b[o + 4] * wr[1] + b[o + 5] * wr[2] + b[o + 6] * wr[3] + b[o + 7]
            val hu = b[o + 8] * wr[1] + b[o + 9] * wr[2] + b[o + 10] * wr[3] + b[o + 11]
            handLocal[0] = hf; handLocal[1] = hr; handLocal[2] = hu; handLocal[3] = 1f
            handX = x + (fx * hf + rx * hr) * s; handY = y + (fy * hf + ry * hr) * s; handZ = z + hu * s
        }
        val p = m.pos; val b = bones
        for (v in 0 until nv) {
            val px = p[v * 3]; val py = p[v * 3 + 1]; val pz = p[v * 3 + 2]
            val a = (m.b0[v].toInt() and 255) * 12; val c = (m.b1[v].toInt() and 255) * 12
            val w = m.w[v]; val u = 1 - w
            val f = (b[a] * px + b[a + 1] * py + b[a + 2] * pz + b[a + 3]) * w + (b[c] * px + b[c + 1] * py + b[c + 2] * pz + b[c + 3]) * u
            val r = (b[a + 4] * px + b[a + 5] * py + b[a + 6] * pz + b[a + 7]) * w + (b[c + 4] * px + b[c + 5] * py + b[c + 6] * pz + b[c + 7]) * u
            val up = (b[a + 8] * px + b[a + 9] * py + b[a + 10] * pz + b[a + 11]) * w + (b[c + 8] * px + b[c + 9] * py + b[c + 10] * pz + b[c + 11]) * u
            vx[v] = x + (fx * f + rx * r) * s; vy[v] = y + (fy * f + ry * r) * s; vz[v] = z + up * s
        }
        val prev = scene.material
        scene.material = Mat.PLAIN
        val t = m.tri
        for (i in 0 until m.triangles) {
            val role = m.roles[i].toInt()
            var col = when (role) {
                PeopleModels.SKIN -> skin
                PeopleModels.HAIR -> hair
                PeopleModels.TOP -> top
                PeopleModels.BOTTOM -> bottom
                else -> m.palette[role - 4]
            }
            if (alpha < 1f) col = scene.withAlpha(col, alpha)
            for (k in 0 until 3) { val v = t[i * 3 + k]; tx[k] = vx[v]; ty[k] = vy[v]; tz[k] = vz[v] }
            scene.poly(3, tx, ty, tz, col)
        }
        scene.material = prev
    }

    private fun proj() = scene.proj

    /** A box held at the chest, or a hand truck / flatbed cart pushed in front with its boxes stacked on it. */
    private fun carried(floor: Float, chest: Float, s: Float, carry: Int, load: Int) {
        when (carry) {
            1 -> part(.42f * s, 0f, chest + .18f * s, chest + .6f * s, .42f * s, .44f * s, CARD, last = false)
            2 -> {
                for (side in -1..1 step 2) {
                    part(.74f * s, side * .25f * s, floor, floor + .27f * s, .27f * s, .07f * s, TIRE)
                    part(.62f * s, side * .2f * s, floor + .05f * s, floor + 1.25f * s, .05f * s, .05f * s, TRUCK_RED)
                }
                part(.74f * s, 0f, floor + .1f * s, floor + .16f * s, .05f * s, .5f * s, STEEL)
                part(.6f * s, 0f, floor + 1.2f * s, floor + 1.27f * s, .07f * s, .5f * s, c(0xFF2E3238))
                part(.86f * s, 0f, floor, floor + .04f * s, .36f * s, .46f * s, STEEL)
                for (i in 0 until load.coerceAtMost(4)) {
                    val z0 = floor + .04f * s + i * .29f * s
                    part(.86f * s, 0f, z0, z0 + .27f * s, .34f * s, .4f * s, if (i % 2 == 0) CARD else CARD_DARK)
                }
            }
            3 -> {
                part(1.06f * s, 0f, floor + .2f * s, floor + .28f * s, 1.1f * s, .62f * s, c(0xFF5B6770))
                for (side in -1..1 step 2) {
                    part(.62f * s, side * .24f * s, floor, floor + .2f * s, .16f * s, .07f * s, TIRE)
                    part(1.5f * s, side * .24f * s, floor, floor + .2f * s, .16f * s, .07f * s, TIRE)
                    part(.5f * s, side * .27f * s, floor + .28f * s, floor + 1.12f * s, .05f * s, .05f * s, CART_BLUE)
                }
                part(.5f * s, 0f, floor + 1.08f * s, floor + 1.14f * s, .06f * s, .6f * s, CART_BLUE)
                for (i in 0 until load.coerceAtMost(8)) {
                    val layer = i / 4; val slot = i % 4
                    val f = if (slot < 2) .8f else 1.32f
                    val r = if (slot % 2 == 0) -.15f else .15f
                    val z0 = floor + .28f * s + layer * .31f * s
                    part(f * s, r * s, z0, z0 + .29f * s, .44f * s, .28f * s, if ((slot + layer) % 2 == 0) CARD else CARD_DARK)
                }
            }
        }
    }

    // ------------------------------------------------------------------ acting

    /**
     * A model's joints in its bind pose, worked out from where its skin blends between bones: each arm's shoulder,
     * elbow and a point in the hand, the neck and the waist. [right] and [left] are each arm's first bone (its shoulder
     * bone; the upper arm, forearm and wrist follow), whichever side of the body it's on.
     */
    private class Joints(val right: Int, val left: Int, val rShoulder: FloatArray, val rElbow: FloatArray, val lShoulder: FloatArray,
                         val lElbow: FloatArray, val lHand: FloatArray, val neck: FloatArray, val waist: FloatArray)

    private val joints = HashMap<PeopleModels.Model, Joints>()

    private fun jointsOf(m: PeopleModels.Model): Joints = joints.getOrPut(m) {
        // where bones a and b meet: the skin bound to both, weighted toward an even blend
        fun meet(a: Int, b: Int): FloatArray {
            var sf = 0f; var sr = 0f; var su = 0f; var sw = 0f
            for (v in 0 until m.vertices) {
                val b0 = m.b0[v].toInt() and 255; val b1 = m.b1[v].toInt() and 255
                if (!((b0 == a && b1 == b) || (b0 == b && b1 == a))) continue
                val w = minOf(m.w[v], 1f - m.w[v]) + .02f
                sf += m.pos[v * 3] * w; sr += m.pos[v * 3 + 1] * w; su += m.pos[v * 3 + 2] * w; sw += w
            }
            if (sw > 0f) return floatArrayOf(sf / sw, sr / sw, su / sw)
            // no blend: halfway between the two bones' skin
            fun centre(bone: Int): FloatArray {
                var f = 0f; var r = 0f; var u = 0f; var k = 0
                for (v in 0 until m.vertices) if ((m.b0[v].toInt() and 255) == bone) { f += m.pos[v * 3]; r += m.pos[v * 3 + 1]; u += m.pos[v * 3 + 2]; k++ }
                return if (k == 0) floatArrayOf(0f, 0f, 1.4f) else floatArrayOf(f / k, r / k, u / k)
            }
            val ca = centre(a); val cb = centre(b)
            return floatArrayOf((ca[0] + cb[0]) / 2, (ca[1] + cb[1]) / 2, (ca[2] + cb[2]) / 2)
        }
        fun hand(wrist: Int): FloatArray {
            var f = 0f; var r = 0f; var u = 0f; var k = 0
            for (v in 0 until m.vertices) if ((m.b0[v].toInt() and 255) == wrist && m.w[v] >= .5f) { f += m.pos[v * 3]; r += m.pos[v * 3 + 1]; u += m.pos[v * 3 + 2]; k++ }
            return if (k == 0) floatArrayOf(0f, 0f, .8f) else floatArrayOf(f / k, r / k, u / k)
        }
        // the arm whose hand is on the body's right (+r) is the right arm
        val handA = hand(11); val handB = hand(15)
        val right = if (handB[1] >= handA[1]) 12 else 8
        val left = if (right == 12) 8 else 12
        Joints(right, left, meet(right, right + 1), meet(right + 1, right + 2), meet(left, left + 1), meet(left + 1, left + 2),
            hand(left + 3), meet(5, 6), meet(2, 3))
    }

    private val rot = FloatArray(9)
    private val tmpRot = FloatArray(9)

    /** rot = (about up by [twist]) * (about forward by [abduct]) * (about right by [swing]), angles in degrees. */
    private fun rotation(swing: Float, abduct: Float, twist: Float) {
        val a = Math.toRadians(swing.toDouble()); val ca = kotlin.math.cos(a).toFloat(); val sa = kotlin.math.sin(a).toFloat()
        val c = Math.toRadians(abduct.toDouble()); val cc = kotlin.math.cos(c).toFloat(); val sc = kotlin.math.sin(c).toFloat()
        val b = Math.toRadians(twist.toDouble()); val cb = kotlin.math.cos(b).toFloat(); val sb = kotlin.math.sin(b).toFloat()
        // swing about right: f' = f cos - u sin, u' = f sin + u cos (a hanging arm swings forward)
        r1[0] = ca; r1[1] = 0f; r1[2] = -sa; r1[3] = 0f; r1[4] = 1f; r1[5] = 0f; r1[6] = sa; r1[7] = 0f; r1[8] = ca
        // abduct about forward: r' = r cos - u sin, u' = r sin + u cos (a hanging right arm rises out to the right)
        r2[0] = 1f; r2[1] = 0f; r2[2] = 0f; r2[3] = 0f; r2[4] = cc; r2[5] = -sc; r2[6] = 0f; r2[7] = sc; r2[8] = cc
        // twist about up: f' = f cos - r sin, r' = f sin + r cos
        r3[0] = cb; r3[1] = -sb; r3[2] = 0f; r3[3] = sb; r3[4] = cb; r3[5] = 0f; r3[6] = 0f; r3[7] = 0f; r3[8] = 1f
        mul(r2, r1, tmpRot); mul(r3, tmpRot, rot)
    }

    private val r1 = FloatArray(9); private val r2 = FloatArray(9); private val r3 = FloatArray(9)

    private fun mul(a: FloatArray, b: FloatArray, out: FloatArray) {
        for (i in 0 until 3) for (j in 0 until 3) out[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j]
    }

    /** Turns bones [from]..[to] (a limb and what hangs off it) by [rot] about the bind-pose joint [pivot] as bone [carrier] has it now. */
    private fun turnLimb(from: Int, to: Int, pivot: FloatArray, carrier: Int) {
        val b = bones; val o = carrier * 12
        val px = b[o] * pivot[0] + b[o + 1] * pivot[1] + b[o + 2] * pivot[2] + b[o + 3]
        val py = b[o + 4] * pivot[0] + b[o + 5] * pivot[1] + b[o + 6] * pivot[2] + b[o + 7]
        val pz = b[o + 8] * pivot[0] + b[o + 9] * pivot[1] + b[o + 10] * pivot[2] + b[o + 11]
        val r = rot
        for (bone in from..to) {
            val q = bone * 12
            for (col in 0 until 4) {
                val m0 = b[q + col]; val m1 = b[q + 4 + col]; val m2 = b[q + 8 + col]
                var n0 = r[0] * m0 + r[1] * m1 + r[2] * m2
                var n1 = r[3] * m0 + r[4] * m1 + r[5] * m2
                var n2 = r[6] * m0 + r[7] * m1 + r[8] * m2
                if (col == 3) {
                    // the translation turns about the pivot
                    n0 += px - (r[0] * px + r[1] * py + r[2] * pz)
                    n1 += py - (r[3] * px + r[4] * py + r[5] * pz)
                    n2 += pz - (r[6] * px + r[7] * py + r[8] * pz)
                }
                b[q + col] = n0; b[q + 4 + col] = n1; b[q + 8 + col] = n2
            }
        }
    }

    /**
     * One arm ([right] or not): the upper arm swung forward [swing] degrees, out to its side [abduct] and turned [twist]
     * about the shoulder, then the forearm bent forward [elbow] more at the elbow.
     */
    private fun arm(j: Joints, right: Boolean, swing: Float, abduct: Float, twist: Float, elbow: Float) {
        val first = if (right) j.right else j.left
        val sh = if (right) j.rShoulder else j.lShoulder
        val el = if (right) j.rElbow else j.lElbow
        if (first < 0) return
        rotation(swing, if (right) abduct else -abduct, twist)
        turnLimb(first + 1, first + 3, sh, first + 1)
        if (elbow != 0f) {
            rotation(elbow, 0f, 0f)
            turnLimb(first + 2, first + 3, el, first + 2)
        }
    }

    /** Leans the head forward [nod] degrees and turns it [turn]. */
    private fun head(j: Joints, nod: Float, turn: Float) { rotation(nod, 0f, turn); turnLimb(6, 7, j.neck, 6) }

    /** Turns everything above the waist [twist] degrees (sweeping a mop, packing a bag beside you). */
    private fun waist(j: Joints, twist: Float, lean: Float = 0f) { rotation(lean, 0f, twist); turnLimb(3, 15, j.waist, 3) }

    /** Acts [act] out on the bones just posed: the arms, head and waist moved for the job, [t] seconds into it. */
    private fun acting(m: PeopleModels.Model, act: Int, t: Float, pose: Int) {
        val j = jointsOf(m)
        fun wave(rate: Float, ph: Float = 0f) = sin(t * rate + ph)
        when (act) {
            Act.TILL -> {
                // both hands on the counter; every few seconds a burst of typing on the till
                val typing = (t % 5f) < 2f
                val tap = if (typing) wave(17f) * 5f else 0f
                arm(j, true, 32f, 6f, -14f, 50f + tap)
                arm(j, false, 26f, 6f, 10f, 48f - tap * .6f)
                head(j, 14f, if (typing) -8f else wave(.6f) * 18f)
            }
            Act.SCAN -> {
                // the right hand sweeps each item across the scanner, the left steadies the next
                arm(j, true, 38f, 4f, wave(7f) * 26f, 42f)
                arm(j, false, 30f, 8f, 12f, 44f)
                head(j, 18f, wave(7f) * 8f)
            }
            Act.BAG -> {
                waist(j, 22f)
                arm(j, true, 34f + wave(5f) * 14f, 4f, 0f, 36f)
                arm(j, false, 30f + wave(5f, 1.6f) * 12f, 4f, 0f, 40f)
                head(j, 22f, 10f)
            }
            Act.GIVE -> {
                arm(j, true, 68f, 2f, -6f, 12f)
                arm(j, false, 22f, 4f, 0f, 30f)
            }
            Act.PLACE -> {
                arm(j, true, 48f, 2f, -4f, 30f)
                head(j, 18f, 0f)
            }
            Act.CARD -> {
                arm(j, true, 52f, 0f, -10f, 26f + wave(9f) * 3f)
                head(j, 16f, -6f)
            }
            Act.TYPE -> {
                // fingers on the keys: the forearms forward, tapping by turns
                val seated = pose == Pose.SIT
                arm(j, true, if (seated) 10f else 30f, 4f, -10f, (if (seated) 28f else 52f) + wave(15f) * 4f)
                arm(j, false, if (seated) 10f else 28f, 4f, 10f, (if (seated) 28f else 50f) + wave(15f, 2f) * 4f)
                head(j, 10f, wave(.4f) * 6f)
            }
            Act.MOP -> {
                // both hands down the handle, sweeping side to side from the waist
                waist(j, wave(2.4f) * 24f, 12f)
                arm(j, true, 40f, 6f, -10f, 22f)
                arm(j, false, 30f, 10f, 14f, 40f)
                head(j, 20f, 0f)
            }
            Act.DJ -> {
                // one hand pressing the headphones on, the other scratching; nodding to the beat
                arm(j, false, 20f, 62f, 0f, 118f)
                arm(j, true, 34f, 8f, wave(10f) * 12f, 50f)
                head(j, 10f + wave(8f) * 9f, -6f)
            }
            Act.HAIR -> {
                // scissors snipping at head height over the seated client, a comb in the other hand
                arm(j, true, 72f + wave(1.3f) * 6f, 6f, -8f, 40f + wave(14f) * 4f)
                arm(j, false, 62f, 10f, 12f, 52f)
                head(j, 18f, wave(.5f) * 10f)
            }
            Act.COACH -> {
                // clipboard held up in the left hand; the right points out now and then
                arm(j, false, 38f, 8f, 18f, 66f)
                val point = (sin(t * 1.4f)).coerceAtLeast(0f)
                arm(j, true, 20f + point * 58f, 4f, -6f, 26f - point * 18f)
                head(j, 6f, wave(.7f) * 14f)
            }
            Act.PERFORM -> {
                arm(j, true, 24f, 100f + wave(1.7f) * 30f, 0f, 20f)
                arm(j, false, 50f + wave(1.3f, 1f) * 25f, 20f, 0f, 30f)
                head(j, -8f + wave(1.1f) * 10f, wave(.8f) * 20f)
            }
            Act.STOCK, Act.TIDY -> {
                // out of the box at their feet (or along the display) and onto the shelf, a hand at a time
                val k = (wave(3f) + 1f) * .5f
                waist(j, 0f, 10f * (1f - k))
                arm(j, true, 30f + k * 50f, 4f, -6f, 30f - k * 10f)
                arm(j, false, 30f + (1f - k) * 40f, 4f, 6f, 34f)
                head(j, 8f - k * 14f, 0f)
            }
            Act.REACH -> {
                arm(j, true, 82f, 6f, -4f, 10f)
                head(j, -10f, 0f)
            }
            Act.EAT -> {
                // a bite or a sip every few seconds: the hand up to the mouth
                val c = t % 4.5f
                val up = if (c < 1.4f) sin(c / 1.4f * Math.PI.toFloat()) else 0f
                arm(j, true, 16f + up * 22f, 8f, -16f * up, 40f + up * 75f)
                arm(j, false, 14f, 6f, 8f, 40f)
                head(j, 12f - up * 8f, 0f)
            }
            Act.PRESS -> {
                // lying-down bench press as seated: the bar pushed up and lowered
                val k = (wave(2.2f) + 1f) * .5f
                arm(j, true, 70f + k * 45f, 14f, 0f, 80f - k * 70f)
                arm(j, false, 70f + k * 45f, 14f, 0f, 80f - k * 70f)
            }
            Act.CURL -> {
                val k = (wave(2.6f) + 1f) * .5f
                val l = (wave(2.6f, Math.PI.toFloat()) + 1f) * .5f
                arm(j, true, 8f, 6f, 0f, 10f + k * 115f)
                arm(j, false, 8f, 6f, 0f, 10f + l * 115f)
                head(j, 6f, 0f)
            }
            Act.PUNCH -> {
                // a jab with each hand by turns, the guard up in between
                val c = (t * 2.2f) % 2f
                val r = if (c < 1f) sin(c * Math.PI.toFloat()) else 0f
                val l = if (c >= 1f) sin((c - 1f) * Math.PI.toFloat()) else 0f
                arm(j, true, 60f + r * 30f, 6f, -10f, 100f - r * 95f)
                arm(j, false, 60f + l * 30f, 6f, 10f, 100f - l * 95f)
                waist(j, (r - l) * 14f)
            }
            Act.ROW -> {
                val k = (wave(2f) + 1f) * .5f
                waist(j, 0f, 10f - k * 22f)
                arm(j, true, 72f - k * 40f, 6f, 0f, 10f + k * 70f)
                arm(j, false, 72f - k * 40f, 6f, 0f, 10f + k * 70f)
            }
            Act.COOK -> {
                // turning things on the griddle, and every few seconds lifting one off to hand across the cart
                val c = t % 7f
                val serve = if (c > 5.2f) sin((c - 5.2f) / 1.8f * PI_F) else 0f
                arm(j, true, 24f + wave(4.4f) * 6f + serve * 26f, 24f, -42f + wave(4.4f) * 12f, 12f + serve * 20f)
                arm(j, false, 20f, 18f, 36f, 30f)
                head(j, 24f - serve * 18f, wave(.45f) * 10f)
            }
            Act.PHONE -> {
                // the phone held in front of the chest, thumbs going, head bowed over it
                val tap = wave(11f) * 2.5f
                arm(j, true, 9f, 0f, -40f, 76f + tap)
                arm(j, false, 9f, 0f, 40f, 76f - tap)
                head(j, 28f, 0f)
            }
            Act.CALL -> {
                // the phone at the cheek; the free hand moves now and then as they talk
                arm(j, true, 32f, 20f, -34f, 128f)
                val g = sin(t * 1.3f).coerceAtLeast(0f)
                arm(j, false, 8f + g * 18f, 6f, 6f, 18f + g * 55f)
                head(j, 6f, -10f + wave(.7f) * 6f)
            }
            Act.TALK -> {
                // a hand going as they talk, the other at their side; nods and the odd glance away
                val g = sin(t * 1.7f) + sin(t * 2.9f + 1f) * .6f
                arm(j, true, 18f + g.coerceAtLeast(0f) * 22f, 10f, -12f, 48f + g * 22f)
                arm(j, false, 6f, 4f, 6f, 16f + (-g).coerceAtLeast(0f) * 30f)
                head(j, 4f + sin(t * 2.3f) * 5f, sin(t * .6f) * 12f)
            }
            Act.POINT -> {
                // chin in hand, looking along the window; now and then pointing at something in it
                val c = t % 6f
                val p = if (c > 2f && c < 4.2f) sin((c - 2f) / 2.2f * PI_F) else 0f
                arm(j, true, 18f + p * 58f, 6f, -4f, 24f - p * 18f)
                arm(j, false, 28f * (1f - p), 4f, 26f * (1f - p), 118f * (1f - p))
                head(j, 6f - p * 6f, sin(t * .5f) * 16f)
            }
            Act.LOOK -> {
                // a look one way, then the other, the shoulders following the head a little
                val turn = sin(t * 1.15f) * 58f
                head(j, -2f, turn)
                waist(j, turn * .22f)
            }
            Act.SNACK -> {
                // the snack held in front; a bite every few seconds, up to the mouth
                val c = t % 3.4f
                val up = if (c < 1.1f) sin(c / 1.1f * PI_F) else 0f
                arm(j, true, up * 15f, 0f, -24f - 16f * up, 52f + up * 84f)
                head(j, 8f - up * 6f, 0f)
            }
            Act.PAY -> {
                arm(j, true, 20f, 28f, -48f, 45f)
                arm(j, false, 14f, 4f, 0f, 30f)
                head(j, 10f, 0f)
            }
        }
    }

    /** Tests: the posed extent (min and max forward, right, up) of model [index] acting out [act] at [t] seconds. */
    internal fun debugExtent(index: Int, act: Int, t: Float, pose: Int = Pose.STAND): FloatArray {
        val m = PeopleModels.models[index]
        when (pose) {
            Pose.SIT -> PeopleModels.pose(m.rig, "Sit", 0f, "Sit", 0f, 0f, bones)
            else -> PeopleModels.pose(m.rig, "Idle", 0f, "Idle", 0f, 0f, bones)
        }
        if (act != Act.NONE) acting(m, act, t, pose)
        val out = floatArrayOf(9f, 9f, 9f, -9f, -9f, -9f)
        val p = m.pos; val b = bones
        for (v in 0 until m.vertices) {
            val px = p[v * 3]; val py = p[v * 3 + 1]; val pz = p[v * 3 + 2]
            val a = (m.b0[v].toInt() and 255) * 12; val c = (m.b1[v].toInt() and 255) * 12
            val w = m.w[v]; val u = 1 - w
            val q = floatArrayOf(
                (b[a] * px + b[a + 1] * py + b[a + 2] * pz + b[a + 3]) * w + (b[c] * px + b[c + 1] * py + b[c + 2] * pz + b[c + 3]) * u,
                (b[a + 4] * px + b[a + 5] * py + b[a + 6] * pz + b[a + 7]) * w + (b[c + 4] * px + b[c + 5] * py + b[c + 6] * pz + b[c + 7]) * u,
                (b[a + 8] * px + b[a + 9] * py + b[a + 10] * pz + b[a + 11]) * w + (b[c + 8] * px + b[c + 9] * py + b[c + 10] * pz + b[c + 11]) * u)
            for (k in 0 until 3) { out[k] = minOf(out[k], q[k]); out[k + 3] = maxOf(out[k + 3], q[k]) }
        }
        return out
    }

    /**
     * Tests: model [index] standing, its right arm posed with [arm]'s angles: where the right hand ends up (forward,
     * right, up) and where the neck is.
     */
    internal fun debugArm(index: Int, swing: Float, abduct: Float, twist: Float, elbow: Float): FloatArray {
        val m = PeopleModels.models[index]
        PeopleModels.pose(m.rig, "Idle", 0f, "Idle", 0f, 0f, bones)
        val j = jointsOf(m)
        arm(j, true, swing, abduct, twist, elbow)
        val wr = wristOf(m); val o = wr[0].toInt() * 12; val b = bones
        val hf = b[o] * wr[1] + b[o + 1] * wr[2] + b[o + 2] * wr[3] + b[o + 3]
        val hr = b[o + 4] * wr[1] + b[o + 5] * wr[2] + b[o + 6] * wr[3] + b[o + 7]
        val hu = b[o + 8] * wr[1] + b[o + 9] * wr[2] + b[o + 10] * wr[3] + b[o + 11]
        val n = j.neck; val q = 6 * 12
        val nf = b[q] * n[0] + b[q + 1] * n[1] + b[q + 2] * n[2] + b[q + 3]
        val nr = b[q + 4] * n[0] + b[q + 5] * n[1] + b[q + 6] * n[2] + b[q + 7]
        val nu = b[q + 8] * n[0] + b[q + 9] * n[1] + b[q + 10] * n[2] + b[q + 11]
        return floatArrayOf(hf, hr, hu, nf, nr, nu)
    }

    /** Tests: the joints worked out for model [index]. */
    internal fun debugJoints(index: Int): String = jointsOf(PeopleModels.models[index]).let { j ->
        "right ${j.right} left ${j.left} rShoulder ${j.rShoulder.joinToString { "%.2f".format(it) }} rElbow ${j.rElbow.joinToString { "%.2f".format(it) }} " +
            "lShoulder ${j.lShoulder.joinToString { "%.2f".format(it) }} neck ${j.neck.joinToString { "%.2f".format(it) }} waist ${j.waist.joinToString { "%.2f".format(it) }}"
    }

    /** The left hand, after the last [draw] (for what it holds). */
    var lHandX = 0f; var lHandY = 0f; var lHandZ = 0f
        private set

    private val sx = FloatArray(4); private val sy = FloatArray(4); private val sz = FloatArray(4)

    /** A thin pole between two points (a mop handle), [w] thick. */
    private fun stick(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, w: Float, color: Int) {
        val dx = x1 - x0; val dy = y1 - y0
        val l = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
        val px = -dy / l * w; val py = dx / l * w
        for (k in 0 until 2) {
            val ox = if (k == 0) px else 0f; val oy = if (k == 0) py else 0f; val oz = if (k == 0) 0f else w
            sx[0] = x0 - ox; sy[0] = y0 - oy; sz[0] = z0 - oz
            sx[1] = x0 + ox; sy[1] = y0 + oy; sz[1] = z0 + oz
            sx[2] = x1 + ox; sy[2] = y1 + oy; sz[2] = z1 + oz
            sx[3] = x1 - ox; sy[3] = y1 - oy; sz[3] = z1 - oz
            scene.poly(4, sx, sy, sz, color)
        }
    }

    private fun cube(x: Float, y: Float, z: Float, hx: Float, hy: Float, hz: Float, color: Int, top: Int = color) =
        scene.box(x - hx, y - hy, z - hz, x + hx, y + hy, z + hz, top, color)

    /** What the hands hold for [act]: a mop, scissors and a comb, a clipboard, a cup, dumbbells, a phone, a snack. */
    private fun props(act: Int, t: Float, x: Float, y: Float, z: Float, heading: Float, s: Float, item: Int) {
        val fx = sin(heading); val fy = -cos(heading)
        when (act) {
            Act.COOK -> {
                // the spatula: a handle out of the hand and a blade flat on the griddle
                val bx = handX + fx * .2f * s; val by = handY + fy * .2f * s
                stick(handX, handY, handZ, bx, by, handZ - .07f * s, .012f * s, c(0xFF2A2D31))
                cube(bx + fx * .04f * s, by + fy * .04f * s, handZ - .075f * s, .045f * s, .045f * s, .006f * s, c(0xFFC5CBD1))
            }
            Act.PHONE -> cube((handX + lHandX) * .5f, (handY + lHandY) * .5f, (handZ + lHandZ) * .5f + .03f * s, .035f * s, .035f * s, .008f * s,
                c(0xFF1C1E22), c(0xFF9FD3FF))
            Act.CALL -> cube(handX, handY, handZ + .04f * s, .012f * s, .012f * s, .06f * s, c(0xFF1C1E22))
            Act.PAY -> cube(handX + fx * .04f * s, handY + fy * .04f * s, handZ + .01f * s, .05f * s, .05f * s, .004f * s, c(0xFF4E8F4A), c(0xFF7CC576))
            Act.SNACK -> snack(item, handX, handY, handZ, fx, fy, s)
            Act.MOP -> {
                // the handle from the right hand down to the mop head on the floor ahead, swept with the body
                val sw = sin(t * 2.4f) * .35f * s
                val hx = x + fx * .62f * s - fy * sw; val hy = y + fy * .62f * s + fx * sw
                stick(handX, handY, handZ + .05f, hx, hy, z + .06f, .016f * s, c(0xFFB07A52))
                scene.box(hx - .17f * s, hy - .17f * s, z, hx + .17f * s, hy + .17f * s, z + .06f * s, c(0xFFE8E4D8), c(0xFFD0CBBE))
            }
            Act.HAIR -> {
                cube(handX, handY, handZ + .02f, .012f, .012f, .05f, c(0xFFD9DEE4))
                cube(lHandX, lHandY, lHandZ + .02f, .008f, .008f, .06f, c(0xFF2A2D31))
            }
            Act.COACH -> cube(lHandX, lHandY, lHandZ + .1f, .1f * s, .1f * s, .13f * s, c(0xFFB07A52), c(0xFFF4F1EA))
            Act.EAT -> cube(handX, handY, handZ + .03f, .035f, .035f, .05f, c(0xFFF4F1EA), c(0xFF5D3A1A))
            Act.CURL -> {
                cube(handX, handY, handZ, .05f, .05f, .05f, c(0xFF2A2D31))
                cube(lHandX, lHandY, lHandZ, .05f, .05f, .05f, c(0xFF2A2D31))
            }
            Act.PRESS -> stick(handX, handY, handZ, lHandX, lHandY, lHandZ, .02f, c(0xFFB8C0CA))
            Act.ROW -> stick(handX, handY, handZ, lHandX, lHandY, lHandZ, .018f, c(0xFF2A2D31))
            Act.STOCK -> {
                // the open box at their feet, off to the side
                val bx = x + fx * .3f * s + fy * .38f * s; val by = y + fy * .3f * s - fx * .38f * s
                scene.box(bx - .2f * s, by - .16f * s, z, bx + .2f * s, by + .16f * s, z + .3f * s, CARD_DARK, CARD)
            }
        }
    }

    /** A snack of kind [item] ([Snack]) in the hand at (hx, hy, hz), its top end toward the face. */
    private fun snack(item: Int, hx: Float, hy: Float, hz: Float, fx: Float, fy: Float, s: Float) {
        when (item) {
            Snack.SKEWER -> {
                // three balls up a stick
                stick(hx, hy, hz, hx + fx * .04f * s, hy + fy * .04f * s, hz + .24f * s, .004f * s, c(0xFFE8D9B0))
                for (k in 0 until 3) scene.blob(hx + fx * (.012f + k * .011f) * s, hy + fy * (.012f + k * .011f) * s, hz + (.1f + k * .045f) * s,
                    .022f * s, c(0xFFC98A45), 5)
            }
            Snack.HOT_DOG -> {
                val ex = fx * .07f * s; val ey = fy * .07f * s
                stick(hx - ex, hy - ey, hz + .03f * s, hx + ex, hy + ey, hz + .05f * s, .022f * s, c(0xFFE0B070))
                stick(hx - ex * 1.2f, hy - ey * 1.2f, hz + .055f * s, hx + ex * 1.2f, hy + ey * 1.2f, hz + .075f * s, .01f * s, c(0xFFB5523A))
            }
            Snack.CUP -> {
                scene.cylinder(hx, hy, .035f * s, hz - .02f * s, hz + .11f * s, 7, c(0xFFF4F1EA), c(0xFFE8D9B0))
                stick(hx, hy, hz + .11f * s, hx + fx * .01f * s, hy + fy * .01f * s, hz + .17f * s, .004f * s, c(0xFFD32F2F))
            }
            Snack.BOAT -> {
                cube(hx + fx * .03f * s, hy + fy * .03f * s, hz + .03f * s, .05f * s, .05f * s, .02f * s, c(0xFFF4F1EA), c(0xFFC98A45))
            }
            else -> {
                scene.blob(hx + fx * .02f * s, hy + fy * .02f * s, hz + .06f * s, .05f * s, c(0xFFC98A45), 6)
                cube(hx, hy, hz + .03f * s, .045f * s, .045f * s, .03f * s, c(0xFFF4F1EA))
            }
        }
    }

    /** A product, or the paper bag it went into at the register, hanging from the model's right hand (where [modelled] left it). */
    private fun inModelHand(z: Float, s: Float, held: Int, bag: Boolean) {
        val f = handLocal[0] * s; val r = handLocal[1] * s; val hz = z + handLocal[2] * s
        if (bag) {
            part(f, r + .03f * s, hz - .36f * s, hz - .06f * s, .24f * s, .11f * s, BAG, BAG_DARK)
            part(f, r + .03f * s, hz - .06f * s, hz + .01f * s, .14f * s, .015f * s, BAG_DARK)
        } else part(f, r + .02f * s, hz - .18f * s, hz + .02f * s, .1f * s, .075f * s, held)
    }

    /** Where the right hand is in the world, after the last [draw] (an estimate for the simple figures). */
    var handX = 0f; var handY = 0f; var handZ = 0f
        private set
    // the hand in the person's own frame (forward, right, up per meter of height); [3] > 0 once a model has set it
    private val handLocal = FloatArray(4)
    // per model, the right wrist's bone and a point in its hand in the bind pose
    private val wrists = HashMap<PeopleModels.Model, FloatArray>()

    private fun wristOf(m: PeopleModels.Model): FloatArray = wrists.getOrPut(m) {
        // the hand's vertices, for each wrist: the one on the right of the body (+r) is the right hand
        var best = floatArrayOf(-1f, 0f, 0f, 0f)
        for (bone in intArrayOf(11, 15)) {
            var sf = 0f; var sr = 0f; var su = 0f; var k = 0
            for (v in 0 until m.vertices) if ((m.b0[v].toInt() and 255) == bone && m.w[v] >= .5f) { sf += m.pos[v * 3]; sr += m.pos[v * 3 + 1]; su += m.pos[v * 3 + 2]; k++ }
            if (k > 0 && sr / k > 0f) best = floatArrayOf(bone.toFloat(), sf / k, sr / k, su / k)
        }
        best
    }

    /** A product picked off a shelf, or the paper bag it went into at the register, swinging in the right hand. */
    private fun inHand(hip: Float, s: Float, swing: Float, held: Int, bag: Boolean) {
        val f = swing * .16f * s
        if (bag) {
            part(f, .31f * s, hip - .3f * s, hip + .01f * s, .24f * s, .11f * s, BAG, BAG_DARK)
            part(f, .31f * s, hip + .01f * s, hip + .05f * s, .2f * s, .02f * s, BAG_DARK)
        } else part(f + .03f * s, .27f * s, hip - .12f * s, hip + .06f * s, .11f * s, .08f * s, held)
    }

    private fun legs(z: Float, s: Float, pants: Int, swing: Float) {
        part(swing, -.09f * s, z, z + .82f * s, .15f * s, .13f * s, pants)
        part(-swing, .09f * s, z, z + .82f * s, .15f * s, .13f * s, pants)
        // shoes
        part(swing + .03f * s, -.09f * s, z, z + .07f * s, .2f * s, .14f * s, c(0xFF1C1C1E))
        part(-swing + .03f * s, .09f * s, z, z + .07f * s, .2f * s, .14f * s, c(0xFF1C1C1E))
    }

    private fun upperBody(f: Float, z: Float, s: Float, shirt: Int, skin: Int, hair: Int, apron: Int, swing: Float, phase: Float, armsUp: Boolean, lod: Boolean = false, reach: Boolean = false) {
        part(f, 0f, z, z + .6f * s, .22f * s, .4f * s, shirt, if (apron != 0) apron else shirt)
        if (!lod) {
            if (reach) {
                // arms held out in front: holding a box or the handle of a hand truck or cart
                for (side in -1..1 step 2) {
                    part(f + .02f * s, side * .26f * s, z + .3f * s, z + .58f * s, .11f * s, .1f * s, shirt)
                    part(f + .2f * s, side * .25f * s, z + .28f * s, z + .38f * s, .34f * s, .09f * s, shirt)
                    part(f + .41f * s, side * .25f * s, z + .27f * s, z + .37f * s, .09f * s, .09f * s, skin)
                }
            } else if (armsUp) {
                val a = sin(phase * 1.6f) * .12f * s
                part(f, -.26f * s, z + .45f * s + a, z + 1.0f * s + a, .1f * s, .1f * s, shirt)
                part(f, .26f * s, z + .45f * s - a, z + 1.0f * s - a, .1f * s, .1f * s, shirt)
            } else {
                part(f - swing * .13f * s, -.26f * s, z + .06f * s, z + .58f * s, .11f * s, .1f * s, shirt)
                part(f + swing * .13f * s, .26f * s, z + .06f * s, z + .58f * s, .11f * s, .1f * s, shirt)
                // hands
                part(f - swing * .16f * s, -.26f * s, z, z + .08f * s, .09f * s, .09f * s, skin)
                part(f + swing * .16f * s, .26f * s, z, z + .08f * s, .09f * s, .09f * s, skin)
            }
        }
        part(f, 0f, z + .63f * s, z + .88f * s, .21f * s, .2f * s, skin)
        // hair: a cap over the top and back of the head, drawn after the head
        part(f - .02f * s, 0f, z + .8f * s, z + .93f * s, .24f * s, .23f * s, hair, last = true)
    }

    private fun emit(x: Float, y: Float, heading: Float, alpha: Float) {
        val fx = sin(heading); val fy = -cos(heading)
        val rx = -fy; val ry = fx
        val cx = proj().camX; val cy = proj().camY; val cz = proj().camZ
        for (i in 0 until n) {
            val px = x + fx * pf[i] + rx * pr[i]
            val py = y + fy * pf[i] + ry * pr[i]
            val pzc = (pz0[i] + pz1[i]) * .5f
            val dx = px - cx; val dy = py - cy; val dz = pzc - cz
            key[i] = if (plast[i]) -1f else dx * dx + dy * dy + dz * dz
            order[i] = i
        }
        // far first
        for (i in 1 until n) {
            val v = order[i]; val kv = key[v]
            var j = i - 1
            while (j >= 0 && key[order[j]] < kv) { order[j + 1] = order[j]; j-- }
            order[j + 1] = v
        }
        for (k in 0 until n) {
            val i = order[k]
            box(x + fx * pf[i] + rx * pr[i], y + fy * pf[i] + ry * pr[i], fx, fy, pl[i] * .5f, pw[i] * .5f, pz0[i], pz1[i], pc[i], pfront[i], alpha)
        }
    }

    /** Oriented box without allocations: visible sides plus the top. */
    private fun box(x: Float, y: Float, fx: Float, fy: Float, hl: Float, hw: Float, z0: Float, z1: Float, color: Int, front: Int, alpha: Float) {
        val rx = -fy; val ry = fx
        // corners: front-left, front-right, back-right, back-left
        qx[0] = x + fx * hl - rx * hw; qy[0] = y + fy * hl - ry * hw
        qx[1] = x + fx * hl + rx * hw; qy[1] = y + fy * hl + ry * hw
        qx[2] = x - fx * hl + rx * hw; qy[2] = y - fy * hl + ry * hw
        qx[3] = x - fx * hl - rx * hw; qy[3] = y - fy * hl - ry * hw
        val camX = proj().camX; val camY = proj().camY
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            val mx = (qx[i] + qx[j]) * .5f; val my = (qy[i] + qy[j]) * .5f
            var nx = mx - x; var ny = my - y
            val len = sqrt(nx * nx + ny * ny)
            if (len < 1e-5f) continue
            nx /= len; ny /= len
            if ((camX - mx) * nx + (camY - my) * ny <= 0f) continue
            val base = if (i == 0) front else color
            var col = scene.lit(base, scene.sideShade(nx, ny))
            if (alpha < 1f) col = scene.withAlpha(col, alpha)
            scene.quad(qx[i], qy[i], z0, qx[j], qy[j], z0, qx[j], qy[j], z1, qx[i], qy[i], z1, col)
        }
        if (proj().camZ > z1) {
            for (i in 0 until 4) { wx[i] = qx[i]; wy[i] = qy[i]; wz[i] = z1 }
            var col = scene.lit(color, 1.05f)
            if (alpha < 1f) col = scene.withAlpha(col, alpha)
            scene.poly(4, wx, wy, wz, col)
        }
    }
}
