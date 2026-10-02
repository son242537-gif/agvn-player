/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.winlator.cmod.R;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.FileUtils;

import java.io.File;

/**
 * The toolkit of a "Chạy nhẹ" game, as Windows games have it: the on-screen keys of the game type
 * ({@link AgvnLightKeys}), the ⌨ ✎ 👁 ☰ bar at the top ({@link AgvnLightBar}), the menu Back opens
 * ({@link AgvnLightMenu}), moving and resizing the keys ({@link AgvnLightEditBar}) and the HUD ({@link AgvnLightHud}).
 * The runner (Ren'Py, RPG Maker XP/VX/VX Ace, HTML) is the {@link Host}. All calls on the UI thread.
 */
public final class AgvnLightTools {
    /** What the toolkit asks of the game's runner. */
    interface Host {
        /** An on-screen key's binding (KEY_..., MOUSE_..., as in the .icp) pressed or released. */
        void binding(String binding, boolean down);

        void openGameMenu();

        void showKeyboard();

        void quit();

        /** "Chạy bằng Windows", or null for a game without a Windows version. */
        Runnable windows();

        /** Frames per second the game draws now; -1 when it cannot be measured. */
        int fps();

        /** The game's own memory in MB; -1 when it cannot be measured (an HTML game runs in WebView's process). */
        long gameMb();
    }

    private static final float RESIZE_STEP = 0.1f;

    final Activity activity;
    final Host host;
    private final String gameName;
    private final File gameDir;
    private final String game;
    private final AgvnLightPrefs prefs;
    private final AgvnLightLayout layout;
    private final AgvnLightKeys keys;
    private final AgvnLightHud hud;
    private final AgvnLightBar bar;
    private final AgvnLightMenu menu;
    private AgvnLightEditBar editBar;

    private AgvnLightTools(Activity activity, String kind, String gameName, File gameDir, Host host) {
        this.activity = activity;
        this.host = host;
        this.gameName = gameName != null ? gameName : "";
        this.gameDir = gameDir;
        game = gameDir != null ? gameDir.getAbsolutePath() : this.gameName;
        prefs = new AgvnLightPrefs(activity.getFilesDir());
        String json = FileUtils.readString(activity, AgvnLayouts.assetFor(kind));
        layout = AgvnLightLayout.parse(kind, json != null ? json : "{}");
        layout.apply(prefs.positions(kind));
        keys = new AgvnLightKeys(activity, layout, host::binding);
        keys.setAlpha(prefs.opacity());
        keys.setVisibility(prefs.keysHidden(game) ? View.GONE : View.VISIBLE);
        activity.addContentView(keys, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        hud = new AgvnLightHud(activity, host);
        bar = new AgvnLightBar(this);
        menu = new AgvnLightMenu(this, this.gameName);
        if (prefs.hud()) hud.start();
        bar.update(keysShown());
    }

    /** Puts the toolkit on the game screen: {@code kind} is the game type of {@link AgvnLayouts} (VN or RPG). */
    static AgvnLightTools attach(Activity activity, String kind, String gameName, File gameDir, Host host) {
        return new AgvnLightTools(activity, kind, gameName, gameDir, host);
    }

    /** Back: ends editing, else closes the menu, else opens it. Always handled. */
    boolean onBack() {
        if (editBar != null) finishEdit();
        else if (menu.isOpen()) menu.close();
        else openMenu();
        return true;
    }

    /** The game goes to the background: no key stays held, the HUD stops. */
    void onPause() {
        keys.releaseAll();
        hud.stop();
    }

    void onResume() {
        if (prefs.hud()) hud.start();
    }

    void openMenu() {
        if (editBar != null) finishEdit();
        bar.suspend();
        menu.open(keysShown(), prefs.opacity(), prefs.hud());
    }

    /** The menu closed (by a choice, Back or a tap beside it). */
    void menuClosed() {
        bar.reveal();
    }

    boolean keysShown() {
        return keys.getVisibility() == View.VISIBLE;
    }

    void setKeysShown(boolean shown) {
        if (!shown) keys.releaseAll();
        keys.setVisibility(shown ? View.VISIBLE : View.GONE);
        prefs.setKeysHidden(game, !shown);
        bar.update(shown);
    }

    void toggleKeys() {
        setKeysShown(!keysShown());
        AppUtils.showToast(activity, keysShown() ? R.string.agvn_controls_shown : R.string.agvn_controls_hidden);
    }

    void setOpacity(float opacity) {
        prefs.setOpacity(opacity);
        keys.setAlpha(prefs.opacity());
    }

    void setHud(boolean on) {
        prefs.setHud(on);
        if (on) hud.start();
        else hud.stop();
    }

    void sendLogs() {
        AgvnLogShare.share(activity, gameName, gameDir);
    }

    /** Edit mode: the keys show fully, a tap selects one, a drag moves it; the edit bar resizes and saves. */
    void edit() {
        if (editBar != null) return;
        if (menu.isOpen()) menu.close();
        bar.suspend();
        keys.setVisibility(View.VISIBLE);
        keys.setAlpha(1f);
        keys.setEditing(true);
        editBar = new AgvnLightEditBar(this);
        AppUtils.showToast(activity, R.string.agvn_light_edit_hint);
    }

    void resizeSelected(boolean bigger) {
        if (!keys.resizeSelected(bigger ? RESIZE_STEP : -RESIZE_STEP)) AppUtils.showToast(activity, R.string.agvn_edit_select_first);
    }

    void resetKeys() {
        keys.resetAll();
    }

    /** "Xong": keeps the places and sizes for every game of this type. */
    void finishEdit() {
        if (editBar == null) return;
        editBar.remove();
        editBar = null;
        boolean changed = keys.changed();
        keys.setEditing(false);
        keys.setAlpha(prefs.opacity());
        if (changed) prefs.setPositions(layout.kind, layout.positions());
        prefs.setKeysHidden(game, false);
        AppUtils.showToast(activity, changed ? R.string.agvn_light_saved : R.string.agvn_edit_unchanged);
        bar.update(true);
        bar.reveal();
    }
}
