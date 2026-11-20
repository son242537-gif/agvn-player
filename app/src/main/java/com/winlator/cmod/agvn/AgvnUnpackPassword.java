/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.InputType;
import android.text.format.Formatter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.MainActivity;
import com.winlator.cmod.R;

/** What "Thêm từ tệp nén" says and asks: the password of a locked archive, and why an archive could not be unpacked. */
final class AgvnUnpackPassword {
    interface Typed {
        void onPassword(String password);
    }

    private AgvnUnpackPassword() {}

    /**
     * Asks for the password, with "Hiện mật khẩu" and "Dán" (players copy it from the game's page). {@code wrong}: the
     * password that just failed, shown again to be corrected; null at the first ask.
     */
    static void ask(MainActivity a, String archiveName, String wrong, Typed typed) {
        int pad = (int) (20 * a.getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        TextView text = new TextView(a);
        text.setText(wrong != null ? R.string.agvn_unpack_password_wrong : R.string.agvn_unpack_password_message);
        EditText input = new EditText(a);
        input.setSingleLine(true);
        input.setHint(R.string.agvn_unpack_password_hint);
        int hidden = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
        int shown = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        input.setInputType(wrong != null ? shown : hidden); // a wrong password is shown: the player sees what to fix
        if (wrong != null) input.setText(wrong);
        CheckBox show = new CheckBox(a);
        show.setText(R.string.agvn_unpack_password_show);
        show.setChecked(wrong != null);
        show.setOnCheckedChangeListener((v, on) -> {
            int at = input.getSelectionEnd();
            input.setInputType(on ? shown : hidden);
            input.setSelection(Math.max(0, Math.min(at, input.length())));
        });
        box.addView(text);
        box.addView(input);
        box.addView(show);
        AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle(a.getString(R.string.agvn_unpack_password_title, archiveName))
                .setView(box)
                .setPositiveButton(R.string.agvn_unpack_start, (d, w) -> typed.onPassword(input.getText().toString()))
                .setNeutralButton(R.string.agvn_unpack_password_paste, null)
                .setNegativeButton(R.string.agvn_cancel, null)
                .create();
        dialog.show();
        // set after show(): "Dán" must not close the dialog
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            String pasted = clipboard(a);
            if (pasted == null) return;
            input.setText(AgvnArchiveNames.trimPassword(pasted));
            input.setSelection(input.length());
        });
        input.requestFocus();
    }

    private static String clipboard(Context ctx) {
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = cm != null ? cm.getPrimaryClip() : null;
            if (clip == null || clip.getItemCount() == 0) return null;
            CharSequence text = clip.getItemAt(0).coerceToText(ctx);
            return text != null ? text.toString() : null;
        } catch (RuntimeException e) { // the clipboard is the phone's: some refuse it
            return null;
        }
    }

    /**
     * The notification for a player who is in another app when unpacking ends. {@code e}: null when it went well.
     * Null when there is nothing to tell (the player cancelled).
     */
    static String notice(Context ctx, String archiveName, AgvnUnpackException e) {
        if (e == null) return ctx.getString(R.string.agvn_unpack_notif_done, archiveName);
        switch (e.reason) {
            case CANCELLED:
                return null;
            case NEED_PASSWORD:
            case WRONG_PASSWORD:
                return ctx.getString(R.string.agvn_unpack_notif_password);
            default:
                return ctx.getString(R.string.agvn_unpack_notif_failed, archiveName) + ". " + message(ctx, e);
        }
    }

    /** Why the archive could not be unpacked and what to do, in the player's words. */
    static String message(Context ctx, AgvnUnpackException e) {
        switch (e.reason) {
            case MISSING_PART:
                return e.detail != null ? ctx.getString(R.string.agvn_unpack_err_missing_part, e.detail)
                        : ctx.getString(R.string.agvn_unpack_err_missing_any);
            case NO_SPACE:
                return ctx.getString(R.string.agvn_unpack_err_no_space, Formatter.formatShortFileSize(ctx, e.bytes));
            case UNSUPPORTED_METHOD:
                return ctx.getString(R.string.agvn_unpack_err_method);
            case NOT_ARCHIVE:
                return ctx.getString(R.string.agvn_unpack_err_not_archive);
            case CANNOT_WRITE:
                return ctx.getString(R.string.agvn_unpack_err_write, String.valueOf(e.detail));
            default:
                return e.detail != null ? ctx.getString(R.string.agvn_unpack_err_damaged_file, e.detail)
                        : ctx.getString(R.string.agvn_unpack_err_damaged);
        }
    }
}
