#!/bin/bash
# 安装后验证：拉 ZYBCLEAN 日志
DEV=realme
echo "== 模块是否在 lspd 库里启用"
rsh $DEV "sqlite3 /data/adb/lspd/config/modules_config.db 2>/dev/null || echo NO_SQLITE_ON_DEVICE"
echo "== lspd 日志中的 ZYBCLEAN"
rsh $DEV 'grep -h ZYBCLEAN /data/adb/lspd/log/*.log 2>/dev/null | tail -40'
echo "== logcat 中的 ZYBCLEAN（若 XposedBridge.log 也进 logcat）"
rsh $DEV 'logcat -d 2>/dev/null | grep ZYBCLEAN | tail -40'
echo "== 目标进程"
rsh $DEV 'ps -A | grep zuoyebang'
