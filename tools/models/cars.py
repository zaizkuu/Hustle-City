"""Vehicles: the everyday traffic of every city (sedans, hatchbacks, taxis, pickups, vans, sports cars, SUVs and box
trucks) and each city's own (Manila's jeepneys, modern jeepneys and tricycles, kei cars, city buses, motorbikes, mopeds).

Each is modelled at a typical size in meters (x forward, y to the left, z up, its middle at the origin on the ground)
and the game stretches it to each vehicle's own length, width and height. Paint is white with the 'paint' material: the
game tints it with the vehicle's color (grey paint comes out darker). Magenta paint is the livery's second color
(stripes, bands, a two-tone roof), tinted the same way. Lenses are emissive: the game lights the headlights and tail
lights at night (and the tail lights alone when braking) and leaves them as plain lenses by day; emissive glass is a
window lit from inside at night and plain glass by day, and amber emissive route boards stay lit. Riders aren't
modelled: the game seats its own on the bikes.
"""
import math

import numpy as np

from products import gen
from shapes import _tris, box, disc, lathe, prism

PAINT = '#FFFFFF'
SHADE = '#A8A8A8'
GLASS = '#26313D'
TIRE = '#1E2023'
RIM = '#B9C0C7'
BUMPER = '#2E3136'
BLACK = '#17191C'
HEAD = '#FFF1D0'
TAIL = '#C0392B'
PLATE = '#F2F2EA'
CHROME = '#D3D8DD'
ACCENT = '#FF00FF'
LIT = '#FFE3A0'
LED = '#FFD34D'
INSIDE = '#2B2622'
FRAME = '#2B2E33'
ROOF = '#E4E7EA'
DOOR = '#1B2128'


def paint(k):
    """Paint [k]/255 as dark as the vehicle's color."""
    return '#%02X%02X%02X' % (k, k, k)


def accent(k):
    """The livery's second color, [k]/255 as dark."""
    return '#%02X00%02X' % (k, k)


def side(profile, w, color, mat='', emit=False):
    """A side profile [(x, z), ...] extruded across the car from -w to w."""
    return prism(profile, -w, w, color, mat=mat, emit=emit).rotx(90)


def quad(a, b, c, d, color, emit=False, mat=''):
    """A flat quad (two triangles), facing the side its corners run counterclockwise round."""
    p = np.array([a, b, c, a, c, d], dtype=np.float64)
    n = np.cross(p[1] - p[0], p[2] - p[0])
    n = n / (np.linalg.norm(n) or 1)
    return _tris(p, np.tile(n, (6, 1)), color, mat, emit)


def front_face(x, y0, y1, z0, z1, color, emit=False, mat=''):
    """A face across the car at [x], looking forward (+x)."""
    return quad((x, y0, z0), (x, y1, z0), (x, y1, z1), (x, y0, z1), color, emit, mat)


def rear_face(x, y0, y1, z0, z1, color, emit=False, mat=''):
    """The same looking back (-x)."""
    return quad((x, y1, z0), (x, y0, z0), (x, y0, z1), (x, y1, z1), color, emit, mat)


def face_y(x0, x1, z0, z1, y, color, emit=False, mat=''):
    """A face along one side at [y], looking out (to +y when y > 0)."""
    if y > 0:
        return quad((x0, y, z0), (x0, y, z1), (x1, y, z1), (x1, y, z0), color, emit, mat)
    return quad((x0, y, z0), (x1, y, z0), (x1, y, z1), (x0, y, z1), color, emit, mat)


def side_faces(x0, x1, z0, z1, w, color, emit=False, mat=''):
    """A strip on both sides of the car (at y = +-[w]), looking out."""
    return face_y(x0, x1, z0, z1, w, color, emit, mat) + face_y(x0, x1, z0, z1, -w, color, emit, mat)


def strip(x0, x1, z0, z1, y, color, gaps=(), emit=False, mat=''):
    """A face along the side at [y] from x0 to x1, broken where [gaps] (x0, x1) are (doorways)."""
    s = None
    a = x0
    for g0, g1 in sorted(gaps) + [(x1, x1)]:
        b = min(g0, x1)
        if b - a > .02:
            f = face_y(a, b, z0, z1, y, color, emit, mat)
            s = f if s is None else s + f
        a = max(a, g1)
    return s


def top_face(x0, x1, y0, y1, z, color, mat=''):
    """A face looking up at height [z]."""
    return quad((x0, y0, z), (x1, y0, z), (x1, y1, z), (x0, y1, z), color, False, mat)


def screen(xa, za, xb, zb, w, color=GLASS, inset=.06, mat='glass', emit=False):
    """Glass across the car (from -w to w) on the sloping edge (xa, za) to (xb, zb) of its side profile (which runs
    counterclockwise), standing just proud of it."""
    dx, dz = xb - xa, zb - za
    ln = math.hypot(dx, dz)
    nx, nz = dz / ln * .012, -dx / ln * .012
    ux, uz = dx / ln * inset, dz / ln * inset
    ax, az = xa + ux + nx, za + uz + nz
    bx, bz = xb - ux + nx, zb - uz + nz
    return quad((ax, -w, az), (ax, w, az), (bx, w, bz), (bx, -w, bz), color, emit, mat)


def wheel(x, y, r, w):
    """One wheel, its hub caps on both sides (a bike's)."""
    t = tire(r, w).move(x, y, r)
    for sg in (1, -1):
        t = t + disc(r * .5, 0, RIM, n=8).rotx(-90 if sg > 0 else 90).move(x, y + sg * (w / 2 + .004), r)
    return t


def arch(x, z, r, w, sgn):
    """The dark wheel arch on the body's side behind a wheel: a half disc looking out to side [sgn]."""
    pts = []
    n = 8
    for k in range(n):
        a0 = math.pi * k / n; a1 = math.pi * (k + 1) / n
        c = (x, sgn * w, z)
        p0 = (x + r * math.cos(a0), sgn * w, z + r * math.sin(a0))
        p1 = (x + r * math.cos(a1), sgn * w, z + r * math.sin(a1))
        pts += [c, p0, p1] if sgn < 0 else [c, p1, p0]
    p = np.array(pts, dtype=np.float64)
    return _tris(p, np.tile([0, sgn, 0], (len(pts), 1)), BLACK, '')


def tire(r, w):
    """A tire [r] round and [w] wide on its side across y, its middle at the origin: rounded off by smooth shading."""
    return lathe([(r, -w / 2), (r, w / 2)], TIRE, n=12).rotx(90)


def wheels(xs, y, r, w, body_w, arches=True):
    """Four wheels (tire and rim cap) at x in [xs], their centers [y] out from the middle, under arches in the body's side
    (or not, under skirts and fenders)."""
    s = None
    for x in xs:
        for sg in (1, -1):
            t = tire(r, w).move(x, sg * y, r)
            cap = disc(r * .62, 0, RIM, n=8).rotx(-90 if sg > 0 else 90).move(x, sg * (y + w / 2 + .004), r)
            part = t + cap
            if arches:
                part = part + arch(x, r, r + .07, body_w + .003, sg)
            s = part if s is None else s + part
    return s


def lights(xf, xr, yo, yi, zf, zr, hf=.1, hr=.1, d=.1, rear_bar=False):
    """Headlights at the front corners ([xf], between [yi] and [yo] out from the middle, from [zf] up) and tail lights
    at the back ([xr] from [zr] up), or one bar across the back."""
    s = None
    for sg in (1, -1):
        h = box(xf - d, sg * yi, zf, xf, sg * yo, zf + hf, HEAD, emit=True)
        s = h if s is None else s + h
        if not rear_bar:
            s = s + box(xr, sg * yi, zr, xr + d, sg * yo, zr + hr, TAIL, emit=True)
    if rear_bar:
        s = s + box(xr, -yo, zr, xr + d, yo, zr + hr, TAIL, emit=True)
    return s


def ends(xf, xr, w, zb=.3, zt=.45, plate=True, grille=(.4, .38, .52)):
    """Bumpers front and back, the grille and the number plates."""
    s = box(xf - .14, -w, zb, xf + .05, w, zt, BUMPER) + box(xr - .05, -w, zb, xr + .14, w, zt, BUMPER)
    gw, g0, g1 = grille
    s = s + front_face(xf + .002, -gw, gw, g0, g1, BLACK)
    if plate:
        s = s + front_face(xf + .052, -.26, .26, zb + .03, zb + .13, PLATE) + rear_face(xr - .052, -.26, .26, zb + .03, zb + .13, PLATE)
    return s


def mirrors(x, z, w):
    return box(x, w - .02, z, x + .14, w + .1, z + .1, PAINT, mat='paint') + box(x, -w - .1, z, x + .14, -w + .02, z + .1, PAINT, mat='paint')


def sedan():
    """A four-door saloon, 4.5 m."""
    w = .92
    s = side([(-2.25, .3), (2.25, .3), (2.27, .52), (2.2, .7), (.95, .86), (-1.45, .9), (-2.12, .88), (-2.25, .75), (-2.27, .52)], w, PAINT, 'paint')
    s = s + side([(.98, .86), (.25, 1.38), (-.9, 1.42), (-1.5, .9)], .8, GLASS, 'glass')
    s = s + box(-.92, -.76, 1.37, .22, .76, 1.45, PAINT, mat='paint') + box(-.4, -.81, .88, -.3, .81, 1.39, PAINT, mat='paint')
    s = s + box(-1.95, -.86, .18, 1.95, .86, .3, BLACK)
    s = s + side_faces(.45, .465, .36, .86, w + .002, BLACK) + side_faces(-.55, -.535, .36, .88, w + .002, BLACK)
    s = s + lights(2.27, -2.28, .85, .52, .56, .6, rear_bar=False) + ends(2.25, -2.25, .9) + mirrors(.72, .92, w)
    return s + wheels((1.38, -1.38), .84, .34, .22, w)


def hatchback():
    """A small five-door hatchback, 4.1 m."""
    w = .9
    s = side([(-2.05, .3), (2.05, .3), (2.08, .52), (2.0, .7), (.8, .86), (-1.95, .94), (-2.08, .86), (-2.08, .52)], w, PAINT, 'paint')
    s = s + side([(.82, .86), (.15, 1.4), (-1.55, 1.43), (-1.98, .94)], .8, GLASS, 'glass')
    s = s + box(-1.56, -.76, 1.38, .12, .76, 1.46, PAINT, mat='paint') + box(-.5, -.81, .88, -.4, .81, 1.4, PAINT, mat='paint')
    s = s + box(-1.75, -.84, .18, 1.75, .84, .3, BLACK)
    s = s + side_faces(.35, .365, .36, .86, w + .002, BLACK) + side_faces(-.62, -.605, .36, .9, w + .002, BLACK)
    s = s + lights(2.07, -2.09, .83, .52, .56, .66, hr=.16) + ends(2.05, -2.05, .88) + mirrors(.6, .92, w)
    return s + wheels((1.28, -1.3), .82, .32, .22, w)


def taxi():
    """A sedan cab: the roof sign (lit at night) and a checker band along each side."""
    s = sedan()
    s = s + box(-.55, -.3, 1.45, -.05, .3, 1.72, '#FAFAFA', emit=True) + box(-.57, -.32, 1.45, -.03, .32, 1.49, BLACK)
    for k in range(14):
        x0 = -1.95 + k * .28
        col = BLACK if k % 2 == 0 else '#F2F2EA'
        s = s + side_faces(x0, x0 + .28, .58, .66, .922, col)
    return s


def pickup():
    """A pickup, 5.2 m: a crew cab and an open bed."""
    w = .92
    s = side([(-.5, .38), (2.6, .38), (2.63, .6), (2.55, .9), (1.3, 1.02), (-.5, 1.04)], w, PAINT, 'paint')
    s = s + side([(1.32, 1.02), (.75, 1.7), (-.35, 1.72), (-.45, 1.04)], .86, GLASS, 'glass')
    s = s + box(-.37, -.82, 1.66, .72, .82, 1.8, PAINT, mat='paint') + box(.32, -.87, 1.0, .42, .87, 1.68, PAINT, mat='paint')
    # the bed: floor, walls and tailgate
    s = s + box(-2.6, -w, .38, -.5, w, .5, PAINT, mat='paint') + box(-2.55, -.84, .5, -.52, .84, .55, '#2A2C30')
    for sg in (1, -1):
        s = s + box(-2.6, sg * .84, .5, -.5, sg * w, 1.06, PAINT, mat='paint')
    s = s + box(-2.62, -w, .5, -2.52, w, 1.03, PAINT, mat='paint') + box(-.55, -w, .5, -.45, w, 1.08, PAINT, mat='paint')
    s = s + box(-2.4, -.86, .22, 2.3, .86, .38, BLACK)
    s = s + side_faces(.95, .965, .45, 1.0, w + .002, BLACK)
    s = s + lights(2.6, -2.66, .86, .55, .72, .72, hf=.12, hr=.2, d=.08) + ends(2.6, -2.6, .9, zb=.36, zt=.52, grille=(.5, .54, .82)) + mirrors(1.1, 1.08, w)
    return s + wheels((1.7, -1.75), .86, .4, .24, w)


def van():
    """A delivery van, 5.8 m: a tall box with a raked windscreen."""
    w = 1.05
    s = side([(-2.9, .38), (2.9, .38), (2.93, .7), (2.85, 1.15), (2.1, 1.45), (1.3, 2.42), (1.1, 2.5), (-2.9, 2.5)], w, PAINT, 'paint')
    # the windscreen on the slope, side windows up front, the rear doors' windows
    n = np.array([.97, .8]) / math.hypot(.97, .8) * .012
    s = s + quad((2.08 + n[0], -.92, 1.48 + n[1]), (2.08 + n[0], .92, 1.48 + n[1]), (1.33 + n[0], .92, 2.38 + n[1]), (1.33 + n[0], -.92, 2.38 + n[1]), GLASS)
    for sg in (1, -1):
        y = sg * (w + .002)
        a, b, c, d = (1.05, y, 1.5), (1.95, y, 1.5), (1.3, y, 2.25), (1.05, y, 2.25)
        s = s + (quad(a, d, c, b, GLASS) if sg > 0 else quad(a, b, c, d, GLASS))
    s = s + rear_face(-2.902, -.95, -.08, 1.6, 2.3, GLASS) + rear_face(-2.902, .08, .95, 1.6, 2.3, GLASS)
    s = s + rear_face(-2.903, -.02, .02, .45, 2.45, BLACK)
    s = s + side_faces(.7, .715, .45, 2.3, w + .002, BLACK) + side_faces(-.6, -.585, .45, 2.3, w + .002, BLACK)
    s = s + side_faces(-2.7, 1.9, .95, 1.05, w + .003, SHADE)
    s = s + box(-2.6, -1.0, .22, 2.6, 1.0, .38, BLACK)
    s = s + lights(2.9, -2.93, 1.0, .72, .92, .7, hf=.14, hr=.6, d=.08) + ends(2.9, -2.9, 1.04, zb=.36, zt=.55, grille=(.55, .6, .9)) + mirrors(1.95, 1.45, w)
    return s + wheels((2.0, -2.0), .92, .4, .26, w)


def sports():
    """A low sports coupe, 4.5 m: long hood, low cabin, a spoiler."""
    w = .97
    s = side([(-2.25, .22), (2.25, .22), (2.3, .4), (2.15, .55), (.6, .72), (-1.6, .78), (-2.2, .74), (-2.28, .5)], w, PAINT, 'paint')
    s = s + side([(.62, .72), (-.15, 1.15), (-1.0, 1.17), (-1.65, .78)], .78, GLASS, 'glass')
    s = s + box(-1.0, -.72, 1.12, -.12, .72, 1.2, PAINT, mat='paint')
    s = s + box(-2.25, -.85, .86, -1.95, .85, .9, BLACK) + box(-2.15, .55, .74, -2.08, .62, .86, BLACK) + box(-2.15, -.62, .74, -2.08, -.55, .86, BLACK)
    s = s + box(-1.95, -.9, .12, 1.95, .9, .22, BLACK)
    s = s + side_faces(-.05, -.035, .3, .74, w + .002, BLACK) + side_faces(-1.9, 1.9, .24, .3, w + .002, BLACK)
    s = s + lights(2.27, -2.29, .9, .55, .42, .52, hf=.06, hr=.06, d=.08, rear_bar=True) + ends(2.25, -2.25, .95, zb=.22, zt=.34, grille=(.6, .26, .38))
    s = s + mirrors(.42, .78, w)
    return s + wheels((1.45, -1.4), .86, .34, .26, w)


def suv():
    """An SUV, 4.8 m: high, square, with roof rails and dark cladding."""
    w = 1.0
    s = side([(-2.4, .45), (2.4, .45), (2.43, .75), (2.38, 1.02), (1.2, 1.12), (-2.4, 1.15)], w, PAINT, 'paint')
    s = s + side([(1.22, 1.12), (.65, 1.74), (-2.2, 1.77), (-2.38, 1.15)], .92, GLASS, 'glass')
    s = s + box(-2.22, -.88, 1.71, .63, .88, 1.82, PAINT, mat='paint')
    s = s + box(-.35, -.93, 1.12, -.25, .93, 1.74, PAINT, mat='paint') + box(-1.75, -.93, 1.13, -1.65, .93, 1.76, PAINT, mat='paint')
    for sg in (1, -1):
        s = s + box(-1.9, sg * .72, 1.82, .3, sg * .8, 1.88, CHROME, mat='metal')
    s = s + side_faces(-2.35, 2.35, .45, .62, w + .002, BLACK) + side_faces(.45, .465, .62, 1.12, w + .002, BLACK)
    s = s + box(-2.1, -.94, .25, 2.1, .94, .45, BLACK)
    s = s + lights(2.42, -2.44, .92, .58, .8, .9, hf=.12, hr=.16) + ends(2.4, -2.4, .98, zb=.4, zt=.58, grille=(.55, .6, .95)) + mirrors(1.0, 1.18, w)
    return s + wheels((1.5, -1.55), .9, .4, .26, w)


def box_truck():
    """A box truck, 7.2 m: a cab up front and a cargo box behind; the box's stripe takes the paint."""
    w = 1.15
    s = side([(1.7, .45), (3.6, .45), (3.62, .9), (3.55, 1.45), (3.15, 2.35), (1.7, 2.45)], w, PAINT, 'paint')
    n = np.array([.9, .4]) / math.hypot(.9, .4) * .012
    s = s + quad((3.53 + n[0], -1.0, 1.5 + n[1]), (3.53 + n[0], 1.0, 1.5 + n[1]), (3.17 + n[0], 1.0, 2.3 + n[1]), (3.17 + n[0], -1.0, 2.3 + n[1]), GLASS)
    s = s + side_faces(1.95, 2.9, 1.5, 2.2, w + .002, GLASS)
    s = s + box(-3.6, -1.2, .62, 1.6, 1.2, 3.3, '#F1F2F0') + box(-3.62, -1.18, .7, -3.6, 1.18, 3.2, '#D6D9DD')
    s = s + side_faces(-3.4, 1.4, 1.8, 2.1, 1.203, PAINT, mat='paint')
    s = s + box(-3.6, -.8, .35, 3.4, .8, .62, BLACK)
    s = s + lights(3.62, -3.64, 1.1, .8, .72, .7, hf=.14, hr=.18, d=.08) + ends(3.6, -3.6, 1.12, zb=.38, zt=.6, grille=(.6, .65, 1.25))
    s = s + mirrors(3.0, 1.9, w)
    return s + wheels((2.55, -2.25), .98, .46, .3, w)


def jeepney():
    """Manila's jeepney, 6.2 m: a long passenger cab on a jeep's front end. Fenders flank a narrow hood with a chrome
    grille, round lamps and two chrome horses; the cab has open windows between painted stripes (the livery's colors),
    a chrome roof with a rack and the route signboard, and an open doorway with a step at the back."""
    hl, hw, rz, bottom = 3.1, 1.025, 2.07, .48
    c1 = hl - 1.6   # the front of the passenger cab
    hood = hw * .6
    h0 = hl - 1.65  # where the hood starts
    o = hw + .004
    s = side([(-hl, bottom), (c1, bottom), (c1, 1.32), (c1 - .22, rz), (-hl, rz)], hw, PAINT, 'paint')
    s = s + screen(c1, 1.32, c1 - .22, rz, hw - .08)
    s = s + top_face(-hl, c1 - .22, -hw, hw, rz + .004, CHROME, 'metal')
    # a chrome skirt, the stripes and the open windows along both sides, the route painted on the top band
    s = s + side_faces(-hl, c1, bottom, bottom + .1, o, CHROME, mat='metal')
    s = s + side_faces(-hl, c1, .78, .86, o, accent(204), mat='paint')
    s = s + side_faces(-hl, c1, 1.04, 1.2, o, ACCENT, mat='paint')
    s = s + side_faces(-hl, c1 - .2, rz - .2, rz - .03, o, ACCENT, mat='paint')
    s = s + side_faces(-hl + .9, -hl + 2.6, rz - .16, rz - .07, o + .004, '#1F1F1F')
    w0, w1, n = -hl + .3, c1 - .28, 6
    step = (w1 - w0) / n
    for k in range(n):
        s = s + side_faces(w0 + k * step + .07, w0 + (k + 1) * step - .07, 1.3, rz - .26, o, INSIDE)
    for sg in (1, -1):
        # mirrors on stalks, the rails round the roof rack
        s = s + box(c1 + .05, sg * (hw + .02), 1.45, c1 + .14, sg * (hw + .22), 1.7, BUMPER)
        s = s + box(-hl + .15, sg * (hw - .1), rz, c1 - .35, sg * (hw - .02), rz + .12, CHROME, mat='metal')
    for f in (-hl + .3, -hl / 2 + c1 / 2 - .35):
        s = s + box(f, -hw + .1, rz + .06, f + .07, hw - .1, rz + .1, CHROME, mat='metal')
    # the visor over the windscreen and the route signboard
    s = s + box(c1 - .3, -hw, rz - .07, c1 + .28, hw, rz, CHROME, mat='metal')
    sb = c1 - .12
    s = s + box(sb - .08, -hw * .62, rz, sb, hw * .62, rz + .34, '#F7F3E6')
    s = s + front_face(sb + .005, -hw * .1, hw * .5, rz + .2, rz + .27, '#C0392B') + front_face(sb + .005, -hw * .4, hw * .5, rz + .08, rz + .15, '#1F3A93')
    # the hood with a chrome strip and the horses, the fenders either side of it
    s = s + side([(h0, bottom), (hl, bottom), (hl, 1.1), (hl - .14, 1.24), (h0, 1.3)], hood, PAINT, 'paint')
    s = s + box(h0 + .1, -.07, 1.25, hl - .2, .07, 1.29, CHROME, mat='metal')
    s = s + horse(hl - .5, hood * .6, 1.27) + horse(hl - .5, -hood * .6, 1.27)
    fw = (hw + .05 - hood) / 2
    for sg in (1, -1):
        s = s + side([(h0 + .05, .58), (hl - .04, .58), (hl - .04, .82), (hl - .36, 1.02), (h0 + .35, 1.02), (h0 + .05, .88)], fw,
                     PAINT, 'paint').move(0, sg * (hood + fw), 0)
        # round lamps either side of the grille, amber signals on the fenders
        s = s + disc(.085, 0, HEAD, n=8).roty(90).glow().move(hl + .02, sg * (hood - .21), .92)
        y0, y1 = sorted((sg * (hw - .2), sg * (hw + .02)))
        s = s + front_face(hl - .03, y0, y1, .66, .76, '#F39C12')
    s = s + box(hl - .02, -hw + .02, bottom, hl + .16, hw - .02, bottom + .16, CHROME, mat='metal')
    s = s + front_face(hl + .01, -hood + .08, hood - .08, .66, 1.06, CHROME, mat='metal')
    for k in range(-2, 3):
        s = s + front_face(hl + .015, k * .16 - .04, k * .16 + .04, .7, 1.0, '#5B6168')
    # the open doorway at the back, a step and grab bars, tail lamps
    s = s + rear_face(-hl - .01, -.42, .42, bottom + .12, rz - .14, INSIDE)
    s = s + box(-hl - .26, -.55, bottom - .06, -hl, .55, bottom + .04, CHROME, mat='metal')
    for sg in (1, -1):
        s = s + face_y(-hl - .06, -hl - .01, bottom + .2, rz - .2, sg * .5, CHROME, mat='metal') + rear_face(-hl - .06, *sorted((sg * .45, sg * .5)), bottom + .2, rz - .2, CHROME, mat='metal')
        y0, y1 = sorted((sg * (hw - .3), sg * (hw - .08)))
        s = s + rear_face(-hl - .01, y0, y1, .9, 1.25, TAIL, emit=True)
    s = s + box(-hl + .3, -.8, .25, hl - .4, .8, bottom, BLACK)
    # skirts and fenders hang over the wheels
    return s + wheels((hl - .85, -hl + 1.55), hw - .14, .4, .32, hw, arches=False)


def horse(f, y, z):
    """A rearing chrome horse standing on the hood, side on (flat: it's seen from either side)."""
    parts = [[(f - .17, z), (f - .1, z), (f - .04, z + .2), (f - .15, z + .2)],          # hind legs
             [(f - .2, z + .17), (f + .1, z + .2), (f + .1, z + .31), (f - .19, z + .29)],  # body
             [(f + .03, z + .26), (f + .12, z + .24), (f + .24, z + .42), (f + .15, z + .46)],  # neck and head
             [(f + .06, z + .21), (f + .22, z + .27), (f + .2, z + .32), (f + .05, z + .28)]]  # raised forelegs
    s = None
    for q in parts:
        p = quad(*[(x, y, zz) for x, zz in q], CHROME, mat='metal')
        s = p if s is None else s + p
    return s


def modern_jeep():
    """Manila's modern jeepney, 7 m: a boxy minibus with a flat front, a big windscreen under the LED route board, a
    window band (lit at night), the cooperative's band (the livery's second color), an air conditioner on the roof and
    the door on the right, the curb side where traffic keeps right (modern_jeep_l is its mirror image)."""
    hl, hw, h = 3.5, 1.1, 2.85
    front, rear = hl - 1.2, -hl + 1.5
    o = hw + .005
    s = side([(-hl, .36), (hl, .36), (hl, h - .26), (hl - .22, h), (-hl + .1, h), (-hl, h - .1)], hw, PAINT, 'paint')
    s = s + top_face(-hl + .1, hl - .22, -hw + .01, hw - .01, h + .004, ROOF)
    door0, door1 = front - 1.55, front - .5
    door = [(door0, door1)]
    # a dark skirt, a bold band under the windows and a thin one over them; broken by the door on the right
    for y, gaps in ((o, ()), (-o, door)):
        s = s + strip(-hl, hl, .36, .52, y, paint(128), gaps, mat='paint')
        s = s + strip(-hl, hl, .82, 1.08, y, ACCENT, gaps, mat='paint')
        s = s + strip(-hl, hl, 2.4, 2.48, y, ACCENT, gaps, mat='paint')
    # passenger windows back to the door, one long one on the driver's side, the door and a window ahead of it
    w0, w1, n = -hl + .35, door0 - .08, 4
    step = (w1 - w0) / n
    for k in range(n):
        s = s + side_faces(w0 + k * step + .06, w0 + (k + 1) * step - .06, 1.2, 2.32, o, LIT, True, 'glass')
    s = s + face_y(door0, hl - .2, 1.2, 2.32, o, LIT, True, 'glass')
    s = s + face_y(door0, door1, .42, 2.36, -o, DOOR) + face_y(door0 + .07, door1 - .07, .52, 2.3, -o - .004, GLASS, mat='glass')
    s = s + face_y(door1 + .08, hl - .2, 1.2, 2.32, -o, LIT, True, 'glass')
    s = s + face_y(door1 + .2, door1 + .85, 1.7, 2.05, -o - .006, '#F5F5F0')  # the route card in the window
    for sg in (1, -1):
        s = s + box(hl + .02, sg * (hw + .04), 1.6, hl + .2, sg * (hw + .2), 2.1, BUMPER)
    s = s + box(-1.3, -hw * .6, h, 1.0, hw * .6, h + .22, '#D4D8DC')
    # the front: the windscreen, the route board in amber on black, the band across the nose, the headlights
    s = s + box(hl - .02, -hw + .03, .36, hl + .1, hw - .03, .62, BUMPER)
    s = s + front_face(hl + .01, -hw + .1, hw - .1, 1.02, 2.3, GLASS, mat='glass')
    s = s + front_face(hl + .01, -hw + .12, hw - .12, 2.37, h - .3, '#15171A') + front_face(hl + .015, -hw + .35, hw - .35, 2.44, h - .37, LED, True)
    s = s + front_face(hl + .01, -hw + .1, hw - .1, .66, .9, ACCENT, mat='paint')
    s = s + front_face(hl + .015, -hw + .14, -hw + .5, .68, .86, HEAD, True) + front_face(hl + .015, hw - .5, hw - .14, .68, .86, HEAD, True)
    # the back: a window, the route number, tail lamps
    s = s + box(-hl - .1, -hw + .03, .36, -hl + .02, hw - .03, .58, BUMPER)
    s = s + rear_face(-hl - .01, -hw + .32, hw - .32, 1.3, 2.3, GLASS, mat='glass') + rear_face(-hl - .015, -.35, .35, 2.42, 2.62, LED, True)
    s = s + rear_face(-hl - .01, -hw + .08, -hw + .28, .7, 1.5, TAIL, True) + rear_face(-hl - .01, hw - .28, hw - .08, .7, 1.5, TAIL, True)
    return s + wheels((front, rear), hw - .145, .42, .31, hw)


def bus():
    """A single-decker city bus, 11 m: a band of big windows (lit at night) between the livery's stripes, the doors on
    the right where traffic keeps right (bus_l is its mirror image), a wide windscreen under an amber destination
    display and an air-conditioning pod on the white roof."""
    hl, hw, h = 5.5, 1.25, 3.1
    front, rear = hl - 2.5, -hl + 3
    o = hw + .005
    s = side([(-hl, .32), (hl, .32), (hl, h - .16), (hl - .16, h), (-hl + .1, h), (-hl, h - .1)], hw, PAINT, 'paint')
    s = s + top_face(-hl + .1, hl - .16, -hw + .01, hw - .01, h + .004, ROOF)
    doors = [(hl - 1.4, hl - .3), (-.65, .65)]
    for y, gaps in ((o, ()), (-o, doors)):
        s = s + strip(-hl, hl, .32, .5, y, paint(140), gaps, mat='paint')
        s = s + strip(-hl, hl, .9, 1.08, y, ACCENT, gaps, mat='paint')
        s = s + strip(-hl, hl, 2.66, 2.78, y, ACCENT, gaps, mat='paint')
    # the window band, broken by pillars (and the middle door on the right)
    w0, w1 = -hl + .45, hl - 1.45
    n = int((w1 - w0) / 1.35)
    step = (w1 - w0) / n
    for k in range(n):
        a, b = w0 + k * step + .07, w0 + (k + 1) * step - .07
        s = s + face_y(a, b, 1.2, 2.58, o, LIT, True, 'glass')
        f = strip(a, b, 1.2, 2.58, -o, LIT, [(-.72, .72)], True, 'glass')
        if f is not None:
            s = s + f
    # the driver's window on the left, the doors on the right
    s = s + face_y(hl - 1.35, hl - .25, 1.15, 2.6, o, LIT, True, 'glass')
    for d0, d1 in doors:
        s = s + face_y(d0, d1, .4, 2.62, -o, DOOR) + face_y(d0 + .07, d1 - .07, .5, 2.55, -o - .004, GLASS, mat='glass')
    s = s + face_y(hl - 2.6, hl - 1.6, 2.3, 2.52, -o - .006, '#15171A')  # the destination on the side
    for sg in (1, -1):
        s = s + box(hl + .02, sg * (hw + .04), 1.9, hl + .22, sg * (hw + .2), 2.4, BUMPER)
    s = s + box(-1.6, -hw * .62, h, 1.3, hw * .62, h + .24, '#D4D8DC') + box(-hl + 1.5, -.4, h, -hl + 2.3, .4, h + .06, '#B9BEC3')
    # the front: windscreen, the destination display and route number in amber, headlights
    s = s + box(hl - .02, -hw + .03, .32, hl + .1, hw - .03, .58, BUMPER)
    s = s + front_face(hl + .01, -hw + .1, hw - .1, 1.0, 2.6, GLASS, mat='glass')
    s = s + front_face(hl + .01, -hw + .15, hw - .15, 2.66, h - .2, '#15171A')
    s = s + front_face(hl + .015, -hw + .7, hw - .4, 2.74, h - .28, LED, True) + front_face(hl + .015, -hw + .3, -hw + .55, 2.74, h - .28, LED, True)
    s = s + front_face(hl + .015, -hw + .15, -hw + .55, .62, .78, HEAD, True) + front_face(hl + .015, hw - .55, hw - .15, .62, .78, HEAD, True)
    # the back: a window, the engine grille, the route number, tall tail lamps
    s = s + box(-hl - .1, -hw + .03, .32, -hl + .02, hw - .03, .55, BUMPER)
    s = s + rear_face(-hl - .01, -hw + .35, hw - .35, 1.75, 2.55, GLASS, mat='glass') + rear_face(-hl - .01, -.75, .75, .62, 1.2, '#2A2E33')
    s = s + rear_face(-hl - .015, -.35, .35, 2.62, 2.85, LED, True)
    s = s + rear_face(-hl - .01, -hw + .08, -hw + .3, .62, 1.5, TAIL, True) + rear_face(-hl - .01, hw - .3, hw - .08, .62, 1.5, TAIL, True)
    return s + wheels((front, rear), hw - .165, .5, .35, hw)


def kei():
    """A kei car, 3.4 m: a stubby hood, a tall glasshouse with near-vertical sides under a flat roof (in the livery's
    second color: two-tone), tall tail lamps up the rear corners and small wheels pushed out to the ends."""
    hl, hw, h = 1.7, .74, 1.7
    belt = h * .5
    gw = hw * .95
    top = h - .09
    xs = hl - .36 - .59 * (top - belt) / (h - belt)  # where the windscreen meets the roof
    s = side([(-hl, .26), (hl, .26), (hl, .6), (hl - .34, belt), (-hl, belt)], hw, PAINT, 'paint')
    s = s + side([(-hl + .03, belt), (hl - .36, belt), (xs, top), (-hl + .1, top)], gw, GLASS, 'glass')
    s = s + box(-hl + .08, -gw - .012, top, xs + .03, gw + .012, h, ACCENT, mat='paint')
    # thick pillars make it a box; a dark rubbing strip low down
    s = s + side_faces(-.12, .04, belt, top, gw + .004, PAINT, mat='paint') + side_faces(-hl + .1, -hl + .42, belt, top, gw + .004, PAINT, mat='paint')
    s = s + side_faces(-hl + .1, hl - .1, .3, .38, hw + .004, paint(179), mat='paint')
    for sg in (1, -1):
        s = s + box(hl - .5, sg * (hw - .02), belt + .02, hl - .4, sg * (hw + .14), belt + .18, PAINT, mat='paint')
        y0, y1 = sorted((sg * (hw - .24), sg * (hw - .06)))
        s = s + box(-hl - .01, y0, .5, -hl + .04, y1, belt + .32, TAIL, emit=True)
    s = s + box(hl - .02, -hw + .05, .25, hl + .1, hw - .05, .48, BUMPER)
    s = s + front_face(hl + .005, -hw + .15, -hw + .55, .48, .6, HEAD, True) + front_face(hl + .005, hw - .55, hw - .15, .48, .6, HEAD, True)
    s = s + front_face(hl + .005, -.17, .17, .48, .58, '#3A3F45')
    s = s + box(-hl - .08, -hw + .05, .26, -hl + .02, hw - .05, .46, BUMPER) + rear_face(-hl - .005, -.22, .22, .5, .62, PLATE)
    return s + wheels((hl - .52, -hl + .5), hw - .09, .29, .22, hw)


def bike(hl, y=0.0):
    """A small motorbike [hl] meters either side of its middle, at [y]: wheels, the engine, tank and seat, the raked
    fork, handlebars and a headlamp in the livery's second color (no rider: the game seats one)."""
    wf, wr = hl - .42, -hl + .6
    s = wheel(wf, y, .29, .1) + wheel(wr, y, .29, .1)
    s = s + box(wr - .1, y - .12, .28, wf - .35, y + .12, .55, FRAME)
    s = s + box(wr - .16, y - .13, .58, wr + .22, y + .13, .64, PAINT, mat='paint') + box(-.02, y - .15, .58, .38, y + .15, .82, PAINT, mat='paint')
    s = s + box(-.6, y - .14, .64, 0, y + .14, .74, '#1E1E1E') + box(-.62, y - .21, .3, -.15, y - .13, .4, CHROME, mat='metal')
    s = s + side([(wf - .04, .29), (wf + .04, .29), (wf - .2, 1.02), (wf - .28, 1.02)], .035, FRAME).move(0, y, 0)
    s = s + box(wf - .32, y - .39, .98, wf - .26, y + .39, 1.03, FRAME)
    s = s + box(wf - .22, y - .09, .78, wf - .1, y + .09, .94, ACCENT, mat='paint')
    s = s + front_face(wf - .095, y - .07, y + .07, .81, .92, HEAD, True) + rear_face(wr - .17, y - .07, y + .07, .58, .64, TAIL, True)
    return s


def motorbike():
    """A motorbike, 1.9 m."""
    return bike(.95)


def moped(delivery=False):
    """A step-through moped, 1.8 m: a leg shield and floorboard between small wheels, the seat over the engine; a
    delivery rider's has a box on the rack in the livery's second color."""
    hl = .9
    wf, wr = hl - .3, -hl + .32
    s = wheel(wf, 0, .23, .1) + wheel(wr, 0, .23, .1)
    s = s + box(-.3, -.16, .26, .3, .16, .34, FRAME)
    s = s + side([(wr - .14, .3), (-.26, .3), (-.2, .64), (wr - .02, .7), (wr - .2, .54)], .16, PAINT, 'paint')
    s = s + box(-.74, -.15, .7, -.18, .15, .78, '#1E1E1E')
    s = s + side([(.24, .28), (.46, .3), (wf - .1, .98), (wf - .26, 1.0)], .2, PAINT, 'paint')
    if delivery:
        s = s + box(-1.02, -.24, .8, -.54, .24, 1.28, ACCENT, mat='paint') + top_face(-1.0, -.56, -.22, .22, 1.284, accent(255))
    s = s + box(wf - .3, -.36, .98, wf - .22, .36, 1.03, FRAME) + box(wf - .28, -.11, .9, wf - .14, .11, 1.05, PAINT, mat='paint')
    s = s + front_face(wf - .135, -.07, .07, .93, 1.02, HEAD, True) + rear_face(wr - .21, -.06, .06, .55, .62, TAIL, True)
    return s


def tricycle():
    """Manila's tricycle, 2.5 m: a small motorbike with a covered sidecar bolted on its right, the curb side where
    traffic keeps right (tricycle_l is its mirror image). The sidecar's roof reaches over the rider, with the drivers'
    association signboard on top; its trim is the livery's second color."""
    hl, hw, h = 1.25, .75, 1.75
    rz = h * .9
    car = -(hw - .46)  # the sidecar's middle, right of the vehicle's
    s = bike(hl, hw - .27)
    cw = .44
    side_car = side([(-hl + .1, .24), (.52, .24), (.76, .46), (.7, .8), (-hl + .1, .82)], cw, PAINT, 'paint')
    side_car = side_car + side([(-hl + .1, .8), (.6, .8), (.38, rz), (-hl + .1, rz)], cw, PAINT, 'paint') + screen(.6, .8, .38, rz, cw - .06)
    s = s + side_car.move(0, car, 0)
    o = cw + .004
    door = [(-.75, .1)]
    for y, gaps in ((car + o, ()), (car - o, door)):
        s = s + face_y(-hl + .1, .55, .3, .38, y, CHROME, mat='metal')
        s = s + strip(-hl + .1, .58, .66, .76, y, ACCENT, gaps, mat='paint')
    # a doorway on the curb side, a small window toward the bike, tail lamps
    s = s + face_y(-.75, .1, .52, rz - .1, car - o, INSIDE) + face_y(-.85, .15, .95, rz - .12, car + o, INSIDE)
    s = s + rear_face(-hl + .09, car - cw + .06, car - cw + .2, .5, .68, TAIL, True) + rear_face(-hl + .09, car + cw - .2, car + cw - .06, .5, .68, TAIL, True)
    s = s + wheel(-.35, car - cw - .03, .27, .14)
    # the roof over the sidecar, reaching across over the rider's head, its edges trimmed
    c0, c1 = car - .46, car + .46
    s = s + box(-hl + .1, c0, rz, .5, c1, rz + .08, PAINT, mat='paint')
    s = s + face_y(-hl + .1, .5, rz + .005, rz + .075, c0 - .004, ACCENT, mat='paint')
    s = s + strip(-hl + .1, .5, rz + .005, rz + .075, c1 + .004, ACCENT, [(-.72, .2)], mat='paint')
    b0, b1 = c1 - .02, hw - .27 + .22
    s = s + box(-.72, b0, rz - .01, .2, b1, rz + .07, PAINT, mat='paint') + face_y(-.72, .2, rz - .005, rz + .065, b1 + .004, ACCENT, mat='paint')
    # the association's signboard
    s = s + box(.4, car - .34, rz + .08, .47, car + .34, rz + .28, '#F7F3E6') + front_face(.475, car - .28, car + .28, rz + .14, rz + .21, ACCENT, mat='paint')
    return s


def mirrored(shape):
    """The same, its left and right swapped (for cities where traffic keeps left)."""
    return shape.scale(1, -1, 1)


# the game picks by style: 0 sedan (a hatchback when short), taxi, 1 pickup, 2 van, 3 sports, 4 jeepney, 5 tricycle,
# 6 kei car, 7 bus, 8 motorbike, 9 SUV, 10 box truck, 11 modern jeepney, 12 moped (a delivery rider's with a box)
CARS = {
    'sedan': [gen(sedan())],
    'hatchback': [gen(hatchback())],
    'taxi': [gen(taxi())],
    'pickup': [gen(pickup())],
    'van': [gen(van())],
    'sports': [gen(sports())],
    'suv': [gen(suv())],
    'box_truck': [gen(box_truck())],
    'jeepney': [gen(jeepney())],
    'modern_jeep': [gen(modern_jeep())],
    'modern_jeep_l': [gen(mirrored(modern_jeep()))],
    'tricycle': [gen(tricycle())],
    'tricycle_l': [gen(mirrored(tricycle()))],
    'kei': [gen(kei())],
    'bus': [gen(bus())],
    'bus_l': [gen(mirrored(bus()))],
    'motorbike': [gen(motorbike())],
    'moped': [gen(moped())],
    'moped_box': [gen(moped(True))],
}
