package com.sql916.zybclean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 作业帮 (com.zuoyebang.knowledge) 净化模块入口。
 */
public class HookEntry implements IXposedHookLoadPackage {

    public static final String TAG = "ZYBCLEAN";
    public static final String PKG = "com.zuoyebang.knowledge";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!PKG.equals(lpparam.packageName)) return;
        log("hook loaded, process=" + lpparam.processName);

        ClassLoader cl = lpparam.classLoader;

        try {
            AdBlocker.install(cl);
        } catch (Throwable t) {
            log("AdBlocker install failed: " + t);
        }
        try {
            FeedKiller.install(cl);
        } catch (Throwable t) {
            log("FeedKiller install failed: " + t);
        }
        try {
            ActivityBlocker.install(cl);
        } catch (Throwable t) {
            log("ActivityBlocker install failed: " + t);
        }
        try {
            DialogKiller.install(cl);
        } catch (Throwable t) {
            log("DialogKiller install failed: " + t);
        }
        try {
            FloatKiller.install(cl);
        } catch (Throwable t) {
            log("FloatKiller install failed: " + t);
        }
        log("all hooks installed");
    }

    public static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
    }

    public static void log(String msg, Throwable t) {
        XposedBridge.log(TAG + ": " + msg + " -> " + t);
    }
}
