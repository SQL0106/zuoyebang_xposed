# A 部分修复：H5 注入层（WebKiller.java）

范围：`src/io/github/sql0106/zybclean/WebKiller.java`（唯一可改文件）。
依据：`.agent/review-1.3.0.md` P0-1 / P1-inject / P1-WEBVIEWS / P1-ensureBridge / P1-JS 热循环。
硬约束：不编译 Java、不 git、不连设备、不全盘搜索。

## TODO

- [x] 0. 落盘本文件（先写清单再动手）
- [x] 1. `inject()`：代次令牌（WeakHashMap<Object,int[]>）+ 注入降为 2 次（1200/6000ms）+ 5s 诊断只在 `BuildFlags.PROBES` 挂
- [x] 2. `WEBVIEWS` 换弱引用集合（`Collections.newSetFromMap(new WeakHashMap)`），`revealBars()` 快照遍历
- [x] 3. `ensureBridge` / `bindOne`：先执行、成功后再写标记；失败不写标记
- [x] 4. JS 热路径：`gate()` 改缓存（`window.__zybBodyLen` + 仅在 URL 变化/有新 DOM 变更时重算）
- [x] 4b. JS 热路径：正则提到函数外（WS1/WS0），循环内不再出现正则字面量新建
- [x] 4c. JS 热路径：`scanKill()` 两阶段（先读 rect/判定，再统一 `hide()`）
- [x] 5. 删除按文字匹配隐藏：`AI=/^AI(解题|讲题|答疑)$/i` 与 `z==='问小帮'` 整段移除（`DROP` 表保留，仅影响复制文本）
- [x] 5b. 导航条保护：#pageNav / ul / li / nav / navWrap / navBox / navItem 内节点一律跳过（`inNav()`，自家与父节点双检）
- [x] 6. 功能保持不变（5 按钮 / 拖动 + localStorage / __zybBarOp / 700ms 长按 / __zybBarShow / repairNav + __zybNavInfo / 原生长按绑定链）
- [x] 7. JS 语法自检：python3 抽 `buildJs()` 字符串 → `/tmp/opencode/webkiller.js` → `node --check`
- [x] 8. 本文件补「改了什么 / 未做 / 风险」

## 改了什么

### Java 侧
1. `DELAYS{1200,3000,6000,12000}` → `INJECT_DELAYS{1200,6000}`；新增
   `GEN = new WeakHashMap<Object,int[]>()` 代次表。`inject()` 每次进入先 `g[0]++`，
   两个 `postDelayed` 的 Runnable 执行前在 `synchronized(GEN)` 内比对 `gen[0]!=myGen` → 直接 return，
   旧导航的定时器不再能往新文档里灌脚本/刷 `__zybNavInfo`。
2. `scheduleBind()` 只在 `myGen==1`（该 WebView 首次导航）时挂一次，避免每次重定向都排一次绑定。
3. 5 秒 `WebKiller nav:` 诊断整块包进 `if (BuildFlags.PROBES)`（正式包 javac 直接消掉）。
4. `WEBVIEWS`：`ArrayList<Object>` → `Collections.newSetFromMap(new WeakHashMap<Object,Boolean>())`，
   超 `MAX_WEBVIEWS` 用 iterator 移除（不删刚加入的那个）；`revealBars()` 先 `toArray()` 快照再遍历。
5. `ensureBridge()`：`BRIDGED.put` 移到 `addJavascriptInterface` 成功之后（异常 → 不写标记，下次导航重试）。
6. `bindOne()`：`BOUND_NATIVE.put` 移到 `setOnLongClickListener`/`setLongClickable` 成功之后；
   已绑定直接返回 true（让 `bindNativeLongPress` 能落 `BIND_ROOTS`，不再每次 onResume 重扫）。

### JS 侧（buildJs）
1. 删除 `var AI=/^AI(解题|讲题|答疑)$/i` 与 `scanKill` 内 `AI.test(z)||z==='问小帮'` 文字判定（用户死线）。
   `DROP` 表原样保留（只影响复制文本，不影响显示）。
2. `scanKill()` 重写：不再遍历 2500 个 `div/span/li/...`、不再读 `textContent`，
   改为 `querySelectorAll(KILLSEL)`（`[class*="ai-tab"] / aiTab / ai-entry / ai-solve / ai-assistant / [data-role=ai]`）。
   第一趟只读（`inNav()` 过滤 + 每个候选单次 `getBoundingClientRect`，高度≥130 跳过），第二趟统一 `hide()`。
3. 新增 `scanFloat()`：`[class*="video|poster|player"]` 候选 → 向上 8 层找 `position:fixed|sticky` 祖先
   （且高度 < 60% 视口）→ 统一 hide。取代原来 `t.indexOf('讲解')>=0` 的文字入口，语义仍是「干掉悬浮讲解视频卡」。
4. `inNav(el)`：`closest('#pageNav,[class*="navWrap"],[class*="navBox"],[class*="navItem"],nav,ul,li')`，
   `scanKill`/`scanFloat` 对候选节点与将要隐藏的父节点各查一次 → **导航条内任何节点都不会被隐藏**。
5. `gate()`：不再每轮读整页 `document.body.textContent`。新增 `gateMeasure()`：
   只在「URL 变化」或「`window.__zybMut`（MutationObserver 计数）变化且上次未通过」时重算，
   结果缓存在 `window.__zybBodyLen` / `gateOk`。空闲页与已通过页 0 次全文读取。
6. 正则提外：`WS1=/\s+/g`、`WS0=/\s/g` 在 IIFE 顶部定义一次，
   `normTex / walk / pageText / imgTex / repairNav` 内联字面量改用它们（循环/逐节点路径内无正则新建）。
7. `loop(force)`：`scanKill/scanFloat/repairNav` 只在「有 DOM 变更」或强制（首轮、3s 心跳）时跑；
   `mkBar/gate` 每轮都跑（两者都已是早退/缓存实现）。
8. 观察器回调 `window.__zybMut++`（给 `gate` 判脏用），仍保持 450ms 防抖、`{childList,subtree}`（不监听 attributes，避免自触发）。

## 验证（无本地编译，仅静态/脚本自检）

- **Java**：删注释与字符串后括号配平 `{}=96/96`、`()=249/249`、`[]=11/11`；逐行复核
  `inject()` 的 `final int[] gen`/`myGen` 捕获、`synchronized(GEN)` 读代次、`BuildFlags.PROBES` 分支
  （`if (false)` 是 JLS 允许的条件编译写法）、`Collections.newSetFromMap(new WeakHashMap<Object,Boolean>())`
  的类型（`Set<Object>`）、`Iterator<Object>` 迭代删除、`bindOne` 返回值语义改动只影响 `bindNativeLongPress`。
  未跑 `javac`/`build.sh`/`d8`（硬约束）。
- **JS**：`/tmp/opencode/extract_js.py` 从 `buildJs()` 抽出 219 个字面量 → `/tmp/opencode/webkiller.js`
  （12,108 字符）→ `node --check` **通过**。
- **JS 语义冒烟**：`/tmp/opencode/smoke.js`（最小 DOM 桩，vm 沙箱）跑 IIFE 初始化 + MutationObserver 防抖 +
  3s 心跳 + `__zybBarShow()`，**13 项全过**：
  `__zybclean` 标记、`window.__zybBodyLen=404`、浮窗创建、gate→`display:flex`、
  **AI 入口（普通容器）被隐藏**、**导航条 `li` 内节点未被隐藏**、**视频浮卡（fixed 100px）被隐藏**、
  **全屏播放器（fixed 3000px）未被隐藏**、`__zybBarShow` 存在且返回 true、变更后仍正常、
  `__zybBarOp=0` 时 gate 不拉回浮窗、换 URL 后重新测量并恢复。
- **文案匹配残留扫描**：`问小帮`/`讲解` 仅剩 DROP 表（只影响复制文本）；`AI.test`、`new RegExp` = 0 处。

## 未做（及原因）
- 没做任何 Java 编译/`./build.sh`/`d8`（硬约束），Java 改动靠逐行阅读 + 类型/签名自检（记录见下）。
- 没核实真机上 AI 入口/视频浮卡的真实 class 名 → `KILLSEL`/`FLOATSEL` 是按命名惯例给的保守集合；
  如果真机上 class 不含这些 token，`scanKill` 会「什么都不藏」——**宁可漏藏，不可误藏**（用户死线），
  后续要补只能补 class/结构，不能回到文案匹配。
- 未动 `H5Bridge` / `PageShot` / `AnswerPageKiller` / `HookEntry` / manifest / 文档（不属本次范围）。

## 风险
1. `KILLSEL` 覆盖面未经真机验证：可能漏藏（AI 入口仍在）。这是死线优先的有意取舍。
2. `scanFloat` 的「fixed/sticky + 高度<60% 视口 + class 含 video/poster/player」仍可能误伤页面里的
   小尺寸悬浮播放器（旧规则按『讲解』文案判定，命中面更窄）。防护阈值取 60% 视口，全屏播放器不会被藏。
3. `gate` 改为缓存：`gateOk` **只升不降**——同一 URL 内正文变少（极少见）时浮窗不会被自动收起，
   换 URL 才会重算。旧实现每轮重算（代价是每 450ms 读整页 `textContent`）。若某页正文早已齐全
   但 MutationObserver 一直不触发（如 canvas 绘制），首轮 `loop(true)` 仍会测量一次，3s 心跳也会兜底
   重跑 `loop(true)`（但 gate 自身按脏判定，不会重复测量）。浮窗总可由长按蓝色按钮唤回。
4. `WEBVIEWS` 弱集合 + 上限 8：超限时移除的是任意一个弱引用（不是最旧的，WeakHashMap 无顺序）；
   实际同时存活 >8 个 WebView 时 `revealBars` 可能漏掉个别页面（best-effort，与旧实现上限行为等价）。
5. `bindOne` 已绑定返回 true：`bindNativeLongPress` 会因此落 `BIND_ROOTS`，若同一根 View 上后来换了
   可点层（App 动态重建底栏），将不再重新绑定——靠 Activity onResume 的新 decor 兜底。
6. `myGen==1` 才 `scheduleBind`：首导航若发生在 `about:blank`，绑定依赖 1800ms 后的重试(1 次) + onResume 兜底。

## 追加：浮窗拖动修好（用户：这个控件为什么不能自由拖动）
原因四条：
1. `#zyb-bar` 没有 `touch-action:none` → Chromium 把手势判成滚动，派 `touchcancel`，拖到一半就断；
2. 只监听 touch，且要位移 >6px 才 `preventDefault()`，第一次超阈值的 move 常已被滚动接管；
3. 没 setPointerCapture，手指移出控件就不再收到 move；
4. 边界 clamp 用固定 30px（`innerWidth-30`），不是控件实际尺寸，看起来"拖不过去"。
改法：Pointer Events + setPointerCapture（触摸/鼠标都支持，无 PointerEvent 回退 touch）、
`touch-action:none`、按下/抬起用 act 标志、clamp 按 offsetWidth/offsetHeight 夹在视口内。
自检：`.agent/snips/drag-smoke.js`（DRAG_ALL_PASS，11 项）+ `smoke.js`（SMOKE_ALL_PASS）。
