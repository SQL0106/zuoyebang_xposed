package io.github.sql0106.zybclean;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;

/**
 * 整页长截图：把当前 WebView 里渲染好的解答（题目 + 公式 + 排版）直接拼成一张图片。
 *
 * <p>文本提取永远会丢公式、混进隐藏 tab 与内联脚本，所以这里换思路——不抽文本，
 * 按「隐藏 fixed/sticky → 逐屏滚动 → webView.draw() → 竖向拼接 → PNG 落相册」拍照。</p>
 *
 * <p>要点：</p>
 * <ul>
 *   <li>滚动前先让 JS 把 {@code position:fixed/sticky} 元素（含自家工具条）临时隐藏，
 *       否则每一屏都会重复贴一次浮层；</li>
 *   <li>滚动目标分两种：窗口滚动（{@code window.scrollTo}）与页内滚动容器（{@code scrollTop}），
 *       由 JS 探测后统一暴露 {@code __zybShot.scrollTo(y)}；</li>
 *   <li>WebView 渲染是异步的，每屏滚动后必须等一会再 {@code draw()}；</li>
 *   <li>内存保护：总像素超过 1.2e7 时等比缩小，高度上限 20000px，最多 60 屏。</li>
 * </ul>
 */
public final class PageShot {

    private static final int MAX_STEPS = 60;
    private static final long SETTLE_MS = 420L;
    private static final double MAX_PIXELS = 1.2e7;
    private static final int MAX_OUT_HEIGHT = 20000;

    private static volatile boolean sBusy = false;

    private PageShot() {
    }

    /** 由 {@link H5Bridge#shot()} 在主线程调用。 */
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

    /** 一次截图任务。全部回调都在主线程。 */
    private static final class Job {

        private final Context ctx;
        private final WebView wv;
        private final Handler main = new Handler(Looper.getMainLooper());

        private int innerW;
        private int totalH;
        private int clientH;
        private int rectX;
        private int rectY;
        private int rectW;
        private int rectH;
        private float density;

        private float scale = 1f;
        private int outW;
        private int outH;
        private Bitmap out;
        private int step;
        private int steps;

        Job(Context ctx, WebView wv) {
            this.ctx = ctx;
            this.wv = wv;
        }

        void start() {
            try {
                wv.evaluateJavascript(PREP, new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        if (!parse(value)) {
                            done(false, "页面还没准备好");
                            return;
                        }
                        begin();
                    }
                });
            } catch (Throwable t) {
                done(false, "截图失败：" + t);
            }
        }

        /** 解析 JS 回报的 {@code w|total|client|rectX|rectY|rectW|rectH|inner}。 */
        private boolean parse(String raw) {
            if (raw == null) return false;
            String s = raw.replace("\"", "").replace("\\", "").trim();
            String[] p = s.split("\\|");
            if (p.length < 8) return false;
            try {
                innerW = Integer.parseInt(p[0].trim());
                totalH = Integer.parseInt(p[1].trim());
                clientH = Integer.parseInt(p[2].trim());
                rectX = Integer.parseInt(p[3].trim());
                rectY = Integer.parseInt(p[4].trim());
                rectW = Integer.parseInt(p[5].trim());
                rectH = Integer.parseInt(p[6].trim());
                return innerW > 0 && totalH > 0 && clientH > 0 && rectW > 0 && rectH > 0;
            } catch (Throwable t) {
                return false;
            }
        }

        private void begin() {
            try {
                density = wv.getWidth() / (float) innerW;
                if (density <= 0) {
                    done(false, "页面尺寸异常");
                    return;
                }
                steps = (int) Math.ceil(totalH / (double) clientH);
                if (steps < 1) steps = 1;
                if (steps > MAX_STEPS) steps = MAX_STEPS;

                double pixW = rectW * (double) density;
                double pixH = Math.min(totalH, (double) clientH * steps) * density;
                scale = 1f;
                if (pixW * pixH > MAX_PIXELS) {
                    scale = (float) Math.sqrt(MAX_PIXELS / (pixW * pixH));
                }
                outW = Math.max(1, Math.round(rectW * density * scale));
                outH = (int) Math.max(1L, Math.round(Math.min(totalH, (double) clientH * steps) * density * scale));
                if (outH > MAX_OUT_HEIGHT) {
                    scale *= MAX_OUT_HEIGHT / (float) outH;
                    outW = Math.max(1, Math.round(rectW * density * scale));
                    outH = MAX_OUT_HEIGHT;
                }
                out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
                out.eraseColor(0xFFFFFFFF);
                HookEntry.log("PageShot start: " + innerW + "x" + totalH + " client=" + clientH
                        + " rect=" + rectX + "," + rectY + " " + rectW + "x" + rectH
                        + " steps=" + steps + " out=" + outW + "x" + outH);
                toast(ctx, "正在生成整页长图（" + steps + " 屏）…");
                step = 0;
                scrollNext();
            } catch (Throwable t) {
                done(false, "创建画布失败：" + t);
            }
        }

        private void scrollNext() {
            if (step >= steps) {
                finish();
                return;
            }
            int y = step * clientH;
            if (y > totalH - clientH) y = Math.max(0, totalH - clientH);
            final int want = y;
            try {
                wv.evaluateJavascript(
                        "(function(){try{return window.__zybShot?window.__zybShot.scrollTo(" + want + "):-1;}"
                                + "catch(e){return -1;}})()",
                        new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String value) {
                                main.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        shoot(want);
                                    }
                                }, SETTLE_MS);
                            }
                        });
            } catch (Throwable t) {
                done(false, "滚动失败：" + t);
            }
        }

        /** 把当前可见内容画进总图。 */
        private void shoot(int wantY) {
            Bitmap frame = null;
            try {
                frame = Bitmap.createBitmap(wv.getWidth(), wv.getHeight(), Bitmap.Config.ARGB_8888);
                frame.eraseColor(0xFFFFFFFF);
                wv.draw(new Canvas(frame));

                int srcL = Math.round(rectX * density);
                int srcT = Math.round(rectY * density);
                int srcW = Math.round(rectW * density);
                int srcH = Math.round(rectH * density);
                srcL = clamp(srcL, 0, frame.getWidth());
                srcT = clamp(srcT, 0, frame.getHeight());
                srcW = clamp(srcW, 1, frame.getWidth() - srcL);
                srcH = clamp(srcH, 1, frame.getHeight() - srcT);

                int dstTop = Math.round(wantY * density * scale);
                int dstH = Math.round(srcH * scale);
                if (dstTop >= outH) {
                    done(true, null);
                    return;
                }
                int dstBottom = Math.min(outH, dstTop + dstH);
                Canvas c = new Canvas(out);
                c.drawBitmap(frame,
                        new Rect(srcL, srcT, srcL + srcW, srcT + srcH),
                        new Rect(0, dstTop, outW, dstBottom), new Paint(Paint.FILTER_BITMAP_FLAG));
                step++;
                boolean last = step >= steps || wantY + clientH >= totalH - 2;
                if (last) {
                    finish();
                } else {
                    scrollNext();
                }
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
                HookEntry.log("PageShot saved: " + path + " (" + data.length + " B)");
                done(true, path == null || path.length() == 0 ? "保存失败" : "长图已保存到相册");
            } catch (Throwable t) {
                done(false, "保存失败：" + t);
            }
        }

        private void done(boolean ok, String msg) {
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

        private static int clamp(int v, int lo, int hi) {
            return v < lo ? lo : (v > hi ? hi : v);
        }
    }

    /** 探测滚动容器 + 隐藏浮层，返回尺寸信息。幂等。 */
    private static final String PREP =
            "(function(){try{"
                    + "if(window.__zybShot)return window.__zybShot.info();"
                    + "var hidden=[];"
                    + "var all=document.querySelectorAll('body *');"
                    + "for(var i=0;i<all.length;i++){var e=all[i];"
                    + "if(e.id==='zyb-bar'){hidden.push(e);e.style.setProperty('display','none','important');continue;}"
                    + "var cs;try{cs=getComputedStyle(e);}catch(x){continue;}"
                    + "if(!cs)continue;"
                    + "if(cs.position==='fixed'||cs.position==='sticky'){"
                    + "var r=e.getBoundingClientRect();"
                    + "if(r.width>0&&r.height>0&&r.height<window.innerHeight*0.6){"
                    + "hidden.push(e);e.style.setProperty('display','none','important');}}}"
                    + "var sc=document.scrollingElement||document.documentElement;"
                    + "var target=sc,inner=false,best=sc.scrollHeight;"
                    + "var cands=document.querySelectorAll('div,section,main,article');"
                    + "for(var j=0;j<cands.length;j++){var n=cands[j];var c2;"
                    + "try{c2=getComputedStyle(n);}catch(x){continue;}if(!c2)continue;"
                    + "var oy=c2.overflowY;"
                    + "if((oy==='auto'||oy==='scroll')&&n.scrollHeight>n.clientHeight+80"
                    + "&&n.clientHeight>=window.innerHeight*0.5&&n.scrollHeight>best){"
                    + "best=n.scrollHeight;target=n;inner=true;}}"
                    + "window.__zybShot={hidden:hidden,target:target,inner:inner,"
                    + "info:function(){var r=target.getBoundingClientRect();"
                    + "return [Math.round(window.innerWidth),Math.round(target.scrollHeight),"
                    + "Math.round(target.clientHeight),Math.round(r.left),Math.round(r.top),"
                    + "Math.round(r.width),Math.round(r.height),inner?1:0].join('|');},"
                    + "scrollTo:function(y){try{if(inner){target.scrollTop=y;}"
                    + "else{window.scrollTo(0,y);"
                    + "if(document.scrollingElement)document.scrollingElement.scrollTop=y;}}catch(e){}"
                    + "return Math.round(inner?target.scrollTop:(window.pageYOffset||0));}};"
                    + "return window.__zybShot.info();"
                    + "}catch(e){return '';}})()";

    /** 还原浮层与滚动位置。 */
    private static final String RESTORE =
            "(function(){try{var s=window.__zybShot;if(!s)return;"
                    + "for(var i=0;i<s.hidden.length;i++){"
                    + "try{s.hidden[i].style.removeProperty('display');}catch(e){}}"
                    + "try{if(s.inner){s.target.scrollTop=0;}else{window.scrollTo(0,0);}}catch(e){}"
                    + "window.__zybShot=null;}catch(e){}})()";
}
