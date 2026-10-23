"""Original score for the 1.8 update trailer (edit18.py): 120 BPM in D minor, every cut on a beat.

  0-3.5   the open: pad, a music-box line and a clock ticking faster, a riser into a breath
  4-6     the title slam
  6-24    the comparisons: the groove builds over each before/after, a shimmer as each divider sweeps in
  24-27   the shop montage at full tilt, a hit on every word
  27-41   the groove carries the crowds, the night, the phone screens and Manila
  41-43   breakdown hit under UPDATE 1.8
  43-45   the flips: a snare roll and a riser
  45-46   silence
  46-53   final impact under the logo

usage: python3 score18.py <out.wav> [sfx dir]
The game's own sound effects (taps, cash, door chime) are laid over it when the sfx dir is given.
"""
import os, sys
import numpy as np
from synth import *

DUR = 53.0
BEAT = .5
BAR = 2.0
mx = Mixer(DUR)
place = mx.place

DM, BB, F, C, GM, A = (50, 53, 57), (46, 50, 53), (41, 45, 48), (48, 52, 55), (43, 46, 50), (45, 49, 52)
PROG = [DM, BB, F, C]
kicks = []

# 0-3.5: the open
place('music', pad([38, 50, 53, 57, 62], 4.0, bright=1500, attack=1.2), 0, .5, send=.4)
place('bass', sine(note(38), 4.0) * adsr(4.0, 1.2, 1, 1, .4) * .22, 0, 1)
place('music', braam([26, 38, 50, 53, 57]), 0.0, .5, send=.5)
for i, (t0, m) in enumerate(((.5, 74), (1.0, 69), (1.5, 77), (2.0, 76), (2.5, 74), (2.75, 77), (3.0, 81))):
    place('music', bell(m), t0, .5, pan=(-.4 if i % 2 else .4), send=.6)
for i in range(14):
    t0 = i * .25
    place('drums', hat() * (.25 + .6 * t0 / 3.5), t0, .5 if i % 2 == 0 else .3, pan=.3 if i % 2 else -.3)
place('fx', riser(1.5, 200, 1800), 2.0, .9, send=.3)
for i in range(12):
    place('drums', snare(.3 + .7 * i / 12), 2.5 + 1.0 * (1 - (1 - i / 12) ** 1.4), .5, send=.2)
place('fx', reverse_swell(.5), 3.5, .8)

# 4-6: the slam
place('drums', boom(2.6, 70, 28), 4.0, 1.0, send=.5)
place('drums', taiko(), 4.0, .7, send=.4)
place('drums', crash(3.0), 4.0, .6, send=.4)
place('music', braam([26, 38, 45, 50, 53], d=2.4), 4.0, .95, send=.6)
place('music', pad([50, 53, 57, 62], 2.2, bright=2000, attack=.4), 4.1, .5, send=.5)
place('fx', riser(1.0, 300, 2000), 5.0, .6, send=.3)


# 6-24 and 27-41: the groove
def groove(t0, b, level):
    """One bar of the groove; [level] 0..2 adds claps and plucks, then the ostinato and open hats."""
    ch = PROG[b % 4]
    pattern = (0, 1.5, 2, 3.5) if level >= 2 else (0, 1.5, 2)
    for p in pattern: kicks.append(t0 + p * BEAT); place('drums', kick(), t0 + p * BEAT, .95)
    if level >= 1:
        for p in (1, 3): place('drums', clap(), t0 + p * BEAT, .6, send=.15); place('drums', snare(.6), t0 + p * BEAT, .45)
    else:
        for p in (1, 3): place('drums', snare(.4), t0 + p * BEAT, .3, send=.2)
    for s in range(16):
        acc = .55 if s % 4 == 2 else .3
        place('drums', hat(open_=(level >= 2 and s % 8 == 6)), t0 + s * BEAT / 4, acc * (1.15 if level >= 2 else 1), pan=.35)
    if b % 4 == 3:
        for k, (p, f) in enumerate(((3, 140), (3.25, 120), (3.5, 100), (3.75, 82))): place('drums', tom(f), t0 + p * BEAT, .6, pan=-.3 + k * .2, send=.2)
    if b % 4 == 0: place('drums', crash(), t0, .5, send=.3)
    root = ch[0] - 24
    for s in range(16):
        place('bass', bass_note(root + (12 if s in (14, 15) else 0), BEAT / 4 * .92, bright=380 + 200 * level), t0 + s * BEAT / 4, .65)
    place('music', pad(list(ch) + [ch[0] + 12, ch[1] + 12], BAR + .3, bright=1700 + 400 * level, attack=.08), t0, .7, send=.3)
    if level >= 1:
        seq = [ch[0] + 12, ch[1] + 12, ch[2] + 12, ch[0] + 24]
        for s in range(16):
            place('music', pluck(seq[(s * 3) % 4] + (12 if level >= 2 and s % 8 == 7 else 0), bright=2600 + 1100 * level),
                  t0 + s * BEAT / 4, .32, pan=(.45 if s % 2 else -.45), send=.25)
    if level >= 2:
        for s in (0, 3, 6, 8, 11, 14):
            place('music', pluck(ch[0] - 12 + (7 if s in (6, 14) else 0), d=.22, bright=1600) * 1.4, t0 + s * BEAT / 4, .3, pan=-.2)


for b in range(9):  # 6-24: each comparison a notch busier than the last
    groove(6.0 + b * BAR, b, 0 if b < 3 else 1 if b < 6 else 2)
# the divider sweeping in, and out again, on each comparison
for t0 in (6.0, 12.0, 18.0):
    place('fx', whoosh(.6, .5, 400, 5200), t0 + 1.35, .45, send=.3)
    for k, m in enumerate((86, 89, 93)):
        place('music', bell(m, 1.2), t0 + 1.55 + k * .08, .26, pan=-.3 + .3 * k, send=.7)
    place('fx', whoosh(.6, .5, 300, 4200), t0 + 3.75, .35, send=.3)

# 24-27: the shop montage
for b in range(2):
    t0 = 24.0 + b * BAR
    ch = PROG[b % 4]
    place('drums', crash(), t0, .6, send=.3)
    n = 4 if b == 0 else 2
    for p in range(n): kicks.append(t0 + p * BEAT); place('drums', kick(1.2), t0 + p * BEAT, 1.0)
    for p in (1, 3)[:n // 2]: place('drums', clap(), t0 + p * BEAT, .7, send=.15); place('drums', snare(.8), t0 + p * BEAT, .55)
    for s in range(4 * n): place('drums', hat(), t0 + s * BEAT / 4, .45, pan=.35)
    root = ch[0] - 24
    for s in range(4 * n): place('bass', bass_note(root + (12 if s % 4 == 3 else 0), BEAT / 4 * .92, bright=700), t0 + s * BEAT / 4, .7)
    place('music', pad(list(ch) + [ch[0] + 12, ch[1] + 12], n * BEAT + .3, bright=3000, attack=.03), t0, .7, send=.3)
    seq = [ch[0] + 24, ch[2] + 12, ch[1] + 12, ch[2] + 12]
    for s in range(4 * n): place('music', pluck(seq[s % 4], bright=5200), t0 + s * BEAT / 4, .36, pan=(.5 if s % 2 else -.5), send=.25)
for i in range(6): place('fx', boom(.5, 120, 60) * .6, 24.0 + i * .5, .35)

for b in range(7):  # 27-41
    groove(27.0 + b * BAR, b, 1 if b in (2, 3) else 2)

# 41-43: breakdown under UPDATE 1.8
place('drums', boom(2.2), 41.0, .9, send=.45)
place('music', braam([38, 45, 50, 53]), 41.0, .9, send=.5)
place('drums', crash(3.0), 41.0, .5, send=.4)
place('music', pad([50, 53, 57, 62, 65], 2.0, bright=1400, attack=.2), 41.0, .45, send=.6)

# 43-45: the flips
for i in range(8):
    place('drums', kick(1.1) if i % 2 else tom(150 - 6 * i), 43.0 + i * .25, .6 if i % 2 else .45)
for i in range(24):
    place('drums', snare(.3 + .7 * i / 24), 43.0 + 2.0 * (i / 24) ** .85, .5, send=.2)
place('fx', riser(2.0, 220, 2600), 43.0, 1.0, send=.3)
place('fx', reverse_swell(.9), 45.1, .9)

# 46-53: the logo
place('drums', boom(5.0, 75, 26), 46.0, 1.0, send=.6)
place('drums', taiko(), 46.0, .7, send=.4)
place('drums', crash(4.0), 46.0, .6, send=.5)
place('music', braam([26, 38, 45, 50, 53], d=4.5), 46.0, 1.0, send=.6)
place('music', pad([50, 53, 57, 62, 64, 69], 6.8, bright=2600, attack=1.2), 46.2, .7, send=.6)
for t0, m in ((46.6, 74), (47.1, 77), (47.6, 81), (48.6, 86)):
    place('music', bell(m, 2.5), t0, .5, pan=.2, send=.7)
place('drums', boom(3.0, 60, 30) * .5, 50.5, .5, send=.5)

# ---------------------------------------------------------------- the game's sounds
sfx_dir = sys.argv[2] if len(sys.argv) > 2 else None
if sfx_dir and os.path.isdir(sfx_dir):
    S = {n[:-4]: load_sfx(os.path.join(sfx_dir, n)) for n in os.listdir(sfx_dir) if n.endswith('.wav')}
    for t0, name, g in ((12.2, 'door', .3), (12.35, 'chime', .28), (33.4, 'tap', .5), (33.9, 'cash', .5), (35.4, 'tap', .5),
                        (35.9, 'cash', .35), (37.4, 'tap', .5), (37.9, 'tap', .4), (48.9, 'cash', .3)):
        if name in S: place('sfx', S[name], t0, g, send=.15)
place('sfx', murmur(6.0, .7), 6.0, .8, send=.1)
place('sfx', murmur(6.0, .9), 12.0, .7, send=.1)
place('sfx', murmur(6.0, 1.2), 18.0, .9, send=.1)
place('sfx', murmur(4.1, 1.4), 27.0, 1, send=.1)
place('sfx', murmur(2.1, .8), 31.0, .7, send=.1)

# a whoosh under each headline
for t0 in (26.85, 30.85, 32.85, 34.85, 36.85, 38.85):
    place('fx', whoosh(.6), t0, .35, send=.2)

# ---------------------------------------------------------------- mix
mx.finish(sys.argv[1], kicks, breaths=((3.5, 3.985), (45.0, 45.985)))
