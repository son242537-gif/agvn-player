/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * The flag of the app's language (AgvnLanguage), at the top left of the main screen: tapped, it opens the choice of
 * language. Drawn from the emoji, so it needs no picture; "🌐" for a language of the phone the app has no text for.
 */
public final class AgvnLanguageFlag {
    private AgvnLanguageFlag() {}

    /** "🇻🇳", "🇬🇧", "🇹🇭", "🇮🇩", "🇵🇭", "🇨🇳" for the language the texts show in. */
    public static String emoji(Context ctx) {
        return emojiOf(AgvnLanguage.textLanguage(ctx));
    }

    static String emojiOf(String lang) {
        switch (lang) {
            case "vi": return "🇻🇳";
            case "en": return "🇬🇧";
            case "th": return "🇹🇭";
            case "in": return "🇮🇩";
            case "tl": return "🇵🇭";
            case "zh": return "🇨🇳";
            default: return "🌐";
        }
    }

    /** The flag as a drawable for the toolbar's left button, {@code dp} wide. */
    public static Drawable drawable(Context ctx, int dp) {
        return new Flag(emoji(ctx), Math.round(dp * ctx.getResources().getDisplayMetrics().density));
    }

    private static final class Flag extends Drawable {
        private final String text;
        private final int size;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Flag(String text, int size) {
            this.text = text;
            this.size = size;
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(size * 0.8f);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            Paint.FontMetrics m = paint.getFontMetrics();
            canvas.drawText(text, b.exactCenterX(), b.exactCenterY() - (m.ascent + m.descent) / 2, paint);
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            // an emoji keeps its own colours
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
