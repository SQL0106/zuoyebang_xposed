package io.github.sql0106.zybclean;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import io.github.sql0106.zybclean.compat.XC_MethodHook;
import io.github.sql0106.zybclean.compat.XposedBridge;
import io.github.sql0106.zybclean.compat.XposedHelpers;

/**
 * 运行时「快照」探针（仿 GKD 的 snapshot + 选择器思路，当眼睛用）。
 *
 * 用法：root 侧 touch 触发文件，模块在 App 进程内把当前窗口的 View 树
 * （类名 / 资源 id / 可见性 / 屏幕坐标 / 文本 / contentDescription）写成文本，
 * 我这边 cat 出来就能知道「屏幕上这个东西到底是什么控件」。
 *
 *   触发：/data/data/com.zuoyebang.knowledge/files/.zyb_snap
 *   输出：/data/data/com.zuoyebang.knowledge/files/.zyb_snap.txt
 *
 * 选 /data/data 内部目录：root 能写（SELinux unconfined），App 自己也能读写，
 * 不依赖 sdcard FUSE 权限、不需要任何存储权限、不需要 Context。
 *
 * 定位清楚后可以整个删掉，或在 HookEntry 里注释掉 Snap.install。
 */
public final class Snap {

    private static final String TRIGGER = "/data/data/com.zuoyebang.knowledge/files/.zyb_snap";
    private static final String OUT = "/data/data/com.zuoyebang.knowledge/files/.zyb_snap.txt";

    private static final int MAX_LINES = 400;
    private static final int MAX_OVERLAYS = 40;

    private static volatile Object sActivity;
    /** 最近 resume 过的 Activity（去重、上限 8 个）。开屏页会把栈顶占住，所以要按「谁有窗口焦点」挑。 */
    private static final List<Object> ACTIVITIES = new ArrayList<Object>();
    private static final List<Object> OVERLAYS = new ArrayList<Object>();

    public static void install(final ClassLoader cl) {
        // 记录最近一次 resume 的 Activity，用来取 decorView
        // 注意：不能用 XposedHelpers.findAndHookMethod(String, ClassLoader, String, Object...)
        // —— LSPosed 会在运行时把 API 包名混淆且该重载不存在，会炸 NoSuchMethodError。
        XposedBridge.hookAllMethods(XposedHelpers.findClass("android.app.Activity", cl), "onResume",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        sActivity = param.thisObject;
                        synchronized (ACTIVITIES) {
                            for (Object a : ACTIVITIES) {
                                if (a == param.thisObject) return;
                            }
                            ACTIVITIES.add(param.thisObject);
                            while (ACTIVITIES.size() > 8) ACTIVITIES.remove(0);
                        }
                    }
                });

        // WindowManager 直接加的浮层不在 decorView 树里（「讲解中」这种很可能就是），单独记下来。
        // addView 有两个重载 (View, LayoutParams) / (View, LayoutParams, Display)，必须 hookAllMethods。
        try {
            Class<?> wmg = Class.forName("android.view.WindowManagerGlobal", false, cl);
            XposedBridge.hookAllMethods(wmg, "addView", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length < 1) return;
                    Object v = param.args[0];
                    if (v == null) return;
                    String cn = v.getClass().getName();
                    // 只留业务浮层，系统 DecorView/Toast 之类丢掉
                    if (!(cn.startsWith("com.zuoyebang.") || cn.startsWith("com.baidu.")
                            || cn.startsWith("com.zybang.") || cn.startsWith("com.homework."))) {
                        return;
                    }
                    synchronized (OVERLAYS) {
                        OVERLAYS.add(v);
                        while (OVERLAYS.size() > MAX_OVERLAYS) OVERLAYS.remove(0);
                    }
                    HookEntry.log("snap overlay added: " + cn);
                }
            });
        } catch (Throwable t) {
            HookEntry.log("Snap: windowManager hook failed: " + t);
        }

        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(300L);
                        File f = new File(TRIGGER);
                        if (!f.exists()) continue;
                        f.delete();
                        Object act = pickActivity();
                        if (act == null) {
                            write("SNAP error: no activity resumed yet");
                            continue;
                        }
                        long t0 = System.currentTimeMillis();
                        String text = dump(act);
                        write(text);
                        HookEntry.log("snap written, " + (System.currentTimeMillis() - t0) + "ms");
                    } catch (Throwable t) {
                        // 别让探针线程死掉
                    }
                }
            }
        }, "zyb-snap");
        th.setDaemon(true);
        th.start();

        HookEntry.log("Snap installed, trigger=" + TRIGGER);
    }

    /** 优先取「当前有窗口焦点」的 Activity（开屏页 / 已销毁页不该被 dump）。 */
    private static Object pickActivity() {
        synchronized (ACTIVITIES) {
            for (int i = ACTIVITIES.size() - 1; i >= 0; i--) {
                Object a = ACTIVITIES.get(i);
                try {
                    Object f = XposedHelpers.callMethod(a, "hasWindowFocus");
                    if (Boolean.TRUE.equals(f)) return a;
                } catch (Throwable ignored) {
                }
            }
        }
        return sActivity;
    }

    private static void write(String text) {
        try {
            FileWriter w = new FileWriter(OUT, false);
            w.write(text);
            w.close();
        } catch (Throwable t) {
            HookEntry.log("Snap write failed: " + t);
        }
    }

    private static String dump(Object activity) {
        StringBuilder sb = new StringBuilder(32 * 1024);
        sb.append("SNAP activity=").append(activity.getClass().getName()).append('\n');
        try {
            Object window = XposedHelpers.callMethod(activity, "getWindow");
            Object decor = XposedHelpers.callMethod(window, "getDecorView");
            int[] n = new int[]{0};
            walk(decor, 0, sb, n);
            sb.append("SNAP decor views=").append(n[0]).append('\n');
        } catch (Throwable t) {
            sb.append("SNAP decor error: ").append(t).append('\n');
        }
        // WindowManager 浮层
        List<Object> copy;
        synchronized (OVERLAYS) {
            copy = new ArrayList<Object>(OVERLAYS);
        }
        for (int i = 0; i < copy.size(); i++) {
            Object v = copy.get(i);
            try {
                if (v == null) continue;
                try {
                    Object root = XposedHelpers.callMethod(v, "getRootView");
                    sb.append("SNAP overlay#").append(i).append(' ').append(v.getClass().getName()).append('\n');
                    int[] n = new int[]{0};
                    walk(root == null ? v : root, 1, sb, n);
                } catch (Throwable t) {
                    sb.append("SNAP overlay#").append(i).append(" error ").append(t).append('\n');
                }
            } catch (Throwable ignored) {
            }
        }
        return sb.toString();
    }

    private static void walk(Object v, int depth, StringBuilder sb, int[] count) {
        if (v == null || count[0] >= MAX_LINES) return;
        count[0]++;
        if (depth > 0) {
            for (int i = 0; i < depth; i++) sb.append("  ");
        }
        try {
            Class<?> c = v.getClass();
            sb.append(c.getSimpleName());
            // 资源 id 名（混淆名也给，至少能对齐）
            try {
                int id = XposedHelpers.getIntField(v, "mID");
                if (id != 0) {
                    try {
                        Object res = XposedHelpers.callMethod(v, "getResources");
                        String name = (String) XposedHelpers.callMethod(res, "getResourceEntryName", id);
                        sb.append(" id=").append(name);
                    } catch (Throwable t) {
                        sb.append(" id=0x").append(Integer.toHexString(id));
                    }
                }
            } catch (Throwable ignored) {
            }
            // 可见性
            try {
                int vis = XposedHelpers.getIntField(v, "mViewFlags");
                int visibility = vis & 0xC; // VISIBILITY_MASK
                sb.append(" vis=").append(visibility == 0 ? "V" : (visibility == 4 ? "IV" : "G"));
            } catch (Throwable ignored) {
            }
            // 屏幕坐标
            try {
                int[] loc = new int[]{0, 0};
                XposedHelpers.callMethod(v, "getLocationOnScreen", loc);
                int w = XposedHelpers.getIntField(v, "mRight") - XposedHelpers.getIntField(v, "mLeft");
                int h = XposedHelpers.getIntField(v, "mBottom") - XposedHelpers.getIntField(v, "mTop");
                sb.append(" box=").append(loc[0]).append(',').append(loc[1])
                        .append('-').append(loc[0] + w).append(',').append(loc[1] + h);
            } catch (Throwable ignored) {
            }
            // 文本 / 描述
            String txt = textOf(v);
            if (txt != null && txt.length() > 0) sb.append(" text=").append(clip(txt));
            try {
                Object desc = XposedHelpers.callMethod(v, "getContentDescription");
                if (desc != null && desc.toString().length() > 0) sb.append(" desc=").append(clip(desc.toString()));
            } catch (Throwable ignored) {
            }
            sb.append('\n');
            Object group;
            try {
                group = v;
                int n = (Integer) XposedHelpers.callMethod(group, "getChildCount");
                for (int i = 0; i < n && count[0] < MAX_LINES; i++) {
                    walk(XposedHelpers.callMethod(group, "getChildAt", i), depth + 1, sb, count);
                }
            } catch (Throwable ignored) {
                // 叶子
            }
        } catch (Throwable t) {
            sb.append(" error=").append(t).append('\n');
        }
    }

    private static String textOf(Object v) {
        try {
            Class<?> c = v.getClass();
            boolean textView = false;
            while (c != null) {
                if ("android.widget.TextView".equals(c.getName())) {
                    textView = true;
                    break;
                }
                c = c.getSuperclass();
            }
            if (!textView) return null;
            Object t = XposedHelpers.callMethod(v, "getText");
            return t == null ? null : t.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String clip(String s) {
        s = s.replace('\n', ' ').replace('\r', ' ');
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }
}
