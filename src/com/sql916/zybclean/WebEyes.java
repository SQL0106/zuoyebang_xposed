package com.sql916.zybclean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * 「H5 眼睛」：作业帮的搜题结果页是 NestedHybridWebView 渲染的 H5 页面，
 * AI 解题 tab、讲解中浮卡这些都是网页元素，native 侧看不到、hook 不到。
 *
 * 做法：抓住进程里所有 WebView，收到触发文件后往页面里 evaluateJavascript，
 * 把「可见元素清单（标签/class/id/坐标/文字）」dump 到私有目录，root 侧读取。
 * 拿到真实 DOM 后再写精确的 CSS 隐藏规则。
 *
 * 触发：rsh realme 'touch /data/data/com.zuoyebang.knowledge/files/.zyb_web'
 * 输出：/data/data/com.zuoyebang.knowledge/files/.zyb_web.txt
 */
public class WebEyes {

    private static final String TRIGGER = "/data/data/com.zuoyebang.knowledge/files/.zyb_web";
    private static final String OUT = "/data/data/com.zuoyebang.knowledge/files/.zyb_web.txt";
    private static final int MAX_WEBVIEWS = 6;
    private static final int MAX_LINES = 1500;

    private static final List<Object> WEBVIEWS = new ArrayList<Object>();
    private static ClassLoader sCl;

    /** dump 脚本：列出所有可见元素的 tag/class/id/坐标/文字。 */
    private static final String DUMP_JS =
        "(function(){try{"
            + "var out=[];out.push('URL='+location.href);out.push('TITLE='+document.title);"
            + "var all=document.querySelectorAll('*');out.push('TOTAL='+all.length);"
            + "for(var i=0;i<all.length;i++){var e=all[i];"
            + "var cs=getComputedStyle(e);"
            + "if(cs.display==='none'||cs.visibility==='hidden'||cs.opacity==='0')continue;"
            + "var r=e.getBoundingClientRect();"
            + "if(r.width<2||r.height<2)continue;"
            + "var t=e.childElementCount===0?(e.textContent||'').trim().replace(/\\s+/g,' ').slice(0,70):'';"
            + "var c=(typeof e.className==='string')?e.className:'';"
            + "out.push(e.tagName.toLowerCase()+(e.id?'#'+e.id:'')+(c?'.'+c.slice(0,90):'')"
            + "+' box='+Math.round(r.left)+','+Math.round(r.top)+','+Math.round(r.width)+'x'+Math.round(r.height)"
            + "+(t?' txt='+t:''));"
            + "if(out.length>" + MAX_LINES + ")break;}"
            + "return out.join('\\n');}catch(err){return 'JSERR '+err;}})()";

    public static void install(final ClassLoader cl) {
        sCl = cl;
        try {
            Class<?> wv = XposedHelpers.findClass("android.webkit.WebView", cl);
            XC_MethodHook reg = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    remember(param.thisObject);
                }
            };
            XposedBridge.hookAllMethods(wv, "loadUrl", reg);
            XposedBridge.hookAllMethods(wv, "loadDataWithBaseURL", reg);
        } catch (Throwable t) {
            HookEntry.log("WebEyes hook webview failed: " + t);
        }

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(300);
                        File f = new File(TRIGGER);
                        if (!f.exists()) continue;
                        //noinspection ResultOfMethodCallIgnored
                        f.delete();
                        dumpAll();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }, "zyb-web");
        t.setDaemon(true);
        t.start();
        HookEntry.log("WebEyes installed, trigger=" + TRIGGER);
    }

    private static void remember(Object wv) {
        synchronized (WEBVIEWS) {
            for (Object o : WEBVIEWS) {
                if (o == wv) return;
            }
            WEBVIEWS.add(wv);
            while (WEBVIEWS.size() > MAX_WEBVIEWS) WEBVIEWS.remove(0);
        }
    }

    private static void dumpAll() {
        final List<Object> targets;
        synchronized (WEBVIEWS) {
            targets = new ArrayList<Object>(WEBVIEWS);
        }
        HookEntry.log("WebEyes dump request, webviews=" + targets.size());
        final StringBuilder sb = new StringBuilder();
        sb.append("WEBDUMP webviews=").append(targets.size()).append('\n');
        for (int i = 0; i < targets.size(); i++) {
            final Object wv = targets.get(i);
            final int idx = i;
            try {
                Object cb = makeCallback(sb, idx);
                XposedHelpers.callMethod(wv, "post", new Runnable() {
                    @Override
                    public void run() {
                        try {
                            XposedHelpers.callMethod(wv, "evaluateJavascript", DUMP_JS, cb);
                        } catch (Throwable t) {
                            HookEntry.log("WebEyes eval failed: " + t);
                        }
                    }
                });
            } catch (Throwable t) {
                HookEntry.log("WebEyes post failed: " + t);
            }
        }
        // 给 JS 回调留时间，然后落盘
        try {
            Thread.sleep(2500);
        } catch (InterruptedException ignored) {
        }
        write(sb.toString());
        HookEntry.log("WebEyes dump written, bytes=" + sb.length());
    }

    private static Object makeCallback(final StringBuilder sb, final int idx) {
        try {
            Class<?> cbClass = XposedHelpers.findClass("android.webkit.ValueCallback", sCl);
            return Proxy.newProxyInstance(sCl, new Class<?>[]{cbClass}, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    try {
                        if (args != null && args.length > 0 && args[0] != null) {
                            sb.append("\n--- webview#").append(idx).append(" ---\n");
                            sb.append(unquote(String.valueOf(args[0])));
                        }
                    } catch (Throwable ignored) {
                    }
                    return null;
                }
            });
        } catch (Throwable t) {
            HookEntry.log("WebEyes callback failed: " + t);
            return null;
        }
    }

    /** evaluateJavascript 回来的是 JSON 字符串字面量，去掉包裹的引号并还原转义。 */
    private static String unquote(String s) {
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            s = s.substring(1, s.length() - 1);
        }
        return s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\").replace("\\t", " ");
    }

    private static void write(String text) {
        FileWriter w = null;
        try {
            w = new FileWriter(OUT, false);
            w.write(text);
            w.flush();
        } catch (Throwable t) {
            HookEntry.log("WebEyes write failed: " + t);
        } finally {
            if (w != null) try { w.close(); } catch (Throwable ignored) { }
        }
    }
}
