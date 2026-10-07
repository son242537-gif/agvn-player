/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.view.View;

import com.winlator.cmod.R;
import com.winlator.cmod.core.AppUtils;

/**
 * Edits the keys of a "Chạy nhẹ" game on its screen, with the toolbar of the Windows games' editor
 * ({@link AgvnControlsEditor}): a drag moves a key; [+ Nút] adds a key picked on a drawn keyboard, [+ Mũi tên] an
 * arrow pad; [Phím…] gives the selected key another key or text (arrows or W A S D for a pad); [－] [＋] resize it and
 * [Xóa] deletes it; [Mặc định] brings back AGVN's keys of this game type. [Xong] (or Back) keeps the keys as this
 * game's own key set, as a Windows game keeps its own. Only keys the runner's game gets are offered
 * ({@link AgvnLightActions#sendable}). All calls on the UI thread.
 */
final class AgvnLightEditor {
    private final AgvnLightTools tools;
    private AgvnEditToolbar toolbar;
    private boolean active;

    AgvnLightEditor(AgvnLightTools tools) {
        this.tools = tools;
    }

    boolean active() {
        return active;
    }

    /** The keys show fully, the game gets no touch; a tap selects a key, a drag moves it. */
    void start() {
        if (active) return;
        active = true;
        tools.keys.setVisibility(View.VISIBLE);
        tools.keys.setAlpha(1f);
        tools.keys.setEditing(true);
        toolbar().setVisible(true);
        AppUtils.showToast(tools.activity, R.string.agvn_edit_hint);
    }

    /** "Xong": a changed key set becomes this game's own. */
    void finish() {
        if (!active) return;
        active = false;
        toolbar.setVisible(false);
        boolean changed = tools.keys.changed();
        if (changed) tools.prefs.setLayout(tools.game, tools.layout.toJson(), tools.pick.id);
        tools.keys.setEditing(false);
        tools.editEnded();
        AppUtils.showToast(tools.activity, changed ? R.string.agvn_edit_saved : R.string.agvn_edit_unchanged);
    }

    private void addKey() {
        AgvnLightKeyPicker.key(tools.activity, tools.host.runner(), null,
                (binding, face) -> add(AgvnLightLayout.Element.button(face, binding)));
    }

    private void add(AgvnLightLayout.Element key) {
        tools.layout.elements.add(key);
        tools.keys.select(tools.layout.elements.size() - 1); // the tools act on it at once
        tools.keys.markChanged();
    }

    /** [Phím…]: another key for a button (or its text), arrows or W A S D for a pad. */
    private void rekey() {
        AgvnLightLayout.Element key = selected();
        if (key == null) return;
        if (key.pad) {
            AgvnLightKeyPicker.directions(tools.activity, set -> {
                key.bindings = set;
                tools.keys.markChanged();
            });
            return;
        }
        Runnable rename = () -> AgvnLightKeyPicker.label(tools.activity, key.text, text -> {
            key.text = text;
            tools.keys.markChanged();
        });
        AgvnLightKeyPicker.key(tools.activity, tools.host.runner(), rename, (binding, face) -> {
            key.bindings = new String[]{binding};
            key.text = face;
            tools.keys.markChanged();
        });
    }

    private void resize(int direction) {
        AgvnLightLayout.Element key = selected();
        if (key == null) return;
        key.scale = AgvnControlsEditor.nextScale(key.scale, direction);
        tools.keys.markChanged();
    }

    private void delete() {
        if (selected() == null) return;
        tools.layout.elements.remove(tools.keys.selected());
        tools.keys.select(-1);
        tools.keys.markChanged();
    }

    private void reset() {
        AgvnLightKeyPicker.confirmReset(tools.activity, () -> {
            tools.layout.reset();
            tools.keys.select(-1);
            tools.keys.markChanged();
        });
    }

    /** The selected key, or null with a hint to tap one first. */
    private AgvnLightLayout.Element selected() {
        int i = tools.keys.selected();
        if (i < 0) AppUtils.showToast(tools.activity, R.string.agvn_edit_select_first);
        return i >= 0 ? tools.layout.elements.get(i) : null;
    }

    private AgvnEditToolbar toolbar() {
        if (toolbar != null) return toolbar;
        toolbar = new AgvnEditToolbar(tools.activity, R.string.agvn_edit_done, v -> finish())
                .tool(R.string.agvn_edit_add_button, 0, v -> addKey())
                .tool(R.string.agvn_edit_add_dpad, 0, v -> add(AgvnLightLayout.Element.pad(AgvnLightKeyPicker.ARROWS)))
                .tool(R.string.agvn_edit_keys, 0, v -> rekey())
                .tool(R.string.agvn_edit_smaller, R.string.agvn_edit_smaller_desc, v -> resize(-1))
                .tool(R.string.agvn_edit_bigger, R.string.agvn_edit_bigger_desc, v -> resize(1))
                .tool(R.string.agvn_edit_delete, 0, v -> delete())
                .tool(R.string.agvn_light_reset, R.string.agvn_light_reset_desc, v -> reset());
        toolbar.tool(R.string.agvn_edit_move_bar, R.string.agvn_edit_move_bar_desc, v -> toolbar.flip());
        return toolbar;
    }
}
