/* Simulates RPG Maker MV and MZ saves around assets/agvn/html-compat.js. Copyright (c) 2026 agvn.io - MIT License.
 * The app's save folder (window.AgvnSaves) is a Map here, which takes the names AgvnHtmlSaves.FILE takes; MV keeps
 * saves in localStorage, MZ in localforage. */
const fs = require('fs'), vm = require('vm'), assert = require('assert');
const root = require('path').join(__dirname, '..', '..', '..');
const src = fs.readFileSync(require('path').join(root, 'app', 'src', 'main', 'assets', 'agvn', 'html-compat.js'), 'utf8');
const java = fs.readFileSync(require('path').join(root, 'app', 'src', 'main', 'java', 'com', 'winlator', 'cmod', 'agvn', 'AgvnHtmlSaves.java'), 'utf8');
const FILE = new RegExp('^(?:' + JSON.parse('"' + /FILE = Pattern\.compile\("((?:[^"\\]|\\.)*)"\)/.exec(java)[1] + '"') + ')$', 'u');

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
      read: n => (FILE.test(n) && files.has(n) ? files.get(n) : null),
      write(n, t) { this.writes++; if (this.fail || !FILE.test(n)) return false; files.set(n, t); return true; },
      exists: n => FILE.test(n) && files.has(n), remove: n => { if (FILE.test(n)) files.delete(n); },
      any: () => [...files.keys()].some(n => FILE.test(n)),
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

/* MV's StorageManager, as in rpg_managers.js 1.6 in a browser (isLocalMode() false): its file paths are what a PC uses. */
function mvEngine(ctx) {
  ctx.Utils = { RPGMAKER_NAME: 'MV' };
  ctx.StorageManager = {
    save(id, json) { this.saveToWebStorage(id, json); },
    load(id) { return this.loadFromWebStorage(id); },
    exists(id) { return this.webStorageExists(id); },
    remove(id) { this.removeWebStorage(id); },
    localFileDirectoryPath() { const path = ctx.require('path'); return path.join(path.dirname(ctx.process.mainModule.filename), 'save/'); },
    localFilePath(id) { return this.localFileDirectoryPath() + (id < 0 ? 'config.rpgsave' : id === 0 ? 'global.rpgsave' : 'file' + id + '.rpgsave'); },
    webStorageKey: id => (id < 0 ? 'RPG Config' : id === 0 ? 'RPG Global' : 'RPG File' + id),
    saveToWebStorage(id, json) { ctx.localStorage.setItem(this.webStorageKey(id), ctx.LZString.compressToBase64(json)); },
    loadFromWebStorage(id) { return ctx.LZString.decompressFromBase64(ctx.localStorage.getItem(this.webStorageKey(id))); },
    webStorageExists(id) { return !!ctx.localStorage.getItem(this.webStorageKey(id)); },
    removeWebStorage(id) { ctx.localStorage.removeItem(this.webStorageKey(id)); },
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
    fileDirectoryPath() { const path = ctx.require('path'); return path.join(path.dirname(ctx.process.mainModule.filename), 'save/'); },
    filePath(name) { return this.fileDirectoryPath() + name + '.rmmzsave'; },
    saveToForage(name, zip) { this._keys.push(key(name)); return ctx.localforage.setItem(key(name), zip); },
    loadFromForage: name => ctx.localforage.getItem(key(name)),
    forageExists(name) { return this._keys.includes(key(name)); },
    removeForage: name => ctx.localforage.removeItem(key(name)),
  };
  return ctx.StorageManager;
}

/* The plugin of the maintainer's report (09/10): its own data under 'My Plugin Data', in mydata.rpgsave on a PC. */
function counterPlugin(SM) {
  const ID = 'My Plugin Data', lfp = SM.localFilePath, wsk = SM.webStorageKey;
  SM.localFilePath = function (id) { return id === ID ? this.localFileDirectoryPath() + 'mydata.rpgsave' : lfp.apply(this, arguments); };
  SM.webStorageKey = function (id) { return id === ID ? 'Khoá riêng' : wsk.apply(this, arguments); };
  return () => { const n = Number(SM.load(ID) || 0) + 1; SM.save(ID, String(n)); return n; };
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

  // MV: a plugin's own data under an id that is not a number goes to the file its PC version writes
  files = new Map([['file1.rpgsave', 'Z{1}']]);
  browser = {};
  p = page(files, browser);
  SM = mvEngine(p.ctx);
  p.start();
  let enterMap = counterPlugin(SM);
  assert.strictEqual(enterMap(), 1, 'no "Cannot write save/fileMy Plugin Data.rpgsave"');
  assert.strictEqual(files.get('mydata.rpgsave'), 'Z1', "the plugin's own PC file, beside the saves");
  assert.strictEqual(enterMap(), 2);
  assert.ok(SM.exists('My Plugin Data') && !('Khoá riêng' in browser));
  SM.save('Gallery Data', '{"cg":[1]}'); // a plugin that keeps the engine's name: "file" + its id, as on a PC
  assert.strictEqual(files.get('fileGallery Data.rpgsave'), 'Z{"cg":[1]}');
  const named = SM.localFilePath;
  SM.localFilePath = function (id) { return id === 'odd' ? this.localFileDirectoryPath() + 'a:b?.rpgsave' : named.apply(this, arguments); };
  SM.save('odd', 'o'); // a PC name Windows would refuse: one made from the id
  assert.strictEqual(files.get('fileodd.rpgsave'), 'Zo');

  // the app starts again: the count goes on. The folder will not take it: the browser keeps it, and the game goes on
  p = page(files, browser);
  SM = mvEngine(p.ctx);
  p.start();
  enterMap = counterPlugin(SM);
  assert.strictEqual(enterMap(), 3);
  p.ctx.AgvnSaves.fail = true;
  assert.strictEqual(enterMap(), 4, 'no stop');
  assert.strictEqual(browser['Khoá riêng'], 'Z4', "under the plugin's own key");
  assert.ok(!files.has('mydata.rpgsave'), 'not an older count read first');
  assert.strictEqual(enterMap(), 5);
  assert.throws(() => SM.save(1, '{1c}'), /Cannot write save\/file1\.rpgsave/, 'a slot that cannot be written still says so');
  p.ctx.AgvnSaves.fail = false;
  assert.strictEqual(enterMap(), 6, "the browser's count goes on");
  assert.strictEqual(files.get('mydata.rpgsave'), 'Z6');
  SM.remove('My Plugin Data');
  assert.ok(!files.has('mydata.rpgsave') && !('Khoá riêng' in browser) && !SM.exists('My Plugin Data'));

  // its data from before, in the browser only, while the folder already has saves: read, then kept as its file
  browser = { 'Khoá riêng': 'Z41' };
  p = page(files, browser);
  SM = mvEngine(p.ctx);
  p.start();
  enterMap = counterPlugin(SM);
  assert.strictEqual(enterMap(), 42);
  assert.strictEqual(files.get('mydata.rpgsave'), 'Z42');

  // MZ: a plugin's own data has a name of its own, which may have a space
  files = new Map([['global.rmmzsave', 'pc']]);
  browser = {};
  p = page(files, browser);
  SM = mzEngine(p.ctx);
  p.start();
  await SM.saveToForage('My Data', 'zd');
  assert.strictEqual(files.get('My Data.rmmzsave'), 'zd');
  assert.strictEqual(await SM.loadFromForage('My Data'), 'zd');
  SM.filePath = function (name) { return this.fileDirectoryPath() + (name === 'Thành tựu' ? 'thanh-tuu' : name) + '.rmmzsave'; };
  await SM.saveToForage('Thành tựu', 'zt');
  assert.strictEqual(files.get('thanh-tuu.rmmzsave'), 'zt', "the plugin's PC file name");
  p.ctx.AgvnSaves.fail = true;
  await SM.saveToForage('My Data', 'zd2'); // the browser keeps it
  assert.strictEqual(browser['rmmzsave.4242.My Data'], 'zd2');
  assert.ok(!files.has('My Data.rmmzsave'));
  assert.strictEqual(await SM.loadFromForage('My Data'), 'zd2');
  await assert.rejects(SM.saveToForage('file2', 'z2'), 'a slot that cannot be written still says so');
  p.ctx.AgvnSaves.fail = false;
  await SM.removeForage('My Data');
  assert.ok(!('rmmzsave.4242.My Data' in browser) && !files.has('My Data.rmmzsave'));
  console.log('saves simulation OK');
})().catch(e => { console.error(e); process.exit(1); });
