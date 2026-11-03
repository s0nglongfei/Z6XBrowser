package com.z6x.browser;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URLEncoder;

/**
 * Z6XBrowser - 极米 NEW Z6X 专用浏览器
 * Android 8.0 / API 26
 */
public class MainActivity extends Activity {

    WebView web = null;
    EditText address;
    FrameLayout root, stage;
    View cursor;
    LinearLayout bar, keyboardPanel;
    GridView keyboard;
    FrameLayout fullscreen;
    WebChromeClient.CustomViewCallback fullscreenCallback;
    TextView uaBtn, incBtn;

    int mode = 0;
    float px = 350, py = 220;

    boolean incognito = true;
    int uaMode = 0;

    static final String UA_DESKTOP =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    static final String UA_IPAD =
            "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";

    static final String[] KEYS = {
            "a", "b", "c", "d", "e", "f", "g", "h", "i",
            "j", "k", "l", "m", "n", "o", "p", "q", "r",
            "s", "t", "u", "v", "w", "x", "y", "z", "0",
            "1", "2", "3", "4", "5", "6", "7", "8", "9",
            ".", "/", ":", "-", "_", "~", "%", "@", "?",
            ".com", "删除", "清空", "访问", "关闭"
    };

    static final String[] MODE_TIPS = {
            "鼠标模式：方向移动光标，确定点击",
            "滚动模式：方向滚动页面，确定回鼠标",
            "导航模式：方向键切换顶栏按钮",
            "输入模式：方向选字符，确定输入"
    };

    int dp(float x) {
        return (int) (x * getResources().getDisplayMetrics().density + 0.5f);
    }

    TextView button(String s, final Runnable r) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(0xffffffff);
        v.setTextSize(16);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(12), dp(10), dp(12), dp(10));
        v.setFocusable(true);
        v.setBackgroundColor(0xff343b49);
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View w, boolean has) {
                w.setBackgroundColor(has ? 0xff00e5ff : 0xff343b49);
                ((TextView) w).setTextColor(has ? 0xff000000 : 0xffffffff);
            }
        });
        v.setOnClickListener(new View.OnClickListener() {
            public void onClick(View w) { r.run(); }
        });
        return v;
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        try {
            // ========== 1. 全屏 ==========
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
            );

            // ========== 2. 根布局 ==========
            root = new FrameLayout(this);
            setContentView(root);

            LinearLayout main = new LinearLayout(this);
            main.setOrientation(LinearLayout.VERTICAL);
            root.addView(main, new FrameLayout.LayoutParams(-1, -1));

            // ========== 3. 顶栏 ==========
            bar = new LinearLayout(this);
            bar.setBackgroundColor(0xff202631);
            main.addView(bar, new LinearLayout.LayoutParams(-1, dp(54)));

            bar.addView(button("←", new Runnable() {
                public void run() { if (web != null && web.canGoBack()) web.goBack(); }
            }));
            bar.addView(button("→", new Runnable() {
                public void run() { if (web != null && web.canGoForward()) web.goForward(); }
            }));
            bar.addView(button("⟳", new Runnable() {
                public void run() { if (web != null) web.reload(); }
            }));

            address = new EditText(this);
            address.setSingleLine(true);
            address.setTextColor(0xffffffff);
            address.setHintTextColor(0xffbbbbbb);
            address.setHint("输入网址");
            address.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
            address.setBackgroundColor(0xff11151c);
            address.setPadding(dp(10), 0, dp(10), 0);
            bar.addView(address, new LinearLayout.LayoutParams(0, -1, 1));
            address.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                public boolean onEditorAction(TextView v, int a, KeyEvent e) {
                    navigate();
                    return true;
                }
            });
            address.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { openKeyboard(); }
            });

            bar.addView(button("访问", new Runnable() {
                public void run() { navigate(); }
            }));
            bar.addView(button("网址", new Runnable() {
                public void run() { openKeyboard(); }
            }));

            uaBtn = button("UA手机", new Runnable() {
                public void run() { switchUA(); }
            });
            bar.addView(uaBtn);

            incBtn = button("无痕✓", new Runnable() {
                public void run() { toggleIncognito(); }
            });
            bar.addView(incBtn);

            bar.addView(button("模式", new Runnable() {
                public void run() { switchMode(); }
            }));

            // ========== 4. 网页区 ==========
            stage = new FrameLayout(this);
            main.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));

            // ★★★ WebView 最先初始化 ★★★
            web = new WebView(this);
            web.setFocusable(true);
            web.setFocusableInTouchMode(true);
            web.setEnabled(true);
            stage.addView(web, new FrameLayout.LayoutParams(-1, -1));

            // WebView 设置
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setAllowFileAccess(true);
            s.setMediaPlaybackRequiresUserGesture(false);
            s.setBuiltInZoomControls(true);
            s.setDisplayZoomControls(false);
            s.setUseWideViewPort(true);
            s.setLoadWithOverviewMode(true);
            s.setSupportZoom(true);
            s.setSaveFormData(false);
            s.setSavePassword(false);
            s.setGeolocationEnabled(false);
            s.setDatabaseEnabled(false);
            s.setCacheMode(incognito ? WebSettings.LOAD_NO_CACHE : WebSettings.LOAD_DEFAULT);

            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(!incognito);

            applyUA();

            // ========== WebViewClient（核心：拦截 z6x:// 协议） ==========
            web.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView v, String url) {
                    address.setText(url);
                    if ("file:///android_asset/home.html".equals(url)) {
                        bar.setVisibility(View.GONE);
                    } else {
                        bar.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return handleZ6x(request.getUrl().toString());
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return handleZ6x(url);
                }
            });

            // WebChromeClient
            web.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onShowCustomView(View view, CustomViewCallback cb) {
                    if (fullscreen != null) {
                        cb.onCustomViewHidden();
                        return;
                    }
                    fullscreen = new FrameLayout(MainActivity.this);
                    fullscreen.setBackgroundColor(0xff000000);
                    fullscreen.addView(view, new FrameLayout.LayoutParams(-1, -1));
                    root.addView(fullscreen, new FrameLayout.LayoutParams(-1, -1));
                    fullscreenCallback = cb;
                }

                @Override
                public void onHideCustomView() {
                    hideVideo();
                }

                @Override
                public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                    cb.invoke(origin, false, false);
                }
            });

            // 触摸弹键盘
            web.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        if (!v.hasFocus()) v.requestFocusFromTouch();
                        InputMethodManager imm =
                                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
                    }
                    return false;
                }
            });

            // ========== 5. 鼠标光标 ==========
            cursor = new View(this) {
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

                protected void onDraw(Canvas c) {
                    p.setColor(0xff00e5ff);
                    p.setStyle(Paint.Style.FILL);
                    Path path = new Path();
                    path.moveTo(1, 1);
                    path.lineTo(1, dp(27));
                    path.lineTo(dp(8), dp(20));
                    path.lineTo(dp(14), dp(34));
                    path.lineTo(dp(19), dp(31));
                    path.lineTo(dp(12), dp(18));
                    path.lineTo(dp(23), dp(18));
                    path.close();
                    c.drawPath(path, p);
                    p.setColor(0xff000000);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(dp(2));
                    c.drawPath(path, p);
                }
            };
            stage.addView(cursor, new FrameLayout.LayoutParams(dp(36), dp(40)));
            stage.post(new Runnable() {
                public void run() {
                    px = stage.getWidth() / 2f;
                    py = stage.getHeight() / 2f;
                    position();
                }
            });

            // ========== 6. 内置键盘 ==========
            buildKeyboard(main);

            // ========== 7. 加载主页 ==========
            web.loadUrl("file:///android_asset/home.html");

            hideIME();
            updateIncognitoButton();
            updateUAButton();

        } catch (Throwable e) {
            e.printStackTrace();
            Toast.makeText(this, "启动出错：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ========== 核心：处理自定义协议 ==========
    private boolean handleZ6x(String url) {
        if (url != null && url.startsWith("z6x://")) {
            try {
                Uri uri = Uri.parse(url);
                String host = uri.getHost();

                if ("open".equals(host)) {
                    String target = uri.getQueryParameter("url");
                    if (target != null) {
                        web.loadUrl(target);
                    }
                    return true;

                } else if ("search".equals(host)) {
                    String q = uri.getQueryParameter("q");
                    if (q != null && q.length() > 0) {
                        String baidu = "https://m.baidu.com/s?wd=" + URLEncoder.encode(q, "UTF-8");
                        web.loadUrl(baidu);
                    }
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return true;
        }
        return !(url.startsWith("http://") || url.startsWith("https://"));
    }

    // ==================== 以下为各方法 ====================

    void applyUA() {
        WebSettings s = web.getSettings();
        if (uaMode == 1) {
            s.setUserAgentString(UA_DESKTOP);
        } else if (uaMode == 2) {
            s.setUserAgentString(UA_IPAD);
        } else {
            s.setUserAgentString(null);
        }
    }

    void switchUA() {
        uaMode = (uaMode + 1) % 3;
        applyUA();
        updateUAButton();
        if (web != null) web.reload();
    }

    void updateUAButton() {
        if (uaBtn != null) {
            uaBtn.setText(uaMode == 0 ? "UA手机" : uaMode == 1 ? "UA桌面" : "UA平板");
        }
    }

    void toggleIncognito() {
        incognito = !incognito;
        WebSettings s = web.getSettings();
        s.setCacheMode(incognito ? WebSettings.LOAD_NO_CACHE : WebSettings.LOAD_DEFAULT);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(!incognito);
        wipeAll();
        updateIncognitoButton();
        Toast.makeText(this, incognito ? "无痕：开" : "无痕：关", Toast.LENGTH_SHORT).show();
    }

    void updateIncognitoButton() {
        if (incBtn != null) {
            incBtn.setText(incognito ? "无痕✓" : "无痕✗");
        }
    }

    void wipeAll() {
        if (web != null) {
            web.clearCache(true);
            web.clearHistory();
            web.clearFormData();
        }
        CookieManager.getInstance().removeAllCookie();
        try {
            android.webkit.WebStorage.getInstance().deleteAllData();
        } catch (Exception ignore) {}
    }

    void navigate() {
        if (address == null || web == null) return;
        String u = address.getText().toString().trim();
        if (u.length() == 0) return;

        // 如果是搜索词（不含.），直接百度
        if (!u.contains(".") && !u.contains(" ")) {
            try {
                String baidu = "https://m.baidu.com/s?wd=" + URLEncoder.encode(u, "UTF-8");
                web.loadUrl(baidu);
            } catch (Exception e) { e.printStackTrace(); }
            hideIME();
            address.clearFocus();
            return;
        }

        if (!u.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) u = "https://" + u;
        web.loadUrl(u);
        hideIME();
        address.clearFocus();
    }

    void buildKeyboard(LinearLayout main) {
        keyboardPanel = new LinearLayout(this);
        keyboardPanel.setOrientation(LinearLayout.VERTICAL);
        keyboardPanel.setBackgroundColor(0xff1a1f28);
        keyboardPanel.setVisibility(View.GONE);
        main.addView(keyboardPanel, new LinearLayout.LayoutParams(-1, -2));

        keyboard = new GridView(this);
        keyboard.setNumColumns(9);
        keyboard.setVerticalSpacing(dp(4));
        keyboard.setHorizontalSpacing(dp(4));
        keyboard.setPadding(dp(8), dp(8), dp(8), dp(8));
        keyboard.setAdapter(new BaseAdapter() {
            public int getCount() { return KEYS.length; }
            public Object getItem(int i) { return KEYS[i]; }
            public long getItemId(int i) { return i; }

            public View getView(int i, View cv, ViewGroup p) {
                TextView t = (cv instanceof TextView) ? (TextView) cv : new TextView(MainActivity.this);
                t.setText(KEYS[i]);
                t.setTextColor(0xffffffff);
                t.setTextSize(16);
                t.setGravity(Gravity.CENTER);
                t.setPadding(dp(4), dp(12), dp(4), dp(12));
                t.setFocusable(true);
                t.setBackgroundColor(0xff343b49);
                t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    public void onFocusChange(View v, boolean has) {
                        v.setBackgroundColor(has ? 0xff00e5ff : 0xff343b49);
                        ((TextView) v).setTextColor(has ? 0xff000000 : 0xffffffff);
                    }
                });
                return t;
            }
        });
        keyboard.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                String key = KEYS[pos];
                String cur = address.getText().toString();
                if (key.equals("删除")) {
                    if (cur.length() > 0) address.setText(cur.substring(0, cur.length() - 1));
                } else if (key.equals("清空")) {
                    address.setText("");
                } else if (key.equals("访问")) {
                    navigate();
                    closeKeyboard();
                } else if (key.equals("关闭")) {
                    closeKeyboard();
                } else {
                    address.setText(cur + key);
                }
                address.setSelection(address.getText().length());
            }
        });
        keyboardPanel.addView(keyboard, new LinearLayout.LayoutParams(-1, -2));
    }

    void openKeyboard() {
        mode = 3;
        keyboardPanel.setVisibility(View.VISIBLE);
        position();
        keyboard.post(new Runnable() {
            public void run() {
                if (keyboard.getChildCount() > 0) keyboard.getChildAt(0).requestFocus();
            }
        });
        Toast.makeText(this, MODE_TIPS[3], Toast.LENGTH_SHORT).show();
    }

    void closeKeyboard() {
        keyboardPanel.setVisibility(View.GONE);
        mode = 0;
        position();
        hideIME();
    }

    void hideIME() {
        try {
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(address.getWindowToken(), 0);
        } catch (Exception ignore) {}
    }

    void position() {
        if (stage == null || cursor == null) return;
        px = Math.max(0, Math.min(stage.getWidth() - dp(30), px));
        py = Math.max(0, Math.min(stage.getHeight() - dp(36), py));
        cursor.setX(px);
        cursor.setY(py);
        cursor.setVisibility(mode == 0 ? View.VISIBLE : View.GONE);
    }

    void switchMode() {
        mode = (mode + 1) % 4;
        position();
        keyboardPanel.setVisibility(mode == 3 ? View.VISIBLE : View.GONE);
        Toast.makeText(this, MODE_TIPS[mode], Toast.LENGTH_SHORT).show();
        if (mode == 2) {
            bar.getChildAt(0).requestFocus();
        } else if (mode == 3) {
            keyboard.post(new Runnable() {
                public void run() {
                    if (keyboard.getChildCount() > 0) keyboard.getChildAt(0).requestFocus();
                }
            });
        } else {
            stage.requestFocus();
            hideIME();
        }
    }

    void hideVideo() {
        if (fullscreen != null) {
            root.removeView(fullscreen);
            fullscreen = null;
            if (fullscreenCallback != null) {
                fullscreenCallback.onCustomViewHidden();
                fullscreenCallback = null;
            }
        }
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

        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                if (mode == 1) {
                    mode = 0;
                    position();
                } else {
                    long t = SystemClock.uptimeMillis();
                    float x = px + dp(2), y = py + dp(2);
                    web.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0));
                    web.dispatchTouchEvent(MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0));
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

    @Override
    public void onBackPressed() {
        if (fullscreen != null) {
            hideVideo();
            return;
        }
        if (mode != 0) {
            closeKeyboard();
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
        wipeAll();
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
