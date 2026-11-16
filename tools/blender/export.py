"""Saves the library and writes what the game and people need from it. Needs kit.py and studio.py in front.

  art/hustle_city_assets.blend   the library itself (every model is marked as a Blender asset)
  art/models/<category>/<id>.glb one file per model, origin at the middle of its footprint on the floor
  art/models/manifest.json       sizes, triangle counts, footprints and the flat materials of every model
  art/previews/*.png             contact sheets

Set HC_EXPORT to choose the steps: any of "save", "models", "previews".
"""
import json
import os
import uuid

ART = os.path.join(HC_ROOT, "art")
CATS = (("Furniture", "furniture"), ("Items", "items"), ("Street Props", "props"), ("Vehicles", "vehicles"), ("Buildings", "buildings"),
        ("Landmarks", "landmarks"), ("People", "people"))


def cat_objects(name):
    return sorted(bpy.data.collections[name].objects, key=lambda o: (round(o.location.y, 2), o.location.x))


def catalogs():
    """Asset Browser catalogs, one per category, written beside the .blend."""
    lines = ["# This is an Asset Catalog Definition file for Blender.", "", "VERSION 1", ""]
    for cat, slug in CATS:
        cid = str(uuid.uuid5(uuid.NAMESPACE_URL, "hustle-city/" + slug))
        lines.append("{:s}:Hustle City/{:s}:Hustle City-{:s}".format(cid, cat, cat))
        for ob in bpy.data.collections[cat].objects:
            if ob.asset_data is not None:
                ob.asset_data.catalog_id = cid
    with open(os.path.join(ART, "blender_assets.cats.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")


def save():
    os.makedirs(ART, exist_ok=True)
    catalogs()
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(ART, "hustle_city_assets.blend"), compress=True)


def export_models():
    out = {"units": "meters", "up": "+Z in Blender, +Y in the .glb files", "front": "-Y in Blender (the game's +y); vehicles point their nose that way",
           "origin": "middle of the footprint, on the floor", "categories": {}}
    view = bpy.context.view_layer
    for cat, slug in CATS:
        d = os.path.join(ART, "models", slug)
        os.makedirs(d, exist_ok=True)
        entries = []
        for ob in cat_objects(cat):
            for o in view.objects:
                o.select_set(False)
            ob.select_set(True)
            view.objects.active = ob
            loc = ob.location.copy()
            ob.location = (0, 0, 0)
            view.update()
            path = os.path.join(d, ob.name + ".glb")
            bpy.ops.export_scene.gltf(filepath=path, export_format='GLB', use_selection=True, export_extras=True, export_yup=True)
            ob.location = loc
            bb = [Vector(c) for c in ob.bound_box]
            lo = [min(c[i] for c in bb) for i in range(3)]
            hi = [max(c[i] for c in bb) for i in range(3)]
            e = {"id": ob.name, "file": "{:s}/{:s}.glb".format(slug, ob.name), "tris": int(ob["hc_tris"]),
                 "size": [round(hi[i] - lo[i], 3) for i in range(3)], "min": [round(v, 3) for v in lo], "max": [round(v, 3) for v in hi],
                 "bytes": os.path.getsize(path),
                 "materials": [{"name": m.name, "kind": m.get("hc_kind", "PLAIN"), "color": m.get("hc_color", "FFFFFF")} for m in ob.data.materials]}
            if "hc_footprint" in ob:
                e["footprint"] = [int(v) for v in ob["hc_footprint"]]
            entries.append(e)
        out["categories"][slug] = entries
    view.update()
    with open(os.path.join(ART, "models", "manifest.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, indent=1)
    return {slug: len(v) for slug, v in out["categories"].items()}


def sheet(objs, path, row_width, gap, res=(2000, 1250), az=28.0, el=30.0):
    """Shoots the objects packed together, then puts them back where the library keeps them."""
    keep = [(o, o.location.copy()) for o in objs]
    lay_out(objs, x0=1200, y0=-1500, gap=gap, row_width=row_width)
    shoot(objs, path, res, az, el)
    for o, loc in keep:
        o.location = loc


def previews():
    d = os.path.join(ART, "previews")
    os.makedirs(d, exist_ok=True)
    made = []

    def go(name, objs, row_width, gap, **kw):
        p = os.path.join(d, name + ".png")
        sheet(objs, p, row_width, gap, **kw)
        made.append(p)

    furn = cat_objects("Furniture")
    by = {o.name: o for o in furn}
    order = [n for n, w, dd, fn in FURN] if "FURN" in globals() else [o.name for o in furn]
    furn = [by[n] for n in order if n in by]
    q = (len(furn) + 4) // 5
    for i in range(5):
        go("furniture_{:d}".format(i + 1), furn[i * q:(i + 1) * q], 15, 1.3, el=33)
    items = cat_objects("Items")
    by = {o.name: o for o in items}
    order = [n for n, fn in ITEMS] if "ITEMS" in globals() else [o.name for o in items]
    items = [by[n] for n in order if n in by]
    small = [o for o in items if max(o.dimensions) < .45]
    big = [o for o in items if max(o.dimensions) >= .45]
    h = (len(small) + 1) // 2
    go("items_1", small[:h], 1.35, .1, el=34)
    go("items_2", small[h:], 1.35, .1, el=34)
    go("items_3", big, 3.2, .3, el=32)
    props = cat_objects("Street Props")
    tall = [o for o in props if o.dimensions.z >= 3.2]
    low = [o for o in props if o.dimensions.z < 3.2]
    go("props_1", low, 13, 1.2, el=30)
    go("props_2", tall, 26, 2.5, el=26)
    veh = cat_objects("Vehicles")
    go("vehicles", [o for o in veh if o.dimensions.y < 15], 17, 1.6, az=36, el=27)
    go("vehicles_2", [bpy.data.objects[n] for n in ("kei_car", "tricycle", "moped", "modern_jeepney", "metro_car")], 30, 1.6, az=36, el=24)
    b = {o.name: o for o in bpy.data.collections["Buildings"].objects}
    go("buildings_1", [b[n] for n in ("brownstone", "walkup", "shop", "classic", "modern", "industrial")], 400, 5, res=(2200, 1100), el=22)
    go("buildings_2", [b[n] for n in ("colonial", "tropical", "pencil", "hanok")], 400, 5, res=(2200, 1200), el=22)
    go("buildings_3", [b[n] for n in ("deco", "glass_tower")], 400, 8, res=(1400, 1800), el=18)
    lm = {o.name: o for o in bpy.data.collections["Landmarks"].objects}
    go("landmarks_1", [lm[n] for n in ("torii", "paifang", "palace_gate", "monument")], 400, 6, res=(2400, 1200), el=16)
    go("landmarks_2", [lm[n] for n in ("cathedral", "city_hall", "ferris_wheel")], 400, 10, res=(2400, 1300), el=16)
    go("landmarks_3", [lm["lattice_tower"]], 400, 8, res=(1200, 2000), el=14)
    cast = sorted((o for o in bpy.data.collections["People Preview"].objects if o.parent is None), key=lambda o: o.name)
    p = os.path.join(d, "people.png")
    shoot(people_objects(cast), p, (2400, 1000), 22, 12, margin=1.04)
    made.append(p)
    scene = list(bpy.data.collections["Street Scene"].objects)
    for name, az, el, res in (("street_scene_1", 30, 26, (2400, 1350)), ("street_scene_2", -32, 16, (2400, 1350))):
        p = os.path.join(d, name + ".png")
        shoot(scene, p, res, az, el, margin=1.02)
        made.append(p)
    return made


_steps = globals().get("HC_EXPORT", ("save", "models", "previews"))
result = {}
if "models" in _steps:
    result["models"] = export_models()
if "previews" in _steps:
    result["previews"] = [os.path.basename(p) for p in previews()]
if "save" in _steps:
    save()
    result["blend"] = bpy.data.filepath
