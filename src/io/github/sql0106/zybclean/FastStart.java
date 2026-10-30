package io.github.sql0106.zybclean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 开屏提速：InitActivity 进首页是「延后执行」的（延迟跑 L0 → K0 → y0 → IndexActivity），
 * 期间还夹着开屏广告/预热的等待，所以启动很慢。
 *
 * 这里只做一件保守的事：把「InitActivity / Splash 相关的 Runnable」被 postDelayed 的延时
 * 压到 250ms 以内，让跳转尽快发生。不改业务逻辑、不跳过初始化（跳太快也不至于崩），
 * 万一某版延迟有别的用途，把 ENABLED 改 false 即可。
 */
public class FastStart {

    /** 关掉这一项改 false 重新打包即可。 */
    private static final boolean ENABLED = true;

    /** 超过这个延时才认为「是在拖时间」；短延时（动画帧、节流）不动。 */
    private static final long THRESHOLD_MS = 800L;

    /** 压缩后的目标延时。 */
    private static final long TARGET_MS = 250L;

    private static final AtomicInteger LOGGED = new AtomicInteger();

    public static void install(ClassLoader cl) {
        if (!ENABLED) {
            HookEntry.log("FastStart disabled");
            return;
        }
        try {
            Class<?> handler = XposedHelpers.findClass("android.os.Handler", cl);
            XposedBridge.hookAllMethods(handler, "postDelayed", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length != 2) return;
                    Object r = param.args[0];
                    Object d = param.args[1];
                    if (!(d instanceof Long) || r == null) return;
                    long delay = ((Long) d).longValue();
                    if (delay <= THRESHOLD_MS) return;

                    String cn;
                    try {
                        cn = r.getClass().getName();
                    } catch (Throwable t) {
                        return;
                    }
                    if (cn == null) return;
                    if (!cn.contains("InitActivity") && !cn.contains("Splash")) return;

                    param.args[1] = Long.valueOf(TARGET_MS);
                    if (LOGGED.getAndIncrement() < 20) {
                        HookEntry.log("FastStart: " + cn + " delay " + delay + " -> " + TARGET_MS);
                    }
                }
            });
            HookEntry.log("FastStart installed");
        } catch (Throwable t) {
            HookEntry.log("FastStart install failed: " + t);
        }
    }
}
