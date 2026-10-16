/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.winlator.cmod.R;

/**
 * Where Android tells how an update session went ({@link AgvnSessionInstall}). It shows nothing of its own: it opens
 * Android's confirmation when Android asks for the player's yes, says why when Android refused, and closes.
 */
public final class AgvnUpdateStatusActivity extends AppCompatActivity {
    private static final String TAG = "AGVN";

    /** Where Android sends the status of session {@code sessionId}. */
    static IntentSender sender(Context context, int sessionId) {
        Intent intent = new Intent(context, AgvnUpdateStatusActivity.class);
        // Android adds the status to the intent, so it stays mutable
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        return PendingIntent.getActivity(context, sessionId, intent, flags).getIntentSender();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handle(intent);
    }

    private void handle(Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            try {
                if (confirm != null) startActivity(confirm);
            } catch (ActivityNotFoundException | SecurityException e) {
                Log.w(TAG, "update confirmation not opened", e);
            }
            finish();
            return;
        }
        if (status == PackageInstaller.STATUS_SUCCESS || status == PackageInstaller.STATUS_FAILURE_ABORTED) {
            finish(); // installed, or the player said no
            return;
        }
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        Log.w(TAG, "update session failed: " + status + " " + message);
        AgvnUpdateRetry.sessionFailed(this);
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.agvn_update_failed, AgvnSessionInstall.reason(status, message)))
                .setPositiveButton(android.R.string.ok, null)
                .setOnDismissListener(d -> finish())
                .show();
    }
}
