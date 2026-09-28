/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.List;

/** Draws full-bleed library artwork (portrait cover or landscape banner) from a game image or the exe icon. */
final class AgvnCoverPainter {
    private static final Paint FILTER = new Paint(Paint.FILTER_BITMAP_FLAG);

    private AgvnCoverPainter() {}

    /** The game's own image filling {@code w}x{@code h}: cropped when the shape is close, else fitted on a blurred copy. */
    static Bitmap fromImage(Bitmap src, int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float srcRatio = src.getWidth() / (float) src.getHeight(), dstRatio = w / (float) h;
        float mismatch = Math.max(srcRatio, dstRatio) / Math.min(srcRatio, dstRatio);
        if (mismatch < 1.35f) {
            drawCrop(c, src, new RectF(0, 0, w, h));
            return out;
        }
        drawCrop(c, blur(src), new RectF(0, 0, w, h));
        c.drawColor(0x66000000);
        float scale = Math.min(w / (float) src.getWidth(), h / (float) src.getHeight());
        float dw = src.getWidth() * scale, dh = src.getHeight() * scale;
        c.drawBitmap(src, null, new RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), FILTER);
        return out;
    }

    /** No game image: blurred icon as background, the icon large, and the game name. */
    static Bitmap drawn(String name, Bitmap icon, int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint bg = new Paint();
        bg.setShader(new LinearGradient(0, 0, w, h, 0xFF34405A, 0xFF121419, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, bg);
        if (icon != null) {
            Paint soft = new Paint(Paint.FILTER_BITMAP_FLAG);
            soft.setAlpha(150);
            c.drawBitmap(blur(icon), null, cropRect(icon.getWidth(), icon.getHeight(), w, h), soft);
            c.drawColor(0x55000000);
        }
        boolean portrait = h > w;
        int size = portrait ? (int) (w * 0.72f) : (int) (h * 0.62f);
        int left = portrait ? (w - size) / 2 : (int) (w * 0.07f);
        int top = portrait ? (int) (h * 0.14f) : (h - size) / 2;
        if (icon != null) c.drawBitmap(icon, null, new Rect(left, top, left + size, top + size), FILTER);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(0xFFFFFFFF);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setShadowLayer(8, 0, 3, 0xCC000000);
        text.setTextSize(portrait ? w * 0.095f : h * 0.085f);
        text.setTextAlign(portrait || icon == null ? Paint.Align.CENTER : Paint.Align.LEFT);
        float x = portrait || icon == null ? w / 2f : left + size + w * 0.05f;
        List<String> lines = AgvnCovers.wrap(name, portrait ? 14 : 20, 3);
        float lineH = text.getTextSize() * 1.2f;
        float y = portrait ? (icon != null ? top + size + lineH * 1.3f : h * 0.42f) : h / 2f - lineH * (lines.size() - 1) / 2f + lineH * 0.35f;
        for (String line : lines) {
            c.drawText(line, x, y, text);
            y += lineH;
        }
        Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
        accent.setColor(0xFFFFD97A);
        c.drawRect(0, h - Math.max(8, h / 90f), w, h, accent);
        return out;
    }

    private static void drawCrop(Canvas c, Bitmap src, RectF dst) {
        float scale = Math.max(dst.width() / src.getWidth(), dst.height() / src.getHeight());
        int sw = Math.round(dst.width() / scale), sh = Math.round(dst.height() / scale);
        int sx = (src.getWidth() - sw) / 2, sy = (src.getHeight() - sh) / 2;
        c.drawBitmap(src, new Rect(sx, sy, sx + sw, sy + sh), dst, FILTER);
    }

    /** Destination rect that covers {@code w}x{@code h} with a {@code sw}x{@code sh} image (centre crop). */
    private static RectF cropRect(int sw, int sh, int w, int h) {
        float scale = Math.max(w / (float) sw, h / (float) sh);
        float dw = sw * scale, dh = sh * scale;
        return new RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2);
    }

    /** Cheap strong blur: shrink to a few pixels, then let bitmap filtering smooth it back up. */
    private static Bitmap blur(Bitmap src) {
        int small = 12;
        float ratio = src.getWidth() / (float) src.getHeight();
        int bw = ratio >= 1 ? small : Math.max(1, Math.round(small * ratio));
        int bh = ratio >= 1 ? Math.max(1, Math.round(small / ratio)) : small;
        Bitmap tiny = Bitmap.createScaledBitmap(src, bw, bh, true);
        return Bitmap.createScaledBitmap(tiny, bw * 8, bh * 8, true);
    }
}
