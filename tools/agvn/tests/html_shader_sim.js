/* Checks assets/agvn/html-shader-precision.js: WebGL 2 shaders that use sampler2DShadow (and the other samplers GLSL ES
 * 3.00 gives no default precision) without declaring it get "precision highp <type>;" where GLSL allows it, and every
 * other shader stays as it was. With glslangValidator, each shader is also compiled as GLSL ES 3.00 before and after
 * (mv3d's babylon.js on a POCO F8 Pro: "'sampler2DShadow' : No precision specified", 27/10/2026).
 * Copyright (c) 2026 agvn.io - MIT License. */
const fs = require('fs'), vm = require('vm'), assert = require('assert'), path = require('path'), os = require('os');
const { spawnSync } = require('child_process');
const dir = path.join(__dirname, '..', '..', '..', 'app', 'src', 'main', 'assets', 'agvn');
const sent = [], warned = [];
function Ctx2() {}
Ctx2.prototype.shaderSource = function (shader, src) { sent.push(src); };
function Ctx1() {}
Ctx1.prototype.shaderSource = function (shader, src) { sent.push(src); };
const ctx = { WebGL2RenderingContext: Ctx2, WebGLRenderingContext: Ctx1, console: { warn: m => warned.push(m), log: () => {} } };
ctx.window = ctx;
vm.createContext(ctx);
vm.runInContext(fs.readFileSync(path.join(dir, 'html-shader-precision.js'), 'utf8'), ctx, { filename: 'html-shader-precision.js' });
vm.runInContext(fs.readFileSync(path.join(dir, 'html-shader-precision.js'), 'utf8'), ctx); // loaded twice: wrapped once
const fix = src => { new Ctx2().shaderSource({}, src); return sent[sent.length - 1]; };

const glslang = spawnSync('glslangValidator', ['--version']).status === 0;
function compiles(src, stage) {
  if (!glslang) return null;
  const f = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'agvn-glsl-')), 'shader.' + stage);
  fs.writeFileSync(f, src);
  const r = spawnSync('glslangValidator', [f], { encoding: 'utf8' });
  return { ok: r.status === 0, out: r.stdout + r.stderr };
}

// babylon.js 4.x shadow-map fragment shader (WebGL 2), cut down: no precision for sampler2DShadow
const babylon = `#version 300 es
#define WEBGL2
#define SHADOW0
#define SHADOWPCF0
#ifdef GL_ES
#endif
precision highp float;
uniform sampler2D diffuseSampler;
uniform highp sampler2DShadow shadowSampler0;
in vec2 vUV;
in vec4 vPositionFromLight0;
out vec4 glFragColor;
// a shadow is read through sampler2DShadow in a helper
float computeShadowWithPCF1(vec4 vPositionFromLight, float depthMetric, sampler2DShadow shadowSampler, float darkness)
{
    vec3 clipSpace = vPositionFromLight.xyz / vPositionFromLight.w;
    vec3 uvDepth = vec3(0.5 * clipSpace.xy + vec2(0.5), depthMetric);
    float shadow = texture(shadowSampler, uvDepth);
    return mix(darkness, 1.0, shadow);
}
void main(void) {
    vec4 base = texture(diffuseSampler, vUV);
    float shadow = computeShadowWithPCF1(vPositionFromLight0, 0.5, shadowSampler0, 0.3);
    glFragColor = vec4(base.rgb * shadow, base.a);
}
`;
const before = compiles(babylon, 'frag');
if (before) assert.ok(!before.ok && /precision/i.test(before.out), 'glslang refuses it: ' + before.out);
const fixed = fix(babylon);
assert.notStrictEqual(fixed, babylon);
assert.ok(fixed.includes('precision highp sampler2DShadow;'));
const lines = fixed.split('\n');
assert.strictEqual(lines[0], '#version 300 es', '#version stays first');
assert.strictEqual(lines[1], 'precision highp sampler2DShadow;', 'right after #version, at the top level');
const after = compiles(fixed, 'frag');
if (after) assert.ok(after.ok, 'glslang takes it: ' + after.out);
assert.strictEqual(warned.length, 1);
assert.ok(warned[0].includes('sampler2DShadow'));

// #extension lines (one in an #ifdef) come before any declaration: precision goes after the #endif
const ext = `#version 300 es
#extension GL_EXT_shader_texture_lod : enable
#ifdef GL_EXT_frag_depth
#extension GL_EXT_frag_depth : enable
#endif
precision mediump float;
uniform mediump sampler3D lut;
uniform sampler2DArray layers;
uniform isampler2D ids;
in vec3 p;
out vec4 c;
void main() { c = texture(lut, p) + texture(layers, p) + vec4(texelFetch(ids, ivec2(0), 0)); }
`;
const extFixed = fix(ext), extLines = extFixed.split('\n');
for (const t of ['sampler3D', 'sampler2DArray', 'isampler2D']) assert.ok(extLines.includes('precision highp ' + t + ';'), t);
assert.ok(extLines.indexOf('precision highp sampler3D;') > extLines.indexOf('#endif'));
const extAfter = compiles(extFixed, 'frag');
if (extAfter) assert.ok(extAfter.ok, 'glslang takes it: ' + extAfter.out);

// a vertex shader with no precision line at all
const vert = `#version 300 es
uniform sampler2DArray atlas;
in vec3 pos;
out float h;
void main() { h = texture(atlas, pos).r; gl_Position = vec4(pos, 1.0); }
`;
const vertFixed = fix(vert);
assert.strictEqual(vertFixed.split('\n')[1], 'precision highp sampler2DArray;');
const vertAfter = compiles(vertFixed, 'vert');
if (vertAfter) assert.ok(vertAfter.ok, 'glslang takes it: ' + vertAfter.out);

// left as they are: declared already (three.js r130+), only named in a comment, GLSL ES 1.00, not a string
const three = '#version 300 es\nprecision highp float;\nprecision highp sampler2DShadow;\nuniform sampler2DShadow s;\nin vec3 v;\nout vec4 o;\nvoid main() { o = vec4(texture(s, v)); }\n';
assert.strictEqual(fix(three), three);
const comment = '#version 300 es\nprecision highp float;\n// sampler2DShadow is not used here\nout vec4 o;\nvoid main() { o = vec4(1.0); }\n';
assert.strictEqual(fix(comment), comment);
const es1 = 'precision mediump float;\nuniform sampler2D t;\nvarying vec2 uv;\nvoid main() { gl_FragColor = texture2D(t, uv); }\n';
assert.strictEqual(fix(es1), es1);
new Ctx1().shaderSource({}, es1);
assert.strictEqual(sent[sent.length - 1], es1, 'WebGL 1 too, untouched');
const leading = '// generated\n\n#version 300 es\nuniform usampler2D u;\nout vec4 o;\nvoid main() { o = vec4(texelFetch(u, ivec2(0), 0)); }\n';
assert.ok(fix(leading).includes('precision highp usampler2D;'), 'a comment before #version');
assert.strictEqual(ctx.__agvnShaderPrecision.count, 4);
console.log('html-shader ok' + (glslang ? ' (glslang)' : ' (glslang not installed: not compiled)'));
