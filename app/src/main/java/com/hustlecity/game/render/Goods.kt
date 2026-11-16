package com.hustlecity.game.render

/**
 * Which model from the Blender library (assets/models.hcm, "item_" models) shows a product: on a display, in a
 * shopper's hand, on a diner's plate. Products without one keep the small painted shape StockPainter draws.
 */
object Goods {
    /** The model name for product [pid]; [slot] varies the look along a shelf where a product has more than one. */
    fun modelFor(pid: String, slot: Int = 0): String? = when (pid) {
        "soda", "energy_drink" -> "item_soda_can"
        "beer", "draft_beer" -> "item_beer_bottle"
        "wine", "whiskey" -> "item_wine_bottle"
        "protein_shake", "hair_products" -> "item_water_bottle"
        "gift_cheap" -> "item_gift_box"
        "gift_exp" -> if (slot % 2 == 0) "item_teddy_bear" else "item_gift_box"
        "umbrella" -> "item_umbrella"
        "book_paper", "book_hard", "comic_book" -> "item_book_stack"
        "magazine" -> "item_magazine"
        "clothes_cheap", "clothes_exp" -> "item_tshirt"
        "sneakers" -> "item_sneakers"
        "flower_cheap", "flower_exp" -> "item_bouquet"
        "apples" -> "item_apple"
        "bananas" -> "item_bananas"
        "fresh_food" -> "item_bread"
        "ice_cream" -> "item_ice_cream"
        "croissant" -> "item_croissant"
        "cupcake" -> "item_cupcake"
        "coffee", "tea" -> "item_coffee_cup"
        "slushie" -> "item_soda_cup"
        "burger" -> "item_burger"
        "hotdog" -> "item_hotdog"
        "fries" -> "item_fries"
        "pizza" -> "item_pizza_box"
        "popcorn" -> "item_popcorn"
        "smartphone" -> "item_smartphone"
        "smartwatch", "watch" -> "item_watch"
        "laptop" -> "item_laptop"
        "headphones", "earbuds" -> "item_headphones"
        "ring" -> if (slot % 3 == 2) "item_diamond" else "item_ring_box"
        "necklace" -> "item_necklace"
        "cocktail" -> "item_cocktail"
        else -> null
    }

    /**
     * What someone eats or drinks sitting down at a business of type [typeId] (by their own [seed], so it doesn't
     * change while they sit): coffee and something sweet at a café, a burger, a hot dog or fries at the fast-food
     * counter, popcorn at the cinema, a drink at the club. Null where people don't eat.
     */
    fun mealFor(typeId: String?, seed: Int): String? {
        val k = (seed ushr 3) and 0xFF
        return when (typeId) {
            "coffee" -> arrayOf("item_coffee_cup", "item_donut", "item_croissant", "item_coffee_cup", "item_cupcake")[k % 5]
            "fastfood" -> arrayOf("item_burger", "item_hotdog", "item_fries", "item_soda_cup", "item_burger")[k % 5]
            "cinema", "theater" -> if (k % 3 == 2) "item_soda_cup" else "item_popcorn"
            "nightclub" -> if (k % 2 == 0) "item_cocktail" else "item_beer_bottle"
            "liquor" -> "item_wine_bottle"
            else -> null
        }
    }
}
