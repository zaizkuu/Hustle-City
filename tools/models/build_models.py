#!/usr/bin/env python3
"""Builds the furniture models the HD renderer draws, from the glTF sources listed in manifest.py.

usage: python3 tools/models/build_models.py [--preview out.png] [id ...]

Each model is assembled from one or more source meshes, each turned to face a direction and fitted into a box in the
item's own tile units (x across its footprint, y deep, z up in meters; the front faces +y unless the manifest says
otherwise). Colors come from the source materials (palette textures are sampled per vertex), and material names pick
the shader's surface (wood, cloth, glass...). Output: app/src/main/assets/models/<id>.hcm, read by render/Models.kt.

HCM1 layout (little endian): "HCM1", u32 vertex count, u32 index count, 6 x f32 bounds (min xyz, max xyz), then per
vertex f32 x, y, z, i8 nx, ny, nz, u8 flags (material * 2 + emissive), u8 r, g, b, a; then u16 indices.
"""
import argparse
import os
import struct
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gltf  # noqa: E402
from manifest import MODELS, SOURCES  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
OUT = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'models')

# shader surfaces (render/Scene3D.kt Mat)
PLAIN, GLASS, WOOD, FOLIAGE, FABRIC, PAINT = 0, 3, 7, 13, 15, 16


def surface(name):
    n = name.lower()
    if 'glass' in n or 'window' in n:
        return GLASS
    if 'wood' in n:
        return WOOD
    if 'plant' in n or 'leaf' in n or 'leaves' in n:
        return FOLIAGE
    if any(k in n for k in ('carpet', 'fabric', 'cloth', 'cushion', 'pillow', 'rug')):
        return FABRIC
    if 'metal' in n or 'chrome' in n:
        return PAINT
    return PLAIN


FACE_TURNS = {'+y': 0, '-x': 1, '-y': 2, '+x': 3}


def load_part(part):
    t = gltf.read(os.path.join(SOURCES, part['src']))
    # glTF is Y-up with the front toward +Z; the game is Z-up with the front toward +Y (the swap also flips the
    # handedness, which the game's x-east / y-south frame needs)
    pos = t.pos[:, [0, 2, 1]].astype(np.float64)
    nrm = t.nrm[:, [0, 2, 1]].astype(np.float64)
    turns = FACE_TURNS[part.get('face', '+y')]
    for _ in range(turns):
        # a quarter turn counterclockwise seen from above (+y front ends up facing -x)
        pos = np.stack([-pos[:, 1], pos[:, 0], pos[:, 2]], axis=1)
        nrm = np.stack([-nrm[:, 1], nrm[:, 0], nrm[:, 2]], axis=1)
    col = t.col.copy()
    if 'tint' in part:
        for mname, rgb in part['tint'].items():
            sel = np.repeat(np.array([m == mname for m in t.mat]), 3)
            col[sel, :3] = np.array(rgb, dtype=np.float64) / 255.0
    named = {'plain': PLAIN, 'wood': WOOD, 'fabric': FABRIC, 'paint': PAINT, 'glass': GLASS, 'foliage': FOLIAGE}
    over = part.get('surface', {})
    mats = np.repeat(np.array([named[over[m]] if m in over else surface(m) for m in t.mat], dtype=np.int64), 3)
    emit = np.repeat(np.array(t.emit, dtype=bool), 3)
    for mname in part.get('glow', []):
        emit |= np.repeat(np.array([m == mname for m in t.mat]), 3)
    # fit into the part's box
    lo, hi = pos.min(0), pos.max(0)
    size = np.maximum(hi - lo, 1e-6)
    x0, y0, z0, x1, y1, z1 = part['box']
    bw, bd = x1 - x0, y1 - y0
    if part.get('stretch'):
        s = np.array([bw / size[0], bd / size[1], (z1 - z0) / size[2] if z1 is not None else min(bw / size[0], bd / size[1])])
    else:
        k = min(bw / size[0], bd / size[1])
        if z1 is not None:
            k = min(k, (z1 - z0) / size[2])
        s = np.array([k, k, k])
    pos = (pos - lo) * s
    # normals of a non-uniformly scaled mesh
    nrm = nrm / s
    nrm /= np.maximum(np.linalg.norm(nrm, axis=1, keepdims=True), 1e-9)
    w, d = size[0] * s[0], size[1] * s[1]
    align = part.get('align', 'center')
    ox = x0 + (bw - w) / 2
    oy = y0 if align == 'back' else (y1 - d if align == 'front' else y0 + (bd - d) / 2)
    pos += np.array([ox, oy, z0])
    return pos, nrm, col, mats, emit


def build(mid):
    parts = MODELS[mid]
    P, N, C, M, E = [], [], [], [], []
    for part in parts:
        for rep in range(part.get('repeat', 1)):
            p = dict(part)
            dx, dy, dz = part.get('step', (0, 0, 0))
            bx = p['box']
            p['box'] = (bx[0] + dx * rep, bx[1] + dy * rep, bx[2] + dz * rep, bx[3] + dx * rep, bx[4] + dy * rep, None if bx[5] is None else bx[5] + dz * rep)
            pos, nrm, col, mats, emit = load_part(p)
            P.append(pos); N.append(nrm); C.append(col); M.append(mats); E.append(emit)
    pos = np.concatenate(P); nrm = np.concatenate(N); col = np.concatenate(C); mats = np.concatenate(M); emit = np.concatenate(E)
    # weld identical corners into an indexed mesh
    q = np.concatenate([np.round(pos * 2000), np.round(nrm * 100), np.round(col * 255), mats[:, None], emit[:, None]], axis=1)
    uniq, inverse = np.unique(q, axis=0, return_inverse=True)
    first = np.zeros(len(uniq), dtype=np.int64)
    first[inverse[::-1]] = np.arange(len(q))[::-1]
    vpos, vnrm, vcol, vmat, vem = pos[first], nrm[first], col[first], mats[first], emit[first]
    idx = inverse.ravel().astype(np.int64)
    if len(vpos) > 65535:
        raise SystemExit(f'{mid}: {len(vpos)} vertices is too many for 16-bit indices')
    return vpos, vnrm, vcol, vmat, vem, idx


def write(mid, mesh):
    vpos, vnrm, vcol, vmat, vem, idx = mesh
    lo, hi = vpos.min(0), vpos.max(0)
    out = bytearray(b'HCM1')
    out += struct.pack('<II', len(vpos), len(idx))
    out += struct.pack('<6f', *lo, *hi)
    n8 = np.clip(np.round(vnrm * 127), -127, 127).astype(np.int8)
    c8 = np.clip(np.round(vcol * 255), 0, 255).astype(np.uint8)
    flags = (vmat * 2 + vem.astype(np.int64)).astype(np.uint8)
    for i in range(len(vpos)):
        out += struct.pack('<3f', *vpos[i])
        out += struct.pack('<3bB', int(n8[i, 0]), int(n8[i, 1]), int(n8[i, 2]), int(flags[i]))
        out += struct.pack('<4B', *(int(v) for v in c8[i]))
    out += struct.pack('<%dH' % len(idx), *idx)
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, mid + '.hcm'), 'wb') as f:
        f.write(out)
    return len(out)


def preview(meshes, path, cell=220):
    """A contact sheet: every model seen from the front-right, lit from the top-left, on its footprint."""
    from PIL import Image, ImageDraw
    cols = 6
    rows = (len(meshes) + cols - 1) // cols
    img = Image.new('RGB', (cols * cell, rows * cell), (40, 43, 50))
    dr = ImageDraw.Draw(img)
    light = np.array([-.4, .5, .8]); light /= np.linalg.norm(light)
    yaw, pitch = np.radians(-30), np.radians(35)
    # camera looks at the model from the front (+y) and a little from the right
    for k, (mid, mesh) in enumerate(meshes):
        vpos, vnrm, vcol, vmat, vem, idx = mesh
        gx, gy = (k % cols) * cell, (k // cols) * cell
        lo, hi = vpos.min(0), vpos.max(0)
        c = (lo + hi) / 2
        p = vpos - c
        # rotate about z, then tilt: screen x right, screen y down, depth into the screen
        cyw, syw = np.cos(yaw), np.sin(yaw)
        x = p[:, 0] * cyw - p[:, 1] * syw
        yv = p[:, 0] * syw + p[:, 1] * cyw
        sy = -(p[:, 2] * np.cos(pitch) - yv * np.sin(pitch))
        depth = yv * np.cos(pitch) + p[:, 2] * np.sin(pitch)
        span = max(np.ptp(x), np.ptp(sy), 1e-6)
        sc = (cell - 50) / span
        X = gx + cell / 2 + x * sc
        Y = gy + cell / 2 - 10 + sy * sc
        tri = idx.reshape(-1, 3)
        order = np.argsort(-depth[tri].mean(1))
        for t in order:
            a, b, cc = tri[t]
            n = vnrm[a]
            shade = .45 + .55 * max(0, float(n @ light))
            rgb = vcol[a, :3] * (1.0 if vem[a] else shade)
            dr.polygon([(X[a], Y[a]), (X[b], Y[b]), (X[cc], Y[cc])], fill=tuple(int(v * 255) for v in rgb))
        dr.text((gx + 6, gy + cell - 18), f'{mid} {hi[0]-lo[0]:.2f}x{hi[1]-lo[1]:.2f}x{hi[2]:.2f}', fill=(230, 230, 230))
    img.save(path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('ids', nargs='*')
    ap.add_argument('--preview')
    a = ap.parse_args()
    ids = a.ids or sorted(MODELS)
    meshes = []
    total = 0
    for mid in ids:
        mesh = build(mid)
        size = write(mid, mesh)
        total += size
        meshes.append((mid, mesh))
        print(f'{mid:20s} {len(mesh[0]):5d} verts {len(mesh[5]) // 3:5d} tris {size / 1024:6.1f} KB')
    print(f'{len(ids)} models, {total / 1024:.0f} KB')
    if a.preview:
        preview(meshes, a.preview)


if __name__ == '__main__':
    main()
