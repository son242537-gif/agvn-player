package com.winlator.cmod.xserver;

import com.winlator.cmod.agvn.AgvnUseCount;

public class Cursor extends XResource {
    public final int hotSpotX;
    public final int hotSpotY;
    public final Drawable cursorImage;
    public final Drawable sourceImage;
    public final Drawable maskImage;
    private boolean visible = true;
    // AGVN: its image goes back to Android (Drawable.release) once it is freed and no window shows it
    private final AgvnUseCount uses = new AgvnUseCount();

    public Cursor(int id, int hotSpotX, int hotSpotY, Drawable cursorImage, Drawable sourceImage, Drawable maskImage) {
        super(id);
        this.hotSpotX = hotSpotX;
        this.hotSpotY = hotSpotY;
        this.cursorImage = cursorImage;
        this.sourceImage = sourceImage;
        this.maskImage = maskImage;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /** AGVN: a window now shows this cursor (true), or no longer does (false). */
    public void shownBy(boolean shown) {
        if (uses.use(shown)) cursorImage.release();
    }

    /** AGVN: its client freed it (FreeCursor, or the client left). */
    public void free() {
        if (uses.free()) cursorImage.release();
    }
}
