// Renders CinemaWriter shots with the game's own shaders in headless Chromium (WebGL2 on SwiftShader).
// usage: node tools/cinema/render.mjs <cinema dir> <out dir> [shot ...]
// Writes <out dir>/<shot>/NNNN.png for every frame not rendered yet; CINEMA_FRAMES=0,28 limits it to those frames.
import { createRequire } from 'node:module';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const [root, out, ...only] = process.argv.slice(2);
const ROOT = path.resolve(root), OUT = path.resolve(out);
const PAGE = path.resolve(path.dirname(new URL(import.meta.url).pathname), 'cinema.html');

const server = http.createServer((req, res) => {
  const u = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  const file = u === '/cinema.html' ? PAGE : path.join(ROOT, u);
  fs.readFile(file, (err, data) => {
    if (err) { res.writeHead(404); res.end(); return; }
    res.writeHead(200, { 'Content-Type': file.endsWith('.html') ? 'text/html' : 'application/octet-stream' });
    res.end(data);
  });
});
await new Promise(r => server.listen(0, '127.0.0.1', r));
const port = server.address().port;

const shots = (only.length ? only : fs.readdirSync(ROOT)).filter(s => fs.existsSync(path.join(ROOT, s, '0000')));
const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
for (const shot of shots) {
  const pick = process.env.CINEMA_FRAMES?.split(',').map(Number);
  const frames = fs.readdirSync(path.join(ROOT, shot)).filter(n => /^\d{4}$/.test(n) && (!pick || pick.includes(+n))).sort();
  const first = JSON.parse(fs.readFileSync(path.join(ROOT, shot, frames[0], 'frame.json')));
  const w = Math.round(first.width), h = Math.round(first.height);
  fs.mkdirSync(path.join(OUT, shot), { recursive: true });
  const todo = frames.filter(n => !fs.existsSync(path.join(OUT, shot, n + '.png')));
  if (!todo.length) { console.log(shot, 'done already'); continue; }
  const page = await browser.newPage({ viewport: { width: w, height: h } });
  page.on('console', m => { if (m.type() === 'error') console.log('page:', m.text()); });
  await page.goto(`http://127.0.0.1:${port}/cinema.html`);
  const o = JSON.parse(process.env.CINEMA_OPTS || '{}');
  await page.evaluate(([w, h, o]) => window.init('', w, h, o), [w, h, o]);
  const t0 = Date.now();
  for (const n of frames) {
    // frames are rendered in order so the cached city uploads once per version
    const target = path.join(OUT, shot, n + '.png');
    if (fs.existsSync(target) && !todo.includes(n)) continue;
    const r = await page.evaluate(([b, s]) => window.renderFrame(b, s), [`/${shot}/${n}`, `/${shot}`]);
    if (r !== 'ok') console.log(shot, n, r);
    await page.locator('#gl').screenshot({ path: target });
  }
  console.log(shot, frames.length, 'frames', ((Date.now() - t0) / 1000 / todo.length).toFixed(2), 's/frame', JSON.stringify(o));
  await page.close();
}
await browser.close();
server.close();
