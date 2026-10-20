/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.view.InputDevice;
import android.view.MotionEvent;

import java.util.function.Consumer;

/**
 * A Windows game's mouse pointer in touch play. AGVN adds Windows games with "Giả lập màn hình cảm ứng" on (their
 * profile), where a tap clicks where the finger is and the game screen never draws the pointer. A USB or Bluetooth
 * mouse then moved and clicked with no pointer to see (the maintainer's report, 10/10/2026). The pointer now shows
 * once a real mouse moves, clicks or scrolls, and hides again at the next finger on the screen, so touch play looks
 * as before. Nothing changes for a game played with the pointer shown. UI thread.
 */
public final class AgvnMouseCursor {
    private final boolean touchPlay;
    private final Consumer<Boolean> show;
    private boolean shown;

    /** {@code touchPlay}: the game hides its pointer for touch; {@code show} shows or hides the game's pointer. */
    public AgvnMouseCursor(boolean touchPlay, Consumer<Boolean> show) {
        this.touchPlay = touchPlay;
        this.show = show;
    }

    /** {@code event} reached the game screen: a mouse's shows the pointer, a finger's hides it again. */
    public void on(MotionEvent event) {
        if (event != null && event.getPointerCount() > 0)
            on(event.getSource(), event.getToolType(0), event.getActionMasked());
    }

    /** An event from {@code source}, its first pointer a {@code toolType}, doing {@code action}. */
    void on(int source, int toolType, int action) {
        if (!touchPlay) return;
        if (fromMouse(source, toolType)) set(true);
        else if (fingerDown(action, toolType)) set(false);
    }

    /** A held mouse moved or clicked ("Bắt chuột ngoài": Android sends it to the game without a pointer of its own). */
    public void mouse() {
        if (touchPlay) set(true);
    }

    /**
     * True for an event a mouse sent, or a touchpad (newer Android sends its events as a mouse's with a finger as
     * the tool), not a finger on the screen, a stylus or a gamepad.
     */
    static boolean fromMouse(int source, int toolType) {
        if (toolType == MotionEvent.TOOL_TYPE_MOUSE) return true;
        boolean mouse = (source & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE;
        return mouse && toolType != MotionEvent.TOOL_TYPE_STYLUS;
    }

    /** True when a finger first touches the screen (asked after {@link #fromMouse}, so not a touchpad's finger). */
    static boolean fingerDown(int action, int toolType) {
        return action == MotionEvent.ACTION_DOWN && toolType == MotionEvent.TOOL_TYPE_FINGER;
    }

    private void set(boolean visible) {
        if (shown == visible) return;
        shown = visible;
        show.accept(visible);
    }
}
