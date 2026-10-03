/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;

import com.winlator.cmod.R;
import com.winlator.cmod.SettingsFragment;
import com.winlator.cmod.core.FileUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** What an RPG Maker game on "Chạy nhẹ" that stopped on an error says (AgvnRgssActivity), and where the error stays. */
final class AgvnRgssFailure {
    private AgvnRgssFailure() {}

    /** A missing file while an RTP the game asks for is not on the phone: how to add that RTP. Else the error itself. */
    static String message(Context context, String error, AgvnRgssConfig config) {
        String first = error.length() > 600 ? error.substring(0, 600) + "…" : error;
        boolean missingFile = error.contains("ENOENT") || error.contains("No such file") || error.contains("Unable to find");
        if (missingFile && config != null && !config.missingRtp.isEmpty()) {
            String name = config.missingRtp.get(0);
            File folder = new File(new File(SettingsFragment.DEFAULT_WINLATOR_PATH, AgvnRgssConfig.RTP_FOLDER), name);
            return context.getString(R.string.agvn_rgss_missing_rtp, name, folder.getPath()) + "\n\n" + first;
        }
        return context.getString(R.string.agvn_rgss_failed_message) + "\n\n" + first;
    }

    /** Keeps the error where "Gửi nhật ký" in the game's ⋮ menu finds it: a session folder of AGVN-Player/logs/<game>. */
    static void saveLog(String gameName, String error, AgvnRgssConfig config) {
        File dir = new File(new File(AgvnSessionLog.root(), AgvnLogFolders.safeName(gameName)),
                new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()));
        if (config == null || !dir.mkdirs()) return;
        FileUtils.writeString(new File(dir, AgvnSessionLog.SUMMARY), "runner=rgss (mkxp-z)\nrgss=" + config.rgss + "\ngame="
                + config.gameDir + "\nrtp=" + config.rtpDirs + "\nmissingRtp=" + config.missingRtp + "\nend=error\n");
        FileUtils.writeString(new File(dir, "loi-game.txt"), error);
    }
}
