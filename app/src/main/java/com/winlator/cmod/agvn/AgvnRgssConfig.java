/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The mkxp.json AgvnRgssActivity writes before each start of an RPG Maker XP/VX/VX Ace game. mkxp-z reads it from
 * SRCDIR, then switches into gameFolder and reads the game's .ini there. Pure Java (JVM-testable).
 */
final class AgvnRgssConfig {
    /** Where the player can copy an RTP pack: AGVN-Player/RTP/<name>, e.g. AGVN-Player/RTP/RPGVXAce. */
    static final String RTP_FOLDER = "RTP";

    final File gameDir;
    final AgvnRgssGame.Ini ini;
    final int rgss;
    /** RTP folders found on the phone, in the order the game lists them. */
    final List<File> rtpDirs = new ArrayList<>();
    /** RTP packs the game asks for that the phone does not have. */
    final List<String> missingRtp = new ArrayList<>();

    AgvnRgssConfig(File gameDir, File agvnDir, File driveC) {
        this.gameDir = gameDir;
        ini = AgvnRgssGame.readIni(gameDir);
        rgss = AgvnRgssGame.rgssVersion(gameDir);
        File rtpRoot = new File(agvnDir, RTP_FOLDER);
        for (String name : AgvnRgssGame.rtpNames(ini)) {
            File dir = AgvnRgssGame.findRtp(name, rgss, rtpRoot, driveC);
            if (dir != null) rtpDirs.add(dir);
            else missingRtp.add(name);
        }
    }

    /**
     * mkxp.json: full screen, picture scaled with its aspect ratio kept, the game's own frame rate, MIDI through the
     * app's sound font, and mkxp-z's Win32API stand-ins (win32_wrap.rb) for scripts written for Windows.
     */
    String json(File soundFont, List<File> preloadScripts) {
        StringBuilder sb = new StringBuilder("{\n");
        field(sb, "gameFolder", quote(gameDir.getAbsolutePath()));
        if (ini != null) field(sb, "execName", quote(ini.execName()));
        field(sb, "rgssVersion", String.valueOf(rgss));
        field(sb, "fullscreen", "true");
        field(sb, "fixedAspectRatio", "true");
        field(sb, "smoothScaling", "1");
        field(sb, "vsync", "true");
        field(sb, "enableSettings", "false");
        field(sb, "midiSoundFont", soundFont != null ? quote(soundFont.getAbsolutePath()) : "\"\"");
        field(sb, "RTP", array(rtpDirs));
        sb.append("  \"preloadScript\": ").append(array(preloadScripts)).append("\n}\n");
        return sb.toString();
    }

    private static void field(StringBuilder sb, String key, String value) {
        sb.append("  ").append(quote(key)).append(": ").append(value).append(",\n");
    }

    private static String array(List<File> files) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < files.size(); i++) sb.append(i > 0 ? ", " : "").append(quote(files.get(i).getAbsolutePath()));
        return sb.append(']').toString();
    }

    /** A JSON string; paths keep their Vietnamese or Japanese letters, the file is UTF-8. */
    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.append('"').toString();
    }
}
