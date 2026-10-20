/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

/**
 * When an X cursor's image can go back to Android ({@code Drawable.release}): once its client freed it and no window
 * shows it, as X keeps a freed cursor while a window still does. Wine frees a cursor on its own connection, so the
 * FreeCursor can come before the window gets the next one. Says so once. Thread-safe; pure Java.
 */
public final class AgvnUseCount {
    private int users;
    private boolean freed;
    private boolean over;

    /** A window now shows it ({@code on}), or no longer does. True when it is over now. */
    public synchronized boolean use(boolean on) {
        users += on ? 1 : -1;
        return end();
    }

    /** Its client freed it. True when it is over now. */
    public synchronized boolean free() {
        freed = true;
        return end();
    }

    private boolean end() {
        if (over || !freed || users > 0) return false;
        over = true;
        return true;
    }
}
