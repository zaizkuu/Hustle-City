"""Original score for the Manila showcase trailer: 120 BPM in E minor, kulintang and rondalla over a modern groove.

  0-12    dawn to dusk: an agung gong, a drone and a kulintang line; cathedral bells, a bugle at Luneta, the city
          hall clock, then a riser into a breath of silence
  12-32   the drop and the streets (Em C G D): rondalla tremolo and strums, tinikling clacks, the kulintang joining,
          and the street under every cut: buzzing wires, jeepney horns, the LRT, a dribble, the crowds, EDSA, the rain
  32-36   night: the groove falls back to the gongs, a tuko calls
  36-44   Makati: everything back and bigger
  44-46   breakdown hit and snare roll
  46-50   the four words, a gong on each
  50-52   strobe build, 52-53 silence
  53-60   sunset over the bay: the last hit, the kulintang line again, the waves

usage: python3 manila_score.py <out.wav> [sfx dir]
The game's own thunder and till are laid over it when the sfx dir is given.
"""
import os, sys
import numpy as np
from synth import *

DUR = 60.0
BEAT = .5
BAR = 2.0
mx = Mixer(DUR)
place = mx.place


# ---------------------------------------------------------------- Filipino instruments
def kulintang(m, d=1.1):
    """A bossed bronze gong-chime: a bright strike, the pitch settling, a few inharmonic partials."""
    t = t_axis(d)
    f = note(m)
    bend = 1 + .012 * np.exp(-t * 40)
    s = (sine(f * bend, d) + .35 * sine(f * 2.0 * bend, d) * np.exp(-t * 5) + .18 * sine(f * 2.92, d) * np.exp(-t * 9)
         + .1 * sine(f * 4.1, d) * np.exp(-t * 14))
    strike = filt(noise(.015), 'bandpass', [1500, 6000]) * .3
    s[:len(strike)] += strike
    return s * np.exp(-t * 3.2) * adsr(d, .001, 1, 1, .05) * .45


def agung(f=74.0, d=3.4):
    """The big hanging gong: a low boss note beating against its neighbour, a soft mallet thump."""
    t = t_axis(d)
    s = (sine(f * (1 + .03 * np.exp(-t * 8)), d) + .6 * sine(f * 1.007, d) + .3 * sine(f * 2.4, d) * np.exp(-t * 2)
         + .15 * sine(f * 3.9, d) * np.exp(-t * 4))
    thump = filt(noise(.05), 'lowpass', 380) * np.exp(-t_axis(.05) * 60) * 1.5
    s[:len(thump)] += thump
    return np.tanh(s * np.exp(-t * 1.25) * .9) * .7


_ks = {}


def string(m, d=.6, damp=.996, bright=4200.0):
    """A plucked steel string (Karplus-Strong), cached per note."""
    k = (m, d, damp, bright)
    if k in _ks: return _ks[k]
    f = note(m)
    L = max(2, int(round(SR / f)))
    n = int(d * SR)
    y = np.zeros(L + n)
    y[:L] = filt(rng.uniform(-1, 1, L), 'lowpass', bright, 1)
    for start in range(L, L + n, L):
        end = min(start + L, L + n)
        i = np.arange(start, end)
        y[i] = damp * .5 * (y[i - L] + y[i - L - 1])
    out = y[L:] * adsr(d, .001, 1, 1, .04)
    out /= max(1e-6, np.max(np.abs(out)))
    _ks[k] = out
    return out


def tremolo(m, d, rate=.0625, g=.5):
    """A bandurria holding a note the rondalla way: the same string picked again and again."""
    out = np.zeros(int(d * SR))
    k = 0
    while k * rate < d - .02:
        s = string(m, .22, .993, 5200) * (1 if k % 2 == 0 else .7)
        i = int(k * rate * SR)
        out[i:i + len(s)] += s[:len(out) - i]
        k += 1
    return out * g * adsr(d, .02, 1, 1, .06)


def strum(chord, down=True, g=.5):
    """An octavina strummed: the strings a few milliseconds apart."""
    notes = list(chord) if down else list(reversed(chord))
    out = np.zeros(int(.9 * SR))
    for j, m in enumerate(notes):
        s = string(m, .8, .995, 3600)
        i = int(j * .011 * SR)
        out[i:i + len(s)] += s[:len(out) - i]
    return out * g / len(notes) * 1.6


def clack(g=1.0):
    """Tinikling bamboo poles knocked together."""
    d = .09; t = t_axis(d)
    body = bandsweep(noise(d), lambda s: 1250, q=4) * np.exp(-t * 55)
    return (body * 2.2 + sine(620, d) * np.exp(-t * 70) * .4) * g


# ---------------------------------------------------------------- the city's sounds
def big_bell(m, d=4.5):
    """A cathedral bell: hum, prime, tierce, quint and nominal ringing on."""
    t = t_axis(d)
    f = note(m)
    s = sum(a * sine(f * r, d, rng.uniform()) * np.exp(-t * k) for r, a, k in
            ((.5, .7, .45), (1, 1, .7), (1.19, .55, 1.0), (1.5, .35, 1.3), (2, .45, 1.6), (2.52, .2, 2.4), (3.01, .15, 3)))
    strike = filt(noise(.02), 'bandpass', [800, 5000]) * .25
    s[:len(strike)] += strike
    return s * .32


def bugle(notes, t_note=.16, hold=.7):
    """A bugle call on the natural notes: a bright brassy tone with a little vibrato."""
    out = []
    for j, m in enumerate(notes):
        d = hold if j == len(notes) - 1 else t_note
        t = t_axis(d)
        f = note(m) * (1 + .004 * np.sin(2 * np.pi * 5.5 * t) * (t > .15))
        s = saw(f, d) * .6 + saw(f * 2, d) * .2
        s = filt(s, 'bandpass', [note(m) * .9, 3800], 2)
        out.append(s * adsr(d, .012, .2, .85, .04))
    return np.concatenate(out) * .5


def horn(f1=415.0, f2=523.0, d=.32):
    """A jeepney's two-tone horn."""
    t = t_axis(d)
    s = (saw(f1, d) + saw(f2, d)) * .5
    s = filt(s, 'bandpass', [300, 2600], 2)
    return np.tanh(s * 2.2) * adsr(d, .01, .2, .9, .03) * .45


def honks(times, seed=0):
    """Rush-hour honking: horns of every pitch, short and long."""
    out = np.zeros(int((max(times) + .8) * SR))
    for j, t0 in enumerate(times):
        f = 330 + ((j * 97 + seed * 31) % 260)
        h = horn(f, f * 1.26, .18 + .3 * ((j * 7) % 3) / 2)
        i = int(t0 * SR)
        out[i:i + len(h)] += h * (.5 + .5 * ((j * 13) % 5) / 4)
    return out


def tricycle(d=2.0, pan_from=-.8, pan_to=.8):
    """A tricycle's two-stroke putting past."""
    t = t_axis(d)
    rate = 26 + 6 * np.sin(t * 3.1)
    ph = np.cumsum(rate) / SR
    pulses = np.exp(-((ph % 1.0) * 9)) * (1 + .3 * rng.standard_normal(len(t)))
    x = filt(pulses, 'bandpass', [90, 1400], 2) * np.exp(-((t / d - .5) / .32) ** 2)
    pan = pan_from + (pan_to - pan_from) * t / d
    return np.stack([x * np.cos((pan + 1) * np.pi / 4), x * np.sin((pan + 1) * np.pi / 4)], 1) * .9


def lrt(d=2.2):
    """A train on the viaduct: a rolling rumble swelling and passing, the wheels clacking over the joints."""
    t = t_axis(d)
    swell = np.exp(-((t / d - .55) / .3) ** 2)
    rumble = filt(noise(d), 'lowpass', 220, 2) * 1.4 + filt(noise(d), 'bandpass', [400, 1800], 2) * .3
    out = rumble * swell
    for k in np.arange(.15, d, .42):
        for o in (0, .07):
            i = int((k + o) * SR)
            c = filt(noise(.03), 'bandpass', [600, 3000]) * np.exp(-t_axis(.03) * 90) * 1.4
            out[i:i + len(c)] += c[:len(out) - i] * swell[min(i, len(swell) - 1)]
    return out * .8


def dribble(times):
    """A basketball bounced on the street, then off the rim."""
    out = np.zeros(int((max(times) + 1.0) * SR))
    for t0 in times[:-1]:
        d = .2; t = t_axis(d)
        b = sine(95 * (1 + .6 * np.exp(-t * 60)), d) * np.exp(-t * 22) + filt(noise(d), 'bandpass', [200, 1200]) * np.exp(-t * 60) * .4
        i = int(t0 * SR); out[i:i + len(b)] += b
    d = .9; t = t_axis(d)
    rim = sum(sine(f, d, rng.uniform()) * np.exp(-t * k) for f, k in ((610, 5), (1330, 7), (2230, 9))) * .35
    i = int(times[-1] * SR); out[i:i + len(rim)] += rim
    return out * .8


def sizzle(d):
    """Oil spitting on a street cook's griddle."""
    t = t_axis(d)
    crackle = (rng.uniform(0, 1, len(t)) > .9985) * rng.uniform(.3, 1, len(t))
    x = filt(noise(d), 'highpass', 3500) * .12 + filt(crackle, 'bandpass', [2000, 8000]) * 1.5
    return x * np.minimum(1, np.minimum(t / .2, (d - t) / .3))


def buzz(d=1.6):
    """The overhead wires humming, and a spark."""
    t = t_axis(d)
    hum = sum(sine(60 * h, d) / h for h in (1, 2, 3, 5)) * .25
    hum *= np.minimum(1, np.minimum(t / .3, (d - t) / .4))
    zap = filt(noise(.12), 'highpass', 2500) * np.exp(-t_axis(.12) * 30)
    i = int(.35 * SR); hum[i:i + len(zap)] += zap * .8
    return hum


def tuko():
    """A tuko gecko calling: a croaking 'tu-ko', twice."""
    out = []
    for f, d in ((420, .14), (330, .26)):
        t = t_axis(d)
        am = .5 + .5 * np.sign(np.sin(2 * np.pi * 38 * t))
        s = filt(saw(f, d), 'bandpass', [250, 1600], 2) * am * np.sin(np.pi * t / d) ** .6
        out += [s, np.zeros(int(.05 * SR))]
    call = np.concatenate(out)
    return np.concatenate([call, np.zeros(int(.35 * SR)), call]) * .6


def rain(d):
    t = t_axis(d)
    x = np.stack([filt(noise(d), 'bandpass', [700, 9000]), filt(noise(d), 'bandpass', [700, 9000])], 1)
    return x * .14 * np.minimum(1, np.minimum(t / .3, (d - t) / .4))[:, None]


def waves(d):
    """Manila Bay lapping at the seawall: slow swells of filtered noise."""
    t = t_axis(d)
    swell = .55 + .45 * np.sin(2 * np.pi * t / 3.1) ** 2
    x = np.stack([filt(noise(d), 'lowpass', 900, 2), filt(noise(d), 'lowpass', 900, 2)], 1)
    return x * swell[:, None] * .22 * np.minimum(1, np.minimum(t / .8, (d - t) / 1.2))[:, None]


# ---------------------------------------------------------------- the arrangement
EM, CM, GM, DM = (52, 55, 59), (48, 52, 55), (43, 47, 50), (50, 54, 57)
PROG = [EM, CM, GM, DM]


def low(ch): return 28 + (ch[0] - 28) % 12  # the chord's root in the bass octave, E1 to D#2

STRUM = {EM: (40, 47, 52, 55, 59, 64), CM: (48, 52, 55, 60, 64), GM: (43, 47, 50, 55, 59, 67), DM: (50, 57, 62, 66)}
# the kulintang line: E minor pentatonic in eighths, None for a rest
LINE = [76, 79, 81, 79, 76, 74, 76, None, 79, 81, 83, 81, 79, None, 76, None,
        83, 86, 83, 81, 79, 81, 79, 76, 74, 76, 79, None, 76, None, None, None]

# 0-12: dawn to dusk
place('music', pad([40, 47, 52, 59, 64], 12.0, bright=1300, attack=2.5), 0, .5, send=.45)
place('bass', sine(note(28), 11.6) * adsr(11.6, 3, 1, 1, 1.2) * .2, 0, 1)
place('drums', agung(74, 4.0), 0.0, .75, send=.4)
place('sfx', waves(4.4), 0.0, 1, send=.1)
for i, m in enumerate(LINE[:28]):
    if m is not None: place('music', kulintang(m), .5 + i * .25, .5, pan=(-.35 if i % 2 else .35), send=.45)
# 4.0 the Cathedral: its bells; 8.0 Luneta: a bugle; 10.0 City Hall: the clock strikes
place('drums', boom(2.4, 66, 30), 4.0, .32, send=.4)
for k, (t0, m) in enumerate(((4.0, 52), (4.75, 59), (5.5, 52), (6.25, 59))):
    place('sfx', big_bell(m), t0, .7 - k * .1, pan=-.25, send=.6)
place('music', pad([45, 52, 57, 60, 64], 4.2, bright=1700, attack=.8), 4.0, .4, send=.4)
place('drums', agung(66, 3.0), 8.0, .6, send=.4)
place('sfx', bugle([67, 72, 76, 79, 76, 79], .17, .9), 8.15, .55, pan=.2, send=.5)
place('drums', boom(2.0, 70, 32), 10.0, .35, send=.4)
place('sfx', big_bell(40, 3.2), 10.0, .9, send=.6)
for i in range(16):
    t0 = 10.0 + i * .1
    if t0 >= 11.5: break
    place('drums', hat() * (.4 + .6 * i / 16), t0, .45, pan=.3 if i % 2 else -.3)
place('fx', riser(1.5, 220, 1600), 10.0, .9, send=.3)
for i in range(16):
    place('drums', snare(.35 + .65 * i / 16), 10.2 + 1.3 * (1 - (1 - i / 16) ** 1.4), .5, send=.2)
place('fx', reverse_swell(.5), 11.5, .8)

# 12-44: the streets, then the night, then Makati
kicks = []
for b in range(16):
    t0 = 12.0 + b * BAR
    ch = PROG[b % 4]
    night = 10 <= b <= 11
    late = b >= 12 or 8 <= b <= 9
    if b % 4 == 0: place('drums', crash(), t0, .5 if not night else .25, send=.3)
    if night:
        # the groove falls back: a kick on the one, the gongs and the line
        kicks.append(t0); place('drums', kick(.8), t0, .8)
        place('drums', agung(74 if b == 10 else 66, 3.0), t0, .55, send=.4)
        for s in range(8): place('drums', hat(), t0 + s * BEAT / 2, .18, pan=.35)
        for i in range(8):
            m = LINE[(b - 10) * 8 + i]
            if m is not None: place('music', kulintang(m, 1.4), t0 + i * .25, .5, pan=(-.4 if i % 2 else .4), send=.55)
        place('music', pad(list(ch) + [ch[0] + 12], BAR + .3, bright=1200, attack=.4), t0, .55, send=.5)
        place('bass', sine(note(low(ch)), BAR) * adsr(BAR, .01, 1, 1, .3) * .5, t0, 1)
        continue
    pattern = (0, 1.5, 2, 3.5) if late else (0, 1.5, 2)
    for p in pattern: kicks.append(t0 + p * BEAT); place('drums', kick(), t0 + p * BEAT, .95)
    for p in (1, 3): place('drums', clap(), t0 + p * BEAT, .55, send=.15); place('drums', snare(.55), t0 + p * BEAT, .4)
    for s in range(16):
        place('drums', hat(open_=(s % 8 == 6)), t0 + s * BEAT / 4, (.5 if s % 4 == 2 else .28) * (1.2 if late else 1), pan=.35)
    # tinikling: the poles knocked twice, then a long beat
    for s in (2, 3, 10, 11): place('drums', clack(), t0 + s * BEAT / 4, .32, pan=-.4, send=.1)
    if b % 4 == 3:
        for k, (p, f) in enumerate(((3, 140), (3.25, 120), (3.5, 100), (3.75, 82))): place('drums', tom(f), t0 + p * BEAT, .6, pan=-.3 + k * .2, send=.2)
    root = low(ch)
    for s in range(8):
        place('bass', bass_note(root + (12 if s in (3, 7) else 0), BEAT / 2 * .9, bright=420 + (260 if late else 0)), t0 + s * BEAT / 2, .62)
    place('music', pad(list(ch) + [ch[0] + 12], BAR + .3, bright=2200 if late else 1600, attack=.06), t0, .5, send=.3)
    # the octavina strums the chord: down on one, up on the and-of-two, down on three
    st = STRUM[ch]
    for p, down in ((0, True), (1.5, False), (2, True), (3, False)):
        place('music', strum(st, down), t0 + p * BEAT, .42, pan=.3, send=.2)
    # the bandurria's tremolo melody from the second bar
    if b >= 1:
        top = [ch[2] + 12, ch[1] + 12, ch[0] + 24, ch[2] + 12]
        for k in range(2):
            place('music', tremolo(top[(b + k) % 4] + (12 if late and k == 1 else 0), BAR / 2 * .96), t0 + k * BAR / 2, .38,
                  pan=-.3, send=.3)
    # the kulintang joins from bar four, an eighth-note run over the top
    if b >= 4:
        for i in range(8):
            m = LINE[(b * 8 + i) % len(LINE)]
            if m is not None: place('music', kulintang(m + (12 if late and i % 4 == 0 else 0), .8), t0 + i * .25, .3,
                                    pan=(-.45 if i % 2 else .45), send=.3)
    if b == 12 or b == 14:  # Makati: brass on the downbeat
        place('music', braam([ch[0] - 12] + list(ch)), t0, .5, send=.45)
        place('drums', boom(1.8, 80, 36), t0, .35, send=.4)

# 44-46: breakdown hit, then the roll
place('drums', boom(2.2), 44.0, .9, send=.45)
place('music', braam([28, 40, 47, 52, 55]), 44.0, .85, send=.5)
place('drums', agung(66, 3.0), 44.0, .6, send=.4)
place('drums', crash(3.0), 44.0, .45, send=.4)
for i in range(24):
    place('drums', snare(.3 + .7 * i / 24), 45.0 + (i / 24) ** .8 * 1.0, .5, send=.2)
place('fx', riser(1.0, 300, 1800), 45.0, .8)

# 46-50: the four words, a gong and a hit on each, the groove at full tilt underneath
for b in range(2):
    t0 = 46.0 + b * BAR
    for half in range(2):
        tw = t0 + half * 1.0
        place('drums', agung(74 if half == 0 else 83, 2.4), tw, .6, send=.4)
        place('fx', boom(.8, 110, 55) * .7, tw, .4)
        place('drums', crash(1.6), tw, .35, send=.3)
    ch = PROG[b * 2]
    for p in range(4): kicks.append(t0 + p * BEAT); place('drums', kick(1.15), t0 + p * BEAT, 1.0)
    for p in (1, 3): place('drums', clap(), t0 + p * BEAT, .65, send=.15); place('drums', snare(.8), t0 + p * BEAT, .5)
    for s in range(16): place('drums', hat(), t0 + s * BEAT / 4, .42, pan=.35)
    for s in range(8): place('drums', clack(), t0 + s * BEAT / 2 + BEAT / 4, .3, pan=-.4)
    root = low(ch)
    for s in range(16): place('bass', bass_note(root + (12 if s % 4 == 3 else 0), BEAT / 4 * .92, bright=700), t0 + s * BEAT / 4, .68)
    place('music', pad(list(ch) + [ch[0] + 12, ch[1] + 12], BAR + .3, bright=3000, attack=.03), t0, .6, send=.3)
    for i in range(8):
        m = LINE[16 + i]
        if m is not None: place('music', kulintang(m + 12, .7), t0 + i * .25, .32, pan=(-.45 if i % 2 else .45), send=.3)
    for k in range(2): place('music', tremolo(ch[2] + 24 - k * 5, .96), t0 + k, .34, pan=-.3, send=.3)

# 50-52: strobe build
for i in range(32): place('drums', snare(.35 + .65 * i / 32), 50.0 + i * .0625, .55, send=.15)
place('fx', riser(2.0, 220, 2600), 50.0, 1.0, send=.3)
for i in range(8): place('music', kulintang(LINE[i * 2] or 79, .4), 50.0 + i * .25, .3, pan=(-.4 if i % 2 else .4))

# 52-53: silence, then the suck into the last hit
place('fx', reverse_swell(.9), 52.1, .9)

# 53-60: sunset over the bay
place('drums', boom(5.0, 75, 26), 53.0, 1.0, send=.6)
place('drums', agung(66, 5.0), 53.0, .8, send=.5)
place('drums', crash(4.0), 53.0, .55, send=.5)
place('music', braam([28, 40, 47, 52, 55], d=4.5), 53.0, .9, send=.6)
place('music', pad([40, 52, 55, 59, 64, 67, 71], 6.8, bright=2400, attack=1.2), 53.2, .65, send=.6)
for i, m in enumerate(LINE[:16]):
    if m is not None: place('music', kulintang(m, 1.6), 53.6 + i * .3, .42, pan=(-.35 if i % 2 else .35), send=.65)
for t0, m in ((54.0, 64), (56.0, 59)):
    place('sfx', big_bell(m, 4.0), t0, .3, pan=-.3, send=.6)
place('sfx', waves(7.0), 53.0, 1.1, send=.1)

# ---------------------------------------------------------------- the streets under every cut
place('sfx', buzz(1.7), 14.0, .8, pan=.3, send=.1)                                    # spaghetti wires
place('sfx', murmur(2.0, .8), 14.0, 1, send=.1)
place('sfx', honks([0, .38]) * 1.0, 16.15, .9, pan=-.2, send=.15)                    # a jeepney's horn
place('sfx', tricycle(1.9), 16.0, .7)
place('sfx', np.stack([lrt(2.2)] * 2, 1), 18.0, .9, send=.15)                         # the LRT overhead
place('sfx', dribble([20.05, 20.5, 20.95, 21.4, 21.75]), 20.0, .8, pan=.35, send=.2)  # a game on the street
place('sfx', murmur(2.0, 1.0), 20.0, .8, send=.1)
place('sfx', murmur(4.0, 1.5), 22.0, 1, send=.1)                                      # Binondo, Quiapo
place('sfx', sizzle(2.0), 22.0, .9, pan=.3)
for k, t0 in enumerate((24.0, 24.6, 25.2)): place('sfx', big_bell(59 if k % 2 else 64, 2.5), t0, .4, pan=-.3, send=.5)
place('sfx', tricycle(2.0, .8, -.6), 26.0, .8)                                          # the terminal
place('sfx', honks([.1], seed=3), 26.6, .6, pan=.4)
place('sfx', murmur(2.0, 1.2), 26.0, .8, send=.1)
place('sfx', honks([.0, .25, .4, .7, .95, 1.1, 1.35, 1.6, 1.75], seed=1), 28.0, .75, send=.15)  # EDSA
place('sfx', rain(2.3), 30.0, 1, send=.1)                                              # the monsoon
place('sfx', tuko(), 32.4, .5, pan=-.5, send=.4)                                        # a tuko in the night
place('sfx', tuko(), 34.6, .35, pan=.5, send=.5)
for t0 in (13.85, 15.85, 17.85, 19.85, 21.85, 23.85, 25.85, 27.85, 29.85, 31.85, 35.85, 39.85):
    place('fx', whoosh(.6), t0, .3, send=.2)
place('drums', boom(1.6, 90, 40), 42.0, .45, send=.4)                                  # ONE CITY.

sfx_dir = sys.argv[2] if len(sys.argv) > 2 else None
if sfx_dir and os.path.isdir(sfx_dir):
    S = {n[:-4]: load_sfx(os.path.join(sfx_dir, n)) for n in os.listdir(sfx_dir) if n.endswith('.wav')}
    for t0, name, g in ((30.05, 'thunder', .75), (32.3, 'cash', .5), (55.9, 'cash', .3)):
        if name in S: place('sfx', S[name], t0, g, send=.15)

# ---------------------------------------------------------------- mix
mx.finish(sys.argv[1], kicks, breaths=((11.5, 11.985), (52.0, 52.985)))
