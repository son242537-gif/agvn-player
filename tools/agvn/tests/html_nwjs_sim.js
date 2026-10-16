/* An NW.js page game around assets/agvn/html-compat.js (AgvnNwjsGame). Copyright (c) 2026 agvn.io - MIT License.
 * Run: node tools/agvn/tests/html_nwjs_sim.js */
const fs = require('fs'), vm = require('vm'), assert = require('assert');
const src = fs.readFileSync(require('path').join(__dirname, '..', '..', '..', 'app', 'src', 'main', 'assets', 'agvn', 'html-compat.js'), 'utf8');

function page(nwjs, ownNw) {
  const store = {};
  const ctx = {
    console: { warn: () => {}, error: () => {}, log: () => {} },
    document: { title: 't' }, setInterval, clearInterval, setTimeout, Date, Error, TypeError, ReferenceError, String, Array, JSON,
    Object, encodeURI, Promise,
    localStorage: { getItem: k => (k in store ? store[k] : null), setItem: (k, v) => { store[k] = v; }, removeItem: k => { delete store[k]; } },
    XMLHttpRequest: function () { this.open = (m, u) => { this.u = u; }; this.overrideMimeType = () => {}; this.send = () => { this.status = this.u === '/package.json' ? 200 : 404; this.responseText = '{"name":"lmb","main":"index.html","window":{"width":1280}}'; }; },
    location: { reloads: 0, reload() { this.reloads++; } },
    addEventListener: () => {},
  };
  ctx.window = ctx;
  if (nwjs) ctx.__agvnNwjs = true;
  if (ownNw) ctx.nw = ownNw;
  vm.createContext(ctx);
  vm.runInContext(src, ctx);
  return ctx;
}

// RPG Maker (no __agvnNwjs): no `nw`, as before; require('nw.gui') still answers
const mv = page(false);
assert.strictEqual(typeof mv.nw, 'undefined');
assert.strictEqual(typeof mv.require('nw.gui').Window.get().enterFullscreen, 'function');

// an NW.js page game: scripts that use nw without asking go on
const g = page(true);
assert.strictEqual(typeof g.nw, 'object');
vm.runInContext(`
  var win = nw.Window.get();
  win.on('close', function () { this.close(true); }).on('minimize', function () {});
  win.enterFullscreen(); win.setAlwaysOnTop(false); win.resizeTo(1280, 720); win.show();
  nw.Screen.Init();
  var title = nw.App.manifest.name, width = nw.App.manifest.window.width;
  win.reload();
`, g);
assert.strictEqual(vm.runInContext('title', g), 'lmb');
assert.strictEqual(vm.runInContext('width', g), 1280);
assert.strictEqual(g.location.reloads, 1);
// saves through fs go to the browser, as for RPG Maker plugins
vm.runInContext(`require('fs').writeFileSync(nw.App.dataPath + 'save/slot1.json', '{"a":1}');`, g);
assert.strictEqual(vm.runInContext(`require('fs').readFileSync('/save/slot1.json')`, g), '{"a":1}');
// a page that already has nw keeps its own
const mine = { mine: true };
assert.strictEqual(page(true, mine).nw, mine);
console.log('html_nwjs_sim: ok');
