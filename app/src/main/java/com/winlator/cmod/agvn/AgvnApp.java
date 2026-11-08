/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Application;

/** Application entry point: the app's language (AgvnLanguage), and the crash recorder before any activity runs. */
public class AgvnApp extends Application {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(AgvnLanguage.wrap(base)); // the app's language for its own texts too
    }

    @Override
    public void onCreate() {
        super.onCreate();
        CrashRecorder.install(this);
    }
}
