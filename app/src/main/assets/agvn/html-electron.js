/* AGVN Player - Electron's side of an Electron game whose page runs in "Chạy nhẹ" (AgvnElectronGame). Copyright (c)
 * 2026 agvn.io - MIT License. Loaded before html-compat.js, whose require() gives these by name, or by the last part of
 * a path (require('./lib/greenworks')). The game's main script does not run on the phone, so:
 *  - require('electron') and '@electron/remote': ipcRenderer's messages go nowhere (invoke() resolves to undefined);
 *    the window, app, dialog, screen, shell (nothing leaves the phone), webFrame, clipboard and contextBridge answer as
 *    for one full-screen window. A method they do not have does nothing.
 *  - greenworks and steamworks.js (Steam): Steam runs, no achievement is unlocked and unlocking one only says yes; a
 *    file saved to Steam Cloud stays in the page (localStorage).
 * The console names the first call of each kind that would have needed the main script, for "Gửi nhật ký".
 */
(function () {
    'use strict';
    if (!window.__agvnElectron) return;
    var noop = function () {};
    var told = {};

    function tell(what) {
        if (told[what]) return;
        told[what] = true;
        console.warn('[AGVN] Electron: ' + what + ' không chạy khi chạy nhẹ (không có tiến trình chính của game)');
    }

    // unknown methods do nothing; never a thenable, so that `await` on one goes on
    var KEEP = { then: 1, toJSON: 1, constructor: 1, valueOf: 1, toString: 1, inspect: 1 };

    function soft(base, name) {
        if (typeof Proxy !== 'function') return base;
        return new Proxy(base, { get: function (t, k) {
            if (k in t || typeof k !== 'string' || KEEP[k]) return t[k];
            return function () { tell(name + '.' + k + '()'); };
        } });
    }

    // an EventEmitter that never emits: its listener methods give itself back for chaining
    function emitter(base, name) {
        var self = soft(base, name);
        ['on', 'once', 'addListener', 'prependListener', 'prependOnceListener', 'removeListener', 'off',
            'removeAllListeners', 'setMaxListeners'].forEach(function (m) { base[m] = function () { return self; }; });
        base.emit = function () { return false; };
        base.listenerCount = function () { return 0; };
        base.listeners = function () { return []; };
        return self;
    }

    function later(cb) {
        var args = Array.prototype.slice.call(arguments, 1);
        if (typeof cb === 'function') setTimeout(function () { cb.apply(null, args); }, 0);
    }

    function size() { return [window.innerWidth, window.innerHeight]; }

    function bounds() { return { x: 0, y: 0, width: window.innerWidth, height: window.innerHeight }; }

    var ipcRenderer = emitter({
        send: function (ch) { tell('ipcRenderer.send("' + ch + '")'); },
        sendSync: function (ch) { tell('ipcRenderer.sendSync("' + ch + '")'); },
        invoke: function (ch) { tell('ipcRenderer.invoke("' + ch + '")'); return Promise.resolve(undefined); },
        sendTo: noop, sendToHost: noop, postMessage: noop
    }, 'ipcRenderer');

    var full = true;
    var win = emitter({
        id: 1, isFullScreen: function () { return full; }, setFullScreen: function (f) { full = !!f; },
        isMaximized: function () { return true; }, isMinimized: function () { return false; },
        isVisible: function () { return true; }, isDestroyed: function () { return false; },
        isFocused: function () { return document.visibilityState !== 'hidden'; },
        getSize: size, getContentSize: size, getBounds: bounds, getContentBounds: bounds,
        getTitle: function () { return document.title; }, setTitle: function (t) { document.title = String(t); },
        reload: function () { location.reload(); }, close: noop, destroy: noop
    }, 'BrowserWindow');
    var contents = emitter({
        id: 1, getURL: function () { return location.href; }, getZoomFactor: function () { return 1; },
        isDevToolsOpened: function () { return false; }, reload: function () { location.reload(); },
        executeJavaScript: function (code) {
            try { return Promise.resolve((0, eval)(String(code))); } catch (e) { return Promise.reject(e); }
        }
    }, 'webContents');
    win.webContents = contents;

    function version() {
        try {
            var xhr = new XMLHttpRequest();
            xhr.open('GET', '/package.json', false);
            xhr.send();
            return JSON.parse(xhr.responseText).version || '1.0.0';
        } catch (e) {
            return '1.0.0';
        }
    }

    var locale = function () { return navigator.language || 'en-US'; };
    var app = emitter({
        getName: function () { return document.title; }, getVersion: version, getLocale: locale, getSystemLocale: locale,
        getPreferredSystemLanguages: function () { return [locale()]; }, getPath: function () { return '/'; },
        getAppPath: function () { return '/'; }, isPackaged: true, isReady: function () { return true; },
        whenReady: function () { return Promise.resolve(); }, quit: noop, exit: noop, relaunch: noop, focus: noop
    }, 'app');
    var display = function () {
        var s = window.screen || {}, area = { width: s.width || window.innerWidth, height: s.height || window.innerHeight };
        return { id: 1, scaleFactor: window.devicePixelRatio || 1, size: area, workAreaSize: area,
            bounds: { x: 0, y: 0, width: area.width, height: area.height }, workArea: { x: 0, y: 0, width: area.width, height: area.height } };
    };
    var screens = emitter({ getPrimaryDisplay: display, getAllDisplays: function () { return [display()]; },
        getDisplayNearestPoint: display, getCursorScreenPoint: function () { return { x: 0, y: 0 }; } }, 'screen');
    var dialog = soft({
        showMessageBox: function () { return Promise.resolve({ response: 0, checkboxChecked: false }); },
        showMessageBoxSync: function () { return 0; },
        showErrorBox: function (title, text) { console.error('[AGVN] Electron: ' + title + ': ' + text); },
        showOpenDialog: function () { return Promise.resolve({ canceled: true, filePaths: [] }); },
        showOpenDialogSync: function () { return undefined; },
        showSaveDialog: function () { return Promise.resolve({ canceled: true }); },
        showSaveDialogSync: function () { return undefined; }
    }, 'dialog');
    var shell = soft({
        openExternal: function () { tell('shell.openExternal'); return Promise.resolve(); },
        openPath: function () { return Promise.resolve(''); }, showItemInFolder: noop, beep: noop
    }, 'shell');
    var webFrame = soft({ setZoomFactor: noop, getZoomFactor: function () { return 1; }, setZoomLevel: noop,
        getZoomLevel: function () { return 0; }, setVisualZoomLevelLimits: function () { return Promise.resolve(); } }, 'webFrame');
    var clipboard = soft({
        writeText: function (t) { try { navigator.clipboard.writeText(String(t)); } catch (e) { /* not allowed */ } },
        readText: function () { return ''; }
    }, 'clipboard');
    var remote = soft({
        getCurrentWindow: function () { return win; }, getCurrentWebContents: function () { return contents; },
        app: app, dialog: dialog, screen: screens, shell: shell, get process() { return window.process; },
        BrowserWindow: { getFocusedWindow: function () { return win; }, getAllWindows: function () { return [win]; },
            fromId: function () { return win; } },
        require: function (name) { return window.require(name); }, getGlobal: function () { return undefined; }
    }, 'remote');
    var electron = soft({ ipcRenderer: ipcRenderer, remote: remote, shell: shell, webFrame: webFrame, clipboard: clipboard,
        contextBridge: { exposeInMainWorld: function (key, api) { window[key] = api; } } }, 'electron');

    var STEAM = 'agvn_steam:';
    var steamId = { screenName: 'Player', steamId: '0', accountId: 0, staticAccountId: '0',
        getRawSteamID: function () { return '0'; }, getAccountID: function () { return 0; }, isValid: function () { return 1; } };
    var yes = function () { return true; }, no = function () { return false; }, none = function () { return []; };
    var english = function () { return 'english'; };
    var greenworks = emitter({
        init: yes, initAPI: yes, isSteamRunning: yes, restartAppIfNecessary: no, getAppId: function () { return 0; },
        getSteamId: function () { return steamId; }, getCurrentGameLanguage: english, getCurrentUILanguage: english,
        getCurrentGameInstallDir: function () { return '/'; }, getAchievementNames: none,
        getNumberOfAchievements: function () { return 0; }, getFriends: none,
        activateAchievement: function (name, ok) { later(ok); }, clearAchievement: function (name, ok) { later(ok); },
        getAchievement: function (name, ok) { later(ok, false); },
        indicateAchievementProgress: function (name, now, max, ok) { later(ok); },
        getStatInt: function () { return 0; }, getStatFloat: function () { return 0; }, setStat: yes,
        storeStats: function (ok) { later(ok, {}); }, isCloudEnabled: no, isCloudEnabledForUser: no, enableCloud: noop,
        saveTextToFile: function (name, text, ok, fail) {
            try { localStorage.setItem(STEAM + name, String(text)); later(ok); } catch (e) { later(fail, e); }
        },
        readTextFromFile: function (name, ok, fail) {
            var text = null;
            try { text = localStorage.getItem(STEAM + name); } catch (e) { /* none */ }
            if (text === null) later(fail, new Error('Steam Cloud: không có ' + name)); else later(ok, text);
        },
        saveFilesToCloud: function (files, ok) { later(ok); },
        activateGameOverlay: noop, activateGameOverlayToWebPage: noop, isGameOverlayEnabled: no, runCallbacks: noop
    }, 'greenworks');
    var steamClient = soft({
        achievement: soft({ activate: yes, isActivated: no, clear: yes, names: none }, 'steamworks.achievement'),
        localplayer: soft({ getName: function () { return 'Player'; }, getLevel: function () { return 0; },
            getIpCountry: function () { return 'VN'; } }, 'steamworks.localplayer'),
        apps: soft({ isSubscribed: yes, currentGameLanguage: english, isDlcInstalled: no }, 'steamworks.apps'),
        cloud: soft({ isEnabledForAccount: no, isEnabledForApp: no }, 'steamworks.cloud'),
        callback: soft({ register: function () { return { disconnect: noop }; } }, 'steamworks.callback'),
        overlay: soft({}, 'steamworks.overlay')
    }, 'steamworks');
    var steamworks = soft({ init: function () { return steamClient; }, restartAppIfNecessary: no,
        electronEnableSteamOverlay: noop }, 'steamworks.js');

    window.__agvnModules = { electron: electron, '@electron/remote': remote, greenworks: greenworks,
        'steamworks.js': steamworks };
})();
