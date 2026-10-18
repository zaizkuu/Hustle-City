"""Assembles the Hustle City trailer: cold open, development timelapse, gameplay scenes, montage, end card.

usage: TRAILER_WORK=/some/dir python3 tools/trailer/assemble.py [out.mp4]
Needs, from the repo: app/build/snapshots (./gradlew testDebugUnitTest), app/build/trailer
(HC_TRAILER=1 ./gradlew testDebugUnitTest --tests '*TrailerTest*'), app/build/glframes/city_tokyo_21/gpu.png
(GpuFrameDumpTest.cities + tools/glview/render.py) and $TRAILER_WORK/timelapse from tools/trailer/timelapse.sh.
"""
import os, sys, glob, subprocess
from PIL import Image, ImageDraw, ImageFont, ImageFilter

S = os.environ.get('TRAILER_WORK', '/tmp/hustle-trailer')
REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
HERE = os.path.dirname(os.path.abspath(__file__))
os.makedirs(f'{S}/trailer', exist_ok=True)
SNAP = f'{REPO}/app/build/snapshots'
SCENES = f'{REPO}/app/build/trailer'
TL = f'{S}/timelapse'
OUT = sys.argv[1] if len(sys.argv) > 1 else f'{S}/trailer/hustle_city_trailer.mp4'
W, H, FPS = 1280, 720, 30
BEAT = 60 / 110

FONT = '/usr/share/fonts/opentype/inter/InterDisplay-Black.otf'
if not os.path.exists(FONT):
    FONT = '/usr/share/fonts/opentype/inter/Inter-Black.otf'
FONT_B = '/usr/share/fonts/opentype/inter/Inter-Bold.otf'
FONT_M = '/usr/share/fonts/opentype/inter/Inter-Medium.otf'
def font(path, size):
    try: return ImageFont.truetype(path, size)
    except OSError: return ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', size)

YELLOW = (242, 201, 76); GREEN = (39, 174, 96); WHITE = (255, 255, 255); INK = (21, 23, 28)

SILENT = f'{S}/trailer/video_silent.mp4'
ff = subprocess.Popen(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}', '-r', str(FPS), '-i', '-',
                       '-c:v', 'libx264', '-preset', 'medium', '-crf', '19', '-pix_fmt', 'yuv420p', SILENT], stdin=subprocess.PIPE)
frames = 0
def emit(img):
    global frames
    ff.stdin.write(img.convert('RGB').tobytes()); frames += 1

def fit(img):
    """Scales and center-crops to the video frame."""
    img = img.convert('RGB')
    s = max(W / img.width, H / img.height)
    img = img.resize((int(img.width * s + .5), int(img.height * s + .5)), Image.LANCZOS)
    x = (img.width - W) // 2; y = (img.height - H) // 2
    return img.crop((x, y, x + W, y + H))

def kenburns(img, t, z0=1.0, z1=1.07, dx=0.0, dy=0.0):
    z = z0 + (z1 - z0) * t
    cw, ch = W / z, H / z
    cx = W / 2 + dx * t * W * .5; cy = H / 2 + dy * t * H * .5
    box = (cx - cw / 2, cy - ch / 2, cx + cw / 2, cy + ch / 2)
    return img.transform((W, H), Image.EXTENT, box, Image.BICUBIC)

def text_center(d, y, s, f, fill, shadow=True):
    w = d.textlength(s, font=f)
    if shadow: d.text(((W - w) / 2 + 2, y + 3), s, font=f, fill=(0, 0, 0, 160))
    d.text(((W - w) / 2, y), s, font=f, fill=fill)

def lower_third(img, title, sub=None, alpha=1.0, tag=None):
    over = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(over)
    # gradient band at the bottom
    for i in range(170):
        a = int(170 * (i / 170) ** 1.6 * alpha)
        d.line([(0, H - 170 + i), (W, H - 170 + i)], fill=(10, 12, 16, a))
    a = int(255 * alpha)
    d.rectangle([48, H - 112, 54, H - 50 if sub else H - 78], fill=YELLOW + (a,))
    d.text((70, H - 118), title, font=font(FONT_B, 40), fill=WHITE + (a,))
    if sub: d.text((72, H - 72), sub, font=font(FONT_M, 22), fill=(220, 224, 230, a))
    if tag:
        tf = font(FONT_B, 16)
        tw = d.textlength(tag, font=tf)
        d.rounded_rectangle([W - tw - 70, 34, W - 40, 66], 6, fill=(10, 12, 16, int(170 * alpha)))
        d.text((W - tw - 55, 40), tag, font=tf, fill=YELLOW + (a,))
    return Image.alpha_composite(img.convert('RGBA'), over)

def fade(img, k):
    """k=0 black, 1 full."""
    if k >= 1: return img
    return Image.blend(Image.new('RGB', (W, H), (0, 0, 0)), img.convert('RGB'), max(0, k))

def card(lines, t=1.0, bg=None):
    base = bg.copy() if bg is not None else Image.new('RGB', (W, H), INK)
    if bg is not None:
        base = Image.blend(base, Image.new('RGB', (W, H), (0, 0, 0)), .55)
    over = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(over)
    total_h = sum(sz + 14 for _, sz, _ in lines)
    y = (H - total_h) / 2
    for s, sz, col in lines:
        f = font(FONT if sz >= 60 else FONT_B, sz)
        w = d.textlength(s, font=f)
        a = int(255 * min(1, t))
        d.text(((W - w) / 2 + 3, y + 4), s, font=f, fill=(0, 0, 0, a // 2))
        d.text(((W - w) / 2, y), s, font=f, fill=col + (a,))
        y += sz + 14
    return Image.alpha_composite(base.convert('RGBA'), over)

def contain(img):
    """Whole picture visible: scaled to fit over a blurred, darkened fill of itself."""
    img = img.convert('RGB')
    if abs(img.width / img.height - W / H) < .05: return fit(img)
    bg = fit(img).filter(ImageFilter.GaussianBlur(18))
    bg = Image.blend(bg, Image.new('RGB', (W, H), (0, 0, 0)), .45)
    s = min(W / img.width, H / img.height)
    fg = img.resize((int(img.width * s + .5), int(img.height * s + .5)), Image.LANCZOS)
    bg.paste(fg, ((W - fg.width) // 2, (H - fg.height) // 2))
    return bg

def load(path):
    try: return contain(Image.open(path))
    except Exception: return None

def seq(name):
    return sorted(glob.glob(f'{SCENES}/{name}/*.jpg'))

# ---------------------------------------------------------------- 1. cold open (4 s)
for i in range(int(1.6 * FPS)):
    t = i / (1.6 * FPS)
    emit(card([("One phone. One idea.", 34, (200, 205, 212))], t=min(1, t * 2.5) if t < .7 else max(0, (1 - t) * 3.3)))
for i in range(int(2.4 * FPS)):
    t = i / (2.4 * FPS)
    img = card([("HUSTLE", 110, WHITE), ("CITY", 110, YELLOW), ("a development timelapse", 26, (200, 205, 212))], t=min(1, t * 3))
    emit(img)

# ---------------------------------------------------------------- 2. development timelapse (~2 s per milestone)
MILESTONES = [
    ('848fd8d', 'HOUR 0', 'The first playable city', ['midtown.png']),
    ('7ec5fe1', 'HOUR 5', 'Every business type and rivals that fight back', ['ui_rivals.png', 'ui_nightclub.png']),
    ('7f4d0f2', 'HOUR 5½', 'Weather, 3D people and the full furniture range', ['street_rain.png', 'street_people.png']),
    ('f7a78fc', 'HOUR 6', 'Driving, a minimap and GPS', ['ui_driving.png']),
    ('81e2846', 'HOUR 7', 'Buildings with personality', ['midtown.png']),
    ('de427e6', 'HOUR 10', 'Hand-drawn icons and product art', ['ui_bizhub_inventory.png', 'ui_hud.png']),
    ('5a200df', 'HOUR 11', 'Walk-in stores, carts and hand trucks', ['ui_store_shopping.png', 'ui_store.png']),
    ('61dfc39', 'HOUR 15', 'Smooth on mid-range phones', ['street_day.png']),
]
# the same block, version after version
flip = [load(f'{TL}/{c}/midtown.png') for c, *_ in MILESTONES] + [load(f'{SNAP}/midtown.png')]
flip = [f for f in flip if f is not None]
fl = int(.42 * FPS)
for k, img in enumerate(flip):
    for i in range(fl):
        fr = kenburns(img, (k * fl + i) / (len(flip) * fl), 1.0, 1.12)
        fr = lower_third(fr, 'Same block, twenty hours apart', alpha=1.0, tag=f'BUILD {k + 1} OF {len(flip)}')
        emit(fr)
shots = []
for c, hour, cap, names in MILESTONES:
    img = None
    for name in names:
        img = load(f'{TL}/{c}/{name}')
        if img is not None: break
    if img is not None: shots.append((img, hour, cap))
now = load(f'{REPO}/app/build/glframes/city_tokyo_21/gpu.png') or load(f'{SNAP}/ui_newgame.png')
if now is not None: shots.append((now, 'HOUR 20', 'Five cities, each with its own streets and buildings'))
per = int(2.0 * FPS); xf = int(.35 * FPS)
prev = None
for k, (img, hour, cap) in enumerate(shots):
    for i in range(per):
        t = i / per
        fr = kenburns(img, t, 1.0, 1.06, dx=.04 if k % 2 else -.04)
        fr = lower_third(fr, cap, sub=None, alpha=min(1, i / 8), tag=f'DEVELOPMENT TIMELAPSE · {hour}')
        if prev is not None and i < xf:
            fr = Image.blend(prev.convert('RGB'), fr.convert('RGB'), i / xf)
        emit(fr)
    prev = fr

# ---------------------------------------------------------------- 3. gameplay scenes
def scene(name, title, sub, fade_in=True):
    files = seq(name)
    if not files: return None
    last = None
    for i, f in enumerate(files):
        img = Image.open(f).convert('RGB')
        if img.size != (W, H): img = fit(img)
        a = min(1, i / 10) if i < len(files) - 15 else max(0, (len(files) - 1 - i) / 15)
        img = lower_third(img, title, sub, alpha=a)
        if fade_in and i < 6: img = fade(img, i / 6)
        emit(img); last = img
    return last

bg = shots[-1][0] if shots else None
for i in range(int(1.4 * FPS)):
    emit(card([("FIVE CITIES", 92, WHITE), ("each with its own streets, buildings and traffic", 24, (210, 214, 220))], t=min(1, i / 8)))
scene('01_nyc', 'New York', 'Manhattan at golden hour')
scene('02_manila', 'Manila', 'Tin roofs, colonial balconies and jeepneys')
scene('03_tokyo', 'Tokyo', 'Kabukicho in the rain · cars keep left')
scene('04_seoul', 'Seoul', 'Hanok roofs at sunset')
scene('05_classic_snow', 'Hustle City', 'The original grid, under snow')
for i in range(int(1.4 * FPS)):
    emit(card([("BUILD AN EMPIRE", 84, YELLOW), ("rent, furnish, stock and staff your stores", 24, (210, 214, 220))], t=min(1, i / 8)))
scene('07_store', 'Walk in, shop, restock', 'Every shelf, every customer')
scene('06_skyline', 'Downtown at night', 'From one food truck to a skyline')

# ---------------------------------------------------------------- 4. montage on the beat
MONTAGE = [
    ('ui_bizhub_insight.png', 'BizHub insights'), ('ui_realty.png', 'Rent anywhere in the city'),
    ('ui_store_shopping.png', 'Shop for furniture'), ('ui_driving.png', 'Drive across town'),
    ('ui_maps_route.png', 'GPS routes'), ('ui_finance.png', 'Loans and finances'),
    ('ui_wholesale.png', 'Wholesale contracts'), ('ui_warehouse.png', 'Warehouses and logistics'),
    ('interior_gym.png', 'Gyms, cafés, nightclubs…'), ('arch_seoul_hanok_night.png', 'Day and night'),
    ('street_rain.png', 'Weather'), ('ui_rivals.png', 'Rivals that fight back'),
]
beat_frames = int(BEAT * 2 * FPS)
for name, cap in MONTAGE:
    img = load(f'{SNAP}/{name}')
    if img is None: continue
    for i in range(beat_frames):
        t = i / beat_frames
        fr = kenburns(img, t, 1.04, 1.0)
        fr = lower_third(fr, cap, alpha=1.0)
        if i < 3: fr = Image.blend(Image.new('RGB', (W, H), WHITE), fr.convert('RGB'), i / 3)
        emit(fr)

# ---------------------------------------------------------------- 5. end card (5 s)
end_bg = load(f'{SNAP}/city_tokyo_wide.png') or load(f'{SNAP}/midtown.png')
if end_bg is not None: end_bg = end_bg.filter(ImageFilter.GaussianBlur(6))
for i in range(int(5 * FPS)):
    t = i / (5 * FPS)
    img = card([("HUSTLE", 104, WHITE), ("CITY", 104, YELLOW), ("Start with nothing. Build an empire.", 28, WHITE),
                ("v1.6 · for Android", 22, (200, 205, 212))], t=min(1, i / 12), bg=end_bg)
    emit(fade(img, min(1, (1 - t) * 4)))

ff.stdin.close(); ff.wait()
dur = frames / FPS
# a soundtrack cut to the video's length, then both into the final file
subprocess.run([sys.executable, f'{HERE}/music.py', f'{S}/trailer/music.wav', f'{dur:.2f}'], check=True)
subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', SILENT, '-i', f'{S}/trailer/music.wav', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k',
                '-shortest', '-movflags', '+faststart', OUT], check=True)
print(f'{OUT}: {frames} frames, {dur:.1f} s')
