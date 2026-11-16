"""The trailer's other places: three cities seen for two seconds each, the first shop's inside, and a club at night.
Needs everything shots.py needs, and shots.py itself, in front.

Each is its own little set, far from the avenue (the postcards 1200 m south, the rooms 1200 m north), built by the
same rules: crossings kerb to kerb, nothing standing in a road.
"""

POST_Y = -1200.0
ROOM_Y = 1200.0


def put(model, x, y, z=0.0, rz=0.0, s=1.0, coll="Postcards"):
    src = bpy.data.objects[model]
    ob = bpy.data.objects.new("tr." + model, src.data)
    tr_coll(coll).objects.link(ob)
    ob.location = (x, y, z)
    ob.rotation_euler = (0, 0, math.radians(rz))
    ob.scale = (s, s, s)
    return ob


def street(name, ox, half, walk, x0, x1, paving=PAVE, plaza=None, yard=120.0):
    """A straight street along X at (ox, POST_Y): asphalt, a centre line, pavements, and open ground either side."""
    g = Asset("tr.street_" + name)
    oy = POST_Y
    g.box(ox + x0 - 60, oy - yard - 200, -.5, ox + x1 + 420, oy + yard + 200, -.02, LAND)
    g.box(ox + x0 - 60, oy - half, -.3, ox + x1, oy + half, 0, ASPHALT)
    for sy in (-1, 1):
        y0, y1 = sorted((oy + sy * half, oy + sy * (half + yard)))
        g.box(ox + x0 - 60, y0, -.3, ox + x1, y1, KERB, paving)
        g.box(ox + x0 - 60, oy + sy * half - (.3 if sy < 0 else 0), -.3, ox + x1, oy + sy * half + (.3 if sy > 0 else 0), KERB + .004, PAVE_D)
    x = x0 - 50
    while x + 3 < x1 - 4:
        g.box(ox + x, oy - .07, .003, ox + x + 3, oy + .07, .011, LINE_Y)
        x += 8
    if plaza is not None:
        g.box(ox + x1, oy - yard, -.3, ox + x1 + plaza, oy + yard, KERB, PLAZA)
        # where the street meets the square: a crossing, kerb to kerb
        y = -half + .35
        while y + .6 <= half - .3:
            g.box(ox + x1 - 3.4, oy + y, .004, ox + x1 - .7, oy + y + .6, .012, LINE_W)
            y += 1.25
    return g.finish(tr_coll("Postcards"))


def house_row(names, ox, x_start, side, front, gap=.4, coll="Postcards"):
    x = x_start
    for n in names:
        fp = bpy.data.objects[n]["hc_footprint"]
        w, d = float(fp[0]), float(fp[1])
        back = max(0.0, reach(n) - 1.2)
        put(n, ox + x + w / 2, POST_Y + side * (front + back + d / 2), KERB, 0 if side > 0 else 180, coll=coll)
        x += w + gap
    return x


def post_person(k, ox, x, y, d, f0, f1, clip="walk", speed=1.35):
    secs = (f1 - f0) / FPS_TR
    look = anyone(200 + k)
    if clip != "walk":
        return person("post{:d}".format(k), look, ox + x, POST_Y + y, rz=d, clips=[(clip, f0 - 6, f1 + 6, 1.0, (k * .31) % 1)], f0=f0, f1=f1)
    return person("post{:d}".format(k), look, ox + x, POST_Y + y, clips=[("walk", f0 - 4, f1 + 4, speed / 1.4, (k * .37) % 1)],
                  path=[(f0, ox + x, POST_Y + y), (f1, ox + x + d * speed * secs, POST_Y + y)], f0=f0, f1=f1)


def post_car(model, ox, x, lane, f0, f1, speed, color=None):
    d = 1 if lane < 0 else -1
    secs = (f1 - f0) / FPS_TR
    return vehicle(model, ox + x, POST_Y + lane, color=color, path=[(f0, ox + x, POST_Y + lane), (f1, ox + x + d * speed * secs, POST_Y + lane)], f0=f0, f1=f1)


def build_postcards():
    # ---------------------------------------------------------------- Manila: fiesta bunting, jeepneys, the cathedral
    f0, f1 = T(31.5), T(33.5) - 1
    ox = 0.0
    half, walk = 4.2, 3.4
    street("manila", ox, half, walk, -40, 92, plaza=110)
    house_row(["tropical", "colonial", "shop", "tropical", "walkup", "colonial", "tropical", "shop", "colonial"], ox, -38, 1, half + walk)
    house_row(["colonial", "tropical", "tropical", "shop", "colonial", "walkup", "tropical", "colonial", "shop"], ox, -36, -1, half + walk)
    put("cathedral", ox + 92 + 62, POST_Y, KERB, -90)
    put("monument", ox + 92 + 22, POST_Y, KERB, -90, s=.55)
    for k in range(5):
        put("bunting_string", ox + 4 + k * 15, POST_Y, KERB, 90, s=(half + .45) / 4.5)
    for k in range(7):
        for sy in (-1, 1):
            if (k + (sy > 0)) % 2:
                put("tree_palm", ox - 6 + k * 13.5, POST_Y + sy * (half + 1.3), KERB, 90 * sy + k * 30)
            else:
                put("lamp_post", ox - 6 + k * 13.5, POST_Y + sy * (half + .8), KERB, 0 if sy > 0 else 180)
    put("market_stall", ox + 21, POST_Y + half + 2.2, KERB, 0)
    put("hotdog_cart", ox + 44, POST_Y - half - 2.0, KERB, 180)
    post_car("jeepney", ox, 2, -2.1, f0, f1, 7.5)
    post_car("tricycle", ox, 19, -2.2, f0, f1, 7)
    post_car("modern_jeepney", ox, 58, 2.1, f0, f1, 7)
    post_car("jeepney", ox, 30, 2.1, f0, f1, 7, color=0x2F80ED)
    post_car("moped", ox, 46, -2.4, f0, f1, 9)
    for k, (x, sy, d) in enumerate(((3, 1, 1), (9, -1, -1), (15, 1, -1), (26, -1, 1), (33, 1, 1), (12, -1, 1), (40, 1, -1), (48, -1, -1))):
        post_person(k, ox, x, sy * (half + 1.9 + (k % 2) * .6), d, f0, f1)
    post_person(8, ox, 21.2, half + 1.2, 180, f0, f1, clip="talk")
    post_person(9, ox, 20.2, half + 3.2, 0, f0, f1, clip="work")
    shot("p1_manila", f0, f1, "gold", [(0, (ox - 9, POST_Y - 1.0, 2.1), (ox + 100, POST_Y, 15)), (f1 - f0, (ox - 4.5, POST_Y - .8, 2.5), (ox + 100, POST_Y, 16))], lens=32)

    # ---------------------------------------------------------------- Tokyo: lanterns, the gate, the tower behind it
    f0, f1 = T(33.5), T(35.5) - 1
    ox = 900.0
    half, walk = 3.6, 3.0
    street("tokyo", ox, half, walk, -40, 84, plaza=60, paving=M(0x9A958C, "CONCRETE"))
    house_row(["pencil", "shop", "pencil", "walkup", "pencil", "pencil", "shop", "pencil", "modern", "pencil", "walkup", "pencil", "shop", "pencil"], ox, -38, 1, half + walk)
    house_row(["shop", "pencil", "pencil", "walkup", "pencil", "shop", "pencil", "pencil", "walkup", "pencil", "modern", "pencil", "pencil"], ox, -36, -1, half + walk)
    put("torii", ox + 84 + 9, POST_Y, KERB, 90)
    put("lattice_tower", ox + 84 + 150, POST_Y, 0, 45)
    for k, n in enumerate(("glass_tower", "deco", "modern", "glass_tower", "pencil", "deco")):
        put(n, ox + 150 + (k % 3) * 40, POST_Y + (1 if k % 2 else -1) * (70 + k * 14), 0, k * 90, s=1.0 + .15 * k)
    for k in range(5):
        put("lantern_string", ox + 2 + k * 15, POST_Y, KERB, 90, s=(half + .4) / 4.5)
    for k in range(8):
        for sy in (-1, 1):
            n = ("tree_ginkgo", "paper_lantern", "vending_machine", "lamp_post")[(k + (sy > 0) * 2) % 4]
            off = {"vending_machine": half + walk - .6, "tree_ginkgo": half + 1.2, "paper_lantern": half + .7, "lamp_post": half + .8}[n]
            put(n, ox - 8 + k * 11.5, POST_Y + sy * off, KERB, 0 if sy > 0 else 180)
    post_car("kei_car", ox, 6, -1.8, f0, f1, 6.5, color=0xE9EDF2)
    post_car("kei_car", ox, 40, 1.8, f0, f1, 6.5, color=0xF2C94C)
    post_car("moped", ox, 24, -1.9, f0, f1, 8)
    post_car("taxi", ox, 62, 1.8, f0, f1, 6.5)
    for k, (x, sy, d) in enumerate(((2, 1, 1), (7, -1, -1), (13, 1, -1), (22, -1, 1), (30, 1, 1), (10, -1, 1), (36, 1, -1), (44, -1, -1), (52, 1, 1), (60, -1, 1))):
        post_person(20 + k, ox, x, sy * (half + 1.6 + (k % 2) * .5), d, f0, f1)
    shot("p2_tokyo", f0, f1, "dusk", [(0, (ox - 12, POST_Y + 1.4, 1.5), (ox + 110, POST_Y, 26)), (f1 - f0, (ox - 8, POST_Y + 1.2, 1.7), (ox + 110, POST_Y, 28))], lens=28)

    # ---------------------------------------------------------------- Seoul: the palace gate at the head of its avenue
    f0, f1 = T(35.5), T(37.5) - 1
    ox = 1800.0
    half, walk = 9.0, 6.0
    street("seoul", ox, half, walk, -50, 70, plaza=70, paving=M(0xC2BBAE, "STONE"))
    house_row(["hanok", "hanok", "hanok", "hanok", "hanok", "hanok", "hanok", "hanok", "hanok"], ox, -44, 1, half + walk + 1.5, gap=1.2)
    house_row(["hanok", "hanok", "hanok", "hanok", "hanok", "hanok", "hanok", "hanok", "hanok"], ox, -43, -1, half + walk + 1.5, gap=1.2)
    put("palace_gate", ox + 70 + 20, POST_Y, KERB, -90)
    for k, n in enumerate(("glass_tower", "deco", "modern", "glass_tower", "modern", "deco", "glass_tower")):
        put(n, ox + 150 + (k % 2) * 46, POST_Y - 130 + k * 44, 0, k * 90, s=1.0 + .1 * (k % 3))
    for k in range(9):
        for sy in (-1, 1):
            put("tree_cherry" if k % 2 == 0 else "park_lamp", ox - 26 + k * 11, POST_Y + sy * (half + 1.6), KERB, k * 40)
    for sy in (-1, 1):
        flood = bpy.data.objects.new("tr.flood", bpy.data.lights.new("tr.flood", 'SPOT'))
        flood.data.energy = 90000
        flood.data.spot_size = math.radians(80)
        flood.data.color = (1, .78, .5)
        flood.data.use_shadow = False
        tr_coll("Postcards").objects.link(flood)
        flood.location = (ox + 62, POST_Y + sy * 12, 1.2)
        flood.rotation_euler = (Vector((ox + 90, POST_Y + sy * 4, 12)) - Vector(flood.location)).to_track_quat('-Z', 'Y').to_euler()
        scope(flood, f0, f1)
    post_car("bus", ox, -8, -6.6, f0, f1, 8)
    post_car("taxi", ox, 14, -2.4, f0, f1, 9, color=0xF0812E)
    post_car("sedan", ox, 52, 2.4, f0, f1, 9, color=0xE9EDF2)
    post_car("suv", ox, 30, 6.6, f0, f1, 8, color=0x2E3238)
    for k, (x, sy, d) in enumerate(((-2, 1, 1), (5, -1, -1), (12, 1, -1), (20, -1, 1), (28, 1, 1), (8, -1, 1), (34, 1, -1), (40, -1, -1), (16, 1, 1), (46, -1, 1))):
        post_person(40 + k, ox, x, sy * (half + 3.2 + (k % 3) * .7), d, f0, f1)
    petals(ox - 18, POST_Y - 14, ox + 50, POST_Y + 14, f0, f1, n=420, top=11, seed=5)
    shot("p3_seoul", f0, f1, "night", [(0, (ox - 16, POST_Y, 1.7), (ox + 90, POST_Y, 9)), (f1 - f0, (ox - 11, POST_Y, 2.3), (ox + 90, POST_Y, 9.5))], lens=30)


# ---------------------------------------------------------------------------------------------- rooms

def room(name, ox, oy, w, d, h, floor, wall, ceiling=None, open_front=True):
    """Four walls (the front one, toward -Y, left out for the camera when open_front), a floor and a ceiling."""
    r = Asset("tr.room_" + name)
    r.box(ox - w / 2 - .3, oy - d / 2 - .3, -.3, ox + w / 2 + .3, oy + d / 2 + .3, 0, floor)
    r.box(ox - w / 2 - .3, oy + d / 2, 0, ox + w / 2 + .3, oy + d / 2 + .3, h, wall)
    r.box(ox - w / 2 - .3, oy - d / 2, 0, ox - w / 2, oy + d / 2, h, wall)
    r.box(ox + w / 2, oy - d / 2, 0, ox + w / 2 + .3, oy + d / 2, h, wall)
    if not open_front:
        r.box(ox - w / 2 - .3, oy - d / 2 - .3, 0, ox + w / 2 + .3, oy - d / 2, h, wall)
    r.box(ox - w / 2 - 3, oy - d / 2 - 12, h, ox + w / 2 + 3, oy + d / 2 + 3, h + .3, ceiling or wall)
    r.box(ox - w / 2, oy + d / 2 - .03, 0, ox + w / 2, oy + d / 2, .14, M(0x3E2723, "WOOD"))
    return r.finish(tr_coll("Rooms"))


def lamp(kind, x, y, z, energy, color, f0, f1, coll="Rooms", size=.3, aim=None, spot=70.0):
    ob = bpy.data.objects.new("tr.light", bpy.data.lights.new("tr.light", kind))
    ob.data.energy = energy
    ob.data.color = color
    ob.data.use_shadow = False
    if kind == 'SPOT':
        ob.data.spot_size = math.radians(spot)
        ob.data.spot_blend = .5
    if kind == 'AREA':
        ob.data.size = size
    else:
        ob.data.shadow_soft_size = size
    tr_coll(coll).objects.link(ob)
    ob.location = (x, y, z)
    if aim is not None:
        ob.rotation_euler = (Vector(aim) - Vector((x, y, z))).to_track_quat('-Z', 'Y').to_euler()
    scope(ob, f0, f1)
    return ob


def build_rooms():
    # ---------------------------------------------------------------- 5  behind the counter, the first week
    f0, f1 = T(19.5), T(22.0) - 1
    ox, oy = 0.0, ROOM_Y
    room("cafe", ox, oy, 9, 7, 3.3, M(0x8B5A3C, "WOOD"), M(0xF0E6D2, "PLASTER"), M(0xF7F2E8, "PLASTER"))
    R = "Rooms"
    put("checkout_counter", ox + 1.4, oy + 1.6, 0, 0, coll=R)
    put("coffee_machine", ox - 1.6, oy + 2.9, 0, 0, coll=R)
    put("pastry_case", ox + 3.6, oy + 1.7, 0, -90, coll=R)
    put("shelf", ox - 4.0, oy + .2, 0, 90, coll=R)
    put("shelf", ox - 4.0, oy - 2.0, 0, 90, coll=R)
    put("plant_large", ox + 3.9, oy + 3.0, 0, 0, coll=R)
    put("painting", ox + .2, oy + 3.0, 0, 0, coll=R)
    put("neon_cactus", ox - 3.4, oy + 3.0, 0, 0, coll=R)
    put("table_set", ox - 1.3, oy - 1.9, 0, 0, coll=R)
    put("table_set", ox + 2.3, oy - 1.3, 0, 0, coll=R)
    put("floor_lamp", ox + 4.0, oy - 2.9, 0, 0, coll=R)
    put("rug", ox + .4, oy - 1.4, 0, 0, coll=R)
    first_cup(ox + 1.4, oy + 3.46, 2.55, size=.42, f0=f0, f1=f1)
    person("hero_counter", HERO_APRON, ox + 1.3, oy + 2.5, rz=0, z=0, clips=[("work", f0 - 6, f1 + 6, 1.0, .2)], f0=f0, f1=f1)
    person("cafe_talk", anyone(70), ox + 1.6, oy + .65, rz=180, z=0, clips=[("talk", f0 - 6, f1 + 6, 1.0, .4)], f0=f0, f1=f1)
    person("cafe_queue", anyone(71), ox + 2.3, oy - .1, rz=165, z=0, clips=[("idle_phone", f0 - 6, f1 + 6, 1.0, .1)], f0=f0, f1=f1)
    person("cafe_reach", anyone(72), ox - 3.2, oy + .2, rz=-90, z=0, clips=[("reach", f0 - 6, f1 + 6, .8, .3)], f0=f0, f1=f1)
    for k, (tx, ty) in enumerate(((-1.3, -1.9), (2.3, -1.3))):
        person("eat{:d}a".format(k), anyone(73 + k * 2), ox + tx - .7, oy + ty, rz=90, z=.45, clips=[("sit_eat", f0 - 6, f1 + 6, 1.0, .2 + k * .5)], f0=f0, f1=f1)
        person("eat{:d}b".format(k), anyone(74 + k * 2), ox + tx + .7, oy + ty, rz=-90, z=.45, clips=[("sit" if k else "sit_eat", f0 - 6, f1 + 6, 1.0, .7)], f0=f0, f1=f1)
    lamp('AREA', ox, oy - 2.5, 3.1, 900, (1, .93, .82), f0, f1, size=5, aim=(ox, oy - 1, 0))
    lamp('AREA', ox + 1, oy + 1.5, 3.1, 700, (1, .88, .7), f0, f1, size=4, aim=(ox + 1, oy + 1.5, 0))
    lamp('POINT', ox - 3, oy + 2, 2.4, 250, (1, .8, .55), f0, f1)
    shot("05_counter", f0, f1, "day", [(0, (ox - 3.3, oy - 4.6, 1.5), (ox + 1.2, oy + 1.9, 1.15)), (f1 - f0, (ox - 1.9, oy - 4.6, 1.55), (ox + 1.3, oy + 1.9, 1.15))], lens=26)

    # ---------------------------------------------------------------- 10  the club
    f0, f1 = T(41.5), T(45.5) - 1
    ox, oy = 400.0, ROOM_Y
    dark = M(0x15121F, "PLASTER")
    room("club", ox, oy, 15, 13, 5.2, M(0x101018, "TILE"), dark, dark, open_front=False)
    put("dance_floor", ox - 1.5, oy - .4, 0, 0, coll=R)
    put("dance_floor", ox + 1.5, oy - .4, 0, 0, coll=R)
    put("stage", ox, oy + 4.4, 0, 0, coll=R)
    put("dj_booth", ox, oy + 4.1, .6, 0, coll=R)
    for sx in (-1, 1):
        put("speaker_large", ox + sx * 3.2, oy + 4.6, 0, 0, s=1.3, coll=R)
        put("stage_lights", ox + sx * 5.4, oy + 4.6, 0, 0, coll=R)
        put("neon_circle", ox + sx * 5.6, oy + 5.0, .4, 0, s=1.5, coll=R)
    put("cocktail_bar", ox + 6.9, oy - .5, 0, -90, coll=R)
    for k in range(3):
        put("barstool", ox + 5.7, oy - 1.9 + k * 1.3, 0, 0, coll=R)
    put("lounge_sofa", ox - 6.8, oy + 1.2, 0, 90, coll=R)
    put("lounge_sofa", ox - 6.8, oy - 1.6, 0, 90, coll=R)
    put("neon_cactus", ox - 7.0, oy + 3.6, .6, 90, s=1.4, coll=R)
    person("dj", anyone(80), ox, oy + 4.95, rz=0, z=.6, clips=[("dance_b", f0 - 6, f1 + 6, 1.0, .1)], f0=f0, f1=f1)
    spots = ((-2.3, .5, 20), (-.9, -.9, 200), (.4, .6, -40), (1.9, -.6, 150), (2.6, .9, -110), (-1.6, -1.6, 70), (.9, -1.5, 10), (-2.6, -.6, -150), (0, -.1, 100),
             (1.4, .2, 240))
    for k, (dx, dy, rz) in enumerate(spots):
        person("dancer{:d}".format(k), anyone(81 + k), ox + dx, oy - .4 + dy, rz=rz, z=.08,
               clips=[("dance_a" if k % 3 else "dance_b", f0 - 6, f1 + 6, 1.0, (k * .23) % 1)], f0=f0, f1=f1)
    person("bartender", dict(anyone(95), apron=True, accent=0x2C3E50), ox + 7.0, oy + .2, rz=90, z=0, clips=[("work", f0 - 6, f1 + 6, 1.0, .3)], f0=f0, f1=f1)
    for k in range(2):
        person("barfly{:d}".format(k), anyone(96 + k), ox + 5.7, oy - 1.9 + k * 1.3, rz=-90, z=.85, clips=[("sit", f0 - 6, f1 + 6, 1.0, .4 * k)], f0=f0, f1=f1)
    person("sofa0", anyone(98), ox - 6.6, oy + .6, rz=-90, z=.42, clips=[("sit", f0 - 6, f1 + 6, 1.0, .2)], f0=f0, f1=f1)
    person("sofa1", anyone(99), ox - 6.6, oy + 1.8, rz=-90, z=.42, clips=[("sit_eat", f0 - 6, f1 + 6, 1.0, .6)], f0=f0, f1=f1)
    # the light show: four heads over the floor, changing color on every beat
    hues = ((1, .15, .7), (.15, .6, 1), (1, .8, .15), (.2, 1, .55), (1, .35, .15), (.6, .2, 1))
    for i, (dx, dy) in enumerate(((-3, -2.5), (3, -2.5), (-3, 2.0), (3, 2.0))):
        sp = lamp('SPOT', ox + dx, oy + dy, 4.9, 4200, hues[i], f0, f1, aim=(ox + dx * .3, oy - .4 + dy * .2, 0), spot=75)
        keep = _interp('CONSTANT')
        b = 0
        for f in range(f0, f1 + 1, 12):
            sp.data.color = hues[(i + b) % len(hues)]
            sp.data.keyframe_insert("color", frame=f)
            key_kind(sp.data, f, 'CONSTANT')
            b += 1
        _interp(keep)
    lamp('POINT', ox, oy, 4.6, 380, (.5, .3, 1), f0, f1, size=1.0)
    lamp('AREA', ox, oy + 3.6, 3.6, 900, (1, .4, .8), f0, f1, size=3, aim=(ox, oy + 4.4, .6))
    lamp('POINT', ox + 6.6, oy - .5, 2.6, 260, (1, .7, .4), f0, f1)
    mid = (f0 + f1 + 1) // 2
    shot("10_club_a", f0, mid - 1, "night", [(0, (ox - 5.6, oy - 6.0, 1.25), (ox + .2, oy + 1.2, 1.5)), (mid - 1 - f0, (ox - 3.4, oy - 6.0, 1.35), (ox + .2, oy + 1.2, 1.5))], lens=22)
    shot("10_club_b", mid, f1, "night", [(0, (ox + 3.6, oy + 2.6, 2.0), (ox - .4, oy - .5, 1.25)), (f1 - mid, (ox + 2.2, oy + 3.0, 1.9), (ox - .4, oy - .5, 1.25))],
         lens=35, fstop=2.8, focus=4.6)
