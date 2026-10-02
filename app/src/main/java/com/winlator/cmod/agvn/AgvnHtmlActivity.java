/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;

import java.io.File;

/** Plays an HTML game ({@link AgvnHtmlGame}) full screen in a WebView. Offline: only the game's own files load. */
public class AgvnHtmlActivity extends AppCompatActivity {
    public static final String EXTRA_INDEX_PATH = "agvn_html_index";
    /** Presses Esc in the game (RPG Maker's menu / cancel key); keyCode must be forced, KeyboardEvent ignores it. */
    private static final String PRESS_ESC = "(function(){function k(t){var e=new KeyboardEvent(t,{key:'Escape',code:'Escape',bubbles:true});"
            + "Object.defineProperty(e,'keyCode',{get:function(){return 27}});Object.defineProperty(e,'which',{get:function(){return 27}});"
            + "document.dispatchEvent(e);}k('keydown');setTimeout(function(){k('keyup')},120);})();";

    private WebView webView;
    private File root;
    private String host;
    /** assets/agvn/html-compat.js; null when unreadable (the game then runs without it). */
    private String compatJs;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        File index = new File(String.valueOf(getIntent().getStringExtra(EXTRA_INDEX_PATH)));
        if (!index.isFile()) {
            finish();
            return;
        }
        root = index.getParentFile();
        host = AgvnHtmlGame.hostFor(index);
        boolean rpgMaker = AgvnHtmlFiles.resolve(root, "/js/rpg_core.js") != null || AgvnHtmlFiles.resolve(root, "/js/rmmz_core.js") != null;
        if (rpgMaker) compatJs = com.winlator.cmod.core.FileUtils.readString(this, "agvn/html-compat.js");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // with a log setting on (Cài đặt > Nhật ký), the page can be inspected over USB and logs all its console
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean logs = prefs.getBoolean("enable_wine_debug", false) || prefs.getBoolean("enable_winlator_logs", false);
        WebView.setWebContentsDebuggingEnabled(logs);

        try {
            webView = new WebView(this);
        } catch (RuntimeException e) {
            // Android System WebView disabled or updating: play the Windows version instead
            Toast.makeText(this, R.string.agvn_html_no_webview, Toast.LENGTH_LONG).show();
            switchToWindows();
            return;
        }
        webView.setBackgroundColor(Color.BLACK);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setTextZoom(100);
        webView.setWebViewClient(new GameClient());
        webView.setWebChromeClient(new ConsoleClient(logs));
        setContentView(webView);
        hideSystemUi();
        webView.loadUrl("https://" + host + "/" + Uri.encode(index.getName()));
    }

    private final class GameClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            if (!host.equals(url.getHost())) return AgvnHtmlFiles.blocked(); // no network: nothing leaves the phone
            return AgvnHtmlFiles.serve(root, url.getEncodedPath(), compatJs);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return !host.equals(request.getUrl().getHost()); // links out of the game do nothing
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            // the game page crashed or ran out of memory: close the game, never the whole app
            Toast.makeText(AgvnHtmlActivity.this, R.string.agvn_html_crashed, Toast.LENGTH_LONG).show();
            if (webView != null) {
                ((android.view.ViewGroup) webView.getParent()).removeView(webView);
                webView.destroy();
                webView = null;
            }
            finish();
            return true;
        }
    }

    /** Game script messages in logcat (tag AgvnHtml): warnings and errors always, everything with a log setting on. */
    private static final class ConsoleClient extends WebChromeClient {
        private final boolean all;

        ConsoleClient(boolean all) {
            this.all = all;
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage m) {
            ConsoleMessage.MessageLevel level = m.messageLevel();
            boolean problem = level == ConsoleMessage.MessageLevel.ERROR || level == ConsoleMessage.MessageLevel.WARNING;
            if (all || problem) {
                Log.println(problem ? Log.WARN : Log.INFO, "AgvnHtml", m.message() + " (" + m.sourceId() + ":" + m.lineNumber() + ")");
            }
            return true;
        }

        /** No grey "play" picture on a game video before its first frame. */
        @Override
        public Bitmap getDefaultVideoPoster() {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.agvn_html_back_title)
                .setItems(new CharSequence[]{getString(R.string.agvn_html_menu), getString(R.string.agvn_html_exit),
                        getString(R.string.agvn_html_use_windows)}, (d, which) -> {
                    if (which == 0 && webView != null) webView.evaluateJavascript(PRESS_ESC, null);
                    else if (which == 1) finish();
                    else if (which == 2) switchToWindows();
                })
                .setNegativeButton(R.string.agvn_html_keep_playing, null)
                .setOnDismissListener(d -> hideSystemUi())
                .show();
    }

    /** "Chạy bằng Windows": for a game whose scripts need the PC version; the shortcut remembers the choice. */
    private void switchToWindows() {
        String path = getIntent().getStringExtra("shortcut_path");
        int id = getIntent().getIntExtra("container_id", 0);
        if (id == 0 && path != null) id = AgvnHtmlGame.containerIdIn(new File(path));
        Container container = new ContainerManager(this).getContainerById(id);
        if (path == null || container == null) {
            finish();
            return;
        }
        Shortcut shortcut = new Shortcut(container, new File(path));
        shortcut.putExtra(AgvnHtmlGame.EXTRA_RUNNER, AgvnHtmlGame.RUNNER_WINE);
        shortcut.saveData();
        Intent intent = new Intent(this, XServerDisplayActivity.class);
        if (getIntent().getExtras() != null) intent.putExtras(getIntent().getExtras());
        intent.removeExtra(EXTRA_INDEX_PATH);
        startActivity(intent);
        finish();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
