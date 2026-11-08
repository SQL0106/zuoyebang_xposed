package io.github.sql0106.zybclean;

import io.github.sql0106.zybclean.compat.XC_MethodHook;
import io.github.sql0106.zybclean.compat.XposedBridge;
import io.github.sql0106.zybclean.compat.XposedHelpers;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * H5 净化 + 阅读增强：搜题结果页 / 解答页整页是 NestedHybridWebView 渲染的网页
 * （见 analysis/snap4.txt），DOM 里既有垃圾也有用户真正需要的东西，只能注入 JS。
 *
 * <p>注入的幂等脚本做四件事：</p>
 * <ol>
 *   <li>干掉「AI解题 / AI讲题 / 问小帮」tab 与「讲解中」视频浮卡；</li>
 *   <li>解除页面的选中/复制限制（user-select + 事件拦截），让长按能直接选字复制；</li>
 *   <li>右下角挂三个悬浮按钮：<b>复制内容</b>（整页题目+解答文本进剪贴板）、
 *       <b>存长图</b>（原生滚动拼接整页截图，公式排版原样保留，见 {@link PageShot}）、
 *       <b>下载图片</b>（收集页面里所有可见图片，逐张存进相册 Pictures/zybclean）；</li>
 *   <li>MutationObserver + 定时扫描，应对 SPA 动态渲染。</li>
 * </ol>
 *
 * <p>保存图片与写剪贴板都走原生 {@link H5Bridge}（页面里的 {@code zybcleanHost}），
 * 原生侧带 WebView 的 Cookie 去下载，绕开跨域限制。</p>
 */
public class WebKiller {

    private static final int MAX_WEBVIEWS = 8;
    private static final long[] DELAYS = {1200L, 3000L, 6000L, 12000L};

    private static final List<Object> WEBVIEWS = new ArrayList<Object>();
    private static final WeakHashMap<Object, Boolean> BRIDGED = new WeakHashMap<Object, Boolean>();

    /** 注入脚本：幂等，重复注入无副作用。 */
    private static final String JS = buildJs();

    private static String buildJs() {
        return "(function(){"
            + "if(window.__zybclean)return;window.__zybclean=1;"
            + "function H(){try{return window.zybcleanHost||null;}catch(e){return null;}}"
            + "function hide(el){try{el.style.setProperty('display','none','important');}catch(e){}}"
            + "function toast(m){try{var h=H();if(h&&h.toast)h.toast(m);}catch(e){}}"

            /* ---------- 1. 允许选中 / 复制 ---------- */
            + "try{"
            + "var st=document.createElement('style');"
            + "st.textContent='*{-webkit-user-select:text!important;user-select:text!important;"
            + "-webkit-touch-callout:default!important}';"
            + "(document.head||document.documentElement).appendChild(st);"
            + "document.onselectstart=null;document.oncontextmenu=null;document.oncopy=null;document.ondragstart=null;"
            + "var allow=function(e){e.stopPropagation();};"
            + "var evs=['selectstart','contextmenu','copy','cut'];"
            + "for(var ei=0;ei<evs.length;ei++){try{document.addEventListener(evs[ei],allow,true);}catch(e){}}"
            + "}catch(e){}"

            /* ---------- 2. 干掉垃圾 DOM ---------- */
            + "var AI=/^AI(解题|讲题|答疑)$/i;"
            + "function scanKill(){try{"
            + "var els=document.querySelectorAll('div,span,li,a,button,p,section,em,strong');"
            + "var n=els.length>2500?2500:els.length;"
            + "for(var i=0;i<n;i++){var el=els[i];"
            + "if(el.childElementCount>4)continue;"
            + "var t=(el.textContent||'').replace(/\\s+/g,' ').trim();"
            + "if(!t||t.length>30)continue;"
            + "var r;try{r=el.getBoundingClientRect();}catch(e3){continue;}"
            + "var z=t.replace(/\\s/g,'');"
            + "if((AI.test(z)||z==='问小帮')&&r.height<130)hide(el.closest('li')||el.parentElement||el);"
            + "if(t.indexOf('讲解')>=0){var p=el;for(var d=0;d<8&&p;d++){"
            + "var cs;try{cs=getComputedStyle(p);}catch(e2){break;}"
            + "if(cs&&(cs.position==='fixed'||cs.position==='sticky')){hide(p);break;}p=p.parentElement;}}"
            + "}}catch(e){}}"
            /* ---------- 3. 悬浮工具条 ---------- */
            /* 取正文：必须在“活”的 DOM 上走，cloneNode 的 innerText 会退化成 textContent，
               把 <script> 内容和 display:none 的隐藏 tab 全带出来（用户实测过）。 */
            + "var SKIP={SCRIPT:0,STYLE:1,NOSCRIPT:1,TEMPLATE:1,TEXTAREA:1,TITLE:1,IFRAME:1,HEAD:1,"
            + "SVG:1,CANVAS:1,VIDEO:1,AUDIO:1,INPUT:1,SELECT:1,BUTTON:1};"
            + "var BLOCK={DIV:1,P:1,SECTION:1,ARTICLE:1,LI:1,UL:1,OL:1,TR:1,TD:1,TH:1,TABLE:1,"
            + "H1:1,H2:1,H3:1,H4:1,H5:1,H6:1,BR:1,HR:1,DT:1,DD:1,PRE:1,BLOCKQUOTE:1,"
            + "HEADER:1,FOOTER:1,MAIN:1,ASIDE:1,NAV:1,FIGURE:1,FIGCAPTION:1};"
            + "var DROP={'本题精讲':1,'讲解中':1,'问小帮':1,'AI解题':1,'AI讲题':1,'AI答疑':1,"
            + "'复制内容':1,'下载图片':1,'展开':1,'收起':1,'查看解析':1,'答案解析':0};"
            + "function normTex(s){return String(s||'').replace(/\\s+/g,' ').trim();}"
            + "function vis(e){try{var cs=getComputedStyle(e);"
            + "if(cs.display==='none'||cs.visibility==='hidden'||cs.opacity==='0')return false;"
            + "if(e.getAttribute&&e.getAttribute('aria-hidden')==='true')return false;"
            + "var r=e.getBoundingClientRect();"
            + "if(r.width<=0&&r.height<=0&&cs.position!=='fixed')return false;"
            + "return true;}catch(x){return true;}}"
            + "function imgTex(im){var ks=['data-latex','data-tex','alt','title','aria-label'];"
            + "for(var i=0;i<ks.length;i++){var v=im.getAttribute(ks[i]);"
            + "if(v&&v.replace(/\\s/g,'')){var s=v.trim();return /[\\\\^_{}]/.test(s)&&s.indexOf('$')<0?('$'+s+'$'):s;}}"
            + "var s2=im.getAttribute('src')||'';"
            + "var m=/[?&](?:tex|latex|formula|text)=([^&]+)/i.exec(s2);"
            + "if(m){try{return '$'+decodeURIComponent(m[1])+'$';}catch(x){return '';}}"
            + "return '';}"
            + "function mathTex(e){var a=e.querySelector&&e.querySelector('annotation[encoding=\"application/x-tex\"]');"
            + "if(a&&a.textContent)return normTex(a.textContent);"
            + "var at=e.getAttribute&&e.getAttribute('alttext');if(at)return normTex(at);"
            + "var ml=e.querySelector&&e.querySelector('.katex-mathml');if(ml)return normTex(ml.textContent);"
            + "return '';}"
            + "var FC=0;"
            + "function walk(e,out,d){if(!e||d>90||out.length>60000)return;"
            + "var tag=e.tagName?e.tagName.toUpperCase():'';"
            + "if(tag==='SCRIPT'){var ty=e.getAttribute?e.getAttribute('type')||'':'';"
            + "if(ty.indexOf('math/tex')===0){var st=normTex(e.textContent);"
            + "if(st){out.push('$'+st+'$');FC++;}}return;}"
            + "if(SKIP[tag]||e.id==='zyb-bar')return;"
            + "if(tag==='IMG'){var it=imgTex(e);if(it)out.push(it);return;}"
            + "var cls=(typeof e.className==='string')?e.className:'';"
            + "if(tag==='MJX-CONTAINER'||tag==='MATH'||/katex|mathjax|mjx-/i.test(cls)){"
            + "var mt=mathTex(e);if(!mt)mt=normTex(e.textContent);"
            + "if(mt){out.push('$'+mt+'$');FC++;}return;}"
            + "if(e.nodeType===3){var tx=e.nodeValue;"
            + "if(tx&&tx.replace(/\\s/g,'')){if(vis(e.parentNode||e))out.push(tx);}return;}"
            + "if(!vis(e))return;"
            + "var n=e.childNodes?e.childNodes.length:0;"
            + "for(var i=0;i<n;i++)walk(e.childNodes[i],out,d+1);"
            + "if(BLOCK[tag])out.push('\\n');}"
            + "function pageText(){try{FC=0;var out=[];walk(document.body,out,0);"
            + "var s=out.join('').replace(/\\r/g,'').replace(/[ \\t\\u00a0]+/g,' ');"
            + "s=s.replace(/ *\\n */g,'\\n').replace(/\\n{3,}/g,'\\n\\n').trim();"
            + "var lines=s.split('\\n'),keep=[];"
            + "for(var i=0;i<lines.length;i++){var L=lines[i].replace(/\\s/g,'');"
            + "if(!L)continue;if(DROP[L])continue;keep.push(lines[i]);}"
            + "return keep.join('\\n').slice(0,200000);}catch(e){return '';}}"
            + "function legacyCopy(t){var ta=document.createElement('textarea');ta.value=t;"
            + "ta.style.cssText='position:fixed;left:-9999px;top:0;';document.body.appendChild(ta);"
            + "try{ta.select();document.execCommand('copy');toast('已复制');}catch(e){toast('复制失败');}"
            + "try{ta.remove();}catch(e){}}"
            + "function copyAll(){var t=pageText();if(!t){toast('没找到可复制的文字');return;}"
            + "var h=H();"
            + "if(h&&h.copyText){try{h.copyText(t);return;}catch(e){}}"
            + "if(navigator.clipboard&&navigator.clipboard.writeText){"
            + "navigator.clipboard.writeText(t).then(function(){toast('已复制');},function(){legacyCopy(t);});return;}"
            + "legacyCopy(t);}"
            + "function collectImgs(){var out=[],seen={};"
            + "var imgs=document.querySelectorAll('img');"
            + "for(var i=0;i<imgs.length;i++){var im=imgs[i];var s=im.currentSrc||im.src;"
            + "if(!s||seen[s])continue;var r;try{r=im.getBoundingClientRect();}catch(e){continue;}"
            + "if(r.width<60||r.height<60)continue;seen[s]=1;out.push(s);}return out;}"
            + "function extOf(u,d){"
            + "if(d&&d.indexOf('data:image/')===0){var m=/^data:image\\/([a-zA-Z0-9.+-]+)/.exec(d);"
            + "if(m){var x=m[1].toLowerCase();return x==='jpeg'?'jpg':x;}}"
            + "var q=(u||'').split('?')[0].split('#')[0];var e=/\\.([a-zA-Z0-9]{2,4})$/.exec(q);"
            + "return e?e[1].toLowerCase():'jpg';}"
            + "function sleep(ms){return new Promise(function(r){setTimeout(r,ms);});}"
            + "async function downloadAll(){"
            + "var h=H();if(!h||(!h.saveUrl&&!h.saveImage)){toast('下载桥未就绪');return;}"
            + "var list=collectImgs();if(!list.length){toast('没找到图片');return;}"
            + "toast('开始下载 '+list.length+' 张…');var ok=0,fail=0,ts=Date.now();"
            + "for(var i=0;i<list.length;i++){var u=list[i];"
            + "try{var name='zybclean_'+ts+'_'+(i+1)+'.'+extOf(u,'');"
            + "if(u.indexOf('data:')===0){h.saveImage(name,u);}"
            + "else if(h.saveUrl){h.saveUrl(name,u);}else{throw new Error('no bridge');}"
            + "ok++;}catch(e){fail++;}"
            + "await sleep(120);}"
            + "toast('已保存 '+ok+' 张'+(fail?('，'+fail+' 张失败'):''));}"
            + "function mkBar(){"
            + "if(document.getElementById('zyb-bar')||!document.body)return;"
            + "var bar=document.createElement('div');bar.id='zyb-bar';"
            + "bar.style.cssText='position:fixed;right:10px;bottom:140px;z-index:2147483647;"
            + "display:flex;flex-direction:column;gap:8px;';"
            + "function mk(txt,color,fn){var b=document.createElement('div');b.textContent=txt;"
            + "b.style.cssText='padding:8px 12px;border-radius:16px;color:#fff;font-size:13px;"
            + "line-height:1;text-align:center;box-shadow:0 2px 8px rgba(0,0,0,.25);"
            + "background:'+color+';user-select:none;';"
            + "b.addEventListener('click',function(e){e.stopPropagation();e.preventDefault();fn();},true);return b;}"
            + "bar.appendChild(mk('复制内容','#3b82f6',copyAll));"
            + "bar.appendChild(mk('存长图','#f59e0b',function(){var h=H();"
            + "if(h&&h.shot){toast('生成中…');h.shot();}else{toast('桥未就绪');}}));"
            + "bar.appendChild(mk('下载图片','#10b981',function(){downloadAll();}));"
            + "document.body.appendChild(bar);}"
            + "function gate(){var bar=document.getElementById('zyb-bar');if(!bar)return;"
            + "if(window.__zybCapture){bar.style.display='none';return;}"
            + "var t=(document.body&&document.body.textContent)||'';"
            + "var ok=t.length>200&&/(答案|解析|解答|题目)/.test(t);"
            + "bar.style.display=ok?'flex':'none';}"
            + "function loop(){if(window.__zybCapture)return;scanKill();mkBar();gate();}"

            /* ---------- 4. 常驻（必须防抖：DOM 一变就全量扫会拖死加载） ---------- */
            + "loop();"
            + "var pend=0;"
            + "try{new MutationObserver(function(){"
            + "if(pend)return;pend=1;setTimeout(function(){pend=0;loop();},450);})"
            + ".observe(document.documentElement,{childList:true,subtree:true});}catch(e){}"
            + "setInterval(function(){if(document.hidden)return;loop();},3000);"
            + "})()";
    }

    public static void install(ClassLoader cl) {
        try {
            final Class<?> webView = XposedHelpers.findClass("android.webkit.WebView", cl);
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
            XposedBridge.hookAllMethods(webView, "loadData", reg);

            // 原生桥：页面里的「复制内容 / 下载图片」按钮
            // 长按选字：作业帮可能给 WebView 装了拦截长按的 listener，这里直接不注册
            try {
                Class<?> view = XposedHelpers.findClass("android.view.View", cl);
                XposedBridge.hookAllMethods(view, "setOnLongClickListener", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (webView.isInstance(param.thisObject)) param.setResult(null);
                    }
                });
                XposedBridge.hookAllMethods(view, "setLongClickable", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (webView.isInstance(param.thisObject)
                                && param.args != null && param.args.length > 0) {
                            param.args[0] = Boolean.TRUE;
                        }
                    }
                });
            } catch (Throwable t) {
                HookEntry.log("WebKiller long-click hook failed: " + t);
            }
            HookEntry.log("WebKiller installed");
        } catch (Throwable t) {
            HookEntry.log("WebKiller install failed: " + t);
        }
    }

    private static void inject(final Object webView) {
        ensureBridge(webView);
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

    /** 给 WebView 挂上 java 桥对象（重复调用安全）。 */
    private static void ensureBridge(Object webView) {
        synchronized (BRIDGED) {
            if (BRIDGED.containsKey(webView)) return;
            BRIDGED.put(webView, Boolean.TRUE);
        }
        try {
            Object ctx = XposedHelpers.callMethod(webView, "getContext");
            if (!(ctx instanceof android.content.Context)) return;
            Object bridge = new H5Bridge((android.content.Context) ctx, (android.webkit.WebView) webView);
            XposedHelpers.callMethod(webView, "addJavascriptInterface", bridge, "zybcleanHost");
            HookEntry.log("WebKiller bridge attached");
        } catch (Throwable t) {
            HookEntry.log("WebKiller bridge failed: " + t);
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
