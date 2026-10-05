/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.core.ProcessHelper;
import com.winlator.cmod.xserver.Pointer;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.XKeycode;
import com.winlator.cmod.xserver.XLock;
import com.winlator.cmod.xserver.XServer;

/**
 * A movie the game waits on that Wine cannot decode ({@link AgvnMovieRules}). su-kien.txt says so, the startup line
 * says why the game does not go on ({@link AgvnStartupProgress#problem}), and a bar offers "Bỏ qua phim": a click on
 * the game's window, then Esc while the movie is still stuck (many games skip a movie on either). A skip that did not
 * help is not offered to the game again (extra agvnMovieSkip: 0); the bar still says what happened.
 */
public final class AgvnMovieWatch implements Callback<String> {
    private static final String TAG = "AGVN", EXTRA_SKIP = "agvnMovieSkip";
    /** How long a click or Esc gets to stop the movie before the next try. */
    private static final long STEP_MS = 2500;

    private final XServerDisplayActivity activity;
    private final AgvnMovieRules rules = new AgvnMovieRules();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private AgvnMovieWatch(XServerDisplayActivity activity) {
        this.activity = activity;
    }

    /** Reads Wine's output from now on (ProcessHelper's callbacks: every game's output is read, logs on or off). */
    public static void attach(XServerDisplayActivity activity) {
        ProcessHelper.addDebugCallback(new AgvnMovieWatch(activity));
    }

    @Override
    public void call(String line) {
        if (line != null && rules.add(line, SystemClock.uptimeMillis())) handler.post(this::found);
    }

    private void found() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        String movie = rules.movie() != null ? rules.movie() : "?";
        AgvnSessionLog.event("Phim trong game không phát được: " + movie + ", bộ giải mã của Wine từ chối định dạng này");
        Log.i(TAG, "movie not decoded: " + movie);
        AgvnStartupProgress.problem(R.string.agvn_movie_progress);
        Shortcut s = activity.agvnShortcut();
        boolean skip = s == null || !"0".equals(s.getExtra(EXTRA_SKIP));
        String detail = activity.getString(R.string.agvn_movie_detail, rules.movie() != null ? movie : "này") + "\n"
                + activity.getString(skip ? R.string.agvn_doctor_lead : R.string.agvn_movie_no_skip);
        if (skip) AgvnWarningBar.show(activity, activity.getString(R.string.agvn_movie_title), detail,
                new AgvnWarningBar.Choice(R.string.agvn_movie_skip, () -> skip(s)),
                new AgvnWarningBar.Choice(R.string.agvn_black_wait, null));
        else AgvnWarningBar.show(activity, activity.getString(R.string.agvn_movie_title), detail,
                new AgvnWarningBar.Choice(R.string.agvn_movie_ok, null));
    }

    /** A click in the middle of the game's window; Esc if the movie is still stuck; then what came of it. */
    private void skip(Shortcut s) {
        click();
        handler.postDelayed(() -> {
            if (!stuck()) {
                skipped("nhấp chuột");
                return;
            }
            press(XKeycode.KEY_ESC);
            handler.postDelayed(() -> {
                if (stuck()) notSkipped(s);
                else skipped("phím Esc");
            }, STEP_MS);
        }, STEP_MS);
    }

    private boolean stuck() {
        return rules.stillFailing(SystemClock.uptimeMillis());
    }

    private void skipped(String how) {
        AgvnSessionLog.event("Bỏ qua phim: được, bằng " + how);
        AgvnStartupProgress.problem(0);
    }

    private void notSkipped(Shortcut s) {
        AgvnSessionLog.event("Bỏ qua phim: không được, phim vẫn kẹt sau nhấp chuột và phím Esc");
        if (s != null) {
            s.putExtra(EXTRA_SKIP, "0");
            s.saveData();
        }
        if (!activity.isFinishing()) Toast.makeText(activity, R.string.agvn_movie_skip_failed, Toast.LENGTH_LONG).show();
    }

    private void click() {
        XServer xServer = activity.getXServer();
        if (xServer == null) return;
        int x = xServer.screenInfo.width / 2, y = xServer.screenInfo.height / 2;
        try (XLock lock = xServer.lock(XServer.Lockable.WINDOW_MANAGER)) {
            Window game = AgvnGameWindow.find(xServer.windowManager.rootWindow);
            if (game != null) {
                x = game.getRootX() + game.getWidth() / 2;
                y = game.getRootY() + game.getHeight() / 2;
            }
        }
        xServer.injectPointerMove(x, y);
        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT);
        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT);
    }

    private void press(XKeycode key) {
        XServer xServer = activity.getXServer();
        if (xServer == null) return;
        xServer.injectKeyPress(key);
        xServer.injectKeyRelease(key);
    }
}
