package com.hustlecity.game.ui.icons

/** Glyphs for business types, city places and map filters (white on colored pins). */
object PlaceIcons : IconSet {
    override val icons = listOf(
        icon("biz_coffee",
            L("M5 8h11v5.5a5.5 5.5 0 0 1-11 0z"),
            L("M16 9.5h1.2a2.6 2.6 0 0 1 0 5.2H16v-1.8h1.2a.8.8 0 0 0 0-1.6H16z"),
            L("M4 19.5h13a1 1 0 0 1 0 1.5H4a1 1 0 0 1 0-1.5z"),
            L("M8 2.5c1 1-1 2 0 3.5M11 2.5c1 1-1 2 0 3.5", stroke = 1.2f, alpha = .8f),
        ),
    )
}
