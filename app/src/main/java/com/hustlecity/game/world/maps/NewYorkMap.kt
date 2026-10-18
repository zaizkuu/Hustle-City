package com.hustlecity.game.world

import com.hustlecity.game.data.c
import kotlin.math.cos
import kotlin.math.sin

/**
 * New York: Manhattan's grid tilted 29° the way the real one is, Central Park and its lake, Broadway cutting
 * across the avenues (Times Square where it meets 7th), the crooked streets of the Financial District, and
 * Brooklyn's warehouses across the East River, joined by three bridges.
 */
object NewYorkMap : PlannedMap("nyc", "New York", "Manhattan's tilted grid, Central Park, Broadway and Brooklyn across the East River.") {
    private const val THETA = .506f
    private val CU = cos(THETA); private val SU = sin(THETA)
    private const val OX = 640f; private const val OY = 40f
    private const val SU_AV = 130f
    private const val SV_ST = 62f

    /** Manhattan grid coordinates (u across the island west→east, v down the island north→south) to world. */
    private fun wx(u: Float, v: Float) = OX + u * CU - v * SU
    private fun wy(u: Float, v: Float) = OY + u * SU + v * CU
    private fun g(vararg uv: Float): FloatArray {
        val out = FloatArray(uv.size)
        for (i in 0 until uv.size / 2) { out[i * 2] = wx(uv[i * 2], uv[i * 2 + 1]); out[i * 2 + 1] = wy(uv[i * 2], uv[i * 2 + 1]) }
        return out
    }

    override val extent = floatArrayOf(0f, 0f, 1400f, 1500f)

    private val brick = intArrayOf(c(0xFF9C5B4A), c(0xFFA0644F), c(0xFF8D6E63), c(0xFFB08968), c(0xFFD7C4A3), c(0xFF7B5E57))
    private val brown = intArrayOf(c(0xFFA35D4E), c(0xFF8E5446), c(0xFFC2A387), c(0xFF9E7B65), c(0xFFB9A48A), c(0xFF6F4E45))
    private val stone = intArrayOf(c(0xFFC9B8A0), c(0xFFE0D6C8), c(0xFFA1887F), c(0xFFBCAAA4), c(0xFF9C6B5B), c(0xFFD8CBB6))
    private val glass = intArrayOf(c(0xFF8FA3B8), c(0xFF5D6D7E), c(0xFFB0BEC5), c(0xFFA7B4C2), c(0xFFCFC6B8), c(0xFF6E7F91))
    private val gray = intArrayOf(c(0xFF7F8C8D), c(0xFF95A5A6), c(0xFF8E6E53), c(0xFF6B7B8C), c(0xFFA09080))

    override val hoods = listOf(
        HoodSpec(Neighborhood(0, "Upper West Side", c(0xFF2F80ED), .30f, 36.0, 1.15f, 12f, 38f, floatArrayOf(.6f, .3f, .1f, 0f), brown,
            Look.NY_TOWNHOUSE, floatArrayOf(8f, 16f), floatArrayOf(16f, 26f), greenery = .6f), *g(120f, 120f, 120f, 330f)),
        HoodSpec(Neighborhood(1, "Upper East Side", c(0xFFEB5757), .30f, 44.0, 1.35f, 14f, 46f, floatArrayOf(.55f, .35f, .1f, 0f), stone,
            Look.NY_TOWNHOUSE, floatArrayOf(9f, 18f), floatArrayOf(16f, 26f), greenery = .6f), *g(660f, 120f, 660f, 330f)),
        HoodSpec(Neighborhood(2, "Midtown", c(0xFFBB6BD9), .46f, 50.0, 1.3f, 24f, 82f, floatArrayOf(.15f, .38f, .47f, 0f), glass,
            Look.NY_DOWNTOWN, floatArrayOf(18f, 34f), floatArrayOf(20f, 34f), greenery = .3f), *g(390f, 560f, 560f, 660f, 260f, 640f)),
        HoodSpec(Neighborhood(3, "Hell's Kitchen", c(0xFF27AE60), .32f, 32.0, 1.0f, 10f, 32f, floatArrayOf(.5f, .4f, .1f, 0f), brick,
            Look.NY_TOWNHOUSE, floatArrayOf(8f, 18f), floatArrayOf(16f, 28f)), *g(70f, 560f, 90f, 760f)),
        HoodSpec(Neighborhood(4, "Chelsea", c(0xFFF2C94C), .34f, 34.0, 1.05f, 10f, 40f, floatArrayOf(.35f, .45f, .2f, 0f), brick,
            Look.NY_TOWNHOUSE, floatArrayOf(10f, 22f), floatArrayOf(16f, 30f)), *g(200f, 900f, 460f, 930f, 690f, 880f)),
        HoodSpec(Neighborhood(5, "Financial District", c(0xFF56CCF2), .40f, 46.0, 1.25f, 20f, 70f, floatArrayOf(.2f, .3f, .5f, 0f), glass,
            Look.NY_DOWNTOWN, floatArrayOf(14f, 30f), floatArrayOf(18f, 32f), greenery = .2f), *g(400f, 1150f, 300f, 1240f, 560f, 1120f)),
        HoodSpec(Neighborhood(6, "Brooklyn", c(0xFFF2994A), .18f, 20.0, .85f, 7f, 18f, floatArrayOf(.3f, .2f, 0f, .5f), gray,
            Look.INDUSTRIAL, floatArrayOf(14f, 30f), floatArrayOf(18f, 34f), alleys = .15f, greenery = .3f), 1150f, 820f, 1060f, 1080f, 1250f, 1300f, 980f, 1400f),
    )

    override val startHood = 3

    override val placeHoods = listOf(
        "essentials" to 4, "primekitchen" to 3, "lux" to 1, "metro" to 6, "distro" to 4, "imports" to 6,
        "motors" to 0, "school" to 2, "recruit" to 4, "bank" to 5, "buzz" to 2, "hospital" to 1,
        "fuel" to 3, "fuel" to 0, "fuel" to 6, "fuel" to 4, "officeplus" to 6,
    )

    private val avenues = listOf("11th Avenue", "10th Avenue", "Amsterdam Avenue", "Broadway Mall", "7th Avenue", "Madison Avenue", "5th Avenue")
    private val avenueName = listOf("11th Avenue", "10th Avenue", "9th Avenue", "8th Avenue", "7th Avenue", "6th Avenue", "5th Avenue")
    private val fidi = listOf("Wall Street", "Broad Street", "Pearl Street", "Water Street", "William Street", "Nassau Street", "Maiden Lane",
        "Fulton Street", "John Street", "Beaver Street", "Stone Street", "Liberty Street", "Cedar Street", "Pine Street", "Exchange Place")
    private val bk = listOf("Atlantic Avenue", "Court Street", "Smith Street", "Columbia Street", "Van Brunt Street", "Union Street", "President Street",
        "Carroll Street", "Sackett Street", "Degraw Street", "Kent Avenue", "Wythe Avenue", "Bedford Avenue", "Berry Street", "Grand Street")

    override fun plan(p: Plan) = with(p) {
        // Manhattan: a long island with wobbly shores; everything else is the Hudson, the East River and the bay
        val island = ArrayList<Float>()
        var v = -60f
        while (v <= 1060f) { island += wx(-22f + 10f * sin(v / 120f), v); island += wy(-22f + 10f * sin(v / 120f), v); v += 40f }
        island.addAll(g(30f, 1180f, 160f, 1290f, 330f, 1345f, 470f, 1330f, 620f, 1240f, 720f, 1130f).toList())
        v = 1060f
        while (v >= -60f) { island += wx(800f + 12f * sin(v / 95f + 1f), v); island += wy(800f + 12f * sin(v / 95f + 1f), v); v -= 40f }
        land += island.toFloatArray()
        // Brooklyn, across the East River
        val brooklyn = ArrayList<Float>()
        v = 380f
        while (v <= 1500f) { brooklyn += wx(912f + 14f * sin(v / 70f), v); brooklyn += wy(912f + 14f * sin(v / 70f), v); v += 40f }
        brooklyn.addAll(g(1500f, 1500f, 1500f, 380f).toList())
        land += brooklyn.toFloatArray()

        // Central Park, with its lake and a reservoir
        val cp = g(272f, -80f, 508f, -80f, 508f, 300f, 272f, 300f)
        parks += Park("Central Park", cp, Park.GREEN)
        water += g(330f, 130f, 380f, 105f, 440f, 120f, 455f, 165f, 400f, 190f, 345f, 175f)
        water += g(330f, -20f, 450f, -30f, 470f, 30f, 400f, 55f, 335f, 40f)
        // the grid: avenues every 130 m, streets every 62 m, the wide crosstown streets every fourth
        val gridArea = g(-60f, -90f, 840f, -90f, 840f, 1062f, -60f, 1062f)
        grid(gridArea, OX, OY, THETA, SU_AV, SV_ST,
            clsU = { RoadClass.AVENUE }, clsV = { k -> if (k % 4 == 0) RoadClass.AVENUE else RoadClass.STREET },
            nameU = { i -> avenueName.getOrElse(i) { "${ordinal(12 - i)} Avenue" } },
            nameV = { k -> "${ordinal((96 - 4 * k).coerceAtLeast(1))} Street" },
            skipU = { i -> i < 0 || i > 6 }, skipV = { k -> k < 0 || k > 17 })
        // Broadway runs on the diagonal
        curve(g(30f, -60f, 150f, 260f, 245f, 500f, 360f, 700f, 470f, 880f, 565f, 1060f, 600f, 1200f, 560f, 1300f), RoadClass.AVENUE, "Broadway")
        // Times Square, where Broadway crosses 7th Avenue
        val ts = g(372f, 700f, 404f, 705f, 418f, 790f, 384f, 785f)
        parks += Park("Times Square", ts, Park.PLAZA, roads = true)
        landmarks += Landmark(Landmark.BILLBOARDS, wx(395f, 745f), wy(395f, 745f), THETA, 1f, "Times Square")
        skyline += g(180f, 460f, 700f, 460f, 700f, 860f, 180f, 860f) to 1.5f
        // the Financial District: older, crooked streets
        val downtown = g(10f, 1062f, 785f, 1062f, 700f, 1150f, 610f, 1235f, 470f, 1315f, 330f, 1330f, 170f, 1280f, 45f, 1185f)
        organic(downtown, 52f, 13f, THETA + .38f, .16f, RoadClass.STREET, fidi, ring = null)
        skyline += downtown to 1.6f
        parks += Park("Battery Park", g(200f, 1270f, 470f, 1300f, 430f, 1345f, 300f, 1360f, 170f, 1310f), Park.GREEN)
        landmarks += Landmark(Landmark.MONUMENT, wx(330f, 1300f), wy(330f, 1300f), 0f, 1f, "Battery")
        // the waterfront highways
        curve(g(-8f, -60f, -6f, 300f, -8f, 700f, -2f, 1062f, 60f, 1185f, 170f, 1270f), RoadClass.BOULEVARD, "West Side Highway")
        curve(g(786f, -60f, 788f, 300f, 786f, 700f, 784f, 1062f, 705f, 1150f, 610f, 1232f), RoadClass.AVENUE, "FDR Drive")
        // Brooklyn's own grid at its own angle
        val bkArea = ArrayList<Float>()
        v = 400f
        while (v <= 1500f) { bkArea += wx(935f, v); bkArea += wy(935f, v); v += 60f }
        bkArea.addAll(g(1500f, 1500f, 1500f, 400f).toList())
        grid(bkArea.toFloatArray(), 1100f, 900f, .08f, 105f, 72f,
            clsU = { i -> if (i % 3 == 0) RoadClass.AVENUE else RoadClass.STREET }, clsV = { RoadClass.STREET },
            nameU = { i -> bk[Math.floorMod(i, bk.size)] }, nameV = { k -> bk[Math.floorMod(k + 7, bk.size)] })
        // three bridges over the East River
        curve(g(640f, 1170f, 760f, 1110f, 900f, 1060f, 1010f, 1020f), RoadClass.AVENUE, "Brooklyn Bridge", bridge = true)
        roads.add(wx(700f, 930f), wy(700f, 930f), wx(1010f, 900f), wy(1010f, 900f), RoadClass.AVENUE, "Manhattan Bridge", bridge = true)
        roads.add(wx(720f, 560f), wy(720f, 560f), wx(1010f, 520f), wy(1010f, 520f), RoadClass.AVENUE, "Williamsburg Bridge", bridge = true)
        Unit
    }

    override fun streetSpecies(hood: Neighborhood, rnd: kotlin.random.Random) = if (rnd.nextFloat() < .15f) 4 else 0
}
