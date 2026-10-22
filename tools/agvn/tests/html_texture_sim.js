/* A picture larger than the GPU takes, uploaded as RPG Maker's PIXI does, around assets/agvn/html-compat.js (12).
   Copyright (c) 2026 agvn.io - MIT License. Run: node tools/agvn/tests/html_texture_sim.js */
const fs = require('fs'), vm = require('vm'), assert = require('assert');
const src = fs.readFileSync(require('path').join(__dirname, '..', '..', '..', 'app', 'src', 'main', 'assets', 'agvn',
  'html-compat.js'), 'utf8');

const warned = [], drawn = [];
function Gl() {}
Gl.prototype.MAX_TEXTURE_SIZE = 0x0D33;
Gl.prototype.getParameter = function (p) { return p === 0x0D33 ? 8192 : 0; };
Gl.prototype.texImage2D = function () { this.calls.push(['image'].concat(Array.prototype.slice.call(arguments))); };
Gl.prototype.texSubImage2D = function () { this.calls.push(['sub'].concat(Array.prototype.slice.call(arguments))); };

const ctx = {
  console: { warn: (s) => warned.push(s), error: () => {}, log: () => {} },
  document: {
    title: 't',
    createElement: () => ({ width: 0, height: 0, getContext: () => ({ drawImage: (...a) => drawn.push(a) }) }),
  },
  // no engine here, so the wait for one is not run
  setInterval: () => 0, clearInterval, setTimeout, Date, Error, TypeError, ReferenceError, String, Array, encodeURI,
  Math,
  localStorage: { getItem: () => null, setItem: () => {}, removeItem: () => {} },
  addEventListener: () => {}, WebGLRenderingContext: Gl,
};
ctx.window = ctx;
vm.createContext(ctx);
vm.runInContext(src, ctx);

// PIXI's GLTexture.upload: texImage2D for a new size, texSubImage2D at 0,0 when the size is the same
const gl = new Gl();
gl.calls = [];
const tileset = { src: 'https://g.agvn.game/img/tilesets/Outside_B.png', naturalWidth: 768, naturalHeight: 12288,
  width: 768, height: 12288 };
gl.texImage2D(0x0DE1, 0, 0x1908, 0x1908, 0x1401, tileset);
const up = gl.calls[0][6];
assert.notStrictEqual(up, tileset, 'a picture taller than 8192 goes up as a copy');
assert.strictEqual(up.height, 8192);
assert.strictEqual(up.width, 512);
assert.deepStrictEqual(drawn[0].slice(1), [0, 0, 512, 8192]);
assert.strictEqual(warned.length, 1);
assert.ok(/Outside_B\.png 768x12288 larger than the GPU takes \(8192\), drawn at 512x8192/.test(warned[0]), warned[0]);

gl.texSubImage2D(0x0DE1, 0, 0, 0, 0x1908, 0x1401, tileset); // the same picture again: the same shrunk size
assert.strictEqual(gl.calls[1][7].height, 8192);
assert.strictEqual(warned.length, 1, 'named once');

const sheet = { width: 576, height: 384 }; // what fits goes up as it is
gl.texImage2D(0x0DE1, 0, 0x1908, 0x1908, 0x1401, sheet);
assert.strictEqual(gl.calls[2][6], sheet);
gl.texSubImage2D(0x0DE1, 0, 48, 0, 0x1908, 0x1401, tileset); // a part placed elsewhere: left to WebGL
assert.strictEqual(gl.calls[3][7], tileset);
const pixels = new Uint8Array(4); // the 9-argument form with its own size and pixels: untouched
gl.texImage2D(0x0DE1, 0, 0x1908, 1, 1, 0, 0x1908, 0x1401, pixels);
assert.strictEqual(gl.calls[4][9], pixels);
console.log('html textures: ok');
