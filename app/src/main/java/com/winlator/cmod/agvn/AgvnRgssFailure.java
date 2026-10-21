/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;

import com.winlator.cmod.R;
import com.winlator.cmod.SettingsFragment;
import com.winlator.cmod.core.FileUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

/** What an RPG Maker game on "Chạy nhẹ" that stopped on an error says (AgvnRgssActivity), and where the error stays. */
final class AgvnRgssFailure {
    /** mkxp-z names the script's line ("Script 'Scene_Map' line 27: …"), Ruby's backtrace its frames (":27:in `…'"). */
    private static final Pattern SCRIPT = Pattern.compile("Script '[^']*' line \\d+|:\\d+:in [`']");
    /** What only Windows has: a DLL of the game called through Win32API, which Wine runs and mkxp-z cannot. */
    private static final Pattern WINDOWS_ONLY = Pattern.compile("(?i)win32api|\\.dll\\b|miniffi|dlopen|loadlibrary");

    private AgvnRgssFailure() {}

    /**
     * A script of the game stopped on a Ruby error and said where, not for a DLL only Windows has, nor for an RTP the
     * phone lacks: RGSS on Windows runs the same script, so Windows rarely gets further.
     */
    static boolean scriptError(String error, AgvnRgssConfig config) {
        return SCRIPT.matcher(error).find() && !WINDOWS_ONLY.matcher(error).find() && !missingRtp(error, config);
    }

    private static boolean missingRtp(String error, AgvnRgssConfig config) {
        boolean missingFile = error.contains("ENOENT") || error.contains("No such file") || error.contains("Unable to find");
        return missingFile && config != null && !config.missingRtp.isEmpty();
    }

    /** A missing file while an RTP the game asks for is not on the phone: how to add that RTP. Else the error itself. */
    static String message(Context context, String error, AgvnRgssConfig config) {
        String first = error.length() > 600 ? error.substring(0, 600) + "…" : error;
        if (missingRtp(error, config)) {
            String name = config.missingRtp.get(0);
            File folder = new File(new File(SettingsFragment.DEFAULT_WINLATOR_PATH, AgvnRgssConfig.RTP_FOLDER), name);
            return context.getString(R.string.agvn_rgss_missing_rtp, name, folder.getPath()) + "\n\n" + first;
        }
        int intro = scriptError(error, config) ? R.string.agvn_rgss_script_failed : R.string.agvn_rgss_failed_message;
        return context.getString(intro) + "\n\n" + first;
    }

    /**
     * Asks what to do about {@code error}. A script error offers "Gửi nhật ký" first and Windows only as a try: report
     * of 21/10/2026, the player took "Chạy bằng Windows" for an OK, and the game moved to Wine for good and stayed
     * black. Anything else offers Windows first. After the logs are sent, the same question comes back.
     */
    static void ask(Activity a, String error, AgvnRgssConfig config, boolean windows, Runnable toWindows,
            Runnable close) {
        boolean script = scriptError(error, config);
        String gameName = a.getIntent().getStringExtra("shortcut_name");
        Runnable logs = () -> AgvnLogShare.share(a, gameName, config != null ? config.gameDir : null,
                () -> ask(a, error, config, windows, toWindows, close));
        AlertDialog.Builder b = new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.agvn_rgss_failed_title)
                .setMessage(message(a, error, config))
                .setNegativeButton(R.string.agvn_close, (d, w) -> close.run())
                .setCancelable(false);
        if (script) {
            b.setPositiveButton(R.string.agvn_fix_send_logs, (d, w) -> logs.run());
            if (windows) b.setNeutralButton(R.string.agvn_rgss_try_windows, (d, w) -> toWindows.run());
        } else {
            if (windows) b.setPositiveButton(R.string.agvn_html_use_windows, (d, w) -> toWindows.run());
            b.setNeutralButton(R.string.agvn_fix_send_logs, (d, w) -> logs.run());
        }
        b.show();
    }

    /**
     * Keeps the error where "Gửi nhật ký" in the game's ⋮ menu finds it: a session folder of AGVN-Player/logs/<game>,
     * with the files agvn_files.rb found under another spelling or did not find (AgvnRgssFiles.FILES_LOG).
     */
    static void saveLog(String gameName, String error, AgvnRgssConfig config) {
        File dir = new File(new File(AgvnSessionLog.root(), AgvnLogFolders.safeName(gameName)),
                new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()));
        if (config == null || !dir.mkdirs()) return;
        FileUtils.writeString(new File(dir, AgvnSessionLog.SUMMARY), "runner=rgss (mkxp-z)\nrgss=" + config.rgss + "\ngame="
                + config.gameDir + "\nrtp=" + config.rtpDirs + "\nmissingRtp=" + config.missingRtp + "\nend=error\n");
        FileUtils.writeString(new File(dir, "loi-game.txt"), error);
        String files = config.filesLog != null
                ? AgvnRgssConfig.filesLogOf(FileUtils.readString(config.filesLog), config.gameDir) : null;
        if (files != null) FileUtils.writeString(new File(dir, AgvnRgssFiles.FILES_LOG), files);
    }
}
