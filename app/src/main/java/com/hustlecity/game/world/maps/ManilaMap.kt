package com.hustlecity.game.world

import com.hustlecity.game.data.c
import kotlin.math.sin

/**
 * Manila: the bay and Roxas Boulevard's palms on the west, the Pasig River winding through with its bridges,
 * walled Intramuros and Rizal Park, Binondo's tight streets, the LRT running above Rizal Avenue and Taft, and
 * Makati's business grid at 45° in the south-east.
 */
object ManilaMap : PlannedMap("manila", "Manila", "Manila Bay, the Pasig River, Intramuros, Binondo and Makati's towers.") {
    override val extent = floatArrayOf(0f, 0f, 1300f, 1300f)

    override val firstNames = listOf("Jose", "Maria", "Juan", "Ana", "Mark", "Angelica", "Paolo", "Kristine", "Ramon", "Liza", "Carlo", "Joy",
        "Miguel", "Bea", "Rafael", "Camille", "Jericho", "Patricia", "Nico", "Grace", "Andres", "Lourdes", "Emilio", "Teresa")
    override val lastNames = listOf("Santos", "Reyes", "Cruz", "Bautista", "Garcia", "Mendoza", "Villanueva", "Ramos", "Aquino", "Dela Cruz",
        "Tan", "Lim", "Gonzales", "Castillo", "Navarro", "Pascual", "Fernandez", "Torres", "Domingo", "Soriano", "Uy", "Sy", "Co", "Ocampo")

    private val tropical = intArrayOf(c(0xFFE8D9B5), c(0xFFF2E3C6), c(0xFFD9E4D0), c(0xFFF4D6C6), c(0xFFCFE0E8), c(0xFFE9CFA8), c(0xFFDCC7E0))
    private val colonial = intArrayOf(c(0xFFD9C7A6), c(0xFFC9B08A), c(0xFFE2D3B5), c(0xFFB89B72), c(0xFFEADFC8))
    private val cbd = intArrayOf(c(0xFF8FA3B8), c(0xFF6F8399), c(0xFFB0BEC5), c(0xFF9EA7AD), c(0xFFC9C2B6), c(0xFF5D6D7E))
    private val port = intArrayOf(c(0xFF8E8E7E), c(0xFF9A9F92), c(0xFF7D8B8C), c(0xFFA39885), c(0xFF6E7B7C))

    override val hoods = listOf(
        HoodSpec(Neighborhood(0, "Tondo", c(0xFFF2994A), .2f, 12.0, .7f, 6f, 14f, floatArrayOf(.45f, .2f, 0f, .35f), port,
            Look.INDUSTRIAL, floatArrayOf(10f, 24f), floatArrayOf(12f, 26f), alleys = .2f, greenery = .2f), 260f, 90f, 330f, 200f),
        HoodSpec(Neighborhood(1, "Binondo", c(0xFFEB5757), .42f, 26.0, .95f, 9f, 26f, floatArrayOf(.3f, .6f, .1f, 0f), colonial,
            Look.COLONIAL, floatArrayOf(6f, 13f), floatArrayOf(10f, 18f), alleys = .1f, greenery = .15f), 430f, 330f, 520f, 380f),
        HoodSpec(Neighborhood(2, "Quiapo", c(0xFFF2C94C), .4f, 20.0, .8f, 7f, 18f, floatArrayOf(.35f, .55f, .1f, 0f), tropical,
            Look.TROPICAL, floatArrayOf(7f, 15f), floatArrayOf(10f, 20f), alleys = .15f, greenery = .2f), 640f, 360f, 760f, 300f),
        HoodSpec(Neighborhood(3, "Sampaloc", c(0xFF27AE60), .3f, 18.0, .8f, 6f, 16f, floatArrayOf(.6f, .35f, .05f, 0f), tropical,
            Look.TROPICAL, floatArrayOf(7f, 14f), floatArrayOf(10f, 20f), alleys = .2f, greenery = .35f), 900f, 160f, 1150f, 260f, 1100f, 60f),
        HoodSpec(Neighborhood(4, "Intramuros", c(0xFF8E6E53), .36f, 30.0, 1.0f, 8f, 14f, floatArrayOf(.3f, .45f, .25f, 0f), colonial,
            Look.COLONIAL, floatArrayOf(10f, 20f), floatArrayOf(12f, 22f), greenery = .3f), 360f, 610f),
        HoodSpec(Neighborhood(5, "Ermita", c(0xFF56CCF2), .4f, 30.0, 1.0f, 10f, 36f, floatArrayOf(.35f, .45f, .2f, 0f), tropical,
            Look.TROPICAL, floatArrayOf(10f, 20f), floatArrayOf(12f, 24f), greenery = .45f), 330f, 930f, 480f, 900f),
        HoodSpec(Neighborhood(6, "Malate", c(0xFF9B51E0), .38f, 26.0, .95f, 8f, 30f, floatArrayOf(.45f, .45f, .1f, 0f), tropical,
            Look.TROPICAL, floatArrayOf(8f, 18f), floatArrayOf(12f, 22f), greenery = .45f), 330f, 1150f, 520f, 1200f),
        HoodSpec(Neighborhood(7, "Paco", c(0xFF2BB3A6), .28f, 18.0, .85f, 6f, 16f, floatArrayOf(.45f, .3f, .05f, .2f), tropical,
            Look.TROPICAL, floatArrayOf(8f, 16f), floatArrayOf(12f, 22f), alleys = .18f), 700f, 680f, 880f, 760f, 1180f, 720f, 900f, 950f),
        HoodSpec(Neighborhood(8, "Makati", c(0xFF2F80ED), .46f, 52.0, 1.4f, 26f, 88f, floatArrayOf(.25f, .3f, .45f, 0f), cbd,
            Look.CBD, floatArrayOf(20f, 38f), floatArrayOf(20f, 36f), greenery = .55f), 1040f, 1080f, 1180f, 960f, 940f, 1230f),
    )

    override val startHood = 3

    override val placeHoods = listOf(
        "essentials" to 7, "primekitchen" to 1, "lux" to 8, "metro" to 0, "distro" to 2, "imports" to 0,
        "motors" to 8, "school" to 3, "recruit" to 5, "bank" to 8, "buzz" to 8, "hospital" to 5,
        "fuel" to 0, "fuel" to 2, "fuel" to 6, "fuel" to 7, "officeplus" to 7,
    )

    private val binondo = listOf("Ongpin Street", "Escolta Street", "Juan Luna Street", "Quintin Paredes Street", "Nueva Street", "Dasmariñas Street",
        "Benavidez Street", "Sabino Padilla Street", "Tomas Pinpin Street", "Yuchengco Street", "Salazar Street", "Gandara Street")
    private val sampaloc = listOf("Dapitan Street", "Lacson Avenue", "Laong Laan Road", "Dimasalang Street", "Maceda Street", "Earnshaw Street",
        "Loyola Street", "Vicente Cruz Street", "Gastambide Street", "P. Noval Street", "Blumentritt Road", "Tayuman Street")
    private val ermita = listOf("Padre Faura Street", "Mabini Street", "Del Pilar Street", "Kalaw Avenue", "Pedro Gil Street", "Remedios Street",
        "Adriatico Street", "Arquiza Street", "Nakpil Street", "Malvar Street", "San Andres Street", "Quirino Avenue")
    private val makati = listOf("Ayala Avenue", "Paseo de Roxas", "Makati Avenue", "Gil Puyat Avenue", "Dela Rosa Street", "Legazpi Street",
        "Salcedo Street", "Valero Street", "Rufino Street", "Esteban Street", "Herrera Street", "Amorsolo Street")
    private val tondo = listOf("Juan Luna Street", "Moriones Street", "Capulong Street", "Pacheco Street", "Velasquez Street", "Asuncion Street")
    private val intramuros = listOf("General Luna Street", "Real Street", "Cabildo Street", "Solana Street", "Victoria Street", "Anda Street", "Muralla Street")
    private val paco = listOf("Pedro Gil Street", "Paz Street", "Herran Street", "Otis Street", "Nagtahan Street", "Pandacan Street", "Tejeron Street", "Zamora Street")

    override fun address(number: Int, street: String, hood: String) = "${number * 3 + 100} $street, $hood"

    override fun plan(p: Plan) = with(p) {
        // Manila Bay: everything west of the shore, with the Port Area piers sticking out
        val bay = ArrayList<Float>()
        bay += -100f; bay += -100f
        var y = -60f
        while (y <= 1360f) { bay += 165f + 32f * sin(y / 210f) + 12f * sin(y / 57f); bay += y; y += 30f }
        bay += -100f; bay += 1400f
        water += bay.toFloatArray()
        islands += rect(80f, 150f, 200f, 200f)
        islands += rect(70f, 255f, 200f, 300f)
        // the Pasig River
        river(floatArrayOf(-60f, 455f, 180f, 455f, 330f, 492f, 480f, 445f, 640f, 520f, 800f, 470f, 960f, 560f, 1120f, 520f, 1360f, 590f), 26f)

        // Roxas Boulevard along the bay, lined with palms
        curve(floatArrayOf(205f, 500f, 215f, 700f, 210f, 900f, 230f, 1100f, 220f, 1360f), RoadClass.BOULEVARD, "Roxas Boulevard")
        // Rizal Avenue north of the river and Taft Avenue south of it, with the LRT overhead
        curve(floatArrayOf(430f, -60f, 450f, 200f, 470f, 420f), RoadClass.AVENUE, "Rizal Avenue")
        roads.add(470f, 420f, 520f, 540f, RoadClass.AVENUE, "MacArthur Bridge", bridge = true)
        curve(floatArrayOf(520f, 540f, 535f, 800f, 540f, 1050f, 548f, 1360f), RoadClass.AVENUE, "Taft Avenue")
        rails += Rail("LRT-1", Geo.spline(floatArrayOf(430f, -60f, 450f, 200f, 470f, 420f, 520f, 540f, 535f, 800f, 540f, 1050f, 548f, 1360f), 5), c(0xFFF2C94C), false)
        // Quezon and España boulevards to the north-east
        curve(floatArrayOf(560f, 440f, 700f, 330f, 900f, 220f, 1100f, 120f, 1360f, -20f), RoadClass.BOULEVARD, "España Boulevard")
        roads.add(560f, 420f, 590f, 560f, RoadClass.AVENUE, "Quezon Bridge", bridge = true)
        // more bridges over the Pasig
        roads.add(400f, 410f, 410f, 545f, RoadClass.STREET, "Jones Bridge", bridge = true)
        roads.add(720f, 440f, 740f, 600f, RoadClass.AVENUE, "Ayala Bridge", bridge = true)
        roads.add(950f, 480f, 960f, 640f, RoadClass.STREET, "Nagtahan Bridge", bridge = true)
        roads.add(1160f, 440f, 1150f, 620f, RoadClass.STREET, "Lambingan Bridge", bridge = true)
        // EDSA sweeps around the south-east, Gil Puyat crosses to the bay
        curve(floatArrayOf(1360f, 640f, 1180f, 840f, 1020f, 1040f, 900f, 1360f), RoadClass.BOULEVARD, "EDSA")
        curve(floatArrayOf(215f, 1090f, 540f, 1060f, 800f, 1030f, 1360f, 980f), RoadClass.AVENUE, "Gil Puyat Avenue")
        curve(floatArrayOf(215f, 860f, 400f, 870f, 540f, 880f, 760f, 850f, 940f, 830f), RoadClass.AVENUE, "Quirino Avenue")

        // Tondo and the port: a plain grid
        grid(floatArrayOf(175f, -60f, 430f, -60f, 450f, 230f, 175f, 260f), 300f, 100f, 0f, 75f, 62f,
            { RoadClass.STREET }, { RoadClass.STREET }, { tondo[Math.floorMod(it, tondo.size)] }, { tondo[Math.floorMod(it + 3, tondo.size)] })
        // Binondo: Chinatown's crowded old streets
        organic(floatArrayOf(180f, 262f, 600f, 230f, 600f, 440f, 180f, 450f), 38f, 9f, .05f, .12f, RoadClass.LANE, binondo, ring = null)
        landmarks += Landmark(Landmark.CHURCH, 420f, 320f, 0f, .9f, "Binondo Church", Landmark.PAGODA_TOWER)
        // Quiapo and Sampaloc: an older grid at an angle
        grid(floatArrayOf(470f, -60f, 1360f, -60f, 1360f, 470f, 600f, 470f, 480f, 420f), 900f, 200f, -.42f, 85f, 66f,
            { if (it % 3 == 0) RoadClass.AVENUE else RoadClass.STREET }, { RoadClass.STREET },
            { sampaloc[Math.floorMod(it, sampaloc.size)] }, { sampaloc[Math.floorMod(it + 5, sampaloc.size)] })
        landmarks += Landmark(Landmark.CHURCH, 650f, 440f, -.42f, 1f, "Quiapo Church", Landmark.BASILICA)
        // Intramuros: the walled city
        val walled = rotRect(360f, 615f, 230f, 205f, .12f)
        grid(walled, 360f, 615f, .12f, 52f, 48f, { RoadClass.STREET }, { RoadClass.STREET },
            { intramuros[Math.floorMod(it, intramuros.size)] }, { intramuros[Math.floorMod(it + 2, intramuros.size)] })
        // the walls' centre line: 6 m of rampart between the old town's outer streets and Roxas Boulevard
        walls += rotRect(360f, 615f, 240f, 221f, .12f).let { it + floatArrayOf(it[0], it[1]) }
        // no lots on the ramparts or the strip of lawn outside them: a ring between two rectangles
        val ringOut = rotRect(360f, 615f, 264f, 245f, .12f); val ringIn = rotRect(360f, 615f, 231f, 212f, .12f)
        open += ringOut + floatArrayOf(ringOut[0], ringOut[1], ringIn[0], ringIn[1], ringIn[6], ringIn[7], ringIn[4], ringIn[5], ringIn[2], ringIn[3], ringIn[0], ringIn[1])
        landmarks += Landmark(Landmark.CHURCH, 388f, 601f, .12f, 1.2f, "Manila Cathedral", Landmark.CATHEDRAL)
        // Rizal Park (Luneta) between the walls and the bay
        parks += Park("Rizal Park", floatArrayOf(232f, 742f, 470f, 735f, 478f, 845f, 236f, 852f))
        landmarks += Landmark(Landmark.MONUMENT, 300f, 795f, 0f, 1.1f, "Rizal Monument")
        // a Ferris wheel on the bay walk, between the water and Roxas Boulevard
        landmarks += Landmark(Landmark.WHEEL, 183f, 1215f, Math.PI.toFloat() / 2, 1f, "MOA Eye")
        // Manila City Hall's clock tower in the park's corner by the walls, facing Taft Avenue
        landmarks += Landmark(Landmark.CITY_HALL, 448f, 769f, Math.PI.toFloat() / 2, 1f, "Manila City Hall")
        // the Chinatown arch over the Binondo end of Jones Bridge
        landmarks += Landmark(Landmark.ARCH, 401.3f, 427f, -.074f, 1f, "Binondo Arch")
        // Ermita and Malate: the grid along the bay
        grid(floatArrayOf(205f, 860f, 760f, 850f, 760f, 1360f, 205f, 1360f), 300f, 900f, -.05f, 78f, 60f,
            { RoadClass.STREET }, { RoadClass.STREET }, { ermita[Math.floorMod(it, ermita.size)] }, { ermita[Math.floorMod(it + 6, ermita.size)] })
        // Paco and Pandacan: in between
        organic(floatArrayOf(560f, 565f, 1000f, 612f, 1360f, 650f, 1360f, 890f, 1040f, 990f, 820f, 1050f, 770f, 1040f, 770f, 855f, 560f, 862f), 58f, 14f, .2f, .18f, RoadClass.STREET, paco, ring = null)
        parks += Park("Paco Park", Geo.ellipse(700f, 700f, 32f, 26f, 16))
        // Makati: big blocks at 45°, the tallest towers, Ayala Avenue through the middle
        val makatiArea = floatArrayOf(820f, 1060f, 1040f, 1000f, 1360f, 900f, 1360f, 1360f, 900f, 1360f)
        grid(makatiArea, 1080f, 1120f, .78f, 118f, 118f,
            { RoadClass.AVENUE }, { if (it % 2 == 0) RoadClass.AVENUE else RoadClass.STREET },
            { makati[Math.floorMod(it, makati.size)] }, { makati[Math.floorMod(it + 6, makati.size)] })
        curve(floatArrayOf(860f, 1300f, 1000f, 1160f, 1140f, 1020f, 1300f, 880f), RoadClass.BOULEVARD, "Ayala Avenue")
        skyline += makatiArea to 1.8f
        parks += Park("Greenbelt", rotRect(1100f, 1130f, 70f, 48f, .78f), Park.GREEN)
        Unit
    }

    override fun streetSpecies(hood: Neighborhood, rnd: kotlin.random.Random) = when {
        hood.id == 5 || hood.id == 6 -> if (rnd.nextFloat() < .7f) 1 else 0
        hood.id == 8 -> if (rnd.nextFloat() < .3f) 1 else 0
        else -> if (rnd.nextFloat() < .35f) 1 else 0
    }

    override fun parkSpecies(park: Park, rnd: kotlin.random.Random) = if (rnd.nextFloat() < .5f) 1 else 0

    // nearly a third jeepneys, the old kind and the new minibuses, mopeds weaving everywhere, tricycles on the side
    // streets, white cabs, buses and AUV vans; mostly white and silver cars
    override val trafficMix = intArrayOf(4, 4, 4, 4, 4, 4, 11, 11, 11, 5, 5, 5, 0, 0, 0, 0, 0, 1, 2, 2, 6, 7, 7, 8, 12, 12, 12, 12, 9)
    override val trafficPalette = intArrayOf(
        c(0xFFF2F2F2), c(0xFFE8E8E4), c(0xFFB0B7BF), c(0xFF7F8C8D), c(0xFF212121), c(0xFFC0392B), c(0xFF2C3E50),
        c(0xFF8E2442), c(0xFFCFC2A8),
    )
    /** Jeepneys: bright paint or bare stainless steel, with stripes. */
    private val jeepneys = intArrayOf(
        c(0xFFD32F2F), c(0xFFF2C230), c(0xFF1E5BC6), c(0xFFE74C3C), c(0xFFC9CED3), c(0xFFD32F2F), c(0xFFC9CED3), c(0xFF1E5BC6),
        c(0xFFF2C230), c(0xFF27AE60), c(0xFF2E9E4F), c(0xFFF2C230), c(0xFF8E2442), c(0xFFF5F5F5), c(0xFFDADFE3), c(0xFF8E44AD),
        c(0xFFF5F5F5), c(0xFFD32F2F),
    )
    /** Tricycles wear their drivers' association colors. */
    private val tricycles = intArrayOf(
        c(0xFF1E5BC6), c(0xFFF5F5F5), c(0xFFD32F2F), c(0xFFF2C230), c(0xFF27AE60), c(0xFFF5F5F5), c(0xFFF2C230), c(0xFF1E5BC6),
        c(0xFF8E44AD), c(0xFFF2F2F2), c(0xFFE67E22), c(0xFF2B2B2B),
    )
    private val buses = intArrayOf(c(0xFFF2F2F2), c(0xFF1F4FA0), c(0xFFF2F2F2), c(0xFFC0392B), c(0xFF1F4FA0), c(0xFFF2C230))
    /** Modern jeepneys: mostly white or cream, banded in their cooperative's color. */
    private val modernJeeps = intArrayOf(
        c(0xFFF5F5F5), c(0xFF0E8C8C), c(0xFFF5F5F5), c(0xFF1E5BC6), c(0xFFEDE6D3), c(0xFF2E9E4F), c(0xFFF5F5F5), c(0xFFE67E22),
        c(0xFFC9CED3), c(0xFFC0392B), c(0xFFF2F2F2), c(0xFF7D3C98), c(0xFF5FAF6A), c(0xFFF5F5F5),
    )
    /** Mopeds, and delivery riders with a box on the rack in its color (a second color of their own). */
    private val mopeds = intArrayOf(
        c(0xFFC0392B), c(0xFFC0392B), c(0xFF1E1E1E), c(0xFF1E1E1E), c(0xFFF2F2F2), c(0xFFF2F2F2), c(0xFF1E5BC6), c(0xFF1E5BC6),
        c(0xFF9AA0A6), c(0xFF9AA0A6), c(0xFF2B2B2B), c(0xFF2B2B2B), c(0xFF8E2442), c(0xFF8E2442),
        c(0xFF1E1E1E), c(0xFF19A55A), c(0xFFF2F2F2), c(0xFFD7307A), c(0xFF2B2B2B), c(0xFFF28C28),
    )
    override fun livery(style: Int) = when (style) { 4 -> jeepneys; 5 -> tricycles; 7 -> buses; 11 -> modernJeeps; 12 -> mopeds; else -> null }
    override val taxiEvery = 3
    override val taxiColors = intArrayOf(c(0xFFF4F4F2))
}
