"""Bakes the HD renderer's photo materials from CC0 scans on ambientCG (https://ambientcg.com).

usage: python3 tools/assets/textures.py [cache dir]

For each material it writes two 512 x 512 tileable maps into app/src/main/resources/textures/:
  <name>_detail.jpg  grey: the scan's brightness relative to its average (128 = unchanged, 255 = twice as bright).
                     The game keeps its own colours and multiplies this in, so a red-brick scan gives any brick
                     wall its courses and mortar.
  <name>_bump.jpg    red, green: the surface normal's tilt (OpenGL convention, 128 = flat), already scaled by the
                     material's bump strength; blue: roughness.
The order of MATERIALS is the layer order the shader samples (Shaders.kt names the layers). The scans are
downloaded into the cache dir (default /tmp/ambientcg) with ambientCG's own download links, unless already there.
"""
import os, subprocess, sys, zipfile
import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, 'app/src/main/resources/textures')
CACHE = sys.argv[1] if len(sys.argv) > 1 else '/tmp/ambientcg'
SIZE = 512

MATERIALS = [  # (name, ambientCG asset, detail contrast, bump strength)
    ('brick', 'Bricks059', .9, 1.0),
    ('stone', 'PavingStones128', .75, .7),
    ('asphalt', 'Asphalt031', .9, .5),
    ('slabs', 'Concrete046', .8, .35),
    ('grass', 'Grass004', .85, .6),
    ('wood', 'WoodFloor040', .8, .45),
    ('tiles', 'Tiles107', .7, .5),
    ('metal', 'CorrugatedSteel005', .6, 1.0),
    ('gravel', 'Gravel043', .85, .7),
    ('plaster', 'Plaster001', .7, .5),
    ('concrete', 'Concrete031', .75, .4),
]


def fetch(asset):
    d = os.path.join(CACHE, asset)
    if not os.path.isdir(d):
        os.makedirs(CACHE, exist_ok=True)
        z = os.path.join(CACHE, asset + '_1K-JPG.zip')
        if not os.path.exists(z):
            subprocess.run(['curl', '-sS', '-f', '-L', '-o', z, f'https://ambientcg.com/get?file={asset}_1K-JPG.zip'], check=True)
        zipfile.ZipFile(z).extractall(d)
    return lambda m: os.path.join(d, f'{asset}_1K-JPG_{m}.jpg')


def tiled_resize(img):
    """Shrinks a tileable map to SIZE without seams: resample a 3 x 3 tiling and keep the middle."""
    w, h = img.size
    big = Image.new(img.mode, (w * 3, h * 3))
    for i in range(3):
        for j in range(3): big.paste(img, (i * w, j * h))
    big = big.resize((SIZE * 3, SIZE * 3), Image.LANCZOS)
    return big.crop((SIZE, SIZE, SIZE * 2, SIZE * 2))


def bake(name, asset, contrast, bump):
    path = fetch(asset)
    c = np.asarray(tiled_resize(Image.open(path('Color')).convert('RGB')), np.float32) / 255
    lum = (c ** 2.2) @ np.array([.2126, .7152, .0722], np.float32)  # linear light, as the shader multiplies it
    d = lum / lum.mean()
    d = np.clip(1 + (d - 1) * contrast, 0, 1.99)
    Image.fromarray((d * 128 + .5).astype(np.uint8), 'L').save(os.path.join(OUT, f'{name}_detail.jpg'), quality=90)

    n = np.asarray(tiled_resize(Image.open(path('NormalGL')).convert('RGB')), np.float32) / 255 * 2 - 1
    xy = n[..., :2] / np.maximum(n[..., 2:3], .2) * bump  # slopes, scaled; the shader rebuilds z
    xy = np.clip(xy, -1, 1)
    r = np.asarray(tiled_resize(Image.open(path('Roughness')).convert('L')), np.float32) / 255
    rgb = np.dstack([xy[..., 0] * .5 + .5, xy[..., 1] * .5 + .5, r])
    Image.fromarray((rgb * 255 + .5).astype(np.uint8), 'RGB').save(os.path.join(OUT, f'{name}_bump.jpg'), quality=92)


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for m in MATERIALS:
        bake(*m)
        print('baked', m[0], 'from', m[1])
    with open(os.path.join(OUT, 'CREDITS.txt'), 'w') as f:
        f.write('Photo materials for the HD renderer, baked by tools/assets/textures.py from scans on ambientCG,\n'
                'which are released under CC0 1.0 (public domain): https://ambientcg.com\n\n')
        for name, asset, _, _ in MATERIALS: f.write(f'{name:9s} {asset:20s} https://ambientcg.com/view?id={asset}\n')
