/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Html;
import android.util.TypedValue;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.winlator.cmod.core.FileUtils;

/** "Giấy phép mã nguồn mở": upstream MIT notice and third-party list from assets/agvn/licenses.html. */
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
        text.setText(Html.fromHtml(html != null ? html : "", Html.FROM_HTML_MODE_LEGACY));
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#15171C"));
        scroll.addView(text);
        setContentView(scroll);
    }
}
