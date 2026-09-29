/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
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

/** One game on the "Thêm game" screen: readable title, "Engine · chỗ để" line and whether it is already in the library. */
public final class AgvnImportEntry {
    public final File dir;
    public final String title;
    public final String sortKey;
    public final String detail;
    public final boolean hasProfile;
    /** Exe used for the thumbnail; null when none was found. */
    public final File exe;
    /** Shortcut the game already has, or null. */
    public final AgvnLibraryIndex.Existing existing;

    AgvnImportEntry(File dir, String title, String detail, boolean hasProfile, File exe, AgvnLibraryIndex.Existing existing) {
        this.dir = dir;
        this.title = title;
        this.sortKey = AgvnGameTitle.searchKey(title);
        this.detail = detail;
        this.hasProfile = hasProfile;
        this.exe = exe;
        this.existing = existing;
    }

    /** Scans storage (slow: call off the main thread); "Chưa thêm" games first, each group A→Z ignoring accents. */
    public static List<AgvnImportEntry> scan(Context ctx) {
        List<File> dirs = AgvnGameImporter.listGameDirs(AgvnGameRoots.load(ctx));
        AgvnLibraryIndex library = new AgvnLibraryIndex(new ContainerManager(ctx).loadShortcuts());
        AgvnProfileCatalog catalog = AgvnProfileCatalog.get(ctx);
        List<AgvnImportEntry> out = new ArrayList<>();
        for (File dir : dirs) out.add(build(ctx, dir, library, catalog));
        Collections.sort(out, (a, b) -> {
            if ((a.existing == null) != (b.existing == null)) return a.existing == null ? -1 : 1;
            return a.sortKey.compareTo(b.sortKey);
        });
        return out;
    }

    private static AgvnImportEntry build(Context ctx, File dir, AgvnLibraryIndex library, AgvnProfileCatalog catalog) {
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
        return new AgvnImportEntry(dir, title, detail, hasProfile, exe != null ? new File(dir, exe) : null, library.find(dir));
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
