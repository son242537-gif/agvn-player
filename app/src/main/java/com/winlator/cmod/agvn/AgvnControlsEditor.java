/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.graphics.Rect;
import android.view.View;

import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.inputcontrols.Binding;
import com.winlator.cmod.inputcontrols.ControlElement;
import com.winlator.cmod.inputcontrols.ControlsProfile;
import com.winlator.cmod.widget.InputControlsView;

/**
 * Edits the on-screen controls on the game screen, GameHub style: the game stays visible and running, the player drags
 * controls to move them and uses a toolbar to add a button / arrow pad / WASD stick, change keys, resize or delete
 * the selected control. Edits go to the game's own copy of the profile ({@link AgvnControlsFork}) and are saved at
 * once; leaving without a change undoes the copy ({@link AgvnEditSession}). All calls on the UI thread.
 */
final class AgvnControlsEditor {
    static final float MIN_SCALE = 0.5f, MAX_SCALE = 2.5f, SCALE_STEP = 0.1f;
    private static final float BUTTON_SCALE = 0.7f, PAD_SCALE = 0.7f, STICK_SCALE = 0.9f, OPACITY = 0.6f;
    private static final Binding[] ARROWS = {Binding.KEY_UP, Binding.KEY_RIGHT, Binding.KEY_DOWN, Binding.KEY_LEFT};
    private static final Binding[] WASD = {Binding.KEY_W, Binding.KEY_D, Binding.KEY_S, Binding.KEY_A};

    private final XServerDisplayActivity activity;
    private final Runnable onFinished;
    private AgvnEditToolbar toolbar;
    /** Non-null while editing. */
    private AgvnEditSession session;
    /** Where the [⌨ ✎ 👁] bar sits (screen coordinates); controls are kept out of it. */
    private Rect barArea;

    AgvnControlsEditor(XServerDisplayActivity activity, Runnable onFinished) {
        this.activity = activity;
        this.onFinished = onFinished;
    }

    boolean isActive() {
        return session != null;
    }

    /** Scale after one [－]/[＋] step (direction -1 or +1), rounded to 0.1 and kept within 0.5..2.5. */
    static float nextScale(float scale, int direction) {
        float next = Math.round((scale + direction * SCALE_STEP) * 10f) / 10f;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, next));
    }

    /**
     * Enters edit mode on the game's own copy of {@code base}; false when nothing can be edited. {@code barArea} is the
     * top bar's place on screen, kept free of controls.
     */
    boolean start(ControlsProfile base, Rect barArea) {
        InputControlsView view = activity.getInputControlsView();
        if (session != null || view == null) return false;
        if (base == null) {
            AppUtils.showToast(activity, R.string.agvn_controls_none);
            return false;
        }
        view.releaseAll(); // a finger may hold a key: its UP would reach edit mode and never release it
        session = AgvnEditSession.begin(activity, base);
        if (session == null) {
            AppUtils.showToast(activity, R.string.agvn_edit_failed);
            return false;
        }
        ControlsProfile profile = session.profile;
        if (view.getProfile() != profile || view.getVisibility() != View.VISIBLE) activity.agvnShowControls(profile);
        view.setShowTouchscreenControls(true);
        view.setOverlayEditStyle(true);
        view.setEditMode(true);
        view.clearSelection();
        session.snapshot(view);
        this.barArea = barArea;
        toolbar().setVisible(true);
        AppUtils.showToast(activity, R.string.agvn_edit_hint);
        return true;
    }

    /** Leaves edit mode, saves (or undoes the copy when nothing changed), and gives the touches back to the game. */
    void finish() {
        if (session == null) return;
        AgvnEditSession ending = session;
        session = null;
        toolbar.setVisible(false);
        InputControlsView view = activity.getInputControlsView();
        view.setEditMode(false);
        view.setOverlayEditStyle(false);
        view.clearSelection();
        AgvnEditSession.keepClearOf(view, barArea);
        boolean saved = ending.end(activity, view);
        view.invalidate();
        activity.agvnRefreshControlsSidebar();
        onFinished.run();
        AppUtils.showToast(activity, saved ? R.string.agvn_edit_saved : R.string.agvn_edit_unchanged);
    }

    private void add(ControlElement.Type type, Binding[] bindings, ControlElement.Shape shape, float scale) {
        InputControlsView view = activity.getInputControlsView();
        if (!view.isLayoutReady()) {
            AppUtils.showToast(activity, R.string.agvn_edit_not_ready);
            return;
        }
        if (!view.addElementAt(view.getMaxWidth() / 2, view.getMaxHeight() / 2, type, bindings, "")) return;
        ControlElement element = view.getSelectedElement();
        element.setShape(shape);
        element.setScale(scale);
        element.setOpacity(OPACITY);
        saveAndRedraw(view);
        if (type == ControlElement.Type.BUTTON) AgvnBindingPicker.pick(activity, element, changed -> {
            if (changed) saveAndRedraw(view);
            else if (view.getSelectedElement() == element) view.removeElement(); // cancelled: no key, no button
        });
    }

    private ControlElement selected() {
        ControlElement element = activity.getInputControlsView().getSelectedElement();
        if (element == null) AppUtils.showToast(activity, R.string.agvn_edit_select_first);
        return element;
    }

    private void rebind(ControlElement element) {
        if (element != null) AgvnBindingPicker.pick(activity, element, changed -> {
            if (changed) saveAndRedraw(activity.getInputControlsView());
        });
    }

    private void resize(ControlElement element, int direction) {
        if (element == null) return;
        element.setScale(nextScale(element.getScale(), direction));
        saveAndRedraw(activity.getInputControlsView());
    }

    private static void saveAndRedraw(InputControlsView view) {
        ControlsProfile profile = view.getProfile();
        if (profile != null && view.isLayoutReady()) profile.save();
        view.invalidate();
    }

    private AgvnEditToolbar toolbar() {
        if (toolbar != null) return toolbar;
        toolbar = new AgvnEditToolbar(activity, R.string.agvn_edit_done, v -> finish())
                .tool(R.string.agvn_edit_add_button, 0, v -> add(ControlElement.Type.BUTTON, null, ControlElement.Shape.ROUND_RECT, BUTTON_SCALE))
                .tool(R.string.agvn_edit_add_dpad, 0, v -> add(ControlElement.Type.D_PAD, ARROWS, ControlElement.Shape.CIRCLE, PAD_SCALE))
                .tool(R.string.agvn_edit_add_stick, 0, v -> add(ControlElement.Type.STICK, WASD, ControlElement.Shape.CIRCLE, STICK_SCALE))
                .tool(R.string.agvn_edit_keys, 0, v -> rebind(selected()))
                .tool(R.string.agvn_edit_smaller, R.string.agvn_edit_smaller_desc, v -> resize(selected(), -1))
                .tool(R.string.agvn_edit_bigger, R.string.agvn_edit_bigger_desc, v -> resize(selected(), 1))
                .tool(R.string.agvn_edit_delete, 0, v -> {
                    if (selected() != null) activity.getInputControlsView().removeElement();
                });
        toolbar.tool(R.string.agvn_edit_move_bar, R.string.agvn_edit_move_bar_desc, v -> toolbar.flip());
        return toolbar;
    }
}
