/* AGVN Player - compatibility layer for RPG Maker MV/MZ games run without NW.js. Copyright (c) 2026 agvn.io.vn - MIT License.
 * Loaded before the game's own scripts. It fixes what breaks when a PC game runs in a phone browser:
 *  1. Plugins that call require('fs'/'path'/'nw.gui') get small stand-ins (files are read from the game folder,
 *     written files go to localStorage). `process` stays undefined so the engine keeps using browser saves.
 *  2. Small script errors ("Cannot read property 'opacity' of undefined", "...'length'...", "x is not a function")
 *     are logged and skipped instead of stopping the game with an error screen. If they keep coming (a truly broken
 *     scene), the normal error screen is shown so the player is not stuck on a frozen picture.
 *  3. A missing sound file is skipped instead of stopping the game ("Failed to load: audio/...").
 */
(function () {
    'use strict';
    var FS_PREFIX = 'agvn_fs:';

    function norm(p) {
        p = String(p || '').replace(/\\/g, '/');
        var out = [];
        p.split('/').forEach(function (s) {
            if (s === '..') out.pop(); else if (s && s !== '.') out.push(s);
        });
        return '/' + out.join('/');
    }

    function httpGet(p, method) {
        try {
            var xhr = new XMLHttpRequest();
            xhr.open(method || 'GET', encodeURI(norm(p)), false);
            xhr.overrideMimeType('text/plain; charset=utf-8');
            xhr.send();
            return xhr.status === 200 ? xhr : null;
        } catch (e) {
            return null;
        }
    }

    function stored(p) {
        try { return localStorage.getItem(FS_PREFIX + norm(p)); } catch (e) { return null; }
    }

    var path = {
        sep: '/',
        delimiter: ':',
        normalize: norm,
        join: function () { return norm(Array.prototype.slice.call(arguments).join('/')); },
        resolve: function () { return norm(Array.prototype.slice.call(arguments).join('/')); },
        dirname: function (p) { p = norm(p); return p.substring(0, p.lastIndexOf('/')) || '/'; },
        basename: function (p, ext) {
            var b = norm(p).split('/').pop();
            return ext && b.slice(-ext.length) === ext ? b.slice(0, -ext.length) : b;
        },
        extname: function (p) { var b = norm(p).split('/').pop(), i = b.lastIndexOf('.'); return i > 0 ? b.slice(i) : ''; },
        relative: function (from, to) { return norm(to).replace(norm(from) + '/', ''); },
        isAbsolute: function (p) { return String(p).charAt(0) === '/'; }
    };

    var fs = {
        existsSync: function (p) { return stored(p) !== null || httpGet(p, 'HEAD') !== null; },
        readFileSync: function (p) {
            var s = stored(p);
            if (s !== null) return s;
            var xhr = httpGet(p);
            if (!xhr) { var e = new Error('ENOENT: ' + p); e.code = 'ENOENT'; throw e; }
            return xhr.responseText;
        },
        writeFileSync: function (p, data) {
            try { localStorage.setItem(FS_PREFIX + norm(p), String(data)); } catch (e) { /* storage full */ }
        },
        unlinkSync: function (p) { try { localStorage.removeItem(FS_PREFIX + norm(p)); } catch (e) { /* ignore */ } },
        mkdirSync: function () {},
        readdirSync: function () { return []; },
        statSync: function (p) {
            if (!fs.existsSync(p)) { var e = new Error('ENOENT: ' + p); e.code = 'ENOENT'; throw e; }
            return { isFile: function () { return true; }, isDirectory: function () { return false; }, size: 0, mtime: new Date() };
        }
    };
    ['readFile', 'writeFile', 'unlink', 'mkdir', 'readdir', 'stat'].forEach(function (name) {
        fs[name] = function () {
            var args = Array.prototype.slice.call(arguments), cb = args.pop();
            try { var r = fs[name + 'Sync'].apply(fs, args); if (typeof cb === 'function') cb(null, r); } catch (e) { if (typeof cb === 'function') cb(e); }
        };
    });
    fs.exists = function (p, cb) { cb(fs.existsSync(p)); };

    var noop = function () {};
    var nwWindow = { on: noop, close: noop, focus: noop, maximize: noop, enterFullscreen: noop, leaveFullscreen: noop,
        toggleFullscreen: noop, showDevTools: noop, closeDevTools: noop, resizeTo: noop, moveTo: noop, setPosition: noop,
        isFullscreen: true, title: document.title };
    var gui = { Window: { get: function () { return nwWindow; } }, App: { quit: noop, argv: [], dataPath: '/' },
        Shell: { openExternal: noop, openItem: noop }, Menu: function () { return { append: noop, createMacBuiltin: noop }; },
        MenuItem: function () { return {}; } };
    var modules = { fs: fs, path: path, 'nw.gui': gui, os: { platform: function () { return 'android'; }, EOL: '\n' } };

    if (typeof window.require !== 'function') {
        window.require = function (name) {
            if (modules[name]) return modules[name];
            console.warn('[AGVN] require("' + name + '") is not available on the phone');
            return {};
        };
    }

    // --- 2 and 3: keep the game running through small errors ---
    var MINOR = /opacity|length|undefined|null|not a function|not an object|Cannot read|Cannot set/i;
    var recent = [];

    function isMinor(e) {
        if (!(e instanceof TypeError) && !(e instanceof ReferenceError)) return false;
        var now = Date.now();
        recent = recent.filter(function (t) { return now - t < 5000; });
        recent.push(now);
        return MINOR.test(String(e.message)) && recent.length <= 60;
    }

    function patch() {
        var SM = window.SceneManager, AM = window.AudioManager;
        if (!SM || SM.__agvnPatched) return !!SM;
        SM.__agvnPatched = true;
        var isMz = window.Utils && Utils.RPGMAKER_NAME === 'MZ';
        var original = SM.catchException;
        SM.catchException = function (e) {
            if (isMinor(e)) {
                console.warn('[AGVN] skipped game error: ' + e.message);
                if (!isMz && typeof this.requestUpdate === 'function') this.requestUpdate();
                return;
            }
            return original.apply(this, arguments);
        };
        if (AM) {
            if (typeof AM.checkErrors === 'function') AM.checkErrors = function () {};
            if (typeof AM.checkWebAudioError === 'function') AM.checkWebAudioError = function () {};
        }
        return true;
    }

    var tries = 0;
    var timer = setInterval(function () { if (patch() || ++tries > 2000) clearInterval(timer); }, 30);
    window.addEventListener('load', patch, true);
})();
