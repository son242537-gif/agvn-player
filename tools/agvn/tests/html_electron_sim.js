/* Checks assets/agvn/html-electron.js: an Electron game's page in "Chạy nhẹ" gets require('electron'), greenworks and
 * steamworks.js through html-compat.js's require, all answering without the game's main script (26/10/2026).
 * Copyright (c) 2026 agvn.io - MIT License. */
const fs = require('fs'), vm = require('vm'), assert = require('assert'), path = require('path');
const dir = path.join(__dirname, '..', '..', '..', 'app', 'src', 'main', 'assets', 'agvn');
const warned = [], store = new Map();

function page(electron) {
  const ctx = { setTimeout, setInterval, clearInterval, Promise, Proxy, innerWidth: 1280, innerHeight: 720,
    navigator: { language: 'vi-VN' }, location: { href: 'https://g1.agvn.game/index.html', reload: () => {} },
    console: { log: () => {}, warn: m => warned.push(m), error: () => {} },
    document: { title: 'Game', visibilityState: 'visible', getElementById: () => null, hasFocus: () => true },
    XMLHttpRequest: function () { this.open = () => {}; this.send = () => { this.responseText = '{"version":"2.1.0"}'; }; },
    localStorage: { getItem: k => (store.has(k) ? store.get(k) : null), setItem: (k, v) => store.set(k, String(v)),
      removeItem: k => store.delete(k) }, addEventListener: () => {} };
  ctx.window = ctx;
  if (electron) ctx.__agvnElectron = true;
  vm.createContext(ctx);
  for (const f of ['html-electron.js', 'html-compat.js'])
    vm.runInContext(fs.readFileSync(path.join(dir, f), 'utf8'), ctx, { filename: f });
  return ctx;
}

(async () => {
  const other = page(false);
  assert.strictEqual(other.__agvnModules, undefined, 'no Electron modules for another page game');

  const ctx = page(true), run = code => vm.runInContext(code, ctx);
  const electron = run('require("electron")');
  assert.strictEqual(electron.then, undefined, 'not a thenable: await on it goes on');
  assert.strictEqual(await run('require("electron").ipcRenderer.invoke("load-save")'), undefined);
  run('require("electron").ipcRenderer.invoke("load-save")');
  assert.strictEqual(warned.filter(w => w.includes('invoke("load-save")')).length, 1, 'told once');
  assert.strictEqual(run('require("electron").ipcRenderer.on("a", () => {}).once("b", () => {}).send("c")'), undefined);
  assert.strictEqual(run(`(() => { const w = require("@electron/remote").getCurrentWindow();
    w.on("close", () => {}).setFullScreen(false); return w.isFullScreen(); })()`), false);
  assert.strictEqual(run('require("electron").remote.getCurrentWindow().setKiosk(true)'), undefined, 'a method it has not');
  assert.ok(warned.some(w => w.includes('BrowserWindow.setKiosk()')));
  assert.deepStrictEqual(Array.from(run('require("electron").remote.getCurrentWindow().getSize()')), [1280, 720]);
  assert.strictEqual(run('require("electron").remote.app.getVersion()'), '2.1.0');
  assert.strictEqual(run('require("electron").remote.app.getLocale()'), 'vi-VN');
  assert.strictEqual(run('require("electron").remote.process === process'), true);
  assert.strictEqual(await run('require("electron").shell.openExternal("https://agvn.io")'), undefined);
  assert.strictEqual(run('require("electron").contextBridge.exposeInMainWorld("api", {x: 1}), api.x'), 1);
  assert.strictEqual(await run('require("electron").remote.getCurrentWebContents().executeJavaScript("1 + 2")'), 3);

  const steam = run('require("./lib/greenworks.js")');
  assert.strictEqual(steam, run('require("greenworks")'), 'by a path too');
  assert.strictEqual(steam.init(), true);
  assert.strictEqual(steam.getSteamId().screenName, 'Player');
  const got = await new Promise(ok => steam.activateAchievement('ACH_1', () => ok('yes')));
  assert.strictEqual(got, 'yes');
  await new Promise(ok => steam.saveTextToFile('save1.json', '{"lv":3}', ok, () => assert.fail('not saved')));
  assert.strictEqual(await new Promise(ok => steam.readTextFromFile('save1.json', ok, () => ok(null))), '{"lv":3}');
  assert.strictEqual(await new Promise(ok => steam.readTextFromFile('none.json', () => ok('?'), e => ok(e.message))),
    'Steam Cloud: không có none.json');
  assert.strictEqual(run('require("steamworks.js").init(480).achievement.activate("A")'), true);
  assert.strictEqual(typeof run('require("steamworks.js").init(480).callback.register(1, () => {}).disconnect'), 'function');

  assert.deepStrictEqual(JSON.parse(JSON.stringify(run('require("zlib")'))), {}, 'a module the app has not');
  assert.deepStrictEqual(Array.from(ctx.__agvnMissingModules), ['zlib']);
  console.log('html-electron ok');
})().catch(e => { console.error(e); process.exit(1); });
