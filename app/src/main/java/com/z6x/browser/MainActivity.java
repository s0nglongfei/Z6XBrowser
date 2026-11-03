package com.z6x.browser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

@SuppressLint("SetJavaScriptEnabled")
public class MainActivity extends Activity {

    private WebView web;
    private FrameLayout root;
    private View cursor;
    private int px = 300, py = 300;
    private int mode = 0; // 0:光标 1:网页 2:滚动 3:键盘
    private Handler handler = new Handler();
    private FrameLayout fullscreen;

    private static final String UA_DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36";
    private static final String UA_IPAD = "Mozilla/5.0 (iPad; CPU OS 14_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Mobile/15E148 Safari/604.1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new FrameLayout(this);
        setContentView(root);

        setupWebView();
        setupCursor();
        
        // 加载主页
        web.loadUrl("file:///android_asset/home.html");
    }

    private void setupWebView() {
        web = new WebView(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        web.setLayoutParams(lp);
        
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        
        // 默认使用手机UA，兼容性好
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 11; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("z6x".equals(scheme)) {
                    handleZ6XProtocol(uri);
                    return true;
                }
                return false;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                fullscreen = new FrameLayout(MainActivity.this);
                fullscreen.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                fullscreen.addView(view);
                root.addView(fullscreen);
                fullscreenCallback = callback;
            }
            @Override
            public void onHideCustomView() {
                hideVideo();
            }
        });

        root.addView(web);
    }

    private void setupCursor() {
        cursor = new View(this);
        cursor.setBackgroundResource(android.R.drawable.ic_menu_send); // 临时用系统图标代替🐟
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(32), dp(32));
        cursor.setLayoutParams(cp);
        cursor.setVisibility(View.GONE);
        root.addView(cursor);
    }

    private void handleZ6XProtocol(Uri uri) {
        String host = uri.getHost();
        if ("search".equals(host)) {
            String q = uri.getQueryParameter("q");
            if (q != null) {
                web.loadUrl("https://m.baidu.com/s?word=" + Uri.encode(q));
            }
        } else if ("open".equals(host)) {
            String url = uri.getQueryParameter("url");
            if (url != null) {
                web.loadUrl(url);
            }
        }
    }

    private WebChromeClient.CustomViewCallback fullscreenCallback;

    private void hideVideo() {
        if (fullscreen != null) {
            root.removeView(fullscreen);
            fullscreen = null;
            if (fullscreenCallback != null) {
                fullscreenCallback.onCustomViewHidden();
                fullscreenCallback = null;
            }
        }
    }

    private int dp(int v) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(v * density);
    }

    private void position() {
        if (cursor == null) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) cursor.getLayoutParams();
        lp.leftMargin = px;
        lp.topMargin = py;
        cursor.setLayoutParams(lp);
    }

    // JS注入点击（修复网页拦截问题）
    private void jsClickAt(int x, int y) {
        String js = "javascript:(function(){" +
                "var elements = document.elementsFromPoint(" + x + ", " + y + ");" +
                "for(var i=0; i<elements.length; i++){" +
                "   if(elements[i].tagName === 'BUTTON' || elements[i].tagName === 'A' || elements[i].onclick){" +
                "       var ev = document.createEvent('HTMLEvents');" +
                "       ev.initEvent('click', true, true);" +
                "       elements[i].dispatchEvent(ev);" +
                "       break;" +
                "   }" +
                "}" +
                "})()";
        web.evaluateJavascript(js, null);
    }

    private void switchMode() {
        mode = (mode + 1) % 4;
        String msg = "";
        switch (mode) {
            case 0: msg = "光标模式"; cursor.setVisibility(View.VISIBLE); break;
            case 1: msg = "网页模式"; cursor.setVisibility(View.GONE); web.requestFocus(); break;
            case 2: msg = "滚动模式"; cursor.setVisibility(View.VISIBLE); break;
            case 3: msg = "键盘模式"; cursor.setVisibility(View.GONE); break;
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (fullscreen != null) return super.dispatchKeyEvent(e);
        if (mode == 3) return super.dispatchKeyEvent(e);

        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) {
            switchMode();
            return true;
        }
        if (mode == 2) return super.dispatchKeyEvent(e);
        if (k == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(e);

        // 确认键：JS注入点击
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                if (mode == 0) {
                    mode = 1;
                    position();
                    jsClickAt(px + dp(2), py + dp(2));
                }
            }
            return true;
        }

        // 方向键移动光标
        if (k >= KeyEvent.KEYCODE_DPAD_UP && k <= KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                int d = dp(e.getRepeatCount() > 4 ? 34 : 17);
                if (mode == 0) {
                    if (k == KeyEvent.KEYCODE_DPAD_UP) py -= d;
                    if (k == KeyEvent.KEYCODE_DPAD_DOWN) py += d;
                    if (k == KeyEvent.KEYCODE_DPAD_LEFT) px -= d;
                    if (k == KeyEvent.KEYCODE_DPAD_RIGHT) px += d;
                    position();
                } else {
                    web.scrollBy(
                            k == KeyEvent.KEYCODE_DPAD_LEFT ? -dp(160) : k == KeyEvent.KEYCODE_DPAD_RIGHT ? dp(160) : 0,
                            k == KeyEvent.KEYCODE_DPAD_UP ? -dp(200) : k == KeyEvent.KEYCODE_DPAD_DOWN ? dp(200) : 0
                    );
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public void onBackPressed() {
        if (fullscreen != null) {
            hideVideo();
            return;
        }
        if (mode != 0) {
            mode = 0;
            cursor.setVisibility(View.VISIBLE);
            return;
        }
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        if (web != null) web.onResume();
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
