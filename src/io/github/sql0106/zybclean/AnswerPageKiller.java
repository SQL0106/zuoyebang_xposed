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

    private static int intOf(Object target, String method) {
        try {
            Object r = XposedHelpers.callMethod(target, method);
            if (r instanceof Integer) return ((Integer) r).intValue();
        } catch (Throwable ignored) {
        }
        return 0;
    }
}
