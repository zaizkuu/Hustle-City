"""People: body parts the game poses about their joints. Needs kit.py in front.

The engine has no skinned meshes or textures: it turns rigid parts about their joints (render/People.kt, driven by
the clips rig.py authors) and colors vertices. So a person is shaped parts, each with its origin on the joint it
turns about: head with a face, a torso in a collared shirt or a tee, hips with a belt, thighs, shins, feet in
shoes, upper arms, forearms and hands, plus hair, a beard, a tie, a skirt, an apron and an umbrella.

Every limb ends in a ball centred on its joint, so a bent knee or elbow stays closed, and every outline is a smooth
curve through its cross-sections (kit `resample`). Each part is built four times over, from full detail (0) down
to a few dozen triangles (3): the game picks by size on screen, so nobody is ever a crumpled version of the
full model.

Colors are slots the game fills in per person (shirt, trousers, skin, hair, an accent for tie or apron). A slot
material may carry a shade, which the game multiplies in: the placket a touch darker than the shirt, lips darker
than the skin. The figure faces -Y and stands 1.77 m tall at scale 1.
"""

PEOPLE = []
LEVELS_PPL = 4


def part(fn):
    # object names are global in Blender: the prefix keeps the body's umbrella apart from the one in the shop
    PEOPLE.append(("npc_" + fn.__name__, fn))
    return fn


def _slot(name, hexc, k, shade=1.0, kind="PLAIN"):
    n = "SLOT_" + name
    s = bpy.data.materials.get(n)
    if s is None:
        s = M(hexc, kind).copy()
        s.name = n
    r, g, b = [((hexc >> sh) & 255) * shade / 255 for sh in (16, 8, 0)]
    s.diffuse_color = (_lin(r), _lin(g), _lin(b), 1)
    bs = next((nd for nd in s.node_tree.nodes if nd.type == 'BSDF_PRINCIPLED'), None)
    if bs is not None:
        bs.inputs["Base Color"].default_value = (_lin(r), _lin(g), _lin(b), 1)
    s["hc_kind"] = "PLAIN"
    s["hc_slot"] = k
    s["hc_shade"] = int(round(shade * 255))
    s["hc_color"] = "{:06X}".format(hexc)
    return s


class Pal:
    """The materials one person is made of."""

    def __init__(self, shirt, shirt_d, pants, pants_d, skin, skin_d, lip, hair, accent):
        self.shirt, self.shirt_d, self.pants, self.pants_d = shirt, shirt_d, pants, pants_d
        self.skin, self.skin_d, self.lip, self.hair, self.accent = skin, skin_d, lip, hair, accent


SLOTS = Pal(_slot("SHIRT", 0xF2F2F2, 1, kind="FABRIC"), _slot("SHIRT_D", 0xF2F2F2, 1, .86, "FABRIC"), _slot("PANTS", 0x1F3A5F, 2, kind="FABRIC"),
            _slot("PANTS_D", 0x1F3A5F, 2, .82, "FABRIC"), _slot("SKIN", 0xE0AC69, 3), _slot("SKIN_D", 0xE0AC69, 3, .88), _slot("LIP", 0xE0AC69, 3, .72),
            _slot("HAIR", 0x8A5A2B, 4), _slot("ACCENT", 0x1F4E9C, 5, kind="FABRIC"))

BELT = M(0x3B2A20, "PLAIN")
BUCKLE = M(0xC9CED6, "PLAIN")
LEATHER = M(0x5A3A26, "PLAIN")
SOLE = M(0x1C1C1E, "PLAIN")
SOCK = M(0x2E3238, "PLAIN")
BUTTON = M(0xD5DBE2, "PLAIN")
EYE_W = M(0xF4F1EA, "PLAIN")
IRIS = M(0x3A2A20, "PLAIN")

# joints, in meters above the floor at scale 1 (rig.py builds the skeleton from these and the game reads it from
# the clip file, so they are written down once, here)
HIP = .88
WAIST = .98
KNEE = .46
ANKLE = .085
SHOULDER = 1.385
ELBOW = 1.085
WRIST = .835
NECK = 1.48
HIP_X = .09
SHOULDER_X = (.187, .16)    # men, women

MALE = [(0, 0, .0, .1, .074, .07), (0, 0, .06, .136, .096, .088), (0, 0, .11, .151, .106, .096), (0, 0, .16, .153, .108, .098),
        (0, 0, .28, .166, .115, .105), (0, 0, .4, .184, .123, .112), (0, 0, .48, .19, .118, .112), (0, 0, .535, .178, .104, .104),
        (0, .002, .562, .15, .092, .098), (0, .004, .584, .11, .08, .088), (0, .006, .603, .062, .06, .066)]
FEMALE = [(0, 0, .0, .094, .07, .066), (0, 0, .06, .122, .09, .084), (0, 0, .11, .132, .098, .092), (0, 0, .15, .126, .096, .09),
          (0, 0, .27, .14, .108, .098), (0, -.004, .39, .158, .128, .104), (0, -.002, .47, .164, .116, .106), (0, 0, .53, .154, .1, .1),
          (0, .002, .558, .13, .088, .094), (0, .004, .58, .098, .076, .084), (0, .006, .6, .058, .056, .064)]

# detail by level: how many sides a loft keeps, and how far apart its extra rings are (0: only the rings given)
_SIDES = (1.0, .62, .45, .3)
_FINE = (.032, .075, 0.0, 0.0)
_DOME = (4, 3, 1, 0)


def sides(n, q):
    return max(5, int(round(n * _SIDES[q])))


def thin(rings, q):
    """The far level keeps every other ring."""
    if q < 3 or len(rings) <= 3:
        return list(rings)
    out = list(rings[::2])
    if (len(rings) - 1) % 2:
        out.append(rings[-1])
    return out


def capped(rings, q, start=0.0, end=0.0):
    """Rounds the ends of a limb off into balls (heights along the axis, signed)."""
    n = _DOME[q]
    out = list(rings)
    if n and start:
        out = list(reversed(dome(rings[0], start, n))) + out
    if n and end:
        out = out + dome(rings[-1], end, n)
    return out


def skin_loft(a, rings, m, n, q, caps=True, axis='z', start=0.0, end=0.0):
    r = capped(thin(rings, q), q, start, end)
    a.loft(r, m, n=sides(n, q), axis=axis, caps=caps, fine=_FINE[q])
    return r


def front(rings, z):
    """How far forward (-Y) the chest reaches at height z."""
    for a, b in zip(rings, rings[1:]):
        if a[2] <= z <= b[2]:
            t = (z - a[2]) / max(b[2] - a[2], 1e-6)
            return (a[1] - a[4]) * (1 - t) + (b[1] - b[4]) * t
    r = rings[0] if z < rings[0][2] else rings[-1]
    return r[1] - r[4]


def strip(a, rings, zs, half, m, out=.004):
    """A band lying on the chest between heights zs, with half-widths `half` (one per height): plackets, ties."""
    for (z0, h0), (z1, h1) in zip(zip(zs, half), zip(zs[1:], half[1:])):
        y0 = front(rings, z0) - out
        y1 = front(rings, z1) - out
        if h0 <= 1e-4:
            pts = [(0, y0, z0), (h1, y1, z1), (-h1, y1, z1)]
        elif h1 <= 1e-4:
            pts = [(-h0, y0, z0), (h0, y0, z0), (0, y1, z1)]
        else:
            pts = [(-h0, y0, z0), (h0, y0, z0), (h1, y1, z1), (-h1, y1, z1)]
        a.face(pts, m, two_sided=True)


def band(lo, hi, step, widths):
    """Heights from lo to hi about `step` apart, with the half-width at each read off (height, half-width) pairs."""
    n = max(1, int(round(abs(hi - lo) / step)))
    zs = [lo + (hi - lo) * k / n for k in range(n + 1)]
    ws = []
    pts = sorted(widths)
    for z in zs:
        w = pts[0][1] if z <= pts[0][0] else pts[-1][1]
        for (z0, w0), (z1, w1) in zip(pts, pts[1:]):
            if z0 <= z <= z1:
                w = w0 + (w1 - w0) * (z - z0) / max(z1 - z0, 1e-6)
        ws.append(w)
    return zs, ws


def chest(female, q):
    """The torso's rings as the mesh at this level has them: what lies on the chest follows these."""
    r = thin(FEMALE if female else MALE, q)
    return resample(r, .012) if _FINE[q] > 0 else r


# ---------------------------------------------------------------------------------------------- body
# torso parts are built in the hips' frame and then dropped by the waist's height above them, so the part's
# origin is the waist joint

W0 = WAIST - HIP


def torso(a, P, q, female=False, tee=False):
    rings = FEMALE if female else MALE
    with a.at(0, 0, -W0):
        skin_loft(a, rings, P.shirt, 20, q)
        fr = chest(female, q)
        top = rings[-1]
        if tee:
            if q < 3:
                a.loft([(0, top[1], top[2] - .03, top[3] + .026, top[4] + .014, top[5] + .012), (0, top[1], top[2] + .004, top[3] + .008, top[4] + .008, top[5] + .008)],
                       P.shirt_d, n=sides(18, q), caps=False)
            return
        if q == 3:
            return
        # collar: a stand round the neck and two points folded down over the top button
        a.loft([(0, top[1], top[2] - .014, top[3] + .004, top[4] + .004, top[5] + .004), (0, top[1], top[2] + .02, top[3] - .004, top[4] - .004, top[5] - .003)],
               P.shirt, n=sides(18, q), caps=False)
        fy = top[1] - top[4]
        for sx in (-1, 1):
            a.face([(sx * .004, fy - .006, top[2] + .016), (sx * .058, fy + .022, top[2] + .014), (sx * .05, front(fr, top[2] - .05) - .006, top[2] - .05),
                    (sx * .008, front(fr, top[2] - .03) - .008, top[2] - .03)], P.shirt_d, two_sided=True)
        zs, ws = band(.1, .574, (.03, .06, .12)[q], [(.1, .013), (.574, .013)])
        strip(a, fr, zs, ws, P.shirt_d)
        if q < 2:
            for z in (.13, .22, .31, .4, .485):
                a.ico(0, front(fr, z) - .006, z, .0075, BUTTON, smooth=True, scale=(1, .5, 1))


@part
def torso_m_shirt(a, q):
    torso(a, SLOTS, q)


@part
def torso_m_tee(a, q):
    torso(a, SLOTS, q, tee=True)


@part
def torso_f_shirt(a, q):
    torso(a, SLOTS, q, female=True)


@part
def torso_f_tee(a, q):
    torso(a, SLOTS, q, female=True, tee=True)


def tie(a, P, q, female=False):
    fr = chest(female, q)
    with a.at(0, 0, -W0):
        fy = front(fr, .58)
        if q < 3:
            a.loft([(0, fy - .008, .548, .013, .01, .008), (0, fy - .012, .572, .02, .013, .008), (0, fy - .008, .596, .013, .01, .008)], P.accent, n=sides(10, q))
        zs, ws = band(.555, .125, (.03, .06, .11, .22)[q], [(.125, 0), (.17, .039), (.24, .035), (.31, .03), (.38, .025), (.44, .021), (.5, .017), (.555, .014)])
        strip(a, fr, zs, ws, P.accent, out=.016)


@part
def tie_m(a, q):
    tie(a, SLOTS, q)


def hips(a, P, q):
    skin_loft(a, [(0, .004, -.112, .06, .06, .074), (0, .004, -.07, .164, .108, .116), (0, .004, 0, .172, .114, .12), (0, .002, .06, .166, .112, .108),
                  (0, 0, .1, .156, .109, .099), (0, 0, .135, .153, .107, .098)], P.pants, 20, q)
    if q == 3:
        return
    a.loft([(0, 0, .088, .16, .113, .103), (0, 0, .128, .159, .112, .102)], BELT, n=sides(20, q), caps=False)
    a.box(-.024, -.122, .09, .024, -.108, .126, BUCKLE, bevel=.004 if q == 0 else 0)
    if q < 2:
        a.box(-.012, -.125, .099, .012, -.12, .117, BELT)
        for sx in (-1, 1):
            for sy in (-1, 1):
                y = sy * (.092 if sy < 0 else .084)
                a.box(sx * .1 - .008, y - .006, .082, sx * .1 + .008, y + .006, .134, P.pants_d)
        # fly
        a.face([(-.004, -.118, -.02), (.004, -.118, -.02), (.004, -.112, .08), (-.004, -.112, .08)], P.pants_d, two_sided=True)


@part
def hips_trousers(a, q):
    hips(a, SLOTS, q)


def skirt(a, P, q):
    skin_loft(a, [(0, 0, .135, .14, .104, .096), (0, 0, .1, .146, .106, .098), (0, .003, 0, .188, .126, .13), (0, .003, -.18, .204, .146, .146), (0, .003, -.35, .218, .168, .158)],
              P.pants, 20, q)
    if q < 3:
        a.loft([(0, 0, .09, .15, .109, .101), (0, 0, .13, .147, .108, .1)], P.pants_d, n=sides(20, q), caps=False)
        a.loft([(0, .003, -.335, .219, .169, .159), (0, .003, -.352, .22, .17, .16)], P.pants_d, n=sides(20, q), caps=False)


@part
def hips_skirt(a, q):
    skirt(a, SLOTS, q)


def thigh(a, P, q):
    skin_loft(a, [(0, 0, .03, .07, .078, .078), (0, 0, -.03, .084, .092, .096), (0, 0, -.2, .076, .08, .082), (0, 0, -.36, .062, .064, .066),
                  (0, 0, -.42, .056, .058, .058)], P.pants, 14, q, start=.045, end=-.052)


@part
def thigh_leg(a, q):
    thigh(a, SLOTS, q)


def shin(a, P, q):
    skin_loft(a, [(0, 0, 0, .056, .057, .058), (0, .004, -.12, .054, .05, .064), (0, .002, -.26, .044, .044, .047), (0, 0, -.33, .046, .049, .049),
                  (0, 0, -.352, .046, .049, .049)], P.pants, 14, q, start=.052)
    skin_loft(a, [(0, 0, -.34, .034, .036, .036), (0, 0, -.375, .034, .037, .037)], SOCK, 12, q, end=-.03)


@part
def shin_leg(a, q):
    shin(a, SLOTS, q)


def foot(a, P, q):
    z0 = -ANKLE + .016
    skin_loft(a, [(0, .078, z0, .028, 0, .04), (0, .045, z0, .04, 0, .078), (0, -.015, z0, .044, 0, .072), (0, -.085, z0, .047, 0, .052),
                  (0, -.155, z0, .045, 0, .037), (0, -.198, z0, .035, 0, .028), (0, -.222, z0, .016, 0, .013)], LEATHER, 14, q, axis='y')
    skin_loft(a, [(0, .083, z0, .032, .016, 0), (0, .045, z0, .044, .016, 0), (0, -.085, z0, .051, .016, 0), (0, -.155, z0, .049, .016, 0),
                  (0, -.2, z0, .039, .016, 0), (0, -.228, z0, .018, .016, 0)], SOLE, 12, q, axis='y')
    if q < 2:
        # the collar round the ankle, and laces
        a.loft([(0, .012, -.035, .04, .05, .044), (0, .012, .004, .039, .046, .043)], LEATHER, n=sides(12, q), caps=False)
        for y in (-.05, -.08, -.11):
            a.box(-.022, y - .005, z0 + .05 + (y + .05) * .28, .022, y + .005, z0 + .064 + (y + .05) * .28, SOLE)


@part
def foot_shoe(a, q):
    foot(a, SLOTS, q)


def upper_arm(a, P, q, short=False):
    if short:
        skin_loft(a, [(0, 0, 0, .05, .054, .054), (0, 0, -.06, .048, .052, .052), (0, 0, -.12, .046, .05, .05), (0, 0, -.135, .047, .051, .051)],
                  P.shirt, 14, q, start=.046)
        skin_loft(a, [(0, 0, -.125, .038, .042, .042), (0, 0, -.2, .037, .04, .04), (0, 0, -.3, .033, .035, .035)], P.skin, 12, q, end=-.034)
    else:
        skin_loft(a, [(0, 0, 0, .05, .054, .054), (0, 0, -.05, .048, .052, .052), (0, 0, -.14, .042, .046, .046), (0, 0, -.26, .037, .04, .04),
                      (0, 0, -.3, .036, .038, .038)], P.shirt, 14, q, start=.046, end=-.037)


@part
def upper_arm_long(a, q):
    upper_arm(a, SLOTS, q)


@part
def upper_arm_short(a, q):
    upper_arm(a, SLOTS, q, short=True)


def forearm(a, P, q, bare=False):
    m = P.skin if bare else P.shirt
    k = .9 if bare else 1.0
    skin_loft(a, [(0, 0, 0, .035 * k, .038 * k, .038 * k), (0, 0, -.08, .036 * k, .039 * k, .037 * k), (0, 0, -.2, .028 * k, .03 * k, .03 * k),
                  (0, 0, -.25, .024, .026, .026)], m, 12, q, start=.036 * k, end=-.02)
    if not bare and q < 3:
        a.loft([(0, 0, -.212, .031, .034, .034), (0, 0, -.25, .031, .034, .034)], P.shirt_d, n=sides(12, q))


@part
def forearm_long(a, q):
    forearm(a, SLOTS, q)


@part
def forearm_bare(a, q):
    forearm(a, SLOTS, q, bare=True)


def hand(a, P, q):
    # the palm turned in toward the leg, fingers together, thumb forward
    skin_loft(a, [(0, 0, .005, .02, .024, .024), (0, -.004, -.025, .016, .04, .034), (0, -.006, -.065, .015, .044, .036), (0, -.006, -.1, .013, .04, .034),
                  (0, -.004, -.145, .011, .034, .03), (0, -.002, -.172, .008, .024, .022)], P.skin, 12, q, start=.018, end=-.012)
    if q < 3:
        a.rod((0, -.034, -.035), (0, -.06, -.085), .012, P.skin, n=sides(8, q), r2=.009)
    if q < 2:
        a.sphere(0, -.061, -.087, .009, P.skin, seg=8, rings=5)
    if q == 0:
        for dy in (-.018, .002, .02):
            a.box(-.0125, dy - .0012, -.17, .0125, dy + .0012, -.11, P.skin_d)


@part
def hand_open(a, q):
    hand(a, SLOTS, q)


# ---------------------------------------------------------------------------------------------- head

SKULL = [(0, -.040, .038, .030, .028, .030), (0, -.028, .060, .052, .050, .050), (0, -.018, .090, .066, .070, .072), (0, -.012, .125, .072, .082, .088),
         (0, -.008, .160, .075, .088, .096), (0, -.004, .200, .076, .090, .100), (0, .000, .240, .070, .084, .096), (0, .004, .268, .052, .062, .074),
         (0, .006, .285, .026, .032, .040)]


def head(a, P, q):
    a.loft(thin([(0, .01, -.045, .05, .05, .056), (0, .008, .03, .046, .046, .052), (0, .004, .08, .047, .05, .056)], q), P.skin, n=sides(14, q), caps=False,
           fine=_FINE[q])
    skin_loft(a, SKULL, P.skin, 20, q)
    if q == 3:
        return
    # nose, ears
    a.loft([(0, -.098, .12, .016, .017, .01), (0, -.096, .15, .011, .014, .01), (0, -.092, .182, .008, .008, .008)], P.skin, n=sides(8, q), fine=_FINE[q] * .5)
    for sx in (-1, 1):
        a.ico(sx * .0765, .006, .158, .024, P.skin_d, sub=2 if q == 0 else 1, smooth=True, scale=(.4, .9, 1.4))
        # eye: white, iris, and the brow over it
        if q < 2:
            a.sphere(sx * .031, -.0835, .166, .0135, EYE_W, seg=sides(12, q), rings=sides(8, q), scale=(1.25, .45, .85))
        a.sphere(sx * .031, -.088, .166, .0072, IRIS, seg=sides(8, q), rings=max(3, sides(6, q) - 1), scale=(1, .5, 1))
        with a.at(sx * .031, -.0915, .19, ry=sx * 5):
            a.box(-.017, -.004, -.0035, .017, .004, .0035, P.hair, bevel=.002 if q == 0 else 0)
    a.box(-.019, -.0865, .0895, .019, -.079, .0965, P.lip, bevel=.0025 if q == 0 else 0)
    if q < 2:
        a.box(-.013, -.085, .083, .013, -.078, .0895, P.lip, bevel=.002 if q == 0 else 0)


@part
def head_face(a, q):
    head(a, SLOTS, q)


def hair(a, P, q, style):
    h = P.hair
    if style == 0:
        # short back and sides, swept up in front
        skin_loft(a, [(0, .045, .1, .05, .018, .05), (0, .03, .15, .078, .03, .078), (0, .008, .195, .081, .078, .104), (0, .0, .24, .075, .09, .1),
                      (0, .004, .272, .057, .068, .08), (0, .006, .292, .028, .034, .044)], h, 20, q)
        if q < 3:
            with a.at(.008, -.062, .262, rz=-8):
                a.loft([(0, 0, -.03, .05, .02, .03), (0, -.006, 0, .056, .03, .034), (0, -.004, .026, .04, .022, .026), (0, 0, .04, .016, .01, .012)], h,
                       n=sides(12, q), fine=_FINE[q])
    elif style == 1:
        # a bob to the collar, with a fringe
        skin_loft(a, [(0, .04, -.02, .07, .012, .06), (0, .04, .06, .088, .016, .078), (0, .028, .14, .088, .034, .092), (0, .006, .2, .084, .09, .108),
                      (0, .0, .242, .077, .094, .102), (0, .004, .274, .058, .07, .082), (0, .006, .294, .028, .034, .044)], h, 20, q)
        if q < 3:
            a.loft([(0, -.06, .196, .07, .034, .02), (0, -.058, .225, .064, .034, .022), (0, -.046, .25, .046, .03, .02)], h, n=sides(12, q), fine=_FINE[q])
    elif style == 2:
        # pulled back into a bun
        skin_loft(a, [(0, .04, .11, .054, .014, .05), (0, .028, .16, .078, .028, .08), (0, .008, .2, .08, .082, .103), (0, .0, .24, .074, .088, .099),
                      (0, .004, .27, .055, .066, .078), (0, .006, .289, .027, .033, .042)], h, 20, q)
        a.sphere(0, .098, .262, .05, h, seg=sides(14, q), rings=max(3, sides(9, q) - 1), scale=(1, .9, 1))
        if q < 2:
            a.torus(0, .07, .25, .03, .008, P.accent, n=sides(12, q), k=4, axis='y')
    elif style == 3:
        # full and curly
        a.ico(0, .024, .228, .128, h, sub=(3, 2, 1, 1)[q], smooth=True, scale=(.98, .96, .92))
        if q < 3:
            for sx in (-1, 1):
                a.ico(sx * .07, .03, .15, .05, h, sub=(2, 1, 1)[q], smooth=True, scale=(.6, 1.1, 1.1))
    elif style == 4:
        # cropped close
        skin_loft(a, [(0, .045, .11, .05, .012, .046), (0, .03, .16, .0765, .024, .074), (0, .008, .2, .0785, .08, .1005), (0, .0, .24, .0725, .0865, .0985),
                      (0, .004, .268, .0545, .0645, .0765), (0, .006, .2865, .027, .033, .041)], h, 20, q)
    else:
        # long, over the shoulders
        skin_loft(a, [(0, .05, -.12, .074, .01, .05), (0, .046, -.04, .09, .014, .064), (0, .04, .06, .092, .018, .08), (0, .028, .14, .089, .034, .093),
                      (0, .006, .2, .084, .088, .108), (0, .0, .242, .077, .092, .102), (0, .004, .274, .058, .068, .082), (0, .006, .294, .028, .034, .044)], h, 20, q)
        if q < 3:
            for sx in (-1, 1):
                a.loft([(sx * .07, -.03, -.06, .016, .02, .03), (sx * .078, -.036, .04, .018, .024, .034), (sx * .076, -.04, .14, .016, .026, .03),
                        (sx * .06, -.05, .215, .02, .03, .026)], h, n=sides(10, q), fine=_FINE[q])


def _hair(k):
    def fn(a, q):
        hair(a, SLOTS, q, k)
    fn.__name__ = "hair{:d}".format(k)
    return part(fn)


HAIR_STYLES = 6
for _k in range(HAIR_STYLES):
    _hair(_k)


def beard(a, P, q):
    skin_loft(a, [(0, -.036, .03, .034, .03, .028), (0, -.03, .058, .056, .054, .034), (0, -.024, .09, .0695, .0715, .034), (0, -.02, .118, .0745, .073, .03),
                  (0, -.02, .135, .075, .05, .026)], P.hair, 16, q)
    if q < 3:
        a.box(-.024, -.092, .098, .024, -.084, .106, P.hair, bevel=.003 if q == 0 else 0)


@part
def beard_full(a, q):
    beard(a, SLOTS, q)


# ---------------------------------------------------------------------------------------------- worn and carried

def apron(a, P, q, female=False):
    fr = chest(female, q)
    with a.at(0, 0, -W0):
        zs, ws = band(.2, .5, (.03, .06, .15, .3)[q], [(.2, .13), (.3, .125), (.4, .115), (.5, .1)])
        strip(a, fr, zs, ws, P.accent, out=.01)
        a.extrude([(-.17, -.42), (.17, -.42), (.16, .2), (-.16, .2)], front(fr, .2) - .022, front(fr, .2) - .008, P.accent, axis='y')
        if q < 3:
            a.torus(0, 0, .585, .075, .01, P.accent, n=sides(14, q), k=4)
            a.box(-.07, front(fr, .2) - .03, -.12, .07, front(fr, .2) - .02, 0, M(0x22303D, "PLAIN"))


@part
def apron_front(a, q):
    apron(a, SLOTS, q)


def umbrella(a, P, q):
    n = (12, 8, 8, 6)[q]
    a.cyl(0, 0, 0, .95, .012, M(0x2B2B2B, "PLAIN"), n=sides(8, q))
    a.lathe(0, 0, [(.56, 0), (.4, .13), (.2, .21), (0, .24)], P.accent, n=n, z=.74, smooth=False)
    if q < 3:
        a.lathe(0, 0, [(.56, 0), (.4, .11), (.2, .185), (0, .21)], P.accent, n=n, z=.735, smooth=False)
        a.cyl(0, 0, .95, 1.02, .008, M(0x2B2B2B, "PLAIN"), n=5)
        a.torus(.03, 0, -.02, .03, .011, M(0x5D4037, "PLAIN"), n=sides(10, q), k=4, axis='y')


@part
def umbrella_open(a, q):
    umbrella(a, SLOTS, q)


# ---------------------------------------------------------------------------------------------- the library

def build_people():
    """Full-detail parts go in People; their lighter versions, named <part>.L1 to .L3, in People LOD."""
    coll = category("People")
    lod = category("People LOD")
    obs = []
    for name, fn in PEOPLE:
        for q in range(LEVELS_PPL):
            a = Asset(name if q == 0 else "{:s}.L{:d}".format(name, q))
            fn(a, q)
            ob = a.finish(coll if q == 0 else lod)
            if q == 0:
                obs.append(ob)
            else:
                ob.hide_render = True
    lod.hide_viewport = True
    return obs
