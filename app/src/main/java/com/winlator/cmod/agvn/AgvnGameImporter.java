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
    public static final String EXTRA_TIER = "agvnTier";

    /** A validated profile ready to import. */
    public static final class Candidate {
        public final File gameDir;
        public final AgvnProfile profile;
        public final String exe;
        public final GameExeResolver.Engine engine;
        /** The exe of one game of a folder that holds several, or of an exe picked by hand; null for the folder's game. */
        public final String variant;

        Candidate(File gameDir, AgvnProfile profile, String exe, GameExeResolver.Engine engine, String variant) {
            this.gameDir = gameDir;
            this.profile = profile;
            this.exe = exe;
            this.engine = engine;
            this.variant = variant;
        }
    }

    private AgvnGameImporter() {}

    public static File getGamesRoot() {
        return new File(Environment.getExternalStorageDirectory(), "AGVN");
    }

    public static File getProfilesRoot() {
        return new File(SettingsFragment.DEFAULT_WINLATOR_PATH, "profiles");
    }

    /**
     * Game folders found anywhere a player is likely to copy them: folders picked with "Chọn thư mục khác" (3 levels,
     * the folder itself included), internal storage/AGVN, Download and Games (3 levels), the storage root (2 levels:
     * /sdcard/NINJA DISGRACE/Shinobi), and the same places on SD cards / USB drives. A folder holding just one folder
     * (a download unpacked into its own folder) costs no level. Each folder is listed once.
     */
    public static List<File> listGameDirs(List<File> extraRoots) {
        List<AgvnGameScanner.Root> roots = new ArrayList<>();
        for (File extra : extraRoots) roots.add(new AgvnGameScanner.Root(extra, AgvnGameRoots.DEPTH, true));
        for (File volume : storageVolumes()) {
            roots.add(new AgvnGameScanner.Root(new File(volume, "AGVN"), 3));
            roots.add(new AgvnGameScanner.Root(new File(volume, "Download"), 3));
            roots.add(new AgvnGameScanner.Root(new File(volume, "Games"), 3));
            roots.add(new AgvnGameScanner.Root(volume, 2));
        }
        return AgvnGameScanner.scan(roots);
    }

    /** Internal storage first, then removable volumes mounted under /storage. */
    public static List<File> storageVolumes() {
        List<File> volumes = new ArrayList<>();
        volumes.add(Environment.getExternalStorageDirectory());
        File[] mounted = new File("/storage").listFiles();
        if (mounted != null) {
            for (File f : mounted) {
                String n = f.getName();
                if (!n.equals("emulated") && !n.equals("self") && f.isDirectory() && f.canRead()) volumes.add(f);
            }
        }
        return volumes;
    }

    /** True when the app cannot list shared storage (missing "all files" / storage permission). */
    public static boolean storageBlocked() {
        File root = Environment.getExternalStorageDirectory();
        if (android.os.Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            return root.listFiles() == null;
        }
        return !root.canRead() || root.listFiles() == null;
    }

    public static Candidate load(File gameDir) throws AgvnProfileException {
        return load(gameDir, AgvnProfileCatalog.EMPTY);
    }

    public static Candidate load(File gameDir, AgvnProfileCatalog catalog) throws AgvnProfileException {
        return load(gameDir, catalog, null);
    }

    /**
     * The folder's own agvn-profile.json, else a matching bundled profile, else auto settings. {@code variant}: the exe
     * to start instead (one game of several, or picked by hand); its name gets the exe's ("Collection - game2").
     */
    public static Candidate load(File gameDir, AgvnProfileCatalog catalog, String variant) throws AgvnProfileException {
        File profileFile = new File(gameDir, AgvnProfile.FILE_NAME);
        AgvnProfile profile;
        if (profileFile.isFile()) {
            String json = FileUtils.readString(profileFile);
            if (json == null || json.isEmpty()) throw new AgvnProfileException("Không đọc được file agvn-profile.json.");
            profile = AgvnProfile.parse(json);
        } else {
            profile = catalog.find(gameDir);
            if (profile == null) profile = AgvnProfile.defaultFor(gameDir.getName());
        }
        if (variant != null) {
            profile.exe = variant;
            profile.name = AgvnProfile.variantName(profile.name, variant);
        }
        String exe = AgvnProfileValidator.validate(profile, gameDir);
        return new Candidate(gameDir, profile, exe, GameExeResolver.detectEngine(gameDir), variant);
    }

    /** Creates (or replaces) the shortcut in {@code container}; returns the .desktop file. */
    public static File importGame(Context ctx, Container container, Candidate c, DeviceTier tier) throws IOException {
        String name = c.profile.name.trim();
        String folder = c.gameDir.getName();
        File profileDir = new File(getProfilesRoot(), c.variant == null ? folder : AgvnProfile.variantName(folder, c.variant));
        profileDir.mkdirs();
        File profileCopy = new File(profileDir, AgvnProfile.FILE_NAME);
        if (!FileUtils.writeString(profileCopy, c.profile.toJson()))
            throw new IOException("cannot write profile to " + profileCopy);

        File exeFile = new File(c.gameDir, c.exe);
        File desktopDir = container.getDesktopDir();
        desktopDir.mkdirs();
        File desktopFile = new File(desktopDir, name + ".desktop");
        Map<String, String> kept = keptExtras(container, desktopFile);
        try (PrintWriter writer = new PrintWriter(desktopFile, "UTF-8")) {
            writer.println("[Desktop Entry]");
            writer.println("Name=" + name);
            writer.println("Exec=env WINEPREFIX=\"" + wineHome(ctx, container) + "/.wine\" wine \"" + exeFile.getAbsolutePath() + "\"");
            writer.println("Type=Application");
            writer.println("Icon=" + name);
        }

        AgvnControls.ensureProfiles(ctx);
        Shortcut shortcut = new Shortcut(container, desktopFile);
        LaunchPresetResolver.Effective eff = LaunchPresetResolver.resolve(c.profile, tier, DeviceTierManager.getRules(ctx).preset(tier));
        for (Map.Entry<String, String> e : kept.entrySet()) shortcut.putExtra(e.getKey(), e.getValue());
        applyProfile(shortcut, c, profileCopy, eff);
        shortcut.putExtra(EXTRA_TIER, tier.name());
        shortcut.saveData();
        shortcut.genUUID();
        PreLaunchCheck.applyUeConfig(shortcut);
        extractIcon(container, exeFile, name);
        return desktopFile;
    }

    /** Extras a re-import must not lose: the launcher-shortcut id, favourite flag, last run and a picked cover. */
    static final String[] KEPT_EXTRAS = {"uuid", "favorite", "lastRunAt", "customCoverArtPath",
            AgvnHtmlGame.EXTRA_RUNNER};

    private static Map<String, String> keptExtras(Container container, File desktopFile) {
        Map<String, String> kept = new java.util.HashMap<>();
        if (!desktopFile.isFile()) return kept;
        Shortcut old = new Shortcut(container, desktopFile);
        for (String key : KEPT_EXTRAS) {
            String value = old.getExtra(key);
            if (!value.isEmpty()) kept.put(key, value);
        }
        return kept;
    }

    static void applyProfile(Shortcut shortcut, Candidate c, File profileCopy, LaunchPresetResolver.Effective eff) {
        AgvnProfile p = c.profile;
        shortcut.putExtra("execArgs", String.join(" ", p.args));
        File exeDir = new File(c.gameDir, c.exe).getParentFile();
        String dlls = GameDllOverrides.build(GameDllOverrides.detect(exeDir), p.dllOverrides);
        shortcut.putExtra("envVars", buildEnvVars(p.env, dlls));
        AgvnQuality.setStartFps(shortcut, eff.fps);
        AgvnLayouts.applyImport(shortcut, AgvnLayouts.kindFor(p, c.engine));
        if (eff.resolution != null) shortcut.putExtra("screenSize", eff.resolution);
        shortcut.putExtra("simTouchScreen", p.isSimulatedTouchscreen() ? "1" : "0");
        shortcut.putExtra(EXTRA_PROFILE_PATH, profileCopy.getAbsolutePath());
        shortcut.putExtra(EXTRA_GAME_DIR, c.gameDir.getAbsolutePath());
        boolean unreal = c.engine == GameExeResolver.Engine.UNREAL;
        shortcut.putExtra(EXTRA_TEXTURE_POOL, String.valueOf(unreal ? eff.texturePool : 0));
        shortcut.putExtra(EXTRA_ENGINE, c.engine.name());
        shortcut.putExtra("lc_all", AgvnLocale.forGame(p, c.engine, c.gameDir.getName(), c.exe));
        File index = AgvnHtmlGame.indexFor(c.gameDir, c.engine);
        // a player who picked "Chạy bằng Windows" keeps it on re-import unless the profile decides
        boolean pickedWine = p.runner == null && AgvnHtmlGame.RUNNER_WINE.equals(shortcut.getExtra(AgvnHtmlGame.EXTRA_RUNNER));
        boolean html = AgvnHtmlGame.useHtml(p, index) && !pickedWine;
        boolean renpy = !html && !pickedWine && AgvnRenpyGame.useRenpy(p, c.engine, c.gameDir);
        shortcut.putExtra(AgvnHtmlGame.EXTRA_RUNNER, html ? AgvnHtmlGame.RUNNER_HTML : renpy ? AgvnHtmlGame.RUNNER_RENPY
                : pickedWine ? AgvnHtmlGame.RUNNER_WINE : null);
        shortcut.putExtra(AgvnHtmlGame.EXTRA_INDEX, html ? index.getAbsolutePath() : null);
    }

    /**
     * "KEY=VALUE KEY2=VALUE2"; mod DLLs go to WINEDLLOVERRIDES. A profile's DXVK_FRAME_RATE is dropped: the FPS cap is
     * where the in-game "Giới hạn FPS" starts (AgvnQuality.setStartFps), which the player can change in game.
     */
    static String buildEnvVars(Map<String, String> env, String dllOverrides) {
        StringBuilder sb = new StringBuilder();
        String overrides = dllOverrides != null ? dllOverrides : "";
        String fromEnv = env.get("WINEDLLOVERRIDES");
        if (fromEnv != null && !fromEnv.isEmpty()) overrides = overrides.isEmpty() ? fromEnv : fromEnv + ";" + overrides;
        if (!overrides.isEmpty()) sb.append("WINEDLLOVERRIDES=").append(overrides);
        for (Map.Entry<String, String> e : env.entrySet()) {
            if (e.getKey().equals("DXVK_FRAME_RATE") || e.getKey().equals("WINEDLLOVERRIDES")) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(e.getKey()).append('=').append(e.getValue());
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
