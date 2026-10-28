#!/usr/bin/env bash
# Installs everything needed to build Artham from source on a Linux x86_64 machine.
# Idempotent: re-running it is safe and skips work already done.
set -euo pipefail

ROOT="${ARTHAM_TOOLS:-$HOME/artham-toolchain}"
JDK_VERSION=17
GRADLE_VERSION=8.9
SDK_PLATFORM=android-34
SDK_BUILD_TOOLS=34.0.0
CMDLINE_TOOLS=commandlinetools-linux-11076708_latest.zip

mkdir -p "$ROOT"

# 1. JDK 17 (Temurin)
if [ ! -x "$ROOT/jdk17/bin/java" ]; then
  echo "==> installing Temurin JDK $JDK_VERSION"
  curl -sL -o "$ROOT/jdk.tar.gz" \
    "https://api.adoptium.net/v3/binary/latest/${JDK_VERSION}/ga/linux/x64/jdk/hotspot/normal/eclipse"
  tar -xzf "$ROOT/jdk.tar.gz" -C "$ROOT"
  rm -f "$ROOT/jdk.tar.gz"
  mv "$ROOT"/jdk-* "$ROOT/jdk17"
else
  echo "==> JDK already present"
fi
export JAVA_HOME="$ROOT/jdk17"
export PATH="$JAVA_HOME/bin:$PATH"
java -version

# 2. Gradle (only needed to regenerate the wrapper; the wrapper does the real build)
if [ ! -x "$ROOT/gradle-$GRADLE_VERSION/bin/gradle" ]; then
  echo "==> installing Gradle $GRADLE_VERSION"
  curl -sL -o "$ROOT/gradle.zip" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  unzip -q "$ROOT/gradle.zip" -C "$ROOT"
  rm -f "$ROOT/gradle.zip"
else
  echo "==> Gradle already present"
fi

# 3. Android SDK: command line tools, platform, build tools
export ANDROID_HOME="$ROOT/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
if [ ! -d "$ANDROID_HOME/platforms/$SDK_PLATFORM" ]; then
  echo "==> installing Android SDK components"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  curl -sL -o "$ROOT/cmdline-tools.zip" "https://dl.google.com/android/repository/$CMDLINE_TOOLS"
  unzip -q "$ROOT/cmdline-tools.zip" -d "$ANDROID_HOME/cmdline-tools"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -f "$ROOT/cmdline-tools.zip"
  yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --licenses >/dev/null || true
  "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" \
    "platforms;$SDK_PLATFORM" "build-tools;$SDK_BUILD_TOOLS" "platform-tools"
else
  echo "==> Android SDK already present"
fi

# 4. Point the project at the SDK
cat > "$(dirname "$0")/../local.properties" <<PROPS
sdk.dir=$ANDROID_HOME
PROPS

echo
echo "Toolchain ready."
echo "  JAVA_HOME=$JAVA_HOME"
echo "  ANDROID_HOME=$ANDROID_HOME"
echo "Now run: ./gradlew assembleDebug"
