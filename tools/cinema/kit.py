"""What the trailer cuts share: grading, the shots' frames, typography, the logo and the encoder.

A cut sets where its frames are with use(), builds each frame with these pieces and hands frame_at to encode().
"""
import bisect, math, os, subprocess
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H, FPS = 1920, 1080, 24

INTER = '/usr/share/fonts/opentype/inter/'
_fonts = {}


def font(name, size):
    k = (name, size)
    if k not in _fonts: _fonts[k] = ImageFont.truetype(INTER + name + '.otf', size)
    return _fonts[k]


ORANGE = (242, 153, 74)
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


_dirs = {}


def use(frames, cinema):
    """Where render.mjs left the shots' frames and where the recordings (with their overlays) are."""
    _dirs['frames'] = frames; _dirs['cinema'] = cinema


_cache = {}


def src(shot, i):
    """Rendered frame [i] of a shot, or the nearest one rendered (a cut may render only the frames it uses)."""
    d = os.path.join(_dirs['frames'], shot)
    if shot not in _cache: _cache[shot] = sorted(int(n[:-4]) for n in os.listdir(d) if n.endswith('.png'))
    nums = _cache[shot]
    k = bisect.bisect_left(nums, i)
    if k == len(nums) or (k > 0 and i - nums[k - 1] < nums[k] - i): k -= 1
    return Image.open(os.path.join(d, f'{nums[k]:04d}.png'))


_ov = {}


def overlay(shot, i):
    """The 2D layer recorded with a shot: the HUD of a gameplay shot, or the rain and snow of a weather shot."""
    if shot not in _ov: _ov[shot] = sorted(n for n in os.listdir(os.path.join(_dirs['cinema'], shot)) if n.isdigit())
    names = _ov[shot]
    p = os.path.join(_dirs['cinema'], shot, names[max(0, min(len(names) - 1, i))], 'overlay.png')
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


def title(layer, t, sub='BUILD  ·  RUN  ·  RULE THE CITY', hold=1.82):
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
    out = 1 - ease_in_out((t - hold) / .18)
    if out < 1: tmp.putalpha(tmp.split()[3].point(lambda v: int(v * out)))
    layer.alpha_composite(tmp, (int((W - tmp.size[0]) / 2), int(H / 2 - tmp.size[1] / 2 - 10)))
    d = ImageDraw.Draw(layer)
    fs = font('InterDisplay-SemiBold', 30)
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


def logo(layer, t, tag='BUILD YOUR EMPIRE'):
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
    a2 = ease_out((t - 2.4) / .6) * out
    draw_text(d, (W - text_w(tag, fs, 18)) / 2, H / 2 + 222, tag, fs, (255, 255, 255, int(240 * a2)), 18)


def footer(img, t, foot='ANDROID  ·  VERSION 1.7 BETA'):
    """The platform line, set in the lower letterbox bar (drawn after the bars)."""
    a = ease_out((t - 3.6) / .6) * (1 - ease_in_out((t - 6.0) / .8))
    if a <= 0: return
    fm = font('Inter-Medium', 22)
    draw_text(ImageDraw.Draw(img), (W - text_w(foot, fm, 6)) / 2, H - 66, foot, fm, (205, 209, 216, int(230 * a)), 6)


def make_wash():
    m = np.clip(1 - np.sqrt(((xx - 0) / (W * .62)) ** 2 + ((yy - H) / (H * .62)) ** 2), 0, 1) ** 1.6 * .62
    return Image.fromarray(np.dstack([np.zeros((H, W, 3)), m[..., None] * 255]).astype(np.uint8), 'RGBA')


WASH = make_wash()
_center = np.clip(1 - np.sqrt(((xx - W / 2) / (W * .62)) ** 2 + ((yy - H / 2 - 40) / (H * .72)) ** 2), 0, 1) ** 1.1


def center_wash(k):
    """Darkens the middle of the frame behind centred titles."""
    return Image.fromarray(np.dstack([np.zeros((H, W, 3)), _center[..., None] * 255 * k]).astype(np.uint8), 'RGBA')


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


# ---------------------------------------------------------------- output
def stills(frame_at, out, picks):
    """Single frames as JPG to check the look: picks are frame numbers, or seconds when they have a point."""
    os.makedirs(out, exist_ok=True)
    for s in picks:
        n = int(round(float(s) * FPS)) if '.' in s else int(s)
        frame_at(n).save(os.path.join(out, f'{n:04d}.jpg'), quality=90)


def encode(frame_at, nf, score, out):
    cmd = ['ffmpeg', '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}', '-r', str(FPS), '-i', '-',
           '-i', score, '-map', '0:v', '-map', '1:a', '-c:v', 'libx264', '-preset', 'slow', '-crf', '16', '-tune', 'film',
           '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '256k', '-movflags', '+faststart', '-shortest', out]
    p = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    for n in range(nf):
        p.stdin.write(frame_at(n).tobytes())
        if n % 48 == 0: print(f'{n / FPS:5.1f}s', flush=True)
    p.stdin.close()
    p.wait()
    print(out)
