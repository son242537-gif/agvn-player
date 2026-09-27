/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Writes uncaught Java exceptions to filesDir/agvn/last-crash.txt, then hands them to the previous handler. */
public final class CrashRecorder implements Thread.UncaughtExceptionHandler {
    private final Context appContext;
    private final Thread.UncaughtExceptionHandler previous;

    private CrashRecorder(Context appContext, Thread.UncaughtExceptionHandler previous) {
        this.appContext = appContext;
        this.previous = previous;
    }

    public static void install(Context ctx) {
        Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
        if (current instanceof CrashRecorder) return;
        Thread.setDefaultUncaughtExceptionHandler(new CrashRecorder(ctx.getApplicationContext(), current));
    }

    public static File getCrashFile(Context ctx) {
        return new File(new File(ctx.getFilesDir(), "agvn"), "last-crash.txt");
    }

    public static boolean hasPendingCrash(Context ctx) {
        return getCrashFile(ctx).isFile();
    }

    public static void clearPendingCrash(Context ctx) {
        getCrashFile(ctx).delete();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable error) {
        try {
            File file = getCrashFile(appContext);
            file.getParentFile().mkdirs();
            try (PrintWriter out = new PrintWriter(new FileWriter(file, false))) {
                out.println("time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date()));
                out.println("app: " + versionName() + " (" + appContext.getPackageName() + ")");
                out.println("device: " + Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE);
                out.println("thread: " + thread.getName());
                out.println();
                error.printStackTrace(out);
            }
        } catch (Throwable ignored) {
            // never let the recorder hide the original crash
        }
        if (previous != null) {
            previous.uncaughtException(thread, error);
        } else {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        }
    }

    private String versionName() {
        try {
            PackageInfo info = appContext.getPackageManager().getPackageInfo(appContext.getPackageName(), 0);
            return info.versionName + " / " + info.versionCode;
        } catch (Exception e) {
            return "?";
        }
    }
}
