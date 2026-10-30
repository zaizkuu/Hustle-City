"""Bakes the HD renderer's car models from Quaternius's Cars Pack (CC0, https://quaternius.com/packs/cars.html).

usage: python3 tools/assets/cars.py [cache dir]

Writes app/src/main/resources/models/cars.bin, read by CarModels.kt. Each model is turned to the game's axes
(forward, right, up), scaled to one unit long, wide and high so the game can stretch it to any car's size, and
simplified by clustering vertices on an 8 cm grid (the wheels' spokes go; the body's shape stays). Faces
keep a role instead of a colour: the paint takes the car's own colour, glass reflects the sky, the lights glow.

Format (big-endian): "HCM1", model count; per model: name (UTF), polygon count, vertex count; per polygon a role
byte and a vertex count byte; then the vertices as three shorts each (forward and right in -10000..10000 for
-0.5..0.5, up in 0..20000 for 0..1).
"""
import os, struct, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, 'app/src/main/resources/models')
CACHE = sys.argv[1] if len(sys.argv) > 1 else '/tmp/quaternius-cars'

# the pack's OBJ folder on Google Drive (linked from quaternius.com/packs/cars.html): file ids
FILES = {
    'NormalCar1': ('1dfatafHMRRxI3WE5srluI4Y4WD72pCD6', '1S-ieWN4XulN6T__2buAX7AsmvzMi8P0v'),
    'NormalCar2': ('1Msv8m0vli0YGLLEHIx_ojM6grpmD_ygz', '11tdwW7kn3WvZ6SaUo92QBN_IXyh86KDZ'),
    'SportsCar': ('1P1VFc7M7OTUqcyOudeCIFF6lMK1riUGO', '1-4XZS0U1x3upaZIDNyk4hg64s1Dj2wkT'),
    'SportsCar2': ('1o9cgFWD966s-EZ2dILZH7Yig3yNkGR3C', '151D-fvhc-Eq5h_Kq37-sX10pWjXMDvG9'),
    'SUV': ('1_26j_0vooPFidwFSlHRWlF1ifphQZemK', '12V4PUvyNhfAPA9VejWYFsbniXd9Twsnt'),
    'Taxi': ('1UMriCC_JWsDfpTh5tBjQdhAj9_lJeuR2', '1vnTRCYyHM_-IiE_XetjJMHdxD_yE4GZu'),
}

# roles, as CarModels.kt numbers them
PAINT, PAINT2, GLASS, HEAD, TAIL, DARK, TRIM = range(7)
ROLES = {'Windows': GLASS, 'Headlights': HEAD, 'TailLights': TAIL, 'Black': DARK, 'Grey': TRIM,
         'DarkOrange': PAINT2, 'Material.007': PAINT2}
PAINTS = {'Blue', 'LightBlue', 'Orange', 'Yellow', 'White'}

CELL = (.02, .045, .06)  # clustering grid in model units (about 8 cm on a 4 m car)


def fetch(name):
    os.makedirs(CACHE, exist_ok=True)
    for ext, fid in zip(('obj', 'mtl'), FILES[name]):
        p = os.path.join(CACHE, f'{name}.{ext}')
        if not os.path.exists(p):
            subprocess.run(['curl', '-sS', '-f', '-L', '-o', p, f'https://drive.google.com/uc?export=download&id={fid}'], check=True)
    return os.path.join(CACHE, name + '.obj')


def load(path):
    vs, faces, mat = [], [], None
    for line in open(path):
        if line.startswith('v '): vs.append(tuple(map(float, line.split()[1:4])))
        elif line.startswith('usemtl'): mat = line.split()[1]
        elif line.startswith('f '): faces.append((mat, [int(t.split('/')[0]) - 1 for t in line.split()[1:]]))
    return vs, faces


def bake(name):
    vs, faces = load(fetch(name))
    # OBJ: x left of the driver, y up, z forward. The game: forward, right, up.
    xs, ys, zs = zip(*vs)
    cx, cz = (min(xs) + max(xs)) / 2, (min(zs) + max(zs)) / 2
    lx, ly, lz = max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs)
    local = [((z - cz) / lz, -(x - cx) / lx, (y - min(ys)) / ly) for x, y, z in vs]
    # cluster: every vertex snaps to the average of its grid cell
    cells, rep = {}, []
    for i, p in enumerate(local):
        k = tuple(int(round(p[a] / CELL[a])) for a in range(3))
        cells.setdefault(k, []).append(i)
    index = [0] * len(local)
    for members in cells.values():
        n = len(rep)
        rep.append(tuple(sum(local[m][a] for m in members) / len(members) for a in range(3)))
        for m in members: index[m] = n
    polys, seen = [], set()
    for mat, f in faces:
        ring = []
        for v in f:
            r = index[v]
            if not ring or ring[-1] != r: ring.append(r)
        if len(ring) > 1 and ring[0] == ring[-1]: ring.pop()
        if len(set(ring)) < 3 or len(ring) > 255: continue
        key = tuple(sorted(ring))
        if key in seen: continue
        seen.add(key)
        role = PAINT if mat in PAINTS else ROLES.get(mat, TRIM)
        polys.append((role, ring))
    used = sorted({v for _, r in polys for v in r})
    remap = {v: i for i, v in enumerate(used)}
    out = struct.pack('>H', len(polys)) + struct.pack('>H', len(used))
    out = bytearray(out)
    for role, r in polys: out += struct.pack('>BB', role, len(r))
    for role, r in polys:
        for v in r: out += struct.pack('>H', remap[v])
    for v in used:
        f, rt, u = rep[v]
        out += struct.pack('>hhh', int(round(f * 20000)), int(round(rt * 20000)), int(round(u * 20000)))
    print(f'{name}: {len(faces)} faces -> {len(polys)} polygons, {len(vs)} vertices -> {len(used)}')
    return bytes(out)


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    blob = bytearray(b'HCM1' + struct.pack('>H', len(FILES)))
    for name in FILES:
        enc = name.encode()
        blob += struct.pack('>H', len(enc)) + enc + bake(name)
    with open(os.path.join(OUT, 'cars.bin'), 'wb') as f: f.write(blob)
    with open(os.path.join(OUT, 'CREDITS.txt'), 'w') as f:
        f.write('Car models for the HD renderer, baked by tools/assets/cars.py from the Cars Pack by Quaternius,\n'
                'released under CC0 1.0 (public domain): https://quaternius.com/packs/cars.html\n\n')
        for name in FILES: f.write(f'{name}\n')
    print(len(blob), 'bytes')
