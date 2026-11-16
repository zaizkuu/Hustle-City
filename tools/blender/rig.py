"""The people's skeleton and their animation clips. Needs kit.py and people.py in front.

A person is rigid parts on fifteen bones (hips, torso, head, and per side upper arm, forearm, hand, thigh, shin,
foot). Clips are written here as functions of time, baked into Blender Actions on the "HC Rig" armature (so they
can be played, looked at and edited in Blender), and export_game.py writes the Actions out for the game, which
plays them on the same parts (render/People.kt). The trailer's cast (tools/blender/trailer) is made of the same
rig and clips.

Angles are in degrees, in the figure's own axes (it faces -Y, +X is its left, +Z up):
  pitch  the part swings forward about its joint (a leg or arm forward, toes up, the head or torso tipping back)
  roll   a limb lifts out sideways, away from the body; hips, torso and head lean to the figure's left
  yaw    turns to the figure's left
"""

FPS = 30

SX_M, SX_F = SHOULDER_X
BONES = (
    # name, parent, joint (x, y, z), where the bone points
    ("hips", None, (0, 0, HIP), (0, 0, .1)),
    ("torso", "hips", (0, 0, WAIST), (0, 0, NECK - WAIST)),
    ("head", "torso", (0, 0, NECK), (0, 0, .2)),
    ("upper_arm.L", "torso", (SX_M, 0, SHOULDER), (0, 0, ELBOW - SHOULDER)),
    ("forearm.L", "upper_arm.L", (SX_M, 0, ELBOW), (0, 0, WRIST - ELBOW)),
    ("hand.L", "forearm.L", (SX_M, 0, WRIST), (0, 0, -.1)),
    ("upper_arm.R", "torso", (-SX_M, 0, SHOULDER), (0, 0, ELBOW - SHOULDER)),
    ("forearm.R", "upper_arm.R", (-SX_M, 0, ELBOW), (0, 0, WRIST - ELBOW)),
    ("hand.R", "forearm.R", (-SX_M, 0, WRIST), (0, 0, -.1)),
    ("thigh.L", "hips", (HIP_X, 0, HIP), (0, 0, KNEE - HIP)),
    ("shin.L", "thigh.L", (HIP_X, 0, KNEE), (0, 0, ANKLE - KNEE)),
    ("foot.L", "shin.L", (HIP_X, 0, ANKLE), (0, -.15, 0)),
    ("thigh.R", "hips", (-HIP_X, 0, HIP), (0, 0, KNEE - HIP)),
    ("shin.R", "thigh.R", (-HIP_X, 0, KNEE), (0, 0, ANKLE - KNEE)),
    ("foot.R", "shin.R", (-HIP_X, 0, ANKLE), (0, -.15, 0)),
)
BONE_NAMES = [b[0] for b in BONES]
PARENT = {b[0]: b[1] for b in BONES}
REST = {b[0]: Vector(b[2]) for b in BONES}
# sole of the shoe, in the foot's own frame: heel, ball, toe
SOLE_PTS = (Vector((0, .08, -ANKLE)), Vector((0, -.12, -ANKLE)), Vector((0, -.225, -ANKLE)))


def skeleton(female=False):
    """The armature data, made once per build: women's shoulders sit closer in."""
    name = "HC Skeleton F" if female else "HC Skeleton M"
    old = bpy.data.armatures.get(name)
    if old is not None and len(old.bones) == len(BONES):
        return old
    arm = bpy.data.armatures.new(name)
    ob = bpy.data.objects.new("hc_rig_tmp", arm)
    bpy.context.scene.collection.objects.link(ob)
    view = bpy.context.view_layer
    view.update()
    for o in list(view.objects):
        if o is not None:
            o.select_set(False)
    view.objects.active = ob
    ob.select_set(True)
    bpy.ops.object.mode_set(mode='EDIT')
    k = SX_F / SX_M if female else 1.0
    made = {}
    for name_b, parent, joint, aim in BONES:
        b = arm.edit_bones.new(name_b)
        x = joint[0] * (k if "arm" in name_b or "hand" in name_b else 1.0)
        b.head = (x, joint[1], joint[2])
        b.tail = (x + aim[0], joint[1] + aim[1], joint[2] + aim[2])
        b.roll = 0
        if parent:
            b.parent = made[parent]
        made[name_b] = b
    bpy.ops.object.mode_set(mode='OBJECT')
    bpy.data.objects.remove(ob, do_unlink=True)
    arm.display_type = 'STICK'
    return arm


# ---------------------------------------------------------------------------------------------- poses

def rot(pitch=0.0, roll=0.0, yaw=0.0):
    """A joint's turn in the figure's axes: sideways first, then forward, then round the vertical."""
    return (Matrix.Rotation(math.radians(yaw), 3, 'Z') @ Matrix.Rotation(math.radians(-pitch), 3, 'X') @ Matrix.Rotation(math.radians(roll), 3, 'Y'))


class Pose:
    """One moment of the body: a turn for every bone and where the hips are."""

    def __init__(self):
        self.r = {n: Matrix.Identity(3) for n in BONE_NAMES}
        self.root = Vector((0, 0, 0))    # offset of the hips from standing
        self.grounded = True              # feet rest on the floor: the baker sets the hips' height from them

    def set(self, bone, pitch=0.0, roll=0.0, yaw=0.0):
        self.r[bone] = rot(pitch, roll, yaw)
        return self

    def arm(self, side, fwd=0.0, out=0.0, twist=0.0, elbow=0.0, elbow_out=0.0, wrist=0.0, elbow_twist=0.0):
        """side 'L' or 'R'; out lifts the arm away from the body, twist turns it inward, elbow bends it forward."""
        s = -1.0 if side == 'L' else 1.0
        self.r["upper_arm." + side] = rot(fwd, s * out, s * twist)
        self.r["forearm." + side] = rot(elbow, s * elbow_out, s * elbow_twist)
        self.r["hand." + side] = rot(wrist, 0, 0)
        return self

    def leg(self, side, fwd=0.0, knee=0.0, foot=0.0, out=0.0, twist=0.0):
        """knee bends the shin back; foot lifts the toes."""
        s = -1.0 if side == 'L' else 1.0
        self.r["thigh." + side] = rot(fwd, s * out, s * twist)
        self.r["shin." + side] = rot(-knee, 0, 0)
        self.r["foot." + side] = rot(foot, 0, 0)
        return self

    def flat_feet(self):
        """Turns each foot so its sole is level with the floor, whatever the leg above it is doing."""
        w = fk(self)
        for side in "LR":
            R = w["shin." + side][0]
            # the sole's forward direction should stay level: undo the shin's pitch
            f = R @ Vector((0, -1, 0))
            self.r["foot." + side] = rot(math.degrees(math.atan2(-f.z, math.hypot(f.x, f.y))), 0, 0)
        return self


def fk(pose, female=False):
    """bone -> (turn, joint position) in the figure's frame."""
    k = SX_F / SX_M if female else 1.0
    out = {}
    for name, parent, joint, aim in BONES:
        rest = Vector(joint)
        if "arm" in name or "hand" in name:
            rest.x *= k
        if parent is None:
            out[name] = (pose.r[name].copy(), rest + pose.root)
        else:
            pr, pj = out[parent]
            prest = Vector(REST[parent])
            if "arm" in parent or "hand" in parent:
                prest.x *= k
            out[name] = (pr @ pose.r[name], pj + pr @ (rest - prest))
    return out


def ground(pose):
    """Lifts or lowers the hips so the lower foot just stands on the floor."""
    w = fk(pose)
    low = min((w["foot." + s][1] + w["foot." + s][0] @ p).z for s in "LR" for p in SOLE_PTS)
    pose.root.z -= low
    return pose


def smooth(u):
    u = min(1.0, max(0.0, u))
    return u * u * (3 - 2 * u)


def cyc(t, keys):
    """A value that eases from key to key round a loop: keys are (time 0..1, value)."""
    t %= 1.0
    ks = sorted(keys)
    ks = [(ks[-1][0] - 1, ks[-1][1])] + ks + [(ks[0][0] + 1, ks[0][1])]
    for (t0, v0), (t1, v1) in zip(ks, ks[1:]):
        if t0 <= t <= t1:
            return v0 + (v1 - v0) * smooth((t - t0) / max(t1 - t0, 1e-6))
    return ks[0][1]


def ease(t, keys):
    """The same without the loop: holds the first value before, the last after."""
    ks = sorted(keys)
    if t <= ks[0][0]:
        return ks[0][1]
    for (t0, v0), (t1, v1) in zip(ks, ks[1:]):
        if t0 <= t <= t1:
            return v0 + (v1 - v0) * smooth((t - t0) / max(t1 - t0, 1e-6))
    return ks[-1][1]


def S(t, cycles=1.0, shift=0.0):
    return math.sin(2 * math.pi * (t * cycles + shift))


# ---------------------------------------------------------------------------------------------- clips
# each takes t from 0 to 1 over the clip and returns a Pose

CLIPS = []


def clip(seconds, fps=15, loop=True):
    def reg(fn):
        CLIPS.append((fn.__name__, seconds, fps, loop, fn))
        return fn
    return reg


def breathe(p, t, cycles, amount=1.0):
    b = S(t, cycles)
    p.set("torso", pitch=.9 * b * amount)
    return b


def stride(p, t, reach=26.0, flex=58.0, lean=-3.0, arms=True, swing=24.0, elbow=16.0, twist=1.0):
    """One walking cycle: the left leg is furthest forward at a quarter of the way through."""
    for side, ph in (("L", 0.0), ("R", .5)):
        u = t + ph
        thigh = reach * S(u) + 4
        # the knee folds as the leg swings through and gives a little as the foot takes the weight
        knee = 5 + flex * max(0.0, math.cos(2 * math.pi * u)) ** 1.4 + 9 * math.exp(-((((u - .36 + .5) % 1.0) - .5) ** 2) / .004)
        p.leg(side, fwd=thigh, knee=knee)
        # the foot: heel first, flat through the stance, heel peeling up before the toe leaves, relaxed in the air
        level = -(thigh - knee)
        world = cyc(u, [(.25, 16), (.34, 0), (.6, 0), (.75, -32), (.95, -12), (.12, 4)])
        p.r["foot." + side] = rot(level + world)
    p.set("hips", yaw=-5 * twist * S(t), roll=2.2 * math.cos(2 * math.pi * t))
    p.root.x = -.018 * math.cos(2 * math.pi * t)
    p.set("torso", pitch=lean, yaw=9 * twist * S(t), roll=-2.2 * math.cos(2 * math.pi * t))
    p.set("head", yaw=-4 * twist * S(t), pitch=-lean * .6)
    if arms:
        for side, ph in (("L", .5), ("R", 0.0)):
            a = S(t + ph)
            p.arm(side, fwd=swing * a + 2, out=5, elbow=elbow + 12 * (a + 1) / 2, wrist=8)
    return p


@clip(1.0, fps=30)
def walk(t):
    return stride(Pose(), t)


@clip(.7, fps=30)
def run(t):
    p = Pose()
    for side, ph in (("L", 0.0), ("R", .5)):
        u = t + ph
        thigh = 38 * S(u) + 14
        knee = 22 + 78 * max(0.0, math.cos(2 * math.pi * (u + .04))) ** 1.2
        p.leg(side, fwd=thigh, knee=knee)
        level = -(thigh - knee)
        p.r["foot." + side] = rot(level + cyc(u, [(.27, 6), (.4, -4), (.62, -8), (.76, -40), (.98, -22), (.14, -4)]))
    p.set("hips", yaw=-8 * S(t), pitch=-4)
    p.set("torso", pitch=-9, yaw=16 * S(t))
    p.set("head", yaw=-8 * S(t), pitch=9)
    for side, ph in (("L", .5), ("R", 0.0)):
        a = S(t + ph)
        p.arm(side, fwd=38 * a - 4, out=8, twist=10, elbow=88 + 10 * a, wrist=10)
    return p


def stand(p, t, shift=1.0):
    """Standing at ease: weight drifting from one leg to the other, breathing."""
    w = S(t, 1) * shift
    p.root.x = .012 * w
    p.set("hips", roll=1.6 * w)
    for side, sg in (("L", -1), ("R", 1)):
        p.leg(side, fwd=1.5, knee=3 + 2.5 * max(0.0, sg * w), out=1.5 + sg * -.9 * w)
    return p


@clip(6.0, fps=10)
def idle(t):
    p = stand(Pose(), t)
    b = breathe(p, t, 2)
    p.r["torso"] = rot(.9 * b - 1, -1.2 * S(t), 2 * S(t, 1, .1))
    p.set("head", yaw=7 * S(t, 1, .3) + 3 * S(t, 2), pitch=1.5 * S(t, 2, .2))
    p.arm("L", fwd=3 + 1.5 * b, out=5 + .6 * b, elbow=9 + 2 * S(t, 1, .2), wrist=6)
    p.arm("R", fwd=-2 + 1.5 * b, out=5 + .6 * b, elbow=7 + 2 * S(t, 1, .6), wrist=6)
    return p.flat_feet()


@clip(8.0, fps=10)
def idle_look(t):
    """Waiting: looks one way up the street, then the other, then at a wrist watch."""
    p = stand(Pose(), t, .7)
    b = breathe(p, t, 3)
    look = ease(t, [(0, 0), (.1, 38), (.24, 38), (.36, -34), (.5, -34), (.6, 0), (.66, 6), (.86, 6), (.94, 0), (1, 0)])
    watch = ease(t, [(.58, 0), (.68, 1), (.86, 1), (.95, 0)])
    p.r["torso"] = rot(.9 * b - 1 - 3 * watch, 0, look * .22)
    p.set("head", yaw=look * .7, pitch=-22 * watch + 2 * S(t, 2))
    p.arm("L", fwd=3 + 34 * watch, out=5 + 4 * watch, twist=55 * watch, elbow=9 + 96 * watch, wrist=6)
    p.arm("R", fwd=-2 + b, out=5, elbow=8, wrist=6)
    return p.flat_feet()


@clip(6.0, fps=10)
def idle_phone(t):
    """Head down over a phone held in the right hand, thumb at work, a glance up now and then."""
    p = stand(Pose(), t, .5)
    b = breathe(p, t, 2)
    up = ease(t, [(.55, 0), (.62, 1), (.74, 1), (.82, 0)])
    p.r["torso"] = rot(.9 * b - 4, 0, 2 * S(t))
    p.set("head", pitch=-26 + 24 * up, yaw=-6 + 20 * up * S(t, 1, .1))
    p.arm("R", fwd=18, out=4, twist=32, elbow=104 - 8 * up + 1.5 * S(t, 6), wrist=-6 + 4 * S(t, 9))
    p.arm("L", fwd=2 + b, out=5, elbow=10, wrist=6)
    return p.flat_feet()


def seated(p, t, lean=4.0):
    p.grounded = False
    p.root.z = .09 - HIP
    for side in "LR":
        p.leg(side, fwd=87, knee=86, out=4)
    p.set("hips", pitch=0)
    b = S(t, 2)
    p.set("torso", pitch=lean + .8 * b)
    return b


@clip(6.0, fps=10)
def sit(t):
    p = Pose()
    b = seated(p, t)
    p.set("head", yaw=10 * S(t, 1, .2), pitch=-3 + 2 * S(t, 2, .3))
    for side, k in (("L", 0.0), ("R", .4)):
        p.arm(side, fwd=24 + b, out=6, twist=12, elbow=52 + 2 * S(t, 1, k), wrist=20)
    p.leg("R", fwd=87, knee=86 + 5 * S(t, 1), out=4)
    return p.flat_feet()


@clip(5.0, fps=12)
def sit_eat(t):
    """At a table: the right hand goes to the mouth and back, the left rests on the table."""
    p = Pose()
    seated(p, t, lean=-5)
    up = ease(t, [(.1, 0), (.26, 1), (.42, 1), (.56, 0), (1, 0)])
    p.set("torso", pitch=-5 - 4 * up, yaw=-3 * up)
    p.set("head", pitch=-8 - 6 * up + 3 * S(t, 3) * (1 - up), yaw=6 * S(t, 1) * (1 - up))
    p.arm("R", fwd=30 + 26 * up, out=10 + 8 * up, twist=30 + 22 * up, elbow=62 + 74 * up, wrist=10 - 20 * up)
    p.arm("L", fwd=34, out=8, twist=28, elbow=58, wrist=24)
    return p.flat_feet()


@clip(2.0, fps=30)
def dance_a(t):
    """Arms in the air, bouncing on the beat (four beats to the loop)."""
    p = Pose()
    beat = abs(S(t, 2))
    sway = S(t, 1)
    p.root.x = .04 * sway
    p.set("hips", roll=5 * sway, yaw=8 * S(t, 1, .25))
    for side, sg in (("L", -1), ("R", 1)):
        p.leg(side, fwd=10 + 10 * beat, knee=14 + 26 * beat, out=5 + 3 * sg * sway)
        p.arm(side, fwd=150 + 12 * S(t, 2, .1) + 8 * sg * sway, out=22 - 10 * sg * sway, elbow=-34 + 22 * S(t, 2, .25 + .25 * sg), wrist=-10)
    p.set("torso", pitch=-2 + 3 * beat, roll=-6 * sway, yaw=-10 * S(t, 1, .25))
    p.set("head", roll=5 * sway, pitch=4 - 8 * beat)
    return ground(p.flat_feet())


@clip(2.0, fps=30)
def dance_b(t):
    """A two-step: weight side to side, elbows pumping at the chest, hips twisting."""
    p = Pose()
    beat = abs(S(t, 2))
    sway = S(t, 1)
    p.root.x = .07 * sway
    p.set("hips", roll=4 * sway, yaw=16 * S(t, 2, .12))
    for side, sg in (("L", -1), ("R", 1)):
        lift = max(0.0, -sg * sway)          # the foot away from the weight comes up
        p.leg(side, fwd=8 + 22 * lift, knee=12 + 18 * beat + 30 * lift, out=6 + 6 * lift)
        pump = S(t, 2, .25 if sg > 0 else .75)
        p.arm(side, fwd=28 + 16 * pump, out=24, twist=38, elbow=96 + 18 * pump, wrist=10)
    p.set("torso", pitch=-4, roll=-5 * sway, yaw=-22 * S(t, 2, .12))
    p.set("head", pitch=-4 + 7 * beat, yaw=8 * S(t, 2, .12), roll=4 * sway)
    return ground(p.flat_feet())


def hold_box(p):
    for side in "LR":
        p.arm(side, fwd=36, out=7, twist=18, elbow=68, wrist=-18)
    return p


@clip(1.0, fps=30)
def carry(t):
    """Walking with a box in both arms: a shorter stride, leaning back against the weight."""
    p = stride(Pose(), t, reach=20, flex=46, lean=5, arms=False, twist=.4)
    return hold_box(p)


@clip(4.0, fps=10)
def carry_idle(t):
    p = stand(Pose(), t, .6)
    b = S(t, 2)
    p.set("torso", pitch=4 + .8 * b)
    p.set("head", yaw=6 * S(t, 1, .2))
    return hold_box(p).flat_feet()


def hold_handle(p):
    for side in "LR":
        p.arm(side, fwd=50, out=4, twist=14, elbow=36, wrist=-30)
    return p


@clip(1.0, fps=30)
def push(t):
    """Walking behind a hand truck or cart: leaning into it, hands on the handle."""
    p = stride(Pose(), t, reach=22, flex=50, lean=-9, arms=False, twist=.3)
    p.set("head", pitch=7)
    return hold_handle(p)


@clip(4.0, fps=10)
def push_idle(t):
    p = stand(Pose(), t, .5)
    p.set("torso", pitch=-5 + .8 * S(t, 2))
    p.set("head", pitch=4, yaw=8 * S(t, 1, .3))
    return hold_handle(p).flat_feet()


@clip(3.0, fps=15)
def wave(t):
    """Hailing: the right arm up, the hand waving."""
    p = stand(Pose(), t, .4)
    up = ease(t, [(0, 0), (.14, 1), (.82, 1), (1, 0)])
    w = S(t, 6) * up
    p.set("torso", roll=3 * up, yaw=-5 * up, pitch=-1)
    p.set("head", yaw=-6 * up, pitch=6 * up)
    p.arm("R", fwd=12 * up, out=5 + 96 * up, twist=-10 * up, elbow_out=(72 + 22 * w) * up, elbow=8, wrist=6)
    p.arm("L", fwd=3, out=5, elbow=10, wrist=6)
    return p.flat_feet()


@clip(6.0, fps=12)
def talk(t):
    """In conversation: the hands do half the talking."""
    p = stand(Pose(), t, .6)
    g = ease(t, [(0, 0), (.08, 1), (.3, .4), (.42, 1), (.6, 0), (.7, .8), (.86, .2), (1, 0)])
    h = ease(t, [(0, .2), (.2, 0), (.36, .9), (.5, .3), (.66, 1), (.8, .5), (1, .2)])
    p.set("torso", pitch=-2 + S(t, 2), yaw=4 * S(t, 1))
    p.set("head", pitch=3 * S(t, 5) * g, yaw=6 * S(t, 1, .2), roll=3 * S(t, 2))
    p.arm("R", fwd=8 + 22 * g, out=8 + 10 * g, twist=30 * g, elbow=20 + 70 * g, wrist=-14 * g + 6)
    p.arm("L", fwd=6 + 18 * h, out=8 + 8 * h, twist=26 * h, elbow=18 + 62 * h, wrist=-12 * h + 6)
    return p.flat_feet()


@clip(4.0, fps=15)
def work(t):
    """Behind a counter: takes an item on the left, passes it over the scanner, sets it down on the right."""
    p = stand(Pose(), t, .3)
    x = S(t, 2)                       # hands travel left to right and back, twice
    lift = abs(S(t, 2, .125))
    p.set("torso", pitch=-6, yaw=9 * x)
    p.set("head", pitch=-14 + 6 * lift, yaw=7 * x)
    p.arm("R", fwd=30 + 10 * lift, out=10 - 8 * x, twist=24 + 18 * x, elbow=72 + 10 * lift, wrist=-8)
    p.arm("L", fwd=28 + 8 * lift, out=10 + 8 * x, twist=24 - 18 * x, elbow=70 + 8 * lift, wrist=-8)
    return p.flat_feet()


@clip(1.4, fps=30, loop=False)
def reach(t):
    """A shopper takes something off a shelf: the right hand goes out, closes, and comes back."""
    p = stand(Pose(), 0, 0)
    out = ease(t, [(0, 0), (.42, 1), (.58, 1), (1, 0)])
    p.set("torso", pitch=-7 * out - 1, yaw=10 * out)
    p.set("head", pitch=-6 * out, yaw=6 * out)
    p.arm("R", fwd=4 + 78 * out, out=5 + 6 * out, twist=18 * out, elbow=12 + 24 * out, wrist=6 - 16 * out)
    p.arm("L", fwd=3 - 6 * out, out=5, elbow=10, wrist=6)
    return p.flat_feet()


@clip(1.0, fps=30)
def cycle(t):
    """On an exercise bike: leaning to the bars, legs going round."""
    p = Pose()
    p.grounded = False
    p.root.z = .09 - HIP
    p.set("hips", pitch=-8)
    p.set("torso", pitch=-22 + 1.5 * S(t, 2))
    p.set("head", pitch=20)
    for side, ph in (("L", 0.0), ("R", .5)):
        u = t + ph
        p.leg(side, fwd=58 + 22 * S(u), knee=74 - 30 * S(u, 1, .25), foot=-10 + 14 * S(u, 1, .1), out=3)
        p.arm(side, fwd=62, out=6, twist=10, elbow=18, wrist=-28)
    return p


@clip(2.4, fps=20)
def row(t):
    """On a rowing machine: legs drive, the back opens, the arms pull through."""
    p = Pose()
    p.grounded = False
    d = cyc(t, [(0, 0), (.42, 1), (.52, 1), (1, 0)])      # 0 at the catch, 1 at the finish
    p.root.z = .09 - HIP
    p.root.y = .26 * d
    p.set("torso", pitch=-22 + 40 * smooth(d * 1.2 - .1))
    p.set("head", pitch=6 - 10 * d)
    pull = smooth(d * 1.6 - .6)
    for side in "LR":
        p.leg(side, fwd=96 - 62 * d, knee=118 - 96 * d, foot=18 - 12 * d, out=5)
        p.arm(side, fwd=68 - 62 * pull - 22 * d, out=8 + 10 * pull, twist=12, elbow=6 + 96 * pull, wrist=-6)
    return p


@clip(2.6, fps=20)
def lift(t):
    """Seated on a bench, pressing dumbbells overhead."""
    p = Pose()
    seated(p, t, lean=2)
    up = cyc(t, [(0, 0), (.38, 1), (.5, 1), (.9, 0)])
    p.set("head", pitch=4 * up)
    for side in "LR":
        p.arm(side, fwd=6, out=82 + 72 * up, twist=0, elbow_out=84 - 70 * up, wrist=0)
        p.leg(side, fwd=87, knee=86, out=9)
    return p.flat_feet()


@clip(1.0, fps=5)
def hold(t):
    """An umbrella's handle in the right fist (the game takes only the right arm from this)."""
    p = stand(Pose(), 0, 0)
    p.arm("R", fwd=-8, out=6, twist=20, elbow=102, wrist=4)
    p.arm("L", fwd=3, out=5, elbow=10, wrist=6)
    return p.flat_feet()


@clip(1.0, fps=5)
def hang(t):
    """A bag hanging from the right hand: the arm straight, hardly swinging (the right arm only)."""
    p = stand(Pose(), 0, 0)
    p.arm("R", fwd=2, out=7, elbow=5, wrist=4)
    p.arm("L", fwd=3, out=5, elbow=10, wrist=6)
    return p.flat_feet()


# ---------------------------------------------------------------------------------------------- baking

def rig_object(name="HC Rig", female=False, coll=None):
    ob = bpy.data.objects.get(name)
    if ob is None or ob.type != 'ARMATURE':
        ob = bpy.data.objects.new(name, skeleton(female))
        (coll or category("People Rig")).objects.link(ob)
        bpy.context.view_layer.update()     # an armature object has no pose until it has been evaluated once
    ob.show_in_front = True
    return ob


def rest_of(arm):
    return {b.name: b.matrix_local.to_3x3() for b in arm.bones}


def apply_pose(ob, pose, rest=None, prev=None, frame=None):
    """Puts a Pose on an armature object (and keys it at `frame`). `prev` keeps quaternions on the same side."""
    rest = rest or rest_of(ob.data)
    for name in BONE_NAMES:
        pb = ob.pose.bones[name]
        pb.rotation_mode = 'QUATERNION'
        r = rest[name]
        q = (r.inverted() @ pose.r[name] @ r).to_quaternion()
        if prev is not None:
            if name in prev and prev[name].dot(q) < 0:
                q.negate()
            prev[name] = q.copy()
        pb.rotation_quaternion = q
        if name == "hips":
            pb.location = r.inverted() @ pose.root
        if frame is not None:
            pb.keyframe_insert("rotation_quaternion", frame=frame)
            if name == "hips":
                pb.keyframe_insert("location", frame=frame)


def clip_pose(fn, t):
    p = fn(t)
    if p.grounded:
        ground(p)
    return p


def bind_action(ob, act):
    """Makes the object play the action (Blender 5 actions have slots: take the first that fits)."""
    ad = ob.animation_data or ob.animation_data_create()
    ad.action = act
    try:
        if act is not None and ad.action_slot is None and len(act.slots):
            ad.action_slot = act.slots[0]
    except Exception:
        pass
    return ad


def bake_clips(only=None):
    """Writes every clip into an Action named hc_<clip> on the HC Rig armature."""
    ob = rig_object()
    rest = rest_of(ob.data)
    made = {}
    for name, seconds, fps, loop, fn in CLIPS:
        if only and name not in only:
            continue
        old = bpy.data.actions.get("hc_" + name)
        if old is not None:
            bpy.data.actions.remove(old)
        act = bpy.data.actions.new("hc_" + name)
        act.use_fake_user = True
        bind_action(ob, act)
        n = max(1, int(round(seconds * fps)))
        step = FPS / fps
        prev = {}
        # a loop's last key repeats its first, a frame past the end, so Blender closes the cycle the game closes
        for k in range(n + 1):
            t = k / n
            apply_pose(ob, clip_pose(fn, t % 1.0 if loop else t), rest, prev, frame=1 + k * step)
        act["hc_seconds"] = float(seconds)
        act["hc_fps"] = float(fps)
        act["hc_loop"] = bool(loop)
        act["hc_frames"] = n
        try:
            act.frame_range = (1, 1 + n * step)
            act.use_frame_range = True
            act.use_cyclic = bool(loop)
        except Exception:
            pass
        made[name] = n
    bind_action(ob, bpy.data.actions.get("hc_idle"))
    return made


# ---------------------------------------------------------------------------------------------- people on the rig

_SLOT_OF = {"SLOT_SHIRT": ("shirt", 1.0, "FABRIC"), "SLOT_SHIRT_D": ("shirt", .86, "FABRIC"), "SLOT_PANTS": ("pants", 1.0, "FABRIC"),
            "SLOT_PANTS_D": ("pants", .82, "FABRIC"), "SLOT_SKIN": ("skin", 1.0, "PLAIN"), "SLOT_SKIN_D": ("skin", .88, "PLAIN"),
            "SLOT_LIP": ("skin", .72, "PLAIN"), "SLOT_HAIR": ("hair", 1.0, "PLAIN"), "SLOT_ACCENT": ("accent", 1.0, "FABRIC")}


def _shade(hexc, f):
    return (int(((hexc >> 16) & 255) * f) << 16) | (int(((hexc >> 8) & 255) * f) << 8) | int((hexc & 255) * f)


def person_parts(look):
    """(part, bone) for everything this person is made of."""
    f = look.get("female", False)
    tee = look.get("tee", False)
    out = [("npc_torso_{:s}_{:s}".format("f" if f else "m", "tee" if tee else "shirt"), "torso"),
           ("npc_hips_skirt" if look.get("skirt") else "npc_hips_trousers", "hips"), ("npc_head_face", "head"),
           ("npc_hair{:d}".format(look.get("style", 0)), "head")]
    if look.get("beard"):
        out.append(("npc_beard_full", "head"))
    if look.get("tie"):
        out.append(("npc_tie_m", "torso"))
    if look.get("apron"):
        out.append(("npc_apron_front", "torso"))
    for s in "LR":
        out += [("npc_upper_arm_" + ("short" if tee else "long"), "upper_arm." + s), ("npc_forearm_" + ("bare" if tee else "long"), "forearm." + s),
                ("npc_hand_open", "hand." + s), ("npc_thigh_leg", "thigh." + s), ("npc_shin_leg", "shin." + s), ("npc_foot_shoe", "foot." + s)]
    return out


def spawn_person(name, look, coll=None, loc=(0, 0, 0), rz=0.0, action=None, scale=None, level=0):
    """Stands a dressed person on their own armature: parts are linked copies of the library's, recolored per object.

    look: shirt, pants, skin, hair, accent (colors); female, tee, skirt, tie, beard, apron (flags); style (hair).
    """
    coll = coll or category("People Preview")
    female = look.get("female", False)
    arm = bpy.data.objects.new(name, skeleton(female))
    coll.objects.link(arm)
    arm.location = loc
    arm.rotation_euler = (0, 0, math.radians(rz))
    s = scale or look.get("s", 1.0)
    arm.scale = (s, s, s)
    arm["hc_person"] = True
    if arm.pose is None:
        bpy.context.view_layer.update()
    cols = {"shirt": look.get("shirt", 0xF2F2F2), "pants": look.get("pants", 0x1F3A5F), "skin": look.get("skin", 0xE0AC69),
            "hair": look.get("hair", 0x8A5A2B), "accent": look.get("accent", 0x1F4E9C)}
    for pname, bone in person_parts(look):
        src = bpy.data.objects.get(pname if level == 0 else "{:s}.L{:d}".format(pname, level))
        if src is None:
            continue
        ob = bpy.data.objects.new("{:s}.{:s}.{:s}".format(name, pname[4:], bone[-1] if bone[-2] == "." else "c"), src.data)
        coll.objects.link(ob)
        ob.parent = arm
        ob.parent_type = 'BONE'
        ob.parent_bone = bone
        b = arm.data.bones[bone]
        ob.matrix_parent_inverse = (b.matrix_local @ Matrix.Translation((0, b.length, 0))).inverted() @ Matrix.Translation(b.head_local)
        legs = look.get("skirt") and bone.split(".")[0] in ("thigh", "shin")
        for slot in ob.material_slots:
            m = slot.material
            if m is None or m.name not in _SLOT_OF:
                continue
            key, shade, kind = _SLOT_OF[m.name]
            if legs and key == "pants":
                key, kind = "skin", "PLAIN"
            slot.link = 'OBJECT'
            slot.material = M(_shade(cols[key], shade), kind)
    if action is not None:
        act = bpy.data.actions.get(action if action.startswith("hc_") else "hc_" + action)
        bind_action(arm, act)
    return arm


def remove_person(name):
    arm = bpy.data.objects.get(name)
    if arm is None:
        return
    for ch in list(arm.children):
        bpy.data.objects.remove(ch, do_unlink=True)
    bpy.data.objects.remove(arm, do_unlink=True)


def pose_person(arm, clip_name, t):
    """Freezes a person in one moment of a clip (no action attached)."""
    fn = next(c[4] for c in CLIPS if c[0] == clip_name)
    apply_pose(arm, clip_pose(fn, t))


CAST = (
    dict(shirt=0xF2F2F2, pants=0x1F3A5F, skin=0xE8B48A, hair=0xB0834A, style=0, clip="idle", tie=True, beard=True, accent=0x1F4E9C),
    dict(shirt=0xEB5757, pants=0x34383F, skin=0xF1C9A5, hair=0x8A5A2B, style=1, female=True, tee=True, clip="walk", t=.22),
    dict(shirt=0xF2C94C, pants=0x4A3B2A, skin=0x8D5524, hair=0x0F0F10, style=3, tee=True, clip="run", t=.7),
    dict(shirt=0x27AE60, pants=0x22252A, skin=0xE0AC69, hair=0x4A3220, style=2, female=True, clip="sit", accent=0xEB5757, z=.45),
    dict(shirt=0xE9EDF2, pants=0x5B6770, skin=0xC68B59, hair=0xD9B26A, style=5, female=True, clip="work", apron=True, accent=0x2C3E50),
    dict(shirt=0x9B51E0, pants=0x2E4A3A, skin=0xF1C9A5, hair=0xA0522D, style=4, tee=True, clip="dance_a", t=.1),
    dict(shirt=0x56A0D8, pants=0x34383F, skin=0x8D5524, hair=0x7A7A7A, style=4, clip="carry", t=.2, beard=True),
    dict(shirt=0xF7F0E0, pants=0x8E3B4E, skin=0xE0AC69, hair=0x2B1D14, style=2, female=True, clip="wave", t=.5, skirt=True, accent=0xC0392B, s=.95),
    dict(shirt=0x2F80ED, pants=0x34383F, skin=0xF1C9A5, hair=0x2B1D14, style=0, clip="idle_phone", t=.2),
    dict(shirt=0xF2994A, pants=0x1F3A5F, skin=0xC68B59, hair=0x4A3220, style=5, female=True, tee=True, clip="talk", t=.4),
    dict(shirt=0x6FCF97, pants=0x22252A, skin=0xE8B48A, hair=0x0F0F10, style=1, female=True, tee=True, clip="dance_b", t=.3),
    dict(shirt=0xD9DEE4, pants=0x4A3B2A, skin=0x8D5524, hair=0x0F0F10, style=0, clip="push", t=.3),
)


def build_people_preview():
    """One of each kind of person, frozen in a moment of a clip, to judge the look."""
    coll = category("People Preview")
    for ob in [o for o in coll.objects if o.parent is None]:
        remove_person(ob.name)
    obs = []
    for k, c in enumerate(CAST):
        arm = spawn_person("cast_{:02d}_{:s}".format(k + 1, c["clip"]), c, coll, loc=(k * 1.1, 0, c.get("z", 0)))
        pose_person(arm, c["clip"], c.get("t", 0.0))
        obs.append(arm)
    return obs


def people_objects(arms):
    """The armatures and everything hanging on them (for the camera to frame)."""
    out = []
    for a in arms:
        out.append(a)
        out += list(a.children)
    return out
