/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A 32-bit game whose emulator would not run the code of one of its own DLLs. Wine's report says the game jumped to
 * an address its backtrace places inside a loaded module, at that module's code: "page fault on execute access to
 * 0x79811e80 in wow64 32-bit code", then "=>0 0x79811e80 in msvcr100 (+0x1e80)", where winedbg reads "push %ebp". A
 * real CPU runs such code. FEX (libwow64fex.dll) raises the fault itself when it holds the page for not executable
 * (FEXCore's Decoder::CheckRangeExecutable, the guest pages it was told are executable). The Agnietta (Pixel Game
 * Maker MV, 32-bit) stopped so on a Redmi Note 9 Pro, 80 s in, as its scripts first used SQLite: sqlite3.dll called
 * the msvcr100.dll beside the exe (21/10/2026). The way round: Wine's own copy of a C runtime DLL
 * ({@link AgvnDllProxy}), or WOWBox64 for the 32-bit code ("emulator-wowbox64"). Pure Java but {@link #fix}.
 */
final class AgvnEmulatorNoExec {
    static final String FIX = "emulator-wowbox64";
    /** The game's "Giả lập 32-bit" for WOWBox64 under an arm64ec Wine, as shortcuts keep it (AgvnEmulatorNote). */
    static final String BOX64 = "Box64", FEX = "FEXCore";
    private static final Pattern EXECUTE = Pattern.compile(
            "Unhandled exception: page fault on execute access to (0x[0-9a-fA-F]+) in wow64 32-bit code");
    private static final Pattern FRAME =
            Pattern.compile("^=>0\\s+(0x[0-9a-fA-F]+)\\s+(?:\\S+\\s+)?in\\s+\\S+\\s+\\(\\+0x[0-9a-fA-F]+\\)");

    private AgvnEmulatorNoExec() {}

    /** Wine's report of a 32-bit game that executed an address inside a module, the first frame at that address. */
    static boolean moduleCode(List<String> lines) {
        String address = null;
        for (String raw : lines) {
            String line = raw.trim();
            Matcher m = EXECUTE.matcher(line);
            if (m.find()) {
                address = m.group(1).toLowerCase(Locale.ROOT);
            } else if (address != null && (m = FRAME.matcher(line)).find()) {
                if (m.group(1).toLowerCase(Locale.ROOT).equals(address)) return true;
                address = null; // the first frame is elsewhere: the game jumped out of any module
            }
        }
        return false;
    }

    /** WOWBox64 for a game whose 32-bit code FEX runs under an arm64ec Wine; else null. */
    static AgvnFixes.Fix fix(Context a, Shortcut s) {
        String emulator = s.getExtra("emulator", s.container.getEmulator());
        String wine = s.container.getWineVersion();
        boolean arm64ec = wine != null && wine.toLowerCase(Locale.ROOT).contains("arm64ec");
        return arm64ec && FEX.equalsIgnoreCase(emulator)
                ? new AgvnFixes.Fix(FIX, a.getString(R.string.agvn_fix_emulator_wowbox64), BOX64) : null;
    }
}
