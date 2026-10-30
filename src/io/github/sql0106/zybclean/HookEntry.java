package io.github.sql0106.zybclean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.sql0106.zybclean.compat.XposedBridge;

/**
 * 作业帮 (com.zuoyebang.knowledge) 净化模块入口。
 *
 * <p>使用 libxposed 现代 API（102）：继承 {@link XposedModule}，由框架回调
 * {@link #onModuleLoaded} / {@link #onPackageLoaded}。作用域已在
 * {@code META-INF/xposed/scope.list} 里静态声明（com.zuoyebang.knowledge），
 * 用户不需要手动勾选。</p>
 */
public class HookEntry extends XposedModule {

    public static final String TAG = XposedBridge.TAG;
    public static final String PKG = "com.zuoyebang.knowledge";

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        // 必须在 onModuleLoaded 之后才能用 hook API（框架此时才 attach 完）
        XposedBridge.bind(this);
        log("module loaded, process=" + param.getProcessName()
                + ", api=" + XposedInterface.LIB_API);
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (!PKG.equals(param.getPackageName())) return;
        log("package loaded: " + param.getPackageName());

        ClassLoader cl = param.getDefaultClassLoader();

        try {
            AdBlocker.install(cl);
        } catch (Throwable t) {
            log("AdBlocker install failed", t);
        }
        try {
            FeedKiller.install(cl);
        } catch (Throwable t) {
            log("FeedKiller install failed", t);
        }
        try {
            ActivityBlocker.install(cl);
        } catch (Throwable t) {
            log("ActivityBlocker install failed", t);
        }
        try {
            DialogKiller.install(cl);
        } catch (Throwable t) {
            log("DialogKiller install failed", t);
        }
        try {
            FloatKiller.install(cl);
        } catch (Throwable t) {
            log("FloatKiller install failed", t);
        }
        try {
            HomeChromeKiller.install(cl);
        } catch (Throwable t) {
            log("HomeChromeKiller install failed", t);
        }
        try {
            WebKiller.install(cl);
        } catch (Throwable t) {
            log("WebKiller install failed", t);
        }
        try {
            FastStart.install(cl);
        } catch (Throwable t) {
            log("FastStart install failed", t);
        }
        // 探针：需要现场取证时再打开（触发文件 .zyb_snap / .zyb_web，写在 App 私有目录）
        // try { Probe.install(cl); } catch (Throwable t) { log("Probe install failed", t); }
        // try { ViewDump.install(cl); } catch (Throwable t) { log("ViewDump install failed", t); }
        // try { Snap.install(cl); } catch (Throwable t) { log("Snap install failed", t); }
        // try { WebEyes.install(cl); } catch (Throwable t) { log("WebEyes install failed", t); }
        log("all hooks installed");
    }

    public static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
    }

    public static void log(String msg, Throwable t) {
        XposedBridge.log(TAG + ": " + msg + " -> " + t);
    }
}
