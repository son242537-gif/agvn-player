/* AGVN Player - small stand-ins for Node modules a page game written for NW.js requires as it loads. Copyright (c) 2026
 * agvn.io - MIT License. Loaded before html-compat.js, whose require hands them out (window.__agvnNodeModules).
 * A webpack-bundled logger (pino) asked for assert, events, module, stream, string_decoder, tty, url, util and
 * worker_threads as it loaded, and got empty objects: the game stopped at its first script (RPG Maker MV, 0.1.77,
 * 06/11/2026). These do what a game's own code asks of them on a phone: events and streams work in the page, a
 * stream's output goes to the console, there is no terminal, and a worker thread cannot start (an error its caller
 * can catch).
 */
(function () {
    'use strict';
    if (window.__agvnNodeModules) return;

    function EventEmitter() { this._events = {}; }
    EventEmitter.prototype.on = EventEmitter.prototype.addListener = function (name, fn) {
        if (!this._events) this._events = {};
        (this._events[name] = this._events[name] || []).push(fn);
        return this;
    };
    EventEmitter.prototype.prependListener = EventEmitter.prototype.on;
    EventEmitter.prototype.once = function (name, fn) {
        var self = this;
        function once() { self.off(name, once); fn.apply(self, arguments); }
        once.listener = fn;
        return this.on(name, once);
    };
    EventEmitter.prototype.off = EventEmitter.prototype.removeListener = function (name, fn) {
        var list = this._events && this._events[name];
        if (list) this._events[name] = list.filter(function (f) { return f !== fn && f.listener !== fn; });
        return this;
    };
    EventEmitter.prototype.removeAllListeners = function (name) {
        if (!this._events) this._events = {};
        if (name === undefined) this._events = {}; else delete this._events[name];
        return this;
    };
    EventEmitter.prototype.emit = function (name) {
        var list = this._events && this._events[name], args = Array.prototype.slice.call(arguments, 1);
        if (!list || !list.length) {
            if (name === 'error') throw args[0] instanceof Error ? args[0] : new Error(String(args[0]));
            return false;
        }
        list.slice().forEach(function (f) { f.apply(this, args); }, this);
        return true;
    };
    EventEmitter.prototype.listeners = function (name) { return ((this._events && this._events[name]) || []).slice(); };
    EventEmitter.prototype.listenerCount = function (name) { return this.listeners(name).length; };
    EventEmitter.prototype.setMaxListeners = function () { return this; };
    EventEmitter.prototype.getMaxListeners = function () { return 10; };
    EventEmitter.EventEmitter = EventEmitter;
    EventEmitter.defaultMaxListeners = 10;
    EventEmitter.once = function (em, name) { return new Promise(function (ok) { em.once(name, function () { ok(Array.prototype.slice.call(arguments)); }); }); };

    function inherits(ctor, parent) {
        ctor.super_ = parent;
        ctor.prototype = Object.create(parent.prototype, { constructor: { value: ctor, writable: true, configurable: true } });
    }

    function inspect(v) {
        if (typeof v === 'string') return v;
        try { return JSON.stringify(v); } catch (e) { return String(v); }
    }

    function format(f) {
        var args = Array.prototype.slice.call(arguments, 1);
        if (typeof f !== 'string') return [f].concat(args).map(inspect).join(' ');
        var out = f.replace(/%[sdifjoO%]/g, function (m) {
            if (m === '%%') return '%';
            if (!args.length) return m;
            var a = args.shift();
            return m === '%d' || m === '%i' ? String(parseInt(a, 10)) : m === '%f' ? String(parseFloat(a)) : m === '%s' ? String(a) : inspect(a);
        });
        return args.length ? out + ' ' + args.map(inspect).join(' ') : out;
    }

    var util = {
        inherits: inherits, format: format, inspect: inspect,
        deprecate: function (fn) { return fn; },
        debuglog: function () { return function () {}; },
        promisify: function (fn) {
            return function () {
                var args = Array.prototype.slice.call(arguments), self = this;
                return new Promise(function (ok, fail) { fn.apply(self, args.concat(function (e, v) { if (e) fail(e); else ok(v); })); });
            };
        },
        isArray: Array.isArray,
        types: { isPromise: function (v) { return v instanceof Promise; }, isDate: function (v) { return v instanceof Date; },
            isRegExp: function (v) { return v instanceof RegExp; } },
        TextEncoder: window.TextEncoder, TextDecoder: window.TextDecoder
    };

    function fail(message) { var e = new Error(message || 'Assertion failed'); e.code = 'ERR_ASSERTION'; throw e; }
    function assert(v, message) { if (!v) fail(message); }
    assert.ok = assert;
    assert.equal = function (a, b, m) { if (a != b) fail(m || a + ' == ' + b); }; // eslint-disable-line eqeqeq
    assert.notEqual = function (a, b, m) { if (a == b) fail(m || a + ' != ' + b); }; // eslint-disable-line eqeqeq
    assert.strictEqual = function (a, b, m) { if (a !== b) fail(m || a + ' === ' + b); };
    assert.notStrictEqual = function (a, b, m) { if (a === b) fail(m || a + ' !== ' + b); };
    assert.deepEqual = assert.deepStrictEqual = function (a, b, m) { if (inspect(a) !== inspect(b)) fail(m); };
    assert.fail = fail;
    assert.throws = function (fn, m) { try { fn(); } catch (e) { return; } fail(typeof m === 'string' ? m : 'Missing expected exception'); };
    assert.strict = assert;

    // streams: what a logger or a parser does with them in the page; a Writable without its own write goes to console
    function Stream(opts) { EventEmitter.call(this); this.writable = this.readable = true; this._opts = opts || {}; }
    inherits(Stream, EventEmitter);
    Stream.prototype.pipe = function (dest) { this.on('data', function (d) { dest.write(d); }); return dest; };
    function Readable(opts) { Stream.call(this, opts); }
    inherits(Readable, Stream);
    Readable.prototype.push = function (chunk) { if (chunk === null) this.emit('end'); else this.emit('data', chunk); return true; };
    Readable.prototype.read = function () { return null; };
    Readable.prototype.resume = Readable.prototype.pause = function () { return this; };
    Readable.prototype.setEncoding = function () { return this; };
    function Writable(opts) {
        Stream.call(this, opts);
        if (opts && typeof opts.write === 'function') this._write = opts.write;
    }
    inherits(Writable, Stream);
    Writable.prototype._write = function (chunk, enc, cb) { console.log(String(chunk).replace(/\n$/, '')); cb(); };
    Writable.prototype.write = function (chunk, enc, cb) {
        if (typeof enc === 'function') { cb = enc; enc = 'utf8'; }
        var self = this;
        this._write(chunk, enc, function (e) { if (e) self.emit('error', e); if (typeof cb === 'function') cb(e); });
        return true;
    };
    Writable.prototype.end = function (chunk, enc, cb) {
        if (typeof chunk === 'function') { cb = chunk; chunk = null; }
        if (chunk !== null && chunk !== undefined) this.write(chunk, enc);
        this.writable = false;
        this.emit('finish');
        this.emit('close');
        if (typeof cb === 'function') cb();
        return this;
    };
    Writable.prototype.cork = Writable.prototype.uncork = function () {};
    Writable.prototype.destroy = function () { this.writable = false; this.emit('close'); return this; };
    function Transform(opts) {
        Writable.call(this, opts);
        if (opts && typeof opts.transform === 'function') this._transform = opts.transform;
    }
    inherits(Transform, Writable);
    Transform.prototype._transform = function (chunk, enc, cb) { cb(null, chunk); };
    Transform.prototype._write = function (chunk, enc, cb) {
        var self = this;
        this._transform(chunk, enc, function (e, out) { if (out !== undefined && out !== null) self.emit('data', out); cb(e); });
    };
    Transform.prototype.push = Readable.prototype.push;
    function PassThrough(opts) { Transform.call(this, opts); }
    inherits(PassThrough, Transform);
    var stream = Stream;
    stream.Stream = Stream;
    stream.Readable = Readable;
    stream.Writable = Writable;
    stream.Duplex = Transform;
    stream.Transform = Transform;
    stream.PassThrough = PassThrough;
    stream.finished = function (s, cb) { s.on('finish', function () { cb(); }); s.on('error', cb); };
    stream.pipeline = function () {
        var list = Array.prototype.slice.call(arguments), cb = typeof list[list.length - 1] === 'function' ? list.pop() : null;
        for (var i = 0; i + 1 < list.length; i++) list[i].pipe(list[i + 1]);
        if (cb) stream.finished(list[list.length - 1], cb);
        return list[list.length - 1];
    };

    function StringDecoder() {}
    StringDecoder.prototype.write = function (b) { return typeof b === 'string' ? b : new TextDecoder().decode(b); };
    StringDecoder.prototype.end = function (b) { return b ? this.write(b) : ''; };

    var url = {
        URL: window.URL, URLSearchParams: window.URLSearchParams,
        fileURLToPath: function (u) { return decodeURIComponent(String(u && u.href || u).replace(/^file:\/\//, '')); },
        pathToFileURL: function (p) { return new URL('file://' + encodeURI(String(p))); },
        parse: function (u) { try { var x = new URL(u, location.href); return { href: x.href, protocol: x.protocol, host: x.host,
            hostname: x.hostname, port: x.port, pathname: x.pathname, search: x.search, hash: x.hash, query: x.search.slice(1) }; }
            catch (e) { return { href: String(u), pathname: String(u) }; } },
        format: function (o) { return typeof o === 'string' ? o : (o.protocol ? o.protocol + '//' : '') + (o.host || o.hostname || '')
            + (o.pathname || '') + (o.search || ''); }
    };

    var noThread = function () { throw new Error('worker_threads không có trên điện thoại (AGVN Player)'); };

    window.__agvnNodeModules = {
        events: EventEmitter, util: util, assert: assert, stream: stream, string_decoder: { StringDecoder: StringDecoder },
        url: url, tty: { isatty: function () { return false; } },
        worker_threads: { isMainThread: true, parentPort: null, workerData: null, threadId: 0, Worker: noThread },
        module: { createRequire: function () { return window.require; }, builtinModules: [] }
    };
})();
