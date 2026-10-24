"""Cuts the 1.8 update trailer to its own score (score18.py): what changed, shown side by side with how it looked before.

usage: python3 edit18.py <frames dir> <cinema dir> <ui stills dir> <score.wav> <out.mp4>
       python3 edit18.py ... --stills <dir> <frame> [frame ...]   writes single frames as JPG to check the look
       python3 edit18.py ... --need                               lists the source frames each shot needs

<frames dir>/<shot>/NNNN.png come from render.mjs; a comparison shot <name> has <name>_before and <name>_after (the
same moment drawn without and with the 3D models, see CinematicTest.Pair2). <cinema dir>/c14_snow/NNNN/overlay.png are
the snowflakes, and <ui stills dir> holds the phone screens from CinematicUiTest (v_sell, v_landlord, v_slots).
"""
import sys
from cut18 import *

DUR = 53.0
FRAMES, CINEMA, UI, SCORE, OUT = sys.argv[1:6]
use(FRAMES, CINEMA)


# ---------------------------------------------------------------- the cut
# (start, end, kind, params); 120 BPM, every cut on a beat
EDL = [
    (0.0, 3.5, 'shot', dict(shot='v01_open', f0=8, look='dusk')),
    (3.5, 4.0, 'black', {}),
    (4.0, 6.0, 'shot', dict(shot='b1_title', f0=14, look='night', plate='title')),
    # the divider sweeps in a beat and a half into each comparison, and on across three beats later
    (6.0, 12.0, 'compare', dict(shot='v03_market', f0=4, look='interior', marks=(7.5, 8.1, 9.9, 10.5))),
    (12.0, 18.0, 'compare', dict(shot='v04_cafe', f0=4, look='interior', marks=(13.5, 14.1, 15.9, 16.5))),
    (18.0, 24.0, 'compare', dict(shot='v05_street', f0=4, look='day', marks=(19.5, 20.1, 21.9, 22.5))),
]
MONTAGE = [  # (shot, f0, word): half a second each from 24.0
    ('v06_fashion', 14, 'FASHION'), ('v07_jewelry', 14, 'JEWELRY'), ('v08_bakery', 14, 'BAKERY'), ('v09_florist', 14, 'FLOWERS'),
    ('v10_tech', 14, 'GADGETS'), ('v11_bar', 14, 'NIGHTLIFE'),
]
for i, (s, f0, wd) in enumerate(MONTAGE):
    EDL.append((24.0 + i * .5, 24.5 + i * .5, 'shot', dict(shot=s, f0=f0, look='interior' if s != 'v11_bar' else 'night', tag=wd)))
EDL += [
    (27.0, 31.0, 'shot', dict(shot='v12_crowd', f0=0, look='day')),
    (31.0, 33.0, 'shot', dict(shot='v13_night', f0=6, look='night')),
    (33.0, 35.0, 'device', dict(still='v_sell', bg=('c9_manila', 10, 'day'))),
    (35.0, 37.0, 'device', dict(still='v_landlord', bg=('b1_title', 20, 'night'))),
    (37.0, 39.0, 'device', dict(still='v_slots', bg=('v01_open', 40, 'dusk'))),
    (39.0, 41.0, 'shot', dict(shot='c9_manila', f0=6, look='day')),
    (41.0, 43.0, 'shot', dict(shot='c14_snow', f0=20, look='snow')),
]
# quick flips between before and after, a quarter second each
FLIPS = [('v03_market', 70, 0), ('v03_market', 70, 1), ('v04_cafe', 96, 0), ('v04_cafe', 96, 1), ('v05_street', 60, 0), ('v05_street', 60, 1),
         ('v04_cafe', 130, 0), ('v04_cafe', 130, 1)]
for i, (s, f, side) in enumerate(FLIPS):
    EDL.append((43.0 + i * .25, 43.25 + i * .25, 'flip', dict(shot=s, f0=f, side=side, look='day' if s == 'v05_street' else 'interior')))
EDL += [
    (45.0, 46.0, 'black', {}),
    (46.0, 53.0, 'shot', dict(shot='e1_finale', f0=0, look='night', plate='logo')),
]

CAPTIONS = [
    (0.45, 1.75, 'statement', ('SAME CITY.',)),
    (1.95, 3.45, 'statement', ('NEW EVERYTHING.',)),
    (4.0, 6.0, 'title', ()),
    (6.15, 11.85, 'headline', (['REAL PRODUCTS'], 'Fruit in crates, cans in the fridges, pizzas in the freezer')),
    (12.15, 17.85, 'headline', (['REAL FURNITURE'], 'Café tables, sofas, rugs, lamps and plants, all modelled in 3D')),
    (18.15, 23.85, 'headline', (['STREET FURNITURE'], 'Street lights, hydrants, benches, shelters and bike racks')),
    (27.1, 30.9, 'headline', (['PEOPLE, REBUILT'], 'Faces, hairstyles and real outfits, stepping round every bench')),
    (31.1, 32.9, 'headline', (['NOTHING OUT OF PLACE'], 'Every prop checked in all five cities')),
    (33.1, 34.9, 'side', (['SELL', 'ANYTHING'], 'Businesses, buildings and cars.')),
    (35.1, 36.9, 'side', (['BE THE', 'LANDLORD'], 'Rivals and families rent from you.')),
    (37.1, 38.9, 'side', (['SIX SAVE', 'SLOTS'], 'A different city in each.')),
    (39.1, 40.9, 'headline', (['LOCAL MONEY'], 'Pesos in Manila, yen in Tokyo, won in Seoul')),
    (41.0, 42.95, 'big', ('UPDATE 1.8', 'Furniture  ·  Products  ·  Streets  ·  People')),
    (46.0, 53.0, 'logo', ()),
]

FLASHES = [(4.0, .9, .4), (6.0, .35, .25), (12.0, .3, .2), (18.0, .3, .2), (27.0, .35, .25), (31.0, .25, .2), (33.0, .3, .2),
           (39.0, .25, .2), (41.0, .55, .35), (46.0, .9, .3)]
FLASHES += [(24.0 + i * .5, .3, .15) for i in range(6)]
FLASHES += [(43.0 + i * .25, .28 if i % 2 else .12, .1) for i in range(8)]
# a glint each time the divider sweeps in
FLASHES += [(t0 + 1.5, .1, .25) for t0 in (6.0, 12.0, 18.0)]


CUT = Cut(DUR, EDL, CAPTIONS, FLASHES, slam=4.0, logo=46.0, ui=UI, bleach=(3.2, 3.5))


if __name__ == '__main__':
    if '--need' in sys.argv:
        for shot, fr in sorted(needed(CUT).items()): print(shot, min(fr), max(fr), len(fr))
        sys.exit(0)
    if '--stills' in sys.argv:
        k = sys.argv.index('--stills')
        stills(lambda n: frame_at(CUT, n), sys.argv[k + 1], sys.argv[k + 2:])
        sys.exit(0)
    encode(lambda n: frame_at(CUT, n), CUT.frames, SCORE, OUT)
