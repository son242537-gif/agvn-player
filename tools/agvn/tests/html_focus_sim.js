/* Simulates RPG Maker MZ's scene loop around assets/agvn/html-compat.js, on a page WebView shows without the focus
 * (Android 9+ in touch mode). Copyright (c) 2026 agvn.io - MIT License. */
const fs = require('fs'), vm = require('vm'), assert = require('assert');
const src = fs.readFileSync(require('path').join(__dirname, '..', '..', '..', 'app', 'src', 'main', 'assets', 'agvn', 'html-compat.js'), 'utf8');

const page = Object.create({ hasFocus: () => false }); // what WebView answered: shown, never picked
page.title = 't';
page.visibilityState = 'visible';
const ctx = {
  console: { warn: () => {}, error: () => {}, log: console.log },
  document: page, setInterval, clearInterval, setTimeout, Date, Error, TypeError, ReferenceError, String, Array, encodeURI,
  localStorage: { getItem: () => null, setItem: () => {}, removeItem: () => {} },
};
ctx.window = ctx.top = ctx;
ctx.addEventListener = () => {};
vm.createContext(ctx);
vm.runInContext(src, ctx);
// SceneManager.updateScene and isGameActive as rmmz_managers.js has them
vm.runInContext(`
  var updates = 0;
  var SceneManager = {
    _scene: { isStarted: function () { return true; }, update: function () { updates++; } },
    updateScene: function () {
      if (this._scene && this._scene.isStarted() && this.isGameActive()) this._scene.update();
    },
    isGameActive: function () {
      try { return window.top.document.hasFocus(); } catch (e) { return true; }
    },
    catchException: function () {}
  };
`, ctx);

for (let i = 0; i < 60; i++) ctx.SceneManager.updateScene();
assert.strictEqual(ctx.updates, 60, 'the first scene moves on while the page shows, picked or not');
page.visibilityState = 'hidden'; // the player went to another app
ctx.SceneManager.updateScene();
assert.strictEqual(ctx.updates, 60, 'the game waits while the app is left');
page.visibilityState = 'visible';
ctx.SceneManager.updateScene();
assert.strictEqual(ctx.updates, 61, 'and goes on when the player comes back');
console.log('focus simulation OK: updates=' + ctx.updates);
process.exit(0);
