# zybclean —— 作业帮净化 Xposed 模块

[![build](https://github.com/SQL0106/zuoyebang_xposed/actions/workflows/build.yml/badge.svg)](https://github.com/SQL0106/zuoyebang_xposed/actions/workflows/build.yml)
[![license: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

LSPosed 模块，用来给作业帮去广告、去信息流、去推广与无关功能。**不重打包原 APK**。

> **⚠️ 本项目代码由 LLM（大语言模型）生成。** 在人工指导下迭代并经真机试跑，但**未经逐行安全审计**。按「现状」提供，安装即自行承担后果；与作业帮 / 百度 / LSPosed 官方无关；不收集、不上传任何数据。以 [MIT](LICENSE) 开源。

- 适配版本：`com.zuoyebang.knowledge` **14.34.0 (1738)**
- 模块版本：**1.4.9 (24)**，基于 [libxposed API 102](https://github.com/LSPosed/LSPosed)
- 运行环境：Android 9+（minSdk 24，targetSdk 34），需 LSPosed（支持 API 102 的版本）

## 功能

| 模块 | 作用 |
|---|---|
| `AdBlocker` | 拦截广告聚合层 `com.fastad` 全部 34 个适配器的 `doLoadAD()` / `doShowAD()`（穿山甲 / 优量汇 / 快手 / 百度都走这层） |
| `FeedKiller` | 首页信息流、「科普专区」整列表隐藏并在数据层清零；`DFeedListAdapter` / `DFeedFollowerAdapter` 数据里再过滤广告与 VIP 推荐条目；首页 banner 位隐藏 |
| `ActivityBlocker` | 开屏 / 回前台广告页、激励视频页、AI 解题页、各广告 SDK 落地页，创建即 `finish()` |
| `DialogKiller` | 首页弹窗任务体系与新手引导浮层一律不展示；逐个 hook `dialog.i..t` 自己 override 的 `e(Activity)`（基类钩子会被虚调用绕过），VIP 续费图片浮窗 `dialog.i` 在发请求前就短路 |
| `FloatKiller` | 首页运营浮球不弹、不请求数据 |
| `HomeChromeKiller` | 首页顶栏净化：搜索框、铃铛（通知）、红点角标全部隐藏；年级切换右侧的图片红点入口 `#7f0911a3`（普通 `View`，不是 `BadgeTextView`）也一并隐藏；标题栏右侧两个角标 `#7f09067d` / `#7f09067c` 按 id 清（4 趟，最晚 12s），并用基类 `View.setVisibility` 兜底——角标是 XML `visibility=visible` inflate 出来的，不走 `BadgeTextView.setVisibility`；按文字干掉「绑定作业帮APP账号」推广卡 |
| `WebKiller` | H5 页面注入 JS：隐藏「AI解题」tab 与「讲解中」浮窗；解除复制限制（样式 + 事件拦截）；答案页浮出「复制内容 / 存长图 / 下载图片」工具条，MutationObserver 持续生效。点「隐藏」整条收成半透明「显示」小按钮；按钮禁止长按选中文字 |
| `PageShot` | **整页长截图**：隐藏浮层与视频/广告卡 → 把 WebView 临时撑到整页高度、软件渲染 → 一次画完整页 → PNG 存相册（`Pictures/zybclean`）。不滚动不拼接，公式排版原样保留；空白则自动回退拼接方案。`存题目 / 存解答` 会按「答案/解析…」标题重新找 `splitY` 并裁剪 |
| `AnswerPageKiller` | 原生视图层净化：历史记录页贴底 VIP 横幅按 id `#7f091716` 隐藏（连上面那条 1px 分隔线），几何兜底深 12 层、支持「贴父容器底」；底栏「问小帮」在任意搜题/结果页 `onResume` 后整页深搜 3 趟，只藏它自己的按钮容器（分享 / 加错题本 / 再拍一页不动） |
| `H5Bridge` | WebView 原生桥 `zybcleanHost`：写剪贴板、整页长截图触发、data-url/带 Cookie 下载图片，存到 `Pictures/zybclean`（MediaStore 优先，回落 FUSE + MediaScanner） |
| `FastStart` | 把开屏页里 >800ms 的延时压到 250ms，缩短启动等待 |

调试探针（`Probe` / `ViewDump` / `Snap` / `WebEyes`）代码保留，需要在 `HookEntry` 里取消注释才启用。

## 下载

- APK：[Releases](https://github.com/SQL0106/zuoyebang_xposed/releases/latest)
- 每次提交的构建产物：Actions 最近一次成功运行 → Artifacts → `zybclean-<版本>-universal.apk` / `zybclean-<版本>-universal-debug.apk`

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
src/io/github/sql0106/zybclean/          HookEntry（XposedModule 入口）+ 各 Killer + H5Bridge/PageShot + 探针
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
- 公式是 KaTeX/MathJax 结构或图片，**纯文本提取必然残缺**；要保真就整页长截图（`PageShot`）。
- 长截图必须**一次画完**：滚动拼接会重复段落/留白线；而 `WebView.draw()` 在硬件加速下拍不到内容，得先 `setLayerType(LAYER_TYPE_SOFTWARE)`。
- 底栏「问小帮」是**原生**的，而且不止一套：搜题页在 `AISearchBottomInputView` 里，拍照搜题结果页（`NewV2PicManySearchActivity`）是另一套普通 ViewGroup 底栏 → 两条路径都由 `AnswerPageKiller` 处理。
- WebView 默认禁长按选中，是 App 主动 `setOnLongClickListener` 拦截的 → hook 后 `setResult(null)` 并强制 `setLongClickable(true)` 才能自由复制。
