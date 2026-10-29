package com.sql916.zybclean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 直接掐死广告 Activity / 广告落地页。
 */
public class ActivityBlocker {

    /** 精确类名黑名单。 */
    private static final String[] EXACT = {
            "com.baidu.homework.ads.advertise.AdResumeSplashActivity",
            "com.fastad.api.reward.RewardAd1Activity",
            "com.fastad.api.reward.RewardAd2Activity",
            "com.fastad.api.reward.RewardAd3Activity",
            "com.fastad.csj.half.reward.CsjRewardActivity",
            "com.homework.fastad.reward.RewardDialogActivity",
            "com.homework.fastad.common.web.ApiWebActivity",
            "com.qq.e.ads.ADActivity",
            "com.qq.e.ads.PortraitADActivity",
            "com.qq.e.ads.LandscapeADActivity",
    };

    /** 前缀黑名单：这些包下的 Activity 全是广告。 */
    private static final String[] PREFIX = {
            "com.bytedance.sdk.openadsdk.core.activity.",
            "com.bytedance.sdk.openadsdk.stub.activity.",
            "com.baidu.mobads.sdk.internal.",
            "com.baidu.mobads.production.",
            "com.kwad.sdk.api.proxy.app.",
    };

    static void install(ClassLoader cl) {
        try {
            Class<?> activity = Class.forName("android.app.Activity", false, cl);
            XposedBridge.hookAllMethods(activity, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object self = param.thisObject;
                    if (self == null) return;
                    String name = self.getClass().getName();
                    if (!isBlocked(name)) return;
                    HookEntry.log("finish ad activity: " + name);
                    try {
                        XposedHelpers.callMethod(self, "finish");
                    } catch (Throwable ignored) {
                    }
                }
            });
            HookEntry.log("ActivityBlocker installed");
        } catch (Throwable t) {
            HookEntry.log("ActivityBlocker install failed", t);
        }
    }

    private static boolean isBlocked(String name) {
        for (String e : EXACT) {
            if (e.equals(name)) return true;
        }
        for (String p : PREFIX) {
            if (name.startsWith(p)) return true;
        }
        return false;
    }
}
