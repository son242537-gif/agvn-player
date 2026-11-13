/* AGVN Player - Steam for a page game in "Chạy nhẹ": greenworks and steamworks.js, as JoiPlay ships its own
 * greenworks.js. Copyright (c) 2026 agvn.io - MIT License. Loaded before html-compat.js, whose require() gives these by
 * name or by the last part of a path: require('greenworks'), require('./greenworks.js'), require('./lib/greenworks-win64').
 * On a PC they load Steam's native library (greenworks-win64.node), which a phone cannot run, so an RPG Maker MV/MZ or
 * NW.js game that asked for them got an empty object and stopped at "greenworks.init is not a function" (Electron
 * games had them from 0.1.70, html-electron.js). Here Steam runs and the player is "Player"; an achievement the game
 * unlocks is kept in the page (localStorage) and named in the console, so the game finds it unlocked next time; a
 * file saved to Steam Cloud stays in the page too. Methods it has not do nothing.
 */
(function () {
    'use strict';
    if (window.__agvnSteamModules) return;
    var noop = function () {};
    var told = {};
    var CLOUD = 'agvn_steam:', ACHIEVED = 'agvn_steam_ach:';

    function tell(what) {
        if (told[what]) return;
        told[what] = true;
        console.warn('[AGVN] Steam: ' + what + ' không có khi chạy nhẹ');
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

    // greenworks is an EventEmitter that never emits: its listener methods give itself back for chaining
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

    function get(key) { try { return localStorage.getItem(key); } catch (e) { return null; } }

    function set(key, value) {
        try {
            if (value === null) localStorage.removeItem(key); else localStorage.setItem(key, String(value));
            return true;
        } catch (e) {
            return false;
        }
    }

    function achieved(name) { return get(ACHIEVED + name) !== null; }

    function achieve(name, on) {
        if (on && !achieved(name)) console.log('[AGVN] Steam: mở thành tựu ' + name);
        set(ACHIEVED + name, on ? Date.now() : null);
    }

    function achievedNames() {
        var out = [];
        try {
            for (var i = 0; i < localStorage.length; i++) {
                var k = localStorage.key(i);
                if (k && k.indexOf(ACHIEVED) === 0) out.push(k.slice(ACHIEVED.length));
            }
        } catch (e) { /* no storage */ }
        return out;
    }

    var steamId = { screenName: 'Player', steamId: '0', accountId: 0, staticAccountId: '0', type: { name: 'k_EAccountTypeIndividual' },
        getRawSteamID: function () { return '0'; }, getAccountID: function () { return 0; }, isValid: function () { return 1; },
        getPersonaName: function () { return 'Player'; } };
    var yes = function () { return true; }, no = function () { return false; }, none = function () { return []; };
    var english = function () { return 'english'; };
    var greenworks = emitter({
        init: yes, initAPI: yes, isSteamRunning: yes, restartAppIfNecessary: no, getAppId: function () { return 0; },
        getSteamId: function () { return steamId; }, getCurrentGameLanguage: english, getCurrentUILanguage: english,
        getCurrentGameInstallDir: function () { return '/'; }, getAppInstallDir: function () { return '/'; },
        getAchievementNames: achievedNames, getNumberOfAchievements: function () { return achievedNames().length; },
        activateAchievement: function (name, ok) { achieve(name, true); later(ok); },
        clearAchievement: function (name, ok) { achieve(name, false); later(ok); },
        getAchievement: function (name, ok) { later(ok, achieved(name)); },
        indicateAchievementProgress: function (name, now, max, ok) { later(ok); },
        getStatInt: function (name) { return Number(get(CLOUD + 'stat:' + name)) || 0; },
        getStatFloat: function (name) { return Number(get(CLOUD + 'stat:' + name)) || 0; },
        setStat: function (name, value) { return set(CLOUD + 'stat:' + name, value); },
        storeStats: function (ok) { later(ok, {}); }, resetAllStats: function (withAchievements, ok) { later(ok); },
        isCloudEnabled: no, isCloudEnabledForUser: no, enableCloud: noop, getCloudQuota: function (ok) { later(ok, 0, 0); },
        saveTextToFile: function (name, text, ok, fail) {
            if (set(CLOUD + name, text)) later(ok); else later(fail, new Error('Steam Cloud: đầy bộ nhớ'));
        },
        readTextFromFile: function (name, ok, fail) {
            var text = get(CLOUD + name);
            if (text === null) later(fail, new Error('Steam Cloud: không có ' + name)); else later(ok, text);
        },
        deleteRemoteFile: function (name, ok) { set(CLOUD + name, null); later(ok); },
        saveFilesToCloud: function (files, ok) { later(ok); },
        getFriends: none, getFriendCount: function () { return 0; }, getDLCCount: function () { return 0; },
        isDLCInstalled: no, isSubscribedApp: yes, isAppInstalled: yes,
        activateGameOverlay: noop, activateGameOverlayToWebPage: noop, activateGameOverlayToStore: noop,
        isGameOverlayEnabled: no, runCallbacks: noop
    }, 'greenworks');
    var steamClient = soft({
        achievement: soft({ activate: function (name) { achieve(name, true); return true; }, isActivated: achieved,
            clear: function (name) { achieve(name, false); return true; }, names: achievedNames }, 'steamworks.achievement'),
        localplayer: soft({ getName: function () { return 'Player'; }, getLevel: function () { return 0; },
            getIpCountry: function () { return 'VN'; }, getSteamId: function () {
                return { steamId64: typeof BigInt === 'function' ? BigInt(0) : 0, steamId32: '0', accountId: 0 }; } },
            'steamworks.localplayer'),
        apps: soft({ isSubscribed: yes, currentGameLanguage: english, isDlcInstalled: no, appInstallDir: function () { return '/'; } },
            'steamworks.apps'),
        cloud: soft({ isEnabledForAccount: no, isEnabledForApp: no }, 'steamworks.cloud'),
        callback: soft({ register: function () { return { disconnect: noop }; } }, 'steamworks.callback'),
        overlay: soft({}, 'steamworks.overlay'), utils: soft({ getAppId: function () { return 0; } }, 'steamworks.utils')
    }, 'steamworks');
    var steamworks = soft({ init: function () { return steamClient; }, restartAppIfNecessary: no,
        electronEnableSteamOverlay: noop }, 'steamworks.js');

    // by the names games require them, the native library's own included
    window.__agvnSteamModules = { greenworks: greenworks, 'greenworks-win64': greenworks, 'greenworks-win32': greenworks,
        'greenworks-linux64': greenworks, 'greenworks-osx64': greenworks, 'steamworks.js': steamworks, steamworks: steamworks };
})();
