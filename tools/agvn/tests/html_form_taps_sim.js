/* Simulates RPG Maker MV/MZ's touch handling around assets/agvn/html-compat.js. Copyright (c) 2026 agvn.io - MIT License.
 * A plugin's name input (<input>, "Xác nhận") laid over the game took no tap on a phone: the engine's touchstart handler,
 * on the whole page, calls preventDefault for every touch inside the game, and the browser then gives the field no
 * focus and the button no click (20/10/2026). Also path.parse and process.execPath, which a game picked its language by.
 * Run: node tools/agvn/tests/html_form_taps_sim.js */
const fs = require('fs'), vm = require('vm'), assert = require('assert');
const src = fs.readFileSync(require('path').join(__dirname, '..', '..', '..', 'app', 'src', 'main', 'assets', 'agvn', 'html-compat.js'), 'utf8');

function page(exe) {
  const timers = [], onDocument = {};
  const ctx = {
    console: { warn: () => {}, error: () => {}, log: () => {} }, setTimeout: f => f(), clearInterval: () => {},
    setInterval: f => { timers.push(f); return timers.length; }, Promise, Date, Error, TypeError, ReferenceError, String, Array,
    localStorage: { getItem: () => null, setItem() {}, removeItem() {} },
    document: { title: 't', visibilityState: 'visible', getElementById: () => null, nodeType: 9,
      addEventListener: (type, f) => (onDocument[type] = onDocument[type] || []).push(f) },
    navigator: {},
  };
  ctx.window = ctx;
  ctx.addEventListener = () => {};
  if (exe !== undefined) ctx.__agvnExe = exe;
  vm.createContext(ctx);
  vm.runInContext(src, ctx);
  return { ctx, tick: () => timers.forEach(f => f()), onDocument };
}

// elements as the browser has them: an upper-case tag name, the parent up to <html>, then the document
function element(ctx, tag, parent, editable) {
  return { tagName: tag, nodeType: 1, parentNode: parent || ctx.document, isContentEditable: !!editable };
}

// the browser's touchstart at (x, y) on target, through every touchstart handler the page put on document
function tap(p, target, x, y) {
  const touch = { pageX: x, pageY: y, target };
  const ev = { type: 'touchstart', target, changedTouches: [touch], touches: [touch], defaultPrevented: false,
    preventDefault() { this.defaultPrevented = true; } };
  (p.onDocument.touchstart || []).forEach(f => f(ev));
  return ev;
}

// RPG Maker MV 1.6 (rpg_core.js, rpg_managers.js): TouchInput binds its handlers as SceneManager.run starts the game.
// _setupEventHandlers' touch lines and _onTouchStart are the engine's, word for word.
const MV = `
var Utils = { RPGMAKER_NAME: 'MV', isMobileDevice: function () { return true; }, isSupportPassiveEvent: function () { return true; } };
var Graphics = { pageToCanvasX: function (x) { return x; }, pageToCanvasY: function (y) { return y; },
    isInsideCanvas: function (x, y) { return x >= 0 && x < 816 && y >= 0 && y < 624; } };
function TouchInput() {}
TouchInput.initialize = function () { this.triggered = 0; this.cancelled = 0; this._setupEventHandlers(); };
TouchInput._onTrigger = function () { this.triggered++; };
TouchInput._onCancel = function () { this.cancelled++; };
TouchInput._setupEventHandlers = function() {
    var isSupportPassive = Utils.isSupportPassiveEvent();
    document.addEventListener('touchstart', this._onTouchStart.bind(this), isSupportPassive ? {passive: false} : false);
};
TouchInput._onTouchStart = function(event) {
    for (var i = 0; i < event.changedTouches.length; i++) {
        var touch = event.changedTouches[i];
        var x = Graphics.pageToCanvasX(touch.pageX);
        var y = Graphics.pageToCanvasY(touch.pageY);
        if (Graphics.isInsideCanvas(x, y)) {
            this._screenPressed = true;
            this._pressedTime = 0;
            if (event.touches.length >= 2) {
                this._onCancel(x, y);
            } else {
                this._onTrigger(x, y);
            }
            event.preventDefault();
        }
    }
    if (window.cordova || window.navigator.standalone) {
        event.preventDefault();
    }
};
var SceneManager = { run: function (sceneClass) { this.initialize(); }, initialize: function () { this.initInput(); },
    initInput: function () { TouchInput.initialize(); }, catchException: function () {} };
`;

// RPG Maker MZ 1.x (rmmz_core.js): the same, written anew
const MZ = MV.replace("RPGMAKER_NAME: 'MV'", "RPGMAKER_NAME: 'MZ'").replace(/TouchInput\._setupEventHandlers[\s\S]*?\n};\nTouchInput\._onTouchStart[\s\S]*?\n};\n/, `
TouchInput._setupEventHandlers = function() {
    const pf = { passive: false };
    document.addEventListener("touchstart", this._onTouchStart.bind(this), pf);
};
TouchInput._onTouchStart = function(event) {
    for (const touch of event.changedTouches) {
        const x = Graphics.pageToCanvasX(touch.pageX);
        const y = Graphics.pageToCanvasY(touch.pageY);
        if (Graphics.isInsideCanvas(x, y)) {
            this._screenPressed = true;
            this._pressedTime = 0;
            if (event.touches.length >= 2) {
                this._onCancel(x, y);
            } else {
                this._onTrigger(x, y);
            }
            event.preventDefault();
        }
    }
    if (window.cordova || window.navigator.standalone) {
        event.preventDefault();
    }
};
`);
assert.notStrictEqual(MZ, MV);

for (const [name, engine] of [['MV', MV], ['MZ', MZ]]) {
  const p = page();
  vm.runInContext(engine, p.ctx); // the engine's scripts after html-compat.js, then main.js starts the game
  p.tick();
  vm.runInContext('SceneManager.run(function Scene_Boot() {})', p.ctx);
  const T = p.ctx.TouchInput, html = element(p.ctx, 'HTML'), body = element(p.ctx, 'BODY', html);
  const canvas = element(p.ctx, 'CANVAS', body), form = element(p.ctx, 'FORM', body);
  const field = element(p.ctx, 'INPUT', form), ok = element(p.ctx, 'BUTTON', form);
  assert.strictEqual(tap(p, field, 300, 200).defaultPrevented, false, name + ': a tap on the name field focuses it');
  assert.strictEqual(tap(p, element(p.ctx, 'SPAN', ok), 340, 200).defaultPrevented, false, name + ': the text in "Xác nhận" clicks it');
  assert.strictEqual(tap(p, element(p.ctx, 'DIV', body, true), 300, 300).defaultPrevented, false, name + ': an editable box');
  assert.strictEqual(tap(p, element(p.ctx, 'textarea', body), 300, 300).defaultPrevented, false, name + ': a lower-case tag');
  assert.strictEqual(T.triggered, 0, name + ': the game takes none of them');
  assert.strictEqual(tap(p, canvas, 300, 200).defaultPrevented, true, name + ': a tap on the game is the engine\'s');
  assert.strictEqual(tap(p, element(p.ctx, 'DIV', body), 300, 200).defaultPrevented, true, name + ': a plugin\'s picture over the game too');
  assert.strictEqual(T.triggered, 2, name + ': ...as before');
  assert.strictEqual(tap(p, canvas, 900, 200).defaultPrevented, false, name + ': beside the game, as before');
}

{ // a plugin's own touch handler, set as the plugins load (after html-compat.js, before the game starts), is wrapped too
  const p = page();
  vm.runInContext(MV, p.ctx);
  p.tick();
  vm.runInContext(`var seen = 0, own = TouchInput._onTouchStart;
    TouchInput._onTouchStart = function (event) { seen++; event.preventDefault(); own.call(this, event); };
    SceneManager.run(function () {})`, p.ctx);
  const body = element(p.ctx, 'BODY', element(p.ctx, 'HTML'));
  assert.strictEqual(tap(p, element(p.ctx, 'INPUT', body), 300, 200).defaultPrevented, false, 'plugin: the field is the browser\'s');
  assert.strictEqual(tap(p, element(p.ctx, 'CANVAS', body), 300, 200).defaultPrevented, true);
  assert.strictEqual(vm.runInContext('seen', p.ctx), 1, 'plugin: its handler sees every touch on the game');
  assert.strictEqual(p.ctx.TouchInput.triggered, 1);
  assert.strictEqual(p.onDocument.touchstart.length, 1, 'one handler, bound once');
}

{ // process.execPath is the game's exe by its own name, and path.parse reads it as Node does
  const p = page('Game_EN.exe');
  const parsed = vm.runInContext("require('path').parse(process.execPath)", p.ctx);
  assert.strictEqual(p.ctx.process.execPath, '/Game_EN.exe');
  assert.deepStrictEqual(JSON.parse(JSON.stringify(parsed)), { root: '/', dir: '/', base: 'Game_EN.exe', ext: '.exe', name: 'Game_EN' });
  const path = p.ctx.require('path');
  assert.strictEqual(path.format(parsed), '/Game_EN.exe');
  assert.strictEqual(path.format({ name: 'file1', ext: '.rpgsave' }), 'file1.rpgsave');
  assert.strictEqual(path.format({ dir: '/save', base: 'file1.rpgsave' }), '/save/file1.rpgsave');
  assert.strictEqual(path.parse('/www/data/System.json').dir, '/www/data');
  assert.strictEqual(path.parse('C:\\Games\\X\\.hidden').name, '.hidden');
  assert.strictEqual(page('C:\\Games\\X\\ゲーム.exe').ctx.process.execPath, '/ゲーム.exe', 'a full Windows path: its name');
  assert.strictEqual(page().ctx.process.execPath, '/Game.exe', 'no exe told: Game.exe, as before');
  assert.strictEqual(page('').ctx.process.execPath, '/Game.exe');
}

console.log('html_form_taps_sim: all checks passed');
