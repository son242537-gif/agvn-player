/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.winlator.cmod.core.FileUtils;

/**
 * "Giấy phép mã nguồn mở": the app's GNU GPL 3 notice, upstream MIT notice and third-party list from
 * assets/agvn/licenses.html, then the full GPL text (assets/agvn/gpl-3.0.txt), which GPL requires to be given along,
 * then Ren'Py's LICENSE.txt with the libraries in librenpython.so (assets/agvn/renpy-license.txt, app/agvn-renpy.gradle),
 * then mkxp-z and the libraries in libmkxp-z.so (assets/agvn/mkxp-z-license.txt, scripts/agvn/mkxp-z/licenses.sh).
 */
public class LicensesActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        TextView text = new TextView(this);
        text.setPadding(pad, pad, pad, pad);
        text.setTextColor(Color.parseColor("#E6E6EA"));
        text.setTextIsSelectable(true);
        String html = FileUtils.readString(this, "agvn/licenses.html");
        String gpl = FileUtils.readString(this, "agvn/gpl-3.0.txt");
        String renpy = FileUtils.readString(this, "agvn/renpy-license.txt");
        String mkxpz = FileUtils.readString(this, "agvn/mkxp-z-license.txt");
        text.setText(TextUtils.concat(Html.fromHtml(html != null ? html : "", Html.FROM_HTML_MODE_LEGACY), "\n",
                gpl != null ? gpl : "", "\n\n", renpy != null ? renpy : "", "\n\n", mkxpz != null ? mkxpz : ""));
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#15171C"));
        scroll.addView(text);
        setContentView(scroll);
    }
}
