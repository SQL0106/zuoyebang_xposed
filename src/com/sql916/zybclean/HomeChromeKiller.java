package com.sql916.zybclean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页顶栏净化：去掉搜索框（跑马灯热词）、铃铛（通知入口）、红色角标。
 *
 * 依据真机快照（analysis/snap3.txt）：
 *   RelativeLayout(标题栏)
 *     ├─ RelativeLayout  年级切换
 *     ├─ RelativeLayout  ← 搜索框容器（内含 XMarqueeView 跑马灯热词）→ 整个 GONE
 *     └─ RelativeLayout
 *          ├─ StateImageView  铃铛（左，通知）
 *          ├─ StateImageView  扫码（右，保留）
 *          ├─ BadgeTextView   红点 ×2
 *
 * 注意：作业帮所有资源 id 都被混淆成同一个名字 `zyb`，只能按控件类名 + 兄弟顺序定位。
 */
public class HomeChromeKiller {

    private static final String INDEX = "com.baidu.homework.activity.index.IndexActivity";
    private static final String BADGE = "com.zuoyebang.design.badge.BadgeTextView";
    private static final long[] DELAYS = {600L, 1500L, 3000L, 6000L, 12000L};
    private static final int GONE = 8;

    /**
     * 首页「垃圾卡片」——按可见文字定位（id 全混淆成 zyb，只能靠文字）。
     * 命中后向上找卡片容器（ConstraintLayout 且够高）整块 GONE。
     */
    private static final String[] JUNK_TEXTS = {
            "绑定作业帮APP账号",   // 账号推广卡：立即绑定 / 随时了解孩子学习进度
    };

    public static void install(final ClassLoader cl) {
        try {
            Class<?> index = XposedHelpers.findClass(INDEX, cl);
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    schedule(param.thisObject);
                }
            };
            XposedBridge.hookAllMethods(index, "onCreate", hook);
            XposedBridge.hookAllMethods(index, "onResume", hook);
        } catch (Throwable t) {
            HookEntry.log("HomeChromeKiller: index activity hook failed: " + t);
        }

        // 红点角标：所有 BadgeTextView 一律不可见（含底部 tab 红点、铃铛红点）
        try {
            Class<?> badge = XposedHelpers.findClass(BADGE, cl);
            XposedBridge.hookAllMethods(badge, "setVisibility", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args != null && param.args.length > 0) {
                        param.args[0] = Integer.valueOf(GONE);
                    }
                }
            });
            HookEntry.log("HomeChromeKiller: badge suppressed");
        } catch (Throwable t) {
            HookEntry.log("HomeChromeKiller: badge hook failed: " + t);
        }

        HookEntry.log("HomeChromeKiller installed");
    }

    private static void schedule(Object activity) {
        try {
            Object win = XposedHelpers.callMethod(activity, "getWindow");
            if (win == null) return;
            final Object decor = XposedHelpers.callMethod(win, "getDecorView");
            if (decor == null) return;
            for (long delay : DELAYS) {
                XposedHelpers.callMethod(decor, "postDelayed", new Runnable() {
                    @Override
                    public void run() {
                        try {
                            clean(decor);
                        } catch (Throwable t) {
                            HookEntry.log("HomeChromeKiller: clean failed: " + t);
                        }
                    }
                }, Long.valueOf(delay));
            }
        } catch (Throwable t) {
            HookEntry.log("HomeChromeKiller: schedule failed: " + t);
        }
    }

    private static void clean(Object root) {
        hideJunkCards(root);

        List<Object> marquees = new ArrayList<Object>();
        List<Object> stateIcons = new ArrayList<Object>();
        collect(root, marquees, stateIcons, 0);
        if (marquees.isEmpty() && stateIcons.isEmpty()) return;

        // 1) 搜索框：跑马灯热词的父容器（只到父级，避免连带右侧铃铛/扫码一起没）
        for (Object m : marquees) {
            Object parent = XposedHelpers.callMethod(m, "getParent");
            if (parent != null) {
                hide(parent);
                HookEntry.log("HomeChromeKiller: hide search box");
            }
        }

        // 2) 铃铛：同一父容器里最左边的 StateImageView（右边那个是扫码，保留）
        if (stateIcons.size() >= 2) {
            Object leftMost = null;
            int minX = Integer.MAX_VALUE;
            for (Object v : stateIcons) {
                int x = leftOf(v);
                if (x >= 0 && x < minX) {
                    minX = x;
                    leftMost = v;
                }
            }
            if (leftMost != null) {
                hide(leftMost);
                HookEntry.log("HomeChromeKiller: hide bell at x=" + minX);
            }
        }

        // 3) 红点：树上的 BadgeTextView 直接 GONE（setVisibility 钩子兜底新创建的）
        List<Object> badges = new ArrayList<Object>();
        collectByName(root, "BadgeTextView", badges, 0);
        for (Object b : badges) {
            hide(b);
        }
        if (!badges.isEmpty()) {
            HookEntry.log("HomeChromeKiller: hide " + badges.size() + " badge(s)");
        }
    }

    private static void collect(Object v, List<Object> marquees, List<Object> stateIcons, int depth) {
        if (v == null || depth > 60) return;
        String simple = v.getClass().getSimpleName();
        if ("XMarqueeView".equals(simple)) {
            marquees.add(v);
        } else if ("StateImageView".equals(simple)) {
            stateIcons.add(v);
        }
        int n = intOf(v, "getChildCount");
        for (int i = 0; i < n; i++) {
            Object child = null;
            try {
                child = XposedHelpers.callMethod(v, "getChildAt", Integer.valueOf(i));
            } catch (Throwable ignored) {
            }
            collect(child, marquees, stateIcons, depth + 1);
        }
    }

    /** 首页「垃圾卡片」：文字命中 JUNK_TEXTS 就整卡隐藏。 */
    private static void hideJunkCards(Object root) {
        List<Object> texts = new ArrayList<Object>();
        collectText(root, texts, 0);
        int hidden = 0;
        for (Object tv : texts) {
            String s = textOf(tv);
            if (s == null || !isJunk(s)) continue;
            Object card = findCard(tv);
            if (card != null) {
                hide(card);
                hidden++;
            }
        }
        if (hidden > 0) {
            HookEntry.log("HomeChromeKiller: hide " + hidden + " junk card(s)");
        }
    }

    private static boolean isJunk(String text) {
        for (String key : JUNK_TEXTS) {
            if (text.contains(key)) return true;
        }
        return false;
    }

    /** 从文字节点往上找卡片容器：ConstraintLayout 且高度 ≥ 300px。 */
    private static Object findCard(Object textView) {
        Object cur = textView;
        for (int i = 0; i < 6; i++) {
            Object parent;
            try {
                parent = XposedHelpers.callMethod(cur, "getParent");
            } catch (Throwable t) {
                return null;
            }
            if (parent == null) break;
            cur = parent;
            if (cur.getClass().getSimpleName().contains("ConstraintLayout") && intOf(cur, "getHeight") >= 300) {
                return cur;
            }
        }
        try {
            return XposedHelpers.callMethod(textView, "getParent");
        } catch (Throwable t) {
            return null;
        }
    }

    private static void collectText(Object v, List<Object> out, int depth) {
        if (v == null || depth > 60) return;
        if (v.getClass().getSimpleName().endsWith("TextView")) out.add(v);
        int n = intOf(v, "getChildCount");
        for (int i = 0; i < n; i++) {
            Object child = null;
            try {
                child = XposedHelpers.callMethod(v, "getChildAt", Integer.valueOf(i));
            } catch (Throwable ignored) {
            }
            collectText(child, out, depth + 1);
        }
    }

    private static String textOf(Object view) {
        try {
            Object t = XposedHelpers.callMethod(view, "getText");
            if (t != null) return t.toString().trim();
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void collectByName(Object v, String simpleName, List<Object> out, int depth) {
        if (v == null || depth > 60) return;
        if (simpleName.equals(v.getClass().getSimpleName())) out.add(v);
        int n = intOf(v, "getChildCount");
        for (int i = 0; i < n; i++) {
            Object child = null;
            try {
                child = XposedHelpers.callMethod(v, "getChildAt", Integer.valueOf(i));
            } catch (Throwable ignored) {
            }
            collectByName(child, simpleName, out, depth + 1);
        }
    }

    private static void hide(Object view) {
        try {
            XposedHelpers.callMethod(view, "setVisibility", Integer.valueOf(GONE));
        } catch (Throwable ignored) {
        }
    }

    private static int intOf(Object target, String method) {
        try {
            Object r = XposedHelpers.callMethod(target, method);
            if (r instanceof Integer) return ((Integer) r).intValue();
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static int leftOf(Object view) {
        try {
            int[] loc = new int[2];
            XposedHelpers.callMethod(view, "getLocationOnScreen", loc);
            return loc[0];
        } catch (Throwable t) {
            return -1;
        }
    }
}
