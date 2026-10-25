/* AGVN Player - AES for html-node-crypto.js (Node's createCipheriv on a page game), in plain JavaScript: a plugin calls
 * it synchronously, and the browser's crypto.subtle answers only later. A key's schedule and one 16-byte block either
 * way, kept in window.__agvnNodeAes. Copyright (c) 2026 agvn.io - MIT License.
 */
(function (g) {
    'use strict';
    var SBOX = new Uint8Array(256), INV = new Uint8Array(256), MUL = {};
    (function () { // as CryptoJS builds them
        var d = [], x = 0, xi = 0;
        for (var i = 0; i < 256; i++) d[i] = i < 128 ? i << 1 : (i << 1) ^ 0x11b;
        for (i = 0; i < 256; i++) {
            var s = xi ^ (xi << 1) ^ (xi << 2) ^ (xi << 3) ^ (xi << 4);
            s = (s >>> 8) ^ (s & 0xff) ^ 0x63;
            SBOX[x] = s;
            INV[s] = x;
            var x2 = d[x], x8 = d[d[x2]];
            if (!x) { x = xi = 1; } else { x = x2 ^ d[d[d[x8 ^ x2]]]; xi ^= d[d[xi]]; }
        }
        [1, 2, 3, 9, 11, 13, 14].forEach(function (m) {
            var t = MUL[m] = new Uint8Array(256);
            for (var b = 0; b < 256; b++) {
                var r = 0, a = b, k = m;
                while (k) { if (k & 1) r ^= a; a = (a << 1) ^ (a & 0x80 ? 0x11b : 0); k >>= 1; }
                t[b] = r;
            }
        });
    })();

    function expand(key) {
        var nk = key.length / 4, nr = nk + 6, w = new Uint8Array(16 * (nr + 1)), t = new Uint8Array(4), rcon = 1;
        w.set(key);
        for (var i = nk; i < 4 * (nr + 1); i++) {
            t.set(w.subarray(4 * (i - 1), 4 * i));
            if (i % nk === 0) {
                var first = t[0];
                t[0] = SBOX[t[1]] ^ rcon; t[1] = SBOX[t[2]]; t[2] = SBOX[t[3]]; t[3] = SBOX[first];
                rcon = MUL[2][rcon];
            } else if (nk > 6 && i % nk === 4) {
                for (var j = 0; j < 4; j++) t[j] = SBOX[t[j]];
            }
            for (j = 0; j < 4; j++) w[4 * i + j] = w[4 * (i - nk) + j] ^ t[j];
        }
        return { w: w, nr: nr };
    }

    /** One 16-byte block (state byte r + 4c is row r, column c), in place. */
    function block(k, s, decrypt) {
        var nr = k.nr, w = k.w, t = new Uint8Array(16), r, i;
        var key = function (round) { for (var b = 0; b < 16; b++) s[b] ^= w[round * 16 + b]; };
        var shift = function (inverse) {
            for (var row = 0; row < 4; row++) {
                for (var col = 0; col < 4; col++) {
                    var to = row + 4 * col, from = row + 4 * ((col + row) % 4);
                    t[inverse ? from : to] = s[inverse ? to : from];
                }
            }
            s.set(t);
        };
        var mix = function (m) {
            for (var c = 0; c < 16; c += 4) {
                var a0 = s[c], a1 = s[c + 1], a2 = s[c + 2], a3 = s[c + 3];
                s[c] = MUL[m[0]][a0] ^ MUL[m[1]][a1] ^ MUL[m[2]][a2] ^ MUL[m[3]][a3];
                s[c + 1] = MUL[m[3]][a0] ^ MUL[m[0]][a1] ^ MUL[m[1]][a2] ^ MUL[m[2]][a3];
                s[c + 2] = MUL[m[2]][a0] ^ MUL[m[3]][a1] ^ MUL[m[0]][a2] ^ MUL[m[1]][a3];
                s[c + 3] = MUL[m[1]][a0] ^ MUL[m[2]][a1] ^ MUL[m[3]][a2] ^ MUL[m[0]][a3];
            }
        };
        if (!decrypt) {
            key(0);
            for (r = 1; r <= nr; r++) {
                for (i = 0; i < 16; i++) s[i] = SBOX[s[i]];
                shift(false);
                if (r < nr) mix([2, 3, 1, 1]);
                key(r);
            }
            return;
        }
        key(nr);
        for (var round = nr - 1; round >= 0; round--) {
            shift(true);
            for (i = 0; i < 16; i++) s[i] = INV[s[i]];
            key(round);
            if (round > 0) mix([14, 11, 13, 9]);
        }
    }

    g.__agvnNodeAes = { expand: expand, block: block };
})(typeof window !== 'undefined' ? window : this);
