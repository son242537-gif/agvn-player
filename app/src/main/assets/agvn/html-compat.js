/* AGVN Player - compatibility layer for RPG Maker MV/MZ games run without NW.js. Copyright (c) 2026 agvn.io - MIT License.
 * Loaded before the game's own scripts. It fixes what breaks when a PC game runs in a phone browser:
 *  1. Plugins that call require('fs'/'path'/'nw.gui') get small stand-ins (files are read from the game folder,
 *     written files go to localStorage). `process` stays undefined so the engine keeps its browser save functions,
 *     which 6 sends to real files.
 *  2. Small script errors ("Cannot read property 'opacity' of undefined", "...'length'...", "x is not a function")
 *     are logged and skipped instead of stopping the game with an error screen. If they keep coming (a truly broken
 *     scene), the normal error screen is shown so the player is not stuck on a frozen picture.
 *  3. A missing sound file is skipped instead of stopping the game ("Failed to load: audio/...").
 *  4. RPG Maker MV is told it runs on a PC, as JoiPlay does: on a phone it asks for .m4a sound and .mp4 video, which
 *     PC games do not ship (every sound was missing, silently because of 3), and draws with the slow canvas renderer.
 *  5. An error that stops the game (its error screen, or a missing picture or data file) is kept in
 *     window.__agvnFatal, so the app can say what happened when the player leaves ("Tự sửa lỗi").
 *  6. Saves are files in the game's save folder, as on a PC (MV save/file1.rpgsave, MZ save/file1.rmmzsave), through
 *     the app (window.AgvnSaves): "Nhập save" / "Xuất save" and the Windows version reach them. Saves the browser kept
 *     before are carried over while the folder holds none.
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

    // --- 4: RPG Maker MV as on a PC (.ogg sound, .webm video, WebGL), still filling the screen and going quiet when
    // the player leaves the app as it did on the phone: MV does both only on NW.js or a phone, and this is neither.
    // Engine functions only: one a plugin has already replaced does not name isMobileDevice and is kept.
    function desktopMv() {
        if (!window.Utils || typeof Utils.isMobileDevice !== 'function') return;
        var asOnPhone = function () { return true; };
        [[window.Graphics, '_defaultStretchMode'], [window.WebAudio, '_shouldMuteOnHide'],
            [window.Html5Audio, '_shouldMuteOnHide']].forEach(function (f) {
            if (f[0] && /isMobileDevice/.test(String(f[0][f[1]]))) f[0][f[1]] = asOnPhone;
        });
        Utils.isMobileDevice = function () { return false; };
        Utils.isAndroidChrome = function () { return false; };
    }

    // --- 6: saves as files, as on a PC. The engine's browser storage functions are replaced (a plugin that replaces
    // them later keeps its own). While the folder holds no save, the browser's are read as before and written out.
    function filesForSaves(SM) {
        var saves = window.AgvnSaves;
        if (!saves || !SM || SM.__agvnSaves) return;
        SM.__agvnSaves = true;
        if (!saves.writable()) return; // a folder the app cannot write to: saves stay in the browser, as before
        var fromBrowser = !saves.any();
        if (typeof SM.saveToWebStorage === 'function') mvSaves(SM, saves, fromBrowser);
        else if (typeof SM.saveToForage === 'function') mzSaves(SM, saves, fromBrowser);
    }

    function mvSaves(SM, saves, fromBrowser) {
        var name = function (id) { return id < 0 ? 'config.rpgsave' : id === 0 ? 'global.rpgsave' : 'file' + id + '.rpgsave'; };
        var load = SM.loadFromWebStorage, exists = SM.webStorageExists, remove = SM.removeWebStorage, failed = null;
        SM.saveToWebStorage = function (id, json) {
            failed = saves.write(name(id), LZString.compressToBase64(json)) ? null : id;
            if (failed !== null) throw new Error('Cannot write save/' + name(id));
        };
        SM.loadFromWebStorage = function (id) {
            var data = saves.read(name(id));
            if (data !== null || !fromBrowser) return LZString.decompressFromBase64(data);
            var json = load.call(this, id);
            if (json) saves.write(name(id), LZString.compressToBase64(json));
            return json;
        };
        SM.webStorageExists = function (id) { return saves.exists(name(id)) || fromBrowser && exists.call(this, id); };
        SM.removeWebStorage = function (id) {
            if (id === failed) { failed = null; return; } // MV removes a save it failed to write: the last one stays
            saves.remove(name(id));
            if (fromBrowser) remove.call(this, id);
        };
        if (!fromBrowser) return;
        try {
            for (var i = 0; i < localStorage.length; i++) {
                var key = localStorage.key(i) || '', m = /^RPG (Config|Global|File(\d+))$/.exec(key);
                var id = !m ? null : m[1] === 'Config' ? -1 : m[1] === 'Global' ? 0 : Number(m[2]);
                if (id !== null && !saves.exists(name(id))) saves.write(name(id), localStorage.getItem(key));
            }
        } catch (e) { /* no browser storage: nothing to carry over */ }
    }

    function mzSaves(SM, saves, fromBrowser) {
        var name = function (saveName) { return saveName + '.rmmzsave'; };
        var save = SM.saveToForage, load = SM.loadFromForage, exists = SM.forageExists, remove = SM.removeForage;
        var carried = null;
        SM.saveToForage = function (saveName, zip) {
            if (typeof zip !== 'string') return save.apply(this, arguments); // not the text a PC writes: kept as before
            return saves.write(name(saveName), zip) ? Promise.resolve() : Promise.reject(new Error('Cannot write save/' + name(saveName)));
        };
        SM.loadFromForage = function (saveName) {
            var self = this;
            return carryOver(self).then(function () {
                var zip = saves.read(name(saveName));
                if (zip !== null || !fromBrowser) return zip;
                return load.call(self, saveName).then(function (z) {
                    if (typeof z === 'string' && z) saves.write(name(saveName), z);
                    return z;
                });
            });
        };
        SM.forageExists = function (saveName) { return saves.exists(name(saveName)) || fromBrowser && exists.call(this, saveName); };
        SM.removeForage = function (saveName) {
            saves.remove(name(saveName));
            return fromBrowser ? remove.call(this, saveName) : Promise.resolve();
        };

        // every save the browser kept, as files, at the first load: the keys hold the game's id, known by then
        function carryOver(self) {
            if (carried) return carried;
            carried = Promise.resolve();
            if (!fromBrowser || !window.localforage || typeof self.forageKey !== 'function') return carried;
            try {
                var prefix = String(self.forageKey('global')).slice(0, -'global'.length);
                carried = localforage.keys().then(function (keys) {
                    return Promise.all(keys.map(function (key) {
                        var saveName = key.indexOf(prefix) === 0 ? key.slice(prefix.length) : '';
                        if (!/^(config|global|file\d+)$/.test(saveName) || saves.exists(name(saveName))) return null;
                        return localforage.getItem(key).then(function (zip) {
                            if (typeof zip === 'string' && zip) saves.write(name(saveName), zip);
                        });
                    }));
                }).catch(function () {});
            } catch (e) { /* the game's id is not known yet: each save is carried over when it is loaded */ }
            return carried;
        }
    }

    function patch() {
        var SM = window.SceneManager, AM = window.AudioManager;
        if (!SM || SM.__agvnPatched) return !!SM;
        SM.__agvnPatched = true;
        filesForSaves(window.StorageManager);
        var isMz = window.Utils && Utils.RPGMAKER_NAME === 'MZ';
        if (!isMz) desktopMv(); // before main.js starts the game: the renderer and screen size are chosen then
        var original = SM.catchException;
        SM.catchException = function (e) {
            if (isMinor(e)) {
                console.warn('[AGVN] skipped game error: ' + e.message);
                if (!isMz && typeof this.requestUpdate === 'function') this.requestUpdate();
                return;
            }
            keepFatal((e && e.name ? e.name + ': ' : '') + (e && e.message ? e.message : String(e)));
            return original.apply(this, arguments);
        };
        var G = window.Graphics;
        if (G && typeof G.printLoadingError === 'function') { // RPG Maker MV: a picture or data file that is missing
            var printLoadingError = G.printLoadingError;
            G.printLoadingError = function (url) {
                keepFatal('Failed to load: ' + url);
                return printLoadingError.apply(this, arguments);
            };
        }
        if (AM) {
            if (typeof AM.checkErrors === 'function') AM.checkErrors = function () {};
            if (typeof AM.checkWebAudioError === 'function') AM.checkWebAudioError = function () {};
        }
        return true;
    }

    function keepFatal(text) {
        if (!window.__agvnFatal) window.__agvnFatal = String(text).slice(0, 300);
    }

    var tries = 0;
    var timer = setInterval(function () { if (patch() || ++tries > 2000) clearInterval(timer); }, 30);
    window.addEventListener('load', patch, true);
})();
