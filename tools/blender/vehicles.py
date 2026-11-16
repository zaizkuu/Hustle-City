"""Cars, vans, trucks and two-wheelers. Needs kit.py in front. The nose points to -Y; length runs along Y."""

VEHICLES = []


def vehicle(fn):
    VEHICLES.append((fn.__name__, fn))
    return fn


V_GLASS = M(0x26313D, "GLASS", alpha=1)
HEAD = E(0xFFF4D6, 2.0)
TAIL = E(0xFF4D4D, 1.5)
TYRE = M(0x1B1D21, "RUBBER")
ARCH = M(0x16181B, "PLASTIC")
PLATE = M(0xF5F5F5, "PAINT")


def wheel(a, x, y, r, w=.2, hub=CHROME):
    sx = 1 if x > 0 else -1
    a.rod((x - w / 2, y, r), (x + w / 2, y, r), r, TYRE, n=14)
    a.rod((x + sx * w / 2, y, r), (x + sx * (w / 2 + .012), y, r), r * .58, hub, n=10)
    a.rod((x + sx * (w / 2 + .012), y, r), (x + sx * (w / 2 + .02), y, r), r * .2, GUN, n=6)


def wheels(a, W, ys, r, w=.2, hub=CHROME, arch=True):
    for y in ys:
        for sx in (-1, 1):
            x = sx * (W / 2 - w / 2 + .01)
            wheel(a, x, y, r, w, hub)
            if arch:
                a.rod((sx * (W / 2 - w - .02), y, r), (sx * (W / 2 + .004), y, r), r + .055, ARCH, n=14)


def car(a, col, L=4.4, W=1.8, belt=.84, roof=1.42, hood=1.2, ws=.55, cab=1.55, rw=.5, tail=.88, nose=.66, clear=.24, r=.32,
        wf=.85, wr=.85, trim=DARK):
    """A car body from a side profile: hood, raked windshield, cabin, rear window, boot."""
    f = -L / 2
    b = L / 2
    y1 = f + hood
    y2 = y1 + ws
    y3 = y2 + cab
    y4 = min(y3 + rw, b - .02)
    side = [(f, clear + .1), (f, nose), (f + .14, nose + .09), (y1, belt), (y4, belt)]
    if y4 < b - .2:
        side += [(b - .06, tail), (b, tail - .1)]
    else:
        side += [(b, belt - .12)]
    side += [(b, clear + .1), (b - .12, clear), (f + .12, clear)]
    a.extrude(side, -W / 2, W / 2, col, axis='x')
    # greenhouse: one dark glass block, a roof slab and three pillars a side
    gx = W / 2 - .07
    a.extrude([(y1, belt), (y2, roof), (y3, roof), (y4, belt)], -gx, gx, V_GLASS, axis='x')
    a.box(-gx + .02, y2 - .03, roof - .005, gx - .02, y3 + .03, roof + .04, col, bevel=.012)
    ym = (y2 + y3) / 2
    for sx in (-1, 1):
        x0, x1 = sorted((sx * (gx - .004), sx * (gx + .012)))
        a.extrude([(y1, belt), (y1 + .1, belt), (y2 + .08, roof), (y2 - .02, roof)], x0, x1, col, axis='x')
        a.extrude([(y4, belt), (y4 - .1, belt), (y3 - .08, roof), (y3 + .02, roof)], x0, x1, col, axis='x')
        a.box(x0, ym - .04, belt, x1, ym + .04, roof, col)
        a.box(sx * (W / 2) - (0 if sx > 0 else .08), y1 + .1, belt, sx * (W / 2) + (.08 if sx > 0 else 0), y1 + .2, belt + .1, col)
        for y in (ym - .45, ym + .45):
            a.box(sx * (W / 2 + .008) - .008, y, belt - .16, sx * (W / 2 + .008) + .008, y + .14, belt - .13, CHROME)
        a.box(sx * (W / 2 + .004) - .004, f + wf + r + .12, clear + .06, sx * (W / 2 + .004) + .004, b - wr - r - .12, clear + .13, trim)
    wheels(a, W, (f + wf, b - wr), r)
    # bumpers, lights, grille, plates
    a.box(-W / 2 - .01, f - .05, clear + .04, W / 2 + .01, f + .08, clear + .22, trim, bevel=.02)
    a.box(-W / 2 - .01, b - .08, clear + .04, W / 2 + .01, b + .05, clear + .22, trim, bevel=.02)
    for sx in (-1, 1):
        a.box(sx * (W / 2 - .12) - .2 * (sx > 0), f - .012, nose - .14, sx * (W / 2 - .12) + .2 * (sx < 0), f + .01, nose - .03, HEAD)
        a.box(sx * (W / 2 - .1) - .26 * (sx > 0), b - .01, tail - .24 if y4 < b - .2 else belt - .3,
              sx * (W / 2 - .1) + .26 * (sx < 0), b + .012, tail - .12 if y4 < b - .2 else belt - .18, TAIL)
    a.box(-W / 2 + .4, f - .012, nose - .16, W / 2 - .4, f + .01, nose - .04, ARCH)
    a.box(-.24, f - .062, clear + .08, .24, f - .05, clear + .19, PLATE)
    a.box(-.24, b + .05, clear + .08, .24, b + .062, clear + .19, PLATE)
    return y1, y2, y3, y4


@vehicle
def beater(a):
    """The car you start with: a tired old hatchback with a primer-grey wing and a roof rack."""
    col = M(0x6F8FA0, "PAINT")
    y1, y2, y3, y4 = car(a, col, L=3.9, W=1.66, hood=1.0, ws=.42, cab=1.75, rw=.3, roof=1.44, belt=.86, nose=.7, r=.29, wf=.75, wr=.7)
    a.box(-.835, y1 - .9, .45, -.83, y1 + .05, .84, M(0x8A8F96, "PAINT"))
    a.box(.83, 0.2, .4, .836, .9, .6, M(0x9C5A3C, "PAINT"))
    for y in (y2 + .2, y3 - .2):
        a.rod((-.7, y, 1.52), (.7, y, 1.52), .018, GUN, n=5)
        for sx in (-1, 1):
            a.rod((sx * .68, y, 1.46), (sx * .68, y, 1.52), .014, GUN, n=4)
    a.box(-.5, y2 + .1, 1.54, .5, y3 - .1, 1.72, CARDBOARD, bevel=.01)
    a.box(-.52, y2 + .6, 1.54, .52, y2 + .66, 1.73, M(0xD9C7A3, "PAPER"))


@vehicle
def sedan(a):
    car(a, M(0x2F80ED, "PAINT"))


@vehicle
def taxi(a):
    y1, y2, y3, y4 = car(a, M(0xF2C94C, "PAINT"))
    a.box(-.28, (y2 + y3) / 2 - .1, 1.46, .28, (y2 + y3) / 2 + .1, 1.62, E(0xFFE680, 1.5), bevel=.02)
    a.box(-.2, (y2 + y3) / 2 - .105, 1.5, .2, (y2 + y3) / 2 - .1, 1.58, BLACK)
    for sx in (-1, 1):
        for k in range(10):
            y = y1 - .1 + k * .24
            for row in range(2):
                if (k + row) % 2 == 0:
                    a.box(sx * .904 - .004, y, .56 + row * .07, sx * .904 + .004, y + .24, .63 + row * .07, BLACK)


@vehicle
def sports_car(a):
    col = M(0xD0392B, "PAINT")
    y1, y2, y3, y4 = car(a, col, L=4.3, W=1.86, belt=.72, roof=1.16, hood=1.45, ws=.7, cab=.8, rw=.75, tail=.78, nose=.5, clear=.16, r=.33, wf=.8, wr=.8)
    for sx in (-1, 1):
        a.box(sx * .7 - .04, 1.85, .78, sx * .7 + .04, 1.95, .98, col)
        a.box(sx * .45 - .08, -1.4, .7, sx * .45 + .08, -.5, .735, ARCH)
    a.box(-.9, 1.78, .98, .9, 2.08, 1.02, col, bevel=.01)
    a.box(-.5, 2.1, .2, .5, 2.16, .3, ARCH)
    for sx in (-1, 1):
        a.rod((sx * .3, 2.12, .25), (sx * .3, 2.2, .25), .05, CHROME, n=8)


@vehicle
def suv(a):
    col = M(0x2E3238, "PAINT")
    y1, y2, y3, y4 = car(a, col, L=4.7, W=1.92, belt=1.0, roof=1.74, hood=1.25, ws=.45, cab=2.5, rw=.2, nose=.86, clear=.32, r=.38, wf=.9, wr=.9)
    for sx in (-1, 1):
        a.rod((sx * .72, y2 + .1, 1.82), (sx * .72, y3 - .1, 1.82), .02, CHROME, n=5)
        for y in (y2 + .2, y3 - .2):
            a.rod((sx * .72, y, 1.77), (sx * .72, y, 1.82), .015, CHROME, n=4)
    a.rod((0, 2.36, .95), (0, 2.5, .95), .34, TYRE, n=14)
    a.rod((0, 2.5, .95), (0, 2.52, .95), .2, col, n=10)


@vehicle
def pickup(a):
    col = M(0xC0392B, "PAINT")
    L, W = 5.2, 1.9
    f, b = -L / 2, L / 2
    clear, belt, nose, r = .34, 1.0, .88, .38
    a.extrude([(f, clear + .1), (f, nose), (f + .14, nose + .09), (f + 1.35, belt), (b, belt), (b, clear + .1), (b - .12, clear), (f + .12, clear)],
              -W / 2, W / 2, col, axis='x')
    y1, y2, y3, y4 = f + 1.35, f + 1.8, f + 2.9, f + 3.0
    gx = W / 2 - .07
    a.extrude([(y1, belt), (y2, 1.72), (y3, 1.72), (y4, belt)], -gx, gx, V_GLASS, axis='x')
    a.box(-gx + .02, y2 - .03, 1.715, gx - .02, y3 + .03, 1.76, col, bevel=.012)
    for sx in (-1, 1):
        x0, x1 = sorted((sx * (gx - .004), sx * (gx + .012)))
        a.extrude([(y1, belt), (y1 + .1, belt), (y2 + .08, 1.72), (y2 - .02, 1.72)], x0, x1, col, axis='x')
        a.extrude([(y4, belt), (y4 - .1, belt), (y3 - .08, 1.72), (y3 + .02, 1.72)], x0, x1, col, axis='x')
        a.box(sx * (W / 2) - (0 if sx > 0 else .08), y1 + .1, belt, sx * (W / 2) + (.08 if sx > 0 else 0), y1 + .2, belt + .1, col)
        a.box(sx * (W / 2 - .06) - .04, y4 + .06, belt, sx * (W / 2 - .06) + .04, b - .02, belt + .28, col)
        a.box(sx * (W / 2 - .12) - .2 * (sx > 0), f - .012, nose - .14, sx * (W / 2 - .12) + .2 * (sx < 0), f + .01, nose - .03, HEAD)
        a.box(sx * (W / 2 - .06) - .05, b - .01, belt - .25, sx * (W / 2 - .06) + .05, b + .012, belt + .2, TAIL)
    a.box(-W / 2 + .02, y4 + .02, belt, W / 2 - .02, y4 + .1, belt + .28, col)
    a.box(-W / 2 + .02, b - .08, belt, W / 2 - .02, b - .0, belt + .28, col)
    a.box(-W / 2 + .1, y4 + .1, belt, W / 2 - .1, b - .08, belt + .02, ARCH)
    wheels(a, W, (f + .95, b - 1.0), r)
    a.box(-W / 2 - .01, f - .06, clear + .04, W / 2 + .01, f + .08, clear + .26, CHROME, bevel=.02)
    a.box(-W / 2 - .01, b - .08, clear + .04, W / 2 + .01, b + .06, clear + .26, CHROME, bevel=.02)
    a.box(-W / 2 + .4, f - .012, nose - .2, W / 2 - .4, f + .01, nose - .02, ARCH)
    # a load in the bed
    a.box(-.6, y4 + .2, belt + .02, -.05, y4 + .8, belt + .5, CARDBOARD, bevel=.008)
    a.box(.05, y4 + .25, belt + .02, .65, y4 + .9, belt + .42, CARDBOARD_D, bevel=.008)
    a.box(-.4, y4 + .95, belt + .02, .4, b - .2, belt + .34, CARDBOARD, bevel=.008)


@vehicle
def van(a):
    col = M(0xE9EDF2, "PAINT")
    L, W, H = 5.0, 1.95, 2.3
    f, b = -L / 2, L / 2
    clear, r = .3, .36
    a.extrude([(f, clear + .1), (f, .85), (f + .25, 1.1), (f + .95, H), (b, H), (b, clear + .1), (b - .12, clear), (f + .12, clear)],
              -W / 2, W / 2, col, axis='x')
    a.extrude([(f + .27, 1.14), (f + .9, 2.14), (f + .93, 2.14), (f + .31, 1.14)], -W / 2 + .1, W / 2 - .1, V_GLASS, axis='x')
    for sx in (-1, 1):
        x0, x1 = sorted((sx * (W / 2 - .002), sx * (W / 2 + .01)))
        a.extrude([(f + .5, 1.2), (f + 1.5, 1.2), (f + 1.5, 2.05), (f + 1.05, 2.05)], x0, x1, V_GLASS, axis='x')
        a.box(x0, f + 1.6, .5, x1, b - .15, 1.0, BLUE)
        a.box(x0, f + 1.6, 1.0, x1, b - .15, 1.12, ORANGE)
        a.box(sx * (W / 2 + .012) - .006, f + 1.9, 1.3, sx * (W / 2 + .012) + .006, f + 3.6, 1.9, M(0xF7FAFC, "PAINT"))
        a.box(sx * (W / 2 + .016) - .006, f + 2.1, 1.5, sx * (W / 2 + .016) + .006, f + 3.2, 1.62, BLUE)
        a.box(sx * (W / 2 + .016) - .006, f + 2.1, 1.66, sx * (W / 2 + .016) + .006, f + 2.9, 1.74, ORANGE)
        a.box(sx * (W / 2) - (0 if sx > 0 else .1), f + .55, 1.25, sx * (W / 2) + (.1 if sx > 0 else 0), f + .65, 1.5, DARK)
        a.box(sx * (W / 2 - .14) - .2 * (sx > 0), f - .012, .62, sx * (W / 2 - .14) + .2 * (sx < 0), f + .01, .8, HEAD)
        a.box(sx * (W / 2 - .08) - .06, b - .01, .7, sx * (W / 2 - .08) + .06, b + .012, 1.3, TAIL)
    wheels(a, W, (f + .9, b - 1.0), r)
    a.box(-W / 2 - .01, f - .05, clear + .04, W / 2 + .01, f + .08, clear + .28, DARK, bevel=.02)
    a.box(-W / 2 - .01, b - .08, clear + .04, W / 2 + .01, b + .05, clear + .28, DARK, bevel=.02)
    a.box(-.5, f - .012, .55, .5, f + .01, .78, ARCH)
    a.box(-.01, b, .5, .01, b + .012, 2.2, GREY)
    a.box(-.12, b + .012, 1.2, .12, b + .03, 1.26, CHROME)


@vehicle
def box_truck(a):
    cab = M(0xE9EDF2, "PAINT")
    L, W = 6.6, 2.2
    f, b = -L / 2, L / 2
    clear, r = .42, .44
    a.extrude([(f, clear + .1), (f, 1.2), (f + .2, 1.45), (f + .7, 2.35), (f + 1.9, 2.35), (f + 1.9, clear + .1), (f + .12, clear)],
              -W / 2 + .1, W / 2 - .1, cab, axis='x')
    a.extrude([(f + .22, 1.5), (f + .66, 2.26), (f + .69, 2.26), (f + .26, 1.5)], -W / 2 + .2, W / 2 - .2, V_GLASS, axis='x')
    for sx in (-1, 1):
        x0, x1 = sorted((sx * (W / 2 - .102), sx * (W / 2 - .09)))
        a.extrude([(f + .45, 1.5), (f + 1.5, 1.5), (f + 1.5, 2.2), (f + .88, 2.2)], x0, x1, V_GLASS, axis='x')
        a.box(sx * (W / 2 - .1) - (0 if sx > 0 else .14), f + .5, 1.6, sx * (W / 2 - .1) + (.14 if sx > 0 else 0), f + .58, 2.0, DARK)
        a.box(sx * (W / 2 - .3) - .18 * (sx > 0), f - .012, .85, sx * (W / 2 - .3) + .18 * (sx < 0), f + .01, 1.05, HEAD)
        a.box(sx * (W / 2 - .12) - .08, b - .01, .7, sx * (W / 2 - .12) + .08, b + .012, .9, TAIL)
    a.box(-W / 2 + .05, f - .06, clear + .02, W / 2 - .05, f + .1, clear + .3, DARK, bevel=.02)
    a.box(-.6, f - .012, .8, .6, f + .01, 1.15, ARCH)
    # chassis and the cargo box with roll door and logo
    a.box(-.5, f + 1.9, clear + .1, .5, b - .1, clear + .32, ARCH)
    a.box(-W / 2, f + 2.0, clear + .32, W / 2, b, 3.3, M(0xF7FAFC, "PAINT"), bevel=.03)
    for sx in (-1, 1):
        x0, x1 = sorted((sx * (W / 2 - .002), sx * (W / 2 + .012)))
        a.box(x0, f + 2.3, 1.3, x1, b - .3, 2.7, GREEN)
        a.box(sx * (W / 2 + .016) - .005, f + 2.7, 1.8, sx * (W / 2 + .016) + .005, b - 1.2, 2.2, WHITE)
        a.box(sx * (W / 2 + .016) - .005, f + 2.7, 1.5, sx * (W / 2 + .016) + .005, b - 2.0, 1.65, YELLOW)
    a.box(-W / 2 + .12, b, clear + .5, W / 2 - .12, b + .012, 3.15, GREY)
    for k in range(9):
        a.box(-W / 2 + .12, b + .012, clear + .6 + k * .27, W / 2 - .12, b + .018, clear + .62 + k * .27, GUN)
    a.box(-W / 2, b - .05, clear + .12, W / 2, b + .2, clear + .3, GUN)
    wheels(a, W, (f + 1.0, b - 1.3, b - 2.25), r, w=.26, hub=WHITE, arch=False)


@vehicle
def bus(a):
    col = M(0x2F80ED, "PAINT")
    L, W, H = 10.5, 2.5, 3.0
    f, b = -L / 2, L / 2
    clear, r = .36, .46
    a.box(-W / 2, f, clear, W / 2, b, H, col, bevel=.08, seg=2)
    a.box(-W / 2 - .005, f - .005, clear + .2, W / 2 + .005, b + .005, 1.1, WHITE)
    a.box(-W / 2 + .1, f - .02, 1.35, W / 2 - .1, f + .01, 2.55, V_GLASS)
    a.box(-W / 2 + .3, f - .025, 2.6, W / 2 - .3, f + .01, 2.85, M(0x14181D, "PAINT"))
    a.box(-W / 2 + .5, f - .03, 2.66, W / 2 - .2, f - .025, 2.8, E(0xFFD27A, 1.5))
    a.box(-W / 2 + .2, b - .01, 1.7, W / 2 - .2, b + .02, 2.5, V_GLASS)
    for sx in (-1, 1):
        x0, x1 = sorted((sx * (W / 2 - .002), sx * (W / 2 + .012)))
        for k in range(7):
            y = f + 1.5 + k * 1.22
            a.box(x0, y, 1.4, x1, y + 1.08, 2.5, V_GLASS)
        a.box(sx * (W / 2 - .2) - .22 * (sx > 0), f - .02, .7, sx * (W / 2 - .2) + .22 * (sx < 0), f + .01, .92, HEAD)
        a.box(sx * (W / 2 - .15) - .1, b - .01, .8, sx * (W / 2 - .15) + .1, b + .02, 1.2, TAIL)
        a.box(sx * (W / 2) - (0 if sx > 0 else .2), f + .1, 2.0, sx * (W / 2) + (.2 if sx > 0 else 0), f + .2, 2.45, DARK)
    for y in (f + .25, f + 5.3):
        a.box(W / 2, y, clear + .12, W / 2 + .014, y + 1.1, 2.55, GREY_D)
        a.box(W / 2 + .014, y + .08, 1.0, W / 2 + .02, y + .5, 2.45, V_GLASS)
        a.box(W / 2 + .014, y + .6, 1.0, W / 2 + .02, y + 1.02, 2.45, V_GLASS)
    a.box(-.9, -2.0, H, .9, -.2, H + .22, GREY, bevel=.04)
    wheels(a, W, (f + 2.0, b - 2.4), r, w=.28, hub=WHITE)
    a.box(-W / 2 - .01, f - .06, clear, W / 2 + .01, f + .1, clear + .3, DARK, bevel=.02)
    a.box(-W / 2 - .01, b - .1, clear, W / 2 + .01, b + .06, clear + .3, DARK, bevel=.02)


@vehicle
def jeepney(a):
    steel = M(0xD9DEE4, "METAL")
    L, W = 6.2, 1.9
    f, b = -L / 2, L / 2
    clear, r = .4, .4
    # long flat-topped nose, flat windshield, open-sided passenger cabin with a low roof
    a.extrude([(f, clear + .1), (f, 1.05), (f + 1.5, 1.15), (f + 1.5, clear + .1), (f + .1, clear)], -.62, .62, steel, axis='x')
    for sx in (-1, 1):
        a.extrude([(f + .15, clear + .2), (f + .15, .9), (f + 1.5, 1.0), (f + 1.5, clear + .2)], sx * .62, sx * .95, RED, axis='x')
        a.rod((sx * .78, f + .1, .92), (sx * .78, f + .14, .92), .11, HEAD, n=10)
        a.rod((sx * .78, f + .14, .92), (sx * .78, f + .3, .92), .13, steel, n=10)
    a.box(-.45, f - .02, .5, .45, f + .01, 1.0, steel)
    for k in range(7):
        a.box(-.4 + k * .125, f - .035, .52, -.35 + k * .125, f - .02, .98, GUN)
    a.box(-W / 2, f + 1.5, clear, W / 2, b, 1.2, RED, bevel=.02)
    for sx in (-1, 1):
        x0, x1 = sorted((sx * (W / 2 - .002), sx * (W / 2 + .014)))
        a.box(x0, f + 1.6, .75, x1, b - .1, .9, YELLOW)
        a.box(x0, f + 1.6, .92, x1, b - .1, .98, BLUE)
        for k in range(6):
            y = f + 1.9 + k * .7
            a.extrude([(y, .5), (y + .25, .62), (y + .5, .5), (y + .25, .38)], x0, sx * (W / 2 + .02), (GREEN, WHITE, YELLOW)[k % 3], axis='x')
    a.box(-W / 2 + .03, f + 1.5, 1.2, W / 2 - .03, f + 1.56, 1.95, V_GLASS)
    a.box(-.02, f + 1.49, 1.2, .02, f + 1.57, 1.95, steel)
    for y in (f + 1.53, f + 2.6, f + 3.6, f + 4.6, b - .05):
        for sx in (-1, 1):
            a.box(sx * (W / 2 - .04) - .03, y - .03, 1.2, sx * (W / 2 - .04) + .03, y + .03, 1.98, steel)
    a.box(-W / 2 - .06, f + 1.3, 1.95, W / 2 + .06, b + .1, 2.08, M(0xF7FAFC, "PAINT"), bevel=.03)
    a.box(-W / 2 - .07, f + 1.25, 1.96, W / 2 + .07, f + 1.3, 2.3, YELLOW)
    a.box(-.6, f + 1.24, 2.02, .6, f + 1.25, 2.24, RED)
    # roof rack with luggage, side benches seen through the open sides, rear step
    for sx in (-1, 1):
        a.rod((sx * .8, f + 1.8, 2.2), (sx * .8, b - .2, 2.2), .02, steel, n=5)
        a.box(sx * .6 - .25, f + 1.9, 1.2, sx * .6 + .25, b - .2, 1.3, F(0x2F5D43))
    for k in range(4):
        a.rod((-.8, f + 1.8 + k * 1.3, 2.2), (.8, f + 1.8 + k * 1.3, 2.2), .02, steel, n=5)
        a.rod((-.8, f + 1.8 + k * 1.3, 2.08), (-.8, f + 1.8 + k * 1.3, 2.2), .015, steel, n=4)
        a.rod((.8, f + 1.8 + k * 1.3, 2.08), (.8, f + 1.8 + k * 1.3, 2.2), .015, steel, n=4)
    a.box(-.6, f + 2.2, 2.08, .1, f + 3.2, 2.4, CARDBOARD, bevel=.01)
    a.box(.15, f + 2.6, 2.08, .7, f + 3.6, 2.32, BLUE, bevel=.02)
    a.box(-.5, b, clear, .5, b + .35, clear + .06, steel)
    for sx in (-1, 1):
        a.box(sx * .85 - .08, b - .01, .9, sx * .85 + .08, b + .012, 1.1, TAIL)
        for k in range(3):
            a.rod((sx * (.2 + k * .22), f + .2, 1.1 + k * .0), (sx * (.2 + k * .22), f + .2, 1.36), .008, steel, n=4)
    for k in range(3):
        a.ico(-.3 + k * .3, f + .3, 1.14, .06, steel, scale=(1, 2.2, .8))
    wheels(a, W, (f + .8, b - 1.3), r, w=.22, hub=steel, arch=False)
    a.box(-W / 2 - .02, f - .1, clear + .0, W / 2 + .02, f + .05, clear + .22, steel, bevel=.02)


@vehicle
def scooter(a):
    col = M(0x27AE60, "PAINT")
    for y in (-.62, .62):
        a.rod((-.05, y, .22), (.05, y, .22), .22, TYRE, n=14)
        a.rod((-.06, y, .22), (.06, y, .22), .1, CHROME, n=8)
    a.extrude([(-.5, .25), (-.42, .7), (-.3, .72), (-.2, .3), (.3, .3), (.35, .62), (.85, .68), (.9, .45), (.7, .25)], -.16, .16, col, axis='x')
    a.box(-.15, -.2, .26, .15, .3, .31, ARCH)
    a.box(-.15, .3, .68, .15, .88, .78, F(0x2E3238), bevel=.03, seg=2)
    a.rod((0, -.45, .7), (0, -.36, 1.08), .03, col, n=6)
    a.rod((-.3, -.36, 1.08), (.3, -.36, 1.08), .018, ARCH, n=6)
    for sx in (-1, 1):
        a.rod((sx * .3, -.36, 1.08), (sx * .2, -.36, 1.08), .024, TYRE, n=6)
        a.rod((sx * .22, -.38, 1.12), (sx * .22, -.4, 1.24), .008, ARCH, n=4)
        a.rod((sx * .22, -.4, 1.26), (sx * .22, -.42, 1.26), .05, CHROME, n=8)
    a.rod((0, -.42, .95), (0, -.47, .95), .08, HEAD, n=10)
    a.extrude([(-.5, .72), (-.4, 1.0), (-.36, 1.0), (-.32, .72)], -.17, .17, col, axis='x')
    a.box(-.1, .9, .5, .1, .93, .58, TAIL)
    a.box(-.2, .55, .78, .2, .95, 1.1, ARCH, bevel=.03)
    a.box(-.21, .6, .85, .21, .9, 1.0, RED)
    a.rod((.0, .1, .08), (.2, .2, .08), .012, ARCH, n=4)


def build_vehicles():
    coll = category("Vehicles")
    obs = []
    for name, fn in VEHICLES:
        a = Asset(name)
        fn(a)
        obs.append(a.finish(coll, forward="-Y"))
    return obs
