"""Renders the frames MeshModelsTest dumped (app/build/meshframes/*.bin) from the game's own camera.

Each file is the triangles the engine built for the GPU, colors baked per vertex: so this shows exactly what the
game put in the frame (posing, facing, tints), lit by Blender rather than the game's shaders. Needs kit.py and
studio.py in front. Set HC_FRAMES to a list of names to render only some.
"""
import os
import struct

FRAMES = os.path.join(HC_ROOT, "app", "build", "meshframes")
OUT = os.path.join(HC_ROOT, "app", "build", "meshframes")


def frame_material():
    m = bpy.data.materials.get("HC Vertex Colors")
    if m is None:
        m = bpy.data.materials.new("HC Vertex Colors")
        m.use_nodes = True
        nt = m.node_tree
        bsdf = next(n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED')
        attr = nt.nodes.new("ShaderNodeVertexColor")
        attr.layer_name = "Col"
        nt.links.new(attr.outputs["Color"], bsdf.inputs["Base Color"])
        nt.links.new(attr.outputs["Alpha"], bsdf.inputs["Alpha"])
        bsdf.inputs["Roughness"].default_value = .7
        m.surface_render_method = 'DITHERED'
    return m


def load_frame(path, name):
    with open(path, "rb") as f:
        data = f.read()
    n = struct.unpack_from("<i", data, 0)[0]
    cam = struct.unpack_from("<3f", data, 4)
    fwd = struct.unpack_from("<3f", data, 16)
    focal = struct.unpack_from("<f", data, 28)[0]
    off = 32
    verts = []
    cols = []
    for t in range(n):
        for j in range(3):
            x, y, z, c = struct.unpack_from("<fffI", data, off)
            off += 16
            # the game's y points south; Blender's north
            verts.append((x, -y, z))
            cols.append(((c & 255) / 255, ((c >> 8) & 255) / 255, ((c >> 16) & 255) / 255, ((c >> 24) & 255) / 255))
    faces = [(k * 3, k * 3 + 1, k * 3 + 2) for k in range(n)]
    remove_object(name)
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    attr = me.color_attributes.new("Col", 'FLOAT_COLOR', 'CORNER')
    for li, loop in enumerate(me.loops):
        attr.data[li].color = cols[loop.vertex_index]
    me.materials.append(frame_material())
    for p in me.polygons:
        p.use_smooth = False
    ob = bpy.data.objects.new(name, me)
    collection("Game Frames").objects.link(ob)
    return ob, (cam[0], -cam[1], cam[2]), (fwd[0], -fwd[1], fwd[2]), focal


def render_frame(name, res=(1600, 900)):
    ob, cam, fwd, focal = load_frame(os.path.join(FRAMES, name + ".bin"), "frame_" + name)
    scn = bpy.context.scene
    c = bpy.data.objects["HC Camera"]
    c.data.type = 'PERSP'
    # the game's focal is in pixels per screen height: turn it into a vertical field of view
    c.data.sensor_fit = 'VERTICAL'
    c.data.angle = 2 * math.atan(.5 / focal)
    c.location = cam
    c.rotation_euler = Vector(fwd).to_track_quat('-Z', 'Y').to_euler()
    hidden = []
    for o in scn.objects:
        if o.name not in (ob.name, "HC Sun", "HC Camera") and not o.hide_render:
            o.hide_render = True
            hidden.append(o)
    scn.render.resolution_x, scn.render.resolution_y = res
    scn.render.filepath = os.path.join(OUT, name + ".png")
    bpy.ops.render.render(write_still=True)
    for o in hidden:
        o.hide_render = False
    c.data.type = 'ORTHO'
    return scn.render.filepath, len(ob.data.polygons)


if bpy.data.objects.get("HC Camera") is None:
    studio_setup()
_names = globals().get("HC_FRAMES") or sorted(f[:-4] for f in os.listdir(FRAMES) if f.endswith(".bin"))
result = {n: render_frame(n) for n in _names}
