#!/usr/bin/env python3
"""Renders GPU frames dumped by GpuFrameDumpTest with headless Chromium (WebGL2 on SwiftShader).

usage: python3 tools/glview/render.py [frame-name ...]   (default: every frame in app/build/glframes)
Writes app/build/glframes/<name>/gpu.png (with the overlay layer) and gpu_raw.png (3D only).
"""
import functools, glob, http.server, json, os, socketserver, subprocess, sys, threading

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', 'app', 'build', 'glframes'))
PAGE = os.path.abspath(os.path.join(os.path.dirname(__file__), 'view.html'))
SHELL = glob.glob('/opt/pw-browsers/chromium_headless_shell-*/chrome-linux/headless_shell')[0]

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))

class Quiet(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *a): pass

server = socketserver.TCPServer(('127.0.0.1', 0), functools.partial(Quiet, directory=REPO))
PORT = server.server_address[1]
threading.Thread(target=server.serve_forever, daemon=True).start()

FAST = os.environ.get('FAST') == '1'

def render(name, overlay=True):
    d = os.path.join(ROOT, name)
    f = json.load(open(os.path.join(d, 'frame.json')))
    out = os.path.join(d, ('gpu_fast' if FAST else 'gpu') + ('.png' if overlay else '_raw.png'))
    rel = lambda p: '/' + os.path.relpath(p, REPO)
    url = f'http://127.0.0.1:{PORT}{rel(PAGE)}?f={rel(d)}&s={rel(ROOT)}' + ('' if overlay else '&nooverlay=1') + ('&fast=1' if FAST else '')
    subprocess.run([SHELL, '--no-sandbox', '--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader',
                    '--hide-scrollbars', '--virtual-time-budget=20000',
                    f'--window-size={int(f["width"])},{int(f["height"])}', f'--screenshot={out}', url],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=300)
    print(name, '->', out, f'({f["triangles"]} triangles)')

names = sys.argv[1:] or sorted(n for n in os.listdir(ROOT) if os.path.isdir(os.path.join(ROOT, n)))
for n in names:
    render(n, True)
    render(n, False)
