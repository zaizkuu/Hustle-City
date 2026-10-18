package com.hustlecity.game.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.hustlecity.game.engine.AppId
import com.hustlecity.game.engine.GameEngine
import com.hustlecity.game.render.InteriorRenderer
import com.hustlecity.game.render.WorldRenderer
import com.hustlecity.game.ui.apps.MapsOverlay
import com.hustlecity.game.ui.apps.PhoneAppScreen
import com.hustlecity.game.ui.hud.ActionButtons
import com.hustlecity.game.ui.hud.BuildingCard
import com.hustlecity.game.ui.hud.DaySummaryCard
import com.hustlecity.game.ui.hud.DesignerBar
import com.hustlecity.game.ui.hud.EndingOverlay
import com.hustlecity.game.ui.hud.TipCard
import com.hustlecity.game.ui.hud.Dialogs
import com.hustlecity.game.ui.hud.InteriorPanel
import com.hustlecity.game.ui.hud.Joystick
import com.hustlecity.game.ui.hud.LocationLabel
import com.hustlecity.game.ui.hud.ObjectivesPanel
import com.hustlecity.game.ui.hud.PhonePanel
import com.hustlecity.game.ui.hud.SkipOverlay
import com.hustlecity.game.ui.hud.Toasts
import com.hustlecity.game.ui.hud.TopBar
import com.hustlecity.game.ui.hud.DrivingControls
import com.hustlecity.game.ui.places.PanelHost
import kotlinx.coroutines.isActive
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot

class Renderers(e: GameEngine) {
    val world = WorldRenderer(e)
    val interior = InteriorRenderer(e)
}

@Composable
fun GameScreen(e: GameEngine) {
    @Suppress("UNUSED_VARIABLE") val tick = e.ui.tick
    val renderers = remember { Renderers(e) }
    val inside = e.state.player.inBuilding >= 0 && e.interior != null
    Box(Modifier.fillMaxSize()) {
        WorldCanvas(e, renderers, interactive = true)
        val app = e.ui.app
        when {
            app == AppId.MAPS -> MapsOverlay(e, renderers)
            app != null -> PhoneAppScreen(e, app)
            else -> {
                Column(Modifier.align(Alignment.TopStart).padding(start = 8.dp, end = 8.dp, top = 6.dp)) {
                    TopBar(e)
                    if (!e.ui.designer) ObjectivesPanel(e, Modifier.padding(top = 6.dp).width(236.dp))
                }
                // tips and toasts sit in the gap between the objectives panel and the right-hand panels
                val leftGap = if (!e.ui.designer && e.ui.objectivesOpen) 252.dp else 8.dp
                val rightGap = if (e.ui.phoneOpen || inside || e.ui.selected >= 0) 186.dp else 8.dp
                Column(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 56.dp, start = leftGap, end = rightGap),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!inside) com.hustlecity.game.ui.hud.GpsBanner(e, Modifier.padding(bottom = 4.dp))
                    TipCard(e)
                    Toasts(e)
                }
                if (inside) {
                    if (e.ui.designer) DesignerBar(e, Modifier.align(Alignment.BottomCenter))
                    else InteriorPanel(e, Modifier.align(Alignment.TopEnd).padding(top = 58.dp, end = 8.dp))
                } else {
                    if (e.state.player.driving >= 0) {
                        DrivingControls(e)
                    } else {
                        Joystick(e, Modifier.align(if (e.settings.leftHanded) Alignment.BottomEnd else Alignment.BottomStart).padding(18.dp))
                        LocationLabel(e, Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
                        ActionButtons(e, Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp))
                    }
                    BuildingCard(e, Modifier.align(Alignment.TopEnd).padding(top = 58.dp, end = 8.dp))
                }
                val minimap = e.settings.minimap && !inside && e.ui.selected < 0 && e.skip == null
                if (minimap) com.hustlecity.game.ui.hud.MiniMap(e, Modifier.align(if (e.settings.leftHanded) Alignment.TopStart else Alignment.TopEnd)
                    .padding(top = 58.dp, end = 10.dp, start = if (e.settings.leftHanded) 250.dp else 0.dp))
                if (!e.ui.designer) PhonePanel(e, Modifier.align(if (e.settings.leftHanded) Alignment.BottomStart else Alignment.BottomEnd).padding(8.dp), reserveTop = if (minimap) 138 else 0)
            }
        }
        e.ui.panel?.let { PanelHost(e, it) }
        DaySummaryCard(e, Modifier.align(Alignment.Center))
        if (e.ui.skipping) SkipOverlay(e)
        EndingOverlay(e)
        Dialogs(e)
    }
}

/** The 3D view: runs the frame loop and draws either the city or the current interior. */
@Composable
fun WorldCanvas(e: GameEngine, r: Renderers, interactive: Boolean) {
    val frame = remember { mutableLongStateOf(0L) }
    val density = LocalDensity.current.density
    LaunchedEffect(Unit) {
        var last = 0L
        var acc = 0f
        while (isActive) {
            androidx.compose.runtime.withFrameNanos { t ->
                val dt = if (last == 0L) 1 / 60f else (t - last) / 1e9f
                last = t
                acc += dt
                val minFrame = if (e.settings.fpsCap <= 30) 1f / 31f else 0f
                if (acc >= minFrame) {
                    e.frame(acc)
                    acc = 0f
                    frame.longValue = t
                    if (interactive) e.ui.frameTime = t
                }
            }
        }
    }
    var mod = Modifier.fillMaxSize()
    if (interactive) mod = mod.pointerInput(Unit) { worldGestures(e, r) }
    androidx.compose.foundation.layout.Spacer(
        mod.drawBehind {
            frame.longValue
            drawIntoCanvas { c ->
                val w = size.width.toInt(); val h = size.height.toInt()
                if (e.interior != null && e.state.player.inBuilding >= 0) r.interior.draw(c.nativeCanvas, w, h)
                else r.world.draw(c.nativeCanvas, w, h, density)
            }
        },
    )
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.worldGestures(e: GameEngine, r: Renderers) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var moved = false
        var last = down.position
        var lastDist = 0f
        var lastAngle = 0f
        var count = 1
        var total = 0f
        val slop = viewConfiguration.touchSlop
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            var cx = 0f; var cy = 0f
            for (p in pressed) { cx += p.position.x; cy += p.position.y }
            val centroid = Offset(cx / pressed.size, cy / pressed.size)
            if (pressed.size >= 2) {
                val a = pressed[0].position; val b = pressed[1].position
                val d = hypot(b.x - a.x, b.y - a.y)
                val ang = atan2(b.y - a.y, b.x - a.x)
                if (count >= 2 && lastDist > 0f) {
                    onZoom(e, lastDist / d.coerceAtLeast(1f))
                    var da = ang - lastAngle
                    if (da > Math.PI) da -= (2 * Math.PI).toFloat()
                    if (da < -Math.PI) da += (2 * Math.PI).toFloat()
                    onRotate(e, -da)
                }
                lastDist = d; lastAngle = ang
                moved = true
            } else {
                if (count >= 2) last = centroid
                val delta = centroid - last
                total += delta.getDistance()
                if (moved || total > slop) {
                    moved = true
                    onPan(e, r, last, centroid)
                }
            }
            count = pressed.size
            last = centroid
            event.changes.forEach { it.consume() }
        }
        if (!moved) onTap(e, r, down.position)
    }
}

private fun onZoom(e: GameEngine, factor: Float) {
    val sc = e.interior
    when {
        sc != null && e.state.player.inBuilding >= 0 -> sc.zoom = (sc.zoom / factor).coerceIn(.6f, 2.4f)
        e.ui.app == AppId.MAPS -> e.camera.mapDist = (e.camera.mapDist * factor).coerceIn(220f, 1500f)
        else -> e.camera.zoom = (e.camera.zoom * factor).coerceIn(26f, 230f)
    }
}

private fun onRotate(e: GameEngine, da: Float) {
    val sc = e.interior
    if (sc != null && e.state.player.inBuilding >= 0) sc.yaw += da
    else if (e.ui.app != AppId.MAPS) e.camera.yaw += da
}

private val panTmp0 = FloatArray(2)
private val panTmp1 = FloatArray(2)

private fun onPan(e: GameEngine, r: Renderers, from: Offset, to: Offset) {
    val sc = e.interior
    if (sc != null && e.state.player.inBuilding >= 0) {
        sc.yaw += (to.x - from.x) * .006f
        return
    }
    val proj = r.world.proj
    if (!proj.unproject(from.x, from.y, panTmp0) || !proj.unproject(to.x, to.y, panTmp1)) return
    val dx = panTmp1[0] - panTmp0[0]
    val dy = panTmp1[1] - panTmp0[1]
    if (e.ui.app == AppId.MAPS) {
        e.camera.mapX = (e.camera.mapX - dx).coerceIn(-100f, com.hustlecity.game.world.City.WIDTH + 100f)
        e.camera.mapY = (e.camera.mapY - dy).coerceIn(-100f, com.hustlecity.game.world.City.HEIGHT + 100f)
    } else {
        e.camera.panX = (e.camera.panX - dx).coerceIn(-300f, 300f)
        e.camera.panY = (e.camera.panY - dy).coerceIn(-300f, 300f)
    }
}

private fun onTap(e: GameEngine, r: Renderers, pos: Offset) {
    val sc = e.interior
    if (sc != null && e.state.player.inBuilding >= 0) {
        val out = FloatArray(2)
        if (!r.interior.proj.unproject(pos.x, pos.y, out)) return
        val tx = floor(out[0]).toInt(); val ty = floor(out[1]).toInt()
        if (e.ui.designer) {
            if (sc.ghostId != null) sc.moveGhostTo(tx, ty)
            else sc.selectedIndex = sc.itemAt(tx, ty)
        } else if (tx in 0 until sc.w && ty in 0 until sc.d) {
            sc.walkPlayerTo(tx, ty)
        }
        e.bump()
        return
    }
    val id = e.pickBuilding(r.world.proj, pos.x, pos.y)
    e.ui.selected = if (id == e.ui.selected && e.ui.app != AppId.MAPS) -1 else id
    e.bump()
}
