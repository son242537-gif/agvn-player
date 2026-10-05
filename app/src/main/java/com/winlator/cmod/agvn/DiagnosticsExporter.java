/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;
import com.winlator.cmod.SettingsFragment;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * One-tap support bundle: /sdcard/Download/AGVN-nhat-ky-yyyyMMdd-HHmm.zip with device info, logcat,
 * the last crash and the two newest app log files. Never includes API keys or full preference dumps.
 */
public final class DiagnosticsExporter {
    private static final long MAX_FILE_BYTES = 3L * 1024 * 1024;
    private static final String[] PREF_ALLOWLIST = {
            "box64_preset", "fexcore_preset", "use_dri3", "cursor_speed", "enable_wine_debug",
            "enable_box64_logs", "enable_winlator_logs", "high_refresh_rate_mode", "use_xr"};

    private DiagnosticsExporter() {}

    /** Runs off the UI thread and reports the result with a toast. */
    public static void export(Context context) {
        export(context, null);
    }

    /** {@code afterSuccess} runs on the worker thread once the ZIP is written. */
    public static void export(Context context, Runnable afterSuccess) {
        Context ctx = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            String message;
            try {
                File zip = writeZip(ctx);
                message = ctx.getString(R.string.agvn_export_done, zip.getName());
                if (afterSuccess != null) afterSuccess.run();
            } catch (Exception e) {
                message = ctx.getString(R.string.agvn_export_failed, String.valueOf(e.getMessage()));
            }
            String text = message;
            main.post(() -> Toast.makeText(ctx, text, Toast.LENGTH_LONG).show());
        });
    }

    static File writeZip(Context ctx) throws IOException {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        dir.mkdirs();
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
        File zip = new File(dir, "AGVN-nhat-ky-" + stamp + ".zip");
        try (ZipOutputStream out = new ZipOutputStream(new java.io.FileOutputStream(zip))) {
            putText(out, "thiet-bi.txt", deviceInfo(ctx));
            putText(out, "logcat.txt", logcat(ctx));
            File crash = CrashRecorder.getCrashFile(ctx);
            if (crash.isFile()) putFile(out, "last-crash.txt", crash);
            File[] logs = new File(SettingsFragment.DEFAULT_WINLATOR_PATH, "logs").listFiles(File::isFile);
            if (logs != null) {
                Arrays.sort(logs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                for (int i = 0; i < Math.min(2, logs.length); i++) putFile(out, "logs/" + logs[i].getName(), logs[i]);
            }
        }
        return zip;
    }

    static String deviceInfo(Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("Thiết bị: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("Chip: ").append(Build.HARDWARE);
        if (Build.VERSION.SDK_INT >= 31) sb.append(" / ").append(Build.SOC_MANUFACTURER).append(' ').append(Build.SOC_MODEL);
        sb.append('\n');
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("GPU: ").append(DriverSafety.getGpuRenderer(ctx)).append('\n');
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        sb.append("RAM: tổng ").append(mi.totalMem >> 20).append(" MB, trống ").append(mi.availMem >> 20).append(" MB\n");
        try {
            android.content.pm.PackageInfo info = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            sb.append("Ứng dụng: ").append(info.versionName).append(" / ").append(info.versionCode).append('\n');
        } catch (Exception ignored) {}
        for (String line : AgvnDiagnosticsExtras.lines(ctx)) sb.append(line).append('\n');
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        Map<String, Object> picked = new TreeMap<>();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            String key = e.getKey();
            if (key.startsWith("agvn") || Arrays.asList(PREF_ALLOWLIST).contains(key)) picked.put(key, e.getValue());
        }
        sb.append("\nCài đặt (rút gọn):\n");
        for (Map.Entry<String, Object> e : picked.entrySet()) sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        return sb.toString();
    }

    private static String logcat(Context ctx) {
        File tmp = new File(ctx.getCacheDir(), "agvn-logcat.txt");
        try {
            Process p = new ProcessBuilder("logcat", "-d", "-v", "time", "-t", "20000")
                    .redirectErrorStream(true).redirectOutput(tmp).start();
            if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroy();
            return new String(readTail(tmp), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "logcat unavailable: " + e;
        } finally {
            tmp.delete();
        }
    }

    private static byte[] readTail(File file) throws IOException {
        long len = file.length();
        long start = Math.max(0, len - MAX_FILE_BYTES);
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] data = new byte[(int) (len - start)];
            raf.seek(start);
            raf.readFully(data);
            return data;
        }
    }

    private static void putText(ZipOutputStream out, String name, String text) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private static void putFile(ZipOutputStream out, String name, File file) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(readTail(file));
        out.closeEntry();
    }
}
