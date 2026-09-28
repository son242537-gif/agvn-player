/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Automatic on-screen controls per game type. Six bundled layouts (assets/agvn/controls-&lt;kind&gt;.icp) own the
 * reserved controls profile ids 9000-9005. The kind comes from agvn-profile.json "controls" or from the engine:
 * visual novels get advance/skip/menu keys, RPG Maker/Wolf get a D-pad with Z/X/Shift/Esc, GameMaker/Godot a 2D pad,
 * everything else the PC keys. Pure Java (JVM-testable) except {@link #applyImport} and {@link #labelRes}.
 */
public final class AgvnLayouts {
    public static final String PC = "pc", VN = "vn", RPG = "rpg", TWO_D = "2d", ACTION = "action", MOUSE = "mouse";
    /** Index i uses profile id FIRST_ID + i and asset controls-&lt;kind&gt;.icp; never reorder. Mirrored in agvn_profile_lib.py. */
    public static final List<String> KINDS = Collections.unmodifiableList(Arrays.asList(PC, VN, RPG, TWO_D, ACTION, MOUSE));
    public static final int FIRST_ID = 9000;
    /** The old single "AGVN Bàn phím" profile: may still be on disk, no longer assigned, treated as auto-assigned. */
    public static final int LEGACY_ID = 900;
    public static final String EXTRA_PROFILE = "controlsProfile";
    /** Kind chosen at import (profile override or engine). */
    public static final String EXTRA_KIND = "agvnControls";
    /** "1" while controlsProfile was picked by AGVN, not by the player, so it may be picked again at launch. */
    public static final String EXTRA_AUTO = "agvnControlsAuto";
    /** Parent folders of the exe folder searched for an engine at launch (Unreal: Win64 -> Binaries -> Project -> root). */
    static final int PARENT_LEVELS = 3;
    /** Engine sub-folders holding the exe, which the launch-time search climbs out of (plus Ren'Py lib/py3-..., windows-...). */
    private static final List<String> BINARY_FOLDERS = Arrays.asList("win64", "win32", "binaries", "bin", "x64", "x86", "lib");

    private AgvnLayouts() {}

    public static boolean isKind(String kind) {
        return kind != null && KINDS.contains(kind);
    }

    public static String kindFor(GameExeResolver.Engine engine) {
        if (engine == null) return PC;
        switch (engine) {
            case RENPY: case KIRIKIRI: case TYRANO: case SIGLUS: case NSCRIPTER:
                return VN;
            case RPGMAKER: case RPGMAKER_MV: case WOLFRPG:
                return RPG;
            case GAMEMAKER: case GODOT:
                return TWO_D;
            default:
                return PC; // UNREAL, UNITY, UNKNOWN: genre cannot be read from the files
        }
    }

    /** The profile's "controls" when set and valid, else by engine. */
    public static String kindFor(AgvnProfile profile, GameExeResolver.Engine engine) {
        return profile != null && isKind(profile.controls) ? profile.controls : kindFor(engine);
    }

    /** Controls profile id of a kind (unknown kinds get the PC layout). */
    public static int idFor(String kind) {
        return FIRST_ID + Math.max(KINDS.indexOf(kind), 0);
    }

    /** Kind of a bundled layout id, or null for any other profile id. */
    public static String kindForId(int id) {
        int i = id - FIRST_ID;
        return i >= 0 && i < KINDS.size() ? KINDS.get(i) : null;
    }

    /** True for the profile ids AGVN assigns by itself: the six layouts and the legacy 900. */
    public static boolean isAgvnId(String id) {
        try {
            int value = Integer.parseInt(id.trim());
            return value == LEGACY_ID || kindForId(value) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static String assetFor(String kind) {
        return "agvn/controls-" + kind + ".icp";
    }

    /** Vietnamese name of a kind, for the import preview. */
    public static int labelRes(String kind) {
        switch (kind != null ? kind : PC) {
            case VN: return R.string.agvn_controls_vn;
            case RPG: return R.string.agvn_controls_rpg;
            case TWO_D: return R.string.agvn_controls_2d;
            case ACTION: return R.string.agvn_controls_action;
            case MOUSE: return R.string.agvn_controls_mouse;
            default: return R.string.agvn_controls_pc;
        }
    }

    /** Import: remembers the kind and assigns its layout as an AGVN choice (availability is re-checked at launch). */
    public static void applyImport(Shortcut shortcut, String kind) {
        String k = isKind(kind) ? kind : PC;
        shortcut.putExtra(EXTRA_KIND, k);
        shortcut.putExtra(EXTRA_PROFILE, String.valueOf(idFor(k)));
        shortcut.putExtra(EXTRA_AUTO, "1");
    }

    /**
     * Whether AGVN may (re)pick the layout at launch: nothing chosen yet, the legacy "900", or one of the AGVN layouts
     * that AGVN picked itself. "0" means the player turned the controls off; any other profile is the player's choice.
     */
    static boolean shouldAssign(String profileId, String auto) {
        String id = profileId != null ? profileId.trim() : "";
        if (id.isEmpty() || id.equals(String.valueOf(LEGACY_ID))) return true;
        if (id.equals("0")) return false;
        return "1".equals(auto) && isAgvnId(id);
    }

    /**
     * Kind for this launch: the kind stored at import; else the engine of the game folder saved at import, of the exe
     * folder or of up to {@link #PARENT_LEVELS} parents it sits in ({@link #detectAround}); else the engine stored at
     * import (older builds could store a wrong one, e.g. GODOT for Siglus, so files win over it).
     */
    static String launchKind(String storedKind, String storedEngine, String gameDir, String exeUnixPath) {
        if (isKind(storedKind)) return storedKind;
        GameExeResolver.Engine engine = GameExeResolver.Engine.UNKNOWN;
        if (gameDir != null && !gameDir.isEmpty()) engine = GameExeResolver.detectEngine(new File(gameDir));
        if (engine == GameExeResolver.Engine.UNKNOWN && exeUnixPath != null) engine = detectAround(new File(exeUnixPath).getParentFile());
        if (engine == GameExeResolver.Engine.UNKNOWN) engine = parseEngine(storedEngine);
        return kindFor(engine);
    }

    /**
     * Engine of {@code dir}, else of the game folder above it while {@code dir} is an engine's exe sub-folder (Unreal
     * Project/Binaries/Win64 up to a root with Engine/, Ren'Py lib/py3-windows-x86_64, bin, x64 ...), at most
     * {@link #PARENT_LEVELS} up; UNKNOWN when none is recognised. A plain game folder is never climbed out of: its parent
     * is often a shared folder (Download, the storage root) where a stray patch.xp3 or *.pck says nothing about this game.
     */
    static GameExeResolver.Engine detectAround(File dir) {
        String child = null;
        for (int level = 0; dir != null && level <= PARENT_LEVELS; level++) {
            GameExeResolver.Engine engine = GameExeResolver.detectEngine(dir);
            if (engine != GameExeResolver.Engine.UNKNOWN) return engine;
            String name = dir.getName().toLowerCase(Locale.ROOT);
            File parent = dir.getParentFile();
            boolean unrealProject = "binaries".equals(child) && parent != null && new File(parent, "Engine").isDirectory();
            if (!isBinaryFolder(name) && !unrealProject) break;
            child = name;
            dir = parent;
        }
        return GameExeResolver.Engine.UNKNOWN;
    }

    static boolean isBinaryFolder(String lowerName) {
        return BINARY_FOLDERS.contains(lowerName) || lowerName.startsWith("py2-") || lowerName.startsWith("py3-")
                || lowerName.startsWith("windows-");
    }

    private static GameExeResolver.Engine parseEngine(String name) {
        try {
            return name != null ? GameExeResolver.Engine.valueOf(name.trim()) : GameExeResolver.Engine.UNKNOWN;
        } catch (IllegalArgumentException e) {
            return GameExeResolver.Engine.UNKNOWN;
        }
    }
}
