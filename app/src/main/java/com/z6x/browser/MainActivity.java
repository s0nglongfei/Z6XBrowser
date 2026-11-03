package com.z6x.browser;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView web;

    // 桌面 Chrome UA，降低被识别为机器人/触发验证的概率
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 全屏
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#1a1a2e"));
        setContentView(web);

        setupWebView();

        // 加载主页
        web.loadUrl("file:///android_asset/home.html");
    }

    private void setupWebView() {
        WebSettings s = web.getSettings();

        // 基础
        s.setJavaScriptEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);

        // 视频播放
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);

        // 文件/跨域
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setAllowFileAccessFromFileURLs(true);

        // 缓存
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setAppCacheEnabled(true);

        // UA：默认桌面版，防机器人验证
        s.setUserAgentString(DESKTOP_UA);

        // Cookie：允许第三方，过年龄验证必须
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        // 跳转拦截：处理主页的 yuyu:// 协议
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(view, request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 页面加载完，自动尝试点击"确认/继续"类按钮
                autoClickConfirm();
            }
        });

        web.setWebChromeClient(new WebChromeClient());
    }

    // 处理自定义协议跳转
    private boolean handleUrl(WebView view, String url) {
        if (url == null) return false;

        if (url.startsWith("yuyu://")) {
            Uri uri = Uri.parse(url);
            String action = uri.getHost();

            if ("open".equals(action)) {
                String target = uri.getQueryParameter("url");
                if (target != null) {
                    view.loadUrl(target);
                }
            } else if ("search".equals(action)) {
                String q = uri.getQueryParameter("q");
                if (q != null && !q.isEmpty()) {
                    String baidu = "https://m.baidu.com/s?wd=" + q;
                    view.loadUrl(baidu);
                }
            }
            return true;

        } else if (url.startsWith("http://") || url.startsWith("https://")) {
            return false; // 正常网页，WebView 自己加载
        }

        return false;
    }

    // 自动点击"确认/继续"按钮（应对未满18岁验证页）
    private void autoClickConfirm() {
        String js =
            "javascript:(function(){" +
            "  var btns = document.querySelectorAll('button, a, input[type=submit], .btn, [role=button]');" +
            "  var keywords = ['accept','continue','confirm','agree','enter','verify','weitermachen','ich bin','i am','18','allow','access','submit','ok','yes'];" +
            "  for (var i = 0; i < btns.length; i++) {" +
            "    var txt = (btns[i].innerText || btns[i].value || btns[i].getAttribute('aria-label') || '').toLowerCase();" +
            "    for (var j = 0; j < keywords.length; j++) {" +
            "      if (txt.indexOf(keywords[j]) >= 0) {" +
            "        btns[i].click();" +
            "        return;" +
            "      }" +
            "    }" +
            "  }" +
            "})()";
        web.evaluateJavascript(js, null);
    }

    @Override
    public void onBackPressed() {
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
