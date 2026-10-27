/* Checks assets/agvn/html-keyboard.js: a text field the player can see that takes the focus asks for the Android
 * keyboard (also one a script focused, which WebView brings none up for), and the keyboard goes when no field has it;
 * a hidden field an engine keeps focused, a button and a read-only field ask nothing (27/10/2026).
 * Copyright (c) 2026 agvn.io - MIT License. */
const fs = require('fs'), vm = require('vm'), assert = require('assert'), path = require('path');
const dir = path.join(__dirname, '..', '..', '..', 'app', 'src', 'main', 'assets', 'agvn');
const calls = [], listeners = {};
const box = (x, y, w, h) => () => ({ left: x, top: y, right: x + w, bottom: y + h, width: w, height: h });
const el = (tag, type, rect, extra) => Object.assign({ tagName: tag, getAttribute: n => (n === 'type' ? type : null),
  getBoundingClientRect: rect || box(100, 100, 200, 30), style: {} }, extra || {});
const ctx = { innerWidth: 1280, innerHeight: 720, setTimeout: f => f(),
  getComputedStyle: e => ({ visibility: e.style.visibility || 'visible', opacity: e.style.opacity || '1' }),
  AgvnPageKeyboard: { field: on => calls.push(on) },
  document: { activeElement: null, addEventListener: (type, f) => { listeners[type] = f; } } };
ctx.window = ctx;
vm.createContext(ctx);
const js = fs.readFileSync(path.join(dir, 'html-keyboard.js'), 'utf8');
vm.runInContext(js, ctx);
vm.runInContext(js, ctx); // run again on the same page: once only
const focus = e => { ctx.document.activeElement = e; listeners.focusin({ target: e }); };
const blur = (e, next) => { ctx.document.activeElement = next || null; listeners.focusout({ target: e }); };

const name = el('INPUT', 'text');
focus(name);
assert.deepStrictEqual(calls, [true], 'a name box a plugin focused');
const other = el('TEXTAREA', null);
blur(name, other); focus(other);
assert.deepStrictEqual(calls, [true, true], 'to another field: the keyboard stays');
blur(other, null);
assert.deepStrictEqual(calls, [true, true, false], 'no field any more: it goes');

calls.length = 0;
for (const quiet of [el('INPUT', 'text', box(0, 0, 1, 1)), el('INPUT', 'text', box(-500, 10, 200, 30)),
  el('INPUT', 'text', null, { style: { opacity: '0' } }), el('INPUT', 'button'), el('INPUT', 'range'),
  el('INPUT', 'text', null, { readOnly: true }), el('DIV', null)]) {
  focus(quiet); blur(quiet);
}
assert.deepStrictEqual(calls, [], 'a hidden field, a button, a slider, a read-only field, a div: nothing');
focus(el('DIV', null, null, { isContentEditable: true }));
assert.deepStrictEqual(calls, [true], 'contenteditable');
focus(el('INPUT', null));
assert.deepStrictEqual(calls, [true, true], 'an input with no type is a text field');

// a field focused before the script ran (autofocus)
calls.length = 0;
const ctx2 = Object.assign({}, ctx, { document: { activeElement: el('INPUT', 'password'), addEventListener: () => {} } });
delete ctx2.__agvnKeyboard; // a new page
ctx2.window = ctx2;
vm.createContext(ctx2);
vm.runInContext(js, ctx2);
assert.deepStrictEqual(calls, [true], 'autofocus');
console.log('html-keyboard ok');
