package com.z6x.browser;

import android.content.Context;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.webkit.WebView;

/**
 * 针对 Z6X 老系统(Android 9 / GMUI)的加固 WebView。
 * 老系统 WebView 在遥控器移动时可能抛空指针导致崩溃，这里兜住。
 */
public class TVWebView extends WebView {

    public TVWebView(Context context) {
        super(context);
    }

    @Override
    public PointerIcon onResolvePointerIcon(MotionEvent event, int pointerIndex) {
        try {
            return super.onResolvePointerIcon(event, pointerIndex);
        } catch (Exception e) {
            // 老系统兼容兜底：返回默认指针，避免崩溃
            try {
                return PointerIcon.getSystemIcon(getContext(), PointerIcon.TYPE_DEFAULT);
            } catch (Exception ignore) {
                return null;
            }
        }
    }
}
