#!/usr/bin/env python3
"""Renders the icons defined in Kotlin icon-set files to a PNG contact sheet with headless Chromium.

usage: python3 tools/icons/preview.py OUT.png path/to/IconSet.kt [more.kt ...] [--only id1,id2]

Icon files must use the exact form
    icon("id", L("<svg path>"[, 0xAARRGGBB][, alpha = .5f][, stroke = 1.5f][, evenOdd = true]), ...)
on a 24x24 grid. Glyph icons (no explicit colors) are shown white on a colored pin, at map size and on a light card;
color icons are shown on white, on dark and small.
"""
import glob, html, os, re, subprocess, sys, tempfile

L_RE = re.compile(r'L\(\s*"([^"]*)"\s*(?:,\s*(0x[0-9A-Fa-f]{8}L?|TINT))?(?:\s*,\s*alpha\s*=\s*([0-9.]+)f?)?'
                  r'(?:\s*,\s*stroke\s*=\s*([0-9.]+)f?)?(?:\s*,\s*evenOdd\s*=\s*(true|false))?\s*\)')
ICON_RE = re.compile(r'icon\(\s*"([^"]+)"')
PIN_COLORS = ['#E8833A', '#2F80ED', '#27AE60', '#9B51E0', '#EB5757', '#00897B', '#C2185B', '#455A64']

def parse(path):
    src = open(path, encoding='utf8').read()
    marks = [(m.start(), m.group(1)) for m in ICON_RE.finditer(src)]
    icons = {}
    order = []
    for m in L_RE.finditer(src):
        owner = None
        for pos, iid in marks:
            if pos < m.start(): owner = iid
            else: break
        if owner is None: continue
        if owner not in icons: icons[owner] = []; order.append(owner)
        col = m.group(2)
        color = None if (col is None or col == 'TINT') else '#' + col[4:10] if col.lower().startswith('0xff') else '#' + col[4:10]
        a = 1.0 if col is None or col == 'TINT' else int(col[2:4], 16) / 255.0
        icons[owner].append(dict(d=m.group(1), color=color, alpha=float(m.group(3) or 1) * a,
                                 stroke=float(m.group(4) or 0), evenodd=m.group(5) == 'true'))
    # icons declared with no layers parsed are reported
    for _, iid in marks:
        if iid not in icons: print('WARNING: no layers parsed for', iid, file=sys.stderr)
    return [(i, icons[i]) for i in order]

def svg(layers, tint, size):
    parts = []
    for l in layers:
        c = l['color'] or tint
        if l['stroke'] > 0:
            parts.append(f'<path d="{html.escape(l["d"])}" fill="none" stroke="{c}" stroke-width="{l["stroke"]}" '
                         f'stroke-linecap="round" stroke-linejoin="round" opacity="{l["alpha"]}"/>')
        else:
            fr = ' fill-rule="evenodd"' if l['evenodd'] else ''
            parts.append(f'<path d="{html.escape(l["d"])}" fill="{c}"{fr} opacity="{l["alpha"]}"/>')
    return f'<svg width="{size}" height="{size}" viewBox="0 0 24 24">{"".join(parts)}</svg>'

def main():
    args = sys.argv[1:]
    only = None
    if '--only' in args:
        i = args.index('--only'); only = set(args[i + 1].split(',')); del args[i:i + 2]
    out, files = args[0], args[1:]
    items = []
    for f in files: items += parse(f)
    if only: items = [it for it in items if it[0] in only]
    cells = []
    for k, (iid, layers) in enumerate(items):
        colored = any(l['color'] for l in layers)
        if colored:
            body = (f'<div class="b" style="background:#fff">{svg(layers, "#222", 96)}</div>'
                    f'<div class="b" style="background:#2b2f36">{svg(layers, "#fff", 96)}</div>'
                    f'<div class="s" style="background:#fff">{svg(layers, "#222", 32)}</div>')
        else:
            pc = PIN_COLORS[k % len(PIN_COLORS)]
            body = (f'<div class="b pin" style="background:{pc}">{svg(layers, "#fff", 72)}</div>'
                    f'<div class="s pin" style="background:{pc};width:30px;height:30px">{svg(layers, "#fff", 20)}</div>'
                    f'<div class="b" style="background:#e9edf2">{svg(layers, "#2b2f36", 64)}</div>')
        cells.append(f'<div class="c">{body}<div class="t">{html.escape(iid)}</div></div>')
    page = ('<html><head><style>body{margin:0;padding:8px;background:#9aa3ad;font:12px sans-serif;display:flex;flex-wrap:wrap;'
            'gap:8px;width:1584px}.c{background:#cfd5db;border-radius:6px;padding:6px;display:flex;flex-wrap:wrap;gap:6px;'
            'width:370px;align-items:center}.b{width:104px;height:104px;display:flex;align-items:center;justify-content:center;'
            'border-radius:8px}.s{width:40px;height:40px;display:flex;align-items:center;justify-content:center;border-radius:6px}'
            '.pin{border-radius:50%;border:3px solid #fff;box-sizing:border-box}.t{width:100%;font-weight:bold;color:#1d2228}'
            '</style></head><body>' + ''.join(cells) + '</body></html>')
    rows = (len(cells) + 3) // 4
    height = 16 + rows * 152
    with tempfile.NamedTemporaryFile('w', suffix='.html', delete=False) as t:
        t.write(page); name = t.name
    shell = glob.glob('/opt/pw-browsers/chromium_headless_shell-*/chrome-linux/headless_shell')[0]
    subprocess.run([shell, '--no-sandbox', '--disable-gpu', '--hide-scrollbars', f'--window-size=1600,{height}',
                    f'--screenshot={os.path.abspath(out)}', 'file://' + name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=120)
    os.unlink(name)
    print(f'{len(items)} icons -> {out}')

if __name__ == '__main__':
    main()
