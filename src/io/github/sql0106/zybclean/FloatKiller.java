package io.github.sql0106.zybclean;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

import io.github.sql0106.zybclean.compat.XC_MethodHook;
import io.github.sql0106.zybclean.compat.XposedBridge;
import io.github.sql0106.zybclean.compat.XposedHelpers;

/**
 * 干掉首页「视频讲解」浮窗。
 *
 * 目标类 com.baidu.homework.activity.index.HomePageFloatView：
 *  - 由 IndexActivity.onCreate 创建，DHomepageFragment.G / DHPListFragment.onStart /
 *    EHPListFragment.onStart 注册滚动监听（P(ListScrollPresenter)）；
 *  - n(View) 才是真正把浮窗 View addView 到 activity decor 的 FrameLayout；
 *  - o() 播放出现动画，q() 定时器，s(String) 设置跳转，A() 隐藏。
 *
 * 做法：把这几个方法全部变成空实现（<init> 保留，避免字段未初始化的 NPE）。
 */
final class FloatKiller {

    private static final String CLS = "com.baidu.homework.activity.index.HomePageFloatView";

    /** 只拦这些（都是 void 方法），保留 <init>/<clinit> 与 getter。 */
    private static final Set<String> KILL = new LinkedHashSet<String>();

    static {
        KILL.add("onCreate");          // (LifecycleOwner) 初始化 + 拉浮球数据
        KILL.add("onStart");
        KILL.add("onResume");
        KILL.add("onPause");
        KILL.add("onStop");
        KILL.add("onDestroy");
        KILL.add("onLoginStateChange");
        KILL.add("P");                 // 注册 RecyclerView 滚动监听 -> 滑到下面就冒出来
        KILL.add("n");                 // addView(decorView) 真正挂上浮窗
        KILL.add("o");                 // 出现动画
        KILL.add("q");                 // 定时自动展示
        KILL.add("s");                 // 设置跳转链接 / 展示
        KILL.add("A");                 // gone
        KILL.add("K");
        KILL.add("L");
        KILL.add("M");
        KILL.add("N");
        KILL.add("O");
    }

    private FloatKiller() {}

    static void install(ClassLoader cl) {
        Class<?> c;
        try {
            c = XposedHelpers.findClass(CLS, cl);
        } catch (Throwable t) {
            HookEntry.log("FloatKiller: class not found -> " + t);
            return;
        }

        int hooked = 0;
        Method[] methods = c.getDeclaredMethods();
        for (Method m : methods) {
            if (m.getReturnType() != void.class) continue;
            if (!KILL.contains(m.getName())) continue;
            try {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(null);
                    }
                });
                hooked++;
            } catch (Throwable t) {
                HookEntry.log("FloatKiller: hook " + m.getName() + " failed -> " + t);
            }
        }
        HookEntry.log("FloatKiller installed on " + hooked + " methods of HomePageFloatView");
    }
}
