"""Cuts the 1.8 update trailer to "Wala" by Kamikazee (Long Time Noisy, 2009), every cut on the song's beats.

usage: python3 edit18_wala.py <frames dir> <cinema dir> <ui stills dir> <song file> <out.mp4>
       python3 edit18_wala.py ... --stills <dir> <frame> [frame ...]   writes single frames as JPG to check the look
       python3 edit18_wala.py ... --need                               lists the source frames each shot needs

The song isn't in the repository: bring your own copy. Its beats are found with librosa (cached next to <out.mp4>).
At about 96 BPM the bars that matter (beat numbers from the tracker, counted from 0) are:

  b21  13.2 s  the first pre-chorus          the opening shot, SAME CITY. / NEW EVERYTHING., the music credit
  b29  18.2 s  the first chorus (a crash)    the title slams on
  b33-b57      the chorus                    the three comparisons, two bars each, the divider moving on the beat
  b57          its last bar                  the crowds
  b61-b73      a break, a hit on every beat  a shop on every hit, then before and after on every hit
  b73-b97      the second verse              the night, the phone screens, local money, UPDATE 1.8 over the snow
  b97-b105     the second pre-chorus         the new look, a cut on every beat and then every half beat
  b105 66.2 s  the second chorus (the biggest crash)  the logo, to the end at b117
"""
import os, subprocess, sys
import numpy as np
from cut18 import *

FRAMES, CINEMA, UI, SONG, OUT = sys.argv[1:6]
use(FRAMES, CINEMA)


def beat_grid(song, cache):
    if os.path.exists(cache): return np.load(cache)
    import librosa
    # ffmpeg reads whatever the song comes as (m4a, mp3, ...)
    sr = 22050
    pcm = subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', song, '-f', 'f32le', '-ac', '1', '-ar', str(sr), '-'], check=True, capture_output=True).stdout
    y = np.frombuffer(pcm, np.float32)
    _, beats = librosa.beat.beat_track(y=y, sr=sr, units='time', tightness=200)
    np.save(cache, beats)
    return beats


BEATS = beat_grid(SONG, os.path.splitext(OUT)[0] + '.beats.npy')
START, CHORUS, END = 21, 29, 117
T0 = float(BEATS[START])


def B(i):
    """Beat [i] of the song in trailer time; halves fall between beats."""
    k = int(i)
    t = BEATS[k] if i == k else BEATS[k] + (BEATS[k + 1] - BEATS[k]) * (i - k)
    return float(t) - T0


DUR = B(END)


# ---------------------------------------------------------------- the cut
def compare(i, shot, look):
    """Two bars from beat [i]: before for two beats, the divider in on the third, held, and on across on the sixth."""
    return (B(i), B(i + 8), 'compare', dict(shot=shot, f0=4, look=look, marks=(B(i + 2), B(i + 3), B(i + 5), B(i + 6))))


EDL = [
    (B(21), B(29), 'shot', dict(shot='v01_open', f0=0, look='dusk', speed=99 / ((B(29) - B(21)) * FPS))),
    (B(29), B(33), 'shot', dict(shot='b1_title', f0=0, look='night', plate='title')),
    compare(33, 'v03_market', 'interior'),
    compare(41, 'v04_cafe', 'interior'),
    compare(49, 'v05_street', 'day'),
    (B(57), B(61), 'shot', dict(shot='v12_crowd', f0=0, look='day')),
]
MONTAGE = [('v06_fashion', 'FASHION'), ('v07_jewelry', 'JEWELRY'), ('v08_bakery', 'BAKERY'), ('v09_florist', 'FLOWERS'),
           ('v10_tech', 'GADGETS'), ('v11_bar', 'NIGHTLIFE')]
for k, (s, wd) in enumerate(MONTAGE):  # the break: a shop on every hit
    EDL.append((B(61 + k), B(62 + k), 'shot', dict(shot=s, f0=14, look='night' if s == 'v11_bar' else 'interior', tag=wd)))
FLIPS = [('v03_market', 70), ('v04_cafe', 96), ('v05_street', 60)]
for k in range(6):  # and before and after on every hit
    s, f = FLIPS[k // 2]
    EDL.append((B(67 + k), B(68 + k), 'flip', dict(shot=s, f0=f, side=k % 2, look='day' if s == 'v05_street' else 'interior')))
EDL += [
    (B(73), B(77), 'shot', dict(shot='v13_night', f0=0, look='night')),
    (B(77), B(81), 'device', dict(still='v_sell', bg=('c9_manila', 10, 'day'))),
    (B(81), B(85), 'device', dict(still='v_landlord', bg=('v12_crowd', 20, 'day'))),
    (B(85), B(89), 'device', dict(still='v_slots', bg=('v01_open', 40, 'dusk'))),
    (B(89), B(93), 'shot', dict(shot='c9_manila', f0=6, look='day')),
    (B(93), B(97), 'shot', dict(shot='c14_snow', f0=12, look='snow')),
]
# the second pre-chorus: the new look, a cut on every beat, then on every half beat into the chorus
SIZZLE = [('v03_market_after', 100, 'interior'), ('v04_cafe_after', 110, 'interior'), ('v05_street_after', 120, 'day'), ('v12_crowd', 64, 'day')]
for k, (s, f, look) in enumerate(SIZZLE):
    EDL.append((B(97 + k), B(98 + k), 'shot', dict(shot=s, f0=f, look=look)))
SIZZLE2 = [('v06_fashion', 22), ('v07_jewelry', 22), ('v08_bakery', 22), ('v09_florist', 22), ('v10_tech', 22), ('v11_bar', 22),
           ('v13_night', 30), ('v01_open', 60)]
for k, (s, f) in enumerate(SIZZLE2):
    look = 'night' if s in ('v11_bar', 'v13_night') else 'dusk' if s == 'v01_open' else 'interior'
    EDL.append((B(101 + k * .5), B(101.5 + k * .5), 'shot', dict(shot=s, f0=f, look=look)))
EDL += [
    (B(105), B(END), 'shot', dict(shot='e1_finale', f0=0, look='night', plate='logo', speed=157 / ((B(END) - B(105)) * FPS))),
]


def span(i, j, pad=.12):
    return B(i) + pad, B(j) - pad


CAPTIONS = [
    (B(21) + .35, B(25) - .1, 'statement', ('SAME CITY.',)),
    (B(25) + .1, B(29) - .05, 'statement', ('NEW EVERYTHING.',)),
    (B(21) + .7, B(29) - .25, 'credit', ('MUSIC', '“Wala” by Kamikazee')),
    (B(29), B(33), 'title', ()),
    (*span(33, 41), 'headline', (['REAL PRODUCTS'], 'Fruit in crates, cans in the fridges, pizzas in the freezer')),
    (*span(41, 49), 'headline', (['REAL FURNITURE'], 'Café tables, sofas, rugs, lamps and plants, all modelled in 3D')),
    (*span(49, 57), 'headline', (['STREET FURNITURE'], 'Street lights, hydrants, benches, shelters and bike racks')),
    (*span(57, 61), 'headline', (['PEOPLE, REBUILT'], 'Faces, hairstyles and real outfits')),
    (*span(73, 77), 'headline', (['NOTHING OUT OF PLACE'], 'Every prop checked in all five cities')),
    (*span(77, 81), 'side', (['SELL', 'ANYTHING'], 'Businesses, buildings and cars.')),
    (*span(81, 85), 'side', (['BE THE', 'LANDLORD'], 'Rivals and families rent from you.')),
    (*span(85, 89), 'side', (['SIX SAVE', 'SLOTS'], 'A different city in each.')),
    (*span(89, 93), 'headline', (['LOCAL MONEY'], 'Pesos in Manila, yen in Tokyo, won in Seoul')),
    (B(93), B(97) - .05, 'big', ('UPDATE 1.8', 'Furniture  ·  Products  ·  Streets  ·  People')),
    (B(105), B(END), 'logo', ()),
]

FLASHES = [(B(29), .9, .4), (B(105), .9, .35), (B(93), .55, .35), (B(73), .3, .25)]
FLASHES += [(B(i), .3, .2) for i in (33, 41, 49, 57, 77, 81, 85, 89)]
FLASHES += [(B(61 + k), .28, .15) for k in range(6)]
FLASHES += [(B(67 + k), .3 if k % 2 else .12, .1) for k in range(6)]
FLASHES += [(B(97 + k), .22, .12) for k in range(4)]
FLASHES += [(B(101 + k * .5), .18, .08) for k in range(8)]
# a glint as each divider sweeps in
FLASHES += [(B(i + 2), .1, .25) for i in (33, 41, 49)]

CUT = Cut(DUR, EDL, CAPTIONS, FLASHES, slam=B(CHORUS), logo=B(105), ui=UI, credit='MUSIC  “WALA”  BY KAMIKAZEE')


def song_excerpt(out):
    """The song from the first pre-chorus for the length of the cut: in quick, out over the last bar."""
    fade = B(END) - B(113)
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-ss', f'{T0:.3f}', '-i', SONG, '-t', f'{DUR:.3f}', '-vn',
                    '-af', f'afade=t=in:st=0:d=0.03,afade=t=out:st={DUR - fade:.3f}:d={fade:.3f}', '-ar', '48000', '-ac', '2', out], check=True)
    return out


if __name__ == '__main__':
    if '--need' in sys.argv:
        for shot, fr in sorted(needed(CUT).items()): print(shot, min(fr), max(fr), len(fr))
        sys.exit(0)
    if '--stills' in sys.argv:
        k = sys.argv.index('--stills')
        stills(lambda n: frame_at(CUT, n), sys.argv[k + 1], sys.argv[k + 2:])
        sys.exit(0)
    print(f'{DUR:.2f} s from {T0:.2f} s into the song')
    encode(lambda n: frame_at(CUT, n), CUT.frames, song_excerpt(os.path.splitext(OUT)[0] + '.song.wav'), OUT)
