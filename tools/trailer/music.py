"""Procedural trailer soundtrack: a warm house groove that builds, 110 BPM, Am-F-C-G."""
import numpy as np, wave, sys

SR = 44100
BPM = 110
beat = 60 / BPM
total = float(sys.argv[2]) if len(sys.argv) > 2 else 72.0
n = int(SR * total)
L = np.zeros(n); R = np.zeros(n)
rng = np.random.default_rng(7)

def add(sig, start, pan=0.0, gain=1.0):
    i = int(start * SR)
    if i >= n: return
    sig = sig[: n - i] * gain
    L[i:i + len(sig)] += sig * (1 - max(0, pan))
    R[i:i + len(sig)] += sig * (1 + min(0, pan))

def env(length, a=0.005, d=0.2, s=0.0, r=0.05):
    t = np.arange(int(length * SR)) / SR
    e = np.ones_like(t)
    e[t < a] = t[t < a] / a
    dm = (t >= a) & (t < a + d)
    e[dm] = 1 - (1 - s) * (t[dm] - a) / d
    e[t >= a + d] = s
    rel = t > length - r
    e[rel] *= np.clip((length - t[rel]) / r, 0, 1)
    return e

def kick():
    t = np.arange(int(0.35 * SR)) / SR
    f = 50 + 120 * np.exp(-t * 30)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 9) * 0.9

def clap():
    t = np.arange(int(0.25 * SR)) / SR
    noise = rng.standard_normal(len(t))
    burst = np.exp(-t * 25) * (1 + 0.6 * (np.sin(2 * np.pi * 90 * t) > 0))
    return noise * burst * 0.25

def hat(open_=False):
    t = np.arange(int((0.18 if open_ else 0.05) * SR)) / SR
    noise = rng.standard_normal(len(t))
    noise = np.diff(np.concatenate([[0], noise]))  # crude high-pass
    return noise * np.exp(-t * (12 if open_ else 60)) * 0.12

def saw(freq, length):
    t = np.arange(int(length * SR)) / SR
    out = np.zeros_like(t)
    for k in range(1, 12):
        out += np.sin(2 * np.pi * freq * k * t) / k
    return out

def pad(freqs, length):
    t = np.arange(int(length * SR)) / SR
    out = np.zeros_like(t)
    for f in freqs:
        for det in (-0.12, 0.0, 0.12):
            out += np.sin(2 * np.pi * f * (1 + det / 100) * t + rng.uniform(0, 6))
            out += 0.35 * np.sin(2 * np.pi * 2 * f * (1 + det / 100) * t)
    return out / (len(freqs) * 3) * env(length, a=0.4, d=0.1, s=1.0, r=0.6)

def bass(freq, length):
    t = np.arange(int(length * SR)) / SR
    s = np.sin(2 * np.pi * freq * t) + 0.3 * np.sin(2 * np.pi * freq * 2 * t)
    return s * env(length, a=0.005, d=length * 0.6, s=0.4, r=0.03) * 0.55

def note(m): return 440 * 2 ** ((m - 69) / 12)

chords = [(57, [57, 60, 64]), (53, [53, 57, 60]), (48, [48, 52, 55]), (55, [55, 59, 62])]  # Am F C G
bar = 4 * beat
bars = int(total / bar) + 1
for b in range(bars):
    t0 = b * bar
    root, tri = chords[b % 4]
    intro = b < 2
    build = b >= 2
    drop = b >= 6
    outro = t0 > total - 2 * bar
    # pads all the way
    add(pad([note(m) for m in tri] + [note(tri[0] + 12)], bar + 0.6), t0, gain=0.22 if not outro else 0.28)
    if build and not outro:
        for k in range(4):
            add(kick(), t0 + k * beat, gain=0.9 if drop else 0.6)
            add(hat(), t0 + k * beat + beat / 2, pan=0.3, gain=1.0)
        if drop:
            add(clap(), t0 + beat, gain=0.9); add(clap(), t0 + 3 * beat, gain=0.9)
            for k in range(8): add(hat(), t0 + k * beat / 2 + beat / 4, pan=-0.3, gain=0.6)
            add(hat(True), t0 + 3.5 * beat, pan=0.2, gain=0.8)
        # bassline: root on the offbeats
        for k in range(4):
            add(bass(note(root - 12), beat * 0.45), t0 + k * beat + beat / 2, gain=1.0 if drop else 0.6)
    if drop and not outro:
        # a plucked arpeggio
        for k in range(8):
            m = tri[k % 3] + 12 * (1 + (k // 3) % 2)
            length = beat / 2
            s = saw(note(m), length) * env(length, a=0.003, d=0.18, s=0.0, r=0.02) * 0.06
            add(s, t0 + k * beat / 2, pan=0.4 if k % 2 else -0.4)
    if intro:
        # rising noise sweep into the beat
        if b == 1:
            t = np.arange(int(bar * SR)) / SR
            sw = rng.standard_normal(len(t)) * (t / bar) ** 2 * 0.08
            add(sw, t0)

# a final hit and fade
add(kick(), total - 2 * bar, gain=1.0)
fade = np.ones(n); fl = int(3 * SR); fade[-fl:] = np.linspace(1, 0, fl)
fi = int(0.5 * SR); fade[:fi] = np.linspace(0, 1, fi)
mix = np.stack([L, R], axis=1) * fade[:, None]
# soft clip and normalize
mix = np.tanh(mix * 1.2)
mix /= np.max(np.abs(mix)) * 1.05
pcm = (mix * 32767).astype(np.int16)
with wave.open(sys.argv[1], 'wb') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes(pcm.tobytes())
print('wrote', sys.argv[1], total, 's')
