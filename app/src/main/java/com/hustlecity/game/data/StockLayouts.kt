package com.hustlecity.game.data

/** The small 3D shapes products are drawn as on displays. */
object StockShape {
    const val BOX = 0
    const val CAN = 1
    const val BOTTLE = 2
    const val BAR = 3
    const val ROLLS = 4
    const val GIFT = 5
    const val UMBRELLA = 6
    const val BOOK = 7
    const val MAGAZINE = 8
    const val GARMENT = 9
    const val SHOE = 10
    const val FLOWERS = 11
    const val PRODUCE = 12
    const val TRAY = 13
    const val TUB = 14
    const val PASTRY = 15
    const val CUP = 16
    const val PATTY = 17
    const val FRIES = 18
    const val PIZZA = 19
    const val BUCKET = 20
    const val PHONE = 21
    const val LAPTOP = 22
    const val HEADPHONES = 23
    const val JEWEL = 24
    const val GLASS = 25
    const val CARTON = 26
}

/**
 * A level of product slots on a display, in the item's own tile units before rotation: [cols] across x from [x0] to
 * [x1], [rows] deep along y from [y0] to [y1], standing on height [z] with [h] meters of room above. Hanging rows
 * ([hang]) have the goods hanging down from [z] instead (clothes on a rail).
 */
class StockRow(
    val x0: Float, val x1: Float, val y0: Float, val y1: Float, val z: Float, val cols: Int, val rows: Int = 1,
    val h: Float = .3f, val hang: Boolean = false,
)

/**
 * How a display shows what it holds. [rows] are its levels of product slots (listed top first; the front row of each
 * level fills first). [hide] lists the model's own placeholder goods (part indices) that real stock replaces, and its
 * solid parts that [shell] rebuilds hollow so you can see inside. [glass] is drawn see-through over the goods.
 */
class StockLayout(val rows: List<StockRow>, val hide: Set<Int> = emptySet(), val shell: List<Part> = emptyList(), val glass: List<Part> = emptyList()) {
    /** Visible product slots (at most [MAX_SLOTS]). */
    val slots: Int = rows.sumOf { it.cols * it.rows }.coerceAtMost(MAX_SLOTS)
    /** Bit mask of [hide], for the part sorter. */
    val hideMask: Long = hide.fold(0L) { m, i -> m or (1L shl i) }

    private val rowOf = IntArray(slots)
    private val cellOf = IntArray(slots)

    init {
        var s = 0
        loop@ for ((r, row) in rows.withIndex()) {
            // front row first: it's what you see, and it should be the last to go
            for (dr in row.rows - 1 downTo 0) for (c in 0 until row.cols) {
                if (s >= slots) break@loop
                rowOf[s] = r; cellOf[s] = dr * row.cols + c; s++
            }
        }
    }

    fun row(slot: Int): StockRow = rows[rowOf[slot]]

    /** Slot center on its level, in the item's own units: out = (x, y, z of the surface it stands on or hangs from). */
    fun slotCenter(slot: Int, out: FloatArray) {
        val row = rows[rowOf[slot]]
        val c = cellOf[slot] % row.cols; val dr = cellOf[slot] / row.cols
        out[0] = row.x0 + (c + .5f) * (row.x1 - row.x0) / row.cols
        out[1] = row.y0 + (dr + .5f) * (row.y1 - row.y0) / row.rows
        out[2] = row.z
    }

    /** Width (x) and depth (y) of a slot's cell. */
    fun cellW(slot: Int) = row(slot).let { (it.x1 - it.x0) / it.cols }
    fun cellD(slot: Int) = row(slot).let { (it.y1 - it.y0) / it.rows }

    companion object {
        const val MAX_SLOTS = 24
    }
}

/** Stock layouts for every display, and the shape each product is drawn as. */
object StockLayouts {
    private fun p(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, color: Int) = Part(x0, y0, z0, x1, y1, z1, color)

    private val WOOD = c(0xFF8B5A3C)
    private val WOOD_L = c(0xFFB07A52)
    private val WHITE = c(0xFFE9EDF2)
    private val STEEL_D = c(0xFF5B6770)
    private val WIRE = c(0xFFC9D1D9)
    /** See-through glass (drawn translucent). */
    val GLASS = c(0xFFBFE3F5)

    /** A chest freezer opened up: base, four walls and a see-through lid. */
    private fun chest(body: Int) = listOf(
        p(0f, .1f, 0f, 2f, .95f, .42f, body),
        p(0f, .1f, .42f, 2f, .16f, .85f, body), p(0f, .89f, .42f, 2f, .95f, .85f, body),
        p(0f, .16f, .42f, .06f, .89f, .85f, body), p(1.94f, .16f, .42f, 2f, .89f, .85f, body),
    )

    private val layouts: Map<String, StockLayout> = mapOf(
        "shelf" to StockLayout(
            rows = listOf(
                StockRow(.1f, 1.9f, .28f, .86f, 1.1f, 8, h = .45f),
                StockRow(.1f, 1.9f, .28f, .86f, .5f, 8, h = .5f),
                StockRow(.1f, 1.9f, .28f, .86f, .08f, 8, h = .34f),
            ),
            hide = setOf(5, 6, 7, 8),
            shell = listOf(p(.08f, .2f, 0f, 1.92f, .9f, .08f, c(0xFFB07A52)), p(.08f, .86f, .08f, 1.92f, .9f, .16f, c(0xFF9C6B43))),
        ),
        "drinks_fridge" to StockLayout(
            rows = listOf(
                StockRow(.12f, .88f, .4f, .84f, 1.47f, 5, h = .32f),
                StockRow(.12f, .88f, .4f, .84f, 1.02f, 5, h = .36f),
                StockRow(.12f, .88f, .4f, .84f, .57f, 5, h = .36f),
                StockRow(.12f, .88f, .4f, .84f, .15f, 5, h = .36f),
            ),
            hide = setOf(0, 1, 2, 3),
            shell = listOf(
                p(.05f, .05f, 0f, .95f, .12f, 2f, WHITE), p(.05f, .12f, 0f, .1f, .88f, 2f, WHITE), p(.9f, .12f, 0f, .95f, .88f, 2f, WHITE),
                p(.1f, .12f, 0f, .9f, .88f, .15f, WHITE), p(.1f, .12f, 1.86f, .9f, .88f, 2f, WHITE),
                p(.1f, .12f, .55f, .9f, .86f, .57f, WIRE), p(.1f, .12f, 1.0f, .9f, .86f, 1.02f, WIRE), p(.1f, .12f, 1.45f, .9f, .86f, 1.47f, WIRE),
                p(.05f, .88f, 0f, .12f, .95f, 2f, c(0xFFD5DBE2)), p(.88f, .88f, 0f, .95f, .95f, 2f, c(0xFFD5DBE2)),
                p(.12f, .88f, 0f, .88f, .95f, .15f, c(0xFFD5DBE2)), p(.12f, .88f, 1.85f, .88f, .95f, 2f, c(0xFF2F80ED)),
            ),
            glass = listOf(p(.12f, .9f, .15f, .88f, .93f, 1.85f, GLASS)),
        ),
        "fresh_fridge" to StockLayout(
            rows = listOf(
                StockRow(.1f, 1.9f, .26f, .44f, 1.33f, 6, h = .2f),
                StockRow(.1f, 1.9f, .3f, .9f, .9f, 6, rows = 2, h = .25f),
            ),
            hide = setOf(2),
            shell = listOf(p(0f, .25f, 1.3f, 2f, .46f, 1.33f, WIRE), p(.05f, .9f, .9f, 1.95f, .95f, 1.0f, c(0xFFD5DBE2))),
        ),
        "freezer" to StockLayout(
            rows = listOf(StockRow(.12f, 1.88f, .2f, .86f, .42f, 6, rows = 3, h = .36f)),
            hide = setOf(0, 1),
            shell = chest(WHITE),
            glass = listOf(p(.06f, .16f, .83f, 1.94f, .89f, .86f, GLASS)),
        ),
        "ice_cream_freezer" to StockLayout(
            rows = listOf(StockRow(.12f, 1.88f, .2f, .86f, .42f, 6, rows = 2, h = .36f)),
            hide = setOf(0, 1, 2, 3, 4),
            shell = chest(c(0xFFE3F2FD)),
            glass = listOf(p(.06f, .16f, .83f, 1.94f, .89f, .86f, GLASS)),
        ),
        "flower_stand" to StockLayout(
            rows = listOf(
                StockRow(.12f, .88f, .14f, .44f, .95f, 3, h = .5f),
                StockRow(.12f, .88f, .52f, .88f, .7f, 3, h = .5f),
            ),
            hide = setOf(1, 2, 3, 4),
            shell = listOf(p(.1f, .1f, .7f, .9f, .48f, .95f, c(0xFF9C6B43))),
        ),
        "produce" to StockLayout(
            rows = listOf(StockRow(.08f, 1.92f, .18f, .88f, .78f, 9, rows = 2, h = .2f)),
            hide = setOf(1, 2, 3),
            shell = listOf(
                p(.05f, .15f, .75f, .65f, .9f, .78f, c(0xFF8B6A4E)), p(.7f, .15f, .75f, 1.3f, .9f, .78f, c(0xFF8B6A4E)), p(1.35f, .15f, .75f, 1.95f, .9f, .78f, c(0xFF8B6A4E)),
                p(.05f, .86f, .75f, 1.95f, .9f, .86f, c(0xFF8B6A4E)),
            ),
        ),
        "coffee_machine" to StockLayout(
            rows = listOf(
                StockRow(.4f, 1.3f, .32f, .74f, 1.55f, 5, rows = 2, h = .14f),
                StockRow(.15f, 1.85f, .82f, .98f, 1.05f, 7, h = .14f),
            ),
        ),
        "pastry_case" to StockLayout(
            rows = listOf(
                StockRow(.14f, .86f, .2f, .84f, 1.13f, 3, rows = 2, h = .18f),
                StockRow(.14f, .86f, .2f, .84f, .9f, 3, rows = 2, h = .2f),
            ),
            hide = setOf(1, 2),
            shell = listOf(p(.1f, .17f, 1.11f, .9f, .88f, 1.13f, WIRE)),
            glass = listOf(p(.08f, .15f, .9f, .92f, .9f, 1.35f, GLASS)),
        ),
        "grill" to StockLayout(rows = listOf(StockRow(.18f, 1.82f, .24f, .82f, 1.0f, 6, rows = 3, h = .1f))),
        "fryer" to StockLayout(rows = listOf(StockRow(.18f, .82f, .22f, .84f, 1.0f, 3, rows = 3, h = .12f))),
        "pizza_oven" to StockLayout(rows = listOf(StockRow(.15f, 1.85f, .2f, .86f, 1.6f, 4, rows = 2, h = .2f))),
        "clothing_rack" to StockLayout(
            rows = listOf(StockRow(.14f, 1.86f, .3f, .7f, 1.55f, 12, h = .7f, hang = true)),
            hide = setOf(3, 4, 5),
        ),
        "round_rack" to StockLayout(
            rows = listOf(
                StockRow(.25f, 1.75f, 1.55f, 1.8f, 1.4f, 6, h = .7f, hang = true),
                StockRow(.25f, 1.75f, .2f, .45f, 1.4f, 6, h = .7f, hang = true),
            ),
            hide = setOf(2, 3, 4, 5),
        ),
        "shoe_rack" to StockLayout(
            rows = listOf(
                StockRow(.1f, 1.9f, .7f, .9f, 1.1f, 5, h = .12f),
                StockRow(.1f, 1.9f, .5f, .7f, .7f, 5, h = .12f),
                StockRow(.1f, 1.9f, .3f, .5f, .3f, 5, h = .12f),
            ),
            hide = setOf(3, 4, 5, 6),
        ),
        "electronics_table" to StockLayout(
            rows = listOf(StockRow(.12f, 1.88f, .2f, .84f, .9f, 6, rows = 2, h = .25f)),
            hide = setOf(1, 2, 3),
        ),
        "jewelry_case" to StockLayout(
            rows = listOf(StockRow(.16f, .84f, .16f, .84f, .92f, 4, rows = 3, h = .1f)),
            hide = setOf(1, 2),
            shell = listOf(p(.1f, .1f, .9f, .9f, .9f, .92f, c(0xFF5C1F2E))),
            glass = listOf(p(.08f, .08f, .92f, .92f, .92f, 1.25f, GLASS)),
        ),
        "bookshelf" to StockLayout(
            rows = listOf(
                StockRow(.08f, 1.92f, .1f, .46f, 1.5f, 6, h = .4f),
                StockRow(.08f, 1.92f, .1f, .46f, 1.02f, 6, h = .4f),
                StockRow(.08f, 1.92f, .1f, .46f, .54f, 6, h = .4f),
                StockRow(.08f, 1.92f, .1f, .46f, .08f, 6, h = .4f),
            ),
            hide = setOf(0, 1, 2, 3),
            shell = listOf(
                p(0f, 0f, 0f, 2f, .06f, 2f, WOOD), p(0f, .06f, 0f, .06f, .5f, 2f, WOOD), p(1.94f, .06f, 0f, 2f, .5f, 2f, WOOD),
                p(.06f, .06f, 0f, 1.94f, .5f, .08f, WOOD_L), p(.06f, .06f, .5f, 1.94f, .5f, .54f, WOOD_L),
                p(.06f, .06f, .98f, 1.94f, .5f, 1.02f, WOOD_L), p(.06f, .06f, 1.46f, 1.94f, .5f, 1.5f, WOOD_L), p(.06f, .06f, 1.94f, 1.94f, .5f, 2f, WOOD),
            ),
        ),
        "magazine_rack" to StockLayout(
            rows = listOf(
                StockRow(.24f, .76f, .8f, .84f, 1.2f, 2, h = .26f),
                StockRow(.24f, .76f, .8f, .84f, .88f, 2, h = .26f),
                StockRow(.24f, .76f, .8f, .84f, .56f, 2, h = .26f),
                StockRow(.24f, .76f, .16f, .2f, 1.2f, 2, h = .26f),
                StockRow(.24f, .76f, .16f, .2f, .88f, 2, h = .26f),
                StockRow(.24f, .76f, .16f, .2f, .56f, 2, h = .26f),
            ),
            hide = setOf(2, 3, 4),
        ),
        "cocktail_bar" to StockLayout(
            rows = listOf(StockRow(.85f, 2.95f, .14f, .5f, 1.12f, 8, rows = 2, h = .32f)),
            hide = setOf(4, 5, 6, 7),
        ),
        "concession_stand" to StockLayout(
            rows = listOf(
                StockRow(.2f, .8f, .36f, .76f, 1.0f, 3, rows = 2, h = .22f),
                StockRow(.9f, 1.95f, .8f, .95f, 1.0f, 6, h = .16f),
            ),
            hide = setOf(1, 2),
            shell = listOf(p(.15f, .3f, 1.58f, .85f, .8f, 1.62f, c(0xFFC0392B))),
            glass = listOf(p(.15f, .3f, 1.0f, .85f, .8f, 1.58f, GLASS)),
        ),
        "pallet_rack" to StockLayout(
            rows = listOf(
                StockRow(.12f, 1.88f, .14f, .86f, 1.88f, 4, h = .62f),
                StockRow(.12f, 1.88f, .14f, .86f, .98f, 4, h = .7f),
                StockRow(.12f, 1.88f, .14f, .86f, .1f, 4, h = .7f),
            ),
            hide = setOf(4, 5, 6, 7),
        ),
        "storage_shelf" to StockLayout(
            rows = listOf(
                StockRow(.1f, 1.9f, .16f, .84f, 1.48f, 4, h = .34f),
                StockRow(.1f, 1.9f, .16f, .84f, 1.02f, 4, h = .34f),
                StockRow(.1f, 1.9f, .16f, .84f, .56f, 4, h = .34f),
                StockRow(.1f, 1.9f, .16f, .84f, .1f, 4, h = .34f),
            ),
            hide = setOf(6, 7, 8, 9),
        ),
    )

    fun of(furnitureId: String): StockLayout? = layouts[furnitureId]

    /** The shape a product is drawn as on a display. */
    fun shapeOf(productId: String): Int = when (productId) {
        "soda", "energy_drink", "beer" -> StockShape.CAN
        "wine", "whiskey", "protein_shake", "hair_products" -> StockShape.BOTTLE
        "snacks" -> StockShape.BAR
        "toilet_paper" -> StockShape.ROLLS
        "gift_cheap", "gift_exp" -> StockShape.GIFT
        "umbrella" -> StockShape.UMBRELLA
        "book_paper", "book_hard", "comic_book" -> StockShape.BOOK
        "magazine" -> StockShape.MAGAZINE
        "clothes_cheap", "clothes_exp" -> StockShape.GARMENT
        "sneakers" -> StockShape.SHOE
        "flower_cheap", "flower_exp" -> StockShape.FLOWERS
        "apples", "bananas", "carrots", "lettuce" -> StockShape.PRODUCE
        "fresh_food", "nachos" -> StockShape.TRAY
        "frozen_food" -> StockShape.BOX
        "ice_cream" -> StockShape.TUB
        "croissant", "cupcake" -> StockShape.PASTRY
        "coffee", "tea", "slushie" -> StockShape.CUP
        "burger", "hotdog" -> StockShape.PATTY
        "fries" -> StockShape.FRIES
        "pizza" -> StockShape.PIZZA
        "popcorn", "cotton_candy" -> StockShape.BUCKET
        "smartphone", "smartwatch", "earbuds" -> StockShape.PHONE
        "laptop" -> StockShape.LAPTOP
        "headphones" -> StockShape.HEADPHONES
        "ring", "necklace", "watch" -> StockShape.JEWEL
        "draft_beer", "cocktail" -> StockShape.GLASS
        else -> StockShape.BOX
    }
}
