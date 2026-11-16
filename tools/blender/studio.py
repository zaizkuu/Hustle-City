"""Preview studio: lights, backdrop and a camera that frames whatever it's asked to shoot. Needs kit.py in front."""


def studio_setup():
    scn = bpy.context.scene
    for name in ("Cube", "Light", "Camera"):
        ob = bpy.data.objects.get(name)
        if ob is not None and ob.users_collection and ob.users_collection[0].name == "Collection":
            bpy.data.objects.remove(ob, do_unlink=True)
    old = bpy.data.collections.get("Collection")
    if old is not None and not old.objects and not old.children:
        bpy.data.collections.remove(old)

    st = collection("Studio")
    sun = bpy.data.objects.get("HC Sun")
    if sun is None:
        sun = bpy.data.objects.new("HC Sun", bpy.data.lights.new("HC Sun", 'SUN'))
        st.objects.link(sun)
    sun.data.energy = 2.6
    sun.data.angle = math.radians(6)
    sun.data.color = (1, .96, .9)
    sun.rotation_euler = Euler((math.radians(52), 0, math.radians(-38)))

    cam = bpy.data.objects.get("HC Camera")
    if cam is None:
        cam = bpy.data.objects.new("HC Camera", bpy.data.cameras.new("HC Camera"))
        st.objects.link(cam)
    cam.data.type = 'ORTHO'
    cam.data.clip_start = .1
    cam.data.clip_end = 5000
    scn.camera = cam

    ground = bpy.data.objects.get("HC Backdrop")
    if ground is None:
        me = bpy.data.meshes.new("HC Backdrop")
        s = 3000
        me.from_pydata([(-s, -s, 0), (s, -s, 0), (s, s, 0), (-s, s, 0)], [], [(0, 1, 2, 3)])
        ground = bpy.data.objects.new("HC Backdrop", me)
        st.objects.link(ground)
        me.materials.append(M(0xC9D1DA, "PLAIN"))
    ground.location = (0, 0, -.002)
    ground.hide_select = True
    ground.hide_viewport = True

    w = scn.world or bpy.data.worlds.new("HC World")
    scn.world = w
    try:
        w.use_nodes = True
    except Exception:
        pass
    bg = next((n for n in w.node_tree.nodes if n.type == 'BACKGROUND'), None)
    if bg is not None:
        bg.inputs[0].default_value = (.82, .88, 1, 1)
        bg.inputs[1].default_value = .55

    scn.render.engine = 'BLENDER_EEVEE'
    scn.view_settings.view_transform = 'Standard'
    try:
        scn.view_settings.look = 'None'
    except Exception:
        pass
    scn.render.image_settings.file_format = 'PNG'
    scn.render.film_transparent = False
    for attr, val in (("use_shadows", True), ("use_raytracing", False), ("taa_render_samples", 32), ("use_gtao", True)):
        try:
            setattr(scn.eevee, attr, val)
        except Exception:
            pass


def shoot(objs, path, res=(1600, 900), az=32.0, el=30.0, margin=1.1):
    """Renders just these objects to a PNG. az: degrees the camera swings to the right of straight-on; el: height."""
    scn = bpy.context.scene
    bpy.context.view_layer.update()
    cam = bpy.data.objects["HC Camera"]
    keep = {o.name for o in objs} | {"HC Sun", "HC Camera", "HC Backdrop"}
    hidden = []
    for o in scn.objects:
        if o.name not in keep and not o.hide_render:
            o.hide_render = True
            hidden.append(o)
    a = math.radians(az)
    e = math.radians(el)
    to_cam = Vector((math.sin(a) * math.cos(e), -math.cos(a) * math.cos(e), math.sin(e)))
    q = (-to_cam).to_track_quat('-Z', 'Y')
    cam.rotation_euler = q.to_euler()
    right = q @ Vector((1, 0, 0))
    up = q @ Vector((0, 1, 0))
    pts = [o.matrix_world @ Vector(c) for o in objs for c in o.bound_box]
    us = [p.dot(right) for p in pts]
    vs = [p.dot(up) for p in pts]
    ds = [p.dot(to_cam) for p in pts]
    cu = (min(us) + max(us)) / 2
    cv = (min(vs) + max(vs)) / 2
    w = max(us) - min(us)
    h = max(vs) - min(vs)
    aspect = res[0] / res[1]
    cam.data.ortho_scale = max(w, h * aspect) * margin if aspect >= 1 else max(w / aspect, h) * margin
    cam.location = right * cu + up * cv + to_cam * (max(ds) + 200)
    scn.render.resolution_x, scn.render.resolution_y = res
    scn.render.resolution_percentage = 100
    scn.render.filepath = path
    bpy.context.view_layer.update()
    bpy.ops.render.render(write_still=True)
    for o in hidden:
        o.hide_render = False


def shoot_category(name, path, res=(1800, 1000), az=28.0, el=30.0):
    c = bpy.data.collections[name]
    shoot(list(c.objects), path, res, az, el)
