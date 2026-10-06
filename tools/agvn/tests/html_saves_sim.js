/* Simulates RPG Maker MV and MZ saves around assets/agvn/html-compat.js. Copyright (c) 2026 agvn.io - MIT License.
 * The app's save folder (window.AgvnSaves) is a Map here; MV keeps saves in localStorage, MZ in localforage. */
const fs = require('fs'), vm = require('vm'), assert = require('assert');
const src = fs.readFileSync(require('path').join(__dirname, '..', '..', '..', 'app', 'src', 'main', 'assets', 'agvn', 'html-compat.js'), 'utf8');

function page(files, browser, readOnly) {
  const listeners = {};
  const keys = () => Object.keys(browser);
  const ctx = {
    console: { warn: () => {}, error: () => {}, log: console.log },
    document: { title: 't' }, setInterval, clearInterval, setTimeout, Date, Error, TypeError, ReferenceError, String, Array,
    Number, Object, Promise, encodeURI,
    localStorage: {
      get length() { return keys().length; }, key: i => keys()[i] || null,
      getItem: k => (k in browser ? browser[k] : null), setItem: (k, v) => { browser[k] = String(v); }, removeItem: k => { delete browser[k]; },
    },
    localforage: {
      keys: () => Promise.resolve(keys()), getItem: k => Promise.resolve(k in browser ? browser[k] : null),
      setItem: (k, v) => { browser[k] = v; return Promise.resolve(); }, removeItem: k => { delete browser[k]; return Promise.resolve(); },
    },
    AgvnSaves: {
      writes: 0, fail: false, readOnly: false, writable() { return !this.readOnly; },
      read: n => (files.has(n) ? files.get(n) : null),
      write(n, t) { this.writes++; if (this.fail) return false; files.set(n, t); return true; },
      exists: n => files.has(n), remove: n => { files.delete(n); }, any: () => files.size > 0,
    },
    LZString: { compressToBase64: s => 'Z' + s, decompressFromBase64: s => (s == null ? '' : s.slice(1)) },
  };
  ctx.AgvnSaves.readOnly = !!readOnly;
  ctx.window = ctx;
  ctx.addEventListener = (n, f) => { listeners[n] = f; };
  vm.createContext(ctx);
  vm.runInContext(src, ctx);
  ctx.SceneManager = { catchException() {}, requestUpdate() {} };
  return { ctx, start: () => listeners.load() };
}

/* MV's web storage functions, as in rpg_managers.js. */
function mvEngine(ctx) {
  const key = id => (id < 0 ? 'RPG Config' : id === 0 ? 'RPG Global' : 'RPG File' + id);
  ctx.Utils = { RPGMAKER_NAME: 'MV' };
  ctx.StorageManager = {
    saveToWebStorage: (id, json) => ctx.localStorage.setItem(key(id), ctx.LZString.compressToBase64(json)),
    loadFromWebStorage: id => ctx.LZString.decompressFromBase64(ctx.localStorage.getItem(key(id))),
    webStorageExists: id => !!ctx.localStorage.getItem(key(id)),
    removeWebStorage: id => ctx.localStorage.removeItem(key(id)),
  };
  return ctx.StorageManager;
}

/* MZ's forage functions, as in rmmz_managers.js; its keys hold the game's id. */
function mzEngine(ctx) {
  const key = name => 'rmmzsave.4242.' + name;
  ctx.Utils = { RPGMAKER_NAME: 'MZ' };
  ctx.StorageManager = {
    _keys: [],
    forageKey: key,
    saveToForage(name, zip) { return ctx.localforage.setItem(key(name), zip); },
    loadFromForage: name => ctx.localforage.getItem(key(name)),
    forageExists(name) { return this._keys.includes(key(name)); },
    removeForage: name => ctx.localforage.removeItem(key(name)),
  };
  return ctx.StorageManager;
}

(async () => {
  // MV, played on the phone before: the browser's saves become files, then every save goes to the folder
  let files = new Map(), browser = { 'RPG Global': 'Z[g]', 'RPG File1': 'Z{1}', 'RPG Config': 'Z{c}', other: 'x' };
  let p = page(files, browser), SM = mvEngine(p.ctx);
  p.start();
  assert.deepStrictEqual([...files.keys()].sort(), ['config.rpgsave', 'file1.rpgsave', 'global.rpgsave']);
  assert.strictEqual(files.get('file1.rpgsave'), 'Z{1}', 'the same text as a PC file');
  assert.strictEqual(SM.loadFromWebStorage(1), '{1}');
  SM.saveToWebStorage(2, '{2}');
  assert.strictEqual(files.get('file2.rpgsave'), 'Z{2}');
  assert.ok(!('RPG File2' in browser), 'a new save is a file only');
  assert.ok(SM.webStorageExists(2) && !SM.webStorageExists(3));
  // a save that cannot be written: MV then removes it and restores its backup; the last good file stays
  p.ctx.AgvnSaves.fail = true;
  assert.throws(() => SM.saveToWebStorage(2, '{2b}'));
  SM.removeWebStorage(2);
  assert.strictEqual(files.get('file2.rpgsave'), 'Z{2}');
  p.ctx.AgvnSaves.fail = false;
  SM.removeWebStorage(2);
  assert.ok(!files.has('file2.rpgsave'), 'a save the player deletes goes');

  // MV with saves already in the folder (copied from a PC, or "Nhập save"): the browser's are left alone
  files = new Map([['file1.rpgsave', 'Z{pc}']]);
  browser = { 'RPG File1': 'Z{phone}', 'RPG File5': 'Z{5}' };
  p = page(files, browser);
  SM = mvEngine(p.ctx);
  p.start();
  assert.strictEqual(SM.loadFromWebStorage(1), '{pc}');
  assert.ok(!SM.webStorageExists(5) && !files.has('file5.rpgsave'));
  assert.strictEqual(SM.loadFromWebStorage(5), '', 'no file: empty, as the engine reads a missing save');

  // MV in a folder the app cannot write to: saves stay in the browser, as before
  files = new Map();
  browser = { 'RPG File1': 'Z{1}' };
  p = page(files, browser, true);
  SM = mvEngine(p.ctx);
  p.start();
  assert.strictEqual(SM.loadFromWebStorage(1), '{1}');
  SM.saveToWebStorage(2, '{2}');
  assert.strictEqual(browser['RPG File2'], 'Z{2}');
  assert.strictEqual(files.size, 0);

  // MZ, played on the phone before: carried over at the first load, then files only
  files = new Map();
  browser = { 'rmmzsave.4242.global': 'zg', 'rmmzsave.4242.file0': 'z0', 'rmmzsave.4242.file3': 'z3', 'rmmzsave.99.file1': 'not ours' };
  p = page(files, browser);
  SM = mzEngine(p.ctx);
  SM._keys = Object.keys(browser);
  p.start();
  assert.strictEqual(await SM.loadFromForage('global'), 'zg');
  assert.deepStrictEqual([...files.keys()].sort(), ['file0.rmmzsave', 'file3.rmmzsave', 'global.rmmzsave']);
  assert.ok(SM.forageExists('file3'));
  await SM.saveToForage('file4', 'z4é');
  assert.strictEqual(files.get('file4.rmmzsave'), 'z4é');
  assert.ok(!('rmmzsave.4242.file4' in browser));
  p.ctx.AgvnSaves.fail = true;
  await assert.rejects(SM.saveToForage('file4', 'z4b'));
  assert.strictEqual(files.get('file4.rmmzsave'), 'z4é', 'the last good save stays');
  p.ctx.AgvnSaves.fail = false;
  await SM.removeForage('file3');
  assert.ok(!files.has('file3.rmmzsave'));

  // MZ with saves in the folder: read from there, nothing carried over
  files = new Map([['global.rmmzsave', 'pc']]);
  browser = { 'rmmzsave.4242.file1': 'z1' };
  p = page(files, browser);
  SM = mzEngine(p.ctx);
  SM._keys = Object.keys(browser);
  p.start();
  assert.strictEqual(await SM.loadFromForage('global'), 'pc');
  assert.strictEqual(await SM.loadFromForage('file1'), null);
  assert.ok(!SM.forageExists('file1') && !files.has('file1.rmmzsave'));
  console.log('saves simulation OK');
})().catch(e => { console.error(e); process.exit(1); });
