// Renders every preview case to PNG with headless Chromium:
//   SCENE_PREVIEW=1 ./gradlew :core:test --tests '*ScenePreviewExport'
//   node tools/scene-preview/render.mjs [outDir] [case...]
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { chromium } from 'playwright-core';

const root = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../..');
const outDir = path.resolve(process.argv[2] || path.join(root, 'build/scene-preview/png'));
const only = process.argv.slice(3);
fs.mkdirSync(outDir, { recursive: true });
const types = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json' };
const server = http.createServer((req, res) => {
  const file = path.join(root, decodeURIComponent(new URL(req.url, 'http://x').pathname));
  if (!file.startsWith(root) || !fs.existsSync(file)) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'Content-Type': types[path.extname(file)] || 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
}).listen(0);
const port = server.address().port;
const browser = await chromium.launch({
  executablePath: process.env.CHROMIUM || '/opt/pw-browsers/chromium',
  args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'],
});
const page = await browser.newPage({ viewport: { width: 390, height: 330 }, deviceScaleFactor: 2.77 });
page.on('console', (m) => { if (m.type() === 'error') console.error(m.text()); });
const cases = JSON.parse(fs.readFileSync(path.join(root, 'build/scene-preview/cases.json'), 'utf8'));
for (const name of Object.keys(cases)) {
  if (only.length && !only.includes(name)) continue;
  await page.goto(`http://localhost:${port}/tools/scene-preview/index.html?case=${name}`);
  await page.waitForFunction(() => document.title === 'ready' || document.title === 'error', null, { timeout: 120000 });
  if ((await page.title()) === 'error') { console.error(name, await page.textContent('#err')); continue; }
  await page.locator('#stage').screenshot({ path: path.join(outDir, `${name}.png`) });
  console.log('rendered', name);
}
await browser.close();
server.close();
