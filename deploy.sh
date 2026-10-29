#!/bin/bash
# 部署 zybclean 到 realme 并重启作业帮
set -e
cd "$(dirname "$0")"
[ -f zybclean.apk ] || { echo "先跑 ./build.sh"; exit 1; }

TMP=/data/local/tmp/zybclean.apk
scp -q -o BatchMode=yes zybclean.apk "realme:$TMP"
rsh realme "pm install -r $TMP && rm -f $TMP && am force-stop com.zuoyebang.knowledge && echo INSTALLED"
