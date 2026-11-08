# 作业帮（com.zuoyebang.knowledge）Xposed 模块 —— 经验总结

> 目标：去广告、去首页信息流/科普专区、去推广卡与红点、去 AI 解题与「讲解中」浮窗、开屏提速。
> 产物：`com.sql916.zybclean`（仓库 `SQL0106/zuoyebang_xposed`），真机 realme RMX5062 / Android 16 / KernelSU + LSPosed 2.2.1。
> 本文只记「踩过的坑与可复用的方法」，具体结论与版本历史见 `NOTES.md`。

---

## 一、反编译分析方法（静态）

### 1. 工具：dexsql（SQL 查 DEX），优先命令行
```sh
dexsql query -i base.apk --sql "SELECT descriptor FROM classes" --limit 100000 > analysis/classes.txt
grep -i 'holder\|adapter' analysis/classes.txt          # 57k 类名先落盘，再用 grep，别反复查库
dexsql query -i base.apk --sql "SELECT name, descriptor FROM methods WHERE class_descriptor='Lcom/...;'"
dexsql query -i base.apk --sql "SELECT callee_class, callee_name FROM method_calls WHERE method_class='Lcom/...;' AND method_name='X'"
dexsql query -i base.apk --sql "SELECT DISTINCT method_class, method_name FROM method_calls WHERE callee_class LIKE '%Foo;'"   # 谁调用了 Foo
dexsql decode --class 'Lcom/...;'                          # 单类 smali
```
- 大列表**永远先 dump 到 `analysis/*.raw` 再本地 grep**，不要经 MCP 灌进上下文。
- MCP 版 `dexsql` 也可以，但 CLI 更省 token。表结构：`classes/methods/method_calls/method_strings/fields/strings`。
- `method_calls` 的列是扁平的（`callee_class`/`callee_name`），**没有** `caller_method_id`、`callee_class_descriptor` 这种名字，别猜。

### 2. 混淆严重不等于全瞎
- 混淆的是**包名和业务类**（`h`/`g`/`k`/`f`/`ms` 等 1560+ 类），但 **Manifest 里的组件名、第三方 SDK 名（穿山甲 `com.bytedance.sdk.openadsdk` 3553 类、优量汇 `com.qq.e`、快手 `com.kwad`、百度 `com.baidu.mobads`）、以及「作业帮自研广告聚合层 `com.fastad`」全都是真名**。
- 第一步永远是从 `Manifest` 拿 Activity/Service/Provider 真名，再顺着 `method_calls` 找调用方。
- 「按名字猜功能」有效但不保证：`HomePageFloatView` 听着像首页浮窗，实际是**首页运营浮球**，不是用户抱怨的「讲解中」浮卡。

### 3. 广告的统一命中点
`com.fastad` 下 34 个适配器（`Api*Adapter` / `BD*` / `Csj*` / `Ks*` / `Ylh*`）**全部有 `doLoadAD()` 与 `doShowAD()` 无参方法** → 一处 hook 覆盖全站广告。
- 只拦 `doLoadAD` 会让某些「等回调」流程挂住（尤其开屏），**先只拦 `doShowAD` 更安全**，确认无异常再考虑前置拦截。
- 接口类不能 hook：`XposedBridge.hookAllMethods` 对 abstract 方法会抛 `IllegalArgumentException: Cannot hook abstract methods`，要先把接口从名单里剔掉。

---

## 二、运行时才是真相（动态定位）

### 1. `XposedBridge.log` 不进 logcat
LSPosed 把它写到 **`/data/adb/lspd/log/verbose_<开机时间>.log`**（另有镜像 `modules_<时间>.log`），行格式：
```
[时间 pid:tid I/LSPosedFramework] (com.zuoyebang.knowledge)[com.sql916.zybclean,XposedBridge,...] ZYBCLEAN: xxx
```
→ 验证一律 `rsh realme "grep ZYBCLEAN /data/adb/lspd/log/verbose_*.log | tail -50"`，**不要靠截图**。

### 2. 自研「快照」（GKD 思路的简化版）
GKD 是外部无障碍服务，Xposed 模块内没有它对控件树的访问入口，但可以在 **App 进程内**自己做：
- 触发用**私有目录里的文件**（root 可写、App 可读，不打扰用户、不用申请权限）：
  `touch /data/data/com.zuoyebang.knowledge/files/.zyb_snap` → 输出 `.zyb_snap.txt`
- 实现要点：
  - hook `Activity#onResume` 记下最近 resume 的 Activity 列表，**dump 时优先选 `hasWindowFocus()` 为真的那个**——否则永远抓到壳页（`InitActivity`）。
  - 递归 `getChildCount/getChildAt`，每行输出 `类名 id=<资源entry名> vis=V/IV/G box=l,t-r,b text=... desc=...`；全部走 `XposedHelpers.callMethod` 反射，模块里**不用 import android.\***。
  - 另外 hook `WindowManagerGlobal#addView` 记录浮层——`ViewGroup#addView` 抓不到 `WindowManager` 直接挂的窗口。
- 这个机制是本项目最有价值的产出：**没有它，UI 定位全靠猜**。

### 3. 血泪结论：搜题结果页是 H5
`snap4` 显示结果页的 `SearchResultPager` 里是 `NestedHybridWebView → WebViewDelegate`，`.zyb_snap.txt` 里也没有任何 `SNAP overlay#` 行。
→ 「AI 解题」tab、「讲解中」视频浮卡**都是网页元素**，dex 里当然找不到对应类（这也是前面几轮全部落空的原因）。
→ 对策：hook `android.webkit.WebView#loadUrl` / `#loadDataWithBaseURL`，在 afterHook 里 `postDelayed` 若干次 `evaluateJavascript(js, null)`，用 CSS/JS 隐藏目标节点。

### 4. 资源 id 定位基本不可用
- 作业帮把资源名大面积混淆：13,510 个 id 里只有 **128 个可读名字**，其余全叫 `zyb`（一个名字复用）。
- `aapt dump resources` 完整但不解决问题；GKD 社区规则里的 `id/cancel_button` 在当前版本已不存在（社区规则会过时）。
→ 只能按 **控件类名 + 兄弟顺序/坐标 + 文字内容** 定位。文字匹配（`JUNK_TEXTS`）是删卡片最稳的一招。

---

## 三、构建链（不用 Gradle）

### 1. 手工链路
`javac --release 8`（Xposed API 用自写 stub）→ **D8**（`cmdline-tools/lib/r8.jar`，`--min-api 24 --release --lib android.jar`）→ `aapt package -M AndroidManifest.xml -A assets -I android.jar -F base.apk` → `zip` 塞 `classes.dex` → `zipalign -f -p 4` → `apksigner sign`。
- 模块身份：`AndroidManifest` 里三段 meta-data（`xposedmodule=true`、`xposeddescription`、`xposedminversion=53`）+ `assets/xposed_init` 一行入口类名。**没有 Activity 也能装**。

### 2. 坑（每条都真机踩过）
| 坑 | 现象 | 解法 |
|---|---|---|
| 官方 build-tools 是 x86_64 | `aapt2` 报「可执行文件格式错误」，`d8.jar` 被截断 | aarch64 上改用 Debian 的 `aapt`/`zipalign`/`apksigner` + `r8.jar` 跑 D8 |
| `android.jar` 被截断成 8 MiB | aapt 报 `Entry offset ... points outside the Type's boundaries`、`No resource identifier found for attribute 'versionCode'` | 下载后 `unzip -t` 验完整性、比对字节数（应 ≈26 MB）；`build.sh` 里对每个候选 jar 跑一次**真实冒烟链接**再采用 |
| Debian `aapt` 的 `-M` 只认文件名 `AndroidManifest.xml` | `ERROR: No AndroidManifest.xml file found.` | 冒烟清单也必须命名为 `AndroidManifest.xml`（换个名字就报错） |
| `sdkmanager` 装的 platform-34 与本机 aapt 不兼容 | CI 里同样报 `Entry offset` 系列错 | 不用 sdkmanager，统一用官方 `platform-34-ext7_r02.zip`（**没有**纯 `platform-34` 这个包名） |
| 手写 keystore 进仓库 | 换机器签名不一致 → 无法覆盖安装 | keystore 连口令一起入库（本模块无敏感信息），CI 与本机同签名 |

### 3. Xposed API 在 LSPosed 下被混淆
运行时 `de.robv.android.xposed.XposedHelpers` 实际被重命名为 `f.oPozz.wTPCWjM.tAoApZ.XposedHelpers`，且**不存在** `findAndHookMethod(String, ClassLoader, String, Object...)` 这个重载。
→ 只用 `XposedHelpers.findClass` + `XposedBridge.hookAllMethods` + `XposedHelpers.callMethod/getObjectField/getStaticIntField`，别用 `findAndHookMethod`。
→ 编译期的 Xposed API stub 自己写 6 个文件即可（`IXposedHookLoadPackage`、`XC_LoadPackage.LoadPackageParam`、`XC_MethodHook`、`XposedBridge`、`XposedHelpers`），不必依赖 jitpack（`api-82.jar` 取下来是 67 字节的错误文本）。

---

## 四、LSPosed 启用（设备上没有管理器 App）

- 配置库：`/data/adb/lspd/config/modules_config.db`，表
  `modules(module_pkg_name PK, apk_path)` / `modules_state(module_pkg_name, user_id, enabled, scope_request_blocked)` / `scope(module_pkg_name, app_pkg_name, user_id)`。
- 设备无 sqlite3 → 拉到本机用 `sqlite3` 改（`PRAGMA journal_mode=DELETE` 后 `INSERT ... ON CONFLICT`），推回后 `chown root:root`、删 `-wal`/`-shm`。
- **改配置库不需要重启手机**（用户 2026-10-30 明确）：作用域里只有普通 App 时，LSPosed 在目标 App 下次启动时读配置即生效，`am force-stop 目标包` 足够。只有作用域包含 **「系统框架」** 时才必须重启。
- **绝对不要 `kill lspd`**：一旦杀掉就无法手动拉起（`nohup` / `setsid` / 子 shell 全静默退出，`lspd` 本体是 `exec app_process ... org.lsposed.lspd.Main`）——历史上那次「必须重启」是我们自己 kill 出来的，不是 LSPosed 的要求。
- `lspctl` CLI 在本机全静默（rc=0、零输出），不可用；`org.lsposed.manager` 装了也没法自动化 UI（`uiautomator dump` 在这台设备上静默失败）——**用户明确反对装管理器，不要再装**。
- 模块 APK 更新**不需要**重启手机、不需要改库：`pm install -r` 后 `am force-stop 目标包`，下次启动即加载新代码。

---

## 五、CI（GitHub Actions）

- 缓存 SDK 组件会缓存到**损坏的 android.jar**（下载中断/缓存污染都发生过）→ 改为**缓存官方 zip**（`platform-34-ext7_r02.zip`）+ 每次解开后跑冒烟链接，坏就重下。
- 失败必须留证据：`set -o pipefail` + `tee build.log`；`if: failure()` 把 `build.log`/`build-info.txt`/`build/` 打成 `build-failure.tar.gz` 上传；`if: always()` 上传 `build-logs`。
- `if: always()` 的步骤里**不要用 `-e`** 跑可能失败的命令（`aapt version` 失败会让整步 exit 2，掩盖真正错误）。

---

## 六、协作与验证纪律（最贵的一课）

1. **不要反复把用户的 App 拉到前台截图**。用户在用手机，拉前台=打断他。验证一律读日志，或让用户自己看屏幕后描述。
2. **不主动截图**；需要视觉信息时，用「触发文件」让 App 自己 dump 控件树（静默、零打扰）。
3. 断点式推进：每改一版 → 构建 → 安装 → **读日志确认 hook 命中** → 再改下一处。不要一次改十处再验证。
4. 「断言已做/未做」之前先读文件（`NOTES.md` / `git log`），别凭印象。
5. 每次重大结论立刻写进 `NOTES.md`（用户明确要求「记得经常写md，在压缩之前写进去」）。
6. **绝不擅自重启手机**（用户 2026-10-30 指令：「不能随意重启手机，测试让用户做」）。常规启用/更新都不需要重启：勾作用域（普通 App）或 `pm install -r` 后，`am force-stop 目标包` 即可；**只有作用域含系统框架时才需重启**，且由用户决定。
7. **测试一律交给用户**：装完就停，不自己开 App、不滑页面、不读用户界面；等功能确认由用户回话后再按需读日志定位。

---

## 七、当前模块结构（一图流）

```
HookEntry（仅 com.zuoyebang.knowledge）
├─ AdBlocker         com.fastad 33 个适配器的 doLoadAD/doShowAD → 直接 return
├─ FeedKiller        首页 feed：addData/q(List) 过滤广告项 + getItemCount=0 + 隐藏 RecyclerView
├─ ActivityBlocker   Activity#onCreate 后按类名黑名单 finish（含 AI 解题页、各广告 Activity）
├─ DialogKiller      首页弹窗基类 index.dialog.u 的单参 Activity 方法全拦
├─ FloatKiller       首页运营浮球 HomePageFloatView 的所有方法空实现
├─ HomeChromeKiller  搜索框 / 铃铛 / 红点（BadgeTextView#setVisibility 强制 GONE）/ 文字命中「绑定作业帮APP账号」推广卡
├─ WebKiller         WebView 加载后注入 JS：删「AI解题/AI讲题」tab、删含「讲解」的 fixed/sticky 浮层
└─ FastStart         Handler#postDelayed：InitActivity/Splash 里 >800ms 的延时压到 250ms
```
（`Probe` / `ViewDump` / `Snap` / `WebEyes` 是定位用探针，需要时在 `HookEntry` 里取消注释再打包。）

---

## 八、尚未解决 / 待验证

- WebKiller 的 H5 选择器是**启发式**（文字 + `position:fixed|sticky`），未拿到真实 DOM 校准；可能漏删或误删。
- 真正的「讲解中」卡片 DOM 未知（需要 `WebEyes` 在结果页 dump 一次 DOM 才能写精确规则）。
- 开屏到底慢在哪：`InitActivity` 的跳转由 `L0/K0` 触发，`FastStart` 只是压延时，未验证是否真的提前进首页。
- 「科普专区」在数据层清零后是否彻底消失，未在真机确认。

## 九、libxposed API 102（当前正式方案）

**不要再用 legacy `de.robv.android.xposed`。** 新项目直接按下面这套起步：

- 依赖：`libxposed:api:102.0.0` 只有 aar，从 Maven 拉 `api-102.0.0.aar` 解出 `classes.jar` 当编译期 jar（仓库里存成 `libs/libxposed-api-102.jar`）。
- 入口：`class HookEntry extends XposedModule`，覆写 `onModuleLoaded(ModuleLoadedParam)`（拿 `XposedInterface`，可 `log(4,TAG,msg)`）与 `onPackageLoaded(PackageLoadedParam)`（`getPackageName()` / `getDefaultClassLoader()`）。
- Hook：`hook((Executable) method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(Hooker)`；`Chain` 提供 `getThisObject()` / `getArgs()`(List) / `proceed(Object[])` / `getExecutable()`。想「跳过原方法」就在 before 阶段直接 return 一个值，不再 proceed。
- **门面/兼容层最好用**：把 `XC_MethodHook` 那套语义（before/after + `setResult`）用 ~120 行 compat 类桥接到 `Hooker`，历史模块的 killer 一行不用改，只 sed import。
- **作用域自声明**（本次用户明确要求）：包内 `META-INF/xposed/scope.list`（每行一个包名）+ `module.prop` 的 `staticScope=true`；LSPosed 安装后自动带作用域，用户启用即生效。
- `META-INF/xposed/module.prop`：`minApiVersion=102`、`targetApiVersion=102`、`staticScope=true`、`exceptionMode=protective`、`autoHotReload=false`。
- **不再需要** `assets/xposed_init` 与 Manifest 里的 `xposedmodule/xposeddescription/xposedminversion` meta-data；模块名与描述改从 `res/values/strings.xml` + `android:label`/`android:description` 来。
- 打包：javac/D8 都要 `-cp`/`--lib` 指向 API jar；aapt 用 `-S res`；合包时把 `module/META-INF` 拷进 APK 根。
- 坑：`findClass` 这一类 helper 自己实现时**不要声明受检异常**，否则每个探针调用点都要 try-catch；失败抛 RuntimeException 让外层兜。

## 十、WebView 页面「解禁复制 + 一键复制/下载」（2026-11-08，1.2.0）

目标：H5 页面本来禁选中、禁长按，且想给用户一个「复制全文 / 存全部图」的按钮。三件事分开做，缺一不可：

1. **CSS + 事件**：注入 `*{user-select:text!important;-webkit-touch-callout:default!important}`；把 `document.onselectstart/oncontextmenu/oncopy/ondragstart` 置 null；对 `selectstart/contextmenu/copy/cut` 在 document **捕获阶段** `stopPropagation()`（页面的监听多在冒泡阶段）。
2. **原生拦截**：很多 App 不是靠 CSS，而是 `webView.setOnLongClickListener(...) { return true; }` 直接吃掉长按。hook `View#setOnLongClickListener`，`thisObject instanceof WebView` 时 `param.setResult(null)`（不注册）；再 hook `View#setLongClickable` 把参数强制成 `true`。只做 JS 是没用的。
3. **原生桥**（`addJavascriptInterface(obj, "zybcleanHost")`）：JS 侧的剪贴板与下载都不可靠——`navigator.clipboard` 需安全上下文且 WebView 版本敏感，`<a download>` 被 App 的 DownloadListener 抢走，跨域图片 canvas 会污染。所以：
   - 复制 → `@JavascriptInterface copyText(String)` 用 ClipboardManager 写。
   - 下载 → `saveImage(name, dataUrl)`（base64）与 `saveUrl(name, url)`（HttpURLConnection + **`CookieManager.getCookie(url)`**，带登录 Cookie 就能过防盗链），存 MediaStore `Pictures/<album>`（API29+ 用 IS_PENDING 两段式），失败回落公共目录 + `MediaScannerConnection.scanFile`，再退 App 私有目录。
   - JavaBridge 线程没有 Looper → Toast 必须 `Handler(Looper.getMainLooper()).post`。

工程要点：
- 桥只挂一次（`WeakHashMap<WebView,Boolean>`），否则每次导航叠加。
- 每次 `loadUrl/loadData/loadDataWithBaseURL` 后都要重新注入 JS（导航会清 DOM），用 `window.__zybclean` 幂等标记。
- 工具条要 `gate()`：仅当正文够长且含「答案/解析/解答/题目」才显示，否则首页也会浮一个按钮。
- 下载图片逐个来、每张间隔 ~120ms，并发会 OOM；单图设上限（本项目 40MB）。
- **JS 写在 Java 字符串里必须做语法校验**：反射导出 `WebKiller.JS` → `node --check`。本轮就是靠它抓到少一个 `}`（`Unexpected token 'catch'`）。构建脚本 `rm -rf build` 会删掉校验工具，记得重建。


## 十一、WebView 整页长截图（文本抽不出来时的正解）

**触发场景**：页面内容是公式/图表（KaTeX、MathJax、Canvas、图片），任何 `innerText`/`textContent` 方案都会丢内容，
用户要的是「所见即所得」。

做法（约 250 行，`PageShot.java`）：

1. **先隐藏浮层**：`position:fixed|sticky` 的元素在逐屏截图时会每屏重复一次 → 截图前 `display:none`，结束还原。
   只隐藏 `height < 0.6 * innerHeight` 的，避免误伤整屏遮罩。
2. **探测滚动容器**：优先 `document.scrollingElement`；若存在 `overflowY:auto|scroll`、`clientHeight >= 50% 视口`、
   `scrollHeight` 更大的子元素，则用它（H5 常见「页内滚动 div」）。统一封装 `scrollTo(y)`，返回**实际** `scrollTop`。
3. **步长 = clientHeight**，每步：滚动 → **等 400ms 左右**（WebView 渲染异步，等太短会拍出上一屏）→ `wv.draw(canvas)`。
4. **坐标换算**：`density = wv.getWidth() / window.innerWidth`；裁剪 `getBoundingClientRect()*density`，贴到 `y*density*scale`。
5. **内存**：总图像素上限（本例 1.2e7）+ 高度上限（20000px）+ 屏数上限（60），超了等比缩小；每屏临时 bitmap 用完 `recycle()`。
6. **落盘**：`Bitmap.compress(PNG)` → 复用已有的 MediaStore/MediaScanner/私有目录三级回落。
7. **线程**：JS 桥回调在 JavaBridge 线程，`WebView.draw` 必须 post 到主线程；用静态 `sBusy` 防重入。
8. **JS 也要 node --check**：把 Java 里拼的 JS 常量反射 dump 成文件再校验，能提前抓住括号不配对。


## 十二、长截图：别滚动拼接，撑开一次画完

**踩过的三条死路**：
1. `WebView.draw(Canvas)` 直接拍 → 硬件加速下**全白**（内容在 RenderThread 层）；
2. `PixelCopy` 拍窗口能拍到内容，但**逐屏滚动拼接**会出接缝白线、重复段落、末屏截断（Chromium 异步栅格化 + 页面自身平滑滚动）；
3. `getBoundingClientRect()` 是**整页布局盒**（可能是负坐标、高度等于整页），不能当可视裁剪区。

**正解（expand）**：
- 记下 `LayoutParams.height` → 改成 `round(页面总高 * density)`（封顶 14000px）→ `requestLayout`；
- 等 1.2s 布局 → `setLayerType(LAYER_TYPE_SOFTWARE, null)` 再等 0.9s（关键：软件渲染才能真正 draw 出内容）→ `wv.draw(canvas)` 一次画完；
- 抽样统计非白像素判断是否空白（`ink<300` 视为失败）→ 失败回退拼接；
- 无论成败都要**恢复 layerType 与 LayoutParams**，否则页面布局被永久改坏。

**注意**：`step` 这类变量不要一鱼两吃（既当计数器又当步长），否则 `if (step >= steps)` 一进门就结束，保存一张空白图。

**加载卡顿**通常不是 hook 本身，而是注入的 DOM 扫描：
- `MutationObserver` 必须 debounce（≥400ms），否则首屏渲染期跑几千次全量遍历；
- 扫描限元素数（2500）、跳过子节点多的容器、别用 `innerText`（强制同步布局）；
- 定时扫描 3s 起，`document.hidden` 时直接 return；
- 截图/重活期间用全局标记让扫描让路。
