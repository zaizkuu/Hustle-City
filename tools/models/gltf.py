"""Minimal glTF 2.0 / GLB reader: triangles with their normals and colors, in the file's own (Y-up) space.

Only what furniture packs use: node hierarchies (matrix or TRS), indexed or plain triangle lists, POSITION, NORMAL,
COLOR_0 and TEXCOORD_0, and per-material base color factors and textures (sampled per vertex, so palette-textured
low-poly models come out as flat colors).
"""
import base64
import io
import json
import os
import struct

import numpy as np
from PIL import Image

COMPONENT = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}
WIDTH = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}


def _load(path):
    data = open(path, 'rb').read()
    if data[:4] == b'glTF':
        length = struct.unpack_from('<I', data, 8)[0]
        off = 12
        doc, binary = None, None
        while off < length:
            clen, ctype = struct.unpack_from('<II', data, off)
            chunk = data[off + 8:off + 8 + clen]
            if ctype == 0x4E4F534A:
                doc = json.loads(chunk.decode('utf-8'))
            elif ctype == 0x004E4942:
                binary = chunk
            off += 8 + clen
        return doc, binary
    return json.loads(data.decode('utf-8')), None


def _buffer(doc, binary, i, base):
    b = doc['buffers'][i]
    uri = b.get('uri')
    if uri is None:
        return binary
    if uri.startswith('data:'):
        return base64.b64decode(uri.split(',', 1)[1])
    return open(os.path.join(base, uri), 'rb').read()


def _accessor(doc, buffers, i):
    a = doc['accessors'][i]
    n = a['count'] * WIDTH[a['type']]
    dt = np.dtype(COMPONENT[a['componentType']])
    if 'bufferView' not in a:
        return np.zeros((a['count'], WIDTH[a['type']]), dtype=np.float32)
    bv = doc['bufferViews'][a['bufferView']]
    buf = buffers[bv['buffer']]
    start = bv.get('byteOffset', 0) + a.get('byteOffset', 0)
    stride = bv.get('byteStride', 0)
    w = WIDTH[a['type']]
    if stride and stride != dt.itemsize * w:
        rows = [np.frombuffer(buf, dtype=dt, count=w, offset=start + k * stride) for k in range(a['count'])]
        arr = np.stack(rows)
    else:
        arr = np.frombuffer(buf, dtype=dt, count=n, offset=start).reshape(a['count'], w) if w > 1 else np.frombuffer(buf, dtype=dt, count=n, offset=start)
    arr = arr.astype(np.float32)
    if a.get('normalized'):
        arr = arr / float(np.iinfo(dt).max)
    return arr


def _node_matrix(node):
    if 'matrix' in node:
        return np.array(node['matrix'], dtype=np.float64).reshape(4, 4).T
    t = np.array(node.get('translation', [0, 0, 0]), dtype=np.float64)
    x, y, z, w = node.get('rotation', [0, 0, 0, 1])
    s = np.array(node.get('scale', [1, 1, 1]), dtype=np.float64)
    r = np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ])
    m = np.eye(4)
    m[:3, :3] = r * s
    m[:3, 3] = t
    return m


def srgb(linear):
    linear = np.clip(linear, 0, 1)
    return np.where(linear <= 0.0031308, linear * 12.92, 1.055 * np.power(linear, 1 / 2.4) - 0.055)


class Triangles:
    """Flat triangle soup: positions (n*3, 3), normals (n*3, 3), sRGB colors (n*3, 4) in 0..1, material names per triangle."""

    def __init__(self):
        self.pos, self.nrm, self.col, self.mat, self.emit = [], [], [], [], []

    def done(self):
        self.pos = np.concatenate(self.pos) if self.pos else np.zeros((0, 3))
        self.nrm = np.concatenate(self.nrm) if self.nrm else np.zeros((0, 3))
        self.col = np.concatenate(self.col) if self.col else np.zeros((0, 4))
        return self


def read(path):
    doc, binary = _load(path)
    base = os.path.dirname(path)
    buffers = [_buffer(doc, binary, i, base) for i in range(len(doc.get('buffers', [])))]
    images = {}

    def image(i):
        if i not in images:
            im = doc['images'][i]
            if 'bufferView' in im:
                bv = doc['bufferViews'][im['bufferView']]
                raw = buffers[bv['buffer']][bv.get('byteOffset', 0):bv.get('byteOffset', 0) + bv['byteLength']]
                images[i] = np.asarray(Image.open(io.BytesIO(raw)).convert('RGBA'), dtype=np.float32) / 255.0
            else:
                uri = im['uri']
                p = os.path.join(base, uri) if os.path.exists(os.path.join(base, uri)) else os.path.join(base, 'Textures', os.path.basename(uri))
                images[i] = np.asarray(Image.open(p).convert('RGBA'), dtype=np.float32) / 255.0
        return images[i]

    out = Triangles()

    def visit(ni, parent):
        node = doc['nodes'][ni]
        m = parent @ _node_matrix(node)
        if 'mesh' in node:
            for prim in doc['meshes'][node['mesh']]['primitives']:
                if prim.get('mode', 4) != 4:
                    continue
                at = prim['attributes']
                p = _accessor(doc, buffers, at['POSITION'])
                idx = _accessor(doc, buffers, prim['indices']).astype(np.int64).ravel() if 'indices' in prim else np.arange(len(p))
                mat = doc['materials'][prim['material']] if 'material' in prim else {}
                pbr = mat.get('pbrMetallicRoughness', {})
                factor = np.array(pbr.get('baseColorFactor', [1, 1, 1, 1]), dtype=np.float32)
                cols = np.tile(np.concatenate([srgb(factor[:3]), factor[3:]]), (len(p), 1))
                tex = pbr.get('baseColorTexture')
                if tex is not None and 'TEXCOORD_%d' % tex.get('texCoord', 0) in at:
                    uv = _accessor(doc, buffers, at['TEXCOORD_%d' % tex.get('texCoord', 0)])
                    img = image(doc['textures'][tex['index']]['source'])
                    h, w = img.shape[:2]
                    u = np.mod(uv[:, 0], 1.0); v = np.mod(uv[:, 1], 1.0)
                    px = np.clip((u * w).astype(int), 0, w - 1); py = np.clip((v * h).astype(int), 0, h - 1)
                    texel = img[py, px]
                    cols = np.concatenate([texel[:, :3] * factor[:3], texel[:, 3:] * factor[3:]], axis=1)
                if 'COLOR_0' in at:
                    vc = _accessor(doc, buffers, at['COLOR_0'])
                    cols[:, :3] = cols[:, :3] * srgb(vc[:, :3])
                # into the parent's space
                ph = np.concatenate([p, np.ones((len(p), 1))], axis=1) @ m.T
                pw = ph[:, :3]
                tri = idx.reshape(-1, 3)
                a, b, c = pw[tri[:, 0]], pw[tri[:, 1]], pw[tri[:, 2]]
                face = np.cross(b - a, c - a)
                fl = np.linalg.norm(face, axis=1, keepdims=True)
                keep = fl[:, 0] > 1e-12
                tri = tri[keep]; face = face[keep] / fl[keep]
                if 'NORMAL' in at:
                    n = _accessor(doc, buffers, at['NORMAL'])
                    nm = np.linalg.inv(m[:3, :3]).T
                    nw = n @ nm.T
                    nw = nw / np.maximum(np.linalg.norm(nw, axis=1, keepdims=True), 1e-9)
                    nrm = nw[tri.ravel()]
                else:
                    nrm = np.repeat(face, 3, axis=0)
                # a mirrored transform turns faces inside out: keep normals facing their triangle's outside
                if np.linalg.det(m[:3, :3]) < 0:
                    tri = tri[:, ::-1]
                    if 'NORMAL' in at:
                        nrm = nw[tri.ravel()]
                out.pos.append(pw[tri.ravel()])
                out.nrm.append(nrm)
                out.col.append(cols[tri.ravel()])
                name = mat.get('name', '')
                emissive = max(mat.get('emissiveFactor', [0, 0, 0])) > .05
                out.mat += [name] * len(tri)
                out.emit += [emissive] * len(tri)
        for ci in node.get('children', []):
            visit(ci, m)

    scene = doc['scenes'][doc.get('scene', 0)]
    for ni in scene['nodes']:
        visit(ni, np.eye(4))
    return out.done()
