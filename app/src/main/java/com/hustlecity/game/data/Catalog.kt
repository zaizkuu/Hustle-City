package com.hustlecity.game.data

/** Colors are ARGB ints so the renderer can use them without conversion. */
fun c(hex: Long): Int = hex.toInt()

/** Boxes of stock one pallet rack holds in a warehouse. */
const val WAREHOUSE_BOXES_PER_RACK = 24

enum class StaffRole(val label: String, val baseWage: Double) {
    CUSTOMER_SERVICE("Customer Service", 15.0),
    CLEANER("Cleaner", 13.0),
    GYM_TRAINER("Gym Trainer", 19.0),
    HAIRSTYLIST("Hairstylist", 18.0),
    HR_MANAGER("HR Manager", 26.0),
    PURCHASING_AGENT("Purchasing Agent", 24.0),
    ;

    /** Managers work from your Headquarters. */
    val atHeadquarters get() = this == HR_MANAGER || this == PURCHASING_AGENT
}

enum class ItemFn { CASH_REGISTER, DISPLAY, EQUIPMENT, LOCKER, SHOWER, BOARD, DECOR, SPEAKER, SEATING, CLEANING, BED, HOME_FRIDGE, TV, SOFA, OFFICE }

/** One box of a furniture model, in tile units of the item footprint (x across, y depth, z up in meters). */
data class Part(val x0: Float, val y0: Float, val z0: Float, val x1: Float, val y1: Float, val z1: Float, val color: Int)

data class FurnitureDef(
    val id: String,
    val name: String,
    val price: Double,
    val w: Int,
    val d: Int,
    val fn: ItemFn,
    val tags: Set<String> = emptySet(),
    /** Customers per hour this piece of equipment can serve (gym/hair) or limit (lockers/showers). */
    val capacity: Int = 0,
    /** Units of a product this display can hold. */
    val displayUnits: Int = 0,
    val decor: Int = 0,
    val music: Int = 0,
    val description: String = "",
    val parts: List<Part>,
)

enum class Source(val label: String) { METRO("Metro Wholesale"), DISTRO("CityDistro Wholesale"), IMPORT("Global Imports Co."), NONE("Service") }

data class ProductDef(
    val id: String,
    val name: String,
    val cost: Double,
    val price: Double,
    val unitsPerBox: Int,
    /** Furniture tag that displays (or performs, for services) this product. */
    val tag: String,
    val source: Source,
    val service: Boolean = false,
    val color: Int,
)

data class Req(val tag: String, val label: String)

data class BusinessTypeDef(
    val id: String,
    val name: String,
    val products: List<Pair<String, Double>>,
    val requirements: List<Req>,
    val staffRole: StaffRole,
    val needsRegister: Boolean,
    val baseDemand: Double,
    val curve: FloatArray,
    val color: Int,
    val emoji: String,
    val wealthSensitive: Boolean = false,
    /** Warehouses and headquarters: no customers, no register. */
    val facility: Boolean = false,
    /** Building kind this type opens in: RETAIL, WAREHOUSE or OFFICE. */
    val buildingKind: String = "RETAIL",
) {
    val sources: Set<Source> get() = products.mapNotNull { Catalog.product(it.first)?.source }.filter { it != Source.NONE }.toSet()
    val primaryProducts: List<String> get() = products.take(3).mapNotNull { Catalog.product(it.first)?.name }
}

data class CourseDef(val id: String, val name: String, val price: Double, val hours: Int, val description: String)

data class VehicleDef(
    val id: String,
    val name: String,
    val price: Double,
    val cargo: Int,
    val maxSpeed: Float,
    val accel: Float,
    val length: Float,
    val width: Float,
    val height: Float,
    val color: Int,
    val happiness: Int,
    val style: Int, // 0 car, 1 pickup, 2 van, 3 sports
)

data class CampaignDef(val id: String, val name: String, val pricePerDay: Double, val boost: Double, val needsCourse: Boolean, val description: String)

data class LoanDef(val id: String, val name: String, val amount: Double, val days: Int, val dailyRate: Double, val minNetWorth: Double)

enum class PlaceKind { FURNITURE_STORE, WHOLESALER, IMPORTER, CAR_DEALER, GAS_STATION, SCHOOL, RECRUITER, BANK, MARKETING, HOSPITAL }

data class PlaceDef(val id: String, val name: String, val kind: PlaceKind, val slogan: String, val color: Int, val emoji: String, val stock: List<String> = emptyList())

object Catalog {
    // ---------------------------------------------------------------- furniture
    private val WOOD = c(0xFF8B5A3C)
    private val WOOD_L = c(0xFFB07A52)
    private val STEEL = c(0xFFB8C0CA)
    private val DARK = c(0xFF2E3238)
    private val WHITE = c(0xFFE9EDF2)
    private val GLASS = c(0xFF8EC5E8)

    private fun box(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, color: Int) = Part(x0, y0, z0, x1, y1, z1, color)

    val furniture: List<FurnitureDef> = listOf(
        FurnitureDef("cash_register", "Cash Register", 900.0, 2, 1, ItemFn.CASH_REGISTER, setOf("register"),
            description = "Checkout counter. Each staffed register serves about 40 customers per hour.",
            parts = listOf(
                box(0f, .15f, 0f, 2f, .95f, 1f, c(0xFF4A4F57)),
                box(0f, .1f, 1f, 2f, 1f, 1.06f, c(0xFF9AA3AD)),
                box(.25f, .3f, 1.06f, .9f, .8f, 1.3f, DARK),
                box(.4f, .35f, 1.3f, .8f, .45f, 1.62f, c(0xFF56CCF2)),
            )),
        FurnitureDef("shelf", "Product Shelf", 1570.0, 2, 1, ItemFn.DISPLAY, setOf("shelf"), displayUnits = 140,
            description = "Wooden shelving for packaged goods: snacks, gifts, umbrellas, wine and more.",
            parts = listOf(
                box(0f, 0f, 0f, 2f, .2f, 1.9f, WOOD),
                box(0f, 0f, 0f, .08f, .9f, 1.9f, WOOD),
                box(1.92f, 0f, 0f, 2f, .9f, 1.9f, WOOD),
                box(.08f, .2f, .45f, 1.92f, .9f, .5f, WOOD_L),
                box(.08f, .2f, 1.05f, 1.92f, .9f, 1.1f, WOOD_L),
                box(.15f, .3f, .5f, .9f, .8f, .8f, c(0xFFEB5757)),
                box(1.0f, .3f, .5f, 1.8f, .8f, .75f, c(0xFFF2C94C)),
                box(.15f, .3f, 1.1f, .7f, .8f, 1.4f, c(0xFF27AE60)),
                box(.8f, .3f, 1.1f, 1.8f, .8f, 1.35f, c(0xFF2F80ED)),
            )),
        FurnitureDef("drinks_fridge", "Drinks Fridge", 1250.0, 1, 1, ItemFn.DISPLAY, setOf("drinks_fridge"), displayUnits = 160,
            description = "Glass door fridge for cans, energy drinks and beer.",
            parts = listOf(
                box(.05f, .05f, 0f, .95f, .88f, 2.0f, WHITE),
                box(.12f, .88f, .15f, .88f, .95f, 1.85f, GLASS),
                box(.18f, .5f, .4f, .82f, .88f, .6f, c(0xFFEB5757)),
                box(.18f, .5f, 1.0f, .82f, .88f, 1.2f, c(0xFF27AE60)),
            )),
        FurnitureDef("fresh_fridge", "Refrigerated Display", 2100.0, 2, 1, ItemFn.DISPLAY, setOf("fresh_fridge"), displayUnits = 150,
            description = "Open cooler for fresh food.",
            parts = listOf(
                box(0f, .1f, 0f, 2f, .95f, .9f, WHITE),
                box(0f, 0f, 0f, 2f, .25f, 1.8f, c(0xFFD5DBE2)),
                box(.1f, .3f, .9f, 1.9f, .9f, 1.05f, c(0xFF6FCF97)),
            )),
        FurnitureDef("freezer", "Chest Freezer", 1600.0, 2, 1, ItemFn.DISPLAY, setOf("freezer"), displayUnits = 180,
            description = "Keeps frozen food frozen.",
            parts = listOf(
                box(0f, .1f, 0f, 2f, .95f, .85f, WHITE),
                box(.06f, .16f, .85f, 1.94f, .89f, .9f, GLASS),
            )),
        FurnitureDef("flower_stand", "Flower Stand", 450.0, 1, 1, ItemFn.DISPLAY, setOf("flower_stand"), displayUnits = 70,
            description = "Tiered buckets for fresh flowers.",
            parts = listOf(
                box(.1f, .1f, 0f, .9f, .9f, .7f, WOOD_L),
                box(.15f, .15f, .7f, .45f, .45f, 1.1f, c(0xFFEB5757)),
                box(.55f, .15f, .7f, .85f, .45f, 1.05f, c(0xFFF2C94C)),
                box(.15f, .55f, .7f, .45f, .85f, 1.0f, c(0xFFBB6BD9)),
                box(.55f, .55f, .7f, .85f, .85f, 1.08f, c(0xFFF2994A)),
            )),
        FurnitureDef("produce", "Produce Crates", 380.0, 2, 1, ItemFn.DISPLAY, setOf("produce"), displayUnits = 170,
            description = "Slanted crates for fruit and vegetables.",
            parts = listOf(
                box(0f, .1f, 0f, 2f, .95f, .75f, WOOD_L),
                box(.05f, .15f, .75f, .65f, .9f, .9f, c(0xFFEB5757)),
                box(.7f, .15f, .75f, 1.3f, .9f, .9f, c(0xFFF2C94C)),
                box(1.35f, .15f, .75f, 1.95f, .9f, .9f, c(0xFFF2994A)),
            )),
        FurnitureDef("coffee_machine", "Espresso Machine", 2400.0, 2, 1, ItemFn.DISPLAY, setOf("coffee"), displayUnits = 400,
            description = "Industrial espresso machine on a counter. Serves coffee and tea.",
            parts = listOf(
                box(0f, .1f, 0f, 2f, .95f, 1f, c(0xFF5D4037)),
                box(0f, .05f, 1f, 2f, 1f, 1.05f, c(0xFF3E2723)),
                box(.3f, .25f, 1.05f, 1.4f, .8f, 1.55f, STEEL),
                box(1.5f, .3f, 1.05f, 1.85f, .7f, 1.35f, DARK),
            )),
        FurnitureDef("pastry_case", "Pastry Display", 1100.0, 1, 1, ItemFn.DISPLAY, setOf("pastry"), displayUnits = 90,
            description = "Glass case for croissants and cupcakes.",
            parts = listOf(
                box(.05f, .1f, 0f, .95f, .95f, .9f, WOOD),
                box(.08f, .15f, .9f, .92f, .9f, 1.35f, GLASS),
                box(.2f, .3f, .95f, .8f, .75f, 1.05f, c(0xFFF2C94C)),
            )),
        FurnitureDef("grill", "Industrial Grill", 3200.0, 2, 1, ItemFn.DISPLAY, setOf("grill"), displayUnits = 220,
            description = "Flat-top grill for burgers and hotdogs.",
            parts = listOf(
                box(0f, .1f, 0f, 2f, .95f, .95f, STEEL),
                box(.1f, .2f, .95f, 1.9f, .85f, 1.0f, DARK),
                box(0f, 0f, .95f, 2f, .15f, 1.6f, STEEL),
            )),
        FurnitureDef("fryer", "Industrial Fryer", 2800.0, 1, 1, ItemFn.DISPLAY, setOf("fryer"), displayUnits = 260,
            description = "Deep fryer for french fries.",
            parts = listOf(
                box(.05f, .1f, 0f, .95f, .95f, .95f, STEEL),
                box(.15f, .2f, .95f, .85f, .85f, 1.0f, c(0xFFF2C94C)),
            )),
        FurnitureDef("hair_chair", "Hairdresser Chair", 1800.0, 1, 2, ItemFn.EQUIPMENT, setOf("hair_chair"), capacity = 2,
            description = "Styling chair with mirror. One stylist serves ~2 customers per hour.",
            parts = listOf(
                box(.1f, 0f, 0f, .9f, .1f, 1.9f, c(0xFFD5DBE2)),
                box(.15f, .1f, .8f, .85f, .14f, 1.7f, GLASS),
                box(.25f, .9f, 0f, .75f, 1.4f, .5f, DARK),
                box(.2f, .85f, .5f, .8f, 1.55f, .62f, c(0xFFEB5757)),
                box(.2f, 1.45f, .62f, .8f, 1.55f, 1.2f, c(0xFFEB5757)),
            )),
        FurnitureDef("hair_wash", "Hair Wash Station", 1500.0, 1, 2, ItemFn.EQUIPMENT, setOf("hair_wash"), capacity = 3,
            description = "Basin for shampooing and blow-drying.",
            parts = listOf(
                box(.1f, 0f, 0f, .9f, .6f, .9f, WHITE),
                box(.2f, .1f, .9f, .8f, .5f, 1.0f, c(0xFF9AA3AD)),
                box(.2f, .7f, 0f, .8f, 1.8f, .55f, DARK),
            )),
        FurnitureDef("treadmill", "Treadmill", 5250.0, 1, 2, ItemFn.EQUIPMENT, setOf("workout"), capacity = 2,
            description = "Workout machine. Customer capacity: 2.",
            parts = listOf(
                box(.15f, .2f, 0f, .85f, 1.95f, .25f, DARK),
                box(.2f, .3f, .25f, .8f, 1.9f, .28f, c(0xFF4F4F4F)),
                box(.15f, .1f, 0f, .25f, .3f, 1.3f, DARK),
                box(.75f, .1f, 0f, .85f, .3f, 1.3f, DARK),
                box(.15f, .05f, 1.3f, .85f, .35f, 1.45f, c(0xFF27AE60)),
            )),
        FurnitureDef("workout_bench", "Workout Bench", 3500.0, 1, 2, ItemFn.EQUIPMENT, setOf("workout"), capacity = 2,
            description = "Bench with barbell rack. Customer capacity: 2.",
            parts = listOf(
                box(.3f, .5f, 0f, .7f, 1.8f, .5f, DARK),
                box(.05f, .1f, 0f, .15f, .3f, 1.6f, DARK),
                box(.85f, .1f, 0f, .95f, .3f, 1.6f, DARK),
                box(0f, .15f, 1.3f, 1f, .25f, 1.38f, STEEL),
            )),
        FurnitureDef("weight_rack", "Dumbbell Rack", 1900.0, 2, 1, ItemFn.EQUIPMENT, setOf("workout"), capacity = 2,
            description = "Rack of free weights. Customer capacity: 2.",
            parts = listOf(
                box(0f, .2f, 0f, 2f, .8f, .5f, DARK),
                box(.1f, .25f, .5f, 1.9f, .75f, .75f, c(0xFF333333)),
            )),
        FurnitureDef("boxing_bag", "Boxing Bag", 450.0, 1, 1, ItemFn.EQUIPMENT, setOf("workout"), capacity = 1,
            description = "Hanging punching bag. Customer capacity: 1.",
            parts = listOf(
                box(.3f, .3f, .3f, .7f, .7f, 1.6f, c(0xFFEB5757)),
                box(.3f, .3f, .8f, .7f, .7f, 1.0f, DARK),
                box(.47f, .47f, 1.6f, .53f, .53f, 2.4f, STEEL),
            )),
        FurnitureDef("gym_locker", "Gym Lockers", 1100.0, 2, 1, ItemFn.LOCKER, setOf("locker"), capacity = 16,
            description = "Lockers for gym customers. Each set supports 16 customers per hour.",
            parts = listOf(
                box(0f, 0f, 0f, 2f, .6f, 1.9f, c(0xFF2F80ED)),
                box(.98f, .6f, 0f, 1.02f, .62f, 1.9f, c(0xFF1B4F8A)),
            )),
        FurnitureDef("shower", "Public Shower", 2600.0, 1, 1, ItemFn.SHOWER, setOf("shower"), capacity = 10,
            description = "Shower cubicle. Customer capacity: 10.",
            parts = listOf(
                box(0f, 0f, 0f, 1f, 1f, .1f, WHITE),
                box(0f, 0f, 0f, .06f, 1f, 2.1f, GLASS),
                box(.94f, 0f, 0f, 1f, 1f, 2.1f, GLASS),
                box(0f, 0f, 0f, 1f, .06f, 2.1f, WHITE),
            )),
        FurnitureDef("fitness_board", "Fitness Planning Board", 350.0, 1, 1, ItemFn.BOARD, setOf("fitness_board"),
            description = "Whiteboard for training plans. Required for gyms.",
            parts = listOf(
                box(.05f, 0f, .8f, .95f, .08f, 1.8f, WHITE),
                box(.1f, .08f, 1.0f, .5f, .1f, 1.1f, c(0xFF2F80ED)),
                box(.48f, .3f, 0f, .52f, .4f, .8f, STEEL),
            )),
        FurnitureDef("clothing_rack", "Clothing Rack", 700.0, 2, 1, ItemFn.DISPLAY, setOf("clothing"), displayUnits = 60,
            description = "Rail for hanging clothes.",
            parts = listOf(
                box(.05f, .45f, 0f, .12f, .55f, 1.6f, STEEL),
                box(1.88f, .45f, 0f, 1.95f, .55f, 1.6f, STEEL),
                box(.05f, .45f, 1.55f, 1.95f, .55f, 1.6f, STEEL),
                box(.15f, .3f, .7f, .7f, .7f, 1.5f, c(0xFF2F80ED)),
                box(.75f, .3f, .7f, 1.25f, .7f, 1.5f, c(0xFFEB5757)),
                box(1.3f, .3f, .7f, 1.85f, .7f, 1.5f, c(0xFFF2C94C)),
            )),
        FurnitureDef("fitting_room", "Fitting Room", 1500.0, 1, 1, ItemFn.DECOR, setOf("fitting"), decor = 6,
            description = "Curtained fitting room. Makes clothing customers happier.",
            parts = listOf(
                box(0f, 0f, 0f, 1f, .06f, 2.1f, c(0xFFD5DBE2)),
                box(0f, 0f, 0f, .06f, 1f, 2.1f, c(0xFFD5DBE2)),
                box(.06f, .94f, .3f, 1f, 1f, 2.0f, c(0xFF9B51E0)),
            )),
        FurnitureDef("electronics_table", "Electronics Table", 1400.0, 2, 1, ItemFn.DISPLAY, setOf("electronics"), displayUnits = 30,
            description = "Demo table for phones, laptops and headphones.",
            parts = listOf(
                box(0f, .1f, 0f, 2f, .9f, .9f, WHITE),
                box(.2f, .3f, .9f, .7f, .7f, .95f, DARK),
                box(.9f, .3f, .9f, 1.5f, .6f, 1.2f, DARK),
                box(1.6f, .35f, .9f, 1.8f, .55f, 1.0f, DARK),
            )),
        FurnitureDef("jewelry_case", "Jewelry Case", 2200.0, 1, 1, ItemFn.DISPLAY, setOf("jewelry"), displayUnits = 25,
            description = "Lit glass case for rings, necklaces and watches.",
            parts = listOf(
                box(.05f, .05f, 0f, .95f, .95f, .9f, DARK),
                box(.08f, .08f, .9f, .92f, .92f, 1.25f, GLASS),
                box(.3f, .3f, .92f, .45f, .45f, .98f, c(0xFFF2C94C)),
            )),
        FurnitureDef("bookshelf", "Bookshelf", 900.0, 2, 1, ItemFn.DISPLAY, setOf("books"), displayUnits = 160,
            description = "Tall shelf full of books.",
            parts = listOf(
                box(0f, 0f, 0f, 2f, .5f, 2.0f, WOOD),
                box(.1f, .5f, .2f, 1.0f, .55f, .8f, c(0xFF2F80ED)),
                box(1.0f, .5f, .2f, 1.9f, .55f, .8f, c(0xFFEB5757)),
                box(.1f, .5f, 1.0f, 1.9f, .55f, 1.6f, c(0xFF27AE60)),
            )),
        FurnitureDef("table_set", "Table & Chairs", 400.0, 2, 2, ItemFn.SEATING, setOf("seating"), decor = 3,
            description = "Seating for coffee shop and fast food customers.",
            parts = listOf(
                box(.6f, .6f, .7f, 1.4f, 1.4f, .76f, WOOD_L),
                box(.95f, .95f, 0f, 1.05f, 1.05f, .7f, DARK),
                box(.1f, .75f, 0f, .5f, 1.25f, .45f, DARK),
                box(1.5f, .75f, 0f, 1.9f, 1.25f, .45f, DARK),
            )),
        FurnitureDef("barstool", "Barstool", 120.0, 1, 1, ItemFn.SEATING, setOf("seating"), decor = 1,
            description = "A tall stool.",
            parts = listOf(
                box(.45f, .45f, 0f, .55f, .55f, .75f, DARK),
                box(.25f, .25f, .75f, .75f, .75f, .85f, c(0xFFC8E6A0)),
            )),
        FurnitureDef("bench", "Bench", 180.0, 2, 1, ItemFn.SEATING, setOf("seating"), decor = 1,
            description = "Simple wooden bench.",
            parts = listOf(
                box(.1f, .3f, .4f, 1.9f, .7f, .48f, WOOD_L),
                box(.2f, .4f, 0f, .3f, .6f, .4f, STEEL),
                box(1.7f, .4f, 0f, 1.8f, .6f, .4f, STEEL),
            )),
        FurnitureDef("plant_large", "Potted Plant (Large)", 160.0, 1, 1, ItemFn.DECOR, decor = 3,
            description = "Adds life to any interior.",
            parts = listOf(
                box(.3f, .3f, 0f, .7f, .7f, .45f, c(0xFFE0E0E0)),
                box(.15f, .15f, .45f, .85f, .85f, 1.4f, c(0xFF2E8B57)),
                box(.3f, .3f, 1.4f, .7f, .7f, 1.75f, c(0xFF3CB371)),
            )),
        FurnitureDef("plant_small", "Potted Plant (Small)", 60.0, 1, 1, ItemFn.DECOR, decor = 1,
            description = "A small plant.",
            parts = listOf(
                box(.38f, .38f, 0f, .62f, .62f, .3f, c(0xFFE0E0E0)),
                box(.3f, .3f, .3f, .7f, .7f, .7f, c(0xFF3CB371)),
            )),
        FurnitureDef("neon_cactus", "Cactus Neon", 100.0, 1, 1, ItemFn.DECOR, decor = 2,
            description = "Wall neon sign.",
            parts = listOf(
                box(.42f, 0f, .9f, .58f, .06f, 2.0f, c(0xFFB9F6CA)),
                box(.2f, 0f, 1.3f, .42f, .06f, 1.4f, c(0xFFB9F6CA)),
                box(.58f, 0f, 1.5f, .8f, .06f, 1.6f, c(0xFFB9F6CA)),
            )),
        FurnitureDef("neon_circle", "Circle Neon", 75.0, 1, 1, ItemFn.DECOR, decor = 2,
            description = "Wall neon sign.",
            parts = listOf(
                box(.15f, 0f, 1.0f, .85f, .06f, 1.08f, c(0xFFFF8AD8)),
                box(.15f, 0f, 1.72f, .85f, .06f, 1.8f, c(0xFF82B1FF)),
                box(.15f, 0f, 1.0f, .23f, .06f, 1.8f, c(0xFFFF8AD8)),
                box(.77f, 0f, 1.0f, .85f, .06f, 1.8f, c(0xFF82B1FF)),
            )),
        FurnitureDef("painting", "Painting", 250.0, 1, 1, ItemFn.DECOR, decor = 3,
            description = "Abstract art.",
            parts = listOf(
                box(.1f, 0f, 1.0f, .9f, .05f, 1.8f, c(0xFF333333)),
                box(.15f, .05f, 1.05f, .5f, .07f, 1.4f, c(0xFFF2994A)),
                box(.5f, .05f, 1.35f, .85f, .07f, 1.75f, c(0xFF2D9CDB)),
            )),
        FurnitureDef("speaker_small", "Loudspeaker (Small)", 300.0, 1, 1, ItemFn.SPEAKER, music = 1, decor = 1,
            description = "Background music keeps customers happy.",
            parts = listOf(box(.3f, .3f, 0f, .7f, .7f, .7f, DARK), box(.4f, .7f, .25f, .6f, .72f, .5f, c(0xFF555555)))),
        FurnitureDef("speaker_medium", "Loudspeaker (Medium)", 650.0, 1, 1, ItemFn.SPEAKER, music = 2, decor = 1,
            description = "Background music keeps customers happy.",
            parts = listOf(box(.25f, .25f, 0f, .75f, .75f, 1.1f, DARK), box(.35f, .75f, .5f, .65f, .77f, .9f, c(0xFF555555)))),
        FurnitureDef("speaker_large", "Loudspeaker (Large)", 1200.0, 1, 1, ItemFn.SPEAKER, music = 3, decor = 2,
            description = "Big sound for big rooms.",
            parts = listOf(box(.15f, .15f, 0f, .85f, .85f, 1.5f, DARK), box(.3f, .85f, .7f, .7f, .87f, 1.3f, c(0xFF555555)))),
        FurnitureDef("cleaning_station", "Cleaning Station", 350.0, 1, 1, ItemFn.CLEANING,
            description = "Cleaners work 50% faster when a station is nearby.",
            parts = listOf(
                box(.2f, .2f, 0f, .8f, .8f, 1.0f, c(0xFFE0E0E0)),
                box(.25f, .25f, 1.0f, .5f, .5f, 1.3f, c(0xFF2F80ED)),
            )),
        FurnitureDef("wall_lamp", "Wall Lamp", 40.0, 1, 1, ItemFn.DECOR, decor = 1,
            description = "Warm wall light.",
            parts = listOf(box(.4f, 0f, 1.5f, .6f, .12f, 2.0f, c(0xFFFFF3C4)))),
        // home
        FurnitureDef("bed", "Bed", 350.0, 2, 2, ItemFn.BED,
            description = "Sleep here to restore energy.",
            parts = listOf(
                box(0f, 0f, 0f, 2f, 2f, .4f, WOOD),
                box(.05f, .4f, .4f, 1.95f, 2f, .55f, c(0xFF5B4FD6)),
                box(.15f, .05f, .4f, 1.85f, .4f, .6f, WHITE),
            )),
        FurnitureDef("home_fridge", "Standard Fridge", 800.0, 1, 1, ItemFn.HOME_FRIDGE,
            description = "Store groceries for cheap meals at home.",
            parts = listOf(box(.05f, .1f, 0f, .95f, .95f, 1.9f, WHITE), box(.8f, .95f, .9f, .85f, 1f, 1.4f, STEEL))),
        FurnitureDef("tv", "Flatscreen TV", 600.0, 2, 1, ItemFn.TV, decor = 2,
            description = "Watching TV at home improves happiness.",
            parts = listOf(box(.1f, .2f, 0f, 1.9f, .7f, .5f, WOOD), box(.2f, .4f, .5f, 1.8f, .48f, 1.4f, DARK))),
        FurnitureDef("sofa", "Sofa", 700.0, 2, 1, ItemFn.SOFA, decor = 2,
            description = "A comfy sofa. Improves happiness at home.",
            parts = listOf(box(0f, 0f, 0f, 2f, 1f, .45f, c(0xFF6D4C41)), box(0f, 0f, .45f, 2f, .25f, .9f, c(0xFF6D4C41)))),
        // office / backdrop props (not sold)
        FurnitureDef("desk", "Desk", 500.0, 2, 1, ItemFn.OFFICE,
            parts = listOf(box(0f, .1f, .72f, 2f, .9f, .78f, WOOD_L), box(.05f, .15f, 0f, .15f, .85f, .72f, DARK), box(1.85f, .15f, 0f, 1.95f, .85f, .72f, DARK), box(.7f, .3f, .78f, 1.3f, .4f, 1.15f, DARK))),
        FurnitureDef("pallet", "Pallet", 100.0, 1, 1, ItemFn.OFFICE,
            parts = listOf(box(.05f, .05f, 0f, .95f, .95f, .15f, WOOD_L), box(.1f, .1f, .15f, .9f, .9f, .8f, c(0xFFC8A165)), box(.15f, .15f, .8f, .85f, .85f, 1.3f, c(0xFFC8A165)))),
        // warehouse & headquarters
        FurnitureDef("pallet_rack", "Pallet Rack", 1800.0, 2, 1, ItemFn.DISPLAY, setOf("pallet_rack"), displayUnits = WAREHOUSE_BOXES_PER_RACK,
            description = "Heavy steel racking. Stores $WAREHOUSE_BOXES_PER_RACK boxes of any product in a warehouse.",
            parts = listOf(
                box(0f, .1f, 0f, .1f, .9f, 2.6f, c(0xFF1F5FAD)), box(1.9f, .1f, 0f, 2f, .9f, 2.6f, c(0xFF1F5FAD)),
                box(0f, .1f, .9f, 2f, .9f, .98f, c(0xFFF2994A)), box(0f, .1f, 1.8f, 2f, .9f, 1.88f, c(0xFFF2994A)),
                box(.15f, .2f, .1f, .95f, .85f, .7f, c(0xFFC8A165)), box(1.05f, .2f, .1f, 1.85f, .85f, .75f, c(0xFFC8A165)),
                box(.2f, .2f, .98f, 1.0f, .85f, 1.55f, c(0xFFC8A165)), box(1.1f, .2f, 1.88f, 1.8f, .85f, 2.4f, c(0xFFC8A165)),
            )),
        FurnitureDef("office_desk", "Office Desk", 650.0, 2, 1, ItemFn.OFFICE, setOf("desk"), decor = 1,
            description = "Desk with a computer. Each manager at your Headquarters needs one.",
            parts = listOf(box(0f, .1f, .72f, 2f, .9f, .78f, WOOD_L), box(.05f, .15f, 0f, .15f, .85f, .72f, DARK), box(1.85f, .15f, 0f, 1.95f, .85f, .72f, DARK),
                box(.7f, .25f, .78f, 1.3f, .32f, 1.18f, DARK), box(.75f, .32f, .82f, 1.25f, .34f, 1.14f, c(0xFF56CCF2)), box(.6f, .95f, 0f, 1.4f, 1f, .5f, c(0xFF333333)))),
        FurnitureDef("filing_cabinet", "Filing Cabinet", 250.0, 1, 1, ItemFn.DECOR, decor = 1,
            description = "Keeps the paperwork in order.",
            parts = listOf(box(.15f, .1f, 0f, .85f, .8f, 1.3f, c(0xFF9AA3AD)), box(.2f, .8f, .35f, .8f, .82f, .45f, DARK), box(.2f, .8f, .85f, .8f, .82f, .95f, DARK))),
        FurnitureDef("water_cooler", "Water Cooler", 180.0, 1, 1, ItemFn.DECOR, decor = 2,
            description = "Staff love a water cooler.",
            parts = listOf(box(.3f, .3f, 0f, .7f, .7f, 1.0f, WHITE), box(.33f, .33f, 1.0f, .67f, .67f, 1.45f, c(0xFF8EC5E8)))),
    )

    private val furnitureById = furniture.associateBy { it.id }
    fun item(id: String): FurnitureDef? = furnitureById[id]

    // ---------------------------------------------------------------- products
    val products: List<ProductDef> = listOf(
        ProductDef("flower_cheap", "Flower (Cheap)", 2.6, 8.5, 40, "flower_stand", Source.DISTRO, color = c(0xFFF2994A)),
        ProductDef("flower_exp", "Flower (Expensive)", 8.0, 22.0, 25, "flower_stand", Source.DISTRO, color = c(0xFFEB5757)),
        ProductDef("apples", "Bag of Apples", 1.6, 4.5, 40, "produce", Source.METRO, color = c(0xFFEB5757)),
        ProductDef("bananas", "Bag of Bananas", 1.3, 3.9, 40, "produce", Source.METRO, color = c(0xFFF2C94C)),
        ProductDef("carrots", "Bag of Carrots", 1.1, 3.4, 40, "produce", Source.METRO, color = c(0xFFF2994A)),
        ProductDef("lettuce", "Head of Lettuce", 1.0, 3.0, 40, "produce", Source.METRO, color = c(0xFF6FCF97)),
        ProductDef("coffee", "Cup of Coffee", 0.45, 3.5, 120, "coffee", Source.METRO, color = c(0xFF6D4C41)),
        ProductDef("tea", "Cup of Tea", 0.3, 2.8, 120, "coffee", Source.METRO, color = c(0xFFA1887F)),
        ProductDef("croissant", "Croissant", 0.8, 3.2, 48, "pastry", Source.METRO, color = c(0xFFE0B36A)),
        ProductDef("cupcake", "Cupcake", 1.0, 3.8, 48, "pastry", Source.METRO, color = c(0xFFFF8AD8)),
        ProductDef("fresh_food", "Fresh Food", 2.8, 7.5, 30, "fresh_fridge", Source.METRO, color = c(0xFF27AE60)),
        ProductDef("frozen_food", "Frozen Food", 2.2, 6.0, 30, "freezer", Source.METRO, color = c(0xFF56CCF2)),
        ProductDef("soda", "Soda Can", 0.4, 2.0, 48, "drinks_fridge", Source.METRO, color = c(0xFFEB5757)),
        ProductDef("snacks", "Candy Bar", 0.5, 2.2, 60, "shelf", Source.METRO, color = c(0xFF9B51E0)),
        ProductDef("toilet_paper", "Toilet Paper", 1.5, 4.5, 24, "shelf", Source.METRO, color = c(0xFFE0E0E0)),
        ProductDef("burger", "Burger", 1.5, 6.5, 50, "grill", Source.METRO, color = c(0xFFB5651D)),
        ProductDef("hotdog", "Hotdog", 0.8, 4.0, 50, "grill", Source.METRO, color = c(0xFFD35400)),
        ProductDef("fries", "French Fries", 0.4, 3.0, 80, "fryer", Source.METRO, color = c(0xFFF2C94C)),
        ProductDef("energy_drink", "Energy Drink", 1.1, 4.0, 48, "drinks_fridge", Source.DISTRO, color = c(0xFF00C853)),
        ProductDef("gym_fee", "Gym Cover Charge", 0.0, 12.0, 0, "workout", Source.NONE, service = true, color = c(0xFF2F80ED)),
        ProductDef("haircut", "Hair Cutting Fee", 0.0, 28.0, 0, "hair_chair", Source.NONE, service = true, color = c(0xFF9B51E0)),
        ProductDef("shampoo", "Hair Shampooing & Blowdry", 0.0, 18.0, 0, "hair_wash", Source.NONE, service = true, color = c(0xFF56CCF2)),
        ProductDef("haircolor", "Hair Chemical / Color Fee", 0.0, 55.0, 0, "hair_chair", Source.NONE, service = true, color = c(0xFFFF8AD8)),
        ProductDef("gift_cheap", "Gift (Cheap)", 4.0, 12.0, 30, "shelf", Source.DISTRO, color = c(0xFFF2994A)),
        ProductDef("gift_exp", "Gift (Expensive)", 14.0, 38.0, 15, "shelf", Source.DISTRO, color = c(0xFFBB6BD9)),
        ProductDef("umbrella", "Umbrella", 3.0, 11.0, 20, "shelf", Source.DISTRO, color = c(0xFF2F80ED)),
        ProductDef("beer", "Beer", 0.9, 3.2, 48, "drinks_fridge", Source.METRO, color = c(0xFFF2C94C)),
        ProductDef("wine", "Bottle of Wine", 6.0, 16.0, 12, "shelf", Source.METRO, color = c(0xFF8E2442)),
        ProductDef("whiskey", "Whiskey", 15.0, 38.0, 12, "shelf", Source.DISTRO, color = c(0xFFB5651D)),
        ProductDef("clothes_cheap", "Classic Clothing", 7.0, 24.0, 20, "clothing", Source.DISTRO, color = c(0xFF2F80ED)),
        ProductDef("clothes_exp", "Designer Clothing", 28.0, 85.0, 10, "clothing", Source.DISTRO, color = c(0xFF333333)),
        ProductDef("book_paper", "Paperback Book", 3.5, 11.0, 40, "books", Source.DISTRO, color = c(0xFF27AE60)),
        ProductDef("book_hard", "Hardcover Book", 8.0, 24.0, 20, "books", Source.DISTRO, color = c(0xFF8B5A3C)),
        ProductDef("smartphone", "Smartphone", 210.0, 449.0, 5, "electronics", Source.IMPORT, color = c(0xFF333333)),
        ProductDef("laptop", "Laptop", 420.0, 899.0, 3, "electronics", Source.IMPORT, color = c(0xFF9AA3AD)),
        ProductDef("headphones", "Headphones", 28.0, 79.0, 10, "electronics", Source.IMPORT, color = c(0xFFEB5757)),
        ProductDef("ring", "Ring", 165.0, 320.0, 5, "jewelry", Source.IMPORT, color = c(0xFFF2C94C)),
        ProductDef("necklace", "Necklace", 125.0, 240.0, 5, "jewelry", Source.IMPORT, color = c(0xFFE0E0E0)),
        ProductDef("watch", "Watch", 270.0, 520.0, 4, "jewelry", Source.IMPORT, color = c(0xFF9AA3AD)),
    )
    private val productById = products.associateBy { it.id }
    fun product(id: String): ProductDef? = productById[id]

    // ---------------------------------------------------------------- business types
    private fun curve(vararg pts: Pair<Int, Float>): FloatArray {
        val out = FloatArray(24)
        val sorted = pts.sortedBy { it.first }
        for (h in 0 until 24) {
            val a = sorted.lastOrNull { it.first <= h } ?: sorted.first()
            val b = sorted.firstOrNull { it.first > h } ?: sorted.last()
            out[h] = if (a.first == b.first) a.second else a.second + (b.second - a.second) * (h - a.first) / (b.first - a.first).toFloat()
        }
        return out
    }

    private val RETAIL = curve(0 to 0f, 6 to 0.05f, 9 to .45f, 12 to .9f, 14 to 1f, 17 to .95f, 19 to .6f, 21 to .25f, 23 to 0.02f)
    private val MORNING = curve(0 to 0f, 5 to .05f, 7 to .8f, 8 to 1f, 10 to .7f, 12 to .6f, 15 to .7f, 17 to .5f, 20 to .15f, 23 to 0f)
    private val MEALS = curve(0 to .05f, 6 to .05f, 9 to .2f, 12 to 1f, 14 to .6f, 16 to .4f, 18 to .95f, 20 to .7f, 22 to .3f, 23 to .1f)
    private val EVENING = curve(0 to .1f, 8 to .05f, 11 to .3f, 15 to .5f, 18 to 1f, 21 to .9f, 23 to .3f)
    private val GYM = curve(0 to .02f, 5 to .2f, 7 to .9f, 9 to .6f, 12 to .5f, 15 to .4f, 17 to .9f, 19 to 1f, 21 to .5f, 23 to .05f)

    private val REG = Req("register", "Cash Register")

    val businessTypes: List<BusinessTypeDef> = listOf(
        BusinessTypeDef("florist", "Florist", listOf("flower_cheap" to .7, "flower_exp" to .35),
            listOf(REG, Req("flower_stand", "Flower Stand")), StaffRole.CUSTOMER_SERVICE, true, 30.0, RETAIL, c(0xFFE91E63), "💐"),
        BusinessTypeDef("produce", "Fruit And Vegetable Store", listOf("apples" to .55, "bananas" to .5, "carrots" to .4, "lettuce" to .35),
            listOf(REG, Req("produce", "Produce Crates")), StaffRole.CUSTOMER_SERVICE, true, 36.0, RETAIL, c(0xFF43A047), "🥕"),
        BusinessTypeDef("coffee", "Coffee Shop", listOf("coffee" to .75, "tea" to .25, "croissant" to .35, "cupcake" to .2),
            listOf(REG, Req("coffee", "Espresso Machine")), StaffRole.CUSTOMER_SERVICE, true, 55.0, MORNING, c(0xFF795548), "☕"),
        BusinessTypeDef("supermarket", "Supermarket", listOf("fresh_food" to .5, "frozen_food" to .35, "soda" to .4, "snacks" to .35, "toilet_paper" to .25),
            listOf(REG, Req("shelf", "Product Shelf"), Req("drinks_fridge", "Drinks Fridge")), StaffRole.CUSTOMER_SERVICE, true, 48.0, RETAIL, c(0xFF1E88E5), "🛒"),
        BusinessTypeDef("fastfood", "Fast Food Restaurant", listOf("burger" to .6, "fries" to .55, "hotdog" to .3, "soda" to .5),
            listOf(REG, Req("grill", "Industrial Grill"), Req("fryer", "Industrial Fryer")), StaffRole.CUSTOMER_SERVICE, true, 58.0, MEALS, c(0xFFF4511E), "🍔"),
        BusinessTypeDef("hairdresser", "Hairdresser", listOf("haircut" to .75, "shampoo" to .4, "haircolor" to .2),
            listOf(REG, Req("hair_chair", "Hairdresser Chair")), StaffRole.HAIRSTYLIST, true, 16.0, RETAIL, c(0xFF8E24AA), "💇"),
        BusinessTypeDef("gym", "Gym", listOf("gym_fee" to 1.0, "energy_drink" to .3, "soda" to .25),
            listOf(Req("workout", "Workout Machines"), Req("locker", "Gym Lockers"), Req("shower", "Public Shower"), Req("fitness_board", "Fitness Planning Board"), Req("drinks_fridge", "Drinks Fridge")),
            StaffRole.GYM_TRAINER, false, 34.0, GYM, c(0xFFD32F2F), "🏋"),
        BusinessTypeDef("giftshop", "Gift Shop", listOf("gift_cheap" to .55, "gift_exp" to .2, "umbrella" to .3),
            listOf(REG, Req("shelf", "Product Shelf")), StaffRole.CUSTOMER_SERVICE, true, 17.0, RETAIL, c(0xFFFF7043), "🎁"),
        BusinessTypeDef("liquor", "Liquor Store", listOf("beer" to .6, "wine" to .35, "whiskey" to .2),
            listOf(REG, Req("drinks_fridge", "Drinks Fridge"), Req("shelf", "Product Shelf")), StaffRole.CUSTOMER_SERVICE, true, 30.0, EVENING, c(0xFF6D4C41), "🍷"),
        BusinessTypeDef("clothing", "Clothing Store", listOf("clothes_cheap" to .45, "clothes_exp" to .14),
            listOf(REG, Req("clothing", "Clothing Rack")), StaffRole.CUSTOMER_SERVICE, true, 17.0, RETAIL, c(0xFF3949AB), "👕", wealthSensitive = true),
        BusinessTypeDef("bookstore", "Bookstore", listOf("book_paper" to .6, "book_hard" to .3),
            listOf(REG, Req("books", "Bookshelf")), StaffRole.CUSTOMER_SERVICE, true, 20.0, RETAIL, c(0xFF00897B), "📚"),
        BusinessTypeDef("electronics", "Electronics Store", listOf("headphones" to .35, "smartphone" to .1, "laptop" to .05),
            listOf(REG, Req("electronics", "Electronics Table")), StaffRole.CUSTOMER_SERVICE, true, 7.0, RETAIL, c(0xFF455A64), "📱", wealthSensitive = true),
        BusinessTypeDef("jewelry", "Jewelry Store", listOf("ring" to .12, "necklace" to .12, "watch" to .07),
            listOf(REG, Req("jewelry", "Jewelry Case")), StaffRole.CUSTOMER_SERVICE, true, 3.0, RETAIL, c(0xFFC9A227), "💎", wealthSensitive = true),
    )
    /** Non-retail operations you can open in warehouses and offices. */
    val facilityTypes: List<BusinessTypeDef> = listOf(
        BusinessTypeDef("warehouse", "Warehouse", emptyList(), listOf(Req("pallet_rack", "Pallet Rack")), StaffRole.CLEANER, false, 0.0, FloatArray(24),
            c(0xFF8D6E63), "📦", facility = true, buildingKind = "WAREHOUSE"),
        BusinessTypeDef("hq", "Headquarters", emptyList(), listOf(Req("desk", "Office Desk")), StaffRole.HR_MANAGER, false, 0.0, FloatArray(24),
            c(0xFF34495E), "🏢", facility = true, buildingKind = "OFFICE"),
    )

    private val typeById = (businessTypes + facilityTypes).associateBy { it.id }
    fun type(id: String): BusinessTypeDef = typeById[id] ?: businessTypes.first()

    // ---------------------------------------------------------------- courses
    const val COURSE_HR = "basic_hr"
    const val COURSE_MARKETING = "marketing"
    const val COURSE_LOGISTICS = "logistics"
    const val COURSE_IMPORT = "import"
    const val COURSE_REAL_ESTATE = "real_estate"
    const val COURSE_LEADERSHIP = "leadership"

    val courses = listOf(
        CourseDef(COURSE_HR, "Basic HR", 500.0, 4, "Learn to recruit and manage staff. Unlocks hiring at Apex Recruitment."),
        CourseDef(COURSE_MARKETING, "Marketing Fundamentals", 1200.0, 4, "Unlocks internet ads, billboards and radio campaigns."),
        CourseDef(COURSE_LOGISTICS, "Logistics Management", 2500.0, 6, "Unlocks scheduled deliveries from wholesalers to your stores."),
        CourseDef(COURSE_IMPORT, "Import & Trade", 6000.0, 8, "Unlocks Global Imports Co. for electronics and jewelry."),
        CourseDef(COURSE_REAL_ESTATE, "Real Estate Investing", 4000.0, 6, "Unlocks buying buildings instead of renting them."),
        CourseDef(COURSE_LEADERSHIP, "Leadership", 3000.0, 6, "Your employees are 15% more satisfied and train faster."),
    )
    fun course(id: String) = courses.first { it.id == id }

    // ---------------------------------------------------------------- vehicles
    val vehicles = listOf(
        VehicleDef("tico", "Tico Compact", 6800.0, 6, 19f, 6f, 3.8f, 1.7f, 1.45f, c(0xFFC0392B), 0, 0),
        VehicleDef("volta", "Volta Sedan", 15500.0, 8, 23f, 7f, 4.6f, 1.85f, 1.45f, c(0xFF2C3E50), 4, 0),
        VehicleDef("bulldog", "Bulldog Pickup", 24000.0, 14, 21f, 6.5f, 5.3f, 2.0f, 1.8f, c(0xFFE0E0E0), 4, 1),
        VehicleDef("freighter", "Freighter Van", 31000.0, 24, 18f, 5f, 5.6f, 2.1f, 2.4f, c(0xFFF5F5F5), 2, 2),
        VehicleDef("stellar", "Stellar GT", 78000.0, 2, 33f, 12f, 4.5f, 1.95f, 1.2f, c(0xFFF2C94C), 15, 3),
    )
    fun vehicle(id: String) = vehicles.first { it.id == id }

    // ---------------------------------------------------------------- marketing / loans
    val campaigns = listOf(
        CampaignDef("flyers", "Flyer Distribution", 120.0, .10, false, "Local flyers around the block."),
        CampaignDef("internet", "Internet Ads", 350.0, .22, true, "Targeted social media ads."),
        CampaignDef("billboard", "Billboard", 800.0, .35, true, "A billboard on a busy avenue."),
        CampaignDef("radio", "Radio Spots", 1500.0, .50, true, "City-wide radio coverage."),
    )
    fun campaign(id: String) = campaigns.first { it.id == id }

    val loans = listOf(
        LoanDef("starter", "Starter Loan", 15000.0, 60, 0.002, 0.0),
        LoanDef("growth", "Growth Loan", 50000.0, 60, 0.003, 40000.0),
        LoanDef("expansion", "Expansion Loan", 200000.0, 90, 0.0035, 150000.0),
    )

    // ---------------------------------------------------------------- places
    val places = listOf(
        PlaceDef("essentials", "Essentials Appliances", PlaceKind.FURNITURE_STORE, "If you need it, we've got it.", c(0xFF2F80ED), "🛋",
            listOf("cash_register", "shelf", "drinks_fridge", "fresh_fridge", "freezer", "treadmill", "workout_bench", "weight_rack", "boxing_bag",
                "gym_locker", "shower", "fitness_board", "bookshelf", "bench", "barstool", "neon_cactus", "neon_circle", "speaker_small",
                "speaker_medium", "speaker_large", "cleaning_station", "wall_lamp", "pallet_rack")),
        PlaceDef("primekitchen", "Prime Kitchen Supply", PlaceKind.FURNITURE_STORE, "Pro gear for food & beauty.", c(0xFFF2994A), "🍳",
            listOf("coffee_machine", "pastry_case", "grill", "fryer", "hair_chair", "hair_wash", "cash_register", "home_fridge", "bed", "tv", "table_set")),
        PlaceDef("lux", "Lux Interiors", PlaceKind.FURNITURE_STORE, "Make it beautiful.", c(0xFF9B51E0), "🪴",
            listOf("plant_large", "plant_small", "painting", "clothing_rack", "fitting_room", "electronics_table", "jewelry_case", "flower_stand",
                "produce", "sofa", "table_set", "neon_circle")),
        PlaceDef("metro", "Metro Wholesale", PlaceKind.WHOLESALER, "Bulk groceries, drinks & food supplies.", c(0xFF27AE60), "📦"),
        PlaceDef("distro", "CityDistro Wholesale", PlaceKind.WHOLESALER, "Flowers, gifts, books & fashion in bulk.", c(0xFF00897B), "📦"),
        PlaceDef("imports", "Global Imports Co.", PlaceKind.IMPORTER, "Electronics & jewelry from overseas.", c(0xFF455A64), "🚢"),
        PlaceDef("motors", "Eastside Motors", PlaceKind.CAR_DEALER, "New wheels, fair deals.", c(0xFFEB5757), "🚗"),
        PlaceDef("school", "Hustle Business School", PlaceKind.SCHOOL, "Invest in yourself.", c(0xFF2D9CDB), "🎓"),
        PlaceDef("recruit", "Apex Recruitment", PlaceKind.RECRUITER, "We find the people you need.", c(0xFF9B51E0), "🤝"),
        PlaceDef("bank", "Hudson Bank", PlaceKind.BANK, "Banking on your ambition.", c(0xFF1B5E20), "🏦"),
        PlaceDef("buzz", "Buzz Marketing Agency", PlaceKind.MARKETING, "Get noticed.", c(0xFFFFB300), "📣"),
        PlaceDef("hospital", "City Hospital", PlaceKind.HOSPITAL, "Get well soon.", c(0xFFE53935), "🏥"),
        PlaceDef("fuel", "Fuel Up", PlaceKind.GAS_STATION, "Fill her up.", c(0xFFF2C94C), "⛽"),
        PlaceDef("officeplus", "OfficePlus Supply", PlaceKind.FURNITURE_STORE, "Racks, desks and everything in between.", c(0xFF1F5FAD), "🗄",
            listOf("pallet_rack", "office_desk", "filing_cabinet", "water_cooler", "plant_large", "painting", "speaker_small", "wall_lamp", "cleaning_station", "bench")),
    )
    fun place(id: String) = places.first { it.id == id }

    fun sourcePlaceId(source: Source) = when (source) {
        Source.METRO -> "metro"
        Source.DISTRO -> "distro"
        Source.IMPORT -> "imports"
        Source.NONE -> ""
    }

    // ---------------------------------------------------------------- names
    val firstNames = listOf("Amy", "Ben", "Carla", "Dev", "Elena", "Frank", "Grace", "Hiro", "Isla", "Jamal", "Kara", "Liam", "Maya", "Noah",
        "Olga", "Pedro", "Quinn", "Rosa", "Sam", "Tara", "Umar", "Vera", "Wes", "Ximena", "Yusuf", "Zoe", "Andre", "Bianca", "Caleb", "Dana",
        "Eli", "Fatima", "Gus", "Hana", "Ivan", "Jade", "Kofi", "Lena", "Marco", "Nina", "Owen", "Priya", "Rafael", "Sofia", "Theo", "Uma")
    val lastNames = listOf("Davison", "Cowherd", "Nguyen", "Okafor", "Smith", "Garcia", "Kowalski", "Rossi", "Chen", "Patel", "Murphy", "Silva",
        "Johansson", "Haddad", "Kim", "Brown", "Lopez", "Ivanova", "Mensah", "Fischer", "Reyes", "Novak", "Tanaka", "Walsh", "Moreau", "Ali")

    private val nameAdj = listOf("Scarlet", "Golden", "Urban", "Lucky", "Happy", "Royal", "Iron", "Blue", "Sunny", "Big", "Little", "Fresh", "Prime",
        "Midnight", "Velvet", "Crimson", "Green", "Silver", "Wild", "Metro", "Hustle", "Bright", "Cozy", "Bold")
    private val nameNoun = mapOf(
        "florist" to listOf("Petals", "Blooms", "Bouquet", "Garden", "Rose"),
        "produce" to listOf("Harvest", "Market", "Orchard", "Greens", "Farm"),
        "coffee" to listOf("Bean", "Brew", "Roast", "Cup", "Espresso"),
        "supermarket" to listOf("Mart", "Grocer", "Market", "Pantry", "Foods"),
        "fastfood" to listOf("Burger", "Grill", "Bites", "Diner", "Fryer"),
        "hairdresser" to listOf("Cuts", "Salon", "Shears", "Locks", "Style"),
        "gym" to listOf("Gainz", "Fitness", "Iron", "Muscle", "Strength"),
        "giftshop" to listOf("Gifts", "Treasures", "Presents", "Finds", "Corner"),
        "liquor" to listOf("Spirits", "Cellar", "Barrel", "Bottle", "Vine"),
        "clothing" to listOf("Threads", "Wear", "Closet", "Apparel", "Fashion"),
        "bookstore" to listOf("Pages", "Books", "Chapter", "Library", "Novel"),
        "electronics" to listOf("Tech", "Gadgets", "Circuit", "Electro", "Digital"),
        "jewelry" to listOf("Gems", "Jewels", "Sparkle", "Diamonds", "Gold"),
    )

    fun randomBusinessName(typeId: String, rnd: kotlin.random.Random): String {
        val nouns = nameNoun[typeId] ?: listOf("Store")
        return "${nameAdj.random(rnd)} ${nouns.random(rnd)}"
    }

    val rivalCompanies = listOf(
        "Davison Group" to c(0xFFEB5757), "Lunar Holdings" to c(0xFF2F80ED), "Brooks & Sons" to c(0xFF27AE60),
        "Vanta Retail" to c(0xFF9B51E0), "Golden Fork Inc." to c(0xFFF2C94C), "Pinnacle Ventures" to c(0xFF56CCF2),
        "Urban Bloom Ltd." to c(0xFFFF8AD8), "Ironclad Fitness" to c(0xFFF2994A),
    )

    val radioTracks = listOf(
        "Pop" to "Jane & The Boy – Happier Without You", "Lo-fi" to "Night Shift – Rooftop Rain", "Rock" to "The Leases – Rent Is Due",
        "Jazz" to "Midtown Quartet – Blue Avenue", "Hip-hop" to "MC Margin – Profit Season", "Electro" to "Neonbyte – Overtime",
    )
}
