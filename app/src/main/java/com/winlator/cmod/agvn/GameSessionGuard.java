/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.R;

/**
 * Polls temperature every 2 s while a game is in the foreground and shows a gentle warning with a 30 s countdown.
 * Never closes the game or other apps by itself. The heat warning's "Máy bố, bố biết" keeps heat quiet for a day
 * ({@link AgvnHeatAck}). Free RAM is {@link AgvnMemoryWatch}'s bar alone: its level follows the phone's RAM and it keeps
 * the player's "Để sau", while this dialog's "Sắp hết RAM" under 300 MB came back every 5 minutes (14/10/2026).
 */
public final class GameSessionGuard {
    private static final String TAG = "AGVN";
    private static final long POLL_MS = 2000;
    private static final int COUNTDOWN_S = 30;

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SessionGuard guard = new SessionGuard();
    private AlertDialog dialog;
    private boolean running;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (dialog == null || !dialog.isShowing()) {
                SessionGuard.Decision decision;
                try {
                    decision = guard.feed(ThermalMonitor.sample(activity), System.currentTimeMillis());
                } catch (RuntimeException e) {
                    Log.w(TAG, "session guard sample failed", e);
                    decision = SessionGuard.Decision.OK;
                }
                if (decision == SessionGuard.Decision.WARN_THERMAL) warn();
            }
            handler.postDelayed(this, POLL_MS);
        }
    };

    public GameSessionGuard(Activity activity) {
        this.activity = activity;
    }

    public void start() {
        if (running) return;
        running = true;
        handler.postDelayed(poll, POLL_MS);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(poll);
    }

    /** Stops polling, cancels the countdown and closes an open warning (activity going away). */
    public void destroy() {
        stop();
        handler.removeCallbacksAndMessages(null);
        if (dialog != null && dialog.isShowing() && !activity.isDestroyed()) {
            try {
                dialog.dismiss();
            } catch (IllegalArgumentException ignored) {
                // window already gone
            }
        }
        dialog = null;
    }

    private void warn() {
        if (activity.isFinishing()) return;
        // AGVN: heat is told once per game (this dialog or the heat bar), not for a day after "Máy bố, bố biết"
        if (AgvnHeatAck.quiet(activity) || !AgvnHeatWatch.firstHeatWarning(activity)) return;
        Log.w(TAG, "session guard: " + SessionGuard.Decision.WARN_THERMAL);
        String message = activity.getString(R.string.agvn_guard_thermal) + "\n" + activity.getString(R.string.agvn_guard_heat_ack_note);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.agvn_guard_thermal_title)
                .setMessage(message)
                .setPositiveButton(R.string.agvn_guard_heat_ack, (d, w) -> AgvnHeatAck.acknowledge(activity));
        dialog = builder.create();
        dialog.show();
        countdown(dialog, message, COUNTDOWN_S);
    }

    private void countdown(AlertDialog shown, String message, int secondsLeft) {
        if (shown != dialog || !shown.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
        if (secondsLeft <= 0) {
            shown.dismiss();
            return;
        }
        shown.setMessage(message + "\n\n" + activity.getString(R.string.agvn_guard_countdown, secondsLeft));
        handler.postDelayed(() -> countdown(shown, message, secondsLeft - 1), 1000);
    }
}
