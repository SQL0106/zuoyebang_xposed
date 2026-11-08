package io.github.sql0106.zybclean;

import io.github.sql0106.zybclean.compat.XC_MethodHook;
import io.github.sql0106.zybclean.compat.XposedBridge;
import io.github.sql0106.zybclean.compat.XposedHelpers;

import java.util.ArrayList;
import java.util.List;

/**
 * 解答 / 搜题结果页的原生部分净化：
 * <ul>
 *   <li>底栏「问小帮」（{@code AISearchBottomInputView} 里的第 4 个按钮，见 analysis/snap4.txt
 *       第 118 行 StateTextView text=问小帮）→ 连同图标所在的 FrameLayout 一起 GONE；</li>
 *   <li>原生的「AI解题 / AI讲题」tab 或按钮 → 同上 GONE。</li>
 * </ul>
 *
 * <p>作业帮所有资源 id 都是 {@code zyb}，所以只能按文字定位，再向上找一层按钮容器隐藏。
 * 只扫名字像解答/搜题页的 Activity，避免在别的页面误伤同名文字。</p>
 */
public class AnswerPageKiller {

    private static final int GONE = 8;
    private static final long[] DELAYS = {600L, 2000L, 6000L};

    /** 需要干掉的文字（去掉空格后精确匹配）。 */
    private static final String[] KILL_TEXTS = {
            "问小帮",
            "AI解题", "AI讲题", "AI答疑",
    };

    /** 只在这些 Activity 上扫描（类名包含任一关键字，忽略大小写）。 */
    private static final String[] ACTIVITY_HINTS = {
            "Search", "Answer", "Solution", "Explain", "Question", "Homework", "NewV2",
    };

    /** 贴底横幅清理的时机（不按文字匹配，只按几何位置）。 */
    private static final long[] BANNER_DELAYS = {800L, 2500L, 5000L, 9000L};

    public static void install(final ClassLoader cl) {
        try {
            Class<?> activity = XposedHelpers.findClass("android.app.Activity", cl);
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (!worth(param.thisObject)) return;
                        schedule(param.thisObject);
                    } catch (Throwable t) {
                        HookEntry.log("AnswerPageKiller: hook failed: " + t);
                    }
                }
            };
            XposedBridge.hookAllMethods(activity, "onResume", hook);
            XposedBridge.hookAllMethods(activity, "onCreate", hook);
            HookEntry.log("AnswerPageKiller installed");
            try {
                installRecordBanner(cl);
            } catch (Throwable t) {
                HookEntry.log("AnswerPageKiller: banner install failed: " + t);
            }
        } catch (Throwable t) {
            HookEntry.log("AnswerPageKiller install failed: " + t);
        }
    }

    private static boolean worth(Object activity) {
        if (activity == null) return false;
        String name = activity.getClass().getName();
        for (String hint : ACTIVITY_HINTS) {
            if (name.toLowerCase().contains(hint.toLowerCase())) return true;
        }
        return false;
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
                            HookEntry.log("AnswerPageKiller: clean failed: " + t);
                        }
                    }
                }, Long.valueOf(delay));
            }
        } catch (Throwable t) {
            HookEntry.log("AnswerPageKiller: schedule failed: " + t);
        }
    }

    private static void clean(Object root) {
        List<Object> texts = new ArrayList<Object>();
        collectText(root, texts, 0);
        int hidden = 0;
        for (Object tv : texts) {
            String s = textOf(tv);
            if (s == null) continue;
            if (!isKillText(s)) continue;
            Object target = buttonOf(tv);
            if (target != null && isVisible(target)) {
                hide(target);
                hidden++;
            }
        }
        if (hidden > 0) {
            HookEntry.log("AnswerPageKiller: hid " + hidden + " item(s)");
        }
    }

    private static boolean isKillText(String raw) {
        String s = raw.replace(" ", "").replace("\u00a0", "").trim();
        for (String k : KILL_TEXTS) {
            if (s.equals(k)) return true;
        }
        return false;
    }

    /** 文字节点 → 往上找按钮容器（FrameLayout / LinearLayout / RelativeLayout）。 */
    private static Object buttonOf(Object textView) {
        Object cur = textView;
        for (int i = 0; i < 3; i++) {
            Object parent;
            try {
                parent = XposedHelpers.callMethod(cur, "getParent");
            } catch (Throwable t) {
                return null;
            }
            if (parent == null) return null;
            String simple = parent.getClass().getSimpleName();
            if (simple.contains("FrameLayout")
                    || simple.contains("LinearLayout")
                    || simple.contains("RelativeLayout")
                    || simple.contains("ConstraintLayout")) {
                return parent;
            }
            cur = parent;
        }
        return textView;
    }

    private static boolean isVisible(Object view) {
        try {
            Object v = XposedHelpers.callMethod(view, "getVisibility");
            if (v instanceof Integer && ((Integer) v).intValue() == 0) return true;
        } catch (Throwable ignored) {
        }
        // GONE 的占位节点（0x0）也一并处理，反正看不见
        return false;
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

    private static void hide(Object view) {
        try {
            XposedHelpers.callMethod(view, "setVisibility", Integer.valueOf(GONE));
        } catch (Throwable ignored) {
        }
    }

    /* ---------------- 历史记录页：贴底 VIP 横幅（按几何定位，不看文字） ---------------- */

    private static final String RECORD_ACTIVITY =
            "com.baidu.homework.activity.user.UserAskRecordActivity";

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
                    HookEntry.log("AnswerPageKiller: banner scan scheduled");
                    Object win = XposedHelpers.callMethod(param.thisObject, "getWindow");
                    if (win == null) return;
                    final Object decor = XposedHelpers.callMethod(win, "getDecorView");
                    if (decor == null) return;
                    for (long delay : BANNER_DELAYS) {
                        XposedHelpers.callMethod(decor, "postDelayed", new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    killBottomBanner(decor);
                                } catch (Throwable t) {
                                    HookEntry.log("AnswerPageKiller: banner failed: " + t);
                                }
                            }
                        }, Long.valueOf(delay));
                    }
                } catch (Throwable t) {
                    HookEntry.log("AnswerPageKiller: banner schedule failed: " + t);
                }
            }
        };
        XposedBridge.hookAllMethods(act, "onResume", hook);
        HookEntry.log("AnswerPageKiller: record banner hook ok");
    }

    private static int probeCount;

    private static void killBottomBanner(Object decor) {
        int dh = intOf(decor, "getHeight");
        int dw = intOf(decor, "getWidth");
        if (dh <= 0 || dw <= 0) {
            HookEntry.log("AnswerPageKiller: probe decor not laid out dw=" + dw + " dh=" + dh);
            return;
        }
        probeCount = 0;
        int[] base = locOf(decor);
        int hidden = walkBanner(decor, base[1], dw, dh, 0);
        if (hidden > 0) {
            HookEntry.log("AnswerPageKiller: hid " + hidden + " bottom banner(s)");
        } else {
            HookEntry.log("AnswerPageKiller: probe no hit dw=" + dw + " dh=" + dh
                    + " base=" + base[1]);
        }
    }

    /**
     * 深度优先找「整屏宽 + 贴屏幕底 + 高度不过屏高 30%」的容器并 GONE。
     * 用 {@code getLocationOnScreen} 取绝对坐标，避免被 App 自身滚动/偏移影响。
     */
    private static int walkBanner(Object v, int screenY, int dw, int dh, int depth) {
        if (v == null || depth > 40) return 0;
        int w = intOf(v, "getWidth");
        int h = intOf(v, "getHeight");
        int top = locOf(v)[1] - screenY;
        int bottom = top + h;
        boolean hit = depth > 0
                && w >= dw * 95 / 100
                && h >= 80 && h <= dh * 3 / 10
                && bottom >= dh - 6 && bottom <= dh + 6;
        if (!hit && depth > 0 && probeCount < 10
                && w >= dw * 90 / 100 && h >= 40 && bottom >= dh * 8 / 10) {
            probeCount++;
            HookEntry.log("AnswerPageKiller: probe " + safeName(v) + " w=" + w + " h=" + h
                    + " top=" + top + " bottom=" + bottom);
        }
        if (hit) {
            hideSiblingAbove(v);
            hide(v);
            HookEntry.log("AnswerPageKiller: banner " + safeName(v) + " h=" + h + " top=" + top);
            return 1;
        }
        int n = intOf(v, "getChildCount");
        int count = 0;
        for (int i = 0; i < n; i++) {
            Object child = null;
            try {
                child = XposedHelpers.callMethod(v, "getChildAt", Integer.valueOf(i));
            } catch (Throwable ignored) {
            }
            count += walkBanner(child, screenY, dw, dh, depth + 1);
        }
        return count;
    }

    /** 横幅上沿常有一条 1px 分隔线，一起收掉。 */
    private static void hideSiblingAbove(Object v) {
        try {
            Object parent = XposedHelpers.callMethod(v, "getParent");
            if (parent == null) return;
            int n = intOf(parent, "getChildCount");
            int idx = -1;
            for (int i = 0; i < n; i++) {
                Object c = XposedHelpers.callMethod(parent, "getChildAt", Integer.valueOf(i));
                if (c == v) {
                    idx = i;
                    break;
                }
            }
            if (idx <= 0) return;
            Object prev = XposedHelpers.callMethod(parent, "getChildAt", Integer.valueOf(idx - 1));
            if (prev != null && intOf(prev, "getHeight") <= 12) hide(prev);
        } catch (Throwable ignored) {
        }
    }

    private static String safeName(Object v) {
        try {
            return v.getClass().getSimpleName();
        } catch (Throwable t) {
            return "?";
        }
    }

    private static int[] locOf(Object view) {
        int[] loc = new int[2];
        try {
            XposedHelpers.callMethod(view, "getLocationOnScreen", loc);
        } catch (Throwable ignored) {
        }
        return loc;
    }

    private static int intOf(Object target, String method) {
        try {
            Object r = XposedHelpers.callMethod(target, method);
            if (r instanceof Integer) return ((Integer) r).intValue();
        } catch (Throwable ignored) {
        }
        return 0;
    }
}
