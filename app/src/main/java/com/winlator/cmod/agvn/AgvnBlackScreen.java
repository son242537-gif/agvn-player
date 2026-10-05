/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.xserver.Drawable;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.XLock;
import com.winlator.cmod.xserver.XServer;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * A Windows game that stays black, as a KiriKiri game does on a screen smaller than itself: Wine lists display modes
 * only up to the screen's size, so its switch to full screen fails. Every {@link AgvnBlackScreenRules#POLL_MS} it
 * looks at {@link #ROWS} rows of the game's window ({@link AgvnGameWindow}): the last Vulkan or OpenGL frame presented
 * into a game window, else what the largest game window holds (GDI). A frame the CPU cannot read tells nothing, and
 * black while the game's memory still grows fast is its loading. When the rules say so ({@link AgvnBlackScreenRules}),
 * a bar offers a larger screen, then a new start. The game keeps "Để vậy, không hỏi lại" (extra agvnBlackScreen: 0):
 * then it is still watched, and su-kien.txt says what was seen, but not asked. A black window not asked about is
 * written there too when the watch ends. UI thread.
 */
public final class AgvnBlackScreen {
    private static final String TAG = "AGVN", EXTRA_ASK = "agvnBlackScreen";
    private static final int ROWS = 5;
    private static final List<Integer> FOUR_BYTES = Arrays.asList(1, 2, 5, 0x2b);
    private static volatile Window directWindow;
    private static volatile Drawable directFrame;

    private final XServerDisplayActivity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::poll;
    private final AgvnBlackScreenRules rules;
    private final long startMs = SystemClock.uptimeMillis();
    private final boolean ask;
    /** The game's window as last looked at, {x, y, width, height}, or null. */
    private int[] window;
    /** Wine refused the game's resolution, or it showed a message box, since the start (the tail keeps 300 lines). */
    private boolean refused, box;

    /** A Vulkan or OpenGL frame presented into {@code window} (X server thread, every frame: kept cheap). */
    public static void onDirectFrame(Window window, Drawable frame) {
        directWindow = window;
        directFrame = frame;
    }

    AgvnBlackScreen(XServerDisplayActivity activity) {
        this.activity = activity;
        directWindow = null;
        directFrame = null;
        Shortcut s = activity.agvnShortcut();
        rules = new AgvnBlackScreenRules(s != null ? AgvnStartTimes.last(activity, s.file.getPath()) : 0);
        ask = s != null && !"0".equals(s.getExtra(EXTRA_ASK));
        if (s != null) handler.postDelayed(poll, AgvnBlackScreenRules.POLL_MS);
    }

    private void poll() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try {
            long elapsed = SystemClock.uptimeMillis() - startMs;
            List<String> lines = AgvnWineTail.get().lines();
            refused |= AgvnBlackScreenRules.refused(lines);
            box |= AgvnBlackScreenRules.boxShown(lines);
            long grewMb = AgvnSessionTrack.grewMb(System.currentTimeMillis(), AgvnBlackScreenRules.LOADING_SAMPLE_MS);
            boolean loading = grewMb >= AgvnBlackScreenRules.LOADING_MB;
            AgvnBlackScreenRules.Step step = rules.next(look(), elapsed, refused, box, loading);
            if (step == AgvnBlackScreenRules.Step.OFFER) offer(elapsed, refused);
            else if (step == AgvnBlackScreenRules.Step.WAIT) handler.postDelayed(poll, AgvnBlackScreenRules.POLL_MS);
            else if (rules.blackNow()) notAsked(elapsed);
        } catch (RuntimeException e) {
            Log.w(TAG, "black screen check failed", e); // never in the game's way: no more checks
        }
    }

    private AgvnBlackScreenRules.Look look() {
        XServer xServer = activity.getXServer();
        if (xServer == null) return AgvnBlackScreenRules.Look.UNKNOWN;
        try (XLock lock = xServer.lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.DRAWABLE_MANAGER)) {
            Window into = directWindow;
            Drawable frame = directFrame;
            Window game = into != null && frame != null ? AgvnGameWindow.topLevel(into) : null;
            if (game != null) { // it draws with Vulkan or OpenGL: its window holds nothing, its last frame shows
                window = rect(game);
                return xServer.drawableManager.getDrawable(frame.id) == frame ? look(frame) : AgvnBlackScreenRules.Look.UNKNOWN;
            }
            game = AgvnGameWindow.find(xServer.windowManager.rootWindow);
            if (game == null) return AgvnBlackScreenRules.Look.NO_WINDOW;
            window = rect(game);
            return look(game.getContent()); // GDI: what the window holds
        }
    }

    private static int[] rect(Window w) {
        return new int[]{w.getRootX(), w.getRootY(), w.getWidth(), w.getHeight()};
    }

    /**
     * {@link #ROWS} rows across the image ({@link AgvnBlackScreenRules#look}): unknown when one cannot be read or its
     * pixels are not 4 bytes (RGBA, RGBX, BGRA, RGBA 10:10:10:2).
     */
    private static AgvnBlackScreenRules.Look look(Drawable image) {
        if (image == null || !FOUR_BYTES.contains(image.format)) return AgvnBlackScreenRules.Look.UNKNOWN;
        ByteBuffer[] rows = new ByteBuffer[ROWS];
        for (int i = 1; i <= ROWS; i++) {
            rows[i - 1] = image.agvnReadRow(image.height * i / (ROWS + 1));
            if (rows[i - 1] == null) return AgvnBlackScreenRules.Look.UNKNOWN;
        }
        return AgvnBlackScreenRules.look(rows, image.width);
    }

    private void offer(long elapsedMs, boolean refused) {
        Shortcut s = activity.agvnShortcut();
        XServer xServer = activity.getXServer();
        if (s == null || xServer == null) return;
        String screen = String.valueOf(xServer.screenInfo);
        int sw = xServer.screenInfo.width, sh = xServer.screenInfo.height;
        // a window that followed the screen made larger for it gets no larger one (AgvnScreenGrowth)
        boolean follows = window != null && AgvnScreenGrowth.followed(s, window[2], window[3], sw, sh);
        String bigger = AgvnBlackScreenRules.bigger(screen, s.getExtra(AgvnKirikiri.EXTRA_GAME_SIZE),
                follows ? null : window, refused);
        boolean sync = bigger == null && AgvnPresentSync.offerable(s);
        long seconds = elapsedMs / 1000;
        String frame = window != null ? window[2] + "x" + window[3] : "?";
        AgvnSessionLog.event("Màn hình đen sau " + seconds + " giây, màn hình " + screen + ", khung game " + frame
                + (refused ? ", Wine từ chối độ phân giải game xin" : "")
                + (bigger != null ? ": hỏi đổi sang " + bigger
                : sync ? ": hỏi bật đồng bộ khung hình" : ": không hỏi")
                + (ask || bigger == null && !sync ? "" : " (không hiện thanh: game đã chọn \"Để vậy, không hỏi lại\")"));
        Log.i(TAG, "black screen after " + seconds + " s on " + screen + ", window " + frame
                + (refused ? ", mode refused" : "") + ", offer " + (sync ? "frame sync" : bigger) + (ask ? "" : ", not asked"));
        if (!ask) return; // "Để vậy, không hỏi lại"
        if (bigger != null) offerScreen(s, bigger, screen, seconds, refused);
        else if (sync) offerSync(s, seconds);
    }

    /** The watch ended on a black window it did not ask about: why, in su-kien.txt, for "Gửi nhật ký". */
    private void notAsked(long elapsedMs) {
        String frame = window != null ? window[2] + "x" + window[3] : "?";
        String why = box ? "game đã hiện hộp thoại" : rules.shown() ? "game đã hiện hình trước đó" : "game còn đang tải";
        AgvnSessionLog.event("Màn hình đen tới giây " + elapsedMs / 1000 + ", khung game " + frame + ": không hỏi, " + why);
        Log.i(TAG, "black screen at " + elapsedMs / 1000 + " s, window " + frame + ", not asked: " + why);
    }

    private void offerScreen(Shortcut s, String bigger, String screen, long seconds, boolean refused) {
        String label = activity.getString(R.string.agvn_fit_resize, bigger.replace('x', '×'));
        String detail = activity.getString(R.string.agvn_black_detail, seconds, screen.replace('x', '×'))
                + (refused ? "\n" + activity.getString(R.string.agvn_black_refused) : "")
                + "\n" + activity.getString(R.string.agvn_doctor_lead);
        int[] seen = window;
        show(s, detail, new AgvnWarningBar.Choice(label, () -> AgvnScreenFit.resize(activity, bigger, label, seen)));
    }

    /** A DirectX game black with nothing to offer for its screen: what got Support Pregnancy School to show. */
    private void offerSync(Shortcut s, long seconds) {
        String label = activity.getString(R.string.agvn_black_sync);
        String detail = activity.getString(R.string.agvn_black_sync_detail, seconds)
                + "\n" + activity.getString(R.string.agvn_doctor_lead);
        show(s, detail, new AgvnWarningBar.Choice(label, () -> {
            AgvnPresentSync.turnOn(s);
            AgvnSlowBar.offerRestart(activity, s, label, activity::agvnExit);
        }));
    }

    private void show(Shortcut s, String detail, AgvnWarningBar.Choice fix) {
        AgvnWarningBar.show(activity, activity.getString(R.string.agvn_black_title), detail, fix,
                new AgvnWarningBar.Choice(R.string.agvn_black_wait, null),
                new AgvnWarningBar.Choice(R.string.agvn_doctor_keep, () -> {
                    s.putExtra(EXTRA_ASK, "0");
                    s.saveData();
                }));
    }
}
