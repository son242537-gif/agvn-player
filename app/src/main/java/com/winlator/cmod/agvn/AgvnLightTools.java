/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
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
 * ({@link AgvnLightMenu}), moving, changing, adding and deleting keys ({@link AgvnLightEditor}) and the HUD
 * ({@link AgvnLightHud}). Each game can have a key set of its own ({@link AgvnLightPrefs#layout}); a ✎ stays in the
 * top-left corner to edit it ({@link AgvnEditPen}).
 * The runner (Ren'Py, RPG Maker XP/VX/VX Ace, Godot, HTML) is the {@link Host}. All calls on the UI thread.
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

        /** The runner: AgvnHtmlGame.RUNNER_RENPY, RUNNER_RGSS, RUNNER_GODOT or RUNNER_HTML. */
        String runner();

        /** The frames per second the game is made for; -1 when it has none (Ren'Py draws only when something moves). */
        int targetFps();
    }

    final Activity activity;
    final Host host;
    private final String gameName;
    private final File gameDir;
    /** The game's folder (or name): its own key set and hidden keys are kept under it. */
    final String game;
    final AgvnLightPrefs prefs;
    /** The key profile picked for the game in its settings, which its keys start from. */
    final AgvnLightPick pick;
    final AgvnLightLayout layout;
    final AgvnLightKeys keys;
    private final AgvnLightHud hud;
    private final AgvnLightBar bar;
    private final AgvnLightMenu menu;
    private final AgvnLightSlow slow;
    private final AgvnLightEditor editor = new AgvnLightEditor(this);
    private final AgvnEditPen pen;

    private AgvnLightTools(Activity activity, String kind, String gameName, File gameDir, Host host) {
        this.activity = activity;
        this.host = host;
        this.gameName = gameName != null ? gameName : "";
        this.gameDir = gameDir;
        game = gameDir != null ? gameDir.getAbsolutePath() : this.gameName;
        prefs = new AgvnLightPrefs(activity.getFilesDir());
        pick = AgvnLightPick.of(activity);
        String json = pick.json != null ? pick.json : FileUtils.readString(activity, AgvnLayouts.assetFor(kind));
        layout = AgvnLightLayout.parse(kind, json != null ? json : "{}", host.runner());
        boolean own = prefs.layoutBase(game) == pick.id && AgvnLightPick.ownIsNewest(prefs.layoutAt(game), pick.editedMs)
                && layout.load(prefs.layout(game)); // made from this pick, which was not saved again since
        if (!own && pick.json == null) layout.apply(prefs.positions(kind)); // else the places of AGVN 0.1.6-0.1.8
        if (pick.id != prefs.lastPick(game)) prefs.picked(game, pick.id);
        keys = new AgvnLightKeys(activity, layout, host::binding);
        keys.setAlpha(prefs.opacity());
        keys.setVisibility(pick.id == AgvnLightPick.OFF || prefs.keysHidden(game) ? View.GONE : View.VISIBLE);
        activity.addContentView(keys, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        hud = new AgvnLightHud(activity, host);
        bar = new AgvnLightBar(this);
        menu = new AgvnLightMenu(this, this.gameName);
        pen = new AgvnEditPen(activity, this::edit);
        if (prefs.hud()) hud.start();
        bar.update(keysShown());
        slow = new AgvnLightSlow(activity, host); // "Tự sửa lỗi": a game slow for a minute says why
        slow.start();
    }

    /** Puts the toolkit on the game screen: {@code kind} is the game type of {@link AgvnLayouts} (VN or RPG). */
    static AgvnLightTools attach(Activity activity, String kind, String gameName, File gameDir, Host host) {
        AgvnPowerSave.warn(activity);
        return new AgvnLightTools(activity, kind, gameName, gameDir, host);
    }

    /** Back: ends editing, else closes the menu, else opens it. Always handled. */
    boolean onBack() {
        if (editor.active()) editor.finish();
        else if (menu.isOpen()) menu.close();
        else openMenu();
        return true;
    }

    /** The game goes to the background: no key stays held, the HUD stops. */
    void onPause() {
        keys.releaseAll();
        hud.stop();
        slow.stop();
        if (activity.isFinishing()) AgvnLightSession.ended(activity); // before the library comes back and reads it
    }

    void onResume() {
        if (prefs.hud()) hud.start();
        slow.start();
    }

    void openMenu() {
        if (editor.active()) editor.finish();
        bar.suspend();
        pen.setVisible(false); // it would sit on the menu
        menu.open(keysShown(), prefs.opacity(), prefs.hud());
    }

    /** The menu closed (by a choice, Back or a tap beside it). */
    void menuClosed() {
        bar.reveal();
        pen.setVisible(!editor.active());
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

    /** "Sửa phím": the keys can be moved, changed, added and deleted ({@link AgvnLightEditor}). */
    void edit() {
        if (editor.active()) return;
        if (menu.isOpen()) menu.close();
        bar.suspend();
        pen.setVisible(false);
        editor.start();
    }

    /** The editor is done: the keys show again at the player's opacity, with the bar. */
    void editEnded() {
        keys.setAlpha(prefs.opacity());
        prefs.setKeysHidden(game, false);
        bar.update(true);
        bar.reveal();
        pen.setVisible(true);
    }
}
