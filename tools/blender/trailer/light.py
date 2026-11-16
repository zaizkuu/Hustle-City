"""The trailer's light and air: a sky with clouds that goes from blue hour to day to night, a sun to match, haze
that thickens with distance, windows and lamps that come on after dark, and the render settings. Needs kit.py and
trailer/set.py in front.

The haze is made after the render, from its depth (the compositor mixes each pixel toward the air's color by how far
away it is): this machine's integrated graphics can't do EEVEE's volumetric fog, and this costs next to nothing.

One number, the night factor (0 by day, 1 at night), lives in the node group "HC Night"; every window and lamp
material reads it, so keying that one value switches the city's lights on.
"""

RES = (1280, 720)
FPS_TR = 24


def night_group():
    ng = bpy.data.node_groups.get("HC Night")
    if ng is None:
        ng = bpy.data.node_groups.new("HC Night", "ShaderNodeTree")
        ng.interface.new_socket(name="Factor", in_out='OUTPUT', socket_type='NodeSocketFloat')
        val = ng.nodes.new("ShaderNodeValue")
        val.name = "night"
        out = ng.nodes.new("NodeGroupOutput")
        ng.links.new(val.outputs[0], out.inputs[0])
    return ng


def night_value():
    return night_group().nodes["night"].outputs[0]


def _bsdf(m):
    return next((n for n in m.node_tree.nodes if n.type == 'BSDF_PRINCIPLED'), None)


def windows_light_up(m, cell=(3.1, 3.1, 3.3), share=.5, strength=1.9):
    """Glass that is dark by day and, at night, lit pane by pane: about `share` of the rooms have someone in."""
    nt = m.node_tree
    if nt.nodes.get("hc_night") is not None:
        return
    b = _bsdf(m)
    grp = nt.nodes.new("ShaderNodeGroup")
    grp.name = "hc_night"
    grp.node_tree = night_group()
    geo = nt.nodes.new("ShaderNodeNewGeometry")
    div = nt.nodes.new("ShaderNodeVectorMath")
    div.operation = 'DIVIDE'
    div.inputs[1].default_value = cell
    flo = nt.nodes.new("ShaderNodeVectorMath")
    flo.operation = 'FLOOR'
    noise = nt.nodes.new("ShaderNodeTexWhiteNoise")
    noise.noise_dimensions = '3D'
    gt = nt.nodes.new("ShaderNodeMath")
    gt.operation = 'GREATER_THAN'
    gt.inputs[1].default_value = 1 - share
    mul = nt.nodes.new("ShaderNodeMath")
    mul.operation = 'MULTIPLY'
    mul2 = nt.nodes.new("ShaderNodeMath")
    mul2.operation = 'MULTIPLY'
    mul2.inputs[1].default_value = strength
    # warm rooms and the odd cold one
    mix = nt.nodes.new("ShaderNodeMix")
    mix.data_type = 'RGBA'
    mix.inputs[6].default_value = (1.0, .66, .32, 1)
    mix.inputs[7].default_value = (.62, .82, 1.0, 1)
    sep = nt.nodes.new("ShaderNodeSeparateColor")
    gt2 = nt.nodes.new("ShaderNodeMath")
    gt2.operation = 'GREATER_THAN'
    gt2.inputs[1].default_value = .8
    nt.links.new(geo.outputs["Position"], div.inputs[0])
    nt.links.new(div.outputs[0], flo.inputs[0])
    nt.links.new(flo.outputs[0], noise.inputs["Vector"])
    nt.links.new(noise.outputs["Value"], gt.inputs[0])
    nt.links.new(gt.outputs[0], mul.inputs[0])
    nt.links.new(grp.outputs[0], mul.inputs[1])
    nt.links.new(mul.outputs[0], mul2.inputs[0])
    nt.links.new(noise.outputs["Color"], sep.inputs[0])
    nt.links.new(sep.outputs[0], gt2.inputs[0])
    nt.links.new(gt2.outputs[0], mix.inputs[0])
    nt.links.new(mix.outputs[2], b.inputs["Emission Color"])
    nt.links.new(mul2.outputs[0], b.inputs["Emission Strength"])


def lamp_lights_up(m, strength, floor=.0):
    """A lamp or a sign that is off by day and on at night."""
    nt = m.node_tree
    if nt.nodes.get("hc_night") is not None:
        return
    b = _bsdf(m)
    grp = nt.nodes.new("ShaderNodeGroup")
    grp.name = "hc_night"
    grp.node_tree = night_group()
    mul = nt.nodes.new("ShaderNodeMath")
    mul.operation = 'MULTIPLY_ADD'
    mul.inputs[1].default_value = strength - floor
    mul.inputs[2].default_value = floor
    nt.links.new(grp.outputs[0], mul.inputs[0])
    nt.links.new(mul.outputs[0], b.inputs["Emission Strength"])


def city_lights():
    """Wires the night factor into the buildings' glass, the street lamps and the screens."""
    done = []
    for m in bpy.data.materials:
        if not m.node_tree or _bsdf(m) is None:
            continue
        kind = m.get("hc_kind", "")
        if kind == "GLASS" and m.name in ("GLASS_3C5266", "GLASS_4F7FA6", "GLASS_9FC4DD", "GLASS_8EC5E8"):
            share = {"GLASS_3C5266": .42, "GLASS_4F7FA6": .5, "GLASS_9FC4DD": .6, "GLASS_8EC5E8": .5}[m.name]
            windows_light_up(m, share=share)
            done.append(m.name)
        elif m.name in ("EMIT_FFE3A0", "EMIT_FFF3C4"):
            lamp_lights_up(m, 9.0, .15)
            done.append(m.name)
    return done


# ---------------------------------------------------------------------------------------------- sky and sun

def world_setup():
    scn = tr_scene()
    w = bpy.data.worlds.get("HC Trailer Sky")
    if w is None:
        w = bpy.data.worlds.new("HC Trailer Sky")
        w.use_nodes = True
        nt = w.node_tree
        for n in list(nt.nodes):
            nt.nodes.remove(n)
        out = nt.nodes.new("ShaderNodeOutputWorld")
        bg = nt.nodes.new("ShaderNodeBackground")
        bg.name = "bg"
        tc = nt.nodes.new("ShaderNodeTexCoord")
        sep = nt.nodes.new("ShaderNodeSeparateXYZ")
        ramp = nt.nodes.new("ShaderNodeMapRange")
        ramp.inputs[1].default_value = -.02
        ramp.inputs[2].default_value = .55
        hor = nt.nodes.new("ShaderNodeRGB")
        hor.name = "horizon"
        zen = nt.nodes.new("ShaderNodeRGB")
        zen.name = "zenith"
        mix = nt.nodes.new("ShaderNodeMix")
        mix.data_type = 'RGBA'
        # a few stars, only when it is dark
        scale = nt.nodes.new("ShaderNodeVectorMath")
        scale.operation = 'SCALE'
        scale.inputs[3].default_value = 900
        flo = nt.nodes.new("ShaderNodeVectorMath")
        flo.operation = 'FLOOR'
        noise = nt.nodes.new("ShaderNodeTexWhiteNoise")
        noise.noise_dimensions = '3D'
        gt = nt.nodes.new("ShaderNodeMath")
        gt.operation = 'GREATER_THAN'
        gt.inputs[1].default_value = .9990
        grp = nt.nodes.new("ShaderNodeGroup")
        grp.node_tree = night_group()
        star = nt.nodes.new("ShaderNodeMath")
        star.operation = 'MULTIPLY'
        up = nt.nodes.new("ShaderNodeMath")
        up.operation = 'MULTIPLY'
        add = nt.nodes.new("ShaderNodeMix")
        add.data_type = 'RGBA'
        add.blend_type = 'ADD'
        add.inputs[7].default_value = (.9, .95, 1, 1)
        nt.links.new(tc.outputs["Generated"], sep.inputs[0])
        nt.links.new(sep.outputs[2], ramp.inputs[0])
        nt.links.new(ramp.outputs[0], mix.inputs[0])
        nt.links.new(hor.outputs[0], mix.inputs[6])
        nt.links.new(zen.outputs[0], mix.inputs[7])
        nt.links.new(tc.outputs["Generated"], scale.inputs[0])
        nt.links.new(scale.outputs[0], flo.inputs[0])
        nt.links.new(flo.outputs[0], noise.inputs["Vector"])
        nt.links.new(noise.outputs["Value"], gt.inputs[0])
        nt.links.new(gt.outputs[0], star.inputs[0])
        nt.links.new(grp.outputs[0], star.inputs[1])
        nt.links.new(star.outputs[0], up.inputs[0])
        nt.links.new(ramp.outputs[0], up.inputs[1])
        nt.links.new(up.outputs[0], add.inputs[0])
        # clouds: a noise laid on a flat ceiling far overhead, brighter than the sky and tinted by the horizon
        cdiv = nt.nodes.new("ShaderNodeMath")
        cdiv.operation = 'MAXIMUM'
        cdiv.inputs[1].default_value = .06
        cproj = nt.nodes.new("ShaderNodeVectorMath")
        cproj.operation = 'DIVIDE'
        cnoise = nt.nodes.new("ShaderNodeTexNoise")
        cnoise.name = "clouds"
        cnoise.inputs["Scale"].default_value = 1.15
        cnoise.inputs["Detail"].default_value = 5.0
        cnoise.inputs["Roughness"].default_value = .58
        cramp = nt.nodes.new("ShaderNodeMapRange")
        cramp.inputs[1].default_value = .5
        cramp.inputs[2].default_value = .78
        chigh = nt.nodes.new("ShaderNodeMath")      # none at the horizon itself, where the noise would smear
        chigh.operation = 'MULTIPLY'
        ccover = nt.nodes.new("ShaderNodeValue")
        ccover.name = "cloud"
        ccov = nt.nodes.new("ShaderNodeMath")
        ccov.operation = 'MULTIPLY'
        ccol = nt.nodes.new("ShaderNodeMix")
        ccol.data_type = 'RGBA'
        ccol.inputs[0].default_value = .55
        ccol.inputs[7].default_value = (1, 1, 1, 1)
        cmix = nt.nodes.new("ShaderNodeMix")
        cmix.data_type = 'RGBA'
        comb = nt.nodes.new("ShaderNodeCombineXYZ")
        nt.links.new(sep.outputs[2], cdiv.inputs[0])
        nt.links.new(tc.outputs["Generated"], cproj.inputs[0])
        nt.links.new(cdiv.outputs[0], comb.inputs[0])
        nt.links.new(cdiv.outputs[0], comb.inputs[1])
        nt.links.new(cdiv.outputs[0], comb.inputs[2])
        nt.links.new(comb.outputs[0], cproj.inputs[1])
        nt.links.new(cproj.outputs[0], cnoise.inputs["Vector"])
        nt.links.new(cnoise.outputs[0], cramp.inputs[0])
        nt.links.new(cramp.outputs[0], chigh.inputs[0])
        nt.links.new(ramp.outputs[0], chigh.inputs[1])
        nt.links.new(chigh.outputs[0], ccov.inputs[0])
        nt.links.new(ccover.outputs[0], ccov.inputs[1])
        nt.links.new(hor.outputs[0], ccol.inputs[6])
        nt.links.new(ccov.outputs[0], cmix.inputs[0])
        nt.links.new(mix.outputs[2], cmix.inputs[6])
        nt.links.new(ccol.outputs[2], cmix.inputs[7])
        nt.links.new(cmix.outputs[2], add.inputs[6])
        nt.links.new(add.outputs[2], bg.inputs[0])
        nt.links.new(bg.outputs[0], out.inputs[0])
    scn.world = w
    tr_coll("Lights")
    sun = bpy.data.objects.get("TR Sun")
    if sun is None:
        sun = bpy.data.objects.new("TR Sun", bpy.data.lights.new("TR Sun", 'SUN'))
        tr_coll("Lights").objects.link(sun)
    sun.data.angle = math.radians(2.5)
    return w, sun


def haze_setup():
    """The air: the further away, the more of the haze's color. Amount and color are keyed with the light."""
    scn = tr_scene()
    vl = scn.view_layers[0]
    vl.use_pass_mist = True
    vl.use_pass_z = True
    scn.world.mist_settings.start = 10
    scn.world.mist_settings.depth = 420
    scn.world.mist_settings.falloff = 'LINEAR'
    ng = bpy.data.node_groups.get("HC Air")
    if ng is None:
        ng = bpy.data.node_groups.new("HC Air", "CompositorNodeTree")
        ng.interface.new_socket(name="Image", in_out='OUTPUT', socket_type='NodeSocketColor')
        rl = ng.nodes.new("CompositorNodeRLayers")
        rl.name = "render"
        go = ng.nodes.new("NodeGroupOutput")
        amount = ng.nodes.new("ShaderNodeValue")
        amount.name = "amount"
        color = ng.nodes.new("CompositorNodeRGB")
        color.name = "color"
        mul = ng.nodes.new("ShaderNodeMath")
        mul.operation = 'MULTIPLY'
        mul.use_clamp = True
        near = ng.nodes.new("ShaderNodeMath")       # the sky has no depth: it keeps its own colors
        near.operation = 'LESS_THAN'
        near.inputs[1].default_value = 5000
        mul2 = ng.nodes.new("ShaderNodeMath")
        mul2.operation = 'MULTIPLY'
        mix = ng.nodes.new("ShaderNodeMix")
        mix.data_type = 'RGBA'
        ng.links.new(rl.outputs["Mist"], mul.inputs[0])
        ng.links.new(amount.outputs[0], mul.inputs[1])
        ng.links.new(rl.outputs["Depth"], near.inputs[0])
        ng.links.new(mul.outputs[0], mul2.inputs[0])
        ng.links.new(near.outputs[0], mul2.inputs[1])
        ng.links.new(mul2.outputs[0], mix.inputs[0])
        ng.links.new(rl.outputs["Image"], mix.inputs[6])
        ng.links.new(color.outputs[0], mix.inputs[7])
        ng.links.new(mix.outputs[2], go.inputs[0])
    ng.nodes["render"].scene = scn
    scn.compositing_node_group = ng
    scn.render.use_compositing = True
    return ng


def render_setup():
    scn = tr_scene()
    scn.render.engine = 'BLENDER_EEVEE'
    scn.render.resolution_x, scn.render.resolution_y = RES
    scn.render.resolution_percentage = 100
    scn.render.fps = FPS_TR
    scn.render.film_transparent = False
    scn.render.image_settings.file_format = 'PNG'
    scn.render.image_settings.color_mode = 'RGB'
    scn.render.image_settings.compression = 15
    scn.render.use_file_extension = True
    for attr, val in (("taa_render_samples", 8), ("use_shadows", True), ("use_raytracing", False), ("use_gtao", True), ("use_volumetric_shadows", False),
                      ("shadow_ray_count", 1), ("shadow_step_count", 3), ("use_shadow_jitter_viewport", False), ("shadow_resolution_scale", .5)):
        try:
            setattr(scn.eevee, attr, val)
        except Exception:
            pass
    scn.view_settings.view_transform = 'AgX'
    for look in ('AgX - Punchy', 'Punchy', 'AgX - Medium High Contrast', 'Medium High Contrast'):
        try:
            scn.view_settings.look = look
            break
        except Exception:
            pass
    scn.view_settings.exposure = 0
    scn.view_settings.gamma = 1


# what the light is like at each time of day: sun elevation and compass (degrees: 0 from +X, 90 from +Y), sun strength
# and color, the sky at the horizon and overhead, the sky's strength, the night factor, exposure
LOOKS = {
    "blue":    dict(el=-6, az=8, sun=0.0, col=(1, .7, .5), hor=(.30, .42, .62), zen=(.045, .09, .22), sky=.55, night=1.0, ev=.9, haze=.8, air=(.20, .28, .42), cloud=.5),
    "dawn":    dict(el=7, az=8, sun=2.6, col=(1, .62, .36), hor=(1.0, .62, .38), zen=(.22, .38, .70), sky=.8, night=.25, ev=.2, haze=.75, air=(.95, .62, .42), cloud=.8),
    "day":     dict(el=44, az=-52, sun=5.0, col=(1, .95, .86), hor=(.62, .80, 1.0), zen=(.12, .34, .86), sky=1.15, night=0.0, ev=.1, haze=.42, air=(.62, .74, .92), cloud=.7),
    "noon":    dict(el=64, az=-90, sun=5.2, col=(1, .98, .92), hor=(.64, .82, 1.0), zen=(.12, .33, .86), sky=1.15, night=0.0, ev=.05, haze=.38, air=(.64, .76, .93), cloud=.7),
    "gold":    dict(el=15, az=-158, sun=5.2, col=(1, .72, .44), hor=(1.0, .70, .44), zen=(.24, .38, .74), sky=.95, night=.12, ev=.2, haze=.5, air=(1.0, .62, .36), cloud=.85),
    "dusk":    dict(el=5, az=-170, sun=2.0, col=(1, .5, .34), hor=(.98, .46, .34), zen=(.10, .14, .40), sky=.7, night=.9, ev=.45, haze=.7, air=(.62, .34, .34), cloud=.8),
    "night":   dict(el=38, az=-120, sun=.25, col=(.55, .68, 1), hor=(.06, .09, .2), zen=(.008, .016, .05), sky=1.0, night=1.0, ev=.7, haze=.6, air=(.05, .07, .15), cloud=.45),
}


def sun_rotation(el, az):
    e, a = math.radians(el), math.radians(az)
    travel = -Vector((math.cos(e) * math.cos(a), math.cos(e) * math.sin(a), math.sin(e)))
    return travel.to_track_quat('-Z', 'Y').to_euler()


def set_look(name, frame=None, hold=True):
    """Puts the light in one of the LOOKS, and keys it at `frame` (hold: it stays so until the next key)."""
    L = LOOKS[name]
    scn = tr_scene()
    w = scn.world
    sun = bpy.data.objects["TR Sun"]
    nt = w.node_tree
    sun.rotation_euler = sun_rotation(L["el"], L["az"])
    sun.data.energy = L["sun"]
    lamp = bpy.data.lights.get("TR Lamp")
    if lamp is not None:
        lamp.energy = 2600 * L["night"] ** 2
    sun.data.color = L["col"]
    nt.nodes["horizon"].outputs[0].default_value = (*L["hor"], 1)
    nt.nodes["zenith"].outputs[0].default_value = (*L["zen"], 1)
    nt.nodes["bg"].inputs[1].default_value = L["sky"]
    night_value().default_value = L["night"]
    scn.view_settings.exposure = L["ev"]
    nt.nodes["cloud"].outputs[0].default_value = L["cloud"]
    air = bpy.data.node_groups.get("HC Air")
    if air is not None:
        air.nodes["amount"].outputs[0].default_value = L["haze"]
        air.nodes["color"].outputs[0].default_value = (*L["air"], 1)
    if frame is None:
        return
    prefs = bpy.context.preferences.edit
    keep = prefs.keyframe_new_interpolation_type
    prefs.keyframe_new_interpolation_type = 'CONSTANT' if hold else 'LINEAR'
    try:
        sun.keyframe_insert("rotation_euler", frame=frame)
        sun.data.keyframe_insert("energy", frame=frame)
        sun.data.keyframe_insert("color", frame=frame)
        if lamp is not None:
            lamp.keyframe_insert("energy", frame=frame)
        nt.nodes["horizon"].outputs[0].keyframe_insert("default_value", frame=frame)
        nt.nodes["zenith"].outputs[0].keyframe_insert("default_value", frame=frame)
        nt.nodes["bg"].inputs[1].keyframe_insert("default_value", frame=frame)
        night_value().keyframe_insert("default_value", frame=frame)
        scn.view_settings.keyframe_insert("exposure", frame=frame)
        nt.nodes["cloud"].outputs[0].keyframe_insert("default_value", frame=frame)
        if air is not None:
            air.nodes["amount"].outputs[0].keyframe_insert("default_value", frame=frame)
            air.nodes["color"].outputs[0].keyframe_insert("default_value", frame=frame)
        for owner in (sun, sun.data, lamp, nt, night_group(), scn, air):
            if owner is not None:
                for fc in _curves(owner):
                    for kp in fc.keyframe_points:
                        if abs(kp.co.x - frame) < .01:
                            kp.interpolation = 'CONSTANT' if hold else 'LINEAR'
    finally:
        prefs.keyframe_new_interpolation_type = keep


def _curves(owner):
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


def clear_light_keys():
    scn = tr_scene()
    for owner in (bpy.data.objects.get("TR Sun"), bpy.data.lights.get("TR Sun"), bpy.data.lights.get("TR Lamp"), scn.world.node_tree if scn.world else None,
                  night_group(), scn, bpy.data.node_groups.get("HC Air")):
        if owner is not None and owner.animation_data is not None:
            owner.animation_data_clear()


def light_setup():
    world_setup()
    render_setup()
    haze_setup()
    lit = city_lights()
    set_look("day")
    return lit


def tr_camera(name="TR Preview"):
    cam = bpy.data.objects.get(name)
    if cam is None:
        cam = bpy.data.objects.new(name, bpy.data.cameras.new(name))
        tr_coll("Cameras").objects.link(cam)
    cam.data.clip_start = .1
    cam.data.clip_end = 3000
    cam.data.sensor_width = 36
    return cam


def aim(cam, loc, target, lens=35.0, roll=0.0):
    cam.location = loc
    q = (Vector(target) - Vector(loc)).to_track_quat('-Z', 'Y')
    e = q.to_euler()
    cam.rotation_euler = e
    if roll:
        cam.rotation_euler.rotate_axis('Z', math.radians(roll))
    cam.data.lens = lens


def tr_still(path, loc, target, lens=35.0, look=None, frame=None, res=None, cam=None, samples=None):
    """Renders one frame of the trailer scene from a point of view, for looking at."""
    scn = tr_scene()
    c = cam or tr_camera()
    if cam is None:
        aim(c, loc, target, lens)
    if look:
        set_look(look)
    if frame is not None:
        scn.frame_set(frame)
    keep = (scn.render.resolution_x, scn.render.resolution_y, scn.camera, scn.eevee.taa_render_samples, scn.render.filepath)
    scn.camera = c
    if res:
        scn.render.resolution_x, scn.render.resolution_y = res
    if samples:
        scn.eevee.taa_render_samples = samples
    scn.render.filepath = path
    bpy.ops.render.render(scene=scn.name, write_still=True)
    scn.render.resolution_x, scn.render.resolution_y, scn.camera, scn.eevee.taa_render_samples, scn.render.filepath = keep
