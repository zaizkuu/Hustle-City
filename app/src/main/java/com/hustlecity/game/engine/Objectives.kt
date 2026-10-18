package com.hustlecity.game.engine

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.world.BKind

class Objective(
    val id: String,
    val text: String,
    /** Place id or "biz" used to set a waypoint when the objective is tapped. */
    val target: String? = null,
    val done: (GameEngine) -> Boolean,
)

class ObjectiveGroup(val title: String, val intro: String, val items: List<Objective>)

data class Alert(val text: String, val severe: Boolean, val businessId: Int = -1)

object Objectives {
    private fun flag(id: String): (GameEngine) -> Boolean = { e -> id in e.state.objectivesDone }

    val groups = listOf(
        ObjectiveGroup(
            "Uncle Sal", "Welcome to Hustle City, kid! I lent you a little cash. Let's turn it into an empire. Start with a loan from Hudson Bank, then find a store to rent.",
            listOf(
                Objective("loan", "Take the Starter Loan at Hudson Bank", "bank", flag("loan")),
                Objective("rent", "Rent a retail building using the Realty app", "realty") { e ->
                    e.state.rentals.any { e.city.building(it.buildingId)?.kind == BKind.RETAIL }
                },
                Objective("start", "Start a business in your new building", "biz") { e -> e.state.businesses.isNotEmpty() },
                Objective("furniture", "Buy furniture at an appliance store", "furniture", flag("furniture")),
                Objective("requirements", "Place furniture to meet all requirements", "biz") { e ->
                    e.state.businesses.any { BizCalc.requirementsMet(it) }
                },
                Objective("stock", "Buy products at a wholesaler", "stock") { e ->
                    e.state.businesses.any { b ->
                        !b.type.facility && (b.stock.values.any { it > 0 } || b.type.products.firstOrNull()?.let { Catalog.product(it.first)?.service } == true)
                    }
                },
                Objective("open", "Open your store (turn off Temporarily Closed)", "biz") { e -> e.state.businesses.any { !it.tempClosed } },
                Objective("work", "Start working in your store", "biz", flag("work")),
                Objective("collect", "Collect your earnings at midnight", null, flag("collect")),
            ),
        ),
        ObjectiveGroup(
            "Uncle Sal", "Nice work! You can't run the register forever. Take the Basic HR course and hire some help.",
            listOf(
                Objective("hr", "Complete the 'Basic HR' course at Hustle Business School", "school") { e -> Catalog.COURSE_HR in e.state.player.courses },
                Objective("recruit", "Visit the recruiter to find employee candidates", "recruit", flag("recruit")),
                Objective("hire", "Once you receive your candidates, hire an employee", null) { e -> e.state.employees.isNotEmpty() },
                Objective("assign", "Assign your employee in the BizHub Scheduler", "biz") { e -> e.state.employees.any { it.businessId >= 0 } },
            ),
        ),
        ObjectiveGroup(
            "Big Ambitions", "Now we're talking. Grow the brand, beat the rivals, own this city.",
            listOf(
                Objective("marketing", "Run a marketing campaign at Buzz Marketing", "buzz", flag("marketing")),
                Objective("two", "Own 2 businesses", "realty") { e -> e.state.businesses.count { !it.type.facility } >= 2 },
                Objective("warehouse", "Open a warehouse and store stock in it", "realty") { e ->
                    e.state.businesses.any { BizCalc.isWarehouse(it) && it.stock.values.any { q -> q > 0 } }
                },
                Objective("hq", "Open a Headquarters and hire a manager", "realty") { e ->
                    e.state.businesses.any { BizCalc.isHq(it) } && e.state.employees.any { it.role.atHeadquarters && it.businessId >= 0 }
                },
                Objective("nw100k", "Reach a net worth of \$100,000", null) { e -> e.netWorth() >= 100_000 },
                Objective("five", "Own 5 businesses", "realty") { e -> e.state.businesses.count { !it.type.facility } >= 5 },
                Objective("million", "Reach a net worth of \$1,000,000", null) { e -> e.netWorth() >= 1_000_000 },
                Objective("top", "Become #1 in the Rivals ranking", null) { e -> e.rank() == 1 },
            ),
        ),
    )

    /** Once an objective has been met it stays checked, even if the condition later stops holding. */
    fun isDone(e: GameEngine, o: Objective) = "obj:${o.id}" in e.state.objectivesDone || o.done(e)

    /** Records newly met objectives so they stay done. */
    fun latch(e: GameEngine) {
        for (g in groups) for (o in g.items) if ("obj:${o.id}" !in e.state.objectivesDone && o.done(e)) e.state.objectivesDone += "obj:${o.id}"
    }

    /** Index of the first group that still has open objectives (or the last group). */
    fun currentGroup(e: GameEngine): Int {
        for ((i, g) in groups.withIndex()) if (g.items.any { !isDone(e, it) }) return i
        return groups.size - 1
    }

    fun alerts(e: GameEngine): List<Alert> {
        val out = ArrayList<Alert>()
        val s = e.state
        val p = s.player
        if (p.hunger < 20) out += Alert("You are hungry. Eat at home, a restaurant or a coffee shop.", true)
        if (p.energy < 20) out += Alert("You are exhausted. Go home and sleep.", true)
        if (p.money < 0) out += Alert("Your bank account is overdrawn!", true)
        for (b in s.businesses) {
            for ((req, ok) in BizCalc.requirements(b)) if (!ok) out += Alert("${b.name}: Requires at least one ${req.label}.", true, b.id)
            if (b.type.facility) {
                if (BizCalc.isHq(b)) {
                    val managers = s.employees.count { it.businessId == b.id }
                    if (managers > BizCalc.count(b, "desk")) out += Alert("${b.name}: ${managers - BizCalc.count(b, "desk")} manager(s) have no desk", true, b.id)
                }
                continue
            }
            if (b.tempClosed) out += Alert("${b.name}: Is temporarily closed", false, b.id)
            if (!b.tempClosed && BizCalc.requirementsMet(b)) {
                val empty = b.type.products.mapNotNull { Catalog.product(it.first) }.filter { !it.service && (b.stock[it.id] ?: 0) == 0 && BizCalc.capacityFor(b, it.id) > 0 }
                if (empty.isNotEmpty()) out += Alert("${b.name}: Out of ${empty.joinToString { it.name }}", true, b.id)
                val missingDisplay = b.type.products.mapNotNull { Catalog.product(it.first) }.filter { !it.service && BizCalc.capacityFor(b, it.id) == 0 }
                if (missingDisplay.isNotEmpty() && missingDisplay.size < b.type.products.size) {
                    out += Alert("${b.name}: No display for ${missingDisplay.joinToString { it.name }}", false, b.id)
                }
                if (BizCalc.isWithinHours(b, s.hour) && BizCalc.workers(e, b, s.hour, s.weekday).isEmpty()) {
                    out += Alert("${b.name}: Nobody is working right now", true, b.id)
                }
                if (b.cleanliness < 40) out += Alert("${b.name}: The store is dirty (${b.cleanliness.toInt()}%)", false, b.id)
            }
        }
        for (emp in s.employees) if (emp.satisfaction < .3) out += Alert("${emp.name} is unhappy at work", false)
        return out
    }
}
