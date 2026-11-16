"""The trailer, shot by shot. Needs kit.py, people.py, rig.py, trailer/set.py (HC_TR_NOBUILD set, the set already
built), trailer/light.py and trailer/cast.py in front.

"Every empire starts with one door": someone arrives before dawn with a box under their arm, opens one shop, then
the block, then the cities; night falls on a skyline with their sign on it, and at the next dawn somebody new walks
up with a box of their own.

The cut runs at 120 beats a minute and 24 frames a second, so a beat is 12 frames and every cut lands on one.
"""

HERO = dict(shirt=0xF2994A, pants=0x1F3A5F, skin=0xC68B59, hair=0x2B1D14, style=0, tee=True)
HERO_APRON = dict(HERO, apron=True, tee=False, shirt=0xF7F0E0, accent=0xF2994A)
HERO_BOSS = dict(shirt=0xF4F4F4, pants=0x22252A, skin=0xC68B59, hair=0x2B1D14, style=0, tie=True, accent=0xF2994A)
ROOKIE = dict(shirt=0x27AE60, pants=0x34383F, skin=0xE8B48A, hair=0x4A3220, style=5, female=True, tee=True)

SHIRTS = (0xEB5757, 0x2F80ED, 0x27AE60, 0xF2C94C, 0x9B51E0, 0xE9EDF2, 0x56CCF2, 0x34383F, 0xF28AB2, 0x6FCF97, 0xD9B26A)
PANTS = (0x34383F, 0x1F3A5F, 0x4A3B2A, 0x5B6770, 0x2E4A3A, 0x22252A, 0x6B4F3A)
SKINS = (0xF1C9A5, 0xE0AC69, 0xC68B59, 0x8D5524, 0xE8B48A, 0xFFDBAC)
HAIRS = (0x2B1D14, 0x4A3220, 0x0F0F10, 0x8A5A2B, 0xD9B26A, 0x7A7A7A, 0xA0522D)
CAR_COLORS = (0x2F80ED, 0xC0392B, 0xE9EDF2, 0x2E3238, 0x27AE60, 0x9AA3AD, 0x1F3A5F, 0xF2994A, 0x8E3B4E)

SIGN_WHITE = M(0xFFF6E0, "PAINT")
NEON_ORANGE = M(0xFFB060, "EMIT", emit=1.0)
lamp_lights_up(NEON_ORANGE, 7.0, .7)
RENT_RED = M(0xC0392B, "PAINT")
RENT_BOARD = M(0xF5F2EA, "PAPER")


def anyone(k):
    """A passer-by, different for every k and the same every time."""
    r = random.Random(1000 + k)
    f = r.random() < .5
    return dict(shirt=r.choice(SHIRTS), pants=r.choice(PANTS), skin=r.choice(SKINS), hair=r.choice(HAIRS), female=f, tee=r.random() < .5,
                style=r.choice((1, 2, 5, 3)) if f else r.choice((0, 4, 0, 3)), skirt=f and r.random() < .3, tie=(not f) and r.random() < .25,
                beard=(not f) and r.random() < .25, accent=r.choice((0x1F4E9C, 0xB03A2E, 0x2E3238)), s=r.uniform(.94, 1.05))


def stroller(k, x, y, dx, f0, f1, speed=1.4, clip="walk", look=None):
    """Somebody walking along the avenue's pavement (or across it, dx 0) for the length of a shot."""
    secs = (f1 - f0) / FPS_TR
    look = look or anyone(k)
    if isinstance(dx, tuple):
        tx, ty = dx
    else:
        tx, ty = dx, 0
    d = math.hypot(tx, ty)
    ux, uy = tx / d, ty / d
    dist = speed * secs
    return person("walk{:d}".format(k), look, x, y, clips=[(clip, f0 - 4, f1 + 4, speed / 1.4, (k * .37) % 1.0)],
                  path=[(f0, x, y), (f1, x + ux * dist, y + uy * dist)], f0=f0, f1=f1)


def cruiser(model, lane_y, x_at_start, f0, f1, speed, color=None, k=0):
    """A car driving the avenue for a shot: eastbound on the south half, westbound on the north."""
    d = 1 if lane_y < 0 else -1
    secs = (f1 - f0) / FPS_TR
    return vehicle(model, x_at_start, lane_y, color=color, path=[(f0, x_at_start, lane_y), (f1, x_at_start + d * speed * secs, lane_y)], f0=f0, f1=f1)


def floor_at(x, y, z_from=400.0):
    scn = tr_scene()
    dg = scn.view_layers[0].depsgraph
    dg.update()
    hit, loc, nrm, idx, ob, mat = scn.ray_cast(dg, (x, y, z_from), (0, 0, -1))
    return loc.z if hit else 0.0


def first_cup(x, y, z, size=.62, rz=0.0, f0=None, f1=None, text="FIRST CUP"):
    ob = lettering(text, x, y, z, size, NEON_ORANGE, rz=rz, depth=.05, spacing=1.08)
    if f0 is not None:
        scope(ob, f0, f1)
    return ob


def for_rent(x, y, side, f0, f1):
    """A board in the window of an empty shop; side +1 for a shop on the north pavement."""
    yb = y - side * .22
    board = slab("for_rent", x - 1.7, min(yb, yb - side * .05), 1.55, x + 1.7, max(yb, yb - side * .05), 2.55, RENT_BOARD)
    t1 = lettering("FOR RENT", x, yb - side * .06, 2.2, .46, RENT_RED, rz=0 if side > 0 else 180, depth=.01)
    t2 = lettering("YOUR NAME HERE", x, yb - side * .06, 1.78, .2, M(0x2E3238, "PAINT"), rz=0 if side > 0 else 180, depth=.01, spacing=1.15)
    for o in (board, t1, t2):
        scope(o, f0, f1)


def parked_cars():
    """Cars at the kerb in the parking lanes, there all day: clear of the crossings, the bus stops and each other."""
    r = random.Random(4)
    spots = []
    for side in (1, -1):
        x = AVE_X0 + 30
        while x < AVE_X1 - 20:
            near_cross = any(abs(x - c) < 19 for c in CROSS)
            bus = (side > 0 and abs(x - 52) < 9) or (side < 0 and abs(x + 40) < 9)
            if not near_cross and not bus and r.random() < .55:
                spots.append((x, side))
            x += 6.4 + r.random() * 1.2
    for k, (x, side) in enumerate(spots):
        m = r.choice(("sedan", "sedan", "suv", "pickup", "van", "sports_car", "beater", "taxi"))
        vehicle(m, x, side * 5.45, rz=90 if side < 0 else -90, color=None if m in ("taxi", "beater") else r.choice(CAR_COLORS))
    return len(spots)


def build_main_shots():
    scn = tr_scene()
    row = list(scn["tr_hero_row"])
    shop_x, shop_y = row[0], row[1]
    front = FRONT                      # the hero shop's wall line (north pavement)
    W = WALK_Y
    parked_cars()

    # ---------------------------------------------------------------- 1  before dawn: someone with a box
    f0, f1 = T(3.0), T(8.0) - 1
    hero = person("hero_dawn", HERO, -24, W, clips=[("carry", f0 - 4, T(14.2), 1.0), ("carry_idle", T(14.2), T(16.5))],
                  path=[(f0, -24, W), (T(11.0) - 1, -24 + 1.4 * 8.0, W), (T(11.0), 9.4, W), (T(14.2), 9.4 + 1.4 * 3.2, W), (T(14.6), 9.4 + 1.4 * 3.2, W + .25)],
                  f0=f0, f1=T(16.5) - 1)
    hold_thing(hero, "cardboard_box", "torso", (0, -.4, .24), s=1.1)
    scope(hero, f0, T(16.5) - 1)
    shot("01_arrival", f0, f1, "blue", [(0, (-34.5, W - .7, 1.35), (10, W - 1.6, 4.5)), (f1 - f0, (-31.2, W - .7, 1.5), (10, W - 1.6, 5.2))], lens=30)

    # ---------------------------------------------------------------- 2  the walk, close on the shoes
    f0, f1 = T(8.0), T(11.0) - 1
    hx0 = -24 + 1.4 * 5.0
    hx1 = hx0 + 1.4 * 3.0
    shot("02_steps", f0, f1, "blue", [(0, (hx0 + 1.9, W - 1.25, .3), (hx0 + .55, W, .36)), (f1 - f0, (hx1 + 1.7, W - 1.25, .33), (hx1 + .55, W, .36))], lens=38, fstop=4.0)

    # ---------------------------------------------------------------- 3  the empty shop, as the sun comes up
    f0, f1 = T(11.0), T(16.5) - 1
    for_rent(shop_x - .6, front, 1, 1, f1)
    shot("03_the_shop", f0, f1, "blue", [(0, (8.6, 6.4, 1.2), (shop_x - .3, front, 2.5)), (f1 - f0, (9.6, 3.6, 2.6), (shop_x + 3.5, front + 14, 15.5))],
         lens=26, ease=True, look_keys=[(40, "blue"), (f1 - f0, "dawn")])

    # ---------------------------------------------------------------- 4  open for business
    f0, f1 = T(16.5), T(19.5) - 1
    first_cup(shop_x - .45, front - .2, 4.1, f0=f0, f1=T(58.5))
    person("hero_door", HERO_APRON, shop_x + 3.3, front - 1.0, rz=-20, clips=[("wave", f0 - 6, f1 + 6, 1.0, .3)], f0=f0, f1=f1)
    stroller(1, 4.5, W - .3, 1, f0, f1)
    stroller(2, 23, W + .3, -1, f0, f1, speed=1.3)
    stroller(3, 27.5, W - .5, -1, f0, f1, speed=1.5)
    person("cust_in", anyone(4), shop_x + 1.6, W, clips=[("walk", f0 - 4, f1 + 4, 1.0)],
           path=[(f0, shop_x - 1.2, W - .2), (f0 + 40, shop_x + 1.3, front - .9), (f1, shop_x + 1.7, front + .9)], f0=f0, f1=f1)
    person("cust_talk", anyone(5), shop_x - 3.4, front - 1.1, rz=140, clips=[("talk", f0 - 6, f1 + 6, 1.0, .2)], f0=f0, f1=f1)
    person("cust_talk2", anyone(6), shop_x - 4.3, front - 1.9, rz=-55, clips=[("idle", f0 - 6, f1 + 6, 1.0, .5)], f0=f0, f1=f1)
    cruiser("taxi", 1.75, 44, f0, f1, 9.5)
    cruiser("kei_car", -1.75, -22, f0, f1, 10, color=0x56CCF2)
    petals(2, 5, 30, 13, f0, f1, n=220)
    shot("04_open", f0, f1, "day", [(0, (6.2, 3.6, 1.45), (shop_x + .5, front, 2.5)), (f1 - f0, (10.4, 4.4, 1.6), (shop_x + 1.2, front, 2.6))], lens=30)

    # ---------------------------------------------------------------- 6  the avenue at full tilt
    f0, f1 = T(22.0), T(24.5) - 1
    for k, (x, y, d) in enumerate(((6.2, -6.5, 1), (7.4, -3.0, 1), (6.6, 1.2, 1), (7.6, 4.2, -1), (6.0, 6.4, -1), (-6.4, -5.0, 1), (-7.5, 0.5, -1), (-6.2, 5.2, -1),
                                   (7.0, -8.6, 1))):
        stroller(20 + k, x, y, (0, d), f0, f1, speed=1.35 + (k % 3) * .1)
    for k, (m, x, y, c) in enumerate((("taxi", -13.2, -1.75, None), ("bus", -17.4, -5.25, None), ("sedan", -20.2, -1.75, 0xC0392B), ("suv", -27, -1.75, 0x2E3238))):
        vehicle(m, x, y, rz=90, color=c, f0=f0, f1=f1)
    for k, (m, x, y, c) in enumerate((("jeepney", 13.6, 1.75, None), ("sedan", 13.2, 5.25, 0x9AA3AD), ("van", 20.5, 1.75, None))):
        vehicle(m, x, y, rz=-90, color=c, f0=f0, f1=f1)
    vehicle("box_truck", 2.5, -34, path=[(f0, 2.5, -34), (f1, 2.5, -34 + 11 * 2.5)], f0=f0, f1=f1)
    vehicle("moped", 2.3, -50, path=[(f0, 2.3, -50), (f1, 2.3, -50 + 11 * 2.5)], f0=f0, f1=f1)
    vehicle("sports_car", -2.5, 46, color=0xD0392B, path=[(f0, -2.5, 46), (f1, -2.5, 46 - 12 * 2.5)], f0=f0, f1=f1)
    for k in range(4):
        stroller(30 + k, -30 + k * 9, (W + .3) * (1 if k % 2 else -1), 1 if k % 2 else -1, f0, f1)
    shot("06_avenue", f0, f1, "day", [(0, (-52, -3.2, 2.5), (2, -.5, 1.3)), (f1 - f0, (-49.5, -3.2, 2.6), (2, -.5, 1.3))], lens=80, fstop=5.6, focus=56)

    # ---------------------------------------------------------------- 7  one block: the name on every door
    f0, f1 = T(24.5), T(27.5) - 1
    shop2_x = row[10]
    first_cup(shop2_x - .45, front - .2, 4.1, f0=f0, f1=T(58.5))
    col_x = row[6]
    first_cup(col_x, front - .35, 4.4, size=.5, f0=f0, f1=T(58.5), text="FIRST CUP BAKERY")
    worker = person("mover", dict(shirt=0x56A0D8, pants=0x34383F, skin=0x8D5524, hair=0x0F0F10, style=4), 30, W, clips=[("push", f0 - 4, f1 + 4, 1.0)],
                    path=[(f0, 30, W), (f1, 30 + 1.4 * 3.0, W)], f0=f0, f1=f1)
    truck = follow(worker, "hand_truck", (0, -.72, 0), rz=180)
    for i in range(3):
        b = follow(worker, "cardboard_box", (0, -.86, .06 + i * .31), rz=4 * i - 4, s=1.0)
    scope(worker, f0, f1)
    person("hero_phone", HERO_BOSS, 23.2, front - 1.3, rz=25, clips=[("idle_phone", f0 - 6, f1 + 6, 1.0, .1)], f0=f0, f1=f1)
    for k, (x, d, o) in enumerate(((18, 1, .3), (38, -1, -.3), (47, 1, .2), (55, -1, .4), (61, -1, -.2), (26.5, -1, .5), (52, 1, -.4))):
        stroller(40 + k, x, W + o, d, f0, f1, speed=1.3 + (k % 3) * .12)
    person("bus_wait1", anyone(48), 50.2, AVE_HALF + 2.0, rz=0, clips=[("idle_look", f0 - 6, f1 + 6, 1.0, .2)], f0=f0, f1=f1)
    person("bus_wait2", anyone(49), 53.6, AVE_HALF + 2.3, rz=0, clips=[("idle_phone", f0 - 6, f1 + 6, 1.0, .6)], f0=f0, f1=f1)
    cruiser("bus", 1.75, 95, f0, f1, 9)
    cruiser("pickup", -1.75, 4, f0, f1, 11, color=0x27AE60)
    cruiser("sedan", -1.75, -18, f0, f1, 11, color=0xE9EDF2)
    petals(8, 6, 70, 12.5, f0, f1, n=420, seed=11)
    shot("07_one_block", f0, f1, "noon", [(0, (11.5, 7.3, 1.75), (20.5, front, 2.9)), (f1 - f0, (51.5, 7.3, 1.85), (60.5, front, 3.0))], lens=24)

    # ---------------------------------------------------------------- 8  the day goes by: up over the roofs
    f0, f1 = T(27.5), T(31.5) - 1
    for i in range(3):
        vehicle("metro_car", 0, VIA_Y - 2.0, z=VIA_Z, path=[(f0, -150 + i * 20.6, VIA_Y - 2.0), (f1, 60 + i * 20.6, VIA_Y - 2.0)], f0=f0, f1=f1)
    for k in range(9):
        cruiser(("sedan", "taxi", "suv", "van", "kei_car", "pickup", "sedan", "bus", "sports_car")[k], -1.75 if k % 2 else 1.75, -120 + k * 31 if k % 2 else 150 - k * 28,
                f0, f1, 24 + k % 3 * 4, color=CAR_COLORS[k % len(CAR_COLORS)] if k % 4 else None)
    shot("08_rise", f0, f1, "day", [(0, (-16, 0, 2.6), (70, 22, 12)), (f1 - f0, (-66, -1, 58), (90, 120, 22))], lens=24, ease=True,
         look_keys=[(0, "day"), (36, "noon"), (f1 - f0, "gold")])

    # ---------------------------------------------------------------- 9  night: the avenue from the back of a cab
    f0, f1 = T(37.5), T(41.5) - 1
    x0, x1 = -64.0, -64.0 + 14 * 4.0
    cab = vehicle("taxi", x0, -1.75, path=[(f0, x0, -1.75), (f1, x1, -1.75)], f0=f0, f1=f1)
    beam = bpy.data.objects.new("tr.headlight", bpy.data.lights.new("tr.headlight", 'SPOT'))
    beam.data.energy = 5000
    beam.data.spot_size = math.radians(70)
    beam.data.color = (1, .95, .82)
    beam.data.use_shadow = False
    tr_coll("Traffic").objects.link(beam)
    beam.parent = cab
    beam.location = (0, -2.4, .8)
    beam.rotation_euler = (math.radians(82), 0, 0)
    scope(cab, f0, f1)
    rain(cab, f0, f1)
    wet_road(f0, f1)
    for k, (m, x, c) in enumerate((("sedan", -10, 0x2F80ED), ("bus", 26, None), ("kei_car", 60, 0xE9EDF2), ("suv", 82, 0x2E3238), ("taxi", 110, None))):
        cruiser(m, 1.75, x, f0, f1, 12, color=c)
    cruiser("sports_car", -1.75, x0 + 26, f0, f1, 15.5, color=0xD0392B)
    for k, (x, sd, d) in enumerate(((-40, 1, 1), (-28, 1, -1), (-12, -1, 1), (14, 1, -1), (22, -1, -1), (30, 1, 1), (-52, -1, 1), (6, -1, 1))):
        stroller(60 + k, x, sd * (W + (k % 3 - 1) * .3), d, f0, f1)
    shot("09_night_ride", f0, f1, "night", [(0, (x0 - 7.5, -.9, 1.25), (x0 + 14, -.6, 2.4)), (f1 - f0, (x1 - 7.0, -.6, 1.35), (x1 + 14, -.2, 3.0))], lens=24)

    # ---------------------------------------------------------------- 11  the roof
    f0, f1 = T(45.5), T(51.5) - 1
    mx, my = row_w1_modern(scn)
    # the roof deck is the lowest thing up there; the hero stands where nothing of the roof garden is in the camera's way
    deck = [floor_at(mx + i * 1.5, my + j * 1.5) for i in range(-4, 5) for j in range(-4, 5)]
    roof = min(v for v in deck if v > 15)
    hx, hy = mx, my - 6.4
    for dx in (0, 1.5, -1.5, 3, -3, 4.5, -4.5, 5.5, -5.5):
        pts = [(mx + dx + ox, my - 6.4 + oy) for ox in (-2.6, -1.3, 0, 1.3, 2.6) for oy in (-.4, .6, 1.6, 2.4)]
        if all(abs(floor_at(px, py) - roof) < .25 for px, py in pts):
            hx = mx + dx
            break
    boss = person("hero_roof", HERO_BOSS, hx, hy, rz=0, z=roof, clips=[("idle", f0 - 6, T(58.5), 1.0, .2)], f0=f0, f1=T(58.5) - 1)
    # a short arc behind their shoulder: the roof garden's trees stand either side of it, nothing within 3 m of the lens
    shot("11_roof", f0, f1, "night", [(0, (hx + 1.0, hy + .6, roof + 1.75), (hx - .6, hy - 9, roof + 1.0)), (f1 - f0, (hx - .9, hy + .9, roof + 1.7), (hx + .9, hy - 9, roof + 1.0))],
         lens=26, ease=True)

    # ---------------------------------------------------------------- 12  the sign
    f0, f1 = T(51.5), T(58.5) - 1
    letters = rooftop_sign(mx, my + 4.6, roof, f0 + 30)
    shot("12_sign", f0, f1, "night", [(0, (hx + .5, hy - 3.2, roof + 1.5), (hx, hy, roof + 1.5), 40), (40, (hx + .5, hy - 4.5, roof + 1.8), (hx, hy + 2, roof + 2.2), 38),
                                      (f1 - f0, (mx, hy - 24, roof + 7.5), (mx, my + 4.6, roof + 6.6), 32)], lens=40, ease=True)

    # ---------------------------------------------------------------- 13  the next morning: somebody new
    f0, f1 = T(59.5), T(64.0) - 1
    sx = 9.5 + 22 + .5 + 10 + .5 + 5
    for_rent(sx - .6, -front, -1, f0, f1)
    rook = person("rookie", ROOKIE, sx - 7, -W, clips=[("carry", f0 - 4, f0 + 62, 1.0), ("carry_idle", f0 + 62, f1 + 6)],
                  path=[(f0, sx - 5.2, -W), (f0 + 62, sx - 5.2 + 1.4 * 62 / 24, -W), (f0 + 70, sx - 5.2 + 1.4 * 62 / 24, -W - .25)], f0=f0, f1=f1)
    hold_thing(rook, "cardboard_box", "torso", (0, -.4, .24), s=1.1)
    scope(rook, f0, f1)
    shot("13_next", f0, f1, "dawn", [(0, (sx + 7.5, -5.4, 1.35), (sx - 1.6, -front, 2.0)), (f1 - f0, (sx + 5.6, -4.6, 1.7), (sx - 1.8, -front, 2.6))], lens=27, ease=True)


def row_w1_modern(scn):
    """Where the terrace-roofed block north of the avenue stands (second in its row)."""
    for ob in tr_coll("Set").objects:
        if ob.name.startswith("tr.modern") and -60 < ob.location.x < -20 and 0 < ob.location.y < 40:
            return ob.location.x, ob.location.y
    raise RuntimeError("no modern block where the roof scene plays")


def rooftop_sign(x, y, roof, f_on):
    """HUSTLE over CITY in lit letters on a steel frame; they come on one after another from frame f_on."""
    steel = M(0x2A2D31, "METAL")
    a = Asset("tr.sign_frame")
    for sx in (-6.2, -2.1, 2.1, 6.2):
        a.box(x + sx - .09, y + .3, roof, x + sx + .09, y + .48, roof + 11.4, steel)
        a.rod((x + sx, y + .4, roof + 7.5), (x + sx, y + 3.0, roof), .06, steel, n=5)
    for z in (3.4, 7.0, 7.4, 11.0):
        a.box(x - 6.6, y + .3, roof + z, x + 6.6, y + .42, roof + z + .12, steel)
    frame = a.finish(tr_coll("Extras"))
    scope(frame, f_on - 180, f_on + 160)
    out = []
    k = 0
    for line, z, size in (("HUSTLE", roof + 9.2, 3.0), ("CITY", roof + 5.3, 3.0)):
        n = len(line)
        pitch = 1.95 if n == 6 else 2.15
        for i, ch in enumerate(line):
            m = bpy.data.materials.new("tr.neon." + ch + str(k))
            m.use_nodes = True
            b = next(nd for nd in m.node_tree.nodes if nd.type == 'BSDF_PRINCIPLED')
            b.inputs["Base Color"].default_value = (.16, .1, .06, 1)
            b.inputs["Emission Color"].default_value = (1.0, .5, .14, 1) if line == "HUSTLE" else (1.0, .86, .55, 1)
            ob = lettering(ch, x + (i - (n - 1) / 2) * pitch, y + .2, z, size, m, depth=.16, face_name="impact")
            ob.data.offset = .04
            es = b.inputs["Emission Strength"]
            on = f_on + k * 7
            keep = _interp('CONSTANT')
            # each tube stutters before it holds
            for f, v in ((1, 0.0), (on, 9.0), (on + 2, 0.0), (on + 4, 9.0), (on + 5, 1.0), (on + 7, 10.0)):
                es.default_value = v
                es.keyframe_insert("default_value", frame=f)
                key_kind(m.node_tree, f, 'CONSTANT')
            _interp(keep)
            scope(ob, f_on - 180, f_on + 160)
            out.append(ob)
            k += 1
    # the letters' glow on the roof and on whoever stands there
    glow = bpy.data.objects.new("tr.sign_glow", bpy.data.lights.new("tr.sign_glow", 'AREA'))
    glow.data.shape = 'RECTANGLE'
    glow.data.size = 12
    glow.data.size_y = 6
    glow.data.color = (1, .6, .25)
    glow.data.use_shadow = False
    tr_coll("Extras").objects.link(glow)
    glow.location = (x, y - .3, roof + 7.0)
    glow.rotation_euler = (math.radians(100), 0, 0)
    keep = _interp('LINEAR')
    for f, v in ((1, 0.0), (f_on, 0.0), (f_on + 70, 2600.0)):
        glow.data.energy = v
        glow.data.keyframe_insert("energy", frame=f)
        key_kind(glow.data, f, 'LINEAR')
    _interp(keep)
    return out
