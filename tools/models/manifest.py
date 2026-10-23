"""Which source meshes make up each furniture model, and where they go.

Keys are furniture ids from data/Catalog.kt. Each part:
  src      source file under sources/
  box      (x0, y0, z0, x1, y1, z1) in the item's tile units (x across, y deep, z up in meters); z1 None = no height cap
  face     where the source's front ends up: '+y' (default, toward the customer), '-y', '+x' or '-x'
  stretch  fill the box exactly instead of keeping the source's proportions
  align    'center' (default), 'back' (against y0) or 'front' (against y1)
  repeat / step   copies of the part, each moved by step (dx, dy, dz)
  tint     {material name: (r, g, b)} recolors
  surface  {material name: shader surface} overrides ('plain', 'wood', 'fabric', 'paint', 'glass')
  glow     material names that light up (lamp shades)
Seat heights follow the seats in Catalog.seats (sofas .45, barstool .85, chairs .45).
"""
import os

SOURCES = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'sources')

F = 'kenney-furniture-kit/'
M = 'kenney-mini-market/'


def chairs_along(xs, y0, y1, face):
    return [dict(src=F + 'chairCushion.glb', box=(x - .22, y0, 0, x + .22, y1, .95), face=face) for x in xs]


MODELS = {
    # ---------------------------------------------------------------- shop fittings
    'cash_register': [dict(src=M + 'cash-register.glb', box=(0, .05, 0, 2, .95, 1.15), stretch=True)],
    'checkout_counter': [dict(src=M + 'cash-register.glb', box=(0, .05, 0, 3, .95, 1.1), stretch=True)],
    'shopping_baskets': [dict(src=M + 'shopping-basket.glb', box=(.2, .2, 0, .8, .8, None), repeat=3, step=(0, 0, .13))],
    'cart_rack': [dict(src=M + 'shopping-cart.glb', box=(.05, .05, 0, .95, .95, 1.1), repeat=3, step=(1, 0, 0))],
    'trash_can': [dict(src=F + 'trashcan.glb', box=(.25, .25, 0, .75, .75, .85))],
    # ---------------------------------------------------------------- seating
    'sofa': [dict(src=F + 'loungeSofa.glb', box=(0, 0, 0, 2, 1, None), align='back')],
    'lounge_sofa': [dict(src=F + 'loungeDesignSofa.glb', box=(0, 0, 0, 2, 1, None), align='back')],
    'barstool': [dict(src=F + 'stoolBar.glb', box=(.25, .25, 0, .75, .75, .88))],
    'bench': [dict(src=F + 'benchCushion.glb', box=(.05, .25, 0, 1.95, .75, .5), stretch=True)],
    'table_set': [dict(src=F + 'tableRound.glb', box=(.55, .55, 0, 1.45, 1.45, .74), stretch=True)]
    + [dict(src=F + 'chairCushion.glb', box=(.05, .78, 0, .51, 1.22, .95), face='+x'),
       dict(src=F + 'chairCushion.glb', box=(1.49, .78, 0, 1.95, 1.22, .95), face='-x')],
    'meeting_table': [dict(src=F + 'table.glb', box=(.3, .55, 0, 2.7, 1.45, .76), stretch=True)]
    + chairs_along((.5, 1.5, 2.5), .02, .48, '+y') + chairs_along((.5, 1.5, 2.5), 1.52, 1.98, '-y'),
    # ---------------------------------------------------------------- home
    'bed': [dict(src=F + 'bedDouble.glb', box=(0, 0, 0, 2, 2, None), align='back')],
    'home_fridge': [dict(src=F + 'kitchenFridge.glb', box=(.05, .05, 0, .95, .95, 1.9), align='back')],
    'tv': [dict(src=F + 'cabinetTelevision.glb', box=(.1, .15, 0, 1.9, .85, .55), align='back'),
           dict(src=F + 'televisionModern.glb', box=(.25, .22, .55, 1.75, .6, 1.5), align='back')],
    'rug': [dict(src=F + 'rugRectangle.glb', box=(.1, .1, 0, 1.9, 1.9, .03), stretch=True)],
    'floor_lamp': [dict(src=F + 'lampRoundFloor.glb', box=(.25, .25, 0, .75, .75, 1.85), glow=['lamp'])],
    'plant_large': [dict(src=F + 'pottedPlant.glb', box=(.1, .1, 0, .9, .9, 1.6))],
    'plant_small': [dict(src=F + 'plantSmall1.glb', box=(.25, .25, 0, .75, .75, .75))],
    # ---------------------------------------------------------------- office
    'desk': [dict(src=F + 'desk.glb', box=(.05, .1, 0, 1.95, .9, .76), stretch=True),
             dict(src=F + 'computerScreen.glb', box=(.7, .15, .76, 1.3, .45, 1.2), align='back'),
             dict(src=F + 'computerKeyboard.glb', box=(.75, .5, .76, 1.25, .7, .8))],
    'office_desk': [dict(src=F + 'desk.glb', box=(.05, .1, 0, 1.95, .9, .76), stretch=True),
                    dict(src=F + 'computerScreen.glb', box=(.7, .15, .76, 1.3, .45, 1.2), align='back'),
                    dict(src=F + 'computerKeyboard.glb', box=(.75, .5, .76, 1.25, .7, .8))],
    'filing_cabinet': [dict(src=F + 'sideTableDrawers.glb', box=(.15, .1, 0, .85, .8, 1.3), stretch=True, align='back',
                            tint={'wood': (154, 163, 173), 'woodDark': (120, 128, 138)}, surface={'wood': 'paint', 'woodDark': 'paint'})],
    # ---------------------------------------------------------------- music and gym
    'speaker_small': [dict(src=F + 'speakerSmall.glb', box=(.3, .3, 0, .7, .7, .7))],
    'speaker_medium': [dict(src=F + 'speaker.glb', box=(.25, .25, 0, .75, .75, 1.1))],
    'speaker_large': [dict(src=F + 'speaker.glb', box=(.12, .12, 0, .88, .88, 1.5), stretch=True)],
    'shower': [dict(src=F + 'shower.glb', box=(0, 0, 0, 1, 1, 2.1))],
}
