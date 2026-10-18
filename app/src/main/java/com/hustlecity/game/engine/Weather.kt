package com.hustlecity.game.engine

import com.hustlecity.game.data.BusinessTypeDef
import com.hustlecity.game.model.MINUTES_PER_DAY
import com.hustlecity.game.model.Sky
import com.hustlecity.game.model.WeatherSpan
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Seasonal weather. The calendar starts on April 1st with 30-day months. Weather comes in spans of a few hours
 * planned four days ahead (so the forecast is honest), and it changes who goes out and what they buy.
 */
class WeatherSystem(private val e: GameEngine) {
    companion object {
        /** Average temperature (°C) per month, January first. */
        private val MONTH_TEMP = doubleArrayOf(.5, 2.0, 6.5, 12.5, 18.0, 23.5, 26.5, 25.5, 21.5, 15.0, 9.0, 3.5)
        val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        private const val PLAN_AHEAD = 4L * MINUTES_PER_DAY
    }

    /** Month index (0 = January) of a game day. */
    fun month(day: Int = e.state.day) = ((day - 1) / 30 + 3) % 12

    fun dateLabel(day: Int = e.state.day) = "${MONTHS[month(day)]} ${(day - 1) % 30 + 1}"

    fun season(day: Int = e.state.day) = when (month(day)) { 11, 0, 1 -> "Winter"; 2, 3, 4 -> "Spring"; 5, 6, 7 -> "Summer"; else -> "Autumn" }

    // the simulation asks for the current weather many times a minute
    private var cacheMinute = Long.MIN_VALUE
    private var cacheSky = Sky.CLEAR
    private var cacheTemp = 15.0

    private fun refresh() {
        val m = e.state.minute
        if (m == cacheMinute) return
        cacheMinute = m
        cacheSky = skyAt(m)
        cacheTemp = tempAt(m)
    }

    fun sky(minute: Long = e.state.minute): Sky = if (minute == e.state.minute) { refresh(); cacheSky } else skyAt(minute)

    private fun skyAt(minute: Long): Sky {
        for (sp in e.state.weather) if (minute >= sp.start && minute < sp.end) return sp.sky
        return Sky.CLEAR
    }

    fun temperature(minute: Long = e.state.minute): Double = if (minute == e.state.minute) { refresh(); cacheTemp } else tempAt(minute)

    /** Temperature in °C: the month's average, a daily swing peaking mid-afternoon, a day-to-day wobble and the sky. */
    private fun tempAt(minute: Long): Double {
        val day = (minute / MINUTES_PER_DAY).toInt() + 1
        val m = month(day)
        // blend toward the next month so seasons change gradually
        val frac = ((day - 1) % 30) / 30.0
        val base = MONTH_TEMP[m] * (1 - frac) + MONTH_TEMP[(m + 1) % 12] * frac
        val hour = (minute % MINUTES_PER_DAY) / 60.0
        val swing = 4.5 * sin((hour - 9.0) / 24.0 * 2 * PI)
        val wobble = Random(e.state.seed * 31 + day).nextDouble(-3.0, 3.0)
        val skyAdj = when (skyAt(minute)) { Sky.CLEAR -> 1.0; Sky.CLOUDY -> -1.0; Sky.RAIN -> -3.0; Sky.STORM -> -4.0; Sky.SNOW -> -2.0; Sky.FOG -> -1.0 }
        return base + swing + wobble + skyAdj
    }

    /** Plans the weather four days ahead and forgets spans that ended yesterday. */
    fun ensure() {
        cacheMinute = Long.MIN_VALUE
        val s = e.state
        s.weather.removeAll { it.end < s.minute - MINUTES_PER_DAY }
        var t = s.weather.lastOrNull()?.end ?: (s.minute - s.minute % 60)
        var prev = s.weather.lastOrNull()?.sky
        while (t < s.minute + PLAN_AHEAD) {
            val r = Random(s.seed xor (t * 7919))
            val sky = pick(t, prev, r)
            val hours = when (sky) {
                Sky.CLEAR -> 6 + r.nextInt(14)
                Sky.CLOUDY -> 4 + r.nextInt(9)
                Sky.RAIN -> 2 + r.nextInt(7)
                Sky.STORM -> 1 + r.nextInt(3)
                Sky.SNOW -> 3 + r.nextInt(8)
                Sky.FOG -> 2 + r.nextInt(4)
            }
            s.weather += WeatherSpan(t, t + hours * 60L, sky)
            t += hours * 60L
            prev = sky
        }
    }

    private fun pick(t: Long, prev: Sky?, r: Random): Sky {
        val day = (t / MINUTES_PER_DAY).toInt() + 1
        val hour = ((t % MINUTES_PER_DAY) / 60).toInt()
        val m = month(day)
        // clear, cloudy, rain, storm, snow, fog
        val w = when (m) {
            11, 0, 1 -> doubleArrayOf(.33, .3, .08, 0.0, .2, .09)
            5, 6, 7 -> doubleArrayOf(.5, .22, .13, .1, 0.0, .05)
            2, 3, 4 -> doubleArrayOf(.38, .3, .22, .04, .02, .04)
            else -> doubleArrayOf(.36, .3, .22, .03, .02, .07)
        }
        if (hour in 4..9) w[5] *= 3.0 else w[5] *= .3 // fog in the morning
        if (prev != null) w[prev.ordinal] *= .35 // weather changes
        var x = r.nextDouble() * w.sum()
        var i = 0
        while (i < w.size - 1 && x >= w[i]) { x -= w[i]; i++ }
        var sky = Sky.entries[i]
        // precipitation falls as snow only when it's cold enough
        val baseTemp = MONTH_TEMP[m]
        if (sky == Sky.SNOW && baseTemp > 4.0) sky = Sky.RAIN
        if ((sky == Sky.RAIN || sky == Sky.STORM) && baseTemp < 1.5) sky = Sky.SNOW
        return sky
    }

    // ------------------------------------------------------------------ effects

    /** How many walk-in customers come compared with a mild day. */
    fun demandFactor(t: BusinessTypeDef): Double {
        val sky = sky()
        if (t.office) return if (sky == Sky.STORM) .95 else 1.0
        val temp = temperature()
        var f = when (sky) { Sky.CLEAR -> 1.0; Sky.CLOUDY -> .97; Sky.FOG -> .9; Sky.RAIN -> .8; Sky.STORM -> .6; Sky.SNOW -> .7 }
        when (t.id) {
            // a rainy day is a cinema day
            "cinema", "theater" -> if (sky.wet) f *= 1.45 else if (sky == Sky.FOG) f *= 1.1 else if (sky == Sky.CLEAR && temp > 26) f *= .9
            "coffee" -> { if (sky.wet) f *= 1.2; if (temp < 8) f *= 1.15; if (temp > 27) f *= .9 }
            "bookstore" -> if (sky.wet) f *= 1.15
            "florist", "produce" -> if (sky.wet) f *= .9
            "supermarket" -> if (sky == Sky.STORM || sky == Sky.SNOW) f *= 1.3 // stocking up
            "giftshop" -> if (sky.wet) f *= 1.1
            "gym" -> if (sky == Sky.CLEAR && temp > 26) f *= .92
            "nightclub" -> { if (temp > 22) f *= 1.1; if (sky == Sky.STORM) f *= .85 }
            "liquor" -> if (temp > 24 || sky == Sky.SNOW) f *= 1.1
        }
        return f
    }

    /** How much more (or less) likely a customer is to buy a product today. */
    fun productFactor(pid: String): Double {
        val sky = sky()
        val temp = temperature()
        return when (pid) {
            "umbrella" -> when (sky) { Sky.RAIN -> 4.0; Sky.STORM -> 5.0; Sky.SNOW -> 2.0; Sky.CLOUDY -> 1.4; Sky.FOG -> 1.2; Sky.CLEAR -> .5 }
            "ice_cream" -> when { temp > 27 -> 2.4; temp > 22 -> 1.6; temp < 10 -> .35; else -> 1.0 }
            "soda", "energy_drink", "slushie", "draft_beer", "beer", "protein_shake" -> when { temp > 27 -> 1.5; temp > 22 -> 1.25; temp < 5 -> .7; else -> 1.0 }
            "coffee", "tea" -> when { temp < 5 -> 1.5; temp < 12 -> 1.25; temp > 27 -> .7; else -> 1.0 }
            "cocktail" -> if (temp > 24) 1.3 else 1.0
            "coat_check_fee" -> when { temp < 10 || sky.wet -> 1.6; temp > 22 -> .3; else -> 1.0 }
            "clothes_cheap", "clothes_exp" -> if (sky == Sky.SNOW || temp < 5) 1.2 else 1.0
            "sneakers" -> if (sky.wet) .8 else 1.0
            "flower_cheap", "flower_exp" -> if (sky.wet) .85 else 1.0
            else -> 1.0
        }
    }

    /** Muddy shoes: more dirt per customer in wet weather. */
    fun dirtFactor(): Double = when (sky()) { Sky.RAIN -> 2.0; Sky.STORM -> 2.6; Sky.SNOW -> 2.2; Sky.FOG -> 1.1; else -> 1.0 } + e.state.snowCover * .5

    /** Share of pedestrians who stay out in this weather. */
    fun pedFactor(): Float = when (sky()) { Sky.CLEAR -> 1f; Sky.CLOUDY -> .95f; Sky.FOG -> .8f; Sky.RAIN -> .6f; Sky.STORM -> .35f; Sky.SNOW -> .55f }

    fun carSpeedFactor(): Float = when (sky()) { Sky.RAIN -> .85f; Sky.STORM -> .75f; Sky.SNOW -> .65f; Sky.FOG -> .8f; else -> 1f }

    private var lastSky: Sky? = null

    /** Snow piles up while it snows and melts above freezing; a toast when the weather turns. */
    fun hourly() {
        ensure()
        val s = e.state
        val sky = sky()
        val temp = temperature()
        s.snowCover = when {
            sky == Sky.SNOW -> s.snowCover + .18
            temp > 2 -> s.snowCover - (if (sky.wet) .25 else .08)
            else -> s.snowCover - .01
        }.coerceIn(0.0, 1.0)
        val prev = lastSky
        lastSky = sky
        if (prev != null && prev != sky) {
            when (sky) {
                Sky.RAIN -> e.toast("It's raining: fewer shoppers outside, but umbrellas sell fast", icon = "rain")
                Sky.STORM -> e.toast("Thunderstorm: streets empty out, cinemas fill up", icon = "storm")
                Sky.SNOW -> e.toast("It's snowing: hot coffee and coats are in demand", icon = "snow")
                Sky.FOG -> e.toast("Fog rolls in", icon = "fog")
                else -> {}
            }
            if (sky.wet) e.tip("weather", "Weather changes business",
                "Rain, snow and storms keep shoppers indoors, so walk-in stores get fewer customers, but cinemas, cafés and bookstores get more. Umbrellas, hot coffee, ice cream and cold drinks all sell with the weather. Wet shoes also dirty your floors faster. Tap the weather in the top bar for the forecast.")
        }
    }

    /** Text for the forecast dialog. */
    fun forecastText(): String {
        val s = e.state
        val sb = StringBuilder()
        sb.append("${dateLabel()} · ${season()}\n")
        sb.append("Now: ${sky().label}, ${temperature().toInt()}°C\n\n")
        for (d in 0..2) {
            val day = s.day + d
            val start = (day - 1L) * MINUTES_PER_DAY
            val skies = (8..20 step 3).map { sky(start + it * 60L) }
            val main = skies.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
            val hi = (8..20 step 2).maxOf { temperature(start + it * 60L) }.toInt()
            val lo = (0..23 step 3).minOf { temperature(start + it * 60L) }.toInt()
            val wetHours = (0 until 24).count { sky(start + it * 60L).wet }
            sb.append(if (d == 0) "Today" else if (d == 1) "Tomorrow" else dateLabel(day))
            sb.append(": ${main.label}, $lo° to $hi°C")
            if (wetHours > 0 && !main.wet) sb.append(", showers ($wetHours h)")
            sb.append('\n')
        }
        sb.append("\nBad weather keeps walk-in shoppers at home but fills cinemas, cafés and bookstores. Umbrellas sell in the rain, ice cream and cold drinks in the heat, coffee in the cold.")
        return sb.toString()
    }
}
