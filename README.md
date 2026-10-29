# zybclean —— 作业帮（com.zuoyebang.knowledge）净化 Xposed 模块

LSPosed 模块，不重打包原 APK。目前功能：

| 模块 | 作用 |
|---|---|
| `AdBlocker` | 拦掉作业帮广告聚合层 `com.fastad` 全部 33 个适配器的 `doLoadAD()` / `doShowAD()`（穿山甲/优量汇/快手/百度全走这层），广告不加载也不展示 |
| `FeedKiller` | 首页信息流（`EHPFeedsListAdapter`）、「科普专区」流整列表隐藏；`DFeedListAdapter` / `DFeedFollowerAdapter` 的数据里再过滤 `TYPE_AD` / `TYPE_LINKED_AD` / `TYPE_VIP_RECOMMEND` 条目；首页 banner 位（`HomeBannerAdapter`、`ADOnePlusNPageAdapter`）隐藏 |
| `ActivityBlocker` | 开屏/回前台广告页、激励视频页、各广告 SDK 的落地页（`com.bytedance.sdk.openadsdk...`、`com.qq.e.ads.ADActivity`、`com.baidu.mobads...`、`com.kwad...`）创建即 `finish()` |
| `DialogKiller` | 首页弹窗任务体系（`activity.index.dialog.*`，`ZybDialogTaskManager` 调度）与新手引导浮层 `MainPageGuideDialog` 一律不展示 |

## 编译

本机（aarch64 Linux）：

```sh
./build.sh            # 产出 zybclean.apk
./deploy.sh           # scp 到真机并 pm install -r
```

依赖：JDK、Android SDK（`platforms/android-*/android.jar` + `cmdline-tools/*/lib/r8.jar`）、`aapt` / `zipalign` / `apksigner`（系统包或 build-tools 均可，脚本会自动找）。**不用 gradle**。

也可以直接推 GitHub，由 [`.github/workflows/build.yml`](.github/workflows/build.yml) 在 CI 上出包（打 `v*` tag 会发 Release）。

## 安装 / 启用

1. 安装 APK，确认 LSPosed 作用域勾选 `com.zuoyebang.knowledge`。
2. 强制停止作业帮后重新打开。

没有 LSPosed 管理 App 的设备（本机 realme 就是这样），用 `./enable_module.sh` 直接改 LSPosed 配置库并重启手机生效。

## 日志

`XposedBridge.log` 不写 logcat，LSPosed 会落到：

```sh
/data/adb/lspd/log/verbose_<开机时间>.log     # grep ZYBCLEAN
```

## 目录

```
src/com/sql916/zybclean/    模块源码（HookEntry + 四个 Killer）
libs/stubs/                 编译期 Xposed API stub（不进 dex）
assets/xposed_init          模块入口类名
AndroidManifest.xml         xposedmodule / xposeddescription / xposedminversion
analysis/NOTES.md           逆向分析与实测记录（权威工作笔记）
```
