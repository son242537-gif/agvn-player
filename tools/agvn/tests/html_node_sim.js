/* Checks assets/agvn/html-node-*.js (Node's Buffer and require('crypto') for page games) against Node's own: the same
 * bytes for every encoding, hash, HMAC, PBKDF2 and AES mode, then the plugin of the 26/10/2026 report through
 * html-compat.js's require. Copyright (c) 2026 agvn.io - MIT License. */
const fs = require('fs'), vm = require('vm'), assert = require('assert'), path = require('path'), node = require('crypto');
const dir = path.join(__dirname, '..', '..', '..', 'app', 'src', 'main', 'assets', 'agvn');
const ctx = { TextEncoder, TextDecoder, atob, btoa, crypto: node.webcrypto, setTimeout, setInterval, clearInterval,
  console: { log: () => {}, warn: () => {}, error: () => {} },
  document: { title: 't', getElementById: () => null, hasFocus: () => true },
  localStorage: { getItem: () => null, setItem: () => {}, removeItem: () => {} }, addEventListener: () => {} };
ctx.window = ctx;
vm.createContext(ctx);
for (const f of ['html-node-buffer.js', 'html-node-hash.js', 'html-node-aes.js', 'html-node-crypto.js', 'html-compat.js'])
  vm.runInContext(fs.readFileSync(path.join(dir, f), 'utf8'), ctx, { filename: f });
const B = ctx.Buffer, C = ctx.__agvnNodeCrypto;
const hex = b => Buffer.from(b).toString('hex'), rnd = n => node.randomBytes(n), sizes = [0, 1, 15, 16, 17, 31, 32, 55, 56, 63, 64, 65, 100, 1000];

// Buffer: every encoding both ways, as Node's
const text = 'Xin chào, trò chơi! 漢字 😀';
for (const n of sizes) {
  const raw = rnd(n);
  for (const e of ['hex', 'base64', 'base64url', 'latin1', 'utf16le']) {
    assert.strictEqual(B.from(raw).toString(e), raw.toString(e), e + ' out, ' + n);
    assert.strictEqual(hex(B.from(raw.toString(e), e)), hex(Buffer.from(raw.toString(e), e)), e + ' in, ' + n);
  }
}
for (const e of ['utf8', 'latin1', 'ascii', 'utf16le'])
  assert.strictEqual(hex(B.from(text, e)), hex(Buffer.from(text, e)), e);
assert.strictEqual(B.from(text).toString(), text);
assert.strictEqual(B.from(Buffer.from(text).toString('base64').replace(/=+$/, '') + '\n', 'base64').toString(), text);
assert.strictEqual(B.concat([B.from('ab'), B.from([0x63]), new Uint8Array([100])]).toString(), 'abcd');
assert.strictEqual(B.concat([B.from('abcdef')], 3).toString(), 'abc');
assert.strictEqual(B.alloc(5, 'ab').toString(), 'ababa');
assert.ok(B.isBuffer(B.from('x').slice(0, 1)), 'a slice is a Buffer');
const num = rnd(16);
for (const m of ['readUInt8', 'readInt8', 'readUInt16LE', 'readUInt16BE', 'readInt32LE', 'readUInt32BE', 'readFloatLE', 'readDoubleBE'])
  assert.strictEqual(B.from(num)[m](0), num[m](0), m);
const w = B.alloc(8); w.writeUInt32BE(0xdeadbeef, 0); w.writeInt16LE(-2, 4);
assert.strictEqual(hex(w), 'deadbeeffeff0000');
assert.deepStrictEqual(JSON.parse(JSON.stringify(B.from('hi'))), { type: 'Buffer', data: [104, 105] });
assert.strictEqual(B.from(B.from('hi').toJSON()).toString(), 'hi');
assert.strictEqual(new B('6869', 'hex').toString(), 'hi', 'the old new Buffer(string, encoding)');

// hashes, HMAC and PBKDF2
for (const n of sizes) {
  const data = rnd(n);
  for (const h of ['md5', 'sha1', 'sha256']) {
    assert.strictEqual(C.createHash(h).update(B.from(data)).digest('hex'), node.createHash(h).update(data).digest('hex'), h + ' ' + n);
    for (const k of [0, 10, 64, 100]) {
      const key = rnd(k);
      assert.strictEqual(C.createHmac(h, B.from(key)).update(B.from(data)).digest('base64'),
        node.createHmac(h, key).update(data).digest('base64'), 'hmac ' + h + ' ' + n + ' ' + k);
    }
  }
}
assert.strictEqual(C.createHash('SHA-256').update('abc').update('def', 'utf8').digest('hex'), node.createHash('sha256').update('abcdef').digest('hex'));
assert.strictEqual(hex(C.pbkdf2Sync('mat-khau', 'muoi', 1000, 40, 'sha256')), hex(node.pbkdf2Sync('mat-khau', 'muoi', 1000, 40, 'sha256')));
assert.throws(() => C.createHash('sha512'), /chưa hỗ trợ/);

// AES: what Node encrypts the app decrypts, and the other way round, in pieces too
for (const bits of [128, 192, 256]) {
  for (const mode of ['cbc', 'ecb', 'ctr']) {
    const name = 'aes-' + bits + '-' + mode;
    for (const n of sizes) {
      const key = rnd(bits / 8), iv = mode === 'ecb' ? null : rnd(16), data = rnd(n);
      const sealer = node.createCipheriv(name, key, iv), theirs = Buffer.concat([sealer.update(data), sealer.final()]);
      const enc = C.createCipheriv(name, B.from(key), iv && B.from(iv));
      const ours = B.concat([enc.update(B.from(data.subarray(0, 7))), enc.update(B.from(data.subarray(7))), enc.final()]);
      const full = node.createCipheriv(name, key, iv);
      assert.strictEqual(hex(ours), hex(Buffer.concat([full.update(data), full.final()])), name + ' encrypt ' + n);
      const dec = C.createDecipheriv(name, B.from(key), iv && B.from(iv));
      assert.strictEqual(hex(B.concat([dec.update(theirs.toString('hex'), 'hex'), dec.final()])), hex(data), name + ' decrypt ' + n);
    }
  }
}
const key = rnd(32), iv = rnd(16), block = rnd(32);
const raw = node.createCipheriv('aes-256-cbc', key, iv).setAutoPadding(false);
const noPad = Buffer.concat([raw.update(block), raw.final()]);
const ours = C.createDecipheriv('aes-256-cbc', B.from(key), B.from(iv)).setAutoPadding(false);
assert.strictEqual(hex(B.concat([ours.update(B.from(noPad)), ours.final()])), hex(block), 'no padding');
const right = node.createCipheriv('aes-256-cbc', Buffer.alloc(32, 1), Buffer.alloc(16, 3));
const abc = Buffer.concat([right.update('abc'), right.final()]), wrong = C.createDecipheriv('aes-256-cbc', B.alloc(32, 2), B.alloc(16, 3));
wrong.update(B.from(abc));
assert.throws(() => wrong.final(), /bad decrypt/, 'the wrong key, as Node says it');
const md5 = b => node.createHash('md5').update(b).digest(); // OpenSSL's EVP_BytesToKey, as Node's createCipher had it
const d1 = md5(Buffer.from('pw')), d2 = md5(Buffer.concat([d1, Buffer.from('pw')])), d3 = md5(Buffer.concat([d2, Buffer.from('pw')]));
const evp = Buffer.concat([d1, d2, d3]), plain = Buffer.from('cấu hình');
const old = node.createCipheriv('aes-256-cbc', evp.subarray(0, 32), evp.subarray(32, 48));
const sealed = Buffer.concat([old.update(plain), old.final()]);
const open = C.createDecipher('aes-256-cbc', 'pw');
assert.strictEqual(B.concat([open.update(B.from(sealed)), open.final()]).toString(), 'cấu hình', 'createDecipher');
assert.throws(() => C.createCipheriv('aes-256-gcm', B.from(key), B.from(iv)), /chưa hỗ trợ/);
assert.throws(() => C.createCipheriv('aes-256-cbc', B.from(rnd(16)), B.from(iv)), /Invalid key length/);
assert.strictEqual(C.randomBytes(70000).length, 70000);

// the plugin of the report, through require as html-compat.js gives it
const settings = JSON.stringify({ ten: 'Trò chơi', mucDo: 3 }), k = '0123456789abcdef0123456789abcdef', bufferKey = 'abcdef9876543210';
const c = node.createCipheriv('aes-256-cbc', Buffer.from(k), Buffer.from(bufferKey));
const encryptedHex = Buffer.concat([c.update(settings), c.final()]).toString('hex');
ctx.encryptedHex = encryptedHex; ctx.k = k; ctx.bufferKey = bufferKey;
const result = vm.runInContext(`(function () {
  const crypto = require("crypto");
  const iv = Buffer.from(bufferKey);
  const decipher = crypto.createDecipheriv("aes-256-cbc", Buffer.from(k), iv);
  let out = decipher.update(Buffer.from(encryptedHex, "hex"));
  out = Buffer.concat([out, decipher.final()]);
  return JSON.parse(out.toString());
})()`, ctx);
assert.deepStrictEqual(JSON.parse(JSON.stringify(result)), { ten: 'Trò chơi', mucDo: 3 });
assert.strictEqual(vm.runInContext('require("buffer").Buffer === Buffer', ctx), true);
assert.strictEqual(vm.runInContext('typeof require("zlib").inflateSync', ctx), 'undefined', 'a module the app has not');
assert.deepStrictEqual(Array.from(ctx.__agvnMissingModules), ['zlib']);
assert.throws(() => vm.runInContext('require("child_process").execSync("start https://agvn.io")', ctx), /không có trên điện thoại/);
console.log('html-node ok');
