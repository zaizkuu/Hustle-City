"""Cars: the everyday traffic of every city (sedans, hatchbacks, taxis, pickups, vans, sports cars, SUVs and box trucks).

Each is modelled at a typical size in meters (x forward, y to the left, z up, its middle at the origin on the ground)
and the game stretches it to each car's own length, width and height. Paint is white with the 'paint' material: the
game tints it with the car's color (grey paint comes out darker). Lenses are emissive: the game lights the headlights
and tail lights at night (and the tail lights alone when braking) and leaves them as plain lenses by day.
"""
import math

import numpy as np

from products import gen
from shapes import _tris, box, cylinder, disc, prism

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


def side(profile, w, color, mat='', emit=False):
    """A side profile [(x, z), ...] extruded across the car from -w to w."""
    return prism(profile, -w, w, color, mat=mat, emit=emit).rotx(90)


def quad(a, b, c, d, color, emit=False):
    """A flat quad (two triangles), facing the side its corners run counterclockwise round."""
    p = np.array([a, b, c, a, c, d], dtype=np.float64)
    n = np.cross(p[1] - p[0], p[2] - p[0])
    n = n / (np.linalg.norm(n) or 1)
    return _tris(p, np.tile(n, (6, 1)), color, '', emit)


def front_face(x, y0, y1, z0, z1, color, emit=False):
    """A face across the car at [x], looking forward (+x)."""
    return quad((x, y0, z0), (x, y1, z0), (x, y1, z1), (x, y0, z1), color, emit)


def rear_face(x, y0, y1, z0, z1, color, emit=False):
    """The same looking back (-x)."""
    return quad((x, y1, z0), (x, y0, z0), (x, y0, z1), (x, y1, z1), color, emit)


def side_faces(x0, x1, z0, z1, w, color, emit=False):
    """A strip on both sides of the car (at y = +-[w]), looking out."""
    return quad((x0, w, z0), (x0, w, z1), (x1, w, z1), (x1, w, z0), color, emit) + \
        quad((x0, -w, z0), (x1, -w, z0), (x1, -w, z1), (x0, -w, z1), color, emit)


def arch(x, z, r, w, sgn):
    """The dark wheel arch on the body's side behind a wheel: a half disc looking out to side [sgn]."""
    pts = []
    n = 6
    for k in range(n):
        a0 = math.pi * k / n; a1 = math.pi * (k + 1) / n
        c = (x, sgn * w, z)
        p0 = (x + r * math.cos(a0), sgn * w, z + r * math.sin(a0))
        p1 = (x + r * math.cos(a1), sgn * w, z + r * math.sin(a1))
        pts += [c, p0, p1] if sgn < 0 else [c, p1, p0]
    p = np.array(pts, dtype=np.float64)
    return _tris(p, np.tile([0, sgn, 0], (len(pts), 1)), BLACK, '')


def wheels(xs, y, r, w, body_w):
    """Four wheels (tire and rim cap) at x in [xs], their centers [y] out from the middle, under arches in the body's side."""
    s = None
    for x in xs:
        for sg in (1, -1):
            t = cylinder(r, -w / 2, w / 2, TIRE, n=8).rotx(90).move(x, sg * y, r)
            cap = disc(r * .62, 0, RIM, n=6).rotx(-90 if sg > 0 else 90).move(x, sg * (y + w / 2 + .004), r)
            part = t + cap + arch(x, r, r + .07, body_w + .003, sg)
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
    s = s + side_faces(-3.4, 1.4, 1.8, 2.1, 1.203, PAINT)
    s = s + box(-3.6, -.8, .35, 3.4, .8, .62, BLACK)
    s = s + lights(3.62, -3.64, 1.1, .8, .72, .7, hf=.14, hr=.18, d=.08) + ends(3.6, -3.6, 1.12, zb=.38, zt=.6, grille=(.6, .65, 1.25))
    s = s + mirrors(3.0, 1.9, w)
    return s + wheels((2.55, -2.25), .98, .46, .3, w)


# the game picks by style: 0 sedan (a hatchback when short), taxi, 1 pickup, 2 van, 3 sports, 9 SUV, 10 box truck
CARS = {
    'sedan': [gen(sedan())],
    'hatchback': [gen(hatchback())],
    'taxi': [gen(taxi())],
    'pickup': [gen(pickup())],
    'van': [gen(van())],
    'sports': [gen(sports())],
    'suv': [gen(suv())],
    'box_truck': [gen(box_truck())],
}
