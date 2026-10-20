// Copyright (c) 2026 agvn.io - MIT License (see LICENSE).
//
// PC check of the app's assets/agvn/html-compat.js: node scripts/agvn/html/test-html-compat.js
// It loads the file into a page stand-in, then the parts of RPG Maker MZ it changes: Bitmap, SceneManager and
// Sprite_Button with MZ's own checkBitmap (rmmz_sprites.js), which stops the game when img/system/ButtonSet is
// narrower than 11 cells ("ButtonSet image is too small"). No WebView, no game.

'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const COMPAT = path.join(__dirname, '..', '..', '..', 'app', 'src', 'main', 'assets', 'agvn', 'html-compat.js');

function page() {
    const listeners = {};
    const timers = [];
    const window = {
        addEventListener: (type, f) => (listeners[type] = listeners[type] || []).push(f),
        localStorage: { getItem: () => null, setItem() {}, removeItem() {} },
        location: { reload() {} },
        innerWidth: 800, innerHeight: 450, pageXOffset: 0, pageYOffset: 0,
    };
    window.window = window;
    window.document = { title: '', visibilityState: 'visible', getElementById: () => null };
    window.console = { log() {}, warn() {}, error() {} };
    window.setInterval = (f) => { timers.push(f); return timers.length; };
    window.clearInterval = () => {};
    window.setTimeout = (f) => f();
    window.Promise = Promise;
    const ctx = vm.createContext(window);
    vm.runInContext(fs.readFileSync(COMPAT, 'utf8'), ctx, { filename: 'html-compat.js' });
    return { ctx, tick: () => timers.forEach((f) => f()) };
}

// RPG Maker MZ, as much of it as the check needs: rmmz_core's Bitmap, rmmz_managers' SceneManager, rmmz_sprites'
// Sprite_Button. checkBitmap is MZ 1.x's, word for word.
const MZ = `
var Utils = { RPGMAKER_NAME: 'MZ' };
function Bitmap(width, height) { this.width = width; this.height = height; this.drawn = []; }
Bitmap.prototype.isReady = function () { return true; };
Bitmap.prototype.blt = function (source, sx, sy, sw, sh, dx, dy, dw, dh) { this.drawn.push([source, dw, dh]); };
var SceneManager = { stopped: null, started: false,
    run: function () { this.started = true; },
    catchException: function (e) { this.stopped = e.message; } };
function Sprite_Button() { this.bitmap = null; }
Sprite_Button.prototype.blockWidth = function () { return 48; };
Sprite_Button.prototype.blockHeight = function () { return 48; };
Sprite_Button.prototype.checkBitmap = function() {
    if (this.bitmap.isReady() && this.bitmap.width < this.blockWidth() * 11) {
        // Probably MV image is used
        throw new Error("ButtonSet image is too small");
    }
};
Sprite_Button.prototype.update = function () {
    try { this.checkBitmap(); } catch (e) { SceneManager.catchException(e); }
};
`;

let failures = 0;
function check(name, ok) {
    console.log((ok ? 'ok   ' : 'FAIL ') + name);
    if (!ok) failures++;
}

function button(ctx, w, h) {
    return vm.runInContext(`(function () { var b = new Sprite_Button(); b.bitmap = new Bitmap(${w}, ${h}); b.update(); return b; })()`, ctx);
}

{ // MZ's scripts load after html-compat.js, then main.js starts the game with SceneManager.run
    const p = page();
    vm.runInContext(MZ, p.ctx);
    p.tick();
    vm.runInContext('SceneManager.run(Scene_Boot = function () {})', p.ctx);
    const empty = button(p.ctx, 1, 1);
    check('the app\'s 1x1 empty picture: the game goes on', vm.runInContext('SceneManager.stopped', p.ctx) === null);
    check('...with the picture stretched to 528x96', empty.bitmap.width === 528 && empty.bitmap.height === 96
        && empty.bitmap.drawn.length === 1 && empty.bitmap.drawn[0][1] === 528 && empty.bitmap.drawn[0][2] === 96);
    const real = button(p.ctx, 528, 96);
    check('a real ButtonSet stays as it is', real.bitmap.width === 528 && real.bitmap.drawn.length === 0);
    check('an MV-sized one (264x48) is stretched too', button(p.ctx, 264, 48).bitmap.width === 528);
    check('a 0x0 picture does not stop the game either', button(p.ctx, 0, 0).bitmap.width === 0
        && vm.runInContext('SceneManager.stopped', p.ctx) === null);
    check('the game started', vm.runInContext('SceneManager.started', p.ctx) === true);
}

{ // a plugin that replaced the check before the game started keeps its own
    const p = page();
    vm.runInContext(MZ, p.ctx);
    p.tick();
    vm.runInContext('Sprite_Button.prototype.checkBitmap = function () { if (this.bitmap.width < 528) throw new Error("Nút hỏng"); };' +
        'SceneManager.run(function () {})', p.ctx);
    const b = button(p.ctx, 1, 1);
    check('a plugin\'s own check is kept, and its stop goes as before', vm.runInContext('SceneManager.stopped', p.ctx)
        === 'Nút hỏng' && b.bitmap.width === 1);
}

{ // the engine's check replaced only once its sprites are there
    const p = page();
    vm.runInContext(MZ.replace(/function Sprite_Button[\s\S]*$/, ''), p.ctx);
    p.tick();
    vm.runInContext(MZ.slice(MZ.indexOf('function Sprite_Button')), p.ctx);
    vm.runInContext('SceneManager.run = undefined', p.ctx); // a game that starts another way
    button(p.ctx, 1, 1);
    check('sprites loaded after SceneManager, no SceneManager.run: the first stop is caught, the game goes on',
        vm.runInContext('SceneManager.stopped', p.ctx) === null);
    check('...and from the next frame stretched', button(p.ctx, 1, 1).bitmap.width === 528);
}

{ // RPG Maker MV has no such check
    const p = page();
    vm.runInContext(MZ.replace("RPGMAKER_NAME: 'MZ'", "RPGMAKER_NAME: 'MV', isMobileDevice: function () { return true; }")
        .replace(/Sprite_Button\.prototype\.checkBitmap[\s\S]*?\n};\n/, ''), p.ctx);
    p.tick();
    vm.runInContext('SceneManager.run(function () {})', p.ctx);
    check('MV: nothing to replace, the game starts', vm.runInContext('SceneManager.started && !Sprite_Button.prototype.checkBitmap', p.ctx));
}

if (failures) process.exit(1);
console.log('all checks passed');
