"""Records what the Blender viewport itself shows for some of the trailer's shots: solid shading, every mesh's
wireframe drawn over it, the people's skeletons showing through. No lights, no sky, no render: the scene as it is
worked on. For the breakdown that sets this beside the finished frames. Needs kit.py, trailer/set.py (HC_TR_NOBUILD)
and trailer/cast.py in front; runs in the Blender you have open (it borrows its 3D viewport).

Set HC_VIEW_OUT to the folder and HC_VIEW_RANGES to a list of (first frame, last frame).
"""
import os


def viewport_pass(out_dir, ranges, still=None):
    scn = tr_scene()
    win = bpy.context.window_manager.windows[0]
    win.scene = scn
    area = next(a for a in win.screen.areas if a.type == 'VIEW_3D')
    region = next(r for r in area.regions if r.type == 'WINDOW')
    space = area.spaces.active
    sh = space.shading
    sh.type = 'SOLID'
    sh.light = 'STUDIO'
    sh.color_type = 'MATERIAL'
    sh.show_shadows = False
    sh.show_cavity = False
    sh.show_object_outline = False
    sh.show_xray = False
    sh.background_type = 'VIEWPORT'
    sh.background_color = (.2, .21, .23)
    ov = space.overlay
    ov.show_overlays = True
    ov.show_wireframes = True
    ov.wireframe_threshold = 1.0
    ov.wireframe_opacity = .5
    for attr, val in (("show_floor", False), ("show_axis_x", False), ("show_axis_y", False), ("show_cursor", False), ("show_extras", False),
                      ("show_relationship_lines", False), ("show_bones", True), ("show_text", False), ("show_outline_selected", False),
                      ("show_object_origins", False), ("show_stats", False), ("show_annotation", False), ("show_motion_paths", False)):
        try:
            setattr(ov, attr, val)
        except Exception:
            pass
    space.show_gizmo = False
    space.region_3d.view_perspective = 'CAMERA'
    for o in scn.objects:
        try:
            o.select_set(False)
        except Exception:
            pass
    os.makedirs(out_dir, exist_ok=True)
    keep = (scn.render.filepath, scn.frame_start, scn.frame_end, scn.render.use_overwrite, scn.frame_current)
    scn.render.image_settings.file_format = 'PNG'
    scn.render.use_overwrite = True
    done = 0
    with bpy.context.temp_override(window=win, area=area, region=region, scene=scn):
        if still is not None:
            scn.frame_set(still)
            scn.render.filepath = os.path.join(out_dir, "still_{:04d}.png".format(still))
            bpy.ops.render.opengl(write_still=True, view_context=True)
            done = 1
        for f0, f1 in ranges:
            scn.frame_start, scn.frame_end = f0, f1
            scn.render.filepath = os.path.join(out_dir, "")
            bpy.ops.render.opengl(animation=True, view_context=True)
            done += f1 - f0 + 1
    scn.render.filepath, scn.frame_start, scn.frame_end, scn.render.use_overwrite = keep[:4]
    scn.frame_set(keep[4])
    return done


result = {"frames": viewport_pass(HC_VIEW_OUT, globals().get("HC_VIEW_RANGES", []), globals().get("HC_VIEW_STILL"))}
