/* AGVN Player - the Android keyboard for a text field of a page game ("Chạy nhẹ"). Copyright (c) 2026 agvn.io - MIT
 * License. Run on every page once it has loaded (AgvnPageKeyboard). WebView brings the keyboard up only for a field
 * the player taps: a field a script focuses (a name box a plugin opens, a field RPG Maker's touch handling hands the
 * focus to) got none, and the player could not type. Now a text field that takes the focus asks for the keyboard, and
 * the keyboard goes when no text field has it any more. Only a field the player can see: an engine that keeps a hidden
 * field focused to read keys (1x1, transparent, off the screen) gets no keyboard over its game.
 */
(function () {
    'use strict';
    if (window.__agvnKeyboard) return;
    window.__agvnKeyboard = true;
    var TYPES = ['text', 'password', 'search', 'email', 'number', 'tel', 'url', ''];

    function shown(el) {
        if (typeof el.getBoundingClientRect !== 'function') return true;
        var r = el.getBoundingClientRect();
        if (r.width < 4 || r.height < 4) return false;
        if (r.right <= 0 || r.bottom <= 0 || r.left >= window.innerWidth || r.top >= window.innerHeight) return false;
        var style = typeof getComputedStyle === 'function' ? getComputedStyle(el) : null;
        return !style || (style.visibility !== 'hidden' && !(parseFloat(style.opacity) < 0.05));
    }

    function isText(el) {
        if (!el || el.disabled || el.readOnly || !shown(el)) return false;
        if (el.isContentEditable) return true;
        var tag = String(el.tagName || '').toUpperCase();
        if (tag === 'TEXTAREA') return true;
        return tag === 'INPUT' && TYPES.indexOf(String(el.getAttribute && el.getAttribute('type') || '').toLowerCase()) >= 0;
    }

    function tell(focused) {
        var k = window.AgvnPageKeyboard;
        if (k && typeof k.field === 'function') k.field(focused);
    }

    document.addEventListener('focusin', function (e) { if (isText(e.target)) tell(true); }, true);
    document.addEventListener('focusout', function (e) {
        if (!isText(e.target)) return;
        setTimeout(function () { if (!isText(document.activeElement)) tell(false); }, 0); // focus moving to another field
    }, true);
    if (isText(document.activeElement)) tell(true); // a field the page focused as it loaded
})();
