/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

/**
 * The line su-kien.txt gets for the emulator that runs a game's x86 code: with an arm64ec Wine, the one for 32-bit
 * programs (WoW64's HODLL). moi-truong.txt is written before the launcher sets HODLL, and logcat cuts the
 * environment's line before it, so no log said which ran The Agnietta (32-bit, its JavaScript engine mozjs-33 makes
 * x86 code as it runs), which closed right after its scripts started (19/10/2026). Pure Java.
 */
public final class AgvnEmulatorNote {
    private AgvnEmulatorNote() {}

    /** {@code emulator} is the game's "Giả lập 32-bit" ("FEXCore" or "Box64", as shortcuts keep it). */
    public static String line(boolean arm64ec, String emulator, String fexVersion, String box64Version) {
        if (!arm64ec) return "Giả lập x86: Box64 " + box64Version;
        return "FEXCore".equalsIgnoreCase(emulator) ? "Giả lập 32-bit: FEXCore " + fexVersion + " (libwow64fex.dll)"
                : "Giả lập 32-bit: WOWBox64 " + box64Version + " (wowbox64.dll)";
    }
}
