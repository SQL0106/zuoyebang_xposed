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
    private static final long SETTLE_MS = 700L;
    private static final double MAX_PIXELS = 1.2e7;
    private static final int MAX_OUT_HEIGHT = 20000;

    private static final int MODE_JS = 1;
    private static final int MODE_NATIVE = 2;
    private static final int MODE_SINGLE = 3;
    private static final int MODE_EXPAND = 4;
    private static final int MAX_EXPAND_H = 14000;

    private static volatile boolean sBusy = false;

    private PageShot() {
    }

    public static void capture(final Context context, final WebView wv) {
        capture(context, wv, "full");
    }

    /**
     * @param part "full"=整页、"q"=只存题目、"a"=只存解答+解析
     */
    public static void capture(final Context context, final WebView wv, String part) {
        if (sBusy) {
            toast(context, "正在生成，请稍候");
            return;
        }
        if (wv == null || wv.getWidth() <= 0 || wv.getHeight() <= 0) {
            toast(context, "页面还没准备好");
            return;
        }
        sBusy = true;
        new Job(context.getApplicationContext(), wv, part == null ? "full" : part).start();
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
        private final String part;
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
        /** 「答案/解析」标题的文档纵坐标（CSS px），JS 探测；<=0 表示没找到。 */
        private int splitY;
        private boolean innerScroller;

        /* 可见区域（WebView 本地坐标，设备 px） */
        private final Rect vis = new Rect();

        private float density = 1f;
        private float scale = 1f;
        private int outW;
        private int outH;
        private Bitmap out;
        private android.view.Window win;
        private final int[] wvLoc = new int[2];

        private int mode = MODE_SINGLE;
        private int step;
        private int steps;
        private int jsTries;
        private int coveredPx;
        private int accOffset;
        private View scroller;
        private int scrollerStartY;

        Job(Context ctx, WebView wv, String part) {
            this.ctx = ctx;
            this.wv = wv;
            this.part = part;
        }

        void start() {
            try {
                localVisible();
                win = findWindow(wv.getContext());
                if (win != null) wv.getLocationInWindow(wvLoc);
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
            if (p.length < 9) return false;
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
                splitY = p.length > 9 ? Integer.parseInt(p[9].trim()) : 0;
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
                    steps = (int) Math.ceil(cssScrollH / (double) cssClientH);
                    outH = (int) Math.round(Math.min(cssScrollH, (double) cssClientH * steps) * density);
                } else if (wv.getHeight() > vis.height() + 8) {
                    mode = MODE_NATIVE;
                    steps = (int) Math.ceil(wv.getHeight() / (double) vis.height());
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
                toast(ctx, "正在生成整页长图…");
                if (mode == MODE_JS) {
                    // 新路线：整页撑开一次画完，不滚动、不拼接（滚动拼接会重复/接缝）
                    expandShot();
                    return;
                }
                main.postDelayed(this, SETTLE_MS);
            } catch (Throwable t) {
                done(false, "创建画布失败：" + t);
            }
        }

        private String modeOf() {
            return mode == MODE_JS ? "js" : (mode == MODE_NATIVE ? "native"
                    : (mode == MODE_EXPAND ? "expand" : "single"));
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
                                // 页面有自己的滚动动画/虚拟列表重建，scrollTo 后位置未必立刻到位。
                                // 没到位就再等一轮重试，否则会拍到上一屏内容 → 图里出现重复段落。
                                if (Math.abs(actual - wantY) > 2 && jsTries < 6) {
                                    jsTries++;
                                    HookEntry.log("PageShot js retry " + step + " want=" + wantY
                                            + " actual=" + actual);
                                    main.postDelayed(PageShot.Job.this, 260);
                                    return;
                                }
                                jsTries = 0;
                                HookEntry.log("PageShot js step " + step + " want=" + wantY
                                        + " actual=" + actual);
                                // 用「实际」位置贴图：即使没滚到 want，也不会把内容贴错位置。
                                grab(actual, 0);
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
            return vis.height();
        }

        /**
         * 画一帧并贴到总图。
         *
         * @param cssY   JS 模式下这一屏的内容起点（CSS px），0 表示用当前定位
         * @param nativeY 原生模式下累计滚动位移（设备 px）
         */
        private void grab(final int cssY, final int nativeY) {
            final Bitmap frame;
            try {
                frame = Bitmap.createBitmap(wv.getWidth(), wv.getHeight(), Bitmap.Config.ARGB_8888);
            } catch (Throwable t) {
                done(false, "创建帧失败：" + t);
                return;
            }
            // WebView 是硬件加速渲染的，View.draw(Canvas) 拍不到内容（实测全白）。
            // PixelCopy 没有 View 重载（只有 Surface/SurfaceView/Window），所以拍整个窗口再裁。
            if (win != null && android.os.Build.VERSION.SDK_INT >= 26) {
                final android.view.View decor = win.getDecorView();
                final int w = decor.getWidth();
                final int h = decor.getHeight();
                if (w > 0 && h > 0) {
                    final Bitmap shot = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    try {
                        android.view.PixelCopy.request(win, new Rect(0, 0, w, h),
                                shot, new android.view.PixelCopy.OnPixelCopyFinishedListener() {
                                    @Override
                                    public void onPixelCopyFinished(int result) {
                                        if (result != android.view.PixelCopy.SUCCESS) {
                                            HookEntry.log("PageShot PixelCopy result=" + result);
                                            shot.recycle();
                                            frame.recycle();
                                            done(false, "抓屏失败（" + result + "）");
                                            return;
                                        }
                                        paste(shot, frame, cssY, nativeY, true);
                                    }
                                }, main);
                        return;
                    } catch (Throwable t) {
                        HookEntry.log("PageShot PixelCopy 异常，回退 draw(): " + t);
                        shot.recycle();
                    }
                }
            }
            frame.eraseColor(0xFFFFFFFF);
            try {
                wv.draw(new Canvas(frame));
            } catch (Throwable t) {
                done(false, "绘制失败：" + t);
                return;
            }
            paste(frame, cssY, nativeY);
        }

        /** JS 模式下内容是否已经全部覆盖（比按屏数算更可靠）。 */
        private boolean fullyCovered() {
            return mode == MODE_JS && coveredPx >= cssScrollH * density - 4;
        }

        /** 把一帧按可见区裁剪并贴进总图。 */
        private void paste(Bitmap frame, int cssY, int nativeY) {
            paste(frame, null, cssY, nativeY, false);
        }

        private void paste(Bitmap shot, Bitmap viewFrame, int cssY, int nativeY, boolean fromWindow) {
            try {
                // 每一帧都是「WebView 当前可见区域」的画面（JS 模式下页面内容已滚到 cssY），
                // 所以裁剪区固定用 vis；切勿用 getBoundingClientRect() 的 rectH——
                // 它是整页布局高度（若 rectY 为负，裁剪会一路向上跑，贴出来就是白的）。
                Rect src = new Rect(vis);
                if (fromWindow && shot != null) src.offset(wvLoc[0], wvLoc[1]);
                Bitmap frame = fromWindow ? shot : viewFrame;
                if (!src.intersect(0, 0, frame.getWidth(), frame.getHeight())) {
                    done(false, "可见区域异常");
                    return;
                }

                int frameTop = (mode == MODE_JS) ? Math.round(cssY * density) : nativeY;
                int srcH0 = src.height();
                // 帧与帧之间必然有重叠（滚动量不足一屏、或页面有粘性行）：
                // 把已经贴过的顶部裁掉，既消掉重复段落，也消掉接缝处的白线。
                if (frameTop < coveredPx) {
                    int cut = Math.min(srcH0 - 1, coveredPx - frameTop);
                    if (cut > 0) {
                        src.top += cut;
                        srcH0 -= cut;
                        frameTop += cut;
                    }
                }
                if (srcH0 <= 2) {
                    HookEntry.log("PageShot skip duplicate frame at " + frameTop);
                    step++;
                    if (step >= steps || fullyCovered()) finish();
                    else main.postDelayed(this, 120);
                    return;
                }
                int dstTop = Math.round(frameTop * scale);
                if (dstTop >= outH) {
                    finish();
                    return;
                }
                int dstH = Math.round(srcH0 * scale);
                int dstBottom = Math.min(outH, dstTop + dstH);
                Canvas c = new Canvas(out);
                c.drawBitmap(frame, src, new Rect(0, dstTop, outW, dstBottom),
                        new Paint(Paint.FILTER_BITMAP_FLAG));
                coveredPx = Math.max(coveredPx, frameTop + srcH0);
                step++;
                if (step >= steps || fullyCovered()) finish();
                else main.postDelayed(this, SETTLE_MS);
            } catch (Throwable t) {
                done(false, "绘制失败：" + t);
            } finally {
                if (shot != null && shot != viewFrame) shot.recycle();
                if (viewFrame != null) viewFrame.recycle();
            }
        }

        /** 从 WebView 的 Context 往上找 Activity/Window（PixelCopy 只接受 Window）。 */
        private static android.view.Window findWindow(android.content.Context c) {
            android.content.Context cur = c;
            while (cur != null) {
                if (cur instanceof android.app.Activity) return ((android.app.Activity) cur).getWindow();
                if (cur instanceof android.content.ContextWrapper) {
                    android.content.Context next = ((android.content.ContextWrapper) cur).getBaseContext();
                    if (next == cur) break;
                    cur = next;
                } else {
                    break;
                }
            }
            // 兜底：用 WebView 的 rootView 反查（PopupWindow 等场景拿不到 Activity）
            return null;
        }

        private void finish() {
            saveBitmap(out);
        }

        /** 用 JS 探到的「答案」标题位置把整页图切成题目段 / 解答段。 */
        private Bitmap cropPart(Bitmap whole) {
            int h = whole.getHeight();
            int splitPx = Math.round(splitY * density);
            if (splitY <= 0 || splitPx < 40 || splitPx > h - 40) {
                toast(ctx, "没找到题目/解答分界，已存整页");
                return whole;
            }
            boolean q = "q".equals(part);
            int y0 = q ? 0 : Math.max(0, splitPx - 12);
            int y1 = q ? Math.min(h, splitPx) : h;
            if (y1 - y0 < 40) {
                toast(ctx, "分段太短，已存整页");
                return whole;
            }
            HookEntry.log("PageShot crop part=" + part + " " + whole.getWidth() + "x" + (y1 - y0)
                    + " from y=" + y0 + " (split=" + splitPx + ")");
            return Bitmap.createBitmap(whole, 0, y0, whole.getWidth(), y1 - y0);
        }

        /** 统一落盘：PNG → 相册。expand 路径与拼接路径共用。 */
        private void saveBitmap(Bitmap bmp) {
            try {
                if (bmp == null) {
                    done(false, "没有可保存的图像");
                    return;
                }
                if (part != null && part.length() > 0 && !"full".equals(part)) {
                    Bitmap cp = cropPart(bmp);
                    if (cp != bmp) {
                        if (bmp == out) out = null;
                        bmp.recycle();
                        bmp = cp;
                    }
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream(256 * 1024);
                bmp.compress(Bitmap.CompressFormat.PNG, 100, bos);
                byte[] data = bos.toByteArray();
                String name = "zybclean_" + android.text.format.DateFormat.format(
                        "yyyyMMdd_HHmmss", System.currentTimeMillis()) + ".png";
                String path = H5Bridge.saveBytes(ctx, name, "image/png", data);
                HookEntry.log("PageShot saved: mode=" + modeOf() + " part=" + part + " steps=" + step
                        + " " + bmp.getWidth() + "x" + bmp.getHeight()
                        + " size=" + data.length + " B path=" + path);
                if (bmp != out) bmp.recycle();
                done(true, path == null || path.length() == 0 ? "保存失败" : "长图已保存到相册");
            } catch (Throwable t) {
                done(false, "保存失败：" + t);
            }
        }

        /* ---------------- 新路线：整页撑开一次画完 ---------------- */

        /**
         * 把 WebView 的高度临时改成整页高度、切软件层，等布局稳定后一次 {@code draw()} 拿全图。
         * 不滚动 → 没有接缝、没有重复段落；失败则回退到旧的逐屏拼接。
         */
        private void expandShot() {
            int oldH;
            try {
                android.view.ViewGroup.LayoutParams lp = wv.getLayoutParams();
                if (lp == null) {
                    stitchFallback("no layout params");
                    return;
                }
                oldH = lp.height;
                final int keepH = oldH;
                int targetH = Math.round(cssScrollH * density) + 4;
                if (targetH > MAX_EXPAND_H) targetH = MAX_EXPAND_H;
                if (targetH <= vis.height()) {
                    stitchFallback("target too short " + targetH);
                    return;
                }
                if (out != null) {
                    out.recycle();
                    out = null;
                }
                lp.height = targetH;
                wv.setLayoutParams(lp);
                wv.requestLayout();
                HookEntry.log("PageShot expand: " + wv.getWidth() + "x" + targetH
                        + " (oldH=" + keepH + ")");
                main.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            wv.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                        } catch (Throwable ignored) {
                        }
                        main.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // 撑开/重排后广告条常会「复活」，落笔前再清一次，并刷新题目/解答分界
                                try {
                                    wv.evaluateJavascript(CLEAN, new ValueCallback<String>() {
                                        @Override
                                        public void onReceiveValue(String value) {
                                            int s = -1;
                                            int hid = -1;
                                            try {
                                                String v = value == null ? ""
                                                        : value.replace("\"", "").trim();
                                                int bar = v.indexOf('|');
                                                if (bar >= 0) {
                                                    s = Integer.parseInt(v.substring(0, bar).trim());
                                                    hid = Integer.parseInt(v.substring(bar + 1).trim());
                                                } else if (v.length() > 0) {
                                                    s = Integer.parseInt(v);
                                                }
                                            } catch (Throwable ignored) {
                                            }
                                            if (s > 60) splitY = s;
                                            HookEntry.log("PageShot clean split=" + s
                                                    + " hidden=" + hid + " part=" + part);
                                            main.postDelayed(new Runnable() {
                                                @Override
                                                public void run() {
                                                    drawExpanded(keepH);
                                                }
                                            }, 420L);
                                        }
                                    });
                                } catch (Throwable t) {
                                    drawExpanded(keepH);
                                }
                            }
                        }, 900L);
                    }
                }, 1200L);
            } catch (Throwable t) {
                stitchFallback("expand error " + t);
            }
        }

        private void drawExpanded(int oldH) {
            Bitmap bmp = null;
            try {
                int h = Math.min(wv.getHeight(), MAX_EXPAND_H);
                if (h <= 0) {
                    restoreHeight(oldH);
                    stitchFallback("expanded height 0");
                    return;
                }
                bmp = Bitmap.createBitmap(wv.getWidth(), h, Bitmap.Config.ARGB_8888);
                bmp.eraseColor(0xFFFFFFFF);
                wv.draw(new Canvas(bmp));
                int ink = inkPixels(bmp);
                restoreHeight(oldH);
                HookEntry.log("PageShot expand ink=" + ink + " size=" + bmp.getWidth() + "x" + h);
                if (ink < 300) {
                    bmp.recycle();
                    stitchFallback("blank ink=" + ink);
                    return;
                }
                mode = MODE_EXPAND;
                saveBitmap(bmp);
            } catch (Throwable t) {
                if (bmp != null) bmp.recycle();
                restoreHeight(oldH);
                stitchFallback("draw error " + t);
            }
        }

        private void restoreHeight(int oldH) {
            try {
                wv.setLayerType(View.LAYER_TYPE_NONE, null);
            } catch (Throwable ignored) {
            }
            try {
                android.view.ViewGroup.LayoutParams lp = wv.getLayoutParams();
                if (lp != null) {
                    lp.height = oldH;
                    wv.setLayoutParams(lp);
                    wv.requestLayout();
                }
            } catch (Throwable ignored) {
            }
        }

        private void stitchFallback(String why) {
            HookEntry.log("PageShot expand failed -> stitch: " + why);
            mode = MODE_JS;
            step = 0;
            coveredPx = 0;
            try {
                wv.evaluateJavascript(
                        "(function(){try{window.scrollTo(0,0);}catch(e){}})()", null);
            } catch (Throwable ignored) {
            }
            main.postDelayed(this, 400L);
        }

        /** 抽样统计非白像素，用来判定“撑开后是不是白图”。 */
        private static int inkPixels(Bitmap b) {
            try {
                int w = b.getWidth();
                int h = b.getHeight();
                int sx = Math.max(1, w / 120);
                int sy = Math.max(1, h / 300);
                int ink = 0;
                for (int y = 0; y < h; y += sy) {
                    for (int x = 0; x < w; x += sx) {
                        if ((b.getPixel(x, y) & 0x00FFFFFF) != 0x00FFFFFF) ink++;
                    }
                }
                return ink;
            } catch (Throwable t) {
                return 0;
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
                    + "window.__zybCapture=1;"
                    + "try{var st=document.createElement('style');"
                    + "st.textContent='html,body,*{scroll-behavior:auto!important}';"
                    + "(document.head||document.documentElement).appendChild(st);}catch(e){}"
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
                    /* 作业帮自己的视频/会员推广模块：素材全在 zyb-vip.cdnjtzy.com，纯背景图无文字 */
                    + "var HRE=/(video__promotion|vip-video-explain|single-video__wrapper|"
                    + "video__payBox|video-poster__wrapper|poster-player__wrapper|relate-video|"
                    + "paisou-svip-video|new-poster|biz-poster)/;"
                    + "var VCDN=/zyb-vip\\.cdnjtzy\\.com/i;"
                    + "var nodes=document.querySelectorAll('body *');"
                    + "for(var k=0;k<nodes.length;k++){var n2=nodes[k];"
                    + "var tag=n2.tagName;"
                    + "if(tag==='VIDEO'||tag==='IFRAME'){upHide(n2);continue;}"
                    + "var cls=((n2.id||'')+' '+(typeof n2.className==='string'?n2.className:'')).trim();"
                    + "if(cls&&HRE.test(cls)){hide(n2);continue;}"
                    + "if(tag==='IMG'&&VCDN.test(n2.currentSrc||n2.src||'')){upHide(n2);continue;}"
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
                    + "var split=0;"
                    + "try{var heads=document.querySelectorAll('div,span,p,h1,h2,h3,h4,strong,b,em');"
                    + "for(var hh=0;hh<heads.length;hh++){var he=heads[hh];"
                    + "var ht=(he.textContent||'').replace(/\\s/g,'');"
                    + "if(!ht||ht.length>6)continue;"
                    + "if(!/^(答案|解析|解答|参考答案|答案解析|题目解析|解答过程|详解|分析)$/.test(ht))continue;"
                    + "var hr=he.getBoundingClientRect();"
                    + "if(hr.height<=0||hr.height>90)continue;"
                    + "var hy=Math.round(hr.top+(window.pageYOffset||0));"
                    + "if(hy>60&&(split===0||hy<split))split=hy;}}catch(e){}"
                    + "window.__zybStop=1;"
                    + "window.__zybShot={hidden:hidden,target:target,inner:inner,"
                    + "info:function(){var r=target.getBoundingClientRect();"
                    + "return [Math.round(window.innerWidth),Math.round(target.scrollHeight),"
                    + "Math.round(target.clientHeight),Math.round(window.innerHeight),"
                    + "Math.round(r.left),Math.round(r.top),"
                    + "Math.round(r.width),Math.round(r.height),inner?1:0,split].join('|');},"
                    + "scrollTo:function(y){try{if(inner){target.scrollTop=y;}"
                    + "else{try{window.scrollTo({top:y,left:0,behavior:'instant'});}"
                    + "catch(e2){window.scrollTo(0,y);}"
                    + "if(document.scrollingElement)document.scrollingElement.scrollTop=y;}}catch(e){}"
                    + "return Math.round(inner?target.scrollTop:(window.pageYOffset||0));}};"
                    + "return window.__zybShot.info();"
                    + "}catch(e){return '';}})()";

    private static final String RESTORE =
            "(function(){try{var s=window.__zybShot;window.__zybCapture=0;if(!s){window.__zybStop=0;return;}"
                    + "for(var i=0;i<s.hidden.length;i++){"
                    + "try{s.hidden[i].style.removeProperty('display');}catch(e){}}"
                    + "try{if(s.inner){s.target.scrollTop=0;}else{window.scrollTo(0,0);}}catch(e){}"
                    + "window.__zybShot=null;window.__zybStop=0;}catch(e){}})()";

    /**
     * 截图落笔前的第二次清理：页面在撑开/重排后会重新渲染 React 节点，
     * PREP 阶段隐藏掉的广告条会「复活」。这里再扫一遍并重新计算题目/解答分界。
     *
     * @return 分界 y（CSS px，0 表示没找到）
     */
    private static final String CLEAN =
            "(function(){try{"
                    + "var KILL=[/本题精讲/,/讲解(中|视频)/,/视频讲解/,/双十一/,/新客专享/,"
                    + "/元\\s*\\/\\s*月/,/立即抢/,/即将结束/,/开通会员/,/立即开通/,"
                    + "/限时/,/秒杀/,/领券/,/领取/,/^广告$/,/特惠/,/优惠/,"
                    + "/立即下载/,/下载\\s*App/i,/扫码/];"
                    + "var ADRE=/(^|[\\s_-])(ad|ads|advert|advertisement|banner|promo|promotion|gg|coupon|sales)([\\s_-]|$)/i;"
                    + "var HRE=/(video__promotion|vip-video-explain|single-video__wrapper|"
                    + "video__payBox|video-poster__wrapper|poster-player__wrapper|relate-video|"
                    + "paisou-svip-video|new-poster|biz-poster)/;"
                    + "var VCDN=/zyb-vip\\.cdnjtzy\\.com/i;"
                    + "var vh=window.innerHeight,vw=window.innerWidth,hid=0;"
                    + "function hide(el){try{el.style.setProperty('display','none','important');hid++;}catch(e){}}"
                    + "function upHide(e){var t=e;"
                    + "for(var i=0;i<6&&t&&t.parentElement&&t!==document.body;i++){"
                    + "var r=t.getBoundingClientRect();"
                    + "if(r.height>=40&&r.height<vh*0.6&&r.width>=vw*0.5){hide(t);return;}"
                    + "t=t.parentElement;}hide(e);}"
                    + "function scan(root){var nodes=root.querySelectorAll('*');"
                    + "for(var k=0;k<nodes.length;k++){var n=nodes[k];"
                    + "if(n.id==='zyb-bar'){hide(n);continue;}"
                    + "var cls=((n.id||'')+' '+(typeof n.className==='string'?n.className:'')).trim();"
                    + "if(cls&&HRE.test(cls)){hide(n);continue;}"
                    + "if(n.tagName==='IMG'&&VCDN.test(n.currentSrc||n.src||'')){upHide(n);continue;}"
                    + "if(cls&&ADRE.test(cls)){var ar=n.getBoundingClientRect();"
                    + "if(ar.height>=40&&ar.height<vh*0.7){hide(n);continue;}}"
                    + "if(n.childElementCount>4)continue;"
                    + "var tx=(n.textContent||'').replace(/\\s+/g,' ').trim();"
                    + "if(!tx||tx.length>40)continue;"
                    + "for(var q=0;q<KILL.length;q++){if(KILL[q].test(tx)){upHide(n);break;}}}}"
                    + "scan(document);"
                    + "var hosts=document.querySelectorAll('*');"
                    + "for(var s=0;s<hosts.length;s++){try{if(hosts[s].shadowRoot)scan(hosts[s].shadowRoot);}catch(e){}}"
                    + "var split=0;"
                    + "var heads=document.querySelectorAll('div,span,p,h1,h2,h3,h4,strong,b,em');"
                    + "for(var i2=0;i2<heads.length;i2++){var h2=heads[i2];"
                    + "var t2=(h2.textContent||'').replace(/[\\s\\u00a0]/g,'');"
                    + "if(!t2||t2.length>6)continue;"
                    + "if(!/^(答案|解析|解答|参考答案|答案解析|题目解析|解答过程|详解|分析)$/.test(t2))continue;"
                    + "var r2;try{r2=h2.getBoundingClientRect();}catch(e){continue;}"
                    + "if(r2.height<=0||r2.height>90)continue;"
                    + "var top=r2.top+(window.pageYOffset||0);"
                    + "if(top>60&&(split===0||top<split))split=top;}"
                    + "return String(Math.round(split))+\"|\"+hid;"
                    + "}catch(e){return '';}})()";
}
