/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.graphics.Bitmap;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;

import org.json.JSONArray;
import org.json.JSONException;

/** Game script messages in logcat (tag AgvnHtml): warnings and errors always, everything with a log setting on. */
public final class AgvnHtmlConsole extends WebChromeClient {
    private final boolean all;

    AgvnHtmlConsole(boolean all) {
        this.all = all;
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage m) {
        ConsoleMessage.MessageLevel level = m.messageLevel();
        boolean problem = level == ConsoleMessage.MessageLevel.ERROR || level == ConsoleMessage.MessageLevel.WARNING;
        if (all || problem) {
            Log.println(problem ? Log.WARN : Log.INFO, "AgvnHtml", m.message() + " (" + m.sourceId() + ":" + m.lineNumber() + ")");
        }
        return true;
    }

    /** No grey "play" picture on a game video before its first frame. */
    @Override
    public Bitmap getDefaultVideoPoster() {
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    }

    /** The error that stopped the game, kept by html-compat.js; "" while there is none. */
    static final String FATAL = "window.__agvnFatal||''";
    static final long FATAL_POLL_MS = 5000;

    /** A value evaluateJavascript returned (a JSON string) as plain text; "" for none. */
    static String unquote(String json) {
        if (json == null || json.isEmpty() || json.equals("null")) return "";
        try {
            return new JSONArray("[" + json + "]").optString(0, "");
        } catch (JSONException e) {
            return "";
        }
    }
}
