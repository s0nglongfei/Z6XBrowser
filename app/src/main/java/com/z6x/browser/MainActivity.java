package com.z6x.browser;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
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
import android.content.Context;

/**
 * Z6X / NEW Z6X 专用网页浏览器
 * 目标系统：Android 8.0（API 26，实测 NEW Z6X GMUI 5.0）
 * 全部调用的 API 均 <= 26，不会触发 NoSuchMethodError
 *
 * 遥控器 MENU 键循环切换四种模式：
 *   0 鼠标模式：方向键移动青色箭头光标，OK 键模拟触摸点击（彻底绕开网页焦点问题）
 *   1 滚动模式：方向键滚动页面，OK 键回到鼠标模式
 *   2 导航模式：方向键在顶栏按钮间移动焦点
 *   3 输入模式：屏幕弹出字符键盘，方向键选字符、OK 输入（不依赖任何输入法）
 *
 * 默认无痕：不写历史、不落 Cookie、不缓存、不存表单，退出时全部清空
 * UA 切换：手机 / 桌面Chrome / iPad 三档，解决老 WebView 被网站拒绝播放
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
    TextView uaBtn, incBtn;      // 直接持有引用，避免靠索引找按钮错位

    int mode = 0;              // 0鼠标 1滚动 2导航 3输入
    float px = 350, py = 220;

    boolean incognito = true;  // 默认无痕
    int uaMode = 0;            // 0手机 1桌面 2iPad

    // 桌面 Chrome UA（伪装后多数视频站会放行）
    static final String UA_DESKTOP =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    static final String UA_IPAD =
            "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";

    // 内置键盘字符表（9 列，遥控器上下左右选字，OK 输入）
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
        return (int) (x * getResources().getDisplayMetrics().density + .5f);
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

    public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(1024, 1024);   // FLAG_FULLSCREEN
        root = new FrameLayout(this);
        setContentView(root);
// 动态创建 WebView
web = new WebView(this);
FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
    FrameLayout.LayoutParams.MATCH_PARENT,
    FrameLayout.LayoutParams.MATCH_PARENT
);
web.setLayoutParams(params);
root.addView(web); // 加到主布局里
// 解决网页无法打开/自定义协议报错
web.setWebViewClient(new WebViewClient() {
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return false; // 正常加载网页
        }
        // 忽略其他自定义协议（如 baiduboxapp://）
        return true; 
    }
});

// 设置焦点和触摸弹键盘
web.setFocusable(true);
web.setFocusableInTouchMode(true);
// ... 后面的代码保持不变


// 设置焦点和触摸弹键盘
web.setFocusable(true);
web.setFocusableInTouchMode(true);
web.setEnabled(true);
web.requestFocus(View.FOCUS_DOWN);

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



        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));

        // ---------- 顶栏 ----------
        bar = new LinearLayout(this);
        bar.setBackgroundColor(0xff202631);
        main.addView(bar, new LinearLayout.LayoutParams(-1, dp(54)));

        bar.addView(button("←", new Runnable() {
            public void run() { if (web.canGoBack()) web.goBack(); }
        }));
        bar.addView(button("→", new Runnable() {
            public void run() { if (web.canGoForward()) web.goForward(); }
        }));
        bar.addView(button("⟳", new Runnable() {
            public void run() { web.reload(); }
        }));

        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextColor(0xffffffff);
        address.setHintTextColor(0xffbbbbbb);
        address.setHint("输入网址");
        address.setInputType(17);
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
        uaBtn = button("UA", new Runnable() {
            public void run() { switchUA(); }
        });
        bar.addView(uaBtn);
        incBtn = button("无痕", new Runnable() {
            public void run() { toggleIncognito(); }
        });
        bar.addView(incBtn);
        bar.addView(button("模式", new Runnable() {
            public void run() { switchMode(); }
        }));

        // ---------- 网页区 ----------
        stage = new FrameLayout(this);
        main.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));
        web = new WebView(this);
        stage.addView(web, new FrameLayout.LayoutParams(-1, -1));

        applyIncognito();      // 先按无痕初始化
        setupWebViewSettings();

        web.setWebViewClient(new WebViewClient() {
            public void onPageFinished(WebView v, String url) { address.setText(url); }
            public boolean shouldOverrideUrlLoading(WebView v, String url) { return false; }
        });

        web.setWebChromeClient(new WebChromeClient() {
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

            public void onHideCustomView() { hideVideo(); }

            // 无痕下拒绝定位，避免站点拿真实位置
            public void onGeolocationPermissionsShowPrompt(String origin,
                                                           GeolocationPermissions.Callback cb) {
                cb.invoke(origin, false, false);
            }
        });

        // ---------- 鼠标光标（青色箭头） ----------
        cursor = new View(this) {
            Paint p = new Paint(3);

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

        // ---------- 内置键盘 ----------
        buildKeyboard(main);

        web.loadUrl("https://www.example.com");
        web.setFocusable(false);
        hideIME();
        updateIncognitoButton();
        updateUAButton();
    }

    /** WebView 基础设置（视频播放相关） */
    void setupWebViewSettings() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // 多数视频站依赖，无痕下退出会清空
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setSaveFormData(false);
        s.setSavePassword(false);
        s.setGeolocationEnabled(false);
        //s.setAppCacheEnabled(false);
        s.setDatabaseEnabled(false);
        applyUA();
    }

    /** 应用无痕：关缓存、禁 Cookie、不写历史 */
    void applyIncognito() {
        WebSettings s = web.getSettings();
        if (incognito) {
            s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        } else {
            s.setCacheMode(WebSettings.LOAD_DEFAULT);
        }
        s.setSaveFormData(false);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(!incognito);
        try {
            CookieSyncManager.createInstance(this);
            CookieSyncManager.getInstance().sync();
        } catch (Exception ignore) {
            // Android 8 上 CookieSyncManager 已废弃，CookieManager 自身已自动 flush
        }
    }

    /** 清空一切痕迹 */
    void wipeAll() {
        web.clearCache(true);
        web.clearHistory();
        web.clearFormData();
        CookieManager.getInstance().removeAllCookie();
        WebStorage.getInstance().deleteAllData();
    }

    void toggleIncognito() {
        incognito = !incognito;
        applyIncognito();
        wipeAll();
        updateIncognitoButton();
        Toast.makeText(this, incognito ? "无痕：开（不记录任何浏览数据）" : "无痕：关（会缓存和记录）",
                Toast.LENGTH_SHORT).show();
    }

    // 顶栏子控件顺序：0← 1→ 2⟳ 3地址框 4访问 5网址 6UA 7无痕 8模式
    // 只用文字表示状态，不改背景色，避免与焦点高亮互相覆盖
    void updateIncognitoButton() {
        if (incBtn != null) {
            incBtn.setText(incognito ? "无痕✓" : "无痕✗");
        }
    }

    void switchUA() {
        uaMode = (uaMode + 1) % 3;
        applyUA();
        updateUAButton();
        web.reload();
    }

    void applyUA() {
        WebSettings s = web.getSettings();
        if (uaMode == 1) {
            s.setUserAgentString(UA_DESKTOP);
        } else if (uaMode == 2) {
            s.setUserAgentString(UA_IPAD);
        } else {
            s.setUserAgentString(null);   // 恢复系统默认
        }
    }

    void updateUAButton() {
        if (uaBtn != null) {
            uaBtn.setText(uaMode == 0 ? "UA手机" : uaMode == 1 ? "UA桌面" : "UA平板");
        }
    }

    /** 构建不依赖输入法的字符键盘 */
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
        } catch (Exception ignore) {
        }
    }

    void navigate() {
        String u = address.getText().toString().trim();
        if (u.length() == 0) return;
        if (!u.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) u = "https://" + u;
        web.loadUrl(u);
        hideIME();
        address.clearFocus();
    }

    void position() {
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
        // 输入模式：全部交给系统焦点，GridView 方向键导航即可用
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
                            k == KeyEvent.KEYCODE_DPAD_UP ? -dp(200) : k == KeyEvent.KEYCODE_DPAD_DOWN ? dp(200) : 0);
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
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
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
        wipeAll();                 // 退出即清空所有痕迹
        if (web != null) {
            web.destroy();
        }
        super.onDestroy();
    }
}
