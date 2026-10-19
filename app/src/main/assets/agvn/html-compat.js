/* AGVN Player - compatibility layer for RPG Maker MV/MZ games run without NW.js. Copyright (c) 2026 agvn.io - MIT License.
 * Loaded before the game's own scripts. It fixes what breaks when a PC game runs in a phone browser:
 *  1. Plugins that call require('fs'/'path'/'nw.gui'/'os') get small stand-ins (files are read from the game folder,
 *     written files go to localStorage), and `process` is there for plugins that read it as they load (mainModule,
 *     execPath, platform, cwd(), env, on()). It is a function, not an object: RPG Maker takes typeof process ===
 *     'object' for NW.js (Utils.isNwjs), so the engine keeps its browser save functions, which 6 sends to real files.
 *  2. Small script errors ("Cannot read property 'opacity' of undefined", "...'length'...", "x is not a function")
 *     are logged and skipped instead of stopping the game with an error screen. If they keep coming (a truly broken
 *     scene), the normal error screen is shown so the player is not stuck on a frozen picture.
 *  3. A missing sound file is skipped instead of stopping the game ("Failed to load: audio/...").
 *  4. RPG Maker MV is told it runs on a PC, as JoiPlay does: on a phone it asks for .m4a sound and .mp4 video, which
 *     PC games do not ship (every sound was missing, silently because of 3), and draws with the slow canvas renderer.
 *  5. An error that stops the game (its error screen, or a missing picture or data file) is kept in
 *     window.__agvnFatal, so the app can say what happened when the player leaves ("Tự sửa lỗi"). So is an error
 *     in a script as it loads (a plugin's first lines) that keeps RPG Maker MZ from starting, with its file and line.
 *  6. Saves are files in the game's save folder, as on a PC (MV save/file1.rpgsave, MZ save/file1.rmmzsave), through
 *     the app (window.AgvnSaves): "Nhập save" / "Xuất save" and the Windows version reach them. Saves the browser kept
 *     before are carried over while the folder holds none. A plugin's own data (achievements, a gallery) is the file
 *     its PC version writes; when the folder will not take it, it stays in the browser and the game goes on.
 *  7. The page counts as the picked window while it shows (document.hasFocus): RPG Maker MZ moves its scenes only
 *     then, and WebView can leave the page without the focus, so every MZ game stood still on its first scene.
 *  8. Other NW.js games made of pages (the app sets __agvnNwjs first, AgvnNwjsGame) also get `nw` as NW.js has it,
 *     for scripts that call nw.Window.get() or nw.App without asking whether they run on NW.js.
 *  9. RPG Maker MZ's touch buttons take a narrow img/system/ButtonSet (the app's empty picture for a missing or
 *     unreadable one, an MV game's) stretched to the size they are cut from, instead of stopping the game with
 *     "ButtonSet image is too small".
 * 10. A touch on a field or button of the page (a name or number box a plugin puts over the game: <input>,
 *     <textarea>, <select>, <button>, <label>) goes to it. RPG Maker cancelled every touch inside its picture, so once
 *     the keyboard went the box got no focus and its OK button no click, and the game waited for ever. Touches on the
 *     game stay the engine's. A text box that covers its own OK button on a game shrunk to fit the phone is narrowed.
 * 11. path.parse and path.format as Node has them, and process.execPath names the game's own exe (the app sets
 *     __agvnExe first): a game that picks its language from path.parse(process.execPath).name gets a PC's answer.
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
        isAbsolute: function (p) { return String(p).charAt(0) === '/'; },
        // 11: a game picked its language by its exe's name, path.parse(process.execPath).name, and got the default with
        // "path.parse is not a function" (20/10/2026). As Node's for '/' paths, nothing normalised; "\" counts as "/"
        parse: function (p) {
            p = String(p).replace(/\\/g, '/');
            var root = p.charAt(0) === '/' ? '/' : '', end = p.length;
            while (end > root.length && p.charAt(end - 1) === '/') end--;
            var body = p.slice(0, end), cut = body.lastIndexOf('/'), base = body.slice(cut + 1);
            var dot = base.lastIndexOf('.'), ext = dot > 0 && base !== '..' ? base.slice(dot) : '';
            return { root: root, dir: cut < 0 ? '' : body.slice(0, cut) || '/', base: base, ext: ext,
                name: base.slice(0, base.length - ext.length) };
        },
        format: function (o) {
            var dir = o.dir || o.root || '', base = o.base || (o.name || '') + (o.ext || '');
            return !dir ? base : dir === o.root ? dir + base : dir + '/' + base;
        }
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
        },
        lstatSync: function (p) { return fs.statSync(p); },
        accessSync: function (p) { fs.statSync(p); },
        // a copy, a move or an addition is written as any file is (localStorage); readdirSync stays empty, so a plugin
        // that backs up a whole folder copies nothing
        copyFileSync: function (from, to) { fs.writeFileSync(to, fs.readFileSync(from)); },
        renameSync: function (from, to) { fs.writeFileSync(to, fs.readFileSync(from)); fs.unlinkSync(from); },
        appendFileSync: function (p, data) {
            var now = '';
            try { now = fs.readFileSync(p); } catch (e) { /* a new file */ }
            fs.writeFileSync(p, now + String(data));
        },
        rmSync: function (p) { fs.unlinkSync(p); },
        rmdirSync: function () {}
    };
    ['readFile', 'writeFile', 'unlink', 'mkdir', 'readdir', 'stat', 'lstat', 'access', 'copyFile', 'rename', 'appendFile',
        'rm', 'rmdir'].forEach(function (name) {
        fs[name] = function () {
            var args = Array.prototype.slice.call(arguments), cb = args.pop();
            try { var r = fs[name + 'Sync'].apply(fs, args); if (typeof cb === 'function') cb(null, r); } catch (e) { if (typeof cb === 'function') cb(e); }
        };
    });
    fs.exists = function (p, cb) { cb(fs.existsSync(p)); };

    var noop = function () {};
    var nwWindow = { on: noop, once: noop, removeAllListeners: noop, close: noop, focus: noop, maximize: noop,
        unmaximize: noop, minimize: noop, restore: noop, show: noop, hide: noop, enterFullscreen: noop,
        leaveFullscreen: noop, toggleFullscreen: noop, showDevTools: noop, closeDevTools: noop, resizeTo: noop,
        resizeBy: noop, moveTo: noop, moveBy: noop, setPosition: noop, setAlwaysOnTop: noop, setResizable: noop,
        setMinimumSize: noop, setMaximumSize: noop, reload: function () { location.reload(); },
        isFullscreen: true, title: document.title };
    nwWindow.on = nwWindow.once = function () { return nwWindow; };
    var gui = { Window: { get: function () { return nwWindow; } }, App: { quit: noop, argv: [], dataPath: '/' },
        Shell: { openExternal: noop, openItem: noop }, Menu: function () { return { append: noop, createMacBuiltin: noop }; },
        MenuItem: function () { return {}; }, Screen: { Init: noop, on: noop, screens: [] } };
    var modules = { fs: fs, path: path, 'nw.gui': gui, os: { platform: function () { return 'android'; }, EOL: '\n' } };

    // `process` for plugins written for NW.js that read it as they load (path.dirname(process.mainModule.filename),
    // process.platform, process.cwd(), process.env.LOCALAPPDATA, process.on('exit', ...)): without it they stopped the
    // game with "process is not defined". A function, not an object, so RPG Maker (Utils.isNwjs, main.js) and plugins
    // that check typeof process === 'object' still see a browser; versions names neither node nor nw, for the same.
    if (typeof window.process === 'undefined') {
        var proc = function process() {};
        var chain = function () { return proc; };
        proc.platform = 'android';
        proc.env = {};
        proc.argv = [];
        proc.execArgv = [];
        proc.versions = {};
        proc.version = '';
        // 11: the game's exe by its own name (the app sets __agvnExe first), which a game may pick its language by; in the
        // game folder, '/', as NW.js has it beside package.json
        proc.execPath = '/' + (String(window.__agvnExe || '').split(/[\\/]/).pop() || 'Game.exe');
        proc.mainModule = { filename: '/index.html' }; // its folder, '/', is the game folder for fs and path above
        proc.cwd = function () { return '/'; };
        proc.on = proc.once = proc.off = proc.addListener = proc.removeListener = proc.removeAllListeners = chain;
        proc.emit = proc.exit = noop;
        proc.nextTick = function (f) {
            var args = Array.prototype.slice.call(arguments, 1);
            Promise.resolve().then(function () { f.apply(null, args); });
        };
        proc.memoryUsage = function () { return { rss: 0, heapTotal: 0, heapUsed: 0, external: 0 }; };
        proc.stdout = proc.stderr = { write: function (s) { console.log(String(s)); return true; } };
        window.process = proc;
    }

    // --- 8: `nw` for an NW.js page game, whose scripts may use it without require('nw.gui'); package.json read on demand
    if (window.__agvnNwjs && typeof window.nw === 'undefined') {
        Object.defineProperty(gui.App, 'manifest', { get: function () {
            var xhr = httpGet('/package.json');
            try { return xhr ? JSON.parse(xhr.responseText) : {}; } catch (e) { return {}; }
        } });
        window.nw = gui;
    }

    if (typeof window.require !== 'function') {
        window.require = function (name) {
            if (modules[name]) return modules[name];
            console.warn('[AGVN] require("' + name + '") is not available on the phone');
            return {};
        };
    }

    // --- 7: picked while shown. RPG Maker MZ updates its scene only while window.top.document.hasFocus()
    // (SceneManager.isGameActive), as a PC game waits while another window is picked. A phone shows one app, but
    // WebView can leave the page without the focus (a tap does not give it; the ⌨ field holds it while it types): the
    // game drew on and stood still on its first scene, with no error. Plugins that ask the same follow. The game still
    // stops when the player leaves the app, since the page hides.
    document.hasFocus = function () { return document.visibilityState !== 'hidden'; };

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
    // A save's file name, as AgvnHtmlSaves.FILE takes it: no folder, nothing Windows refuses, at most 60 characters.
    var SAVE_NAME = (function () {
        try {
            return new RegExp('^(?:' + '[\\p{L}\\p{N}_-][\\p{L}\\p{M}\\p{N} _.()\\[\\]-]{0,59}\\.(?:rpgsave|rmmzsave)' + ')$', 'u');
        } catch (e) { // a WebView without Unicode classes in its patterns
            return /^[A-Za-z0-9_-][A-Za-z0-9 _.()\[\]-]{0,59}\.(?:rpgsave|rmmzsave)$/;
        }
    })();

    // The file a plugin's own data has on a PC: the engine's path for it, which the plugin may rename, without its
    // folder (cut as the engine gives it: path.join here drops the folder's last "/"). Null when no plain save name.
    function pcFile(SM, dirOf, fileOf, id) {
        try {
            var dir = String(SM[dirOf]()), file = String(SM[fileOf](id));
            file = (file.indexOf(dir) === 0 ? file.slice(dir.length) : file.split(/[\\/]/).pop()).replace(/^[\\/]+/, '');
            return SAVE_NAME.test(file) ? file : null;
        } catch (e) {
            return null; // the engine has no file path for it
        }
    }

    function filesForSaves(SM) {
        var saves = window.AgvnSaves;
        if (!saves || !SM || SM.__agvnSaves) return;
        SM.__agvnSaves = true;
        if (!saves.writable()) return; // a folder the app cannot write to: saves stay in the browser, as before
        var fromBrowser = !saves.any();
        if (typeof SM.saveToWebStorage === 'function') mvSaves(SM, saves, fromBrowser);
        else if (typeof SM.saveToForage === 'function') mzSaves(SM, saves, fromBrowser);
    }

    // A plugin may keep its own data (achievements, a gallery, unlocks for every playthrough) under an id that is not
    // a number: StorageManager.save('My Plugin Data', json). It gets the file its PC version writes (localFilePath,
    // "mydata.rpgsave" or "fileMy Plugin Data.rpgsave"), and the browser keeps it when the folder will not: 0.1.24 to
    // 0.1.32 made "fileMy Plugin Data.rpgsave", which the app refused, and the game stopped on "Cannot write".
    function mvSaves(SM, saves, fromBrowser) {
        var own = function (id) { return typeof id !== 'number'; };
        var name = function (id) {
            if (!own(id)) return id < 0 ? 'config.rpgsave' : id === 0 ? 'global.rpgsave' : 'file' + id + '.rpgsave';
            return pcFile(SM, 'localFileDirectoryPath', 'localFilePath', id)
                || 'file' + String(id).replace(/[^A-Za-z0-9_-]+/g, '_').slice(0, 40) + '.rpgsave';
        };
        var save = SM.saveToWebStorage, load = SM.loadFromWebStorage, exists = SM.webStorageExists, remove = SM.removeWebStorage;
        var failed = null;
        SM.saveToWebStorage = function (id, json) {
            failed = saves.write(name(id), LZString.compressToBase64(json)) ? null : id;
            if (failed === null) return;
            if (!own(id)) throw new Error('Cannot write save/' + name(id));
            failed = null;
            console.warn('[AGVN] save/' + name(id) + ' not written: kept in the browser');
            try {
                save.call(this, id, json);
                saves.remove(name(id)); // an older file would be read first
            } catch (e) {
                console.warn('[AGVN] ' + e.message);
            }
        };
        SM.loadFromWebStorage = function (id) {
            var data = saves.read(name(id));
            if (data !== null || !fromBrowser && !own(id)) return LZString.decompressFromBase64(data);
            var json = load.call(this, id);
            if (json) saves.write(name(id), LZString.compressToBase64(json));
            return json;
        };
        SM.webStorageExists = function (id) { return saves.exists(name(id)) || (fromBrowser || own(id)) && exists.call(this, id); };
        SM.removeWebStorage = function (id) {
            if (id === failed) { failed = null; return; } // MV removes a save it failed to write: the last one stays
            saves.remove(name(id));
            if (fromBrowser || own(id)) remove.call(this, id);
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

    // A plugin's own data has a name of its own (StorageManager.saveObject('My Data', ...)): as for MV, the file its PC
    // version writes (filePath), else the browser.
    function mzSaves(SM, saves, fromBrowser) {
        var own = function (saveName) { return !/^(config|global|file\d+)$/.test(saveName); };
        var name = function (saveName) {
            return own(saveName) && pcFile(SM, 'fileDirectoryPath', 'filePath', saveName) || saveName + '.rmmzsave';
        };
        var save = SM.saveToForage, load = SM.loadFromForage, exists = SM.forageExists, remove = SM.removeForage;
        var carried = null;
        SM.saveToForage = function (saveName, zip) {
            if (typeof zip !== 'string') return save.apply(this, arguments); // not the text a PC writes: kept as before
            if (saves.write(name(saveName), zip)) return Promise.resolve();
            if (!own(saveName)) return Promise.reject(new Error('Cannot write save/' + name(saveName)));
            console.warn('[AGVN] save/' + name(saveName) + ' not written: kept in the browser');
            return save.apply(this, arguments).then(function (r) {
                saves.remove(name(saveName)); // an older file would be read first
                return r;
            });
        };
        SM.loadFromForage = function (saveName) {
            var self = this;
            return carryOver(self).then(function () {
                var zip = saves.read(name(saveName));
                if (zip !== null || !fromBrowser && !own(saveName)) return zip;
                return load.call(self, saveName).then(function (z) {
                    if (typeof z === 'string' && z) saves.write(name(saveName), z);
                    return z;
                });
            });
        };
        SM.forageExists = function (saveName) {
            return saves.exists(name(saveName)) || (fromBrowser || own(saveName)) && exists.call(this, saveName);
        };
        SM.removeForage = function (saveName) {
            saves.remove(name(saveName));
            return fromBrowser || own(saveName) ? remove.call(this, saveName) : Promise.resolve();
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
        formTaps(); // 10: as soon as rpg_core / rmmz_core has run, if no script load told it first
        var SM = window.SceneManager, AM = window.AudioManager;
        if (!SM || SM.__agvnPatched) return !!SM;
        SM.__agvnPatched = true;
        filesForSaves(window.StorageManager);
        var isMz = window.Utils && Utils.RPGMAKER_NAME === 'MZ';
        if (!isMz) desktopMv(); // before main.js starts the game: the renderer and screen size are chosen then
        if (window.__agvnStretch) stretch(window.Graphics); // "Toàn màn hình cho mọi game"
        wideButtons(); // MZ: when its sprites are loaded; else as the game starts, once the plugins are too
        var run = SM.run;
        if (typeof run === 'function') SM.run = function () { wideButtons(); formTaps(); return run.apply(this, arguments); };
        var original = SM.catchException;
        SM.catchException = function (e) {
            if (e && /^ButtonSet image is too small/.test(e.message) && !(window.Sprite_Button && Sprite_Button.prototype.__agvnWide)) {
                wideButtons(); // a game that started another way: the next frame has the stretched picture
                if (Sprite_Button.prototype.__agvnWide) return;
            }
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

    // --- 7: "Toàn màn hình cho mọi game" (the app sets __agvnStretch first): the game's canvas, video and error box
    // stretched over the whole page instead of centred with bars, and a touch mapped back on each axis on its own.
    // MV and MZ both size them in Graphics._centerElement and read touches through pageToCanvasX/Y.
    function stretch(G) {
        if (!G || G.__agvnStretched || typeof G._centerElement !== 'function') return;
        G.__agvnStretched = true;
        var center = G._centerElement;
        G._centerElement = function (element) {
            center.apply(this, arguments);
            if (!element || !element.style) return;
            element.style.margin = '0';
            element.style.width = window.innerWidth + 'px';
            element.style.height = window.innerHeight + 'px';
        };
        var axis = function (page, start, size, game) {
            return size > 0 ? Math.round((page - start) * game / size) : 0;
        };
        G.pageToCanvasX = function (x) {
            var c = this._canvas;
            return c ? axis(x, c.getBoundingClientRect().left + window.pageXOffset, c.clientWidth, this._width) : 0;
        };
        G.pageToCanvasY = function (y) {
            var c = this._canvas;
            return c ? axis(y, c.getBoundingClientRect().top + window.pageYOffset, c.clientHeight, this._height) : 0;
        };
        if (G._canvas && typeof G._updateAllElements === 'function') G._updateAllElements();
    }

    // --- 9: MZ cuts its touch buttons (Menu, Cancel, page up/down, shown with Touch UI, on by default) from
    // img/system/ButtonSet, 11 cells of 48 px wide, and stops the game when the picture is narrower ("Probably MV image
    // is used"). A missing or unreadable one is served as a 1 px empty picture (AgvnHtmlStandIn), so that game stopped
    // where a PC says "Failed to load" (19/10/2026). A narrower one is stretched to the size instead: buttons from the
    // empty picture are clear, and the app tells the player which file it could not read. Only the engine's own check
    // is replaced; MV has none.
    function wideButtons() {
        var p = window.Sprite_Button && Sprite_Button.prototype;
        if (!p || p.__agvnWide || typeof p.checkBitmap !== 'function' || !/too small/.test(String(p.checkBitmap))) return;
        p.__agvnWide = true;
        var wide = null;
        p.checkBitmap = function () {
            var b = this.bitmap, w = this.blockWidth() * 11, h = this.blockHeight() * 2;
            if (!b || !b.isReady() || b.width >= w || !(b.width > 0 && b.height > 0)) return;
            if (!wide || wide.__agvnFrom !== b) { // every button shares the one ButtonSet, so one stretched copy
                wide = new Bitmap(w, h);
                wide.blt(b, 0, 0, b.width, b.height, 0, 0, w, h);
                wide.__agvnFrom = b;
            }
            this.bitmap = wide;
        };
    }

    // --- 10: MV and MZ take every touch on the page (TouchInput._onTouchStart, on document) and cancel one inside the
    // game's picture (preventDefault), so the browser gave a box a plugin put over the game no focus and its OK button
    // no click (20/10/2026). A touch on such an element is the page's now, as a mouse click is on a PC. The engine binds
    // its handler as it starts (SceneManager.run): it is wrapped once rpg_core / rmmz_core has run, and again just
    // before, should a plugin have replaced it. One bound before this file got to it finds such a touch outside the
    // game's picture instead (formsLate). Touchend and mousedown cancel nothing, so they are left as they are.
    var FIELD = /^(INPUT|TEXTAREA|SELECT|OPTION|BUTTON|LABEL)$/, formsLate = null;

    function onField(event) {
        for (var n = event && event.target; n && n.nodeType === 1; n = n.parentNode) { // a <span> in a <button> too
            if (FIELD.test(String(n.tagName).toUpperCase()) || n.isContentEditable) return true;
        }
        return false;
    }

    function formTaps() {
        var T = window.TouchInput;
        if (!T || typeof T._onTouchStart !== 'function') return false;
        if (formsLate === null && (formsLate = typeof T._screenPressed === 'boolean')) lateForms(); // clear() has run
        var start = T._onTouchStart;
        if (start.__agvnField) return true;
        T._onTouchStart = function (event) { // a field's touch: the browser gives it the focus, the keyboard or the click
            if (!onField(event)) return start.apply(this, arguments);
        };
        T._onTouchStart.__agvnField = true;
        return true;
    }

    function lateForms() {
        var G = window.Graphics, during = null;
        if (!G || typeof G.isInsideCanvas !== 'function') return;
        var inside = G.isInsideCanvas;
        G.isInsideCanvas = function () { return during ? false : inside.apply(this, arguments); };
        window.addEventListener('touchstart', function (ev) {
            during = onField(ev) ? ev : null;
            if (during) setTimeout(function () { during = null; }, 0); // also when a listener stopped it on the way
        }, true);
        window.addEventListener('touchstart', function () { during = null; }, false);
        console.warn('[AGVN] the game took touches before html-compat.js: fields kept through isInsideCanvas');
    }

    // A text box a plugin places by the game's scale but sizes in fixed px covers its OK button once the game is
    // shrunk to fit the phone (scale ~0.5). It is narrowed to end before a button on its right, from where the two
    // are on the screen, so it is never shrunk twice; a button elsewhere, or one the plugin keeps covering, is put in
    // front of it. Only on a shrunk game, only a box over a button of its own form, never a button's size.
    function isText(n) {
        var tag = n && n.tagName ? String(n.tagName).toUpperCase() : '';
        return tag === 'TEXTAREA' || tag === 'SELECT'
            || tag === 'INPUT' && /^(text|search|tel|url|email|password|number)$/i.test(n.type || 'text');
    }

    function fitField(f) {
        var G = window.Graphics, box = f.form || f.parentNode;
        if (!G || !(G._realScale < 0.95) || !box || !box.querySelectorAll || !f.getBoundingClientRect) return;
        var all = box.querySelectorAll('button, input');
        for (var i = 0; i < all.length; i++) {
            var b = all[i], tag = String(b.tagName).toUpperCase();
            if (b !== f && (tag === 'BUTTON' || /^(submit|button|reset|image)$/i.test(b.type))) unblock(f, b, G._realScale);
        }
    }

    function unblock(f, b, scale) {
        var r = f.getBoundingClientRect(), rb = b.getBoundingClientRect();
        if (!r.width || !rb.width || r.right <= rb.left || rb.right <= r.left || r.bottom <= rb.top || rb.bottom <= r.top) return;
        var css = function (e) { return window.getComputedStyle ? window.getComputedStyle(e) : {}; };
        var room = rb.left - r.left - 2, cf = css(f), note = '';
        if (room >= Math.max(24, 2 * r.height) && (f.__agvnFits = (f.__agvnFits || 0) + 1) <= 3) {
            var k = f.offsetWidth > 0 ? r.width / f.offsetWidth : 1, px = function (v) { return parseFloat(v) || 0; };
            var extra = cf.boxSizing === 'border-box' ? 0
                : px(cf.paddingLeft) + px(cf.paddingRight) + px(cf.borderLeftWidth) + px(cf.borderRightWidth);
            f.style.width = Math.floor(room / k - extra) + 'px';
            note = 'narrowed to end before its button';
        } else if (!b.__agvnFront) {
            b.__agvnFront = true;
            if ((css(b).position || 'static') === 'static') b.style.position = 'relative';
            b.style.zIndex = String((parseInt(cf.zIndex, 10) || 0) + 1);
            note = 'button put in front of it';
        }
        if (note && !f.__agvnNoted) console.log('[AGVN] text box over its button, ' + note + ' (game at '
            + (f.__agvnNoted = scale.toFixed(2)) + 'x)');
    }

    function fitSoon(f) { // once the plugin has placed it
        if (isText(f)) [60, 400].forEach(function (ms) { setTimeout(function () { fitField(f); }, ms); });
    }

    function keepFatal(text) {
        if (!window.__agvnFatal) window.__agvnFatal = String(text).slice(0, 300);
    }

    // --- 5, as the scripts load: an error in a plugin's first lines keeps RPG Maker MZ from starting (main.js shows it
    // on its error screen, and the first scene never comes). The first one is kept with its file and line once that
    // screen shows. MV goes on after such an error, so there it is no stop.
    var loadError = '';
    window.addEventListener('error', function (ev) {
        if (loadError || !ev || !(ev.error || ev.message)) return;
        var e = ev.error;
        loadError = (e && e.name ? e.name + ': ' : '') + (e && e.message ? e.message : ev.message)
            + ' (' + String(ev.filename || '').split('/').pop() + ':' + (ev.lineno || '') + ')';
    });
    window.addEventListener('load', function () {
        setTimeout(function () { // main.js has had the load event by then
            var printer = document.getElementById('errorPrinter');
            var shown = printer ? String(printer.innerText || printer.textContent || '').replace(/\s+/g, ' ').trim() : '';
            if (shown && !(window.SceneManager && SceneManager._scene)) keepFatal(loadError || shown);
        }, 0);
    });

    // --- 10: the engine's touch handler as soon as rpg_core / rmmz_core has run (a script's load passes document
    // first, before main.js hears of it), and a text box once it has the focus, or when the screen turns
    if (typeof document.addEventListener === 'function') {
        document.addEventListener('load', function scripts() {
            if (formTaps()) document.removeEventListener('load', scripts, true);
        }, true);
        document.addEventListener('focusin', function (ev) { fitSoon(ev.target); }, true);
        window.addEventListener('resize', function () { fitSoon(document.activeElement); });
    }

    var tries = 0;
    var timer = setInterval(function () { if (patch() || ++tries > 2000) clearInterval(timer); }, 30);
    window.addEventListener('load', patch, true);
})();
