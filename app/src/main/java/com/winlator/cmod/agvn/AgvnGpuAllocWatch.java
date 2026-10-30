/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.core.ProcessHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Graphics memory the driver refused while there was room ({@link AgvnGpuAlloc#roomLeft}), seen as the game plays:
 * after it DXVK has no way to draw, and the game may freeze for good instead of closing, so "Tự sửa lỗi" would only
 * ask once the player gives up (an Unreal Engine 5 game, Adreno 830, 30/10/2026). su-kien.txt says so, and a bar
 * offers, once a session, to start the game again with the driver's shared memory hidden ({@link AgvnGpuAlloc#FIX}).
 */
public final class AgvnGpuAllocWatch implements Callback<String> {
    private static final String TAG = "AGVN", FAILED = "DxvkMemoryAllocator: Memory allocation failed";

    private final XServerDisplayActivity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Wine's lines from the last failed allocation on, while DXVK prints its heaps (Wine's output thread). */
    private final List<String> since = new ArrayList<>();
    private volatile boolean told;

    private AgvnGpuAllocWatch(XServerDisplayActivity activity) {
        this.activity = activity;
    }

    /** Reads Wine's output from now on (ProcessHelper's callbacks: every game's output is read, logs on or off). */
    public static void attach(XServerDisplayActivity activity) {
        ProcessHelper.addDebugCallback(new AgvnGpuAllocWatch(activity));
    }

    @Override
    public void call(String line) {
        if (line == null || told) return;
        if (line.contains(FAILED)) since.clear();
        else if (since.isEmpty()) return;
        since.add(line);
        if (since.size() > AgvnGpuAlloc.SCAN_LINES) {
            since.clear();
            return;
        }
        if (!AgvnGpuAlloc.roomLeft(since)) return;
        told = true;
        handler.post(this::found);
    }

    private void found() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        AgvnSessionLog.event("Driver đồ họa không cấp bộ nhớ cho game dù bộ nhớ đồ họa còn trống (DxvkMemoryAllocator)");
        Log.i(TAG, "GPU memory refused with room left");
        Shortcut s = activity.agvnShortcut();
        boolean fix = s != null && AgvnFixes.dxvk(s)
                && !AgvnGpuAlloc.hidden(AgvnFixEdits.configValue(AgvnFixes.driverConfig(s), AgvnGpuAlloc.KEY, ';'));
        String detail = activity.getString(R.string.agvn_gpu_refused_detail)
                + (fix ? "\n" + activity.getString(R.string.agvn_gpu_refused_fix) : "");
        List<AgvnWarningBar.Choice> choices = new ArrayList<>();
        if (fix) choices.add(new AgvnWarningBar.Choice(R.string.agvn_gpu_refused_restart, () -> hideShared(s)));
        choices.add(new AgvnWarningBar.Choice(fix ? R.string.agvn_black_wait : R.string.agvn_movie_ok, null));
        AgvnWarningBar.show(activity, activity.getString(R.string.agvn_gpu_refused_title), detail,
                choices.toArray(new AgvnWarningBar.Choice[0]));
    }

    /** The driver's shared memory hidden for the game, which starts again at once. */
    private void hideShared(Shortcut s) {
        s.putExtra("graphicsDriverConfig", AgvnGpuAlloc.withHidden(AgvnFixes.driverConfig(s)));
        s.saveData();
        AgvnSessionLog.event("Người chơi chọn tắt bộ nhớ đồ họa chia sẻ (" + AgvnGpuAlloc.EXTENSION + "); game mở lại");
        AgvnDoctor.requestRelaunch(activity, s);
        activity.agvnExit();
    }
}
