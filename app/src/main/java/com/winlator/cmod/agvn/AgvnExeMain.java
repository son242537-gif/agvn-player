/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.util.Map;

/**
 * A game added with a small .NET launcher as its exe: "Choi Game.exe" (PE32 AnyCPU, 8 KB, no icon) beside a GameMaker
 * game's own exe (POCO, 24/10/2026). Wine Mono does not load into a 64-bit .NET program on the phone ("Could not load
 * Mono into this process", c000007b), so the launcher closed after 4 s with nothing shown. Games are added with the
 * engine's own exe now ({@link GameExeResolver}); one added before is offered "exe-main", which starts the exe the
 * folder would be added with today instead ({@link #EXTRA}: its name, beside the launcher). Only the command of the
 * launch changes, as with {@link AgvnExe64}: the shortcut keeps its path.
 * <p>
 * A game added with an exe that is never a game ({@link GameExeResolver#neverAGame}: UnityCrashHandler64.exe, a
 * redistributable's installer, an uninstaller) starts the folder's game at once, unasked: Succubus Kiss (Unity, Mali-G610,
 * 26/10/2026) was added by picking UnityCrashHandler64.exe in "Chọn thư mục khác", which closed after 4 s with nothing
 * shown at each of five starts. With no such game beside it, "Tự sửa lỗi" says so ({@link #helperName}).
 */
public final class AgvnExeMain {
    static final String EXTRA = "agvnExeMain", FIX = "exe-main";
    private static final String TAG = "AGVN";
    private static volatile String note;

    private AgvnExeMain() {}

    /**
     * The exe beside {@code exe} that its folder would be added with today, when {@code exe} is a .NET program, the
     * folder's engine is a known one and that exe is no .NET program; else null. {@code engine}: the shortcut's.
     */
    static File main(File exe, String engine) {
        File dir = exe.getParentFile();
        if (dir == null || !exe.isFile() || !AgvnWineMono.isDotNet(exe)) return null;
        GameExeResolver.Engine known = engine(engine, dir);
        String name = known == GameExeResolver.Engine.UNKNOWN ? null : GameExeResolver.resolveExe(dir, known);
        if (name == null || name.contains("/") || name.equalsIgnoreCase(exe.getName())) return null;
        File main = new File(dir, name);
        return main.isFile() && !AgvnWineMono.isDotNet(main) ? main : null;
    }

    private static GameExeResolver.Engine engine(String name, File dir) {
        for (GameExeResolver.Engine e : GameExeResolver.Engine.values()) if (e.name().equals(name)) return e;
        return GameExeResolver.detectEngine(dir);
    }

    /**
     * The game beside {@code exe} when {@code exe} is never a game: the exe its folder would be added with today
     * ({@code engine}: the shortcut's); else null.
     */
    static File gameFor(File exe, String engine) {
        File dir = exe.getParentFile();
        if (dir == null || !GameExeResolver.neverAGame(exe.getName())) return null;
        String name = GameExeResolver.resolveExe(dir, engine(engine, dir));
        if (name == null || name.contains("/")) return null;
        File game = new File(dir, name);
        return game.isFile() ? game : null;
    }

    /** The name of the exe {@code s} starts when it is never a game and has no game beside it to start; else null. */
    static String helperName(Shortcut s) {
        File exe = AgvnEngineLogs.exe(s);
        return GameExeResolver.neverAGame(exe.getName()) ? exe.getName() : null;
    }

    /** The exe {@code s} starts: {@code exe}, or the one "exe-main" chose beside it, or the game beside a helper. */
    static File launched(Shortcut s, File exe) {
        File chosen = chosen(s, exe);
        return chosen != null ? chosen : exe;
    }

    private static File chosen(Shortcut s, File exe) {
        String name = s.getExtra(EXTRA);
        File dir = exe.getParentFile();
        File chosen = name.isEmpty() || name.contains("/") || name.contains("\\") || dir == null ? null : new File(dir, name);
        if (chosen != null && chosen.isFile()) return chosen;
        return gameFor(exe, s.getExtra(AgvnGameImporter.EXTRA_ENGINE)); // a crash handler never is the game
    }

    /** "Chạy file game chính (&lt;Name&gt;.exe)" for a game started with a .NET launcher beside it; else null. */
    static AgvnFixes.Fix fix(Context a, Shortcut s) {
        String unix = s.getExtra(EXTRA).isEmpty() ? AgvnExeRedirect.toUnixPath(s.path, s.container) : null;
        File main = unix != null ? main(new File(unix), s.getExtra(AgvnGameImporter.EXTRA_ENGINE)) : null;
        return main != null ? new AgvnFixes.Fix(FIX, a.getString(R.string.agvn_fix_exe_main, main.getName()), main.getName()) : null;
    }

    /**
     * {@code launch} (the exe as the launch command has it, quoted or not, with its arguments) with the exe "exe-main"
     * chose; else {@code launch} itself. Never throws.
     */
    public static String apply(String launch, Shortcut s, Container container) {
        note = null;
        if (launch == null || s == null || container == null) return launch;
        try {
            return apply(launch, s, AgvnExeRedirect.driveMap(container));
        } catch (RuntimeException e) {
            Log.w(TAG, "no main exe for " + launch, e);
            return launch;
        }
    }

    static String apply(String launch, Shortcut s, Map<String, String> drives) {
        note = null;
        File[] picked = new File[2];
        String to = AgvnExe64.swap(launch, drives, exe -> {
            picked[0] = exe;
            return picked[1] = chosen(s, exe);
        });
        if (to == null) return launch;
        note = "Chạy file game chính: " + picked[1].getName() + " thay cho " + picked[0].getName()
                + (GameExeResolver.neverAGame(picked[0].getName()) ? " (chương trình phụ của game, không phải game)"
                : " (chương trình .NET, đã chọn trong Tự sửa lỗi)");
        Log.i(TAG, "main exe " + launch + " -> " + to);
        return to;
    }

    /** The line for the session's events, once; null when the game starts its own exe. */
    static String takeNote() {
        String n = note;
        note = null;
        return n;
    }
}
