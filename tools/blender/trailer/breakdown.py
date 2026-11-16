"""The breakdown: the Blender viewport against the finished frame, shot by shot.

  blender -b --python tools/blender/trailer/breakdown.py -- <viewport dir> <frames dir> <score.wav> <out.mp4>

Each shot starts as the viewport shows it (solid shading, wireframes, nothing lit), a line wipes across, and what is
left behind it is the rendered film. Also writes side-by-side stills next to the film (breakdown_<shot>.png).
"""
import os
import sys

import bpy
import numpy as np

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
VIEW, FRAMES, SCORE, OUT = argv[0], argv[1], argv[2], argv[3]
FPS = 24
W, H = 1280, 720
SHOTS = [("04_open", 397, 468, "THE FIRST SHOP"), ("06_avenue", 529, 588, "THE AVENUE"), ("p1_manila", 757, 804, "MANILA"), ("09_night_ride", 901, 996, "NIGHT, RAIN"),
         ("12_sign", 1309, 1404, "THE SIGN")]
INTRO, OUTRO = 60, 84

scn = bpy.data.scenes.new("HC Breakdown")
if bpy.context.window is not None:
    bpy.context.window.scene = scn
scn.render.resolution_x, scn.render.resolution_y = W, H
scn.render.resolution_percentage = 100
scn.render.fps = FPS
scn.view_settings.view_transform = 'Standard'
se = scn.sequence_editor_create()
strips = se.strips if hasattr(se, "strips") else se.sequences


def effect(name, kind, channel, f0, f1=None, a=None, b=None):
    kw = dict(name=name, type=kind, channel=channel, frame_start=f0)
    if a is None:
        try:
            return strips.new_effect(length=f1 - f0 + 1, **kw)
        except TypeError:
            return strips.new_effect(frame_end=f1 + 1, **kw)
    for ka, kb in (("input1", "input2"), ("seq1", "seq2")):
        try:
            if b is None:
                return strips.new_effect(**kw, **{ka: a})
            return strips.new_effect(**kw, **{ka: a, kb: b})
        except TypeError:
            continue
    raise RuntimeError("could not make a " + kind)


def image_strip(name, folder, first, last, channel, at):
    files = ["{:04d}.png".format(f) for f in range(first, last + 1)]
    lacking = [f for f in files if not os.path.exists(os.path.join(folder, f))]
    if lacking:
        raise SystemExit("missing {:d} frames of {:s} in {:s}".format(len(lacking), name, folder))
    try:
        st = strips.new_image(name=name, filepath=os.path.join(folder, files[0]), channel=channel, frame_start=at, fit_method='FIT')
    except TypeError:
        st = strips.new_image(name=name, filepath=os.path.join(folder, files[0]), channel=channel, frame_start=at)
    for f in files[1:]:
        st.elements.append(f)
    return st


font = None
for cand in ("C:/Windows/Fonts/bahnschrift.ttf", "C:/Windows/Fonts/segoeuib.ttf"):
    if os.path.exists(cand):
        font = bpy.data.fonts.load(cand)
        break
heavy = bpy.data.fonts.load("C:/Windows/Fonts/impact.ttf") if os.path.exists("C:/Windows/Fonts/impact.ttf") else None


def caption(text, f0, f1, x, y, size, channel, color=(1, 1, 1, 1), align='CENTER', big=False, fin=6, fout=6, box=False):
    st = effect("cap." + text[:10], 'TEXT', channel, f0, f1)
    st.text = text
    st.font_size = size
    st.color = color
    st.location = (x, y)
    f = heavy if (big and heavy is not None) else font
    if f is not None:
        st.font = f
    for attr, val in (("alignment_x", align), ("anchor_x", align), ("align_x", align), ("anchor_y", 'CENTER'), ("align_y", 'CENTER'), ("wrap_width", 0.0),
                      ("use_shadow", True), ("shadow_color", (0, 0, 0, .8)), ("use_box", box), ("box_color", (0, 0, 0, .55)), ("box_margin", .012)):
        try:
            setattr(st, attr, val)
        except Exception:
            pass
    st.blend_type = 'ALPHA_OVER'
    if fin:
        st.blend_alpha = 0.0
        st.keyframe_insert("blend_alpha", frame=f0)
        st.blend_alpha = 1.0
        st.keyframe_insert("blend_alpha", frame=f0 + fin)
    if fout:
        st.blend_alpha = 1.0
        st.keyframe_insert("blend_alpha", frame=f1 - fout)
        st.blend_alpha = 0.0
        st.keyframe_insert("blend_alpha", frame=f1)
    return st


# ---------------------------------------------------------------- opening card
caption("HUSTLE CITY", 1, INTRO, .5, .58, 96, 5, color=(1, .62, .22, 1), big=True, fin=10, fout=8)
caption("T R A I L E R   B R E A K D O W N", 1, INTRO, .5, .43, 28, 6, fin=14, fout=8)
caption("the Blender viewport, then the finished frame", 8, INTRO, .5, .34, 22, 7, color=(.75, .78, .82, 1), fin=14, fout=8)

at = INTRO + 1
stills = []
for name, first, last, title in SHOTS:
    n = last - first + 1
    raw = image_strip(name + ".viewport", VIEW, first, last, 2, at)
    raw.color_multiply = 1.7                       # the viewport has no lights: lift it so the wires read
    fin = image_strip(name + ".final", FRAMES, first, last, 3, at)
    wipe = effect(name + ".wipe", 'WIPE', 4, at, a=raw, b=fin)
    wipe.transition_type = 'SINGLE'
    wipe.direction = 'IN'
    wipe.angle = 0
    wipe.blur_width = 0.0
    wipe.use_default_fade = False
    w0, w1 = at + int(n * .3), at + int(n * .68)
    for f, v in ((at, 0.0), (w0, 0.0), (w1, 1.0)):
        wipe.effect_fader = v
        wipe.keyframe_insert("effect_fader", frame=f)
    # a bright line riding the edge of the wipe
    line = effect(name + ".line", 'COLOR', 5, w0, w1)
    line.color = (1, .72, .3)
    line.blend_type = 'ALPHA_OVER'
    line.transform.scale_x = 4 / W
    for f, x in ((w0, -W / 2), (w1, W / 2)):
        line.transform.offset_x = x
        line.transform.keyframe_insert("offset_x", frame=f)
    caption("BLENDER VIEWPORT   solid + wireframe", at, w1 - 2, .035, .08, 22, 6, align='LEFT', fin=4, fout=6, box=True)
    caption("FINAL RENDER   EEVEE", w0 + 8, at + n - 1, .965, .08, 22, 7, align='RIGHT', color=(1, .78, .45, 1), fin=6, fout=4, box=True)
    caption(title, at, at + n - 1, .035, .92, 26, 8, align='LEFT', fin=4, fout=4)
    stills.append((name, first + n // 2))
    at += n

# ---------------------------------------------------------------- closing card
caption("MODELLED, RIGGED, ANIMATED AND RENDERED IN BLENDER 5.2", at, at + OUTRO - 1, .5, .6, 26, 5, fin=10, fout=14)
caption("212 models     15-bone rig     22 animation clips     1,440 frames", at + 6, at + OUTRO - 1, .5, .48, 22, 6, color=(.75, .78, .82, 1), fin=12, fout=14)
caption("every model is a script in tools/blender", at + 12, at + OUTRO - 1, .5, .39, 20, 7, color=(1, .72, .42, 1), fin=12, fout=14)
END = at + OUTRO - 1
black = effect("black", 'COLOR', 1, 1, END)
black.color = (0, 0, 0)

snd = strips.new_sound(name="score", filepath=SCORE, channel=10, frame_start=1 - int(16.5 * FPS) + 12)
snd.volume = .8
for f, v in ((END - 40, .8), (END, 0.0)):
    snd.volume = v
    snd.keyframe_insert("volume", frame=f)

# ---------------------------------------------------------------- stills, side by side
def load(path):
    im = bpy.data.images.load(path)
    px = np.empty(im.size[0] * im.size[1] * 4, np.float32)
    im.pixels.foreach_get(px)
    return px.reshape(im.size[1], im.size[0], 4)


for name, f in stills:
    a = load(os.path.join(VIEW, "{:04d}.png".format(f)))
    b = load(os.path.join(FRAMES, "{:04d}.png".format(f)))
    a = a.copy()
    a[..., :3] = np.clip(a[..., :3] * 1.7, 0, 1)
    gap = np.ones((a.shape[0], 12, 4), np.float32)
    gap[..., :3] = (1, .72, .3)
    both = np.concatenate([a, gap, b], axis=1)
    out = bpy.data.images.new("bd_" + name, both.shape[1], both.shape[0], alpha=False)
    out.pixels.foreach_set(both.ravel())
    out.filepath_raw = os.path.join(os.path.dirname(OUT), "breakdown_" + name + ".png")
    out.file_format = 'PNG'
    out.save()

scn.frame_start, scn.frame_end = 1, END
r = scn.render
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
r.filepath = OUT
r.use_file_extension = False
bpy.ops.render.render(animation=True, scene=scn.name)
print("WROTE", OUT, flush=True)
