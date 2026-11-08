# fix-pageshot — C 部分：长截图与桥接

依据: `.agent/review-1.3.0.md`（P0 文字匹配 32-63 / P0 主线程 PNG 193-242 / P1 expandShot 还原 290-321 /
P1 死等 1200-900-420 322-361 / P1 位图峰值 520-531 / P1 sBusy 无看门狗 532-555 / P1 grab 白分配 556-580）

只改了两个文件：`src/io/github/sql0106/zybclean/PageShot.java`（主体）、`H5Bridge.java`（仅注释）。
未编译（硬约束），未 git add/commit，未连设备。JS 三常量已抽成 `.js` 过 `node --check`。

## TODO（全部完成）
1. [x] `sBusy` 看门狗：`sBusyAt` 时间戳 + `done()` 同步幂等 + `finally` 复位 + 60s 硬看门狗 + 陈旧锁自愈
2. [x] `expandShot()` 资源安全：`oldLpHeight` + `applyExpandedHeight/restoreExpandHeight`，六条出口全还原
3. [x] 去死等：`awaitExpandLayout()` 用 `postOnAnimation` 轮询（高度到位 + 稳定 2 帧）+ 2500ms 硬超时兜底
4. [x] PNG 编码/落盘移后台线程 `zyb-shot`（H5Bridge.saveBytes 确认可跨线程）
5. [x] 降低位图峰值：crop 后立即回收原图、`grab()` 帧按需分配 + 失败分支回收、`drawExpanded` RGB_565、JS 模式不预分配 `out`
6. [x] 删除 `PREP`/`CLEAN` 里「答案/解析…」文字分界（含 KILL 文本数组），split 恒 0；HRE/VCDN/ADRE 保留
7. [x] `cropPart` 对外行为不变（splitY<=0 整页 / q 上半 / a 从 splitY-12px）
8. [x] JS 常量抽成 `.js` → `node --check`：PREP/RESTORE/CLEAN 三个全 OK
   （产物 `.agent/snips/pageshot-{prep,restore,clean}.js`，抽取器 `.agent/snips/extract_pageshot_js.py`）

## 改了什么（逐条）

### 1. sBusy 看门狗
- 新增 `sBusyAt`（uptimeMillis）与 `BUSY_TIMEOUT_MS = 60000L`。
- `capture()`：如果 `sBusy` 且已挂超过 60s → 打日志并强制释放（陈旧锁自愈），否则照旧提示「正在生成」。
- `capture()`：建 `Job` 后立刻用主线程 Handler 排一个 60s 定时器 → `job.watchdog()`。
- `Job.watchdog()`：`finished` 为真则空转，否则 `done(false, "长图超时，已中止")`。
- `done()`：`synchronized` + `finished` 保证只生效一次（正常出口/后台编码回调/看门狗可能同时到）；
  清理体包在 `try { … } catch (Throwable) { log } finally { out=null; sBusy=false; sBusyAt=0; }`。

### 2. expandShot 资源安全（LayoutParams 一定还原）
- 新增字段 `oldLpHeight`（`Integer.MIN_VALUE` = 当前未撑开）。
- 新增 `applyExpandedHeight(targetH)`（记录旧高度后撑开，取不到 lp 直接抛 → 走 fallback）与
  `restoreExpandHeight()`（幂等：还原过一次再调就是空操作）。
- 还原点：`stitchFallback()` 第一行、`done()` 第一行、`drawExpanded()` 拿到像素后立即、
  `drawExpanded`/`expandShot` 的 catch（经 stitchFallback）。`restoreHeight(oldH)` 原样保留，只由
  `restoreExpandHeight()` 调用。
- 顺手修掉一个真 bug：原来 `expandShot` 先把 `out` recycle 掉再失败回退，`paste()` 会拿 null 画布 NPE
  （等于「回退即失败」）；现由 `ensureOut()` 按 `begin()` 算好的 outW/outH 重建，`run()` 里只剩
  「内存不足」的优雅报错。

### 3. 去掉 1200/900/420ms 死等 —— **选了「轮询布局到位」方案（非退化方案）**
- `awaitExpandLayout(targetH, then)`：`wv.postOnAnimation` 每帧检查
  `wv.getHeight() >= targetH-8 && getWidth()>0` 且高度**连续 2 帧不变**即继续；
  超过 `EXPAND_WAIT_MS=2500ms` 打印 `expand wait timeout` 后也继续（落回原量级，不死等、不无限等）。
- 双保险：同时 `main.postDelayed(go, 2700ms)`，防 WebView 已 detach 时 `postOnAnimation` 挂在 RunQueue 不回调。
- 布局到位后：切 `LAYER_TYPE_SOFTWARE` → 等 `LAYER_SETTLE_MS=200ms` 一帧栅格化 → `CLEAN`（`cleanThenDraw()`）
  → 等 `CLEAN_SETTLE_MS=200ms` → `drawExpanded()`。小页面常见 300~500ms 落笔（原来固定 2520ms）。

### 4. PNG 编码 + 写相册移出主线程
- 先读了 `H5Bridge`：`saveBytes()` 是 static，`new H5Bridge(ctx, null)` 内部 Handler 取
  `Looper.getMainLooper()`，只用 ContentResolver/File IO，**不碰 WebView、不依赖调用线程** → 可后台调用，
  所以不需要「后台压 byte[]、回主线程调桥」的折中版。已在 `saveBytes` 上加注释说明。
- `saveBitmap()`：主线程只做（可选的）crop，然后 `new Thread(..., "zyb-shot")` 里
  `compress(PNG,100)` → `bos.toByteArray()` → `H5Bridge.saveBytes(...)`，`finally` 里 recycle 位图；
  完成后 `main.post` 回主线程打 log + `done(true, "长图已保存到相册"/"保存失败")`，编码失败走
  `done(false, "图片编码失败")`。

### 5. 降低位图峰值
- JS(expand) 模式不再预分配拼接总画布（最多 48MB），回退时才 `ensureOut()`。
- `saveBitmap` 里 `part != full`：先 `cropPart`，随即 `safeRecycle(原图)`，只留一份像素；交给后台前把
  `out` 置 null（所有权转移，`done()` 不再重复回收）。
- `grab()`：帧改为**按需分配**——PixelCopy 分支只建 `shot`（不再无条件建 `frame`），
  `wv.draw` 兜底分支才建帧；`wv.draw` 抛异常的分支补上回收（原来漏了，每步泄漏一帧）。
- `drawExpanded()` 用 `RGB_565`（1080×14000：60MB → 30MB），拿到像素后 `bmp=null` 交棒，
  失败分支 `safeRecycle`；`paste()` finally 里仍然回收 shot/viewFrame。
- 删掉被 `grab()` 改写后失效且有 bug 的 4 参 `paste(frame, cssY, nativeY)` 重载（它把 viewFrame 传 null
  → 必然 NPE），现在只有一个 5 参实现，并在开头判 null。

### 6. 禁止按文字匹配（用户死线）
- `PREP`：删掉 `var KILL=[/本题精讲/,/讲解(中|视频)/,…/领取/]`、删掉「取 textContent → 正则 → upHide」
  的整段（含 `if(n2.children.length)continue;`）；删掉 `heads` 探测循环，`split` 恒 0（info() 仍输出
  第 10 段 split=0，`parse()` 不用改）。
- `CLEAN`：同样删掉 `KILL` 数组与文字尾部判定、删掉 `heads` 循环；返回值改为 `"0|"+hid`（两段格式不变）。
  `cleanThenDraw()` 只解析 hidden，不再写 `splitY`。
- **保留不动**：`HRE`（class 名）、`VCDN`（`zyb-vip.cdnjtzy.com`）、`ADRE`（class）、`VIDEO/IFRAME`、
  `upHide` 的几何上溯、`zyb-bar`、fixed/sticky 浮层。
- 后果（预期内）：`split` 恒 0 → 点「存题目/存解答」现在都存整页，并弹「没找到题目/解答分界，已存整页」。
  分段位置只能由 Java 侧传入 `splitY`。

### 7. cropPart 对外行为
- `cropPart()` 方法体**一字未改**（`splitY<=0` 存整页、`q` 上半 `[0,splitPx)`、`a` 从 `splitPx-12` 起、
  `<40px` 存整页 + toast）；只换了上面的 javadoc 措辞。

## JS 自检（产物在仓库内，禁写 /tmp）
```
python3 .agent/snips/extract_pageshot_js.py     # 输出目录 .agent/snips/，前缀 pageshot-
pageshot-prep.js    OK   (3087 字符)
pageshot-restore.js OK   ( 322 字符)
pageshot-clean.js   OK   (1339 字符)
```
抽取器按 Java 字符串/注释状态机解析（`\\s`、`\"` 等反转义正确）；`node --check` 三个全 OK。
`.agent/snips/` 里保留两套同名内容：`pageshot-*.js` 是本次规范的规范产物，
`pageshot_*.js`（下划线）是用户从旧 /tmp 目录搬回来的同一份副本 —— md5 逐一相同
（clean `03e2d111…`、prep `ce713e12…`、restore `d6e60949…`），按用户要求都不删，随 commit 留着。
另做了 Java 侧括号平衡自检：`{} 193/193`、`() 520/520`。

> 规则遵守：本轮起不再写 `/tmp` 或 `/tmp/opencode`，抽 JS / node --check / 临时产物一律在
> `.agent/snips/` 下、文件名前缀 `pageshot-`，结束不删。

## 未做 / 风险
- **未编译**（硬约束）：结论来自静态检查，真机验证仍需构建后跑。
- 未改 `MAX_PIXELS`(1.2e7) / `MAX_OUT_HEIGHT`(20000)：review 建议降到 6e6，但会改变成图分辨率
  （属于可感知的对外行为），不在本次 7 条范围内，留给主会话决定。
- `drawExpanded` 用 RGB_565 是 review P1 的建议，严格说超出「三条硬性清单」；风险是带大量照片的页面
  可能出现轻微色带。若不接受，把那一行改回 `ARGB_8888` 即可（其余逻辑不依赖色彩格式）。
- review 里「下半屏 ink 检查 + 重试一次」没做：属于 review 该节附带建议，本次只按任务清单改造等待方式。
- 已知行为变化：`split` 恒 0 → q/a 分段失效（仅存整页），这是删文字匹配的必然结果；要恢复分段需由
  Java 侧传入真实 splitY。
