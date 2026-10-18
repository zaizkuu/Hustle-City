package com.hustlecity.game.render.gpu

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Fills a frame's light, sky and fog uniforms from the time of day and the weather. */
object GpuLighting {
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun mix3(out: FloatArray, a: FloatArray, b: FloatArray, t: Float) { for (i in 0..2) out[i] = lerp(a[i], b[i], t) }
    private fun set(out: FloatArray, r: Float, g: Float, b: Float) { out[0] = r; out[1] = g; out[2] = b }

    private val tmpA = FloatArray(3)
    private val tmpB = FloatArray(3)

    private val SUN_NOON = floatArrayOf(1.02f, .96f, .86f)
    private val SUN_LOW = floatArrayOf(1.0f, .66f, .42f)
    private val MOON = floatArrayOf(.16f, .2f, .32f)
    private val SKY_DAY = floatArrayOf(.44f, .52f, .64f)
    private val SKY_NIGHT = floatArrayOf(.09f, .11f, .19f)
    private val GROUND_DAY = floatArrayOf(.32f, .29f, .26f)
    private val GROUND_NIGHT = floatArrayOf(.05f, .05f, .07f)
    private val TOP_DAY = floatArrayOf(.33f, .56f, .86f)
    private val HORIZON_DAY = floatArrayOf(.72f, .83f, .94f)
    private val HORIZON_DUSK = floatArrayOf(.98f, .66f, .44f)
    private val TOP_NIGHT = floatArrayOf(.02f, .04f, .1f)
    private val HORIZON_NIGHT = floatArrayOf(.09f, .12f, .21f)
    private val OVERCAST_TOP = floatArrayOf(.55f, .59f, .64f)
    private val OVERCAST_HORIZON = floatArrayOf(.7f, .73f, .77f)

    /**
     * City and map views. [hour] is fractional (0..24); [daylight] 0 night .. 1 day; weather terms 0..1;
     * [fogRgb] is the scene's fog color (ARGB) and [fog]/[fogStart]/[fogEnd] its haze.
     */
    fun city(f: GpuFrame, hour: Float, daylight: Float, cloud: Float, rain: Float, storm: Float, flash: Float, wet: Float, snow: Float,
             fogRgb: Int, fog: Float, fogStart: Float, fogEnd: Float, map: Boolean) {
        f.mode = if (map) GpuFrame.MODE_MAP else GpuFrame.MODE_CITY
        val d = daylight.coerceIn(0f, 1f)
        f.daylight = d
        f.wet = wet.coerceIn(0f, 1f)
        // the sun climbs from the east in the morning to the west in the evening; at night a cool moon from above
        val t = ((hour - 6f) / 12f).coerceIn(0f, 1f)
        val ang = (t * Math.PI).toFloat()
        val el = max(.12f, sin(ang)) * 1.05f
        val hx = cos(ang); val hy = max(.25f, sin(ang)) * .8f
        val hl = sqrt(hx * hx + hy * hy).coerceAtLeast(1e-3f)
        if (d > .15f) set(f.sunDir, hx / hl * cos(el), hy / hl * cos(el), sin(el)) else set(f.sunDir, .35f, .55f, .76f)
        normalize(f.sunDir)
        // warm and low at the ends of the day
        val low = (1f - sin(ang)).coerceIn(0f, 1f)
        mix3(tmpA, SUN_NOON, SUN_LOW, low * low)
        mix3(f.sunColor, MOON, tmpA, d)
        val overcast = (cloud * .7f + rain * .25f + storm * .3f).coerceIn(0f, .85f)
        for (i in 0..2) f.sunColor[i] *= (1f - overcast)
        mix3(f.skyAmbient, SKY_NIGHT, SKY_DAY, d)
        mix3(f.groundAmbient, GROUND_NIGHT, GROUND_DAY, d)
        // clouds turn direct sun into soft skylight; snow on the ground bounces light back up
        for (i in 0..2) {
            f.skyAmbient[i] *= 1f + overcast * .55f
            f.groundAmbient[i] = f.groundAmbient[i] * (1f + overcast * .3f) + snow * .12f * d
            f.skyAmbient[i] += flash * .9f
        }
        f.exposure = if (map) 1.05f else lerp(1.25f, 1f, d)
        // sky gradient (display space)
        mix3(tmpA, TOP_NIGHT, TOP_DAY, d)
        mix3(tmpB, HORIZON_NIGHT, HORIZON_DAY, d)
        val dusk = (low * low * d * (1f - overcast)).coerceIn(0f, 1f)
        mix3(tmpB, tmpB, HORIZON_DUSK, dusk * .8f)
        mix3(f.skyTop, tmpA, OVERCAST_TOP, overcast * d + overcast * .4f * (1f - d))
        mix3(f.skyHorizon, tmpB, OVERCAST_HORIZON, overcast * d + overcast * .4f * (1f - d))
        for (i in 0..2) { f.skyTop[i] = min(1f, f.skyTop[i] + flash * .5f); f.skyHorizon[i] = min(1f, f.skyHorizon[i] + flash * .5f) }
        // haze: the scene's fog color, dimmed at night; also used below the horizon
        val fr = ((fogRgb shr 16) and 255) / 255f; val fg = ((fogRgb shr 8) and 255) / 255f; val fb = (fogRgb and 255) / 255f
        val dim = .18f + .82f * d
        set(f.fogColor, fr * dim, fg * dim, fb * dim * (if (d < .5f) 1.15f else 1f))
        mix3(f.fogColor, f.fogColor, f.skyHorizon, .35f)
        f.fogAmount = if (map) 0f else max(fog, .18f)
        f.fogStart = if (fog > .01f) fogStart else 260f
        f.fogEnd = if (fog > .01f) fogEnd else 1400f
        f.clearColor = 0xFF27445C.toInt()
    }

    /** Interiors: warm ceiling light from above the front, soft fill, no haze. */
    fun interior(f: GpuFrame, clearColor: Int) {
        f.mode = GpuFrame.MODE_INTERIOR
        f.daylight = 1f
        f.wet = 0f
        set(f.sunDir, .25f, .55f, .8f); normalize(f.sunDir)
        set(f.sunColor, .78f, .74f, .66f)
        set(f.skyAmbient, .58f, .58f, .62f)
        set(f.groundAmbient, .36f, .34f, .31f)
        f.exposure = 1.08f
        f.fogAmount = 0f
        f.clearColor = clearColor
    }

    private fun normalize(v: FloatArray) {
        val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).coerceAtLeast(1e-6f)
        v[0] /= l; v[1] /= l; v[2] /= l
    }
}
