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
import os, sys, wave
import numpy as np
from scipy import signal

SR = 48000
DUR = 60.0
N = int(SR * DUR)
BEAT = .5
BAR = 2.0
rng = np.random.default_rng(11)

buses = {k: np.zeros((N, 2), np.float32) for k in ('drums', 'bass', 'music', 'fx', 'sfx')}
sends = np.zeros((N, 2), np.float32)  # into the big hall reverb


def note(m): return 440.0 * 2 ** ((m - 69) / 12)


def place(bus, sig, t, gain=1.0, pan=0.0, send=0.0):
    """Mixes a mono or stereo signal into a bus at time t (seconds)."""
    i = int(round(t * SR))
    if i >= N or len(sig) == 0: return
    if i < 0: sig = sig[-i:]; i = 0
    sig = sig[:N - i]
    if sig.ndim == 1:
        l = np.cos((pan + 1) * np.pi / 4); r = np.sin((pan + 1) * np.pi / 4)
        st = np.stack([sig * l, sig * r], 1) * np.sqrt(2)
    else:
        st = sig
    buses[bus][i:i + len(st)] += st * gain
    if send: sends[i:i + len(st)] += st * gain * send


# ---------------------------------------------------------------- oscillators and filters
def t_axis(d): return np.arange(int(d * SR)) / SR


def saw(freq, d, phase0=None):
    """PolyBLEP sawtooth; freq may be a scalar or a per-sample array."""
    n = int(d * SR)
    f = np.broadcast_to(np.asarray(freq, np.float64), (n,)) if np.ndim(freq) else np.full(n, float(freq))
    dt = f / SR
    ph = (np.cumsum(dt) + (rng.uniform() if phase0 is None else phase0)) % 1.0
    y = 2 * ph - 1
    m = ph < dt
    x = ph[m] / dt[m]; y[m] -= x + x - x * x - 1
    m = ph > 1 - dt
    x = (ph[m] - 1) / dt[m]; y[m] -= x * x + x + x + 1
    return y


def sine(freq, d, phase0=0.0):
    n = int(d * SR)
    f = np.broadcast_to(np.asarray(freq, np.float64), (n,)) if np.ndim(freq) else np.full(n, float(freq))
    return np.sin(2 * np.pi * (np.cumsum(f) / SR + phase0))


def noise(d): return rng.standard_normal(int(d * SR))


def filt(x, kind, fc, order=2):
    sos = signal.butter(order, fc, btype=kind, fs=SR, output='sos')
    return signal.sosfilt(sos, x, axis=0)


def sweep(x, kind, fc_of_t, order=2, block=256):
    """Filter whose cutoff follows fc_of_t(seconds) (low/high pass), block by block with carried state."""
    out = np.empty_like(x)
    zi = None
    for s in range(0, len(x), block):
        fc = float(np.clip(fc_of_t(s / SR), 20, SR * .45))
        sos = signal.butter(order, fc, btype=kind, fs=SR, output='sos')
        if zi is None: zi = np.zeros((sos.shape[0], 2))
        out[s:s + block], zi = signal.sosfilt(sos, x[s:s + block], zi=zi)
    return out


def bandsweep(x, fc_of_t, q=1.2, block=256):
    out = np.empty_like(x)
    zi = None
    for s in range(0, len(x), block):
        fc = float(np.clip(fc_of_t(s / SR), 40, SR * .4))
        lo, hi = fc / (1 + .5 / q), fc * (1 + .5 / q)
        sos = signal.butter(2, [lo, hi], btype='bandpass', fs=SR, output='sos')
        if zi is None: zi = np.zeros((sos.shape[0], 2))
        out[s:s + block], zi = signal.sosfilt(sos, x[s:s + block], zi=zi)
    return out


def adsr(d, a=.005, dec=.1, s=1.0, r=.05):
    n = int(d * SR); t = np.arange(n) / SR
    e = np.where(t < a, t / max(a, 1e-6), s + (1 - s) * np.exp(-(t - a) / max(dec, 1e-6)))
    tail = int(r * SR)
    if tail and n > tail: e[-tail:] *= np.linspace(1, 0, tail) ** 2
    return e


def expdecay(d, tau): return np.exp(-t_axis(d) / tau)


# ---------------------------------------------------------------- instruments
def kick(punch=1.0):
    d = .55; t = t_axis(d)
    f = 46 + 130 * np.exp(-t * 28)
    body = sine(f, d) * np.exp(-t * 6.5)
    click = filt(noise(.01), 'highpass', 3000) * .35
    body[:len(click)] += click
    return np.tanh(body * 1.6 * punch) * .9


def boom(d=3.2, f0=62, f1=27):
    """Trailer impact: a falling sub, a cracked noise burst and a metallic ring."""
    t = t_axis(d)
    sub = sine(f1 + (f0 - f1) * np.exp(-t * 2.2), d) * np.exp(-t * 1.1)
    crack = filt(noise(d), 'lowpass', 2400) * np.exp(-t * 9) * .9
    metal = sum(sine(f, d, rng.uniform()) * np.exp(-t * k) for f, k in ((173, 3.2), (461, 4.5), (1013, 6), (2711, 8))) * .12
    return np.tanh((sub * 1.0 + crack + metal * 1.6) * 1.3)


def snare(g=1.0):
    d = .3; t = t_axis(d)
    n = filt(noise(d), 'bandpass', [900, 7000]) * np.exp(-t * 18)
    body = sine(185 * (1 + .3 * np.exp(-t * 40)), d) * np.exp(-t * 24) * .6
    return (n * .9 + body) * g


def clap():
    d = .35; t = t_axis(d)
    env = np.zeros_like(t)
    for k, o in enumerate((0, .011, .022)): env += (t >= o) * np.exp(-np.maximum(t - o, 0) * (60 if k < 2 else 16))
    return filt(noise(d), 'bandpass', [1000, 5200]) * env * .8


def hat(open_=False):
    d = .25 if open_ else .06; t = t_axis(d)
    return filt(noise(d), 'highpass', 7500) * np.exp(-t * (14 if open_ else 70)) * .5


def tom(f=110, d=.6):
    t = t_axis(d)
    return np.tanh((sine(f * (1 + .5 * np.exp(-t * 18)), d) * np.exp(-t * 6) + filt(noise(d), 'lowpass', 1500) * np.exp(-t * 22) * .3) * 1.4)


def taiko():
    d = 1.2; t = t_axis(d)
    return np.tanh((sine(62 * (1 + .45 * np.exp(-t * 14)), d) * np.exp(-t * 3.6) + filt(noise(d), 'lowpass', 700) * np.exp(-t * 12) * .5) * 1.8)


def crash(d=2.6):
    t = t_axis(d)
    shimmer = sum(sine(f, d, rng.uniform()) for f in (3150, 4270, 5340, 6620, 8100, 9330)) / 6
    return (filt(noise(d), 'highpass', 4200) * .7 + shimmer * .25) * np.exp(-t * 1.6) * .55


def braam(chord, d=2.8):
    """Low brass blast: detuned saws through a filter that bites open and closes."""
    t = t_axis(d)
    out = np.zeros((len(t), 2))
    for m in chord:
        for k, det in enumerate((-.13, -.05, .05, .13)):
            s = saw(note(m) * (1 + det / 100), d)
            out[:, k % 2] += s
    fc = lambda s: 180 + 2600 * np.exp(-s * 2.2) * (1 - np.exp(-s * 40))
    out = np.stack([sweep(out[:, 0], 'lowpass', fc, 2), sweep(out[:, 1], 'lowpass', fc, 2)], 1)
    env = adsr(d, .015, .9, .45, .9)[:, None]
    return np.tanh(out * env / len(chord) * 1.6) * .8


def pad(chord, d, bright=900.0, attack=.6):
    out = np.zeros((int(d * SR), 2))
    for m in chord:
        for k, det in enumerate((-.09, -.03, .03, .09)):
            out[:, k % 2] += saw(note(m) * (1 + det / 100), d)
    out = filt(out, 'lowpass', bright, 2) / (len(chord) * 2)
    return out * adsr(d, attack, .5, 1, min(.8, d * .4))[:, None]


def pluck(m, d=.28, bright=3200.0):
    t = t_axis(d)
    s = saw(note(m), d) * .6 + sine(note(m) * 2, d) * .25
    s = sweep(s, 'lowpass', lambda x: 300 + bright * np.exp(-x * 14), 2)
    return s * np.exp(-t * 9) * adsr(d, .002, 1, 1, .02)


def bell(m, d=1.6):
    t = t_axis(d)
    f = note(m)
    s = sine(f, d) + .5 * sine(f * 2.76, d) * np.exp(-t * 3) + .25 * sine(f * 5.4, d) * np.exp(-t * 6)
    return s * np.exp(-t * 2.4) * adsr(d, .002, 1, 1, .05) * .5


def bass_note(m, d, bright=420.0):
    s = saw(note(m), d) * .7 + sine(note(m), d) * .6
    s = filt(s, 'lowpass', bright, 2)
    return np.tanh(s * 1.5) * adsr(d, .003, d * .8, .55, .015)


def whoosh(d=.7, peak=.55, lo=250.0, hi=4200.0):
    t = t_axis(d)
    shape = np.exp(-((t / d - peak) / .22) ** 2)
    fc = lambda s: lo + (hi - lo) * np.exp(-((s / d - peak) / .25) ** 2)
    x = bandsweep(noise(d), fc, q=.9)
    pan = np.clip((t / d) * 2 - 1, -1, 1)
    l = x * shape * np.cos((pan + 1) * np.pi / 4); r = x * shape * np.sin((pan + 1) * np.pi / 4)
    return np.stack([l, r], 1) * 1.6


def riser(d, f0=180.0, f1=1400.0):
    t = t_axis(d)
    k = (t / d) ** 2
    nz = sweep(noise(d), 'highpass', lambda s: 120 + 6000 * (s / d) ** 2.2, 2) * .5
    tone = sum(saw(f0 * (f1 / f0) ** ((t / d) ** 1.6) * (1 + det / 100), d) for det in (-.4, 0, .4)) / 3
    tone = filt(tone, 'lowpass', 5000) * .25
    return (nz + tone) * k


def reverse_swell(d=1.0):
    """A reversed cymbal-and-reverb tail that sucks into the next hit."""
    t = t_axis(d)
    x = filt(noise(d), 'highpass', 1800) * np.exp(-t * 3.2)
    x += boom(d, 90, 40)[: len(x)] * .25
    x = x[::-1]
    return x * np.linspace(0, 1, len(x)) ** 1.5


def env_follow_kicks(times, depth=.6, rel=.18):
    """Sidechain gain: dips under every kick and breathes back."""
    g = np.ones(N, np.float32)
    k = np.exp(-np.arange(int(rel * 4 * SR)) / (rel * SR)).astype(np.float32)
    for t in times:
        i = int(t * SR)
        if i >= N: continue
        m = min(len(k), N - i)
        g[i:i + m] = np.minimum(g[i:i + m], 1 - depth * k[:m])
    return g


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
def load_sfx(path):
    with wave.open(path) as w:
        ch = w.getnchannels(); sr = w.getframerate()
        d = np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32) / 32768
    if ch == 2: d = d.reshape(-1, 2).mean(1)
    return np.interp(np.arange(0, len(d), sr / SR), np.arange(len(d)), d)


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
for a, b, base in ((22.0, 24.0, 44), (38.0, 40.0, 70)):
    d = b - a; t = t_axis(d)
    f = base + base * .9 * (t / d) ** 1.4
    eng = saw(f, d) * .5 + saw(f * 2.01, d) * .25
    eng = sweep(eng, 'lowpass', lambda s: 900 + 1500 * s / d) * np.minimum(1, np.minimum(t / .15, (d - t) / .2))
    place('sfx', eng * .35, a, 1, pan=0)
# a whoosh under each headline
for t0 in (13.8, 17.8, 21.8, 25.8, 29.8, 33.8, 35.8, 37.8, 39.8, 41.8, 43.8):
    place('fx', whoosh(.6), t0, .35, send=.2)

# ---------------------------------------------------------------- mix
side = env_follow_kicks(kicks, .55, .16)[:, None]
buses['bass'] *= side
buses['music'] *= (1 - (1 - side) * .5)
# the two breaths before a drop: everything stops dead and only the hall rings on
tt = np.arange(N) / SR
for a, b in ((11.5, 11.985), (52.0, 52.985)):
    gate = np.clip(np.maximum((a - tt) / .02, (tt - b) / .015), 0, 1).astype(np.float32)[:, None]
    for k in ('drums', 'bass', 'music', 'sfx'): buses[k] *= gate


def hall(x, d=3.2):
    ir_t = t_axis(d)
    irl = noise(d) * np.exp(-ir_t * 2.1); irr = noise(d) * np.exp(-ir_t * 2.1)
    irl = filt(irl, 'lowpass', 5200); irr = filt(irr, 'lowpass', 5200)
    pre = int(.02 * SR)
    irl = np.concatenate([np.zeros(pre), irl]); irr = np.concatenate([np.zeros(pre), irr])
    out = np.stack([signal.fftconvolve(x[:, 0], irl)[:N], signal.fftconvolve(x[:, 1], irr)[:N]], 1)
    return out / np.max(np.abs(irl)) * .02


mix = (buses['drums'] * .9 + buses['bass'] * .5 + buses['music'] * 1.3 + buses['fx'] * .8 + buses['sfx'] * .9
       + hall(sends) * 1.0)
mix = filt(mix, 'highpass', 30, 2)
# a low shelf: keep the weight, lose the mud
mix -= .45 * filt(mix, 'lowpass', 75, 2)
# and a little presence
mix += .25 * filt(mix, 'bandpass', [1800, 5000], 1)
# glue: a slow compressor, then a soft clip and the master fade
rms = np.sqrt(signal.sosfilt(signal.butter(1, 6, fs=SR, output='sos'), (mix ** 2).mean(1)) + 1e-9)
gain = np.minimum(1, (.28 / rms) ** .45)
mix *= gain[:, None]
mix = np.tanh(mix * 1.3) / np.tanh(1.3)
mix *= np.clip((DUR - tt) / 1.2, 0, 1)[:, None] ** 1.5
mix /= np.max(np.abs(mix)) / .93
pcm = (mix * 32767).astype(np.int16)
with wave.open(sys.argv[1], 'wb') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
print(sys.argv[1], f'{DUR:.1f}s')
