"""A contact sheet of a finished film, to look it over without a player:

  blender -b --python tools/blender/trailer/contact.py -- <film.mp4> <out.png> [every n frames] [columns]

Reads the film back through Blender's own decoder (the same one that wrote it), so a sheet that looks right means
the file plays. Also prints the film's length, frame rate and whether it carries sound.
"""
import os
import sys

import bpy
import numpy as np

argv = sys.argv[sys.argv.index("--") + 1:]
FILM, OUT = argv[0], argv[1]
EVERY = int(argv[2]) if len(argv) > 2 else 48
COLS = int(argv[3]) if len(argv) > 3 else 6
TW, TH = 384, 216

scn = bpy.data.scenes.new("HC Contact")
if bpy.context.window is not None:
    bpy.context.window.scene = scn
scn.render.resolution_x, scn.render.resolution_y = TW, TH
scn.render.resolution_percentage = 100
scn.view_settings.view_transform = 'Standard'
se = scn.sequence_editor_create()
strips = se.strips if hasattr(se, "strips") else se.sequences
try:
    mv = strips.new_movie(name="film", filepath=FILM, channel=1, frame_start=1, fit_method='FIT')
except TypeError:
    mv = strips.new_movie(name="film", filepath=FILM, channel=1, frame_start=1)
n = mv.frame_final_duration
fps = None
try:
    clip = bpy.data.movieclips.load(FILM)
    fps = clip.fps
except Exception:
    pass
sound = False
try:
    sd = strips.new_sound(name="sound", filepath=FILM, channel=2, frame_start=1)
    sound = sd.frame_final_duration > 0
except Exception:
    sound = False
print("FILM {:s}: {:d} frames, {} fps, sound {}".format(os.path.basename(FILM), n, fps, sound), flush=True)

scn.render.image_settings.file_format = 'PNG'
tmp = os.path.join(os.path.dirname(OUT), "_contact_tmp.png")
frames = list(range(1, n + 1, EVERY))
rows = (len(frames) + COLS - 1) // COLS
sheet = np.zeros((rows * (TH + 4), COLS * (TW + 4), 4), np.float32)
sheet[..., 3] = 1
for i, f in enumerate(frames):
    scn.frame_start = scn.frame_end = f
    scn.frame_set(f)
    scn.render.filepath = tmp
    bpy.ops.render.render(write_still=True, scene=scn.name)
    im = bpy.data.images.load(tmp)
    px = np.empty(TW * TH * 4, np.float32)
    im.pixels.foreach_get(px)
    bpy.data.images.remove(im)
    px = px.reshape(TH, TW, 4)
    r, c = i // COLS, i % COLS
    y0 = (rows - 1 - r) * (TH + 4)
    sheet[y0:y0 + TH, c * (TW + 4):c * (TW + 4) + TW] = px
img = bpy.data.images.new("contact", sheet.shape[1], sheet.shape[0], alpha=False)
img.pixels.foreach_set(sheet.ravel())
img.filepath_raw = OUT
img.file_format = 'PNG'
img.save()
os.remove(tmp)
print("SHEET", OUT, len(frames), "frames", flush=True)
