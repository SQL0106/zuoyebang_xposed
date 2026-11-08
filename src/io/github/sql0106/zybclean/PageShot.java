package io.github.sql0106.zybclean;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewParent;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;

/**
 * 整页长截图：把当前 WebView 里渲染好的解答（题目 + 公式 + 排版）拼成一张图片。
 *
 * <p>文本提取会丢公式、混进隐藏 tab 与内联脚本，所以不抽文本，改为拍照拼接。</p>
 *
 * <p><b>两种滚动模式</b>（作业帮的解答页可能两者都有）：</p>
 * <ul>
 *   <li><b>JS 滚动</b>：网页自己的滚动条（`scrollHeight > clientHeight`）→ 用 {@code scrollTo(y)} 步进；</li>
 *   <li><b>原生滚动</b>：WebView 被撑成整页高度、真正的滚动在原生父容器（NestedScrollView/RecyclerView）里，
 *       网页自己的滚动条永远是 0 → 改用 {@code View#scrollBy} 滚外层，并且**每屏只裁可见区域**，
 *       否则 Chromium 没栅格化的部分画出来是空白。</li>
 * </ul>
 */
public final class PageShot {

    private static final int MAX_STEPS = 60;
    private static final long SETTLE_MS = 460L;
    private static final double MAX_PIXELS = 1.2e7;
    private static final int MAX_OUT_HEIGHT = 20000;

    private static final int MODE_JS = 1;
    private static final int MODE_NATIVE = 2;
    private static final int MODE_SINGLE = 3;

    private static volatile boolean sBusy = false;

    private PageShot() {
    }

    public static void capture(final Context context, final WebView wv) {
        if (sBusy) {
            toast(context, "正在生成，请稍候");
            return;
        }
        if (wv == null || wv.getWidth() <= 0 || wv.getHeight() <= 0) {
            toast(context, "页面还没准备好");
            return;
        }
        sBusy = true;
        new Job(context.getApplicationContext(), wv).start();
    }

    private static void toast(Context ctx, String msg) {
        try {
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    private static final class Job implements Runnable {

        private final Context ctx;
        private final WebView wv;
        private final Handler main = new Handler(Looper.getMainLooper());

        /* JS 回报 */
        private int cssW;
        private int cssScrollH;
        private int cssClientH;
        private int cssInnerH;
        private int rectX;
        private int rectY;
        private int rectW;
        private int rectH;
        private boolean innerScroller;

        /* 可见区域（WebView 本地坐标，设备 px） */
        private final Rect vis = new Rect();

        private float density = 1f;
        private float scale = 1f;
        private int outW;
        private int outH;
        private Bitmap out;

        private int mode = MODE_SINGLE;
        private int step;
        private int steps;
        private int accOffset;
        private View scroller;
        private int scrollerStartY;

        Job(Context ctx, WebView wv) {
            this.ctx = ctx;
            this.wv = wv;
        }

        void start() {
            try {
                localVisible();
                wv.evaluateJavascript(PREP, new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        if (!parse(value)) {
                            done(false, "页面还没准备好：" + value);
                            return;
                        }
                        begin();
                    }
                });
            } catch (Throwable t) {
                done(false, "截图失败：" + t);
            }
        }

        /** WebView 在屏幕上有多少真正可见（父容器可能裁剪它）。 */
        private void localVisible() {
            Rect r = new Rect();
            if (wv.getGlobalVisibleRect(r)) {
                int[] loc = new int[2];
                wv.getLocationOnScreen(loc);
                r.offset(-loc[0], -loc[1]);
                vis.set(r);
            } else {
                vis.set(0, 0, wv.getWidth(), wv.getHeight());
            }
            if (vis.width() <= 0 || vis.height() <= 0) vis.set(0, 0, wv.getWidth(), wv.getHeight());
        }

        private boolean parse(String raw) {
            if (raw == null) return false;
            String s = raw.replace("\"", "").replace("\\", "").trim();
            String[] p = s.split("\\|");
            if (p.length < 10) return false;
            try {
                cssW = Integer.parseInt(p[0].trim());
                cssScrollH = Integer.parseInt(p[1].trim());
                cssClientH = Integer.parseInt(p[2].trim());
                cssInnerH = Integer.parseInt(p[3].trim());
                rectX = Integer.parseInt(p[4].trim());
                rectY = Integer.parseInt(p[5].trim());
                rectW = Integer.parseInt(p[6].trim());
                rectH = Integer.parseInt(p[7].trim());
                innerScroller = "1".equals(p[8].trim());
                return cssW > 0 && cssScrollH > 0 && cssClientH > 0 && rectW > 0 && rectH > 0;
            } catch (Throwable t) {
                return false;
            }
        }

        private void begin() {
            try {
                density = wv.getWidth() / (float) cssW;
                if (density <= 0) {
                    done(false, "页面尺寸异常");
                    return;
                }
                // 网页自己能滚（真实滚动条）→ JS 模式；否则 WebView 被撑高 → 滚原生外层
                boolean jsScroll = cssScrollH > cssClientH + 8 && !innerScroller;
                if (jsScroll) {
                    mode = MODE_JS;
                    step = cssClientH;
                    steps = (int) Math.ceil(cssScrollH / (double) cssClientH);
                    outH = (int) Math.round(Math.min(cssScrollH, (double) cssClientH * steps) * density);
                } else if (wv.getHeight() > vis.height() + 8) {
                    mode = MODE_NATIVE;
                    step = vis.height();
                    steps = (int) Math.ceil(wv.getHeight() / (double) step);
                    outH = wv.getHeight();
                } else {
                    mode = MODE_SINGLE;
                    steps = 1;
                    outH = vis.height();
                }
                if (steps < 1) steps = 1;
                if (steps > MAX_STEPS) steps = MAX_STEPS;

                double pixW = vis.width();
                double pixH = outH;
                scale = 1f;
                if (pixW * pixH > MAX_PIXELS) scale = (float) Math.sqrt(MAX_PIXELS / (pixW * pixH));
                outW = Math.max(1, Math.round(vis.width() * scale));
                outH = Math.max(1, Math.round(outH * scale));
                if (outH > MAX_OUT_HEIGHT) {
                    scale *= MAX_OUT_HEIGHT / (float) outH;
                    outW = Math.max(1, Math.round(vis.width() * scale));
                    outH = MAX_OUT_HEIGHT;
                }
                out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
                out.eraseColor(0xFFFFFFFF);

                if (mode == MODE_NATIVE) {
                    scroller = findScroller(wv);
                    if (scroller != null) scrollerStartY = scroller.getScrollY();
                }
                HookEntry.log("PageShot start: mode=" + modeOf()
                        + " css=" + cssW + "x" + cssScrollH + " client=" + cssClientH
                        + " innerH=" + cssInnerH + " rootScroll=" + (cssScrollH > cssClientH + 8)
                        + " innerScroller=" + innerScroller
                        + " rect=" + rectX + "," + rectY + " " + rectW + "x" + rectH
                        + " wv=" + wv.getWidth() + "x" + wv.getHeight() + " vis=" + vis.toShortString()
                        + " scroller=" + (scroller == null ? "none" : scroller.getClass().getName())
                        + " steps=" + steps + " out=" + outW + "x" + outH);
                if (mode == MODE_NATIVE && scroller == null) {
                    done(false, "找不到可滚动的外层容器");
                    return;
                }
                toast(ctx, "正在生成整页长图（" + steps + " 屏）…");
                main.postDelayed(this, SETTLE_MS);
            } catch (Throwable t) {
                done(false, "创建画布失败：" + t);
            }
        }

        private String modeOf() {
            return mode == MODE_JS ? "js" : (mode == MODE_NATIVE ? "native" : "single");
        }

        /** 每屏：滚动 → 等待渲染 → 画一帧 → 贴到总图。 */
        @Override
        public void run() {
            if (step >= steps) {
                finish();
                return;
            }
            if (mode == MODE_JS) {
                jsScrollStep();
            } else if (mode == MODE_NATIVE) {
                nativeScrollStep();
            } else {
                grab(0, 0);
                finish();
            }
        }

        private void jsScrollStep() {
            int want = step * cssClientH;
            if (want > cssScrollH - cssClientH) want = Math.max(0, cssScrollH - cssClientH);
            final int wantY = want;
            try {
                wv.evaluateJavascript(
                        "(function(){try{return window.__zybShot?window.__zybShot.scrollTo(" + wantY + "):-1;}"
                                + "catch(e){return -1;}})()",
                        new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String value) {
                                int actual = toInt(value);
                                HookEntry.log("PageShot js step " + step + " want=" + wantY
                                        + " actual=" + actual);
                                grab(wantY, 0);
                            }
                        });
            } catch (Throwable t) {
                done(false, "滚动失败：" + t);
            }
        }

        private void nativeScrollStep() {
            int before = scroller.getScrollY();
            if (step > 0) {
                try {
                    scroller.scrollBy(0, stepPx());
                } catch (Throwable t) {
                    HookEntry.log("PageShot scrollBy failed: " + t);
                }
            }
            int after = scroller.getScrollY();
            int delta = after - before;
            accOffset += delta;
            if (step > 0 && delta <= 0) {
                HookEntry.log("PageShot native stop at step " + step + " y=" + after);
                finish();
                return;
            }
            HookEntry.log("PageShot native step " + step + " y=" + after + " delta=" + delta);
            grab(0, accOffset);
        }

        private int stepPx() {
            return Math.round(step * density);
        }

        /**
         * 画一帧并贴到总图。
         *
         * @param cssY   JS 模式下这一屏的内容起点（CSS px），0 表示用当前定位
         * @param nativeY 原生模式下累计滚动位移（设备 px）
         */
        private void grab(int cssY, int nativeY) {
            Bitmap frame = null;
            try {
                frame = Bitmap.createBitmap(wv.getWidth(), wv.getHeight(), Bitmap.Config.ARGB_8888);
                frame.eraseColor(0xFFFFFFFF);
                wv.draw(new Canvas(frame));

                Rect src;
                if (mode == MODE_NATIVE || mode == MODE_SINGLE) {
                    src = new Rect(vis);
                } else {
                    src = new Rect(
                            Math.round(rectX * density), Math.round(rectY * density),
                            Math.round((rectX + rectW) * density), Math.round((rectY + rectH) * density));
                }
                if (!src.intersect(0, 0, frame.getWidth(), frame.getHeight())) {
                    done(false, "可见区域异常");
                    return;
                }

                int dstTop;
                if (mode == MODE_JS) {
                    dstTop = Math.round(cssY * density * scale);
                } else {
                    dstTop = Math.round(nativeY * scale);
                }
                int dstH = Math.round(src.height() * scale);
                if (dstTop >= outH) {
                    finish();
                    return;
                }
                int dstBottom = Math.min(outH, dstTop + dstH);
                Canvas c = new Canvas(out);
                c.drawBitmap(frame, src, new Rect(0, dstTop, outW, dstBottom),
                        new Paint(Paint.FILTER_BITMAP_FLAG));
                step++;
                if (step >= steps) finish();
                else main.postDelayed(this, SETTLE_MS);
            } catch (Throwable t) {
                done(false, "绘制失败：" + t);
            } finally {
                if (frame != null) frame.recycle();
            }
        }

        private void finish() {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream(256 * 1024);
                out.compress(Bitmap.CompressFormat.PNG, 100, bos);
                byte[] data = bos.toByteArray();
                String name = "zybclean_" + android.text.format.DateFormat.format(
                        "yyyyMMdd_HHmmss", System.currentTimeMillis()) + ".png";
                String path = H5Bridge.saveBytes(ctx, name, "image/png", data);
                HookEntry.log("PageShot saved: mode=" + modeOf() + " steps=" + step
                        + " size=" + data.length + " B path=" + path);
                done(true, path == null || path.length() == 0 ? "保存失败" : "长图已保存到相册");
            } catch (Throwable t) {
                done(false, "保存失败：" + t);
            }
        }

        private void done(boolean ok, String msg) {
            restoreNative();
            try {
                wv.evaluateJavascript(RESTORE, null);
            } catch (Throwable ignored) {
            }
            try {
                if (out != null) out.recycle();
            } catch (Throwable ignored) {
            }
            out = null;
            sBusy = false;
            if (msg != null && msg.length() > 0) toast(ctx, msg);
            if (!ok) HookEntry.log("PageShot failed: " + msg);
        }

        private void restoreNative() {
            if (scroller != null) {
                try {
                    scroller.scrollTo(scroller.getScrollX(), scrollerStartY);
                } catch (Throwable ignored) {
                }
            }
        }

        private static int toInt(String v) {
            try {
                return Integer.parseInt(v == null ? "-1" : v.replace("\"", "").trim());
            } catch (Throwable t) {
                return -1;
            }
        }

        /** 找 WebView 外层真正能滚的 View（NestedScrollView / RecyclerView 都支持 scrollBy）。 */
        private static View findScroller(View v) {
            ViewParent p = v.getParent();
            while (p instanceof View) {
                View pv = (View) p;
                int before = pv.getScrollY();
                try {
                    pv.scrollBy(0, 1);
                } catch (Throwable ignored) {
                }
                int after = pv.getScrollY();
                if (after != before) {
                    try {
                        pv.scrollTo(pv.getScrollX(), before);
                    } catch (Throwable ignored) {
                    }
                    return pv;
                }
                p = pv.getParent();
            }
            return null;
        }
    }

    /**
     * 探测滚动信息并临时隐藏浮层。返回
     * {@code 宽|scrollH|clientH|innerH|rectX|rectY|rectW|rectH|innerScroller}。
     */
    private static final String PREP =
            "(function(){try{"
                    + "if(window.__zybShot)return window.__zybShot.info();"
                    + "var hidden=[];"
                    + "function hide(el){try{hidden.push(el);"
                    + "el.style.setProperty('display','none','important');}catch(e){}}"
                    + "var all=document.querySelectorAll('body *');"
                    + "for(var i=0;i<all.length;i++){var e=all[i];"
                    + "if(e.id==='zyb-bar'){hidden.push(e);"
                    + "e.style.setProperty('display','none','important');continue;}"
                    + "var cs;try{cs=getComputedStyle(e);}catch(x){continue;}"
                    + "if(!cs)continue;"
                    + "if(cs.position==='fixed'||cs.position==='sticky'){"
                    + "var r=e.getBoundingClientRect();"
                    + "if(r.width>0&&r.height>0&&r.height<window.innerHeight*0.6){"
                    + "hidden.push(e);e.style.setProperty('display','none','important');}}}"
                    /* 垃圾清理：视频讲解卡、广告 banner、促销条一律不进图 */
                    + "var KILL=[/本题精讲/,/讲解(中|视频)/,/视频讲解/,/双十一/,/^广告$/,"
                    + "/开通会员/,/立即开通/,/限时/,/秒杀/,/领券/,/领取/];"
                    + "function upHide(e){var t=e;"
                    + "for(var i=0;i<5&&t&&t.parentElement&&t!==document.body;i++){"
                    + "var r=t.getBoundingClientRect();"
                    + "if(r.height>=60&&r.height<window.innerHeight*0.55"
                    + "&&r.width>=window.innerWidth*0.5){hide(t);return;}"
                    + "t=t.parentElement;}hide(e);}"
                    + "var ADRE=/(^|[\\s_-])(ad|ads|advert|advertisement|banner|promo|gg)([\\s_-]|$)/i;"
                    + "var nodes=document.querySelectorAll('body *');"
                    + "for(var k=0;k<nodes.length;k++){var n2=nodes[k];"
                    + "var tag=n2.tagName;"
                    + "if(tag==='VIDEO'||tag==='IFRAME'){upHide(n2);continue;}"
                    + "var cls=((n2.id||'')+' '+(typeof n2.className==='string'?n2.className:'')).trim();"
                    + "if(cls&&ADRE.test(cls)){var ar=n2.getBoundingClientRect();"
                    + "if(ar.height>=60&&ar.height<window.innerHeight*0.6){hide(n2);continue;}}"
                    + "if(n2.children.length)continue;"
                    + "var tx=(n2.textContent||'').replace(/\\s+/g,' ').trim();"
                    + "if(!tx||tx.length>40)continue;"
                    + "for(var q=0;q<KILL.length;q++){if(KILL[q].test(tx)){upHide(n2);break;}}}"
                    + "var sc=document.scrollingElement||document.documentElement;"
                    + "var target=sc,inner=false,best=sc.scrollHeight;"
                    + "var cands=document.querySelectorAll('div,section,main,article');"
                    + "for(var j=0;j<cands.length;j++){var n=cands[j];var c2;"
                    + "try{c2=getComputedStyle(n);}catch(x){continue;}if(!c2)continue;"
                    + "var oy=c2.overflowY;"
                    + "if((oy==='auto'||oy==='scroll')&&n.scrollHeight>n.clientHeight+80"
                    + "&&n.clientHeight>=window.innerHeight*0.5&&n.scrollHeight>best){"
                    + "best=n.scrollHeight;target=n;inner=true;}}"
                    + "window.__zybStop=1;"
                    + "window.__zybShot={hidden:hidden,target:target,inner:inner,"
                    + "info:function(){var r=target.getBoundingClientRect();"
                    + "return [Math.round(window.innerWidth),Math.round(target.scrollHeight),"
                    + "Math.round(target.clientHeight),Math.round(window.innerHeight),"
                    + "Math.round(r.left),Math.round(r.top),"
                    + "Math.round(r.width),Math.round(r.height),inner?1:0].join('|');},"
                    + "scrollTo:function(y){try{if(inner){target.scrollTop=y;}"
                    + "else{window.scrollTo(0,y);"
                    + "if(document.scrollingElement)document.scrollingElement.scrollTop=y;}}catch(e){}"
                    + "return Math.round(inner?target.scrollTop:(window.pageYOffset||0));}};"
                    + "return window.__zybShot.info();"
                    + "}catch(e){return '';}})()";

    private static final String RESTORE =
            "(function(){try{var s=window.__zybShot;if(!s){window.__zybStop=0;return;}"
                    + "for(var i=0;i<s.hidden.length;i++){"
                    + "try{s.hidden[i].style.removeProperty('display');}catch(e){}}"
                    + "try{if(s.inner){s.target.scrollTop=0;}else{window.scrollTo(0,0);}}catch(e){}"
                    + "window.__zybShot=null;window.__zybStop=0;}catch(e){}})()";
}
