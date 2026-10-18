package com.hustlecity.game.model

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.StaffRole
import kotlinx.serialization.Serializable

const val MINUTES_PER_DAY = 1440
val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

@Serializable
data class CargoItem(
    /** Furniture id, or product id when [isProduct]. */
    val id: String,
    val isProduct: Boolean,
    /** Units contained (products) or 1 (furniture). */
    var qty: Int = 1,
) {
    val label: String
        get() = if (isProduct) "${Catalog.product(id)?.name ?: id} ×$qty" else Catalog.item(id)?.name ?: id
}

@Serializable
data class PlayerState(
    var name: String = "Alex",
    var skin: Int = 0,
    var shirt: Int = 0,
    var money: Double = 5000.0,
    var energy: Double = 90.0,
    var hunger: Double = 80.0,
    var happiness: Double = 70.0,
    var x: Float = 0f,
    var y: Float = 0f,
    var heading: Float = 0f,
    var inBuilding: Int = -1,
    var driving: Int = -1,
    var home: Int = -1,
    var fridgeMeals: Int = 4,
    var courses: MutableSet<String> = mutableSetOf(),
    var hand: MutableList<CargoItem> = mutableListOf(),
    var waypoint: Int = -1,
    /** Business id the player is currently working at, -1 if none. */
    var workingAt: Int = -1,
    var totalEarned: Double = 0.0,
    var difficulty: Int = 1,
)

@Serializable
data class PlacedItem(var itemId: String, var x: Int, var y: Int, var rot: Int)

@Serializable
data class Campaign(val campaignId: String, var daysLeft: Int)

@Serializable
data class DeliveryContract(
    var enabled: Boolean = true,
    var hour: Int = 7,
    val targets: MutableMap<String, Int> = mutableMapOf(),
)

@Serializable
data class BizDay(
    val day: Int,
    val revenue: Double,
    val cogs: Double,
    val wages: Double,
    val rent: Double,
    val marketing: Double,
    val customers: Int,
    val satisfaction: Double,
)

@Serializable
data class Business(
    val id: Int,
    var name: String,
    val typeId: String,
    val buildingId: Int,
    val openedDay: Int,
    val furniture: MutableList<PlacedItem> = mutableListOf(),
    val storage: MutableList<String> = mutableListOf(),
    val stock: MutableMap<String, Int> = mutableMapOf(),
    val prices: MutableMap<String, Double> = mutableMapOf(),
    /** Weighted average purchase cost per unit for each product. */
    val unitCost: MutableMap<String, Double> = mutableMapOf(),
    var openHour: Int = 8,
    var closeHour: Int = 20,
    var tempClosed: Boolean = true,
    var marketing: Double = 0.0,
    val campaigns: MutableList<Campaign> = mutableListOf(),
    var reputation: Double = 0.5,
    var cleanliness: Double = 100.0,
    var floorColor: Int = 0,
    var wallColor: Int = 0,
    var delivery: DeliveryContract? = null,
    // live counters for today
    var revenueToday: Double = 0.0,
    var cogsToday: Double = 0.0,
    var customersToday: Int = 0,
    var lostToday: Int = 0,
    var custAcc: Double = 0.0,
    var lostAcc: Double = 0.0,
    val hourlyToday: MutableList<Int> = MutableList(24) { 0 },
    val hourlyYesterday: MutableList<Int> = MutableList(24) { 0 },
    val soldToday: MutableMap<String, Int> = mutableMapOf(),
    // satisfaction accumulators (sum over customers)
    var satPrice: Double = 0.0,
    var satAvail: Double = 0.0,
    var satWait: Double = 0.0,
    var satClean: Double = 0.0,
    var satInterior: Double = 0.0,
    var satStaff: Double = 0.0,
    var satSamples: Int = 0,
    /** Last finished day's factor breakdown (price, availability, wait, cleanliness, interior, staff). */
    var lastFactors: MutableList<Double> = mutableListOf(),
    var lastSatisfaction: Double = -1.0,
    val history: MutableList<BizDay> = mutableListOf(),
    var pendingDelivery: MutableList<CargoItem> = mutableListOf(),
    var pendingDeliveryAt: Long = -1,
) {
    val type get() = Catalog.type(typeId)
}

@Serializable
data class Employee(
    val id: Int,
    val name: String,
    val role: StaffRole,
    var skill: Double,
    var wage: Double,
    val demand: Double,
    val age: Int,
    var satisfaction: Double = 0.75,
    var businessId: Int = -1,
    var shiftStart: Int = 8,
    var shiftEnd: Int = 16,
    /** Bitmask of work days, bit 0 = Monday. */
    var days: Int = 0b0011111,
    var hoursToday: Int = 0,
    var unhappyDays: Int = 0,
    var hiredDay: Int = 0,
    var expiresDay: Int = 0,
) {
    fun worksAt(hour: Int, weekday: Int): Boolean {
        if (shiftStart <= shiftEnd) return (days shr weekday) and 1 == 1 && hour in shiftStart until shiftEnd
        // overnight shift: the hours after midnight belong to the shift that started the day before
        return if (hour >= shiftStart) (days shr weekday) and 1 == 1
        else hour < shiftEnd && (days shr ((weekday + 6) % 7)) and 1 == 1
    }

    val weeklyHours: Int
        get() {
            val perDay = if (shiftStart <= shiftEnd) shiftEnd - shiftStart else 24 - shiftStart + shiftEnd
            return perDay * Integer.bitCount(days)
        }
}

@Serializable
data class Rental(val buildingId: Int, var rentPerMonth: Double, val startDay: Int, val owned: Boolean = false)

@Serializable
data class OwnedVehicle(
    val id: Int,
    val defId: String,
    var x: Float,
    var y: Float,
    var heading: Float,
    var fuel: Double = 1.0,
    var condition: Double = 1.0,
    val cargo: MutableList<CargoItem> = mutableListOf(),
    var color: Int = 0,
) {
    val def get() = Catalog.vehicle(defId)
}

@Serializable
data class Loan(val id: Int, val name: String, var remaining: Double, val dailyPrincipal: Double, val dailyRate: Double, var daysLeft: Int)

@Serializable
data class Rival(val id: Int, val name: String, val color: Int, var cash: Double, var netWorth: Double, var trend: Double = 0.0)

@Serializable
data class RivalBusiness(val id: Int, val rivalId: Int, val buildingId: Int, val typeId: String, val name: String)

@Serializable
data class DayRecord(
    val day: Int,
    val revenue: Double,
    val expenses: Map<String, Double>,
    val profit: Double,
    val cash: Double,
    val netWorth: Double,
    val businesses: Int,
)

@Serializable
data class Message(val day: Int, val minute: Int, val from: String, val text: String, var read: Boolean = false)

@Serializable
data class PendingRecruit(val role: StaffRole, val count: Int, val readyAt: Long)

@Serializable
enum class SkipKind { SLEEP, COURSE, WORK, SIDE_JOB, TAXI, WAIT, HOSPITAL, CLEAN, REST, WORKOUT }

/** A fast-forward in progress. Saved with the game so a course or taxi ride survives the app being closed. */
@Serializable
data class SkipJob(
    val kind: SkipKind,
    val start: Long,
    val end: Long,
    val title: String,
    val cancellable: Boolean,
    /** Building or business id the skip is about (taxi destination, store being worked/cleaned). */
    val target: Int = -1,
    /** Extra data, e.g. the course id. */
    val payload: String = "",
    /** Hourly wage for side jobs. */
    val amount: Double = 0.0,
)

@Serializable
data class GameState(
    val seed: Long,
    var minute: Long,
    var player: PlayerState = PlayerState(),
    val businesses: MutableList<Business> = mutableListOf(),
    val employees: MutableList<Employee> = mutableListOf(),
    val candidates: MutableList<Employee> = mutableListOf(),
    val rentals: MutableList<Rental> = mutableListOf(),
    val vehicles: MutableList<OwnedVehicle> = mutableListOf(),
    val loans: MutableList<Loan> = mutableListOf(),
    val rivals: MutableList<Rival> = mutableListOf(),
    val rivalBusinesses: MutableList<RivalBusiness> = mutableListOf(),
    /** Items the player owns inside their home (placed) besides the defaults. */
    val homeFurniture: MutableList<PlacedItem> = mutableListOf(),
    val homeStorage: MutableList<String> = mutableListOf(),
    val ledgerToday: MutableMap<String, Double> = mutableMapOf(),
    var revenueToday: Double = 0.0,
    val history: MutableList<DayRecord> = mutableListOf(),
    val messages: MutableList<Message> = mutableListOf(),
    val objectivesDone: MutableSet<String> = mutableSetOf(),
    val recruits: MutableList<PendingRecruit> = mutableListOf(),
    var nextId: Int = 1,
    var radioIndex: Int = 0,
    var skip: SkipJob? = null,
) {
    val day: Int get() = (minute / MINUTES_PER_DAY).toInt() + 1
    val hour: Int get() = ((minute % MINUTES_PER_DAY) / 60).toInt()
    val minuteOfHour: Int get() = (minute % 60).toInt()
    val weekday: Int get() = ((day - 1) % 7)
    val dayName: String get() = DAY_NAMES[weekday]
    val clock: String get() = "%02d:%02d".format(hour, minuteOfHour)

    fun newId(): Int = nextId++

    fun rentalFor(buildingId: Int) = rentals.firstOrNull { it.buildingId == buildingId }
    fun businessAt(buildingId: Int) = businesses.firstOrNull { it.buildingId == buildingId }
    fun business(id: Int) = businesses.firstOrNull { it.id == id }
    fun rivalBusinessAt(buildingId: Int) = rivalBusinesses.firstOrNull { it.buildingId == buildingId }
    fun vehicle(id: Int) = vehicles.firstOrNull { it.id == id }
}
