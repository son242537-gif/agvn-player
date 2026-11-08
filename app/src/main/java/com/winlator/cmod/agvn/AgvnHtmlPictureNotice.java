/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Context;

import com.winlator.cmod.R;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Tells the player, once a session, on a bar over the game ({@link AgvnWarningBar}), when an HTML game's system
 * picture (img/system: windows, buttons, icons) is shown empty, with the file and why ({@link AgvnHtmlStandIn#why}):
 * the game's screens then miss parts. Before, only logcat and "Gửi nhật ký" had it, and an MZ game stopped on its own
 * error instead ("ButtonSet image is too small", 19/10/2026). Pure Java but {@link #around} and {@link #show}.
 */
final class AgvnHtmlPictureNotice {
    /** How {@link AgvnHtmlStandIn#why} names the game folder itself. */
    private static final String GAME_FOLDER = "the game folder";

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
        String detail = a.getString(R.string.agvn_html_picture_detail, file(item), reason(a, why(item)));
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

    /** {@link AgvnHtmlStandIn#why} in the player's words, in Vietnamese (the JVM tests: no Context). */
    static String reason(String why) {
        return reason(null, why);
    }

    /** {@link AgvnHtmlStandIn#why} in the player's words, in the app's language ({@code c} null: in Vietnamese). */
    static String reason(Context c, String why) {
        if (why.equals("no-access to " + GAME_FOLDER))
            return text(c, R.string.agvn_ui_agvn1_picture_no_access_game, "app không đọc được thư mục game");
        String in = why.replace(GAME_FOLDER, text(c, R.string.agvn_ui_agvn1_picture_game_folder, "thư mục game"));
        if (in.startsWith("short: "))
            return text(c, R.string.agvn_ui_agvn1_picture_short, "tệp chỉ có %1$s byte, không phải ảnh",
                    in.substring(7).replace(" bytes", ""));
        if (in.startsWith("no-access to "))
            return text(c, R.string.agvn_ui_agvn1_picture_no_access, "app không đọc được thư mục %1$s", in.substring(13));
        if (in.startsWith("not-found in "))
            return text(c, R.string.agvn_ui_agvn1_picture_not_found, "không có trong %1$s", in.substring(13));
        return text(c, R.string.agvn_ui_agvn1_picture_no_file, "không có tệp này");
    }

    /** The app's text {@code id}, else (no Context) its Vietnamese {@code vi}, with {@code args}. */
    private static String text(Context c, int id, String vi, Object... args) {
        return c != null ? c.getString(id, args) : String.format(Locale.ROOT, vi, args);
    }
}
