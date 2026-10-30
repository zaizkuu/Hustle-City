"""Cuts the banger trailer: Hustle City at 152 BPM, every cut on the beat.

usage: python3 banger.py --fetch <audio dir>                                downloads the track and the sound effects
       python3 banger.py <frames dir> <cinema dir> <audio dir> <out.mp4>   cuts and encodes
       python3 banger.py ... --plan <file>     lists the frames the cut uses, for CinemaRender's -PcinemaPlan
       python3 banger.py ... --stills <dir> <frame or seconds> [...]       single frames as JPG to check the look
       python3 banger.py ... --audio <file.wav>                           just the mastered sound

The music is "Trap" and the hits, whooshes, risers and sirens are sound effects, all from Mixkit
(https://mixkit.co, the Mixkit free licences: free to use in videos, the files themselves not to be redistributed),
so they are fetched rather than kept in the repository. The track's arc is the trailer's: an eight-bar intro with
no bass for the cold open, a sixteen-bar drop for the hustle, an eight-bar breakdown for the night and the police,
and the second drop for the getaway, cut to black on a bar line for the name.

The shots are CinematicTest's n01 .. n21 and b01 .. b05, rendered with the desktop's CinemaRender into <frames dir>.
"""
import math, os, subprocess, sys
from multiprocessing import Pool
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from kit import *

TRACK = ('trap.mp3', 'https://assets.mixkit.co/music/305/305.mp3')
SFX_IDS = {  # name: Mixkit sound effect id
    'impact_trailer': 2908, 'impact_big': 788, 'impact_logo': 2900, 'impact_whoosh': 1143, 'whoosh_fast': 1492,
    'whoosh_air': 1489, 'riser': 790, 'riser_stutter': 1144, 'glitch_break': 2951, 'glitch_text': 2950,
    'siren': 1643, 'police_arrive': 1655, 'drive_by': 1538, 'money_bag': 1989, 'coins': 1993,
}
# where each effect's hit is, seconds into its file, so the hit lands on the beat
SFX_PEAK = {'impact_trailer': 1.0, 'impact_big': 2.18, 'impact_logo': 4.84, 'impact_whoosh': .56, 'whoosh_fast': 1.08,
            'whoosh_air': .72, 'riser': 2.54, 'riser_stutter': 6.25, 'glitch_break': .02, 'glitch_text': 1.31,
            'siren': 1.2, 'police_arrive': 2.92, 'drive_by': 1.22, 'money_bag': .12, 'coins': .35}


def fetch(d):
    os.makedirs(d, exist_ok=True)
    jobs = [TRACK] + [(f'{n}.mp3', f'https://assets.mixkit.co/active_storage/sfx/{i}/{i}-preview.mp3') for n, i in SFX_IDS.items()]
    for name, url in jobs:
        p = os.path.join(d, name)
        if not os.path.exists(p): subprocess.run(['curl', '-sS', '-f', '-L', '-o', p, url], check=True)
    print('fetched', len(jobs), 'files into', d)


if '--fetch' in sys.argv:
    fetch(sys.argv[sys.argv.index('--fetch') + 1])
    sys.exit(0)

FRAMES, CINEMA, AUDIO, OUT = sys.argv[1:5]
use(FRAMES, CINEMA)

# ---------------------------------------------------------------- the beat grid (fitted to the track: 152.00 BPM)
BEAT = 0.39473353
D0 = 0.010


def tb(b): return D0 + b * BEAT


DROP1, BREAK, DROP2, NAME = 32, 96, 128, 148
DUR = tb(NAME) + 4.2
NF = int(round(DUR * FPS))

ANTON = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'fonts', 'Anton-Regular.ttf')
_anton = {}


def anton(size):
    if size not in _anton: _anton[size] = ImageFont.truetype(ANTON, size)
    return _anton[size]


LOOKS.update({  # exposure, contrast, saturation, shadow tint, highlight tint, bloom amount, bloom threshold
    'dawn': (1.0, .28, 1.0, (-.02, .0, .045), (.05, .02, -.01), .3, .72),
    'morning': (1.03, .28, 1.12, (-.014, .006, .035), (.04, .02, -.012), .25, .76),
    'golden': (1.06, .3, 1.28, (-.024, -.004, .05), (.08, .022, -.04), .45, .64),
    'sunset': (1.05, .3, 1.3, (-.024, -.01, .065), (.09, .02, -.032), .52, .6),
    'neon': (1.12, .36, 1.34, (-.035, .014, .07), (.055, -.006, .045), .8, .52),
    'club': (.96, .36, 1.4, (-.024, -.012, .075), (.065, -.012, .065), .75, .54),
    'teal': (1.0, .34, 1.05, (-.03, .02, .06), (.03, .018, .0), .6, .56),
})
WEATHER = ('n14_rain_cross', 'n17_snow')

# ---------------------------------------------------------------- the cut, in beats
# (from, to, shot, first frame, speed (source frames per output frame, or a ramp (start, end)), look)
CUT = [
    # the cold open: eight bars with no bass, letterboxed
    (0, 4, None, 0, 1, None),
    (4, 8, 'n01_fidi_dawn', 20, .9, 'dawn'),
    (8, 12, 'n03_rush', 25, 1, 'morning'),
    (12, 16, 'n02_hk_morning', 20, 1, 'morning'),
    (16, 20, 'n20_finale', 0, 1, 'golden'),
    (20, 24, 'b04_fpv', 0, 1.3, 'golden'),
    (24, 26, 'n10_news_aerial', 20, 1, 'day'),
    (26, 27, 'n04_bridge_gold', 30, 1, 'golden'),
    (27, 28, 'n06_times_dusk', 30, 1, 'neon'),
    (28, 32, None, 0, 1, None),
    # the drop: the hustle, a cut every two beats
    (32, 36, 'n03_rush', 0, 1.6, 'morning'),
    (36, 38, 'n13_cafe', 10, 1, 'interior'),
    (38, 40, 'n11_gym', 10, 1, 'interior'),
    (40, 42, 'n09_van_bridge', 10, 1.3, 'day'),
    (42, 48, 'n10_news_aerial', 4, 1, 'day'),
    (48, 52, 'n06_times_dusk', 0, 1.4, 'neon'),
    (52, 54, 'n12_club', 10, 1, 'club'),
    (54, 55, 'n12_club', 40, 1, 'club'),
    (55, 56, 'n13_cafe', 40, 1, 'interior'),
    (56, 58, 'n05_lake', 20, 1, 'golden'),
    (58, 60, 'n08_brooklyn', 20, 1, 'sunset'),
    (60, 62, 'n17_snow', 20, 1, 'snow'),
    (62, 64, 'n02_hk_morning', 45, 1, 'morning'),
    (64, 68, 'n04_bridge_gold', 0, 1.2, 'golden'),
    (68, 70, 'n14_rain_cross', 10, 1, 'rain'),
    (70, 72, 'n18_fidi_night', 10, 1, 'neon'),
    (72, 74, 'n11_gym', 35, 1, 'interior'),
    (74, 76, 'n15_hypercar', 10, 1, 'neon'),
    (76, 78, 'n09_van_bridge', 35, 1, 'day'),
    (78, 80, 'n19_skyline_night', 10, 1, 'neon'),
    (80, 84, 'n16_river_night', 0, 1.4, 'neon'),
    (84, 86, 'n01_fidi_dawn', 0, 1, 'dawn'),
    (86, 88, 'n03_rush', 50, 1, 'morning'),
    (88, 89, 'n12_club', 20, 1, 'club'),
    (89, 90, 'n11_gym', 50, 1, 'interior'),
    (90, 91, 'n13_cafe', 25, 1, 'interior'),
    (91, 92, 'n06_times_dusk', 45, 1, 'neon'),
    (92, 96, 'n19_skyline_night', 30, .8, 'neon'),
    # the breakdown: night, the police, slow motion, letterboxed again
    (96, 104, 'n16_river_night', 0, .5, 'teal'),
    (104, 108, 'n21_patrol', 0, .7, 'neon'),
    (108, 112, 'n14_rain_cross', 20, .6, 'rain'),
    (112, 116, 'b03_chase_high', 0, .7, 'neon'),
    (116, 120, 'b05_hero', 20, .8, 'neon'),
    (120, 124, 'b01_chase_front', 20, .6, 'neon'),
    # a stutter into the second drop, half a beat a cut
    (124, 124.5, 'b02_chase_drift', 0, 1, 'neon'),
    (124.5, 125, 'n21_patrol', 20, 1, 'neon'),
    (125, 125.5, 'b03_chase_high', 30, 1, 'neon'),
    (125.5, 126, 'b01_chase_front', 50, 1, 'neon'),
    (126, 126.5, 'n15_hypercar', 20, 1, 'neon'),
    (126.5, 127, 'b02_chase_drift', 10, 1, 'neon'),
    (127, 127.5, 'b05_hero', 55, 1, 'neon'),
    (127.5, 128, None, 0, 1, None),
    # the second drop: the getaway
    (128, 131, 'b02_chase_drift', 0, (.5, 1.4), 'neon'),
    (131, 133, 'b01_chase_front', 45, 1.2, 'neon'),
    (133, 134, 'b03_chase_high', 40, 1, 'neon'),
    (134, 135, 'n15_hypercar', 30, 1, 'neon'),
    (135, 137, 'b02_chase_drift', 18, (.35, 1), 'neon'),
    (137, 139, 'n21_patrol', 10, 1, 'neon'),
    (139, 141, 'b05_hero', 45, 1, 'neon'),
    (141, 142, 'b01_chase_front', 60, 1, 'neon'),
    (142, 143, 'b03_chase_high', 55, 1, 'neon'),
    (143, 144, 'n15_hypercar', 45, 1, 'neon'),
    (144, 145, 'b02_chase_drift', 28, 1, 'neon'),
    (145, 146, 'n18_fidi_night', 30, 1, 'neon'),
    (146, 146.5, 'b05_hero', 60, 1, 'neon'),
    (146.5, 147, 'n21_patrol', 25, 1, 'neon'),
    (147, 147.5, 'b01_chase_front', 70, 1, 'neon'),
    (147.5, 148, 'b03_chase_high', 62, 1, 'neon'),
]
SLAMS = [(32, 35.6, 'START WITH NOTHING.'), (48, 51.6, 'BUILD AN EMPIRE.'), (64, 67.6, 'OWN THE CITY.'), (80, 83.6, 'NO DAYS OFF.')]
NEWS = (42, 48)
# the voice-over: <audio dir>/voNN.mp3 (made in ElevenLabs), each line's speech starting at [at] seconds; the kind
# sets its treatment (dry, a TV broadcast, a police radio) and how far the music dips under it
VO = [  # (file, at, speaker (None: no subtitle), line, kind, level dBFS)
    ('vo01', .3, 'LOAN OFFICER', 'Five grand and a beat-up car.', 'dry', -20),
    ('vo02', 2.65, 'LOAN OFFICER', "That's your plan?", 'dry', -20),
    ('vo03', 3.8, 'ALEX', "That's my start.", 'dry', -21),
    ('vo04', 11.95, 'ALEX', 'Watch me.', 'dry', -22),
    ('vo08', 16.7, None, "Who is the newcomer buying up Hell's Kitchen?", 'tv', -19),
    ('vo05', 38.3, 'LOAN OFFICER', 'You know what this city does to people like you?', 'dry', -20),
    ('vo09', 41.05, 'DISPATCH', 'All units, purple hypercar, southbound on Broadway.', 'radio', -23),
    ('vo06', 44.8, 'ALEX', 'Yeah.', 'dry', -22),
    ('vo07', 46.1, 'ALEX', 'It makes room.', 'dry', -20),
]
DUCK = {'dry': 4, 'tv': 9, 'radio': 5}  # dB the music dips under a line


def speech_span(y, sr):
    """Where the speech in a take starts and ends, seconds: the envelope above -38 dBFS."""
    w = sr // 100
    env = np.sqrt(np.convolve(y * y, np.ones(w) / w, 'same'))
    on = np.where(env > 10 ** (-38 / 20))[0]
    return (on[0] / sr, on[-1] / sr) if len(on) else (0.0, len(y) / sr)


def _mono(path, sr):
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-f', 'f32le', '-ac', '1', '-ar', str(sr), '-'], capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).copy()


def subtitles():
    """The subtitles, timed to the takes that are there (a line without one keeps a nominal length)."""
    out = []
    for name, at, who, line, kind, _ in VO:
        if who is None: continue
        p = os.path.join(AUDIO, name + '.mp3')
        n = len(line.split())
        dur = .4 + .3 * n
        if os.path.exists(p):
            a, b = speech_span(_mono(p, 16000), 16000)
            dur = b - a
        out.append((at - .06, at + dur + .35, who, line))
    return out


SUBS = subtitles()
WHIPS = [40, 56, 68, 84, 137, 139]
STUTTER = [124 + i * .5 for i in range(8)] + [146, 146.5, 147, 147.5]

# effects: (beat, strength); punches zoom, shakes jolt (pixels), flashes whiten, splits pull the colours apart (pixels)
PUNCH = ([(b, .055) for b in range(DROP1, BREAK, 4)] + [(b, .03) for b in range(DROP1 + 2, BREAK, 4)]
         + [(b, .06) for b in range(DROP2, NAME, 2)] + [(s[0], .1) for s in SLAMS] + [(b, .04) for b in STUTTER])
SHAKE = [(DROP1, 26), (DROP2, 30), (BREAK, 16)] + [(s[0], 16) for s in SLAMS] + [(b, 10) for b in STUTTER] + [(NAME, 22)]
FLASH = [(DROP1, .9, .16), (DROP2, .9, .16), (NAME, .9, .22), (BREAK, .5, .3)] + [(s[0], .5, .14) for s in SLAMS] + \
        [(b, .3, .06) for b in STUTTER]
SPLIT = [(DROP1, 16, .22), (DROP2, 18, .22), (NAME, 22, .4)] + [(s[0], 9, .14) for s in SLAMS] + [(b, 12, .08) for b in STUTTER]

# sound: (beat or seconds as a float in a 1-tuple, effect, gain dB); the effect's hit lands on the time
SOUND = [
    (20, 'whoosh_air', -9), (DROP1, 'riser', -3), (DROP1, 'impact_trailer', -2), (36.2, 'coins', -9),
    (44, 'glitch_text', -12), (BREAK, 'impact_big', -4), (103, 'police_arrive', -13), (DROP2, 'riser_stutter', -5),
    (DROP2, 'impact_trailer', -1), (129, 'siren', -9), (131.3, 'drive_by', -6), (74.1, 'money_bag', -7), (NAME, 'impact_logo', 0),
] + [(s[0], 'impact_whoosh', -5) for s in SLAMS] + [(b, 'whoosh_fast', -8) for b in WHIPS] + \
    [(b, 'glitch_break', -10) for b in STUTTER]


# ---------------------------------------------------------------- pieces
_count = {}


def recorded(shot):
    if shot not in _count: _count[shot] = len([d for d in os.listdir(os.path.join(CINEMA, shot)) if d.isdigit()])
    return _count[shot]


def position(clip, lt):
    """Source frame (fractional) [lt] seconds into a clip, speed ramps integrated."""
    b0, b1, shot, f0, sp, _ = clip
    if isinstance(sp, tuple):
        d = tb(b1) - tb(b0)
        p = f0 + FPS * (sp[0] * lt + (sp[1] - sp[0]) * lt * lt / (2 * d))
    else: p = f0 + FPS * sp * lt
    return min(max(p, 0), recorded(shot) - 1)


def shot_image(clip, lt):
    shot = clip[2]
    p = position(clip, lt)
    i = int(p); fr = p - i
    im = src(shot, i)
    # slow motion: blend between the recorded frames instead of repeating them
    if fr > .08 and i + 1 < recorded(shot): im = Image.blend(im.convert('RGB'), src(shot, i + 1).convert('RGB'), fr)
    a = grade(im, clip[5])
    if shot in WEATHER:
        ov = overlay(shot, int(round(p)))
        if ov:
            o = np.asarray(ov, np.float32) / 255
            k = o[..., 3:] * .85
            a = a * (1 - k) + o[..., :3] * k
    return a


def clip_at(t):
    for c in CUT:
        if tb(c[0]) <= t < tb(c[1]): return c
    return None


def decay(t, events, tau_default=.15):
    """Sum of events that struck at or before t, each dying away exponentially."""
    s = 0.0
    for e in events:
        dt = t - tb(e[0])
        if 0 <= dt < 1.2: s += e[1] * math.exp(-dt / (e[2] if len(e) > 2 else tau_default))
    return s


def noise(t, seed):
    """Smooth deterministic wobble in -1..1."""
    return (math.sin(t * 13.1 + seed) * .5 + math.sin(t * 29.7 + seed * 2.3) * .3 + math.sin(t * 57.3 + seed * 4.1) * .2)


def transform(a, zoom, dx, dy):
    """Zoom in by [zoom] about the centre, shifted (dx, dy) pixels."""
    if zoom <= 1.0005 and abs(dx) < .5 and abs(dy) < .5: return a
    zoom = max(zoom, 1 + (abs(dx) * 2 + 4) / W, 1 + (abs(dy) * 2 + 4) / H)
    im = to_img(a)
    w, h = W / zoom, H / zoom
    x0 = (W - w) / 2 - dx / zoom; y0 = (H - h) / 2 - dy / zoom
    return np.asarray(im.transform((W, H), Image.EXTENT, (x0, y0, x0 + w, y0 + h), Image.BILINEAR), np.float32) / 255


def split(a, px):
    """Chromatic aberration: red one way, blue the other."""
    p = int(round(px))
    if p < 1: return a
    out = a.copy()
    out[:, p:, 0] = a[:, :-p, 0]; out[:, :-p, 2] = a[:, p:, 2]
    return out


def whip(a, k, direction):
    """A whip pan: the frame smeared sideways and slid [direction]."""
    if k <= 0: return a
    n = 9
    span = int(220 * k)
    acc = np.zeros_like(a)
    for j in range(n):
        acc += np.roll(a, int(direction * (span * j / (n - 1) + 260 * k)), axis=1)
    return acc / n


def subtitle(layer, lt, dur, speaker, line):
    d = ImageDraw.Draw(layer)
    al = fade(lt, dur, .1, .12)
    fs = font('Inter-SemiBold', 40)
    fn = font('Inter-Bold', 22)
    y = H - 138 - 96
    draw_text(d, (W - text_w(speaker, fn, 6)) / 2, y, speaker, fn, (242, 201, 76, int(255 * al)), 6)
    draw_text(d, (W - text_w(line, fs)) / 2, y + 34, line, fs, (255, 255, 255, int(255 * al)))


def slam(img, lt, dur, text):
    """A title slammed onto the frame: in big, lands, holds, punches out."""
    k = ease_out(lt / .12)
    out = ease_in_out((lt - (dur - .14)) / .14)
    s = 1.45 - .45 * k + .1 * out
    f = anton(int(196 * s))
    tw = text_w(text, f, 8 * s)
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    al = int(255 * min(1, lt / .05) * (1 - out))
    draw_text(d, (W - tw) / 2, H / 2 - f.size * .62, text, f, (255, 255, 255, al), 8 * s)
    img.alpha_composite(shadowed(layer, 18, .75, (0, 6)))


TICKER = ('MARKETS: RIVAL CHAINS SLASH PRICES ACROSS MIDTOWN   •   REAL ESTATE: RENTS UP 4% IN CHELSEA   •   '
          'TRAFFIC: DELAYS ON THE MANHATTAN BRIDGE   •   ')


def news(layer, lt):
    d = ImageDraw.Draw(layer)
    k = ease_out(lt / .25)
    d.rounded_rectangle((64, 60, 214, 116), 8, fill=(200, 30, 40, 235))
    d.text((139, 88), 'HCN', font=anton(40), fill=(255, 255, 255, 255), anchor='mm')
    d.rounded_rectangle((224, 70, 300, 106), 6, fill=(255, 255, 255, 230))
    d.text((262, 88), 'LIVE', font=font('Inter-ExtraBold', 20), fill=(200, 30, 40, 255), anchor='mm')
    y = H - 230
    x0 = int(-1500 * (1 - k))
    d.rectangle((x0 + 64, y, x0 + 64 + 260, y + 50), fill=(200, 30, 40, 245))
    d.text((x0 + 194, y + 25), 'BREAKING NEWS', font=font('Inter-ExtraBold', 24), fill=(255, 255, 255, 255), anchor='mm')
    d.rectangle((x0 + 64, y + 50, x0 + 1580, y + 128), fill=(250, 250, 250, 245))
    d.text((x0 + 92, y + 89), "WHO IS THE NEWCOMER BUYING UP HELL'S KITCHEN?", font=anton(46), fill=(20, 22, 28, 255), anchor='lm')
    d.rectangle((x0 + 64, y + 128, x0 + 1580, y + 162), fill=(20, 22, 28, 235))
    off = int(lt * 300) % int(text_w(TICKER, font('Inter-Bold', 20)))
    d.text((x0 + 84 - off, y + 145), TICKER * 3, font=font('Inter-Bold', 20), fill=(255, 255, 255, 230), anchor='lm')


def gradient_text(text, f, colors, track=0):
    w = int(text_w(text, f, track)) + 16
    asc, desc = f.getmetrics()
    h = asc + desc + 16
    mask = Image.new('L', (w, h), 0)
    draw_text(ImageDraw.Draw(mask), 8, 8, text, f, 255, track)
    g = np.zeros((h, w, 3), np.float32)
    n = len(colors) - 1
    for y in range(h):
        u = y / max(1, h - 1) * n
        i = min(int(u), n - 1); fr = u - i
        g[y] = np.array(colors[i]) * (1 - fr) + np.array(colors[i + 1]) * fr
    im = Image.fromarray(g.astype(np.uint8)).convert('RGBA')
    im.putalpha(mask)
    return im


_logo_bg = None


def logo(lt):
    """The name on black: slammed in gold, the tag and the credits after it."""
    global _logo_bg
    if _logo_bg is None:
        r = np.sqrt(((xx - W / 2) / 900) ** 2 + ((yy - H * .46) / 520) ** 2)
        _logo_bg = (np.clip(1 - r, 0, 1) ** 2)[..., None] * np.array([.16, .1, .05], np.float32)
    a = _logo_bg * (.6 + .4 * ease_out(lt / .4))
    img = to_img(a).convert('RGBA')
    k = ease_out(lt / .14)
    s = 1.3 - .3 * k
    gold = [(255, 246, 214), (255, 206, 110), (214, 140, 40)]
    for txt, y in (('HUSTLE', H * .3), ('CITY', H * .53)):
        t = gradient_text(txt, anton(int(290 * s)), gold, 10 * s)
        x = int((W - t.size[0]) / 2); yv = int(y - t.size[1] / 2)
        glow = Image.new('RGBA', img.size, (0, 0, 0, 0))
        glow.alpha_composite(t, (x, yv))
        img.alpha_composite(Image.eval(glow.filter(ImageFilter.GaussianBlur(28)), lambda v: int(v * .7)))
        img.alpha_composite(t, (x, yv))
    # the tag and the credits on a layer of their own (drawing straight onto the frame would ignore their alpha)
    layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    ta = int(255 * ease_out((lt - .7) / .5))
    ft = font('Inter-Bold', 34)
    tag = 'NOW ON WINDOWS  &  ANDROID'
    draw_text(d, (W - text_w(tag, ft, 10)) / 2, H * .74, tag, ft, (255, 255, 255, ta), 10)
    fc = font('Inter-Medium', 18)
    cred = 'Music: "Trap" and sound effects from Mixkit  ·  Cars: Quaternius (CC0)  ·  Materials: ambientCG (CC0)  ·  Type: Anton (OFL)'
    d.text(((W - fc.getlength(cred)) / 2, H - 64), cred, font=fc, fill=(255, 255, 255, int(ta * .5)))
    img.alpha_composite(layer)
    return np.asarray(img.convert('RGB'), np.float32) / 255


LB = 138  # 2.39:1


def letterbox(t):
    """How far the scope bars close: in for the cold open and the breakdown, snapping open on the drops."""
    if t < tb(DROP1): return 1.0
    if t < tb(BREAK): return 1 - ease_out((t - tb(DROP1)) / .12) if t < tb(DROP1) + .12 else 0.0
    if t < tb(DROP2): return ease_in_out((t - tb(BREAK)) / .35)
    if t < tb(NAME): return 1 - ease_out((t - tb(DROP2)) / .1) if t < tb(DROP2) + .1 else 0.0
    return 0.0


# ---------------------------------------------------------------- one frame
def frame_at(n):
    t = n / FPS
    if t >= tb(NAME):
        lt = t - tb(NAME)
        a = logo(lt)
        dx = decay(t, [(NAME, 22, .25)]) * noise(t, 3); dy = decay(t, [(NAME, 22, .25)]) * noise(t, 7)
        a = transform(a, 1 + decay(t, [(NAME, .08, .2)]), dx, dy)
        a = split(a, decay(t, [s for s in SPLIT if s[0] == NAME]))
        fl = decay(t, [f for f in FLASH if f[0] == NAME])
        if fl > 0: a = a + (1 - a) * min(1, fl)
        if t > DUR - .5: a *= 1 - ease_in_out((t - (DUR - .5)) / .45)
        return to_img(a)
    clip = clip_at(t)
    if clip is None or clip[2] is None: a = np.zeros((H, W, 3), np.float32)
    else:
        lt = t - tb(clip[0])
        # whip pans: the last frames of the outgoing shot and the first of the incoming smear past
        wk = 0.0; wd = 1
        for wb in WHIPS:
            dt = t - tb(wb)
            if -.09 <= dt < .09: wk = 1 - abs(dt) / .09; wd = -1 if dt < 0 else 1
        a = shot_image(clip, lt)
        if wk > 0: a = whip(a, wk, wd)
        # a slow push through every shot; punches on the beat; a hand on the camera in the breakdown and the getaway
        dur = tb(clip[1]) - tb(clip[0])
        zoom = 1.02 + .04 * lt / max(dur, .2) + decay(t, PUNCH, .12)
        hand = 5 if tb(DROP2) <= t < tb(NAME) else 2.5 if tb(BREAK) <= t < tb(DROP2) else 0
        sh = decay(t, SHAKE, .2) + hand
        a = transform(a, zoom, sh * noise(t, 1.3), sh * .7 * noise(t, 5.9))
        a = split(a, decay(t, SPLIT))
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    if tb(NEWS[0]) <= t < tb(NEWS[1]): news(layer, t - tb(NEWS[0]))
    for s0, s1, who, line in SUBS:
        if s0 <= t < s1: subtitle(layer, t - s0, s1 - s0, who, line)
    img = to_img(a).convert('RGBA')
    img.alpha_composite(shadowed(layer, 8, .7, (0, 2)))
    for b0, b1, text in SLAMS:
        if tb(b0) <= t < tb(b1): slam(img, t - tb(b0), tb(b1) - tb(b0), text)
    a = np.asarray(img.convert('RGB'), np.float32) / 255
    fl = decay(t, FLASH)
    if fl > 0: a = a + (1 - a) * min(1, fl)
    g = GRAIN[n % len(GRAIN)]
    a += np.repeat(np.repeat(g, 2, 0), 2, 1)[..., None] * .014
    lb = letterbox(t)
    if lb > 0:
        hgt = int(round(LB * lb))
        a[:hgt] = 0; a[H - hgt:] = 0
    return to_img(a)


def frame_bytes(n): return frame_at(n).tobytes()


# ---------------------------------------------------------------- sound
def load(path, sr=48000):
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-f', 'f32le', '-ac', '2', '-ar', str(sr), '-'], capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).reshape(-1, 2).copy()


def mix(path, sr=48000):
    music = load(os.path.join(AUDIO, TRACK[0]), sr)
    n = int(DUR * sr)
    out = np.zeros((n, 2), np.float32)
    # the track up to the bar line where it cuts to the name, with a hair of fade so it doesn't click
    cut = int(tb(NAME) * sr)
    m = music[:cut].copy()
    f = int(.02 * sr)
    m[-f:] *= np.linspace(1, 0, f)[:, None]
    # the music dips under the voice-over
    duck = np.zeros(n, np.float32)
    lines = []
    for name, at, who, line, kind, level in VO:
        p = os.path.join(AUDIO, name + '.mp3')
        if not os.path.exists(p): continue
        raw = _mono(p, sr)
        a0, a1 = speech_span(raw, sr)
        y = voice(raw, sr, kind)
        speech = y[int(a0 * sr):int(a1 * sr)]
        y = y * (10 ** ((level - 20 * np.log10(np.sqrt((speech ** 2).mean()) + 1e-9)) / 20))
        start = int((at - a0) * sr)
        lines.append((start, y))
        d = 1 - 10 ** (-DUCK[kind] / 20)
        i0 = max(0, int((at - .08) * sr)); i1 = min(n, int((at + a1 - a0 + .12) * sr))
        duck[i0:i1] = np.maximum(duck[i0:i1], d)
    # smooth the dips: quick down, slower back up
    k_dn = np.exp(-1 / (.05 * sr)); k_up = np.exp(-1 / (.25 * sr))
    env = np.empty_like(duck); e = 0.0
    for i in range(0, n, 64):
        target = duck[i]
        e = target + (e - target) * (k_dn ** 64 if target > e else k_up ** 64)
        env[i:i + 64] = e
    m = m * (1 - env[:len(m)])[:, None]
    out[:len(m)] += m
    for start, y in lines:
        a = max(0, start); b = min(n, start + len(y))
        if b > a: out[a:b] += y[a - start:b - start, None]
    for when, name, gain in SOUND:
        s = load(os.path.join(AUDIO, name + '.mp3'), sr) * (10 ** (gain / 20))
        start = int((tb(when) - SFX_PEAK[name]) * sr)
        a = max(0, start); b = min(n, start + len(s))
        if b > a: out[a:b] += s[a - start:b - start]
    with open(path, 'wb') as fo: fo.write(out.tobytes())


def voice(y, sr, kind):
    """A take treated for where it's heard: dry and close, through a TV, or over a police radio."""
    from scipy.signal import butter, sosfilt
    a0, a1 = speech_span(y, sr)
    y = sosfilt(butter(2, 90, 'highpass', fs=sr, output='sos'), y)
    if kind == 'tv':
        y = sosfilt(butter(2, [170, 7000], 'bandpass', fs=sr, output='sos'), y)
    elif kind == 'radio':
        y = sosfilt(butter(4, [380, 2900], 'bandpass', fs=sr, output='sos'), y)
        y = np.tanh(y * 6) / 3
        rng = np.random.default_rng(9)
        hiss = sosfilt(butter(2, [800, 5000], 'bandpass', fs=sr, output='sos'), rng.standard_normal(len(y))) * .012
        y = y + hiss
        # the squelch: a burst of static as the key goes down and again as it comes up
        for t in (max(0, a0 - .09), a1 + .02):
            i = int(t * sr); k = int(.06 * sr)
            if i + k < len(y): y[i:i + k] += rng.standard_normal(k) * .09 * np.exp(-np.arange(k) / (.02 * sr))
    return y.astype(np.float32)


def encode(out):
    pcm = out + '.f32'
    mix(pcm)
    cmd = ['ffmpeg', '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}', '-r', str(FPS), '-i', '-',
           '-f', 'f32le', '-ar', '48000', '-ac', '2', '-i', pcm, '-map', '0:v', '-map', '1:a',
           '-c:v', 'libx264', '-preset', 'slow', '-crf', '20', '-tune', 'film', '-pix_fmt', 'yuv420p',
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
        if t >= tb(NAME) or c is None or c[2] is None: continue
        p = position(c, t - tb(c[0]))
        for i in {int(p), min(int(p) + 1, recorded(c[2]) - 1)}: need.setdefault(c[2], set()).add(i)
    with open(path, 'w') as f:
        for shot in sorted(need): f.write(shot + ' ' + ','.join(map(str, sorted(need[shot]))) + '\n')
    print(sum(len(v) for v in need.values()), 'frames from', len(need), 'shots')


if __name__ == '__main__':
    if '--plan' in sys.argv:
        plan(sys.argv[sys.argv.index('--plan') + 1])
        sys.exit(0)
    if '--audio' in sys.argv:
        # just the sound, mastered as the film's, to check the mix
        wav = sys.argv[sys.argv.index('--audio') + 1]
        mix(wav + '.f32')
        subprocess.run(['ffmpeg', '-y', '-v', 'error', '-f', 'f32le', '-ar', '48000', '-ac', '2', '-i', wav + '.f32',
                        '-af', 'loudnorm=I=-14:TP=-1:LRA=11', '-ar', '48000', wav], check=True)
        os.remove(wav + '.f32')
        sys.exit(0)
    if '--stills' in sys.argv:
        k = sys.argv.index('--stills')
        stills(frame_at, sys.argv[k + 1], sys.argv[k + 2:])
        sys.exit(0)
    print(f'{DUR:.2f} s, {NF} frames')
    encode(OUT)
