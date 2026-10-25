/* AGVN Player - require('crypto') for games written for NW.js, run as a page ("Chạy nhẹ"). Copyright (c) 2026 agvn.io - MIT License.
 * A plugin of an RPG Maker MZ game decrypted its settings (a hex file beside Game.exe) with
 * crypto.createDecipheriv('aes-256-cbc', ...) as it loaded (POCO, 26/10/2026). As Node has them, synchronously:
 * createCipheriv / createDecipheriv for aes-128/192/256 in cbc, ecb and ctr (PKCS#7 padding, setAutoPadding),
 * createCipher / createDecipher (a password, through EVP_BytesToKey), with AES from html-node-aes.js; createHash and
 * createHmac (html-node-hash.js),
 * pbkdf2Sync, randomBytes, randomUUID, timingSafeEqual. Kept in window.__agvnNodeCrypto for html-compat.js's require.
 */
(function (g) {
    'use strict';
    var Buffer = g.Buffer, hashes = g.__agvnNodeHash, aes = g.__agvnNodeAes;
    if (!Buffer || !hashes || !aes) return;
    var expand = aes.expand, block = aes.block;
    function bytes(v, e) { return v instanceof Uint8Array && !e ? v : Buffer.from(v, e); }

    function Cipher(name, key, iv, decrypt) {
        var m = /^aes-?(128|192|256)(?:-(cbc|ecb|ctr))?$/i.exec(String(name));
        if (!m) throw new Error('AGVN Player chưa hỗ trợ "' + name + '" của Node crypto (chỉ có aes-128/192/256 cbc, ecb, ctr)');
        key = bytes(key);
        iv = iv == null ? null : bytes(iv);
        this.mode = (m[2] || 'cbc').toLowerCase();
        if (key.length !== Number(m[1]) / 8) throw new RangeError('Invalid key length');
        if (this.mode !== 'ecb' && (!iv || iv.length !== 16)) throw new TypeError('Invalid initialization vector');
        this.k = expand(key);
        this.chain = this.mode === 'ecb' ? null : Uint8Array.from(iv);
        this.decrypt = decrypt;
        this.padding = true;
        this.left = new Uint8Array(0);
        this.stream = new Uint8Array(16);
        this.used = 16;
    }

    Cipher.prototype.run = function (data) { // whole blocks, or any length in ctr
        var out = new Uint8Array(data.length), s = new Uint8Array(16);
        for (var at = 0; at < data.length; at += this.mode === 'ctr' ? 1 : 16) {
            if (this.mode === 'ctr') {
                if (this.used === 16) {
                    this.stream.set(this.chain);
                    block(this.k, this.stream, false);
                    for (var n = 15; n >= 0 && ++this.chain[n] === 256; n--) this.chain[n] = 0;
                    this.used = 0;
                }
                out[at] = data[at] ^ this.stream[this.used++];
                continue;
            }
            s.set(data.subarray(at, at + 16));
            var input = s.slice();
            if (this.chain && !this.decrypt) for (var i = 0; i < 16; i++) s[i] ^= this.chain[i];
            block(this.k, s, this.decrypt);
            if (this.chain && this.decrypt) for (i = 0; i < 16; i++) s[i] ^= this.chain[i];
            if (this.chain) this.chain.set(this.decrypt ? input : s);
            out.set(s, at);
        }
        return out;
    };

    Cipher.prototype.update = function (data, inEnc, outEnc) {
        var input = bytes(data, inEnc), result;
        if (this.mode === 'ctr') result = this.run(input);
        else {
            var all = new Uint8Array(this.left.length + input.length);
            all.set(this.left);
            all.set(input, this.left.length);
            var whole = all.length - all.length % 16;
            if (this.decrypt && this.padding && whole === all.length && whole > 0) whole -= 16; // its padding comes in final()
            result = this.run(all.subarray(0, whole));
            this.left = all.slice(whole);
        }
        return outEnc ? Buffer.from(result).toString(outEnc) : Buffer.from(result);
    };

    Cipher.prototype.final = function (outEnc) {
        var result = new Uint8Array(0), left = this.left;
        this.left = new Uint8Array(0);
        if (this.mode !== 'ctr' && !this.decrypt && this.padding) {
            var pad = 16 - left.length % 16, padded = new Uint8Array(left.length + pad);
            padded.set(left);
            padded.fill(pad, left.length);
            result = this.run(padded);
        } else if (this.mode !== 'ctr' && left.length) {
            if (left.length % 16 || !this.decrypt || !this.padding) throw new Error('wrong final block length');
            result = this.run(left);
        }
        if (this.mode !== 'ctr' && this.decrypt && this.padding) {
            var p = result.length ? result[result.length - 1] : 0;
            for (var i = 1; i <= p; i++) if (p > 16 || result[result.length - i] !== p) p = 0;
            if (!p) throw new Error('bad decrypt');
            result = result.subarray(0, result.length - p);
        }
        return outEnc ? Buffer.from(result).toString(outEnc) : Buffer.from(result);
    };

    Cipher.prototype.setAutoPadding = function (on) { this.padding = on !== false; return this; };

    function Hash(name, key) {
        this.fn = hashes.find(name);
        if (!this.fn) throw new Error('AGVN Player chưa hỗ trợ hash "' + name + '" của Node crypto (chỉ có ' + hashes.names.join(', ') + ')');
        this.key = key === undefined ? null : bytes(key);
        this.parts = [];
    }
    Hash.prototype.update = function (data, e) { this.parts.push(bytes(data, e)); return this; };
    Hash.prototype.digest = function (e) {
        var data = Buffer.concat(this.parts), d = Buffer.from(this.key ? hashes.hmac(this.fn, this.key, data) : this.fn(data));
        return e ? d.toString(e) : d;
    };

    /** OpenSSL's EVP_BytesToKey with MD5 and no salt: the key and iv Node's createCipher makes from a password. */
    function fromPassword(name, password) {
        var bits = Number((/(128|192|256)/.exec(String(name)) || [0, 256])[1]), md5 = hashes.find('md5'), all = [], last = new Uint8Array(0);
        password = bytes(password);
        while (Buffer.concat(all).length < bits / 8 + 16) all.push(last = md5(Buffer.concat([last, password])));
        var keyIv = Buffer.concat(all);
        return [keyIv.subarray(0, bits / 8), keyIv.subarray(bits / 8, bits / 8 + 16)];
    }

    function randomBytes(n, cb) {
        var out = Buffer.alloc(n);
        for (var at = 0; at < n; at += 65536) g.crypto.getRandomValues(out.subarray(at, Math.min(n, at + 65536)));
        if (typeof cb === 'function') { setTimeout(function () { cb(null, out); }, 0); return undefined; }
        return out;
    }

    g.__agvnNodeCrypto = {
        createCipheriv: function (name, key, iv) { return new Cipher(name, key, iv, false); },
        createDecipheriv: function (name, key, iv) { return new Cipher(name, key, iv, true); },
        createCipher: function (name, password) { var k = fromPassword(name, password); return new Cipher(name, k[0], k[1], false); },
        createDecipher: function (name, password) { var k = fromPassword(name, password); return new Cipher(name, k[0], k[1], true); },
        createHash: function (name) { return new Hash(name); },
        createHmac: function (name, key) { return new Hash(name, key); },
        pbkdf2Sync: function (password, salt, iterations, length, digest) {
            var fn = hashes.find(digest || 'sha1');
            if (!fn) throw new Error('AGVN Player chưa hỗ trợ hash "' + digest + '" của Node crypto');
            return Buffer.from(hashes.pbkdf2(fn, bytes(password), bytes(salt), iterations, length));
        },
        randomBytes: randomBytes,
        randomUUID: function () { return g.crypto.randomUUID(); },
        getRandomValues: function (a) { return g.crypto.getRandomValues(a); },
        timingSafeEqual: function (a, b) {
            if (a.length !== b.length) throw new RangeError('Input buffers must have the same byte length');
            var d = 0;
            for (var i = 0; i < a.length; i++) d |= a[i] ^ b[i];
            return d === 0;
        },
        getHashes: function () { return hashes.names.slice(); },
        getCiphers: function () { return ['aes-128-cbc', 'aes-192-cbc', 'aes-256-cbc', 'aes-128-ecb', 'aes-192-ecb', 'aes-256-ecb',
            'aes-128-ctr', 'aes-192-ctr', 'aes-256-ctr']; }
    };
})(typeof window !== 'undefined' ? window : this);
