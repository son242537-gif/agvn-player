package com.winlator.cmod.xserver;

import android.graphics.Bitmap;

import java.nio.ByteBuffer;

public class Pixmap extends XResource {
    public final Drawable drawable;

    public Pixmap(Drawable drawable) {
        super(drawable.id);
        this.drawable = drawable;
    }

    public Bitmap toBitmap(Pixmap maskPixmap) {
        // AGVN: read under the drawables' renderLock, as a freed pixmap gives its buffer back (Drawable.release); the
        // task manager reads window icons on its own thread
        Object maskLock = maskPixmap != null ? maskPixmap.drawable.renderLock : new Object();
        synchronized (drawable.renderLock) {
            synchronized (maskLock) {
                if (drawable.backingAHB == 0) return null;
                long maskData = maskPixmap != null ? maskPixmap.drawable.backingAHB : 0;
                short maskStride = maskPixmap != null ? maskPixmap.drawable.getStride() : 0;
                Bitmap bitmap = Bitmap.createBitmap(drawable.width, drawable.height, Bitmap.Config.ARGB_8888);
                toBitmap(drawable.getStride(), drawable.backingAHB, maskStride, maskData, bitmap);
                return bitmap;
            }
        }
    }

    private static native void toBitmap(short colorStride, long colorData, short maskStride, long maskData, Bitmap bitmap);
}
