/* Simulates an RPG Maker MV engine around assets/agvn/html-compat.js. Copyright (c) 2026 agvn.io - MIT License. */
const fs = require('fs'), vm = require('vm');
const src = fs.readFileSync(require('path').join(__dirname, '..', '..', '..', 'app', 'src', 'main', 'assets', 'agvn', 'html-compat.js'), 'utf8');
const store = {};
const listeners = {};
const ctx = {
  console: { warn: () => {}, error: () => {}, log: console.log },
  document: { title: 't' }, setInterval, clearInterval, setTimeout, Date, Error, TypeError, ReferenceError, String, Array, encodeURI,
  localStorage: { getItem: k => (k in store ? store[k] : null), setItem: (k, v) => { store[k] = v; }, removeItem: k => { delete store[k]; } },
  XMLHttpRequest: function () { this.open = (m, u) => { this.u = u; }; this.overrideMimeType = () => {}; this.send = () => { this.status = this.u === '/data/Items.json' ? 200 : 404; this.responseText = '[1]'; }; },
};
ctx.window = ctx;
ctx.addEventListener = (n, f) => { listeners[n] = f; };
vm.createContext(ctx);
vm.runInContext(src, ctx);
let stopped = 0, updates = 0;
ctx.Utils = { RPGMAKER_NAME: 'MV' };
ctx.SceneManager = { catchException(e) { stopped++; }, requestUpdate() { updates++; } };
ctx.AudioManager = { checkErrors() { throw 'Failed to load: audio/bgm/x.ogg'; }, checkWebAudioError() { throw 'x'; } };
listeners.load();
const assert = require('assert');
assert.strictEqual(typeof ctx.process, 'undefined');
const p = ctx.require('path');
assert.strictEqual(p.join('/www', 'save', '../data', 'a.json'), '/www/data/a.json');
assert.strictEqual(p.basename('/a/b.rpgsave', '.rpgsave'), 'b');
const f = ctx.require('fs');
assert.strictEqual(f.existsSync('/data/Items.json'), true);
assert.strictEqual(f.readFileSync('/data/Items.json'), '[1]');
f.writeFileSync('/save/file1.rpgsave', 'abc');
assert.strictEqual(f.readFileSync('save/file1.rpgsave'), 'abc');
assert.throws(() => f.readFileSync('/nope.json'));
ctx.AudioManager.checkErrors(); // no throw
// minor errors are skipped and the loop continues
ctx.SceneManager.catchException(new TypeError("Cannot read property 'opacity' of undefined"));
ctx.SceneManager.catchException(new TypeError("Cannot read properties of undefined (reading 'length')"));
assert.strictEqual(stopped, 0); assert.strictEqual(updates, 2);
// a real error still stops
ctx.SceneManager.catchException(new Error('Failed to load: img/x.png'));
assert.strictEqual(stopped, 1);
// a flood of minor errors falls back to the error screen
for (let i = 0; i < 70; i++) ctx.SceneManager.catchException(new TypeError("x is not a function"));
assert.ok(stopped > 1, 'flood falls back');
console.log('compat simulation OK: stopped=' + stopped + ' updates=' + updates);
process.exit(0);
