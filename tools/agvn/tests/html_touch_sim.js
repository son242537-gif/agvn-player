/* Touches on a page's own fields over an RPG Maker MV/MZ game, around assets/agvn/html-compat.js (report of
 * 20/10/2026, docs/agvn/bao-cao/2026-10-20-cham-o-nhap-html.md), with path.parse and process.execPath.
 * Copyright (c) 2026 agvn.io - MIT License. Run: node tools/agvn/tests/html_touch_sim.js */
const fs = require('fs'), vm = require('vm'), assert = require('assert'), nodePath = require('path');
const src = fs.readFileSync(nodePath.join(__dirname, '..', '..', '..', 'app', 'src', 'main', 'assets', 'agvn',
  'html-compat.js'), 'utf8');

// listeners as a browser keeps them, and dispatch as it does: capture from window down, the target, bubble up
function target(o) {
  o.l = [];
  o.addEventListener = (type, f, opt) => o.l.push({ type, f, capture: opt === true || !!(opt && opt.capture) });
  o.removeEventListener = (type, f, opt) => {
    const capture = opt === true || !!(opt && opt.capture);
    o.l = o.l.filter(x => !(x.type === type && x.f === f && x.capture === capture));
  };
  return o;
}
function fire(n, ev, capture) {
  n.l.filter(x => x.type === ev.type && x.capture === capture).forEach(x => { if (!ev.stopped) x.f.call(n, ev); });
}
function dispatch(win, t, type, extra) {
  const ev = Object.assign({ type, target: t, defaultPrevented: false, stopped: false,
    preventDefault() { this.defaultPrevented = true; }, stopPropagation() { this.stopped = true; } }, extra);
  const path = [];
  for (let n = t; n; n = n.parentNode) path.push(n);
  if (type !== 'load') path.push(win); // a script's load stops at document
  for (let i = path.length - 1; i > 0; i--) fire(path[i], ev, true);
  fire(t, ev, true); fire(t, ev, false); // at the target, capture first (Chrome 89+)
  for (let i = 1; i < path.length; i++) fire(path[i], ev, false);
  return ev;
}
function el(tag, parent, props) {
  return target(Object.assign({ tagName: tag, nodeType: 1, parentNode: parent, style: {} }, props));
}
const touch = (win, t, x, y) => dispatch(win, t, 'touchstart', { changedTouches: [{ pageX: x, pageY: y }], touches: [{}] });

// TouchInput as rpg_core.js 1.6 (MV) and rmmz_core.js 1.x (MZ) have it, the touch start word for word
const ENGINE = (mz) => `
var Utils = { RPGMAKER_NAME: '${mz ? 'MZ' : 'MV'}' };
var Graphics = { _realScale: 0.5, _width: 816, _height: 624,
  pageToCanvasX: function (x) { return Math.round(x / this._realScale); },
  pageToCanvasY: function (y) { return Math.round(y / this._realScale); },
  isInsideCanvas: function (x, y) { return x >= 0 && x < this._width && y >= 0 && y < this._height; } };
function TouchInput() { throw new Error('This is a static class'); }
TouchInput.initialize = function () { this.clear(); this._setupEventHandlers(); };
TouchInput.clear = function () { this._mousePressed = false; this._screenPressed = false; this._pressedTime = 0; this.taps = 0; };
TouchInput._setupEventHandlers = function () {
  document.addEventListener('touchstart', this._onTouchStart.bind(this), { passive: false });
};
TouchInput._onTrigger = function () { this.taps++; };
TouchInput._onCancel = function () {};
` + (mz ? `
TouchInput._onTouchStart = function(event) {
    for (const touch of event.changedTouches) {
        const x = Graphics.pageToCanvasX(touch.pageX);
        const y = Graphics.pageToCanvasY(touch.pageY);
        if (Graphics.isInsideCanvas(x, y)) {
            this._screenPressed = true;
            this._pressedTime = 0;
            if (event.touches.length >= 2) { this._onCancel(x, y); } else { this._onTrigger(x, y); }
            event.preventDefault();
        }
    }
    if (window.cordova || window.navigator.standalone) { event.preventDefault(); }
};` : `
TouchInput._onTouchStart = function(event) {
    for (var i = 0; i < event.changedTouches.length; i++) {
        var touch = event.changedTouches[i];
        var x = Graphics.pageToCanvasX(touch.pageX);
        var y = Graphics.pageToCanvasY(touch.pageY);
        if (Graphics.isInsideCanvas(x, y)) {
            this._screenPressed = true;
            this._pressedTime = 0;
            if (event.touches.length >= 2) { this._onCancel(x, y); } else { this._onTrigger(x, y); }
            event.preventDefault();
        }
    }
    if (window.cordova || window.navigator.standalone) { event.preventDefault(); }
};`) + `
var SceneManager = { run: function () { TouchInput.initialize(); }, catchException: function () {} };`;

// a page: the game's canvas, and a plugin's name box over it (<form> with <input> and its OK button)
function page(opts) {
  const doc = target({ title: 't', nodeType: 9, visibilityState: 'visible', getElementById: () => null });
  const html = el('HTML', doc), body = el('BODY', html);
  const ctx = target({ console: { warn() {}, error() {}, log: (s) => ctx.logged.push(s) }, logged: [], document: doc,
    setInterval, clearInterval, setTimeout, Date, Error, TypeError, ReferenceError, String, Array, encodeURI, Math,
    navigator: {}, localStorage: { getItem: () => null, setItem() {}, removeItem() {} } });
  ctx.window = ctx;
  if (opts.exe !== undefined) ctx.__agvnExe = opts.exe;
  vm.createContext(ctx);
  if (opts.withCompat !== false) vm.runInContext(src, ctx);
  const canvas = el('CANVAS', body), form = el('FORM', body), input = el('INPUT', form, { type: 'text', form }),
    ok = el('INPUT', form, { type: 'submit', form }), button = el('BUTTON', body), label = el('SPAN', button),
    note = el('DIV', body, { isContentEditable: true }), box = el('DIV', body);
  const script = el('SCRIPT', body);
  vm.runInContext(ENGINE(opts.mz), ctx);
  if (opts.lateSetup) vm.runInContext('TouchInput.initialize();', ctx); // a game that starts as its scripts load
  if (!opts.noScriptLoad) dispatch(ctx, script, 'load'); // rpg_core / rmmz_core has run
  if (opts.plugin) vm.runInContext(opts.plugin, ctx);
  ctx.l.filter(x => x.type === 'load').forEach(x => x.f({})); // window load: main.js then runs the game
  if (!opts.lateSetup) vm.runInContext('SceneManager.run();', ctx);
  return { ctx, canvas, form, input, ok, button, label, note, box, taps: () => vm.runInContext('TouchInput.taps', ctx) };
}

for (const mz of [false, true]) {
  const name = mz ? 'MZ' : 'MV';
  // before: the engine cancels the touch on the box, so the browser gives it no focus and no click
  const old = page({ mz, withCompat: false });
  assert.strictEqual(touch(old.ctx, old.input, 120, 100).defaultPrevented, true, name + ': the report, without the fix');

  const p = page({ mz });
  let ownTouch = 0;
  p.input.addEventListener('touchstart', () => ownTouch++); // the plugin's own listener still hears it
  for (const t of [p.input, p.ok, p.label, p.note]) {
    assert.strictEqual(touch(p.ctx, t, 120, 100).defaultPrevented, false, name + ': ' + t.tagName + ' is the page\'s');
  }
  assert.strictEqual(p.taps(), 0, name + ': and no tap on the game under it');
  assert.strictEqual(ownTouch, 1);
  assert.strictEqual(touch(p.ctx, p.canvas, 120, 100).defaultPrevented, true, name + ': the game\'s picture as before');
  assert.strictEqual(touch(p.ctx, p.box, 120, 100).defaultPrevented, true, name + ': a plain element over it too');
  assert.strictEqual(p.taps(), 2);
  assert.strictEqual(touch(p.ctx, p.canvas, 900, 100).defaultPrevented, false, name + ': outside the picture as before');

  // a plugin that replaced the handler after rpg_core, without calling the engine's: wrapped again before the game runs
  const r = page({ mz, plugin: 'TouchInput._onTouchStart = function (e) { this.taps++; e.preventDefault(); };' });
  assert.strictEqual(touch(r.ctx, r.input, 120, 100).defaultPrevented, false, name + ': a plugin\'s own handler');
  assert.strictEqual(touch(r.ctx, r.canvas, 120, 100).defaultPrevented, true);

  // TouchInput set up before this file could wrap it: the bound handler finds the box outside the game's picture
  const late = page({ mz, lateSetup: true, noScriptLoad: true });
  assert.strictEqual(touch(late.ctx, late.input, 120, 100).defaultPrevented, false, name + ': set up early');
  assert.strictEqual(touch(late.ctx, late.canvas, 120, 100).defaultPrevented, true, name + ': set up early, the game');
  assert.strictEqual(late.taps(), 1, name + ': one tap, the game\'s, never two');
  assert.strictEqual(vm.runInContext('Graphics.isInsideCanvas(10, 10)', late.ctx), true, 'and the picture is whole after');
}

// 11: path.parse / format as Node's, and process.execPath names the game's exe
const pc = page({ exe: 'Game_E.exe' }).ctx;
const p = vm.runInContext('require("path")', pc);
for (const s of ['/Game.exe', 'Game.exe', '/', '//', '', '//a', '/a//b', '/a/b//', '/.bashrc', '..', '...', 'a.', '.a.b',
  '/a/file.tar.gz', 'a/b', 'a/', '/www/index.html']) {
  assert.deepStrictEqual(Object.assign({}, p.parse(s)), nodePath.posix.parse(s), 'parse ' + JSON.stringify(s));
}
assert.strictEqual(p.parse('C:\\Games\\Game_E.exe').name, 'Game_E', 'a Windows path, as a PC reads it');
for (const o of [{ root: '/', dir: '/a', base: 'b.txt' }, { root: '/', name: 'x', ext: '.exe' }, { dir: 'a', name: 'x', ext: '.e' },
  { name: 'x', ext: '.e' }, { root: '/', base: 'q' }]) assert.strictEqual(p.format(o), nodePath.posix.format(o));
assert.strictEqual(vm.runInContext('process.execPath', pc), '/Game_E.exe');
assert.strictEqual(vm.runInContext('require("path").parse(process.execPath).name', pc), 'Game_E');
assert.strictEqual(vm.runInContext('process.execPath', page({}).ctx), '/Game.exe', 'none given: Game.exe');
assert.strictEqual(vm.runInContext('process.execPath', page({ exe: 'D:\\x/My Game.exe' }).ctx), '/My Game.exe');

// the box sized for the game at full size covers its OK button on a game at 0.5: narrowed once, the button never
function boxes(scale) {
  const q = page({});
  q.ctx.Graphics._realScale = scale;
  q.ctx.getComputedStyle = (e) => ({ boxSizing: 'content-box', paddingLeft: '2px', paddingRight: '2px', borderLeftWidth: '1px',
    borderRightWidth: '1px', position: e.style.position || 'static', zIndex: e.style.zIndex || 'auto' });
  const rect = (left, top, w, h) => ({ left, top, width: w, height: h, right: left + w, bottom: top + h });
  q.input.offsetWidth = 200;
  q.input.getBoundingClientRect = () => rect(100, 200, q.input.style.width ? parseFloat(q.input.style.width) + 6 : 200, 20);
  q.ok.getBoundingClientRect = () => rect(q.okLeft, 200, 44, 14);
  q.okLeft = 200;
  q.form.querySelectorAll = () => [q.input, q.ok];
  return q;
}
const wait = (ms) => new Promise((done) => setTimeout(done, ms));
const focus = (q) => dispatch(q.ctx, q.input, 'focusin');
(async () => {
  const q = boxes(0.5);
  focus(q);
  await wait(450);
  assert.strictEqual(q.input.style.width, '92px', 'ends 2 px before the button (98 px with padding and border)');
  focus(q);
  await wait(450);
  assert.strictEqual(q.input.style.width, '92px', 'never twice');
  assert.deepStrictEqual(q.ok.style, {}, 'the button keeps its size and place');
  assert.ok(q.ctx.logged.some((s) => /narrowed/.test(s)), 'a note in the console');

  const full = boxes(1);
  focus(full);
  await wait(450);
  assert.strictEqual(full.input.style.width, undefined, 'a game at full size: nothing changes');

  const left = boxes(0.5);
  left.okLeft = 104; // the button over the box's start: in front of it instead
  focus(left);
  await wait(450);
  assert.strictEqual(left.input.style.width, undefined);
  assert.strictEqual(left.ok.style.zIndex, '1');
  assert.strictEqual(left.ok.style.position, 'relative');

  const fight = boxes(0.5); // a plugin that sets its width back every time
  fight.input.getBoundingClientRect = () => ({ left: 100, top: 200, width: 200, height: 20, right: 300, bottom: 220 });
  for (let i = 0; i < 3; i++) { focus(fight); await wait(450); }
  assert.strictEqual(fight.ok.style.zIndex, '1', 'after three tries the button comes in front');
  console.log('html touch simulation OK');
  process.exit(0);
})().catch((e) => { console.error(e); process.exit(1); });
