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

    /** 复用的屏幕坐标数组（只在主线程使用，不每次 new int[2]）。 */
    private static final int[] LOC = new int[2];

    private static int probeLogs;

    public static void install(final ClassLoader cl) {
        try {
            installRecordBanner(cl);
        } catch (Throwable t) {
            HookEntry.log("AnswerPageKiller install failed: " + t);
        }
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
                } catch (Throwable t) {
                    HookEntry.log("AnswerPageKiller: banner schedule failed: " + t);
                }
            }
        };
        XposedBridge.hookAllMethods(act, "onResume", hook);
        HookEntry.log("AnswerPageKiller: record banner hook ok");
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
