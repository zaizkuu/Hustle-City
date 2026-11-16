"""Street furniture, trees and kerbside things. Needs kit.py in front. The front (-Y) faces the road."""

PROPS = []


def prop(fn):
    PROPS.append((fn.__name__, fn))
    return fn


POST = M(0x2A2D31, "METAL")
GREEN_D = M(0x2F5D43, "METAL")
BARK = M(0x6B4A32, "WOOD")
BARK_D = M(0x4E3524, "WOOD")
CONC = M(0xB9BDC2, "CONCRETE")
CONC_D = M(0x8E9399, "CONCRETE")
LAMP = E(0xFFE3A0, 4.0)
SIGNAL_R = E(0xFF4D4D, 3.0)
SIGNAL_Y = M(0x8A6A1E, "PLASTIC")
SIGNAL_G = M(0x1E5A36, "PLASTIC")


# ---------------------------------------------------------------------------------------------- lights and signs

@prop
def lamp_post(a):
    """Sized like the game's own: a 6.2 m pole whose arm reaches 1.6 m out over the road."""
    a.lathe(0, 0, [(0, 0), (.2, 0), (.2, .08), (.12, .16), (.1, .56), (.075, .62), (0, .62)], POST, n=10)
    a.cyl(0, 0, .56, 5.7, .07, POST, n=8, r2=.05)
    a.tube([(0, 0, 5.7), (0, -.3, 6.1), (0, -1.6, 6.14)], .045, POST, n=6)
    a.lathe(0, -1.6, [(.0, .16), (.14, .13), (.32, 0), (.27, 0), (.14, .05), (0, .05)], POST, n=10, z=5.98)
    a.lathe(0, -1.6, [(0, -.09), (.16, -.05), (.26, .0), (0, .0)], LAMP, n=10, z=5.98)
    a.torus(0, 0, 1.3, .085, .025, POST, n=8, k=4)


@prop
def traffic_light(a):
    a.lathe(0, 0, [(0, 0), (.18, 0), (.18, .08), (.09, .16), (0, .16)], POST, n=10)
    a.cyl(0, 0, .1, 5.4, .07, POST, n=8, r2=.05)
    a.tube([(0, 0, 5.3), (0, -.3, 5.6), (0, -3.2, 5.7)], .045, POST, n=6)
    # two signal heads, one out on the arm over the lanes and one on the pole, both looking along the road (+X) at
    # the traffic coming, at right angles to the arm
    for hx, hy, z in ((0.0, -3.0, 5.0), (.2, 0.0, 2.9)):
        a.box(hx - .13, hy - .17, z - .5, hx + .13, hy + .17, z + .5, M(0x1B1D21, "PAINT"), bevel=.02)
        for k, m in enumerate((SIGNAL_G, SIGNAL_Y, SIGNAL_R)):
            zz = z - .32 + k * .32
            a.rod((hx + .13, hy, zz), (hx + .15, hy, zz), .1, m, n=10)
            a.extrude([(hy - .12, zz + .06), (hy + .12, zz + .06), (hy + .12, zz + .13), (hy - .12, zz + .13)], hx + .13, hx + .26,
                      M(0x1B1D21, "PAINT"), axis='x')
    a.rod((0, -3.0, 5.7), (0, -3.0, 5.5), .03, POST, n=5)
    # walk signal and push button
    a.box(.08, -.1, 2.0, .22, .1, 2.3, M(0x1B1D21, "PAINT"), bevel=.01)
    a.box(.22, -.07, 2.04, .225, .07, 2.26, E(0x2EE87A, 2.0))
    a.box(-.06, -.1, 1.1, .06, -.07, 1.3, YELLOW, bevel=.008)


@prop
def street_sign(a):
    a.cyl(0, 0, 0, 3.2, .035, STEEL, n=6)
    a.box(-.6, -.02, 2.9, .6, .02, 3.15, M(0x1F6F43, "PAINT"), bevel=.008)
    a.box(-.5, -.025, 2.97, .3, -.02, 3.08, WHITE)
    with a.at(0, 0, 2.62, rz=90):
        a.box(-.6, -.02, 0, .6, .02, .25, M(0x1F6F43, "PAINT"), bevel=.008)
        a.box(-.45, -.025, .07, .35, -.02, .18, WHITE)
    a.cyl(0, 0, 3.2, 3.26, .05, STEEL, n=6)
    # stop sign lower down
    with a.at(0, -.04, 2.05):
        a.rod((0, 0, 0), (0, -.02, 0), .34, RED, n=8, smooth=False)
        a.box(-.2, -.025, -.05, .2, -.02, .05, WHITE)


@prop
def parking_meter(a):
    a.cyl(0, 0, 0, .03, .1, CONC_D, n=8)
    a.cyl(0, 0, 0, 1.05, .03, STEEL, n=6)
    a.box(-.09, -.07, 1.05, .09, .07, 1.35, GUN, bevel=.02)
    a.lathe(0, 0, [(.0, .08), (.07, .06), (.09, 0), (0, 0)], GUN, n=8, z=1.35)
    a.box(-.06, -.075, 1.2, .06, -.07, 1.3, SCREEN)
    a.box(-.03, -.075, 1.1, .03, -.07, 1.14, BLACK)


@prop
def billboard(a):
    for x in (-2.2, 2.2):
        a.box(x - .12, -.12, 0, x + .12, .12, 5.0, GUN)
        a.rod((x, .12, 3.2), (x, 1.4, 0.1), .05, GUN, n=5)
    a.box(-3.6, -.25, 5.0, 3.6, .1, 8.2, M(0x1B1D21, "PAINT"), bevel=.03)
    a.box(-3.45, -.27, 5.15, 3.45, -.25, 8.05, M(0xF2C94C, "PAINT"))
    a.extrude([(-3.45, 5.15), (3.45, 5.15), (3.45, 6.1), (1.0, 7.0), (-3.45, 6.0)], -.29, -.27, M(0xEB5757, "PAINT"), axis='y')
    a.ico(1.9, -.29, 7.1, .55, WHITE, scale=(1, .04, 1), sub=2)
    for k in range(3):
        a.box(-3.0, -.3, 7.2 - k * .38, -3.0 + (2.6, 2.0, 1.4)[k], -.29, 7.45 - k * .38, M(0x1B1D21, "PAINT"))
    a.box(-3.7, -.9, 4.85, 3.7, -.25, 4.95, GUN)
    for k in range(4):
        x = -2.7 + k * 1.8
        a.rod((x, -.85, 4.95), (x, -.75, 5.2), .03, POST, n=5)
        a.sphere(x, -.72, 5.24, .07, LAMP, seg=6, rings=4)


# ---------------------------------------------------------------------------------------------- kerbside

@prop
def fire_hydrant(a):
    red = M(0xD0392B, "PAINT")
    a.lathe(0, 0, [(0, 0), (.15, 0), (.15, .05), (.11, .08), (.11, .5), (.13, .52), (.13, .58), (.1, .6), (.07, .72), (.03, .76), (.03, .8), (0, .8)], red, n=10)
    for sx in (-1, 1):
        a.rod((sx * .1, 0, .42), (sx * .19, 0, .42), .055, red, n=8)
        a.rod((sx * .19, 0, .42), (sx * .21, 0, .42), .04, STEEL, n=5, smooth=False)
    a.rod((0, -.1, .34), (0, -.2, .34), .07, red, n=8)
    a.rod((0, -.2, .34), (0, -.22, .34), .05, STEEL, n=5, smooth=False)


@prop
def park_bench(a):
    for x in (-.8, .8):
        a.extrude([(-.24, 0), (-.18, 0), (-.16, .4), (.16, .4), (.18, 0), (.24, 0), (.22, .44), (-.22, .44)], x - .03, x + .03, GREEN_D, axis='x')
        a.rod((x, .18, .44), (x, .28, .9), .025, GREEN_D, n=6)
        a.tube([(x, -.22, .44), (x, -.24, .64), (x, .2, .66)], .02, GREEN_D, n=5)
    for k in range(4):
        y = -.22 + k * .115
        a.box(-.95, y, .44, .95, y + .095, .48, M(0x9C6B43, "WOOD"), bevel=.008)
    for k in range(3):
        with a.at(0, .21 + k * .028, .56 + k * .12, rx=-12):
            a.box(-.95, -.016, 0, .95, .016, .1, M(0x9C6B43, "WOOD"), bevel=.008)


@prop
def bus_shelter(a):
    for x in (-1.9, 1.9):
        a.box(x - .05, .45, 0, x + .05, .55, 2.5, GUN)
        a.box(x - .05, -.75, 0, x + .05, -.65, 2.5, GUN)
    a.extrude([(-.9, 2.5), (.7, 2.5), (.7, 2.62), (-.9, 2.72)], -2.05, 2.05, M(0x3C5266, "GLASS", alpha=.8), axis='x')
    a.box(-2.05, -.9, 2.46, 2.05, .7, 2.52, GUN)
    a.box(-1.85, .48, .3, 1.85, .52, 2.3, M(0xBFE3F5, "GLASS", alpha=.4))
    a.box(1.88, -.6, .3, 1.92, .45, 2.3, M(0xBFE3F5, "GLASS", alpha=.4))
    # advert panel on the left end, lit
    a.box(-1.96, -.6, .4, -1.84, .45, 2.2, DARK, bevel=.01)
    a.box(-1.83, -.5, .5, -1.84, .35, 2.1, E(0xFFF4D6, 1.2))
    a.box(-1.825, -.4, .9, -1.83, .25, 1.7, M(0xEB5757, "PAINT"))
    # bench, route sign
    a.box(-1.2, .1, .42, 1.2, .42, .48, M(0x9C6B43, "WOOD"), bevel=.01)
    for x in (-1.0, 1.0):
        a.box(x - .03, .2, 0, x + .03, .3, .42, GUN)
    a.cyl(2.3, -.6, 0, 2.9, .035, STEEL, n=6)
    a.rod((2.3, -.6, 2.6), (2.3, -.62, 2.6), .22, BLUE, n=12)
    a.box(2.18, -.635, 2.52, 2.42, -.62, 2.68, WHITE)


@prop
def trash_bin(a):
    a.lathe(0, 0, [(0, 0), (.24, 0), (.27, .8), (.3, .8), (.3, .86), (.2, .96), (0, .98)], GREEN_D, n=10)
    for k in range(10):
        ang = math.radians(k * 36 + 18)
        a.rod((.25 * math.cos(ang), .25 * math.sin(ang), .1), (.275 * math.cos(ang), .275 * math.sin(ang), .76), .012, M(0x244A35, "METAL"), n=4)
    a.box(-.13, -.3, .82, .13, -.2, .9, BLACK)


@prop
def newspaper_box(a):
    blue = M(0x2F5D8C, "PAINT")
    a.box(-.22, -.18, 0, .22, .2, .1, POST)
    a.box(-.04, -.04, .1, .04, .04, .5, POST)
    a.box(-.25, -.2, .5, .25, .22, 1.2, blue, bevel=.02)
    a.box(-.2, -.21, .62, .2, -.2, 1.0, GLASS)
    a.box(-.18, -.205, .66, .18, -.2, .96, PAPER)
    a.box(-.15, -.208, .86, .15, -.205, .93, BLACK)
    a.box(-.1, -.23, 1.03, .1, -.2, 1.07, CHROME)
    a.box(-.2, -.21, 1.1, .2, -.2, 1.17, YELLOW)


@prop
def mailbox(a):
    blue = M(0x2F5D8C, "PAINT")
    for x in (-.2, .2):
        for y in (-.18, .18):
            a.box(x - .025, y - .025, 0, x + .025, y + .025, .2, blue)
    a.extrude([(-.25, .2), (.25, .2), (.25, .95), (.18, 1.1), (0, 1.16), (-.18, 1.1), (-.25, .95)], -.23, .23, blue, axis='y')
    a.box(-.2, -.25, .85, .2, -.23, .98, M(0x244A70, "PAINT"), bevel=.008)
    a.box(-.12, -.27, .9, .12, -.25, .92, CHROME)
    a.box(-.15, -.235, .45, .15, -.23, .65, WHITE)


@prop
def planter(a):
    a.box(-.6, -.3, 0, .6, .3, .5, CONC, bevel=.03)
    a.box(-.54, -.24, .46, .54, .24, .52, SOIL)
    for k, (x, y, r, m) in enumerate(((-.35, 0, .2, LEAF), (0, .05, .24, LEAF_D), (.35, -.02, .2, LEAF_L), (-.15, -.1, .14, LEAF_L), (.2, .1, .15, LEAF))):
        a.ico(x, y, .52 + r * .7, r, m, rz=k * 40, scale=(1, 1, .9))
    for k, (x, y) in enumerate(((-.42, -.14), (-.1, .12), (.12, -.15), (.42, .12), (.3, -.1))):
        a.ico(x, y, .78 + (k % 2) * .05, .05, (RED, YELLOW, PINK, WHITE, PURPLE)[k])


@prop
def bike_rack(a):
    for k in range(3):
        x = -.6 + k * .6
        a.tube([(x, -.3, 0), (x, -.3, .7), (x, -.15, .82), (x, .15, .82), (x, .3, .7), (x, .3, 0)], .025, STEEL, n=6)
    # a bicycle leaning in the middle bay
    with a.at(.28, 0, 0, ry=-6):
        for y in (-.52, .52):
            a.torus(0, y, .34, .31, .022, RUBBER, n=16, k=5, axis='x')
            a.rod((-.02, y, .34), (.02, y, .34), .03, STEEL, n=6)
            for s in range(6):
                ang = math.radians(s * 60)
                a.rod((0, y, .34), (0, y + .3 * math.cos(ang), .34 + .3 * math.sin(ang)), .004, STEEL, n=3)
        red = M(0xD0392B, "PAINT")
        a.tube([(0, .52, .34), (0, .12, .36), (0, -.38, .82), (0, .2, .86), (0, .12, .36)], .018, red, n=5)
        a.tube([(0, .2, .86), (0, .52, .34)], .014, red, n=5)
        a.tube([(0, -.52, .34), (0, -.4, .92)], .016, red, n=5)
        a.rod((-.22, -.4, .95), (.22, -.4, .95), .014, POST, n=5)
        a.rod((0, .2, .86), (0, .23, .97), .012, POST, n=5)
        a.box(-.05, .12, .96, .05, .34, 1.0, BLACK, bevel=.015)
        a.rod((-.04, .12, .36), (.04, .12, .36), .07, POST, n=10)


@prop
def bollard(a):
    a.lathe(0, 0, [(0, 0), (.1, 0), (.1, .08), (.07, .1), (.07, .8), (.09, .84), (.07, .9), (0, .94)], POST, n=10)
    a.cyl(0, 0, .62, .7, .074, YELLOW, n=10, caps=False)


@prop
def traffic_cone(a):
    a.box(-.19, -.19, 0, .19, .19, .03, ORANGE, bevel=.01)
    a.lathe(0, 0, [(.15, .03), (.035, .68), (0, .7)], ORANGE, n=10)
    a.lathe(0, 0, [(.118, .22), (.088, .38), (.092, .38), (.122, .22)], WHITE, n=10, loop=True)
    a.lathe(0, 0, [(.074, .46), (.058, .55), (.062, .55), (.078, .46)], WHITE, n=10, loop=True)


@prop
def barrier(a):
    for x in (-.9, .9):
        a.extrude([(-.3, 0), (-.24, 0), (0, .9), (.24, 0), (.3, 0), (.04, 1.0), (-.04, 1.0)], x - .03, x + .03, STEEL, axis='x')
    for z, m in ((.75, ORANGE), (.4, ORANGE)):
        a.box(-1.1, -.03, z, 1.1, .03, z + .2, WHITE, bevel=.008)
        for k in range(6):
            x = -1.0 + k * .36
            a.extrude([(x, z + .01), (x + .14, z + .01), (x + .28, z + .19), (x + .14, z + .19)], -.034, .034, m, axis='y')
    a.sphere(.9, 0, 1.08, .07, E(0xFFD34D, 3.0), seg=8, rings=5)


@prop
def dumpster(a):
    g = M(0x2F6F4F, "METAL")
    a.extrude([(-.55, .15), (.6, .15), (.6, 1.25), (-.7, 1.05)], -.95, .95, g, axis='x')
    for x in (-.95, -.32, .32, .95):
        a.extrude([(-.56, .15), (-.5, .15), (-.66, 1.03), (-.72, 1.03)], x - .03, x + .03, M(0x25573E, "METAL"), axis='x')
    with a.at(0, .6, 1.25, rx=-9):
        a.box(-.98, -1.36, 0, -.01, .02, .05, M(0x1B1D21, "PLASTIC"))
    with a.at(0, .6, 1.25, rx=18):
        a.box(.01, -1.36, 0, .98, .02, .05, M(0x1B1D21, "PLASTIC"))
    for sx in (-1, 1):
        a.box(sx * .95 - .06 * (sx > 0), -.2, .75, sx * .95 + .06 * (sx < 0) + sx * .06, .2, .85, M(0x25573E, "METAL"))
        for y in (-.4, .45):
            a.rod((sx * .8 - .03, y, .09), (sx * .8 + .03, y, .09), .09, RUBBER, n=10)
    a.ico(.4, -.1, 1.3, .22, M(0x2E3238, "PLASTIC"), scale=(1.2, 1, .8))
    a.ico(.6, .2, 1.32, .18, WHITE, scale=(1, 1, .8), rz=40)


@prop
def vending_machine(a):
    red = M(0xD0392B, "PAINT")
    a.box(-.5, -.35, 0, .5, .4, 1.9, red, bevel=.02)
    # the display stands proud of the body: dark back, shelves of cans, glass in a frame
    a.box(-.44, -.36, .75, .2, -.35, 1.8, M(0x14181D, "PAINT"))
    for x0, z0, x1, z1 in ((-.46, .73, -.42, 1.82), (.18, .73, .22, 1.82), (-.42, .73, .18, .77), (-.42, 1.78, .18, 1.82)):
        a.box(x0, -.47, z0, x1, -.35, z1, M(0xB03226, "PAINT"))
    a.box(-.42, -.465, .77, .18, -.455, 1.78, M(0xBFE3F5, "GLASS", alpha=.3))
    tints = (RED, GREEN, BLUE, YELLOW, ORANGE, WHITE, SKY, PURPLE)
    for r in range(4):
        z = .82 + r * .24
        a.box(-.42, -.45, z - .02, .18, -.36, z, STEEL)
        for i in range(6):
            a.cyl(-.37 + i * .1, -.405, z, z + .15, .035, tints[(i + r * 3) % 8], n=8)
    a.box(.26, -.36, 1.0, .44, -.35, 1.6, DARK)
    a.box(.29, -.365, 1.42, .41, -.36, 1.55, SCREEN)
    for k in range(6):
        a.box(.29 + (k % 3) * .045, -.365, 1.26 + (k // 3) * .05, .32 + (k % 3) * .045, -.36, 1.29 + (k // 3) * .05, WHITE)
    a.box(.31, -.365, 1.08, .39, -.36, 1.1, BLACK)
    a.box(-.4, -.36, .28, .16, -.35, .55, BLACK)
    a.box(-.38, -.37, .3, .14, -.36, .5, GREY_D)
    a.box(-.5, -.36, 1.82, .5, -.35, 1.9, E(0xFFF4D6, 1.5))


@prop
def phone_booth(a):
    red = M(0xC0392B, "PAINT")
    for x in (-.45, .45):
        for y in (-.45, .45):
            a.box(x - .05, y - .05, 0, x + .05, y + .05, 2.3, red)
    a.box(-.5, -.5, 0, .5, .5, .12, red)
    a.box(-.52, -.52, 2.3, .52, .52, 2.5, red, bevel=.03)
    a.extrude([(-.5, 2.5), (.5, 2.5), (.4, 2.6), (-.4, 2.6)], -.5, .5, red, axis='y')
    for k in range(4):
        with a.at(0, 0, 0, rz=k * 90):
            a.box(-.4, -.47, .12, .4, -.45, 2.3, M(0xBFE3F5, "GLASS", alpha=.35))
            a.box(-.36, -.53, 2.34, .36, -.52, 2.46, E(0xFFF4D6, 1.5))
            for z in (.7, 1.25, 1.8):
                a.box(-.4, -.48, z, .4, -.44, z + .03, red)
    a.box(-.18, .2, 1.1, .18, .42, 1.6, DARK, bevel=.015)
    a.box(-.12, .19, 1.2, .0, .2, 1.4, STEEL)
    a.tube([(.1, .18, 1.5), (.1, .15, 1.25), (.1, .18, 1.2)], .02, BLACK, n=5)


@prop
def atm(a):
    a.box(-.4, -.25, 0, .4, .35, 1.9, M(0x2F5D8C, "PAINT"), bevel=.02)
    a.box(-.4, -.27, 1.6, .4, -.25, 1.85, E(0x5FE3FF, 1.5))
    a.extrude([(-.35, .95), (-.25, .95), (-.25, 1.5), (-.3, 1.5)], -.32, .32, DARK, axis='x')
    with a.at(0, -.3, 1.22, rx=12):
        a.box(-.2, -.01, -.15, .2, .0, .15, SCREEN)
    a.extrude([(-.4, .85), (-.25, .95), (-.25, .97), (-.4, .9)], -.32, .32, STEEL, axis='x')
    for k in range(12):
        a.box(-.1 + (k % 3) * .06, -.38 + (k // 3) * .028, .9 + (k // 3) * .018, -.06 + (k % 3) * .06, -.36 + (k // 3) * .028, .915 + (k // 3) * .018, WHITE)
    a.box(.14, -.27, 1.0, .28, -.25, 1.02, BLACK)
    a.box(-.2, -.27, .6, .2, -.25, .64, BLACK)
    a.box(-.18, -.3, .61, .18, -.25, .625, M(0x6FAF7A, "PAPER"))


# ---------------------------------------------------------------------------------------------- green

def canopy(a, x, y, z, R, mats, squash=.85, seed=0):
    spots = ((0, 0, .1, 1.0), (.62, .15, -.12, .72), (-.58, .25, -.1, .76), (.12, -.62, -.14, .7), (-.2, .6, -.05, .68),
             (.3, .3, .5, .62), (-.3, -.25, .45, .66))
    for k, (dx, dy, dz, s) in enumerate(spots):
        a.ico(x + dx * R, y + dy * R, z + dz * R, R * s, mats[(k + seed) % len(mats)], rz=k * 37 + seed * 11, scale=(1, 1, squash))


@prop
def tree_oak(a):
    a.lathe(0, 0, [(0, 0), (.3, 0), (.2, .25), (.16, 1.6), (.13, 2.6), (0, 2.6)], BARK, n=7, smooth=False)
    a.rod((0, 0, 2.2), (.8, .2, 3.4), .09, BARK, n=5, r2=.05, smooth=False)
    a.rod((0, 0, 2.3), (-.7, -.3, 3.5), .09, BARK, n=5, r2=.05, smooth=False)
    a.rod((0, 0, 2.5), (.1, .5, 3.8), .09, BARK, n=5, r2=.05, smooth=False)
    canopy(a, 0, 0, 4.3, 1.75, (LEAF, LEAF_L, LEAF_D))
    a.box(-.75, -.75, 0, .75, .75, .04, M(0x4A3728, "PLAIN"))
    for k in range(4):
        with a.at(0, 0, 0, rz=k * 90):
            a.box(-.8, -.8, 0, .8, -.72, .06, CONC_D)


@prop
def tree_cherry(a):
    pinks = (M(0xF7B7CE, "FOLIAGE"), M(0xF28AB2, "FOLIAGE"), M(0xFAD3E0, "FOLIAGE"))
    a.lathe(0, 0, [(0, 0), (.24, 0), (.15, .2), (.12, 1.3), (0, 1.3)], BARK_D, n=7, smooth=False)
    for k, (x, y, z) in enumerate(((.9, .1, 2.6), (-.8, .3, 2.7), (.1, -.8, 2.5), (-.1, .7, 2.9))):
        a.rod((0, 0, 1.2), (x, y, z), .08, BARK_D, n=5, r2=.04, smooth=False)
        canopy(a, x, y, z + .45, .95, pinks, seed=k)
    canopy(a, 0, 0, 3.4, 1.2, pinks, seed=2)
    for k in range(9):
        ang = k * 2.4
        a.ico(1.3 * math.cos(ang) * (.4 + k % 3 * .25), 1.3 * math.sin(ang) * (.4 + k % 3 * .25), .015, .05, pinks[k % 3], scale=(1, 1, .15), sub=0)


@prop
def tree_pine(a):
    a.cyl(0, 0, 0, 1.2, .16, BARK_D, n=6, r2=.12, smooth=False)
    g = (M(0x2C6E3F, "FOLIAGE"), M(0x245C36, "FOLIAGE"), M(0x347A49, "FOLIAGE"))
    for k in range(5):
        z = .9 + k * .95
        r = 1.5 - k * .27
        with a.at(0, 0, 0, rz=k * 25):
            a.lathe(0, 0, [(0, 0), (r, 0), (r * .25, 1.35), (0, 1.45)], g[k % 3], n=8, z=z, smooth=False)


@prop
def tree_palm(a):
    pts = [(0, 0, 0), (.12, 0, 1.6), (.35, 0, 3.2), (.7, 0, 4.8), (1.1, 0, 6.2)]
    for k, (p0, p1) in enumerate(zip(pts, pts[1:])):
        a.rod(p0, p1, .2 - k * .025, M(0x8B6A4E, "WOOD"), n=7, r2=.175 - k * .025, smooth=False)
    cx, cz = 1.1, 6.2
    a.sphere(cx, 0, cz, .22, M(0x6B4A32, "WOOD"), seg=8, rings=5)
    for k in range(9):
        with a.at(cx, 0, cz, rz=k * 40):
            frond = [(0, 0), (.7, .45), (1.5, .5), (2.2, .15), (2.7, -.5), (2.1, -.05), (1.4, .25), (.7, .25)]
            a.extrude(frond, -.16, .16, (LEAF, LEAF_L, LEAF_D)[k % 3], axis='y')
    for k in range(4):
        ang = k * 1.57 + .4
        a.ico(cx + .22 * math.cos(ang), .22 * math.sin(ang), cz - .28, .13, M(0x6B4A32, "FOOD"))


@prop
def bush(a):
    for k, (x, y, r, m) in enumerate(((0, 0, .5, LEAF), (.45, .1, .38, LEAF_L), (-.42, .12, .4, LEAF_D), (.1, -.35, .34, LEAF_L), (-.12, .38, .36, LEAF))):
        a.ico(x, y, r * .75, r, m, rz=k * 43, scale=(1, 1, .85))


@prop
def fountain(a):
    a.cyl(0, 0, 0, .45, 2.4, CONC, n=20)
    a.cyl(0, 0, .45, .5, 2.5, M(0xD9CFBF, "STONE"), n=20)
    a.cyl(0, 0, .38, .46, 2.15, M(0x4F9FCB, "GLASS", alpha=.8), n=20)
    a.lathe(0, 0, [(0, .4), (.35, .4), (.25, .7), (.2, 1.4), (.9, 1.6), (1.0, 1.75), (.9, 1.75), (.3, 1.65), (.2, 1.7), (.14, 2.4), (.5, 2.55),
                   (.56, 2.65), (.5, 2.65), (.12, 2.6), (0, 2.6)], M(0xD9CFBF, "STONE"), n=12)
    water = M(0x9FD4F2, "GLASS", alpha=.6)
    a.cyl(0, 0, 1.72, 1.76, .88, water, n=12)
    a.cyl(0, 0, 2.62, 2.66, .48, water, n=12)
    a.lathe(0, 0, [(0, 2.6), (.06, 2.6), (.03, 3.2), (0, 3.25)], water, n=6)
    for k in range(6):
        ang = math.radians(k * 60)
        a.rod((.95 * math.cos(ang), .95 * math.sin(ang), 1.7), (1.3 * math.cos(ang), 1.3 * math.sin(ang), .46), .04, water, n=4)


# ---------------------------------------------------------------------------------------------- trade

@prop
def hotdog_cart(a):
    a.box(-.9, -.4, .55, .9, .4, 1.1, STEEL, bevel=.02)
    a.box(-.9, -.415, .65, .9, -.4, .95, RED)
    a.box(-.5, -.42, .72, .5, -.415, .88, YELLOW)
    for x in (-.6, .6):
        a.rod((x, -.45, .32), (x, -.38, .32), .32, RUBBER, n=14)
        a.rod((x, -.47, .32), (x, -.45, .32), .12, CHROME, n=8)
        a.rod((x, .45, .32), (x, .38, .32), .32, RUBBER, n=14)
    a.box(-.85, -.35, 1.1, -.1, .35, 1.14, GUN)
    a.box(.1, -.3, 1.1, .8, .3, 1.2, STEEL, bevel=.01)
    for k in range(3):
        a.rod((.2 + k * .2, -.2, 1.22), (.2 + k * .2, .2, 1.22), .03, M(0xB5452E, "FOOD"), n=6)
    for k, m in enumerate((RED, YELLOW)):
        a.lathe(-.7 + k * .15, -.2, [(0, 0), (.04, 0), (.04, .16), (.01, .22), (0, .22)], m, n=8, z=1.14)
    a.tube([(.9, -.3, .9), (1.3, -.3, 1.0), (1.3, .3, 1.0), (.9, .3, .9)], .02, CHROME, n=5)
    # umbrella
    a.cyl(-.3, 0, 1.1, 2.5, .025, STEEL, n=6)
    for k in range(8):
        with a.at(-.3, 0, 2.3, rz=k * 45):
            a.face([(0, 0, .4), (1.2 * math.cos(math.radians(-22.5)), 1.2 * math.sin(math.radians(-22.5)), 0),
                    (1.2 * math.cos(math.radians(22.5)), 1.2 * math.sin(math.radians(22.5)), 0)], (RED, WHITE)[k % 2], two_sided=True)
    a.sphere(-.3, 0, 2.72, .05, RED, seg=6, rings=4)


@prop
def market_stall(a):
    for x in (-1.4, 1.4):
        for y in (-.7, .7):
            a.box(x - .04, y - .04, 0, x + .04, y + .04, 2.3 if y > 0 else 2.0, WOOD_D)
    for k in range(8):
        x = -1.6 + k * .4
        a.extrude([(-1.0, 1.95), (.85, 2.35), (.85, 2.4), (-1.0, 2.0)], x, x + .4, (RED, WHITE)[k % 2], axis='x')
        a.extrude([(x, 1.78), (x + .2, 1.7), (x + .4, 1.78), (x + .4, 1.97), (x, 1.97)], -1.02, -1.0, (RED, WHITE)[k % 2], axis='y')
    a.box(-1.5, -.8, .85, 1.5, .5, .92, WOOD_L, bevel=.01)
    a.box(-1.45, -.75, .1, 1.45, -.72, .85, M(0x2F5D43, "FABRIC"))
    fruit = (0xE5483B, 0xF2C94C, 0xF2994A, 0x6DBE45, 0x8E3B6E)
    for k in range(5):
        cx = -1.2 + k * .6
        with a.at(cx, -.25, .92, rx=14):
            a.box(-.26, -.3, 0, .26, .3, .14, WOOD, bevel=.008)
            for i in range(3):
                for j in range(3):
                    a.ico(-.16 + i * .16, -.18 + j * .18, .17, .08, M(fruit[k], "FOOD"), rz=i * 31 + j * 13)
    a.box(.9, .15, .92, 1.3, .4, 1.05, GUN, bevel=.01)
    a.cyl(1.1, .27, 1.05, 1.07, .16, STEEL, n=10)
    a.box(-.5, -.82, 1.25, .5, -.8, 1.55, M(0x2E3238, "PAINT"))
    a.box(-.4, -.825, 1.36, .4, -.82, 1.44, YELLOW)
    for x in (-.5, .5):
        a.rod((x, -.81, 1.55), (x, -.9, 1.95), .008, WOOD_D, n=3)


@prop
def cafe_table(a):
    a.cyl(0, 0, 0, .03, .25, POST, n=10)
    a.cyl(0, 0, .03, .72, .03, POST, n=6)
    a.cyl(0, 0, .72, .75, .4, WHITE, n=16)
    a.cyl(0, 0, .75, 2.3, .02, STEEL, n=6)
    for k in range(8):
        with a.at(0, 0, 2.1, rz=k * 45):
            a.face([(0, 0, .35), (1.15 * math.cos(math.radians(-22.5)), 1.15 * math.sin(math.radians(-22.5)), 0),
                    (1.15 * math.cos(math.radians(22.5)), 1.15 * math.sin(math.radians(22.5)), 0)], (GREEN, WHITE)[k % 2], two_sided=True)
    for k in range(2):
        with a.at((-.62, .62)[k], 0, 0, rz=(-90, 90)[k]):
            legs4(a, -.17, -.17, .17, .17, 0, .44, .018, POST, n=6)
            a.box(-.21, -.21, .44, .21, .21, .48, WOOD_L, bevel=.012)
            a.rod((-.17, .19, .48), (-.17, .23, .88), .016, POST, n=6)
            a.rod((.17, .19, .48), (.17, .23, .88), .016, POST, n=6)
            a.box(-.2, .2, .66, .2, .24, .9, WOOD_L, bevel=.012)
    a.lathe(.12, .08, [(0, 0), (.03, 0), (.042, .07), (.036, .07), (.03, .01), (0, .01)], WHITE, n=8, z=.75)


def legs4(a, x0, y0, x1, y1, z0, z1, r, m, n=8):
    for x in (x0, x1):
        for y in (y0, y1):
            a.cyl(x, y, z0, z1, r, m, n=n)


@prop
def power_pole(a):
    a.cyl(0, 0, 0, 8.0, .14, M(0x6B5A48, "WOOD"), n=7, r2=.1, smooth=False)
    for z, w in ((7.4, 1.2), (6.7, .9)):
        a.box(-w, -.05, z, w, .05, z + .1, M(0x5A4A3A, "WOOD"))
        for sx in (-1, -.5, .5, 1):
            a.cyl(sx * (w - .1), 0, z + .1, z + .22, .035, M(0x9FC4DD, "GLASS", alpha=.9), n=6)
    a.cyl(.25, 0, 5.6, 6.3, .18, GUN, n=8)
    a.rod((.0, 0, 6.0), (.25, 0, 6.0), .03, GUN, n=4)
    a.box(-.12, -.16, 2.2, .12, -.13, 2.6, M(0xE9EDF2, "PAPER"))
    a.box(-.1, -.165, 2.3, .1, -.16, 2.4, RED)
    # the tangle of low-voltage lines Manila poles carry
    for k in range(5):
        z = 5.0 - k * .12
        a.torus(0, 0, z, .17 + (k % 2) * .02, .012, BLACK, n=8, k=3)
    a.torus(.2, 0, 4.7, .16, .012, BLACK, n=8, k=3, axis='y')


@prop
def basketball_hoop(a):
    a.box(-.4, .1, 0, .4, .7, .12, CONC_D, bevel=.02)
    a.tube([(0, .4, .12), (0, .4, 2.9), (0, -.4, 3.3)], .05, POST, n=6)
    a.box(-.9, -.46, 2.9, .9, -.42, 4.0, M(0xF7FAFC, "PAINT"), bevel=.01)
    a.box(-.3, -.47, 3.05, .3, -.46, 3.5, RED)
    a.box(-.26, -.472, 3.09, .26, -.47, 3.46, M(0xF7FAFC, "PAINT"))
    a.torus(0, -.7, 3.05, .23, .014, ORANGE, n=14, k=4)
    a.box(-.04, -.5, 3.03, .04, -.46, 3.07, ORANGE)
    a.lathe(0, -.7, [(.22, 0), (.14, -.4), (.135, -.4), (.215, 0)], M(0xF7FAFC, "FABRIC"), n=8, z=3.04, loop=True, smooth=False)
    a.sphere(.5, -.3, .12, .12, M(0xD9772E, "RUBBER"), seg=10, rings=6)


@prop
def paper_lantern(a):
    a.cyl(0, 0, 0, 2.6, .04, M(0x3E2723, "WOOD"), n=6)
    a.rod((0, 0, 2.5), (0, -.5, 2.5), .03, M(0x3E2723, "WOOD"), n=5)
    a.rod((0, -.45, 2.5), (0, -.45, 2.3), .006, BLACK, n=3)
    red = E(0xFF4A1F, 1.6)
    a.lathe(0, -.45, [(0, 0), (.1, 0), (.19, .12), (.21, .28), (.19, .44), (.1, .56), (0, .56)], red, n=10, z=1.72)
    a.cyl(0, -.45, 1.68, 1.74, .11, BLACK, n=10)
    a.cyl(0, -.45, 2.26, 2.32, .11, BLACK, n=10)
    a.box(-.03, -.665, 1.85, .03, -.66, 2.15, BLACK)


def build_props():
    coll = category("Street Props")
    obs = []
    for name, fn in PROPS:
        a = Asset(name)
        fn(a)
        obs.append(a.finish(coll))
    return obs
