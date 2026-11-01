"""Cuts the life trailer: one day in Hustle City's five cities, the people at work and play, cut to a feel-good song.

usage: python3 life.py --fetch <audio dir>                                 downloads the song
       python3 life.py <frames dir> <cinema dir> <audio dir> <out.mp4>    cuts and encodes
       python3 life.py ... --plan <file>     lists the frames the cut uses, for CinemaRender's -PcinemaPlan
       python3 life.py ... --stills <dir> <frame or seconds> [...]       single frames as JPG to check the look

The music is "One More Dance" by Arulo, from Mixkit (https://mixkit.co/free-stock-music/, the Mixkit Stock Music Free
License: made by a person, free to use in videos, the file itself not to be redistributed), so it is fetched rather than
kept in the repository. It runs at 110 BPM, and its arc is the trailer's:

    beats   0 - 20   a soft intro          dawn, the first cart, Alex heads out, the morning rush
           20 - 44   the bass comes in     the cities, one bar each: from the air, then down at a street food cart
           44 - 48   a break               the name
           48 - 80   the chorus            the work, a cut every two beats: every job its own animation
           80 - 88   a lift                the sun goes down over the skyline (a time-lapse)
           88 - 144  the big section       the night: carts and tents lit, the club, the rain, the late shift
          144 - 152  a dip                 Alex on the waterfront
          152 - 168  the finale            the day again in two-beat cuts, up and away over Shinjuku
          168 -      the last hit          the name

The shots are CinematicTest's l01 .. l56, rendered with the desktop's CinemaRender into <frames dir> (PNG or JPG).
"""
import math, os, subprocess, sys
from multiprocessing import Pool
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from kit import *

TRACK = ('one_more_dance.mp3', 'https://assets.mixkit.co/music/288/288.mp3')
CREDIT = 'Music: "One More Dance" by Arulo (Mixkit)'


def fetch(d):
    os.makedirs(d, exist_ok=True)
    p = os.path.join(d, TRACK[0])
    if not os.path.exists(p): subprocess.run(['curl', '-sS', '-f', '-L', '-o', p, TRACK[1]], check=True)
    print('fetched', p)


if '--fetch' in sys.argv:
    fetch(sys.argv[sys.argv.index('--fetch') + 1])
    sys.exit(0)

FRAMES, CINEMA, AUDIO, OUT = sys.argv[1:5]
use(FRAMES, CINEMA)

# ---------------------------------------------------------------- the beat grid (fitted to the track: 110.00 BPM)
BEAT = 60 / 110
D0 = 0.028


def tb(b): return D0 + b * BEAT


VERSE, BREAK1, CHORUS, LIFT, NIGHT, DIP, FINALE, NAME = 20, 44, 48, 80, 88, 144, 152, 168
DUR = tb(NAME) + 6.6
NF = int(round(DUR * FPS))

LOOKS.update({  # exposure, contrast, saturation, shadow tint, highlight tint, bloom amount, bloom threshold
    'dawn': (1.02, .26, 1.04, (-.02, .0, .045), (.05, .02, -.01), .3, .72),
    'morning': (1.03, .24, 1.1, (-.012, .006, .03), (.04, .02, -.012), .24, .78),
    'golden': (1.05, .26, 1.22, (-.02, -.004, .045), (.07, .02, -.035), .42, .66),
    'warm': (1.04, .2, 1.14, (-.01, .002, .02), (.03, .014, -.014), .22, .8),
    'neon': (1.1, .32, 1.3, (-.03, .012, .06), (.05, -.005, .04), .75, .55),
    'club': (.96, .34, 1.38, (-.022, -.012, .072), (.062, -.012, .062), .72, .55),
})
WEATHER = ('l47_rain',)

# ---------------------------------------------------------------- the cut, in beats
# (from, to, shot, first frame, speed (source frames per output frame), look)
CUT = [
    # the morning, letterboxed
    (0, 7, 'l01_dawn', 0, .9, 'dawn'),
    (7, 10, 'l02_cart_dawn', 6, 1, 'morning'),
    (10, 16, 'l04_wake', 2, 1, 'morning'),
    (16, 20, 'l03_scramble_am', 14, 1, 'morning'),
    # the cities: from the air, then down at a cart
    (20, 22, 'l05_nyc_air', 0, 1, 'day'),
    (22, 24, 'l06_nyc_cart', 8, 1, 'day'),
    (24, 26, 'l07_manila_jeep', 4, 1, 'day'),
    (26, 28, 'l08_manila_cart', 8, 1, 'day'),
    (28, 30, 'l09_tokyo_air', 4, 1, 'day'),
    (30, 32, 'l10_tokyo_yatai', 8, 1, 'day'),
    (32, 34, 'l11_seoul_air', 4, 1, 'day'),
    (34, 36, 'l12_seoul_pocha', 8, 1, 'day'),
    (36, 38, 'l13_classic_air', 4, 1, 'warm'),
    (38, 40, 'l14_classic_cart', 8, 1, 'warm'),
    (40, 44, 'l26_pay', 4, 1.3, 'day'),
    (44, 48, 'l26_pay', 72, .25, 'day'),
    # the work
    (48, 52, 'l20_salon', 6, 1, 'interior'),
    (52, 54, 'l21_waiting', 10, 1, 'interior'),
    (54, 56, 'l22_till', 10, 1, 'interior'),
    (56, 58, 'l23_selfcheck', 10, 1, 'interior'),
    (58, 60, 'l24_boxing', 10, 1, 'interior'),
    (60, 62, 'l25_coach', 10, 1, 'interior'),
    (62, 64, 'l27_cafe', 10, 1, 'interior'),
    (64, 66, 'l32_kiosk', 10, 1, 'interior'),
    (66, 68, 'l33_player_till', 10, 1, 'interior'),
    (68, 70, 'l28_mop', 10, 1, 'interior'),
    (70, 72, 'l29_stock', 10, 1, 'interior'),
    (72, 74, 'l30_seoul_lunch', 10, 1, 'day'),
    (74, 76, 'l31_stage', 10, 1, 'interior'),
    (76, 80, 'l05_nyc_air', 28, .5, 'golden'),
    # the sun goes down
    (80, 88, 'l40_timelapse', 6, 1, 'dusk'),
    # the night
    (88, 92, 'l41_yatai_night', 4, 1, 'neon'),
    (92, 96, 'l42_pocha_night', 4, 1, 'neon'),
    (96, 98, 'l43_nyc_cart_night', 6, 1, 'neon'),
    (98, 100, 'l44_manila_night', 6, 1, 'neon'),
    (100, 104, 'l45_dj', 6, 1, 'club'),
    (104, 106, 'l46_dance', 8, 1, 'club'),
    (106, 108, 'l47_rain', 8, 1, 'rain'),
    (108, 112, 'l53_hero_night', 6, 1, 'neon'),
    (112, 114, 'l50_scramble_night', 6, 1, 'neon'),
    (114, 116, 'l49_times', 6, 1, 'neon'),
    (116, 118, 'l48_night_till', 8, 1, 'interior'),
    (118, 120, 'l51_classic_night', 6, 1, 'neon'),
    (120, 124, 'l54_seoul_tower', 4, 1, 'neon'),
    (124, 128, 'l52_makati', 4, 1, 'neon'),
    # one beat a cut
    (128, 129, 'l46_dance', 36, 1, 'club'),
    (129, 130, 'l41_yatai_night', 40, 1, 'neon'),
    (130, 131, 'l50_scramble_night', 36, 1, 'neon'),
    (131, 132, 'l47_rain', 36, 1, 'rain'),
    (132, 133, 'l42_pocha_night', 40, 1, 'neon'),
    (133, 134, 'l45_dj', 40, 1, 'club'),
    (134, 135, 'l53_hero_night', 60, 1, 'neon'),
    (135, 136, 'l49_times', 40, 1, 'neon'),
    (136, 140, 'l43_nyc_cart_night', 30, .6, 'neon'),
    (140, 144, 'l44_manila_night', 30, .6, 'neon'),
    # Alex on the waterfront
    (144, 152, 'l55_shore', 0, 1, 'neon'),
    # the day again
    (152, 154, 'l06_nyc_cart', 32, 1, 'day'),
    (154, 156, 'l07_manila_jeep', 30, 1, 'day'),
    (156, 158, 'l20_salon', 34, 1, 'interior'),
    (158, 160, 'l24_boxing', 34, 1, 'interior'),
    (160, 162, 'l10_tokyo_yatai', 30, 1, 'day'),
    (162, 164, 'l12_seoul_pocha', 30, 1, 'day'),
    (164, 168, 'l56_finale', 0, 1.3, 'neon'),
]

# the time of day, top left, through the morning: (from, to, clock, place)
CLOCKS = [(0.4, 7, '6:21 AM', 'MANILA'), (7, 10, '7:06 AM', 'HUSTLE CITY'), (10, 16, '7:54 AM', 'HUSTLE CITY'),
          (16, 20, '8:24 AM', 'TOKYO')]
# the cities: (from, to, name, what you find there)
CITIES = [(20, 24, 'NEW YORK', 'Yellow cabs  ·  bodegas  ·  hot dog carts'), (24, 28, 'MANILA', 'Jeepneys  ·  banderitas  ·  fishball carts'),
          (28, 32, 'TOKYO', 'Shibuya  ·  lanterns  ·  yatai stalls'), (32, 36, 'SEOUL', 'Gwanghwamun  ·  neon  ·  pojangmacha'),
          (36, 40, 'HUSTLE CITY', 'The original grid  ·  seven avenues')]
# what you do: (from, to, words)
CAPTIONS = [(40, 44, 'GRAB A BITE.'), (48, 52, 'CUT HAIR.'), (52, 54, 'KEEP THEM WAITING.'), (54, 56, 'RING IT UP.'),
            (56, 58, 'OR LET THEM DO IT.'), (58, 60, 'HIT THE BAG.'), (60, 62, 'TRAIN THEM.'), (62, 64, 'FEED THE RUSH.'),
            (64, 66, 'TAKE ORDERS.'), (66, 68, 'WORK THE TILL.'), (68, 70, 'MOP UP.'), (70, 72, 'STOCK THE SHELVES.'),
            (72, 74, 'EAT ON THE STREET.'), (74, 76, 'PUT ON A SHOW.'), (76, 80, 'BUILD YOUR EMPIRE.'),
            (100, 104, 'RUN THE NIGHT.'), (108, 112, 'OWN THE STREETS.'), (116, 118, 'WORK THE LATE SHIFT.')]
# centred lines: (from, to, text)
STATEMENTS = [(80.5, 87.6, 'WHEN THE SUN GOES DOWN'), (145, 151.6, 'WHAT WILL YOU BUILD?')]
TITLE = (BREAK1, CHORUS)

# flashes of white on the big entrances, a beat of zoom on every bar of the chorus and the night
FLASH = [(VERSE, .45, .18), (CHORUS, .55, .2), (NIGHT, .6, .22), (FINALE, .5, .2), (NAME, .8, .3)]
PUNCH = [(b, .03) for b in range(CHORUS, LIFT, 4)] + [(b, .035) for b in range(NIGHT, DIP, 4)] + [(b, .02) for b in range(128, 136)] + \
        [(b, .03) for b in range(FINALE, NAME, 2)]


# ---------------------------------------------------------------- pieces
_count = {}


def recorded(shot):
    """How many frames a shot has: the recording's frame folders (kept when its geometry is cleared after rendering)."""
    if shot not in _count: _count[shot] = len([n for n in os.listdir(os.path.join(CINEMA, shot)) if n.isdigit()])
    return _count[shot]


def position(clip, lt):
    b0, b1, shot, f0, sp, _ = clip
    return min(max(f0 + FPS * sp * lt, 0), recorded(shot) - 1)


def shot_image(clip, lt):
    shot = clip[2]
    p = position(clip, lt)
    i = int(p); fr = p - i
    im = src(shot, i)
    # slow motion: blend between the rendered frames instead of repeating them
    if fr > .08 and i + 1 < recorded(shot): im = Image.blend(im.convert('RGB'), src(shot, i + 1).convert('RGB'), fr)
    a = grade(im, clip[5])
    if shot in WEATHER:
        ov = overlay(shot, int(round(p)))
        if ov:
            o = np.asarray(ov, np.float32) / 255
            k = o[..., 3:] * .8
            a = a * (1 - k) + o[..., :3] * k
    return a


def clip_at(t):
    for c in CUT:
        if tb(c[0]) <= t < tb(c[1]): return c
    return None


def decay(t, events, tau=.15):
    s = 0.0
    for e in events:
        dt = t - tb(e[0])
        if 0 <= dt < 1.2: s += e[1] * math.exp(-dt / (e[2] if len(e) > 2 else tau))
    return s


def transform(a, zoom):
    if zoom <= 1.0005: return a
    im = to_img(a)
    w, h = W / zoom, H / zoom
    x0 = (W - w) / 2; y0 = (H - h) / 2
    return np.asarray(im.transform((W, H), Image.EXTENT, (x0, y0, x0 + w, y0 + h), Image.BILINEAR), np.float32) / 255


def clock(layer, t, dur, time, place):
    """The time of day and the city, set small at the top left."""
    a = fade(t, dur, .35, .25)
    d = ImageDraw.Draw(layer)
    x, y = 96, 158
    f = font('InterDisplay-Bold', 54)
    draw_text(d, x, y, time, f, (255, 255, 255, int(255 * a)), 1)
    d.rectangle((x, y + 76, x + 40 * ease_out(t / .4), y + 79), fill=ORANGE + (int(255 * a),))
    draw_text(d, x, y + 92, place, font('Inter-SemiBold', 22), (235, 238, 244, int(235 * a)), 5)


def ticking(layer, t, p):
    """The clock running through the time-lapse: [p] is the shot's frame, its game time from 6:12 PM over 165 minutes."""
    m = 18 * 60 + 12 + 165 * min(max(p, 0), 119) / 119
    hh, mm = int(m // 60), int(m % 60)
    clock(layer, t, 99, f'{hh - 12}:{mm:02d} PM', 'NEW YORK')


def caption(layer, t, dur, words):
    """Big words rising into the bottom left, an orange rule over them."""
    headline(layer, t, dur, [words], None, 112, H - 128, 88, 24, 900)


def statement_c(layer, t, dur, text):
    a = fade(t, dur, .5, .4)
    f = font('InterDisplay-SemiBold', 64)
    track = 10 + 10 * (t / dur)
    d = ImageDraw.Draw(layer)
    w = text_w(text, f, track)
    draw_text(d, (W - w) / 2, H / 2 - 44, text, f, (255, 255, 255, int(255 * a)), track)


_logo_bg = None


def name_card(lt):
    """The name on a warm dark ground: the icon, HUSTLE CITY, the tag, the platforms and the credits."""
    global _logo_bg
    if _logo_bg is None:
        r = np.sqrt(((xx - W / 2) / 1000) ** 2 + ((yy - H * .45) / 600) ** 2)
        _logo_bg = (np.clip(1 - r, 0, 1) ** 2)[..., None] * np.array([.13, .1, .07], np.float32)
    img = to_img(_logo_bg * ease_out(lt / .5)).convert('RGBA')
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    k = ease_out(lt / .2)
    s = 1.12 - .12 * k + .03 * min(lt / 6, 1)
    f = font('InterDisplay-Black', int(168 * s))
    w1, w2 = text_w('HUSTLE ', f, -2), text_w('CITY', f, -2)
    x = (W - w1 - w2) / 2; y = H * .43 - f.size * .6
    d = ImageDraw.Draw(layer)
    ic = ICON.resize((int(ICON.size[0] * .8 * s), int(ICON.size[1] * .8 * s)), Image.LANCZOS)
    ic.putalpha(ic.split()[3].point(lambda v: int(v * k)))
    layer.alpha_composite(ic, (int((W - ic.size[0]) / 2), int(y - ic.size[1] - 24)))
    draw_text(d, x, y, 'HUSTLE ', f, (255, 255, 255, int(255 * k)), -2)
    draw_text(d, x + w1, y, 'CITY', f, ORANGE + (int(255 * k),), -2)
    a2 = int(255 * ease_out((lt - .7) / .5))
    tag = 'FIVE CITIES.  ONE HUSTLE.'
    ft = font('InterDisplay-Bold', 40)
    draw_text(d, (W - text_w(tag, ft, 16)) / 2, H * .43 + 150, tag, ft, (255, 255, 255, a2), 16)
    a3 = int(255 * ease_out((lt - 1.5) / .5))
    plat = 'ANDROID  ·  WINDOWS'
    fp = font('Inter-SemiBold', 26)
    draw_text(d, (W - text_w(plat, fp, 8)) / 2, H * .43 + 222, plat, fp, (226, 229, 235, int(a3 * .9)), 8)
    fc = font('Inter-Medium', 19)
    cred = CREDIT + '  ·  Cars and people: Quaternius (CC0)  ·  Materials: ambientCG (CC0)'
    d.text(((W - fc.getlength(cred)) / 2, H - 70), cred, font=fc, fill=(255, 255, 255, int(a3 * .5)))
    glow = layer.filter(ImageFilter.GaussianBlur(24))
    glow.putalpha(glow.split()[3].point(lambda v: int(v * .35)))
    img.alpha_composite(glow)
    img.alpha_composite(layer)
    a = np.asarray(img.convert('RGB'), np.float32) / 255
    if lt > DUR - tb(NAME) - 1.0: a *= 1 - ease_in_out((lt - (DUR - tb(NAME) - 1.0)) / .95)
    return a


LB = 138  # 2.39:1


def letterbox(t):
    """The scope bars: in for the morning and the waterfront, opening on the bass and on the finale."""
    if t < tb(VERSE): return 1.0
    if t < tb(VERSE) + .4: return 1 - ease_in_out((t - tb(VERSE)) / .4)
    if tb(DIP) <= t < tb(FINALE): return ease_in_out((t - tb(DIP)) / .6)
    if tb(FINALE) <= t < tb(FINALE) + .3: return 1 - ease_in_out((t - tb(FINALE)) / .3)
    return 0.0


# ---------------------------------------------------------------- one frame
def frame_at(n):
    t = n / FPS
    if t >= tb(NAME):
        a = name_card(t - tb(NAME))
        fl = decay(t, [f for f in FLASH if f[0] == NAME])
        if fl > 0: a = a + (1 - a) * min(1, fl)
        return to_img(a)
    clip = clip_at(t)
    lt = t - tb(clip[0]) if clip else 0
    a = shot_image(clip, lt) if clip else np.zeros((H, W, 3), np.float32)
    if clip:
        dur = tb(clip[1]) - tb(clip[0])
        a = transform(a, 1.015 + .03 * lt / max(dur, .2) + decay(t, PUNCH, .14))
    # fade up from black at the start; the title over a darkened, blurred frame
    if t < 1.2: a *= ease_in_out(t / 1.2)
    title_on = tb(TITLE[0]) <= t < tb(TITLE[1])
    if title_on:
        k = min(1, (t - tb(TITLE[0])) / .25)
        blur = to_img(a).filter(ImageFilter.GaussianBlur(10 * k))
        a = np.asarray(blur, np.float32) / 255 * (1 - .55 * k)
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    for b0, b1, tm, place in CLOCKS:
        if tb(b0) <= t < tb(b1): clock(layer, t - tb(b0), tb(b1) - tb(b0), tm, place)
    if clip and clip[2] == 'l40_timelapse': ticking(layer, t - tb(clip[0]), position(clip, lt))
    for b0, b1, name, sub in CITIES:
        if tb(b0) <= t < tb(b1): city(layer, t - tb(b0), tb(b1) - tb(b0), name, sub)
    for b0, b1, words in CAPTIONS:
        if tb(b0) <= t < tb(b1): caption(layer, t - tb(b0), tb(b1) - tb(b0), words)
    for b0, b1, text in STATEMENTS:
        if tb(b0) <= t < tb(b1): statement_c(layer, t - tb(b0), tb(b1) - tb(b0), text)
    if title_on: title(layer, t - tb(TITLE[0]), sub='FIVE CITIES  ·  ONE DAY  ·  YOUR HUSTLE', hold=tb(TITLE[1]) - tb(TITLE[0]) - .2)
    img = to_img(a).convert('RGBA')
    img.alpha_composite(shadowed(layer, 10, .65, (0, 3)))
    a = np.asarray(img.convert('RGB'), np.float32) / 255
    fl = decay(t, FLASH)
    if fl > 0: a = a + (1 - a) * min(1, fl)
    g = GRAIN[n % len(GRAIN)]
    a += np.repeat(np.repeat(g, 2, 0), 2, 1)[..., None] * .012
    lb = letterbox(t)
    if lb > 0:
        hgt = int(round(LB * lb))
        a[:hgt] = 0; a[H - hgt:] = 0
    return to_img(a)


def frame_bytes(n): return frame_at(n).tobytes()


# ---------------------------------------------------------------- sound
def mix(path, sr=48000):
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', os.path.join(AUDIO, TRACK[0]), '-f', 'f32le', '-ac', '2', '-ar', str(sr), '-'],
                         capture_output=True, check=True).stdout
    m = np.frombuffer(raw, np.float32).reshape(-1, 2).copy()
    n = int(DUR * sr)
    out = np.zeros((n, 2), np.float32)
    k = min(n, len(m))
    out[:k] = m[:k]
    # the song rings out under the name and fades with the picture
    f0 = int((DUR - 1.2) * sr)
    if f0 < n: out[f0:] *= np.linspace(1, 0, n - f0)[:, None]
    with open(path, 'wb') as fo: fo.write(out.tobytes())


def encode(out):
    pcm = out + '.f32'
    mix(pcm)
    cmd = ['ffmpeg', '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}', '-r', str(FPS), '-i', '-',
           '-f', 'f32le', '-ar', '48000', '-ac', '2', '-i', pcm, '-map', '0:v', '-map', '1:a',
           '-c:v', 'libx264', '-preset', 'slow', '-crf', '19', '-tune', 'film', '-pix_fmt', 'yuv420p',
           '-af', 'loudnorm=I=-14:TP=-1:LRA=11', '-c:a', 'aac', '-b:a', '256k', '-ar', '48000', '-movflags', '+faststart', '-shortest', out]
    p = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    with Pool(os.cpu_count()) as pool:
        for i, buf in enumerate(pool.imap(frame_bytes, range(NF), chunksize=4)):
            p.stdin.write(buf)
            if i % 96 == 0: print(f'{i / FPS:5.1f}s', flush=True)
    p.stdin.close()
    p.wait()
    os.remove(pcm)
    print(out)


def plan(path):
    """Every source frame the cut reads, per shot (both neighbours where slow motion blends them)."""
    need = {}
    for n in range(NF):
        t = n / FPS
        c = clip_at(t)
        # (only the shots recorded so far: they are recorded and rendered a few at a time)
        if t >= tb(NAME) or c is None or not os.path.isdir(os.path.join(CINEMA, c[2])): continue
        p = position(c, t - tb(c[0]))
        for i in {int(p), min(int(p) + 1, recorded(c[2]) - 1)}: need.setdefault(c[2], set()).add(i)
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
    print(f'{DUR:.2f} s, {NF} frames')
    encode(OUT)
