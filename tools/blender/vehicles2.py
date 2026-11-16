"""The cities' own vehicles: a kei car, a tricycle, a modern jeepney, a moped with its rider, and a metro car.
Needs kit.py, vehicles.py and people.py in front (it adds to VEHICLES; riders are made of the people's parts).

The nose points to -Y; length runs along Y. Sizes follow world/Traffic.kt.
"""


def real_pal(shirt, pants, skin=0xE0AC69, hair=0x2B1D14, accent=0x1F4E9C):
    d = _dark_hex
    return Pal(F(shirt), F(d(shirt, .86)), F(pants), F(d(pants, .82)), M(skin, "PLAIN"), M(d(skin, .88), "PLAIN"), M(d(skin, .72), "PLAIN"),
               M(hair, "PLAIN"), F(accent))


def rider(a, x, y, seat, P, lean=10.0, arm=(52.0, 30.0), leg=(78.0, 82.0), helmet=None, q=1, spread=10.0, tee=True):
    """Someone sitting astride at (x, y), the seat `seat` meters up: knees forward, hands out to the bars."""
    with a.at(x, y, seat + .09):
        hips(a, P, q)
        with a.at(0, 0, W0, rx=lean):
            torso(a, P, q, tee=tee)
            with a.at(0, 0, NECK - WAIST, rx=-lean * .8):
                head(a, P, q)
                if helmet is None:
                    hair(a, P, q, 4)
                else:
                    a.sphere(0, .004, .19, .122, helmet, seg=14, rings=9, scale=(.95, 1.08, .95))
                    a.box(-.09, -.128, .15, .09, -.1, .2, M(0x26313D, "GLASS", alpha=1), bevel=.008)
            for sx in (-1, 1):
                with a.at(sx * SHOULDER_X[0], 0, SHOULDER - WAIST, rx=-arm[0], ry=-sx * 7):
                    upper_arm(a, P, q, short=tee)
                    with a.at(0, 0, ELBOW - SHOULDER, rx=-arm[1]):
                        forearm(a, P, q, bare=tee)
                        with a.at(0, 0, WRIST - ELBOW, rx=40):
                            hand(a, P, q)
        for sx in (-1, 1):
            with a.at(sx * HIP_X, 0, 0, rx=-leg[0], ry=-sx * spread):
                thigh(a, P, q)
                with a.at(0, 0, KNEE - HIP, rx=leg[1]):
                    shin(a, P, q)
                    with a.at(0, 0, ANKLE - KNEE, rx=leg[0] - leg[1]):
                        foot(a, P, q)


@vehicle
def kei_car(a):
    """A tall little city car, as boxy as the rules allow."""
    col = M(0x6FCF97, "PAINT")
    L, W = 3.4, 1.48
    car(a, col, L=L, W=W, belt=.92, roof=1.66, hood=.62, ws=.42, cab=1.92, rw=.3, nose=.78, clear=.2, r=.27, wf=.55, wr=.55)
    # the roof panel is its own paint: the game gives two-tone cars a roof of another color
    a.box(-W / 2 + .1, -L / 2 + 1.0, 1.695, W / 2 - .1, L / 2 - .42, 1.71, M(0xEDEFF2, "PAINT"), bevel=.006)
    # a roof rail each side and round lamps set into the nose
    for sx in (-1, 1):
        a.rod((sx * .55, -.6, 1.7), (sx * .55, 1.1, 1.7), .014, DARK, n=5)
        a.rod((sx * .5, -L / 2 - .012, .66), (sx * .5, -L / 2 + .02, .66), .075, CHROME, n=12)
    a.box(-.3, -L / 2 - .014, .5, .3, -L / 2, .58, CHROME)


def motorbike(a, col, seat=.74, bars=1.02, box=None):
    """A small motorcycle along Y at the origin: front wheel at -Y."""
    for y in (-.62, .6):
        a.rod((-.045, y, .26), (.045, y, .26), .26, TYRE, n=16)
        a.rod((-.055, y, .26), (.055, y, .26), .13, CHROME, n=10)
    a.rod((0, -.62, .26), (0, -.38, bars - .08), .022, CHROME, n=6)
    a.box(-.07, -.86, .5, .07, -.4, .54, col, bevel=.015)
    a.extrude([(-.4, .5), (-.36, .88), (-.05, .9), (.1, .72), (.1, .46), (-.3, .4)], -.12, .12, col, axis='x')
    a.box(-.13, -.02, .3, .13, .34, .62, GUN, bevel=.03)
    a.box(-.14, .05, seat - .08, .14, .78, seat, F(0x1F2327), bevel=.03, seg=2)
    a.extrude([(.3, .5), (.82, .56), (.86, .76), (.4, .7)], -.11, .11, col, axis='x')
    a.rod((.1, .24, .34), (.12, .9, .4), .035, CHROME, n=8)
    a.rod((-.3, -.38, bars), (.3, -.38, bars), .016, ARCH, n=6)
    for sx in (-1, 1):
        a.rod((sx * .3, -.38, bars), (sx * .2, -.38, bars), .024, TYRE, n=6)
        a.rod((sx * .24, -.4, bars + .03), (sx * .27, -.42, bars + .16), .007, ARCH, n=4)
        a.rod((sx * .27, -.42, bars + .18), (sx * .27, -.44, bars + .18), .045, CHROME, n=8)
    a.rod((0, -.46, bars - .14), (0, -.52, bars - .14), .085, HEAD, n=12)
    a.box(-.06, .86, .62, .06, .89, .7, TAIL)
    a.box(-.09, .88, .44, .09, .9, .54, PLATE)
    if box is not None:
        a.box(-.22, .5, seat + .02, .22, .95, seat + .46, box, bevel=.02)
        a.box(-.223, .6, seat + .16, .223, .85, seat + .32, WHITE)


@vehicle
def moped(a):
    """A delivery moped: a rider in a helmet, a box on the rack."""
    red = M(0xD0392B, "PAINT")
    motorbike(a, red, box=M(0xF2994A, "PAINT"))
    rider(a, 0, .2, .74, real_pal(0x2F80ED, 0x34383F, 0xE0AC69), lean=12, arm=(50, 34), leg=(72, 70), helmet=M(0xE9EDF2, "PAINT"), spread=14)


@vehicle
def tricycle(a):
    """A motorcycle with a roofed sidecar: the cab of every side street in Manila."""
    body = M(0x2F80ED, "PAINT")
    acc = M(0xF2C94C, "PAINT")
    steel = M(0xC9CED6, "METAL")
    with a.at(-.42, 0, 0):
        motorbike(a, body, seat=.72)
        rider(a, 0, .22, .72, real_pal(0xF2F2F2, 0x34383F, 0xC68B59), lean=8, arm=(48, 36), leg=(72, 70), spread=13)
    # the sidecar: a low tub with a bench, its own wheel, a windscreen, and a roof reaching over the rider
    a.extrude([(-.75, .3), (-.95, .62), (-.6, .95), (.7, .95), (.82, .3)], .0, .72, body, axis='x')
    a.box(.02, -.5, .34, .7, .7, .5, F(0x2E3238))
    a.box(.02, .5, .5, .7, .7, .95, F(0x2E3238), bevel=.02)
    a.box(-.005, -.9, .56, .725, -.6, .64, acc)
    a.box(.725, -.6, .6, .735, .7, .7, acc)
    a.rod((.74, .15, .24), (.84, .15, .24), .24, TYRE, n=14)
    a.rod((.84, .15, .24), (.852, .15, .24), .12, steel, n=10)
    a.box(.03, -.66, .95, .69, -.62, 1.5, M(0xBFE3F5, "GLASS", alpha=.4))
    for x in (.02, .7):
        for y in (-.64, .68):
            a.rod((x, y, .95), (x, y, 1.62), .014, steel, n=5)
    for y in (-.2, .5):
        a.rod((-.62, y, .95 if y > 0 else 1.05), (-.62, y, 1.62), .014, steel, n=5)
    a.box(-.72, -.82, 1.62, .76, .82, 1.68, body, bevel=.02)
    a.box(-.72, -.83, 1.56, .76, -.8, 1.62, acc)
    a.box(-.3, -.85, 1.68, .4, -.78, 1.82, WHITE)
    a.rod((.3, -.9, .7), (.3, -.94, .7), .06, HEAD, n=10)
    a.box(.5, .94, .5, .66, .96, .58, TAIL)
    # a passenger in the sidecar
    rider(a, .36, .36, .5, real_pal(0xEB5757, 0x1F3A5F, 0xE8B48A, 0x2B1D14), lean=-4, arm=(24, 60), leg=(84, 80), q=1, spread=4)


@vehicle
def modern_jeepney(a):
    """The modern jeepney: a tall minibus with a side door, wide windows and its route in lights over the windshield."""
    col = M(0xF7FAFC, "PAINT")
    band = M(0x1E8E5A, "PAINT")
    L, W, H = 7.0, 2.2, 2.85
    f, b = -L / 2, L / 2
    clear, r = .38, .42
    a.extrude([(f + .25, clear), (f, clear + .3), (f, 1.5), (f + .32, H - .12), (f + .7, H), (b - .1, H), (b, H - .12), (b, clear + .2), (b - .15, clear)],
              -W / 2, W / 2, col, axis='x')
    # windshield, the route board over it, lamps and a grille
    a.extrude([(f - .004, 1.4), (f + .3, H - .32), (f + .34, H - .32), (f + .03, 1.4)], -W / 2 + .12, W / 2 - .12, V_GLASS, axis='x')
    a.box(-.8, f + .3, H - .3, .8, f + .36, H - .1, E(0xFFC94A, 2.0))
    a.box(-W / 2 + .1, f - .02, .62, W / 2 - .1, f + .02, .82, DARK)
    for sx in (-1, 1):
        a.box(sx * (W / 2 - .32) - .18, f - .025, .9, sx * (W / 2 - .32) + .18, f + .02, 1.08, HEAD)
        a.box(sx * (W / 2 - .2) - .1, b - .02, 1.0, sx * (W / 2 - .2) + .1, b + .02, 1.5, TAIL)
        x0, x1 = sorted((sx * (W / 2 - .003), sx * (W / 2 + .012)))
        # the livery band along the side, the window strip over it
        a.box(x0, f + .3, .95, x1, b - .05, 1.3, band)
        a.box(x0, f + .6, 1.42, x1, b - .25, 2.32, V_GLASS)
        for k in range(1, 5):
            y = f + .6 + k * (L - .85) / 5
            a.box(x0 - .003, y - .035, 1.42, x1 + .003, y + .035, 2.32, col)
        a.box(sx * (W / 2) - (0 if sx > 0 else .1), f + .5, 1.6, sx * (W / 2) + (.1 if sx > 0 else 0), f + .58, 1.85, DARK)
    # the passenger door on the kerb side (+X), with a step
    a.box(W / 2 - .002, f + 1.55, clear + .1, W / 2 + .018, f + 2.6, 2.4, M(0xD9DEE4, "PAINT"))
    a.box(W / 2 + .01, f + 1.65, 1.3, W / 2 + .022, f + 2.5, 2.3, V_GLASS)
    a.box(W / 2 + .012, f + 2.06, clear + .1, W / 2 + .024, f + 2.09, 2.4, DARK)
    a.box(W / 2 - .05, f + 1.6, clear, W / 2 + .12, f + 2.55, clear + .06, GUN)
    a.box(-W / 2 + .2, b - .012, 1.5, W / 2 - .2, b + .012, 2.3, V_GLASS)
    a.box(-.9, f + 2.0, H, .9, f + 4.2, H + .22, M(0xD9DEE4, "PAINT"), bevel=.04)
    a.box(-W / 2 - .01, f - .04, clear, W / 2 + .01, f + .1, clear + .22, DARK, bevel=.02)
    a.box(-W / 2 - .01, b - .1, clear, W / 2 + .01, b + .04, clear + .22, DARK, bevel=.02)
    a.box(-.26, f - .05, clear + .04, .26, f - .04, clear + .17, PLATE)
    a.box(-.26, b + .04, clear + .04, .26, b + .05, clear + .17, PLATE)
    wheels(a, W, (f + 1.25, b - 1.35), r, w=.26)


@vehicle
def metro_car(a):
    """One car of an elevated train: four doors a side, the line's color in a band under the windows."""
    body = M(0xDCE1E6, "METAL")
    line = M(0x2F80ED, "PAINT")
    L, W = 20.0, 2.9
    hl = L / 2
    z0, z1 = 1.05, 3.75
    a.extrude([(-W / 2, z0), (-W / 2, z1 - .35), (-W / 2 + .3, z1), (W / 2 - .3, z1), (W / 2, z1 - .35), (W / 2, z0)], -hl, hl, body, axis='y')
    a.box(-W / 2 + .5, -hl + .6, z1, W / 2 - .5, hl - .6, z1 + .08, M(0xB8C0CA, "METAL"))
    for y in (-5.5, 0, 5.5):
        a.box(-.9, y - 1.5, z1 + .08, .9, y + 1.5, z1 + .34, M(0xC9CED6, "METAL"), bevel=.04)
    for sx in (-1, 1):
        x0, x1 = sorted((sx * (W / 2 - .002), sx * (W / 2 + .014)))
        a.box(x0, -hl, z0 + .55, x1, hl, z0 + 1.05, line)
        a.box(x0, -hl, z1 - .5, x1, hl, z1 - .4, line)
        pitch = L / 4
        for d in range(4):
            y = -hl + pitch * (d + .5)
            a.box(x0 - .004, y - .68, z0 + .05, x1 + .004, y + .68, z1 - .62, M(0x9DA4AA, "METAL"))
            a.box(x0 - .008, y - .012, z0 + .05, x1 + .008, y + .012, z1 - .62, DARK)
            for s2 in (-1, 1):
                a.box(x0 - .008, y + s2 * .34 - .22, z0 + 1.35, x1 + .008, y + s2 * .34 + .22, z0 + 2.3, V_GLASS)
            if d < 3:
                a.box(x0 - .004, y + .95, z0 + 1.3, x1 + .004, y + pitch - .95, z0 + 2.4, V_GLASS)
    # cab ends: a wide windscreen, lamps, the destination in lights
    for sy in (-1, 1):
        y0, y1 = sorted((sy * (hl - .002), sy * (hl + .014)))
        a.box(-W / 2 + .25, y0, z0 + 1.3, W / 2 - .25, y1, z0 + 2.35, V_GLASS)
        a.box(-.7, y0, z1 - .42, .7, y1, z1 - .18, E(0xFFC94A, 2.0))
        a.box(-W / 2, y0, z0 + .55, W / 2, y1, z0 + 1.05, line)
        for sx in (-1, 1):
            a.box(sx * 1.05 - .16, y0 - .004, z0 + .2, sx * 1.05 + .16, y1 + .004, z0 + .4, HEAD if sy < 0 else TAIL)
        a.box(-.5, sy * hl - .25, z0 - .3, .5, sy * hl + .3, z0 - .05, GUN)
    # underframe and two bogies, a pantograph on the roof
    a.box(-1.15, -hl + .8, z0 - .45, 1.15, hl - .8, z0, M(0x2E3238, "METAL"))
    for y in (-6.5, 6.5):
        a.box(-1.2, y - 1.5, .3, 1.2, y + 1.5, z0 - .35, GUN, bevel=.04)
        for dy in (-1.0, 1.0):
            for sx in (-1, 1):
                a.rod((sx * .72 - .07, y + dy, .43), (sx * .72 + .07, y + dy, .43), .43, IRON, n=14)
    for sx in (-1, 1):
        a.tube([(sx * .5, 2.0, z1 + .34), (sx * .3, 3.0, z1 + 1.0), (sx * .5, 4.0, z1 + 1.55)], .03, GUN, n=5)
    a.box(-.8, 3.9, z1 + 1.55, .8, 4.1, z1 + 1.6, IRON)
