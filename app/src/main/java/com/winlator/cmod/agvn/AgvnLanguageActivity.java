/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.winlator.cmod.MainActivity;

import java.util.Locale;

/**
 * The first start's choice of language, before anything else (SplashActivity opens it until one is picked): each
 * language under its own flag and name, and the phone's own. The pick goes on in Cài đặt → "Ngôn ngữ · Language", and
 * as the flag at the top left of the main screen (AgvnLanguageFlag). Back leaves the app; it asks again next time.
 */
public class AgvnLanguageActivity extends Activity {
    /** The title, in every language the app has: nobody can read the screen's language yet. */
    static final String TITLE = "Ngôn ngữ · Language · ภาษา · Bahasa · Wika · 语言";

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(AgvnLanguage.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(24);
        list.setPadding(pad, pad, pad, pad);
        TextView title = text("🌐", 44);
        list.addView(title);
        TextView names = text(TITLE, 17);
        names.setTextColor(Color.parseColor("#FFD97A")); // the yellow of the AGVN logo
        names.setPadding(0, dp(8), 0, dp(20));
        list.addView(names);
        for (String[] choice : AgvnLanguage.CHOICES) {
            String lang = choice[0].startsWith("zh") ? "zh" : choice[0];
            list.addView(button(AgvnLanguageFlag.emojiOf(lang) + "   " + choice[1], choice[0]));
        }
        Locale phone = Locale.getDefault();
        String own = phone.getDisplayLanguage(phone);
        if (!own.isEmpty()) own = own.substring(0, 1).toUpperCase(phone) + own.substring(1);
        list.addView(button("📱   " + own, AgvnLanguage.SYSTEM));
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#15171C"));
        scroll.setFillViewport(true);
        scroll.addView(list);
        setContentView(scroll);
    }

    private TextView button(String label, String code) {
        TextView b = text(label, 20);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(20), dp(14), dp(20), dp(14));
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(14));
        shape.setColor(Color.parseColor("#232630"));
        b.setBackground(shape);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.min(dp(420),
                getResources().getDisplayMetrics().widthPixels - dp(48)), LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> {
            AgvnLanguage.choose(this, code);
            startActivity(new Intent(this, MainActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });
        return b;
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.parseColor("#E6E6EA"));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
