"""Packs the models the game draws into app/src/main/assets/models.hcm, and the people's animation clips into
anims.hca beside it. Needs kit.py, people.py and rig.py in front (Blender side).

Every model of the library goes in, each with reduced-detail copies for distance (Blender's Decimate; people bring
their own lighter versions, built for the purpose), in the game's own axes (x right, y toward the front/forward,
z up: Blender's y negated) and with colors baked per vertex, so the engine appends a model to its frame with one
loop and no material lookups. render/Meshes.kt reads the file.

Names in the pack, by where the game draws them:
  <furniture id>         furniture (render/PartSorter.kt), keyed by its catalog id, plus office_chair at desks
  prop_<name>            street props and trees (render/StreetProps.kt, TreePainter.kt, WorldRenderer.kt)
  prop_tree_<k>_bare     a deciduous tree in winter; prop_tree_oak_park without its street pit
  car_<name>             vehicles (render/CarPainter.kt) and car_metro_car (render/Railways.kt)
  item_<name>            goods, things carried and held (render/StockPainter.kt, People.kt, InteriorRenderer.kt)
  lmk_<name>             landmarks (render/Landmarks.kt); the Ferris wheel in three: lmk_ferris_wheel (what stands),
                         lmk_ferris_rotor (what turns, centred on the hub) and lmk_ferris_gondola (one cabin,
                         hanging from the origin)
  bld_<name>             one building per architecture style (render/BuildingPainter.kt)
  ppl_<part>             the parts people are posed from (render/People.kt)

Color slots (per vertex, 0 = the vertex's own color; the game tints the rest, see render/Meshes.kt):
  people   1 shirt, 2 trousers, 3 skin, 4 hair, 5 apron or tie
  cars     1 paintwork, 2 headlights, 3 tail lights, 4 second color
  props    1 the body the game recolors (a tree's leaves by the season), 2 lamps lit at night,
           3 / 4 / 5 a traffic light's red, amber and green
  bld_*    1 facade, 2 / 3 / 4 windows in three groups (each lit at night or not, per building)
  lmk_*    1 a gondola's paint
  any      8 a light in its own color that shines only when the game says (night): signs, lamps, clocks

File (little-endian):
  'HCM1', u16 mesh count, then per mesh:
    u8 name length, name (ASCII)
    f32 x6  bounds: min x, y, z, max x, y, z
    u8 level count, then per level two parts, opaque then see-through:
      u32 vertex count, u32 index count
      f32[3n] positions, i8[3n] normals (x127), u8[4n] r g b a, u8[n] shader material, u8[n] color slot, u16[] indices
  then optionally 'META', u16 entry count, per entry: u8 name length, name, u8 value count, f32 values:
    bld_*  footprint width, footprint depth, height of the walls (the model's own design size, origin mid-footprint)
    lmk_*  footprint width, footprint depth; lmk_ferris_rotor adds hub height and rim radius

Clips (little-endian), read by render/Anims.kt:
  'HCA1', u8 bone count, then per bone: u8 name length, name, i8 parent (-1: none), f32 x3 joint at rest
  f32 how much closer in a woman's shoulders sit (a factor on the arm joints' x)
  u16 clip count, then per clip: u8 name length, name, u16 frame count, f32 frames per second, u8 loops,
    then per frame: f32 x3 where the hips are, and per bone i16 x4 its turn from its parent (quaternion x y z w, x32767)
Positions and turns are in the game's axes for a model: x to the figure's left, y forward, z up.
"""
import math
import os
import struct

OUT = os.path.join(HC_ROOT, "app", "src", "main", "assets", "models.hcm")

# render/Scene3D.kt `Mat`
MAT_OF = {"PLAIN": 0, "BRICK": 1, "STONE": 2, "GLASS": 3, "WOOD": 7, "TILE": 8, "METAL": 9, "ROOF": 10, "PLASTER": 12, "FOLIAGE": 13,
          "CONCRETE": 14, "FABRIC": 15, "PAINT": 16}

# Furniture, keyed by its catalog id (and office_chair, which the game sets at desks). True turns the model half a
# turn, where the game's definition faces the other way from the library's "front is -Y" (a cinema seat's back is at
# high y, a treadmill's console at low y). Displays with live stock in your own store keep the game's boxes (their
# goods are drawn slot by slot); everywhere else (rivals, places you visit, the warehouse) these models stand in.
FURNITURE = {
    "cash_register": False, "checkout_counter": False, "table_set": False, "barstool": False, "bench": True, "sofa": False,
    "lounge_sofa": False, "cinema_seats": True, "meeting_table": False, "office_desk": True, "desk": True, "filing_cabinet": False,
    "water_cooler": False, "copier": False, "whiteboard": False, "bed": False, "home_fridge": False, "tv": False, "plant_large": False,
    "plant_small": False, "floor_lamp": False, "trash_can": False, "rug": False, "treadmill": True, "exercise_bike": True,
    "weight_rack": False, "workout_bench": False, "boxing_bag": False, "gym_locker": False, "hair_chair": False, "hair_wash": False,
    "speaker_large": False, "dj_booth": False, "mannequin": False, "pallet": False, "hand_truck": True, "cleaning_station": False,
    "shopping_baskets": False, "security_camera": False, "security_gate": False, "stage_lights": False, "neon_cactus": False,
    "painting": False,
    "shower": False, "fitness_board": False, "fitting_room": False, "neon_circle": False, "wall_lamp": False, "dance_floor": False,
    "cinema_screen": False, "stage": False, "graphic_tablet": False, "desk_globe": False, "desk_calendar": False, "rowing_machine": False,
    "coat_check": False, "digital_scale": False,
    # displays
    "shelf": False, "drinks_fridge": True, "fresh_fridge": False, "freezer": False, "ice_cream_freezer": False, "flower_stand": True,
    "produce": False, "coffee_machine": True, "pastry_case": False, "bookshelf": True, "jewelry_case": False, "clothing_rack": False,
    "round_rack": False, "shoe_rack": True, "magazine_rack": False, "electronics_table": False, "cocktail_bar": False,
    "storage_shelf": False, "pallet_rack": False, "grill": False, "fryer": False, "cart_rack": False, "concession_stand": True,
    "pizza_oven": True,
    "office_chair": False,
}
TREES = ("tree_oak", "tree_cherry", "tree_pine", "tree_palm", "tree_ginkgo")
# vehicle -> the material that is its paintwork, which the game recolors per car
VEHICLES = {"sedan": "PAINT_2F80ED", "taxi": "PAINT_F2C94C", "pickup": "PAINT_C0392B", "van": "PAINT_E9EDF2", "sports_car": "PAINT_D0392B",
            "suv": "PAINT_2E3238", "box_truck": "PAINT_E9EDF2", "bus": "PAINT_2F80ED", "jeepney": "PAINT_EB5757", "kei_car": "PAINT_6FCF97",
            "modern_jeepney": "PAINT_F7FAFC", "beater": "PAINT_6F8FA0", "scooter": "PAINT_27AE60", "moped": "PAINT_D0392B",
            "tricycle": "PAINT_2F80ED", "metro_car": "PAINT_2F80ED"}
# a second color the game picks per vehicle: a two-tone roof, a cooperative's band
ACCENTS = {"kei_car": "PAINT_EDEFF2", "modern_jeepney": "PAINT_1E8E5A"}
# props whose body the game recolors
PROP_TINT = {"newspaper_box": "PAINT_2F5D8C", "vending_machine": "PAINT_D0392B", "phone_booth": "PAINT_C0392B", "dumpster": "METAL_2F6F4F",
             "market_stall": "PAINT_EB5757", "hotdog_cart": "PAINT_EB5757", "cafe_table": "PAINT_27AE60"}
# a traffic light's three lamps: red, amber and green (props.py SIGNAL_R / _Y / _G)
SIGNAL = {"EMIT_FF4D4D": 3, "PLASTIC_8A6A1E": 4, "PLASTIC_1E5A36": 5}
# props whose lights the game tints by the hour (a warm glow at night, grey by day); other props' lights keep their
# own color and only shine at night (slot 8)
LAMPLIKE = ("lamp_post", "park_lamp", "bus_shelter", "vending_machine")
# what some jobs leave out: the monument's own flag (the game flies the city's), and the posts of the strings across
# the street (the game ties them to the houses, or to poles of its own)
DROP = {"monument": ("PAINT_2F80ED", "PAINT_EB5757", "PAINT_E9EDF2", "PAINT_F2C94C", "METAL_E9EDF2", "METAL_D4AF37")}
# each building's wall material: the game paints it in the building's own facade color
FACADE = {"walkup": "BRICK_9C4A3A", "modern": "PLASTER_E8E6E1", "brownstone": "STONE_6B4436", "classic": "STONE_D9CFBF",
          "deco": "STONE_E6DDD0", "glass_tower": "GLASS_9FC4DD", "hanok": "PLASTER_E9E2D0", "shop": "PLASTER_F0ECE4",
          "pencil": "TILE_E8E2D6", "tropical": "PLASTER_7FB8B0", "colonial": "STONE_CFC3AE", "industrial": "BRICK_7A3B2E"}
# the Ferris wheel: its hub height and rim radius (landmarks.py)
WHEEL_HUB, WHEEL_R = 32.0, 28.0

# Detail levels by kind: a decimation ratio each; people bring their own modelled levels (0..3); a building's
# lighter levels ("trim", m2) leave out the small pieces of trim under that area (window frames, sills, fire-escape
# rods, AC units) and keep its walls, roofs and every window, which Decimate would melt into triangles
LEVELS = {"furn": (1.0, .42), "prop": (1.0, .4, .16), "car": (1.0, .42, .16), "ppl": (0, 1, 2, 3), "item": (1.0, .5, .22),
          "lmk": (1.0, .45, .18), "bld": (1.0, ("trim", .3), ("trim", 1.6))}
# what a building always keeps at every level
BLD_KEEP = ("ROOF", "GLASS")
# Most triangles a model may have at full detail, by kind; heavier ones start decimated (the tricycle and the moped
# carry riders modelled like the people, a cocktail bar its rows of bottles)
CAP = {"furn": 2000, "prop": 2000, "car": 4000, "item": 1000, "lmk": 5000, "bld": 11000}
# Height of a building's walls where its facade material doesn't tell: a glass tower's curtain wall is another glass,
# and pitched roofs ("max") count to the ridge, as the game's building height does
HEIGHT = {"glass_tower": 82.0, "colonial": "max", "hanok": "max", "industrial": "max"}
ANIMS = os.path.join(HC_ROOT, "app", "src", "main", "assets", "anims.hca")


def names_in(coll):
    return [o.name for o in bpy.data.collections[coll].objects]


def jobs():
    """(category, Blender object, packed name, flip, options) for everything that goes in."""
    out = []
    for name, flip in FURNITURE.items():
        out.append(("furn", name, name, flip, {}))
    for name in names_in("Street Props"):
        out.append(("prop", name, "prop_" + name, False, {}))
        if name in ("tree_oak", "tree_cherry", "tree_ginkgo"):
            out.append(("prop", name, "prop_" + name + "_bare", False, {"drop_kind": "FOLIAGE"}))
    out.append(("prop", "tree_oak", "prop_tree_oak_park", False, {"drop": ("PLAIN_4A3728", "CONCRETE_8E9399")}))
    out.append(("prop", "lantern_string", "prop_lantern_string_line", False, {"drop": ("WOOD_3E2723", "CONCRETE_8E9399")}))
    out.append(("prop", "bunting_string", "prop_bunting_string_line", False, {"drop": ("METAL_2A2D31",)}))
    for name in VEHICLES:
        out.append(("car", name, "car_" + name, False, {}))
    for name in names_in("Items"):
        out.append(("item", name, "item_" + name, False, {}))
    for name in names_in("Landmarks"):
        if name == "ferris_wheel":
            out.append(("lmk", name, "lmk_ferris_wheel", False, {"split": "frame"}))
            out.append(("lmk", name, "lmk_ferris_rotor", False, {"split": "rotor"}))
            out.append(("lmk", name, "lmk_ferris_gondola", False, {"split": "gondola"}))
        else:
            out.append(("lmk", name, "lmk_" + name, False, {"drop": DROP[name]} if name in DROP else {}))
    for name in names_in("Buildings"):
        out.append(("bld", name, "bld_" + name, False, {}))
    for ob in bpy.data.collections["People"].objects:
        out.append(("ppl", ob.name, "ppl_" + ob.name[4:], False, {}))
    return out


def lum(hexc):
    r, g, b = (hexc >> 16) & 255, (hexc >> 8) & 255, hexc & 255
    return .299 * r + .587 * g + .114 * b


def vertex_rule(cat, name, m, mats):
    """(shader material, color slot, emissive, shade or None) for a Blender material on a model of this kind.
    A shade (0..255) is what a slot's tint is multiplied by; None means full."""
    kind = m.get("hc_kind", "PLAIN")
    lit = kind in ("EMIT", "SCREEN")
    slot = 0
    shade = None
    if cat == "ppl":
        slot = int(m.get("hc_slot", 0))
    elif cat == "car":
        if m.name == VEHICLES[name]:
            slot = 1
        elif m.name == ACCENTS.get(name):
            slot = 4
        elif m.name == "EMIT_FFF4D6":
            slot, lit = 2, False
        elif m.name == "EMIT_FF4D4D":
            slot, lit = 3, False
        elif lit and name == "metro_car":
            # the lit windows and destination signs of a train, after dark
            slot, lit = 8, False
    elif cat == "prop":
        if name == "traffic_light" and m.name in SIGNAL:
            slot, lit = SIGNAL[m.name], False
        elif name in TREES and kind == "FOLIAGE":
            # the leaves: the game colors them by the season, keeping their light and dark tufts
            brightest = max(lum(int(x.get("hc_color", "FFFFFF"), 16)) for x in mats if x.get("hc_kind") == "FOLIAGE")
            slot, shade = 1, int(round(255 * min(1.0, lum(int(m.get("hc_color", "FFFFFF"), 16)) / max(1.0, brightest))))
        elif m.name == PROP_TINT.get(name):
            slot = 1
        elif lit and name in LAMPLIKE:
            # street lights and lit panels: the game decides, by the hour, whether they glow
            slot, lit = 2, False
        elif kind == "EMIT":
            # a sign, a lantern, a warning lamp: its own color, shining after dark
            slot, lit = 8, False
    elif cat == "bld":
        if m.name == FACADE[name]:
            slot = 1
        elif kind == "GLASS":
            slot = 2  # spread over 2..4 per face, below
        elif lit:
            slot, lit = 8, False
    elif cat == "lmk":
        if lit:
            slot, lit = 8, False
        elif name == "ferris_wheel" and m.name == "PAINT_EB5757":
            slot = 1
    if cat == "car":
        mat = {"PAINT": 16, "GLASS": 3}.get(kind, 0)
    elif cat == "prop":
        mat = {"WOOD": 7, "FOLIAGE": 13}.get(kind, 0)
    elif cat in ("bld", "lmk"):
        mat = MAT_OF.get(kind, 0)
    else:
        mat = 7 if kind == "WOOD" else 0
    return mat, slot, lit, shade


def alpha_of(m):
    try:
        b = next(n for n in m.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
        return float(b.inputs["Alpha"].default_value)
    except Exception:
        return 1.0


def level_mesh(ob, ratio):
    """The object's triangles at a detail ratio: (positions, corner normals, triangles as (loop triple, vert triple,
    material, centre and area of the face it came from))."""
    tmp = bpy.data.objects.new("hc_tmp", ob.data)
    bpy.context.scene.collection.objects.link(tmp)
    try:
        if ratio < .999:
            mod = tmp.modifiers.new("d", 'DECIMATE')
            mod.decimate_type = 'COLLAPSE'
            mod.ratio = ratio
        dg = bpy.context.evaluated_depsgraph_get()
        dg.update()
        ev = tmp.evaluated_get(dg)
        me = ev.to_mesh()
        me.calc_loop_triangles()
        pos = [tuple(v.co) for v in me.vertices]
        cn = [tuple(c.vector) for c in me.corner_normals]
        pc = [tuple(p.center) for p in me.polygons]
        pa = [p.area for p in me.polygons]
        tris = [(tuple(t.loops), tuple(t.vertices), t.material_index, pc[t.polygon_index], pa[t.polygon_index]) for t in me.loop_triangles]
        ev.to_mesh_clear()
    finally:
        bpy.data.objects.remove(tmp, do_unlink=True)
    return pos, cn, tris


def centroid(pos, verts):
    return tuple(sum(pos[v][i] for v in verts) / 3.0 for i in range(3))


def wheel_part(pos, verts):
    """Which part of the Ferris wheel a triangle belongs to: 'gondola' (with its index), 'rotor' or 'frame'."""
    cx, cy, cz = centroid(pos, verts)
    for k in range(24):
        ang = 2 * math.pi * k / 24
        px, pz = WHEEL_R * math.cos(ang), WHEEL_HUB + WHEEL_R * math.sin(ang)
        if abs(cx - px) <= 1.05 and pz - 2.8 <= cz <= pz - .16 and abs(cy) <= 1.2:
            return "gondola", k
    r = math.hypot(cx, cz - WHEEL_HUB)
    if 1.75 <= r <= WHEEL_R + .6 and abs(cy) <= 1.85:
        return "rotor", -1
    return "frame", -1


def keep_tris(tris, pos, mats, opt):
    """The triangles a job keeps, and how far to move them (Blender axes)."""
    drop = set(opt.get("drop", ()))
    dk = opt.get("drop_kind")
    if drop or dk:
        tris = [t for t in tris if mats[t[2]].name not in drop and (dk is None or mats[t[2]].get("hc_kind") != dk)]
    split = opt.get("split")
    shift = (0.0, 0.0, 0.0)
    if split:
        out = []
        for t in tris:
            part, k = wheel_part(pos, t[1])
            if part == split and (split != "gondola" or k == 0):
                out.append(t)
        tris = out
        if split == "rotor":
            shift = (0.0, 0.0, -WHEEL_HUB)
        elif split == "gondola":
            shift = (-WHEEL_R, 0.0, -WHEEL_HUB)
    return tris, shift


def window_group(c):
    """Windows fall into three groups by where they are, so a building can have some lit at night and not others."""
    h = (int(math.floor(c[0] * .8)) * 73856093) ^ (int(math.floor(c[1] * .8)) * 19349663) ^ (int(math.floor(c[2] / 1.5)) * 83492791)
    h ^= h >> 13
    return 2 + (h & 0x7fffffff) % 3


def pack_part(tris, pos, cn, rules, flip, shift, per_face):
    """One draw part as bytes: unique (vertex, normal, material, slot) corners and 16-bit indices."""
    sx = -1.0 if flip else 1.0
    index = {}
    P = []
    N = []
    C = []
    MT = []
    SL = []
    idx = []
    for loops, verts, mi, pc, _area in tris:
        mat, slot, lit, rgba = rules[mi]
        if per_face and slot == 2:
            slot = window_group(pc)
        for l, v in zip(loops, verts):
            n = cn[l]
            q = (int(round(n[0] * 127)), int(round(n[1] * 127)), int(round(n[2] * 127)))
            key = (v, q, mi, slot)
            k = index.get(key)
            if k is None:
                k = len(P) // 3
                index[key] = k
                p = pos[v]
                px, py, pz = p[0] + shift[0], p[1] + shift[1], p[2] + shift[2]
                # Blender (x, y, z) -> game (x, -y, z); a half turn negates x and y again
                P += [px * sx, -py * sx, pz]
                N += [max(-127, min(127, int(q[0] * sx))), max(-127, min(127, int(-q[1] * sx))), q[2]]
                C += rgba
                MT.append(mat * 2 + (1 if lit else 0))
                SL.append(slot)
            idx.append(k)
    nv = len(P) // 3
    if nv > 65535:
        raise RuntimeError("too many vertices for 16-bit indices: {:d}".format(nv))
    b = struct.pack("<II", nv, len(idx))
    b += struct.pack("<{:d}f".format(nv * 3), *P)
    b += struct.pack("<{:d}b".format(nv * 3), *N)
    b += struct.pack("<{:d}B".format(nv * 4), *C)
    b += struct.pack("<{:d}B".format(nv), *MT)
    b += struct.pack("<{:d}B".format(nv), *SL)
    b += struct.pack("<{:d}H".format(len(idx)), *idx)
    return b, nv, len(idx) // 3, P


def facade_top(ob, name):
    """Height of a building's walls: the top of its facade material, ignoring what stands above a parapet."""
    me = ob.data
    fi = next((i for i, m in enumerate(me.materials) if m.name == FACADE[name]), -1)
    zs = sorted(v.co.z for p in me.polygons if p.material_index == fi for v in (me.vertices[i] for i in p.vertices))
    if not zs:
        return max(v.co.z for v in me.vertices)
    # the walls' own top, not the odd chimney or stair bulkhead in the facade material
    return zs[int(len(zs) * .97)]


def meta_of(cat, name, packed, ob):
    if cat == "bld":
        fp = list(ob.get("hc_footprint", [0, 0]))
        h = HEIGHT.get(name)
        if h == "max":
            h = max(v.co.z for v in ob.data.vertices)
        elif h is None:
            h = facade_top(ob, name)
        return [float(fp[0]), float(fp[1]), float(h)]
    if cat == "lmk":
        fp = list(ob.get("hc_footprint", [0, 0]))
        vals = [float(fp[0]), float(fp[1])]
        if packed == "lmk_ferris_rotor":
            vals += [WHEEL_HUB, WHEEL_R]
        return vals
    return None


def export_game():
    blob = bytearray()
    stats = {}
    meta = []
    todo = jobs()
    blob += b"HCM1" + struct.pack("<H", len(todo))
    for cat, name, packed, flip, opt in todo:
        ob = bpy.data.objects[name]
        mats = list(ob.data.materials)
        rules = []
        clear = []
        for m in mats:
            mat, slot, lit, shade = vertex_rule(cat, name, m, mats)
            a = alpha_of(m)
            hexc = int(m.get("hc_color", "FFFFFF"), 16)
            rgb = [(hexc >> 16) & 255, (hexc >> 8) & 255, hexc & 255]
            if slot >= 8:
                pass  # a light keeps its own color: the slot only says when it shines
            elif shade is not None:
                rgb = [shade] * 3
            elif slot and "hc_shade" in m:
                # a slot's own color is a shade the game multiplies its tint by
                rgb = [int(m["hc_shade"])] * 3
            elif slot:
                rgb = [255, 255, 255]
            rules.append((mat, slot, lit, rgb + [int(round(a * 255))]))
            clear.append(a < .995)
        levels = []
        bounds = None
        counts = []
        ratios = LEVELS[cat]
        full = int(ob.get("hc_tris", 0))
        if cat in CAP and full > CAP[cat] and not opt.get("split"):
            k = CAP[cat] * .97 / full
            ratios = tuple(r if isinstance(r, tuple) else r * k for r in ratios)
        for ratio in ratios:
            if cat == "ppl":
                # people are modelled at every level: <part>.L1 and so on carry the same materials in their own order
                lob = ob if ratio == 0 else bpy.data.objects[name + ".L{:d}".format(ratio)]
                lmats = list(lob.data.materials)
                pos, cn, tris = level_mesh(lob, 1.0)
                tris = [(l, v, mats.index(lmats[mi]), c, a) for l, v, mi, c, a in tris]
                ratio = 1.0
            elif isinstance(ratio, tuple):
                # a building without its small trim: walls (the facade), roofs and windows always stay
                pos, cn, tris = level_mesh(ob, 1.0)
                keep = [m.name == FACADE.get(name) or m.get("hc_kind") in BLD_KEEP for m in mats]
                tris = [t for t in tris if keep[t[2]] or t[4] >= ratio[1]]
                ratio = .5
            else:
                pos, cn, tris = level_mesh(ob, ratio)
            tris, shift = keep_tris(tris, pos, mats, opt)
            if ratio < .999 and counts and len(tris) > counts[-1] * .93:
                # nothing to gain: this level is no lighter than the one before
                continue
            solid = [t for t in tris if not clear[t[2]]]
            glassy = [t for t in tris if clear[t[2]]]
            b0, nv0, nt0, P0 = pack_part(solid, pos, cn, rules, flip, shift, cat == "bld")
            b1, nv1, nt1, P1 = pack_part(glassy, pos, cn, rules, flip, shift, cat == "bld")
            levels.append(b0 + b1)
            counts.append(nt0 + nt1)
            if bounds is None:
                allp = P0 + P1
                xs, ys, zs = allp[0::3], allp[1::3], allp[2::3]
                bounds = (min(xs), min(ys), min(zs), max(xs), max(ys), max(zs))
        nm = packed.encode("ascii")
        blob += struct.pack("<B", len(nm)) + nm + struct.pack("<6f", *bounds) + struct.pack("<B", len(levels))
        for b in levels:
            blob += b
        stats.setdefault(cat, []).append((packed, counts))
        mv = meta_of(cat, name, packed, ob)
        if mv:
            meta.append((packed, mv))
    blob += b"META" + struct.pack("<H", len(meta))
    for packed, vals in meta:
        nm = packed.encode("ascii")
        blob += struct.pack("<B", len(nm)) + nm + struct.pack("<B", len(vals)) + struct.pack("<{:d}f".format(len(vals)), *vals)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "wb") as f:
        f.write(blob)
    return stats, len(blob), meta


def action_curves(act):
    """Every F-curve of an action, wherever this Blender keeps them."""
    out = []
    try:
        for layer in act.layers:
            for st in layer.strips:
                for bag in st.channelbags:
                    out += list(bag.fcurves)
    except Exception:
        pass
    if not out:
        try:
            out = list(act.fcurves)
        except Exception:
            pass
    return out


def export_anims():
    """Writes the hc_* Actions as clips: each bone's turn from its parent, in the figure's own axes."""
    arm = skeleton(False)
    rest = {b.name: b.matrix_local.to_3x3() for b in arm.bones}
    index = {n: k for k, n in enumerate(BONE_NAMES)}
    blob = bytearray(b"HCA1") + struct.pack("<B", len(BONES))
    for name, parent, joint, aim in BONES:
        nm = name.encode("ascii")
        blob += struct.pack("<B", len(nm)) + nm + struct.pack("<b", index[parent] if parent else -1) + struct.pack("<3f", joint[0], -joint[1], joint[2])
    blob += struct.pack("<f", SX_F / SX_M)
    acts = [a for a in bpy.data.actions if a.name.startswith("hc_") and "hc_frames" in a]
    order = [c[0] for c in CLIPS]
    acts.sort(key=lambda a: order.index(a.name[3:]) if a.name[3:] in order else 999)
    blob += struct.pack("<H", len(acts))
    report = {}
    for act in acts:
        n = int(act["hc_frames"])
        fps = float(act["hc_fps"])
        loop = bool(act["hc_loop"])
        curves = {}
        for fc in action_curves(act):
            curves[(fc.data_path, fc.array_index)] = fc
        frames = n if loop else n + 1
        nm = act.name[3:].encode("ascii")
        blob += struct.pack("<B", len(nm)) + nm + struct.pack("<HfB", frames, fps, 1 if loop else 0)
        last = {}
        for k in range(frames):
            f = 1 + k * FPS / fps
            loc = Vector([curves[('pose.bones["hips"].location', i)].evaluate(f) if ('pose.bones["hips"].location', i) in curves else 0.0 for i in range(3)])
            off = rest["hips"] @ loc
            blob += struct.pack("<3f", off.x, -off.y, HIP + off.z)
            for bn in BONE_NAMES:
                path = 'pose.bones["{:s}"].rotation_quaternion'.format(bn)
                q = Quaternion([curves[(path, i)].evaluate(f) if (path, i) in curves else (1.0 if i == 0 else 0.0) for i in range(4)])
                q.normalize()
                d = (rest[bn] @ q.to_matrix() @ rest[bn].inverted()).to_quaternion()
                # Blender's y is the game's -y: a mirror turns a rotation's x and z about
                g = Quaternion((d.w, -d.x, d.y, -d.z))
                if bn in last and last[bn].dot(g) < 0:
                    g.negate()
                last[bn] = g
                blob += struct.pack("<4h", *[max(-32767, min(32767, int(round(v * 32767)))) for v in (g.x, g.y, g.z, g.w)])
        report[act.name[3:]] = frames
    with open(ANIMS, "wb") as fh:
        fh.write(blob)
    return report, len(blob)


from mathutils import Quaternion

_stats, _size, _meta = export_game()
_clips, _asize = export_anims()
result = {"file": OUT, "bytes": _size, "clips": len(_clips), "clip_bytes": _asize, "meshes": sum(len(v) for v in _stats.values()),
          "per_category": {c: len(v) for c, v in _stats.items()},
          "tris_per_level": {c: [sum(m[1][k] for m in v if k < len(m[1])) for k in range(max(len(m[1]) for m in v))] for c, v in _stats.items()},
          "heaviest": {c: max(v, key=lambda m: m[1][0]) for c, v in _stats.items()},
          "detail": {c: {m[0]: m[1] for m in v} for c, v in _stats.items() if c in ("lmk", "bld", "car")},
          "meta": {k: [round(x, 2) for x in v] for k, v in _meta}}
