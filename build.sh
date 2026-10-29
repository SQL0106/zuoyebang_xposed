#!/bin/bash
# 手工打包 zybclean（不用 gradle）
# 依赖：JDK、Android SDK（cmdline-tools 的 r8.jar + platforms/android-XX/android.jar）
#       aapt / zipalign / apksigner（SDK build-tools 或系统包均可）
set -e
cd "$(dirname "$0")"

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/android-sdk/sdk}}"
BT="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1 || true)"
JAR="$(ls "$SDK"/platforms/*/android.jar 2>/dev/null | sort -V | tail -1 || true)"

# 找一个能真正跑起来的可执行文件：先系统命令，再 SDK build-tools
pick() {
  local name="$1" cand
  shift
  if command -v "$name" >/dev/null 2>&1; then
    echo "$(command -v "$name")"
    return 0
  fi
  for cand in "$@"; do
    [ -x "$cand" ] && echo "$cand" && return 0
  done
  return 1
}

AAPT="$(pick aapt "$BT/aapt" || true)"
ZIPALIGN="$(pick zipalign "$BT/zipalign" || true)"
APKSIGNER="$(pick apksigner "$BT/apksigner" || true)"
[ -f "$JAR" ] || { echo "android.jar 未找到（\$SDK/platforms/*/android.jar）"; exit 1; }
[ -n "$AAPT" ] || { echo "aapt 未找到"; exit 1; }
[ -n "$ZIPALIGN" ] || { echo "zipalign 未找到"; exit 1; }
[ -n "$APKSIGNER" ] || { echo "apksigner 未找到"; exit 1; }

# d8：优先 cmdline-tools 自带的 r8.jar（build-tools 里的 d8.jar 在部分镜像上不完整）
R8="$(ls "$SDK"/cmdline-tools/*/lib/r8.jar 2>/dev/null | sort -V | tail -1 || true)"
if [ -n "$R8" ]; then
  D8_CMD=(java -cp "$R8" com.android.tools.r8.D8)
elif [ -n "$BT" ] && [ -f "$BT/lib/d8.jar" ]; then
  D8_CMD=(java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8)
else
  echo "d8/r8.jar 未找到"; exit 1
fi

echo "SDK=$SDK"; echo "android.jar=$JAR"; echo "aapt=$AAPT"; echo "zipalign=$ZIPALIGN"; echo "apksigner=$APKSIGNER"

rm -rf build out.apk
mkdir -p build/classes build/stubs build/dex build/pkg

# 1. Xposed API stub（仅编译期，不进 dex）
javac --release 8 -nowarn -d build/stubs $(find libs/stubs -name '*.java')

# 2. 模块源码
javac --release 8 -nowarn -d build/classes -cp build/stubs $(find src -name '*.java')

# 3. dex
"${D8_CMD[@]}" --min-api 24 --release --lib "$JAR" \
    --output build/dex $(find build/classes -name '*.class')

# 4. 清单/资源（aapt1 同时把 assets/ 打进包里）
"$AAPT" package -f -M AndroidManifest.xml -A assets -I "$JAR" -F build/base.apk

# 5. 合入 dex
cp build/dex/classes.dex build/pkg/
( cd build/pkg && zip -q -X ../base.apk classes.dex )

# 6. 对齐 + 签名
"$ZIPALIGN" -f -p 4 build/base.apk build/aligned.apk

KS="$PWD/zybclean.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias zybclean -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass zybclean -keypass zybclean \
    -dname "CN=zybclean, O=sql916, C=CN" >/dev/null 2>&1
  echo "keystore created: $KS"
fi

"$APKSIGNER" sign --ks "$KS" --ks-pass pass:zybclean --key-pass pass:zybclean \
    --v1-signing-enabled true --v2-signing-enabled true \
    --out zybclean.apk build/aligned.apk
"$APKSIGNER" verify --print-certs zybclean.apk | head -3

ls -l zybclean.apk
echo BUILD_OK
