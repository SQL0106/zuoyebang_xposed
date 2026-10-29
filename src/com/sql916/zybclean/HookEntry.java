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
        // 临时探针：定位阶段用，定位完删掉/注释
        // try { Probe.install(cl); } catch (Throwable t) { log("Probe install failed: " + t); }
        // try { ViewDump.install(cl); } catch (Throwable t) { log("ViewDump install failed: " + t); }
        try {
            HomeChromeKiller.install(cl);
        } catch (Throwable t) {
            log("HomeChromeKiller install failed: " + t);
        }
        try {
            WebKiller.install(cl);
        } catch (Throwable t) {
            log("WebKiller install failed: " + t);
        }
        try {
            FastStart.install(cl);
        } catch (Throwable t) {
            log("FastStart install failed: " + t);
        }
        // 探针：需要现场取证时再打开（触发文件 .zyb_snap / .zyb_web，写在 App 私有目录）
        // try { Snap.install(cl); } catch (Throwable t) { log("Snap install failed: " + t); }
        // try { WebEyes.install(cl); } catch (Throwable t) { log("WebEyes install failed: " + t); }
        log("all hooks installed");
    }

    public static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
    }

    public static void log(String msg, Throwable t) {
        XposedBridge.log(TAG + ": " + msg + " -> " + t);
    }
}
