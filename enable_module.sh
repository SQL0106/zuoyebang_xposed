#!/bin/bash
# 备用脚本：设备上没有 LSPosed 管理器时，直接把模块写进 lspd 配置库（作用域=目标 App）。
#
# 注意：
#  - **不重启手机**。作用域里只有普通 App 时，LSPosed 在目标 App 下次启动时读取配置即生效；
#    只有作用域包含「系统框架（system_server）」才必须重启。
#  - **不要 kill lspd**：lspd 被杀后无法手动拉起（`nohup/setsid ./daemon` 一律静默退出），
#    那才会被迫重启手机。
#
# 用法: ./enable_module.sh [模块包名] [目标包名]
set -e
PKG="${1:-io.github.sql0106.zybclean}"
TARGET="${2:-com.zuoyebang.knowledge}"
DEV=realme
TMP="$HOME/zuoyebang_xposed/analysis/lspd_live"
mkdir -p "$TMP"
rm -f "$TMP"/modules_config.db "$TMP"/modules_config.db-wal "$TMP"/modules_config.db-shm

APK_PATH="$(rsh $DEV "pm path $PKG" | sed -n 's/^package://p' | head -1)"
[ -n "$APK_PATH" ] || { echo "模块未安装: $PKG"; exit 1; }
echo "apk_path=$APK_PATH"

echo "== 拉取配置库（连 wal/shm 一起，本地打开时会自动合并）"
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

echo "== 推回（先推 db 再删 wal/shm，方便 lspd 重新打开新库）"
scp -q "$TMP/modules_config.db" "$DEV:/data/adb/lspd/config/modules_config.db"
rsh $DEV 'rm -f /data/adb/lspd/config/modules_config.db-wal /data/adb/lspd/config/modules_config.db-shm; chown root:root /data/adb/lspd/config/modules_config.db; chmod 666 /data/adb/lspd/config/modules_config.db'

echo "== 重启目标 App（改动在它下次启动时生效，不需要重启手机）"
rsh $DEV "am force-stop $TARGET"
echo ENABLE_DONE
