"""Street furniture: what each kind of prop in render/StreetProps.kt looks like, and the traffic lights.

Each value is a list of parts (as in products.py), or Variants of several (the game picks one per prop). Props are in
meters in their own frame: origin where the prop stands, x along the street, +y toward the road, z up. Whatever glows
or changes (lamp lenses at night, a traffic light's lit lamp, the glass of a bus shelter and its lit advert, snow) is
drawn by the game on top, so the models stay the same day and night.
"""
from products import Variants, gen
import numpy as np

from shapes import _tris, box, cylinder, disc, lathe, prism, sphere, torus, tube

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
    """Three hoops in a row along the curb, the bikes locked to them standing alongside, on the building side."""
    s = None
    for k in range(3):
        x = (k - 1) * 1.1
        hoop = tube([(x - .35, 0, 0), (x - .35, 0, .62), (x - .28, 0, .78), (x - .15, 0, .84), (x + .15, 0, .84), (x + .28, 0, .78), (x + .35, 0, .62),
                     (x + .35, 0, 0)], .03, STEEL, n=6)
        s = hoop if s is None else s + hoop
    for x, color in bikes:
        s = s + bicycle(color).rotz(90).move(x, -.27, 0)
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


# ---------------------------------------------------------------- Manila and Tokyo, parks and courts

CART_LOOKS = [  # body, trim, umbrella, what's for sale (None: fishballs in a wok)
    ('#C9CED3', '#D32F2F', '#E74C3C', None),
    ('#2E7D4F', '#F2C230', '#2F80ED', '#F4D03F'),
    ('#B5652D', '#6B3E1F', '#F2C230', '#E67E22'),
]


def front(x0, z0, x1, z1, y, color):
    """A flat face looking out along +y (drinks behind a window, buttons): two triangles, for what's only seen from the front."""
    pts = np.array([[x0, y, z0], [x1, y, z0], [x1, y, z1], [x0, y, z0], [x1, y, z1], [x0, y, z1]], dtype=np.float64)
    return _tris(pts, np.tile([0, 1, 0], (6, 1)), color, '')


def food_cart(look):
    """A Manila street food cart on bicycle wheels: a wok of fishballs or a case of snacks on the counter, a striped
    umbrella over it, and a stool and a crate of supplies on the sidewalk side (the wheels are on the road side)."""
    body, trim, umbrella, food = CART_LOOKS[look]
    metal = look == 0
    s = box(-.8, -.4, .45, .8, .4, 1.05, body, mat='metal' if metal else ('wood' if look == 2 else ''), r=.03)
    s = s + box(-.83, -.43, 1.05, .83, .43, 1.1, trim, r=.012)
    s = s + box(-.74, -.34, .22, .74, .34, .26, IRON)
    for x in (-.74, .74):
        s = s + box(x - .025, -.36, 0, x + .025, -.3, .46, IRON)
    # the wheels and their axle, and a handle to push it by
    wheel = torus(.3, .022, '#26292E', nu=10, nv=3).rotx(90)
    for x in (-.55, .55):
        s = s + wheel.move(x, .46, .32) + cylinder(.04, -.02, .02, STEEL, n=6).rotx(90).move(x, .46, .32)
        for a in (0, 90):
            s = s + box(-.006, -.006, -.28, .006, .006, .28, STEEL).roty(a).move(x, .46, .32)
    s = s + box(-.56, .4, .3, .56, .44, .34, IRON)
    s = s + tube([(-.8, -.25, .95), (-1.05, -.25, 1.0), (-1.05, .25, 1.0), (-.8, .25, .95)], .018, IRON, n=4)
    if food is None:
        # a burner and a wok of oil with fishballs, the sauce jars beside it
        s = s + cylinder(.2, 1.1, 1.16, IRON, n=8)
        s = s + lathe([(.05, 1.16), (.28, 1.2), (.34, 1.3)], '#2A2D31', n=10, cap=False)
        s = s + disc(.3, 1.27, '#8A5A2C', n=10)
        for k in range(5):
            s = s + sphere(.04, .04, .032, '#E09A3E', nu=5, nv=3).move(-.12 + (k % 3) * .1, -.06 + (k // 3) * .12, 1.29)
        for k, col in enumerate(('#7A2E1F', '#C9A227', '#3E2A1E')):
            s = s + cylinder(.05, 1.1, 1.24, col, n=6).move(.42 + k * .12, .18, 0)
    else:
        # a glass case on the counter, snacks stacked inside on paper
        s = s + box(-.62, -.3, 1.1, .62, .3, 1.13, '#E8E4DA')
        for x in (-.6, .6):
            for y in (-.28, .28):
                s = s + box(x - .015, y - .015, 1.1, x + .015, y + .015, 1.5, '#DDE3E8', mat='metal')
        s = s + box(-.62, -.3, 1.48, .62, .3, 1.5, '#DDE3E8', mat='metal')
        for k in range(8):
            x = -.42 + (k % 4) * .28; y = -.12 + (k // 4) * .22
            s = s + box(x - .1, y - .06, 1.13, x + .1, y + .06, 1.2 + (k % 3) * .03, food)
    # the umbrella, its panels alternating with white, on a pole through the counter
    s = s + cylinder(.025, 1.1, 2.72, IRON, n=5)
    s = s + lathe([(1.36, 2.24), (1.3, 2.3), (0, 2.76)], umbrella, n=8, stripes=(umbrella, '#F5F5F0'), smooth=False)
    # for the customers: a stool; for the vendor: a crate of supplies
    s = s + cylinder(.17, .42, .46, '#D32F2F', n=8).move(.4, -.7, 0) + box(.37, -.73, 0, .43, -.67, .43, '#B02424')
    s = s + box(.28, -.82, 0, .52, -.58, .03, '#B02424')
    s = s + box(-1.32, -.42, 0, -.92, .02, .32, '#2F80ED', r=.015) + box(-1.29, -.39, .32, -.95, -.01, .33, '#1F5FB0')
    # bottles of soy sauce and vinegar in it
    s = s + box(-1.27, -.36, .32, -1.15, -.04, .5, '#7A3E1F') + box(-1.09, -.36, .32, -.97, -.04, .46, '#2E7D32')
    return s


VENDING_BODIES = ('#F2F2F0', '#D32F2F', '#1E5BC6', '#F2F2F0', '#2E9E4F', '#F2F2F0')
DRINKS = ('#E53935', '#1E88E5', '#FDD835', '#43A047', '#6D4C41', '#F5F5F5', '#FB8C00')


def vending_machine(body, seed):
    """One machine, its front (+y) to the road: drinks on three shelves in a recessed window, a strip of buttons under
    each shelf, the coin slot and the drop tray. The window's back is where the game lights it at night."""
    dark = '#22252A'
    s = box(-.49, -.38, 0, .49, .3, 1.86, body, mat='metal', r=.02)
    # the front around the window
    s = s + box(-.49, .3, 0, .49, .38, .93, body, mat='metal')
    s = s + box(-.49, .3, 1.77, .49, .38, 1.86, body, mat='metal')
    for x0, x1 in ((-.49, -.41), (.41, .49)):
        s = s + box(x0, .3, .93, x1, .38, 1.77, body, mat='metal')
    s = s + box(-.41, .3, .95, .41, .31, 1.75, '#DCE6EE')
    for row in range(3):
        z = 1.0 + row * .25
        s = s + front(-.41, z - .02, .41, z, .36, '#B9C2CA')
        for col in range(6):
            x = -.35 + col * .14
            drink = DRINKS[(row * 7 + col * 3 + seed) % len(DRINKS)]
            # cans short, bottles tall
            s = s + front(x - .03, z, x + .03, z + (.13 if (row + col + seed) % 3 == 0 else .17), .35, drink)
        s = s + front(-.39, z - .07, .39, z - .045, .382, '#F4F6F8')
    # the coin slot and note reader, the drop tray
    s = s + front(.18, .6, .38, .86, .382, '#3A3D42') + front(.24, .74, .32, .76, .384, '#C9CED3')
    s = s + front(-.34, .12, .3, .38, .382, dark)
    # the maker's band over the window
    s = s + front(-.41, 1.79, .41, 1.84, .382, '#FFFFFF' if body != '#F2F2F0' else '#D32F2F')
    return s


def vending_row(n, look):
    """[n] machines side by side (and a recycling bin beside a pair)."""
    s = None
    for m in range(n):
        x = (m - n * .5) * 1.02 + .49
        one = vending_machine(VENDING_BODIES[(look + m) % len(VENDING_BODIES)], look + m).move(x, 0, 0)
        s = one if s is None else s + one
    if n == 2:
        bx = n * .51 + .3
        s = s + box(bx - .22, -.2, 0, bx + .22, .2, .74, '#2F80ED', r=.03) + box(bx - .23, -.21, .74, bx + .23, .21, .79, '#1F5FB0', r=.02)
        s = s + cylinder(.07, .79, .795, '#1C1F22', n=8).move(bx, 0, 0)
    return s


def utility_pole(look):
    """A Manila concrete power pole: a cross-arm with porcelain insulators for the lines, climbing rungs, a street
    light reaching over the road, and on some a transformer or the cable companies' junction boxes. The lines are the
    game's (they run to the next pole and the houses)."""
    concrete = '#A8A49B'
    s = box(-.2, -.2, 0, .2, .2, 1.0, concrete, r=.02)
    s = s + lathe([(.16, 1.0), (.115, 8.6), (.1, 8.66)], concrete, n=8)
    for k in range(6):
        z = 2.6 + k * .8
        a = 90 if k % 2 else -90
        s = s + box(0, -.02, -.02, .26, .02, .02, IRON).rotz(a + 90).move(0, 0, z)
    s = s + box(-.08, -.75, 7.9, .08, .75, 8.08, '#5A4A3A', mat='wood')
    s = s + tube([(0, -.6, 7.92), (0, -.1, 7.4)], .02, IRON, n=4) + tube([(0, .6, 7.92), (0, .1, 7.4)], .02, IRON, n=4)
    for y in (-.65, -.22, .22, .65):
        s = s + lathe([(.035, 0), (.07, .04), (.04, .08), (.065, .11), (.025, .15)], '#E8E4DA', n=5).move(0, y, 8.08)
    # the street light: an arm over the road and a head (its lens is the game's, lit at night)
    s = s + tube([(0, .1, 6.9), (0, .8, 7.1), (0, 1.62, 7.02)], .04, IRON, n=5)
    s = s + box(-.28, 1.35, 6.77, .28, 1.95, 6.98, '#5B6168', mat='metal', r=.04)
    if look % 3 == 0:
        # a transformer drum on its bracket
        s = s + box(-.06, -.18, 6.0, .06, -.1, 6.95, IRON)
        s = s + cylinder(.3, 6.05, 6.9, '#7D858C', n=10, top='#5E666D').move(0, -.42, 0)
        for x in (-.12, .12):
            s = s + lathe([(.03, 0), (.05, .03), (.03, .06), (.05, .09), (.02, .12)], '#5E5A52', n=5).move(x, -.42, 6.9)
    if look % 2 == 0:
        s = s + box(-.17, -.36, 4.7, .17, -.15, 5.25, '#8D9399', mat='metal', r=.02)
        for z in (4.8, 5.15):
            s = s + box(-.2, -.2, z, .2, .2, z + .03, IRON)
    if look % 3 == 1:
        s = s + box(-.15, .15, 5.05, .15, .34, 5.45, '#55606A', mat='metal', r=.02)
        s = s + box(-.18, -.18, 5.2, .18, .18, 5.23, IRON)
    return s


def hoop(look):
    """A basketball hoop on a post by the lane: a backboard with its painted square, an orange rim over the street and
    a net."""
    post = '#6B4E33' if look % 2 == 0 else '#5E666D'
    board = '#D9C9A3' if look % 3 == 0 else '#F2F2EE'
    paint = '#C0392B'
    orange = '#E8622A'
    s = box(-.08, -.08, 0, .08, .08, 3.95, post, mat='wood' if look % 2 == 0 else 'metal')
    s = s + box(-.05, 0, 3.55, .05, .82, 3.65, post) + tube([(0, .05, 3.0), (0, .6, 3.58)], .03, post, n=4)
    s = s + box(-.62, .78, 2.85, .62, .84, 3.8, board, mat='wood' if look % 3 == 0 else '', r=.01)
    # the edge stripe and the square over the rim
    for x0, z0, x1, z1 in ((-.62, 3.76, .62, 3.8), (-.62, 2.85, .62, 2.89), (-.62, 2.85, -.58, 3.8), (.58, 2.85, .62, 3.8),
                           (-.25, 3.1, .25, 3.13), (-.25, 3.45, .25, 3.48), (-.25, 3.1, -.22, 3.48), (.22, 3.1, .25, 3.48)):
        s = s + box(x0, .84, z0, x1, .846, z1, paint)
    s = s + box(-.1, .84, 2.98, .1, .92, 3.06, orange)
    s = s + torus(.23, .018, orange, nu=14, nv=4).move(0, 1.08, 3.05)
    s = s + lathe([(.225, 3.04), (.17, 2.84), (.13, 2.64)], '#E9E6DD', n=12, stripes=('#E9E6DD', '#BDB9AE'), cap=False, smooth=False).move(0, 1.08, 0)
    return s


def fountain():
    """A park fountain in stone: a wide basin with a lip, a pedestal and a bowl with a spout on top. The water, its
    ripples and the jet are the game's (they move, and freeze in the cold)."""
    stone = '#CFC6B6'
    s = lathe([(3.36, 0), (3.4, .06), (3.33, .1), (3.33, .48), (3.4, .52), (3.4, .58), (3.04, .6), (3.04, .42), (0, .42)], stone, n=28, smooth=False)
    s = s + lathe([(.62, .42), (.5, .6), (.42, 1.25), (.52, 1.45), (.4, 1.62)], stone, n=12)
    s = s + lathe([(.4, 1.6), (1.2, 1.78), (1.36, 1.9), (1.36, 1.98), (1.22, 1.98), (1.22, 1.9)], stone, n=20, smooth=False)
    s = s + lathe([(.24, 1.9), (.18, 2.25), (.26, 2.38), (.12, 2.52), (.07, 2.62), (0, 2.66)], stone, n=10)
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
    'bike_rack': Variants([[gen(bike_rack([]))], [gen(bike_rack([(-.55, '#C0392B')]))], [gen(bike_rack([(-.55, '#2F80ED'), (.55, '#27AE60')]))],
                           [gen(bike_rack([(.55, '#26292E')]))]]),
    'planter': [gen(planter())],
    'traffic_light': [gen(traffic_light())],
    # looks picked as the game picks them: a cart's by i % 3, the rest by i % 6
    'food_cart': Variants([[gen(food_cart(k))] for k in range(len(CART_LOOKS))]),
    'vending': Variants([[gen(vending_row(2 + k % 2, k))] for k in range(6)]),
    'utility_pole': Variants([[gen(utility_pole(k))] for k in range(6)]),
    'hoop': Variants([[gen(hoop(k))] for k in range(6)]),
    'fountain': [gen(fountain())],
}
