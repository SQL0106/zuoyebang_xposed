package io.github.sql0106.zybclean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * 拦截作业帮自研广告聚合层 com.fastad 的 34 个适配器。
 * 所有适配器都实现 doLoadAD()V / doShowAD()V，直接短路即可不加载、不展示。
 */
public class AdBlocker {

    /** 34 个适配器全名（静态分析得出，全部含 doLoadAD/doShowAD）。 */
    private static final String[] ADAPTERS = {
            // ---- 聚合 API 层 ----
            "com.fastad.api.ApiBannerAdapter",
            "com.fastad.api.ApiFlowExpressAdapter",
            "com.fastad.api.ApiInterstitialAdapter",
            "com.fastad.api.ApiRewardVideoAdapter",
            "com.fastad.api.ApiSplashAdapter",
            // ---- 百度 ----
            "com.fastad.baidu.BDBannerAdapter",
            "com.fastad.baidu.BDFlowExpressAdapter",
            "com.fastad.baidu.BDHalfFlowExpressAdapter",
            "com.fastad.baidu.BDHalfSplashAdapter",
            "com.fastad.baidu.BDInterstitialAdapter",
            "com.fastad.baidu.BDRewardVideoAdapter",
            "com.fastad.baidu.BDSplashAdapter",
            // ---- 穿山甲 csj ----
            "com.fastad.csj.CsjBannerAdapter",
            "com.fastad.csj.CsjFlowExpressAdapter",
            "com.fastad.csj.CsjHalfFlowExpressAdapter",
            "com.fastad.csj.CsjHalfRewardVideoAdapter",
            "com.fastad.csj.CsjHalfSplashAdapter",
            "com.fastad.csj.CsjInterstitialAdapter",
            "com.fastad.csj.CsjRewardVideoAdapter",
            "com.fastad.csj.CsjSplashAdapter",
            // ---- 快手 ks ----
            "com.fastad.ks.KsFlowExpressAdapter",
            "com.fastad.ks.KsHalfFlowExpressAdapter",
            "com.fastad.ks.KsHalfSplashAdapter",
            "com.fastad.ks.KsInterstitialAdapter",
            "com.fastad.ks.KsRewardVideoAdapter",
            "com.fastad.ks.KsSplashAdapter",
            // ---- 优量汇 ylh ----
            "com.fastad.ylh.YlhBannerAdapter",
            "com.fastad.ylh.YlhFlowExpressAdapter",
            "com.fastad.ylh.YlhHalfFlowExpressAdapter",
            "com.fastad.ylh.YlhHalfSplashAdapter",
            "com.fastad.ylh.YlhInterstitialAdapter",
            "com.fastad.ylh.YlhRewardVideoAdapter",
            "com.fastad.ylh.YlhSplashAdapter",
    };

    /** 终止方法：让广告永远不加载、不展示。 */
    private static final String[] KILL_METHODS = {"doLoadAD", "doShowAD"};

    static void install(ClassLoader cl) {
        int hit = 0;
        for (String name : ADAPTERS) {
            Class<?> c;
            try {
                c = Class.forName(name, false, cl);
            } catch (Throwable t) {
                continue;
            }
            hit++;
            for (String m : KILL_METHODS) {
                try {
                    XposedBridge.hookAllMethods(c, m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(null);
                            HookEntry.log("skip " + param.method.getDeclaringClass().getSimpleName()
                                    + "." + param.method.getName());
                        }
                    });
                } catch (Throwable t) {
                    HookEntry.log("hook " + name + "." + m + " failed", t);
                }
            }
        }
        HookEntry.log("AdBlocker installed on " + hit + "/" + ADAPTERS.length + " adapters");
    }
}
