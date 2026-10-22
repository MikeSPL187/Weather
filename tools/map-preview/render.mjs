// Renders the map style with forecast overlays in headless Chromium:
//   MAP_PREVIEW=1 ./gradlew :core:test --tests '*MapPreviewExport'
//   node tools/map-preview/render.mjs [outDir] [query...]   e.g. "city=msk&layer=precipitation&zoom=7"
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { chromium } from 'playwright-core';

const root = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../..');
const outDir = path.resolve(process.argv[2] || path.join(root, 'build/map-preview/png'));
const queries = process.argv.slice(3).length ? process.argv.slice(3) : ['city=msk&layer=precipitation'];
fs.mkdirSync(outDir, { recursive: true });
const types = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json', '.css': 'text/css', '.png': 'image/png' };
const server = http.createServer((req, res) => {
  const file = path.join(root, decodeURIComponent(new URL(req.url, 'http://x').pathname));
  if (!file.startsWith(root) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'Content-Type': types[path.extname(file)] || 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
}).listen(0);
const port = server.address().port;
const browser = await chromium.launch({
  executablePath: process.env.CHROMIUM || '/opt/pw-browsers/chromium',
  args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'],
});
const page = await browser.newPage({ viewport: { width: 412, height: 700 }, deviceScaleFactor: 2.6 });
page.on('console', (m) => { if (m.type() === 'error') console.error(m.text()); });
for (const q of queries) {
  await page.goto(`http://localhost:${port}/tools/map-preview/index.html?${q}`);
  try {
    await page.waitForFunction(() => document.title === 'ready', null, { timeout: 90000 });
  } catch (e) { console.error('timeout', q); }
  const name = q.replace(/[^a-z0-9]+/gi, '_');
  await page.screenshot({ path: path.join(outDir, `${name}.png`) });
  console.log('rendered', name);
}
await browser.close();
server.close();
