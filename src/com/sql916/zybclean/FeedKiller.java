package com.sql916.zybclean;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 首页信息流净化：
 *  1) 过滤 feed 数据源里的广告/推广条目（HPListItem.itemType）；
 *  2) 隐藏首页 banner / 1+N 广告 pager。
 */
public class FeedKiller {

    private static final String HP_LIST_ITEM =
            "com.baidu.homework.activity.newhomepage.a_home.bean.HPListItem";

    /** 需要过滤广告条目的信息流 adapter。 */
    private static final String[] FEED_ADAPTERS = {
            "com.baidu.homework.activity.newhomepage.d_home.adapter.DFeedListAdapter",
            "com.baidu.homework.activity.newhomepage.e_home.adapter.EHPFeedsListAdapter",
            "com.baidu.homework.activity.newhomepage.d_home.adapter.DFeedFollowerAdapter",
    };

    /** 装数据的方法（都接收 List）。 */
    private static final String[] DATA_METHODS = {"addData", "q"};

    /** 这些 adapter 直接隐藏（banner / 广告 pager）。 */
    private static final String[] HIDE_SIMPLE_NAMES = {
            "HomeBannerAdapter",
            "ADOnePlusNPageAdapter",
            /** 首页「科普专区」信息流（实测首页 RecyclerView 用的就是它），整个列表隐藏。 */
            "EHPFeedsListAdapter",
    };

    /** 广告/推广条目类型，运行时从 HPListItem 静态常量读取，避免硬编码。 */
    private static Set<Integer> adTypes;

    static void install(ClassLoader cl) {
        initAdTypes(cl);

        for (String name : FEED_ADAPTERS) {
            Class<?> c;
            try {
                c = Class.forName(name, false, cl);
            } catch (Throwable t) {
                HookEntry.log("feed adapter not found: " + name);
                continue;
            }
            for (Method m : c.getDeclaredMethods()) {
                if (!DATA_METHODS[0].equals(m.getName()) && !DATA_METHODS[1].equals(m.getName())) continue;
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length != 1 || !List.class.isAssignableFrom(pt[0])) continue;
                try {
                    XposedBridge.hookMethod(m, new FeedFilterHook());
                } catch (Throwable t) {
                    HookEntry.log("hook " + name + "." + m.getName() + " failed", t);
                }
            }
            // 兜底：方法返回后再清一遍成员变量 d
            try {
                XposedBridge.hookAllMethods(c, "notifyDataSetChanged", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        stripFieldList(param.thisObject);
                    }
                });
            } catch (Throwable ignored) {
            }
            HookEntry.log("feed filter installed: " + name);
        }

        hookAdapterHost(cl, "androidx.recyclerview.widget.RecyclerView");
        hookAdapterHost(cl, "androidx.viewpager.widget.ViewPager");
        hookAdapterHost(cl, "androidx.viewpager2.widget.ViewPager2");
    }

    /** 隐藏 banner 类 adapter 所在的容器。 */
    private static void hookAdapterHost(ClassLoader cl, String hostClass) {
        Class<?> c;
        try {
            c = Class.forName(hostClass, false, cl);
        } catch (Throwable t) {
            return;
        }
        try {
            XposedBridge.hookAllMethods(c, "setAdapter", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length == 0 || param.args[0] == null) return;
                    String simple = param.args[0].getClass().getSimpleName();
                    HookEntry.log("setAdapter " + param.thisObject.getClass().getName() + " <- " + simple);
                    for (String hide : HIDE_SIMPLE_NAMES) {
                        if (simple.contains(hide)) {
                            try {
                                XposedHelpers.callMethod(param.thisObject, "setVisibility", 8);
                            } catch (Throwable ignored) {
                            }
                            HookEntry.log("hide " + hostClass.substring(hostClass.lastIndexOf('.') + 1)
                                    + " with " + simple);
                            return;
                        }
                    }
                }
            });
        } catch (Throwable t) {
            HookEntry.log("hook " + hostClass + "#setAdapter failed", t);
        }
    }

    private static class FeedFilterHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (param.args == null || param.args.length == 0) return;
            if (!(param.args[0] instanceof List)) return;
            List<?> in = (List<?>) param.args[0];
            probe(param.thisObject, in);
            Object out = filter((List<?>) param.args[0]);
            if (out != null) param.args[0] = out;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            stripFieldList(param.thisObject);
        }
    }

    private static int probeBudget = 6;

    /** 调试探针：打印 feed 数据里每个条目的类型，定位「科普专区」由谁渲染。 */
    private static void probe(Object adapter, List<?> in) {
        if (probeBudget <= 0) return;
        probeBudget--;
        String who = adapter == null ? "?" : adapter.getClass().getSimpleName();
        HookEntry.log("probe " + who + " size=" + in.size());
        int n = Math.min(in.size(), 12);
        for (int i = 0; i < n; i++) {
            Object o = in.get(i);
            if (o == null) continue;
            String extra = "";
            try {
                extra = " adType=" + XposedHelpers.getIntField(o, "adType")
                        + " listItem=" + (XposedHelpers.getObjectField(o, "listItem") == null
                        ? "null" : XposedHelpers.getObjectField(o, "listItem").getClass().getSimpleName());
            } catch (Throwable ignored) {
            }
            HookEntry.log("  item[" + i + "] " + o.getClass().getSimpleName()
                    + " itemType=" + itemType(o) + extra);
        }
    }

    private static String itemType(Object item) {
        try {
            return String.valueOf(XposedHelpers.getIntField(item, "itemType"));
        } catch (Throwable t) {
            return "n/a";
        }
    }

    @SuppressWarnings("unchecked")
    private static void stripFieldList(Object adapter) {
        if (adapter == null) return;
        Object v;
        try {
            v = XposedHelpers.getObjectField(adapter, "d");
        } catch (Throwable t) {
            return;
        }
        if (!(v instanceof List)) return;
        int before = ((List<?>) v).size();
        try {
            ((List<Object>) v).removeIf(FeedKiller::isAdItem);
        } catch (Throwable ignored) {
            return;
        }
        int after = ((List<?>) v).size();
        if (after != before) {
            HookEntry.log("field d filtered " + before + " -> " + after);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object filter(List<?> in) {
        if (in.isEmpty()) return null;
        List<Object> kept = null;
        for (int i = 0; i < in.size(); i++) {
            Object o = in.get(i);
            if (isAdItem(o)) {
                if (kept == null) kept = new ArrayList<>(in.subList(0, i));
            } else if (kept != null) {
                kept.add(o);
            }
        }
        if (kept == null) return null;
        HookEntry.log("feed filtered " + in.size() + " -> " + kept.size());
        return kept;
    }

    private static boolean isAdItem(Object item) {
        if (item == null) return false;
        int type;
        try {
            type = XposedHelpers.getIntField(item, "itemType");
        } catch (Throwable t) {
            return false;
        }
        return adTypes != null && adTypes.contains(type);
    }

    private static void initAdTypes(ClassLoader cl) {
        Set<Integer> s = new HashSet<>();
        try {
            Class<?> c = Class.forName(HP_LIST_ITEM, false, cl);
            addConst(s, c, "TYPE_AD");
            addConst(s, c, "TYPE_LINKED_AD");
            addConst(s, c, "TYPE_VIP_RECOMMEND");
            HookEntry.log("ad item types = " + s);
        } catch (Throwable t) {
            HookEntry.log("HPListItem not found, ad types unavailable", t);
        }
        adTypes = s.isEmpty() ? null : s;
    }

    private static void addConst(Set<Integer> s, Class<?> c, String name) {
        try {
            s.add(XposedHelpers.getStaticIntField(c, name));
        } catch (Throwable ignored) {
        }
    }
}
