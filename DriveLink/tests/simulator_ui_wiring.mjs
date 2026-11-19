// DriveLink simulator UI wiring test.
//
// Loads the real DriveLink/simulator/web/index.html, executes its inline script inside a
// minimal DOM/WebSocket stub, and asserts that the HMI reacts to receiver state messages,
// that reverse-touch gestures reach the socket, and that the connection manager can stop
// and re-arm the socket. This runs anywhere (no browser, no Go compiler, no device).
import fs from 'node:fs';
import vm from 'node:vm';
const html = fs.readFileSync(new URL('../simulator/web/index.html', import.meta.url), 'utf8');
const js = html.match(/<script>([\s\S]*)<\/script>/)[1];

const nodes = new Map();
function makeEl(id) {
  const cls = new Set();
  const e = {
    id, style: {}, dataset: {}, textContent: '', innerHTML: '', disabled: false,
    width: 0, height: 0, listeners: {},
    classList: {
      add: (c) => cls.add(c), remove: (c) => cls.delete(c),
      toggle: (c, on) => { if (on === undefined) { cls.has(c) ? cls.delete(c) : cls.add(c); } else if (on) cls.add(c); else cls.delete(c); },
      contains: (c) => cls.has(c),
    },
    addEventListener: (type, fn) => { (e.listeners[type] = e.listeners[type] || []).push(fn); },
    getBoundingClientRect: () => ({ width: 200, height: 100, left: 0, top: 0 }),
    getContext: () => new Proxy({}, { get: () => () => ({ addColorStop() {} }) }),
    fire: (type, ev = {}) => (e.listeners[type] || []).forEach((f) => f(ev)),
  };
  nodes.set(id, e);
  return e;
}
const documentStub = {
  documentElement: { dir: 'rtl' },
  body: { className: '' },
  getElementById: (id) => nodes.get(id) || makeEl(id),
  createElement: () => makeEl('tmp'),
};
// pre-create every id present in the markup
for (const m of html.matchAll(/id="([^"]+)"/g)) makeEl(m[1]);

const sent = [];
let socketInstance = null;
function FakeSocket(url) {
  this.url = url; this.readyState = 0; this.binaryType = 'arraybuffer'; this.sent = [];
  socketInstance = this;
  FakeSocket.last = this;
}
FakeSocket.OPEN = 1; FakeSocket.CLOSING = 2; FakeSocket.CLOSED = 3;
FakeSocket.prototype.send = function (d) { sent.push(d); };
FakeSocket.prototype.close = function () { this.readyState = 3; if (this.onclose) this.onclose(); };

const timers = [];
const ctx = {
  document: documentStub,
  window: { addEventListener: () => {}, AudioContext: function () { this.state = 'running'; this.resume = () => {}; this.currentTime = 0; } },
  WebSocket: FakeSocket,
  location: { protocol: 'http:', host: '127.0.0.1:47812' },
  setInterval: (fn) => { timers.push(fn); return timers.length; },
  clearInterval: () => {}, setTimeout: (fn) => { timers.push(fn); return timers.length; }, clearTimeout: () => {},
  console, Date, Math, JSON, Set, Map, isNaN,
};
ctx.globalThis = ctx;
vm.createContext(ctx);
vm.runInContext(js, ctx);

const results = [];
const check = (name, cond, extra = '') => results.push(`${cond ? 'PASS' : 'FAIL'}  ${name}${extra ? ' — ' + extra : ''}`);

// 1. socket created against the served origin
check('WebSocket opened on same origin', !!FakeSocket.last && FakeSocket.last.url === 'ws://127.0.0.1:47812/ws', FakeSocket.last && FakeSocket.last.url);

// 2. connection badge reflects state
FakeSocket.last.readyState = 1; FakeSocket.last.onopen && FakeSocket.last.onopen();
check('connection label after open', nodes.get('modeHint').textContent === 'متصل', JSON.stringify(nodes.get('modeHint').textContent));

// 3. streaming state message drives HUD + decoder init path
FakeSocket.last.onmessage && FakeSocket.last.onmessage({ data: JSON.stringify({ state: 'streaming', fps: 60, mime: 'video/avc', phoneName: 'BYD Seal', isLandscape: true }) });
check('fps badge shows LIVE 60', /LIVE • 60 FPS/.test(nodes.get('fpsBadge').textContent), nodes.get('fpsBadge').textContent);
check('codec badge H.264', nodes.get('codecBadge').textContent === 'H.264', nodes.get('codecBadge').textContent);
check('status line carries phone name', /BYD Seal/.test(nodes.get('statusFooter').textContent), nodes.get('statusFooter').textContent);

// 4. touch back-channel packets (BYD-WMP TOUCH_EVENT over WS JSON)
nodes.get('videoCanvas').fire('pointerdown', { clientX: 100, clientY: 50 });
nodes.get('videoCanvas').fire('pointerup', { clientX: 100, clientY: 50 });
const touches = sent.filter((d) => typeof d === 'string' && d.includes('"touch"'));
check('touch down/up forwarded to receiver', touches.length === 2, JSON.stringify(touches));
check('failures surfaced when VideoDecoder missing', (typeof VideoDecoder === 'undefined') ? /WebCodecs/.test(nodes.get('diagStateValue').textContent) : true, nodes.get('diagStateValue').textContent);

// 5. manual stop releases the socket and marks state
ctx.toggleConnection();
check('stop releases socket', FakeSocket.last.readyState === 3 || nodes.get('modeHint').textContent.includes('متوقف'), nodes.get('modeHint').textContent);

// 6. car profile switching keeps protocol ports in the header
ctx.selectCar('geely');
check('profile switch updates head-unit spec', /47810/.test(nodes.get('headUnitSpec').textContent), nodes.get('headUnitSpec').textContent);

console.log(results.join('\n'));
const failed = results.filter((r) => r.startsWith('FAIL'));
console.log(failed.length ? `\n${failed.length} simulator UI check(s) failed` : '\nALL SIMULATOR UI WIRING CHECKS PASSED');
process.exit(failed.length ? 1 : 0);
