"""The trailer's city: a stretch of avenue built out of the library, in its own scene. Needs kit.py in front, and the
library built (build_all.py) in the same Blender.

  bl.py kit.py trailer/set.py            builds the "HC Trailer" scene: streets, buildings, props, the viaduct, skyline

The avenue runs along X at y = 0 (traffic keeps right: eastbound on the south half), crossed by three streets and
closed at its east end by the city hall. Everything is placed by rule and then checked (check_set): nothing stands
in a road, in a building or on top of something else, and every crossing runs kerb to kerb on its own road.
"""
import random

TRAILER = "HC Trailer"
AVE_HALF = 7.0
AVE_X0, AVE_X1 = -250.0, 172.0
CROSS = (-80.0, 0.0, 80.0)
CROSS_HALF = 5.0
CROSS_Y = 135.0
WALK = 5.0                     # avenue pavement, kerb to building line
SIDE_WALK = 4.0                # cross street pavement
FRONT = AVE_HALF + WALK        # building line, north and south
KERB = .15
WALK_Y = AVE_HALF + 3.4        # the line people walk along the avenue: clear of the kerb's lamps and trees, clear of the doorsteps

ROADS = [("avenue", AVE_X0, -AVE_HALF, AVE_X1, AVE_HALF)] + [("cross", c - CROSS_HALF, -CROSS_Y, c + CROSS_HALF, CROSS_Y) for c in CROSS]
PLACED = []                    # (tag, name, x, y, radius) of every prop, for the checks
LOTS = []                      # (name, x0, y0, x1, y1) of every building
CROSSINGS = []                 # (road index, x0, y0, x1, y1)


def tr_scene():
    scn = bpy.data.scenes.get(TRAILER)
    if scn is None:
        scn = bpy.data.scenes.new(TRAILER)
    return scn


def tr_coll(name):
    scn = tr_scene()
    full = "TR " + name
    c = bpy.data.collections.get(full)
    if c is None:
        c = bpy.data.collections.new(full)
    if full not in scn.collection.children:
        scn.collection.children.link(c)
    return c


def tr_clear(name):
    """Empties one of the trailer's collections; meshes made for it go too, the library's stay."""
    c = tr_coll(name)
    for ob in list(c.objects):
        data = ob.data
        bpy.data.objects.remove(ob, do_unlink=True)
        if data is not None and data.users == 0:
            if isinstance(data, bpy.types.Mesh):
                bpy.data.meshes.remove(data)
            elif isinstance(data, bpy.types.Curve):
                bpy.data.curves.remove(data)
            elif isinstance(data, bpy.types.Light):
                bpy.data.lights.remove(data)
            elif isinstance(data, bpy.types.Camera):
                bpy.data.cameras.remove(data)
    return c


def inst(name, x, y, z=0.0, rz=0.0, s=1.0, coll="Set", tag=None, radius=None):
    """A linked copy of a library model, standing in the trailer."""
    src = bpy.data.objects[name]
    ob = bpy.data.objects.new("tr." + name, src.data)
    tr_coll(coll).objects.link(ob)
    ob.location = (x, y, z)
    ob.rotation_euler = (0, 0, math.radians(rz))
    ob.scale = (s, s, s)
    if tag:
        ob["tr_tag"] = tag
        PLACED.append((tag, name, x, y, radius if radius is not None else .45))
    return ob


def on_road(x, y, pad=0.0):
    return any(x0 - pad < x < x1 + pad and y0 - pad < y < y1 + pad for _, x0, y0, x1, y1 in ROADS)


def in_lot(x, y, pad=0.0):
    return any(x0 - pad < x < x1 + pad and y0 - pad < y < y1 + pad for _, x0, y0, x1, y1 in LOTS)


# ---------------------------------------------------------------------------------------------- ground

ASPHALT = M(0x33363B, "ASPHALT")
PAVE = M(0xB4AEA3, "CONCRETE")
PAVE_D = M(0x9A958C, "CONCRETE")
PLAZA = M(0xC9BFAE, "STONE")
LINE_W = M(0xE4E6E8, "PAINT")
LINE_Y = M(0xE2B93B, "PAINT")
LAND = M(0x6F7468, "CONCRETE")


def zebra(g, road, x0, y0, x1, y1, along):
    """Bars of a crossing filling the rectangle; `along` is the axis the traffic drives ('x' or 'y')."""
    CROSSINGS.append((road, x0, y0, x1, y1))
    if along == 'x':
        y = y0 + .35
        while y + .6 <= y1 - .3:
            g.box(x0, y, .004, x1, y + .6, .012, LINE_W)
            y += 1.25
    else:
        x = x0 + .35
        while x + .6 <= x1 - .3:
            g.box(x, y0, .004, x + .6, y1, .012, LINE_W)
            x += 1.25


def build_ground():
    g = Asset("tr.ground")
    X0, X1, Y0, Y1 = -420.0, 420.0, -420.0, 420.0
    g.box(X0, Y0, -.5, X1, Y1, -.02, LAND)
    # asphalt
    g.box(AVE_X0, -AVE_HALF, -.3, AVE_X1, AVE_HALF, 0, ASPHALT)
    for c in CROSS:
        g.box(c - CROSS_HALF, -CROSS_Y, -.3, c + CROSS_HALF, CROSS_Y, 0, ASPHALT)
    # pavement: the blocks between the roads, as far as the buildings go, each with a paler kerb stone round it
    xs = [AVE_X0 - 30]
    for c in CROSS:
        xs += [c - CROSS_HALF, c + CROSS_HALF]
    xs += [AVE_X1]
    for i in range(0, len(xs), 2):
        for y0, y1 in ((AVE_HALF, CROSS_Y + 40), (-CROSS_Y - 40, -AVE_HALF)):
            g.box(xs[i], y0, -.3, xs[i + 1], y1, KERB, PAVE)
            g.box(xs[i], y0 if y0 > 0 else y1 - .3, -.3, xs[i + 1], y0 + .3 if y0 > 0 else y1, KERB + .004, PAVE_D)
            for sx in (xs[i], xs[i + 1] - .3):
                if AVE_X0 < sx < AVE_X1 - 1:
                    g.box(sx, y0, -.3, sx + .3, y1, KERB + .004, PAVE_D)
    # paving joints across the avenue's pavements
    x = AVE_X0
    while x < AVE_X1:
        if not on_road(x, 9, .3):
            for sy in (-1, 1):
                g.box(x, sy * (AVE_HALF + .3) if sy > 0 else -FRONT, KERB, x + .04, FRONT if sy > 0 else -(AVE_HALF + .3), KERB + .003, PAVE_D)
        x += 4.0
    # the square in front of the city hall
    g.box(AVE_X1, -60, -.3, AVE_X1 + 90, 60, KERB, PLAZA)
    for k in range(9):
        g.box(AVE_X1 + 2 + k * 3.0, -40, KERB, AVE_X1 + 2.08 + k * 3.0, 40, KERB + .003, PAVE_D)
    # the avenue's paint: a double yellow centre line, dashed lane lines, a solid line off the parking lane
    spans = []
    edges = [AVE_X0] + [v for c in CROSS for v in (c - CROSS_HALF - 9.6, c + CROSS_HALF + 9.6)] + [AVE_X1 - 6]
    for i in range(0, len(edges), 2):
        spans.append((edges[i], edges[i + 1]))
    for a, b in spans:
        g.box(a, .12, .003, b, .28, .011, LINE_Y)
        g.box(a, -.28, .003, b, -.12, .011, LINE_Y)
        x = a + 1
        while x + 3 < b:
            for y in (-3.5, 3.5):
                g.box(x, y - .07, .003, x + 3, y + .07, .011, LINE_W)
            x += 9
    for c in CROSS:
        # crossings over the avenue, either side of the junction, and over the cross street, north and south
        zebra(g, 0, c - CROSS_HALF - 3.2, -AVE_HALF, c - CROSS_HALF - .5, AVE_HALF, 'x')
        zebra(g, 0, c + CROSS_HALF + .5, -AVE_HALF, c + CROSS_HALF + 3.2, AVE_HALF, 'x')
        ri = 1 + CROSS.index(c)
        zebra(g, ri, c - CROSS_HALF, WALK_Y - 1.5, c + CROSS_HALF, WALK_Y + 1.2, 'y')
        zebra(g, ri, c - CROSS_HALF, -WALK_Y - 1.2, c + CROSS_HALF, -WALK_Y + 1.5, 'y')
        # stop lines on the driver's own side
        g.box(c - CROSS_HALF - 4.6, -AVE_HALF + .3, .003, c - CROSS_HALF - 4.2, -.4, .011, LINE_W)
        g.box(c + CROSS_HALF + 4.2, .4, .003, c + CROSS_HALF + 4.6, AVE_HALF - .3, .011, LINE_W)
        g.box(c + .3, -WALK_Y - 2.6, .003, c + CROSS_HALF - .3, -WALK_Y - 2.2, .011, LINE_W)
        g.box(c - CROSS_HALF + .3, WALK_Y + 2.2, .003, c - .3, WALK_Y + 2.6, .011, LINE_W)
        # the cross street's own centre line
        for y0, y1 in ((WALK_Y + 4, CROSS_Y), (-CROSS_Y, -WALK_Y - 4)):
            y = y0
            while y + 3 < y1:
                g.box(c - .07, y, .003, c + .07, y + 3, .011, LINE_Y)
                y += 7
    # manholes and drains
    rnd = random.Random(5)
    for k in range(22):
        x = AVE_X0 + 20 + k * 18.5
        if on_road(x, 30, 12):
            continue
        g.cyl(x, rnd.choice((-5.2, -1.8, 1.8, 5.2)), 0, .008, .42, M(0x3B3E43, "METAL"), n=12)
    ob = g.finish(tr_coll("Set"))
    return ob


# ---------------------------------------------------------------------------------------------- buildings

def lot_of(name, x, y, rz, s=1.0):
    """The ground a building covers, stoops, steps and porticos included (awnings overhead too: nothing tall goes under them)."""
    bb = [Vector(c) for c in bpy.data.objects[name].bound_box]
    r = Matrix.Rotation(math.radians(rz), 3, 'Z')
    pts = [r @ Vector((c.x * s, c.y * s, 0)) for c in bb]
    return (name, x + min(p.x for p in pts), y + min(p.y for p in pts), x + max(p.x for p in pts), y + max(p.y for p in pts))


def reach(name):
    """How far a building's front (-Y) comes out past its footprint, in meters."""
    ob = bpy.data.objects[name]
    return -min(c[1] for c in ob.bound_box) - float(ob["hc_footprint"][1]) / 2


def stand(name, x, y, rz=0.0, s=1.0):
    LOTS.append(lot_of(name, x, y, rz, s))
    return inst(name, x, y, KERB if not on_road(x, y) else 0, rz, s)


def row(names, x0, side, gap=.5):
    """Buildings shoulder to shoulder along the avenue from x0 eastward; side +1 north (fronts face south), -1 south."""
    x = x0
    out = []
    for n in names:
        fp = bpy.data.objects[n]["hc_footprint"]
        w, d = float(fp[0]), float(fp[1])
        # an awning may hang 1.5 m over the pavement; stoops and steps that come out further push the house back
        back = max(0.0, reach(n) - 1.5)
        out.append(stand(n, x + w / 2, side * (FRONT + back + d / 2), 0 if side > 0 else 180))
        x += w + gap
    return out, x


def build_blocks():
    made = {}
    # the hero's block: north side, east of the middle crossing; his shop is the first door
    made["hero"], _ = row(["shop", "brownstone", "walkup", "colonial", "pencil", "shop"], 9.5, 1)
    row(["classic", "modern", "walkup", "brownstone"], -70.5, 1)
    row(["deco", "tropical", "shop", "walkup", "pencil"], 9.5, -1)
    row(["glass_tower", "shop", "brownstone", "brownstone", "walkup"], -70.5, -1)
    row(["industrial", "walkup", "modern", "classic", "pencil", "deco", "shop", "colonial", "walkup"], -233, 1)
    row(["modern", "deco", "walkup", "glass_tower", "brownstone", "classic", "shop", "tropical", "pencil"], -222, -1)
    row(["walkup", "classic", "pencil", "modern", "shop", "brownstone"], 89.5, 1)
    row(["glass_tower", "walkup", "colonial", "deco", "pencil"], 89.5, -1)
    stand("city_hall", AVE_X1 + 48, 0, -90)
    # the skyline behind: towers on a loose grid, bigger the further back
    rnd = random.Random(21)
    tall = ["glass_tower", "deco", "modern", "pencil", "classic", "glass_tower", "deco", "walkup", "modern"]
    for side, ys in ((1, (84, 122, 162, 204)), (-1, (-62, -100, -140, -182, -226))):
        for j, y in enumerate(ys):
            x = -262 + (j % 2) * 17
            while x < 262:
                n = rnd.choice(tall)
                s = 1.0 + rnd.random() * (.5 + .22 * j)
                px, py = x + rnd.uniform(-5, 5), y + rnd.uniform(-5, 5)
                turn = rnd.choice((0, 90, 180, 270))
                lot = lot_of(n, px, py, turn, s)
                clear = not any(c - CROSS_HALF - SIDE_WALK < lot[3] and lot[1] < c + CROSS_HALF + SIDE_WALK for c in CROSS)
                clear = clear and not any(l[1] - 1 < lot[3] and lot[1] < l[3] + 1 and l[2] - 1 < lot[4] and lot[2] < l[4] + 1 for l in LOTS)
                if clear and rnd.random() < .86 and not (AVE_X1 - 5 < px < AVE_X1 + 100 and abs(py) < 70):
                    stand(n, px, py, turn, s)
                x += 34 + rnd.uniform(0, 10)
    # and the ordinary streets between the towers: low houses wherever there is room
    low = ["walkup", "brownstone", "shop", "colonial", "walkup", "pencil", "industrial", "tropical", "brownstone", "walkup"]
    y = -262
    while y < 262:
        x = -268 + (int(y) % 3) * 7
        while x < 268:
            n = rnd.choice(low)
            px, py = x + rnd.uniform(-4, 4), y + rnd.uniform(-4, 4)
            turn = rnd.choice((0, 90, 180, 270))
            lot = lot_of(n, px, py, turn)
            clear = abs(py) > 46 and not (40 < py < 64)
            clear = clear and not any(c - CROSS_HALF - SIDE_WALK < lot[3] and lot[1] < c + CROSS_HALF + SIDE_WALK for c in CROSS)
            clear = clear and not any(l[1] - 2 < lot[3] and lot[1] < l[3] + 2 and l[2] - 2 < lot[4] and lot[2] < l[4] + 2 for l in LOTS)
            if clear and rnd.random() < .8 and not (AVE_X1 - 5 < px < AVE_X1 + 100 and abs(py) < 70):
                stand(n, px, py, turn)
            x += 27
        y += 25
    LOTS.append(lot_of("lattice_tower", -150, 300, 0))
    inst("lattice_tower", -150, 300, 0)
    LOTS.append(lot_of("ferris_wheel", 150, 268, 0))
    inst("ferris_wheel", 150, 268, 0, rz=18)
    return made


# ---------------------------------------------------------------------------------------------- the railway

VIA_Y, VIA_Z = 53.0, 9.0


def build_viaduct():
    v = Asset("tr.viaduct")
    conc = M(0xA8ABAF, "CONCRETE")
    conc_d = M(0x8C9096, "CONCRETE")
    steel = M(0x4A4F57, "METAL")
    v.box(-300, VIA_Y - 4.2, VIA_Z - 1.1, 300, VIA_Y + 4.2, VIA_Z - .1, conc)
    v.box(-300, VIA_Y - 2.4, VIA_Z - 1.9, 300, VIA_Y + 2.4, VIA_Z - 1.1, conc_d)
    for sy in (-1, 1):
        v.box(-300, VIA_Y + sy * 4.2 - .15, VIA_Z - .1, 300, VIA_Y + sy * 4.2 + .15, VIA_Z + 1.0, conc)
        for o in (.72, -.72):
            v.box(-300, VIA_Y + sy * 2.0 + o - .04, VIA_Z - .1, 300, VIA_Y + sy * 2.0 + o + .04, VIA_Z, steel)
    x = -290
    while x <= 290:
        if not any(abs(x - c) < CROSS_HALF + 2.5 for c in CROSS):
            v.box(x - 1.1, VIA_Y - 1.1, 0, x + 1.1, VIA_Y + 1.1, VIA_Z - 1.9, conc_d, bevel=.2)
            v.box(x - 1.5, VIA_Y - 3.6, VIA_Z - 2.5, x + 1.5, VIA_Y + 3.6, VIA_Z - 1.9, conc_d)
            LOTS.append(("pier", x - 1.2, VIA_Y - 1.2, x + 1.2, VIA_Y + 1.2))
        # catenary masts
        v.cyl(x + 10, VIA_Y + 4.0, VIA_Z + 1.0, VIA_Z + 6.2, .09, steel, n=6)
        v.rod((x + 10, VIA_Y + 4.0, VIA_Z + 5.9), (x + 10, VIA_Y - 2.4, VIA_Z + 5.9), .05, steel, n=4)
        x += 20
    for sy in (-1, 1):
        v.rod((-300, VIA_Y + sy * 2.0, VIA_Z + 5.6), (300, VIA_Y + sy * 2.0, VIA_Z + 5.6), .02, steel, n=3)
    return v.finish(tr_coll("Set"))


# ---------------------------------------------------------------------------------------------- street furniture

def prop(name, x, y, rz=0.0, radius=.45, s=1.0):
    """Stands a prop on the pavement, if the spot is free; returns it or None."""
    if on_road(x, y, radius * .5 + .15) or in_lot(x, y, radius * .3):
        return None
    for tag, n, px, py, r in PLACED:
        if (px - x) ** 2 + (py - y) ** 2 < (r + radius) ** 2:
            return None
    return inst(name, x, y, KERB, rz, s, tag="prop", radius=radius)


def build_props():
    rnd = random.Random(8)
    # the kerb line of the avenue: lamps, trees, and between them the small things, both sides
    for side in (1, -1):
        rz = 0 if side > 0 else 180
        x = AVE_X0 + 14
        k = 0
        while x < AVE_X1 - 4:
            prop("lamp_post", x, side * (AVE_HALF + .9), rz, .3)
            prop("tree_oak" if (k + (side > 0)) % 3 else "tree_cherry", x + 12, side * (AVE_HALF + 1.7), rnd.uniform(0, 360), 1.1)
            small = rnd.choice(("parking_meter", "trash_bin", "fire_hydrant", "bollard", "parking_meter", "newspaper_box", "mailbox", "planter"))
            prop(small, x + 5.5 + rnd.uniform(-.6, .6), side * (AVE_HALF + (.75 if small in ("parking_meter", "fire_hydrant", "bollard") else 1.2)), rz, .5)
            if k % 3 == 1:
                prop("park_bench", x + 18.5, side * (AVE_HALF + 1.5), rz, 1.1)
            if k % 5 == 2:
                prop("bike_rack", x + 17.5, side * (AVE_HALF + 1.3), rz + 90, 1.2)
            x += 24
            k += 1
    # signals at the crossings: one on each corner the traffic meets first, its arm over the lanes it governs
    for c in CROSS:
        prop("traffic_light", c - CROSS_HALF - 1.0, -AVE_HALF - .9, 180, .35)
        prop("traffic_light", c + CROSS_HALF + 1.0, AVE_HALF + .9, 0, .35)
        prop("street_sign", c + CROSS_HALF + .8, -AVE_HALF - .8, 180, .3)
        prop("street_sign", c - CROSS_HALF - .8, AVE_HALF + .8, 0, .3)
        for sy in (-1, 1):
            y = sy * 30
            while abs(y) < CROSS_Y - 10:
                prop("lamp_post", c + (CROSS_HALF + .9) * sy, y, 90 * sy, .3)
                y += sy * 30
    # set pieces
    prop("bus_shelter", 52, AVE_HALF + 2.6, 0, 2.4)
    prop("bus_shelter", -40, -AVE_HALF - 2.6, 180, 2.4)
    prop("phone_booth", 37.5, AVE_HALF + 3.9, 0, .7)
    prop("vending_machine", 60.2, FRONT - .55, 0, .9)
    prop("atm", -36.4, FRONT - .45, 0, .7)
    prop("hotdog_cart", AVE_X1 + 26, -13, 200, 1.4)
    prop("dumpster", 84.2 + 3.6, 30, 90, 1.4)
    prop("market_stall", AVE_X1 + 30, 15, 160, 1.6)
    # the square: a fountain, lamps and benches round it, trees along its edges
    prop("fountain", AVE_X1 + 14, 0, 0, 4.2)
    for k in range(8):
        ang = k * math.pi / 4 + math.pi / 8
        prop("park_lamp" if k % 2 == 0 else "park_bench", AVE_X1 + 14 + 7.5 * math.cos(ang), 7.5 * math.sin(ang), math.degrees(ang) + 90, .8)
    for sy in (-1, 1):
        for k in range(5):
            prop("tree_ginkgo", AVE_X1 + 4 + k * 5.2, sy * 26, k * 50, 1.1)
            prop("planter", AVE_X1 + 6.5 + k * 5.2, sy * 20, 0, .7)


def build_lamp_light():
    """Real light under the avenue's lamps, for the night: one lamp shared by all, so one key turns them all on."""
    c = tr_clear("Lamps")
    lamp = bpy.data.lights.get("TR Lamp") or bpy.data.lights.new("TR Lamp", 'POINT')
    lamp.energy = 0
    lamp.color = (1, .82, .55)
    lamp.shadow_soft_size = .4
    lamp.use_shadow = False
    n = 0
    for tag, name, x, y, r in PLACED:
        if name == "lamp_post" and abs(y) < 9 and -130 < x < 150:
            ob = bpy.data.objects.new("TR lamp light", lamp)
            c.objects.link(ob)
            ob.location = (x, y - math.copysign(1.6, y), 5.6)
            n += 1
    return n


def check_set():
    """Everything in its place: returns the list of what isn't (empty when the set is sound)."""
    bad = []
    for tag, n, x, y, r in PLACED:
        if on_road(x, y, .15):
            bad.append("{:s} at {:.1f},{:.1f} stands in a road".format(n, x, y))
        if in_lot(x, y, -.2):
            bad.append("{:s} at {:.1f},{:.1f} stands in a building".format(n, x, y))
    for i, (ta, na, xa, ya, ra) in enumerate(PLACED):
        for tb, nb, xb, yb, rb in PLACED[i + 1:]:
            if (xa - xb) ** 2 + (ya - yb) ** 2 < (min(ra, rb) * .9) ** 2:
                bad.append("{:s} and {:s} share a spot at {:.1f},{:.1f}".format(na, nb, xa, ya))
    for ri, x0, y0, x1, y1 in CROSSINGS:
        own = ROADS[ri]
        if not (own[1] - .01 <= x0 and x1 <= own[3] + .01 and own[2] - .01 <= y0 and y1 <= own[4] + .01):
            bad.append("a crossing leaves its road at {:.1f},{:.1f}".format(x0, y0))
        for rj, other in enumerate(ROADS):
            if rj != ri and x0 < other[3] and other[1] < x1 and y0 < other[4] and other[2] < y1:
                bad.append("a crossing lies across the {:s} at {:.1f},{:.1f}".format(other[0], x0, y0))
        # pavement at both ends: just past each kerb is neither asphalt nor a wall
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        ends = ((cx, own[2] - 1.0), (cx, own[4] + 1.0)) if ri == 0 else ((own[1] - 1.0, cy), (own[3] + 1.0, cy))
        for ex, ey in ends:
            if on_road(ex, ey) or in_lot(ex, ey):
                bad.append("a crossing at {:.1f},{:.1f} has no pavement to step onto".format(cx, cy))
    for name, x0, y0, x1, y1 in LOTS:
        if name == "pier":
            continue
        for _, rx0, ry0, rx1, ry1 in ROADS:
            if x0 < rx1 and rx0 < x1 and y0 < ry1 and ry0 < y1:
                bad.append("{:s} at {:.0f},{:.0f} stands in a road".format(name, (x0 + x1) / 2, (y0 + y1) / 2))
    return bad


def build_set():
    del PLACED[:]
    del LOTS[:]
    del CROSSINGS[:]
    tr_clear("Set")
    build_ground()
    made = build_blocks()
    build_viaduct()
    build_props()
    build_lamp_light()
    scn = tr_scene()
    scn["tr_lots"] = [v for l in LOTS for v in l[1:]]
    scn["tr_hero_shop"] = list(made["hero"][0].location)
    scn["tr_hero_row"] = [v for ob in made["hero"] for v in (ob.location.x, ob.location.y)]
    bad = check_set()
    # people keep to the walking line: nothing may stand on it
    for tag, n, x, y, r in PLACED:
        if abs(abs(y) - WALK_Y) < r + .35 and AVE_X0 < x < AVE_X1:
            bad.append("{:s} at {:.1f},{:.1f} blocks the walking line".format(n, x, y))
    return {"buildings": len(LOTS), "props": len(PLACED), "crossings": len(CROSSINGS), "out_of_place": bad}


if "HC_TR_NOBUILD" not in globals():
    result = build_set()
