"""Furniture for shops, offices, gyms and home. Needs kit.py in front.

Ids match data/Catalog.kt. Footprints are in tiles (1 m); the front (-Y) is the side customers walk up to.
"""

FURN = []


def furn(w, d):
    def deco(fn):
        FURN.append((fn.__name__, w, d, fn))
        return fn
    return deco


# ---------------------------------------------------------------------------------------------- shared bits

def legs4(a, x0, y0, x1, y1, z0, z1, r, m, n=8):
    for x in (x0, x1):
        for y in (y0, y1):
            a.cyl(x, y, z0, z1, r, m, n=n)


def chair(a, seat=RED, frame=IRON):
    """A cafe chair at the origin; the sitter looks toward -Y."""
    legs4(a, -.17, -.17, .17, .17, 0, .4, .018, frame, n=6)
    a.box(-.21, -.21, .4, .21, .21, .45, seat, bevel=.015)
    a.rod((-.17, .19, .45), (-.17, .23, .88), .016, frame, n=6)
    a.rod((.17, .19, .45), (.17, .23, .88), .016, frame, n=6)
    a.box(-.2, .2, .64, .2, .245, .9, seat, bevel=.015)


def monitor(a, w=.52, h=.31, col=SCREEN):
    """A flat screen at the origin, facing -Y."""
    a.cyl(0, .02, 0, .015, .1, DARK, n=10)
    a.box(-.02, .01, 0, .02, .04, .2, DARK)
    a.box(-w / 2, -.025, .13, w / 2, .005, .13 + h, DARK, bevel=.008)
    a.box(-w / 2 + .018, -.031, .148, w / 2 - .018, -.025, .112 + h, col)


def bottle(a, x, y, z, m, s=1.0, cap=None):
    a.lathe(x, y, [(0, 0), (.035 * s, 0), (.035 * s, .15 * s), (.013 * s, .21 * s), (.013 * s, .27 * s), (0, .27 * s)], m, n=8, z=z)
    if cap is not None:
        a.cyl(x, y, z + .26 * s, z + .285 * s, .016 * s, cap, n=8)


def can_row(a, x0, x1, y, z, r, h, mats, n=6):
    count = max(1, int((x1 - x0) / (r * 2.25)))
    step = (x1 - x0) / count
    for i in range(count):
        a.cyl(x0 + step * (i + .5), y, z, z + h, r, mats[i % len(mats)], n=n)


def flowers(a, x, y, z, bloom, r=.11, h=.3):
    a.cyl(x, y, z, z + h, .035, LEAF_D, n=6, r2=r * .75)
    for k, (dx, dy, dz) in enumerate(((0, 0, .06), (.06, .03, 0), (-.06, .02, .01), (.01, -.06, .02), (-.02, .07, .03))):
        a.ico(x + dx, y + dy, z + h + dz, r * .5, bloom, rz=k * 40)


def pot_plant(a, x, y, z, pot_r, height, pot=TERRACOTTA):
    a.lathe(x, y, [(0, 0), (pot_r * .7, 0), (pot_r, pot_r * 1.5), (pot_r * 1.08, pot_r * 1.5), (pot_r * 1.08, pot_r * 1.7),
                   (pot_r * .9, pot_r * 1.7), (pot_r * .9, pot_r * 1.55), (0, pot_r * 1.55)], pot, n=10, z=z)
    a.cyl(x, y, z + pot_r * 1.5, z + pot_r * 1.58, pot_r * .9, SOIL, n=10)
    top = z + pot_r * 1.6
    a.cyl(x, y, top, top + height * .55, pot_r * .12, WOOD_D, n=6, r2=pot_r * .07)
    R = height * .26
    for k, (dx, dy, dz, s, m) in enumerate(((0, 0, .78, 1.0, LEAF), (.55, .2, .55, .75, LEAF_L), (-.5, .3, .6, .8, LEAF_D),
                                            (.1, -.55, .5, .7, LEAF_L), (-.2, -.3, .95, .6, LEAF))):
        a.ico(x + dx * R, y + dy * R, top + height * dz, R * s, m, rz=k * 37, scale=(1, 1, .85))


# ---------------------------------------------------------------------------------------------- checkout

@furn(2, 1)
def cash_register(a):
    a.box(-.96, -.34, 0, .96, .38, .08, BLACK)
    a.box(-1, -.38, .08, 1, .42, .96, GREY_D, bevel=.02)
    a.box(-.98, -.392, .5, .98, -.378, .6, BLUE)
    a.box(-1.03, -.46, .96, 1.03, .47, 1.02, GREY, bevel=.012)
    # till: drawer, sloped keypad towards the cashier (+Y), screen on a stalk
    a.box(-.78, -.2, 1.02, -.12, .32, 1.13, DARK, bevel=.012)
    a.extrude([(-.16, 1.13), (.3, 1.13), (.3, 1.17), (-.16, 1.29)], -.74, -.16, GREY_D, axis='x')
    for i in range(4):
        for j in range(3):
            a.box(-.7 + i * .13, -.02 + j * .1, 1.2 - j * .026, -.61 + i * .13, .05 + j * .1, 1.225 - j * .026, WHITE)
    with a.at(-.45, -.19, 1.13, rz=180):
        monitor(a, .34, .22)
    # card reader and a receipt curl for the customer
    a.box(.05, -.32, 1.02, .2, -.12, 1.06, DARK, bevel=.008)
    a.box(.07, -.3, 1.06, .18, -.2, 1.065, SCREEN)
    a.box(.45, -.25, 1.02, .9, .2, 1.035, RUBBER)
    a.cyl(.75, .32, 1.02, 1.12, .05, CHROME, n=8)


@furn(3, 1)
def checkout_counter(a):
    a.box(-1.46, -.34, 0, 1.46, .34, .08, BLACK)
    a.box(-1.5, -.38, .08, 1.5, .38, .86, WHITE, bevel=.02)
    a.box(-1.48, -.392, .3, 1.48, -.378, .42, GREEN)
    a.box(-1.52, -.42, .86, 1.52, .42, .92, STEEL, bevel=.01)
    # belt, end rollers and the bagging well
    a.box(-1.4, -.26, .92, .1, .26, .935, RUBBER)
    a.rod((-1.42, -.26, .92), (-1.42, .26, .92), .022, GUN, n=8)
    a.box(.55, -.3, .92, 1.42, .3, .96, STEEL, bevel=.01)
    a.box(.6, -.25, .955, 1.37, .25, .965, GREY)
    # scanner, till screen for the cashier, divider bar
    a.box(.15, -.22, .92, .5, .22, .94, DARK)
    a.box(.2, -.16, .94, .45, .16, .945, GLASS_D)
    a.box(.17, .3, .92, .47, .4, 1.25, GREY_D, bevel=.01)
    with a.at(.32, .35, 1.25, rz=180):
        monitor(a, .36, .24)
    a.rod((-.9, -.24, .96), (-.9, .24, .96), .02, RED, n=6)
    a.box(1.2, -.38, .96, 1.5, -.3, 1.1, DARK, bevel=.01)
    a.box(1.22, -.385, 1.0, 1.48, -.38, 1.08, SCREEN)


# ---------------------------------------------------------------------------------------------- displays

@furn(2, 1)
def shelf(a):
    a.box(-1, .3, 0, 1, .45, 1.9, WOOD)
    a.box(-1, -.45, 0, -.93, .45, 1.9, WOOD, bevel=.012)
    a.box(.93, -.45, 0, 1, .45, 1.9, WOOD, bevel=.012)
    a.box(-1.02, -.47, 1.9, 1.02, .47, 1.96, WOOD_D, bevel=.012)
    for z in (.08, .52, .98, 1.44):
        a.box(-.93, -.43, z, .93, .3, z + .045, WOOD_L)
        a.box(-.93, -.44, z - .03, .93, -.425, z + .052, WHITE)
    # bottom: detergent boxes; then cereal, cans, bottles
    cols = (RED, YELLOW, GREEN, BLUE, ORANGE, PURPLE, SKY)
    x = -.88
    for i in range(7):
        w = .2 + .04 * (i % 3)
        a.box(x, -.3, .125, x + w, .2, .125 + .28 + .03 * (i % 2), cols[i % 7], bevel=.008)
        x += w + .035
    x = -.88
    for i in range(8):
        a.box(x, -.3, .565, x + .17, -.05, .565 + .3, cols[(i * 3 + 1) % 7])
        a.box(x + .02, -.305, .7, x + .15, -.3, .82, PAPER)
        x += .225
    can_row(a, -.88, .9, -.28, 1.025, .055, .14, (RED, STEEL, GREEN, YELLOW), n=8)
    can_row(a, -.88, .9, -.14, 1.025, .055, .14, (STEEL, GREEN, YELLOW, RED), n=8)
    for i in range(10):
        bottle(a, -.82 + i * .18, -.25, 1.485, (M(0x3E7C4F, "GLASS", alpha=.85), M(0x7A3B2E, "GLASS", alpha=.85), M(0x2C5E7A, "GLASS", alpha=.85))[i % 3],
               cap=(YELLOW, RED, WHITE)[i % 3])


@furn(1, 1)
def drinks_fridge(a):
    a.box(-.45, .4, 0, .45, .45, 2.0, WHITE)
    a.box(-.45, -.36, 0, -.4, .4, 2.0, WHITE)
    a.box(.4, -.36, 0, .45, .4, 2.0, WHITE)
    a.box(-.4, -.36, 0, .4, .4, .27, WHITE)
    a.box(-.4, -.36, 1.73, .4, .4, 2.0, WHITE)
    a.box(-.4, -.38, .1, .4, -.36, .22, DARK)
    for i in range(6):
        a.box(-.34 + i * .12, -.385, .13, -.28 + i * .12, -.38, .19, GREY)
    # lit header, door frame, glass, handle
    a.box(-.42, -.385, 1.78, .42, -.36, 1.96, RED)
    a.box(-.3, -.39, 1.83, .3, -.385, 1.91, E(0xFFF4D6, 1.5))
    for x0, z0, x1, z1 in ((-.41, .26, -.36, 1.74), (.36, .26, .41, 1.74), (-.36, .26, .36, .31), (-.36, 1.69, .36, 1.74)):
        a.box(x0, -.4, z0, x1, -.37, z1, GREY_D)
    a.box(-.36, -.405, .31, .36, -.39, 1.69, GLASS)
    a.box(.3, -.44, .7, .33, -.4, 1.3, CHROME, bevel=.008)
    for k, z in enumerate((.32, .66, 1.0, 1.34)):
        a.box(-.38, -.34, z - .02, .38, .38, z, STEEL)
        mats = ((RED, RED, STEEL), (GREEN, YELLOW, GREEN), (BLUE, SKY, BLUE), (ORANGE, PURPLE, ORANGE))[k]
        for row in range(2):
            can_row(a, -.36, .36, -.27 + row * .13, z, .05, .15 if k % 2 else .22, mats, n=8)


@furn(2, 1)
def fresh_fridge(a):
    a.box(-1, -.42, 0, 1, .45, .42, WHITE, bevel=.02)
    a.box(-1, -.435, .12, 1, -.42, .22, MINT)
    a.box(-1, .3, .42, 1, .45, 1.95, WHITE)
    side = [(-.42, .42), (.45, .42), (.45, 1.95), (-.22, 1.95), (-.22, 1.78), (.26, 1.62), (.26, .62), (-.42, .5)]
    a.extrude(side, -1, -.95, CREAM, axis='x')
    a.extrude(side, .95, 1, CREAM, axis='x')
    a.box(-.95, -.22, 1.78, .95, .3, 1.95, WHITE, bevel=.015)
    a.box(-.9, -.18, 1.765, .9, .0, 1.78, E(0xFFF4D6, 2.0))
    # well and three sloped decks
    a.box(-.95, -.38, .42, .95, .3, .46, STEEL)
    goods = (MINT, RED, YELLOW, GREEN, ORANGE, WHITE, PINK, SKY)
    for k in range(9):
        a.box(-.9 + k * .2, -.32, .46, -.74 + k * .2, .05, .56, goods[k % 8], bevel=.01)
    for d, z in enumerate((.72, 1.06, 1.4)):
        a.extrude([(-.12 + d * .06, z), (.3, z + .06), (.3, z + .09), (-.12 + d * .06, z + .03)], -.95, .95, STEEL, axis='x')
        a.box(-.95, -.135 + d * .06, z, .95, -.12 + d * .06, z + .07, WHITE)
        for k in range(11):
            m = goods[(k + d * 3) % 8]
            x = -.9 + k * .165
            if (k + d) % 3 == 0:
                bottle(a, x + .06, .08 + d * .03, z + .05, m, s=.9, cap=WHITE)
            else:
                a.box(x, -.04 + d * .05, z + .045, x + .13, .2, z + .2, m, bevel=.008)


@furn(2, 1)
def freezer(a):
    a.box(-.97, -.4, 0, .97, .42, .1, GREY_D)
    a.box(-1, -.43, .1, 1, -.37, .82, WHITE)
    a.box(-1, .39, .1, 1, .45, .82, WHITE)
    a.box(-1, -.37, .1, -.94, .39, .82, WHITE)
    a.box(.94, -.37, .1, 1, .39, .82, WHITE)
    a.box(-.94, -.37, .1, .94, .39, .5, WHITE)
    a.box(-.98, -.445, .3, .98, -.43, .42, SKY)
    a.box(-.94, -.37, .82, .94, .39, .9, GREY, bevel=.01)
    # two sliding glass lids, one stepped over the other
    a.box(-.9, -.33, .9, .02, .35, .915, GLASS)
    a.box(-.06, -.33, .915, .9, .35, .93, GLASS)
    a.box(-.5, -.3, .93, -.38, -.26, .95, CHROME)
    a.box(.38, -.3, .945, .5, -.26, .965, CHROME)
    for k in range(7):
        a.box(-.86 + k * .25, -.28, .5, -.66 + k * .25, .3, .8, (RED, PINK, SKY, YELLOW, MINT, WHITE, ORANGE)[k], bevel=.01)


@furn(2, 1)
def ice_cream_freezer(a):
    a.box(-.97, -.4, 0, .97, .42, .1, GREY_D)
    a.box(-1, -.43, .1, 1, .45, .86, PINK, bevel=.03, seg=2)
    a.box(-.97, -.445, .5, .97, -.43, .7, WHITE)
    a.box(-.94, -.37, .86, .94, .39, .92, WHITE)
    # curved sneeze guard
    a.extrude([(-.4, .92), (-.38, .92), (-.38, 1.12), (-.2, 1.3), (.39, 1.3), (.39, 1.32), (-.22, 1.32), (-.4, 1.14)], -.94, .94, GLASS, axis='x')
    tubs = (0xF7E7CE, 0x7B4A2E, 0xF28AB2, 0xA8E0B0, 0xF2C94C, 0xBCA0E8, 0xFFFFFF, 0xE85D5D)
    for k in range(8):
        x = -.82 + (k % 4) * .46
        y = -.16 + (k // 4) * .3
        a.box(x - .19, y - .13, .86, x + .19, y + .13, .925, STEEL)
        a.box(x - .17, y - .11, .9, x + .17, y + .11, .94, M(tubs[k], "FOOD"), bevel=.015)
        a.ico(x + .05, y, .95, .05, M(tubs[k], "FOOD"))


@furn(1, 1)
def flower_stand(a):
    # three wooden steps
    for k in range(3):
        y0 = -.42 + k * .28
        a.box(-.45, y0, 0, .45, y0 + .3, .22 + k * .24, WOOD_L, bevel=.012)
    blooms = (RED, YELLOW, PURPLE, ORANGE, PINK, WHITE, M(0xE04F7A, "PAINT"), SKY, M(0xFFB347, "PAINT"))
    for k in range(3):
        for i in range(3):
            x = -.29 + i * .29
            y = -.27 + k * .28
            z = .22 + k * .24
            a.lathe(x, y, [(0, 0), (.075, 0), (.1, .18), (.105, .2), (.09, .2), (.09, .19), (0, .19)], (STEEL, GUN, M(0x3D6E8C, "METAL"))[(i + k) % 3], n=8, z=z)
            flowers(a, x, y, z + .15, blooms[k * 3 + i], r=.1, h=.26 + .04 * ((i + k) % 2))


@furn(2, 1)
def produce(a):
    # a wooden stand with three crates tilted toward the shopper, plus a row underneath
    legs4(a, -.92, -.3, .92, .36, 0, .7, .04, WOOD, n=4)
    a.box(-.98, -.36, .25, .98, .42, .3, WOOD)
    fruit = ((0xE5483B, 0xC93A2E), (0xF2C94C, 0xE0B030), (0xF2994A, 0xE07F2E), (0x6DBE45, 0x4E9E36), (0x8E3B6E, 0x7A2E5E), (0xE8D36A, 0xD6BF4E))
    for k in range(3):
        cx = -.65 + k * .65
        with a.at(cx, .02, .62, rx=18):
            a.box(-.3, -.36, 0, .3, .36, .03, WOOD_L)
            a.box(-.3, -.36, 0, -.27, .36, .2, WOOD_L)
            a.box(.27, -.36, 0, .3, .36, .2, WOOD_L)
            a.box(-.3, -.36, 0, .3, -.33, .2, WOOD_L)
            a.box(-.3, .33, 0, .3, .36, .2, WOOD_L)
            a.box(-.27, -.33, .03, .27, .33, .12, M(fruit[k][1], "FOOD"))
            for i in range(4):
                for j in range(4):
                    a.ico(-.2 + i * .135 + (j % 2) * .02, -.24 + j * .16, .15, .07, M(fruit[k][(i + j) % 2], "FOOD"), rz=(i * 7 + j * 13) * 9)
        with a.at(cx, -.02, .3):
            a.box(-.3, -.3, 0, .3, .3, .22, WOOD, bevel=.008)
            a.box(-.27, -.27, .18, .27, .27, .24, M(fruit[k + 3][1], "FOOD"))
            for i in range(4):
                for j in range(3):
                    a.ico(-.2 + i * .135, -.17 + j * .17, .25, .065, M(fruit[k + 3][(i + j) % 2], "FOOD"), rz=(i * 5 + j * 11) * 9)
    a.box(-.5, -.44, .68, .5, -.42, .82, M(0x2E3238, "PAINT"))
    a.box(-.4, -.445, .73, .4, -.44, .77, YELLOW)


@furn(2, 1)
def coffee_machine(a):
    a.box(-.96, -.34, 0, .96, .38, .08, WOOD_X)
    a.box(-1, -.38, .08, 1, .42, .97, WOOD_D, bevel=.015)
    for i in range(4):
        a.box(-.95 + i * .49, -.39, .16, -.52 + i * .49, -.38, .88, M(0x6B4A3A, "WOOD"))
    a.box(-1.03, -.44, .97, 1.03, .46, 1.03, WOOD_X, bevel=.012)
    # espresso machine: body, top cup rail, two group heads with portafilters, drip tray, steam wand
    a.box(-.72, -.2, 1.03, .38, .3, 1.12, GUN)
    a.box(-.75, -.05, 1.12, .41, .32, 1.52, RED, bevel=.03, seg=2)
    a.box(-.75, -.26, 1.38, .41, .0, 1.52, STEEL, bevel=.02)
    a.box(-.7, -.22, 1.52, .36, .28, 1.535, CHROME)
    for x in (-.45, .1):
        a.cyl(x, -.15, 1.3, 1.38, .07, CHROME, n=10)
        a.cyl(x, -.15, 1.25, 1.3, .06, GUN, n=10)
        a.rod((x, -.2, 1.27), (x, -.42, 1.24), .016, BLACK, n=6)
        a.lathe(x, -.16, [(0, 0), (.03, 0), (.042, .07), (.036, .07), (.03, .01), (0, .01)], WHITE, n=8, z=1.12)
    a.box(-.7, -.26, 1.105, .36, -.04, 1.125, CHROME)
    a.tube([(.3, -.1, 1.4), (.34, -.24, 1.36), (.34, -.26, 1.2)], .012, CHROME, n=6)
    for i in range(5):
        a.lathe(-.6 + i * .12, .1, [(0, 0), (.03, 0), (.042, .07), (0, .07)], WHITE, n=8, z=1.535)
    for i in range(3):
        a.cyl(-.45 + i * .15, -.266, 1.43, 1.47, .022, BLACK, n=8)
    # grinder with bean hopper, pitcher
    a.box(.55, -.12, 1.03, .85, .2, 1.36, DARK, bevel=.02)
    a.lathe(.7, .04, [(0, 0), (.08, 0), (.14, .2), (.14, .22), (0, .22)], M(0x6B4A3A, "GLASS", alpha=.75), n=10, z=1.36)
    a.rod((.7, -.12, 1.2), (.7, -.2, 1.16), .03, GUN, n=6)
    a.lathe(.72, -.28, [(0, 0), (.045, 0), (.04, .1), (.05, .12), (0, .12)], STEEL, n=8, z=1.03)


@furn(1, 1)
def pastry_case(a):
    a.box(-.42, -.36, 0, .42, .4, .08, WOOD_X)
    a.box(-.45, -.4, .08, .45, .43, .9, WOOD, bevel=.015)
    a.box(-.4, -.41, .2, .4, -.4, .78, WOOD_L)
    a.box(-.47, -.42, .9, .47, .45, .94, WOOD_X, bevel=.01)
    # curved glass front
    a.extrude([(-.4, .94), (-.38, .94), (-.38, 1.2), (-.22, 1.42), (.42, 1.42), (.42, 1.44), (-.23, 1.44), (-.4, 1.21)], -.44, .44, GLASS, axis='x')
    a.box(-.45, -.4, .94, -.43, .43, 1.44, GLASS)
    a.box(.43, -.4, .94, .45, .43, 1.44, GLASS)
    a.box(-.43, .41, .94, .43, .43, 1.44, STEEL)
    a.box(-.42, -.3, 1.17, .42, .4, 1.185, GLASS)
    crust = M(0xD9A45B, "FOOD")
    for z in (.94, 1.185):
        a.box(-.38, -.3, z, -.02, .3, z + .015, STEEL)
        a.box(.02, -.3, z, .38, .3, z + .015, STEEL)
    for i in range(3):
        for j in range(3):
            # croissants below, cupcakes above
            a.ico(-.3 + i * .1, -.2 + j * .2, .985, .05, crust, scale=(1.5, .8, .7), rz=30)
            a.cyl(.1 + i * .1, -.2 + j * .2, .955, .995, .028, M(0x8B5A3C, "FOOD"), n=6, r2=.036)
            a.ico(.1 + i * .1, -.2 + j * .2, 1.01, .036, (PINK, WHITE, M(0x7B4A2E, "FOOD"))[(i + j) % 3])
    for i in range(2):
        a.cyl(-.2 + i * .4, 0, 1.2, 1.26, .13, (M(0xF7E7CE, "FOOD"), M(0x7B4A2E, "FOOD"))[i], n=12)
        a.ico(-.2 + i * .4, 0, 1.27, .03, RED)


@furn(2, 1)
def bookshelf(a):
    a.box(-1, .32, 0, 1, .4, 2.0, WOOD_X)
    for x in (-1, -.02, .96):
        a.box(x, -.3, 0, x + .04 if x != -.02 else .02, .4, 2.0, WOOD_D, bevel=.008)
    a.box(-1.03, -.33, 2.0, 1.03, .43, 2.06, WOOD_X, bevel=.012)
    a.box(-1, -.3, 0, 1, .4, .1, WOOD_X)
    spines = (0xB03A2E, 0x2F5D8C, 0x3E7C4F, 0xE0B030, 0x6B3F7A, 0xD9D2C0, 0x1F2A38, 0xC96F2E, 0x7A2E3E, 0x4E8C8C)
    seed = 7
    for row, z in enumerate((.1, .48, .86, 1.24, 1.62)):
        a.box(-.96, -.3, z + .33, .96, .32, z + .37, WOOD_D)
        for side in (-.94, .04):
            x = side
            while x < side + .86:
                seed = (seed * 1103515245 + 12345) & 0x7fffffff
                w = .035 + (seed % 5) * .009
                h = .22 + ((seed >> 4) % 6) * .018
                if (seed >> 9) % 11 == 0 and x < side + .6:
                    # a few books lying flat
                    for k in range(3):
                        a.box(x, -.2, z + k * .04, x + .2, .1, z + k * .04 + .035, M(spines[(seed + k) % 10], "PAPER"))
                    x += .22
                    continue
                a.box(x, -.22 + ((seed >> 7) % 3) * .012, z, x + w, .12, z + h, M(spines[(seed >> 2) % 10], "PAPER"))
                x += w + .004


@furn(1, 1)
def jewelry_case(a):
    a.box(-.4, -.4, 0, .4, .4, .08, BLACK)
    a.box(-.43, -.43, .08, .43, .43, .92, WOOD_X, bevel=.015)
    a.box(-.36, -.44, .2, .36, -.43, .8, M(0x4E342E, "WOOD"))
    a.box(-.44, -.44, .92, .44, .44, .95, M(0xD4AF37, "METAL"), bevel=.006)
    # velvet bed, glass box with brass corner posts
    a.box(-.4, -.4, .95, .4, .4, .99, F(0x5B1F3A))
    for x in (-.41, .41):
        for y in (-.41, .41):
            a.box(x - .012, y - .012, .95, x + .012, y + .012, 1.33, M(0xD4AF37, "METAL"))
    a.box(-.42, -.42, .99, .42, .42, 1.32, GLASS)
    for k in range(4):
        with a.at(0, 0, 0, rz=k * 90):
            a.box(-.43, -.43, 1.32, .43, -.4, 1.34, M(0xD4AF37, "METAL"))
    gold = M(0xE8C04A, "METAL")
    gems = (M(0x7FDBFF, "GLASS", alpha=.9), M(0xE85D5D, "GLASS", alpha=.9), M(0x6FCF97, "GLASS", alpha=.9))
    for i in range(3):
        for j in range(2):
            x = -.24 + i * .24
            y = -.2 + j * .22
            a.box(x - .05, y - .05, .99, x + .05, y + .05, 1.03, F(0x2A1020), bevel=.008)
            a.torus(x, y, 1.065, .028, .007, (gold, CHROME)[(i + j) % 2], n=10, k=5, axis='y')
            a.ico(x, y, 1.105, .016, gems[(i + j) % 3])
    # a necklace bust at the back
    a.lathe(0, .22, [(0, 0), (.07, 0), (.05, .04), (.03, .1), (.06, .16), (.035, .24), (0, .25)], F(0x2A1020), n=10, z=.99)
    a.torus(0, .2, 1.17, .05, .006, gold, n=12, k=4, axis='y')


@furn(2, 1)
def clothing_rack(a):
    for x in (-.9, .9):
        a.rod((x, -.3, .03), (x, .3, .03), .025, CHROME, n=8)
        a.cyl(x, 0, .03, 1.6, .022, CHROME, n=8)
        for y in (-.3, .3):
            a.cyl(x, y, 0, .05, .035, RUBBER, n=8)
    a.rod((-.94, 0, 1.6), (.94, 0, 1.6), .02, CHROME, n=8)
    cols = (0xEB5757, 0x2F80ED, 0xF2C94C, 0x27AE60, 0xE9EDF2, 0x2E3238, 0xBB6BD9, 0xF2994A, 0x56CCF2, 0xF28AB2, 0x8B5A3C)
    for i in range(11):
        x = -.78 + i * .156
        long = i % 4 == 1
        m = F(cols[i])
        a.torus(x, 0, 1.63, .028, .005, CHROME, n=8, k=4, axis='x')
        # shoulders slope down from the hook, body hangs below; sleeves just proud of it
        a.extrude([(-.02, 1.57), (.02, 1.57), (.24, 1.46), (.24, .62 if long else .86), (-.24, .62 if long else .86), (-.24, 1.46)],
                  x - .022, x + .022, m, axis='x')
        if not long:
            a.extrude([(.24, 1.46), (.3, 1.2), (.22, 1.18)], x - .026, x + .026, m, axis='x')
            a.extrude([(-.24, 1.46), (-.22, 1.18), (-.3, 1.2)], x - .026, x + .026, m, axis='x')


@furn(2, 2)
def round_rack(a):
    a.cyl(0, 0, 0, .04, .4, CHROME, n=16)
    a.cyl(0, 0, .04, 1.55, .028, CHROME, n=8)
    a.torus(0, 0, 1.5, .62, .018, CHROME, n=20, k=5)
    for k in range(4):
        ang = math.radians(k * 90 + 45)
        a.rod((0, 0, 1.5), (.62 * math.cos(ang), .62 * math.sin(ang), 1.5), .012, CHROME, n=5)
    a.sphere(0, 0, 1.58, .05, CHROME, seg=8, rings=5)
    cols = (0xEB5757, 0x2F80ED, 0xF2C94C, 0x27AE60, 0xE9EDF2, 0x2E3238, 0xBB6BD9, 0xF2994A, 0x56CCF2, 0xF28AB2)
    n = 20
    for i in range(n):
        with a.at(0, 0, 0, rz=i * 360 / n):
            m = F(cols[i % 10])
            h = .7 if i % 5 == 2 else .9
            a.extrude([(.6, 1.47), (.64, 1.47), (.84, 1.38), (.84, h), (.4, h), (.4, 1.38)], -.02, .02, m, axis='y')


@furn(2, 1)
def shoe_rack(a):
    a.box(-1, .3, 0, 1, .4, 1.7, WHITE)
    a.box(-1, -.1, 0, -.96, .4, 1.7, WHITE)
    a.box(.96, -.1, 0, 1, .4, 1.7, WHITE)
    cols = (0xEB5757, 0x2E3238, 0xE9EDF2, 0x2F80ED, 0xF2C94C, 0x8B5A3C, 0x27AE60, 0xF28AB2)
    for r, z in enumerate((.25, .65, 1.05, 1.42)):
        a.extrude([(-.25, z - .08), (.3, z + .05), (.3, z + .08), (-.25, z - .05)], -.96, .96, WOOD_L, axis='x')
        a.box(-.96, -.26, z - .08, .96, -.24, z - .02, WOOD)
        for i in range(5):
            x = -.78 + i * .39
            m = F(cols[(i + r * 3) % 8])
            with a.at(x, 0, z + .012, rx=13.3):
                # a pair of sneakers in profile: sole, toe box, ankle
                for dy in (-.07, .07):
                    with a.at(0, dy, 0, rz=-90 if (i + r) % 2 else 90):
                        a.box(-.05, -.15, .0, .05, .15, .025, WHITE)
                        a.box(-.048, -.15, .025, .048, .15, .075, m, bevel=.012)
                        a.box(-.046, .02, .075, .046, .15, .14, m, bevel=.012)


@furn(1, 1)
def magazine_rack(a):
    a.cyl(0, 0, 0, .04, .32, GUN, n=12)
    a.cyl(0, 0, .04, 1.5, .03, GUN, n=8)
    covers = (0xEB5757, 0x2F80ED, 0xF2C94C, 0x27AE60, 0xBB6BD9, 0xF2994A, 0x56CCF2, 0xF28AB2)
    for tier in range(4):
        z = .3 + tier * .3
        for k in range(4):
            with a.at(0, 0, z, rz=k * 90 + tier * 45):
                a.box(-.13, -.26, 0, .13, -.24, .08, GUN)
                a.box(-.13, -.26, 0, .13, -.14, .012, GUN)
                with a.at(0, -.2, .012, rx=-14):
                    a.box(-.11, -.03, 0, .11, .0, .28, M(covers[(tier * 3 + k) % 8], "PAPER"))
                    a.box(-.09, -.034, .2, .09, -.03, .25, PAPER)


@furn(2, 1)
def electronics_table(a):
    legs4(a, -.9, -.35, .9, .35, 0, .86, .035, WHITE, n=4)
    a.box(-1, -.45, .86, 1, .45, .92, M(0xF5F5F5, "PAINT"), bevel=.012)
    a.box(-1, -.45, .8, 1, .45, .86, WOOD_L)
    # laptops, tablets on stands, phones on pucks, headphones
    for x in (-.7, -.2):
        with a.at(x, .05, .92):
            a.box(-.17, -.12, 0, .17, .12, .015, STEEL, bevel=.004)
            a.box(-.14, -.09, .015, .14, .05, .018, DARK)
            with a.at(0, .12, .015, rx=-18):
                a.box(-.17, -.008, 0, .17, .004, .22, STEEL)
                a.box(-.155, -.012, .012, .155, -.008, .21, SCREEN)
    for x in (.25, .55):
        with a.at(x, .12, .92, rx=-20):
            a.box(-.1, 0, 0, .1, .012, .27, DARK, bevel=.004)
            a.box(-.09, -.004, .01, .09, 0, .26, (SCREEN, M(0xF2994A, "SCREEN"))[x > .4])
        a.box(x - .05, .15, .92, x + .05, .25, 1.0, WHITE)
    for i in range(5):
        x = -.8 + i * .18
        a.cyl(x, -.28, .92, .95, .045, WHITE, n=10)
        with a.at(x, -.26, .95, rx=-25):
            a.box(-.035, 0, 0, .035, .008, .14, (DARK, WHITE, M(0xD4AF37, "METAL"), BLUE, RED)[i], bevel=.003)
            a.box(-.03, -.003, .008, .03, 0, .132, SCREEN)
    a.cyl(.82, -.05, .92, 1.14, .012, WHITE, n=6)
    a.torus(.82, -.05, 1.2, .07, .012, DARK, n=12, k=5, axis='y')
    a.cyl(.74, -.05, 1.13, 1.2, .035, DARK, n=8)
    a.cyl(.9, -.05, 1.13, 1.2, .035, DARK, n=8)


# ---------------------------------------------------------------------------------------------- seating

@furn(2, 2)
def table_set(a):
    a.cyl(0, 0, 0, .03, .28, IRON, n=12)
    a.cyl(0, 0, .03, .72, .04, IRON, n=8)
    a.cyl(0, 0, .72, .76, .46, WOOD_L, n=20)
    a.cyl(0, 0, .7, .72, .44, WOOD, n=20)
    for k in (1, 3):
        th = math.radians(k * 90 + 180)
        with a.at(-.68 * math.sin(th), .68 * math.cos(th), 0, rz=k * 90 + 180):
            chair(a, RED)
    # set for two: cups and a little vase
    for sx in (-1, 1):
        a.cyl(sx * .2, -.05, .76, .765, .07, WHITE, n=10)
        a.lathe(sx * .2, -.05, [(0, 0), (.03, 0), (.042, .06), (.036, .06), (.03, .01), (0, .01)], WHITE, n=8, z=.765)
    a.lathe(0, .15, [(0, 0), (.03, 0), (.04, .06), (.02, .1), (.025, .12), (0, .12)], SKY, n=8, z=.76)
    a.ico(0, .15, .93, .035, YELLOW)
    a.cyl(0, .15, .86, .92, .006, LEAF_D, n=4)


@furn(1, 1)
def barstool(a):
    a.cyl(0, 0, 0, .025, .22, CHROME, n=14)
    a.cyl(0, 0, .025, .72, .028, CHROME, n=8)
    a.torus(0, 0, .3, .15, .012, CHROME, n=14, k=5)
    for k in range(3):
        ang = math.radians(k * 120)
        a.rod((0, 0, .3), (.15 * math.cos(ang), .15 * math.sin(ang), .3), .008, CHROME, n=4)
    a.lathe(0, 0, [(0, 0), (.14, 0), (.19, .03), (.19, .08), (.15, .11), (0, .11)], F(0xEB5757), n=14, z=.72)


@furn(2, 1)
def bench(a):
    for x in (-.8, .8):
        a.extrude([(-.22, 0), (-.16, 0), (-.14, .4), (.14, .4), (.16, 0), (.22, 0), (.2, .44), (-.2, .44)], x - .025, x + .025, IRON, axis='x')
        a.rod((x, .16, .44), (x, .24, .86), .02, IRON, n=6)
    for k in range(4):
        y = -.2 + k * .115
        a.box(-.95, y, .44, .95, y + .095, .475, WOOD_L, bevel=.008)
    for k in range(3):
        with a.at(0, .2 + k * .025, .56 + k * .115, rx=-10):
            a.box(-.95, -.015, 0, .95, .015, .095, WOOD_L, bevel=.008)


@furn(2, 1)
def sofa(a):
    col = F(0x4F7FA6)
    dark = F(0x3E6E8E)
    legs4(a, -.9, -.3, .9, .32, 0, .1, .025, WOOD_X, n=6)
    a.box(-1, -.38, .1, 1, .42, .32, dark, bevel=.03, seg=2)
    a.box(-.8, -.4, .3, -.01, .2, .48, col, bevel=.05, seg=2)
    a.box(.01, -.4, .3, .8, .2, .48, col, bevel=.05, seg=2)
    with a.at(0, .3, .3, rx=-8):
        a.box(-.8, -.12, 0, -.01, .1, .55, col, bevel=.05, seg=2)
        a.box(.01, -.12, 0, .8, .1, .55, col, bevel=.05, seg=2)
    a.box(-1, -.4, .1, -.8, .42, .62, dark, bevel=.06, seg=2)
    a.box(.8, -.4, .1, 1, .42, .62, dark, bevel=.06, seg=2)
    with a.at(-.55, .05, .5, rx=-25, rz=12):
        a.box(-.17, -.05, 0, .17, .05, .3, F(0xF2C94C), bevel=.045, seg=2)
    with a.at(.6, .06, .5, rx=-25, rz=-8):
        a.box(-.17, -.05, 0, .17, .05, .3, F(0xEB5757), bevel=.045, seg=2)


@furn(2, 1)
def lounge_sofa(a):
    col = F(0x5B1F3A)
    a.box(-1, -.4, 0, 1, .42, .12, IRON)
    a.box(-1, -.4, .12, 1, .42, .4, col, bevel=.04, seg=2)
    with a.at(0, .32, .36, rx=-6):
        a.box(-1, -.1, 0, 1, .1, .5, col, bevel=.04, seg=2)
    # button tufting on the back, gold trim on the plinth
    for i in range(7):
        for j in range(2):
            a.ico(-.84 + i * .28, .2 - j * .012, .55 + j * .18, .022, M(0xD4AF37, "METAL"))
    a.box(-1.005, -.405, .1, 1.005, .425, .125, M(0xD4AF37, "METAL"))
    a.box(-1, -.4, .12, -.84, .42, .58, col, bevel=.05, seg=2)
    a.box(.84, -.4, .12, 1, .42, .58, col, bevel=.05, seg=2)


@furn(3, 1)
def cinema_seats(a):
    red = F(0xA8323A)
    for i in range(3):
        x = -1 + i
        a.box(x - .06, -.05, 0, x + .06, .25, .3, IRON)
        with a.at(x, .0, .33, rx=-5):
            a.box(-.36, -.34, 0, .36, .22, .13, red, bevel=.04, seg=2)
        with a.at(x, .3, .36, rx=-10):
            a.box(-.36, -.07, 0, .36, .07, .72, red, bevel=.045, seg=2)
            a.box(-.34, .07, .05, .34, .09, .68, IRON)
    for i in range(4):
        x = -1.46 + i * .973
        a.box(x - .04, -.3, 0, x + .04, .3, .56, IRON, bevel=.012)
        a.box(x - .055, -.34, .56, x + .055, .2, .62, DARK, bevel=.015)
        a.cyl(x, -.27, .62, .64, .045, IRON, n=10)


@furn(3, 2)
def meeting_table(a):
    for x in (-.9, .9):
        a.box(x - .05, -.3, 0, x + .05, .3, .04, IRON)
        a.box(x - .03, -.04, .04, x + .03, .04, .71, IRON)
    a.box(-1.3, -.5, .71, 1.3, .5, .76, WOOD_L, bevel=.02, seg=2)
    for i in range(3):
        with a.at(-1 + i, -.8, 0, rz=180):
            chair(a, GREY_D)
        with a.at(-1 + i, .8, 0):
            chair(a, GREY_D)
    # conference phone, papers, mugs
    a.lathe(0, 0, [(0, 0), (.12, 0), (.1, .03), (.04, .05), (0, .05)], DARK, n=3, z=.76, smooth=False)
    for k, (x, y, rz) in enumerate(((-.8, -.25, 8), (0, -.3, -5), (.8, -.25, 3), (-.8, .25, 184), (.8, .28, 176))):
        with a.at(x, y, .76, rz=rz):
            a.box(-.11, -.15, 0, .11, .15, .006, PAPER)
        a.lathe(x + .22, y, [(0, 0), (.035, 0), (.035, .09), (.028, .09), (.028, .01), (0, .01)], (WHITE, RED, BLUE, WHITE, YELLOW)[k], n=8, z=.76)


# ---------------------------------------------------------------------------------------------- office and home

@furn(2, 1)
def office_desk(a):
    a.box(-1, -.42, .72, 1, .42, .76, WOOD_L, bevel=.012)
    a.box(-.98, -.36, 0, -.94, .4, .72, WHITE)
    a.box(-.94, -.36, .25, .42, -.32, .7, WHITE)
    a.box(.42, -.38, 0, .96, .4, .72, WHITE, bevel=.01)
    for k in range(3):
        a.box(.44, .4, .04 + k * .23, .94, .41, .24 + k * .23, M(0xF7FAFC, "PAINT"))
        a.box(.62, .41, .13 + k * .23, .76, .42, .15 + k * .23, CHROME)
    with a.at(-.25, -.16, .76, rz=180):
        monitor(a, .58, .34)
    a.box(-.48, .1, .76, -.02, .24, .775, DARK, bevel=.004)
    a.box(.08, .1, .76, .14, .2, .785, DARK, bevel=.01)
    a.lathe(.3, -.1, [(0, 0), (.04, 0), (.04, .1), (.032, .1), (.032, .01), (0, .01)], BLUE, n=8, z=.76)
    for k in range(3):
        a.box(-.9, -.1 + k * .01, .76 + k * .012, -.66, .22 + k * .01, .772 + k * .012, (PAPER, M(0xF2C94C, "PAPER"), PAPER)[k])
    pot_plant(a, .78, -.2, .76, .045, .2, pot=WHITE)


@furn(2, 1)
def desk(a):
    legs4(a, -.92, -.36, .92, .36, 0, .72, .03, WOOD_D, n=4)
    a.box(-1, -.44, .72, 1, .44, .77, WOOD, bevel=.012)
    a.box(-.9, -.4, .58, .9, -.38, .72, WOOD_D)
    with a.at(.1, .12, .77, rz=180):
        a.box(-.2, -.13, 0, .2, .13, .016, STEEL, bevel=.004)
        with a.at(0, .13, .016, rx=-15):
            a.box(-.2, -.006, 0, .2, .006, .25, STEEL)
            a.box(-.185, -.01, .012, .185, -.006, .24, SCREEN)
    # banker's lamp and a mug
    a.cyl(-.7, .2, .77, .79, .09, M(0xD4AF37, "METAL"), n=10)
    a.tube([(-.7, .2, .79), (-.7, .2, 1.05), (-.7, .1, 1.1)], .012, M(0xD4AF37, "METAL"), n=5)
    a.rod((-.86, .08, 1.1), (-.54, .08, 1.1), .05, M(0x2C6E3F, "GLASS", alpha=.9), n=8)
    a.lathe(.7, -.1, [(0, 0), (.04, 0), (.04, .1), (.032, .1), (.032, .01), (0, .01)], RED, n=8, z=.77)


@furn(1, 1)
def office_chair(a):
    for k in range(5):
        ang = math.radians(k * 72 + 18)
        x, y = .28 * math.cos(ang), .28 * math.sin(ang)
        a.rod((0, 0, .1), (x, y, .07), .022, DARK, n=5)
        a.sphere(x, y, .035, .035, BLACK, seg=6, rings=4)
    a.cyl(0, 0, .08, .42, .028, CHROME, n=8)
    a.box(-.24, -.24, .42, .24, .24, .52, F(0x2F80ED), bevel=.04, seg=2)
    a.rod((0, .2, .44), (0, .28, .62), .025, DARK, n=6)
    with a.at(0, .27, .56, rx=-6):
        a.box(-.22, -.04, 0, .22, .04, .52, F(0x2F80ED), bevel=.035, seg=2)
    for sx in (-1, 1):
        a.tube([(sx * .24, .1, .46), (sx * .29, .1, .66), (sx * .29, -.12, .66)], .016, DARK, n=5)
        a.box(sx * .29 - .03, -.16, .66, sx * .29 + .03, .12, .69, DARK, bevel=.01)


@furn(1, 1)
def filing_cabinet(a):
    a.box(-.28, -.36, 0, .28, .4, 1.36, STEEL, bevel=.012)
    for k in range(4):
        z = .05 + k * .325
        a.box(-.25, -.375, z, .25, -.36, z + .29, M(0xC9D1DA, "METAL"), bevel=.006)
        a.box(-.09, -.395, z + .2, .09, -.375, z + .225, CHROME)
        a.box(-.07, -.38, z + .09, .07, -.375, z + .15, PAPER)
    a.box(.19, -.38, 1.28, .22, -.375, 1.31, IRON)
    pot_plant(a, 0, .05, 1.36, .07, .26, pot=WHITE)


@furn(1, 1)
def water_cooler(a):
    a.box(-.19, -.19, 0, .19, .19, 1.0, WHITE, bevel=.025, seg=2)
    a.box(-.15, -.2, .58, .15, -.19, .86, M(0xD5DBE2, "PAINT"))
    a.box(-.15, -.26, .52, .15, -.19, .56, GREY, bevel=.008)
    for sx, m in ((-1, BLUE), (1, RED)):
        a.rod((sx * .07, -.19, .78), (sx * .07, -.24, .78), .018, WHITE, n=6)
        a.box(sx * .07 - .02, -.26, .74, sx * .07 + .02, -.23, .8, m)
    a.cyl(0, 0, 1.0, 1.04, .13, WHITE, n=12)
    water = M(0x7FC4EE, "GLASS", alpha=.6)
    a.lathe(0, 0, [(0, 0), (.05, 0), (.14, .08), (.14, .16), (.135, .18), (.14, .2), (.14, .3), (.135, .32), (.14, .34), (.14, .4),
                   (.1, .46), (0, .46)], water, n=12, z=1.04)
    a.box(.2, -.1, .5, .26, .1, .78, WHITE)
    a.cyl(.23, 0, .78, .84, .04, PAPER, n=8, r2=.03)


@furn(1, 1)
def copier(a):
    a.box(-.4, -.36, 0, .4, .4, .7, M(0xE9EDF2, "PLASTIC"), bevel=.015)
    for k in range(2):
        a.box(-.36, -.375, .08 + k * .28, .36, -.36, .32 + k * .28, M(0xD5DBE2, "PLASTIC"))
        a.box(-.1, -.385, .24 + k * .28, .1, -.375, .27 + k * .28, GREY_D)
    a.box(-.42, -.38, .7, .42, .42, .9, M(0xD5DBE2, "PLASTIC"), bevel=.02)
    a.box(-.4, -.36, .9, .28, .4, .98, WHITE, bevel=.02)
    a.box(-.36, -.2, .98, .24, .3, 1.02, GREY, bevel=.015)
    with a.at(.34, -.3, .9, rx=30):
        a.box(-.07, -.06, 0, .07, .06, .02, DARK)
        a.box(-.06, -.05, .02, .06, .05, .024, SCREEN)
    a.box(-.42, -.2, .74, -.62, .2, .76, GREY)
    a.box(-.58, -.15, .76, -.42, .15, .775, PAPER)


@furn(1, 1)
def whiteboard(a):
    for sx in (-1, 1):
        a.rod((sx * .42, -.25, .03), (sx * .42, .25, .03), .02, GUN, n=6)
        a.cyl(sx * .42, 0, .03, 1.85, .018, GUN, n=6)
        for y in (-.25, .25):
            a.sphere(sx * .42, y, .03, .035, BLACK, seg=6, rings=4)
    a.box(-.46, -.02, .8, .46, .02, 1.8, STEEL, bevel=.008)
    a.box(-.43, -.026, .83, .43, -.02, 1.77, M(0xFAFCFD, "PAINT"))
    a.box(-.4, -.06, .8, .4, -.02, .82, STEEL)
    for k, m in enumerate((RED, BLUE, GREEN, DARK)):
        a.rod((-.3 + k * .09, -.045, .83), (-.23 + k * .09, -.045, .83), .01, m, n=5)
    # a rising chart and a few notes
    pts = [(-.35, 1.0), (-.2, 1.12), (-.05, 1.08), (.1, 1.3), (.3, 1.5)]
    for (x0, z0), (x1, z1) in zip(pts, pts[1:]):
        a.rod((x0, -.028, z0), (x1, -.028, z1), .008, RED, n=4)
    a.box(-.38, -.028, .92, -.37, -.026, 1.6, DARK)
    a.box(-.38, -.028, .92, .38, -.026, .93, DARK)
    for k in range(3):
        a.box(.15 + k * .09, -.03, 1.62, .22 + k * .09, -.026, 1.69, (YELLOW, PINK, MINT)[k])


@furn(2, 2)
def bed(a):
    a.box(-.98, -.98, .08, .98, 1.0, .3, WOOD, bevel=.015)
    legs4(a, -.9, -.9, .9, .9, 0, .1, .04, WOOD_D, n=6)
    a.box(-1, .92, 0, 1, 1.0, 1.0, WOOD, bevel=.02)
    a.box(-.9, .9, .4, .9, .92, .92, WOOD_L)
    a.box(-.94, -.96, .3, .94, .92, .5, WHITE, bevel=.05, seg=2)
    a.box(-.96, -.97, .32, .96, .3, .54, F(0x7B6FE6), bevel=.05, seg=2)
    a.box(-.96, .2, .46, .96, .36, .55, F(0xE9EDF2), bevel=.03, seg=2)
    for sx in (-1, 1):
        with a.at(sx * .46, .66, .5, rx=-8):
            a.box(-.36, -.2, 0, .36, .2, .13, F(0xF7FAFC), bevel=.06, seg=2)


@furn(1, 1)
def home_fridge(a):
    a.box(-.4, -.34, 0, .4, .42, .06, BLACK)
    a.box(-.42, -.36, .06, .42, .44, 1.8, M(0xC9CED6, "METAL"), bevel=.025, seg=2)
    a.box(-.41, -.385, .1, .41, -.36, .66, STEEL, bevel=.012)
    a.box(-.41, -.385, .7, .41, -.36, 1.77, STEEL, bevel=.012)
    a.box(-.36, -.43, .5, -.33, -.385, .64, CHROME)
    a.box(-.36, -.43, .74, -.33, -.385, 1.3, CHROME)
    a.box(.05, -.39, 1.2, .3, -.385, 1.45, DARK)
    for k, m in enumerate((YELLOW, PINK, MINT)):
        a.box(.0 + k * .12, -.39, .9 + (k % 2) * .08, .09 + k * .12, -.385, 1.0 + (k % 2) * .08, m)


@furn(2, 1)
def tv(a):
    a.box(-.9, -.2, 0, .9, .25, .45, WOOD_D, bevel=.012)
    a.box(-.86, -.21, .06, -.02, -.2, .4, WOOD)
    a.box(.02, -.21, .06, .86, -.2, .4, WOOD)
    a.box(-.5, -.1, .45, .5, .15, .47, DARK)
    a.box(-.04, .0, .47, .04, .06, .6, DARK)
    a.box(-.85, -.0, .56, .85, .05, 1.54, BLACK, bevel=.012)
    a.box(-.82, -.006, .59, .82, 0, 1.51, M(0x2F80ED, "SCREEN"))
    a.box(-.82, -.008, .59, .82, -.006, .85, M(0x27AE60, "SCREEN"))
    a.ico(.45, -.008, 1.25, .12, E(0xFFE680, 2.0), scale=(1, .05, 1), sub=2)
    a.box(.55, -.15, .47, .8, .1, .53, DARK, bevel=.01)
    a.box(-.8, -.12, .47, -.62, .0, .49, GREY_D)


@furn(1, 1)
def plant_large(a):
    pot_plant(a, 0, 0, 0, .2, 1.25)


@furn(1, 1)
def plant_small(a):
    a.lathe(0, 0, [(0, 0), (.1, 0), (.14, .2), (.15, .2), (.15, .24), (.12, .24), (.12, .21), (0, .21)], WHITE, n=10)
    for k in range(7):
        ang = math.radians(k * 51)
        lean = .12 + .05 * (k % 3)
        h = .32 + .08 * (k % 2)
        with a.at(0, 0, .2, rz=k * 51):
            a.extrude([(0, 0), (.035, 0), (lean + .03, h * .6), (lean + .1, h), (lean, h * .65)], -.012, .012, (LEAF, LEAF_L, LEAF_D)[k % 3], axis='y')


@furn(1, 1)
def floor_lamp(a):
    a.cyl(0, 0, 0, .03, .18, IRON, n=14)
    a.cyl(0, 0, .03, 1.45, .015, IRON, n=6)
    a.lathe(0, 0, [(.2, 0), (.13, .28), (.12, .28), (.19, 0)], F(0xF5E6C8), n=14, z=1.4, loop=True)
    a.sphere(0, 0, 1.5, .06, E(0xFFE6A8, 4.0), seg=8, rings=5)


@furn(1, 1)
def trash_can(a):
    a.lathe(0, 0, [(0, 0), (.17, 0), (.2, .62), (.21, .62), (.21, .66), (0, .66)], STEEL, n=14)
    a.lathe(0, 0, [(.21, .66), (.19, .76), (.1, .82), (0, .83)], GUN, n=14)
    a.box(-.1, -.2, .68, .1, -.14, .78, BLACK)
    a.torus(0, 0, .05, .185, .012, GUN, n=14, k=4)


@furn(2, 2)
def rug(a):
    a.box(-.95, -.95, 0, .95, .95, .02, F(0xB03A2E))
    a.box(-.85, -.85, .02, .85, .85, .024, F(0xE8D9B5))
    a.box(-.75, -.75, .024, .75, .75, .028, F(0x2F5D8C))
    for k in range(4):
        with a.at(0, 0, .028, rz=k * 90):
            a.extrude([(0, .15), (.3, .45), (0, .7), (-.3, .45)], 0, .004, F(0xE8D9B5), axis='z')
    a.extrude([(0, -.2), (.2, 0), (0, .2), (-.2, 0)], .028, .034, F(0xB03A2E), axis='z')


# ---------------------------------------------------------------------------------------------- gym and salon

@furn(1, 2)
def treadmill(a):
    a.box(-.36, -.75, .06, .36, .95, .2, DARK, bevel=.03)
    a.box(-.28, -.6, .2, .28, .92, .215, RUBBER)
    for k in range(6):
        a.box(-.28, -.5 + k * .26, .215, .28, -.49 + k * .26, .217, GREY_D)
    a.box(-.38, -.95, .04, .38, -.6, .3, GREY_D, bevel=.04, seg=2)
    for sx in (-1, 1):
        a.rod((sx * .34, -.8, .25), (sx * .34, -.62, 1.25), .028, STEEL, n=8)
        a.tube([(sx * .34, -.62, 1.25), (sx * .34, -.3, 1.12), (sx * .34, -.1, 1.1)], .022, STEEL, n=6)
        a.rod((sx * .34, -.3, 1.12), (sx * .34, -.1, 1.1), .03, RUBBER, n=6)
        a.cyl(sx * .3, .9, 0, .08, .04, RUBBER, n=8)
    with a.at(0, -.62, 1.22, rx=38):
        a.box(-.36, -.04, -.1, .36, .04, .2, DARK, bevel=.03)
        a.box(-.22, .04, -.02, .22, .046, .16, SCREEN)
        a.box(-.3, .04, -.07, -.24, .046, -.03, GREEN)
        a.box(.24, .04, -.07, .3, .046, -.03, RED)


@furn(1, 2)
def exercise_bike(a):
    a.box(-.25, -.6, 0, .25, -.52, .06, DARK)
    a.box(-.25, .5, 0, .25, .58, .06, DARK)
    a.rod((0, -.56, .04), (0, .54, .04), .035, GREY_D, n=6)
    a.rod((-.07, -.35, .32), (.07, -.35, .32), .26, DARK, n=16)
    a.rod((-.08, -.35, .32), (.08, -.35, .32), .08, RED, n=10)
    a.rod((0, -.35, .1), (0, -.5, 1.05), .035, GREY_D, n=6)
    a.rod((0, .1, .05), (0, .3, .72), .035, GREY_D, n=6)
    a.rod((0, -.3, .35), (0, .15, .3), .04, GREY_D, n=6)
    a.box(-.12, .16, .72, .12, .46, .79, BLACK, bevel=.03, seg=2)
    a.rod((-.25, -.5, 1.05), (.25, -.5, 1.05), .02, RUBBER, n=6)
    for sx in (-1, 1):
        a.rod((sx * .25, -.5, 1.05), (sx * .25, -.36, 1.1), .02, RUBBER, n=6)
        a.rod((sx * .1, .05, .32 + sx * .12), (sx * .2, .05, .32 + sx * .12), .012, STEEL, n=5)
        a.box(sx * .2 - .05, .0, .3 + sx * .12, sx * .2 + .05, .1, .33 + sx * .12, BLACK)
    a.rod((-.1, .05, .2), (.1, .05, .44), .014, STEEL, n=5)
    with a.at(0, -.52, 1.1, rx=30):
        a.box(-.09, -.02, 0, .09, .02, .14, DARK, bevel=.01)
        a.box(-.07, .02, .02, .07, .025, .12, SCREEN)


@furn(2, 1)
def weight_rack(a):
    for sx in (-1, 1):
        a.extrude([(-.35, 0), (-.27, 0), (.0, .95), (.3, 0), (.38, 0), (.05, 1.05), (-.05, 1.05)], sx * .92 - .03, sx * .92 + .03, DARK, axis='x')
    weights = (.05, .058, .066, .074, .082, .09)
    for t, (y, z) in enumerate(((-.22, .32), (.0, .62), (.2, .92))):
        a.extrude([(y - .13, z - .02), (y + .13, z + .03), (y + .13, z + .06), (y - .13, z + .01)], -.92, .92, GUN, axis='x')
        a.box(-.92, y - .14, z - .02, .92, y - .13, z + .05, GUN)
        for i in range(6):
            x = -.75 + i * .3
            r = weights[(i + t * 2) % 6]
            zz = z + .02 + r
            a.rod((x - .1, y, zz), (x + .1, y, zz), .014, CHROME, n=6)
            for sx in (-1, 1):
                a.rod((x + sx * .1, y, zz), (x + sx * .04, y, zz), r, (BLACK, BLACK, M(0x2F80ED, "RUBBER"))[t], n=6, smooth=False)


@furn(1, 2)
def workout_bench(a):
    for sx in (-1, 1):
        a.box(sx * .42 - .03, .5, 0, sx * .42 + .03, .56, 1.25, DARK)
        a.box(sx * .42 - .05, .38, 0, sx * .42 + .05, .68, .04, DARK)
        a.box(sx * .42 - .03, .44, 1.02, sx * .42 + .03, .5, 1.1, GUN)
        for k in range(2):
            a.rod((sx * (.5 + k * .05), .47, 1.14), (sx * (.55 + k * .05), .47, 1.14), .2 - k * .04, BLACK, n=14)
        a.rod((sx * .6, .47, 1.14), (sx * .68, .47, 1.14), .03, CHROME, n=8)
    a.rod((-.72, .47, 1.14), (.72, .47, 1.14), .016, CHROME, n=8)
    a.box(-.04, -.8, 0, .04, .5, .06, DARK)
    a.box(-.25, -.84, 0, .25, -.76, .05, DARK)
    for y in (-.6, .2):
        a.box(-.03, y, .06, .03, y + .06, .4, DARK)
    a.box(-.15, -.85, .4, .15, .42, .48, F(0x2E3238), bevel=.03, seg=2)
    a.box(-.13, -.86, .48, .13, -.6, .485, RED)


@furn(1, 1)
def boxing_bag(a):
    a.box(-.35, .2, 0, .35, .3, .06, DARK)
    a.box(-.04, .2, 0, .04, .28, 2.2, DARK)
    a.rod((0, .25, 2.2), (0, -.12, 2.2), .03, DARK, n=6)
    a.rod((0, .25, 1.8), (0, 0, 2.2), .02, DARK, n=5)
    for k in range(3):
        ang = math.radians(k * 120)
        a.rod((0, -.1, 2.17), (.14 * math.cos(ang), -.1 + .14 * math.sin(ang), 1.75), .006, CHROME, n=3)
    a.lathe(0, -.1, [(0, 0), (.14, 0), (.18, .06), (.18, .98), (.15, 1.05), (0, 1.05)], F(0xB03A2E), n=14, z=.7)
    a.cyl(0, -.1, 1.25, 1.33, .183, BLACK, n=14)


@furn(2, 1)
def gym_locker(a):
    a.box(-.98, -.2, 0, .98, .42, .1, DARK)
    a.box(-1, -.22, .1, 1, .44, 1.9, M(0x3D6E8C, "METAL"), bevel=.012)
    for i in range(4):
        x = -.97 + i * .49
        for k in range(2):
            z = .14 + k * .88
            a.box(x, -.235, z, x + .46, -.22, z + .84, M(0x4F86A8, "METAL"), bevel=.006)
            for v in range(3):
                a.box(x + .1, -.24, z + .66 + v * .04, x + .36, -.235, z + .68 + v * .04, IRON)
            a.box(x + .38, -.25, z + .36, x + .42, -.235, z + .46, CHROME)
            a.box(x + .16, -.24, z + .54, x + .3, -.235, z + .6, WHITE)


@furn(1, 2)
def hair_chair(a):
    # mirror station against the back, the chair out front
    a.box(-.48, .9, 0, .48, 1.0, 2.0, M(0xD5DBE2, "PAINT"))
    a.box(-.4, .88, .95, .4, .9, 1.85, WOOD_X, bevel=.01)
    a.box(-.36, .872, .99, .36, .88, 1.81, GLASS)
    for k in range(5):
        a.sphere(-.3 + k * .15, .86, 1.9, .03, E(0xFFF4D6, 3.0), seg=6, rings=4)
    a.box(-.46, .62, .78, .46, .9, .84, WOOD_L, bevel=.012)
    a.box(-.4, .7, .5, .4, .9, .78, WOOD_X)
    for k in range(3):
        bottle(a, -.3 + k * .1, .75, .84, (PINK, SKY, MINT)[k], s=.7, cap=WHITE)
    a.lathe(.25, .74, [(0, 0), (.05, 0), (.05, .1), (0, .1)], DARK, n=8, z=.84)
    for k in range(3):
        a.rod((.25 + (k - 1) * .02, .74, .92), (.25 + (k - 1) * .05, .74, 1.05), .006, (RED, CHROME, BLUE)[k], n=4)
    # chair: round base, hydraulic post, padded seat, back, arms, footrest
    # (built facing -Y, then turned round so the customer looks into the mirror)
    red = F(0xEB5757)
    with a.at(0, -.2, 0, rz=180):
        a.lathe(0, 0, [(0, 0), (.3, 0), (.3, .025), (.08, .06), (0, .06)], CHROME, n=16)
        a.cyl(0, 0, .05, .46, .04, CHROME, n=8)
        a.box(-.27, -.26, .46, .27, .24, .6, red, bevel=.05, seg=2)
        with a.at(0, .27, .56, rx=-8):
            a.box(-.25, -.05, 0, .25, .05, .56, red, bevel=.045, seg=2)
        for sx in (-1, 1):
            a.tube([(sx * .29, .2, .52), (sx * .31, .2, .78), (sx * .31, -.18, .78)], .018, CHROME, n=5)
            a.box(sx * .31 - .035, -.2, .78, sx * .31 + .035, .2, .815, BLACK, bevel=.012)
        a.tube([(-.15, -.26, .5), (-.15, -.45, .2), (.15, -.45, .2), (.15, -.26, .5)], .016, CHROME, n=5)


@furn(1, 2)
def hair_wash(a):
    a.box(-.4, .5, 0, .4, 1.0, .82, WHITE, bevel=.03, seg=2)
    a.lathe(0, .68, [(0, 0), (.16, 0), (.26, .16), (.28, .16), (.28, .2), (.24, .2), (.15, .05), (0, .05)], M(0x2E3238, "TILE"), n=14, z=.8)
    a.extrude([(-.1, .98), (.1, .98), (.07, .94), (-.07, .94)], .4, .46, M(0x2E3238, "TILE"), axis='y')
    a.tube([(.2, .9, .82), (.2, .9, 1.1), (.08, .78, 1.12)], .014, CHROME, n=5)
    for k in range(3):
        bottle(a, -.3 + k * .09, .92, .82, (PINK, SKY, MINT)[k], s=.7, cap=WHITE)
    # reclined chair
    a.box(-.28, -.5, 0, .28, .3, .4, DARK, bevel=.03)
    black = F(0x2E3238)
    a.box(-.27, -.6, .4, .27, .0, .54, black, bevel=.05, seg=2)
    with a.at(0, -.0, .5, rx=-32):
        a.box(-.25, -.05, 0, .25, .05, .62, black, bevel=.045, seg=2)
    with a.at(0, -.6, .48, rx=-65):
        a.box(-.22, -.04, -.36, .22, .04, 0, black, bevel=.035, seg=2)


# ---------------------------------------------------------------------------------------------- nightlife, storage, decor

@furn(1, 1)
def speaker_large(a):
    a.box(-.28, -.24, 0, .28, .28, 1.2, BLACK, bevel=.02)
    a.box(-.25, -.25, .06, .25, -.24, 1.14, GREY_D)
    for z, r in ((.3, .19), (.72, .19)):
        a.rod((0, -.25, z), (0, -.265, z), r, GUN, n=16)
        a.rod((0, -.265, z), (0, -.27, z), r * .86, BLACK, n=16)
        a.rod((0, -.27, z), (0, -.3, z), r * .5, DARK, n=12, r2=r * .28)
        a.sphere(0, -.29, z, r * .26, GREY, seg=8, rings=5, scale=(1, .5, 1))
    a.rod((0, -.25, 1.02), (0, -.262, 1.02), .07, GUN, n=12)
    a.sphere(0, -.262, 1.02, .04, CHROME, seg=8, rings=5, scale=(1, .5, 1))
    for sx in (-1, 1):
        a.rod((sx * .17, -.25, 1.02), (sx * .17, -.256, 1.02), .035, BLACK, n=8)
        a.box(sx * .28, -.05, .7, sx * .3, .1, .8, GUN)


@furn(2, 1)
def dj_booth(a):
    a.box(-1, -.4, 0, 1, -.3, 1.05, BLACK, bevel=.01)
    a.box(-1, -.4, 0, -.9, .4, 1.05, BLACK, bevel=.01)
    a.box(.9, -.4, 0, 1, .4, 1.05, BLACK, bevel=.01)
    for k, m in enumerate((E(0xFF5FD8), E(0x5FE3FF), E(0xFF5FD8))):
        a.box(-.88, -.412, .18 + k * .3, .88, -.4, .24 + k * .3, m)
    a.box(-1.02, -.44, 1.0, 1.02, .42, 1.05, DARK, bevel=.012)
    for x in (-.55, .55):
        a.box(x - .3, -.28, 1.05, x + .3, .3, 1.1, GREY_D, bevel=.012)
        a.cyl(x, .0, 1.1, 1.115, .23, BLACK, n=20)
        a.cyl(x, .0, 1.115, 1.12, .08, (RED, YELLOW)[x > 0], n=12)
        a.cyl(x, .0, 1.12, 1.13, .012, CHROME, n=6)
        a.rod((x + .26, .24, 1.13), (x + .12, -.02, 1.13), .008, CHROME, n=4)
        a.cyl(x + .26, .24, 1.1, 1.14, .025, CHROME, n=8)
    a.box(-.2, -.28, 1.05, .2, .3, 1.11, DARK, bevel=.012)
    for i in range(4):
        for j in range(3):
            a.cyl(-.135 + i * .09, .0 + j * .1, 1.11, 1.13, .016, (CHROME, RED, CHROME, SKY)[i], n=6)
    for i in range(3):
        a.box(-.12 + i * .1, -.24, 1.11, -.1 + i * .1, -.08, 1.115, BLACK)
        a.box(-.125 + i * .1, -.2 + i * .04, 1.115, -.095 + i * .1, -.17 + i * .04, 1.13, WHITE)
    # headphones resting on the rail
    a.torus(.82, .05, 1.14, .08, .014, DARK, n=12, k=5, axis='x')


@furn(3, 1)
def cocktail_bar(a):
    a.box(-1.48, -.36, 0, 1.48, .1, .1, BLACK)
    a.box(-1.5, -.4, .1, 1.5, .12, 1.05, WOOD_X, bevel=.015)
    for i in range(6):
        a.box(-1.44 + i * .49, -.41, .2, -1.02 + i * .49, -.4, .95, WOOD_D)
    a.box(-1.5, -.412, .12, 1.5, -.4, .16, E(0xFFD27A, 2.0))
    a.box(-1.54, -.48, 1.05, 1.54, .16, 1.11, M(0x2E3238, "STONE"), bevel=.015)
    a.rod((-1.45, -.47, .25), (1.45, -.47, .25), .018, M(0xD4AF37, "METAL"), n=6)
    for x in (-1.2, 0, 1.2):
        a.rod((x, -.4, .25), (x, -.47, .25), .012, M(0xD4AF37, "METAL"), n=5)
    # back bar: lit shelves of bottles
    a.box(-1.5, .34, 0, 1.5, .45, 2.0, WOOD_X)
    tints = (0x3E7C4F, 0x7A3B2E, 0x2C5E7A, 0xD9A441, 0xE9EDF2, 0x8E3B6E)
    for r, z in enumerate((1.15, 1.48, 1.81)):
        a.box(-1.46, .2, z - .03, 1.46, .34, z, GLASS_D)
        a.box(-1.46, .19, z - .035, 1.46, .2, z - .005, E(0x5FE3FF, 2.0))
        for i in range(16):
            bottle(a, -1.38 + i * .184, .27, z, M(tints[(i + r * 2) % 6], "GLASS", alpha=.85), s=.95 + .1 * ((i + r) % 3), cap=(YELLOW, RED, WHITE)[i % 3])
    # taps, shaker, glasses, a cocktail
    a.box(-.9, -.2, 1.11, -.6, -.08, 1.36, CHROME, bevel=.02)
    for k in range(3):
        a.rod((-.84 + k * .09, -.2, 1.3), (-.84 + k * .09, -.26, 1.38), .012, (BLACK, RED, YELLOW)[k], n=5)
    a.lathe(.1, -.15, [(0, 0), (.035, 0), (.045, .14), (.03, .2), (.03, .24), (0, .24)], CHROME, n=10, z=1.11)
    for i in range(4):
        a.lathe(.5 + i * .12, -.1, [(0, 0), (.03, 0), (.006, .01), (.006, .07), (.045, .14), (.04, .14), (0, .08)], M(0xDDEEF7, "GLASS", alpha=.5), n=8, z=1.11)
    a.lathe(1.2, -.2, [(0, 0), (.03, 0), (.006, .01), (.006, .07), (.05, .14), (0, .13)], M(0xF2994A, "GLASS", alpha=.9), n=8, z=1.11)
    a.ico(1.24, -.2, 1.26, .015, RED)


@furn(1, 1)
def mannequin(a):
    skin = M(0xE6E1D8, "PLASTIC")
    a.cyl(0, 0, 0, .03, .22, CHROME, n=14)
    a.cyl(0, .06, .03, .9, .012, CHROME, n=5)
    for sx in (-1, 1):
        a.rod((sx * .09, 0, .08), (sx * .1, 0, .5), .05, F(0x2F5D8C), n=8, r2=.06)
        a.rod((sx * .1, 0, .5), (sx * .1, 0, .92), .06, F(0x2F5D8C), n=8, r2=.085)
        a.box(sx * .09 - .05, -.14, .03, sx * .09 + .05, .07, .09, WHITE, bevel=.02)
    a.lathe(0, 0, [(0, 0), (.15, 0), (.17, .08), (.14, .25), (.17, .42), (.19, .5), (.12, .56), (.05, .58), (0, .58)], F(0xEB5757), n=12, z=.9)
    for sx in (-1, 1):
        a.rod((sx * .2, 0, 1.4), (sx * .26, .0, 1.12), .045, F(0xEB5757), n=8)
        a.rod((sx * .26, 0, 1.12), (sx * .25, -.08, .88), .035, skin, n=8)
    a.cyl(0, 0, 1.46, 1.54, .045, skin, n=8)
    a.sphere(0, 0, 1.63, .1, skin, seg=10, rings=7, scale=(.9, 1, 1.15))


@furn(2, 1)
def storage_shelf(a):
    for x in (-.96, .96):
        for y in (-.36, .36):
            a.box(x - .03, y - .03, 0, x + .03, y + .03, 1.95, GUN)
    for z in (.12, .72, 1.32, 1.9):
        a.box(-.96, -.38, z, .96, .38, z + .04, M(0x7E878F, "METAL"))
    for sx in (-1, 1):
        a.rod((sx * .96, -.36, .16), (sx * .96, .36, .72), .012, GUN, n=4)
        a.rod((sx * .96, -.36, 1.36), (sx * .96, .36, .76), .012, GUN, n=4)
    seed = 3
    for z in (.16, .76, 1.36):
        x = -.88
        while x < .6:
            seed = (seed * 1103515245 + 12345) & 0x7fffffff
            w = .32 + (seed % 4) * .06
            h = .26 + ((seed >> 3) % 4) * .06
            a.box(x, -.3, z, x + w, .3, z + h, (CARDBOARD, CARDBOARD_D)[(seed >> 6) % 2], bevel=.006)
            a.box(x + w / 2 - .03, -.302, z, x + w / 2 + .03, .302, z + h + .002, M(0xD9C7A3, "PAPER"))
            x += w + .05


@furn(2, 1)
def pallet_rack(a):
    blue = M(0x2F5D8C, "METAL")
    orange = M(0xF2994A, "METAL")
    for x in (-.96, .96):
        for y in (-.4, .4):
            a.box(x - .04, y - .04, 0, x + .04, y + .04, 3.0, blue)
            a.box(x - .07, y - .07, 0, x + .07, y + .07, .02, YELLOW)
        for k in range(4):
            z = .2 + k * .7
            a.rod((x, -.4, z), (x, .4, z + .5), .015, blue, n=4)
    for z in (1.3, 2.5):
        for y in (-.4, .4):
            a.box(-.96, y - .03, z, .96, y + .03, z + .1, orange)
    for z in (0, 1.4, 2.6):
        for cx in (-.46, .46):
            with a.at(cx, 0, z):
                pallet_part(a)
                if z < 2:
                    for i in range(2):
                        for j in range(2):
                            for k in range(2 if (cx > 0 or z > 0) else 3):
                                a.box(-.38 + i * .39, -.38 + j * .39, .14 + k * .3, -.01 + i * .39, -.01 + j * .39, .42 + k * .3,
                                      (CARDBOARD, CARDBOARD_D)[(i + j + k) % 2], bevel=.006)
                else:
                    a.box(-.36, -.36, .14, .36, .36, .8, M(0xDDEEF7, "GLASS", alpha=.55), bevel=.02)
                    a.box(-.32, -.32, .14, .32, .32, .76, (BLUE, RED)[cx > 0])


def pallet_part(a):
    for y in (-.38, 0, .38):
        a.box(-.42, y - .045, 0, .42, y + .045, .09, WOOD_L)
    for k in range(5):
        x = -.42 + k * .19
        a.box(x, -.42, .09, x + .08, .42, .115, M(0xC69A6B, "WOOD"))
    for k in range(3):
        a.box(-.42 + k * .38, -.42, -.0, -.34 + k * .38, .42, .02, M(0xC69A6B, "WOOD"))


@furn(1, 1)
def pallet(a):
    pallet_part(a)
    for i in range(2):
        for j in range(2):
            a.box(-.38 + i * .39, -.38 + j * .39, .115, -.01 + i * .39, -.01 + j * .39, .4, (CARDBOARD, CARDBOARD_D)[(i + j) % 2], bevel=.006)
    a.box(-.3, -.3, .4, .1, .1, .66, CARDBOARD, bevel=.006)


@furn(1, 1)
def hand_truck(a):
    red = M(0xC0392B, "METAL")
    for sx in (-1, 1):
        a.tube([(sx * .2, .1, .08), (sx * .2, .1, 1.1), (sx * .12, .16, 1.25)], .016, red, n=5)
        a.rod((sx * .26, .14, .13), (sx * .31, .14, .13), .13, RUBBER, n=12)
        a.rod((sx * .31, .14, .13), (sx * .315, .14, .13), .06, GREY, n=8)
    a.rod((-.26, .14, .13), (.26, .14, .13), .012, GUN, n=5)
    a.rod((-.12, .16, 1.25), (.12, .16, 1.25), .018, RUBBER, n=6)
    for z in (.4, .75):
        a.rod((-.2, .1, z), (.2, .1, z), .012, red, n=5)
    a.box(-.24, -.25, .0, .24, .1, .02, GUN)


@furn(1, 1)
def cleaning_station(a):
    a.lathe(-.12, 0, [(0, 0), (.14, 0), (.17, .3), (.18, .3), (.18, .33), (.15, .33), (.13, .03), (0, .03)], YELLOW, n=12)
    a.cyl(-.12, 0, .2, .22, .13, M(0x7FC4EE, "GLASS", alpha=.7), n=12)
    a.rod((-.12, .05, .1), (.02, .1, 1.35), .014, WOOD_L, n=5)
    a.lathe(-.12, .05, [(0, 0), (.07, 0), (.05, .1), (0, .12)], M(0xE9EDF2, "FABRIC"), n=8, z=.03)
    a.box(.12, -.2, 0, .36, .2, .02, YELLOW)
    a.extrude([(.14, .02), (.34, .02), (.26, .6), (.22, .6)], -.2, -.18, YELLOW, axis='y')
    a.extrude([(.14, .02), (.34, .02), (.26, .6), (.22, .6)], .18, .2, YELLOW, axis='y')
    a.box(.2, -.205, .3, .28, -.2, .42, DARK)
    bottle(a, .3, .0, .02, BLUE, s=1.1, cap=WHITE)


@furn(1, 1)
def shopping_baskets(a):
    for k in range(5):
        z = .04 + k * .09
        m = (RED, RED, GREEN, RED, GREEN)[k]
        a.extrude([(-.2, z), (.2, z), (.24, z + .22), (.22, z + .22), (.185, z + .02), (-.185, z + .02), (-.22, z + .22), (-.24, z + .22)], -.15, .15, m, axis='y')
        a.extrude([(-.15, z), (.15, z), (.17, z + .22), (-.17, z + .22)], -.2, -.185, m, axis='x')
        a.extrude([(-.15, z), (.15, z), (.17, z + .22), (-.17, z + .22)], .185, .2, m, axis='x')
    z = .04 + 4 * .09 + .22
    a.tube([(-.23, 0, z), (-.2, 0, z + .14), (.2, 0, z + .14), (.23, 0, z)], .012, DARK, n=5)
    a.box(-.26, -.2, 0, .26, .2, .04, GUN)


@furn(1, 1)
def security_camera(a):
    a.box(-.08, .4, 2.3, .08, .45, 2.5, WHITE)
    a.tube([(0, .4, 2.4), (0, .25, 2.4), (0, .2, 2.34)], .018, WHITE, n=6)
    with a.at(0, .1, 2.3, rx=25):
        a.box(-.06, -.2, -.06, .06, .1, .06, WHITE, bevel=.015)
        a.box(-.07, -.24, .04, .07, .06, .07, WHITE)
        a.rod((0, -.2, 0), (0, -.215, 0), .04, BLACK, n=10)
        a.rod((0, -.215, 0), (0, -.22, 0), .02, GLASS_D, n=8)
    a.sphere(.04, -.07, 2.2, .008, E(0xFF2E2E, 4.0), seg=5, rings=3)


@furn(2, 1)
def security_gate(a):
    for x in (-.8, .8):
        a.box(x - .1, -.2, 0, x + .1, .2, .05, GREY_D, bevel=.012)
        a.extrude([(-.16, .05), (.16, .05), (.16, 1.45), (.08, 1.6), (-.08, 1.6), (-.16, 1.45)], x - .03, x + .03, WHITE, axis='x')
        a.extrude([(-.1, .2), (.1, .2), (.1, 1.35), (-.1, 1.35)], x - .035, x + .035, M(0xBFE3F5, "GLASS", alpha=.5), axis='x')
        a.sphere(x, 0, 1.63, .03, E(0x2EE87A, 3.0), seg=6, rings=4)


@furn(1, 1)
def stage_lights(a):
    for sx in (-1, 1):
        a.cyl(sx * .4, 0, 0, 2.6, .025, IRON, n=6)
        a.cyl(sx * .4, 0, 0, .03, .12, IRON, n=10)
    for z in (2.45, 2.6):
        a.rod((-.45, 0, z), (.45, 0, z), .02, IRON, n=6)
    for k in range(5):
        a.rod((-.4 + k * .2, 0, 2.45 + (k % 2) * .15), (-.2 + k * .2, 0, 2.6 - (k % 2) * .15), .01, IRON, n=4)
    for k, hexc in enumerate((0xFF5FD8, 0x5FE3FF, 0xFFE680)):
        x = -.28 + k * .28
        a.rod((x, 0, 2.45), (x, 0, 2.36), .01, IRON, n=4)
        with a.at(x, 0, 2.28, rx=35, rz=(k - 1) * -20):
            a.rod((0, .08, 0), (0, -.1, 0), .075, BLACK, n=10, r2=.09)
            a.rod((0, -.1, 0), (0, -.105, 0), .08, E(hexc, 5.0), n=10)


@furn(1, 1)
def neon_cactus(a):
    a.box(-.3, .4, 1.1, .3, .44, 2.0, M(0x1B1D21, "PAINT"))
    g = E(0x2EE87A, 4.0)
    a.tube([(0, .38, 1.2), (0, .38, 1.9)], .028, g, n=6)
    a.tube([(0, .38, 1.5), (-.16, .38, 1.5), (-.16, .38, 1.72)], .024, g, n=6)
    a.tube([(0, .38, 1.38), (.16, .38, 1.38), (.16, .38, 1.62)], .024, g, n=6)
    a.sphere(0, .38, 1.9, .028, g, seg=6, rings=4)
    a.rod((-.2, .38, 1.17), (.2, .38, 1.17), .018, E(0xFF5FD8, 4.0), n=5)


@furn(1, 1)
def painting(a):
    a.box(-.4, .4, 1.15, .4, .45, 1.85, M(0xD4AF37, "METAL"), bevel=.012)
    a.box(-.35, .392, 1.2, .35, .4, 1.8, M(0x9FC4DD, "PAINT"))
    a.box(-.35, .39, 1.2, .35, .392, 1.4, M(0x5FB36A, "PAINT"))
    a.extrude([(-.35, 1.4), (-.1, 1.62), (.05, 1.48), (.2, 1.58), (.35, 1.4)], .388, .392, M(0x6A7F99, "PAINT"), axis='y')
    a.ico(.2, .388, 1.7, .05, E(0xFFE680, 1.5), scale=(1, .1, 1), sub=2)


# ---------------------------------------------------------------------------------------------- build

def build_furniture():
    coll = category("Furniture")
    obs = []
    for name, w, d, fn in FURN:
        a = Asset(name)
        fn(a)
        obs.append(a.finish(coll, footprint=[w, d]))
    lay_out(obs, x0=0, y0=0, gap=.9, row_width=22)
    return obs
