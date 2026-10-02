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
        if (Build.VERSION.SDK_INT >= 30 && startMs > 0) {
            try {
                ActivityManager am = context.getSystemService(ActivityManager.class);
                ApplicationExitInfo first = null; // the list is newest first; the session ended with the oldest one after it began
                for (ApplicationExitInfo info : am.getHistoricalProcessExitReasons(context.getPackageName(), 0, 16))
                    if (info.getTimestamp() >= startMs) first = info;
                if (first != null)
                    return describe(first.getReason()) + " (reason=" + first.getReason() + ", importance="
                            + first.getImportance() + " – " + importance(first.getImportance()) + ", " + first.getDescription() + ")";
            } catch (RuntimeException e) {
                Log.w(TAG, "exit reasons not readable", e);
            }
        }
        return "App dừng giữa phiên, không rõ lý do";
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
