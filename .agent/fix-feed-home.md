# D 部分修复：信息流/首页/广告拦截/反射兼容层/装配

## 我拥有的文件
- src/io/github/sql0106/zybclean/FeedKiller.java
- src/io/github/sql0106/zybclean/HomeChromeKiller.java
- src/io/github/sql0106/zybclean/AdBlocker.java
- src/io/github/sql0106/zybclean/FloatKiller.java
- src/io/github/sql0106/zybclean/HookEntry.java
- src/io/github/sql0106/zybclean/compat/XposedHelpers.java

## TODO（全部完成）
- [x] 1. `compat/XposedHelpers.callMethod` 加缓存（无锁读 + 未命中才 synchronized 写）；findClass 同样加缓存；找不到方法时异常行为保持一致
- [x] 2. `HomeChromeKiller.clean()` 三趟合一为单趟遍历；"每生命周期 5 次" 改为首次 + 1 次兜底（WeakHashMap 计数去重）
- [x] 3. `FeedKiller` 改就地修改原 List（removeIf / clear），killForever 同样处理
- [x] 4. 删除 HomeChromeKiller / FloatKiller / AdBlocker 中按文字匹配隐藏控件的逻辑（实际只有 HomeChromeKiller 有）
- [x] 5. `HookEntry.java`：确认探针段 `if (BuildFlags.PROBES)`；所有 install 包 try/catch + 日志（原样已符合，未改）

## 改了什么

### 1) compat/XposedHelpers.java（全量重写，P0-3）
- 新增 `HashMap<String, Method> METHOD_CACHE`：
  - key = `类名#方法名/参数运行时类型,...`（`o==null` 记为 `?`）→ 同参数个数但类型不同的重载也能区分。
  - `callMethod` 读路径**无锁**：`METHOD_CACHE.get(key)`，只有未命中才进 `findCachedMethod()` 的 `synchronized (METHOD_CACHE)`。
  - 命中后校验 `m.getDeclaringClass().isAssignableFrom(cls)`（防同名类不同 ClassLoader 串味），不匹配则重解析。
  - `setAccessible(true)` 只在解析时做一次（原来每次 invoke 前都做）。
  - 找不到方法：`findMethod` 照旧抛 `RuntimeException("NoSuchMethod: ...")`，不静默返回 null；命中/未命中两条路径的 `InvocationTargetException` 仍 `rethrow(cause)`，其他异常仍包装成 `RuntimeException("callMethod ...")` ——语义与改前一致。
- 新增 `HashMap<String, Field> FIELD_CACHE`（`getIntField` / `getObjectField` / `getStaticIntField` 走 `findCachedField`，同样的无锁读+同步写）；`NoSuchField` 仍抛 RuntimeException，外层 catch 后再包装，错误文本与改前一致。
- 新增 `CLASS_CACHE`（`findClass`）：`ClassEntry{loader, type}`，**只有 `cached.loader == cl` 才复用**（不同 ClassLoader 同名类必须重新解析）；未命中才 `Class.forName`，`ClassNotFoundException` 仍转 `RuntimeException("class not found: ...")`。

### 2) HomeChromeKiller.java（重写，P0-4）
- 删除全部文字匹配：`JUNK_TEXTS`、`hideJunkCards`、`isJunk`、`findCard`、`collectText`、`textOf`（以及 `clean()` 里对 `hideJunkCards` 的调用）。剩下的判据只有「控件类名（XMarqueeView / StateImageView / BadgeTextView）、父容器关系、横向几何（最左）」，无任何 `getText()`。
- 三趟合一：`collect` + `collectByName` + `hideJunkCards` → 单趟 `walk(View, marquees, icons, badges, depth)` 收集到三个 List，遍历结束后统一 `hide()`。
- 遍历改强转（`View` / `ViewGroup.getChildCount/getChildAt`），不再每个节点走 `XposedHelpers.callMethod`。
- `DELAYS` 由 5 个（600/1500/3000/6000/12000）改成 `{600, 2500}`；新增 `WeakHashMap<View,int[]> PASSES` + `MAX_PASSES=2`：`markPass()` 占额度、`hasPassLeft()` 在 `schedule()` 里提前短路（清够 2 趟后不再排定时器）→ 每个 decor 最多 2 趟全树（原最多 30 趟）。
- 保留原语义：搜索框 = 跑马灯的父容器 GONE；铃铛 = 同父里最左 StateImageView；BadgeTextView 全 GONE（另有 `setVisibility` hook 兜底）。

### 3) FeedKiller.java（P1-7）
- `FeedFilterHook.beforeHookedMethod`：不再 `param.args[0] = 新 List`。改为 `in.removeIf(FeedKiller::isAdItem)` **就地过滤，List 引用不变**（分页 loadMore 的 addAll 仍写进同一个 List）。
- 仅当原 List 不可变（removeIf 抛异常）才退化：从原 List 逐个取、保留未命中的元素构造新 List 写回 `args[0]`（不是「只放新数据」），并打日志说明。
- 删除 `filter(List)` 方法（返回新 List 的旧实现），其调用点同步改掉。
- `killForever` 的 `takesList` 分支：`param.args[0] = new ArrayList<>()` → `((List<?>) param.args[0]).clear()`（保持实例身份）；不可变时仍退回新 ArrayList。
- `stripFieldList` / `probe` / `isAdItem` / `initAdTypes` 未改（`stripFieldList` 本来就地 removeIf）。

### 4) AdBlocker.java / FloatKiller.java
- 逐行核对：AdBlocker 只按 `com.fastad.*` 类名 + `doLoadAD/doShowAD` 方法名；FloatKiller 只按类 + 方法名集合，**两者都没有 TextView 文字匹配**，无需删除；未做无意义 diff。

### 5) HookEntry.java（未改，仅核对）
- 探针段仍是 `if (BuildFlags.PROBES) { Probe / ViewDump / Snap / WebEyes ... }`，BuildFlags 本身未动。
- 9 个 install（AdBlocker / FeedKiller / ActivityBlocker / DialogKiller / FloatKiller / HomeChromeKiller / WebKiller / AnswerPageKiller / FastStart）+ 4 个探针 install 全部在独立 `try/catch (Throwable)` 里 `log(...)`。

## 未做什么
- 未本地编译（遵守硬约束），改动用「括号配平 + 悬空引用 grep」自检。
- AdBlocker 每次 `skip` 都打日志（review P2）未处理：不在本次 5 条范围内，避免扩大 diff。
- FeedKiller `probeBudget` 常驻探针未改成 `BuildFlags.PROBES` 门控（review P2，非本次必做）。
- FeedKiller 的 `notifyDataSetChanged → stripFieldList` hook 保留未删（review P1 建议删，但不在本次 5 条内；现在它与就地过滤是同一 List，属冗余扫描，不会丢数据）。
- 未加 `HookEntry.onPackageLoaded` 的重复-install 防抖 / 子进程过滤（review P2，任务明确「只做两件事」）。
- 未改 manifest / 版本号 / README / 文档；未 git add/commit/push；未连设备。

## 风险
1. `XposedHelpers` 缓存 key 用「类名 + 参数运行时类型」：若同一个 `callMethod` 调用点在某些批次传 `null`、某些传实例（如 `postDelayed(..., null)` vs `(Runnable)`），会产生两个 key 各解析一次 —— 只是多一次慢路径，结果一致。`setAccessible` 失败时不缓存失败状态，靠 `isAssignableFrom` 校验保证不会用错类的方法。
2. 缓存是无锁读普通 `HashMap`：仅在**写（首次解析）**时可能与读并发。最坏结果是读到 null/stale → 走同步块重解析，不会返回错误方法（Android 的 HashMap 不是 JDK7 的链表头插实现，不会死循环）。写入次数=不同 (类,方法,参数类型) 组合数，预热后基本为 0。
3. `MAX_PASSES=2`：若首页控件在 2.5s 之后才 inflate（极慢设备/冷启动），第 3 趟不再执行 → 搜索框/铃铛可能漏藏一次（回首页/重进 Activity 会重新计算）。这是用「最多 30 趟」换来的取舍。
4. `magic number`：`HomeChromeKiller` 现在直接 `View.setVisibility(GONE)`（不再反射），若目标控件覆写了 `setVisibility` 且做额外处理，行为与反射版一致（同一方法）。
5. `killForever` 就地 `clear()` 会清掉**调用方**持有的那个 List（原来是调用方保留数据、adapter 拿空表）。这正是 review 要的语义（彻底清空该 adapter 数据源），但若页面后续复用该 List，会重新 add 进去（`getItemCount()` 仍被 hook 成 0，界面不会显示）。
