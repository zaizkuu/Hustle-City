"""Original song for the New York story trailer: 100 BPM in D major, an 80s heartland-rock groove.

  0-10.8   cold open: a dark drone and the city's murmur under the loan officer's questions, a far-off siren
  10.8     the riff alone (bars 0-1), a plucked line like a clean electric guitar through a delay
  15.6     verse (bars 2-5): drums, eighth-note bass, e-piano on the off-beats      D | A | Bm | G
  25.2     pre-chorus (bars 6-7): toms, a riser, a snare roll into the drop        Bm | G | Em | A
  30.0     chorus (bars 8-15): everything, and the hook sung by a lead synth
  44.4     the last two bars go double-time under the quick cuts
  49.2     breakdown: drums out, the riff and the pads under the finale
  55.2     the logo: one big D chord, a crash and a boom, ringing out

usage: python3 newyork_score.py <out.wav>
"""
import sys
import numpy as np
from synth import *

DUR = 60.0
BEAT = .6
BAR = 2.4
S0 = 10.8  # the song's first downbeat
mx = Mixer(DUR)
place = mx.place


def bar(k, beat=0.0): return S0 + k * BAR + beat * BEAT


D, A, Bm, G, Em = (62, 66, 69), (57, 61, 64), (59, 62, 66), (55, 59, 62), (52, 55, 59)
ROOT = {D: 38, A: 33, Bm: 35, G: 31, Em: 40}
LOOP = [D, A, Bm, G]
PRE = [Bm, G, Em, A]


# ---------------------------------------------------------------- instruments
def rhodes(chord, d=.9):
    """An electric piano stab: sine tines with a little bark and a slow tremolo."""
    t = t_axis(d)
    out = np.zeros(len(t))
    for m in chord:
        f = note(m)
        out += sine(f, d) + .35 * sine(f * 2, d) * np.exp(-t * 5) + .12 * sine(f * 3.01, d) * np.exp(-t * 9)
    trem = 1 + .18 * np.sin(2 * np.pi * 4.5 * t)
    return out / len(chord) * np.exp(-t * 2.2) * trem * adsr(d, .004, 1, 1, .08) * .55


def guitar(m, d=.3):
    """The riff: a bright pluck with a body resonance, like a clean single-coil."""
    t = t_axis(d)
    s = pluck(m, d, 4200.0) + .4 * filt(saw(note(m) * 2, d), 'bandpass', [1200, 3800]) * np.exp(-t * 18)
    return s * .8


def lead(notes, start):
    """The hook: a saw lead that glides between notes, with vibrato that grows on held notes and a vowel-like
    formant, so it sings. notes = [(midi, beats)]."""
    total = sum(b for _, b in notes) * BEAT
    n = int(total * SR)
    f = np.zeros(n); env = np.zeros(n)
    i = 0
    prev = None
    for m, b in notes:
        k = int(b * BEAT * SR)
        seg = np.arange(k) / SR
        target = note(m)
        if prev is None: prev = target
        glide = prev + (target - prev) * (1 - np.exp(-seg / .045))
        vib = 1 + .006 * np.sin(2 * np.pi * 5.5 * seg) * np.clip((seg - .18) / .25, 0, 1)
        f[i:i + k] = glide * vib
        a = np.minimum(1, seg / .02) * np.where(seg > b * BEAT - .06, np.clip((b * BEAT - seg) / .06, 0, 1), 1)
        env[i:i + k] = a * (.85 + .15 * np.exp(-seg * 3))
        prev = target
        i += k
    x = saw(f, total) * .6 + saw(f * 1.004, total) * .4
    # two formants that open with the level: "ah"
    x = filt(x, 'lowpass', 3800, 2)
    x = .6 * filt(x, 'bandpass', [600, 1300], 1) + .5 * filt(x, 'bandpass', [2000, 3000], 1) + .25 * x
    place('music', x * env * .42, start, send=.35)
    # a slap echo, three eighths later, quieter and darker
    place('music', filt(x * env, 'lowpass', 2200) * .13, start + 1.5 * BEAT, pan=-.4, send=.4)
    place('music', filt(x * env, 'lowpass', 1600) * .07, start + 3 * BEAT, pan=.4, send=.4)


def siren(d=4.0):
    t = t_axis(d)
    f = 700 + 260 * np.sin(2 * np.pi * .55 * t)
    s = filt(sine(f, d) * .6 + .3 * sine(f * 2, d), 'lowpass', 1800)
    return s * np.minimum(1, t / 1.2) * np.minimum(1, (d - t) / 1.5) * .12


def stinger():
    """The news channel's sting: three bright synth stabs and a timpani."""
    out = np.zeros(int(1.6 * SR))
    for k, ch in enumerate(((74, 78, 81), (76, 79, 83), (79, 83, 86))):
        s = pad(ch, .22, bright=4200, attack=.005)[:, 0] * 4
        i = int(k * .12 * SR)
        out[i:i + len(s)] += s
    tim = tom(98, 1.2)
    out[int(.36 * SR):int(.36 * SR) + len(tim)] += tim * .8
    return out * .5


def pop():
    """A like on a phone clip: a short rising blip."""
    d = .09; t = t_axis(d)
    return sine(900 + 1400 * t / d, d) * np.exp(-t * 30) * .12


# ---------------------------------------------------------------- cold open (0 - 10.8)
# kept well under the song, so it arrives as a lift
drone = pad((38, 45, 50), 10.6, bright=380, attack=2.0) * .45
place('music', drone, 0.0, send=.3)
place('sfx', murmur(10.7, .9), 0.0, gain=.28)
place('sfx', siren(5.0), 3.6, gain=.35, pan=.5, send=.5)
# a low pulse, like a slow heartbeat across the desk
for t in (1.0, 3.4, 5.8, 8.2):
    place('drums', kick(.5) * .22, t, send=.2)
# "Not like mine." cuts it all to black; the song comes in 0.2 s later
place('fx', reverse_swell(.8), 10.0, gain=.5)

# ---------------------------------------------------------------- the song
kicks = []


def drums(k, full=True, double=False):
    t0 = bar(k)
    beats = [0, 2] + ([1.5] if full else [])
    if double: beats = [0, 1, 2, 3]
    for b in beats:
        place('drums', kick(), t0 + b * BEAT, gain=.95)
        kicks.append(t0 + b * BEAT)
    if full:
        for b in (1, 3):
            place('drums', snare(1.1), t0 + b * BEAT, gain=.75, send=.55)  # the gated-reverb snare: a big send
            place('drums', clap(), t0 + b * BEAT, gain=.25, send=.4)
    for h in range(8):
        place('drums', hat(open_=(h % 2 == 1 and double)), t0 + h * BEAT / 2, gain=.5 if h % 2 else .32, pan=.25)


def bass(k, chord, pattern='eighths'):
    t0 = bar(k)
    r = ROOT[chord]
    if pattern == 'eighths':
        for h in range(8): place('bass', bass_note(r + (12 if h == 7 else 0), .28, 520), t0 + h * BEAT / 2, gain=.95)
    else:
        place('bass', bass_note(r, BAR - .05, 380), t0, gain=.9)


def riff(k, chord, gain=1.0):
    """Sixteenth arpeggio over the chord's tones, up and back: the trailer's signature."""
    t0 = bar(k)
    tones = [chord[0] + 12, chord[1] + 12, chord[2] + 12, chord[1] + 24]
    order = [0, 1, 2, 3, 2, 1, 2, 3] * 2
    for s, idx in enumerate(order):
        g = guitar(tones[idx], .3)
        place('music', g * gain * (1.0 if s % 4 == 0 else .7), t0 + s * BEAT / 4, pan=-.3, send=.25)
        # the delay: dotted eighth, panned across
        place('music', filt(g, 'lowpass', 2600) * gain * .28, t0 + s * BEAT / 4 + .75 * BEAT, pan=.45, send=.3)


def keys(k, chord):
    t0 = bar(k)
    for b in (1, 3): place('music', rhodes(chord, .7), t0 + b * BEAT, gain=.9, pan=.2, send=.25)


# intro: the riff alone, then the bass under it
for k in (0, 1):
    riff(k, LOOP[k])
place('bass', bass_note(ROOT[A], BAR, 300), bar(1), gain=.7)
place('drums', crash(2.0) * .5, bar(0), send=.4)

# verse
for k in range(2, 6):
    ch = LOOP[(k - 2) % 4]
    drums(k, full=True)
    bass(k, ch)
    riff(k, ch, .6)
    keys(k, ch)
    place('music', pad(ch, BAR, bright=700, attack=.4) * .35, bar(k), send=.2)

# pre-chorus: lift on Bm G Em A, toms and a snare roll
for i, k in enumerate((6, 7)):
    for half in (0, 1):
        ch = PRE[i * 2 + half]
        t0 = bar(k, half * 2)
        place('music', pad(ch, BAR / 2, bright=1400, attack=.08) * .55, t0, send=.3)
        place('bass', bass_note(ROOT[ch], BAR / 2 - .05, 600), t0, gain=.9)
        place('music', rhodes(ch, 1.0), t0, gain=.8, send=.3)
    for b in range(4): place('drums', tom(110 - b * 8, .5) * .6, bar(k, b), send=.3)
for s in range(16):  # the roll, getting louder
    place('drums', snare(.4 + .6 * s / 15), bar(7) + s * BEAT / 4, gain=.6, send=.3)
place('fx', riser(BAR * 2, 200, 1800), bar(6), gain=.55)
kicks += [bar(6), bar(7)]

# chorus: the drop
place('fx', boom(2.2) * .7, bar(8), send=.3)
place('drums', crash(2.6), bar(8), gain=.8, send=.4)
for k in range(8, 16):
    ch = LOOP[(k - 8) % 4]
    double = k >= 14
    drums(k, full=True, double=double)
    bass(k, ch)
    riff(k, ch, .55)
    keys(k, ch)
    place('music', pad(ch, BAR, bright=1600, attack=.12) * .5, bar(k), send=.3)
    if k == 12: place('drums', crash(2.0) * .6, bar(k), send=.3)
HOOK = [
    [(78, 1), (76, .5), (74, .5), (76, 1), (78, 1)],
    [(76, 1.5), (73, .5), (69, 2)],
    [(74, 1), (73, .5), (71, .5), (73, 1), (74, 1)],
    [(71, 1.5), (69, .5), (67, 1), (69, 1)],
    [(78, 1), (81, 1), (78, .5), (76, .5), (74, 1)],
    [(76, 1), (73, 1), (76, 1), (78, 1)],
    [(79, 1.5), (78, .5), (74, 1), (71, 1)],
    [(74, 2), (76, 1), (74, 1)],
]
lead([n for b in HOOK for n in b], bar(8))

# breakdown under the finale: the riff, a pad and the hook's last line, softly
for k in (16, 17):
    ch = LOOP[(k - 16) % 4]
    riff(k, ch, .7)
    place('music', pad(ch, BAR, bright=900, attack=.3) * .5, bar(k), send=.4)
    place('bass', bass_note(ROOT[ch], BAR - .05, 300), bar(k), gain=.6)
place('music', pad(Bm, BAR / 2, bright=900, attack=.2) * .45, bar(18), send=.4)
place('fx', reverse_swell(1.2), bar(18, 2) - 1.2 + .02, gain=.9)

# the logo: one big chord
LOGO = bar(18, 2)  # 55.2
place('fx', boom(4.0), LOGO, gain=.9, send=.3)
place('drums', crash(4.0), LOGO, gain=.9, send=.5)
place('music', pad((50, 57, 62, 66, 69, 74), 4.8, bright=2400, attack=.01) * 1.3, LOGO, send=.6)
place('bass', bass_note(26, 4.6, 260), LOGO, gain=1.0)
for i, m in enumerate((74, 78, 81, 86)):
    place('music', bell(m, 3.0) * .6, LOGO + .05 + i * .12, pan=-.3 + .2 * i, send=.5)
kicks.append(LOGO)

# ---------------------------------------------------------------- sound design on the cuts
place('fx', stinger(), 25.2, gain=.9, pan=-.1, send=.2)  # the news
for t in (18.0, 34.8, 42.0):  # the phone clips: a swipe in, likes popping
    place('fx', whoosh(.5, .6, 400, 6000), t - .3, gain=.5)
    for j in range(6): place('fx', pop(), t + .5 + j * .27 + .05 * (j % 2), gain=.9, pan=.4)
for t in (44.4 + .6 * i for i in range(8)):  # the quick cuts
    place('fx', whoosh(.25, .5, 900, 7000), t - .12, gain=.25)

mx.finish(sys.argv[1], kicks, breaths=[(10.62, 10.8), (49.18, 49.25)], fade=.8)
