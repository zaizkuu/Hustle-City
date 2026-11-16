"""Renders the trailer's frames in a Blender of its own, so the one you are working in stays free:

  blender -b art/trailer/hustle_city_trailer.blend --python tools/blender/trailer/render.py -- <frames dir> [shot ...] [--engine workbench] [--step n]

Each shot is rendered as an animation into <frames dir>/0001.png and so on; frames already there are left alone, so
a stopped render picks up where it was. --engine workbench renders what the viewport's solid mode shows instead
(for the breakdown). --step n renders every nth frame (for a quick look at the whole cut).
"""
import json
import os
import sys
import time

import bpy

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
out = argv[0] if argv else "//frames"
engine = "eevee"
step = 1
names = []
i = 1
while i < len(argv):
    if argv[i] == "--engine":
        engine = argv[i + 1]
        i += 2
    elif argv[i] == "--step":
        step = int(argv[i + 1])
        i += 2
    else:
        names.append(argv[i])
        i += 1

scn = bpy.data.scenes["HC Trailer"]
if bpy.context.window is not None:
    bpy.context.window.scene = scn
shots = json.loads(scn["tr_shots"])
os.makedirs(out, exist_ok=True)
scn.render.use_overwrite = False
scn.render.use_placeholder = False
scn.render.image_settings.file_format = 'PNG'
scn.render.use_file_extension = True
scn.render.filepath = os.path.join(out, "")
scn.frame_step = step
if engine == "workbench":
    scn.render.engine = 'BLENDER_WORKBENCH'
    sh = scn.display.shading
    sh.light = 'STUDIO'
    sh.color_type = 'SINGLE'
    sh.single_color = (.72, .72, .72)
    sh.show_shadows = False
    sh.show_cavity = True
    sh.cavity_type = 'BOTH'
    sh.show_object_outline = True
    sh.object_outline_color = (.08, .08, .08)
    scn.display.render_aa = '8'
    scn.view_settings.view_transform = 'Standard'
    scn.view_settings.look = 'None'
    scn.view_settings.exposure = 0
    # the exposure is keyed for the lit film; here it must stay put
    if scn.animation_data is not None:
        scn.animation_data_clear()
    scn.world.node_tree.nodes["horizon"].outputs[0].default_value = (.22, .22, .24, 1)

# after dark the sun is a faint moon: its shadows cost most of a frame's time and show as nothing
NO_SHADOWS = ("01_arrival", "02_steps", "09_night_ride", "10_club_a", "10_club_b", "11_roof", "12_sign", "p3_seoul")

t0 = time.time()
done = 0
for name, f0, f1 in shots:
    if names and name not in names:
        continue
    scn.frame_start, scn.frame_end = f0, f1
    if engine != "workbench":
        scn.eevee.use_shadows = name not in NO_SHADOWS
    t = time.time()
    bpy.ops.render.render(animation=True, scene=scn.name)
    n = len(range(f0, f1 + 1, step))
    done += n
    print("SHOT {:s} {:d}-{:d}: {:.1f} s, {:.2f} s a frame".format(name, f0, f1, time.time() - t, (time.time() - t) / max(1, n)), flush=True)
print("DONE {:d} frames in {:.1f} min".format(done, (time.time() - t0) / 60), flush=True)
