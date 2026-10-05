/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.graphics.Bitmap;
import android.util.Log;

import com.winlator.cmod.SettingsFragment;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.ExeIconExtractor;
import com.winlator.cmod.core.FileUtils;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The icon of a game in the library. The .exe of a Ren'Py or RPG Maker game usually carries the engine's own icon,
 * so for them, and for phone copies without an .exe, the icon comes from the game itself: the window icon its
 * developer made (Ren'Py), else a square from its title or menu picture. Other games keep their exe icon.
 */
public final class AgvnGameIcons {
    /** Written next to AGVN-Player/icons/<name>.png ("<file>.agvn"), so games imported earlier get their icon once. */
    static final String VERSION = "1";
    static final int SIZE = 256;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private AgvnGameIcons() {}

    /** Engines whose .exe icon is usually the engine's own rather than the game's. */
    static boolean prefersGameArt(GameExeResolver.Engine engine) {
        return engine == GameExeResolver.Engine.RENPY || engine == GameExeResolver.Engine.RPGMAKER
                || engine == GameExeResolver.Engine.RPGMAKER_MV;
    }

    /** The icon, at most {@code size} pixels a side; null when neither the game nor its exe has one. Slow: off the UI thread. */
    static Bitmap make(File gameDir, GameExeResolver.Engine engine, File exe, int size) {
        boolean hasExe = exe != null && exe.isFile();
        Bitmap icon = prefersGameArt(engine) || !hasExe ? fromGame(gameDir, size) : null;
        if (icon == null && hasExe) icon = fit(ExeIconExtractor.extractBitmap(exe), size);
        return icon;
    }

    /** The developer's own icon when there is one, else a square from the game's cover picture; null when it has neither. */
    static Bitmap fromGame(File gameDir, int size) {
        Bitmap own = ownIcon(gameDir, size);
        if (own != null) return fit(own, size);
        Bitmap picture = AgvnCovers.firstPicture(AgvnCoverSources.candidates(gameDir, rtpRoot()), size, size);
        return picture != null ? square(picture, size) : null;
    }

    /** The icon of a cover drawn without a game picture: the game's own, else the exe's unless that is RPG Maker's. */
    static Bitmap forDrawnCover(File gameDir, File exe) {
        Bitmap own = ownIcon(gameDir, SIZE);
        if (own != null) return own;
        GameExeResolver.Engine engine = gameDir != null ? GameExeResolver.detectEngine(gameDir) : GameExeResolver.Engine.UNKNOWN;
        boolean stock = engine == GameExeResolver.Engine.RPGMAKER || engine == GameExeResolver.Engine.RPGMAKER_MV;
        return exe != null && exe.isFile() && !stock ? ExeIconExtractor.extractBitmap(exe) : null;
    }

    /** AGVN-Player/RTP: RPG Maker games that take their title picture from an RTP find it there. */
    static File rtpRoot() {
        return new File(SettingsFragment.DEFAULT_WINLATOR_PATH, AgvnRgssConfig.RTP_FOLDER);
    }

    private static Bitmap ownIcon(File gameDir, int size) {
        AgvnArt art = AgvnCoverSources.icon(gameDir);
        byte[] data = art != null ? art.read() : null;
        return data != null ? AgvnCovers.decode(data, size, size) : null;
    }

    /**
     * Writes the icon of a game being imported where the shortcut and the library look for it: the container's
     * icons/<name>.png (the .desktop's Icon=) and AGVN-Player/icons/<name>.png.
     */
    static void write(Container container, File gameDir, GameExeResolver.Engine engine, File exe, String name) {
        try {
            File iconDir = container.getIconsDir(64);
            iconDir.mkdirs();
            File icon = new File(iconDir, name + ".png");
            File copy = new File(new File(SettingsFragment.DEFAULT_WINLATOR_PATH, "icons"), name + ".png");
            boolean hasExe = exe != null && exe.isFile();
            Bitmap art = prefersGameArt(engine) || !hasExe ? fromGame(gameDir, SIZE) : null;
            boolean written = art != null ? AgvnCovers.save(art, icon) : hasExe && ExeIconExtractor.extractIcon(exe, icon);
            if (written) {
                copy.getParentFile().mkdirs();
                FileUtils.copy(icon, copy);
            }
            AgvnCovers.writeNote(copy, VERSION);
        } catch (Exception | OutOfMemoryError e) {
            Log.w("AGVN", "icon for " + name + " failed", e); // the library falls back to a generic icon
        }
    }

    /**
     * For a game imported before game icons: makes its icon from the game once, in the background, when its exe icon
     * is the engine's own (or it has no exe). {@code done} runs on the worker after a new icon was written.
     */
    public static void refreshAsync(Shortcut shortcut, File exe, File autoIcon, Runnable done) {
        String dir = shortcut.getExtra(AgvnGameImporter.EXTRA_GAME_DIR);
        if (dir.isEmpty() || shortcut.container == null || VERSION.equals(AgvnCovers.readNote(AgvnCovers.noteFor(autoIcon)))) return;
        EXECUTOR.execute(() -> {
            File gameDir = new File(dir);
            if (!gameDir.isDirectory()) return; // memory card not in: next time
            try {
                boolean hasExe = exe != null && exe.isFile();
                Bitmap art = prefersGameArt(engineOf(shortcut, gameDir)) || !hasExe ? fromGame(gameDir, SIZE) : null;
                File icon = shortcut.iconFile != null ? shortcut.iconFile
                        : new File(shortcut.container.getIconsDir(64), FileUtils.getBasename(shortcut.file.getPath()) + ".png");
                boolean written = art != null && AgvnCovers.save(art, icon);
                if (written) {
                    FileUtils.copy(icon, autoIcon);
                    shortcut.icon = art;
                }
                AgvnCovers.writeNote(autoIcon, VERSION);
                if (written && done != null) done.run();
            } catch (Exception | OutOfMemoryError e) {
                Log.w("AGVN", "icon for " + shortcut.name + " failed", e);
            }
        });
    }

    /** The engine noted at import, else found again from the folder (games imported before it was noted). */
    private static GameExeResolver.Engine engineOf(Shortcut shortcut, File gameDir) {
        try {
            return GameExeResolver.Engine.valueOf(shortcut.getExtra(AgvnGameImporter.EXTRA_ENGINE));
        } catch (IllegalArgumentException e) {
            return GameExeResolver.detectEngine(gameDir);
        }
    }

    /** A square of {@code size} from the middle of {@code picture}: the part a title screen keeps in view. */
    static Bitmap square(Bitmap picture, int size) {
        int side = Math.min(picture.getWidth(), picture.getHeight());
        int x = (picture.getWidth() - side) / 2, y = (picture.getHeight() - side) / 2;
        Bitmap crop = Bitmap.createBitmap(picture, x, y, side, side);
        return side == size ? crop : Bitmap.createScaledBitmap(crop, size, size, true);
    }

    private static Bitmap fit(Bitmap b, int size) {
        if (b == null || (b.getWidth() <= size && b.getHeight() <= size)) return b;
        float scale = Math.min(size / (float) b.getWidth(), size / (float) b.getHeight());
        int w = Math.max(1, Math.round(b.getWidth() * scale)), h = Math.max(1, Math.round(b.getHeight() * scale));
        return Bitmap.createScaledBitmap(b, w, h, true);
    }
}
