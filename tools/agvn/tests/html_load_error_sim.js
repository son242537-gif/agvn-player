/* Simulates RPG Maker starting around assets/agvn/html-compat.js when a plugin fails in its first lines: MZ's main.js
 * shows the error and never starts, and the app learns which file and line. Copyright (c) 2026 agvn.io - MIT License. */
const fs = require('fs'), vm = require('vm'), assert = require('assert');
const src = fs.readFileSync(require('path').join(__dirname, '..', '..', '..', 'app', 'src', 'main', 'assets', 'agvn', 'html-compat.js'), 'utf8');

function start(engine, plugin) {
  const listeners = {}, elements = {};
  const ctx = {
    console: { warn: () => {}, error: () => {}, log: () => {} },
    document: { title: 't', getElementById: id => elements[id] || null },
    setInterval, clearInterval, setTimeout, Date, Error, TypeError, ReferenceError, String, Array, Promise, encodeURI,
    localStorage: { getItem: () => null, setItem: () => {}, removeItem: () => {} },
  };
  ctx.window = ctx;
  ctx.addEventListener = (n, f) => { (listeners[n] = listeners[n] || []).push(f); };
  const fire = (n, ev) => (listeners[n] || []).forEach(f => f(ev));
  vm.createContext(ctx);
  vm.runInContext(src, ctx);
  ctx.Utils = { RPGMAKER_NAME: engine };
  ctx.SceneManager = { _scene: null, catchException() {} };
  let mainError = null; // MZ's main.js keeps the first error and shows it at load instead of starting the game
  if (engine === 'MZ') ctx.addEventListener('error', ev => { mainError = mainError || ev.error; });
  try {
    vm.runInContext(plugin, ctx);
  } catch (error) { // what WebView does with an error in a script's first lines: an error event on the window
    fire('error', { error, message: 'Uncaught ' + error, filename: 'https://g.agvn.game/js/plugins/Thu.js', lineno: 1 });
  }
  fire('load', {});
  if (mainError) elements.errorPrinter = { innerText: mainError.name + '\n' + mainError.message };
  else ctx.SceneManager._scene = {}; // the first scene starts
  return ctx;
}

const broken = start('MZ', 'khongCo.x = 1;');
const mv = start('MV', 'khongCo.x = 1;');
const fine = start('MZ', 'var a = 1;');
const nwjs = start('MZ', "var base = require('path').dirname(process.mainModule.filename);"); // stopped before 0.1.26
setTimeout(() => {
  assert.strictEqual(broken.__agvnFatal, 'ReferenceError: khongCo is not defined (Thu.js:1)');
  assert.strictEqual(mv.__agvnFatal, undefined, 'MV goes on after it: no stop to tell');
  assert.strictEqual(fine.__agvnFatal, undefined);
  assert.strictEqual(nwjs.__agvnFatal, undefined, 'a plugin that reads process as it loads starts the game');
  console.log('load error simulation OK: ' + broken.__agvnFatal);
  process.exit(0);
}, 20);
