"""Small things: stock, food, goods, money. Needs kit.py in front. Real-world size, standing on z = 0."""

ITEMS = []


def item(fn):
    ITEMS.append((fn.__name__, fn))
    return fn


GOLD = M(0xE8C04A, "METAL")
BUN = M(0xD9A45B, "FOOD")
BUN_D = M(0xB9803C, "FOOD")
CHOC = M(0x6B4226, "FOOD")
CREAM_F = M(0xF7E7CE, "FOOD")
TAPE = M(0xD9C7A3, "PAPER")
KRAFT = M(0xB98A55, "PAPER")
CASH = M(0x6FAF7A, "PAPER")
CASH_D = M(0x4E8C5E, "PAPER")


def cup_profile(r0, r1, h, wall=.004):
    return [(0, 0), (r0, 0), (r1, h), (r1 - wall, h), (r0 - wall, wall * 2), (0, wall * 2)]


# ---------------------------------------------------------------------------------------------- stock and shopping

@item
def cardboard_box(a):
    a.box(-.25, -.2, 0, .25, .2, .3, CARDBOARD, bevel=.006)
    a.box(-.03, -.202, .1, .03, .202, .302, TAPE)
    a.box(-.252, -.03, .2, .252, .03, .302, TAPE)
    a.box(.08, -.203, .06, .2, -.2, .14, PAPER)
    for k in range(2):
        a.box(-.2 + k * .05, -.203, .18, -.17 + k * .05, -.2, .26, CARDBOARD_D)


@item
def open_box(a):
    a.box(-.25, -.2, 0, .25, .2, .02, CARDBOARD_D)
    a.box(-.25, -.2, 0, .25, -.19, .3, CARDBOARD)
    a.box(-.25, .19, 0, .25, .2, .3, CARDBOARD)
    a.box(-.25, -.2, 0, -.24, .2, .3, CARDBOARD)
    a.box(.24, -.2, 0, .25, .2, .3, CARDBOARD)
    for sx in (-1, 1):
        with a.at(sx * .25, 0, .3, ry=sx * -50):
            a.box(0 if sx > 0 else -.2, -.2, 0, .2 if sx > 0 else 0, .2, .008, CARDBOARD)
    for sy in (-1, 1):
        with a.at(0, sy * .2, .3, rx=sy * 65):
            a.box(-.25, 0 if sy > 0 else -.16, 0, .25, .16 if sy > 0 else 0, .008, CARDBOARD)
    for i in range(4):
        for j in range(3):
            a.cyl(-.17 + i * .115, -.12 + j * .12, .02, .26, .05, (RED, STEEL, GREEN, YELLOW)[(i + j) % 4], n=8)


@item
def wooden_crate(a):
    a.box(-.28, -.2, 0, .28, .2, .02, WOOD_L)
    for z in (.03, .13, .23):
        a.box(-.28, -.2, z, .28, -.185, z + .08, WOOD_L)
        a.box(-.28, .185, z, .28, .2, z + .08, WOOD_L)
        a.box(-.28, -.185, z, -.265, .185, z + .08, WOOD_L)
        a.box(.265, -.185, z, .28, .185, z + .08, WOOD_L)
    for x in (-.265, .245):
        for y in (-.185, .165):
            a.box(x, y, 0, x + .02, y + .02, .31, WOOD)
    for i in range(4):
        for j in range(3):
            a.ico(-.18 + i * .12, -.11 + j * .11, .27, .06, M((0xE5483B, 0xC93A2E, 0x6DBE45)[(i + j) % 3], "FOOD"), rz=i * 31 + j * 17)


@item
def shopping_bag(a):
    a.extrude([(-.15, 0), (.15, 0), (.17, .36), (-.17, .36)], -.07, .07, KRAFT, axis='y')
    a.box(-.1, -.072, .12, .1, -.07, .26, RED)
    a.ico(0, -.074, .19, .04, WHITE, scale=(1, .1, 1), sub=2)
    for sy in (-1, 1):
        a.tube([(-.07, sy * .06, .36), (-.06, sy * .06, .47), (.06, sy * .06, .47), (.07, sy * .06, .36)], .006, WOOD_L, n=4)
    a.rod((-.02, 0, .3), (.06, .02, .46), .02, BUN, n=6)
    a.ico(-.08, 0, .38, .045, LEAF_L)


@item
def shopping_basket(a):
    z = 0
    a.extrude([(-.2, z), (.2, z), (.24, z + .22), (.22, z + .22), (.185, z + .02), (-.185, z + .02), (-.22, z + .22), (-.24, z + .22)], -.15, .15, RED, axis='y')
    a.extrude([(-.15, z), (.15, z), (.17, z + .22), (-.17, z + .22)], -.2, -.185, RED, axis='x')
    a.extrude([(-.15, z), (.15, z), (.17, z + .22), (-.17, z + .22)], .185, .2, RED, axis='x')
    a.tube([(-.23, 0, .22), (-.2, 0, .36), (.2, 0, .36), (.23, 0, .22)], .012, DARK, n=5)
    a.box(-.15, -.1, .02, -.02, .1, .2, YELLOW)
    a.cyl(.08, -.05, .02, .16, .04, GREEN, n=8)
    a.cyl(.08, .06, .02, .16, .04, STEEL, n=8)
    a.rod((-.1, .05, .2), (.12, .0, .26), .025, BUN, n=6)


@item
def shopping_cart(a):
    chrome = CHROME
    for sx in (-1, 1):
        a.tube([(sx * .22, .42, .1), (sx * .22, -.4, .1)], .012, chrome, n=5)
        a.tube([(sx * .22, .42, .1), (sx * .22, .5, 1.0)], .012, chrome, n=5)
        for y in (-.36, .4):
            a.rod((sx * .22 - .015, y, .05), (sx * .22 + .015, y, .05), .05, RUBBER, n=10)
    a.rod((-.24, .5, 1.0), (.24, .5, 1.0), .016, RED, n=6)
    a.rod((-.22, -.4, .1), (.22, -.4, .1), .012, chrome, n=5)
    # basket: an open mesh suggested by rails and thin slats
    for z in (.42, .6, .78, .95):
        t = (z - .42) / .53
        x = .2 + .06 * t
        y0 = -.38 - .1 * t
        a.tube([(-x, y0, z), (x, y0, z), (x, .46, z), (-x, .46, z), (-x, y0, z)], .008, chrome, n=4, joints=False)
    for k in range(7):
        y = -.35 + k * .13
        a.rod((-.2, y, .42), (.2, y, .42), .006, chrome, n=4)
        for sx in (-1, 1):
            a.rod((sx * .2, y, .42), (sx * .26, y - .03, .95), .006, chrome, n=4)
    for k in range(5):
        x = -.18 + k * .09
        a.rod((x, -.38, .42), (x * 1.3, -.48, .95), .006, chrome, n=4)
    a.box(-.19, -.36, .425, .19, .44, .43, GREY)
    a.box(-.15, -.3, .43, .05, -.05, .7, YELLOW, bevel=.006)
    a.box(-.1, .0, .43, .15, .3, .62, CARDBOARD, bevel=.006)
    a.cyl(.11, -.2, .43, .7, .05, GREEN, n=8)


@item
def gift_box(a):
    a.box(-.14, -.14, 0, .14, .14, .2, RED, bevel=.006)
    a.box(-.15, -.15, .2, .15, .15, .25, M(0xD94A4A, "PAINT"), bevel=.006)
    a.box(-.152, -.02, 0, .152, .02, .252, GOLD)
    a.box(-.02, -.152, 0, .02, .152, .252, GOLD)
    for sx in (-1, 1):
        a.torus(sx * .045, 0, .285, .04, .012, GOLD, n=8, k=4, axis='y')
    a.ico(0, 0, .265, .022, GOLD)


# ---------------------------------------------------------------------------------------------- food and drink

@item
def coffee_cup(a):
    a.lathe(0, 0, cup_profile(.03, .043, .11), PAPER, n=12)
    a.lathe(0, 0, [(.034, .035), (.041, .035), (.0425, .08), (.0355, .08)], M(0x8B5A3C, "PAPER"), n=12, loop=True)
    a.lathe(0, 0, [(0, .106), (.046, .106), (.046, .118), (.036, .126), (0, .126)], BLACK, n=12)
    a.box(-.012, -.03, .126, .012, -.022, .13, DARK)


@item
def soda_can(a):
    a.lathe(0, 0, [(0, 0), (.026, 0), (.033, .008), (.033, .108), (.027, .12), (.027, .123), (0, .12)], RED, n=12)
    a.cyl(0, 0, .04, .09, .0335, WHITE, n=12, caps=False)
    a.cyl(0, 0, .0, .004, .027, STEEL, n=12)
    a.cyl(0, 0, .12, .1225, .0265, STEEL, n=12)


@item
def soda_cup(a):
    a.lathe(0, 0, cup_profile(.035, .048, .15), RED, n=12)
    a.cyl(0, 0, .06, .1, .0445, WHITE, n=12, r2=.0465, caps=False)
    a.lathe(0, 0, [(0, .147), (.051, .147), (.051, .157), (.04, .165), (0, .165)], M(0xF7FAFC, "PLASTIC"), n=12)
    a.rod((.01, 0, .12), (.03, 0, .25), .004, WHITE, n=5)
    a.rod((.01, 0, .16), (.024, 0, .2), .0045, RED, n=5)


@item
def water_bottle(a):
    water = M(0x9FD4F2, "GLASS", alpha=.6)
    a.lathe(0, 0, [(0, 0), (.03, 0), (.034, .01), (.034, .07), (.031, .08), (.034, .09), (.034, .15), (.015, .2), (.015, .22), (0, .22)], water, n=12)
    a.cyl(0, 0, .215, .235, .017, BLUE, n=10)
    a.cyl(0, 0, .1, .15, .0345, M(0xF7FAFC, "PAPER"), n=12, caps=False)
    a.cyl(0, 0, .115, .135, .035, BLUE, n=12, caps=False)


@item
def wine_bottle(a):
    a.lathe(0, 0, [(0, 0), (.036, 0), (.038, .01), (.038, .18), (.016, .25), (.014, .31), (.016, .315), (0, .315)], M(0x23402B, "GLASS", alpha=.92), n=12)
    a.cyl(0, 0, .285, .32, .0165, M(0x7A1F2B, "PAINT"), n=10)
    a.cyl(0, 0, .06, .15, .0385, M(0xF2E7CF, "PAPER"), n=12, caps=False)
    a.cyl(0, 0, .09, .12, .039, M(0x7A1F2B, "PAINT"), n=12, caps=False)


@item
def beer_bottle(a):
    a.lathe(0, 0, [(0, 0), (.03, 0), (.031, .01), (.031, .13), (.014, .19), (.013, .23), (0, .23)], M(0x7A4A1E, "GLASS", alpha=.9), n=12)
    a.cyl(0, 0, .225, .236, .015, GOLD, n=10)
    a.cyl(0, 0, .05, .11, .0315, YELLOW, n=12, caps=False)
    a.cyl(0, 0, .16, .185, .0215, YELLOW, n=10, r2=.0165, caps=False)


@item
def burger(a):
    a.lathe(0, 0, [(0, 0), (.05, 0), (.058, .012), (.058, .022), (0, .022)], BUN, n=12)
    a.cyl(0, 0, .022, .036, .06, M(0x5A3320, "FOOD"), n=12)
    a.box(-.058, -.058, .036, .058, .058, .041, M(0xF2B632, "FOOD"))
    a.cyl(0, 0, .041, .047, .055, M(0xD0392B, "FOOD"), n=10)
    a.lathe(0, 0, [(0, .047), (.064, .047), (.066, .052), (.062, .056), (0, .056)], M(0x6DBE45, "FOOD"), n=9)
    a.lathe(0, 0, [(0, .056), (.058, .056), (.058, .068), (.045, .092), (.022, .104), (0, .106)], BUN, n=12)
    for k in range(7):
        ang = k * 2.4
        r = .012 + (k % 3) * .012
        a.ico(r * math.cos(ang), r * math.sin(ang), .104 - r * .35, .004, CREAM_F, sub=0)


@item
def fries(a):
    a.extrude([(-.035, 0), (.035, 0), (.05, .09), (.03, .075), (0, .085), (-.03, .075), (-.05, .09)], -.022, .022, RED, axis='y')
    a.ico(0, -.023, .04, .015, YELLOW, scale=(1, .1, 1), sub=2)
    fry = M(0xF2C94C, "FOOD")
    for k in range(11):
        x = -.04 + k * .008
        y = -.012 + (k % 3) * .012
        with a.at(x, y, .05, ry=(k - 5) * 4, rx=(k % 3 - 1) * 6):
            a.box(-.005, -.005, 0, .005, .005, .07 + (k * 7 % 5) * .008, fry)


@item
def hotdog(a):
    a.rod((-.07, -.012, .018), (.07, -.012, .018), .02, BUN, n=8)
    a.rod((-.07, .012, .018), (.07, .012, .018), .02, BUN, n=8)
    for sx in (-1, 1):
        a.sphere(sx * .07, -.012, .018, .02, BUN, seg=8, rings=4)
        a.sphere(sx * .07, .012, .018, .02, BUN, seg=8, rings=4)
        a.sphere(sx * .085, 0, .034, .014, M(0xB5452E, "FOOD"), seg=8, rings=4)
    a.rod((-.085, 0, .034), (.085, 0, .034), .014, M(0xB5452E, "FOOD"), n=8)
    pts = [(-.07 + k * .02, (.006 if k % 2 else -.006), .049) for k in range(8)]
    a.tube(pts, .003, YELLOW, n=4, joints=False)


@item
def pizza_box(a):
    a.box(-.17, -.17, 0, .17, .17, .035, CARDBOARD, bevel=.004)
    with a.at(0, .17, .035, rx=62):
        a.box(-.17, -.34, 0, .17, 0, .006, CARDBOARD)
        a.cyl(0, -.17, -.002, 0, .09, RED, n=12)
    a.cyl(0, 0, .035, .043, .15, BUN, n=16)
    a.cyl(0, 0, .043, .046, .135, M(0xF2B632, "FOOD"), n=16)
    for k in range(9):
        ang = k * 2.4
        r = .03 + (k % 3) * .04
        a.cyl(r * math.cos(ang), r * math.sin(ang), .046, .049, .018, M(0xC0392B, "FOOD"), n=8)
    for k in range(5):
        ang = k * 1.9 + .5
        a.ico(.07 * math.cos(ang), .07 * math.sin(ang), .049, .008, LEAF, sub=0)


@item
def donut(a):
    a.torus(0, 0, .02, .032, .02, BUN, n=14, k=7)
    a.torus(0, 0, .026, .032, .017, PINK, n=14, k=6)
    for k in range(10):
        ang = k * 2.4
        with a.at(.032 * math.cos(ang), .032 * math.sin(ang), .043, rz=k * 67):
            a.box(-.006, -.0015, 0, .006, .0015, .003, (WHITE, YELLOW, SKY, GREEN)[k % 4])


@item
def cupcake(a):
    a.lathe(0, 0, [(0, 0), (.022, 0), (.032, .04), (0, .04)], M(0xE9EDF2, "PAPER"), n=10, smooth=False)
    a.lathe(0, 0, [(0, .04), (.036, .04), (.038, .052), (.028, .066), (.03, .07), (.018, .084), (.019, .087), (0, .1)], PINK, n=10)
    a.ico(0, 0, .104, .01, RED)


@item
def croissant(a):
    for k in range(7):
        t = (k - 3) / 3
        ang = t * 1.1
        r = .026 * (1 - abs(t) * .55)
        a.sphere(.06 * math.sin(ang), .05 - .05 * math.cos(ang), r, r, (BUN, BUN_D)[k % 2], seg=8, rings=5, scale=(.7, 1, 1))


@item
def ice_cream(a):
    a.lathe(0, 0, [(0, 0), (.028, .11), (.03, .11), (.03, .12), (0, .12)], BUN, n=10, smooth=False)
    a.sphere(0, 0, .135, .034, PINK, seg=10, rings=6)
    a.sphere(.004, 0, .175, .028, CREAM_F, seg=10, rings=6)
    a.ico(.008, 0, .205, .01, RED)
    a.rod((-.015, 0, .19), (-.03, 0, .225), .004, CHOC, n=4)


@item
def apple(a):
    a.lathe(0, 0, [(0, .008), (.02, 0), (.036, .02), (.04, .045), (.032, .068), (.014, .075), (0, .068)], M(0xD0392B, "FOOD"), n=10)
    a.rod((0, 0, .068), (.006, 0, .09), .003, WOOD_D, n=4)
    with a.at(.006, 0, .085, ry=-30):
        a.extrude([(0, 0), (.02, .008), (.034, 0), (.02, -.008)], 0, .002, LEAF, axis='z')


@item
def bananas(a):
    peel = M(0xF2D14C, "FOOD")
    for k in range(4):
        with a.at(0, 0, 0, rz=-24 + k * 16):
            pts = [(-.02 + .09 * math.sin(t * 1.5), 0, .02 + .03 * k * 0 + .09 * (1 - math.cos(t * 1.5)) * .5 + .012) for t in (0, .25, .5, .75, 1.0)]
            pts = [(p[0] + .0, (k - 1.5) * .012, p[2]) for p in pts]
            for (p0, p1), r in zip(zip(pts, pts[1:]), (.014, .016, .015, .011)):
                a.rod(p0, p1, r, peel, n=5, smooth=False)
            a.rod(pts[-1], (pts[-1][0] + .012, pts[-1][1], pts[-1][2] + .01), .005, CHOC, n=4)
    a.rod((-.025, 0, .03), (-.045, 0, .05), .008, M(0x8B6A2E, "FOOD"), n=5)


@item
def bread(a):
    a.extrude([(-.05, 0), (.05, 0), (.055, .05), (.04, .08), (0, .09), (-.04, .08), (-.055, .05)], -.12, .12, BUN, axis='y')
    for k in range(4):
        with a.at(0, -.08 + k * .055, .086, rz=25):
            a.box(-.035, -.004, 0, .035, .004, .004, CREAM_F)


@item
def bouquet(a):
    with a.at(0, 0, 0, rx=-12):
        a.lathe(0, 0, [(0, 0), (.02, 0), (.09, .26), (.085, .26), (.018, .01), (0, .01)], M(0xF2D9E4, "PAPER"), n=8, smooth=False)
        a.torus(0, 0, .09, .034, .008, RED, n=10, k=4)
        blooms = (RED, PINK, YELLOW, WHITE, PURPLE, ORANGE, M(0xE04F7A, "PAINT"))
        for k in range(7):
            ang = k * 2.4
            r = .0 if k == 0 else .05
            a.ico(r * math.cos(ang), r * math.sin(ang), .28 + (k % 2) * .015, .036, blooms[k], rz=k * 40)
        for k in range(4):
            ang = k * 1.57 + .7
            a.ico(.07 * math.cos(ang), .07 * math.sin(ang), .25, .03, LEAF, rz=k * 30, scale=(1, 1, .6))


@item
def popcorn(a):
    a.lathe(0, 0, [(0, 0), (.05, 0), (.075, .17), (.07, .17), (.047, .008), (0, .008)], WHITE, n=4, smooth=False)
    for k in range(4):
        with a.at(0, 0, 0, rz=k * 90 + 45):
            a.extrude([(-.012, .02), (.012, .02), (.018, .16), (-.018, .16)], .036, .0545, RED, axis='y')
    pop = M(0xFBF3D0, "FOOD")
    for k in range(14):
        ang = k * 2.4
        r = .012 + (k % 4) * .014
        a.ico(r * math.cos(ang), r * math.sin(ang), .165 + (3 - k % 4) * .009, .016, (pop, M(0xF2D98A, "FOOD"))[k % 3 == 0], rz=k * 50)


@item
def cocktail(a):
    glass = M(0xDDEEF7, "GLASS", alpha=.45)
    a.lathe(0, 0, [(0, 0), (.035, 0), (.006, .008), (.005, .09), (.055, .15), (.052, .15), (0, .098)], glass, n=12)
    a.lathe(0, 0, [(0, .099), (.04, .136), (0, .136)], M(0xF2994A, "GLASS", alpha=.85), n=12)
    a.rod((.02, 0, .12), (.05, 0, .19), .0015, WOOD_L, n=3)
    a.ico(.038, 0, .162, .01, M(0x4E9E36, "FOOD"))
    with a.at(-.052, 0, .15, ry=90):
        a.cyl(0, 0, -.003, .003, .022, YELLOW, n=10)


# ---------------------------------------------------------------------------------------------- goods

@item
def book_stack(a):
    for k, (c, w, d, rz) in enumerate(((0xB03A2E, .15, .21, 4), (0x2F5D8C, .14, .2, -8), (0x3E7C4F, .13, .19, 6), (0xE0B030, .12, .17, -3))):
        with a.at(0, 0, k * .034, rz=rz):
            a.box(-w / 2, -d / 2, 0, w / 2, d / 2, .032, M(c, "PAPER"))
            a.box(-w / 2 + .004, -d / 2 - .001, .004, w / 2 + .001, d / 2 + .001, .028, PAPER)


@item
def magazine(a):
    with a.at(0, 0, 0, rz=8):
        a.box(-.105, -.14, 0, .105, .14, .006, PAPER)
        a.box(-.105, -.14, .006, .105, .14, .008, RED)
        a.box(-.08, .06, .008, .08, .11, .009, WHITE)
        a.box(-.08, -.11, .008, .03, .03, .009, SKY)


@item
def laptop(a):
    a.box(-.16, -.11, 0, .16, .11, .014, STEEL, bevel=.004)
    a.box(-.14, -.04, .014, .14, .09, .016, DARK)
    a.box(-.045, -.095, .014, .045, -.05, .0155, GREY)
    with a.at(0, .11, .014, rx=-18):
        a.box(-.16, -.005, 0, .16, .005, .21, STEEL, bevel=.003)
        a.box(-.148, -.008, .01, .148, -.005, .2, SCREEN)


@item
def smartphone(a):
    with a.at(0, 0, 0, rz=-12):
        a.box(-.036, -.075, 0, .036, .075, .008, DARK, bevel=.003)
        a.box(-.033, -.07, .008, .033, .07, .009, SCREEN)
        a.box(-.012, .064, .009, .012, .068, .0095, BLACK)


@item
def headphones(a):
    with a.at(0, 0, .09, rx=0):
        a.tube([(-.08, 0, 0), (-.075, 0, .05), (-.04, 0, .085), (0, 0, .095), (.04, 0, .085), (.075, 0, .05), (.08, 0, 0)], .008, DARK, n=6)
        for sx in (-1, 1):
            a.rod((sx * .09, 0, -.01), (sx * .065, 0, -.01), .042, DARK, n=12)
            a.rod((sx * .065, 0, -.01), (sx * .05, 0, -.01), .038, F(0x4A4F57), n=12)
            a.rod((sx * .09, 0, -.01), (sx * .093, 0, -.01), .028, RED, n=10)


@item
def camera(a):
    a.box(-.065, -.03, 0, .065, .03, .08, DARK, bevel=.006)
    a.box(-.03, -.025, .08, .03, .025, .1, DARK, bevel=.004)
    a.box(-.065, -.032, .015, -.03, -.03, .065, F(0x1B1D21))
    a.rod((.012, -.03, .042), (.012, -.07, .042), .034, BLACK, n=14)
    a.rod((.012, -.07, .042), (.012, -.078, .042), .036, GREY_D, n=14)
    a.rod((.012, -.078, .042), (.012, -.08, .042), .026, M(0x3C5266, "GLASS", alpha=.9), n=12)
    a.cyl(-.045, 0, .08, .09, .012, CHROME, n=8)
    a.box(.035, -.032, .06, .055, -.03, .072, E(0xFFF4D6, 1.0))


@item
def tshirt(a):
    m = F(0x2F80ED)
    a.box(-.14, -.11, 0, .14, .11, .03, m, bevel=.008)
    a.extrude([(-.045, -.11), (0, -.07), (.045, -.11)], .03, .032, F(0xE9EDF2), axis='z')
    a.box(-.14, -.02, .03, .14, .0, .032, F(0x2567C2))
    a.box(.05, .03, .03, .1, .07, .032, YELLOW)


@item
def sneakers(a):
    for sx, rz in ((-1, 8), (1, -6)):
        with a.at(sx * .07, 0, 0, rz=rz):
            a.extrude([(-.14, 0), (.14, 0), (.145, .02), (-.14, .03)], -.045, .045, WHITE, axis='y')
            a.extrude([(-.14, .03), (.145, .02), (.14, .05), (.04, .078), (.0, .12), (-.14, .125)], -.043, .043, RED, axis='y')
            a.box(.0, -.02, .1, .06, .02, .085, WHITE)
            a.box(-.1, -.0445, .05, .02, .0445, .07, WHITE)


@item
def handbag(a):
    m = M(0x8E3B4E, "FABRIC")
    a.extrude([(-.16, 0), (.16, 0), (.13, .2), (-.13, .2)], -.06, .06, m, axis='y')
    a.extrude([(-.135, .13), (.135, .13), (.13, .21), (-.13, .21)], -.065, .065, M(0x7A2E40, "FABRIC"), axis='y')
    a.box(-.02, -.07, .12, .02, -.062, .16, GOLD)
    a.tube([(-.08, 0, .2), (-.07, 0, .32), (0, 0, .36), (.07, 0, .32), (.08, 0, .2)], .008, M(0x7A2E40, "FABRIC"), n=5)


@item
def hat(a):
    a.lathe(0, 0, [(0, 0), (.17, 0), (.165, .01), (.085, .016), (.08, .1), (.05, .115), (0, .11)], F(0xD9C7A3), n=14)
    a.lathe(0, 0, [(.088, .014), (.0865, .045), (.08, .045), (.08, .014)], F(0x2E3238), n=14, loop=True)


@item
def ring_box(a):
    a.box(-.03, -.03, 0, .03, .03, .028, F(0x2A1F4E), bevel=.004)
    with a.at(0, .03, .028, rx=-70):
        a.box(-.03, -.06, 0, .03, 0, .02, F(0x2A1F4E), bevel=.004)
    a.box(-.024, -.024, .028, .024, .024, .031, F(0xE9EDF2))
    a.torus(0, 0, .041, .011, .0025, GOLD, n=10, k=4, axis='y')
    a.ico(0, 0, .056, .006, M(0x9FE8FF, "GLASS", alpha=.9))


@item
def watch(a):
    a.torus(0, 0, .03, .028, .006, DARK, n=12, k=4, axis='x')
    a.rod((-.011, 0, .058), (-.011, 0, .064), .0005, DARK, n=3)
    a.cyl(0, 0, .054, .064, .02, GOLD, n=12)
    a.cyl(0, 0, .064, .0655, .017, WHITE, n=12)
    a.box(-.001, 0, .0655, .001, .012, .0662, BLACK)
    a.box(0, -.001, .0655, .009, .001, .0662, BLACK)
    a.box(-.036, -.03, 0, .036, .03, .004, F(0x2A1F4E))


@item
def diamond(a):
    a.lathe(0, 0, [(0, 0), (.04, .045), (.026, .062), (0, .062)], M(0x9FE8FF, "GLASS", alpha=.85), n=8, smooth=False)


@item
def necklace(a):
    a.lathe(0, 0, [(0, 0), (.07, 0), (.05, .04), (.03, .1), (.06, .16), (.035, .24), (0, .25)], F(0x2A1020), n=10)
    with a.at(0, -.02, .17, rx=20):
        a.torus(0, 0, 0, .05, .0035, GOLD, n=14, k=4, axis='y')
    a.ico(0, -.045, .115, .011, M(0xE85D5D, "GLASS", alpha=.9))


@item
def teddy_bear(a):
    fur = F(0xB07A52)
    light = F(0xD9B38C)
    a.sphere(0, 0, .1, .085, fur, seg=10, rings=7, scale=(1, .9, 1.1))
    a.sphere(0, -.06, .09, .045, light, seg=8, rings=5, scale=(1, .5, 1.1))
    a.sphere(0, 0, .24, .065, fur, seg=10, rings=7)
    a.sphere(0, -.055, .225, .028, light, seg=8, rings=5)
    a.ico(0, -.082, .232, .008, BLACK)
    for sx in (-1, 1):
        a.sphere(sx * .05, 0, .295, .024, fur, seg=8, rings=5, scale=(1, .6, 1))
        a.ico(sx * .025, -.058, .255, .006, BLACK)
        a.sphere(sx * .09, -.02, .14, .032, fur, seg=8, rings=5, scale=(.9, .9, 1.6))
        a.sphere(sx * .055, -.06, .03, .038, fur, seg=8, rings=5, scale=(.9, 1.5, .8))
        a.sphere(sx * .055, -.115, .032, .022, light, seg=6, rings=4, scale=(1, .3, 1))
    a.torus(0, 0, .185, .05, .01, RED, n=10, k=4)


@item
def umbrella(a):
    with a.at(0, 0, 0, ry=12):
        a.rod((0, 0, .05), (0, 0, .85), .008, DARK, n=5)
        a.lathe(0, 0, [(.035, .18), (.03, .74), (.012, .82), (0, .84)], BLUE, n=8, smooth=False)
        a.lathe(0, 0, [(0, .18), (.035, .18)], BLUE, n=8)
        a.torus(.03, 0, .05, .03, .01, WOOD_D, n=8, k=5, axis='y')
        a.torus(0, 0, .5, .033, .005, YELLOW, n=8, k=4)


@item
def vinyl(a):
    with a.at(0, 0, 0, rz=6):
        a.box(-.158, -.158, 0, .158, .158, .006, M(0x7A2E40, "PAPER"))
        a.box(-.158, -.158, .006, .158, -.05, .0065, YELLOW)
    with a.at(.08, .02, .0065):
        a.cyl(0, 0, 0, .003, .15, BLACK, n=20)
        a.cyl(0, 0, .003, .0038, .05, RED, n=14)
        a.cyl(0, 0, .0038, .0045, .004, CHROME, n=6)


@item
def dumbbell(a):
    a.rod((-.1, 0, .05), (.1, 0, .05), .014, CHROME, n=8)
    for sx in (-1, 1):
        a.rod((sx * .1, 0, .05), (sx * .045, 0, .05), .05, BLACK, n=6, smooth=False)


# ---------------------------------------------------------------------------------------------- money

@item
def cash_stack(a):
    for k in range(3):
        with a.at((k % 2) * .012, k * .006, k * .024, rz=(k - 1) * 5):
            a.box(-.078, -.033, 0, .078, .033, .022, CASH)
            a.box(-.02, -.034, -.0005, .02, .034, .0225, WHITE)
            a.ico(0, 0, .0222, .012, CASH_D, scale=(1, 1, .05), sub=2)


@item
def coins(a):
    for k, (x, y, n) in enumerate(((0, 0, 7), (.04, .01, 4), (-.01, .042, 5), (.03, -.035, 2))):
        for i in range(n):
            a.cyl(x + (i % 2) * .002, y + (i % 3) * .001, i * .0045, i * .0045 + .004, .018, (GOLD, M(0xD9B23C, "METAL"))[i % 2], n=12)
    with a.at(-.045, -.02, .018, rx=80, rz=30):
        a.cyl(0, 0, -.002, .002, .018, GOLD, n=12)


@item
def money_bag(a):
    a.lathe(0, 0, [(0, 0), (.09, 0), (.13, .06), (.125, .14), (.06, .22), (.035, .24), (.06, .28), (.05, .29), (0, .27)], M(0xC9B48A, "FABRIC"), n=12)
    a.torus(0, 0, .24, .04, .008, WOOD_D, n=10, k=4)
    with a.at(0, -.128, .1, rx=-8):
        a.box(-.012, -.004, -.04, .012, .004, .04, CASH_D)
        a.tube([(.022, 0, .025), (-.022, 0, .025), (-.022, 0, 0), (.022, 0, 0), (.022, 0, -.025), (-.022, 0, -.025)], .006, CASH_D, n=4)


@item
def briefcase(a):
    a.box(-.22, -.05, 0, .22, .05, .3, WOOD_X, bevel=.012)
    a.box(-.222, -.052, .2, .222, .052, .205, BLACK)
    for sx in (-1, 1):
        a.box(sx * .12 - .02, -.058, .19, sx * .12 + .02, -.05, .225, GOLD)
    a.tube([(-.07, 0, .3), (-.06, 0, .345), (.06, 0, .345), (.07, 0, .3)], .009, BLACK, n=5)


@item
def safe(a):
    a.box(-.3, -.28, .04, .3, .3, .8, GUN, bevel=.02)
    for x in (-.24, .24):
        for y in (-.2, .22):
            a.cyl(x, y, 0, .05, .03, BLACK, n=8)
    a.box(-.25, -.3, .1, .25, -.28, .74, M(0x4A5560, "METAL"), bevel=.008)
    a.rod((-.08, -.3, .45), (-.08, -.33, .45), .06, STEEL, n=14)
    a.rod((-.08, -.33, .45), (-.08, -.345, .45), .025, BLACK, n=10)
    for k in range(3):
        ang = math.radians(k * 120 + 20)
        a.rod((.12, -.32, .45), (.12 + .08 * math.cos(ang), -.32, .45 + .08 * math.sin(ang)), .008, CHROME, n=5)
    a.rod((.12, -.3, .45), (.12, -.33, .45), .02, CHROME, n=8)
    for z in (.2, .64):
        a.box(.25, -.3, z, .28, -.27, z + .08, BLACK)


def build_items():
    coll = category("Items")
    obs = []
    for name, fn in ITEMS:
        a = Asset(name)
        fn(a)
        obs.append(a.finish(coll))
    return obs
