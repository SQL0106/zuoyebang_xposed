package io.github.sql0106.zybclean;

import io.github.sql0106.zybclean.compat.XC_MethodHook;
import io.github.sql0106.zybclean.compat.XposedBridge;
import io.github.sql0106.zybclean.compat.XposedHelpers;

import java.util.WeakHashMap;

/**
 * 历史记录页（{@code UserAskRecordActivity}）的原生净化：贴底 VIP 横幅按几何定位隐藏。
 *
 * <p>**不读任何文字**，也不碰底栏「问小帮」等原生按钮——那些是用户要保留的。</p>
 */
public class AnswerPageKiller {

    /** 贴底横幅清理的时机（不按文字匹配，只按几何位置）。 */
    private static final long[] BANNER_DELAYS = {800L, 2500L, 5000L, 9000L};

    /** 横幅只出现在贴底附近，深度 ≤ 4 即可覆盖，遍历量降一个数量级。 */
    private static final int BANNER_DEPTH = 4;

    /** 已隐藏过贴底横幅的根 View；命中后 4 档延时全部短路。 */
    private static final WeakHashMap<Object, Boolean> BANNER_DONE =
            new WeakHashMap<Object, Boolean>();

    /**
     * 每个 decor 最近一次的「浅层结构签名」（深度 ≤3 的 childCount 哈希）。
     * 用来在 onGlobalLayout 里做 O(十几个节点) 的比较：只有树形发生变化（横幅被插进来）
     * 才跑一次真正的横幅扫描。这样既能抓住 9s 之后才懒加载的横幅，又不会每次 layout 都全树遍历。
     */
    private static final WeakHashMap<android.view.View, int[]> SHAPE =
            new WeakHashMap<android.view.View, int[]>();

    /** 复用的屏幕坐标数组（只在主线程使用，不每次 new int[2]）。 */
    private static final int[] LOC = new int[2];

    private static int probeLogs;

    public static void install(final ClassLoader cl) {
        try {
            installAskHelpKiller(cl);
        } catch (Throwable t) {
            HookEntry.log("AnswerPageKiller: ask-help install failed: " + t);
        }
        try {
            installRecordBanner(cl);
        } catch (Throwable t) {
            HookEntry.log("AnswerPageKiller install failed: " + t);
        }
    }

    /* ---------------- 搜题/解答页底栏：「问小帮」原生入口（先按类名，再按结构/固定文案） ---------------- */

    /** 底栏本体（真机 dumpsys 实测 14.34.0）：AISearchBottomInputView，整条 1280x210。
     *  用专门类名直接 hook，底栏什么时候 inflate/复用到窗口上都能抓到，不再依赖 Activity.onResume + 定时扫。 */
    private static final String BOTTOM_BAR_CLASS =
            "com.baidu.homework.activity.search.newsearchv3.ai.AISearchBottomInputView";
    /** 蓝色按钮容器（839x154）：问小帮是它的兄弟子项，蓝按钮是另一个子项。 */
    private static final int ID_BLUE_WRAP = 0x7f090be3;

    /** 蓝色按钮可点击层：这个必须留着（长按它唤回浮窗）。 */
    private static final int ID_BLUE_TAP = 0x7f0911f7;

    /** 底栏里「问小帮」的固定产品名（多年不变；只在底栏这棵小子树里比对）。 */
    private static final String ASK_TEXT = "问小帮";

    private static void installAskHelpKiller(ClassLoader cl) {
        Class<?> bar;
        try {
            bar = XposedHelpers.findClass(BOTTOM_BAR_CLASS, cl);
        } catch (Throwable t) {
            HookEntry.log("AnswerPageKiller: bottom bar class not found");
            return;
        }
        java.lang.reflect.Constructor<?>[] ctors = bar.getDeclaredConstructors();
        for (java.lang.reflect.Constructor<?> ctor : ctors) {
            try {
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object o = param.thisObject;
                        if (!(o instanceof android.view.View)) return;
                        final android.view.View v = (android.view.View) o;
                        v.addOnAttachStateChangeListener(
                                new android.view.View.OnAttachStateChangeListener() {
                                    @Override
                                    public void onViewAttachedToWindow(android.view.View view) {
                                        hideAskHelpInBar(view);
                                    }

                                    @Override
                                    public void onViewDetachedFromWindow(android.view.View view) {
                                    }
                                });
                        // 底栏内部子项可能比底栏本身晚一步 inflate，补一次 post 兜底。
                        v.post(new Runnable() {
                            @Override
                            public void run() {
                                hideAskHelpInBar(v);
                            }
                        });
                    }
                });
            } catch (Throwable t) {
                HookEntry.log("AnswerPageKiller: bottom bar hook failed: " + t);
            }
        }
        HookEntry.log("AnswerPageKiller: ask-help hooked on " + BOTTOM_BAR_CLASS);
    }

    /**
     * 在底栏子树里藏「问小帮」。两条规则，命中任意一条即可：
     * <ol>
     *   <li>结构规则：底栏里带 {@link #ID_BLUE_WRAP} 的容器中，除带 {@link #ID_BLUE_TAP} 的蓝按钮那支外，
     *       含 TextView 的子项全 GONE（拍照/预览态：问小帮与蓝色主按钮并排）；</li>
     *   <li>文案规则：子树里文字恰好是「问小帮」的 TextView，向上找它的按钮容器（FrameLayout）GONE
     *       （搜题结果态：分享 / 加错题本 / 问小帮 / 再拍一页 四个平级按钮）。</li>
     * </ol>
     * 只在底栏这棵十几到几十个节点的小树里走，不涉及整页遍历。
     */
    private static boolean hideAskHelpInBar(android.view.View bar) {
        if (bar == null) return false;
        int hidden = 0;

        android.view.View wrap = null;
        try {
            wrap = bar.findViewById(ID_BLUE_WRAP);
        } catch (Throwable ignored) {
        }
        // 只有「蓝按钮确实在这个容器里」时才套用结构规则；否则这个 id 可能落在别处，
        // 会把分享/加错题本等正常按钮一起藏掉（真机搜题态实测过）。
        if (wrap instanceof android.view.ViewGroup && wrap.findViewById(ID_BLUE_TAP) != null) {
            android.view.ViewGroup g = (android.view.ViewGroup) wrap;
            for (int i = 0, n = g.getChildCount(); i < n; i++) {
                android.view.View c = g.getChildAt(i);
                if (c == null) continue;
                int id;
                try {
                    id = c.getId();
                } catch (Throwable t) {
                    continue;
                }
                if (id == ID_BLUE_TAP || c.findViewById(ID_BLUE_TAP) != null) continue; // 蓝按钮本体
                if (!hasTextView(c, 2)) continue;
                if (c.getVisibility() != android.view.View.GONE) {
                    c.setVisibility(android.view.View.GONE);
                    hidden++;
                }
            }
        }

        java.util.List<android.view.View> textViews = new java.util.ArrayList<android.view.View>();
        collectText(bar, textViews, 0);
        for (android.view.View tv : textViews) {
            if (!ASK_TEXT.contentEquals(((android.widget.TextView) tv).getText())) continue;
            if (tv.getVisibility() != android.view.View.VISIBLE) continue;
            android.view.View btn = buttonOfText(tv, bar);
            // 底栏里还有几个 0×0 的占位节点也带同样的文案，别把它们（以及它们的容器）当按钮藏了。
            if (btn == null || btn == bar) continue;
            if (btn.getWidth() <= 0 || btn.getHeight() <= 0) continue;
            if (btn.getVisibility() != android.view.View.GONE) {
                btn.setVisibility(android.view.View.GONE);
                hidden++;
            }
        }

        if (hidden > 0) {
            HookEntry.log("AnswerPageKiller: hid " + hidden + " ask-help entry(ies)");
        }
        return hidden > 0;
    }

    private static void collectText(android.view.View v, java.util.List<android.view.View> out, int depth) {
        if (v == null || depth > 12) return;
        if (v instanceof android.widget.TextView) out.add(v);
        if (!(v instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup g = (android.view.ViewGroup) v;
        for (int i = 0, n = g.getChildCount(); i < n; i++) {
            collectText(g.getChildAt(i), out, depth + 1);
        }
    }

    /** 文字节点 → 它的按钮容器：向上最多 3 层，取第一个 FrameLayout/LinearLayout/RelativeLayout。 */
    private static android.view.View buttonOfText(android.view.View tv, android.view.View stop) {
        android.view.View cur = tv;
        for (int i = 0; i < 3; i++) {
            Object p = cur.getParent();
            if (!(p instanceof android.view.View)) return null;
            cur = (android.view.View) p;
            if (cur == stop) return null;
            String s = cur.getClass().getSimpleName();
            if (s.contains("FrameLayout") || s.contains("LinearLayout") || s.contains("RelativeLayout")) {
                return cur;
            }
        }
        return null;
    }

    private static boolean hasTextView(android.view.View v, int depth) {
        if (v == null) return false;
        if (v.getClass().getSimpleName().endsWith("TextView")) return true;
        if (depth <= 0 || !(v instanceof android.view.ViewGroup)) return false;
        android.view.ViewGroup g = (android.view.ViewGroup) v;
        for (int i = 0, n = g.getChildCount(); i < n; i++) {
            if (hasTextView(g.getChildAt(i), depth - 1)) return true;
        }
        return false;
    }

    /* ---------------- 历史记录页：贴底 VIP 横幅（按几何定位，不看文字） ---------------- */

    private static final String RECORD_ACTIVITY =
            "com.baidu.homework.activity.user.UserAskRecordActivity";

    /** decor 在屏幕上的 Y 基准（横幅判定用相对 decor 的坐标）。 */
    private static int baseY;

    /** 只在「历史记录」页挂一次；该页底部横幅是全宽、贴底、高度适中的 ViewGroup。 */
    private static void installRecordBanner(ClassLoader cl) {
        // 目标 Activity 不自己声明 onResume，直接 hook 子类会落空；
        // 改为 hook Activity 的 onResume，只对目标类名放行（一次字符串比较，开销可忽略）。
        Class<?> act = XposedHelpers.findClass("android.app.Activity", cl);
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (!RECORD_ACTIVITY.equals(param.thisObject.getClass().getName())) return;
                    Object win = XposedHelpers.callMethod(param.thisObject, "getWindow");
                    if (win == null) return;
                    final Object decor = XposedHelpers.callMethod(win, "getDecorView");
                    if (!(decor instanceof android.view.View)) return;
                    final android.view.View root = (android.view.View) decor;
                    // 本页已经清过横幅 → 不再排 4 档延时。
                    if (BANNER_DONE.containsKey(root)) return;
                    HookEntry.log("AnswerPageKiller: banner scan scheduled");
                    for (long delay : BANNER_DELAYS) {
                        root.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    killBottomBanner(root);
                                } catch (Throwable t) {
                                    HookEntry.log("AnswerPageKiller: banner failed: " + t);
                                }
                            }
                        }, delay);
                    }
                    watchLayout(root);
                } catch (Throwable t) {
                    HookEntry.log("AnswerPageKiller: banner schedule failed: " + t);
                }
            }
        };
        XposedBridge.hookAllMethods(act, "onResume", hook);
        HookEntry.log("AnswerPageKiller: record banner hook ok");
    }

    /**
     * 结构变化才扫：给 decor 挂一次 onGlobalLayout，比较浅层签名。
     * 横幅是懒加载的（真机实测 9s 后才出现），固定延时抓不到；又不能每次 layout 全树遍历。
     */
    private static void watchLayout(final android.view.View root) {
        synchronized (SHAPE) {
            if (SHAPE.containsKey(root)) return;
            SHAPE.put(root, new int[]{-1});
        }
        try {
            root.getViewTreeObserver().addOnGlobalLayoutListener(
                    new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            try {
                                if (BANNER_DONE.containsKey(root)) return;
                                int sig = shapeSig(root, 0);
                                int[] last = SHAPE.get(root);
                                if (last != null && last[0] == sig) return;
                                SHAPE.put(root, new int[]{sig});
                                killBottomBanner(root);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable t) {
            HookEntry.log("AnswerPageKiller: layout watch failed: " + t);
        }
    }

    /** 浅层结构签名：只统计深度 ≤3 的子树形状（childCount），十几次虚调用。 */
    private static int shapeSig(android.view.View v, int depth) {
        if (v == null || depth > 3) return 0;
        if (!(v instanceof android.view.ViewGroup)) return 1;
        android.view.ViewGroup g = (android.view.ViewGroup) v;
        int s = g.getChildCount() * 31 + 7;
        for (int i = 0, n = g.getChildCount(); i < n; i++) {
            s = s * 31 + shapeSig(g.getChildAt(i), depth + 1);
        }
        return s;
    }

    private static void killBottomBanner(android.view.View root) {
        if (root == null || BANNER_DONE.containsKey(root)) return;
        int dh = root.getHeight();
        int dw = root.getWidth();
        if (dh <= 0 || dw <= 0) {
            if (probeLogs < 3) {
                probeLogs++;
                HookEntry.log("AnswerPageKiller: probe decor not laid out dw=" + dw + " dh=" + dh);
            }
            return;
        }
        baseY = screenY(root);
        int hidden = walkBanner(root, 0, dw, dh, 0);
        if (hidden > 0) {
            BANNER_DONE.put(root, Boolean.TRUE);
            HookEntry.log("AnswerPageKiller: hid " + hidden + " bottom banner(s)");
        } else if (probeLogs < 3) {
            probeLogs++;
            HookEntry.log("AnswerPageKiller: probe no hit dw=" + dw + " dh=" + dh);
        }
    }

    /**
     * 深度优先找「整屏宽 + 贴屏幕底 + 高度不过屏高 30%」的容器并 GONE。
     * 坐标用父子累计偏移算（每节点只 getTop/getScrollY），只有全宽候选才对
     * {@link #LOC} 做一次真实坐标校准；深度限制 {@link #BANNER_DEPTH}。
     */
    private static int walkBanner(android.view.View v, int offsetTop, int dw, int dh, int depth) {
        if (v == null || depth > BANNER_DEPTH) return 0;
        int w = v.getWidth();
        int h = v.getHeight();
        int top = offsetTop;
        boolean wide = w >= dw * 95 / 100;
        if (depth > 0 && wide && h > 0) {
            // 全宽候选才取屏幕坐标（复用 LOC，不分配数组）
            top = screenY(v) - baseY;
        }
        int bottom = top + h;
        boolean hit = depth > 0 && wide && h >= 80 && h <= dh * 3 / 10
                && bottom >= dh - 6 && bottom <= dh + 6;
        if (!hit && depth > 0 && probeLogs < 3
                && w >= dw * 90 / 100 && h >= 40 && bottom >= dh * 8 / 10) {
            probeLogs++;
            HookEntry.log("AnswerPageKiller: probe " + safeName(v) + " w=" + w + " h=" + h
                    + " top=" + top + " bottom=" + bottom);
        }
        if (hit) {
            hideSiblingAbove(v);
            hide(v);
            HookEntry.log("AnswerPageKiller: banner " + safeName(v) + " h=" + h + " top=" + top);
            return 1;
        }
        int count = 0;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            int childTop = top - v.getScrollY();
            for (int i = 0, n = g.getChildCount(); i < n; i++) {
                android.view.View c = g.getChildAt(i);
                if (c == null) continue;
                count += walkBanner(c, childTop + c.getTop(), dw, dh, depth + 1);
            }
        }
        return count;
    }

    /** 横幅上沿常有一条 1px 分隔线，一起收掉。 */
    private static void hideSiblingAbove(android.view.View v) {
        try {
            Object parent = v.getParent();
            if (!(parent instanceof android.view.ViewGroup)) return;
            android.view.ViewGroup g = (android.view.ViewGroup) parent;
            int n = g.getChildCount();
            int idx = -1;
            for (int i = 0; i < n; i++) {
                if (g.getChildAt(i) == v) {
                    idx = i;
                    break;
                }
            }
            if (idx <= 0) return;
            android.view.View prev = g.getChildAt(idx - 1);
            if (prev != null && prev.getHeight() <= 12) hide(prev);
        } catch (Throwable ignored) {
        }
    }

    private static String safeName(android.view.View v) {
        try {
            return v.getClass().getSimpleName();
        } catch (Throwable t) {
            return "?";
        }
    }

    /** 屏幕 Y 坐标，复用 {@link #LOC}（只在主线程调用）。 */
    private static int screenY(android.view.View v) {
        try {
            v.getLocationOnScreen(LOC);
            return LOC[1];
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void hide(android.view.View v) {
        try {
            v.setVisibility(android.view.View.GONE);
        } catch (Throwable ignored) {
        }
    }
}
