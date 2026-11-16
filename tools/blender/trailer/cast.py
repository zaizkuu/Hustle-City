"""Putting people, traffic, signs and cameras into the trailer. Needs kit.py, people.py, rig.py, trailer/set.py and
trailer/light.py in front.

Everything a shot brings in is scoped to that shot's frames: it is hidden (render and viewport) outside them, so
nothing from one shot is left standing in the road of another.
"""

FPS_LIB = 30.0           # the clips were keyed at 30 frames a second; the trailer runs at 24
FONT_PATH = "C:/Windows/Fonts/bahnschrift.ttf"

PAINT = {"sedan": "PAINT_2F80ED", "taxi": "PAINT_F2C94C", "pickup": "PAINT_C0392B", "van": "PAINT_E9EDF2", "sports_car": "PAINT_D0392B",
         "suv": "PAINT_2E3238", "box_truck": "PAINT_E9EDF2", "bus": "PAINT_2F80ED", "jeepney": "PAINT_EB5757", "kei_car": "PAINT_6FCF97",
         "modern_jeepney": "PAINT_F7FAFC"}


def T(sec):
    """The frame a time on the trailer's clock falls on."""
    return int(round(1 + sec * FPS_TR))


def id_fcurves(owner):
    """Every F-curve animating a datablock, wherever this Blender keeps them."""
    ad = getattr(owner, "animation_data", None)
    if ad is None or ad.action is None:
        return []
    out = []
    try:
        for layer in ad.action.layers:
            for st in layer.strips:
                for bag in st.channelbags:
                    out += list(bag.fcurves)
    except Exception:
        pass
    if not out:
        try:
            out = list(ad.action.fcurves)
        except Exception:
            pass
    return out


def key_kind(owner, frame, kind):
    """Makes the keys a datablock has at `frame` hold (CONSTANT), run straight (LINEAR) or ease (BEZIER) to the next."""
    for fc in id_fcurves(owner):
        for kp in fc.keyframe_points:
            if abs(kp.co.x - frame) < .01:
                kp.interpolation = kind


def _interp(kind):
    prefs = bpy.context.preferences.edit
    keep = prefs.keyframe_new_interpolation_type
    prefs.keyframe_new_interpolation_type = kind
    return keep


def scope(ob, f0, f1):
    """Shows an object (and what hangs on it) only from frame f0 to f1."""
    for o in [ob] + list(ob.children_recursive):
        for f, hidden in ((f0 - 1, True), (f0, False), (f1 + 1, True)):
            o.hide_render = hidden
            o.hide_viewport = hidden
            o.keyframe_insert("hide_render", frame=f)
            o.keyframe_insert("hide_viewport", frame=f)


def face(dx, dy):
    """Degrees to turn something whose front is -Y so that it looks along (dx, dy)."""
    return math.degrees(math.atan2(dx, -dy))


def move(ob, keys, turn=True, z=None, ease=False):
    """Walks or drives an object through (frame, x, y) points in straight lines, turning to face the way it goes."""
    keep = _interp('BEZIER' if ease else 'LINEAR')
    try:
        for i, k in enumerate(keys):
            f, x, y = k[0], k[1], k[2]
            ob.location = (x, y, ob.location.z if z is None else z)
            ob.keyframe_insert("location", frame=f)
            key_kind(ob, f, 'BEZIER' if ease else 'LINEAR')
            if turn and len(keys) > 1:
                j = min(i, len(keys) - 2)
                dx, dy = keys[j + 1][1] - keys[j][1], keys[j + 1][2] - keys[j][2]
                if abs(dx) + abs(dy) < 1e-4 and j > 0:
                    dx, dy = keys[j][1] - keys[j - 1][1], keys[j][2] - keys[j - 1][2]
                if abs(dx) + abs(dy) > 1e-4:
                    rz = math.radians(face(dx, dy))
                    # the shorter way round from wherever it was facing
                    cur = ob.rotation_euler.z
                    while rz - cur > math.pi:
                        rz -= 2 * math.pi
                    while rz - cur < -math.pi:
                        rz += 2 * math.pi
                    if i > 0 and i < len(keys) - 1:
                        # hold the old heading up to the corner, then turn over a few frames
                        ob.keyframe_insert("rotation_euler", frame=f - 1)
                        ob.rotation_euler.z = rz
                        ob.keyframe_insert("rotation_euler", frame=f + 6)
                    else:
                        ob.rotation_euler.z = rz
                        ob.keyframe_insert("rotation_euler", frame=f)
    finally:
        _interp(keep)


def play(arm, clips):
    """Queues clips on a person: (clip, first frame, last frame[, speed[, shift in seconds]]) in order; each blends into the next."""
    ad = arm.animation_data_create()
    for i, c in enumerate(clips):
        name, f0, f1 = c[0], c[1], c[2]
        speed = c[3] if len(c) > 3 else 1.0
        shift = c[4] if len(c) > 4 else 0.0
        act = bpy.data.actions["hc_" + name]
        n = float(act["hc_frames"]) * FPS_LIB / float(act["hc_fps"])          # the clip's length in its own frames
        scale = (FPS_TR / FPS_LIB) / speed
        tr = ad.nla_tracks.new()
        tr.name = name
        start = f0 - int(round(shift * FPS_TR)) - (10 if i > 0 else 0)
        st = tr.strips.new(name, max(-2000, start), act)
        try:
            if st.action_slot is None and len(act.slots):
                st.action_slot = act.slots[0]
        except Exception:
            pass
        st.action_frame_start = 1
        st.action_frame_end = 1 + n
        st.scale = scale
        st.repeat = max(1.0, (f1 - start + 12) / (n * scale))
        st.blend_type = 'REPLACE'
        st.extrapolation = 'NOTHING' if i > 0 else 'HOLD'
        if i > 0:
            st.blend_in = 10
            st.use_auto_blend = False
    return arm


def person(name, look, x, y, rz=0.0, z=None, clips=None, path=None, f0=None, f1=None, s=None):
    """Somebody in the trailer: standing at (x, y) or walking a path of (frame, x, y), playing clips, seen from f0 to f1."""
    arm = spawn_person("tr." + name, look, tr_coll("Cast"), loc=(x, y, KERB if z is None else z), rz=rz, scale=s)
    if clips:
        play(arm, clips)
    if path:
        move(arm, path, z=KERB if z is None else z)
    if f0 is not None:
        scope(arm, f0, f1)
    return arm


def hold_thing(arm, model, bone, offset, rot=(0, 0, 0), s=1.0):
    """Hangs a library model on one of a person's bones: offset from the joint, in the figure's own axes."""
    src = bpy.data.objects[model]
    ob = bpy.data.objects.new(arm.name + "." + model, src.data)
    tr_coll("Cast").objects.link(ob)
    ob.parent = arm
    ob.parent_type = 'BONE'
    ob.parent_bone = bone
    b = arm.data.bones[bone]
    local = Matrix.Translation(Vector(b.head_local) + Vector(offset)) @ Euler([math.radians(v) for v in rot]).to_matrix().to_4x4() @ Matrix.Scale(s, 4)
    ob.matrix_parent_inverse = (b.matrix_local @ Matrix.Translation((0, b.length, 0))).inverted() @ local
    return ob


def follow(arm, model, offset, rz=0.0, s=1.0):
    """Something that goes where a person goes without being in their hands: a hand truck pushed in front."""
    src = bpy.data.objects[model]
    ob = bpy.data.objects.new(arm.name + "." + model, src.data)
    tr_coll("Cast").objects.link(ob)
    ob.parent = arm
    ob.location = offset
    ob.rotation_euler = (0, 0, math.radians(rz))
    ob.scale = (s, s, s)
    return ob


def vehicle(model, x, y, rz=0.0, color=None, path=None, f0=None, f1=None, coll="Traffic", z=0.0):
    src = bpy.data.objects[model]
    ob = bpy.data.objects.new("tr." + model, src.data)
    tr_coll(coll).objects.link(ob)
    ob.location = (x, y, z)
    ob.rotation_euler = (0, 0, math.radians(rz))
    if color is not None and model in PAINT:
        for slot in ob.material_slots:
            if slot.material is not None and slot.material.name == PAINT[model]:
                slot.link = 'OBJECT'
                slot.material = M(color, "PAINT")
    if path:
        move(ob, path, z=z)
    if f0 is not None:
        scope(ob, f0, f1)
    return ob


def font(name="bahnschrift"):
    for f in bpy.data.fonts:
        if f.filepath and name in f.filepath.lower():
            return f
    try:
        return bpy.data.fonts.load("C:/Windows/Fonts/{:s}.ttf".format(name))
    except Exception:
        return None


def lettering(text, x, y, z, size, mat, rz=0.0, depth=.04, coll="Extras", align='CENTER', tilt=90.0, spacing=1.0, face_name="bahnschrift"):
    """Raised letters standing upright, read from the -Y side when rz is 0."""
    cu = bpy.data.curves.new("tr.text", 'FONT')
    cu.body = text
    cu.size = size
    cu.extrude = depth
    cu.align_x = align
    cu.align_y = 'CENTER'
    cu.space_character = spacing
    f = font(face_name)
    if f is not None:
        cu.font = f
    ob = bpy.data.objects.new("tr.text." + text[:12], cu)
    tr_coll(coll).objects.link(ob)
    ob.location = (x, y, z)
    ob.rotation_euler = (math.radians(tilt), 0, math.radians(rz))
    cu.materials.append(mat)
    return ob


def slab(name, x0, y0, z0, x1, y1, z1, mat, coll="Extras", bevel=0.0):
    a = Asset("tr." + name)
    a.box(x0, y0, z0, x1, y1, z1, mat, bevel=bevel)
    return a.finish(tr_coll(coll))


def shot(name, f0, f1, look, keys, lens=35.0, look_to=None, fstop=None, focus=None, ease=False, look_keys=None):
    """One shot: a camera moving through (frame offset, position, where it looks[, lens]) keys, cut to at f0.

    look/look_to: the light at the start and, if it changes, at the end of the shot. focus: the distance kept sharp.
    """
    scn = tr_scene()
    cam = bpy.data.objects.new("TR Cam " + name, bpy.data.cameras.new("TR Cam " + name))
    tr_coll("Cameras").objects.link(cam)
    cam.data.clip_start = .1
    cam.data.clip_end = 4000
    cam.data.sensor_width = 36
    cam.data.lens = lens
    target = bpy.data.objects.new("TR Aim " + name, None)
    tr_coll("Cameras").objects.link(target)
    target.empty_display_size = .5
    con = cam.constraints.new('TRACK_TO')
    con.target = target
    con.track_axis = 'TRACK_NEGATIVE_Z'
    con.up_axis = 'UP_Y'
    keep = _interp('BEZIER' if ease else 'LINEAR')
    try:
        for k in keys:
            f = f0 + k[0]
            cam.location = k[1]
            target.location = k[2]
            cam.keyframe_insert("location", frame=f)
            target.keyframe_insert("location", frame=f)
            key_kind(cam, f, 'BEZIER' if ease else 'LINEAR')
            key_kind(target, f, 'BEZIER' if ease else 'LINEAR')
            if len(k) > 3:
                cam.data.lens = k[3]
                cam.data.keyframe_insert("lens", frame=f)
                key_kind(cam.data, f, 'BEZIER' if ease else 'LINEAR')
    finally:
        _interp(keep)
    if fstop:
        cam.data.dof.use_dof = True
        cam.data.dof.aperture_fstop = fstop
        if focus is None:
            cam.data.dof.focus_object = target
        else:
            cam.data.dof.focus_distance = focus
    m = scn.timeline_markers.new(name, frame=f0)
    m.camera = cam
    set_look(look, f0, hold=look_to is None and not look_keys)
    if look_to:
        set_look(look_to, f1, hold=True)
    for off, lk in (look_keys or []):
        set_look(lk, f0 + off, hold=False)
    SHOTS.append((name, f0, f1))
    return cam


def rain(parent, f0, f1, box=(-14.0, 62.0, -15.0, 15.0), height=11.0, n=3200, seed=3):
    """Rain falling round whatever it is parented to: streaks in a box (x0, x1, y0, y1), looping as they fall."""
    m = bpy.data.materials.get("tr.rain")
    if m is None:
        m = bpy.data.materials.new("tr.rain")
        m.use_nodes = True
        b = next(nd for nd in m.node_tree.nodes if nd.type == 'BSDF_PRINCIPLED')
        b.inputs["Base Color"].default_value = (.7, .8, 1, 1)
        b.inputs["Emission Color"].default_value = (.75, .85, 1, 1)
        b.inputs["Emission Strength"].default_value = 1.4
        b.inputs["Alpha"].default_value = .3
        try:
            m.surface_render_method = 'BLENDED'
        except Exception:
            pass
    r = random.Random(seed)
    verts, faces = [], []
    for k in range(n):
        x, y, z = r.uniform(box[0], box[1]), r.uniform(box[2], box[3]), r.uniform(0, height)
        ln = r.uniform(.35, .6)
        ang = r.uniform(0, math.pi)
        dx, dy = math.cos(ang) * .009, math.sin(ang) * .009
        # the same streak twice, a box-height apart, so the loop has no seam; it leans with the wind
        for zz in (z, z + height):
            i = len(verts)
            verts += [(x - dx, y - dy, zz), (x + dx, y + dy, zz), (x + dx + .06, y + dy, zz + ln), (x - dx + .06, y - dy, zz + ln)]
            faces.append((i, i + 1, i + 2, i + 3))
    me = bpy.data.meshes.new("tr.rain")
    me.from_pydata(verts, [], faces)
    me.materials.append(m)
    ob = bpy.data.objects.new("tr.rain", me)
    tr_coll("Extras").objects.link(ob)
    ob.visible_shadow = False
    # world space, following the parent along the road only (it must not turn or tip with it)
    period = 26
    keep = _interp('LINEAR')
    f = f0 - period
    while f <= f1 + period:
        for ff, z in ((f, 0.0), (f + period - 1, -height * (period - 1) / period)):
            ob.location = (0, 0, z)
            ob.keyframe_insert("location", index=2, frame=ff)
            key_kind(ob, ff, 'LINEAR')
        f += period
    _interp(keep)
    if parent is not None:
        con = ob.constraints.new('COPY_LOCATION')
        con.target = parent
        con.use_z = False
    scope(ob, f0, f1)
    return ob


def petals(x0, y0, x1, y1, f0, f1, n=260, top=15.0, drift=(-.55, .12, -.6), color=0xF7B7CE, seed=9):
    """Blossom on the air: small petals through a box, drifting down and along for the length of a shot."""
    r = random.Random(seed)
    secs = (f1 - f0) / FPS_TR
    verts, faces = [], []
    for k in range(n):
        c = Vector((r.uniform(x0, x1) - drift[0] * secs * r.random(), r.uniform(y0, y1), r.uniform(.3, top)))
        u = Vector((r.uniform(-1, 1), r.uniform(-1, 1), r.uniform(-1, 1))).normalized() * .045
        v = u.cross(Vector((r.uniform(-1, 1), r.uniform(-1, 1), r.uniform(-1, 1)))).normalized() * .03
        i = len(verts)
        verts += [tuple(c - u - v), tuple(c + u - v), tuple(c + u + v), tuple(c - u + v)]
        faces.append((i, i + 1, i + 2, i + 3))
    me = bpy.data.meshes.new("tr.petals")
    me.from_pydata(verts, [], faces)
    me.materials.append(M(color, "FOLIAGE"))
    ob = bpy.data.objects.new("tr.petals", me)
    tr_coll("Extras").objects.link(ob)
    ob.visible_shadow = False
    keep = _interp('LINEAR')
    for f, k in ((f0 - 2, 0.0), (f1 + 2, secs)):
        ob.location = (drift[0] * k, drift[1] * k, drift[2] * k)
        ob.keyframe_insert("location", frame=f)
        key_kind(ob, f, 'LINEAR')
    _interp(keep)
    scope(ob, f0, f1)
    return ob


def wet_road(f0, f1):
    """Rain on the asphalt: it shines under the lamps from f0 to f1 and is dry again after."""
    b = next(nd for nd in ASPHALT.node_tree.nodes if nd.type == 'BSDF_PRINCIPLED')
    for f, v in ((1, .95), (f0, .22), (f1 + 1, .95)):
        b.inputs["Roughness"].default_value = v
        b.inputs["Roughness"].keyframe_insert("default_value", frame=f)
        key_kind(ASPHALT.node_tree, f, 'CONSTANT')
    b.inputs["Roughness"].default_value = .95


SHOTS = []


def clear_shots():
    del SHOTS[:]
    for c in ("Cast", "Traffic", "Cameras", "Extras", "Postcards", "Rooms"):
        tr_clear(c)
    scn = tr_scene()
    for m in list(scn.timeline_markers):
        scn.timeline_markers.remove(m)
    clear_light_keys()
    if ASPHALT.node_tree.animation_data is not None:
        ASPHALT.node_tree.animation_data_clear()
    for a in list(bpy.data.actions):
        if a.users == 0 and not a.name.startswith("hc_"):
            bpy.data.actions.remove(a)


def at_shot(name, off=0):
    """Moves the scene to a frame of a shot, with its camera, for looking at."""
    scn = tr_scene()
    for n, f0, f1 in SHOTS:
        if n == name:
            scn.frame_set(f0 + off)
            scn.camera = bpy.data.objects["TR Cam " + name]
            return f0 + off
    raise KeyError(name)


def shot_still(name, off, path, res=(960, 540), samples=8):
    at_shot(name, off)
    scn = tr_scene()
    keep = (scn.render.resolution_x, scn.render.resolution_y, scn.eevee.taa_render_samples, scn.render.filepath)
    scn.render.resolution_x, scn.render.resolution_y = res
    scn.eevee.taa_render_samples = samples
    scn.render.filepath = path
    bpy.ops.render.render(scene=scn.name, write_still=True)
    scn.render.resolution_x, scn.render.resolution_y, scn.eevee.taa_render_samples, scn.render.filepath = keep
