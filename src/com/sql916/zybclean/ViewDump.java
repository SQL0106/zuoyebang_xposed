package com.sql916.zybclean;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 临时探针 2（定位完删掉）：
 *  1) 首页 IndexActivity 起来后，延迟把整个 decor View 树打出来（类名 + 资源 id 名 + contentDescription），
 *     用来定位右上角「通知」铃铛（资源名被混淆，静态查不到）。
 *  2) hook WindowManagerGlobal#addView，抓不在 decor 里的浮层（可能是 WindowManager 加的浮窗，
 *     这类浮窗不会经过 ViewGroup#addView，Probe 抓不到）。
 */
public class ViewDump {

    private static final String HOME_ACTIVITY = "com.baidu.homework.activity.index.IndexActivity";
    private static final int MAX_LINES = 220;
    private static int lines = 0;

    private static final int OVERLAY_BUDGET = 40;
    private static int overlayLeft = OVERLAY_BUDGET;

    static void install(ClassLoader cl) {
        hookHome(cl);
        hookOverlay(cl);
        HookEntry.log("ViewDump installed");
    }

    // ---------- 1. decor 树 dump ----------

    private static void hookHome(ClassLoader cl) {
        Class<?> c;
        try {
            c = Class.forName(HOME_ACTIVITY, false, cl);
        } catch (Throwable t) {
            HookEntry.log("ViewDump: home activity not found");
            return;
        }
        try {
            XposedBridge.hookAllMethods(c, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    final Object activity = param.thisObject;
                    try {
                        Object decor = XposedHelpers.callMethod(activity, "getWindow");
                        decor = XposedHelpers.callMethod(decor, "getDecorView");
                        final Object d = decor;
                        Runnable r = new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    lines = 0;
                                    HookEntry.log("viewdump start");
                                    walk(d, 0);
                                    HookEntry.log("viewdump end (" + lines + " views)");
                                } catch (Throwable t) {
                                    HookEntry.log("viewdump failed: " + t);
                                }
                            }
                        };
                        // 首页内容异步加载，5s + 12s 各 dump 一次
                        XposedHelpers.callMethod(d, "postDelayed", r, 5000L);
                        XposedHelpers.callMethod(d, "postDelayed", r, 12000L);
                    } catch (Throwable t) {
                        HookEntry.log("ViewDump post failed: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            HookEntry.log("ViewDump hook onCreate failed: " + t);
        }
    }

    private static void walk(Object v, int depth) {
        if (v == null || lines >= MAX_LINES) return;
        StringBuilder sb = new StringBuilder("viewdump ");
        for (int i = 0; i < depth; i++) sb.append(' ');
        sb.append(v.getClass().getSimpleName());
        sb.append(' ').append(idName(v));
        sb.append(" vis=").append(intOf(v, "getVisibility"));
        int w = intOf(v, "getWidth");
        int h = intOf(v, "getHeight");
        if (w > 0 || h > 0) sb.append(' ').append(w).append('x').append(h);
        String desc = strOf(v, "getContentDescription");
        if (desc != null) sb.append(" desc=").append(desc);
        HookEntry.log(sb.toString());
        lines++;

        int n = intOf(v, "getChildCount");
        for (int i = 0; i < n; i++) {
            Object child = null;
            try {
                child = XposedHelpers.callMethod(v, "getChildAt", i);
            } catch (Throwable ignored) {
            }
            walk(child, depth + 1);
        }
    }

    private static String idName(Object v) {
        try {
            int id = XposedHelpers.callMethod(v, "getId") == null ? 0
                    : (Integer) XposedHelpers.callMethod(v, "getId");
            if (id == 0) return "id=none";
            Object res = XposedHelpers.callMethod(v, "getResources");
            String name = (String) XposedHelpers.callMethod(res, "getResourceName", id);
            return "id=" + name.substring(name.indexOf('/') + 1);
        } catch (Throwable t) {
            return "id=?";
        }
    }

    private static int intOf(Object v, String m) {
        try {
            Object r = XposedHelpers.callMethod(v, m);
            return r instanceof Integer ? (Integer) r : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String strOf(Object v, String m) {
        try {
            Object r = XposedHelpers.callMethod(v, m);
            return r == null ? null : String.valueOf(r);
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------- 2. WindowManager 浮层 ----------

    private static void hookOverlay(ClassLoader cl) {
        Class<?> c;
        try {
            c = Class.forName("android.view.WindowManagerGlobal", false, cl);
        } catch (Throwable t) {
            HookEntry.log("ViewDump: WindowManagerGlobal not found");
            return;
        }
        try {
            XposedBridge.hookAllMethods(c, "addView", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (overlayLeft <= 0 || param.args == null || param.args.length == 0) return;
                    Object v = param.args[0];
                    if (v == null) return;
                    String name = v.getClass().getName();
                    if (!(name.startsWith("com.zuoyebang") || name.startsWith("com.baidu")
                            || name.startsWith("com.zybang") || name.startsWith("com.homework"))) return;
                    overlayLeft--;
                    String type = "?";
                    try {
                        type = String.valueOf(XposedHelpers.getIntField(param.args[1], "type"));
                    } catch (Throwable ignored) {
                    }
                    HookEntry.log("overlay addView: " + name + " type=" + type);
                }
            });
            HookEntry.log("ViewDump overlay hook installed");
        } catch (Throwable t) {
            HookEntry.log("ViewDump overlay hook failed: " + t);
        }
    }
}
