/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.os.Environment;

import com.winlator.cmod.R;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.core.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One game on the "Thêm game" screen: readable title, "Engine · chỗ để" line and whether it is already in the library.
 * A folder that holds several games (a collection) gives one row per game.
 */
public final class AgvnImportEntry {
    public final File dir;
    public final String title;
    public final String sortKey;
    public final String detail;
    public final boolean hasProfile;
    /** Exe used for the thumbnail; null when none was found. */
    public final File exe;
    /** The folder's engine: Ren'Py and RPG Maker rows show the game's own picture instead of the engine's exe icon. */
    public final GameExeResolver.Engine engine;
    /** Shortcut the game already has, or null. */
    public final AgvnLibraryIndex.Existing existing;
    /** The exe this row starts when the folder holds several games or the player picked it; null for the folder's game. */
    public final String variant;
    /** Unique per row (list key): the folder, plus the exe for a variant. */
    public final String key;

    AgvnImportEntry(File dir, String title, String detail, boolean hasProfile, File exe, GameExeResolver.Engine engine,
                    AgvnLibraryIndex.Existing existing, String variant) {
        this.dir = dir;
        this.title = title;
        this.sortKey = AgvnGameTitle.searchKey(title);
        this.detail = detail;
        this.hasProfile = hasProfile;
        this.exe = exe;
        this.engine = engine;
        this.existing = existing;
        this.variant = variant;
        this.key = variant == null ? dir.getAbsolutePath() : dir.getAbsolutePath() + "|" + variant;
    }

    /** Scans storage (slow: call off the main thread); "Chưa thêm" games first, each group A→Z ignoring accents. */
    public static List<AgvnImportEntry> scan(Context ctx) {
        List<File> dirs = AgvnGameImporter.listGameDirs(AgvnGameRoots.load(ctx));
        AgvnLibraryIndex library = new AgvnLibraryIndex(new ContainerManager(ctx).loadShortcuts());
        AgvnProfileCatalog catalog = AgvnProfileCatalog.get(ctx);
        List<AgvnImportEntry> out = new ArrayList<>();
        for (File dir : dirs) out.addAll(build(ctx, dir, library, catalog, null));
        Collections.sort(out, (a, b) -> {
            if ((a.existing == null) != (b.existing == null)) return a.existing == null ? -1 : 1;
            return a.sortKey.compareTo(b.sortKey);
        });
        return out;
    }

    /**
     * The row for a file picked in "Chọn thư mục khác" (slow: off the main thread). An exe: the folder's game or one of
     * its games when it is one of them, else a game of its own named after the exe. index.html, Game.ini or an RPG Maker
     * archive: the game of that folder (MV's www/ counts as the folder above it), also a phone copy without an exe.
     */
    public static AgvnImportEntry forFile(Context ctx, File file) {
        AgvnLibraryIndex library = new AgvnLibraryIndex(new ContainerManager(ctx).loadShortcuts());
        boolean exe = file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".exe");
        File dir = exe ? file.getParentFile() : AgvnLightGame.folderOf(file);
        return build(ctx, dir, library, AgvnProfileCatalog.get(ctx), exe ? file.getName() : null).get(0);
    }

    /** The folder's game, one row per game when it holds several, or only the row of {@code picked} (an exe name). */
    private static List<AgvnImportEntry> build(Context ctx, File dir, AgvnLibraryIndex library, AgvnProfileCatalog catalog,
                                               String picked) {
        File profileFile = new File(dir, AgvnProfile.FILE_NAME);
        boolean hasProfile = profileFile.isFile();
        GameExeResolver.Engine engine = GameExeResolver.detectEngine(dir);
        String title = null;
        String exe = null;
        if (hasProfile) {
            try {
                AgvnProfile p = AgvnProfile.parse(FileUtils.readString(profileFile));
                if (p.name != null && !p.name.trim().isEmpty()) title = p.name.trim();
                if (p.exe != null && !p.exe.trim().isEmpty()) exe = p.exe.trim();
            } catch (Exception ignored) {
                // a broken profile is reported when the player taps the game
            }
        } else {
            AgvnProfile p = catalog.find(dir);
            if (p != null) {
                hasProfile = true;
                title = p.name.trim();
                if (p.exe != null && !p.exe.trim().isEmpty()) exe = p.exe.trim();
            }
        }
        if (title == null) title = AgvnGameTitle.pretty(dir.getName());
        if (exe == null) exe = GameExeResolver.resolveExe(dir, engine);
        String detail = engineLabel(ctx, engine) + " · " + place(ctx, dir);
        List<String> games = hasProfile ? Collections.<String>emptyList() : GameExeResolver.gameExes(dir, engine);
        List<AgvnImportEntry> rows = new ArrayList<>();
        if (picked != null) {
            // the folder's game when it starts this exe (an Unreal folder always starts Shipping: its root exe is a bootstrap)
            boolean folderGame = games.size() <= 1 && (picked.equalsIgnoreCase(exe) || engine == GameExeResolver.Engine.UNREAL);
            rows.add(row(dir, title, detail, hasProfile, picked, engine, folderGame ? null : picked, library));
        } else if (games.size() > 1) {
            for (String game : games) rows.add(row(dir, title, detail, false, game, engine, game, library));
        } else {
            rows.add(row(dir, title, detail, hasProfile, exe, engine, null, library));
        }
        return rows;
    }

    private static AgvnImportEntry row(File dir, String title, String detail, boolean hasProfile, String exe,
                                       GameExeResolver.Engine engine, String variant, AgvnLibraryIndex library) {
        String shown = variant != null ? AgvnProfile.variantName(title, variant) : title;
        return new AgvnImportEntry(dir, shown, detail, hasProfile, exe != null ? new File(dir, exe) : null, engine,
                library.find(dir, variant), variant);
    }

    static String engineLabel(Context ctx, GameExeResolver.Engine engine) {
        switch (engine) {
            case UNREAL: return "Unreal";
            case UNITY: return "Unity";
            case GODOT: return "Godot";
            case GAMEMAKER: return "GameMaker";
            case RENPY: return "Ren'Py";
            case KIRIKIRI: return "KiriKiri";
            case TYRANO: return "Tyrano";
            case SIGLUS: return "Siglus";
            case NSCRIPTER: return "NScripter";
            case RPGMAKER: return "RPG Maker";
            case RPGMAKER_MV: return "RPG Maker MV";
            case WOLFRPG: return "Wolf RPG";
            default: return ctx.getString(R.string.agvn_add_engine_unknown);
        }
    }

    /** "AGVN", "Download", "Bộ nhớ trong" or "Thẻ nhớ/Download": the top folder the game sits in. */
    static String place(Context ctx, File dir) {
        String path = dir.getAbsolutePath();
        File internal = Environment.getExternalStorageDirectory();
        for (File volume : AgvnGameImporter.storageVolumes()) {
            String root = volume.getAbsolutePath() + File.separator;
            if (!path.startsWith(root)) continue;
            String rest = path.substring(root.length());
            int slash = rest.indexOf(File.separatorChar);
            String top = slash > 0 ? rest.substring(0, slash) : null;
            if (volume.equals(internal)) return top != null ? top : ctx.getString(R.string.agvn_internal_storage);
            String card = ctx.getString(R.string.agvn_add_sd_card);
            return top != null ? card + "/" + top : card;
        }
        File parent = dir.getParentFile();
        return parent != null ? parent.getName() : path;
    }
}
