/* AGVN Player - the hashes of Node's crypto (md5, sha1, sha256, their HMAC and PBKDF2) for html-node-crypto.js, in plain
 * JavaScript: a plugin calls them synchronously, and the browser's crypto.subtle answers only later. Pure functions on
 * Uint8Array, kept in window.__agvnNodeHash. Copyright (c) 2026 agvn.io - MIT License.
 */
(function (g) {
    'use strict';
    var MD5_T = [], SHA256_H = [], SHA256_K = [];
    (function () {
        for (var i = 0; i < 64; i++) MD5_T[i] = (Math.abs(Math.sin(i + 1)) * 0x100000000) | 0;
        var frac = function (n) { return ((n - (n | 0)) * 0x100000000) | 0; };
        for (var n = 2, k = 0; k < 64; n++) { // the first 64 primes, as SHA-256 takes them
            var prime = true;
            for (var d = 2; d * d <= n; d++) if (n % d === 0) { prime = false; break; }
            if (!prime) continue;
            if (k < 8) SHA256_H[k] = frac(Math.pow(n, 1 / 2));
            SHA256_K[k++] = frac(Math.pow(n, 1 / 3));
        }
    })();

    /** {@code data} padded to 64-byte blocks with its length in bits (big- or little-endian), as 32-bit words. */
    function blocks(data, littleEndian) {
        var n = ((data.length + 8) >> 6) + 1, words = new Int32Array(n * 16), view = new DataView(new ArrayBuffer(n * 64));
        new Uint8Array(view.buffer).set(data);
        view.setUint8(data.length, 0x80);
        var bits = data.length * 8;
        view.setUint32(n * 64 - (littleEndian ? 8 : 4), bits >>> 0, littleEndian);
        view.setUint32(n * 64 - (littleEndian ? 4 : 8), Math.floor(bits / 0x100000000), littleEndian);
        for (var i = 0; i < words.length; i++) words[i] = view.getInt32(i * 4, littleEndian);
        return words;
    }

    function out(h, littleEndian) {
        var view = new DataView(new ArrayBuffer(h.length * 4));
        h.forEach(function (v, i) { view.setInt32(i * 4, v, littleEndian); });
        return new Uint8Array(view.buffer);
    }

    function rotl(x, n) { return (x << n) | (x >>> (32 - n)); }

    function md5(data) {
        var w = blocks(data, true), h = [0x67452301, 0xefcdab89 | 0, 0x98badcfe | 0, 0x10325476];
        var S = [7, 12, 17, 22, 5, 9, 14, 20, 4, 11, 16, 23, 6, 10, 15, 21];
        for (var b = 0; b < w.length; b += 16) {
            var a = h[0], bb = h[1], c = h[2], d = h[3];
            for (var i = 0; i < 64; i++) {
                var f, k, round = i >> 4;
                if (round === 0) { f = (bb & c) | (~bb & d); k = i; }
                else if (round === 1) { f = (d & bb) | (~d & c); k = (5 * i + 1) % 16; }
                else if (round === 2) { f = bb ^ c ^ d; k = (3 * i + 5) % 16; }
                else { f = c ^ (bb | ~d); k = (7 * i) % 16; }
                var t = d;
                d = c;
                c = bb;
                bb = (bb + rotl((a + f + MD5_T[i] + w[b + k]) | 0, S[round * 4 + (i & 3)])) | 0;
                a = t;
            }
            h = [(h[0] + a) | 0, (h[1] + bb) | 0, (h[2] + c) | 0, (h[3] + d) | 0];
        }
        return out(h, true);
    }

    function sha1(data) {
        var w = blocks(data, false), h = [0x67452301, 0xefcdab89 | 0, 0x98badcfe | 0, 0x10325476, 0xc3d2e1f0 | 0], x = new Int32Array(80);
        for (var b = 0; b < w.length; b += 16) {
            for (var i = 0; i < 80; i++) x[i] = i < 16 ? w[b + i] : rotl(x[i - 3] ^ x[i - 8] ^ x[i - 14] ^ x[i - 16], 1);
            var a = h[0], bb = h[1], c = h[2], d = h[3], e = h[4];
            for (i = 0; i < 80; i++) {
                var f = i < 20 ? (bb & c) | (~bb & d) : i < 40 ? bb ^ c ^ d : i < 60 ? (bb & c) | (bb & d) | (c & d) : bb ^ c ^ d;
                var k = i < 20 ? 0x5a827999 : i < 40 ? 0x6ed9eba1 : i < 60 ? 0x8f1bbcdc | 0 : 0xca62c1d6 | 0;
                var t = (rotl(a, 5) + f + e + k + x[i]) | 0;
                e = d;
                d = c;
                c = rotl(bb, 30);
                bb = a;
                a = t;
            }
            h = [(h[0] + a) | 0, (h[1] + bb) | 0, (h[2] + c) | 0, (h[3] + d) | 0, (h[4] + e) | 0];
        }
        return out(h, false);
    }

    function sha256(data) {
        var w = blocks(data, false), h = SHA256_H.slice(), x = new Int32Array(64);
        var rotr = function (v, n) { return (v >>> n) | (v << (32 - n)); };
        for (var b = 0; b < w.length; b += 16) {
            for (var i = 0; i < 64; i++) {
                if (i < 16) { x[i] = w[b + i]; continue; }
                var s0 = rotr(x[i - 15], 7) ^ rotr(x[i - 15], 18) ^ (x[i - 15] >>> 3);
                var s1 = rotr(x[i - 2], 17) ^ rotr(x[i - 2], 19) ^ (x[i - 2] >>> 10);
                x[i] = (x[i - 16] + s0 + x[i - 7] + s1) | 0;
            }
            var v = h.slice();
            for (i = 0; i < 64; i++) {
                var t1 = (v[7] + (rotr(v[4], 6) ^ rotr(v[4], 11) ^ rotr(v[4], 25)) + ((v[4] & v[5]) ^ (~v[4] & v[6])) + SHA256_K[i] + x[i]) | 0;
                var t2 = ((rotr(v[0], 2) ^ rotr(v[0], 13) ^ rotr(v[0], 22)) + ((v[0] & v[1]) ^ (v[0] & v[2]) ^ (v[1] & v[2]))) | 0;
                v = [(t1 + t2) | 0, v[0], v[1], v[2], (v[3] + t1) | 0, v[4], v[5], v[6]];
            }
            for (i = 0; i < 8; i++) h[i] = (h[i] + v[i]) | 0;
        }
        return out(h, false);
    }

    var HASHES = { md5: md5, sha1: sha1, sha256: sha256 };

    /** The hash named as Node names it ("md5", "sha256", "SHA-256"...), or null when the app has none. */
    function find(name) {
        return HASHES[String(name || '').toLowerCase().replace(/^rsa-/, '').replace('-', '')] || null;
    }

    function hmac(hash, key, data) {
        if (key.length > 64) key = hash(key);
        var inner = new Uint8Array(64 + data.length), outer = new Uint8Array(64 + 32);
        for (var i = 0; i < 64; i++) { var k = i < key.length ? key[i] : 0; inner[i] = k ^ 0x36; outer[i] = k ^ 0x5c; }
        inner.set(data, 64);
        var first = hash(inner), all = new Uint8Array(64 + first.length);
        all.set(outer.subarray(0, 64));
        all.set(first, 64);
        return hash(all);
    }

    function pbkdf2(hash, password, salt, iterations, length) {
        var out = new Uint8Array(length), at = 0;
        for (var block = 1; at < length; block++) {
            var s = new Uint8Array(salt.length + 4);
            s.set(salt);
            new DataView(s.buffer).setUint32(salt.length, block, false);
            var u = hmac(hash, password, s), t = u.slice();
            for (var i = 1; i < iterations; i++) {
                u = hmac(hash, password, u);
                for (var j = 0; j < t.length; j++) t[j] ^= u[j];
            }
            out.set(t.subarray(0, length - at), at);
            at += t.length;
        }
        return out;
    }

    g.__agvnNodeHash = { find: find, hmac: hmac, pbkdf2: pbkdf2, names: Object.keys(HASHES) };
})(typeof window !== 'undefined' ? window : this);
