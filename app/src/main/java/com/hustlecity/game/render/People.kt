package com.hustlecity.game.render

import com.hustlecity.game.data.c
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Poses for [PersonPainter]. */
object Pose {
    const val STAND = 0
    const val SIT = 1
    const val DANCE = 2
    /** Running on a treadmill: a full running cycle without moving. */
    const val RUN = 3
    /** Seated and busy: pedalling an exercise bike, on a rowing machine, pressing weights on a bench, eating at a table. */
    const val CYCLE = 4
    const val ROW = 5
    const val LIFT = 6
    const val EAT = 7

    /** True for every pose that sits on something. */
    fun seated(pose: Int) = pose == SIT || pose >= CYCLE
}

/**
 * Draws a person facing [heading] (0 = north). In the HD renderer at Medium and High quality that is a modelled
 * figure on a skeleton, moving to clips made in Blender ([AnimLib]); otherwise, and in the far distance, a low-poly
 * one out of boxes whose legs and arms swing while walking and who can sit, dance, run in place and hold an umbrella.
 * No allocations per call, and the boxes are drawn back to front so the figure looks solid from any side.
 */
class PersonPainter(private val scene: Scene3D) {
    companion object {
        val PANTS = intArrayOf(c(0xFF34383F), c(0xFF1F3A5F), c(0xFF4A3B2A), c(0xFF5B6770), c(0xFF2E4A3A), c(0xFF22252A), c(0xFF6B4F3A))
        val HAIR = intArrayOf(c(0xFF2B1D14), c(0xFF4A3220), c(0xFF0F0F10), c(0xFF8A5A2B), c(0xFFD9B26A), c(0xFF7A7A7A), c(0xFFA0522D))
        val UMBRELLAS = intArrayOf(c(0xFF1F2933), c(0xFFC0392B), c(0xFF2F80ED), c(0xFFF2C94C), c(0xFF27AE60), c(0xFF9B51E0))
        private const val MAX = 40
        private const val TWO_PI = 6.2831855f
        /** Below this height on screen (pixels) a person is drawn as boxes: the model would be wasted. */
        private const val FIGURE_MIN_PX = 13f
        private val M_HAIR = intArrayOf(0, 4, 0, 3)
        private val F_HAIR = intArrayOf(1, 2, 5, 3)
        private val TIES = intArrayOf(c(0xFF1F4E9C), c(0xFFB03A2E), c(0xFF2E3238), c(0xFF2F6F4F))
        private val CARD = c(0xFFC8A165)
        private val CARD_DARK = c(0xFFB38A55)
        private val TIRE = c(0xFF1C1E22)
        private val STEEL = c(0xFF9AA3AD)
        private val TRUCK_RED = c(0xFFD0392B)
        private val CART_BLUE = c(0xFF2F80ED)
        private val BAG = c(0xFFD9B98A)
        private val BAG_DARK = c(0xFFB38A55)
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
     * @param carry 0 nothing, 1 a box in both arms, 2 pushing a hand truck, 3 pushing a flatbed cart, 4 pushing a
     *   shopping cart, 5 a shopping basket on the arm
     * @param load boxes on the hand truck or cart
     * @param held color of a product held in the right hand (a shopper's pick), or 0 for none
     * @param bag true to carry a paper shopping bag instead (paid for)
     * @param reach how far through reaching for a shelf they are, 0 to 1, or negative when they aren't
     * @param item the library model (see [Goods]) of what's in the right hand: the product [held] is the color of,
     *   or what someone sitting eats or drinks; the modelled figure shows it instead of a colored block
     */
    fun draw(
        x: Float, y: Float, z: Float, heading: Float, phase: Float, moving: Float,
        shirt: Int, pants: Int, skin: Int, hair: Int, scale: Float = 1f, pose: Int = Pose.STAND,
        apron: Int = 0, umbrella: Int = 0, alpha: Float = 1f, carry: Int = 0, load: Int = 0, held: Int = 0, bag: Boolean = false,
        reach: Float = -1f, item: String? = null,
    ) {
        n = 0
        val s = scale
        val camD = proj().camZOf(x, y, z + .9f * s)
        val px = if (camD > 0f) proj().focal * 1.75f * s / camD else 0f
        // big enough to see who they are: the modelled figure (GPU renderer); otherwise the boxes below
        if (px >= FIGURE_MIN_PX && scene.meshesOn && figure(x, y, z, heading, phase, moving, shirt, pants, skin, hair, s, pose, apron, umbrella, alpha, carry, load, held, bag, px, reach, item)) return
        // far away people get a cheap three-box version
        val lod = camD > 0 && px < 22f
        when (if (Pose.seated(pose)) Pose.SIT else pose) {
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
                val holding = carry in 2..4 || (carry == 1 && load > 0)
                upperBody(0f, z + .82f * s + bob, s, shirt, skin, hair, apron, swing, phase, false, lod, reach = holding)
                if (holding) carried(z, z + .82f * s + bob, s, carry, load)
                else if (!lod && (held != 0 || bag)) inHand(swing * .16f * s, .31f * s, z + .83f * s + bob, s, held, bag)
                else if (!lod && carry == 5) part(swing * .16f * s, .33f * s, z + .45f * s + bob, z + .78f * s + bob, .42f * s, .3f * s, c(0xFFEB5757))
            }
        }
        if (umbrella != 0 && pose == Pose.STAND) {
            val top = z + 2.05f * s
            part(.08f * s, .2f * s, z + 1.1f * s, top, .03f * s, .03f * s, c(0xFF2B2B2B), last = true)
            part(.05f * s, .1f * s, top - .06f * s, top, 1.05f * s, 1.05f * s, umbrella, last = true)
            part(.05f * s, .1f * s, top, top + .1f * s, .6f * s, .6f * s, umbrella, last = true)
        }
        emit(x, y, heading, alpha)
    }

    private fun proj() = scene.proj

    // ------------------------------------------------------------------ the modelled figure

    /** Seconds on a clock that never stops (the renderer sets it each frame): what people standing about move to. */
    var clock = 0f

    private var libVersion = -1
    private var animVersion = -1
    private var figureOk = false
    private val mTorso = arrayOfNulls<Mesh>(4)
    private val mUpper = arrayOfNulls<Mesh>(2)
    private val mFore = arrayOfNulls<Mesh>(2)
    private val mHair = arrayOfNulls<Mesh>(6)
    private var mHips: Mesh? = null; private var mSkirt: Mesh? = null; private var mThigh: Mesh? = null; private var mShin: Mesh? = null
    private var mHead: Mesh? = null; private var mBeard: Mesh? = null; private var mTie: Mesh? = null; private var mApron: Mesh? = null
    private var mUmbrella: Mesh? = null; private var mHand: Mesh? = null; private var mFoot: Mesh? = null
    // things from the library people hold and carry (each optional: a figure without them still draws)
    private var mBag: Mesh? = null; private var mBox: Mesh? = null; private var mHat: Mesh? = null; private var mBriefcase: Mesh? = null
    private var mHandbag: Mesh? = null; private var mPhone: Mesh? = null; private var mCamera: Mesh? = null; private var mCart: Mesh? = null
    private var mBasket: Mesh? = null; private var mDumbbell: Mesh? = null
    private val itemCache = HashMap<String, Mesh?>()
    private var cWalk: Clip? = null; private var cRun: Clip? = null; private var cSit: Clip? = null; private var cEat: Clip? = null
    private var cCarry: Clip? = null; private var cCarryIdle: Clip? = null; private var cPush: Clip? = null; private var cPushIdle: Clip? = null
    private var cWork: Clip? = null; private var cReach: Clip? = null; private var cCycle: Clip? = null; private var cRow: Clip? = null
    private var cLift: Clip? = null; private var cHold: Clip? = null; private var cHang: Clip? = null
    private val cIdle = arrayOfNulls<Clip>(3)
    private val cDance = arrayOfNulls<Clip>(2)
    private val tints = IntArray(6)
    private val pm = FloatArray(9); private val pn = FloatArray(9)
    // the pose being built (hips, then a quaternion per bone), a second one to mix in, and the bones solved from it
    private val r0 = FloatArray(3); private val q0 = FloatArray(AnimLib.BONES * 4)
    private val r1 = FloatArray(3); private val q1 = FloatArray(AnimLib.BONES * 4)
    private val bR = FloatArray(AnimLib.BONES * 9); private val bJ = FloatArray(AnimLib.BONES * 3)
    // the figure's right and forward directions on the ground
    private var bRx = 1f; private var bRy = 0f; private var bFx = 0f; private var bFy = -1f

    /** Looks the body parts and clips up once per pack; false while anything a whole figure needs is missing. */
    private fun resolve(): Boolean {
        if (libVersion == MeshLib.version && animVersion == AnimLib.version) return figureOk
        libVersion = MeshLib.version; animVersion = AnimLib.version
        mTorso[0] = MeshLib.get("ppl_torso_m_shirt"); mTorso[1] = MeshLib.get("ppl_torso_m_tee")
        mTorso[2] = MeshLib.get("ppl_torso_f_shirt"); mTorso[3] = MeshLib.get("ppl_torso_f_tee")
        mUpper[0] = MeshLib.get("ppl_upper_arm_long"); mUpper[1] = MeshLib.get("ppl_upper_arm_short")
        mFore[0] = MeshLib.get("ppl_forearm_long"); mFore[1] = MeshLib.get("ppl_forearm_bare")
        for (k in 0 until 6) mHair[k] = MeshLib.get("ppl_hair$k")
        mHips = MeshLib.get("ppl_hips_trousers"); mSkirt = MeshLib.get("ppl_hips_skirt")
        mThigh = MeshLib.get("ppl_thigh_leg"); mShin = MeshLib.get("ppl_shin_leg"); mFoot = MeshLib.get("ppl_foot_shoe")
        mHand = MeshLib.get("ppl_hand_open")
        mHead = MeshLib.get("ppl_head_face"); mBeard = MeshLib.get("ppl_beard_full")
        mTie = MeshLib.get("ppl_tie_m"); mApron = MeshLib.get("ppl_apron_front"); mUmbrella = MeshLib.get("ppl_umbrella_open")
        mBag = MeshLib.get("item_shopping_bag"); mBox = MeshLib.get("item_cardboard_box"); mHat = MeshLib.get("item_hat")
        mBriefcase = MeshLib.get("item_briefcase"); mHandbag = MeshLib.get("item_handbag"); mPhone = MeshLib.get("item_smartphone")
        mCamera = MeshLib.get("item_camera"); mCart = MeshLib.get("item_shopping_cart"); mBasket = MeshLib.get("item_shopping_basket")
        mDumbbell = MeshLib.get("item_dumbbell")
        itemCache.clear()
        cWalk = AnimLib.get("walk"); cRun = AnimLib.get("run"); cSit = AnimLib.get("sit"); cEat = AnimLib.get("sit_eat")
        cCarry = AnimLib.get("carry"); cCarryIdle = AnimLib.get("carry_idle"); cPush = AnimLib.get("push"); cPushIdle = AnimLib.get("push_idle")
        cWork = AnimLib.get("work"); cReach = AnimLib.get("reach"); cCycle = AnimLib.get("cycle"); cRow = AnimLib.get("row")
        cLift = AnimLib.get("lift"); cHold = AnimLib.get("hold"); cHang = AnimLib.get("hang")
        cIdle[0] = AnimLib.get("idle"); cIdle[1] = AnimLib.get("idle_look"); cIdle[2] = AnimLib.get("idle_phone")
        cDance[0] = AnimLib.get("dance_a"); cDance[1] = AnimLib.get("dance_b")
        figureOk = mTorso.all { it != null } && mUpper.all { it != null } && mFore.all { it != null } && mHair.all { it != null } &&
            mHips != null && mSkirt != null && mThigh != null && mShin != null && mFoot != null && mHand != null && mHead != null &&
            cWalk != null && cRun != null && cSit != null && cIdle[0] != null && cDance[0] != null
        return figureOk
    }

    /**
     * Draws the person as modelled body parts on the skeleton, moving to the clips made in Blender: walking and
     * running by the stride [phase], standing about, sitting, eating, dancing, working a counter, carrying and
     * pushing, reaching for a shelf, and the gym machines. Build, clothes and hair follow from the colors the person
     * was given, so they never change from frame to frame. Returns false, leaving the boxes to draw, when a pack is
     * missing.
     *
     * Four levels of detail by size on screen ([px], the person's height in pixels); the full one only when zoomed
     * right in at High quality.
     */
    private fun figure(x: Float, y: Float, z: Float, heading: Float, phase: Float, moving: Float, shirt: Int, pants: Int, skin: Int, hair: Int, s: Float,
                       pose: Int, apron: Int, umbrella: Int, alpha: Float, carry: Int, load: Int, held: Int, bag: Boolean, px: Float, reach: Float,
                       item: String?): Boolean {
        if (!resolve()) return false
        val itemMesh = item?.let { name -> itemCache.getOrPut(name) { MeshLib.get(name) } }
        val level = if (px > 260f && scene.modelDetail >= 2) 0 else if (px > 105f) 1 else if (px > 40f) 2 else 3
        var h = shirt xor (hair * 31) xor (skin * 131) xor (pants * 17)
        h = h xor (h ushr 13); h *= 0x5bd1e995; h = h xor (h ushr 15)
        val staff = apron != 0
        val female = (h and 1) == 1
        val tee = !staff && ((h ushr 1) and 1) == 1
        val seated = Pose.seated(pose)
        val skirt = female && ((h ushr 2) and 0xFF) % 3 == 0
        val tie = !female && !tee && ((h ushr 10) and 0xFF) % 3 == 0
        val beard = !female && ((h ushr 18) and 3) == 0
        val style = if (female) F_HAIR[(h ushr 20) and 3] else M_HAIR[(h ushr 20) and 3]
        tints[1] = shirt; tints[2] = pants; tints[3] = skin; tints[4] = hair; tints[5] = apron
        bFx = sin(heading); bFy = -cos(heading)
        bRx = -bFy; bRy = bFx

        // everybody keeps their own time, so a crowd doesn't breathe in step
        val t = clock + ((h ushr 8) and 0xFF) * .173f
        val amp = moving.coerceIn(0f, 1f)
        val stride = phase / TWO_PI
        val holding = carry in 2..4 || (carry == 1 && load > 0)
        // what people out and about carry of their own: a briefcase with a shirt and tie, some a handbag; standing
        // about, a phone out (those who stand looking at it) or a camera (some of those looking round); now and then a hat
        val idleKind = ((h ushr 12) and 0xFF) % 10
        val free = !staff && !holding && held == 0 && !bag && item == null && umbrella == 0 && carry == 0 && !seated && pose == Pose.STAND
        val briefcase = free && tie && ((h ushr 28) and 1) == 0 && mBriefcase != null
        val handbag = free && !briefcase && female && ((h ushr 27) and 3) == 0 && mHandbag != null
        val phone = free && !briefcase && !handbag && idleKind >= 8 && amp < .5f && mPhone != null
        val camera = free && !briefcase && !handbag && idleKind in 6..7 && ((h ushr 29) and 1) == 1 && amp < .5f && mCamera != null
        val hat = !staff && umbrella == 0 && pose != Pose.DANCE && ((h ushr 25) and 7) == 0 && mHat != null
        val hangs = bag || held != 0 || briefcase || handbag || carry == 5 || (item != null && !seated)
        when (pose) {
            Pose.SIT -> cSit!!.atTime(t, r0, q0)
            Pose.EAT -> (cEat ?: cSit!!).atTime(t, r0, q0)
            Pose.CYCLE -> (cCycle ?: cSit!!).at(stride, r0, q0)
            Pose.ROW -> (cRow ?: cSit!!).at(stride * .5f, r0, q0)
            Pose.LIFT -> (cLift ?: cSit!!).at(stride * .4f, r0, q0)
            Pose.DANCE -> (cDance[(h ushr 5) and 1] ?: cDance[0]!!).at(phase * 1.6f / (2f * TWO_PI), r0, q0)
            Pose.RUN -> cRun!!.at(stride, r0, q0)
            else -> {
                val going = if (holding) (if (carry == 1) cCarry else cPush) ?: cWalk!! else cWalk!!
                val waiting = when {
                    holding -> (if (carry == 1) cCarryIdle else cPushIdle) ?: cIdle[0]!!
                    staff -> cWork ?: cIdle[0]!!
                    // most people just stand; some look up the street and at their watch, some are on their phone
                    else -> cIdle[when (((h ushr 12) and 0xFF) % 10) { in 0..5 -> 0; in 6..7 -> 1; else -> 2 }] ?: cIdle[0]!!
                }
                val reaching = cReach
                if (reach >= 0f && amp < .5f && !holding && reaching != null) reaching.at(reach, r0, q0)
                else if (amp >= .99f) going.at(stride, r0, q0)
                else {
                    waiting.atTime(t, r0, q0)
                    if (amp > .01f) { going.at(stride, r1, q1); mix(amp) }
                }
                if (!holding) {
                    // an umbrella's handle in the right fist; or a bag hanging from it, the arm hardly swinging
                    val arm = if (umbrella != 0) cHold else if (hangs) cHang else null
                    if (arm != null) {
                        arm.at(0f, r1, q1)
                        mixBone(AnimLib.UPPER_R, if (umbrella != 0) 1f else .75f); mixBone(AnimLib.FORE_R, 1f); mixBone(AnimLib.HAND_R, 1f)
                    }
                }
                // a skirt shortens the stride
                if (skirt && amp > 0f) for (b in AnimLib.THIGH_L..AnimLib.FOOT_R) relax(b, .4f * amp)
            }
        }
        solve(female)

        val short = if (tee) 1 else 0
        put(mTorso[(if (female) 2 else 0) + short], level, AnimLib.TORSO, x, y, z, s, alpha)
        put(if (skirt && !seated) mSkirt else mHips, level, AnimLib.HIPS, x, y, z, s, alpha)
        if (tie) { tints[5] = TIES[(h ushr 24) and 3]; put(mTie, level, AnimLib.TORSO, x, y, z, s, alpha); tints[5] = apron }
        if (staff) put(mApron, level, AnimLib.TORSO, x, y, z, s, alpha)
        put(mHead, level, AnimLib.HEAD, x, y, z, s, alpha)
        put(mHair[style], level, AnimLib.HEAD, x, y, z, s, alpha)
        if (beard) put(mBeard, level, AnimLib.HEAD, x, y, z, s, alpha)
        for (side in 0..1) {
            val o = side * 3
            put(mUpper[short], level, AnimLib.UPPER_L + o, x, y, z, s, alpha)
            put(mFore[short], level, AnimLib.FORE_L + o, x, y, z, s, alpha)
            put(mHand, level, AnimLib.HAND_L + o, x, y, z, s, alpha)
            // bare legs under a skirt; sitting, the thighs stay in its color
            if (skirt) tints[2] = if (seated) pants else skin
            put(mThigh, level, AnimLib.THIGH_L + o, x, y, z, s, alpha)
            if (skirt) tints[2] = skin
            put(mShin, level, AnimLib.SHIN_L + o, x, y, z, s, alpha)
            tints[2] = pants
            put(mFoot, level, AnimLib.FOOT_L + o, x, y, z, s, alpha)
        }
        // where the right fist is: what the hand holds goes there
        val ho = AnimLib.HAND_R * 9; val hj = AnimLib.HAND_R * 3
        val gx = bJ[hj] - bR[ho + 2] * .09f; val gy = bJ[hj + 1] - bR[ho + 5] * .09f; val gz = bJ[hj + 2] - bR[ho + 8] * .09f
        if (umbrella != 0 && pose == Pose.STAND && !holding) {
            tints[5] = umbrella
            val mesh = mUmbrella
            if (mesh != null) {
                pn[0] = -bRx; pn[1] = bFx; pn[2] = 0f; pn[3] = -bRy; pn[4] = bFy; pn[5] = 0f; pn[6] = 0f; pn[7] = 0f; pn[8] = 1f
                for (i in 0 until 9) pm[i] = pn[i] * s
                scene.meshMatrix(mesh, level, pm, pn, x + s * (-bRx * gx + bFx * gy), y + s * (-bRy * gx + bFy * gy), z + s * (gz - .06f), tints, 0, alpha, false)
            }
        }
        val il = level.coerceAtMost(2)
        // the hat's brim a little below the crown of the head, so the hair stays under it
        if (hat) putAt(mHat!!, il, AnimLib.HEAD, mHead!!.maxZ - .08f, x, y, z, s, alpha)
        if (!seated && pose != Pose.DANCE) {
            when {
                holding -> carriedModels(x, y, z, s, carry, load, alpha, il)
                bag && mBag != null -> inFist(mBag!!, il, AnimLib.HAND_R, x, y, z, s, alpha, hang = true)
                itemMesh != null -> inFist(itemMesh, il, AnimLib.HAND_R, x, y, z, s, alpha, hang = false)
                carry == 5 && mBasket != null -> inFist(mBasket!!, il, AnimLib.HAND_R, x, y, z, s, alpha, hang = true)
                briefcase -> inFist(mBriefcase!!, il, AnimLib.HAND_R, x, y, z, s, alpha, hang = true)
                handbag -> inFist(mHandbag!!, il, AnimLib.HAND_R, x, y, z, s, alpha, hang = true)
                phone -> inFist(mPhone!!, il, AnimLib.HAND_R, x, y, z, s, alpha, hang = false)
                camera -> inFist(mCamera!!, il, AnimLib.HAND_R, x, y, z, s, alpha, hang = false)
                held != 0 || bag -> inHand(gy * s, -gx * s, z + gz * s, s, held, bag)
            }
            if (n > 0) emit(x, y, heading, alpha)
        } else if (pose == Pose.LIFT) {
            // a dumbbell in each fist
            mDumbbell?.let { d -> inFist(d, il, AnimLib.HAND_R, x, y, z, s, alpha, hang = false); inFist(d, il, AnimLib.HAND_L, x, y, z, s, alpha, hang = false) }
        } else if (seated && itemMesh != null && pose != Pose.CYCLE && pose != Pose.ROW) {
            // a meal or a drink in hand while they sit
            inFist(itemMesh, il, AnimLib.HAND_R, x, y, z, s, alpha, hang = false)
        }
        return true
    }

    /**
     * Item model [m] in the fist of bone [hand] for the figure standing at (x, y, z): held round its middle, upright,
     * or ([hang]) hanging from its handle, its long side along the way they face, like a bag or a briefcase.
     */
    private fun inFist(m: Mesh, level: Int, hand: Int, x: Float, y: Float, z: Float, s: Float, alpha: Float, hang: Boolean) {
        val ho = hand * 9; val hj = hand * 3
        // the fist: a little way down the hand from the wrist joint, in the figure's frame (x left, y forward)
        val gx = bJ[hj] - bR[ho + 2] * .09f; val gy = bJ[hj + 1] - bR[ho + 5] * .09f; val gz = bJ[hj + 2] - bR[ho + 8] * .09f
        // the model's x and y on the ground: across the body and forward, or (hanging) forward and to the right
        val ax: Float; val ay: Float; val bx: Float; val by: Float
        if (hang) { ax = bFx; ay = bFy; bx = bRx; by = bRy } else { ax = -bRx; ay = -bRy; bx = bFx; by = bFy }
        pn[0] = ax; pn[1] = bx; pn[2] = 0f; pn[3] = ay; pn[4] = by; pn[5] = 0f; pn[6] = 0f; pn[7] = 0f; pn[8] = 1f
        for (i in 0 until 9) pm[i] = pn[i] * s
        // its middle (or the middle of its top) on the fist
        val cx = m.centerX; val cy = m.centerY
        val cz = if (hang) m.maxZ - .02f else m.centerZ
        val wx = x + s * (-bRx * gx + bFx * gy) - s * (ax * cx + bx * cy)
        val wy = y + s * (-bRy * gx + bFy * gy) - s * (ay * cx + by * cy)
        scene.meshMatrix(m, level.coerceAtMost(m.levels.size - 1), pm, pn, wx, wy, z + s * (gz - cz), null, 0, alpha, false)
    }

    /** A model on bone [b], lifted [up] meters along the bone's own up axis (a hat on the head). */
    private fun putAt(m: Mesh, level: Int, b: Int, up: Float, x: Float, y: Float, z: Float, s: Float, alpha: Float) {
        val o = b * 9
        for (c in 0 until 3) {
            pn[c] = -bRx * bR[o + c] + bFx * bR[o + 3 + c]
            pn[3 + c] = -bRy * bR[o + c] + bFy * bR[o + 3 + c]
            pn[6 + c] = bR[o + 6 + c]
        }
        for (i in 0 until 9) pm[i] = pn[i] * s
        val jx = bJ[b * 3] + bR[o + 2] * up; val jy = bJ[b * 3 + 1] + bR[o + 5] * up; val jz = bJ[b * 3 + 2] + bR[o + 8] * up
        scene.meshMatrix(m, level.coerceAtMost(m.levels.size - 1), pm, pn, x + s * (-bRx * jx + bFx * jy), y + s * (-bRy * jx + bFy * jy), z + s * jz, null, 0, alpha, false)
    }

    /** One model stood in the figure's frame: [f] forward, [r] to the right, from height [z0], turned with the figure. */
    private fun putFrame(m: Mesh, level: Int, x: Float, y: Float, f: Float, r: Float, z0: Float, s: Float, alpha: Float, k: Float = 1f) {
        pn[0] = -bRx; pn[1] = bFx; pn[2] = 0f; pn[3] = -bRy; pn[4] = bFy; pn[5] = 0f; pn[6] = 0f; pn[7] = 0f; pn[8] = 1f
        for (i in 0 until 9) pm[i] = pn[i] * s * k
        val cx = m.centerX * s * k; val cy = m.centerY * s * k
        val wx = x + bFx * f + bRx * r - (-bRx * cx + bFx * cy)
        val wy = y + bFy * f + bRy * r - (-bRy * cx + bFy * cy)
        scene.meshMatrix(m, level.coerceAtMost(m.levels.size - 1), pm, pn, wx, wy, z0 - m.minZ * s * k, null, 0, alpha, false)
    }

    /**
     * What a modelled figure carries: a cardboard box in both arms; the boxes stacked on a hand truck or a flatbed
     * cart (the truck and the cart themselves stay the painted ones); a shopping cart pushed in front.
     */
    private fun carriedModels(x: Float, y: Float, floor: Float, s: Float, carry: Int, load: Int, alpha: Float, level: Int) {
        val box = mBox
        val chest = floor + .82f * s
        when (carry) {
            1 -> if (box != null) putFrame(box, level, x, y, .42f * s, 0f, chest + .2f * s, s, alpha, .85f) else carried(floor, chest, s, carry, load)
            2, 3 -> {
                if (box == null) { carried(floor, chest, s, carry, load); return }
                // the truck or cart, painted, without its boxes; then the boxes as models
                carried(floor, chest, s, carry, 0)
                if (carry == 2) for (i in 0 until load.coerceAtMost(4)) putFrame(box, level, x, y, .86f * s, 0f, floor + .04f * s + i * .29f * s, s, alpha, .8f)
                else for (i in 0 until load.coerceAtMost(8)) {
                    val layer = i / 4; val slot = i % 4
                    putFrame(box, level, x, y, (if (slot < 2) .8f else 1.32f) * s, (if (slot % 2 == 0) -.15f else .15f) * s, floor + .28f * s + layer * .31f * s, s, alpha, .6f)
                }
            }
            4 -> {
                val cart = mCart
                if (cart != null) putFrame(cart, level, x, y, .85f * s, 0f, floor, s, alpha) else carried(floor, chest, s, 3, 0)
            }
        }
    }

    /** Mixes the second pose into the first: [w] of the way, every bone and the hips. */
    private fun mix(w: Float) {
        for (i in 0 until 3) r0[i] += (r1[i] - r0[i]) * w
        for (b in 0 until AnimLib.BONES) mixBone(b, w)
    }

    private fun mixBone(b: Int, w: Float) {
        val i = b * 4
        var bx = q1[i]; var by = q1[i + 1]; var bz = q1[i + 2]; var bw = q1[i + 3]
        if (q0[i] * bx + q0[i + 1] * by + q0[i + 2] * bz + q0[i + 3] * bw < 0f) { bx = -bx; by = -by; bz = -bz; bw = -bw }
        val qx = q0[i] + (bx - q0[i]) * w; val qy = q0[i + 1] + (by - q0[i + 1]) * w; val qz = q0[i + 2] + (bz - q0[i + 2]) * w; val qw = q0[i + 3] + (bw - q0[i + 3]) * w
        val l = sqrt(qx * qx + qy * qy + qz * qz + qw * qw).coerceAtLeast(1e-6f)
        q0[i] = qx / l; q0[i + 1] = qy / l; q0[i + 2] = qz / l; q0[i + 3] = qw / l
    }

    /** Turns a bone [w] of the way back to how it hangs at rest. */
    private fun relax(b: Int, w: Float) {
        val i = b * 4
        val sg = if (q0[i + 3] < 0f) -1f else 1f
        val qx = q0[i] * (1f - w); val qy = q0[i + 1] * (1f - w); val qz = q0[i + 2] * (1f - w); val qw = q0[i + 3] + (sg - q0[i + 3]) * w
        val l = sqrt(qx * qx + qy * qy + qz * qz + qw * qw).coerceAtLeast(1e-6f)
        q0[i] = qx / l; q0[i + 1] = qy / l; q0[i + 2] = qz / l; q0[i + 3] = qw / l
    }

    /** From the pose to every bone's turn and joint position in the figure's own frame (x left, y forward, z up). */
    private fun solve(female: Boolean) {
        val par = AnimLib.parent; val rest = AnimLib.rest
        val narrow = if (female) AnimLib.narrow else 1f
        for (b in 0 until AnimLib.BONES) {
            val i = b * 4
            val qx = q0[i]; val qy = q0[i + 1]; val qz = q0[i + 2]; val qw = q0[i + 3]
            val l0 = 1f - 2f * (qy * qy + qz * qz); val l1 = 2f * (qx * qy - qz * qw); val l2 = 2f * (qx * qz + qy * qw)
            val l3 = 2f * (qx * qy + qz * qw); val l4 = 1f - 2f * (qx * qx + qz * qz); val l5 = 2f * (qy * qz - qx * qw)
            val l6 = 2f * (qx * qz - qy * qw); val l7 = 2f * (qy * qz + qx * qw); val l8 = 1f - 2f * (qx * qx + qy * qy)
            val o = b * 9
            val p = par[b]
            if (p < 0) {
                bR[o] = l0; bR[o + 1] = l1; bR[o + 2] = l2; bR[o + 3] = l3; bR[o + 4] = l4; bR[o + 5] = l5; bR[o + 6] = l6; bR[o + 7] = l7; bR[o + 8] = l8
                bJ[b * 3] = r0[0]; bJ[b * 3 + 1] = r0[1]; bJ[b * 3 + 2] = r0[2]
                continue
            }
            val po = p * 9
            for (r in 0 until 3) {
                val a0 = bR[po + r * 3]; val a1 = bR[po + r * 3 + 1]; val a2 = bR[po + r * 3 + 2]
                bR[o + r * 3] = a0 * l0 + a1 * l3 + a2 * l6
                bR[o + r * 3 + 1] = a0 * l1 + a1 * l4 + a2 * l7
                bR[o + r * 3 + 2] = a0 * l2 + a1 * l5 + a2 * l8
            }
            val kb = if (b in AnimLib.UPPER_L..AnimLib.HAND_R) narrow else 1f
            val kp = if (p in AnimLib.UPPER_L..AnimLib.HAND_R) narrow else 1f
            val dx = rest[b * 3] * kb - rest[p * 3] * kp; val dy = rest[b * 3 + 1] - rest[p * 3 + 1]; val dz = rest[b * 3 + 2] - rest[p * 3 + 2]
            bJ[b * 3] = bJ[p * 3] + bR[po] * dx + bR[po + 1] * dy + bR[po + 2] * dz
            bJ[b * 3 + 1] = bJ[p * 3 + 1] + bR[po + 3] * dx + bR[po + 4] * dy + bR[po + 5] * dz
            bJ[b * 3 + 2] = bJ[p * 3 + 2] + bR[po + 6] * dx + bR[po + 7] * dy + bR[po + 8] * dz
        }
    }

    /** Appends a body part on its bone, for the person standing at (x, y, z). */
    private fun put(mesh: Mesh?, level: Int, b: Int, x: Float, y: Float, z: Float, s: Float, alpha: Float) {
        if (mesh == null) return
        val o = b * 9
        // the figure's own +y is its front and its +x its left
        for (c in 0 until 3) {
            pn[c] = -bRx * bR[o + c] + bFx * bR[o + 3 + c]
            pn[3 + c] = -bRy * bR[o + c] + bFy * bR[o + 3 + c]
            pn[6 + c] = bR[o + 6 + c]
        }
        for (i in 0 until 9) pm[i] = pn[i] * s
        val jx = bJ[b * 3]; val jy = bJ[b * 3 + 1]; val jz = bJ[b * 3 + 2]
        scene.meshMatrix(mesh, level, pm, pn, x + s * (-bRx * jx + bFx * jy), y + s * (-bRy * jx + bFy * jy), z + s * jz, tints, 0, alpha, false)
    }

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

    /** A product picked off a shelf, or the paper bag it went into at the register, in the right fist at ([f] forward, [r] right, [zh] up). */
    private fun inHand(f: Float, r: Float, zh: Float, s: Float, held: Int, bag: Boolean) {
        if (bag) {
            part(f, r + .02f * s, zh - .33f * s, zh - .02f * s, .24f * s, .11f * s, BAG, BAG_DARK)
            part(f, r + .02f * s, zh - .02f * s, zh + .02f * s, .2f * s, .02f * s, BAG_DARK)
        } else part(f + .03f * s, r - .03f * s, zh - .15f * s, zh + .03f * s, .11f * s, .08f * s, held)
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
