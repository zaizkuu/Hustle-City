package com.hustlecity.game.world

import com.hustlecity.game.data.c

/**
 * Seoul: the Han River sweeping across with Yeouido's towers on an island, Gyeongbokgung palace and Sejong-daero
 * in the old north, hanok lanes in Bukchon, the Cheonggyecheon stream, Myeongdong and Namsan's tower, Itaewon,
 * Hongdae, and Gangnam's superblocks south of the river.
 */
object SeoulMap : PlannedMap("seoul", "Seoul", "The Han River and its bridges, Gyeongbokgung, Namsan Tower, Myeongdong and Gangnam.") {
    override val extent = floatArrayOf(0f, 0f, 1300f, 1300f)

    override val firstNames = listOf("Min-jun", "Seo-yeon", "Ji-ho", "Ha-eun", "Do-yun", "Ji-woo", "Seo-jun", "Su-ah", "Ye-jun", "Ji-an",
        "Hyun-woo", "Yu-na", "Jae-won", "Da-eun", "Sung-min", "Hye-jin", "Tae-yang", "Eun-ji", "Joon-ho", "So-yeon")
    override val lastNames = listOf("Kim", "Lee", "Park", "Choi", "Jung", "Kang", "Cho", "Yoon", "Jang", "Lim", "Han", "Oh", "Seo", "Shin", "Kwon", "Hwang")

    private val modern = intArrayOf(c(0xFFE8E6E1), c(0xFFD5D8DC), c(0xFFBFC5CB), c(0xFFF0EDE6), c(0xFFC9CED4))
    private val towers = intArrayOf(c(0xFF8FA3B8), c(0xFF6F8399), c(0xFFB0BEC5), c(0xFF9EA7AD), c(0xFF5D6D7E), c(0xFF7D9AB2))
    private val brick = intArrayOf(c(0xFFA0644F), c(0xFF8D6E63), c(0xFFB08968), c(0xFF9C7B6A), c(0xFFC2A387))
    private val hanok = intArrayOf(c(0xFFEDE3D3), c(0xFFE2D6C2), c(0xFFF2EADC), c(0xFFD9CBB5))
    private val works = intArrayOf(c(0xFF8E8E86), c(0xFF9AA0A6), c(0xFFA0846C), c(0xFF7B8794))

    override val hoods = listOf(
        HoodSpec(Neighborhood(0, "Jongno", c(0xFFEB5757), .42f, 34.0, 1.1f, 10f, 40f, floatArrayOf(.3f, .5f, .2f, 0f), brick,
            Look.DENSE_ASIA, floatArrayOf(7f, 15f), floatArrayOf(10f, 20f), alleys = .1f, greenery = .3f), 850f, 260f, 1000f, 380f, 680f, 240f, 1180f, 60f, 1250f, 300f, 530f, 330f),
        HoodSpec(Neighborhood(1, "Bukchon", c(0xFF8E6E53), .26f, 30.0, 1.15f, 4f, 7f, floatArrayOf(.7f, .3f, 0f, 0f), hanok,
            Look.HANOK, floatArrayOf(8f, 14f), floatArrayOf(10f, 16f), alleys = .05f, greenery = .3f), 810f, 110f),
        HoodSpec(Neighborhood(2, "Myeongdong", c(0xFFF2C94C), .55f, 50.0, 1.3f, 14f, 46f, floatArrayOf(.1f, .75f, .15f, 0f), modern,
            Look.DENSE_ASIA, floatArrayOf(6f, 13f), floatArrayOf(10f, 18f), alleys = .08f, greenery = .15f), 700f, 480f),
        HoodSpec(Neighborhood(3, "Itaewon", c(0xFF9B51E0), .38f, 36.0, 1.1f, 8f, 26f, floatArrayOf(.4f, .55f, .05f, 0f), brick,
            Look.DENSE_ASIA, floatArrayOf(7f, 15f), floatArrayOf(10f, 20f), alleys = .12f, greenery = .35f), 980f, 690f, 820f, 720f),
        HoodSpec(Neighborhood(4, "Yongsan", c(0xFF2BB3A6), .34f, 32.0, 1.05f, 10f, 44f, floatArrayOf(.35f, .4f, .25f, 0f), modern,
            Look.CBD, floatArrayOf(12f, 26f), floatArrayOf(14f, 28f), greenery = .4f), 540f, 640f, 420f, 600f),
        HoodSpec(Neighborhood(5, "Hongdae", c(0xFF27AE60), .44f, 30.0, 1.0f, 8f, 24f, floatArrayOf(.45f, .5f, .05f, 0f), brick,
            Look.DENSE_ASIA, floatArrayOf(7f, 14f), floatArrayOf(10f, 20f), alleys = .12f, greenery = .3f), 160f, 330f, 300f, 200f, 260f, 520f, 220f, 40f),
        HoodSpec(Neighborhood(6, "Seongsu", c(0xFFF2994A), .24f, 22.0, .9f, 6f, 16f, floatArrayOf(.25f, .25f, .05f, .45f), works,
            Look.INDUSTRIAL, floatArrayOf(12f, 26f), floatArrayOf(14f, 28f), alleys = .15f, greenery = .2f), 1160f, 560f, 1180f, 680f),
        HoodSpec(Neighborhood(7, "Yeouido", c(0xFF56CCF2), .4f, 52.0, 1.4f, 30f, 92f, floatArrayOf(.1f, .25f, .65f, 0f), towers,
            Look.CBD, floatArrayOf(22f, 40f), floatArrayOf(20f, 34f), greenery = .5f), 330f, 790f),
        HoodSpec(Neighborhood(8, "Gangnam", c(0xFF2F80ED), .5f, 58.0, 1.45f, 28f, 90f, floatArrayOf(.2f, .4f, .4f, 0f), towers,
            Look.CBD, floatArrayOf(18f, 36f), floatArrayOf(18f, 34f), greenery = .45f), 960f, 1080f, 1180f, 1150f, 800f, 1200f),
        HoodSpec(Neighborhood(9, "Seocho", c(0xFFBB6BD9), .32f, 44.0, 1.3f, 30f, 70f, floatArrayOf(.65f, .25f, .1f, 0f), modern,
            Look.CBD, floatArrayOf(20f, 38f), floatArrayOf(18f, 34f), greenery = .55f), 300f, 1100f, 520f, 1000f, 150f, 1220f),
    )

    override val startHood = 5

    override val placeHoods = listOf(
        "essentials" to 4, "primekitchen" to 0, "lux" to 8, "metro" to 6, "distro" to 9, "imports" to 6,
        "motors" to 9, "school" to 5, "recruit" to 0, "bank" to 7, "buzz" to 8, "hospital" to 3,
        "fuel" to 5, "fuel" to 6, "fuel" to 8, "fuel" to 4, "officeplus" to 7,
    )

    private val jongno = listOf("Insadong-gil", "Ujeongguk-ro", "Samil-daero", "Donhwamun-ro", "Changgyeonggung-ro", "Supyo-ro", "Yulgok-ro",
        "Gwanhun-gil", "Ikseon-gil", "Daehak-ro", "Cheonggyecheon-ro", "Eulji-ro")
    private val bukchon = listOf("Bukchon-ro", "Gyedong-gil", "Gahoe-ro", "Changdeokgung-gil", "Samcheong-ro", "Yunboseon-gil")
    private val myeongdong = listOf("Myeongdong-gil", "Myeongdong 8-gil", "Namdaemun-ro", "Toegye-ro", "Sogong-ro", "Myeongdong 10-gil", "Eulji-ro")
    private val itaewon = listOf("Itaewon-ro", "Bogwang-ro", "Hoenamu-ro", "Noksapyeong-daero", "Usadan-ro", "Hannam-daero", "Gyeongridan-gil")
    private val hongdae = listOf("Yanghwa-ro", "Wausan-ro", "Eoulmadang-ro", "Hongik-ro", "Sinchon-ro", "Yeonhui-ro", "Donggyo-ro", "Seogang-ro",
        "Changjeon-ro", "Dongmak-ro")
    private val gangnam = listOf("Teheran-ro", "Gangnam-daero", "Nonhyeon-ro", "Seolleung-ro", "Eonju-ro", "Dosan-daero", "Apgujeong-ro",
        "Bongeunsa-ro", "Samseong-ro", "Yeoksam-ro", "Gangnam-daero 94-gil", "Teheran-ro 5-gil")
    private val seocho = listOf("Banpo-daero", "Seocho-daero", "Sapyeong-daero", "Seorae-ro", "Hyoryeong-ro", "Bangbae-ro", "Nambusunhwan-ro")
    private val seongsu = listOf("Seongsuil-ro", "Seongsui-ro", "Ttukseom-ro", "Achasan-ro", "Yeonmujang-gil", "Wangsimni-ro")
    private val yongsan = listOf("Hangang-daero", "Wonhyo-ro", "Cheongpa-ro", "Baekbeom-ro", "Huam-ro", "Yongsan-ro")

    /** Road-name addresses: the road first, then the building number. */
    override fun address(number: Int, street: String, hood: String) = "$street ${number + 1}"

    override fun plan(p: Plan) = with(p) {
        // the Han River and Yeouido
        river(floatArrayOf(-60f, 780f, 250f, 762f, 560f, 830f, 850f, 800f, 1100f, 850f, 1360f, 830f), 74f)
        val yeouido = Geo.ellipse(330f, 788f, 185f, 44f, 28, .02f)
        islands += yeouido
        // bridges
        roads.add(300f, 640f, 300f, 960f, RoadClass.AVENUE, "Mapo Bridge", bridge = true)
        roads.add(470f, 650f, 470f, 800f, RoadClass.STREET, "Wonhyo Bridge", bridge = true)
        roads.add(600f, 690f, 580f, 980f, RoadClass.AVENUE, "Banpo Bridge", bridge = true)
        roads.add(800f, 680f, 790f, 960f, RoadClass.AVENUE, "Hannam Bridge", bridge = true)
        roads.add(1060f, 700f, 1060f, 1000f, RoadClass.AVENUE, "Seongsu Bridge", bridge = true)
        // riverside expressways
        curve(floatArrayOf(-60f, 690f, 250f, 676f, 560f, 742f, 850f, 712f, 1100f, 762f, 1360f, 742f), RoadClass.BOULEVARD, "Gangbyeonbuk-ro")
        curve(floatArrayOf(-60f, 872f, 250f, 852f, 560f, 920f, 850f, 890f, 1100f, 940f, 1360f, 920f), RoadClass.BOULEVARD, "Olympic-daero")

        // Gyeongbokgung and Sejong-daero
        val palace = rect(480f, -80f, 720f, 150f)
        parks += Park("Gyeongbokgung", palace, Park.GROUNDS)
        landmarks += Landmark(Landmark.PALACE_GATE, 600f, 148f, Math.PI.toFloat(), 1.2f, "Gwanghwamun")
        roads.add(600f, 160f, 600f, 430f, RoadClass.BOULEVARD, "Sejong-daero")
        roads.add(600f, 430f, 560f, 600f, RoadClass.AVENUE, "Namdaemun-ro")
        parks += Park("Gwanghwamun Square", rect(590f, 175f, 610f, 300f), Park.PLAZA, roads = true)
        curve(floatArrayOf(380f, 330f, 600f, 330f, 900f, 340f, 1360f, 320f), RoadClass.AVENUE, "Jong-ro")
        curve(floatArrayOf(380f, 440f, 600f, 430f, 900f, 450f, 1360f, 440f), RoadClass.AVENUE, "Eulji-ro")
        curve(floatArrayOf(-60f, 190f, 200f, 175f, 480f, 160f), RoadClass.AVENUE, "Seongsan-ro")
        // the Cheonggyecheon stream between Jong-ro and Eulji-ro
        canal(floatArrayOf(620f, 385f, 800f, 392f, 1000f, 378f, 1160f, 396f), 6f)
        // Bukchon's hanok lanes and the old streets of Jongno
        organic(floatArrayOf(730f, -60f, 960f, -60f, 960f, 170f, 730f, 175f), 34f, 9f, .1f, .14f, RoadClass.LANE, bukchon, ring = null)
        organic(floatArrayOf(960f, -60f, 1360f, -60f, 1360f, 160f, 960f, 170f), 46f, 12f, .15f, .16f, RoadClass.LANE, jongno, ring = null)
        organic(floatArrayOf(480f, 165f, 592f, 168f, 590f, 440f, 548f, 556f, 480f, 556f), 40f, 10f, 0f, .15f, RoadClass.LANE, jongno, ring = null)
        organic(floatArrayOf(620f, 175f, 1360f, 160f, 1360f, 425f, 620f, 425f), 44f, 11f, -.05f, .15f, RoadClass.LANE, jongno, ring = null)
        // Myeongdong: shopping streets packed together
        organic(floatArrayOf(610f, 455f, 840f, 455f, 840f, 560f, 610f, 560f), 36f, 8f, .08f, .1f, RoadClass.LANE, myeongdong, ring = null)
        landmarks += Landmark(Landmark.BILLBOARDS, 720f, 500f, 0f, .8f, "Myeongdong", Landmark.SIGNS)
        // Namsan and N Seoul Tower
        val namsan = Geo.ellipse(860f, 590f, 150f, 92f, 30, -.1f)
        parks += Park("Namsan Park", namsan, Park.GREEN)
        landmarks += Landmark(Landmark.TOWER, 860f, 590f, 0f, 1.25f, "N Seoul Tower")
        curve(floatArrayOf(700f, 600f, 860f, 690f, 1040f, 620f), RoadClass.AVENUE, "Sowol-ro")
        // Itaewon between the mountain and the river
        organic(floatArrayOf(700f, 650f, 1150f, 610f, 1160f, 710f, 700f, 705f), 42f, 11f, -.1f, .14f, RoadClass.LANE, itaewon, ring = null)
        // Yongsan
        grid(floatArrayOf(380f, 560f, 700f, 560f, 700f, 690f, 380f, 690f), 540f, 620f, .2f, 82f, 64f,
            { RoadClass.STREET }, { RoadClass.STREET }, { yongsan[Math.floorMod(it, yongsan.size)] }, { yongsan[Math.floorMod(it + 3, yongsan.size)] })
        // Mapo, Sinchon and Hongdae in the west
        grid(floatArrayOf(-60f, -60f, 470f, -60f, 470f, 690f, -60f, 690f), 200f, 400f, -.32f, 92f, 66f,
            { if (it % 3 == 0) RoadClass.AVENUE else RoadClass.STREET }, { RoadClass.STREET },
            { hongdae[Math.floorMod(it, hongdae.size)] }, { hongdae[Math.floorMod(it + 5, hongdae.size)] })
        organic(floatArrayOf(60f, 200f, 300f, 220f, 280f, 470f, 50f, 450f), 38f, 10f, -.32f, .14f, RoadClass.LANE, hongdae, ring = null)
        // Seongsu's workshops by the river
        grid(floatArrayOf(1080f, 470f, 1360f, 470f, 1360f, 735f, 1080f, 740f), 1200f, 600f, .12f, 80f, 70f,
            { RoadClass.STREET }, { RoadClass.STREET }, { seongsu[Math.floorMod(it, seongsu.size)] }, { seongsu[Math.floorMod(it + 2, seongsu.size)] })
        // Yeouido's towers
        grid(yeouido, 330f, 788f, .02f, 74f, 40f, { RoadClass.AVENUE }, { RoadClass.STREET }, { "Yeouido-daero" }, { "Gukjegeumyung-ro" })
        skyline += yeouido to 1.8f
        // Gangnam: superblocks of boulevards with lanes inside
        val gangnamArea = floatArrayOf(640f, 935f, 1360f, 960f, 1360f, 1360f, 640f, 1360f)
        grid(gangnamArea, 1000f, 1150f, -.32f, 220f, 175f, { RoadClass.BOULEVARD }, { RoadClass.BOULEVARD },
            { gangnam[Math.floorMod(it, 2)] }, { gangnam[Math.floorMod(it, 4) + 2] })
        organic(gangnamArea, 46f, 6f, -.32f, .2f, RoadClass.LANE, gangnam.drop(6), ring = null)
        skyline += gangnamArea to 1.7f
        // Seocho and Banpo: tall apartment blocks
        grid(floatArrayOf(-60f, 905f, 640f, 935f, 640f, 1360f, -60f, 1360f), 300f, 1100f, -.15f, 110f, 90f,
            { if (it % 2 == 0) RoadClass.AVENUE else RoadClass.STREET }, { RoadClass.STREET },
            { seocho[Math.floorMod(it, seocho.size)] }, { seocho[Math.floorMod(it + 3, seocho.size)] })
        Unit
    }

    override fun streetSpecies(hood: Neighborhood, rnd: kotlin.random.Random) = if (rnd.nextFloat() < .55f) 4 else if (rnd.nextFloat() < .2f) 3 else 0

    override fun parkSpecies(park: Park, rnd: kotlin.random.Random) = when {
        park.kind == Park.GROUNDS -> if (rnd.nextFloat() < .5f) 2 else 0
        rnd.nextFloat() < .4f -> 3
        rnd.nextFloat() < .3f -> 2
        else -> 0
    }

    // boxy city cars and minivans, orange and silver cabs, and a lot of buses; white, gray and black cars
    override val trafficMix = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 6, 6, 6, 6, 2, 2, 2, 1, 3, 7, 7, 7, 8, 8, 9, 9, 10, 12, 12, 12)
    override val trafficPalette = intArrayOf(
        c(0xFFF5F5F5), c(0xFFF0F0F0), c(0xFFC0C6CC), c(0xFF7F878E), c(0xFF1C1C1E), c(0xFF26323F), c(0xFF5A6570), c(0xFF8E2442),
    )
    /** Seoul's color-coded buses: blue trunk lines, green branch lines, red express and yellow loop buses. */
    private val buses = intArrayOf(
        c(0xFF1F6FD1), c(0xFFF4F5F2), c(0xFF1F6FD1), c(0xFFF4F5F2), c(0xFF4CAF50), c(0xFFF4F5F2), c(0xFF4CAF50), c(0xFFF4F5F2),
        c(0xFFD63A2F), c(0xFFF4F5F2), c(0xFFF2B705), c(0xFFF4F5F2),
    )
    /** Mopeds: most of them delivering, a box on the rack. */
    private val mopeds = intArrayOf(
        c(0xFF2B2B2B), c(0xFF2AC1BC), c(0xFFF2F2F2), c(0xFFE53935), c(0xFF1E1E1E), c(0xFFF2F2F2), c(0xFF9AA0A6), c(0xFF9AA0A6),
        c(0xFF1E5BC6), c(0xFF1E5BC6), c(0xFF2B2B2B), c(0xFF2AC1BC),
    )
    override fun livery(style: Int) = when (style) { 7 -> buses; 12 -> mopeds; else -> null }
    override val taxiEvery = 3
    override val taxiColors = intArrayOf(c(0xFFF0812E), c(0xFFD5DADE), c(0xFFF0812E), c(0xFFF4F5F2))
}
