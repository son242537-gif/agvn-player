/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.container.Shortcut;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
     * had dropped it, and two fixes went back counted as tried after "42 s", 28 of them stopped. Not a game the player
     * left by its own Exit menu ({@link #cleanExit}), unless its end shows a problem all the same ({@code problem}: the
     * doctor's catalog finds one, as Unreal's crash box, after which the game ends with code 0 too): a look at the fix
     * and out within a minute took it off the game as tried (audit of 20/11/2026).
     */
    static boolean endedTooSoon(AgvnEvidence ev, boolean problem) {
        if (ev.stopped || ev.seconds >= AgvnEvidence.GOOD_AFTER_S) return false;
        return ev.crashed || ev.endedByGame && (problem || !cleanExit(ev));
    }

    /** The game drew its window and ended with an exit code agvn-winhandler.exe told, not a crash's (AgvnGameEnd). */
    static boolean cleanExit(AgvnEvidence ev) {
        return ev.started && ev.exitKnown && !ev.exitCrash;
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
        if (!ranWithTrial(s, state, ran) || !endedTooSoon(ev, AgvnDoctor.catalog(ctx).find(ev) != null)) return false;
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
        goBack(s, state);
        s.saveData();
        AgvnRepair.stopTrying(state);
        return label;
    }

    /**
     * The settings from before the first try back on {@code s}, the fix on trial in {@code state} with them: the move
     * "Chạy bằng Wine mới" asked for is no setting, and the game still moved to Proton 10 at its next start though the
     * player had gone on to another fix (audit of 20/11/2026). The caller saves.
     */
    static void goBack(Shortcut s, Properties state) {
        AgvnRepair.restore(s, AgvnRepair.before(state));
        for (String extra : moveExtras(AgvnRepair.trying(state))) s.putExtra(extra, null);
    }

    /** The extras fix {@code trying} left on the game besides its settings: the move to Proton 10 not done yet. */
    static List<String> moveExtras(String trying) {
        return AgvnWine10Fix.ID.equals(trying) ? Arrays.asList(AgvnGameMove.EXTRA_TO, AgvnWine10Fix.EXTRA_WANTED)
                : Collections.emptyList();
    }
}
