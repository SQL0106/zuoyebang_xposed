package io.github.sql0106.zybclean;

import android.view.View;

import java.lang.reflect.Constructor;

import io.github.sql0106.zybclean.compat.XC_MethodHook;
import io.github.sql0106.zybclean.compat.XposedBridge;
import io.github.sql0106.zybclean.compat.XposedHelpers;

/**
 * 按「专门类名」干掉广告卡，不再依赖文案，也不再反复全树扫描。
 *
 * <p>依据 14.34.0 真机快照 + dex 类表（analysis/classes.txt）：</p>
 * <ul>
 *   <li>首页信息流广告＝{@code com.baidu.homework.activity.newhomepage.a_home.holder.AD*Holder}
 *       这一族 ViewHolder（ADNewBaseHolder / ADInnerNativeBaseHolder / ADNativeHolder12680 …）。
 *       名字带版本号后缀，只能按前缀匹配；</li>
 *   <li>「我的」页顶部会员/广告横滑卡＝{@code user_tab.holder.VipAbCardViewHolder}；</li>
 *   <li>广告位（{@code com.baidu.homework.adx} 包）里的 {@code XMarqueeView} 只是卡片里的
 *       跑马灯一行，真正要藏的是它所在的那张卡；</li>
 *   <li>{@code user_tab.jgw.UserJGWBannerView} 是「我的」页的推广位，整块藏掉。</li>
 * </ul>
 *
 * <p>做法：hook {@code RecyclerView.ViewHolder} 的构造器（ViewHolder 是所有 item 的基类，
 * 一次 hook 覆盖所有版本），只在构造完成后做一次类名前缀判断；命中才 GONE，
 * 并挂一个 attach 监听兜底（个别适配器 bind 时会把 itemView 设回 VISIBLE）。</p>
 *
 * <p>成本：每个 ViewHolder 一次字符串前缀比较，不做遍历、不读文字、不排定时器。</p>
 */
public final class AdCardKiller {

    /** 首页信息流广告 Holder 前缀（后面的版本号后缀会变，只能前缀匹配）。 */
    private static final String AD_HOLDER_PREFIX =
            "com.baidu.homework.activity.newhomepage.a_home.holder.AD";

    /** 额外按全名命中的 Holder。 */
    private static final String[] EXTRA_HOLDERS = {
            "com.baidu.homework.activity.user.user_tab.holder.VipAbCardViewHolder",
    };

    /** 整块推广位：命中后把自己 GONE。 */
    private static final String[] HIDE_SELF_VIEWS = {
            "com.baidu.homework.activity.user.user_tab.jgw.UserJGWBannerView",
    };

    /** 广告卡里的跑马灯：命中后把它爹（整张卡）GONE。 */
    private static final String[] HIDE_PARENT_VIEWS = {
            "com.baidu.homework.adx.widget.XMarqueeView",
    };

    /** attach 时兜底再压一次的监听（每个控件只挂一个）。 */
    private static final View.OnAttachStateChangeListener REHIDE =
            new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    try {
                        v.setVisibility(View.GONE);
                    } catch (Throwable ignored) {
                    }
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                }
            };

    /** 已加过「反弹压制」监听的控件（弱键，Activity 销毁后自动回收）。 */
    private static final java.util.WeakHashMap<View, Boolean> PATCHED =
            new java.util.WeakHashMap<View, Boolean>();

    private static int killed;

    static void install(ClassLoader cl) {
        hookViewHolder(cl);
        for (String n : HIDE_SELF_VIEWS) hookViewClass(cl, n, false);
        for (String n : HIDE_PARENT_VIEWS) hookViewClass(cl, n, true);
        HookEntry.log("AdCardKiller installed, killed=" + killed);
    }

    /* ---------------- 首页信息流 / 我的页 Holder ---------------- */

    private static void hookViewHolder(ClassLoader cl) {
        try {
            Class<?> vh = XposedHelpers.findClass(
                    "androidx.recyclerview.widget.RecyclerView$ViewHolder", cl);
            Constructor<?> ctor = vh.getDeclaredConstructor(View.class);
            XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object holder = param.thisObject;
                    if (holder == null) return;
                    if (!isAdHolder(holder.getClass().getName())) return;
                    try {
                        Object item = XposedHelpers.getObjectField(holder, "itemView");
                        if (item instanceof View) hideCard((View) item, "holder");
                    } catch (Throwable t) {
                        HookEntry.log("AdCardKiller: read itemView failed: " + t);
                    }
                }
            });
            HookEntry.log("AdCardKiller: ViewHolder hooked");
        } catch (Throwable t) {
            HookEntry.log("AdCardKiller: ViewHolder hook failed: " + t);
        }
    }

    private static boolean isAdHolder(String cn) {
        if (cn.startsWith(AD_HOLDER_PREFIX)) return true;
        for (String n : EXTRA_HOLDERS) {
            if (cn.equals(n)) return true;
        }
        return false;
    }

    /* ---------------- adx 广告位 / 推广位 ---------------- */

    /**
     * @param hideParent true＝命中控件本身只是卡片里的一行，藏它的父容器；
     *                   false＝命中控件就是整块推广位，藏它自己。
     */
    private static void hookViewClass(ClassLoader cl, String name, final boolean hideParent) {
        Class<?> c;
        try {
            c = XposedHelpers.findClass(name, cl);
        } catch (Throwable t) {
            HookEntry.log("AdCardKiller: " + name + " not found");
            return;
        }
        Constructor<?>[] ctors = c.getDeclaredConstructors();
        for (final Constructor<?> ctor : ctors) {
            try {
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object o = param.thisObject;
                        if (!(o instanceof View)) return;
                        View v = (View) o;
                        // 构造期还没挂到父容器上，父容器要等 attach 时才拿得到。
                        v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                            @Override
                            public void onViewAttachedToWindow(View view) {
                                View target = view;
                                if (hideParent) {
                                    Object p = view.getParent();
                                    if (p instanceof View) target = (View) p;
                                }
                                hideCard(target, view.getClass().getSimpleName());
                            }

                            @Override
                            public void onViewDetachedFromWindow(View view) {
                            }
                        });
                    }
                });
            } catch (Throwable t) {
                HookEntry.log("AdCardKiller: hook " + name + " ctor failed: " + t);
            }
        }
    }

    private static void hideCard(final View v, String why) {
        if (v == null) return;
        try {
            if (v.getVisibility() != View.GONE) {
                v.setVisibility(View.GONE);
                killed++;
                if (killed <= 10) {
                    HookEntry.log("AdCardKiller: hide " + why + " " + v.getClass().getSimpleName());
                }
            }
        } catch (Throwable ignored) {
        }
        synchronized (PATCHED) {
            if (PATCHED.containsKey(v)) return;
            PATCHED.put(v, Boolean.TRUE);
        }
        try {
            v.addOnAttachStateChangeListener(REHIDE);
        } catch (Throwable ignored) {
        }
        // 复用/绑定时适配器可能把 itemView 设回 VISIBLE：每次 layout 顺手压一次，代价是一次 int 比较。
        try {
            v.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View view, int l, int t, int r, int b,
                                           int ol, int ot, int or, int ob) {
                    if (view.getVisibility() != View.GONE) {
                        try {
                            view.setVisibility(View.GONE);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }
}
