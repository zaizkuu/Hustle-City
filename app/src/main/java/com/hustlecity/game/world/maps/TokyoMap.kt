package com.hustlecity.game.world

import com.hustlecity.game.data.c
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tokyo: the Imperial Palace and its moat at the heart, wide avenues and towers in Shinjuku, Kabukicho's neon
 * lanes, the Shibuya scramble with streets fanning out, Ginza's grid, Akihabara's crowded blocks, the Yamanote
 * loop overhead and the Sumida River in the east. Cars keep left.
 */
object TokyoMap : PlannedMap("tokyo", "Tokyo", "The palace and its moat, Shinjuku, Shibuya's scramble, Ginza and Akihabara. Cars keep left.") {
    override val extent = floatArrayOf(0f, 0f, 1300f, 1300f)
    override val leftHand = true

    override val firstNames = listOf("Haruto", "Yui", "Sota", "Hina", "Ren", "Aoi", "Yuto", "Sakura", "Riku", "Mei", "Kaito", "Yuna", "Takumi",
        "Rin", "Daiki", "Miyu", "Kenji", "Akiko", "Hiroshi", "Emi", "Shota", "Nanami", "Kazuki", "Ayaka")
    override val lastNames = listOf("Sato", "Suzuki", "Takahashi", "Tanaka", "Watanabe", "Ito", "Yamamoto", "Nakamura", "Kobayashi", "Kato",
        "Yoshida", "Yamada", "Sasaki", "Yamaguchi", "Matsumoto", "Inoue", "Kimura", "Hayashi", "Shimizu", "Mori")

    private val dense = intArrayOf(c(0xFFE6E2DA), c(0xFFD6D2CA), c(0xFFC9CDD2), c(0xFFF0ECE4), c(0xFFBFC4C9), c(0xFFD9CFC1), c(0xFFE0D6E6))
    private val towers = intArrayOf(c(0xFF8FA3B8), c(0xFF6F8399), c(0xFFB0BEC5), c(0xFF9EA7AD), c(0xFFC9C2B6), c(0xFF5D6D7E))
    private val residential = intArrayOf(c(0xFFE9E4DA), c(0xFFDCD5C8), c(0xFFCBC2B4), c(0xFFF2EEE6), c(0xFFB9B2A6))

    override val hoods = listOf(
        HoodSpec(Neighborhood(0, "Shinjuku", c(0xFF2F80ED), .46f, 50.0, 1.35f, 30f, 96f, floatArrayOf(.15f, .35f, .5f, 0f), towers,
            Look.CBD, floatArrayOf(22f, 40f), floatArrayOf(22f, 38f), greenery = .35f), 200f, 460f, 120f, 360f),
        HoodSpec(Neighborhood(1, "Kabukicho", c(0xFFEB5757), .5f, 40.0, 1.1f, 12f, 34f, floatArrayOf(.25f, .65f, .1f, 0f), dense,
            Look.DENSE_ASIA, floatArrayOf(5f, 10f), floatArrayOf(8f, 15f), alleys = .1f, greenery = .1f), 460f, 360f, 430f, 260f),
        HoodSpec(Neighborhood(2, "Shibuya", c(0xFF9B51E0), .52f, 46.0, 1.25f, 14f, 50f, floatArrayOf(.2f, .6f, .2f, 0f), dense,
            Look.DENSE_ASIA, floatArrayOf(6f, 13f), floatArrayOf(9f, 18f), alleys = .1f, greenery = .2f), 300f, 960f, 200f, 1100f, 420f, 1060f),
        HoodSpec(Neighborhood(3, "Yotsuya", c(0xFF27AE60), .3f, 32.0, 1.05f, 8f, 26f, floatArrayOf(.6f, .3f, .1f, 0f), residential,
            Look.DENSE_ASIA, floatArrayOf(7f, 14f), floatArrayOf(10f, 18f), alleys = .15f, greenery = .4f), 560f, 380f, 560f, 120f, 150f, 120f, 280f, 40f),
        HoodSpec(Neighborhood(4, "Akihabara", c(0xFFF2C94C), .48f, 38.0, 1.05f, 12f, 34f, floatArrayOf(.25f, .6f, .15f, 0f), dense,
            Look.DENSE_ASIA, floatArrayOf(5f, 11f), floatArrayOf(9f, 16f), alleys = .1f, greenery = .1f), 950f, 100f, 1100f, 140f, 800f, 80f),
        HoodSpec(Neighborhood(5, "Marunouchi", c(0xFF56CCF2), .44f, 56.0, 1.45f, 32f, 92f, floatArrayOf(.05f, .3f, .65f, 0f), towers,
            Look.CBD, floatArrayOf(24f, 44f), floatArrayOf(24f, 40f), greenery = .5f), 1060f, 450f, 1060f, 320f),
        HoodSpec(Neighborhood(6, "Ginza", c(0xFFBB6BD9), .5f, 60.0, 1.5f, 16f, 48f, floatArrayOf(.1f, .7f, .2f, 0f), towers,
            Look.CBD, floatArrayOf(9f, 18f), floatArrayOf(12f, 22f), greenery = .3f), 1030f, 880f, 1100f, 760f, 1060f, 1200f),
        HoodSpec(Neighborhood(7, "Roppongi", c(0xFFF2994A), .4f, 44.0, 1.3f, 12f, 56f, floatArrayOf(.4f, .4f, .2f, 0f), dense,
            Look.DENSE_ASIA, floatArrayOf(8f, 18f), floatArrayOf(10f, 22f), alleys = .1f, greenery = .35f), 680f, 960f, 780f, 1120f, 560f, 1180f),
        HoodSpec(Neighborhood(8, "Sumida", c(0xFF2BB3A6), .22f, 22.0, .9f, 6f, 16f, floatArrayOf(.45f, .2f, 0f, .35f), residential,
            Look.INDUSTRIAL, floatArrayOf(10f, 22f), floatArrayOf(12f, 24f), alleys = .15f), 1275f, 300f, 1275f, 900f),
    )

    override val startHood = 3

    override val placeHoods = listOf(
        "essentials" to 4, "primekitchen" to 1, "lux" to 6, "metro" to 3, "distro" to 7, "imports" to 4,
        "motors" to 2, "school" to 3, "recruit" to 0, "bank" to 5, "buzz" to 2, "hospital" to 7,
        "fuel" to 0, "fuel" to 3, "fuel" to 7, "fuel" to 4, "officeplus" to 5,
    )

    private val lanes = listOf("Golden Gai", "Omoide Yokocho", "Sakura-dori", "Hanamichi-dori", "Ichiban-gai", "Center-gai", "Spain-zaka",
        "Dogenzaka", "Miyamasu-zaka", "Cat Street", "Kagurazaka", "Hijirizaka", "Kotohira-dori", "Chuo-gai", "Shinmichi-dori", "Ura-dori")
    private val akiba = listOf("Kanda Myojin-dori", "Kuramae-bashi-dori", "Showa-dori", "Junk-dori", "Maid-dori", "Kanda-dori", "Sotokanda-dori")
    private val ginza = listOf("Chuo-dori", "Harumi-dori", "Miyuki-dori", "Namiki-dori", "Sotobori-dori", "Showa-dori", "Marronnier-dori", "Suzuran-dori")
    private val shinjuku = listOf("Chuo-dori", "Kita-dori", "Minami-dori", "Gyoen-dori", "Higashi-dori", "Kitamuki-dori", "Ome-kaido")
    private val roppongi = listOf("Roppongi-dori", "Gaien-higashi-dori", "Imoarai-zaka", "Keyakizaka", "Kogai-zaka", "Toriizaka", "Azabu-juban-dori", "Sakurada-dori")
    private val marunouchi = listOf("Naka-dori", "Gyoko-dori", "Eidai-dori", "Hibiya-dori", "Otemachi-dori", "Kaji-bashi-dori")

    /** Tokyo addresses count blocks: chome, block and building number, then the district. */
    override fun address(number: Int, street: String, hood: String) = "${number / 40 + 1}-${(number / 4) % 20 + 1}-${number % 9 + 1} $hood"

    override fun plan(p: Plan) = with(p) {
        // the Sumida River along the east, and the land beyond it
        river(floatArrayOf(1195f, -60f, 1212f, 300f, 1182f, 650f, 1232f, 1000f, 1212f, 1360f), 42f)
        // the Imperial Palace and its moat
        val grounds = rotRect(800f, 470f, 290f, 320f, .05f)
        parks += Park("Imperial Palace", grounds, Park.GROUNDS)
        val moat = Geo.band(rotRect(800f, 470f, 318f, 348f, .05f).let { it + floatArrayOf(it[0], it[1]) }, 8f)
        water += moat
        landmarks += Landmark(Landmark.PALACE_GATE, 960f, 470f, .05f + Math.PI.toFloat() / 2, 1f, "Otemon")

        // avenues
        curve(floatArrayOf(-60f, 330f, 200f, 320f, 400f, 300f, 640f, 250f, 900f, 230f, 1300f, 175f), RoadClass.BOULEVARD, "Yasukuni-dori")
        curve(floatArrayOf(380f, -60f, 370f, 200f, 360f, 420f, 330f, 700f, 345f, 960f, 390f, 1360f), RoadClass.AVENUE, "Meiji-dori")
        curve(floatArrayOf(300f, 960f, 460f, 860f, 560f, 760f, 660f, 680f), RoadClass.BOULEVARD, "Aoyama-dori")
        curve(floatArrayOf(1000f, -60f, 1035f, 400f, 1050f, 700f, 1065f, 1360f), RoadClass.AVENUE, "Chuo-dori")
        curve(floatArrayOf(960f, 650f, 1080f, 720f, 1180f, 780f, 1300f, 830f), RoadClass.AVENUE, "Harumi-dori", bridge = true)
        curve(floatArrayOf(-60f, 560f, 200f, 560f, 420f, 580f), RoadClass.AVENUE, "Koshu-kaido")
        // the palace's inner ring road and the outer ring
        roads.polyline(rotRect(800f, 470f, 368f, 398f, .05f).let { it + floatArrayOf(it[0], it[1]) }, RoadClass.AVENUE, "Uchibori-dori")
        roads.polyline(Geo.ellipse(780f, 520f, 330f, 330f, 40).let { it + floatArrayOf(it[0], it[1]) }, RoadClass.AVENUE, "Sotobori-dori")
        // bridges across the Sumida
        roads.add(1100f, 140f, 1300f, 120f, RoadClass.STREET, "Kototoi-bashi", bridge = true)
        roads.add(1120f, 600f, 1300f, 640f, RoadClass.STREET, "Eitai-bashi", bridge = true)

        // West Shinjuku: towers on a grid of wide avenues
        val westShinjuku = floatArrayOf(30f, 300f, 330f, 300f, 330f, 620f, 30f, 620f)
        grid(westShinjuku, 180f, 460f, -.1f, 105f, 98f, { RoadClass.AVENUE }, { RoadClass.AVENUE },
            { shinjuku[Math.floorMod(it, shinjuku.size)] }, { shinjuku[Math.floorMod(it + 3, shinjuku.size)] })
        skyline += westShinjuku to 2.0f
        // Kabukicho and East Shinjuku: neon lanes
        organic(floatArrayOf(360f, 200f, 600f, 210f, 610f, 520f, 370f, 540f), 36f, 9f, .02f, .12f, RoadClass.LANE, lanes, ring = null)
        landmarks += Landmark(Landmark.BILLBOARDS, 470f, 330f, 0f, .8f, "Kabukicho")
        // Shibuya: streets fanning out from the scramble
        val sx = 300f; val sy = 960f
        val spokes = floatArrayOf(0f, 50f, 105f, 165f, 220f, 280f, 330f)
        for ((i, a) in spokes.withIndex()) {
            val r = Math.toRadians(a.toDouble()).toFloat()
            roads.add(sx, sy, sx + cos(r) * 320f, sy + sin(r) * 320f, if (i % 2 == 0) RoadClass.STREET else RoadClass.LANE, lanes[(i + 6) % lanes.size])
        }
        for (rad in floatArrayOf(80f, 160f, 240f)) roads.polyline(Geo.ellipse(sx, sy, rad, rad * .92f, 28).let { it + floatArrayOf(it[0], it[1]) }, RoadClass.LANE, lanes[(rad / 80f).toInt() + 7])
        landmarks += Landmark(Landmark.BILLBOARDS, sx, sy, 0f, 1.2f, "Shibuya Crossing")
        // Meiji Shrine's forest and Yoyogi Park
        parks += Park("Meiji Jingu", floatArrayOf(40f, 640f, 250f, 650f, 262f, 830f, 40f, 820f), Park.GROUNDS)
        landmarks += Landmark(Landmark.TORII, 256f, 760f, Math.PI.toFloat() / 2, 1.1f, "Meiji Jingu")
        parks += Park("Shinjuku Gyoen", floatArrayOf(425f, 570f, 610f, 560f, 620f, 700f, 430f, 705f), Park.GREEN)
        // Nakano and Okubo north of Shinjuku: quiet residential streets
        organic(floatArrayOf(-60f, -60f, 368f, -60f, 362f, 290f, -60f, 312f), 54f, 14f, .12f, .18f, RoadClass.LANE, lanes, ring = null)
        // Yotsuya and Ichigaya: quiet residential streets
        organic(floatArrayOf(400f, -60f, 720f, -60f, 640f, 200f, 620f, 540f, 400f, 540f), 52f, 13f, .3f, .18f, RoadClass.LANE, lanes, ring = null)
        // Akihabara and Kanda
        organic(floatArrayOf(700f, -60f, 1160f, -60f, 1160f, 240f, 700f, 230f), 40f, 10f, -.05f, .12f, RoadClass.LANE, akiba, ring = null)
        landmarks += Landmark(Landmark.BILLBOARDS, 1010f, 120f, 0f, .9f, "Akihabara")
        // Marunouchi and Otemachi: business towers east of the palace
        val marunouchiArea = floatArrayOf(985f, 250f, 1150f, 250f, 1140f, 700f, 985f, 700f)
        grid(marunouchiArea, 1060f, 470f, .05f, 112f, 100f, { RoadClass.AVENUE }, { RoadClass.STREET },
            { marunouchi[Math.floorMod(it, marunouchi.size)] }, { marunouchi[Math.floorMod(it + 2, marunouchi.size)] })
        skyline += marunouchiArea to 1.9f
        // Ginza's grid
        grid(floatArrayOf(930f, 720f, 1155f, 720f, 1165f, 1060f, 930f, 1060f), 1040f, 880f, .62f, 56f, 56f,
            { RoadClass.STREET }, { RoadClass.STREET }, { ginza[Math.floorMod(it, ginza.size)] }, { ginza[Math.floorMod(it + 4, ginza.size)] })
        // Tsukiji and Shiodome south of Ginza
        organic(floatArrayOf(930f, 1060f, 1170f, 1060f, 1185f, 1360f, 930f, 1360f), 46f, 11f, .62f, .14f, RoadClass.LANE, ginza, ring = null)
        // Roppongi and Azabu: up and down hill streets, Tokyo Tower above them
        organic(floatArrayOf(470f, 760f, 920f, 720f, 940f, 1200f, 600f, 1250f, 470f, 1180f), 50f, 14f, -.25f, .15f, RoadClass.STREET, roppongi, ring = null)
        landmarks += Landmark(Landmark.TOWER, 820f, 1150f, 0f, 1f, "Tokyo Tower")
        // the east bank of the Sumida
        grid(floatArrayOf(1255f, -60f, 1360f, -60f, 1360f, 1360f, 1255f, 1360f), 1300f, 600f, 0f, 90f, 70f,
            { RoadClass.STREET }, { RoadClass.STREET }, { "Sumida ${it}-chome" }, { "Kuramae-bashi-dori" })
        // the Yamanote loop and the Chuo line, up on viaducts
        rails += Rail("Yamanote Line", Geo.ellipse(660f, 620f, 560f, 590f, 48).let { it + floatArrayOf(it[0], it[1]) }, c(0xFF9ACD32), true)
        rails += Rail("Chuo Line", Geo.spline(floatArrayOf(-60f, 285f, 300f, 270f, 640f, 205f, 1000f, 200f, 1300f, 215f), 5), c(0xFFF2994A), false)
        Unit
    }

    override fun streetSpecies(hood: Neighborhood, rnd: kotlin.random.Random) = when {
        rnd.nextFloat() < .45f -> 4
        rnd.nextFloat() < .3f -> 3
        else -> 0
    }

    override fun parkSpecies(park: Park, rnd: kotlin.random.Random) = when {
        park.kind == Park.GROUNDS -> if (rnd.nextFloat() < .3f) 2 else 0
        rnd.nextFloat() < .55f -> 3
        else -> 0
    }

    // kei cars and minivans, black and green cabs, Toei buses; pearl white, silver and black cars
    override val trafficMix = intArrayOf(6, 6, 6, 6, 6, 6, 0, 0, 0, 0, 0, 0, 0, 0, 2, 2, 1, 3, 7, 7)
    override val trafficPalette = intArrayOf(
        c(0xFFF5F5F2), c(0xFFF0EEE8), c(0xFFC4C9CE), c(0xFF8F969C), c(0xFF1E1F22), c(0xFF2C3E50), c(0xFF6E2A35), c(0xFFDCD3BE),
    )
    /** Kei cars come in pastels, often with a white or contrasting roof. */
    private val kei = intArrayOf(
        c(0xFFBFE3D0), c(0xFFF7F7F5), c(0xFFF1E3C0), c(0xFF6D4C41), c(0xFFB8D4EA), c(0xFFF7F7F5), c(0xFFF2C6CF), c(0xFFF7F7F5),
        c(0xFFF7F7F5), 0, c(0xFFC8CDD2), 0, c(0xFF1E1F22), 0, c(0xFFF2D35B), c(0xFFF7F7F5), c(0xFF8FA36B), c(0xFFF7F7F5),
    )
    private val toei = intArrayOf(c(0xFFF4F4F0), c(0xFF2E8B57), c(0xFFF4F4F0), c(0xFF2E8B57), c(0xFFF2EAD3), c(0xFFC0392B))
    override fun livery(style: Int) = when (style) { 6 -> kei; 7 -> toei; else -> null }
    override val taxiEvery = 3
    override val taxiColors = intArrayOf(c(0xFF1C1D20), c(0xFF3E8E4E), c(0xFF1C1D20), c(0xFF27324A))
}
