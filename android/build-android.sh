#!/bin/bash
# 一键构建：先在 JVM 上跑单元测试，再用 Gradle 打包。
# 有 keystore.properties（或 CLAUDE_NET_KEYSTORE_PROPERTIES）时生成正式签名的 release 包，
# 否则生成 debug 包。支持 macOS / Linux / Windows（Git Bash）。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
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
  echo "找不到 Android SDK（需要 $PLATFORM），请设置 ANDROID_SDK_ROOT" >&2
  exit 1
}
export ANDROID_HOME="$SDK_ROOT" ANDROID_SDK_ROOT="$SDK_ROOT"

if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
for cmd in java javac; do
  command -v "$cmd" >/dev/null || { echo "找不到 $cmd，请安装 JDK 17 或设置 JAVA_HOME" >&2; exit 1; }
done

BUILD="$ROOT/build"
TEST_CLASSES="$BUILD/test-classes"
SRC="$ROOT/app/src/main/java/com/jael/claudenet"
# 不依赖 Android API 的类可以直接在 JVM 上测试
TESTABLE_SOURCES=(
  "$SRC/ConfigBuilder.java"
  "$SRC/ProxyPortDetector.java"
  "$SRC/LocalConfigServer.java"
  "$SRC/ImportParser.java"
)
TESTS=(ConfigBuilderTest ProxyPortDetectorTest LocalConfigServerTest ImportParserTest)

rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"

echo "==> 单元测试"
javac -encoding UTF-8 -d "$TEST_CLASSES" \
  "${TESTABLE_SOURCES[@]}" "$ROOT"/tests/com/jael/claudenet/*.java
for test in "${TESTS[@]}"; do
  java -Dfile.encoding=UTF-8 -cp "$TEST_CLASSES" "com.jael.claudenet.$test"
done

PROPS="${CLAUDE_NET_KEYSTORE_PROPERTIES:-$ROOT/keystore.properties}"
if [ -f "$PROPS" ]; then
  VARIANT=release
  OUTPUT="$BUILD/Claude网络配置助手-Android-release.apk"
  echo "==> Gradle 构建（正式签名：$PROPS）"
else
  VARIANT=debug
  OUTPUT="$BUILD/Claude网络配置助手-Android-debug.apk"
  echo "==> Gradle 构建（未找到 keystore.properties，生成调试签名的 debug 包）"
fi

GRADLE_TASK="assemble$(tr '[:lower:]' '[:upper:]' <<< "${VARIANT:0:1}")${VARIANT:1}"
(cd "$ROOT" && ./gradlew --no-daemon -q "$GRADLE_TASK")

cp "$ROOT/app/build/outputs/apk/$VARIANT/app-$VARIANT.apk" "$OUTPUT"
echo "$OUTPUT"
