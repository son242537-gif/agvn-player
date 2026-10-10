/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;

import org.godotengine.godot.Godot;
import org.godotengine.godot.GodotLib;
import org.godotengine.godot.GodotRenderView;

import java.util.Locale;

/**
 * The "Chạy nhẹ" toolkit's keys and mouse buttons as Godot for Android takes a keyboard and a mouse
 * ({@link AgvnGodotActivity}): a key through Godot's own key handler, as from a hardware keyboard; a mouse button or
 * the wheel at the last touch, as from a mouse. Also which links a game may not open.
 */
final class AgvnGodotInput {
    /** How long a tapped key stays down, so the game sees it for a few frames. */
    static final int TAP_MS = 100;

    private AgvnGodotInput() {}

    /** An action of {@link AgvnLightActions} (a key code, or a negative mouse action) pressed or released at x, y. */
    static void send(Godot godot, int action, boolean down, float x, float y) {
        GodotRenderView view = godot != null ? godot.getRenderView() : null;
        if (view == null || action == AgvnLightActions.NONE) return;
        if (action > 0) {
            KeyEvent event = new KeyEvent(down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, action);
            if (down) view.getInputHandler().onKeyDown(action, event);
            else view.getInputHandler().onKeyUp(action, event);
            return;
        }
        float px = x >= 0 ? x : view.getView().getWidth() / 2f, py = y >= 0 ? y : view.getView().getHeight() / 2f;
        if (action == AgvnLightActions.SCROLL_UP || action == AgvnLightActions.SCROLL_DOWN) {
            if (down) mouse(godot, MotionEvent.ACTION_SCROLL, 0, px, py, action == AgvnLightActions.SCROLL_UP ? 1 : -1);
            return;
        }
        mouse(godot, down ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP, down ? button(action) : 0, px, py, 0);
    }

    /** A key pressed and released a moment later. */
    static void tap(Godot godot, int keyCode) {
        send(godot, keyCode, true, -1, -1);
        new Handler(Looper.getMainLooper()).postDelayed(() -> send(godot, keyCode, false, -1, -1), TAP_MS);
    }

    /** Android's mouse button for a mouse action of {@link AgvnLightActions}. */
    static int button(int action) {
        if (action == AgvnLightActions.RIGHT_CLICK) return MotionEvent.BUTTON_SECONDARY;
        if (action == AgvnLightActions.MIDDLE_CLICK) return MotionEvent.BUTTON_TERTIARY;
        return MotionEvent.BUTTON_PRIMARY;
    }

    /** As GodotInputHandler does for a mouse: on Godot's render thread when the engine asks for it. */
    private static void mouse(Godot godot, int action, int buttons, float x, float y, float scroll) {
        Runnable event = () -> GodotLib.dispatchMouseEvent(action, buttons, x, y, 0f, scroll, false, false, 1f, 0f, 0f);
        if (GodotLib.shouldDispatchInputToRenderThread()) godot.runOnRenderThread(event);
        else event.run();
    }

    /** True for a page a game asks to open (OS.shell_open: a store, a web site): AGVN Player opens none. */
    static boolean isWebLink(Intent intent) {
        if (intent == null || intent.getComponent() != null || !Intent.ACTION_VIEW.equals(intent.getAction())) return false;
        Uri data = intent.getData();
        String scheme = data != null && data.getScheme() != null ? data.getScheme().toLowerCase(Locale.ROOT) : "";
        return !scheme.equals("file") && !scheme.equals("content");
    }
}
