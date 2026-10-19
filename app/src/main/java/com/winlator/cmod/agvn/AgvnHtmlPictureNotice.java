/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;

import com.winlator.cmod.R;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Tells the player, once a session, on a bar over the game ({@link AgvnWarningBar}), when an HTML game's system
 * picture (img/system: windows, buttons, icons) is shown empty, with the file and why ({@link AgvnHtmlStandIn#why}):
 * the game's screens then miss parts. Before, only logcat and "Gửi nhật ký" had it, and an MZ game stopped on its own
 * error instead ("ButtonSet image is too small", 19/10/2026). Pure Java but {@link #around} and {@link #show}.
 */
final class AgvnHtmlPictureNotice {
    private AgvnHtmlPictureNotice() {}

    /** {@code keep} (the session's list), also showing the bar for the first system picture told. */
    static Consumer<String> around(Activity activity, Consumer<String> keep) {
        AtomicBoolean told = new AtomicBoolean();
        return item -> {
            keep.accept(item);
            if (AgvnHtmlStandIn.systemPicture(item) && told.compareAndSet(false, true))
                activity.runOnUiThread(() -> show(activity, item)); // told on WebView's file thread
        };
    }

    private static void show(Activity a, String item) {
        String detail = a.getString(R.string.agvn_html_picture_detail, file(item), reason(why(item)));
        if (!AgvnFirstContainer.hasStorageAccess(a)) detail += "\n" + a.getString(R.string.agvn_html_picture_access);
        AgvnWarningBar.show(a, a.getString(R.string.agvn_html_picture_title), detail,
                new AgvnWarningBar.Choice(R.string.agvn_movie_ok, null));
    }

    /** "img/system/ButtonSet.png_" of "/img/system/ButtonSet.png_ (not-found in img/system)". */
    static String file(String item) {
        int open = item.lastIndexOf(" (");
        String path = open > 0 ? item.substring(0, open) : item;
        return path.startsWith("/") ? path.substring(1) : path;
    }

    /** "not-found in img/system" of "/img/system/ButtonSet.png_ (not-found in img/system)"; "" without one. */
    static String why(String item) {
        int open = item.lastIndexOf(" (");
        return open > 0 && item.endsWith(")") ? item.substring(open + 2, item.length() - 1) : "";
    }

    /** {@link AgvnHtmlStandIn#why} in the player's words. */
    static String reason(String why) {
        String in = why.replace("the game folder", "thư mục game");
        if (in.startsWith("short: ")) return "tệp chỉ có " + in.substring(7).replace(" bytes", " byte") + ", không phải ảnh";
        if (in.startsWith("no-access to ")) return "app không đọc được thư mục " + in.substring(13);
        if (in.startsWith("not-found in ")) return "không có trong " + in.substring(13);
        return "không có tệp này";
    }
}
