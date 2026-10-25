/* AGVN Player - Node's Buffer for games written for NW.js, run as a page ("Chạy nhẹ"). Copyright (c) 2026 agvn.io - MIT License.
 * NW.js gives every script Node's Buffer; a phone browser has none, and a plugin that read its settings with
 * Buffer.from(hex, 'hex') stopped an RPG Maker MZ game with "Buffer is not defined" (POCO, 26/10/2026). A Uint8Array as
 * Node's is, with the encodings games use: utf8, hex, base64 (and base64url), latin1 (binary), ascii, utf16le (ucs2).
 * Loaded before html-node-hash.js, html-node-crypto.js and html-compat.js.
 */
(function (g) {
    'use strict';
    if (typeof g.Buffer === 'function') return;
    var NAMES = { utf8: 'utf8', 'utf-8': 'utf8', hex: 'hex', base64: 'base64', base64url: 'base64url', latin1: 'latin1',
        binary: 'latin1', ascii: 'ascii', utf16le: 'utf16le', 'utf-16le': 'utf16le', ucs2: 'utf16le', 'ucs-2': 'utf16le' };

    function encoding(e) {
        var name = NAMES[String(e || 'utf8').toLowerCase()];
        if (!name) throw new TypeError('Unknown encoding: ' + e);
        return name;
    }

    function latin1(s) {
        var out = new Uint8Array(s.length);
        for (var i = 0; i < s.length; i++) out[i] = s.charCodeAt(i) & 0xff;
        return out;
    }

    function encode(s, e) {
        s = String(s);
        switch (encoding(e)) {
            case 'hex': {
                var n = s.length >>> 1, hex = new Uint8Array(n);
                for (var i = 0; i < n; i++) {
                    var b = parseInt(s.substr(i * 2, 2), 16);
                    if (isNaN(b)) return hex.subarray(0, i); // Node stops at the first pair that is no hex
                    hex[i] = b;
                }
                return hex;
            }
            case 'base64':
            case 'base64url': { // as lenient as Node: url letters, spaces and a missing "=" are fine
                var t = s.replace(/-/g, '+').replace(/_/g, '/').split('=')[0].replace(/[^A-Za-z0-9+/]/g, '');
                if (t.length % 4 === 1) t = t.slice(0, -1);
                while (t.length % 4) t += '=';
                return latin1(g.atob(t));
            }
            case 'latin1':
            case 'ascii':
                return latin1(s);
            case 'utf16le': {
                var u = new Uint8Array(s.length * 2);
                for (var j = 0; j < s.length; j++) { u[j * 2] = s.charCodeAt(j) & 0xff; u[j * 2 + 1] = s.charCodeAt(j) >>> 8; }
                return u;
            }
            default:
                return new g.TextEncoder().encode(s);
        }
    }

    function chars(bytes, mask) {
        var out = '';
        for (var i = 0; i < bytes.length; i += 8192) {
            var part = bytes.subarray(i, i + 8192);
            out += String.fromCharCode.apply(null, mask ? Array.prototype.map.call(part, function (b) { return b & mask; }) : part);
        }
        return out;
    }

    function decode(bytes, e) {
        switch (encoding(e)) {
            case 'hex':
                return Array.prototype.map.call(bytes, function (b) { return (b < 16 ? '0' : '') + b.toString(16); }).join('');
            case 'base64':
                return g.btoa(chars(bytes));
            case 'base64url':
                return g.btoa(chars(bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
            case 'latin1':
                return chars(bytes);
            case 'ascii':
                return chars(bytes, 0x7f);
            case 'utf16le': {
                var s = '';
                for (var i = 0; i + 1 < bytes.length; i += 2) s += String.fromCharCode(bytes[i] | bytes[i + 1] << 8);
                return s;
            }
            default:
                return new g.TextDecoder('utf-8').decode(bytes);
        }
    }

    function bytesOf(v, e) {
        if (typeof v === 'string') return encode(v, e);
        if (v && v.type === 'Buffer' && Array.isArray(v.data)) return new Uint8Array(v.data); // a Buffer's toJSON
        if (v instanceof DataView) return new Uint8Array(v.buffer, v.byteOffset, v.byteLength);
        if (v && typeof v.length === 'number') return new Uint8Array(v); // a copy, as Node makes
        throw new TypeError('The first argument must be a string, Buffer, ArrayBuffer, Array or array-like object');
    }

    class Buffer extends Uint8Array {
        constructor(a, b, c) { // also the old new Buffer(string, encoding)
            if (typeof a === 'number' || a instanceof ArrayBuffer) { super(a, b, c); return; }
            var bytes = bytesOf(a, b);
            super(bytes.length);
            this.set(bytes);
        }

        static from(v, e, length) { return v instanceof ArrayBuffer ? new Buffer(v, e || 0, length) : new Buffer(v, e); }
        static alloc(n, fill, e) {
            var b = new Buffer(n);
            return fill === undefined || fill === 0 ? b : b.fill(fill, 0, n, e);
        }
        static allocUnsafe(n) { return new Buffer(n); }
        static allocUnsafeSlow(n) { return new Buffer(n); }
        static isBuffer(v) { return v instanceof Buffer; }
        static isEncoding(e) { return !!NAMES[String(e).toLowerCase()]; }
        static byteLength(v, e) { return typeof v === 'string' ? encode(v, e).length : v.byteLength; }
        static compare(a, b) {
            for (var i = 0; i < Math.min(a.length, b.length); i++) if (a[i] !== b[i]) return a[i] < b[i] ? -1 : 1;
            return a.length === b.length ? 0 : a.length < b.length ? -1 : 1;
        }
        static concat(list, total) {
            if (total === undefined) total = list.reduce(function (n, b) { return n + b.length; }, 0);
            var out = Buffer.alloc(total), at = 0;
            for (var i = 0; i < list.length && at < total; i++) {
                var part = list[i].subarray(0, total - at);
                out.set(part, at);
                at += part.length;
            }
            return out;
        }

        toString(e, start, end) { return decode(this.subarray(start || 0, end === undefined ? this.length : end), e); }
        toJSON() { return { type: 'Buffer', data: Array.prototype.slice.call(this) }; }
        equals(o) { return Buffer.compare(this, o) === 0; }
        compare(o) { return Buffer.compare(this, o); }
        slice(start, end) { return this.subarray(start, end); } // a view, as Node's
        copy(target, at, start, end) {
            start = start || 0;
            end = end === undefined ? this.length : end;
            var part = this.subarray(start, Math.min(end, start + target.length - (at || 0)));
            target.set(part, at || 0);
            return part.length;
        }
        write(s, at, length, e) {
            if (typeof at === 'string') { e = at; at = 0; length = undefined; } else if (typeof length === 'string') { e = length; length = undefined; }
            var bytes = encode(s, e).subarray(0, Math.min(length === undefined ? Infinity : length, this.length - (at || 0)));
            this.set(bytes, at || 0);
            return bytes.length;
        }
        fill(v, start, end, e) {
            if (typeof start === 'string') { e = start; start = 0; end = this.length; }
            start = start || 0;
            end = end === undefined ? this.length : end;
            if (typeof v !== 'string' && !(v instanceof Uint8Array)) return Uint8Array.prototype.fill.call(this, v & 0xff, start, end);
            var bytes = typeof v === 'string' ? encode(v, e) : v;
            for (var i = start; i < end; i++) this[i] = bytes.length ? bytes[(i - start) % bytes.length] : 0;
            return this;
        }
        indexOf(v, from, e) {
            if (typeof v === 'number') return Uint8Array.prototype.indexOf.call(this, v & 0xff, from);
            var needle = typeof v === 'string' ? encode(v, e) : v;
            outer: for (var i = from || 0; i + needle.length <= this.length; i++) {
                for (var j = 0; j < needle.length; j++) if (this[i + j] !== needle[j]) continue outer;
                return i;
            }
            return -1;
        }
        includes(v, from, e) { return this.indexOf(v, from, e) !== -1; }
    }

    // readUInt32LE, writeInt16BE, readFloatLE, ... (and the readUint32LE spellings), through a DataView
    [['UInt8', 'Uint8', 1], ['Int8', 'Int8', 1], ['UInt16', 'Uint16', 2], ['Int16', 'Int16', 2], ['UInt32', 'Uint32', 4],
        ['Int32', 'Int32', 4], ['Float', 'Float32', 4], ['Double', 'Float64', 8]].forEach(function (t) {
        (t[2] === 1 ? [''] : ['LE', 'BE']).forEach(function (order) {
            var view = function (b) { return new DataView(b.buffer, b.byteOffset, b.byteLength); }, le = order === 'LE';
            var read = function (at) { return view(this)['get' + t[1]](at || 0, le); };
            var write = function (v, at) { view(this)['set' + t[1]](at || 0, v, le); return (at || 0) + t[2]; };
            [t[0], t[0].replace('UInt', 'Uint')].forEach(function (name) {
                Buffer.prototype['read' + name + order] = read;
                Buffer.prototype['write' + name + order] = write;
            });
        });
    });

    Buffer.poolSize = 8192;
    g.Buffer = Buffer;
})(typeof window !== 'undefined' ? window : this);
