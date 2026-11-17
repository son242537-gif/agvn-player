/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.winhandler.MouseEventFlags;
import com.winlator.cmod.winhandler.WinHandler;
import com.winlator.cmod.xserver.XServer;

/**
 * Turning a 3D game's camera from the phone, at the speed the player set for the game ({@link AgvnCameraSettings}).
 * Players found it very hard (17/11/2026): a finger turned it by the X screen's pixels it crossed, so little on a
 * weak phone; the first 80 ms of every swipe were lost (TouchpadView's click delay), so a quick flick turned nothing;
 * a second finger resting on the screen stopped it (two-finger scroll and click); the camera stick ran at one speed
 * in ten steps, its diagonals pulled onto the axes. Now, while the game holds the cursor ({@link AgvnMouseLook}),
 * every finger off the controls turns it at once by its share of the screen ({@link #finger}), the stick turns it
 * slowly near its centre and fast at its rim ({@link #stick}), and the gyroscope when the player turned it on
 * ({@link AgvnGyroLook}). A camera zone and the buttons set to turn it as they are dragged (Bắn, Ngắm:
 * {@link AgvnLookControl}) turn it also when the game is not seen to hold the cursor ({@link #zone}).
 */
public final class AgvnLook {
    /** The stick's old speed, kept while the game shows its cursor: counts a tick (1/60 s) at full tilt, speed 1. */
    static final float CURSOR_STICK_PER_TICK = 10f;

    private final XServer xServer;
    private final AgvnCamera camera = new AgvnCamera(), cursor = new AgvnCamera();
    private volatile AgvnCameraSettings settings = new AgvnCameraSettings();

    public AgvnLook(XServer xServer) {
        this.xServer = xServer;
    }

    public AgvnCameraSettings settings() {
        return settings;
    }

    public void setSettings(AgvnCameraSettings s) {
        settings = s != null ? s : new AgvnCameraSettings();
    }

    /**
     * A finger off the controls moved ({@code dx}, {@code dy}) pixels on a screen {@code width} × {@code height}
     * (TouchpadView): true when it turned the camera, the game holding the cursor; false to handle it as a touch.
     */
    public boolean finger(float dx, float dy, int width, int height) {
        AgvnMouseLook look = xServer.agvnMouseLook;
        if (!look.locked()) return false;
        look.moved(Math.abs(dx) + Math.abs(dy), Math.max(8, Math.min(width, height) / 40));
        turn(dx, dy, Math.max(width, height));
        return true;
    }

    /** A camera zone or a button that turns the camera was dragged ({@code dx}, {@code dy}) pixels: it turns. */
    public void zone(float dx, float dy, int width, int height) {
        turn(dx, dy, Math.max(width, height));
    }

    /**
     * One tick ({@code seconds} long) of a stick set to the mouse, tilted ({@code x}, {@code y}) (InputControlsView).
     * While the game holds the cursor it turns the camera along its curve ({@link AgvnCamera#curve}); else it moves
     * the cursor as it did, with no part of a count lost.
     */
    public void stick(float x, float y, float cursorSpeed, float seconds) {
        if (xServer.agvnMouseLook.locked()) {
            float m = (float) Math.hypot(x, y), share = AgvnCamera.curve(Math.min(1f, m));
            if (share <= 0) return;
            AgvnCameraSettings s = settings;
            float k = share * AgvnCamera.STICK_COUNTS_PER_S * s.speed * cursorSpeed * seconds / m;
            send(camera.take(x * k, y * k * s.vertical));
            return;
        }
        float k = cursorSpeed * CURSOR_STICK_PER_TICK;
        int[] d = cursor.take(x * k, y * k);
        if (d[0] == 0 && d[1] == 0) return;
        WinHandler winHandler = xServer.getWinHandler();
        if (xServer.isRelativeMouseMovement() && winHandler != null) {
            winHandler.mouseEvent(MouseEventFlags.MOVE, d[0], d[1], 0);
        } else {
            xServer.injectPointerMoveDelta(d[0], d[1]);
        }
    }

    /** The phone turned ({@code dx}, {@code dy}) counts (AgvnGyroLook): the camera, while the cursor is held. */
    void gyro(float dx, float dy) {
        if (xServer.agvnMouseLook.locked()) send(camera.take(dx, dy * settings.vertical));
    }

    private void turn(float dx, float dy, int longSide) {
        AgvnCameraSettings s = settings;
        float x = AgvnCamera.drag(dx, longSide, s.speed), y = AgvnCamera.drag(dy, longSide, s.speed) * s.vertical;
        send(camera.take(x, y));
    }

    private void send(int[] d) {
        if (d[0] == 0 && d[1] == 0) return;
        WinHandler winHandler = xServer.getWinHandler();
        // "Di chuột tương đối" with Winlator's own winhandler.exe: it moves the mouse by as much as asked
        if (winHandler != null && !winHandler.agvnRelativeReady() && xServer.isRelativeMouseMovement()) {
            winHandler.mouseEvent(MouseEventFlags.MOVE, d[0], d[1], 0);
        } else {
            xServer.agvnMouseLook.by(d[0], d[1]);
        }
    }
}
