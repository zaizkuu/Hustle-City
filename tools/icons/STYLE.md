# Hustle City icon style

Hustle City draws its own icons instead of emojis. They follow the look of Big Ambitions' UI: flat, bold, friendly
shapes that read instantly at small sizes.

## Format

Icons live in `app/src/main/java/com/hustlecity/game/ui/icons/*Icons*.kt` as SVG path data on a **24×24 grid**:

```kotlin
icon("biz_coffee",
    L("M5 8h11v5.5a5.5 5.5 0 0 1-11 0z"),                       // fill in the tint color
    L("M8 2.5c1 1-1 2 0 3.5", stroke = 1.2f, alpha = .8f),       // a stroked line
    L("M7 9h10v6H7z", 0xFFFFFFFF, alpha = .9f),                  // an explicit color
    L("M3 3h18v18H3zM8 8h8v8H8z", evenOdd = true),               // a cut-out hole
),
```

Write `L(...)` arguments in exactly this order: path, optional color (`0xAARRGGBB` or `TINT`), then optional named
`alpha =`, `stroke =`, `evenOdd =`. One path string per layer (no string concatenation), so the preview tool can parse
it. Layers draw in order, so put backgrounds first.

Preview your icons with:

```bash
python3 tools/icons/preview.py /tmp/out.png app/src/main/java/com/hustlecity/game/ui/icons/YourIcons.kt
```

Then open the PNG with the Read tool and check every icon. The game renderer gives the same result
(`IconGalleryTest` writes `app/build/snapshots/icons_*.png`).

## Two kinds of icons

**Glyphs** (business types, places, map filters, phone apps, HUD, actions, weather): single-color silhouettes using
only `TINT` layers (no explicit colors). The game draws them white on a colored round pin (Voogle Maps), white on a
rounded-square gradient tile (BizPhone apps) or dark on light cards. Rules:
- Solid filled shapes, not thin outlines. Strokes only for small accents (steam, motion lines) and at least 1.5 wide.
- Keep about 2 units of padding: draw inside roughly x 2..22, y 2..22, centred, and fill the space (no tiny glyphs).
- Use `alpha = .55f`–`.7f` layers for a secondary tone (e.g. the liquid in a glass, a shelf behind products).
- Use `evenOdd = true` or white-space gaps to separate parts (cup and handle, bottle label) so they read at 20 px.
- One clear subject per icon, recognizable in silhouette at 20 px. No text inside icons.

**Products** (everything sold in shops: flowers, energy drinks, gifts, umbrellas, whiskey, clothing, phones…):
full-color flat illustrations with explicit colors on every layer. Rules:
- 3–6 colors per icon: a base, a darker shade (often black at `alpha = .15f`–`.25f` on the right/bottom side) and a
  highlight (white at `alpha = .3f`–`.5f`) for volume, plus accent colors.
- No outlines; shapes meet edge to edge. Slight 3/4 or front view, centred, filling about 80% of the grid.
- They must read on both white and dark backgrounds (avoid pure black or pure white silhouettes without an edge).
- Depict the actual product: a bouquet for flowers, a can for energy drinks, a wrapped box with ribbon for gifts, a
  bottle for whiskey, a T-shirt or jacket for clothing, a smartphone, a laptop, and so on. Cheap vs expensive variants
  must look different (e.g. a single tulip bunch vs a rose bouquet in paper; a plain tee vs a tailored blazer).
- Services and tickets (gym fee, haircut, legal hour, movie ticket, club entry…) get an object that stands for the
  service: dumbbell with a ticket, scissors and comb, gavel, film ticket, wristband…

## References

Big Ambitions screenshots provided by the player, in `/root/.claude/uploads/12bc0a81-bb70-5ca1-ac7a-dd544428124c/`
(open them with the Read tool): `3ca2fbad-image.jpg` (map pins and filter list icons), `5fd5903a-image.jpg` (filter
list icons), `e419c2d4-image.jpg` and `1f9508f5-image.jpg` (BizPhone app icons), `1981ee79-image.jpg` (vehicle HUD
icons: car, parking P, boxes, fuel pump, gear). Match the clarity and weight of those glyphs, but draw our own shapes.
