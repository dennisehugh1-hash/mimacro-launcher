#!/bin/bash
# 编译 MiMacro 调起器 debug APK。用法：./build.sh [gradle 附加参数]
set -euo pipefail
export JAVA_HOME=~/workspace/android/jdk/jdk-21.0.13+9
export ANDROID_HOME=~/workspace/android/sdk
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(dirname "$0")"
~/workspace/android/gradle/gradle-8.14.3/bin/gradle assembleDebug --max-workers=1 "$@"
ls -lh app/build/outputs/apk/debug/app-debug.apk
