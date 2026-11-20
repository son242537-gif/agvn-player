/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.io.IOException;

/**
 * The real file behind the address another app hands over when the player opens an archive with AGVN Player. The
 * unpackers need a path: they seek in the file and look for the other parts beside it. The app may read all of shared
 * storage, so the address only has to tell where the file is: a file: address, a document id of Android's pickers, the
 * path a provider gives ("_data"), or a guess from the address ({@link AgvnUriPath}) that is taken only when a file of
 * that name and size is there. Nothing is copied: null when the file cannot be found, and the player is told to pick
 * it in "Thêm từ tệp nén".
 */
final class AgvnOpenArchivePath {
    private static final String TAG = "AGVN";

    /** What the other app says of the file. */
    static final class Named {
        String name;
        long size = -1;
    }

    private AgvnOpenArchivePath() {}

    /** The file on shared storage, or null. Reads the other app's provider: off the main thread. */
    static File resolve(Context ctx, Uri uri, Named named) {
        if (uri == null) return null;
        String internal = Environment.getExternalStorageDirectory().getAbsolutePath();
        String scheme = uri.getScheme(), path = uri.getPath();
        if ("file".equalsIgnoreCase(scheme)) {
            named.name = path != null ? new File(path).getName() : null;
            return path != null && !AgvnUriPath.climbs(path) ? usable(new File(path)) : null;
        }
        if (!"content".equalsIgnoreCase(scheme)) return null;
        describe(ctx, uri, named);
        if (named.name == null) named.name = AgvnUriPath.nameIn(path);
        try {
            if (DocumentsContract.isDocumentUri(ctx, uri)) {
                String id = DocumentsContract.getDocumentId(uri);
                String exact = AgvnUriPath.fromDocumentId(uri.getAuthority(), id, internal);
                File file = exact != null ? usable(new File(exact)) : null;
                if (file != null) return file;
                long media = AgvnUriPath.mediaId(id);
                if (media >= 0) {
                    file = dataOf(ctx, ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), media));
                    if (file != null && matches(file, named)) return file;
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "document id not read: " + uri.getAuthority(), e);
        }
        File file = dataOf(ctx, uri);
        if (file != null) return file;
        // a guess: only a file of the same name and size
        if (named.size <= 0) return null;
        for (String guess : AgvnUriPath.guesses(path, named.name, internal)) {
            file = usable(new File(guess));
            if (file != null && matches(file, named) && file.length() == named.size) return file;
        }
        return null;
    }

    private static void describe(Context ctx, Uri uri, Named named) {
        try (Cursor c = ctx.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (c == null || !c.moveToFirst()) return;
            int name = c.getColumnIndex(OpenableColumns.DISPLAY_NAME), size = c.getColumnIndex(OpenableColumns.SIZE);
            if (name >= 0 && !c.isNull(name)) named.name = new File(c.getString(name)).getName();
            if (size >= 0 && !c.isNull(size)) named.size = c.getLong(size);
        } catch (RuntimeException e) { // the other app's provider: may refuse, may be broken
            Log.w(TAG, "name of opened file not read: " + uri.getAuthority(), e);
        }
    }

    /** The path a provider gives in "_data" (the media store, some file managers), when that file is there. */
    private static File dataOf(Context ctx, Uri uri) {
        try (Cursor c = ctx.getContentResolver().query(uri, new String[]{"_data"}, null, null, null)) {
            if (c == null || !c.moveToFirst() || c.isNull(0)) return null;
            String path = c.getString(0);
            return path != null && path.startsWith("/") && !AgvnUriPath.climbs(path) ? usable(new File(path)) : null;
        } catch (RuntimeException e) { // no such column, or not allowed
            return null;
        }
    }

    /** The name and, when told, the size are the ones the other app gave. */
    private static boolean matches(File file, Named named) {
        if (named.name != null && !file.getName().equalsIgnoreCase(named.name)) return false;
        return named.size <= 0 || file.length() == named.size;
    }

    /** {@code file} when it is a readable file on shared storage (never the app's own data, never the system's). */
    private static File usable(File file) {
        if (!file.isFile() || !file.canRead()) return null;
        try {
            String real = file.getCanonicalPath();
            for (File volume : AgvnGameImporter.storageVolumes()) {
                if (real.startsWith(volume.getCanonicalPath() + File.separator)) return new File(real);
            }
        } catch (IOException e) {
            // not a path that can be followed
        }
        return null;
    }
}
