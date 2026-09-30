/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.winlator.cmod.MainActivity;

/** "agvn.io" for 1.2 s (tap to skip), then the normal start flow in MainActivity. */
public class SplashActivity extends Activity {
    private static final long DURATION_MS = 1200;
    private static final String TEXT = "agvn.io";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean launched;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isTaskRoot() && getIntent() != null && Intent.ACTION_MAIN.equals(getIntent().getAction())) {
            finish();
            return;
        }
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#15171C"));
        TextView name = new TextView(this);
        name.setText(TEXT);
        name.setTextColor(Color.parseColor("#FFD97A")); // the yellow of the AGVN logo
        name.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        int shortSide = Math.min(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
        name.setTextSize(TypedValue.COMPLEX_UNIT_PX, shortSide * 0.14f);
        name.setSingleLine(true);
        root.addView(name, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.setOnClickListener(v -> openMain());
        setContentView(root);
        handler.postDelayed(this::openMain, DURATION_MS);
    }

    private void openMain() {
        if (launched || isFinishing()) return;
        launched = true;
        handler.removeCallbacksAndMessages(null);
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
