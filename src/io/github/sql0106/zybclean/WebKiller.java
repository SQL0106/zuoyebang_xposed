package io.github.sql0106.zybclean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.util.ArrayList;
import java.util.List;

/**
 * H5 净化：搜题结果页整页是 NestedHybridWebView 渲染的网页（见 analysis/snap4.txt），
 * 「AI 解题」tab、「讲解中」视频浮卡都在 DOM 里，native 侧 hook 不到，只能注入 JS。
 *
 * 做法：抓住进程里所有 WebView，页面加载后多次 evaluateJavascript 注入一段幂等脚本：
 *   1. 文本等于「AI解题 / AI 解题」的 tab/按钮 → 隐藏；
 *   2. 含「讲解」字样、且向上 8 层内存在 position:fixed / sticky 祖先 → 把那个浮层隐藏；
 *   3. MutationObserver + 定时器持续生效（结果页是 SPA，DOM 随时会变）。
 */
public class WebKiller {

    private static final int MAX_WEBVIEWS = 8;
    private static final long[] DELAYS = {1200L, 3000L, 6000L, 12000L};

    private static final List<Object> WEBVIEWS = new ArrayList<Object>();

    private static final String AI_LIST = "['AI解题','AI 解题','AI讲题','AI 讲题']";

    /** 注入脚本：幂等，重复注入无副作用。 */
    private static final String JS =
        "(function(){"
            + "if(window.__zybclean)return;window.__zybclean=1;"
            + "var AI=" + AI_LIST + ";"
            + "function hide(el){try{el.style.setProperty('display','none','important');}catch(e){}}"
            + "function scan(){try{"
            + "var els=document.querySelectorAll('div,span,li,a,button,p,section,em,strong');"
            + "for(var i=0;i<els.length;i++){var el=els[i];"
            + "var t=(el.textContent||'').replace(/\\s+/g,' ').trim();"
            + "if(!t||t.length>30)continue;"
            + "for(var k=0;k<AI.length;k++){if(t===AI[k]){var box=el.closest('li')||el.parentElement||el;hide(box);}}"
            + "if(t.indexOf('讲解')>=0){var p=el;for(var d=0;d<8&&p;d++){"
            + "var cs;try{cs=getComputedStyle(p);}catch(e2){break;}"
            + "if(cs&&(cs.position==='fixed'||cs.position==='sticky')){hide(p);break;}p=p.parentElement;}}"
            + "}"
            + "}catch(e){}}"
            + "scan();"
            + "try{new MutationObserver(function(){scan();}).observe(document.documentElement,{childList:true,subtree:true});}catch(e){}"
            + "setInterval(scan,1500);"
            + "})()";

    public static void install(ClassLoader cl) {
        try {
            Class<?> webView = XposedHelpers.findClass("android.webkit.WebView", cl);
            XC_MethodHook reg = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        inject(param.thisObject);
                    } catch (Throwable t) {
                        HookEntry.log("WebKiller inject failed: " + t);
                    }
                }
            };
            XposedBridge.hookAllMethods(webView, "loadUrl", reg);
            XposedBridge.hookAllMethods(webView, "loadDataWithBaseURL", reg);
            HookEntry.log("WebKiller installed");
        } catch (Throwable t) {
            HookEntry.log("WebKiller install failed: " + t);
        }
    }

    private static void inject(final Object webView) {
        remember(webView); // 只做登记；每次导航都要重新注入（新文档会丢掉 __zybclean 标记）
        for (long delay : DELAYS) {
            try {
                XposedHelpers.callMethod(webView, "postDelayed", new Runnable() {
                    @Override
                    public void run() {
                        try {
                            XposedHelpers.callMethod(webView, "evaluateJavascript", JS, null);
                        } catch (Throwable ignored) {
                        }
                    }
                }, Long.valueOf(delay));
            } catch (Throwable t) {
                HookEntry.log("WebKiller postDelayed failed: " + t);
                return;
            }
        }
    }

    private static boolean remember(Object webView) {
        synchronized (WEBVIEWS) {
            for (Object o : WEBVIEWS) {
                if (o == webView) return false;
            }
            WEBVIEWS.add(webView);
            while (WEBVIEWS.size() > MAX_WEBVIEWS) WEBVIEWS.remove(0);
            return true;
        }
    }
}
