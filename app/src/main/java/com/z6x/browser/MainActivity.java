package com.z6x.browser;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private WebView web;
    private FrameLayout root;
    private LinearLayout keyboard;
    private TextView address;
    private View cursor;
    private int px = 100, py = 100;
    private int mode = 0; // 0:鼠标 1:滚动 2:键盘 3:浏览
    private int uaMode = 0; // 0:手机 1:桌面 2:iPad
    private boolean incognito = false;
    
    private final String UA_DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private final String UA_IPAD = "Mozilla/5.0 (iPad; CPU OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        root = findViewById(R.id.root);
        web = findViewById(R.id.web);
        keyboard = findViewById(R.id.keyboard);
        address = findViewById(R.id.address);

        // ★★★ 修复闪屏：禁用硬件加速，设置透明背景 ★★★
        web.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        web.setBackgroundColor(Color.TRANSPARENT);

        // ★★★ 修复点击 & 认证：补全所有必要 WebView 设置 ★★★
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setAllowFileAccess(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        // 开启第三方 Cookie（过年龄验证必须）
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        applyUA();

        // 自定义 WebViewClient 处理跳转和自动点击验证
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // ★★★ 自动尝试点击常见验证按钮 (Cloudflare/年龄确认等) ★★★
                String autoClickJs = "javascript:(function(){" +
                    "var btns = document.querySelectorAll('button, a, input[type=submit], .button');" +
                    "for(var i=0; i<btns.length; i++){" +
                    "   var txt = (btns[i].innerText || btns[i].value || btns[i].getAttribute('aria-label') || '').toLowerCase();" +
                    "   if(txt.includes('weitermachen') || txt.includes('enter') || txt.includes('continue') || txt.includes('accept') || txt.includes('verify') || txt.includes('confirm') || txt.includes('check')){" +
                    "       btns[i].click();" +
                    "   }" +
                    "}" +
                    "})()";
                web.evaluateJavascript(autoClickJs, null);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View v, CustomViewCallback cb) {
                root.addView(v, new FrameLayout.LayoutParams(-1, -1));
                fullscreen = v;
                fullscreenCallback = cb;
            }
            @Override
            public void onHideCustomView() {
                hideVideo();
            }
        });

        // 初始化主页
        web.loadUrl("file:///android_asset/home.html");

        // 初始化光标
        cursor = new View(this);
        cursor.setBackgroundResource(R.drawable.cursor_fish); // 需确保有此资源，或用系统箭头
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(40), dp(40));
        cp.leftMargin = px;
        cp.topMargin = py;
        root.addView(cursor, cp);

        setupKeyboard();
    }

    // ★★★ 核心修复：用 JS 注入模拟真实点击，解决网页拦截 ★★★
    private void jsClickAt(int x, int y) {
        String js = "javascript:(function(){" +
            "var elements = document.elementsFromPoint(" + x + ", " + y + ");" +
            "for(var i=0; i<elements.length; i++){" +
            "   var el = elements[i];" +
            "   if(el.tagName === 'BUTTON' || el.tagName === 'A' || el.onclick || getComputedStyle(el).cursor === 'pointer'){" +
            "       var ev = document.createEvent('HTMLEvents');" +
            "       ev.initEvent('click', true, true);" +
            "       el.dispatchEvent(ev);" +
            "       break;" +
            "   }" +
            "}" +
            "})()";
        web.evaluateJavascript(js, null);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (mode == 3) return super.dispatchKeyEvent(e);

        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) {
            switchMode();
            return true;
        }
        if (mode == 2) return super.dispatchKeyEvent(e); // 键盘模式
        if (k == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(e);

        // 确认键 / 方向键处理
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                if (mode == 1) {
                    mode = 0;
                    position();
                } else {
                    // 移动光标后按确认，注入JS点击
                    jsClickAt(px + dp(2), py + dp(2));
                }
            }
            return true;
        }

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

    // 处理自定义协议跳转 (修复主页搜索)
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        if (url.startsWith("z6x://")) {
            try {
                android.net.Uri uri = android.net.Uri.parse(url);
                String host = uri.getHost();
                if ("open".equals(host)) {
                    String target = uri.getQueryParameter("url");
                    if (target != null) view.loadUrl(target);
                    return true;
                } else if ("search".equals(host)) {
                    String q = uri.getQueryParameter("q");
                    if (q != null && q.length() > 0) {
                        String baidu = "https://m.baidu.com/s?wd=" + java.net.URLEncoder.encode(q, "UTF-8");
                        view.loadUrl(baidu);
                    }
                    return true;
                }
            } catch (Exception e) { e.printStackTrace(); }
            return true;
        }
        return false;
    }

    private void applyUA() {
        WebSettings s = web.getSettings();
        if (uaMode == 1) s.setUserAgentString(UA_DESKTOP);
        else if (uaMode == 2) s.setUserAgentString(UA_IPAD);
        else s.setUserAgentString(null); // 默认系统手机UA
    }

    private void switchMode() {
        mode = (mode + 1) % 4;
        // 更新UI状态...
    }

    private void position() {
        if (cursor != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) cursor.getLayoutParams();
            lp.leftMargin = px;
            lp.topMargin = py;
            cursor.setLayoutParams(lp);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack() && mode != 2) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }
    
    private View fullscreen = null;
    private WebChromeClient.CustomViewCallback fullscreenCallback = null;
    private void hideVideo() { /* ... 保持原样 ... */ }
    private void setupKeyboard() { /* ... 保持原样 ... */ }
    @Override protected void onPause() { if(web!=null) web.onPause(); super.onPause(); }
    @Override protected void onResume() { if(web!=null) web.onResume(); super.onResume(); }
    @Override protected void onDestroy() { if(web!=null) web.destroy(); super.onDestroy(); }
}
