package com.hustlecity.game.engine

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The money a city counts in. The game keeps its books in dollars everywhere; a city with its own currency only shows
 * them converted at a round rate, in whole units (a 3.50 coffee is 175 pesos, 525 yen or 4,550 won).
 */
class Currency(val symbol: String, val rate: Double) {
    private val dollars = NumberFormat.getCurrencyInstance(Locale.US)

    fun format(v: Double): String {
        if (rate == 1.0) return dollars.format(v)
        val n = (v * rate).roundToLong()
        return (if (n < 0) "-" else "") + symbol + "%,d".format(abs(n))
    }

    /** Whole units with no cents, for round amounts. */
    fun whole(v: Double): String = (if (v < 0) "-" else "") + symbol + "%,d".format(abs((v * rate).roundToLong()))

    fun short(v: Double): String {
        val a = abs(v) * rate
        val sign = if (v < 0) "-" else ""
        return when {
            a >= 1_000_000_000 -> "$sign$symbol%.2fB".format(a / 1_000_000_000)
            a >= 1_000_000 -> "$sign$symbol%.2fM".format(a / 1_000_000)
            a >= 10_000 -> "$sign$symbol%.1fK".format(a / 1000)
            else -> "$sign$symbol" + "%,.0f".format(a)
        }
    }

    companion object {
        val DOLLAR = Currency("$", 1.0)
        private val LOCAL = mapOf("manila" to Currency("\u20B1", 50.0), "tokyo" to Currency("\u00A5", 150.0), "seoul" to Currency("\u20A9", 1300.0))
        /** The currency shown in a city: its own when [local] is on and it has one, dollars otherwise. */
        fun of(mapId: String, local: Boolean): Currency = if (local) LOCAL[mapId] ?: DOLLAR else DOLLAR
    }
}
