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

    private val labels = ArrayList<Label>()
    private class Label(var text: String, var x: Float, var y: Float, var size: Float, var color: Int, var bg: Int, var icon: String? = null)
    private val labelPool = ArrayList<Label>()

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    private val rect = RectF()
    private val people = PersonPainter(scene)
    private val painter = BuildingPainter(this, e)
    private val cars = CarPainter(scene)
    private var propsCache: StreetProps? = null
    private val props: StreetProps
        get() = propsCache?.takeIf { it.city === e.city } ?: StreetProps(e.city).also { propsCache = it }

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
        val GLASS = c(0xFF3C5266)
        val GLASS_NIGHT = c(0xFF1E2A36)
        val LIT = c(0xFFFFE3A0)
        val GLASS_DARK = c(0xFF26313D)
        val TRUNK = c(0xFF6B4A32)
        val TREE = intArrayOf(c(0xFF4C8C3F), c(0xFF5E9E45), c(0xFF3F7A3A))
        val AUTUMN = intArrayOf(c(0xFFD9822B), c(0xFFC0392B), c(0xFFE6B422), c(0xFFB5651D), c(0xFFD35400))
        val PANTS = c(0xFF34383F)
        const val PITCH_STREET = 58f
        const val PITCH_MAP = 62f
    }

    private fun label(text: String, x: Float, y: Float, size: Float, color: Int, bg: Int = 0, icon: String? = null) {
        val l = if (labelPool.isNotEmpty()) labelPool.removeAt(labelPool.size - 1) else Label("", 0f, 0f, 0f, 0, 0)
        l.text = text; l.x = x; l.y = y; l.size = size; l.color = color; l.bg = bg; l.icon = icon
        labels += l
    }

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
            gpu.setShadow(bbox[0], bbox[1], bbox[2], bbox[3], min(e.city.maxHeight, 140f), if (e.settings.quality >= 2) 2048 else 1536, strength)
        }
        if (gpu == null) batch.begin(canvas)
        for (l in labels) labelPool += l
        labels.clear()

        drawGround()
        collectDrawables()
        sortDrawables()
        for (k in 0 until dCount) {
            val i = order[k]
            when (dType[i]) {
                0 -> drawBuilding(e.city.buildings[dIdx[i]])
                1 -> drawTree(dIdx[i])
                2 -> e.traffic.cars.getOrNull(dIdx[i])?.let {
                    // every sixth sedan is a cab
                    val taxi = it.style == 0 && dIdx[i] % 6 == 0
                    drawCar(it.x, it.y, it.heading, it.length, it.width, it.height, if (taxi) CarPainter.TAXI else it.color, it.style, false,
                        taxi = taxi, braking = it.speed < it.cruise * .45f)
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
            }
        }
        if (gpu == null) batch.flush()
        scene.gpu = null
        if (!mapMode) drawWeather(canvas, w, h)
        drawOverlays(canvas, w, h)
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

    private fun drawGround() {
        // snow lies on sidewalks and parks, roads turn to slush; rain darkens everything
        val snow = scene.snow
        val wet = if (mapMode) 0f else e.skyVis.wet
        val white = scene.lit(0xFFF2F5F8.toInt(), 1f)
        val dark = scene.lit(0xFF2A2D33.toInt(), 1f)
        val sidewalk = scene.mix(scene.mix(scene.lit(SIDEWALK, .97f), white, snow * .9f), dark, wet * .22f)
        val asphalt = scene.mix(scene.mix(scene.lit(ASPHALT, 1f), white, snow * .45f), dark, wet * .3f)
        val grass = scene.mix(scene.lit(GRASS, 1f), white, snow * .95f)
        val pathC = scene.mix(scene.lit(PATH, 1f), white, snow * .9f)
        scene.groundPass = true
        // the GPU draws a sky, so the sea around the city is real geometry there
        if (scene.gpu != null) {
            scene.material = Mat.WATER
            scene.groundRect(bbox[0] - 400f, bbox[1] - 400f, bbox[2] + 400f, bbox[3] + 400f, -.05f, WATER)
        }
        scene.material = Mat.SIDEWALK
        clipRect(-City.AV_HALF - City.SIDEWALK, -City.ST_HALF - City.SIDEWALK, City.WIDTH + City.AV_HALF + City.SIDEWALK, City.HEIGHT + City.ST_HALF + City.SIDEWALK, 0f, sidewalk)
        scene.material = Mat.ASPHALT
        for (i in 0 until City.AVENUES) {
            val x = City.avenueX(i)
            clipRect(x - City.AV_HALF, -City.ST_HALF, x + City.AV_HALF, City.HEIGHT + City.ST_HALF, 0f, asphalt)
        }
        for (k in 0 until City.STREETS) {
            val y = City.streetY(k)
            clipRect(-City.AV_HALF, y - City.ST_HALF, City.WIDTH + City.AV_HALF, y + City.ST_HALF, 0f, asphalt)
        }
        // neighborhood tint (map filters)
        if (mapMode) {
            for (row in 0 until City.ROWS) for (col in 0 until City.COLS) {
                val hood = City.neighborhoodOfBlock(col, row)
                if (e.ui.mapFilters["hood:$hood"] == true) {
                    val x0 = col * City.AV_SPACING + City.AV_HALF; val y0 = row * City.ST_SPACING + City.ST_HALF
                    clipRect(x0, y0, x0 + City.AV_SPACING - 2 * City.AV_HALF, y0 + City.ST_SPACING - 2 * City.ST_HALF, .05f,
                        scene.withAlpha(City.neighborhoods[hood].color, .45f))
                }
            }
        }
        // lots that are visible from above
        val col0 = floor(bbox[0] / City.AV_SPACING).toInt().coerceAtLeast(0)
        val col1 = floor(bbox[2] / City.AV_SPACING).toInt().coerceAtMost(City.COLS - 1)
        val row0 = floor(bbox[1] / City.ST_SPACING).toInt().coerceAtLeast(0)
        val row1 = floor(bbox[3] / City.ST_SPACING).toInt().coerceAtMost(City.ROWS - 1)
        for (row in row0..row1) for (col in col0..col1) {
            for (b in e.city.blocks[row * City.COLS + col]) {
                when (b.kind) {
                    BKind.PARK -> {
                        scene.material = Mat.GRASS
                        clipRect(b.minX, b.minY, b.maxX, b.maxY, .02f, grass)
                        scene.material = Mat.ROOF
                        clipRect(b.cx - 1.5f, b.minY, b.cx + 1.5f, b.maxY, .04f, pathC)
                        clipRect(b.minX, b.cy - 1.5f, b.maxX, b.cy + 1.5f, .04f, pathC)
                        scene.material = Mat.PLAIN
                    }
                    BKind.PARKING -> {
                        scene.material = Mat.ASPHALT
                        clipRect(b.minX, b.minY, b.maxX, b.maxY, .02f, scene.mix(scene.mix(scene.lit(c(0xFF55585E), 1f), white, snow * .6f), dark, wet * .3f))
                        if (!mapMode && proj.dist < 300) {
                            var x = b.minX + 3f
                            val line = scene.lit(WHITE_LINE, .9f)
                            while (x < b.maxX - 1f) {
                                clipRect(x, b.minY + 1f, x + .15f, b.minY + 6f, .04f, line)
                                clipRect(x, b.maxY - 6f, x + .15f, b.maxY - 1f, .04f, line)
                                x += 2.8f
                            }
                        }
                    }
                    BKind.GAS -> clipRect(b.minX, b.minY, b.maxX, b.maxY, .02f, scene.lit(c(0xFF5A5D63), 1f))
                    else -> if (mapMode && b.kind != BKind.PARK) clipRect(b.minX, b.minY, b.maxX, b.maxY, .01f, scene.lit(LOT, 1f))
                }
            }
        }
        scene.material = Mat.PLAIN
        if (proj.dist < 420) drawMarkings()
        drawRoute()
        // night street lights
        if (scene.daylight < .6f && !mapMode) {
            val glow = scene.withAlpha(c(0xFFFFD27A), .10f * (1f - scene.daylight))
            val core = scene.withAlpha(c(0xFFFFE6A8), .12f * (1f - scene.daylight))
            if (proj.dist < 260) {
                val pr = props
                for (row in row0..row1) for (col in col0..col1) for (pi in pr.byBlock[row * City.COLS + col]) {
                    if (!pr.isLamp(pi)) continue
                    val lx = pr.lightX(pi); val ly = pr.lightY(pi)
                    if (lx < bbox[0] - 6f || lx > bbox[2] + 6f || ly < bbox[1] - 6f || ly > bbox[3] + 6f) continue
                    scene.disc(lx, ly, .05f, 4.5f, 4.5f, 12, glow)
                    scene.disc(lx, ly, .06f, 2f, 2f, 10, core)
                }
            }
            forIntersections { x, y, _, _ ->
                scene.disc(x - 12f, y - 8f, .05f, 5f, 5f, 14, glow)
                scene.disc(x - 12f, y - 8f, .06f, 2.2f, 2.2f, 10, core)
                scene.disc(x + 12f, y + 8f, .05f, 5f, 5f, 14, glow)
                scene.disc(x + 12f, y + 8f, .06f, 2.2f, 2.2f, 10, core)
            }
        }
        // shadows
        if (e.settings.shadows && !mapMode && scene.daylight > .15f && proj.dist < 400 && scene.gpu?.shadowOn != true) {
            val shadowColor = scene.withAlpha(0xFF000000.toInt(), .22f * scene.daylight)
            val expand = 60f
            for (row in row0..row1) for (col in col0..col1) {
                for (b in e.city.blocks[row * City.COLS + col]) {
                    if (b.height <= 0f) continue
                    shadow(b.minX, b.minY, b.maxX, b.maxY, b.height, shadowColor)
                }
            }
            if (proj.dist < 250) for (t in e.city.trees) {
                if (t.x < bbox[0] - expand || t.x > bbox[2] + expand || t.y < bbox[1] - expand || t.y > bbox[3] + expand) continue
                scene.disc(t.x + shadowDX * t.h * .8f, t.y + shadowDY * t.h * .8f, .03f, t.r, t.r * .85f, 8, shadowColor)
            }
        }
        scene.groundPass = false
    }

    private val hx = FloatArray(8)
    private val hy = FloatArray(8)
    private val hz = FloatArray(8)
    private val hullX = FloatArray(16)
    private val hullY = FloatArray(16)
    private val sortIdx = IntArray(8)
    private val hidx = IntArray(20)

    private fun shadow(x0: Float, y0: Float, x1: Float, y1: Float, h: Float, color: Int) {
        val ox = shadowDX * h; val oy = shadowDY * h
        hx[0] = x0; hy[0] = y0; hx[1] = x1; hy[1] = y0; hx[2] = x1; hy[2] = y1; hx[3] = x0; hy[3] = y1
        for (i in 0 until 4) { hx[i + 4] = hx[i] + ox; hy[i + 4] = hy[i] + oy }
        // monotone chain convex hull on 8 points (allocation free)
        for (i in 0 until 8) sortIdx[i] = i
        for (i in 1 until 8) {
            val v = sortIdx[i]
            var j = i - 1
            while (j >= 0 && (hx[sortIdx[j]] > hx[v] || (hx[sortIdx[j]] == hx[v] && hy[sortIdx[j]] > hy[v]))) { sortIdx[j + 1] = sortIdx[j]; j-- }
            sortIdx[j + 1] = v
        }
        var k = 0
        for (q in 0 until 8) {
            val i = sortIdx[q]
            while (k >= 2 && cross(hidx[k - 2], hidx[k - 1], i) <= 0) k--
            hidx[k++] = i
        }
        val lower = k + 1
        for (q in 6 downTo 0) {
            val i = sortIdx[q]
            while (k >= lower && cross(hidx[k - 2], hidx[k - 1], i) <= 0) k--
            hidx[k++] = i
        }
        val n = (k - 1).coerceAtMost(8)
        for (i in 0 until n) { hullX[i] = hx[hidx[i]]; hullY[i] = hy[hidx[i]]; hz[i] = .03f }
        scene.poly(n, hullX, hullY, hz, color)
    }

    private fun cross(o: Int, a: Int, b: Int) = (hx[a] - hx[o]) * (hy[b] - hy[o]) - (hy[a] - hy[o]) * (hx[b] - hx[o])

    private inline fun forIntersections(block: (Float, Float, Int, Int) -> Unit) {
        val i0 = floor(bbox[0] / City.AV_SPACING).toInt().coerceAtLeast(0)
        val i1 = ceil(bbox[2] / City.AV_SPACING).toInt().coerceAtMost(City.AVENUES - 1)
        val k0 = floor(bbox[1] / City.ST_SPACING).toInt().coerceAtLeast(0)
        val k1 = ceil(bbox[3] / City.ST_SPACING).toInt().coerceAtMost(City.STREETS - 1)
        for (i in i0..i1) for (k in k0..k1) block(City.avenueX(i), City.streetY(k), i, k)
    }

    private fun drawMarkings() {
        val yellow = scene.lit(YELLOW, 1f)
        val white = scene.lit(WHITE_LINE, .95f)
        val near = proj.dist < 220
        // avenues
        for (i in 0 until City.AVENUES) {
            val x = City.avenueX(i)
            if (x + City.AV_HALF < bbox[0] || x - City.AV_HALF > bbox[2]) continue
            for (k in 0 until City.STREETS - 1) {
                val y0 = City.streetY(k) + City.ST_HALF + 3.5f
                val y1 = City.streetY(k + 1) - City.ST_HALF - 3.5f
                if (y1 < bbox[1] || y0 > bbox[3]) continue
                clipRect(x - .35f, y0, x - .15f, y1, .03f, yellow)
                clipRect(x + .15f, y0, x + .35f, y1, .03f, yellow)
                if (near) {
                    var y = y0 + 1f
                    while (y < y1 - 2f) {
                        clipRect(x - 5.1f, y, x - 4.9f, y + 3f, .03f, white)
                        clipRect(x + 4.9f, y, x + 5.1f, y + 3f, .03f, white)
                        y += 8f
                    }
                }
            }
        }
        // streets
        for (k in 0 until City.STREETS) {
            val y = City.streetY(k)
            if (y + City.ST_HALF < bbox[1] || y - City.ST_HALF > bbox[3]) continue
            for (i in 0 until City.AVENUES - 1) {
                val x0 = City.avenueX(i) + City.AV_HALF + 3.5f
                val x1 = City.avenueX(i + 1) - City.AV_HALF - 3.5f
                if (x1 < bbox[0] || x0 > bbox[2]) continue
                var x = x0 + 1f
                while (x < x1 - 2f) {
                    clipRect(x, y - .1f, x + 3f, y + .1f, .03f, yellow)
                    x += 7f
                }
            }
        }
        if (!near) return
        // manhole covers and storm drains
        val iron = scene.lit(c(0xFF3B3E43), 1f)
        val rim = scene.lit(c(0xFF5A5E64), 1f)
        for (k in 0 until City.STREETS) {
            val y = City.streetY(k)
            if (y + City.ST_HALF < bbox[1] || y - City.ST_HALF > bbox[3]) continue
            for (i in 0 until City.AVENUES - 1) {
                val x0 = City.avenueX(i) + City.AV_HALF; val x1 = City.avenueX(i + 1) - City.AV_HALF
                for (q in 1..2) {
                    val mx = x0 + (x1 - x0) * q / 3f + ((i * 7 + k * 3) % 5) * 2f
                    if (mx < bbox[0] || mx > bbox[2]) continue
                    val my = y + (if ((i + k + q) % 2 == 0) 2.4f else -2.4f)
                    scene.disc(mx, my, .035f, .55f, .55f, 10, rim)
                    scene.disc(mx, my, .04f, .45f, .45f, 10, iron)
                }
                for (side in intArrayOf(-1, 1)) {
                    val dx = x0 + 6f
                    if (dx < bbox[0] || dx > bbox[2]) continue
                    clipRect(dx, y + side * (City.ST_HALF - .1f) - .25f, dx + 1.2f, y + side * (City.ST_HALF - .1f) + .25f, .035f, iron)
                }
            }
        }
        // crosswalks
        forIntersections { x, y, _, _ ->
            var sx = x - City.AV_HALF + .4f
            while (sx < x + City.AV_HALF - .4f) {
                clipRect(sx, y - City.ST_HALF - 3.2f, sx + .6f, y - City.ST_HALF - .5f, .04f, white)
                clipRect(sx, y + City.ST_HALF + .5f, sx + .6f, y + City.ST_HALF + 3.2f, .04f, white)
                sx += 1.25f
            }
            var sy = y - City.ST_HALF + .4f
            while (sy < y + City.ST_HALF - .4f) {
                clipRect(x - City.AV_HALF - 3.2f, sy, x - City.AV_HALF - .5f, sy + .6f, .04f, white)
                clipRect(x + City.AV_HALF + .5f, sy, x + City.AV_HALF + 3.2f, sy + .6f, .04f, white)
                sy += 1.25f
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

    private fun collectDrawables() {
        dCount = 0
        val city = e.city
        val p = e.state.player
        val m = min(city.maxHeight, 130f) * .9f
        val bx0 = bbox[0] - m; val by0 = bbox[1] - m; val bx1 = bbox[2] + m; val by1 = bbox[3] + m
        val col0 = floor(bx0 / City.AV_SPACING).toInt().coerceAtLeast(0)
        val col1 = floor(bx1 / City.AV_SPACING).toInt().coerceAtMost(City.COLS - 1)
        val row0 = floor(by0 / City.ST_SPACING).toInt().coerceAtLeast(0)
        val row1 = floor(by1 / City.ST_SPACING).toInt().coerceAtMost(City.ROWS - 1)
        for (row in row0..row1) for (col in col0..col1) {
            for (b in city.blocks[row * City.COLS + col]) {
                if (b.kind == BKind.PARK) continue
                if (b.kind == BKind.PARKING) {
                    if (!mapMode && proj.dist < 300) {
                        val slots = ((b.width - 4f) / 2.8f).toInt()
                        for (sIdx in 0 until min(slots, 12)) if ((b.id * 31 + sIdx * 17) % 10 < 6) {
                            add(7, b.id * 32 + sIdx, keyFor(b.minX + 4.4f + sIdx * 2.8f, b.minY + 3.5f))
                        }
                    }
                    continue
                }
                add(0, b.id, keyForRect(b))
            }
        }
        val farLod = mapMode || proj.dist > 450
        if (!farLod) {
            for ((i, t) in city.trees.withIndex()) {
                if (t.x < bbox[0] || t.x > bbox[2] || t.y < bbox[1] - 10f || t.y > bbox[3]) continue
                add(1, i, keyFor(t.x, t.y))
            }
        }
        if (!mapMode) {
            for ((i, a) in e.traffic.cars.withIndex()) if (a.x in bbox[0]..bbox[2] && a.y in bbox[1]..bbox[3]) add(2, i, keyFor(a.x, a.y))
            if (proj.dist < 260) for ((i, a) in e.traffic.peds.withIndex()) if (!a.hidden && a.x in bbox[0]..bbox[2] && a.y in bbox[1]..bbox[3]) add(3, i, keyFor(a.x, a.y))
            if (proj.dist < 230) {
                val pr = props
                val col0 = floor((bbox[0] - 4f) / City.AV_SPACING).toInt().coerceAtLeast(0)
                val col1 = floor((bbox[2] + 4f) / City.AV_SPACING).toInt().coerceAtMost(City.COLS - 1)
                val row0 = floor((bbox[1] - 4f) / City.ST_SPACING).toInt().coerceAtLeast(0)
                val row1 = floor((bbox[3] + 4f) / City.ST_SPACING).toInt().coerceAtMost(City.ROWS - 1)
                for (row in row0..row1) for (col in col0..col1) for (pi in pr.byBlock[row * City.COLS + col]) {
                    val x = pr.x[pi]; val y = pr.y[pi]
                    if (x < bbox[0] - 3f || x > bbox[2] + 3f || y < bbox[1] - 6f || y > bbox[3] + 3f) continue
                    add(8, pi, keyFor(x, y))
                }
            }
            if (proj.dist < 240) forIntersections { x, y, i, k ->
                add(6, (i * 32 + k) * 4 + 0, keyFor(x - 11f, y - 7.5f))
                add(6, (i * 32 + k) * 4 + 1, keyFor(x + 11f, y + 7.5f))
            }
        }
        for (v in e.state.vehicles) if (v.x in bbox[0] - 5..bbox[2] + 5 && v.y in bbox[1] - 5..bbox[3] + 5) add(5, v.id, keyFor(v.x, v.y))
        playerKey = keyFor(p.x, p.y)
        if (e.hasGame && p.driving < 0 && p.inBuilding < 0 && !mapMode) add(4, 0, playerKey)
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
        if (!mapMode && e.hasGame && p.inBuilding < 0 && b.height > 5f && keyForRect(b) < playerKey) {
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
            scene.box(b.minX, b.minY, 0f, b.maxX, b.maxY, b.height, roof, facade, alpha)
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
        val cxp = if (face <= 1) b.cx else (if (face == 2) b.minX else b.maxX)
        val cyp = if (face >= 2) b.cy else (if (face == 0) b.minY else b.maxY)
        if (proj.project(cxp + ox * .2f, cyp + oy * .2f, 3.8f, tmp)) {
            val size = (proj.focal * .75f / tmp[2]).coerceAtMost(15f * density)
            if (size > 6.5f * density * .6f) label(name, tmp[0], tmp[1] + size * .35f, size, 0xFFFFFFFF.toInt())
        }
    }

    internal fun forRentLabel(b: Building, face: Int) {
        if (proj.project(b.doorX, if (face == 0) b.minY else b.maxY, 3.6f, tmp)) {
            val size = (proj.focal * .6f / tmp[2]).coerceAtMost(12f * density)
            if (size > 4f * density) label("FOR RENT", tmp[0], tmp[1], size, c(0xFF27AE60), 0xEEFFFFFF.toInt())
        }
    }

    internal fun gasLabel(b: Building, y0: Float, y1: Float) {
        if (mapMode) return
        if (proj.project(b.cx, if (b.facesNorth) y0 else y1, 6.3f, tmp)) {
            val size = (proj.focal * .8f / tmp[2]).coerceAtMost(14f * density)
            if (size > 4f * density) label("Fuel Up", tmp[0], tmp[1], size, 0xFFFFFFFF.toInt(), icon = "place_gas")
        }
    }

    // ------------------------------------------------------------------ props

    private fun drawTree(i: Int) {
        val pr = props
        if (pr.hiddenTree[i]) return
        val t = e.city.trees[i]
        val kind = pr.treeKind[i]
        val near = keyFor(t.x, t.y) < 70f * 70f
        scene.sphereDetail = if (near) 2 else 1
        val month = e.weather.month()
        val white = scene.lit(0xFFF2F5F8.toInt(), 1f)
        // street trees grow out of a pit with a low iron guard
        if (kind == 0 || kind == 3) {
            scene.groundRect(t.x - .7f, t.y - .7f, t.x + .7f, t.y + .7f, .025f, scene.mix(scene.lit(c(0xFF4A3A2C), 1f), white, scene.snow * .8f))
            if (near) {
                val g = c(0xFF2A2D31)
                scene.box(t.x - .72f, t.y - .72f, 0f, t.x + .72f, t.y - .66f, .35f, g, g)
                scene.box(t.x - .72f, t.y + .66f, 0f, t.x + .72f, t.y + .72f, .35f, g, g)
            }
        }
        if (kind == 2) {
            // conifer: stacked cones, green all year
            scene.material = Mat.FOLIAGE
            scene.box(t.x - .14f, t.y - .14f, 0f, t.x + .14f, t.y + .14f, t.h * .3f, TRUNK, TRUNK)
            val g = TREE[2]
            scene.cone(t.x, t.y, t.r * .95f, t.h * .25f, t.h * .75f, 7, scene.mix(g, c(0xFF2E5E36), .3f), .5f)
            scene.cone(t.x, t.y, t.r * .72f, t.h * .55f, t.h * 1.05f, 7, g, .6f)
            scene.cone(t.x, t.y, t.r * .45f, t.h * .85f, t.h * 1.3f, 6, scene.mix(g, c(0xFF7FB069), .2f), .7f)
            scene.material = Mat.PLAIN
            return
        }
        // deciduous trees follow the seasons: blossom and fresh leaves in spring, deep green in summer,
        // reds and golds in autumn and bare branches in winter
        val bare = month == 11 || month == 0 || month == 1 || (month == 2 && i % 3 != 0)
        scene.box(t.x - .15f, t.y - .15f, 0f, t.x + .15f, t.y + .15f, t.h * .55f, TRUNK, TRUNK)
        if (near || bare) {
            // a few branches fork out of the trunk
            for (k in 0 until 4) {
                val a = (k * 1.5708f + i * .7f)
                val bx = t.x + cos(a) * t.r * .6f; val by = t.y + sin(a) * t.r * .6f
                val z0 = t.h * .45f; val z1 = t.h * (.78f + (k % 2) * .12f)
                scene.quad(t.x - .06f, t.y, z0, t.x + .06f, t.y, z0, bx + .03f, by, z1, bx - .03f, by, z1, scene.lit(TRUNK, .9f))
                scene.quad(t.x, t.y - .06f, z0, t.x, t.y + .06f, z0, bx, by + .03f, z1, bx, by - .03f, z1, scene.lit(TRUNK, .8f))
            }
        }
        scene.material = Mat.FOLIAGE
        if (bare) {
            // a haze of twigs, dusted with snow
            scene.blob(t.x, t.y, t.h * .78f, t.r * .9f, scene.withAlpha(scene.mix(scene.lit(c(0xFF6B5444), 1f), white, scene.snow * .5f), .35f), 10)
            scene.material = Mat.PLAIN
            return
        }
        val base = when {
            kind == 3 && (month == 3 || month == 2) -> if (i % 2 == 0) c(0xFFF4B6C8) else c(0xFFF7E1E8) // cherry blossom
            month == 3 || month == 4 -> scene.mix(TREE[t.tone], c(0xFF9CCB5A), .35f)
            month in 8..10 -> AUTUMN[(i + t.tone) % AUTUMN.size].let { if (month == 8) scene.mix(TREE[t.tone], it, .45f) else it }
            else -> TREE[t.tone]
        }
        val z = t.h * .75f
        scene.blob(t.x, t.y, z - t.r * .15f, t.r, scene.mix(scene.lit(base, .78f), white, scene.snow * .35f), 12)
        scene.blob(t.x, t.y, z, t.r * .78f, scene.mix(scene.lit(base, .95f), white, scene.snow * .5f), 11, -.12f, -.2f)
        if (near) {
            scene.blob(t.x, t.y, z + t.r * .2f, t.r * .45f, scene.mix(scene.lit(base, 1.12f), white, scene.snow * .7f), 9, -.35f * scene.sunX, -.55f)
            scene.blob(t.x, t.y, z, t.r * .35f, scene.mix(scene.lit(base, .88f), white, scene.snow * .4f), 8, .55f, -.05f)
        }
        scene.material = Mat.PLAIN
    }

    private fun drawCar(x: Float, y: Float, heading: Float, len: Float, wid: Float, hgt: Float, color: Int, style: Int, owned: Boolean,
                        taxi: Boolean = false, braking: Boolean = false) {
        val lod = when (e.settings.quality) { 0 -> 45f; 1 -> 80f; else -> 120f }
        val near = !mapMode && keyFor(x, y) < lod * lod
        cars.draw(x, y, heading, len, wid, hgt, color, style, near, taxi, braking)
    }

    private fun drawParkedCar(code: Int) {
        val b = e.city.buildings[code / 32]
        val slot = code % 32
        val x = b.minX + 4.4f + slot * 2.8f
        val top = slot % 2 == 0
        val y = if (top) b.minY + 3.5f else b.maxY - 3.5f
        val color = intArrayOf(c(0xFFC0392B), c(0xFFE0E0E0), c(0xFF2C3E50), c(0xFF7F8C8D), c(0xFF1F3A93), c(0xFF212121))[(b.id + slot) % 6]
        drawCar(x, y, 0f, 4.3f, 1.85f, 1.45f, color, if ((b.id + slot) % 5 == 0) 1 else 0, false)
    }

    private fun drawTrafficLight(code: Int) {
        val corner = code % 4
        val ik = code / 4
        val i = ik / 32; val k = ik % 32
        val x = City.avenueX(i) + if (corner == 0) -11f else 11f
        val y = City.streetY(k) + if (corner == 0) -7.5f else 7.5f
        val pole = c(0xFF3A3D42)
        scene.box(x - .1f, y - .1f, 0f, x + .1f, y + .1f, 4.4f, pole, pole)
        // street lamp on the same corner
        val lx = x + if (corner == 0) -1f else 1f
        val ly = y + if (corner == 0) -.5f else .5f
        scene.box(lx - .08f, ly - .08f, 0f, lx + .08f, ly + .08f, 5.2f, pole, pole)
        val lampOn = scene.daylight < .5f
        scene.box(lx - .35f, ly - .2f, 5.0f, lx + .35f, ly + .2f, 5.3f, if (lampOn) c(0xFFFFE6A8) else c(0xFF55595F), pole)
        val state = e.traffic.light(if (corner == 0) 0 else 1)
        val lightColor = when (state) { 0 -> c(0xFF2EE87A); 1 -> c(0xFFFFD34D); else -> c(0xFFFF4D4D) }
        scene.box(x - .25f, y - .25f, 3.6f, x + .25f, y + .25f, 4.5f, lightColor, c(0xFF22252A))
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

    private fun drawOverlays(canvas: Canvas, w: Int, h: Int) {
        val s = e.state
        val sel = e.city.building(e.ui.selected)
        if (sel != null && !mapMode) outlineBuilding(canvas, sel)
        // business / place signs
        for (l in labels) {
            textPaint.textSize = l.size
            if (l.bg != 0) {
                val tw = textPaint.measureText(l.text)
                pillPaint.color = l.bg
                rect.set(l.x - tw / 2 - l.size * .4f, l.y - l.size * 1.05f, l.x + tw / 2 + l.size * .4f, l.y + l.size * .35f)
                canvas.drawRoundRect(rect, l.size * .3f, l.size * .3f, pillPaint)
                textPaint.color = l.color
                canvas.drawText(l.text, l.x, l.y, textPaint)
            } else {
                // an icon sits left of the text, the pair centred on the label point
                val ic = l.icon
                val shift = if (ic != null) l.size * .65f else 0f
                if (ic != null) {
                    val tw = textPaint.measureText(l.text)
                    IconCanvas.draw(canvas, ic, l.x - tw / 2 - l.size * .1f + shift - l.size * .55f + 1.5f, l.y - l.size * .35f + 1.5f, l.size * 1.1f, 0xAA000000.toInt())
                    IconCanvas.draw(canvas, ic, l.x - tw / 2 - l.size * .1f + shift - l.size * .55f, l.y - l.size * .35f, l.size * 1.1f, l.color)
                }
                textPaint.color = 0xAA000000.toInt()
                canvas.drawText(l.text, l.x + shift + 1.5f, l.y + 1.5f, textPaint)
                textPaint.color = l.color
                canvas.drawText(l.text, l.x + shift, l.y, textPaint)
            }
        }
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
            canvas.drawText("${e.fps.roundToInt()} FPS · ${batch.trianglesDrawn} tris", 8f * density, h - 6f * density, textPaint)
            textPaint.textAlign = Paint.Align.CENTER
        }
    }

    private fun outlineBuilding(canvas: Canvas, b: Building) {
        val h = max(b.height, .3f)
        val xs = floatArrayOf(b.minX, b.maxX, b.maxX, b.minX)
        val ys = floatArrayOf(b.minY, b.minY, b.maxY, b.maxY)
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

    private fun drawMapOverlay(canvas: Canvas) {
        val s = e.state
        val f = e.ui.mapFilters
        // street names
        val roadFont = (proj.focal * 7f / proj.dist).coerceAtMost(15f * density)
        if (roadFont > 6f * density) {
            textPaint.color = 0xCCFFFFFF.toInt()
            textPaint.textSize = roadFont
            for (i in 0 until City.AVENUES) for (k in 1 until City.STREETS step 3) {
                val x = City.avenueX(i); val y = City.streetY(k) + City.ST_SPACING / 2
                roadText(canvas, City.avenueNames[i].uppercase(), x, y - 10f, x, y + 10f)
            }
            for (k in 0 until City.STREETS) for (i in 0 until City.AVENUES - 1 step 2) {
                val x = City.avenueX(i) + City.AV_SPACING / 2; val y = City.streetY(k)
                roadText(canvas, City.streetName(k).uppercase(), x - 10f, y, x + 10f, y)
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
