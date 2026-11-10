package com.hustlecity.game.engine

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.world.BKind

/** A milestone worth a trophy: unlocked once, the day it happened kept in the save. */
class Achievement(val id: String, val title: String, val text: String, val check: (GameEngine) -> Boolean)

object Achievements {
    val all: List<Achievement> = listOf(
        Achievement("open", "Open for Business", "Start your first business") { it.state.businesses.any { b -> !Catalog.type(b.typeId).facility } },
        Achievement("sale", "First Sale", "Take your first money over the counter") { it.state.player.totalEarned > 0 },
        Achievement("boss", "The Boss", "Have an employee on the payroll") { it.state.employees.isNotEmpty() },
        Achievement("crew", "Team of Ten", "Employ 10 people") { it.state.employees.size >= 10 },
        Achievement("chain", "Chain Reaction", "Run 3 businesses at once") { it.state.businesses.count { b -> !Catalog.type(b.typeId).facility } >= 3 },
        Achievement("empire", "Empire", "Run 10 businesses at once") { it.state.businesses.count { b -> !Catalog.type(b.typeId).facility } >= 10 },
        Achievement("variety", "Jack of All Trades", "Run 5 different kinds of business") { it.state.businesses.map { b -> b.typeId }.filter { t -> !Catalog.type(t).facility }.distinct().size >= 5 },
        Achievement("logistics", "Supply Chain", "Open a warehouse") { it.state.businesses.any { b -> b.typeId == "warehouse" } },
        Achievement("hq", "Corner Office", "Open a headquarters") { it.state.businesses.any { b -> b.typeId == "hq" } },
        Achievement("scholar", "Top of the Class", "Finish every course at the business school") { e -> Catalog.courses.all { c -> c.id in e.state.player.courses } },
        Achievement("landlord", "Landlord", "Own a building") { it.state.rentals.any { r -> r.owned } },
        Achievement("tenant", "Rent Day", "Let a building of yours to a tenant") { it.state.rentals.any { r -> r.tenant.isNotEmpty() } },
        Achievement("wheels", "New Wheels", "Own a second vehicle") { it.state.vehicles.size >= 2 },
        Achievement("fleet", "Fleet Manager", "Own 3 vans or trucks") { it.ops.vans().size >= 3 },
        Achievement("penthouse", "Moving Up", "Live in a home of the top class") { e -> e.city.building(e.state.player.home)?.let { h -> h.kind == BKind.RESIDENTIAL && e.homeTier(h) == GameEngine.HOME_TIERS.size - 1 } == true },
        Achievement("investor", "Investor", "Hold \$50,000 in shares and bonds") { it.ops.portfolioValue() + it.ops.bondsValue() >= 50_000 },
        Achievement("buyout", "Hostile Takeover", "Buy a rival's store") { it.state.rivalBusinesses.any { rb -> rb.rivalId == SUBSIDIARY } },
        Achievement("debtfree", "Debt Free", "Pay off a loan in full") { it.state.objectivesDone.contains("loan_repaid") },
        Achievement("commuter", "Commuter", "Ride the train") { it.state.objectivesDone.contains("train") },
        Achievement("survivor", "Still Standing", "Reach day 30") { it.state.day >= 30 },
        Achievement("veteran", "Old Hand", "Reach day 100") { it.state.day >= 100 },
        Achievement("nw100k", "Six Figures", "Reach a net worth of \$100,000") { it.netWorth() >= 100_000 },
        Achievement("million", "Millionaire", "Reach a net worth of \$1,000,000") { it.netWorth() >= 1_000_000 },
        Achievement("top3", "On the Podium", "Climb into the top three of the rankings") { it.state.rivals.isNotEmpty() && it.rank() <= 3 },
        Achievement("number1", "Number One", "Top the rankings") { it.state.rivals.isNotEmpty() && it.rank() == 1 },
    )

    /** Unlocks whatever has just been earned, with a toast for each. */
    fun check(e: GameEngine) {
        val got = e.state.achievements
        for (a in all) {
            if (a.id in got || !runCatching { a.check(e) }.getOrDefault(false)) continue
            got[a.id] = e.state.day
            e.toast("Achievement: ${a.title}", ToastKind.GOOD, icon = "trophy")
            com.hustlecity.game.audio.Audio.play(com.hustlecity.game.audio.Sfx.CASH)
        }
    }
}
