"""One hero building for each of the game's architecture styles (render/BuildingPainter.kt `Arch`). Needs kit.py in front.

A building stands on its lot with the street front at -Y: x runs along the front, the footprint is centred on the
origin. Floors are about 3.3 m, as in the game.
"""

BUILDINGS = []


def building(w, d):
    def deco(fn):
        BUILDINGS.append((fn.__name__, w, d, fn))
        return fn
    return deco


BRICK = M(0x9C4A3A, "BRICK")
BRICK_L = M(0xB5553C, "BRICK")
BRICK_D = M(0x7A3B2E, "BRICK")
STONE = M(0xD9CFBF, "STONE")
STONE_L = M(0xE6DDD0, "STONE")
STONE_D = M(0xBFB6A8, "STONE")
STONE_X = M(0x8E8676, "STONE")
BROWNSTONE = M(0x6B4436, "STONE")
BROWNSTONE_D = M(0x5E3D31, "STONE")
PLASTER = M(0xE8E6E1, "PLASTER")
PLASTER_W = M(0xF0EDE6, "PLASTER")
CONCRETE = M(0xB9BDC2, "CONCRETE")
CONCRETE_D = M(0x8E9399, "CONCRETE")
B_GLASS = M(0x3C5266, "GLASS", alpha=1)
B_GLASS_L = M(0x4F7FA6, "GLASS", alpha=1)
B_GLASS_S = M(0x9FC4DD, "GLASS", alpha=1)
ROOF = M(0x3A3F45, "ROOF")
ROOF_L = M(0x4C535B, "ROOF")
TILE_RED = M(0xB0563C, "ROOF")
TILE_RED_D = M(0xA34B34, "ROOF")
GIWA = M(0x3F444A, "ROOF")
SIDING = M(0x8A9199, "METAL")
SIDING_D = M(0x7E878F, "METAL")
B_IRON = M(0x2A2D31, "METAL")
B_WOOD = M(0x5A3A26, "WOOD")
BRASS = M(0xD4AF37, "METAL")
SHUTTER = M(0x9AA3AA, "METAL")
WARM = E(0xFFE3A0, 1.6)


# ---------------------------------------------------------------------------------------------- parts

def walls(a, w, d, sides="frbl"):
    """Yields (side, wall length, frame) for each wall. Inside the frame x runs along the wall, y = 0 is the wall
    and -y points out of the building."""
    spec = {"f": (0, -d / 2, 0, w), "r": (w / 2, 0, 90, d), "b": (0, d / 2, 180, w), "l": (-w / 2, 0, 270, d)}
    for s in sides:
        x, y, rz, L = spec[s]
        yield s, L, a.at(x, y, 0, rz=rz)


def win(a, u, z, w, h, glass=B_GLASS, frame=None, sill=None, proud=.05):
    if frame is not None:
        a.box(u - w / 2 - .09, -proud + .02, z - .09, u + w / 2 + .09, .02, z + h + .09, frame)
    a.box(u - w / 2, -proud, z, u + w / 2, .02, z + h, glass)
    if sill is not None:
        a.box(u - w / 2 - .14, -proud - .1, z - .16, u + w / 2 + .14, 0, z - .08, sill)


def win_row(a, L, z, n, w, h, margin=1.0, **kw):
    span = L - 2 * margin
    for i in range(n):
        win(a, -span / 2 + span * (i + .5) / n, z, w, h, **kw)


def parapet(a, w, d, z, h=.8, t=.3, m=BRICK, cap=STONE):
    for x0, y0, x1, y1 in ((-w / 2, -d / 2, w / 2, -d / 2 + t), (-w / 2, d / 2 - t, w / 2, d / 2), (-w / 2, -d / 2 + t, -w / 2 + t, d / 2 - t),
                           (w / 2 - t, -d / 2 + t, w / 2, d / 2 - t)):
        a.box(x0, y0, z, x1, y1, z + h, m)
    if cap is not None:
        a.box(-w / 2 - .06, -d / 2 - .06, z + h, w / 2 + .06, -d / 2 + t + .06, z + h + .1, cap)
        a.box(-w / 2 - .06, d / 2 - t - .06, z + h, w / 2 + .06, d / 2 + .06, z + h + .1, cap)
        a.box(-w / 2 - .06, -d / 2 + t + .06, z + h, -w / 2 + t + .06, d / 2 - t - .06, z + h + .1, cap)
        a.box(w / 2 - t - .06, -d / 2 + t + .06, z + h, w / 2 + .06, d / 2 - t - .06, z + h + .1, cap)
    a.box(-w / 2 + t, -d / 2 + t, z, w / 2 - t, d / 2 - t, z + .06, ROOF)


def ac_unit(a, x, y, z, s=1.0):
    a.box(x - .6 * s, y - .45 * s, z + .12, x + .6 * s, y + .45 * s, z + .9 * s, M(0xC9CED6, "METAL"), bevel=.03)
    a.cyl(x, y, z + .9 * s, z + .93 * s, .34 * s, B_IRON, n=10)
    for sx in (-1, 1):
        a.box(x + sx * .45 * s - .05, y - .4 * s, z, x + sx * .45 * s + .05, y + .4 * s, z + .12, B_IRON)


def water_tower(a, x, y, z, r=1.3):
    for sx in (-1, 1):
        for sy in (-1, 1):
            a.rod((x + sx * r * .75, y + sy * r * .75, z), (x + sx * r * .6, y + sy * r * .6, z + 2.4), .07, B_IRON, n=4)
    for k in range(4):
        ang = math.radians(k * 90 + 45)
        ang2 = math.radians(k * 90 + 135)
        a.rod((x + r * 1.05 * math.cos(ang), y + r * 1.05 * math.sin(ang), z + .1), (x + r * .85 * math.cos(ang2), y + r * .85 * math.sin(ang2), z + 2.3), .03, B_IRON, n=3)
    a.cyl(x, y, z + 2.4, z + 2.55, r * 1.02, B_IRON, n=10)
    a.cyl(x, y, z + 2.55, z + 5.0, r, M(0x7A5A44, "WOOD"), n=12)
    for h in (2.9, 3.8, 4.7):
        a.torus(x, y, z + h, r + .01, .035, B_IRON, n=12, k=4)
    a.lathe(x, y, [(r * 1.08, 0), (0, .9)], M(0x5E4636, "WOOD"), n=12, z=z + 5.0)
    a.cyl(x, y, z + 5.0, z + 5.02, r * 1.08, M(0x5E4636, "WOOD"), n=12)


def bulkhead(a, x, y, z, m=BRICK, w=3.0, d=3.4, h=2.7):
    a.box(x - w / 2, y - d / 2, z, x + w / 2, y + d / 2, z + h, m)
    a.box(x - w / 2 - .1, y - d / 2 - .1, z + h, x + w / 2 + .1, y + d / 2 + .1, z + h + .12, STONE_D)
    a.box(x - .5, y - d / 2 - .04, z, x + .5, y - d / 2, z + 2.1, M(0x4A5560, "METAL"))


def awning(a, u0, u1, z, out=1.3, drop=.75, cols=(RED, WHITE), stripe=.5, valance=.22):
    n = max(1, round((u1 - u0) / stripe))
    w = (u1 - u0) / n
    for k in range(n):
        a.extrude([(0, z), (-out, z - drop), (-out, z - drop - valance), (-out + .05, z - drop - valance), (-out + .05, z - drop - .06), (0, z - .1)],
                  u0 + k * w, u0 + (k + 1) * w, cols[k % len(cols)], axis='x')
    for u in (u0 + .05, u1 - .05):
        a.rod((u, 0, z - drop - .3), (u, -out + .1, z - drop - .08), .025, B_IRON, n=4)


def sign(a, u0, u1, z0, z1, col, text=WHITE, proud=.16, bars=1):
    a.box(u0, -proud, z0, u1, 0, z1, col, bevel=.03)
    h = z1 - z0
    w = u1 - u0
    for k in range(bars):
        zz = z0 + h * (.5 if bars == 1 else (.62 - k * .34))
        ww = w * (.62 if k == 0 else .4)
        a.box((u0 + u1) / 2 - ww / 2, -proud - .02, zz - h * (.16 if bars == 1 else .1), (u0 + u1) / 2 + ww / 2, -proud, zz + h * (.16 if bars == 1 else .1), text)


def storefront(a, L, h=3.9, frame=None, sign_col=None, awn=None, door_u=0.0, base=None, name_bars=1):
    """A shop front along a wall: stall riser, big panes either side of a glazed door, sign band and awning."""
    frame = frame or M(0x2E3238, "PAINT")
    base = base or frame
    a.box(-L / 2, -.08, 0, L / 2, 0, h, frame)
    a.box(-L / 2, -.14, 0, L / 2, 0, .55, base)
    top = h - 1.1
    if door_u is None:
        panes = ((-L / 2 + .4, 0 - .05), (.05, L / 2 - .4))
    else:
        panes = ((-L / 2 + .4, door_u - .9), (door_u + .9, L / 2 - .4))
    for u0, u1 in panes:
        if u1 - u0 > .8:
            a.box(u0, -.12, .62, u1, -.04, top, B_GLASS_S)
            a.box((u0 + u1) / 2 - .04, -.15, .62, (u0 + u1) / 2 + .04, -.04, top, frame)
            a.box(u0 + .2, -.3, .62, u1 - .2, -.12, .72, M(0xF5F2EA, "PAINT"))
    if door_u is not None:
        a.box(door_u - .7, -.12, 0, door_u + .7, -.04, top, M(0x6B4A32, "WOOD"))
        a.box(door_u - .55, -.15, .25, door_u + .55, -.1, top - .2, B_GLASS_S)
        a.box(door_u + .42, -.2, 1.0, door_u + .47, -.15, 1.5, BRASS)
        a.box(door_u - .8, -.5, 0, door_u + .8, 0, .08, STONE_D)
    if sign_col is not None:
        sign(a, -L / 2 + .3, L / 2 - .3, h - .95, h - .1, sign_col, bars=name_bars)
    if awn is not None:
        awning(a, -L / 2 + .2, L / 2 - .2, top + .15, cols=awn)


def fire_escape(a, u0, u1, zs, drop_to=2.8):
    for i, z in enumerate(zs):
        a.box(u0, -1.15, z - .07, u1, 0, z, B_IRON)
        a.box(u0, -1.15, z + .95, u1, -1.1, z + 1.0, B_IRON)
        for u in (u0, u1 - .05):
            a.box(u, -1.15, z + .95, u + .05, 0, z + 1.0, B_IRON)
        n = int((u1 - u0) / .45)
        for k in range(n + 1):
            u = u0 + (u1 - u0 - .04) * k / n
            a.box(u, -1.14, z, u + .04, -1.1, z + .95, B_IRON)
        if i + 1 < len(zs):
            for dy in (-.95, -.45):
                a.rod((u0 + .5, dy, z), (u1 - .5, dy, zs[i + 1] - .07), .035, B_IRON, n=4)
            steps = 7
            for k in range(1, steps):
                t = k / steps
                uu = u0 + .5 + (u1 - u0 - 1.0) * t
                zz = z + (zs[i + 1] - .07 - z) * t
                a.box(uu - .12, -.95, zz - .02, uu + .12, -.45, zz + .02, B_IRON)
    z = zs[0]
    for du in (.2, .65):
        a.rod((u1 - du, -1.05, z), (u1 - du, -1.05, drop_to), .025, B_IRON, n=4)
    for k in range(6):
        zz = drop_to + (z - drop_to) * (k + .5) / 6
        a.box(u1 - .65, -1.07, zz, u1 - .2, -1.03, zz + .03, B_IRON)


def hip_roof(a, x0, y0, x1, y1, z, h, m, ridge=None):
    """A hipped roof over a rectangle with the ridge along x (a pyramid when ridge is 0)."""
    bm = a.bm
    cx = (x0 + x1) / 2
    cy = (y0 + y1) / 2
    if ridge is None:
        ridge = max(0.0, (x1 - x0) - (y1 - y0))
    v = [bm.verts.new(p) for p in ((x0, y0, z), (x1, y0, z), (x1, y1, z), (x0, y1, z))]
    if ridge > 1e-3:
        r0 = bm.verts.new((cx - ridge / 2, cy, z + h))
        r1 = bm.verts.new((cx + ridge / 2, cy, z + h))
        faces = [(v[0], v[1], r1, r0), (v[1], v[2], r1), (v[2], v[3], r0, r1), (v[3], v[0], r0)]
    else:
        t = bm.verts.new((cx, cy, z + h))
        faces = [(v[0], v[1], t), (v[1], v[2], t), (v[2], v[3], t), (v[3], v[0], t)]
    faces.append((v[3], v[2], v[1], v[0]))
    mi = a._mi(m)
    for f in faces:
        bm.faces.new(f).material_index = mi


# ---------------------------------------------------------------------------------------------- New York

@building(12, 16)
def walkup(a):
    """Brick tenement: shop on the ground floor, fire escape, cornice, water tower."""
    W, D = 12.0, 16.0
    g, fh, n = 4.2, 3.3, 4
    H = g + fh * n
    a.box(-W / 2, -D / 2, 0, W / 2, D / 2, H, BRICK)
    for s, L, fr in walls(a, W, D, "fbr"):
        with fr:
            if s == "f":
                storefront(a, L, 4.0, sign_col=M(0x1F6F43, "PAINT"), awn=(M(0x1F6F43, "FABRIC"), F(0xF5F2EA)), door_u=-2.5)
                a.box(-L / 2 - .05, -.16, 4.0, L / 2 + .05, 0, 4.3, STONE)
                for k in range(n):
                    win_row(a, L, g + k * fh + .95, 4, 1.25, 1.85, margin=.9, frame=STONE, sill=STONE)
                fire_escape(a, -2.35, 2.35, [g + k * fh + .7 for k in range(n)])
                a.box(-L / 2 - .25, -.6, H - .45, L / 2 + .25, 0, H + .3, M(0x4A3A32, "STONE"), bevel=.06)
                for k in range(13):
                    u = -L / 2 + .3 + k * (L - .6) / 12
                    a.box(u - .12, -.5, H - .95, u + .12, 0, H - .45, M(0x4A3A32, "STONE"))
            elif s == "b":
                for k in range(n + 1):
                    win_row(a, L, (1.2 if k == 0 else g + (k - 1) * fh + .95), 4, 1.1, 1.6, margin=.9, sill=STONE_D)
                a.box(-.5, -.06, 0, .5, 0, 2.2, M(0x4A5560, "METAL"))
            else:
                # a faded painted advert on the blank party wall
                a.box(-5.5, -.03, 7.0, 3.5, 0, 14.5, M(0xD9C7A3, "PAINT"))
                a.box(-5.0, -.05, 11.5, 3.0, -.03, 13.8, M(0xB8452F, "PAINT"))
                a.box(-4.2, -.07, 12.2, 2.2, -.05, 13.1, M(0xD9C7A3, "PAINT"))
                a.box(-5.0, -.05, 7.6, -1.5, -.03, 10.8, M(0x2F5D8C, "PAINT"))
                a.box(-.8, -.05, 8.6, 3.0, -.03, 9.2, M(0x3A3F45, "PAINT"))
                a.box(-.8, -.05, 9.7, 2.0, -.03, 10.3, M(0x3A3F45, "PAINT"))
    parapet(a, W, D, H, h=.8)
    water_tower(a, 2.6, 3.5, H + .06)
    bulkhead(a, -3.2, 4.8, H + .06)
    ac_unit(a, -2.5, -3.5, H + .06)
    ac_unit(a, 0, -4.5, H + .06, s=.8)
    for x, y in ((3.5, -4), (4.2, -1), (-.5, 1)):
        a.cyl(x, y, H, H + 1.0, .12, B_IRON, n=6)
        a.lathe(x, y, [(.2, 0), (0, .2)], B_IRON, n=6, z=H + 1.0)


@building(7, 14)
def brownstone(a):
    """Row house: high stoop, bay window, heavy cornice, areaway railings."""
    W, D = 7.0, 14.0
    base, fh, n = 1.5, 3.4, 3
    H = base + fh * n + .5
    a.box(-W / 2, -D / 2, 0, W / 2, D / 2, H, BROWNSTONE)
    for s, L, fr in walls(a, W, D, "fb"):
        with fr:
            if s == "b":
                for k in range(n):
                    win_row(a, L, base + k * fh + .9, 3, 1.1, 1.9, margin=.6, sill=BROWNSTONE_D)
                continue
            a.box(-L / 2 - .04, -.1, 0, L / 2 + .04, 0, base, BROWNSTONE_D)
            a.box(-L / 2 - .06, -.16, base - .2, L / 2 + .06, 0, base, STONE_X)
            # stoop on the left: steps, cheek walls, newel posts
            du = -2.0
            for k in range(8):
                a.box(du - .85, -.55 - (8 - k) * .36, 0, du + .85, -.55 - (7 - k) * .36, (k + 1) * base / 8, BROWNSTONE_D)
            a.box(du - .85, -.55, 0, du + .85, 0, base, BROWNSTONE_D)
            for sx in (-1, 1):
                a.extrude([(0, 0), (-3.43, 0), (-3.43, .5), (-.55, base + .5), (0, base + .5)], du + sx * 1.0 - .15, du + sx * 1.0 + .15, BROWNSTONE, axis='x')
                a.box(du + sx * 1.0 - .2, -3.63, 0, du + sx * 1.0 + .2, -3.23, 1.1, BROWNSTONE, bevel=.03)
                a.sphere(du + sx * 1.0, -3.43, 1.22, .17, BROWNSTONE_D, seg=8, rings=5)
            # arched double door with a transom
            a.box(du - .95, -.2, base, du + .95, 0, base + 3.1, BROWNSTONE_D)
            a.box(du - .7, -.24, base, du + .7, -.1, base + 2.3, M(0x3E2723, "WOOD"))
            a.rod((du, -.1, base + 2.3), (du, -.24, base + 2.3), .7, M(0x3E2723, "WOOD"), n=16)
            a.rod((du, -.24, base + 2.3), (du, -.27, base + 2.3), .5, B_GLASS_S, n=14)
            a.box(du - .02, -.27, base, du + .02, -.24, base + 2.3, BROWNSTONE_D)
            for sx in (-1, 1):
                a.box(du + sx * .35 - .22, -.27, base + .9, du + sx * .35 + .22, -.24, base + 2.0, B_GLASS_S)
            # the bay: three-sided, full height, with a window in each face
            u0, u1 = .1, 3.3
            a.extrude([(u0, 0), (u0 + .7, -.9), (u1 - .7, -.9), (u1, 0)], .3, H - .6, BROWNSTONE, axis='z')
            a.extrude([(u0 - .1, 0), (u0 + .65, -1.0), (u1 - .65, -1.0), (u1 + .1, 0)], H - .6, H - .4, BROWNSTONE_D, axis='z')
            for k in range(n):
                z = base + k * fh + .85
                a.box(u0 + .9, -.96, z, u1 - .9, -.88, z + 2.0, B_GLASS)
                a.box(u0 + .8, -1.02, z - .16, u1 - .8, -.9, z - .06, STONE_X)
                a.box(u0 + .8, -1.0, z + 2.02, u1 - .8, -.9, z + 2.2, BROWNSTONE_D)
                for su, ang in ((u0 + .35, 52.1), (u1 - .35, -52.1)):
                    with a.at(su, -.45, 0, rz=ang):
                        a.box(-.33, -.06, z, .33, .04, z + 2.0, B_GLASS)
                if k > 0:
                    win(a, du, z, 1.15, 2.0, frame=BROWNSTONE_D, sill=STONE_X)
                    a.box(du - .8, -.22, z + 2.15, du + .8, 0, z + 2.38, BROWNSTONE_D)
            a.box(u0 + .9, -.96, .45, u1 - .9, -.88, 1.15, B_GLASS)
            for k in range(5):
                a.box(u0 + 1.0 + k * .3, -.99, .45, u0 + 1.03 + k * .3, -.96, 1.15, B_IRON)
            # cornice with brackets
            a.box(-L / 2 - .15, -.75, H - .35, L / 2 + .15, 0, H + .25, M(0x4A3229, "STONE"), bevel=.06)
            for k in range(8):
                u = -L / 2 + .35 + k * (L - .7) / 7
                a.extrude([(0, H - .35), (-.6, H - .35), (-.5, H - .65), (0, H - 1.1)], u - .1, u + .1, M(0x4A3229, "STONE"), axis='x')
            # areaway railing
            for k in range(13):
                u = -.6 + k * .33
                a.box(u, -3.45, 0, u + .04, -3.41, 1.0, B_IRON)
            a.box(-.6, -3.46, .92, 3.4, -3.4, .98, B_IRON)
            a.box(3.36, -3.45, 0, 3.4, 0, .98, B_IRON)
    parapet(a, W, D, H, h=.4, m=BROWNSTONE, cap=None)
    for x, y in ((-2.6, 2.0), (2.6, -2.5)):
        a.box(x - .45, y - .7, H, x + .45, y + .7, H + 1.6, BRICK_D)
        a.box(x - .55, y - .8, H + 1.6, x + .55, y + .8, H + 1.75, STONE_X)
        for dy in (-.35, .35):
            a.cyl(x, y + dy, H + 1.75, H + 2.1, .14, TERRACOTTA, n=6)
    a.extrude([(-1.2, H + .06), (1.2, H + .06), (0, H + .9)], 1.0, 3.5, B_GLASS_S, axis='y')


@building(22, 22)
def deco(a):
    """Art deco tower: setbacks, vertical piers, gilded crown and spire."""
    tiers = ((22.0, 0.0, 32.0), (17.0, 32.0, 50.0), (12.0, 50.0, 62.0), (7.5, 62.0, 68.0))
    gold = M(0xC9A84A, "METAL")
    for ti, (w, z0, z1) in enumerate(tiers):
        a.box(-w / 2, -w / 2, z0, w / 2, w / 2, z1, STONE_L)
        npier = (9, 7, 5, 3)[ti]
        for s, L, fr in walls(a, w, w):
            with fr:
                step = (L - .8) / (npier - 1)
                lo = z0 + (5.2 if ti == 0 else .6)
                a.box(-L / 2 + .6, -.04, lo, L / 2 - .6, 0, z1 - 1.2, B_GLASS)
                floors = int((z1 - 1.2 - lo) / 3.3)
                for k in range(1, floors + 1):
                    zz = lo + k * (z1 - 1.2 - lo) / (floors + 1) if floors else lo
                    a.box(-L / 2 + .6, -.1, zz - .55, L / 2 - .6, 0, zz + .55, STONE)
                for k in range(npier):
                    u = -L / 2 + .4 + k * step
                    a.box(u - .32, -.28, z0, u + .32, 0, z1 + (.9 if ti < 3 else .3), STONE_L)
                    a.extrude([(u - .32, z1 + (.9 if ti < 3 else .3)), (u + .32, z1 + (.9 if ti < 3 else .3)), (u, z1 + (1.7 if ti < 3 else .9))],
                              -.28, 0, gold, axis='y')
                a.box(-L / 2, -.12, z1 - 1.2, L / 2, 0, z1, STONE)
                for k in range(npier - 1):
                    u = -L / 2 + .4 + (k + .5) * step
                    a.extrude([(u - .5, z1 - 1.1), (u + .5, z1 - 1.1), (u, z1 - .2)], -.16, -.12, gold, axis='y')
                if ti == 0:
                    a.box(-L / 2, -.14, 0, L / 2, 0, 5.2, M(0x4A4640, "STONE"))
                    a.box(-L / 2, -.2, 4.7, L / 2, 0, 5.2, gold)
                    if s == "f":
                        # three-storey portal
                        a.box(-3.2, -.5, 0, 3.2, 0, 9.5, STONE)
                        a.extrude([(-3.2, 9.5), (3.2, 9.5), (2.2, 10.6), (-2.2, 10.6)], -.5, 0, STONE, axis='y')
                        a.box(-2.4, -.56, 0, 2.4, -.5, 8.6, gold)
                        a.box(-2.1, -.6, 0, 2.1, -.56, 8.3, B_GLASS_L)
                        for k in range(5):
                            a.box(-2.1 + k * 1.05 - .05, -.64, 0, -2.1 + k * 1.05 + .05, -.6, 8.3, gold)
                        a.box(-2.1, -.64, 3.2, 2.1, -.6, 3.5, gold)
                        a.box(-3.6, -1.6, 3.5, 3.6, -.5, 3.8, M(0x4A4640, "STONE"))
                        a.box(-3.6, -1.62, 3.5, 3.6, -1.6, 3.8, gold)
                    else:
                        for k in range(npier - 1):
                            u = -L / 2 + .4 + (k + .5) * step
                            a.box(u - .8, -.2, .6, u + .8, -.14, 3.9, B_GLASS_L)
    # crown: stepped fins round an octagonal lantern, then the needle
    for k in range(4):
        with a.at(0, 0, 0, rz=k * 90):
            for j in range(3):
                a.extrude([(-1.6 + j * .5, 68.0), (1.6 - j * .5, 68.0), (1.6 - j * .5, 70.5 + j * 1.6), (0, 72.0 + j * 1.8), (-1.6 + j * .5, 70.5 + j * 1.6)],
                          -3.75 + j * .55, -3.5 + j * .55, (gold, STONE_L, gold)[j], axis='y')
    a.lathe(0, 0, [(2.6, 0), (2.4, 5.5), (1.5, 8.0), (.7, 9.0), (.5, 11.0), (0, 11.0)], STONE_L, n=8, z=68.0, smooth=False)
    a.lathe(0, 0, [(.5, 0), (.34, 3.0), (.12, 9.0), (0, 14.0)], gold, n=8, z=79.0)
    a.sphere(0, 0, 82.0, .5, gold, seg=8, rings=5)


@building(24, 24)
def glass_tower(a):
    """Curtain-wall skyscraper: lobby podium, mullion grid, raked crown."""
    W, H = 24.0, 82.0
    lobby = 7.0
    steel = M(0xC9D1DA, "METAL")
    a.box(-W / 2 + .8, -W / 2 + .8, 0, W / 2 - .8, W / 2 - .8, lobby, B_GLASS_S)
    a.box(-W / 2, -W / 2, lobby, W / 2, W / 2, H, B_GLASS_L)
    a.box(-W / 2 - .2, -W / 2 - .2, lobby - .5, W / 2 + .2, W / 2 + .2, lobby + .3, steel)
    floors = int((H - lobby) / 3.4)
    for s, L, fr in walls(a, W, W):
        with fr:
            for k in range(1, floors + 1):
                z = lobby + k * (H - lobby) / (floors + 1)
                a.box(-L / 2, -.06, z - .32, L / 2, 0, z + .32, M(0x3E6E8E, "GLASS", alpha=1))
            for k in range(13):
                u = -L / 2 + k * L / 12
                a.box(u - .09, -.2, lobby + .3, u + .09, 0, H, steel)
            # lobby: columns, a full-width canopy on the street side
            for k in range(5):
                u = -L / 2 + 1.2 + k * (L - 2.4) / 4
                a.box(u - .5, -.1, 0, u + .5, .9, lobby - .5, M(0xE9EDF2, "STONE"))
            if s == "f":
                a.box(-4.5, -3.2, 3.9, 4.5, .8, 4.2, steel, bevel=.05)
                a.box(-4.5, -3.22, 3.95, 4.5, -3.2, 4.15, WARM)
                for u in (-3.9, 3.9):
                    a.rod((u, -3.0, 4.2), (u, .7, 6.2), .04, steel, n=4)
                a.box(-1.6, .78, 0, 1.6, .84, 3.3, steel)
                a.box(-1.45, .74, 0, 1.45, .78, 3.15, M(0x6A90A8, "GLASS", alpha=1))
                a.box(-.03, .7, 0, .03, .74, 3.15, steel)
    # crown: a raked glass wedge over a louvred plant floor
    a.box(-W / 2 + 1.0, -W / 2 + 1.0, H, W / 2 - 1.0, W / 2 - 1.0, H + 3.0, M(0x46647E, "GLASS", alpha=1))
    for s, L, fr in walls(a, W - 2.0, W - 2.0):
        with fr:
            for k in range(6):
                a.box(-L / 2, -.08, H + .3 + k * .45, L / 2, 0, H + .5 + k * .45, steel)
    a.extrude([(-W / 2, H + 3.0), (W / 2, H + 3.0), (W / 2, H + 4.0), (-W / 2, H + 10.0)], -W / 2, W / 2, B_GLASS_L, axis='y')
    for k in range(13):
        u = -W / 2 + k * W / 12
        a.rod((u, -W / 2 - .05, H + 10.0), (u, W / 2 + .05, H + 4.0), .09, steel, n=4)
    a.cyl(-W / 2 + 2.0, -W / 2 + 2.0, H + 9.0, H + 22.0, .14, steel, n=6, r2=.05)
    a.sphere(-W / 2 + 2.0, -W / 2 + 2.0, H + 22.0, .22, E(0xFF2E2E, 4.0), seg=6, rings=4)


@building(22, 18)
def classic(a):
    """Stone office block: rusticated base, giant-order columns and pediment, cornice, attic."""
    W, D = 22.0, 18.0
    g, fh, n = 5.0, 3.5, 5
    H = g + fh * n
    a.box(-W / 2, -D / 2, 0, W / 2, D / 2, H, STONE)
    for s, L, fr in walls(a, W, D):
        with fr:
            a.box(-L / 2 - .12, -.2, 0, L / 2 + .12, 0, g, STONE_D)
            for k in range(7):
                a.box(-L / 2 - .14, -.24, .25 + k * .68, L / 2 + .14, 0, .75 + k * .68, STONE_D, bevel=.03)
            a.box(-L / 2 - .25, -.4, g - .25, L / 2 + .25, 0, g + .25, STONE_L, bevel=.05)
            nw = 7 if L > 20 else 6
            for k in range(n):
                z = g + k * fh + 1.0
                top = k == n - 1
                win_row(a, L, z, nw, 1.3, 1.4 if top else 2.1, margin=1.3, frame=STONE_L, sill=STONE_L)
            for k in range(nw):
                u = -(L - 2.6) / 2 + (L - 2.6) * (k + .5) / nw
                if s == "f" and abs(u) < 5:
                    continue
                a.box(u - .7, -.3, 1.2, u + .7, -.18, 3.9, B_GLASS)
                a.rod((u, -.18, 3.9), (u, -.3, 3.9), .7, B_GLASS, n=12)
            a.box(-L / 2 - .3, -.5, g + fh * (n - 1) - .1, L / 2 + .3, 0, g + fh * (n - 1) + .35, STONE_L, bevel=.06)
            a.box(-L / 2 - .5, -.8, H - .2, L / 2 + .5, 0, H + .5, STONE_L, bevel=.1)
            for k in range(int(L / 1.5)):
                u = -L / 2 + .4 + k * 1.5
                a.box(u, -.7, H - .55, u + .6, 0, H - .2, STONE_L)
            if s == "f":
                # portico: steps, six columns two storeys tall, entablature, pediment
                for k in range(5):
                    a.box(-6.2 + k * .0, -3.2 + k * .36, 0, 6.2, -2.84 + k * .36, 1.0 - (4 - k) * .2, STONE_L)
                a.box(-6.2, -1.4, 0, 6.2, 0, 1.0, STONE_L)
                for k in range(6):
                    u = -5.2 + k * 2.08
                    a.box(u - .55, -1.35, 1.0, u + .55, -.25, 1.35, STONE_L)
                    a.lathe(u, -.8, [(.44, 0), (.42, .3), (.4, 4.0), (.36, 7.6), (.5, 7.9), (.5, 8.1), (0, 8.1)], STONE_L, n=10, z=1.35)
                    a.box(u - .55, -1.35, 9.45, u + .55, -.25, 9.7, STONE_L)
                a.box(-6.1, -1.45, 9.7, 6.1, 0, 10.7, STONE_L)
                a.box(-6.3, -1.6, 10.7, 6.3, 0, 10.95, STONE_L, bevel=.04)
                a.extrude([(-6.3, 10.95), (6.3, 10.95), (0, 13.2)], -1.6, 0, STONE_L, axis='y')
                a.extrude([(-5.2, 11.2), (5.2, 11.2), (0, 12.8)], -1.64, -1.6, STONE_D, axis='y')
                a.box(-4.5, -1.48, 9.95, 4.5, -1.45, 10.45, BRASS)
                a.box(-1.4, -.3, 1.0, 1.4, -.18, 4.6, M(0x3E2723, "WOOD"))
                a.box(-1.2, -.34, 1.2, 1.2, -.3, 4.3, B_GLASS_L)
                a.box(-.04, -.38, 1.0, .04, -.34, 4.3, BRASS)
                for u in (-7.4, 7.4):
                    a.lathe(u, -1.6, [(0, 0), (.14, 0), (.12, .4), (.05, .6), (.05, 3.0), (.3, 3.1), (.3, 3.6), (0, 3.8)], B_IRON, n=8)
                    a.sphere(u, -1.6, 3.35, .24, WARM, seg=8, rings=5)
    parapet(a, W, D, H + .5, h=1.1, t=.4, m=STONE, cap=STONE_L)
    for s, L, fr in walls(a, W, D):
        with fr:
            for k in range(int(L / 2.2)):
                u = -L / 2 + .6 + k * 2.2
                a.box(u, -.02, H + .7, u + 1.0, .0, H + 1.4, STONE_D)
    a.cyl(0, -D / 2 + 1.2, H + 1.6, H + 9.0, .07, CHROME, n=6)
    a.extrude([(.07, H + 7.0), (2.4, H + 7.2), (2.4, H + 8.6), (.07, H + 8.8)], -D / 2 + 1.18, -D / 2 + 1.22, RED, axis='y')
    bulkhead(a, 4, 3, H + .56, m=STONE, w=5, d=5, h=3)
    ac_unit(a, -4, 2, H + .56, s=1.4)
    ac_unit(a, -4, -2, H + .56, s=1.4)


@building(16, 16)
def modern(a):
    """White apartment block: staggered glass balconies, timber fin, pilotis, roof garden."""
    W, D = 16.0, 16.0
    g, fh, n = 4.4, 3.2, 7
    H = g + fh * n
    glass_rail = M(0xBFE3F5, "GLASS", alpha=.45)
    a.box(-W / 2, -D / 2 + 2.0, 0, W / 2, D / 2, g, M(0x4A4F57, "CONCRETE"))
    a.box(-W / 2, -D / 2, g, W / 2, D / 2, H, PLASTER)
    for x in (-6.5, -2.2, 2.2, 6.5):
        a.cyl(x, -D / 2 + .8, 0, g, .32, CONCRETE, n=10)
    a.box(-3.0, -D / 2 + 1.94, 0, 3.0, -D / 2 + 2.0, 3.6, B_GLASS_S)
    for u in (-3.0, -1.0, 1.0, 3.0):
        a.box(u - .05, -D / 2 + 1.9, 0, u + .05, -D / 2 + 2.0, 3.6, B_IRON)
    a.box(-3.4, -D / 2 + .6, 3.6, 3.4, -D / 2 + 2.0, 3.8, B_IRON)
    timber = M(0xB07A52, "WOOD")
    for s, L, fr in walls(a, W, D):
        with fr:
            if s == "f":
                a.box(-L / 2 + .6, -.25, g, -L / 2 + 3.0, 0, H + 1.2, timber)
                for k in range(8):
                    a.box(-L / 2 + .6 + k * .3, -.3, g, -L / 2 + .68 + k * .3, -.25, H + 1.2, M(0x8B5A3C, "WOOD"))
                for k in range(n):
                    z = g + k * fh
                    off = (k % 2) * 3.4
                    a.box(-L / 2 + 3.6, -.05, z + .5, L / 2 - .6, 0, z + 2.8, B_GLASS)
                    for j in range(4):
                        a.box(-L / 2 + 3.6 + j * 3.0 - .05, -.08, z + .5, -L / 2 + 3.6 + j * 3.0 + .05, 0, z + 2.8, PLASTER_W)
                    u0 = -L / 2 + 3.8 + off
                    a.box(u0, -1.6, z + .25, u0 + 5.0, 0, z + .45, PLASTER_W)
                    a.box(u0, -1.6, z + .45, u0 + 5.0, -1.55, z + 1.5, glass_rail)
                    a.box(u0, -1.55, z + .45, u0 + .05, 0, z + 1.5, glass_rail)
                    a.box(u0 + 4.95, -1.55, z + .45, u0 + 5.0, 0, z + 1.5, glass_rail)
                    a.box(u0 - .02, -1.62, z + 1.5, u0 + 5.02, -1.53, z + 1.55, CHROME)
                    if k % 3 == 0:
                        a.ico(u0 + .6, -1.0, z + .95, .4, LEAF, scale=(1, 1, 1.1))
                        a.cyl(u0 + .6, -1.0, z + .45, z + .75, .25, M(0xE9EDF2, "PLASTER"), n=8)
            else:
                for k in range(n):
                    z = g + k * fh
                    cols = 4
                    for j in range(cols):
                        u = -L / 2 + 2.0 + j * (L - 4.0) / (cols - 1)
                        wide = (j + k) % 2 == 0
                        win(a, u, z + .7, 2.4 if wide else 1.2, 1.9)
                    a.box(-L / 2, -.04, z - .1, L / 2, 0, z + .1, M(0xD5D8DC, "PLASTER"))
    parapet(a, W, D, H, h=1.2, t=.3, m=PLASTER, cap=None)
    # roof garden: decking, planters with trees, pergola
    a.box(-6.5, -6.5, H + .06, 2.5, 0, H + .14, timber)
    for x, y in ((-5.5, -5.5), (1.5, -5.5), (-5.5, 3.5)):
        a.box(x - .9, y - .9, H + .06, x + .9, y + .9, H + .8, CONCRETE, bevel=.04)
        a.cyl(x, y, H + .8, H + 2.2, .1, M(0x6B4A32, "WOOD"), n=5)
        for k, (dx, dy, dz, r) in enumerate(((0, 0, 2.8, 1.1), (.6, .2, 2.4, .8), (-.5, .4, 2.5, .8))):
            a.ico(x + dx, y + dy, H + dz, r, (LEAF, LEAF_L, LEAF_D)[k], rz=k * 40, scale=(1, 1, .85))
    for x in (-2.5, 1.5):
        for y in (-3.5, -.8):
            a.box(x - .08, y - .08, H + .14, x + .08, y + .08, H + 2.7, timber)
    for k in range(9):
        a.box(-2.9 + k * .58, -3.9, H + 2.7, -2.75 + k * .58, -.4, H + 2.85, timber)
    bulkhead(a, 4.5, 4.0, H + .06, m=PLASTER, w=4, d=5, h=3)
    for k in range(3):
        a.box(-6.0 + k * 2.1, 2.5, H + .5, -4.2 + k * 2.1, 5.8, H + .58, M(0x1F3A5A, "GLASS", alpha=1))
        a.rod((-5.1 + k * 2.1, 5.6, H + .06), (-5.1 + k * 2.1, 5.6, H + .5), .05, B_IRON, n=4)


@building(26, 18)
def industrial(a):
    """Warehouse: ribbed siding on a brick plinth, sawtooth roof lights, loading dock, stack."""
    W, D, h = 26.0, 18.0, 7.5
    a.box(-W / 2, -D / 2, 0, W / 2, D / 2, 2.2, BRICK_D)
    a.box(-W / 2, -D / 2, 2.2, W / 2, D / 2, h, SIDING)
    for s, L, fr in walls(a, W, D):
        with fr:
            for k in range(int(L / .8)):
                u = -L / 2 + .4 + k * .8
                a.box(u - .06, -.07, 2.2, u + .06, 0, h, SIDING_D)
            a.box(-L / 2 - .05, -.12, h - .3, L / 2 + .05, 0, h + .1, M(0x4A5560, "METAL"))
            if s == "f":
                # dock: platform, three roll doors with bumpers and numbers, steps, office door
                a.box(-11.0, -2.6, 0, 6.0, 0, 1.2, CONCRETE)
                a.box(-11.0, -2.66, 1.0, 6.0, -2.6, 1.2, YELLOW)
                for k in range(3):
                    u = -8.5 + k * 5.0
                    a.box(u - 1.9, -.14, 1.2, u + 1.9, 0, 5.4, M(0x4A5560, "METAL"))
                    a.box(u - 1.7, -.18, 1.2, u + 1.7, -.1, 5.2, SHUTTER)
                    for j in range(12):
                        a.box(u - 1.7, -.2, 1.35 + j * .32, u + 1.7, -.18, 1.39 + j * .32, M(0x7E878F, "METAL"))
                    for du in (-1.5, 1.5):
                        a.box(u + du - .15, -2.72, .55, u + du + .15, -2.6, 1.1, RUBBER)
                    a.box(u - .4, -.2, 5.6, u + .4, -.1, 6.4, YELLOW)
                    a.box(u - .12, -.22, 5.75, u + .12, -.2, 6.25, B_IRON)
                    a.box(u - 2.2, -2.9, 5.5, u + 2.2, 0, 5.62, M(0x4A5560, "METAL"))
                for k in range(6):
                    a.box(6.0 + k * .32, -2.2, 0, 6.32 + k * .32, -.6, 1.2 - (k + 1) * .2, CONCRETE_D)
                for u0, u1, z in ((6.0, 7.9, 1.2), ):
                    a.rod((6.0, -2.2, 2.1), (7.9, -2.2, 1.0), .03, YELLOW, n=4)
                    for k in range(3):
                        a.rod((6.0 + k * .95, -2.2, 1.2 - k * .6), (6.0 + k * .95, -2.2, 2.1 - k * .55), .03, YELLOW, n=4)
                a.box(9.0, -.1, 0, 10.2, 0, 2.3, M(0x2F5D8C, "PAINT"))
                a.box(9.2, -.13, 1.2, 10.0, -.1, 2.1, B_GLASS_S)
                a.box(8.4, -1.0, 2.5, 10.8, 0, 2.6, M(0x4A5560, "METAL"))
                win(a, 11.6, 1.1, 1.4, 1.2, sill=CONCRETE)
                sign(a, 7.2, 12.6, 5.0, 6.6, M(0x2F5D8C, "PAINT"), bars=2)
                # pallets waiting on the dock
                for k, (u, n) in enumerate(((-10.2, 3), (4.9, 2))):
                    a.box(u - .5, -2.3, 1.2, u + .5, -1.3, 1.32, M(0xC69A6B, "WOOD"))
                    for j in range(n):
                        a.box(u - .45, -2.25, 1.32 + j * .45, u + .45, -1.35, 1.75 + j * .45, (CARDBOARD, CARDBOARD_D)[j % 2], bevel=.01)
            elif s == "b":
                for k in range(6):
                    win(a, -L / 2 + 2.5 + k * 4.2, 3.6, 2.6, 1.6, glass=M(0x6A90A8, "GLASS", alpha=1), frame=M(0x4A5560, "METAL"))
            else:
                for k in range(3):
                    win(a, -L / 2 + 3.5 + k * 5.5, 3.6, 2.6, 1.6, glass=M(0x6A90A8, "GLASS", alpha=1), frame=M(0x4A5560, "METAL"))
    # sawtooth: four north lights, glass on the steep face
    teeth = 4
    tw = W / teeth
    for k in range(teeth):
        x0 = -W / 2 + k * tw
        a.extrude([(x0, h), (x0 + tw, h), (x0 + 1.0, h + 3.0), (x0, h + 3.0)], -D / 2, D / 2, ROOF_L, axis='y')
        a.box(x0 - .02, -D / 2 + .6, h + .4, x0 + .04, D / 2 - .6, h + 2.7, M(0x9FC4DD, "GLASS", alpha=1))
        for j in range(8):
            y = -D / 2 + .6 + j * (D - 1.2) / 7
            a.box(x0 - .05, y - .05, h + .4, x0 + .06, y + .05, h + 2.7, M(0x4A5560, "METAL"))
    a.cyl(10.5, 6.5, 0, 17.0, .9, BRICK, n=10, r2=.6)
    a.cyl(10.5, 6.5, 16.2, 17.2, .68, BRICK_D, n=10)
    for k in range(3):
        a.lathe(-8.0 + k * 6.5, 4.0, [(.4, 0), (.4, .6), (.6, .9), (.6, 1.0), (0, 1.3)], SHUTTER, n=8, z=h + 1.2)


@building(10, 10)
def shop(a):
    """Corner shop with a flat above: glazed corner, striped awnings, blade sign, shutters."""
    W, D = 10.0, 10.0
    g, fh = 4.0, 3.3
    H = g + fh
    cream = M(0xF0ECE4, "PLASTER")
    a.box(-W / 2, -D / 2, 0, W / 2, D / 2, H, cream)
    red = M(0xC0392B, "PAINT")
    for s, L, fr in walls(a, W, D):
        with fr:
            if s in "fr":
                storefront(a, L, g, frame=M(0x2F5D43, "PAINT"), sign_col=red if s == "f" else None,
                           awn=(F(0xC0392B), F(0xF5F2EA)), door_u=(2.6 if s == "f" else None), name_bars=1)
                for k in range(3):
                    u = -L / 2 + 1.8 + k * (L - 3.6) / 2
                    win(a, u, g + .9, 1.2, 1.7, frame=WHITE, sill=STONE)
                    for sx in (-1, 1):
                        a.box(u + sx * .95 - .28, -.08, g + .85, u + sx * .95 + .28, 0, g + 2.65, M(0x2F5D43, "PAINT"))
                        for j in range(6):
                            a.box(u + sx * .95 - .24, -.1, g + .95 + j * .28, u + sx * .95 + .24, -.08, g + 1.1 + j * .28, M(0x3A7052, "PAINT"))
                    a.box(u - .5, -.4, g + .55, u + .5, -.1, g + .82, M(0x6B4A32, "WOOD"))
                    for j in range(4):
                        a.ico(u - .36 + j * .24, -.25, g + .92, .13, (RED, PINK, YELLOW, PURPLE)[(j + k) % 4])
            else:
                for k in range(2):
                    win(a, -L / 2 + 2.5 + k * 5, g + .9, 1.2, 1.7, sill=STONE)
                    win(a, -L / 2 + 2.5 + k * 5, 1.3, 1.2, 1.5, sill=STONE)
                if s == "b":
                    a.box(-.5, -.06, 0, .5, 0, 2.2, M(0x4A5560, "METAL"))
            a.box(-L / 2 - .2, -.4, H - .1, L / 2 + .2, 0, H + .35, M(0xD9D4CC, "STONE"), bevel=.05)
    # blade sign and lantern on the corner, pavement display
    a.box(W / 2 - .1, -D / 2 - 1.3, 4.6, W / 2 + .1, -D / 2 - .1, 6.4, red, bevel=.04)
    a.box(W / 2 - .12, -D / 2 - 1.1, 5.2, W / 2 + .12, -D / 2 - .3, 5.8, WHITE)
    a.rod((W / 2, -D / 2, 6.5), (W / 2, -D / 2 - 1.3, 6.5), .03, B_IRON, n=4)
    for k, (x, m) in enumerate(((-3.6, 0xE5483B), (-2.5, 0xF2C94C), (-1.4, 0x6DBE45))):
        with a.at(x, -D / 2 - .7, 0):
            a.box(-.45, -.35, 0, .45, .35, .6, M(0x8B5A3C, "WOOD"), bevel=.02)
            for i in range(3):
                for j in range(2):
                    a.ico(-.28 + i * .28, -.15 + j * .3, .68, .15, M(m, "FOOD"), rz=i * 40 + j * 20)
    with a.at(.6, -D / 2 - 1.1, 0):
        a.extrude([(-.35, 0), (0, 1.0), (.35, 0), (.28, 0), (0, .85), (-.28, 0)], -.3, .3, M(0x2E3238, "PAINT"), axis='y')
    parapet(a, W, D, H + .35, h=.5, m=cream, cap=M(0xD9D4CC, "STONE"))
    ac_unit(a, -2, 2, H + .41)
    a.box(2.0, 1.5, H + .35, 3.0, 2.5, H + 2.4, BRICK)
    a.box(1.9, 1.4, H + 2.4, 3.1, 2.6, H + 2.55, STONE_X)


# ---------------------------------------------------------------------------------------------- Manila

@building(14, 12)
def colonial(a):
    """Bahay na bato: stone ground floor, overhanging timber upper floor with capiz windows, tiled hip roof."""
    W, D = 14.0, 12.0
    g, up = 4.2, 3.8
    adobe = M(0xCFC3AE, "STONE")
    capiz = M(0xEDE6D3, "PAINT")
    a.box(-W / 2, -D / 2, 0, W / 2, D / 2, g, adobe)
    o = .7
    a.box(-W / 2 - o, -D / 2 - o, g, W / 2 + o, D / 2 + o, g + up, B_WOOD)
    a.box(-W / 2 - o - .1, -D / 2 - o - .1, g - .25, W / 2 + o + .1, D / 2 + o + .1, g + .05, M(0x4A2F1F, "WOOD"))
    for s, L, fr in walls(a, W, D):
        with fr:
            a.box(-L / 2 - .08, -.14, 0, L / 2 + .08, 0, .9, M(0xBFB6A8, "STONE"))
            n = 3 if L > 13 else 2
            for k in range(n + 1):
                u = -L / 2 + 1.6 + k * (L - 3.2) / n
                if s == "f" and k == 1:
                    # the main gate: arched double door
                    a.box(u - 1.25, -.2, 0, u + 1.25, 0, 3.2, M(0xBFB6A8, "STONE"))
                    a.box(u - 1.0, -.26, 0, u + 1.0, -.12, 2.5, M(0x3E2723, "WOOD"))
                    a.rod((u, -.12, 2.5), (u, -.26, 2.5), 1.0, M(0x3E2723, "WOOD"), n=16)
                    a.box(u - .03, -.3, 0, u + .03, -.26, 3.4, B_IRON)
                    for sx in (-1, 1):
                        a.torus(u + sx * .3, -.29, 1.3, .1, .02, BRASS, n=8, k=4, axis='y')
                    continue
                a.box(u - .65, -.12, 1.3, u + .65, .02, 3.1, M(0x1E2A36, "GLASS", alpha=1))
                a.rod((u, .02, 3.1), (u, -.12, 3.1), .65, M(0x1E2A36, "GLASS", alpha=1), n=12)
                for j in range(5):
                    a.box(u - .5 + j * .25 - .025, -.17, 1.3, u - .5 + j * .25 + .025, -.12, 3.6, B_IRON)
                a.box(u - .8, -.2, 1.12, u + .8, 0, 1.3, M(0xBFB6A8, "STONE"))
        # upper floor: a continuous band of capiz-shell sliding windows over ventanillas
    for s, L, fr in walls(a, W + 2 * o, D + 2 * o):
        with fr:
            a.box(-L / 2 + .3, -.06, g + 1.3, L / 2 - .3, 0, g + 3.3, capiz)
            panels = int((L - .6) / 1.15)
            pw = (L - .6) / panels
            for k in range(panels + 1):
                u = -L / 2 + .3 + k * pw
                a.box(u - .05, -.1, g + 1.3, u + .05, 0, g + 3.3, B_WOOD)
                if k < panels:
                    for j in range(1, 4):
                        a.box(u + pw * j / 4 - .012, -.075, g + 1.3, u + pw * j / 4 + .012, -.06, g + 3.3, M(0x8B6A4E, "WOOD"))
            for j in range(6):
                a.box(-L / 2 + .3, -.075, g + 1.3 + j * .4, L / 2 - .3, -.06, g + 1.325 + j * .4, M(0x8B6A4E, "WOOD"))
            a.box(-L / 2 + .2, -.14, g + 1.18, L / 2 - .2, 0, g + 1.3, M(0x4A2F1F, "WOOD"))
            a.box(-L / 2 + .2, -.14, g + 3.3, L / 2 - .2, 0, g + 3.45, M(0x4A2F1F, "WOOD"))
            for k in range(int((L - .8) / .28)):
                u = -L / 2 + .45 + k * .28
                a.box(u, -.06, g + .35, u + .08, -.02, g + 1.18, M(0x8B6A4E, "WOOD"))
            # a few panels slid open
            for u in ((-L / 2 + .3 + pw * 2), (L / 2 - .3 - pw * 3)):
                a.box(u + .06, -.03, g + 1.34, u + pw - .06, .01, g + 3.26, M(0x1E2A36, "GLASS", alpha=1))
    e = 1.5
    a.box(-W / 2 - o - e, -D / 2 - o - e, g + up, W / 2 + o + e, D / 2 + o + e, g + up + .18, M(0x4A2F1F, "WOOD"))
    hip_roof(a, -W / 2 - o - e, -D / 2 - o - e, W / 2 + o + e, D / 2 + o + e, g + up + .18, 4.6, TILE_RED, ridge=3.0)
    a.box(-1.7, -.18, g + up + 4.65, 1.7, .18, g + up + 4.95, TILE_RED_D)
    for k in range(12):
        t = (k + .5) / 12
        for sy in (-1, 1):
            y = sy * (D / 2 + o + e) * (1 - t)
            a.box(-(W / 2 + o + e) + (W / 2 + o + e - 1.5) * t, y - .04, g + up + .2 + 4.6 * t, (W / 2 + o + e) - (W / 2 + o + e - 1.5) * t, y + .04, g + up + .26 + 4.6 * t, TILE_RED_D)


@building(10, 12)
def tropical(a):
    """Manila walk-up: sari-sari store, grilled balconies, jalousies, tin roof, water tank, rebar left for another floor."""
    W, D = 10.0, 12.0
    g, fh, n = 3.6, 3.1, 2
    H = g + fh * n
    paint = M(0x7FB8B0, "PLASTER")
    paint_d = M(0x5F9A92, "PLASTER")
    trim = M(0xF2E7CF, "PLASTER")
    a.box(-W / 2, -D / 2, 0, W / 2, D / 2, H, paint)
    for s, L, fr in walls(a, W, D):
        with fr:
            for k in range(n + 1):
                z = g + k * fh
                a.box(-L / 2 - .1, -.12, z - .3, L / 2 + .1, 0, z, trim)
            for u in (-L / 2 + .2, L / 2 - .2):
                a.box(u - .22, -.08, 0, u + .22, 0, H, paint_d)
            if s == "f":
                # sari-sari store: counter window behind a grille, hanging sachets, roll shutter, signboard
                a.box(-4.4, -.1, .0, .3, 0, 3.1, M(0x4A4F57, "CONCRETE"))
                a.box(-4.2, -.14, 1.0, .1, -.06, 2.7, M(0x1E2A36, "GLASS", alpha=1))
                a.box(-4.4, -.5, .9, .3, 0, 1.05, M(0x8B5A3C, "WOOD"))
                for k in range(15):
                    a.box(-4.2 + k * .3, -.2, 1.05, -4.17 + k * .3, -.16, 2.7, B_IRON)
                for k in range(4):
                    a.box(-4.2, -.2, 1.3 + k * .42, .1, -.17, 1.33 + k * .42, B_IRON)
                sachets = (0xE53935, 0xFDD835, 0x1E88E5, 0x43A047, 0xFB8C00, 0x8E24AA)
                for k in range(12):
                    u = -4.0 + k * .34
                    for j in range(4):
                        a.box(u, -.26, 2.5 - j * .22, u + .16, -.22, 2.68 - j * .22, M(sachets[(k + j) % 6], "PAPER"))
                for k in range(5):
                    a.cyl(-3.8 + k * .5, -.3, 1.05, 1.35, .13, M(0xDDEEF7, "GLASS", alpha=.6), n=8)
                    a.cyl(-3.8 + k * .5, -.3, 1.35, 1.4, .14, (RED, YELLOW, BLUE, GREEN, ORANGE)[k], n=8)
                sign(a, -4.5, .4, 3.1, 3.9, M(0xE53935, "PAINT"), text=YELLOW, bars=1)
                a.box(1.0, -.12, 0, 4.4, 0, 3.0, M(0x4A4F57, "CONCRETE"))
                a.box(1.15, -.16, 0, 4.25, -.08, 2.85, M(0x3D6E8C, "METAL"))
                for k in range(11):
                    a.box(1.15, -.18, .15 + k * .25, 4.25, -.16, .19 + k * .25, M(0x335C76, "METAL"))
                awning(a, -4.6, 4.6, 3.4, out=1.5, drop=.45, cols=(M(0x9C5A3C, "METAL"), M(0x8C4E33, "METAL")), stripe=.3, valance=.05)
                for k in range(n):
                    z = g + k * fh
                    # balcony with an ornamental grille, jalousie door and window behind
                    a.box(-4.6, -1.2, z + .0, 2.0, 0, z + .15, trim)
                    for j in range(23):
                        a.box(-4.6 + j * .3, -1.2, z + .15, -4.56 + j * .3, -1.16, z + 1.1, B_IRON)
                    a.box(-4.6, -1.22, z + 1.1, 2.0, -1.14, z + 1.16, B_IRON)
                    for u in (-4.6, 1.96):
                        a.box(u, -1.2, z + .15, u + .04, 0, z + 1.16, B_IRON)
                    a.box(-3.9, -.07, z + .15, -2.9, 0, z + 2.3, M(0x8B5A3C, "WOOD"))
                    for u in (-1.2, 3.4):
                        win(a, u, z + 1.0, 1.6, 1.3, glass=M(0x9FC4DD, "GLASS", alpha=1), frame=trim)
                        for j in range(6):
                            a.box(u - .8, -.09, z + 1.05 + j * .21, u + .8, -.05, z + 1.09 + j * .21, CHROME)
                    a.box(2.9, -.5, z + .55, 3.9, -.05, z + .95, M(0xC9CED6, "METAL"), bevel=.02)
                # washing on a line
                for k, c in enumerate((0xEB5757, 0xF2C94C, 0xE9EDF2, 0x2F80ED, 0xF28AB2)):
                    a.box(-4.2 + k * 1.1, -1.12, g + fh + 1.25, -3.6 + k * 1.1, -1.1, g + fh + 2.0, F(c))
                a.rod((-4.6, -1.11, g + fh + 2.02), (2.0, -1.11, g + fh + 2.02), .01, B_IRON, n=3)
            else:
                for k in range(n + 1):
                    z = (1.0 if k == 0 else g + (k - 1) * fh + 1.0)
                    cols = 3
                    for j in range(cols):
                        u = -L / 2 + 2.0 + j * (L - 4.0) / (cols - 1)
                        win(a, u, z, 1.4, 1.3, glass=M(0x9FC4DD, "GLASS", alpha=1), frame=trim)
                        for i in range(5):
                            a.box(u - .7, -.09, z + .1 + i * .25, u + .7, -.05, z + .14 + i * .25, CHROME)
                        if k > 0 and j == 1:
                            a.box(u + 1.0, -.5, z, u + 2.0, -.05, z + .45, M(0xC9CED6, "METAL"), bevel=.02)
    # roof: half slab, half rusty lean-to; blue tank; rebar standing proud of the columns
    a.box(-W / 2, -D / 2, H, W / 2, D / 2, H + .12, CONCRETE_D)
    tin = (M(0x9C5A3C, "METAL"), M(0x8C9399, "METAL"), M(0xA7A9A4, "METAL"))
    for k in range(12):
        x = -W / 2 + k * .45
        a.extrude([(-D / 2 + .3, H + 2.2), (D / 2 - 1.0, H + 3.2), (D / 2 - 1.0, H + 3.27), (-D / 2 + .3, H + 2.27)], x, x + .45, tin[(k * 7) % 3], axis='x')
    for x in (-W / 2 + .2, 0.2):
        for y in (-D / 2 + .5, D / 2 - 1.2):
            a.box(x - .08, y - .08, H + .12, x + .08, y + .08, H + (2.2 if y < 0 else 3.2), CONCRETE_D)
    a.box(1.2, 1.5, H + .12, 3.6, 3.9, H + 1.9, CONCRETE_D)
    a.cyl(2.4, 2.7, H + 1.9, H + 3.7, 1.0, M(0x2F6FB0, "PLASTIC"), n=12)
    a.lathe(2.4, 2.7, [(1.0, 0), (.9, .25), (.3, .4), (.3, .5), (0, .5)], M(0x2F6FB0, "PLASTIC"), n=12, z=H + 3.7)
    for x, y in ((W / 2 - .3, -D / 2 + .3), (W / 2 - .3, D / 2 - .3), (W / 2 - .3, 0), (1.0, -D / 2 + .3)):
        a.box(x - .2, y - .2, H + .12, x + .2, y + .2, H + .7, CONCRETE_D)
        for k in range(4):
            a.rod((x - .12 + (k % 2) * .24, y - .12 + (k // 2) * .24, H + .7), (x - .14 + (k % 2) * .3, y - .12 + (k // 2) * .26, H + 1.7 + k * .12), .018,
                  M(0x7A4A32, "METAL"), n=3)


# ---------------------------------------------------------------------------------------------- Tokyo and Seoul

@building(6, 12)
def pencil(a):
    """Tokyo pencil building: one room wide, a different business on every floor, stacked signs, roof hoarding."""
    W, D = 6.0, 12.0
    g, fh, n = 3.6, 3.0, 8
    H = g + fh * n
    tile = M(0xE8E2D6, "TILE")
    a.box(-W / 2, -D / 2, 0, W / 2, D / 2, H, tile)
    neon = (0xFF2A9D, 0x22D3FF, 0xFFD21F, 0x27F06A, 0xFF4A1F, 0x9B5CFF)
    for s, L, fr in walls(a, W, D, "fbl"):
        with fr:
            if s == "f":
                # convenience store at street level
                a.box(-L / 2, -.1, 0, L / 2, 0, g, M(0xF7FAFC, "PAINT"))
                a.box(-L / 2 + .2, -.14, .15, L / 2 - 1.6, -.06, 2.7, B_GLASS_S)
                a.box(L / 2 - 1.5, -.14, 0, L / 2 - .2, -.06, 2.7, B_GLASS_S)
                a.box(L / 2 - .88, -.17, 0, L / 2 - .82, -.14, 2.7, CHROME)
                for k, c in enumerate((0x1F9D55, 0xF7FAFC, 0x2F80ED)):
                    a.box(-L / 2, -.2, 2.8 + k * .25, L / 2, 0, 3.05 + k * .25, E(c, 1.2))
                for k in range(4):
                    a.box(-L / 2 + .4 + k * .7, -.05, .3, -L / 2 + .9 + k * .7, -.02, 1.6, (RED, YELLOW, GREEN, WHITE)[k])
                # vending machine and a potted tree out front
                a.box(-L / 2 + .1, -.95, 0, -L / 2 + 1.0, -.2, 1.85, M(0xD0392B, "PAINT"), bevel=.02)
                a.box(-L / 2 + .18, -.97, .8, -L / 2 + .92, -.95, 1.75, E(0xFFF4D6, 1.0))
                for k in range(5):
                    a.box(-L / 2 + .22 + k * .14, -.985, 1.1, -L / 2 + .32 + k * .14, -.97, 1.35, (RED, BLUE, GREEN, YELLOW, WHITE)[k])
                for k in range(n):
                    z = g + k * fh
                    a.box(-L / 2 + .3, -.06, z + .7, L / 2 - 1.3, 0, z + 2.5, B_GLASS)
                    for j in range(3):
                        a.box(-L / 2 + .3 + (j + 1) * 1.1 - .03, -.09, z + .7, -L / 2 + .3 + (j + 1) * 1.1 + .03, 0, z + 2.5, CHROME)
                    a.box(-L / 2, -.1, z - .15, L / 2, 0, z + .15, M(0xD9D4CC, "TILE"))
                    # lit window poster for the business on this floor, and its blade sign
                    c = neon[k % 6]
                    a.box(-L / 2 + .45, -.09, z + .9, -L / 2 + 1.3, -.06, z + 2.3, M(c, "PAINT"))
                    a.box(L / 2 - 1.15, -1.0, z + .35, L / 2 - .15, -.1, z + 2.85, M(0x14181D, "PAINT"), bevel=.03)
                    for sx in (-1, 1):
                        a.box(L / 2 - .65 + sx * .51, -.9, z + .5, L / 2 - .65 + sx * .52, -.2, z + 2.7, E(c, 2.0))
                        for j in range(4):
                            a.box(L / 2 - .65 + sx * .525, -.72, z + .7 + j * .5, L / 2 - .65 + sx * .53, -.38, z + 1.0 + j * .5, WHITE)
            elif s == "l":
                # escape stair zig-zagging up the side
                for k in range(n):
                    z = g + k * fh
                    a.box(-2.0, -1.0, z - .08, 2.0, 0, z, B_IRON)
                    a.box(-2.0, -1.0, z + 1.0, 2.0, -.96, z + 1.05, B_IRON)
                    for j in range(9):
                        a.box(-2.0 + j * .5, -1.0, z, -1.97 + j * .5, -.96, z + 1.0, B_IRON)
                    if k + 1 < n:
                        d = 1 if k % 2 == 0 else -1
                        for dy in (-.9, -.5):
                            a.rod((-1.4 * d, dy, z), (1.4 * d, dy, z + fh - .08), .03, B_IRON, n=4)
                    a.box(-.45, -.05, z, .45, 0, z + 2.1, M(0x4A5560, "METAL"))
                for k in range(n):
                    a.box(-4.4, -.5, g + k * fh + .9, -3.5, -.05, g + k * fh + 1.5, M(0xC9CED6, "METAL"), bevel=.02)
            else:
                for k in range(n):
                    win_row(a, L, g + k * fh + .9, 2, 1.2, 1.4, margin=.6, sill=M(0xD9D4CC, "TILE"))
    parapet(a, W, D, H, h=.9, t=.25, m=tile, cap=M(0xD9D4CC, "TILE"))
    # roof: hoarding on a frame facing the street, tank, condensers
    for x in (-2.4, 2.4):
        a.box(x - .06, -D / 2 + .5, H, x + .06, -D / 2 + .62, H + 5.2, B_IRON)
        a.rod((x, -D / 2 + .56, H + 4.0), (x, -D / 2 + 3.0, H + .1), .04, B_IRON, n=4)
    a.box(-2.9, -D / 2 + .3, H + 1.4, 2.9, -D / 2 + .5, H + 5.3, M(0x14181D, "PAINT"), bevel=.03)
    a.box(-2.75, -D / 2 + .27, H + 1.55, 2.75, -D / 2 + .3, H + 5.15, E(0xFF2A9D, 1.0))
    a.box(-2.75, -D / 2 + .25, H + 1.55, 2.75, -D / 2 + .27, H + 2.9, E(0x22D3FF, 1.2))
    a.ico(1.2, -D / 2 + .24, H + 4.0, .8, E(0xFFD21F, 1.5), scale=(1, .04, 1), sub=2)
    for k in range(3):
        a.box(-2.3, -D / 2 + .23, H + 4.5 - k * .5, -2.3 + (2.2, 1.6, 1.9)[k], -D / 2 + .25, H + 4.8 - k * .5, WHITE)
    a.cyl(1.3, 3.5, H + .9, H + 2.9, .9, M(0xD9DEE4, "METAL"), n=12)
    a.lathe(1.3, 3.5, [(.9, 0), (0, .35)], M(0xD9DEE4, "METAL"), n=12, z=H + 2.9)
    for sx in (-1, 1):
        a.box(1.3 + sx * .6 - .05, 3.45, H, 1.3 + sx * .6 + .05, 3.55, H + .9, B_IRON)
    for k in range(3):
        ac_unit(a, -1.4, 1.0 + k * 1.3, H + .06, s=.8)


@building(12, 8)
def hanok(a):
    """Korean hanok: stone plinth, timber frame, paper screens, sweeping tiled roof with upturned eaves."""
    W, D = 12.0, 8.0
    p = .6
    hanji = M(0xF2E7CF, "PAPER")
    wood = M(0x5A3A26, "WOOD")
    a.box(-W / 2 - .6, -D / 2 - .6, 0, W / 2 + .6, D / 2 + .6, p, M(0xBFB6A8, "STONE"), bevel=.05)
    a.box(-W / 2 + .3, -D / 2 + .6, p, W / 2 - .3, D / 2 - .3, p + 3.0, hanji)
    cols = 5
    for k in range(cols):
        x = -W / 2 + .3 + k * (W - .6) / (cols - 1)
        for y in (-D / 2 + .6, D / 2 - .3, -D / 2 - .1):
            a.cyl(x, y, p, p + 3.0, .15, wood, n=8)
            a.cyl(x, y, p - .02, p + .12, .22, M(0x8E8676, "STONE"), n=8)
    for y in (-D / 2 + .6, D / 2 - .3, -D / 2 - .1):
        a.box(-W / 2 + .2, y - .12, p + 2.85, W / 2 - .2, y + .12, p + 3.15, wood)
    # the open wooden veranda (maru) along the front, stone step
    a.box(-W / 2 + .3, -D / 2 - .3, p, W / 2 - .3, -D / 2 + .6, p + .28, M(0x8B6A4E, "WOOD"))
    a.box(-1.2, -D / 2 - 1.3, 0, 1.2, -D / 2 - .6, .3, M(0xBFB6A8, "STONE"), bevel=.04)
    # lattice doors between the posts, front and back
    for sy, y in ((-1, -D / 2 + .6), (1, D / 2 - .3)):
        for k in range(cols - 1):
            x0 = -W / 2 + .3 + k * (W - .6) / (cols - 1) + .2
            x1 = x0 + (W - .6) / (cols - 1) - .4
            z0 = p + (.28 if sy < 0 else .9)
            a.box(x0, y + sy * .04, z0, x1, y + sy * .08, p + 2.6, hanji)
            a.box(x0 - .05, y + sy * .08, z0 - .05, x1 + .05, y + sy * .12, z0 + .05, wood)
            a.box(x0 - .05, y + sy * .08, p + 2.55, x1 + .05, y + sy * .12, p + 2.65, wood)
            n = 6
            for j in range(n + 1):
                x = x0 + (x1 - x0) * j / n
                a.box(x - .02, y + sy * .08, z0, x + .02, y + sy * .11, p + 2.6, wood)
            for j in range(1, 5):
                zz = z0 + (p + 2.6 - z0) * j / 5
                a.box(x0, y + sy * .08, zz - .015, x1, y + sy * .11, zz + .015, wood)
            if sy > 0:
                a.box(x0 - .05, y + .04, p, x1 + .05, y + .1, z0, M(0xE9E2D0, "PLASTER"))
    # roof: a concave sweep from ridge to eaves, hipped ends, heavy ridge, upturned corners
    e = 1.5
    zr = p + 3.15
    prof = [(-D / 2 - e, zr + .25), (-D / 2 - e, zr + .45), (-D / 2 - e + 1.2, zr + .7), (-1.4, zr + 1.9), (0, zr + 2.9),
            (1.4, zr + 1.9), (D / 2 + e - 1.2, zr + .7), (D / 2 + e, zr + .45), (D / 2 + e, zr + .25), (0, zr + .25)]
    a.extrude(prof, -W / 2 - .2, W / 2 + .2, GIWA, axis='x')
    for sx in (-1, 1):
        x0 = sx * (W / 2 + .2)
        x1 = sx * (W / 2 + e + .3)
        bm = a.bm
        mi = a._mi(GIWA)
        ys = (-D / 2 - e, D / 2 + e)
        v = [bm.verts.new(q) for q in ((x0, ys[0], zr + .45), (x0, 0, zr + 2.9), (x0, ys[1], zr + .45), (x1, ys[1] + .25, zr + .85), (x1, ys[0] - .25, zr + .85),
                                       (x0, ys[0], zr + .25), (x0, ys[1], zr + .25), (x1, ys[1] + .25, zr + .65), (x1, ys[0] - .25, zr + .65))]
        for f in ((v[0], v[1], v[4]), (v[1], v[3], v[4]), (v[1], v[2], v[3]), (v[5], v[8], v[7], v[6]), (v[0], v[4], v[8], v[5]), (v[2], v[6], v[7], v[3]),
                  (v[4], v[3], v[7], v[8])):
            bm.faces.new(f).material_index = mi
    dark = M(0x2E3238, "ROOF")
    a.box(-W / 2 - .4, -.22, zr + 2.8, W / 2 + .4, .22, zr + 3.2, dark, bevel=.06)
    for sx in (-1, 1):
        a.box(sx * (W / 2 + .4) - .2, -.3, zr + 2.9, sx * (W / 2 + .4) + .2, .3, zr + 3.5, dark, bevel=.06)
        for sy in (-1, 1):
            a.rod((sx * (W / 2 + .2), 0, zr + 2.85), (sx * (W / 2 + e + .3), sy * (D / 2 + e + .25), zr + .9), .14, dark, n=5)
    for k in range(int((W + 2.0) / .45)):
        x = -W / 2 - 1.0 + k * .45
        for sy in (-1, 1):
            a.rod((x, sy * 1.2, zr + 2.15), (x, sy * (D / 2 + e - .1), zr + .6), .06, dark, n=4)
            a.rod((x, sy * (D / 2 + e - .3), zr + .2), (x, sy * (D / 2 - .1), zr + .1), .07, wood, n=4)
    # jars of kimchi and soy by the side, a low stone wall
    for k, (x, y, s) in enumerate(((W / 2 + 1.6, -1.5, 1.0), (W / 2 + 2.4, -1.0, .8), (W / 2 + 1.7, -.4, .7), (W / 2 + 2.5, .0, 1.1))):
        a.lathe(x, y, [(0, 0), (.22 * s, 0), (.38 * s, .35 * s), (.36 * s, .7 * s), (.24 * s, .85 * s), (.27 * s, .9 * s), (0, .9 * s)], M(0x5A3A2A, "TILE"), n=10)
        a.lathe(x, y, [(.3 * s, 0), (.1 * s, .1 * s), (0, .16 * s)], M(0x4A2E22, "TILE"), n=10, z=.9 * s)


def build_buildings():
    coll = category("Buildings")
    obs = []
    for name, w, d, fn in BUILDINGS:
        a = Asset(name)
        fn(a)
        obs.append(a.finish(coll, footprint=[w, d]))
    return obs
