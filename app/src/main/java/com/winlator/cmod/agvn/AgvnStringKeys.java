/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;

import com.winlator.cmod.R;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Text the app shows in another language (AgvnLanguage), back to the Vietnamese it was translated from: what is keyed
 * by Vietnamese labels (the "?" help of each setting, AgvnSettingHelp) finds it again. Every string of the app is read
 * once in the language shown and once in Vietnamese (values/); a text two strings share keeps the first one's.
 */
final class AgvnStringKeys {
    private static final String TAG = "AGVN";

    private AgvnStringKeys() {}

    /** The app's strings as {@code ctx} shows them → the same strings in Vietnamese; empty when it cannot tell. */
    static Map<String, String> toVietnamese(Context ctx) {
        try {
            Resources shown = ctx.getResources();
            Configuration config = new Configuration(shown.getConfiguration());
            config.setLocale(new Locale(AgvnLanguage.VIETNAMESE));
            Resources vi = ctx.createConfigurationContext(config).getResources();
            Map<String, String> out = new HashMap<>();
            for (Field f : R.string.class.getFields()) {
                int id = f.getInt(null);
                String text, original;
                try {
                    text = shown.getString(id).trim();
                    original = vi.getString(id).trim();
                } catch (Resources.NotFoundException e) {
                    continue;
                }
                if (!text.isEmpty() && !text.equals(original)) out.putIfAbsent(text, original);
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "strings not mapped to Vietnamese", e);
            return Collections.emptyMap();
        }
    }
}
