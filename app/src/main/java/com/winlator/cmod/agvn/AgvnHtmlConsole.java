/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.graphics.Bitmap;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Game script messages in logcat (tag AgvnHtml): warnings and errors always, everything with a log setting on. A
 * message seen again is counted, not logged again ({@link #repeat}).
 */
public final class AgvnHtmlConsole extends WebChromeClient {
    /** Messages told apart at once: a flood of up to this many different messages is folded. */
    static final int KEYS = 32;
    private final boolean all;
    /** Times each recent message came; the least recently seen goes first. UI thread only. */
    private final Map<String, int[]> seen = new LinkedHashMap<String, int[]>(KEYS, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, int[]> eldest) {
            return size() > KEYS;
        }
    };

    AgvnHtmlConsole(boolean all) {
        this.all = all;
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage m) {
        ConsoleMessage.MessageLevel level = m.messageLevel();
        boolean problem = level == ConsoleMessage.MessageLevel.ERROR || level == ConsoleMessage.MessageLevel.WARNING;
        if (all || problem) {
            String line = repeat(seen, m.message() + " (" + m.sourceId() + ":" + m.lineNumber() + ")");
            if (line != null) Log.println(problem ? Log.WARN : Log.INFO, "AgvnHtml", line);
        }
        return true;
    }

    /**
     * {@code text} as logged: whole the first time, then only the 10th, 100th, 1000th... time, with its count; null
     * the other times. An RPG Maker game drawing text without an alignment had WebView warn "The provided value
     * 'undefined' is not a valid enum value of type CanvasTextAlign" 19,339 times in 9 minutes, and the logcat that
     * "Gửi nhật ký" sent kept nothing else (07/10/2026).
     */
    static String repeat(Map<String, int[]> seen, String text) {
        int[] times = seen.get(text);
        if (times == null) seen.put(text, times = new int[1]);
        int count = ++times[0];
        if (count == 1) return text;
        long power = 10;
        while (power < count) power *= 10;
        return power == count ? text + " (×" + count + ")" : null;
    }

    /** No grey "play" picture on a game video before its first frame. */
    @Override
    public Bitmap getDefaultVideoPoster() {
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    }

    /** The error that stopped the game, kept by html-compat.js; "" while there is none. */
    static final String FATAL = "window.__agvnFatal||''";
    /** Often enough for a player who quits as soon as an error screen shows (a plugin that fails as it loads). */
    static final long FATAL_POLL_MS = 2000;

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
