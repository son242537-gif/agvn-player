/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.LocaleList;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.R;

import java.util.Locale;

/**
 * The app's language, picked in Cài đặt ("Ngôn ngữ · Language"): Vietnamese, English, Thai, Indonesian, Filipino,
 * Simplified Chinese, or the phone's own. Vietnamese until the player picks another, whatever the phone's language:
 * many Vietnamese players keep their phone in English, and the app is made for them first. Each activity takes it in
 * attachBaseContext ({@link #wrap}), so it stays through a turn of the screen, and so does the app's own context
 * (AgvnApp); a new pick restarts the screen it was made on. A language the app has no text for shows Vietnamese.
 */
public final class AgvnLanguage {
    static final String PREF = "agvn_language", SYSTEM = "system", VIETNAMESE = "vi";
    /** The choices: Android's code (values-&lt;code&gt;), and the name in that language. */
    static final String[][] CHOICES = {{"vi", "Tiếng Việt"}, {"en", "English"}, {"th", "ไทย"},
            {"in", "Bahasa Indonesia"}, {"tl", "Filipino"}, {"zh-CN", "简体中文"}};

    private AgvnLanguage() {}

    /** The code picked: one of {@link #CHOICES}, or {@link #SYSTEM}; Vietnamese when none was. */
    static String picked(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getString(PREF, VIETNAMESE);
    }

    /** True once the player picked a language: the first start asks (AgvnLanguageActivity). */
    public static boolean chosen(Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx).contains(PREF);
    }

    /**
     * Keeps {@code code} as the app's language, for the screens opened from now on and for the app's own texts (its
     * context was wrapped as the app started, AgvnApp); the screen showing gets it once it is made again.
     */
    static void choose(Context ctx, String code) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().putString(PREF, code).commit();
        AgvnDoctor.forgetCatalog(); // its texts are the old language's
        try {
            android.content.res.Resources res = ctx.getApplicationContext().getResources();
            Configuration config = new Configuration(res.getConfiguration());
            Locale locale = locale(code, Locale.getDefault());
            config.setLocale(locale);
            config.setLocales(new LocaleList(locale));
            res.updateConfiguration(config, res.getDisplayMetrics());
        } catch (RuntimeException e) {
            // the app's own texts follow at its next start
        }
    }

    /** The locale for {@code code}; for {@link #SYSTEM}, the phone's, under the codes the app's folders use. */
    static Locale locale(String code, Locale phone) {
        if (SYSTEM.equals(code)) {
            String lang = phone.getLanguage();
            if (lang.equals("fil")) return new Locale("tl", phone.getCountry());
            if (lang.equals("id")) return new Locale("in", phone.getCountry());
            return phone;
        }
        if ("zh-CN".equals(code)) return Locale.SIMPLIFIED_CHINESE;
        return new Locale(code);
    }

    /** "en", "th", "in", "tl", "zh" or "vi": the language whose texts show (game-problems-&lt;it&gt;.json). */
    static String textLanguage(Context ctx) {
        String lang = ctx.getResources().getConfiguration().getLocales().get(0).getLanguage();
        if (lang.equals("fil")) return "tl";
        if (lang.equals("id")) return "in";
        for (String[] c : CHOICES) if (c[0].startsWith(lang)) return lang;
        return VIETNAMESE;
    }

    /** {@code base} showing the language picked: for attachBaseContext of every activity and of the app. */
    public static Context wrap(Context base) {
        try {
            Locale locale = locale(picked(base), Locale.getDefault());
            Configuration config = new Configuration(base.getResources().getConfiguration());
            config.setLocale(locale);
            config.setLocales(new LocaleList(locale));
            return base.createConfigurationContext(config);
        } catch (RuntimeException e) {
            return base; // the phone's language, rather than no screen
        }
    }

    /** The picked language's name, for Cài đặt. */
    public static String summary(Context ctx) {
        String code = picked(ctx);
        for (String[] c : CHOICES) if (c[0].equals(code)) return c[1];
        return ctx.getString(R.string.agvn_language_system);
    }

    /** Cài đặt's choice: the languages, then the phone's; a new pick restarts {@code a} in it. */
    public static void showPicker(Activity a) {
        String[] names = new String[CHOICES.length + 1];
        for (int i = 0; i < CHOICES.length; i++) names[i] = CHOICES[i][1];
        names[CHOICES.length] = a.getString(R.string.agvn_language_system);
        String now = picked(a);
        int checked = CHOICES.length;
        for (int i = 0; i < CHOICES.length; i++) if (CHOICES[i][0].equals(now)) checked = i;
        new AlertDialog.Builder(a)
                .setTitle(R.string.agvn_language_title)
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    dialog.dismiss();
                    String code = which < CHOICES.length ? CHOICES[which][0] : SYSTEM;
                    if (code.equals(now)) return;
                    choose(a, code);
                    a.recreate();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
