# zybclean —— 作业帮净化 Xposed 模块

[![build](https://github.com/SQL0106/zuoyebang_xposed/actions/workflows/build.yml/badge.svg)](https://github.com/SQL0106/zuoyebang_xposed/actions/workflows/build.yml)
[![license: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

LSPosed 模块，用来给作业帮去广告、去信息流、去推广与无关功能。**不重打包原 APK**。

> **⚠️ 本项目代码由 LLM（大语言模型）生成。** 在人工指导下迭代并经真机试跑，但**未经逐行安全审计**。按「现状」提供，安装即自行承担后果；与作业帮 / 百度 / LSPosed 官方无关；不收集、不上传任何数据。以 [MIT](LICENSE) 开源。

- 适配版本：`com.zuoyebang.knowledge` **14.34.0 (1738)**
- 模块版本：**1.1.0 (3)**，基于 [libxposed API 102](https://github.com/LSPosed/LSPosed)
- 运行环境：Android 9+（minSdk 24，targetSdk 34），需 LSPosed（支持 API 102 的版本）

## 功能

| 模块 | 作用 |
|---|---|
| `AdBlocker` | 拦截广告聚合层 `com.fastad` 全部 33 个适配器的 `doLoadAD()` / `doShowAD()`（穿山甲 / 优量汇 / 快手 / 百度都走这层） |
| `FeedKiller` | 首页信息流、「科普专区」整列表隐藏并在数据层清零；`DFeedListAdapter` / `DFeedFollowerAdapter` 数据里再过滤广告与 VIP 推荐条目；首页 banner 位隐藏 |
| `ActivityBlocker` | 开屏 / 回前台广告页、激励视频页、AI 解题页、各广告 SDK 落地页，创建即 `finish()` |
| `DialogKiller` | 首页弹窗任务体系与新手引导浮层一律不展示 |
| `FloatKiller` | 首页运营浮球不弹、不请求数据 |
| `HomeChromeKiller` | 首页顶栏净化：搜索框、铃铛（通知）、红点角标全部隐藏；按文字干掉「绑定作业帮APP账号」推广卡 |
| `WebKiller` | 结果页是 H5，加载后注入 JS：隐藏「AI解题」tab 与「讲解中」浮窗，MutationObserver 持续生效 |
| `FastStart` | 把开屏页里 >800ms 的延时压到 250ms，缩短启动等待 |

调试探针（`Probe` / `ViewDump` / `Snap` / `WebEyes`）代码保留，需要在 `HookEntry` 里取消注释才启用。

## 下载

- APK：[Releases](https://github.com/SQL0106/zuoyebang_xposed/releases/latest)
- 每次提交的构建产物：Actions 最近一次成功运行 → Artifacts → `zybclean-apk`

## 安装

1. 安装 APK。
2. **作用域已在包里声明**（`META-INF/xposed/scope.list` = `com.zuoyebang.knowledge`），LSPosed 里启用模块即可，不需要手动勾选。
3. 强制停止作业帮，重新打开。

**不需要重启手机**：作用域里只有普通 App 时，配置在目标 App 下次启动时生效。只有把「系统框架」放进作用域才必须重启。别去 `kill lspd`——它被杀后无法手动拉起，那才真的只能重启。

设备上没有 LSPosed 管理 App 时，用 `./enable_module.sh` 直接写 LSPosed 配置库（同样不重启）。

## 编译

```sh
./build.sh     # 产出 zybclean.apk
./deploy.sh    # scp 到真机并 pm install -r
```

需要 JDK、Android SDK（`platforms/android-*/android.jar`、`cmdline-tools/*/lib/r8.jar`）、`aapt` / `zipalign` / `apksigner`，以及仓库内的 `libs/libxposed-api-102.jar`。**不用 Gradle**。也可以直接 push，由 [`.github/workflows/build.yml`](.github/workflows/build.yml) 在 CI 出包；打 `v*` tag 会发布 Release。

## 日志

`XposedBridge.log` 不写 logcat，LSPosed 落在：

```sh
/data/adb/lspd/log/verbose_<开机时间>.log     # grep ZYBCLEAN
```

## 目录

```
src/io/github/sql0106/zybclean/          HookEntry（XposedModule 入口）+ 各 Killer + 探针
src/io/github/sql0106/zybclean/compat/   libxposed API 102 上的 legacy 兼容层
libs/libxposed-api-102.jar               编译期依赖（libxposed:api:102.0.0）
res/values/strings.xml                   模块名与描述
module/META-INF/xposed/                  module.prop / java_init.list / scope.list
AndroidManifest.xml                      包信息（不再使用 xposed* meta-data）
analysis/NOTES.md                        逆向分析与实测记录
analysis/EXPERIENCE.md                   方法论与踩坑
```

## 关键认知

- 作业帮资源 id 几乎全被混淆成同名 `zyb`，只能靠**控件类名 + 层级/坐标 + 文字**定位。
- **搜题结果页是 H5**，AI 解题 tab、讲解浮窗都在网页里，native hook 无效 → 走 WebView JS 注入。
