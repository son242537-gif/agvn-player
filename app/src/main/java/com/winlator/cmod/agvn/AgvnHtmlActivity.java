/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;

import java.io.File;

/**
 * Plays an HTML game ({@link AgvnHtmlGame}) full screen in a WebView. Offline: only the game's own files load. The
 * "Chạy nhẹ" toolkit ({@link AgvnLightTools}) gives the keys of the Windows layout (RPG for RPG Maker MV/MZ, visual
 * novel for Tyrano), the ⌨ ✎ 👁 ☰ bar, the menu on Back and the HUD.
 */
public class AgvnHtmlActivity extends AppCompatActivity {
    public static final String EXTRA_INDEX_PATH = "agvn_html_index";

    private WebView webView;
    private File root;
    private String host;
    /** assets/agvn/html-compat.js; null when unreadable (the game then runs without it). */
    private String compatJs;
    /** "Chạy bằng Windows" was picked: the Wine game now owns the keep-alive notification. */
    private boolean toWindows;
    private AgvnLightTools tools;

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
        AgvnKeepAlive.start(this, getIntent().getStringExtra("shortcut_name")); // the game keeps running in the background
        AgvnLightSession.begin(this, AgvnHtmlGame.RUNNER_HTML, AgvnLightGame.folderOf(index)); // "Tự sửa lỗi" reads its end
        boolean rpgMaker = AgvnHtmlFiles.resolve(root, "/js/rpg_core.js") != null || AgvnHtmlFiles.resolve(root, "/js/rmmz_core.js") != null;
        if (rpgMaker) compatJs = com.winlator.cmod.core.FileUtils.readString(this, "agvn/html-compat.js");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        AgvnRefreshCap.apply(this); // 60 Hz: the page draws every refresh, the games move 60 times a second
        // with a log setting on (Cài đặt > Nhật ký), the page can be inspected over USB and logs all its console
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean logs = prefs.getBoolean("enable_wine_debug", false) || prefs.getBoolean("enable_winlator_logs", false);
        WebView.setWebContentsDebuggingEnabled(logs);

        try {
            webView = new WebView(this);
        } catch (RuntimeException e) {
            // Android System WebView disabled or updating: play the Windows version instead, when there is one
            boolean windows = AgvnHtmlGame.hasWindowsExe(this);
            Toast.makeText(this, windows ? R.string.agvn_html_no_webview : R.string.agvn_html_no_webview_no_exe, Toast.LENGTH_LONG).show();
            if (windows) switchToWindows();
            else finish();
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
        webView.setWebChromeClient(new AgvnHtmlConsole(logs));
        if (rpgMaker) webView.addJavascriptInterface(new AgvnHtmlSaves(root), AgvnHtmlSaves.NAME); // saves as on a PC
        setContentView(webView);
        hideSystemUi();
        webView.loadUrl("https://" + host + "/" + Uri.encode(index.getName()));
        tools = AgvnLightTools.attach(this, rpgMaker ? AgvnLayouts.RPG : AgvnLayouts.VN, getIntent().getStringExtra("shortcut_name"),
                AgvnLightGame.folderOf(index), new AgvnHtmlHost(this, webView));
        webView.postDelayed(fatalPoll, AgvnHtmlConsole.FATAL_POLL_MS);
    }

    /** "Tự sửa lỗi": an error that stopped the game (html-compat.js keeps it in __agvnFatal), asked about at the end. */
    private final Runnable fatalPoll = new Runnable() {
        @Override
        public void run() {
            if (webView == null || isFinishing()) return;
            webView.evaluateJavascript(AgvnHtmlConsole.FATAL, v -> {
                String error = AgvnHtmlConsole.unquote(v);
                if (!error.isEmpty()) AgvnLightSession.error(AgvnHtmlActivity.this, error);
                else if (webView != null) webView.postDelayed(fatalPoll, AgvnHtmlConsole.FATAL_POLL_MS);
            });
        }
    };

    private final class GameClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            if (!host.equals(url.getHost())) return AgvnHtmlFiles.blocked(); // no network: nothing leaves the phone
            return AgvnHtmlFiles.serve(root, url.getEncodedPath(), compatJs, AgvnLightSession.standIns(view.getContext()));
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return !host.equals(request.getUrl().getHost()); // links out of the game do nothing
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            AgvnLightSession.started(AgvnHtmlActivity.this);
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            // the game page crashed or ran out of memory: close the game, never the whole app
            AgvnLightSession.pageCrashed(AgvnHtmlActivity.this); // the library then says why and offers Windows
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

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (tools != null) tools.onBack();
        else finish();
    }

    /** "Chạy bằng Windows": for a game whose scripts need the PC version; the shortcut remembers the choice. */
    void switchToWindows() {
        Intent intent = AgvnHtmlGame.toWindows(this, EXTRA_INDEX_PATH);
        toWindows = intent != null;
        if (intent != null) startActivity(intent);
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
        if (tools != null) tools.onPause();
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
        if (tools != null) tools.onResume();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        if (isFinishing() && !toWindows) AgvnKeepAlive.stop(this); // back to the library, which needs no keep-alive
        if (isFinishing()) AgvnLightSession.ended(this);
        super.onDestroy();
    }
}
