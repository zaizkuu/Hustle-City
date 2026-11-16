"""Cuts the trailer together in Blender's sequencer and writes the film:

  blender -b --python tools/blender/trailer/edit.py -- <frames dir> <score.wav> <out.mp4> [--draft] [--range first last] [--stills f,f,...]

The rendered frames go on the bottom track shot by shot, each under a soft glow (lamps and neon bloom), then a
letterbox with a vignette, the captions, and the score. --draft leaves holes where frames are missing instead of
stopping, for looking at a cut before the render is done.
"""
import json
import os
import sys

import bpy

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
FRAMES, SCORE, OUT = argv[0], argv[1], argv[2]
DRAFT = "--draft" in argv
RANGE = None
if "--range" in argv:
    k = argv.index("--range")
    RANGE = (int(argv[k + 1]), int(argv[k + 2]))
FPS = 24
W, H = 1280, 720
END = 1 + 68 * FPS

SHOTS = [["01_arrival", 73, 192], ["02_steps", 193, 264], ["03_the_shop", 265, 396], ["04_open", 397, 468], ["05_counter", 469, 528], ["06_avenue", 529, 588],
         ["07_one_block", 589, 660], ["08_rise", 661, 756], ["p1_manila", 757, 804], ["p2_tokyo", 805, 852], ["p3_seoul", 853, 900], ["09_night_ride", 901, 996],
         ["10_club_a", 997, 1044], ["10_club_b", 1045, 1092], ["11_roof", 1093, 1236], ["12_sign", 1237, 1404], ["13_next", 1429, 1536]]


def T(sec):
    return int(round(1 + sec * FPS))


scn = bpy.data.scenes.new("HC Edit")
if bpy.context.window is not None:
    bpy.context.window.scene = scn
scn.render.resolution_x, scn.render.resolution_y = W, H
scn.render.resolution_percentage = 100
scn.render.fps = FPS
scn.frame_start, scn.frame_end = 1, END - 1
scn.view_settings.view_transform = 'Standard'
se = scn.sequence_editor_create()
strips = se.strips if hasattr(se, "strips") else se.sequences


def new_effect(name, kind, channel, f0, f1, src=None):
    kw = dict(name=name, type=kind, channel=channel, frame_start=f0)
    if src is None:
        try:
            return strips.new_effect(length=f1 - f0 + 1, **kw)
        except TypeError:
            return strips.new_effect(frame_end=f1 + 1, **kw)
    for key in ("input1", "seq1"):
        try:
            return strips.new_effect(**kw, **{key: src})
        except TypeError:
            continue
    raise RuntimeError("could not make a " + kind)


def fade(strip, f0, f1, n_in=0, n_out=0):
    """Keys a strip's opacity up over its first frames and down over its last."""
    if n_in:
        strip.blend_alpha = 0.0
        strip.keyframe_insert("blend_alpha", frame=f0)
        strip.blend_alpha = 1.0
        strip.keyframe_insert("blend_alpha", frame=f0 + n_in)
    if n_out:
        strip.blend_alpha = 1.0
        strip.keyframe_insert("blend_alpha", frame=f1 - n_out)
        strip.blend_alpha = 0.0
        strip.keyframe_insert("blend_alpha", frame=f1)


# ---------------------------------------------------------------- picture
black = new_effect("black", 'COLOR', 1, 1, END - 1)
black.color = (0, 0, 0)
missing = []
for name, f0, f1 in SHOTS:
    files = ["{:04d}.png".format(f) for f in range(f0, f1 + 1)]
    have = [f for f in files if os.path.exists(os.path.join(FRAMES, f))]
    if len(have) < len(files):
        missing.append((name, len(files) - len(have)))
        if not DRAFT or not have:
            continue
        # a draft holds the last frame it has across the gap
        last = have[0]
        fixed = []
        for f in files:
            if f in have:
                last = f
            fixed.append(last)
        files = fixed
    try:
        st = strips.new_image(name=name, filepath=os.path.join(FRAMES, files[0]), channel=2, frame_start=f0, fit_method='FIT')
    except TypeError:
        st = strips.new_image(name=name, filepath=os.path.join(FRAMES, files[0]), channel=2, frame_start=f0)
    for f in files[1:]:
        st.elements.append(f)
    gl = new_effect(name + ".glow", 'GLOW', 3, f0, f1, st)
    gl.threshold = .82
    gl.clamp = 1.0
    gl.boost_factor = .55
    gl.blur_radius = 7
    gl.quality = 4
    if name == "01_arrival":
        fade(gl, f0, f1, n_in=30)
    if name == "12_sign":
        fade(gl, f0, f1, n_out=18)
    if name == "03_the_shop" or name == "08_rise":
        pass
if missing and not DRAFT:
    raise SystemExit("frames missing: " + json.dumps(missing))

# ---------------------------------------------------------------- letterbox and vignette, painted once into an overlay
import numpy as np

bar = int(round((H - W / 2.39) / 2))
yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
r = np.sqrt(((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2)
alpha = np.clip((r - .78) / .75, 0, 1) ** 1.6 * .55
alpha[:bar, :] = 1.0
alpha[H - bar:, :] = 1.0
px = np.zeros((H, W, 4), np.float32)
px[..., 3] = alpha
img = bpy.data.images.new("hc_letterbox", W, H, alpha=True)
img.pixels.foreach_set(px.ravel())
over_path = os.path.join(os.path.dirname(OUT), "letterbox.png")
img.filepath_raw = over_path
img.file_format = 'PNG'
img.save()
try:
    lb = strips.new_image(name="letterbox", filepath=over_path, channel=4, frame_start=1, fit_method='FIT')
except TypeError:
    lb = strips.new_image(name="letterbox", filepath=over_path, channel=4, frame_start=1)
try:
    lb.frame_final_duration = END - 1
except Exception:
    lb.frame_final_end = END
lb.blend_type = 'ALPHA_OVER'

# ---------------------------------------------------------------- captions
font = None
for cand in ("C:/Windows/Fonts/bahnschrift.ttf", "C:/Windows/Fonts/segoeuib.ttf"):
    if os.path.exists(cand):
        try:
            font = bpy.data.fonts.load(cand)
            break
        except Exception:
            pass
heavy = None
if os.path.exists("C:/Windows/Fonts/impact.ttf"):
    try:
        heavy = bpy.data.fonts.load("C:/Windows/Fonts/impact.ttf")
    except Exception:
        pass


def spaced(text, n=1):
    return (" " * n).join(text)


def caption(text, t0, t1, y=.5, size=58, x=.5, color=(1, 1, 1, 1), channel=6, big=False, track=1, fin=8, fout=8, align='CENTER'):
    f0, f1 = T(t0), T(t1) - 1
    st = new_effect("cap." + text[:10], 'TEXT', channel, f0, f1)
    st.text = spaced(text, track) if track else text
    st.font_size = size
    st.color = color
    st.location = (x, y)
    f = heavy if (big and heavy is not None) else font
    if f is not None:
        st.font = f
    for attr, val in (("alignment_x", align), ("anchor_x", align), ("align_x", align), ("anchor_y", 'CENTER'), ("align_y", 'CENTER'), ("wrap_width", 0.0),
                      ("use_shadow", True), ("shadow_color", (0, 0, 0, .75)), ("shadow_blur", .5), ("shadow_offset", .04), ("use_outline", False)):
        try:
            setattr(st, attr, val)
        except Exception:
            pass
    st.blend_type = 'ALPHA_OVER'
    fade(st, f0, f1, fin, fout)
    return st


caption("EVERY EMPIRE", .5, 2.9, y=.56, size=54, track=2, fin=18, fout=10)
caption("STARTS WITH ONE DOOR.", 1.3, 2.9, y=.45, size=30, track=2, fin=18, fout=10, color=(1, .72, .42, 1), channel=7)
caption("ONE SHOP.", 15.0, 16.45, y=.5, size=92, big=True, track=1, fin=5, fout=4)
caption("ONE BLOCK.", 26.0, 27.45, y=.5, size=92, big=True, track=1, fin=5, fout=4)
caption("MANILA", 31.65, 33.45, y=.2, x=.09, size=34, track=2, align='LEFT', fin=5, fout=4)
caption("TOKYO", 33.65, 35.45, y=.2, x=.09, size=34, track=2, align='LEFT', fin=5, fout=4)
caption("SEOUL", 35.65, 37.45, y=.2, x=.09, size=34, track=2, align='LEFT', fin=5, fout=4)
caption("FIVE CITIES.", 37.7, 39.3, y=.5, size=92, big=True, track=1, fin=5, fout=6)
caption("BUILD IT FROM NOTHING.", 48.4, 51.3, y=.2, size=34, track=2, fin=14, fout=10)
caption("HUSTLE CITY", 64.1, 67.7, y=.54, size=118, big=True, track=1, fin=4, fout=20, color=(1, .62, .22, 1))
caption("BUSINESS SIM  -  ANDROID BETA", 64.6, 67.7, y=.37, size=26, track=1, fin=14, fout=20, channel=7)

# ---------------------------------------------------------------- sound
snd = strips.new_sound(name="score", filepath=SCORE, channel=10, frame_start=1)

# ---------------------------------------------------------------- out
r = scn.render
if "--stills" in argv:
    # a look at single frames of the cut, as PNGs beside the film
    r.image_settings.file_format = 'PNG'
    for f in argv[argv.index("--stills") + 1].split(","):
        scn.frame_set(int(f))
        r.filepath = OUT.replace(".mp4", "") + "_{:04d}.png".format(int(f))
        bpy.ops.render.render(write_still=True, scene=scn.name)
    raise SystemExit(0)
try:
    r.image_settings.media_type = 'VIDEO'       # Blender 5: say it is a film before naming the container
except Exception:
    pass
r.image_settings.file_format = 'FFMPEG'
r.ffmpeg.format = 'MPEG4'
r.ffmpeg.codec = 'H264'
r.ffmpeg.constant_rate_factor = 'HIGH'
r.ffmpeg.ffmpeg_preset = 'GOOD'
r.ffmpeg.gopsize = 24
r.ffmpeg.audio_codec = 'AAC'
r.ffmpeg.audio_bitrate = 192
r.ffmpeg.audio_mixrate = 44100 if hasattr(r.ffmpeg, "audio_mixrate") else 44100
r.filepath = OUT
r.use_file_extension = False
if RANGE:
    scn.frame_start, scn.frame_end = RANGE
bpy.ops.render.render(animation=True, scene=scn.name)
print("WROTE", OUT, "missing", missing, flush=True)
