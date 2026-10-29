#!/bin/bash
# 不依赖 LSPosed 管理器，直接把模块写进 lspd 配置库并重启 lspd
# 用法: ./enable_module.sh [包名]
set -e
PKG="${1:-com.sql916.zybclean}"
TARGET="com.zuoyebang.knowledge"
DEV=realme
TMP="$HOME/zuoyebang_xposed/analysis/lspd_live"
mkdir -p "$TMP"
rm -f "$TMP"/modules_config.db "$TMP"/modules_config.db-wal "$TMP"/modules_config.db-shm

APK_PATH="$(rsh $DEV "pm path $PKG" | sed -n 's/^package://p' | head -1)"
[ -n "$APK_PATH" ] || { echo "模块未安装: $PKG"; exit 1; }
echo "apk_path=$APK_PATH"

echo "== 停 lspd"
rsh $DEV 'kill -TERM $(pidof lspd) 2>/dev/null; sleep 2; pidof lspd || echo LSPD_GONE'

echo "== 拉取配置库"
scp -q "$DEV:/data/adb/lspd/config/modules_config.db" "$TMP/"
scp -q "$DEV:/data/adb/lspd/config/modules_config.db-wal" "$TMP/" 2>/dev/null || true
scp -q "$DEV:/data/adb/lspd/config/modules_config.db-shm" "$TMP/" 2>/dev/null || true

echo "== 修改配置库"
sqlite3 "$TMP/modules_config.db" <<SQL
PRAGMA journal_mode=DELETE;
INSERT INTO modules(module_pkg_name, apk_path) VALUES('$PKG','$APK_PATH')
  ON CONFLICT(module_pkg_name) DO UPDATE SET apk_path=excluded.apk_path;
INSERT INTO modules_state(module_pkg_name,user_id,enabled,scope_request_blocked) VALUES('$PKG',0,1,0)
  ON CONFLICT(module_pkg_name,user_id) DO UPDATE SET enabled=1;
INSERT INTO scope(module_pkg_name,app_pkg_name,user_id) VALUES('$PKG','$TARGET',0)
  ON CONFLICT DO NOTHING;
SELECT 'modules:', count(*) FROM modules;
SELECT 'state:', enabled FROM modules_state WHERE module_pkg_name='$PKG';
SELECT 'scope:', app_pkg_name FROM scope WHERE module_pkg_name='$PKG';
SQL

echo "== 推回"
scp -q "$TMP/modules_config.db" "$DEV:/data/adb/lspd/config/modules_config.db"
rsh $DEV 'rm -f /data/adb/lspd/config/modules_config.db-wal /data/adb/lspd/config/modules_config.db-shm; chown root:root /data/adb/lspd/config/modules_config.db; chmod 666 /data/adb/lspd/config/modules_config.db'

echo "== 重启 lspd"
rsh $DEV 'nohup /data/adb/modules/zygisk_lsposed/daemon --force >/dev/null 2>&1 & sleep 3; pidof lspd || echo LSPD_DOWN'

echo "== 重启目标 App"
rsh $DEV 'am force-stop com.zuoyebang.knowledge'
echo ENABLE_DONE
