/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import org.godotengine.godot.Godot;
import org.godotengine.godot.GodotActivity;

import java.io.File;

/**
 * Plays a Godot 4 game on "Chạy nhẹ" ({@link AgvnGodotLight}) with Godot for Android, in its own process (":godot"):
 * Godot cannot start twice in one process, and a crash there never takes the app down. The game's pack, log and FPS cap
 * come in the command line; user:// and the exe's path, which AGVN's engine reads (scripts/agvn/godot/patches), in the
 * environment. The "Chạy nhẹ" toolkit ({@link AgvnLightTools}) gives the keys, the ⌨ ✎ 👁 ☰ bar, the menu on Back and the
 * HUD. A game that ends before its first frame says so and offers "Chạy bằng Windows".
 */
public class AgvnGodotActivity extends GodotActivity {
    private static final String TAG = "AGVN";
    public static final String EXTRA_GAME_DIR = "agvn_godot_game_dir", EXTRA_EXE = "agvn_godot_exe",
            EXTRA_APPDATA = "agvn_godot_appdata", EXTRA_LOG = "agvn_godot_log";
    /** GodotActivity.EXTRA_COMMAND_LINE_PARAMS: read from the intent of an activity that is not exported. */
    public static final String EXTRA_COMMAND_LINE = "command_line_params";

    private AgvnLightTools tools;
    private File gameDir;
    private volatile boolean drawing;
    private boolean ending;
    /** Where the player last touched the game, for the mouse buttons of on-screen keys. */
    float touchX = -1, touchY = -1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Intent intent = getIntent();
        String dir = intent.getStringExtra(EXTRA_GAME_DIR);
        gameDir = dir != null ? new File(dir) : null;
        AgvnGodotLight.setenv("AGVN_GODOT_APPDATA", intent.getStringExtra(EXTRA_APPDATA)); // read by the engine at its start
        AgvnGodotLight.setenv("AGVN_GODOT_EXECUTABLE", intent.getStringExtra(EXTRA_EXE));
        String log = intent.getStringExtra(EXTRA_LOG);
        if (log != null) {
            File file = new File(log);
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            file.delete(); // the log tells of this run only
        }
        super.onCreate(savedInstanceState);
        AgvnLightSession.begin(this, AgvnHtmlGame.RUNNER_GODOT, gameDir); // "Tự sửa lỗi" reads how it ends
        String name = intent.getStringExtra("shortcut_name");
        tools = AgvnLightTools.attach(this, AgvnGodotLight.layoutKind(intent), name, gameDir, new AgvnGodotHost(this));
        AgvnKeepAlive.startGodot(this, name); // keeps running in the background
    }

    @Override
    public void onGodotMainLoopStarted() {
        drawing = true;
        AgvnLightSession.started(this);
    }

    @Override
    protected void onPause() {
        if (tools != null) tools.onPause(); // no key stays down while the game is in the background
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tools != null) tools.onResume();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean mouse = (event.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE;
        if (event.getKeyCode() != KeyEvent.KEYCODE_BACK || mouse) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled() && tools != null) tools.onBack();
        return true; // Godot would quit on Back (application/config/quit_on_go_back)
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        touchX = event.getX();
        touchY = event.getY();
        return super.dispatchTouchEvent(event);
    }

    /** "Chạy bằng Windows": the shortcut remembers Wine and the game starts there; this process ends. */
    void switchToWindows() {
        Intent intent = AgvnHtmlGame.toWindows(this, EXTRA_COMMAND_LINE);
        if (intent != null) startActivity(intent);
        end();
    }

    /** The game quit (get_tree().quit()), or the engine stopped: before its first frame, the player is told why. */
    @Override
    public void onGodotForceQuit(Godot instance) {
        runOnUiThread(() -> {
            if (drawing || ending || isFinishing()) {
                end();
                return;
            }
            ending = true;
            AgvnGodotFailure.ask(this, getIntent().getStringExtra(EXTRA_LOG),
                    AgvnHtmlGame.hasWindowsExe(this) ? this::switchToWindows : null, this::end);
        });
    }

    /** A game asking Godot to restart ends instead: the player starts it again from the library. */
    @Override
    public void onGodotRestartRequested(Godot instance) {
        runOnUiThread(this::end);
    }

    @Override
    public int onNewGodotInstanceRequested(String[] args) {
        Log.w(TAG, "Godot: a second instance is not started");
        return -1;
    }

    /** Godot's quit removes the whole task, the library's screens with it: this screen alone goes. */
    @Override
    public void finishAndRemoveTask() {
        finish();
    }

    /** No web page leaves the game (OS.shell_open): AGVN Player opens no links but agvn.io. */
    @Override
    public void startActivity(Intent intent, Bundle options) {
        if (AgvnGodotInput.isWebLink(intent)) {
            Log.i(TAG, "Godot: web page not opened: " + intent.getData());
            return;
        }
        super.startActivity(intent, options);
    }

    /** Ends this process: Godot cannot start twice in one process, and the app's screens live elsewhere. */
    private void end() {
        AgvnLightSession.ended(this); // a choice of the player or the game, never a crash
        Process.killProcess(Process.myPid());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isFinishing()) end();
    }
}
