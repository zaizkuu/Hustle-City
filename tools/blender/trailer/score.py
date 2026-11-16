"""The trailer's music and sound, synthesised from nothing: python score.py <out.wav>

Runs on Blender's own Python (numpy only, no scipy). 120 beats a minute, so a beat is half a second and every cut
of the film (shots.py) lands on one. A lone piano figure in A minor for the arrival, the same notes turned into a
dance track as the business grows, a breakdown on the roof, and the figure alone again for whoever comes next.
"""
import sys
import wave

import numpy as np

SR = 44100
BEAT = .5
LENGTH = 68.0
rng = np.random.default_rng(7)
N = int(LENGTH * SR)


def hz(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def tt(d):
    return np.arange(int(d * SR)) / SR


def env(d, a=.005, r=.05, curve=1.0):
    n = int(d * SR)
    e = np.ones(n)
    na, nr = max(1, int(a * SR)), max(1, int(r * SR))
    na, nr = min(na, n), min(nr, n)
    e[:na] *= np.linspace(0, 1, na) ** curve
    e[-nr:] *= np.linspace(1, 0, nr) ** curve
    return e


def decay(d, tau):
    return np.exp(-tt(d) / tau)


def lowpass(x, cutoff, order=2):
    """A static low-pass by shaping the spectrum (no scipy here)."""
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    X *= 1 / np.sqrt(1 + (f / cutoff) ** (2 * order))
    return np.fft.irfft(X, len(x))


def highpass(x, cutoff, order=2):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    X *= 1 - 1 / np.sqrt(1 + (f / cutoff) ** (2 * order))
    return np.fft.irfft(X, len(x))


def saw(freq, d, detune=0.0):
    t = tt(d)
    f = freq * (1 + detune)
    ph = (t * f + rng.uniform()) % 1.0
    return 2 * ph - 1


def stereo(x, pan=0.0):
    l = np.cos((pan + 1) * np.pi / 4)
    r = np.sin((pan + 1) * np.pi / 4)
    return np.stack([x * l, x * r])


class Bus:
    def __init__(self):
        self.b = np.zeros((2, N))

    def add(self, t, x, gain=1.0, pan=0.0):
        if x.ndim == 1:
            x = stereo(x, pan)
        i = int(t * SR)
        if i >= N or i + x.shape[1] <= 0:
            return
        a = max(0, -i)
        n = min(x.shape[1] - a, N - max(i, 0))
        self.b[:, max(i, 0):max(i, 0) + n] += x[:, a:a + n] * gain


def reverb(x, seconds=2.4, mix=.3, bright=5000):
    n = int(seconds * SR)
    out = []
    for ch in range(2):
        ir = rng.standard_normal(n) * np.exp(-np.arange(n) / (seconds * SR / 5.5))
        ir = lowpass(ir, bright)
        ir[:int(.012 * SR)] *= .2
        m = x.shape[1] + n
        y = np.fft.irfft(np.fft.rfft(x[ch], m) * np.fft.rfft(ir, m), m)[:x.shape[1]]
        out.append(y)
    wet = np.stack(out)
    wet *= np.max(np.abs(x)) / max(1e-9, np.max(np.abs(wet)))
    return x * (1 - mix * .5) + wet * mix


# ---------------------------------------------------------------- instruments

def piano(m, d=2.2, vel=1.0):
    f = hz(m)
    t = tt(d)
    y = np.zeros_like(t)
    for k, a in enumerate((1.0, .52, .3, .16, .1, .06, .03), 1):
        fk = f * k * (1 + .0006 * k * k)
        y += a * np.sin(2 * np.pi * fk * t) * np.exp(-t * (1.1 + .55 * k))
    y *= env(d, .004, .15)
    thump = lowpass(rng.standard_normal(len(t)), 900) * np.exp(-t * 60) * .25
    return (y + thump) * vel * .32


def pluck(m, d=.4, bright=3200, vel=1.0):
    f = hz(m)
    y = (saw(f, d) + .6 * saw(f * 2.005, d)) * decay(d, .09) * env(d, .002, .03)
    return lowpass(y, bright) * vel * .3


def bell(m, d=1.2, vel=1.0):
    f = hz(m)
    t = tt(d)
    y = sum(a * np.sin(2 * np.pi * f * r * t) * np.exp(-t * dk) for r, a, dk in ((1, 1, 3.5), (2.76, .5, 5), (5.4, .25, 8), (8.9, .12, 12)))
    return y * env(d, .002, .1) * vel * .18


def pad(notes, d, cutoff=1400, vel=1.0):
    y = np.zeros(int(d * SR))
    for m in notes:
        for det in (-.004, 0, .005):
            y += saw(hz(m), d, det)
    y = lowpass(y, cutoff, 2) * env(d, min(1.2, d * .35), min(1.5, d * .4), 1.5)
    return y * vel * .045


def bass(m, d, cutoff=420, vel=1.0):
    f = hz(m)
    t = tt(d)
    y = saw(f, d) * .7 + np.sin(2 * np.pi * f * t)
    return lowpass(y, cutoff) * env(d, .006, .04) * vel * .34


def kick(vel=1.0, d=.42):
    t = tt(d)
    f = 46 + 120 * np.exp(-t * 34)
    y = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 9)
    click = highpass(rng.standard_normal(len(t)), 2500) * np.exp(-t * 320) * .2
    return (y + click) * vel * .9


def clap(vel=1.0, d=.28):
    t = tt(d)
    n = highpass(lowpass(rng.standard_normal(len(t)), 5200), 900)
    e = sum(np.exp(-np.maximum(0, t - o) * 55) * (t >= o) for o in (0, .011, .023)) / 3
    return n * e * vel * .55


def hat(vel=1.0, d=.07, open_=False):
    d = .22 if open_ else d
    t = tt(d)
    return highpass(rng.standard_normal(len(t)), 7500) * np.exp(-t * (16 if open_ else 70)) * vel * .16


def snare(vel=1.0, d=.2):
    t = tt(d)
    n = highpass(rng.standard_normal(len(t)), 1400) * np.exp(-t * 26)
    tone = np.sin(2 * np.pi * 190 * t) * np.exp(-t * 30)
    return (n * .7 + tone * .5) * vel * .5


def riser(d, vel=1.0):
    t = tt(d)
    n = rng.standard_normal(len(t))
    bands = []
    for k in range(8):
        u0, u1 = k / 8, (k + 1) / 8
        seg = (t >= u0 * d) & (t < u1 * d)
        fc = 300 * (40 ** ((u0 + u1) / 2))
        bands.append(highpass(lowpass(n, fc * 1.5), fc * .6) * seg)
    y = sum(bands)
    sweep = np.sin(2 * np.pi * np.cumsum(110 * 2 ** (t / d * 3)) / SR) * .25
    return (y * .5 + sweep) * (t / d) ** 2.2 * vel * .5


def impact(vel=1.0, d=3.0):
    t = tt(d)
    f = 34 + 70 * np.exp(-t * 9)
    sub = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 1.5)
    boom = lowpass(rng.standard_normal(len(t)), 220) * np.exp(-t * 3) * .9
    crack = highpass(rng.standard_normal(len(t)), 1800) * np.exp(-t * 16) * .25
    return (sub + boom + crack) * vel * .8


def braam(m, d=3.2, vel=1.0):
    y = np.zeros(int(d * SR))
    for o in (0, 12, 19):
        for det in (-.006, 0, .007):
            y += saw(hz(m + o), d, det)
    y = np.tanh(lowpass(y, 900) * .9)
    return y * env(d, .06, 1.6, 1.6) * vel * .16


def step(vel=1.0):
    d = .09
    t = tt(d)
    return lowpass(rng.standard_normal(len(t)), 700) * np.exp(-t * 55) * vel * .5 + highpass(rng.standard_normal(len(t)), 2500) * np.exp(-t * 200) * vel * .06


def zap(vel=1.0, d=.16):
    t = tt(d)
    buzz = np.sign(np.sin(2 * np.pi * 100 * t)) * (rng.uniform(size=len(t)) > .35)
    return (highpass(buzz, 1800) * .5 + np.sin(2 * np.pi * 2400 * t) * np.exp(-t * 40) * .3) * env(d, .001, .03) * vel * .25


def murmur(d, vel=1.0):
    n = np.cumsum(rng.standard_normal(int(d * SR)))
    n = highpass(n - np.mean(n), 30)
    n = lowpass(n / np.max(np.abs(n)), 500)
    return np.stack([n, np.roll(n, 9000)]) * vel * .12


def rain(d, vel=1.0):
    n = highpass(lowpass(rng.standard_normal(int(d * SR)), 9000), 1800)
    n2 = highpass(lowpass(rng.standard_normal(int(d * SR)), 9000), 1800)
    return np.stack([n, n2]) * env(d, .4, .6) * vel * .05


# ---------------------------------------------------------------- the piece

A2, C3, E3, F2, G2, D3 = 45, 48, 52, 41, 43, 50
CHORDS = [(A2, (57, 60, 64, 69)), (F2, (53, 57, 60, 65)), (C3 - 12, (55, 60, 64, 67)), (G2, (55, 59, 62, 67))]   # Am F C G
MOTIF = [(0, 69), (1.5, 72), (2, 76), (3, 74), (4, 72), (5.5, 69), (6, 67), (7, 64)]                              # in beats, over two bars


def compose():
    dry = Bus()       # drums, bass
    wet = Bus()       # everything that lives in the hall
    fx = Bus()

    def B(n):
        return n * BEAT

    # the city, heard from far off, under the whole film
    wet.add(0, murmur(LENGTH, .8))

    # 0 - 11 s: the figure alone, and (from 8 s) footsteps on the beat
    wet.add(0, pad((45, 52, 57), 6.5, 700, .9))
    for rep, t0 in enumerate((3.0, 7.0)):
        for b, m in MOTIF:
            wet.add(t0 + B(b), piano(m - 12 if rep == 0 else m, 2.4, .8 if rep == 0 else .95), pan=-.15 + .04 * b)
    wet.add(3.0, pad((45, 52, 57, 60), 8.5, 900, .9))
    for k in range(6):
        fx.add(8.0 + k * .5 + .02, step(.55 if k % 2 else .7), pan=-.2 + k * .08)
    # 11 - 16.5 s: the sun comes up
    wet.add(11.0, pad((53, 57, 60, 65), 3.0, 1300, 1.0))
    wet.add(13.5, pad((55, 60, 64, 67, 72), 3.4, 2600, 1.15))
    for b, m in ((0, 69), (2, 72), (4, 76), (6, 79), (8, 81), (9, 84)):
        wet.add(11.0 + B(b), piano(m, 2.6, .9), pan=.1)
    fx.add(14.0, riser(2.5, .8))
    fx.add(16.5, impact(.85))

    # 16.5 - 31.5 s: the business grows: the same four chords with a beat under them
    def groove(t0, bars, level=1.0, hats=True, claps=True, lead=True, arp=True, cut=3000, start_bar=0):
        for bar in range(bars):
            root, chord = CHORDS[(start_bar + bar) % 4]
            t = t0 + bar * 2.0
            for q in range(4):
                dry.add(t + B(q), kick(level))
                if claps and q % 2 == 1:
                    dry.add(t + B(q), clap(level * .9))
                if hats:
                    dry.add(t + B(q) + .25, hat(level, open_=(q == 3)), pan=.25)
                    dry.add(t + B(q) + .125, hat(level * .5), pan=-.25)
                # the bass ducks under the kick: it plays the off-beats
                dry.add(t + B(q) + .25, bass(root, .22, 420, level))
            wet.add(t, pad(chord, 2.05, 1500, .8 * level))
            if arp:
                for s in range(8):
                    wet.add(t + s * .25, pluck(chord[(s * 3) % 4] + (12 if s % 4 == 2 else 0), .35, cut, .8 * level), pan=.5 * np.sin(s * 1.7))
        if lead:
            for rep in range(bars // 2):
                for b, m in MOTIF:
                    wet.add(t0 + rep * 4.0 + B(b), bell(m, 1.0, .9 * level), pan=-.3)
                    wet.add(t0 + rep * 4.0 + B(b), pluck(m + 12, .5, 5000, .5 * level), pan=.3)

    groove(16.5, 3, .9, lead=False, cut=2200)                   # 16.5 - 22.5: open for business, behind the counter
    groove(22.5, 3, 1.0, cut=3200, start_bar=3)                 # 22.5 - 28.5: the avenue, the block
    # 27.5 - 31.5: up over the roofs: a roll and a rise into the cities
    for k in range(24):
        dry.add(28.5 + k * .125, snare(.35 + .55 * k / 24))
    wet.add(27.5, pad((57, 60, 64, 69, 72), 4.0, 3000, 1.1))
    fx.add(28.5, riser(3.0, 1.0))
    fx.add(31.5, impact(1.0))

    # 31.5 - 37.5 s: three cities, two seconds each, each with its own voice over the same beat
    groove(31.5, 3, 1.0, lead=False, arp=False, start_bar=0)
    for i, (scale, voice) in enumerate((((69, 72, 74, 76, 79, 81), "bell"), ((69, 71, 72, 76, 77, 81), "koto"), ((69, 72, 74, 76, 79, 84), "gaya"))):
        t0 = 31.5 + i * 2.0
        order = (0, 2, 4, 5, 3, 4, 2, 1) if i != 1 else (5, 4, 2, 1, 0, 1, 2, 4)
        for s, k in enumerate(order):
            m = scale[k]
            if voice == "bell":
                wet.add(t0 + s * .25, bell(m + 12, .8, .9), pan=.3 * np.sin(s))
            elif voice == "koto":
                wet.add(t0 + s * .25, pluck(m, .7, 6500, 1.1), pan=.3 * np.sin(s))
                wet.add(t0 + s * .25 + .03, pluck(m + .12, .7, 6500, .5), pan=-.3 * np.sin(s))
            else:
                wet.add(t0 + s * .25, pluck(m - 12, .9, 2600, 1.2), pan=.2)
                wet.add(t0 + s * .25, bell(m, .5, .35), pan=-.2)
    fx.add(37.5, impact(.9))

    # 37.5 - 45.5 s: night: the ride, then the club, harder
    groove(37.5, 2, 1.05, cut=2200, lead=False, start_bar=0)
    wet.add(37.5, rain(4.2, 1.0))
    groove(41.5, 2, 1.2, cut=5200, start_bar=2)
    for bar in range(2):
        root, chord = CHORDS[(2 + bar) % 4]
        for s in range(8):
            dry.add(41.5 + bar * 2 + s * .25, bass(root + 12, .12, 1400, .5 if s % 2 else .0))
    fx.add(44.5, riser(1.0, .7))
    fx.add(45.5, impact(1.0))

    # 45.5 - 51.5 s: the roof: everything falls away but the figure
    wet.add(45.5, pad((45, 52, 57, 60, 64), 6.4, 1100, 1.2))
    for b, m in MOTIF:
        wet.add(45.5 + B(b), piano(m, 3.0, 1.0), pan=-.1)
    for b, m in ((8, 69), (9.5, 72), (10, 76), (11, 79)):
        wet.add(45.5 + B(b), piano(m, 3.0, 1.0), pan=.1)

    # 51.5 - 58.5 s: the sign: a tube catches for every letter, and when the last one holds, the name
    wet.add(51.5, pad((53, 57, 60, 65), 2.6, 1600, 1.1))
    wet.add(53.6, pad((55, 59, 62, 67, 71), 2.6, 2600, 1.2))
    f_on = 51.5 + 30 / 24
    for k in range(10):
        t = f_on + k * 7 / 24
        fx.add(t, zap(.8), pan=-.6 + k * .13)
        fx.add(t + 4 / 24, zap(.6), pan=-.6 + k * .13)
        wet.add(t, bell(69 + (0, 3, 7, 10, 12, 15, 19, 22, 24, 27)[k], 1.4, .55), pan=-.5 + k * .11)
    fx.add(f_on + 1.2, riser(10 * 7 / 24 - 1.0, .9))
    hit = f_on + 10 * 7 / 24 + .1
    fx.add(hit, impact(1.25, 3.5))
    wet.add(hit, braam(33, 3.4, 1.3))
    wet.add(hit, pad((57, 60, 64, 69, 72, 76), 3.4, 3600, 1.5))
    for q in range(4):
        dry.add(hit + q * .5, kick(.9 - q * .12))

    # 59.5 - 64 s: the next morning: the figure again, somebody else's footsteps
    wet.add(59.3, pad((45, 52, 57), 5.0, 800, .9))
    for b, m in MOTIF[:6]:
        wet.add(59.5 + B(b), piano(m - 12, 2.6, .85), pan=.15 - .04 * b)
    for k in range(5):
        fx.add(59.5 + k * .5 + .02, step(.6), pan=.3 - k * .08)
    # 64 s: the last word
    fx.add(64.0, impact(1.0, 3.0))
    wet.add(64.0, piano(57, 3.6, 1.1))
    wet.add(64.0, piano(45, 3.6, 1.0))
    wet.add(64.0, pad((45, 57, 64), 3.6, 1200, 1.0))

    # sidechain: the hall ducks a little on every kick, which is what makes it pump
    hall = reverb(wet.b, 2.6, .34)
    out = dry.b + hall + reverb(fx.b, 3.2, .4, 3500)
    # fades at the very ends, then a soft ceiling
    edge = np.ones(N)
    edge[:int(1.5 * SR)] = np.linspace(0, 1, int(1.5 * SR))
    edge[-int(1.2 * SR):] = np.linspace(1, 0, int(1.2 * SR))
    out *= edge
    out = np.tanh(out * 1.15) / np.tanh(1.15)
    out *= .92 / np.max(np.abs(out))
    return out


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "score.wav"
    y = compose()
    data = (np.clip(y.T, -1, 1) * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())
    print("wrote", path, round(len(data) / SR, 2), "s, peak", round(float(np.max(np.abs(y))), 3))


if __name__ == "__main__":
    main()
