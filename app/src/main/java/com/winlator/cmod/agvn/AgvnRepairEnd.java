/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.container.Shortcut;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * A fix "Tự sửa lỗi" is trying ({@link AgvnRepair}) that the game did not get past. The bar asks about a fix after 45 s
 * of play ({@link AgvnRepairAsk}), so a fix after which the game closed by itself sooner was never asked about and
 * stayed on the game. Av Director Life (Unreal, Mali-G925, 15/10/2026): "Đổi DXVK sang bản 2.3.1", tried for a black
 * screen, ended every start at Unreal's box after 30 s, also once the black-screen bar had moved the game to Proton 9.
 * Such a fix counts as tried and not helping, as after "Vẫn còn lỗi": the settings go back to before the first try,
 * and the report goes on from the library ({@link AgvnRepairDialog#plan}, with what happened).
 */
final class AgvnRepairEnd {
    private static final String TAG = "AGVN";
    /** The values a stored report carries to the library: the fix put back, after how many seconds. */
    static final String UNDONE = "undone", AFTER = "after";

    private AgvnRepairEnd() {}

    /**
     * True when the game closed by itself (or crashed) before it could be asked about the fix on trial. Not when the app
     * stopped the game meanwhile: Rj01608199 (vivo V2403A, 16/10/2026) died at each return to the app, when the X server
     * had dropped it, and two fixes went back counted as tried after "42 s", 28 of them stopped.
     */
    static boolean endedTooSoon(AgvnEvidence ev) {
        return !ev.stopped && (ev.crashed || ev.endedByGame) && ev.seconds < AgvnEvidence.GOOD_AFTER_S;
    }

    /** True when a fix is on trial, it was put on before this start, and the game ran with it ({@code ran}). */
    static boolean ranWithTrial(Shortcut s, Properties state, Map<String, String> ran) {
        return !AgvnRepair.trying(state).isEmpty() && AgvnRepair.ran(state, s.getExtra("lastRunAt"))
                && AgvnRepairCheck.on(s, state, ran);
    }

    /** {@code params} (the report's own) and which fix went back after {@code seconds}, for the library. */
    static Map<String, String> params(Map<String, String> params, String label, long seconds) {
        params = new LinkedHashMap<>(params);
        params.put(UNDONE, label);
        params.put(AFTER, String.valueOf(seconds));
        return params;
    }

    /**
     * At a Windows game's end, before the doctor reads it ({@code ran}: the settings it ran with): true when the fix on
     * trial went back ({@link #endedTooSoon}); the library then shows the report's plan, and nothing else is asked.
     */
    static boolean afterGame(Context ctx, Shortcut s, AgvnEvidence ev, Map<String, String> ran) {
        Properties state = AgvnGoodConfig.load(ctx, s);
        if (!endedTooSoon(ev) || !ranWithTrial(s, state, ran)) return false;
        String label = putBack(s, state);
        AgvnGoodConfig.save(ctx, s, state);
        AgvnSessionLog.event("Tự sửa lỗi: game tự tắt sau " + ev.seconds + " giây khi đang thử " + label
                + ": tính là không giúp, trả cấu hình về như trước");
        Log.i(TAG, "trial fix put back after " + ev.seconds + " s for " + s.name);
        AgvnProblemCatalog.Finding f = AgvnDoctor.catalog(ctx).finding(AgvnRepair.symptom(state),
                params(AgvnGodotGame.params(s), label, ev.seconds));
        if (f != null) AgvnDoctorStore.ask(ctx, s, f);
        return true;
    }

    /**
     * The black-screen bar moves the game to Proton 9 ({@link AgvnBlackScreen}) while a fix is on trial: the screen is
     * still black with it, and a trial cannot go on across another Wine. The fix goes back first, counted as tried.
     */
    static void beforeMove(Context ctx, Shortcut s, Properties state, Map<String, String> ran) {
        if (!ranWithTrial(s, state, ran)) return;
        String label = putBack(s, state);
        AgvnGoodConfig.save(ctx, s, state);
        AgvnSessionLog.event("Tự sửa lỗi: màn hình vẫn đen khi đang thử " + label
                + ": tính là không giúp, trả cấu hình về như trước rồi chuyển sang Wine cũ");
    }

    /** The settings from before the first try back on {@code s} (saved); the fix on trial stays tried. Its words. */
    static String putBack(Shortcut s, Properties state) {
        String label = AgvnRepair.label(state);
        AgvnRepair.restore(s, AgvnRepair.before(state));
        s.saveData();
        AgvnRepair.stopTrying(state);
        return label;
    }
}
