# zybclean 1.3.0 代码审查（正确性缺陷 + 性能热点）

> 审查方式：只读源码 + 成本推算（未编译、未连设备）。时间估算按 Snapdragon 865 / ART（`getDeclaredMethods()` 每次调用都会复制一个 `Method[]`）；量级可信，绝对值有 ±2 倍浮动。

## 审查范围
- 目标：LSPosed 模块 `zybclean`，适配作业帮 `com.zuoyebang.knowledge` 14.34.0（1738）
- 仓库：`/home/SQL916/zuoyebang_xposed`，分支 main
- HEAD：
  ```
  007a866 1.3.0: 导航选项卡彻底不碰（命中点在 li/ul/nav 内直接跳过），repairNav 还原 display/visibility/opacity，并打印真实选项卡内容
  821d902 1.3.0: 长按蓝色底栏按钮改按真实控件定位（AISearchBottomInputView + id 0x7f0911f7，几何兜底）
  2fcbbe8 修编译：清理重复的 bindOne/revealBars，walkBind 返回绑定数
  ```
- 受审文件：`WebKiller.java`(595)、`AnswerPageKiller.java`(335)、`FeedKiller.java`(277)、`HomeChromeKiller.java`(271)、`AdBlocker.java`(84)、`FloatKiller.java`(80)、`PageShot.java`(876)、`HookEntry.java`(100)
- 顺带核对（影响结论但不单列）：`compat/XposedHelpers.java`、`compat/XposedBridge.java`、`compat/XC_MethodHook.java`、`H5Bridge.java`、`FastStart.java`

## TODO 清单
- [x] 1. WebKiller：buildJs 注入 JS 热路径 / scanKill / repairNav / loop / mkBar 重建 / inject 4×postDelayed / bindNativeLongPress / scheduleBind / ensureBridge / remember 泄漏面
- [x] 2. AnswerPageKiller：install() hook 清单、schedule() 多次 postDelayed 全树 clean()、walkBanner
- [x] 3. FeedKiller / HomeChromeKiller / AdBlocker / FloatKiller：每帧 / 每 item bind 的昂贵操作
- [x] 4. PageShot：expandShot 时序、inkPixels 全图扫描、cropPart 边界、drawExpanded 大 Bitmap
- [x] 5. HookEntry：hook 装配顺序、重复 install
- [x] 6. 汇总：按 P0/P1/P2 排序 + 速度优化优先级 Top 5

## 统计
**P0 = 5，P1 = 11，P2 = 15，合计 31 条。**

---

# P0（必须改：死线违反 / 明显卡顿 / ANR / OOM）

### [P0] 仍在按文字/字符串匹配隐藏控件（用户死线，共 4 处）
- 文件:行号
  - `AnswerPageKiller.java:27-30`（`KILL_TEXTS`）+ `:118-124`（`isKillText`）+ `:99-116`（`clean`）
  - `HomeChromeKiller.java:35-37`（`JUNK_TEXTS`）+ `:179-184`（`isJunk`）+ `:161-177`
  - `WebKiller.java:72`（`var AI=/^AI(解题|讲题|答疑)$/i`）+ `:78-89`（`scanKill` 内 `AI.test(z)||z==='问小帮'`）
  - `PageShot.java:757-758 / 783`（`PREP` 的 `KILL` 正则）、`:833-836 / 860`（`CLEAN` 的 `KILL` 正则）
- 现象/风险
  - 四处都是「取 textContent → 正则/字符串比较 → 隐藏祖先容器」。这是用户明确划掉的死线：文案一改、多语言、A/B 实验、同名不同义的节点都会漏杀或误杀。
  - `AnswerPageKiller` 类注释（`:18`「所有资源 id 都是 zyb，所以只能按文字定位」）已是过时结论——1.3.0 的 `WebKiller` 已用 `AISearchBottomInputView` + `0x7f0911f7` 按真实控件定位成功，证明结构特征可用。
- 建议改法（**不新增任何文字匹配**，只做替换/删除）
  - `AnswerPageKiller`：不再遍历 TextView 找文字，改为按真实底栏结构定位最后一项。替换 `clean()` 里 `for (Object tv : texts) {...}` 整段（`:103-112`）：
    ```java
    android.view.View bar = findBottomBar(root);   // 按 AISearchBottomInputView 类名找，见下
    if (bar instanceof android.view.ViewGroup) {
        android.view.ViewGroup g = (android.view.ViewGroup) bar;
        for (int i = g.getChildCount() - 1; i >= 0; i--) {   // 最右一项 = 问小帮位
            android.view.View c = g.getChildAt(i);
            if (c != null && c.getVisibility() == android.view.View.VISIBLE) {
                c.setVisibility(android.view.View.GONE);     // 不含任何文字判断
                break;
            }
        }
    }
    ```
    `findBottomBar` 复用 `WebKiller.findByClass` 的同款 DFS（按 `com.baidu.homework.activity.search.newsearchv3.ai.AISearchBottomInputView`），失败时直接 return，不降级成文字匹配。
  - `HomeChromeKiller`：删掉 `hideJunkCards(root);`（`:97`）这个方法调用与 `JUNK_TEXTS`（`:35-37`），改由 `FeedKiller` 的数据层过滤覆盖（它已在过滤 `TYPE_VIP_RECOMMEND`）；若仍需隐藏卡片，用 `:187-207` 已有的几何骨架（`ConstraintLayout` + 高度阈值），去掉文字入口。
  - `WebKiller` JS：把 `AI.test(z)||z==='问小帮'` 换成 class 判定，例如 `el.matches('[class*="ai-tab"],[class*="aiTab"],[data-role="ai"]')`，保留 `closest('#pageNav,...')` 保护；`:90` 的 `t.indexOf('讲解')` + fixed/sticky 判定**可以保留**——它判断的是定位方式而非文案命中。
  - `PageShot`：`HRE`（class 名）、`VCDN`（`zyb-vip.cdnjtzy.com`）、`VIDEO/IFRAME` 三条结构性规则已够覆盖视频/会员推广卡；删掉 `KILL` 数组与 `:783`、`:860` 的 `for(q...)` 循环。
- 预估收益
  - 功能：去掉「文案一变就失效」的主隐患（同时避免误伤设置页/订单页的同名按钮）。
  - 性能：`scanKill` 单轮少最多 2500 次 `textContent + 2 次正则 + 3 个临时字符串`；`PREP`/`CLEAN` 各少一遍 9 选择器文本扫描，截图前置省 ~100~300ms。

### [P0] `AnswerPageKiller.worth()` 恒为 true：每个 Activity 每次生命周期触发 3 次全树遍历
- 文件:行号 `AnswerPageKiller.java:33-35`（`ACTIVITY_HINTS`）、`:67-74`（`worth`）、`:76-97`（`schedule`）、`:54-55`（挂 `onResume`/`onCreate`）
- 现象/风险
  - 作业帮类名是 `com.baidu.homework.activity.xxx`，而 `ACTIVITY_HINTS` 里含 `"Homework"` → `name.toLowerCase().contains("homework")` **对 App 里几乎所有 Activity 都成立**。注释写的「只扫名字像解答/搜题页的 Activity」实际不成立。
  - 结果：任意页面 `onCreate` + `onResume` 各排 3 个延时（600/2000/6000ms），每次 `clean()` 从 `decor` 全树 `collectText` 一遍 → 每进出一次页面 **6 趟全树**，每趟 1500~3000 次 `XposedHelpers.callMethod`（反射无缓存，见下条）。这是当前最大的单点卡顿源。
  - 误伤面同样变大：任何页面出现「问小帮 / AI解题」文字都会被 GONE。
  - 附带零碎开销：`worth()` 每次调用为 7 个 hint 各做一次 `hint.toLowerCase()`（每次新建字符串），每 Activity 生命周期 2~4 次。
- 建议改法
  - 替换 `ACTIVITY_HINTS`（`:33-35`）与 `worth`（`:67-74`）为包前缀判定（零分配）：
    ```java
    /** 只认搜题/解答/历史记录三类页面（类名前缀，零分配）。按实际页面补前缀。 */
    private static final String[] PAGE_PREFIX = {
            "com.baidu.homework.activity.search",
            "com.baidu.homework.activity.user.UserAskRecord",
    };

    private static boolean worth(Object activity) {
        if (activity == null) return false;
        String name = activity.getClass().getName();
        for (String p : PAGE_PREFIX) {
            if (name.startsWith(p)) return true;
        }
        return false;
    }
    ```
  - `schedule()` 的 `DELAYS`（`:24`）压成 `{800L, 3000L}`，并在 `clean()` 命中后不再重排（同 P0-3 的 `DONE` 标记）。
- 预估收益
  - 每页面 6 趟 → 0~2 趟；按单趟 50~200ms 计，**每次页面切换省 200~800ms 主线程时间**。

### [P0] `XposedHelpers.callMethod` 每次调用都做全类反射查找，把每个 View 遍历放大几十倍
- 文件:行号 `compat/XposedHelpers.java:31-48`（`callMethod`）、`:70-83`（`findMethod`）；热点调用方：`AnswerPageKiller:159-171 / 254-287 / 318-334`、`HomeChromeKiller:141-158 / 209-260`、`WebKiller:443-451 / 494-506`
- 现象/风险
  - `findMethod` 每次调用都 `for (c = clazz; ...; c = c.getSuperclass()) for (Method m : c.getDeclaredMethods())`。ART 上 `getDeclaredMethods()` **每次都会新建 `Method[]` 并把方法对象复制出来**，`View` 约 700 个方法。
  - 「遍历 800 个 View」= 约 1600~2400 次 `callMethod` = 约 **110 万次方法比较 + 1600 个 `Method[]` 数组分配**；单趟 50~200ms 几乎全是反射开销。`m.setAccessible(true)` 也每次重复执行。
- 建议改法
  - 方案 A（最彻底，改动局部）：遍历处直接强转，不用反射。以 `AnswerPageKiller.collectText`（`:159-171`）为例整段替换：
    ```java
    private static void collectText(Object v, List<Object> out, int depth) {
        if (!(v instanceof android.view.View) || depth > 60) return;
        android.view.View view = (android.view.View) v;
        if (view instanceof android.widget.TextView) out.add(view);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) view;
            for (int i = 0, n = g.getChildCount(); i < n; i++) {
                collectText(g.getChildAt(i), out, depth + 1);
            }
        }
    }
    ```
    `HomeChromeKiller.collect`（`:141-158`）、`collectText`（`:209-221`）、`collectByName`（`:232-244`）同样处理（`XMarqueeView`/`StateImageView`/`BadgeTextView` 可保留 simpleName 比较，但走路用强转）。
  - 方案 B（一处改完全模块）：在 `compat/XposedHelpers.java` 类体加缓存，并把 `callMethod` 前 4 行（`:33-36`）改为：
    ```java
    private static final java.util.concurrent.ConcurrentHashMap<String, Method> MCACHE =
            new java.util.concurrent.ConcurrentHashMap<String, Method>();

    public static Object callMethod(Object obj, String name, Object... args) {
        if (obj == null) throw new NullPointerException("callMethod on null: " + name);
        Object[] a = args == null ? new Object[0] : args;
        Class<?> cls = obj.getClass();
        String key = cls.getName() + '#' + name + '/' + a.length;
        Method m = MCACHE.get(key);
        if (m == null) {
            m = findMethod(cls, name, a);
            MCACHE.put(key, m);
        }
        try {
            m.setAccessible(true);
            return m.invoke(obj, a);
        } catch (InvocationTargetException e) {
            throw rethrow(e.getCause());
        } catch (Throwable t) {
            throw new RuntimeException("callMethod " + name + " failed: " + t, t);
        }
    }
    ```
    （key 只含「类名+方法名+参数个数」。本仓库同 arity 的重载无歧义；若将来引入，把首参类名拼进 key。）
- 预估收益
  - 单趟全树遍历 **50~200ms → 2~5ms（约 -95%）**；`AnswerPageKiller`、`HomeChromeKiller`、`WebKiller.bindNativeLongPress`、`PageShot.findScroller` 全部受益。全模块性价比最高的一处。

### [P0] `HomeChromeKiller.clean()` 一次做三趟全树，且每个生命周期跑 5 次 → 首页最多 30 趟
- 文件:行号 `HomeChromeKiller.java:28`（`DELAYS` 5 个）、`:73-94`（`schedule`）、`:96-139`（`clean` 内三次遍历）
- 现象/风险
  - `clean()` 内 `hideJunkCards(root)` 一趟、`collect(root,...)` 一趟、`collectByName(root,"BadgeTextView")` 再一趟。
  - `onCreate` + `onResume` 各排 5 个延时（600/1500/3000/6000/12000ms）→ 最多 10 × 3 = **30 趟全树**，而首页 `decor` 挂着整个信息流 RecyclerView 的可见 item。叠加 P0-3 的反射开销 → 进首页/每次回首页必卡。
  - 12000ms 那次几乎必然什么都干不了（前面已藏完），纯浪费。
- 建议改法
  - 三趟合一 + 「成功即停」标记。替换 `:96-139` 的 `clean()`：
    ```java
    /** 每个 decor 只完整清一次（首页 RecyclerView 很大，重复全树代价太高）。 */
    private static final java.util.WeakHashMap<Object, Boolean> CLEANED =
            new java.util.WeakHashMap<Object, Boolean>();

    private static void clean(Object root) {
        if (!(root instanceof android.view.View)) return;
        synchronized (CLEANED) {
            if (CLEANED.containsKey(root)) return;
        }
        List<android.view.View> marquees = new ArrayList<android.view.View>();
        List<android.view.View> icons = new ArrayList<android.view.View>();
        List<android.view.View> badges = new ArrayList<android.view.View>();
        walk((android.view.View) root, marquees, icons, badges, 0);
        for (android.view.View m : marquees) {                 // 原 :105-111 语义
            android.view.ViewParent p = m.getParent();
            if (p instanceof android.view.View) hide(p);
        }
        if (icons.size() >= 2) { /* :114-128 最左 logic 原样保留 */ }
        for (android.view.View b : badges) hide(b);            // 原 :131-138
        synchronized (CLEANED) { CLEANED.put(root, Boolean.TRUE); }
    }

    private static void walk(android.view.View v, List<android.view.View> marquees,
                             List<android.view.View> icons, List<android.view.View> badges, int depth) {
        if (v == null || depth > 60) return;
        String simple = v.getClass().getSimpleName();
        if ("XMarqueeView".equals(simple)) marquees.add(v);
        else if ("StateImageView".equals(simple)) icons.add(v);
        else if ("BadgeTextView".equals(simple)) badges.add(v);
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0, n = g.getChildCount(); i < n; i++) {
                walk(g.getChildAt(i), marquees, icons, badges, depth + 1);
            }
        }
    }
    ```
    `DELAYS`（`:28`）改成 `{600L, 2500L}`：`CLEANED` 让第 2 次变 O(1)，保留它只兜「首屏数据晚到」。
- 预估收益
  - 30 趟 → 1~2 趟；进首页/回首页省 **0.5~1.5s CPU**，点击到展开的掉帧基本消失。

### [P0] `PageShot` 在主线程做 PNG 编码 + 写相册（长图越大越久，ANR 风险）
- 文件:行号 `PageShot.java:485-513`（`saveBitmap`，`:499-501` 的 `bmp.compress`、`:504` 的 `H5Bridge.saveBytes`）、调用链 `:460-462 finish()`、`:619`
- 现象/风险
  - 整个 `Job` 跑在主线程（`main = new Handler(Looper.getMainLooper())`）。`Bitmap.compress(PNG, 100)` 对 1080×14000 的 ARGB（约 60MB 原始）要 **0.5~3s**，`MediaStore` insert + `os.write(10~20MB)` **再 0.3~1.5s**。全程 UI 冻结，最大长图直接 ANR。
  - `bos.toByteArray()`（`:501`）把已编码数据再复制一份，峰值 +10~20MB。
- 建议改法
  - 把编码/落盘移到后台线程，主线程只 toast 与状态。替换 `saveBitmap`（`:485-513`）：
    ```java
    private void saveBitmap(final Bitmap bmp) {
        if (bmp == null) { done(false, "没有可保存的图像"); return; }
        final String name = "zybclean_" + android.text.format.DateFormat.format(
                "yyyyMMdd_HHmmss", System.currentTimeMillis()) + ".png";
        final Bitmap src = bmp;
        new Thread(new Runnable() {
            @Override public void run() {
                byte[] data = null;
                Bitmap work = src;
                try {
                    if (part != null && part.length() > 0 && !"full".equals(part)) {
                        Bitmap cp = cropPart(work);          // 只读像素，可在后台
                        if (cp != work) { work.recycle(); work = cp; }
                    }
                    ByteArrayOutputStream bos = new ByteArrayOutputStream(1 << 20);
                    work.compress(Bitmap.CompressFormat.PNG, 100, bos);
                    data = bos.toByteArray();
                } catch (Throwable ignored) {
                } finally {
                    if (work != src) work.recycle();
                    src.recycle();
                }
                final byte[] out = data;
                final String path = out == null ? "" : H5Bridge.saveBytes(ctx, name, "image/png", out);
                main.post(new Runnable() {
                    @Override public void run() {
                        HookEntry.log("PageShot saved: mode=" + modeOf() + " part=" + part
                                + " size=" + (out == null ? 0 : out.length) + " B path=" + path);
                        done(true, path == null || path.length() == 0 ? "保存失败" : "长图已保存到相册");
                    }
                });
            }
        }, "zyb-shot").start();
    }
    ```
- 预估收益
  - UI 冻结 0.8~4.5s → **0**；ANR 窗口消除。这是长图功能唯一的阻塞源。

---

# P1（应改：功能风险 / 明显浪费）

### [P1] `inject()` 每次 `loadUrl` 都排 4 个 12KB JS 注入 + 1 个 5s 诊断，旧 Runnables 从不取消
- 文件:行号 `WebKiller.java:31`（`DELAYS`）、`:339-380`（`inject`）、`:342-357`（4 次 `postDelayed`）、`:362-379`（5s 诊断）、`:360`（`scheduleBind`）
- 现象/风险
  - `loadUrl` 在重定向链 / SPA 里会被多次调用（登录跳转、302 链、reload），每次都排 4 个定时器。跳转两次 = **8 次 12KB `evaluateJavascript`**；每次都要跨 JNI 传串 + Blink 解析编译（1~3ms）。`__zybclean` 幂等标记只能省掉执行，省不掉传输和编译。
  - 旧定时器不会因新导航失效：在已换文档的 WebView 上执行旧脚本，会把上一页的 `__zybNavInfo` 状态刷回来；`scheduleBind` 每次都会再排一次（`BIND_ROOTS` 只兜住「已绑定」的情况，`getRootView` 在 `inject` 时还会同步调一次）。
  - `:362-379` 的诊断是**常驻**的（不受 `BuildFlags.PROBES` 控制），每次导航 5s 后打一条 LSPosed 日志（框架 IPC + 落文件）。
- 建议改法
  - 加「代次令牌」+ 减到 2 次注入 + 诊断进 debug 包。新增字段，替换 `:339-380` 整个 `inject`：
    ```java
    private static final long[] INJECT_DELAYS = {1500L, 6000L};
    /** 每个 WebView 的注入代次：新导航让旧的待执行 Runnable 直接 no-op。 */
    private static final WeakHashMap<Object, int[]> GEN = new WeakHashMap<Object, int[]>();

    private static void inject(final Object webView) {
        ensureBridge(webView);
        remember(webView);
        final int[] gen;
        synchronized (GEN) {
            int[] g = GEN.get(webView);
            if (g == null) { g = new int[]{0}; GEN.put(webView, g); }
            g[0]++;
            gen = g;
        }
        final int myGen = gen[0];
        for (long delay : INJECT_DELAYS) {
            try {
                XposedHelpers.callMethod(webView, "postDelayed", new Runnable() {
                    @Override public void run() {
                        if (gen[0] != myGen) return;      // 已被更新的导航取代
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
        if (gen[0] == 1) scheduleBind(webView, 0);      // 同一 WebView 只挂一次长按
        if (BuildFlags.PROBES) { /* 原 :362-379 诊断块原样放这里 */ }
    }
    ```
- 预估收益
  - 每次导航少 2~3 次 12KB 注入（约 10~30ms WebView 主线程）+ 少 1 个 5s 定时器与 1 条日志 IPC；重定向链上线性放大。

### [P1] `expandShot()` 失败/异常路径不还原 LayoutParams → WebView 永久停在 14000px，后续 `grab()` 每帧分配 60MB
- 文件:行号 `PageShot.java:521-596`（`expandShot`，`:541-543` 改高度）、`:643-654`（`stitchFallback` 不还原）、`:627-641`（`restoreHeight`）、`:675-689`（`done` 也不还原）
- 现象/风险
  - 只有 `drawExpanded`（`:598-625`）会调 `restoreHeight`。`expandShot` 自己的 `catch`（`:593-595`）直接 `stitchFallback`，**不还原**；若嵌套 `postDelayed` 因 Activity 销毁/Handler 移除没跑，就再没人还原。
  - 一旦停在 14000px：页面变成超高白板（用户可见），且 `grab()`（`:334`）`Bitmap.createBitmap(wv.getWidth(), wv.getHeight())` = **1080×14000×4 ≈ 60MB/帧**，直接 OOM。
  - `done()` 作为统一出口不还原高度，等于放弃最后一道保险。
- 建议改法
  - `Job` 加字段 `private int oldLpHeight = Integer.MIN_VALUE;`，新增两个方法并统一三处出口：
    ```java
    private void applyExpandedHeight(int targetH) {
        android.view.ViewGroup.LayoutParams lp = wv.getLayoutParams();
        if (lp == null) { stitchFallback("no layout params"); return; }
        oldLpHeight = lp.height;
        lp.height = targetH;
        wv.setLayoutParams(lp);
        wv.requestLayout();
    }

    private void restoreExpandHeight() {
        if (oldLpHeight == Integer.MIN_VALUE) return;
        int h = oldLpHeight;
        oldLpHeight = Integer.MIN_VALUE;
        restoreHeight(h);
    }
    ```
    - `:541-543` → `applyExpandedHeight(targetH);`
    - `drawExpanded` 里 `:603/:611/:622` 的 `restoreHeight(oldH)` → `restoreExpandHeight()`（`oldH` 参数可删）
    - `stitchFallback`（`:643`）第一行插 `restoreExpandHeight();`
    - `done`（`:675`）第一行插 `restoreExpandHeight();`
- 预估收益
  - 消除「截图失败后页面变超长白板」事故；最坏情况下的 60MB/帧分配回落到正常视口。

### [P1] `expandShot()` 用 1200/900/420ms 死等，重排与软件栅格化没完成就落笔 → 半张白图
- 文件:行号 `PageShot.java:546-592`（三层 `postDelayed`）、`:598-625`（`drawExpanded` 只在 `ink<300` 时判失败）
- 现象/风险
  - 改高度后要 measure/layout + Chromium 重新栅格化整页（14000px、软件层），大页面 900ms 常不够。`drawExpanded` 只抽样判「有没有内容」，只要上半屏有字（`ink>=300`）就算成功，**下半张空白照样存进相册**。不可复现（跟机器负载相关），是最常见的用户投诉形态。
- 建议改法
  - 「布局稳定轮询 + 分段 ink 检查」替换写死的 900/420。替换 `:546-592`：
    ```java
    private void afterExpandLayout(final int targetH, final int keepH) {
        awaitHeight(targetH, new Runnable() {
            @Override public void run() {
                try { wv.setLayerType(View.LAYER_TYPE_SOFTWARE, null); } catch (Throwable ignored) { }
                main.postDelayed(new Runnable() {
                    @Override public void run() { cleanThenDraw(keepH, 0); }
                }, 200L);
            }
        });
    }

    /** 轮询到 WebView 真正达到目标高度（最多 6s），而不是死等固定毫秒。 */
    private void awaitHeight(final int targetH, final Runnable then) {
        final long deadline = android.os.SystemClock.uptimeMillis() + 6000L;
        final Runnable poll = new Runnable() {
            @Override public void run() {
                boolean ok = wv.getHeight() >= targetH - 8 && wv.getWidth() > 0;
                if (ok) { then.run(); return; }
                if (android.os.SystemClock.uptimeMillis() > deadline) {
                    HookEntry.log("PageShot expand timeout " + wv.getHeight() + "/" + targetH);
                    then.run();
                    return;
                }
                main.postDelayed(this, 100L);
            }
        };
        main.postDelayed(poll, 100L);
    }
    ```
    `drawExpanded` 里补下半屏判定：`int inkTop = inkPixels(bmp, 0, h/2); int inkBottom = inkPixels(bmp, h/2, h);`，若 `h > 3000 && inkBottom < 100` 则重试一次 `cleanThenDraw(keepH, 1)`，仍失败才回退拼接（`inkPixels` 需加 `(from,to)` 重载）。
- 预估收益
  - 消除「长图下半截空白」；正常机器反而更快（布局稳定即落笔，常见 300~500ms，而非固定 2100ms）。

### [P1] `WEBVIEWS` 用 `ArrayList` 强引用最多 8 个 WebView（连带 Activity）
- 文件:行号 `WebKiller.java:33`（`WEBVIEWS`）、`:585-594`（`remember`）、`:552-566`（`revealBars`）
- 现象/风险
  - `remember()` 把 WebView 强引用进静态 `ArrayList`；`MAX_WEBVIEWS=8` 只限条数，**不是弱引用**。WebView 的 `getContext()` 是 Activity，所以最多 8 个已销毁的搜题/解答 Activity 被钉住（每个几十 MB）。长时间刷题时就是稳定的内存增长。
- 建议改法
  - 换弱引用集合并顺手清理死引用。替换 `:33` 与 `:585-594`：
    ```java
    private static final List<java.lang.ref.WeakReference<Object>> WEBVIEWS =
            new ArrayList<java.lang.ref.WeakReference<Object>>();

    private static boolean remember(Object webView) {
        synchronized (WEBVIEWS) {
            for (java.util.Iterator<java.lang.ref.WeakReference<Object>> it = WEBVIEWS.iterator(); it.hasNext(); ) {
                Object o = it.next().get();
                if (o == null) { it.remove(); continue; }
                if (o == webView) return false;
            }
            WEBVIEWS.add(new java.lang.ref.WeakReference<Object>(webView));
            while (WEBVIEWS.size() > MAX_WEBVIEWS) WEBVIEWS.remove(0);
            return true;
        }
    }
    ```
    `revealBars`（`:553-565`）改成遍历弱引用、`get()==null` 跳过。
- 预估收益
  - 消除最多 8 个 Activity 级泄漏，常驻内存峰值可降数十 MB。

### [P1] `ensureBridge` / `bindOne` 先写缓存标记再执行操作 → 一次失败就永久挂空
- 文件:行号 `WebKiller.java:569-583`（`ensureBridge` 先 `BRIDGED.put` 再 `addJavascriptInterface`）、`:528-550`（`bindOne` 先 `BOUND_NATIVE.put` 再 `setOnLongClickListener`）
- 现象/风险
  - `:570-573` 先把 WebView 记进 `BRIDGED`，`:578` 的 `addJavascriptInterface` 若抛异常（WebView 已销毁、类名混淆等），标记已生效 → **该 WebView 永远拿不到 `zybcleanHost`**，页面「复制内容/存图/存题目」全部报「桥未就绪」，且不重试。
  - `bindOne` 同理：`:530-532` 先记 `BOUND_NATIVE`，`:541` 失败后永不重试 → 长按唤回浮窗永久失效。
  - 方向反了：成功侧缺幂等保护、失败侧不收敛。
- 建议改法
  - 「成功后才记」。替换 `ensureBridge`（`:569-583`）：
    ```java
    private static void ensureBridge(Object webView) {
        synchronized (BRIDGED) {
            if (BRIDGED.containsKey(webView)) return;
        }
        try {
            Object ctx = XposedHelpers.callMethod(webView, "getContext");
            if (!(ctx instanceof android.content.Context)) return;
            Object bridge = new H5Bridge((android.content.Context) ctx, (android.webkit.WebView) webView);
            XposedHelpers.callMethod(webView, "addJavascriptInterface", bridge, "zybcleanHost");
            synchronized (BRIDGED) { BRIDGED.put(webView, Boolean.TRUE); }
            HookEntry.log("WebKiller bridge attached");
        } catch (Throwable t) {
            HookEntry.log("WebKiller bridge failed: " + t);
        }
    }
    ```
    `bindOne`（`:528-550`）把 `BOUND_NATIVE.put` 移到 `setOnLongClickListener` 成功之后（见 P2-11 的整段替换）。
- 预估收益
  - 一次抖动不再让整页复制/存图/长按永久失效；性能不变。

### [P1] `walkBanner` 每节点调 `getLocationOnScreen`（O(N·depth)）并分配 `int[2]`
- 文件:行号 `AnswerPageKiller.java:254-287`（`:258` 每节点一次 `locOf`）、`:318-325`（每次都 `new int[2]`）、`:209-220`（4 个延时）
- 现象/风险
  - `getLocationOnScreen` 沿父链逐级累加 `mLeft/mTop - scrollX/scrollY`（含 matrix），单次 O(depth)。`walkBanner` 每个节点调一次 → 整体 O(N·depth) + 每节点一个小数组。历史记录页深度 20~30、节点数百 → 上万次父链遍历，4 次延时各一遍。
  - 遍历本身已知道父子关系，用「累计偏移」可 O(N) 算完。
- 建议改法
  - 替换 `walkBanner`（`:254-287`），并同步改 `killBottomBanner` 的入口（`:232-248`）：
    ```java
    private static int walkBanner(android.view.View v, int offsetTop, int dw, int dh, int depth) {
        if (v == null || depth > 40) return 0;
        int w = v.getWidth();
        int h = v.getHeight();
        int top = offsetTop;
        int bottom = top + h;
        boolean hit = depth > 0 && w >= dw * 95 / 100 && h >= 80 && h <= dh * 3 / 10
                && bottom >= dh - 6 && bottom <= dh + 6;
        if (hit) { hideSiblingAbove(v); hide(v); return 1; }
        int count = 0;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            int childOffset = top - v.getScrollY();
            for (int i = 0, n = g.getChildCount(); i < n; i++) {
                android.view.View c = g.getChildAt(i);
                if (c == null) continue;
                count += walkBanner(c, childOffset + c.getTop(), dw, dh, depth + 1);
            }
        }
        return count;
    }
    ```
    入口 `int hidden = walkBanner((android.view.View) decor, 0, dw, dh, 0);`（decor 自身 top 为 0，等价原 `locOf(decor)[1]` 基准）。`locOf` 仅 probe 分支保留或删除。
- 预估收益
  - 单趟从「数百次父链遍历 + 数百个 int[2]」降到「每节点一次 getTop」；4 次延时合计省 ~10~30ms。

### [P1] `FeedKiller` 用新 List 替换 `args[0]`（`killForever` 也是）：分页/追加数据会丢
- 文件:行号 `FeedKiller.java:158-173`（`FeedFilterHook`，`:166` `param.args[0]=out`）、`:229-244`（`filter` 返回新 List）、`:88-123`（`killForever`，`:111` 换成空 `ArrayList`）、`:208-227`（`stripFieldList` 在 `notifyDataSetChanged` 之后改列表）
- 现象/风险
  - adapter 保存的是**新** List 实例，而调用方（`addData` 发起者、分页组件）还持旧引用。后续 `loadMore` 往旧 List `addAll()`，adapter 看不到 → 视图不更新或反复请求下一页（表现为「一直转圈/重复加载」）。
  - `killForever` 同病；`getItemCount` 恒 0 与 adapter 内部 `notifyItemInserted` 的索引计算不一致，有 `IndexOutOfBoundsException` 风险。
  - `stripFieldList` 在 `notifyDataSetChanged` 的 **after** 里直接改后端列表：此时 RecyclerView 已算完 item 数/位置，列表变短后下次 layout 的 position 可能越界（`Inconsistency detected`），且不再 notify，界面与数据不同步。
- 建议改法
  - 保留 List 身份，就地过滤。替换 `beforeHookedMethod`（`:159-167`）：
    ```java
    @Override
    protected void beforeHookedMethod(MethodHookParam param) {
        if (param.args == null || param.args.length == 0) return;
        if (!(param.args[0] instanceof List)) return;
        @SuppressWarnings("unchecked")
        List<Object> in = (List<Object>) param.args[0];
        probe(param.thisObject, in);
        try {
            in.removeIf(FeedKiller::isAdItem);      // 就地过滤，引用不变
        } catch (Throwable ignored) {
        }
    }
    ```
    `filter()`（`:229-244`）与 `out` 赋值删掉。`killForever` 的 `takesList` 分支（`:108-114`）改用 `((List<?>) param.args[0]).clear()`（不可变 List 会抛，catch 后降级）。`stripFieldList` 只留在数据入口，把 `:67-74` 的 `notifyDataSetChanged` hook 删掉（入口已过滤）。
- 预估收益
  - 修掉分页/追加丢失的功能隐患；信息流滚动时少一次反射字段查找 + 一次全列表 `removeIf`。

### [P1] JS `loop()` 每 450ms + 3s 都读整页 `body.textContent`；`scanKill` 每轮最多 2500 次 rect 且读写成对交错
- 文件:行号 `WebKiller.java:249-254`（`gate`，`:252`）、`:255`（`loop`）、`:260-263`（450ms + 3000ms）、`:73-93`（`scanKill`）
- 现象/风险
  - `gate()` 的 `document.body.textContent` 会把整页文本节点拼成新字符串：解答页 50~200KB，每 450ms 与每 3s 各一次 → 持续 GC 压力 + 主线程几十毫秒级分配。
  - `scanKill` 对最多 2500 个元素做 `textContent` → `replace(/\s+/g,' ')` → `trim` → `replace(/\s/g,'')`（**每元素 2 次正则 + 3 个临时字符串**），通过的再做 `getBoundingClientRect()`。而 `hide()` 写 `display:none` 与后续 rect 读**交错在同一循环**：每次写让布局失效、下一个读强制同步重排（layout thrash）。首屏几轮最贵。
  - 结果：每 450ms 有 5~20ms 的 JS 卡顿（首轮 30~80ms），与 App 渲染抢主线程。
- 建议改法（语义不变的三处小改）
  1. `gate` 结果缓存，仅 URL 变化时重算。替换 `:249-254`：
     ```js
     "var gateUrl='',gateOk=false;"
     "function gate(){var bar=document.getElementById('zyb-bar');if(!bar)return;"
     "if(window.__zybCapture||window.__zybBarOp===0){bar.style.display='none';return;}"
     "if(gateUrl!==location.href){gateUrl=location.href;"
     "var b=document.body;var t=(b&&b.textContent)||'';"
     "gateOk=t.length>200&&/(答案|解析|解答|题目)/.test(t);}"
     "bar.style.display=gateOk?'flex':'none';}"
     ```
  2. 正则提到函数外（`scanKill` 的 `:78`、`:81` 每迭代都新建 `RegExp`）。在 `:72` 附近加：
     ```js
     "var AI=/^AI(解题|讲题|答疑)$/i;var WS1=/\\s+/g,WS0=/\\s/g;"
     ```
     并把 `:78` 改 `...replace(WS1,' ').trim()`、`:81` 改 `var z=t.replace(WS0,'');`
  3. `scanKill` 改「先读后写」两阶段。替换 `:73-93` 核心循环：
     ```js
     "function scanKill(){try{"
     "var els=document.querySelectorAll('div,span,li,a,button,p,section,em,strong');"
     "var n=els.length>2500?2500:els.length,hit=[];"
     "for(var i=0;i<n;i++){var el=els[i];if(el.childElementCount>4)continue;"
     "var t=(el.textContent||'').replace(WS1,' ').trim();"
     "if(!t||t.length>30)continue;var z=t.replace(WS0,'');"
     "if(!AI.test(z))continue;"
     "var r;try{r=el.getBoundingClientRect();}catch(e3){continue;}"
     "if(r.height<130)hit.push(el);}"
     "for(var k=0;k<hit.length;k++){var e2=hit[k];var nav=null;"
     "try{nav=e2.closest('#pageNav,[class*=\"navWrap\"],[class*=\"navBox\"],[class*=\"navItem\"]');}catch(e4){}"
     "if(nav)continue;var p=e2.parentElement;if(!p||p.tagName==='BODY')p=e2;hide(p);}"
     "}catch(e){}}"
     ```
     （文字命中部分按 P0-1 应整体换成 class 判定；这里只展示重排写法，并顺手去掉 `li,ul` 见 P2-10。）
- 预估收益
  - 每轮从「1 次全页字符串拼接 + 2500×2 正则 + 2500 次 rect + 读写成对」降到「0 次拼接（缓存）+ 2500 次比较 + 仅命中时 1 次 rect」，**10~60ms → 2~6ms**；450ms 节拍下持续 CPU 降一个量级。

### [P1] `PageShot` 位图峰值过高（`out` 48MB + 窗口截图 + 帧 + `cropPart` 副本）
- 文件:行号 `PageShot.java:35`（`MAX_PIXELS=1.2e7`）、`:203-215`（`out` 分配）、`:331-338`（`frame`）、`:346`（窗口位图）、`:465-482`（`cropPart` 新建整图）、`:598-619`（`drawExpanded` 60MB）
- 现象/风险
  - 单次长图的并发位图：`out`（12M px → 48MB）+ `shot`（decor 1080×2400 ≈ 10MB）+ `frame`（WebView 全尺寸）+ `cropPart` 副本（再 +最大 48MB）+ `bos.toByteArray()`（+10~20MB）。峰值 120~180MB，超过默认堆（未声明 `largeHeap`）即 `OutOfMemoryError`，用户看到「点存长图 App 崩」。
  - `MAX_EXPAND_H = 14000` 在 1080 宽下 = 60MB `ARGB_8888`，`drawExpanded`（`:607`）无任何降采样。
- 建议改法
  1. `expandShot` 路径不必先建 `out`：`begin()`（`:214`）按模式分配——JS 模式先 `out = null`，等 expand 真失败转拼接时再建，省掉 48MB 白分配。
  2. `drawExpanded`（`:607`）用 `Bitmap.Config.RGB_565`（文字截屏无色带问题，内存直接减半）。
  3. `MAX_PIXELS` 从 `1.2e7` 降到 `6e6`（26MB 上限，长图仍清晰）；`cropPart` 与编码合并（P0-5 的补丁已顺带做到「先裁再编码、编码完立刻回收源」）。
- 预估收益
  - 峰值 120~180MB → **30~60MB**；长图 OOM 基本消除，大对象分配减少也降低 GC 停顿。

### [P1] `PageShot.sBusy` 没有看门狗，任何一次异常都让长图永久失效
- 文件:行号 `PageShot.java:44`（`sBusy`）、`:56-67`（`capture`）、`:675-689`（`done` 是唯一清零点）
- 现象/风险
  - `done()` 是唯一清零点。以下任一都走不到它：`ValueCallback` 不回调（WebView 已销毁/切后台）、`main.postDelayed` 的 Runnable 被移除、`PixelCopy` 回调丢失、`expandShot` 嵌套定时器链断掉。之后所有点击只弹「正在生成，请稍候」，**直到重启 App**。
- 建议改法
  - `capture`（`:65` 之后）加超时兜底：
    ```java
    sBusy = true;
    final Job job = new Job(context.getApplicationContext(), wv, part == null ? "full" : part);
    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
        @Override public void run() {
            if (sBusy) {
                HookEntry.log("PageShot watchdog: force release");
                sBusy = false;
                job.forceRestore();
            }
        }
    }, 60000L);
    job.start();
    ```
    `Job` 里加 `void forceRestore() { restoreExpandHeight(); restoreNative(); if (out != null) { out.recycle(); out = null; } }`。
- 预估收益
  - 消除「长图再也用不了」的不可恢复故障；正常路径零开销。

### [P1] `grab()` 在 PixelCopy 路径下白分配一整帧 `frame`（每步 8~60MB）
- 文件:行号 `PageShot.java:331-338`（`grab` 开头无条件建帧）、`:341-367`（PixelCopy 分支根本不用 `frame`）、`:389-441`（`paste` 只负责回收）
- 现象/风险
  - PixelCopy 成功时 `frame` 从头到尾没被写过，只在 `finally` 回收。原生滚动每步白分配 `wv 宽 × wv 高` 的 ARGB（正常视口 1080×2400 ≈ 10MB，高度没还原时 60MB）。
  - 顺带：原 `:370-375` 的 `wv.draw` 失败分支没有 `frame.recycle()`。
- 建议改法
  - 把帧分配挪进 `draw()` 兜底分支。替换 `:331-377`：
    ```java
    private void grab(final int cssY, final int nativeY) {
        if (win != null && android.os.Build.VERSION.SDK_INT >= 26) {
            final android.view.View decor = win.getDecorView();
            final int w = decor.getWidth();
            final int h = decor.getHeight();
            if (w > 0 && h > 0) {
                final Bitmap shot = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                try {
                    android.view.PixelCopy.request(win, new Rect(0, 0, w, h), shot,
                            new android.view.PixelCopy.OnPixelCopyFinishedListener() {
                                @Override public void onPixelCopyFinished(int result) {
                                    if (result != android.view.PixelCopy.SUCCESS) {
                                        shot.recycle();
                                        done(false, "抓屏失败（" + result + "）");
                                        return;
                                    }
                                    paste(shot, null, cssY, nativeY, true);
                                }
                            }, main);
                    return;
                } catch (Throwable t) {
                    HookEntry.log("PageShot PixelCopy 异常，回退 draw(): " + t);
                    shot.recycle();
                }
            }
        }
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
            frame.recycle();
            done(false, "绘制失败：" + t);
            return;
        }
        paste(frame, null, cssY, nativeY, false);
    }
    ```
- 预估收益
  - 拼接模式每步少 8~60MB 分配 + 一次清零，30 步长图少分配 ~300MB~1.8GB 临时内存，单步省 5~15ms，峰值下来。

---

# P2（可选：细节正确性 / 代码卫生）

### [P2] `mkBar()` **不是**每轮重建（有 id 短路），真正浪费的是 `repairNav` 每轮重建 `__zybNavInfo`
- 文件:行号 `WebKiller.java:194-242`（`mkBar`，`:195` 的 `if(document.getElementById('zyb-bar')||!document.body)return;`）、`:95-107`（`repairNav`，`:105-106` 每次 `join(' | ')`）、`:255`
- 现象/风险
  - 澄清一个常见误判：`mkBar` 首行 `getElementById('zyb-bar')` 命中即返回，**只有 bar 被页面重渲染掉时才重建**（`appendChild` 会额外触发一次 MutationObserver，下一轮又因已存在而短路，不自激）。每轮成本只是一次 id 查找（微秒级），可以不管。
  - 真正浪费的是 `repairNav`：每次 `querySelectorAll` nav 节点、对每个 nav 再 `querySelectorAll('*')` 建数组、拼 `textContent` 并 `join(' | ')` 写 `window.__zybNavInfo` —— 而它只在 5s 后被 Java 读一次（且按 P1-1 应进 debug 包）。450ms 节拍下纯白烧。
- 建议改法
  - `repairNav` 只在未 dump 过时收集信息；`loop()` 里改为每 3s 一次（或仅当 `scanKill` 命中过 nav 时跑）：
    ```js
    "function repairNav(){try{"
    "var ns=document.querySelectorAll('#pageNav [class*=\"navItem\"],[class*=\"navBox\"]>*');"
    "var info=window.__zybNavDump?null:[];"
    "for(var i=0;i<ns.length;i++){var n=ns[i];/* 原 :99-104 的还原逻辑原样保留 */"
    "if(info)info.push((n.textContent||'').replace(/\\s+/g,'')+'/'+(n.style.display||'-'));}"
    "if(info){window.__zybNavDump=1;window.__zybNavInfo=info.join(' | ');}"
    "}catch(e){}}"
    ```
- 预估收益
  - 每轮少一次 `querySelectorAll` + (nav 数 × 子树选择器 + 拼接)，约 0.5~3ms/轮（大页面更多），3 次/秒。

### [P2] JS 热循环里每次都新建正则对象与临时字符串
- 文件:行号 `WebKiller.java` JS `:78`（`/\s+/g`）、`:81`（`/\s/g`）、`:105`、`:118`（`normTex`）、`:127`、`:129`、`:156-159`
- 现象/风险
  - JS 正则字面量**每次求值都创建新 `RegExp`**。`scanKill` 最多 2500 元素 × 2 = 每轮 5000 个对象；`pageText` 的 `walk` 里每个文本节点 1~2 个。纯分配 + GC 垃圾。
- 建议改法
  - 顶部统一建常量（`replace` 用 `/g` 无状态，可复用）：
    ```js
    "var RE_WS=/\\s+/g,RE_WS0=/\\s/g,RE_TEXQ=/[?&](?:tex|latex|formula|text)=([^&]+)/i;"
    ```
    替换 `:78/:81/:105/:118/:127/:159` 的对应字面量（`RE_TEXQ` 无 `/g`，`exec` 无 `lastIndex` 依赖，安全）。
- 预估收益
  - 每轮少 5000+ 个 `RegExp` 与等量临时字符串，减少可观测的 GC 抖动。

### [P2] `scanKill` 的 2500 上限按**文档序**截断，长页面后半段清不到
- 文件:行号 `WebKiller.java:74-76`
- 现象/风险
  - `var n = els.length>2500 ? 2500 : els.length;` 取 `querySelectorAll` 结果的前 2500 个（DOM 顺序）。解答页前 2500 个往往是题头/公式容器，真正的相关视频卡、推广条在后面 → 永不处理。既留功能缺口，又让「上限」失去保护意义（每轮照样扫 2500 次）。
- 建议改法
  - 用时间预算替代元素上限（比倒序抽样更稳）：
    ```js
    "var t0=Date.now();for(var i=n-1;i>=0;i--){if(Date.now()-t0>8)break;/* … */}
    ```
- 预估收益
  - 长页面底部垃圾能清掉；单轮最长 8ms，节拍可控。

### [P2] 常驻的调试日志/诊断代码（正式包里也在跑）
- 文件:行号 `AdBlocker.java:73-74`（**每次** skip 都打日志，还 `getDeclaringClass().getSimpleName()` 分配）、`WebKiller.java:362-379`（已列 P1-1）、`FeedKiller.java:175-197`（`probeBudget=6`）、`AnswerPageKiller.java:242-247 / 264-269`
- 现象/风险
  - `XposedBridge.log` 走 `XposedInterface.log`（框架 IPC + 落文件），单条几十到几百微秒。`AdBlocker` 的日志在 `doLoadAD/doShowAD` 里，信息流每次刷新可能触发多条；LSPosed 日志也被刷大，影响事后排查。
- 建议改法
  - `AdBlocker` 加计数上限（与 `FastStart.LOGGED` 同款）：
    ```java
    private static final java.util.concurrent.atomic.AtomicInteger LOGGED =
            new java.util.concurrent.atomic.AtomicInteger();
    // hook 内：
    if (LOGGED.getAndIncrement() < 30) {
        HookEntry.log("skip " + param.method.getDeclaringClass().getSimpleName()
                + "." + param.method.getName());
    }
    ```
    其余诊断统一套 `if (BuildFlags.PROBES)`。
- 预估收益
  - 去掉每次广告回调的字符串分配与 IPC。

### [P2] 三张 `WeakHashMap` 确实能回收，但存在线程安全隐患（`BIND_ROOTS` 读在锁外）
- 文件:行号 `WebKiller.java:34/36/383`（三张表）、`:319`（`BIND_ROOTS.containsKey`）、`:388`（`scheduleBind` 内 `containsKey`）、`:423/:427`（`bindNativeLongPress` 读写）
- 现象/风险
  - 结论先说：三张表**不会泄漏**——`value` 都是 `Boolean.TRUE`，不存在「value 强引用 key」的经典 WeakHashMap 泄漏环；`H5Bridge` ↔ WebView 的循环引用由 GC 处理。真正的强引用泄漏是 `WEBVIEWS`（P1-4）。
  - 但 `WeakHashMap` 非线程安全，`BIND_ROOTS` 的读写全在同步块外。目前调用点都在主线程，**没有实际竞争**；可是 `get/containsKey` 会顺带 `expungeStaleEntries`（结构性修改），一旦将来有后台线程碰它就可能 `ConcurrentModificationException` 或死循环。属于埋雷。
- 建议改法
  - `BIND_ROOTS` 改 `Collections.synchronizedMap(new WeakHashMap<>())`，或所有访问统一 `synchronized (BIND_ROOTS)`（与 `BOUND_NATIVE` 一致）。
- 预估收益
  - 无性能收益，纯稳定性。

### [P2] `HookEntry.onPackageLoaded` 没有重复-install 防护，也没按进程过滤
- 文件:行号 `HookEntry.java:31-91`（9 个 `install`）、`:32`（只判包名）
- 现象/风险
  - 若框架对同一进程回调两次（模块热更新/多 classloader），这里会把 34 个广告适配器、3 个 feed adapter、`Activity.onCreate/onResume`、`View.setOnLongClickListener` **全再挂一遍**：每个方法 2 个 hook，回调翻倍，延时任务翻倍 → 卡顿 ×2。无任何幂等保护。
  - 也没判进程：`:push`、`:remote` 等子进程同样跑全部 `Class.forName` + hook（37 次类解析），纯浪费。
- 建议改法
  - 入口加一次性开关 + 主进程判定：
    ```java
    private static volatile boolean sInstalled = false;

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (!PKG.equals(param.getPackageName())) return;
        if (!param.isFirstPackage()) { log("skip secondary process"); return; }
        synchronized (HookEntry.class) {
            if (sInstalled) { log("already installed, skip"); return; }
            sInstalled = true;
        }
        ...
    ```
    （若该 API 版本没有 `isFirstPackage()`，改用 `android.app.Application.getProcessName()` 判主进程。）
- 预估收益
  - 防止「回调翻倍」的灾难性退化；子进程启动省 ~20~60ms。

### [P2] `hookAllMethods` 只扫 `declaredMethods` → 子类覆写且不调 `super` 就挂空
- 文件:行号 `compat/XposedBridge.java:62-66`；受影响：`AnswerPageKiller.java:54-55 / 226`、`WebKiller.java:280-282 / 309`
- 现象/风险
  - `hookAllMethods` 只遍历 `clazz.getDeclaredMethods()`。hook `android.app.Activity.onResume` 只能拦到**真正执行 `Activity.onResume` 方法体**的那次调用；若某 Activity 覆写 `onResume()` 且不调 `super.onResume()`（大厂代码里不罕见），整个 hook 静默失效。`WebView.loadUrl` 同理（`NestedHybridWebView` 若覆写且不 super，注入永远不来）。
  - 佐证：`AnswerPageKiller:196-198` 的注释「目标 Activity 不自己声明 onResume，直接 hook 子类会落空」——同一份代码里对同一问题用了两套互相矛盾的假设。
- 建议改法
  - 换更稳的挂点：`android.app.Instrumentation.callActivityOnResume`（每个 Activity 必走）或按类名精确到子类 + 基类兜底：
    ```java
    private static void hookResume(ClassLoader cl, String cls, XC_MethodHook hook) {
        try {
            XposedBridge.hookAllMethods(Class.forName(cls, false, cl), "onResume", hook);
        } catch (Throwable ignored) {
            // 子类没覆写 → 落到基类
        }
        XposedBridge.hookAllMethods(android.app.Activity.class, "onResume", hook);
    }
    ```
- 预估收益
  - 修掉「某些页面完全没生效」的隐性 bug；无性能变化（配合 P2-6 的幂等开关避免重复挂）。

### [P2] `inkPixels` 逐点 `getPixel`（约 3.6 万次 JNI 调用）
- 文件:行号 `PageShot.java:657-673`
- 现象/风险
  - 采样 `w/120 × h/300` → 约 36000 次 `Bitmap.getPixel`，每次一次带校验的原生调用（200~500ns），合计 7~18ms，且在主线程（`drawExpanded` 内）。判白图不需要这么细。
- 建议改法
  - 按行批量取像素（一次 JNI 拿一整行），替换 `:657-673`：
    ```java
    private static int inkPixels(Bitmap b) {
        try {
            int w = b.getWidth(), h = b.getHeight();
            int stepY = Math.max(1, h / 300);
            int stepX = Math.max(1, w / 120);
            int[] row = new int[w];
            int ink = 0;
            for (int y = 0; y < h; y += stepY) {
                b.getPixels(row, 0, w, 0, y, w, 1);
                for (int x = 0; x < w; x += stepX) {
                    if ((row[x] & 0x00FFFFFF) != 0x00FFFFFF) ink++;
                }
            }
            return ink;
        } catch (Throwable t) {
            return 0;
        }
    }
    ```
- 预估收益
  - 36k 次原生调用 → 300 次；7~18ms → 0.5~1.5ms（约 10 倍）。

### [P2] `cropPart` 复制整图（`part="a"` 时峰值直接翻倍）
- 文件:行号 `PageShot.java:465-482`（`:481`）
- 现象/风险
  - `createBitmap(src,x,y,w,h)` 新建位图并拷像素。存「解答」时（`y0≈split`，通常占全图 80%）等于再分配近 48MB，且要等拷贝完才回收源 → 峰值 2×。叠加 P1-9 的其他位图，是 OOM 高发路径。
- 建议改法
  - 更彻底：在 `begin()`（`:190`/`:208` 附近）就按 `part` 限定 `outH`（`splitY*density` 或 总高 `- split`），从源头不画不需要的区域；退一步则保持 P0-5 的「先裁再编码、编完立刻回收源」。
- 预估收益
  - 分段截图峰值降 40~50%，编码前的拷贝时间省一半。

### [P2] `scanKill` 的 `closest('li,ul,...')` 保护范围过大，内容列表里的 AI 入口藏不掉
- 文件:行号 `WebKiller.java:86`
- 现象/风险
  - `el.closest('li,ul,#pageNav,...')` 只要祖先有任意 `ul`/`li` 就整段跳过。解答页的选项、步骤列表、相关推荐普遍是 `ul>li`，于是落在任何列表里的 AI 入口永远清不掉。意图只是「别碰顶部选项卡」，实际保护面是「全站所有列表」。
- 建议改法
  - 收窄到真实导航容器：`el.closest('#pageNav,[class*="navWrap"],[class*="navBox"],[class*="navItem"]')`，去掉 `li,ul`。
- 预估收益
  - 功能更准；选择器更简单，微增性能。

### [P2] `bindOne` 覆盖 App 原有的 `OnLongClickListener`（底栏蓝按钮行为被劫持）
- 文件:行号 `WebKiller.java:533-542`
- 现象/风险
  - `setOnLongClickListener(l)` 替换掉 App 自己设的监听；若作业帮给「再拍一页」配了长按行为（连拍、语音提问），会被永久吃掉，且 `onLongClick` 恒返回 `true`（事件被消费）。用户感知「某些长按操作没了」。
- 建议改法
  - 保留旧监听，没消费时回退（同时修掉 P1-5 的「先标记后执行」）：
    ```java
    private static boolean bindOne(final android.view.View target) {
        synchronized (BOUND_NATIVE) {
            if (BOUND_NATIVE.containsKey(target)) return false;
        }
        final android.view.View.OnLongClickListener old = target.getOnLongClickListener();
        android.view.View.OnLongClickListener l = new android.view.View.OnLongClickListener() {
            @Override public boolean onLongClick(android.view.View v) {
                if (revealBars()) return true;                  // 有已登记 WebView → 我们消费
                return old != null && old.onLongClick(v);        // 否则还原 App 行为
            }
        };
        try {
            target.setOnLongClickListener(l);
            target.setLongClickable(true);
            synchronized (BOUND_NATIVE) { BOUND_NATIVE.put(target, Boolean.TRUE); }
            HookEntry.log("WebKiller native long-press bound: " + describe(target));
            return true;
        } catch (Throwable t) {
            HookEntry.log("WebKiller native long-press bind failed: " + t);
            return false;
        }
    }
    ```
    `revealBars` 由 `void` 改为返回有无可显示目标（`:553-566` 末尾 `return !list.isEmpty();`）。`getOnLongClickListener` 是 public API，无需反射。
- 预估收益
  - 修掉对宿主交互的破坏；顺带解决 P1-5。

### [P2] `HomeChromeKiller` 把卡片 `setVisibility(GONE)` 后留下同高空白
- 文件:行号 `HomeChromeKiller.java:161-177`（`hideJunkCards`）、`:246-251`（`hide`）
- 现象/风险
  - 首页卡片多半在 RecyclerView 的 item 内，`GONE` 内层容器不会让 item 高度塌陷，结果是「广告没了但空了一块」。
- 建议改法
  - 命中的卡片连同 item 容器一起处理：
    ```java
    private static void collapse(android.view.View v) {
        android.view.ViewParent p = v.getParent();
        hide(v);
        if (p != null && p.getClass().getName().contains("RecyclerView")) {
            android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp != null) { lp.height = 0; v.setLayoutParams(lp); }
        }
    }
    ```
- 预估收益
  - 纯 UI 质量修复，无性能影响。

### [P2] `PageShot` 的 `PREP` / `CLEAN` 各做 1.5~2 遍全树 `getComputedStyle`
- 文件:行号 `PageShot.java:746-755`（全量 + 每节点 `getComputedStyle`）、`:771-783`（**第二次** `document.querySelectorAll('body *')`）、`:786-802`（第三次容器扫描 + 第四次 heading 扫描）、`CLEAN:861-863`（`scan(document)` 后再 `querySelectorAll('*')` 找 shadowRoot）
- 现象/风险
  - 每次长图跑 4 遍全页遍历 + 数千次 `getComputedStyle`（Blink 里最贵的只读 API 之一，触发样式解析/重算）。大解答页 `PREP` 单次 200~600ms，`CLEAN` 再来一遍，都在 WebView 主线程（用户看到点「存题目」后卡一下）。
- 建议改法
  - 合并成单趟：一次 `document.querySelectorAll('body *')` 里同时判 `position:fixed|sticky`、`VIDEO/IFRAME`、`class` 命中 `HRE/ADRE`、`IMG` 的 `VCDN`；容器候选（`:786-792`）从已收集集合里选，不再单独跑一遍；heading 扫描用 `h1~h6,strong,b` 的有限集合或复用同一趟。
- 预估收益
  - 截图前置从 300~900ms 降到 100~300ms；`getComputedStyle` 调用减少 60%+。

### [P2] `buildJs()` 用 60+ 段字符串拼接（一次性，但极易写错）
- 文件:行号 `WebKiller.java:41-265`（`buildJs`）、`:39`（`static final String JS = buildJs()`）
- 现象/风险
  - 只在类初始化执行一次，运行时零成本，**不是性能问题**。但 JS 无法被编辑器/`node --check` 校验，`\\s`/`\\n` 极易写错（本文件就有 `'\\n'` 与 `\n` 混用）。改动 JS 时最常在这里引入语法错误，而语法错误的唯一症状是「注入后什么都没发生」。
- 建议改法
  - 移到 `res/raw/zyb_inject.js`（或 build.sh 内联模板）用 `getResourceAsStream` 读；至少拆成 `jsSelect()/jsBar()/jsLoop()` 三段，便于离线 `node --check`。
- 预估收益
  - 无运行时收益；显著降低改错概率。

### [P2] JS `DROP` 表里有无效条目 `'答案解析':0`（笔误）
- 文件:行号 `WebKiller.java:116-117`
- 现象/风险
  - `var DROP={...,'查看解析':1,'答案解析':0};`，而 `pageText()`（`:159-160`）用 `if(DROP[L])continue;`，`0` 是 falsy → 「答案解析」这一行**永远保留**。从上下文看应为 `1`（与「查看解析」同类按钮文案）。属功能笔误，不影响用户死线（只影响复制出来的文本）。
- 建议改法
  - 改成 `'答案解析':1`，或直接删掉该项。
- 预估收益
  - 复制内容更干净。

---

# 速度优化优先级 Top 5

按「收益 / 风险」排序，全部只动逻辑不改行为，可逐条独立上线。

| # | 改动 | 位置 | 预期收益 |
|---|---|---|---|
| 1 | **修 `worth()` 恒真**（`ACTIVITY_HINTS` 含 `Homework`，命中全 App） | `AnswerPageKiller.java:33-35/67-74` | 每进一页 **6 趟全树 → 0~2 趟**；按单趟 50~200ms 计，**省 200~800ms**。1 个常量 + 1 个方法，风险极低。 |
| 2 | **给 `XposedHelpers.callMethod` 加 `Method` 缓存**（或遍历处直接强转） | `compat/XposedHelpers.java:33-44` | 单趟全树 **50~200ms → 2~5ms（-95%）**；`AnswerPageKiller`/`HomeChromeKiller`/`WebKiller`/`PageShot` 全部受益。10 行代码、全局乘性收益，最高性价比。 |
| 3 | **`HomeChromeKiller` 三趟合一 + 单次标记** | `HomeChromeKiller.java:96-139` + `:28` | 首页 **30 趟全树 → 1~2 趟**；进首页/回首页省 **0.5~1.5s CPU**。「点开就卡」直接消失，改成一个 `walk` + `WeakHashMap` 标记，无行为变化。 |
| 4 | **`inject()` 加代次令牌 + 减到 2 次注入 + 诊断进 debug** | `WebKiller.java:339-380` | 每次导航 **4 次 12KB 注入 → 2 次**（省 10~30ms WebView 主线程 + 少 2 次 JNI 传串/编译），重定向链上线性放大；顺带消掉一个 5s 定时器与一条日志 IPC。改动局限在一个方法内。 |
| 5 | **JS 热循环：`gate` 结果缓存 + 正则外提 + `scanKill` 两阶段** | `WebKiller.java:249-254 / 78-81 / 73-93` | 每 450ms 常驻开销 **10~60ms → 2~6ms**（首轮 30~80ms → ~10ms）：少 1 次全页 `textContent` 拼接、少 ~5000 个 `RegExp`/临时字符串、消除读写成对的 layout thrash。滑动手感直接改善，判定语义不变。 |

**次优候选（做完 Top5 再看）**
- `PageShot` 主线程 PNG 编码移后台（P0-5）：一次改动消除 0.8~4.5s UI 冻结，但涉及线程与 `sBusy` 生命周期，风险略高于上面 5 条。
- `walkBanner` 改累计偏移（P1-6）：省 10~30ms/次，仅历史记录页触发，优先级低。
- `inkPixels` 批量化（P2-8）：省 7~18ms/次截图，10 行以内，很安全，可随时做。

---

## 附：审查中确认「没问题」的点（避免重复排查）
- `mkBar()` 有 `getElementById('zyb-bar')` 短路，**不会**每轮重建 DOM；追加节点只额外触发一次 MutationObserver（450ms 后短路），不会自激成死循环。
- 三张 `WeakHashMap`（`BRIDGED`/`BOUND_NATIVE`/`BIND_ROOTS`）的 `value` 都是 `Boolean.TRUE`，**不存在**「value 强引用 key」的经典泄漏环；`H5Bridge` ↔ WebView 的循环引用由 GC 处理。真正的强引用泄漏是 `WEBVIEWS`（P1-4）。
- `PageShot.paste()` 的 `finally` 回收路径正确（含 PixelCopy 成功/失败两分支），没有位图泄漏（只是峰值高，见 P1-9）。
- `AdBlocker` 的 `param.setResult(null)` 用于 `void` 方法（注释已确认 34 个适配器的 `doLoadAD/doShowAD` 都是 void），不会触发拆箱 NPE。
- `FloatKiller` 只按「方法名 + void 返回」匹配，`<init>/<clinit>` 与 getter 已被正确排除；不在任何热路径上（只在类加载时装配一次）。
- `HookEntry` 装配顺序合理（`AdBlocker` → 数据层 → 视图层 → `WebKiller` → `FastStart`）；`FastStart` 最后装，避免它改到模块自己 `Handler.postDelayed` 的延时（它按 runnable 类名过滤 `InitActivity`/`Splash`，不会误伤 `PageShot` 的 700ms）。顺序**无需调整**，只缺幂等/进程过滤（P2-6）。
