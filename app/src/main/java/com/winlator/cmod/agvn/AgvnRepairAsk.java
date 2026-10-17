/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.container.Shortcut;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Over a game that starts with a fix "Tự sửa lỗi" is trying ({@link AgvnRepair}): once it has shown frames and played
 * {@link #ASK_AFTER_MS} (time the app kept it stopped does not count), a bar asks whether the problem the player
 * reported is gone. "Hết lỗi rồi" keeps the fix, "Vẫn còn lỗi" puts the settings back and the next fix on (then "Mở lại
 * game ngay"), "Hỏi lần sau" asks at the next start. Not over a game that crashed: the doctor asks about that. Once per
 * start. UI thread.
 * <p>Not while the game shows nothing ({@link #waitsForFrames}): it may be loading. Immoral Bathhouse (Unity, Adreno 725,
 * 17/10/2026) drew its logo, then nothing for 2–2.5 min while its first scene loaded; asked at 45 s, the player answered
 * "Vẫn còn lỗi" to four fixes in a row, and each restart cut the load short.
 */
public final class AgvnRepairAsk {
    static final long ASK_AFTER_MS = 45_000, POLL_MS = 5000;
    /** No frame for this long: the game shows nothing now, so the bar waits for it to draw again. */
    static final long STILL_MS = 5_000;
    /** ... unless it has shown nothing for this long of play: frozen, or a load longer than any seen. */
    static final long FROZEN_MS = 180_000;

    private final XServerDisplayActivity activity;
    private final Shortcut shortcut;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long startMs = SystemClock.uptimeMillis();
    private final long stoppedAtStartNs = AgvnGamePause.pausedNs(System.nanoTime());
    private boolean checked, waitNoted;
    /** Play time (without the time the app kept the game stopped) when it last showed a frame. */
    private long drawnAtPlayedMs;

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
        if (!checked) { // first: did the game start with the fix on trial at all (AgvnRepairCheck)?
            checked = true;
            Properties state = AgvnGoodConfig.load(activity, shortcut);
            Map<String, String> ran = AgvnSessionTrack.settings();
            if (!AgvnRepair.trying(state).isEmpty() && AgvnRepair.ran(state, shortcut.getExtra("lastRunAt"))
                    && !AgvnRepairCheck.on(shortcut, state, ran.isEmpty() ? AgvnGoodConfig.snapshot(shortcut) : ran)) {
                notOn(state);
                return;
            }
        }
        long stoppedMs = (AgvnGamePause.pausedNs(System.nanoTime()) - stoppedAtStartNs) / 1_000_000L;
        long played = SystemClock.uptimeMillis() - startMs - stoppedMs;
        if (AgvnSessionTrack.msSinceFrame(System.currentTimeMillis()) < STILL_MS) drawnAtPlayedMs = played;
        boolean still = waitsForFrames(played - drawnAtPlayedMs);
        if (AgvnGamePause.isPaused() || !AgvnSessionTrack.showedFrames() || played < ASK_AFTER_MS || still) {
            if (still && played >= ASK_AFTER_MS && !waitNoted) {
                waitNoted = true;
                AgvnSessionLog.event("Tự sửa lỗi: game không vẽ khung nào từ " + (played - drawnAtPlayedMs) / 1000
                        + " giây (có thể đang tải): chờ game vẽ lại, hoặc đứng yên " + FROZEN_MS / 60_000 + " phút, rồi mới hỏi");
            }
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

    /** True while the game has shown nothing for {@code stillMs} of play, under {@link #FROZEN_MS}. Pure Java. */
    static boolean waitsForFrames(long stillMs) {
        return stillMs >= STILL_MS && stillMs < FROZEN_MS;
    }

    /** The game started without the fix on trial: not asked about nor counted; the first fix left is offered. */
    private void notOn(Properties state) {
        String label = AgvnRepair.label(state);
        AgvnRepairCheck.untry(activity, shortcut, state);
        AgvnProblemCatalog.Finding f =
                AgvnDoctor.catalog(activity).finding(AgvnRepair.symptom(state), AgvnGodotGame.params(shortcut));
        List<AgvnFixes.Fix> left = f != null ? AgvnRepair.left(activity, shortcut, f, state) : Collections.emptyList();
        String title = activity.getString(R.string.agvn_repair_off_title, label);
        String detail = activity.getString(R.string.agvn_repair_off_detail, label);
        if (left.isEmpty()) {
            AgvnWarningBar.show(activity, title, detail, new AgvnWarningBar.Choice(R.string.agvn_movie_ok, null));
            return;
        }
        AgvnFixes.Fix next = left.get(0);
        AgvnWarningBar.show(activity, title, detail,
                new AgvnWarningBar.Choice(activity.getString(R.string.agvn_repair_off_try, next.label), () -> {
                    if (AgvnRepair.start(activity, shortcut, f, next)) {
                        AgvnSlowBar.offerRestart(activity, shortcut, next.label, activity::agvnExit);
                    }
                }),
                new AgvnWarningBar.Choice(R.string.agvn_doctor_later, null));
    }
}
