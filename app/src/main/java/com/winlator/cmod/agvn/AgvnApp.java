/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Application;

/** Application entry point: installs the crash recorder before any activity runs. */
public class AgvnApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashRecorder.install(this);
    }
}
