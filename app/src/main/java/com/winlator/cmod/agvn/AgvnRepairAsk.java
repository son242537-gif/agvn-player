/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.container.Shortcut;

import java.util.Collections;
import java.util.Properties;

/**
 * Over a game that starts with a fix "Tự sửa lỗi" is trying ({@link AgvnRepair}): once it has shown frames and played
 * {@link #ASK_AFTER_MS} (time the app kept it stopped does not count), a bar asks whether the problem the player
 * reported is gone. "Hết lỗi rồi" keeps the fix, "Vẫn còn lỗi" puts the settings back and the next fix on (then "Mở lại
 * game ngay"), "Hỏi lần sau" asks at the next start. Not over a game that crashed: the doctor asks about that. Once per
 * start. UI thread.
 */
public final class AgvnRepairAsk {
    static final long ASK_AFTER_MS = 45_000, POLL_MS = 5000;

    private final XServerDisplayActivity activity;
    private final Shortcut shortcut;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long startMs = SystemClock.uptimeMillis();
    private final long stoppedAtStartNs = AgvnGamePause.pausedNs(System.nanoTime());

    private AgvnRepairAsk(XServerDisplayActivity activity, Shortcut shortcut) {
        this.activity = activity;
        this.shortcut = shortcut;
    }

    /** Watches the game of {@code activity} when a fix is being tried on it. */
    public static void start(XServerDisplayActivity activity) {
        Shortcut s = activity.agvnShortcut();
        if (s == null || AgvnRepair.trying(AgvnGoodConfig.load(activity, s)).isEmpty()) return;
        AgvnRepairAsk ask = new AgvnRepairAsk(activity, s);
        ask.handler.postDelayed(ask::poll, POLL_MS);
    }

    private void poll() {
        if (activity.isFinishing() || activity.isDestroyed() || AgvnSessionTrack.engineCrash() != null) return;
        long stoppedMs = (AgvnGamePause.pausedNs(System.nanoTime()) - stoppedAtStartNs) / 1_000_000L;
        long played = SystemClock.uptimeMillis() - startMs - stoppedMs;
        if (AgvnGamePause.isPaused() || !AgvnSessionTrack.showedFrames() || played < ASK_AFTER_MS) {
            handler.postDelayed(this::poll, POLL_MS);
            return;
        }
        Properties state = AgvnGoodConfig.load(activity, shortcut);
        // answered in the meantime, or another fix was put on during this run: asked about after the next start
        if (AgvnRepair.trying(state).isEmpty() || !AgvnRepair.ran(state, shortcut.getExtra("lastRunAt"))) return;
        AgvnProblemCatalog.Finding f =
                AgvnDoctor.catalog(activity).finding(AgvnRepair.symptom(state), Collections.emptyMap());
        String title = f != null ? f.title() : activity.getString(R.string.agvn_repair_title);
        AgvnSessionLog.event("Tự sửa lỗi: hỏi người chơi còn lỗi \"" + title + "\" không (đang thử "
                + AgvnRepair.label(state) + ")");
        AgvnWarningBar.show(activity, activity.getString(R.string.agvn_repair_ask_title, title),
                activity.getString(R.string.agvn_repair_ask_detail, AgvnRepair.label(state)),
                new AgvnWarningBar.Choice(R.string.agvn_repair_gone, () -> AgvnRepairDialog.gone(activity, shortcut)),
                new AgvnWarningBar.Choice(R.string.agvn_repair_still,
                        () -> AgvnRepairDialog.still(activity, shortcut, activity::agvnExit)),
                new AgvnWarningBar.Choice(R.string.agvn_repair_later, null));
    }
}
