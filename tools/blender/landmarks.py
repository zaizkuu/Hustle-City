"""The cities' landmarks, and the street things only some cities have. Needs kit.py, props.py and buildings.py in front.

The game draws its landmarks in code (render/Landmarks.kt); these are the same sights as models, for the library,
for stills and for the trailer. Each stands with its front to -Y, centred on the origin.
"""

LANDMARKS = []


def landmark(w, d):
    def deco(fn):
        LANDMARKS.append((fn.__name__, w, d, fn))
        return fn
    return deco


VERMILION = M(0xD9452B, "PAINT")
INTL_ORANGE = M(0xE8502A, "PAINT")
GRANITE = M(0xA9A59C, "STONE")
GRANITE_D = M(0x7E7A72, "STONE")
BRONZE = M(0x6E5A3A, "METAL")
COPPER = M(0x5FA08C, "ROOF")
GOLD = M(0xD4AF37, "METAL")
CREAM_S = M(0xEFE6D2, "STONE")
DANCHEONG = M(0x2F7D6B, "PAINT")


# ---------------------------------------------------------------------------------------------- street things

@prop
def park_lamp(a):
    """A short lamp for squares and parks: a fluted post with a glass globe."""
    a.lathe(0, 0, [(0, 0), (.2, 0), (.2, .1), (.11, .2), (.09, .6), (.07, .68), (.055, 3.0), (.09, 3.06), (.09, 3.12), (0, 3.12)], POST, n=10)
    a.sphere(0, 0, 3.34, .23, E(0xFFF3C4, 3.0), seg=12, rings=8)
    a.lathe(0, 0, [(.1, 0), (.14, .03), (.05, .07), (0, .12)], POST, n=10, z=3.54)
    a.torus(0, 0, 3.14, .12, .02, POST, n=10, k=4)


@prop
def tree_ginkgo(a):
    """A ginkgo in autumn gold: a straight trunk, the crown tall and narrow."""
    gold = (M(0xE8B923, "FOLIAGE"), M(0xF2CE4E, "FOLIAGE"), M(0xD9A21B, "FOLIAGE"))
    a.lathe(0, 0, [(0, 0), (.26, 0), (.17, .25), (.13, 2.2), (.08, 5.2), (0, 5.2)], BARK_D, n=7, smooth=False)
    for k, (z, r) in enumerate(((3.0, 1.25), (4.1, 1.35), (5.2, 1.15), (6.2, .9), (7.0, .6))):
        a.ico(.12 * math.cos(k * 2.1), .12 * math.sin(k * 2.1), z, r, gold[k % 3], rz=k * 40, scale=(1, 1, .8))
    for k in range(5):
        ang = k * 1.3
        a.rod((0, 0, 2.4 + k * .3), (.9 * math.cos(ang), .9 * math.sin(ang), 3.0 + k * .35), .05, BARK_D, n=4, r2=.025, smooth=False)
    for k in range(14):
        ang = k * 2.4
        d = .5 + (k % 4) * .28
        a.ico(d * math.cos(ang), d * math.sin(ang), .012, .06, gold[k % 3], scale=(1, 1, .12), sub=0)
    a.box(-.75, -.75, 0, .75, .75, .04, M(0x4A3728, "PLAIN"))


@prop
def lantern_string(a):
    """Red lanterns on a line between two posts, 9 m apart, across a festival street."""
    wood = M(0x3E2723, "WOOD")
    red = E(0xFF4A1F, 1.8)
    for sx in (-1, 1):
        a.cyl(sx * 4.5, 0, 0, 4.9, .07, wood, n=6)
        a.box(sx * 4.5 - .14, -.14, 0, sx * 4.5 + .14, .14, .2, CONC_D)
    n = 7
    pts = [(-4.5 + 9 * k / 20, 0, 4.8 - 1.0 * math.sin(math.pi * k / 20) ** .9) for k in range(21)]
    a.tube(pts, .012, BLACK, n=4, joints=False)
    for k in range(n):
        u = (k + .5) / n
        x = -4.5 + 9 * u
        z = 4.8 - 1.0 * math.sin(math.pi * u) ** .9
        a.lathe(x, 0, [(0, 0), (.08, 0), (.17, .1), (.19, .24), (.17, .38), (.08, .48), (0, .48)], red, n=8, z=z - .56)
        a.cyl(x, 0, z - .6, z - .55, .09, BLACK, n=8)
        a.cyl(x, 0, z - .09, z - .04, .09, BLACK, n=8)
        a.rod((x, 0, z - .04), (x, 0, z), .005, BLACK, n=3)


@prop
def bunting_string(a):
    """Fiesta bunting on a line between two poles, 9 m apart."""
    for sx in (-1, 1):
        a.cyl(sx * 4.5, 0, 0, 5.8, .06, POST, n=6)
    cols = (RED, YELLOW, BLUE, GREEN, WHITE, ORANGE)
    n = 18
    for k in range(n):
        u0, u1 = k / n, (k + 1) / n
        x0, x1 = -4.5 + 9 * u0, -4.5 + 9 * u1
        z0 = 5.7 - .55 * math.sin(math.pi * u0)
        z1 = 5.7 - .55 * math.sin(math.pi * u1)
        a.rod((x0, 0, z0), (x1, 0, z1), .008, BLACK, n=3)
        a.face([(x0 + .04, 0, z0 - .01), (x1 - .04, 0, z1 - .01), ((x0 + x1) / 2, 0, (z0 + z1) / 2 - .42)], cols[k % len(cols)], two_sided=True)


# ---------------------------------------------------------------------------------------------- landmarks

def _tw(z, top=150.0):
    """Half-width of the lattice tower at height z: the game's own curve."""
    return 1.2 + 18.8 * (1 - min(1.0, z / top)) ** 2.2


@landmark(40, 40)
def lattice_tower(a):
    """A 150 m broadcast tower of red and white steel, with two observation decks."""
    white = M(0xF2F2F2, "PAINT")
    levels = [0, 9, 18, 28, 38, 46, 56, 66, 76, 86, 96, 104, 114, 124, 134, 142, 150]
    for i, (z0, z1) in enumerate(zip(levels, levels[1:])):
        m = INTL_ORANGE if (i // 2) % 2 == 0 else white
        w0, w1 = _tw(z0), _tw(z1)
        r = max(.12, .5 * w0 / 20)
        for sx in (-1, 1):
            for sy in (-1, 1):
                a.rod((sx * w0, sy * w0, z0), (sx * w1, sy * w1, z1), r, m, n=5, r2=max(.1, .5 * w1 / 20), smooth=False)
        for k in range(4):
            with a.at(0, 0, 0, rz=k * 90):
                a.rod((-w1, -w1, z1), (w1, -w1, z1), r * .6, m, n=4, smooth=False)
                if i > 0:
                    a.rod((-w0, -w0, z0), (w1, -w1, z1), r * .4, m, n=4, smooth=False)
                    a.rod((w0, -w0, z0), (-w1, -w1, z1), r * .4, m, n=4, smooth=False)
    # the great arches between the legs at the foot
    for k in range(4):
        with a.at(0, 0, 0, rz=k * 90):
            w = _tw(4)
            pts = [(-w + 2 * w * j / 12, -_tw(4 + 9 * math.sin(math.pi * j / 12)), 2 + 12 * math.sin(math.pi * j / 12)) for j in range(13)]
            a.tube(pts, .35, INTL_ORANGE, n=5, joints=False)
    for z, h, m in ((44, 5.5, white), (98, 4.5, white)):
        w = _tw(z) + 1.6
        a.box(-w, -w, z, w, w, z + h, m, bevel=.4)
        a.box(-w - .05, -w - .05, z + 1.2, w + .05, w + .05, z + h - 1.0, B_GLASS_L)
        a.box(-w - .3, -w - .3, z + h, w + .3, w + .3, z + h + .5, INTL_ORANGE)
    a.cyl(0, 0, 150, 166, .5, white, n=6, r2=.15)
    a.cyl(0, 0, 156, 157, .9, INTL_ORANGE, n=8)
    a.sphere(0, 0, 166.4, .5, E(0xFF4D4D, 4.0), seg=8, rings=5)
    a.box(-9, -9, 0, 9, 9, 7, CONCRETE, bevel=.3)
    a.box(-9.05, -9.05, 1.2, 9.05, 9.05, 5.6, B_GLASS)
    a.box(-10, -10, 7, 10, 10, 7.6, CONCRETE_D)


@landmark(16, 6)
def torii(a):
    """A great vermilion gate: two leaning pillars, the tie beam, and the lintel curving up at its ends."""
    black = M(0x1B1D21, "PAINT")
    for sx in (-1, 1):
        a.lathe(sx * 4.6, 0, [(0, 0), (.95, 0), (.95, .5), (.7, .7), (0, .7)], GRANITE_D, n=12)
        a.rod((sx * 4.6, 0, .7), (sx * 4.25, 0, 9.4), .55, VERMILION, n=14, r2=.46)
        a.rod((sx * 4.55, 0, .7), (sx * 4.53, 0, 1.5), .6, black, n=14)
    a.box(-5.9, -.3, 7.0, 5.9, .3, 7.7, VERMILION)
    a.box(-.4, -.34, 7.7, .4, .34, 9.3, VERMILION)
    a.box(-.3, -.36, 7.9, .3, .36, 9.1, GOLD)
    # lintel and its cap: straight in the middle, lifting toward the tips
    n = 16
    for k in range(n):
        x0 = -7.4 + 14.8 * k / n
        x1 = -7.4 + 14.8 * (k + 1) / n
        l0 = max(0.0, abs(x0) - 4.6) * .17 if abs(x0) > abs(x1) else max(0.0, abs(x1) - 4.6) * .17
        l1 = max(0.0, abs((x0 + x1) / 2) - 4.6) * .17
        with a.at(0, 0, l1):
            a.box(x0, -.42, 9.3, x1 + .01, .42, 9.95, VERMILION)
            a.box(x0, -.62, 9.95, x1 + .01, .62, 10.35, black)
            a.box(x0, -.56, 10.35, x1 + .01, .56, 10.5, COPPER)
    a.box(-9, -3, -.2, 9, 3, 0, GRANITE)


@landmark(60, 20)
def ferris_wheel(a):
    """A 60 m observation wheel on two A-frames, its gondolas hanging level."""
    white = M(0xF2F2F2, "PAINT")
    R, hub = 28.0, 32.0
    for sy in (-1, 1):
        y = sy * 3.2
        for sx in (-1, 1):
            a.rod((sx * 13, sy * 6.5, 0), (0, y, hub), .7, white, n=8, r2=.45)
        a.rod((-6.5, sy * 4.85, hub / 2), (6.5, sy * 4.85, hub / 2), .3, white, n=6)
        a.box(-15, sy * 6.5 - 2, 0, 15, sy * 6.5 + 2, 1.0, CONCRETE)
    a.rod((0, -3.6, hub), (0, 3.6, hub), 1.5, GOLD, n=16)
    a.rod((0, -3.8, hub), (0, 3.8, hub), .7, white, n=10)
    spokes = 24
    cols = (RED, YELLOW, BLUE, GREEN, ORANGE, PURPLE)
    for sy in (-1, 1):
        y = sy * 1.6
        a.torus(0, y, hub, R, .32, white, n=48, k=5, axis='y')
        a.torus(0, y, hub, R * .62, .16, white, n=36, k=4, axis='y')
        for k in range(spokes):
            ang = 2 * math.pi * k / spokes
            a.rod((0, y, hub), (R * math.cos(ang), y, hub + R * math.sin(ang)), .1, white, n=4, smooth=False)
    for k in range(spokes):
        ang = 2 * math.pi * k / spokes
        x, z = R * math.cos(ang), hub + R * math.sin(ang)
        a.rod((x, -1.6, z), (x, 1.6, z), .14, white, n=5)
        a.box(x - .95, -1.1, z - 2.7, x + .95, 1.1, z - .7, cols[k % len(cols)], bevel=.2)
        a.box(x - .97, -.9, z - 2.0, x + .97, .9, z - 1.0, B_GLASS_S)
        a.box(x - .75, -1.12, z - 2.0, x + .75, 1.12, z - 1.0, B_GLASS_S)
        a.rod((x, 0, z), (x, 0, z - .7), .08, white, n=4)
    # the boarding platform and its little station
    a.box(-9, -5, 1.0, 9, 5, 2.6, CONCRETE_D)
    a.box(-4, -6.5, 2.6, 4, -4.5, 5.6, white, bevel=.1)
    a.box(-3.6, -6.55, 3.4, 3.6, -6.5, 5.0, B_GLASS)
    hip_roof(a, -4.6, -7.1, 4.6, -3.9, 5.6, 1.4, M(0xC0392B, "ROOF"))


@landmark(24, 24)
def monument(a):
    """A hero's monument: an obelisk on a stepped base, a bronze group at its foot, a flag flying beside it."""
    for k, (w, h) in enumerate(((11, .5), (9.5, .5), (8, .5), (5.6, 2.2))):
        z = sum(x[1] for x in ((11, .5), (9.5, .5), (8, .5), (5.6, 2.2))[:k])
        a.box(-w, -w, z, w, w, z + h, GRANITE if k < 3 else GRANITE_D, bevel=.08)
    a.lathe(0, 0, [(2.2, 0), (2.2, .6), (1.7, 1.0), (1.25, 12.5), (0, 14.2)], CREAM_S, n=4, z=3.7, smooth=False)
    a.box(-1.5, -2.9, 3.7, 1.5, -2.2, 4.3, GRANITE_D)
    # the bronze group: a standing figure with a book, two more seated at his feet
    with a.at(0, -2.55, 4.3):
        a.loft([(0, 0, 0, .34, .26), (0, 0, 1.2, .3, .24), (0, 0, 2.0, .42, .28), (0, 0, 2.5, .2, .18)], BRONZE, n=10)
        a.sphere(0, 0, 2.75, .22, BRONZE, seg=10, rings=7)
        a.loft([(0, 0, 1.6, .5, .32), (0, .05, .2, .62, .4)], BRONZE, n=10, caps=False)
        a.box(-.5, -.34, 1.5, -.2, -.14, 1.9, BRONZE)
    for sx in (-1, 1):
        with a.at(sx * 1.6, -2.3, 3.7):
            a.loft([(0, 0, 0, .5, .45), (0, 0, .7, .38, .32), (0, .05, 1.3, .32, .26), (0, .05, 1.55, .16, .15)], BRONZE, n=8)
            a.sphere(0, 0, 1.75, .19, BRONZE, seg=8, rings=6)
    a.box(-1.6, -5.62, 2.3, 1.6, -5.6, 3.2, BRONZE)
    # the flagpole, off to one side, the flag caught mid-ripple
    a.lathe(8.5, -7, [(0, 0), (.5, 0), (.5, .4), (.16, .6), (.1, 24), (0, 24.2)], M(0xE9EDF2, "METAL"), n=8)
    a.sphere(8.5, -7, 24.3, .22, GOLD, seg=8, rings=5)
    n = 10
    for k in range(n):
        x0 = 8.6 + 6.0 * k / n
        x1 = 8.6 + 6.0 * (k + 1) / n
        y0 = -7 + .35 * (k / n) * math.sin(k * .9)
        y1 = -7 + .35 * ((k + 1) / n) * math.sin((k + 1) * .9)
        d0 = .25 * (k / n) ** 2
        d1 = .25 * ((k + 1) / n) ** 2
        a.face([(x0, y0, 23.6 - d0), (x1, y1, 23.6 - d1), (x1, y1, 21.9 - d1), (x0, y0, 21.9 - d0)], BLUE, two_sided=True)
        a.face([(x0, y0, 21.9 - d0), (x1, y1, 21.9 - d1), (x1, y1, 20.2 - d1), (x0, y0, 20.2 - d0)], RED, two_sided=True)
    a.face([(8.6, -7.02, 23.6), (11.4, -7.02, 21.9), (8.6, -7.02, 20.2)], WHITE, two_sided=True)
    a.ico(9.5, -7.05, 21.9, .32, YELLOW, sub=1, scale=(1, .1, 1))
    for k in range(4):
        with a.at(0, 0, 0, rz=k * 90):
            a.cyl(-10.2, -10.2, 1.0, 1.9, .1, GRANITE_D, n=6)
            a.sphere(-10.2, -10.2, 2.0, .2, GRANITE_D, seg=8, rings=5)
            a.rod((-10.2, -10.2, 1.5), (10.2, -10.2, 1.5), .04, IRON, n=4)


def _arch(hw, hh, z0=0.0, n=10):
    """Outline of a round-headed opening hw wide and hh to the top of the arch, as (x, z)."""
    pts = [(-hw, z0)]
    for k in range(n + 1):
        ang = math.pi - k * math.pi / n
        pts.append((hw * math.cos(ang), z0 + hh - hw + hw * math.sin(ang)))
    pts.append((hw, z0))
    return pts


@landmark(26, 44)
def cathedral(a):
    """A twin-towered stone cathedral: a rose window over three arched doors, a red roof, a dome at the crossing."""
    wall = M(0xD8CBB4, "STONE")
    trim = M(0xEFE6D2, "STONE")
    roof = M(0xA34B34, "ROOF")
    a.box(-13, -22, 0, 13, 22, .6, GRANITE)
    a.box(-9, -18, .6, 9, 20, 15, wall)
    # the roof: a long gable, and side aisles under their own lean-to roofs
    a.extrude([(-9.6, 15), (0, 22.5), (9.6, 15)], -18, 20.4, roof, axis='y')
    for sx in (-1, 1):
        x0, x1 = sorted((sx * 9, sx * 12.5))
        a.box(x0, -15, .6, x1, 19, 8, wall)
        a.extrude([(sx * 9, 11), (sx * 12.9, 8), (sx * 9, 8)] if sx > 0 else [(sx * 9, 11), (sx * 9, 8), (sx * 12.9, 8)], -15, 19, roof, axis='y')
        for k in range(6):
            y = -12 + k * 5.6
            a.box(sx * 12.5 - .7, y - .6, .6, sx * 12.5 + .7, y + .6, 9.4, trim)
            a.box(sx * 12.52 - .03, y + 1.8, 2.6, sx * 12.52 + .03, y + 3.8, 6.6, B_GLASS_L)
    # the west front between two towers
    a.box(-9, -19.2, .6, 9, -18, 17.5, wall)
    a.extrude([(-9, 17.5), (0, 24), (9, 17.5)], -19.2, -18, wall, axis='y')
    for hw, hh, x in ((2.0, 6.5, 0), (1.3, 4.6, -5.4), (1.3, 4.6, 5.4)):
        a.extrude([(p[0] + x, p[1]) for p in _arch(hw + .5, hh + .6, .6)], -19.5, -19.2, trim, axis='y')
        a.extrude([(p[0] + x, p[1]) for p in _arch(hw, hh, .6)], -19.56, -19.5, M(0x5D4037, "WOOD"), axis='y')
    a.rod((0, -19.2, 12.6), (0, -19.5, 12.6), 3.0, trim, n=24)
    a.rod((0, -19.5, 12.6), (0, -19.56, 12.6), 2.5, B_GLASS_L, n=24)
    for k in range(8):
        ang = k * math.pi / 4
        a.rod((0, -19.6, 12.6), (2.5 * math.cos(ang), -19.6, 12.6 + 2.5 * math.sin(ang)), .1, trim, n=4)
    a.rod((0, -19.56, 12.6), (0, -19.64, 12.6), .6, trim, n=12)
    for sx in (-1, 1):
        x = sx * 10.5
        a.box(x - 3.5, -21, .6, x + 3.5, -14, 26, wall)
        for z in (9, 17, 26):
            a.box(x - 3.8, -21.3, z, x + 3.8, -13.7, z + .7, trim)
        for z in (11.5, 19.5):
            a.box(x - 1.0, -21.06, z, x + 1.0, -21, z + 4.2, M(0x3C3A36, "PLAIN"))
            a.box(x - 1.3, -21.1, z - .4, x + 1.3, -21, z, trim)
        a.box(x - 2.9, -20.4, 26.7, x + 2.9, -14.6, 32, wall)
        for k in range(4):
            with a.at(x, -17.5, 0, rz=k * 90):
                a.box(-.8, -2.96, 27.6, .8, -2.9, 31, M(0x3C3A36, "PLAIN"))
        a.lathe(x, -17.5, [(3.4, 0), (3.2, .8), (2.6, 2.6), (1.4, 4.4), (.5, 5.4), (.5, 6.6), (0, 6.9)], COPPER, n=8, z=32)
        a.box(x - .1, -17.6, 38.9, x + .1, -17.4, 41.6, GOLD)
        a.box(x - .8, -17.6, 40.3, x + .8, -17.4, 40.5, GOLD)
    # the dome over the crossing
    a.cyl(0, 10, 15, 24, 6.4, wall, n=16)
    for k in range(8):
        with a.at(0, 10, 0, rz=k * 45 + 22.5):
            a.box(-.9, -6.46, 17.5, .9, -6.4, 22, B_GLASS_L)
    a.cyl(0, 10, 24, 24.8, 6.8, trim, n=16)
    a.lathe(0, 10, [(6.4, 0), (6.1, 2.2), (5.0, 4.4), (3.2, 6.0), (1.2, 6.9), (1.2, 8.6), (0, 9.4)], COPPER, n=16, z=24.8)
    a.box(-.12, 9.88, 34.2, .12, 10.12, 37.2, GOLD)
    a.box(-.9, 9.88, 35.8, .9, 10.12, 36.0, GOLD)


@landmark(30, 14)
def palace_gate(a):
    """A palace gate: a stone base pierced by three arches, a two-tiered pavilion with sweeping tiled roofs on top."""
    red = M(0x9E2B25, "PAINT")
    a.box(-15, -6, 0, 15, 6, 7, GRANITE, bevel=.15)
    for hw, x in ((2.4, 0), (1.9, -7.5), (1.9, 7.5)):
        a.extrude([(p[0] + x, p[1]) for p in _arch(hw, 5.6 if x == 0 else 4.8)], -6.1, 6.1, M(0x2A2622, "PLAIN"), axis='y')
        a.extrude([(p[0] + x, p[1]) for p in _arch(hw + .35, (5.6 if x == 0 else 4.8) + .4)], -6.06, -6.0, CREAM_S, axis='y')
    a.box(-15.3, -6.3, 7, 15.3, 6.3, 7.5, GRANITE_D)
    for k in range(20):
        a.box(-14.8 + k * 1.5, -6.3, 7.5, -13.9 + k * 1.5, -5.9, 8.2, GRANITE)

    def tier(z, w, d, h, eave):
        for i in range(int(w / 2.6) + 1):
            x = -w / 2 + i * (w / int(w / 2.6))
            for y in (-d / 2, d / 2):
                a.cyl(x, y, z, z + h, .3, red, n=10)
        a.box(-w / 2 + .3, -d / 2 + .3, z, w / 2 - .3, d / 2 - .3, z + h, M(0x7A2A22, "PAINT"))
        a.box(-w / 2 - .2, -d / 2 - .2, z + h - .9, w / 2 + .2, d / 2 + .2, z + h, DANCHEONG)
        a.box(-w / 2 - .3, -d / 2 - .3, z + h - .3, w / 2 + .3, d / 2 + .3, z + h - .1, GOLD)
        # the roof: a hip with its corners lifted
        x0, y0, x1, y1 = -w / 2 - eave, -d / 2 - eave, w / 2 + eave, d / 2 + eave
        hip_roof(a, x0, y0, x1, y1, z + h, 2.6, GIWA)
        for sx in (-1, 1):
            for sy in (-1, 1):
                a.rod((sx * (w / 2 + eave * .2), sy * (d / 2 + eave * .2), z + h + .9), (sx * (w / 2 + eave + .5), sy * (d / 2 + eave + .5), z + h + .7), .22, GIWA, n=5, r2=.1)
        a.box(-(w - d) / 2 - .4, -.25, z + h + 2.5, (w - d) / 2 + .4, .25, z + h + 3.0, M(0xE9EDF2, "PLASTER"))

    tier(7.5, 22, 8, 4.4, 2.2)
    tier(14.6, 17, 6, 3.6, 2.0)
    a.box(-2.4, -4.3, 10.6, 2.4, -4.1, 11.6, M(0x1B1D21, "PAINT"))
    a.box(-2.1, -4.36, 10.8, 2.1, -4.3, 11.4, GOLD)


@landmark(20, 6)
def paifang(a):
    """A Chinatown arch: four red columns under three tiers of green glazed roofs. The inner pair stands on the kerbs
    of a street 13 m across and the outer pair on its sidewalks, as the game spaces them (render/Landmarks.kt), so the
    game only has to stretch it a little to the street it spans."""
    green = M(0x2F7D4F, "ROOF")
    red = M(0xC0392B, "PAINT")
    for x, h in ((-9.2, 8.5), (-6.7, 11), (6.7, 11), (9.2, 8.5)):
        a.box(x - .75, -.75, 0, x + .75, .75, 1.2, GRANITE, bevel=.1)
        a.cyl(x, 0, 1.2, h, .42, red, n=12)
        a.box(x - .6, -.6, h - .5, x + .6, .6, h, GOLD)
    for x0, x1, z in ((-9.2, -6.7, 7.0), (6.7, 9.2, 7.0), (-6.7, 6.7, 9.4)):
        a.box(x0, -.35, z - 1.2, x1, .35, z, DANCHEONG)
        a.box(x0, -.38, z - .5, x1, .38, z - .35, GOLD)
        w = x1 - x0
        cx = (x0 + x1) / 2
        hip_roof(a, cx - w / 2 - 1.3, -1.9, cx + w / 2 + 1.3, 1.9, z + 1.5, 1.6, green)
        a.box(x0 - .6, -1.2, z, x1 + .6, 1.2, z + 1.5, red)
        a.box(cx - w / 2 - 1.3, -1.95, z + 1.4, cx + w / 2 + 1.3, 1.95, z + 1.56, GOLD)
        for sx in (-1, 1):
            a.rod((cx + sx * (w / 2 + .9), 0, z + 2.0), (cx + sx * (w / 2 + 1.9), 0, z + 2.5), .16, green, n=5, r2=.06)
    a.box(-2.7, -.42, 8.3, 2.7, .42, 9.25, M(0x1F3A5F, "PAINT"))
    a.box(-2.4, -.46, 8.45, 2.4, .46, 9.1, GOLD)
    # red lanterns hung under the middle span
    for x in (-3.7, 0.0, 3.7):
        a.lathe(x, 0, [(0, 0), (.3, 0), (.5, .35), (.5, .9), (.3, 1.25), (0, 1.25)], E(0xFF4A1F, 1.6), n=10, z=6.3)
        a.rod((x, 0, 7.55), (x, 0, 8.2), .02, BLACK, n=3)


@landmark(46, 30)
def city_hall(a):
    """A city hall: a colonnaded front under a pediment, and a clock tower with a copper dome."""
    wall = M(0xE4DAC6, "STONE")
    trim = M(0xF2EBDC, "STONE")
    a.box(-23, -15, 0, 23, 15, 1.2, GRANITE)
    for k in range(6):
        a.box(-9 + k * .0, -15 - (6 - k) * .45, 0, 9, -15, .2 * (k + 1), GRANITE)
    a.box(-22, -13, 1.2, 22, 14, 15, wall)
    a.box(-22.4, -13.4, 14.2, 22.4, 14.4, 15.4, trim)
    a.box(-21, -12, 15.4, 21, 13, 16, M(0x3A3F45, "ROOF"))
    for floor in range(3):
        z = 3.0 + floor * 4.0
        for k in range(14):
            x = -20 + k * 3.08
            if abs(x) < 9.5:
                continue
            a.box(x - .8, -13.06, z, x + .8, -13, z + 2.6, B_GLASS)
            a.box(x - 1.0, -13.1, z - .25, x + 1.0, -13, z, trim)
    # the portico
    a.box(-9.5, -15, 1.2, 9.5, -13, 1.6, GRANITE)
    for k in range(6):
        x = -8.2 + k * 3.28
        a.cyl(x, -14.2, 1.6, 12.2, .62, trim, n=14, r2=.52)
        a.box(x - .8, -15, 12.2, x + .8, -13.4, 12.8, trim)
        a.box(x - .8, -15, 1.6, x + .8, -13.4, 2.0, trim)
    a.box(-9.6, -15.1, 12.8, 9.6, -13, 14.2, trim)
    a.extrude([(-9.9, 14.2), (0, 18), (9.9, 14.2)], -15.2, -13, trim, axis='y')
    a.extrude([(-8.2, 14.5), (0, 17.3), (8.2, 14.5)], -15.26, -15.2, wall, axis='y')
    a.box(-1.8, -13.06, 1.6, 1.8, -13, 6.4, M(0x5D4037, "WOOD"))
    # the tower: shaft, clock stage, belfry, dome and lantern
    a.box(-4.5, -4.5, 15, 4.5, 4.5, 36, wall)
    a.box(-4.9, -4.9, 35.4, 4.9, 4.9, 36.4, trim)
    a.box(-4.2, -4.2, 36.4, 4.2, 4.2, 43, wall)
    for k in range(4):
        with a.at(0, 0, 0, rz=k * 90):
            a.rod((0, -4.2, 39.7), (0, -4.3, 39.7), 2.5, trim, n=24)
            a.rod((0, -4.3, 39.7), (0, -4.34, 39.7), 2.2, E(0xFFF6DC, .6), n=24)
            a.box(-.07, -4.4, 39.7, .07, -4.36, 41.5, BLACK)
            a.box(-.06, -4.4, 39.64, 1.1, -4.36, 39.76, BLACK)
            for z in (18, 24, 30):
                a.box(-.7, -4.56, z, .7, -4.5, z + 2.8, B_GLASS)
            for sx in (-1, 1):
                a.cyl(sx * 2.6, -3.4, 44, 49, .4, trim, n=8)
    a.box(-4.6, -4.6, 43, 4.6, 4.6, 44, trim)
    a.box(-4.2, -4.2, 49, 4.2, 4.2, 50, trim)
    a.lathe(0, 0, [(4.0, 0), (3.8, 1.5), (3.0, 3.4), (1.6, 4.8), (.7, 5.4), (.7, 7.2), (0, 8.0)], COPPER, n=12, z=50)
    a.cyl(0, 0, 58, 62, .08, GOLD, n=5)
    a.sphere(0, 0, 58.3, .4, GOLD, seg=8, rings=6)


def build_landmarks():
    coll = category("Landmarks")
    obs = []
    for name, w, d, fn in LANDMARKS:
        a = Asset(name)
        fn(a)
        obs.append(a.finish(coll, footprint=[w, d]))
    return obs
