"""Cuts the rendered shots into the 60-second cinematic trailer: grade, letterbox, captions, phone mockups, logo.

usage: python3 edit.py <frames dir> <cinema dir> <ui stills dir> <score.wav> <out.mp4>
       python3 edit.py ... --stills <dir> <frame> [frame ...]   writes single frames as JPG to check the look

<frames dir>/<shot>/NNNN.png come from render.mjs, <cinema dir>/<shot>/NNNN/overlay.png are the HUD layers of the
gameplay shots, and <ui stills dir> holds the phone-app screenshots from CinematicUiTest.
"""
import math, os, subprocess, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H, FPS = 1920, 1080, 24
DUR = 60.0
NF = int(DUR * FPS)
FRAMES, CINEMA, UI, SCORE, OUT = sys.argv[1:6]

INTER = '/usr/share/fonts/opentype/inter/'
_fonts = {}


def font(name, size):
    k = (name, size)
    if k not in _fonts: _fonts[k] = ImageFont.truetype(INTER + name + '.otf', size)
    return _fonts[k]


ORANGE = (242, 153, 74)
WEATHER = ('c6_rain', 'c14_snow')
BLUE_A, BLUE_B = (47, 128, 237), (27, 63, 139)
GREEN = (39, 174, 96)
WHITE = (255, 255, 255)


# ---------------------------------------------------------------- timing helpers
def clamp(x, a=0.0, b=1.0): return a if x < a else b if x > b else x
def ease_out(x): x = clamp(x); return 1 - (1 - x) ** 3
def ease_in_out(x): x = clamp(x); return x * x * (3 - 2 * x)
def ramp(t, a, b): return clamp((t - a) / (b - a)) if b > a else float(t >= a)


def fade(t, dur, fin=.3, fout=.3):
    """Alpha for an element shown from 0 to dur: eases in, holds, eases out."""
    return ease_out(t / fin) * (1 - ease_in_out((t - (dur - fout)) / fout)) if dur > 0 else 0


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
    (42.0, 44.0, 'shot', dict(shot='c15_seoul_night', f0=6, look='night')),
    (44.0, 46.0, 'shot', dict(shot='c16_scramble', f0=12, look='night')),
]
MONTAGE = [  # (shot, f0, look, word): half a second each from 46.0, at double speed
    ('a2_manila', 30, 'day', 'BUILD'), ('c3_cafe', 8, 'interior', 'DESIGN'), ('c2_market', 10, 'interior', 'HIRE'),
    ('c13_hypercar', 20, 'day', 'DRIVE'), ('c7_fleet', 12, 'day', 'DELIVER'), ('c16_scramble', 20, 'night', 'COMPETE'),
    ('a1_nyc', 40, 'dusk', 'INVEST'), ('a3_tokyo', 20, 'dusk', 'EXPAND'),
]
STROBE = [('c6_rain', 20, 'rain'), ('c10_yokocho', 24, 'night'), ('c15_seoul_night', 20, 'night'), ('b1_title', 30, 'night'),
          ('c9_manila', 20, 'day'), ('a4_seoul', 30, 'dusk'), ('c14_snow', 50, 'snow'), ('c13_hypercar', 36, 'day')]
for i, (s, f0, look, word) in enumerate(MONTAGE):
    EDL.append((46.0 + i * .5, 46.5 + i * .5, 'shot', dict(shot=s, f0=f0, look=look, step=2, word=word)))
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
    (30.1, 33.9, 'headline', (['EVERY CITY HAS A SOUL'], "Manila's tangled wires. Tokyo's lantern alleys.")),
    (34.1, 35.9, 'side', (['BUY OUT', 'YOUR RIVALS'], 'Every competitor has a price.')),
    (36.1, 37.9, 'side', (['PLAY THE', 'MARKETS'], 'Stocks, bonds and hostile takeovers.')),
    (38.1, 39.9, 'headline', (['LIVE LARGE'], 'Hypercars, mansions and the good life')),
    (40.1, 41.9, 'headline', (['EVERY SEASON'], 'Summer nights to winter snow')),
    (42.0, 43.95, 'big', ('FIVE CITIES.', 'New York  ·  Manila  ·  Tokyo  ·  Seoul  ·  Hustle City')),
    (44.0, 45.95, 'big', ('ONE HUSTLE.', None)),
    (53.0, 60.0, 'logo', ()),
]

# white flashes on the hits: (time, strength, decay seconds)
FLASHES = [(3.0, .35, .25), (6.0, .35, .25), (8.0, .35, .25), (10.0, .3, .25), (12.0, .9, .4), (42.0, .3, .25),
           (44.0, .55, .35), (46.0, .6, .3), (53.0, .9, .3)]
FLASHES += [(46.0 + i * .5, .25, .15) for i in range(1, 8)]
FLASHES += [(50.0 + i * .25, .2, .1) for i in range(8)]


# ---------------------------------------------------------------- grading
LOOKS = {  # exposure, contrast, saturation, shadow tint, highlight tint, bloom amount, bloom threshold
    'dusk': (1.02, .26, 1.14, (-.018, .004, .03), (.035, .014, -.022), .38, .70),
    'day': (1.03, .22, 1.14, (-.012, .004, .022), (.026, .012, -.016), .22, .80),
    'night': (1.06, .30, 1.2, (-.022, .008, .045), (.035, .016, -.02), .62, .58),
    'interior': (1.03, .2, 1.12, (-.01, .003, .018), (.022, .01, -.012), .2, .82),
    'rain': (1.0, .28, 1.0, (-.024, .01, .045), (.012, .012, .004), .55, .58),
    'snow': (1.0, .2, 1.0, (-.012, .006, .03), (.01, .01, .012), .3, .84),
}
yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
VIG = (1 - .32 * (((xx - W / 2) / (W / 2)) ** 2 * .75 + ((yy - H / 2) / (H / 2)) ** 2 * .9) ** 1.15).clip(0, 1)
LUMA = np.array([.2126, .7152, .0722], np.float32)
grain_rng = np.random.default_rng(5)
GRAIN = [grain_rng.standard_normal((H // 2, W // 2)).astype(np.float32) for _ in range(6)]


def grade(img, look, vig=1.0):
    exp, con, sat, sh, hi, bloom, thr = LOOKS[look]
    a = np.asarray(img.convert('RGB'), np.float32) * (exp / 255)
    a = np.clip(a, 0, 1)
    a += con * (a * a * (3 - 2 * a) - a)
    l = a @ LUMA
    a = l[..., None] + (a - l[..., None]) * sat
    a += ((1 - l) ** 2)[..., None] * np.array(sh, np.float32) + (l ** 2)[..., None] * np.array(hi, np.float32)
    if bloom > 0:
        small = np.asarray(img.convert('RGB').resize((W // 4, H // 4), Image.BILINEAR), np.float32) / 255
        ls = small @ LUMA
        b = small * np.clip((ls - thr) / (1 - thr), 0, 1)[..., None]
        bi = Image.fromarray((b * 255).astype(np.uint8))
        b1 = np.asarray(bi.filter(ImageFilter.GaussianBlur(4)), np.float32)
        b2 = np.asarray(bi.filter(ImageFilter.GaussianBlur(14)), np.float32)
        bl = Image.fromarray(np.clip(b1 * .6 + b2 * 1.1, 0, 255).astype(np.uint8)).resize((W, H), Image.BILINEAR)
        bl = np.asarray(bl, np.float32) / 255 * bloom
        a = 1 - (1 - np.clip(a, 0, 1)) * (1 - bl)
    a *= VIG[..., None] ** vig
    return np.clip(a, 0, 1)


def to_img(a): return Image.fromarray((np.clip(a, 0, 1) * 255 + .5).astype(np.uint8))


_cache = {}


def src(shot, i):
    """A rendered frame, clamped to the shot's length."""
    d = os.path.join(FRAMES, shot)
    if shot not in _cache: _cache[shot] = sorted(n for n in os.listdir(d) if n.endswith('.png'))
    names = _cache[shot]
    return Image.open(os.path.join(d, names[max(0, min(len(names) - 1, i))]))


_ov = {}


def overlay(shot, i):
    """The 2D layer recorded with a shot: the HUD of a gameplay shot, or the rain and snow of a weather shot."""
    if shot not in _ov: _ov[shot] = sorted(n for n in os.listdir(os.path.join(CINEMA, shot)) if n.isdigit())
    names = _ov[shot]
    p = os.path.join(CINEMA, shot, names[max(0, min(len(names) - 1, i))], 'overlay.png')
    return Image.open(p).convert('RGBA') if os.path.exists(p) else None


# ---------------------------------------------------------------- typography
def text_w(s, f, track=0): return sum(f.getlength(c) for c in s) + track * max(0, len(s) - 1) if track else f.getlength(s)


def draw_text(d, x, y, s, f, fill, track=0):
    if not track: d.text((x, y), s, font=f, fill=fill); return
    for c in s:
        d.text((x, y), c, font=f, fill=fill)
        x += f.getlength(c) + track


def shadowed(layer, radius=12, alpha=.6, offset=(0, 4)):
    """Puts a soft dark shadow under everything drawn on the layer."""
    a = layer.split()[3]
    sh = Image.new('RGBA', layer.size, (0, 0, 0, 0))
    sh.putalpha(a.filter(ImageFilter.GaussianBlur(radius)).point(lambda v: int(v * alpha)))
    out = Image.new('RGBA', layer.size, (0, 0, 0, 0))
    out.alpha_composite(sh, offset)
    out.alpha_composite(layer)
    return out


def wrap(s, f, width):
    words, lines, cur = s.split(), [], ''
    for w in words:
        t = (cur + ' ' + w).strip()
        if f.getlength(t) > width and cur: lines.append(cur); cur = w
        else: cur = t
    if cur: lines.append(cur)
    return lines


def statement(layer, t, dur, text):
    a = fade(t, dur, .45, .35)
    f = font('InterDisplay-SemiBold', 62)
    track = 8 + 8 * (t / dur)
    d = ImageDraw.Draw(layer)
    w = text_w(text, f, track)
    draw_text(d, (W - w) / 2, H / 2 - 50, text, f, (255, 255, 255, int(255 * a)), track)


def city(layer, t, dur, name, sub):
    a = fade(t, dur, .45, .3)
    x0, y0 = 112, H - 104 - 168
    d = ImageDraw.Draw(layer)
    d.rectangle((x0, y0, x0 + 56 * ease_out(t / .5), y0 + 3), fill=ORANGE + (int(255 * a),))
    slide = 24 * (1 - ease_out(t / .6))
    draw_text(d, x0 + slide, y0 + 18, name, font('InterDisplay-Light', 56), (255, 255, 255, int(255 * a)), 14)
    a2 = fade(t - .18, dur - .18, .45, .3)
    draw_text(d, x0 + slide * 1.5, y0 + 92, sub.upper(), font('Inter-Medium', 21), (226, 229, 235, int(235 * a2)), 4)


def headline(layer, t, dur, lines, sub, x0, y_bottom, size, sub_size, sub_width):
    """Words rise into place one after another, an orange rule above, a line of detail below."""
    d = ImageDraw.Draw(layer)
    out = 1 - ease_in_out((t - (dur - .25)) / .25)
    lift = -14 * ease_in_out((t - (dur - .25)) / .25)
    f = font('InterDisplay-Black', size)
    fs = font('Inter-Medium', sub_size)
    sub_lines = wrap(sub, fs, sub_width) if sub else []
    lh = int(size * 1.0)
    block = lh * len(lines) + (14 + int(sub_size * 1.35) * len(sub_lines) if sub_lines else 0)
    y = y_bottom - block
    d.rectangle((x0, y - 26, x0 + 72 * ease_out(t / .35), y - 18), fill=ORANGE + (int(255 * out),))
    k = 0
    for line in lines:
        x = x0
        for word in line.split(' '):
            p = ease_out((t - k * .07) / .32)
            draw_text(d, x, y + 34 * (1 - p) + lift, word, f, (255, 255, 255, int(255 * p * out)), -1)
            x += text_w(word + ' ', f, -1)
            k += 1
        y += lh
    y += 14
    p = ease_out((t - .3) / .35)
    for sl in sub_lines:
        draw_text(d, x0, y + 10 * (1 - p) + lift, sl, fs, (232, 235, 240, int(240 * p * out)), 1)
        y += int(sub_size * 1.35)


def title(layer, t):
    """HUSTLE CITY slammed onto the screen on the drop."""
    s = 1 + .35 * (1 - ease_out(t / .16))
    a = ease_out(t / .08)
    f = font('InterDisplay-Black', 196)
    w1, w2 = text_w('HUSTLE ', f, -2), text_w('CITY', f, -2)
    tmp = Image.new('RGBA', (int(w1 + w2) + 40, 260), (0, 0, 0, 0))
    d = ImageDraw.Draw(tmp)
    draw_text(d, 20, 10, 'HUSTLE ', f, (255, 255, 255, 255), -2)
    draw_text(d, 20 + w1, 10, 'CITY', f, ORANGE + (255,), -2)
    tw, th = tmp.size
    tmp = tmp.resize((int(tw * s), int(th * s)), Image.BICUBIC)
    if a < 1: tmp.putalpha(tmp.split()[3].point(lambda v: int(v * a)))
    out = 1 - ease_in_out((t - 1.82) / .18)
    if out < 1: tmp.putalpha(tmp.split()[3].point(lambda v: int(v * out)))
    layer.alpha_composite(tmp, (int((W - tmp.size[0]) / 2), int(H / 2 - tmp.size[1] / 2 - 10)))
    d = ImageDraw.Draw(layer)
    fs = font('InterDisplay-SemiBold', 30)
    sub = 'BUILD  ·  RUN  ·  RULE THE CITY'
    a2 = ease_out((t - .45) / .4) * out
    draw_text(d, (W - text_w(sub, fs, 10)) / 2, H / 2 + 112, sub, fs, (235, 238, 244, int(235 * a2)), 10)


def big(layer, t, dur, text, sub):
    p = ease_out(t / .14)
    out = 1 - ease_in_out((t - (dur - .2)) / .2)
    f = font('InterDisplay-Black', 190)
    w = text_w(text, f, -2)
    tmp = Image.new('RGBA', (int(w) + 40, 240), (0, 0, 0, 0))
    draw_text(ImageDraw.Draw(tmp), 20, 0, text, f, (255, 255, 255, 255), -2)
    s = 1.18 - .18 * p + .03 * (t / dur)
    tmp = tmp.resize((int(tmp.size[0] * s), int(tmp.size[1] * s)), Image.BICUBIC)
    tmp.putalpha(tmp.split()[3].point(lambda v: int(v * p * out)))
    layer.alpha_composite(tmp, (int((W - tmp.size[0]) / 2), int(H / 2 - tmp.size[1] / 2 - 20)))
    if sub:
        fs = font('Inter-Medium', 26)
        a = ease_out((t - .35) / .4) * out
        draw_text(ImageDraw.Draw(layer), (W - text_w(sub, fs, 3)) / 2, H / 2 + 120, sub.upper(), fs, (232, 235, 240, int(230 * a)), 3)


def word(layer, t, text):
    p = ease_out(t / .1)
    f = font('InterDisplay-Black', 176)
    w = text_w(text, f, -2)
    tmp = Image.new('RGBA', (int(w) + 40, 220), (0, 0, 0, 0))
    draw_text(ImageDraw.Draw(tmp), 20, 0, text, f, (255, 255, 255, 255), -2)
    s = 1.12 - .12 * p + .05 * t
    tmp = tmp.resize((int(tmp.size[0] * s), int(tmp.size[1] * s)), Image.BICUBIC)
    layer.alpha_composite(tmp, (int((W - tmp.size[0]) / 2), int(H / 2 - tmp.size[1] / 2 - 18)))


def icon(size):
    """The launcher icon: skyline, gold coin and green street on the blue tile."""
    s = 4
    n = size * s
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    g = np.linspace(0, 1, n)[:, None, None]
    arr = (np.array(BLUE_A) * (1 - g) + np.array(BLUE_B) * g) * np.ones((1, n, 1))
    grad = Image.fromarray(np.concatenate([arr, np.full((n, n, 1), 255)], 2).astype(np.uint8), 'RGBA')
    mask = Image.new('L', (n, n), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, n - 1, n - 1), radius=int(n * .23), fill=255)
    im.paste(grad, (0, 0), mask)
    d = ImageDraw.Draw(im)
    k = n / 108  # the vector drawable's viewport is 108 units
    def r(x0, y0, x1, y1, c): d.rectangle((x0 * k, y0 * k, x1 * k, y1 * k), fill=c)
    r(30, 46, 40, 76, (232, 238, 246)); r(42, 34, 54, 76, (255, 255, 255)); r(56, 40, 66, 76, (232, 238, 246)); r(68, 52, 78, 76, (207, 218, 232))
    d.ellipse((61 * k, 27 * k, 83 * k, 49 * k), fill=ORANGE)
    fd = ImageFont.truetype(INTER + 'Inter-Black.otf', int(17 * k))
    d.text((72 * k, 38.5 * k), '$', font=fd, fill=WHITE, anchor='mm')
    r(26, 78, 82, 82, GREEN)
    return im.resize((size, size), Image.LANCZOS)


ICON = icon(196)


def logo(layer, t):
    d = ImageDraw.Draw(layer)
    a = ease_out((t - .55) / .7)
    s = 1.06 - .06 * ease_out((t - .55) / 3.5)
    out = 1 - ease_in_out((t - 6.0) / .8)
    if a <= 0: return
    f = font('InterDisplay-Black', 150)
    w1, w2 = text_w('HUSTLE ', f, -2), text_w('CITY', f, -2)
    tmp = Image.new('RGBA', (int(w1 + w2) + 60, 460), (0, 0, 0, 0))
    tmp.alpha_composite(ICON, (int((tmp.size[0] - ICON.size[0]) / 2), 0))
    dt = ImageDraw.Draw(tmp)
    draw_text(dt, 30, 228, 'HUSTLE ', f, (255, 255, 255, 255), -2)
    draw_text(dt, 30 + w1, 228, 'CITY', f, ORANGE + (255,), -2)
    tmp = tmp.resize((int(tmp.size[0] * s), int(tmp.size[1] * s)), Image.BICUBIC)
    glow = tmp.filter(ImageFilter.GaussianBlur(26))
    glow.putalpha(glow.split()[3].point(lambda v: int(v * .55 * a * out)))
    tmp.putalpha(tmp.split()[3].point(lambda v: int(v * a * out)))
    x, y = int((W - tmp.size[0]) / 2), int(H / 2 - 262 - (tmp.size[1] - 460) / 2)
    layer.alpha_composite(glow, (x, y))
    layer.alpha_composite(tmp, (x, y))
    fs = font('InterDisplay-Bold', 40)
    tag = 'BUILD YOUR EMPIRE'
    a2 = ease_out((t - 2.4) / .6) * out
    draw_text(d, (W - text_w(tag, fs, 18)) / 2, H / 2 + 222, tag, fs, (255, 255, 255, int(240 * a2)), 18)
    fm = font('Inter-Medium', 22)
    foot = 'ANDROID  ·  VERSION 1.7 BETA'
    a3 = ease_out((t - 3.6) / .6) * out
    draw_text(d, (W - text_w(foot, fm, 6)) / 2, H - 70, foot, fm, (205, 209, 216, int(230 * a3)), 6)


# ---------------------------------------------------------------- the phone
SCREEN_W, SCREEN_H = 1120, 630
BEZEL = 20


def device_image(screen):
    """The screen inside a landscape phone with a thin bezel, rounded corners and a glass sheen."""
    dw, dh = SCREEN_W + BEZEL * 2, SCREEN_H + BEZEL * 2
    im = Image.new('RGBA', (dw, dh), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, dw - 1, dh - 1), radius=58, fill=(16, 17, 20, 255), outline=(86, 90, 98, 255), width=3)
    d.rounded_rectangle((4, 4, dw - 5, dh - 5), radius=55, outline=(40, 42, 47, 255), width=2)
    scr = screen.convert('RGB').resize((SCREEN_W, SCREEN_H), Image.LANCZOS)
    m = Image.new('L', (SCREEN_W, SCREEN_H), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, SCREEN_W - 1, SCREEN_H - 1), radius=38, fill=255)
    im.paste(scr, (BEZEL, BEZEL), m)
    # a faint diagonal sheen across the glass
    sheen = Image.new('L', (SCREEN_W, SCREEN_H), 0)
    sd = ImageDraw.Draw(sheen)
    sd.polygon([(SCREEN_W * .55, 0), (SCREEN_W * .8, 0), (SCREEN_W * .45, SCREEN_H), (SCREEN_W * .2, SCREEN_H)], fill=16)
    sheen = Image.fromarray((np.asarray(sheen, np.float32) * (np.asarray(m, np.float32) / 255)).astype(np.uint8))
    im.alpha_composite(Image.merge('RGBA', [Image.new('L', sheen.size, 255)] * 3 + [sheen]), (BEZEL, BEZEL))
    d.ellipse((7, dh / 2 - 5, 17, dh / 2 + 5), fill=(30, 32, 38, 255))  # the front camera, on the left in landscape
    return im


def homography(src_pts, dst_pts):
    """Coefficients for PIL's PERSPECTIVE transform: maps output points (dst) back to input points (src)."""
    A, b = [], []
    for (x, y), (u, v) in zip(dst_pts, src_pts):
        A.append([x, y, 1, 0, 0, 0, -u * x, -u * y]); b.append(u)
        A.append([0, 0, 0, x, y, 1, -v * x, -v * y]); b.append(v)
    return np.linalg.solve(np.array(A, float), np.array(b, float)).tolist()


def place_device(frame, dev, t, dur):
    """The phone floats in on the right, turning gently toward the viewer."""
    p = ease_out(t / .6)
    yaw = math.radians(-12 + 7 * ease_in_out(t / dur))
    pitch = math.radians(4 - 2.5 * ease_in_out(t / dur))
    scale = .94 + .06 * p
    cx, cy = W * .655, H * .5 + 40 * (1 - p)
    dw, dh = dev.size
    F = 2600.0
    pts = []
    for x, y in ((-dw / 2, -dh / 2), (dw / 2, -dh / 2), (dw / 2, dh / 2), (-dw / 2, dh / 2)):
        x, y = x * scale, y * scale
        # turn about the vertical axis, then tip about the horizontal one
        X, Z = x * math.cos(yaw), x * math.sin(yaw)
        Y, Z = y * math.cos(pitch) - Z * math.sin(pitch), y * math.sin(pitch) + Z * math.cos(pitch)
        k = F / (F + Z)
        pts.append((cx + X * k, cy + Y * k))
    co = homography([(0, 0), (dw, 0), (dw, dh), (0, dh)], pts)
    warped = dev.transform((W, H), Image.PERSPECTIVE, co, Image.BICUBIC)
    a = warped.split()[3]
    sh = a.resize((W // 4, H // 4), Image.BILINEAR).filter(ImageFilter.GaussianBlur(9)).resize((W, H), Image.BILINEAR)
    shadow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    shadow.putalpha(sh.point(lambda v: int(v * .7 * p)))
    frame.alpha_composite(shadow, (0, 36))
    if p < 1: warped.putalpha(a.point(lambda v: int(v * p)))
    frame.alpha_composite(warped)


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
    if t >= 59.0: a *= 1 - ease_in_out((t - 59.0) / .9)
    return to_img(a)


def make_wash():
    m = np.clip(1 - np.sqrt(((xx - 0) / (W * .62)) ** 2 + ((yy - H) / (H * .62)) ** 2), 0, 1) ** 1.6 * .62
    return Image.fromarray(np.dstack([np.zeros((H, W, 3)), m[..., None] * 255]).astype(np.uint8), 'RGBA')


WASH = make_wash()
_center = np.clip(1 - np.sqrt(((xx - W / 2) / (W * .62)) ** 2 + ((yy - H / 2 - 40) / (H * .72)) ** 2), 0, 1) ** 1.1


def center_wash(k):
    """Darkens the middle of the frame behind centred titles."""
    return Image.fromarray(np.dstack([np.zeros((H, W, 3)), _center[..., None] * 255 * k]).astype(np.uint8), 'RGBA')


# ---------------------------------------------------------------- main
if __name__ == '__main__':
    if '--stills' in sys.argv:
        k = sys.argv.index('--stills')
        out = sys.argv[k + 1]
        os.makedirs(out, exist_ok=True)
        for s in sys.argv[k + 2:]:
            n = int(round(float(s) * FPS)) if '.' in s else int(s)
            frame_at(n).save(os.path.join(out, f'{n:04d}.jpg'), quality=90)
        sys.exit(0)
    cmd = ['ffmpeg', '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}', '-r', str(FPS), '-i', '-',
           '-i', SCORE, '-map', '0:v', '-map', '1:a', '-c:v', 'libx264', '-preset', 'slow', '-crf', '16', '-tune', 'film',
           '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '256k', '-movflags', '+faststart', '-shortest', OUT]
    p = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    for n in range(NF):
        p.stdin.write(frame_at(n).tobytes())
        if n % 48 == 0: print(f'{n / FPS:5.1f}s', flush=True)
    p.stdin.close()
    p.wait()
    print(OUT)
