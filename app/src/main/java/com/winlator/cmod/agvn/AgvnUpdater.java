/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Downloads and checks AGVN Player updates from this repository's GitHub releases. Nothing about the phone is sent:
 * the app only reads agvn-update.txt and the APK it names. The APK must match the size and SHA-256 in that file,
 * be AGVN Player, be newer, and be signed with the same key as the installed app before Android is asked to install it.
 */
public final class AgvnUpdater {
    static final String DIR = "updates";
    private static final int TIMEOUT_MS = 20_000;

    public interface Progress {
        void onProgress(long done, long total);
    }

    private AgvnUpdater() {}

    /** The newest release, or null when none is published yet. */
    public static AgvnUpdateInfo fetch() throws IOException {
        return AgvnUpdateInfo.parse(getText(AgvnUpdateInfo.LATEST_URL));
    }

    public static long installedCode(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    public static String installedName(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    public static File apkFile(Context context, AgvnUpdateInfo info) {
        return new File(new File(context.getCacheDir(), DIR), "AGVN-Player-" + info.versionCode + ".apk");
    }

    /** Frees the space of downloads that are installed already (or not ours); a newer partial one stays for resuming. */
    public static void cleanupInstalled(Context context) {
        long installed = installedCode(context);
        File[] files = new File(context.getCacheDir(), DIR).listFiles();
        if (files == null) return;
        for (File f : files) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("AGVN-Player-(\\d{1,9})\\.apk").matcher(f.getName());
            if (!m.matches() || Long.parseLong(m.group(1)) <= installed) f.delete();
        }
    }

    /** Before a download: other versions' files go, so only one APK takes space. */
    static void cleanupExcept(Context context, File keep) {
        File[] files = new File(context.getCacheDir(), DIR).listFiles();
        if (files == null) return;
        for (File f : files) if (!f.equals(keep)) f.delete();
    }

    /** Downloads (resuming a cut-off download) and checks the APK. Throws with a message for the player. */
    public static void download(Context context, AgvnUpdateInfo info, File apk, Progress progress, AtomicBoolean cancel) throws IOException {
        apk.getParentFile().mkdirs();
        if (apk.length() > info.size) apk.delete();
        if (apk.length() < info.size) {
            HttpURLConnection c = open(info.apkUrl);
            long have = apk.length();
            if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
            int status = c.getResponseCode();
            if (status == 416) apk.delete(); // the part we have no longer fits the file: start over next time
            if (status != HttpURLConnection.HTTP_OK && status != HttpURLConnection.HTTP_PARTIAL)
                throw new IOException("máy chủ trả lỗi " + status + ", hãy thử lại");
            boolean append = status == HttpURLConnection.HTTP_PARTIAL;
            if (!append) have = 0;
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(apk, append)) {
                byte[] buf = new byte[256 * 1024];
                for (int n; (n = in.read(buf)) != -1; ) {
                    if (cancel.get()) throw new IOException("đã hủy");
                    out.write(buf, 0, n);
                    have += n;
                    progress.onProgress(have, info.size);
                }
            } finally {
                c.disconnect();
            }
        }
        if (apk.length() != info.size || !info.sha256.equals(sha256(apk))) {
            apk.delete();
            throw new IOException("tệp tải về bị hỏng, hãy thử lại");
        }
        checkPackage(context, info, apk);
    }

    private static void checkPackage(Context context, AgvnUpdateInfo info, File apk) throws IOException {
        PackageManager pm = context.getPackageManager();
        PackageInfo update = pm.getPackageArchiveInfo(apk.getPath(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (update == null || !context.getPackageName().equals(update.packageName) || update.getLongVersionCode() != info.versionCode) {
            apk.delete();
            throw new IOException("tệp tải về không phải AGVN Player bản " + info.versionName);
        }
        try {
            PackageInfo self = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            Signature[] mine = self.signingInfo != null ? self.signingInfo.getApkContentsSigners() : null;
            Signature[] theirs = update.signingInfo != null ? update.signingInfo.getApkContentsSigners() : null;
            // Android refuses an update with another key anyway; saying so here is clearer than its "App not installed"
            if (mine != null && theirs != null && !Arrays.equals(mine, theirs))
                throw new IOException("bản mới không cùng chữ ký với app đang cài, nên không cập nhật đè được");
        } catch (PackageManager.NameNotFoundException ignored) {}
    }

    /** False until the player allows AGVN Player to install apps ("Cài ứng dụng không rõ nguồn gốc"). */
    public static boolean canInstall(Context context) {
        return context.getPackageManager().canRequestPackageInstalls();
    }

    public static Intent allowInstallIntent(Context context) {
        return new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.getPackageName()));
    }

    /** Opens Android's installer on the checked APK; Android asks the player to confirm the update. */
    public static Intent installIntent(Context context, File apk) {
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".tileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    static String sha256(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[256 * 1024];
            for (int n; (n = in.read(buf)) != -1; ) digest.update(buf, 0, n);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /** The text at {@code url}, or null when nothing is published there (404). */
    private static String getText(String url) throws IOException {
        HttpURLConnection c = open(url);
        try {
            int status = c.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) return null;
            if (status != HttpURLConnection.HTTP_OK) throw new IOException("máy chủ trả lỗi " + status);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) != -1 && out.size() < 64 * 1024; ) out.write(buf, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setInstanceFollowRedirects(true); // GitHub sends release files from another https host
        c.setUseCaches(false);
        return c;
    }
}
