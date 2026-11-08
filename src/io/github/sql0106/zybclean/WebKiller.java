package io.github.sql0106.zybclean;

import io.github.sql0106.zybclean.compat.XC_MethodHook;
import io.github.sql0106.zybclean.compat.XposedBridge;
import io.github.sql0106.zybclean.compat.XposedHelpers;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * H5 净化 + 阅读增强：搜题结果页 / 解答页整页是 NestedHybridWebView 渲染的网页
 * （见 analysis/snap4.txt），DOM 里既有垃圾也有用户真正需要的东西，只能注入 JS。
 *
 * <p>注入的幂等脚本做四件事：</p>
 * <ol>
 *   <li>按真实 class / 定位方式干掉 AI 入口与视频推广浮卡（<b>绝不按文案匹配</b>，用户死线）；</li>
 *   <li>解除页面的选中/复制限制（user-select + 事件拦截），让长按能直接选字复制；</li>
 *   <li>右下角挂四个悬浮按钮：<b>复制内容</b>（整页题目+解答文本进剪贴板）、
 *       <b>存题目 / 存解答</b>（原生整页截图后按「答案」标题切段，公式排版原样保留，见 {@link PageShot}）、
 *       <b>下载图片</b>（收集页面里所有可见图片，逐张存进相册 Pictures/zybclean）；</li>
 *   <li>MutationObserver + 定时扫描，应对 SPA 动态渲染。</li>
 * </ol>
 *
 * <p>保存图片与写剪贴板都走原生 {@link H5Bridge}（页面里的 {@code zybcleanHost}），
 * 原生侧带 WebView 的 Cookie 去下载，绕开跨域限制。</p>
 */
public class WebKiller {

    private static final int MAX_WEBVIEWS = 8;
    /** 每次导航只排 2 次注入：1200ms 管首屏，6000ms 管懒加载 / SPA 二次渲染。 */
    private static final long[] INJECT_DELAYS = {1200L, 6000L};

    /**
     * 已登记的 WebView。<b>必须弱引用</b>：WebView 的 Context 是 Activity，
     * 强引用最多 8 个等于钉住 8 个已销毁的搜题/解答页（每个几十 MB）。
     */
    private static final Set<Object> WEBVIEWS =
            Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());
    /**
     * 每个 WebView 的注入代次：新导航自增，旧导航排下的 Runnable 发现代次不符就直接 no-op，
     * 不会再把上一页的脚本 / {@code __zybNavInfo} 刷回新文档。
     */
    private static final WeakHashMap<Object, int[]> GEN = new WeakHashMap<Object, int[]>();
    private static final WeakHashMap<Object, Boolean> BRIDGED = new WeakHashMap<Object, Boolean>();
    /** 已经挂过长按监听的原生 View（底部蓝色按钮），避免重复挂。 */
    private static final WeakHashMap<Object, Boolean> BOUND_NATIVE = new WeakHashMap<Object, Boolean>();

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

            /* ---------- 1.5 会员推广 / 视频广告（按真实 class，不看文字） ---------- */
            /* 证据：div.video__promotion 是 325x44 的纯背景图广告（zyb-vip.cdnjtzy.com）；
               外层 div.video.vip-video-explain-v2 是「本题精讲」付费视频卡；其余为相关视频/考点视频推广。 */
            + "try{var st2=document.createElement('style');"
            + "st2.textContent='[class*=\"video__promotion\"],[class*=\"vip-video-explain\"],"
            + "[class*=\"single-video__wrapper\"],[class*=\"video__payBox\"],"
            + "[class*=\"video-poster__wrapper\"],[class*=\"poster-player__wrapper\"],"
            + "[class*=\"relate-video\"],[class*=\"paisou-svip-video\"],"
            + "[class*=\"new-poster\"],[class*=\"biz-poster\"]{display:none!important}';"
            + "(document.head||document.documentElement).appendChild(st2);}catch(e){}"

            /* ---------- 2. 干掉垃圾 DOM（只用结构 / class 判定） ---------- */
            /* 用户死线：禁止「取 textContent → 正则/字符串比较 → 隐藏祖先」。
               所以这里一个字都不看：AI 入口按 class / data-role 找，视频浮卡按 class + 定位方式找。
               导航条（#pageNav / ul / li / nav / navWrap / navBox / navItem）内任何节点一律不碰。 */
            + "var WS1=/\\s+/g,WS0=/\\s/g;"
            + "var NAVSEL='#pageNav,[class*=\"navWrap\"],[class*=\"navBox\"],[class*=\"navItem\"],nav,ul,li';"
            + "function inNav(el){try{return !!(el&&el.closest&&el.closest(NAVSEL));}catch(e){return false;}}"
            + "var KILLSEL='[class*=\"ai-tab\"],[class*=\"aiTab\"],[class*=\"ai-tabs\"],[class*=\"ai-entry\"],"
            + "[class*=\"aiEntry\"],[class*=\"ai-solve\"],[class*=\"aiSolve\"],[class*=\"ai-assistant\"],"
            + "[class*=\"aiAssistant\"],[data-role=\"ai\"]';"
            /* 两阶段：第一趟只读（每个候选一次 rect），第二趟统一写。
               边读边写会让每个 display:none 触发一次同步重排（layout thrash）。 */
            + "function scanKill(){try{"
            + "var els=document.querySelectorAll(KILLSEL);"
            + "var n=els.length>200?200:els.length,hit=[];"
            + "for(var i=0;i<n;i++){var el=els[i];if(inNav(el))continue;"
            + "var r;try{r=el.getBoundingClientRect();}catch(e3){continue;}"
            + "if(r.height<130)hit.push(el);}"
            + "for(var k=0;k<hit.length;k++){var e2=hit[k];var p=e2.parentElement;"
            + "if(!p||p.tagName==='BODY'||p.tagName==='HTML'||p.tagName==='LI'||p.tagName==='UL'||p.tagName==='NAV')p=e2;"
            + "if(inNav(p))continue;hide(p);}"
            + "}catch(e){}}"
            /* 悬浮的「讲解中 / 本题精讲」播放卡：class 含 video/poster/player，外层是 fixed/sticky，
               且高度不足 60% 视口（全屏播放器不碰）。同样不看文字。 */
            + "var FLOATSEL='[class*=\"video\"],[class*=\"poster\"],[class*=\"player\"]';"
            + "function scanFloat(){try{"
            + "var els=document.querySelectorAll(FLOATSEL);"
            + "var n=els.length>120?120:els.length,hit=[];"
            + "for(var i=0;i<n;i++){var el=els[i];if(inNav(el))continue;"
            + "var p=el,fx=null;"
            + "for(var d=0;d<8&&p;d++){var cs;try{cs=getComputedStyle(p);}catch(e2){break;}"
            + "if(cs&&(cs.position==='fixed'||cs.position==='sticky')){fx=p;break;}p=p.parentElement;}"
            + "if(!fx||inNav(fx))continue;"
            + "if(fx.clientHeight<window.innerHeight*0.6)hit.push(fx);}"
            + "for(var k=0;k<hit.length;k++)hide(hit[k]);"
            + "}catch(e){}}"
            /* 顶部选项卡一旦被任何旧规则藏掉，这里强制还原，保证「结果N」都在。 */
            + "function repairNav(){try{"
            + "var ns=document.querySelectorAll('#pageNav [class*=\"navItem\"],[class*=\"navBox\"]>*');"
            + "var info=[];"
            + "for(var i=0;i<ns.length;i++){var n=ns[i];"
            + "var all=[n];"
            + "try{var ds=n.querySelectorAll('*');for(var j=0;j<ds.length;j++)all.push(ds[j]);}catch(e5){}"
            + "for(var k=0;k<all.length;k++){var x=all[k];if(!x.style)continue;"
            + "if(x.style.display==='none')x.style.display='';"
            + "if(x.style.visibility==='hidden')x.style.visibility='';"
            + "if(x.style.opacity==='0')x.style.opacity='';}"
            + "info.push((n.textContent||'').replace(WS1,'')+'/'+(n.style.display||'-'));}"
            + "window.__zybNavInfo=info.join(' | ');"
            + "}catch(e){}}"
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
            + "function normTex(s){return String(s||'').replace(WS1,' ').trim();}"
            + "function vis(e){try{var cs=getComputedStyle(e);"
            + "if(cs.display==='none'||cs.visibility==='hidden'||cs.opacity==='0')return false;"
            + "if(e.getAttribute&&e.getAttribute('aria-hidden')==='true')return false;"
            + "var r=e.getBoundingClientRect();"
            + "if(r.width<=0&&r.height<=0&&cs.position!=='fixed')return false;"
            + "return true;}catch(x){return true;}}"
            + "function imgTex(im){var ks=['data-latex','data-tex','alt','title','aria-label'];"
            + "for(var i=0;i<ks.length;i++){var v=im.getAttribute(ks[i]);"
            + "if(v&&v.replace(WS0,'')){var s=v.trim();return /[\\\\^_{}]/.test(s)&&s.indexOf('$')<0?('$'+s+'$'):s;}}"
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
            + "if(tx&&tx.replace(WS0,'')){if(vis(e.parentNode||e))out.push(tx);}return;}"
            + "if(!vis(e))return;"
            + "var n=e.childNodes?e.childNodes.length:0;"
            + "for(var i=0;i<n;i++)walk(e.childNodes[i],out,d+1);"
            + "if(BLOCK[tag])out.push('\\n');}"
            + "function pageText(){try{FC=0;var out=[];walk(document.body,out,0);"
            + "var s=out.join('').replace(/\\r/g,'').replace(/[ \\t\\u00a0]+/g,' ');"
            + "s=s.replace(/ *\\n */g,'\\n').replace(/\\n{3,}/g,'\\n\\n').trim();"
            + "var lines=s.split('\\n'),keep=[];"
            + "for(var i=0;i<lines.length;i++){var L=lines[i].replace(WS0,'');"
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
            /* 位置：可拖动，拖动后记在 localStorage；换页重新注入时沿用上次位置。 */
            + "var bw=86,bh=200,lx=window.innerWidth-bw-10,ly=window.innerHeight-140-bh;"
            + "try{var ps=localStorage.getItem('zyb_bar_pos');if(ps){var pa=ps.split(',');lx=parseFloat(pa[0]);ly=parseFloat(pa[1]);}}catch(e0){}"
            + "if(!(lx>=0&&lx<window.innerWidth-40))lx=window.innerWidth-bw-10;"
            + "if(!(ly>=0&&ly<window.innerHeight-40))ly=window.innerHeight-140-bh;"
            + "bar.style.cssText='position:fixed;left:'+Math.round(lx)+'px;top:'+Math.round(ly)+'px;"
            + "z-index:2147483647;display:flex;flex-direction:column;gap:8px;';"
            + "bar.style.opacity=String(window.__zybBarOp||1);"
            + "function mk(txt,color,fn){var b=document.createElement('div');b.textContent=txt;"
            + "b.style.cssText='padding:8px 12px;border-radius:16px;color:#fff;font-size:13px;"
            + "line-height:1;text-align:center;box-shadow:0 2px 8px rgba(0,0,0,.25);"
            + "background:'+color+';user-select:none;';"
            + "b.addEventListener('click',function(e){e.stopPropagation();e.preventDefault();"
            /* 拖动结束会补发一次 click，用它拦住误触。 */
            + "if(window.__zybBarMoved){window.__zybBarMoved=0;return;}fn();},true);return b;}"
            + "function pt(e){var t=(e.touches&&e.touches[0])||e;return {x:t.clientX,y:t.clientY};}"
            + "var px=0,py=0,ox=0,oy=0,drag=0;"
            + "bar.addEventListener('touchstart',function(e){var p=pt(e);var r=bar.getBoundingClientRect();"
            + "ox=r.left;oy=r.top;px=p.x;py=p.y;drag=0;window.__zybBarMoved=0;"
            /* 长按 700ms 整条消失；本页内不再出现，换页/再拍一页重新注入时自然恢复。 */
            + "clearTimeout(window.__zybHold);"
            + "window.__zybHold=setTimeout(function(){if(!drag){window.__zybBarOp=0;bar.style.display='none';}},700);"
            + "},true);"
            + "bar.addEventListener('touchmove',function(e){var p=pt(e);var mx=p.x-px,my=p.y-py;"
            + "if(!drag&&(Math.abs(mx)>6||Math.abs(my)>6)){drag=1;window.__zybBarMoved=1;clearTimeout(window.__zybHold);}"
            + "if(!drag)return;e.preventDefault();"
            + "bar.style.left=Math.round(Math.max(0,Math.min(window.innerWidth-30,ox+mx)))+'px';"
            + "bar.style.top=Math.round(Math.max(0,Math.min(window.innerHeight-30,oy+my)))+'px';},true);"
            + "bar.addEventListener('touchend',function(e){clearTimeout(window.__zybHold);"
            + "if(drag){var r=bar.getBoundingClientRect();"
            + "try{localStorage.setItem('zyb_bar_pos',Math.round(r.left)+','+Math.round(r.top));}catch(e1){}"
            + "setTimeout(function(){window.__zybBarMoved=0;},150);}},true);"
            + "bar.appendChild(mk('复制内容','#3b82f6',copyAll));"
            + "bar.appendChild(mk('存题目','#f59e0b',function(){var h=H();"
            + "if(h&&h.shotPart){toast('生成中…');h.shotPart('q');}else{toast('桥未就绪');}}));"
            + "bar.appendChild(mk('存解答','#f59e0b',function(){var h=H();"
            + "if(h&&h.shotPart){toast('生成中…');h.shotPart('a');}else{toast('桥未就绪');}}));"
            + "bar.appendChild(mk('下载图片','#10b981',function(){downloadAll();}));"
            /* 隐藏按钮：点一下变半透明（仍可拖），再点恢复。 */
            + "var hideBtn=null;"
            + "hideBtn=mk('隐藏','#64748b',function(){var v=(window.__zybBarOp||1)>0.5?0.35:1;"
            + "window.__zybBarOp=v;bar.style.opacity=String(v);hideBtn.textContent=v>0.5?'隐藏':'显示';});"
            + "if((window.__zybBarOp||1)<=0.5)hideBtn.textContent='显示';"
            + "hideBtn.setAttribute('data-zybhide','1');"
            + "bar.appendChild(hideBtn);"
            + "document.body.appendChild(bar);}"
            /* 长按底部的原生蓝色按钮时，由 Java 侧调用这里把整条浮窗叫回来。 */
            + "window.__zybBarShow=function(){window.__zybBarOp=1;"
            + "var b=document.getElementById('zyb-bar');"
            + "if(b){b.style.display='flex';b.style.opacity='1';"
            + "var hb=b.querySelector('[data-zybhide]');if(hb)hb.textContent='隐藏';}"
            + "return true;};"
            /* 浮窗是否显示只看「这页有没有正文」。旧实现每 450ms 读一次整页 textContent（50~200KB），
               这里改成缓存：仅 URL 变化、或有新 DOM 变更且上次未通过时才重算，长度存 window.__zybBodyLen。 */
            + "var GARE=/(答案|解析|解答|题目)/;"
            + "var gateUrl='',gateOk=false,gateMut=-1,gateTries=0;"
            + "function gateMeasure(){try{var b=document.body;var t=b?(b.textContent||''):'';"
            + "window.__zybBodyLen=t.length;return t.length>200&&GARE.test(t);}catch(e){return false;}}"
            + "function gate(){var bar=document.getElementById('zyb-bar');if(!bar)return;"
            /* __zybBarOp===0 是长按消失，gate 不能把它拉回来。 */
            + "if(window.__zybCapture||window.__zybBarOp===0){bar.style.display='none';return;}"
            + "var m=window.__zybMut||0;"
            /* 整页 textContent 读取是有成本的（50~200KB）：每次导航最多量 3 次，
               量过就永久缓存，不再随 DOM 变更反复读整页。 */
            + "if(gateUrl!==location.href){gateUrl=location.href;gateOk=false;gateMut=-1;gateTries=0;}"
            + "if(!gateOk&&gateMut!==m&&gateTries<3){gateMut=m;gateTries++;gateOk=gateMeasure();}"
            + "bar.style.display=gateOk?'flex':'none';}"
            /* 脏判定：没有 DOM 变更就不再反复全扫；首轮与 3s 心跳强制跑（页面自渲染不靠 DOM 变更也能修）。 */
            + "var lastMut=-1;"
            + "function loop(force){if(window.__zybCapture)return;"
            + "var m=window.__zybMut||0;"
            + "if(force||m!==lastMut){lastMut=m;scanKill();scanFloat();repairNav();}"
            + "mkBar();gate();}"

            /* ---------- 4. 常驻（必须防抖：DOM 一变就全量扫会拖死加载） ---------- */
            + "loop(true);"
            + "var pend=0;"
            + "try{new MutationObserver(function(){"
            + "window.__zybMut=(window.__zybMut||0)+1;"
            + "if(pend)return;pend=1;setTimeout(function(){pend=0;loop(false);},450);})"
            + ".observe(document.documentElement,{childList:true,subtree:true});}catch(e){}"
            + "setInterval(function(){if(document.hidden)return;loop(true);},3000);"
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
            // 不管页面是刚开还是早就开着：只要作业帮某个 Activity 回到前台，就补一次定位（每个根 View 只扫一次）。
            try {
                Class<?> activity = XposedHelpers.findClass("android.app.Activity", cl);
                XposedBridge.hookAllMethods(activity, "onResume", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (!param.thisObject.getClass().getName()
                                    .startsWith("com.baidu.homework")) return;
                            Object win = XposedHelpers.callMethod(param.thisObject, "getWindow");
                            if (win == null) return;
                            final Object decor = XposedHelpers.callMethod(win, "getDecorView");
                            if (decor == null) return;
                            if (BIND_ROOTS.containsKey(decor)) return;
                            XposedHelpers.callMethod(decor, "postDelayed", new Runnable() {
                                @Override
                                public void run() {
                                    bindNativeLongPress(decor);
                                }
                            }, Long.valueOf(800L));
                        } catch (Throwable ignored) {
                        }
                    }
                });
            } catch (Throwable t) {
                HookEntry.log("WebKiller resume hook failed: " + t);
            }
            HookEntry.log("WebKiller installed");
        } catch (Throwable t) {
            HookEntry.log("WebKiller install failed: " + t);
        }
    }

    private static void inject(final Object webView) {
        ensureBridge(webView);
        remember(webView); // 只做登记；每次导航都要重新注入（新文档会丢掉 __zybclean 标记）
        // 代次令牌：本次导航排下的定时器只认自己的代次；被后续导航取代后直接 no-op，
        // 不会再往新文档里灌旧脚本、也不会把上一页的 __zybNavInfo 刷回来。
        final int[] gen;
        final int myGen;
        synchronized (GEN) {
            int[] g = GEN.get(webView);
            if (g == null) {
                g = new int[]{0};
                GEN.put(webView, g);
            }
            g[0]++;
            gen = g;
            myGen = g[0];
        }
        for (long delay : INJECT_DELAYS) {
            try {
                XposedHelpers.callMethod(webView, "postDelayed", new Runnable() {
                    @Override
                    public void run() {
                        synchronized (GEN) {
                            if (gen[0] != myGen) return;
                        }
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
        // 底部蓝色「再拍一页」是原生按钮，长按它唤回浮窗。
        // 同一 WebView 只在首次导航时挂一次；绑不上靠 1 次重试 + Activity onResume 兜底。
        if (myGen == 1) scheduleBind(webView, 0);
        if (BuildFlags.PROBES) {
            // 诊断：把网页里顶部选项卡的真实内容打出来，便于确认「结果N」到底存不存在。
            // 只在 debug 包（BuildFlags.PROBES=true）编进来，正式包 javac 整段消除。
            try {
                XposedHelpers.callMethod(webView, "postDelayed", new Runnable() {
                    @Override
                    public void run() {
                        try {
                            XposedHelpers.callMethod(webView, "evaluateJavascript",
                                    "window.__zybNavInfo||'(none)'", new android.webkit.ValueCallback<String>() {
                                        @Override
                                        public void onReceiveValue(String value) {
                                            HookEntry.log("WebKiller nav: " + value);
                                        }
                                    });
                        } catch (Throwable ignored) {
                        }
                    }
                }, Long.valueOf(5000L));
            } catch (Throwable ignored) {
            }
        }
    }

    /** 每个根 View 只做一次全树定位，之后不再重复遍历（作业帮页面本来就卡）。 */
    private static final WeakHashMap<Object, Boolean> BIND_ROOTS = new WeakHashMap<Object, Boolean>();

    private static void scheduleBind(final Object webView, final int attempt) {
        try {
            Object root = XposedHelpers.callMethod(webView, "getRootView");
            if (root instanceof android.view.View && BIND_ROOTS.containsKey(root)) return;
            XposedHelpers.callMethod(webView, "postDelayed", new Runnable() {
                @Override
                public void run() {
                    int n = bindNativeLongPress(webView);
                    if (n <= 0 && attempt < 1) scheduleBind(webView, attempt + 1);
                }
            }, Long.valueOf(1800L));
        } catch (Throwable ignored) {
        }
    }

    /**
     * 底栏原生组件（全限定类名，App 升级也不变）：整条底栏都在这里面。
     */
    private static final String BOTTOM_BAR_CLASS =
            "com.baidu.homework.activity.search.newsearchv3.ai.AISearchBottomInputView";

    /**
     * 实测 resource id（真机 dumpsys activity top，14.34.0/1738）：
     * #7f090be3 = 蓝按钮容器 LinearLayout 399,28-1238,182（839x154）
     * #7f0911f7 = 容器里可点击的 FrameLayout 0,0-839,154（VFE...C..）
     */
    private static final int ID_BLUE_WRAP = 0x7f090be3;
    private static final int ID_BLUE_TAP = 0x7f0911f7;

    /**
     * 长按底部蓝色「再拍一页 / 再拍一题」→ 唤回网页里被隐藏的悬浮工具条。
     * 定位顺序：精确 resource id → 容器 id → 底栏类名内按几何（宽≥40%、高 60~320）。
     * 不读任何文字；每个根 View 只做一次，绑上即停。
     */
    private static int bindNativeLongPress(Object anchor) {
        try {
            android.view.View rv = rootOf(anchor);
            if (rv == null || rv.getWidth() <= 0) return 0;
            if (BIND_ROOTS.containsKey(rv)) return 0;
            android.view.View target = findBlueButton(rv);
            int bound = 0;
            if (target != null && bindOne(target)) bound = 1;
            if (bound > 0) BIND_ROOTS.put(rv, Boolean.TRUE);
            if (bound > 0 || bindLogs < 6) {
                bindLogs++;
                HookEntry.log("WebKiller bind native: root=" + rv.getWidth() + "x" + rv.getHeight()
                        + " found=" + (target == null ? "null" : describe(target))
                        + " bound=" + bound);
            }
            return bound;
        } catch (Throwable t) {
            HookEntry.log("WebKiller bind native failed: " + t);
            return 0;
        }
    }

    private static int bindLogs;

    private static android.view.View rootOf(Object anchor) {
        try {
            Object root = XposedHelpers.callMethod(anchor, "getRootView");
            if (root instanceof android.view.View) return (android.view.View) root;
        } catch (Throwable ignored) {
        }
        if (anchor instanceof android.view.View) return ((android.view.View) anchor).getRootView();
        return null;
    }

    private static String describe(android.view.View v) {
        try {
            return v.getClass().getName() + "#" + Integer.toHexString(v.getId())
                    + " " + v.getWidth() + "x" + v.getHeight();
        } catch (Throwable t) {
            return "?";
        }
    }

    private static android.view.View findBlueButton(android.view.View root) {
        android.view.View v = null;
        try {
            v = root.findViewById(ID_BLUE_TAP);
        } catch (Throwable ignored) {
        }
        if (v == null) {
            try {
                v = root.findViewById(ID_BLUE_WRAP);
            } catch (Throwable ignored) {
            }
        }
        if (v != null && v.getWidth() > 0) return tapOf(v);
        // id 变了（App 升级）：只在这一条底栏内部找，不做全树瞎扫。
        android.view.View bar = findByClass(root, BOTTOM_BAR_CLASS, 0);
        if (bar == null) return null;
        int barW = bar.getWidth();
        if (barW <= 0) return null;
        return findWideClickable(bar, barW, 0);
    }

    /** 挂到真正可点击的那一层（容器本身不可点，里面那层 FrameLayout 才是）。 */
    private static android.view.View tapOf(android.view.View v) {
        if (!(v instanceof android.view.ViewGroup)) return v;
        android.view.ViewGroup g = (android.view.ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) {
            android.view.View c = g.getChildAt(i);
            if (c != null && c.isClickable() && c.getWidth() > 0) return c;
        }
        return v;
    }

    private static android.view.View findByClass(android.view.View v, String name, int depth) {
        if (depth > 40) return null;
        if (name.equals(v.getClass().getName())) return v;
        if (!(v instanceof android.view.ViewGroup)) return null;
        android.view.ViewGroup g = (android.view.ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) {
            android.view.View c = g.getChildAt(i);
            if (c == null) continue;
            android.view.View r = findByClass(c, name, depth + 1);
            if (r != null) return r;
        }
        return null;
    }

    private static android.view.View findWideClickable(android.view.View v, int barW, int depth) {
        if (depth > 20) return null;
        if (!(v instanceof android.view.ViewGroup)) return null;
        android.view.ViewGroup g = (android.view.ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) {
            android.view.View c = g.getChildAt(i);
            if (c == null || c.getVisibility() != android.view.View.VISIBLE) continue;
            if (c instanceof android.webkit.WebView) continue;
            int cw = c.getWidth();
            int ch = c.getHeight();
            if (cw >= barW * 40 / 100 && ch >= 60 && ch <= 320) {
                android.view.View tap = tapOf(c);
                return tap == null ? c : (tap.isClickable() ? tap : c);
            }
            android.view.View r = findWideClickable(c, barW, depth + 1);
            if (r != null) return r;
        }
        return null;
    }

    private static boolean bindOne(final android.view.View target) {
        synchronized (BOUND_NATIVE) {
            // 已绑过：算成功（让调用方落 BIND_ROOTS，不再每次 onResume 重扫）。
            if (BOUND_NATIVE.containsKey(target)) return true;
        }
        android.view.View.OnLongClickListener l = new android.view.View.OnLongClickListener() {
            @Override
            public boolean onLongClick(android.view.View v) {
                revealBars();
                return true;
            }
        };
        try {
            target.setOnLongClickListener(l);
            target.setLongClickable(true);
            // 成功之后才记标记：失败不写，下次还能重试（否则一次抖动 = 长按永久失效）。
            synchronized (BOUND_NATIVE) {
                BOUND_NATIVE.put(target, Boolean.TRUE);
            }
            HookEntry.log("WebKiller native long-press bound: "
                    + target.getClass().getName() + " " + target.getWidth() + "x" + target.getHeight());
            return true;
        } catch (Throwable t) {
            HookEntry.log("WebKiller native long-press bind failed: " + t);
            return false;
        }
    }

    /** 让所有已登记的 WebView 把悬浮工具条重新显示出来。 */
    private static void revealBars() {
        Object[] list;
        synchronized (WEBVIEWS) {
            list = WEBVIEWS.toArray(); // 快照，避免遍历时被并发 add/remove
        }
        int n = 0;
        for (Object o : list) {
            if (o == null) continue;
            n++;
            try {
                XposedHelpers.callMethod(o, "evaluateJavascript",
                        "window.__zybBarShow&&window.__zybBarShow()", null);
            } catch (Throwable ignored) {
            }
        }
        HookEntry.log("WebKiller native long-press: reveal " + n + " webview(s)");
    }

    /** 给 WebView 挂上 java 桥对象（重复调用安全）。 */
    private static void ensureBridge(Object webView) {
        synchronized (BRIDGED) {
            if (BRIDGED.containsKey(webView)) return;
        }
        try {
            Object ctx = XposedHelpers.callMethod(webView, "getContext");
            if (!(ctx instanceof android.content.Context)) return;
            Object bridge = new H5Bridge((android.content.Context) ctx, (android.webkit.WebView) webView);
            XposedHelpers.callMethod(webView, "addJavascriptInterface", bridge, "zybcleanHost");
            // 挂上之后才记标记：失败不写，下次导航会重试（旧实现一次失败 = 整页复制/存图永久失效）。
            synchronized (BRIDGED) {
                BRIDGED.put(webView, Boolean.TRUE);
            }
            HookEntry.log("WebKiller bridge attached");
        } catch (Throwable t) {
            HookEntry.log("WebKiller bridge failed: " + t);
        }
    }

    private static boolean remember(Object webView) {
        synchronized (WEBVIEWS) {
            if (WEBVIEWS.contains(webView)) return false;
            WEBVIEWS.add(webView);
            // 弱集合没有顺序，超限时随便移除一个「不是刚加入的」引用即可（被回收的也会自动消失）。
            int over = WEBVIEWS.size() - MAX_WEBVIEWS;
            if (over > 0) {
                Iterator<Object> it = WEBVIEWS.iterator();
                while (over > 0 && it.hasNext()) {
                    if (it.next() != webView) {
                        it.remove();
                        over--;
                    }
                }
            }
            return true;
        }
    }
}
