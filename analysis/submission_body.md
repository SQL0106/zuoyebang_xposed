### 模块

- 名称：**zybclean**
- applicationId：`io.github.sql0106.zybclean`
- 源码：https://github.com/SQL0106/zuoyebang_xposed
- Release：https://github.com/SQL0106/zuoyebang_xposed/releases/tag/2-1.0.1 （tag `2-1.0.1` = versionCode 2 / versionName 1.0.1，含 `zybclean.apk`）
- minSdk 24 / targetSdk 34，适用于 LSPosed（Zygisk）。

### 作用

作业帮（`com.zuoyebang.knowledge`，测试于 14.34.0 / 1738）净化模块：拦截广告聚合层 `com.fastad` 的加载与展示，隐藏首页信息流与「科普专区」，隐藏首页搜索框 / 通知铃铛 / 红点角标与绑定账号推广卡，屏蔽开屏与激励视频等广告页、AI 解题页，注入 JS 隐藏结果页 H5 里的 AI 解题 tab 与「讲解中」浮窗，并压缩开屏延时。不重打包原 APK，不收集任何数据。

### 说明

本项目代码由 LLM（大语言模型）在人工指导下生成，未经逐行安全审计，MIT 开源。旧包名 `com.sql916.zybclean` 的提交（#2356）已按提示改名后重新提交。
