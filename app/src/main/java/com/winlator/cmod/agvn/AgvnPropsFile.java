/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * A small settings file several processes read ({@link AgvnLightPrefs}, {@link AgvnDeviceFacts}). It is written whole
 * to a temporary file, then moved over the old one in one step, so a reader never finds half a file. Files.move, not
 * File.renameTo: on Windows (the unit tests of a PC build) renameTo refuses to replace a file that exists. Pure Java.
 */
final class AgvnPropsFile {
    private AgvnPropsFile() {}

    /** The file's settings; none when it is missing or cannot be read. */
    static Properties load(File file) {
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            props.load(in);
        } catch (IOException | RuntimeException ignored) {
            // first use: no settings yet
        }
        return props;
    }

    /** Returns false, leaving the old file as it was, when the new one cannot be written. */
    static boolean store(Properties props, File file, String comment) {
        File tmp = new File(file.getPath() + ".tmp");
        try {
            try (OutputStream out = new FileOutputStream(tmp)) {
                props.store(out, comment);
            }
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            tmp.delete();
            return false;
        }
    }
}
