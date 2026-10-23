"""Cuts the rendered shots into the 60-second cinematic trailer: grade, letterbox, captions, phone mockups, logo.

usage: python3 edit.py <frames dir> <cinema dir> <ui stills dir> <score.wav> <out.mp4>
       python3 edit.py ... --stills <dir> <frame> [frame ...]   writes single frames as JPG to check the look

<frames dir>/<shot>/NNNN.png come from render.mjs, <cinema dir>/<shot>/NNNN/overlay.png are the HUD layers of the
gameplay shots, and <ui stills dir> holds the phone-app screenshots from CinematicUiTest.
"""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from kit import *

DUR = 60.0
NF = int(DUR * FPS)
FRAMES, CINEMA, UI, SCORE, OUT = sys.argv[1:6]
use(FRAMES, CINEMA)
WEATHER = ('c6_rain', 'c14_snow')


# ---------------------------------------------------------------- the cut
# (start, end, kind, params). Shots are 24 fps; f0 is the first source frame, step 2 plays at double speed.
EDL = [
    (0.0, 3.0, 'shot', dict(shot='a1_nyc', f0=9, look='dusk')),
    (3.0, 6.0, 'shot', dict(shot='a2_manila', f0=9, look='day')),
    (6.0, 8.0, 'shot', dict(shot='a3_tokyo', f0=9, look='dusk')),
    (8.0, 10.0, 'shot', dict(shot='a4_seoul', f0=9, look='dusk')),
    (10.0, 11.5, 'shot', dict(shot='a5_classic', f0=3, look='dusk')),
    (11.5, 12.0, 'black', {}),
    (12.0, 14.0, 'shot', dict(shot='b1_title', f0=14, look='night')),
    (14.0, 16.0, 'shot', dict(shot='c2_market', f0=6, look='interior')),
    (16.0, 18.0, 'shot', dict(shot='c3_cafe', f0=6, look='interior')),
    (18.0, 20.0, 'device', dict(shot='u1_designer', f0=10, look='interior', bg=('u1_designer', 10))),
    (20.0, 22.0, 'device', dict(still='app_schedule', bg=('c3_cafe', 20))),
    (22.0, 24.0, 'device', dict(shot='u2_drive', f0=12, look='day', bg=('u2_drive', 12))),
    (24.0, 26.0, 'shot', dict(shot='c6_rain', f0=6, look='rain')),
    (26.0, 28.0, 'shot', dict(shot='c7_fleet', f0=9, look='day')),
    (28.0, 30.0, 'device', dict(still='app_fleet', bg=('c7_fleet', 8))),
    (30.0, 32.0, 'shot', dict(shot='c9_manila', f0=6, look='day')),
    (32.0, 34.0, 'shot', dict(shot='c10_yokocho', f0=6, look='night')),
    (34.0, 36.0, 'device', dict(still='app_buyout', bg=('c16_scramble', 8))),
    (36.0, 38.0, 'device', dict(still='app_markets', bg=('a1_nyc', 30))),
    (38.0, 40.0, 'shot', dict(shot='c13_hypercar', f0=6, look='day')),
    (40.0, 42.0, 'shot', dict(shot='c14_snow', f0=33, look='snow')),
    (42.0, 43.0, 'shot', dict(shot='c17_times', f0=10, look='night')),
    (43.0, 44.0, 'shot', dict(shot='c15_seoul_night', f0=20, look='night')),
    (44.0, 46.0, 'shot', dict(shot='c16_scramble', f0=12, look='night')),
]
MONTAGE = [  # (shot, f0, look, word): half a second each from 46.0, at double speed
    ('a2_manila', 30, 'day', 'BUILD'), ('c3_cafe', 8, 'interior', 'DESIGN'), ('c2_market', 10, 'interior', 'HIRE'),
    ('c13_hypercar', 20, 'day', 'DRIVE'), ('c7_fleet', 12, 'day', 'DELIVER'), ('c16_scramble', 20, 'night', 'COMPETE'),
    ('a1_nyc', 40, 'dusk', 'INVEST'), ('a3_tokyo', 20, 'dusk', 'EXPAND'),
]
STROBE = [('c6_rain', 20, 'rain'), ('c10_yokocho', 24, 'night'), ('c15_seoul_night', 20, 'night'), ('b1_title', 30, 'night'),
          ('c9_manila', 20, 'day'), ('c17_times', 30, 'night'), ('c14_snow', 50, 'snow'), ('c13_hypercar', 36, 'day')]
for i, (s, f0, look, wd) in enumerate(MONTAGE):
    EDL.append((46.0 + i * .5, 46.5 + i * .5, 'shot', dict(shot=s, f0=f0, look=look, step=2, word=wd)))
for i, (s, f0, look) in enumerate(STROBE):
    EDL.append((50.0 + i * .25, 50.25 + i * .25, 'shot', dict(shot=s, f0=f0, look=look, step=2, strobe=True)))
EDL += [
    (52.0, 53.0, 'black', {}),
    (53.0, 60.0, 'shot', dict(shot='e1_finale', f0=0, look='night')),
]

# captions: (start, end, kind, args)
CAPTIONS = [
    (0.45, 2.85, 'statement', ('EVERY EMPIRE',)),
    (3.4, 5.85, 'statement', ('STARTS WITH ONE STORE',)),
    (.5, 2.85, 'city', ('NEW YORK', 'Midtown  ·  Central Park')),
    (3.5, 5.85, 'city', ('MANILA', 'Binondo  ·  LRT Line 1')),
    (6.3, 7.85, 'city', ('TOKYO', 'Minato  ·  Tokyo Tower')),
    (8.3, 9.85, 'city', ('SEOUL', 'Jongno  ·  Gwanghwamun')),
    (10.2, 11.45, 'city', ('HUSTLE CITY', 'The original grid')),
    (12.0, 14.0, 'title', ()),
    (14.1, 17.9, 'headline', (['BUILD A BUSINESS'], '21 business types, from coffee shops to nightclubs')),
    (18.1, 21.9, 'side', (['DESIGN IT.', 'STAFF IT.'], 'Place every shelf. Hire, train and schedule your team.')),
    (22.1, 23.9, 'side', (['OWN THE', 'STREETS'], 'Drive anywhere. The GPS knows the way.')),
    (24.1, 25.9, 'headline', (['RAIN OR SHINE'], 'The weather changes who shops, and when')),
    (26.1, 27.9, 'headline', (['RUN A FLEET'], 'Trucks, drivers and delivery routes')),
    (28.1, 29.9, 'side', (['AUTO-', 'RESTOCK'], 'Set the stops once. Your drivers do the rest.')),
    (30.1, 33.9, 'headline', (['A CITY THAT LIVES'], 'Crowds, traffic and street life on every block')),
    (34.1, 35.9, 'side', (['BUY OUT', 'YOUR RIVALS'], 'Every competitor has a price.')),
    (36.1, 37.9, 'side', (['PLAY THE', 'MARKETS'], 'Stocks, bonds and hostile takeovers.')),
    (38.1, 39.9, 'headline', (['LIVE LARGE'], 'Hypercars, mansions and the good life')),
    (40.1, 41.9, 'headline', (['EVERY SEASON'], 'Summer nights to winter snow')),
    (42.0, 43.95, 'big', ('FIVE CITIES.', 'New York  ·  Manila  ·  Tokyo  ·  Seoul  ·  Hustle City')),
    (44.0, 45.95, 'big', ('ONE HUSTLE.', None)),
    (53.0, 60.0, 'logo', ()),
]

# white flashes on the hits: (time, strength, decay seconds)
FLASHES = [(3.0, .35, .25), (6.0, .35, .25), (8.0, .35, .25), (10.0, .3, .25), (12.0, .9, .4), (42.0, .3, .25), (43.0, .2, .15),
           (44.0, .55, .35), (46.0, .6, .3), (53.0, .9, .3)]
FLASHES += [(46.0 + i * .5, .25, .15) for i in range(1, 8)]
FLASHES += [(50.0 + i * .25, .2, .1) for i in range(8)]


def device_frame(t, t0, t1, prm):
    i = int(round((t - t0) * FPS))
    bshot, bf = prm['bg']
    bg = grade(src(bshot, bf + i), 'night' if bshot in ('c16_scramble',) else 'day', vig=1.6)
    bgi = to_img(bg).resize((W // 6, H // 6), Image.BILINEAR).filter(ImageFilter.GaussianBlur(5)).resize((W, H), Image.BICUBIC)
    a = np.asarray(bgi, np.float32) / 255 * .42
    a = a * np.array([.92, 1.0, 1.08], np.float32)
    frame = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)).convert('RGBA')
    if 'still' in prm:
        screen = Image.open(os.path.join(UI, prm['still'] + '.png')).convert('RGBA')
        bgc = Image.new('RGBA', screen.size, (24, 26, 30, 255)); bgc.alpha_composite(screen); screen = bgc
    else:
        f = prm['f0'] + i
        screen = to_img(grade(src(prm['shot'], f), prm['look'], vig=0)).convert('RGBA')
        ov = overlay(prm['shot'], f)
        if ov: screen.alpha_composite(ov)
    place_device(frame, device_image(screen), t - t0, t1 - t0)
    return frame


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
    elif kind == 'device':
        img = device_frame(t, t0, t1, prm)
    else:
        i = int(round((t - t0) * FPS)) * prm.get('step', 1)
        a = grade(src(prm['shot'], prm['f0'] + i), prm['look'])
        if prm['shot'] in WEATHER:  # rain streaks and snowflakes ride on top of the graded city
            ov = overlay(prm['shot'], prm['f0'] + i)
            if ov:
                o = np.asarray(ov, np.float32) / 255
                k = o[..., 3:] * .85
                a = a * (1 - k) + o[..., :3] * k
        if prm.get('word'): a *= .62  # the montage words sit on darkened plates
        if prm['shot'] == 'b1_title': a *= .5 + .25 * ease_in_out((t - 13.2) / .8)  # a dark plate for the title
        if prm['shot'] == 'e1_finale': a *= 1 - .32 * ease_out((t - 53.3) / 1.2)
        if t0 <= 10.0 and t1 >= 11.5 and t > 11.2:  # Act 1 bleaches out into the breath before the drop
            k = ease_in_out((t - 11.2) / .3)
            a = a + (1 - a) * k * .85
        img = to_img(a).convert('RGBA')
        if t >= 12.0 and t < 12.45:  # the slam shakes the camera
            k = (1 - (t - 12.0) / .45) ** 2
            dx, dy = 14 * k * math.sin(n * 2.3), 10 * k * math.cos(n * 3.1)
            img = img.transform((W, H), Image.AFFINE, (1 / 1.02, 0, dx + W * .01, 0, 1 / 1.02, dy + H * .01), Image.BILINEAR)
    # captions
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    for c0, c1, ck, args in CAPTIONS:
        if not (c0 <= t < c1): continue
        lt, dur = t - c0, c1 - c0
        if ck == 'statement': statement(layer, lt, dur, *args)
        elif ck == 'city': city(layer, lt, dur, *args)
        elif ck == 'title': title(layer, lt)
        elif ck == 'headline': headline(layer, lt, dur, args[0], args[1], 112, H - 96, 112, 30, 1100)
        elif ck == 'side': headline(layer, lt, dur, args[0], args[1], 112, H / 2 + 150, 84, 28, 520)
        elif ck == 'big': big(layer, lt, dur, *args)
        elif ck == 'logo': logo(layer, lt)
    if kind == 'shot' and prm.get('word'): word(layer, t - t0, prm['word'])
    if kind != 'device' and any(c[2] in ('headline', 'city') and c[0] <= t < c[1] for c in CAPTIONS):
        # a soft dark wash in the lower left keeps the captions legible
        img.alpha_composite(WASH)
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


# ---------------------------------------------------------------- main
if __name__ == '__main__':
    if '--stills' in sys.argv:
        k = sys.argv.index('--stills')
        stills(frame_at, sys.argv[k + 1], sys.argv[k + 2:])
        sys.exit(0)
    encode(frame_at, NF, SCORE, OUT)
