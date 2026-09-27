#!/bin/bash
# 不依赖 Gradle 的最小构建：aapt2 → javac → d8 → zipalign → apksigner。
# 支持 macOS / Linux / Windows（Git Bash）。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
PACKAGE="com.jael.claudenet"
BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-34.0.0}"
PLATFORM="${ANDROID_PLATFORM:-android-34}"

find_sdk() {
  local candidate
  for candidate in \
      "${ANDROID_SDK_ROOT:-}" \
      "${ANDROID_HOME:-}" \
      "$HOME/Library/Android/sdk" \
      "/opt/homebrew/share/android-commandlinetools" \
      "$HOME/Android/Sdk" \
      "${LOCALAPPDATA:-}/Android/Sdk"; do
    if [ -n "$candidate" ] && [ -d "$candidate/platforms/$PLATFORM" ]; then
      echo "$candidate"
      return 0
    fi
  done
  return 1
}

SDK_ROOT="$(find_sdk)" || {
  echo "找不到 Android SDK（需要 $PLATFORM 与 Build Tools $BUILD_TOOLS_VERSION），请设置 ANDROID_SDK_ROOT" >&2
  exit 1
}
BUILD_TOOLS="$SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION"
ANDROID_JAR="$SDK_ROOT/platforms/$PLATFORM/android.jar"

# Windows 上 d8/apksigner 是 .bat，其余工具是 .exe
tool() {
  local name="$1" ext
  for ext in "" ".exe" ".bat"; do
    if [ -f "$BUILD_TOOLS/$name$ext" ]; then
      echo "$BUILD_TOOLS/$name$ext"
      return 0
    fi
  done
  echo "缺少 Build Tools 组件：$BUILD_TOOLS/$name" >&2
  return 1
}
AAPT="$(tool aapt)"
AAPT2="$(tool aapt2)"
D8="$(tool d8)"
ZIPALIGN="$(tool zipalign)"
APKSIGNER="$(tool apksigner)"

if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
for cmd in java javac jar keytool; do
  command -v "$cmd" >/dev/null || { echo "找不到 $cmd，请安装 JDK 17 或设置 JAVA_HOME" >&2; exit 1; }
done

BUILD="$ROOT/build"
CLASSES="$BUILD/classes"
TEST_CLASSES="$BUILD/test-classes"
DEX="$BUILD/dex"
KEYSTORE="${KEYSTORE:-$BUILD/debug.keystore}"
OUTPUT="$BUILD/Claude网络配置助手-Android-debug.apk"
SOURCES=(
  "$ROOT/app/src/main/java/com/jael/claudenet/ConfigBuilder.java"
  "$ROOT/app/src/main/java/com/jael/claudenet/ProxyPortDetector.java"
  "$ROOT/app/src/main/java/com/jael/claudenet/LocalConfigServer.java"
  "$ROOT/app/src/main/java/com/jael/claudenet/MainActivity.java"
)
# 不依赖 Android API 的类可以直接在 JVM 上测试
TESTABLE_SOURCES=("${SOURCES[@]:0:3}")
TESTS=(ConfigBuilderTest ProxyPortDetectorTest LocalConfigServerTest)

rm -rf "$CLASSES" "$TEST_CLASSES" "$DEX"
mkdir -p "$CLASSES" "$TEST_CLASSES" "$DEX"

echo "==> 单元测试"
javac -encoding UTF-8 -d "$TEST_CLASSES" \
  "${TESTABLE_SOURCES[@]}" "$ROOT"/tests/com/jael/claudenet/*.java
for test in "${TESTS[@]}"; do
  java -Dfile.encoding=UTF-8 -cp "$TEST_CLASSES" "com.jael.claudenet.$test"
done

echo "==> 打包资源"
# 源 Manifest 按 AGP 8 规范不写 package（由 Gradle namespace 提供），这里临时补上
sed "s#<manifest #<manifest package=\"$PACKAGE\" #" \
  "$ROOT/app/src/main/AndroidManifest.xml" > "$BUILD/AndroidManifest.xml"
"$AAPT2" link \
  -o "$BUILD/resources.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$BUILD/AndroidManifest.xml" \
  --min-sdk-version 26 \
  --target-sdk-version 34 \
  --version-code 4 \
  --version-name 0.3.1

echo "==> 编译 Java"
javac -encoding UTF-8 --release 8 \
  -classpath "$ANDROID_JAR" \
  -d "$CLASSES" \
  "${SOURCES[@]}"
(cd "$CLASSES" && jar cf "../classes.jar" .)

echo "==> 转换 DEX"
"$D8" --lib "$ANDROID_JAR" --min-api 26 --output "$DEX" "$BUILD/classes.jar"

echo "==> 组装 APK"
cp "$BUILD/resources.apk" "$BUILD/unaligned.apk"
# 用 SDK 自带的 aapt 追加 classes.dex，不依赖系统 zip 命令
(cd "$DEX" && "$AAPT" add -k "$BUILD/unaligned.apk" classes.dex >/dev/null)
"$ZIPALIGN" -f 4 "$BUILD/unaligned.apk" "$BUILD/aligned.apk"

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

echo "==> 签名"
"$APKSIGNER" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$OUTPUT" \
  "$BUILD/aligned.apk"
"$APKSIGNER" verify --verbose "$OUTPUT"
echo "$OUTPUT"
