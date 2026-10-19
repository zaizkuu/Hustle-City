package com.hustlecity.game.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.c
import com.hustlecity.game.engine.AppId
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.engine.carryPose
import com.hustlecity.game.engine.SHIRT_COLORS
import com.hustlecity.game.engine.SKIN_TONES
import com.hustlecity.game.ui.icons.IconCanvas
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.Building
import com.hustlecity.game.world.City
import com.hustlecity.game.world.dist
import com.hustlecity.game.render.gpu.GpuFrame
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Draws the city exterior (street view and map view). */
private fun wrapAngle(a: Float): Float {
    var x = a
    while (x > Math.PI) x -= (2 * Math.PI).toFloat()
    while (x < -Math.PI) x += (2 * Math.PI).toFloat()
    return x
}

class WorldRenderer(private val e: GameEngine) {
    val proj = Projector()
    private val batch = RenderBatch()
    val scene = Scene3D(proj, batch)
    private val bbox = FloatArray(4)
    private val tmp = FloatArray(3)

    private val maxDraw = 6000
    private var dCount = 0
    private val dType = IntArray(maxDraw)
    private val dIdx = IntArray(maxDraw)
    private val dKey = FloatArray(maxDraw)
    private val order = IntArray(maxDraw)

    /**
     * Signs over buildings, kept in world space and projected when the overlay is drawn, so cached buildings keep
     * their signs. Size is focal × [k] / depth, capped at [max]; smaller than [min] and it's hidden.
     */
    private class Label(
        var text: String, var x: Float, var y: Float, var z: Float, var k: Float, var max: Float, var min: Float, var shift: Float,
        var color: Int, var bg: Int, var icon: String? = null,
    )
    private val labels = ArrayList<Label>()
    private val staticLabels = ArrayList<Label>()
    private var labelOut = labels
    private val labelPool = ArrayList<Label>()

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    private val rect = RectF()
    private val people = PersonPainter(scene)
    private val painter = BuildingPainter(this, e)
    private val cars = CarPainter(scene)
    private val trees = TreePainter(scene)
    private var propsCache: StreetProps? = null
    private val props: StreetProps
        get() = propsCache?.takeIf { it.city === e.city } ?: StreetProps(e.city).also { propsCache = it }
    private var featCache: CityFeatures? = null
    /** Landmarks, elevated railways, bridges, quays and city walls. */
    private val features: CityFeatures
        get() = featCache?.takeIf { it.city === e.city } ?: CityFeatures(e.city).also { featCache = it }
    private val addFeature: (Int, Float) -> Unit = { part, key -> add(12, part, key) }
    private val addTrainCar: (Int, Float, Float) -> Unit = { car, x, y -> add(13, car, keyFor(x, y)) }

    // weather particles in screen space: x, y, speed, size
    private val pX = FloatArray(420); private val pY = FloatArray(420); private val pV = FloatArray(420); private val pS = FloatArray(420)
    private var particlesInit = false
    private var lastT = 0f
    private val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val snowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val splashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val flashPaint = Paint()

    // per-frame lookups by building id (avoid linear scans in the draw loops)
    private val bizByBuilding = HashMap<Int, com.hustlecity.game.model.Business>()
    private val rivalByBuilding = HashMap<Int, com.hustlecity.game.model.RivalBusiness>()
    private val rentedBuildings = HashSet<Int>()

    private fun rebuildLookups() {
        val s = e.state
        bizByBuilding.clear(); rivalByBuilding.clear(); rentedBuildings.clear()
        for (b in s.businesses) bizByBuilding[b.buildingId] = b
        for (r in s.rivalBusinesses) rivalByBuilding[r.buildingId] = r
        for (r in s.rentals) rentedBuildings += r.buildingId
    }

    private fun vacant(b: Building) = b.rentable && b.placeId == null && b.id !in rentedBuildings && b.id !in rivalByBuilding

    private var focusX = 0f
    private var focusY = 0f
    private var shadowDX = 0f
    private var shadowDY = 0f
    private var playerKey = 0f
    private var mapMode = false
    private var density = 2f

    companion object {
        val WATER = c(0xFF27445C)
        val SIDEWALK = c(0xFFB9BCC1)
        val ASPHALT = c(0xFF4A4D53)
        val LOT = c(0xFF8E9297)
        val GRASS = c(0xFF6DA35C)
        val PATH = c(0xFFD8CFB8)
        val YELLOW = c(0xFFE8C547)
        val WHITE_LINE = c(0xFFE6E8EA)
        val CURB = c(0xFFD3D0C8)
        val GLASS = c(0xFF3C5266)
        val GLASS_NIGHT = c(0xFF1E2A36)
        val LIT = c(0xFFFFE3A0)
        private val POOL_OUTER = c(0xFFFFB85C)
        private val POOL_INNER = c(0xFFFFE3A0)
        val GLASS_DARK = c(0xFF26313D)
        val TRUNK = c(0xFF6B4A32)
        val TREE = intArrayOf(c(0xFF4C8C3F), c(0xFF5E9E45), c(0xFF3F7A3A))
        val AUTUMN = intArrayOf(c(0xFFD9822B), c(0xFFC0392B), c(0xFFE6B422), c(0xFFB5651D), c(0xFFD35400))
        val PANTS = c(0xFF34383F)
        const val PITCH_STREET = 58f
        private const val ALL = 0
        private const val STATIC = 1
        private const val DYNAMIC = 2
        const val PITCH_MAP = 62f
    }

    private fun label(text: String, x: Float, y: Float, z: Float, k: Float, max: Float, min: Float, shift: Float, color: Int, bg: Int = 0, icon: String? = null) {
        val l = if (labelPool.isNotEmpty()) labelPool.removeAt(labelPool.size - 1) else Label("", 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0, 0)
        l.text = text; l.x = x; l.y = y; l.z = z; l.k = k; l.max = max; l.min = min; l.shift = shift; l.color = color; l.bg = bg; l.icon = icon
        labelOut += l
    }

    private fun recycle(list: ArrayList<Label>) { labelPool += list; list.clear() }

    fun setupCamera(w: Int, h: Int) {
        val cam = e.camera
        mapMode = e.ui.app == AppId.MAPS
        if (mapMode) {
            proj.tx = cam.mapX; proj.ty = cam.mapY; proj.dist = cam.mapDist
            proj.pitch = Math.toRadians(PITCH_MAP.toDouble()).toFloat(); proj.yaw = 0f
        } else {
            proj.tx = cam.tx; proj.ty = cam.ty; proj.dist = cam.dist
            proj.pitch = Math.toRadians(PITCH_STREET.toDouble()).toFloat(); proj.yaw = cam.yaw
        }
        proj.near = .8f
        // while driving, keep the car above the dashboard
        proj.update(w.toFloat(), h.toFloat(), if (mapMode) .5f else if (e.hasGame && e.state.player.driving >= 0) .42f else .56f)
    }

    /** Draws the city; with [gpu] the 3D goes into that frame for the GPU renderer and [canvas] only gets the overlays. */
    fun draw(canvas: Canvas, w: Int, h: Int, density: Float, gpu: com.hustlecity.game.render.gpu.GpuFrame? = null) {
        if (!e.ready) return
        this.density = density
        scene.gpu = gpu
        setupCamera(w, h)
        val day = e.daylight()
        scene.applyDaylight(if (mapMode) max(day, .75f) else day)
        val v = e.skyVis
        if (!mapMode) scene.applyWeather(v.cloud, v.rain, v.storm, v.flash * .6f)
        scene.fog = if (mapMode) 0f else max(max(v.fog, v.rain * .3f), max(v.snow * .35f, v.storm * .2f))
        scene.fogStart = proj.dist * (if (v.fog > .3f) .3f else .7f)
        scene.fogEnd = proj.dist * (if (v.fog > .3f) 1.5f else 3f)
        scene.snow = if (e.hasGame) e.state.snowCover.toFloat() else 0f
        // sun moves east -> south -> west over the day
        val hour = e.state.hour + e.state.minuteOfHour / 60f
        val t = ((hour - 6f) / 12f).coerceIn(0f, 1f)
        val ang = (t * Math.PI).toFloat()
        scene.sunX = cos(ang); scene.sunY = sin(ang).coerceAtLeast(.25f)
        val len = .45f + 1.3f * (1f - sin(ang))
        shadowDX = -scene.sunX * len; shadowDY = -scene.sunY * len

        if (gpu != null) {
            gpu.clear()
            gpu.setCamera(proj)
            com.hustlecity.game.render.gpu.GpuLighting.city(gpu, hour, scene.daylight, if (mapMode) 0f else v.cloud, if (mapMode) 0f else v.rain,
                if (mapMode) 0f else v.storm, if (mapMode) 0f else v.flash, if (mapMode) 0f else v.wet, scene.snow, scene.fogColor, scene.fog,
                scene.fogStart, scene.fogEnd, mapMode)
            if (mapMode) gpu.clearColor = WATER
        } else canvas.drawColor(scene.mix(scene.lit(WATER, 1f), scene.lit(scene.fogColor, 1f), scene.fog * .7f))
        rebuildLookups()
        if (e.hasGame) { val f = e.focusPoint(); focusX = f.first; focusY = f.second }
        proj.visibleGround(bbox, 30f)
        if (gpu != null) {
            gpu.time = e.realTime
            // real sun shadows replace the painted ones when the view is small enough for a sharp shadow map
            val overcast = (v.cloud * .7f + v.rain * .25f + v.storm * .3f).coerceIn(0f, .85f)
            val wide = mapMode || bbox[2] - bbox[0] > 1200f || bbox[3] - bbox[1] > 1200f || e.settings.quality == 0
            val strength = if (wide) 0f else ((scene.daylight - .12f) / .88f).coerceIn(0f, 1f) * (1f - .8f * overcast) * .82f
            val size = e.settings.shadowMapSize
            gpu.setShadow(bbox[0], bbox[1], bbox[2], bbox[3], min(e.city.maxHeight, 140f), size.coerceAtLeast(16), if (size == 0) 0f else strength)
        }
        if (gpu == null) batch.begin(canvas)
        recycle(labels)
        labelOut = labels
        if (e.hasGame) playerKey = keyFor(e.state.player.x, e.state.player.y)
        if (gpu != null) {
            // the city that doesn't move is cached on the GPU; only traffic, people and the route are built every frame
            ensureStatic(gpu)
            scene.gpu = gpu
            labelOut = labels
            scene.groundPass = true
            drawRoute()
            scene.groundPass = false
            findOccluders(gpu)
            collectDrawables(DYNAMIC)
        } else {
            recycle(staticLabels)
            staticValid = false
            drawGround(true)
            collectDrawables(ALL)
        }
        sortDrawables()
        drawList()
        if (gpu == null) batch.flush()
        scene.gpu = null
        if (!mapMode) drawWeather(canvas, w, h)
        drawOverlays(canvas, w, h)
    }

    // ------------------------------------------------------------------ static cache (GPU)

    private val staticFrames = arrayOf(GpuFrame(), GpuFrame())
    private val rangeStart = arrayOf(IntArray(0), IntArray(0))
    private val rangeEnd = arrayOf(IntArray(0), IntArray(0))
    private val drawnIds = arrayOf(IntArray(0), IntArray(0))
    private val drawnCount = IntArray(2)
    private var building = 0
    private var published = -1
    private var staticValid = false
    private var staticCity: City? = null
    private var staticPass = false
    private val staticBox = FloatArray(4)
    private val viewBox = FloatArray(4)
    private var sCamX = 0f; private var sCamY = 0f; private var sYaw = 0f; private var sDist = 1f
    private var staticVersion = 0L
    private val occl = ArrayList<Int>()
    private val skipTmp = ArrayList<Long>()

    /** What static geometry depends on that the player changes on purpose: rebuilt right away when it changes. */
    private fun hardKey(): Long {
        val st = e.state
        var k = (if (mapMode) 1L else 0L) + e.settings.quality * 3
        k = k * 31 + e.ui.selected
        k = k * 31 + e.ui.mapFilters.hashCode()
        if (e.hasGame) {
            for (b in st.businesses) k = k * 31 + b.buildingId * 7 + (if (b.tempClosed) 1 else 0) + b.openHour * 3 + b.closeHour * 5 + b.typeId.hashCode()
            for (r in st.rentals) k = k * 31 + r.buildingId
            for (r in st.rivalBusinesses) k = k * 31 + r.buildingId * 3 + r.level
        }
        return k
    }

    /** What drifts with time (daylight, weather, season): rebuilt in the background. */
    private fun softKey(gpu: GpuFrame): Long {
        var k = (scene.daylight * 24f).roundToInt().toLong()
        k = k * 31 + (scene.snow * 10f).roundToInt()
        k = k * 31 + (if (mapMode) 0 else (e.skyVis.wet * 5f).roundToInt())
        k = k * 31 + (if (e.hasGame) e.state.hour else 0)
        k = k * 31 + e.weather.month()
        k = k * 31 + (if (gpu.shadowOn) 1 else 0)
        return k
    }

    private var sHard = 0L
    private var sSoft = 0L
    // a rebuild spread over several frames into the back buffer
    private var stageActive = false
    private var stageIdx = 0
    private var stageCount = 0
    private var stagePos = 0
    private val stageType = IntArray(maxDraw)
    private val stageId = IntArray(maxDraw)
    private val stageKeyArr = FloatArray(maxDraw)
    private var stageLabels = ArrayList<Label>()
    private val stageBox = FloatArray(4)
    private var stageCamX = 0f; private var stageCamY = 0f; private var stageYaw = 0f; private var stageDist = 1f
    private var stageHard = 0L; private var stageSoft = 0L

    private fun ensureStatic(gpu: GpuFrame) {
        val hard = hardKey(); val soft = softKey(gpu)
        val moved = dist(proj.camX, proj.camY, sCamX, sCamY)
        val far = if (mapMode) proj.dist * .25f else 34f
        val outside = bbox[0] < staticBox[0] || bbox[1] < staticBox[1] || bbox[2] > staticBox[2] || bbox[3] > staticBox[3]
        val turn = abs(wrapAngle(proj.yaw - sYaw)); val zoom = abs(proj.dist / sDist - 1f)
        val must = !staticValid || staticCity !== e.city || hard != sHard || moved > far || turn > .3f || zoom > .2f || outside
        if (must) {
            stageActive = false
            startStage(hard, soft)
            finishStage(Long.MAX_VALUE)
        } else {
            // close to needing one: start building the next cache now, a little each frame
            val nearEdge = bbox[0] - staticBox[0] < 25f || bbox[1] - staticBox[1] < 25f || staticBox[2] - bbox[2] < 25f || staticBox[3] - bbox[3] < 25f
            if (!stageActive && (soft != sSoft || moved > far * .5f || turn > .14f || zoom > .08f || nearEdge)) startStage(hard, soft)
            if (stageActive) finishStage(2_500_000L)
        }
        gpu.staticGeom = staticFrames[published]
    }

    /** Starts building the cache for the current view into the back buffer: the ground right away, the rest queued. */
    private fun startStage(hard: Long, soft: Long) {
        val idx = if (published == 0) 1 else 0
        val sf = staticFrames[idx]
        stageIdx = idx
        stageHard = hard; stageSoft = soft
        stageCamX = proj.camX; stageCamY = proj.camY; stageYaw = proj.yaw; stageDist = proj.dist
        // a margin around the view so small moves don't need a rebuild
        val mx = max(70f, (bbox[2] - bbox[0]) * .3f); val my = max(70f, (bbox[3] - bbox[1]) * .3f)
        stageBox[0] = bbox[0] - mx; stageBox[1] = bbox[1] - my; stageBox[2] = bbox[2] + mx; stageBox[3] = bbox[3] + my
        val n = e.city.buildings.size
        if (rangeStart[idx].size < n) { rangeStart[idx] = IntArray(n); rangeEnd[idx] = IntArray(n); drawnIds[idx] = IntArray(n) }
        drawnCount[idx] = 0
        recycle(stageLabels)
        synchronized(sf) {
            sf.clear()
            bbox.copyInto(viewBox); stageBox.copyInto(bbox)
            withStage { drawGround(false) }
            collectDrawables(STATIC)
            sortDrawables()
            viewBox.copyInto(bbox)
        }
        stageCount = dCount
        for (k in 0 until dCount) { val i = order[k]; stageType[k] = dType[i]; stageId[k] = dIdx[i]; stageKeyArr[k] = dKey[i] }
        stagePos = 0
        stageActive = true
    }

    /** Draws queued static items for up to [budgetNs], publishing the new cache when everything is in. */
    private fun finishStage(budgetNs: Long) {
        val sf = staticFrames[stageIdx]
        val t0 = System.nanoTime()
        synchronized(sf) {
            withStage {
                while (stagePos < stageCount) {
                    dCount = 1
                    dType[0] = stageType[stagePos]; dIdx[0] = stageId[stagePos]; dKey[0] = stageKeyArr[stagePos]; order[0] = 0
                    drawList()
                    stagePos++
                    if ((stagePos and 7) == 0 && System.nanoTime() - t0 > budgetNs) break
                }
            }
            if (stagePos < stageCount) return
            sf.version = ++staticVersion
        }
        published = stageIdx
        val t = staticLabels.toList()
        staticLabels.clear(); staticLabels.addAll(stageLabels)
        stageLabels.clear(); stageLabels.addAll(t); recycle(stageLabels)
        stageBox.copyInto(staticBox)
        sCamX = stageCamX; sCamY = stageCamY; sYaw = stageYaw; sDist = stageDist; sHard = stageHard; sSoft = stageSoft
        staticValid = true
        staticCity = e.city
        stageActive = false
        dCount = 0
    }

    /** Runs [block] drawing into the back buffer as part of the cache. */
    private inline fun withStage(block: () -> Unit) {
        val prevGpu = scene.gpu
        building = stageIdx
        scene.gpu = staticFrames[stageIdx]
        labelOut = stageLabels
        staticPass = true
        scene.cacheBuild = true
        try { block() } finally {
            scene.cacheBuild = false
            staticPass = false
            labelOut = labels
            scene.gpu = prevGpu
        }
    }

    /** Cached buildings standing between the camera and the player: skipped in the cache and drawn see-through. */
    private fun findOccluders(gpu: GpuFrame) {
        occl.clear()
        gpu.skipCount = 0
        val p = e.state.player
        if (mapMode || !e.hasGame || p.inBuilding >= 0 || published < 0) return
        val sx0 = min(proj.camX, focusX) - 8f; val sx1 = max(proj.camX, focusX) + 8f
        val sy0 = min(proj.camY, focusY) - 8f; val sy1 = max(proj.camY, focusY) + 8f
        val ids = drawnIds[published]
        skipTmp.clear()
        for (i in 0 until drawnCount[published]) {
            val b = e.city.buildings[ids[i]]
            if (b.height <= 5f || b.maxX < sx0 || b.minX > sx1 || b.maxY < sy0 || b.minY > sy1) continue
            if (keyForRect(b) >= playerKey || !e.occludes(proj, b, focusX, focusY, 1.2f)) continue
            occl += b.id
            val s0 = rangeStart[published][b.id]; val s1 = rangeEnd[published][b.id]
            if (s1 > s0) skipTmp += (s0.toLong() shl 32) or s1.toLong()
        }
        skipTmp.sort()
        if (gpu.skip.size < skipTmp.size * 2) gpu.skip = IntArray(skipTmp.size * 2 + 8)
        for ((k, v) in skipTmp.withIndex()) { gpu.skip[k * 2] = (v ushr 32).toInt(); gpu.skip[k * 2 + 1] = (v and 0xffffffffL).toInt() }
        gpu.skipCount = skipTmp.size
    }

    private fun drawList() {
        for (k in 0 until dCount) {
            val i = order[k]
            when (dType[i]) {
                0 -> {
                    val b = e.city.buildings[dIdx[i]]
                    if (staticPass) {
                        // remember where each building's solid geometry sits in the cache so it can be skipped later
                        val st = scene.gpu!!.streams[GpuFrame.OPAQUE]
                        rangeStart[building][b.id] = st.ni
                        drawBuilding(b)
                        rangeEnd[building][b.id] = st.ni
                        drawnIds[building][drawnCount[building]++] = b.id
                    } else drawBuilding(b)
                }
                1 -> drawTree(dIdx[i])
                2 -> e.traffic.cars.getOrNull(dIdx[i])?.let {
                    // some of the sedans are cabs, in the city's colors
                    val map = e.city.map
                    val taxi = it.style == 0 && dIdx[i] % map.taxiEvery == 0
                    val color = if (taxi) map.taxiColors[(dIdx[i] / map.taxiEvery) % map.taxiColors.size] else it.color
                    drawCar(it.x, it.y, it.heading, it.length, it.width, it.height, color, it.style, false,
                        taxi = taxi, braking = it.speed < it.cruise * .45f, accent = it.accent)
                }
                3 -> e.traffic.peds.getOrNull(dIdx[i])?.let { drawPed(it, dIdx[i]) }
                4 -> drawPlayer()
                5 -> e.state.vehicle(dIdx[i])?.let { v ->
                    if (e.state.player.driving != v.id && !mapMode) scene.disc(v.x, v.y, .03f, 2.6f, 2.6f, 14, scene.withAlpha(c(0xFF27AE60), .35f))
                    val driving = e.state.player.driving == v.id
                    drawCar(v.x, v.y, v.heading, v.def.length, v.def.width, v.def.height, if (v.color != 0) v.color else v.def.color, v.def.style, true,
                        braking = driving && e.input.brake)
                }
                6 -> drawTrafficLight(dIdx[i])
                7 -> drawParkedCar(dIdx[i])
                8 -> props.draw(scene, dIdx[i], dKey[i] < 55f * 55f, e.realTime)
                12 -> features.draw(scene, dIdx[i], e.realTime, scene.gpu == null, mapMode, if (e.hasGame) e.state.hour else 12)
                13 -> features.drawCar(scene, dIdx[i])
            }
        }
    }

    /** Rain streaks with splashes, drifting snowflakes and lightning, drawn over the city. */
    private fun drawWeather(canvas: Canvas, w: Int, h: Int) {
        val v = e.skyVis
        val dt = (e.realTime - lastT).coerceIn(0f, .1f)
        lastT = e.realTime
        val rnd = e.rnd
        if (!particlesInit) {
            for (i in pX.indices) { pX[i] = rnd.nextFloat() * w; pY[i] = rnd.nextFloat() * h; pV[i] = rnd.nextFloat(); pS[i] = rnd.nextFloat() }
            particlesInit = true
        }
        val d = density
        if (v.rain > .02f) {
            val n = (v.rain * pX.size).toInt()
            rainPaint.color = scene.withAlpha(0xFFD6E2EE.toInt(), .32f + .15f * v.storm)
            rainPaint.strokeWidth = 1.1f * d
            val slant = .22f + .25f * v.storm
            for (i in 0 until n) {
                val speed = (900f + pV[i] * 500f) * d / 2f
                val len = (14f + pS[i] * 16f) * d
                pY[i] += speed * dt
                pX[i] += speed * dt * slant
                if (pY[i] > h || pX[i] > w + 40) { pY[i] = -len - rnd.nextFloat() * 60f; pX[i] = rnd.nextFloat() * (w + 80f) - 80f }
                canvas.drawLine(pX[i], pY[i], pX[i] - len * slant, pY[i] - len, rainPaint)
                // a few splashes on the ground
                if (i % 9 == 0) {
                    val ph = (e.realTime * 3f + pS[i] * 7f) % 1f
                    val sx = (pS[i] * 9301f + pV[i] * 49297f) % w
                    val sy = h * .35f + ((pV[i] * 7919f + pS[i] * 104729f) % (h * .65f))
                    splashPaint.color = scene.withAlpha(0xFFE3ECF5.toInt(), (1f - ph) * .35f * v.rain)
                    splashPaint.strokeWidth = d
                    rect.set(sx - ph * 7f * d, sy - ph * 2.5f * d, sx + ph * 7f * d, sy + ph * 2.5f * d)
                    canvas.drawOval(rect, splashPaint)
                }
            }
        }
        if (v.snow > .02f) {
            val n = (v.snow * pX.size * .7f).toInt()
            snowPaint.color = scene.withAlpha(0xFFFFFFFF.toInt(), .8f)
            for (i in 0 until n) {
                val j = pX.size - 1 - i
                val speed = (40f + pV[j] * 70f) * d / 2f
                pY[j] += speed * dt
                pX[j] += sin(e.realTime * 1.3f + pS[j] * 9f) * 18f * d * dt
                if (pY[j] > h) { pY[j] = -5f; pX[j] = rnd.nextFloat() * w }
                canvas.drawCircle(pX[j], pY[j], (1.1f + pS[j] * 1.6f) * d, snowPaint)
            }
        }
        if (v.flash > .01f) {
            flashPaint.color = scene.withAlpha(0xFFF4F7FF.toInt(), v.flash * .45f)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), flashPaint)
        }
    }

    // ------------------------------------------------------------------ ground

    private val triX = FloatArray(3); private val triY = FloatArray(3); private val triZ = FloatArray(3)

    /** The GPS route as a blue band along the roads with white arrows pointing the way. */
    private fun drawRoute() {
        val gps = e.gps
        if (!gps.active) return
        val r = gps.route
        val wide = if (mapMode) max(7f, proj.dist / 60f) else 2.6f
        val col = scene.withAlpha(c(0xFF2F80ED), if (mapMode) .92f else .62f)
        val arrow = scene.withAlpha(0xFFFFFFFF.toInt(), .9f)
        for (q in 0 until gps.points - 1) {
            val x0 = r[q * 2]; val y0 = r[q * 2 + 1]; val x1 = r[q * 2 + 2]; val y1 = r[q * 2 + 3]
            if (max(x0, x1) < bbox[0] - wide || min(x0, x1) > bbox[2] + wide || max(y0, y1) < bbox[1] - wide || min(y0, y1) > bbox[3] + wide) continue
            val dx = x1 - x0; val dy = y1 - y0
            val len = sqrt(dx * dx + dy * dy)
            if (len < .1f) continue
            val ux = dx / len; val uy = dy / len
            val nx = -uy * wide / 2; val ny = ux * wide / 2
            scene.quad(x0 + nx, y0 + ny, .08f, x1 + nx, y1 + ny, .08f, x1 - nx, y1 - ny, .08f, x0 - nx, y0 - ny, .08f, col)
            scene.disc(x1, y1, .08f, wide / 2, wide / 2, 10, col)
            if (!mapMode && proj.dist < 300) {
                var t = 5f
                while (t < len - 2f) {
                    val cx = x0 + ux * t; val cy = y0 + uy * t
                    if (cx in bbox[0]..bbox[2] && cy in bbox[1]..bbox[3]) {
                        triX[0] = cx + ux * .9f; triY[0] = cy + uy * .9f
                        triX[1] = cx - ux * .5f - uy * .8f; triY[1] = cy - uy * .5f + ux * .8f
                        triX[2] = cx - ux * .5f + uy * .8f; triY[2] = cy - uy * .5f - ux * .8f
                        triZ[0] = .1f; triZ[1] = .1f; triZ[2] = .1f
                        scene.poly(3, triX, triY, triZ, arrow)
                    }
                    t += 9f
                }
            }
        }
    }

    private fun clipRect(x0: Float, y0: Float, x1: Float, y1: Float, z: Float, color: Int) {
        val a = max(x0, bbox[0]); val b = max(y0, bbox[1]); val cc = min(x1, bbox[2]); val d = min(y1, bbox[3])
        if (a >= cc || b >= d) return
        scene.groundRect(a, b, cc, d, z, color)
    }

    private val gx = FloatArray(16); private val gy = FloatArray(16); private val gz = FloatArray(16)

    /** Draws cached triangles of a ground polygon, if its bounding box is in view. */
    private fun groundPoly(poly: FloatArray, z: Float, color: Int) {
        val bb = e.city.boxOf(poly)
        if (bb[2] < bbox[0] || bb[0] > bbox[2] || bb[3] < bbox[1] || bb[1] > bbox[3]) return
        val t = e.city.triangles(poly)
        var k = 0
        while (k + 5 < t.size) {
            val x0 = t[k]; val y0 = t[k + 1]; val x1 = t[k + 2]; val y1 = t[k + 3]; val x2 = t[k + 4]; val y2 = t[k + 5]
            k += 6
            if (max(x0, max(x1, x2)) < bbox[0] || min(x0, min(x1, x2)) > bbox[2] || max(y0, max(y1, y2)) < bbox[1] || min(y0, min(y1, y2)) > bbox[3]) continue
            gx[0] = x0; gy[0] = y0; gx[1] = x1; gy[1] = y1; gx[2] = x2; gy[2] = y2
            gz[0] = z; gz[1] = z; gz[2] = z
            scene.poly(3, gx, gy, gz, color)
        }
    }

    /** A flat strip along a road piece, [o0]..[o1] meters to the left of a→b, from [s0] to [s1] along it. */
    private fun strip(e0: com.hustlecity.game.world.RoadEdge, s0: Float, s1: Float, o0: Float, o1: Float, z: Float, color: Int) {
        if (s1 <= s0) return
        val lx = e0.uy; val ly = -e0.ux
        val ax = e0.ax + e0.ux * s0; val ay = e0.ay + e0.uy * s0
        val bx = e0.ax + e0.ux * s1; val by = e0.ay + e0.uy * s1
        scene.quad(ax + lx * o0, ay + ly * o0, z, bx + lx * o0, by + ly * o0, z, bx + lx * o1, by + ly * o1, z, ax + lx * o1, ay + ly * o1, z, color)
    }

    private fun edgeVisible(e0: com.hustlecity.game.world.RoadEdge, pad: Float) =
        !(max(e0.ax, e0.bx) + pad < bbox[0] || min(e0.ax, e0.bx) - pad > bbox[2] || max(e0.ay, e0.by) + pad < bbox[1] || min(e0.ay, e0.by) - pad > bbox[3])

    private fun drawGround(withRoute: Boolean) {
        // snow lies on sidewalks and parks, roads turn to slush; rain darkens everything
        val snow = scene.snow
        val wet = if (mapMode) 0f else e.skyVis.wet
        val white = scene.lit(0xFFF2F5F8.toInt(), 1f)
        val dark = scene.lit(0xFF2A2D33.toInt(), 1f)
        val sidewalk = scene.mix(scene.mix(scene.lit(SIDEWALK, .97f), white, snow * .9f), dark, wet * .22f)
        val asphalt = scene.mix(scene.mix(scene.lit(ASPHALT, 1f), white, snow * .45f), dark, wet * .3f)
        val grass = scene.mix(scene.lit(GRASS, 1f), white, snow * .95f)
        val pathC = scene.mix(scene.lit(PATH, 1f), white, snow * .9f)
        val city = e.city
        scene.groundPass = true
        // the GPU draws a sky, so the sea around the city is real geometry there
        if (scene.gpu != null) {
            scene.material = Mat.WATER
            scene.groundRect(bbox[0] - 400f, bbox[1] - 400f, bbox[2] + 400f, bbox[3] + 400f, -.05f, WATER)
        }
        // land: the city's paving, then rivers and bays cut into it (or, on island maps, the land masses)
        scene.material = Mat.SIDEWALK
        val sh = city.shore
        if (sh.land.isEmpty()) clipRect(city.bounds[0], city.bounds[1], city.bounds[2], city.bounds[3], 0f, sidewalk)
        else for (l in sh.land) groundPoly(l, 0f, sidewalk)
        scene.material = Mat.WATER
        val water = scene.lit(WATER, 1f)
        for (w in sh.water) groundPoly(w, .005f, water)
        scene.material = Mat.SIDEWALK
        for (w in sh.islands) groundPoly(w, .01f, sidewalk)
        scene.material = Mat.WATER
        for (w in sh.canals) groundPoly(w, .012f, water)
        // parks, plazas and palace grounds with their footpaths
        for (pk in city.parks) {
            when (pk.kind) {
                com.hustlecity.game.world.Park.PLAZA -> { scene.material = Mat.SIDEWALK; groundPoly(pk.poly, .015f, scene.mix(pathC, sidewalk, .4f)) }
                com.hustlecity.game.world.Park.GROUNDS -> { scene.material = Mat.GRASS; groundPoly(pk.poly, .015f, scene.mix(grass, scene.lit(c(0xFF4E7F45), 1f), .35f)) }
                else -> { scene.material = Mat.GRASS; groundPoly(pk.poly, .015f, grass) }
            }
            scene.material = Mat.ROOF
            for (path in pk.paths) {
                val dx = path[2] - path[0]; val dy = path[3] - path[1]
                val l = sqrt(dx * dx + dy * dy).coerceAtLeast(.01f)
                val nx = -dy / l * 1.5f; val ny = dx / l * 1.5f
                if (max(path[0], path[2]) < bbox[0] || min(path[0], path[2]) > bbox[2] || max(path[1], path[3]) < bbox[1] || min(path[1], path[3]) > bbox[3]) continue
                scene.quad(path[0] + nx, path[1] + ny, .03f, path[2] + nx, path[3] + ny, .03f, path[2] - nx, path[3] - ny, .03f, path[0] - nx, path[1] - ny, .03f, pathC)
            }
        }
        // neighborhood tint (map filters)
        if (mapMode) for (h in city.neighborhoods) {
            if (e.ui.mapFilters["hood:${h.id}"] != true) continue
            city.forBuildingsIn(bbox[0], bbox[1], bbox[2], bbox[3]) { b ->
                if (b.hood.id != h.id) return@forBuildingsIn
                scene.setFrame(b.ox, b.oy, b.cs, b.sn)
                scene.groundRect(-3f, -3f, b.w + 3f, b.d + 3f, .05f, scene.withAlpha(h.color, .45f))
                scene.clearFrame()
            }
        }
        // roads: bridge decks get their own sidewalks, then the asphalt, then the junctions
        val g = city.roads
        if (roadSeen.size < g.edges.size) roadSeen = IntArray(g.edges.size)
        scene.material = Mat.SIDEWALK
        for (ed in g.edges) if (ed.overWater && edgeVisible(ed, 20f)) strip(ed, 0f, ed.len, -ed.cls.outer, ed.cls.outer, .02f, sidewalk)
        scene.material = Mat.ASPHALT
        g.forCells(bbox[0] - 20f, bbox[1] - 20f, bbox[2] + 20f, bbox[3] + 20f) { id -> roadSeen[id] = roadStamp }
        for (ed in g.edges) {
            if (roadSeen.getOrElse(ed.id) { 0 } != roadStamp) continue
            strip(ed, ed.clearA, ed.len - ed.clearB, -ed.cls.halfWidth, ed.cls.halfWidth, .025f, asphalt)
        }
        for (n in g.nodes) {
            if (n.x < bbox[0] - 30f || n.x > bbox[2] + 30f || n.y < bbox[1] - 30f || n.y > bbox[3] + 30f) continue
            junction(n, asphalt)
        }
        roadStamp++
        if (!mapMode && proj.dist < 420) {
            scene.material = Mat.SIDEWALK
            drawCurbs(asphalt, scene.mix(scene.mix(scene.lit(CURB, 1f), white, snow * .85f), dark, wet * .2f))
        }
        // lots that are visible from above
        city.forBuildingsIn(bbox[0], bbox[1], bbox[2], bbox[3]) { b ->
            when (b.kind) {
                BKind.PARKING -> {
                    scene.setFrame(b.ox, b.oy, b.cs, b.sn)
                    scene.material = Mat.ASPHALT
                    scene.groundRect(0f, 0f, b.w, b.d, .02f, scene.mix(scene.mix(scene.lit(c(0xFF55585E), 1f), white, snow * .6f), dark, wet * .3f))
                    if (!mapMode && proj.dist < 300) {
                        var x = 3f
                        val line = scene.lit(WHITE_LINE, .9f)
                        while (x < b.w - 1f) {
                            scene.groundRect(x, 1f, x + .15f, 6f, .04f, line)
                            scene.groundRect(x, b.d - 6f, x + .15f, b.d - 1f, .04f, line)
                            x += 2.8f
                        }
                    }
                    scene.clearFrame()
                }
                BKind.GAS -> { scene.setFrame(b.ox, b.oy, b.cs, b.sn); scene.groundRect(0f, 0f, b.w, b.d, .02f, scene.lit(c(0xFF5A5D63), 1f)); scene.clearFrame() }
                BKind.PARK -> {}
                else -> if (mapMode) { scene.setFrame(b.ox, b.oy, b.cs, b.sn); scene.groundRect(0f, 0f, b.w, b.d, .01f, scene.lit(LOT, 1f)); scene.clearFrame() }
            }
        }
        scene.material = Mat.PLAIN
        if (proj.dist < 420) drawMarkings()
        if (!mapMode && proj.dist < 260) drawTreePits(snow)
        features.drawGround(scene, bbox, mapMode, shadowDX, shadowDY,
            e.settings.shadows && !mapMode && scene.daylight > .15f && proj.dist < 400 && scene.gpu?.shadowOn != true)
        if (withRoute) drawRoute()
        // night street lights: soft warm pools on the pavement under every lamp and signal
        if (scene.daylight < .6f && !mapMode) {
            val night = ((.6f - scene.daylight) / .6f).coerceIn(0f, 1f)
            if (proj.dist < 260) {
                val pr = props
                pr.forIn(bbox[0] - 6f, bbox[1] - 6f, bbox[2] + 6f, bbox[3] + 6f) { pi ->
                    if (pr.isLamp(pi)) lightPool(pr.lightX(pi), pr.lightY(pi), 4.8f, night)
                }
            }
            forSignals { _, _, x, y -> lightPool(x, y, 4f, night * .8f) }
        }
        // shadows
        if (e.settings.shadows && !mapMode && scene.daylight > .15f && proj.dist < 400 && scene.gpu?.shadowOn != true) {
            val shadowColor = scene.withAlpha(0xFF000000.toInt(), .22f * scene.daylight)
            val expand = 60f
            city.forBuildingsIn(bbox[0] - expand, bbox[1] - expand, bbox[2] + expand, bbox[3] + expand) { b ->
                if (b.height > 0f) shadow(b, shadowColor)
            }
            if (proj.dist < 250) {
                val pr = props
                for ((i, t) in city.trees.withIndex()) {
                    if (t.x < bbox[0] - expand || t.x > bbox[2] + expand || t.y < bbox[1] - expand || t.y > bbox[3] + expand || pr.hiddenTree[i]) continue
                    trees.shadow(t.x, t.y, t.r, t.h, pr.treeKind[i], i, shadowDX, shadowDY, shadowColor)
                }
            }
        }
        scene.groundPass = false
    }

    /**
     * A pool of lamplight on the ground: concentric discs that build up toward a warm centre, so it fades out softly
     * instead of ending in a hard grey edge. The GPU renderer draws them as light, not as lit paint.
     */
    private fun lightPool(x: Float, y: Float, r: Float, strength: Float) {
        if (strength <= .01f) return
        scene.glow = true
        scene.disc(x, y, .05f, r, r, 18, scene.withAlpha(POOL_OUTER, .07f * strength))
        scene.disc(x, y, .052f, r * .74f, r * .74f, 16, scene.withAlpha(POOL_OUTER, .09f * strength))
        scene.disc(x, y, .054f, r * .5f, r * .5f, 14, scene.withAlpha(POOL_INNER, .12f * strength))
        scene.disc(x, y, .056f, r * .28f, r * .28f, 12, scene.withAlpha(POOL_INNER, .18f * strength))
        scene.glow = false
    }

    private var roadSeen = IntArray(0)
    private var roadStamp = 1

    /**
     * Fills a junction: the end corners of every road meeting there, in order around the node, fanned out from the
     * node. Neighbouring roads join corner to corner, so sharp-angled junctions stay as tight as the roads they join.
     */
    private fun junction(n: com.hustlecity.game.world.RoadNode, asphalt: Int) {
        val k = junctionOutline(n)
        if (k < 0) {
            // a bend in the road: round the joint off
            val hw = n.edges.maxOf { e.city.roads.edges[it].cls.halfWidth }
            scene.disc(n.x, n.y, .025f, hw, hw, 14, asphalt)
            return
        }
        for (i in 0 until k) {
            val j = if (i + 1 < k) i + 1 else 0
            jx[0] = n.x; jy[0] = n.y; jz[0] = .026f
            jx[1] = hx[i]; jy[1] = hy[i]; jz[1] = .026f
            jx[2] = hx[j]; jy[2] = hy[j]; jz[2] = .026f
            scene.poly(3, jx, jy, jz, asphalt)
        }
    }

    /**
     * The outline of a junction's asphalt into hx/hy, in order round the node: two end corners per road, with arcs
     * across wide gaps. [mouth] marks the sides where a road comes in (the rest is curb). -1 for a plain bend.
     */
    private fun junctionOutline(n: com.hustlecity.game.world.RoadNode): Int {
        val g = e.city.roads
        val m = min(n.edges.size, jOrder.size)
        var any = false
        for (k in 0 until m) {
            val ed = g.edges[n.edges[k]]
            jOrder[k] = k
            jAngle[k] = g.angleAway(ed, n.id)
            if ((if (ed.a == n.id) ed.clearA else ed.clearB) > 0f) any = true
        }
        if (!any) return -1
        // roads in order of the direction they leave the node
        for (i in 1 until m) {
            val v = jOrder[i]; var j = i - 1
            while (j >= 0 && jAngle[jOrder[j]] > jAngle[v]) { jOrder[j + 1] = jOrder[j]; j-- }
            jOrder[j + 1] = v
        }
        var k = 0
        for (q in 0 until m) {
            val ed = g.edges[n.edges[jOrder[q]]]
            val clear = if (ed.a == n.id) ed.clearA else ed.clearB
            val dx = if (ed.a == n.id) ed.ux else -ed.ux; val dy = if (ed.a == n.id) ed.uy else -ed.uy
            val hw = ed.cls.halfWidth
            // the corner just before this road's direction, then the one just after it
            if (k + 2 > hx.size) break
            hx[k] = n.x + dx * clear + dy * hw; hy[k] = n.y + dy * clear - dx * hw; mouth[k] = true; k++
            hx[k] = n.x + dx * clear - dy * hw; hy[k] = n.y + dy * clear + dx * hw; mouth[k] = false; k++
            // a wide gap to the next road (the outside of a bend or a fork): sweep round it so the fan stays convex
            val a0 = jAngle[jOrder[q]]
            val a1 = if (q + 1 < m) jAngle[jOrder[q + 1]] else jAngle[jOrder[0]] + 2f * PI.toFloat()
            if (a1 - a0 > 2.6f) {
                val nextEd = g.edges[n.edges[jOrder[(q + 1) % m]]]
                val r = max(hw, nextEd.cls.halfWidth)
                val from = a0 + PI.toFloat() / 2; val to = a1 - PI.toFloat() / 2
                val steps = ((to - from) / .5f).toInt().coerceIn(1, 8)
                for (t in 0..steps) {
                    if (k >= hx.size) break
                    val a = from + (to - from) * t / steps
                    hx[k] = n.x + cos(a) * r; hy[k] = n.y + sin(a) * r; mouth[k] = false; k++
                }
            }
        }
        // a dead end has a single road: its far side is curb all round
        return k
    }

    private val mouth = BooleanArray(64)
    private val jOrder = IntArray(16)
    private val jAngle = FloatArray(16)
    private val jx = FloatArray(3)
    private val jy = FloatArray(3)
    private val jz = FloatArray(3)

    /** Convex hull of the first [k] points of hx/hy into hullX/hullY; returns the point count. */
    private fun hullOf(k: Int): Int {
        for (i in 0 until k) sortIdx[i] = i
        for (i in 1 until k) {
            val v = sortIdx[i]
            var j = i - 1
            while (j >= 0 && (hx[sortIdx[j]] > hx[v] || (hx[sortIdx[j]] == hx[v] && hy[sortIdx[j]] > hy[v]))) { sortIdx[j + 1] = sortIdx[j]; j-- }
            sortIdx[j + 1] = v
        }
        var m = 0
        for (q in 0 until k) {
            val i = sortIdx[q]
            while (m >= 2 && cross(hidx[m - 2], hidx[m - 1], i) <= 0) m--
            hidx[m++] = i
        }
        val lower = m + 1
        for (q in k - 2 downTo 0) {
            val i = sortIdx[q]
            while (m >= lower && cross(hidx[m - 2], hidx[m - 1], i) <= 0) m--
            hidx[m++] = i
        }
        val n = (m - 1).coerceIn(0, hullX.size)
        for (i in 0 until n) { hullX[i] = hx[hidx[i]]; hullY[i] = hy[hidx[i]] }
        return n
    }

    /** Each signalled junction approach in view: (node, edge, x, y) of the pole on its corner. */
    private inline fun forSignals(block: (com.hustlecity.game.world.RoadNode, com.hustlecity.game.world.RoadEdge, Float, Float) -> Unit) {
        val g = e.city.roads
        for (n in g.nodes) {
            if (!n.signal || n.x < bbox[0] - 20f || n.x > bbox[2] + 20f || n.y < bbox[1] - 20f || n.y > bbox[3] + 20f) continue
            for (id in n.edges) {
                val ed = g.edges[id]
                val clear = if (ed.a == n.id) ed.clearA else ed.clearB
                // the approach comes toward the node; its pole stands on the near-side corner past the curb
                val dx = if (ed.a == n.id) ed.ux else -ed.ux; val dy = if (ed.a == n.id) ed.uy else -ed.uy
                val side = if (e.city.leftHand) -1f else 1f
                val off = ed.cls.halfWidth + 1.4f
                // right of someone driving toward the node (direction -d) is (dy, -dx) in a y-down world
                block(n, ed, n.x + dx * (clear + 1f) + dy * off * side, n.y + dy * (clear + 1f) - dx * off * side)
            }
        }
    }

    private val hx = FloatArray(64)
    private val hy = FloatArray(64)
    private val hz = FloatArray(40)
    private val hullX = FloatArray(40)
    private val hullY = FloatArray(40)
    private val sortIdx = IntArray(32)
    private val hidx = IntArray(80)

    /** The sun's shadow of a building on the ground: the hull of its footprint and the footprint pushed away from the sun. */
    private fun shadow(b: Building, color: Int) {
        val ox = shadowDX * b.height; val oy = shadowDY * b.height
        hx[0] = b.wx(0f, 0f); hy[0] = b.wy(0f, 0f); hx[1] = b.wx(b.w, 0f); hy[1] = b.wy(b.w, 0f)
        hx[2] = b.wx(b.w, b.d); hy[2] = b.wy(b.w, b.d); hx[3] = b.wx(0f, b.d); hy[3] = b.wy(0f, b.d)
        for (i in 0 until 4) { hx[i + 4] = hx[i] + ox; hy[i + 4] = hy[i] + oy }
        val n = hullOf(8).coerceAtMost(8)
        for (i in 0 until n) hz[i] = .03f
        scene.poly(n, hullX, hullY, hz, color)
    }

    private fun cross(o: Int, a: Int, b: Int) = (hx[a] - hx[o]) * (hy[b] - hy[o]) - (hy[a] - hy[o]) * (hx[b] - hx[o])

    /** Street trees stand in a square of earth behind an iron grate. */
    private fun drawTreePits(snow: Float) {
        val pr = props
        val soil = scene.mix(scene.lit(c(0xFF4A3B2E), 1f), scene.lit(0xFFF2F5F8.toInt(), 1f), snow * .8f)
        val grate = scene.lit(c(0xFF34373B), 1f)
        val edge = scene.lit(c(0xFF8D8A83), 1f)
        for ((i, t) in e.city.trees.withIndex()) {
            if (!pr.streetTree[i] || pr.hiddenTree[i] || t.x < bbox[0] - 3f || t.x > bbox[2] + 3f || t.y < bbox[1] - 3f || t.y > bbox[3] + 3f) continue
            val r = (t.r * .45f).coerceIn(.7f, 1.1f)
            scene.groundRect(t.x - r - .12f, t.y - r - .12f, t.x + r + .12f, t.y + r + .12f, .013f, edge)
            scene.groundRect(t.x - r, t.y - r, t.x + r, t.y + r, .015f, soil)
            // the grate: two bars each way round a hole for the trunk
            val h = r * .32f
            scene.groundRect(t.x - r, t.y - r, t.x + r, t.y - r + .14f, .017f, grate)
            scene.groundRect(t.x - r, t.y + r - .14f, t.x + r, t.y + r, .017f, grate)
            scene.groundRect(t.x - r, t.y - r, t.x - r + .14f, t.y + r, .017f, grate)
            scene.groundRect(t.x + r - .14f, t.y - r, t.x + r, t.y + r, .017f, grate)
            scene.groundRect(t.x - h - .07f, t.y - r, t.x - h + .07f, t.y + r, .017f, grate)
            scene.groundRect(t.x + h - .07f, t.y - r, t.x + h + .07f, t.y + r, .017f, grate)
        }
    }

    /** A steady 0..1 value for a pair of numbers, for wear and placement that never flickers. */
    private fun hash01(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    /** Paint that has seen some traffic: [color] faded by up to [amount], differently for each piece. */
    private fun worn(color: Int, a: Int, b: Int, amount: Float) = scene.withAlpha(color, 1f - amount * hash01(a, b))

    /** How crossings are painted at a junction: 0 none, 1 zebra, 2 zebra between two edge lines, 3 two thin lines. */
    private fun crossStyle(n: com.hustlecity.game.world.RoadNode): Int {
        if (n.degree < 3) return 0
        return when (e.city.mapId) {
            "nyc" -> if (n.signal) 2 else 3
            com.hustlecity.game.world.Maps.CLASSIC -> if (n.signal) 1 else 3
            else -> 1
        }
    }

    /**
     * Curbs: a pale concrete edge between the asphalt and the sidewalk with a darker gutter inside it, along every
     * road and round the corners of its junctions.
     */
    private fun drawCurbs(asphalt: Int, curb: Int) {
        val g = e.city.roads
        val gutter = scene.mix(asphalt, scene.lit(c(0xFF1E2024), 1f), .22f)
        for (ed in g.edges) {
            if (ed.overWater || !edgeVisible(ed, 12f)) continue
            val s0 = ed.clearA; val s1 = ed.len - ed.clearB
            val hw = ed.cls.halfWidth
            strip(ed, s0, s1, hw - .38f, hw, .0262f, gutter); strip(ed, s0, s1, -hw, -hw + .38f, .0262f, gutter)
            strip(ed, s0, s1, hw, hw + .24f, .028f, curb); strip(ed, s0, s1, -hw - .24f, -hw, .028f, curb)
        }
        for (n in g.nodes) {
            if (n.x < bbox[0] - 30f || n.x > bbox[2] + 30f || n.y < bbox[1] - 30f || n.y > bbox[3] + 30f) continue
            val k = junctionOutline(n)
            for (i in 0 until k) {
                if (mouth[i]) continue
                val j = if (i + 1 < k) i + 1 else 0
                val ax = hx[i]; val ay = hy[i]; val bx = hx[j]; val by = hy[j]
                val dx = bx - ax; val dy = by - ay
                val l = sqrt(dx * dx + dy * dy)
                if (l < .05f) continue
                var nx = -dy / l; var ny = dx / l
                if (nx * ((ax + bx) * .5f - n.x) + ny * ((ay + by) * .5f - n.y) < 0f) { nx = -nx; ny = -ny }
                scene.quad(ax, ay, .028f, bx, by, .028f, bx + nx * .24f, by + ny * .24f, .028f, ax + nx * .24f, ay + ny * .24f, .028f, curb)
            }
        }
    }

    private fun drawMarkings() {
        val manila = e.city.mapId == "manila"
        val fade = if (manila) .55f else .38f
        val yellow = scene.lit(YELLOW, 1f)
        val white = scene.lit(WHITE_LINE, .95f)
        val near = proj.dist < 220
        val iron = scene.lit(c(0xFF3B3E43), 1f)
        val rim = scene.lit(c(0xFF5A5E64), 1f)
        val asphaltLit = scene.lit(ASPHALT, 1f)
        val patchDark = scene.mix(asphaltLit, scene.lit(c(0xFF202226), 1f), .22f)
        val patchPale = scene.mix(asphaltLit, scene.lit(c(0xFF9A9CA0), 1f), .15f)
        val track = scene.withAlpha(scene.lit(c(0xFF1C1D20), 1f), .16f)
        val g = e.city.roads
        val left = e.city.leftHand
        for (ed in g.edges) {
            if (!edgeVisible(ed, 12f)) continue
            val s0 = ed.clearA + 3.5f; val s1 = ed.len - ed.clearB - 3.5f
            if (s1 <= s0) continue
            val hw = ed.cls.halfWidth
            if (near) {
                // tyre tracks darken the middle of every lane, and old repairs patch the asphalt here and there
                if (!ed.overWater) {
                    for (lane in 0 until ed.cls.lanes) for (sd in -1..1 step 2) {
                        val o = sd * (lane + .5f) * ed.cls.laneWidth
                        if (kotlin.math.abs(o) > hw - .6f) continue
                        strip(ed, s0 - 2f, s1 + 2f, o - .9f, o - .35f, .0254f, track)
                        strip(ed, s0 - 2f, s1 + 2f, o + .35f, o + .9f, .0254f, track)
                    }
                    val patches = (ed.len / 40f).toInt().coerceAtMost(3)
                    for (p in 0 until patches) {
                        val r = hash01(ed.id, p * 3)
                        val pl = 2.2f + 3f * hash01(ed.id, p * 3 + 1)
                        val at = s0 + (s1 - s0 - pl).coerceAtLeast(0f) * r
                        val pw = 1.2f + 1.4f * hash01(ed.id, p * 3 + 2)
                        val o = (hash01(p, ed.id) * 2f - 1f) * (hw - pw * .5f - .5f).coerceAtLeast(0f)
                        strip(ed, at, min(at + pl, s1), o - pw * .5f, o + pw * .5f, .0256f, if ((ed.id + p) % 3 == 0) patchPale else patchDark)
                    }
                }
            }
            when {
                // divided roads: a double yellow line, and dashed white lines between the lanes; the paint is worn in places
                ed.cls.rank >= 2 -> {
                    var t = s0; var k = 0
                    while (t < s1) {
                        val t1 = min(t + 6f, s1)
                        strip(ed, t, t1, -.35f, -.15f, .03f, worn(yellow, ed.id, k, fade))
                        strip(ed, t, t1, .15f, .35f, .03f, worn(yellow, k, ed.id, fade))
                        t = t1; k++
                    }
                    if (near) for (lane in 1 until ed.cls.lanes) for (sd in -1..1 step 2) {
                        val o = sd * lane * ed.cls.laneWidth
                        t = s0 + 1f; k = 0
                        while (t < s1 - 2f) { strip(ed, t, min(t + 3f, s1), o - .1f, o + .1f, .03f, worn(white, ed.id * 7 + lane * sd, k++, fade)); t += 8f }
                    }
                }
                ed.cls == com.hustlecity.game.world.RoadClass.STREET -> {
                    var t = s0 + 1f; var k = 0
                    while (t < s1 - 2f) { strip(ed, t, min(t + 3f, s1), -.1f, .1f, .03f, worn(yellow, ed.id, k++, fade)); t += 7f }
                }
            }
            if (!near) continue
            // crosswalks where the road meets a junction, painted the city's way, with stop lines at the lights
            for (end in 0..1) {
                val node = g.nodes[if (end == 0) ed.a else ed.b]
                val style = crossStyle(node)
                if (style == 0 || ed.cls.rank < 1) continue
                val clear = if (end == 0) ed.clearA else ed.clearB
                val a = if (end == 0) clear + .5f else ed.len - clear - 3.2f
                val salt = ed.id * 2 + end
                when (style) {
                    3 -> {
                        strip(ed, a, a + .3f, -hw + .3f, hw - .3f, .04f, worn(white, salt, 1, fade))
                        strip(ed, a + 2.4f, a + 2.7f, -hw + .3f, hw - .3f, .04f, worn(white, salt, 2, fade))
                    }
                    else -> {
                        if (style == 2) {
                            strip(ed, a - .1f, a + .2f, -hw + .3f, hw - .3f, .04f, worn(white, salt, 1, fade))
                            strip(ed, a + 2.5f, a + 2.8f, -hw + .3f, hw - .3f, .04f, worn(white, salt, 2, fade))
                        }
                        var o = -hw + .4f; var k = 0
                        while (o < hw - .4f) { strip(ed, a, a + 2.7f, o, o + .6f, .04f, worn(white, salt, 10 + k++, fade)); o += 1.25f }
                    }
                }
                if (node.signal) {
                    // traffic coming toward this junction stops behind the line on its own side of the road
                    val sign = (if (end == 1) -1f else 1f) * (if (left) -1f else 1f)
                    val b = if (end == 0) clear + 4.2f else ed.len - clear - 4.6f
                    val o0 = if (sign > 0) .4f else -hw + .3f
                    val o1 = if (sign > 0) hw - .3f else -.4f
                    strip(ed, b, b + .4f, o0, o1, .04f, worn(white, salt, 3, fade))
                }
            }
            // a manhole cover, a utility cover on the sidewalk and storm drains at the curb
            val mid = (s0 + s1) * .5f + ((ed.id * 7) % 5) * 2f
            if (mid < s1) {
                val o = (if (ed.id % 2 == 0) .5f else -.5f) * ed.cls.laneWidth
                val mx = ed.ax + ed.ux * mid + ed.uy * o; val my = ed.ay + ed.uy * mid - ed.ux * o
                scene.disc(mx, my, .035f, .55f, .55f, 10, rim)
                scene.disc(mx, my, .04f, .45f, .45f, 10, iron)
                if (!ed.overWater && ed.len > 30f) {
                    val so = (if (ed.id % 3 == 0) -1f else 1f) * (hw + ed.cls.sidewalk * .55f)
                    strip(ed, mid - 7f, mid - 6.2f, so - .4f, so + .4f, .012f, rim)
                    strip(ed, mid - 6.95f, mid - 6.25f, so - .35f, so + .35f, .014f, iron)
                }
            }
            if (!ed.overWater) for (sd in -1..1 step 2) {
                val o = sd * (hw - .35f)
                strip(ed, s0 + 2.5f, s0 + 3.7f, o - .25f, o + .25f, .035f, iron)
            }
        }
    }

    // ------------------------------------------------------------------ drawables

    private fun add(type: Int, idx: Int, key: Float) {
        if (dCount >= maxDraw) return
        dType[dCount] = type; dIdx[dCount] = idx; dKey[dCount] = key
        dCount++
    }

    private fun keyFor(x: Float, y: Float): Float {
        val dx = x - proj.camX; val dy = y - proj.camY
        return dx * dx + dy * dy
    }

    private fun keyForRect(b: Building): Float {
        val nx = proj.camX.coerceIn(b.minX, b.maxX)
        val ny = proj.camY.coerceIn(b.minY, b.maxY)
        return keyFor(nx, ny)
    }

    private fun collectDrawables(mode: Int) {
        dCount = 0
        val stat = mode != DYNAMIC
        val dyn = mode != STATIC
        val city = e.city
        val p = e.state.player
        val m = min(city.maxHeight, 130f) * .9f
        val bx0 = bbox[0] - m; val by0 = bbox[1] - m; val bx1 = bbox[2] + m; val by1 = bbox[3] + m
        if (mode == DYNAMIC) {
            // only the buildings hiding the player, drawn see-through
            for (id in occl) { val b = city.buildings[id]; add(0, b.id, keyForRect(b)) }
        } else city.forBuildingsIn(bx0, by0, bx1, by1) { b ->
            if (b.kind == BKind.PARK) return@forBuildingsIn
            if (b.kind == BKind.PARKING) {
                if (!mapMode && proj.dist < 300) {
                    val slots = ((b.w - 4f) / 2.8f).toInt()
                    if (stat) for (sIdx in 0 until min(slots, 12)) if ((b.id * 31 + sIdx * 17) % 10 < 6) {
                        add(7, b.id * 32 + sIdx, keyFor(b.wx(4.4f + sIdx * 2.8f, 3.5f), b.wy(4.4f + sIdx * 2.8f, 3.5f)))
                    }
                }
                return@forBuildingsIn
            }
            add(0, b.id, keyForRect(b))
        }
        val farLod = mapMode || proj.dist > 450
        if (!farLod && stat) {
            for ((i, t) in city.trees.withIndex()) {
                if (t.x < bbox[0] || t.x > bbox[2] || t.y < bbox[1] - 10f || t.y > bbox[3]) continue
                add(1, i, keyFor(t.x, t.y))
            }
        }
        if (!mapMode) {
            if (dyn) for ((i, a) in e.traffic.cars.withIndex()) if (a.x in bbox[0]..bbox[2] && a.y in bbox[1]..bbox[3]) add(2, i, keyFor(a.x, a.y))
            if (dyn && proj.dist < 260) for ((i, a) in e.traffic.peds.withIndex()) if (!a.hidden && a.x in bbox[0]..bbox[2] && a.y in bbox[1]..bbox[3]) add(3, i, keyFor(a.x, a.y))
            if (proj.dist < 230) {
                val pr = props
                pr.forIn(bbox[0] - 4f, bbox[1] - 7f, bbox[2] + 4f, bbox[3] + 4f) { pi ->
                    val x = pr.x[pi]; val y = pr.y[pi]
                    if (x < bbox[0] - 3f || x > bbox[2] + 3f || y < bbox[1] - 6f || y > bbox[3] + 3f) return@forIn
                    // fountains move, so they're redrawn every frame; the rest is cached
                    if (mode == ALL || (mode == STATIC) != pr.animated(pi)) add(8, pi, keyFor(x, y))
                }
            }
            if (dyn && proj.dist < 240) forSignals { n, ed, x, y ->
                add(6, (n.id shl 4) or n.edges.indexOf(ed.id).coerceIn(0, 15), keyFor(x, y))
            }
        }
        // landmarks, viaducts, bridges and walls are cached; trains and the wheel's rotor move (the Canvas path draws
        // them with the span or landmark they're on, so they sort with it)
        features.collect(bbox, proj.camX, proj.camY, stat, mode == DYNAMIC, mapMode, addFeature)
        if (mode == DYNAMIC && !mapMode) features.forCars(e.realTime, bbox, addTrainCar)
        if (dyn) for (v in e.state.vehicles) if (v.x in bbox[0] - 5..bbox[2] + 5 && v.y in bbox[1] - 5..bbox[3] + 5) add(5, v.id, keyFor(v.x, v.y))
        if (dyn && e.hasGame && p.driving < 0 && p.inBuilding < 0 && !mapMode) add(4, 0, playerKey)
    }

    private fun sortDrawables() {
        for (i in 0 until dCount) order[i] = i
        quicksort(0, dCount - 1)
    }

    /** Sorts [order] by key descending (farthest first). */
    private fun quicksort(lo0: Int, hi0: Int) {
        var lo = lo0; var hi = hi0
        while (lo < hi) {
            if (hi - lo < 16) {
                for (i in lo + 1..hi) {
                    val v = order[i]; val kv = dKey[v]
                    var j = i - 1
                    while (j >= lo && dKey[order[j]] < kv) { order[j + 1] = order[j]; j-- }
                    order[j + 1] = v
                }
                return
            }
            val pivot = dKey[order[(lo + hi) ushr 1]]
            var i = lo; var j = hi
            while (i <= j) {
                while (dKey[order[i]] > pivot) i++
                while (dKey[order[j]] < pivot) j--
                if (i <= j) { val t = order[i]; order[i] = order[j]; order[j] = t; i++; j-- }
            }
            if (j - lo < hi - i) { quicksort(lo, j); lo = i } else { quicksort(i, hi); hi = j }
        }
    }

    // ------------------------------------------------------------------ buildings

    private fun hash(a: Int, b: Int, c: Int): Int {
        var h = a * 73856093 xor b * 19349663 xor c * 83492791
        h = h xor (h ushr 13)
        return h and 0x7fffffff
    }

    private fun drawBuilding(b: Building) {
        if (b.kind == BKind.GAS) { painter.draw(b, b.roof, b.facade, sqrt(keyFor(b.cx, b.cy)), !mapMode); return }
        val s = e.state
        val p = s.player
        var alpha = 1f
        if (!staticPass && !mapMode && e.hasGame && p.inBuilding < 0 && b.height > 5f && keyForRect(b) < playerKey) {
            if (e.occludes(proj, b, focusX, focusY, 1.2f)) alpha = .3f
        }
        var roof = b.roof
        var facade = if (mapMode) b.facade else painter.facadeColor(b, b.facade)
        if (mapMode) {
            facade = c(0xFFC9CED6); roof = c(0xFFE4E7EC)
            val rented = b.id in rentedBuildings
            if (rented) { roof = c(0xFF5FD38D); facade = c(0xFF3FAE6E) }
            else if (e.ui.mapFilters["rent"] == true && vacant(b)) { roof = c(0xFF9FE3B8); facade = c(0xFF7CCB9B) }
            if (b.id == e.ui.selected) { roof = c(0xFFFFE08A); facade = c(0xFFE6B84A) }
        } else if (b.id == e.ui.selected) {
            roof = brighten(roof, 1.25f); facade = brighten(facade, 1.15f)
        }
        if (alpha < 1f || mapMode) {
            scene.setFrame(b.ox, b.oy, b.cs, b.sn)
            scene.box(0f, 0f, 0f, b.w, b.d, b.height, roof, facade, alpha)
            scene.clearFrame()
            return
        }
        painter.draw(b, roof, facade, sqrt(keyFor(b.cx, b.cy)), true)
    }

    private fun brighten(color: Int, f: Float): Int {
        val r = min(255, (((color shr 16) and 255) * f).toInt()); val g = min(255, (((color shr 8) and 255) * f).toInt()); val bb = min(255, ((color and 255) * f).toInt())
        return (color and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or bb
    }

    // helpers for [BuildingPainter]

    internal fun bizAt(buildingId: Int) = bizByBuilding[buildingId]
    internal fun rivalAt(buildingId: Int) = rivalByBuilding[buildingId]
    internal fun isVacant(b: Building) = vacant(b)
    internal fun car(x: Float, y: Float, heading: Float, len: Float, wid: Float, hgt: Float, color: Int, style: Int) =
        drawCar(x, y, heading, len, wid, hgt, color, style, false)

    /** The shop name floating in front of the sign band. */
    internal fun shopLabel(b: Building, face: Int, name: String) {
        val ox = when (face) { 2 -> -1f; 3 -> 1f; else -> 0f }
        val oy = when (face) { 0 -> -1f; 1 -> 1f; else -> 0f }
        // in the building's frame: faces 0 back, 1 front (the street), 2 and 3 the sides
        val lx = (if (face <= 1) b.w * .5f else (if (face == 2) 0f else b.w)) + ox * .2f
        val ly = (if (face >= 2) b.d * .5f else (if (face == 0) 0f else b.d)) + oy * .2f
        label(name, b.wx(lx, ly), b.wy(lx, ly), 3.8f, .75f, 15f * density, 6.5f * density * .6f, .35f, 0xFFFFFFFF.toInt())
    }

    internal fun forRentLabel(b: Building, face: Int) {
        val ly = if (face == 0) 0f else b.d
        label("FOR RENT", b.wx(b.w * .5f, ly), b.wy(b.w * .5f, ly), 3.6f, .6f, 12f * density, 4f * density, 0f, c(0xFF27AE60), 0xEEFFFFFF.toInt())
    }

    internal fun gasLabel(b: Building, y0: Float, y1: Float) {
        if (mapMode) return
        label("Fuel Up", b.wx(b.w * .5f, y1), b.wy(b.w * .5f, y1), 6.3f, .8f, 14f * density, 4f * density, 0f, 0xFFFFFFFF.toInt(), icon = "place_gas")
    }

    // ------------------------------------------------------------------ props

    private fun drawTree(i: Int) {
        val pr = props
        if (pr.hiddenTree[i]) return
        val t = e.city.trees[i]
        trees.draw(t.x, t.y, t.r, t.h, t.tone, pr.treeKind[i], e.weather.month(), i, keyFor(t.x, t.y) < 70f * 70f)
    }

    private fun drawCar(x: Float, y: Float, heading: Float, len: Float, wid: Float, hgt: Float, color: Int, style: Int, owned: Boolean,
                        taxi: Boolean = false, braking: Boolean = false, accent: Int = 0) {
        val lod = when (e.settings.quality) { 0 -> 45f; 1 -> 80f; else -> 120f }
        val near = !mapMode && keyFor(x, y) < lod * lod
        cars.leftHand = e.city.leftHand
        cars.draw(x, y, heading, len, wid, hgt, color, style, near, taxi, braking, accent)
    }


    private fun drawTrafficLight(code: Int) {
        val g = e.city.roads
        val n = g.nodes.getOrNull(code shr 4) ?: return
        val ed = g.edges.getOrNull(n.edges.getOrElse(code and 15) { -1 }) ?: return
        val clear = if (ed.a == n.id) ed.clearA else ed.clearB
        val dx = if (ed.a == n.id) ed.ux else -ed.ux; val dy = if (ed.a == n.id) ed.uy else -ed.uy
        val side = if (e.city.leftHand) -1f else 1f
        val off = ed.cls.halfWidth + 1.4f
        val x = n.x + dx * (clear + 1f) + dy * off * side
        val y = n.y + dy * (clear + 1f) - dx * off * side
        val pole = c(0xFF3A3D42)
        scene.box(x - .1f, y - .1f, 0f, x + .1f, y + .1f, 4.4f, pole, pole)
        // the arm reaches over the lanes it controls
        val ax = x - dy * side * 2.6f; val ay = y + dx * side * 2.6f
        scene.box(min(x, ax) - .05f, min(y, ay) - .05f, 4.3f, max(x, ax) + .05f, max(y, ay) + .05f, 4.42f, pole, pole)
        val state = e.traffic.light(n, ed)
        val lightColor = when (state) { 0 -> c(0xFF2EE87A); 1 -> c(0xFFFFD34D); else -> c(0xFFFF4D4D) }
        scene.box(ax - .25f, ay - .25f, 3.5f, ax + .25f, ay + .25f, 4.4f, lightColor, c(0xFF22252A))
    }

    private fun drawParkedCar(code: Int) {
        val b = e.city.buildings[code / 32]
        val slot = code % 32
        val x = 4.4f + slot * 2.8f
        val top = slot % 2 == 0
        val y = if (top) 3.5f else b.d - 3.5f
        val color = intArrayOf(c(0xFFC0392B), c(0xFFE0E0E0), c(0xFF2C3E50), c(0xFF7F8C8D), c(0xFF1F3A93), c(0xFF212121))[(b.id + slot) % 6]
        scene.setFrame(b.ox, b.oy, b.cs, b.sn)
        try { drawCar(x, y, 0f, 4.3f, 1.85f, 1.45f, color, if ((b.id + slot) % 5 == 0) 1 else 0, false) } finally { scene.clearFrame() }
    }

    private fun drawPed(a: com.hustlecity.game.world.Agent, i: Int) {
        // most people carry an umbrella in the rain (and some in the snow)
        val v = e.skyVis
        val umbrella = if ((v.rain > .25f && i % 10 < 7) || (v.snow > .4f && i % 10 < 3)) PersonPainter.UMBRELLAS[i % PersonPainter.UMBRELLAS.size] else 0
        people.draw(a.x, a.y, 0f, a.heading, a.phase, (a.speed / 1.3f).coerceIn(0f, 1f),
            a.color, PersonPainter.PANTS[(i * 3) % PersonPainter.PANTS.size], SKIN_TONES[(i * 7) % SKIN_TONES.size], PersonPainter.HAIR[(i * 5) % PersonPainter.HAIR.size],
            umbrella = umbrella)
    }

    private fun drawPlayer() {
        val p = e.state.player
        scene.disc(p.x, p.y, .04f, 1f, 1f, 14, scene.withAlpha(c(0xFF27AE60), .55f))
        people.draw(p.x, p.y, 0f, p.heading, e.walkPhase, e.walkAmount, SHIRT_COLORS[p.shirt % SHIRT_COLORS.size], PersonPainter.PANTS[1],
            SKIN_TONES[p.skin % SKIN_TONES.size], PersonPainter.HAIR[p.skin % PersonPainter.HAIR.size], 1.12f,
            carry = e.carryPose(), load = p.hand.size)
    }

    // ------------------------------------------------------------------ overlays

    private fun drawLabels(canvas: Canvas, list: ArrayList<Label>) {
        for (l in list) {
            if (!proj.project(l.x, l.y, l.z, tmp)) continue
            val size = (proj.focal * l.k / tmp[2]).coerceAtMost(l.max)
            if (size <= l.min) continue
            val x = tmp[0]; val y = tmp[1] + size * l.shift
            if (x < -200f || y < -60f || x > proj.screenW + 200f || y > proj.screenH + 60f) continue
            textPaint.textSize = size
            if (l.bg != 0) {
                val tw = textPaint.measureText(l.text)
                pillPaint.color = l.bg
                rect.set(x - tw / 2 - size * .4f, y - size * 1.05f, x + tw / 2 + size * .4f, y + size * .35f)
                canvas.drawRoundRect(rect, size * .3f, size * .3f, pillPaint)
                textPaint.color = l.color
                canvas.drawText(l.text, x, y, textPaint)
            } else {
                // an icon sits left of the text, the pair centred on the label point
                val ic = l.icon
                val shift = if (ic != null) size * .65f else 0f
                if (ic != null) {
                    val tw = textPaint.measureText(l.text)
                    IconCanvas.draw(canvas, ic, x - tw / 2 - size * .1f + shift - size * .55f + 1.5f, y - size * .35f + 1.5f, size * 1.1f, 0xAA000000.toInt())
                    IconCanvas.draw(canvas, ic, x - tw / 2 - size * .1f + shift - size * .55f, y - size * .35f, size * 1.1f, l.color)
                }
                textPaint.color = 0xAA000000.toInt()
                canvas.drawText(l.text, x + shift + 1.5f, y + 1.5f, textPaint)
                textPaint.color = l.color
                canvas.drawText(l.text, x + shift, y, textPaint)
            }
        }
    }

    private fun drawOverlays(canvas: Canvas, w: Int, h: Int) {
        val s = e.state
        val sel = e.city.building(e.ui.selected)
        if (sel != null && !mapMode) outlineBuilding(canvas, sel)
        // business / place signs
        drawLabels(canvas, staticLabels)
        drawLabels(canvas, labels)
        if (mapMode) drawMapOverlay(canvas)
        // own vehicle pins
        if (!mapMode && s.player.inBuilding < 0) {
            for (v in s.vehicles) {
                if (v.id == s.player.driving) continue
                val d = dist(v.x, v.y, s.player.x, s.player.y)
                if (d < 400f && proj.project(v.x, v.y, v.def.height + 2.5f, tmp)) {
                    // loaded cars show what's in the trunk
                    if (v.cargo.isNotEmpty()) waypointPill(canvas, tmp[0], tmp[1] - 6f * density, "${v.cargo.size}/${v.def.cargo} in the trunk", c(0xFFF2994A), "cargo_boxes")
                    else if (d > 9f) pin(canvas, tmp[0], tmp[1], "car", c(0xFF27AE60), 11f * density)
                }
            }
        }
        // waypoint
        val wp = e.city.building(s.player.waypoint)
        // with GPS on, the banner shows the way; the label marks the place only once it's in view
        val inView = wp != null && proj.project(wp.doorX, wp.doorY, wp.height + 4f, tmp) && tmp[0] in 0f..w.toFloat() && tmp[1] in 0f..h.toFloat()
        if (wp != null && (!e.gps.active || mapMode || inView)) {
            val (fx, fy) = e.focusPoint()
            val d = (if (e.gps.active) e.gps.distance else dist(fx, fy, wp.doorX, wp.doorY)).roundToInt()
            val text = "${e.buildingName(wp)} (${d} m)"
            val onScreen = proj.project(wp.doorX, wp.doorY, wp.height + 4f, tmp)
            var sx = if (onScreen) tmp[0] else w / 2f
            var sy = if (onScreen) tmp[1] else h / 2f
            if (!onScreen) {
                // direction from focus in screen space
                if (proj.project(wp.doorX, wp.doorY, 0f, tmp)) { sx = tmp[0]; sy = tmp[1] } else {
                    val dx = wp.doorX - fx; val dy = wp.doorY - fy
                    sx = w / 2f + dx * 10f; sy = h / 2f + dy * 10f
                }
            }
            val margin = 70f * density
            sx = sx.coerceIn(margin, w - margin)
            sy = sy.coerceIn(70f * density, h - 50f * density)
            waypointPill(canvas, sx, sy, text)
        }
        if (e.settings.showFps) {
            textPaint.textSize = 11f * density
            textPaint.color = 0xFFFFFFFF.toInt()
            textPaint.textAlign = Paint.Align.LEFT
            val gpuText = if (e.gpuFps > 0f) " · GPU ${e.gpuFps.roundToInt()} FPS at ${(e.renderScale * 100).roundToInt()}%" else " · ${batch.trianglesDrawn} tris"
            canvas.drawText("${e.fps.roundToInt()} FPS$gpuText", 8f * density, h - 6f * density, textPaint)
            textPaint.textAlign = Paint.Align.CENTER
        }
    }

    private fun outlineBuilding(canvas: Canvas, b: Building) {
        val h = max(b.height, .3f)
        val xs = floatArrayOf(b.wx(0f, 0f), b.wx(b.w, 0f), b.wx(b.w, b.d), b.wx(0f, b.d))
        val ys = floatArrayOf(b.wy(0f, 0f), b.wy(b.w, 0f), b.wy(b.w, b.d), b.wy(0f, b.d))
        path.rewind()
        for (k in 0 until 4) {
            if (!proj.project(xs[k], ys[k], h, tmp)) return
            if (k == 0) path.moveTo(tmp[0], tmp[1]) else path.lineTo(tmp[0], tmp[1])
        }
        path.close()
        strokePaint.color = 0xFFFFFFFF.toInt()
        strokePaint.strokeWidth = 2.5f * density
        canvas.drawPath(path, strokePaint)
    }

    private fun pin(canvas: Canvas, x: Float, y: Float, icon: String, ring: Int, r: Float) = IconCanvas.pin(canvas, icon, x, y, r, ring)

    private fun waypointPill(canvas: Canvas, x: Float, y: Float, text: String, dot: Int = c(0xFF2F80ED), icon: String = "pin") {
        textPaint.textSize = 13f * density
        val tw = textPaint.measureText(text)
        val r = 14f * density
        rect.set(x - tw / 2 - r * 1.6f, y - r, x + tw / 2 + r * .8f, y + r)
        pillPaint.color = 0xE63A4250.toInt()
        canvas.drawRoundRect(rect, r, r, pillPaint)
        pillPaint.color = dot
        canvas.drawCircle(rect.left + r, y, r * .78f, pillPaint)
        IconCanvas.draw(canvas, icon, rect.left + r, y, r * 1.05f)
        textPaint.color = 0xFFFFFFFF.toInt()
        textPaint.textSize = 13f * density
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(text, rect.left + r * 2.1f, y + 4.5f * density, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
    }

    private val nameEdges = ArrayList<Int>()
    private val namedAt = ArrayList<Triple<String, Float, Float>>()

    private fun drawMapOverlay(canvas: Canvas) {
        val s = e.state
        val f = e.ui.mapFilters
        // street names
        val roadFont = (proj.focal * 7f / proj.dist).coerceAtMost(15f * density)
        if (roadFont > 6f * density) {
            textPaint.color = 0xCCFFFFFF.toInt()
            textPaint.textSize = roadFont
            // one name per road and area: the longest pieces in view first
            val g = e.city.roads
            nameEdges.clear()
            g.forCells(bbox[0], bbox[1], bbox[2], bbox[3]) { id -> if (g.edges[id].len > 40f && g.edges[id].cls.rank >= 1) nameEdges += id }
            val sorted = nameEdges.distinct().sortedByDescending { g.edges[it].len }
            namedAt.clear()
            var shown = 0
            for (id in sorted) {
                if (shown >= 40) break
                val ed = g.edges[id]
                val mx = (ed.ax + ed.bx) * .5f; val my = (ed.ay + ed.by) * .5f
                if (namedAt.any { it.first == ed.name && dist(it.second, it.third, mx, my) < 260f }) continue
                namedAt += Triple(ed.name, mx, my)
                roadText(canvas, ed.name.uppercase(), mx - ed.ux * 10f, my - ed.uy * 10f, mx + ed.ux * 10f, my + ed.uy * 10f)
                shown++
            }
        }
        val r = 11f * density
        val showNames = proj.dist < 650
        // places
        for (b in e.city.buildings) {
            val placeId = b.placeId
            var icon: String? = null
            var ring = 0
            var name: String? = null
            if (placeId != null && f["places"] != false) {
                val pl = Catalog.place(placeId); icon = pl.icon; ring = pl.color; name = pl.name
            }
            val biz = bizByBuilding[b.id]
            if (biz != null) { icon = biz.type.icon; ring = biz.type.color; name = biz.name }
            else if (b.id == s.player.home) { icon = "home"; ring = c(0xFFEB5757); name = "Home" }
            else if (b.id in rentedBuildings) { icon = "rental"; ring = c(0xFF27AE60); name = b.address }
            else {
                val rb = rivalByBuilding[b.id]
                if (rb != null && f["type:${rb.typeId}"] == true) { icon = Catalog.type(rb.typeId).icon; ring = c(0xFF7D8793); name = rb.name }
                else if (f["rent"] == true && b.kind == BKind.RETAIL && vacant(b)) { icon = "for_rent"; ring = c(0xFF27AE60) }
            }
            if (icon == null) continue
            if (!proj.project(b.cx, b.cy, b.height + 3f, tmp)) continue
            if (tmp[0] < -r || tmp[0] > proj.screenW + r || tmp[1] < -r || tmp[1] > proj.screenH + r) continue
            val px = tmp[0]; val py = tmp[1] - r * 1.4f
            pin(canvas, px, py, icon, ring, r)
            if (showNames && name != null) {
                textPaint.textSize = 10f * density
                textPaint.color = 0xFF000000.toInt()
                canvas.drawText(name, px + 1, py - r * 1.4f + 1, textPaint)
                textPaint.color = 0xFFFFFFFF.toInt()
                canvas.drawText(name, px, py - r * 1.4f, textPaint)
            }
        }
        features.mapLabels(canvas, proj, density)
        // player marker
        val (fx, fy) = e.focusPoint()
        if (proj.project(fx, fy, 2f, tmp)) {
            pillPaint.color = c(0xFF27AE60)
            canvas.drawCircle(tmp[0], tmp[1], 9f * density, pillPaint)
            pillPaint.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(tmp[0], tmp[1], 4f * density, pillPaint)
        }
    }

    private fun roadText(canvas: Canvas, text: String, x0: Float, y0: Float, x1: Float, y1: Float) {
        if (!proj.project(x0, y0, .1f, tmp)) return
        val ax = tmp[0]; val ay = tmp[1]
        if (!proj.project(x1, y1, .1f, tmp)) return
        val bx = tmp[0]; val by = tmp[1]
        val mx = (ax + bx) / 2; val my = (ay + by) / 2
        if (mx < 0 || mx > proj.screenW || my < 0 || my > proj.screenH) return
        var angle = Math.toDegrees(atan2((by - ay).toDouble(), (bx - ax).toDouble())).toFloat()
        if (angle > 90) angle -= 180f
        if (angle < -90) angle += 180f
        canvas.save()
        canvas.rotate(angle, mx, my)
        canvas.drawText(text, mx, my + textPaint.textSize * .35f, textPaint)
        canvas.restore()
    }

    /** Screen position of a world point, or null if behind the camera. */
    fun screenOf(x: Float, y: Float, z: Float): Pair<Float, Float>? = if (proj.project(x, y, z, tmp)) tmp[0] to tmp[1] else null
}
