/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.winlator.cmod.MainActivity;
import com.winlator.cmod.R;

/** AGVN logo for 1.2 s (tap to skip), then the normal start flow in MainActivity. */
public class SplashActivity extends Activity {
    private static final long DURATION_MS = 1200;
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
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.agvn_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setAdjustViewBounds(true);
        int size = Math.min(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
        root.addView(logo, new FrameLayout.LayoutParams(size, size, android.view.Gravity.CENTER));
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
