package io.github.sql0106.zybclean;

import java.lang.reflect.Method;

import io.github.sql0106.zybclean.compat.XC_MethodHook;
import io.github.sql0106.zybclean.compat.XposedBridge;

/**
 * 干掉首页的任务弹窗体系（活动/红包/签到/广告等 12 种弹窗，混淆为
 * com.baidu.homework.activity.index.dialog.{i..t}，共同基类 dialog.u）。
 * 基类 u 的 e(Activity) 是「展示」入口，直接短路。
 */
public class DialogKiller {

    private static final String BASE = "com.baidu.homework.activity.index.dialog.u";

    /**
     * 首页任务弹窗子类。必须逐个钩：它们各自 override 了 e(Activity)，
     * 只钩基类 u 的实现会被虚调用绕过（VIP 续费图片浮窗 dialog.i 就是这么漏的）。
     */
    private static final String[] TASKS = {
            "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t"
    };

    /** 需要放行的弹窗（基类子类的简单类名），留空表示全部拦截。 */
    private static final String[] ALLOW = {};

    static void install(ClassLoader cl) {
        Class<?> base;
        try {
            base = Class.forName(BASE, false, cl);
        } catch (Throwable t) {
            HookEntry.log("dialog base not found: " + BASE);
            return;
        }

        try {
            XposedBridge.hookAllMethods(base, "<init>", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    HookEntry.log("popup task created: " + param.thisObject.getClass().getName());
                }
            });
        } catch (Throwable t) {
            HookEntry.log("hook dialog <init> failed", t);
        }

        for (Method m : base.getDeclaredMethods()) {
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length != 1 || !"android.app.Activity".equals(pt[0].getName())) continue;
            block(m);
        }

        // 子类各自 override 的 e(Activity) 才是真正入口，必须逐个短路
        for (String task : TASKS) {
            try {
                Class<?> c = Class.forName("com.baidu.homework.activity.index.dialog." + task, false, cl);
                for (Method m : c.getDeclaredMethods()) {
                    Class<?>[] pt = m.getParameterTypes();
                    if (pt.length != 1 || !"android.app.Activity".equals(pt[0].getName())) continue;
                    block(m);
                }
            } catch (Throwable t) {
                HookEntry.log("hook dialog." + task + " failed", t);
            }
        }

        // dialog.i 的图片浮窗还有一条私有直通路径 s(Activity, ParentActivityActivitylayer)
        try {
            Class<?> i = Class.forName("com.baidu.homework.activity.index.dialog.i", false, cl);
            Class<?> layer = Class.forName(
                    "com.baidu.homework.common.net.model.v1.ParentActivityActivitylayer", false, cl);
            block(i.getDeclaredMethod("s", android.app.Activity.class, layer));
        } catch (Throwable t) {
            HookEntry.log("hook dialog.i#s failed", t);
        }

        // 首页新手引导浮层
        hookDialogShow(cl, "com.baidu.homework.activity.index.dialog.main_page.MainPageGuideDialog");
    }

    /** 统一短路：展示入口直接返回，不发请求、不弹窗。 */
    private static void block(Method m) {
        XposedBridge.hookMethod(m, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String name = param.thisObject.getClass().getSimpleName();
                for (String allow : ALLOW) {
                    if (allow.equals(name)) return;
                }
                HookEntry.log("block popup: " + param.thisObject.getClass().getName()
                        + "#" + ((Method) param.method).getName());
                param.setResult(null);
            }
        });
    }

    private static void hookDialogShow(ClassLoader cl, String className) {
        Class<?> c;
        try {
            c = Class.forName(className, false, cl);
        } catch (Throwable t) {
            return;
        }
        try {
            XposedBridge.hookAllMethods(c, "show", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    HookEntry.log("block dialog show: " + className);
                    param.setResult(null);
                }
            });
        } catch (Throwable t) {
            HookEntry.log("hook " + className + "#show failed", t);
        }
    }
}
