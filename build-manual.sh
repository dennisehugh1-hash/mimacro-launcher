#!/bin/bash
# 手动编译 MiMacro 调起器（不用 Gradle）。
#
# 原因：本机 Gradle 的 Apache HttpClient 走出口代理时 CONNECT 隧道会被
# reset（curl 同地址 200 正常），AGP 依赖下不下来。调起器只有一个 Activity、
# 零第三方依赖，直接用 SDK 工具链编译即可：
#   javac -> d8 -> aapt2 link -> zipalign -> apksigner
# 产物：build/mimacro-launcher-debug.apk（debug 签名，可直接安装）
set -euo pipefail

export JAVA_HOME=~/workspace/android/jdk/jdk-21.0.13+9
export ANDROID_HOME=~/workspace/android/sdk
BT="$ANDROID_HOME/sdk/build-tools/35.0.0"
# 修正：ANDROID_HOME 已是 sdk 目录
BT="$ANDROID_HOME/build-tools/35.0.0"
PLATFORM="$ANDROID_HOME/platforms/android-35/android.jar"
export PATH="$JAVA_HOME/bin:$PATH"

cd "$(dirname "$0")"
rm -rf build/classes build/dex build/app-unaligned.apk build/app-aligned.apk
mkdir -p build/classes build/dex

echo "== javac =="
javac --release 17 -cp "$PLATFORM" -d build/classes \
  $(find app/src/main/java -name '*.java')

echo "== d8 =="
"$BT/d8" --lib "$PLATFORM" --output build/dex $(find build/classes -name '*.class')

echo "== aapt2 link =="
"$BT/aapt2" link -o build/app-unaligned.apk -I "$PLATFORM" \
  --manifest app/src/main/AndroidManifest.xml

echo "== add dex =="
cd build/dex && zip -0 -q ../app-unaligned.apk classes.dex && cd ../..

echo "== zipalign =="
"$BT/zipalign" -f 4 build/app-unaligned.apk build/app-aligned.apk

echo "== keystore =="
mkdir -p ~/.android
if [ ! -f ~/.android/debug.keystore ]; then
  keytool -genkeypair -keystore ~/.android/debug.keystore \
    -storepass android -alias androiddebugkey -keypass android \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" 2>&1 | tail -1
fi

echo "== apksigner =="
"$BT/apksigner" sign --ks ~/.android/debug.keystore \
  --ks-pass pass:android --key-pass pass:android \
  --out build/mimacro-launcher-debug.apk build/app-aligned.apk

echo "== verify =="
"$BT/apksigner" verify -v build/mimacro-launcher-debug.apk 2>&1 | grep -E 'Verified|WARNING|ERROR' | head -5
ls -lh build/mimacro-launcher-debug.apk
