/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * What a game's exe says of itself, for a game whose engine AGVN does not know ({@link AgvnGameFacts}): 32 or 64 bit,
 * the Windows DLLs it loads (Direct3D, OpenGL, DirectShow or Media Foundation for movies, DirectSound, XInput...), a
 * packer's sections (Enigma Virtual Box keeps a whole game in its exe; SteamStub's .bind wants Steam), and data
 * appended after its last section (a LÖVE or Python game, a self-extracting archive, an installer).
 * Isekai NTR Inn (07/10/2026) was one exe, with no DLL beside it and no engine log. Pure Java (JVM-testable).
 */
final class AgvnPeFacts {
    static final int MAX_DLLS = 40;
    static final long BIG_OVERLAY = 1 << 20;
    private static final String[][] PACKERS = {{".enigma1", "Enigma Virtual Box"}, {"UPX0", "UPX"},
            {".vmp0", "VMProtect"}, {".themida", "Themida"}, {".winlice", "WinLicense"}, {".bind", "SteamStub"},
            {".aspack", "ASPack"}, {".MPRESS1", "MPRESS"}};

    private AgvnPeFacts() {}

    /** One line for the session events, or null when {@code exe} is not a PE file or cannot be read. */
    static String describe(File exe) {
        try (RandomAccessFile f = new RandomAccessFile(exe, "r")) {
            byte[] dos = read(f, 0, 64);
            if (dos == null || dos[0] != 'M' || dos[1] != 'Z') return null;
            long pe = u32(dos, 0x3c);
            byte[] nt = read(f, pe, 24);
            if (nt == null || nt[0] != 'P' || nt[1] != 'E' || nt[2] != 0 || nt[3] != 0) return null;
            int machine = u16(nt, 4), count = u16(nt, 6), optSize = u16(nt, 20);
            byte[] opt = read(f, pe + 24, optSize);
            byte[] table = read(f, pe + 24 + optSize, count * 40);
            if (opt == null || table == null || optSize < 96) return null;
            boolean plus = u16(opt, 0) == 0x20b;
            int dirs = plus ? 112 : 96;
            long base = plus ? u32(opt, 24) | u32(opt, 28) << 32 : u32(opt, 28);
            int entries = (int) Math.min(16, Math.max(0, (optSize - dirs) / 8));
            StringBuilder line = new StringBuilder("File exe: ").append(bits(machine));
            List<String> dlls = imports(f, table, entries > 1 ? u32(opt, dirs + 8) : 0, 20, 12, 0);
            if (entries > 13) dlls.addAll(imports(f, table, u32(opt, dirs + 104), 32, 4, base));
            List<String> names = new ArrayList<>(new TreeSet<>(dlls));
            if (!names.isEmpty()) {
                int more = names.size() - MAX_DLLS;
                line.append(" · nạp: ").append(String.join(", ", more > 0 ? names.subList(0, MAX_DLLS) : names));
                if (more > 0) line.append(" (+").append(more).append(')');
            }
            long end = 0;
            for (int i = 0; i < count; i++) {
                String name = new String(table, i * 40, 8, StandardCharsets.US_ASCII).replace("\0", "");
                for (String[] p : PACKERS) if (name.equals(p[0])) line.append(" · đóng gói: ").append(p[1]);
                end = Math.max(end, u32(table, i * 40 + 20) + u32(table, i * 40 + 16));
            }
            if (entries > 4 && u32(opt, dirs + 32) == end) end += u32(opt, dirs + 36); // its signature, if any
            long overlay = f.length() - end;
            if (end > 0 && overlay >= BIG_OVERLAY) line.append(" · ").append(AgvnGameFacts.size(overlay))
                    .append(" nối sau exe (").append(appended(f, end)).append(')');
            return line.toString();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String bits(int machine) {
        return machine == 0x8664 ? "64-bit" : machine == 0x14c ? "32-bit" : String.format(Locale.ROOT, "máy 0x%x", machine);
    }

    /**
     * The DLL names of an import table at {@code rva}: descriptors of {@code size} bytes, the name's RVA at {@code at};
     * {@code base} > 0 for delay-load descriptors, whose old form gives addresses instead (attribute bit 0 unset).
     */
    private static List<String> imports(RandomAccessFile f, byte[] table, long rva, int size, int at, long base)
            throws IOException {
        List<String> names = new ArrayList<>();
        long offset = offset(table, rva);
        for (int i = 0; offset > 0 && i < 256; i++) {
            byte[] d = read(f, offset + (long) i * size, size);
            if (d == null || allZero(d)) break;
            long name = u32(d, at);
            if (base > 0 && (u32(d, 0) & 1) == 0) name -= base;
            long nameAt = offset(table, name);
            byte[] text = nameAt < 0 ? null : read(f, nameAt, (int) Math.min(64, f.length() - nameAt));
            if (text == null) continue;
            int len = 0;
            while (len < text.length && text[len] >= 0x20 && text[len] < 0x7f) len++;
            String dll = new String(text, 0, len, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
            if (!dll.isEmpty()) names.add(dll.endsWith(".dll") ? dll.substring(0, dll.length() - 4) : dll);
        }
        return names;
    }

    /** The file offset of {@code rva}, through the section that holds it; -1 when none does. */
    private static long offset(byte[] table, long rva) {
        if (rva <= 0) return -1;
        for (int i = 0; i + 40 <= table.length; i += 40) {
            long va = u32(table, i + 12), span = Math.max(u32(table, i + 8), u32(table, i + 16));
            if (rva >= va && rva < va + span) return rva - va + u32(table, i + 20);
        }
        return -1;
    }

    /** What the data appended at {@code at} looks like. */
    private static String appended(RandomAccessFile f, long at) throws IOException {
        long tailAt = Math.max(at, f.length() - 4096);
        byte[] head = read(f, at, 32), tail = read(f, tailAt, (int) (f.length() - tailAt));
        String start = head != null ? new String(head, StandardCharsets.ISO_8859_1) : "";
        String end = tail != null ? new String(tail, StandardCharsets.ISO_8859_1) : "";
        if (end.contains("MEI\u000c\u000b\n\u000b\u000e")) return "PyInstaller, game Python";
        if (end.endsWith("GDPC")) return "gói Godot";
        if (start.startsWith("PK\u0003\u0004")) return "file zip: LÖVE, NW.js hoặc file nén tự giải nén";
        if (start.startsWith("7z¼¯'\u001c")) return "7-Zip tự giải nén";
        if (start.startsWith("Rar!")) return "RAR tự giải nén";
        if (start.contains("NullsoftInst")) return "bộ cài NSIS, không phải game";
        if (start.startsWith("MZ")) return "một file exe khác";
        return "chưa rõ loại";
    }

    private static boolean allZero(byte[] b) {
        for (byte x : b) if (x != 0) return false;
        return true;
    }

    private static byte[] read(RandomAccessFile f, long at, int length) throws IOException {
        if (at < 0 || length < 0 || at + length > f.length()) return null;
        byte[] b = new byte[length];
        f.seek(at);
        f.readFully(b);
        return b;
    }

    private static int u16(byte[] b, int at) {
        return (b[at] & 0xff) | (b[at + 1] & 0xff) << 8;
    }

    private static long u32(byte[] b, int at) {
        return (b[at] & 0xffL) | (b[at + 1] & 0xffL) << 8 | (b[at + 2] & 0xffL) << 16 | (b[at + 3] & 0xffL) << 24;
    }
}
