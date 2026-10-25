// 블로그 그림 캡처: HTML 파일이나 URL을 headless Chrome으로 열어 요소 하나를 PNG로 저장한다.
// 사용: node capture.mjs <html 파일|URL> <out.png> [CSS 선택자=body] [배율=2]
// Chrome 경로는 CHROME 환경 변수로 바꾼다. 의존성 없이 Node 22+ 내장 WebSocket과 DevTools 프로토콜을 쓴다.
import { spawn } from 'node:child_process';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const [src, out, selector = 'body', scale = '2'] = process.argv.slice(2);
if (!src || !out) {
  console.error('사용: node capture.mjs <html 파일|URL> <out.png> [CSS 선택자=body] [배율=2]');
  process.exit(2);
}
const url = /^(https?|file):/.test(src) ? src : pathToFileURL(resolve(src)).href;
const chrome = process.env.CHROME ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const profile = mkdtempSync(join(tmpdir(), 'blog-capture-'));
const proc = spawn(chrome, ['--headless=new', '--remote-debugging-port=0', `--user-data-dir=${profile}`,
  '--hide-scrollbars', '--allow-file-access-from-files', 'about:blank']);

try {
  const port = await new Promise((ok, fail) => {
    let log = '';
    proc.stderr.on('data', (chunk) => {
      log += chunk;
      const m = log.match(/DevTools listening on ws:\/\/[^:]+:(\d+)\//);
      if (m) ok(m[1]);
    });
    proc.on('error', fail);
    proc.on('exit', () => fail(new Error(`Chrome이 바로 종료됐다: ${log}`)));
  });
  const pages = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
  const ws = new WebSocket(pages.find((p) => p.type === 'page').webSocketDebuggerUrl);
  await new Promise((ok) => ws.addEventListener('open', ok, { once: true }));

  let id = 0;
  const waiting = new Map();
  let onLoad;
  ws.addEventListener('message', ({ data }) => {
    const msg = JSON.parse(data);
    if (msg.method === 'Page.loadEventFired') onLoad?.();
    const w = waiting.get(msg.id);
    if (!w) return;
    waiting.delete(msg.id);
    msg.error ? w.fail(new Error(msg.error.message)) : w.ok(msg.result);
  });
  const send = (method, params = {}) => new Promise((ok, fail) => {
    waiting.set(++id, { ok, fail });
    ws.send(JSON.stringify({ id, method, params }));
  });

  await send('Page.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: 1400, height: 1000, deviceScaleFactor: Number(scale), mobile: false });
  const loaded = new Promise((ok) => { onLoad = ok; });
  await send('Page.navigate', { url });
  await loaded;
  // ponytail: 차트 애니메이션(Gatling Highcharts)이 끝나길 고정 1.5초 기다린다. 덜 그려진 채 찍히면 늘린다.
  await new Promise((ok) => setTimeout(ok, 1500));

  // Gatling 리포트처럼 안쪽 영역만 스크롤하는 페이지는 화면 밖 요소가 빈칸으로 찍히므로 먼저 화면 안으로 스크롤한다.
  const measure = async () => (await send('Runtime.evaluate', {
    returnByValue: true,
    expression: `(() => {
      const el = document.querySelector(${JSON.stringify(selector)});
      if (!el) return null;
      el.scrollIntoView({ block: 'start' });
      const r = el.getBoundingClientRect();
      return { x: r.x + scrollX, y: r.y + scrollY, width: r.width, height: r.height, bottom: r.bottom };
    })()`,
  })).result.value;
  let { bottom, ...rect } = (await measure()) ?? {};
  if (!rect.width) throw new Error(`선택자에 맞는 요소가 없다: ${selector}`);
  // captureBeyondViewport는 화면 크기를 바꿔 차트를 다시 그리게 하므로(빈칸) 대신 화면을 요소가 들어가게 키운다.
  if (bottom > 1000) {
    await send('Emulation.setDeviceMetricsOverride', { width: 1400, height: Math.ceil(bottom), deviceScaleFactor: Number(scale), mobile: false });
    await new Promise((ok) => setTimeout(ok, 1500));
    ({ bottom, ...rect } = await measure());
  }
  const shot = await send('Page.captureScreenshot', { format: 'png', clip: { ...rect, scale: 1 } });
  writeFileSync(out, Buffer.from(shot.data, 'base64'));
  const { width, height } = rect;
  console.log(`${out}: ${Math.round(width)}x${Math.round(height)} CSS px, 배율 ${scale}`);
  ws.close();
} finally {
  if (proc.exitCode === null) {
    const exited = new Promise((ok) => proc.once('exit', ok));
    proc.kill();
    await exited;
  }
  rmSync(profile, { recursive: true, force: true, maxRetries: 3 });
}
