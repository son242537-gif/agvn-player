/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.winlator.cmod.SettingsFragment;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Starts an RPG Maker XP/VX/VX Ace game on "Chạy nhẹ" and lays out what mkxp-z reads before it: mkxp.json, mkxp-z's
 * preload scripts (assets/agvn/mkxp-z, CC0) and the app's MIDI sound font, in files/rgss (SRCDIR).
 */
final class AgvnRgssFiles {
    private static final String TAG = "AGVN";
    /**
     * The preload scripts: mkxp-z's own (Ruby 1.8/1.9 names RGSS scripts use, mkxp names, Win32API stand-ins), then
     * AGVN's agvn_fps.rb, which reports the frames the game runs per second (the HUD, "Tự sửa lỗi").
     */
    static final String[] PRELOAD = {"ruby_classic_wrap.rb", "mkxp_wrap.rb", "win32_wrap.rb", "agvn_fps.rb"};
    /** The game's own "skip frames when behind" ("Tự sửa lỗi" turns it on where the phone's tier did not). */
    static final String EXTRA_FRAME_SKIP = "agvnRgssFrameSkip";
    static final String SOUND_FONT = "soundfonts/wt_210k_G.sf2";

    private AgvnRgssFiles() {}

    /**
     * Called by AgvnHtmlGame.redirect for a shortcut set to "Chạy nhẹ" RGSS. Returns false, so the game runs in Wine,
     * when its folder moved or no longer holds an XP, VX or VX Ace game.
     */
    static boolean start(Activity activity, Intent from, Map<String, String> extras) {
        String dir = extras.get(AgvnGameImporter.EXTRA_GAME_DIR);
        File gameDir = dir != null && !dir.isEmpty() ? new File(dir) : null;
        if (!AgvnRgssGame.canRun(gameDir)) return false;
        Intent intent = new Intent(activity, AgvnRgssActivity.class);
        if (from.getExtras() != null) intent.putExtras(from.getExtras());
        intent.putExtra(AgvnRgssActivity.EXTRA_GAME_DIR, gameDir.getPath());
        intent.putExtra(AgvnRgssActivity.EXTRA_FRAME_SKIP, "1".equals(extras.get(EXTRA_FRAME_SKIP)));
        File driveC = driveC(activity, from);
        if (driveC != null) intent.putExtra(AgvnRgssActivity.EXTRA_DRIVE_C, driveC.getPath());
        activity.startActivity(intent);
        return true;
    }

    /** drive_c of the game's Wine container, where an RTP installed for "Chạy bằng Windows" lives; null if none. */
    private static File driveC(Activity activity, Intent from) {
        int id = from.getIntExtra("container_id", 0);
        String path = from.getStringExtra("shortcut_path");
        if (id == 0 && path != null) id = AgvnHtmlGame.containerIdIn(new File(path));
        Container container = id != 0 ? new ContainerManager(activity).getContainerById(id) : null;
        return container != null ? new File(container.getRootDir(), ".wine/drive_c") : null;
    }

    /** True when mkxp-z skips drawing a frame it is behind on: weak and mid phones, or a game "Tự sửa lỗi" set. */
    static boolean frameSkip(Context context, Shortcut shortcut) {
        return DeviceTierManager.current(context) != DeviceTier.FLAGSHIP || "1".equals(shortcut.getExtra(EXTRA_FRAME_SKIP));
    }

    /** Writes everything mkxp-z needs; returns the config, or null when the files cannot be written. */
    static AgvnRgssConfig prepare(Context context, File gameDir, File driveC, File runDir, boolean forceFrameSkip) {
        try {
            if (!runDir.isDirectory() && !runDir.mkdirs()) throw new IOException("cannot create " + runDir);
            List<File> preload = new ArrayList<>();
            for (String name : PRELOAD) preload.add(copyAsset(context, "agvn/mkxp-z/" + name, new File(runDir, name), false));
            File soundFont = copyAsset(context, SOUND_FONT, new File(runDir, new File(SOUND_FONT).getName()), true);
            AgvnRgssConfig config = new AgvnRgssConfig(gameDir, new File(SettingsFragment.DEFAULT_WINLATOR_PATH), driveC);
            try (OutputStream out = new FileOutputStream(new File(runDir, "mkxp.json"))) {
                // weak and mid phones skip drawing a frame when they fall behind, rather than slowing the game down
                boolean frameSkip = forceFrameSkip || DeviceTierManager.current(context) != DeviceTier.FLAGSHIP;
                out.write(config.json(soundFont, preload, frameSkip).getBytes(StandardCharsets.UTF_8));
            }
            Log.i(TAG, "RGSS" + config.rgss + " game " + gameDir + ", RTP " + config.rtpDirs + ", missing " + config.missingRtp);
            return config;
        } catch (IOException e) {
            Log.e(TAG, "mkxp-z files could not be written", e);
            return null;
        }
    }

    /**
     * Copies an asset (the APK's assets cannot be read by path). The scripts are small and copied every time; the sound
     * font only when the copy there is missing or has another size.
     */
    private static File copyAsset(Context context, String asset, File target, boolean keepSameSize) throws IOException {
        try (InputStream in = context.getAssets().open(asset)) {
            int size = in.available();
            if (keepSameSize && target.isFile() && size > 0 && target.length() == size) return target;
            try (OutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[65536];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            }
        }
        return target;
    }
}
