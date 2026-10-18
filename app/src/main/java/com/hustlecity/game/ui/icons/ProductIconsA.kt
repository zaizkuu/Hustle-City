package com.hustlecity.game.ui.icons

/** Full-color product illustrations, part A (wholesale lists, inventory, cargo). */
object ProductIconsA : IconSet {
    override val icons = listOf(
        icon("product_soda",
            L("M7 5.5h10v14a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2z", 0xFFE5484D),
            L("M7 9h10v6H7z", 0xFFFFFFFF, alpha = .9f),
            L("M8 3h8l1 2.5H7z", 0xFFB8C0CA),
            L("M14.5 5.5h2.5v14a2 2 0 0 1-2 2h-.5z", 0xFF000000, alpha = .15f),
        ),
    )
}
