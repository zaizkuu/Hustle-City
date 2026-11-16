"""Modeling kit for Hustle City's Blender asset library.

Runs inside Blender (sent over the MCP socket by bl.py, in front of an asset script).

Conventions
  * 1 Blender unit = 1 meter = 1 interior tile.
  * Z is up. An asset's FRONT faces -Y (Blender's front view); the game's y axis points the other way
    (south), so the exporter flips y.
  * Each asset is one mesh whose origin sits at the middle of its footprint on the floor.
  * Flat colors only, no textures. A material is named <KIND>_<RRGGBB>; KIND is the game's shader
    material (render/Scene3D.kt `Mat`), so the engine can keep drawing its procedural brick, wood, glass...
"""
import math

import bmesh
import bpy
from mathutils import Euler, Matrix, Vector

# ---------------------------------------------------------------------------------------------- materials

_KIND = {
    # kind: (roughness, metallic)
    "PLAIN": (.6, 0), "PAINT": (.45, 0), "PLASTIC": (.35, 0), "WOOD": (.65, 0), "METAL": (.32, .85),
    "GLASS": (.06, 0), "FABRIC": (.95, 0), "BRICK": (.9, 0), "STONE": (.85, 0), "CONCRETE": (.9, 0),
    "PLASTER": (.9, 0), "ROOF": (.8, 0), "TILE": (.3, 0), "FOLIAGE": (.85, 0), "ASPHALT": (.95, 0),
    "RUBBER": (.9, 0), "FOOD": (.6, 0), "PAPER": (.9, 0), "EMIT": (.4, 0), "SCREEN": (.15, 0),
}


def _lin(c):
    return c / 12.92 if c <= .04045 else ((c + .055) / 1.055) ** 2.4


def rgb(hexc):
    return (_lin(((hexc >> 16) & 255) / 255), _lin(((hexc >> 8) & 255) / 255), _lin((hexc & 255) / 255))


def M(hexc, kind="PAINT", alpha=None, emit=None):
    """Get or make the flat material for a color and a surface kind."""
    hexc &= 0xFFFFFF
    name = "{:s}_{:06X}".format(kind, hexc)
    m = bpy.data.materials.get(name)
    if m is not None:
        return m
    m = bpy.data.materials.new(name)
    try:
        m.use_nodes = True
    except Exception:
        pass
    nt = m.node_tree
    bsdf = next((n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED'), None)
    if bsdf is None:
        bsdf = nt.nodes.new("ShaderNodeBsdfPrincipled")
        out = next((n for n in nt.nodes if n.type == 'OUTPUT_MATERIAL'), None) or nt.nodes.new("ShaderNodeOutputMaterial")
        nt.links.new(bsdf.outputs[0], out.inputs[0])
    rough, metal = _KIND.get(kind, (.6, 0))
    col = rgb(hexc)
    bsdf.inputs["Base Color"].default_value = (*col, 1)
    bsdf.inputs["Roughness"].default_value = rough
    bsdf.inputs["Metallic"].default_value = metal
    if kind == "GLASS" and alpha is None:
        alpha = .42
    if kind in ("EMIT", "SCREEN") and emit is None:
        emit = 3.0 if kind == "EMIT" else 1.2
    if emit:
        bsdf.inputs["Emission Color"].default_value = (*col, 1)
        bsdf.inputs["Emission Strength"].default_value = emit
    if alpha is not None and alpha < 1:
        bsdf.inputs["Alpha"].default_value = alpha
        for attr, val in (("surface_render_method", 'BLENDED'), ("use_transparent_shadow", True)):
            try:
                setattr(m, attr, val)
            except Exception:
                pass
    m.diffuse_color = (*col, alpha if alpha is not None else 1)
    m.roughness = rough
    m.metallic = metal
    m["hc_kind"] = kind
    m["hc_color"] = "{:06X}".format(hexc)
    return m


# the game's own palette (data/Catalog.kt, render/BuildingPainter.kt)
WOOD = M(0x8B5A3C, "WOOD")
WOOD_L = M(0xB07A52, "WOOD")
WOOD_D = M(0x5D4037, "WOOD")
WOOD_X = M(0x3E2723, "WOOD")
STEEL = M(0xB8C0CA, "METAL")
CHROME = M(0xD9DEE4, "METAL")
IRON = M(0x2A2D31, "METAL")
GUN = M(0x5B6770, "METAL")
DARK = M(0x2E3238, "PLASTIC")
BLACK = M(0x1B1D21, "PLASTIC")
GREY = M(0x9AA3AD, "PLASTIC")
GREY_D = M(0x4A4F57, "PLASTIC")
WHITE = M(0xE9EDF2, "PAINT")
CREAM = M(0xF0ECE4, "PAINT")
GLASS = M(0x8EC5E8, "GLASS")
GLASS_D = M(0x3C5266, "GLASS", alpha=1)
RUBBER = M(0x22252A, "RUBBER")
RED = M(0xEB5757, "PAINT")
YELLOW = M(0xF2C94C, "PAINT")
GREEN = M(0x27AE60, "PAINT")
BLUE = M(0x2F80ED, "PAINT")
SKY = M(0x56CCF2, "PAINT")
PURPLE = M(0xBB6BD9, "PAINT")
VIOLET = M(0x9B51E0, "PAINT")
ORANGE = M(0xF2994A, "PAINT")
MINT = M(0x6FCF97, "PAINT")
PINK = M(0xF28AB2, "PAINT")
LEAF = M(0x3E8E4E, "FOLIAGE")
LEAF_L = M(0x5FB36A, "FOLIAGE")
LEAF_D = M(0x2C6E3F, "FOLIAGE")
SOIL = M(0x4A3728, "PLAIN")
TERRACOTTA = M(0xB0563C, "PLASTER")
CARDBOARD = M(0xC69A6B, "PAPER")
CARDBOARD_D = M(0xA67C52, "PAPER")
PAPER = M(0xF5F2EA, "PAPER")
SCREEN = M(0x56CCF2, "SCREEN")
SCREEN_D = M(0x14181D, "SCREEN", emit=0)


def F(hexc):
    return M(hexc, "FABRIC")


def P(hexc):
    return M(hexc, "PAINT")


def E(hexc, strength=3.0):
    return M(hexc, "EMIT", emit=strength)


# ---------------------------------------------------------------------------------------------- collections

def collection(name, parent=None):
    c = bpy.data.collections.get(name)
    if c is None:
        c = bpy.data.collections.new(name)
        (parent or bpy.context.scene.collection).children.link(c)
    return c


ROOT = collection("Hustle City")


def category(name):
    return collection(name, ROOT)


def remove_object(name):
    ob = bpy.data.objects.get(name)
    if ob is None:
        return
    me = ob.data
    bpy.data.objects.remove(ob, do_unlink=True)
    if me is not None and me.users == 0 and isinstance(me, bpy.types.Mesh):
        bpy.data.meshes.remove(me)


# ---------------------------------------------------------------------------------------------- curves

def _pchip(ts, vs):
    """Slopes for a monotone cubic through (ts, vs): smooth, and never overshooting the values it is given."""
    n = len(ts)
    if n < 2:
        return [0.0] * n
    d = [(vs[i + 1] - vs[i]) / (ts[i + 1] - ts[i]) for i in range(n - 1)]
    m = [0.0] * n
    m[0], m[-1] = d[0], d[-1]
    for i in range(1, n - 1):
        if d[i - 1] * d[i] > 0:
            w1 = 2 * (ts[i + 1] - ts[i]) + (ts[i] - ts[i - 1])
            w2 = (ts[i + 1] - ts[i]) + 2 * (ts[i] - ts[i - 1])
            m[i] = (w1 + w2) / (w1 / d[i - 1] + w2 / d[i])
    return m


def resample(rings, fine, axis='z'):
    """Loft rings with more put between them, about `fine` apart along the axis, every value on a smooth curve."""
    ai = 2 if axis == 'z' else 1
    R = [tuple(r) + ((r[3],) if len(r) < 5 else ()) for r in rings]
    R = [r + ((r[4],) if len(r) < 6 else ()) for r in R]
    R = [r + ((1.0,) if len(r) < 7 else ()) for r in R]
    ts = [0.0]
    for a, b in zip(R, R[1:]):
        ts.append(ts[-1] + max(abs(b[ai] - a[ai]), 1e-4))
    cols = list(zip(*R))
    slopes = [_pchip(ts, c) for c in cols]
    out = []
    for i in range(len(R) - 1):
        h = ts[i + 1] - ts[i]
        steps = max(1, int(round(h / fine)))
        for k in range(steps):
            u = k / steps
            h00 = 2 * u ** 3 - 3 * u ** 2 + 1
            h10 = u ** 3 - 2 * u ** 2 + u
            h01 = -2 * u ** 3 + 3 * u ** 2
            h11 = u ** 3 - u ** 2
            out.append(tuple(h00 * c[i] + h10 * h * s[i] + h01 * c[i + 1] + h11 * h * s[i + 1] for c, s in zip(cols, slopes)))
    out.append(R[-1])
    return out


def dome(ring, h, n=4, axis='z'):
    """Rings that round a loft's open end off into a cap of height h (negative: downward), from the end ring outward."""
    ai = 2 if axis == 'z' else 1
    r = list(ring) + ([ring[3]] if len(ring) < 5 else [])
    r = r + ([r[4]] if len(r) < 6 else [])
    out = []
    for k in range(1, n + 1):
        a = (math.pi / 2) * k / (n + .35)
        q = list(r)
        q[ai] = r[ai] + h * math.sin(a)
        c = math.cos(a)
        q[3], q[4], q[5] = r[3] * c, r[4] * c, r[5] * c
        out.append(tuple(q))
    return out


# ---------------------------------------------------------------------------------------------- the builder

class _Frame:
    """Transforms the verts made inside a `with` block.

    Verts are tracked with an integer layer rather than by index: bevels delete verts and BMesh then reuses their
    slots, so "everything after index n" is not "everything made since".  0 = made in the innermost open frame;
    a frame's id = made earlier in that frame, waiting for it to close; -1 = outside any frame.
    """

    def __init__(self, asset, mat):
        self.a = asset
        self.m = mat

    def __enter__(self):
        a = self.a
        lay = a.stamp
        parent = a.stack[-1]
        for v in a.bm.verts:
            if v[lay] == 0:
                v[lay] = parent
        a.frames += 1
        self.id = a.frames
        a.stack.append(self.id)
        return self

    def __exit__(self, *exc):
        a = self.a
        lay = a.stamp
        a.stack.pop()
        vs = [v for v in a.bm.verts if v[lay] == 0 or v[lay] == self.id]
        if vs:
            bmesh.ops.transform(a.bm, matrix=self.m, verts=vs)
            for v in vs:
                v[lay] = 0
        return False


class Asset:
    """Collects primitives into one mesh. Every primitive takes a material and returns nothing."""

    def __init__(self, name):
        self.name = name
        self.bm = bmesh.new()
        self.mats = []
        self.stamp = self.bm.verts.layers.int.new("hc_frame")
        self.stack = [-1]
        self.frames = 0

    # -- helpers

    def _mi(self, m):
        if m not in self.mats:
            self.mats.append(m)
        return self.mats.index(m)

    def _paint(self, verts, m, smooth=False):
        faces = {f for v in verts for f in v.link_faces}
        i = self._mi(m)
        for f in faces:
            f.material_index = i
            f.smooth = smooth
        return faces

    def at(self, x=0.0, y=0.0, z=0.0, rz=0.0, rx=0.0, ry=0.0, s=1.0, sx=1.0, sy=1.0, sz=1.0):
        """`with a.at(...):` moves, turns (degrees) and scales everything built inside."""
        m = Matrix.Translation((x, y, z)) @ Euler((math.radians(rx), math.radians(ry), math.radians(rz))).to_matrix().to_4x4()
        m = m @ Matrix.Diagonal((s * sx, s * sy, s * sz, 1))
        return _Frame(self, m)

    # -- primitives

    def box(self, x0, y0, z0, x1, y1, z1, m, bevel=0.0, seg=1):
        dx, dy, dz = abs(x1 - x0), abs(y1 - y0), abs(z1 - z0)
        mat = Matrix.Translation(((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)) @ Matrix.Diagonal((dx, dy, dz, 1))
        vs = bmesh.ops.create_cube(self.bm, size=1.0, matrix=mat)["verts"]
        self._paint(vs, m)
        if bevel > 0:
            b = min(bevel, .45 * min(dx, dy, dz))
            edges = list({e for v in vs for e in v.link_edges})
            res = bmesh.ops.bevel(self.bm, geom=edges, offset=b, segments=seg, affect='EDGES', profile=.5, clamp_overlap=True, material=-1)
            if seg > 1:
                for f in res["faces"]:
                    f.smooth = True

    def cbox(self, x, y, z0, w, d, h, m, bevel=0.0, seg=1):
        """Box by centre, size and base height."""
        self.box(x - w / 2, y - d / 2, z0, x + w / 2, y + d / 2, z0 + h, m, bevel, seg)

    def rod(self, p0, p1, r, m, n=10, r2=None, caps=True, smooth=True):
        """Cylinder (or cone, with r2) from point p0 to p1."""
        p0 = Vector(p0)
        p1 = Vector(p1)
        d = p1 - p0
        L = d.length
        if L < 1e-6:
            return
        rot = Vector((0, 0, 1)).rotation_difference(d.normalized()).to_matrix().to_4x4()
        mat = Matrix.Translation((p0 + p1) / 2) @ rot
        r2 = r if r2 is None else r2
        vs = bmesh.ops.create_cone(self.bm, cap_ends=caps, cap_tris=False, segments=n, radius1=r, radius2=max(r2, 1e-5),
                                   depth=L, matrix=mat)["verts"]
        faces = self._paint(vs, m, smooth)
        if smooth:
            axis = d.normalized()
            for f in faces:
                f.normal_update()
                if abs(f.normal.dot(axis)) > .98 and len(f.verts) > 4:
                    f.smooth = False

    def cyl(self, x, y, z0, z1, r, m, n=12, r2=None, caps=True, smooth=True):
        self.rod((x, y, z0), (x, y, z1), r, m, n, r2, caps, smooth)

    def sphere(self, x, y, z, r, m, seg=12, rings=8, scale=(1, 1, 1), smooth=True):
        mat = Matrix.Translation((x, y, z)) @ Matrix.Diagonal((*scale, 1))
        vs = bmesh.ops.create_uvsphere(self.bm, u_segments=seg, v_segments=rings, radius=r, matrix=mat)["verts"]
        self._paint(vs, m, smooth)

    def ico(self, x, y, z, r, m, sub=1, scale=(1, 1, 1), smooth=False, rz=0.0):
        mat = Matrix.Translation((x, y, z)) @ Matrix.Rotation(math.radians(rz), 4, 'Z') @ Matrix.Diagonal((*scale, 1))
        vs = bmesh.ops.create_icosphere(self.bm, subdivisions=sub, radius=r, matrix=mat)["verts"]
        self._paint(vs, m, smooth)

    def extrude(self, pts, a0, a1, m, axis='y', smooth=False):
        """Extrudes a 2D outline along an axis.

        axis 'y': points are (x, z);  axis 'x': points are (y, z);  axis 'z': points are (x, y).
        """
        def p3(u, v, a):
            if axis == 'y':
                return (u, a, v)
            if axis == 'x':
                return (a, u, v)
            return (u, v, a)
        bm = self.bm
        A = [bm.verts.new(p3(u, v, a0)) for u, v in pts]
        B = [bm.verts.new(p3(u, v, a1)) for u, v in pts]
        n = len(pts)
        faces = [bm.faces.new(A), bm.faces.new(B)]
        for i in range(n):
            j = (i + 1) % n
            f = bm.faces.new((A[i], A[j], B[j], B[i]))
            f.smooth = smooth
            faces.append(f)
        i = self._mi(m)
        for f in faces:
            f.material_index = i

    def lathe(self, x, y, profile, m, n=12, z=0.0, smooth=True, loop=False):
        """Spins a profile of (radius, height) pairs, bottom to top, around the vertical through (x, y).

        loop joins the last ring back to the first (a thin open shell such as a lamp shade) instead of capping.
        """
        bm = self.bm
        rings = []
        for r, h in profile:
            if r <= 1e-6:
                rings.append([bm.verts.new((x, y, z + h))])
            else:
                rings.append([bm.verts.new((x + r * math.cos(2 * math.pi * k / n), y + r * math.sin(2 * math.pi * k / n), z + h))
                              for k in range(n)])
        faces = []
        for a, b in zip(rings, rings[1:] + ([rings[0]] if loop else [])):
            if len(a) == 1 and len(b) == 1:
                continue
            for k in range(n):
                j = (k + 1) % n
                if len(a) == 1:
                    faces.append(bm.faces.new((a[0], b[j], b[k])))
                elif len(b) == 1:
                    faces.append(bm.faces.new((a[k], a[j], b[0])))
                else:
                    faces.append(bm.faces.new((a[k], a[j], b[j], b[k])))
        for f in faces:
            f.smooth = smooth
        if len(rings[0]) > 1 and not loop:
            faces.append(bm.faces.new(list(reversed(rings[0]))))
        if len(rings[-1]) > 1 and not loop:
            faces.append(bm.faces.new(rings[-1]))
        i = self._mi(m)
        for f in faces:
            f.material_index = i

    def loft(self, rings, m, n=12, axis='z', caps=True, smooth=True, fine=0.0):
        """Skins a stack of oval cross-sections: the way to shape bodies, limbs, heads and shoes.

        axis 'z': a ring is (x, y, z, rx, front, back): half-width rx across x, reaching `front` toward -Y and
        `back` toward +Y (back defaults to front, front to rx), so chests and faces can be fuller in front.
        axis 'y': a ring is (x, y, z, rx, down, up), standing in the XZ plane, for things that run front to back.
        A seventh value squares the oval off (below 1) or pinches it (above 1).
        fine (meters) puts extra rings between the given ones, that far apart, on a smooth curve through them
        (see `resample`): the outline then bends instead of running straight from ring to ring.
        """
        bm = self.bm
        if fine > 0:
            rings = resample(rings, fine, axis)
        R = []
        for r in rings:
            cx, cy, cz, rx = r[0], r[1], r[2], r[3]
            ra = r[4] if len(r) > 4 else rx
            rb = r[5] if len(r) > 5 else ra
            p = r[6] if len(r) > 6 else 1.0
            ring = []
            for k in range(n):
                t = 2 * math.pi * k / n
                c, s = math.cos(t), math.sin(t)
                if p != 1.0:
                    c = math.copysign(abs(c) ** p, c)
                    s = math.copysign(abs(s) ** p, s)
                u = rx * c
                v = (ra if s < 0 else rb) * s
                ring.append(bm.verts.new((cx + u, cy + v, cz) if axis == 'z' else (cx + u, cy, cz + v)))
            R.append(ring)
        faces = []
        for a, b in zip(R, R[1:]):
            for k in range(n):
                j = (k + 1) % n
                f = bm.faces.new((a[k], a[j], b[j], b[k]))
                f.smooth = smooth
                faces.append(f)
        if caps:
            for ring in (list(reversed(R[0])), R[-1]):
                f = bm.faces.new(ring)
                f.smooth = smooth
                faces.append(f)
        i = self._mi(m)
        for f in faces:
            f.material_index = i

    def tube(self, pts, r, m, n=6, joints=True):
        """A bent pipe through the points."""
        for a, b in zip(pts, pts[1:]):
            self.rod(a, b, r, m, n)
        if joints:
            for p in pts[1:-1]:
                self.sphere(p[0], p[1], p[2], r, m, seg=n, rings=max(3, n // 2))

    def face(self, pts, m, two_sided=False):
        """One flat polygon from 3D points (counter-clockwise seen from its front)."""
        bm = self.bm
        vs = [bm.verts.new(p) for p in pts]
        f = bm.faces.new(vs)
        f.material_index = self._mi(m)
        if two_sided:
            f2 = bm.faces.new([bm.verts.new(p) for p in reversed(pts)])
            f2.material_index = f.material_index

    def torus(self, x, y, z, R, r, m, n=14, k=6, axis='z'):
        """A ring: radius R, tube radius r, around the given axis."""
        bm = self.bm
        rings = []
        for i in range(n):
            a = 2 * math.pi * i / n
            ring = []
            for j in range(k):
                b = 2 * math.pi * j / k
                u = (R + r * math.cos(b)) * math.cos(a)
                v = (R + r * math.cos(b)) * math.sin(a)
                w = r * math.sin(b)
                p = {'z': (x + u, y + v, z + w), 'y': (x + u, y + w, z + v), 'x': (x + w, y + u, z + v)}[axis]
                ring.append(bm.verts.new(p))
            rings.append(ring)
        mi = self._mi(m)
        for i in range(n):
            a, b = rings[i], rings[(i + 1) % n]
            for j in range(k):
                f = bm.faces.new((a[j], b[j], b[(j + 1) % k], a[(j + 1) % k]))
                f.smooth = True
                f.material_index = mi

    # -- output

    def finish(self, coll, loc=(0, 0, 0), **props):
        bm = self.bm
        bm.verts.layers.int.remove(self.stamp)
        bm.normal_update()
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
        sharp = math.radians(50)
        for e in bm.edges:
            if len(e.link_faces) == 2 and e.link_faces[0].smooth and e.link_faces[1].smooth:
                e.smooth = e.calc_face_angle(0) < sharp
        remove_object(self.name)
        me = bpy.data.meshes.new(self.name)
        bm.to_mesh(me)
        tris = sum(len(f.verts) - 2 for f in bm.faces)
        bm.free()
        for m in self.mats:
            me.materials.append(m)
        ob = bpy.data.objects.new(self.name, me)
        coll.objects.link(ob)
        ob.location = loc
        ob["hc_id"] = self.name
        ob["hc_category"] = coll.name
        ob["hc_tris"] = tris
        for k, v in props.items():
            ob["hc_" + k] = v
        return ob


def lay_out(objs, x0=0.0, y0=0.0, gap=.8, row_width=16.0):
    """Stands the objects in rows along +X, front to the viewer, new rows further back (+Y)."""
    x = x0
    y = y0
    row_depth = 0.0
    for ob in objs:
        bb = [Vector(c) for c in ob.bound_box]
        minx = min(c.x for c in bb)
        maxx = max(c.x for c in bb)
        miny = min(c.y for c in bb)
        maxy = max(c.y for c in bb)
        w = maxx - minx
        d = maxy - miny
        if x > x0 and x + w > x0 + row_width:
            x = x0
            y += row_depth + gap
            row_depth = 0.0
        ob.location = (x - minx, y - miny, 0)
        x += w + gap
        row_depth = max(row_depth, d)
    return y + row_depth
