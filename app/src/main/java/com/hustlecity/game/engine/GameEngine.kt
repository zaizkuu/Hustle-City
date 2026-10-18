package com.hustlecity.game.engine

import android.content.Context
import com.hustlecity.game.audio.Audio
import com.hustlecity.game.audio.Sfx
import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.PlaceKind
import com.hustlecity.game.model.CargoItem
import com.hustlecity.game.model.GameState
import com.hustlecity.game.model.MINUTES_PER_DAY
import com.hustlecity.game.model.Message
import com.hustlecity.game.model.OwnedVehicle
import com.hustlecity.game.model.PlacedItem
import com.hustlecity.game.model.PlayerState
import com.hustlecity.game.model.Rental
import com.hustlecity.game.model.Rival
import com.hustlecity.game.model.RivalBusiness
import com.hustlecity.game.model.SkipJob
import com.hustlecity.game.model.SkipKind
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.Building
import com.hustlecity.game.world.City
import com.hustlecity.game.world.Traffic
import com.hustlecity.game.world.dist
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class Input {
    var jx = 0f
    var jy = 0f
    var brake = false
}

class CameraRig {
    var tx = 0f
    var ty = 0f
    var dist = 70f
    var zoom = 70f
    var yaw = 0f
    var panX = 0f
    var panY = 0f
    var mapX = City.WIDTH / 2
    var mapY = City.HEIGHT / 2
    var mapDist = 900f
}

class GameEngine(context: Context?) {
    val settings = Settings(context)
    val ui = UiState()
    val saves = SaveManager(context)
    lateinit var state: GameState
    lateinit var city: City
    val traffic = Traffic()
    val economy = Economy(this)
    val camera = CameraRig()
    val input = Input()
    var interior: InteriorScene? = null
    var rnd: Random = Random(System.nanoTime())
    var hasGame = false
    /** A city exists only as the animated main-menu backdrop. */
    var menuMode = false
    val ready: Boolean get() = hasGame || menuMode

    /** 0 = paused, 1 = 1x, 2 = 2x, 3 = 4x. */
    var speedIndex = 1
    /** The fast-forward in progress, stored in the save so it survives the app closing. */
    val skip: SkipJob? get() = if (::state.isInitialized) state.skip else null
    private var minuteAcc = 0.0
    var realTime = 0f
    var summaryShownAt = -100f
    private var publishAcc = 0f
    var carSpeed = 0f
    var fps = 0f
    private var fpsAcc = 0f
    private var fpsFrames = 0
    var crashCooldown = 0f

    // interaction hints, refreshed each frame
    var nearDoor: Building? = null
    var nearVehicle: OwnedVehicle? = null
    var atGasStation = false

    private val tmp = FloatArray(2)
    private val moneyFmt: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)

    // ------------------------------------------------------------------ lifecycle

    fun newGame(name: String, skin: Int, shirt: Int, difficulty: Int, seed: Long = 1337L) {
        city = City(seed)
        val startMoney = when (difficulty) { 0 -> 20000.0; 1 -> 5000.0; else -> 1500.0 }
        val s = GameState(seed = seed, minute = 8L * 60)
        s.player = PlayerState(name = name.ifBlank { "Alex" }, skin = skin, shirt = shirt, money = startMoney, difficulty = difficulty)
        state = s
        rnd = Random(seed xor System.nanoTime())
        val homeRnd = Random(seed + 99)
        // home: small residential unit in the Garment District
        val home = city.buildings.filter { it.kind == BKind.RESIDENTIAL && it.neighborhood == 0 && !it.facesNorth && it.area in 30..70 }
            .ifEmpty { city.buildings.filter { it.kind == BKind.RESIDENTIAL } }
            .let { it[homeRnd.nextInt(it.size)] }
        s.rentals += Rental(home.id, (home.baseRent * .8 / 10).roundToInt() * 10.0, 1)
        s.player.home = home.id
        s.player.x = home.doorX
        s.player.y = home.doorY + 2f
        s.homeFurniture += PlacedItem("bed", 0, 0, 0)
        s.homeFurniture += PlacedItem("home_fridge", 3, 0, 0)
        // a used starter car parked on the street in front of home
        val spot = curbSpot(home, 4f)
        s.vehicles += OwnedVehicle(s.newId(), "tico", spot.first, spot.second, (Math.PI / 2).toFloat(), fuel = .6, condition = .75)
        // rivals and their businesses
        val rivalRnd = Random(seed + 7)
        for ((i, pair) in Catalog.rivalCompanies.withIndex()) {
            val cash = 150_000.0 + rivalRnd.nextDouble() * 900_000.0
            s.rivals += Rival(s.newId(), pair.first, pair.second, cash, cash)
            if (i == 0) Unit
        }
        val retail = city.buildings.filter { it.kind == BKind.RETAIL }.shuffled(rivalRnd)
        val rivalShare = (retail.size * .55).toInt()
        for (b in retail.take(rivalShare)) {
            val rival = s.rivals[rivalRnd.nextInt(s.rivals.size)]
            val type = if (rival.name == "Ironclad Fitness" && rivalRnd.nextBoolean()) Catalog.type("gym") else Catalog.businessTypes[rivalRnd.nextInt(Catalog.businessTypes.size)]
            s.rivalBusinesses += RivalBusiness(s.newId(), rival.id, b.id, type.id, Catalog.randomBusinessName(type.id, rivalRnd))
        }
        for (r in s.rivals) r.netWorth = r.cash + s.rivalBusinesses.count { it.rivalId == r.id } * 65000.0
        message("Uncle Sal", Objectives.groups[0].intro, notify = false)
        message("Uncle Sal", "Your old Tico Compact is parked outside your apartment at ${home.address}. Tap the phone apps to get around — Maps shows the whole city.", notify = false)
        camera.zoom = 70f
        camera.dist = 70f
        camera.yaw = 0f; camera.panX = 0f; camera.panY = 0f
        camera.tx = s.player.x; camera.ty = s.player.y
        interior = null
        minuteAcc = 0.0
        ui.skipping = false
        speedIndex = 1
        hasGame = true
        menuMode = false
        ui.toasts.clear()
        lastGroup = -1
        traffic.ensure(settings.carCount, settings.pedCount)
        traffic.update(0f, camera.tx, camera.ty, 220f, -9999f, -9999f, initial = true)
        ui.screen = Screen.GAME
        ui.app = null; ui.panel = null; ui.selected = -1; ui.summary = null; ui.designer = false; ui.phoneOpen = true
        save()
    }

    fun loadGame(): Boolean {
        val s = saves.load() ?: return false
        state = s
        city = City(s.seed)
        rnd = Random(System.nanoTime())
        interior = null
        minuteAcc = 0.0
        s.player.workingAt = if (s.skip?.kind == SkipKind.WORK) s.skip!!.target else -1
        ui.skipping = s.skip != null
        if (s.player.inBuilding >= 0) {
            val b = city.building(s.player.inBuilding)
            s.player.inBuilding = -1
            if (b != null) { s.player.x = b.doorX; s.player.y = b.doorY }
        }
        val focus = focusPoint()
        camera.tx = focus.first; camera.ty = focus.second
        camera.yaw = 0f; camera.panX = 0f; camera.panY = 0f; camera.zoom = 70f; camera.dist = 70f
        traffic.ensure(settings.carCount, settings.pedCount)
        traffic.update(0f, camera.tx, camera.ty, 220f, -9999f, -9999f, initial = true)
        hasGame = true
        menuMode = false
        speedIndex = 1
        lastGroup = Objectives.currentGroup(this)
        ui.toasts.clear()
        ui.screen = Screen.GAME
        ui.app = null; ui.panel = null; ui.selected = -1; ui.summary = null; ui.designer = false; ui.phoneOpen = true
        bump()
        return true
    }

    /** Builds a throwaway city to orbit behind the main menu. */
    fun prepareMenuBackground() {
        if (hasGame || menuMode) return
        city = City(1337L)
        state = GameState(seed = 1337L, minute = 18L * 60 + 40)
        val b = city.buildings.first { it.neighborhood == 4 && it.kind == BKind.OFFICE }
        state.player.x = b.doorX; state.player.y = b.doorY + 8f
        camera.tx = state.player.x; camera.ty = state.player.y
        camera.zoom = 150f; camera.dist = 150f; camera.yaw = .5f
        traffic.ensure(settings.carCount, settings.pedCount)
        traffic.update(0f, camera.tx, camera.ty, 230f, -9999f, -9999f, initial = true)
        menuMode = true
    }

    fun save(showToast: Boolean = false) {
        if (!hasGame) return
        val ok = saves.save(state)
        if (showToast) toast(if (ok) "Game saved" else "Saving failed", if (ok) ToastKind.SAVE else ToastKind.BAD)
    }

    // ------------------------------------------------------------------ helpers

    fun money(v: Double): String = moneyFmt.format(v)

    fun moneyShort(v: Double): String {
        val a = abs(v)
        val sign = if (v < 0) "-" else ""
        return when {
            a >= 1_000_000 -> "$sign$%.2fM".format(a / 1_000_000)
            a >= 10_000 -> "$sign$%.1fK".format(a / 1000)
            else -> "$sign$" + "%,.0f".format(a)
        }
    }

    fun bump() { ui.tick++ }

    fun toast(text: String, kind: ToastKind = ToastKind.INFO) {
        when (kind) {
            ToastKind.GOOD -> Audio.play(Sfx.CHIME)
            ToastKind.BAD -> Audio.play(Sfx.ERROR)
            else -> {}
        }
        ui.toasts += Toast(System.nanoTime(), text, kind, System.currentTimeMillis())
        while (ui.toasts.size > 4) ui.toasts.removeAt(0)
    }

    fun message(from: String, text: String, notify: Boolean = true) {
        state.messages += Message(state.day, (state.minute % MINUTES_PER_DAY).toInt(), from, text)
        if (state.messages.size > 120) state.messages.removeAt(0)
        if (notify) toast("✉ $from: ${text.take(70)}${if (text.length > 70) "…" else ""}")
    }

    fun spend(amount: Double, category: String, allowDebt: Boolean = false, silent: Boolean = false): Boolean {
        if (amount <= 0) return true
        val p = state.player
        if (!allowDebt && p.money < amount) {
            if (!silent) toast("Not enough money (${money(amount)} needed)", ToastKind.BAD)
            return false
        }
        p.money -= amount
        state.ledgerToday[category] = (state.ledgerToday[category] ?: 0.0) + amount
        bump()
        return true
    }

    fun earn(amount: Double, category: String) {
        if (amount <= 0) return
        state.player.money += amount
        state.ledgerToday["in:$category"] = (state.ledgerToday["in:$category"] ?: 0.0) + amount
        bump()
    }

    fun speedMultiplier(): Float = when (speedIndex) { 0 -> 0f; 1 -> 1f; 2 -> 2f; else -> 4f }

    fun menuOpen(): Boolean = ui.app != null && ui.app != AppId.MAPS || ui.panel != null

    fun simPaused(): Boolean = speedIndex == 0 || (settings.pauseInMenus && menuOpen() && skip == null)

    fun pendingEarnings(): Double = state.businesses.sumOf { it.revenueToday }

    fun isVacant(buildingId: Int): Boolean {
        val b = city.building(buildingId) ?: return false
        if (!b.rentable || b.placeId != null) return false
        return state.rentalFor(buildingId) == null && state.rivalBusinessAt(buildingId) == null
    }

    fun vehicleValue(v: OwnedVehicle) = v.def.price * .6 * (.4 + .6 * v.condition)

    fun netWorth(): Double {
        val s = state
        var nw = s.player.money
        nw += s.vehicles.sumOf { vehicleValue(it) }
        for (b in s.businesses) nw += BizCalc.furnitureValue(b) * .5 + BizCalc.stockValue(b) + b.revenueToday
        nw += s.rentals.filter { it.owned }.sumOf { city.building(it.buildingId)?.value ?: 0.0 }
        nw -= s.loans.sumOf { it.remaining }
        return nw
    }

    /** Player's rank among rivals by net worth (1 = richest). */
    fun rank(): Int {
        val mine = netWorth()
        return 1 + state.rivals.count { it.netWorth > mine }
    }

    fun daylight(): Float {
        val h = state.hour + state.minuteOfHour / 60f
        return when {
            h < 5f -> 0f
            h < 7.5f -> (h - 5f) / 2.5f
            h < 18f -> 1f
            h < 21f -> 1f - (h - 18f) / 3f
            else -> 0f
        }
    }

    fun focusPoint(): Pair<Float, Float> {
        val p = state.player
        val v = if (p.driving >= 0) state.vehicle(p.driving) else null
        return if (v != null) v.x to v.y else p.x to p.y
    }

    fun buildingName(b: Building): String {
        b.placeId?.let { return Catalog.place(it).name }
        state.businessAt(b.id)?.let { return it.name }
        state.rivalBusinessAt(b.id)?.let { return it.name }
        if (b.id == state.player.home) return "Home"
        return b.address
    }

    // ------------------------------------------------------------------ main loop

    fun frame(dtIn: Float) {
        if (!ready) return
        val dt = dtIn.coerceIn(0f, .05f)
        if (!hasGame || ui.screen != Screen.GAME) {
            // main menu: slowly orbit the city
            camera.yaw += dt * .05f
            traffic.update(dt, camera.tx, camera.ty, 230f, -9999f, -9999f)
            realTime += dt
            return
        }
        realTime += dt
        fpsAcc += dtIn; fpsFrames++
        if (fpsAcc >= .5f) { fps = fpsFrames / fpsAcc; fpsAcc = 0f; fpsFrames = 0 }
        if (crashCooldown > 0) crashCooldown -= dt

        val job = skip
        if (job != null) {
            val rate = ((job.end - job.start) / 2.0).coerceIn(60.0, 360.0)
            minuteAcc += rate * dt
            while (minuteAcc >= 1.0 && state.minute < job.end && skip === job) {
                minuteAcc -= 1.0
                simulateMinute()
            }
            if (state.minute >= job.end && skip === job) finishSkip(false)
            minuteAcc = minuteAcc.coerceAtMost(1.0)
        } else if (!simPaused()) {
            minuteAcc += speedMultiplier() * dt
            var guard = 0
            while (minuteAcc >= 1.0 && guard++ < 30) {
                minuteAcc -= 1.0
                simulateMinute()
            }
        }

        val inside = state.player.inBuilding >= 0
        val drivingNow = state.vehicle(state.player.driving)
        Audio.engineLevel = if (drivingNow != null && !inside && skip == null) (abs(carSpeed) / drivingNow.def.maxSpeed).coerceIn(0f, 1f) else -1f
        // the car and player hold still while a phone app, the map or a panel covers the controls
        if (!inside && skip == null && ui.app == null && ui.panel == null) updateMovement(dt)
        updateCamera(dt)
        val (fx, fy) = focusPoint()
        if (!inside) {
            val bx = if (state.player.driving >= 0) fx else state.player.x
            val by = if (state.player.driving >= 0) fy else state.player.y
            val parked = state.vehicles.size
            if (traffic.obstacles.size < parked * 2) traffic.obstacles = FloatArray(parked * 2 + 8)
            var n = 0
            for (v in state.vehicles) if (v.id != state.player.driving) { traffic.obstacles[n * 2] = v.x; traffic.obstacles[n * 2 + 1] = v.y; n++ }
            traffic.obstacleCount = n
            traffic.update(dt, camera.tx, camera.ty, 230f, bx, by)
        }
        interior?.update(dt, !simPaused() || skip != null)
        refreshHints()

        publishAcc += dt
        if (publishAcc > .25f) {
            publishAcc = 0f
            bump()
            val now = System.currentTimeMillis()
            ui.toasts.removeAll { now - it.bornMs > 4500 }
            if (ui.summary != null && realTime - summaryShownAt > 7f) ui.summary = null
        }
    }

    private fun refreshHints() {
        val p = state.player
        if (p.inBuilding >= 0) { nearDoor = null; nearVehicle = null; atGasStation = false; return }
        if (p.driving >= 0) {
            nearDoor = null; nearVehicle = null
            val v = state.vehicle(p.driving)
            atGasStation = v != null && city.buildings.any { it.kind == BKind.GAS && it.contains(v.x, v.y) }
        } else {
            nearDoor = city.nearestDoor(p.x, p.y, 7f)
            nearVehicle = state.vehicles.firstOrNull { dist(it.x, it.y, p.x, p.y) < 6f }
            atGasStation = false
        }
    }

    fun simulateMinute() {
        val s = state
        s.minute++
        needsMinute()
        economy.minute()
        if (s.minute % MINUTES_PER_DAY == 0L) {
            economy.midnight()
            checkObjectives()
            save()
            toast("Day ${s.day - 1}, 23:59 — Auto saving…", ToastKind.SAVE)
        } else if (s.minute % 30 == 0L) {
            checkObjectives()
        }
    }

    private fun needsMinute() {
        val p = state.player
        val sleeping = skip?.kind == SkipKind.SLEEP || skip?.kind == SkipKind.HOSPITAL
        val working = p.workingAt >= 0 || skip?.kind == SkipKind.SIDE_JOB || skip?.kind == SkipKind.WORK
        if (sleeping) {
            p.energy = (p.energy + 100.0 / (7.5 * 60)).coerceAtMost(100.0)
            p.hunger = (p.hunger - 100.0 / (28 * 60)).coerceAtLeast(0.0)
        } else {
            var drain = 100.0 / (18 * 60)
            if (working) drain *= 1.35
            if (p.happiness < 25) drain *= 1.2
            if (p.hunger <= 0) drain *= 1.6
            p.energy = (p.energy - drain).coerceAtLeast(0.0)
            p.hunger = (p.hunger - 100.0 / (14 * 60)).coerceAtLeast(0.0)
            if (p.energy <= 0.0 && skip == null) passOut()
        }
    }

    fun happinessHourly() {
        val p = state.player
        var target = 48.0
        val home = city.building(p.home)
        if (home != null) target += (home.area / 8.0).coerceAtMost(12.0) + City.neighborhoods[home.neighborhood].wealth * 4
        target += state.vehicles.maxOfOrNull { it.def.happiness }?.toDouble() ?: -4.0
        target += state.homeFurniture.sumOf { Catalog.item(it.itemId)?.decor ?: 0 } * 1.5
        if (p.hunger < 20) target -= 20
        if (p.energy < 15) target -= 12
        if (p.money < 0) target -= 15
        if (p.workingAt >= 0) target -= 4
        target = target.coerceIn(5.0, 100.0)
        p.happiness += (target - p.happiness) * .08
    }

    private fun passOut() {
        val hospital = city.placeBuilding("hospital") ?: return
        exitBuildingInternal()
        val p = state.player
        p.driving = -1
        p.workingAt = -1
        p.x = hospital.doorX; p.y = hospital.doorY + 1.5f
        carSpeed = 0f
        spend(400.0, "Medical", allowDebt = true, silent = true)
        p.happiness = (p.happiness - 15).coerceAtLeast(0.0)
        message("City Hospital", "You collapsed from exhaustion and were brought to City Hospital. Bill: \$400. Remember to sleep!")
        startSkip(SkipKind.HOSPITAL, 6 * 60, "Recovering at City Hospital…", cancellable = false)
    }

    // ------------------------------------------------------------------ skipping time

    fun startSkip(
        kind: SkipKind, minutes: Int, title: String, cancellable: Boolean = true,
        target: Int = -1, payload: String = "", amount: Double = 0.0,
    ) {
        val job = SkipJob(kind, state.minute, state.minute + minutes.coerceAtLeast(0), title, cancellable, target, payload, amount)
        if (minutes <= 0) { completeSkip(job, false); return }
        if (state.skip != null) finishSkip(true, force = true)
        state.skip = job
        minuteAcc = 0.0
        ui.skipping = true
        bump()
    }

    fun cancelSkip() {
        val job = skip ?: return
        if (!job.cancellable) return
        finishSkip(true)
    }

    private fun finishSkip(cancelled: Boolean, force: Boolean = false) {
        val job = state.skip ?: return
        if (cancelled && !job.cancellable && !force) return
        state.skip = null
        ui.skipping = false
        minuteAcc = 0.0
        completeSkip(job, cancelled)
        bump()
    }

    /** Applies the result of a finished (or cancelled) skip. */
    private fun completeSkip(job: SkipJob, cancelled: Boolean) {
        val p = state.player
        val elapsedHours = (state.minute - job.start) / 60.0
        when (job.kind) {
            SkipKind.SLEEP -> toast("Good morning! Energy ${p.energy.roundToInt()}%")
            SkipKind.HOSPITAL -> p.energy = max(p.energy, 35.0)
            SkipKind.COURSE -> {
                val course = Catalog.courses.firstOrNull { it.id == job.payload } ?: return
                p.courses += course.id
                message("Hustle Business School", "Congratulations on completing ${course.name}! ${course.description}")
                toast("Course completed: ${course.name}", ToastKind.GOOD)
                checkObjectives()
            }
            SkipKind.WORK -> if (p.workingAt == job.target) p.workingAt = -1
            SkipKind.SIDE_JOB -> {
                val pay = elapsedHours * job.amount
                earn(pay, "Side jobs")
                toast("Shift done: earned ${money(pay)}", ToastKind.GOOD)
            }
            SkipKind.TAXI -> {
                val b = city.building(job.target) ?: return
                p.x = b.doorX
                p.y = b.doorY + if (b.facesNorth) -1.5f else 1.5f
                camera.tx = p.x; camera.ty = p.y
                camera.panX = 0f; camera.panY = 0f
                traffic.update(0f, camera.tx, camera.ty, 230f, -9999f, -9999f, initial = true)
            }
            SkipKind.CLEAN -> {
                val biz = state.business(job.target) ?: return
                if (!cancelled) biz.cleanliness = (biz.cleanliness + 50).coerceAtMost(100.0)
                toast("Cleanliness ${biz.cleanliness.roundToInt()}%")
            }
            SkipKind.REST -> if (!cancelled) {
                p.energy = (p.energy + 25).coerceAtMost(100.0)
                p.happiness = (p.happiness + 3).coerceAtMost(100.0)
            }
            SkipKind.WORKOUT -> if (!cancelled) {
                p.happiness = (p.happiness + 8).coerceAtMost(100.0)
                p.energy = (p.energy - 8).coerceAtLeast(1.0)
            }
            SkipKind.WAIT -> {}
        }
    }

    // ------------------------------------------------------------------ movement

    private fun updateMovement(dt: Float) {
        val p = state.player
        val yaw = camera.yaw
        val rx = cos(yaw); val ry = -sin(yaw)
        val fx = -sin(yaw); val fy = -cos(yaw)
        val wx = rx * input.jx + fx * -input.jy
        val wy = ry * input.jx + fy * -input.jy
        val mag = hypot(input.jx, input.jy).coerceAtMost(1f)
        if (p.driving >= 0) {
            val v = state.vehicle(p.driving) ?: run { p.driving = -1; return }
            drive(v, wx, wy, mag, dt)
            p.x = v.x; p.y = v.y
        } else {
            if (mag > .08f) {
                val speed = 6.5f * mag
                val nx = p.x + wx / mag * speed * dt
                val ny = p.y + wy / mag * speed * dt
                city.collide(nx, ny, .45f, tmp)
                var px = tmp[0]; var py = tmp[1]
                if (traffic.bump(px, py, .45f, tmp)) { px = tmp[0]; py = tmp[1] }
                p.x = px; p.y = py
                p.heading = atan2(wx, -wy)
            }
        }
    }

    private fun wrap(a: Float): Float {
        var x = a
        while (x > Math.PI) x -= (2 * Math.PI).toFloat()
        while (x < -Math.PI) x += (2 * Math.PI).toFloat()
        return x
    }

    private fun drive(v: OwnedVehicle, wx: Float, wy: Float, mag: Float, dt: Float) {
        val def = v.def
        var throttle = 0f
        var steer = 0f
        if (v.fuel <= 0.0) {
            if (crashCooldown <= 0f && mag > .2f) { toast("Out of fuel! Call roadside assistance from the vehicle panel.", ToastKind.BAD); crashCooldown = 4f }
        } else if (mag > .15f) {
            val desired = atan2(wx, -wy)
            val diff = wrap(desired - v.heading)
            if (abs(diff) < 1.9f) {
                throttle = mag
                steer = diff
            } else if (carSpeed < 1.5f) {
                throttle = -mag * .7f
                steer = -wrap(desired - (v.heading + Math.PI.toFloat()))
            } else {
                carSpeed -= 16f * dt
            }
        }
        if (input.brake) {
            if (carSpeed > .4f) { carSpeed = max(0f, carSpeed - 18f * dt); throttle = 0f }
            else if (v.fuel > 0.0) { throttle = -.8f; steer = 0f }
            else throttle = 0f
        }
        val maxSpeed = def.maxSpeed * (.75f + .25f * v.condition.toFloat())
        if (throttle != 0f) {
            carSpeed += throttle * def.accel * dt
        } else {
            val drag = 4f * dt
            carSpeed = if (carSpeed > 0) max(0f, carSpeed - drag) else min(0f, carSpeed + drag)
        }
        carSpeed = carSpeed.coerceIn(-6f, maxSpeed)
        val turnRate = 2.3f * min(1f, abs(carSpeed) / 5f) * dt
        v.heading = wrap(v.heading + steer.coerceIn(-turnRate, turnRate) * (if (carSpeed >= 0) 1f else -1f).let { if (throttle < 0) 1f else it })
        val ox = v.x; val oy = v.y
        val nx = v.x + sin(v.heading) * carSpeed * dt
        val ny = v.y - cos(v.heading) * carSpeed * dt
        // two collision circles along the body
        val off = def.length * .28f
        val r = def.width * .55f
        val hx = sin(v.heading) * off; val hy = -cos(v.heading) * off
        var cx = nx; var cy = ny
        var hit = false
        for (k in 0 until 2) {
            val sx = if (k == 0) hx else -hx
            val sy = if (k == 0) hy else -hy
            if (city.collide(cx + sx, cy + sy, r, tmp)) { cx += tmp[0] - (cx + sx); cy += tmp[1] - (cy + sy); hit = true }
            if (traffic.bump(cx + sx, cy + sy, r, tmp)) { cx += tmp[0] - (cx + sx); cy += tmp[1] - (cy + sy); hit = true }
        }
        v.x = cx; v.y = cy
        if (hit) {
            val impact = abs(carSpeed)
            carSpeed *= -.25f
            if (impact > 7f) {
                v.condition = (v.condition - impact * .004).coerceAtLeast(.05)
                if (crashCooldown <= 0f) { toast("Crash! Vehicle condition ${(v.condition * 100).roundToInt()}%", ToastKind.BAD); crashCooldown = 2f }
            }
        }
        v.fuel = (v.fuel - dist(ox, oy, v.x, v.y) / 30000.0).coerceAtLeast(0.0)
    }

    private fun updateCamera(dt: Float) {
        val k = 1f - exp(-dt * 6f)
        val (fx, fy) = focusPoint()
        val moving = hypot(input.jx, input.jy) > .1f || abs(carSpeed) > 1f
        if (moving) {
            val decay = exp(-dt * 2.5f)
            camera.panX *= decay; camera.panY *= decay
        }
        camera.tx += (fx + camera.panX - camera.tx) * k
        camera.ty += (fy + camera.panY - camera.ty) * k
        val target = camera.zoom * (1f + abs(carSpeed) / 40f)
        camera.dist += (target - camera.dist) * (1f - exp(-dt * 2.5f))
    }

    // ------------------------------------------------------------------ vehicles

    fun resetInput() {
        input.jx = 0f; input.jy = 0f; input.brake = false
    }

    fun enterVehicle(v: OwnedVehicle) {
        val p = state.player
        if (p.inBuilding >= 0) return
        resetInput()
        Audio.play(Sfx.CAR_DOOR)
        p.driving = v.id
        p.x = v.x; p.y = v.y
        carSpeed = 0f
        // move hand-carried goods into the trunk
        val free = v.def.cargo - v.cargo.size
        if (p.hand.isNotEmpty() && free > 0) {
            val n = minOf(free, p.hand.size)
            v.cargo += p.hand.take(n)
            repeat(n) { p.hand.removeAt(0) }
        }
        bump()
    }

    fun exitVehicle() {
        val p = state.player
        val v = state.vehicle(p.driving) ?: return
        p.driving = -1
        carSpeed = 0f
        resetInput()
        Audio.play(Sfx.CAR_DOOR)
        // step out on the driver's side, avoiding buildings
        val sx = v.x + cos(v.heading) * -2.2f
        val sy = v.y + sin(v.heading) * -2.2f
        city.collide(sx, sy, .45f, tmp)
        p.x = tmp[0]; p.y = tmp[1]
        bump()
    }

    /** Curbside parking spot in front of a building, clear of the traffic lanes. */
    fun curbSpot(b: Building, along: Float): Pair<Float, Float> {
        val streetY = City.streetY(b.streetIndex)
        val y = streetY + if (b.facesNorth) City.ST_HALF - 1.1f else -(City.ST_HALF - 1.1f)
        return (b.doorX + along) to y
    }

    /** Makes [b] the player's home; furniture that doesn't fit the new place goes to storage. */
    fun moveHomeTo(b: Building) {
        state.player.home = b.id
        val w = b.gridW.coerceAtLeast(7); val d = b.gridD.coerceAtLeast(6)
        val keep = state.homeFurniture.filter { p ->
            val def = Catalog.item(p.itemId) ?: return@filter false
            val (fw, fd) = itemDims(def, p.rot)
            p.x + fw <= w && p.y + fd <= d
        }
        state.homeStorage += state.homeFurniture.filter { it !in keep }.map { it.itemId }
        state.homeFurniture.retainAll(keep.toSet())
    }

    fun nearbyVehicle(x: Float, y: Float, radius: Float): OwnedVehicle? {
        val p = state.player
        if (p.driving >= 0) return state.vehicle(p.driving)
        return state.vehicles.filter { dist(it.x, it.y, x, y) < radius }.minByOrNull { dist(it.x, it.y, x, y) }
    }

    // ------------------------------------------------------------------ buildings

    fun enterBuilding(b: Building) {
        val s = state
        val p = s.player
        if (p.driving >= 0) { toast("Park your vehicle first"); return }
        ui.selected = -1
        if (b.kind == BKind.GAS) { ui.panel = Panel.Place(b.id, b.placeId ?: "fuel"); bump(); return }
        val mode = when {
            b.id == p.home -> InteriorMode.HOME
            s.businessAt(b.id) != null -> InteriorMode.BUSINESS
            s.rentalFor(b.id) != null && b.kind == BKind.RETAIL -> InteriorMode.EMPTY
            s.rentalFor(b.id) != null && b.kind == BKind.RESIDENTIAL -> InteriorMode.HOME
            b.placeId != null -> InteriorMode.PLACE
            s.rivalBusinessAt(b.id) != null -> InteriorMode.RIVAL
            else -> null
        }
        if (mode == null) {
            if (b.rentable && isVacant(b.id)) { ui.panel = Panel.Listing(b.id) }
            else toast("${b.address} is private property")
            bump()
            return
        }
        if (mode == InteriorMode.HOME && b.id != p.home) {
            // a second residential rental: make it the new home
            moveHomeTo(b)
            toast("You moved into ${b.address}")
        }
        p.inBuilding = b.id
        p.x = b.doorX; p.y = b.doorY
        Audio.play(Sfx.DOOR)
        interior = InteriorScene(this, b, mode)
        ui.designer = false
        ui.phoneOpen = false
        resetInput()
        when (mode) {
            InteriorMode.EMPTY -> ui.panel = Panel.StartBusiness(b.id)
            InteriorMode.PLACE -> ui.panel = Panel.Place(b.id, b.placeId!!)
            InteriorMode.RIVAL -> ui.panel = Panel.RivalShop(b.id)
            else -> {}
        }
        bump()
    }

    private fun exitBuildingInternal() {
        val p = state.player
        resetInput()
        p.workingAt = -1
        p.inBuilding = -1
        interior = null
        ui.designer = false
        if (ui.panel !is Panel.Settings && ui.panel !is Panel.Help) ui.panel = null
    }

    fun exitBuilding() {
        val p = state.player
        val b = city.building(p.inBuilding)
        Audio.play(Sfx.DOOR)
        exitBuildingInternal()
        if (b != null) { p.x = b.doorX; p.y = b.doorY + if (b.facesNorth) -1f else 1f }
        bump()
    }

    // ------------------------------------------------------------------ picking

    /** Returns the id of the building under a screen point, using the given projector. */
    fun pickBuilding(proj: com.hustlecity.game.render.Projector, sx: Float, sy: Float): Int {
        val dir = FloatArray(3)
        proj.rayDir(sx, sy, dir)
        val ox = proj.camX; val oy = proj.camY; val oz = proj.camZ
        val bbox = FloatArray(4)
        proj.visibleGround(bbox, 60f)
        var best = -1
        var bestT = Float.MAX_VALUE
        for (b in city.buildings) {
            if (b.maxX < bbox[0] || b.minX > bbox[2] || b.maxY < bbox[1] || b.minY > bbox[3]) continue
            val h = max(b.height, .4f)
            val t = rayBox(ox, oy, oz, dir[0], dir[1], dir[2], b.minX, b.minY, 0f, b.maxX, b.maxY, h)
            if (t >= 0 && t < bestT) { bestT = t; best = b.id }
        }
        return best
    }

    private fun rayBox(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float): Float {
        var tmin = -Float.MAX_VALUE
        var tmax = Float.MAX_VALUE
        // slab test per axis, without allocations (this runs per building per frame)
        if (abs(dx) < 1e-6f) { if (ox < x0 || ox > x1) return -1f } else {
            var t1 = (x0 - ox) / dx; var t2 = (x1 - ox) / dx
            if (t1 > t2) { val t = t1; t1 = t2; t2 = t }
            tmin = max(tmin, t1); tmax = min(tmax, t2)
            if (tmin > tmax) return -1f
        }
        if (abs(dy) < 1e-6f) { if (oy < y0 || oy > y1) return -1f } else {
            var t1 = (y0 - oy) / dy; var t2 = (y1 - oy) / dy
            if (t1 > t2) { val t = t1; t1 = t2; t2 = t }
            tmin = max(tmin, t1); tmax = min(tmax, t2)
            if (tmin > tmax) return -1f
        }
        if (abs(dz) < 1e-6f) { if (oz < z0 || oz > z1) return -1f } else {
            var t1 = (z0 - oz) / dz; var t2 = (z1 - oz) / dz
            if (t1 > t2) { val t = t1; t1 = t2; t2 = t }
            tmin = max(tmin, t1); tmax = min(tmax, t2)
            if (tmin > tmax) return -1f
        }
        return if (tmax < 0) -1f else max(tmin, 0f)
    }

    /** True when the segment from the camera to a point passes through the building box. */
    fun occludes(proj: com.hustlecity.game.render.Projector, b: Building, px: Float, py: Float, pz: Float): Boolean {
        val dx = px - proj.camX; val dy = py - proj.camY; val dz = pz - proj.camZ
        val t = rayBox(proj.camX, proj.camY, proj.camZ, dx, dy, dz, b.minX, b.minY, 0f, b.maxX, b.maxY, b.height)
        return t in 0f..0.98f
    }

    // ------------------------------------------------------------------ objectives

    private var lastGroup = -1

    fun checkObjectives() {
        if (!hasGame) return
        Objectives.latch(this)
        val g = Objectives.currentGroup(this)
        if (lastGroup in 0 until g) {
            val group = Objectives.groups[g]
            message(group.title, group.intro)
        }
        lastGroup = g
    }

    fun objectiveTarget(target: String?): Int? {
        return when (target) {
            null -> null
            "biz" -> state.businesses.firstOrNull()?.buildingId ?: state.rentals.firstOrNull { city.building(it.buildingId)?.kind == BKind.RETAIL }?.buildingId
            "realty" -> { ui.app = AppId.REALTY; null }
            "furniture" -> {
                // the store that sells the first piece this business is still missing
                val biz = state.businesses.firstOrNull()
                val tag = biz?.type?.requirements?.firstOrNull { BizCalc.count(biz, it.tag) == 0 }?.tag
                val item = tag?.let { itemForTag(it) }
                val place = item?.let { id -> Catalog.places.firstOrNull { it.kind == PlaceKind.FURNITURE_STORE && id in it.stock } }
                city.placeBuilding(place?.id ?: "essentials")?.id
            }
            "stock" -> {
                val source = state.businesses.firstOrNull()?.type?.products?.mapNotNull { Catalog.product(it.first) }?.firstOrNull { !it.service }?.source
                city.placeBuilding(source?.let { Catalog.sourcePlaceId(it) } ?: "metro")?.id
            }
            else -> city.placeBuilding(target)?.id
        }
    }

    fun setWaypoint(buildingId: Int) {
        state.player.waypoint = if (state.player.waypoint == buildingId) -1 else buildingId
        bump()
    }

    // ------------------------------------------------------------------ cargo

    fun cargoSlotsFree(v: OwnedVehicle) = v.def.cargo - v.cargo.size

    fun placeKind(b: Building): PlaceKind? = b.placeId?.let { Catalog.place(it).kind }

    fun handOrTrunk(): List<CargoItem> {
        val p = state.player
        val list = ArrayList<CargoItem>(p.hand)
        return list
    }
}
