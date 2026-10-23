"""A small modelling kit for products no free pack has (phones, rings, clothes on hangers, sneakers...).

Shapes are built in the game's own frame, in meters: x across, y toward the front (the customer), z up. Each one is a
triangle soup with normals, sRGB colors (0..1) and a material name per triangle, the same as gltf.read returns, so
manifests can mix them freely with model files. Combine shapes with +, and place them with move / scale / rotations.
"""
import math

import numpy as np


def rgb(hexcolor, a=1.0):
    """'#rrggbb' or 0xrrggbb to an sRGB color (0..1)."""
    if isinstance(hexcolor, str):
        hexcolor = int(hexcolor.lstrip('#'), 16)
    return ((hexcolor >> 16 & 255) / 255.0, (hexcolor >> 8 & 255) / 255.0, (hexcolor & 255) / 255.0, a)


def mix(c1, c2, t):
    a, b = rgb(c1) if not isinstance(c1, tuple) else c1, rgb(c2) if not isinstance(c2, tuple) else c2
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(4))


def _col(c):
    return rgb(c) if not isinstance(c, tuple) else (c if len(c) == 4 else (*c, 1.0))


class Shape:
    def __init__(self, pos=None, nrm=None, col=None, mat=None, emit=None):
        self.pos = np.zeros((0, 3)) if pos is None else np.asarray(pos, dtype=np.float64)
        self.nrm = np.zeros((0, 3)) if nrm is None else np.asarray(nrm, dtype=np.float64)
        self.col = np.zeros((0, 4)) if col is None else np.asarray(col, dtype=np.float64)
        self.mat = [] if mat is None else list(mat)
        self.emit = [] if emit is None else list(emit)

    def __add__(self, o):
        return Shape(np.concatenate([self.pos, o.pos]), np.concatenate([self.nrm, o.nrm]), np.concatenate([self.col, o.col]),
                     self.mat + o.mat, self.emit + o.emit)

    def copy(self):
        return Shape(self.pos.copy(), self.nrm.copy(), self.col.copy(), self.mat, self.emit)

    def move(self, dx=0.0, dy=0.0, dz=0.0):
        s = self.copy()
        s.pos += np.array([dx, dy, dz])
        return s

    def scale(self, sx, sy=None, sz=None):
        sy = sx if sy is None else sy
        sz = sx if sz is None else sz
        s = self.copy()
        k = np.array([sx, sy, sz])
        s.pos *= k
        s.nrm /= k
        s.nrm /= np.maximum(np.linalg.norm(s.nrm, axis=1, keepdims=True), 1e-9)
        if sx * sy * sz < 0:
            s._flip()
        return s

    def _flip(self):
        t = self.pos.reshape(-1, 3, 3)[:, ::-1].reshape(-1, 3)
        n = self.nrm.reshape(-1, 3, 3)[:, ::-1].reshape(-1, 3)
        c = self.col.reshape(-1, 3, 4)[:, ::-1].reshape(-1, 4)
        self.pos, self.nrm, self.col = t.copy(), n.copy(), c.copy()

    def _rot(self, m):
        s = self.copy()
        s.pos = s.pos @ m.T
        s.nrm = s.nrm @ m.T
        return s

    def turn(self, m):
        """Applies a 3x3 rotation (or axis permutation) matrix."""
        return self._rot(np.asarray(m, dtype=np.float64))

    def rotx(self, deg):
        a = math.radians(deg); c, sn = math.cos(a), math.sin(a)
        return self._rot(np.array([[1, 0, 0], [0, c, -sn], [0, sn, c]]))

    def roty(self, deg):
        a = math.radians(deg); c, sn = math.cos(a), math.sin(a)
        return self._rot(np.array([[c, 0, sn], [0, 1, 0], [-sn, 0, c]]))

    def rotz(self, deg):
        a = math.radians(deg); c, sn = math.cos(a), math.sin(a)
        return self._rot(np.array([[c, -sn, 0], [sn, c, 0], [0, 0, 1]]))

    def color(self, c):
        """Recolors everything."""
        s = self.copy()
        s.col[:] = _col(c)
        return s

    def glow(self):
        s = self.copy()
        s.emit = [True] * len(s.emit)
        return s

    def bounds(self):
        return self.pos.min(0), self.pos.max(0)


def _tris(points, normals, color, mat, emit=False):
    """Triangles from (n*3, 3) points and normals, one color."""
    n = len(points) // 3
    return Shape(points, normals, np.tile(_col(color), (len(points), 1)), [mat] * n, [emit] * n)


def _quad_tris(a, b, c, d):
    return [a, b, c, a, c, d]


def group(*shapes):
    out = Shape()
    for s in shapes:
        out = out + s
    return out


def box(x0, y0, z0, x1, y1, z1, color, mat='', r=0.0, seg=1, emit=False):
    """A box, its edges rounded by [r] (seg steps per rounded edge; 1 = a chamfer)."""
    lo = np.array([min(x0, x1), min(y0, y1), min(z0, z1)], dtype=np.float64)
    hi = np.array([max(x0, x1), max(y0, y1), max(z0, z1)], dtype=np.float64)
    c = (lo + hi) / 2
    h = (hi - lo) / 2
    r = min(r, *(h * .999))
    inner = h - r
    pts, nrms = [], []
    if r <= 0:
        # six flat faces
        for ax in range(3):
            for sgn in (-1, 1):
                u, v = [(1, 2), (2, 0), (0, 1)][ax]
                corners = []
                for su, sv in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
                    p = np.zeros(3)
                    p[ax] = sgn * h[ax]; p[u] = su * h[u]; p[v] = sv * h[v]
                    corners.append(c + p)
                if sgn < 0:
                    corners = corners[::-1]
                n = np.zeros(3); n[ax] = sgn
                pts += _quad_tris(*corners)
                nrms += [n] * 6
        return _tris(np.array(pts), np.array(nrms), color, mat, emit)

    # sample each axis: the flat middle plus [seg] steps around each rounded edge
    def samples(k):
        angles = [math.pi / 2 * i / seg for i in range(seg + 1)]
        side = [inner[k] + r * math.sin(a) for a in angles]  # inner .. h
        return [-v for v in side[::-1]] + side

    def place(p):
        q = np.clip(p, -inner, inner)
        d = p - q
        ln = np.linalg.norm(d)
        if ln < 1e-12:
            return p, None
        n = d / ln
        return q + n * r, n

    for ax in range(3):
        u, v = [(1, 2), (2, 0), (0, 1)][ax]
        su, sv = samples(u), samples(v)
        for sgn in (-1, 1):
            grid = []
            for a in su:
                row = []
                for b in sv:
                    p = np.zeros(3)
                    p[ax] = sgn * h[ax]; p[u] = a; p[v] = b
                    q, n = place(p)
                    if n is None:
                        n = np.zeros(3); n[ax] = sgn
                    row.append((c + q, n))
                grid.append(row)
            for i in range(len(su) - 1):
                for j in range(len(sv) - 1):
                    quad = [grid[i][j], grid[i + 1][j], grid[i + 1][j + 1], grid[i][j + 1]]
                    if sgn < 0:
                        quad = quad[::-1]
                    for k in (0, 1, 2, 0, 2, 3):
                        pts.append(quad[k][0]); nrms.append(quad[k][1])
    s = _tris(np.array(pts), np.array(nrms), color, mat, emit)
    # drop slivers where rounded faces meet
    return _clean(s)


def _clean(s):
    t = s.pos.reshape(-1, 3, 3)
    area = np.linalg.norm(np.cross(t[:, 1] - t[:, 0], t[:, 2] - t[:, 0]), axis=1)
    keep = area > 1e-12
    k3 = np.repeat(keep, 3)
    return Shape(s.pos[k3], s.nrm[k3], s.col[k3], [m for m, k in zip(s.mat, keep) if k], [e for e, k in zip(s.emit, keep) if k])


def lathe(profile, color, n=12, mat='', colors=None, stripes=None, smooth=True, cap=True, emit=False, arc=360.0, phase=0.0):
    """
    Spins a profile of (radius, z) points (bottom to top) around the z axis. A repeated point makes a crease.
    [colors] colors each band of the profile; [stripes] alternates two colors around it instead. Closed with flat
    caps where the profile doesn't reach the axis (unless [cap] is False).
    """
    prof = [(float(r), float(z)) for r, z in profile]
    full = abs(arc - 360.0) < 1e-6
    angs = [math.radians(phase + arc * i / n) for i in range(n + 1)]
    # profile segment normals (in the r-z plane), with creases where points repeat
    segs = []
    for i in range(len(prof) - 1):
        (r0, z0), (r1, z1) = prof[i], prof[i + 1]
        dr, dz = r1 - r0, z1 - z0
        ln = math.hypot(dr, dz)
        segs.append(None if ln < 1e-12 else (dz / ln, -dr / ln))
    def vnorm(i, side):
        # normal at profile point i for the segment on [side] (-1 before, +1 after)
        own = segs[i] if side > 0 else segs[i - 1]
        if not smooth:
            return own
        other = segs[i - 1] if side > 0 and i > 0 else (segs[i] if side < 0 and i < len(segs) else None)
        if other is None:
            return own
        a = (own[0] + other[0], own[1] + other[1])
        ln = math.hypot(*a)
        return own if ln < 1e-9 or (own[0] * other[0] + own[1] * other[1]) < .5 else (a[0] / ln, a[1] / ln)
    out = Shape()
    for i, sn in enumerate(segs):
        if sn is None:
            continue
        (r0, z0), (r1, z1) = prof[i], prof[i + 1]
        n0, n1 = vnorm(i, 1), vnorm(i + 1, -1)
        pts, nrms, cols = [], [], []
        for j in range(n):
            a, b = angs[j], angs[j + 1]
            col = _col(colors[i]) if colors else (_col(stripes[j % 2]) if stripes else _col(color))
            def P(r, z, t):
                return np.array([r * math.cos(t), r * math.sin(t), z])
            def N(nn, t):
                return np.array([nn[0] * math.cos(t), nn[0] * math.sin(t), nn[1]])
            quad = [(P(r0, z0, a), N(n0, a)), (P(r0, z0, b), N(n0, b)), (P(r1, z1, b), N(n1, b)), (P(r1, z1, a), N(n1, a))]
            for k in (0, 1, 2, 0, 2, 3):
                pts.append(quad[k][0]); nrms.append(quad[k][1]); cols.append(col)
        sh = Shape(np.array(pts), np.array(nrms), np.array(cols), [mat] * (len(pts) // 3), [emit] * (len(pts) // 3))
        out = out + _clean(sh)
    if cap and full:
        for idx, up in ((0, -1), (len(prof) - 1, 1)):
            r, z = prof[idx]
            if r < 1e-6:
                continue
            ccol = _col(colors[0] if idx == 0 else colors[-1]) if colors else _col(color)
            pts = []
            for j in range(n):
                a, b = angs[j], angs[j + 1]
                pa = np.array([r * math.cos(a), r * math.sin(a), z]); pb = np.array([r * math.cos(b), r * math.sin(b), z])
                ctr = np.array([0, 0, z])
                pts += [ctr, pa, pb] if up > 0 else [ctr, pb, pa]
            out = out + _tris(np.array(pts), np.tile([0, 0, up], (len(pts), 1)), ccol, mat, emit)
    return out


def cylinder(r, z0, z1, color, n=12, mat='', top=None, emit=False):
    s = lathe([(r, z0), (r, z1)], color, n=n, mat=mat, smooth=False, emit=emit)
    if top is not None:
        s = lathe([(r, z0), (r, z1)], color, n=n, mat=mat, smooth=False, cap=False, emit=emit) + disc(r, z1, top, n=n, mat=mat) + disc(r, z0, color, n=n, mat=mat, up=False)
    return s


def disc(r, z, color, n=12, mat='', up=True):
    pts = []
    for j in range(n):
        a, b = 2 * math.pi * j / n, 2 * math.pi * (j + 1) / n
        pa = [r * math.cos(a), r * math.sin(a), z]; pb = [r * math.cos(b), r * math.sin(b), z]
        pts += [[0, 0, z], pa, pb] if up else [[0, 0, z], pb, pa]
    return _tris(np.array(pts, dtype=np.float64), np.tile([0, 0, 1 if up else -1], (len(pts), 1)), color, mat)


def sphere(rx, ry, rz, color, nu=10, nv=6, mat='', emit=False):
    """An ellipsoid centered on the origin."""
    prof = [(math.sin(math.pi * i / nv), -math.cos(math.pi * i / nv)) for i in range(nv + 1)]
    s = lathe(prof, color, n=nu, mat=mat, emit=emit)
    return s.scale(rx, ry, rz)


def torus(R, r, color, nu=16, nv=6, mat='', arc=360.0, phase=0.0):
    """A ring around the z axis (radius R, tube radius r); [arc] degrees of it, starting at [phase]."""
    pts, nrms = [], []
    for i in range(nu):
        a0 = math.radians(phase + arc * i / nu); a1 = math.radians(phase + arc * (i + 1) / nu)
        for j in range(nv):
            b0 = 2 * math.pi * j / nv; b1 = 2 * math.pi * (j + 1) / nv
            def P(a, b):
                return np.array([(R + r * math.cos(b)) * math.cos(a), (R + r * math.cos(b)) * math.sin(a), r * math.sin(b)])
            def N(a, b):
                return np.array([math.cos(b) * math.cos(a), math.cos(b) * math.sin(a), math.sin(b)])
            quad = [(a0, b0), (a1, b0), (a1, b1), (a0, b1)]
            for k in (0, 1, 2, 0, 2, 3):
                pts.append(P(*quad[k])); nrms.append(N(*quad[k]))
    return _tris(np.array(pts), np.array(nrms), color, mat)


def _triangulate(poly):
    """Ear clipping for a simple polygon (counterclockwise list of (x, y))."""
    idx = list(range(len(poly)))
    area = sum(poly[i][0] * poly[(i + 1) % len(poly)][1] - poly[(i + 1) % len(poly)][0] * poly[i][1] for i in range(len(poly)))
    if area < 0:
        idx.reverse()
    out = []
    def inside(p, a, b, c):
        def s(p1, p2, p3):
            return (p1[0] - p3[0]) * (p2[1] - p3[1]) - (p2[0] - p3[0]) * (p1[1] - p3[1])
        d1, d2, d3 = s(p, a, b), s(p, b, c), s(p, c, a)
        return not ((d1 < 0 or d2 < 0 or d3 < 0) and (d1 > 0 or d2 > 0 or d3 > 0))
    guard = 0
    while len(idx) > 3 and guard < 10000:
        guard += 1
        for k in range(len(idx)):
            i0, i1, i2 = idx[k - 1], idx[k], idx[(k + 1) % len(idx)]
            a, b, c = poly[i0], poly[i1], poly[i2]
            cross = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
            if cross <= 1e-12:
                continue
            if any(inside(poly[j], a, b, c) for j in idx if j not in (i0, i1, i2)):
                continue
            out.append((i0, i1, i2))
            idx.pop(k)
            break
        else:
            break
    if len(idx) == 3:
        out.append(tuple(idx))
    return out


def prism(poly, z0, z1, color, mat='', side=None, emit=False):
    """A flat shape: polygon [poly] of (x, y) points extruded from z0 to z1 (sides colored [side] if given)."""
    tris = _triangulate(poly)
    pts, nrms = [], []
    for a, b, c in tris:
        for z, up in ((z1, 1), (z0, -1)):
            seq = (a, b, c) if up > 0 else (a, c, b)
            for k in seq:
                pts.append([poly[k][0], poly[k][1], z]); nrms.append([0, 0, up])
    caps = _tris(np.array(pts, dtype=np.float64), np.array(nrms, dtype=np.float64), color, mat, emit)
    # sides, wound so they face out whichever way the polygon runs
    area = sum(poly[i][0] * poly[(i + 1) % len(poly)][1] - poly[(i + 1) % len(poly)][0] * poly[i][1] for i in range(len(poly)))
    sgn = 1 if area > 0 else -1
    pts, nrms = [], []
    for i in range(len(poly)):
        p, q = poly[i], poly[(i + 1) % len(poly)]
        dx, dy = q[0] - p[0], q[1] - p[1]
        ln = math.hypot(dx, dy)
        if ln < 1e-12:
            continue
        n = [dy / ln * sgn, -dx / ln * sgn, 0]
        quad = [[p[0], p[1], z0], [q[0], q[1], z0], [q[0], q[1], z1], [p[0], p[1], z1]]
        if sgn < 0:
            quad = quad[::-1]
        for k in (0, 1, 2, 0, 2, 3):
            pts.append(quad[k]); nrms.append(n)
    sides = _tris(np.array(pts, dtype=np.float64), np.array(nrms, dtype=np.float64), side if side is not None else color, mat, emit)
    return caps + sides


def gem(r, color, mat='glass'):
    """A cut stone: an octahedron, taller below."""
    top = [(r, 0), (0, r * .55)]
    s = lathe([(0, -r * .9), (r, 0)], color, n=8, mat=mat, smooth=False, cap=False) + lathe(top, color, n=8, mat=mat, smooth=False, cap=False)
    return s.move(0, 0, r * .9)


def tube(points, r, color, n=6, mat=''):
    """A thin tube along a polyline of (x, y, z) points (chains, wires, hangers)."""
    out = Shape()
    pts = [np.array(p, dtype=np.float64) for p in points]
    for i in range(len(pts) - 1):
        a, b = pts[i], pts[i + 1]
        d = b - a
        ln = np.linalg.norm(d)
        if ln < 1e-9:
            continue
        seg = lathe([(r, 0), (r, ln)], color, n=n, mat=mat, smooth=False, cap=False)
        # turn z to d
        z = d / ln
        x = np.cross([0, 0, 1], z) if abs(z[2]) < .999 else np.array([1.0, 0, 0])
        x /= np.linalg.norm(x)
        y = np.cross(z, x)
        m = np.stack([x, y, z], axis=1)
        seg = seg._rot(m)
        out = out + seg.move(*a)
        # a ball at each joint keeps bends closed
        out = out + sphere(r, r, r, color, nu=n, nv=3, mat=mat).move(*b)
    return out
