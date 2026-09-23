#!/usr/bin/env node
/**
 * DevTerminal 演示录制器（v0.8.0）
 * ------------------------------------------------------------
 * 本地静态服务器 + 系统 Chromium（puppeteer-core）+ CDP 实时录屏：
 *   - 真实驱动 mockup.html（离线 Pyodide 运行 numpy/pandas/matplotlib）
 *   - 全程 CDP 截屏录制为 demo.mp4（真实录屏，非幻灯片）
 *   - 每步关键截图存入 test_results/screenshots 与 ui_shots（供宣传片剪辑）
 *   - 输出结构化测试报告 report.json
 */
import http from 'http';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import puppeteer from 'puppeteer-core';
import { execSync } from 'child_process';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..', 'design');
const OUT = path.resolve(__dirname, '..', 'test_results');
const SHOTS = path.join(OUT, 'screenshots');
const UISHOTS = path.join(OUT, 'ui_shots');
const FRAMES = path.join(OUT, 'frames');
const VIDEO = path.join(OUT, 'demo.mp4');
for (const d of [SHOTS, UISHOTS, FRAMES]) fs.mkdirSync(d, { recursive: true });

const MIME = {
  '.html':'text/html', '.js':'text/javascript', '.mjs':'text/javascript', '.css':'text/css',
  '.json':'application/json', '.wasm':'application/wasm', '.whl':'application/octet-stream',
  '.zip':'application/octet-stream', '.png':'image/png', '.svg':'image/svg+xml',
  '.ttf':'font/ttf', '.py':'text/plain', '.txt':'text/plain'
};

const PORT = 8231;
const server = http.createServer((req, res) => {
  let p = decodeURIComponent(req.url.split('?')[0]);
  if (p === '/') p = '/mockup.html';
  const fp = path.join(ROOT, p);
  if (!fp.startsWith(ROOT)) { res.writeHead(403); res.end(); return; }
  fs.stat(fp, (err, st) => {
    if (err || !st.isFile()) { res.writeHead(404); res.end('not found'); return; }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(fp).toLowerCase()] || 'application/octet-stream' });
    fs.createReadStream(fp).pipe(res);
  });
});
await new Promise(r => server.listen(PORT, '127.0.0.1', r));

const URL = `http://127.0.0.1:${PORT}/mockup.html`;
const browser = await puppeteer.launch({
  executablePath: '/usr/bin/chromium',
  headless: true,
  args: ['--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage', '--use-gl=swiftshader', '--enable-unsafe-swiftshader']
});
const page = await browser.newPage();
await page.setViewport({ width: 430, height: 880, deviceScaleFactor: 2 });

// ---------- CDP 实时录屏 ----------
const client = await page.target().createCDPSession();
await client.send('Page.startScreencast', { format: 'jpeg', quality: 72, everyNthFrame: 6 });
let frameN = 0;
client.on('Page.screencastFrame', async (e) => {
  fs.writeFileSync(path.join(FRAMES, `f${String(frameN++).padStart(5, '0')}.jpg`), Buffer.from(e.data, 'base64'));
  await client.send('Page.screencastFrameAck', { sessionId: e.sessionId });
});

const waitOutput = (needle, timeout) => page.waitForFunction(
  (n) => document.getElementById('outBody').innerText.includes(n),
  { timeout, polling: 300 }, needle);
const snap = (name) => page.screenshot({ path: path.join(name.startsWith('ui_') ? UISHOTS : SHOTS, name) });
const clickId = async (id) => { await page.evaluate((i) => document.getElementById(i).click(), id); await sleep(650); };
const openLab = async (key) => {
  await page.evaluate(() => document.getElementById('btnLab').click());
  await sleep(350);
  await page.evaluate((k) => document.querySelector(`#labList .lab-item[data-key='${k}']`).click(), key);
};
const t0 = Date.now();
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const log = (...a) => console.log(`[${(Date.now()-t0)/1000}s]`, ...a);

const results = [];
try {
  await page.goto(URL, { waitUntil: 'load', timeout: 60000 });
  log('页面加载，离线解释器预热中…');
  await sleep(1500);

  // 首页
  await snap('00_home.png');
  await snap('ui_00_home.png');

  // 题库面板
  await clickId('btnLab');
  await snap('01_lab_panel.png');
  await page.evaluate(() => document.getElementById('labSheet').classList.remove('open'));
  await sleep(400);

  // 题库顺序（保持历史索引，便于宣传片对齐）
  const ORDER = [
    ['lab_sieve','成功',['1..1000 内的素数共 168 个','前 20 个:']],
    ['lab_fib','失败',['RecursionError']],
    ['lab_syntax','失败',['SyntaxError']],
    ['lab_zerodiv','失败',['ZeroDivisionError','10 / 4 = 2']],
    ['lab_key','失败',['KeyError']],
    ['lab_multi','失败',['hello, DevTerminal','ZeroDivisionError']],
    ['lab_long','成功',['-- 共输出 5000 行 --']],
    ['lab_fileio','成功',['读到: DevTerminal 离线文件 IO 测试']],
    ['lab_unicode','成功',['你好，世界 🌍','∑ ∫ √ π','🚀🔥✅']],
    ['lab_quiz','成功',['你好，小明！代码已在离线环境运行。','再过 10 年你就 28 岁了']],
    ['lab_loop','失败',[]],                 // 死循环：必失败且不卡死
    // v0.8.0 数据科学
    ['lab_pandas','成功',['各城市销量统计']],
    ['lab_matplotlib','成功',[]],            // 由 plot 图判定
  ];
  const NAMES = {
    lab_sieve:'02_lab_sieve.png', lab_fib:'03_lab_fib.png', lab_syntax:'04_lab_syntax.png',
    lab_zerodiv:'05_lab_zerodiv.png', lab_key:'06_lab_key.png', lab_multi:'07_lab_multi.png',
    lab_long:'08_lab_long.png', lab_fileio:'09_lab_fileio.png', lab_unicode:'10_lab_unicode.png',
    lab_quiz:'11_lab_quiz.png', lab_loop:'12_lab_loop.png', lab_pandas:'13_lab_pandas.png',
    lab_matplotlib:'14_lab_matplotlib.png'
  };

  for (const [key, expBadge, must] of ORDER) {
    const rec = { key, expected_badge: expBadge, ok: false };
    const tRun = Date.now();
    try {
      await openLab(key);
      await waitOutput('devterminal run ', 40000);
      const endMarker = key === 'lab_loop' ? '运行超时' : '退出码';
      if (key === 'lab_matplotlib') {
        await page.waitForSelector('.plot-line img', { timeout: 150000 }).catch(()=>{});
        await waitOutput(endMarker, 150000);
      } else {
        await waitOutput(endMarker, 150000);
      }
      rec.run_seconds = +((Date.now() - tRun)/1000).toFixed(1);
      const badge = await page.evaluate(() => document.getElementById('badgeText').textContent);
      const out = await page.evaluate(() => document.getElementById('outBody').innerText);
      const plot = await page.evaluate(() => document.querySelectorAll('.plot-line img').length);
      rec.badge = badge; rec.has_plot = plot > 0; rec.output_len = out.length;
      let ok = badge === expBadge;
      for (const s of must) if (!out.includes(s)) { ok = false; rec.note = (rec.note||'') + ` 缺:${JSON.stringify(s)}`; }
      if (key === 'lab_matplotlib' && plot < 1) ok = false;
      rec.ok = ok;
      await snap(NAMES[key]);
      log(`${key}: badge=${badge} plot=${plot} -> ${ok ? 'PASS' : 'FAIL'}${rec.note||''}`);
    } catch (e) {
      rec.error = String(e).slice(0, 120);
      try { await snap(NAMES[key]); } catch (_) {}
      log(`${key}: EXCEPTION ${rec.error}`);
    }
    results.push(rec);
    await sleep(500);
  }

  // ---------- UI 覆盖面 ----------
  await clickId('btnMenu'); await snap('ui_01_drawer.png');
  await page.evaluate(() => document.querySelector('.row[data-file="main.py"]').click());
  await sleep(700); await snap('ui_02_mainpy.png');
  await clickId('btnRun'); await sleep(2600); await snap('ui_03_guess_running.png');
  await sleep(1600); await snap('ui_04_guess_done.png');
  await page.evaluate(() => document.getElementById('btnMenu').click()); await sleep(400);
  await page.evaluate(() => document.querySelector('.row[data-file="utils.py"]').click());
  await sleep(700); await snap('ui_05_utilspy.png');

  // 报错行跳转
  await page.evaluate(() => document.getElementById('btnLab').click()); await sleep(300);
  await page.evaluate(() => document.querySelector("#labList .lab-item[data-key='lab_fib']").click());
  await waitOutput('退出码', 60000); await sleep(600); await snap('ui_06_fib_error.png');
  const jumped = await page.evaluate(() => { const j=document.querySelector('#outBody .l.jump'); if(j){j.click(); return true;} return false; });
  await sleep(900); await snap('ui_07_error_jump.png');
  results.push({ key:'ui_error_jump', clicked: jumped });

  await page.evaluate(() => document.getElementById('labSheet').classList.remove('open'));
  await clickId('btnGit'); await snap('ui_08_git.png');
  await page.evaluate(() => document.getElementById('gitSheet').classList.remove('open'));
  await clickId('btnAi'); await snap('ui_09_ai.png');
  await page.evaluate(() => document.getElementById('aiSheet').classList.remove('open'));
  await clickId('btnCmd'); await snap('ui_10_cmdk.png');
  await page.evaluate(() => document.getElementById('cmdk').classList.remove('open'));
  await clickId('btnGsearch'); await snap('ui_11_search.png');
  await page.evaluate(() => document.getElementById('gsSheet').classList.remove('open'));

  // 拉回一个成功题
  await page.evaluate(() => document.getElementById('btnLab').click()); await sleep(300);
  await page.evaluate(() => document.querySelector("#labList .lab-item[data-key='lab_sieve']").click());
  await waitOutput('退出码', 60000); await sleep(700); await snap('ui_12_sieve_ok.png');
  await page.evaluate(() => document.getElementById('labSheet').classList.remove('open'));

  // ---------- v0.8.0：主题切换 + Markdown 预览 ----------
  await page.evaluate(() => document.querySelector('#themeChips .tchip[data-theme="monokai"]').click());
  await sleep(700); await snap('ui_13_theme.png');
  await page.evaluate(() => document.querySelector('#themeChips .tchip[data-theme="solarized-light"]').click());
  await sleep(500);
  await page.evaluate(() => document.getElementById('btnPreview').click());
  await sleep(900); await snap('ui_14_markdown.png');

  await snap('15_final.png');
} catch (e) {
  log('FATAL', String(e).slice(0, 200));
} finally {
  await client.send('Page.stopScreencast').catch(()=>{});
  await browser.close();
  server.close();
}

const passed = results.filter(r => r.ok).length;
const report = { url: URL, seconds: +((Date.now()-t0)/1000).toFixed(1), passed, total: results.length, results };
fs.writeFileSync(path.join(OUT, 'report.json'), JSON.stringify(report, null, 2));
console.log(`\n===== 演示录制完成：${passed}/${results.length} 通过，耗时 ${report.seconds}s，帧数 ${frameN} =====`);

// ---------- 帧 → 视频 ----------
if (frameN > 10) {
  const fps = 10;
  const cmd = `ffmpeg -y -framerate ${fps} -i ${FRAMES}/f%05d.jpg -c:v libopenh264 -pix_fmt yuv420p -b:v 4500k ${VIDEO}`;
  console.log('合成录屏视频…');
  execSync(cmd, { stdio: 'inherit' });
  console.log('录屏视频：', VIDEO);
}
