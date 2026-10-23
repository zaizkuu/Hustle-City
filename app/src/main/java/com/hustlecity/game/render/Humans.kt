package com.hustlecity.game.render

import com.hustlecity.game.data.c
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * What a person looks like, decoded from one number (a pedestrian's or customer's seed): build and height, hairstyle,
 * what they wear and carry, and their age. The same seed always gives the same person; [warm] dresses them for the
 * cold (coats, scarves and hats instead of shorts and tank tops).
 */
class Look {
    /** 0: a broader, squarer frame; 1: a slighter one, more often in long hair, skirts and dresses. */
    var frame = 0
    /** 0 adult, 1 young, 2 senior. */
    var age = 0
    var height = 1f
    var build = 1f
    var hair = H_SHORT
    var hat = HAT_NONE
    var top = T_TEE
    var bottom = B_TROUSERS
    var coat = false
    var scarf = false
    var backpack = false
    var shoulderBag = false
    var glasses = false
    var beard = false
    var headphones = false
    var cane = false
    var accent = 0
    var accent2 = 0
    var shoes = 0
    var suit = 0
    var tie = 0
    var grayHair = 0
    /** Walking speed relative to an average adult (seniors amble, the young hurry). */
    var pace = 1f

    companion object {
        const val H_SHORT = 0; const val H_BUZZ = 1; const val H_LONG = 2; const val H_PONY = 3; const val H_BUN = 4
        const val H_CURLY = 5; const val H_BOB = 6; const val H_BALD = 7
        const val HAT_NONE = 0; const val HAT_CAP = 1; const val HAT_BEANIE = 2
        const val T_TEE = 0; const val T_LONG = 1; const val T_JACKET = 2; const val T_HOODIE = 3; const val T_SUIT = 4; const val T_TANK = 5; const val T_DRESS = 6
        const val B_TROUSERS = 0; const val B_SHORTS = 1; const val B_SKIRT = 2

        val COATS = intArrayOf(c(0xFF1F2A44), c(0xFF1B1D21), c(0xFFB08A5A), c(0xFF4E5A3A), c(0xFF6B2737), c(0xFF6C737C), c(0xFF3F5F86), c(0xFFC9B48E), c(0xFF2F4A3C), c(0xFF8C3B2F))
        val SUITS = intArrayOf(c(0xFF2E3238), c(0xFF1E2840), c(0xFF151619), c(0xFF4F555D), c(0xFF3A3328))
        val TIES = intArrayOf(c(0xFFB0303A), c(0xFF2F5FA8), c(0xFF6B2737), c(0xFF2F6B45), c(0xFFC9A227), c(0xFF5B3A8C))
        val SHOES = intArrayOf(c(0xFF1C1C1E), c(0xFFEDEDED), c(0xFF5A3B24), c(0xFF7B828A), c(0xFF1C1C1E), c(0xFFE8E2D6))
        val BAGS = intArrayOf(c(0xFF7A4B2A), c(0xFF1C1C1E), c(0xFFB0303A), c(0xFF2F80ED), c(0xFFE0C9A6), c(0xFF27AE60), c(0xFF9B51E0), c(0xFFF2994A))
        val GRAY = intArrayOf(c(0xFFB9B9B9), c(0xFFDADADA), c(0xFF8E8E8E), c(0xFFC8C2B8))

        private fun mix(x: Int): Int {
            var h = x * -0x61c88647
            h = h xor (h ushr 15); h *= 0x2c1b3c6d
            h = h xor (h ushr 12); h *= 0x297a2d39
            return h xor (h ushr 15)
        }
    }

    private var bits = 0
    private var used = 0

    /** A pseudo-random number in 0 until [n], drawn from the seed in turn. */
    private fun pick(n: Int): Int {
        if (used > 24) { bits = mix(bits); used = 0 }
        val v = ((bits ushr used) and 0xFF) * n / 256
        used += 8
        return v
    }

    private fun chance(pct: Int) = pick(100) < pct

    fun decode(seed: Int, warm: Boolean): Look {
        bits = mix(seed + 0x5bd1e995); used = 0
        frame = pick(2)
        age = pick(100).let { if (it < 12) 2 else if (it < 32) 1 else 0 }
        height = if (frame == 0) 1f + pick(9) * .008f else .93f + pick(9) * .008f
        if (age == 2) height -= .02f
        build = .92f + pick(8) * .03f
        pace = when (age) { 2 -> .72f; 1 -> 1.12f; else -> 1f }
        grayHair = if (age == 2) GRAY[pick(GRAY.size)] else 0
        hair = if (frame == 1) when (pick(100)) {
            in 0..33 -> H_LONG; in 34..53 -> H_PONY; in 54..65 -> H_BUN; in 66..80 -> H_BOB; in 81..92 -> H_CURLY; else -> H_SHORT
        } else when (pick(100)) {
            in 0..47 -> H_SHORT; in 48..62 -> H_BUZZ; in 63..75 -> H_CURLY; in 76..83 -> H_LONG; else -> if (age == 2) H_BALD else H_SHORT
        }
        accent = COATS[pick(COATS.size)]
        accent2 = BAGS[pick(BAGS.size)]
        shoes = SHOES[pick(SHOES.size)]
        suit = SUITS[pick(SUITS.size)]
        tie = TIES[pick(TIES.size)]
        val r = pick(100)
        top = when {
            frame == 1 && r < 12 -> T_DRESS
            age == 0 && r < 24 -> T_SUIT
            r < 40 -> T_JACKET
            r < 52 + (if (age == 1) 14 else 0) -> T_HOODIE
            r < 66 -> T_LONG
            r < 72 && !warm -> T_TANK
            else -> T_TEE
        }
        bottom = when {
            top == T_DRESS || top == T_SUIT -> B_TROUSERS
            frame == 1 && chance(26) -> B_SKIRT
            !warm && chance(14) -> B_SHORTS
            else -> B_TROUSERS
        }
        if (warm) {
            if (top == T_TEE || top == T_TANK || top == T_LONG) top = if (chance(50)) T_JACKET else T_HOODIE
            if (bottom == B_SHORTS) bottom = B_TROUSERS
            coat = chance(45)
            scarf = chance(40)
        } else { coat = false; scarf = false }
        hat = when {
            warm && chance(30) -> HAT_BEANIE
            age != 2 && hair != H_BUN && hair != H_PONY && chance(10) -> HAT_CAP
            else -> HAT_NONE
        }
        backpack = chance(if (age == 1) 42 else 12)
        shoulderBag = !backpack && chance(if (frame == 1) 34 else 10)
        glasses = chance(if (age == 2) 55 else 16)
        beard = frame == 0 && age != 1 && chance(26)
        headphones = hat == HAT_NONE && hair != H_LONG && chance(if (age == 1) 22 else 5)
        cane = age == 2 && chance(35)
        return this
    }
}

/**
 * Draws people for the GPU renderer as smooth, jointed figures: a skeleton posed for walking, running, sitting or
 * dancing, dressed in tapered limbs and rounded shapes with faces, hairstyles, clothes and things to carry. Distant
 * people use fewer, coarser parts. Same parameters as [PersonPainter.draw], plus a [look] seed and an [activity]
 * (1 looking at a phone).
 */
class HumanPainter(private val scene: Scene3D) {
    private val look = Look()

    // body frame: forward (fx, fy) and right (rx, ry) on the ground
    private var ox = 0f; private var oy = 0f; private var oz = 0f
    private var fx = 0f; private var fy = 0f; private var rx = 0f; private var ry = 0f
    private var segs = 6
    private var det = 1
    private var alpha = 1f
    private var s = 1f

    private fun wx(f: Float, r: Float) = ox + fx * f + rx * r
    private fun wy(f: Float, r: Float) = oy + fy * f + ry * r

    private fun col(c: Int) = if (alpha < 1f) scene.withAlpha(c, alpha) else c

    /** A limb or torso piece between two body-space points (forward, right, up), radii tapering from [ra] to [rb]. */
    private fun limb(f0: Float, r0: Float, z0: Float, ra: Float, f1: Float, r1: Float, z1: Float, rb: Float, color: Int, mat: Int, flat: Float = 1f, sideRight: Boolean = true, caps: Boolean = false) {
        val sxv = if (sideRight) rx else fx; val syv = if (sideRight) ry else fy
        scene.tube(wx(f0 * s, r0 * s), wy(f0 * s, r0 * s), oz + z0 * s, ra * s, wx(f1 * s, r1 * s), wy(f1 * s, r1 * s), oz + z1 * s, rb * s,
            segs, col(color), mat, flat, sxv, syv, 0f, caps)
    }

    /** An ellipsoid in body space: [rf] forward, [rr] to the right, [ru] up; [pitch] tips it forward. */
    private fun blob(f: Float, r: Float, z: Float, rf: Float, rr: Float, ru: Float, color: Int, mat: Int, detail: Int = det, pitch: Float = 0f, yaw: Float = 0f) {
        var afx = fx; var afy = fy; var arx = rx; var ary = ry
        if (yaw != 0f) {
            val cy = cos(yaw); val sy = sin(yaw)
            afx = fx * cy + rx * sy; afy = fy * cy + ry * sy
            arx = rx * cy - fx * sy; ary = ry * cy - fy * sy
        }
        val cp = cos(pitch); val sp = sin(pitch)
        // forward axis tipped down by the pitch (right axis stays level)
        scene.ellipsoid(wx(f * s, r * s), wy(f * s, r * s), oz + z * s, afx * cp, afy * cp, -sp, rf * s, arx, ary, 0f, rr * s, ru * s, col(color), mat, detail)
    }

    // joints of the posed skeleton, in body space (forward, up) per side: 0 left, 1 right
    private val hipF = FloatArray(2); private val hipZ = FloatArray(2)
    private val kneeF = FloatArray(2); private val kneeZ = FloatArray(2)
    private val ankleF = FloatArray(2); private val ankleZ = FloatArray(2)
    private val shF = FloatArray(2); private val shZ = FloatArray(2)
    private val elF = FloatArray(2); private val elZ = FloatArray(2); private val elR = FloatArray(2)
    private val haF = FloatArray(2); private val haZ = FloatArray(2); private val haR = FloatArray(2)

    fun draw(
        x: Float, y: Float, z: Float, heading: Float, phase: Float, moving: Float,
        shirt: Int, pants: Int, skin: Int, hair: Int, scale: Float = 1f, pose: Int = Pose.STAND,
        apron: Int = 0, umbrella: Int = 0, alpha: Float = 1f, carry: Int = 0, load: Int = 0, held: Int = 0, bag: Boolean = false,
        lookSeed: Int = 0, warm: Boolean = false, activity: Int = 0,
    ) {
        val lk = look.decode(lookSeed, warm)
        s = scale * lk.height
        this.alpha = alpha
        ox = x; oy = y; oz = z
        fx = sin(heading); fy = -cos(heading)
        rx = -fy; ry = fx
        // how big they are on screen sets the level of detail
        val camD = scene.proj.camZOf(x, y, z + .9f * s)
        if (camD < -2f) return
        val px = if (camD > 0f) scene.proj.focal * 1.75f * s / camD else 999f
        val lod = when { px < 26f -> 0; px < 90f -> 1; else -> 2 }
        segs = when (lod) { 0 -> 4; 1 -> 7; else -> 12 }
        det = when (lod) { 0 -> 0; 1 -> 1; else -> 2 }

        val w = lk.build * (if (lk.frame == 1) .9f else 1f)
        val hipW = if (lk.frame == 1) .092f else .085f
        val shW = .19f * w
        val hairCol = if (lk.grayHair != 0) lk.grayHair else hair
        val topCol = when (lk.top) { Look.T_SUIT -> lk.suit; Look.T_JACKET -> lk.accent; else -> shirt }
        val legCol = if (lk.top == Look.T_SUIT) lk.suit else pants
        val shoe = if (lk.top == Look.T_SUIT) c(0xFF1C1C1E) else lk.shoes
        val skinShade = scene.mix(skin, 0xFF000000.toInt(), .1f)

        // ---------------------------------------------------------------- pose the skeleton
        val sitting = pose == Pose.SIT
        val reach = carry >= 2 || (carry == 1 && load > 0)
        val running = pose == Pose.RUN || (pose == Pose.STAND && moving > 1.5f)
        val amp = if (running) 1f else moving.coerceIn(0f, 1f)
        var lean = if (running) .14f else 0f
        val thighA = if (running) .62f else .42f
        val dancing = pose == Pose.DANCE
        var pelvisZ: Float
        if (sitting) {
            // seat height z: thighs forward along the seat, shins down to the floor
            oz = 0f
            pelvisZ = z / s + .06f
            for (k in 0..1) {
                hipF[k] = 0f; hipZ[k] = pelvisZ
                kneeF[k] = .42f; kneeZ[k] = pelvisZ + .02f
                ankleF[k] = .46f; ankleZ[k] = .07f
            }
        } else {
            val bounce = if (dancing) abs(sin(phase * 1.6f)) * .07f else 0f
            var lowest = 9f
            for (k in 0..1) {
                val ph = phase + (if (k == 0) 0f else 3.14159f)
                var th: Float; var kn: Float
                if (dancing) {
                    th = sin(phase * 1.6f + k * 3.14159f) * .12f
                    kn = .25f + .2f * abs(sin(phase * 1.6f))
                } else {
                    th = thighA * amp * sin(ph)
                    kn = amp * ((if (running) .25f else .1f) + (if (running) 1.05f else .55f) * max(0f, cos(ph)))
                }
                hipF[k] = 0f; hipZ[k] = 0f
                kneeF[k] = sin(th) * .42f; kneeZ[k] = -cos(th) * .42f
                ankleF[k] = kneeF[k] + sin(th - kn) * .4f; ankleZ[k] = kneeZ[k] - cos(th - kn) * .4f
                lowest = min(lowest, ankleZ[k])
            }
            // the standing foot is on the ground: the hips ride on it (a natural bob)
            pelvisZ = .07f - lowest + bounce
            for (k in 0..1) { hipZ[k] += pelvisZ; kneeZ[k] += pelvisZ; ankleZ[k] += pelvisZ }
        }
        val waistZ = pelvisZ + .16f
        val chestZ = pelvisZ + .4f
        val shoulderZ = pelvisZ + .52f
        val neckZ = shoulderZ + .03f
        var headF = if (lk.age == 2) .03f else 0f
        var headZ = shoulderZ + .19f
        if (activity == 1) { headF += .025f; headZ -= .015f }
        // a forward lean tips everything above the hips
        fun lf(zAbove: Float) = lean * (zAbove - pelvisZ)
        if (lk.age == 2 && !sitting) lean += .05f

        // arms: swing opposite the legs, bend at the elbow
        for (k in 0..1) {
            val side = if (k == 0) -1f else 1f
            shF[k] = lf(shoulderZ); shZ[k] = shoulderZ - .02f
            var ua: Float; var fa: Float; var spread = .02f
            when {
                reach -> { ua = 1.15f; fa = 1.5f }
                sitting -> { ua = .35f; fa = 1.25f }
                dancing -> {
                    val wave = sin(phase * 1.6f + k * 3.14159f)
                    ua = 2.4f + wave * .35f; fa = 2.7f + wave * .3f; spread = .1f
                }
                activity == 1 && k == 1 -> { ua = .25f; fa = 1.95f }
                umbrella != 0 && k == 1 -> { ua = .55f; fa = 1.9f }
                lk.cane && k == 1 && !running -> { ua = .22f; fa = .55f }
                else -> {
                    val sw = -sin(phase + (if (k == 0) 0f else 3.14159f)) * (if (running) .75f else .38f) * amp
                    ua = sw
                    fa = sw + (if (running) 1.45f else .18f + .28f * amp * max(0f, sw / .38f))
                }
            }
            elF[k] = shF[k] + sin(ua) * .29f; elZ[k] = shZ[k] - cos(ua) * .29f; elR[k] = side * (shW + spread)
            haF[k] = elF[k] + sin(fa) * .26f; haZ[k] = elZ[k] - cos(fa) * .26f; haR[k] = side * (shW + spread + .015f)
        }
        if (reach) for (k in 0..1) { haR[k] = (if (k == 0) -1f else 1f) * .2f; elR[k] = (if (k == 0) -1f else 1f) * (shW + .03f) }

        // ---------------------------------------------------------------- legs and shoes
        val skirtish = lk.bottom == Look.B_SKIRT || lk.top == Look.T_DRESS
        val bareLegs = skirtish && !warm
        val legUpper = if (skirtish) (if (warm) c(0xFF2A2A30) else skin) else legCol
        val legLower = when {
            skirtish -> if (warm) c(0xFF2A2A30) else skin
            lk.bottom == Look.B_SHORTS -> skin
            else -> legCol
        }
        val legMat = if (bareLegs || lk.bottom == Look.B_SHORTS) Mat.SKIN else Mat.FABRIC
        for (k in 0..1) {
            val r = (if (k == 0) -1f else 1f) * hipW * w
            if (lod == 0) {
                limb(hipF[k], r, hipZ[k], .085f, ankleF[k], r, ankleZ[k], .055f, legUpper, Mat.FABRIC)
                continue
            }
            if (lk.bottom == Look.B_SHORTS) {
                val mf = (hipF[k] + kneeF[k]) * .5f; val mz = (hipZ[k] + kneeZ[k]) * .5f
                limb(hipF[k], r, hipZ[k], .095f, mf, r, mz, .082f, legCol, Mat.FABRIC)
                limb(mf, r, mz, .07f, kneeF[k], r, kneeZ[k], .06f, skin, Mat.SKIN)
            } else limb(hipF[k], r, hipZ[k], .092f, kneeF[k], r, kneeZ[k], .064f, legUpper, if (skirtish && bareLegs) Mat.SKIN else Mat.FABRIC)
            blob(kneeF[k], r, kneeZ[k], .063f, .063f, .063f, legLower, legMat, 0)
            limb(kneeF[k], r, kneeZ[k], .062f, ankleF[k], r, ankleZ[k], .043f, legLower, legMat)
            // a shoe pointing along the foot
            blob(ankleF[k] + .05f, r, ankleZ[k] - .035f, .12f, .05f, .042f, shoe, Mat.PLAIN, if (lod == 2) 1 else 0)
            if (lod == 2 && shoe != c(0xFF1C1C1E)) blob(ankleF[k] + .05f, r, ankleZ[k] - .068f, .124f, .053f, .012f, c(0xFFE9E9E9), Mat.PLAIN, 0)
        }

        // ---------------------------------------------------------------- torso
        val torsoMat = Mat.FABRIC
        val chestR = .18f * w; val waistR = .155f * w; val hipR = (if (lk.frame == 1) .165f else .15f) * lk.build
        // hips and seat in the trousers' (or skirt's) color
        val hipsCol = if (lk.top == Look.T_DRESS) shirt else if (skirtish) pants else legCol
        limb(lf(pelvisZ - .08f), 0f, pelvisZ - .08f, hipR * .95f, lf(waistZ), 0f, waistZ, waistR, hipsCol, Mat.FABRIC, .68f)
        val tucked = lk.top == Look.T_LONG || lk.top == Look.T_SUIT
        if (lod > 0 && tucked && !skirtish)
            limb(lf(waistZ - .02f), 0f, waistZ - .02f, waistR * 1.03f, lf(waistZ + .01f), 0f, waistZ + .01f, waistR * 1.03f, c(0xFF2B2420), Mat.PLAIN, .68f)
        // an untucked top hangs over the hips
        val hem = if (tucked || skirtish || lk.top == Look.T_DRESS) waistZ else pelvisZ + .06f
        limb(lf(hem), 0f, hem, waistR * (if (tucked) 1.0f else 1.06f), lf(chestZ), 0f, chestZ, chestR, topCol, torsoMat, .66f)
        limb(lf(chestZ), 0f, chestZ, chestR, lf(shoulderZ - .03f), 0f, shoulderZ - .03f, chestR * .92f, topCol, torsoMat, .64f)
        // rounded shoulders across the top
        blob(lf(shoulderZ - .035f), 0f, shoulderZ - .035f, .105f * w, shW + .015f, .06f, topCol, torsoMat, if (lod == 2) 3 else det)
        if (lk.top == Look.T_TANK) for (k in 0..1) blob(shF[k], (if (k == 0) -1f else 1f) * shW, shZ[k], .055f, .055f, .05f, skin, Mat.SKIN, 0)

        // skirts, dresses and long coats flare out over the legs
        if (skirtish) {
            val sc = if (lk.top == Look.T_DRESS) shirt else pants
            limb(lf(waistZ), 0f, waistZ, waistR * 1.05f, .02f, 0f, pelvisZ - .36f, .23f * lk.build, sc, Mat.FABRIC, .82f, caps = true)
        }
        if (lk.coat && !sitting) limb(lf(chestZ), 0f, chestZ, chestR * 1.06f, .02f, 0f, pelvisZ - .4f, .22f * lk.build, lk.accent, Mat.FABRIC, .72f, caps = true)

        if (lod > 0) {
            // what shows at the front: an open jacket's shirt, a suit's shirt and tie, a staff apron
            val front = lf(chestZ) + chestR * .64f
            when (lk.top) {
                Look.T_JACKET -> blob(front - .01f, 0f, chestZ - .02f, .025f, .05f, .2f, shirt, Mat.FABRIC)
                Look.T_SUIT -> {
                    blob(front - .01f, 0f, chestZ + .03f, .025f, .045f, .15f, c(0xFFF2F2F0), Mat.FABRIC)
                    blob(front + .008f, 0f, chestZ - .01f, .012f, .022f, .13f, lk.tie, Mat.FABRIC, 0)
                }
                Look.T_HOODIE -> blob(lf(neckZ) - .1f, 0f, neckZ - .02f, .07f, .13f, .07f, topCol, Mat.FABRIC)
            }
            if (apron != 0) blob(front, 0f, waistZ - .06f, .03f, .15f * w, .3f, apron, Mat.FABRIC)
            if (lk.backpack && !sitting) {
                blob(lf(chestZ) - .2f * w, 0f, chestZ - .04f, .085f, .15f, .2f, lk.accent2, Mat.FABRIC)
                if (lod == 2) blob(lf(chestZ) - .27f * w, 0f, chestZ - .1f, .03f, .11f, .08f, scene.mix(lk.accent2, 0xFF000000.toInt(), .2f), Mat.FABRIC, 1)
            }
            if (lk.shoulderBag && !reach && !sitting) {
                limb(lf(shoulderZ), -shW * .7f, shoulderZ + .01f, .012f, .04f, shW + .03f, pelvisZ - .02f, .012f, scene.mix(lk.accent2, 0xFF000000.toInt(), .3f), Mat.PLAIN)
                blob(.03f, shW + .05f, pelvisZ - .06f, .045f, .11f, .085f, lk.accent2, Mat.PLAIN, 0)
            }
            if (lk.scarf) {
                limb(lf(neckZ), 0f, neckZ - .02f, .075f, lf(neckZ + .05f), 0f, neckZ + .05f, .07f, lk.accent2, Mat.FABRIC)
                limb(lf(neckZ) + .07f, .04f, neckZ, .025f, lf(chestZ) + .12f, .05f, chestZ - .06f, .022f, lk.accent2, Mat.FABRIC)
            }
        }

        // ---------------------------------------------------------------- arms and hands
        val sleeveCol = topCol
        for (k in 0..1) {
            val sr = (if (k == 0) -1f else 1f) * shW
            if (lod == 0) {
                limb(shF[k], sr, shZ[k], .055f, haF[k], haR[k], haZ[k], .042f, sleeveCol, Mat.FABRIC)
                continue
            }
            val upper = if (lk.top == Look.T_TANK || (lk.top == Look.T_DRESS && !warm)) skin else sleeveCol
            val lower = when (lk.top) { Look.T_TEE, Look.T_TANK, Look.T_DRESS -> if (warm) sleeveCol else skin; else -> sleeveCol }
            if (lk.top == Look.T_TEE && !warm) {
                // short sleeves: the shirt ends halfway down the upper arm
                val mf = (shF[k] + elF[k]) * .5f; val mz = (shZ[k] + elZ[k]) * .5f; val mr = (sr + elR[k]) * .5f
                limb(shF[k], sr, shZ[k], .066f, mf, mr, mz, .06f, sleeveCol, Mat.FABRIC, caps = true)
                limb(mf, mr, mz, .05f, elF[k], elR[k], elZ[k], .044f, skin, Mat.SKIN)
            } else limb(shF[k], sr, shZ[k], .06f, elF[k], elR[k], elZ[k], .049f, upper, if (upper == skin) Mat.SKIN else Mat.FABRIC)
            blob(elF[k], elR[k], elZ[k], .047f, .047f, .047f, lower, if (lower == skin) Mat.SKIN else Mat.FABRIC, 0)
            limb(elF[k], elR[k], elZ[k], .047f, haF[k], haR[k], haZ[k], .037f, lower, if (lower == skin) Mat.SKIN else Mat.FABRIC)
            blob(haF[k] + .02f, haR[k], haZ[k] - .03f, .048f, .034f, .058f, skin, Mat.SKIN, 0)
        }

        // ---------------------------------------------------------------- neck and head
        val hf = lf(headZ) + headF
        limb(lf(neckZ) + headF * .5f, 0f, neckZ - .02f, .052f, hf - .005f, 0f, headZ - .07f, .048f, skin, Mat.SKIN)
        blob(hf, 0f, headZ, .105f, .088f, .12f, skin, Mat.SKIN, if (lod == 2) 3 else det)
        if (lod == 2) {
            // ears, nose, eyes, brows and mouth
            for (k in 0..1) blob(hf - .01f, (if (k == 0) -1f else 1f) * .087f, headZ - .005f, .022f, .012f, .032f, skinShade, Mat.SKIN, 0)
            blob(hf + .098f, 0f, headZ - .012f, .016f, .013f, .022f, skinShade, Mat.SKIN, 1)
            for (k in 0..1) {
                val r = (if (k == 0) -1f else 1f) * .035f
                blob(hf + .096f, r, headZ + .016f, .009f, .013f, .009f, c(0xFFF4F1EC), Mat.PLAIN, 1)
                blob(hf + .102f, r, headZ + .016f, .005f, .0075f, .0075f, c(0xFF2A2018), Mat.PLAIN, 1)
                blob(hf + .095f, r * 1.05f, headZ + .041f, .006f, .021f, .0045f, scene.mix(hairCol, 0xFF000000.toInt(), .2f), Mat.HAIR, 1)
            }
            blob(hf + .091f, 0f, headZ - .055f, .008f, .027f, .007f, scene.mix(skin, c(0xFF8C3A3A), .45f), Mat.SKIN, 0)
            if (lk.glasses) {
                // tinted lenses under a dark rim, and the bridge between them
                for (k in 0..1) {
                    val r = (if (k == 0) -1f else 1f) * .036f
                    blob(hf + .112f, r, headZ + .016f, .004f, .028f, .02f, c(0x6690A4B4), Mat.GLASS, 0)
                    blob(hf + .112f, r, headZ + .036f, .006f, .03f, .0045f, c(0xFF1E1F22), Mat.PLAIN, 0)
                }
                limb(hf + .113f, -.01f, headZ + .025f, .004f, hf + .113f, .01f, headZ + .025f, .004f, c(0xFF1E1F22), Mat.PLAIN)
            }
        } else if (lod == 1 && lk.glasses) blob(hf + .1f, 0f, headZ + .018f, .01f, .07f, .016f, c(0xFF1E1F22), Mat.PLAIN, 0)
        if (lk.beard && lod > 0) blob(hf + .05f, 0f, headZ - .065f, .065f, .078f, .05f, hairCol, Mat.HAIR, 0)
        hairdo(lk, hf, headZ, hairCol, lod)
        if (lk.headphones && lod > 0) {
            for (k in 0..1) blob(hf - .005f, (if (k == 0) -1f else 1f) * .098f, headZ, .035f, .02f, .038f, c(0xFF1E1F22), Mat.PLAIN, 0)
            // the band arcs over the hair
            val bf = hf - .01f
            limb(bf, -.1f, headZ + .03f, .009f, bf, -.085f, headZ + .095f, .009f, c(0xFF1E1F22), Mat.PLAIN)
            limb(bf, -.085f, headZ + .095f, .009f, bf, -.05f, headZ + .128f, .009f, c(0xFF1E1F22), Mat.PLAIN)
            limb(bf, -.05f, headZ + .128f, .009f, bf, .05f, headZ + .128f, .009f, c(0xFF1E1F22), Mat.PLAIN)
            limb(bf, .05f, headZ + .128f, .009f, bf, .085f, headZ + .095f, .009f, c(0xFF1E1F22), Mat.PLAIN)
            limb(bf, .085f, headZ + .095f, .009f, bf, .1f, headZ + .03f, .009f, c(0xFF1E1F22), Mat.PLAIN)
        }

        // ---------------------------------------------------------------- things in hand
        val hr = haR[1]; val hfr = haF[1] + .02f; val hz = haZ[1]
        if (activity == 1 && lod > 0) blob(hfr + .04f, hr - .02f, hz + .03f, .008f, .035f, .07f, c(0xFF15171B), Mat.PLAIN, 0, pitch = -.6f)
        if (lk.cane && !sitting && !running && !reach) limb(hfr + .03f, hr, hz, .012f, hfr + .08f, hr + .02f, .0f - oz / s, .012f, c(0xFF4A3424), Mat.WOOD)
        if (!reach && !sitting && (held != 0 || bag) && lod > 0) {
            val lh = haR[1]; val lfh = haF[1]; val lz = haZ[1]
            if (bag) {
                blob(lfh, lh + .02f, lz - .17f, .07f, .045f, .14f, c(0xFFD9B98A), Mat.PLAIN, 0)
            } else blob(lfh + .03f, lh, lz - .05f, .045f, .035f, .07f, held, Mat.PLAIN, 0)
        }
        if (umbrella != 0 && !sitting) {
            val top = 2.08f
            limb(hfr, hr, hz, .01f, hfr - .02f, hr - .08f, top, .01f, c(0xFF2B2B2B), Mat.PLAIN)
            blob(hfr - .02f, hr - .08f, top - .02f, .56f, .56f, .17f, umbrella, Mat.FABRIC, if (lod == 2) 2 else 1)
        }
        if (reach) carried(carry, load)
    }

    private fun hairdo(lk: Look, hf: Float, hz: Float, color: Int, lod: Int) {
        val d = if (lod == 2) 3 else det
        when (lk.hat) {
            Look.HAT_CAP -> {
                blob(hf - .005f, 0f, hz + .065f, .112f, .098f, .072f, lk.accent2, Mat.FABRIC, d)
                blob(hf + .11f, 0f, hz + .052f, .075f, .085f, .012f, lk.accent2, Mat.FABRIC, 0)
                if (lk.hair == Look.H_LONG || lk.hair == Look.H_BOB) limb(hf - .07f, 0f, hz + .02f, .095f, hf - .075f, 0f, hz - .2f, .08f, color, Mat.HAIR, .45f)
                return
            }
            Look.HAT_BEANIE -> {
                blob(hf - .008f, 0f, hz + .055f, .117f, .103f, .1f, lk.accent2, Mat.FABRIC, d)
                if (lod > 0) limb(hf - .008f, 0f, hz + .005f, .118f, hf - .008f, 0f, hz + .04f, .118f, scene.mix(lk.accent2, 0xFF000000.toInt(), .15f), Mat.FABRIC, .9f, sideRight = true)
                if (lk.hair == Look.H_LONG) limb(hf - .07f, 0f, hz, .095f, hf - .075f, 0f, hz - .2f, .08f, color, Mat.HAIR, .45f)
                return
            }
        }
        when (lk.hair) {
            Look.H_BALD -> if (lod > 0) blob(hf - .045f, 0f, hz - .01f, .07f, .093f, .06f, color, Mat.HAIR, 0)
            Look.H_BUZZ -> blob(hf - .008f, 0f, hz + .022f, .109f, .093f, .108f, color, Mat.HAIR, d)
            Look.H_CURLY -> blob(hf - .01f, 0f, hz + .05f, .128f, .118f, .112f, color, Mat.HAIR, d)
            else -> {
                // a cap of hair over the crown and the back of the head
                blob(hf - .016f, 0f, hz + .036f, .114f, .097f, .1f, color, Mat.HAIR, d)
                if (lod == 0) return
                when (lk.hair) {
                    Look.H_LONG -> {
                        limb(hf - .06f, 0f, hz + .03f, .1f, hf - .07f, 0f, hz - .24f, .085f, color, Mat.HAIR, .48f)
                        for (k in 0..1) limb(hf + .02f, (if (k == 0) -1f else 1f) * .085f, hz + .02f, .03f, hf - .01f, (if (k == 0) -1f else 1f) * .09f, hz - .14f, .025f, color, Mat.HAIR)
                    }
                    Look.H_BOB -> limb(hf - .03f, 0f, hz + .02f, .105f, hf - .035f, 0f, hz - .085f, .105f, color, Mat.HAIR, .92f)
                    Look.H_PONY -> {
                        blob(hf - .125f, 0f, hz + .02f, .045f, .04f, .05f, color, Mat.HAIR, 0)
                        limb(hf - .14f, 0f, hz, .035f, hf - .17f, 0f, hz - .17f, .018f, color, Mat.HAIR)
                    }
                    Look.H_BUN -> blob(hf - .07f, 0f, hz + .125f, .055f, .055f, .05f, color, Mat.HAIR, 0)
                }
            }
        }
    }

    /** A box in both arms, or a hand truck or flatbed cart pushed in front, with its boxes. */
    private fun carried(carry: Int, load: Int) {
        val card = c(0xFFC8A165); val cardDark = c(0xFFB38A55)
        when (carry) {
            1 -> box(.42f, 0f, haZ[1] - .05f, haZ[1] + .37f, .42f, .44f, card)
            2 -> {
                for (side in -1..1 step 2) {
                    blob(.74f, side * .25f, .13f, .13f, .035f, .13f, c(0xFF1C1E22), Mat.PLAIN, 0)
                    limb(.62f, side * .2f, .05f, .025f, .6f, side * .2f, 1.25f, .025f, c(0xFFD0392B), Mat.METAL)
                }
                limb(.6f, -.25f, 1.24f, .03f, .6f, .25f, 1.24f, .03f, c(0xFF2E3238), Mat.PLAIN)
                box(.86f, 0f, 0f, .04f, .36f, .46f, c(0xFF9AA3AD))
                for (i in 0 until load.coerceAtMost(4)) {
                    val z0 = .04f + i * .29f
                    box(.86f, 0f, z0, z0 + .27f, .34f, .4f, if (i % 2 == 0) card else cardDark)
                }
            }
            3 -> {
                box(1.06f, 0f, .2f, .28f, 1.1f, .62f, c(0xFF5B6770))
                for (side in -1..1 step 2) {
                    blob(.62f, side * .24f, .1f, .1f, .035f, .1f, c(0xFF1C1E22), Mat.PLAIN, 0)
                    blob(1.5f, side * .24f, .1f, .1f, .035f, .1f, c(0xFF1C1E22), Mat.PLAIN, 0)
                    limb(.5f, side * .27f, .28f, .025f, .5f, side * .27f, 1.12f, .025f, c(0xFF2F80ED), Mat.METAL)
                }
                limb(.5f, -.3f, 1.11f, .03f, .5f, .3f, 1.11f, .03f, c(0xFF2F80ED), Mat.METAL)
                for (i in 0 until load.coerceAtMost(8)) {
                    val layer = i / 4; val slot = i % 4
                    val f = if (slot < 2) .8f else 1.32f
                    val r = if (slot % 2 == 0) -.15f else .15f
                    val z0 = .28f + layer * .31f
                    box(f, r, z0, z0 + .29f, .44f, .28f, if ((slot + layer) % 2 == 0) card else cardDark)
                }
            }
        }
    }

    /** A box in body space: centre (f, r), [l] long forward, [w] wide, from [z0] to [z1]. */
    private fun box(f: Float, r: Float, z0: Float, z1: Float, l: Float, w: Float, color: Int) {
        val hl = l * .5f * s; val hw = w * .5f * s
        val cx = wx(f * s, r * s); val cy = wy(f * s, r * s)
        val ax = floatArrayOf(cx + fx * hl - rx * hw, cx + fx * hl + rx * hw, cx - fx * hl + rx * hw, cx - fx * hl - rx * hw)
        val ay = floatArrayOf(cy + fy * hl - ry * hw, cy + fy * hl + ry * hw, cy - fy * hl + ry * hw, cy - fy * hl - ry * hw)
        val a = oz + z0 * s; val b = oz + z1 * s
        val cc = col(color)
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            scene.quad(ax[i], ay[i], a, ax[j], ay[j], a, ax[j], ay[j], b, ax[i], ay[i], b, cc)
        }
        scene.quad(ax[0], ay[0], b, ax[1], ay[1], b, ax[2], ay[2], b, ax[3], ay[3], b, cc)
    }
}

/** A dog on a walk: body, head and snout, ears, a wagging tail and trotting legs (smooth on the GPU, boxes otherwise). */
class DogPainter(private val scene: Scene3D) {
    companion object {
        private val COATS = intArrayOf(c(0xFF8B5A2B), c(0xFF1F1F21), c(0xFFE8DCC8), c(0xFFC8923F), c(0xFF6E6A66), c(0xFFF3EFE8))
    }

    /** [seed] picks the breed's size and coat; [phase] drives the trot; returns where the collar is (x, y, z) in [out]. */
    fun draw(x: Float, y: Float, heading: Float, phase: Float, moving: Float, seed: Int, out: FloatArray? = null) {
        val coat = COATS[(seed ushr 3) % COATS.size]
        val size = .75f + ((seed ushr 7) and 7) * .07f
        val fx = sin(heading); val fy = -cos(heading); val rx = -fy; val ry = fx
        val legH = .26f * size; val bodyZ = legH + .09f * size
        val bodyL = .26f * size
        val headF = bodyL + .1f * size; val headZ = bodyZ + .12f * size
        out?.let { it[0] = x + fx * (bodyL - .02f); it[1] = y + fy * (bodyL - .02f); it[2] = bodyZ + .08f * size }
        if (!scene.smooth) {
            scene.orientedBox(x, y, heading, bodyL * 2, .2f * size, legH, bodyZ + .1f * size, coat, coat)
            scene.orientedBox(x + fx * headF, y + fy * headF, heading, .18f * size, .14f * size, headZ - .07f * size, headZ + .07f * size, coat, coat)
            return
        }
        fun at(f: Float, r: Float, out2: FloatArray) { out2[0] = x + fx * f + rx * r; out2[1] = y + fy * f + ry * r }
        val p = FloatArray(2); val q = FloatArray(2)
        // trotting legs, diagonal pairs together
        for (i in 0 until 4) {
            val front = i < 2
            val side = if (i % 2 == 0) -1f else 1f
            val f0 = if (front) bodyL * .7f else -bodyL * .7f
            // front-left with back-right, front-right with back-left
            val sw = sin(phase * 1.7f + (if (i == 0 || i == 3) 0f else 3.14159f)) * .09f * size * moving.coerceIn(0f, 1f)
            at(f0, side * .07f * size, p); at(f0 + sw, side * .07f * size, q)
            scene.tube(p[0], p[1], bodyZ, .045f * size, q[0], q[1], .03f, .032f * size, 5, coat, Mat.FABRIC)
        }
        // body, chest and head with its snout, nose and ears
        scene.ellipsoid(x, y, bodyZ + .02f * size, fx, fy, 0f, bodyL + .07f * size, rx, ry, 0f, .11f * size, .1f * size, coat, Mat.FABRIC, 1)
        at(headF, 0f, p)
        scene.ellipsoid(p[0], p[1], headZ, fx, fy, 0f, .1f * size, rx, ry, 0f, .085f * size, .085f * size, coat, Mat.FABRIC, 1)
        at(headF + .09f * size, 0f, p)
        scene.ellipsoid(p[0], p[1], headZ - .03f * size, fx, fy, 0f, .07f * size, rx, ry, 0f, .05f * size, .045f * size, coat, Mat.FABRIC, 0)
        at(headF + .155f * size, 0f, p)
        scene.ellipsoid(p[0], p[1], headZ - .015f * size, fx, fy, 0f, .02f * size, rx, ry, 0f, .025f * size, .02f * size, c(0xFF141414), Mat.PLAIN, 0)
        val ear = scene.mix(coat, 0xFF000000.toInt(), .25f)
        for (k in -1..1 step 2) {
            at(headF - .02f * size, k * .065f * size, p)
            scene.ellipsoid(p[0], p[1], headZ + .045f * size, fx, fy, 0f, .03f * size, rx, ry, 0f, .02f * size, .06f * size, ear, Mat.FABRIC, 0)
        }
        // the tail wags
        at(-bodyL - .05f * size, 0f, p)
        at(-bodyL - .16f * size, sin(phase * 4f) * .06f * size, q)
        scene.tube(p[0], p[1], bodyZ + .06f * size, .025f * size, q[0], q[1], bodyZ + .2f * size, .015f * size, 4, coat, Mat.FABRIC)
    }
}
