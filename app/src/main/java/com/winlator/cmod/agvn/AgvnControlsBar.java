/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.drawerlayout.widget.DrawerLayout;

import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.inputcontrols.ControlsProfile;
import com.winlator.cmod.widget.InputControlsView;

/**
 * Small bar at the top centre of the game screen: [⌨] opens the Android keyboard, [✎ Sửa] edits the on-screen controls
 * right on the game ({@link AgvnControlsEditor}), [👁 Ẩn / 👁 Hiện] hides or shows them for this game (remembered in the
 * shortcut, extra agvnControlsHidden). Hidden controls are really gone (View.GONE), so touches reach the game.
 * Long-press a button for its full name. The bar tucks itself away after 3 s without a tap, leaving a thin line at the
 * top edge that brings it back ({@link AgvnBarAutoHide}); it hides while the sidebar drawer is open.
 */
public final class AgvnControlsBar {
    private static final int BG_NORMAL = AgvnBarButton.BG_NORMAL, BG_HIDDEN = AgvnBarButton.BG_HIDDEN;

    private final XServerDisplayActivity activity;
    private final LinearLayout bar;
    private final TextView eye;
    private final AgvnControlsEditor editor;
    private final AgvnBarAutoHide autoHide;
    /** Profile that was on screen before the player hid it (-1: none), shown again by [👁]. */
    private int hiddenProfileId = -1;
    /** The sidebar drawer started to open and hid the bar. */
    private boolean drawerHid;

    private AgvnControlsBar(XServerDisplayActivity activity) {
        this.activity = activity;
        float dp = activity.getResources().getDisplayMetrics().density;
        bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(barButton("⌨", 18, R.string.agvn_bar_keyboard, v -> AppUtils.showKeyboard(activity)));
        bar.addView(barButton(activity.getString(R.string.agvn_bar_edit_label), 14, R.string.agvn_bar_edit, v -> edit()));
        eye = barButton(activity.getString(R.string.agvn_bar_hide_label), 14, R.string.agvn_bar_toggle, v -> toggle());
        bar.addView(eye);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = (int) (6 * dp);
        activity.addContentView(bar, lp);
        autoHide = new AgvnBarAutoHide(activity, bar);
        editor = new AgvnControlsEditor(activity, () -> {
            autoHide.reveal();
            updateEye();
        });
        watchDrawer();
        autoHide.reveal(); // up for the first seconds of the game, then tucked away
    }

    /** Called once after the game screen and its sidebar are set up; applies the game's saved hidden state. */
    public static void attach(XServerDisplayActivity activity) {
        AgvnControlsBar controlsBar = new AgvnControlsBar(activity);
        controlsBar.applySavedState();
        // The sidebar's profile spinner re-applies (re-shows) its profile once, from a message posted before this one;
        // the later check covers a slower first layout. Both are no-ops once the player shows or edits the controls.
        controlsBar.bar.post(controlsBar::applySavedState);
        controlsBar.bar.postDelayed(controlsBar::applySavedState, 1000);
    }

    private void applySavedState() {
        if (AgvnControlsFork.isHidden(activity.agvnShortcut()) && isShown()) hide();
        updateEye();
    }

    private boolean isShown() {
        InputControlsView view = activity.getInputControlsView();
        return view != null && view.getVisibility() == View.VISIBLE && view.getProfile() != null;
    }

    private void hide() {
        InputControlsView view = activity.getInputControlsView();
        hiddenProfileId = view.getProfile().id;
        view.releaseAll();
        activity.agvnHideControls();
    }

    private void toggle() {
        InputControlsView view = activity.getInputControlsView();
        if (view == null || editor.isActive()) return;
        if (isShown()) {
            hide();
            AgvnControlsFork.setHidden(activity.agvnShortcut(), true);
            AppUtils.showToast(activity, R.string.agvn_controls_hidden);
        } else {
            ControlsProfile profile = profileToShow();
            if (profile == null) {
                AppUtils.showToast(activity, R.string.agvn_controls_none);
                return;
            }
            activity.agvnShowControls(profile);
            view.setShowTouchscreenControls(true);
            hiddenProfileId = -1;
            AgvnControlsFork.rememberShown(activity.agvnShortcut(), profile);
            activity.agvnRefreshControlsSidebar();
            AppUtils.showToast(activity, R.string.agvn_controls_shown);
        }
        updateEye();
    }

    private void edit() {
        InputControlsView view = activity.getInputControlsView();
        if (view == null || editor.isActive()) return;
        ControlsProfile base = view.getProfile() != null ? view.getProfile() : profileToShow();
        int[] at = new int[2];
        bar.getLocationOnScreen(at); // the editor keeps controls out of the bar, which it hides meanwhile
        if (!editor.start(base, new Rect(at[0], at[1], at[0] + bar.getWidth(), at[1] + bar.getHeight()))) return;
        hiddenProfileId = -1;
        AgvnControlsFork.setHidden(activity.agvnShortcut(), false);
        autoHide.suspend(); // the editor has its own toolbar
    }

    /** The profile hidden by [👁], else the game's profile, else its AGVN layout. */
    private ControlsProfile profileToShow() {
        ControlsProfile profile = null;
        if (hiddenProfileId > 0 && activity.agvnControlsManager() != null)
            profile = activity.agvnControlsManager().getProfile(hiddenProfileId);
        return profile != null ? profile : AgvnControlsFork.gameProfile(activity);
    }

    private void updateEye() {
        boolean shown = isShown();
        ((GradientDrawable) eye.getBackground()).setColor(shown ? BG_NORMAL : BG_HIDDEN);
        eye.setText(shown ? R.string.agvn_bar_hide_label : R.string.agvn_bar_show_label);
        eye.setContentDescription(activity.getString(shown ? R.string.agvn_bar_hide : R.string.agvn_bar_show));
    }

    private void watchDrawer() {
        View root = activity.findViewById(R.id.DrawerLayout);
        if (!(root instanceof DrawerLayout)) return;
        ((DrawerLayout) root).addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                if (slideOffset <= 0f) { // back to closed; a drawer that never fully opened gets no onDrawerClosed
                    if (drawerHid) autoHide.reveal();
                    drawerHid = false;
                    return;
                }
                if (editor.isActive()) editor.finish(); // opening the menu ends editing (saved)
                autoHide.suspend();
                drawerHid = true;
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                drawerHid = false;
                autoHide.reveal();
                if (isShown()) { // the sidebar showed the controls again: do not hide them at the next launch
                    hiddenProfileId = -1;
                    AgvnControlsFork.setHidden(activity.agvnShortcut(), false);
                }
                updateEye(); // the sidebar may have changed the controls
            }
        });
    }

    private TextView barButton(String text, int sp, int nameRes, View.OnClickListener onClick) {
        return AgvnBarButton.make(activity, text, sp, nameRes, onClick);
    }
}
