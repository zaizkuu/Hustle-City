"""Cuts the 1.8 update trailer: what changed, shown side by side with how it looked before.

usage: python3 edit18.py <frames dir> <cinema dir> <ui stills dir> <score.wav> <out.mp4>
       python3 edit18.py ... --stills <dir> <frame> [frame ...]   writes single frames as JPG to check the look

<frames dir>/<shot>/NNNN.png come from render.mjs; a comparison shot <name> has <name>_before and <name>_after (the
same moment drawn without and with the 3D models, see CinematicTest.Pair2). <cinema dir>/c14_snow/NNNN/overlay.png are
the snowflakes, and <ui stills dir> holds the phone screens from CinematicUiTest (v_sell, v_landlord, v_slots).
"""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from kit import *

DUR = 53.0
NF = int(DUR * FPS)
FRAMES, CINEMA, UI, SCORE, OUT = sys.argv[1:6]
use(FRAMES, CINEMA)


# ---------------------------------------------------------------- the cut
# (start, end, kind, params); 120 BPM, every cut on a beat
EDL = [
    (0.0, 3.5, 'shot', dict(shot='v01_open', f0=8, look='dusk')),
    (3.5, 4.0, 'black', {}),
    (4.0, 6.0, 'shot', dict(shot='b1_title', f0=14, look='night')),
    (6.0, 12.0, 'compare', dict(shot='v03_market', f0=4, look='interior')),
    (12.0, 18.0, 'compare', dict(shot='v04_cafe', f0=4, look='interior')),
    (18.0, 24.0, 'compare', dict(shot='v05_street', f0=4, look='day')),
]
MONTAGE = [  # (shot, f0, word): half a second each from 24.0
    ('v06_fashion', 14, 'FASHION'), ('v07_jewelry', 14, 'JEWELRY'), ('v08_bakery', 14, 'BAKERY'), ('v09_florist', 14, 'FLOWERS'),
    ('v10_tech', 14, 'GADGETS'), ('v11_bar', 14, 'NIGHTLIFE'),
]
for i, (s, f0, wd) in enumerate(MONTAGE):
    EDL.append((24.0 + i * .5, 24.5 + i * .5, 'shot', dict(shot=s, f0=f0, look='interior' if s != 'v11_bar' else 'night', tag=wd)))
EDL += [
    (27.0, 31.0, 'shot', dict(shot='v12_crowd', f0=0, look='day')),
    (31.0, 33.0, 'shot', dict(shot='v13_night', f0=6, look='night')),
    (33.0, 35.0, 'device', dict(still='v_sell', bg=('c9_manila', 10, 'day'))),
    (35.0, 37.0, 'device', dict(still='v_landlord', bg=('b1_title', 20, 'night'))),
    (37.0, 39.0, 'device', dict(still='v_slots', bg=('v01_open', 40, 'dusk'))),
    (39.0, 41.0, 'shot', dict(shot='c9_manila', f0=6, look='day')),
    (41.0, 43.0, 'shot', dict(shot='c14_snow', f0=20, look='snow')),
]
# quick flips between before and after, a quarter second each
FLIPS = [('v03_market', 70, 0), ('v03_market', 70, 1), ('v04_cafe', 96, 0), ('v04_cafe', 96, 1), ('v05_street', 60, 0), ('v05_street', 60, 1),
         ('v04_cafe', 130, 0), ('v04_cafe', 130, 1)]
for i, (s, f, side) in enumerate(FLIPS):
    EDL.append((43.0 + i * .25, 43.25 + i * .25, 'flip', dict(shot=s, f0=f, side=side, look='day' if s == 'v05_street' else 'interior')))
EDL += [
    (45.0, 46.0, 'black', {}),
    (46.0, 53.0, 'shot', dict(shot='e1_finale', f0=0, look='night')),
]

CAPTIONS = [
    (0.45, 1.75, 'statement', ('SAME CITY.',)),
    (1.95, 3.45, 'statement', ('NEW EVERYTHING.',)),
    (4.0, 6.0, 'title', ()),
    (6.15, 11.85, 'headline', (['REAL PRODUCTS'], 'Fruit in crates, cans in the fridges, pizzas in the freezer')),
    (12.15, 17.85, 'headline', (['REAL FURNITURE'], 'Café tables, sofas, rugs, lamps and plants, all modelled in 3D')),
    (18.15, 23.85, 'headline', (['STREET FURNITURE'], 'Street lights, hydrants, benches, shelters and bike racks')),
    (27.1, 30.9, 'headline', (['PEOPLE, REBUILT'], 'Faces, hairstyles and real outfits, stepping round every bench')),
    (31.1, 32.9, 'headline', (['NOTHING OUT OF PLACE'], 'Every prop checked in all five cities')),
    (33.1, 34.9, 'side', (['SELL', 'ANYTHING'], 'Businesses, buildings and cars.')),
    (35.1, 36.9, 'side', (['BE THE', 'LANDLORD'], 'Rivals and families rent from you.')),
    (37.1, 38.9, 'side', (['SIX SAVE', 'SLOTS'], 'A different city in each.')),
    (39.1, 40.9, 'headline', (['LOCAL MONEY'], 'Pesos in Manila, yen in Tokyo, won in Seoul')),
    (41.0, 42.95, 'big', ('UPDATE 1.8', 'Furniture  ·  Products  ·  Streets  ·  People')),
    (46.0, 53.0, 'logo', ()),
]

FLASHES = [(4.0, .9, .4), (6.0, .35, .25), (12.0, .3, .2), (18.0, .3, .2), (27.0, .35, .25), (31.0, .25, .2), (33.0, .3, .2),
           (39.0, .25, .2), (41.0, .55, .35), (46.0, .9, .3)]
FLASHES += [(24.0 + i * .5, .3, .15) for i in range(6)]
FLASHES += [(43.0 + i * .25, .28 if i % 2 else .12, .1) for i in range(8)]
# a glint each time the divider sweeps in
FLASHES += [(t0 + 1.5, .1, .25) for t0 in (6.0, 12.0, 18.0)]


# ---------------------------------------------------------------- before and after
def split_at(lt):
    """Where the divider stands [lt] seconds into a comparison, as a share of the width (1 = all before)."""
    if lt < 1.5: return 1.0
    if lt < 2.1: return 1 - .5 * ease_in_out((lt - 1.5) / .6)
    if lt < 3.9: return .5 - .03 * math.sin((lt - 2.1) / 1.8 * math.pi)
    if lt < 4.5: return .5 * (1 - ease_in_out((lt - 3.9) / .6))
    return 0.0


def pill(layer, x, y, text, fill, a, anchor='left'):
    """A rounded label: BEFORE in grey, AFTER in orange."""
    if a <= 0: return
    f = font('InterDisplay-Bold', 26)
    tw = text_w(text, f, 5)
    pw, ph = tw + 44, 50
    if anchor == 'right': x -= pw
    elif anchor == 'center': x -= pw / 2
    d = ImageDraw.Draw(layer)
    d.rounded_rectangle((x, y, x + pw, y + ph), radius=25, fill=fill + (int(225 * a),))
    draw_text(d, x + 22, y + 10, text, f, (255, 255, 255, int(255 * a)), 5)


GREY = (60, 64, 72)


def compare_frame(t, t0, prm):
    lt = t - t0
    i = prm['f0'] + int(round(lt * FPS))
    b = grade(src(prm['shot'] + '_before', i), prm['look'])
    a = grade(src(prm['shot'] + '_after', i), prm['look'])
    s = split_at(lt)
    x = int(round(W * s))
    out = a.copy()
    out[:, :x] = b[:, :x]
    img = to_img(out).convert('RGBA')
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    if 0 < x < W:
        # the divider: a bright line with a soft glow and a round handle
        glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(glow).rectangle((x - 6, 0, x + 6, H), fill=(255, 255, 255, 120))
        glow = glow.filter(ImageFilter.GaussianBlur(10))
        layer.alpha_composite(glow)
        d = ImageDraw.Draw(layer)
        d.rectangle((x - 2, 0, x + 2, H), fill=(255, 255, 255, 255))
        cy = H * .5
        d.ellipse((x - 34, cy - 34, x + 34, cy + 34), fill=(255, 255, 255, 255))
        for sgn in (-1, 1):
            d.polygon([(x + sgn * 8, cy - 11), (x + sgn * 8, cy + 11), (x + sgn * 21, cy)], fill=(40, 44, 52, 255))
    # labels in the top corners, each while its side has room
    a_before = ease_out(lt / .35) * clamp((x - 260) / 120)
    a_after = clamp((W - x - 260) / 120) * (1 - ease_in_out((lt - 5.6) / .3))
    pill(layer, 64, 56, 'BEFORE', GREY, a_before)
    pill(layer, W - 64, 56, 'AFTER  ·  1.8', ORANGE, a_after, 'right')
    img.alpha_composite(layer)
    return img


def flip_frame(t, t0, prm):
    i = prm['f0'] + int(round((t - t0) * FPS))
    side = prm['side']
    a = grade(src(prm['shot'] + ('_after' if side else '_before'), i), prm['look'])
    img = to_img(a).convert('RGBA')
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    pill(layer, W / 2, 56, 'AFTER  ·  1.8' if side else 'BEFORE', ORANGE if side else GREY, 1.0, 'center')
    img.alpha_composite(layer)
    return img


# ---------------------------------------------------------------- the phone
def device_frame(t, t0, t1, prm):
    i = int(round((t - t0) * FPS))
    bshot, bf, blook = prm['bg']
    bg = grade(src(bshot, bf + i), blook, vig=1.6)
    bgi = to_img(bg).resize((W // 6, H // 6), Image.BILINEAR).filter(ImageFilter.GaussianBlur(5)).resize((W, H), Image.BICUBIC)
    a = np.asarray(bgi, np.float32) / 255 * .42
    a = a * np.array([.92, 1.0, 1.08], np.float32)
    frame = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)).convert('RGBA')
    screen = Image.open(os.path.join(UI, prm['still'] + '.png')).convert('RGBA')
    bgc = Image.new('RGBA', screen.size, (24, 26, 30, 255)); bgc.alpha_composite(screen)
    place_device(frame, device_image(bgc), t - t0, t1 - t0)
    return frame


def tag(layer, t, text):
    """The montage's word for each kind of shop: smaller than the old trailer's, so the shelves still show."""
    p = ease_out(t / .1)
    f = font('InterDisplay-Black', 132)
    w = text_w(text, f, -2)
    tmp = Image.new('RGBA', (int(w) + 40, 170), (0, 0, 0, 0))
    draw_text(ImageDraw.Draw(tmp), 20, 0, text, f, (255, 255, 255, 255), -2)
    s = 1.1 - .1 * p + .05 * t
    tmp = tmp.resize((int(tmp.size[0] * s), int(tmp.size[1] * s)), Image.BICUBIC)
    layer.alpha_composite(tmp, (int((W - tmp.size[0]) / 2), int(H / 2 - tmp.size[1] / 2 - 18)))


# ---------------------------------------------------------------- one frame
def letterbox(t):
    bar = 104
    if t < 4.0: return bar
    if t < 4.3: return bar * (1 - ease_out((t - 4.0) / .3))
    if t < 46.0: return 0
    return bar * ease_out((t - 46.0) / .7)


def frame_at(n):
    t = n / FPS
    cut = next((c for c in EDL if c[0] <= t < c[1]), EDL[-1])
    t0, t1, kind, prm = cut
    if kind == 'black':
        img = Image.new('RGBA', (W, H), (0, 0, 0, 255))
    elif kind == 'device':
        img = device_frame(t, t0, t1, prm)
    elif kind == 'compare':
        img = compare_frame(t, t0, prm)
    elif kind == 'flip':
        img = flip_frame(t, t0, prm)
    else:
        i = prm['f0'] + int(round((t - t0) * FPS))
        a = grade(src(prm['shot'], i), prm['look'])
        if prm['shot'] == 'c14_snow':  # the snowflakes ride on top of the graded city
            ov = overlay(prm['shot'], i)
            if ov:
                o = np.asarray(ov, np.float32) / 255
                k = o[..., 3:] * .85
                a = a * (1 - k) + o[..., :3] * k
        if prm.get('tag'): a *= .74
        if prm['shot'] == 'b1_title': a *= .5 + .25 * ease_in_out((t - 5.2) / .8)
        if prm['shot'] == 'e1_finale': a *= 1 - .32 * ease_out((t - 46.3) / 1.2)
        if t0 == 0.0 and t > 3.2:  # the opening bleaches out into the breath before the drop
            k = ease_in_out((t - 3.2) / .3)
            a = a + (1 - a) * k * .85
        img = to_img(a).convert('RGBA')
        if 4.0 <= t < 4.45:  # the slam shakes the camera
            k = (1 - (t - 4.0) / .45) ** 2
            dx, dy = 14 * k * math.sin(n * 2.3), 10 * k * math.cos(n * 3.1)
            img = img.transform((W, H), Image.AFFINE, (1 / 1.02, 0, dx + W * .01, 0, 1 / 1.02, dy + H * .01), Image.BILINEAR)
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    for c0, c1, ck, args in CAPTIONS:
        if not (c0 <= t < c1): continue
        lt, dur = t - c0, c1 - c0
        if ck == 'statement': statement(layer, lt, dur, *args)
        elif ck == 'title': title(layer, lt, sub='THE 1.8 UPDATE')
        elif ck == 'headline': headline(layer, lt, dur, args[0], args[1], 112, H - 96, 104, 30, 1060)
        elif ck == 'side': headline(layer, lt, dur, args[0], args[1], 112, H / 2 + 150, 84, 28, 520)
        elif ck == 'big': big(layer, lt, dur, *args)
        elif ck == 'logo': logo(layer, lt, tag='THE 1.8 UPDATE')
    if kind == 'shot' and prm.get('tag'): tag(layer, t - t0, prm['tag'])
    if kind != 'device' and any(c[2] == 'headline' and c[0] <= t < c[1] for c in CAPTIONS):
        img.alpha_composite(WASH)
    centre = 0.0
    for c0, c1, ck, args in CAPTIONS:
        if c0 <= t < c1 and ck in ('statement', 'title', 'big'): centre = max(centre, .55 * fade(t - c0, c1 - c0, .3, .3))
    if t >= 46.0: centre = max(centre, .8 * ease_out((t - 46.4) / 1.0))
    if centre > 0: img.alpha_composite(center_wash(centre))
    img.alpha_composite(shadowed(layer))
    a = np.asarray(img.convert('RGB'), np.float32) / 255
    fl = sum(s * math.exp(-(t - ft) / d) for ft, s, d in FLASHES if t >= ft)
    if fl > 0: a = a + (1 - a) * min(1, fl)
    g = GRAIN[n % len(GRAIN)]
    g = np.repeat(np.repeat(g, 2, 0), 2, 1)
    a += g[..., None] * .016
    bar = int(round(letterbox(t)))
    if bar: a[:bar] = 0; a[H - bar:] = 0
    if t >= 46.0:
        im = to_img(a).convert('RGBA')
        fl = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        footer(fl, t - 46.0, foot='ANDROID  ·  VERSION 1.8 BETA')
        im.alpha_composite(fl)
        a = np.asarray(im.convert('RGB'), np.float32) / 255
    if t < .6: a *= ease_in_out(t / .6)
    if t >= DUR - 1.0: a *= 1 - ease_in_out((t - (DUR - 1.0)) / .9)
    return to_img(a)


if __name__ == '__main__':
    if '--stills' in sys.argv:
        k = sys.argv.index('--stills')
        stills(frame_at, sys.argv[k + 1], sys.argv[k + 2:])
        sys.exit(0)
    encode(frame_at, NF, SCORE, OUT)
