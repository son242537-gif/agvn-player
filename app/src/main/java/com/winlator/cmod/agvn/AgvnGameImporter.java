/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.os.Environment;

import com.winlator.cmod.SettingsFragment;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.ExeIconExtractor;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.xenvironment.ImageFs;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns /sdcard/AGVN/<GAME>/agvn-profile.json into a library shortcut. The profile is copied to
 * /sdcard/AGVN-Player/profiles/<GAME>/ and the shortcut stores its path in the "agvnProfilePath" extra.
 */
public final class AgvnGameImporter {
    public static final String EXTRA_PROFILE_PATH = "agvnProfilePath";
    public static final String EXTRA_GAME_DIR = "agvnGameDir";
    public static final String EXTRA_TEXTURE_POOL = "agvnTexturePool";
    public static final String EXTRA_ENGINE = "agvnEngine";

    /** A validated profile ready to import. */
    public static final class Candidate {
        public final File gameDir;
        public final AgvnProfile profile;
        public final String exe;
        public final GameExeResolver.Engine engine;

        Candidate(File gameDir, AgvnProfile profile, String exe, GameExeResolver.Engine engine) {
            this.gameDir = gameDir;
            this.profile = profile;
            this.exe = exe;
            this.engine = engine;
        }
    }

    private AgvnGameImporter() {}

    public static File getGamesRoot() {
        return new File(Environment.getExternalStorageDirectory(), "AGVN");
    }

    public static File getProfilesRoot() {
        return new File(SettingsFragment.DEFAULT_WINLATOR_PATH, "profiles");
    }

    /** Game folders directly under /sdcard/AGVN that contain agvn-profile.json. */
    public static List<File> listGameDirs() {
        List<File> dirs = new ArrayList<>();
        File[] children = getGamesRoot().listFiles();
        if (children == null) return dirs;
        java.util.Arrays.sort(children);
        for (File dir : children) {
            if (dir.isDirectory() && new File(dir, AgvnProfile.FILE_NAME).isFile()) dirs.add(dir);
        }
        return dirs;
    }

    public static Candidate load(File gameDir) throws AgvnProfileException {
        String json = FileUtils.readString(new File(gameDir, AgvnProfile.FILE_NAME));
        if (json == null) throw new AgvnProfileException("Không đọc được file agvn-profile.json.");
        AgvnProfile profile = AgvnProfile.parse(json);
        String exe = AgvnProfileValidator.validate(profile, gameDir);
        return new Candidate(gameDir, profile, exe, GameExeResolver.detectEngine(gameDir));
    }

    /** Creates (or replaces) the shortcut in {@code container}; returns the .desktop file. */
    public static File importGame(Context ctx, Container container, Candidate c) throws IOException {
        String name = c.profile.name.trim();
        File profileDir = new File(getProfilesRoot(), c.gameDir.getName());
        profileDir.mkdirs();
        File profileCopy = new File(profileDir, AgvnProfile.FILE_NAME);
        if (!FileUtils.copy(new File(c.gameDir, AgvnProfile.FILE_NAME), profileCopy))
            throw new IOException("cannot copy profile to " + profileCopy);

        File exeFile = new File(c.gameDir, c.exe);
        File desktopDir = container.getDesktopDir();
        desktopDir.mkdirs();
        File desktopFile = new File(desktopDir, name + ".desktop");
        try (PrintWriter writer = new PrintWriter(desktopFile, "UTF-8")) {
            writer.println("[Desktop Entry]");
            writer.println("Name=" + name);
            writer.println("Exec=env WINEPREFIX=\"" + wineHome(ctx, container) + "/.wine\" wine \"" + exeFile.getAbsolutePath() + "\"");
            writer.println("Type=Application");
            writer.println("Icon=" + name);
        }

        Shortcut shortcut = new Shortcut(container, desktopFile);
        applyProfile(shortcut, c, profileCopy);
        shortcut.saveData();
        shortcut.genUUID();
        extractIcon(container, exeFile, name);
        return desktopFile;
    }

    static void applyProfile(Shortcut shortcut, Candidate c, File profileCopy) {
        AgvnProfile p = c.profile;
        shortcut.putExtra("execArgs", String.join(" ", p.args));
        shortcut.putExtra("envVars", buildEnvVars(p.env, p.getFpsLimit()));
        if (p.resolution != null) shortcut.putExtra("screenSize", p.resolution);
        shortcut.putExtra("simTouchScreen", p.isSimulatedTouchscreen() ? "1" : "0");
        shortcut.putExtra(EXTRA_PROFILE_PATH, profileCopy.getAbsolutePath());
        shortcut.putExtra(EXTRA_GAME_DIR, c.gameDir.getAbsolutePath());
        shortcut.putExtra(EXTRA_TEXTURE_POOL, String.valueOf(p.getTexturePool()));
        shortcut.putExtra(EXTRA_ENGINE, c.engine.name());
    }

    /** "KEY=VALUE KEY2=VALUE2"; the FPS cap uses DXVK's DXVK_FRAME_RATE. */
    static String buildEnvVars(Map<String, String> env, int fpsLimit) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : env.entrySet()) {
            if (e.getKey().equals("DXVK_FRAME_RATE")) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        if (fpsLimit > 0) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("DXVK_FRAME_RATE=").append(fpsLimit);
        }
        return sb.toString();
    }

    private static String wineHome(Context ctx, Container container) {
        String imagefs = canonical(new File(ctx.getFilesDir(), "imagefs"));
        String root = canonical(container.getRootDir());
        return root.startsWith(imagefs) ? root.substring(imagefs.length()) : "/home/" + ImageFs.USER;
    }

    private static String canonical(File f) {
        try {
            return f.getCanonicalPath();
        } catch (IOException e) {
            return f.getAbsolutePath();
        }
    }

    private static void extractIcon(Container container, File exeFile, String name) {
        try {
            File iconDir = container.getIconsDir(64);
            iconDir.mkdirs();
            File icon = new File(iconDir, name + ".png");
            if (ExeIconExtractor.extractIcon(exeFile, icon)) {
                File userIcons = new File(SettingsFragment.DEFAULT_WINLATOR_PATH, "icons");
                userIcons.mkdirs();
                FileUtils.copy(icon, new File(userIcons, name + ".png"));
            }
        } catch (Exception ignored) {
            // the library falls back to a generic icon
        }
    }
}
