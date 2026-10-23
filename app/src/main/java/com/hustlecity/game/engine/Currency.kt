package com.hustlecity.game.engine

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * How a city shows money. The simulation keeps every amount in dollars; a city that pays in pesos, yen or won shows
 * them at [rate] local units to the dollar, with [decimals] places (yen and won have no cents).
 */
data class Currency(val code: String, val symbol: String, val rate: Double, val decimals: Int) {
    /** A dollar amount in this currency: "-₱1,234.50", "¥12,800". */
    fun format(dollars: Double): String {
        val local = dollars * rate
        val sign = if (local < 0 && abs(local) >= .5 * 10.0.pow(-decimals)) "-" else ""
        val digits = if (decimals == 0) "%,.0f".format(Locale.US, abs(local)) else "%,.${decimals}f".format(Locale.US, abs(local))
        return sign + symbol + digits
    }

    /** A dollar amount in a few characters for tight spots: "₩6.75B", "¥1.2K", "$950". */
    fun short(dollars: Double): String {
        val a = abs(dollars * rate)
        val sign = if (dollars < 0 && a >= .5) "-" else ""
        return sign + symbol + when {
            a >= 1e9 -> "%.2fB".format(Locale.US, a / 1e9)
            a >= 1e6 -> "%.2fM".format(Locale.US, a / 1e6)
            a >= 1e4 -> "%.1fK".format(Locale.US, a / 1e3)
            else -> "%,.0f".format(Locale.US, a)
        }
    }

    /** A dollar amount without cents, for buttons and round prices: "$150", "₩202,500". */
    fun whole(dollars: Double): String {
        val local = dollars * rate
        return (if (local <= -.5) "-" else "") + symbol + "%,.0f".format(Locale.US, abs(local))
    }

    /**
     * Rewrites the dollar amounts written into a fixed text ("below -$5,000", "costs $250") in this currency, so help
     * pages and objectives read in the city's money.
     */
    fun localize(text: String): String {
        if (this == USD || '$' !in text) return text
        return AMOUNT.replace(text) { m ->
            val v = m.groupValues[2].replace(",", "").toDoubleOrNull() ?: return@replace m.value
            val neg = m.groupValues[1] == "-"
            if (m.groupValues[3].isNotEmpty()) format(if (neg) -v else v) else whole(if (neg) -v else v)
        }
    }

    /** Local amount as a plain number (what a player reads off a price tag). */
    fun local(dollars: Double) = dollars * rate

    /** Dollars for a local amount. */
    fun dollars(local: Double) = local / rate

    /** A price rounded to what a shop in this city would ask: whole yen or won, cents elsewhere. */
    fun roundPrice(dollars: Double): Double {
        val unit = when (code) { "JPY" -> 10.0; "KRW" -> 100.0; "PHP" -> 1.0; else -> .01 }
        val local = (dollars * rate / unit).roundToLong() * unit
        return local / rate
    }

    /**
     * The step a price stepper moves by, in dollars: a round amount of local money that suits the price
     * (10 cents on a coffee, ten dollars on a laptop; likewise ¥10 or ¥1,000).
     */
    fun priceStep(dollars: Double): Double {
        val steps = when (code) {
            "JPY" -> doubleArrayOf(10.0, 50.0, 100.0, 1000.0)
            "KRW" -> doubleArrayOf(100.0, 500.0, 1000.0, 10000.0)
            "PHP" -> doubleArrayOf(1.0, 5.0, 10.0, 100.0)
            else -> doubleArrayOf(.1, .5, 1.0, 10.0)
        }
        val i = when { dollars < 5 -> 0; dollars < 20 -> 1; dollars < 100 -> 2; else -> 3 }
        return steps[i] / rate
    }

    companion object {
        private val AMOUNT = Regex("""(-?)\$([0-9][0-9,]*(\.([0-9]+))?)""")
        val USD = Currency("USD", "$", 1.0, 2)
        val PHP = Currency("PHP", "₱", 56.0, 2)
        val JPY = Currency("JPY", "¥", 150.0, 0)
        val KRW = Currency("KRW", "₩", 1350.0, 0)

        /** The currency of the city a map was drawn from. */
        fun forMap(mapId: String): Currency = when (mapId) {
            "manila" -> PHP
            "tokyo" -> JPY
            "seoul" -> KRW
            else -> USD
        }
    }
}
