"""Cuts the Manila showcase trailer: 60 seconds from dawn over the bay, through the streets and the night, to sunset.

usage: python3 manila.py <frames dir> <cinema dir> <score.wav> <out.mp4>
       python3 manila.py ... --stills <dir> <frame> [frame ...]   writes single frames as JPG to check the look

The shots are CinematicTest's m01 .. m21, rendered with render.mjs into <frames dir>; <cinema dir> holds their
recordings, whose overlays carry the rain of the monsoon shot.
"""
import math, sys
import numpy as np
from PIL import Image, ImageDraw
from kit import *

DUR = 60.0
NF = int(DUR * FPS)
FRAMES, CINEMA, SCORE, OUT = sys.argv[1:5]
use(FRAMES, CINEMA)
WEATHER = ('m14_rain',)

LOOKS.update({  # exposure, contrast, saturation, shadow tint, highlight tint, bloom amount, bloom threshold
    'dawn': (1.04, .22, 1.08, (-.01, .0, .035), (.04, .018, .0), .34, .70),
    'morning': (1.03, .2, 1.12, (-.008, .004, .02), (.03, .016, -.012), .24, .78),
    'golden': (1.04, .24, 1.16, (-.014, .002, .024), (.045, .02, -.026), .3, .74),
    'sunset': (1.03, .26, 1.18, (-.02, .0, .04), (.05, .016, -.03), .42, .66),
})

# ---------------------------------------------------------------- the cut
# (start, end, kind, params). Shots are 24 fps; f0 is the first source frame, step 2 plays at double speed.
EDL = [
    (0.0, 4.0, 'shot', dict(shot='m01_dawn', f0=8, look='dawn')),
    (4.0, 8.0, 'shot', dict(shot='m02_cathedral', f0=8, look='morning')),
    (8.0, 10.0, 'shot', dict(shot='m03_rizal', f0=8, look='morning')),
    (10.0, 11.5, 'shot', dict(shot='m04_cityhall', f0=6, look='sunset')),
    (11.5, 12.0, 'black', {}),
    (12.0, 14.0, 'shot', dict(shot='m05_arch', f0=8, look='dusk')),
    (14.0, 16.0, 'shot', dict(shot='m06_wires', f0=8, look='day')),
    (16.0, 18.0, 'shot', dict(shot='m07_jeepneys', f0=8, look='day')),
    (18.0, 20.0, 'shot', dict(shot='m08_lrt', f0=8, look='day')),
    (20.0, 22.0, 'shot', dict(shot='m09_barangay', f0=8, look='golden')),
    (22.0, 24.0, 'shot', dict(shot='m10_binondo', f0=8, look='day')),
    (24.0, 26.0, 'shot', dict(shot='m11_quiapo', f0=8, look='morning')),
    (26.0, 28.0, 'shot', dict(shot='m12_terminal', f0=8, look='morning')),
    (28.0, 30.0, 'shot', dict(shot='m13_edsa', f0=8, look='sunset')),
    (30.0, 32.0, 'shot', dict(shot='m14_rain', f0=8, look='rain')),
    (32.0, 34.0, 'shot', dict(shot='m15_arch_night', f0=8, look='night')),
    (34.0, 36.0, 'shot', dict(shot='m16_cathedral_night', f0=8, look='night')),
    (36.0, 40.0, 'shot', dict(shot='m17_makati', f0=8, look='night')),
    (40.0, 42.0, 'shot', dict(shot='m18_bay_night', f0=8, look='night')),
    (42.0, 44.0, 'shot', dict(shot='m19_binondo_night', f0=8, look='night')),
    (44.0, 46.0, 'shot', dict(shot='m20_quiapo_night', f0=8, look='night')),
]
# the four words a Manileño lives by, a second each from 46.0 at double speed
MONTAGE = [('m12_terminal', 6, 'morning', 'SIPAG', 'HARD WORK'), ('m10_binondo', 6, 'day', 'TIYAGA', 'PATIENCE'),
           ('m07_jeepneys', 8, 'day', 'DISKARTE', 'STREET SMARTS'), ('m17_makati', 30, 'night', 'PANGARAP', 'BIG DREAMS')]
STROBE = [('m06_wires', 20, 'day'), ('m14_rain', 20, 'rain'), ('m11_quiapo', 24, 'morning'), ('m13_edsa', 20, 'sunset'),
          ('m15_arch_night', 20, 'night'), ('m09_barangay', 24, 'golden'), ('m02_cathedral', 60, 'morning'), ('m18_bay_night', 20, 'night')]
for i, (s, f0, look, wd, sub) in enumerate(MONTAGE):
    EDL.append((46.0 + i, 47.0 + i, 'shot', dict(shot=s, f0=f0, look=look, step=2, word=wd, sub=sub)))
for i, (s, f0, look) in enumerate(STROBE):
    EDL.append((50.0 + i * .25, 50.25 + i * .25, 'shot', dict(shot=s, f0=f0, look=look, step=2)))
EDL += [
    (52.0, 53.0, 'black', {}),
    (53.0, 60.0, 'shot', dict(shot='m21_finale', f0=0, look='sunset')),
]

CAPTIONS = [
    (0.45, 3.85, 'statement', ('THE PEARL OF THE ORIENT',)),
    (4.5, 7.85, 'city', ('MANILA', 'Intramuros  ·  Manila Cathedral')),
    (8.3, 9.85, 'city', ('RIZAL PARK', 'The Rizal Monument  ·  Luneta')),
    (10.2, 11.45, 'city', ('CITY HALL', 'The clock tower on Taft Avenue')),
    (12.0, 14.0, 'title', ()),
    (14.1, 15.9, 'side', (['SPAGHETTI', 'WIRES'], 'Every pole a beautiful tangle.')),
    (16.1, 17.9, 'headline', (['KING OF THE ROAD'], 'Jeepneys old and new, tricycles and mopeds in every lane')),
    (18.1, 19.9, 'headline', (['LRT LINE 1'], 'Trains overhead, the city underneath')),
    (20.1, 21.9, 'side', (['FIESTA', 'EVERY DAY'], 'Banderitas over the street, a hoop on every corner.')),
    (22.1, 23.9, 'headline', (['BINONDO'], "The world's oldest Chinatown, a food cart on every corner")),
    (24.1, 25.9, 'headline', (['QUIAPO'], "Crowds at the Black Nazarene's basilica")),
    (26.1, 27.9, 'side', (['PARA PO!'], 'Jeepney terminals keep the city moving.')),
    (28.1, 29.9, 'headline', (['EDSA AT RUSH HOUR'], 'Bring patience. And a full tank.')),
    (30.1, 31.9, 'headline', (['MONSOON NIGHTS'], 'When the rain comes, the city shines')),
    (32.1, 35.9, 'headline', (['FROM SARI-SARI', 'TO SKYSCRAPER'], 'Start with a corner store in Tondo. Finish on top of Makati.')),
    (36.1, 39.9, 'headline', (['MAKATI'], 'Where the deals are made. Your tower is waiting.')),
    (40.1, 41.9, 'headline', (['THE BAY AT NIGHT'], 'The MOA Eye turns over Manila Bay')),
    (42.0, 43.95, 'big', ('ONE CITY.', 'Tondo  ·  Binondo  ·  Quiapo  ·  Intramuros  ·  Ermita  ·  Paco  ·  Makati')),
    (44.0, 45.95, 'big', ('ENDLESS HUSTLE.', None)),
    (53.0, 60.0, 'logo', ()),
]

# white flashes on the hits: (time, strength, decay seconds)
FLASHES = [(4.0, .3, .25), (8.0, .3, .25), (10.0, .25, .2), (12.0, .9, .4), (32.0, .3, .25), (36.0, .3, .25), (42.0, .3, .25),
           (44.0, .55, .35), (46.0, .6, .3), (53.0, .9, .3)]
FLASHES += [(46.0 + i, .3, .18) for i in range(1, 4)]
FLASHES += [(50.0 + i * .25, .2, .1) for i in range(8)]


def word_sub(layer, t, text, sub):
    """A montage word with its meaning in small capitals under it."""
    word(layer, t, text)
    fs = font('Inter-SemiBold', 30)
    a = ease_out((t - .15) / .3)
    draw_text(ImageDraw.Draw(layer), (W - text_w(sub, fs, 12)) / 2, H / 2 + 118, sub, fs, (255, 214, 170, int(240 * a)), 12)


# ---------------------------------------------------------------- one frame
def letterbox(t):
    bar = 104
    if t < 12.0: return bar
    if t < 12.3: return bar * (1 - ease_out((t - 12.0) / .3))
    if t < 53.0: return 0
    return bar * ease_out((t - 53.0) / .7)


def frame_at(n):
    t = n / FPS
    cut = next((c for c in EDL if c[0] <= t < c[1]), EDL[-1])
    t0, t1, kind, prm = cut
    if kind == 'black':
        img = Image.new('RGBA', (W, H), (0, 0, 0, 255))
    else:
        i = int(round((t - t0) * FPS)) * prm.get('step', 1)
        a = grade(src(prm['shot'], prm['f0'] + i), prm['look'])
        if prm['shot'] in WEATHER:  # rain streaks ride on top of the graded city
            ov = overlay(prm['shot'], prm['f0'] + i)
            if ov:
                o = np.asarray(ov, np.float32) / 255
                k = o[..., 3:] * .85
                a = a * (1 - k) + o[..., :3] * k
        if prm.get('word'): a *= .58  # the montage words sit on darkened plates
        if prm['shot'] == 'm05_arch': a *= .5 + .25 * ease_in_out((t - 13.2) / .8)  # a dark plate for the title
        if prm['shot'] == 'm21_finale': a *= 1 - .3 * ease_out((t - 53.3) / 1.2)
        if t0 == 10.0 and t > 11.2:  # the clock tower bleaches out into the breath before the drop
            k = ease_in_out((t - 11.2) / .3)
            a = a + (1 - a) * k * .85
        img = to_img(a).convert('RGBA')
        if 12.0 <= t < 12.45:  # the slam shakes the camera
            k = (1 - (t - 12.0) / .45) ** 2
            dx, dy = 14 * k * math.sin(n * 2.3), 10 * k * math.cos(n * 3.1)
            img = img.transform((W, H), Image.AFFINE, (1 / 1.02, 0, dx + W * .01, 0, 1 / 1.02, dy + H * .01), Image.BILINEAR)
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    for c0, c1, ck, args in CAPTIONS:
        if not (c0 <= t < c1): continue
        lt, dur = t - c0, c1 - c0
        if ck == 'statement': statement(layer, lt, dur, *args)
        elif ck == 'city': city(layer, lt, dur, *args)
        elif ck == 'title': title(layer, lt, sub='MANILA  ·  NOW OPEN FOR BUSINESS')
        elif ck == 'headline': headline(layer, lt, dur, args[0], args[1], 112, H - 96, 112, 30, 1100)
        elif ck == 'side': headline(layer, lt, dur, args[0], args[1], 112, H / 2 + 150, 84, 28, 520)
        elif ck == 'big': big(layer, lt, dur, *args)
        elif ck == 'logo': logo(layer, lt, tag='BUILD YOUR EMPIRE IN MANILA')
    if kind == 'shot' and prm.get('word'): word_sub(layer, t - t0, prm['word'], prm['sub'])
    if any(c[2] in ('headline', 'city', 'side') and c[0] <= t < c[1] for c in CAPTIONS):
        img.alpha_composite(WASH)  # a soft dark wash in the lower left keeps the captions legible
    centre = 0.0
    for c0, c1, ck, args in CAPTIONS:
        if c0 <= t < c1 and ck in ('statement', 'title', 'big'): centre = max(centre, .55 * fade(t - c0, c1 - c0, .3, .3))
    if t >= 53.0: centre = max(centre, .8 * ease_out((t - 53.4) / 1.0))
    if centre > 0: img.alpha_composite(center_wash(centre))
    img.alpha_composite(shadowed(layer))
    # flashes, bars and grain
    a = np.asarray(img.convert('RGB'), np.float32) / 255
    fl = sum(s * math.exp(-(t - ft) / d) for ft, s, d in FLASHES if t >= ft)
    if fl > 0: a = a + (1 - a) * min(1, fl)
    g = GRAIN[n % len(GRAIN)]
    g = np.repeat(np.repeat(g, 2, 0), 2, 1)
    a += g[..., None] * .016
    bar = int(round(letterbox(t)))
    if bar: a[:bar] = 0; a[H - bar:] = 0
    if t >= 53.0:
        im = to_img(a).convert('RGBA')
        fl = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        footer(fl, t - 53.0)
        im.alpha_composite(fl)
        a = np.asarray(im.convert('RGB'), np.float32) / 255
    if t < .6: a *= ease_in_out(t / .6)
    if t >= 59.0: a *= 1 - ease_in_out((t - 59.0) / .9)
    return to_img(a)


if __name__ == '__main__':
    if '--stills' in sys.argv:
        k = sys.argv.index('--stills')
        stills(frame_at, sys.argv[k + 1], sys.argv[k + 2:])
        sys.exit(0)
    encode(frame_at, NF, SCORE, OUT)
