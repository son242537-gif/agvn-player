/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.util.Log;

/** Why the app process ended, from Android's exit records, in words for the session summary. */
final class AgvnExitReason {
    private static final String TAG = "AGVN";

    private AgvnExitReason() {}

    /** How the app process ended after {@code startMs}, from Android's records (Android 11+). */
    static String after(Context context, long startMs) {
        Exit exit = exit(context, startMs);
        if (exit == null) return "App dừng giữa phiên, không rõ lý do";
        return describe(exit.reason) + " (reason=" + exit.reason + ", importance=" + exit.importance + " – "
                + importance(exit.importance) + ", " + exit.description + ")";
    }

    /** The end of the app process as Android recorded it. */
    static final class Exit {
        final int reason, importance;
        final String description;

        Exit(int reason, int importance, String description) {
            this.reason = reason;
            this.importance = importance;
            this.description = description;
        }
    }

    /** The first end of the app process after {@code startMs} (Android 11+), or null when unknown. */
    static Exit exit(Context context, long startMs) {
        if (Build.VERSION.SDK_INT < 30 || startMs <= 0) return null;
        try {
            ActivityManager am = context.getSystemService(ActivityManager.class);
            ApplicationExitInfo first = null; // the list is newest first; the session ended with the oldest one after it began
            for (ApplicationExitInfo info : am.getHistoricalProcessExitReasons(context.getPackageName(), 0, 16))
                if (info.getTimestamp() >= startMs) first = info;
            return first != null ? new Exit(first.getReason(), first.getImportance(), first.getDescription()) : null;
        } catch (RuntimeException e) {
            Log.w(TAG, "exit reasons not readable", e);
            return null;
        }
    }

    static String describe(int reason) {
        switch (reason) {
            case 3: return "Android tắt app vì thiếu RAM (LOW_MEMORY)";
            case 4: return "App bị lỗi Java (CRASH)";
            case 5: return "App bị lỗi mã máy (CRASH_NATIVE)";
            case 6: return "App bị treo (ANR)";
            case 2: return "Hệ thống tắt app (SIGKILL): do vuốt tắt app, hoặc HyperOS dọn app khi chuyển sang app khác";
            case 10: return "Người dùng tắt app (USER_REQUESTED)";
            case 1: return "App tự thoát (EXIT_SELF)";
            default: return "Lý do khác (" + reason + ")";
        }
    }

    /** Android's importance of the app when it ended, in words. */
    static String importance(int importance) {
        if (importance <= 100) return "app đang mở";
        if (importance <= 125) return "chạy nền có thông báo giữ app";
        if (importance < 300) return "app vẫn hiện trên màn hình";
        return "chạy nền, không được giữ";
    }
}
