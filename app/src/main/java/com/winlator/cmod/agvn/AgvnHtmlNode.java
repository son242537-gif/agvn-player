/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;

import com.winlator.cmod.core.FileUtils;

/**
 * Node's Buffer and require('crypto') for a page game written for NW.js (assets/agvn/html-node-*.js), put before
 * html-compat.js, whose require hands crypto out. A plugin of an RPG Maker MZ game decrypted its settings with
 * Buffer and crypto.createDecipheriv as it loaded, and stopped the game with "Buffer is not defined" (POCO, 26/10/2026).
 */
final class AgvnHtmlNode {
    /** In the order they need each other. */
    static final String[] SCRIPTS = {"agvn/html-node-buffer.js", "agvn/html-node-hash.js", "agvn/html-node-aes.js",
            "agvn/html-node-crypto.js"};

    private AgvnHtmlNode() {}

    /** {@code compatJs} with the scripts that could be read before it; null when {@code compatJs} is. */
    static String before(Context ctx, String compatJs) {
        if (compatJs == null) return null;
        StringBuilder all = new StringBuilder();
        for (String asset : SCRIPTS) {
            String js = FileUtils.readString(ctx, asset);
            if (js != null) all.append(js).append('\n');
        }
        return all.append(compatJs).toString();
    }
}
