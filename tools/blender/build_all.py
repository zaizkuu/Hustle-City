"""Builds the whole library, lays it out, marks every model as a Blender asset and stages a street scene.

Run after kit.py, studio.py and the asset scripts, in this order (later ones use earlier ones' parts):
  bl.py kit.py studio.py furniture.py furniture2.py items.py props.py vehicles.py buildings.py landmarks.py people.py
        vehicles2.py rig.py build_all.py
"""


def label(text, x, y, size=1.2):
    name = "Label " + text
    old = bpy.data.objects.get(name)
    if old is not None:
        bpy.data.objects.remove(old, do_unlink=True)
    cu = bpy.data.curves.new(name, 'FONT')
    cu.body = text
    cu.size = size
    ob = bpy.data.objects.new(name, cu)
    collection("Studio").objects.link(ob)
    ob.location = (x, y, .01)
    ob.hide_render = True
    ob.hide_select = True
    cu.materials.append(M(0x2E3238, "PAINT"))
    return ob


def mark_assets(obs, cat):
    for ob in obs:
        try:
            ob.asset_mark()
            ob.asset_data.description = "Hustle City / " + cat
            for t in ("hustle-city", cat.lower()):
                if t not in ob.asset_data.tags:
                    ob.asset_data.tags.new(t)
        except Exception:
            pass


def street_scene():
    """A block of the city put together from the library, as linked copies: a quick check that the scales agree."""
    coll = category("Street Scene")
    for ob in list(coll.objects):
        me = ob.data
        bpy.data.objects.remove(ob, do_unlink=True)
        if me is not None and me.users == 0 and isinstance(me, bpy.types.Mesh):
            bpy.data.meshes.remove(me)
    ox, oy = 0.0, 300.0
    made = []

    def put(name, x, y, rz=0.0):
        src = bpy.data.objects[name]
        ob = bpy.data.objects.new("scene." + name, src.data)
        coll.objects.link(ob)
        ob.location = (ox + x, oy + y, 0)
        ob.rotation_euler = (0, 0, math.radians(rz))
        made.append(ob)
        return ob

    g = Asset("scene.ground")
    asphalt = M(0x3A3D42, "ASPHALT")
    walk = M(0xB9B4AA, "CONCRETE")
    g.box(-48, -22, -.3, 62, 0, 0, asphalt)
    g.box(-48, 0, -.3, 62, 6, .16, walk)
    g.box(-48, 6, -.3, 62, 34, .1, M(0x8E8A82, "CONCRETE"))
    g.box(-48, -28, -.3, 62, -22, .16, walk)
    g.box(-48, -.3, 0, 62, 0, .16, M(0x9A958C, "CONCRETE"))
    for k in range(19):
        g.box(-46 + k * 6, -11.15, 0, -43 + k * 6, -10.85, .012, M(0xE9EDF2, "PAINT"))
    for k in range(8):
        g.box(18 + k * .9, -21, 0, 18.5 + k * .9, -1, .012, M(0xE9EDF2, "PAINT"))
    for k in range(18):
        g.box(-47.9 + k * 6.1, 0, .16, -47.85 + k * 6.1, 6, .165, M(0x9A958C, "CONCRETE"))
    ground = g.finish(coll)
    ground.location = (ox, oy, 0)
    made.append(ground)

    x = -44.0
    for name, w, d in (("brownstone", 7, 14), ("walkup", 12, 16), ("shop", 10, 10), ("classic", 22, 18), ("deco", 22, 22), ("modern", 16, 16), ("pencil", 6, 12)):
        put(name, x + w / 2, 6.2 + d / 2 + (4.0 if name == "brownstone" else 1.0 if name in ("classic", "shop") else 0))
        x += w + 1.2
    for k in range(6):
        put("lamp_post", -40 + k * 18, .9)
    for k, n in enumerate(("tree_oak", "tree_cherry", "tree_oak", "tree_oak", "tree_cherry")):
        put(n, -31 + k * 18.5, 1.6, rz=k * 50)
    put("traffic_light", 15.5, .9)
    put("fire_hydrant", -22, 1.0)
    put("park_bench", -8, 4.6, rz=0)
    put("trash_bin", -4.5, 4.6)
    put("newspaper_box", -15.5, 4.9)
    put("mailbox", 5.5, 4.9)
    put("bus_shelter", 36, 3.6)
    put("hotdog_cart", 9, 2.6, rz=10)
    put("cafe_table", -19.5, 3.6)
    put("bike_rack", 24.5, 2.0, rz=90)
    put("vending_machine", 55.6, 5.4)
    put("planter", 28, 5.2)
    put("phone_booth", -36, 4.4)
    # traffic: this side drives towards -X, the far side towards +X
    for name, xx, yy, rz in (("taxi", -30, -3.6, -90), ("beater", -12, -3.4, -90), ("bus", 38, -6.2, -90), ("van", 6, -7.4, -90), ("scooter", -2, -9.2, -90),
                             ("sedan", -20, -14.6, 90), ("box_truck", 4, -15.4, 90), ("sports_car", 30, -14.8, 90), ("suv", 48, -18.4, 90),
                             ("pickup", -38, -18.2, 90), ("jeepney", -40, -8.0, -90)):
        put(name, xx, yy, rz)
    return made


def build_all():
    studio_setup()
    for name in ("test_shelf",):
        remove_object(name)
    furn = build_furniture()
    items = build_items()
    props = build_props()
    veh = build_vehicles()
    bld = build_buildings()
    lmk = build_landmarks()
    ppl = build_people()
    clips = bake_clips()
    cast = build_people_preview()
    # the library floor: small things nearest the viewer, buildings at the back
    y = 0.0
    label("PEOPLE (parts the game poses)", -8, y - 9, 1.0)
    lay_out(ppl, x0=0, y0=y - 9, gap=.3, row_width=14)
    label("PEOPLE (on the rig, each in a moment of a clip)", -8, y - 5, 1.0)
    for k, arm in enumerate(cast):
        arm.location = (k * 1.15, y - 5, arm.location.z)
    rig = rig_object()
    rig.location = (-2.5, y - 5, 0)
    label("ITEMS", -8, y, 1.0)
    y = lay_out(items, x0=0, y0=y, gap=.35, row_width=14) + 4
    label("FURNITURE", -8, y, 1.0)
    y = lay_out(furn, x0=0, y0=y, gap=1.4, row_width=44) + 8
    label("STREET PROPS", -12, y, 1.6)
    y = lay_out(props, x0=0, y0=y, gap=2.0, row_width=60) + 10
    label("VEHICLES", -12, y, 1.6)
    y = lay_out(veh, x0=0, y0=y, gap=2.2, row_width=60) + 14
    label("BUILDINGS", -16, y, 2.4)
    y = lay_out(bld, x0=0, y0=y, gap=8, row_width=150) + 40
    label("LANDMARKS", -16, y, 2.4)
    y = lay_out(lmk, x0=0, y0=y, gap=14, row_width=240) + 30
    for obs, cat in ((furn, "Furniture"), (items, "Items"), (props, "Street Props"), (veh, "Vehicles"), (bld, "Buildings"), (lmk, "Landmarks"), (ppl, "People")):
        mark_assets(obs, cat)
    scene = street_scene()
    for ob in scene:
        ob.location.y += y
    bpy.context.view_layer.update()
    return {"Furniture": furn, "Items": items, "Street Props": props, "Vehicles": veh, "Buildings": bld, "Landmarks": lmk, "People": ppl, "People Preview": cast, "Street Scene": scene}


LIB = build_all()
result = {k: {"count": len(v), "tris": sum(int(o.get("hc_tris", 0)) for o in v)} for k, v in LIB.items()}
