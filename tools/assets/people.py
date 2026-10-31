"""Bakes the HD renderer's people from Quaternius's Ultimate Modular Men and Women packs (CC0,
https://quaternius.com/packs/ultimatemodularcharacters.html and .../ultimatemodularwomen.html).

usage: python3 tools/assets/people.py [cache dir] [--preview sheet.png]

Writes app/src/main/resources/models/people.bin, read by PeopleModels.kt. Each character is simplified to about 900
triangles with a quadric decimator (pyfqmr), each part on its own so small things like eyes survive, and every vertex
keeps its two strongest bones (the fingers fold into the hand). The animation is baked per rig (the men share one,
the women another): the packs' Idle, Walk and Run, and poses the packs don't have built from them (sitting on a
chair, carrying a box in both arms, dancing). Faces keep a role instead of a colour where the game dresses its
people (skin, hair, top, bottoms), so each person in the street wears their own colours.

Format (big-endian): "HPC1"; rig count, then per rig its name (UTF), bone count and clip count, each clip its name
(UTF), a loop flag byte, duration (float) and sample count, then per sample per bone a rotation quaternion (x, y, z,
w as shorts / 32767) and a translation (three shorts / 8000 m), the skin transform from the bind pose in the game's
axes (forward, right, up); model count, then per model its name, rig index, palette (count, then ARGB ints),
triangle count and vertex count, per triangle a role byte (0 skin, 1 hair, 2 top, 3 bottoms, 4 + n palette colour n)
and three vertex indices (unsigned shorts), and per vertex its position (three shorts / 8000 m, bind pose), two bone
indices and the first bone's weight (bytes).
"""
import base64, json, math, os, struct, subprocess, sys
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, 'app/src/main/resources/models')
args = [a for a in sys.argv[1:] if not a.startswith('--')]
CACHE = args[0] if args else '/tmp/quaternius-people'
PREVIEW = sys.argv[sys.argv.index('--preview') + 1] if '--preview' in sys.argv else None

# the packs' "Individual Characters / glTF" folders on Google Drive: file ids; whether the game dresses the top and
# the bottoms (suits and hi-vis keep their own; 'top' dresses a skirt like the top it belongs to)
FILES = {
    'M_Casual': ('1Jn7kULNmrtqP8BUUL19h8MhbdOnwPFhv', 'M', True, True),
    'M_Hoodie': ('1em1So1xwwQNfHJYMvzKcXkZllvtxpKP5', 'M', True, True),
    'M_Suit': ('1NhXHnGU0zK9hBrT5FoZp8nTz_EmvTPg5', 'M', False, False),
    'M_Worker': ('14d8n7IDnnlnGt_uiATnNg3uvi_4dyd9V', 'M', False, False),
    'M_Punk': ('1yHWu5ezXq4dYBcn4sWiNd16YN9fMtXo0', 'M', True, True),
    'M_Beach': ('1IL1YJPJvNkuGnKI69-W-VMBIDCo-u49N', 'M', True, True),
    'M_Adventurer': ('1fzSq1Rr037f7QkfXPWEAzmbLMNx-FpPA', 'M', True, True),
    'F_Casual': ('18b3WwlrwrFYWAM7BcnjWeIxKJyxAQiGh', 'F', True, True),
    'F_Formal': ('1iayBzVv_zLjuPtaNPouw_auwKlQLLmes', 'F', True, 'top'),
    'F_Suit': ('1GjWtofxjmPku25cXJxHrzLLeUbXw7A_s', 'F', False, False),
    'F_Worker': ('1iwF_fqDErPH9uyol6NmS-MnzGgsZ5ejV', 'F', False, False),
    'F_Punk': ('1ITb_iFiroAsmjQI38z_p6nNXinwliVLA', 'F', True, True),
    'F_Adventurer': ('1uxAFnDp73NO1c16LvHHjAYh1-deMNk5I', 'F', True, True),
}
SKIP_MESHES = ('Pistol',)
TARGET = 900  # triangles per character

# the bones the game poses, in PeopleModels' order; fingers fold into the wrist, pole targets drop out
BONES = ['Root', 'Body', 'Hips', 'Abdomen', 'Torso', 'Chest', 'Neck', 'Head',
         'Shoulder.L', 'UpperArm.L', 'LowerArm.L', 'Wrist.L', 'Shoulder.R', 'UpperArm.R', 'LowerArm.R', 'Wrist.R',
         'UpperLeg.L', 'LowerLeg.L', 'Foot.L', 'UpperLeg.R', 'LowerLeg.R', 'Foot.R']
SKIN, HAIR, TOP, BOTTOM = range(4)
HAIRS = ('Hair', 'Hair_Brown', 'Hair_Blond', 'Eyebrows', 'Moustache')

# glTF (x, y, z) with the character facing +z -> the game's (forward, right, up), as cars.py turns them
C = np.array([[0, 0, 1], [-1, 0, 0], [0, 1, 0]], np.float64)

CT = {5126: np.float32, 5123: np.uint16, 5121: np.uint8, 5125: np.uint32}
NC = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}


# ---------------------------------------------------------------- glTF
class GLTF:
    def __init__(self, path):
        self.g = g = json.load(open(path))
        self.buf = [base64.b64decode(b['uri'].split(',', 1)[1]) for b in g['buffers']]
        self.parent = {}
        for i, n in enumerate(g['nodes']):
            for ch in n.get('children', []): self.parent[ch] = i
        self.names = [n.get('name', str(i)) for i, n in enumerate(g['nodes'])]
        self.joints = g['skins'][0]['joints']
        self.jname = [self.names[j] for j in self.joints]
        inv = self.acc(g['skins'][0]['inverseBindMatrices']).reshape(-1, 4, 4).transpose(0, 2, 1)
        self.inv = {self.jname[k]: inv[k].astype(np.float64) for k in range(len(self.joints))}

    def acc(self, i):
        a = self.g['accessors'][i]
        bv = self.g['bufferViews'][a['bufferView']]
        dt = CT[a['componentType']]; nc = NC[a['type']]
        off = bv.get('byteOffset', 0) + a.get('byteOffset', 0)
        arr = np.frombuffer(self.buf[bv['buffer']], dt, a['count'] * nc, off).reshape(a['count'], nc)
        if a.get('normalized') and dt != np.float32: arr = arr.astype(np.float32) / np.iinfo(dt).max
        return arr

    def trs(self, i, over):
        n = self.g['nodes'][i]
        t = np.array(n.get('translation', [0, 0, 0]), np.float64)
        r = np.array(n.get('rotation', [0, 0, 0, 1]), np.float64)
        s = np.array(n.get('scale', [1, 1, 1]), np.float64)
        if over and i in over:
            o = over[i]; t = o.get('translation', t); r = o.get('rotation', r); s = o.get('scale', s)
        m = np.eye(4); m[:3, :3] = quat_mat(r) * s; m[:3, 3] = t
        return m

    def globals(self, over=None):
        G = {}
        def get(i):
            if i not in G:
                p = self.parent.get(i)
                G[i] = (get(p) if p is not None else np.eye(4)) @ self.trs(i, over)
            return G[i]
        return {self.names[j]: get(j) for j in self.joints}

    def clip(self, name):
        return next(a for a in self.g['animations'] if a['name'] == name)

    def duration(self, name):
        a = self.clip(name)
        return max(float(self.acc(a['samplers'][c['sampler']]['input'])[-1, 0]) for c in a['channels'])

    def sample(self, name, t):
        a = self.clip(name)
        out = {}
        for ch in a['channels']:
            smp = a['samplers'][ch['sampler']]
            times = self.acc(smp['input'])[:, 0]; vals = self.acc(smp['output']).astype(np.float64)
            if smp.get('interpolation') == 'CUBICSPLINE': vals = vals[1::3]
            tt = min(max(t, times[0]), times[-1])
            k = min(max(int(np.searchsorted(times, tt, side='right')) - 1, 0), len(times) - 1)
            path = ch['target']['path']
            if k == len(times) - 1: v = vals[k]
            else:
                u = (tt - times[k]) / max(times[k + 1] - times[k], 1e-9)
                v = slerp(vals[k], vals[k + 1], u) if path == 'rotation' else vals[k] * (1 - u) + vals[k + 1] * u
            out.setdefault(ch['target']['node'], {})[path] = v
        return out


def quat_mat(q):
    x, y, z, w = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def mat_quat(m):
    t = np.trace(m)
    if t > 0:
        s = math.sqrt(t + 1) * 2
        q = [(m[2, 1] - m[1, 2]) / s, (m[0, 2] - m[2, 0]) / s, (m[1, 0] - m[0, 1]) / s, .25 * s]
    elif m[0, 0] > m[1, 1] and m[0, 0] > m[2, 2]:
        s = math.sqrt(1 + m[0, 0] - m[1, 1] - m[2, 2]) * 2
        q = [.25 * s, (m[0, 1] + m[1, 0]) / s, (m[0, 2] + m[2, 0]) / s, (m[2, 1] - m[1, 2]) / s]
    elif m[1, 1] > m[2, 2]:
        s = math.sqrt(1 + m[1, 1] - m[0, 0] - m[2, 2]) * 2
        q = [(m[0, 1] + m[1, 0]) / s, .25 * s, (m[1, 2] + m[2, 1]) / s, (m[0, 2] - m[2, 0]) / s]
    else:
        s = math.sqrt(1 + m[2, 2] - m[0, 0] - m[1, 1]) * 2
        q = [(m[0, 2] + m[2, 0]) / s, (m[1, 2] + m[2, 1]) / s, .25 * s, (m[1, 0] - m[0, 1]) / s]
    q = np.array(q); return q / np.linalg.norm(q)


def slerp(a, b, u):
    d = np.dot(a, b)
    if d < 0: b = -b; d = -d
    if d > .9995: q = a + (b - a) * u; return q / np.linalg.norm(q)
    th = math.acos(min(d, 1.0))
    return (math.sin((1 - u) * th) * a + math.sin(u * th) * b) / math.sin(th)


def axis_rot(axis, deg):
    a = np.asarray(axis, np.float64); a = a / np.linalg.norm(a)
    h = math.radians(deg) / 2
    return quat_mat(np.r_[a * math.sin(h), math.cos(h)])


# ---------------------------------------------------------------- posing
def subtree(gl, bone):
    """[bone] and every joint under it."""
    idx = {gl.names[j]: j for j in gl.joints}
    out = []
    for name, j in idx.items():
        k = j
        while k is not None:
            if gl.names[k] == bone: out.append(name); break
            k = gl.parent.get(k)
    return out


def turn(gl, G, bone, axis, deg):
    """Swings [bone] and everything below it [deg] about a world [axis] through the bone's joint."""
    p = G[bone][:3, 3].copy()
    R = np.eye(4); R[:3, :3] = axis_rot(axis, deg)
    T0 = np.eye(4); T0[:3, 3] = -p; T1 = np.eye(4); T1[:3, 3] = p
    M = T1 @ R @ T0
    for b in subtree(gl, bone): G[b] = M @ G[b]


def shift(G, dy):
    """Bob the body up or down; the feet (IK bones) stay planted and the knees take it."""
    for b in G:
        if b.startswith('Foot.'): continue
        G[b] = G[b].copy(); G[b][1, 3] += dy


# x points to the character's left (it faces +z), so a turn about +x by a negative angle swings a limb forward
def sit(gl):
    G = gl.globals(gl.sample('Idle', 0))
    idle = {k: v.copy() for k, v in G.items()}
    for s in ('L', 'R'):
        turn(gl, G, f'UpperLeg.{s}', (1, 0, 0), -88)
        turn(gl, G, f'LowerLeg.{s}', (1, 0, 0), 92)
        turn(gl, G, f'UpperArm.{s}', (1, 0, 0), -22)
        turn(gl, G, f'LowerArm.{s}', (1, 0, 0), -48)
        follow(gl, G, idle, f'Foot.{s}', f'LowerLeg.{s}')
    return G


def follow(gl, G, G_idle, bone, onto):
    """The feet are IK bones hung off the root, not the shins: put [bone] back where it sits on [onto] when idle."""
    d = G[onto] @ np.linalg.inv(G_idle[onto]) @ G_idle[bone] @ np.linalg.inv(G[bone])
    for b in subtree(gl, bone): G[b] = d @ G[b]


def carry_arms(gl, G, G_idle):
    """Both arms forward at chest height, holding something in front."""
    for s in ('L', 'R'):
        rel = np.linalg.inv(G_idle['Chest']) @ G_idle[f'Shoulder.{s}']
        new = G['Chest'] @ rel
        d = new @ np.linalg.inv(G[f'Shoulder.{s}'])
        for b in subtree(gl, f'Shoulder.{s}'): G[b] = d @ G[b]
        turn(gl, G, f'UpperArm.{s}', (1, 0, 0), -62)
        turn(gl, G, f'LowerArm.{s}', (1, 0, 0), -28)
    return G


def dance(gl, u):
    """A bouncing groove, arms up, one cycle over u in 0..1."""
    G = gl.globals(gl.sample('Idle', u * gl.duration('Idle')))
    w = 2 * math.pi * u
    turn(gl, G, 'Hips', (0, 1, 0), 14 * math.sin(w))
    for s, sg, ph in (('L', 1, 0), ('R', -1, math.pi)):
        turn(gl, G, f'UpperArm.{s}', (0, 0, 1), sg * (118 + 26 * math.sin(2 * w + ph)))
        turn(gl, G, f'LowerArm.{s}', (0, 0, 1), sg * (22 + 18 * math.sin(2 * w + ph + 1)))
    shift(G, -.045 * (1 - abs(math.sin(2 * w))))
    return G


def clips(gl):
    """The rig's clips: (name, loop, duration, [globals per sample])."""
    out = []
    for name, n in (('Idle', 20), ('Walk', 32), ('Run', 20)):
        d = gl.duration(name)
        out.append((name, True, d, [gl.globals(gl.sample(name, d * k / n)) for k in range(n)]))
    idle0 = gl.globals(gl.sample('Idle', 0))
    d = gl.duration('Walk')
    out.append(('Carry', True, d, [carry_arms(gl, gl.globals(gl.sample('Walk', d * k / 32)), idle0) for k in range(32)]))
    out.append(('CarryIdle', False, 0.0, [carry_arms(gl, gl.globals(gl.sample('Idle', 0)), idle0)]))
    out.append(('Sit', False, 0.0, [sit(gl)]))
    out.append(('Dance', True, 1.2, [dance(gl, k / 24) for k in range(24)]))
    return out


def skin_mats(gl, G):
    """Per game bone, the bind-to-pose transform in the game's axes: rotation 3x3 and translation."""
    out = []
    for b in BONES:
        S = G[b] @ gl.inv[b]
        out.append((C @ S[:3, :3] @ C.T, C @ S[:3, 3]))
    return out


# ---------------------------------------------------------------- meshes
def fold(name):
    """Fingers into the hand; pole targets have no vertices."""
    if name in BONES: return BONES.index(name)
    side = name[-1]
    if name[:-2] in ('Index1', 'Index2', 'Index3', 'Index4', 'Middle1', 'Middle2', 'Middle3', 'Middle4', 'Ring1', 'Ring2', 'Ring3',
                     'Ring4', 'Pinky1', 'Pinky2', 'Pinky3', 'Pinky4', 'Thumb1', 'Thumb2', 'Thumb3'):
        return BONES.index(f'Wrist.{side}')
    return BONES.index('Hips')


def srgb(c):
    c = np.clip(np.asarray(c[:3], np.float64), 0, 1)
    v = np.where(c <= .0031308, c * 12.92, 1.055 * c ** (1 / 2.4) - .055)
    return 0xFF000000 | (int(v[0] * 255 + .5) << 16) | (int(v[1] * 255 + .5) << 8) | int(v[2] * 255 + .5)


def parts(gl):
    """(mesh node name, material name, colour, positions, bones (n,2), weight0, triangles) per primitive."""
    from scipy.spatial import cKDTree
    out = []
    mats = gl.g['materials']
    for node in gl.g['nodes']:
        if 'mesh' not in node or node.get('name', '').startswith(SKIP_MESHES): continue
        for p in gl.g['meshes'][node['mesh']]['primitives']:
            pos = gl.acc(p['attributes']['POSITION']).astype(np.float64)
            jn = gl.acc(p['attributes']['JOINTS_0']).astype(np.int64)
            wt = gl.acc(p['attributes']['WEIGHTS_0']).astype(np.float64)
            tri = gl.acc(p['indices'])[:, 0].astype(np.int64).reshape(-1, 3)
            m = mats[p.get('material', 0)]
            out.append((node['name'], m.get('name', ''), srgb(m.get('pbrMetallicRoughness', {}).get('baseColorFactor', [.8, .8, .8, 1])),
                        pos, jn, wt, tri))
    return out


def two_bones(gl, jn, wt):
    """Per vertex the two strongest game bones and the first one's share."""
    n = len(jn)
    acc = np.zeros((n, len(BONES)))
    for k in range(4):
        b = np.array([fold(gl.jname[j]) for j in jn[:, k]])
        np.add.at(acc, (np.arange(n), b), wt[:, k])
    order = np.argsort(-acc, 1)
    b0, b1 = order[:, 0], order[:, 1]
    w0 = acc[np.arange(n), b0]; w1 = acc[np.arange(n), b1]
    share = np.where(w0 + w1 > 0, w0 / np.maximum(w0 + w1, 1e-9), 1)
    b1 = np.where(w1 > .02, b1, b0)
    return b0, b1, share


def weld(pos, tri):
    """Shares corners that sit at the same place: the packs are flat shaded, so every triangle has its own, and to the
    simplifier every edge would be a border it may not touch."""
    key = np.round(pos / 1e-5).astype(np.int64)
    uniq, first, inv = np.unique(key, axis=0, return_index=True, return_inverse=True)
    return pos[first], inv.reshape(-1)[tri]


def simplify(pos, tri, target):
    if len(tri) <= target: return pos, tri
    import pyfqmr
    pos, tri = weld(pos, tri)
    s = pyfqmr.Simplify()
    s.setMesh(pos.astype(np.float64), tri.astype(np.int32))
    s.simplify_mesh(target_count=int(target), aggressiveness=7, preserve_border=True, verbose=0)
    v, f, _ = s.getMesh()
    return np.asarray(v, np.float64), np.asarray(f, np.int64)


def bake_model(name, gl, top, bottom):
    from scipy.spatial import cKDTree
    prims = parts(gl)
    total = sum(len(p[6]) for p in prims)
    # which material is the top and which the bottoms: the most of each, skin aside, on the body and the legs
    area = {}
    for node, mat, col, pos, jn, wt, tri in prims:
        a = np.linalg.norm(np.cross(pos[tri[:, 1]] - pos[tri[:, 0]], pos[tri[:, 2]] - pos[tri[:, 0]]), axis=1).sum()
        kind = 'body' if node.endswith('_Body') else 'legs' if node.endswith(('_Legs', '_Pants')) else 'other'
        if not mat.startswith('Skin') and mat not in HAIRS and mat != 'Eye': area[(kind, mat)] = area.get((kind, mat), 0) + a
    top_mat = max(((m, a) for (k, m), a in area.items() if k == 'body'), key=lambda x: x[1], default=(None, 0))[0]
    bot_mat = max(((m, a) for (k, m), a in area.items() if k == 'legs'), key=lambda x: x[1], default=(None, 0))[0]
    palette, V, B0, B1, W, T, R = [], [], [], [], [], [], []
    for node, mat, col, pos, jn, wt, tri in prims:
        b0, b1, share = two_bones(gl, jn, wt)
        # small parts (eyes, earrings) as they are; the rest down to its share of the target
        keep = max(int(len(tri) * TARGET / total), 24)
        sp, st = simplify(pos, tri, keep) if len(tri) > 60 else (pos, tri)
        near = cKDTree(pos).query(sp)[1]
        if mat.startswith('Skin'): role = SKIN
        elif mat in HAIRS: role = HAIR
        elif top and mat == top_mat and node.endswith('_Body'): role = TOP
        elif bottom and mat == bot_mat and node.endswith(('_Legs', '_Pants')): role = TOP if bottom == 'top' else BOTTOM
        else:
            if col not in palette: palette.append(col)
            role = 4 + palette.index(col)
        base = sum(len(v) for v in V)
        V.append(sp @ C.T); B0.append(b0[near]); B1.append(b1[near]); W.append(share[near])
        T.append(st + base); R += [role] * len(st)
    V = np.vstack(V); B0 = np.concatenate(B0); B1 = np.concatenate(B1); W = np.concatenate(W); T = np.vstack(T); R = np.array(R)
    print(f'{name}: {total} triangles -> {len(T)}, {len(V)} vertices, top {top_mat if top else "-"}, bottoms {bot_mat if bottom else "-"}, '
          f'{len(palette)} own colours')
    return dict(name=name, V=V, B0=B0, B1=B1, W=W, T=T, R=R, palette=palette)


# ---------------------------------------------------------------- output
def fetch(name):
    os.makedirs(CACHE, exist_ok=True)
    p = os.path.join(CACHE, name + '.gltf')
    if not os.path.exists(p):
        subprocess.run(['curl', '-sS', '-f', '-L', '-o', p, f'https://drive.google.com/uc?export=download&id={FILES[name][0]}'], check=True)
    return p


def utf(s):
    b = s.encode(); return struct.pack('>H', len(b)) + b


def write(rigs, models, path):
    out = bytearray(b'HPC1')
    out += struct.pack('>H', len(rigs))
    for rname, cl in rigs:
        out += utf(rname) + struct.pack('>HH', len(BONES), len(cl))
        for name, loop, dur, samples in cl:
            out += utf(name) + struct.pack('>BfH', 1 if loop else 0, dur, len(samples))
            for mats in samples:
                for R, t in mats:
                    q = mat_quat(R)
                    out += struct.pack('>4h', *[int(round(v * 32767)) for v in q])
                    out += struct.pack('>3h', *[int(round(v * 8000)) for v in t])
    out += struct.pack('>H', len(models))
    for m in models:
        out += utf(m['name']) + struct.pack('>B', m['rig'])
        out += struct.pack('>B', len(m['palette'])) + b''.join(struct.pack('>I', c) for c in m['palette'])
        out += struct.pack('>HH', len(m['T']), len(m['V']))
        for r, t in zip(m['R'], m['T']): out += struct.pack('>B3H', int(r), *map(int, t))
        for v, b0, b1, w in zip(m['V'], m['B0'], m['B1'], m['W']):
            out += struct.pack('>3h3B', *[int(round(c * 8000)) for c in v], int(b0), int(b1), int(round(w * 255)))
    with open(path, 'wb') as f: f.write(out)
    return len(out)


def pose(model, mats):
    """A baked model's vertices in a pose (for the preview)."""
    V = model['V']
    P0 = np.stack([mats[b][0] @ v + mats[b][1] for v, b in zip(V, model['B0'])])
    P1 = np.stack([mats[b][0] @ v + mats[b][1] for v, b in zip(V, model['B1'])])
    return P0 * model['W'][:, None] + P1 * (1 - model['W'][:, None])


def preview(models, rigs, path):
    from PIL import Image, ImageDraw
    dress = {SKIN: 0xFFC68B59, HAIR: 0xFF2B1D14, TOP: 0xFF2F80ED, BOTTOM: 0xFF34383F}
    poses = [('Idle', 0), ('Walk', 8), ('Walk', 24), ('Run', 5), ('Carry', 10), ('Sit', 0), ('Dance', 6)]
    size = 220
    sheet = Image.new('RGB', (size * len(poses), size * len(models)), (214, 219, 226))
    for mi, m in enumerate(models):
        cl = dict((c[0], c) for c in rigs[m['rig']][1])
        for pi, (clip, k) in enumerate(poses):
            mats = skin_mats_cache[(m['rig'], clip, k)]
            P = pose(m, mats)
            im = Image.new('RGB', (size, size), (214, 219, 226)); d = ImageDraw.Draw(im)
            yaw = .7
            # game axes: forward, right, up; look from front-right
            x = P[:, 1] * math.cos(yaw) - P[:, 0] * math.sin(yaw); z = P[:, 1] * math.sin(yaw) + P[:, 0] * math.cos(yaw)
            sc = size / 2.3; px = size / 2 + x * sc; py = size * .92 - P[:, 2] * sc
            tri = m['T']
            depth = z[tri].mean(1)
            a = P[tri[:, 1]] - P[tri[:, 0]]; b = P[tri[:, 2]] - P[tri[:, 0]]
            nrm = np.cross(a, b); nrm /= np.linalg.norm(nrm, axis=1, keepdims=True) + 1e-9
            sh = .6 + .4 * np.abs(nrm @ np.array([.3, -.5, .8]) / np.linalg.norm([.3, -.5, .8]))
            for t in np.argsort(-depth):
                r = m['R'][t]
                c = dress[r] if r < 4 else m['palette'][r - 4]
                rgb = ((c >> 16) & 255, (c >> 8) & 255, c & 255)
                d.polygon([(px[i], py[i]) for i in tri[t]], fill=tuple(int(v * sh[t]) for v in rgb))
            d.text((4, 4), f'{m["name"]} {clip}', fill=(0, 0, 0))
            sheet.paste(im, (pi * size, mi * size))
    sheet.save(path)


skin_mats_cache = {}

if __name__ == '__main__':
    rigs, models, rig_of = [], [], {}
    for name, (fid, sex, top, bottom) in FILES.items():
        gl = GLTF(fetch(name))
        if sex not in rig_of:
            cl = [(n, loop, d, [skin_mats(gl, G) for G in samples]) for n, loop, d, samples in clips(gl)]
            # sitting: the seat at height 0, the thighs resting on it
            rig_of[sex] = len(rigs)
            rigs.append((sex, cl))
            for n, loop, d, samples in cl:
                for k, mats in enumerate(samples): skin_mats_cache[(rig_of[sex], n, k)] = mats
        m = bake_model(name, gl, top, bottom)
        m['rig'] = rig_of[sex]
        models.append(m)
    # the sitting pose: lower everyone so the thighs rest on a seat at height 0
    for ri, (sex, cl) in enumerate(rigs):
        sample = next(c for c in cl if c[0] == 'Sit')[3][0]
        m = next(m for m in models if m['rig'] == ri)
        thigh = np.isin(m['B0'], [BONES.index('UpperLeg.L'), BONES.index('UpperLeg.R')])
        P = pose(m, sample)
        drop = np.percentile(P[thigh, 2], 3)
        cl[[c[0] for c in cl].index('Sit')] = ('Sit', False, 0.0, [[(R, t - np.array([0, 0, drop])) for R, t in sample]])
        skin_mats_cache[(ri, 'Sit', 0)] = cl[[c[0] for c in cl].index('Sit')][3][0]
        print(f'rig {sex}: sitting drops {drop:.2f} m to the seat')
    os.makedirs(OUT, exist_ok=True)
    n = write(rigs, models, os.path.join(OUT, 'people.bin'))
    with open(os.path.join(OUT, 'PEOPLE_CREDITS.txt'), 'w') as f:
        f.write('People for the HD renderer, baked by tools/assets/people.py from the Ultimate Modular Men and Ultimate Modular\n'
                'Women packs by Quaternius, released under CC0 1.0 (public domain):\n'
                'https://quaternius.com/packs/ultimatemodularcharacters.html\nhttps://quaternius.com/packs/ultimatemodularwomen.html\n\n')
        for name in FILES: f.write(f'{name}\n')
    print(n, 'bytes')
    if PREVIEW: preview(models, rigs, PREVIEW)
