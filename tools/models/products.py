"""What each product looks like on a display.

Keys are product ids from data/Catalog.kt (services have no model). A value is a list of parts, or Variants of several
such lists (neighboring slots on a display take turns, so a clothing rail or a bookshelf isn't all one color). A part is
either a model file fitted into a box (kit(): the box is in meters) or a shape from shapes.py placed as modelled (gen()).

Products are modelled at their real size, centered on x = y = 0 and standing on z = 0, their front toward +y (the
customer). One model is what a slot of a display shows, so small things come in rows (six cans, a pile of apples).
The game only ever shrinks a model to fit its slot. Clothes hang: the hanger's hook is at z = 0, the garment below it.
"""
import math
import random

from shapes import box, cylinder, disc, gem, lathe, prism, sphere, torus, tube

F = 'kenney-food-kit/'
FURN = 'kenney-furniture-kit/'

SILVER = '#C9D1D9'
WHITE = '#F5F5F5'
BLACK = '#26292E'
GOLD = '#E8C04A'
KRAFT = '#C8A165'
CREAM = '#EFE6D2'
VELVET = '#5C1F2E'
GREEN = '#3E8E41'

# x-y-z of a flat shape drawn in its own (width, height, thickness) to (thickness, width, height): a garment on a rail
UPRIGHT_SIDEWAYS = [[0, 0, 1], [1, 0, 0], [0, 1, 0]]
# (width, height, thickness) to (width, thickness, height): a flat shape standing up facing the customer
UPRIGHT_FRONT = [[1, 0, 0], [0, 0, -1], [0, 1, 0]]


class Variants(list):
    """Several looks for one product: slot k of a display shows look k mod n."""


def kit(src, x=0.0, y=0.0, z=0.0, w=.1, d=.1, h=None, **kw):
    """A model file fitted (keeping its proportions unless stretch=True) into a box centered on (x, y) from z up."""
    return dict(src=src, box=(x - w / 2, y - d / 2, z, x + w / 2, y + d / 2, None if h is None else z + h), **kw)


def gen(shape, x=0.0, y=0.0, z=0.0):
    return dict(gen=shape, at=(x, y, z))


def grid(shape, cols, rows, dx, dy, layers=1, dz=0.0):
    """Copies of [shape] in rows (across x, deep along y), centered."""
    out = []
    for k in range(layers):
        for i in range(cols):
            for j in range(rows):
                out.append(gen(shape, (i - (cols - 1) / 2) * dx, (j - (rows - 1) / 2) * dy, k * dz))
    return out


# ------------------------------------------------------------------------------------------------ drinks


def can(body, band=None, r=.033, h=.122, n=8):
    """A drinks can: silver top and bottom, the body in [body], a stripe of [band] round its middle."""
    prof = [(r * .8, 0), (r, .01), (r, h - .014), (r * .82, h - .003), (r * .82, h)]
    s = lathe(prof, body, n=n, colors=[SILVER, body, SILVER, SILVER], mat='metal')
    if band:
        s = s + lathe([(r * 1.01, h * .42), (r * 1.01, h * .6)], band, n=n, mat='metal', smooth=False, cap=False)
    return s


def bottle(glass, label, top, r=.031, h=.235, neck=.012, shoulder=.6, label_band=(.22, .5), top_from=.9, n=8):
    """A glass bottle with a paper label round it and a cap or foil ([top]) from [top_from] of its height up."""
    prof = [(r * .9, 0), (r, .008), (r, h * shoulder), (neck * 1.25, h * .8), (neck, h * .86), (neck, h * top_from),
            (neck, h * top_from), (neck * 1.12, h * top_from + .002), (neck * 1.12, h)]
    colors = [glass, glass, glass, glass, glass, top, top, top]
    s = lathe(prof, glass, n=n, colors=colors, mat='metal')
    a, b = label_band
    s = s + lathe([(r * 1.01, h * a), (r * 1.01, h * b)], label, n=n, smooth=False, cap=False)
    return s


def wine_bottle(capsule):
    return bottle('#2E1C22', CREAM, capsule, r=.037, h=.3, neck=.0135, shoulder=.62, label_band=(.2, .45), top_from=.82)


def beer_bottle(label):
    return bottle('#5A3212', label, GOLD, r=.031, h=.23, neck=.012, shoulder=.55, label_band=(.18, .42), top_from=.95)


def whiskey_bottle(liquor):
    s = box(-.04, -.028, 0, .04, .028, .17, liquor, mat='metal', r=.008)
    s = s + lathe([(.03, .17), (.016, .19), (.014, .2)], liquor, n=8, mat='metal', cap=False)
    s = s + cylinder(.017, .2, .245, BLACK, n=8)
    # a cream label with a black band on the front
    s = s + box(-.03, .0285, .05, .03, .031, .13, CREAM) + box(-.03, .029, .08, .03, .0315, .095, BLACK)
    return s


def shaker(body, lid):
    s = lathe([(.033, 0), (.036, .01), (.036, .14)], body, n=10)
    s = s + lathe([(.0365, .14), (.0365, .172), (.022, .188), (.012, .198), (0, .198)], lid, n=10)
    s = s + lathe([(.0365, .05), (.0365, .1)], '#5D4037', n=10, smooth=False, cap=False)
    return s


def togo_cup(sleeve, tag=None):
    """A takeaway cup with a lid and a cardboard sleeve (and a tea bag's tag hanging out)."""
    s = lathe([(.028, 0), (.031, .01), (.04, .1)], WHITE, n=12)
    s = s + lathe([(.0345, .03), (.0375, .075)], sleeve, n=12, smooth=False, cap=False)
    s = s + lathe([(.042, .1), (.042, .107), (.036, .112), (.034, .118), (0, .118)], '#EDEDED', n=12)
    if tag:
        s = s + tube([(.02, .0, .118), (.034, .02, .1), (.037, .024, .07)], .0012, WHITE, n=4)
        s = s + box(.032, .024, .045, .046, .027, .066, tag)
    return s


def pint():
    s = lathe([(.031, 0), (.035, .006), (.042, .15)], '#F2B33A', n=12, mat='metal')
    return s + lathe([(.042, .15), (.0435, .163), (.03, .171), (0, .173)], '#FFF8E7', n=12)


def martini(drink):
    s = lathe([(.035, 0), (.035, .004), (.005, .008), (.004, .09)], '#DCEFF5', n=10, mat='glass', cap=False)
    s = s + lathe([(.004, .09), (.058, .155)], '#DCEFF5', n=12, mat='glass', cap=False)
    s = s + lathe([(0, .1), (.048, .143), (0, .143)], drink, n=12, mat='metal')
    # a lime wheel on the rim
    s = s + cylinder(.016, -.002, .002, '#8BC34A', n=8).rotx(90).move(.05, 0, .15)
    return s


def slushie(ice):
    s = lathe([(.03, 0), (.042, .13)], ice, n=12, mat='metal')
    s = s + lathe([(.0425, .095), (.0435, .125)], WHITE, n=12, smooth=False, cap=False)
    s = s + lathe([(.044, .13), (.043, .143), (.026, .165), (.009, .17), (0, .17)], '#E8F6FB', n=12, mat='glass')
    s = s + cylinder(.004, 0, .1, '#EB5757', n=6).roty(8).move(.006, 0, .145)
    return s


# ------------------------------------------------------------------------------------------------ groceries


def pile(src, size, h, cols, rows, top, seed, **kw):
    """Fruit and vegetables heaped up: a layer of cols x rows and [top] more on top, each turned at random."""
    rnd = random.Random(seed)
    parts = []
    sp = size * .98
    for i in range(cols):
        for j in range(rows):
            x = (i - (cols - 1) / 2) * sp + rnd.uniform(-.004, .004)
            y = (j - (rows - 1) / 2) * sp + rnd.uniform(-.004, .004)
            parts.append(kit(src, x, y, 0, size, size, h, rot=[('z', rnd.uniform(0, 360))], **kw))
    for k in range(top):
        x = ((k % max(cols - 1, 1)) - (cols - 2) / 2) * sp if cols > 1 else 0
        y = ((k // max(cols - 1, 1)) - (rows - 2) / 2) * sp if rows > 1 else 0
        parts.append(kit(src, x, y, h * .55, size, size, h, rot=[('z', rnd.uniform(0, 360))], **kw))
    return parts


def bananas():
    parts = []
    for by, seed in ((-.085, 1), (.085, 2)):
        for i, x in enumerate((-.054, -.018, .018, .054)):
            parts.append(kit(F + 'banana.glb', x, by, .004 * (i % 2), .045, .19, .055, rot=[('z', (i - 1.5) * 6)]))
    return parts


def carrots():
    parts = []
    for i in range(5):
        parts.append(kit(F + 'carrot.glb', (i - 2) * .034, 0, 0, .09, .26, .09, rot=[('x', 90), ('z', (i - 2) * 3)]))
    for i in range(3):
        parts.append(kit(F + 'carrot.glb', (i - 1) * .034, .015, .022, .09, .26, .09, rot=[('x', 90), ('z', (1 - i) * 4)]))
    return parts


def snack_box(wrapper, stripe):
    """A counter display box of candy bars standing in rows."""
    s = box(-.1, -.05, 0, .1, .05, .012, wrapper)
    s = s + box(-.1, .045, 0, .1, .05, .03, wrapper) + box(-.1, -.05, 0, .1, -.045, .08, wrapper)
    s = s + box(-.1, -.045, .045, .1, -.0445, .07, stripe) + box(-.1, -.05, 0, -.095, .05, .03, wrapper) + box(.095, -.05, 0, .1, .05, .03, wrapper)
    for j in range(3):
        for i in range(2):
            x = (i - .5) * .092
            y = -.025 + j * .026
            s = s + box(x - .043, y - .008, .012, x + .043, y + .008, .062, wrapper)
            s = s + box(x - .043, y + .008, .03, x + .043, y + .0085, .045, stripe)
    return s


def toilet_rolls():
    roll = lathe([(.05, 0), (.054, .004), (.054, .101), (.05, .105)], WHITE, n=10)
    roll = roll + disc(.017, .1055, '#B08B5A', n=6)
    s = None
    for k in range(2):
        for i in range(2):
            for j in range(2):
                r = roll.move((i - .5) * .11, (j - .5) * .11, k * .106)
                s = r if s is None else s + r
    # the pack's printed wrap
    s = s + box(-.1, .109, .03, .1, .111, .18, '#8EC5E8') + box(-.06, .1095, .08, .06, .1115, .13, WHITE)
    return s


def frozen_boxes(color):
    s = None
    for k in range(3):
        dx = (k % 2) * .006 - .003
        b = box(-.11 + dx, -.085, k * .037, .11 + dx, .085, k * .037 + .035, color, r=.003)
        s = b if s is None else s + b
    top = 3 * .037
    # the picture on the top box: a pizza on a white panel
    s = s + box(-.08, -.06, top - .002, .08, .06, top + .0005, WHITE)
    s = s + cylinder(.045, top, top + .002, '#E9A13B', n=10) + cylinder(.036, top + .001, top + .003, '#C0392B', n=10)
    return s


def hair_products(color):
    pump = lathe([(.026, 0), (.028, .006), (.028, .12), (.012, .135), (.009, .142)], color, n=10, mat='metal')
    pump = pump + cylinder(.006, .142, .16, WHITE, n=6) + box(-.003, -.002, .155, .022, .004, .162, WHITE)
    jar = lathe([(.034, 0), (.035, .004), (.035, .045)], WHITE, n=10) + cylinder(.036, .045, .06, color, n=10)
    return [gen(pump, -.05, -.045), gen(pump, .05, -.045), gen(jar, -.048, .05), gen(jar, .048, .05)]


# ------------------------------------------------------------------------------------------------ gifts and goods


def gift(w, d, h, paper, ribbon, bow=True):
    s = box(-w / 2, -d / 2, 0, w / 2, d / 2, h, paper)
    rw = min(w, d) * .14
    s = s + box(-w / 2 - .001, -rw / 2, -.001, w / 2 + .001, rw / 2, h + .0015, ribbon)
    s = s + box(-rw / 2, -d / 2 - .001, -.001, rw / 2, d / 2 + .001, h + .0015, ribbon)
    if bow:
        loop = torus(.016, .0045, ribbon, nu=10, nv=4).rotx(90)
        s = s + loop.rotz(35).move(-.012, 0, h + .012) + loop.rotz(-35).move(.012, 0, h + .012)
    return s


def hat_box(paper, band):
    s = lathe([(.055, 0), (.055, .07)], paper, n=14, smooth=False)
    s = s + lathe([(.058, .06), (.058, .082)], band, n=14, smooth=False)
    return s


def umbrella(color):
    # closed: a long cone of cloth, the tip at the bottom, then the shaft and a J handle (modelled standing, laid down)
    s = cylinder(.004, 0, .05, SILVER, n=6)
    s = s + lathe([(0, .02), (.024, .14), (.03, .3), (.018, .5), (.006, .54)], color, n=10, mat='cloth')
    s = s + box(-.026, -.004, .3, .026, .004, .33, BLACK)  # the strap
    s = s + cylinder(.005, .54, .58, SILVER, n=6)
    s = s + tube([(0, 0, .58), (0, 0, .64), (0, -.012, .66), (0, -.03, .662), (0, -.044, .65), (0, -.046, .632)], .007, '#3A2A20', n=6)
    # lying down along y, the handle toward the customer with its hook curling up
    return s.rotx(-90).move(0, -.331, .03)


def books(colors, w, d, h, seed, bands=None):
    """A row of books standing with their spines to the front, filling [w] across."""
    rnd = random.Random(seed)
    s = None
    x = -w / 2
    k = 0
    while x < w / 2 - .012:
        t = rnd.uniform(.022, .034) if bands else rnd.uniform(.016, .026)
        hh = h * rnd.uniform(.84, 1.0)
        dd = d * rnd.uniform(.9, 1.0)
        c = colors[k % len(colors)]
        b = box(x, -dd / 2, 0, x + t - .002, dd / 2, hh, c)
        # page edges show on top
        b = b + box(x + .002, -dd / 2 + .004, hh, x + t - .004, dd / 2 - .006, hh + .001, '#F4EEDF')
        if bands:
            b = b + box(x, dd / 2, hh * .12, x + t - .002, dd / 2 + .001, hh * .16, bands)
            b = b + box(x, dd / 2, hh * .78, x + t - .002, dd / 2 + .001, hh * .84, bands)
        else:
            b = b + box(x + .003, dd / 2, hh * .55, x + t - .005, dd / 2 + .001, hh * .8, WHITE)
        s = b if s is None else s + b
        x += t
        k += 1
    return s


def comics(colors, seed):
    rnd = random.Random(seed)
    s = None
    for k in range(5):
        a = rnd.uniform(-8, 8)
        c = colors[k % len(colors)]
        b = box(-.085, -.13, 0, .085, .13, .006, c) + box(-.07, .06, .006, .07, .115, .0068, WHITE) + box(-.06, -.1, .006, .06, .04, .0068, colors[(k + 1) % len(colors)])
        b = b.rotz(a).move(rnd.uniform(-.006, .006), rnd.uniform(-.006, .006), k * .0072)
        s = b if s is None else s + b
    return s


def magazine(cover, accent):
    s = box(-.105, -.006, 0, .105, .006, .27, cover)
    s = s + box(-.095, .006, .22, .095, .0068, .262, WHITE)
    s = s + box(-.075, .006, .05, .075, .0068, .19, accent)
    s = s + box(-.095, .006, .02, .02, .0068, .035, WHITE)
    return s


# ------------------------------------------------------------------------------------------------ clothes and shoes


def hanger():
    s = tube([(0, -.17, -.105), (0, 0, -.06), (0, .17, -.105)], .003, '#8D6E63', n=4)
    s = s + tube([(0, 0, -.06), (0, 0, -.03), (0, .006, -.012), (0, .018, -.002), (0, .03, -.006), (0, .034, -.018)], .0028, SILVER, n=4)
    return s


def tee(color, print_color=None):
    pts = [(0, -.035), (.06, 0), (.17, -.03), (.215, -.16), (.155, -.185), (.145, -.6), (-.145, -.6), (-.155, -.185), (-.215, -.16), (-.17, -.03), (-.06, 0)]
    s = prism(pts, -.016, .016, color, mat='cloth')
    s = s + prism([(-.06, 0), (0, -.035), (.06, 0), (0, -.045)], -.017, .017, _darker(color), mat='cloth')
    if print_color:
        s = s + box(-.075, -.32, .016, .075, -.2, .0175, print_color) + box(-.075, -.32, -.0175, .075, -.2, -.016, print_color)
    return s.turn(UPRIGHT_SIDEWAYS).move(0, 0, -.06) + hanger()


def blazer(color, lapel):
    pts = [(0, -.2), (.07, 0), (.19, -.025), (.225, -.08), (.22, -.62), (.16, -.64), (.155, -.235), (.15, -.76), (-.15, -.76), (-.155, -.235), (-.16, -.64), (-.22, -.62), (-.225, -.08), (-.19, -.025), (-.07, 0)]
    s = prism(pts, -.02, .02, color, mat='cloth')
    for z0, z1 in ((.02, .022), (-.022, -.02)):
        s = s + prism([(0, -.2), (.07, 0), (.1, -.02), (.035, -.24)], z0, z1, lapel, mat='cloth')
        s = s + prism([(0, -.2), (-.035, -.24), (-.1, -.02), (-.07, 0)], z0, z1, lapel, mat='cloth')
        for by in (-.36, -.46):
            s = s + box(-.008, by - .008, min(z0, z1) - .002, .008, by + .008, max(z0, z1) + .002, '#1A1A1A')
    # a white shirt collar showing at the neck
    s = s + prism([(-.035, -.012), (0, -.07), (.035, -.012), (0, -.03)], -.021, .021, WHITE, mat='cloth')
    return s.turn(UPRIGHT_SIDEWAYS).move(0, 0, -.06) + hanger()


def _darker(c):
    v = int(c.lstrip('#'), 16)
    r, g, b = (v >> 16 & 255) * .8, (v >> 8 & 255) * .8, (v & 255) * .8
    return '#%02X%02X%02X' % (int(r), int(g), int(b))


def sneaker(upper, accent, sole=WHITE):
    """One shoe, its toe toward +x, seen side on."""
    outline = [(-.135, 0), (-.125, .034), (-.085, .043), (.02, .039), (.09, .047), (.128, .032), (.14, 0), (.128, -.032), (.09, -.046), (.02, -.037), (-.085, -.041), (-.125, -.033)]
    s = prism(outline, 0, .026, sole, side='#E6E6E6')
    s = s + box(-.13, -.04, .024, .05, .04, .095, upper, mat='cloth', r=.02)
    s = s + sphere(.075, .042, .036, upper, nu=10, nv=5, mat='cloth').move(.06, 0, .04)
    s = s + box(-.12, -.03, .09, -.035, .03, .099, BLACK, r=.006)  # the opening
    s = s + box(-.04, -.022, .08, .0, .022, .112, upper, mat='cloth', r=.006)  # the tongue
    for i, x in enumerate((-.025, .0, .025)):
        s = s + box(x - .004, -.026, .095 - i * .012, x + .004, .026, .1 - i * .012, WHITE)
    for sy in (-1, 1):
        s = s + prism([(-.09, .045), (.03, .06), (.05, .052), (-.08, .035)], sy * .0405, sy * .0425, accent).turn([[1, 0, 0], [0, 0, 1], [0, 1, 0]])
    s = s + box(-.137, -.012, .03, -.128, .012, .1, accent, r=.003)  # the heel tab
    return s


def sneakers(upper, accent):
    a = sneaker(upper, accent)
    return [gen(a, -.012, -.05), gen(a, .012, .05)]


# ------------------------------------------------------------------------------------------------ electronics


def phone(back, screen):
    p = box(-.037, -.0045, 0, .037, .0045, .155, back, mat='metal', r=.006)
    p = p + box(-.033, .0045, .006, .033, .0055, .149, screen, emit=True)
    p = p + box(-.012, .0045, .14, .012, .0058, .145, BLACK)
    p = p.rotx(16).move(0, .015, .012)
    stand = box(-.045, -.03, 0, .045, .045, .012, WHITE, r=.003) + box(-.02, -.032, .012, .02, -.016, .1, WHITE).rotx(16)
    boxes = box(-.045, -.115, 0, .045, -.05, .022, WHITE, r=.002) + box(-.012, -.095, .022, .012, -.07, .0225, BLACK)
    return p + stand + boxes


def laptop(body):
    s = box(-.16, -.11, 0, .16, .11, .014, body, mat='metal', r=.004)
    s = s + box(-.14, -.075, .014, .14, .035, .0155, '#2C3036') + box(-.04, .05, .014, .04, .095, .0152, _darker(body))
    lid = box(-.16, -.008, 0, .16, 0, .21, body, mat='metal', r=.003)
    lid = lid + box(-.148, 0, .012, .148, .001, .198, '#3B7DD8', emit=True) + box(-.148, .0005, .012, .148, .0015, .05, '#5BB0F0', emit=True)
    return s + lid.rotx(14).move(0, -.105, .014)


def headphones(color):
    s = lathe([(.045, 0), (.045, .01), (.03, .014)], BLACK, n=12) + cylinder(.006, .01, .17, BLACK, n=6)
    s = s + box(-.03, -.012, .168, .03, .012, .176, BLACK)
    band = torus(.075, .008, color, nu=14, nv=5, arc=180).rotx(90).move(0, 0, .1)
    s = s + band
    cup = lathe([(0, 0), (.038, 0), (.04, .008), (.04, .028), (0, .03)], color, n=12, mat='metal')
    pad = lathe([(.03, .028), (.036, .03), (.036, .042), (.02, .044), (0, .044)], BLACK, n=12, mat='cloth')
    one = (cup + pad).roty(-90)
    s = s + one.move(.115, 0, .1) + one.rotz(180).move(-.115, 0, .1)
    return s


def smartwatch(band):
    s = box(-.05, -.04, 0, .05, .04, .01, WHITE, r=.003)
    s = s + cylinder(.022, -.03, .03, WHITE, n=10).roty(90).move(0, 0, .042)
    s = s + torus(.028, .006, band, nu=14, nv=4).roty(90).move(0, 0, .042)
    face = box(-.019, -.006, -.024, .019, .006, .024, BLACK, r=.006) + box(-.016, .006, -.02, .016, .0068, .02, '#2D9CDB', emit=True)
    return s + face.move(0, .033, .042)


def earbuds(color):
    s = box(-.05, -.04, 0, .05, .04, .008, '#DADDE1', r=.003)
    s = s + box(-.03, -.025, .008, .03, .0, .055, color, r=.012, seg=2) + box(-.03, -.0254, .042, .03, .0004, .0435, '#9AA3AD')
    bud = sphere(.01, .01, .01, color, nu=8, nv=4) + cylinder(.0035, -.03, 0, color, n=6).rotx(-20)
    s = s + bud.move(-.016, .022, .04) + bud.rotz(180).move(.016, .022, .04)
    return s


# ------------------------------------------------------------------------------------------------ jewelry


def ring_box(metal, stone):
    s = box(-.035, -.035, 0, .035, .035, .03, VELVET, mat='cloth', r=.005)
    s = s + box(-.035, -.05, .028, .035, -.036, .095, VELVET, mat='cloth', r=.005)
    s = s + box(-.03, -.03, .03, .03, .03, .036, '#3D1420', mat='cloth', r=.003)
    s = s + torus(.012, .0026, metal, nu=14, nv=4, mat='metal').rotx(90).move(0, 0, .046)
    s = s + gem(.0065, stone).move(0, 0, .057)
    return s


def necklace(metal, stone):
    s = box(-.05, -.05, 0, .05, .05, .016, VELVET, mat='cloth', r=.005)
    s = s + torus(.03, .0017, metal, nu=20, nv=3, mat='metal').scale(1.15, 1, 1).move(0, -.006, .017)
    s = s + gem(.0075, stone).rotx(90).move(0, .03, .0175)
    return s


def watch(metal, face):
    s = box(-.034, -.025, 0, .034, .025, .07, CREAM, mat='cloth', r=.016, seg=2)
    s = s + torus(.03, .0055, '#3A3A3A', nu=16, nv=4).roty(90).scale(1, .95, 1).move(0, 0, .036)
    case = lathe([(0, 0), (.019, 0), (.02, .004), (.02, .011), (0, .012)], metal, n=14, mat='metal').rotx(-90)
    dial = disc(.016, 0, face, n=14).rotx(-90)
    hands = box(-.0012, 0, -.001, .0012, .0008, .012, WHITE) + box(-.001, 0, -.0012, .009, .0008, .0012, WHITE)
    return s + case.move(0, .029, .036) + dial.move(0, .0415, .036) + hands.move(0, .0418, .036)


# ------------------------------------------------------------------------------------------------ concessions and food


def popcorn():
    s = lathe([(.045, 0), (.066, .14)], '#C0392B', n=16, stripes=('#D0392B', WHITE), smooth=False)
    s = s + lathe([(.066, .14), (.062, .158), (.045, .178), (.02, .19), (0, .192)], '#F7E08A', n=12)
    rnd = random.Random(7)
    for k in range(9):
        a = k * 2.4
        rr = .02 + .035 * rnd.random()
        z = .192 - (rr / .066) ** 2 * .04
        s = s + sphere(.011, .011, .009, '#FFF4C8', nu=5, nv=3).move(rr * math.cos(a), rr * math.sin(a), z)
    return s


def nachos():
    s = box(-.07, -.05, 0, .07, .05, .028, '#2B2B2B', r=.004)
    rnd = random.Random(3)
    chip = prism([(-.022, -.016), (.022, -.016), (0, .022)], 0, .003, '#F2C94C')
    for k in range(9):
        c = chip.rotx(rnd.uniform(-25, 25)).roty(rnd.uniform(-25, 25)).rotz(rnd.uniform(0, 360))
        s = s + c.move(rnd.uniform(-.04, .03), rnd.uniform(-.03, .03), .03 + rnd.uniform(0, .012))
    s = s + lathe([(.017, 0), (.02, .025)], WHITE, n=10).move(.045, .025, .028) + disc(.019, .051, '#F2994A', n=10).move(.045, .025, 0)
    return s


def cotton_candy(color):
    s = box(-.035, -.035, 0, .035, .035, .03, '#DADDE1', r=.004)
    s = s + cylinder(.004, .03, .12, WHITE, n=6)
    s = s + sphere(.05, .045, .055, color, nu=10, nv=6, mat='cloth').move(0, 0, .17)
    s = s + sphere(.035, .035, .04, color, nu=8, nv=5, mat='cloth').move(.03, .015, .2)
    s = s + sphere(.032, .03, .035, color, nu=8, nv=5, mat='cloth').move(-.028, -.01, .21)
    return s


def flowers(bloom, kind, seed):
    rnd = random.Random(seed)
    s = lathe([(.06, 0), (.064, .004), (.075, .16)], '#9AA3AD', n=12, mat='metal')
    s = s + lathe([(.077, .15), (.077, .165)], '#7E8790', n=12, mat='metal', smooth=False)
    if kind == 'rose':
        s = s + lathe([(.035, .12), (.09, .33)], KRAFT, n=12, cap=False, smooth=False)
    for k in range(7):
        a = k * 2 * math.pi / 7 + rnd.uniform(-.2, .2)
        rr = 0 if k == 0 else .045
        top = (rr * 1.4 * math.cos(a), rr * 1.4 * math.sin(a), .34 + rnd.uniform(-.03, .04))
        s = s + tube([(rr * .3 * math.cos(a), rr * .3 * math.sin(a), .1), top], .003, GREEN, n=4)
        if kind == 'rose':
            b = sphere(.026, .026, .022, bloom, nu=8, nv=4) + torus(.02, .009, _darker(bloom), nu=8, nv=3).move(0, 0, -.004)
        else:
            b = sphere(.017, .017, .028, bloom, nu=7, nv=4)
        s = s + b.move(top[0], top[1], top[2] + .01)
    leaf = prism([(0, 0), (.02, .03), (0, .07), (-.02, .03)], -.001, .001, GREEN)
    for k in range(4):
        s = s + leaf.rotx(-60).rotz(k * 90 + 45).move(0, 0, .17)
    return s


def gelato(color, garnish):
    s = box(-.125, -.15, 0, .125, .15, .01, SILVER, mat='metal')
    for x0, y0, x1, y1 in ((-.125, -.15, .125, -.142), (-.125, .142, .125, .15), (-.125, -.15, -.117, .15), (.117, -.15, .125, .15)):
        s = s + box(x0, y0, 0, x1, y1, .08, SILVER, mat='metal')
    s = s + box(-.116, -.141, .01, .116, .141, .1, color, r=.035, seg=2)
    s = s + sphere(.012, .012, .01, garnish, nu=6, nv=3).move(-.04, .02, .1) + sphere(.012, .012, .01, garnish, nu=6, nv=3).move(.035, -.04, .098)
    return s


def pizza():
    s = cylinder(.155, 0, .004, SILVER, n=16, mat='metal')
    s = s + torus(.138, .016, '#D9A55B', nu=20, nv=5).scale(1, 1, .75).move(0, 0, .016)
    s = s + cylinder(.135, .004, .018, '#F2C14E', n=16)
    rnd = random.Random(5)
    for k in range(9):
        a = k * 2.39
        rr = .03 + .085 * ((k * .61) % 1)
        s = s + cylinder(.019, .018, .021, '#B03A2E', n=8).move(rr * math.cos(a), rr * math.sin(a), 0)
    return s


PRODUCTS = {
    # ---------------------------------------------------------------- produce (heaped in crates)
    'apples': pile(F + 'apple.glb', .08, .078, 2, 3, 2, 1),
    'bananas': bananas(),
    'carrots': carrots(),
    'lettuce': [kit(F + 'cabbage.glb', 0, -.075, 0, .14, .14, .115), kit(F + 'cabbage.glb', 0, .075, 0, .14, .14, .115, rot=[('z', 70)])],
    # ---------------------------------------------------------------- café and bakery
    'coffee': [gen(togo_cup('#8D6E63'))],
    'tea': [gen(togo_cup('#A5D6A7', tag='#F2C94C'))],
    'croissant': [kit(F + 'croissant.glb', -.058, .045, 0, .12, .12, .05, rot=[('z', 20)]),
                  kit(F + 'croissant.glb', .058, .045, 0, .12, .12, .05, rot=[('z', -15)]),
                  kit(F + 'croissant.glb', 0, -.06, 0, .12, .12, .05, rot=[('z', 90)])],
    'cupcake': [kit(F + 'cupcake.glb', -.045, .04, 0, .075, .075, .085), kit(F + 'cupcake.glb', .045, .04, 0, .075, .075, .085),
                kit(F + 'cupcake.glb', 0, -.045, 0, .075, .075, .085)],
    'fresh_food': [kit(F + 'salad.glb', -.065, 0, 0, .12, .12, .06), kit(F + 'sandwich.glb', .068, 0, 0, .11, .11, .045, rot=[('z', 30)])],
    'frozen_food': [gen(frozen_boxes('#56CCF2'))],
    # ---------------------------------------------------------------- drinks
    'soda': grid(can('#D93A2F', band=WHITE), 2, 3, .07, .072),
    'energy_drink': grid(can('#1E2328', band='#00C853', r=.027, h=.158), 2, 3, .058, .06),
    'beer': grid(beer_bottle('#F2C94C'), 2, 3, .066, .07),
    'protein_shake': grid(shaker('#D7CCC8', '#4E342E'), 2, 3, .076, .078),
    'wine': grid(wine_bottle('#8E2442'), 2, 3, .08, .085),
    'whiskey': grid(whiskey_bottle('#B5651D'), 2, 2, .092, .07),
    'draft_beer': [gen(pint())],
    'cocktail': Variants([[gen(martini('#FF4FD8'))], [gen(martini('#3EC1F3'))], [gen(martini('#F2994A'))]]),
    # ---------------------------------------------------------------- shelf goods
    'snacks': [gen(snack_box('#7B3FC4', '#F2C94C'), 0, -.055), gen(snack_box('#7B3FC4', '#F2C94C'), 0, .055)],
    'toilet_paper': [gen(toilet_rolls())],
    'hair_products': Variants([hair_products('#9B51E0'), hair_products('#2D9CDB'), hair_products('#EB5757')]),
    'gift_cheap': Variants([
        [gen(gift(.12, .12, .1, '#F2994A', WHITE), -.04, -.03), gen(gift(.08, .08, .07, '#2D9CDB', GOLD, bow=False), .055, .05)],
        [gen(gift(.12, .12, .1, '#F2C94C', '#EB5757'), -.04, -.03), gen(gift(.08, .08, .07, '#27AE60', WHITE, bow=False), .055, .05)],
        [gen(gift(.12, .12, .1, '#EB5757', GOLD), -.04, -.03), gen(gift(.08, .08, .07, WHITE, '#EB5757', bow=False), .055, .05)],
    ]),
    'gift_exp': Variants([
        [gen(gift(.14, .14, .12, '#8E44AD', GOLD), -.03, -.04), gen(hat_box('#F8BBD0', '#8E44AD'), .06, .06)],
        [gen(gift(.14, .14, .12, '#1F1F24', GOLD), -.03, -.04), gen(hat_box(WHITE, '#1F1F24'), .06, .06)],
        [gen(gift(.14, .14, .12, '#B71C1C', GOLD), -.03, -.04), gen(hat_box('#FFE0B2', '#B71C1C'), .06, .06)],
    ]),
    'umbrella': Variants([[gen(umbrella(c), -.045), gen(umbrella(c), .045)] for c in ('#2F80ED', '#26292E', '#C0392B')]),
    # ---------------------------------------------------------------- clothes (hanging) and shoes
    'clothes_cheap': Variants([[gen(tee('#2F80ED'))], [gen(tee('#EB5757'))], [gen(tee('#F2F2F2', '#2D9CDB'))], [gen(tee('#27AE60'))],
                               [gen(tee('#F2C94C'))]]),
    'clothes_exp': Variants([[gen(blazer('#2B2B30', '#3A3A42'))], [gen(blazer('#B08D57', '#9A7A48'))], [gen(blazer('#2C3E50', '#22303E'))]]),
    'sneakers': Variants([sneakers(WHITE, '#EB5757'), sneakers('#26292E', WHITE), sneakers('#2F80ED', WHITE), sneakers('#EB5757', '#26292E')]),
    # ---------------------------------------------------------------- books and magazines
    'book_paper': Variants([[gen(books(c, .28, .13, .19, k))] for k, c in enumerate((
        ('#27AE60', '#F2C94C', '#EB5757', '#2D9CDB'), ('#F2994A', '#9B51E0', '#56CCF2', '#F5F5F5'), ('#E91E63', '#4CAF50', '#FFC107', '#3F51B5')))]),
    'book_hard': Variants([[gen(books(c, .28, .16, .24, k + 10, bands=GOLD))] for k, c in enumerate((
        ('#5D4037', '#1B5E20', '#7B1F2E', '#263238'), ('#2C3E50', '#8B5A3C', '#4A148C', '#33691E'), ('#3E2723', '#1A237E', '#880E4F', '#004D40')))]),
    'comic_book': Variants([[gen(comics(c, k))] for k, c in enumerate((('#F2C94C', '#EB5757', '#2D9CDB'), ('#E91E63', '#FFEB3B', '#00BCD4'), ('#27AE60', '#FF9800', '#673AB7')))]),
    'magazine': Variants([[gen(magazine(c, a))] for c, a in (('#EB5757', '#F2C9C4'), ('#2D9CDB', '#BBDEFB'), ('#F2C94C', '#FFF3C4'), ('#26292E', '#9AA3AD'))]),
    # ---------------------------------------------------------------- electronics
    'smartphone': Variants([[gen(phone('#2B2F35', '#3B7DD8'))], [gen(phone('#D5D9DE', '#E3A04B'))], [gen(phone('#3A5A80', '#7B61FF'))]]),
    'laptop': Variants([[gen(laptop('#AEB6BF'))], [gen(laptop('#5B6770'))]]),
    'headphones': Variants([[gen(headphones('#EB5757'))], [gen(headphones('#26292E'))], [gen(headphones('#F2F2F2'))]]),
    'smartwatch': Variants([[gen(smartwatch('#2F80ED'))], [gen(smartwatch('#26292E'))], [gen(smartwatch('#F48FB1'))]]),
    'earbuds': Variants([[gen(earbuds(WHITE))], [gen(earbuds('#2B2F35'))]]),
    # ---------------------------------------------------------------- jewelry
    'ring': Variants([[gen(ring_box('#F2C94C', '#BFEFFF'))], [gen(ring_box('#E0E0E0', '#FF5C8A'))]]),
    'necklace': Variants([[gen(necklace('#E0E0E0', '#2F80ED'))], [gen(necklace('#F2C94C', '#27AE60'))]]),
    'watch': Variants([[gen(watch('#C0C6CC', '#1C2A3A'))], [gen(watch('#E8C04A', '#F5F5F5'))], [gen(watch('#3A3A3A', '#0E4D64'))]]),
    # ---------------------------------------------------------------- fast food and concessions
    'burger': [kit(F + 'burger.glb', 0, 0, 0, .1, .1, .075)],
    'hotdog': [kit(F + 'hot-dog.glb', 0, 0, 0, .17, .06, .05)],
    'fries': [kit(F + 'fries.glb', 0, 0, 0, .075, .065, .13, rot=[('z', 90)])],
    'pizza': [gen(pizza())],
    'ice_cream': Variants([[gen(gelato(c, g))] for c, g in (('#FF8AD8', '#E53935'), ('#6D4C41', '#3E2723'), ('#F3E5C0', '#6D4C41'), ('#A5D6A7', '#5D4037'))]),
    'popcorn': [gen(popcorn())],
    'slushie': Variants([[gen(slushie('#56CCF2'))], [gen(slushie('#EB5757'))]]),
    'nachos': [gen(nachos())],
    'cotton_candy': Variants([[gen(cotton_candy('#FF8AD8'))], [gen(cotton_candy('#8FD3FF'))]]),
    'flower_cheap': Variants([[gen(flowers(c, 'tulip', k))] for k, c in enumerate(('#F2994A', '#F2C94C', '#FF8AD8'))]),
    'flower_exp': Variants([[gen(flowers(c, 'rose', k))] for k, c in enumerate(('#D32F2F', '#F5F5F5', '#F48FB1'))]),
    # ---------------------------------------------------------------- unopened stock
    '_box': [kit(FURN + 'cardboardBoxClosed.glb', 0, 0, 0, .3, .3, .3, stretch=True)],
}
