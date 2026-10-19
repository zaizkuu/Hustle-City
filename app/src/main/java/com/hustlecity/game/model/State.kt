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
    /** Picked up in a store and not paid for yet; it can't leave the store until it is. */
    var unpaid: Boolean = false,
    /** Shelf price of an unpaid item. */
    val price: Double = 0.0,
) {
    val label: String
        get() = if (isProduct) "${Catalog.product(id)?.name ?: id} ×$qty" else Catalog.item(id)?.name ?: id
    val name: String
        get() = if (isProduct) Catalog.product(id)?.name ?: id else Catalog.item(id)?.name ?: id
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
    /** Boxes you're carrying: in your hands, on your hand truck or on a store cart (see [carrier]). */
    var hand: MutableList<CargoItem> = mutableListOf(),
    /** What you carry boxes with: 0 bare hands (1 box), 1 hand truck (4), 2 a store's flatbed cart (8). */
    var carrier: Int = 0,
    /** Store the flatbed cart belongs to (it can't leave that store's parking lot). */
    var cartStore: Int = -1,
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
    /** Licence fees (cinemas and theaters). */
    val fees: Double = 0.0,
    /** Cost of shoplifted stock. */
    val stolen: Double = 0.0,
) {
    val profit get() = revenue - cogs - wages - rent - marketing - fees - stolen
}

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
    /** Dirt on each floor tile (0..100), row by row over the interior grid. */
    var dirt: MutableList<Float> = mutableListOf(),
    /** Cost of stock shoplifted today. */
    var stolenToday: Double = 0.0,
    /** Units of each product boxed up on the back room's storage shelves (not on display yet). */
    val backStock: MutableMap<String, Int> = mutableMapOf(),
    /**
     * Rows at the back of the store walled off as a staff-only back room: -1 until the interior is first laid out
     * (old saves), 0 for none.
     */
    var backRoom: Int = -1,
    /** First tile (x) of the doorway in the back room's wall. */
    var backDoor: Int = -1,
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
enum class Sky(val label: String, val icon: String) {
    CLEAR("Clear", "sun"), CLOUDY("Cloudy", "partly_cloudy"), RAIN("Rain", "rain"), STORM("Thunderstorm", "storm"), SNOW("Snow", "snow"), FOG("Fog", "fog");

    val wet get() = this == RAIN || this == STORM || this == SNOW
}

@Serializable
data class WeatherSpan(val start: Long, val end: Long, val sky: Sky)

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
data class Rival(
    val id: Int,
    val name: String,
    val color: Int,
    var cash: Double,
    var netWorth: Double,
    /** Yesterday's operating profit across all stores. */
    var trend: Double = 0.0,
    /** 0 balanced, 1 aggressive expander, 2 premium (wealthy areas), 3 discounter, 4 specialist. */
    var strategy: Int = 0,
    /** Business types this company likes to open. */
    var focus: List<String> = emptyList(),
    /** 0..1: how readily it expands, cuts prices and goes after your stores. */
    var aggression: Double = .5,
    /** Net worth at the end of each day (last 60 days). */
    val worthHistory: MutableList<Double> = mutableListOf(),
    /** Latest moves, newest first. */
    val log: MutableList<String> = mutableListOf(),
    var revenue: Double = 0.0,
    var milestone: Int = 0,
)

@Serializable
data class RivalBusiness(
    val id: Int,
    val rivalId: Int,
    val buildingId: Int,
    val typeId: String,
    val name: String,
    /** 1..3: size of the fit-out (staff, registers and displays). */
    var level: Int = 1,
    var reputation: Double = .55,
    /** Price relative to the market price; below 1 means a price war. */
    var priceFactor: Double = 1.0,
    var marketing: Double = 0.0,
    /** Smoothed daily profit. */
    var profit: Double = 0.0,
    var revenue: Double = 0.0,
    var customers: Int = 0,
    var opened: Int = 0,
    var owned: Boolean = false,
    var losingDays: Int = 0,
    /** Player business this store is fighting on price (-1 none). */
    var targetBiz: Int = -1,
)

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
    /** Consecutive midnights with a deep overdraft. */
    var debtDays: Int = 0,
    var peakNetWorth: Double = 0.0,
    /** Bankruptcy no longer ends the game. */
    var sandbox: Boolean = false,
    var gameOver: Boolean = false,
    /** Upcoming weather, in time order. */
    val weather: MutableList<WeatherSpan> = mutableListOf(),
    /** 0..1 snow lying on the ground. */
    var snowCover: Double = 0.0,
    /** Which map the city was generated from (older saves are all the classic grid). */
    val mapId: String = "classic",
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
