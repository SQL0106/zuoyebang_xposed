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

    /** 整页撑开前的布局轮询上界（超过就落回固定延时继续，不无限等）。 */
    private static final long EXPAND_WAIT_MS = 2500L;
    /** 切软件层后等一帧栅格化的短延时（原 900ms 死等 → 布局到位后只需一帧）。 */
    private static final long LAYER_SETTLE_MS = 200L;
    /** CLEAN 之后到落笔的短延时（原 420ms）。 */
    private static final long CLEAN_SETTLE_MS = 200L;
    /** 一次截图的硬上界：超过就强制复位 {@code sBusy}，避免一次异常让长图永久失效。 */
    private static final long BUSY_TIMEOUT_MS = 60000L;

    private static volatile boolean sBusy = false;
    /** {@code sBusy} 被置真的时刻（uptimeMillis），用于陈旧锁自愈。 */
    private static volatile long sBusyAt = 0L;

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
            // 陈旧锁自愈：正常路径最慢几秒就复位，超过 BUSY_TIMEOUT_MS 还挂着说明回调丢了
            long held = android.os.SystemClock.uptimeMillis() - sBusyAt;
            if (held < BUSY_TIMEOUT_MS) {
                toast(context, "正在生成，请稍候");
                return;
            }
            HookEntry.log("PageShot busy guard: release stale lock after " + held + "ms");
            sBusy = false;
        }
        if (wv == null || wv.getWidth() <= 0 || wv.getHeight() <= 0) {
            toast(context, "页面还没准备好");
            return;
        }
        sBusy = true;
        sBusyAt = android.os.SystemClock.uptimeMillis();
        final Job job = new Job(context.getApplicationContext(), wv, part == null ? "full" : part);
        // 硬看门狗：WebView 已销毁 / 定时器被移除 / PixelCopy 不回调 时 done() 可能永远轮不到，
        // 没有它 sBusy 会永久为 true，长图从此只能重启 App 才能用。
        job.main.postDelayed(new Runnable() {
            @Override
            public void run() {
                job.watchdog();
            }
        }, BUSY_TIMEOUT_MS);
        job.start();
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
        /**
         * 题目/解答分界（CSS px）。JS 侧已取消「答案/解析…」文字探测（用户死线）→ 恒为 0，
         * 也就是默认存整页；字段保留只是为了 {@link #cropPart} 的既有契约不变。
         */
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

        /** 撑开前 WebView 的 LayoutParams.height；{@code Integer.MIN_VALUE} = 当前没有撑开。 */
        private int oldLpHeight = Integer.MIN_VALUE;
        /** done() 只能生效一次（正常出口 / 看门狗 / 后台线程回调可能同时到）。 */
        private volatile boolean finished = false;

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
                if (jsScroll) {
                    // 撑开整页路径不拼接，先用不着总画布：不分配（最多省 48MB）。
                    // 只有撑开失败回退拼接时，才由 ensureOut() 按这套 outW/outH 重建。
                    out = null;
                } else {
                    out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
                    out.eraseColor(0xFFFFFFFF);
                }

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
            if (out == null) {
                // 撑开路径提前释放了拼接画布；能走到这里说明 fallback 重建失败
                done(false, "内存不足，无法拼接长图");
                return;
            }
            if (mode == MODE_JS) {
                jsScrollStep();
            } else if (mode == MODE_NATIVE) {
                nativeScrollStep();
            } else {
                // 单屏：落笔与收尾都由 paste() 负责（PixelCopy 是异步的，
                // 这里再 finish() 会先存下一张还没贴图的空画布）
                grab(0, 0);
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
            // WebView 是硬件加速渲染的，View.draw(Canvas) 拍不到内容（实测全白）。
            // PixelCopy 没有 View 重载（只有 Surface/SurfaceView/Window），所以拍整个窗口再裁。
            if (win != null && android.os.Build.VERSION.SDK_INT >= 26) {
                final android.view.View decor = win.getDecorView();
                final int w = decor.getWidth();
                final int h = decor.getHeight();
                if (w > 0 && h > 0) {
                    final Bitmap shot;
                    try {
                        shot = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    } catch (Throwable t) {
                        done(false, "创建帧失败：" + t);
                        return;
                    }
                    try {
                        android.view.PixelCopy.request(win, new Rect(0, 0, w, h),
                                shot, new android.view.PixelCopy.OnPixelCopyFinishedListener() {
                                    @Override
                                    public void onPixelCopyFinished(int result) {
                                        if (result != android.view.PixelCopy.SUCCESS) {
                                            HookEntry.log("PageShot PixelCopy result=" + result);
                                            safeRecycle(shot);
                                            done(false, "抓屏失败（" + result + "）");
                                            return;
                                        }
                                        paste(shot, null, cssY, nativeY, true);
                                    }
                                }, main);
                        return;
                    } catch (Throwable t) {
                        HookEntry.log("PageShot PixelCopy 异常，回退 draw(): " + t);
                        safeRecycle(shot);
                    }
                }
            }
            // 兜底：软件层 View.draw。帧按需分配——PixelCopy 成功时一帧都不建，每步省 8~60MB。
            final Bitmap frame;
            try {
                frame = Bitmap.createBitmap(wv.getWidth(), wv.getHeight(), Bitmap.Config.ARGB_8888);
            } catch (Throwable t) {
                done(false, "创建帧失败：" + t);
                return;
            }
            frame.eraseColor(0xFFFFFFFF);
            try {
                wv.draw(new Canvas(frame));
            } catch (Throwable t) {
                safeRecycle(frame);
                done(false, "绘制失败：" + t);
                return;
            }
            paste(frame, frame, cssY, nativeY, false);
        }

        /** JS 模式下内容是否已经全部覆盖（比按屏数算更可靠）。 */
        private boolean fullyCovered() {
            return mode == MODE_JS && coveredPx >= cssScrollH * density - 4;
        }

        /** 把一帧按可见区裁剪并贴进总图。 */
        private void paste(Bitmap shot, Bitmap viewFrame, int cssY, int nativeY, boolean fromWindow) {
            try {
                Bitmap frame = fromWindow ? shot : viewFrame;
                if (frame == null) {
                    done(false, "抓屏失败：帧为空");
                    return;
                }
                // 每一帧都是「WebView 当前可见区域」的画面（JS 模式下页面内容已滚到 cssY），
                // 所以裁剪区固定用 vis；切勿用 getBoundingClientRect() 的 rectH——
                // 它是整页布局高度（若 rectY 为负，裁剪会一路向上跑，贴出来就是白的）。
                Rect src = new Rect(vis);
                if (fromWindow) src.offset(wvLoc[0], wvLoc[1]);
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

        /**
         * 用 {@link #splitY} 把整页图切成题目段 / 解答段。
         *
         * <p>对外契约不变：{@code splitY<=0} 存整页、{@code q} 取上半、{@code a} 从
         * {@code splitY-12px} 起取下半。注意 JS 侧已不再按文字探测分界（用户死线），
         * 所以默认 {@code splitY=0} → 存整页；分段位置只能由 Java 侧传入。</p>
         */
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

        /**
         * 统一落盘：PNG → 相册。expand 路径与拼接路径共用。
         *
         * <p>大图 {@code compress(PNG,100)} 要 0.5~3s、写相册再 0.3~1.5s，全在主线程会直接 ANR，
         * 所以编码与落盘都放到后台线程；{@link H5Bridge#saveBytes} 只用 ContentResolver/File IO
         * （内部自建的主线程 Handler 是从 {@code Looper.getMainLooper()} 取的），不依赖调用线程，
         * 可以安全地在后台线程调用。主线程只负责 toast 与状态。</p>
         */
        private void saveBitmap(Bitmap bmp) {
            if (bmp == null) {
                done(false, "没有可保存的图像");
                return;
            }
            // 先裁再编码：裁完立刻回收原图，避免「原图 + 副本」双份像素同时驻留。
            if (part != null && part.length() > 0 && !"full".equals(part)) {
                Bitmap cp = null;
                try {
                    cp = cropPart(bmp);
                } catch (Throwable t) {
                    HookEntry.log("PageShot crop failed: " + t);
                }
                if (cp != null && cp != bmp) {
                    if (bmp == out) out = null;
                    safeRecycle(bmp);
                    bmp = cp;
                }
            }
            final Bitmap src = bmp;
            final int srcW = src.getWidth();
            final int srcH = src.getHeight();
            final String logMode = modeOf();
            if (src == out) out = null;   // 所有权交给后台线程，done() 不再碰它
            new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] data = null;
                    String path = "";
                    try {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream(256 * 1024);
                        if (src.compress(Bitmap.CompressFormat.PNG, 100, bos)) {
                            data = bos.toByteArray();
                        }
                        bos = null;
                    } catch (Throwable t) {
                        HookEntry.log("PageShot compress failed: " + t);
                    } finally {
                        safeRecycle(src);
                    }
                    if (data != null) {
                        try {
                            String name = "zybclean_" + android.text.format.DateFormat.format(
                                    "yyyyMMdd_HHmmss", System.currentTimeMillis()) + ".png";
                            path = H5Bridge.saveBytes(ctx, name, "image/png", data);
                        } catch (Throwable t) {
                            HookEntry.log("PageShot store failed: " + t);
                        }
                    }
                    final byte[] outData = data;
                    final String outPath = path;
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (outData == null) {
                                done(false, "图片编码失败");
                                return;
                            }
                            try {
                                HookEntry.log("PageShot saved: mode=" + logMode + " part=" + part
                                        + " steps=" + step + " " + srcW + "x" + srcH
                                        + " size=" + outData.length + " B path=" + outPath);
                            } catch (Throwable ignored) {
                            }
                            done(true, outPath == null || outPath.length() == 0
                                    ? "保存失败" : "长图已保存到相册");
                        }
                    });
                }
            }, "zyb-shot").start();
        }

        /** 拼接画布（begin() 已算好 outW/outH/scale），JS 模式失败回退时才分配。 */
        private void ensureOut() {
            if (out != null) return;
            try {
                out = Bitmap.createBitmap(Math.max(1, outW), Math.max(1, outH),
                        Bitmap.Config.ARGB_8888);
                out.eraseColor(0xFFFFFFFF);
            } catch (Throwable t) {
                out = null;
                HookEntry.log("PageShot ensureOut failed: " + t);
            }
        }

        /* ---------------- 新路线：整页撑开一次画完 ---------------- */

        /**
         * 把 WebView 的高度临时改成整页高度、切软件层，等布局真正到位后一次 {@code draw()} 拿全图。
         * 不滚动 → 没有接缝、没有重复段落；失败则回退到旧的逐屏拼接。
         *
         * <p>撑开后的高度一定会在 {@link #restoreExpandHeight()} 里还原：正常落笔、CLEAN 回调丢失、
         * draw 抛异常、{@link #stitchFallback} 回退、{@link #done} 收尾、看门狗强杀，六条路都过这里。
         * 否则 WebView 会永久停在 14000px（页面变白板，且每帧分配 60MB）。</p>
         */
        private void expandShot() {
            try {
                android.view.ViewGroup.LayoutParams lp = wv.getLayoutParams();
                if (lp == null) {
                    stitchFallback("no layout params");
                    return;
                }
                final int keepH = lp.height;
                int targetH = Math.round(cssScrollH * density) + 4;
                if (targetH > MAX_EXPAND_H) targetH = MAX_EXPAND_H;
                if (targetH <= vis.height()) {
                    stitchFallback("target too short " + targetH);
                    return;
                }
                // 撑开路径不拼接：总画布（最多 48MB）先释放，真要回退时由 stitchFallback 重建
                if (out != null) {
                    safeRecycle(out);
                    out = null;
                }
                applyExpandedHeight(targetH);
                HookEntry.log("PageShot expand: " + wv.getWidth() + "x" + targetH
                        + " (oldH=" + keepH + ")");
                awaitExpandLayout(targetH, new Runnable() {
                    @Override
                    public void run() {
                        // 布局到位后切软件层（硬件层在超长尺寸下拿不到像素），再等一帧栅格化
                        try {
                            wv.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                        } catch (Throwable ignored) {
                        }
                        main.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                cleanThenDraw();
                            }
                        }, LAYER_SETTLE_MS);
                    }
                });
            } catch (Throwable t) {
                stitchFallback("expand error " + t);
            }
        }

        /** 记录撑开前的高度并撑开。只有它成功，{@link #restoreExpandHeight()} 才会动手。 */
        private void applyExpandedHeight(int targetH) {
            android.view.ViewGroup.LayoutParams lp = wv.getLayoutParams();
            if (lp == null) throw new IllegalStateException("no layout params");
            oldLpHeight = lp.height;
            lp.height = targetH;
            wv.setLayoutParams(lp);
            wv.requestLayout();
        }

        /** 无论走到哪条失败路径都要还原（幂等：还原过一次后再调是空操作）。 */
        private void restoreExpandHeight() {
            int h = oldLpHeight;
            if (h == Integer.MIN_VALUE) return;
            oldLpHeight = Integer.MIN_VALUE;
            restoreHeight(h);
        }

        /**
         * 轮询等 WebView 真的撑到目标高度（且连续 2 帧高度不变），最长 {@link #EXPAND_WAIT_MS}。
         *
         * <p>原来的 1200ms + 900ms + 420ms 三段死等合计 2520ms，既慢又不够稳：小页面白等 2s，
         * 大页面 900ms 又常常还没重新栅格化完（存出下半张白图）。改成「布局到位就继续」，
         * 超时则落回原来的量级继续走（不会无限等）。</p>
         */
        private void awaitExpandLayout(final int targetH, final Runnable then) {
            final long deadline = android.os.SystemClock.uptimeMillis() + EXPAND_WAIT_MS;
            final boolean[] fired = new boolean[]{false};
            final Runnable go = new Runnable() {
                @Override
                public void run() {
                    if (fired[0]) return;
                    fired[0] = true;
                    then.run();
                }
            };
            final int[] lastH = new int[]{-1};
            final int[] stable = new int[]{0};
            final Runnable poll = new Runnable() {
                @Override
                public void run() {
                    if (fired[0]) return;
                    int h = wv.getHeight();
                    if (h == lastH[0]) {
                        stable[0]++;
                    } else {
                        lastH[0] = h;
                        stable[0] = 0;
                    }
                    boolean reached = h >= targetH - 8 && wv.getWidth() > 0;
                    if (reached && stable[0] >= 2) {
                        go.run();
                        return;
                    }
                    if (android.os.SystemClock.uptimeMillis() >= deadline) {
                        HookEntry.log("PageShot expand wait timeout h=" + h + "/" + targetH);
                        go.run();
                        return;
                    }
                    wv.postOnAnimation(this);
                }
            };
            // 双保险：WebView 已 detach 时 postOnAnimation 只会挂在 RunQueue 里不回调
            main.postDelayed(go, EXPAND_WAIT_MS + 200L);
            wv.postOnAnimation(poll);
        }

        /** 撑开/重排后广告条常会「复活」，落笔前再清一次（CLEAN 不再探测文字分界）。 */
        private void cleanThenDraw() {
            try {
                wv.evaluateJavascript(CLEAN, new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        int hid = -1;
                        try {
                            String v = value == null ? "" : value.replace("\"", "").trim();
                            int bar = v.indexOf('|');
                            String tail = bar >= 0 ? v.substring(bar + 1).trim() : v;
                            if (tail.length() > 0) hid = Integer.parseInt(tail);
                        } catch (Throwable ignored) {
                        }
                        HookEntry.log("PageShot clean hidden=" + hid + " part=" + part);
                        main.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                drawExpanded();
                            }
                        }, CLEAN_SETTLE_MS);
                    }
                });
            } catch (Throwable t) {
                drawExpanded();
            }
        }

        private void drawExpanded() {
            Bitmap bmp = null;
            try {
                int h = Math.min(wv.getHeight(), MAX_EXPAND_H);
                if (h <= 0) {
                    stitchFallback("expanded height 0");
                    return;
                }
                // 保持 ARGB_8888：565 会把彩色公式/公式高亮的抗锯齿边缘压出色噪，
                // 存下来的解答图肉眼可见变差；内存靠「画完立刻还原高度 + 后台编码」控制。
                bmp = Bitmap.createBitmap(wv.getWidth(), h, Bitmap.Config.ARGB_8888);
                bmp.eraseColor(0xFFFFFFFF);
                wv.draw(new Canvas(bmp));
                int ink = inkPixels(bmp);
                int w = bmp.getWidth();
                // 像素已经拿到手，立刻还原高度：后续编码在后台线程，没必要占着 14000px
                restoreExpandHeight();
                HookEntry.log("PageShot expand ink=" + ink + " size=" + w + "x" + h);
                if (ink < 300) {
                    safeRecycle(bmp);
                    bmp = null;
                    stitchFallback("blank ink=" + ink);
                    return;
                }
                mode = MODE_EXPAND;
                Bitmap hand = bmp;
                bmp = null;   // 所有权交给 saveBitmap（后台线程编码完回收）
                saveBitmap(hand);
            } catch (Throwable t) {
                safeRecycle(bmp);
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
            // 先还原高度：不然回退拼接每帧要 draw 1080×14000（60MB/帧），页面也还是超长白板
            restoreExpandHeight();
            mode = MODE_JS;
            step = 0;
            coveredPx = 0;
            ensureOut();
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

        /** 看门狗入口：正常路径下 done() 已经把 finished 置真，这里就是空转。 */
        void watchdog() {
            if (finished) return;
            HookEntry.log("PageShot watchdog: capture exceeded " + BUSY_TIMEOUT_MS
                    + "ms, force release");
            done(false, "长图超时，已中止");
        }

        /** 唯一的收尾出口：幂等，且必定复位 {@code sBusy}（finally）。 */
        private void done(boolean ok, String msg) {
            synchronized (this) {
                if (finished) return;
                finished = true;
            }
            try {
                restoreExpandHeight();   // 兜底：任何路径都不许把 WebView 留在 14000px
                restoreNative();
                try {
                    wv.evaluateJavascript(RESTORE, null);
                } catch (Throwable ignored) {
                }
                safeRecycle(out);
                if (msg != null && msg.length() > 0) toast(ctx, msg);
                if (!ok) HookEntry.log("PageShot failed: " + msg);
            } catch (Throwable t) {
                HookEntry.log("PageShot done error: " + t);
            } finally {
                out = null;
                sBusy = false;
                sBusyAt = 0L;
            }
        }

        private static void safeRecycle(Bitmap b) {
            if (b == null) return;
            try {
                if (!b.isRecycled()) b.recycle();
            } catch (Throwable ignored) {
            }
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
     * {@code 宽|scrollH|clientH|innerH|rectX|rectY|rectW|rectH|innerScroller|split}。
     *
     * <p>第十段 {@code split} 现在恒为 {@code 0}（存整页）：原来的「答案/解析/解答…」文字探测
     * 属于用户明令删除的死线，隐藏浮层只按 position / tag / class / 素材 CDN 判定。</p>
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
                    /* 垃圾清理：视频讲解卡、会员推广卡一律不进图。
                       判定只用结构特征（tag / class / 素材 CDN），绝不看文案 —— 文案匹配是用户死线。 */
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
                    + "if(ar.height>=60&&ar.height<window.innerHeight*0.6){hide(n2);continue;}}}"
                    + "var sc=document.scrollingElement||document.documentElement;"
                    + "var target=sc,inner=false,best=sc.scrollHeight;"
                    + "var cands=document.querySelectorAll('div,section,main,article');"
                    + "for(var j=0;j<cands.length;j++){var n=cands[j];var c2;"
                    + "try{c2=getComputedStyle(n);}catch(x){continue;}if(!c2)continue;"
                    + "var oy=c2.overflowY;"
                    + "if((oy==='auto'||oy==='scroll')&&n.scrollHeight>n.clientHeight+80"
                    + "&&n.clientHeight>=window.innerHeight*0.5&&n.scrollHeight>best){"
                    + "best=n.scrollHeight;target=n;inner=true;}}"
                    /* 题目/解答分界不再按文字探测（「答案/解析/…」是用户死线）：恒为 0 = 存整页，
                       真的要分段时由 Java 侧传入 splitY。info() 仍保留 split 字段位以兼容 parse()。 */
                    + "var split=0;"
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
     * PREP 阶段隐藏掉的广告条会「复活」。这里再扫一遍（只按 class/素材 URL 判定，不看文案）。
     *
     * @return {@code "0|hiddenN"}（split 恒为 0：不再按文字探测题目/解答分界）
     */
    private static final String CLEAN =
            "(function(){try{"
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
                    + "if(ar.height>=40&&ar.height<vh*0.7){hide(n);continue;}}}}"
                    + "scan(document);"
                    + "var hosts=document.querySelectorAll('*');"
                    + "for(var s=0;s<hosts.length;s++){try{if(hosts[s].shadowRoot)scan(hosts[s].shadowRoot);}catch(e){}}"
                    /* 题目/解答分界不再按文字探测（「答案/解析/…」是用户死线）：恒为 0 = 存整页，
                       真要分段由 Java 侧传入 splitY。返回值保持 "split|hidden" 两段格式。 */
                    + "return \"0|\"+hid;"
                    + "}catch(e){return '';}})()";
}
