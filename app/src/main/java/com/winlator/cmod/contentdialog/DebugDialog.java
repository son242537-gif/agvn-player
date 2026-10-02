package com.winlator.cmod.contentdialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.winlator.cmod.R;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.core.UnitUtils;
import com.winlator.cmod.widget.LogView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DebugDialog extends ContentDialog implements Callback<String> {
    private final LogView logView;
    private static boolean paused = false;
    private BufferedWriter writer;
    private File logFile;
    /** AGVN: the log goes to /sdcard once a second, not after every line (a game can write thousands a minute). */
    private final ScheduledExecutorService flusher = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "DebugDialogFlush");
        t.setDaemon(true);
        return t;
    });

    public DebugDialog(@NonNull Context context) {
        super(context, R.layout.debug_dialog);
        setIcon(R.drawable.icon_debug);
        setTitle(context.getString(R.string.logs));
        logView = findViewById(R.id.LogView);
        
        logView.getLayoutParams().width = (int)UnitUtils.dpToPx(UnitUtils.pxToDp(AppUtils.getScreenWidth()) * 0.7f);

       // findViewById(R.id.BTCancel).setVisibility(View.GONE);

        LinearLayout llBottomBarPanel = findViewById(R.id.LLBottomBarPanel);
        llBottomBarPanel.setVisibility(View.VISIBLE);

        View toolbarView = LayoutInflater.from(context).inflate(R.layout.debug_toolbar, llBottomBarPanel, false);
        toolbarView.findViewById(R.id.BTClear).setOnClickListener((v) -> logView.clear());
        toolbarView.findViewById(R.id.BTPause).setOnClickListener((v) -> {
            setPaused(!paused);
            ((ImageButton)v).setImageResource(getPaused() ? R.drawable.icon_play : R.drawable.icon_pause);
        });
        llBottomBarPanel.addView(toolbarView);
        try {
            logFile = LogView.getLogFile(context);
            writer = new BufferedWriter(new FileWriter(logFile));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        flusher.scheduleWithFixedDelay(this::flush, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public void call(final String line) {
        if (!getPaused()) logView.append(line+"\n");
        try {
            writer.write(line + "\n");
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** AGVN: writes out the lines still buffered, e.g. before the log is copied into the play session. */
    public void flush() {
        try {
            writer.flush();
        }
        catch (IOException ignored) {
            // the next flush tries again
        }
    }
    
    /** The file this dialog writes the log to (AGVN: copied into the play session's logs). */
    public File getLogFile() {
        return logFile;
    }

    public static void setPaused(boolean cond) {
        paused = cond;
    }
    
    public static boolean getPaused() {
        return paused;
    }
}
