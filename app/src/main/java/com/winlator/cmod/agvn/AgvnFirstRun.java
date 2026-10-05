/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.winlator.cmod.R;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.contents.AdrenotoolsManager;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.WineRuntimeGuard;
import com.winlator.cmod.xenvironment.ImageFs;
import com.winlator.cmod.xenvironment.ImageFsInstaller;

import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * First start without any download: installs the bundled system files, Proton and drivers from the APK, then creates
 * the first game environment. State is process-wide, so a recreated setup screen picks up a running install instead
 * of starting a second one over the same files.
 */
public final class AgvnFirstRun {
    public interface Listener {
        void onCoreProgress(int progress);

        /** Core install ended; {@code ok} false means it must be retried. */
        void onCoreFinished(boolean ok);

        /** First environment ready ({@code ok}) or failed. */
        void onContainerFinished(boolean ok);
    }

    private static final String TAG = "AGVN";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static boolean coreRunning;
    private static int coreProgress;
    private static boolean containerRunning;
    private static Listener listener;

    private AgvnFirstRun() {}

    /** The current screen; callbacks always arrive on the main thread. Pass null when the screen goes away. */
    public static void setListener(Listener l) {
        listener = l;
    }

    public static boolean isCoreReady(Context context) {
        ImageFs imageFs = ImageFs.find(context);
        return imageFs.isValid() && imageFs.getVersion() >= ImageFsInstaller.LATEST_VERSION;
    }

    public static boolean isCoreRunning() {
        return coreRunning;
    }

    public static int coreProgress() {
        return coreProgress;
    }

    public static boolean isContainerRunning() {
        return containerRunning;
    }

    public static boolean hasContainer(Context context) {
        return !new ContainerManager(context).getContainers().isEmpty();
    }

    /**
     * Starts the bundled install unless one is already running (main thread). When the system files are already in
     * place only the bundled Proton is checked, so calling this again is cheap.
     */
    public static void startCore(AppCompatActivity activity) {
        if (coreRunning) return;
        coreRunning = true;
        if (isCoreReady(activity)) {
            coreProgress = 90;
            verifyRuntime(activity);
            return;
        }
        coreProgress = 0;
        dropBundledDrivers(activity);
        ImageFsInstaller.installFromAssetsSilently(activity, new ImageFsInstaller.InstallationProgressListener() {
            @Override
            public void onProgress(int progress) {
                coreProgress = Math.max(coreProgress, progress);
                if (listener != null) listener.onCoreProgress(coreProgress);
            }

            @Override
            public void onFinished(boolean success) {
                if (success) verifyRuntime(activity);
                else finishCore(false);
            }
        });
    }

    /** The installer ignores Proton errors: check it and extract it once more if needed. */
    private static void verifyRuntime(AppCompatActivity activity) {
        Context app = activity.getApplicationContext();
        IO.execute(() -> {
            boolean ok = WineRuntimeGuard.isBundledMainInstalled(app);
            if (!ok) {
                try {
                    ImageFsInstaller.installWineFromAssets(null, activity);
                } catch (Exception e) {
                    Log.w(TAG, "bundled Proton install failed", e);
                }
                ok = WineRuntimeGuard.isBundledMainInstalled(app);
            }
            boolean result = ok;
            MAIN.post(() -> finishCore(result));
        });
    }

    /**
     * A driver folder that exists is never extracted again, so one cut off by an earlier interrupted first start
     * would stay broken: remove the bundled ones before a fresh install puts them back.
     */
    private static void dropBundledDrivers(Context context) {
        AdrenotoolsManager drivers = new AdrenotoolsManager(context);
        for (String id : context.getResources().getStringArray(R.array.wrapper_graphics_driver_version_entries)) {
            if (id.equalsIgnoreCase("System") || !drivers.isFromResources(id)) continue;
            FileUtils.delete(new File(context.getFilesDir(), "contents/adrenotools/" + id));
        }
    }

    private static void finishCore(boolean ok) {
        coreRunning = false;
        if (ok) coreProgress = 100;
        if (listener != null) listener.onCoreFinished(ok);
    }

    /** Creates the first environment with the bundled Proton (main thread). No-op when one exists or is being made. */
    public static void createFirstContainer(Context context) {
        Context app = context.getApplicationContext();
        if (containerRunning) return;
        if (hasContainer(app)) {
            MAIN.post(() -> {
                if (listener != null) listener.onContainerFinished(true);
            });
            return;
        }
        containerRunning = true;
        IO.execute(() -> {
            JSONObject data = null;
            ContentsManager contents = new ContentsManager(app);
            try {
                contents.syncContents();
                data = AgvnFirstContainer.data(app, contents, new ContainerManager(app).getNextContainerId());
            } catch (Exception e) {
                Log.w(TAG, "first environment data failed", e);
            }
            JSONObject ready = data;
            MAIN.post(() -> {
                if (ready == null) {
                    finishContainer(false);
                    return;
                }
                new ContainerManager(app).createContainerAsync(ready, contents, created -> finishContainer(created != null));
            });
        });
    }

    private static void finishContainer(boolean ok) {
        containerRunning = false;
        if (listener != null) listener.onContainerFinished(ok);
    }
}
