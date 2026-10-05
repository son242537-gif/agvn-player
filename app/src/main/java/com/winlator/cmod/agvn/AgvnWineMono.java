/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.TarCompressorUtils;
import com.winlator.cmod.core.WineInfo;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Wine Mono, the .NET runtime Wine runs .NET (C#) programs with; without it Wine closes them at once ("Wine Mono is not
 * installed"). The app bundles Wine Mono's own release archive (44 MB) in the version the bundled Proton's mscoree.dll
 * asks for, and unpacks it (235 MB) where Proton's own build puts it, share/wine/mono/wine-mono-VERSION in the Wine
 * folder (mscoree.dll finds it through WINEDATADIR): once per Wine, and only for a game whose exe is a .NET program
 * ({@link #isDotNet}) or whose player chose the doctor's fix ({@link #EXTRA}, for a launcher that starts one).
 */
public final class AgvnWineMono {
    private static final String TAG = "AGVN";
    static final String VERSION = "9.3.1";
    static final String ASSET = "wine-mono-" + VERSION + "-x86.tar.xz";
    /** Bytes of the archive's files, for the progress; unpacking needs that much free space, and the margin. */
    static final long UNPACKED_BYTES = 234_887_961L, MARGIN_BYTES = 64L << 20;
    /** Shortcut extra "1": the player chose the fix for "Wine Mono is not installed", so the next start unpacks it. */
    static final String EXTRA = "agvnWineMono";
    /** IMAGE_DIRECTORY_ENTRY_COM_DESCRIPTOR: the CLR header, which only a .NET program has. */
    private static final int CLR_DIRECTORY = 14;
    private static final String[] MSCOREE = {"aarch64-windows", "x86_64-windows", "i386-windows"};
    /** What the last start did about Wine Mono, for its session log ({@link #takeNote}); null when nothing. */
    private static volatile String note;

    private AgvnWineMono() {}

    /**
     * Before Wine starts: unpacks Wine Mono for a game that needs it and lacks it, telling {@code status} how far it is
     * (null when done). Runs off the main thread and never throws: the game starts either way.
     */
    public static void prepare(Activity a, Shortcut s, String winePath, String exePath, Consumer<String> status) {
        note = null;
        if (s == null || winePath == null || winePath.isEmpty()) return;
        try {
            String unix = AgvnExeRedirect.toUnixPath(exePath, s.container);
            if (!"1".equals(s.getExtra(EXTRA)) && (unix == null || !isDotNet(new File(unix)))) return;
            File wine = new File(winePath), dir = dir(wine);
            note = isInstalled(dir) ? "Game cần .NET: đã có Wine Mono " + VERSION : install(a, wine, dir, status);
            Log.i(TAG, "Wine Mono: " + note + " (" + dir + ")");
        } catch (RuntimeException e) {
            Log.w(TAG, "Wine Mono not prepared", e);
        }
    }

    /** Unpacks Wine Mono into {@code dir} when the Wine wants this version and there is room; returns its log note. */
    private static String install(Activity a, File wine, File dir, Consumer<String> status) {
        String wanted = wantedVersion(wine);
        if (!VERSION.equals(wanted)) {
            String other = wanted.isEmpty() ? "khác" : wanted;
            return "Game cần .NET: bản Wine này cần Wine Mono " + other + ", app chỉ có " + VERSION;
        }
        long freeMb = wine.getUsableSpace() >> 20, neededMb = (UNPACKED_BYTES + MARGIN_BYTES) >> 20;
        if (freeMb < neededMb) {
            toast(a, a.getString(R.string.agvn_wine_mono_no_space, neededMb, freeMb));
            return "Không cài được Wine Mono " + VERSION + ": máy còn trống " + freeMb + " MB, cần " + neededMb + " MB";
        }
        long start = System.currentTimeMillis();
        boolean ok = unpack(a, dir, p -> status.accept(a.getString(R.string.agvn_wine_mono_installing, p)));
        status.accept(null);
        long seconds = (System.currentTimeMillis() - start) / 1000;
        if (!ok) toast(a, a.getString(R.string.agvn_wine_mono_failed));
        return (ok ? "Đã cài" : "Không cài được") + " Wine Mono " + VERSION + " cho game cần .NET (" + seconds + " s)";
    }

    /**
     * True when the doctor can offer to install it for {@code s}: the game's Wine asks for the version the app has, and
     * it is not there yet.
     */
    static boolean offered(Context ctx, Shortcut s) {
        try {
            File wine = wineDir(ctx, s);
            return wine != null && !isInstalled(dir(wine)) && VERSION.equals(wantedVersion(wine));
        } catch (RuntimeException e) {
            Log.w(TAG, "Wine Mono: no Wine folder for " + s.name, e);
            return false;
        }
    }

    /** What the game about to start got, once, for its session log; null when it is not a .NET game. */
    static String takeNote() {
        String n = note;
        note = null;
        return n;
    }

    /** Where Wine Mono goes for the Wine in {@code wine}: its data folder, as Proton's own build does. */
    static File dir(File wine) {
        return new File(wine, "share/wine/mono/wine-mono-" + VERSION);
    }

    /** True when {@code dir} holds Wine Mono's runtime for 32-bit and for 64-bit programs. */
    static boolean isInstalled(File dir) {
        File bin = new File(dir, "bin");
        return new File(bin, "libmono-2.0-x86.dll").isFile() && new File(bin, "libmono-2.0-x86_64.dll").isFile();
    }

    /** True when {@code exe} is a .NET program: a PE file with a CLR header. */
    static boolean isDotNet(File exe) {
        try (RandomAccessFile raf = new RandomAccessFile(exe, "r")) {
            byte[] dos = AgvnPeCheck.read(raf, 0, 64);
            if (dos == null || dos[0] != 'M' || dos[1] != 'Z') return false;
            long pe = AgvnPeCheck.u32(dos, 0x3c);
            byte[] nt = AgvnPeCheck.read(raf, pe, 26);
            if (nt == null || nt[0] != 'P' || nt[1] != 'E' || nt[2] != 0 || nt[3] != 0) return false;
            int optionalSize = AgvnPeCheck.u16(nt, 20), magic = AgvnPeCheck.u16(nt, 24);
            int dirs = magic == 0x10b ? 96 : magic == 0x20b ? 112 : -1; // data directories, in the optional header
            int entry = dirs + CLR_DIRECTORY * 8;
            if (dirs < 0 || entry + 8 > optionalSize) return false;
            byte[] optional = AgvnPeCheck.read(raf, pe + 24, entry + 8);
            return optional != null && AgvnPeCheck.u32(optional, dirs - 4) > CLR_DIRECTORY
                    && AgvnPeCheck.u32(optional, entry) != 0 && AgvnPeCheck.u32(optional, entry + 4) != 0;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** The Wine Mono version the mscoree.dll of the Wine in {@code wine} looks for, e.g. "9.3.1"; "" when unknown. */
    static String wantedVersion(File wine) {
        for (String arch : MSCOREE) {
            File dll = new File(wine, "lib/wine/" + arch + "/mscoree.dll");
            try {
                String version = dll.isFile() ? versionIn(Files.readAllBytes(dll.toPath())) : "";
                if (!version.isEmpty()) return version;
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "cannot read " + dll, e);
            }
        }
        return "";
    }

    /** The digits and dots after "\wine-mono-" in UTF-16 text (mscoree's folder name for Wine Mono); "" when none. */
    static String versionIn(byte[] data) {
        byte[] key = "\\wine-mono-".getBytes(StandardCharsets.UTF_16LE);
        search:
        for (int i = 0; i + key.length <= data.length; i++) {
            for (int k = 0; k < key.length; k++) if (data[i + k] != key[k]) continue search;
            StringBuilder version = new StringBuilder();
            for (int j = i + key.length; j + 1 < data.length && data[j + 1] == 0; j += 2) {
                char c = (char) data[j];
                if (c != '.' && (c < '0' || c > '9')) break;
                version.append(c);
            }
            if (version.length() > 0) return version.toString();
        }
        return "";
    }

    /** Unpacks the archive beside {@code dir} first, so a cut-short unpack (storage full) never looks installed. */
    private static boolean unpack(Context ctx, File dir, IntConsumer percent) {
        File part = new File(dir.getParentFile(), "." + dir.getName() + ".part");
        FileUtils.delete(part);
        if (!part.mkdirs()) return false;
        long[] done = {0};
        int[] shown = {-1};
        boolean ok = TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, ctx, ASSET, part, (file, size) -> {
            done[0] += Math.max(size, 0);
            int p = (int) Math.min(99, done[0] * 100 / UNPACKED_BYTES);
            if (p != shown[0]) percent.accept(shown[0] = p);
            return file;
        });
        File top = new File(part, dir.getName()); // the archive's one folder, wine-mono-VERSION
        if (ok && isInstalled(top)) FileUtils.delete(dir); // what a half copy may have left
        ok = ok && isInstalled(top) && top.renameTo(dir);
        FileUtils.delete(part);
        return ok;
    }

    /** The folder of the Wine the game's container runs, or null when it is not known. */
    private static File wineDir(Context ctx, Shortcut s) {
        String version = s.container.getWineVersion();
        ContentsManager contents = new ContentsManager(ctx);
        if (!WineInfo.isMainWineVersion(version)) contents.syncContents();
        String path = WineInfo.fromIdentifier(ctx, contents, version).path;
        return path == null || path.isEmpty() ? null : new File(path);
    }

    private static void toast(Activity a, String text) {
        a.runOnUiThread(() -> Toast.makeText(a, text, Toast.LENGTH_LONG).show());
    }
}
