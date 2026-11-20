/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Lifecycle;

import com.winlator.cmod.MainActivity;
import com.winlator.cmod.R;
import com.winlator.cmod.agvn.AgvnUnpackException.Reason;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * "Thêm từ tệp nén": unpacks the archive the player picked into internal storage/AGVN ({@link AgvnUnpack}) behind a
 * dialog with the percent done, the file being written and "Hủy". The screen stays on while the dialog shows; when the
 * player goes to another app the work goes on ({@link AgvnUnpackService}) and its end is told by a notification. The
 * password is asked only when the archive turns out to be locked ({@link AgvnUnpackPassword}), again after a wrong
 * one. Once unpacked, the player is asked whether the archive may be deleted ({@link AgvnUnpackFlow#askDelete}).
 */
public final class AgvnUnpackDialog {
    /** One archive at a time. */
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    /** The dialog is redrawn this often at most; the worker reports after every block it writes. */
    private static final long REDRAW_MS = 120;

    /** 7-Zip's console program in the app's nativeLibraryDir (scripts/agvn/7zip/build-7zz.sh). */
    private static final String SEVEN_ZIP = "libagvn7zz.so";

    public interface Done {
        /** {@code gameDir}: the new folder, complete. */
        void onUnpacked(File gameDir);
    }

    private AgvnUnpackDialog() {}

    public static void start(MainActivity activity, File archive, Done done) {
        run(activity, archive, null, done);
    }

    private static void run(MainActivity a, File archive, String password, Done done) {
        if (a.isFinishing() || a.isDestroyed()) return;
        int pad = (int) (20 * a.getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        TextView percent = new TextView(a);
        percent.setText(R.string.agvn_unpack_opening);
        ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setIndeterminate(true);
        TextView name = new TextView(a);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        TextView note = new TextView(a);
        note.setText(R.string.agvn_unpack_keep_open);
        note.setPadding(0, pad / 2, 0, 0);
        box.addView(percent);
        box.addView(bar);
        box.addView(name);
        box.addView(note);

        AtomicBoolean cancel = new AtomicBoolean(), finished = new AtomicBoolean();
        AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle(a.getString(R.string.agvn_unpack_progress_title, archive.getName()))
                .setView(box)
                .setCancelable(false)
                .setNegativeButton(R.string.agvn_cancel, (d, w) -> cancel.set(true))
                .create();
        // closed with its screen (the player left the app): the work goes on, the notification has "Hủy"
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Handler main = new Handler(Looper.getMainLooper());
        Context app = a.getApplicationContext();
        File sevenZip = new File(a.getApplicationInfo().nativeLibraryDir, SEVEN_ZIP);
        AgvnUnpackService.begin(app, archive.getName(), cancel);
        AgvnUnpack.Listener listener = new AgvnUnpack.Listener() {
            long drawn;

            @Override
            public void onProgress(long doneBytes, long totalBytes, String file) {
                long now = SystemClock.uptimeMillis();
                if (now - drawn < REDRAW_MS && doneBytes < totalBytes) return;
                drawn = now;
                int permille = totalBytes > 0 ? (int) (doneBytes * 1000 / totalBytes) : 1000;
                AgvnUnpackService.progress(app, permille);
                main.post(() -> {
                    if (finished.get()) return;
                    bar.setIndeterminate(false);
                    bar.setProgress(permille);
                    percent.setText(a.getString(R.string.agvn_unpack_progress, permille / 10,
                            Formatter.formatShortFileSize(a, doneBytes), Formatter.formatShortFileSize(a, totalBytes)));
                    name.setText(file);
                });
            }

            @Override
            public boolean cancelled() {
                return cancel.get();
            }
        };
        WORK.execute(() -> {
            AgvnUnpack.Result result = null;
            AgvnUnpackException error = null;
            try {
                result = AgvnUnpack.extract(archive, password, AgvnGameImporter.getGamesRoot(), sevenZip, listener);
            } catch (AgvnUnpackException e) {
                error = e;
            } catch (Throwable t) { // never leaves the dialog up
                error = new AgvnUnpackException(Reason.DAMAGED, null, 0, t);
            }
            AgvnUnpack.Result unpacked = result;
            AgvnUnpackException failure = error;
            main.post(() -> {
                finished.set(true);
                boolean gone = a.isFinishing() || a.isDestroyed();
                boolean watching = !gone && a.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED);
                AgvnUnpackService.end(app, watching ? null : AgvnUnpackPassword.notice(a, archive.getName(), failure));
                if (gone) return; // the folder is in AGVN: "Thêm game" finds it
                dialog.dismiss();
                if (unpacked != null) AgvnUnpackFlow.askDelete(a, unpacked, () -> done.onUnpacked(unpacked.dir));
                else failed(a, archive, password, failure, done);
            });
        });
    }

    private static void failed(MainActivity a, File archive, String password, AgvnUnpackException e, Done done) {
        android.util.Log.w("AGVN", "unpack " + archive.getName() + ": " + e.getMessage(), e.getCause());
        switch (e.reason) {
            case NEED_PASSWORD:
            case WRONG_PASSWORD:
                AgvnUnpackPassword.ask(a, archive.getName(), e.reason == Reason.WRONG_PASSWORD ? password : null,
                        typed -> run(a, archive, typed, done));
                break;
            case CANCELLED:
                Toast.makeText(a, R.string.agvn_unpack_cancelled, Toast.LENGTH_SHORT).show();
                break;
            default:
                new AlertDialog.Builder(a)
                        .setTitle(R.string.agvn_unpack_failed_title)
                        .setMessage(AgvnUnpackPassword.message(a, e))
                        .setPositiveButton(R.string.agvn_close, null)
                        .show();
        }
    }
}
