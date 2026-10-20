"""The trailers' synthesiser: oscillators, filters, drums, brass, pads, plucks, bells, effects, the city's murmur,
and a mixer with buses, a hall reverb and the master chain. All noise comes from one seeded generator, so a score
renders the same every time.
"""
import wave
import numpy as np
from scipy import signal

SR = 48000
rng = np.random.default_rng(11)


def note(m): return 440.0 * 2 ** ((m - 69) / 12)


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


def murmur(d, busy=1.0):
    """The city's own murmur: many voices and the traffic under them."""
    t = t_axis(d)
    out = np.zeros((len(t), 2))
    for ch in range(2):
        v = filt(noise(d), 'bandpass', [280, 2600], 2)
        # many voices: the level wanders a little, syllable by syllable
        am = 1 + .35 * filt(noise(d), 'lowpass', 6, 1) * 8
        out[:, ch] = v * np.clip(am, .3, 1.8) * .08 * busy
    out += np.stack([filt(noise(d), 'lowpass', 400, 2)] * 2, 1) * .05 * busy
    edge = np.minimum(1, np.minimum(t / .25, (d - t) / .25))[:, None]
    return out * edge


def load_sfx(path):
    with wave.open(path) as w:
        ch = w.getnchannels(); sr = w.getframerate()
        d = np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32) / 32768
    if ch == 2: d = d.reshape(-1, 2).mean(1)
    return np.interp(np.arange(0, len(d), sr / SR), np.arange(len(d)), d)


# ---------------------------------------------------------------- the mixer
class Mixer:
    """Five buses and a reverb send, [dur] seconds long."""

    def __init__(self, dur):
        self.dur = dur
        self.n = int(SR * dur)
        self.buses = {k: np.zeros((self.n, 2), np.float32) for k in ('drums', 'bass', 'music', 'fx', 'sfx')}
        self.sends = np.zeros((self.n, 2), np.float32)  # into the big hall reverb

    def place(self, bus, sig, t, gain=1.0, pan=0.0, send=0.0):
        """Mixes a mono or stereo signal into a bus at time t (seconds)."""
        N = self.n
        i = int(round(t * SR))
        if i >= N or len(sig) == 0: return
        if i < 0: sig = sig[-i:]; i = 0
        sig = sig[:N - i]
        if sig.ndim == 1:
            l = np.cos((pan + 1) * np.pi / 4); r = np.sin((pan + 1) * np.pi / 4)
            st = np.stack([sig * l, sig * r], 1) * np.sqrt(2)
        else:
            st = sig
        self.buses[bus][i:i + len(st)] += st * gain
        if send: self.sends[i:i + len(st)] += st * gain * send

    def ducking(self, times, depth=.6, rel=.18):
        """Sidechain gain: dips under every kick and breathes back."""
        N = self.n
        g = np.ones(N, np.float32)
        k = np.exp(-np.arange(int(rel * 4 * SR)) / (rel * SR)).astype(np.float32)
        for t in times:
            i = int(t * SR)
            if i >= N: continue
            m = min(len(k), N - i)
            g[i:i + m] = np.minimum(g[i:i + m], 1 - depth * k[:m])
        return g

    def hall(self, x, d=3.2):
        N = self.n
        ir_t = t_axis(d)
        irl = noise(d) * np.exp(-ir_t * 2.1); irr = noise(d) * np.exp(-ir_t * 2.1)
        irl = filt(irl, 'lowpass', 5200); irr = filt(irr, 'lowpass', 5200)
        pre = int(.02 * SR)
        irl = np.concatenate([np.zeros(pre), irl]); irr = np.concatenate([np.zeros(pre), irr])
        out = np.stack([signal.fftconvolve(x[:, 0], irl)[:N], signal.fftconvolve(x[:, 1], irr)[:N]], 1)
        return out / np.max(np.abs(irl)) * .02

    def finish(self, path, kicks, breaths=(), fade=1.2):
        """Ducks the bass and music under the kicks, cuts everything dead for the [breaths] (start, end) but the hall,
        then sums, EQs, compresses, soft-clips, fades out and writes a 16-bit stereo WAV."""
        N, DUR, buses = self.n, self.dur, self.buses
        side = self.ducking(kicks, .55, .16)[:, None]
        buses['bass'] *= side
        buses['music'] *= (1 - (1 - side) * .5)
        tt = np.arange(N) / SR
        for a, b in breaths:
            gate = np.clip(np.maximum((a - tt) / .02, (tt - b) / .015), 0, 1).astype(np.float32)[:, None]
            for k in ('drums', 'bass', 'music', 'sfx'): buses[k] *= gate
        mix = (buses['drums'] * .9 + buses['bass'] * .5 + buses['music'] * 1.3 + buses['fx'] * .8 + buses['sfx'] * .9
               + self.hall(self.sends) * 1.0)
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
        mix *= np.clip((DUR - tt) / fade, 0, 1)[:, None] ** 1.5
        mix /= np.max(np.abs(mix)) / .93
        pcm = (mix * 32767).astype(np.int16)
        with wave.open(path, 'wb') as w:
            w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
        print(path, f'{DUR:.1f}s')
