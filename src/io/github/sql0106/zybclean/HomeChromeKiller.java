package io.github.sql0106.zybclean;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

import io.github.sql0106.zybclean.compat.XC_MethodHook;
import io.github.sql0106.zybclean.compat.XposedBridge;
import io.github.sql0106.zybclean.compat.XposedHelpers;

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
 * 只按「类名 / 几何位置」判断，不做任何文字匹配（文案会变，且会误伤同名按钮）。
 */
public class HomeChromeKiller {

    private static final String INDEX = "com.baidu.homework.activity.index.IndexActivity";
    private static final String BADGE = "com.zuoyebang.design.badge.BadgeTextView";

    /**
     * 首页 / 我的页「垃圾卡片」里**不会变的固定文案**（用户 2026-11-08 明确：稳定的文案可以按文字匹配，
     * 有专门类名的优先按类名）。命中后向上找卡片容器整块 GONE。
     */
    private static final String[] JUNK_TEXTS = {
            "绑定作业帮APP账号",   // 账号推广卡：立即绑定 / 随时了解孩子学习进度
    };

    /** 首屏数据可能晚到，所以只留「首次 + 1 次兜底」两趟（后续生命周期回调直接跳过）。 */
    private static final long[] DELAYS = {600L, 2500L};
    private static final int MAX_PASSES = 2;
    private static final int MAX_DEPTH = 60;
    private static final int GONE = View.GONE;

    /**
     * 每个 decorView 已清过的次数（弱键：Activity 销毁后 key 可被回收）。
     * 首页 decor 底下挂着整个信息流 RecyclerView，重复全树代价太高。
     */
    private static final WeakHashMap<View, int[]> PASSES = new WeakHashMap<View, int[]>();

    public static void install(ClassLoader cl) {
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
            if (!(decor instanceof View)) return;
            if (!hasPassLeft((View) decor)) return;         // 已经清够两趟，不必再排定时器
            for (long delay : DELAYS) {
                XposedHelpers.callMethod(decor, "postDelayed", new Runnable() {
                    @Override
                    public void run() {
                        try {
                            clean((View) decor);
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

    private static boolean hasPassLeft(View root) {
        synchronized (PASSES) {
            int[] n = PASSES.get(root);
            return n == null || n[0] < MAX_PASSES;
        }
    }

    /** 占用一次清理额度；返回 false 表示这个 decor 已经不缺清理了。 */
    private static boolean markPass(View root) {
        synchronized (PASSES) {
            int[] n = PASSES.get(root);
            if (n == null) {
                n = new int[]{0};
                PASSES.put(root, n);
            }
            if (n[0] >= MAX_PASSES) return false;
            n[0]++;
            return true;
        }
    }

    /** 单趟遍历收集，循环结束后统一设置（原先是三趟全树 + 边遍历边写）。 */
    private static void clean(View root) {
        if (root == null || !markPass(root)) return;

        List<View> marquees = new ArrayList<View>();
        List<View> icons = new ArrayList<View>();
        List<View> badges = new ArrayList<View>();
        List<View> junk = new ArrayList<View>();
        walk(root, marquees, icons, badges, junk, 0);
        if (marquees.isEmpty() && icons.isEmpty() && badges.isEmpty() && junk.isEmpty()) return;

        // 0) 推广卡：稳定文案命中 → 向上找卡片容器整块 GONE
        for (View tv : junk) {
            View card = cardOf(tv);
            if (card != null) hide(card);
        }
        if (!junk.isEmpty()) {
            HookEntry.log("HomeChromeKiller: hide " + junk.size() + " junk card(s)");
        }

        // 1) 搜索框：跑马灯热词的父容器（只到父级，避免连带右侧铃铛/扫码一起没）
        for (View m : marquees) {
            ViewParent parent = m.getParent();
            if (parent instanceof View) hide((View) parent);
        }
        if (!marquees.isEmpty()) {
            HookEntry.log("HomeChromeKiller: hide " + marquees.size() + " search box(es)");
        }

        // 2) 铃铛：同一父容器里最左边的 StateImageView（右边那个是扫码，保留）
        if (icons.size() >= 2) {
            View leftMost = null;
            int minX = Integer.MAX_VALUE;
            for (View v : icons) {
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
        for (View b : badges) hide(b);
        if (!badges.isEmpty()) {
            HookEntry.log("HomeChromeKiller: hide " + badges.size() + " badge(s)");
        }
    }

    private static void walk(View v, List<View> marquees, List<View> icons, List<View> badges,
                             List<View> junk, int depth) {
        if (v == null || depth > MAX_DEPTH) return;
        String simple = v.getClass().getSimpleName();
        if ("XMarqueeView".equals(simple)) {
            marquees.add(v);
        } else if ("StateImageView".equals(simple)) {
            icons.add(v);
        } else if ("BadgeTextView".equals(simple)) {
            badges.add(v);
        }
        if (v instanceof android.widget.TextView && isJunkText((android.widget.TextView) v)) {
            junk.add(v);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0, n = g.getChildCount(); i < n; i++) {
                walk(g.getChildAt(i), marquees, icons, badges, junk, depth + 1);
            }
        }
    }

    /** 只比对固定文案，取到就返回，不做任何正则 / 分词。 */
    private static boolean isJunkText(android.widget.TextView tv) {
        CharSequence cs;
        try {
            cs = tv.getText();
        } catch (Throwable t) {
            return false;
        }
        if (cs == null || cs.length() == 0) return false;
        String s = cs.toString();
        for (String k : JUNK_TEXTS) {
            if (s.contains(k)) return true;
        }
        return false;
    }

    /** 文字节点 → 卡片容器：向上最多 6 层，找高度足够的 ConstraintLayout；找不到就退到父容器。 */
    private static View cardOf(View textView) {
        View cur = textView;
        for (int i = 0; i < 6; i++) {
            ViewParent parent = cur.getParent();
            if (!(parent instanceof View)) break;
            cur = (View) parent;
            if (cur.getClass().getSimpleName().contains("ConstraintLayout") && cur.getHeight() >= 300) {
                return cur;
            }
        }
        ViewParent p = textView.getParent();
        return p instanceof View ? (View) p : null;
    }

    private static void hide(View view) {
        if (view == null) return;
        try {
            view.setVisibility(GONE);
        } catch (Throwable ignored) {
        }
    }

    private static int leftOf(View view) {
        try {
            int[] loc = new int[2];
            view.getLocationOnScreen(loc);
            return loc[0];
        } catch (Throwable t) {
            return -1;
        }
    }
}
