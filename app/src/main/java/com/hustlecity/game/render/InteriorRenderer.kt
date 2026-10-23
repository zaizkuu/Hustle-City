package com.hustlecity.game.render

import android.graphics.Canvas
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.FurnitureDef
import com.hustlecity.game.data.c
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.carryPose
import com.hustlecity.game.engine.InteriorScene
import com.hustlecity.game.engine.SHIRT_COLORS
import com.hustlecity.game.engine.SKIN_TONES
import com.hustlecity.game.engine.ShelfStock
import com.hustlecity.game.engine.itemDims
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Draws building interiors as a cut-away room floating in the dark, like a dollhouse. */
class InteriorRenderer(private val e: GameEngine) {
    val proj = Projector()
    private val batch = RenderBatch()
    val scene = Scene3D(proj, batch)

    private val maxDraw = 1024
    private var dCount = 0
    private val dType = IntArray(maxDraw)
    private val dIdx = IntArray(maxDraw)
    private val dKey = FloatArray(maxDraw)
    private val order = IntArray(maxDraw)
    private val people = PersonPainter(scene)
    private val parts = PartSorter(scene)
    private val stock = StockPainter(scene, parts)
    private val cars = CarPainter(scene)
    private val px = FloatArray(10); private val py = FloatArray(10); private val pz = FloatArray(10)

    companion object {
        val BG = c(0xFF16181D)
        val WALL_TOP = c(0xFF1E2024)
        const val WALL_H = 3f
        /** Height of the back room's wall: cut away like the walls nearest the camera, so you can see in. */
        const val PARTITION_H = 1.2f
        /** Top of the back room's door frame, where the STAFF ONLY sign hangs. */
        const val BACK_DOOR_H = 2.25f
        private val BACK_FLOOR = c(0xFFA3A7AD)
        private val STAFF_RED = c(0xFFC0392B)
        private val LIGHT_WARM = c(0xFFFFE6B0)
        private val LIGHT_COOL = c(0xFFE8F2FF)
        /** Sorting offsets: everything beyond the back room wall goes first, then the wall, then the near side. */
        private const val BEYOND = 1e6f
        private const val WALL_KEY = 5e5f
    }

    /** Film camera for trailers: target (x, y in tiles) and distance, and a pitch in radians, instead of the game's. */
    var cinemaTarget: FloatArray? = null
    var cinemaPitch = Float.NaN

    fun setupCamera(sc: InteriorScene, w: Int, h: Int) {
        // zoomed in, the camera drifts from the middle of the room toward the player
        val follow = ((sc.zoom - 1f) / 1.2f).coerceIn(0f, 1f)
        proj.tx = sc.w / 2f + (sc.px - sc.w / 2f) * follow
        proj.ty = sc.d / 2f + (sc.py - sc.d / 2f) * follow
        proj.dist = (max(sc.w, sc.d) * .95f + 5f) / sc.zoom
        cinemaTarget?.let { proj.tx = it[0]; proj.ty = it[1]; proj.dist = it[2] }
        proj.pitch = if (!cinemaPitch.isNaN()) cinemaPitch else Math.toRadians(56.0).toFloat()
        proj.yaw = sc.yaw
        proj.near = .5f
        proj.update(w.toFloat(), h.toFloat(), .55f)
    }

    /** Draws the current interior; with [gpu] the 3D goes into that frame for the GPU renderer. */
    fun draw(canvas: Canvas, w: Int, h: Int, gpu: com.hustlecity.game.render.gpu.GpuFrame? = null, density: Float = 1f) {
        val sc = e.interior ?: return
        scene.gpu = gpu
        setupCamera(sc, w, h)
        scene.applyDaylight(1f)
        scene.sunX = .45f; scene.sunY = .89f
        if (gpu != null) {
            gpu.clear()
            gpu.setCamera(proj)
            com.hustlecity.game.render.gpu.GpuLighting.interior(gpu, BG)
            gpu.time = e.realTime
            // furniture and people cast soft shadows from the ceiling light
            gpu.setShadow(-1f, -1f, sc.w + 1f, sc.d + 1f, 3.2f, if (e.settings.quality >= 2) 1024 else 512, if (e.settings.quality > 0) .5f else 0f)
            scene.material = Mat.TILE
        } else {
            canvas.drawColor(BG)
            batch.begin(canvas)
        }
        scene.groundPass = true

        // floor (checker tiles)
        val fa = sc.floorColor
        val fb = scene.lit(fa, .9f)
        val room = sc.backRoom
        val backA = scene.lit(BACK_FLOOR, 1f); val backB = scene.lit(BACK_FLOOR, .93f)
        for (y in 0 until sc.d) for (x in 0 until sc.w) {
            val even = (x + y) % 2 == 0
            val col = if (room != null && room.contains(y)) (if (even) backA else backB) else if (even) scene.lit(fa, 1f) else fb
            scene.groundRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, 0f, col)
        }
        if (!e.ui.designer) {
            // grout lines between the floor tiles
            val grout = scene.withAlpha(scene.lit(scene.mix(fa, 0xFF000000.toInt(), .45f), 1f), .35f)
            for (x in 1 until sc.w) scene.groundRect(x - .012f, 0f, x + .012f, sc.d.toFloat(), .004f, grout)
            for (y in 1 until sc.d) scene.groundRect(0f, y - .012f, sc.w.toFloat(), y + .012f, .004f, grout)
        }
        // entrance mat
        scene.groundRect(sc.doorX - 1f, sc.d - .9f, sc.doorX + 1f, sc.d.toFloat(), .01f, c(0xFF3B3F46))
        scene.groundRect(sc.doorX - .9f, sc.d - .8f, sc.doorX + .9f, sc.d - .1f, .012f, c(0xFF4A505A))
        if (room != null) {
            // a metal threshold strip in the back room doorway
            val dy = room.depth.toFloat()
            scene.groundRect(room.doorX.toFloat(), dy - .09f, (room.doorX + room.doorW).toFloat(), dy + .09f, .013f, c(0xFF7D838B))
        }
        sc.business?.let { if (!e.ui.designer) drawDirt(sc, it) }
        if (!e.ui.designer) bakedLight(sc)

        if (e.ui.designer) {
            val line = scene.withAlpha(0xFFFFFFFF.toInt(), .22f)
            for (x in 0..sc.w) scene.groundRect(x - .02f, 0f, x + .02f, sc.d.toFloat(), .015f, line)
            for (y in 0..sc.d) scene.groundRect(0f, y - .02f, sc.w.toFloat(), y + .02f, .015f, line)
            if (room != null) {
                // staff only: shade the back room, and mark the doorway that has to stay clear
                scene.groundRect(0f, 0f, sc.w.toFloat(), room.depth.toFloat(), .016f, scene.withAlpha(c(0xFFF2994A), .16f))
                val dy = room.depth.toFloat()
                scene.groundRect(room.doorX.toFloat(), dy - 1f, (room.doorX + room.doorW).toFloat(), dy + 1f, .017f, scene.withAlpha(c(0xFF27AE60), .3f))
            }
        }

        scene.groundPass = false
        scene.material = Mat.PLASTER
        // walls: back walls first, front (cut-away) walls last
        val camX = proj.camX; val camY = proj.camY
        val backN = camY > 0; val backS = camY < sc.d; val backW = camX > 0; val backE = camX < sc.w
        val wall = sc.wallColor
        val tall = if (sc.showWalls) WALL_H else .35f
        if (backN) wallN(sc, wall, tall)
        if (backW) wallW(sc, wall, tall)
        if (backE) wallE(sc, wall, tall)
        if (backS) wallS(sc, wall, tall)

        scene.material = Mat.PLAIN
        // drawables: items, people, cars (with a back room, whatever is beyond its wall goes first, then the wall)
        dCount = 0
        wallY = room?.depth?.toFloat() ?: -1f
        val items = sc.items
        for ((i, p) in items.withIndex()) {
            val def = Catalog.item(p.itemId) ?: continue
            val (fw, fd) = itemDims(def, p.rot)
            add(0, i, key(p.x + fw / 2f, p.y + fd / 2f))
        }
        for ((i, v) in sc.visitors.withIndex()) add(1, i, key(v.x, v.y))
        for ((i, v) in sc.staff.withIndex()) add(2, i, key(v.x, v.y))
        if (e.state.player.inBuilding >= 0 && cinemaTarget == null) add(3, 0, key(sc.px, sc.py))
        for ((i, car) in sc.showroom.withIndex()) add(4, i, key(car.second, car.third))
        if (sc.ghostId != null) {
            val def = Catalog.item(sc.ghostId!!)
            if (def != null) {
                val (fw, fd) = itemDims(def, sc.ghostRot)
                add(5, 0, key(sc.ghostX + fw / 2f, sc.ghostY + fd / 2f) - .01f)
            }
        }
        if (room != null) add(6, 0, WALL_KEY)
        for (i in 0 until dCount) order[i] = i
        // insertion sort, far first (counts are small)
        for (i in 1 until dCount) {
            val v = order[i]; val kv = dKey[v]
            var j = i - 1
            while (j >= 0 && dKey[order[j]] < kv) { order[j + 1] = order[j]; j-- }
            order[j + 1] = v
        }
        for (k in 0 until dCount) {
            val i = order[k]
            when (dType[i]) {
                0 -> {
                    val p = items[dIdx[i]]
                    val def = Catalog.item(p.itemId) ?: continue
                    if (dIdx[i] == sc.movingIndex) continue
                    if (dIdx[i] == sc.selectedIndex) {
                        val (fw, fd) = itemDims(def, p.rot)
                        scene.groundRect(p.x.toFloat(), p.y.toFloat(), (p.x + fw).toFloat(), (p.y + fd).toFloat(), .02f, scene.withAlpha(c(0xFF2F80ED), .45f))
                    }
                    val shelf = sc.shelf
                    val lay = shelf?.layoutOf(dIdx[i])
                    if (shelf != null && lay != null) stock.draw(def, p, dIdx[i], shelf, lay, 1f)
                    else drawItem(def, p.x.toFloat(), p.y.toFloat(), p.rot, 1f)
                }
                1 -> sc.visitors.getOrNull(dIdx[i])?.let { person(it, false); takes(sc, it) }
                2 -> sc.staff.getOrNull(dIdx[i])?.let { person(it, true) }
                3 -> {
                    val p = e.state.player
                    scene.disc(sc.px, sc.py, .02f, .45f, .45f, 12, scene.withAlpha(c(0xFF27AE60), .55f))
                    // a box fetched from the back room is held in both arms (or rides on the hand truck)
                    val pose = e.carryPose()
                    val carry = if (sc.playerBox && pose == 0) 1 else pose
                    val load = p.hand.size + if (sc.playerBox) 1 else 0
                    people.draw(sc.px, sc.py, 0f, sc.playerHeading, e.realTime * 7f, sc.playerMoving, SHIRT_COLORS[p.shirt % SHIRT_COLORS.size], PersonPainter.PANTS[1],
                        SKIN_TONES[p.skin % SKIN_TONES.size], PersonPainter.HAIR[p.skin % PersonPainter.HAIR.size], carry = carry, load = load)
                }
                4 -> {
                    val car = sc.showroom[dIdx[i]]
                    val def = Catalog.vehicle(car.first)
                    scene.disc(car.second, car.third, .02f, 2.6f, 2.6f, 16, scene.withAlpha(0xFFFFFFFF.toInt(), .25f))
                    drawCar(car.second, car.third, 2.4f, def.length, def.width, def.height, def.color, def.style)
                }
                5 -> {
                    val def = Catalog.item(sc.ghostId!!) ?: continue
                    val valid = sc.ghostValid
                    val (fw, fd) = itemDims(def, sc.ghostRot)
                    scene.groundRect(sc.ghostX.toFloat(), sc.ghostY.toFloat(), (sc.ghostX + fw).toFloat(), (sc.ghostY + fd).toFloat(), .03f,
                        scene.withAlpha(if (valid) c(0xFF27AE60) else c(0xFFEB5757), .55f))
                    drawItem(def, sc.ghostX.toFloat(), sc.ghostY.toFloat(), sc.ghostRot, .75f)
                }
                6 -> sc.backRoom?.let { backWall(sc, it, wall) }
            }
        }
        scene.material = Mat.PLASTER
        if (!backN) wallN(sc, wall, .35f)
        if (!backW) wallW(sc, wall, .35f)
        if (!backE) wallE(sc, wall, .35f)
        if (!backS) wallS(sc, wall, .35f)
        scene.material = Mat.PLAIN
        if (gpu == null) batch.flush()
        scene.gpu = null
        if (sc.walkIn && !e.ui.designer) priceTags(canvas, sc, density)
        room?.let { staffSign(canvas, sc, it, density) }
    }

    private val rayD = FloatArray(3)

    /** The item under a screen point (ray against each item's bounding box), or -1. */
    fun pickItem(sc: InteriorScene, sx: Float, sy: Float): Int {
        proj.rayDir(sx, sy, rayD)
        val ox = proj.camX; val oy = proj.camY; val oz = proj.camZ
        var best = -1
        var bestT = Float.MAX_VALUE
        for ((i, p) in sc.items.withIndex()) {
            val def = Catalog.item(p.itemId) ?: continue
            val (fw, fd) = itemDims(def, p.rot)
            val top = (def.parts.maxOfOrNull { it.z1 } ?: 1f).coerceAtLeast(.3f)
            var t0 = 0f; var t1 = Float.MAX_VALUE
            val lo = floatArrayOf(p.x.toFloat(), p.y.toFloat(), 0f)
            val hi = floatArrayOf((p.x + fw).toFloat(), (p.y + fd).toFloat(), top)
            val o = floatArrayOf(ox, oy, oz)
            var hit = true
            for (a in 0 until 3) {
                val dv = rayD[a]
                if (abs(dv) < 1e-6f) { if (o[a] < lo[a] || o[a] > hi[a]) { hit = false; break }; continue }
                var ta = (lo[a] - o[a]) / dv; var tb = (hi[a] - o[a]) / dv
                if (ta > tb) { val tt = ta; ta = tb; tb = tt }
                t0 = max(t0, ta); t1 = min(t1, tb)
                if (t0 > t1) { hit = false; break }
            }
            if (hit && t0 < bestT) { bestT = t0; best = i }
        }
        return best
    }

    private val tagText = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
    }
    private val tagFill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val tagPt = FloatArray(3)
    private val tagRect = android.graphics.RectF()

    private val tagOrder = ArrayList<Int>()
    private val placedTags = ArrayList<android.graphics.RectF>()

    /**
     * Price tags over everything on display in a walk-in store, plus signs for the checkout and the carts.
     * Nearest first; a tag that would overlap one already drawn is left out (the one you're looking at always shows).
     */
    private fun priceTags(canvas: Canvas, sc: InteriorScene, density: Float) {
        tagText.textSize = 10f * density
        tagOrder.clear()
        for (i in sc.items.indices) if (i in sc.forSale || i == sc.registerIndex || i == sc.rackIndex) tagOrder += i
        tagOrder.sortWith(compareBy({ it != sc.inspect }, { it != sc.registerIndex && it != sc.rackIndex }, { val p = sc.items[it]; key(p.x + .5f, p.y + .5f) }))
        placedTags.clear()
        for (i in tagOrder) {
            val p = sc.items[i]
            val sale = sc.forSale[i]
            val text: String
            val bg: Int
            when {
                sale != null -> {
                    val def = Catalog.item(sale) ?: continue
                    val price = e.moneyWhole(def.price)
                    text = if (i == sc.inspect) "${def.name} · $price" else price
                    bg = if (i == sc.inspect) c(0xFF2F80ED) else c(0xFFF2C94C)
                }
                i == sc.registerIndex -> { text = "PAY HERE"; bg = c(0xFF27AE60) }
                else -> { text = "FREE CARTS"; bg = c(0xFF2F80ED) }
            }
            val def = Catalog.item(p.itemId) ?: continue
            val (fw, fd) = itemDims(def, p.rot)
            val top = (def.parts.maxOfOrNull { it.z1 } ?: 1f) + .25f
            if (!proj.project(p.x + fw / 2f, p.y + fd / 2f, top, tagPt)) continue
            val tw = tagText.measureText(text) + 9f * density
            val th = 14f * density
            tagRect.set(tagPt[0] - tw / 2, tagPt[1] - th, tagPt[0] + tw / 2, tagPt[1])
            if (placedTags.any { android.graphics.RectF.intersects(it, tagRect) }) continue
            placedTags += android.graphics.RectF(tagRect)
            tagFill.color = 0x55000000
            canvas.drawRoundRect(tagRect.left + density, tagRect.top + 1.5f * density, tagRect.right + density, tagRect.bottom + 1.5f * density, 4f * density, 4f * density, tagFill)
            tagFill.color = bg
            canvas.drawRoundRect(tagRect, 4f * density, 4f * density, tagFill)
            tagText.color = if (bg == c(0xFFF2C94C)) c(0xFF1D2228) else 0xFFFFFFFF.toInt()
            canvas.drawText(text, tagPt[0], tagPt[1] - 3.8f * density, tagText)
        }
    }

    /** The back room wall's y while drawing (-1 for none). */
    private var wallY = -1f

    /** Draw order key: distance from the camera, with everything beyond the back room wall pushed to the front of the queue. */
    private fun key(x: Float, y: Float): Float {
        val dx = x - proj.camX; val dy = y - proj.camY
        val dist = dx * dx + dy * dy
        return if (wallY > 0f && (y < wallY) != (proj.camY < wallY)) dist + BEYOND else dist
    }

    private fun add(type: Int, idx: Int, key: Float) {
        if (dCount >= maxDraw) return
        dType[dCount] = type; dIdx[dCount] = idx; dKey[dCount] = key; dCount++
    }

    private fun trimOf(wall: Int) = scene.mix(wall, 0xFF000000.toInt(), .38f)

    /** The lower part of the walls, below the chair rail: a shade deeper than the paint above. */
    private fun wainscotOf(wall: Int) = scene.mix(wall, 0xFF000000.toInt(), .16f)
    private fun railOf(wall: Int) = scene.mix(wall, 0xFFFFFFFF.toInt(), .38f)

    /**
     * Light that would be baked into the room: soft shade where the floor meets the walls and under the furniture,
     * and warm pools under the ceiling lights (cooler tubes in the back room).
     */
    private fun bakedLight(sc: InteriorScene) {
        val w = sc.w.toFloat(); val d = sc.d.toFloat()
        val black = 0xFF000000.toInt()
        // three overlapping bands darken toward each wall
        for (k in 0..2) {
            val r = .18f + k * .22f
            val a = scene.withAlpha(black, .085f)
            scene.groundRect(0f, 0f, w, r, .005f, a)
            scene.groundRect(0f, 0f, r, d, .005f, a)
            scene.groundRect(w - r, 0f, w, d, .005f, a)
            scene.groundRect(0f, d - r, sc.doorX - 1f, d, .005f, a)
            scene.groundRect(sc.doorX + 1f, d - r, w, d, .005f, a)
            sc.backRoom?.let { room ->
                val y = room.depth.toFloat()
                scene.groundRect(0f, y - r * .7f, room.doorX.toFloat(), y + r * .7f, .005f, a)
                scene.groundRect((room.doorX + room.doorW).toFloat(), y - r * .7f, w, y + r * .7f, .005f, a)
            }
        }
        // contact shadows: a soft dark footprint under every piece of furniture
        for (p in sc.items) {
            val def = Catalog.item(p.itemId) ?: continue
            if (def.id == "rug" || def.id == "dance_floor") continue
            val (fw, fd) = itemDims(def, p.rot)
            val x0 = p.x.toFloat(); val y0 = p.y.toFloat(); val x1 = x0 + fw; val y1 = y0 + fd
            scene.groundRect(x0 - .06f, y0 - .06f, x1 + .06f, y1 + .06f, .006f, scene.withAlpha(black, .1f))
            scene.groundRect(x0 + .08f, y0 + .08f, x1 - .08f, y1 - .08f, .0065f, scene.withAlpha(black, .12f))
        }
        // pools of light under the ceiling fittings
        scene.glow = true
        val room = sc.backRoom
        var y = 2f
        while (y < d - .5f) {
            var x = 2f
            while (x < w - .5f) {
                val back = room != null && room.contains(y.toInt())
                val col = if (back) LIGHT_COOL else LIGHT_WARM
                scene.glowDisc(x, y, .007f, 1.9f, col, .17f)
                x += 4f
            }
            y += 4f
        }
        scene.glow = false
    }

    /** A wall light: a small brass fitting with a glowing shade and its wash of light up and down the wall. */
    private fun sconce(x: Float, y: Float, nx: Float, ny: Float) {
        val ax = -ny; val ay = nx
        val brass = c(0xFFB08D57)
        scene.box(min(x, x + nx * .08f) - .05f * abs(ax), min(y, y + ny * .08f) - .05f * abs(ay), 1.95f,
            max(x, x + nx * .08f) + .05f * abs(ax), max(y, y + ny * .08f) + .05f * abs(ay), 2.15f, brass, brass)
        scene.glow = true
        val sx = x + nx * .16f; val sy = y + ny * .16f
        scene.box(sx - .13f * abs(ax) - .08f * abs(nx), sy - .13f * abs(ay) - .08f * abs(ny), 2.05f,
            sx + .13f * abs(ax) + .08f * abs(nx), sy + .13f * abs(ay) + .08f * abs(ny), 2.32f, c(0xFFFFF0C8), c(0xFFFFE3A0))
        val wx = x + nx * .012f; val wy = y + ny * .012f
        scene.quad(wx - ax * .2f, wy - ay * .2f, 2.32f, wx + ax * .2f, wy + ay * .2f, 2.32f, wx + ax * .65f, wy + ay * .65f, 2.95f, wx - ax * .65f, wy - ay * .65f, 2.95f,
            scene.withAlpha(LIGHT_WARM, .2f))
        scene.quad(wx - ax * .2f, wy - ay * .2f, 2.05f, wx + ax * .2f, wy + ay * .2f, 2.05f, wx + ax * .45f, wy + ay * .45f, 1.55f, wx - ax * .45f, wy - ay * .45f, 1.55f,
            scene.withAlpha(LIGHT_WARM, .14f))
        scene.glow = false
    }

    private fun wallN(sc: InteriorScene, color: Int, h: Float) {
        scene.box(-.2f, -.2f, 0f, sc.w + .2f, 0f, h, WALL_TOP, color)
        if (h <= 1f) return
        val day = e.daylight()
        val trim = trimOf(color)
        // baseboard, wainscoting up to a chair rail, and crown molding
        val wains = wainscotOf(color); val rail = railOf(color)
        scene.box(0f, 0f, .14f, sc.w.toFloat(), .025f, .92f, wains, wains)
        scene.box(0f, 0f, .92f, sc.w.toFloat(), .055f, .98f, rail, rail)
        scene.box(0f, 0f, 0f, sc.w.toFloat(), .05f, .14f, trim, trim)
        scene.box(0f, 0f, h - .12f, sc.w.toFloat(), .04f, h, scene.mix(color, 0xFFFFFFFF.toInt(), .3f), scene.mix(color, 0xFFFFFFFF.toInt(), .3f))
        // framed windows with sills; daylight falls on the floor in front of them
        val sky = scene.mix(c(0xFF1C2A3A), c(0xFF9FC6E6), day)
        val frame = c(0xFFE9E6DF)
        var x = 1.5f
        while (x + 1.4f < sc.w - .5f) {
            scene.quad(x, .02f, 1.1f, x + 1.4f, .02f, 1.1f, x + 1.4f, .02f, 2.5f, x, .02f, 2.5f, scene.lit(sky, .95f))
            if (day > .3f) {
                // a few buildings across the street, seen through the glass
                scene.quad(x + .1f, .025f, 1.1f, x + .6f, .025f, 1.1f, x + .6f, .025f, 1.9f, x + .1f, .025f, 1.9f, scene.lit(c(0xFF7D8A99), .9f))
                scene.quad(x + .7f, .025f, 1.1f, x + 1.3f, .025f, 1.1f, x + 1.3f, .025f, 1.6f, x + .7f, .025f, 1.6f, scene.lit(c(0xFF8E7F72), .9f))
            }
            scene.box(x - .08f, 0f, 1.02f, x + 1.48f, .05f, 1.1f, frame, frame)
            scene.box(x - .08f, 0f, 2.5f, x + 1.48f, .05f, 2.58f, frame, frame)
            scene.box(x - .08f, 0f, 1.1f, x, .05f, 2.5f, frame, frame)
            scene.box(x + 1.4f, 0f, 1.1f, x + 1.48f, .05f, 2.5f, frame, frame)
            scene.box(x + .67f, 0f, 1.1f, x + .73f, .04f, 2.5f, frame, frame)
            scene.box(x, 0f, 1.77f, x + 1.4f, .04f, 1.83f, frame, frame)
            scene.box(x - .12f, 0f, .96f, x + 1.52f, .16f, 1.02f, frame, frame)
            if (day > .2f) {
                val sun = scene.withAlpha(c(0xFFFFF4D6), .16f * day)
                scene.quad(x, 0f, .006f, x + 1.4f, 0f, .006f, x + 1.7f, 1.7f, .006f, x + .3f, 1.7f, .006f, sun)
            }
            x += 3.5f
        }
        // wall lights between the windows
        var lx = 1.5f + 1.4f + 1.05f
        while (lx < sc.w - 1f) { sconce(lx, 0f, 0f, 1f); lx += 3.5f }
        // light switch and outlets
        scene.box(1.0f, 0f, 1.2f, 1.12f, .03f, 1.38f, c(0xFFF5F5F5), c(0xFFF5F5F5))
        var ox = 3.2f
        while (ox < sc.w - 1f) { scene.box(ox, 0f, .3f, ox + .1f, .03f, .42f, c(0xFFF5F5F5), c(0xFFF5F5F5)); ox += 4.5f }
    }

    private fun wallS(sc: InteriorScene, color: Int, h: Float) {
        scene.box(-.2f, sc.d.toFloat(), 0f, sc.doorX - 1f, sc.d + .2f, h, WALL_TOP, color)
        scene.box(sc.doorX + 1f, sc.d.toFloat(), 0f, sc.w + .2f, sc.d + .2f, h, WALL_TOP, color)
        val trim = trimOf(color)
        val d = sc.d.toFloat()
        if (h > 1f) {
            scene.box(0f, d - .05f, 0f, sc.doorX - 1f, d, .14f, trim, trim)
            scene.box(sc.doorX + 1f, d - .05f, 0f, sc.w.toFloat(), d, .14f, trim, trim)
            // the wall above the door, its frame and a lit exit sign
            scene.box(sc.doorX - 1f, d, 2.45f, sc.doorX + 1f, d + .2f, h, WALL_TOP, color)
            scene.box(sc.doorX - .35f, d - .06f, 2.55f, sc.doorX + .35f, d, 2.78f, c(0xFF27AE60), c(0xFF1E8449))
        }
        // door frame
        val fh = if (h > 1f) 2.45f else h
        scene.box(sc.doorX - 1.1f, d - .06f, 0f, sc.doorX - 1f, d + .26f, fh, trim, trim)
        scene.box(sc.doorX + 1f, d - .06f, 0f, sc.doorX + 1.1f, d + .26f, fh, trim, trim)
        if (h > 1f) scene.box(sc.doorX - 1.1f, d - .06f, 2.35f, sc.doorX + 1.1f, d + .26f, 2.45f, trim, trim)
    }

    private fun wallW(sc: InteriorScene, color: Int, h: Float) {
        scene.box(-.2f, -.2f, 0f, 0f, sc.d + .2f, h, WALL_TOP, color)
        if (h <= 1f) return
        val trim = trimOf(color)
        val wains = wainscotOf(color); val rail = railOf(color)
        scene.box(0f, 0f, .14f, .025f, sc.d.toFloat(), .92f, wains, wains)
        scene.box(0f, 0f, .92f, .055f, sc.d.toFloat(), .98f, rail, rail)
        var ly = 2.5f
        while (ly < sc.d - 1.5f) { sconce(0f, ly, 1f, 0f); ly += 4f }
        scene.box(0f, 0f, 0f, .05f, sc.d.toFloat(), .14f, trim, trim)
        scene.box(0f, 0f, h - .12f, .04f, sc.d.toFloat(), h, scene.mix(color, 0xFFFFFFFF.toInt(), .3f), scene.mix(color, 0xFFFFFFFF.toInt(), .3f))
    }

    private fun wallE(sc: InteriorScene, color: Int, h: Float) {
        scene.box(sc.w.toFloat(), -.2f, 0f, sc.w + .2f, sc.d + .2f, h, WALL_TOP, color)
        if (h <= 1f) return
        val trim = trimOf(color)
        val w = sc.w.toFloat()
        val wains = wainscotOf(color); val rail = railOf(color)
        scene.box(w - .025f, 0f, .14f, w, sc.d.toFloat(), .92f, wains, wains)
        scene.box(w - .055f, 0f, .92f, w, sc.d.toFloat(), .98f, rail, rail)
        var ly = 2.5f
        while (ly < sc.d - 1.5f) { sconce(w, ly, -1f, 0f); ly += 4f }
        scene.box(w - .05f, 0f, 0f, w, sc.d.toFloat(), .14f, trim, trim)
        scene.box(w - .04f, 0f, h - .12f, w, sc.d.toFloat(), h, scene.mix(color, 0xFFFFFFFF.toInt(), .3f), scene.mix(color, 0xFFFFFFFF.toInt(), .3f))
    }

    /** Draws a furniture model at grid position (x, y) with rotation. */
    fun drawItem(def: FurnitureDef, x: Float, y: Float, rot: Int, alpha: Float) = parts.draw(def, x, y, rot, alpha)

    private fun person(v: com.hustlecity.game.engine.Person, staff: Boolean) {
        // shoppers hold what they picked (once it has reached their hands), then a paper bag after paying
        val held = if (!staff && v.heldIn <= 0f && !v.bagged) v.held else 0
        people.draw(v.x, v.y, v.z, v.heading, v.phase, v.moving, v.shirt, PersonPainter.PANTS[v.pants % PersonPainter.PANTS.size], v.skin,
            PersonPainter.HAIR[v.hair % PersonPainter.HAIR.size], pose = v.pose, apron = if (staff) c(0xFF2C3E50) else 0,
            carry = v.carry, load = if (v.carry > 0) 1 else 0, held = held, bag = !staff && v.bagged, look = v.look,
            warm = !staff && e.hasGame && e.weather.temperature() < 9.0)
    }

    private val takeFrom = FloatArray(3)

    /** Items on their way from a shelf into this customer's hands: a little arc from the slot to the right hand. */
    private fun takes(sc: InteriorScene, v: com.hustlecity.game.engine.Person) {
        val shelf = sc.shelf ?: return
        val list = shelf.takes
        for (k in list.indices) {
            val t = list[k]
            if (t.person !== v) continue
            val p = sc.items.getOrNull(t.item) ?: continue
            val def = Catalog.item(p.itemId) ?: continue
            val lay = shelf.layoutOf(t.item) ?: continue
            stock.slotWorld(def, p, lay, t.slot, takeFrom)
            val u = (t.t / ShelfStock.TAKE_TIME).coerceIn(0f, 1f)
            // the right hand: a little ahead of and to the right of the body, at hip height
            val fx = kotlin.math.sin(v.heading); val fy = -kotlin.math.cos(v.heading)
            val hx = v.x + fx * .12f - fy * .27f; val hy = v.y + fy * .12f + fx * .27f; val hz = v.z + .86f
            val x = takeFrom[0] + (hx - takeFrom[0]) * u
            val y = takeFrom[1] + (hy - takeFrom[1]) * u
            val z = takeFrom[2] + (hz - takeFrom[2]) * u + sin(u * Math.PI.toFloat()) * .18f
            val col = Catalog.product(t.pid)?.color ?: c(0xFFC8A165)
            scene.box(x - .05f, y - .05f, z - .06f, x + .05f, y + .05f, z + .06f, scene.mix(col, 0xFFFFFFFF.toInt(), .2f), col)
        }
    }

    /**
     * The back room's wall: cut down so you can see in (like the walls nearest the camera), with baseboards, a door
     * frame around its doorway and a red STAFF ONLY sign over it. It runs along the tile edge at y = depth.
     */
    private fun backWall(sc: InteriorScene, room: com.hustlecity.game.engine.BackRoom, color: Int) {
        val y = room.depth.toFloat()
        val t = .07f
        val h = if (sc.showWalls) PARTITION_H else .35f
        val trim = trimOf(color)
        val d0 = room.doorX.toFloat(); val d1 = (room.doorX + room.doorW).toFloat()
        scene.material = Mat.PLASTER
        if (d0 > 0f) scene.box(0f, y - t, 0f, d0, y + t, h, WALL_TOP, color)
        if (d1 < sc.w) scene.box(d1, y - t, 0f, sc.w.toFloat(), y + t, h, WALL_TOP, color)
        scene.material = Mat.PLAIN
        for (side in -1..1 step 2) {
            val a = y + side * t; val b = y + side * (t + .03f)
            if (d0 > 0f) scene.box(0f, minOf(a, b), 0f, d0, maxOf(a, b), .14f, trim, trim)
            if (d1 < sc.w) scene.box(d1, minOf(a, b), 0f, sc.w.toFloat(), maxOf(a, b), .14f, trim, trim)
        }
        // the door frame, taller than the cut-away wall so the doorway reads as one
        val fh = if (sc.showWalls) BACK_DOOR_H else h
        val frame = c(0xFFE9E6DF)
        scene.box(d0 - .1f, y - t - .03f, 0f, d0, y + t + .03f, fh, frame, frame)
        scene.box(d1, y - t - .03f, 0f, d1 + .1f, y + t + .03f, fh, frame, frame)
        if (!sc.showWalls) return
        scene.box(d0 - .1f, y - t - .03f, fh - .1f, d1 + .1f, y + t + .03f, fh, frame, frame)
        // STAFF ONLY: a red sign over the doorway (its lettering is drawn on top, see staffSign)
        val cx = (d0 + d1) / 2
        val sw = minOf(d1 - d0 + .1f, 1.1f) / 2
        scene.box(cx - sw, y - .025f, fh, cx + sw, y + .025f, fh + .28f, STAFF_RED, STAFF_RED)
        scene.box(cx - sw + .04f, y + .025f, fh + .04f, cx + sw - .04f, y + .03f, fh + .24f, c(0xFFD9483B), c(0xFFD9483B))
    }

    private val signText = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
        color = 0xFFFFFFFF.toInt()
    }
    private val signA = FloatArray(3); private val signB = FloatArray(3)

    /** The lettering on the back room's STAFF ONLY sign, and in the designer a label over the room. */
    private fun staffSign(canvas: Canvas, sc: InteriorScene, room: com.hustlecity.game.engine.BackRoom, density: Float) {
        val y = room.depth.toFloat()
        if (e.ui.designer) {
            if (proj.project(sc.w / 2f, room.depth / 2f, .05f, signA)) {
                signText.textSize = 12f * density
                signText.color = 0xCCFFFFFF.toInt()
                canvas.drawText("BACK ROOM · STAFF ONLY", signA[0], signA[1], signText)
                signText.color = 0xFFFFFFFF.toInt()
            }
        }
        if (!sc.showWalls) return
        val cx = room.doorX + room.doorW / 2f
        val sw = minOf(room.doorW + .1f, 1.1f) / 2 - .06f
        val side = if (proj.camY > y) .04f else -.04f
        if (!proj.project(cx - sw, y + side, BACK_DOOR_H + .14f, signA) || !proj.project(cx + sw, y + side, BACK_DOOR_H + .14f, signB)) return
        // seen from the back room the sign's ends swap sides on screen: read it left to right either way
        val flip = signB[0] < signA[0]
        val ax = if (flip) signB[0] else signA[0]; val ay = if (flip) signB[1] else signA[1]
        val bx = if (flip) signA[0] else signB[0]; val by = if (flip) signA[1] else signB[1]
        val width = kotlin.math.hypot(bx - ax, by - ay)
        val size = width / 6.4f
        if (size < 4.5f) return
        signText.textSize = size
        val mx = (ax + bx) / 2; val my = (ay + by) / 2
        canvas.save()
        canvas.rotate(Math.toDegrees(kotlin.math.atan2((by - ay).toDouble(), (bx - ax).toDouble())).toFloat(), mx, my)
        canvas.drawText("STAFF ONLY", mx, my + size * .36f, signText)
        canvas.restore()
    }

    /** Small deterministic hash in [0, 1) so the dirt pattern on a tile stays put from frame to frame. */
    private fun hf(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFFFF) / 16777216f
    }

    /**
     * Procedural dirt: scuffs and footprints on lightly used tiles, stains as they get worse,
     * and litter (wrappers, cups) on the dirtiest ones. Near the door it turns to muddy prints.
     */
    private fun drawDirt(sc: InteriorScene, b: com.hustlecity.game.model.Business) {
        val dirt = b.dirt
        if (dirt.size != sc.w * sc.d) return
        val mudBrown = c(0xFF5A4632); val grime = c(0xFF3A3632)
        for (t in dirt.indices) {
            val v = dirt[t]
            if (v < 3f) continue
            val x0 = (t % sc.w).toFloat(); val y0 = (t / sc.w).toFloat()
            val nearDoor = kotlin.math.abs(x0 + .5f - sc.doorX) < 2.5f && y0 >= sc.d - 3
            val k = (v / 100f).coerceIn(0f, 1f)
            // footprints and scuffs
            val prints = 1 + (k * 5).toInt()
            for (n in 0 until prints) {
                val fx = x0 + .12f + hf(t, n * 3) * .76f
                val fy = y0 + .12f + hf(t, n * 3 + 1) * .76f
                val rot = hf(t, n * 3 + 2) > .5f
                val col = scene.withAlpha(if (nearDoor) mudBrown else grime, .1f + .32f * k)
                scene.disc(fx, fy, .006f, if (rot) .11f else .06f, if (rot) .06f else .11f, 7, col)
            }
            // stains spread as the floor gets dirtier
            if (v > 22f) {
                val cx = x0 + .25f + hf(t, 40) * .5f; val cy = y0 + .25f + hf(t, 41) * .5f
                val r = .12f + .3f * k * (.6f + .4f * hf(t, 42))
                val m = 7
                for (q in 0 until m) {
                    val a = q / m.toFloat() * 6.2832f
                    val rr = r * (.65f + .5f * hf(t, 50 + q))
                    px[q] = cx + kotlin.math.cos(a) * rr; py[q] = cy + kotlin.math.sin(a) * rr; pz[q] = .008f
                }
                val tone = when ((hf(t, 43) * 3).toInt()) { 0 -> c(0xFF6B4A2F); 1 -> c(0xFF4E4A44); else -> c(0xFF7A6A3A) }
                scene.poly(m, px, py, pz, scene.withAlpha(scene.lit(if (nearDoor) mudBrown else tone, 1f), .22f + .3f * k))
            }
            // litter on the worst tiles
            if (v > 55f) {
                val lx = x0 + .2f + hf(t, 60) * .6f; val ly = y0 + .2f + hf(t, 61) * .6f
                val paper = when ((hf(t, 62) * 4).toInt()) { 0 -> c(0xFFF2F2F2); 1 -> c(0xFFEB5757); 2 -> c(0xFFF2C94C); else -> c(0xFF56CCF2) }
                scene.box(lx - .07f, ly - .05f, 0f, lx + .07f, ly + .05f, .015f, paper, paper)
                if (v > 75f) {
                    val cx2 = x0 + .2f + hf(t, 63) * .6f; val cy2 = y0 + .2f + hf(t, 64) * .6f
                    scene.box(cx2 - .04f, cy2 - .04f, 0f, cx2 + .04f, cy2 + .04f, .11f, c(0xFFF5F5F5), c(0xFFC0392B))
                }
            }
        }
    }

    private fun drawCar(x: Float, y: Float, heading: Float, len: Float, wid: Float, hgt: Float, color: Int, style: Int) =
        cars.draw(x, y, heading, len, wid, hgt, color, style, true)
}
