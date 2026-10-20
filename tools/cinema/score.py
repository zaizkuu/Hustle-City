"""Original score for the 60-second cinematic trailer: 120 BPM in D minor, every cut on a beat.

  0-10    the cities: drone, pad and a music-box line, a brass hit on each city reveal
  10-12   riser into a breath of silence
  12-44   the drop and the groove (Dm Bb F C), growing every eight bars
  44-46   breakdown hit and snare roll
  46-52   montage at full tilt, then a strobe build
  52-53   silence
  53-60   final impact under the logo

usage: python3 score.py <out.wav> [sfx dir]
The game's own sound effects (door chime, taps, car door, thunder, cash) are laid over it when the sfx dir is given.
"""
import os, sys
import numpy as np
from synth import *

DUR = 60.0
BEAT = .5
BAR = 2.0
mx = Mixer(DUR)
place = mx.place


# ---------------------------------------------------------------- the arrangement
DM, BB, F, C, GM, A = (50, 53, 57), (46, 50, 53), (41, 45, 48), (48, 52, 55), (43, 46, 50), (45, 49, 52)
PROG = [DM, BB, F, C]

# 0-10: the cities
place('music', pad([38, 50, 53, 57, 62], 10.3, bright=1500, attack=2.2), 0, .5, send=.4)
place('bass', sine(note(38), 10.4) * adsr(10.4, 3, 1, 1, 1.2) * .22, 0, 1)
for t0, ch, low in ((0.0, DM, 26), (3.0, BB, 22), (6.0, GM, 19), (8.0, A, 21)):
    place('music', braam([low + 12] + [m - 12 for m in ch] + list(ch)), t0, .62, send=.5)
    place('drums', boom(2.6, 70, 32), t0, .4, send=.4)
    place('drums', taiko(), t0, .35, send=.3)
# a music-box line over the cities
for i, (t0, m) in enumerate(((.5, 74), (1.0, 69), (1.5, 77), (2.0, 76), (3.5, 74), (4.0, 70), (4.5, 77), (5.0, 74),
                             (6.5, 79), (7.0, 74), (7.5, 77), (8.5, 76), (9.0, 73), (9.5, 76))):
    place('music', bell(m), t0, .5, pan=(-.4 if i % 2 else .4), send=.6)
# a clock ticking faster under the reveals
for i in range(40):
    t0 = i * .25
    if t0 >= 10: break
    place('drums', hat() * (.25 + .5 * t0 / 10), t0, .5 if i % 2 == 0 else .3, pan=.3 if i % 2 else -.3)

# 10-12: riser, then a breath
place('fx', riser(1.5), 10.0, .9, send=.3)
for i in range(16):
    t0 = 10.0 + 1.5 * (1 - (1 - i / 16) ** 1.4)
    place('drums', snare(.35 + .65 * i / 16), t0, .55, send=.2)
place('fx', reverse_swell(.5), 11.5, .8)

# 12-44: the drop and the groove
kicks = []
for b in range(16):
    t0 = 12.0 + b * BAR
    if t0 >= 44.0: break
    ch = PROG[b % 4]
    late = b >= 8
    pattern = (0, 1.5, 2, 3.5) if late else (0, 1.5, 2)
    for p in pattern: kicks.append(t0 + p * BEAT); place('drums', kick(), t0 + p * BEAT, .95)
    for p in (1, 3): place('drums', clap(), t0 + p * BEAT, .6, send=.15); place('drums', snare(.6), t0 + p * BEAT, .45)
    for s in range(16):
        acc = .55 if s % 4 == 2 else .3
        place('drums', hat(open_=(s % 8 == 6)), t0 + s * BEAT / 4, acc * (1.2 if late else 1), pan=.35)
    if b % 4 == 3:  # tom fill into the next phrase
        for k, (p, f) in enumerate(((3, 140), (3.25, 120), (3.5, 100), (3.75, 82))): place('drums', tom(f), t0 + p * BEAT, .65, pan=-.3 + k * .2, send=.2)
    if b % 4 == 0: place('drums', crash(), t0, .55, send=.3)
    # bass: sixteenth pulse on the root, an octave jump at the end of the bar
    root = ch[0] - 24
    for s in range(16):
        m = root + (12 if s in (14, 15) else 0)
        place('bass', bass_note(m, BEAT / 4 * .92, bright=380 + (240 if late else 0)), t0 + s * BEAT / 4, .65)
    place('music', pad([m for m in ch] + [ch[0] + 12, ch[1] + 12], BAR + .3, bright=2400 if late else 1800, attack=.08), t0, .7, send=.3)
    if b >= 4:  # plucked arpeggio, brighter later
        seq = [ch[0] + 12, ch[1] + 12, ch[2] + 12, ch[0] + 24]
        for s in range(16):
            place('music', pluck(seq[(s * 3) % 4] + (12 if late and s % 8 == 7 else 0), bright=2600 + (2200 if late else 0)),
                  t0 + s * BEAT / 4, .34, pan=(.45 if s % 2 else -.45), send=.25)
    if late:  # a staccato low-string ostinato
        for s in (0, 3, 6, 8, 11, 14):
            place('music', pluck(ch[0] - 12 + (7 if s in (6, 14) else 0), d=.22, bright=1600) * 1.4, t0 + s * BEAT / 4, .3, pan=-.2)

# 44-46: breakdown hit, then the roll
place('drums', boom(2.2), 44.0, .9, send=.45)
place('music', braam([38, 45, 50, 53]), 44.0, .9, send=.5)
place('drums', crash(3.0), 44.0, .5, send=.4)
for i in range(24):
    t0 = 45.0 + (i / 24) ** .8 * 1.0
    place('drums', snare(.3 + .7 * i / 24), t0, .5, send=.2)
place('fx', riser(1.0, 300, 1800), 45.0, .8)

# 46-52: montage at full tilt
for b in range(3):
    t0 = 46.0 + b * BAR
    ch = PROG[b % 4]
    place('drums', crash(), t0, .6, send=.3)
    for p in range(4): kicks.append(t0 + p * BEAT); place('drums', kick(1.2), t0 + p * BEAT, 1.0)
    for p in (1, 3): place('drums', clap(), t0 + p * BEAT, .7, send=.15); place('drums', snare(.8), t0 + p * BEAT, .55)
    for s in range(8): place('drums', tom(150 - 9 * (s % 4)), t0 + s * BEAT / 2 + BEAT / 4, .35, pan=(-.4 if s % 2 else .4))
    for s in range(16): place('drums', hat(), t0 + s * BEAT / 4, .45, pan=.35)
    root = ch[0] - 24
    for s in range(16): place('bass', bass_note(root + (12 if s % 4 == 3 else 0), BEAT / 4 * .92, bright=700), t0 + s * BEAT / 4, .7)
    place('music', pad(list(ch) + [ch[0] + 12, ch[1] + 12], BAR + .3, bright=3000, attack=.03), t0, .7, send=.3)
    seq = [ch[0] + 24, ch[2] + 12, ch[1] + 12, ch[2] + 12]
    for s in range(16): place('music', pluck(seq[s % 4], bright=5200), t0 + s * BEAT / 4, .36, pan=(.5 if s % 2 else -.5), send=.25)
# a hit on every word (46-50), then the strobe build (50-52)
for i in range(8): place('fx', boom(.5, 120, 60) * .6, 46.0 + i * .5, .35)
for i in range(32):
    t0 = 50.0 + i * .0625
    place('drums', snare(.35 + .65 * i / 32), t0, .55, send=.15)
place('fx', riser(2.0, 220, 2600), 50.0, 1.0, send=.3)

# 52-53: silence, then the suck into the last hit
place('fx', reverse_swell(.9), 52.1, .9)

# 53-60: the logo
place('drums', boom(5.0, 75, 26), 53.0, 1.0, send=.6)
place('drums', taiko(), 53.0, .7, send=.4)
place('drums', crash(4.0), 53.0, .6, send=.5)
place('music', braam([26, 38, 45, 50, 53], d=4.5), 53.0, 1.0, send=.6)
place('music', pad([50, 53, 57, 62, 64, 69], 6.8, bright=2600, attack=1.2), 53.2, .7, send=.6)
for t0, m in ((53.6, 74), (54.1, 77), (54.6, 81), (55.6, 86)):
    place('music', bell(m, 2.5), t0, .5, pan=.2, send=.7)
place('drums', boom(3.0, 60, 30) * .5, 57.5, .5, send=.5)

# ---------------------------------------------------------------- the game's sounds
sfx_dir = sys.argv[2] if len(sys.argv) > 2 else None
if sfx_dir and os.path.isdir(sfx_dir):
    S = {n[:-4]: load_sfx(os.path.join(sfx_dir, n)) for n in os.listdir(sfx_dir) if n.endswith('.wav')}
    for t0, name, g in ((14.15, 'door', .35), (14.3, 'chime', .3), (18.7, 'tap', .5), (19.5, 'tap', .5), (20.6, 'tap', .45),
                        (22.05, 'car_door', .5), (24.05, 'thunder', .75), (28.4, 'tap', .45), (34.5, 'cash', .55),
                        (36.4, 'cash', .4), (55.9, 'cash', .35)):
        if name in S: place('sfx', S[name], t0, g, send=.15)
# rain on the windscreen under the storm shot
d = 2.2; t = t_axis(d)
place('sfx', np.stack([filt(noise(d), 'bandpass', [800, 9000]), filt(noise(d), 'bandpass', [800, 9000])], 1) * .12
      * np.minimum(1, np.minimum(t / .2, (d - t) / .3))[:, None], 23.95, 1)
# an engine for the drives, and the hypercar passing
for a, b, base in ((22.0, 24.0, 44), (26.0, 28.0, 32), (38.0, 40.0, 70)):
    d = b - a; t = t_axis(d)
    f = base + base * .9 * (t / d) ** 1.4
    eng = saw(f, d) * .5 + saw(f * 2.01, d) * .25
    eng = sweep(eng, 'lowpass', lambda s: 900 + 1500 * s / d) * np.minimum(1, np.minimum(t / .15, (d - t) / .2))
    place('sfx', eng * .35, a, 1, pan=0)
def chirp_signal(d):
    """A Tokyo walk signal: two quick rising chirps, over and over."""
    out = np.zeros(int(d * SR))
    k = 0.0
    while k < d - .2:
        for o, (f0, f1) in ((0.0, (2300, 3100)), (.14, (2500, 3300))):
            c = t_axis(.07)
            tone = sine(f0 + (f1 - f0) * c / .07, .07) * np.sin(np.pi * c / .07) ** 2
            i = int((k + o) * SR)
            out[i:i + len(tone)] += tone[:len(out) - i]
        k += .5
    return out * .2


place('sfx', murmur(4.1, 1.0), 29.95, 1, send=.1)
place('sfx', murmur(2.2, 1.3), 41.9, 1, send=.1)
place('sfx', murmur(2.2, 1.5), 43.9, 1, send=.1)
place('sfx', chirp_signal(2.0), 44.05, .55, pan=.2, send=.25)
# a taxi swishing past the camera in Times Square
d = 1.0; t = t_axis(d)
place('sfx', np.stack([bandsweep(noise(d), lambda s: 500 + 2500 * np.exp(-((s - .45) / .2) ** 2)) * np.exp(-((t - .45) / .25) ** 2) * .5] * 2, 1), 42.0, .9)

# a whoosh under each headline
for t0 in (13.85, 17.85, 21.85, 23.85, 25.85, 27.85, 29.85, 33.85, 35.85, 37.85, 39.85):
    place('fx', whoosh(.6), t0, .35, send=.2)
place('drums', boom(1.6, 90, 40), 42.0, .45, send=.4)  # FIVE CITIES.

# ---------------------------------------------------------------- mix
mx.finish(sys.argv[1], kicks, breaths=((11.5, 11.985), (52.0, 52.985)))
