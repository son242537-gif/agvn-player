/* Plays assets/agvn/html-frame.js on screens of 60, 90, 120 and 144 Hz, with a game that asks for a frame from each
 * frame, as RPG Maker MV/MZ and PIXI do. Copyright (c) 2026 agvn.io - MIT License. */
const fs = require('fs'), path = require('path'), vm = require('vm'), assert = require('assert');
const src = fs.readFileSync(path.join(__dirname, '..', '..', '..', 'app', 'src', 'main', 'assets', 'agvn', 'html-frame.js'), 'utf8');

function screen(hz, maxFps, seconds, game) {
  let queue = [], id = 0, now = 0, errors = [];
  const ctx = {
    Map, setTimeout,
    requestAnimationFrame: cb => { queue.push({ id: ++id, cb }); return id; },
    cancelAnimationFrame: i => { queue = queue.filter(q => q.id !== i); },
    reportError: e => errors.push(e),
    __agvnMaxFps: maxFps,
  };
  ctx.window = ctx;
  vm.createContext(ctx);
  vm.runInContext(src, ctx);
  game(ctx);
  const period = 1000 / hz;
  for (let i = 1; i <= hz * seconds; i++) { // the screen's refreshes, each a little early or late
    now = i * period + (i % 3 - 1) * 0.3;
    const run = queue;
    queue = [];
    run.forEach(q => q.cb(now));
  }
  return { ctx, errors, pending: queue.length };
}

function loop(ctx) {
  const times = ctx.__times = [];
  (function f(t) { if (t !== undefined) times.push(t); ctx.requestAnimationFrame(f); })();
}

for (const hz of [60, 90, 120, 144]) {
  const { ctx } = screen(hz, 60, 10, loop);
  const fps = ctx.__times.length / 10;
  assert(fps >= 58 && fps <= 60.5, hz + ' Hz: ' + fps + ' frames a second');
  assert.strictEqual(ctx.__agvnFrame.frames, ctx.__times.length);
  if (hz === 120) { // every other refresh, evenly
    for (let i = 1; i < ctx.__times.length; i++) {
      const gap = ctx.__times[i] - ctx.__times[i - 1];
      assert(gap > 15 && gap < 18.5, '120 Hz gap ' + gap);
    }
  }
}
assert.strictEqual(screen(60, 60, 10, loop).ctx.__times.length, 600, '60 Hz: every refresh, as before');
assert.strictEqual(screen(120, 0, 10, loop).ctx.__times.length, 1200, '"Tần số quét cao": every refresh');

// a cancelled callback is not called; one cancelled by another callback of the same frame neither
let r = screen(120, 60, 1, ctx => {
  ctx.called = [];
  const a = ctx.requestAnimationFrame(() => ctx.called.push('a'));
  ctx.requestAnimationFrame(() => { ctx.called.push('b'); ctx.cancelAnimationFrame(c); });
  const c = ctx.requestAnimationFrame(() => ctx.called.push('c'));
  ctx.cancelAnimationFrame(a);
});
assert.deepStrictEqual(r.ctx.called, ['b']);
assert.strictEqual(r.pending, 0, 'nothing asked: the screen is not asked for frames any more');

// a callback that throws is told, and the others still run
r = screen(120, 60, 1, ctx => {
  ctx.called = [];
  ctx.requestAnimationFrame(() => { throw new Error('boom'); });
  ctx.requestAnimationFrame(() => ctx.called.push('after'));
});
assert.deepStrictEqual(r.ctx.called, ['after']);
assert.strictEqual(r.errors.length, 1);

console.log('html-frame.js: all cases passed');

// the HUD's probe (AgvnHtmlKeys.FPS) reads the frames counted here, and starts no loop of its own
{
  const java = fs.readFileSync(path.join(__dirname, '..', '..', '..', 'app', 'src', 'main', 'java', 'com', 'winlator', 'cmod',
    'agvn', 'AgvnHtmlKeys.java'), 'utf8');
  const decl = /static final String FPS = ([\s\S]*?);\n/.exec(java)[1];
  const probe = JSON.parse('"' + decl.match(/"((?:[^"\\]|\\.)*)"/g).map(q => q.slice(1, -1)).join('') + '"');
  let clock = 0, queue = [];
  const ctx = { Map, setTimeout, __agvnMaxFps: 60, performance: { now: () => clock },
    requestAnimationFrame: cb => { queue.push(cb); return queue.length; }, cancelAnimationFrame: () => {} };
  ctx.window = ctx;
  vm.createContext(ctx);
  assert.strictEqual(vm.runInContext(probe, ctx), -1, 'before html-frame.js: unknown');
  vm.runInContext(src, ctx);
  assert.strictEqual(vm.runInContext(probe, ctx), -1, 'the first reading starts the count');
  assert.strictEqual(queue.length, 0, 'the probe asks for no frame');
  vm.runInContext('var go = true; (function f(){ if (go) requestAnimationFrame(f); })()', ctx);
  for (let i = 1; i <= 600; i++) { clock = i * 1000 / 120; const run = queue; queue = []; run.forEach(cb => cb(clock)); }
  const fps = vm.runInContext(probe, ctx);
  assert(fps >= 59 && fps <= 61, 'the page drew ' + fps + ' frames a second on a 120 Hz screen');
  vm.runInContext('go = false', ctx); // a still page: it asks for no frame
  for (let i = 0; i < 3; i++) { clock += 1000 / 120; const run = queue; queue = []; run.forEach(cb => cb(clock)); }
  vm.runInContext(probe, ctx);
  clock += 5000;
  assert.strictEqual(vm.runInContext(probe, ctx), 0, 'a still page drew nothing');
  assert.strictEqual(queue.length, 0, 'and the screen is not asked for frames');
}
console.log('AgvnHtmlKeys.FPS: reads html-frame.js');
