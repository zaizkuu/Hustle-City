"""The rest of the catalog's furniture: kitchen, gym, fashion, office, cinema and club pieces. Needs kit.py and
furniture.py in front (it adds to FURN).

Same rules as furniture.py: ids match data/Catalog.kt, footprints are in tiles, the front (-Y) is the side customers
walk up to, and what hangs on a wall hangs on the back edge (+Y).
"""

BRICK_R = M(0x8D4B2D, "BRICK")
BURGUNDY = F(0x7A1E2C)
BRASS = M(0xC9A227, "METAL")
NEON_PINK = E(0xFF8AD8, 4.0)
NEON_BLUE = E(0x82B1FF, 4.0)


def knobs(a, x0, x1, y, z, n, m=BLACK, r=.028):
    for k in range(n):
        x = x0 + (x1 - x0) * (k + .5) / n
        a.rod((x, y, z), (x, y - .03, z), r, m, n=8)
        a.box(x - .004, y - .034, z, x + .004, y - .03, z + r, WHITE)


@furn(2, 1)
def grill(a):
    a.box(-1, -.45, .12, 1, .4, .95, STEEL, bevel=.012)
    legs4(a, -.92, -.38, .92, .33, 0, .12, .03, GUN, n=6)
    # the hot plate: a ridged half and a flat half, a grease trough along the front
    a.box(-.92, -.36, .95, .92, .34, .985, DARK)
    for k in range(14):
        a.box(-.9 + k * .062, -.34, .985, -.87 + k * .062, .32, 1.0, IRON)
    a.box(.02, -.34, .985, .9, .32, .995, GUN)
    a.box(-.92, -.43, .93, .92, -.37, .96, GUN)
    knobs(a, -.9, .9, -.45, .78, 6)
    a.box(-.9, -.455, .3, -.05, -.45, .62, GUN)
    a.box(.05, -.455, .3, .9, -.45, .62, GUN)
    for x in (-.48, .48):
        a.rod((x - .3, -.47, .56), (x + .3, -.47, .56), .012, CHROME, n=6)
    # splash back and a shelf over it
    a.box(-1, .35, .95, 1, .5, 1.6, STEEL, bevel=.008)
    a.box(-1, .2, 1.56, 1, .5, 1.6, STEEL)
    # a few things on the heat
    for x, y in ((-.6, -.1), (-.35, .12), (-.7, .16)):
        a.cyl(x, y, 1.0, 1.03, .055, M(0x7A4A2B, "FOOD"), n=10)
    for x in (.3, .55):
        a.box(x, -.15, .995, x + .16, .1, 1.01, M(0xB5542F, "FOOD"), bevel=.004)


@furn(1, 1)
def fryer(a):
    a.box(-.45, -.45, .12, .45, .4, .95, STEEL, bevel=.012)
    legs4(a, -.38, -.38, .38, .33, 0, .12, .03, GUN, n=6)
    oil = M(0xF2C94C, "FOOD")
    for sx in (-1, 1):
        x = sx * .2
        a.box(x - .17, -.33, .9, x + .17, .28, .96, DARK)
        a.box(x - .15, -.31, .93, x + .15, .26, .965, oil)
        # a basket hanging in the oil, its handle over the front edge
        for k in range(5):
            a.rod((x - .12 + k * .06, -.26, .97), (x - .12 + k * .06, .2, .97), .004, CHROME, n=4)
        for k in range(7):
            a.rod((x - .12, -.26 + k * .076, .97), (x + .12, -.26 + k * .076, .97), .004, CHROME, n=4)
        a.tube([(x, -.26, .97), (x, -.36, 1.02), (x, -.5, 1.02)], .012, BLACK, n=6)
    knobs(a, -.4, .4, -.45, .78, 2)
    a.box(-.4, -.455, .25, .4, -.45, .62, GUN)
    a.box(-.45, .35, .95, .45, .5, 1.45, STEEL, bevel=.008)
    a.box(-.3, .34, 1.15, .3, .35, 1.35, GUN)


@furn(1, 1)
def shower(a):
    a.box(-.5, -.5, 0, .5, .5, .1, WHITE, bevel=.02)
    a.box(-.4, -.4, .1, .4, .4, .105, M(0xD5DBE2, "TILE"))
    a.cyl(0, 0, .1, .11, .05, CHROME, n=10)
    # tiled back wall, glass sides in a slim frame
    a.box(-.5, .44, 0, .5, .5, 2.1, M(0xE9EDF2, "TILE"))
    for k in range(1, 7):
        a.box(-.5, .437, k * .3, .5, .44, k * .3 + .008, M(0xB8C0CA, "PLAIN"))
    for sx in (-1, 1):
        x0, x1 = sorted((sx * .47, sx * .5))
        a.box(x0, -.5, .1, x1, .44, 2.05, GLASS)
        a.box(x0 - .005, -.5, 2.05, x1 + .005, .44, 2.1, CHROME)
        a.box(x0 - .005, -.5, .1, x1 + .005, -.47, 2.1, CHROME)
    # riser, head, mixer and a shelf
    a.tube([(0, .42, 1.05), (0, .42, 2.0), (0, .2, 2.0)], .014, CHROME, n=6)
    a.cyl(0, .2, 1.95, 2.0, .1, CHROME, n=14, r2=.03)
    a.rod((0, .44, 1.1), (0, .38, 1.1), .045, CHROME, n=10)
    a.rod((0, .38, 1.1), (0, .36, 1.1), .02, RED, n=6)
    a.box(.2, .36, 1.3, .42, .44, 1.32, CHROME)
    a.cyl(.27, .4, 1.32, 1.46, .025, M(0x56CCF2, "PLASTIC"), n=8)
    a.cyl(.35, .4, 1.32, 1.43, .025, M(0xF28AB2, "PLASTIC"), n=8)


@furn(1, 1)
def fitness_board(a):
    # a planning board on a stand: the week across, the classes down
    for x in (-.38, .38):
        a.rod((x, .2, 0), (x, .46, .9), .018, STEEL, n=6)
        a.rod((x, .46, .9), (x, .46, 1.8), .018, STEEL, n=6)
        a.box(x - .03, .0, 0, x + .03, .3, .03, GUN)
    a.box(-.45, .42, .8, .45, .5, 1.8, M(0xD5DBE2, "METAL"), bevel=.008)
    a.box(-.42, .414, .83, .42, .42, 1.77, WHITE)
    a.box(-.42, .41, 1.66, .42, .414, 1.77, BLUE)
    for k in range(1, 7):
        a.box(-.42 + k * .12, .411, .83, -.416 + k * .12, .414, 1.66, GREY)
    for k in range(1, 5):
        a.box(-.42, .411, .83 + k * .166, .42, .414, .834 + k * .166, GREY)
    for i, (cx, cz, m) in enumerate(((0, 0, RED), (2, 1, GREEN), (4, 0, ORANGE), (1, 3, PURPLE), (5, 2, SKY), (3, 4, YELLOW), (6, 3, RED), (2, 4, BLUE))):
        a.box(-.41 + cx * .12, .408, .85 + cz * .166, -.31 + cx * .12, .411, .97 + cz * .166, m)
    a.box(-.42, .38, .8, .42, .42, .82, M(0xD5DBE2, "METAL"))
    a.rod((-.2, .39, .83), (-.08, .39, .83), .01, BLACK, n=5)
    a.rod((0, .39, .83), (.12, .39, .83), .01, RED, n=5)


@furn(1, 1)
def fitting_room(a):
    wall = M(0xD5DBE2, "PLASTER")
    a.box(-.5, .44, 0, .5, .5, 2.1, wall)
    a.box(-.5, -.5, 0, -.44, .5, 2.1, wall)
    a.box(.44, -.5, 0, .5, -.44, 2.1, M(0xB8C0CA, "METAL"))
    # a rail and a curtain in soft folds, drawn across the front
    a.rod((-.47, -.47, 2.02), (.47, -.47, 2.02), .012, CHROME, n=6)
    cur = F(0x9B51E0)
    pts = []
    for k in range(13):
        pts.append((-.42 + k * .07, -.47 + (.022 if k % 2 else -.022)))
    a.extrude(pts + [(p[0], p[1] + .012) for p in reversed(pts)], .3, 2.0, cur, axis='z')
    for k in range(7):
        a.torus(-.4 + k * .135, -.47, 2.02, .018, .004, CHROME, n=8, k=4, axis='y')
    # mirror, a hook with a hanger, a stool
    a.box(-.3, .425, .5, .3, .44, 1.9, M(0x9AA3AD, "METAL"))
    a.box(-.27, .42, .53, .27, .426, 1.87, M(0xBFE3F5, "GLASS", alpha=1))
    a.rod((-.44, .1, 1.7), (-.38, .1, 1.7), .012, CHROME, n=6)
    a.tube([(-.38, -.08, 1.6), (-.38, .1, 1.68), (-.38, .28, 1.6)], .008, WOOD_L, n=5)
    a.cyl(.22, .15, 0, .42, .02, STEEL, n=6)
    a.cyl(.22, .15, .42, .47, .16, F(0x5B4B8A), n=12)


@furn(1, 1)
def neon_circle(a):
    a.box(-.42, .46, .95, .42, .5, 1.85, M(0x1B1D21, "PAINT"))
    # a ring of two tubes, pink below and blue above, with a third stroke through the middle
    n = 20
    for k in range(n):
        a0 = 2 * math.pi * k / n
        a1 = 2 * math.pi * (k + 1) / n
        m = NEON_PINK if (k + 5) % n < n // 2 else NEON_BLUE
        a.rod((.35 * math.cos(a0), .43, 1.4 + .35 * math.sin(a0)), (.35 * math.cos(a1), .43, 1.4 + .35 * math.sin(a1)), .022, m, n=6)
    for k in range(n):
        a0 = 2 * math.pi * k / n
        a.sphere(.35 * math.cos(a0), .43, 1.4 + .35 * math.sin(a0), .022, NEON_PINK if (k + 5) % n < n // 2 else NEON_BLUE, seg=6, rings=4)
    a.rod((-.2, .43, 1.3), (.2, .43, 1.5), .016, E(0xFFE14F, 4.0), n=6)


@furn(1, 1)
def wall_lamp(a):
    a.box(-.07, .47, 1.55, .07, .5, 1.8, BRASS, bevel=.008)
    a.tube([(0, .47, 1.66), (0, .38, 1.66), (0, .36, 1.78)], .012, BRASS, n=6)
    a.lathe(0, .36, [(.05, 0), (.1, .2), (.095, .2), (.045, 0)], E(0xFFF3C4, 2.5), n=12, z=1.78, loop=True)
    a.sphere(0, .36, 1.84, .035, E(0xFFF8E1, 5.0), seg=8, rings=5)
    a.cyl(0, .36, 1.76, 1.79, .05, BRASS, n=10)


def flat_cart(a, handle=BLUE):
    """A flatbed store cart, 0.82 wide and 0.8 deep, at the origin: handle at the back (+Y)."""
    a.box(-.41, -.4, .2, .41, .4, .27, GUN, bevel=.01)
    a.box(-.41, -.4, .27, .41, .4, .3, M(0x7D8A96, "RUBBER"))
    for sx in (-1, 1):
        for y in (-.3, .3):
            a.rod((sx * .33 - .025, y, .08), (sx * .33 + .025, y, .08), .08, RUBBER, n=10)
            a.box(sx * .33 - .035, y - .03, .14, sx * .33 + .035, y + .03, .2, STEEL)
        a.rod((sx * .36, .37, .3), (sx * .36, .37, 1.05), .02, handle, n=6)
    a.rod((-.36, .37, 1.05), (.36, .37, 1.05), .02, handle, n=6)
    a.rod((-.36, .37, .7), (.36, .37, .7), .012, handle, n=5)


@furn(3, 1)
def cart_rack(a):
    for k in range(3):
        with a.at(-.98 + k * .98, 0, 0):
            flat_cart(a)
    # the bay they are kept in: a yellow line on the floor and a sign
    a.box(-1.48, -.49, 0, 1.48, -.45, .006, YELLOW)
    a.box(-1.48, .45, 0, 1.48, .49, .006, YELLOW)


@furn(3, 3)
def dance_floor(a):
    a.box(-1.5, -1.5, 0, 1.5, 1.5, .06, M(0x1A1A2E, "PLASTIC"), bevel=.015)
    cols = (0xFF4FD8, 0x4FC3FF, 0xFFE14F, 0x4FFF9A, 0xFF7A4F, 0xB04FFF, 0x4FC3FF, 0xFFE14F, 0xFF4FD8)
    for j in range(3):
        for i in range(3):
            x = -1.5 + i * 1.0
            y = 1.5 - j * 1.0
            a.box(x + .08, y - .92, .06, x + .92, y - .08, .08, E(cols[j * 3 + i], 1.6))
            # each panel is four lamps behind frosted glass
            a.box(x + .49, y - .92, .08, x + .51, y - .08, .083, M(0x1A1A2E, "PLASTIC"))
            a.box(x + .08, y - .51, .08, x + .92, y - .49, .083, M(0x1A1A2E, "PLASTIC"))
    for k in range(4):
        with a.at(0, 0, 0, rz=k * 90):
            a.box(-1.5, -1.5, .06, 1.5, -1.47, .085, CHROME)


@furn(4, 1)
def cinema_screen(a):
    a.box(-2, .2, 0, 2, .5, .5, DARK, bevel=.02)
    a.box(-2, .19, .42, 2, .2, .46, E(0x56CCF2, 1.5))
    # the screen in a black mask, heavy curtains gathered at each side, a valance over the top
    a.box(-1.95, .33, .48, 1.95, .47, 2.72, BLACK)
    a.box(-1.82, .32, .58, 1.82, .34, 2.6, M(0xF5F5F5, "SCREEN", emit=.9))
    for sx in (-1, 1):
        pts = []
        for k in range(7):
            pts.append((sx * (1.56 + k * .07), .27 + (.035 if k % 2 else -.02)))
        a.extrude(pts + [(p[0], .45) for p in reversed(pts)], .5, 2.7, BURGUNDY, axis='z')
        a.box(sx * 1.7 - .14, .22, 1.2, sx * 1.7 + .14, .3, 1.27, BRASS)
    for k in range(10):
        x = -1.95 + k * .39
        a.extrude([(x, 2.72), (x + .39, 2.72), (x + .39, 2.5), (x + .195, 2.42), (x, 2.5)], .24, .32, BURGUNDY, axis='y')
    a.box(-2, .22, 2.7, 2, .5, 2.78, BRASS)
    for sx in (-1, 1):
        a.box(sx * 1.3 - .14, .3, .08, sx * 1.3 + .14, .34, .4, M(0x16181B, "FABRIC"))


@furn(2, 1)
def concession_stand(a):
    red = M(0xC0392B, "PAINT")
    a.box(-1, -.45, 0, 1, .35, 1.0, red, bevel=.012)
    a.box(-1.02, -.47, .96, 1.02, .37, 1.01, CREAM, bevel=.008)
    for k in range(8):
        a.box(-1 + k * .25, -.455, .08, -.875 + k * .25, -.45, .92, CREAM)
    a.box(-1, -.46, 0, 1, .36, .08, DARK)
    # the popcorn machine: a glass case on a red base with a kettle and a heap of popcorn
    a.box(-.88, -.22, 1.01, -.12, .22, 1.1, red, bevel=.01)
    a.box(-.85, -.2, 1.1, -.15, .2, 1.62, GLASS)
    for x in (-.85, -.15):
        for y in (-.2, .2):
            a.box(x - .012, y - .012, 1.1, x + .012, y + .012, 1.62, CHROME)
    a.box(-.88, -.22, 1.62, -.12, .22, 1.7, red, bevel=.01)
    a.box(-.8, -.225, 1.63, -.2, -.22, 1.69, YELLOW)
    pop = M(0xF7E7A0, "FOOD")
    for dx, dy, r in ((0, 0, .13), (.14, .05, .1), (-.14, -.04, .1), (.05, -.1, .09), (-.06, .1, .09)):
        a.ico(-.5 + dx, dy, 1.13 + r * .5, r, pop, sub=1)
    a.cyl(-.5, 0, 1.44, 1.56, .09, STEEL, n=10)
    a.rod((-.5, 0, 1.56), (-.5, 0, 1.62), .01, STEEL, n=4)
    # two drinks dispensers and a stack of cups
    for x, m in ((.3, M(0x56CCF2, "PLASTIC")), (.65, M(0xEB5757, "PLASTIC"))):
        a.box(x - .14, -.18, 1.01, x + .14, .2, 1.12, DARK, bevel=.01)
        a.box(x - .12, -.14, 1.12, x + .12, .16, 1.46, M(0xDDEAF2, "GLASS", alpha=.5))
        a.box(x - .11, -.13, 1.12, x + .11, .15, 1.38, m)
        a.box(x - .14, -.18, 1.46, x + .14, .2, 1.52, DARK, bevel=.01)
        a.rod((x, -.18, 1.08), (x, -.24, 1.08), .015, CHROME, n=6)
    for k in range(5):
        a.cyl(-.02, -.3, 1.01 + k * .03, 1.13 + k * .03, .035, M(0xF5F2EA, "PAPER"), n=8, r2=.045)
    a.box(.82, -.3, 1.01, .98, -.1, 1.14, M(0xF2C94C, "PAPER"))


@furn(4, 2)
def stage(a):
    a.box(-2, -1, 0, 2, 1, .56, M(0x3E2723, "WOOD"))
    a.box(-2.02, -1.02, .56, 2.02, 1.02, .6, WOOD, bevel=.01)
    for k in range(1, 10):
        a.box(-2 + k * .4, -1, .6, -1.994 + k * .4, 1, .602, WOOD_D)
    # footlights along the front edge, steps up at one side
    for k in range(7):
        x = -1.65 + k * .55
        a.box(x - .08, -.99, .6, x + .08, -.9, .66, DARK)
        a.box(x - .06, -.9, .61, x + .06, -.895, .65, E(0xFFF3C4, 3.0))
    for k in range(2):
        a.box(1.3, -1.02 - (2 - k) * .22, 0, 1.9, -1.02, .2 + k * .2, WOOD_D)
    # proscenium: posts, a valance, and curtains gathered at each side of the back
    for sx in (-1, 1):
        a.box(sx * 2 - (.25 if sx > 0 else 0), .75, .6, sx * 2 + (.25 if sx < 0 else 0), 1, 2.8, BURGUNDY)
        pts = []
        for k in range(8):
            pts.append((sx * (1.1 + k * .09), .86 + (.03 if k % 2 else -.03)))
        a.extrude(pts + [(p[0], .98) for p in reversed(pts)], .6, 2.62, F(0x8E2436), axis='z')
    a.box(-2, .8, 2.6, 2, 1, 2.8, BURGUNDY)
    for k in range(10):
        x = -2 + k * .4
        a.extrude([(x, 2.6), (x + .4, 2.6), (x + .2, 2.44)], .8, .86, BRASS, axis='y')
    a.box(-1.1, .95, .6, 1.1, .99, 2.6, M(0x1B1D21, "FABRIC"))
    # a microphone on a stand, centre stage, and a monitor wedge
    a.cyl(0, 0, .6, .62, .13, IRON, n=12)
    a.cyl(0, 0, .62, 1.95, .012, CHROME, n=6)
    a.rod((0, 0, 1.95), (0, -.1, 2.03), .012, CHROME, n=6)
    a.sphere(0, -.12, 2.05, .035, GUN, seg=8, rings=5)
    a.extrude([(-.75, .6), (-.75, .84), (-.5, .92), (-.4, .6)], -.4, .4, M(0x16181B, "PLASTIC"), axis='x')
    a.box(-.36, -.752, .64, .36, -.75, .82, M(0x2E3238, "FABRIC"))


@furn(1, 1)
def graphic_tablet(a):
    a.box(-.35, -.35, .68, .35, .35, .72, WOOD_L, bevel=.008)
    legs4(a, -.31, -.31, .31, .31, 0, .68, .02, STEEL, n=6)
    a.box(-.31, .29, .3, .31, .31, .6, WOOD_L)
    # the tablet propped at a drawing angle, a pen, and a mug of them
    with a.at(0, -.02, .72, rx=14):
        a.box(-.25, -.2, 0, .25, .2, .03, DARK, bevel=.008)
        a.box(-.2, -.15, .03, .2, .15, .033, SCREEN)
        a.box(-.24, -.19, .03, -.215, .19, .034, GREY_D)
    a.rod((.12, -.2, .745), (.28, -.26, .735), .006, WHITE, n=5)
    a.cyl(-.26, .24, .72, .82, .035, M(0xEB5757, "PLASTIC"), n=10)
    a.rod((-.26, .24, .8), (-.24, .25, .9), .005, BLACK, n=4)
    a.rod((-.27, .23, .8), (-.29, .22, .89), .005, BLUE, n=4)


@furn(1, 1)
def desk_globe(a):
    a.box(-.2, -.2, 0, .2, .2, .7, WOOD, bevel=.015)
    a.box(-.22, -.22, .66, .22, .22, .7, WOOD_D, bevel=.008)
    a.cyl(0, 0, .7, .73, .12, BRASS, n=14)
    a.cyl(0, 0, .73, .82, .015, BRASS, n=6)
    # the globe on a tilted axis inside its meridian ring
    with a.at(0, 0, 1.04, ry=23):
        a.sphere(0, 0, 0, .2, M(0x2D9CDB, "PAINT"), seg=18, rings=12)
        a.torus(0, 0, 0, .225, .01, BRASS, n=20, k=5, axis='y')
        a.cyl(0, 0, -.235, .235, .006, BRASS, n=5)
        land = M(0x5FB36A, "PAINT")
        for lat, lon, r in ((35, 20, .09), (10, 60, .075), (-25, 40, .06), (45, 150, .1), (5, 200, .07), (-30, 230, .055), (50, 280, .08), (-10, 300, .06)):
            la, lo = math.radians(lat), math.radians(lon)
            a.ico(.2 * math.cos(la) * math.cos(lo), .2 * math.cos(la) * math.sin(lo), .2 * math.sin(la), r, land, sub=1, scale=(.5, .5, .5))
    a.rod((0, 0, .82), (0, 0, .84), .03, BRASS, n=8)


@furn(1, 1)
def desk_calendar(a):
    a.box(-.45, .45, 1.0, .45, .5, 1.8, WHITE, bevel=.006)
    a.box(-.4, .443, 1.6, .4, .45, 1.75, RED)
    for k in range(8):
        a.box(-.4 + k * .1, .444, 1.1, -.396 + k * .1, .45, 1.55, GREY)
    for k in range(6):
        a.box(-.4, .444, 1.1 + k * .09, .4, .45, 1.104 + k * .09, GREY)
    for i, j, m in ((1, 0, BLUE), (3, 1, ORANGE), (4, 3, GREEN), (6, 2, PURPLE), (2, 4, RED), (5, 4, YELLOW)):
        a.box(-.39 + i * .1, .442, 1.11 + j * .09, -.31 + i * .1, .446, 1.18 + j * .09, m)
    for x in (-.3, .3):
        a.torus(x, .44, 1.79, .02, .005, CHROME, n=8, k=4, axis='y')


@furn(1, 2)
def rowing_machine(a):
    # the flywheel is at the back (+Y), which the rower faces; the seat slides on the rail
    a.box(-.06, -.9, .12, .06, .7, .2, STEEL, bevel=.01)
    for y in (-.86, .5):
        a.box(-.25, y - .04, 0, .25, y + .04, .12, DARK, bevel=.01)
    a.rod((-.07, .72, .32), (.07, .72, .32), .24, DARK, n=18)
    a.rod((-.075, .72, .32), (.075, .72, .32), .1, GUN, n=10)
    for k in range(8):
        ang = k * math.pi / 4
        a.rod((.072, .72 + .1 * math.cos(ang), .32 + .1 * math.sin(ang)), (.072, .72 + .22 * math.cos(ang), .32 + .22 * math.sin(ang)), .008, GREY, n=4)
    a.rod((0, .6, .5), (0, .52, .95), .025, GUN, n=6)
    with a.at(0, .5, .98, rx=-25):
        a.box(-.1, -.02, -.07, .1, .02, .07, DARK, bevel=.01)
        a.box(-.08, -.024, -.05, .08, -.02, .05, SCREEN)
    # seat, foot plates, the handle on its chain
    a.box(-.15, -.25, .2, .15, .1, .3, BLACK, bevel=.03, seg=2)
    for sx in (-1, 1):
        with a.at(sx * .13, .34, .2, rx=-40):
            a.box(-.06, -.02, 0, .06, .02, .3, GREY_D)
            a.box(-.065, -.03, .1, .065, -.02, .14, BLACK)
        a.cyl(sx * .12, -.08, .12, .2, .03, RUBBER, n=8)
    a.rod((-.22, .2, .62), (.22, .2, .62), .016, RUBBER, n=6)
    a.rod((0, .2, .62), (0, .55, .5), .005, CHROME, n=4)


@furn(2, 1)
def coat_check(a):
    plum = M(0x3B2A4A, "WOOD")
    a.box(-1, -.5, 0, 1, .2, 1.0, plum, bevel=.012)
    a.box(-1.02, -.52, 1.0, 1.02, .22, 1.05, M(0x241A2E, "WOOD"), bevel=.008)
    for k in range(4):
        a.box(-.94 + k * .49, -.505, .12, -.56 + k * .49, -.5, .9, M(0x4A385C, "WOOD"))
    # the wall behind, a rail of coats on hangers, numbered tags
    a.box(-1, .4, 0, 1, .5, 2.0, M(0x5D4E6D, "PLASTER"))
    a.rod((-.92, .32, 1.92), (.92, .32, 1.92), .014, BRASS, n=6)
    for k, col in enumerate((0x2F80ED, 0xEB5757, 0x333333, 0xC69A6B, 0x27AE60, 0x8E3B4E)):
        x = -.78 + k * .31
        a.rod((x, .32, 1.92), (x, .32, 1.86), .004, CHROME, n=4)
        a.extrude([(x - .15, 1.78), (x - .06, 1.86), (x + .06, 1.86), (x + .15, 1.78), (x + .13, 1.2), (x - .13, 1.2)], .27, .37, F(col), axis='y')
        a.box(x - .012, .262, 1.3, x + .012, .27, 1.84, F(_dark_hex(col)))
        a.box(x - .03, .255, 1.72, x + .03, .262, 1.78, PAPER)
    # the bell, the tickets and a tip jar on the counter
    a.lathe(-.6, -.25, [(.06, 0), (.055, .03), (.02, .055), (0, .06)], BRASS, n=12, z=1.05)
    a.box(.2, -.36, 1.05, .42, -.2, 1.07, PAPER)
    a.cyl(.72, -.28, 1.05, 1.2, .05, M(0xDDEAF2, "GLASS", alpha=.45), n=10)
    a.cyl(.72, -.28, 1.05, 1.1, .04, M(0x7FB77E, "PAPER"), n=8)


def _dark_hex(c, f=.7):
    return (int(((c >> 16) & 255) * f) << 16) | (int(((c >> 8) & 255) * f) << 8) | int((c & 255) * f)


@furn(2, 1)
def pizza_oven(a):
    a.box(-1, -.45, 0, 1, .4, .9, STEEL, bevel=.012)
    a.box(-.9, -.455, .15, -.05, -.45, .8, GUN)
    a.box(.05, -.455, .15, .9, -.45, .8, GUN)
    for x in (-.48, .48):
        a.rod((x - .3, -.47, .72), (x + .3, -.47, .72), .012, CHROME, n=6)
    # a brick dome with an arched mouth, embers inside, a steel flue
    a.box(-.92, -.38, .9, .92, .36, .98, M(0xB8C0CA, "STONE"))
    with a.at(0, 0, .98):
        a.sphere(0, 0, 0, .9, BRICK_R, seg=20, rings=12, scale=(1, .42, .72))
    mouth = [(-.34, .98)]
    for k in range(9):
        ang = math.pi - k * math.pi / 8
        mouth.append((.34 * math.cos(ang), 1.2 + .22 * math.sin(ang)))
    mouth.append((.34, .98))
    a.extrude(mouth, -.39, -.3, M(0x1B1D21, "PLAIN"), axis='y')
    a.extrude([(p[0] * .82, .98 + (p[1] - .98) * .8) for p in mouth], -.395, -.39, E(0xFF7A2F, 2.0), axis='y')
    for k in range(9):
        ang = math.pi - (k + .5) * math.pi / 9
        with a.at(.41 * math.cos(ang), -.37, 1.2 + .29 * math.sin(ang), ry=90 - math.degrees(ang)):
            a.box(-.06, -.03, -.03, .06, .03, .03, M(0xA85A38, "BRICK"))
    a.cyl(0, .12, 1.55, 2.2, .14, STEEL, n=12)
    a.cyl(0, .12, 2.2, 2.26, .19, STEEL, n=12, r2=.1)
    # a peel leaning at the side and a pizza waiting on the ledge
    a.rod((.82, -.44, .92), (.9, -.3, 2.0), .012, WOOD_L, n=5)
    a.box(.7, -.5, .9, .94, -.42, .93, WOOD_L)
    a.cyl(-.6, -.4, .98, 1.0, .16, M(0xE8B96A, "FOOD"), n=14)
    a.cyl(-.6, -.4, 1.0, 1.006, .135, M(0xD0392B, "FOOD"), n=14)
    for dx, dy in ((0, 0), (.06, .05), (-.07, .02), (.02, -.07)):
        a.cyl(-.6 + dx, -.4 + dy, 1.006, 1.012, .025, M(0xF5E6A8, "FOOD"), n=6)


@furn(1, 1)
def digital_scale(a):
    a.box(-.3, -.3, 0, .3, .3, .9, M(0xD5DBE2, "PAINT"), bevel=.015)
    a.box(-.3, -.305, .1, .3, -.3, .14, GREY)
    a.box(-.35, -.35, .9, .35, .35, .93, STEEL, bevel=.008)
    a.box(-.32, -.32, .93, .32, .2, .95, CHROME)
    # the display on its post at the customer's side, a keypad under it, a roll of labels
    a.box(-.03, -.3, .93, .03, -.24, 1.14, GUN)
    with a.at(0, -.27, 1.2, rx=-18):
        a.box(-.2, -.04, -.1, .2, .04, .1, DARK, bevel=.012)
        a.box(-.16, -.045, -.01, .16, -.04, .07, M(0x6FCF97, "SCREEN"))
        for i in range(4):
            for j in range(2):
                a.box(-.15 + i * .08, -.045, -.085 + j * .035, -.09 + i * .08, -.04, -.06 + j * .035, GREY)
    a.rod((.2, .27, .96), (.3, .27, .96), .045, PAPER, n=10)
    # something being weighed
    a.box(-.14, -.1, .95, .14, .12, 1.0, M(0xF5F2EA, "PAPER"), bevel=.006)
    for dx, dy in ((-.05, 0), (.05, .02), (0, -.04)):
        a.ico(dx, dy, 1.03, .045, M(0xEB5757, "FOOD"), sub=1, smooth=True)
