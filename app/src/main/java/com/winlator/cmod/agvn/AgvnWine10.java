/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.os.StatFs;
import android.util.Log;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.TarCompressorUtils;

import org.json.JSONObject;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;

/**
 * Proton 10, AGVN's second Wine (app/agvn-wine.gradle). Proton 9's mfplat drops the codec data of a movie's video type,
 * so a WMV3 movie never plays and the game waits for ever (KiriKiri ticket, 06/10); Proton 10 plays it. New games go
 * to Proton 10, but .NET games ({@link #forImport}); a game on Proton 9 whose movie does not play moves over
 * ({@link AgvnMovieWatch}), and one that fails on Proton 10 can go back ("Tự sửa lỗi": wine-old). Wine belongs to a
 * container, so Proton 10 has its own ({@link #CONTAINER_NAME}), made once like the first one. The bundled package is
 * unpacked once, as the player's .wcp would be (ContentsManager), without its build files (.a) and the libraries in
 * lib/, which nothing here loads: AGVN draws through X11 and lib/ is not on the library path. Worker threads.
 */
public final class AgvnWine10 {
    private static final String TAG = "AGVN";
    static final String ASSET = "agvn/proton-10.0-4-arm64ec.wcp";
    /** ContentsManager's name for it and a container's wineVersion: type, versionName and versionCode of its profile. */
    public static final String IDENTIFIER = "Proton-10.0-4-arm64ec-9";
    static final String CONTAINER_NAME = "AGVN Proton 10";
    /** Bytes of the files kept (for the progress), and the free space it needs with a container made from it. */
    static final long UNPACKED_BYTES = 394L << 20, NEEDED_BYTES = 900L << 20;
    /** Files of the package left out: Windows import libraries, lib/'s Wayland and Mesa libraries, their Vulkan ICDs. */
    private static final Pattern SKIP = Pattern.compile("(?:\\./)?(?:lib/wine/[^/]+/[^/]+\\.a|lib/[^/]+\\.so(?:\\.\\d+)*|share/vulkan/.*)");

    private AgvnWine10() {}

    /** True when {@code entry}, a path in the package, is left out. */
    static boolean skip(String entry) {
        return SKIP.matcher(entry).matches();
    }

    /** True when {@code c} runs Proton 10. */
    public static boolean runs(Container c) {
        return c != null && IDENTIFIER.equals(c.getWineVersion());
    }

    static boolean installed(Context ctx) {
        ContentsManager contents = new ContentsManager(ctx);
        contents.syncContents();
        ContentProfile p = contents.getProfileByEntryName(IDENTIFIER);
        return p != null && new File(ContentsManager.getInstallDir(ctx, p), "bin/wine").exists();
    }

    /** Unpacks Proton 10 once (about a minute; {@code percent} 0-100 when not null); false when there is no room or it failed. */
    static synchronized boolean install(Context ctx, IntConsumer percent) {
        if (installed(ctx)) return true;
        long free = new StatFs(ctx.getFilesDir().getPath()).getAvailableBytes();
        if (free < NEEDED_BYTES) {
            Log.w(TAG, "Proton 10 not installed: " + (free >> 20) + " MB free, " + (NEEDED_BYTES >> 20) + " MB needed");
            return false;
        }
        ContentsManager.cleanTmpDir(ctx);
        File tmp = ContentsManager.getTmpDir(ctx);
        tmp.mkdirs();
        String root = tmp.getPath() + File.separator;
        AtomicLong done = new AtomicLong();
        AtomicInteger said = new AtomicInteger(-1);
        boolean unpacked = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, ctx, ASSET, tmp, (file, size) -> {
            String path = file.getPath();
            if (skip(path.startsWith(root) ? path.substring(root.length()) : path)) return null;
            int now = (int) Math.min(99, done.addAndGet(Math.max(0, size)) * 100 / UNPACKED_BYTES);
            if (percent != null && said.getAndSet(now) != now) percent.accept(now);
            return file;
        });
        ContentsManager contents = new ContentsManager(ctx);
        ContentProfile profile = unpacked ? contents.readProfile(new File(tmp, "profile.json")) : null;
        if (profile == null || !IDENTIFIER.equals(ContentsManager.getEntryName(profile))) {
            Log.w(TAG, "Proton 10 not installed: " + (unpacked ? "profile " + (profile != null ? ContentsManager.getEntryName(profile) : null) : "not unpacked"));
            ContentsManager.cleanTmpDir(ctx);
            return false;
        }
        contents.finishInstallContent(profile, new ContentsManager.OnInstallFinishedCallback() {
            @Override
            public void onFailed(ContentsManager.InstallFailedReason reason, Exception e) {
                Log.w(TAG, "Proton 10 not installed: " + reason, e);
            }

            @Override
            public void onSucceed(ContentProfile p) {
                Log.i(TAG, "Proton 10 installed");
            }
        });
        if (percent != null) percent.accept(100);
        return installed(ctx);
    }

    /** Proton 10's container: the one made before, else a new one like the first container; null when it cannot be. */
    static synchronized Container container(Context ctx, IntConsumer percent) {
        Container made = find(new ContainerManager(ctx).getContainers(), true);
        if (made != null) return made;
        if (!install(ctx, percent)) return null;
        try {
            ContentsManager contents = new ContentsManager(ctx);
            contents.syncContents();
            JSONObject data = AgvnFirstContainer.data(ctx, contents, 0);
            data.put("name", CONTAINER_NAME);
            data.put("wineVersion", IDENTIFIER);
            Container c = new ContainerManager(ctx).createContainerNow(data, contents);
            Log.i(TAG, "Proton 10 container " + (c != null ? String.valueOf(c.id) : "not made"));
            return c;
        } catch (Exception e) {
            Log.w(TAG, "Proton 10 container not made", e);
            return null;
        }
    }

    /** The first container that runs Proton 10 ({@code ten}) or another Wine, or null. */
    static Container find(List<Container> containers, boolean ten) {
        for (Container c : containers) if (runs(c) == ten) return c;
        return null;
    }

    /**
     * The container a game being added goes to: Proton 10's; for a .NET game (Proton 10 asks for Wine Mono 10.4.1, the
     * app has 9.3.1), or when Proton 10 cannot be set up, one with the other Wine. Null when there is no container.
     */
    static Container forImport(Context ctx, File exe, IntConsumer percent) {
        List<Container> all = new ContainerManager(ctx).getContainers();
        Container other = find(all, false);
        if (other == null && !all.isEmpty()) other = all.get(0);
        if (exe != null && exe.isFile() && AgvnWineMono.isDotNet(exe)) return other;
        Container ten = container(ctx, percent);
        return ten != null ? ten : other;
    }
}
