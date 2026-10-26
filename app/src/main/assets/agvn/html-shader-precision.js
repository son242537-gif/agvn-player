/* AGVN Player - precision for the samplers GLSL ES 3.00 gives none, for page games' WebGL 2 shaders. Copyright (c)
 * 2026 agvn.io - MIT License. Loaded before html-compat.js.
 * In a "#version 300 es" shader, sampler2DShadow, samplerCubeShadow, sampler3D, the array samplers and the integer
 * samplers have no default precision, in either stage: a shader that uses one without a "precision ... <type>;" does
 * not compile. ANGLE on D3D11 or desktop GL lets it pass, ANGLE on Vulkan (Android's WebView) does not: an RPG Maker MV
 * game with the mv3d plugin (an old babylon.js whose shadow shaders lack it) went black on every map, logcat full of
 * "'sampler2DShadow' : No precision specified" (POCO F8 Pro, 27/10/2026), while its PC copy ran.
 * So shaderSource() adds "precision highp <type>;" for each such type a shader uses and declares nowhere, after its
 * #extension lines and outside any #if, and leaves every other shader as it is. The console says it once.
 */
(function () {
    'use strict';
    var TYPES = ['sampler2DShadow', 'samplerCubeShadow', 'sampler2DArrayShadow', 'sampler3D', 'sampler2DArray',
        'isampler2D', 'isampler3D', 'isamplerCube', 'isampler2DArray', 'usampler2D', 'usampler3D', 'usamplerCube',
        'usampler2DArray'];
    var VERSION = /^\s*#\s*version\s+300\s+es\b/;

    /** The text without its comments, so that a type named in one is not taken as used. */
    function code(src) {
        return src.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/\/\/[^\n]*/g, '');
    }

    /** The types {@code src} uses and gives no precision to. */
    function missing(src) {
        var c = code(src);
        return TYPES.filter(function (t) {
            return new RegExp('\\b' + t + '\\b').test(c)
                && !new RegExp('\\bprecision\\s+(lowp|mediump|highp)\\s+' + t + '\\s*;').test(c);
        });
    }

    /**
     * The line before which declarations may go: after #version, after the last #extension (which must come before
     * any declaration), and at the top level, outside #if/#ifdef/#ifndef ... #endif.
     */
    function insertAt(lines) {
        var version = -1, lastExt = -1, depth = 0, depths = [], i;
        for (i = 0; i < lines.length; i++) {
            depths.push(depth);
            var l = lines[i].replace(/\/\/.*$/, '').trim();
            if (version < 0 && VERSION.test(l)) version = i;
            if (/^#\s*extension\b/.test(l)) lastExt = i;
            if (/^#\s*if(n?def)?\b/.test(l)) depth++;
            else if (/^#\s*endif\b/.test(l) && depth > 0) depth--;
        }
        depths.push(depth);
        for (i = Math.max(version, lastExt) + 1; i <= lines.length; i++) if (depths[i] === 0) return i;
        return version + 1;
    }

    /** {@code src} with the precisions it lacks, or {@code src} itself; also the types added, for the console. */
    function fix(src) {
        // #version comes first, only blanks and comments before it
        if (typeof src !== 'string' || !VERSION.test(src.replace(/^(?:\s+|\/\/[^\n]*|\/\*[\s\S]*?\*\/)*/, '')))
            return { src: src, added: [] };
        var add = missing(src);
        if (!add.length) return { src: src, added: [] };
        var lines = src.split('\n'), at = insertAt(lines);
        var decl = add.map(function (t) { return 'precision highp ' + t + ';'; });
        lines.splice.apply(lines, [at, 0].concat(decl));
        return { src: lines.join('\n'), added: add };
    }

    window.__agvnShaderPrecision = { count: 0, fix: function (src) { return fix(src).src; } };

    function wrap(proto) {
        if (!proto || typeof proto.shaderSource !== 'function' || proto.shaderSource.__agvn) return;
        var original = proto.shaderSource;
        var patched = function (shader, src) {
            var r = fix(src);
            if (r.added.length) {
                if (window.__agvnShaderPrecision.count++ === 0)
                    console.warn('[AGVN] Shader WebGL 2 thiếu precision cho ' + r.added.join(', ') +
                        ': app đã thêm (WebView của Android bắt buộc khai, máy tính thì không)');
            }
            return original.call(this, shader, r.src);
        };
        patched.__agvn = true;
        proto.shaderSource = patched;
    }

    if (typeof WebGL2RenderingContext !== 'undefined') wrap(WebGL2RenderingContext.prototype);
    if (typeof WebGLRenderingContext !== 'undefined') wrap(WebGLRenderingContext.prototype);
})();
