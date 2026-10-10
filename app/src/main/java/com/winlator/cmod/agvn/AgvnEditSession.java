/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.graphics.Rect;
import android.util.Log;

import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.inputcontrols.ControlElement;
import com.winlator.cmod.inputcontrols.ControlsProfile;
import com.winlator.cmod.inputcontrols.InputControlsManager;
import com.winlator.cmod.widget.InputControlsView;

import java.io.File;

/**
 * One visit of the in-game controls editor. [✎] gives the game its own copy of the shown profile and points the game at
 * it before anything is edited ({@link AgvnControlsFork#editable}); when the player leaves without changing a control,
 * all of that is put back (the copy removed or its earlier content restored, the shortcut's controls extras restored),
 * so a stray tap on [✎] does not cut the game off from AGVN layout updates. UI thread only.
 */
final class AgvnEditSession {
    private static final String TAG = "AGVN";
    private static final String[] EXTRAS = {AgvnLayouts.EXTRA_PROFILE, AgvnLayouts.EXTRA_AUTO, AgvnControlsFork.EXTRA_OWN};

    /** The profile being edited. */
    final ControlsProfile profile;
    private final int baseId;
    private final String[] extrasBefore;
    private final int ownIdBefore;
    /** The game's earlier copy, which a new copy is written over; null when there was none. */
    private final String ownJsonBefore;
    private String controlsBefore;

    private AgvnEditSession(ControlsProfile profile, int baseId, String[] extrasBefore, int ownIdBefore, String ownJsonBefore) {
        this.profile = profile;
        this.baseId = baseId;
        this.extrasBefore = extrasBefore;
        this.ownIdBefore = ownIdBefore;
        this.ownJsonBefore = ownJsonBefore;
    }

    /** Makes {@code base} editable for this game; null when no copy could be made. */
    static AgvnEditSession begin(XServerDisplayActivity activity, ControlsProfile base) {
        if (base == null) return null;
        Shortcut shortcut = activity.agvnShortcut();
        String[] extras = new String[EXTRAS.length];
        for (int i = 0; i < EXTRAS.length; i++) extras[i] = shortcut != null ? shortcut.getExtra(EXTRAS[i], null) : null;
        int ownId = AgvnControlsFork.parseId(extras[2]);
        File ownFile = ownId > 0 ? ControlsProfile.getProfileFile(activity, ownId) : null;
        String ownJson = ownFile != null && ownFile.isFile() ? FileUtils.readString(ownFile) : null;
        ControlsProfile profile = AgvnControlsFork.editable(activity, base);
        return profile != null ? new AgvnEditSession(profile, base.id, extras, ownId, ownJson) : null;
    }

    /** Remembers the controls before editing; call once the profile is on screen. */
    void snapshot(InputControlsView view) {
        controlsBefore = controls(view, profile);
    }

    /** Saves the edits and returns true; or, when no control changed, undoes what [✎] did and returns false. */
    boolean end(XServerDisplayActivity activity, InputControlsView view) {
        ControlsProfile shown = view.getProfile();
        if (shown == profile && controlsBefore != null && controlsBefore.equals(controls(view, profile))) {
            undo(activity);
            return false;
        }
        if (shown != null && view.isLayoutReady()) shown.save();
        return true;
    }

    private void undo(XServerDisplayActivity activity) {
        Shortcut shortcut = activity.agvnShortcut();
        if (shortcut != null) {
            for (int i = 0; i < EXTRAS.length; i++) shortcut.putExtra(EXTRAS[i], extrasBefore[i]);
            shortcut.saveData();
        }
        if (profile.id == baseId) return; // the game's own copy was opened: nothing was copied
        File file = ControlsProfile.getProfileFile(activity, profile.id);
        boolean undone = profile.id == ownIdBefore && ownJsonBefore != null ? FileUtils.writeString(file, ownJsonBefore) : file.delete();
        if (!undone) Log.w(TAG, "cannot undo controls copy " + file.getName());
        InputControlsManager manager = activity.agvnControlsManager();
        if (manager == null) return;
        manager.loadProfiles(false);
        ControlsProfile base = manager.getProfile(baseId);
        if (base != null) activity.agvnShowControls(base);
    }

    /** The controls of {@code p} as they would be saved, or null before the game screen is laid out. */
    private static String controls(InputControlsView view, ControlsProfile p) {
        if (!view.isLayoutReady()) return null;
        if (!p.isElementsLoaded()) p.loadElements(view);
        StringBuilder text = new StringBuilder();
        for (ControlElement element : p.getElements()) text.append(element.toJSONObject()).append('\n');
        return text.toString();
    }

    /**
     * Moves the controls that the [⌨ ✎ 👁] bar would cover once it shows again ({@code barOnScreen}, screen coordinates)
     * to just below it, where they can still be pressed. Returns true when a control moved.
     */
    static boolean keepClearOf(InputControlsView view, Rect barOnScreen) {
        ControlsProfile p = view.getProfile();
        int snap = view.getSnappingSize();
        if (p == null || barOnScreen == null || barOnScreen.isEmpty() || snap <= 0) return false;
        int[] at = new int[2];
        view.getLocationOnScreen(at);
        Rect bar = new Rect(barOnScreen);
        bar.offset(-at[0], -at[1]);
        bar.inset(-snap, -snap); // a little room: the eye's label changes the bar's width
        boolean moved = false;
        for (ControlElement element : p.getElements()) {
            Rect box = element.getBoundingBox();
            if (!Rect.intersects(box, bar)) continue;
            int y = bar.bottom + box.height() / 2;
            element.setY((y + snap - 1) / snap * snap);
            if (element.getType() == ControlElement.Type.STICK) element.setCurrentPosition(element.getX(), element.getY());
            moved = true;
        }
        return moved;
    }
}
