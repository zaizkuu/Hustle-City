"""Street furniture: what each kind of prop in render/StreetProps.kt looks like, and the traffic lights.

Each value is a list of parts (as in products.py), or Variants of several (the game picks one per prop). Props are in
meters in their own frame: origin where the prop stands, x along the street, +y toward the road, z up. Whatever glows
or changes (lamp lenses at night, a traffic light's lit lamp, the glass of a bus shelter and its lit advert, snow) is
drawn by the game on top, so the models stay the same day and night.
"""
from products import Variants, gen
from shapes import box, cylinder, disc, lathe, prism, sphere, torus, tube

POLE = '#2F3A35'
IRON = '#2A2D31'
STEEL = '#9AA3AD'
WOOD = '#9C6B43'
CONCRETE = '#B9B4AA'
WHITE = '#F5F5F5'
BLACK = '#1C1F22'
NEWS_COLORS = ('#D63031', '#0984E3', '#FDCB6E', '#00B894')


def street_lamp():
    """A cobra-head street light: a fluted base, a tapering pole and an arm curving out over the road."""
    s = lathe([(.2, 0), (.2, .45), (.12, .6), (.1, .7)], POLE, n=6)
    s = s + lathe([(.1, .7), (.065, 5.45), (.05, 5.6)], POLE, n=6, cap=False)
    s = s + tube([(0, 0, 5.5), (0, .3, 5.95), (0, .8, 6.08), (0, 1.3, 6.1)], .045, POLE, n=4)
    # the head; its lens (lit at night) is drawn by the game underneath
    s = s + box(-.22, 1.25, 5.92, .22, 1.95, 6.1, '#5B6168', mat='metal')
    return s


def park_lamp():
    s = lathe([(.17, 0), (.17, .14), (.08, .3), (.06, .6), (.055, 3.25), (.085, 3.33), (.085, 3.45), (.05, 3.56)], IRON, n=6)
    # a little cup the globe sits in (the globe itself is drawn by the game)
    return s + lathe([(.05, 3.52), (.11, 3.62)], IRON, n=6, cap=False, smooth=False)


def hydrant():
    red = '#D63031'
    cap = '#C9CED4'
    s = lathe([(.17, 0), (.17, .05), (.125, .08), (.125, .44), (.15, .46), (.15, .51), (.1, .62), (.045, .7)], red, n=8)
    side = cylinder(.052, 0, .1, red, n=6).roty(90).move(.11, 0, .36) + disc(.06, 0, cap, n=6).roty(90).move(.21, 0, .36)
    s = s + side + side.rotz(180)
    # the big pumper outlet faces the road
    s = s + cylinder(.07, 0, .1, red, n=6).rotx(-90).move(0, .11, .32) + disc(.075, 0, cap, n=6).rotx(-90).move(0, .212, .32)
    return s


def litter_bin():
    green = '#2E5E46'
    s = lathe([(.26, 0), (.3, .78)], green, n=10, stripes=(green, '#28523D'), smooth=False, cap=False)
    s = s + lathe([(.3, .76), (.33, .78), (.33, .88), (.27, .88)], '#3F7A5C', n=10)
    s = s + disc(.27, .86, BLACK, n=10)
    return s


def bench(slat=WOOD):
    """A park bench: wooden slats on cast iron ends, its back to the buildings and its seat toward the road."""
    s = None
    for k in range(4):
        y = -.17 + k * .115
        b = box(-.85, y - .045, .43, .85, y + .045, .47, slat, mat='wood')
        s = b if s is None else s + b
    for k in range(3):
        b = box(-.85, -.035, 0, .85, .035, .085, slat, mat='wood').rotx(14).move(0, -.27 - k * .03, .55 + k * .12)
        s = s + b
    end = prism([(-.26, 0), (-.2, 0), (-.16, .4), (.16, .4), (.2, 0), (.26, 0), (.23, .46), (-.22, .46), (-.32, .95), (-.38, .95), (-.28, .46)],
                -.03, .03, IRON)
    end = end.turn([[0, 0, 1], [1, 0, 0], [0, 1, 0]])
    s = s + end.move(-.72, 0, 0) + end.move(.72, 0, 0)
    return s


def bus_shelter():
    frame = '#5D6670'
    s = None
    for x in (-2.0, 2.0):
        for y in (-.72, .62):
            p = box(x - .04, y - .04, 0, x + .04, y + .04, 2.38, frame, mat='metal')
            s = p if s is None else s + p
    # rails along the back, the roof with a lip, and the frame the advert sits in
    s = s + box(-2.0, -.74, .08, 2.0, -.7, .12, frame, mat='metal') + box(-2.0, -.74, 2.28, 2.0, -.7, 2.32, frame, mat='metal')
    s = s + box(-2.18, -.92, 2.35, 2.18, .82, 2.47, '#4E565E', mat='metal', r=.03)
    s = s + box(-2.2, .8, 2.3, 2.2, .84, 2.5, '#C0392B')
    s = s + box(-.86, -.76, .26, .86, -.68, 2.14, frame, mat='metal')
    # the bench inside
    s = s + box(-1.1, -.64, .44, 1.1, -.3, .5, '#8C939B', mat='metal', r=.01)
    for x in (-.9, .9):
        s = s + box(x - .03, -.6, 0, x + .03, -.34, .44, frame)
    # the stop's sign on a pole by the curb
    s = s + cylinder(.045, 0, 2.95, IRON, n=6).move(2.6, .6, 0)
    sign = box(-.28, -.02, 2.4, .28, .02, 2.95, '#1F6FD1', r=.02) + box(-.14, .02, 2.55, .14, .026, 2.8, WHITE)
    s = s + sign.move(2.6, .6, 0)
    return s


def news_box(color):
    s = box(-.17, -.17, 0, .17, .17, .24, '#3A3F45')
    s = s + box(-.22, -.22, .24, .22, .22, .95, color, mat='metal')
    s = s + box(-.17, .222, .5, .17, .226, .86, '#E8ECEF')
    s = s + box(-.06, .1, .95, .06, .2, 1.0, '#3A3F45')
    return s


def news_boxes(k):
    return [gen(news_box(NEWS_COLORS[(k + j) % len(NEWS_COLORS)]), (j - 1) * .55) for j in range(3)]


def mailbox():
    blue = '#1F4E9C'
    s = box(-.28, -.25, .12, .28, .25, 1.06, blue, mat='metal', r=.1)
    for x in (-.2, .2):
        for y in (-.19, .19):
            s = s + box(x - .025, y - .025, 0, x + .025, y + .025, .14, IRON)
    s = s + box(-.17, .25, .74, .17, .27, .86, '#173B76') + box(-.12, .27, .78, .12, .28, .8, STEEL)
    s = s + box(-.07, .251, .4, .07, .256, .55, WHITE)
    return s


def bicycle(color):
    """A bike standing along y: wheels, a diamond frame, saddle and handlebar."""
    wheel = torus(.33, .018, '#26292E', nu=14, nv=3).roty(90)
    s = wheel.move(0, -.52, .35) + wheel.move(0, .52, .35)
    s = s + tube([(0, -.52, .35), (0, -.12, .37), (0, -.18, .78), (0, -.52, .35)], .02, color, n=4)
    s = s + tube([(0, -.12, .37), (0, .4, .78), (0, -.18, .78)], .02, color, n=4)
    s = s + tube([(0, .4, .78), (0, .52, .35)], .018, STEEL, n=4)
    s = s + tube([(0, .4, .78), (0, .38, .92), (-.24, .38, .94)], .015, IRON, n=4) + tube([(0, .38, .92), (.24, .38, .94)], .015, IRON, n=4)
    s = s + box(-.05, -.3, .84, .05, -.12, .88, BLACK, r=.015)
    s = s + tube([(0, -.18, .78), (0, -.22, .84)], .015, STEEL, n=4)
    return s


def bike_rack(bikes):
    s = None
    for k in range(4):
        x = (k - 1.5) * .75
        hoop = tube([(x, -.35, 0), (x, -.35, .62), (x, -.28, .78), (x, -.15, .84), (x, .15, .84), (x, .28, .78), (x, .35, .62), (x, .35, 0)], .03, STEEL, n=6)
        s = hoop if s is None else s + hoop
    for k, color in bikes:
        s = s + bicycle(color).move((k - 1.5) * .75 + .2, .05, 0)
    return s


def planter():
    s = box(-.55, -.55, 0, .55, .55, .6, CONCRETE, r=.04)
    return s + box(-.47, -.47, .6, .47, .47, .62, '#5A4030')


def traffic_light():
    """A mast arm signal: the pole on the corner, the arm reaching out along +y over the lanes, the signal facing +x."""
    pole = '#3A3D42'
    s = lathe([(.16, 0), (.16, .12), (.1, .3), (.085, 4.5), (.07, 4.62)], pole, n=6)
    s = s + box(-.05, 0, 4.28, .05, 2.75, 4.4, pole, mat='metal')
    s = s + tube([(0, 0, 3.7), (0, 1.1, 4.3)], .03, pole, n=4)
    head = box(-.13, -.17, 3.42, .13, .17, 4.38, '#22252A', mat='metal')
    head = head + box(-.14, -.3, 3.38, -.11, .3, 4.42, BLACK)
    for z in (3.62, 3.9, 4.18):
        head = head + disc(.09, 0, '#14161A', n=8).roty(90).move(.131, 0, z)
        head = head + lathe([(.105, 0), (.105, .12)], '#22252A', n=4, arc=180, phase=90, cap=False, smooth=False).roty(90).move(.13, 0, z)
    s = s + head.move(0, 2.62, 0)
    # a walk signal on the pole for the crossing
    s = s + box(.09, -.12, 2.45, .29, .12, 2.75, '#22252A', mat='metal')
    return s


PROPS = {
    'lamp': [gen(street_lamp())],
    'park_lamp': [gen(park_lamp())],
    'hydrant': [gen(hydrant())],
    'bin': [gen(litter_bin())],
    'bench': [gen(bench())],
    'bus_stop': [gen(bus_shelter())],
    'news': Variants([news_boxes(k) for k in range(len(NEWS_COLORS))]),
    'mailbox': [gen(mailbox())],
    'bike_rack': Variants([[gen(bike_rack([]))], [gen(bike_rack([(1, '#C0392B')]))], [gen(bike_rack([(0, '#2F80ED'), (2, '#27AE60')]))],
                           [gen(bike_rack([(3, '#26292E')]))]]),
    'planter': [gen(planter())],
    'traffic_light': [gen(traffic_light())],
}
