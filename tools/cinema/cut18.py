"""What the 1.8 trailer's cuts share: before/after comparisons, flips between the two, the phone, and the frame.

A cut is a Cut: its edit decision list, captions, flashes and key moments. edit18.py times it to its own score,
edit18_song.py to the beats of a song.

EDL entries are (start, end, kind, params):
  'shot'     a rendered shot: shot, f0 (first source frame), look, and optionally speed (source frames per frame),
             tag (a word over a darkened plate), plate ('title' or 'logo': darkened behind the title or the logo)
  'compare'  before and after side by side: shot (<shot>_before and <shot>_after), f0, look, and marks, the times
             the divider starts in, reaches the middle, leaves it and reaches the far side
  'flip'     before or after alone: shot, f0, side (0 before, 1 after), look
  'device'   a phone screen from the UI stills: still, bg (shot, frame, look) blurred behind it
  'black'
Captions are (start, end, kind, args) with kind statement, title, headline, side, big, logo or credit.
"""
import math, os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from kit import *

GREY = (60, 64, 72)


class Cut:
    def __init__(self, dur, edl, captions, flashes, slam, logo, ui, bars_until=None, title_sub='THE 1.8 UPDATE',
                 foot='ANDROID  ·  VERSION 1.8 BETA', credit=None, bleach=None):
        self.dur, self.edl, self.captions, self.flashes = dur, edl, captions, flashes
        # the title slams on at [slam] (the letterbox opening up), the logo at [logo] (the letterbox closing again)
        self.slam, self.logo, self.ui = slam, logo, ui
        self.bars_until = slam if bars_until is None else bars_until
        self.title_sub, self.foot, self.credit = title_sub, foot, credit
        # (from, to): the opening bleaches out into a breath before the slam
        self.bleach = bleach

    @property
    def frames(self): return int(self.dur * FPS)


# ---------------------------------------------------------------- before and after
def split_at(t, marks):
    """Where the divider stands at [t], as a share of the width (1 = all before), given its [marks]."""
    a, b, c, d = marks
    if t < a: return 1.0
    if t < b: return 1 - .5 * ease_in_out((t - a) / (b - a))
    if t < c: return .5 - .03 * math.sin((t - b) / (c - b) * math.pi)
    if t < d: return .5 * (1 - ease_in_out((t - c) / (d - c)))
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


def compare_frame(t, t0, t1, prm):
    lt = t - t0
    i = prm['f0'] + int(round(lt * FPS))
    b = grade(src(prm['shot'] + '_before', i), prm['look'])
    a = grade(src(prm['shot'] + '_after', i), prm['look'])
    x = int(round(W * split_at(t, prm['marks'])))
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
    a_after = clamp((W - x - 260) / 120) * (1 - ease_in_out((t - (t1 - .4)) / .3))
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
def device_frame(cut, t, t0, t1, prm):
    i = int(round((t - t0) * FPS))
    bshot, bf, blook = prm['bg']
    bg = grade(src(bshot, bf + i), blook, vig=1.6)
    bgi = to_img(bg).resize((W // 6, H // 6), Image.BILINEAR).filter(ImageFilter.GaussianBlur(5)).resize((W, H), Image.BICUBIC)
    a = np.asarray(bgi, np.float32) / 255 * .42
    a = a * np.array([.92, 1.0, 1.08], np.float32)
    frame = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)).convert('RGBA')
    screen = Image.open(os.path.join(cut.ui, prm['still'] + '.png')).convert('RGBA')
    bgc = Image.new('RGBA', screen.size, (24, 26, 30, 255)); bgc.alpha_composite(screen)
    place_device(frame, device_image(bgc), t - t0, t1 - t0)
    return frame


def tag(layer, t, text):
    """A montage's word for each kind of shop: smaller than the old trailer's, so the shelves still show."""
    p = ease_out(t / .1)
    f = font('InterDisplay-Black', 132)
    w = text_w(text, f, -2)
    tmp = Image.new('RGBA', (int(w) + 40, 170), (0, 0, 0, 0))
    draw_text(ImageDraw.Draw(tmp), 20, 0, text, f, (255, 255, 255, 255), -2)
    s = 1.1 - .1 * p + .05 * t
    tmp = tmp.resize((int(tmp.size[0] * s), int(tmp.size[1] * s)), Image.BICUBIC)
    layer.alpha_composite(tmp, (int((W - tmp.size[0]) / 2), int(H / 2 - tmp.size[1] / 2 - 18)))


def credit(layer, t, dur, label, text):
    """The music credit: a small label over the song and who it's by, low on the left."""
    a = fade(t, dur, .5, .5)
    if a <= 0: return
    d = ImageDraw.Draw(layer)
    x0, y0 = 112, H - 104 - 112
    d.rectangle((x0, y0, x0 + 40 * ease_out(t / .5), y0 + 3), fill=ORANGE + (int(255 * a),))
    draw_text(d, x0, y0 + 16, label, font('Inter-Medium', 18), (226, 229, 235, int(220 * a)), 5)
    draw_text(d, x0, y0 + 44, text, font('InterDisplay-SemiBold', 34), (255, 255, 255, int(255 * a)), 1)


# ---------------------------------------------------------------- one frame
def letterbox(cut, t):
    bar = 104
    if t < cut.bars_until: return bar
    if t < cut.bars_until + .3: return bar * (1 - ease_out((t - cut.bars_until) / .3))
    if t < cut.logo: return 0
    return bar * ease_out((t - cut.logo) / .7)


def frame_at(cut, n):
    t = n / FPS
    c = next((c for c in cut.edl if c[0] <= t < c[1]), cut.edl[-1])
    t0, t1, kind, prm = c
    if kind == 'black':
        img = Image.new('RGBA', (W, H), (0, 0, 0, 255))
    elif kind == 'device':
        img = device_frame(cut, t, t0, t1, prm)
    elif kind == 'compare':
        img = compare_frame(t, t0, t1, prm)
    elif kind == 'flip':
        img = flip_frame(t, t0, prm)
    else:
        i = prm['f0'] + int(round((t - t0) * FPS * prm.get('speed', 1.0)))
        a = grade(src(prm['shot'], i), prm['look'])
        if prm['shot'] == 'c14_snow':  # the snowflakes ride on top of the graded city
            ov = overlay(prm['shot'], i)
            if ov:
                o = np.asarray(ov, np.float32) / 255
                k = o[..., 3:] * .85
                a = a * (1 - k) + o[..., :3] * k
        if prm.get('tag'): a *= .74
        if prm.get('plate') == 'title': a *= .5 + .25 * ease_in_out((t - (t1 - .8)) / .8)
        if prm.get('plate') == 'logo': a *= 1 - .32 * ease_out((t - (cut.logo + .3)) / 1.2)
        if cut.bleach and t0 <= cut.bleach[0] < t1 and t > cut.bleach[0]:
            k = ease_in_out((t - cut.bleach[0]) / (cut.bleach[1] - cut.bleach[0]))
            a = a + (1 - a) * k * .85
        img = to_img(a).convert('RGBA')
        if cut.slam <= t < cut.slam + .45:  # the slam shakes the camera
            k = (1 - (t - cut.slam) / .45) ** 2
            dx, dy = 14 * k * math.sin(n * 2.3), 10 * k * math.cos(n * 3.1)
            img = img.transform((W, H), Image.AFFINE, (1 / 1.02, 0, dx + W * .01, 0, 1 / 1.02, dy + H * .01), Image.BILINEAR)
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    for c0, c1, ck, args in cut.captions:
        if not (c0 <= t < c1): continue
        lt, dur = t - c0, c1 - c0
        if ck == 'statement': statement(layer, lt, dur, *args)
        elif ck == 'title': title(layer, lt, sub=cut.title_sub, hold=dur - .18)
        elif ck == 'headline': headline(layer, lt, dur, args[0], args[1], 112, H - 96, 104, 30, 1060)
        elif ck == 'side': headline(layer, lt, dur, args[0], args[1], 112, H / 2 + 150, 84, 28, 520)
        elif ck == 'big': big(layer, lt, dur, *args)
        elif ck == 'logo': logo(layer, lt, tag='THE 1.8 UPDATE')
        elif ck == 'credit': credit(layer, lt, dur, *args)
    if kind == 'shot' and prm.get('tag'): tag(layer, t - t0, prm['tag'])
    if kind != 'device' and any(c[2] in ('headline', 'credit') and c[0] <= t < c[1] for c in cut.captions):
        img.alpha_composite(WASH)
    centre = 0.0
    for c0, c1, ck, args in cut.captions:
        if c0 <= t < c1 and ck in ('statement', 'title', 'big'): centre = max(centre, .55 * fade(t - c0, c1 - c0, .3, .3))
    if t >= cut.logo: centre = max(centre, .8 * ease_out((t - cut.logo - .4) / 1.0))
    if centre > 0: img.alpha_composite(center_wash(centre))
    img.alpha_composite(shadowed(layer))
    a = np.asarray(img.convert('RGB'), np.float32) / 255
    fl = sum(s * math.exp(-(t - ft) / d) for ft, s, d in cut.flashes if t >= ft)
    if fl > 0: a = a + (1 - a) * min(1, fl)
    g = GRAIN[n % len(GRAIN)]
    g = np.repeat(np.repeat(g, 2, 0), 2, 1)
    a += g[..., None] * .016
    bar = int(round(letterbox(cut, t)))
    if bar: a[:bar] = 0; a[H - bar:] = 0
    if t >= cut.logo:
        im = to_img(a).convert('RGBA')
        fl = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        footer(fl, t - cut.logo, foot=cut.foot)
        if cut.credit: music_line(fl, t - cut.logo, cut.credit)
        im.alpha_composite(fl)
        a = np.asarray(im.convert('RGB'), np.float32) / 255
    if t < .6: a *= ease_in_out(t / .6)
    if t >= cut.dur - 1.0: a *= 1 - ease_in_out((t - (cut.dur - 1.0)) / .9)
    return to_img(a)


def music_line(img, t, text):
    """The song's credit on the end card, in the top letterbox bar."""
    a = ease_out((t - 3.0) / .6) * (1 - ease_in_out((t - 6.0) / .8))
    if a <= 0: return
    fm = font('Inter-Medium', 22)
    draw_text(ImageDraw.Draw(img), (W - text_w(text, fm, 6)) / 2, 44, text, fm, (205, 209, 216, int(230 * a)), 6)


def needed(cut):
    """Which source frames of which shots the cut uses: {shot: set of frame numbers}."""
    need = {}
    def add(shot, i): need.setdefault(shot, set()).add(i)
    for n in range(cut.frames):
        t = n / FPS
        c = next((c for c in cut.edl if c[0] <= t < c[1]), cut.edl[-1])
        t0, t1, kind, prm = c
        i = int(round((t - t0) * FPS))
        if kind == 'compare':
            add(prm['shot'] + '_before', prm['f0'] + i); add(prm['shot'] + '_after', prm['f0'] + i)
        elif kind == 'flip':
            add(prm['shot'] + ('_after' if prm['side'] else '_before'), prm['f0'] + i)
        elif kind == 'device':
            add(prm['bg'][0], prm['bg'][1] + i)
        elif kind == 'shot':
            add(prm['shot'], prm['f0'] + int(round((t - t0) * FPS * prm.get('speed', 1.0))))
    return need
