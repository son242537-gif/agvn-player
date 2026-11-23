/* AGVN Player - the frame pace of an HTML game. Copyright (c) 2026 agvn.io - MIT License.
 * A page draws when requestAnimationFrame calls it back, once per refresh of the screen: 120 times a second on a
 * 120 Hz screen, where the app asked for 60 Hz and the phone kept 120 (HyperOS, BLACKBOOK80, RPG Maker MZ,
 * 24/11/2026). The page and WebView then worked 120 times a second for a game made for 60, and the phone got hot as
 * the game stood still. With window.__agvnMaxFps set first (60, unless the player chose "Tần số quét cao"), the
 * page's callbacks come at most that many times a second, on the screen's own refreshes, without catching up after a
 * slow one; on a 60 Hz screen every refresh goes as before. window.__agvnFrame.frames counts the frames the page was
 * called for, for the app's HUD and slow-game check, which no longer keep a loop of their own going.
 */
(function () {
  'use strict';
  if (window.__agvnFrame || typeof window.requestAnimationFrame !== 'function') return;
  var nativeRequest = window.requestAnimationFrame.bind(window);
  var nativeCancel = typeof window.cancelAnimationFrame === 'function' ? window.cancelAnimationFrame.bind(window) : null;
  var max = +window.__agvnMaxFps || 0, interval = max > 0 ? 1000 / max : 0;
  var SLACK = 2; // ms: a refresh this early still counts, as refreshes come a little early or late
  var state = window.__agvnFrame = { frames: 0, max: max };
  var waiting = new Map(), nextId = 0x40000000; // apart from the browser's own ids, kept by a script from before this
  var pending = 0, due = -1, current = null;

  function request() {
    if (!pending) pending = nativeRequest(tick);
  }

  function tick(time) {
    pending = 0;
    if (!waiting.size) return;
    if (interval > 0 && due >= 0 && time < due - SLACK) { // too soon: this refresh goes by
      request();
      return;
    }
    due = due < 0 || time - due > interval ? time + interval : due + interval;
    var callbacks = current = waiting;
    waiting = new Map();
    state.frames++;
    callbacks.forEach(function (callback) {
      try {
        callback.call(window, time);
      } catch (e) { // as the browser does: told, and the other callbacks still run
        if (typeof window.reportError === 'function') window.reportError(e);
        else setTimeout(function () { throw e; });
      }
    });
    current = null;
  }

  window.requestAnimationFrame = function (callback) {
    if (typeof callback !== 'function') return nativeRequest(callback); // the browser's own error
    var id = nextId++;
    waiting.set(id, callback);
    request();
    return id;
  };

  window.cancelAnimationFrame = function (id) {
    if (waiting.has(id)) waiting.delete(id);
    else if (current && current.has(id)) current.delete(id); // one of this frame's, not called yet
    else if (nativeCancel) nativeCancel(id);
  };

  if (window.webkitRequestAnimationFrame) window.webkitRequestAnimationFrame = window.requestAnimationFrame;
  if (window.webkitCancelAnimationFrame) window.webkitCancelAnimationFrame = window.cancelAnimationFrame;
})();
