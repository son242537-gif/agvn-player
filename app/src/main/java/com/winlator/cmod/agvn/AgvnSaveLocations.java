/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.GameSaveManager;
import com.winlator.cmod.xenvironment.ImageFs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Where a game keeps its saves, for "Nhập save" / "Xuất save" in the library menu. Game-folder places come first,
 * because most visual novel and RPG engines save next to the game:
 * <ul>
 *   <li>Ren'Py game/saves; RPG Maker MV www/save and MZ save; Wolf RPG Save; KiriKiri and Siglus savedata;</li>
 *   <li>RPG Maker XP/VX/Ace (Save*.rxdata/rvdata/rvdata2) and NScripter (save*.dat, gloval.sav, kidoku.dat, envdata):
 *       only those files, directly in the game folder.</li>
 * </ul>
 * Then folders in the container's Windows user folder (users/xuser): Unity AppData/LocalLow/&lt;company&gt;/&lt;product&gt;,
 * Unreal AppData/Local/&lt;project&gt;/Saved/SaveGames, and those {@link GameSaveManager} finds by the game's name.
 * Unity PlayerPrefs, which live in the registry, are handled by {@link AgvnSavePrefs}.
 */
final class AgvnSaveLocations {
    static final Pattern RPGMAKER_SAVES = Pattern.compile("(?i)save\\d*\\.(rxdata|rvdata2?)");
    static final Pattern NSCRIPTER_SAVES = Pattern.compile("(?i)save\\d+\\.dat|glo[bv]al\\.sav|kidoku\\.dat|envdata");

    /** A folder whose files are saves: all of them (recursively), or only those directly in it matching {@link #files}. */
    static final class Location {
        final File dir;
        /** Where the files go in an exported zip: "game/..." or "profile/...". */
        final String zipPath;
        final Pattern files;

        Location(File dir, String zipPath, Pattern files) {
            this.dir = dir;
            this.zipPath = zipPath;
            this.files = files;
        }

        boolean takes(String name) {
            return files == null || files.matcher(name).matches();
        }
    }

    private AgvnSaveLocations() {}

    /** Every place the shortcut's game may keep saves; the first one is where "Nhập save" puts plain files. */
    static List<Location> find(Shortcut shortcut) {
        String engine = shortcut.getExtra(AgvnGameImporter.EXTRA_ENGINE);
        File exe = new File(shortcut.path.replace("\"", ""));
        String gameDirPath = shortcut.getExtra(AgvnGameImporter.EXTRA_GAME_DIR);
        File gameDir = !gameDirPath.isEmpty() ? new File(gameDirPath) : exe.isAbsolute() ? exe.getParentFile() : null;
        List<Location> out = new ArrayList<>();
        if (gameDir != null) out.addAll(inGameFolder(gameDir, engine));
        File profile = new File(shortcut.container.getRootDir(), ".wine/drive_c/users/" + ImageFs.USER);
        for (String rel : inProfile(exe, gameDir, engine, GameSaveManager.getSaveRoots(shortcut)))
            out.add(new Location(new File(profile, rel), "profile/" + rel, null));
        return out;
    }

    /** Save places inside the game folder for {@code engine} (a {@link GameExeResolver.Engine} name). */
    static List<Location> inGameFolder(File gameDir, String engine) {
        List<Location> out = new ArrayList<>();
        switch (engine) {
            case "RENPY": folder(out, gameDir, "game/saves"); break;
            case "RPGMAKER_MV": folder(out, gameDir, new File(gameDir, "www").isDirectory() ? "www/save" : "save"); break;
            case "WOLFRPG": folder(out, gameDir, "Save"); break;
            case "KIRIKIRI":
            case "SIGLUS": folder(out, gameDir, "savedata"); break;
            case "RPGMAKER": out.add(new Location(gameDir, "game", RPGMAKER_SAVES)); break;
            case "NSCRIPTER": out.add(new Location(gameDir, "game", NSCRIPTER_SAVES)); break;
            default: break;
        }
        return out;
    }

    private static void folder(List<Location> out, File gameDir, String rel) {
        out.add(new Location(new File(gameDir, rel), "game/" + rel, null));
    }

    /** Save folders relative to users/xuser: the engine's own first, then {@code discovered} ones not inside it. */
    static List<String> inProfile(File exe, File gameDir, String engine, List<String> discovered) {
        List<String> rels = new ArrayList<>();
        if ("UNITY".equals(engine)) {
            String[] names = unityNames(exe);
            if (names != null) rels.add("AppData/LocalLow/" + names[0] + "/" + names[1]);
        } else if ("UNREAL".equals(engine) && gameDir != null) {
            String exePath = exe.getPath(), base = gameDir.getPath() + "/";
            String relative = exePath.startsWith(base) ? exePath.substring(base.length()) : exe.getName();
            rels.add("AppData/Local/" + UeIniWriter.projectName(gameDir, relative) + "/Saved/SaveGames");
        }
        for (String rel : discovered) {
            boolean covered = false;
            for (String kept : rels)
                if (rel.equals(kept) || rel.startsWith(kept + "/") || kept.startsWith(rel + "/")) covered = true;
            if (!covered) rels.add(rel);
        }
        return rels;
    }

    /** {company, product} of a Unity game from its {@code <exe>_Data/app.info}, or null. */
    static String[] unityNames(File exe) {
        try {
            File appInfo = AgvnUnityQuality.findAppInfo(exe);
            return appInfo != null ? AgvnUnityQuality.companyProduct(appInfo) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
