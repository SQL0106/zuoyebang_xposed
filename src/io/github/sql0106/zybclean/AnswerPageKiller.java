package io.github.sql0106.zybclean;

import io.github.sql0106.zybclean.compat.XC_MethodHook;
import io.github.sql0106.zybclean.compat.XposedBridge;
import io.github.sql0106.zybclean.compat.XposedHelpers;

import java.util.HashMap;
import java.util.WeakHashMap;

/**
 * 解答 / 搜题结果页的原生部分净化（**不读任何文字**，只按结构 + 几何定位）：
 * <ul>
 *   <li>底栏 {@code AISearchBottomInputView} 里的「问小帮」按钮 —— 按全限定类名找到底栏，
 *       再在栏内的横向按钮组里取最后一个「图标 + 文字」按钮（真机 dump 里就是第 4 个）；</li>
 *   <li>历史记录页（{@code UserAskRecordActivity}）贴底 VIP 横幅 —— 全宽 + 贴屏幕底 + 高度适中；</li>
 * </ul>
 *
 * <p>只对白名单 Activity 生效（精确包/类名前缀，见 {@link #PAGE_PREFIX}），判断结果按类名缓存，
 * 避免像 1.3.0 之前那样「所有 Activity 都算命中」导致每次页面切换全树扫多趟。</p>
 */
public class AnswerPageKiller {

    /** 只扫这几类页面（类名前缀匹配，零分配；同类名结果只算一次）。 */
    private static final String[] PAGE_PREFIX = {
            "com.baidu.homework.activity.search",        // 搜题/解答页（AISearchBottomInputView 所在）
            "com.baidu.homework.activity.exam_explain",  // 原生视频讲解页
            "com.baidu.homework.activity.user.UserAskRecord", // 历史记录页
    };

    /** 同上，按类名缓存，避免每次生命周期重复 startsWith。 */
    private static final HashMap<String, Boolean> WORTH = new HashMap<String, Boolean>();

    /** 类名缓存上限，防止动态类名无限增长。 */
    private static final int WORTH_CACHE_MAX = 256;

    /** 白名单页面上的结构清理时机（两趟足够：底栏/浮层晚于 800ms 才布局的情况由第二趟兜底）。 */
    private static final long[] DELAYS = {800L, 3000L};

    /** 贴底横幅清理的时机（不按文字匹配，只按几何位置）。 */
    private static final long[] BANNER_DELAYS = {800L, 2500L, 5000L, 9000L};

    /** 横幅只出现在贴底附近，深度 ≤ 4 即可覆盖，遍历量降一个数量级。 */
    private static final int BANNER_DEPTH = 4;

    /** 底栏原生组件全限定类名（App 升级也不变，与 WebKiller.BOTTOM_BAR_CLASS 一致）。 */
    private static final String AI_BAR_CLASS =
            "com.baidu.homework.activity.search.newsearchv3.ai.AISearchBottomInputView";

    /** 在 decor 全树里找底栏的深度上限（实测底栏在 10~15 层）。 */
    private static final int AI_SCAN_DEPTH = 24;

    /** 已隐藏过「问小帮」入口的根 View；命中后同一页面的后续延时直接跳过。 */
    private static final WeakHashMap<Object, Boolean> AI_DONE =
            new WeakHashMap<Object, Boolean>();

    /** 已隐藏过贴底横幅的根 View；命中后 4 档延时全部短路。 */
    private static final WeakHashMap<Object, Boolean> BANNER_DONE =
            new WeakHashMap<Object, Boolean>();

    /** 复用的屏幕坐标数组（只在主线程使用，不每次 new int[2]）。 */
    private static final int[] LOC = new int[2];

    private static int probeLogs;
    private static int aiLogs;

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
            // 只挂 onResume：onCreate 时 decor 尚未布局，排的延时和 onResume 完全重复。
            XposedBridge.hookAllMethods(activity, "onResume", hook);
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
        Boolean cached;
        synchronized (WORTH) {
            cached = WORTH.get(name);
        }
        if (cached != null) return cached.booleanValue();
        boolean hit = false;
        for (String p : PAGE_PREFIX) {
            if (name.startsWith(p)) {
                hit = true;
                break;
            }
        }
        synchronized (WORTH) {
            if (WORTH.size() < WORTH_CACHE_MAX) WORTH.put(name, Boolean.valueOf(hit));
        }
        return hit;
    }

    private static void schedule(Object activity) {
        try {
            if (!(activity instanceof android.app.Activity)) return;
            android.view.View decor = ((android.app.Activity) activity).getWindow().getDecorView();
            if (decor == null) return;
            final android.view.View root = decor;
            for (long delay : DELAYS) {
                root.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            scanAiEntry(root);
                        } catch (Throwable t) {
                            HookEntry.log("AnswerPageKiller: ai scan failed: " + t);
                        }
                    }
                }, delay);
            }
        } catch (Throwable t) {
            HookEntry.log("AnswerPageKiller: schedule failed: " + t);
        }
    }

    /* ---------------- 底栏「问小帮」：按类名 + 结构定位，不读文字 ---------------- */

    private static void scanAiEntry(android.view.View root) {
        if (root == null || AI_DONE.containsKey(root)) return;
        android.view.View bar = findAiBar(root, 0);
        if (bar == null) {
            if (aiLogs < 3) {
                aiLogs++;
                HookEntry.log("AnswerPageKiller: probe no ai bar");
            }
            return;
        }
        android.view.View target = lastIconLabelButton(bar, bar.getWidth(), 0);
        if (target == null) {
            if (aiLogs < 3) {
                aiLogs++;
                HookEntry.log("AnswerPageKiller: probe ai bar without button group "
                        + safeName(bar));
            }
            return;
        }
        hide(target);
        AI_DONE.put(root, Boolean.TRUE);
        HookEntry.log("AnswerPageKiller: hid ai entry " + safeName(target)
                + " w=" + target.getWidth() + " h=" + target.getHeight());
    }

    private static android.view.View findAiBar(android.view.View v, int depth) {
        if (v == null || depth > AI_SCAN_DEPTH) return null;
        if (AI_BAR_CLASS.equals(v.getClass().getName())) return v;
        if (!(v instanceof android.view.ViewGroup)) return null;
        android.view.ViewGroup g = (android.view.ViewGroup) v;
        for (int i = 0, n = g.getChildCount(); i < n; i++) {
            android.view.View r = findAiBar(g.getChildAt(i), depth + 1);
            if (r != null) return r;
        }
        return null;
    }

    /**
     * 在底栏内部找「图标 + 文字」横向按钮组（同类等高、每个子项都含 ImageView + TextView），
     * 返回组内最后一个可见子项（最右 = 问小帮位）。不读文字、不看资源 id（全 App 都是 zyb）。
     */
    private static android.view.View lastIconLabelButton(android.view.View v, int barW, int depth) {
        if (v == null || depth > 8 || !(v instanceof android.view.ViewGroup)) return null;
        android.view.ViewGroup g = (android.view.ViewGroup) v;
        int n = g.getChildCount();
        android.view.View lastVisible = null;
        int refHeight = -1;
        int visible = 0;
        boolean sameHeight = true;
        boolean iconAndLabel = true;
        for (int i = 0; i < n; i++) {
            android.view.View c = g.getChildAt(i);
            if (c == null || c.getVisibility() != android.view.View.VISIBLE) continue;
            visible++;
            lastVisible = c;
            if (refHeight < 0) {
                refHeight = c.getHeight();
            } else if (c.getHeight() != refHeight) {
                sameHeight = false;
            }
            if (!hasDescendantNamed(c, "ImageView", 2)
                    || !hasDescendantNamed(c, "TextView", 2)) {
                iconAndLabel = false;
            }
        }
        android.view.View best = null;
        if (visible >= 2 && sameHeight && iconAndLabel && refHeight > 0
                && lastVisible != null && lastVisible.getWidth() > 0
                && lastVisible.getWidth() * 2 < barW) {
            best = lastVisible;
        }
        for (int i = 0; i < n; i++) {
            android.view.View sub = lastIconLabelButton(g.getChildAt(i), barW, depth + 1);
            if (sub != null) best = sub; // 更内层的按钮组优先；同层靠右者最后覆盖
        }
        return best;
    }

    private static boolean hasDescendantNamed(android.view.View v, String suffix, int depth) {
        if (v == null) return false;
        if (v.getClass().getSimpleName().endsWith(suffix)) return true;
        if (depth <= 0 || !(v instanceof android.view.ViewGroup)) return false;
        android.view.ViewGroup g = (android.view.ViewGroup) v;
        for (int i = 0, n = g.getChildCount(); i < n; i++) {
            if (hasDescendantNamed(g.getChildAt(i), suffix, depth - 1)) return true;
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
