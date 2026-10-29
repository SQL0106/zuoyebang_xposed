package com.sql916.zybclean;

import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * 临时探针：只为定位「搜题结果页右下角视频讲解浮卡」到底是谁加进来的。
 * 定位完就删掉（或在 HookEntry 里注释掉 install）。
 *
 * 输出（LSPosed verbose log，前缀 ZYBCLEAN）：
 *   probe activity: com.xxx.YyyActivity
 *   probe addView: com.xxx.FloatCardView <- com.xxx.ParentLayout
 *   probe fragment: com.xxx.SomeFragment
 */
public class Probe {

    /** 每个进程最多打这么多条，防止刷爆日志。 */
    private static final int BUDGET = 150;
    private static final int VIEW_BUDGET = 400;
    private static final AtomicInteger used = new AtomicInteger();
    private static final AtomicInteger viewUsed = new AtomicInteger();

    private static boolean want() {
        return used.incrementAndGet() <= BUDGET;
    }

    private static boolean wantView() {
        return viewUsed.incrementAndGet() <= VIEW_BUDGET;
    }

    /** 只关心自家业务的 View，过滤掉系统控件减少噪音。 */
    private static boolean interesting(String name) {
        if (name == null) return false;
        if (name.startsWith("com.zuoyebang.")) return true;
        if (name.startsWith("com.baidu.homework.")) return true;
        if (name.startsWith("com.zybang.")) return true;
        if (name.startsWith("com.homework.")) return true;
        return name.contains("Video") || name.contains("Float") || name.contains("Explain");
    }

    static void install(ClassLoader cl) {
        hookActivity(cl);
        hookAddView(cl);
        hookFragment(cl);
        HookEntry.log("Probe installed (budget " + BUDGET + ")");
    }

    private static void hookActivity(ClassLoader cl) {
        try {
            Class<?> activity = Class.forName("android.app.Activity", false, cl);
            XposedBridge.hookAllMethods(activity, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object self = param.thisObject;
                    if (self == null || !want()) return;
                    HookEntry.log("probe activity: " + self.getClass().getName());
                }
            });
        } catch (Throwable t) {
            HookEntry.log("probe activity failed", t);
        }
    }

    private static void hookAddView(ClassLoader cl) {
        try {
            Class<?> vg = Class.forName("android.view.ViewGroup", false, cl);
            XposedBridge.hookAllMethods(vg, "addView", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length == 0) return;
                    Object child = param.args[0];
                    if (child == null) return;
                    String childName = child.getClass().getName();
                    if (!interesting(childName)) return;
                    if (!wantView()) return;
                    Object parent = param.thisObject;
                    String parentName = parent == null ? "?" : parent.getClass().getName();
                    HookEntry.log("probe addView: " + childName + " <- " + parentName);
                }
            });
        } catch (Throwable t) {
            HookEntry.log("probe addView failed", t);
        }
    }

    private static void hookFragment(ClassLoader cl) {
        String[] names = {
                "androidx.fragment.app.Fragment",
                "android.app.Fragment",
        };
        for (String n : names) {
            try {
                Class<?> f = Class.forName(n, false, cl);
                XposedBridge.hookAllMethods(f, "onViewCreated", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object self = param.thisObject;
                        if (self == null || !want()) return;
                        HookEntry.log("probe fragment: " + self.getClass().getName());
                    }
                });
            } catch (Throwable ignored) {
                // 某些 ROM 只有一个 Fragment 实现，缺哪个都正常
            }
        }
    }
}
