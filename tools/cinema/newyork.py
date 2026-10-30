"""Cuts the New York story trailer: a cold open, then one song and the city at full tilt, then a name.

usage: python3 newyork.py <frames dir> <cinema dir> <score.wav> <out.mp4>
       python3 newyork.py ... --stills <dir> <frame> [frame ...]   writes single frames as JPG to check the look
       python3 newyork.py ... --plan <file>   lists the frames the cut uses, for CinemaRender's -PcinemaPlan

Unlike the showcase cuts it has almost no captions: a subtitled conversation in the cold open, the city cut to the
beat of newyork_score.py (100 BPM, a bar every 2.4 s from 10.8 s), three phone livestreams and a news break inside
the montage, Alex at the waterfront, and the name on a sunset. The shots are CinematicTest's n01 .. n20, rendered
with render.mjs or the desktop's CinemaRender into <frames dir>.
"""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from kit import *

DUR = 60.0
NF = int(DUR * FPS)
FRAMES, CINEMA, SCORE, OUT = sys.argv[1:5]
use(FRAMES, CINEMA)
WEATHER = ('n14_rain_cross', 'n17_snow')
ANTON = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'fonts', 'Anton-Regular.ttf')
_anton = {}


def anton(size):
    if size not in _anton: _anton[size] = ImageFont.truetype(ANTON, size)
    return _anton[size]


LOOKS.update({  # exposure, contrast, saturation, shadow tint, highlight tint, bloom amount, bloom threshold
    'dawn': (1.0, .26, 1.0, (-.02, .0, .04), (.05, .02, -.01), .3, .72),
    'morning': (1.02, .24, 1.06, (-.012, .006, .03), (.035, .02, -.01), .22, .78),
    'golden': (1.05, .26, 1.22, (-.02, -.004, .045), (.07, .02, -.035), .42, .66),
    'sunset': (1.04, .28, 1.25, (-.02, -.01, .06), (.08, .018, -.03), .5, .62),
    'neon': (1.08, .32, 1.28, (-.03, .012, .06), (.05, -.005, .04), .75, .55),
    'club': (.92, .34, 1.35, (-.02, -.01, .07), (.06, -.01, .06), .7, .56),
    'phone': (1.02, .18, 1.1, (-.008, .004, .02), (.02, .01, -.01), .18, .8),
})

# ---------------------------------------------------------------- the cut
S0, BAR = 10.8, 2.4
EDL = [  # (start, end, kind, params); shots are 24 fps from frame f0, at [speed] source frames per output frame
    (0.0, 3.2, 'black', {}),
    (3.2, 5.8, 'shot', dict(shot='n01_fidi_dawn', f0=6, look='dawn')),
    (5.8, 8.2, 'shot', dict(shot='n02_hk_morning', f0=4, look='morning')),
    (8.2, 10.6, 'shot', dict(shot='n03_rush', f0=4, look='morning')),
    (10.6, 10.8, 'black', {}),
    (10.8, 13.2, 'shot', dict(shot='n04_bridge_gold', f0=2, look='golden', speed=.7)),
    (13.2, 15.6, 'shot', dict(shot='n05_lake', f0=2, look='golden', speed=.7)),
    (15.6, 18.0, 'shot', dict(shot='n06_times_dusk', f0=2, look='neon')),
    (18.0, 20.4, 'phone', dict(shot='n13_cafe', f0=2, look='phone', handle='tenth.and.bean', viewers='3.1K',
                               caption='the line for this new coffee spot on 10th is UNREAL', likes=2400)),
    (20.4, 21.6, 'shot', dict(shot='n07_brownstones', f0=8, look='golden')),
    (21.6, 22.8, 'shot', dict(shot='n08_brooklyn', f0=8, look='sunset')),
    (22.8, 25.2, 'shot', dict(shot='n09_van_bridge', f0=2, look='day')),
    (25.2, 27.6, 'news', dict(shot='n10_news_aerial', f0=2, look='day')),
    (27.6, 28.8, 'shot', dict(shot='n11_gym', f0=10, look='interior')),
    (28.8, 30.0, 'shot', dict(shot='n12_club', f0=10, look='club')),
    (30.0, 32.4, 'shot', dict(shot='n15_hypercar', f0=2, look='neon')),
    (32.4, 33.6, 'shot', dict(shot='n14_rain_cross', f0=10, look='rain')),
    (33.6, 34.8, 'shot', dict(shot='n16_river_night', f0=10, look='neon')),
    (34.8, 37.2, 'phone', dict(shot='n12_club', f0=2, look='club', handle='nightowl.nyc', viewers='12.4K',
                               caption='this club opened TWO WEEKS ago??', likes=18700)),
    (37.2, 38.4, 'shot', dict(shot='n17_snow', f0=10, look='snow')),
    (38.4, 39.6, 'shot', dict(shot='n18_fidi_night', f0=10, look='neon')),
    (39.6, 42.0, 'shot', dict(shot='n19_skyline_night', f0=4, look='neon', speed=.8)),
    (42.0, 44.4, 'phone', dict(shot='n15_hypercar', f0=2, look='neon', speed=.9, handle='carspotter.nyc', viewers='48K',
                               caption='who IS this guy. 3 stores in a month and now THIS', likes=96100)),
]
QUICK = [('n06_times_dusk', 40, 'neon'), ('n12_club', 40, 'club'), ('n14_rain_cross', 40, 'rain'), ('n09_van_bridge', 40, 'day'),
         ('n11_gym', 40, 'interior'), ('n03_rush', 40, 'morning'), ('n16_river_night', 40, 'neon'), ('n04_bridge_gold', 40, 'golden')]
for i, (s, f0, look) in enumerate(QUICK):
    EDL.append((44.4 + i * .6, 45.0 + i * .6, 'shot', dict(shot=s, f0=f0, look=look)))
EDL += [
    # the finale's whole camera move (8 s recorded) in 6 s
    (49.2, 55.2, 'shot', dict(shot='n20_finale', f0=0, look='sunset', speed=1.3)),
    (55.2, 60.0, 'logo', {}),
]

SUBS = [  # (start, end, speaker, line)
    (1.0, 2.6, 'LOAN OFFICER', 'So, Mr. Rivera.'),
    (2.7, 4.6, 'LOAN OFFICER', 'Five thousand dollars. A beat-up car.'),
    (4.7, 6.1, 'LOAN OFFICER', "And a walk-up in Hell's Kitchen."),
    (6.4, 7.9, 'ALEX', 'And a plan.'),
    (8.3, 9.95, 'LOAN OFFICER', 'Everybody in this city has a plan, kid.'),
    (10.0, 10.58, 'ALEX', 'Not like mine.'),
    (51.0, 54.4, 'ALEX', "Give it a year. This whole city's gonna know my name."),
]

FLASHES = [(10.8, .35, .3), (30.0, .8, .35), (55.2, 1.0, .4)] + [(44.4 + i * .6, .25, .12) for i in range(8)]


# ---------------------------------------------------------------- pieces
def shot_frame(prm, lt):
    i = int(round(lt * FPS * prm.get('speed', 1.0)))
    a = grade(src(prm['shot'], prm['f0'] + i), prm['look'])
    if prm['shot'] in WEATHER:  # rain and snow ride on top of the graded city
        ov = overlay(prm['shot'], prm['f0'] + i)
        if ov:
            o = np.asarray(ov, np.float32) / 255
            k = o[..., 3:] * .85
            a = a * (1 - k) + o[..., :3] * k
    return a


def subtitle(layer, lt, dur, speaker, line):
    """A trailer subtitle: the speaker in gold small capitals over the line, low in the frame."""
    d = ImageDraw.Draw(layer)
    al = fade(lt, dur, .12, .12)
    fs = font('Inter-SemiBold', 40)
    fn = font('Inter-Bold', 22)
    y = H - 104 - 88
    draw_text(d, (W - text_w(speaker, fn, 6)) / 2, y, speaker, fn, (242, 201, 76, int(255 * al)), 6)
    draw_text(d, (W - text_w(line, fs)) / 2, y + 34, line, fs, (255, 255, 255, int(255 * al)))


def rounded(im, r):
    m = Image.new('L', im.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, im.size[0] - 1, im.size[1] - 1), r, fill=255)
    out = im.convert('RGBA'); out.putalpha(m)
    return out


HEART = None


def heart(size, color):
    im = Image.new('RGBA', (size * 4, size * 4), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    s = size * 4
    d.ellipse((0, 0, s * .55, s * .55), fill=color); d.ellipse((s * .45, 0, s, s * .55), fill=color)
    d.polygon([(s * .03, s * .36), (s * .97, s * .36), (s * .5, s * .95)], fill=color)
    return im.resize((size, size), Image.LANCZOS)


def phone_frame(prm, lt, dur):
    """A livestream someone is filming on their phone, on 'Buzz': the vertical picture, its overlay and the hearts."""
    a = shot_frame(prm, lt)
    full = to_img(a)
    # behind: the same picture, blown up, blurred and dimmed
    bg = full.resize((W // 8, H // 8), Image.BILINEAR).filter(ImageFilter.GaussianBlur(3)).resize((W, H), Image.BILINEAR)
    bg = Image.fromarray((np.asarray(bg, np.float32) * .42).astype(np.uint8)).convert('RGBA')
    # the phone's picture: a 9:16 crop of the middle, handheld
    ph, pw = 940, 528
    cw = int(H * 9 / 16)
    sway = 10 * math.sin(lt * 2.1) + 6 * math.sin(lt * 3.7 + 1)
    crop = full.crop((int((W - cw) / 2 + sway * 3), 0, int((W + cw) / 2 + sway * 3), H)).resize((pw, ph), Image.LANCZOS)
    card = rounded(crop, 34)
    ui = Image.new('RGBA', (pw, ph), (0, 0, 0, 0))
    d = ImageDraw.Draw(ui)
    # top: the account, LIVE and the viewer count
    d.rectangle((0, 0, pw, 130), fill=(0, 0, 0, 0))
    grad = Image.new('L', (1, 140), 0)
    for y in range(140): grad.putpixel((0, y), int(150 * (1 - y / 140)))
    ui.paste(Image.new('RGBA', (pw, 140), (0, 0, 0, 255)), (0, 0), grad.resize((pw, 140)))
    grad2 = grad.transpose(Image.FLIP_TOP_BOTTOM).resize((pw, 260))
    ui.paste(Image.new('RGBA', (pw, 260), (0, 0, 0, 255)), (0, ph - 260), grad2)
    d.ellipse((24, 26, 74, 76), fill=(242, 153, 74, 255))
    d.text((49, 51), prm['handle'][0].upper(), font=font('Inter-Bold', 26), fill=(255, 255, 255, 255), anchor='mm')
    d.text((86, 34), '@' + prm['handle'], font=font('Inter-Bold', 22), fill=(255, 255, 255, 255))
    d.text((86, 60), 'Buzz  ·  just now', font=font('Inter-Medium', 17), fill=(255, 255, 255, 190))
    lx = pw - 190
    d.rounded_rectangle((lx, 34, lx + 74, 66), 7, fill=(235, 52, 64, 255))
    d.text((lx + 37, 50), 'LIVE', font=font('Inter-ExtraBold', 18), fill=(255, 255, 255, 255), anchor='mm')
    d.rounded_rectangle((lx + 80, 34, lx + 168, 66), 7, fill=(0, 0, 0, 120))
    d.text((lx + 124, 50), prm['viewers'], font=font('Inter-Bold', 18), fill=(255, 255, 255, 255), anchor='mm')
    # bottom: the caption, typed out, and the likes counting up
    cap = prm['caption']
    shown = cap[:int(len(cap) * clamp((lt - .2) / .9))]
    f = font('Inter-Bold', 27)
    lines = wrap(shown, f, pw - 120) if shown else []
    for k, ln in enumerate(lines[-3:]):
        d.text((26, ph - 150 + k * 36), ln, font=f, fill=(255, 255, 255, 255))
    likes = int(prm['likes'] * (.7 + .3 * clamp(lt / dur)))
    hx = pw - 64
    ui.alpha_composite(heart(44, (255, 255, 255, 255)), (hx - 22, ph - 260))
    d.text((hx, ph - 206), f'{likes / 1000:.1f}K' if likes >= 1000 else str(likes), font=font('Inter-Bold', 18), fill=(255, 255, 255, 255), anchor='mm')
    # hearts float up the right side
    rng = np.random.default_rng(int(prm['likes']))
    for k in range(14):
        t0 = rng.uniform(0, dur); life = 1.6
        u = (lt - t0) / life
        if not 0 <= u <= 1: continue
        col = [(255, 64, 96), (255, 140, 60), (255, 210, 64), (180, 90, 255)][k % 4]
        hs = int(30 + 12 * rng.uniform())
        x = int(hx - 20 + 26 * math.sin(u * 6 + k) - hs / 2)
        y = int(ph - 300 - u * 420)
        hh = heart(hs, col + (int(255 * (1 - u) ** .7),))
        ui.alpha_composite(hh, (x, y))
    card.alpha_composite(ui)
    # the phone slides up into view, and away at the end
    k_in = ease_out(lt / .35); k_out = ease_in_out((lt - (dur - .3)) / .3)
    py = int((H - ph) / 2 + (1 - k_in) * 200 + k_out * 120)
    img = bg
    shadow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(((W - pw) // 2 - 6, py + 10, (W + pw) // 2 + 6, py + ph + 18), 40, fill=(0, 0, 0, 170))
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(18)))
    img.alpha_composite(card, ((W - pw) // 2, py))
    return np.asarray(img.convert('RGB'), np.float32) / 255


TICKER = ('MARKETS: RIVAL CHAINS SLASH PRICES ACROSS MIDTOWN   •   WEATHER: STORMS TONIGHT, SNOW BY THE WEEKEND   •   '
          'TRAFFIC: DELAYS ON THE MANHATTAN BRIDGE   •   REAL ESTATE: RENTS UP 4% IN CHELSEA   •   ')


def news(layer, lt, dur):
    """Hustle City News breaking in: the channel bug, a headline bar and the ticker."""
    d = ImageDraw.Draw(layer)
    k = ease_out(lt / .3)
    # channel bug
    d.rounded_rectangle((64, 60, 214, 116), 8, fill=(200, 30, 40, 235))
    d.text((139, 88), 'HCN', font=anton(40), fill=(255, 255, 255, 255), anchor='mm')
    d.rounded_rectangle((224, 70, 300, 106), 6, fill=(255, 255, 255, 230))
    d.text((262, 88), 'LIVE', font=font('Inter-ExtraBold', 20), fill=(200, 30, 40, 255), anchor='mm')
    # the headline bar slides in from the left
    y = H - 250
    x0 = int(-1500 * (1 - k))
    d.rectangle((x0 + 64, y, x0 + 64 + 260, y + 50), fill=(200, 30, 40, 245))
    d.text((x0 + 64 + 130, y + 25), 'BREAKING NEWS', font=font('Inter-ExtraBold', 24), fill=(255, 255, 255, 255), anchor='mm')
    d.rectangle((x0 + 64, y + 50, x0 + 1520, y + 128), fill=(250, 250, 250, 245))
    d.text((x0 + 92, y + 89), "HELL'S KITCHEN NEWCOMER BUYS HIS THIRD BUILDING THIS MONTH", font=anton(44), fill=(20, 22, 28, 255), anchor='lm')
    d.rectangle((x0 + 64, y + 128, x0 + 1520, y + 162), fill=(20, 22, 28, 235))
    off = int(lt * 260) % int(text_w(TICKER, font('Inter-Bold', 20)))
    d.text((x0 + 84 - off, y + 145), TICKER * 3, font=font('Inter-Bold', 20), fill=(255, 255, 255, 230), anchor='lm')
    d.rectangle((0, y + 128, 64, y + 162), fill=(0, 0, 0, 0))


def gradient_text(text, f, colors):
    """Text filled with a vertical gradient through [colors]."""
    box = f.getbbox(text)
    w, h = box[2] - box[0] + 8, box[3] - box[1] + 8
    mask = Image.new('L', (w, h), 0)
    ImageDraw.Draw(mask).text((4 - box[0], 4 - box[1]), text, font=f, fill=255)
    g = np.zeros((h, w, 3), np.float32)
    n = len(colors) - 1
    for y in range(h):
        u = y / max(1, h - 1) * n
        i = min(int(u), n - 1); fr = u - i
        g[y] = np.array(colors[i]) * (1 - fr) + np.array(colors[i + 1]) * fr
    im = Image.fromarray(g.astype(np.uint8)).convert('RGBA')
    im.putalpha(mask)
    return im


def logo_card(lt):
    """The name on a sunset: violet to rose to amber, the words in a sunset fill with a glow."""
    yy = np.linspace(0, 1, H, dtype=np.float32)[:, None]
    top, mid, bot = np.array([.16, .07, .28]), np.array([.62, .16, .38]), np.array([.98, .55, .22])
    col = np.where(yy < .55, top + (mid - top) * (yy / .55), mid + (bot - mid) * ((yy - .55) / .45))
    a = np.repeat(col[:, None, :], W, 1).reshape(H, W, 3)
    # a low sun on the horizon line
    r = np.sqrt(((xx - W / 2) / 520) ** 2 + ((yy[:, 0][:, None] * H - H * .74) / 520) ** 2)
    a = a + np.clip(1 - r, 0, 1)[..., None] ** 2 * np.array([.5, .32, .12])
    img = to_img(a * (.85 + .15 * ease_out(lt / 1.2))).convert('RGBA')
    k = ease_out(lt / .5)
    s = 1.08 - .08 * k
    sunset = [(255, 236, 170), (255, 170, 90), (255, 84, 140)]
    for txt, size, y in (('HUSTLE', 250, H * .27), ('CITY', 250, H * .49)):
        t = gradient_text(txt, anton(int(size * s)), sunset)
        glow = Image.new('RGBA', img.size, (0, 0, 0, 0))
        x = int((W - t.size[0]) / 2); yv = int(y - t.size[1] / 2 + (1 - k) * 30)
        glow.alpha_composite(t, (x, yv))
        img.alpha_composite(Image.eval(glow.filter(ImageFilter.GaussianBlur(22)), lambda v: int(v * .8)))
        img.alpha_composite(t, (x, yv))
    d = ImageDraw.Draw(img)
    tag = 'NOW ON WINDOWS  &  ANDROID'
    ta = int(255 * ease_out((lt - .7) / .6))
    ft = font('Inter-Bold', 34)
    draw_text(d, (W - text_w(tag, ft, 10)) / 2, H * .7, tag, ft, (255, 255, 255, ta), 10)
    fc = font('Inter-Medium', 18)
    cred = 'Original score  ·  Cars: Quaternius (CC0)  ·  Materials: ambientCG (CC0)  ·  Logo type: Anton (OFL)'
    d.text(((W - fc.getlength(cred)) / 2, H - 70), cred, font=fc, fill=(255, 255, 255, int(ta * .55)))
    return np.asarray(img.convert('RGB'), np.float32) / 255


# ---------------------------------------------------------------- one frame
BARS = 104  # 2.39:1


def frame_at(n):
    t = n / FPS
    cut = next((c for c in EDL if c[0] <= t < c[1]), EDL[-1])
    t0, t1, kind, prm = cut
    lt, dur = t - t0, t1 - t0
    bars = kind in ('shot', 'black')
    if kind == 'black': a = np.zeros((H, W, 3), np.float32)
    elif kind == 'shot' or kind == 'news': a = shot_frame(prm, lt)
    elif kind == 'phone': a = phone_frame(prm, lt, dur)
    else: a = logo_card(lt)
    if kind in ('shot', 'news'):
        # a slow push in, so even a locked-off shot breathes
        z = 1 + .025 * lt / max(dur, .1)
        if z > 1.001:
            im = to_img(a)
            a = np.asarray(im.resize((int(W * z), int(H * z)), Image.BILINEAR).crop((int(W * (z - 1) / 2), int(H * (z - 1) / 2),
                                                                                   int(W * (z - 1) / 2) + W, int(H * (z - 1) / 2) + H)), np.float32) / 255
        if t >= 49.2 and t < 55.2: a *= 1 - .25 * ease_out((t - 53.5) / 1.5)
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    if kind == 'news': news(layer, lt, dur)
    for s0, s1, who, line in SUBS:
        if s0 <= t < s1: subtitle(layer, t - s0, s1 - s0, who, line)
    img = to_img(a).convert('RGBA')
    img.alpha_composite(shadowed(layer, 8, .7, (0, 2)))
    a = np.asarray(img.convert('RGB'), np.float32) / 255
    fl = sum(s * math.exp(-(t - ft) / d) for ft, s, d in FLASHES if t >= ft)
    if fl > 0: a = a + (1 - a) * min(1, fl)
    g = GRAIN[n % len(GRAIN)]
    a += np.repeat(np.repeat(g, 2, 0), 2, 1)[..., None] * (.02 if kind != 'logo' else .012)
    if bars: a[:BARS] = 0; a[H - BARS:] = 0
    if t >= 59.1: a *= 1 - ease_in_out((t - 59.1) / .85)
    return to_img(a)


def plan(path):
    """Every source frame the cut reads, per shot."""
    need = {}
    for t0, t1, kind, prm in EDL:
        if 'shot' not in prm: continue
        recorded = len([d for d in os.listdir(os.path.join(CINEMA, prm['shot'])) if d.isdigit()])
        for i in range(int(round((t1 - t0) * FPS)) + 1):
            need.setdefault(prm['shot'], set()).add(min(recorded - 1, prm['f0'] + int(round(i * prm.get('speed', 1.0)))))
    with open(path, 'w') as f:
        for shot in sorted(need): f.write(shot + ' ' + ','.join(map(str, sorted(need[shot]))) + '\n')
    print(sum(len(v) for v in need.values()), 'frames from', len(need), 'shots')


if __name__ == '__main__':
    if '--plan' in sys.argv:
        plan(sys.argv[sys.argv.index('--plan') + 1])
        sys.exit(0)
    if '--stills' in sys.argv:
        k = sys.argv.index('--stills')
        stills(frame_at, sys.argv[k + 1], sys.argv[k + 2:])
        sys.exit(0)
    encode(frame_at, NF, SCORE, OUT)
