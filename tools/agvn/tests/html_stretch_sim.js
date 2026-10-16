/* "Toàn màn hình cho mọi game" in an RPG Maker MV/MZ page, around assets/agvn/html-compat.js.
   Copyright (c) 2026 agvn.io - MIT License. Run: node tools/agvn/tests/html_stretch_sim.js */
const fs = require('fs'), vm = require('vm'), assert = require('assert');
const src = fs.readFileSync(require('path').join(__dirname, '..', '..', '..', 'app', 'src', 'main', 'assets', 'agvn',
  'html-compat.js'), 'utf8');

function page(stretch) {
  const listeners = {};
  const ctx = {
    console: { warn: () => {}, error: () => {}, log: () => {} }, document: { title: 't' },
    setInterval, clearInterval, setTimeout, Date, Error, TypeError, ReferenceError, String, Array, encodeURI, Math,
    localStorage: { getItem: () => null, setItem: () => {}, removeItem: () => {} },
    innerWidth: 2400, innerHeight: 1080, pageXOffset: 0, pageYOffset: 0,
  };
  ctx.window = ctx;
  ctx.addEventListener = (n, f) => { listeners[n] = f; };
  vm.createContext(ctx);
  if (stretch) vm.runInContext('window.__agvnStretch = true;', ctx);
  vm.runInContext(src, ctx);
  // the engine's own centring (MV and MZ alike): the game's size times one scale, in the middle of the page
  const canvas = { width: 816, height: 624, style: {}, clientWidth: 0, clientHeight: 0, left: 0, top: 0,
    getBoundingClientRect() { return { left: this.left, top: this.top }; } };
  ctx.Graphics = {
    _width: 816, _height: 624, _realScale: 1080 / 624, _canvas: canvas,
    _centerElement(e) {
      const w = e.width * this._realScale, h = e.height * this._realScale;
      e.style.margin = 'auto'; e.style.width = w + 'px'; e.style.height = h + 'px';
      e.clientWidth = w; e.clientHeight = h; e.left = (2400 - w) / 2; e.top = (1080 - h) / 2;
    },
    _updateAllElements() { this._centerElement(this._canvas); },
    pageToCanvasX(x) { return Math.round((x - this._canvas.left) / this._realScale); },
    pageToCanvasY(y) { return Math.round((y - this._canvas.top) / this._realScale); },
  };
  ctx.Utils = { RPGMAKER_NAME: 'MV' };
  ctx.SceneManager = { catchException() {}, requestUpdate() {} };
  listeners.load();
  return ctx;
}

// off: the engine's own centred picture, with bars at the sides
const kept = page(false).Graphics;
kept._updateAllElements();
assert.notStrictEqual(kept._canvas.style.width, '2400px');

// on: the canvas covers the whole page, and a touch maps back on each axis
const ctx = page(true), G = ctx.Graphics;
assert.strictEqual(G._canvas.style.width, '2400px');
assert.strictEqual(G._canvas.style.height, '1080px');
assert.strictEqual(G._canvas.style.margin, '0');
G._canvas.clientWidth = 2400; G._canvas.clientHeight = 1080; G._canvas.left = 0; G._canvas.top = 0;
assert.strictEqual(G.pageToCanvasX(2400), 816);
assert.strictEqual(G.pageToCanvasY(1080), 624);
assert.strictEqual(G.pageToCanvasX(1200), 408);
assert.strictEqual(G.pageToCanvasY(540), 312);
console.log('html stretch: ok');
