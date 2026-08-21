#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
DEFAULT_SDK="/Users/zanderli/Library/Android/sdk"
if [ -d "/opt/homebrew/share/android-commandlinetools/platforms" ]; then
  DEFAULT_SDK="/opt/homebrew/share/android-commandlinetools"
fi
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$DEFAULT_SDK}}"
BUILD_TOOLS="$SDK_ROOT/build-tools/34.0.0"
ANDROID_JAR="$SDK_ROOT/platforms/android-34/android.jar"
BUILD="$ROOT/build"
CLASSES="$BUILD/classes"
DEX="$BUILD/dex"
KEYSTORE="$BUILD/debug.keystore"

if [ ! -f "$ANDROID_JAR" ] || [ ! -x "$BUILD_TOOLS/aapt2" ]; then
  echo "缺少 Android SDK Platform 34 或 Build Tools 34.0.0" >&2
  exit 1
fi

rm -rf "$CLASSES" "$DEX"
mkdir -p "$CLASSES" "$DEX"

"$BUILD_TOOLS/aapt2" link \
  -o "$BUILD/resources.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$ROOT/app/src/main/AndroidManifest.xml" \
  --min-sdk-version 26 \
  --target-sdk-version 34 \
  --version-code 2 \
  --version-name 0.2.0

javac -encoding UTF-8 -source 8 -target 8 \
  -classpath "$ANDROID_JAR" \
  -d "$CLASSES" \
  "$ROOT/app/src/main/java/com/jael/claudenet/ConfigBuilder.java" \
  "$ROOT/app/src/main/java/com/jael/claudenet/LocalConfigServer.java" \
  "$ROOT/app/src/main/java/com/jael/claudenet/MainActivity.java"

jar cf "$BUILD/classes.jar" -C "$CLASSES" .
"$BUILD_TOOLS/d8" --lib "$ANDROID_JAR" --min-api 26 --output "$DEX" "$BUILD/classes.jar"
cp "$BUILD/resources.apk" "$BUILD/unaligned.apk"
(cd "$DEX" && zip -q -j "$BUILD/unaligned.apk" classes.dex)
"$BUILD_TOOLS/zipalign" -f 4 "$BUILD/unaligned.apk" "$BUILD/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -noprompt \
    -keystore "$KEYSTORE" \
    -storepass android \
    -alias androiddebugkey \
    -keypass android \
    -dname "CN=Android Debug,O=Local Recovery,C=CN" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 >/dev/null 2>&1
fi

OUTPUT="$BUILD/Claude网络配置助手-Android-debug.apk"
"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$OUTPUT" \
  "$BUILD/aligned.apk"
"$BUILD_TOOLS/apksigner" verify --verbose "$OUTPUT"
echo "$OUTPUT"
