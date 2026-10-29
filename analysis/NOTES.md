# 作业帮去广告 Xposed 模块 —— 工作笔记

> 每次压缩上下文前必须更新本文件。所有结论以本文件为准，不依赖记忆。

## 0. 任务

用户原话：「com.zuoyebang.knowledge，目标去广告，去除首页信息流，还有你觉得是垃圾的内容，做成Xposed，机器realme，用rsh链接。」
另一条要求：「记得经常写md，在压缩之前写进去。」

1. LSPosed/Xposed 模块（不重打包 APK），作用于 `com.zuoyebang.knowledge`。
2. 去广告（开屏/信息流原生/banner/插屏/激励视频/浮层）。
3. 去首页信息流。
4. 去其他「垃圾」内容（自行判断，大改前确认）。
5. 部署机 = realme RMX5062。

## 1. 目录与产物

| 路径 | 说明 |
|---|---|
| `/home/SQL916/zuoyebang_xposed/` | 工作目录（**不是 git repo**） |
| `./base.apk` | 作业帮原版 APK，56,485,322 B（v14.34.0 / versionCode 1738） |
| `./analysis/classes.txt` | 57,606 个类 descriptor（一行一个，`Lxxx;`） |
| `./analysis/fastad_methods.txt` | com.fastad + com.homework.fastad 全部方法（5398 行） |
| `./analysis/homepage_classes.raw` / `.txt` | newhomepage/index/feed 类 + 父类（1030 行，格式 `L类; -> L父类;`） |
| `./analysis/imp_methods.txt` | com.zuoyebang.imp (211 类) 方法表 |
| `./analysis/doload.raw` | doLoadAD/doShowAD 原始表输出 |
| `./libs/stubs/de/robv/android/xposed/*.java` | **自写 Xposed API 编译期 stub**（6 个文件，已写好：IXposedMod / IXposedHookLoadPackage / callbacks.XC_LoadPackage / XC_MethodHook(+MethodHookParam+Unhook) / XposedBridge / XposedHelpers） |
| `./analysis/NOTES.md` | 本文件 |

## 2. 本机环境（OnePlus 8, Debian 13 aarch64）

- java 25.0.5；**无 jadx/apktool/gradle/Android SDK**。`/home` 14G，剩 ~3G。
- Android SDK：`~/android-sdk/cmdline-tools.zip` 从 `https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip` 下载（2026-10-30 00:00 时 80MB，预计总量 ~130MB，速度约 15-20MB/min）。
- **jitpack 取 `api-82.jar` 失败**（返回 67 字节错误文本）→ 改用自写 stub（见上），编译只需 javac；**不需要 android.jar**（模块全部用字符串类名 + XposedHelpers 反射，不 import android.*）。
- 构建链路：`javac -cp libs/stubs/classes (或直接编译 stub 源码)` → `d8 --output out classes/` → `aapt2 link` 生成 manifest+resources → `zipalign` → `apksigner`。仍需 build-tools 的 **d8 / aapt2 / apksigner / zipalign**（下载完成后 `sdkmanager --install "build-tools;34.0.0"`）。

## 3. 设备 realme（RMX5062）

- `rsh realme '<cmd>'`；`~/.ssh/config`: HostName 192.168.1.100，User root，Port 8022，IdentityFile `~/.ssh/Keys/id_ed25519`。
- `Linux localhost 6.1.157-android14-...`，Android 16/SDK 36，KernelSU，`/data` 剩 83G。
- 应用：`/data/app/~~5h_0UpGKRBL1XR8IwSTeGg==/com.zuoyebang.knowledge-Mzauk9M1_b2CVfO9Xc1ZHQ==/base.apk`（单 base.apk）。
- **LSPosed v2.2.1 (7912)** 已装启用（`/data/adb/modules/zygisk_lsposed`，Zygisk 模式）。
- scp 56MB ≈ 2.1s。

## 4. APK 基本信息

- `com.zuoyebang.knowledge`，14.34.0 / 1738，minSdk 21 / targetSdk 30 / compileSdk 32
- application `com.baidu.homework.base.ZybApplicationLike`，largeHeap/persistent/usesCleartextTraffic=true
- 组件：activity 402 / alias 1 / provider 25 / receiver 16 / service 34；7 dex / 57,606 类

## 5. 关键类结构

### 5.1 首页（新首页 `com.baidu.homework.activity.newhomepage`）
- `d_home/DHomepageFragment` = 首页 Fragment；`d_home/fragment/DHPListFragment` = 信息流列表 Fragment
- **`d_home/adapter/DFeedListAdapter`**（→ `RecyclerView$Adapter`）= 首页信息流适配器
  - 字段：`a:Context  b:ask_feed.listitem.a  c:DHPHomeTabHelper$HomeTabBean  d:List  e:AtomicInteger  f:SparseArray  g:SparseIntArray  h:int  i:adapter.g`
  - 方法：`addData(List)`（17=public final）、`q(List)I`（18=public final）、`getItemCount()I`、`getItemViewType(I)I`、`onCreateViewHolder(ViewGroup,I)`、`onBindViewHolder(ViewHolder,I)`、`h/i/j/v(...HPListItem)`、`m(ADNewBaseHolder,I)`、`n(AHPBaseHolder,I)`、`o(ViewGroup,I)AHPBaseHolder`、`p(AHPBaseHolder)`
- 其他 adapter：`d_home/adapter/DFeedFollowerAdapter`、`e_home/adapter/EHPFeedsListAdapter`、`d_home/adapter/DHeaderImpAdapter`、`DHeaderRecyclerViewAdapter`、`DFunctionAreasGridAdapter`、`knowledge/adapter/KnowledgeListAdapter`、`HomeBannerAdapter`（homepage 的 banner 位）
- `a_home` = 首页列表项/广告位容器（只有子包 holder/adapter/bean/component/util/widget）：
  - `a_home/bean/HPListItem` **信息流数据模型**，常量 `TYPE_AD / TYPE_FEED / TYPE_LINKED_AD / TYPE_VIP_RECOMMEND`（int），字段 `itemType:int, adType:int, adItemWrappers:List, listItem:a_home.bean.b, isExposed:Z`，构造 `<init>(b) / (b,List) / (b,List,I)`
  - `a_home/holder/` 62 类：基类 `AHPBaseHolder`(ViewHolder)→`AHPBaseNlogHolder`/`ADNewBaseHolder`/`DefaultHolder`/`HPDefaultFullHolder`/`HPHiveViewHolder`；广告子类 `ADNativeHolder10690/12226/12227/12661/12680/13342`、`ADNativeNewBaseHolder`、`ADInnerNativeBaseHolder`、`ADCardComplexPageHolder`、`ADNewGroupBaseHolder`、`ADNewGroupSinglePagerHolder`、`ADNewCourseLargeImg3`；d_home/holder 下 `HPImageHolder/HPVideoHolder/HPHotTopicHolder/HPPublishHolder/HPFeedbackHolder`
  - `a_home/adapter/ADOnePlusNPageAdapter`（PagerAdapter）
- `e_home/EHomepageFragment` + `EHPListFragment`（另一个 tab 的 feed）
- `com.baidu.homework.activity.index.IndexActivity` = 老首页
- `d_home/publish/*` = UGC 发布（115 类），`d_home/helper`(47)/`persenter`(42)/`fragment`(30)
- `knowledge/*` = 知识库（viewmodel 77 / data 37 / holder 25 / adapter 7）

### 5.2 作业帮广告聚合层 `com.fastad`（944 类）+ `com.homework.fastad`
**全部 34 个适配器都有 `doLoadAD()V` 与 `doShowAD()V`（无参）——最佳统一 hook 点：**

```
com.fastad.api.ApiBannerAdapter / ApiFlowExpressAdapter / ApiInterstitialAdapter / ApiRewardVideoAdapter / ApiSplashAdapter
com.fastad.baidu.BDBannerAdapter / BDFlowExpressAdapter / BDHalfFlowExpressAdapter / BDHalfSplashAdapter / BDInterstitialAdapter / BDRewardVideoAdapter / BDSplashAdapter
com.fastad.csj.CsjBannerAdapter / CsjFlowExpressAdapter / CsjHalfFlowExpressAdapter / CsjHalfRewardVideoAdapter / CsjHalfSplashAdapter / CsjInterstitialAdapter / CsjRewardVideoAdapter / CsjSplashAdapter
com.fastad.ks.KsFlowExpressAdapter / KsHalfFlowExpressAdapter / KsHalfSplashAdapter / KsInterstitialAdapter / KsRewardVideoAdapter / KsSplashAdapter
com.fastad.ylh.YlhBannerAdapter / YlhFlowExpressAdapter / YlhHalfFlowExpressAdapter / YlhHalfSplashAdapter / YlhInterstitialAdapter / YlhRewardVideoAdapter / YlhSplashAdapter
h.m.c.f.m
```
另外每个 api 适配器有 `doDestroy()V`、`getM*Ad()`、`setM*Ad(...)`；`ApiFlowExpressAdapter.formatClose()V`。
- 模板：`com.fastad.api.express.FlowExpressTemplateV1..V5`、`api.interstitial.InterstitialTemplateV1..V3`、`api.banner.BannerAd`、`api.interstitial.InterstitialAd`
- 激励视频 Activity：`com.fastad.api.reward.RewardAd1Activity` / `RewardAd2Activity` / `RewardAd3Activity`、`com.fastad.csj.half.reward.CsjRewardActivity`
- 流程：`com.homework.fastad.flow.b`（`loadAd(String)` / `show(Activity)` / `j(FrameLayout)` / `destroy()`）、`com.homework.fastad.reward.h`、`com.homework.fastad.common.web.ApiWebActivity` / `ApiAgreementActivity`、`com.homework.fastad.reward.RewardDialogActivity`、`FastAdDebugActivity`

### 5.3 内置广告 SDK（类名完整）
穿山甲 `com.bytedance.sdk.openadsdk` 3553 / 快手 `com.kwad` 5556 / 百度 `com.baidu.mobads` 1759 / 穿山甲新 `com.bytedance.android.dy.sdk.pangle` 322 / 优量汇 `com.qq.e` 169；`com.ss.android.socialbase.downloadlib`、`com.byted.live.lite`。权限 `com.zuoyebang.knowledge.openadsdk.permission.TT_PANGOLIN`。
已知 Activity：`TTNativePageActivity`、`TTRewardVideoActivity`、`TTFullScreenVideoActivity`、`com.qq.e.ads.ADActivity`、`stub.activity.Stub_Standard_Activity`。

### 5.4 开屏
- `com.zuoyebang.imp.splash.*`（146 类）：`InnerSplashProcessor`(`closeSplash()V`)/`InnerSplashProcessor_Impl`(`closeSplash`/`onSplashClose(Z)`)、`AdxModuleHelper`/`_Impl`、`AdSplashRunnable`、`AdxCacheUtils`、`AdFilterPreference`（枚举，只有 `FILTER_ADX_VERSION`）、`nativ.AdItem`/`ADXAdItem`
- `com.baidu.homework.activity.init.SplashContainer` + `SplashView`（启动页 View，不是广告位本身）
- `com.baidu.homework.activity.init` 相邻包；`com.baidu.homework.ads.advertise.AdResumeSplashActivity`（回前台插屏/开屏）

### 5.5 其他包
`com.zuoyebang.imp`(211 类，广告投放/曝光统计，`AdPreference` 只有 `PHONE_WIFI_MAC`)、`com.baidu.homework.ads`(46 类，`advertise/ZybAdLoader`：`<init>(Activity,Lifecycle)`、`a()..i(String,Z,String)`、`e()Z`、`h(Z)V`)、`com.zuoyebang.page.activity.BaseCacheHybridActivity`、`com.zuoyebang.hivekit.core.HiveViewActivity`、`com.qiyukf.*`（客服）、`com.zhihu.matisse.*`、`com.alipay.sdk.*`、`com.tencent.tauth.*`、`com.zuoyebang.design.test.*`（Test*Activity）、`com.baidu.homework.debug.*`、`activity.shopmall.coupon.*`、`activity.fudao.purchase.PayParentsActivity`、`activity.user.StudyCoin*`。
无 sigmob/mintegral/unity/anythink。
Robust 未混淆 token 印证：`com.meituan.robust.ChangeQuickRedirect`（热修框架在，类字段含 `changeQuickRedirect`）。

## 6. dexsql 用法

- CLI `/home/SQL916/.local/bin/dexsql`：`query -i base.apk -s "SQL"`（重复 -s、`--limit`、`--timing`、`-t`）、`tables -i`、`decode -i --class 'Lx;' | --method 'Lx;->y()V'`、`--interactive`、`--cache NAME=SQL`。**输出是 ASCII 表格**，用 `grep -oE 'L[^ |]*'` 或 `awk -F'|'` 抽列；大结果先落 `analysis/*.raw` 再 grep。
- 表：`classes(descriptor, superclass_descriptor, access_flags, source_file…)`、`methods(class_descriptor, name, descriptor, param_descriptors, access_flags, opcode_count)`、`method_calls`、`method_strings`、`strings`、`fields(class_descriptor, name, descriptor)`、`class_methods`、`types`、`dex_files`、`map_items`、`id_items`。**没有 manifest 专用表**（Manifest 走 MCP `dexsql.get_manifest` / `get_manifest_components`）。
- access_flags 值：1=public、17=public final、18=public final+synchronized?、4161=public+??（照抄不影响）。
- MCP 版：`dexsql.open_dataset{inputs}`→dataset_id、`dexsql.query{dataset_id,sql:[...]}`、`get_manifest`、`get_manifest_components`、`decode`。Code Mode 里写**纯 JS**（不能带 TS 类型注解）。

## 7. 模块设计（v1，已定稿方向）

包名 `com.sql916.zybclean`，入口类 `com.sql916.zybclean.HookEntry implements IXposedHookLoadPackage`，`handleLoadPackage` 只在 `com.zuoyebang.knowledge` 生效（log 进程名）。

**A. AdBlocker**：对上面 34 个适配器类 `hookAllMethods(c,"doLoadAD")` 与 `"doShowAD"` → before `param.setResult(null)` 跳过原方法。类不存在则跳过并 log。
> 风险：跳过 load 可能导致等待回调的流程挂起（尤其开屏）。若发现卡启动或卡加载，改为「放行 doLoadAD、只拦 doShowAD」，或其他回调补偿。

**B. FeedKiller**：hook `androidx.recyclerview.widget.RecyclerView#setAdapter`（afterHook）：adapter 类名 ∈ {DFeedListAdapter, EHPFeedsListAdapter, DFeedFollowerAdapter} → 对 RecyclerView `setVisibility(8)`（反射 callMethod）。另打算 hook `DFeedListAdapter.addData(List)` 过滤 `HPListItem.itemType == TYPE_AD/TYPE_LINKED_AD/TYPE_VIP_RECOMMEND`（常量运行时用 `XposedHelpers.getStaticIntField` 读，别硬编码）。

**C. ActivityKiller**：hook `android.app.Activity#onCreate`（afterHook）→ 类名在黑名单则 `callMethod(this,"finish")`：`AdResumeSplashActivity`、`com.fastad.api.reward.RewardAd1/2/3Activity`、`com.fastad.csj.half.reward.CsjRewardActivity`、TT 系列广告 Activity、`com.qq.e.ads.ADActivity` 等。

**D. 待定（垃圾内容）**：商城优惠券、学币充值、家长付费、Test/Debug 页面——先不动，v1 跑通后再问用户。

### 打包细节
- `AndroidManifest.xml`：package `com.sql916.zybclean`，minSdk 24，无 Activity；`<meta-data android:name="xposedmodule" android:value="true"/>`、`xposeddescription`、`xposedminversion=53`（xposedscope 需 string-array 资源，暂不用，LSPosed 里手动勾 `com.zuoyebang.knowledge`）。
- `assets/xposed_init` 内容一行：`com.sql916.zybclean.HookEntry`。
- 编译：`javac -d out -cp <stubs> src/**/*.java`；stub 源码单独编译到 `libs/stubs/classes`，**不参与 d8**（避免与 LSPosed 自带 API 冲突）；`d8 --min-api 24 --output dexout out/**/*.class`。
- 安装：`adb`? — 用 `rsh realme 'pm install -r /data/local/tmp/x.apk'`（先 scp 到 /data/local/tmp）。

## 8. 待办清单

- [x] 拉 APK、Manifest/类结构侦察
- [x] dump classes/fastad/homepage/imp/doload
- [x] 定位首页信息流：`DFeedListAdapter` + `HPListItem.itemType` 类型过滤
- [x] 定位全站广告统一入口：34 个 `doLoadAD/doShowAD`
- [x] 写 Xposed API stub（6 文件，编译期用）
- [ ] 完成 cmdline-tools 安装 → `sdkmanager --install "build-tools;34.0.0"`（+ 也许 platforms;android-34）
- [ ] 写模块源码 `src/com/sql916/zybclean/*.java`
- [ ] 打包 + `rsh realme` 安装 + LSPosed 勾作用域 + force-stop 验证
- [ ] logcat 观察挂起/crash，迭代
- [ ] 交付：APK 路径、启用步骤、已知副作用

## 9. 验证命令备忘

```sh
rsh realme 'pm list packages | grep zuoyebang'
rsh realme 'am force-stop com.zuoyebang.knowledge'
rsh realme 'logcat -d -s XposedBridge:* | tail -100'   # 若 tag 不对，用 LSPosed 日志
rsh realme 'am start -n com.zuoyebang.knowledge/com.baidu.homework.activity.init.SplashActivity'  # 名称待确认
```

---

## 10. 2026-10-30 进度快照（第二轮）

### 已完成的代码（`~/zuoyebang_xposed/src/com/sql916/zybclean/`）
- `HookEntry.java`：`handleLoadPackage` 只在 `com.zuoyebang.knowledge` 生效，依次装 AdBlocker / FeedKiller / ActivityBlocker / DialogKiller，各自 try-catch；`log()` → `XposedBridge.log("ZYBCLEAN: ...")`。
- `AdBlocker.java`：34 个适配器全名数组（`com.fastad.api.Api*` 5 + `com.fastad.baidu.BD*` 7 + `com.fastad.csj.Csj*` 8 + `com.fastad.ks.Ks*` 6 + `com.fastad.ylh.Ylh*` 7 + `h.m.c.f.m`），对 `doLoadAD`/`doShowAD` 做 `XposedBridge.hookAllMethods` → before `setResult(null)`，每次调用打 log；统计 `installed on N/34`。
- `FeedKiller.java`：
  - `FEED_ADAPTERS` = DFeedListAdapter / EHPFeedsListAdapter / DFeedFollowerAdapter；对其中签名为 `(List)` 的 `addData`、`q` 方法 beforeHook，用 `filter()` 生成新 ArrayList（剔除 `itemType ∈ {TYPE_AD, TYPE_LINKED_AD, TYPE_VIP_RECOMMEND}`）替换 `param.args[0]`；afterHook 再 `stripFieldList()` 清成员 `d`（`List.removeIf`）。
  - ad type 常量运行时从 `Lcom/baidu/homework/activity/newhomepage/a_home/bean/HPListItem;` 用 `getStaticIntField` 读，读不到则 adTypes=null（不过滤）。
  - `hookAdapterHost()` 对 `RecyclerView` / `androidx.viewpager.widget.ViewPager` / `ViewPager2` 的 `setAdapter` afterHook，adapter 简单类名含 `HomeBannerAdapter` 或 `ADOnePlusNPageAdapter` → `setVisibility(8)`。
- `ActivityBlocker.java`：hook `android.app.Activity#onCreate` afterHook，类名命中精确黑名单（AdResumeSplashActivity、com.fastad.api.reward.RewardAd1/2/3Activity、com.fastad.csj.half.reward.CsjRewardActivity、com.homework.fastad.reward.RewardDialogActivity、com.homework.fastad.common.web.ApiWebActivity、com.qq.e.ads.ADActivity/PortraitADActivity/LandscapeADActivity）或前缀黑名单（com.bytedance.sdk.openadsdk.core.activity. / .stub.activity. / com.baidu.mobads.sdk.internal. / com.baidu.mobads.production. / com.kwad.sdk.api.proxy.app.）→ `callMethod(self,"finish")`。
- `DialogKiller.java`：首页弹窗任务体系基类 `com.baidu.homework.activity.index.dialog.u`（12 个子类 i/j/k/l/m/n/o/p/q/r/s/t，`ZybDialogTaskManager` 调度）；hookAllMethods `<init>` 仅打 log（探明谁被创建）；对基类中「单参 `android.app.Activity`」的方法（`e(Activity)` = 展示入口）beforeHook → `setResult(null)` 全拦（`ALLOW` 白名单预留为空）；另 hook `index.dialog.main_page.MainPageGuideDialog#show` 拦截新手引导浮层。
- **javac 编译已通过**（`javac --release 8 -cp build/stubs`，stub 单独编译到 `build/stubs`，不进 dex）。
- `AndroidManifest.xml`（package `com.sql916.zybclean`, minSdk24/targetSdk34, label `zybclean`, meta-data xposedmodule/xposeddescription/xposedminversion=53）与 `assets/xposed_init`（一行 `com.sql916.zybclean.HookEntry`）已写。
- `build.sh`：javac → `d8 --min-api 24 --release --lib android.jar` → `aapt2 link` → `zip` 合入 classes.dex + assets/xposed_init → `zipalign -f -p 4` → `apksigner`（keystore `zybclean.keystore`，口令 zybclean，首次自动 `keytool -genkeypair` 生成）→ 输出 `zybclean.apk`。SDK 根路径假定 `$HOME/android-sdk/sdk`，build-tools 取最新，`platforms/android-34/android.jar`。
- `deploy.sh`：`scp zybclean.apk realme:/data/local/tmp/` → `rsh realme "pm install -r ... && am force-stop com.zuoyebang.knowledge"`。

### 开屏风险调研（dexsql CLI）
- `com.zuoyebang.imp.splash.k` 是**接口**，只有 `onSplashClose(Z)V`；约 25 处调用者（`splash.d/j/t/v`、`f`、`g`、`h$f.run`、`i$h.run`、`p.run`、`q/*` 等）→ 说明有独立倒计时/超时关闭路径（`p.run`），被 AdBlocker 跳过加载后大概率会超时自关。
- `InnerSplashProcessor` 的 `closeSplash()V` / `init(Activity,ViewGroup,Runnable)` / `showSplashView()` / `loadAdxCacheItem()Z` 等 access_flags=1025（public+synthetic，实例方法，Robust 生成 Impl 痕迹）。**结论：v1 先不加额外开屏兜底，等实测**。
- 首页弹窗子类见 DialogKiller 说明；`analysis/garbage_candidates.raw` 存了 dialog/banner/redpacket 候选清单。

### LSPosed 部署方式（重要，设备无 LSPosed 管理 App）
- `pm list packages | grep -i lsposed` 只有 `org.lsposed.corepatch`，**没有 org.lsposed.manager** → 不能在 UI 里勾选。
- LSPosed 数据：`/data/adb/lspd/config/modules_config.db`（+`-wal`,`-shm`，已 scp 到 `analysis/lspd/`）。表结构：
  - `modules(module_pkg_name TEXT PK, apk_path TEXT)`
  - `modules_state(module_pkg_name, user_id, enabled BOOLEAN DEFAULT 0, scope_request_blocked BOOLEAN DEFAULT 0, PK(module_pkg_name,user_id))`
  - `scope(module_pkg_name, app_pkg_name, user_id, PK(三者))`
  - `module_configs(module_pkg_name,user_id,group_name,key_name,data BLOB)`、`lspd_configs(key_name,data)`、`app_configs(app_pkg_name,data)`
- 模块目录：`/data/adb/modules/zygisk_lsposed/`（含 `bin daemon daemon.apk framework.dex lib lspctl lspd`）。
- `lspctl` 是 shell 脚本：校验 `$BOOTCLASSPATH` 非空后 `exec app_process -Djava.class.path=$dir/daemon.apk -Xnoimage-dex2oat /system/bin --nice-name=lspctl org.lsposed.lspd.cli.CliMain "$@"`。
- 绕过校验直接跑：`P=$(pidof com.android.systemui); BC=$(tr "\0" "\n" < /proc/$P/environ | sed -n "s/^BOOTCLASSPATH=//p"); BOOTCLASSPATH="$BC" /system/bin/app_process -Djava.class.path=/data/adb/modules/zygisk_lsposed/daemon.apk -Xnoimage-dex2oat /system/bin --nice-name=lspctl org.lsposed.lspd.cli.CliMain <args>` → **试 `--help` 时无任何输出（BC len=2702 说明环境变量取到了），需再试 `modules list` / 看退出码**。
- 备选：直接改 `modules_config.db`（插 modules + modules_state.enabled=1 + scope 行）后重启 lspd（或重启手机）。改 DB 前需确认 WAL 安全与 lspd 是否长驻。

### SDK 工具链现状（阻塞点）
- `~/android-sdk/cmdline-tools.zip` 已完整下载（153,607,504 B，`unzip -t` 通过），解压到 `~/android-sdk/sdk/cmdline-tools/latest/`（含 bin/lib/source.properties，结构正确）。
- **`sdkmanager` 报错**：`找不到或无法加载主类 com.android.sdklib.tool.sdkmanager.SdkManagerCli`（ClassNotFoundException）→ 待查：`latest/bin/sdkmanager` 脚本的 classpath 写法 / `lib/` 里是否有 sdklib jar / Java 25 兼容性（本机 java 25.0.5，可能需换 JDK 17）。
- `build-tools;34.0.0` 与 `platforms;android-34` 尚未安装 → `./build.sh` 目前会在检查处退出。

### 下一步（按顺序）
1. 修 sdkmanager（看 `latest/bin/sdkmanager` 脚本 + `ls latest/lib | grep -i sdklib`；必要时下 jdk17 或手工下载 build-tools zip 解压）。
2. 装 build-tools/aapt2/d8/apksigner/zipalign + android.jar → 跑 `./build.sh` 出 `zybclean.apk`。
3. 用 lspctl（或改 DB + 重启）注册并启用模块、加 scope `com.zuoyebang.knowledge`。
4. `./deploy.sh` 安装 → `rsh realme 'logcat -d | grep ZYBCLEAN'` 验证；重点看开屏是否卡住、首页是否空。
5. 按实测补 v2：首页弹窗白名单、垃圾入口（商城/优惠券/学币）、悬浮广告。

---

## 11. 2026-10-30 构建/部署打通（第三轮）

### 构建链路最终形态（本机 aarch64，Google build-tools 是 x86_64 跑不了！）
- **根因**：Google build-tools 只发 x86_64 二进制 → `aapt2` 报「可执行文件格式错误」；`build-tools/34.0.0/lib/d8.jar` 被**截断成正好 12 MiB**（12,582,912 B，zip 无效）。
- **解决**：`sudo -n apt-get install -y aapt zipalign apksigner`（Debian trixie arm64 原生，`aapt` 1:14~beta1-3 → `/usr/bin/aapt` 符号链到 `/usr/lib/android-sdk/build-tools/debian/aapt`）。
- dex：用 cmdline-tools 里的完整 **`$HOME/android-sdk/sdk/cmdline-tools/latest/lib/r8.jar`**（15,305,887 B，`unzip -t` OK）跑 `java -cp r8.jar com.android.tools.r8.D8`。
- **`sdk/platforms/android-34/android.jar` 被截断成 8 MiB**（8,388,608 B，正是 AGENTS.md 说的「掉电截断在 8MiB」）→ 从 `~/android-sdk/plat34.zip` 重新 unzip 出该文件 + `sync`，现在 26,361,808 B，`unzip -t` 无错。**下载/解压后的 jar 必须 `unzip -t` 验完整性**。
- `build.sh` 现已改为：javac(stub) → javac(src) → `java -cp r8.jar com.android.tools.r8.D8 --min-api 24 --release --lib android.jar --output build/dex ...` → `/usr/bin/aapt package -f -M AndroidManifest.xml -A assets -I android.jar -F build/base.apk`（**aapt1 会自动打包 assets**）→ `zip` 加 classes.dex → `/usr/bin/zipalign -f -p 4` → `/usr/bin/apksigner sign`（keystore `zybclean.keystore`，口令 zybclean，已生成）→ `BUILD_OK`。
- 产物 `~/zuoyebang_xposed/zybclean.apk` = **16,791 B**，内容：AndroidManifest.xml / assets/xposed_init / classes.dex (15,044 B) / META-INF 签名。`aapt dump xmltree` 确认三个 meta-data（xposedmodule=true、xposeddescription=作业帮净化：去广告、去首页信息流、xposedminversion=0x35=53）。

### 部署
- `bash deploy.sh` → scp 到 realme:/data/local/tmp → `pm install -r` = **Success**（apk_path=`/data/app/~~mKqt8OkCaXEyur7zNnDhQw==/com.sql916.zybclean-7z684eqy4CU37AhKmj94nw==/base.apk`）。
- `bash enable_module.sh` → 已把模块写进 lspd DB：modules 26 条、`modules_state.enabled=1`、`scope=com.zuoyebang.knowledge`。
- **⚠️ 当前阻塞：lspd 起不来**。kill 之后 `nohup daemon --force &`、`setsid nohup ... &`、直接 `(./lspd --force &)` 都没让 pidof lspd 出现；只剩 zygisk companion 进程（zn-zygisk-companion64）。`daemon` 脚本会 `./lspd "$@" >/dev/null 2>&1 & PID=$!; wait $PID`，rc≠0 时写 `/data/adb/lspd/log/daemon-$PID-crash-*.log` 并 dump logcat。**正在后台跑**：`ls -lt /data/adb/lspd/log/` + `(./lspd --force >/data/local/tmp/lspd.out 2>&1 &); sleep 5; pidof lspd; cat /data/local/tmp/lspd.out`（shell ID `sh_1887a1051001M2KcTOfyx5yReV`）。
- 待查方向：lspd 可能需要 zygisk 侧的 socket/环境（正常由 service.sh 在 **zygisk 注入上下文**里拉起）、或 native 二进制依赖库路径（`lib/`）、或 `--force` 参数行为；实在不行**重启手机**让 init 正常拉起 lspd（最稳，DB 已改好，重启即生效）。

### 验证脚本
- `bash check.sh`（DEV=realme）：grep `/data/adb/lspd/log/*.log` 与 `logcat -d` 里的 `ZYBCLEAN`，并 `ps -A | grep zuoyebang`。**XposedBridge.log 在 LSPosed 下写到 lspd 日志**，logcat 不一定有。

---

## 12. 2026-10-30 00:30 —— 模块已在真机生效（首次验证）

### lspd 重启失败 → 直接重启手机（已解决）
- `lspd` 不是二进制而是 **shell 脚本**：`exec /system/bin/app_process -Djava.class.path=$dir/daemon.apk -Xnoimage-dex2oat /system/bin --nice-name=lspd org.lsposed.lspd.Main "$@"`。
- kill 掉 lspd 后手动拉起（nohup / setsid / `(./lspd --force &)` / `timeout 8 ./lspd --force`）**全部 rc=0 但无输出、无进程**（app_process 里的 Main 静默 exit，产物 stdout 为空）→ 手动重启 lspd 这条路走不通。
- **结论：改完 lspd DB 后必须重启手机**。`rsh realme 'sync; (sleep 2; reboot) >/dev/null 2>&1 &'` → 约 25s 后 `sys.boot_completed=1`，`pidof lspd` = 2105（正常）。
- 重启后 DB 内容仍在（26 modules / zybclean enabled=1 / scope=com.zuoyebang.knowledge）。

### 运行验证（真实日志）
- 启动 Activity：`am start -n com.zuoyebang.knowledge/com.baidu.homework.activity.init.InitActivity`（**launcher = InitActivity**，不是 SplashActivity）。
- **注意：`logcat` 里看不到 ZYBCLEAN 输出**，LSPosed 把 `XposedBridge.log` 写进自己的日志文件：`/data/adb/lspd/log/verbose_<boot时间>.log`（grep `ZYBCLEAN`，行格式带 `(com.zuoyebang.knowledge)[com.sql916.zybclean,XposedBridge,...]`）。`modules_<时间>.log` 是各模块自身日志的镜像。
- 实测输出（19 行）：
  - `hook loaded, process=com.zuoyebang.knowledge`
  - `all hooks installed`
  - `AdBlocker installed on 34/34 adapters` ← 34 个 fastad 适配器全部 hook 成功
  - `ad item types = [1, 2, 3]` ← HPListItem 的 TYPE_AD/TYPE_LINKED_AD/TYPE_VIP_RECOMMEND 常量读到了
  - 3 条 `feed filter installed:`（DFeedListAdapter / EHPFeedsListAdapter / DFeedFollowerAdapter）
  - 12 条 `block popup: com.baidu.homework.activity.index.dialog.{i,k,l,n,o,p,q,s,t}` 等（**是安装期确认，不是运行时拦截**）
  - `ActivityBlocker installed`
  - 2 条失败：`hook h.m.c.f.m.doShowAD/doLoadAD failed -> IllegalArgumentException: Cannot hook abstract methods`（**已知无害**：`h.m.c.f.m` 是接口，其实现类已包含在 34 个里；可后续从列表移除避免噪音）
- 目录里同时存在 `/data/adb/lspd/log/verbose_*.log`（hook 详细，含 ZYBCLEAN）与 `/data/adb/lspd/log/modules_*.log`；每次开机一份。

### 验证技巧（无 UI 自动化时）
- 设备 `uiautomator dump` 不可用，但 **`screencap -p /data/local/tmp/x.png` + scp + 本机 `read` 看图**完全可行。
- `dumpsys window | grep mCurrentFocus` 取当前前台包名（用户当时在用 `mark.via.gp`（Via 浏览器）看 opencode web UI）。
- 验证完记得把前台还给用户原来的 App。

### 待办（v2 候选）
- 去掉 `h.m.c.f.m` 的无效 hook（abstract）以消除报错噪音。
- 运行时确认：首页是否真的空/无广告、开屏是否卡住、`doShowAD` 是否在首屏后被调用（日志暂无运行时拦截记录，可能是启动 10s 内未触发）。
- 弹窗 12 个子类目前是「全拦（`e(Activity)` beforeHook 直接 setResult(null)）」——需确认没有误伤必要弹窗（如协议/升级/登录）。

---

## 13. v2 生效确认 + Git 仓库/CI（2026-10-30 00:35）

### v2 改动与验证
- `FeedKiller.HIDE_SIMPLE_NAMES` 增加 `"EHPFeedsListAdapter"`（首页信息流整列表隐藏）；`AdBlocker` 删除 `h.m.c.f.m`（接口，abstract hook 报错噪音）。
- `./build.sh` → 16,791 B；`./deploy.sh` → `pm install -r` Success。
- 启动后日志（`/data/adb/lspd/log/verbose_2026-10-30T00:25:39.919863.log`）确认：
  - `AdBlocker installed on 33/33 adapters`、`ad item types = [1, 2, 3]`、3 条 `feed filter installed`、`ActivityBlocker installed`
  - `skip ApiSplashAdapter.doLoadAD`
  - `block popup: com.baidu.homework.activity.index.dialog.{o,s,p,t,q,i,l,k,n}`
  - `setAdapter ... <- EHPFeedsListAdapter` → **`hide RecyclerView with EHPFeedsListAdapter`**（命中，信息流已隐藏）
  - 首页其它 adapter 正常放行：`DFunctionAreasGridAdapter`、`DFunctionAreasPagerAdapter`、`DHeaderImpAdapter`、`EHomeSubPagerAdapter`

### git / CI（用户要求）
- 仓库：`git@github.com:SQL0106/zuoyebang_xposed.git`（public），本地 `~/zuoyebang_xposed`，分支 main。
- 忽略：`base.apk`、`zybclean.apk`、`build/`、`analysis/*.{png,raw,txt,log,db}`、`*.idsig`、`libs/api-82.jar`；**`zybclean.keystore` 故意入库**（口令 zybclean），保证 CI 与本机签名一致、可覆盖安装。
- `build.sh` 已改为可移植：`SDK=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/android-sdk/sdk}}`，android.jar 用 `platforms/*/android.jar` 排序取最新，d8 用 `cmdline-tools/*/lib/r8.jar`（回退 build-tools d8.jar），aapt/zipalign/apksigner 先找系统命令再找 build-tools，全部打印路径。
- `.github/workflows/build.yml`（ubuntu-latest, JDK 17）：
  - `concurrency` 取消旧构建；`timeout-minutes: 20`；`permissions.contents: write`
  - 步骤：定位 SDK → **actions/cache 缓存 `$SDK/platforms` + `$SDK/build-tools`（key `*-androidsdk-p34-bt34-v1`）** → apt 装 aapt/zipalign/apksigner → 缓存命中则跳过 sdkmanager 下载 → `./build.sh 2>&1 | tee build.log`
  - **失败保留现场**：`if: failure()` 打 `build-failure.tar.gz`（build.log + build-info.txt + failure-context.txt + build/ 中间产物）并上传 artifact `build-failure`(30 天)；`build-logs` artifact 总是上传（14 天）
  - 成功上传 `zybclean-apk`；`v*` tag 用 softprops/action-gh-release 发版
- 两次 commit 已 push：`c30d13c` 初始模块、`b235150` CI 优化。

### 用户反馈（重要）
- 「为什么总是要看作业帮」→ **不要反复把作业帮拉到前台截图**，会打断用户用手机；验证一律走 `/data/adb/lspd/log/verbose_*.log` 的 `ZYBCLEAN` 行，必要时再请用户自己看手机屏幕确认。
- 之前 c.png 截到的是用户当时的 Via+opencode 界面，说明启动确实发生且 hook 生效，但截图不可靠。

### 剩余待办
- 删除「绑定作业帮APP账号 / 100 立刻绑定」推广卡（位置在首页 `DHeaderImpAdapter` 区）。
- 需要时再收「垃圾」入口（商城优惠券、学币充值、家长付费、Test/Debug）。自动化测试：真机手动确认首屏。

---

## 14. v3 目标：删「视频讲解小窗」（滚动到底部自动出现的浮窗）

用户原话：「视频讲解小窗删掉」+ 补充「就是滑到下方，自动出现一个浮窗」。

### 已排除
- `com.zuoyebang.homework.video.clip.widget.VideoTabFloatView`（悬浮球，`showFloatView/tryShowFloatView/hideFloatView/animateToHalfHide/isTodayEnable`）——调用者只有 `ShortVideoTabFragment`（短视频 tab 内部），与首页滚动无关。
- `VideoViewManager`（`com.zybang.sdk.player.base.videoview`）只是 VideoView 缓存池。
- 字符串 `%小窗%/%悬浮%/%画中画%` 只命中「悬浮窗搜题」等无关项。
- `%视频讲解%` 只有一条（`TabUserFragmentVipCardsHelper.e` 的会员权益文案）。

### 命中候选（待确认后 hook）
- **`com.baidu.homework.activity.index.HomePageFloatView`**（+ 内部类 `$scrollListener$2`、`$scrollListener$2$1`、`$a..$k`）→ 名字与「首页浮窗 + 滚动监听」完全吻合，极可能就是目标。**下一步：decode 该类，找 `show`/`onScroll` 入口并 hook 成不显示（或 setVisibility 8 / removeView）。**
- 另有 `com.lzf.easyfloat.*`（EasyFloat 悬浮窗库，未混淆）可作备选拦截层：拦 `EasyFloat.show(...)`。

### 静态分析工具备注
- `dexsql query` 的 `method_calls` 表扁平列：`method`、`method_class`、`method_name`、`callee`、`callee_class`、`callee_name`、`file_id`、`class_idx`、`method_idx`、`callee_method_idx`（**没有 caller_method_id / callee_class_descriptor**）。
- `method_strings` 扁平列：`method`、`method_class`、`method_name`、`value`、`file_id`、`class_idx`、`method_idx`、`string_idx`。
- `methods` 列：`class_descriptor`、`name`、`descriptor`、`return_descriptor`、`param_count`、`param_descriptors`、`access_flags`、`opcode_count`。
- 中间产物：`analysis/video_pkg.txt`(340)、`analysis/video_all.txt`(1346)、`analysis/float_candidates.txt`(113)。

### v3 部署状态
- `./build.sh` → BUILD_OK（classes.dex 17,688 B，含 FloatKiller）；`./deploy.sh` → `pm install -r` Success / INSTALLED（2026-10-30 约 01:0x）。
- 运行时待确认：启动后 verbose log 应出现 `FloatKiller installed on N methods of HomePageFloatView`；用户下滑首页应无浮窗。
- 教训：用户反感被反复拉前台，验证交给用户或读日志。

---

## 15. FloatKiller 无效 —— 浮窗其实在「搜题结果页」（v4）

- 部署后用户实测：「还是有这个恶心的浮窗」，截图显示浮窗出现在**拍照搜题结果页**（顶部相机照，底部弹出面板，tab `结果1 / AI解题 / 结果3 / 结果4`，底部按钮「分享 / 加错题本 / 问小帮 / 再拍一页」），右下角一张**视频讲解小卡**：红色角标「讲解中」+ 视频预览图 + 右上角关闭 X，滚动时自动出现。
  → **`com.baidu.homework.activity.index.HomePageFloatView` 是首页的运营浮球，不是这个**。FloatKiller 保留无害（首页浮球也不弹了）。
- 已查字符串：
  - `%讲解中%` → 只有 `com.zybang.parent.view.GameLoadingProgressView.upadteProgress`（"准备讲解中..."/"准备讲解中...100%"）。
  - 其它 `%讲解%` 命中：`"正在使用非WI-FI网络，下载讲解（"`、一大段老师评价 JSON（沙*咧 等）。**没有「讲解中」角标文案的字符串** → 角标是图片资源或服务端下发。
- 已排查（无结果）：`activity/search/**` 里没有 Video/Player/Float/Window 类；`%Explain%` 只剩 ExplainLabelContainer(搜索页标签)、parent 的 ExplainGetpkginfo(讲解包下载)；`%Suspend%/%MiniWindow%/%SmallWindow%` 未见相关；`com.zybang.parent.*` 无 Float/Video/Mini 类。
- 页面确认为 `com.baidu.homework.activity.search.SearchResultActivity` + `search/core/SearchResultPager(Adapter)` + `search/newsearchv3/**`（有 `ai/AIInputSugAdapter`，AI解题 tab）。
- 下一步方向（按优先级）：
  1. 反编译/静态定位 `SearchResultActivity`、`SearchResultPager`、`SearchResultPagerAdapter` 的子页 Fragment（`a`..`g` 七个内部类可能就是 结果1/AI解题/结果3/结果4 页），看谁持有一个「视频讲解」View。
  2. 找资源 id：`aapt dump resources base.apk | grep -i -E 'explain|video'` 找布局/角标资源，再反查 `R.id` 常量使用方。
  3. 运行时探针：hook `Activity#onCreate` 打印所有 Activity 类名 + hook `Fragment#onViewCreated` 打印 Fragment 类名 → 让用户复现一次（不主动拉前台，等用户自己滑），从 verbose log 读到真实类。
  4. 兜底：hook `android.view.WindowManager#addView`（type 为 `TYPE_APPLICATION_PANEL/SUB_PANEL/TYPE_PHONE` 的浮层）或 `PopupWindow#showAsDropDown/#showAtLocation` 统一拦截，慎用（可能误伤下拉菜单/Toast）。

### v4 补充：AI解题入口已定位，浮窗仍未定位（需运行时探针）
- 用户新指令（原话）：「AI解题也干掉，根本不会用的东西。」
- **AI 解题入口 = `com.baidu.homework.activity.search.newsearchv3.ai.ResultAiActivity`**（`createIntent` 静态工厂 + `onCreate`；进入方：`ai/e0.onClick`（tab 点击）、`AISearchBottomInputView.jumpToXbAiPage`）。→ 加进 ActivityBlocker 黑名单（finish）即可让该 tab 点了不生效。
- `search/newsearchv3/ai/` 包另有 `AISearchBottomInputView`、`BaseAiContainerView`、`AISearchEditText`、`AIInputSugAdapter`、`AiConfigModel`。
- 浮窗仍未定位：
  - `activity/search/**` 里唯一继承 `RecyclerView$OnScrollListener` 的是 `newsearchv3/ai/AISearchBottomInputView$3`（与浮窗无关）。
  - 无 `VideoCard/ExplainCard/VideoSuspend` 类（仅 `ADXListVideoCardCountdownItem`、`RadiusCardView`）。
  - `com/zuoyebang/homework/video/clip` 下有 `databinding/FloatViewVideoTabBinding`、`widget/RadiusCardView`、`player/ShortVideoView` 等，但都是短视频 tab 用的。
  - `activity/search/**` 无 %视频%/%讲解% 字符串（服务端下发）。
- 结论：**需运行时探针** —— 在 `Activity#onCreate` 打 Activity 类名、`Fragment#onViewCreated` 打 Fragment 类名、`ViewGroup#addView` 仅对 `com.zuoyebang/com.baidu/com.zybang` 包的 View 打类名（限 80 条/进程），让用户自己复现一次（拍照搜题→下滑），再从 verbose log 找真实类名。

### v4 代码改动（已部署 b082d4b）
- `ActivityBlocker.EXACT` 增加 `com.baidu.homework.activity.search.newsearchv3.ai.ResultAiActivity`（AI解题页 onCreate 后 finish）。
- 新增 `src/com/sql916/zybclean/Probe.java` 探针（临时，定位浮卡用），HookEntry 里在 FloatKiller 之后 install：
  - `Activity#onCreate` → `probe activity: <类名>`（预算 150/进程）
  - `ViewGroup#addView` → 只对 `com.zuoyebang./com.baidu.homework./com.zybang./com.homework.` 开头或类名含 Video/Float/Explain 的 View 打 `probe addView: <子View> <- <父容器>`（预算 400/进程）
  - `Fragment#onViewCreated`（androidx + android.app） → `probe fragment: <类名>`
- 构建/安装：`./build.sh` BUILD_OK + `./deploy.sh` INSTALLED；`unzip -p zybclean.apk classes.dex | strings` 能看到 `probe addView`、`ResultAiActivity`、`HomePageFloatView`。
- APK 大小仍是 16,791 B（dex 变大、zip 对齐后总大小巧合不变，已用 strings 核实内容已更新）。

### v5（已部署，2026-10-30 00:45）—— 科普专区硬杀 + 首页 View 树探针
1. **科普专区硬杀**：`FeedKiller.killForever(cl, "com.baidu.homework.activity.newhomepage.e_home.adapter.EHPFeedsListAdapter")`
   - 遍历 `getDeclaredMethods()`：凡「单参且参数是 List」的方法 → before 把 `args[0]` 换成空 `ArrayList`（打到 `feed emptied at <方法名>`）；`getItemCount()I` → before `setResult(0)`。
   - 原因：单靠 `setAdapter` 后 `setVisibility(8)` 会被后续显隐逻辑覆盖（日志里 `hide RecyclerView` 出现 4 次但页面照显）。
2. **新增 `src/com/sql916/zybclean/ViewDump.java`**（临时探针，定位完删）：
   - `hookHome`：hook `com.baidu.homework.activity.index.IndexActivity#onCreate` afterHook → 对 decorView `postDelayed(runnable, 5000)` 和 `12000` 各 dump 一次整棵 View 树，行前缀 `viewdump `，内容 = 缩进 + `view.getClass().getSimpleName()` + `id=<资源名去包前缀>`（`Resources.getResourceName(id)`）+ `vis=` + `宽x高` + `desc=`（contentDescription）；`MAX_LINES=220`。
   - `hookOverlay`：hook `android.view.WindowManagerGlobal#addView`，只对包名前缀 `com.zuoyebang/com.baidu/com.zybang/com.homework` 的 View 打 `overlay addView: <类名> type=<LayoutParams.type>`，预算 40 条。**用于抓不经 ViewGroup#addView 的 WindowManager 浮层**（「讲解中」浮卡可能属于这类）。
   - `HookEntry` 里在 Probe 之后 `try { ViewDump.install(cl); } catch ...`。
3. 已 `./build.sh`（BUILD_OK，`zybclean.apk` 20,887 B）+ `./deploy.sh`（Success/INSTALLED）。
4. **资源名反查失败**：`aapt dump resources base.apk > analysis/res.txt`（4.1 MB）里搜 `notice|bell|xiaoxi|unread` **0 命中** → 资源名被混淆，通知铃铛只能靠 ViewDump 运行时拿 id。
5. 静态补充：
   - `NewVideoRemindersLayout`（`activity/user/widget/`）方法：3 个 `<init>(Context[,...])` 65537、`init(Context)`、`initView(Context)`、`setGlideVisibility(I)`、`setVisibility(I)` — 它是 `activity/user` 学习记录页的「视频提醒」卡，未必是搜题页浮卡。
   - `grep 'activity/search/.*(Video|Clip|Player|Suspend|Float|Mini)'` 与 `newsearchv3` 内 `video|player|float|card` 均 **0 命中** → 搜题结果页没有视频相关类 → 浮卡很可能是 WindowManager 浮层或跨包复用组件。

## 16. Snap 首次成功输出 + 首页与开屏分离问题（2026-10-30 00:50）

- 触发成功：`/data/data/com.zuoyebang.knowledge/files/.zyb_snap.txt` 生成 431 B（`analysis/snap1.txt`、`snap2.txt`）。
- 内容（两次相同）：
  ```
  SNAP activity=com.baidu.homework.activity.init.InitActivity
  DecorView → LinearLayout → (ViewStub action_mode_bar_stub vis=G) + FrameLayout#content
    → RelativeLayout#zyb → FrameLayout#zyb box=0,0-1280,2450 + ViewStub#zyb vis=G
  SNAP decor views=7
  ```
  → **InitActivity 只是个壳/开屏，首页真实 View 树不在它里面**；且 `sActivity` 只记最后一次 onResume，所以抓不到真正前台页。
- 修复（已改 `Snap.java`，未编译）：
  1. 新增 `static List<Object> ACTIVITIES`（去重、上限 8），`Activity#onResume` afterHook 里 append；
  2. 新增 `pickActivity()`：从后往前找 `hasWindowFocus()==true` 的 Activity，找不到回落 `sActivity`；
  3. 触发线程改用 `pickActivity()`。
- 下一步：`./build.sh` → `./deploy.sh` → 请用户停在首页 → `rsh realme 'touch .../.zyb_snap'` → scp 出 dump，定位搜索框/铃铛/红角标。
