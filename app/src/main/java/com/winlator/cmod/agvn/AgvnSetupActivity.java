/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.MainActivity;
import com.winlator.cmod.OnboardingActivity;
import com.winlator.cmod.R;
import com.winlator.cmod.core.WineInfo;

/**
 * First start for players: everything comes from the APK (no Wine download page, no component list, no runtime
 * choice). Shows install progress, creates the game environment, asks once for file access in plain words, and
 * shows a short guide while waiting. The component manager stays in Cài đặt → Nâng cao for experts.
 */
public class AgvnSetupActivity extends AppCompatActivity implements AgvnFirstRun.Listener, AgvnSetupActions {
    private static final int REQUEST_ALL_FILES = 931;
    private static final int REQUEST_STORAGE = 932;
    private static final int REQUEST_NOTIFICATIONS = 933;
    /** Same key OnboardingActivity uses for the runtime of the first environment. */
    private static final String PREF_INITIAL_WINE = "winz_initial_wine_version";
    private static final String STATE_SKIPPED = "access_skipped";
    private static final String STATE_ASKED = "access_asked";

    private final AgvnSetupState state = new AgvnSetupState();
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (savedInstanceState != null) {
            state.setAccessSkipped(savedInstanceState.getBoolean(STATE_SKIPPED));
            state.setAccessAsked(savedInstanceState.getBoolean(STATE_ASKED));
        }
        state.setAccessDone(AgvnFirstContainer.hasStorageAccess(this));
        AgvnSetupHost.attach(this, state, this);
        AgvnFirstRun.setListener(this);
        advance();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_SKIPPED, state.getAccessSkipped());
        outState.putBoolean(STATE_ASKED, state.getAccessAsked());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAccess();
    }

    @Override
    protected void onDestroy() {
        AgvnFirstRun.setListener(null);
        super.onDestroy();
    }

    /** Moves to the next unfinished step; safe to call any time on the main thread. */
    private void advance() {
        if (state.getCoreFailed() || state.getContainerFailed()) return;
        if (!state.getCoreDone()) {
            state.setCoreProgress(AgvnFirstRun.coreProgress());
            AgvnFirstRun.startCore(this);
            return;
        }
        if (!state.getContainerDone()) AgvnFirstRun.createFirstContainer(this);
    }

    @Override
    public void onCoreProgress(int progress) {
        state.setCoreProgress(progress);
    }

    @Override
    public void onCoreFinished(boolean ok) {
        state.setCoreDone(ok);
        state.setCoreFailed(!ok);
        advance();
    }

    @Override
    public void onContainerFinished(boolean ok) {
        state.setContainerDone(ok);
        state.setContainerFailed(!ok);
    }

    @Override
    public void onRetry() {
        // Check the installed files again too: an environment usually fails because the runtime is incomplete.
        state.setCoreDone(false);
        state.setCoreFailed(false);
        state.setContainerFailed(false);
        advance();
    }

    @Override
    public void onAllowAccess() {
        state.setAccessAsked(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())), REQUEST_ALL_FILES);
            } catch (Exception e) {
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), REQUEST_ALL_FILES);
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        }
    }

    @Override
    public void onAccessLater() {
        state.setAccessSkipped(true);
    }

    @Override
    public void onStartPlaying() {
        if (!state.getReady()) return;
        preferences.edit()
                .putBoolean(OnboardingActivity.PREF_ONBOARDING_COMPLETE, true)
                .putString(PREF_INITIAL_WINE, WineInfo.MAIN_WINE_VERSION.identifier())
                .apply();
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("selected_menu_item_id", R.id.main_menu_shortcuts);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ALL_FILES) refreshAccess();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_STORAGE) refreshAccess();
    }

    /** Re-reads the storage permission; once granted, asks for notifications (Android 13+) without waiting. */
    private void refreshAccess() {
        boolean had = state.getAccessDone();
        state.setAccessDone(AgvnFirstContainer.hasStorageAccess(this));
        if (!had && state.getAccessDone() && Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }
}
